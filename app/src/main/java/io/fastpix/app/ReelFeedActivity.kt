package io.fastpix.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import io.fastpix.app.databinding.ActivityReelFeedBinding
import io.fastpix.app.databinding.ItemReelBinding
import io.fastpix.media3.PlaybackListener
import io.fastpix.media3.abr.AbrConfig
import io.fastpix.media3.buffer.BufferConfig
import io.fastpix.media3.cache.CacheConfig
import io.fastpix.media3.cache.FastPixPreCacher
import io.fastpix.media3.cache.MediaCacheProvider
import io.fastpix.media3.cache.PreCacheConfig
import io.fastpix.media3.cache.PreCacheListener
import io.fastpix.media3.core.FastPixPlayer
import java.util.Locale

/**
 * A vertical reel feed for measuring start-up latency with and without the 2.1.0 preload/cache path.
 *
 * The **Turbo** toggle at the bottom flips between two configurations and restarts the screen:
 *
 * | | Turbo OFF (pre-2.1.0) | Turbo ON |
 * |---|---|---|
 * | Buffering | `BufferConfig.MEDIA3_DEFAULT` | `BufferConfig.FEED` |
 * | Disk cache | none | `CacheConfig.forOnDemandFeed()` |
 * | Pre-caching | none | next two items warmed |
 *
 * The HUD at the top reports the number that matters: milliseconds from the page becoming current
 * to the player reporting ready. Swipe down the feed with Turbo off, then again with it on, and
 * compare. Note that the second pass over the *same* items is faster either way once they are in
 * the OS page cache, so compare fresh items or clear the cache between runs.
 *
 * Playback is deliberately started only when a page becomes current — never for the page ahead — so
 * the number isolates the SDK's pre-caching rather than app-level early preparation. A production
 * feed would do both.
 */
