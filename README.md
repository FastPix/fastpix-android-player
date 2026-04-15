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
- **Subtitle and Audio Track Switching** – Discover and switch audio/subtitle tracks, set default languages, disable subtitles, and render subtitle cues via listeners

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
    implementation("io.fastpix.player:android:1.0.7")
}
```

Or if using version catalogs, add to `libs.versions.toml`:

```toml
[versions]
fastpix-player = "1.0.7"

[libraries]
fastpix-player = { module = "io.fastpix.player:android-player-sdk", version.ref = "fastpix-player" }
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

## Analytics (FastPix Data Core SDK)

The FastPix Android Data Core SDK is the analytics foundation for FastPix playback on Android. It is **not** a standalone video player; instead, it attaches to your player and automatically captures playback analytics such as:

- Playback lifecycle events (play, pause, ready, complete, errors)
- Buffering behavior and seek patterns
- Engagement signals and session-level playback usage

Collected analytics is sent to your FastPix workspace and can be monitored in the FastPix dashboard in near real time. The integration is designed to be lightweight so analytics tracking does not interrupt or degrade playback.

### What it is for

Use analytics when you want to:
- Measure viewer engagement and completion trends
- Detect buffering and quality-of-experience issues
- Correlate playback behavior with video metadata (title, id, etc.)
- Observe player health and failures in production

### How to use it

Analytics is configured through `AnalyticsConfig` and passed into `FastPixPlayer.Builder`.

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

### Notes

- `playerView` and `workSpaceId` are mandatory.
- `videoDataDetails`, `playerDataDetails`, and `customDataDetails` are optional and can be added based on your use case.
- If analytics setup fails at runtime, playback continues (analytics is fail-safe by design).
- Current analytics APIs are optimized for Java-first Android integration; Kotlin ergonomics and customization options will continue to improve in upcoming releases.

---

## Seek Preview (Spritesheet thumbnails)

Seek preview lets you show **thumbnail previews while the user scrubs** your seek bar. When enabled, the SDK automatically attempts to resolve the default FastPix spritesheet URL from the currently loaded stream URL:

- Stream URL: `https://stream.fastpix.io/{playbackId}.m3u8`
- Spritesheet metadata: `https://images.fastpix.io/{playbackId}/spritesheet.json`

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
    
    // Custom domain (defaults to "stream.fastpix.io")
    customDomain = "custom.stream.fastpix.io"
    
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
- Player is released when view is detached
- A fresh player instance is created on reattach
- Playback will restart from the beginning

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
