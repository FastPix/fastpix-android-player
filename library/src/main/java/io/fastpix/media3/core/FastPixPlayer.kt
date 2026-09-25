package io.fastpix.media3.core

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import io.fastpix.media3.abr.AbrConfig
import io.fastpix.media3.abr.NetworkAwareAbrController
import io.fastpix.media3.abr.NetworkMonitor
import io.fastpix.media3.abr.PlaybackStallWatchdog
import io.fastpix.media3.analytics.AnalyticsConfig
import io.fastpix.media3.analytics.AnalyticsManager
import io.fastpix.media3.analytics.AnalyticsSessions
import io.fastpix.data.domain.model.VideoDataDetails
import io.fastpix.media3.buffer.BufferConfig
import io.fastpix.media3.cache.CacheConfig
import io.fastpix.media3.cache.FastPixItemKeys
import io.fastpix.media3.cache.ItemScopedMediaSourceFactory
import io.fastpix.media3.cache.MediaCacheProvider
import io.fastpix.media3.preload.PlaylistPreloader
import io.fastpix.media3.preload.PreloadConfig
import io.fastpix.media3.preload.PreloadPolicy
import io.fastpix.media3.preload.PreloadSetup
import io.fastpix.media3.preload.PreloadWindow
import io.fastpix.media3.prerender.DecoderBudget
import io.fastpix.media3.prerender.PrerenderConfig
import io.fastpix.media3.prerender.PrerenderSurfaceHost
import io.fastpix.media3.prerender.SingleViewPrerenderer
import androidx.media3.exoplayer.DecoderCounters
import io.fastpix.media3.PlaybackListener
import io.fastpix.media3.playlist.PlaylistItem
import io.fastpix.media3.playlist.PlaylistItemChangeReason
import io.fastpix.media3.playlist.PlaylistListener
import io.fastpix.media3.playlist.PlaylistQueue
import io.fastpix.media3.seekpreview.PlaybackUrlProvider
import androidx.media3.common.text.CueGroup
import io.fastpix.media3.tracks.AudioTrack
import io.fastpix.media3.tracks.AudioTrackError
import io.fastpix.media3.tracks.AudioTrackListener
import io.fastpix.media3.tracks.AudioTrackUpdateReason
import io.fastpix.media3.tracks.MediaSelectionController
import io.fastpix.media3.tracks.SubtitleCueInfo
import io.fastpix.media3.tracks.SubtitleRenderInfo
import io.fastpix.media3.tracks.SubtitleTrack
import io.fastpix.media3.tracks.SubtitleTrackError
import io.fastpix.media3.tracks.SubtitleTrackListener
import io.fastpix.media3.tracks.TrackManager
import io.fastpix.media3.tracks.VideoTrack
import io.fastpix.media3.seekpreview.SeekPreviewManager
import io.fastpix.media3.seekpreview.listeners.SeekPreviewListener
import io.fastpix.media3.seekpreview.models.PreviewFallbackMode
import io.fastpix.media3.seekpreview.models.SeekPreviewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

