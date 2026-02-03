package io.fastpix.app

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
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
import io.fastpix.media3.PlaybackListener

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
            binding.tvEndTime.text = Utils.formatDurationSmart(binding.playerView.getDuration())
            binding.sbProgress.progress = currentPositionMs.toInt()
            binding.sbProgress.secondaryProgress = bufferedPositionMs.toInt()

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        videoModel = intent.getParcelableExtra("video_model", DummyData::class.java)
        setupPlayerView()
        setupControls()
    }

    /**
     * Setup PlayerView with media source and listeners.
     * This demonstrates the core PlayerView API.
     */
    private fun setupPlayerView() {
        binding.loader.isVisible = false
        binding.playerView.addPlaybackListener(playbackListener)
        binding.playerView.retainPlayerOnConfigChange = true
        binding.playerView.isTapGestureEnabled = false
        val mediaItem = MediaItem.fromUri(
            videoModel?.url.orEmpty()
        )
        binding.playerView.setMediaItem(mediaItem)
        binding.playerView.setPlayWhenReady(true)

    }

    private fun togglePlayPause() {
        if (binding.playerView.isPlaying()) {
            binding.playerView.pause()
            binding.ivPlayPause.setImageDrawable(
                ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_play,
                    null
                )
            )
        } else {
            binding.playerView.play()
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
        binding.ivPlayPause.setOnClickListener {
            togglePlayPause()
        }


        binding.ivBackwardSeek.setOnClickListener {
            handleBackwardSeek()
        }

        binding.ivForwardSeek.setOnClickListener {
            handleForwardSeek()
        }

        binding.ivFullScreen.setOnClickListener {
            if (isFullscreen) {
                exitFullscreen()
            } else {
                enterFullscreen()
            }
        }

        binding.sbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Don't seek during dragging, only update UI
                if (fromUser) {
                    val duration = binding.playerView.getDuration()
                    if (duration != C.TIME_UNSET) {
                        binding.tvStartTime.text = Utils.formatDurationSmart(progress.toLong())
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // User started dragging
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Seek only when user releases the seekbar
                val progress = seekBar?.progress ?: 0
                val duration = binding.playerView.getDuration()
                if (duration != C.TIME_UNSET && progress >= 0) {
                    val position = progress.toLong().coerceIn(0, duration)
                    Log.d(TAG, "Seeking to position: ${position / 1000}s")
                    binding.playerView.seekTo(position)
                }
            }
        })
    }

    private var isFullscreen = false
    private var userTriggeredFullscreen = false


    private fun enterFullscreen() {
        isFullscreen = true
        userTriggeredFullscreen = true
        // Lock to landscape orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // After a delay, allow sensor-based rotation and clear the flag
        Handler(Looper.getMainLooper()).postDelayed({
            if (isFullscreen) {  // Only allow sensor if still in fullscreen
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }
            userTriggeredFullscreen = false
        }, 5000)
        hideSystemUI()
        binding.ivFullScreen.setImageResource(R.drawable.ic_full_screen_exit)
    }

    @SuppressLint("InlinedApi")
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
            }
        } else {
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Handle orientation change and sync fullscreen state
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
        userTriggeredFullscreen = true
        // Lock to portrait orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // After a delay, allow sensor-based rotation and clear the flag
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFullscreen) {  // Only allow sensor if still not in fullscreen
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }
            userTriggeredFullscreen = false
        }, 5000)
        showSystemUI()
        binding.ivFullScreen.setImageResource(R.drawable.ic_full_screen)
    }

    private fun handleForwardSeek() {
        val currentPosition = binding.playerView.getCurrentPosition()
        val duration = binding.playerView.getDuration()

        // Seek forward 30 seconds, but don't exceed duration
        val targetPosition = if (duration > 0) {
            minOf(currentPosition + 10000, duration - 1000) // -1s to avoid seeking to end
        } else {
            currentPosition + 10000
        }

        Log.d(TAG, "Seeking from ${currentPosition / 1000}s to ${targetPosition / 1000}s")
        binding.playerView.seekTo(targetPosition)
        binding.sbProgress.progress = binding.playerView.getCurrentPosition().toInt()
        binding.tvStartTime.text = Utils.formatDurationSmart(binding.playerView.getCurrentPosition())
    }

    private fun handleBackwardSeek() {

        val currentPosition = binding.playerView.getCurrentPosition()
        val duration = binding.playerView.getDuration()

        // Seek forward 30 seconds, but don't exceed duration
        val targetPosition = if (duration > 0) {
            minOf(currentPosition - 10000, duration - 1000) // -1s to avoid seeking to end
        } else {
            currentPosition - 10000
        }

        Log.d(TAG, "Seeking from ${currentPosition / 1000}s to ${targetPosition / 1000}s")
        binding.playerView.seekTo(targetPosition)
        binding.sbProgress.progress = binding.playerView.getCurrentPosition().toInt()
        binding.tvStartTime.text = Utils.formatDurationSmart(binding.playerView.getCurrentPosition())
    }


    override fun onResume() {
        super.onResume()
        binding.playerView.play()
    }

    override fun onPause() {
        super.onPause()
        binding.playerView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.playerView.removePlaybackListener(playbackListener)
        if (isFinishing) {
            binding.playerView.release()
        }
    }
}
