package io.fastpix.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import io.fastpix.media3.PlaybackListener
import io.fastpix.media3.PlayerView
import io.fastpix.media3.ResizeMode
import io.fastpix.media3.abr.AbrConfig
import io.fastpix.media3.buffer.BufferConfig
import io.fastpix.media3.cache.CacheConfig
import io.fastpix.media3.cache.FastPixPreCacher
import io.fastpix.media3.cache.MediaCacheProvider
import io.fastpix.media3.cache.PreCacheConfig
import io.fastpix.media3.core.FastPixPlayer
import io.fastpix.media3.playlist.FastPixPlayerPool
import io.fastpix.media3.playlist.PlaylistItem
import io.fastpix.media3.preload.PreloadConfig
import io.fastpix.media3.prerender.PrerenderConfig
import io.fastpix.media3.tracks.VideoTrack
import java.util.Locale

/**
 * A Compose reel feed that reproduces the integration shapes a client app is likely to use, so a
 * "buffers on every swipe" report can be narrowed down without the client's code.
 *
 * Every knob is an intent extra (see [newIntent]) so runs can be scripted over adb, and every swipe
 * writes one parseable line to logcat under [TAG]:
 *
 * ```
 * swipe page=3 startMs=412 warm=true height=480 bitrate=1650000 rebuffers=0 ...
 * ```
 *
 * Player shapes ([PlayerMode]):
 * - [PlayerMode.SHARED] — one app-wide player handed to whichever page is current, the page's
 *   `AndroidView` clearing it when the page stops being current.
 * - [PlayerMode.SHARED_NAIVE] — the same shared player, but every page's view keeps it once set.
 * - [PlayerMode.SHARED_WORKAROUND] — [PlayerMode.SHARED] plus a view id and `onRelease`, which
 *   keeps the SDK's `PlayerView` from releasing the shared player when a page leaves the screen.
 * - [PlayerMode.PER_PAGE] — each page builds its own player and releases it on dispose.
 * - [PlayerMode.PRERENDER_NEXT] — [PlayerMode.PER_PAGE], but every composed page prepares its item
 *   paused as soon as it exists, and the neighbouring pages are kept composed
 *   (`offscreenPageLimit = 1` / `beyondViewportPageCount = 1`). The next page's first frame is then
 *   already on its surface when the user arrives, so the swipe reveals video instead of black.
 *   Costs one extra decoder per kept neighbour.
 * - [PlayerMode.POOL] — the SDK's [FastPixPlayerPool] doing what [PlayerMode.PRERENDER_NEXT] does
 *   by hand: `PrerenderConfig(count = 1, behind = 1)` plus `PreloadConfig(count = 3, behind = 1)`,
 *   with the decoder budget deciding how many neighbours actually pre-render.
 *
 * Hosts ([Host]):
 * - [Host.COMPOSE_PAGER] — a Compose `VerticalPager`; playback starts on `settledPage`.
 * - [Host.VIEWPAGER2] — a View-based vertical `ViewPager2` whose pages are each a `ComposeView`
 *   running the same page composable; playback starts on `onPageSelected`. RecyclerView re-attaches
 *   cached pages without rebinding them, so a page's composition can outlive a detach.
 */
@UnstableApi
class ComposeReelFeedActivity : ComponentActivity() {

    enum class PlayerMode { SHARED, SHARED_NAIVE, SHARED_WORKAROUND, PER_PAGE, PRERENDER_NEXT, POOL }
    private val PlayerMode.isShared: Boolean
        get() = this == PlayerMode.SHARED || this == PlayerMode.SHARED_NAIVE ||
                this == PlayerMode.SHARED_WORKAROUND

    enum class CacheMode { OFF, ENABLED, ON_DEMAND_FEED }
    enum class Host { COMPOSE_PAGER, VIEWPAGER2 }

    private lateinit var host: Host
    private lateinit var mode: PlayerMode
    private lateinit var cacheMode: CacheMode
    private var preCacheOn = true
    private var capMatched = true

    private lateinit var cacheConfig: CacheConfig
    private lateinit var abrConfig: AbrConfig
    private var preCacher: FastPixPreCacher? = null
    private var sharedPlayer: FastPixPlayer? = null
    private val pagePlayers = mutableMapOf<Int, FastPixPlayer>()
    private var pool: FastPixPlayerPool? = null

    /** Players whose first frames are being timed, so a pool player is hooked up only once. */
    private val trackedPlayers = HashSet<FastPixPlayer>()

