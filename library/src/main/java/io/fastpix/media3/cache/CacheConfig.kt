package io.fastpix.media3.cache

import java.io.File

/**
 * Configures the read-through media cache: downloaded segments are written to disk, so a re-watch
 * or a scroll back to an earlier feed item plays from local storage instead of the network.
 *
 * Disabled by default — enabling it means the SDK writes to the user's device, which is the app's
 * decision to make, not the SDK's.
 *
 * ```kotlin
 * FastPixPlayer.Builder(context)
 *     .setCacheConfig(CacheConfig(enabled = true))
 *     .build()
 * ```
 *
 * The cache is **process-wide**: every [io.fastpix.media3.core.FastPixPlayer] in the app shares one
 * on-disk store (Media3 forbids two `SimpleCache` instances over the same folder), so the first
 * config to reach [MediaCacheProvider] wins for the life of the process.
 *
 * Safe for any content, live included. FastPix streams are keyed by asset rather than by signed
 * URL, so their segments are reused across sessions and token refreshes without caching playlists.
 *
 * Combine with [io.fastpix.media3.preload.PreloadConfig] and upcoming playlist entries are written
 * to disk before the user reaches them.
 */
data class CacheConfig(
    /** Whether the disk cache is active. Off by default. */
    val enabled: Boolean = false,

    /**
     * Hard ceiling on disk usage. Least-recently-used content is evicted once the store exceeds
     * this. Defaults to [DEFAULT_MAX_BYTES] (256 MB).
     */
    val maxBytes: Long = DEFAULT_MAX_BYTES,

    /**
     * Whether HLS playlists (`.m3u8`) are cached alongside media segments.
     *
     * **Deprecated since 2.2.0 — leave it off.** It was needed because FastPix re-signs segment URLs
     * on every playlist fetch; the cache now keys FastPix segments by asset, so they hit without it.
     * Turning it on is only safe for on-demand content: a cached live playlist pins the player to a
     * stale segment list.
     */
    @Deprecated("No longer needed: FastPix segments are cached by asset. Leave off.")
    val cachePlaylists: Boolean = false,

    /**
     * Query parameters removed before a URL is used as a cache key.
     *
     * These authorise a request rather than select content, and they change between sessions: a
     * stream re-requested with a freshly minted `token` addresses the identical asset but would
     * otherwise miss the cache entirely. Content-selecting parameters (`maxResolution`,
     * `renditionOrder`, ...) are never stripped.
     *
     * Note the consequence of ignoring `token`: bytes fetched under one token stay readable from
     * disk after it expires, until eviction. That is ordinary HTTP-cache behaviour and DRM licences
     * are unaffected, but pass a set without `token` if your entitlements require otherwise, or
     * [emptySet] to key on the exact URL as before.
     */
    val cacheKeyIgnoredQueryParameters: Set<String> = DEFAULT_IGNORED_QUERY_PARAMETERS,

    /**
     * Directory backing the cache. Defaults to `context.cacheDir/[DEFAULT_DIRECTORY_NAME]`, which
     * the OS may reclaim under storage pressure — the right semantics for a transient cache.
     *
     * Do not point this at a directory used by any other `SimpleCache` in the app.
     */
    val directory: File? = null,
) {
    init {
        require(maxBytes > 0L) { "maxBytes must be > 0, was $maxBytes" }
    }

    companion object {
        /** Default ceiling: 256 MB. */
        const val DEFAULT_MAX_BYTES: Long = 256L * 1024L * 1024L

        /** Default folder name under `context.cacheDir`. */
        const val DEFAULT_DIRECTORY_NAME: String = "fastpix-media-cache"

        /** Query parameters stripped from cache keys by default. */
        @JvmField
        val DEFAULT_IGNORED_QUERY_PARAMETERS: Set<String> =
            setOf("token", "signature", "expires", "cdn")

        /** Cache turned off — the SDK's default. */
        @JvmField
        val DISABLED: CacheConfig = CacheConfig(enabled = false)

        /** Cache on, with the default 256 MB ceiling in the default location. */
        @JvmStatic
        @JvmOverloads
        fun enabled(maxBytes: Long = DEFAULT_MAX_BYTES): CacheConfig =
            CacheConfig(enabled = true, maxBytes = maxBytes)

        /**
         * Cache on with playlists cached too, which on-demand feeds needed before 2.2.0.
         */
        @Deprecated(
            "Playlist caching is no longer needed for FastPix segments to hit. Use enabled().",
            ReplaceWith("CacheConfig.enabled(maxBytes)"),
        )
        @JvmStatic
        @JvmOverloads
        @Suppress("DEPRECATION")
        fun forOnDemandFeed(maxBytes: Long = DEFAULT_MAX_BYTES): CacheConfig =
            CacheConfig(enabled = true, maxBytes = maxBytes, cachePlaylists = true)
    }
}
