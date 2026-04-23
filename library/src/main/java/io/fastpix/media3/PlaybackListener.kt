package io.fastpix.media3

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import io.fastpix.media3.tracks.VideoTrack

/**
 * Listener interface for playback events from [io.fastpix.media3.core.FastPixPlayer].
 *
 * This interface provides callbacks for key playback events, allowing developers to
 * react to playback state changes, errors, user interactions, and continuous time updates.
 *
 * All callbacks are invoked on the main thread.
 *
 * ## Time Updates
 *
 * The [onTimeUpdate] callback provides continuous time updates during active playback,
 * similar to HTML5 video's `onTimeUpdate` event. The callback is invoked periodically
 * (default: 500ms) while playback is active.
 *
 * - Callbacks start automatically when playback begins
 * - Callbacks stop automatically when playback is paused, ended, or player is released
 * - Callbacks resume automatically when playback resumes
 *
 * ## Seek Interaction Callbacks
 *
 * The [onSeekStart] and [onSeekEnd] callbacks provide notifications for seek operations.
 * These callbacks are triggered for both programmatic seeks and built-in seek bar interactions.
 *
 * During seek operations:
 * - Automatic time updates are paused
 * - Normal operation resumes after [onSeekEnd] is called
 *
 * Both methods have default empty implementations, so they're optional to override.
 *
 * ## Buffering Callbacks
 *
 * The [onBufferingStart] and [onBufferingEnd] callbacks provide notifications for buffering state changes.
 * These callbacks are triggered when the player enters or exits the buffering state.
 *
 * - [onBufferingStart] is called when the player enters the buffering state (e.g., during initial loading or when network conditions require buffering)
 * - [onBufferingEnd] is called when the player exits the buffering state and becomes ready to play
 *
 * Both methods have default empty implementations, so they're optional to override.
 *
 * @see io.fastpix.media3.core.FastPixPlayer.addPlaybackListener
 * @see io.fastpix.media3.core.FastPixPlayer.removePlaybackListener
 * @see PlayerView
 */
interface PlaybackListener {
    enum class VideoQualityChangeSource {
        MANUAL,
        ABR
    }
    /**
     * Called when playback starts or resumes.
     */
    fun onPlay()

    /**
     * Called when playback is paused.
     */
    fun onPause()

    /**
     * Called when the playback state changes (playing or paused).
     *
     * @param isPlaying true if currently playing, false if paused.
     */
    fun onPlaybackStateChanged(isPlaying: Boolean)

    /**
     * Called when a playback error occurs.
     *
     * @param error The playback exception that occurred.
     */
    fun onError(error: PlaybackException)

    /**
     * Called periodically during active playback with current time information.
     *
     * This callback is invoked approximately every 500ms while playback is active.
     * It provides the current playback position, total duration, and buffered position
     * to enable external UI updates (e.g., seek bars) and analytics tracking.
     *
     * This method has a default empty implementation, so it's optional to override.
     * Only override if you need to receive time updates.
     *
     * @param currentPositionMs The current playback position in milliseconds.
     * @param durationMs The total duration of the media in milliseconds, or [C.TIME_UNSET]
     *   if the duration is not yet known.
     * @param bufferedPositionMs The buffered position in milliseconds, indicating how much
     *   of the media has been loaded ahead of the current position.
     */
    fun onTimeUpdate(
        currentPositionMs: Long,
        durationMs: Long,
        bufferedPositionMs: Long,
    ) {
        // Default empty implementation - optional to override
    }

    /**
     * Called when a seek operation begins.
     *
     * This callback is triggered when:
     * - User starts dragging the built-in seek bar
     * - A programmatic seek is initiated via [PlayerView.seekTo]
     *
     * During the seek operation:
     * - Automatic time updates are paused
     * - Tap-to-toggle play/pause is disabled
     *
     * This method has a default empty implementation, so it's optional to override.
     * Only override if you need to receive seek start events.
     *
     * @param currentPositionMs The current playback position in milliseconds when the seek starts.
     */
    fun onSeekStart(currentPositionMs: Long) {
        // Default empty implementation - optional to override
    }

