package io.fastpix.media3.cache

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Builds each media item's source over a cache-backed data source scoped to that item's asset, so
 * its FastPix segments are keyed by asset rather than by signed URL ([FastPixCacheKeyFactory]).
 *
 * A single factory cannot do this: Media3 hands one `DataSource.Factory` every request for every
 * item, with no way to tell which item a segment belongs to. Building per item is cheap —
 * [DefaultMediaSourceFactory] is configuration, not state.
 */
@UnstableApi
internal class ItemScopedMediaSourceFactory(
    context: Context,
    private val cache: Cache,
    private val config: CacheConfig,
) : MediaSource.Factory {

    private val appContext = context.applicationContext
    private var drmSessionManagerProvider: DrmSessionManagerProvider? = null
    private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory {
        this.drmSessionManagerProvider = drmSessionManagerProvider
        return this
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory {
        this.loadErrorHandlingPolicy = loadErrorHandlingPolicy
        return this
    }

    /** Whatever the bundled Media3 modules support, as a plain factory would report. */
    private val bundledTypes: IntArray by lazy {
        DefaultMediaSourceFactory(appContext).supportedTypes
    }

    override fun getSupportedTypes(): IntArray = bundledTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val dataSourceFactory = MediaCacheProvider.buildDataSourceFactory(
            appContext,
            cache,
            config,
            FastPixItemKeys.itemKey(mediaItem),
        )
        val factory = DefaultMediaSourceFactory(dataSourceFactory)
        drmSessionManagerProvider?.let { factory.setDrmSessionManagerProvider(it) }
        loadErrorHandlingPolicy?.let { factory.setLoadErrorHandlingPolicy(it) }
        return factory.createMediaSource(mediaItem)
    }
}
