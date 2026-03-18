package io.fastpix.media3.tracks

/**
 * SDK model for an audio track. Hides Media3 internals.
 *
 * Use [id] to switch tracks via [io.fastpix.media3.core.FastPixPlayer.setAudioTrack].
 */
data class AudioTrack(
    val id: String,

    val languageCode: String?,
    val languageName: String?,
    val label: String?,

    val isSelected: Boolean,
    val isPlayable: Boolean,
    val isDefault: Boolean,

    val role: String?,
    val channels: String?,
    val codec: String?,
    val bitrate: Int?,
    val groupId: String?
)
