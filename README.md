# FastPix Android Player SDK

A clean, modern Android video player SDK built on top of [AndroidX Media3 (ExoPlayer)](https://developer.android.com/media/media3). FastPix Player SDK provides a simple, SDK-friendly API for video playback with built-in support for configuration change survival, playback event tracking, and fullscreen mode.

---

## Features

- **Built on Media3 (ExoPlayer)** – Uses Google's powerful and reliable video playback engine
- **FastPix URL Generator** – Built-in builder pattern for creating FastPix media items with resolution, token, and streaming options
- **Configuration Change Survival** – Playback state is preserved across orientation changes and configuration updates (default behavior)
- **Event-Driven Architecture** – Comprehensive playback event listeners for time updates, seek operations, buffering, and errors
- **Fullscreen Mode** – Built-in fullscreen support with proper view reparenting and system UI handling
- **Gesture Support** – Single-tap to toggle play/pause (configurable)
- **Lifecycle Management** – Automatic ExoPlayer lifecycle handling
- **Seek Tracking** – Callbacks for seek start and end events
- **Seek Preview (Spritesheet thumbnails)** – Show thumbnail previews while scrubbing using FastPix spritesheets (with graceful timestamp fallback)
- **Time Updates** – Continuous time updates during playback (similar to HTML5 `onTimeUpdate`)
- **Volume Control** – Complete volume management with mute/unmute, volume level control, and device volume monitoring
- **AutoPlay** – Automatic playback start when media is ready (configurable)
- **Loop Playback** – Seamless looping functionality for continuous playback
- **Playback Rate Control** – Adjustable playback speed from 0.25x to 2.0x with multiple speed options
- **Video Quality Switching** – Get available video renditions, lock a specific quality, or return to ABR auto mode
- **Subtitle and Audio Track Switching** – Discover and switch audio/subtitle tracks, set default languages, disable subtitles, and render subtitle cues via listeners
- **Widevine DRM Playback** – Configure secure playback with `playbackToken` + `DrmConfig` for FastPix protected streams
- **Fast Start** – Tuned buffering thresholds so the first frame appears sooner than Media3's defaults (configurable via `BufferConfig`)
- **Playlists** – `setPlaylist` with `next`, `previous`, `skipTo`, editing and `PlaylistListener` callbacks
- **Preloading** – Prepare the next N playlist entries (and optionally previous ones) with `PreloadConfig(count = N)`
- **Disk Caching** – Opt-in cache so re-watches, scroll-backs and preloaded entries play from local storage
- **Pre-rendering** – `PrerenderConfig` prepares upcoming entries up to their first frame — on a single player or across the pages of a feed with `FastPixPlayerPool` — so moving between videos never shows a black screen

---

## Requirements

- **Android Studio** Arctic Fox or newer
- **Android SDK** version 24 (Android 7.0) or higher
- **Kotlin** 1.8 or higher
- **AndroidX Media3** 1.9.0

---

## Installation

### Step 1: Add the GitHub Maven Repository to `settings.gradle`
```groovy
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/FastPix/fastpix-android-player")
        credentials {
            username = "<your-github-username>"
            password = "<your-personal-access-token>"
        }
    }
}
```

### Step 2: Add the dependency

Add the following to your `build.gradle.kts` (or `build.gradle`):

```kotlin
dependencies {
    implementation("io.fastpix.player:android:2.2.0")
}
```

Or if using version catalogs, add to `libs.versions.toml`:

```toml
[versions]
fastpix-player = "2.2.0"

[libraries]
fastpix-player = { module = "io.fastpix.player:android", version.ref = "fastpix-player" }
```

### Step 3: Sync Gradle

Sync your project to download the dependency.

---

## Quick Start

### 1. Add PlayerView to your layout

```xml
<io.fastpix.media3.PlayerView
    android:id="@+id/playerView"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

**Important:** Assign an `android:id` to enable configuration change survival. Without an ID, a new player will be created on each configuration change.

### 2. Use PlayerView in your Activity/Fragment

#### Option A: Using FastPix Builder with Advanced Configuration (Recommended)

```kotlin
import io.fastpix.media3.FastPixPlayer
import io.fastpix.media3.PlayerView
import io.fastpix.media3.PlaybackListener
import io.fastpix.media3.core.PlaybackResolution

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var fastPixPlayer: FastPixPlayer
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupPlayer()
    }
    
    private fun setupPlayer() {
        // Create FastPixPlayer with configuration using builder pattern
        fastPixPlayer = FastPixPlayer.Builder(this)
            .setLoop(false)        // Enable looping (optional)
            .setAutoplay(true)      // Enable autoplay (optional)
            .build()
        
        // Pass the configured player to PlayerView
        binding.playerView.player = fastPixPlayer
        
        // Set FastPix media item using builder pattern
        fastPixPlayer.setFastPixMediaItem {
            playbackId = "your-playback-id"
            maxResolution = PlaybackResolution.FHD_1080
        }
        
        // Add playback listener
        fastPixPlayer.addPlaybackListener(object : PlaybackListener {
            override fun onPlay() {
                // Playback started
            }
            
            override fun onPause() {
                // Playback paused
            }
            
            override fun onTimeUpdate(
                currentPositionMs: Long,
                durationMs: Long,
                bufferedPositionMs: Long
            ) {
                // Update UI with current time, duration, and buffered position
            }
            
            override fun onError(error: PlaybackException) {
                // Handle playback error
            }
            
            override fun onVolumeChanged(volumeLevel: Float) {
                // Handle volume changes from device buttons
            }
            
            override fun onPlaybackRateChanged(rate: Float) {
                // Handle playback speed changes
            }
        })
        
        // Autoplay is already configured, no need to call play() if autoplay is enabled
    }
    
    override fun onDestroy() {
        super.onDestroy()
        fastPixPlayer.removePlaybackListener(playbackListener)
        if (isFinishing) {
            binding.playerView.release()
        }
    }
}
```

#### Option B: Using Direct URL

```kotlin
import io.fastpix.media3.PlayerView
import io.fastpix.media3.PlaybackListener
import androidx.media3.common.MediaItem

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupPlayer()
    }
    
    private fun setupPlayer() {
        // Set media item with direct URL
        val mediaItem = MediaItem.fromUri("https://example.com/video.mp4")
        binding.playerView.setMediaItem(mediaItem)
        
        // Add playback listener
        binding.playerView.addPlaybackListener(object : PlaybackListener {
            override fun onPlay() {
                // Playback started
            }
            
            override fun onPause() {
                // Playback paused
            }
            
            override fun onTimeUpdate(
                currentPositionMs: Long,
                durationMs: Long,
                bufferedPositionMs: Long
            ) {
                // Update UI with current time, duration, and buffered position
            }
            
            override fun onError(error: PlaybackException) {
                // Handle playback error
            }
        })
        
        // Start playback
        binding.playerView.play()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            binding.playerView.release()
        }
    }
}
```

---

## Playlists

Give the player a list and it plays the entries in order, moving on by itself when one finishes
(unless `loop` is on, which repeats the current entry).

```kotlin
player.setPlaylist(
    listOf(
        PlaylistItem.fastPix("playback-id-1"),
        PlaylistItem.fastPix("playback-id-2", playbackToken = token),   // signed playback
        PlaylistItem.fastPix {                                          // any setFastPixMediaItem option
            playbackId = "playback-id-3"
            maxResolution = PlaybackResolution.HD_720
        },
        PlaylistItem.fromUrl("https://example.com/video.m3u8"),
    ),
    startIndex = 0,
)

player.next()            // false at the last entry
player.previous()        // false at the first entry
player.skipTo(3)

player.addToPlaylist(moreItems)          // append — e.g. the next page of an endless feed
player.addToPlaylist(1, listOf(item))    // insert
player.removeFromPlaylist(2)
player.clearPlaylist()

player.getPlaylist(); player.getCurrentIndex(); player.getCurrentItem()
player.hasNext(); player.hasPrevious()

