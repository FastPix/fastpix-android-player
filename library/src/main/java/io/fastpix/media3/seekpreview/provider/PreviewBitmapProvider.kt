package io.fastpix.media3.seekpreview.provider

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.fastpix.media3.seekpreview.cache.CacheManager
import io.fastpix.media3.seekpreview.cache.VideoHashUtil
import io.fastpix.media3.seekpreview.mapper.PreviewMapper
import io.fastpix.media3.seekpreview.models.SeekPreviewConfig
import io.fastpix.media3.seekpreview.models.SpritesheetMetadata
import io.fastpix.media3.seekpreview.parser.SpritesheetParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Provides bitmap previews for seek positions with preloading support.
 * Supports 60fps scrubbing with configurable preload radius.
 */
interface PreviewBitmapProvider {

    /**
     * Gets a preview bitmap for a specific time position.
     *
     * @param timeMs Time position in milliseconds
     * @return The preview bitmap, or null if unavailable
     */
    suspend fun getPreviewBitmap(timeMs: Long): Bitmap?

    /**
     * Preloads surrounding frames for smooth scrubbing.
     *
     * @param centerTimeMs Center time position
     * @param radius Preload radius (number of frames in each direction)
     */
    fun preloadFrames(centerTimeMs: Long, radius: Int)

    /**
     * Clears the memory cache.
     */
    fun clearCache()
}

/**
 * Default implementation of [PreviewBitmapProvider].
 *
 * Decodes the full spritesheet image **once** into memory and crops individual frames from it.
 * Frame extraction is a fast bitmap crop (microseconds), so preloading is not needed.
 */
internal class PreviewBitmapProviderImpl(
    private val spritesheetFile: File,
    private val metadata: SpritesheetMetadata,
    private val parser: SpritesheetParser,
    private val mapper: PreviewMapper,
    private val cacheManager: CacheManager,
    private val config: SeekPreviewConfig,
    private val scope: CoroutineScope
) : PreviewBitmapProvider {

    private val videoHash: String = VideoHashUtil.hashFromFile(spritesheetFile)

    /** Full decoded spritesheet bitmap, loaded lazily on first frame request. */
    @Volatile
    private var fullBitmap: Bitmap? = null
    private val decodeLock = Any()

    /**
     * Returns the cached full bitmap, or decodes it from disk on first call.
     * Thread-safe via double-checked locking.
     */
    private fun getOrDecodeFullBitmap(): Bitmap? {
        fullBitmap?.let { if (!it.isRecycled) return it }
        synchronized(decodeLock) {
            fullBitmap?.let { if (!it.isRecycled) return it }
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(spritesheetFile.absolutePath, options)
            fullBitmap = bitmap
            return bitmap
        }
    }

    /**
     * Extracts a single frame from the cached full spritesheet bitmap.
     * This is a fast bitmap crop — no file I/O.
     */
    private fun extractFrame(frameIndex: Int): Bitmap? {
        if (frameIndex < 0 || frameIndex >= metadata.frameCount) return null
        val source = getOrDecodeFullBitmap() ?: return null

        val row = frameIndex / metadata.columns
        val column = frameIndex % metadata.columns
        val x = column * metadata.frameWidth
        val y = row * metadata.frameHeight

        return try {
            Bitmap.createBitmap(source, x, y, metadata.frameWidth, metadata.frameHeight)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getPreviewBitmap(timeMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val frameIndex = mapper.timeToFrameIndex(timeMs, metadata)
        if (frameIndex < 0) return@withContext null

        val cacheKey = "${videoHash}_$frameIndex"

        // Check memory cache first
        cacheManager.getCachedBitmap(cacheKey)?.let { return@withContext it }

        // Extract frame from cached full bitmap (fast crop, no file I/O)
        val bitmap = extractFrame(frameIndex)

        // Cache if successful
        bitmap?.let { cacheManager.cacheBitmap(cacheKey, it) }

        bitmap
    }

    override fun preloadFrames(centerTimeMs: Long, radius: Int) {
        // No-op: frame extraction from the cached bitmap is instant,
        // so preloading provides no benefit.
    }

    override fun clearCache() {
        synchronized(decodeLock) {
            fullBitmap = null
        }
        cacheManager.invalidateCache(videoHash)
    }
}