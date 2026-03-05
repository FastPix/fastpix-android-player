package io.fastpix.player.seekpreview.parser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.fastpix.player.seekpreview.models.SpritesheetMetadata
import java.io.File
import java.io.InputStream

/**
 * Parses and validates sprite sheet PNG files and JSON metadata.
 * All operations are performed off the main thread.
 */
interface SpritesheetParser {
    
    /**
     * Parses JSON metadata from a file.
     * 
     * @param jsonFile The JSON metadata file
     * @return Parsed metadata, or null if parsing fails
     */
    fun parseMetadata(jsonFile: File): SpritesheetMetadata?
    
    /**
     * Parses JSON metadata from an input stream.
     * 
     * @param inputStream The JSON metadata input stream
     * @return Parsed metadata, or null if parsing fails
     */
    fun parseMetadata(inputStream: InputStream): SpritesheetMetadata?
    
    /**
     * Parses FastPix spritesheet JSON (url, tile_width, tile_height, duration, tiles).
     * Returns metadata and the image URL to use for loading the sheet.
     *
     * @param json Raw JSON string from e.g. https://images.fastpix.io/{playbackId}/spritesheet.json
     * @return [FastPixSpritesheetResult] or null if not in FastPix format / parse fails
     */
    fun parseFastPixSpritesheetJson(json: String): FastPixSpritesheetResult?
    
    /**
     * Validates that a PNG sprite sheet matches the expected metadata.
     * 
     * @param pngFile The PNG sprite sheet file
     * @param metadata The expected metadata
     * @return true if the PNG matches the metadata, false otherwise
     */
    fun validateSpritesheet(pngFile: File, metadata: SpritesheetMetadata): Boolean
    
    /**
     * Decodes a single frame from the sprite sheet.
     * 
     * @param spritesheetFile The sprite sheet PNG file
     * @param metadata The sprite sheet metadata
     * @param frameIndex The zero-based frame index
     * @param options Bitmap decoding options (for reuse)
     * @return The decoded frame bitmap, or null if decoding fails
     */
    fun decodeFrame(
        spritesheetFile: File,
        metadata: SpritesheetMetadata,
        frameIndex: Int,
        options: BitmapFactory.Options? = null
    ): Bitmap?
}

/**
 * Result of parsing FastPix-style spritesheet JSON (url, tile_width, tile_height, duration, tiles).
 * Exposed for use by repository/source layer.
 */
data class FastPixSpritesheetResult(
    val metadata: SpritesheetMetadata,
    val imageUrl: String
)

/**
 * Default implementation of [SpritesheetParser] using Gson and BitmapFactory.
 */
internal class SpritesheetParserImpl : SpritesheetParser {
    
    private val gson = com.google.gson.Gson()
    
    /**
     * Parses FastPix spritesheet JSON format.
     * Expects: url, tile_width, tile_height, duration (seconds), tiles (array of {start, x, y}).
     * @return Metadata plus image URL, or null if parsing fails
     */
    override fun parseFastPixSpritesheetJson(json: String): FastPixSpritesheetResult? {
        return try {
            val obj = gson.fromJson(json, FastPixSpritesheetJson::class.java)
                ?: return null
            val imageUrl = obj.url?.takeIf { it.isNotBlank() } ?: return null
            val tileWidth = obj.tileWidth
            val tileHeight = obj.tileHeight
            val durationSec = obj.duration
            val tiles = obj.tiles ?: return null
            if (tiles.isEmpty() || tileWidth <= 0 || tileHeight <= 0 || durationSec <= 0.0) return null
            
            val frameCount = tiles.size
            val durationMs = (durationSec * 1000).toLong()
            val intervalMs = if (frameCount > 0) durationMs / frameCount else durationMs
            
            val maxX = tiles.maxOfOrNull { it.x } ?: 0
            val maxY = tiles.maxOfOrNull { it.y } ?: 0
            val columns = (maxX / tileWidth) + 1
            val rows = (maxY / tileHeight) + 1
            
            val metadata = SpritesheetMetadata(
                rows = rows,
                columns = columns,
                frameWidth = tileWidth,
                frameHeight = tileHeight,
                frameCount = frameCount,
                durationMs = durationMs,
                intervalMs = intervalMs
            )
            if (!metadata.isValid()) return null
            FastPixSpritesheetResult(metadata, imageUrl)
        } catch (e: Exception) {
            null
        }
    }
    
    override fun parseMetadata(jsonFile: File): SpritesheetMetadata? {
        return try {
            jsonFile.readText().let { json ->
                parseMetadataFromJson(json)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override fun parseMetadata(inputStream: InputStream): SpritesheetMetadata? {
        return try {
            inputStream.bufferedReader().use { reader ->
                parseMetadataFromJson(reader.readText())
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseMetadataFromJson(json: String): SpritesheetMetadata? {
        return try {
            val jsonObject = gson.fromJson(json, Map::class.java) as? Map<*, *>
                ?: return null
            
            val rows = (jsonObject["rows"] as? Number)?.toInt() ?: return null
            val columns = (jsonObject["columns"] as? Number)?.toInt() ?: return null
            val frameWidth = (jsonObject["frameWidth"] as? Number)?.toInt() ?: return null
            val frameHeight = (jsonObject["frameHeight"] as? Number)?.toInt() ?: return null
            val frameCount = (jsonObject["frameCount"] as? Number)?.toInt() ?: return null
            val durationMs = (jsonObject["durationMs"] as? Number)?.toLong() ?: return null
            val intervalMs = (jsonObject["intervalMs"] as? Number)?.toLong() ?: return null
            
            SpritesheetMetadata(
                rows = rows,
                columns = columns,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                frameCount = frameCount,
                durationMs = durationMs,
                intervalMs = intervalMs
            ).takeIf { it.isValid() }
        } catch (e: Exception) {
            null
        }
    }
    
    
    override fun validateSpritesheet(pngFile: File, metadata: SpritesheetMetadata): Boolean {
        if (!pngFile.exists()) return false
        
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(pngFile.absolutePath, options)
            
            val expectedWidth = metadata.columns * metadata.frameWidth
            val expectedHeight = metadata.rows * metadata.frameHeight
            
            options.outWidth == expectedWidth && options.outHeight == expectedHeight
        } catch (e: Exception) {
            false
        }
    }
    
    override fun decodeFrame(
        spritesheetFile: File,
        metadata: SpritesheetMetadata,
        frameIndex: Int,
        options: BitmapFactory.Options?
    ): Bitmap? {
        if (frameIndex < 0 || frameIndex >= metadata.frameCount) {
            return null
        }
        
        return try {
            val row = frameIndex / metadata.columns
            val column = frameIndex % metadata.columns
            
            val x = column * metadata.frameWidth
            val y = row * metadata.frameHeight
            
            val decodeOptions = options ?: BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inSampleSize = 1
            }
            
            // Decode the full sprite sheet
            val fullBitmap = BitmapFactory.decodeFile(spritesheetFile.absolutePath, decodeOptions)
                ?: return null
            
            // Extract the frame
            val frameBitmap = Bitmap.createBitmap(
                fullBitmap,
                x,
                y,
                metadata.frameWidth,
                metadata.frameHeight
            )
            
            // Recycle the full bitmap if we created a new options object
            if (options == null) {
                fullBitmap.recycle()
            }
            
            frameBitmap
        } catch (e: Exception) {
            null
        }
    }
}
