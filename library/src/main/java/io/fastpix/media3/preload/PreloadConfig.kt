package io.fastpix.media3.preload

/**
 * How many playlist entries around the current one the player prepares ahead of time, so moving to
 * them starts without waiting on the network.
 *
 * ```kotlin
 * FastPixPlayer.Builder(context)
 *     .setPreloadConfig(PreloadConfig(count = 3))             // the next 3 entries
 *     .build()
 *
 * FastPixPlayer.Builder(context)
 *     .setPreloadConfig(PreloadConfig(count = 3, behind = 1)) // and the previous one, for going back
 *     .build()
 * ```
 *
 * Acts on the playlist set with [io.fastpix.media3.core.FastPixPlayer.setPlaylist]; a single item
 * set with `setMediaItem` has nothing around it to preload.
 *
 * What the SDK does for each entry in range:
 * - **Adjacent entries** (one step away) have their first seconds buffered in memory, so they can
 *   start almost immediately.
 * - **Entries further out** have their playlists fetched and tracks chosen — little memory, but
 *   it removes the round trips before the first segment request.
 * - **With the disk cache on** ([io.fastpix.media3.cache.CacheConfig]), entries further out also
 *   have their first segments written to disk, in the rendition playback will pick.
 *
 * Off by default: preloading spends the user's data on entries they may never reach.
 */
data class PreloadConfig(
    /** Entries after the current one to preload. 0 turns look-ahead off. */
    val count: Int = 0,

    /** Entries before the current one to keep preloaded, for going back. 0 by default. */
    val behind: Int = 0,
) {
    init {
        require(count >= 0) { "count must be >= 0, was $count" }
        require(behind >= 0) { "behind must be >= 0, was $behind" }
    }

    /**
     * How much of an adjacent entry is buffered in memory. Fixed by the SDK; only the deprecated
     * constructor sets it.
     */
    internal var bufferedDurationMs: Long = DEFAULT_BUFFERED_DURATION_MS
        private set

    /** Pre-2.2.0 form: preloads the next entry only, buffering [targetPreloadDurationMs] of it. */
    @Deprecated(
        message = "Use PreloadConfig(count = 1). The buffered amount is managed by the SDK.",
        replaceWith = ReplaceWith("PreloadConfig(count = if (enabled) 1 else 0)"),
    )
    constructor(
        enabled: Boolean,
        targetPreloadDurationMs: Long = DEFAULT_BUFFERED_DURATION_MS,
    ) : this(count = if (enabled) 1 else 0) {
        require(targetPreloadDurationMs > 0L) {
            "targetPreloadDurationMs must be > 0, was $targetPreloadDurationMs"
        }
        bufferedDurationMs = targetPreloadDurationMs
    }

    /** Whether anything is preloaded. */
    val enabled: Boolean get() = count > 0 || behind > 0

    @Deprecated("The buffered amount is managed by the SDK.")
    val targetPreloadDurationMs: Long get() = bufferedDurationMs

    companion object {
        internal const val DEFAULT_BUFFERED_DURATION_MS: Long = 3_000L

        /** Preloading off — the SDK's default. */
        @JvmField
        val DISABLED: PreloadConfig = PreloadConfig()

        /** The next entry. */
        @Deprecated("Use PreloadConfig(count = 1).", ReplaceWith("PreloadConfig(count = 1)"))
        @JvmField
        val ENABLED: PreloadConfig = PreloadConfig(count = 1)

        /** The next entry, with 5 s of it buffered. */
        @Deprecated("Use PreloadConfig(count = 1).", ReplaceWith("PreloadConfig(count = 1)"))
        @JvmField
        @Suppress("DEPRECATION")
        val FEED: PreloadConfig = PreloadConfig(enabled = true, targetPreloadDurationMs = 5_000L)
    }
}
