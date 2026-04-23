package io.fastpix.media3.abr

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Network-aware adaptive bitrate controller.
 *
 * Responsibilities:
 * - Subscribe to [NetworkMonitor] and translate the current [NetworkType] into a
 *   `maxVideoBitrate` cap via [AbrConfig].
 * - Apply the cap to the supplied [DefaultTrackSelector] so within-network ABR picks the best
 *   rendition under the ceiling.
 * - On downgrade (tighter cap), abandon the in-flight chunk by seeking to the current position.
 *   This is the only way to unblock playback when Media3 has a large chunk already requested from
 *   the previous (faster) network — track-selection changes alone don't cancel in-flight loads.
 * - On upgrade (looser cap), just raise the cap and let [AdaptiveTrackSelection] ramp up
 *   gradually on buffered-duration confidence. Do NOT force a seek; that would cause an
 *   unnecessary rebuffer on an already-healthy link.
 *
 * Threading:
 * - [NetworkMonitor.Listener] fires on a background thread; we post to the main Looper before
 *   touching the player or track selector.
 * - [attach] / [detach] must be called on the main thread.
 *
 * This controller is deliberately free of UI or SDK dependencies — it only needs the player, the
 * track selector, and the monitor. Host apps that just want the behavior don't interact with it
 * directly; `FastPixPlayer` wires it up internally.
 */
