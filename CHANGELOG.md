# Changelog

All notable changes to this project will be documented in this file.

## [2.1.0]

### Added
- **Read-through disk cache** (`CacheConfig`, opt-in): downloaded segments are persisted to disk, so
  a re-watch — or a scroll back to an earlier item in a feed — plays from local storage instead of
  the network. Enable with `FastPixPlayer.Builder.setCacheConfig(CacheConfig.enabled())`; bounded by
  a hard byte ceiling (256 MB default) with LRU eviction. The cache is process-wide, since Media3
  forbids two `SimpleCache` instances over one directory
  - `MediaCacheProvider` owns the singleton and exposes `cachedBytes()`, `clear()`, and `release()`
  - Manifests bypass the cache by default (`CacheConfig.cachePlaylists = false`) so a cached
    playlist can never pin a **live** stream to a stale segment list. On-demand-only apps can opt in
    with `CacheConfig.forOnDemandFeed()`, which removes two round trips per item
- **`FastPixPreCacher`** (opt-in): warms upcoming media into the cache before the user reaches it —
  the fix for reel/episode feeds that give every page its own player. Walks the HLS ladder the way
  the player will (multivariant playlist → chosen media playlist → first segments), bounded by
  `PreCacheConfig.segmentCount` and `maxBytesPerItem`. `preCache(urls)` also cancels in-flight warms
  that have fallen out of the window, so a download the user swiped past stops competing with the
  item on screen. Live streams are detected and skipped. FastPix ladders are demuxed, so the audio
  rendition referenced by the chosen variant is warmed alongside the video
  (`PreCacheConfig.includeAudioRendition`, on by default)
- **Next-item preloading** (`PreloadConfig`, opt-in): exposes ExoPlayer's own preloading via
  `FastPixPlayer.Builder.setPreloadConfig(...)`, for feeds that queue media with `setMediaItems`
- **`PlayerView.resizeMode`**: video scaling is now configurable through an SDK-level `ResizeMode`
  enum (`FIT`, `FIXED_WIDTH`, `FIXED_HEIGHT`, `FILL`, `ZOOM`), in code or via the new
  `app:fastPixResizeMode` XML attribute. Defaults to `FIT`, unchanged from before; feeds that want a
  source of any shape to fill the page use `ZOOM`
- **`BufferConfig`**: buffering thresholds are now configurable, with `BufferConfig.FEED` for
  short-form feeds and `BufferConfig.MEDIA3_DEFAULT` to restore Media3's stock values

### Changed
- **Faster first frame by default**: the player now installs a tuned `LoadControl` instead of
  Media3's stock one. `bufferForPlaybackMs` drops from 1000 ms to 500 ms and
  `bufferForPlaybackAfterRebufferMs` from 2000 ms to 1500 ms; ahead-buffer sizing is unchanged.
  Pass `BufferConfig.MEDIA3_DEFAULT` to opt out
- `FastPixPlayer.Builder.build()` installs a caching `MediaSource.Factory` only when a cache is
  configured and opened successfully; with caching off, player construction is unchanged

### Sample app
- Added **ReelFeedActivity**: a vertical reel feed over the sample streams with a three-player pool,
  a Turbo toggle that A/B tests the 2.1.0 path against pre-2.1.0 behaviour, and a HUD reporting
  time-to-ready per swipe

### Version
- Library version bumped to `2.1.0` in `build.gradle.kts` and `FastPixPlayerLibraryInfo.PLAYER_VERSION`

## [2.0.1]

### Updates:
- Refactored `applyDefaultTrackSelection` in `FastPixPlayer.kt` into smaller, more specific helper methods for audio and subtitle selection
- Changed `streamType` in `FastPixMediaItemBuilder.kt` from nullable to a default value of `StreamType.onDemand`
- Improved safety when registering `autoRotateObserver` in `MainActivity.kt` using null-safe calls
- Updated `PlayerStore.kt` and `PlayerView.kt` to support nullable `FastPixPlayer` instances
- Replaced `e.printStackTrace()` with `Logger.log` in `Utils.kt`
- Cleaned up code by using `.orEmpty()` and removing unnecessary null checks in `DrmManager.kt` and `FastPixPlayer.kt`

