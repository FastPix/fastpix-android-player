package io.fastpix.media3.abr

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Observes the device's active network and classifies it into a [NetworkType] plus the latest
 * OS-reported downstream bandwidth (Kbps).
 *
 * Thread-safety: listeners are stored in a [CopyOnWriteArrayList] and callbacks fire on whatever
 * thread `ConnectivityManager` uses to dispatch (background). Listeners must marshal to their own
 * thread if they touch UI / player state.
 *
 * Lifecycle: call [register] once the consumer is ready to receive updates, and [unregister] when
 * done. The monitor is reusable — you can call `register()` again after `unregister()`.
 *
 * Reliability note: [NetworkCapabilities.getLinkDownstreamBandwidthKbps] is the OS's *estimate*
 * of the link's nominal speed. It's usually accurate on Wi-Fi and on real cellular hardware; it
 * can be wildly optimistic on emulators that throttle at the packet layer rather than advertising
 * a slower link type. When it returns 0 (unknown), we classify cellular as [NetworkType.CELLULAR_2G]
 * conservatively so the cap defaults to the safest rendition.
 */
class NetworkMonitor(
    context: Context,
    /** Downstream ≥ this value (kbps) is classified as 4G/5G. */
    val cellularHighMinKbps: Int = 5_000,
    /** Downstream ≥ this (and < [cellularHighMinKbps]) is classified as 3G. */
    val cellularMediumMinKbps: Int = 500,
) {

    /**
     * Listener for network changes. Invoked on a background thread — marshal to main if needed.
     */
    fun interface Listener {
        fun onNetworkChanged(type: NetworkType, downstreamKbps: Int)
    }

    private val appContext: Context = context.applicationContext
    private val connectivityManager: ConnectivityManager? =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile
    var currentType: NetworkType = NetworkType.UNKNOWN
        private set

    @Volatile
    var currentDownstreamKbps: Int = 0
        private set

    private var isRegistered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            updateStateFrom(capabilities)
        }

        override fun onLost(network: Network) {
            val changed = currentType != NetworkType.OFFLINE
            currentType = NetworkType.OFFLINE
            currentDownstreamKbps = 0
            if (changed) notifyListeners()
        }
    }

    /**
     * Registers the underlying `ConnectivityManager` callback and seeds [currentType] from the
     * currently active network so listeners added immediately afterwards see a non-UNKNOWN state.
     */
    @SuppressLint("MissingPermission")
    fun register() {
        val cm = connectivityManager ?: return
        if (isRegistered) return
        try {
            cm.activeNetwork
                ?.let { cm.getNetworkCapabilities(it) }
                ?.also { seedInitialState(it) }

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
            isRegistered = true
        } catch (_: Throwable) {
            // Missing ACCESS_NETWORK_STATE or other failure: we just won't observe transitions.
            // Consumers still function with whatever initial state they saw.
        }
    }

    /** Unregisters the callback. Safe to call multiple times. */
    fun unregister() {
        val cm = connectivityManager ?: return
        if (!isRegistered) return
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Throwable) {
            // No-op
        } finally {
            isRegistered = false
        }
    }

    /**
     * Adds a listener. Immediately fires once with the current state so late-attaching consumers
     * don't have to wait for the next network transition to receive a cap decision.
     */
    fun addListener(listener: Listener) {
        if (listeners.addIfAbsent(listener)) {
            listener.onNetworkChanged(currentType, currentDownstreamKbps)
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun seedInitialState(capabilities: NetworkCapabilities) {
        currentType = classify(capabilities)
        currentDownstreamKbps = capabilities.linkDownstreamBandwidthKbps.coerceAtLeast(0)
    }

    private fun updateStateFrom(capabilities: NetworkCapabilities) {
        val newType = classify(capabilities)
        val newKbps = capabilities.linkDownstreamBandwidthKbps.coerceAtLeast(0)
        val changed = newType != currentType || newKbps != currentDownstreamKbps
        currentType = newType
        currentDownstreamKbps = newKbps
        if (changed) notifyListeners()
    }

    /**
     * Maps a [NetworkCapabilities] to a [NetworkType] using transport + downstream bandwidth.
     *
     * Wi-Fi and Ethernet are treated as "WIFI" (unrestricted). Cellular is split into three
     * buckets via [cellularHighMinKbps] / [cellularMediumMinKbps]. When the cellular downstream
     * is reported as 0 (unknown), we return [NetworkType.CELLULAR_2G] so the controller errs on
     * the side of low quality rather than assuming a fast link.
     */
    private fun classify(caps: NetworkCapabilities): NetworkType {
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val kbps = caps.linkDownstreamBandwidthKbps
                when {
                    kbps >= cellularHighMinKbps -> NetworkType.CELLULAR_5G_4G
                    kbps >= cellularMediumMinKbps -> NetworkType.CELLULAR_3G
                    else -> NetworkType.CELLULAR_2G
                }
            }
            else -> NetworkType.UNKNOWN
        }
    }

    private fun notifyListeners() {
        val type = currentType
        val kbps = currentDownstreamKbps
        listeners.forEach { it.onNetworkChanged(type, kbps) }
    }
}
