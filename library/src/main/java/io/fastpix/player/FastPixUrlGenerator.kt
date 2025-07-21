package io.fastpix.player

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.fastpix.data.exo.FastPixBaseMedia3Player
import io.fastpix.data.request.FastPixMetrics
import io.fastpix.data.request.RequestFailureException
import java.io.IOException

object FastPixUrlGenerator {
    private const val DEFAULT_DOMAIN = "stream.fastpix.io"
    private const val STREAM_TYPE = "on-demand"
    private var listener: Player.Listener? = null
    private val ERROR_INVALID_STREAM_TYPE = 9001
    private val ERROR_EMPTY_PLAYBACK_ID = 9002
    fun setPlayerListener(l: Player.Listener) {
        listener = l
    }

    @OptIn(UnstableApi::class)
    fun createCustomPlaybackUri(
        playbackId: String,
        maxResolution: PlaybackResolution? = null,
        minResolution: PlaybackResolution? = null,
        resolution: PlaybackResolution? = null,
        renditionOrder: RenditionOrder? = null,
        customDomain: String? = DEFAULT_DOMAIN,
        streamType: String? = STREAM_TYPE,
        playbackToken: String? = null
    ): MediaItem.Builder? {

        if (playbackId.isBlank()) {
            val exception = PlaybackException(
                "Playback ID is empty",
                IllegalArgumentException(),
                ERROR_EMPTY_PLAYBACK_ID
            )
            listener?.onPlayerError(exception)
            return null

        }
        if (streamType !in listOf("on-demand", "live-stream")) {
            val exception = PlaybackException(
                "Invalid stream type. Must be 'on-demand' or 'live-stream'",
                IllegalArgumentException(),
                ERROR_INVALID_STREAM_TYPE
            )
            listener?.onPlayerError(exception)
            return null

        }
        val urlSting = createPlaybackUrl(
            playbackId = playbackId,
            customDomain = customDomain ?: DEFAULT_DOMAIN,
            maxResolution = maxResolution,
            minResolution = minResolution,
            resolution = resolution,
            renditionOrder = renditionOrder,
            playbackToken = playbackToken,
        )
        return  MediaItem.Builder().setUri(urlSting)

    }

    @OptIn(UnstableApi::class)
    private fun createPlaybackUrl(
        playbackId: String,
        customDomain: String = DEFAULT_DOMAIN,
        maxResolution: PlaybackResolution? = null,
        minResolution: PlaybackResolution? = null,
        resolution: PlaybackResolution? = null,
        renditionOrder: RenditionOrder? = null,
        playbackToken: String? = null,
    ): String {
        val base = Uri.Builder()
            .scheme("https")
            .authority(customDomain)
            .appendPath("$playbackId.m3u8")
            .apply {
                minResolution?.let {
                    appendQueryParameter(
                        "minResolution",
                        resolutionValue(it)
                    )
                }
                maxResolution?.let {
                    appendQueryParameter(
                        "maxResolution",
                        resolutionValue(it)
                    )
                }
                resolution?.let { appendQueryParameter("resolution", resolutionValue(it)) }
                renditionOrder?.takeIf { it != RenditionOrder.Default }
                    ?.let { appendQueryParameter("renditionOrder", renditionValue(it)) }
                playbackToken?.let { appendQueryParameter("token", it) }
            }
            .build()

        Log.e("base", base.toString())
        return base.toString()
    }

    fun renditionValue(renditionOrder: RenditionOrder): String {
        return when (renditionOrder) {
            RenditionOrder.Descending -> "desc"
            RenditionOrder.Ascending -> "asc"
            else -> ""
        }
    }

    fun resolutionValue(playbackResolution: PlaybackResolution): String {
        return when (playbackResolution) {
            PlaybackResolution.LD_480 -> "480p"
            PlaybackResolution.LD_540 -> "540p"
            PlaybackResolution.HD_720 -> "720p"
            PlaybackResolution.FHD_1080 -> "1080p"
            PlaybackResolution.QHD_1440 -> "1440p"
            PlaybackResolution.FOUR_K_2160 -> "2160p"
        }
    }
}


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
