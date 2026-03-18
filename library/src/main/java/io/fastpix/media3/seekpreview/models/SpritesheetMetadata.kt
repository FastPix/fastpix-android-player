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
        require(rows > 0) { "Rows must be greater than 0" }
        require(columns > 0) { "Columns must be greater than 0" }
        require(frameWidth > 0) { "Frame width must be greater than 0" }
        require(frameHeight > 0) { "Frame height must be greater than 0" }
        require(frameCount > 0) { "Frame count must be greater than 0" }
        require(durationMs > 0) { "Duration must be greater than 0" }
        require(intervalMs > 0) { "Interval must be greater than 0" }
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
     * Validates that the metadata is consistent.
     */
    fun isValid(): Boolean {
        return rows > 0 && columns > 0 &&
                frameWidth > 0 && frameHeight > 0 &&
                frameCount > 0 && frameCount <= gridSize &&
                durationMs > 0 && intervalMs > 0
    }
}