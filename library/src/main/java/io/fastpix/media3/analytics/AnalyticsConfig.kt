package io.fastpix.media3.analytics

import androidx.media3.common.util.UnstableApi
import io.fastpix.data.domain.model.CustomDataDetails
import io.fastpix.data.domain.model.PlayerDataDetails
import io.fastpix.data.domain.model.VideoDataDetails
import io.fastpix.media3.PlayerView

/**
 * Configuration for FastPix Media3 Analytics integration.
 *
 * When provided to [io.fastpix.media3.core.FastPixPlayer.Builder.setAnalyticsConfig],
 * playback events are automatically sent to the FastPix Data dashboard.
 *
 * Create via [AnalyticsConfig.Builder]:
 * ```
 * AnalyticsConfig.Builder(playerView, "your-workspace-id")
 *     .setVideoDataDetails(videoDataDetails)
 *     .setEnabled(true)
 *     .build()
 * ```
 *
 * Mandatory (set in builder constructor):
 * - [workSpaceId] FastPix workspace ID for data collection.
 *
 * Optional:
 * - [playerView] The FastPix PlayerView used for playback. Leave it out when the player is shown
 *   in different views over time — the pages of a feed with
 *   [io.fastpix.media3.playlist.FastPixPlayerPool], say — and each view is measured through
 *   whichever [PlayerView] is showing the player at the time:
 *   `AnalyticsConfig.Builder("your-workspace-id").build()`.
 *
 * **One view per video watched.** A view begins when a video becomes the one being watched — set
 * with `setMediaItem`, reached in a playlist, or made current in a pool — and ends when playback
 * moves to another. Entries that are only preloaded or pre-rendered are never reported. Give each
 * playlist entry its own metadata with [io.fastpix.media3.playlist.PlaylistItem.withVideoData];
 * [videoDataDetails] here is the fallback for entries without any.
 *
 * The FastPix Data SDK reports one view at a time per app: starting a view on one player ends any
 * view still open on another.
 */
@UnstableApi
data class AnalyticsConfig internal constructor(
    val videoDataDetails: VideoDataDetails?,
    val customDataDetails: CustomDataDetails?,
    val playerView: PlayerView?,
    val workSpaceId: String,
    val enabled: Boolean,
    val beaconDomain: String?
) {
    /**
     * Builder for [AnalyticsConfig].
     *
     * @param playerView The [PlayerView] that displays playback, or null to measure through
     *   whichever [PlayerView] shows the player.
     * @param workSpaceId FastPix workspace ID. Mandatory; must not be blank.
     */
    class Builder(
        private val playerView: PlayerView?,
        private val workSpaceId: String
    ) {
        /** Measures through whichever [PlayerView] is showing the player — for feeds and pools. */
        constructor(workSpaceId: String) : this(null, workSpaceId)

        private var videoDataDetails: VideoDataDetails? = null
        private var customDataDetails: CustomDataDetails? = null
        private var enabled: Boolean = true
        private var beaconDomain: String? = null

        /**
         * Sets optional video metadata (id, title, series, etc.).
         */
        fun setVideoDataDetails(details: VideoDataDetails?): Builder {
            this.videoDataDetails = details
            return this
        }

        fun setBeaconDomain(beaconDomain: String?): Builder {
            this.beaconDomain = beaconDomain
            return this
        }

        /**
         * Sets optional custom fields for business logic.
         */
        fun setCustomDataDetails(details: CustomDataDetails?): Builder {
            this.customDataDetails = details
            return this
        }

        /**
         * Sets whether analytics is enabled. Default is true.
         */
        fun setEnabled(enabled: Boolean): Builder {
            this.enabled = enabled
            return this
        }

        /**
         * Builds and returns an [AnalyticsConfig] instance.
         *
         * @throws IllegalArgumentException if [workSpaceId] is blank.
         */
        fun build(): AnalyticsConfig {
            require(workSpaceId.isNotBlank()) { "workSpaceId must not be blank" }
            return AnalyticsConfig(
                videoDataDetails = videoDataDetails,
                customDataDetails = customDataDetails,
                playerView = playerView,
                workSpaceId = workSpaceId,
                enabled = enabled,
                beaconDomain = beaconDomain
            )
        }
    }
}
