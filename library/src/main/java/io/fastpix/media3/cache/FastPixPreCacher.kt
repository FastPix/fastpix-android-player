package io.fastpix.media3.cache

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Warms upcoming media into the disk cache so that swiping to the next item in a feed starts from
 * local bytes instead of a cold network fetch.
 *
 * Without warming, arriving at a new item costs a TLS handshake, a multivariant playlist fetch, a
 * media playlist fetch, and then the first segment — four sequential round trips before a frame can
 * be decoded. Pre-caching moves that work behind the user's current item, where there is idle
 * bandwidth to spend.
 *
 * Requires the disk cache: create it with a [CacheConfig] where [CacheConfig.enabled] is true, and
 * give the *same* config to [io.fastpix.media3.core.FastPixPlayer.Builder.setCacheConfig] so
 * playback reads from the store this writes to.
 *
 * One instance per feed is enough; it is safe to share across the pages of a feed.
 *
 * ```kotlin
 * // once, e.g. in your Application or feed ViewModel
 * val cacheConfig = CacheConfig.forOnDemandFeed()
 * val preCacher = FastPixPreCacher.create(context, cacheConfig)
 *
 * // whenever the visible page changes, hand it the next few URLs
 * preCacher?.preCache(urlsForPositions(current + 1, current + 3))
 * ```
 *
 * Warming is best-effort: every failure is swallowed (and reported to [PreCacheListener]) because a
 * failed warm must never affect playback.
 */
