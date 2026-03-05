package io.fastpix.player.seekpreview

import android.content.Context
import android.graphics.Bitmap
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.fastpix.player.seekpreview.cache.CacheManager
import io.fastpix.player.seekpreview.cache.CacheManagerImpl
import io.fastpix.player.seekpreview.listeners.SeekPreviewListener
import io.fastpix.player.seekpreview.listeners.SpritesheetGenerationListener
import io.fastpix.player.seekpreview.mapper.PreviewMapper
import io.fastpix.player.seekpreview.mapper.PreviewMapperImpl
import io.fastpix.player.seekpreview.models.PreviewFallbackMode
import io.fastpix.player.seekpreview.models.PreviewMode
import io.fastpix.player.seekpreview.models.SeekPreviewConfig
import io.fastpix.player.seekpreview.models.SpritesheetConfig
import io.fastpix.player.seekpreview.models.SpritesheetMetadata
import io.fastpix.player.seekpreview.parser.SpritesheetParser
import io.fastpix.player.seekpreview.parser.SpritesheetParserImpl
import io.fastpix.player.seekpreview.provider.PreviewBitmapProvider
import io.fastpix.player.seekpreview.provider.PreviewBitmapProviderImpl
import io.fastpix.player.seekpreview.repository.SpritesheetRepository
import io.fastpix.player.seekpreview.repository.SpritesheetRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.URL

/**
 * Main public API for seek preview functionality.
 * Provides bitmap previews for video seek positions using sprite sheets.
 * 
 * All operations are performed off the main thread.
 * All public APIs are Java-compatible.
 * 
 * Example usage:
 * ```
 * val manager = SeekPreviewManager.create(context)
 * manager.setListener(listener)
 * 
 * manager.loadSpritesheet(
 *     url = URL("https://example.com/spritesheet.png"),
 *     previewEnable = true,
 *     config = SeekPreviewConfig.DEFAULT
 * ).collect { progress ->
 *     // Handle progress updates
 * }
 * 
 * val bitmap = manager.getPreviewBitmap(5000) // 5 seconds
 * ```
 */
