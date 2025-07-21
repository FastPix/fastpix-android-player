package io.fastpix.player

import android.content.Context
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.Listener
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import io.fastpix.data.entity.CustomerDataEntity
import io.fastpix.data.exo.FastPixBaseMedia3Player
import io.fastpix.data.request.CustomOptions
import java.io.IOException

class MediaFastPixPlayer private constructor(
    val exoPlayer: ExoPlayer,
    context: Context,
    dynamicCustomerData: CustomerDataEntity,
) : Player by exoPlayer {
    private var fastPixBaseMedia3Player: FastPixBaseMedia3Player? = null
    private var released: Boolean = false

    fun enableAnalyticsTracking(listener: AnalyticsListener) {
        exoPlayer.addAnalyticsListener(listener)
    }

    fun playerSizeChanged(widthPx: Int, heightPx: Int) {
        fastPixBaseMedia3Player?.setPlayerSize(widthPx, heightPx)
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        if (surfaceView != null) {
            fastPixBaseMedia3Player?.setPlayerView(surfaceView)
        }
        exoPlayer.setVideoSurfaceView(surfaceView)
    }

    override fun setVideoTextureView(textureView: TextureView?) {
        if (textureView != null) {
            fastPixBaseMedia3Player?.setPlayerView(textureView)
        }
        exoPlayer.setVideoTextureView(textureView)
    }

    override fun release() {
        fastPixBaseMedia3Player?.release()
        fastPixBaseMedia3Player = null
        exoPlayer?.release()
        released =true
    }

    init {
        fastPixBaseMedia3Player =
            FastPixBaseMedia3Player(
                context,
                exoPlayer,
                dynamicCustomerData,
                CustomOptions()
            )

    }
    class Builder private constructor(
        private val context: Context,
        private val playerBuilder: ExoPlayer.Builder,
    ) {
        private var customerDataEntity: CustomerDataEntity = CustomerDataEntity()
        constructor(context: Context) : this(context, ExoPlayer.Builder(context))

        fun pushMonitoringData(customerData: CustomerDataEntity): Builder {
            this.customerDataEntity.setCustomerPlayerData(customerData.customerPlayerData)
            this.customerDataEntity.setCustomerVideoData(customerData.customerVideoData)
            this.customerDataEntity.setCustomerViewData(customerData.customerViewData)
            this.customerDataEntity.setCustomData(customerData.customData)


            return this
        }

        fun configureExoPlayer(block: ExoPlayer.Builder.() -> Unit): Builder {
            playerBuilder.block()
            return this
        }
        fun build(): MediaFastPixPlayer {
            return MediaFastPixPlayer(
                context = context,
                exoPlayer = this.playerBuilder.build(),
                dynamicCustomerData = customerDataEntity)
        }
    }
}
