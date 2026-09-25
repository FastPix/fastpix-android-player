package io.fastpix.app.feed

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import io.fastpix.app.DummyData
import io.fastpix.app.ReelFeedActivity
import io.fastpix.app.dummyData
import io.fastpix.data.domain.model.VideoDataDetails
import io.fastpix.media3.PlaybackListener
import io.fastpix.media3.analytics.AnalyticsConfig
import io.fastpix.media3.buffer.BufferConfig
import io.fastpix.media3.cache.CacheConfig
import io.fastpix.media3.core.FastPixPlayer
import io.fastpix.media3.playlist.FastPixPlayerPool
import io.fastpix.media3.playlist.PlaylistItem
import io.fastpix.media3.preload.PreloadConfig
import io.fastpix.media3.prerender.PrerenderConfig

/**
 * A vertical reel feed built the way many apps build one: a View-based [ViewPager2] whose pages
 * are each a [ComposeView] calling the app's shared [CommonVideoPlayer].
 *
 * The one thing that makes it fast is where the players come from. Each page asks
 * [FastPixPlayerPool] for its player instead of building one, and tells the pool which page is on
 * screen. The pool then keeps the pages around it ready — preloaded, cached, and with
 * [PrerenderConfig] decoded up to their first frame — so a swipe lands on video, not a spinner.
 *
 * The HUD reports, for each swipe, whether the page's first frame was already on screen when it
 * arrived. The toggle rebuilds the screen with pre-rendering off, for comparison.
 */
@UnstableApi
class ViewPagerFeedActivity : AppCompatActivity() {

    private lateinit var pool: FastPixPlayerPool
    private lateinit var pager: ViewPager2

    private var prerenderEnabled = true
    private val hudText = mutableStateOf("")

    private val videos: List<DummyData> by lazy {
        dummyData.filterNot { item ->
            item.id.contains("DRM", ignoreCase = true) ||
                    item.url.contains(ReelFeedActivity.EXCLUDED_PLAYBACK_ID)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prerenderEnabled = intent.getBooleanExtra(EXTRA_PRERENDER, true)

        pool = FastPixPlayerPool.Builder(this)
            .setPlayerConfig {
                it.setLoop(true)
                    .setBufferConfig(BufferConfig.FEED)
            }
            .setPreloadConfig(PreloadConfig(count = 3, behind = 1))
            .setCacheConfig(CacheConfig.enabled())
            .setPrerenderConfig(
                if (prerenderEnabled) PrerenderConfig(count = 1, behind = 1)
                else PrerenderConfig.DISABLED
            )
            // One FastPix Data view per page the user lands on; pre-rendered pages never count.
            .setAnalyticsConfig(
                intent.getStringExtra(EXTRA_ANALYTICS_WORKSPACE)?.let { workspace ->
                    AnalyticsConfig.Builder(workspace)
                        .setBeaconDomain(intent.getStringExtra(EXTRA_BEACON_DOMAIN))
                        .build()
                }
            )
            .build()
        pool.setPlaylist(
            videos.map { video ->
                PlaylistItem.fromUrl(video.url, id = video.id)
                    .withVideoData(VideoDataDetails(videoId = video.id, videoTitle = video.id))
            }
        )

        pager = ViewPager2(this).apply {
            orientation = ViewPager2.ORIENTATION_VERTICAL
            // The neighbouring pages must exist for their players to have somewhere to draw.
            offscreenPageLimit = 1
            adapter = FeedAdapter()
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = onPageShown(position)
            })
        }