player.addPlaylistListener(object : PlaylistListener {
    override fun onPlaylistItemChanged(index: Int, item: PlaylistItem, reason: PlaylistItemChangeReason) {
        // reason: PLAYLIST_SET, NAVIGATION, AUTO_ADVANCE or PLAYLIST_EDITED
    }
    override fun onPlaylistChanged(items: List<PlaylistItem>) { }
})
```

Editing keeps the current entry playing unless it is the one removed. `setMediaItem` and
`setFastPixMediaItem` still play a single item and clear any playlist. `setMediaItems` is deprecated
and now forwards to `setPlaylist`.

---

## Fast Start, Preloading, Caching & Pre-rendering

Arriving at a cold item costs a TLS handshake, a multivariant playlist fetch, a media playlist
fetch and the first segment — four round trips — and then a video decoder has to start before the
first frame appears. Each feature below removes part of that. All are opt-in, and each is one
setting: say *how much*, and the SDK works out the rest.

| Feature | Setting | What it removes |
|---|---|---|
| Fast start | `BufferConfig` (on by default) | Waiting for a deep buffer before starting |
| Preloading | `PreloadConfig(count, behind)` | The network round trips for upcoming entries |
| Disk cache | `CacheConfig.enabled()` | Re-downloading anything watched or preloaded before |
| Pre-rendering | `PrerenderConfig(count, behind)` | The black frame while the next video's decoder starts |

### 1. Fast start (on by default)

The player starts after 500 ms of buffered media rather than Media3's 1000 ms. Tune or opt out:

```kotlin
FastPixPlayer.Builder(context)
    .setBufferConfig(BufferConfig.FEED)            // reel-tuned: start at 250 ms, shallow buffer
    // .setBufferConfig(BufferConfig.MEDIA3_DEFAULT) // restore Media3's stock thresholds
    .build()
```

| Preset | `bufferForPlaybackMs` | Ahead buffer | Use for |
|---|---|---|---|
| `BufferConfig.DEFAULT` | 500 ms | 50 s | General playback (applied automatically) |
| `BufferConfig.FEED` | 250 ms | 20 s | Reel / short-form feeds |
| `BufferConfig.MEDIA3_DEFAULT` | 1000 ms | 50 s | Restoring pre-2.1.0 behaviour |

### 2. Preloading

How many playlist entries around the current one to prepare ahead of time:

```kotlin
FastPixPlayer.Builder(context)
    .setPreloadConfig(PreloadConfig(count = 3))              // the next 3 entries
    // .setPreloadConfig(PreloadConfig(count = 3, behind = 1)) // and the previous one
    .build()

player.setPlaylist(items)
```

- **Adjacent entries** have their first seconds buffered in memory, so they start almost at once.
- **Entries further out** have their playlists fetched and tracks chosen — little memory, no
  round trips left before their first segment.
- **With the disk cache on**, entries further out also have their first segments written to disk,
  in the rendition this player will pick — the SDK follows the player's own bitrate cap and
  bandwidth estimate, so nothing is warmed that playback then refuses.

### 3. Disk cache

Persists what was downloaded, so re-watching, going back, or reaching a preloaded entry reads from
disk:

```kotlin
FastPixPlayer.Builder(context)
    .setCacheConfig(CacheConfig.enabled())          // 256 MB, LRU-evicted
    .build()
```

Safe for any content, live included. FastPix streams are cached **by asset**: their segment URLs
are re-signed on every playlist fetch, so the SDK keys segments by playback ID and rendition rather
than by URL, and they hit across sessions and token refreshes. Query parameters that only authorise
a request (`token`, `signature`, `expires`, `cdn`) are ignored for other URLs.

The cache is **process-wide**: every player shares one store, and the first config to open it fixes
the location and size. Inspect or reset it with `MediaCacheProvider.cachedBytes()` and
`MediaCacheProvider.clear()`. Opening it reads its index from disk, so open it once at startup off
the main thread:

```kotlin
// Application.onCreate()
Executors.newSingleThreadExecutor().execute {
    MediaCacheProvider.getOrCreate(this, CacheConfig.enabled())
}
```

### 4. Pre-rendering

Pre-rendering prepares an upcoming entry up to its **first frame**, so moving to it shows that frame
immediately — the first frame is the loader, and there is no black screen while the video starts.
There are two shapes, depending on your UI.

**One `PlayerView` moving through a playlist** (next episode, a player with Next/Previous):

```kotlin
val player = FastPixPlayer.Builder(context)
    .setPreloadConfig(PreloadConfig(count = 3))
    .setCacheConfig(CacheConfig.enabled())
    .setPrerenderConfig(PrerenderConfig(count = 1, behind = 1))   // next and previous entries
    .build()

playerView.player = player
player.setPlaylist(items)
player.next()     // shows the next entry's first frame at once, then its video
```

While the current entry plays, the SDK decodes each entry in range in the background and keeps its
first frame. On `next()`, `previous()` or `skipTo()`, the view shows that frame at once and swaps
to the video the moment the video's own first frame is on screen. Background decoding waits until
the entry you moved to is showing, so it never slows the transition down; turn the cache on too, so
the background decode and playback share one download.

**A page and a `PlayerView` per entry** (reel feeds, carousels) — `FastPixPlayerPool` supplies each
page's player, and neighbouring pages are decoded up to their first frame and held paused; your
pager and UI stay yours:

```kotlin
val pool = FastPixPlayerPool.Builder(context)
    .setPlayerConfig { it.setLoop(true).setBufferConfig(BufferConfig.FEED) }  // every player
    .setPreloadConfig(PreloadConfig(count = 3))
    .setCacheConfig(CacheConfig.enabled())
    .setPrerenderConfig(PrerenderConfig(count = 1, behind = 1))
    .build()

pool.setPlaylist(items)
viewPager.offscreenPageLimit = 1        // pages must be bound to be pre-rendered into

// when a page is bound — and again when a cached page is re-attached (see below)
playerView.player = pool.playerAt(position)

// when the pager settles on a page
pool.setCurrentIndex(position)          // plays it, pauses the page left, re-centres the window

