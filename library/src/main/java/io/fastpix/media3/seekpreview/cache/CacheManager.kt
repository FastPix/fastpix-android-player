package io.fastpix.player.seekpreview.cache

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import io.fastpix.player.seekpreview.models.SpritesheetMetadata
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Manages disk and memory caching for sprite sheets and frame bitmaps.
 */
interface CacheManager {
    
    /**
     * Gets the cached sprite sheet PNG file for a video.
     * 
     * @param videoHash Unique hash identifying the video
     * @return Cached PNG file, or null if not cached
     */
    fun getCachedSpritesheet(videoHash: String): File?
    
    /**
     * Gets the cached metadata JSON file for a video.
     * 
     * @param videoHash Unique hash identifying the video
     * @return Cached JSON file, or null if not cached
     */
    fun getCachedMetadata(videoHash: String): File?
    
    /**
     * Caches a sprite sheet PNG file.
     * 
     * @param videoHash Unique hash identifying the video
     * @param spritesheetFile The PNG file to cache
     * @return true if caching succeeded, false otherwise
     */
    fun cacheSpritesheet(videoHash: String, spritesheetFile: File): Boolean
    
    /**
     * Caches metadata JSON file.
     * 
     * @param videoHash Unique hash identifying the video
     * @param metadataFile The JSON file to cache
     * @return true if caching succeeded, false otherwise
     */
    fun cacheMetadata(videoHash: String, metadataFile: File): Boolean
    
    /**
     * Gets a cached frame bitmap from memory cache.
     * 
     * @param cacheKey Unique key for the frame (e.g., "videoHash_frameIndex")
     * @return Cached bitmap, or null if not cached
     */
    fun getCachedBitmap(cacheKey: String): Bitmap?
    
    /**
     * Caches a frame bitmap in memory.
     * 
     * @param cacheKey Unique key for the frame
     * @param bitmap The bitmap to cache
     */
    fun cacheBitmap(cacheKey: String, bitmap: Bitmap)
    
    /**
     * Invalidates cache for a video (removes both disk and memory entries).
     * 
     * @param videoHash Unique hash identifying the video
     */
    fun invalidateCache(videoHash: String)
    
    /**
     * Clears all caches.
     */
    fun clearAll()
    
    /**
     * Validates that cached files match the expected metadata.
     * 
     * @param videoHash Unique hash identifying the video
     * @param expectedMetadata The expected metadata
     * @return true if cached files are valid, false otherwise
     */
    fun validateCache(videoHash: String, expectedMetadata: SpritesheetMetadata?): Boolean
}

/**
 * Default implementation of [CacheManager] with disk and LRU memory cache.
 * Memory cache access is synchronized because LruCache is not thread-safe and
 * multiple coroutines (preload + getPreviewBitmap) can access it concurrently.
 */
internal class CacheManagerImpl(
    private val context: Context,
    private val maxMemoryCacheSize: Int = calculateMemoryCacheSize()
) : CacheManager {
    
    private val cacheDir: File = File(context.cacheDir, "seekpreview")
    private val memoryCache: LruCache<String, Bitmap>
    private val memoryCacheLock = Any()
    
    init {
        cacheDir.mkdirs()
        
        memoryCache = object : LruCache<String, Bitmap>(maxMemoryCacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount
            }
        }
    }
    
    override fun getCachedSpritesheet(videoHash: String): File? {
        val file = File(cacheDir, "${videoHash}_spritesheet.png")
        return file.takeIf { it.exists() && it.length() > 0 }
    }
    
    override fun getCachedMetadata(videoHash: String): File? {
        val file = File(cacheDir, "${videoHash}_metadata.json")
        return file.takeIf { it.exists() && it.length() > 0 }
    }
    
    override fun cacheSpritesheet(videoHash: String, spritesheetFile: File): Boolean {
        if (!spritesheetFile.exists()) return false

        return try {
            val destFile = File(cacheDir, "${videoHash}_spritesheet.png")
            // Skip if source is already the cached file (copyTo with overwrite deletes target first)
            if (spritesheetFile.canonicalPath == destFile.canonicalPath) return true
            spritesheetFile.copyTo(destFile, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun cacheMetadata(videoHash: String, metadataFile: File): Boolean {
        if (!metadataFile.exists()) return false

        return try {
            val destFile = File(cacheDir, "${videoHash}_metadata.json")
            // Skip if source is already the cached file (copyTo with overwrite deletes target first)
            if (metadataFile.canonicalPath == destFile.canonicalPath) return true
            metadataFile.copyTo(destFile, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getCachedBitmap(cacheKey: String): Bitmap? {
        synchronized(memoryCacheLock) {
            return memoryCache.get(cacheKey)
        }
    }
    
    override fun cacheBitmap(cacheKey: String, bitmap: Bitmap) {
        synchronized(memoryCacheLock) {
            memoryCache.put(cacheKey, bitmap)
        }
    }
    
    override fun invalidateCache(videoHash: String) {
        // Remove from disk
        getCachedSpritesheet(videoHash)?.delete()
        getCachedMetadata(videoHash)?.delete()
        
        // Remove from memory cache (all frames for this video)
        synchronized(memoryCacheLock) {
            val keysToRemove = memoryCache.snapshot().keys.filter { it.startsWith("${videoHash}_") }
            keysToRemove.forEach { memoryCache.remove(it) }
        }
    }
    
    override fun clearAll() {
        // Clear disk cache
        cacheDir.listFiles()?.forEach { it.delete() }
        
        // Clear memory cache
        synchronized(memoryCacheLock) {
            memoryCache.evictAll()
        }
    }
    
    override fun validateCache(videoHash: String, expectedMetadata: SpritesheetMetadata?): Boolean {
        val spritesheetFile = getCachedSpritesheet(videoHash) ?: return false
        val metadataFile = getCachedMetadata(videoHash) ?: return false
        
        // If expected metadata is provided, validate against it
        expectedMetadata?.let { metadata ->
            // TODO: Parse cached metadata and compare
            // For now, just check that files exist and are non-empty
            return spritesheetFile.length() > 0 && metadataFile.length() > 0
        }
        
        return spritesheetFile.length() > 0 && metadataFile.length() > 0
    }
    
    companion object {
        /**
         * Calculates appropriate memory cache size (1/8 of available memory).
         */
        private fun calculateMemoryCacheSize(): Int {
            val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            return maxMemory / 8
        }
    }
}

/**
 * Utility for generating video hash from file path or URL.
 */
object VideoHashUtil {
    
    /**
     * Generates a hash from a file path.
     */
    fun hashFromFile(file: File): String {
        val input = "${file.absolutePath}_${file.length()}_${file.lastModified()}"
        return hashString(input)
    }
    
    /**
     * Generates a hash from a URL string.
     */
    fun hashFromUrl(url: String): String {
        return hashString(url)
    }
    
    private fun hashString(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
