package io.fastpix.media3.core

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import io.fastpix.media3.cache.FastPixItemKeys

/**
 * Turns a [FastPixMediaItemBuilder] configuration into a playable Media3 [MediaItem]: the signed
 * stream URL plus, for protected content, the Widevine configuration.
 *
 * Shared by [FastPixPlayer.setFastPixMediaItem] and [io.fastpix.media3.playlist.PlaylistItem.fastPix]
 * so a playback ID resolves to the same URL — and the same cache entry — whichever way it is played.
 */
internal object FastPixMediaItems {

    /** Stream host used when [FastPixMediaItemBuilder.customDomain] is not set. */
    const val DEFAULT_STREAM_DOMAIN = "stream.fastpix.com"

    /**
     * Builds the media item for [config].
     *
     * @param mediaId identifier stamped on the item; defaults to the playback ID.
     */
    fun build(config: FastPixMediaItemBuilder, mediaId: String = config.playbackId): MediaItem {
        val builder = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(playbackUrl(config))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setTag(FastPixItemKeys.FastPixItemTag(config.playbackId))
        DrmManager.buildMediaItemDrmConfiguration(
            config.drmConfig,
            config.playbackId,
            config.playbackToken,
            config.streamType,
        )?.let { builder.setDrmConfiguration(it) }
        return builder.build()
    }

    /** The HLS URL for [config]'s playback ID, with its resolution, rendition and token options. */
    fun playbackUrl(config: FastPixMediaItemBuilder): String {
        val uriBuilder = Uri.Builder()
            .scheme("https")
            .authority(config.customDomain ?: DEFAULT_STREAM_DOMAIN)
            .appendPath("${config.playbackId}.m3u8")

        config.minResolution?.let {
            uriBuilder.appendQueryParameter("minResolution", resolutionValue(it))
        }
        config.maxResolution?.let {
            uriBuilder.appendQueryParameter("maxResolution", resolutionValue(it))
        }
        config.resolution?.let {
            uriBuilder.appendQueryParameter("resolution", resolutionValue(it))
        }
        config.renditionOrder?.takeIf { it != RenditionOrder.Default }?.let {
            uriBuilder.appendQueryParameter("renditionOrder", renditionValue(it))
        }
        config.playbackToken?.let {
            uriBuilder.appendQueryParameter("token", it)
        }

        return uriBuilder.build().toString()
    }

    private fun resolutionValue(resolution: PlaybackResolution): String = when (resolution) {
        PlaybackResolution.LD_480 -> "480p"
        PlaybackResolution.LD_540 -> "540p"
        PlaybackResolution.HD_720 -> "720p"
        PlaybackResolution.FHD_1080 -> "1080p"
        PlaybackResolution.QHD_1440 -> "1440p"
        PlaybackResolution.FOUR_K_2160 -> "2160p"
    }

    private fun renditionValue(renditionOrder: RenditionOrder): String = when (renditionOrder) {
        RenditionOrder.Descending -> "desc"
        RenditionOrder.Ascending -> "asc"
        RenditionOrder.Default -> ""
    }
}