        val hud = ComposeView(this).apply {
            setContent { MaterialTheme { Hud() } }
        }

        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                addView(pager, MATCH, MATCH)
                addView(hud, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))
            }
        )
        updateHud("swipe up")
    }

    /** The pager settled on [position]: play it, and note whether it arrived ready. */
    private fun onPageShown(position: Int) {
        val player = pool.playerAt(position)
        val shownAt = SystemClock.elapsedRealtime()
        if (player.hasRenderedFirstFrame()) {
            report(position, 0L, preRendered = true)
        } else {
            player.addPlaybackListener(object : PlaybackListener {
                override fun onFirstFrameRendered() {
                    player.removePlaybackListener(this)
                    report(position, SystemClock.elapsedRealtime() - shownAt, preRendered = false)
                }

                override fun onPlay() = Unit
                override fun onPause() = Unit
                override fun onPlaybackStateChanged(isPlaying: Boolean) = Unit
                override fun onError(error: PlaybackException) = player.removePlaybackListener(this)
            })
        }
        pool.setCurrentIndex(position)
        // Belt and braces: the page on screen must show the player the pool is playing for it.
        val holder = (pager.getChildAt(0) as? RecyclerView)
            ?.findViewHolderForAdapterPosition(position) as? FeedPageHolder
        if (holder != null && holder.player !== player) holder.bind(position)
    }

    private fun report(position: Int, waitedMs: Long, preRendered: Boolean) {
        val line = if (preRendered) "#$position: first frame was already on screen"
        else "#$position: waited $waitedMs ms for the first frame"
        Log.i(TAG, "page=$position waitedMs=$waitedMs preRendered=$preRendered prerender=$prerenderEnabled")
        updateHud(line)
    }

    private fun updateHud(line: String) {
        hudText.value = "Pre-render ${if (prerenderEnabled) "ON" else "OFF"}\n$line"
    }

    @Composable
    private fun Hud() {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color(0x88000000))
                .padding(12.dp)
        ) {
            Text(hudText.value, color = Color.White, fontSize = 13.sp)
            Button(onClick = {
                startActivity(newIntent(this@ViewPagerFeedActivity, !prerenderEnabled))
                finish()
            }) {
                Text(if (prerenderEnabled) "Turn pre-render off" else "Turn pre-render on")
            }
        }
    }

    /** One page: the shared player, plus whatever the app draws over it. */
    @Composable
    private fun FeedPage(video: DummyData, position: Int, player: FastPixPlayer) {
        Box(Modifier.fillMaxSize()) {
            CommonVideoPlayer(player = player, modifier = Modifier.fillMaxSize())
            Text(
                text = "#$position  ${video.id}",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 48.dp),
            )
        }
    }

    private inner class FeedAdapter : RecyclerView.Adapter<FeedPageHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedPageHolder =
            FeedPageHolder(
                ComposeView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(MATCH, MATCH)
                }
            )

        override fun onBindViewHolder(holder: FeedPageHolder, position: Int) = holder.bind(position)

        override fun onViewAttachedToWindow(holder: FeedPageHolder) {
            super.onViewAttachedToWindow(holder)
            // RecyclerView re-attaches cached pages without rebinding them; by then the pool may
            // have given that page's player to another page, so ask again.
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION && holder.player !== pool.playerAt(position)) {
                holder.bind(position)
            }
        }

        override fun getItemCount(): Int = videos.size
    }

    private inner class FeedPageHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {

        var player: FastPixPlayer? = null
            private set

        fun bind(position: Int) {
            val pagePlayer = pool.playerAt(position)
            player = pagePlayer
            val video = videos[position]
            composeView.setContent {
                MaterialTheme { FeedPage(video, position, pagePlayer) }
            }
        }
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
        private const val TAG = "ViewPagerFeed"
        private const val EXTRA_PRERENDER = "prerender"

        /** FastPix Data workspace to report to; analytics is off without it. */
        private const val EXTRA_ANALYTICS_WORKSPACE = "analyticsWorkspace"
        private const val EXTRA_BEACON_DOMAIN = "beaconDomain"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        fun newIntent(context: Context, prerenderEnabled: Boolean = true): Intent =
            Intent(context, ViewPagerFeedActivity::class.java)
                .putExtra(EXTRA_PRERENDER, prerenderEnabled)
    }
}
