package io.fastpix.media3.cache

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheKeyFactory

/**
 * Keys cache entries by URL with volatile query parameters removed.
 *
 * Media3's default keys on the full URL, so a stream re-requested with a freshly minted
 * `?token=` — the normal pattern for FastPix secure playback, where a token is minted per session —
 * is a complete cache miss even though it addresses the identical asset. Dropping the parameters
 * that authorise a request rather than select content makes those requests hit.
 *
 * **Signed paths.** FastPix segment and media-playlist URLs carry a signature in the *path*
 * (`https://cdn.fastpix.io/<signed-blob>/video_1080/1.m4s`) that changes on every playlist fetch,
 * so keying on the URL would never hit. The stable remainder (`video_1080/1.m4s`) is the same for
 * every asset, so it cannot be the key on its own either. When the factory knows which asset it is
 * loading for ([itemKey], one factory per media item), such URLs are keyed as that asset plus the
 * stable remainder — which is unique and survives re-signing. Without an [itemKey] they fall back to
 * query normalisation only.
 *
 * A `DataSpec` carrying an explicit key is passed through untouched.
 */
@UnstableApi
internal class FastPixCacheKeyFactory(
    ignoredQueryParameters: Set<String>,
    /** Asset this factory loads for (see [FastPixItemKeys]); null when unknown. */
    private val itemKey: String? = null,
) : CacheKeyFactory {

    private val ignored: Set<String> = ignoredQueryParameters.mapTo(HashSet()) { it.lowercase() }

    override fun buildCacheKey(dataSpec: DataSpec): String {
        dataSpec.key?.let { return it }
        val url = dataSpec.uri.toString()
        return itemScoped(url) ?: normalize(url)
    }

    /**
     * `fastpix-item:<itemKey>/<path after the signed segment><normalised query>` for a URL whose
     * first path segment is a signature blob, or null when there is no [itemKey] or the URL has no
     * such segment (a stream URL like `stream.fastpix.com/<id>.m3u8`, say).
     */
    internal fun itemScoped(url: String): String? {
        val key = itemKey ?: return null
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return null
        val pathStart = url.indexOf('/', schemeEnd + 3)
        if (pathStart < 0) return null
        val pathEnd = url.indexOfAny(charArrayOf('?', '#'), pathStart)
            .let { if (it < 0) url.length else it }

        val segments = url.substring(pathStart + 1, pathEnd).split('/')
        if (segments.size < 2 || segments[0].length < MIN_SIGNED_SEGMENT_LENGTH) return null

        val stablePath = segments.drop(1).joinToString("/")
        return SCOPED_KEY_PREFIX + key + "/" + stablePath + normalizeSuffix(url.substring(pathEnd))
    }

    /**
     * Strips [ignored] parameters from [url]'s query string, preserving the order and the exact
     * encoding of everything kept. Parameters that select content — `maxResolution`,
     * `renditionOrder` and the like — must survive, so this is a deny-list rather than an
     * allow-list.
     */
    internal fun normalize(url: String): String {
        if (ignored.isEmpty()) return url

        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        return url.substring(0, queryStart) + normalizeSuffix(url.substring(queryStart))
    }

    /** [normalize] for the part of a URL from its `?` (or `#`) onwards. */
    private fun normalizeSuffix(suffix: String): String {
        if (ignored.isEmpty() || !suffix.startsWith("?")) return suffix

        val fragmentStart = suffix.indexOf('#')
        val query = if (fragmentStart < 0) suffix.substring(1) else suffix.substring(1, fragmentStart)
        val fragment = if (fragmentStart < 0) "" else suffix.substring(fragmentStart)

        val kept = query.split('&').filter { parameter ->
            parameter.isNotEmpty() &&
                    parameter.substringBefore('=').lowercase() !in ignored
        }

        return if (kept.isEmpty()) fragment
        else "?" + kept.joinToString("&") + fragment
    }

    companion object {
        internal const val SCOPED_KEY_PREFIX = "fastpix-item:"

        /**
         * Shortest first path segment treated as a signature blob. FastPix blobs run to hundreds of
         * characters; ordinary directory names are far below this.
         */
        internal const val MIN_SIGNED_SEGMENT_LENGTH = 64
    }
}