// when the screen goes away
pool.release()
```

Keep `offscreenPageLimit` (ViewPager2) or `beyondViewportPageCount` (Compose pager) at least as large
as the pre-render counts. The pool owns its players: don't release them yourself.

**Ask for a page's player whenever the page (re)appears; don't keep it.** The pool reuses players
across pages as the user scrolls. A player an attached `PlayerView` is showing is never handed to
another page, but a page RecyclerView kept in its cache (detached) may have lost its player by the
time it comes back — ViewPager2 re-attaches cached pages without rebinding them. So compare and
rebind on attach:

```kotlin
override fun onViewAttachedToWindow(holder: PageHolder) {
    super.onViewAttachedToWindow(holder)
    val position = holder.bindingAdapterPosition
    if (position != RecyclerView.NO_POSITION && holder.player !== pool.playerAt(position)) {
        holder.bind(position)          // playerView.player = pool.playerAt(position)
    }
}
```

**Drive your own UI from the player's real state.** Pages outside the pre-render window are
stopped, and a reused player is given another entry; neither arrives as a `PlaybackListener` event.
If you draw your own spinner or play button, re-read `hasRenderedFirstFrame()` and `isPlaying()`
when the engine changes state (`getExoPlayer().addListener`, `onPlaybackStateChanged` /
`onMediaItemTransition`) rather than keeping flags from earlier callbacks. Listeners may add or
remove listeners — themselves included — from inside any callback.

The sample app's [`ViewPagerFeedActivity`](app/src/main/java/io/fastpix/app/feed/ViewPagerFeedActivity.kt)
and [`CommonVideoPlayer`](app/src/main/java/io/fastpix/app/feed/CommonVideoPlayer.kt) do all of this:
a ViewPager2 of `ComposeView` pages calling one shared player composable.

**The counts are a maximum.** Each pre-rendered entry holds a video decoder, and devices run only a
few at once — budget phones as few as two or three. Opening one too many fails *the next* player to
ask, which may be the one being watched. So the SDK tracks every open decoder across all FastPix
players, keeps one in reserve, pre-renders only as many entries as fit, and never refuses the item
playing. If the device refuses a decoder anyway, it lowers its limit for the rest of the process. A
pre-render that fails is dropped silently — the page then loads normally when reached — and
DRM-protected entries are never pre-rendered.

Hide your own poster or placeholder at exactly the right moment with
`PlaybackListener.onFirstFrameRendered()`, or check `pool.isFirstFrameRendered(position)`. For a
pre-rendered page it has already fired by the time the user arrives.

An entry whose video itself opens on black still opens on black: pre-rendering shows its real first
frame. What it removes is the black the *player* adds while the video starts.

### Migrating from 2.1.0

The 2.1.0 APIs keep working and are marked deprecated, with IDE quick-fixes to the replacements.

| 2.1.0 | 2.2.0 |
|---|---|
| `setMediaItems(items)` | `setPlaylist(items.map { PlaylistItem.fromMediaItem(it) })` |
| `PreloadConfig(enabled = true)` / `PreloadConfig.FEED` | `PreloadConfig(count = 1)` |
| `CacheConfig.forOnDemandFeed()` | `CacheConfig.enabled()` — playlist caching is no longer needed |
| `FastPixPreCacher` + `PreCacheConfig` | `PreloadConfig(count = N)` with the cache on |
| Per-page players you build yourself | `FastPixPlayerPool`, which also pre-renders |
| Black frame between items on one player | `setPrerenderConfig(PrerenderConfig(count = 1))` |

### Measuring it

The sample app's **Reel Feed: ViewPager + Compose player** is the shape many apps ship: a vertical
`ViewPager2` whose pages are `ComposeView`s calling one shared player composable, with players from
`FastPixPlayerPool`. The composable only *shows* a player, so the same one works on every screen;
its HUD reports whether each page's first frame was already on screen when it arrived. See
[`ViewPagerFeedActivity`](app/src/main/java/io/fastpix/app/feed/ViewPagerFeedActivity.kt) and
[`CommonVideoPlayer`](app/src/main/java/io/fastpix/app/feed/CommonVideoPlayer.kt).

The sample app's **Episode Feed** is one player over a playlist with Prev/Next and a Preload toggle
(launch it with the `prerender` extra to pre-render too); its HUD shows milliseconds from moving to
an entry to that entry's first frame. See
[`EpisodeFeedActivity`](app/src/main/java/io/fastpix/app/EpisodeFeedActivity.kt).

---

## Recipes: Playlists & Feeds

Complete, copy-ready setups for the common ways to show more than one video. They share one rule:
**whoever shows a video decides nothing about playback.** A page or screen binds a player to a
`PlayerView`; the player (or the pool of players) decides what plays and what is prepared next.

| Your UI | Use | Recipe |
|---|---|---|
| One screen, Next / Previous (episodes, a course, a playlist) | `FastPixPlayer` + `setPlaylist` | [A](#a-one-player-with-next--previous) |
| Vertical or horizontal feed with `ViewPager2`, pages are Views | `FastPixPlayerPool` | [B](#b-viewpager2-with-view-pages) |
| `ViewPager2` whose pages are `ComposeView`s | `FastPixPlayerPool` | [C](#c-viewpager2-with-compose-pages) |
| Compose `VerticalPager` / `HorizontalPager` | `FastPixPlayerPool` | [D](#d-compose-verticalpager--horizontalpager) |
| `RecyclerView` with `PagerSnapHelper` | `FastPixPlayerPool` | [E](#e-recyclerview-with-pagersnaphelper) |
| A single video on its own screen | `FastPixPlayer` | [F](#f-a-single-video-anywhere) |

Every recipe builds its items the same way:

```kotlin
val items = videos.map { video ->
    PlaylistItem.fastPix(video.playbackId, playbackToken = video.token)   // or PlaylistItem.fromUrl(url)
}
```

### A. One player with Next / Previous

One `PlayerView`, one player, a playlist. `PrerenderConfig` makes Next / Previous show the entry's
first frame at once instead of a black frame.

```kotlin
class EpisodesActivity : AppCompatActivity() {

    private lateinit var player: FastPixPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_episodes)   // contains <io.fastpix.media3.PlayerView android:id="@+id/playerView" .../>

        player = FastPixPlayer.Builder(this)
            .setAutoplay(true)
            .setPreloadConfig(PreloadConfig(count = 2, behind = 1))
            .setCacheConfig(CacheConfig.enabled())
            .setPrerenderConfig(PrerenderConfig(count = 1, behind = 1))
            .build()

        findViewById<PlayerView>(R.id.playerView).player = player
        player.setPlaylist(items, startIndex = 0)

        findViewById<View>(R.id.next).setOnClickListener { player.next() }
        findViewById<View>(R.id.previous).setOnClickListener { player.previous() }

        player.addPlaylistListener(object : PlaylistListener {
            override fun onPlaylistItemChanged(index: Int, item: PlaylistItem, reason: PlaylistItemChangeReason) {
                title = "Episode ${index + 1}"
            }
        })
    }

    override fun onPause() { super.onPause(); player.pause() }
    override fun onResume() { super.onResume(); player.play() }
    override fun onDestroy() { player.release(); super.onDestroy() }
}
```

The player moves to the next entry by itself when one ends (unless `loop` is on). Add entries as
they load with `player.addToPlaylist(moreItems)`.

### B. ViewPager2 with View pages

A reel feed: one page per video, each page its own `PlayerView`, players from the pool.

```kotlin
class ReelsActivity : AppCompatActivity() {

    private lateinit var pool: FastPixPlayerPool

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pool = FastPixPlayerPool.Builder(this)
            .setPlayerConfig { it.setLoop(true).setBufferConfig(BufferConfig.FEED) }
            .setPreloadConfig(PreloadConfig(count = 3, behind = 1))
            .setCacheConfig(CacheConfig.enabled())
            .setPrerenderConfig(PrerenderConfig(count = 1, behind = 1))
            .build()
        pool.setPlaylist(items)

        val pager = ViewPager2(this).apply {
            orientation = ViewPager2.ORIENTATION_VERTICAL
            offscreenPageLimit = 1                       // ≥ pre-render counts: neighbours need views
            adapter = ReelAdapter(pool)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = pool.setCurrentIndex(position)
            })
        }
        setContentView(pager)
    }

    override fun onPause() {
        super.onPause()
        pool.getCurrentIndex().takeIf { it >= 0 }?.let { pool.playerAt(it).pause() }
    }

    override fun onResume() {
        super.onResume()
        pool.getCurrentIndex().takeIf { it >= 0 }?.let { pool.playerAt(it).play() }
    }

    override fun onDestroy() { pool.release(); super.onDestroy() }
}

class ReelAdapter(private val pool: FastPixPlayerPool) : RecyclerView.Adapter<ReelAdapter.Holder>() {

    class Holder(val playerView: PlayerView) : RecyclerView.ViewHolder(playerView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = PlayerView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            resizeMode = ResizeMode.ZOOM
            // Pages share one layout; the pool, not the view, keeps players across changes.
            retainPlayerOnConfigChange = false
        }
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.playerView.player = pool.playerAt(position)
    }

    // ViewPager2 re-attaches cached pages without rebinding them; the pool may have reused their
    // player meanwhile, so ask again.
    override fun onViewAttachedToWindow(holder: Holder) {
        super.onViewAttachedToWindow(holder)
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return
        val player = pool.playerAt(position)
        if (holder.playerView.player !== player) holder.playerView.player = player
    }

    override fun getItemCount() = pool.getPlaylist().size
}
```

Inflating pages from XML instead? Give the `PlayerView` no `android:id`, or set
`retainPlayerOnConfigChange = false` on it: every page inflates the same id, and ids are what
configuration-change retention keys on.

For a horizontal carousel, use `ViewPager2.ORIENTATION_HORIZONTAL` — nothing else changes.

### C. ViewPager2 with Compose pages

The same feed where each page is a `ComposeView` calling your app's shared player composable —
the composable only *shows* the player it is given.

```kotlin
@Composable
fun AppVideoPlayer(player: FastPixPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                resizeMode = ResizeMode.ZOOM
                this.player = player
            }
        },
        update = { view -> view.player = player },   // a recycled page gets another player
        modifier = modifier,
    )
}

