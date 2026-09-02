package io.fastpix.media3.cache

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Owns the app's single on-disk media cache.
 *
 * Media3's [SimpleCache] throws if two instances are opened over the same directory, so the cache
 * has to be a process singleton rather than per-player state. The first [getOrCreate] call wins;
 * later calls with a different [CacheConfig] reuse the already-open cache and log a warning.
 *
 * Every failure path here returns null rather than throwing: a device with no writable cache
 * directory should fall back to un-cached playback, not crash the host app.
 */
@UnstableApi
object MediaCacheProvider {

    private const val TAG = "FastPixCache"

    private val lock = Any()

    @Volatile
    private var cache: SimpleCache? = null

    private var openDirectory: File? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null

    /**
     * Returns the process-wide cache, opening it on first use.
     *
     * @return the cache, or null when [CacheConfig.enabled] is false or the cache could not be
     *   opened (in which case playback continues without caching).
     */
    @JvmStatic
    fun getOrCreate(context: Context, config: CacheConfig): Cache? {
        if (!config.enabled) return null

        cache?.let { return it }

        synchronized(lock) {
            cache?.let { open ->
                val requested = config.directory
                if (requested != null && requested.absolutePath != openDirectory?.absolutePath) {
                    Log.w(
                        TAG,
                        "Media cache already open at ${openDirectory?.absolutePath}; ignoring " +
                                "request for ${requested.absolutePath}. The cache is process-wide.",
                    )
                }
                return open
            }

            val appContext = context.applicationContext
            val directory = config.directory
                ?: File(appContext.cacheDir, CacheConfig.DEFAULT_DIRECTORY_NAME)

            return try {
                val database = StandaloneDatabaseProvider(appContext)
                val opened = SimpleCache(
                    directory,
                    LeastRecentlyUsedCacheEvictor(config.maxBytes),
                    database,
                )
                cache = opened
                openDirectory = directory
                databaseProvider = database
                opened
            } catch (t: Throwable) {
                // Full disk, unwritable path, or a folder already locked by another SimpleCache.
                Log.w(TAG, "Failed to open media cache at ${directory.absolutePath}; " +
                        "continuing without caching", t)
                null
            }
        }
    }

    /**
     * Builds the read-through [CacheDataSource.Factory] that playback and pre-caching share.
     *
     * Reads are served from [cache] when present and fall through to the network otherwise; writes
     * populate the cache as bytes arrive. [CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR] means a
     * corrupt or unwritable cache degrades to a plain network read instead of failing playback.
     */
    internal fun cacheDataSourceFactory(context: Context, cache: Cache): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context.applicationContext))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * Builds the [DataSource.Factory] the player loads media through.
     *
     * When [CacheConfig.cachePlaylists] is false (the default) the factory is wrapped so manifest
     * requests bypass the cache entirely — see [PlaylistAwareDataSourceFactory] for why.
     */
    @JvmStatic
    fun buildDataSourceFactory(
        context: Context,
        cache: Cache,
        config: CacheConfig,
    ): DataSource.Factory {
        val cacheFactory = cacheDataSourceFactory(context, cache)
        if (config.cachePlaylists) return cacheFactory
        return PlaylistAwareDataSourceFactory(
            cacheFactory = cacheFactory,
            upstreamFactory = DefaultDataSource.Factory(context.applicationContext),
        )
    }

    /** Bytes currently held on disk, or 0 when the cache is not open. */
    @JvmStatic
    fun cachedBytes(): Long = cache?.cacheSpace ?: 0L

    /**
     * Removes everything from the cache. Safe to call while players are active, though anything
     * currently being read is retained until its reader closes.
     *
     * @return true if the cache was open and the sweep completed.
     */
    @JvmStatic
    fun clear(): Boolean {
        val open = cache ?: return false
        return try {
            for (key in open.keys.toList()) {
                open.removeResource(key)
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to clear media cache", t)
            false
        }
    }

    /**
     * Closes the cache and releases its file locks. Only needed when the app wants to hand the
     * directory to something else; normal apps let the cache live for the process lifetime.
     */
    @JvmStatic
    fun release() {
        synchronized(lock) {
            try {
                cache?.release()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to release media cache", t)
            }
            cache = null
            openDirectory = null
            databaseProvider = null
        }
    }
}
