package io.fastpix.media3.prerender

/**
 * How many playlist entries around the current one are **pre-rendered**: decoded up to their first
 * frame and held paused, so moving to them shows video at once instead of a black frame.
 *
 * ```kotlin
 * // One PlayerView moving through a playlist
 * val player = FastPixPlayer.Builder(context)
 *     .setPrerenderConfig(PrerenderConfig(count = 1))            // the next entry
 *     .build()
 *
 * // A page and a PlayerView per entry (reel feeds, carousels)
 * val pool = FastPixPlayerPool.Builder(context)
 *     .setPrerenderConfig(PrerenderConfig(count = 1, behind = 1))
 *     .build()
 * ```
 *
 * With one view, each upcoming entry's first frame is captured in the background and shown the
 * moment the player moves to it, until the video itself appears. With a pool, neighbouring pages'
 * players are decoded up to their first frame and held paused. Either way: no black frame.
 *
 * An entry whose video itself opens on black still opens on black — its first frame is black.
 *
 * Each pre-rendered entry holds a video decoder, and devices can run only a few at once — budget
 * phones as few as two or three. **The counts are a maximum:** the SDK pre-renders as many as the
 * device can spare without risking the item playing, which may be fewer, and never more. It learns
 * from failures: a device that refuses a decoder is not asked for that many again.
 *
 * DRM-protected entries are never pre-rendered; their decoders are scarcer still.
 *
 * Off by default.
 */
data class PrerenderConfig(
    /** Entries after the current one to pre-render. 0 turns look-ahead off. */
    val count: Int = 0,

    /** Entries before the current one to keep pre-rendered, for going back. 0 by default. */
    val behind: Int = 0,
) {
    init {
        require(count >= 0) { "count must be >= 0, was $count" }
        require(behind >= 0) { "behind must be >= 0, was $behind" }
    }

    /** Whether anything is pre-rendered. */
    val enabled: Boolean get() = count > 0 || behind > 0

    /**
     * Indices in range around [currentIndex] within a playlist of [size], nearest first and ahead
     * before behind at equal distance — the order entries are pre-rendered in.
     */
    internal fun windowIndices(currentIndex: Int, size: Int): List<Int> {
        if (currentIndex !in 0 until size) return emptyList()
        val indices = mutableListOf<Int>()
        for (distance in 1..maxOf(count, behind)) {
            if (distance <= count && currentIndex + distance < size) indices += currentIndex + distance
            if (distance <= behind && currentIndex - distance >= 0) indices += currentIndex - distance
        }
        return indices
    }

    /** Whether the entry [distance] steps from the current one (negative = before) is in range. */
    internal fun covers(distance: Int): Boolean = when {
        distance > 0 -> distance <= count
        distance < 0 -> -distance <= behind
        else -> false
    }

    companion object {
        /** Pre-rendering off — the SDK's default. */
        @JvmField
        val DISABLED: PrerenderConfig = PrerenderConfig()
    }
}