class ComposeReelAdapter(private val pool: FastPixPlayerPool) :
    RecyclerView.Adapter<ComposeReelAdapter.Holder>() {

    class Holder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView) {
        var player: FastPixPlayer? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ComposeView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = bind(holder, position)

    override fun onViewAttachedToWindow(holder: Holder) {
        super.onViewAttachedToWindow(holder)
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION && holder.player !== pool.playerAt(position)) {
            bind(holder, position)
        }
    }

    private fun bind(holder: Holder, position: Int) {
        val player = pool.playerAt(position)
        holder.player = player
        holder.composeView.setContent {
            AppVideoPlayer(player, Modifier.fillMaxSize())
        }
    }

    override fun getItemCount() = pool.getPlaylist().size
}
```

The pager and activity are exactly as in recipe B. The sample app's
[`CommonVideoPlayer`](app/src/main/java/io/fastpix/app/feed/CommonVideoPlayer.kt) extends
`AppVideoPlayer` with a spinner, a play icon and an error message, driven from the player's state;
[`ViewPagerFeedActivity`](app/src/main/java/io/fastpix/app/feed/ViewPagerFeedActivity.kt) is this
recipe end to end.

### D. Compose VerticalPager / HorizontalPager

All Compose, no ViewPager2:

```kotlin
@Composable
fun ReelFeed(items: List<PlaylistItem>) {
    val context = LocalContext.current
    val pool = remember {
        FastPixPlayerPool.Builder(context)
            .setPlayerConfig { it.setLoop(true).setBufferConfig(BufferConfig.FEED) }
            .setPreloadConfig(PreloadConfig(count = 3, behind = 1))
            .setCacheConfig(CacheConfig.enabled())
            .setPrerenderConfig(PrerenderConfig(count = 1, behind = 1))
            .build()
            .apply { setPlaylist(items) }
    }
    DisposableEffect(pool) { onDispose { pool.release() } }

    // Pause in the background, resume in the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, pool) {
        val observer = LifecycleEventObserver { _, event ->
            val current = pool.getCurrentIndex().takeIf { it >= 0 } ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> pool.playerAt(current).pause()
                Lifecycle.Event.ON_RESUME -> pool.playerAt(current).play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pagerState = rememberPagerState { items.size }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { pool.setCurrentIndex(it) }
    }

    VerticalPager(                                   // or HorizontalPager
        state = pagerState,
        beyondViewportPageCount = 1,                 // ≥ pre-render counts: neighbours need views
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val player = remember(page) { pool.playerAt(page) }
        AppVideoPlayer(player, Modifier.fillMaxSize())   // from recipe C
    }
}
```

Compose disposes pages that leave the pager, so there is no re-attach case to handle here.

### E. RecyclerView with PagerSnapHelper

The adapter from recipe B works unchanged. What ViewPager2 did for you — telling the pool which
page is current — you do from the snap:

```kotlin
// Lay out one screen beyond each edge, so the neighbouring pages are bound — and pre-rendered —
// before they scroll into view. This is what offscreenPageLimit does for ViewPager2.
recyclerView.layoutManager = object : LinearLayoutManager(this, RecyclerView.VERTICAL, false) {
    override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
        extraLayoutSpace[0] = recyclerView.height
        extraLayoutSpace[1] = recyclerView.height
    }
}
val snapHelper = PagerSnapHelper().apply { attachToRecyclerView(recyclerView) }
recyclerView.adapter = ReelAdapter(pool)

recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        if (newState != RecyclerView.SCROLL_STATE_IDLE) return
        val snapped = snapHelper.findSnapView(recyclerView.layoutManager) ?: return
        val position = recyclerView.getChildAdapterPosition(snapped)
        if (position != RecyclerView.NO_POSITION) pool.setCurrentIndex(position)
    }
})
recyclerView.post { pool.setCurrentIndex(0) }
```

Without the extra layout space a RecyclerView binds the next item only as it scrolls into view,
too late to pre-render it.

### F. A single video anywhere

A detail screen, a preview, a banner — one player, owned by the screen:

```kotlin
// Views
val player = FastPixPlayer.Builder(context).setAutoplay(true).build()
playerView.player = player
player.setFastPixMediaItem { playbackId = "your-playback-id" }
// ...and player.release() when the screen goes away.

