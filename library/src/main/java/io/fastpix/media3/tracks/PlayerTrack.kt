package io.fastpix.media3.tracks

/**
 * Unified internal track model. Not exposed in the public SDK.
 * Used by [TrackManager] to represent both audio and subtitle tracks;
 * mapped to [AudioTrack] or [SubtitleTrack] before returning to API consumers.
 *
 * Optional [channels] is set for audio tracks (from Format.channelCount).
 * Optional [width]/[height] are set for video tracks (from Format.width/height).
 * Optional [bitrate] can be set for audio/video tracks (from Format.bitrate).
 */
internal data class PlayerTrack(
    val id: String,
    val type: TrackType,

    val languageCode: String?,
    val languageName: String?,
    val label: String?,

    val isSelected: Boolean,
    val isPlayable: Boolean,
    val isDefault: Boolean,
    val isForced: Boolean,

    val role: String?,
    val codec: String?,

    val groupId: String?,
    val groupIndex: Int,
    val trackIndex: Int,

    /** Only for [TrackType.AUDIO]; from Format.channelCount. */
    val channels: String? = null,
    /** Only for [TrackType.VIDEO]; from Format.width. */
    val width: Int? = null,
    /** Only for [TrackType.VIDEO]; from Format.height. */
    val height: Int? = null,
    /** For [TrackType.AUDIO] and [TrackType.VIDEO]; from Format.bitrate. */
    val bitrate: Int? = null
)