@UnstableApi
class FastPixPlayer private constructor(
    private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val trackSelector: DefaultTrackSelector,
    private val abrConfig: AbrConfig,
    initialLoop: Boolean = false,
    initialAutoplay: Boolean = false,
    private val seekPreviewConfig: SeekPreviewConfig? = null,
    private val analyticsConfig: AnalyticsConfig? = null,
    private val bandwidthMeter: BandwidthMeter,
    preloadSetup: PreloadSetup? = null,
    /** Preload track selectors shared through a [io.fastpix.media3.playlist.FastPixPlayerPool]. */
    sharedPreloadTrackSelectors: List<DefaultTrackSelector> = emptyList(),
) {

    private val seekPreviewEnabled: Boolean = seekPreviewConfig?.enabled == true

    /** The [io.fastpix.media3.PlayerView] currently showing this player, if any. */
    private var displayingView: io.fastpix.media3.PlayerView? = null

    /**
     * A view to begin as soon as a [io.fastpix.media3.PlayerView] shows this player: analytics
     * measures through a view, and none was available when the video started.
     */
    private var pendingAnalyticsView = false
    private var pendingAnalyticsVideoData: VideoDataDetails? = null

    /**
     * Builder class for creating FastPixPlayer instances with configuration.
     *
     * Example usage:
     * ```
     * val player = FastPixPlayer.Builder(context)
     *     .setLoop(true)
     *     .setAutoplay(false)
     *     .setSeekPreviewConfig(SeekPreviewConfig.Builder().setEnabled(true).build())
     *     .setAnalyticsConfig(AnalyticsConfig.Builder(playerView, "your-workspace-id").build())
     *     .build()
     * ```
     */
    class Builder(private val context: Context) {
        private var loop: Boolean = false
        private var autoplay: Boolean = false
        private var seekPreviewConfig: SeekPreviewConfig? = null
        private var analyticsConfig: AnalyticsConfig? = null
        internal var abrConfig: AbrConfig = AbrConfig.DEFAULT
            private set
        internal var bufferConfig: BufferConfig = BufferConfig.DEFAULT
            private set
        private var cacheConfig: CacheConfig = CacheConfig.DISABLED
        private var preloadConfig: PreloadConfig = PreloadConfig.DISABLED
        private var prerenderConfig: PrerenderConfig = PrerenderConfig.DISABLED

        /**
         * Sets whether playback should loop when it reaches the end.
         *
         * @param loop true to enable looping, false otherwise. Defaults to false.
         * @return This builder instance for method chaining.
         */
        fun setLoop(loop: Boolean): Builder {
            this.loop = loop
            return this
        }

        /**
         * Sets whether playback should start automatically when ready.
         *
         * @param autoplay true to enable autoplay, false otherwise. Defaults to false.
         * @return This builder instance for method chaining.
         */
        fun setAutoplay(autoplay: Boolean): Builder {
            this.autoplay = autoplay
            return this
        }

        /**
         * Sets seek preview configuration (enabled, custom URL, preload, cache, etc.).
         * When null, seek preview is disabled. When non-null, use [SeekPreviewConfig.Builder]
         * to configure enabled, custom spritesheet URL, preload radius, and cache.
         *
         * @param config Seek preview config, or null to disable seek preview.
         * @return This builder instance for method chaining.
         */
        fun setSeekPreviewConfig(config: SeekPreviewConfig?): Builder {
            this.seekPreviewConfig = config
            return this
        }

        /**
         * Sets FastPix Analytics configuration. When non-null and [AnalyticsConfig.enabled] is true,
         * playback events are automatically sent to the FastPix Data dashboard.
         * When null, analytics is disabled. Analytics never affects playback stability.
         *
         * @param config Analytics config with [AnalyticsConfig.playerView], [AnalyticsConfig.workSpaceId],
         *   and optional metadata; or null to disable analytics.
         * @return This builder instance for method chaining.
         */
        fun setAnalyticsConfig(config: AnalyticsConfig?): Builder {
            this.analyticsConfig = config
            return this
        }

        /**
         * Sets ABR (adaptive bitrate) tuning. Controls how aggressively the player switches
         * down when bandwidth drops (e.g. WiFi → mobile) and whether the bandwidth meter and
         * track selector react to network-type changes. Defaults are tuned for faster downgrade
         * than Media3's out-of-the-box values.
         *
         * @param config ABR tuning parameters; use [AbrConfig.DEFAULT] to restore defaults.
         * @return This builder instance for method chaining.
         */
        fun setAbrConfig(config: AbrConfig): Builder {
            this.abrConfig = config
            return this
        }

        /**
         * Sets the buffering thresholds that decide how soon the first frame appears and how much
         * media is held ahead of the playhead.
         *
         * Defaults to [BufferConfig.DEFAULT], which starts sooner than Media3's stock values. Use
         * [BufferConfig.FEED] for reel-style feeds, or [BufferConfig.MEDIA3_DEFAULT] to restore
         * Media3's behaviour exactly.
         *
         * @param config Buffering thresholds.
         * @return This builder instance for method chaining.
         */
        fun setBufferConfig(config: BufferConfig): Builder {
            this.bufferConfig = config
            return this
        }

        /**
         * Enables the read-through disk cache, so re-watching an item — or scrolling back to an
         * earlier one in a feed — plays from local storage instead of the network.
         *
         * Off by default: caching writes to the user's device, which is the app's call. The cache
         * is process-wide, so the first player to enable it fixes the location and size for the
         * process. Pass the same [CacheConfig] to
         * [io.fastpix.media3.cache.FastPixPreCacher.create] to warm upcoming items into the same
         * store.
         *
         * The first [build] with caching enabled opens the cache index on the calling thread, which
         * touches disk. To keep that off the main thread, open it once from a background thread at
         * startup with [MediaCacheProvider.getOrCreate] — later calls just return the open cache.
         *
         * @param config Cache settings; [CacheConfig.DISABLED] to turn caching off.
         * @return This builder instance for method chaining.
         */
        fun setCacheConfig(config: CacheConfig): Builder {
            this.cacheConfig = config
            return this
        }

        /**
         * Sets how many playlist entries around the current one are prepared ahead of time, e.g.
         * `PreloadConfig(count = 3)` for the next three. See [PreloadConfig] for what is prepared.
         *
         * Acts on the playlist set with [FastPixPlayer.setPlaylist]. With the disk cache on as well
         * ([setCacheConfig]), entries further out are also written to disk.
         *
         * @param config Preload settings; [PreloadConfig.DISABLED] to turn preloading off.
         * @return This builder instance for method chaining.
         */
        fun setPreloadConfig(config: PreloadConfig): Builder {
            this.preloadConfig = config
            return this
        }

        /**
         * Sets how many playlist entries around the current one are pre-rendered, e.g.
         * `PrerenderConfig(count = 1)` for the next one. Moving to a pre-rendered entry shows its
         * first frame at once, instead of a black frame while the video starts. See
         * [PrerenderConfig].
         *
         * Needs a [io.fastpix.media3.PlayerView] showing the player: frames are captured through it.
         * For UIs with a page and a view per entry, use
         * [io.fastpix.media3.playlist.FastPixPlayerPool] instead.
         *
         * @param config Pre-render settings; [PrerenderConfig.DISABLED] to turn it off.
         * @return This builder instance for method chaining.
         */
        fun setPrerenderConfig(config: PrerenderConfig): Builder {
            this.prerenderConfig = config
            return this
        }

        /**
         * Builds and returns a configured FastPixPlayer instance.
         *
         * @return A new FastPixPlayer instance with the configured settings.
         */
        fun build(): FastPixPlayer {
            // Bandwidth meter drives within-network ABR (used by AdaptiveTrackSelection).
            // Network-type transitions are handled separately by NetworkAwareAbrController, which
            // applies a maxVideoBitrate cap. Resetting the meter on type change is still useful so
            // stale Wi-Fi measurements don't seed the initial estimate on cellular.
            val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
                .setResetOnNetworkTypeChange(abrConfig.resetBandwidthOnNetworkChange)
                .build()

            val trackSelectionFactory = AdaptiveTrackSelection.Factory(
                abrConfig.minDurationForQualityIncreaseMs,
                abrConfig.maxDurationForQualityDecreaseMs,
                abrConfig.minDurationToRetainAfterDiscardMs,
                abrConfig.bandwidthFraction
            )

            val loadControl = bufferConfig.toLoadControl()

            // The cache has to be installed at construction time: it lives underneath the
            // MediaSource.Factory, which ExoPlayer.Builder freezes on build(). Null when caching is
            // off or the cache could not be opened, in which case playback is byte-for-byte what
            // it was before.
            val cache = MediaCacheProvider.getOrCreate(context, cacheConfig)
            val mediaSourceFactory: MediaSource.Factory? =
                cache?.let { ItemScopedMediaSourceFactory(context, it, cacheConfig) }

            val exoPlayer: ExoPlayer
            val trackSelector: DefaultTrackSelector
            var preloadSetup: PreloadSetup? = null

            if (preloadConfig.enabled || prerenderConfig.enabled) {
                // Preloaded sources are only playable by a player built from the same preload
                // manager builder, which shares load control, bandwidth estimate, playback thread
                // and source factory between the two. Pre-rendered entries are preloaded too, so
                // the main player starts them from warm bytes while their first frame is shown.
                val policy = PreloadPolicy(
                    PreloadWindow(
                        ahead = maxOf(preloadConfig.count, prerenderConfig.count),
                        behind = maxOf(preloadConfig.behind, prerenderConfig.behind),
                    ),
                    preloadConfig.bufferedDurationMs,
                )
                val createdSelectors = mutableListOf<DefaultTrackSelector>()
                val preloadBuilder = DefaultPreloadManager.Builder(context, policy)
                    .setBandwidthMeter(bandwidthMeter)
                    .setLoadControl(loadControl)
                    .setTrackSelectorFactory { selectorContext ->
                        DefaultTrackSelector(selectorContext, trackSelectionFactory)
                            .also { createdSelectors += it }
                    }
                    .setMediaSourceFactory(mediaSourceFactory ?: DefaultMediaSourceFactory(context))
                exoPlayer = preloadBuilder.buildExoPlayer(ExoPlayer.Builder(context))
                val preloadManager = preloadBuilder.build()
                trackSelector = exoPlayer.trackSelector as DefaultTrackSelector
                val prerenderer = if (prerenderConfig.enabled) {
                    SingleViewPrerenderer(
                        capturePlayer = preloadBuilder.buildExoPlayer(ExoPlayer.Builder(context)),
                        config = prerenderConfig,
                    )
                } else {
                    null
                }
                preloadSetup = PreloadSetup(
                    preloadManager = preloadManager,
                    policy = policy,
                    // The capture player's selector is included: its captures must match the
                    // rendition playback will show.
                    preloadTrackSelectors = createdSelectors.filter { it !== trackSelector },
                    cache = cache,
                    cacheConfig = cacheConfig,
                    prerenderer = prerenderer,
                )
            } else {
                trackSelector = DefaultTrackSelector(context, trackSelectionFactory)
                val playerBuilder = ExoPlayer.Builder(context)
                    .setBandwidthMeter(bandwidthMeter)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(loadControl)
                mediaSourceFactory?.let { playerBuilder.setMediaSourceFactory(it) }
                exoPlayer = playerBuilder.build()
            }

            return FastPixPlayer(
                context = context,
                exoPlayer = exoPlayer,
                trackSelector = trackSelector,
                abrConfig = abrConfig,
                initialLoop = loop,
                initialAutoplay = autoplay,
                seekPreviewConfig = seekPreviewConfig,
                analyticsConfig = analyticsConfig,
                bandwidthMeter = bandwidthMeter,
                preloadSetup = preloadSetup,
            )
        }

        /** The adaptive track selection [abrConfig] describes. */
        internal fun trackSelectionFactory(): AdaptiveTrackSelection.Factory =
            AdaptiveTrackSelection.Factory(
                abrConfig.minDurationForQualityIncreaseMs,
                abrConfig.maxDurationForQualityDecreaseMs,
                abrConfig.minDurationToRetainAfterDiscardMs,
                abrConfig.bandwidthFraction
            )

        /**
         * Builds a player for a [io.fastpix.media3.playlist.FastPixPlayerPool]: from the pool's
         * preload manager builder, so it can play what the pool preloaded, sharing the pool's
         * bandwidth estimate, buffering and cache. This builder's own cache and preload settings are
         * ignored — the pool's apply — and autoplay is off: the pool decides what plays.
         */
        internal fun buildForPool(
            preloadBuilder: DefaultPreloadManager.Builder,
            bandwidthMeter: BandwidthMeter,
            preloadTrackSelectors: List<DefaultTrackSelector>,
        ): FastPixPlayer {
            val exoPlayer = preloadBuilder.buildExoPlayer(ExoPlayer.Builder(context))
            return FastPixPlayer(
                context = context,
                exoPlayer = exoPlayer,
                trackSelector = exoPlayer.trackSelector as DefaultTrackSelector,
                abrConfig = abrConfig,
                initialLoop = loop,
                initialAutoplay = false,
                seekPreviewConfig = seekPreviewConfig,
                analyticsConfig = analyticsConfig,
                bandwidthMeter = bandwidthMeter,
                preloadSetup = null,
                sharedPreloadTrackSelectors = preloadTrackSelectors,
            )
        }
    }

    companion object {
        private const val ERROR_CODE_EMPTY_PLAYBACK_ID = 9002

        /** Longest a captured first frame covers the video while this player starts. */
        private const val BRIDGE_TIMEOUT_MS = 4_000L
        private const val ERROR_CODE_DRM_LICENSE_URL_EMPTY = 9010
        private const val ERROR_CODE_DRM_CONFIGURATION_FAILED = 9011
        private const val ERROR_CODE_SET_MEDIA_ITEM_FAILED = 9012

        /**
         * Error code emitted via `PlaybackListener.onError` when the player has been stuck in
         * `STATE_BUFFERING` for longer than [AbrConfig.playbackStallTimeoutMs]. Apps can match
         * on this code to show a "Network too slow to play" message instead of treating it as
         * a generic playback error.
         */
        const val ERROR_CODE_NETWORK_STALL_TIMEOUT: Int = 9013

        /**
         * Default interval for playback time updates in milliseconds.
         */
        private const val DEFAULT_TIME_UPDATE_INTERVAL_MS = 500L

        /**
         * Default interval for device volume monitoring in milliseconds.
         */
        private const val DEFAULT_VOLUME_CHECK_INTERVAL_MS = 200L

        /**
         * Available playback speeds in order from slowest to fastest.
         */
        @JvmStatic
        val AVAILABLE_PLAYBACK_SPEEDS =
            floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

        /**
         * Normal playback speed (1.0x).
         */
        private const val NORMAL_PLAYBACK_SPEED = 1.0f

        /**
         * Creates a new FastPixPlayer instance with default settings.
         * For custom configuration, use [Builder] instead.
         *
         * @param context The context to use for creating the underlying ExoPlayer.
         * @return A new FastPixPlayer instance with default settings.
         */
        @JvmStatic
        fun create(context: Context): FastPixPlayer {
            return Builder(context).build()
        }
    }

    /**
     * Whether playback should loop when it reaches the end.
     *
     * Default: `false`
     *
     * When `true`:
     * - Playback will automatically restart from the beginning when it reaches the end
     * - The current media item will repeat indefinitely
     *
     * When `false`:
     * - Playback will stop when it reaches the end
     */
    var loop: Boolean = false
        set(value) {
            field = value
            exoPlayer.repeatMode = if (value) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            }
        }

    /**
     * Whether playback should start automatically when ready.
     *
     * Default: `false`
     *
     * When `true`:
     * - Playback will automatically start when the media is ready to play
     * - No manual call to play() is required
     *
     * When `false`:
     * - Playback must be started manually by calling play() or setPlayWhenReady(true)
     */
    var autoplay: Boolean = false
        set(value) {
            field = value
            // Update existing player if it exists
            exoPlayer.setPlayWhenReady(value)
        }

    /**
     * List of playback listeners.
     *
     * Copy-on-write, like every listener list here: dispatch iterates a snapshot, so a listener
     * may add or remove listeners — itself included — from inside a callback. Apps do this
     * routinely (a one-shot listener removing itself on first frame), and a plain list throws
     * ConcurrentModificationException mid-dispatch.
     */
    private val playbackListeners = CopyOnWriteArrayList<PlaybackListener>()

    /** Playlist entries and position; empty unless [setPlaylist] or [addToPlaylist] was used. */
    private val playlist = PlaylistQueue<PlaylistItem>()

    private val playlistListeners = CopyOnWriteArrayList<PlaylistListener>()

    /**
     * List of audio track listeners.
     */
    private val audioTrackListeners = CopyOnWriteArrayList<AudioTrackListener>()

    /**
     * List of subtitle track listeners.
     */
    private val subtitleTrackListeners = CopyOnWriteArrayList<SubtitleTrackListener>()

    /**
     * Unified track manager: discovers and stores audio and subtitle tracks from Media3.
     */
    private val trackManager = TrackManager()

    /**
     * Applies audio and subtitle track selection. Does not prepare or reload.
     */
    private val mediaSelectionController = MediaSelectionController(exoPlayer, trackManager)

    /**
     * True until the first onTracksChanged for the current media (used for INITIAL reason).
     */
    private var firstTracksForCurrentMedia = true

    /**
     * Pending audio track ID to apply when seek completes. Null when no pending switch.
     */
    private var pendingAudioTrackId: String? = null

    /**
     * Pending subtitle track ID to apply when seek completes. Null when no pending switch.
     */
    private var pendingSubtitleTrackId: String? = null

    /**
     * Pending video track ID to apply when seek completes. Null when no pending switch.
     */
    private var pendingVideoTrackId: String? = null
    private var lastNotifiedVideoQuality: VideoTrack? = null

    /**
     * Whether a seek operation is currently in progress.
     */
    private var isSeeking = false

    /**
     * The playback position when the current seek operation started.
     */
    private var seekStartPositionMs: Long = 0L

    /**
     * Handler for dispatching time updates on the main thread.
     */
    private val timeUpdateHandler = Handler(Looper.getMainLooper())

    /**
     * Whether time updates are currently scheduled.
     */
    private var isTimeUpdateScheduled = false

    /**
     * When true, time updates are not dispatched (e.g. while user is scrubbing the progress bar).
     * Managed internally by [showPreview] / [hidePreview].
     */
    private var timeUpdatesPaused = false

    /**
     * Previous playback state to detect transitions.
     */
    private var previousPlaybackState: Int = Player.STATE_IDLE

    /**
     * Whether [PlaybackListener.onPlayerReady] has been fired for the current media item.
     * Reset when new media is set.
     */
    private var hasNotifiedPlayerReady: Boolean = false

    /** Whether the current media's first frame is on the surface. Reset when new media is set. */
    private var firstFrameRendered: Boolean = false

    /**
     * Sees playback errors before the app's listeners and may consume them (return true). Set by a
     * [io.fastpix.media3.playlist.FastPixPlayerPool] while this player pre-renders, so a failure of
     * speculative work never reaches the app as a playback error.
     */
    internal var errorInterceptor: ((PlaybackException) -> Boolean)? = null

    /**
     * Track if the player listener is currently attached to avoid duplicate listeners.
     */
    private var isListenerAttached = false

    /**
     * Stores the volume level before muting, so it can be restored when unmuting.
     * If null, the player was never muted or was unmuted to default (1.0f).
     */
    private var volumeBeforeMute: Float? = null

    /**
     * AudioManager instance for monitoring device volume changes.
     */
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Previous device volume level to detect changes.
     */
    private var previousDeviceVolumeLevel: Float = getDeviceVolumeLevel()

    /**
     * Runnable that periodically checks device volume changes.
     */
    private val volumeCheckRunnable = object : Runnable {
        override fun run() {
            if (isVolumeMonitoringActive) {
                checkDeviceVolumeChange()
                // Schedule next check
                timeUpdateHandler.postDelayed(this, DEFAULT_VOLUME_CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * Whether volume monitoring is currently active.
     */
    private var isVolumeMonitoringActive = false

    /**
     * Current playback speed index in AVAILABLE_PLAYBACK_SPEEDS array.
     * Default is 3 (index of 1.0f - normal speed).
     */
    private var currentPlaybackSpeedIndex: Int = 3

    /**
     * Scope for seek preview loading (runs when media is set). Only active when seek preview is enabled.
     */
    private val seekPreviewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Seek preview manager; non-null only when [seekPreviewEnabled] is true.
     */
    private val seekPreviewManager: SeekPreviewManager? = if (seekPreviewEnabled) {
        SeekPreviewManager.Companion.create(
            context,
            PlaybackUrlProvider { getCurrentPlaybackUrl() }).apply {
            seekPreviewConfig?.let { config -> setFallbackMode(config.fallbackMode) }
        }
    } else null

    /**
     * Runnable that dispatches time updates to all registered listeners.
     */
    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            // Pause time updates during seek operations or when app is scrubbing (e.g. finger on seek bar)
            if (isSeeking || timeUpdatesPaused) {
                if (timeUpdatesPaused) isTimeUpdateScheduled = false
                return
            }

            if (exoPlayer.isPlaying && playbackListeners.isNotEmpty()) {
                val currentPositionMs = exoPlayer.currentPosition
                val durationMs = if (exoPlayer.duration != C.TIME_UNSET) {
                    exoPlayer.duration
                } else {
                    C.TIME_UNSET
                }
                val bufferedPositionMs = exoPlayer.bufferedPosition

                // Dispatch time updates to all listeners
                playbackListeners.forEach { listener ->
                    listener.onTimeUpdate(currentPositionMs, durationMs, bufferedPositionMs)
                }

                pollVideoQualityChange()

                // Schedule next update if still playing and not paused
                if (exoPlayer.isPlaying && playbackListeners.isNotEmpty() && !isSeeking && !timeUpdatesPaused) {
                    timeUpdateHandler.postDelayed(this, DEFAULT_TIME_UPDATE_INTERVAL_MS)
                } else {
                    isTimeUpdateScheduled = false
                }
            } else {
                isTimeUpdateScheduled = false
            }
        }
    }


    /**
     * Sets a FastPix media item with a builder pattern.
     *
     * This method creates a MediaItem from a FastPix playback ID with optional
     * configuration parameters and sets it to the player.
     *
     * Example usage:
     * ```
     * playerView.setFastPixMediaItem {
     *     playbackId = "your-playback-id"
     *     maxResolution = PlaybackResolution.FHD_1080
     *     playbackToken = "your-token"
     * }
     * ```
     *
     * @param block The builder lambda to configure the media item.
     * @return true if the media item was successfully set, false if there was an error.
     */
    @UnstableApi
    fun setFastPixMediaItem(block: FastPixMediaItemBuilder.() -> Unit) {
        exoPlayer ?: return

        // Build the configuration
        val config = fastPixMediaItem(block)

        // Validate playback ID
        if (config.playbackId.isBlank()) {
            notifyPlayerError(
                PlaybackException(
                    "Playback ID is empty",
                    IllegalArgumentException(),
                    ERROR_CODE_EMPTY_PLAYBACK_ID
                )
            )
        }

        // Create playback URL
        val playbackUrl = FastPixMediaItems.playbackUrl(config)

        val mediaItemBuilder = MediaItem.Builder().setUri(
            playbackUrl
        ).setMimeType(MimeTypes.APPLICATION_M3U8)
            // Lets the cache key this asset's segments by playback ID, custom domain or not.
            .setTag(FastPixItemKeys.FastPixItemTag(config.playbackId))
        if(config.playbackToken != null) {
            val drmConfig = config.drmConfig
            if (drmConfig == null) {
                notifyPlayerError(
                    PlaybackException(
                        "Token is empty",
                        IllegalArgumentException(),
                        ERROR_CODE_DRM_LICENSE_URL_EMPTY
                    )
                )
            }
            val drmConfiguration =
                DrmManager.buildMediaItemDrmConfiguration(
                    drmConfig,
                    config.playbackId,
                    config.playbackToken,
                    config.streamType
                )
            if (drmConfiguration != null) {
                mediaItemBuilder.setDrmConfiguration(drmConfiguration)
            }
        }

        // Create and set the media item
        val mediaItem = mediaItemBuilder.build()
        try {
            setMediaItem(mediaItem)
        } catch (exception: Exception) {
            notifyPlayerError(
                PlaybackException(
                    "Failed to set media item",
                    exception,
                    ERROR_CODE_SET_MEDIA_ITEM_FAILED
                )
            )
        }
    }


    /**
     * Notifies the player listener of an error.
     */
    private fun notifyPlayerError(exception: PlaybackException) {
        playerListener.onPlayerError(exception)
    }

    /**
     * Internal player listener that maps Media3 events to PlaybackListener callbacks.
     */
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                playbackListeners.forEach { it.onPlay() }
                // Only start time updates if not seeking
                if (!isSeeking) {
                    startTimeUpdates()
                }
            } else {
                // Only call onPause if not ended or idle
                if (exoPlayer.playbackState != Player.STATE_ENDED &&
                    exoPlayer.playbackState != Player.STATE_IDLE
                ) {
                    playbackListeners.forEach { it.onPause() }
                }
                stopTimeUpdates()
            }
            // Notify listeners of the playing state change
            playbackListeners.forEach { it.onPlaybackStateChanged(isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            handleBufferingStateTransition(playbackState)
            notifyPlayerReadyIfNeeded(playbackState)
            previousPlaybackState = playbackState
            handlePlaybackEndedState(playbackState)
            completeSeekIfReady(playbackState)
        }

        override fun onRenderedFirstFrame() {
            hideBridge()
            if (firstFrameRendered) return
            firstFrameRendered = true
            prerenderer?.onPrimaryFirstFrame()
            playbackListeners.forEach { it.onFirstFrameRendered() }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
                DecoderBudget.onOpenFailed()
            }
            // A pre-rendering player's failure is its pool's business, not the app's.
            if (errorInterceptor?.invoke(error) == true) return
            playbackListeners.forEach { it.onError(error) }
            stopTimeUpdates()

            // Complete seek even on error to ensure cleanup
            if (isSeeking) {
                val finalPositionMs = exoPlayer.currentPosition
                val durationMs = if (exoPlayer.duration != C.TIME_UNSET) {
                    exoPlayer.duration
                } else {
                    0L
                }

                // Reset seeking state
                isSeeking = false

                // Notify playback listeners
                playbackListeners.forEach {
                    it.onSeekEnd(
                        seekStartPositionMs,
                        finalPositionMs,
                        durationMs
                    )
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // Handle seek operations entirely in the Player.Listener
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                if (isSeeking) {
                    // Complete the ongoing seek operation
                    val finalPositionMs = newPosition.positionMs
                    val durationMs = if (exoPlayer.duration != C.TIME_UNSET) {
                        exoPlayer.duration
                    } else {
                        0L
                    }

                    // Reset seeking state
                    isSeeking = false

                    // Notify playback listeners
                    playbackListeners.forEach {
                        it.onSeekEnd(
                            seekStartPositionMs,
                            finalPositionMs,
                            durationMs
                        )
                    }

                    // Apply any pending audio/subtitle track switch that was deferred during seek
                    applyPendingAudioTrackSwitch()
                    applyPendingSubtitleTrackSwitch()
                    applyPendingVideoTrackSwitch()

                    // Resume time updates if player is playing
                    if (exoPlayer.isPlaying && playbackListeners.isNotEmpty()) {
                        startTimeUpdates()
                    }
                } else {
                    // Start tracking a new seek operation
                    // This handles both programmatic seeks and built-in seek bar interactions
                    isSeeking = true
                    seekStartPositionMs = oldPosition.positionMs

                    // Pause time updates during seek
                    stopTimeUpdates()

                    // Notify playback listeners
                    playbackListeners.forEach { it.onSeekStart(seekStartPositionMs) }

                    // The seek will complete when player becomes ready or on next position discontinuity
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            trackManager.updateTracks(tracks)

            // Apply default audio/subtitle selection after tracks are updated, without overriding manual picks.
            // If we apply a change, Media3 will emit another onTracksChanged with the new selected state.
            if (applyDefaultTrackSelectionIfNeeded()) {
                return
            }

            val reason = if (firstTracksForCurrentMedia) {
                firstTracksForCurrentMedia = false
                AudioTrackUpdateReason.INITIAL
            } else {
                AudioTrackUpdateReason.TRACKS_UPDATED
            }
            val audioTracks = trackManager.getAudioTracks()
            audioTrackListeners.forEach { listener ->
                listener.onAudioTracksLoaded(audioTracks, reason)
            }
            val subtitleTracks = trackManager.getSubtitleTracks()
            subtitleTrackListeners.forEach { listener ->
                listener.onSubtitlesLoaded(subtitleTracks)
            }
            notifyVideoQualityChangedIfNeeded(PlaybackListener.VideoQualityChangeSource.ABR)
        }

        override fun onCues(cueGroup: CueGroup) {
            val cues = cueGroup.cues.map { cue ->
                val text = cue.text?.toString().orEmpty()
                val startUs = cueGroup.presentationTimeUs
                val startMs = if (startUs != C.TIME_UNSET) startUs / 1000 else 0L
                val endMs = startMs
                SubtitleCueInfo(text = text, startTimeMs = startMs, endTimeMs = endMs)
            }
            val info = SubtitleRenderInfo(cues = cues)
            subtitleTrackListeners.forEach { listener ->
                listener.onSubtitleCueChange(info)
            }
        }
    }

    private fun handleBufferingStateTransition(playbackState: Int) {
        val enteredBuffering =
            previousPlaybackState != Player.STATE_BUFFERING &&
                playbackState == Player.STATE_BUFFERING
        val exitedBufferingToReady =
            previousPlaybackState == Player.STATE_BUFFERING &&
                playbackState == Player.STATE_READY
        when {
            enteredBuffering -> {
                playbackListeners.forEach { it.onBufferingStart() }
                pollVideoQualityChange()
            }

            exitedBufferingToReady -> {
                playbackListeners.forEach { it.onBufferingEnd() }
                pollVideoQualityChange()
            }
        }
    }

    private fun notifyPlayerReadyIfNeeded(playbackState: Int) {
        if (playbackState != Player.STATE_READY || hasNotifiedPlayerReady) return
        hasNotifiedPlayerReady = true
        val durationMs = if (exoPlayer.duration != C.TIME_UNSET) exoPlayer.duration else C.TIME_UNSET
        playbackListeners.forEach { it.onPlayerReady(durationMs) }
    }

    private fun handlePlaybackEndedState(playbackState: Int) {
        if (playbackState != Player.STATE_ENDED) return
        stopTimeUpdates()
        playbackListeners.forEach { it.onCompleted() }
        advancePlaylistAfterEnd()
    }

    private fun completeSeekIfReady(playbackState: Int) {
        if (!isSeeking || playbackState != Player.STATE_READY) return
        val finalPositionMs = exoPlayer.currentPosition
        val durationMs = if (exoPlayer.duration != C.TIME_UNSET) exoPlayer.duration else 0L

        // Reset seeking state
        isSeeking = false

        // Notify playback listeners
        playbackListeners.forEach {
            it.onSeekEnd(
                seekStartPositionMs,
                finalPositionMs,
                durationMs
            )
        }

        // Apply any pending audio/subtitle track switch that was deferred during seek
        applyPendingAudioTrackSwitch()
        applyPendingSubtitleTrackSwitch()
        applyPendingVideoTrackSwitch()

        // Resume time updates if player is playing
        if (exoPlayer.isPlaying && playbackListeners.isNotEmpty()) {
            startTimeUpdates()
        }
    }

    /** Reports this player's video decoder to the process-wide [DecoderBudget]. */
    private val decoderBudgetListener = object : AnalyticsListener {
        override fun onVideoEnabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters,
        ) {
            DecoderBudget.onDecoderOpened(this@FastPixPlayer)
        }

        override fun onVideoDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters,
        ) {
            DecoderBudget.onDecoderClosed(this@FastPixPlayer)
        }
    }

    private val formatAnalyticsListener = object : AnalyticsListener {
        override fun onDownstreamFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            mediaLoadData: MediaLoadData
        ) {
            if (mediaLoadData.trackType != C.TRACK_TYPE_VIDEO) return
            val format = mediaLoadData.trackFormat ?: return
            val w = format.width.takeIf { it != Format.NO_VALUE } ?: return
            val h = format.height.takeIf { it != Format.NO_VALUE } ?: return
            trackManager.updateRenderedVideoSize(w, h)
            notifyVideoQualityChangedIfNeeded(PlaybackListener.VideoQualityChangeSource.ABR)
        }
    }

    /**
     * Observes the device's active network and classifies it into a [io.fastpix.media3.abr.NetworkType].
     * Shared with [abrController] which reacts to type changes.
     */
    private val networkMonitor: NetworkMonitor = NetworkMonitor(
        context = context,
        cellularHighMinKbps = abrConfig.cellularHighMinKbps,
        cellularMediumMinKbps = abrConfig.cellularMediumMinKbps,
    )

    /**
     * Applies per-network-type `maxVideoBitrate` caps to [trackSelector] and forces re-selection
     * on downgrade. Within-network rendition switching is still handled by Media3's own
     * [AdaptiveTrackSelection] under the ceiling we set.
     */
    private val abrController: NetworkAwareAbrController = NetworkAwareAbrController(
        player = exoPlayer,
        trackSelector = trackSelector,
        networkMonitor = networkMonitor,
        config = abrConfig,
        onCapApplied = { bps -> applyCapToPreloadSelectors(bps) },
    )

    /** Track selectors the preload manager picks renditions with; empty without preloading. */
    private val preloadTrackSelectors: List<DefaultTrackSelector> =
        preloadSetup?.preloadTrackSelectors ?: sharedPreloadTrackSelectors

    /**
     * Prepares playlist entries around the current one; null unless preloading was configured.
     * With the cache on, entries beyond the adjacent ones are also warmed to disk in the rendition
     * this player would pick.
     */
    private val playlistPreloader: PlaylistPreloader? = preloadSetup?.let { setup ->
        val diskWarmer = setup.cache?.let { cache ->
            @Suppress("DEPRECATION")
            val warmer = io.fastpix.media3.cache.FastPixPreCacher.forPlayer(context, cache, setup.cacheConfig) {
                preloadTargetBitrate()
            }
            object : PlaylistPreloader.DiskWarmer {
                override fun warm(mediaItems: List<MediaItem>) = warmer.preCacheMediaItems(mediaItems)
                override fun cancelAll() = warmer.cancelAll()
                override fun release() = warmer.release()
            }
        }
        PlaylistPreloader(setup.preloadManager, setup.policy, diskWarmer)
    }

    /** Captures upcoming entries' first frames; null unless pre-rendering was configured. */
    private val prerenderer: SingleViewPrerenderer? = preloadSetup?.prerenderer

    /** The view showing this player, while one does and pre-rendering is on. */
    private var prerenderHost: PrerenderSurfaceHost? = null

    /** Whether a captured frame is covering the video, and the timeout that will lift it. */
    private var bridgeShown = false
    private val bridgeTimeout = Runnable { hideBridge() }

    /** Whether this player pre-renders, and so needs a view to capture frames through. */
    internal val prerendersThroughView: Boolean get() = prerenderer != null

    /**
     * Called by [io.fastpix.media3.PlayerView] as it starts or stops showing this player. Only the
     * latest view is used: a player shown in two views captures through the second.
     */
    internal fun setPrerenderHost(host: PrerenderSurfaceHost?) {
        if (host == null && prerenderHost == null) return
        if (host == null) hideBridge()
        prerenderHost = host
        prerenderer?.setHost(host)
    }

    /** Releases the host if it is [host] — a view detaching must not unset its successor. */
    internal fun clearPrerenderHost(host: PrerenderSurfaceHost) {
        if (prerenderHost === host) setPrerenderHost(null)
    }

    private fun showBridge(frame: android.graphics.Bitmap) {
        val host = prerenderHost ?: return
        host.showBridge(frame)
        bridgeShown = true
        timeUpdateHandler.removeCallbacks(bridgeTimeout)
        // A frame left up after the video should have started reads as a hang; take it down.
        timeUpdateHandler.postDelayed(bridgeTimeout, BRIDGE_TIMEOUT_MS)
    }

    private fun hideBridge() {
        if (!bridgeShown) return
        bridgeShown = false
        timeUpdateHandler.removeCallbacks(bridgeTimeout)
        prerenderHost?.hideBridge()
    }

    /** Keeps the preload manager's rendition choice under the same cap as playback. */
    private fun applyCapToPreloadSelectors(maxVideoBitrateBps: Int) {
        for (selector in preloadTrackSelectors) {
            selector.setParameters(
                selector.buildUponParameters().setMaxVideoBitrate(maxVideoBitrateBps)
            )
        }
    }

    /**
     * The bitrate this player would pick for an entry starting now: its bandwidth estimate, scaled
     * the way its adaptive selection scales it, under any network cap in force. Entries warmed to
     * disk are warmed in that rendition, so playback finds them.
     */
    internal fun preloadTargetBitrate(): Int {
        val estimate = (bandwidthMeter.bitrateEstimate * abrConfig.bandwidthFraction).toLong()
        val cap = trackSelector.parameters.maxVideoBitrate
        return minOf(estimate, cap.toLong()).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Emits [ERROR_CODE_NETWORK_STALL_TIMEOUT] via the normal error path when the player stays in
     * `STATE_BUFFERING` longer than [AbrConfig.playbackStallTimeoutMs]. Fires at most once per
     * buffering episode; re-arms on the next BUFFERING entry.
     */
    private val stallWatchdog: PlaybackStallWatchdog = PlaybackStallWatchdog(
        player = exoPlayer,
        timeoutMs = abrConfig.playbackStallTimeoutMs,
    ) { stallDurationMs, positionMs ->
        notifyPlayerError(
            PlaybackException(
                "Network too slow to sustain playback (stalled ${stallDurationMs}ms at ${positionMs}ms)",
                null,
                ERROR_CODE_NETWORK_STALL_TIMEOUT,
            )
        )
    }

    init {
        // Initialize loop and autoplay from constructor parameters
        // Apply these settings immediately during initialization
        // Set the backing field directly and configure ExoPlayer
        loop = initialLoop
        autoplay = initialAutoplay

        // Initialize previous playback state
        previousPlaybackState = exoPlayer.playbackState

        // Attach listener
        attachPlayerListener()

        // Initialize device volume level
        previousDeviceVolumeLevel = getDeviceVolumeLevel()

        // Initialize playback speed to normal (1.0x)
        normalize()

        // Analytics views begin per video, as media is loaded — see beginAnalyticsView.

        // Start the network-aware ABR pipeline. Register the monitor first so the controller's
        // addListener callback sees a non-UNKNOWN initial state.
        networkMonitor.register()
        abrController.attach()
        stallWatchdog.attach()
    }

    /**
     * Attaches the player listener, ensuring no duplicate listeners.
     */
    private fun attachPlayerListener() {
        if (!isListenerAttached) {
            exoPlayer.removeListener(playerListener)
            exoPlayer.removeAnalyticsListener(formatAnalyticsListener)
            exoPlayer.addListener(playerListener)
            exoPlayer.addAnalyticsListener(formatAnalyticsListener)
            exoPlayer.addAnalyticsListener(decoderBudgetListener)
            isListenerAttached = true
        }
    }

    /**
     * Detaches the player listener.
     */
    private fun detachPlayerListener() {
        if (isListenerAttached) {
            exoPlayer.removeListener(playerListener)
            exoPlayer.removeAnalyticsListener(formatAnalyticsListener)
            exoPlayer.removeAnalyticsListener(decoderBudgetListener)
            isListenerAttached = false
        }
    }

    /**
     * Gets the underlying ExoPlayer instance.
     * This allows advanced users to access ExoPlayer APIs directly if needed.
     *
     * @return The ExoPlayer instance.
     */
    fun getExoPlayer(): ExoPlayer = exoPlayer

    /**
     * Gets the current playback URL (stream URI) for the loaded media item.
     * Works for both playback sources: FastPix (playbackId) and direct URL.
     *
     * - When using [setFastPixMediaItem], returns the resolved stream URL (e.g. https://stream.fastpix.com/{playbackId}.m3u8).
     * - When using [setMediaItem] with a URI, returns that URI.
     *
     * @return The current media URI as a string, or null if no media is loaded.
     */
    fun getCurrentPlaybackUrl(): String? {
        val mediaItem = exoPlayer.currentMediaItem ?: return null
        return mediaItem.localConfiguration?.uri?.toString()
    }

    // --------------- Seek preview API (when setSeekPreviewEnabled(true)) ---------------

    /**
     * Sets the listener for seek preview events (loaded, failed, show, hide).
     * Only used when seek preview was enabled in the builder.
     *
     * @param listener Listener instance, or null to clear.
     */
    fun setSeekPreviewListener(listener: SeekPreviewListener?) {
        seekPreviewManager?.setListener(listener)
    }

    /**
     * Returns a preview bitmap for the given position, or null if not available.
     * Call from a coroutine (e.g. when the user drags the seek bar).
     *
     * @param timeMs Position in milliseconds.
     * @return Bitmap for that position, or null.
     */
    suspend fun getPreviewBitmap(timeMs: Long): Bitmap? {
        return seekPreviewManager?.getPreviewBitmap(timeMs)
    }

    /**
     * Formats a time position as "MM:SS" for timestamp fallback.
     *
     * @param timeMs Position in milliseconds.
     * @return Formatted string.
     */
    fun formatTimestamp(timeMs: Long): String {
        return seekPreviewManager?.formatTimestamp(timeMs) ?: "00:00"
    }

    /**
     * Call when the user starts dragging the seek bar (e.g. onStartTrackingTouch).
     * Automatically pauses time updates so the seek bar is not overwritten by playback position,
     * and notifies the seek preview listener via [SeekPreviewListener.onPreviewShow].
     */
    fun showPreview() {
        setTimeUpdatesPaused(true)
        seekPreviewManager?.showPreview()
    }

    /**
     * Loads the seek preview for the given position.
     * Fetches the bitmap on a background thread and delivers the result via
     * [SeekPreviewListener.onSpritesheetLoaded]. Automatically cancels any pending
     * request from a previous call, so it's safe to call from onProgressChanged.
     *
     * @param timeMs Position in milliseconds.
     */
    fun loadPreview(timeMs: Long) {
        seekPreviewManager?.loadPreview(timeMs)
    }

    /**
     * Call when the user stops dragging the seek bar (e.g. onStopTrackingTouch).
     * Cancels any pending preview update, resumes time updates, and notifies the listener via
     * [SeekPreviewListener.onPreviewHide].
     */
    fun hidePreview() {
        seekPreviewManager?.hidePreview()
        setTimeUpdatesPaused(false)
    }

    /**
     * Sets fallback behavior when thumbnails are unavailable.
     * [io.fastpix.player.seekpreview.models.PreviewFallbackMode.TIMESTAMP] shows a time label (e.g. "02:30"); [io.fastpix.player.seekpreview.models.PreviewFallbackMode.NONE] shows nothing.
     *
     * @param mode Fallback mode. Default is [io.fastpix.player.seekpreview.models.PreviewFallbackMode.TIMESTAMP].
     */
    fun setFallbackMode(mode: PreviewFallbackMode) {
        seekPreviewManager?.setFallbackMode(mode)
    }

    /**
     * Sets a media item to play.
     *
     * @param mediaItem The media item to set.
     */
    fun setMediaItem(mediaItem: MediaItem) {
        // A single item replaces any playlist.
        clearPlaylistState()

        // Check if player already has media items and is in a valid state
        val currentMediaItemCount = exoPlayer.mediaItemCount
        if (currentMediaItemCount > 0) {
            // Player already has media items - check if it's the same item
            val currentMediaItem = exoPlayer.currentMediaItem
            if (currentMediaItem != null) {
                val currentMediaId = currentMediaItem.mediaId
                val currentUri = currentMediaItem.localConfiguration?.uri
                val currentDrm = currentMediaItem.localConfiguration?.drmConfiguration
                val newMediaId = mediaItem.mediaId
                val newUri = mediaItem.localConfiguration?.uri
                val newDrm = mediaItem.localConfiguration?.drmConfiguration

                // Compare media ID and URI.
                val mediaIdMatches = currentMediaId == newMediaId
                val uriMatches = (currentUri == null && newUri == null) ||
                        (currentUri != null && currentUri == newUri)
                val drmMatches = currentDrm == newDrm

                if (mediaIdMatches && uriMatches && drmMatches) {
                    // Same media item already set - don't reset playback state
                    return
                }
            }
        }

        // New or different media item - set it and prepare
        resetStateForNewMedia()
        beginAnalyticsView(analyticsConfig?.videoDataDetails)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        triggerSeekPreviewLoadIfEnabled()
    }

    /**
     * Sets multiple media items to play.
     *
     * @param mediaItems The list of media items to set.
     * @param startIndex The index of the item to start playing from.
     * @param startPositionMs The position in milliseconds to start from.
     */
    @Deprecated(
        message = "Use setPlaylist, which adds navigation (next, previous, skipTo), editing and " +
                "PlaylistListener callbacks.",
        replaceWith = ReplaceWith(
            "setPlaylist(mediaItems.map { PlaylistItem.fromMediaItem(it) }, startIndex, startPositionMs)",
            "io.fastpix.media3.playlist.PlaylistItem",
        ),
    )
    fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0
    ) {
        // Same items already queued: keep playback where it is, as before 2.2.0.
        val current = playlist.snapshot()
        if (current.isNotEmpty() && current.size == mediaItems.size &&
            current.indices.all { current[it].mediaItem == mediaItems[it] }
        ) {
            return
        }
        setPlaylist(mediaItems.map { PlaylistItem.fromMediaItem(it) }, startIndex, startPositionMs)
    }

    /** Resets per-media state before a different item is loaded. */
    private fun resetStateForNewMedia() {
        hasNotifiedPlayerReady = false
        firstFrameRendered = false
        firstTracksForCurrentMedia = true
        pendingAudioTrackId = null
        pendingSubtitleTrackId = null
        pendingVideoTrackId = null
        lastNotifiedVideoQuality = null
        trackManager.resetSelectionStateForNewMedia()
    }

    // --------------- Playlist API ---------------

    /**
     * Replaces whatever is loaded with [items] and starts loading the entry at [startIndex].
     *
     * The player then moves to the next entry by itself when one finishes, unless [loop] is on,
     * which repeats the current entry. Navigate with [next], [previous] and [skipTo]; edit with
     * [addToPlaylist] and [removeFromPlaylist]; observe with [addPlaylistListener].
     *
     * An empty list clears the playlist and stops playback. Call from the main thread.
     *
     * @param items entries to play, in order.
     * @param startIndex entry to start from.
     * @param startPositionMs position within that entry to start from.
     * @throws IllegalArgumentException if [startIndex] is out of range for a non-empty [items].
     */
    @JvmOverloads
    fun setPlaylist(items: List<PlaylistItem>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        playlist.set(items, startIndex)
        playlistPreloader?.setPlaylist(items)
        prerenderer?.setPlaylist(items)
        notifyPlaylistChanged()
        if (playlist.isEmpty()) {
            unloadMedia()
        } else {
            loadCurrentPlaylistItem(startPositionMs, PlaylistItemChangeReason.PLAYLIST_SET)
        }
    }

    /** Appends [item] to the end of the playlist. */
    fun addToPlaylist(item: PlaylistItem) {
        addToPlaylist(playlist.size, listOf(item))
    }

    /** Appends [items] to the end of the playlist. */
    fun addToPlaylist(items: List<PlaylistItem>) {
        addToPlaylist(playlist.size, items)
    }

    /**
     * Inserts [items] at [index]; entries from [index] onwards shift back. The current entry keeps
     * playing. Adding to an empty playlist starts loading the first added entry.
     *
     * @throws IllegalArgumentException if [index] is not in `0..playlist size`.
     */
    fun addToPlaylist(index: Int, items: List<PlaylistItem>) {
        val appended = index == playlist.size
        val currentChanged = playlist.add(index, items)
        if (items.isEmpty()) return
        // An append keeps every existing entry's position, and so everything already preloaded.
        // An insert shifts positions, so the preload window is rebuilt.
        prerenderer?.setPlaylist(playlist.snapshot())
        if (appended) {
            playlistPreloader?.append(index, items)
        } else {
            playlistPreloader?.setPlaylist(playlist.snapshot())
            if (!currentChanged) playlistPreloader?.onCurrentIndexChanged(playlist.currentIndex)
        }
        notifyPlaylistChanged()
        if (currentChanged) {
            loadCurrentPlaylistItem(0L, PlaylistItemChangeReason.PLAYLIST_EDITED)
        }
    }

    /**
     * Removes the entry at [index]. Removing the current entry loads the one that takes its place
     * (the new last entry, if it was last); removing the only entry stops playback.
     *
     * @throws IllegalArgumentException if [index] is out of range.
     */
    fun removeFromPlaylist(index: Int) {
        val currentRemoved = playlist.removeAt(index)
        playlistPreloader?.setPlaylist(playlist.snapshot())
        prerenderer?.setPlaylist(playlist.snapshot())
        if (!currentRemoved && !playlist.isEmpty()) {
            playlistPreloader?.onCurrentIndexChanged(playlist.currentIndex)
        }
        notifyPlaylistChanged()
        when {
            playlist.isEmpty() -> unloadMedia()
            currentRemoved -> loadCurrentPlaylistItem(0L, PlaylistItemChangeReason.PLAYLIST_EDITED)
        }
    }

    /** Removes every entry and stops playback. */
    fun clearPlaylist() {
        if (playlist.isEmpty()) return
        playlist.clear()
        playlistPreloader?.clear()
        prerenderer?.setPlaylist(emptyList())
        notifyPlaylistChanged()
        unloadMedia()
    }

    /**
     * Moves to the next entry and starts loading it from the beginning.
     *
     * @return false, doing nothing, when already at the last entry.
     */
    fun next(): Boolean {
        if (!playlist.next()) return false
        loadCurrentPlaylistItem(0L, PlaylistItemChangeReason.NAVIGATION)
        return true
    }

    /**
     * Moves to the previous entry and starts loading it from the beginning. To restart the current
     * entry instead, use `seekTo(0)`.
     *
     * @return false, doing nothing, when already at the first entry.
     */
    fun previous(): Boolean {
        if (!playlist.previous()) return false
        loadCurrentPlaylistItem(0L, PlaylistItemChangeReason.NAVIGATION)
        return true
    }

    /**
     * Makes the entry at [index] current and starts loading it at [positionMs]. Reloads it even if
     * it is already current, which restarts it.
     *
     * @throws IllegalArgumentException if [index] is out of range.
     */
    @JvmOverloads
    fun skipTo(index: Int, positionMs: Long = 0L) {
        playlist.moveTo(index)
        loadCurrentPlaylistItem(positionMs, PlaylistItemChangeReason.NAVIGATION)
    }

    /** Whether there is an entry after the current one. */
    fun hasNext(): Boolean = playlist.hasNext()

    /** Whether there is an entry before the current one. */
    fun hasPrevious(): Boolean = playlist.hasPrevious()

    /** The playlist's entries, in order; empty when no playlist is set. */
    fun getPlaylist(): List<PlaylistItem> = playlist.snapshot()

    /** Position of the current entry, or -1 when no playlist is set. */
    fun getCurrentIndex(): Int = playlist.currentIndex

    /** The current entry, or null when no playlist is set. */
    fun getCurrentItem(): PlaylistItem? = playlist.current

    /**
     * Whether the current media's first video frame is on the surface — true for a pre-rendered
     * item before it starts playing. See [PlaybackListener.onFirstFrameRendered].
     */
    fun hasRenderedFirstFrame(): Boolean = firstFrameRendered

    /**
     * Loads [item] for a [io.fastpix.media3.playlist.FastPixPlayerPool] page without starting it:
     * from [source] when the pool preloaded one, and prepared only when [prepare] — preparing opens
     * a video decoder, which the pool budgets.
     */
    internal fun loadForPool(item: PlaylistItem, source: MediaSource?, prepare: Boolean) {
        // A reused pool player's view belonged to its previous entry.
        endAnalyticsView()
        clearPlaylistState()
        resetStateForNewMedia()
        exoPlayer.playWhenReady = false
        if (source != null) exoPlayer.setMediaSource(source) else exoPlayer.setMediaItem(item.mediaItem)
        if (prepare) exoPlayer.prepare()
        triggerSeekPreviewLoadIfEnabled()
    }

    /**
     * How many attached [io.fastpix.media3.PlayerView]s are showing this player right now. A
     * [io.fastpix.media3.playlist.FastPixPlayerPool] never hands a displayed player to another
     * page: the page still showing it would be left with a player playing something else.
     */
    private var attachedViewCount = 0

    internal val isDisplayedByView: Boolean get() = attachedViewCount > 0

    /** Called by [io.fastpix.media3.PlayerView] as it starts or stops showing this player. */
    internal fun onViewDisplayChanged(view: io.fastpix.media3.PlayerView, displayed: Boolean) {
        attachedViewCount = if (displayed) attachedViewCount + 1 else maxOf(0, attachedViewCount - 1)
        if (displayed) {
            displayingView = view
            if (pendingAnalyticsView) beginAnalyticsView(pendingAnalyticsVideoData)
        } else if (displayingView === view) {
            displayingView = null
        }
    }

    // --------------- Analytics views ---------------

    /**
     * Begins a FastPix Data view for the video about to play, ending any view still open — this
     * player's or another's (see [AnalyticsSessions]). Deferred until a
     * [io.fastpix.media3.PlayerView] shows the player when the config names none.
     */
    private fun beginAnalyticsView(videoData: VideoDataDetails?) {
        val config = analyticsConfig?.takeIf { it.enabled } ?: return
        val view = (config.playerView ?: displayingView)?.media3PlayerView
        if (view == null) {
            AnalyticsSessions.endFor(this)
            pendingAnalyticsView = true
            pendingAnalyticsVideoData = videoData
            return
        }
        pendingAnalyticsView = false
        pendingAnalyticsVideoData = null
        AnalyticsSessions.begin(this, AnalyticsManager(context, exoPlayer, config, view, videoData))
    }

    /** Ends this player's analytics view, if one is open or waiting for a view. */
    private fun endAnalyticsView() {
        pendingAnalyticsView = false
        pendingAnalyticsVideoData = null
        AnalyticsSessions.endFor(this)
    }

    /** Begins the analytics view for [item], made current by a pool. */
    internal fun beginAnalyticsViewForPool(item: PlaylistItem) {
        beginAnalyticsView(videoDataFor(item))
    }

    /** Ends the analytics view of a pool page the user has left. */
    internal fun endAnalyticsViewForPool() {
        endAnalyticsView()
    }

    /** An entry's own metadata, else the config's, else just its id. */
    private fun videoDataFor(item: PlaylistItem): VideoDataDetails =
        item.videoDataDetails
            ?: analyticsConfig?.videoDataDetails
            ?: VideoDataDetails(videoId = item.id)

    /** Whether media is prepared or preparing, as opposed to idle. */
    internal val isPreparedForPool: Boolean
        get() = exoPlayer.playbackState != Player.STATE_IDLE

    /** Prepares loaded media that [loadForPool] left idle. */
    internal fun prepareForPool() {
        if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0) {
            exoPlayer.prepare()
        }
    }

    /** Stops decoding and loading, keeping the media item so [prepareForPool] can resume it. */
    internal fun stopForPool() {
        endAnalyticsView()
        exoPlayer.stop()
        firstFrameRendered = false
    }

    /** Registers [listener] for playlist changes. Adding the same listener twice has no effect. */
    fun addPlaylistListener(listener: PlaylistListener) {
        if (listener !in playlistListeners) playlistListeners.add(listener)
    }

    fun removePlaylistListener(listener: PlaylistListener) {
        playlistListeners.remove(listener)
    }

    /**
     * Loads the current playlist entry. Unlike [setMediaItem] this always reloads, even when the
     * same media is already loaded: a playlist may list one item twice, and moving between the two
     * copies must restart it.
     */
    private fun loadCurrentPlaylistItem(positionMs: Long, reason: PlaylistItemChangeReason) {
        val item = playlist.current ?: return
        val index = playlist.currentIndex
        resetStateForNewMedia()
        // Each entry watched is its own analytics view, begun before loading so no event is missed.
        beginAnalyticsView(videoDataFor(item))
        // A pre-rendered entry shows its captured first frame until this player's own lands.
        val frame = prerenderer?.frameFor(item)
        if (frame != null && positionMs == 0L) showBridge(frame) else hideBridge()
        // A preloaded source carries whatever was already fetched and buffered for this entry.
        val preloaded = playlistPreloader?.mediaSourceFor(item)
        if (preloaded != null) {
            exoPlayer.setMediaSource(preloaded, positionMs)
        } else {
            exoPlayer.setMediaItem(item.mediaItem, positionMs)
        }
        exoPlayer.prepare()
        playlistPreloader?.onCurrentIndexChanged(index)
        prerenderer?.onCurrentIndexChanged(index)
        triggerSeekPreviewLoadIfEnabled()
        playlistListeners.toList().forEach { it.onPlaylistItemChanged(index, item, reason) }
    }

    /** Moves on after the current entry ends, when there is somewhere to move to. */
    private fun advancePlaylistAfterEnd() {
        if (loop || !playlist.next()) return
        loadCurrentPlaylistItem(0L, PlaylistItemChangeReason.AUTO_ADVANCE)
    }

    /** Stops and unloads media after the playlist became empty. */
    private fun unloadMedia() {
        endAnalyticsView()
        resetStateForNewMedia()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    /** Drops the playlist when single-item APIs take over the player. Leaves the media alone. */
    private fun clearPlaylistState() {
        if (playlist.isEmpty()) return
        playlist.clear()
        playlistPreloader?.clear()
        prerenderer?.setPlaylist(emptyList())
        notifyPlaylistChanged()
    }

    private fun notifyPlaylistChanged() {
        val items = playlist.snapshot()
        playlistListeners.toList().forEach { it.onPlaylistChanged(items) }
    }

    /**
     * Triggers sprite sheet load when seek preview is enabled. Called after media is set.
     */
    private fun triggerSeekPreviewLoadIfEnabled() {
        if (!seekPreviewEnabled || seekPreviewManager == null || seekPreviewConfig == null) return
        seekPreviewScope.launch {
            seekPreviewManager.loadSpritesheet(
                previewEnable = true,
                config = seekPreviewConfig
            ).collect { /* progress can be observed via SeekPreviewListener */ }
        }
    }

    /**
     * Starts or resumes playback.
     */
    fun play() {
        exoPlayer.play()
    }

    /**
     * Pauses playback.
     */
    fun pause() {
        exoPlayer.pause()
    }

    /**
     * Toggles between play and pause states.
     */
    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    /**
     * Returns whether the player is currently playing.
     *
     * @return true if playing, false otherwise.
     */
    fun isPlaying(): Boolean {
        return exoPlayer.isPlaying
    }

    /**
     * Seeks to a specific position in the current media item.
     *
     * Seek tracking and callbacks are handled automatically by the Player.Listener.
     *
     * @param positionMs The position in milliseconds.
     */
    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    /**
     * Gets the current playback position.
     *
     * @return The current position in milliseconds.
     */
    fun getCurrentPosition(): Long {
        return exoPlayer.currentPosition
    }

    /**
     * Gets the duration of the current media item.
     *
     * @return The duration in milliseconds, or 0 if unknown.
     */
    fun getDuration(): Long {
        return exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0L
    }

    /**
     * Gets the current playback state.
     *
     * @return One of [Player.STATE_IDLE], [Player.STATE_BUFFERING],
     *   [Player.STATE_READY], or [Player.STATE_ENDED].
     */
    fun getPlaybackState(): Int {
        return exoPlayer.playbackState
    }

    /**
     * Sets whether playback should automatically start when ready.
     *
     * @param playWhenReady Whether to play when ready.
     */
    fun setPlayWhenReady(playWhenReady: Boolean) {
        exoPlayer.playWhenReady = playWhenReady
    }

    /**
     * Gets whether playback will automatically start when ready.
     *
     * @return true if will play when ready, false otherwise.
     */
    fun getPlayWhenReady(): Boolean {
        return exoPlayer.playWhenReady
    }

    /**
     * Mutes the player by setting volume to 0.
     * The current volume level is saved and can be restored with [unmute].
     */
    fun mute() {
        val currentVolume = exoPlayer.volume
        if (currentVolume > 0f) {
            volumeBeforeMute = currentVolume
        }
        exoPlayer.volume = 0f
    }

    /**
     * Unmutes the player by restoring the volume level that was set before muting.
     * If no previous volume was saved (player was never muted or was set to 0 directly),
     * the volume is set to 1.0 (maximum).
     */
    fun unmute() {
        val volumeToRestore = volumeBeforeMute ?: 1.0f
        exoPlayer.volume = volumeToRestore
        volumeBeforeMute = null
    }

    /**
     * Sets the volume level for playback.
     *
     * @param volume The volume level, where 0.0f is muted and 1.0f is maximum volume.
     *               Values are clamped to the range [0.0f, 1.0f].
     */
    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        exoPlayer.volume = clampedVolume
        // Clear saved volume if we're setting a non-zero volume (not muted)
        if (clampedVolume > 0f) {
            volumeBeforeMute = null
        }
    }

    /**
     * Gets the current volume level.
     *
     * @return The current volume level, where 0.0f is muted and 1.0f is maximum volume.
     */
    fun getVolume(): Float {
        return exoPlayer.volume
    }

    /**
     * Sets the playback speed using the specified index in the available speeds array.
     * This is an internal helper method to avoid redundant lookups.
     */
    private fun setPlaybackSpeedByIndex(index: Int) {
        val clampedIndex = index.coerceIn(0, AVAILABLE_PLAYBACK_SPEEDS.size - 1)
        currentPlaybackSpeedIndex = clampedIndex
        val targetSpeed = AVAILABLE_PLAYBACK_SPEEDS[clampedIndex]
        // Set playback parameters with speed and pitch (pitch = 1.0f for normal pitch)
        val currentParams = exoPlayer.playbackParameters
        val newParams = PlaybackParameters(targetSpeed, currentParams.pitch)
        exoPlayer.playbackParameters = newParams
        playbackListeners.forEach { listener ->
            listener.onPlaybackRateChanged(targetSpeed)
        }
    }

    /**
     * Increases the playback speed to the next available speed.
     * If already at maximum speed (2.0x), it wraps around to the minimum speed (0.25x).
     */
    fun fast() {
        val nextIndex = (currentPlaybackSpeedIndex + 1) % AVAILABLE_PLAYBACK_SPEEDS.size
        setPlaybackSpeedByIndex(nextIndex)
    }

    /**
     * Decreases the playback speed to the previous available speed.
     * If already at minimum speed (0.25x), it wraps around to the maximum speed (2.0x).
     */
    fun slow() {
        val previousIndex = if (currentPlaybackSpeedIndex == 0) {
            AVAILABLE_PLAYBACK_SPEEDS.size - 1
        } else {
            currentPlaybackSpeedIndex - 1
        }
        setPlaybackSpeedByIndex(previousIndex)
    }

    /**
     * Resets the playback speed to normal (1.0x).
     */
    fun normalize() {
        val normalSpeedIndex =
            AVAILABLE_PLAYBACK_SPEEDS.indexOfFirst { it == NORMAL_PLAYBACK_SPEED }
        val targetIndex =
            if (normalSpeedIndex >= 0) normalSpeedIndex else 3 // Default to index 3 (1.0f)
        setPlaybackSpeedByIndex(targetIndex)
    }

    /**
     * Sets the playback speed to a specific value.
     * If the provided speed is not in the available speeds list, it will be set to the closest available speed.
     *
     * @param speed The playback speed to set. Must be one of the available speeds: 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0
     */
    fun setPlaybackSpeed(speed: Float) {
        // Find the closest available speed
        var closestIndex = 0
        var minDifference = Float.MAX_VALUE

        for (i in AVAILABLE_PLAYBACK_SPEEDS.indices) {
            val difference = abs(AVAILABLE_PLAYBACK_SPEEDS[i] - speed)
            if (difference < minDifference) {
                minDifference = difference
                closestIndex = i
            }
        }

        setPlaybackSpeedByIndex(closestIndex)
    }

    /**
     * Gets the current playback speed.
     *
     * @return The current playback speed (e.g., 1.0f for normal speed, 1.5f for 1.5x speed).
     */
    fun getPlaybackSpeed(): Float {
        return exoPlayer.playbackParameters.speed
    }

    /**
     * Gets all available playback speeds.
     * This is useful for building UI components that allow users to select a playback speed.
     *
     * @return An array of all available playback speeds: [0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0]
     */
    fun getAvailablePlaybackSpeeds(): FloatArray {
        return AVAILABLE_PLAYBACK_SPEEDS.copyOf()
    }

    /**
     * Adds a playback listener to receive playback events and time updates.
     *
     * The listener will receive all playback events including:
     * - Play/pause state changes
     * - Playback errors
     * - Continuous time updates during active playback (if [PlaybackListener.onTimeUpdate] is overridden)
     * - Device volume changes (if [PlaybackListener.onVolumeChanged] is overridden)
     *
     * @param listener The listener to add.
     */
    fun addPlaybackListener(listener: PlaybackListener) {
        if (!playbackListeners.contains(listener)) {
            playbackListeners.add(listener)
            // Start time updates if player is currently playing
            if (exoPlayer.isPlaying) {
                startTimeUpdates()
            }
            // Start volume monitoring if not already active
            startVolumeMonitoring()
        }
    }

    /**
     * Removes a playback listener.
     *
     * @param listener The listener to remove.
     */
    fun removePlaybackListener(listener: PlaybackListener) {
        playbackListeners.remove(listener)
        // Stop time updates if no listeners remain
        if (playbackListeners.isEmpty()) {
            stopTimeUpdates()
        }
        // Stop volume monitoring if no listeners remain
        if (playbackListeners.isEmpty()) {
            stopVolumeMonitoring()
        }
    }

    /**
     * Clears all playback listeners.
     */
    fun clearPlaybackListeners() {
        playbackListeners.clear()
        stopTimeUpdates()
        stopVolumeMonitoring()
    }

    /**
     * Completes any ongoing seek operation.
     * This is useful when the player is being detached or released.
     */
    fun completeSeekIfInProgress() {
        if (isSeeking) {
            val finalPositionMs = exoPlayer.currentPosition
            val durationMs = if (exoPlayer.duration != C.TIME_UNSET) {
                exoPlayer.duration
            } else {
                0L
            }

            // Reset seeking state
            isSeeking = false

            // Notify playback listeners
            playbackListeners.forEach {
                it.onSeekEnd(
                    seekStartPositionMs,
                    finalPositionMs,
                    durationMs
                )
            }
        }
    }

    /**
     * Starts periodic time updates if not already started.
     * Updates are only dispatched while playback is active and listeners are registered.
     */
    private fun startTimeUpdates() {
        if (!isTimeUpdateScheduled && playbackListeners.isNotEmpty() && exoPlayer.isPlaying && !timeUpdatesPaused) {
            isTimeUpdateScheduled = true
            timeUpdateHandler.post(timeUpdateRunnable)
        }
    }

    /**
     * Pauses or resumes periodic time updates (e.g. [PlaybackListener.onTimeUpdate]).
     * Called internally by [showPreview] and [hidePreview] so the seek bar is not
     * overwritten by playback position while the user is scrubbing.
     *
     * @param paused true to stop time updates, false to resume (and start again if playing)
     */
    private fun setTimeUpdatesPaused(paused: Boolean) {
        if (timeUpdatesPaused == paused) return
        timeUpdatesPaused = paused
        if (!paused && playbackListeners.isNotEmpty() && exoPlayer.isPlaying) {
            startTimeUpdates()
        }
    }

    /**
     * Stops periodic time updates.
     */
    private fun stopTimeUpdates() {
        if (isTimeUpdateScheduled) {
            isTimeUpdateScheduled = false
            timeUpdateHandler.removeCallbacks(timeUpdateRunnable)
        }
    }

    /**
     * Gets the current device volume level as a normalized value (0.0f to 1.0f).
     *
     * @return The current device volume level, where 0.0f is muted and 1.0f is maximum volume.
     */
    private fun getDeviceVolumeLevel(): Float {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (maxVolume > 0) {
            currentVolume.toFloat() / maxVolume.toFloat()
        } else {
            0f
        }
    }

    /**
     * Checks if device volume has changed and notifies listeners.
     */
    private fun checkDeviceVolumeChange() {
        val currentVolume = getDeviceVolumeLevel()
        val isMuted = currentVolume == 0f

        // Only notify if volume actually changed
        if (currentVolume != previousDeviceVolumeLevel) {
            previousDeviceVolumeLevel = currentVolume
            playbackListeners.forEach { listener ->
                listener.onVolumeChanged(currentVolume)
            }
            playbackListeners.forEach { listener ->
                listener.onMuteStateChanged(isMuted)
            }
        }
    }

    /**
     * Starts monitoring device volume changes.
     */
    private fun startVolumeMonitoring() {
        if (!isVolumeMonitoringActive && playbackListeners.isNotEmpty()) {
            isVolumeMonitoringActive = true
            // Check initial volume state
            checkDeviceVolumeChange()
            // Start periodic volume checks
            timeUpdateHandler.postDelayed(volumeCheckRunnable, DEFAULT_VOLUME_CHECK_INTERVAL_MS)
        }
    }

    /**
     * Stops monitoring device volume changes.
     */
    private fun stopVolumeMonitoring() {
        if (isVolumeMonitoringActive) {
            isVolumeMonitoringActive = false
            timeUpdateHandler.removeCallbacks(volumeCheckRunnable)
        }
    }

    /**
     * Releases the player instance and all associated resources.
     *
     * After calling this method, the player should not be used anymore.
     * Call this when the player is no longer needed to free resources.
     */
    fun release() {
        stopTimeUpdates()
        stopVolumeMonitoring()
        completeSeekIfInProgress()
        // Tear down ABR pipeline in reverse order (controller removes its listener from the
        // monitor before we unregister the OS callback).
        stallWatchdog.detach()
        abrController.detach()
        networkMonitor.unregister()
        pendingAudioTrackId = null
        pendingSubtitleTrackId = null
        pendingVideoTrackId = null
        endAnalyticsView()
        seekPreviewManager?.release()
        detachPlayerListener()
        playlistListeners.clear()
        playlist.clear()
        // The player first: it shares its playback pipeline with the preload manager, and releasing
        // the manager first can leave the player's release waiting on a thread that is gone.
        exoPlayer.release()
        hideBridge()
        prerenderer?.release()
        playlistPreloader?.release()
        DecoderBudget.onDecoderClosed(this)
        DecoderBudget.release(this)
    }

    // --------------- Audio track API ---------------

    /**
     * Returns all available audio tracks for the currently loaded media.
     * Empty if no media is loaded or the media has no multiple audio tracks.
     * Works with HLS and progressive streams.
     */
    fun getAudioTracks(): List<AudioTrack> {
        return trackManager.getAudioTracks()
    }

    /**
     * Returns the currently selected audio track, or null if none or only one track.
     */
    fun getCurrentAudioTrack(): AudioTrack? {
        return trackManager.getCurrentAudioTrack()
    }

    /**
     * Switches playback to the audio track with the given [trackId].
     * Does not restart playback; position and playback state are preserved.
     * Video rendering is not interrupted.
     *
     * If a seek is in progress, the switch is applied when the seek completes.
     * Safe to call when paused or buffering.
     *
     * @param trackId The track id from [AudioTrack.id] (e.g. from [getAudioTracks]).
     */
    fun setAudioTrack(trackId: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            timeUpdateHandler.post { setAudioTrack(trackId) }
            return
        }
        if (isSeeking) {
            pendingAudioTrackId = trackId
            return
        }
        // No media loaded — player not ready for track switching
        if (exoPlayer.mediaItemCount == 0) {
            audioTrackListeners.forEach {
                it.onAudioTracksLoadedFailed(AudioTrackError.PlayerNotReady(trackId))
            }
            return
        }
        val track = trackManager.findAudioTrackById(trackId)
        when {
            track == null -> {
                audioTrackListeners.forEach {
                    it.onAudioTracksLoadedFailed(AudioTrackError.TrackNotFound(trackId))
                }
            }

            !track.isPlayable -> {
                audioTrackListeners.forEach {
                    it.onAudioTracksLoadedFailed(AudioTrackError.TrackNotPlayable(trackId))
                }
            }

            else -> {
                audioTrackListeners.forEach { it.onAudioTrackSwitching(true) }
                val applied = mediaSelectionController.setAudioTrack(trackId)
                if (applied != null) {
                    trackManager.markAudioManuallySelected()
                    audioTrackListeners.forEach { it.onAudioTracksChange(applied) }
                } else {
                    audioTrackListeners.forEach {
                        it.onAudioTracksLoadedFailed(AudioTrackError.SelectionFailed(trackId, null))
                    }
                }
                audioTrackListeners.forEach { it.onAudioTrackSwitching(false) }
            }
        }
    }

    /**
     * Adds a listener for audio track updates and selection events.
     */
    fun addAudioTrackListener(listener: AudioTrackListener) {
        if (!audioTrackListeners.contains(listener)) {
            audioTrackListeners.add(listener)
        }
    }

    /**
     * Removes an audio track listener.
     */
    fun removeAudioTrackListener(listener: AudioTrackListener) {
        audioTrackListeners.remove(listener)
    }

    /**
     * Applies a pending audio track switch that was deferred during seek. Call on main thread.
     */
    private fun applyPendingAudioTrackSwitch() {
        val trackId = pendingAudioTrackId ?: return
        pendingAudioTrackId = null
        setAudioTrack(trackId)
    }

    /**
     * Applies a pending subtitle track switch that was deferred during seek. Call on main thread.
     */
    private fun applyPendingSubtitleTrackSwitch() {
        val trackId = pendingSubtitleTrackId ?: return
        pendingSubtitleTrackId = null
        setSubtitleTrack(trackId)
    }

    /**
     * Applies a pending video quality switch that was deferred during seek. Call on main thread.
     */
    private fun applyPendingVideoTrackSwitch() {
        val trackId = pendingVideoTrackId ?: return
        pendingVideoTrackId = null
        setVideoQuality(trackId)
    }

    // --------------- Subtitle track API ---------------

    /**
     * Returns all available subtitle tracks for the currently loaded media.
     * Empty if no media is loaded or the media has no subtitle tracks.
     */
    fun getSubtitleTracks(): List<SubtitleTrack> {
        return trackManager.getSubtitleTracks()
    }

    /**
     * Returns the currently selected subtitle track, or null if none or subtitles are disabled.
     */
    fun getCurrentSubtitleTrack(): SubtitleTrack? {
        return trackManager.getCurrentSubtitleTrack()
    }

    /**
     * Switches playback to the subtitle track with the given [trackId].
     * Does not restart playback; position and playback state are preserved.
     *
     * If a seek is in progress, the switch is applied when the seek completes.
     * Safe to call when paused or buffering.
     *
     * @param trackId The track id from [SubtitleTrack.id] (e.g. from [getSubtitleTracks]).
     */
    fun setSubtitleTrack(trackId: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            timeUpdateHandler.post { setSubtitleTrack(trackId) }
            return
        }
        if (isSeeking) {
            pendingSubtitleTrackId = trackId
            return
        }
        if (exoPlayer.mediaItemCount == 0) {
            subtitleTrackListeners.forEach {
                it.onSubtitlesLoadedFailed(SubtitleTrackError.PlayerNotReady(trackId))
            }
            return
        }
        val track = trackManager.findSubtitleTrackById(trackId)
        when {
            track == null -> {
                subtitleTrackListeners.forEach {
                    it.onSubtitlesLoadedFailed(SubtitleTrackError.TrackNotFound(trackId))
                }
            }

            !track.isPlayable -> {
                subtitleTrackListeners.forEach {
                    it.onSubtitlesLoadedFailed(SubtitleTrackError.TrackNotPlayable(trackId))
                }
            }

            else -> {
                val applied = mediaSelectionController.setSubtitleTrack(trackId)
                if (applied != null) {
                    trackManager.markSubtitleManuallySelected()
                    subtitleTrackListeners.forEach { it.onSubtitleChange(applied) }
                } else {
                    subtitleTrackListeners.forEach {
                        it.onSubtitlesLoadedFailed(
                            SubtitleTrackError.SelectionFailed(
                                trackId,
                                null
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Disables subtitle track selection. Playback position and state are preserved.
     * Forced subtitles may still render per stream/selector behavior.
     */
    fun disableSubtitles() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            timeUpdateHandler.post { disableSubtitles() }
            return
        }
        mediaSelectionController.disableSubtitles()
        trackManager.markSubtitlesDisabledByUser()
        subtitleTrackListeners.forEach { it.onSubtitleChange(null) }
    }

    // --------------- Video quality API ---------------

    /**
     * Returns all available video qualities for the currently loaded media.
     * Empty if no media is loaded or no video tracks are present.
     */
    fun getVideoQualities(): List<VideoTrack> {
        return trackManager.getVideoTracks()
    }

    /**
     * Returns the current video quality. In AUTO mode this reflects the currently selected rendition.
     */
    fun getCurrentVideoQuality(): VideoTrack? {
        return trackManager.getCurrentVideoTrack()
    }

    /**
     * Switches playback to a specific fixed video quality.
     * Does not recreate player, reload media, or reset playback position.
     *
     * If a seek is in progress, the switch is deferred until seek completion.
     */
    fun setVideoQuality(trackId: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            timeUpdateHandler.post { setVideoQuality(trackId) }
            return
        }
        if (isSeeking) {
            pendingVideoTrackId = trackId
            return
        }
        if (exoPlayer.mediaItemCount == 0) return
        val applied = mediaSelectionController.setVideoTrack(trackId)
        if (applied != null) {
            trackManager.markVideoManuallySelected(trackId)
            notifyVideoQualityChangedIfNeeded(PlaybackListener.VideoQualityChangeSource.MANUAL)
        }
    }

    /**
     * Re-enables automatic video quality (ABR) by clearing manual overrides.
     */
    fun enableAutoQuality() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            timeUpdateHandler.post { enableAutoQuality() }
            return
        }
        pendingVideoTrackId = null
        mediaSelectionController.enableAutoVideoQuality()
        trackManager.markVideoAutoEnabled()
        notifyVideoQualityChangedIfNeeded(PlaybackListener.VideoQualityChangeSource.MANUAL)
    }

    private fun pollVideoQualityChange() {
        val format = exoPlayer.videoFormat ?: return
        val w = format.width.takeIf { it != Format.NO_VALUE } ?: return
        val h = format.height.takeIf { it != Format.NO_VALUE } ?: return
        trackManager.updateRenderedVideoSize(w, h)
        notifyVideoQualityChangedIfNeeded(PlaybackListener.VideoQualityChangeSource.ABR)
    }

    private fun notifyVideoQualityChangedIfNeeded(
        source: PlaybackListener.VideoQualityChangeSource
    ) {
        val current = trackManager.getCurrentVideoTrack()
        val changed = current != lastNotifiedVideoQuality
        if (!changed) return
        lastNotifiedVideoQuality = current
        playbackListeners.forEach { it.onVideoQualityChanged(current, source) }
    }

    // --------------- Default track language API ---------------

    /**
     * Sets a preferred/default audio language to be applied automatically when tracks become available.
     * Does not restart playback. Manual audio selection is never overridden.
     */
    fun setDefaultAudioTrack(languageCode: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            timeUpdateHandler.post { setDefaultAudioTrack(languageCode) }
            return
        }
        trackManager.setDefaultAudioTrack(languageCode)
        // Apply immediately if tracks are already ready; otherwise it will apply on onTracksChanged().
        applyDefaultTrackSelectionIfNeeded()
    }

    /**
     * Sets a preferred/default subtitle language to be applied automatically when tracks become available.
     * Does not restart playback. Manual subtitle selection is never overridden.
     */
    fun setDefaultSubtitleTrack(languageCode: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            timeUpdateHandler.post { setDefaultSubtitleTrack(languageCode) }
            return
        }
        trackManager.setDefaultSubtitleTrack(languageCode)
        // Apply immediately if tracks are already ready; otherwise it will apply on onTracksChanged().
        applyDefaultTrackSelectionIfNeeded()
    }

    /**
     * Applies TrackManager's default-selection decisions using existing Media3 override logic.
     * Call on main thread only.
     *
     * @return true if a selection/disable change was applied (another onTracksChanged is expected).
     */
    private fun applyDefaultTrackSelectionIfNeeded(): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) return false
        if (exoPlayer.mediaItemCount == 0) return false

        val audioChanged = applyDefaultAudioSelectionIfNeeded(trackManager.decideAutoAudioSelection())
        val subtitleChanged =
            applyDefaultSubtitleSelectionIfNeeded(trackManager.decideAutoSubtitleSelection())
        return audioChanged || subtitleChanged
    }

    private fun applyDefaultAudioSelectionIfNeeded(
        action: TrackManager.AutoSelectionAction
    ): Boolean {
        if (action !is TrackManager.AutoSelectionAction.SelectTrack) return false
        val current = trackManager.getCurrentAudioTrack()
        if (current?.id == action.trackId) return false

        val applied = mediaSelectionController.setAudioTrack(action.trackId) ?: return false
        // Optional but recommended: update UI immediately.
        audioTrackListeners.forEach { it.onAudioTracksChange(applied) }
        return true
    }

    private fun applyDefaultSubtitleSelectionIfNeeded(
        action: TrackManager.AutoSelectionAction
    ): Boolean {
        return when (action) {
            is TrackManager.AutoSelectionAction.SelectTrack ->
                applyDefaultSubtitleTrackSelection(action.trackId)

            TrackManager.AutoSelectionAction.DisableSubtitles -> applyDefaultSubtitleDisable()
            TrackManager.AutoSelectionAction.NoOp -> false
        }
    }

    private fun applyDefaultSubtitleTrackSelection(trackId: String): Boolean {
        val current = trackManager.getCurrentSubtitleTrack()
        val currentlyDisabled = isSubtitleTrackTypeDisabled()
        if (current?.id == trackId && !currentlyDisabled) return false

        val applied = mediaSelectionController.setSubtitleTrack(trackId) ?: return false
        // Optional but recommended: update UI immediately.
        subtitleTrackListeners.forEach { it.onSubtitleChange(applied) }
        return true
    }

    private fun applyDefaultSubtitleDisable(): Boolean {
        // Only auto-disable when user has NOT explicitly disabled; TrackManager encodes that.
        val currentlyDisabled = isSubtitleTrackTypeDisabled()
        // Avoid firing "Subtitle: Off" updates when subtitles are already effectively off.
        // We only need to disable when a subtitle track is actually selected (e.g., default-selected by stream).
        val current = trackManager.getCurrentSubtitleTrack()
        if (currentlyDisabled || current == null) return false

        mediaSelectionController.disableSubtitles()
        subtitleTrackListeners.forEach { it.onSubtitleChange(null) }
        return true
    }

    private fun isSubtitleTrackTypeDisabled(): Boolean {
        return exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    }

    /**
     * Adds a listener for subtitle track updates and cue changes.
     */
    fun addSubtitleTrackListener(listener: SubtitleTrackListener) {
        if (!subtitleTrackListeners.contains(listener)) {
            subtitleTrackListeners.add(listener)
        }
    }

    /**
     * Removes a subtitle track listener.
     */
    fun removeSubtitleTrackListener(listener: SubtitleTrackListener) {
        subtitleTrackListeners.remove(listener)
    }
}