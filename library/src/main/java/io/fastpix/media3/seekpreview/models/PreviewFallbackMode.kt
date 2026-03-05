package io.fastpix.player.seekpreview.models

/**
 * Fallback behavior when thumbnails are unavailable.
 */
enum class PreviewFallbackMode {
    /**
     * Fall back to timestamp display when thumbnails fail.
     */
    TIMESTAMP,
    
    /**
     * Do not show any preview when thumbnails are unavailable.
     */
    NONE
}
