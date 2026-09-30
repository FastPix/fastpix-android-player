package io.fastpix.media3.cache

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations

/**
 * Keeps HLS playlists on disk for as long as they are safe to reuse.
 *
 * Playlists dominate a warm start: with the segments already on disk, the multivariant playlist
 * and the media playlists are still sequential network round trips (~1 s on a phone) before the
 * first frame. Caching them blindly is unsafe in two ways, and this store guards against both:
 *
 * - **Live.** A live media playlist is rewritten every few seconds, so only multivariant playlists
 *   and *finished* media playlists (`#EXT-X-ENDLIST`) are stored.
 * - **Signed URLs.** A FastPix playlist lists child URLs signed with an `expires=` timestamp. A
 *   stored playlist is served only until the earliest such timestamp in it (or in its own URL),
 *   less [EXPIRY_MARGIN_MS], and never for longer than [maxAgeMs]; after that it is re-fetched.
 *
 * Every failure falls back to the network: a broken store costs a round trip, never playback.
 */
@UnstableApi
internal class PlaylistStore(
    private val cache: Cache,
    private val keyFactory: CacheKeyFactory,
    private val maxAgeMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Returns the playlist at [dataSpec], from disk when a fresh copy exists, else via [upstream]. */
    fun load(dataSpec: DataSpec, upstream: DataSource): ByteArray {
        val key = keyFactory.buildCacheKey(dataSpec)
        readFresh(key)?.let { return it }

        val bytes = try {
            upstream.open(dataSpec.buildUpon().setPosition(0).setLength(-1).build())
            DataSourceUtil.readToEnd(upstream)
        } finally {
            DataSourceUtil.closeQuietly(upstream)
        }
        freshUntil(dataSpec.uri.toString(), bytes)?.let { runCatching { write(key, bytes, it) } }
        return bytes
    }

    private fun readFresh(key: String): ByteArray? = runCatching {
        val metadata = cache.getContentMetadata(key)
        val length = ContentMetadata.getContentLength(metadata)
        if (length <= 0 || metadata.get(FRESH_UNTIL, 0L) <= clock()) return null
        if (!cache.isCached(key, 0, length)) return null

        val source = CacheDataSource(cache, /* upstreamDataSource= */ null)
        try {
            source.open(DataSpec.Builder().setUri("fastpix-playlist://cache").setKey(key).build())
            DataSourceUtil.readToEnd(source)
        } finally {
            DataSourceUtil.closeQuietly(source)
        }
    }.getOrNull()

    private fun write(key: String, bytes: ByteArray, freshUntil: Long) {
        cache.removeResource(key)
        // startFile requires the span to be locked, exactly as CacheDataSource does before writing.
        val span = cache.startReadWriteNonBlocking(key, 0, bytes.size.toLong()) ?: return
        try {
            if (span.isCached) return
            val sink = CacheDataSink(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE)
            sink.open(
                DataSpec.Builder()
                    .setUri("fastpix-playlist://cache")
                    .setKey(key)
                    .setLength(bytes.size.toLong())
                    .build()
            )
            try {
                sink.write(bytes, 0, bytes.size)
            } finally {
                sink.close()
            }
        } finally {
            cache.releaseHoleSpan(span)
        }
        val mutations = ContentMetadataMutations().set(FRESH_UNTIL, freshUntil)
        ContentMetadataMutations.setContentLength(mutations, bytes.size.toLong())
        cache.applyContentMetadataMutations(key, mutations)
    }

    /** When a playlist fetched from [url] with body [bytes] stops being safe to serve, or null. */
    private fun freshUntil(url: String, bytes: ByteArray): Long? =
        freshUntil(url, String(bytes, Charsets.UTF_8), clock(), maxAgeMs)

    companion object {
        private const val FRESH_UNTIL = "fastpix-fresh-until"

        /** Stop serving a stored playlist this long before any URL in it expires. */
        const val EXPIRY_MARGIN_MS: Long = 10 * 60 * 1000L

        /** `expires` values below this are Unix seconds, above it milliseconds. */
        private const val SECONDS_THRESHOLD = 100_000_000_000L

        private val EXPIRES = Regex("[?&]expires=(\\d+)", RegexOption.IGNORE_CASE)

        /**
         * Until when a playlist fetched from [url] with body [text] at [now] may be served from
         * disk, or null when it must not be stored at all (live media playlist, or already expiring).
         */
        internal fun freshUntil(url: String, text: String, now: Long, maxAgeMs: Long): Long? {
            if (maxAgeMs <= 0) return null
            val isMultivariant = text.contains("#EXT-X-STREAM-INF") ||
                (text.contains("#EXT-X-MEDIA:") && !text.contains("#EXTINF"))
            if (!isMultivariant && !text.contains("#EXT-X-ENDLIST")) return null

            var until = now + maxAgeMs
            val earliestExpiry = (EXPIRES.findAll(url) + EXPIRES.findAll(text))
                .mapNotNull { it.groupValues[1].toLongOrNull() }
                .map { if (it < SECONDS_THRESHOLD) it * 1000 else it }
                .minOrNull()
            if (earliestExpiry != null) until = minOf(until, earliestExpiry - EXPIRY_MARGIN_MS)
            return until.takeIf { it > now }
        }
    }
}
