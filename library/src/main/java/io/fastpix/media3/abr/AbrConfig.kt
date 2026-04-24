package io.fastpix.media3.abr

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection

/**
 * Tuning knobs for the network-aware ABR stack.
 *
 * There are two layers of adaptation:
 *
 * 1. **Within-network ABR** — once the cap is set, [AdaptiveTrackSelection] handles second-by-
 *    second rendition switching based on the measured bandwidth. The [minDurationForQualityIncreaseMs]
 *    / [maxDurationForQualityDecreaseMs] / [minDurationToRetainAfterDiscardMs] / [bandwidthFraction]
 *    fields tune that layer.
 *
 * 2. **Cross-network caps** — [NetworkAwareAbrController] clamps `maxVideoBitrate` by network type
 *    (Wi-Fi, 4G/5G, 3G, 2G, unknown). The [wifiMaxBitrateBps] / [cellular5g4gMaxBitrateBps] /
 *    [cellular3gMaxBitrateBps] / [cellular2gMaxBitrateBps] / [unknownNetworkMaxBitrateBps] fields
 *    define the per-bucket ceilings.
 *
 * Defaults are tuned for smooth playback over aggressive quality:
 * - Downgrade is aggressive on network type change (cap drops immediately; in-flight chunk is
 *   abandoned).
 * - Upgrade is gradual — we just raise the cap and let [AdaptiveTrackSelection] ramp up as buffered
 *   duration accumulates. We do NOT force a seek on upgrade.
 */
@UnstableApi
data class AbrConfig(
    val minDurationForQualityIncreaseMs: Int = AdaptiveTrackSelection.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
    val maxDurationForQualityDecreaseMs: Int = 10_000,
    val minDurationToRetainAfterDiscardMs: Int = AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
    val bandwidthFraction: Float = AdaptiveTrackSelection.DEFAULT_BANDWIDTH_FRACTION,
    /**
     * When true, [androidx.media3.exoplayer.upstream.DefaultBandwidthMeter] resets its estimate on
     * network-type transitions. Recommended; without this the meter carries stale Wi-Fi
     * measurements into cellular (or vice versa).
     */
    val resetBandwidthOnNetworkChange: Boolean = true,

    /** `maxVideoBitrate` (bps) applied on Wi-Fi / Ethernet. Default is unrestricted. */
    val wifiMaxBitrateBps: Int = Int.MAX_VALUE,
    /** `maxVideoBitrate` (bps) applied on cellular classified as 4G/5G. */
    val cellular5g4gMaxBitrateBps: Int = 3_000_000,
    /** `maxVideoBitrate` (bps) applied on cellular classified as 3G. */
    val cellular3gMaxBitrateBps: Int = 900_000,
    /** `maxVideoBitrate` (bps) applied on cellular classified as 2G. */
    val cellular2gMaxBitrateBps: Int = 300_000,
    /** `maxVideoBitrate` (bps) applied when network type is unknown (treat as worst-case). */
    val unknownNetworkMaxBitrateBps: Int = 300_000,

    /** Downstream (Kbps) at/above which cellular is classified as 4G/5G. */
    val cellularHighMinKbps: Int = 5_000,
    /** Downstream (Kbps) at/above which cellular is classified as 3G; below this → 2G. */
    val cellularMediumMinKbps: Int = 500,

    /**
     * On network downgrade (cap tightens), force Media3 to abandon the in-flight chunk by seeking
     * to the current playback position. Without this, an already-requested 1080p chunk keeps
     * draining over the slower link, blocking playback until it completes (or times out).
     *
     * The cost is a brief visible rebuffer; the benefit is playback resumes within seconds on the
     * new (capped) rendition instead of waiting minutes for the old chunk.
     */
    val abandonInflightLoadOnDowngrade: Boolean = true,

    /**
     * Minimum spacing between successive abandon-seeks (ms). Prevents thrashing when the
     * classification bounces around a threshold.
     */
    val abandonInflightCooldownMs: Long = 5_000L,

    /**
     * On a downgrade, the controller will skip the abandon-seek if there is at least this much
     * buffered content ahead of the current playback position (ms). Rationale: abandon-seek
     * forces Media3 to discard the in-flight chunk — but in the process it can also drop the
     * already-buffered content downloaded on the previous (faster) network. If the user already
     * has several seconds of high-quality buffer, it's smoother to play that buffer out and let
     * the new cap take effect at the next chunk boundary organically.
     *
     * Set to 0 to always abandon on downgrade (legacy behavior).
     */
    val healthyBufferThresholdMs: Long = 10_000L,

    /**
     * Debounce window (ms) for network transitions. Android flaps the default network several
     * times during a handoff (WiFi → cellular or vice versa) within a few hundred milliseconds.
     * Instead of reacting to every flap — which would repeatedly apply/remove the cap and even
     * trigger abandon-seeks — we wait for [networkDebounceMs] of quiet before applying the
     * latest observed network. Set to 0 to apply changes immediately.
     */
    val networkDebounceMs: Long = 400L,

    /**
     * If the player sits in `Player.STATE_BUFFERING` continuously for this many milliseconds
     * without recovering, emit a [androidx.media3.common.PlaybackException] with a
     * network-stall error code via the normal `PlaybackListener.onError` channel so the host
     * app can show a "Network too slow" message instead of an infinite spinner.
     *
     * Set to 0 to disable. Default 30 s.
     */
    val playbackStallTimeoutMs: Long = 30_000L,

    /**
     * When true, the ABR stack writes diagnostic lines to logcat under the `FastPixAbr` tag:
     * network transitions, applied caps, abandoned chunks, rendition switches. Off by default.
     */
    val enableAbrDiagnosticLogging: Boolean = false,
) {
    companion object {
        @JvmStatic
        val DEFAULT: AbrConfig = AbrConfig()
    }
}
