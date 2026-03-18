package io.fastpix.media3.seekpreview.listeners

import java.io.File

/**
 * Listener for sprite sheet generation progress and completion.
 * All callbacks are invoked on a background thread.
 */
interface SpritesheetGenerationListener {
    
    /**
     * Called when generation progress updates.
     * Progress is reported in steps of 5% (0, 5, 10, ..., 95, 100).
     * 
     * @param progress Progress percentage (0-100)
     */
    fun onProgress(progress: Int)
    
    /**
     * Called when sprite sheet generation completes successfully.
     * 
     * @param spritesheetFile The generated PNG sprite sheet file
     * @param metadataFile The generated JSON metadata file
     */
    fun onSuccess(spritesheetFile: File, metadataFile: File)
    
    /**
     * Called when sprite sheet generation fails.
     * 
     * @param error The error that occurred
     */
    fun onError(error: Throwable)
}

/**
 * Empty implementation of [SpritesheetGenerationListener] for convenience.
 */
open class SpritesheetGenerationListenerAdapter : SpritesheetGenerationListener {
    override fun onProgress(progress: Int) {}
    override fun onSuccess(spritesheetFile: File, metadataFile: File) {}
    override fun onError(error: Throwable) {}
}
