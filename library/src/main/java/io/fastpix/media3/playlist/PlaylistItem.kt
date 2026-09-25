package io.fastpix.media3.playlist

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import io.fastpix.data.domain.model.VideoDataDetails
import io.fastpix.media3.core.FastPixMediaItemBuilder
import io.fastpix.media3.core.FastPixMediaItems
import io.fastpix.media3.core.fastPixMediaItem

/**
 * One entry in a [io.fastpix.media3.core.FastPixPlayer] playlist.
 *
 * Create items from a FastPix playback ID, a stream URL, or an existing Media3 [MediaItem]:
 *
 * ```kotlin
 * player.setPlaylist(
 *     listOf(
 *         PlaylistItem.fastPix("playback-id-1"),
 *         PlaylistItem.fastPix {
 *             playbackId = "playback-id-2"
 *             maxResolution = PlaybackResolution.HD_720
 *         },
 *         PlaylistItem.fromUrl("https://example.com/video.m3u8"),
 *     )
 * )
 * ```
 *
 * Items are immutable. The same item may appear in a playlist more than once.
 *
 * With analytics on, each entry watched is reported as its own view. Attach the entry's metadata
 * with [withVideoData]:
 *
 * ```kotlin
 * PlaylistItem.fastPix("playback-id")
 *     .withVideoData(VideoDataDetails(videoId = "v-123", videoTitle = "Episode 1"))
 * ```
 */
@UnstableApi
class PlaylistItem private constructor(
    /**
     * Identifier for this entry: the playback ID for FastPix items, the URL for URL items, or the
     * media ID of a wrapped [MediaItem]. Not required to be unique.
     */
    val id: String,

    /** The Media3 item played for this entry. */
    val mediaItem: MediaItem,

    /**
     * Analytics metadata for this entry, reported with its view. When null, the analytics
     * config's [io.fastpix.media3.analytics.AnalyticsConfig.videoDataDetails] is used, or else just
     * this entry's [id].
     */
    val videoDataDetails: VideoDataDetails? = null,
) {

    /** A copy of this entry reporting [details] as its analytics metadata. */
    fun withVideoData(details: VideoDataDetails?): PlaylistItem = PlaylistItem(id, mediaItem, details)

    override fun toString(): String = "PlaylistItem(id=$id)"

    companion object {

        /**
         * A FastPix stream by playback ID. Pass [playbackToken] for signed playback.
         *
         * @throws IllegalArgumentException if [playbackId] is blank.
         */
        @JvmStatic
        @JvmOverloads
        fun fastPix(playbackId: String, playbackToken: String? = null): PlaylistItem =
            fastPix {
                this.playbackId = playbackId
                this.playbackToken = playbackToken
            }

        /**
         * A FastPix stream configured with the same options as
         * [io.fastpix.media3.core.FastPixPlayer.setFastPixMediaItem]: resolution limits, rendition
         * order, custom domain, stream type, token and DRM.
         *
         * @throws IllegalArgumentException if no playback ID is set.
         */
        @JvmStatic
        fun fastPix(block: FastPixMediaItemBuilder.() -> Unit): PlaylistItem {
            val config = fastPixMediaItem(block)
            require(config.playbackId.isNotBlank()) { "playbackId must not be blank" }
            return PlaylistItem(config.playbackId, FastPixMediaItems.build(config))
        }

        /**
         * A stream at [url] — HLS, DASH or progressive, as Media3 infers from the URL.
         *
         * @param id identifier for this entry; defaults to the URL.
         */
        @JvmStatic
        @JvmOverloads
        fun fromUrl(url: String, id: String = url): PlaylistItem {
            require(url.isNotBlank()) { "url must not be blank" }
            return PlaylistItem(id, MediaItem.Builder().setMediaId(id).setUri(url).build())
        }

        /**
         * Wraps an existing Media3 [MediaItem], for sources the other factories do not cover.
         * The item's media ID becomes [id], falling back to its URI.
         */
        @JvmStatic
        fun fromMediaItem(mediaItem: MediaItem): PlaylistItem {
            val id = mediaItem.mediaId.takeIf { it != MediaItem.DEFAULT_MEDIA_ID }
                ?: mediaItem.localConfiguration?.uri?.toString()
                ?: MediaItem.DEFAULT_MEDIA_ID
            return PlaylistItem(id, mediaItem)
        }
    }
}
