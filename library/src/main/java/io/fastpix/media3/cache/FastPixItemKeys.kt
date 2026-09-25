package io.fastpix.media3.cache

import androidx.media3.common.MediaItem

/**
 * Identifies which FastPix asset a media item plays, so its cache entries can be keyed per asset
 * rather than per signed URL — see [FastPixCacheKeyFactory].
 */
internal object FastPixItemKeys {

    /**
     * Tag the SDK stamps on media items it builds from a playback ID. Lets items served from a
     * custom domain, whose URL the SDK cannot recognise, still be keyed by their asset.
     */
    internal data class FastPixItemTag(val playbackId: String)

    /** FastPix stream hosts: `stream.fastpix.<tld>`, optionally with an environment prefix. */
    private val STREAM_HOST = Regex("^(?:[a-z0-9-]+-)?stream\\.fastpix\\.[a-z]+$")

    /** The asset key for [mediaItem], or null when it is not recognisably a FastPix stream. */
    fun itemKey(mediaItem: MediaItem): String? {
        val configuration = mediaItem.localConfiguration ?: return null
        (configuration.tag as? FastPixItemTag)?.let { return it.playbackId }
        return fromStreamUrl(configuration.uri.toString())
    }

    /**
     * The playback ID in a FastPix stream URL (`https://stream.fastpix.com/<playbackId>.m3u8`), or
     * null for any other URL. Deliberately strict: a wrong key merges two assets' cache entries.
     */
    fun fromStreamUrl(url: String?): String? {
        if (url == null) return null
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return null
        val hostStart = schemeEnd + 3
        val pathStart = url.indexOf('/', hostStart)
        if (pathStart < 0) return null
        val host = url.substring(hostStart, pathStart).substringBefore(':').lowercase()
        if (!STREAM_HOST.matches(host)) return null

        val pathEnd = url.indexOfAny(charArrayOf('?', '#'), pathStart).let { if (it < 0) url.length else it }
        val path = url.substring(pathStart + 1, pathEnd)
        if ('/' in path || !path.endsWith(".m3u8")) return null
        return path.removeSuffix(".m3u8").takeIf { it.isNotEmpty() }
    }
}
