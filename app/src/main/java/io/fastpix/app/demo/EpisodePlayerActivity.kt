package io.fastpix.app.demo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import io.fastpix.app.feed.CommonVideoPlayer
import io.fastpix.media3.ResizeMode
import io.fastpix.media3.buffer.BufferConfig
import io.fastpix.media3.core.FastPixPlayer
import io.fastpix.media3.playlist.PlaylistItem
import io.fastpix.media3.playlist.PlaylistItemChangeReason
import io.fastpix.media3.playlist.PlaylistListener
import io.fastpix.media3.preload.PreloadConfig
import io.fastpix.media3.prerender.PrerenderConfig
import kotlinx.coroutines.delay

/**
 * A series screen: the episode playing on top, every episode listed below it. Episodes play on in
 * order by themselves; tapping one jumps to it.
 *
 * Unlike the reel feed this is **one** player and one view, holding the whole series as a playlist
 * ([FastPixPlayer.setPlaylist]). The SDK keeps moving between episodes fast:
 * - **Pre-rendering** (`PrerenderConfig(count = 1)`): the next episode's first frame is captured in
 *   the background and shown the moment the player moves on, so there is no black frame between
 *   episodes.
 * - **Preloading** (`PreloadConfig(count = 2, behind = 1)`): the next two episodes (and the last
 *   one, for going back) are buffered ahead of time.
 * - **Disk cache** ([DemoPlayback.cache]): shared with the home screen, whose pre-cacher already
 *   warmed the series' first episode, so the screen opens straight from disk.
 * - **Analytics**: each episode watched is its own FastPix Data view, with its title.
 */
@UnstableApi
class EpisodePlayerActivity : ComponentActivity() {

    private lateinit var player: FastPixPlayer
    private lateinit var series: DemoSeries
    private var currentIndex by mutableIntStateOf(0)

    private val playlistListener = object : PlaylistListener {
        override fun onPlaylistItemChanged(
            index: Int,
            item: PlaylistItem,
            reason: PlaylistItemChangeReason,
        ) {
            currentIndex = index
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        series = DemoRepository.series[intent.getIntExtra(EXTRA_SERIES, 0)]
        val startIndex = intent.getIntExtra(EXTRA_EPISODE, 0).coerceIn(series.episodes.indices)

        player = FastPixPlayer.Builder(this)
            .setAutoplay(true)
            // Off, so the playlist advances to the next episode when one ends.
            .setLoop(false)
            // Long-form: the default buffer, which plays on deeper than a feed's.
            .setBufferConfig(BufferConfig.DEFAULT)
            .setAbrConfig(DemoPlayback.abr)
            .setCacheConfig(DemoPlayback.cache)
            .setPreloadConfig(PreloadConfig(count = 2, behind = 1))
            .setPrerenderConfig(PrerenderConfig(count = 1))
            .setAnalyticsConfig(DemoPlayback.analytics())
            .build()
        player.addPlaylistListener(playlistListener)
        player.setPlaylist(series.episodes.map { it.playlistItem }, startIndex)
        currentIndex = startIndex

        setContent {
            MaterialTheme { EpisodeScreen() }
        }
    }

    @Composable
    private fun EpisodeScreen() {
        val episodes = series.episodes
        val current = episodes[currentIndex]

        Column(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                CommonVideoPlayer(
                    player = player,
                    modifier = Modifier.fillMaxSize(),
                    resizeMode = ResizeMode.FIT,
                    // No poster: between episodes the SDK shows the pre-rendered first frame, and a
                    // thumbnail drawn over it would hide exactly that.
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(40.dp)
                        .clickable { finish() }
                        .padding(8.dp),
                )
            }
            PlaybackProgress()

            LazyColumn(Modifier.fillMaxSize()) {
                item { NowPlaying(current) }
                item {
                    Text(
                        "Episodes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                itemsIndexed(episodes, key = { _, episode -> episode.uid }) { index, episode ->
                    EpisodeRow(
                        episode = episode,
                        isPlaying = index == currentIndex,
                        onClick = { if (index != currentIndex) player.skipTo(index) },
                    )
                }
            }
        }
    }

    @Composable
    private fun NowPlaying(current: DemoVideo) {
        Column(Modifier.padding(16.dp)) {
            Text(series.title, color = Color(0xFF2874F0), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(current.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Featured: ${current.price}  ·  Episode ${currentIndex + 1} of ${series.episodes.size}",
                color = Color.Gray,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { player.previous() },
                    enabled = currentIndex > 0,
                ) { Text("Previous") }
                OutlinedButton(
                    onClick = { player.next() },
                    enabled = currentIndex < series.episodes.lastIndex,
                ) { Text("Next episode") }
            }
        }
    }

    @Composable
    private fun EpisodeRow(episode: DemoVideo, isPlaying: Boolean, onClick: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (isPlaying) Color(0xFFEFF4FF) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(
                episode.thumbnailUrl(),
                Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    episode.title,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (isPlaying) "Now playing" else episode.price,
                    color = if (isPlaying) Color(0xFF2874F0) else Color.Gray,
                    fontSize = 12.sp,
                )
            }
        }
    }

    @Composable
    private fun PlaybackProgress() {
        var progress by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
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
            color = Color(0xFF2874F0),
            trackColor = Color(0xFFE0E0E0),
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
        )
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onResume() {
        super.onResume()
        player.play()
    }

    override fun onDestroy() {
        player.removePlaylistListener(playlistListener)
        player.release()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SERIES = "series"
        private const val EXTRA_EPISODE = "episode"
        private const val PROGRESS_POLL_MS = 250L

        fun newIntent(context: Context, seriesId: Int, episode: Int = 0): Intent =
            Intent(context, EpisodePlayerActivity::class.java)
                .putExtra(EXTRA_SERIES, seriesId)
                .putExtra(EXTRA_EPISODE, episode)
    }
}