    /** When each player last presented a first frame for its current item; cleared on item change. */
    private val firstFrameAt = mutableMapOf<FastPixPlayer, Long>()

    private val reels: List<DummyData> by lazy {
        dummyData.filterNot { item ->
            item.id.contains("DRM", ignoreCase = true) ||
                    item.url.contains(ReelFeedActivity.EXCLUDED_PLAYBACK_ID)
        }
    }

    /** Measurement for the page the user is on; replaced on every settle. */
    private var current: SwipeMeasurement? = null
    private val hudLines = mutableStateListOf<String>()
    private val cacheMb = mutableStateOf(0.0)

    /** The selected page when hosted in [ViewPager2]; the Compose pager tracks its own. */
    private val selectedPage = mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = Host.valueOf(intent.getStringExtra(EXTRA_HOST) ?: Host.COMPOSE_PAGER.name)
        mode = PlayerMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: PlayerMode.SHARED.name)
        cacheMode = CacheMode.valueOf(
            intent.getStringExtra(EXTRA_CACHE) ?: CacheMode.ON_DEMAND_FEED.name
        )
        preCacheOn = intent.getBooleanExtra(EXTRA_PRECACHE, true)
        capMatched = intent.getBooleanExtra(EXTRA_CAP_MATCHED, true)

        if (intent.getBooleanExtra(EXTRA_CLEAR_CACHE, false)) {
            // Opens the cache if needed so a clear is possible on a cold process.
            MediaCacheProvider.getOrCreate(this, CacheConfig.enabled())
            MediaCacheProvider.clear()
        }

        cacheConfig = when (cacheMode) {
            CacheMode.OFF -> CacheConfig.DISABLED
            CacheMode.ENABLED -> CacheConfig.enabled()
            CacheMode.ON_DEMAND_FEED -> CacheConfig.forOnDemandFeed()
        }
        // Uncapped Wi-Fi with the pre-cacher's default target is what the README's main feed
        // sample produces; "matched" pins both to the same ceiling.
        abrConfig = if (capMatched) {
            AbrConfig(wifiMaxBitrateBps = CAP_BPS, cellular5g4gMaxBitrateBps = CAP_BPS)
        } else {
            AbrConfig.DEFAULT
        }
        if (preCacheOn) {
            preCacher = FastPixPreCacher.create(
                this,
                cacheConfig,
                if (capMatched) PreCacheConfig(targetBitrateBps = CAP_BPS) else PreCacheConfig.DEFAULT,
            )
        }
        if (mode.isShared) sharedPlayer = buildPlayer()
        if (mode == PlayerMode.POOL) {
            val playerAbr = abrConfig
            pool = FastPixPlayerPool.Builder(this)
                .setPlayerConfig {
                    it.setLoop(true)
                        .setBufferConfig(BufferConfig.FEED)
                        .setAbrConfig(playerAbr)
                }
                .setPreloadConfig(PreloadConfig(count = 3, behind = 1))
                .setCacheConfig(
                    if (cacheMode == CacheMode.OFF) CacheConfig.DISABLED else CacheConfig.enabled()
                )
                .setPrerenderConfig(PrerenderConfig(count = 1, behind = 1))
                .build()
                .apply { setPlaylist(reels.map { PlaylistItem.fromUrl(it.url, id = it.id) }) }
        }

        log(
            "config host=$host mode=$mode cache=$cacheMode precache=${preCacher != null} " +
                    "capMatched=$capMatched"
        )

        when (host) {
            Host.COMPOSE_PAGER -> setContent { MaterialTheme { FeedScreen() } }
            Host.VIEWPAGER2 -> setUpViewPagerHost()
        }
    }

    private fun setUpViewPagerHost() {
        val pager = ViewPager2(this).apply {
            orientation = ViewPager2.ORIENTATION_VERTICAL
            if (mode == PlayerMode.PRERENDER_NEXT || mode == PlayerMode.POOL) {
                offscreenPageLimit = 1
            }
            adapter = ComposePageAdapter()
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    selectedPage.intValue = position
                    onPageSettled(position)
                }
            })
        }
        // Two wrap-height overlays rather than one full-screen one, so the middle of the screen
        // belongs to the pager's swipe.
        val hud = ComposeView(this).apply {
            setContent { MaterialTheme { Hud(Modifier) } }
        }
        val controls = ComposeView(this).apply {
            setContent { MaterialTheme { Controls(Modifier) } }
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(pager, MATCH, MATCH)
            addView(hud, FrameLayout.LayoutParams(MATCH, WRAP, android.view.Gravity.TOP))
            addView(controls, FrameLayout.LayoutParams(MATCH, WRAP, android.view.Gravity.BOTTOM))
        }
        setContentView(root)
    }

    /** One `ComposeView` per page, left on the default composition strategy as a client would. */
    private inner class ComposePageAdapter : RecyclerView.Adapter<ComposePageHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComposePageHolder =
            ComposePageHolder(
                ComposeView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(MATCH, MATCH)
                }
            )

        override fun onBindViewHolder(holder: ComposePageHolder, position: Int) {
            holder.composeView.setContent {
                MaterialTheme {
                    key(position) { Page(position, selectedPage.intValue == position) }
                }
            }
        }

        override fun getItemCount(): Int = reels.size
    }

    private class ComposePageHolder(val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView)

    private fun buildPlayer(autoplay: Boolean = true): FastPixPlayer {
        val player = FastPixPlayer.Builder(this)
            .setAutoplay(autoplay)
            .setLoop(true)
            .setBufferConfig(BufferConfig.FEED)
            .setCacheConfig(cacheConfig)
            .setAbrConfig(abrConfig)
            .build()
        trackFirstFrames(player)
        return player
    }

    /** Records when [player] presents a first frame, forgetting it when the player changes item. */
    private fun trackFirstFrames(player: FastPixPlayer) {
        if (!trackedPlayers.add(player)) return
        player.getExoPlayer().addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameAt[player] = SystemClock.elapsedRealtime()
                current?.onFirstFrame(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                firstFrameAt.remove(player)
            }
        })
    }

    private fun mediaItemFor(page: Int): MediaItem {
        val url = reels[page].url
        return MediaItem.Builder()
            .setMediaId(url)
            .setUri(url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
    }

    @Composable
    private fun FeedScreen() {
        val pagerState = rememberPagerState { reels.size }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { onPageSettled(it) }
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount =
                    if (mode == PlayerMode.PRERENDER_NEXT || mode == PlayerMode.POOL) 1 else 0,
                key = { reels[it].url },
            ) { page ->
                Page(page, isCurrent = pagerState.settledPage == page)
            }
            Hud(Modifier.align(Alignment.TopStart))
            Controls(Modifier.align(Alignment.BottomCenter))
        }
    }

    @Composable
    private fun Page(page: Int, isCurrent: Boolean) {
        when (mode) {
            PlayerMode.SHARED -> SharedPage(page, isCurrent, naive = false)
            PlayerMode.SHARED_NAIVE -> SharedPage(page, isCurrent, naive = true)
            PlayerMode.SHARED_WORKAROUND ->
                SharedPage(page, isCurrent, naive = false, workaround = true)
            PlayerMode.PER_PAGE -> OwnPlayerPage(page, isCurrent, prerender = false)
            PlayerMode.PRERENDER_NEXT -> OwnPlayerPage(page, isCurrent, prerender = true)
            PlayerMode.POOL -> PoolPage(page)
        }
    }

    @Composable
    private fun SharedPage(
        page: Int,
        isCurrent: Boolean,
        naive: Boolean,
        workaround: Boolean = false,
    ) {
        val shared = sharedPlayer ?: return
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    resizeMode = ResizeMode.ZOOM
                    isTapGestureEnabled = false
                    // PlayerView releases its player on detach unless it has an id to retain it
                    // under; a Compose-created view has none.
                    if (workaround) id = android.view.View.generateViewId()
                    if (naive) player = shared
                }
            },
            onRelease = { view -> if (workaround) view.player = null },
            update = { view ->
                if (!naive) view.player = if (isCurrent) shared else null
            },
            modifier = Modifier.fillMaxSize(),
        )
        PageLabel(page)
    }

    @Composable
    private fun OwnPlayerPage(page: Int, isCurrent: Boolean, prerender: Boolean) {
        val player = remember { buildPlayer(autoplay = !prerender) }
        DisposableEffect(player) {
            pagePlayers[page] = player
            // Prepared paused: decodes and presents frame 0, then idles until the page is selected.
            if (prerender) player.setMediaItem(mediaItemFor(page))
            // The page may already be the settled one on first composition (page 0).
            if (current?.page == page && current?.hasPlayer == false) startOn(page, player)
            onDispose {
                pagePlayers.remove(page)
                firstFrameAt.remove(player)
                player.release()
            }
        }
        LaunchedEffect(isCurrent) {
            if (!isCurrent) player.pause()
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    resizeMode = ResizeMode.ZOOM
                    isTapGestureEnabled = false
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        PageLabel(page)
    }

    /** A page whose player comes from the pool — the pool decides whether it plays or pre-renders. */
    @Composable
    private fun PoolPage(page: Int) {
        val pool = pool ?: return
        val player = remember(page) { pool.playerAt(page).also { trackFirstFrames(it) } }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    resizeMode = ResizeMode.ZOOM
                    isTapGestureEnabled = false
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        PageLabel(page)
    }

    @Composable
    private fun PageLabel(page: Int) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomStart) {
            Text("#$page ${reels[page].id}", color = Color.White, fontSize = 14.sp)
        }
    }

    private fun onPageSettled(page: Int) {
        current?.finish()
        val url = reels[page].url
        current = SwipeMeasurement(page, warm = preCacher?.isWarm(url) == true)

        preCacher?.preCache((page + 1..page + PRECACHE_AHEAD).mapNotNull { reels.getOrNull(it)?.url })

        pool?.let { pool ->
            // Attach before selecting: Media3 reports "playing" synchronously inside play().
            val player = pool.playerAt(page)
            trackFirstFrames(player)
            current?.attach(player)
            pool.setCurrentIndex(page)
            return
        }

        // PER_PAGE: the page is composed while it scrolls in, so its player normally exists by now.
        (sharedPlayer ?: pagePlayers[page])?.let { startOn(page, it) }
    }

    private fun startOn(page: Int, player: FastPixPlayer) {
        // A shared player switching items will present a new first frame; a pre-rendered one
        // already has.
        if (player.getCurrentPlaybackUrl() != reels[page].url) firstFrameAt.remove(player)
        current?.attach(player)
        player.setMediaItem(mediaItemFor(page))
        player.play()
    }

    /** Times one page from settle to ready, then counts rebuffers until the next settle. */
    private inner class SwipeMeasurement(val page: Int, val warm: Boolean) {
        private val settledAt = SystemClock.elapsedRealtime()
        private var player: FastPixPlayer? = null
        private var startMs: Long = -1
        /** Settle to first video frame on screen: how long the page showed black. 0 = pre-rendered. */
        private var firstFrameMs: Long = -1
        private var rebuffers = 0
        private var rebufferMs = 0L
        private var bufferingSince = 0L
        private var track: VideoTrack? = null
        private var error: String? = null
        private var finished = false

        private val listener = object : PlaybackListener {
            override fun onPlay() = Unit
            override fun onPause() = Unit
            override fun onPlaybackStateChanged(isPlaying: Boolean) {
                // A pre-rendered player reported ready before this page was selected, so for it
                // playback actually starting is the start signal.
                if (isPlaying && startMs < 0) {
                    startMs = SystemClock.elapsedRealtime() - settledAt
                    report("ready")
                }
            }

            override fun onError(error: PlaybackException) {
                this@SwipeMeasurement.error = error.errorCodeName
                report("error")
            }

            override fun onPlayerReady(durationMs: Long) {
                if (startMs >= 0) return
                startMs = SystemClock.elapsedRealtime() - settledAt
                report("ready")
            }

            override fun onBufferingStart() {
                if (startMs >= 0) bufferingSince = SystemClock.elapsedRealtime()
            }

            override fun onBufferingEnd() {
                if (startMs >= 0 && bufferingSince > 0) {
                    rebuffers++
                    rebufferMs += SystemClock.elapsedRealtime() - bufferingSince
                    bufferingSince = 0
                }
            }

            override fun onVideoQualityChanged(
                quality: VideoTrack?,
                source: PlaybackListener.VideoQualityChangeSource,
            ) {
                if (track == null) track = quality
            }
        }

        val hasPlayer: Boolean get() = player != null

        fun attach(p: FastPixPlayer) {
            player = p
            p.addPlaybackListener(listener)
            if (firstFrameAt.containsKey(p)) firstFrameMs = 0
        }

        fun onFirstFrame(p: FastPixPlayer) {
            if (p === player && firstFrameMs < 0) {
                firstFrameMs = SystemClock.elapsedRealtime() - settledAt
            }
        }

        fun finish() {
            if (finished) return
            finished = true
            report("swipe")
            player?.removePlaybackListener(listener)
        }

        private fun report(kind: String) {
            val line = String.format(
                Locale.US,
                "%s page=%d blackMs=%d startMs=%d warm=%b height=%s bitrate=%s rebuffers=%d rebufferMs=%d error=%s",
                kind, page, firstFrameMs, startMs, warm, track?.height, track?.bitrate, rebuffers, rebufferMs, error,
            )
            log(line)
            if (kind != "ready") {
                hudLines.add(0, line.substringAfter(' '))
                if (hudLines.size > 5) hudLines.removeAt(hudLines.lastIndex)
            }
            cacheMb.value = MediaCacheProvider.cachedBytes() / (1024.0 * 1024.0)
        }
    }

    @Composable
    private fun Hud(modifier: Modifier) {
        Column(
            modifier
                .safeDrawingPadding()
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(8.dp)
        ) {
            val text = buildString {
                append("$host · $mode · cache $cacheMode · precache ${if (preCacher != null) "ON" else "OFF"}")
                append(" · cap ${if (capMatched) "matched" else "default"}")
                append(String.format(Locale.US, " · %.1f MB", cacheMb.value))
                hudLines.forEach { append('\n').append(it) }
            }
            Text(text, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }

    @Composable
    private fun Controls(modifier: Modifier) {
        Row(
            modifier
                .safeDrawingPadding()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(onClick = { restart(mode = PlayerMode.entries[(mode.ordinal + 1) % PlayerMode.entries.size]) }) {
                Text(mode.name, fontSize = 11.sp)
            }
            Button(onClick = { restart(cache = CacheMode.entries[(cacheMode.ordinal + 1) % CacheMode.entries.size]) }) {
                Text("cache ${cacheMode.name}", fontSize = 11.sp)
            }
            Button(onClick = { restart(preCache = !preCacheOn) }) {
                Text("precache ${if (preCacheOn) "ON" else "OFF"}", fontSize = 11.sp)
            }
            Button(onClick = { restart(capMatched = !capMatched) }) {
                Text("cap ${if (capMatched) "matched" else "default"}", fontSize = 11.sp)
            }
            Button(onClick = { restart(host = Host.entries[(host.ordinal + 1) % Host.entries.size]) }) {
                Text(host.name, fontSize = 11.sp)
            }
            Button(onClick = { restart(clearCache = true) }) {
                Text("clear cache", fontSize = 11.sp)
            }
        }
    }

    private fun restart(
        mode: PlayerMode = this.mode,
        cache: CacheMode = cacheMode,
        preCache: Boolean = preCacheOn,
        capMatched: Boolean = this.capMatched,
        clearCache: Boolean = false,
        host: Host = this.host,
    ) {
        startActivity(newIntent(this, mode, cache, preCache, capMatched, clearCache, host))
        finish()
    }

    override fun onPause() {
        super.onPause()
        sharedPlayer?.pause()
        pool?.getCurrentIndex()?.takeIf { it >= 0 }?.let { pool?.playerAt(it)?.pause() }
    }

    override fun onDestroy() {
        current?.finish()
        preCacher?.release()
        sharedPlayer?.release()
        pool?.release()
        super.onDestroy()
    }

    private fun log(message: String) = Log.i(TAG, message)

    companion object {
        const val TAG = "ComposeReelRepro"

        private const val EXTRA_HOST = "host"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_CACHE = "cache"
        private const val EXTRA_PRECACHE = "precache"
        private const val EXTRA_CAP_MATCHED = "capMatched"
        private const val EXTRA_CLEAR_CACHE = "clearCache"

        private const val PRECACHE_AHEAD = 2
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        /** Same ceiling as [ReelFeedActivity]: settles on the 1.65M (480p) rung of the sample ladder. */
        private const val CAP_BPS = 1_650_000

        fun newIntent(
            context: Context,
            mode: PlayerMode = PlayerMode.SHARED,
            cache: CacheMode = CacheMode.ON_DEMAND_FEED,
            preCache: Boolean = true,
            capMatched: Boolean = true,
            clearCache: Boolean = false,
            host: Host = Host.COMPOSE_PAGER,
        ): Intent = Intent(context, ComposeReelFeedActivity::class.java)
            .putExtra(EXTRA_HOST, host.name)
            .putExtra(EXTRA_MODE, mode.name)
            .putExtra(EXTRA_CACHE, cache.name)
            .putExtra(EXTRA_PRECACHE, preCache)
            .putExtra(EXTRA_CAP_MATCHED, capMatched)
            .putExtra(EXTRA_CLEAR_CACHE, clearCache)
    }
}