@UnstableApi
class FastPixPreCacher private constructor(
    context: Context,
    cache: Cache,
    private val cacheConfig: CacheConfig,
    private val config: PreCacheConfig,
) {

    private val appContext: Context = context.applicationContext
    private val cacheFactory = MediaCacheProvider.cacheDataSourceFactory(appContext, cache)
    private val upstreamFactory = DefaultDataSource.Factory(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(config.maxParallelItems)

    /** URLs currently being warmed, so a repeated request does not start a second download. */
    private val inFlight = ConcurrentHashMap<String, Warm>()

    /** Bounded record of what has already been warmed this process, to skip repeat work. */
    private val warmed = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>): Boolean =
            size > MAX_REMEMBERED_URLS
    }

    @Volatile
    private var listener: PreCacheListener? = null

    @Volatile
    private var released = false

    /**
     * Sets (or clears) the observer for warm completions and failures.
     *
     * Callbacks arrive on a background thread — post to the main thread before touching UI.
     */
    fun setListener(listener: PreCacheListener?) {
        this.listener = listener
    }

    /**
     * Warms [urls], and cancels any in-flight warm whose URL is *not* in the list.
     *
     * That cancellation is the point of passing a whole window rather than one URL at a time: when
     * the user swipes past an item you were warming, its download should stop competing with the
     * item now on screen.
     *
     * Safe to call on the main thread; all work happens on [Dispatchers.IO].
     */
    fun preCache(urls: List<String>) {
        if (released) return
        val keep = urls.toSet()
        for (url in inFlight.keys.toList()) {
            if (url !in keep) cancel(url)
        }
        for (url in urls) {
            start(url)
        }
    }

    /** [preCache] for media items already built — items with no URI are skipped. */
    fun preCacheMediaItems(mediaItems: List<MediaItem>) {
        preCache(mediaItems.mapNotNull { it.localConfiguration?.uri?.toString() })
    }

    /** Stops warming [url] if it is in flight. Already-cached bytes are kept. */
    fun cancel(url: String) {
        inFlight.remove(url)?.cancel()
    }

    /** Stops every in-flight warm. Already-cached bytes are kept. */
    fun cancelAll() {
        for (url in inFlight.keys.toList()) {
            cancel(url)
        }
    }

    /** Whether [url] has been warmed by this instance since the process started. */
    fun isWarm(url: String): Boolean = synchronized(warmed) { warmed[url] != null }

    /**
     * Cancels everything and stops accepting new work. The cache itself is process-wide and
     * outlives this object — see [MediaCacheProvider.release].
     */
    fun release() {
        released = true
        cancelAll()
        scope.cancel()
    }

    private fun start(url: String) {
        if (url.isBlank() || isWarm(url) || inFlight.containsKey(url)) return

        val warm = Warm()
        // putIfAbsent guards against two feed callbacks racing on the same URL.
        if (inFlight.putIfAbsent(url, warm) != null) return

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                gate.withPermit {
                    ensureActive()
                    val bytes = warmUrl(url, warm)
                    synchronized(warmed) { warmed[url] = true }
                    log("warmed $url ($bytes bytes)")
                    listener?.onPreCached(url, bytes)
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                // Cancelling a warm interrupts its CacheWriter, which surfaces as an IO failure.
                // That is the caller's own doing, not a fault worth reporting.
                if (!warm.cancelled) {
                    log("failed to warm $url: ${t.message}")
                    listener?.onPreCacheFailed(url, t)
                }
            } finally {
                inFlight.remove(url, warm)
            }
        }
        // Assign before starting so a cancel arriving mid-warm always finds the job.
        warm.job = job
        job.start()
    }

    private suspend fun warmUrl(url: String, warm: Warm): Long {
        val uri = Uri.parse(url)
        return if (isHlsUri(uri)) warmHls(uri, warm) else warmProgressive(uri, warm)
    }

    /**
     * Walks the HLS ladder the way the player will: multivariant playlist, chosen media playlist,
     * then the first segments of that rendition.
     */
    private suspend fun warmHls(playlistUri: Uri, warm: Warm): Long {
        val playlist = parsePlaylist(playlistUri)
        coroutineContext.ensureActive()

        val mediaPlaylist: HlsMediaPlaylist
        val mediaPlaylistUri: Uri
        when (playlist) {
            is HlsMultivariantPlaylist -> {
                val variantUri = selectVariantUri(playlist)
                    ?: throw IllegalStateException("No playable variant in $playlistUri")
                mediaPlaylistUri = variantUri
                mediaPlaylist = parsePlaylist(variantUri) as? HlsMediaPlaylist
                    ?: throw IllegalStateException("$variantUri is not a media playlist")
            }

            is HlsMediaPlaylist -> {
                mediaPlaylistUri = playlistUri
                mediaPlaylist = playlist
            }

            else -> throw IllegalStateException("Unsupported playlist type at $playlistUri")
        }

        // A live playlist's segment list is rewritten continuously; anything warmed now is likely
        // to have rolled out of the window before the user arrives.
        if (!mediaPlaylist.hasEndTag) {
            log("skipping live stream $playlistUri")
            return 0L
        }

        val baseUri = mediaPlaylist.baseUri.ifEmpty { mediaPlaylistUri.toString() }
        var written = 0L
        var initSegmentDone = false

        for (segment in mediaPlaylist.segments.take(config.segmentCount)) {
            coroutineContext.ensureActive()
            if (written >= config.maxBytesPerItem) break

            // fMP4 renditions need their initialisation segment before any media segment decodes.
            val initSegment = segment.initializationSegment
            if (!initSegmentDone && initSegment != null) {
                written += cacheSegment(baseUri, initSegment, warm, config.maxBytesPerItem - written)
                initSegmentDone = true
                if (written >= config.maxBytesPerItem) break
            }

            written += cacheSegment(baseUri, segment, warm, config.maxBytesPerItem - written)
        }
        return written
    }

    /** Progressive (MP4 and friends): just take the head of the file. */
    private suspend fun warmProgressive(uri: Uri, warm: Warm): Long {
        coroutineContext.ensureActive()
        val dataSpec = DataSpec.Builder()
            .setUri(uri)
            .setPosition(0)
            .setLength(config.maxBytesPerItem)
            .build()
        return cache(dataSpec, warm)
    }

    private suspend fun cacheSegment(
        baseUri: String,
        segment: HlsMediaPlaylist.SegmentBase,
        warm: Warm,
        remainingBytes: Long,
    ): Long {
        val segmentUri = UriUtil.resolveToUri(baseUri, segment.url)
        val hasByteRange = segment.byteRangeLength != C.LENGTH_UNSET.toLong()
        val dataSpec = DataSpec.Builder()
            .setUri(segmentUri)
            .setPosition(if (hasByteRange) segment.byteRangeOffset else 0L)
            .setLength(if (hasByteRange) segment.byteRangeLength else remainingBytes)
            .build()
        return cache(dataSpec, warm)
    }

    /** Runs one blocking [CacheWriter], recording it on [warm] so a cancel can interrupt it. */
    private fun cache(dataSpec: DataSpec, warm: Warm): Long {
        var written = 0L
        val writer = CacheWriter(
            cacheFactory.createDataSourceForDownloading(),
            dataSpec,
            null,
        ) { _, bytesCached, _ -> written = bytesCached }
        warm.currentWriter = writer
        try {
            writer.cache()
        } finally {
            warm.currentWriter = null
        }
        return written
    }

    /** Fetches and parses a playlist, honouring [CacheConfig.cachePlaylists] for where it lands. */
    private fun parsePlaylist(uri: Uri): HlsPlaylist {
        val factory = if (cacheConfig.cachePlaylists) cacheFactory else upstreamFactory
        val dataSource = factory.createDataSource()
        val bytes = try {
            dataSource.open(DataSpec(uri))
            DataSourceUtil.readToEnd(dataSource)
        } finally {
            DataSourceUtil.closeQuietly(dataSource)
        }
        return HlsPlaylistParser().parse(uri, ByteArrayInputStream(bytes))
    }

    /**
     * Picks the rendition to warm: the highest variant at or below
     * [PreCacheConfig.targetBitrateBps], or the lowest on the ladder when every variant is above it.
     */
    private fun selectVariantUri(playlist: HlsMultivariantPlaylist): Uri? {
        val variants = playlist.variants.filter { it.format.bitrate != Format.NO_VALUE }
        if (variants.isEmpty()) return playlist.variants.firstOrNull()?.url
        val atOrBelowTarget = variants.filter { it.format.bitrate <= config.targetBitrateBps }
        val chosen = atOrBelowTarget.maxByOrNull { it.format.bitrate }
            ?: variants.minByOrNull { it.format.bitrate }
        return chosen?.url
    }

    private fun isHlsUri(uri: Uri): Boolean {
        val path = uri.path?.lowercase() ?: return false
        return path.endsWith(".m3u8") || path.endsWith(".m3u")
    }

    private fun log(message: String) {
        if (config.enableLogging) Log.d(TAG, message)
    }

    /** One in-flight warm: the coroutine plus whichever [CacheWriter] is currently running. */
    private class Warm {
        @Volatile
        var job: Job? = null

        @Volatile
        var currentWriter: CacheWriter? = null

        @Volatile
        var cancelled: Boolean = false
            private set

        fun cancel() {
            cancelled = true
            // Interrupt the blocking download first, then the coroutine wrapping it.
            currentWriter?.cancel()
            job?.cancel()
        }
    }

    companion object {
        private const val TAG = "FastPixPreCache"
        private const val MAX_REMEMBERED_URLS = 256

        /**
         * Creates a pre-cacher backed by the process-wide media cache.
         *
         * @return null when [cacheConfig] is disabled or the cache could not be opened — in that
         *   case there is nowhere to warm into, and callers should simply skip pre-caching.
         */
        @JvmStatic
        @JvmOverloads
        fun create(
            context: Context,
            cacheConfig: CacheConfig,
            config: PreCacheConfig = PreCacheConfig.DEFAULT,
        ): FastPixPreCacher? {
            val cache = MediaCacheProvider.getOrCreate(context, cacheConfig) ?: return null
            return FastPixPreCacher(context, cache, cacheConfig, config)
        }
    }
}
