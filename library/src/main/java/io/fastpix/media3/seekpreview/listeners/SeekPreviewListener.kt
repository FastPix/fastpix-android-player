package io.fastpix.media3.seekpreview.listeners

import io.fastpix.media3.seekpreview.models.SpritesheetMetadata

/**
 * Listener for seek preview events.
 * All callbacks are invoked on the main thread.
 */
interface SeekPreviewListener {

    /**
     * Called when the sprite sheet is successfully initialized and ready for use.
     */
    fun onSpritesheetInitialized()

    /**
     * Called when sprite sheet loading fails.
     *
     * @param error The error that occurred
     */
    fun onSpritesheetFailed(error: Throwable)

    /**
     * Called when a preview is shown (user started dragging the seek bar).
     */
    fun onPreviewShow()

    /**
     * Called when a preview is hidden (user stopped dragging the seek bar).
     * Any pending preview updates are automatically cancelled by the SDK.
     */
    fun onPreviewHide()

    /**
     * Called when a preview frame is ready for display.
     * Triggered by [io.fastpix.media3.seekpreview.SeekPreviewManager.loadPreview].
     * The [metadata] includes [SpritesheetMetadata.bitmap] and [SpritesheetMetadata.timestampMs]
     * for the current seek position.
     *
     * @param metadata The sprite sheet metadata including the preview bitmap and timestamp
     */
    fun onSpritesheetLoaded(metadata: SpritesheetMetadata)
}

/**
 * Empty implementation of [SeekPreviewListener] for convenience.
 */
open class SeekPreviewListenerAdapter : SeekPreviewListener {
    override fun onSpritesheetInitialized() {}
    override fun onSpritesheetFailed(error: Throwable) {}
    override fun onPreviewShow() {}
    override fun onPreviewHide() {}
    override fun onSpritesheetLoaded(metadata: SpritesheetMetadata) {}
}