// Compose
@Composable
fun VideoDetail(item: PlaylistItem) {
    val context = LocalContext.current
    val player = remember(item) {
        FastPixPlayer.Builder(context).setAutoplay(true).build()
            .apply { setPlaylist(listOf(item)) }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AppVideoPlayer(player, Modifier.fillMaxWidth().aspectRatio(16f / 9f))   // from recipe C
}
```

### Things every recipe relies on

- **Showing a player never releases it.** A `PlayerView` releases only a player it created itself;
  one you assign stays yours (or the pool's), however often views detach and re-attach.
- **Your UI should read the player's real state.** Pool players are stopped outside the pre-render
  window and reused for other entries without `PlaybackListener` events; if you draw your own
  spinner or play button, re-read `hasRenderedFirstFrame()` / `isPlaying()` when the engine changes
  state (see [`CommonVideoPlayer`](app/src/main/java/io/fastpix/app/feed/CommonVideoPlayer.kt)).
  Hide a poster on `PlaybackListener.onFirstFrameRendered()`.
- **Listeners may remove themselves inside a callback** — a one-shot listener is safe.
- **Pre-render counts are a maximum.** The device's decoder limit decides how many actually
  pre-render; DRM entries never are.
- **Analytics counts watched videos only.** Add `setAnalyticsConfig(AnalyticsConfig.Builder(workSpaceId).build())`
  to a pool (or a config to a player) and each video the user lands on is one view; preloaded and
  pre-rendered entries are not. See [One view per video watched](#one-view-per-video-watched).

---

## Analytics (FastPix Data Core SDK)

The FastPix Android Data Core SDK provides analytics for FastPix playback on Android. It is **not** a standalone video player. Instead, it integrates with your player and automatically captures playback analytics, including:

- Playback lifecycle events (play, pause, ready, complete, errors)
- Buffering behavior and seek patterns
- Engagement signals and session-level playback usage

The SDK sends collected analytics to your FastPix workspace, where you can monitor them in near real time through the FastPix dashboard. The integration is lightweight and does not interrupt or degrade playback.

### What it is for

Use analytics when you want to:
- Measure viewer engagement and completion trends
- Detect buffering and quality-of-experience issues
- Correlate playback behavior with video metadata (for example, title and ID,etc)
- Monitor player health and failures in production

### How to use it

Configure analytics using `AnalyticsConfig` and pass it to `FastPixPlayer.Builder`.

```kotlin
import io.fastpix.data.domain.model.VideoDataDetails
import io.fastpix.media3.analytics.AnalyticsConfig
import io.fastpix.media3.core.FastPixPlayer

val videoDataDetails = VideoDataDetails("video-123", "Launch Demo")

val analyticsConfig = AnalyticsConfig.Builder(
    playerView = binding.playerView,         // Required
    workSpaceId = "your-workspace-id"        // Required
)
    .setVideoDataDetails(videoDataDetails) // Optional metadata
    .setEnabled(true) // default is true
    .build()

val fastPixPlayer = FastPixPlayer.Builder(this)
    .setAutoplay(true)
    .setLoop(false)
    .setAnalyticsConfig(analyticsConfig)
    .build()

binding.playerView.player = fastPixPlayer
```

### One view per video watched

Analytics reports a **view** for each video the user actually watches:

- A view begins when a video starts playing for the user — set with `setMediaItem` /
  `setFastPixMediaItem`, reached in a playlist (`setPlaylist`, `next()`, `previous()`, `skipTo()`,
  auto-advance), or made current in a `FastPixPlayerPool` (`setCurrentIndex`).
- It ends when playback moves to another video, the playlist is cleared, or the player is released.
- Entries that are only preloaded or pre-rendered are **never** reported.

Give each playlist entry its own metadata; `setVideoDataDetails` on the config is the fallback for
entries without any, and failing that the entry's id is reported:

```kotlin
player.setPlaylist(
    episodes.map { episode ->
        PlaylistItem.fastPix(episode.playbackId)
            .withVideoData(VideoDataDetails(videoId = episode.id, videoTitle = episode.title))
    }
)
```

### Analytics in a feed (`FastPixPlayerPool`)

A pool's players move between pages, so build the config **without** a view — each page's view is
measured through the `PlayerView` showing it — and give it to the pool:

```kotlin
val pool = FastPixPlayerPool.Builder(context)
    .setAnalyticsConfig(AnalyticsConfig.Builder("your-workspace-id").build())
    .setPrerenderConfig(PrerenderConfig(count = 1, behind = 1))
    .build()

pool.setPlaylist(
    reels.map { reel ->
        PlaylistItem.fastPix(reel.playbackId)
            .withVideoData(VideoDataDetails(videoId = reel.id, videoTitle = reel.caption))
    }
)
```

Each page the user lands on is one view; swiping away ends it. Pages pre-rendered off screen are not
views, so pre-rendering does not inflate view counts.

### Notes

- `workSpaceId` is mandatory. `playerView` is optional: leave it out (`AnalyticsConfig.Builder(workSpaceId)`)
  when the player is shown in different views over time, as in a pool.
- `videoDataDetails`, `playerDataDetails`, and `customDataDetails` are optional and can be added based on your use case.
- The FastPix Data SDK reports one view at a time per app: beginning a view on one player ends any
  view still open on another.
- Since 2.2.0 a view begins when the first video starts, not when the player is built, so a player
  that never plays anything reports nothing.
- If analytics setup fails at runtime, playback continues. Analytics is fail-safe by design.
- Current analytics APIs are optimized for Java-first Android integration. Kotlin ergonomics and customization options will improve in future releases.

---

## Seek Preview (Spritesheet thumbnails)

Seek preview lets you show **thumbnail previews while the user scrubs** your seek bar. When enabled, the SDK automatically attempts to resolve the default FastPix spritesheet URL from the currently loaded stream URL:

- Stream URL: `https://stream.fastpix.com/{playbackId}.m3u8`
- Spritesheet metadata: `https://images.fastpix.com/{playbackId}/spritesheet.json`

If no spritesheet exists (or the current media URL is not a FastPix stream), the SDK falls back based on `PreviewFallbackMode` (default: timestamp).

### 1. Enable seek preview on the player

```kotlin
import io.fastpix.media3.FastPixPlayer
import io.fastpix.player.seekpreview.models.PreviewFallbackMode
import io.fastpix.player.seekpreview.models.SeekPreviewConfig

val player = FastPixPlayer.Builder(context)
    .setSeekPreviewConfig(
        SeekPreviewConfig.Builder()
            .setEnabled(true)
            .setFallbackMode(PreviewFallbackMode.TIMESTAMP)
            .setEnablePreload(true)
            .setPreloadRadius(1)
            .setCacheEnabled(true)
            .build()
    )
    .build()

playerView.player = player
```

### 2. Listen for preview frames and update your UI

```kotlin
import io.fastpix.player.seekpreview.listeners.SeekPreviewListenerAdapter
import io.fastpix.player.seekpreview.models.SpritesheetMetadata

player.setSeekPreviewListener(object : SeekPreviewListenerAdapter() {
    override fun onPreviewShow() {
        previewContainer.visibility = View.VISIBLE
    }

    override fun onPreviewHide() {
        previewContainer.visibility = View.GONE
    }

    override fun onSpritesheetLoaded(metadata: SpritesheetMetadata) {
        // metadata.bitmap can be null when thumbnails are unavailable (timestamp fallback).
        previewImageView.setImageBitmap(metadata.bitmap)
        val ts = metadata.timestampMs ?: 0L
        previewTimeTextView.text = formatTime(ts) // implement your own MM:SS formatter
    }
})
```

### 3. Wire seek preview to your SeekBar scrubbing

Call these methods from your `SeekBar.OnSeekBarChangeListener`:

- `showPreview()` when the user starts dragging
- `loadPreview(positionMs)` while dragging (safe to call frequently)
- `hidePreview()` when the user stops dragging

```kotlin
seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
    override fun onStartTrackingTouch(seekBar: SeekBar) {
        player.showPreview()
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            player.loadPreview(progress.toLong()) // progress in ms (recommended)
        }
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        player.hidePreview()
        player.seekTo(seekBar.progress.toLong())
    }
})
```

---

## Core API

### PlayerView

The main view component that wraps ExoPlayer and provides a clean API.

#### Media Management

```kotlin
// Set a single media item with direct URL
playerView.setMediaItem(MediaItem.fromUri("https://example.com/video.mp4"))

// Set a FastPix media item using builder pattern (recommended for FastPix streams)
playerView.setFastPixMediaItem {
    playbackId = "your-playback-id"
    maxResolution = PlaybackResolution.FHD_1080
    playbackToken = "your-token" // Optional, for secure playback
}
```

#### Video Scaling

```kotlin
playerView.resizeMode = ResizeMode.ZOOM   // fill the view, cropping the overflow
```

Or in XML:

```xml
<io.fastpix.media3.PlayerView
    android:id="@+id/playerView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:fastPixResizeMode="zoom" />
```

| Mode | Behaviour |
|---|---|
| `FIT` (default) | Scale to fit inside the view, letterboxing as needed |
| `FIXED_WIDTH` | Match the view's width; height follows the content |
| `FIXED_HEIGHT` | Match the view's height; width follows the content |
| `FILL` | Stretch to fill, ignoring aspect ratio |
| `ZOOM` | Fill while preserving aspect ratio, cropping the overflow |

`FIT` is right for a general-purpose player, where cropping would hide content. Full-screen feeds
normally want `ZOOM` so a source of any shape fills the page the way short-form apps present it.

#### Playback Control

```kotlin
// Playback control
playerView.play()                    // Start or resume playback
playerView.pause()                   // Pause playback
playerView.togglePlayPause()         // Toggle between play and pause
val isPlaying = playerView.isPlaying() // Check if currently playing

// Seek control
playerView.seekTo(positionMs = 5000) // Seek to 5 seconds

// Playback state
val currentPosition = playerView.getCurrentPosition() // Current position in ms
val duration = playerView.getDuration()               // Total duration in ms
val playbackState = playerView.getPlaybackState()    // Player state constant

// Volume control
playerView.setVolume(0.5f)           // Set volume (0.0f = muted, 1.0f = max)
val volume = playerView.getVolume()  // Get current volume level
playerView.mute()                    // Mute playback (saves volume for restoration)
playerView.unmute()                  // Restore previous volume level

// Playback speed control
playerView.setPlaybackSpeed(1.5f)    // Set playback speed (e.g., 1.5x)
val speed = playerView.getPlaybackSpeed() // Get current playback speed
val availableSpeeds = playerView.getAvailablePlaybackSpeeds() // Get all available speeds
```

#### Configuration

```kotlin
// Enable/disable configuration change survival (default: true)
playerView.retainPlayerOnConfigChange = true

// Enable/disable tap gesture for play/pause (default: true)
playerView.isTapGestureEnabled = true

    // Set whether playback should start automatically when ready
    playerView.setPlayWhenReady(true)
    val playWhenReady = playerView.getPlayWhenReady()
    
    // Configure loop and autoplay (using FastPixPlayer.Builder)
    val player = FastPixPlayer.Builder(context)
        .setLoop(true)      // Enable looping
        .setAutoplay(true)  // Enable autoplay
        .build()
    playerView.player = player
    
    // Or configure at runtime
    player.loop = true
    player.autoplay = true
```

#### Event Listeners

```kotlin
// Add playback listener
playerView.addPlaybackListener(playbackListener)

// Remove playback listener
playerView.removePlaybackListener(playbackListener)

// Clear all listeners
playerView.clearPlaybackListeners()
```

#### Advanced Access

```kotlin
// Get underlying ExoPlayer instance for advanced usage
val exoPlayer = playerView.getPlayer()

// For audio/subtitle track switching, use FastPixPlayer (when view uses FastPixPlayer)
val fastPixPlayer = playerView.player as? FastPixPlayer
fastPixPlayer?.getAudioTracks()
fastPixPlayer?.getSubtitleTracks()
// See "Subtitle and Audio Track Switching" section for full API.
```

---

## FastPix Media Items

The SDK provides a builder pattern for creating FastPix media items with advanced configuration options. This is the recommended way to play FastPix streams.

### Basic Usage

```kotlin
import io.fastpix.media3.core.PlaybackResolution

// Simple usage with just playback ID
playerView.setFastPixMediaItem {
    playbackId = "your-playback-id"
}
```

### Advanced Configuration

```kotlin
playerView.setFastPixMediaItem {
    playbackId = "your-playback-id"
    
    // Resolution options
    maxResolution = PlaybackResolution.FHD_1080  // Maximum resolution
    minResolution = PlaybackResolution.HD_720     // Minimum resolution
    resolution = PlaybackResolution.FHD_1080     // Fixed resolution
    
    // Adaptive streaming
    renditionOrder = RenditionOrder.Descending   // Quality preference order
    
    // Custom domain (defaults to "stream.fastpix.com")
    customDomain = "custom.stream.fastpix.com"
    
    // Stream type
    streamType = "on-demand"  // or "live-stream"
    
    // Secure playback
    playbackToken = "your-playback-token"
}
```

### Playback Resolution Options

```kotlin
enum class PlaybackResolution {
    LD_480,      // 480p
    LD_540,      // 540p
    HD_720,      // 720p
    FHD_1080,    // 1080p
    QHD_1440,    // 1440p
    FOUR_K_2160  // 2160p (4K)
}
```

### Rendition Order

Controls the order of preference for adaptive streaming:

```kotlin
enum class RenditionOrder {
    Descending,  // Prefer higher quality first
    Ascending,   // Prefer lower quality first
    Default      // Use default order
}
```

### Complete Example

```kotlin
class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoPlayerBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupPlayer()
    }
    
    private fun setupPlayer() {
        // Configure FastPix media item with builder
        val success = binding.playerView.setFastPixMediaItem {
            playbackId = "your-playback-id"
            maxResolution = PlaybackResolution.FHD_1080
            playbackToken = "your-token" // Optional
        }
        
        if (!success) {
            // Handle error (e.g., invalid playback ID)
            Toast.makeText(this, "Failed to load video", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Add playback listener
        binding.playerView.addPlaybackListener(object : PlaybackListener {
            override fun onPlay() {
                // Playback started
            }
            
            override fun onError(error: PlaybackException) {
                // Handle playback error
            }
        })
        
        // Start playback
        binding.playerView.setPlayWhenReady(true)
    }
}
```

### Error Handling

The `setFastPixMediaItem` method returns `true` if the media item was successfully set, or `false` if there was an error. Errors are automatically reported through the `PlaybackListener.onError()` callback.

Common errors:
- Empty playback ID
- Invalid stream type (must be "on-demand" or "live-stream")
- Invalid playback token (if provided)

---

## PlaybackListener

Interface for receiving playback events and time updates.

### Callbacks

```kotlin
interface PlaybackListener {
    fun onPlay()                                    // Called when playback starts/resumes
    fun onPause()                                   // Called when playback is paused
    fun onPlaybackStateChanged(isPlaying: Boolean) // Called when play/pause state changes
    fun onError(error: PlaybackException)          // Called when a playback error occurs
    
    // Time updates (called periodically during playback)
    fun onTimeUpdate(
        currentPositionMs: Long,
        durationMs: Long,
        bufferedPositionMs: Long
    )
    
    // Seek callbacks
    fun onSeekStart(currentPositionMs: Long)        // Called when seek starts
    fun onSeekEnd(
        fromPositionMs: Long,
        toPositionMs: Long,
        durationMs: Long
    )                                               // Called when seek completes
    
    // Buffering callbacks
    fun onBufferingStart()                          // Called when buffering starts
    fun onBufferingEnd()                            // Called when buffering ends
    fun onFirstFrameRendered()                      // First video frame is on screen (hide your poster)
    
    // Volume callbacks
    fun onVolumeChanged(volumeLevel: Float)         // Called when device volume changes
    fun onMuteStateChanged(isMuted: Boolean)       // Called when mute state changes
    
    // Playback rate callback
    fun onPlaybackRateChanged(rate: Float)         // Called when playback speed changes
    
    // Completion callback
    fun onCompleted()                               // Called when video playback completes (reaches the end)
}
```

### Example Usage

```kotlin
val listener = object : PlaybackListener {
    override fun onPlay() {
        // Update play button UI
    }
    
    override fun onPause() {
        // Update pause button UI
    }
    
    override fun onTimeUpdate(
        currentPositionMs: Long,
        durationMs: Long,
        bufferedPositionMs: Long
    ) {
        // Update seek bar and time displays
        seekBar.progress = currentPositionMs.toInt()
        seekBar.max = durationMs.toInt()
        seekBar.secondaryProgress = bufferedPositionMs.toInt()
        
        currentTimeTextView.text = formatTime(currentPositionMs)
        durationTextView.text = formatTime(durationMs)
    }
    
    override fun onError(error: PlaybackException) {
        // Show error message to user
        Toast.makeText(context, "Playback error: ${error.message}", Toast.LENGTH_LONG).show()
    }
    
    override fun onSeekStart(currentPositionMs: Long) {
        // Pause time updates UI or show seeking indicator
    }
    
    override fun onSeekEnd(
        fromPositionMs: Long,
        toPositionMs: Long,
        durationMs: Long
    ) {
        // Resume time updates UI or hide seeking indicator
    }
    
    override fun onBufferingStart() {
        // Show buffering indicator
    }
    
    override fun onBufferingEnd() {
        // Hide buffering indicator
    }
    
    override fun onVolumeChanged(volumeLevel: Float) {
        // Update volume UI when device volume changes
        volumeSlider.progress = (volumeLevel * 100).toInt()
    }
    
    override fun onMuteStateChanged(isMuted: Boolean) {
        // Update mute icon
        muteButton.setImageResource(if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on)
    }
    
    override fun onPlaybackRateChanged(rate: Float) {
        // Update playback speed UI
        speedButton.text = "${rate}x"
    }
}

playerView.addPlaybackListener(listener)
```

---

## Volume Control

FastPix Player SDK provides comprehensive volume control with support for programmatic volume adjustment, mute/unmute functionality, and automatic device volume monitoring.

### Basic Usage

```kotlin
// Set volume level (0.0f = muted, 1.0f = maximum)
playerView.setVolume(0.75f)

// Get current volume level
val currentVolume = playerView.getVolume()

// Mute playback (saves current volume for restoration)
playerView.mute()

// Unmute and restore previous volume
playerView.unmute()
```

### Volume Change Monitoring

The SDK automatically monitors device volume changes (via hardware buttons or system controls) and notifies listeners:

```kotlin
playerView.addPlaybackListener(object : PlaybackListener {
    override fun onVolumeChanged(volumeLevel: Float) {
        // Called when device volume changes
        // volumeLevel is between 0.0f (muted) and 1.0f (maximum)
        updateVolumeUI(volumeLevel)
    }
    
    override fun onMuteStateChanged(isMuted: Boolean) {
        // Called when mute state changes
        updateMuteIcon(isMuted)
    }
})
```

### Volume Control Features

- **Volume Range**: 0.0f (muted) to 1.0f (maximum volume)
- **Mute/Unmute**: Smart mute that saves volume level for restoration
- **Device Volume Monitoring**: Automatic detection of hardware volume button changes
- **State Preservation**: Volume state is preserved across configuration changes

---

## AutoPlay

AutoPlay allows playback to start automatically when the media is ready, without requiring a manual call to `play()`.

### Configuration

AutoPlay can be configured during player creation using the builder pattern:

```kotlin
val player = FastPixPlayer.Builder(context)
    .setAutoplay(true)  // Enable autoplay
    .build()

playerView.player = player
```

### Runtime Configuration

You can also enable or disable autoplay at runtime:

```kotlin
// Enable autoplay
player.autoplay = true

// Disable autoplay
player.autoplay = false

// Check current autoplay state
val isAutoplayEnabled = player.autoplay
```

### Behavior

- When `autoplay = true`: Playback automatically starts when media is ready
- When `autoplay = false`: Playback must be started manually via `play()` or `setPlayWhenReady(true)`
- Autoplay state is preserved across configuration changes

---

## Loop Playback

Loop playback enables the video to automatically restart from the beginning when it reaches the end, creating a seamless continuous playback experience.

### Configuration

Loop can be configured during player creation using the builder pattern:

```kotlin
val player = FastPixPlayer.Builder(context)
    .setLoop(true)  // Enable looping
    .build()

playerView.player = player
```

### Runtime Configuration

You can also enable or disable looping at runtime:

```kotlin
// Enable looping
player.loop = true

// Disable looping
player.loop = false

// Check current loop state
val isLooping = player.loop
```

### Behavior

- When `loop = true`: Playback automatically restarts from the beginning when it reaches the end
- When `loop = false`: Playback stops when it reaches the end
- Loop state is preserved across configuration changes
- The `onCompleted()` callback is still triggered when the video reaches the end, even with looping enabled

---

## Playback Rate Control

Playback rate control allows users to adjust the playback speed from 0.25x (slow motion) to 2.0x (double speed), providing flexibility for different viewing preferences.

### Available Playback Speeds

The SDK supports the following playback speeds:
- **0.25x** - Quarter speed (slow motion)
- **0.5x** - Half speed
- **0.75x** - Three-quarter speed
- **1.0x** - Normal speed (default)
- **1.25x** - 1.25x speed
- **1.5x** - 1.5x speed
- **1.75x** - 1.75x speed
- **2.0x** - Double speed

### Basic Usage

```kotlin
// Set playback speed to 1.5x
playerView.setPlaybackSpeed(1.5f)

// Get current playback speed
val currentSpeed = playerView.getPlaybackSpeed()

// Get all available playback speeds
val availableSpeeds = playerView.getAvailablePlaybackSpeeds()
// Returns: [0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f]
```

### Playback Speed Change Monitoring

Listen for playback speed changes:

```kotlin
playerView.addPlaybackListener(object : PlaybackListener {
    override fun onPlaybackRateChanged(rate: Float) {
        // Called when playback speed changes
        // rate is the new playback speed (e.g., 1.5f for 1.5x)
        updateSpeedUI(rate)
    }
})
```

### Example: Speed Selection Menu

```kotlin
private fun showPlaybackSpeedMenu() {
    val popupMenu = PopupMenu(this, speedButton)
    val availableSpeeds = playerView.getAvailablePlaybackSpeeds()
    val currentSpeed = playerView.getPlaybackSpeed()
    
    availableSpeeds.forEachIndexed { index, speed ->
        val speedLabel = if (speed == speed.toInt().toFloat()) {
            "${speed.toInt()}x"
        } else {
            String.format("%.2fx", speed).trimEnd('0').trimEnd('.')
        }
        
        val menuItem = popupMenu.menu.add(0, index, 0, speedLabel)
        if (kotlin.math.abs(speed - currentSpeed) < 0.01f) {
            menuItem.isChecked = true
        }
    }
    
    popupMenu.menu.setGroupCheckable(0, true, true)
    
    popupMenu.setOnMenuItemClickListener { item ->
        val selectedSpeed = availableSpeeds[item.itemId]
        playerView.setPlaybackSpeed(selectedSpeed)
        true
    }
    
    popupMenu.show()
}
```

### Features

- **Automatic Speed Adjustment**: If an exact speed is not available, the SDK automatically selects the closest available speed
- **State Preservation**: Playback speed is preserved across configuration changes
- **Pitch Preservation**: Audio pitch remains normal at all speeds (no chipmunk effect)

---

## Video Quality Switching

Use these APIs when you want users to pick a fixed quality (for example, `1080p`) or switch back to adaptive bitrate (ABR) auto mode.

These APIs are available on `FastPixPlayer`.

### Get available and current video qualities

```kotlin
val player = binding.playerView.player as? FastPixPlayer ?: return

// All available video quality tracks for current media
val qualities: List<VideoTrack> = player.getVideoQualities()

// Current active quality (in auto mode this reflects currently rendered quality)
val current: VideoTrack? = player.getCurrentVideoQuality()
```

`VideoTrack` exposes: `id`, `width`, `height`, `bitrate`, `label`, `isSelected`, and `isAuto`.

### Switch to a fixed quality

```kotlin
// Pick a quality id from getVideoQualities()
player.setVideoQuality(trackId)
```

### Switch back to auto (ABR)

```kotlin
player.enableAutoQuality()
```

### Listen for quality changes

```kotlin
player.addPlaybackListener(object : PlaybackListener {
    override fun onVideoQualityChanged(
        quality: VideoTrack?,
        source: PlaybackListener.VideoQualityChangeSource
    ) {
        // source = MANUAL when you call setVideoQuality/enableAutoQuality
        // source = ABR when SDK switches rendition automatically
        updateQualityUi(quality, source)
    }

    override fun onPlay() {}
    override fun onPause() {}
    override fun onPlaybackStateChanged(isPlaying: Boolean) {}
    override fun onError(error: PlaybackException) {}
})
```

Notes:
- Quality switching does not recreate the player or reset playback position.
- If a seek is in progress, quality change is safely applied after seek completes.
- Keep an `"Auto"` option in your quality menu that calls `enableAutoQuality()`.

---

## DRM (Widevine) Playback

For protected FastPix streams, provide both:
- `playbackToken` (required for secure playback)
- `drmConfig` (enables DRM configuration on the media item)

If `playbackToken` is set but `drmConfig` is not provided, playback emits a DRM configuration error through `PlaybackListener.onError`.

### Basic DRM setup

```kotlin
import io.fastpix.media3.core.DrmConfig
import io.fastpix.media3.core.FastPixPlayer
import io.fastpix.media3.core.StreamType

val player = FastPixPlayer.Builder(this)
    .setAutoplay(true)
    .build()

binding.playerView.player = player

player.setFastPixMediaItem {
    playbackId = "your-playback-id"
    streamType = StreamType.onDemand // Use StreamType.live for live streams
    playbackToken = "your-secure-playback-token"
    drmConfig = DrmConfig() // Defaults to Widevine UUID, multiSession=true
}
```

### Custom DRM options

```kotlin
import androidx.media3.common.C
import io.fastpix.media3.core.DrmConfig

player.setFastPixMediaItem {
    playbackId = "your-playback-id"
    playbackToken = "your-secure-playback-token"
    drmConfig = DrmConfig(
        uuid = C.WIDEVINE_UUID,
        multiSession = true
    )
}
```

Notes:
- Current SDK DRM flow targets Widevine-protected FastPix HLS playback.
- `streamType` helps resolve the correct FastPix DRM license endpoint (`on-demand` or `live`).
- Always test DRM streams on real devices and target Android API levels you support.

---

## Subtitle and Audio Track Switching

The SDK supports discovering and switching **audio** and **subtitle** tracks for media that provides multiple tracks (e.g. HLS with alternate audio or closed captions). These APIs are available on **FastPixPlayer**; use `playerView.player as FastPixPlayer` when your view is backed by a FastPixPlayer instance.

### Audio track switching

#### Get available and current audio tracks

```kotlin
val player = binding.playerView.player as? FastPixPlayer ?: return

// All available audio tracks for the current media
val audioTracks: List<AudioTrack> = player.getAudioTracks()

// Currently selected audio track (null if only one track or none)
val current: AudioTrack? = player.getCurrentAudioTrack()
```

#### Switch audio track

```kotlin
// Switch to the track with the given id (from AudioTrack.id)
player.setAudioTrack(trackId)
```

- Does not restart playback; position and state are preserved.
- If a seek is in progress, the switch is applied when the seek completes.
- Safe to call when paused or buffering.

#### Default audio language

Set a preferred language so it is applied automatically when tracks become available (e.g. after loading new media). Manual selection is never overridden.

```kotlin
player.setDefaultAudioTrack("hi")  // e.g. Hindi
```

#### Audio track listener

```kotlin
import io.fastpix.media3.tracks.AudioTrackListener
import io.fastpix.media3.tracks.AudioTrackUpdateReason

player.addAudioTrackListener(object : AudioTrackListener {
    override fun onAudioTracksLoaded(
        tracks: List<AudioTrack>,
        reason: AudioTrackUpdateReason
    ) {
        // reason: INITIAL, MEDIA_CHANGED, or TRACKS_UPDATED
        updateAudioTrackMenu(tracks)
    }

    override fun onAudioTracksChange(selectedTrack: AudioTrack) {
        updateSelectedAudioUI(selectedTrack)
    }

    override fun onAudioTracksLoadedFailed(error: AudioTrackError) {
        // Handle TrackNotFound, TrackNotPlayable, SelectionFailed, PlayerNotReady
        showError(error)
    }

    override fun onAudioTrackSwitching(isSwitching: Boolean) {
        if (isSwitching) showSpinner()
        else hideSpinner()
    }
})

// Remove when no longer needed
player.removeAudioTrackListener(listener)
```

#### AudioTrack model

`AudioTrack` exposes: `id`, `languageCode`, `languageName`, `label`, `isSelected`, `isPlayable`, `isDefault`, and optional `role`, `channels`, `codec`, `bitrate`, `groupId`. Use `id` when calling `setAudioTrack(trackId)`.

---

### Subtitle track switching

#### Get available and current subtitle tracks

```kotlin
val player = binding.playerView.player as? FastPixPlayer ?: return

// All available subtitle tracks
val subtitleTracks: List<SubtitleTrack> = player.getSubtitleTracks()

// Currently selected subtitle track (null if none or subtitles disabled)
val current: SubtitleTrack? = player.getCurrentSubtitleTrack()
```

#### Switch subtitle track

```kotlin
// Enable a specific subtitle track by id (from SubtitleTrack.id)
player.setSubtitleTrack(trackId)
```

#### Disable subtitles

```kotlin
player.disableSubtitles()
```

- Playback position and state are preserved. Forced subtitles may still render per stream behavior.

#### Default subtitle language

```kotlin
player.setDefaultSubtitleTrack("en")  // e.g. English
```

Manual subtitle selection and “subtitles disabled” are never overridden when applying defaults.

#### Subtitle track listener

```kotlin
import io.fastpix.media3.tracks.SubtitleTrackListener
import io.fastpix.media3.tracks.SubtitleCueInfo
import io.fastpix.media3.tracks.SubtitleRenderInfo

player.addSubtitleTrackListener(object : SubtitleTrackListener {
    override fun onSubtitlesLoaded(tracks: List<SubtitleTrack>) {
        updateSubtitleTrackMenu(tracks)
    }

    override fun onSubtitleChange(track: SubtitleTrack?) {
        // track is null when subtitles are disabled
        updateSubtitleSelectionUI(track)
    }

    override fun onSubtitlesLoadedFailed(error: SubtitleTrackError) {
        // Handle TrackNotFound, TrackNotPlayable, SelectionFailed, PlayerNotReady
        showError(error)
    }

    override fun onSubtitleCueChange(info: SubtitleRenderInfo) {
        // Render current cues (e.g. in a TextView or custom overlay)
        val cues: List<SubtitleCueInfo> = info.cues
        subtitleTextView.text = cues.joinToString("\n") { it.text.toString() }
        // Use cue.startTimeMs / endTimeMs for timing if needed
    }
})

player.removeSubtitleTrackListener(listener)
```

#### SubtitleTrack model

`SubtitleTrack` exposes: `id`, `languageCode`, `languageName`, `label`, `isSelected`, `isPlayable`, `isDefault`, `isForced`, and optional `role`, `codec`, `groupId`. Use `id` with `setSubtitleTrack(trackId)`.

#### Rendering subtitle cues

`SubtitleTrackListener.onSubtitleCueChange(info: SubtitleRenderInfo)` delivers the current cues. Each `SubtitleCueInfo` has `text`, `startTimeMs`, and `endTimeMs`. Use them to drive your subtitle overlay (e.g. show/hide text per cue timing).

---

### Example: audio and subtitle menus

```kotlin
// Assume FastPixPlayer is set on PlayerView
val player = binding.playerView.player as FastPixPlayer

// Audio menu
val audioTracks = player.getAudioTracks()
audioTracks.forEach { track ->
    val label = track.label ?: track.languageName ?: track.languageCode ?: track.id
    // On click:
    player.setAudioTrack(track.id)
}

// Subtitle menu (include "Off")
val subtitleTracks = player.getSubtitleTracks()
// Add "Off" option that calls player.disableSubtitles()
subtitleTracks.forEach { track ->
    val label = track.label ?: track.languageName ?: track.languageCode ?: track.id
    // On click:
    player.setSubtitleTrack(track.id)
}
```

---

## Configuration Change Survival

By default, `PlayerView` preserves playback state across configuration changes (rotation, multi-window, etc.). This means:

- ✅ Video playback does NOT restart on rotation
- ✅ Current playback position is preserved
- ✅ Play/pause state is preserved
- ✅ Buffering state is preserved

### How It Works

The player instance is retained in an internal registry when the view is detached during configuration changes, and reattached to the same instance when the view is recreated.

### Opt-Out

If you need to disable this behavior:

```kotlin
playerView.retainPlayerOnConfigChange = false
```

When disabled:
- A player the view created itself is released when the view is detached
- Playback will restart from the beginning

Either way, a player you assign with `playerView.player = …` is yours: the view never releases it on
detach. It only unbinds the video surface, and binds it again if the view is re-attached — so the
same player can move between views, or survive a page being recycled in a pager or a Compose
`AndroidView`. Release it yourself when you are done.

---

## Fullscreen Mode

PlayerView supports fullscreen mode where the player covers the entire screen.
When entering fullscreen:
- PlayerView is detached from its original parent
- Attached to the Activity's root decor view
- System UI (status bar, navigation bar) is hidden
- Playback state and listeners are preserved

When exiting fullscreen:
- PlayerView is restored to its original parent
- System UI is restored
- Playback continues seamlessly

### Important Notes

- Fullscreen is developer-controlled, not automatic
- Fullscreen state is automatically cleaned up if the view is detached
- Supports both portrait and landscape orientations
- Does NOT force orientation changes
- Handles orientation changes while in fullscreen

---

## Lifecycle Management

PlayerView automatically handles ExoPlayer lifecycle:

- Creates player when attached to window
- Preserves player instance across configuration changes (when `retainPlayerOnConfigChange` is true)
- Releases player when view is truly destroyed (not during config changes)

### Manual Release

Call `release()` when the Activity is finishing:

```kotlin
override fun onDestroy() {
    super.onDestroy()
    if (isFinishing) {
        playerView.release()
    }
}
```

---

## Architecture

This SDK:

- ✅ Does NOT use `android:configChanges` (follows Android best practices)
- ✅ Does NOT require Activity or Fragment lifecycle ownership
- ✅ Does NOT require ViewModel usage
- ✅ Does NOT leak Activity or View references
- ✅ Uses an internal player registry to retain instances across view recreation

---

## Example App

See the `app` module for a complete example implementation demonstrating:

- Basic playback control
- Playback event listeners
- Seek bar integration
- Fullscreen mode
- Configuration change handling
