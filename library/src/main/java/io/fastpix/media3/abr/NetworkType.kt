package io.fastpix.media3.abr

/**
 * Coarse classification of the device's active network, used by [NetworkAwareAbrController] to
 * pick a video bitrate cap.
 *
 * The classification is deliberately coarse — finer cellular subtype detection would require
 * `READ_PHONE_STATE` (or `READ_BASIC_PHONE_STATE` on API 33+) and the runtime-permission dance
 * that goes with it. Instead we infer from
 * [android.net.NetworkCapabilities.getLinkDownstreamBandwidthKbps], which the OS reports without
 * extra permissions.
 */
enum class NetworkType {
    /** Wi-Fi or Ethernet. Treated as unrestricted. */
    WIFI,

    /** Cellular with link downstream ≥ [NetworkMonitor.cellularHighMinKbps] (4G/5G). */
    CELLULAR_5G_4G,

    /** Cellular with link downstream between [NetworkMonitor.cellularMediumMinKbps] and the high threshold (3G). */
    CELLULAR_3G,

    /** Cellular with very low link downstream (2G / GPRS / EDGE). */
    CELLULAR_2G,

    /** Network is present but transport or downstream is unknown — treated conservatively. */
    UNKNOWN,

    /** No network. */
    OFFLINE,
}
