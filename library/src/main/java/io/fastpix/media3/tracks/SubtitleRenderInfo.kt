package io.fastpix.media3.tracks

/**
 * Information for rendering a single subtitle cue (text and timing).
 * Used by [SubtitleTrackListener.onSubtitleCueChange] to drive UI rendering.
 */
data class SubtitleCueInfo(
    val text: CharSequence,
    val startTimeMs: Long,
    val endTimeMs: Long
)

/**
 * Container for the current set of subtitle cues to display.
 * Converted from Media3 [androidx.media3.common.text.Cue] and dispatched via
 * [SubtitleTrackListener.onSubtitleCueChange].
 */
data class SubtitleRenderInfo(
    val cues: List<SubtitleCueInfo>
)
