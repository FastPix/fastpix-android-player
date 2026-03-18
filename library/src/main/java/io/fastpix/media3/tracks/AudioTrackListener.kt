package io.fastpix.media3.tracks

/**
 * Listener for audio track discovery and selection events.
 * All callbacks are invoked on the main thread.
 *
 * @see io.fastpix.media3.core.FastPixPlayer.addAudioTrackListener
 * @see io.fastpix.media3.core.FastPixPlayer.removeAudioTrackListener
 */
interface AudioTrackListener {

    /**
     * Called when the list of available audio tracks is loaded or updated.
     * Triggered when media item loads, track list updates, or HLS dynamic tracks update.
     *
     * @param tracks Current list of audio tracks (may be empty for media without multiple tracks).
     * @param reason Why the list was updated.
     */
    fun onAudioTracksLoaded(
        tracks: List<AudioTrack>,
        reason: AudioTrackUpdateReason
    ) {
        // Default empty implementation
    }

    /**
     * Called when the active audio track has changed (e.g. after a successful switch).
     *
     * @param selectedTrack The track that is now active.
     */
    fun onAudioTracksChange(selectedTrack: AudioTrack) {
        // Default empty implementation
    }

    /**
     * Called when an audio track operation fails (invalid trackId, unsupported track,
     * invalid player state, or internal switching failure).
     *
     * @param error The error that occurred.
     */
    fun onAudioTracksLoadedFailed(error: AudioTrackError) {
        // Default empty implementation
    }

    /**
     * Called when a track switch is started or finished.
     * Optional; default is no-op. Kept for backward compatibility.
     *
     * @param isSwitching true when a switch has started, false when it has finished.
     */
    fun onAudioTrackSwitching(isSwitching: Boolean) {
        // Default empty implementation
    }
}
