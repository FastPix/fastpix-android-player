# Android FastPix Player

The FastPix Android Player SDK is a robust, extensible video playback solution built on top of [Google's ExoPlayer via AndroidX Media3](https://developer.android.com/media/media3), designed to offer seamless streaming experiences across a wide variety of use cases. Whether you're delivering public or private content, on-demand or live streams, FastPix provides the flexibility and performance required for modern video apps. 

---

## Key Features

- **Support for Public & Private Media**: Secure token-based playback for private videos and effortless access for public streams. 

- **Live & On-Demand Streaming**: Adaptive support for both real-time and pre-recorded content with optimized buffering strategies. 

- **Audio Track Switching**: Dynamically switch between available audio tracks, ensuring accessibility and multi-language support. 

- **Subtitle Track Switching**: Enhance viewer experience with support for multiple subtitle tracks and on-the-fly switching. 

- **In-built QoE & Playback Metrics capture support**: Built-in tracking of key Video Quality of Experience (QoE) indicators such as rebuffer events, bitrate, resolution changes, and startup time — enabling deep insights into playback performance. 

- **Custom Playback Resolution**: Programmatically set or limit playback resolution to suit user preferences or bandwidth constraints. 

- **Custom Domain Support**: Compatible with FastPix's custom domain system for secure and branded media delivery. 

- **Rendition Order Control**: The "rendition order" refers to the sequence in which the player considers available video renditions (each representing a different resolution/bitrate combo) during adaptive playback. 

    - Ascending Order = From lowest bitrate/resolution ➜ to highest 
        Example: 144p → 360p → 720p → 1080p 
        Best for slow networks, conservative data use, or fast startup. 

    - Descending Order = From highest bitrate/resolution ➜ to lowest 
        Example: 1080p → 720p → 360p → 144p 
        Useful if you want to prioritize best quality first and only drop on rebuffed or bandwidth issues. 

---

## Prerequisites

### Getting started with Android:
- Android Studio Arctic Fox or newer
- Android SDK version 24+
- Fastpix Player dependency for Player
- FastPix ExoPlayer SDK (media3-compatible) as a dependency
- GitHub Personal Access Token (PAT) for private Maven access

### Getting started with FastPix:

To get started with the FastPix Android Player SDK we need some prerequisites, follow these steps:

1. **Log in to the FastPix Dashboard**: Navigate to the [FastPix-Dashboard](https://dashboard.fastpix.io) and log in with your credentials.
2. **Create Media**: Start by creating a media using a pull or push method. You can also use our APIs instead for [Push media](https://docs.fastpix.io/docs/upload-videos-directly) or [Pull media](https://docs.fastpix.io/docs/upload-videos-from-url).
3. **Retrieve Media Details**: After creation, access the media details by navigating to the "View Media" page.
4. **Get Playback ID**: From the media details, obtain the playback ID.
5. **Play Video**: Use the playback ID in the FastPix-player to play the video seamlessly.

[Explore our detailed guide](https://docs.fastpix.io/docs/get-started-in-5-minutes) to upload videos and getting a playback ID using FastPix APIs

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

### Step 2: Add the SDK Dependency to `build.gradle`
```groovy
dependencies {
    implementation 'io.fastpix.player:android:1.0.0'
}
```

---

### Basic Usage

```kotlin
package com.examples.app

import io.fastpix.player.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class CustomizedPlayerActivity : AppCompatActivity() {
    private val playerView get() = binding.player
    private var player: MediaFastPixPlayer? = null
    private var playWhenReady = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomizedPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        startPlayback()
    }

    private fun startPlayback() {
        val mediaItem = FastPixUrlGenerator.createCustomPlaybackUri(
            playbackId = "YOUR_PLAYBACK_ID"//playbackId is mandatory input
        )
        if (player != null ) {
            player!!.playWhenReady = false
        }
        val item = mediaItem.build()
        if (item != player?.currentMediaItem) {
            beginPlayback(item)
        }
    }

    private fun beginPlayback(mediaItem: MediaItem) {

        val player = createPlayer(this)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = playWhenReady
        player.seekTo(playbackPosition)
        this.playerView.player = player
        this.player = player
    }

    private fun createPlayer(context: Context): MediaFastPixPlayer {

        val mediaFastPixPlayer: MediaFastPixPlayer = MediaFastPixPlayer.Builder(context).build()

        return mediaFastPixPlayer
    }

}
```

### Feature wise examples:

[Click here](https://docs.fastpix.io/docs/overview-and-features) for a detailed overview.

## Playing public media with StreamType:

- The `playback-id` allows for easy video playback by linking directly to the media file. Playback is available as soon as the media status is "ready".
- The `stream-type` is set to `on-demand` by default. and `stream-type` is set to `live-stream` to play live streams.

```kotlin
package com.examples.app

import io.fastpix.player.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class CustomizedPlayerActivity : AppCompatActivity() {
    private val playerView get() = binding.player
    private var player: MediaFastPixPlayer? = null
    private var playWhenReady = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomizedPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        startPlayback()
    }

    private fun startPlayback() {
        val mediaItem = FastPixUrlGenerator.createCustomPlaybackUri(
            playbackId = "YOUR_PLAYBACK_ID",//PlaybackId is mandatory input
            streamType = "SET_STREAM_TYPE"//StreamType set by defalt as "on-demand" and set as live-stream play with live video
        )
        if (player != null ) {
            player!!.playWhenReady = false
        }
        val item = mediaItem.build()
        if (item != player?.currentMediaItem) {
            beginPlayback(item)
        }
    }

    private fun beginPlayback(mediaItem: MediaItem) {

        val player = createPlayer(this)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = playWhenReady
        player.seekTo(playbackPosition)
        this.playerView.player = player
        this.player = player
    }

    private fun createPlayer(context: Context): MediaFastPixPlayer {

        val mediaFastPixPlayer: MediaFastPixPlayer = MediaFastPixPlayer.Builder(context).build()

        return mediaFastPixPlayer
    }

     override fun onStop() {
        destroyPlayer()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        destroyPlayer()
    }

    private fun destroyPlayer() {
        if (player != null ) {
            playbackPosition = player!!.currentPosition
            playWhenReady = player!!.playWhenReady
            playerView?.player = null
            player?.release()
            player = null
        }
    }

}
```

## Securing your playback:

- Secure your video playback with a signed playback using a `playback-id` and `token`.

```kotlin
package com.examples.app

import io.fastpix.player.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class CustomizedPlayerActivity : AppCompatActivity() {
    private val playerView get() = binding.player
    private var player: MediaFastPixPlayer? = null
    private var playWhenReady = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomizedPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        startPlayback()
    }

    private fun startPlayback() {
        val mediaItem = FastPixUrlGenerator.createCustomPlaybackUri(
            playbackId = "YOUR_PLAYBACK_ID",//PlaybackId is mandatory input
            playbackToken = "SET_PLAYBACK_TOKEN",//playbackToken
        )
        if (player != null ) {
            player!!.playWhenReady = false
        }
        val item = mediaItem.build()
        if (item != player?.currentMediaItem) {
            beginPlayback(item)
        }
    }

    private fun beginPlayback(mediaItem: MediaItem) {

        val player = createPlayer(this)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = playWhenReady
        player.seekTo(playbackPosition)
        this.playerView.player = player
        this.player = player
    }

    private fun createPlayer(context: Context): MediaFastPixPlayer {

        val mediaFastPixPlayer: MediaFastPixPlayer = MediaFastPixPlayer.Builder(context).build()

        return mediaFastPixPlayer
    }

     override fun onStop() {
        destroyPlayer()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        destroyPlayer()
    }

    private fun destroyPlayer() {
        if (player != null ) {
            playbackPosition = player!!.currentPosition
            playWhenReady = player!!.playWhenReady
            playerView?.player = null
            player?.release()
            player = null
        }
    }

}
```
## Resolution Control: 

- FastPix Android Player allows you to configure playback resolution, including minimum, maximum, specific, and range-based resolutions for optimized streaming.

```kotlin
package com.examples.app

import io.fastpix.player.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class CustomizedPlayerActivity : AppCompatActivity() {
    private val playerView get() = binding.player
    private var player: MediaFastPixPlayer? = null
    private var playWhenReady = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomizedPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        startPlayback()
    }

    private fun startPlayback() {
        val mediaItem = FastPixUrlGenerator.createCustomPlaybackUri(
            playbackId = "YOUR_PLAYBACK_ID",//PlaybackId is mandatory input
            minResolution = "SET_MIN_RESOLUTION",//PlaybackResolution.LD_480
            maxResolution = "SET_MAX_RESOLUTION",//PlaybackResolution.FHD_1080
            resolution = "SET_RESOLUTION",//PlaybackResolution.LD_480
        )
        if (player != null ) {
            player!!.playWhenReady = false
        }
        val item = mediaItem.build()
        if (item != player?.currentMediaItem) {
            beginPlayback(item)
        }
    }

    private fun beginPlayback(mediaItem: MediaItem) {

        val player = createPlayer(this)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = playWhenReady
        player.seekTo(playbackPosition)
        this.playerView.player = player
        this.player = player
    }

    private fun createPlayer(context: Context): MediaFastPixPlayer {

        val mediaFastPixPlayer: MediaFastPixPlayer = MediaFastPixPlayer.Builder(context).build()

        return mediaFastPixPlayer
    }

     override fun onStop() {
        destroyPlayer()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        destroyPlayer()
    }

    private fun destroyPlayer() {
        if (player != null ) {
            playbackPosition = player!!.currentPosition
            playWhenReady = player!!.playWhenReady
            playerView?.player = null
            player?.release()
            player = null
        }
    }

}
```
## Rendition Order Customization: 

- With the FastPix Android Player SDK, you can configure resolution selection priorities to deliver an optimized viewing experience to user preferences.

```kotlin
package com.examples.app

import io.fastpix.player.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class CustomizedPlayerActivity : AppCompatActivity() {
    private val playerView get() = binding.player
    private var player: MediaFastPixPlayer? = null
    private var playWhenReady = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomizedPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        startPlayback()
    }

    private fun startPlayback() {
        val mediaItem = FastPixUrlGenerator.createCustomPlaybackUri(
            playbackId = "YOUR_PLAYBACK_ID",//PlaybackId is mandatory input
            renditionOrder = "SET_RENDITION_ORDER",//RenditionOrder.Descending
        )
        if (player != null ) {
            player!!.playWhenReady = false
        }
        val item = mediaItem.build()
        if (item != player?.currentMediaItem) {
            beginPlayback(item)
        }
    }

    private fun beginPlayback(mediaItem: MediaItem) {

        val player = createPlayer(this)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = playWhenReady
        player.seekTo(playbackPosition)
        this.playerView.player = player
        this.player = player
    }

    private fun createPlayer(context: Context): MediaFastPixPlayer {

        val mediaFastPixPlayer: MediaFastPixPlayer = MediaFastPixPlayer.Builder(context).build()

        return mediaFastPixPlayer
    }

     override fun onStop() {
        destroyPlayer()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        destroyPlayer()
    }

    private fun destroyPlayer() {
        if (player != null ) {
            playbackPosition = player!!.currentPosition
            playWhenReady = player!!.playWhenReady
            playerView?.player = null
            player?.release()
            player = null
        }
    }

}
```
##Custom Domains: 

- With the FastPix Android Player SDK, you can stream videos from your own custom domain, whether you're serving public content or securing private videos using playback tokens. This flexible setup allows you to maintain and optimize performance, control access to your media with minimal configuration.

```kotlin
package com.examples.app

import io.fastpix.player.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class CustomizedPlayerActivity : AppCompatActivity() {
    private val playerView get() = binding.player
    private var player: MediaFastPixPlayer? = null
    private var playWhenReady = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomizedPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        startPlayback()
    }

    private fun startPlayback() {
        val mediaItem = FastPixUrlGenerator.createCustomPlaybackUri(
            playbackId = "YOUR_PLAYBACK_ID",//PlaybackId is mandatory input
            customDomain = "SET_CUSTOM_DOMAIN"//customDomain
        )
        if (player != null ) {
            player!!.playWhenReady = false
        }
        val item = mediaItem.build()
        if (item != player?.currentMediaItem) {
            beginPlayback(item)
        }
    }

    private fun beginPlayback(mediaItem: MediaItem) {

        val player = createPlayer(this)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = playWhenReady
        player.seekTo(playbackPosition)
        this.playerView.player = player
        this.player = player
    }

    private fun createPlayer(context: Context): MediaFastPixPlayer {

        val mediaFastPixPlayer: MediaFastPixPlayer = MediaFastPixPlayer.Builder(context).build()

        return mediaFastPixPlayer
    }

     override fun onStop() {
        destroyPlayer()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        destroyPlayer()
    }

    private fun destroyPlayer() {
        if (player != null ) {
            playbackPosition = player!!.currentPosition
            playWhenReady = player!!.playWhenReady
            playerView?.player = null
            player?.release()
            player = null
        }
    }

}
```

## Complete code:

```kotlin
package com.examples.app

import io.fastpix.player.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class CustomizedPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCustomizedPlayerBinding
    private val playerView get() = binding.player
    private var player: MediaFastPixPlayer? = null
    private var playbackPosition: Long = 0
    private var playWhenReady = true
    private lateinit var fastPixListener: Player.Listener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomizedPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        startPlayback()
    }

    private fun startPlayback() {
        fastPixListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@CustomizedPlayerActivity,
                    "Playback error! ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        FastPixUrlGenerator.setPlayerListener(fastPixListener)
        val mediaItem = FastPixUrlGenerator.createCustomPlaybackUri(
            playbackId = "YOUR_PLAYBACK_ID",//playbackId is mandatory input
            minResolution = "SET_MIN_RESOLUTION",//PlaybackResolution.LD_480
            maxResolution = "SET_MAX_RESOLUTION",//PlaybackResolution.FHD_1080
            resolution = "SET_RESOLUTION",//PlaybackResolution.LD_480
            renditionOrder = "SET_RENDITION_ORDER",//RenditionOrder.Descending
            playbackToken = "SET_PLAYBACK_TOKEN",//playbackToken
            customDomain = "SET_CUSTOM_DOMAIN",//customDomain
            streamType = "SET_STREAM_TYPE"//streamType set as "live-stream or on-demand"
        )?:return
        if (player != null ) {
            player!!.playWhenReady = false
        }
        val item = mediaItem.build()
        if (item != player?.currentMediaItem) {
            beginPlayback(item)
        }
    }

    private fun beginPlayback(mediaItem: MediaItem) {

        val player = createPlayer(this)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = playWhenReady
        player.seekTo(playbackPosition)
        this.playerView.player = player
        this.player = player
    }

    private fun createPlayer(context: Context): MediaFastPixPlayer {
        val customerPlayerDataEntity = CustomerPlayerDataEntity()
        customerPlayerDataEntity.workspaceKey = "YOUR_WORK_SPACE_KEY"// WorkspaceKey get it from fastpix dashboard
       
        val customerViewDataEntity = CustomerViewDataEntity()
        customerViewDataEntity.viewSessionId = UUID.randomUUID().toString()

        val customerVideoDataEntity = CustomerVideoDataEntity()
        customerVideoDataEntity.videoId = "videoId"
        customerVideoDataEntity.videoTitle = "videoTitle"

        val customDataEntity = CustomDataEntity()
        customDataEntity.customData1 = "item1"
        customDataEntity.customData2 = "item2"
         //       ||                   ||
         //       ||                   || 
        customDataEntity.customData10 = "item10"

        val customerDataEntity = CustomerDataEntity(
            customerPlayerDataEntity,
            customerVideoDataEntity,
            customerViewDataEntity
        )
        customerDataEntity.setCustomData(customDataEntity)

        val mediaFastPixPlayer: MediaFastPixPlayer = MediaFastPixPlayer.Builder(context)
            .pushMonitoringData(customerDataEntity)
            .configureExoPlayer {
                setHandleAudioBecomingNoisy(true)
            }.build()


        mediaFastPixPlayer.addListener(fastPixListener)


        return mediaFastPixPlayer
    }

     override fun onStop() {
        destroyPlayer()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        destroyPlayer()
    }

    private fun destroyPlayer() {
        if (player != null ) {
            playbackPosition = player!!.currentPosition
            playWhenReady = player!!.playWhenReady
            playerView?.player = null
            player?.release()
            player = null
        }
    }

}
```

### XML Layout
```xml
<androidx.media3.ui.PlayerView
    android:id="@+id/player"
    android:layout_width="match_parent"
    android:layout_height="0dp"
    app:layout_constraintDimensionRatio="4:3"
    app:layout_constraintTop_toTopOf="parent"
    app:show_subtitle_button="true" />
```

## Documentation
For advanced usage and APIs, refer to the [FastPix Developer Docs](https://docs.fastpix.io/docs/******).

