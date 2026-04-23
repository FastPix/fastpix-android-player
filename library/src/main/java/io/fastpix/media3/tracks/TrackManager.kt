package io.fastpix.media3.tracks

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale

/**
 * Internal indices for applying a track selection override.
 * [MediaSelectionController] uses these with [player.currentTracks.groups].
 */
@UnstableApi
internal data class TrackSelectionIndices(
    val groupIndex: Int,
    val trackIndex: Int
)

/**
 * Unified internal component that discovers tracks from Media3, stores audio and subtitle
 * tracks as [PlayerTrack], and supports mapping to public [AudioTrack] / [SubtitleTrack].
 * Not exposed in the public SDK.
 *
 * Responsibilities:
 * - Discover tracks from [Tracks] (C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)
 * - Store and maintain selected state
 * - Map to public models for SDK API responses
 */
@UnstableApi
internal class TrackManager {

    private val audioEntries: MutableList<PlayerTrack> = mutableListOf()
    private val subtitleEntries: MutableList<PlayerTrack> = mutableListOf()
    private val videoEntries: MutableList<PlayerTrack> = mutableListOf()

    private var defaultAudioLanguage: String? = null
    private var defaultSubtitleLanguage: String? = null

    private var isAudioManuallySelected: Boolean = false
    private var isSubtitleManuallySelected: Boolean = false
    private var subtitlesDisabledByUser: Boolean = false
    private var isVideoAuto: Boolean = true
    private var selectedVideoTrackId: String? = null
    private var renderedVideoWidth: Int = 0
    private var renderedVideoHeight: Int = 0

    fun setDefaultAudioTrack(languageCode: String) {
        defaultAudioLanguage = languageCode
    }

    fun setDefaultSubtitleTrack(languageCode: String) {
        defaultSubtitleLanguage = languageCode
    }

    fun markAudioManuallySelected() {
        isAudioManuallySelected = true
    }

    fun markSubtitleManuallySelected() {
        isSubtitleManuallySelected = true
        subtitlesDisabledByUser = false
    }

    fun markSubtitlesDisabledByUser() {
        subtitlesDisabledByUser = true
        isSubtitleManuallySelected = false
    }

    fun resetSelectionStateForNewMedia() {
        isAudioManuallySelected = false
        isSubtitleManuallySelected = false
        subtitlesDisabledByUser = false
        isVideoAuto = true
        selectedVideoTrackId = null
        renderedVideoWidth = 0
        renderedVideoHeight = 0
    }

    fun areSubtitlesDisabledByUser(): Boolean = subtitlesDisabledByUser

    private fun normalizeLanguage(lang: String?): String? {
        return lang?.lowercase()?.split("-")?.firstOrNull()
    }

    private fun normalizeLanguageName(name: String?): String? {
        return name?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
    }