@UnstableApi
class ReelFeedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReelFeedBinding
    private lateinit var pool: ReelPlayerPool
    private lateinit var adapter: ReelAdapter

    /**
     * Streams excluded from the feed: the DRM sample needs a playback token, and 112a2222 is
     * dropped at request.
     */
    private val reels: List<DummyData> by lazy {
        dummyData.filterNot { item ->
            item.id.contains("DRM", ignoreCase = true) || item.url.contains(EXCLUDED_PLAYBACK_ID)
        }
    }

    private var turboEnabled: Boolean = true
    private var cacheConfig: CacheConfig = CacheConfig.DISABLED
    private var preCacher: FastPixPreCacher? = null

    private var currentPosition: Int = RecyclerView.NO_POSITION
    private var selectedAtMs: Long = 0L
    private var lastStartLine: String = "swipe to measure"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReelFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        turboEnabled = intent.getBooleanExtra(EXTRA_TURBO, true)
        setUpPlaybackStack()

        adapter = ReelAdapter()
        binding.reelPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.reelPager.adapter = adapter
        binding.reelPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = onPageBecameCurrent(position)
        })

        binding.btnToggleTurbo.text = if (turboEnabled) "Turbo: ON" else "Turbo: OFF"
        binding.btnToggleTurbo.setOnClickListener {
            // The cache and the MediaSource.Factory are fixed at player construction, so the
            // cleanest way to A/B them is to rebuild the screen.
            startActivity(newIntent(this, !turboEnabled))
            finish()
        }
        binding.btnClearCache.setOnClickListener {
            val cleared = MediaCacheProvider.clear()
            Toast.makeText(
                this,
                if (cleared) "Media cache cleared" else "No cache open",
                Toast.LENGTH_SHORT,
            ).show()
            updateHud()
        }

        updateHud()
    }

    /**
     * Builds the two configurations being compared. With Turbo off this is exactly what the SDK did
     * before 2.1.0: stock Media3 buffering, no cache, no warming.
     */
    private fun setUpPlaybackStack() {
        cacheConfig = if (turboEnabled) CacheConfig.forOnDemandFeed() else CacheConfig.DISABLED

        preCacher = if (turboEnabled) {
            FastPixPreCacher.create(
                this,
                cacheConfig,
                PreCacheConfig(
                    segmentCount = 2,
                    maxBytesPerItem = 2L * 1024L * 1024L,
                    // Must agree with the ABR cap below, or the warmed rendition is not the one
                    // playback asks for and the whole warm is wasted.
                    targetBitrateBps = FEED_BITRATE_CAP_BPS,
                    maxParallelItems = 2,
                    enableLogging = true,
                ),
            )?.apply {
                setListener(object : PreCacheListener {
                    override fun onPreCached(url: String, bytesWritten: Long) {
                        runOnUiThread { updateHud() }
                    }
                })
            }
        } else {
            null
        }

        val bufferConfig = if (turboEnabled) BufferConfig.FEED else BufferConfig.MEDIA3_DEFAULT

        // Pinning the ladder is what makes pre-caching pay off: ABR picking 1080p on Wi-Fi while
        // the pre-cacher warmed 480p would be a guaranteed cache miss. A phone-sized reel does not
        // need more than this anyway.
        val abrConfig = AbrConfig(
            wifiMaxBitrateBps = FEED_BITRATE_CAP_BPS,
            cellular5g4gMaxBitrateBps = FEED_BITRATE_CAP_BPS,
        )

        pool = ReelPlayerPool(maxPlayers = PLAYER_POOL_SIZE) {
            FastPixPlayer.Builder(this)
                .setAutoplay(true)
                .setLoop(true)
                .setBufferConfig(bufferConfig)
                .setCacheConfig(cacheConfig)
                .setAbrConfig(abrConfig)
                .build()
        }
    }

    private fun onPageBecameCurrent(position: Int) {
        if (position == currentPosition) return
        currentPosition = position
        selectedAtMs = System.currentTimeMillis()

        // Only the current page holds a player; everything else gives its player back.
        adapter.detachPlayersExcept(position)
        pool.pauseAllExcept(position)
        startPlayback(position)
        warmUpcoming(position)
        updateHud()
    }

    /** Hands the next few URLs to the pre-cacher; it cancels anything that fell out of the window. */
    private fun warmUpcoming(position: Int) {
        val upcoming = (position + 1..position + PRECACHE_AHEAD)
            .mapNotNull { reels.getOrNull(it)?.url }
        preCacher?.preCache(upcoming)
    }

    private fun startPlayback(position: Int) {
        val holder = adapter.holderAt(position) ?: return // arrives via onViewAttachedToWindow
        val item = reels.getOrNull(position) ?: return
        val player = pool.acquire(position, position)
        holder.bindPlayer(player, item, position)
    }

    /**
     * Called by a page once its player reports ready, with the time since the page became current.
     */
    private fun onStartMeasured(position: Int, elapsedMs: Long, resumed: Boolean) {
        val warm = preCacher?.isWarm(reels[position].url) == true
        lastStartLine = buildString {
            append("#").append(position)
            if (resumed) {
                append(" resumed (player still held this item)")
            } else {
                append(" start ").append(elapsedMs).append(" ms")
                append(if (warm) "  pre-cached" else "  cold")
            }
        }
        updateHud()
    }

    private fun updateHud() {
        val cachedMb = MediaCacheProvider.cachedBytes() / (1024.0 * 1024.0)
        binding.tvHud.text = String.format(
            Locale.US,
            "TURBO %s · cache %.1f MB · players %d\n%s",
            if (turboEnabled) "ON  (FEED buffer + disk cache + pre-cache)"
            else "OFF (Media3 defaults, no cache)",
            cachedMb,
            pool.size(),
            lastStartLine,
        )
    }

    override fun onPause() {
        super.onPause()
        pool.playerAt(currentPosition)?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (currentPosition != RecyclerView.NO_POSITION) {
            pool.playerAt(currentPosition)?.play()
        }
    }

    override fun onDestroy() {
        preCacher?.release()
        pool.release()
        super.onDestroy()
    }

    /** One page per reel. Only the current page ever holds a player. */
    private inner class ReelAdapter : RecyclerView.Adapter<ReelViewHolder>() {

        private val attached = LinkedHashMap<Int, ReelViewHolder>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReelViewHolder {
            val itemBinding = ItemReelBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
            // PlayerView keys its config-change retention on the view id, and every page inflates
            // the same layout. Without a unique id per holder they would overwrite each other in
            // the SDK's player store.
            itemBinding.reelPlayerView.id = View.generateViewId()
            return ReelViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ReelViewHolder, position: Int) {
            // Deliberately no player here: preparing the neighbouring page would hide the very
            // latency this screen exists to measure.
            holder.showLabel(reels[position], position)
        }

        override fun getItemCount(): Int = reels.size

        override fun onViewAttachedToWindow(holder: ReelViewHolder) {
            super.onViewAttachedToWindow(holder)
            val position = holder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return
            attached[position] = holder
            // The first page is selected before its view exists, so start it on arrival instead.
            if (position == currentPosition) {
                startPlayback(position)
            } else if (currentPosition == RecyclerView.NO_POSITION && position == 0) {
                onPageBecameCurrent(0)
            }
        }

        override fun onViewDetachedFromWindow(holder: ReelViewHolder) {
            super.onViewDetachedFromWindow(holder)
            val entry = attached.entries.firstOrNull { it.value === holder }
            if (entry != null) attached.remove(entry.key)
            holder.detachPlayer()
        }

        fun holderAt(position: Int): ReelViewHolder? = attached[position]

        fun detachPlayersExcept(position: Int) {
            for ((pos, holder) in attached) {
                if (pos != position) holder.detachPlayer()
            }
        }
    }

    private inner class ReelViewHolder(
        private val itemBinding: ItemReelBinding,
    ) : RecyclerView.ViewHolder(itemBinding.root) {

        private var player: FastPixPlayer? = null
        private var position: Int = RecyclerView.NO_POSITION
        private var measured = false

        private val playbackListener = object : PlaybackListener {
            override fun onPlay() = Unit
            override fun onPause() = Unit
            override fun onPlaybackStateChanged(isPlaying: Boolean) = Unit

            override fun onError(error: PlaybackException) {
                itemBinding.tvReelLabel.text = "Error: ${error.errorCodeName}"
            }

            override fun onPlayerReady(durationMs: Long) {
                if (measured) return
                measured = true
                onStartMeasured(position, System.currentTimeMillis() - selectedAtMs, resumed = false)
            }
        }

        fun showLabel(item: DummyData, position: Int) {
            this.position = position
            itemBinding.tvReelLabel.text = "#$position  ${item.id}"
        }

        fun bindPlayer(player: FastPixPlayer, item: DummyData, position: Int) {
            if (this.player === player && this.position == position) {
                player.play()
                return
            }
            detachPlayer()

            this.player = player
            this.position = position
            this.measured = false
            itemBinding.tvReelLabel.text = "#$position  ${item.id}"

            itemBinding.reelPlayerView.player = player
            player.addPlaybackListener(playbackListener)

            val alreadyLoaded = player.getCurrentPlaybackUrl() == item.url
            player.setMediaItem(
                MediaItem.Builder()
                    .setMediaId(item.url)
                    .setUri(item.url)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
            )
            player.play()

            // setMediaItem is a no-op when the pooled player still holds this exact item, so its
            // ready callback will not fire again — report the resume instead of a bogus timing.
            if (alreadyLoaded && player.getPlaybackState() == Player.STATE_READY) {
                measured = true
                onStartMeasured(position, 0L, resumed = true)
            }
        }

        fun detachPlayer() {
            val current = player ?: return
            current.removePlaybackListener(playbackListener)
            current.pause()
            itemBinding.reelPlayerView.player = null
            player = null
        }
    }

    companion object {
        private const val EXTRA_TURBO = "extra_turbo"

        /** Three players: the current page plus room to rotate without a fresh codec each swipe. */
        private const val PLAYER_POOL_SIZE = 3

        /** How many upcoming items to warm. */
        private const val PRECACHE_AHEAD = 2

        /** Playback ID kept out of the feed. */
        private const val EXCLUDED_PLAYBACK_ID = "112a2222-0f31-44a0-bcf6-30cfa6e1d17d"


        /**
         * Ceiling for both the ABR cap and the pre-cacher's rendition choice, so the two agree.
         * The FastPix sample ladder runs 4.4M / 2.75M / 1.65M / 0.55M, so this settles on 1.65M
         * (854x480) — plenty for a portrait reel.
         */
        private const val FEED_BITRATE_CAP_BPS = 1_650_000

        fun newIntent(context: Context, turboEnabled: Boolean = true): Intent =
            Intent(context, ReelFeedActivity::class.java)
                .putExtra(EXTRA_TURBO, turboEnabled)
    }
}
