package io.fastpix.media3.prerender

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import io.fastpix.media3.playlist.PlaylistItem
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pre-rendering for a single [io.fastpix.media3.PlayerView] that moves through a playlist.
 *
 * A second, silent player decodes each upcoming entry in [PrerenderConfig]'s window into the view's
 * hidden capture surface, and the first frame is copied out as a bitmap. When the main player moves
 * to that entry, the view shows the bitmap at once — the first frame, standing in as the loader —
 * and drops it when the main player's own first frame lands. No black frame in between.
 *
 * Entries are captured one at a time, nearest first, and the capture player stops after each: it
 * holds a decoder for well under a second per entry, and only when [DecoderBudget] grants one.
 * Capturing waits until the main player has its own first frame on screen: starting both at once
 * made them compete for network and decoder, and slowed the very transition this exists to hide.
 * It is built from the main player's preload manager builder, so it reads through the same cache
 * and bandwidth estimate — its download warms the bytes the main player is about to need.
 *
 * Main thread only.
 */
@UnstableApi
internal class SingleViewPrerenderer(
    private val capturePlayer: ExoPlayer,
    private val config: PrerenderConfig,
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var host: PrerenderSurfaceHost? = null
    private var items: List<PlaylistItem> = emptyList()
    private var currentIndex: Int = NO_INDEX

    /** Captured first frames, by entry. Identity-keyed: equal entries may be distinct positions. */
    private val frames = IdentityHashMap<PlaylistItem, Bitmap>()

    /**
     * Failed captures per entry. One retry covers a transient miss (a copy racing the surface, a
     * network blip); after that the entry loads without a bridge until the playlist is replaced.
     */
    private val failures = IdentityHashMap<PlaylistItem, Int>()

    /** The entry being captured, and a counter that invalidates callbacks from earlier captures. */
    private var capturing: PlaylistItem? = null
    private var captureGeneration = 0L
    private var released = false

    /** Whether the main player has shown the current entry; captures wait until it has. */
    private var primarySettled = false
    private val settleFallback = Runnable { onPrimaryFirstFrame() }

    private val captureListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            val item = capturing ?: return
            copyFrame(item, captureGeneration)
        }

        override fun onPlayerError(error: PlaybackException) {
            capturing?.let { failCapture(it) }
        }
    }

    /** The capture player is not a FastPixPlayer, so it reports its own decoder to the budget. */
    private val decoderListener = object : AnalyticsListener {
        override fun onVideoEnabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters,
        ) {
            DecoderBudget.onDecoderOpened(this@SingleViewPrerenderer)
        }

        override fun onVideoDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters,
        ) {
            DecoderBudget.onDecoderClosed(this@SingleViewPrerenderer)
        }
    }

    init {
        capturePlayer.volume = 0f
        capturePlayer.playWhenReady = false
        capturePlayer.addListener(captureListener)
        capturePlayer.addAnalyticsListener(decoderListener)
    }

    /** The view to capture into and show frames on; null while no view shows the player. */
    fun setHost(host: PrerenderSurfaceHost?) {
        if (this.host === host) return
        cancelCapture()
        this.host = host
        if (host != null) {
            capturePlayer.setVideoSurfaceView(host.captureSurfaceView)
        } else {
            capturePlayer.clearVideoSurface()
        }
        scheduleNext()
    }

    /** Follows a playlist replacement or edit. Frames of entries still listed are kept. */
    fun setPlaylist(items: List<PlaylistItem>) {
        this.items = items.toList()
        val listed: MutableSet<PlaylistItem> = Collections.newSetFromMap(IdentityHashMap())
        listed.addAll(this.items)
        frames.keys.retainAll(listed)
        failures.keys.retainAll(listed)
        if (capturing?.let { it !in listed } == true) cancelCapture()
        if (items.isEmpty()) currentIndex = NO_INDEX
    }

    /** Re-centres on [index]: frames outside the window are dropped and missing ones captured. */
    fun onCurrentIndexChanged(index: Int) {
        currentIndex = index
        val window: MutableSet<PlaylistItem> = Collections.newSetFromMap(IdentityHashMap())
        window.addAll(windowItems())
        // The entry now playing keeps its frame: going back to it restarts it at frame 0.
        items.getOrNull(index)?.let { window.add(it) }
        frames.keys.retainAll(window)
        // The main player is starting: get out of its way until it has.
        cancelCapture()
        primarySettled = false
        mainHandler.removeCallbacks(settleFallback)
        // An entry that never renders a frame (audio only, an error) must not stall capturing.
        mainHandler.postDelayed(settleFallback, SETTLE_FALLBACK_MS)
    }

    /** The main player's first frame of the current entry is on screen; capturing may resume. */
    fun onPrimaryFirstFrame() {
        mainHandler.removeCallbacks(settleFallback)
        if (primarySettled) return
        primarySettled = true
        scheduleNext()
    }

    /** The captured first frame of [item], if it has one. */
    fun frameFor(item: PlaylistItem): Bitmap? = frames[item]

    fun release() {
        if (released) return
        released = true
        mainHandler.removeCallbacks(settleFallback)
        cancelCapture()
        capturePlayer.release()
        DecoderBudget.onDecoderClosed(this)
        DecoderBudget.release(this)
        frames.clear()
        host = null
    }

    /** Window entries, nearest first. */
    private fun windowItems(): List<PlaylistItem> =
        config.windowIndices(currentIndex, items.size).map { items[it] }

    private fun scheduleNext() {
        if (released || capturing != null || host == null || !primarySettled) return
        val next = windowItems().firstOrNull { item ->
            item !in frames && (failures[item] ?: 0) < MAX_CAPTURE_ATTEMPTS &&
                    // Protected content's secure decoders are scarcer still.
                    item.mediaItem.localConfiguration?.drmConfiguration == null
        } ?: return
        // Refused: retried on the next move, by when a decoder may have freed up.
        if (!DecoderBudget.tryGrant(this)) return

        capturing = next
        val generation = ++captureGeneration
        capturePlayer.setMediaItem(next.mediaItem)
        capturePlayer.prepare()
        mainHandler.postDelayed({
            if (generation == captureGeneration && capturing === next) failCapture(next)
        }, CAPTURE_TIMEOUT_MS)
    }

    private fun copyFrame(item: PlaylistItem, generation: Long) {
        val host = host ?: return failCapture(item)
        val (width, height) = frameSize(host)
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (t: OutOfMemoryError) {
            return failCapture(item)
        }
        try {
            PixelCopy.request(host.captureSurfaceView, bitmap, { result ->
                if (generation != captureGeneration || capturing !== item) return@request
                if (result == PixelCopy.SUCCESS) {
                    frames[item] = bitmap
                    finishCapture()
                } else {
                    failCapture(item)
                }
            }, mainHandler)
        } catch (t: IllegalArgumentException) {
            // The capture surface went away between the frame and the copy.
            failCapture(item)
        }
    }

    /**
     * The video's size, scaled down to just cover the view — the frame is shown at the view's size,
     * so decoding more pixels than that only costs memory.
     */
    private fun frameSize(host: PrerenderSurfaceHost): Pair<Int, Int> {
        val video = capturePlayer.videoSize
        val videoWidth = if (video.width > 0) video.width * video.pixelWidthHeightRatio else 0f
        val videoHeight = video.height.toFloat()
        if (videoWidth <= 0f || videoHeight <= 0f) {
            return max(host.viewWidth, 1) to max(host.viewHeight, 1)
        }
        val scale = if (host.viewWidth > 0 && host.viewHeight > 0) {
            min(1f, max(host.viewWidth / videoWidth, host.viewHeight / videoHeight))
        } else {
            1f
        }
        return max(1, (videoWidth * scale).roundToInt()) to max(1, (videoHeight * scale).roundToInt())
    }

    private fun finishCapture() {
        capturing = null
        captureGeneration++
        // Stopping frees the decoder; the next capture takes a fresh grant.
        capturePlayer.stop()
        DecoderBudget.release(this)
        scheduleNext()
    }

    private fun failCapture(item: PlaylistItem) {
        failures[item] = (failures[item] ?: 0) + 1
        finishCapture()
    }

    private fun cancelCapture() {
        if (capturing == null) return
        capturing = null
        captureGeneration++
        capturePlayer.stop()
        DecoderBudget.release(this)
    }

    private companion object {
        const val NO_INDEX = -1

        /** A capture taking longer than this is abandoned; the entry then loads without a bridge. */
        const val CAPTURE_TIMEOUT_MS = 8_000L

        const val MAX_CAPTURE_ATTEMPTS = 2

        /** Capturing resumes after this even if the main player never shows a frame. */
        const val SETTLE_FALLBACK_MS = 1_500L
    }
}