## [2.0.0]

### Added
- **Timestamp-only seek previews**: when no spritesheet is available, `SeekPreviewListener.onSpritesheetLoaded(...)` is now invoked with a `SpritesheetMetadata.TIMESTAMP_ONLY` sentinel (bitmap is `null`, `timestampMs` reflects the scrub position), so apps can render the seek time even without thumbnails
  - Gated by `PreviewFallbackMode.TIMESTAMP`; set `PreviewFallbackMode.NONE` to suppress

### Changed
- **Default stream host migration**: the default `customDomain` for `FastPixPlayer` is now `stream.fastpix.com` (was `stream.fastpix.io`). Resolved playback URLs and the example/docs reflect the new host
- **Spritesheet URL resolver**: `FastPixSpritesheetUrlResolver` now maps `stream.fastpix.io` → `images.fastpix.com` and `stream.fastpix.app` → `images.fastpix.co`. The previous `stream.fastpix.io`, `stream.fastpix.app`, and `venus-stream.fastpix.dev` mappings have been removed
- **Seek preview load is no longer an infinite retry loop**: `SeekPreviewManager.loadSpritesheet` now attempts the load once and, on unrecoverable failure, settles into timestamp mode and completes the flow. The lower-level repository/source still retries transient errors internally. Removed the `INITIAL_RETRY_DELAY_MS` / `MAX_RETRY_DELAY_MS` constants and the exponential backoff loop
- **`CustomSpritesheetSource` treats all 4xx as terminal**: previously only `404` skipped retry; now `400`/`401`/`403`/`404` etc. all short-circuit to the no-spritesheet path (these won't recover via retry)
- **`SpritesheetMetadata` validation relaxed** to allow non-negative values so the `TIMESTAMP_ONLY` sentinel can be constructed. Real spritesheets are still validated via `isValid()` before being used

### Version
- Library version bumped to `2.0.0` in `build.gradle.kts` and `FastPixPlayerLibraryInfo.PLAYER_VERSION`

## [1.0.9]
### Added

- **DRM Support (Widevine)**: SDK-level DRM abstraction for protected content playback
  - Added `DrmConfig` data class for configuring DRM without exposing Media3 internals
  - Added `DrmManager` internal manager that builds Media3 `DrmConfiguration` from SDK config
  - Added `drmConfig` property in `FastPixMediaItemBuilder` to enable DRM on a per-media-item basis
  - DRM license URL is auto-constructed from `playbackId`, `playbackToken`, and `streamType` — no manual URL assembly required
  - Widevine UUID and multi-session enabled by default

- **Video Quality Switching**: Manual and automatic video quality (rendition) selection
  - Added `VideoTrack` data model exposing `id`, `width`, `height`, `bitrate`, `label`, `isSelected`, and `isAuto`
  - Added `getVideoQualities()` to retrieve available video renditions sorted by resolution
  - Added `getCurrentVideoQuality()` to get the currently active rendition
  - Added `setVideoQuality(trackId: String)` to lock playback to a specific quality
  - Added `enableAutoQuality()` to re-enable adaptive bitrate (ABR) selection
  - Quality switches are deferred during seek operations and applied on seek completion
  - Video track discovery integrated into `TrackManager` alongside existing audio/subtitle track management



## [1.0.8]
- Bumped `media3-data` version from `1.2.4` to `1.2.5`

## [1.0.7]
### Updates:
- Bumped `media3` version from `1.2.3` to `1.2.4`
- Updated library version to `1.0.7` in `README.md`, `build.gradle.kts`, and `FastPixPlayerLibraryInfo.kt`
- Added `AnalyticsConfig` and `VideoDataDetails` integration in `MainActivity`
- Refactored and cleaned up code in `MainActivity.kt`

## [1.0.6] - 2026
### Fix
- Crash Fix on Seek Preview Config

## [1.0.5] - 2026
### Added

- **Audio Track Switching**: Support for discovering and switching between multiple audio tracks
  - Added `getAudioTracks()` to retrieve available audio tracks
  - Added `getCurrentAudioTrack()` to get the currently selected track
  - Added `setAudioTrack(trackId: String)` to switch audio tracks dynamically without interrupting playback
  - Added `setDefaultAudioTrack(languageCode: String)` to automatically select preferred language
  - Added `AudioTrackListener` with callbacks:
    - `onAudioTracksLoaded()`
    - `onAudioTracksChange()`
    - `onAudioTracksLoadedFailed()`
    - `onAudioTrackSwitching()`

- **Subtitle Track Switching**: Support for subtitles with dynamic switching and rendering
  - Added `getSubtitleTracks()` to retrieve available subtitle tracks
  - Added `getCurrentSubtitleTrack()` to get current subtitle selection
  - Added `setSubtitleTrack(trackId: String)` to enable specific subtitle track
  - Added `disableSubtitles()` to turn off subtitles
  - Added `setDefaultSubtitleTrack(languageCode: String)` for automatic subtitle selection
  - Added `SubtitleTrackListener` with callbacks:
    - `onSubtitlesLoaded()`
    - `onSubtitleChange()`
    - `onSubtitlesLoadedFailed()`
    - `onSubtitleCueChange()`

- **Subtitle Rendering Support**
  - Introduced `SubtitleCueInfo` and `SubtitleRenderInfo` for real-time subtitle rendering
  - Enables custom subtitle UI implementation using cue updates

## [1.0.4] - 2026
### Added

- **Seek Preview (Spritesheet thumbnails)**: Thumbnail previews while scrubbing
  - Added `SeekPreviewConfig` to configure seek preview behavior (enabled, preload, cache, fallback mode)
  - Added `PreviewFallbackMode` for graceful fallback when thumbnails are unavailable (timestamp/none)
  - Added `SeekPreviewListener` callbacks: `onSpritesheetInitialized`, `onSpritesheetFailed`, `onPreviewShow`, `onPreviewHide`, `onSpritesheetLoaded`
  - Added `FastPixPlayer.Builder.setSeekPreviewConfig(config: SeekPreviewConfig?)` to enable seek preview at player creation time
  - Added `FastPixPlayer.setSeekPreviewListener(listener: SeekPreviewListener?)` to receive preview frames
  - Added `FastPixPlayer.showPreview()`, `FastPixPlayer.loadPreview(timeMs)`, `FastPixPlayer.hidePreview()` for seek-bar integration



## [1.0.3] - 2026
### Added

- **Volume Control**: Comprehensive volume management API
  - Added `setVolume(volume: Float)` method to set volume level (0.0f to 1.0f)
  - Added `getVolume()` method to get current volume level
  - Added `mute()` method to mute playback (saves current volume for restoration)
  - Added `unmute()` method to restore previous volume level
  - Added `onVolumeChanged(volumeLevel: Float)` callback in `PlaybackListener` - triggered when device volume changes via hardware buttons or system controls
  - Added `onMuteStateChanged(isMuted: Boolean)` callback in `PlaybackListener` - triggered when mute state changes
  - Automatic device volume monitoring with periodic checks (200ms interval)
  - Volume state is preserved across configuration changes

- **AutoPlay Support**: Automatic playback start when media is ready
  - Added `setAutoplay(autoplay: Boolean)` method in `FastPixPlayer.Builder` to configure autoplay during player creation
  - Added `autoplay` property on `FastPixPlayer` to enable/disable autoplay at runtime
  - When enabled, playback automatically starts when media is ready to play
  - No manual call to `play()` required when autoplay is enabled
  - Autoplay state is preserved across configuration changes

- **Loop Playback**: Seamless looping functionality
  - Added `setLoop(loop: Boolean)` method in `FastPixPlayer.Builder` to configure loop during player creation
  - Added `loop` property on `FastPixPlayer` to enable/disable looping at runtime
  - When enabled, playback automatically restarts from the beginning when it reaches the end
  - Current media item repeats indefinitely until loop is disabled
  - Loop state is preserved across configuration changes

- **Playback Rate Control**: Flexible playback speed adjustment
  - Added `setPlaybackSpeed(speed: Float)` method to set playback speed to a specific value
  - Added `getPlaybackSpeed()` method to get current playback speed
  - Added `getAvailablePlaybackSpeeds()` method to get all available speed options
  - Available playback speeds: 0.25x, 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 1.75x, 2.0x
  - Added `onPlaybackRateChanged(rate: Float)` callback in `PlaybackListener` - triggered when playback speed changes
  - Playback speed automatically adjusts to closest available speed if exact value is not available
  - Playback speed state is preserved across configuration changes


## [1.0.2] - 2026
### Improved
- Code Optimization
- Refactoring


## [1.0.1] - 2026

### Added

- **Playback Control Methods**: Simple and intuitive playback control API
  - Added `play()` method to start or resume playback
  - Added `pause()` method to pause playback
  - Added `togglePlayPause()` method to toggle between play and pause states
  - Added `isPlaying()` method to check current playback state

- **PlaybackListener Interface**: Comprehensive event-driven architecture for playback monitoring
  - Added `onPlay()` callback - triggered when playback starts or resumes
  - Added `onPause()` callback - triggered when playback is paused
  - Added `onPlaybackStateChanged(isPlaying: Boolean)` callback - triggered on play/pause state changes
  - Added `onError(error: PlaybackException)` callback - triggered when playback errors occur
  - Added `onTimeUpdate(currentPositionMs, durationMs, bufferedPositionMs)` callback - continuous time updates during playback (similar to HTML5 `onTimeUpdate`)
    - Invoked approximately every 500ms during active playback
    - Provides current position, total duration, and buffered position
    - Enables real-time UI updates for seek bars and time displays
  - Added `onSeekStart(currentPositionMs)` callback - triggered when seek operations begin
  - Added `onSeekEnd(fromPositionMs, toPositionMs, durationMs)` callback - triggered when seek operations complete
  - Added `onBufferingStart()` callback - triggered when player enters buffering state
  - Added `onBufferingEnd()` callback - triggered when player exits buffering state
  - All callbacks are invoked on the main thread
  - Optional callbacks have default empty implementations for flexibility

- **Listener Management**: Methods for managing playback listeners
  - Added `addPlaybackListener(listener: PlaybackListener)` method
  - Added `removePlaybackListener(listener: PlaybackListener)` method
  - Added `clearPlaybackListeners()` method

### Improved

- Enhanced player API with more intuitive playback control methods
- Better developer experience for building custom playback UIs with comprehensive callbacks
- Real-time time updates enable smooth seek bar and time display synchronization
- Seek operation tracking provides better UX during seeking

### Technical Details

- Playback speed is implemented using Media3's `PlaybackParameters` API
- Speed state is preserved across configuration changes
- All speed methods are available on both `PlayerView` and the underlying player instance
- Time updates are automatically managed - start when playback begins, stop when paused/ended
- Seek callbacks automatically pause time updates during seek operations for better performance

## [1.0.0]

### Initial Release:

The first public release of the FastPix Android Player SDK, packed with modern playback capabilities for both live and on-demand streaming scenarios.

- **Support for Public & Private Media**: Secure token-based playback for private videos and effortless access for public streams.

- **Live & On-Demand Streaming**: Adaptive support for both real-time and pre-recorded content with optimized buffering strategies.

- **Audio Track Switching**: Dynamically switch between available audio tracks, ensuring accessibility and multi-language support.

- **Subtitle Track Switching**: Enhance viewer experience with support for multiple subtitle tracks and on-the-fly switching.

- **QoE & Playback Metrics**: Built-in tracking of key Video Quality of Experience (QoE) indicators such as: rebuffer events, bitrate/resolution changes, and startup time — enabling deep insights into playback performance.

- **Custom Playback Resolution**: Programmatically set or limit playback resolution to suit user preferences or bandwidth constraints.

- **Stream Type Configuration**: Fine-tuned handling of stream types like **HLS**, **DASH**, and others — with control over live latency modes and playback strategies.

- **Custom Domain Support**: Compatible with FastPix's custom domain system for secure and branded media delivery.

- **Rendition Order Control**: Configure and prioritize video renditions based on bitrate, resolution, etc., to ensure predictable quality behavior. Supports both **ascending** (low ➜ high) and **descending** (high ➜ low) strategies. Ideal for optimizing playback under bandwidth constraints or enforcing quality-first strategies.
