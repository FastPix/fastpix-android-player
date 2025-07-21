package io.fastpix.fastpixplayer

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import io.fastpix.data.entity.CustomDataEntity
import io.fastpix.data.entity.CustomerDataEntity
import io.fastpix.data.entity.CustomerPlayerDataEntity
import io.fastpix.data.entity.CustomerVideoDataEntity
import io.fastpix.data.entity.CustomerViewDataEntity
import io.fastpix.fastpixplayer.databinding.ActivityCustomizedPlayerBinding
import io.fastpix.player.FastPixUrlGenerator
import io.fastpix.player.MediaFastPixPlayer
import io.fastpix.player.PlaybackResolution
import io.fastpix.player.RenditionOrder
import java.util.UUID

class CustomizedPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCustomizedPlayerBinding
    private val playerView get() = binding.player
    private var player: MediaFastPixPlayer? = null
    private var playbackPosition: Long = 0
    private var playWhenReady = true
    private lateinit var fastPixListener: Player.Listener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomizedPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        startPlayback()
    }

    private fun startPlayback() {
        fastPixListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@CustomizedPlayerActivity,
                    "Playback error! ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        FastPixUrlGenerator.setPlayerListener(fastPixListener)
        val mediaItem = FastPixUrlGenerator.createCustomPlaybackUri(
            playbackId = "Your_Playback_Id",
            minResolution = PlaybackResolution.LD_480,
            maxResolution = PlaybackResolution.FHD_1080,
            resolution = PlaybackResolution.LD_480,
            renditionOrder = RenditionOrder.Descending,
            playbackToken = "",
            customDomain = "",
            streamType = "on-demand"
        )?:return
        if (player != null ) {
            player!!.playWhenReady = false
        }
        val item = mediaItem.build()
        if (item != player?.currentMediaItem) {
            beginPlayback(item)
        }
    }

    private fun beginPlayback(mediaItem: MediaItem) {

        val player = createPlayer(this)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = playWhenReady
        player.seekTo(playbackPosition)
        this.playerView.player = player
        this.player = player
    }

    private fun createPlayer(context: Context): MediaFastPixPlayer {
        val customerPlayerDataEntity = CustomerPlayerDataEntity()
        customerPlayerDataEntity.workspaceKey = "Your_WorkSpace_Key"
        customerPlayerDataEntity.playerName = "Fastpix Player"
        customerPlayerDataEntity.playerVersion = "1.0.0"
        val customerViewDataEntity = CustomerViewDataEntity()
        customerViewDataEntity.viewSessionId = UUID.randomUUID().toString()

        val customerVideoDataEntity = CustomerVideoDataEntity()
        customerVideoDataEntity.videoId = "videoId"
        customerVideoDataEntity.videoTitle = "videoTitle"

        val customDataEntity = CustomDataEntity()
        customDataEntity.customData1 = "item1"
        customDataEntity.customData2 = "item2"

        val customerDataEntity = CustomerDataEntity(
            customerPlayerDataEntity,
            customerVideoDataEntity,
            customerViewDataEntity
        )
        customerDataEntity.setCustomData(customDataEntity)

        val mediaFastPixPlayer: MediaFastPixPlayer = MediaFastPixPlayer.Builder(context)
            .pushMonitoringData(customerDataEntity)
            .configureExoPlayer {
                setHandleAudioBecomingNoisy(true)
            }.build()


        mediaFastPixPlayer.addListener(fastPixListener)


        return mediaFastPixPlayer
    }

    override fun onStop() {
        destroyPlayer()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        destroyPlayer()
    }

    private fun destroyPlayer() {
        if (player != null ) {
            playbackPosition = player!!.currentPosition
            playWhenReady = player!!.playWhenReady
            playerView.player = null
            player?.release()
            player = null
        }
    }

}
