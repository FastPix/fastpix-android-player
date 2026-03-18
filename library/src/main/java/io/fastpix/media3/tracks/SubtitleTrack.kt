package io.fastpix.media3.tracks

/**
 * SDK model for a subtitle track. Hides Media3 internals.
 *
 * Use [id] to switch tracks via [io.fastpix.media3.core.FastPixPlayer.setSubtitleTrack].
 * [isForced] indicates a forced subtitle track (e.g. for dialogue in foreign language).
 */
data class SubtitleTrack(
    val id: String,

    val languageCode: String?,
    val languageName: String?,
    val label: String?,

    val isSelected: Boolean,
    val isPlayable: Boolean,
    val isDefault: Boolean,
    val isForced: Boolean,

    val role: String?,
    val codec: String?,
    val groupId: String?
)
