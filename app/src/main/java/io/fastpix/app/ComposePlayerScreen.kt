package io.fastpix.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import io.fastpix.media3.PlayerView
import io.fastpix.media3.core.FastPixPlayer
import io.fastpix.media3.core.StreamType
import io.fastpix.media3.PlaybackListener
import io.fastpix.media3.seekpreview.models.PreviewFallbackMode
import io.fastpix.media3.seekpreview.models.SeekPreviewConfig

/**
 * Compose usage of FastPixPlayer: embeds the existing [PlayerView] via [AndroidView]
 * and wires a [FastPixPlayer] with lifecycle and media setup.
 *
 * Use this to verify the SDK works in Compose without a dedicated Compose module.
 */
@UnstableApi
@Composable
fun ComposePlayerScreen(
    videoModel: DummyData?,
    autoplay: Boolean,
    loop: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val player = remember {
        FastPixPlayer.Builder(context)
            .setLoop(loop)
            .setAutoplay(autoplay)
            .setSeekPreviewConfig(
                SeekPreviewConfig.Builder()
                    .setEnabled(true)
                    .setFallbackMode(PreviewFallbackMode.TIMESTAMP)
                    .build()
            )
            .build()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    val playbackListener = remember {
        object : PlaybackListener {
            override fun onPlay() {}
            override fun onPause() {}
            override fun onError(error: PlaybackException) {}
            override fun onPlaybackStateChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTimeUpdate(
                currentPositionMs: Long,
                durationMs: Long,
                bufferedPositionMs: Long
            ) {
                currentPosition = currentPositionMs
                duration = durationMs
            }
        }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    DisposableEffect(Unit) {
        player.addPlaybackListener(playbackListener)
        onDispose {
            player.removePlaybackListener(playbackListener)
            playerViewRef?.player = null
            player.release()
        }
    }

    LaunchedEffect(videoModel) {
        if (videoModel == null) return@LaunchedEffect
        val playbackUrl = videoModel.url
        if (playbackUrl.isNotBlank()) {
            player.setMediaItem(MediaItem.fromUri(playbackUrl))
        } else {
            player.setFastPixMediaItem {
                playbackId = videoModel.id
                streamType = StreamType.onDemand
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    retainPlayerOnConfigChange = true
                    isTapGestureEnabled = true
                    this.player = player
                    playerViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top bar: back + title
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Back")
            }
            Text(
                text = videoModel?.id ?: "Compose Player",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Bottom: time and play state (optional)
        Text(
            text = "${Utils.formatDurationSmart(currentPosition)} / ${
                Utils.formatDurationSmart(
                    duration
                )
            }",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