    private fun derivedLanguageNameFromCode(languageCode: String?): String? {
        val code = languageCode?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return try {
            // Use ENGLISH for stable names ("Hindi", "Spanish") regardless of device locale.
            Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun matchesPreferredLanguageName(
        track: PlayerTrack,
        preferred: String?
    ): Boolean {
        val pref = normalizeLanguageName(preferred) ?: return false
        val candidates = listOfNotNull(
            normalizeLanguageName(track.languageName),
            normalizeLanguageName(derivedLanguageNameFromCode(track.languageCode)),
            normalizeLanguageName(track.label)
        )
        return candidates.any { it == pref }
    }

    /**
     * Logical key for audio deduplication: HLS can expose multiple renditions (e.g. hi/med/lo)
     * with the same NAME and LANGUAGE; we expose one per (label, languageCode).
     */
    private fun audioLogicalKey(t: PlayerTrack): String =
        "${t.label?.trim().orEmpty()}_${t.languageCode?.trim().orEmpty()}"

    /**
     * When multiple audio tracks share the same NAME/LANGUAGE (e.g. hi/med/lo bitrate variants),
     * keep a single representative so the UI shows one option per logical track.
     * Prefer: selected > playable > highest bitrate > first.
     */
    private fun deduplicateAudioByLogicalName(raw: List<PlayerTrack>): List<PlayerTrack> {
        if (raw.isEmpty()) return emptyList()
        if (raw.size == 1) return raw
        val byKey = raw.groupBy { audioLogicalKey(it) }
        return byKey.values.map { group ->
            group.maxWithOrNull(
                compareBy<PlayerTrack> { it.isSelected }
                    .thenBy { it.isPlayable }
                    .thenByDescending { it.bitrate ?: 0 }
            ) ?: group.first()
        }
    }

    /**
     * Updates internal track lists from [tracks]. Call from main thread.
     * Source: player.currentTracks.
     * Audio tracks with the same logical name (label + language) are deduplicated to one per name.
     */
    fun updateTracks(tracks: Tracks) {
        val rawAudio = mutableListOf<PlayerTrack>()
        subtitleEntries.clear()
        videoEntries.clear()
        val groups = tracks.groups
        for (groupIndex in groups.indices) {
            val group = groups[groupIndex]
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    val mediaTrackGroup = group.mediaTrackGroup
                    val groupId = mediaTrackGroup.id ?: "g$groupIndex"
                    for (trackIndex in 0 until group.length) {
                        val format = group.getTrackFormat(trackIndex)
                        val isSelected = group.isTrackSelected(trackIndex)
                        val isPlayable = group.isTrackSupported(trackIndex)
                        val id = "${groupId}_$trackIndex"
                        rawAudio.add(
                            formatToPlayerTrack(
                                id = id,
                                type = TrackType.AUDIO,
                                format = format,
                                groupId = groupId,
                                groupIndex = groupIndex,
                                trackIndex = trackIndex,
                                isSelected = isSelected,
                                isPlayable = isPlayable,
                                channelCount = format.channelCount,
                                bitrate = format.bitrate
                            )
                        )
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    val mediaTrackGroup = group.mediaTrackGroup
                    val groupId = mediaTrackGroup.id ?: "g$groupIndex"
                    for (trackIndex in 0 until group.length) {
                        val format = group.getTrackFormat(trackIndex)
                        val isSelected = group.isTrackSelected(trackIndex)
                        val isPlayable = group.isTrackSupported(trackIndex)
                        val isForced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0
                        val id = "${groupId}_$trackIndex"
                        subtitleEntries.add(
                            formatToPlayerTrack(
                                id = id,
                                type = TrackType.SUBTITLE,
                                format = format,
                                groupId = groupId,
                                groupIndex = groupIndex,
                                trackIndex = trackIndex,
                                isSelected = isSelected,
                                isPlayable = isPlayable,
                                isForced = isForced
                            )
                        )
                    }
                }
                C.TRACK_TYPE_VIDEO -> {
                    for (trackIndex in 0 until group.length) {
                        val format = group.getTrackFormat(trackIndex)
                        val isSelected = group.isTrackSelected(trackIndex)
                        val bitrateOrNull =
                            if (format.bitrate != Format.NO_VALUE) format.bitrate else null
                        val widthOrNull = if (format.width != Format.NO_VALUE) format.width else null
                        val heightOrNull =
                            if (format.height != Format.NO_VALUE) format.height else null
                        val id = "$groupIndex-$trackIndex"
                        videoEntries.add(
                            PlayerTrack(
                                id = id,
                                type = TrackType.VIDEO,
                                languageCode = null,
                                languageName = null,
                                label = resolveVideoLabel(format.label, heightOrNull),
                                isSelected = isSelected,
                                isPlayable = group.isTrackSupported(trackIndex),
                                isDefault = false,
                                isForced = false,
                                role = null,
                                codec = format.sampleMimeType?.takeIf { it.isNotBlank() },
                                groupId = group.mediaTrackGroup.id ?: "g$groupIndex",
                                groupIndex = groupIndex,
                                trackIndex = trackIndex,
                                width = widthOrNull,
                                height = heightOrNull,
                                bitrate = bitrateOrNull
                            )
                        )
                    }
                }
                else -> Unit
            }
        }
        audioEntries.clear()
        audioEntries.addAll(deduplicateAudioByLogicalName(rawAudio))
        syncVideoSelectionState()
    }

    internal sealed class AutoSelectionAction {
        data class SelectTrack(val trackId: String) : AutoSelectionAction()
        data object DisableSubtitles : AutoSelectionAction()
        data object NoOp : AutoSelectionAction()
    }

    fun decideAutoAudioSelection(): AutoSelectionAction {
        if (isAudioManuallySelected) return AutoSelectionAction.NoOp
        val playable = audioEntries.filter { it.isPlayable }
        if (playable.isEmpty()) return AutoSelectionAction.NoOp

        val preferred = defaultAudioLanguage
        if (!normalizeLanguageName(preferred).isNullOrBlank()) {
            val matches = playable.filter { matchesPreferredLanguageName(it, preferred) }
            val pick = matches
                .sortedWith(compareByDescending<PlayerTrack> { it.isDefault })
                .firstOrNull()
            if (pick != null) return AutoSelectionAction.SelectTrack(pick.id)
        }

        val defaultPick = playable.firstOrNull { it.isDefault }
        if (defaultPick != null) return AutoSelectionAction.SelectTrack(defaultPick.id)

        return AutoSelectionAction.SelectTrack(playable.first().id)
    }

    fun decideAutoSubtitleSelection(): AutoSelectionAction {
        if (isSubtitleManuallySelected) return AutoSelectionAction.NoOp

        val playable = subtitleEntries.filter { it.isPlayable }
        if (playable.isEmpty()) {
            return if (subtitlesDisabledByUser) AutoSelectionAction.NoOp else AutoSelectionAction.DisableSubtitles
        }

        // If user disabled subtitles, don't apply default language; only allow forced subtitles.
        if (subtitlesDisabledByUser) {
            val forced = playable.firstOrNull { it.isForced }
            return forced?.let { AutoSelectionAction.SelectTrack(it.id) } ?: AutoSelectionAction.NoOp
        }

        val preferred = defaultSubtitleLanguage
        if (!normalizeLanguageName(preferred).isNullOrBlank()) {
            val matches = playable.filter { matchesPreferredLanguageName(it, preferred) }
            val pick = matches
                .sortedWith(compareByDescending<PlayerTrack> { it.isDefault })
                .firstOrNull()
            if (pick != null) return AutoSelectionAction.SelectTrack(pick.id)
        }

        val forcedPick = playable.firstOrNull { it.isForced }
        if (forcedPick != null) return AutoSelectionAction.SelectTrack(forcedPick.id)

        val defaultPick = playable.firstOrNull { it.isDefault }
        if (defaultPick != null) return AutoSelectionAction.SelectTrack(defaultPick.id)

        return AutoSelectionAction.DisableSubtitles
    }

    fun getAudioTracks(): List<AudioTrack> =
        audioEntries.map { playerTrackToAudioTrack(it) }

    fun getSubtitleTracks(): List<SubtitleTrack> =
        subtitleEntries.map { playerTrackToSubtitleTrack(it) }

    fun getVideoTracks(): List<VideoTrack> =
        videoEntries
            .sortedWith(
                compareByDescending<PlayerTrack> { it.height ?: -1 }
                    .thenByDescending { it.bitrate ?: -1 }
            )
            .map { playerTrackToVideoTrack(it) }

    fun getCurrentAudioTrack(): AudioTrack? =
        audioEntries.firstOrNull { it.isSelected }?.let { playerTrackToAudioTrack(it) }

    /**
     * Returns the currently selected subtitle track, or null if none selected or subtitles disabled.
     */
    fun getCurrentSubtitleTrack(): SubtitleTrack? =
        subtitleEntries.firstOrNull { it.isSelected }?.let { playerTrackToSubtitleTrack(it) }

    fun getCurrentVideoTrack(): VideoTrack? {
        if (!isVideoAuto && !selectedVideoTrackId.isNullOrBlank()) {
            val manual = videoEntries.firstOrNull { it.id == selectedVideoTrackId }
            return manual?.let { playerTrackToVideoTrack(it) }
        }
        // Auto (ABR) mode: match by actual rendered resolution reported by the decoder.
        if (renderedVideoHeight > 0 && videoEntries.isNotEmpty()) {
            val exact = videoEntries.firstOrNull {
                it.width == renderedVideoWidth && it.height == renderedVideoHeight
            }
            if (exact != null) return playerTrackToVideoTrack(exact)
            val byHeight = videoEntries.firstOrNull { it.height == renderedVideoHeight }
            if (byHeight != null) return playerTrackToVideoTrack(byHeight)
            val closest = videoEntries.minByOrNull {
                kotlin.math.abs((it.height ?: 0) - renderedVideoHeight)
            }
            if (closest != null) return playerTrackToVideoTrack(closest)
        }
        return videoEntries.firstOrNull { it.isSelected }?.let { playerTrackToVideoTrack(it) }
    }

    fun findAudioTrackById(trackId: String): AudioTrack? =
        audioEntries.firstOrNull { it.id == trackId }?.let { playerTrackToAudioTrack(it) }

    fun findSubtitleTrackById(trackId: String): SubtitleTrack? =
        subtitleEntries.firstOrNull { it.id == trackId }?.let { playerTrackToSubtitleTrack(it) }

    fun findVideoTrackById(trackId: String): VideoTrack? =
        videoEntries.firstOrNull { it.id == trackId }?.let { playerTrackToVideoTrack(it) }

    /**
     * Returns selection indices for the given audio [trackId]. For use by [MediaSelectionController].
     */
    fun findAudioSelectionIndices(trackId: String): TrackSelectionIndices? {
        val t = audioEntries.firstOrNull { it.id == trackId } ?: return null
        return TrackSelectionIndices(t.groupIndex, t.trackIndex)
    }

    /**
     * Returns selection indices for the given subtitle [trackId]. For use by [MediaSelectionController].
     */
    fun findSubtitleSelectionIndices(trackId: String): TrackSelectionIndices? {
        val t = subtitleEntries.firstOrNull { it.id == trackId } ?: return null
        return TrackSelectionIndices(t.groupIndex, t.trackIndex)
    }

    fun findVideoSelectionIndices(trackId: String): TrackSelectionIndices? {
        val t = videoEntries.firstOrNull { it.id == trackId } ?: return null
        return TrackSelectionIndices(t.groupIndex, t.trackIndex)
    }

    fun updateRenderedVideoSize(width: Int, height: Int) {
        renderedVideoWidth = width
        renderedVideoHeight = height
    }

    fun markVideoManuallySelected(trackId: String) {
        isVideoAuto = false
        selectedVideoTrackId = trackId
    }

    fun markVideoAutoEnabled() {
        isVideoAuto = true
        selectedVideoTrackId = null
    }

    private fun formatToPlayerTrack(
        id: String,
        type: TrackType,
        format: Format,
        groupId: String,
        groupIndex: Int,
        trackIndex: Int,
        isSelected: Boolean,
        isPlayable: Boolean,
        isForced: Boolean = false,
        channelCount: Int = Format.NO_VALUE,
        bitrate: Int = Format.NO_VALUE
    ): PlayerTrack {
        val languageCode = format.language?.takeIf { it.isNotBlank() }
        val languageName: String? = derivedLanguageNameFromCode(languageCode)
        val label = format.label?.takeIf { it.isNotBlank() }
        val codec = format.sampleMimeType?.takeIf { it.isNotBlank() }
        val role = formatRoleFlags(format.roleFlags)
        val isDefault = (format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0
        val channels = if (channelCount != Format.NO_VALUE) channelCount.toString() else null
        val bitrateOrNull = if (bitrate != Format.NO_VALUE) bitrate else null
        return PlayerTrack(
            id = id,
            type = type,
            languageCode = languageCode,
            languageName = languageName,
            label = label,
            isSelected = isSelected,
            isPlayable = isPlayable,
            isDefault = isDefault,
            isForced = isForced,
            role = role,
            codec = codec,
            groupId = groupId,
            groupIndex = groupIndex,
            trackIndex = trackIndex,
            channels = channels,
            bitrate = bitrateOrNull
        )
    }

    private fun playerTrackToAudioTrack(t: PlayerTrack): AudioTrack = AudioTrack(
        id = t.id,
        languageCode = t.languageCode,
        languageName = t.languageName,
        label = t.label,
        isSelected = t.isSelected,
        isPlayable = t.isPlayable,
        isDefault = t.isDefault,
        role = t.role,
        channels = t.channels,
        codec = t.codec,
        bitrate = t.bitrate,
        groupId = t.groupId
    )

    private fun playerTrackToSubtitleTrack(t: PlayerTrack): SubtitleTrack = SubtitleTrack(
        id = t.id,
        languageCode = t.languageCode,
        languageName = t.languageName,
        label = t.label,
        isSelected = t.isSelected,
        isPlayable = t.isPlayable,
        isDefault = t.isDefault,
        isForced = t.isForced,
        role = t.role,
        codec = t.codec,
        groupId = t.groupId
    )

    private fun playerTrackToVideoTrack(t: PlayerTrack): VideoTrack = VideoTrack(
        id = t.id,
        width = t.width,
        height = t.height,
        bitrate = t.bitrate,
        label = resolveVideoLabel(t.label, t.height),
        isSelected = t.isSelected || (!isVideoAuto && selectedVideoTrackId == t.id),
        isAuto = isVideoAuto
    )

    private fun resolveVideoLabel(label: String?, height: Int?): String? {
        return when {
            !label.isNullOrBlank() -> label
            height != null && height > 0 -> "${height}p"
            else -> null
        }
    }

    private fun syncVideoSelectionState() {
        if (videoEntries.isEmpty()) {
            selectedVideoTrackId = null
            isVideoAuto = true
            return
        }
        if (!isVideoAuto && !selectedVideoTrackId.isNullOrBlank()) {
            val stillExists = videoEntries.any { it.id == selectedVideoTrackId }
            if (!stillExists) {
                markVideoAutoEnabled()
            }
        }
    }

    private fun formatRoleFlags(roleFlags: Int): String? {
        if (roleFlags == 0) return null
        val parts = mutableListOf<String>()
        if ((roleFlags and C.ROLE_FLAG_MAIN) != 0) parts.add("main")
        if ((roleFlags and C.ROLE_FLAG_ALTERNATE) != 0) parts.add("alternate")
        if ((roleFlags and C.ROLE_FLAG_SUPPLEMENTARY) != 0) parts.add("supplementary")
        if ((roleFlags and C.ROLE_FLAG_COMMENTARY) != 0) parts.add("commentary")
        if ((roleFlags and C.ROLE_FLAG_DUB) != 0) parts.add("dub")
        if ((roleFlags and C.ROLE_FLAG_EMERGENCY) != 0) parts.add("emergency")
        if ((roleFlags and C.ROLE_FLAG_CAPTION) != 0) parts.add("caption")
        if ((roleFlags and C.ROLE_FLAG_SUBTITLE) != 0) parts.add("subtitle")
        if ((roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO) != 0) parts.add("describes_video")
        if ((roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0) parts.add("describes_music")
        return parts.takeIf { it.isNotEmpty() }?.joinToString(",")
    }
}
