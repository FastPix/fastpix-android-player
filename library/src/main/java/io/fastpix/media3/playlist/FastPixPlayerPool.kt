package io.fastpix.media3.playlist

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import io.fastpix.media3.analytics.AnalyticsConfig
import io.fastpix.media3.cache.CacheConfig
import io.fastpix.media3.cache.ItemScopedMediaSourceFactory
import io.fastpix.media3.cache.MediaCacheProvider
import io.fastpix.media3.core.FastPixPlayer
import io.fastpix.media3.preload.PlaylistPreloader
import io.fastpix.media3.preload.PreloadConfig
import io.fastpix.media3.preload.PreloadPolicy
import io.fastpix.media3.preload.PreloadWindow
import io.fastpix.media3.prerender.DecoderBudget
import io.fastpix.media3.prerender.PrerenderConfig
import kotlin.math.abs

/**
 * Players for a playlist shown one page per item — a vertical reel feed, a carousel — where each
 * page has its own [io.fastpix.media3.PlayerView].
 *
 * The app keeps its pager and UI; the pool supplies each page's player and keeps the pages around
 * the current one ready. With [PrerenderConfig], neighbouring pages are **pre-rendered**: decoded up
 * to their first frame and held paused, so swiping to them shows video immediately rather than a
 * black frame.
 *
 * ```kotlin
 * val pool = FastPixPlayerPool.Builder(context)
 *     .setPlayerConfig { it.setLoop(true) }                  // applied to every player
 *     .setPreloadConfig(PreloadConfig(count = 3))
 *     .setCacheConfig(CacheConfig.enabled())
 *     .setPrerenderConfig(PrerenderConfig(count = 1, behind = 1))
 *     .setAnalyticsConfig(AnalyticsConfig.Builder("your-workspace-id").build())
 *     .build()
 *
 * pool.setPlaylist(items)
 * viewPager.offscreenPageLimit = 1                           // neighbours need views to render into
 *
 * // binding a page
 * playerView.player = pool.playerAt(position)
 * // the user lands on a page
 * pool.setCurrentIndex(position)
 * // the screen goes away
 * pool.release()
 * ```
 *
 * Pre-rendering needs somewhere to draw, so the pages to pre-render must be bound: keep
 * `offscreenPageLimit` (ViewPager2) or `beyondViewportPageCount` (Compose pager) at least as large
 * as the pre-render counts.
 *
 * The pool owns its players: don't release them yourself, and don't keep a player after its page
 * is recycled — ask [playerAt] again. Main thread only.
 */
