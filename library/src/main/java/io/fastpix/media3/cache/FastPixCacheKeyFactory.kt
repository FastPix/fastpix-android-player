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
 * **Scope, stated plainly:** this only normalises the query string. Where a CDN embeds a rotating
 * signature in the *path* it cannot help, because the stable remainder of such a path
 * (`.../video_270/1.m4s`) is not unique across assets and normalising on it would serve one video's
 * bytes for another. On FastPix that is the case for segment URLs, which is why segment reuse
 * depends on [CacheConfig.cachePlaylists] instead — see that field's documentation.
 *
 * A `DataSpec` carrying an explicit key is passed through untouched.
 */
@UnstableApi
internal class FastPixCacheKeyFactory(
    ignoredQueryParameters: Set<String>,
) : CacheKeyFactory {

    private val ignored: Set<String> = ignoredQueryParameters.mapTo(HashSet()) { it.lowercase() }

    override fun buildCacheKey(dataSpec: DataSpec): String {
        dataSpec.key?.let { return it }
        return normalize(dataSpec.uri.toString())
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

        val fragmentStart = url.indexOf('#', queryStart)
        val base = url.substring(0, queryStart)
        val query = if (fragmentStart < 0) {
            url.substring(queryStart + 1)
        } else {
            url.substring(queryStart + 1, fragmentStart)
        }
        val fragment = if (fragmentStart < 0) "" else url.substring(fragmentStart)

        val kept = query.split('&').filter { parameter ->
            parameter.isNotEmpty() &&
                    parameter.substringBefore('=').lowercase() !in ignored
        }

        return if (kept.isEmpty()) base + fragment
        else base + "?" + kept.joinToString("&") + fragment
    }
}
