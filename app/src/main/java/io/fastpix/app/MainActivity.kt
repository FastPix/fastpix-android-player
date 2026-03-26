package io.fastpix.app

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import io.fastpix.app.databinding.ActivityMainBinding
import io.fastpix.data.domain.model.VideoDataDetails
import io.fastpix.media3.PlaybackListener
import io.fastpix.media3.analytics.AnalyticsConfig
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


/**
 * Example usage of io.fastpix.media3.PlayerView.
 *
 * This demonstrates:
 * - Adding PlayerView via XML
 * - Setting media source
 * - Using playback control APIs (play, pause, toggle)
 * - Listening to playback events
 * - Seek interaction callbacks (onSeekStart, onSeekEnd)
 * - Programmatic seeking via seekTo() API
 * - Handling lifecycle (orientation changes)
 */

@UnstableApi
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private var videoModel: DummyData? = null
    private lateinit var fastPixPlayer: FastPixPlayer

    /**
     * PlaybackListener implementation to receive playback events.
     */
    private val playbackListener = object : PlaybackListener {
        override fun onPlay() {
            binding.loader.isVisible = false
            binding.ivPlayPause.setImageDrawable(
                ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_play,
                    null
                )
            )
        }

        override fun onPause() {
            binding.ivPlayPause.setImageDrawable(
                ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_pause,
                    null
                )
            )
        }

        override fun onTimeUpdate(
            currentPositionMs: Long,
            durationMs: Long,
            bufferedPositionMs: Long
        ) {
            super.onTimeUpdate(currentPositionMs, durationMs, bufferedPositionMs)
            binding.sbProgress.max = durationMs.toInt()
            binding.tvStartTime.text = Utils.formatDurationSmart(currentPositionMs)
            binding.tvEndTime.text = Utils.formatDurationSmart(fastPixPlayer.getDuration())
            binding.sbProgress.progress = currentPositionMs.toInt()
            binding.sbProgress.secondaryProgress = bufferedPositionMs.toInt()
        }

        override fun onCompleted() {
            super.onCompleted()
            Log.e(TAG, "onCompleted: ")
        }

        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            if (isPlaying) {
                binding.ivPlayPause.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_pause,
                        null
                    )
                )
            } else {
                binding.ivPlayPause.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_play,
                        null
                    )
                )
            }
        }

        override fun onBufferingStart() {
            super.onBufferingStart()
            binding.loader.isVisible = true
        }

        override fun onBufferingEnd() {
            super.onBufferingEnd()
            binding.loader.isVisible = false
        }

        override fun onError(error: androidx.media3.common.PlaybackException) {
            Toast.makeText(
                this@MainActivity,
                "Playback error: ${error.message}",
                Toast.LENGTH_LONG
            ).show()
        }

        override fun onSeekStart(currentPositionMs: Long) {
            super.onSeekStart(currentPositionMs)
            val positionSeconds = currentPositionMs / 1000
            Log.d(TAG, "Seek started from position: ${positionSeconds}s")
        }

        override fun onSeekEnd(
            fromPositionMs: Long,
            toPositionMs: Long,
            durationMs: Long
        ) {
            super.onSeekEnd(fromPositionMs, toPositionMs, durationMs)
            val fromSeconds = fromPositionMs / 1000
            val toSeconds = toPositionMs / 1000
            val durationSeconds = if (durationMs > 0) durationMs / 1000 else 0
            Log.d(
                TAG,
                "Seek completed: ${fromSeconds}s -> ${toSeconds}s (duration: ${durationSeconds}s)"
            )
        }

        override fun onMuteStateChanged(isMuted: Boolean) {
            super.onMuteStateChanged(isMuted)
            isMute = isMuted
            updateVolumeIcon(isMute)
        }

        override fun onVolumeChanged(volumeLevel: Float) {
            super.onVolumeChanged(volumeLevel)
            Log.d(TAG, "onVolumeChanged: $volumeLevel")
            binding.sbVolumeSlider.progress = (volumeLevel * 100).toInt()
            updateVolumeIcon(isMute)
        }

        override fun onPlayerReady(durationMs: Long) {
            super.onPlayerReady(durationMs)
            binding.playerControls.isVisible = true
        }
    }

    private val seekPreviewListener = object : SeekPreviewListener {
        override fun onSpritesheetInitialized() {
            Log.d(TAG, "Seek preview initialized and ready")
        }

        override fun onSpritesheetFailed(error: Throwable) {
            Log.e(TAG, "Seek preview failed", error)
        }

        override fun onPreviewShow() {
            binding.ivSeekPreviewRl.visibility = View.VISIBLE
            binding.tvSeekPreview.visibility = View.VISIBLE
        }

        override fun onPreviewHide() {
            binding.ivSeekPreviewRl.visibility = View.GONE
        }

        override fun onSpritesheetLoaded(metadata: SpritesheetMetadata) {
            binding.ivSeekPreview.setImageBitmap(metadata.bitmap)
            metadata.timestampMs?.let {
                binding.tvSeekPreview.text = Utils.formatDurationSmart(it)
            }
        }
    }

    /**
     * Audio track listener: shows audio track button when multiple tracks exist,
     * toasts on switch/failure, and logs lifecycle.
     */
    private val audioTrackListener = object : AudioTrackListener {
        override fun onAudioTracksLoaded(
            tracks: List<AudioTrack>,
            reason: AudioTrackUpdateReason
        ) {
            binding.ivAudioTracks.isVisible = tracks.isNotEmpty()
            Log.d(TAG, "Audio tracks loaded: ${tracks.size} tracks, reason=$reason")
        }

        override fun onAudioTracksChange(selectedTrack: AudioTrack) {
            Log.d(
                TAG,
                "Audio track changed: ${selectedTrack.label ?: selectedTrack.languageCode ?: selectedTrack.id}"
            )
            Toast.makeText(
                this@MainActivity,
                "Audio: ${selectedTrack.label ?: selectedTrack.languageCode ?: "Track ${selectedTrack.id}"}",
                Toast.LENGTH_SHORT
            ).show()
        }

        override fun onAudioTracksLoadedFailed(error: AudioTrackError) {
            val message = when (error) {
                is AudioTrackError.TrackNotFound -> "Track not found: ${error.trackId}"
                is AudioTrackError.TrackNotPlayable -> "Track not playable: ${error.trackId}"
                is AudioTrackError.SelectionFailed -> "Selection failed: ${error.message ?: error.trackId}"
                is AudioTrackError.PlayerNotReady -> "Player not ready: ${error.trackId}"
            }
            Log.e(TAG, "Audio track error: $message")
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }

        override fun onAudioTrackSwitching(isSwitching: Boolean) {
            Log.d(TAG, "Audio track switching: $isSwitching")
        }
    }

    /**
     * Subtitle track listener: shows subtitle button when tracks exist, toasts on change/failure,
     * and updates the subtitle cue text overlay.
     */
    private val subtitleTrackListener = object : SubtitleTrackListener {
        override fun onSubtitlesLoaded(tracks: List<SubtitleTrack>) {
            binding.ivSubtitles.isVisible = tracks.isNotEmpty()
            Log.d(TAG, "Subtitle tracks loaded: ${tracks.size} tracks")
        }

        override fun onSubtitleChange(track: SubtitleTrack?) {
            val label = track?.label ?: track?.languageCode ?: track?.id ?: "Off"
            Log.d(TAG, "Subtitle changed: $label")
            Toast.makeText(
                this@MainActivity,
                "Subtitle: $label",
                Toast.LENGTH_SHORT
            ).show()
            if (track == null) {
                binding.tvSubtitleCue.isVisible = false
                binding.tvSubtitleCue.text = ""
            }
        }

        override fun onSubtitlesLoadedFailed(error: SubtitleTrackError) {
            val message = when (error) {
                is SubtitleTrackError.TrackNotFound -> "Subtitle track not found: ${error.trackId}"
                is SubtitleTrackError.TrackNotPlayable -> "Subtitle not playable: ${error.trackId}"
                is SubtitleTrackError.SelectionFailed -> "Subtitle selection failed: ${error.message ?: error.trackId}"
                is SubtitleTrackError.PlayerNotReady -> "Player not ready: ${error.trackId}"
            }
            Log.e(TAG, "Subtitle track error: $message")
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }

        override fun onSubtitleCueChange(info: SubtitleRenderInfo) {
            val text = info.cues.joinToString("\n") { it.text.toString() }.trim()
            Log.e(TAG, "onSubtitleCueChange: $info")
            /*if (text.isEmpty()) {
                binding.tvSubtitleCue.isVisible = false
                binding.tvSubtitleCue.text = ""
            } else {
                binding.tvSubtitleCue.isVisible = true
                binding.tvSubtitleCue.text = text
            }*/
        }
    }

    private var isMute = false
    private var isAutoPlayEnabled = false
    private var isLoopEnabled = false
    private var autoRotateObserver: ContentObserver? = null
    private var defaultAudioName: String? = null
    private var defaultSubtitleName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        keepScreenOn()
        videoModel =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(VideoListScreen.VIDEO_MODEL, DummyData::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(VideoListScreen.VIDEO_MODEL) as DummyData?
            }
        isAutoPlayEnabled = intent.getBooleanExtra(VideoListScreen.AUTO_PLAY, false)
        isLoopEnabled = intent.getBooleanExtra(VideoListScreen.LOOP, false)
        defaultAudioName = intent.getStringExtra(VideoListScreen.DEFAULT_AUDIO_NAME)
        defaultSubtitleName = intent.getStringExtra(VideoListScreen.DEFAULT_SUBTITLE_NAME)

        // Lock to portrait initially when auto-rotate is off
        if (!isAutoRotateEnabled()) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        registerAutoRotateObserver()

        setupPlayerView()
        setupControls()
    }

    /**
     * Setup PlayerView with media source and listeners.
     * This demonstrates the core PlayerView API and FastPix media item integration.
     *
     * Following Media3 pattern: Create player with configuration, then pass to PlayerView.
     */
    private fun setupPlayerView() {
        binding.loader.isVisible = false
        binding.playerView.retainPlayerOnConfigChange = true
        binding.playerView.isTapGestureEnabled = false

        // Create FastPixPlayer with desired configuration (playback + optional seek preview)
        fastPixPlayer = FastPixPlayer.Builder(this)
            .setLoop(isLoopEnabled)
            .setAutoplay(isAutoPlayEnabled)
            .setAnalyticsConfig(
                AnalyticsConfig.Builder(
                    binding.playerView,
                    "work-space-key"
                )
                    .setVideoDataDetails(VideoDataDetails("video-id", "video-title")).build()
            )
            .setSeekPreviewConfig(
                SeekPreviewConfig.Builder()
                    .setEnabled(true)
                    .setFallbackMode(PreviewFallbackMode.TIMESTAMP)
                    .build()
            )
            .build()

        fastPixPlayer.addPlaybackListener(playbackListener)
        fastPixPlayer.addAudioTrackListener(audioTrackListener)
        fastPixPlayer.addSubtitleTrackListener(subtitleTrackListener)
        fastPixPlayer.setSeekPreviewListener(seekPreviewListener)

        // Apply dropdown-selected defaults (language NAME), without forcing anything when "Auto/Off".
        defaultAudioName
            ?.takeIf { it.isNotBlank() && !it.equals("Auto", ignoreCase = true) }
            ?.let { fastPixPlayer.setDefaultAudioTrack(it) }

        defaultSubtitleName
            ?.takeIf { it.isNotBlank() && !it.equals("Off", ignoreCase = true) }
            ?.let { fastPixPlayer.setDefaultSubtitleTrack(it) }

        binding.playerView.player = fastPixPlayer
        setupMediaItem()
    }

    /**
     * Sets up the media item for playback.
     * Called after the player is initialized.
     */
    private fun setupMediaItem() {

        // Use builder pattern to create and set FastPix MediaItem from playback ID
        var playbackUrl = videoModel?.url
        if (playbackUrl.isNullOrEmpty()) {
            // Use the builder pattern to configure FastPix media item
            val success = fastPixPlayer.setFastPixMediaItem {
                playbackId = "66ee6d27-e1c0-4d15-99b2-153e26389c90"
                streamType = StreamType.onDemand
                // Optional: You can configure resolution, token, etc. here
                // maxResolution = PlaybackResolution.FHD_1080
                // playbackToken = "your-token-here"
            }
            if (!success) {
                // Fallback to direct URL if FastPix media item creation fails
                val mediaItem = MediaItem.fromUri(videoModel?.url.orEmpty())
                binding.playerView.setMediaItem(mediaItem)
            }
        } else {
            // Fallback to direct URL if no playback ID is available
            val mediaItem = MediaItem.fromUri(videoModel?.url.orEmpty())
            binding.playerView.setMediaItem(mediaItem)
        }

        // Autoplay is already configured during player creation, no need to set it again
    }

    private fun togglePlayPause() {
        if (fastPixPlayer.isPlaying()) {
            fastPixPlayer.pause()
            binding.ivPlayPause.setImageDrawable(
                ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_play,
                    null
                )
            )
        } else {
            fastPixPlayer.play()
            binding.ivPlayPause.setImageDrawable(
                ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_pause,
                    null
                )
            )
        }
    }

    /**
     * Setup control button listeners.
     * Demonstrates the public playback control APIs.
     */
    private fun setupControls() {
        // Set max to 100 for granular volume control (0-100 maps to 0.0-1.0)
        binding.sbVolumeSlider.max = 100

        // Initialize volume slider with current player volume
        val currentVolume = fastPixPlayer.getVolume()
        binding.sbVolumeSlider.progress = (currentVolume * 100).toInt()

        binding.ivPlayPause.setOnClickListener {
            togglePlayPause()
        }

        binding.ivBackwardSeek.setOnClickListener {
            handleBackwardSeek()
        }

        binding.ivForwardSeek.setOnClickListener {
            handleForwardSeek()
        }

        binding.sbVolumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    // Convert progress (0-100) to volume (0.0-1.0)
                    val volume = progress / 100f
                    Log.d(TAG, "Volume slider changed: progress=$progress, volume=$volume")
                    fastPixPlayer.setVolume(volume)

                    // Update mute state based on volume
                    val wasMuted = isMute
                    isMute = volume == 0f

                    // Update icon if mute state changed
                    if (wasMuted != isMute) {
                        updateVolumeIcon(isMute)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                Log.d(TAG, "Volume slider tracking started")
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Log.d(TAG, "Volume slider tracking stopped")
            }

        })

        binding.ivVolume.setOnClickListener {
            if (isMute) {
                isMute = false
                fastPixPlayer.unmute()
                // Update volume slider to reflect unmuted volume
                val currentVolume = fastPixPlayer.getVolume()
                binding.sbVolumeSlider.progress = (currentVolume * 100).toInt()
                updateVolumeIcon(false)
            } else {
                isMute = true
                fastPixPlayer.mute()
                // Update volume slider to 0 when muted
                binding.sbVolumeSlider.progress = 0
                updateVolumeIcon(true)
            }
        }

        binding.ivFullScreen.setOnClickListener {
            if (isFullscreen) {
                exitFullscreen()
            } else {
                enterFullscreen()
            }
        }

        binding.ivMore.setOnClickListener {
            showPlaybackSpeedMenu(it)
        }

        binding.ivAudioTracks.setOnClickListener {
            showAudioTrackMenu(it)
        }

        binding.ivSubtitles.setOnClickListener {
            showSubtitleTrackMenu(it)
        }

        binding.sbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvStartTime.text = Utils.formatDurationSmart(progress.toLong())
                    fastPixPlayer.loadPreview(progress.toLong())
                    seekBar?.let { movePreviewToThumb(it) }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                fastPixPlayer.showPreview()
                seekBar?.let { movePreviewToThumb(it) }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                fastPixPlayer.hidePreview()
                // Seek only when user releases the seekbar
                val progress = seekBar?.progress ?: 0
                val duration = fastPixPlayer.getDuration()
                if (duration != C.TIME_UNSET && progress >= 0) {
                    val position = progress.toLong().coerceIn(0, duration)
                    Log.d(TAG, "Seeking to position: ${position / 1000}s")
                    fastPixPlayer.seekTo(position)
                }
            }
        })
    }

    /**
     * Moves the seek preview thumbnail horizontally to align with the seekbar thumb.
     */
    private fun movePreviewToThumb(seekBar: SeekBar) {
        val preview = binding.ivSeekPreviewRl

        preview.post {
            val thumb = seekBar.thumb ?: return@post

            // Thumb bounds give exact on-screen position
            val thumbBounds = thumb.bounds
            val thumbCenterX = thumbBounds.exactCenterX()

            val previewWidth = preview.width
            val parentWidth = (preview.parent as View).width

            // Center preview exactly above thumb
            val targetX = (thumbCenterX - previewWidth / 3f)
                .coerceIn(0f, parentWidth - previewWidth.toFloat())

            preview.translationX = targetX
        }
    }

    private var isFullscreen = false

    /**
     * Checks whether system auto-rotate is enabled.
     */
    private fun isAutoRotateEnabled(): Boolean {
        return Settings.System.getInt(
            contentResolver,
            Settings.System.ACCELEROMETER_ROTATION, 0
        ) == 1
    }

    /**
     * Registers a ContentObserver to react when the user toggles auto-rotate
     * in system settings (e.g. from the notification shade).
     */
    private fun registerAutoRotateObserver() {
        autoRotateObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                onAutoRotateSettingChanged()
            }
        }
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            false,
            autoRotateObserver!!
        )
    }

    /**
     * Called when the system auto-rotate setting changes at runtime.
     * Adjusts orientation locking accordingly.
     */
    private fun onAutoRotateSettingChanged() {
        if (isAutoRotateEnabled()) {
            // Auto-rotate turned ON: let sensor control orientation
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            // Auto-rotate turned OFF: lock to current orientation
            requestedOrientation = if (isFullscreen) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    private fun enterFullscreen() {
        isFullscreen = true
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        if (isAutoRotateEnabled()) {
            // Let sensor take over once the rotation settles
            Handler(Looper.getMainLooper()).postDelayed({
                if (isFullscreen) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }, 1000)
        }

        hideSystemUI()
        binding.ivFullScreen.setImageResource(R.drawable.ic_full_screen_exit)
    }

    @SuppressLint("InlinedApi")
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.also { controller ->
                controller.hide(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
            }
        } else {
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Sync fullscreen state with actual orientation
        when (newConfig.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                if (!isFullscreen) {
                    isFullscreen = true
                    hideSystemUI()
                    binding.ivFullScreen.setImageResource(R.drawable.ic_full_screen_exit)
                }
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                if (isFullscreen) {
                    isFullscreen = false
                    showSystemUI()
                    binding.ivFullScreen.setImageResource(R.drawable.ic_full_screen)
                }
            }
        }
    }

    @SuppressLint("InlinedApi")
    private fun showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.show(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
            }
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun exitFullscreen() {
        isFullscreen = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        if (isAutoRotateEnabled()) {
            // Let sensor take over once the rotation settles
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFullscreen) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }, 1000)
        }

        showSystemUI()
        binding.ivFullScreen.setImageResource(R.drawable.ic_full_screen)
    }

    private fun handleForwardSeek() {
        val currentPosition = fastPixPlayer.getCurrentPosition()
        val duration = fastPixPlayer.getDuration()

        // Seek forward 30 seconds, but don't exceed duration
        val targetPosition = if (duration > 0) {
            minOf(currentPosition + 10000, duration - 1000) // -1s to avoid seeking to end
        } else {
            currentPosition + 10000
        }

        Log.d(TAG, "Seeking from ${currentPosition / 1000}s to ${targetPosition / 1000}s")
        fastPixPlayer.seekTo(targetPosition)
        binding.sbProgress.progress = fastPixPlayer.getCurrentPosition().toInt()
        binding.tvStartTime.text =
            Utils.formatDurationSmart(fastPixPlayer.getCurrentPosition())
    }

    private fun handleBackwardSeek() {

        val currentPosition = fastPixPlayer.getCurrentPosition()
        val duration = fastPixPlayer.getDuration()

        // Seek forward 30 seconds, but don't exceed duration
        val targetPosition = if (duration > 0) {
            minOf(currentPosition - 10000, duration - 1000) // -1s to avoid seeking to end
        } else {
            currentPosition - 10000
        }

        Log.d(TAG, "Seeking from ${currentPosition / 1000}s to ${targetPosition / 1000}s")
        fastPixPlayer.seekTo(targetPosition)
        binding.sbProgress.progress = fastPixPlayer.getCurrentPosition().toInt()
        binding.tvStartTime.text =
            Utils.formatDurationSmart(fastPixPlayer.getCurrentPosition())
    }

    /**
     * Updates the volume icon based on mute state.
     *
     * @param isMuted true to show muted icon, false to show unmuted icon.
     */
    private fun updateVolumeIcon(isMuted: Boolean) {
        val iconRes = if (isMuted) {
            R.drawable.ic_volume_off
        } else {
            fastPixPlayer.unmute()
            R.drawable.ic_volume_on
        }
        binding.ivVolume.setImageResource(iconRes)
    }

    /**
     * Shows a popup menu with available playback speeds.
     *
     * @param anchorView The view to anchor the popup menu to.
     */
    private fun showPlaybackSpeedMenu(anchorView: View) {
        val popupMenu = PopupMenu(this, anchorView)
        val availableSpeeds = fastPixPlayer.getAvailablePlaybackSpeeds()
        val currentSpeed = fastPixPlayer.getPlaybackSpeed()

        // Add menu items for each playback speed
        availableSpeeds.forEachIndexed { index, speed ->
            val speedLabel = formatPlaybackSpeedLabel(speed)
            val menuItem = popupMenu.menu.add(0, index, 0, speedLabel)

            // Mark current speed with a checkmark
            if (kotlin.math.abs(speed - currentSpeed) < 0.01f) {
                menuItem.isChecked = true
            }
        }

        // Set menu to support checkable items
        popupMenu.menu.setGroupCheckable(0, true, true)

        // Handle menu item selection
        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            val selectedIndex = item.itemId
            if (selectedIndex in availableSpeeds.indices) {
                val selectedSpeed = availableSpeeds[selectedIndex]
                fastPixPlayer.setPlaybackSpeed(selectedSpeed)
            }
            true
        }

        popupMenu.show()
    }

    /**
     * Shows a popup menu with available audio tracks. Current track is checked.
     * Calls [FastPixPlayer.setAudioTrack] on selection.
     */
    private fun showAudioTrackMenu(anchorView: View) {
        val tracks = fastPixPlayer.getAudioTracks()
        val current = fastPixPlayer.getCurrentAudioTrack()
        if (tracks.isEmpty()) {
            Toast.makeText(this, "No audio tracks", Toast.LENGTH_SHORT).show()
            return
        }
        val popupMenu = PopupMenu(this, anchorView)
        tracks.forEachIndexed { index, track ->
            val label = track.label
                ?: track.languageCode
                ?: "Track ${index + 1}"
            popupMenu.menu.add(0, index, 0, label)
            if (track.id == current?.id) {
                popupMenu.menu.getItem(index).isChecked = true
            }
        }
        popupMenu.menu.setGroupCheckable(0, true, true)
        popupMenu.setOnMenuItemClickListener { item ->
            val index = item.itemId
            if (index in tracks.indices) {
                val track = tracks[index]
                fastPixPlayer.setAudioTrack(track.id)
            }
            true
        }
        popupMenu.show()
    }

    /**
     * Shows a popup menu with "Off" and available subtitle tracks. Current track is checked.
     * Calls [FastPixPlayer.setSubtitleTrack] or [FastPixPlayer.disableSubtitles] on selection.
     */
    private fun showSubtitleTrackMenu(anchorView: View) {
        val tracks = fastPixPlayer.getSubtitleTracks()
        val current = fastPixPlayer.getCurrentSubtitleTrack()
        val popupMenu = PopupMenu(this, anchorView)
        // Off option (itemId = -1)
        popupMenu.menu.add(0, -1, 0, "Off")
        if (current == null) {
            popupMenu.menu.getItem(0).isChecked = true
        }
        tracks.forEachIndexed { index, track ->
            val label = track.label
                ?: track.languageCode
                ?: "Track ${index + 1}"
            val forcedTag = if (track.isForced) " (forced)" else ""
            popupMenu.menu.add(0, index, index + 1, "$label$forcedTag")
            if (track.id == current?.id) {
                popupMenu.menu.getItem(index + 1).isChecked = true
            }
        }
        popupMenu.menu.setGroupCheckable(0, true, true)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                -1 -> fastPixPlayer.disableSubtitles()
                else -> if (item.itemId in tracks.indices) {
                    fastPixPlayer.setSubtitleTrack(tracks[item.itemId].id)
                }
            }
            true
        }
        popupMenu.show()
    }

    /**
     * Formats a playback speed value into a user-friendly label.
     *
     * @param speed The playback speed value (e.g., 1.0f, 1.5f, 2.0f).
     * @return A formatted string like "1.0x", "1.5x", "2.0x".
     */
    private fun formatPlaybackSpeedLabel(speed: Float): String {
        // Format to show one decimal place if needed, or integer if whole number
        return if (speed == speed.toInt().toFloat()) {
            "${speed.toInt()}x"
        } else {
            String.format("%.2fx", speed).trimEnd('0').trimEnd('.')
        }
    }

    override fun onResume() {
        super.onResume()
        // Only auto-resume playback if autoplay is enabled
        // If autoplay is false, respect the user's manual control
        if (fastPixPlayer.autoplay) {
            fastPixPlayer.play()
        }
    }

    override fun onPause() {
        super.onPause()
        fastPixPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoRotateObserver?.let { contentResolver.unregisterContentObserver(it) }
        fastPixPlayer.removePlaybackListener(playbackListener)
        fastPixPlayer.removeAudioTrackListener(audioTrackListener)
        fastPixPlayer.removeSubtitleTrackListener(subtitleTrackListener)
        if (isFinishing) {
            binding.playerView.release()
        }
    }

}
