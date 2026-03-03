package io.fastpix.player.seekpreview.repository

import io.fastpix.player.seekpreview.cache.CacheManager
import io.fastpix.player.seekpreview.models.SpritesheetConfig
import io.fastpix.player.seekpreview.models.SpritesheetMetadata
import io.fastpix.player.seekpreview.parser.SpritesheetParser
import io.fastpix.player.seekpreview.util.FastPixSpritesheetUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.net.URL

/**
 * Repository for managing sprite sheet sources and loading.
 * Handles priority: Custom spritesheets override generated ones.
 */
interface SpritesheetRepository {

    /**
     * Loads a sprite sheet from the configured sources.
     *
     * Priority: 1) Explicit [customUrl], 2) Default FastPix URL from [playbackUrl], 3) [generateConfig].
     *
     * @param customUrl Optional explicit spritesheet URL (.json or .png). If provided, this is used.
     * @param playbackUrl Current playback/stream URL. When [customUrl] is null, used to build default FastPix spritesheet URL (https://{imagesHost}/{playbackID}/spritesheet.json).
     * @param customMetadata Optional metadata for custom sprite sheet
     * @param generateConfig Optional config for generating sprite sheet if custom not available
     * @param enableCache Whether to use cache
     * @return Flow emitting progress (0-100 in steps of 5) and the loaded metadata
     */
    suspend fun loadSpritesheet(
        playbackUrl: String? = null,
        customMetadata: SpritesheetMetadata? = null,
        generateConfig: SpritesheetConfig? = null,
        enableCache: Boolean = true
    ): Flow<Pair<Int, SpritesheetMetadata?>>

    /**
     * Gets the currently loaded sprite sheet file.
     *
     * @return The sprite sheet PNG file, or null if not loaded
     */
    suspend fun getSpritesheetFile(): File?

    /**
     * Gets the currently loaded metadata.
     *
     * @return The metadata, or null if not loaded
     */
    suspend fun getMetadata(): SpritesheetMetadata?
}

/**
 * Default implementation of [SpritesheetRepository].
 */
internal class SpritesheetRepositoryImpl(
    private val parser: SpritesheetParser,
    private val cacheManager: CacheManager,
    private val httpClient: OkHttpClient
) : SpritesheetRepository {

    private var currentSource: SpritesheetSource? = null
    private var currentSpritesheetFile: File? = null
    private var currentMetadata: SpritesheetMetadata? = null

    override suspend fun loadSpritesheet(
        playbackUrl: String?,
        customMetadata: SpritesheetMetadata?,
        generateConfig: SpritesheetConfig?,
        enableCache: Boolean
    ): Flow<Pair<Int, SpritesheetMetadata?>> = flow {
        // Clear stale state from previous loads
        currentSource = null
        currentSpritesheetFile = null
        currentMetadata = null

        emit(Pair(0, null))

        // Resolve effective URL: explicit customUrl, or default from playbackUrl
        val effectiveUrl = withContext(Dispatchers.Default) {
            if (!playbackUrl.isNullOrBlank()) {
                FastPixSpritesheetUrlResolver.getDefaultSpritesheetJsonUrl(playbackUrl)
            } else {
                null
            }
        }

        val source = withContext(Dispatchers.Default) {
            when {
                // 1) Explicit spritesheet URL (JSON or image)
                effectiveUrl != null -> {
                    val urlString = effectiveUrl.toString()
                    if (urlString.endsWith(".json")) {
                        // FastPix: fetch this JSON; image URL is inside the response (url, tile_width, tiles, etc.)
                        CustomSpritesheetSource(
                            spritesheetUrl = null,
                            metadataUrl = effectiveUrl,
                            metadata = customMetadata,
                            parser = parser,
                            cacheManager = cacheManager,
                            httpClient = httpClient
                        )
                    } else {
                        // Caller passed image URL; metadata may be at .json variant
                        val jsonUrl = try {
                            URL(urlString.replace(".png", ".json").replace(".jpg", ".json"))
                        } catch (e: Exception) {
                            null
                        }
                        CustomSpritesheetSource(
                            spritesheetUrl = effectiveUrl,
                            metadataUrl = jsonUrl,
                            metadata = customMetadata,
                            parser = parser,
                            cacheManager = cacheManager,
                            httpClient = httpClient
                        )
                    }
                }
                // 2) Generate from video if config provided
                generateConfig != null -> {
                    GeneratedSpritesheetSource(
                        config = generateConfig,
                        parser = parser,
                        cacheManager = cacheManager
                    )
                }
                // 3) No URL and no playback URL -> fallback to timestamp mode
                else -> null
            }
        }

        if (source == null) {
            emit(Pair(100, null))
            return@flow
        }

        currentSource = source

        // Load metadata (5-50%)
        emit(Pair(5, null))
        val metadata = withContext(Dispatchers.IO) {
            source.loadMetadata()
        }
        emit(Pair(50, metadata))

        if (metadata == null) {
            // Metadata is null — spritesheet doesn't exist for this video (e.g. 404)
            emit(Pair(100, null))
            return@flow
        }

        // Load sprite sheet (50-95%)
        emit(Pair(55, metadata))
        val spritesheetFile = withContext(Dispatchers.IO) {
            source.loadSpritesheet()
        }
        emit(Pair(95, metadata))

        if (spritesheetFile == null) {
            throw Exception("Spritesheet image download failed")
        }

        // Validate
        if (!parser.validateSpritesheet(spritesheetFile, metadata)) {
            throw Exception("Spritesheet validation failed: image dimensions do not match metadata")
        }

        // Cache if enabled
        if (enableCache) {
            withContext(Dispatchers.IO) {
                val sourceId = source.getSourceId()
                cacheManager.cacheSpritesheet(sourceId, spritesheetFile)
                cacheManager.cacheMetadata(
                    sourceId,
                    File(spritesheetFile.parent, "${sourceId}_metadata.json")
                )
            }
        }

        currentSpritesheetFile = spritesheetFile
        currentMetadata = metadata

        emit(Pair(100, metadata))
    }

    override suspend fun getSpritesheetFile(): File? {
        return currentSpritesheetFile
    }

    override suspend fun getMetadata(): SpritesheetMetadata? {
        return currentMetadata
    }
}
