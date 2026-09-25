package io.fastpix.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.fastpix.app.databinding.ActivityEpisodeFeedBinding
import io.fastpix.media3.buffer.BufferConfig
import io.fastpix.media3.cache.CacheConfig
import io.fastpix.media3.cache.MediaCacheProvider
import io.fastpix.media3.core.FastPixPlayer
import io.fastpix.media3.playlist.PlaylistItem
import io.fastpix.media3.playlist.PlaylistItemChangeReason
import io.fastpix.media3.playlist.PlaylistListener
import io.fastpix.media3.preload.PreloadConfig
import io.fastpix.media3.prerender.PrerenderConfig
import java.util.Locale

/**
 * An episode-style queue: **one** player holding every item through [FastPixPlayer.setPlaylist],
 * stepped with [FastPixPlayer.next] / [FastPixPlayer.previous].
 *
 * The Preload button toggles `PreloadConfig(count = 3, behind = 1)`. **The disk cache is off by
 * default here**, so preloading is the only variable: with it on, an item already cached by another
 * screen would transition instantly whether or not preloading did anything. Scripted runs can turn
 * it on with the `cache` extra to check that revisited items are read back from disk. Buffering is
 * pinned to [BufferConfig.FEED] in every state.
 *
 * The HUD reports milliseconds from the playlist moving to an item
 * ([PlaylistListener.onPlaylistItemChanged]) to that item's first frame actually rendered, read from
 * Media3's `Player.Listener` because `PlaybackListener` has no first-frame callback. Each transition
 * is also logged under [TAG] for scripted runs.
 */
@UnstableApi
class EpisodeFeedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEpisodeFeedBinding
    private var player: FastPixPlayer? = null

    private var preloadEnabled: Boolean = true
    private var cacheEnabled: Boolean = false
    private var prerenderEnabled: Boolean = false
    private var transitionAtMs: Long = 0L
    private var awaitingFirstFrame: Boolean = false
    private var lastLine: String = "starting…"

    /** The DRM sample needs a playback token, so it is left out of the queue. */
    private val episodes: List<DummyData> by lazy {
        dummyData.filterNot { it.id.contains("DRM", ignoreCase = true) }
    }

    private var transitionIndex: Int = -1

    private val playlistListener = object : PlaylistListener {
        override fun onPlaylistItemChanged(
            index: Int,
            item: PlaylistItem,
            reason: PlaylistItemChangeReason,
        ) {
            transitionAtMs = System.currentTimeMillis()
            transitionIndex = index
            awaitingFirstFrame = true
            lastLine = "loading…"
            updateHud()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            if (!awaitingFirstFrame) return
            awaitingFirstFrame = false
            val elapsed = System.currentTimeMillis() - transitionAtMs
            lastLine = "first frame in $elapsed ms"
            val cachedKb = MediaCacheProvider.cachedBytes() / 1024
            Log.i(
                TAG,
                "firstFrame index=$transitionIndex ms=$elapsed preload=$preloadEnabled " +
                        "cache=$cacheEnabled prerender=$prerenderEnabled cachedKb=$cachedKb",
            )
            updateHud()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEpisodeFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preloadEnabled = intent.getBooleanExtra(EXTRA_PRELOAD, true)
        cacheEnabled = intent.getBooleanExtra(EXTRA_CACHE, false)
        prerenderEnabled = intent.getBooleanExtra(EXTRA_PRERENDER, false)
        if (intent.getBooleanExtra(EXTRA_CLEAR_CACHE, false)) {
            MediaCacheProvider.getOrCreate(this, CacheConfig.enabled())
            MediaCacheProvider.clear()
        }

        val fastPixPlayer = FastPixPlayer.Builder(this)
            .setAutoplay(true)
            .setBufferConfig(BufferConfig.FEED)
            .setCacheConfig(if (cacheEnabled) CacheConfig.enabled() else CacheConfig.DISABLED)
            .setPreloadConfig(
                if (preloadEnabled) PreloadConfig(count = 3, behind = 1) else PreloadConfig.DISABLED
            )
            // Moving to a pre-rendered episode shows its first frame at once, not a black frame.
            .setPrerenderConfig(
                if (prerenderEnabled) PrerenderConfig(count = 1, behind = 1)
                else PrerenderConfig.DISABLED
            )
            .build()
        player = fastPixPlayer

        binding.episodePlayerView.player = fastPixPlayer
        fastPixPlayer.getExoPlayer().addListener(playerListener)
        fastPixPlayer.addPlaylistListener(playlistListener)

        fastPixPlayer.setPlaylist(episodes.map { PlaylistItem.fromUrl(it.url, id = it.id) })

        binding.btnPrevEpisode.setOnClickListener { fastPixPlayer.previous() }
        binding.btnNextEpisode.setOnClickListener { fastPixPlayer.next() }
        binding.btnTogglePreload.text = if (preloadEnabled) "Preload: ON" else "Preload: OFF"
        binding.btnTogglePreload.setOnClickListener {
            // Preloading is fixed when the player is built, so flipping it rebuilds the screen.
            startActivity(newIntent(this, !preloadEnabled, cacheEnabled))
            finish()
        }

        updateHud()
    }

    private fun updateHud() {
        val index = player?.getCurrentIndex()?.coerceAtLeast(0) ?: 0
        val count = player?.getPlaylist()?.size ?: episodes.size
        binding.tvEpisodeHud.text = String.format(
            Locale.US,
            "PRELOAD %s · queue %d/%d · cache %s · prerender %s\n%s",
            if (preloadEnabled) "ON  (3 ahead, 1 behind)" else "OFF",
            (index + 1).coerceAtMost(count),
            count,
            if (cacheEnabled) "on" else "off",
            if (prerenderEnabled) "on" else "off",
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
            fastPixPlayer.removePlaylistListener(playlistListener)
            binding.episodePlayerView.player = null
            fastPixPlayer.release()
        }
        player = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "EpisodeFeed"
        private const val EXTRA_PRELOAD = "extra_preload"
        private const val EXTRA_CACHE = "cache"
        private const val EXTRA_PRERENDER = "prerender"
        private const val EXTRA_CLEAR_CACHE = "clearCache"

        fun newIntent(
            context: Context,
            preloadEnabled: Boolean = true,
            cacheEnabled: Boolean = false,
        ): Intent =
            Intent(context, EpisodeFeedActivity::class.java)
                .putExtra(EXTRA_PRELOAD, preloadEnabled)
                .putExtra(EXTRA_CACHE, cacheEnabled)
    }
}
