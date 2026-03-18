package io.fastpix.media3.seekpreview.parser

import com.google.gson.annotations.SerializedName

/**
 * FastPix spritesheet JSON format from e.g.
 * https://images.fastpix.io/{playbackId}/spritesheet.json
 *
 * Example:
 * ```json
 * {
 *   "url": "https://images.fastpix.io/.../spritesheet.jpg",
 *   "tile_width": 256,
 *   "tile_height": 160,
 *   "duration": 596.0,
 *   "tiles": [{"start": 0.0, "x": 0, "y": 0}, ...]
 * }
 * ```
 */
internal data class FastPixSpritesheetJson(
    val url: String?,
    @SerializedName("tile_width") val tileWidth: Int,
    @SerializedName("tile_height") val tileHeight: Int,
    val duration: Double,
    val tiles: List<FastPixTile>?
)

internal data class FastPixTile(
    val start: Double,
    val x: Int,
    val y: Int
)
