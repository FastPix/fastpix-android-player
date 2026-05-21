package io.fastpix.media3.seekpreview.models

import android.graphics.Bitmap

/**
 * Metadata for a sprite sheet containing video frame thumbnails.
 *
 * @property rows Number of rows in the sprite sheet grid
 * @property columns Number of columns in the sprite sheet grid
 * @property frameWidth Width of each frame in pixels
 * @property frameHeight Height of each frame in pixels
 * @property frameCount Total number of frames in the sprite sheet
 * @property durationMs Total duration of the video in milliseconds
 * @property intervalMs Time interval between frames in milliseconds
 * @property bitmap The thumbnail bitmap for the current seek position, or null if unavailable
 * @property timestampMs The seek position in milliseconds, or null when not associated with a seek
 */
data class SpritesheetMetadata(
    val rows: Int,
    val columns: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val frameCount: Int,
    val durationMs: Long,
    val intervalMs: Long,
    val bitmap: Bitmap? = null,
    val timestampMs: Long? = null
) {

    init {
        // Allow zero values so [TIMESTAMP_ONLY] can be constructed for previews
        // delivered when no spritesheet is loaded. Parsers still call [isValid]
        // before treating an instance as a real spritesheet.
        require(rows >= 0) { "Rows must be non-negative" }
        require(columns >= 0) { "Columns must be non-negative" }
        require(frameWidth >= 0) { "Frame width must be non-negative" }
        require(frameHeight >= 0) { "Frame height must be non-negative" }
        require(frameCount >= 0) { "Frame count must be non-negative" }
        require(durationMs >= 0) { "Duration must be non-negative" }
        require(intervalMs >= 0) { "Interval must be non-negative" }
        require(frameCount <= rows * columns) {
            "Frame count ($frameCount) cannot exceed grid size (${rows * columns})"
        }
    }

    /**
     * Total grid size (rows * columns)
     */
    val gridSize: Int
        get() = rows * columns

    /**
     * Validates that the metadata represents a real, usable spritesheet.
     */
    fun isValid(): Boolean {
        return rows > 0 && columns > 0 &&
                frameWidth > 0 && frameHeight > 0 &&
                frameCount > 0 && frameCount <= gridSize &&
                durationMs > 0 && intervalMs > 0
    }

    companion object {
        /**
         * Sentinel delivered to [io.fastpix.media3.seekpreview.listeners.SeekPreviewListener.onSpritesheetLoaded]
         * when there is no spritesheet (timestamp-only mode). Copy with the current
         * [timestampMs] before delivering; [bitmap] stays null.
         */
        val TIMESTAMP_ONLY: SpritesheetMetadata = SpritesheetMetadata(
            rows = 0,
            columns = 0,
            frameWidth = 0,
            frameHeight = 0,
            frameCount = 0,
            durationMs = 0,
            intervalMs = 0
        )
    }
}