package io.fastpix.media3.seekpreview.repository

import io.fastpix.media3.seekpreview.cache.CacheManager
import io.fastpix.media3.seekpreview.cache.VideoHashUtil
import io.fastpix.media3.seekpreview.models.SpritesheetMetadata
import io.fastpix.media3.seekpreview.parser.SpritesheetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URL

/**
 * Source for loading custom sprite sheets from network URLs.
 * When only [metadataUrl] is provided (e.g. .json), we fetch that JSON first:
 * - If it's FastPix format (url, tile_width, tile_height, duration, tiles), we use the [url] from JSON
 *   to load the image (can be .jpg or .png).
 * Otherwise uses [spritesheetUrl] when provided.
 * Implements retry logic with exponential backoff (3 attempts).
 */
internal class CustomSpritesheetSource(
    private val spritesheetUrl: URL?,
    private val metadataUrl: URL? = null,
    private val metadata: SpritesheetMetadata? = null,
    private val parser: SpritesheetParser,
    private val cacheManager: CacheManager,
    private val httpClient: OkHttpClient
) : SpritesheetSource {
    
    private val sourceId: String = VideoHashUtil.hashFromUrl((metadataUrl?.toString() ?: spritesheetUrl?.toString()).orEmpty())
    
    /** Image URL from FastPix JSON (set after loadMetadata when metadataUrl is FastPix format). */
    @Volatile
    private var imageUrlFromJson: String? = null
    
    override fun getSourceId(): String = sourceId
    
    override suspend fun loadSpritesheet(): File? = withContext(Dispatchers.IO) {
        // Check cache first
        cacheManager.getCachedSpritesheet(sourceId)?.let { return@withContext it }
        
        val urlToDownload = imageUrlFromJson?.let { URL(it) } ?: spritesheetUrl
            ?: return@withContext null
        
        downloadWithRetry(urlToDownload) { url ->
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            
            val body = response.body ?: throw Exception("Empty response body")
            val ext = when {
                url.path?.endsWith(".jpg", ignoreCase = true) == true -> ".jpg"
                url.path?.endsWith(".jpeg", ignoreCase = true) == true -> ".jpg"
                else -> ".png"
            }
            val tempFile = File.createTempFile("spritesheet_", ext)
            
            try {
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
        }
    }
    
    override suspend fun loadMetadata(): SpritesheetMetadata? = withContext(Dispatchers.IO) {
        // If metadata was provided directly, use it
        if (metadata != null) {
            return@withContext metadata
        }
        
        // Check cache
        cacheManager.getCachedMetadata(sourceId)?.let { cachedFile ->
            val cachedJson = cachedFile.readText()
            parser.parseFastPixSpritesheetJson(cachedJson)?.let { result ->
                imageUrlFromJson = result.imageUrl
                return@withContext result.metadata
            }
            parser.parseMetadata(cachedFile)?.let { return@withContext it }
        }
        
        // Download metadata JSON if URL provided
        metadataUrl?.let { url ->
            downloadWithRetry(url) { downloadUrl ->
                val request = Request.Builder().url(downloadUrl).build()
                val response = httpClient.newCall(request).execute()

                // 404 means spritesheet doesn't exist for this video — return null (no retry)
                if (response.code == 404) {
                    return@downloadWithRetry null
                }

                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }
                
                val body = response.body ?: throw Exception("Empty response body")
                val jsonText = body.string()
                
                // 1) Try FastPix format (url, tile_width, tile_height, duration, tiles)
                parser.parseFastPixSpritesheetJson(jsonText)?.let { result ->
                    imageUrlFromJson = result.imageUrl
                    val tempFile = File.createTempFile("metadata_", ".json")
                    tempFile.writeText(jsonText)
                    cacheManager.cacheMetadata(sourceId, tempFile)
                    tempFile.delete()
                    return@downloadWithRetry result.metadata
                }
                
                // 2) Legacy format (rows, columns, frameWidth, ...)
                parser.parseMetadata(jsonText.byteInputStream())?.also { parsedMetadata ->
                    val tempFile = File.createTempFile("metadata_", ".json")
                    tempFile.writeText(jsonText)
                    cacheManager.cacheMetadata(sourceId, tempFile)
                    tempFile.delete()
                    return@downloadWithRetry parsedMetadata
                }
            }
        }
    }
    
    private suspend fun <T> downloadWithRetry(
        url: URL,
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        block: suspend (URL) -> T
    ): T? {
        var lastException: Exception? = null
        var delayMs = initialDelayMs
        
        repeat(maxRetries) { attempt ->
            try {
                return block(url)
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    delay(delayMs)
                    delayMs *= 2 // Exponential backoff
                }
            }
        }
        
        throw lastException ?: Exception("Failed to download after $maxRetries attempts")
    }
}
