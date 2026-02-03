# Changelog

All notable changes to this project will be documented in this file.


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
