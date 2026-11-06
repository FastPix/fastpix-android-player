package io.fastpix.app.demo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import io.fastpix.app.feed.CommonVideoPlayer
import io.fastpix.media3.buffer.BufferConfig
import io.fastpix.media3.core.FastPixPlayer
import io.fastpix.media3.playlist.FastPixPlayerPool
import io.fastpix.media3.preload.PreloadConfig
import io.fastpix.media3.prerender.PrerenderConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * An Instagram-style reel feed that never ends: a full-screen vertical pager, one video per page,
 * a new page of reels fetched as the user nears the end.
 *
 * Everything that makes it feel instant comes from [FastPixPlayerPool]:
 * - **Pre-rendering** (`PrerenderConfig(count = 1, behind = 1)`): the pages either side are decoded
 *   up to their first frame and held paused, so a swipe lands on video, not black.
 * - **Preloading** (`PreloadConfig(count = 3, behind = 1)`): the next three reels' first seconds are
 *   buffered while the current one plays.
 * - **Disk cache** ([DemoPlayback.cache]): preloaded segments are written to disk, keyed by asset,
 *   so swiping back, re-watching, or arriving from the home grid (whose pre-cacher already warmed
 *   the tapped reel) plays from storage.
 * - **Feed buffering** ([BufferConfig.FEED]): start on 250 ms of media, and don't buffer far ahead
 *   of a video the user will likely swipe away from.
 * - **One bandwidth estimate and ABR cap** ([DemoPlayback.abr]) across all pages, so each page
 *   starts at the quality the last one learned.
 * - A small number of players, recycled, so swiping doesn't create and release decoders.
 * - **Analytics**: one FastPix Data view per reel the user actually lands on.
 *
 * The app itself only binds `pool.playerAt(page)` to each page, calls `pool.setCurrentIndex` when the
 * pager settles, and appends new pages with `pool.addToPlaylist`.
 */
@UnstableApi
class ReelsActivity : ComponentActivity() {

    private lateinit var pool: FastPixPlayerPool
    private var muted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        DemoRepository.ensureFirstPage()
        pool = FastPixPlayerPool.Builder(this)
            .setPlayerConfig {
                it.setLoop(true)
                    .setBufferConfig(BufferConfig.FEED)
                    .setAbrConfig(DemoPlayback.abr)
            }
            .setPreloadConfig(PreloadConfig(count = 3, behind = 1))
            .setCacheConfig(DemoPlayback.cache)
            .setPrerenderConfig(PrerenderConfig(count = 1, behind = 1))
            .setAnalyticsConfig(DemoPlayback.analytics())
            .build()
        pool.setPlaylist(DemoRepository.reels.map { it.playlistItem })

        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
            .coerceIn(0, DemoRepository.reels.lastIndex)

        setContent {
            MaterialTheme { ReelsScreen(startIndex) }
        }
    }

    @Composable
    private fun ReelsScreen(startIndex: Int) {
        val reels = DemoRepository.reels
        val pagerState = rememberPagerState(initialPage = startIndex) { reels.size }
        val scope = rememberCoroutineScope()

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                onPageSettled(page)
                // Fetch the next page well before the end, so the user never hits it.
                if (page >= reels.size - LOAD_MORE_THRESHOLD) {
                    scope.launch {
                        DemoRepository.loadMore()
                        syncPlaylist()
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // Pre-rendered pages need a view to draw into: keep the neighbours composed.
                beyondViewportPageCount = 1,
                key = { reels[it].uid },
            ) { page ->
                ReelPage(reels[page], page, isCurrent = pagerState.settledPage == page)
            }
            TopBar(Modifier.align(Alignment.TopStart))
        }
    }

    /** Appends to the pool any reels the repository has that the pool doesn't yet. */
    private fun syncPlaylist() {
        val known = pool.getPlaylist().size
        val reels = DemoRepository.reels
        if (reels.size > known) {
            pool.addToPlaylist(reels.subList(known, reels.size).map { it.playlistItem })
        }
    }

    private fun onPageSettled(page: Int) {
        syncPlaylist()
        // Mute before the pool starts it, so a muted feed never blips audio.
        pool.playerAt(page).applyMute()
        pool.setCurrentIndex(page)
    }

    private fun FastPixPlayer.applyMute() = if (muted) mute() else unmute()

    @Composable
    private fun ReelPage(reel: DemoVideo, page: Int, isCurrent: Boolean) {
        // The pool supplies (and recycles) the page's player; never build or release one here.
        // Sync first: the home grid may have paged the shared list further than the pool knows.
        val player = remember(page) {
            syncPlaylist()
            pool.playerAt(page)
        }

        Box(Modifier.fillMaxSize()) {
            CommonVideoPlayer(
                player = player,
                modifier = Modifier.fillMaxSize(),
                poster = { Thumbnail(reel.thumbnailUrl(720), Modifier.fillMaxSize()) },
            )
            ReelOverlay(reel, social)
            if (isCurrent) {
                PlaybackProgress(player, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    @Composable
    private fun TopBar(modifier: Modifier) {
        Row(
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(40.dp)
                    .clickable { finish() }
                    .padding(8.dp),
            )
            Text("Reels", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (muted) "Sound off" else "Sound on",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(Color(0x66000000), RoundedCornerShape(50))
                    .clickable {
                        muted = !muted
                        pool.getCurrentIndex().takeIf { it >= 0 }
                            ?.let { pool.playerAt(it).applyMute() }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }

    /** Likes and follows, kept across pages for the life of the screen. */
    private val social = ReelSocialState()

    /** A thin progress line, polled only for the page on screen. */
    @Composable
    private fun PlaybackProgress(player: FastPixPlayer, modifier: Modifier) {
        var progress by remember(player) { mutableFloatStateOf(0f) }
        LaunchedEffect(player) {
            while (true) {
                val duration = player.getDuration()
                progress = if (duration > 0) {
                    (player.getCurrentPosition().toFloat() / duration).coerceIn(0f, 1f)
                } else {
                    0f
                }
                delay(PROGRESS_POLL_MS)
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            color = Color.White,
            trackColor = Color(0x33FFFFFF),
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(2.dp),
        )
    }

    override fun onPause() {
        super.onPause()
        pool.getCurrentIndex().takeIf { it >= 0 }?.let { pool.playerAt(it).pause() }
    }

    override fun onResume() {
        super.onResume()
        pool.getCurrentIndex().takeIf { it >= 0 }?.let { pool.playerAt(it).play() }
    }

    override fun onDestroy() {
        pool.release()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_START_INDEX = "start_index"

        /** Fetch more once the user is this many reels from the end. */
        private const val LOAD_MORE_THRESHOLD = 4
        private const val PROGRESS_POLL_MS = 200L

        fun newIntent(context: Context, startIndex: Int = 0): Intent =
            Intent(context, ReelsActivity::class.java).putExtra(EXTRA_START_INDEX, startIndex)
    }
}
