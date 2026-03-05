package io.fastpix.player.seekpreview.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

/**
 * Configuration for generating a sprite sheet from video.
 * 
 * @property videoFile The video file to extract frames from
 * @property outputDirectory Directory to save the generated sprite sheet
 * @property frameCount Number of frames to extract (default: calculated from duration)
 * @property frameWidth Width of each frame in pixels (default: 160)
 * @property frameHeight Height of each frame in pixels (default: 90)
 * @property quality JPEG quality for frames (1-100, default: 85)
 * @property rows Number of rows in the sprite sheet grid (auto-calculated if null)
 * @property columns Number of columns in the sprite sheet grid (auto-calculated if null)
 */
@Parcelize
data class SpritesheetConfig(
    val videoFile: File,
    val outputDirectory: File,
    val frameCount: Int = 0, // 0 = auto-calculate
    val frameWidth: Int = 160,
    val frameHeight: Int = 90,
    val quality: Int = 85,
    val rows: Int? = null,
    val columns: Int? = null
) : Parcelable {
    
    init {
        require(videoFile.exists()) { "Video file does not exist: ${videoFile.path}" }
        require(frameWidth > 0) { "Frame width must be greater than 0" }
        require(frameHeight > 0) { "Frame height must be greater than 0" }
        require(frameCount >= 0) { "Frame count must be non-negative" }
        require(quality in 1..100) { "Quality must be between 1 and 100" }
        rows?.let { require(it > 0) { "Rows must be greater than 0" } }
        columns?.let { require(it > 0) { "Columns must be greater than 0" } }
    }
}
