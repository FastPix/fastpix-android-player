package io.fastpix.media3.seekpreview

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.fastpix.media3.seekpreview.listeners.SpritesheetGenerationListener
import io.fastpix.media3.seekpreview.models.SpritesheetConfig
import io.fastpix.media3.seekpreview.repository.SpritesheetGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker for generating sprite sheets in the background.
 * This ensures generation never blocks the UI or playback.
 */
internal class SpritesheetGenerationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val config = getConfig() ?: return@withContext Result.failure()
        val listener = getListener() ?: return@withContext Result.failure()
        
        try {
            listener.onProgress(0)
            
            val result = SpritesheetGenerator.generate(config) { progress ->
                listener.onProgress(progress)
            }
            
            if (result != null) {
                val (spritesheetFile, metadataFile) = result
                listener.onSuccess(spritesheetFile, metadataFile)
                Result.success()
            } else {
                listener.onError(Exception("Sprite sheet generation not implemented"))
                Result.failure()
            }
        } catch (e: Exception) {
            listener.onError(e)
            Result.failure()
        }
    }
    
    companion object {
        private var listener: SpritesheetGenerationListener? = null
        private var config: SpritesheetConfig? = null
        
        @Synchronized
        fun setListener(listener: SpritesheetGenerationListener?) {
            this.listener = listener
        }
        
        @Synchronized
        fun getListener(): SpritesheetGenerationListener? {
            return listener
        }
        
        @Synchronized
        fun setConfig(config: SpritesheetConfig?) {
            this.config = config
        }
        
        @Synchronized
        fun getConfig(): SpritesheetConfig? {
            return config
        }
    }
}
