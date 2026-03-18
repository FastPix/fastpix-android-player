package io.fastpix.media3.tracks

/**
 * Error related to subtitle track selection or loading.
 */
sealed class SubtitleTrackError {
    /** Track with the given ID was not found. */
    data class TrackNotFound(val trackId: String) : SubtitleTrackError()

    /** Track is not playable (not supported by the device or format). */
    data class TrackNotPlayable(val trackId: String) : SubtitleTrackError()

    /** Selection could not be applied (e.g. invalid state). */
    data class SelectionFailed(val trackId: String, val message: String?) : SubtitleTrackError()

    /** Player is not in a valid state for track switching (e.g. no media loaded, not ready). */
    data class PlayerNotReady(val trackId: String) : SubtitleTrackError()
}
