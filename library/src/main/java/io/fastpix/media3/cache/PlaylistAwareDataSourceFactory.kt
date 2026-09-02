package io.fastpix.media3.cache

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Routes manifest requests around the cache while letting media segments be cached.
 *
 * A cached HLS media playlist is correct for VOD and wrong for live: the origin rewrites a live
 * playlist every few seconds, so a cached copy pins the player to a segment list that no longer
 * exists. Since a FastPix VOD and live URL are the same shape (`{playbackId}.m3u8`), the SDK
 * cannot tell them apart, so the safe default is to always fetch playlists from the network and
 * cache only the segment bytes underneath them.
 *
 * Apps that play only on-demand content can opt back in with [CacheConfig.cachePlaylists], in
 * which case this wrapper is not used at all.
 */
@UnstableApi
internal class PlaylistAwareDataSourceFactory(
    private val cacheFactory: DataSource.Factory,
    private val upstreamFactory: DataSource.Factory,
) : DataSource.Factory {

    override fun createDataSource(): DataSource =
        PlaylistAwareDataSource(cacheFactory.createDataSource(), upstreamFactory.createDataSource())
}

/**
 * Picks its delegate at [open] time from the requested URI, then forwards everything to it.
 * Media3 uses a `DataSource` from one loader thread at a time, so the mutable delegate is safe.
 */
@UnstableApi
private class PlaylistAwareDataSource(
    private val cached: DataSource,
    private val uncached: DataSource,
) : DataSource {

    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        // The delegate is unknown until open(), so both must carry every listener.
        cached.addTransferListener(transferListener)
        uncached.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val delegate = if (isManifest(dataSpec.uri)) uncached else cached
        active = delegate
        return delegate.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        requireOpen().read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders ?: emptyMap()

    override fun close() {
        val delegate = active
        active = null
        delegate?.close()
    }

    private fun requireOpen(): DataSource =
        active ?: throw IllegalStateException("read() called before open()")

    private fun isManifest(uri: Uri): Boolean {
        val path = uri.path?.lowercase() ?: return false
        return path.endsWith(".m3u8") || path.endsWith(".m3u") || path.endsWith(".mpd")
    }
}
