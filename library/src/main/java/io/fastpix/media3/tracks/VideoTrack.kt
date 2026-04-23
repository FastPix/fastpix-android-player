package io.fastpix.media3.tracks

/**
 * SDK model for a video quality track. Hides Media3 internals.
 *
 * Use [id] to switch quality via [io.fastpix.media3.core.FastPixPlayer.setVideoQuality].
 */
data class VideoTrack(
    val id: String,
    val width: Int?,
    val height: Int?,
    val bitrate: Int?,
    val label: String?,
    val isSelected: Boolean,
    val isAuto: Boolean
)
