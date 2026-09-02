package io.fastpix.media3.cache

/**
 * Tuning for [FastPixPreCacher] — how much of an upcoming item to warm before the user reaches it.
 *
 * The defaults warm roughly the first two segments (a few seconds) of each item, which is enough
 * for the player to render a first frame from disk on arrival while the rest streams normally.
 */
data class PreCacheConfig(
    /**
     * Number of media segments warmed per item, starting from the first. Two is usually enough to
     * cover the start threshold plus the first ABR decision; raising it costs the user's data.
     */
    val segmentCount: Int = 2,

    /**
     * Hard byte ceiling per item. Warming stops once this much has been written, even if
     * [segmentCount] segments have not been reached — protects against unexpectedly long segments.
     */
    val maxBytesPerItem: Long = 2L * 1024L * 1024L,

    /**
     * Bitrate (bps) the warmed rendition is chosen against: the highest variant at or below this,
     * falling back to the lowest variant on the ladder.
     *
     * The warm only pays off if playback then picks the same rendition, so keep this in line with
     * what ABR will realistically choose on the user's network — and consider pinning the ladder
     * with `maxResolution` on the media item so the two cannot diverge.
     */
    val targetBitrateBps: Int = 1_200_000,

    /**
     * How many items are warmed concurrently. Warming competes with the *playing* item for
     * bandwidth, so keep this small.
     */
    val maxParallelItems: Int = 2,

    /** Writes progress and failures to logcat under the `FastPixPreCache` tag. */
    val enableLogging: Boolean = false,
) {
    init {
        require(segmentCount > 0) { "segmentCount must be > 0, was $segmentCount" }
        require(maxBytesPerItem > 0L) { "maxBytesPerItem must be > 0, was $maxBytesPerItem" }
        require(targetBitrateBps > 0) { "targetBitrateBps must be > 0, was $targetBitrateBps" }
        require(maxParallelItems > 0) { "maxParallelItems must be > 0, was $maxParallelItems" }
    }

    companion object {
        /** Balanced default: two segments, 2 MB ceiling, two items at a time. */
        @JvmField
        val DEFAULT: PreCacheConfig = PreCacheConfig()

        /**
         * Warms only the very start of each item — one segment, 1 MB. Cheapest on data, still
         * enough to cover a 250-500 ms start threshold.
         */
        @JvmField
        val LIGHT: PreCacheConfig = PreCacheConfig(
            segmentCount = 1,
            maxBytesPerItem = 1L * 1024L * 1024L,
        )
    }
}

/** Optional observer for pre-cache activity; useful while tuning, not required in production. */
interface PreCacheListener {
    /** A URL finished warming, having written [bytesWritten] bytes to the cache. */
    fun onPreCached(url: String, bytesWritten: Long) {}

    /** A URL could not be warmed. Pre-caching is best-effort; playback is unaffected. */
    fun onPreCacheFailed(url: String, error: Throwable) {}
}
