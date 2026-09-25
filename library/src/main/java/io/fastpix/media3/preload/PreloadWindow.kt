package io.fastpix.media3.preload

/** How far an entry is prepared ahead of playback. */
internal enum class PreloadStage {
    /** Left alone. */
    NONE,

    /** Playlists fetched and tracks chosen; no media bytes held in memory. */
    TRACKS_SELECTED,

    /** The first seconds buffered in memory, ready to start. */
    RANGE_LOADED,
}

/**
 * Which entries around the current one are preloaded, and how far — [PreloadConfig] turned into a
 * decision per playlist index.
 *
 * Pure so it is unit-testable; [PlaylistPreloader] feeds its answers to Media3's preload manager
 * and the disk warmer.
 */
internal class PreloadWindow(
    private val ahead: Int,
    private val behind: Int,
) {

    constructor(config: PreloadConfig) : this(config.count, config.behind)

    /** How far to prepare the entry at [index] while [currentIndex] is playing. */
    fun stageFor(index: Int, currentIndex: Int): PreloadStage {
        if (currentIndex < 0 || index < 0 || index == currentIndex) return PreloadStage.NONE
        val distance = index - currentIndex
        val inWindow = if (distance > 0) distance <= ahead else -distance <= behind
        return when {
            !inWindow -> PreloadStage.NONE
            // Adjacent entries are where the user goes next; hold them ready to start.
            distance == 1 || distance == -1 -> PreloadStage.RANGE_LOADED
            else -> PreloadStage.TRACKS_SELECTED
        }
    }

    /**
     * Entries whose first segments should be written to disk: everything in the window beyond the
     * adjacent entries, nearest first, ahead before behind at equal distance. Adjacent entries need
     * no separate warm — buffering them loads through the cache and writes it as it goes.
     */
    fun diskWarmIndices(currentIndex: Int, size: Int): List<Int> {
        if (currentIndex !in 0 until size) return emptyList()
        val indices = mutableListOf<Int>()
        for (distance in 2..maxOf(ahead, behind)) {
            if (distance <= ahead && currentIndex + distance < size) indices += currentIndex + distance
            if (distance <= behind && currentIndex - distance >= 0) indices += currentIndex - distance
        }
        return indices
    }
}
