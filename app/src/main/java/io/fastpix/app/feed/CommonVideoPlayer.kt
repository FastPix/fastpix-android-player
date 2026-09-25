package io.fastpix.app.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.fastpix.media3.PlaybackListener
import io.fastpix.media3.PlayerView
import io.fastpix.media3.ResizeMode
import io.fastpix.media3.core.FastPixPlayer
import io.fastpix.media3.playlist.PlaylistItem

/**
 * The app's one video player, used by every screen that shows video — a feed page, a detail
 * screen, a preview.
 *
 * It only *shows* a player: whoever owns the player decides what it plays and when. A feed passes
 * the player [io.fastpix.media3.playlist.FastPixPlayerPool] handed it for that page; a standalone
 * screen passes one from [rememberFastPixPlayer]. That split is what lets a feed pre-render its next
 * page — the page's player is already showing its first frame before the page is on screen.
 *
 * Over the video it draws its own UI from the player's state: a spinner until the first frame and
 * while buffering, a play icon while paused, and a message on error. For a pre-rendered page the
 * first frame is already there, so no spinner ever shows.
 */
@UnstableApi
@Composable
fun CommonVideoPlayer(
    player: FastPixPlayer,
    modifier: Modifier = Modifier,
    resizeMode: ResizeMode = ResizeMode.ZOOM,
    tapToPause: Boolean = true,
) {
    var firstFrameShown by remember(player) { mutableStateOf(player.hasRenderedFirstFrame()) }
    var buffering by remember(player) { mutableStateOf(false) }
    var playing by remember(player) { mutableStateOf(player.isPlaying()) }
    var error by remember(player) { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        val listener = object : PlaybackListener {
            override fun onPlay() {
                playing = true
            }

            override fun onPause() {
                playing = false
            }

            override fun onPlaybackStateChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onError(exception: PlaybackException) {
                error = exception.errorCodeName
            }

            override fun onFirstFrameRendered() {
                firstFrameShown = true
            }

            override fun onBufferingStart() {
                buffering = true
            }

            override fun onBufferingEnd() {
                buffering = false
            }
        }
        // A pool may stop this player, or give it another entry, while this page still shows it.
        // Neither is a PlaybackListener event, so watch the engine for them and re-read the
        // player's real state rather than trusting what was last heard.
        val engineListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = resync()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = resync()
            override fun onIsPlayingChanged(isPlaying: Boolean) = resync()

            fun resync() {
                firstFrameShown = player.hasRenderedFirstFrame()
                playing = player.isPlaying()
                if (player.getPlaybackState() == Player.STATE_IDLE) buffering = false
            }
        }
        player.addPlaybackListener(listener)
        player.getExoPlayer().addListener(engineListener)
        engineListener.resync()
        onDispose {
            player.removePlaybackListener(listener)
            player.getExoPlayer().removeListener(engineListener)
        }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.resizeMode = resizeMode
                    isTapGestureEnabled = tapToPause
                    this.player = player
                }
            },
            // A recycled page gets a different player; the view simply switches to it. The SDK's
            // PlayerView never releases a player it was handed, so this is safe in any pager.
            update = { view ->
                view.player = player
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize(),
        )

        when {
            error != null -> Text(
                text = "Can't play this video ($error)",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )

            !firstFrameShown || buffering -> CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )

            !playing -> Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Paused",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(Color(0x66000000), CircleShape)
                    .padding(12.dp),
            )
        }
    }
}

/**
 * A player owned by the calling composable, for screens that play one video on their own: built
 * on first composition, playing [item], released when the composable leaves.
 */
@UnstableApi
@Composable
fun rememberFastPixPlayer(
    item: PlaylistItem,
    configure: FastPixPlayer.Builder.() -> Unit = {},
): FastPixPlayer {
    val context = LocalContext.current
    val player = remember(item) {
        FastPixPlayer.Builder(context)
            .setAutoplay(true)
            .apply(configure)
            .build()
            .apply { setPlaylist(listOf(item)) }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}
