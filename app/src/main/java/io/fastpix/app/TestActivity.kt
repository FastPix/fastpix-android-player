package io.fastpix.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.fastpix.app.databinding.ActivityTestBinding

@UnstableApi
class TestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestBinding
    private var player: ExoPlayer? = null
    private var playWhenReady = true
    private var videoModel: DummyData? = null

    // Startup timing (all SystemClock.elapsedRealtime() millis)
    private var activityCreatedAt = 0L
    private var prepareStartedAt = 0L
    private var readyAt = 0L
    private var firstFrameAt = 0L
    private var cacheStateAtOpen = "off"

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityCreatedAt = SystemClock.elapsedRealtime()
        enableEdgeToEdge()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_test)
        playWhenReady = intent.getBooleanExtra(VideoListScreen.AUTO_PLAY, true)
        videoModel = intent.getParcelableExtra(VideoListScreen.VIDEO_MODEL, DummyData::class.java)
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }


    private fun initializePlayer() {
        if (videoModel?.url?.isBlank() == true) {
            Toast.makeText(this, "No video URL provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        readyAt = 0L
        firstFrameAt = 0L
        binding.tvStartupStats.text = "Loading…"

        val url = videoModel?.url.orEmpty()
        cacheStateAtOpen = when (TestPreCacher.state(url)) {
            TestPreCacher.State.WARM -> "warm (${TestPreCacher.warmedBytes(url) / 1024} KB)"
            TestPreCacher.State.WARMING -> "still warming"
            TestPreCacher.State.FAILED -> "warm failed"
            null -> "not pre-cached"
        }

        // Reads go through the pre-cache: cached bytes come from disk, the rest from network.
        val mediaSourceFactory = DefaultMediaSourceFactory(TestPreCacher.dataSourceFactory(this))

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().also { exoPlayer ->
            binding.playerView.player = exoPlayer
            exoPlayer.addListener(startupListener)
            prepareStartedAt = SystemClock.elapsedRealtime()
            exoPlayer.setMediaItem(MediaItem.fromUri(videoModel?.url!!))
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    private val startupListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && readyAt == 0L) {
                readyAt = SystemClock.elapsedRealtime()
                updateStartupStats()
            }
        }

        override fun onRenderedFirstFrame() {
            if (firstFrameAt == 0L) {
                firstFrameAt = SystemClock.elapsedRealtime()
                updateStartupStats()
            }
        }
    }

    private fun updateStartupStats() {
        val ready = if (readyAt > 0) "${readyAt - prepareStartedAt} ms" else "…"
        val firstFrame = if (firstFrameAt > 0) "${firstFrameAt - prepareStartedAt} ms" else "…"
        val fromOpen = if (firstFrameAt > 0) "${firstFrameAt - activityCreatedAt} ms" else "…"
        val stats = "Ready:        $ready\n" +
            "First frame:  $firstFrame\n" +
            "Open → frame: $fromOpen\n" +
            "Cache:        $cacheStateAtOpen"
        binding.tvStartupStats.text = stats
        if (firstFrameAt > 0) {
            Log.d(TAG, "url=${videoModel?.url} ready=$ready firstFrame=$firstFrame openToFrame=$fromOpen cache=$cacheStateAtOpen")
        }
    }

    private fun releasePlayer() {
        player?.let {
            playWhenReady = it.playWhenReady
            it.removeListener(startupListener)
            it.release()
        }
        player = null
        binding.playerView.player = null
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        private const val TAG = "TestActivityStartup"
        private const val KEY_PLAY_WHEN_READY = "play_when_ready"
        private const val KEY_POSITION = "playback_position"

        fun start(context: Context, url: String) {
            context.startActivity(
                Intent(context, TestActivity::class.java).putExtra(EXTRA_URL, url)
            )
        }
    }
}
