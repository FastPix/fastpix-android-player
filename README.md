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
- **Time Updates** – Continuous time updates during playback (similar to HTML5 `onTimeUpdate`)

---

## Requirements

- **Android Studio** Arctic Fox or newer
- **Android SDK** version 24 (Android 7.0) or higher
- **Kotlin** 1.8 or higher
- **AndroidX Media3** 1.9.0

---

## Installation

### Step 1: Add the dependency

Add the following to your `build.gradle.kts` (or `build.gradle`):

```kotlin
dependencies {
    implementation("io.fastpix.player:android-player-sdk:1.0.2")
}
```

Or if using version catalogs, add to `libs.versions.toml`:

```toml
[versions]
fastpix-player = "1.0.2"

[libraries]
fastpix-player = { module = "io.fastpix.player:android-player-sdk", version.ref = "fastpix-player" }
```

### Step 2: Sync Gradle

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

#### Option A: Using FastPix Builder (Recommended for FastPix streams)

```kotlin
import io.fastpix.media3.PlayerView
import io.fastpix.media3.PlaybackListener
import io.fastpix.media3.core.PlaybackResolution

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupPlayer()
    }
    
    private fun setupPlayer() {
        // Set FastPix media item using builder pattern
        binding.playerView.setFastPixMediaItem {
            playbackId = "your-playback-id"
            maxResolution = PlaybackResolution.FHD_1080
        }
        
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
}

playerView.addPlaybackListener(listener)
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

