package io.fastpix.media3.tracks

/**
 * Internal enum for track kinds. Used by [TrackManager] only.
 * Future track types (e.g. VIDEO) may be added later.
 */
internal enum class TrackType {
    AUDIO,
    SUBTITLE
}
