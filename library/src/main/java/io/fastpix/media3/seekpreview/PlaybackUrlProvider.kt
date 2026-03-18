package io.fastpix.media3.seekpreview

/**
 * Provides the current playback/stream URL for default spritesheet resolution.
 * Pass this when creating [SeekPreviewManager] so that [loadSpritesheet] can resolve
 * the default FastPix URL (https://{imagesHost}/{playbackID}/spritesheet.json) when
 * no explicit URL is provided.
 *
 * Example: use [FastPixPlayer.getCurrentPlaybackUrl] so the manager reads the URL
 * from the player that was set via [io.fastpix.media3.core.FastPixPlayer.setFastPixMediaItem]
 * or [io.fastpix.media3.core.FastPixPlayer.setMediaItem].
 */
fun interface PlaybackUrlProvider {
    /**
     * Returns the current playback/stream URL, or null if none.
     */
    fun getPlaybackUrl(): String?
}
