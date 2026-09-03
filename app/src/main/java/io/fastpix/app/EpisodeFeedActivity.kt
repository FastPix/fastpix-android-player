package io.fastpix.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.fastpix.app.databinding.ActivityEpisodeFeedBinding
import io.fastpix.media3.buffer.BufferConfig
import io.fastpix.media3.cache.CacheConfig
import io.fastpix.media3.core.FastPixPlayer
import io.fastpix.media3.preload.PreloadConfig
import java.util.Locale

/**
 * An episode-style queue: **one** player holding every item, advanced with Prev/Next.
 *
 * This is the other feed shape, and the only one where [PreloadConfig] does anything. It maps onto
 * `ExoPlayer.setPreloadConfiguration`, which warms *the next item in the player's own playlist* — so
 * it needs media queued through [FastPixPlayer.setMediaItems]. The reel screen, which gives every
 * page its own player and calls `setMediaItem`, has no "next item" from the engine's point of view
 * and is served by `FastPixPreCacher` instead.
 *
 * **The disk cache is deliberately off here.** With it on, an item already warmed by the reel screen
 * would transition instantly whether or not preloading did anything, and the measurement would say
 * nothing about the feature under test. Buffering is pinned to [BufferConfig.FEED] in both states,
 * so preload is the only variable.
 *
 * The HUD reports milliseconds from the item transition to the first frame actually rendered. That
 * is measured through Media3's own `Player.Listener`, not the SDK's `PlaybackListener`, for two
 * reasons worth knowing:
 *
 * - `PlaybackListener.onPlayerReady` fires once per `setMediaItem(s)` call, not once per playlist
 *   transition, so items after the first would never report.
 * - There is no first-frame callback on `PlaybackListener` at all, and "ready" precedes pixels.
 */
@UnstableApi
class EpisodeFeedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEpisodeFeedBinding
    private var player: FastPixPlayer? = null

    private var preloadEnabled: Boolean = true
    private var transitionAtMs: Long = 0L
    private var awaitingFirstFrame: Boolean = false
    private var lastLine: String = "starting…"

    /** The DRM sample needs a playback token, so it is left out of the queue. */
    private val episodes: List<DummyData> by lazy {
        dummyData.filterNot { it.id.contains("DRM", ignoreCase = true) }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            transitionAtMs = System.currentTimeMillis()
            awaitingFirstFrame = true
            lastLine = "loading…"
            updateHud()
        }

        override fun onRenderedFirstFrame() {
            if (!awaitingFirstFrame) return
            awaitingFirstFrame = false
            val elapsed = System.currentTimeMillis() - transitionAtMs
            lastLine = "first frame in $elapsed ms"
            updateHud()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEpisodeFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preloadEnabled = intent.getBooleanExtra(EXTRA_PRELOAD, true)

        val fastPixPlayer = FastPixPlayer.Builder(this)
            .setAutoplay(true)
            .setBufferConfig(BufferConfig.FEED)
            .setCacheConfig(CacheConfig.DISABLED)
            .setPreloadConfig(if (preloadEnabled) PreloadConfig.FEED else PreloadConfig.DISABLED)
            .build()
        player = fastPixPlayer

        binding.episodePlayerView.player = fastPixPlayer
        fastPixPlayer.getExoPlayer().addListener(playerListener)

        // The queue is what PreloadConfig acts on — with a single media item it does nothing.
        fastPixPlayer.setMediaItems(
            episodes.map { episode ->
                MediaItem.Builder()
                    .setMediaId(episode.url)
                    .setUri(episode.url)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
            }
        )

        // FastPixPlayer exposes no playlist navigation, so stepping through the queue goes through
        // the underlying player.
        binding.btnPrevEpisode.setOnClickListener {
            fastPixPlayer.getExoPlayer().seekToPreviousMediaItem()
        }
        binding.btnNextEpisode.setOnClickListener {
            fastPixPlayer.getExoPlayer().seekToNextMediaItem()
        }
        binding.btnTogglePreload.text = if (preloadEnabled) "Preload: ON" else "Preload: OFF"
        binding.btnTogglePreload.setOnClickListener {
            // Preloading is fixed when the player is built, so flipping it rebuilds the screen.
            startActivity(newIntent(this, !preloadEnabled))
            finish()
        }

        updateHud()
    }

    private fun updateHud() {
        val exoPlayer = player?.getExoPlayer()
        val index = exoPlayer?.currentMediaItemIndex ?: 0
        val count = exoPlayer?.mediaItemCount ?: episodes.size
        binding.tvEpisodeHud.text = String.format(
            Locale.US,
            "PRELOAD %s · queue %d/%d · cache off\n%s",
            if (preloadEnabled) "ON  (5 s of the next item)" else "OFF",
            (index + 1).coerceAtMost(count),
            count,
            lastLine,
        )
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        player?.let { fastPixPlayer ->
            fastPixPlayer.getExoPlayer().removeListener(playerListener)
            binding.episodePlayerView.player = null
            fastPixPlayer.release()
        }
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PRELOAD = "extra_preload"

        fun newIntent(context: Context, preloadEnabled: Boolean = true): Intent =
            Intent(context, EpisodeFeedActivity::class.java)
                .putExtra(EXTRA_PRELOAD, preloadEnabled)
    }
}
