package io.fastpix.media3

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * Listener interface for playback events from [PlayerView].
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
 * - Callbacks stop automatically when playback is paused, ended, or view is detached
 * - Callbacks resume automatically when playback resumes
 *
 * ## Seek Interaction Callbacks
 *
 * The [onSeekStart] and [onSeekEnd] callbacks provide notifications for seek operations.
 * These callbacks are triggered for both programmatic seeks and built-in seek bar interactions.
 *
 * During seek operations:
 * - Automatic time updates are paused
 * - Tap-to-toggle play/pause is disabled
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
 * @see PlayerView.addPlaybackListener
 * @see PlayerView.removePlaybackListener
 */
interface PlaybackListener {
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
}
