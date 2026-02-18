package io.fastpix.media3.core

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.fastpix.media3.PlaybackListener
import kotlin.math.abs

@UnstableApi
class FastPixPlayer private constructor(
    private val context: Context,
    private val exoPlayer: ExoPlayer,
    initialLoop: Boolean = false,
    initialAutoplay: Boolean = false
) {

    /**
     * Builder class for creating FastPixPlayer instances with configuration.
     *
     * Example usage:
     * ```
     * val player = FastPixPlayer.Builder(context)
     *     .setLoop(true)
     *     .setAutoplay(false)
     *     .build()
     * ```
     */
    class Builder(private val context: Context) {
        private var loop: Boolean = false
        private var autoplay: Boolean = false

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
         * Builds and returns a configured FastPixPlayer instance.
         *
         * @return A new FastPixPlayer instance with the configured settings.
         */
        fun build(): FastPixPlayer {
            val exoPlayer = ExoPlayer.Builder(context).build()
            return FastPixPlayer(context, exoPlayer, loop, autoplay)
        }
    }

    companion object {
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
     */
    private val playbackListeners = mutableListOf<PlaybackListener>()

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
     * Previous playback state to detect transitions.
     */
    private var previousPlaybackState: Int = Player.STATE_IDLE

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
     * Runnable that dispatches time updates to all registered listeners.
     */
    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            // Pause time updates during seek operations
            if (isSeeking) {
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

                // Schedule next update if still playing
                if (exoPlayer.isPlaying && playbackListeners.isNotEmpty() && !isSeeking) {
                    timeUpdateHandler.postDelayed(this, DEFAULT_TIME_UPDATE_INTERVAL_MS)
                } else {
                    // Stop updates if conditions are no longer met
                    isTimeUpdateScheduled = false
                }
            } else {
                // Stop updates if conditions are no longer met
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
    fun setFastPixMediaItem(block: FastPixMediaItemBuilder.() -> Unit): Boolean {
        exoPlayer ?: return false

        // Build the configuration
        val config = fastPixMediaItem(block)

        // Validate playback ID
        if (config.playbackId.isBlank()) {
            notifyPlayerError(
                PlaybackException(
                    "Playback ID is empty",
                    IllegalArgumentException(),
                    9002
                )
            )
            return false
        }

        // Create playback URL
        val playbackUrl = createFastPixPlaybackUrl(
            playbackId = config.playbackId,
            customDomain = config.customDomain ?: "stream.fastpix.io",
            maxResolution = config.maxResolution,
            minResolution = config.minResolution,
            resolution = config.resolution,
            renditionOrder = config.renditionOrder,
            playbackToken = config.playbackToken
        )

        // Create and set the media item
        val mediaItem = MediaItem.Builder().setUri(playbackUrl).build()
        setMediaItem(mediaItem)
        return true
    }


    /**
     * Creates a FastPix playback URL with the given parameters.
     */
    @UnstableApi
    private fun createFastPixPlaybackUrl(
        playbackId: String,
        customDomain: String,
        maxResolution: PlaybackResolution?,
        minResolution: PlaybackResolution?,
        resolution: PlaybackResolution?,
        renditionOrder: RenditionOrder?,
        playbackToken: String?
    ): String {
        val uriBuilder = Uri.Builder()
            .scheme("https")
            .authority(customDomain)
            .appendPath("$playbackId.m3u8")

        minResolution?.let {
            uriBuilder.appendQueryParameter("minResolution", getResolutionValue(it))
        }
        maxResolution?.let {
            uriBuilder.appendQueryParameter("maxResolution", getResolutionValue(it))
        }
        resolution?.let {
            uriBuilder.appendQueryParameter("resolution", getResolutionValue(it))
        }
        renditionOrder?.takeIf { it != RenditionOrder.Default }?.let {
            uriBuilder.appendQueryParameter("renditionOrder", getRenditionValue(it))
        }
        playbackToken?.let {
            uriBuilder.appendQueryParameter("token", it)
        }

        return uriBuilder.build().toString()
    }

    /**
     * Converts a PlaybackResolution enum to its string value.
     */
    private fun getResolutionValue(resolution: PlaybackResolution): String {
        return when (resolution) {
            PlaybackResolution.LD_480 -> "480p"
            PlaybackResolution.LD_540 -> "540p"
            PlaybackResolution.HD_720 -> "720p"
            PlaybackResolution.FHD_1080 -> "1080p"
            PlaybackResolution.QHD_1440 -> "1440p"
            PlaybackResolution.FOUR_K_2160 -> "2160p"
        }
    }

    /**
     * Converts a RenditionOrder enum to its string value.
     */
    private fun getRenditionValue(renditionOrder: RenditionOrder): String {
        return when (renditionOrder) {
            RenditionOrder.Descending -> "desc"
            RenditionOrder.Ascending -> "asc"
            RenditionOrder.Default -> ""
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
            // Detect buffering state transitions
            if (previousPlaybackState != Player.STATE_BUFFERING && playbackState == Player.STATE_BUFFERING) {
                // Transitioned to buffering state
                playbackListeners.forEach { it.onBufferingStart() }
            } else if (previousPlaybackState == Player.STATE_BUFFERING && playbackState == Player.STATE_READY) {
                // Transitioned from buffering to ready
                playbackListeners.forEach { it.onBufferingEnd() }
            }

            // Update previous state
            previousPlaybackState = playbackState

            // Stop time updates when playback ends
            if (playbackState == Player.STATE_ENDED) {
                stopTimeUpdates()
                // Notify listeners that playback has completed
                playbackListeners.forEach { it.onCompleted() }
            }

            // If we're seeking and player becomes ready, complete the seek
            if (isSeeking && playbackState == Player.STATE_READY) {
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

                // Resume time updates if player is playing
                if (exoPlayer.isPlaying && playbackListeners.isNotEmpty()) {
                    startTimeUpdates()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
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
    }

    /**
     * Attaches the player listener, ensuring no duplicate listeners.
     */
    private fun attachPlayerListener() {
        if (!isListenerAttached) {
            // Defensively remove listener first (safe to call even if not attached)
            exoPlayer.removeListener(playerListener)
            // Now add the listener
            exoPlayer.addListener(playerListener)
            isListenerAttached = true
        }
    }

    /**
     * Detaches the player listener.
     */
    private fun detachPlayerListener() {
        if (isListenerAttached) {
            exoPlayer.removeListener(playerListener)
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
     * Sets a media item to play.
     *
     * @param mediaItem The media item to set.
     */
    fun setMediaItem(mediaItem: MediaItem) {
        // Check if player already has media items and is in a valid state
        val currentMediaItemCount = exoPlayer.mediaItemCount
        if (currentMediaItemCount > 0) {
            // Player already has media items - check if it's the same item
            val currentMediaItem = exoPlayer.currentMediaItem
            if (currentMediaItem != null) {
                val currentMediaId = currentMediaItem.mediaId
                val currentUri = currentMediaItem.requestMetadata.mediaUri
                val newMediaId = mediaItem.mediaId
                val newUri = mediaItem.requestMetadata.mediaUri

                // Compare media ID and URI (handle null cases)
                val mediaIdMatches = (currentMediaId == null && newMediaId == null) ||
                        (currentMediaId != null && currentMediaId == newMediaId)
                val uriMatches = (currentUri == null && newUri == null) ||
                        (currentUri != null && currentUri == newUri)

                if (mediaIdMatches && uriMatches) {
                    // Same media item already set - don't reset playback state
                    return
                }
            }
        }

        // New or different media item - set it and prepare
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    /**
     * Sets multiple media items to play.
     *
     * @param mediaItems The list of media items to set.
     * @param startIndex The index of the item to start playing from.
     * @param startPositionMs The position in milliseconds to start from.
     */
    fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0
    ) {
        // Check if player already has the same media items
        val currentMediaItemCount = exoPlayer.mediaItemCount
        if (currentMediaItemCount == mediaItems.size && currentMediaItemCount > 0) {
            // Check if all media items match
            var allMatch = true
            for (i in mediaItems.indices) {
                val currentItem = exoPlayer.getMediaItemAt(i)
                val newItem = mediaItems[i]
                if (currentItem.mediaId != newItem.mediaId ||
                    currentItem.requestMetadata.mediaUri != newItem.requestMetadata.mediaUri
                ) {
                    allMatch = false
                    break
                }
            }

            if (allMatch) {
                // Same media items already set - don't reset playback state
                return
            }
        }

        // New or different media items - set them and prepare
        exoPlayer.setMediaItems(mediaItems, startIndex, startPositionMs)
        exoPlayer.prepare()
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
        if (!isTimeUpdateScheduled && playbackListeners.isNotEmpty() && exoPlayer.isPlaying) {
            isTimeUpdateScheduled = true
            timeUpdateHandler.post(timeUpdateRunnable)
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
        detachPlayerListener()
        exoPlayer.release()
    }
}
