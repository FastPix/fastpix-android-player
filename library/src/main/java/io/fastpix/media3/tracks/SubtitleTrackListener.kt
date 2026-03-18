package io.fastpix.media3.tracks

/**
 * Listener for subtitle track discovery, selection, and cue updates.
 * All callbacks are invoked on the main thread.
 *
 * @see io.fastpix.media3.core.FastPixPlayer.addSubtitleTrackListener
 * @see io.fastpix.media3.core.FastPixPlayer.removeSubtitleTrackListener
 */
interface SubtitleTrackListener {

    /**
     * Called when the list of available subtitle tracks is loaded or updated.
     *
     * @param tracks Current list of subtitle tracks (may be empty for media without subtitles).
     */
    fun onSubtitlesLoaded(tracks: List<SubtitleTrack>) {
        // Default empty implementation
    }

    /**
     * Called when the active subtitle track has changed (e.g. after selection or [disableSubtitles]).
     *
     * @param track The track that is now active, or null if subtitles are disabled.
     */
    fun onSubtitleChange(track: SubtitleTrack?) {
        // Default empty implementation
    }

    /**
     * Called when a subtitle track operation fails.
     *
     * @param error The error that occurred.
     */
    fun onSubtitlesLoadedFailed(error: SubtitleTrackError) {
        // Default empty implementation
    }

    /**
     * Called when the current subtitle cues change (e.g. new text to display at current position).
     *
     * @param info Current cues to render. [SubtitleRenderInfo.cues] may be empty when no subtitle is active.
     */
    fun onSubtitleCueChange(info: SubtitleRenderInfo) {
        // Default empty implementation
    }
}
