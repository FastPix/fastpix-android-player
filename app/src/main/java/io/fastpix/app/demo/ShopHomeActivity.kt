package io.fastpix.app.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import io.fastpix.media3.cache.FastPixPreCacher
import io.fastpix.media3.cache.PreCacheConfig
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A shopping-app home screen with video: a rail of series across the top, and below it a grid of
 * shoppable reels that keeps loading as the user scrolls.
 *
 * - Tapping a reel opens [ReelsActivity] on that reel, and the user can swipe on through the whole
 *   (endless) feed.
 * - Tapping a series opens [EpisodePlayerActivity]: the episode plays on top, the rest are listed
 *   below.
 *
 * Nothing plays on this screen, but it still makes the next screen fast: [FastPixPreCacher] warms
 * the first seconds of whatever reels and series are on screen, once scrolling settles. Whatever
 * the user taps then starts from disk instead of the network. The warming uses the players' own
 * cache and ABR settings ([DemoPlayback]) so it fetches the rendition they will actually start on,
 * and it is cancelled while another screen is in front, so it never competes with playback.
 */
@UnstableApi
class ShopHomeActivity : ComponentActivity() {

    private var preCacher: FastPixPreCacher? = null

    /** What is on screen now, re-warmed when the user comes back to this screen. */
    private var visibleToWarm: List<DemoVideo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DemoRepository.ensureFirstPage()

        preCacher = FastPixPreCacher.create(
            this,
            DemoPlayback.cache,
            // Two segments is a couple of seconds of video: enough to start on while the player
            // fetches the rest, cheap enough to do for every tile on screen.
            PreCacheConfig(segmentCount = 2, maxParallelItems = 2),
            DemoPlayback.abr,
        )

        setContent {
            MaterialTheme { HomeScreen() }
        }
    }

    override fun onStart() {
        super.onStart()
        warm(visibleToWarm)
    }

    override fun onStop() {
        // Another screen is playing now; don't take bandwidth from it.
        preCacher?.cancelAll()
        super.onStop()
    }

    override fun onDestroy() {
        preCacher?.release()
        super.onDestroy()
    }

    /** Replaces what is being warmed; the pre-cacher cancels anything no longer in the list. */
    private fun warm(videos: List<DemoVideo>) {
        visibleToWarm = videos
        if (videos.isNotEmpty()) preCacher?.preCacheItems(videos.map { it.playlistItem })
    }

    @OptIn(FlowPreview::class)
    @Composable
    private fun HomeScreen() {
        val reels = DemoRepository.reels
        val gridState = rememberLazyGridState()
        val railState = rememberLazyListState()

        // Endless grid: fetch the next page as the user nears the end.
        LaunchedEffect(gridState) {
            snapshotFlow {
                val info = gridState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                // Keyed on the item count, so a page that still leaves the user near the end
                // asks for another.
                if (last >= info.totalItemsCount - LOAD_MORE_THRESHOLD) info.totalItemsCount else -1
            }
                .distinctUntilChanged()
                .collect { count -> if (count >= 0) DemoRepository.loadMore() }
        }

        // Warm what the user can see once scrolling settles — not every tile flung past.
        LaunchedEffect(gridState, railState) {
            snapshotFlow {
                val gridItems = gridState.layoutInfo.visibleItemsInfo
                val visibleReels = gridItems
                    .map { it.index - HEADER_ITEMS }
                    .filter { it in reels.indices }
                    .map { reels[it] }
                // The rail keeps its last layout after scrolling off screen; only count it while
                // it is actually showing.
                val railShown = gridItems.any { it.key == RAIL_KEY }
                val visibleSeries = if (!railShown) emptyList() else {
                    railState.layoutInfo.visibleItemsInfo.map { DemoRepository.series[it.index].cover }
                }
                // Reels first: they are the bigger, likelier tap targets.
                (visibleReels.take(MAX_REELS_TO_WARM) + visibleSeries.take(MAX_SERIES_TO_WARM))
                    .distinctBy { it.playbackId }
            }
                .debounce(WARM_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { warm(it) }
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F3F6)),
        ) {
            SearchHeader()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "series-title", span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle("Watch series", "Shop while you binge")
                }
                item(key = RAIL_KEY, span = { GridItemSpan(maxLineSpan) }) {
                    SeriesRail(railState)
                }
                item(key = "reels-title", span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle("Trending reels", "Tap to watch, swipe for more")
                }
                itemsIndexed(reels, key = { _, reel -> reel.uid }) { index, reel ->
                    ReelTile(reel) {
                        startActivity(ReelsActivity.newIntent(this@ShopHomeActivity, index))
                    }
                }
                item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchHeader() {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF2874F0))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text("ShopReels", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color.Gray)
                Spacer(Modifier.width(8.dp))
                Text("Search for products, brands and more", color = Color.Gray, fontSize = 14.sp)
            }
        }
    }

    @Composable
    private fun SectionTitle(title: String, subtitle: String) {
        Column(Modifier.padding(start = 4.dp, top = 8.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
    }

    @Composable
    private fun SeriesRail(railState: LazyListState) {
        LazyRow(
            state = railState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(DemoRepository.series, key = { it.id }) { series ->
                Column(
                    Modifier
                        .width(220.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .clickable {
                            startActivity(
                                EpisodePlayerActivity.newIntent(this@ShopHomeActivity, series.id)
                            )
                        },
                ) {
                    Box {
                        Thumbnail(
                            series.cover.thumbnailUrl(480),
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                        )
                        PlayBadge(Modifier.align(Alignment.Center))
                        Text(
                            "${series.episodes.size} episodes",
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(Color(0x99000000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        series.title,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun ReelTile(reel: DemoVideo, onClick: () -> Unit) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick),
        ) {
            Thumbnail(reel.thumbnailUrl(), Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color(0xCC000000),
                        )
                    ),
            )
            PlayBadge(Modifier.align(Alignment.TopEnd).padding(6.dp), small = true)
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            ) {
                Text(
                    reel.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(reel.price, color = Color(0xFFFFE11B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun PlayBadge(modifier: Modifier, small: Boolean = false) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = modifier
                .size(if (small) 24.dp else 40.dp)
                .background(Color(0x66000000), RoundedCornerShape(50))
                .padding(if (small) 2.dp else 6.dp),
        )
    }

    private companion object {
        const val LOAD_MORE_THRESHOLD = 6

        /** Grid items before the first reel: two section titles and the rail. */
        const val HEADER_ITEMS = 3
        const val RAIL_KEY = "series-rail"
        const val MAX_REELS_TO_WARM = 4
        const val MAX_SERIES_TO_WARM = 2
        const val WARM_DEBOUNCE_MS = 300L
    }
}
