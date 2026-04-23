package io.fastpix.media3.tracks

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi

/**
 * Applies audio and subtitle track selection to the player.
 * Does not call prepare(), reload media, or reset playback.
 * Preserves playback position and AV sync.
 */
@UnstableApi
internal class MediaSelectionController(
    private val player: ExoPlayer,
    private val trackManager: TrackManager
) {

    /**
     * Applies selection for the audio track with [trackId].
     * Call on main thread only.
     *
     * @return The selected [AudioTrack] if successful, null if track not found or selection not applied.
     */
    fun setAudioTrack(trackId: String): AudioTrack? {
        val track = trackManager.findAudioTrackById(trackId) ?: return null
        if (!track.isPlayable) return null
        val indices = trackManager.findAudioSelectionIndices(trackId) ?: return null
        val groups = player.currentTracks.groups
        if (indices.groupIndex < 0 || indices.groupIndex >= groups.size) return null
        val group = groups[indices.groupIndex]
        val mediaTrackGroup = group.mediaTrackGroup
        if (indices.trackIndex < 0 || indices.trackIndex >= group.length) return null
        val override = TrackSelectionOverride(mediaTrackGroup, indices.trackIndex)
        val parameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .build()
        player.trackSelectionParameters = parameters
        return track
    }

    /**
     * Applies selection for the subtitle track with [trackId].
     * Enables text tracks and overrides to the given track. Call on main thread only.
     *
     * @return The selected [SubtitleTrack] if successful, null if track not found or selection not applied.
     */
    fun setSubtitleTrack(trackId: String): SubtitleTrack? {
        val track = trackManager.findSubtitleTrackById(trackId) ?: return null
        if (!track.isPlayable) return null
        val indices = trackManager.findSubtitleSelectionIndices(trackId) ?: return null
        val groups = player.currentTracks.groups
        if (indices.groupIndex < 0 || indices.groupIndex >= groups.size) return null
        val group = groups[indices.groupIndex]
        val mediaTrackGroup = group.mediaTrackGroup
        if (indices.trackIndex < 0 || indices.trackIndex >= group.length) return null
        val override = TrackSelectionOverride(mediaTrackGroup, indices.trackIndex)
        val parameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(override)
            .build()
        player.trackSelectionParameters = parameters
        return track
    }

    /**
     * Disables subtitle track selection. Text tracks are disabled; forced subtitles may still
     * render per Media3 behavior. Call on main thread only.
     */
    fun disableSubtitles() {
        val parameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        player.trackSelectionParameters = parameters
    }

    /**
     * Applies a fixed video quality for the given [trackId]. Call on main thread only.
     *
     * @return The selected [VideoTrack] if successful, null otherwise.
     */
    fun setVideoTrack(trackId: String): VideoTrack? {
        val track = trackManager.findVideoTrackById(trackId) ?: return null
        val indices = trackManager.findVideoSelectionIndices(trackId) ?: return null
        val groups = player.currentTracks.groups
        if (indices.groupIndex < 0 || indices.groupIndex >= groups.size) return null
        val group = groups[indices.groupIndex]
        val mediaTrackGroup = group.mediaTrackGroup
        if (indices.trackIndex < 0 || indices.trackIndex >= group.length) return null
        val override = TrackSelectionOverride(mediaTrackGroup, listOf(indices.trackIndex))
        val parameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .build()
        player.trackSelectionParameters = parameters
        return track
    }

    /**
     * Clears any fixed video-quality override and re-enables ABR.
     * Call on main thread only.
     */
    fun enableAutoVideoQuality() {
        val parameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
        player.trackSelectionParameters = parameters
    }
}
