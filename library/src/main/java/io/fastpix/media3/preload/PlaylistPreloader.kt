package io.fastpix.media3.preload

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.fastpix.media3.cache.CacheConfig
import io.fastpix.media3.playlist.PlaylistItem
import io.fastpix.media3.prerender.SingleViewPrerenderer

/**
 * Tells Media3's preload manager how far to prepare each entry, from [window] and the index now
 * playing. Entries are ranked by playlist index.
 */
@UnstableApi
internal class PreloadPolicy(
    val window: PreloadWindow,
    private val bufferedDurationMs: Long,
) : TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> {

    /** Index of the entry playing; -1 when none. Read on the preload manager's thread. */
    @Volatile
    var currentIndex: Int = -1

    override fun getTargetPreloadStatus(rankingData: Int): DefaultPreloadManager.PreloadStatus =
        when (window.stageFor(rankingData, currentIndex)) {
            PreloadStage.NONE -> DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_NOT_PRELOADED
            PreloadStage.TRACKS_SELECTED ->
                DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_TRACKS_SELECTED
            PreloadStage.RANGE_LOADED ->
                DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(bufferedDurationMs)
        }
}

/**
 * What `FastPixPlayer.Builder` sets up when preloading is on: the preload manager built alongside
 * the player, and what the player needs to finish wiring it.
 */
@UnstableApi
internal class PreloadSetup(
    val preloadManager: DefaultPreloadManager,
    val policy: PreloadPolicy,
    /**
     * Track selectors the preload manager chooses renditions with. They must follow the player's
     * bitrate cap, or entries are preloaded in a rendition playback then refuses.
     */
    val preloadTrackSelectors: List<DefaultTrackSelector>,
    /** The disk cache, when enabled: entries further out are written into it. */
    val cache: Cache?,
    val cacheConfig: CacheConfig,
    /** Captures upcoming entries' first frames; null unless pre-rendering was configured. */
    val prerenderer: SingleViewPrerenderer? = null,
)

/**
 * Keeps the entries around the current one prepared, following the player's playlist.
 *
 * Adjacent and nearby entries are prepared in memory by Media3's [DefaultPreloadManager], whose
 * sources the player then plays directly. With the cache on, entries further out are also warmed
 * to disk through [DiskWarmer]. Main thread only.
 */
@UnstableApi
internal class PlaylistPreloader(
    private val preloadManager: DefaultPreloadManager,
    private val policy: PreloadPolicy,
    private val diskWarmer: DiskWarmer?,
) {

    /** Warms media items to disk; see `FastPixPreCacher.forPlayer`. */
    internal interface DiskWarmer {
        fun warm(mediaItems: List<MediaItem>)
        fun cancelAll()
        fun release()
    }

    private var items: List<PlaylistItem> = emptyList()

    /**
     * Media items already registered. The preload manager keys sources by media item, so a
     * playlist listing one item twice registers it once, ranked at its first position.
     */
    private val registered = HashSet<MediaItem>()

    /** Replaces everything registered with [items], ranked by index. */
    fun setPlaylist(items: List<PlaylistItem>) {
        preloadManager.reset()
        registered.clear()
        this.items = items
        register(0, items)
    }

    /**
     * Registers entries appended at [startIndex] (the old size). Existing rankings are unchanged,
     * so sources already preloaded are kept — the common case for an endlessly growing feed.
     */
    fun append(startIndex: Int, newItems: List<PlaylistItem>) {
        items = items + newItems
        register(startIndex, newItems)
        preloadManager.invalidate()
    }

    /** Moves the window to [index]: re-ranks, then warms what lies beyond the adjacent entries. */
    fun onCurrentIndexChanged(index: Int) {
        policy.currentIndex = index
        preloadManager.setCurrentPlayingIndex(index)
        preloadManager.invalidate()
        diskWarmer?.warm(
            policy.window.diskWarmIndices(index, items.size).map { items[it].mediaItem }
        )
    }

    /** The source to play [item] from — preloaded if it was in the window. */
    fun mediaSourceFor(item: PlaylistItem): MediaSource? =
        preloadManager.getMediaSource(item.mediaItem)

    /** Drops everything preloaded, e.g. when the playlist is cleared. */
    fun clear() {
        preloadManager.reset()
        registered.clear()
        items = emptyList()
        policy.currentIndex = -1
        diskWarmer?.cancelAll()
    }

    fun release() {
        preloadManager.release()
        registered.clear()
        items = emptyList()
        diskWarmer?.release()
    }

    private fun register(startIndex: Int, newItems: List<PlaylistItem>) {
        newItems.forEachIndexed { offset, item ->
            if (registered.add(item.mediaItem)) preloadManager.add(item.mediaItem, startIndex + offset)
        }
    }
}
