package io.fastpix.media3.buffer

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * Buffering thresholds that decide **how soon the first frame appears** and how much media the
 * player keeps ahead of the playhead.
 *
 * The field that matters most for perceived start-up latency is [bufferForPlaybackMs]: the player
 * renders nothing until it has buffered that much media. Media3's own default is
 * [DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS]; the SDK default below is lower, which trades
 * a slightly higher chance of an early re-buffer for a visibly faster start.
 *
 * Three presets cover the common cases:
 * - [DEFAULT] — applied when nothing is configured. Faster start than Media3 stock, everything else
 *   left alone.
 * - [FEED] — for reel / short-form feeds where the user swipes between items constantly.
 * - [MEDIA3_DEFAULT] — restores Media3's stock values exactly, for anyone who wants the old
 *   behaviour back.
 *
 * @see io.fastpix.media3.core.FastPixPlayer.Builder.setBufferConfig
 */
@UnstableApi
data class BufferConfig(
    /** Minimum media buffered ahead of the playhead before the loader may pause (ms). */
    val minBufferMs: Int = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,

    /** Maximum media buffered ahead of the playhead (ms). */
    val maxBufferMs: Int = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,

    /**
     * Media that must be buffered before playback starts or resumes after a seek (ms).
     *
     * This is the single biggest lever on "time to first frame". Media3 stock is
     * [DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS] (1000 ms); the SDK ships 500 ms.
     */
    val bufferForPlaybackMs: Int = 500,

    /** Media that must be buffered before playback resumes after a re-buffer (ms). */
    val bufferForPlaybackAfterRebufferMs: Int = 1_500,

    /**
     * Target buffer size in bytes, or [C.LENGTH_UNSET] to let Media3 derive it from the selected
     * renditions.
     */
    val targetBufferBytes: Int = C.LENGTH_UNSET,

    /**
     * When true the loader keeps buffering to [minBufferMs] even after [targetBufferBytes] is
     * reached. Improves smoothness on low-bitrate content at the cost of memory; leave false for
     * general-purpose playback where a stream may be 4K.
     */
    val prioritizeTimeOverSizeThresholds: Boolean = false,

    /** Media retained *behind* the playhead (ms). Makes short backwards seeks instant. */
    val backBufferDurationMs: Int = 0,

    /** Whether the back buffer is retained from the last keyframe. */
    val retainBackBufferFromKeyframe: Boolean = false,
) {
    init {
        require(minBufferMs > 0) { "minBufferMs must be > 0, was $minBufferMs" }
        require(maxBufferMs >= minBufferMs) {
            "maxBufferMs ($maxBufferMs) must be >= minBufferMs ($minBufferMs)"
        }
        require(bufferForPlaybackMs > 0) {
            "bufferForPlaybackMs must be > 0, was $bufferForPlaybackMs"
        }
        require(bufferForPlaybackMs <= minBufferMs) {
            "bufferForPlaybackMs ($bufferForPlaybackMs) must be <= minBufferMs ($minBufferMs)"
        }
        require(bufferForPlaybackAfterRebufferMs > 0) {
            "bufferForPlaybackAfterRebufferMs must be > 0, was $bufferForPlaybackAfterRebufferMs"
        }
        require(bufferForPlaybackAfterRebufferMs <= minBufferMs) {
            "bufferForPlaybackAfterRebufferMs ($bufferForPlaybackAfterRebufferMs) must be <= " +
                    "minBufferMs ($minBufferMs)"
        }
        require(backBufferDurationMs >= 0) {
            "backBufferDurationMs must be >= 0, was $backBufferDurationMs"
        }
    }

    /**
     * Builds the Media3 [LoadControl] these thresholds describe. Internal: apps configure the
     * player through [io.fastpix.media3.core.FastPixPlayer.Builder.setBufferConfig] instead.
     */
    internal fun toLoadControl(): LoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs,
            )
            .setTargetBufferBytes(targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(prioritizeTimeOverSizeThresholds)
            .setBackBuffer(backBufferDurationMs, retainBackBufferFromKeyframe)
            .build()

    companion object {
        /**
         * SDK default. Halves Media3's start threshold (1000 ms -> 500 ms) and shortens the
         * post-rebuffer threshold; ahead-buffer sizing is untouched.
         */
        @JvmField
        val DEFAULT: BufferConfig = BufferConfig()

        /**
         * Tuned for reel / short-form feeds: start as early as possible, and don't buffer far
         * ahead — in a feed the user usually swipes away long before a 50 s buffer is watched, so
         * a deep buffer is wasted data.
         */
        @JvmField
        val FEED: BufferConfig = BufferConfig(
            minBufferMs = 10_000,
            maxBufferMs = 20_000,
            bufferForPlaybackMs = 250,
            bufferForPlaybackAfterRebufferMs = 1_000,
            prioritizeTimeOverSizeThresholds = true,
            backBufferDurationMs = 5_000,
            retainBackBufferFromKeyframe = true,
        )

        /** Media3's stock thresholds, for restoring pre-2.1.0 behaviour exactly. */
        @JvmField
        val MEDIA3_DEFAULT: BufferConfig = BufferConfig(
            minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            maxBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            bufferForPlaybackMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            bufferForPlaybackAfterRebufferMs =
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            targetBufferBytes = DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES,
            prioritizeTimeOverSizeThresholds =
                DefaultLoadControl.DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,
            backBufferDurationMs = DefaultLoadControl.DEFAULT_BACK_BUFFER_DURATION_MS,
            retainBackBufferFromKeyframe = false,
        )
    }
}
