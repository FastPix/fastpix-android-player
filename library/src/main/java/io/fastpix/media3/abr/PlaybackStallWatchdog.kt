package io.fastpix.media3.abr

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Emits a one-shot callback when the player stays in [Player.STATE_BUFFERING] continuously for
 * longer than [timeoutMs]. Intended for surfacing a "network too slow to play" UX instead of an
 * infinite loading spinner when the link can't sustain any rendition in the manifest.
 *
 * Lifecycle:
 * - [attach] registers a [Player.Listener] on the ExoPlayer. If the player is already BUFFERING
 *   at attach time, the timer starts immediately.
 * - Any exit from BUFFERING (to READY, IDLE, or ENDED) cancels the pending timeout. The next
 *   BUFFERING re-arms it, so this can fire on a later stall episode too.
 * - [detach] removes the listener and cancels any pending timeout. Safe to call multiple times.
 *
 * The watchdog only calls [onTimeout] — it does NOT mutate the player. Whether to stop, retry,
 * or show a UI is the caller's decision.
 *
 * @param timeoutMs Threshold in ms. Must be > 0 for the watchdog to fire; 0 disables it.
 */
@UnstableApi
class PlaybackStallWatchdog(
    private val player: ExoPlayer,
    private val timeoutMs: Long,
    private val onTimeout: (stallDurationMs: Long, positionMs: Long) -> Unit,
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Monotonic-ish timestamp when the current buffering episode started, or 0 when idle. */
    private var bufferingStartedAtMs: Long = 0L

    private var isAttached = false

    private val timeoutRunnable = Runnable {
        val stalledFor = if (bufferingStartedAtMs > 0L) {
            android.os.SystemClock.uptimeMillis() - bufferingStartedAtMs
        } else timeoutMs
        onTimeout(stalledFor, player.currentPosition.coerceAtLeast(0L))
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> arm()
                else -> disarm()
            }
        }
    }

    /** Must be called on the main thread. Idempotent. */
    fun attach() {
        if (isAttached) return
        isAttached = true
        player.addListener(playerListener)
        if (player.playbackState == Player.STATE_BUFFERING) arm()
    }

    /** Must be called on the main thread. Idempotent. */
    fun detach() {
        if (!isAttached) return
        isAttached = false
        disarm()
        player.removeListener(playerListener)
    }

    private fun arm() {
        if (timeoutMs <= 0L) return
        // If we were already armed, keep the original start timestamp so a rapid
        // READY→BUFFERING bounce doesn't hide a longer cumulative stall. In practice,
        // onPlaybackStateChanged fires disarm() on the READY transition which clears the
        // timestamp, so this path is only taken if we're re-arming from a cold start.
        if (bufferingStartedAtMs == 0L) {
            bufferingStartedAtMs = android.os.SystemClock.uptimeMillis()
        }
        mainHandler.removeCallbacks(timeoutRunnable)
        mainHandler.postDelayed(timeoutRunnable, timeoutMs)
    }

    private fun disarm() {
        bufferingStartedAtMs = 0L
        mainHandler.removeCallbacks(timeoutRunnable)
    }
}