    /**
     * Called when a seek operation completes.
     *
     * This callback is triggered when:
     * - User releases the built-in seek bar after dragging
     * - A programmatic seek operation completes (player reaches the target position or buffering completes)
     *
     * After the seek operation:
     * - Automatic time updates resume (if playback is active)
     * - Tap-to-toggle play/pause is re-enabled
     *
     * This method has a default empty implementation, so it's optional to override.
     * Only override if you need to receive seek end events.
     *
     * @param fromPositionMs The playback position in milliseconds before the seek started.
     * @param toPositionMs The playback position in milliseconds after the seek completed.
     * @param durationMs The total duration of the media in milliseconds, or 0 if unknown.
     */
    fun onSeekEnd(
        fromPositionMs: Long,
        toPositionMs: Long,
        durationMs: Long
    ) {
        // Default empty implementation - optional to override
    }

    /**
     * Called when buffering starts.
     *
     * This callback is triggered when the player enters the buffering state,
     * typically when:
     * - Initial media loading begins
     * - Network conditions require buffering during playback
     * - Seeking to a position that requires buffering
     *
     * This method has a default empty implementation, so it's optional to override.
     * Only override if you need to receive buffering start events.
     */
    fun onBufferingStart() {
        // Default empty implementation - optional to override
    }

    /**
     * Called when buffering ends.
     *
     * This callback is triggered when the player exits the buffering state
     * and becomes ready to play, typically when:
     * - Initial media loading completes
     * - Sufficient data has been buffered to continue playback
     * - Buffering after a seek operation completes
     *
     * This method has a default empty implementation, so it's optional to override.
     * Only override if you need to receive buffering end events.
     */
    fun onBufferingEnd() {
        // Default empty implementation - optional to override
    }

    /**
     * Called when the device volume changes (via hardware buttons or system controls).
     *
     * This callback is triggered when:
     * - User presses volume up/down buttons
     * - System volume is changed programmatically
     * - Volume reaches zero (muted) or becomes non-zero (unmuted)
     *
     * This method has a default empty implementation, so it's optional to override.
     * Only override if you need to receive device volume change events.
     *
     * @param volumeLevel The current device volume level (0.0f to 1.0f).
     * @param isMuted true if the device volume is zero (muted), false otherwise.
     */
    fun onVolumeChanged(volumeLevel: Float) {
        // Default empty implementation - optional to override
    }

    fun onMuteStateChanged(isMuted: Boolean) {}

    fun onPlaybackRateChanged(rate: Float) {}

    /**
     * Called when active video quality information changes.
     *
     * Triggered for:
     * - Manual quality changes (`setVideoQuality` / `enableAutoQuality`)
     * - ABR-driven rendition switches while auto mode is enabled
     *
     * @param quality The current quality after the change, or null if unavailable.
     * @param source Whether this came from manual selection or ABR.
     */
    fun onVideoQualityChanged(
        quality: VideoTrack?,
        source: VideoQualityChangeSource
    ) {
        // Default empty implementation - optional to override
    }

    /**
     * Called when video playback completes (reaches the end).
     *
     * This callback is triggered when:
     * - The video reaches the end of playback
     * - Playback state transitions to [Player.STATE_ENDED]
     *
     * Note: If looping is enabled, this callback will still be called when the video
     * reaches the end, before it restarts (if applicable).
     *
     * This method has a default empty implementation, so it's optional to override.
     * Only override if you need to receive completion events.
     */
    fun onCompleted() {
        // Default empty implementation - optional to override
    }

    /**
     * Called when the video is ready to play for the first time after media is set.
     *
     * This callback is triggered when the player transitions to
     * [Player.STATE_READY][androidx.media3.common.Player.STATE_READY] for the first time,
     * indicating that enough data has been buffered to start playback.
     *
     * This is only called once per media item — subsequent ready states
     * (e.g. after seek or rebuffer) do not trigger this callback.
     *
     * This method has a default empty implementation, so it's optional to override.
     *
     * @param durationMs The total duration of the media in milliseconds, or
     *   [C.TIME_UNSET][androidx.media3.common.C.TIME_UNSET] if unknown.
     */
    fun onPlayerReady(durationMs: Long) {
        // Default empty implementation - optional to override
    }
}
