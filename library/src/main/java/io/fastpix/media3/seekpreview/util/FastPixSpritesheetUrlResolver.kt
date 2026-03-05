package io.fastpix.player.seekpreview.util

import java.net.URI
import java.net.URL
import java.util.regex.Pattern

/**
 * Resolves FastPix default spritesheet JSON URL from the current stream/media URL.
 *
 * Default spritesheet URL format: `https://{imagesHost}/{playbackID}/spritesheet.json`
 *
 * Stream host to images host mapping:
 * - stream.fastpix.io → images.fastpix.io
 * - stream.fastpix.app → images.fastpix.app
 * - venus-stream.fastpix.dev → venus-images.fastpix.dev
 */
object FastPixSpritesheetUrlResolver {

    private val UUID_PATTERN = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    )

    /**
     * Extracts the playback ID from a FastPix stream or media URL.
     * Supports UUID in path segments (e.g. .../66ee6d27-e1c0-4d15-99b2-153e26389c90).
     *
     * @param mediaUrl The stream URL (e.g. from current playback item)
     * @return The playback ID (UUID string), or null if not found
     */
    @JvmStatic
    fun extractPlaybackID(mediaUrl: String?): String? {
        if (mediaUrl.isNullOrBlank()) return null
        return try {
            val path = URI(mediaUrl).path ?: return null
            val matcher = UUID_PATTERN.matcher(path)
            if (matcher.find()) matcher.group() else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns the images host for the given stream host.
     *
     * @param streamHost The stream URL host (e.g. from asset URL)
     * @return The images host, or null if unknown (caller should fall back to timestamp mode)
     */
    @JvmStatic
    fun getImagesHost(streamHost: String?): String? {
        if (streamHost.isNullOrBlank()) return null
        return when (streamHost) {
            "stream.fastpix.io" -> "images.fastpix.io"
            "stream.fastpix.app" -> "images.fastpix.app"
            "venus-stream.fastpix.dev" -> "venus-images.fastpix.dev"
            else -> null
        }
    }

    /**
     * Builds the default FastPix spritesheet JSON URL from the current playback URL.
     * Use this when no explicit spritesheet URL is provided.
     *
     * @param mediaUrl The current stream/playback URL (e.g. from player's current item)
     * @return URL to spritesheet.json, or null if playback ID or images host cannot be resolved
     */
    @JvmStatic
    fun getDefaultSpritesheetJsonUrl(mediaUrl: String?): URL? {
        if (mediaUrl.isNullOrBlank()) return null
        return try {
            val uri = URI(mediaUrl)
            val streamHost = uri.host ?: return null
            val imagesHost = getImagesHost(streamHost) ?: return null
            val playbackID = extractPlaybackID(mediaUrl) ?: return null
            val jsonString = "https://$imagesHost/$playbackID/spritesheet.json"
            URL(jsonString)
        } catch (e: Exception) {
            null
        }
    }
}
