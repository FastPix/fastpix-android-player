package io.fastpix.media3.preload

/**
 * Enables ExoPlayer's built-in preloading of the **next item in the player's own playlist**.
 *
 * This only does something when the app queues media with
 * [io.fastpix.media3.core.FastPixPlayer.setMediaItems] — one player holding the feed as a queue.
 * A feed that gives every page its own player and calls
 * [io.fastpix.media3.core.FastPixPlayer.setMediaItem] has no "next item" from the engine's point of
 * view; use [io.fastpix.media3.cache.FastPixPreCacher] for that shape instead. The two can be used
 * together.
 *
 * Preloaded data is held by the player and, when the disk cache is on, also written through to it.
 */
data class PreloadConfig(
    /** Whether the next playlist item is preloaded. Off by default. */
    val enabled: Boolean = false,

    /**
     * How much of the next item to preload, in milliseconds of media. Larger values start the next
     * item faster and cost more of the user's data if they never reach it.
     */
    val targetPreloadDurationMs: Long = 3_000L,
) {
    init {
        require(targetPreloadDurationMs > 0L) {
            "targetPreloadDurationMs must be > 0, was $targetPreloadDurationMs"
        }
    }

    companion object {
        /** Preloading off — the SDK's default. */
        @JvmField
        val DISABLED: PreloadConfig = PreloadConfig(enabled = false)

        /** Preloading on with the default 3 s target. */
        @JvmField
        val ENABLED: PreloadConfig = PreloadConfig(enabled = true)

        /**
         * Short-form feed: preload 5 s of the next reel. Reels are small, so a generous target is
         * cheap and covers the whole visible portion of a quick swipe-through.
         */
        @JvmField
        val FEED: PreloadConfig = PreloadConfig(enabled = true, targetPreloadDurationMs = 5_000L)
    }
}
