package io.fastpix.app

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Plain-Media3 pre-cacher for [TestActivity]: warms the start of each URL into a disk cache so the
 * player can read the first bytes locally instead of from the network.
 *
 * For HLS it caches the multivariant playlist, every media playlist, and the first
 * [SEGMENTS_PER_RENDITION] segments of *every* rendition. Warming every rendition means the cache
 * hits no matter which variant ExoPlayer's ABR picks at startup. Progressive URLs (mp4 etc.) get
 * their first [PROGRESSIVE_BYTES] bytes cached.
 *
 * Uses its own cache directory, separate from the SDK's MediaCacheProvider, because SimpleCache
 * cannot have two instances open on the same folder.
 */
@UnstableApi
object TestPreCacher {

    enum class State { WARMING, WARM, FAILED }

    private const val TAG = "TestPreCacher"
    private const val CACHE_DIR = "test_media_cache"
    private const val MAX_CACHE_BYTES = 200L * 1024 * 1024
    private const val SEGMENTS_PER_RENDITION = 1
    private const val PROGRESSIVE_BYTES = 2L * 1024 * 1024

    private val executor = Executors.newFixedThreadPool(2)
    private val states = ConcurrentHashMap<String, State>()
    private val bytesWarmed = ConcurrentHashMap<String, Long>()

    @Volatile
    private var cache: SimpleCache? = null

    private fun cache(context: Context): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.applicationContext.cacheDir, CACHE_DIR),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                StandaloneDatabaseProvider(context.applicationContext),
            ).also { cache = it }
        }

    /** Read-through factory: serves cached bytes, falls back to network and caches what it reads. */
    fun dataSourceFactory(context: Context): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context.applicationContext))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun state(url: String): State? = states[url]

    fun warmedBytes(url: String): Long = bytesWarmed[url] ?: 0L

    /** Queues [urls] for warming. Already warm or in-flight URLs are skipped. */
    fun preCache(context: Context, urls: List<String>) {
        val factory = dataSourceFactory(context)
        urls.filter { it.isNotBlank() }.forEach { url ->
            if (states.putIfAbsent(url, State.WARMING) != null) return@forEach
            executor.execute {
                val start = System.currentTimeMillis()
                try {
                    val bytes = warm(factory, Uri.parse(url))
                    bytesWarmed[url] = bytes
                    states[url] = State.WARM
                    Log.d(TAG, "warmed ${bytes / 1024} KB in ${System.currentTimeMillis() - start} ms: $url")
                } catch (t: Throwable) {
                    states[url] = State.FAILED
                    Log.w(TAG, "warm failed: $url", t)
                }
            }
        }
    }

    /** Empties the cache so the next open is a cold start again. */
    fun clear(context: Context) {
        val c = cache(context)
        c.keys.toList().forEach { c.removeResource(it) }
        states.clear()
        bytesWarmed.clear()
    }

    private fun warm(factory: CacheDataSource.Factory, uri: Uri): Long {
        if (uri.lastPathSegment?.endsWith(".m3u8", ignoreCase = true) != true) {
            return cacheSpec(factory, DataSpec.Builder().setUri(uri).setLength(PROGRESSIVE_BYTES).build())
        }

        var total = 0L
        val (root, rootBytes) = fetchPlaylist(factory, uri)
        total += rootBytes

        val mediaPlaylistUris = when (root) {
            is HlsMultivariantPlaylist -> root.mediaPlaylistUrls.distinct()
            else -> emptyList()
        }
        val mediaPlaylists = if (root is HlsMediaPlaylist) listOf(root) else mediaPlaylistUris.mapNotNull {
            runCatching { fetchPlaylist(factory, it) }.getOrNull()?.let { (p, b) ->
                total += b
                p as? HlsMediaPlaylist
            }
        }

        mediaPlaylists.forEach { playlist ->
            val base = playlist.baseUri
            playlist.segments.firstOrNull()?.initializationSegment?.let { init ->
                total += cacheSegment(factory, base, init)
            }
            playlist.segments.take(SEGMENTS_PER_RENDITION).forEach { segment ->
                total += cacheSegment(factory, base, segment)
            }
        }
        return total
    }

    private fun fetchPlaylist(factory: CacheDataSource.Factory, uri: Uri): Pair<Any, Long> {
        val dataSource = factory.createDataSource()
        val bytes = try {
            dataSource.open(DataSpec(uri))
            DataSourceUtil.readToEnd(dataSource)
        } finally {
            DataSourceUtil.closeQuietly(dataSource)
        }
        return HlsPlaylistParser().parse(uri, ByteArrayInputStream(bytes)) to bytes.size.toLong()
    }

    private fun cacheSegment(factory: CacheDataSource.Factory, baseUri: String, segment: HlsMediaPlaylist.Segment): Long {
        val builder = DataSpec.Builder().setUri(UriUtil.resolveToUri(baseUri, segment.url))
        if (segment.byteRangeLength != C.LENGTH_UNSET.toLong()) {
            builder.setPosition(segment.byteRangeOffset).setLength(segment.byteRangeLength)
        }
        return cacheSpec(factory, builder.build())
    }

    private fun cacheSpec(factory: CacheDataSource.Factory, spec: DataSpec): Long {
        var written = 0L
        CacheWriter(factory.createDataSource(), spec, null) { _, bytesCached, _ ->
            written = bytesCached
        }.cache()
        return written
    }
}