@UnstableApi
class NetworkAwareAbrController(
    private val player: ExoPlayer,
    private val trackSelector: DefaultTrackSelector,
    private val networkMonitor: NetworkMonitor,
    private val config: AbrConfig,
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Last cap (bps) applied to the track selector. [Int.MAX_VALUE] means "no cap". */
    private var lastAppliedMaxBitrate: Int = Int.MAX_VALUE

    /** Network type from the most recent change we acted on; `null` until first notification. */
    private var lastNetworkType: NetworkType? = null

    /** [SystemClock.uptimeMillis] of the last abandon-seek; 0 means none yet. */
    private var lastAbandonAtMs: Long = 0L

    /**
     * True when a downgrade has happened with a healthy forward buffer, so we intentionally
     * skipped the immediate abandon-seek. When the buffer later depletes (player enters
     * `STATE_BUFFERING`), we abandon at that point — which is the cheapest time to do so,
     * because by then the stuck high-res chunk is blocking actual needed data.
     *
     * Cleared on upgrade, on successful abandon, and on playback state exiting buffering.
     */
    private var pendingAbandonOnStarve: Boolean = false

    private var isAttached = false

    /** Most recent network signal observed, pending debounce application. */
    private var pendingType: NetworkType? = null
    private var pendingKbps: Int = 0

    /**
     * Debounce runnable — fires after [AbrConfig.networkDebounceMs] of quiet and applies the
     * latest seen network. Repeatedly rescheduled while flaps keep coming in.
     */
    private val applyPendingRunnable = Runnable {
        val type = pendingType ?: return@Runnable
        onNetworkChanged(type, pendingKbps)
    }

    /**
     * NetworkMonitor listener. Runs on a background thread — hop to main, update the pending
     * signal, and schedule the debounced apply. If another transition arrives before the
     * debounce window elapses, we cancel the pending apply and reschedule. This collapses the
     * "WiFi → cell → WiFi → cell …" flapping seen during OS network handoffs into a single
     * decision once the state settles.
     */
    private val networkListener = NetworkMonitor.Listener { type, downstreamKbps ->
        mainHandler.post {
            pendingType = type
            pendingKbps = downstreamKbps
            mainHandler.removeCallbacks(applyPendingRunnable)
            val debounce = config.networkDebounceMs
            if (debounce <= 0L) {
                applyPendingRunnable.run()
            } else {
                mainHandler.postDelayed(applyPendingRunnable, debounce)
            }
        }
    }

    /**
     * Analytics listener. Two responsibilities:
     *  1. Diagnostic logging of actual rendition switches (when enabled).
     *  2. Triggering a deferred abandon-seek when the buffer finally depletes after a downgrade
     *     — see [pendingAbandonOnStarve] for the rationale.
     */
    private val analyticsListener = object : AnalyticsListener {
        override fun onDownstreamFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            mediaLoadData: MediaLoadData
        ) {
            if (!config.enableAbrDiagnosticLogging) return
            if (mediaLoadData.trackType != C.TRACK_TYPE_VIDEO) return
            val format = mediaLoadData.trackFormat ?: return
            log(
                "abr switch → ${format.height}p @ ${format.bitrate / 1_000}kbps " +
                    "(network=$lastNetworkType, cap=${formatCap(lastAppliedMaxBitrate)})"
            )
        }

        override fun onPlaybackStateChanged(
            eventTime: AnalyticsListener.EventTime,
            state: Int
        ) {
            if (state != Player.STATE_BUFFERING || !pendingAbandonOnStarve) return
            // Media3 enters STATE_BUFFERING for many reasons (format change, seek, network
            // loss, actual depletion). Before executing the deferred abandon, verify the
            // forward buffer really is drained — otherwise we'd throw away healthy content.
            val bufferedAheadMs = (player.bufferedPosition - player.currentPosition)
                .coerceAtLeast(0L)
            val threshold = config.healthyBufferThresholdMs
            if (threshold > 0 && bufferedAheadMs >= threshold) {
                log(
                    "STATE_BUFFERING but ${bufferedAheadMs}ms still buffered " +
                        "(≥ ${threshold}ms threshold) — not a true depletion, hold"
                )
                return
            }
            log("buffer depleted (${bufferedAheadMs}ms ahead) → executing deferred abandon")
            pendingAbandonOnStarve = false
            performAbandonSeek()
        }
    }

    /**
     * Starts listening to the network monitor and the player. Must be called on the main thread.
     * Idempotent.
     */
    fun attach() {
        if (isAttached) return
        isAttached = true
        player.addAnalyticsListener(analyticsListener)
        // NetworkMonitor.addListener fires once with the current state so we get an initial cap
        // applied immediately, without waiting for a network transition.
        networkMonitor.addListener(networkListener)
    }

    /**
     * Stops listening. After detach, the controller holds no references that would keep the
     * player or monitor alive. Idempotent.
     */
    fun detach() {
        if (!isAttached) return
        isAttached = false
        networkMonitor.removeListener(networkListener)
        player.removeAnalyticsListener(analyticsListener)
        mainHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Core decision point. Called on the main thread. Picks the cap for [type], applies it if
     * changed, and — on downgrade — abandons the in-flight chunk.
     */
    private fun onNetworkChanged(type: NetworkType, downstreamKbps: Int) {
        val previousType = lastNetworkType
        val previousCap = lastAppliedMaxBitrate
        lastNetworkType = type

        val targetCap = bitrateCapFor(type)
        // Android emits a `NetworkCapabilities` heartbeat every ~15s even when nothing's moved;
        // log only when the classification or derived cap actually changes so the diagnostic
        // output is signal, not noise.
        if (previousType != type || previousCap != targetCap) {
            log("network=$type (${downstreamKbps}kbps) → cap=${formatCap(targetCap)}")
        }

        applyCap(targetCap)

        // Downgrade detection: cap got tighter (smaller). We treat the initial notification
        // (previousType == null) as "not a downgrade" — the app is just starting up, and forcing
        // an abandon-seek before playback begins is wasteful.
        // OFFLINE is not a downgrade — it's "no network at all". Capping is harmless (nothing
        // is loading anyway) but triggering an abandon-seek would discard the buffered content
        // we still want to play during the outage. Also cancel any prior pending abandon: the
        // original in-flight chunk is irrelevant now because no chunks can complete.
        if (type == NetworkType.OFFLINE) {
            if (pendingAbandonOnStarve) {
                log("going OFFLINE → cancel pending abandon (buffer stays)")
                pendingAbandonOnStarve = false
            }
            return
        }

        // Recovering from OFFLINE → real network is not a downgrade either, even if the
        // recovered network's cap is tighter than whatever was set during the outage. Treat
        // it as a fresh baseline.
        if (previousType == null || previousType == NetworkType.OFFLINE) return

        val isDowngrade = targetCap < previousCap
        if (isDowngrade && config.abandonInflightLoadOnDowngrade) {
            abandonInflightLoad()
        } else if (!isDowngrade && pendingAbandonOnStarve) {
            // Upgrade or no cap change — any pending "abandon once buffer depletes" is stale
            // because the old in-flight chunk is no longer blocking (higher cap now allows it).
            log("cap relaxed before buffer drained → cancel pending abandon")
            pendingAbandonOnStarve = false
        }
    }

    private fun bitrateCapFor(type: NetworkType): Int = when (type) {
        NetworkType.WIFI -> config.wifiMaxBitrateBps
        NetworkType.CELLULAR_5G_4G -> config.cellular5g4gMaxBitrateBps
        NetworkType.CELLULAR_3G -> config.cellular3gMaxBitrateBps
        NetworkType.CELLULAR_2G -> config.cellular2gMaxBitrateBps
        NetworkType.OFFLINE -> config.cellular2gMaxBitrateBps
        NetworkType.UNKNOWN -> config.unknownNetworkMaxBitrateBps
    }

    /**
     * Writes the cap to the track selector. Clears any prior manual overrides of type video so
     * the two mechanisms (user-invoked fixed-quality override and network-aware cap) don't fight.
     * Manual video-quality overrides are a separate SDK concern applied via a different code path
     * (see `FastPixPlayer.setVideoQuality`); this controller never sets them.
     */
    private fun applyCap(bps: Int) {
        if (bps == lastAppliedMaxBitrate) return
        lastAppliedMaxBitrate = bps
        val params = trackSelector.parameters.buildUpon()
            .setMaxVideoBitrate(bps)
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
        trackSelector.setParameters(params)
    }

    /**
     * Forces Media3 to abandon the currently-loading chunk so playback can resume on the new
     * (tighter) cap within seconds instead of waiting for the old chunk to finish.
     *
     * Guard rails:
     * - Only if there's media loaded and the player isn't IDLE/ENDED.
     * - Respects [AbrConfig.abandonInflightCooldownMs] to prevent thrashing when the network
     *   classification oscillates around a threshold.
     * - Seeks to the current position (rounded non-negative) — same position is fine; Media3
     *   treats it as a reset of the load state.
     */
    /**
     * Entry point after a downgrade. If there's a healthy forward buffer, defers the actual
     * abandon-seek until the buffer depletes (see [analyticsListener]'s `onPlaybackStateChanged`).
     * Otherwise calls [performAbandonSeek] immediately.
     *
     * The deferred path preserves the pre-downgrade buffered content for smooth playback while
     * still guaranteeing that the stale in-flight chunk gets killed the moment it actually
     * starts blocking playback.
     */
    private fun abandonInflightLoad() {
        if (player.mediaItemCount == 0) return
        val state = player.playbackState
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) return

        val threshold = config.healthyBufferThresholdMs
        if (threshold > 0) {
            val bufferedAheadMs = (player.bufferedPosition - player.currentPosition)
                .coerceAtLeast(0L)
            if (bufferedAheadMs >= threshold && state != Player.STATE_BUFFERING) {
                log(
                    "defer abandon: ${bufferedAheadMs}ms buffered ahead " +
                        "(threshold ${threshold}ms) — will abandon when buffer depletes"
                )
                pendingAbandonOnStarve = true
                return
            }
        }
        performAbandonSeek()
    }

    /**
     * Issues the actual `seekTo(currentPosition)` that forces Media3 to drop the in-flight
     * chunk. Rate-limited by [AbrConfig.abandonInflightCooldownMs].
     */
    private fun performAbandonSeek() {
        if (player.mediaItemCount == 0) return
        val state = player.playbackState
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) return
        val now = SystemClock.uptimeMillis()
        if (now - lastAbandonAtMs < config.abandonInflightCooldownMs) return
        lastAbandonAtMs = now
        val pos = player.currentPosition.coerceAtLeast(0L)
        log("abandon in-flight load → seekTo(${pos}ms)")
        player.seekTo(pos)
    }

    private fun formatCap(bps: Int): String =
        if (bps == Int.MAX_VALUE) "UNCAPPED" else "${bps / 1_000}kbps"

    private fun log(msg: String) {
        if (config.enableAbrDiagnosticLogging) Log.d(TAG, msg)
    }

    companion object {
        private const val TAG = "FastPixAbr"
    }
}