class SeekPreviewManager private constructor(
    private val context: Context,
    private val repository: SpritesheetRepository,
    private val parser: SpritesheetParser,
    private val mapper: PreviewMapper,
    private val cacheManager: CacheManager,
    private val httpClient: OkHttpClient,
    private val scope: CoroutineScope,
    private val playbackUrlProvider: PlaybackUrlProvider? = null
) {
    
    private var listener: SeekPreviewListener? = null
    private var fallbackMode: PreviewFallbackMode = PreviewFallbackMode.TIMESTAMP
    private var currentMode: PreviewMode = PreviewMode.TIMESTAMP
    private var bitmapProvider: PreviewBitmapProvider? = null
    private var currentMetadata: SpritesheetMetadata? = null

    /** Monotonic counter: incremented for each [loadPreview] call. */
    private val previewRequestSeq = java.util.concurrent.atomic.AtomicLong(0)
    /** Sequence of the latest result that was actually delivered to the listener. */
    private val previewDeliveredSeq = java.util.concurrent.atomic.AtomicLong(0)
    
    /**
     * Sets the event listener for seek preview events.
     * 
     * @param listener The listener, or null to remove
     */
    @JvmName("setListener")
    fun setListener(listener: SeekPreviewListener?) {
        this.listener = listener
    }
    
    /**
     * Loads a sprite sheet from a URL or uses default FastPix URL from the player.
     *
     * The playback URL is taken from the provider passed at creation (e.g. [FastPixPlayer.getCurrentPlaybackUrl]).
     * Logic:
     * 1) If [url] is provided, load that spritesheet (JSON or PNG).
     * 2) Else if a playback URL is available from the provider, resolve default: https://{imagesHost}/{playbackID}/spritesheet.json
     * 3) Else fall back to timestamp mode (no thumbnails).
     *
     * @param url Optional explicit spritesheet URL (.json or .png). If set, this is used.
     * @param previewEnable Whether to enable preview functionality
     * @param config Configuration for preview behavior
     * @return Flow emitting progress from 0-100 in steps of 5
     */
    @JvmOverloads
    fun loadSpritesheet(
        previewEnable: Boolean = true,
        config: SeekPreviewConfig = SeekPreviewConfig.DEFAULT
    ): Flow<Int> = flow {
        if (!previewEnable) {
            emit(100)
            return@flow
        }

        // Reset state from any previous load
        currentMode = PreviewMode.TIMESTAMP
        bitmapProvider = null
        currentMetadata = null

        emit(0)

        val playbackUrl = playbackUrlProvider?.getPlaybackUrl()

        var loaded = false
        var retryDelayMs = INITIAL_RETRY_DELAY_MS

        while (!loaded) {
            try {
                repository.loadSpritesheet(
                    playbackUrl = playbackUrl,
                    customMetadata = null,
                    generateConfig = null,
                    enableCache = config.cacheEnabled
                ).collect { (progress, metadata) ->
                    emit(progress)

                    if (progress == 100) {
                        withContext(Dispatchers.Main) {
                            if (metadata != null) {
                                val spritesheetFile = repository.getSpritesheetFile()
                                if (spritesheetFile != null) {
                                    currentMetadata = metadata
                                    currentMode = PreviewMode.THUMBNAIL
                                    bitmapProvider = PreviewBitmapProviderImpl(
                                        spritesheetFile = spritesheetFile,
                                        metadata = metadata,
                                        parser = parser,
                                        mapper = mapper,
                                        cacheManager = cacheManager,
                                        config = config,
                                        scope = scope
                                    )
                                    listener?.onSpritesheetInitialized()
                                }
                                loaded = true
                            } else {
                                // No metadata (e.g. no playback URL, or spritesheet doesn't exist) -> timestamp mode, no retry
                                currentMode = PreviewMode.TIMESTAMP
                                loaded = true
                            }
                        }
                    }
                }

                // Repository flow completed without metadata at 100% (shouldn't happen, but guard against it)
                if (!loaded) {
                    currentMode = PreviewMode.TIMESTAMP
                    loaded = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    listener?.onSpritesheetFailed(e)
                }
                // Retry with exponential backoff (capped at MAX_RETRY_DELAY_MS)
                delay(retryDelayMs)
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                emit(0) // Reset progress for retry attempt
            }
        }
    }
    
    /**
     * Generates a sprite sheet from a video file.
     * Runs fully in background using WorkManager.
     * Never blocks UI or playback.
     * 
     * @param config Configuration for sprite sheet generation
     * @param listener Listener for generation progress and completion
     */
    fun generateSpritesheet(
        config: SpritesheetConfig,
        listener: SpritesheetGenerationListener
    ) {
        // Use WorkManager for long-running background task
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<SpritesheetGenerationWorker>()
            .setConstraints(constraints)
            .addTag("spritesheet_generation")
            .build()
        
        // Store listener and config for worker
        SpritesheetGenerationWorker.setListener(listener)
        SpritesheetGenerationWorker.setConfig(config)
        
        WorkManager.getInstance(context).enqueue(workRequest)
    }
    
    /**
     * Sets the fallback mode when thumbnails are unavailable.
     * 
     * @param mode The fallback mode
     */
    fun setFallbackMode(mode: PreviewFallbackMode) {
        this.fallbackMode = mode
    }
    
    /**
     * Gets the current preview mode.
     * 
     * @return The current preview mode
     */
    fun getCurrentPreviewMode(): PreviewMode {
        return currentMode
    }
    
    /**
     * Gets a preview bitmap for a specific time position.
     * Returns null if thumbnails are unavailable and fallback is NONE.
     * 
     * @param timeMs Time position in milliseconds
     * @return The preview bitmap, or null if unavailable
     */
    suspend fun getPreviewBitmap(timeMs: Long): Bitmap? {
        return withContext(Dispatchers.IO) {
            when (currentMode) {
                PreviewMode.THUMBNAIL -> {
                    bitmapProvider?.getPreviewBitmap(timeMs) ?: run {
                        // Fallback to timestamp if enabled
                        if (fallbackMode == PreviewFallbackMode.TIMESTAMP) {
                            currentMode = PreviewMode.TIMESTAMP
                            null
                        } else {
                            null
                        }
                    }
                }
                PreviewMode.TIMESTAMP -> null
            }
        }
    }
    
    /**
     * Formats a time position as a timestamp string (MM:SS).
     * 
     * @param timeMs Time position in milliseconds
     * @return Formatted timestamp string
     */
    fun formatTimestamp(timeMs: Long): String {
        val duration = currentMetadata?.durationMs ?: 0L
        return mapper.formatTimestamp(timeMs, duration)
    }
    
    /**
     * Call when the user starts dragging the seek bar.
     * Notifies the listener via [SeekPreviewListener.onPreviewShow].
     */
    fun showPreview() {
        listener?.onPreviewShow()
    }

    /**
     * Loads the preview for the given time position.
     * Fetches the bitmap on a background thread and delivers the result
     * to [SeekPreviewListener.onSpritesheetLoaded] on the main thread.
     *
     * Safe to call rapidly (e.g. from onProgressChanged). Results stream in as they complete —
     * fast cache hits update the UI immediately, slow decodes are dropped only if a newer
     * result was already displayed.
     *
     * @param timeMs Time position in milliseconds
     */
    fun loadPreview(timeMs: Long) {
        val seq = previewRequestSeq.incrementAndGet()
        scope.launch {
            val bitmap = getPreviewBitmap(timeMs)
            withContext(Dispatchers.Main) {
                // Deliver only if no newer result has been shown yet
                if (seq > previewDeliveredSeq.get()) {
                    previewDeliveredSeq.set(seq)
                    currentMetadata?.let { meta ->
                        listener?.onSpritesheetLoaded(
                            meta.copy(bitmap = bitmap, timestampMs = timeMs)
                        )
                    }
                }
            }
        }
    }

    /**
     * Call when the user stops dragging the seek bar.
     * Invalidates any pending preview updates and notifies the listener via
     * [SeekPreviewListener.onPreviewHide].
     */
    fun hidePreview() {
        // Set deliveredSeq to current requestSeq so no pending coroutine can deliver
        previewDeliveredSeq.set(previewRequestSeq.get())
        listener?.onPreviewHide()
    }
    
    /**
     * Clears all caches.
     */
    fun clearCache() {
        scope.launch(Dispatchers.IO) {
            bitmapProvider?.clearCache()
            cacheManager.clearAll()
        }
    }
    
    /**
     * Releases resources and cancels ongoing operations.
     */
    fun release() {
        previewDeliveredSeq.set(previewRequestSeq.get())
        scope.launch(Dispatchers.IO) {
            bitmapProvider?.clearCache()
        }
        listener = null
        bitmapProvider = null
        currentMetadata = null
        currentMode = PreviewMode.TIMESTAMP
    }
    
    companion object {
        private const val INITIAL_RETRY_DELAY_MS = 2000L
        private const val MAX_RETRY_DELAY_MS = 30_000L

        /**
         * Creates a new [SeekPreviewManager] instance.
         * When [playbackUrlProvider] is set, [loadSpritesheet] uses it to resolve the default
         * FastPix spritesheet URL when no explicit URL is passed (e.g. from [FastPixPlayer.getCurrentPlaybackUrl]).
         *
         * @param context Android context
         * @param playbackUrlProvider Optional provider for the current playback URL (e.g. { player.getCurrentPlaybackUrl() })
         * @return New manager instance
         */
        @JvmStatic
        @JvmOverloads
        fun create(context: Context, playbackUrlProvider: PlaybackUrlProvider? = null): SeekPreviewManager {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val httpClient = OkHttpClient.Builder().build()
            val parser = SpritesheetParserImpl()
            val mapper = PreviewMapperImpl()
            val cacheManager = CacheManagerImpl(context)
            val repository = SpritesheetRepositoryImpl(parser, cacheManager, httpClient)
            
            return SeekPreviewManager(
                context = context,
                repository = repository,
                parser = parser,
                mapper = mapper,
                cacheManager = cacheManager,
                httpClient = httpClient,
                scope = scope,
                playbackUrlProvider = playbackUrlProvider
            )
        }
    }
}
