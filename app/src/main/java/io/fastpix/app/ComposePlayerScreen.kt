package io.fastpix.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import io.fastpix.media3.PlayerView
import io.fastpix.media3.PlaybackListener
import io.fastpix.media3.core.FastPixPlayer
import io.fastpix.media3.core.StreamType
import io.fastpix.media3.seekpreview.listeners.SeekPreviewListener
import io.fastpix.media3.seekpreview.models.PreviewFallbackMode
import io.fastpix.media3.seekpreview.models.SeekPreviewConfig
import io.fastpix.media3.seekpreview.models.SpritesheetMetadata
import io.fastpix.media3.tracks.AudioTrack
import io.fastpix.media3.tracks.AudioTrackError
import io.fastpix.media3.tracks.AudioTrackListener
import io.fastpix.media3.tracks.AudioTrackUpdateReason
import io.fastpix.media3.tracks.SubtitleRenderInfo
import io.fastpix.media3.tracks.SubtitleTrack
import io.fastpix.media3.tracks.SubtitleTrackError
import io.fastpix.media3.tracks.SubtitleTrackListener
import kotlin.math.roundToInt

/**
 * Compose UI aligned to the MainActivity XML layout.
 */
@UnstableApi
@Composable
fun ComposePlayerScreen(
    videoModel: DummyData?,
    autoplay: Boolean,
    loop: Boolean,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val player = remember(autoplay, loop) {
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

    var isPlaying by remember { mutableStateOf(autoplay) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerReady by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var volume by remember { mutableFloatStateOf(1f) }
    var isMuted by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var subtitleCue by remember { mutableStateOf("") }
    var previewVisible by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val audioTracks = remember { mutableStateListOf<AudioTrack>() }
    val subtitleTracks = remember { mutableStateListOf<SubtitleTrack>() }
    var selectedAudioTrackId by remember { mutableStateOf<String?>(null) }
    var selectedSubtitleTrackId by remember { mutableStateOf<String?>(null) }
    var seekPreviewTsMs by remember { mutableLongStateOf(0L) }
    var progressDragging by remember { mutableStateOf(false) }
    var progressDragValue by remember { mutableFloatStateOf(0f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var audioMenuExpanded by remember { mutableStateOf(false) }
    var subtitleMenuExpanded by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    val playbackListener = remember {
        object : PlaybackListener {
            override fun onPlay() {
                isPlaying = true
            }

            override fun onPause() {
                isPlaying = false
            }

            override fun onError(error: PlaybackException) {
                lastError = error.message ?: "Playback failed"
                isBuffering = false
            }

            override fun onPlaybackStateChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTimeUpdate(currentPositionMs: Long, durationMs: Long, bufferedPositionMs: Long) {
                currentPosition = currentPositionMs
                duration = durationMs
                bufferedPosition = bufferedPositionMs
                if (!progressDragging) {
                    progressDragValue = currentPositionMs.toFloat()
                }
            }

            override fun onPlayerReady(durationMs: Long) {
                playerReady = true
                duration = durationMs
                isBuffering = false
            }

            override fun onBufferingStart() {
                isBuffering = true
            }

            override fun onBufferingEnd() {
                isBuffering = false
            }

            override fun onPlaybackRateChanged(rate: Float) {
                playbackSpeed = rate
            }

            override fun onMuteStateChanged(isMutedState: Boolean) {
                isMuted = isMutedState
            }

            override fun onVolumeChanged(volumeLevel: Float) {
                volume = volumeLevel
            }
        }
    }

    val seekPreviewListener = remember {
        object : SeekPreviewListener {
            override fun onSpritesheetInitialized() = Unit
            override fun onSpritesheetFailed(error: Throwable) = Unit
            override fun onPreviewShow() {
                previewVisible = true
            }

            override fun onPreviewHide() {
                previewVisible = false
            }

            override fun onSpritesheetLoaded(metadata: SpritesheetMetadata) {
                seekPreviewTsMs = metadata.timestampMs ?: seekPreviewTsMs
                previewBitmap = metadata.bitmap
            }
        }
    }

    val audioTrackListener = remember {
        object : AudioTrackListener {
            override fun onAudioTracksLoaded(tracks: List<AudioTrack>, reason: AudioTrackUpdateReason) {
                audioTracks.clear()
                audioTracks.addAll(tracks)
                selectedAudioTrackId = player.getCurrentAudioTrack()?.id
            }

            override fun onAudioTracksChange(selectedTrack: AudioTrack) {
                selectedAudioTrackId = selectedTrack.id
            }

            override fun onAudioTracksLoadedFailed(error: AudioTrackError) {
                lastError = "Audio track error: $error"
            }

            override fun onAudioTrackSwitching(isSwitching: Boolean) = Unit
        }
    }

    val subtitleTrackListener = remember {
        object : SubtitleTrackListener {
            override fun onSubtitlesLoaded(tracks: List<SubtitleTrack>) {
                subtitleTracks.clear()
                subtitleTracks.addAll(tracks)
                selectedSubtitleTrackId = player.getCurrentSubtitleTrack()?.id
            }

            override fun onSubtitleChange(track: SubtitleTrack?) {
                selectedSubtitleTrackId = track?.id
                if (track == null) subtitleCue = ""
            }

            override fun onSubtitlesLoadedFailed(error: SubtitleTrackError) {
                lastError = "Subtitle track error: $error"
            }

            override fun onSubtitleCueChange(info: SubtitleRenderInfo) {
                subtitleCue = info.cues.joinToString("\n") { it.text.toString() }.trim()
            }
        }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    DisposableEffect(player) {
        player.addPlaybackListener(playbackListener)
        player.addAudioTrackListener(audioTrackListener)
        player.addSubtitleTrackListener(subtitleTrackListener)
        player.setSeekPreviewListener(seekPreviewListener)
        onDispose {
            if (isFullscreen) {
                activity?.exitFullscreenMode()
            }
            player.removePlaybackListener(playbackListener)
            player.removeAudioTrackListener(audioTrackListener)
            player.removeSubtitleTrackListener(subtitleTrackListener)
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
        PlayerSurface(
            player = player,
            onPlayerViewReady = { playerViewRef = it }
        )

        if (subtitleCue.isNotBlank()) {
            Text(
                text = subtitleCue,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 150.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center)
            )
        }

        PreviewOverlay(
            modifier = Modifier
                .align(Alignment.BottomStart),
            previewVisible = previewVisible,
            previewBitmap = previewBitmap,
            seekPreviewTsMs = seekPreviewTsMs
        )

        TopActions(
            modifier = Modifier.align(Alignment.TopEnd),
            subtitleTracks = subtitleTracks,
            audioTracks = audioTracks,
            selectedSubtitleTrackId = selectedSubtitleTrackId,
            selectedAudioTrackId = selectedAudioTrackId,
            playbackSpeed = playbackSpeed,
            speedMenuExpanded = speedMenuExpanded,
            audioMenuExpanded = audioMenuExpanded,
            subtitleMenuExpanded = subtitleMenuExpanded,
            onSpeedMenuToggle = { speedMenuExpanded = it },
            onAudioMenuToggle = { audioMenuExpanded = it },
            onSubtitleMenuToggle = { subtitleMenuExpanded = it },
            onSpeedSelect = { player.setPlaybackSpeed(it) },
            onAudioSelect = { player.setAudioTrack(it) },
            onSubtitleSelect = { player.setSubtitleTrack(it) },
            onDisableSubtitles = { player.disableSubtitles() },
            availableSpeeds = player.getAvailablePlaybackSpeeds()
        )

        BottomControls(
            modifier = Modifier.align(Alignment.BottomStart),
            playerReady = playerReady,
            progressDragging = progressDragging,
            progressDragValue = progressDragValue,
            currentPosition = currentPosition,
            duration = duration,
            volume = volume,
            isMuted = isMuted,
            isPlaying = isPlaying,
            isFullscreen = isFullscreen,
            errorMessage = lastError,
            onProgressChange = { value ->
                progressDragging = true
                progressDragValue = value
                player.loadPreview(value.toLong())
            },
            onProgressFinish = {
                val target = progressDragValue.toLong().coerceIn(0L, duration.coerceAtLeast(0L))
                player.seekTo(target)
                player.hidePreview()
                progressDragging = false
            },
            onTogglePlayPause = { if (isPlaying) player.pause() else player.play() },
            onSeekBack = {
                val target = (currentPosition - 10_000L).coerceAtLeast(0L)
                player.seekTo(target)
            },
            onSeekForward = {
                val target = if (duration > 0L) (currentPosition + 10_000L).coerceAtMost(duration) else currentPosition + 10_000L
                player.seekTo(target)
            },
            onToggleMute = { if (isMuted) player.unmute() else player.mute() },
            onVolumeChange = { level -> player.setVolume(level) },
            onToggleFullscreen = {
                isFullscreen = !isFullscreen
                if (isFullscreen) activity?.enterFullscreenMode() else activity?.exitFullscreenMode()
            }
        )
    }

    LaunchedEffect(progressDragging) {
        if (progressDragging) {
            player.showPreview()
        }
    }
}

private fun formatPlaybackSpeedLabel(speed: Float): String {
    return if (speed % 1f == 0f) {
        "${speed.roundToInt()}x"
    } else {
        "${speed}x"
    }
}

@Composable
private fun PlayerSurface(
    player: FastPixPlayer,
    onPlayerViewReady: (PlayerView) -> Unit
) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                retainPlayerOnConfigChange = true
                isTapGestureEnabled = true
                this.player = player
                onPlayerViewReady(this)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun PreviewOverlay(
    modifier: Modifier,
    previewVisible: Boolean,
    previewBitmap: android.graphics.Bitmap?,
    seekPreviewTsMs: Long
) {
    if (!previewVisible || previewBitmap == null) return
    Column(
        modifier = modifier
            .offset(x = 16.dp, y = (-190).dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .padding(4.dp)
    ) {
        androidx.compose.foundation.Image(
            bitmap = previewBitmap.asImageBitmap(),
            contentDescription = "Seek preview thumbnail",
            modifier = Modifier.size(width = 100.dp, height = 150.dp)
        )
        Text(
            text = Utils.formatDurationSmart(seekPreviewTsMs),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp)
        )
    }
}

@Composable
private fun TopActions(
    modifier: Modifier,
    subtitleTracks: List<SubtitleTrack>,
    audioTracks: List<AudioTrack>,
    selectedSubtitleTrackId: String?,
    selectedAudioTrackId: String?,
    playbackSpeed: Float,
    speedMenuExpanded: Boolean,
    audioMenuExpanded: Boolean,
    subtitleMenuExpanded: Boolean,
    onSpeedMenuToggle: (Boolean) -> Unit,
    onAudioMenuToggle: (Boolean) -> Unit,
    onSubtitleMenuToggle: (Boolean) -> Unit,
    onSpeedSelect: (Float) -> Unit,
    onAudioSelect: (String) -> Unit,
    onSubtitleSelect: (String) -> Unit,
    onDisableSubtitles: () -> Unit,
    availableSpeeds: FloatArray
) {
    Row(
        modifier = modifier.padding(top = 50.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (subtitleTracks.isNotEmpty()) {
            IconButton(onClick = { onSubtitleMenuToggle(true) }) {
                Icon(painter = painterResource(id = R.drawable.closed_caption), contentDescription = "Subtitle tracks")
            }
        }
        if (audioTracks.isNotEmpty()) {
            IconButton(onClick = { onAudioMenuToggle(true) }) {
                Icon(painter = painterResource(id = R.drawable.ic_audiotrack), contentDescription = "Audio tracks")
            }
        }
        IconButton(onClick = { onSpeedMenuToggle(true) }) {
            Icon(painter = painterResource(id = R.drawable.ic_speed), contentDescription = "Playback speed")
        }

        DropdownMenu(expanded = speedMenuExpanded, onDismissRequest = { onSpeedMenuToggle(false) }) {
            availableSpeeds.forEach { speed ->
                DropdownMenuItem(
                    text = { Text("${formatPlaybackSpeedLabel(speed)}${if (kotlin.math.abs(speed - playbackSpeed) < 0.01f) " ✓" else ""}") },
                    onClick = {
                        onSpeedSelect(speed)
                        onSpeedMenuToggle(false)
                    }
                )
            }
        }

        DropdownMenu(expanded = audioMenuExpanded, onDismissRequest = { onAudioMenuToggle(false) }) {
            audioTracks.forEach { track ->
                val label = track.label ?: track.languageCode ?: "Track ${track.id}"
                DropdownMenuItem(
                    text = { Text("$label${if (track.id == selectedAudioTrackId) " ✓" else ""}") },
                    onClick = {
                        onAudioSelect(track.id)
                        onAudioMenuToggle(false)
                    }
                )
            }
        }

        DropdownMenu(expanded = subtitleMenuExpanded, onDismissRequest = { onSubtitleMenuToggle(false) }) {
            DropdownMenuItem(
                text = { Text("Off${if (selectedSubtitleTrackId == null) " ✓" else ""}") },
                onClick = {
                    onDisableSubtitles()
                    onSubtitleMenuToggle(false)
                }
            )
            subtitleTracks.forEach { track ->
                val label = track.label ?: track.languageCode ?: "Track ${track.id}"
                DropdownMenuItem(
                    text = { Text("$label${if (track.id == selectedSubtitleTrackId) " ✓" else ""}") },
                    onClick = {
                        onSubtitleSelect(track.id)
                        onSubtitleMenuToggle(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun BottomControls(
    modifier: Modifier,
    playerReady: Boolean,
    progressDragging: Boolean,
    progressDragValue: Float,
    currentPosition: Long,
    duration: Long,
    volume: Float,
    isMuted: Boolean,
    isPlaying: Boolean,
    isFullscreen: Boolean,
    errorMessage: String?,
    onProgressChange: (Float) -> Unit,
    onProgressFinish: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleFullscreen: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (playerReady) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = Utils.formatDurationSmart(if (progressDragging) progressDragValue.toLong() else currentPosition),
                    modifier = Modifier.weight(1f)
                )
                Text(text = Utils.formatDurationSmart(duration), modifier = Modifier.weight(1f))
            }
        }

        Slider(
            value = if (progressDragging) progressDragValue else currentPosition.toFloat(),
            onValueChange = onProgressChange,
            valueRange = 0f..(if (duration > 0L) duration.toFloat() else 1f),
            onValueChangeFinished = onProgressFinish,
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ControlIconButton(
                iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                contentDescription = "Play pause",
                onClick = onTogglePlayPause
            )
            ControlIconButton(
                iconRes = R.drawable.ic_backward_10,
                contentDescription = "Backward 10 seconds",
                onClick = onSeekBack
            )
            ControlIconButton(
                iconRes = R.drawable.ic_forward_10,
                contentDescription = "Forward 10 seconds",
                onClick = onSeekForward
            )
            ControlIconButton(
                iconRes = if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on,
                contentDescription = "Volume",
                onClick = onToggleMute
            )
            Slider(
                value = (volume * 100f).coerceIn(0f, 100f),
                onValueChange = { progress -> onVolumeChange((progress / 100f).coerceIn(0f, 1f)) },
                valueRange = 0f..100f,
                modifier = Modifier.width(120.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            ControlIconButton(
                iconRes = if (isFullscreen) R.drawable.ic_full_screen_exit else R.drawable.ic_full_screen,
                contentDescription = "Fullscreen",
                onClick = onToggleFullscreen
            )
        }

        if (errorMessage != null) {
            Text(text = errorMessage, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ControlIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = ImageVector.vectorResource(id = iconRes),
            contentDescription = contentDescription
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun Activity.enterFullscreenMode() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }
}

private fun Activity.exitFullscreenMode() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}
