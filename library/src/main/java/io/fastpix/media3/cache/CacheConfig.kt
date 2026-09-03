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
 * Cache entries are keyed by full playback URL. FastPix signed URLs carry a `token` query
 * parameter, so a stream re-fetched with a freshly minted token is a cache miss. Reuse the same
 * signed URL for the lifetime of its token to get the benefit.
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
     * **Off by default, and only safe for on-demand content.** A live media playlist is rewritten
     * by the origin every few seconds; serving a cached copy would pin the player to a stale
     * segment list.
     *
     * **On FastPix, turning this on is what makes segment caching work at all.** The origin
     * re-signs segment URLs on every media-playlist fetch — the same segment comes back as
     * `.../<new-signature-blob>/video_270/1.m4s` each time — so if the playlist is re-fetched, the
     * segments beneath it are addressed by URLs that were never cached, and every warmed or
     * previously downloaded byte is unreachable. Caching the playlist pins one set of segment URLs,
     * and the segments under it then hit. It also saves two round trips per item.
     *
     * So: on-demand feeds should use [forOnDemandFeed]. Leave this off only when live streams may
     * be played in the same process, accepting that segment reuse is then limited to a single
     * continuous playback.
     */
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
         * Cache on and tuned for an on-demand reel / episode feed: playlists cached too, since
         * every item in such a feed is VOD.
         */
        @JvmStatic
        @JvmOverloads
        fun forOnDemandFeed(maxBytes: Long = DEFAULT_MAX_BYTES): CacheConfig =
            CacheConfig(enabled = true, maxBytes = maxBytes, cachePlaylists = true)
    }
}
