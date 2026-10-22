package io.fastpix.app.demo

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.media3.common.util.UnstableApi
import io.fastpix.app.BuildConfig
import io.fastpix.data.domain.model.VideoDataDetails
import io.fastpix.media3.abr.AbrConfig
import io.fastpix.media3.analytics.AnalyticsConfig
import io.fastpix.media3.cache.CacheConfig
import io.fastpix.media3.playlist.PlaylistItem
import kotlinx.coroutines.delay

/** One video in the demos: a reel in the feed or an episode of a series. */
@UnstableApi
data class DemoVideo(
    /** Unique per feed entry, so the same stream can appear twice; used as the pager key. */
    val uid: String,
    val playbackId: String,
    val title: String,
    val creator: String,
    val price: String,
    val description: String = "",
    /** Profile picture for [creator]. */
    val avatarUrl: String = "",
    val likes: Int = 0,
    val comments: Int = 0,
    val shares: Int = 0,
) {
    /**
     * Built from the playback ID rather than a URL: the pre-cacher and the players then key the
     * disk cache on the same asset, so whatever the home screen warms, the player reads back.
     */
    val playlistItem: PlaylistItem by lazy {
        PlaylistItem.fastPix(playbackId)
            .withVideoData(VideoDataDetails(videoId = playbackId, videoTitle = title))
    }

    fun thumbnailUrl(widthPx: Int = 360): String =
        "https://images.fastpix.io/$playbackId/thumbnail.jpg?width=$widthPx"
}

@UnstableApi
data class DemoSeries(
    val id: Int,
    val title: String,
    val episodes: List<DemoVideo>,
) {
    val cover: DemoVideo get() = episodes.first()
}

/**
 * Playback settings shared by every demo screen.
 *
 * They are shared on purpose: the disk cache is process-wide (the first config wins), and the
 * pre-cacher only helps if it warms the rendition the player will start on, which it can only
 * know from the same [abr] config.
 */
@UnstableApi
object DemoPlayback {

    val cache: CacheConfig = CacheConfig.enabled()

    /**
     * A phone screen gains little above 480p for short video, and a lower cap keeps the first
     * segments small enough to warm quickly. The FastPix sample ladder runs
     * 4.4M / 2.75M / 1.65M / 0.55M, so this settles on 1.65M (854x480).
     */
    val abr: AbrConfig = AbrConfig(
        wifiMaxBitrateBps = 1_650_000,
        cellular5g4gMaxBitrateBps = 1_650_000,
    )

    /**
     * FastPix Data analytics when `fastpix.analyticsWorkspace` is set in local.properties; off
     * otherwise. Built without a view, so each view is measured through whichever PlayerView shows
     * the player — what a feed needs.
     */
    fun analytics(): AnalyticsConfig? =
        BuildConfig.ANALYTICS_WORKSPACE.takeIf { it.isNotBlank() }
            ?.let { AnalyticsConfig.Builder(it).build() }
}

/**
 * Stands in for a backend: hands out the reel feed a page at a time, forever, and a fixed set of
 * series. Main thread only.
 *
 * [reels] is snapshot state shared by the home grid and the reel feed, so both screens page through
 * the same list and a reel opened from the grid sits at the same index in the feed.
 */
@UnstableApi
object DemoRepository {

    /** Public sample streams; the private and DRM samples need tokens and are left out. */
    private val playbackIds = listOf(
        "b7aeedfa-715f-4cc8-b568-237dfb889131",
        "d3fcdcdd-0608-4767-888d-c7dd5c077a04",
        "6a49da1a-6c3e-4c2b-96d2-a0606b8e252a",
        "2125094c-db43-4748-90e1-18539f2ccf98",
        "ca854fd4-a3d0-4525-bd43-80de50887e1a",
        "3ad91e5f-0f45-403f-bda0-2a668a3581ee",
        "46c09d0c-d97a-44b2-9737-c5e6daf30a41",
        "f19268f5-9719-403f-87c9-b604fb3bdce3",
    )

    private val products = listOf(
        "Running shoes", "Wireless earbuds", "Denim jacket", "Smart watch", "Backpack",
        "Sunglasses", "Coffee maker", "Desk lamp", "Yoga mat", "Phone case",
    )
    private val creators = listOf(
        "@styledbyria", "@techwithdev", "@homehacks", "@fitwithneha", "@gadgetguru", "@dailydeals",
    )
    private val prices = listOf("₹499", "₹1,299", "₹799", "₹2,499", "₹349", "₹999")
    private val captions = listOf(
        "Three ways to style one jacket this winter ❄️ Which one is your pick? #ootd #styling",
        "Honest review after 30 days — battery, comfort and the one thing I didn't expect 🎧",
        "Tiny room, big glow-up ✨ Everything here is under ₹999. Saving this for later? #homedecor",
        "15-minute morning routine that actually stuck 💪 Try it for a week and tell me how it went",
        "Unboxing the gadget everyone's been asking about 📦 Worth the hype? #tech #unboxing",
        "Coffee at home that tastes like the café ☕ Recipe in the comments",
    )

    const val PAGE_SIZE = 12

    private val _reels: SnapshotStateList<DemoVideo> = mutableStateListOf()
    val reels: List<DemoVideo> get() = _reels

    private var loading = false

    /** Loads the first page at once if nothing is loaded yet, so a screen can start without waiting. */
    fun ensureFirstPage() {
        if (_reels.isEmpty()) _reels += buildPage()
    }

    /**
     * Appends the next page after a simulated network round trip. Returns the new reels, or an
     * empty list when a load is already in flight.
     */
    suspend fun loadMore(): List<DemoVideo> {
        if (loading) return emptyList()
        loading = true
        try {
            delay(FAKE_LATENCY_MS)
            val page = buildPage()
            _reels += page
            return page
        } finally {
            loading = false
        }
    }

    private fun buildPage(): List<DemoVideo> {
        val start = _reels.size
        return (start until start + PAGE_SIZE).map { n ->
            DemoVideo(
                uid = "reel-$n",
                // Rotate by page as well, so consecutive pages don't line up identically.
                playbackId = playbackIds[(n + n / playbackIds.size) % playbackIds.size],
                title = products[n % products.size],
                creator = creators[n % creators.size],
                price = prices[n % prices.size],
                description = captions[n % captions.size],
                avatarUrl = "https://i.pravatar.cc/150?img=${n % creators.size + 11}",
                // Deterministic but uneven, so every reel doesn't show the same counts.
                likes = 1_200 + (n * 7_919) % 480_000,
                comments = 40 + (n * 613) % 9_000,
                shares = 15 + (n * 271) % 25_000,
            )
        }
    }

    val series: List<DemoSeries> by lazy {
        listOf("Style Diaries", "Gadget Lab", "Home Makeover", "Fit in 15", "Kitchen Stories")
            .mapIndexed { s, title ->
                DemoSeries(
                    id = s,
                    title = title,
                    episodes = (0 until EPISODES_PER_SERIES).map { e ->
                        DemoVideo(
                            uid = "series-$s-ep-$e",
                            playbackId = playbackIds[(s * 3 + e) % playbackIds.size],
                            title = "Episode ${e + 1}: ${products[(s + e) % products.size]}",
                            creator = title,
                            price = prices[(s + e) % prices.size],
                        )
                    },
                )
            }
    }

    private const val EPISODES_PER_SERIES = 8
    private const val FAKE_LATENCY_MS = 400L
}
