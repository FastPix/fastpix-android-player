package io.fastpix.media3.prerender

import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import androidx.media3.common.MimeTypes

/**
 * Bookkeeping behind [DecoderBudget]: which owners hold an open video decoder, which hold a
 * pre-render grant, and how many decoders the device can really run at once.
 *
 * Pure, so the admission rules are unit-testable.
 */
internal class DecoderBudgetLedger(
    initialCeiling: Int,
    /** Decoders always left free for playback the SDK cannot see: other apps, ads, thumbnails. */
    private val reserve: Int,
) {
    /** Concurrent decoders this device can run, lowered each time it proves it can run fewer. */
    var ceiling: Int = initialCeiling
        private set

    private val open = HashSet<Any>()
    private val grants = HashSet<Any>()

    /** Decoders in use or promised: every open decoder, plus grants whose decoder has not opened. */
    val inUse: Int get() = open.size + grants.count { it !in open }

    fun onDecoderOpened(owner: Any) {
        open.add(owner)
    }

    fun onDecoderClosed(owner: Any) {
        open.remove(owner)
    }

    /**
     * Asks to open one more decoder speculatively, for pre-rendering. Refused when that would eat
     * into the reserve. Playback of the current item never asks — it is never refused.
     */
    fun tryGrant(owner: Any): Boolean {
        if (owner in grants) return true
        val alreadyOpen = owner in open
        val needed = if (alreadyOpen) 0 else 1
        if (inUse + needed > ceiling - reserve) return false
        grants.add(owner)
        return true
    }

    /** Returns [owner]'s grant: it stopped pre-rendering, or became the item playing. */
    fun release(owner: Any) {
        grants.remove(owner)
    }

    fun hasGrant(owner: Any): Boolean = owner in grants

    /**
     * A decoder failed to open while [inUse] were in use: the device's real limit is at most that.
     * Never raised again for the life of the process — a device that failed at N fails at N again.
     */
    fun onOpenFailed() {
        val observed = maxOf(1, open.size)
        if (observed < ceiling) ceiling = observed
    }
}

/**
 * Process-wide limit on how many video decoders pre-rendering may hold.
 *
 * Decoders are a device-wide resource, and running out is not graceful: the next decoder fails to
 * open with a fatal playback error — for whichever player asked, which may be the one the user is
 * watching. So every [io.fastpix.media3.core.FastPixPlayer] reports its decoders here, pre-rendering
 * asks before opening one, and the current item never has to.
 */
internal object DecoderBudget {

    private const val TAG = "FastPixDecoderBudget"

    /** Decoders kept free for playback outside the SDK. */
    private const val RESERVE = 1

    /**
     * Upper bound whatever the device reports. `getMaxSupportedInstances()` is often optimistic, and
     * pre-rendering past a handful of items buys nothing.
     */
    private const val MAX_CEILING = 8

    private val ledger: DecoderBudgetLedger by lazy {
        DecoderBudgetLedger(initialCeiling = probeCeiling(), reserve = RESERVE)
    }

    @Synchronized
    fun onDecoderOpened(owner: Any) = ledger.onDecoderOpened(owner)

    @Synchronized
    fun onDecoderClosed(owner: Any) = ledger.onDecoderClosed(owner)

    @Synchronized
    fun tryGrant(owner: Any): Boolean = ledger.tryGrant(owner)

    @Synchronized
    fun release(owner: Any) = ledger.release(owner)

    @Synchronized
    fun onOpenFailed() {
        ledger.onOpenFailed()
        Log.w(TAG, "Video decoder failed to open; pre-render ceiling now ${ledger.ceiling}")
    }

    /**
     * What the device's H.264 hardware decoder claims it can run concurrently, capped at
     * [MAX_CEILING]. Falls back to 2 — enough for one item plus one pre-render — when the device
     * does not say.
     */
    private fun probeCeiling(): Int {
        val reported = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .asSequence()
                .filter { info ->
                    !info.isEncoder && info.supportedTypes.any { it.equals(MimeTypes.VIDEO_H264, true) }
                }
                .sortedByDescending { info ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.isHardwareAccelerated
                    else !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
                }
                .firstOrNull()
                ?.getCapabilitiesForType(MimeTypes.VIDEO_H264)
                ?.maxSupportedInstances
        } catch (t: Throwable) {
            null
        }
        return (reported ?: 2).coerceIn(1, MAX_CEILING)
    }
}
