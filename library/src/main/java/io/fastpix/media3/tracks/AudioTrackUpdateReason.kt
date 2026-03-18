package io.fastpix.media3.tracks

/**
 * Reason for an audio track list update.
 */
enum class AudioTrackUpdateReason {
    /** Initial load of tracks (e.g. when media is first ready). */
    INITIAL,

    /** Loaded media changed (new source). */
    MEDIA_CHANGED,

    /** Track list updated (e.g. dynamic HLS variants). */
    TRACKS_UPDATED
}