@UnstableApi
class FastPixPlayerPool private constructor(
    context: Context,
    playerConfig: PlayerConfig,
    preloadConfig: PreloadConfig,
    cacheConfig: CacheConfig,
    private val prerenderConfig: PrerenderConfig,
    analyticsConfig: AnalyticsConfig?,
) {

    /** Configures a [FastPixPlayerPool]. Every setting is optional; the defaults turn nothing on. */
    @UnstableApi
    class Builder(private val context: Context) {
        private var playerConfig: PlayerConfig = PlayerConfig { }
        private var preloadConfig: PreloadConfig = PreloadConfig.DISABLED
        private var cacheConfig: CacheConfig = CacheConfig.DISABLED
        private var prerenderConfig: PrerenderConfig = PrerenderConfig.DISABLED
        private var analyticsConfig: AnalyticsConfig? = null

        /**
         * Settings applied to every player the pool creates — loop, ABR, buffering, seek preview.
         * Cache, preload and autoplay are the pool's to decide: set them on this builder instead.
         */
        fun setPlayerConfig(config: PlayerConfig): Builder {
            playerConfig = config
            return this
        }

        /** Entries ahead of (and behind) the current one to prepare. See [PreloadConfig]. */
        fun setPreloadConfig(config: PreloadConfig): Builder {
            preloadConfig = config
            return this
        }

        /** The disk cache shared by every player in the pool. See [CacheConfig]. */
        fun setCacheConfig(config: CacheConfig): Builder {
            cacheConfig = config
            return this
        }

        /** Entries around the current one to decode up to their first frame. See [PrerenderConfig]. */
        fun setPrerenderConfig(config: PrerenderConfig): Builder {
            prerenderConfig = config
            return this
        }

        /**
         * FastPix Data analytics for the feed. Each page the user lands on is reported as its own
         * view, with its entry's [PlaylistItem.videoDataDetails]; pages only preloaded or
         * pre-rendered are never reported. Build the config without a view —
         * `AnalyticsConfig.Builder("your-workspace-id").build()` — and each view is measured
         * through the page's [io.fastpix.media3.PlayerView].
         */
        fun setAnalyticsConfig(config: AnalyticsConfig?): Builder {
            analyticsConfig = config
            return this
        }

        fun build(): FastPixPlayerPool = FastPixPlayerPool(
            context, playerConfig, preloadConfig, cacheConfig, prerenderConfig, analyticsConfig,
        )
    }

    /**
     * Settings applied to every player a pool creates:
     * `setPlayerConfig { it.setLoop(true).setBufferConfig(BufferConfig.FEED) }`.
     */
    @UnstableApi
    fun interface PlayerConfig {
        fun configure(builder: FastPixPlayer.Builder)
    }

    private val appContext = context.applicationContext
    private val template = FastPixPlayer.Builder(appContext).also { builder ->
        playerConfig.configure(builder)
        analyticsConfig?.let { builder.setAnalyticsConfig(it) }
    }

    /** One bandwidth estimate for the whole feed, so every page starts from what the last learned. */
    private val bandwidthMeter = DefaultBandwidthMeter.Builder(appContext)
        .setResetOnNetworkTypeChange(template.abrConfig.resetBandwidthOnNetworkChange)
        .build()

    private val cache = MediaCacheProvider.getOrCreate(appContext, cacheConfig)
    private val mediaSourceFactory: MediaSource.Factory =
        cache?.let { ItemScopedMediaSourceFactory(appContext, it, cacheConfig) }
            ?: DefaultMediaSourceFactory(appContext)

    /**
     * Preload covers at least the pre-render window: a pre-rendering player plays the source the
     * preload manager prepared for it.
     */
    private val policy = PreloadPolicy(
        PreloadWindow(
            ahead = maxOf(preloadConfig.count, prerenderConfig.count),
            behind = maxOf(preloadConfig.behind, prerenderConfig.behind),
        ),
        preloadConfig.bufferedDurationMs,
    )

    /** Every track selector created through [preloadBuilder]: the preload manager's and each player's. */
    private val createdSelectors = mutableListOf<DefaultTrackSelector>()
    private val playerSelectors = HashSet<DefaultTrackSelector>()

    private val preloadBuilder = DefaultPreloadManager.Builder(appContext, policy)
        .setBandwidthMeter(bandwidthMeter)
        .setLoadControl(template.bufferConfig.toLoadControl())
        .setTrackSelectorFactory { selectorContext ->
            DefaultTrackSelector(selectorContext, template.trackSelectionFactory())
                .also { createdSelectors += it }
        }
        .setMediaSourceFactory(mediaSourceFactory)

    /**
     * The preload manager's own selectors, which must follow the players' bitrate cap. A live
     * view: the manager may create its selector after the first player.
     */
    private val preloadSelectors: List<DefaultTrackSelector> =
        object : AbstractList<DefaultTrackSelector>() {
            private fun current() = createdSelectors.filter { it !in playerSelectors }
            override val size: Int get() = current().size
            override fun get(index: Int): DefaultTrackSelector = current()[index]
        }

    private val preloader: PlaylistPreloader = PlaylistPreloader(
        preloadManager = preloadBuilder.build(),
        policy = policy,
        diskWarmer = cache?.let { openCache ->
            @Suppress("DEPRECATION")
            val warmer = io.fastpix.media3.cache.FastPixPreCacher.forPlayer(
                appContext,
                openCache,
                cacheConfig,
            ) {
                slots.firstOrNull { it.index == currentIndex }?.player?.preloadTargetBitrate()
                    ?: Int.MAX_VALUE
            }
            object : PlaylistPreloader.DiskWarmer {
                override fun warm(mediaItems: List<androidx.media3.common.MediaItem>) =
                    warmer.preCacheMediaItems(mediaItems)

                override fun cancelAll() = warmer.cancelAll()
                override fun release() = warmer.release()
            }
        },
    )

    /** One player and the playlist index it currently serves. */
    private class Slot(val player: FastPixPlayer) {
        var index: Int = NO_INDEX

        /** Holds a decoder grant and is prepared, paused, off screen. */
        var prerendering: Boolean = false

        /** Pre-rendering failed for the current assignment; don't try again until reassigned. */
        var prerenderFailed: Boolean = false

        /** The current item's decoder already failed once and was retried. */
        var retriedAsCurrent: Boolean = false
    }

    private val slots = mutableListOf<Slot>()

    /**
     * Players kept alive: the current page, the pre-render window, and one either side for pages
     * the pager binds just beyond it.
     */
    private val maxPlayers: Int = 1 + prerenderConfig.count + prerenderConfig.behind + 2

    private var items: List<PlaylistItem> = emptyList()
    private var currentIndex: Int = NO_INDEX
    private var released = false

    /**
     * Replaces the playlist. Every page's player is reassigned when next asked for with [playerAt];
     * call [setCurrentIndex] for the page on screen.
     */
    fun setPlaylist(items: List<PlaylistItem>) {
        checkNotReleased()
        this.items = items.toList()
        slotFor(currentIndex)?.player?.endAnalyticsViewForPool()
        currentIndex = NO_INDEX
        for (slot in slots) unassign(slot)
        preloader.setPlaylist(this.items)
    }

    /** Appends [items]; pages already assigned are unaffected. */
    fun addToPlaylist(items: List<PlaylistItem>) {
        checkNotReleased()
        if (items.isEmpty()) return
        val start = this.items.size
        this.items = this.items + items
        preloader.append(start, items)
    }

    /** The playlist's entries, in order. */
    fun getPlaylist(): List<PlaylistItem> = items

    /** The page on screen, or -1 before [setCurrentIndex]. */
    fun getCurrentIndex(): Int = currentIndex

    /**
     * The player for the page at [index], with its entry loaded. Bind it to that page's
     * [io.fastpix.media3.PlayerView]. It plays only once [setCurrentIndex] selects it; until then it
     * is paused, showing its first frame if it was pre-rendered.
     *
     * @throws IllegalArgumentException if [index] is out of range.
     */
    fun playerAt(index: Int): FastPixPlayer {
        checkNotReleased()
        require(index in items.indices) { "index $index out of range for ${items.size} items" }
        val slot = obtainSlot(index)
        applyWindow()
        return slot.player
    }

    /**
     * Makes [index] the page on screen: plays it, pauses the page left, and re-centres preloading
     * and pre-rendering on it. Call when the pager settles on a page.
     *
     * @throws IllegalArgumentException if [index] is out of range.
     */
    fun setCurrentIndex(index: Int) {
        checkNotReleased()
        require(index in items.indices) { "index $index out of range for ${items.size} items" }
        if (index == currentIndex) {
            slotFor(index)?.player?.play()
            return
        }
        slotFor(currentIndex)?.player?.let { left ->
            left.pause()
            // The user left that page: its view ends here, before the next one begins.
            left.endAnalyticsViewForPool()
        }
        currentIndex = index
        val slot = obtainSlot(index)
        applyWindow()
        slot.player.beginAnalyticsViewForPool(items[index])
        slot.player.play()
        preloader.onCurrentIndexChanged(index)
    }

    /**
     * Whether the page at [index] has its first frame on screen — true for a pre-rendered page
     * before it plays. Use [io.fastpix.media3.PlaybackListener.onFirstFrameRendered] on
     * [playerAt]'s player to hear when it happens.
     */
    fun isFirstFrameRendered(index: Int): Boolean =
        slotFor(index)?.player?.hasRenderedFirstFrame() == true

    /** Releases every player and stops all preloading. The pool cannot be used afterwards. */
    fun release() {
        if (released) return
        released = true
        for (slot in slots) {
            DecoderBudget.release(slot.player)
            slot.player.release()
        }
        slots.clear()
        preloader.release()
    }

    private fun slotFor(index: Int): Slot? =
        if (index == NO_INDEX) null else slots.firstOrNull { it.index == index }

    /**
     * The slot serving [index]: already assigned, a free one, a new one while under [maxPlayers],
     * or else the one furthest from the current page, reassigned.
     *
     * A player an attached view is showing is never reassigned, even over [maxPlayers]: its page
     * would keep showing that player while it plays another page's entry — a black page stuck on
     * pause. During a fast fling the pager can hold more pages than [maxPlayers] (attached, cached
     * and prefetched); the extra player is stopped outside the window, so it costs memory but no
     * decoder, and is reused once its page goes away.
     */
    private fun obtainSlot(index: Int): Slot {
        slotFor(index)?.let { return it }
        val free = slots.firstOrNull { it.index == NO_INDEX && !it.player.isDisplayedByView }
        val slot = when {
            free != null -> free
            slots.size < maxPlayers -> createSlot()
            else -> slots.filter { it.index != currentIndex && !it.player.isDisplayedByView }
                .maxByOrNull { abs(it.index - anchor()) }
                ?: createSlot()
        }
        assign(slot, index)
        return slot
    }

    /** Where distance is measured from before the first [setCurrentIndex]. */
    private fun anchor(): Int = if (currentIndex == NO_INDEX) 0 else currentIndex

    private fun createSlot(): Slot {
        val player = template.buildForPool(preloadBuilder, bandwidthMeter, preloadSelectors)
        playerSelectors += player.getExoPlayer().trackSelector as DefaultTrackSelector
        val slot = Slot(player)
        player.errorInterceptor = { error -> onPlayerError(slot, error) }
        slots += slot
        return slot
    }

    private fun assign(slot: Slot, index: Int) {
        unassign(slot)
        slot.index = index
        val item = items[index]
        slot.player.loadForPool(item, preloader.mediaSourceFor(item), prepare = false)
    }

    private fun unassign(slot: Slot) {
        if (slot.index == NO_INDEX) return
        slot.player.stopForPool()
        DecoderBudget.release(slot.player)
        slot.index = NO_INDEX
        slot.prerendering = false
        slot.prerenderFailed = false
        slot.retriedAsCurrent = false
    }

    /**
     * Decides, for every assigned page, whether it plays, pre-renders or waits: the current page is
     * always prepared; pages in the pre-render window are prepared paused if the decoder budget
     * grants it; everything else is stopped so it holds no decoder.
     */
    private fun applyWindow() {
        for (slot in slots) {
            if (slot.index == NO_INDEX) continue
            when {
                slot.index == currentIndex -> {
                    // Playing: primary decoders are never gated, so give back any grant.
                    DecoderBudget.release(slot.player)
                    slot.prerendering = false
                    slot.player.prepareForPool()
                }

                shouldPrerender(slot) -> {
                    if (slot.prerendering || DecoderBudget.tryGrant(slot.player)) {
                        slot.prerendering = true
                        slot.player.prepareForPool()
                    }
                }

                else -> {
                    if (slot.player.isPreparedForPool) slot.player.stopForPool()
                    DecoderBudget.release(slot.player)
                    slot.prerendering = false
                }
            }
        }
    }

    private fun shouldPrerender(slot: Slot): Boolean {
        if (currentIndex == NO_INDEX || slot.prerenderFailed) return false
        if (!prerenderConfig.covers(slot.index - currentIndex)) return false
        // Protected content's secure decoders are scarcer still; never spend them speculatively.
        return items[slot.index].mediaItem.localConfiguration?.drmConfiguration == null
    }

    /**
     * A pre-render failing is not the app's concern: stop it, and let the page load normally when
     * it becomes current. The current page's decoder failing to open while pre-renders hold
     * decoders is retried once with them freed.
     */
    private fun onPlayerError(slot: Slot, error: PlaybackException): Boolean {
        if (slot.index != currentIndex) {
            slot.prerenderFailed = true
            slot.prerendering = false
            slot.player.stopForPool()
            DecoderBudget.release(slot.player)
            return true
        }
        if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED &&
            !slot.retriedAsCurrent && slots.any { it.prerendering }
        ) {
            slot.retriedAsCurrent = true
            for (other in slots) {
                if (other.prerendering) {
                    other.prerendering = false
                    other.prerenderFailed = true
                    other.player.stopForPool()
                    DecoderBudget.release(other.player)
                }
            }
            slot.player.stopForPool()
            slot.player.prepareForPool()
            slot.player.play()
            return true
        }
        return false
    }

    private fun checkNotReleased() {
        check(!released) { "FastPixPlayerPool has been released" }
    }

    private companion object {
        const val NO_INDEX = -1
    }
}
