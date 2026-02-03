package io.fastpix.media3.core

/**
 * Builder class for configuring FastPix media items.
 * 
 * Use this builder to configure playback parameters before creating a MediaItem.
 * 
 * Example usage:
 * ```
 * val builder = FastPixMediaItemBuilder {
 *     playbackId = "your-playback-id"
 *     maxResolution = PlaybackResolution.FHD_1080
 *     playbackToken = "your-token"
 * }
 * ```
 */
class FastPixMediaItemBuilder {
    /**
     * The FastPix playback ID (required).
     */
    var playbackId: String = ""
    
    /**
     * Optional maximum playback resolution.
     */
    var maxResolution: PlaybackResolution? = null
    
    /**
     * Optional minimum playback resolution.
     */
    var minResolution: PlaybackResolution? = null
    
    /**
     * Optional specific playback resolution.
     */
    var resolution: PlaybackResolution? = null
    
    /**
     * Optional rendition order preference for adaptive streaming.
     */
    var renditionOrder: RenditionOrder? = null
    
    /**
     * Optional custom domain (defaults to "stream.fastpix.io").
     */
    var customDomain: String? = null
    
    /**
     * Optional stream type ("on-demand" or "live-stream", defaults to "on-demand").
     */
    var streamType: String? = null
    
    /**
     * Optional playback token for secure playback.
     */
    var playbackToken: String? = null
}

/**
 * Creates a [FastPixMediaItemBuilder] and configures it using the provided lambda.
 * 
 * @param block The configuration lambda.
 * @return A configured [FastPixMediaItemBuilder] instance.
 */
fun fastPixMediaItem(block: FastPixMediaItemBuilder.() -> Unit): FastPixMediaItemBuilder {
    return FastPixMediaItemBuilder().apply(block)
}

/**
 * Supported playback resolutions for FastPix streams.
 */
enum class PlaybackResolution {
    LD_480,
    LD_540,
    HD_720,
    FHD_1080,
    QHD_1440,
    FOUR_K_2160
}

/**
 * The order of preference for adaptive streaming.
 */
enum class RenditionOrder {
    Descending,
    Ascending,
    Default
}
