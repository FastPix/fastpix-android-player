package io.fastpix.media3

import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout

/**
 * How video content is scaled inside a [PlayerView] when its aspect ratio differs from the view's.
 *
 * The default, [FIT], letterboxes — correct for a general-purpose player where cropping the frame
 * would hide content. Full-screen feeds usually want [ZOOM] instead, so a landscape source fills a
 * portrait page the way short-form apps present it.
 *
 * Set it in code via [PlayerView.resizeMode], or in XML with `app:fastPixResizeMode`.
 */
@UnstableApi
enum class ResizeMode(internal val media3Value: Int) {

    /** Scale to fit inside the view, letterboxing or pillarboxing as needed. The default. */
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT),

    /** Match the view's width and let the height follow the content's aspect ratio. */
    FIXED_WIDTH(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),

    /** Match the view's height and let the width follow the content's aspect ratio. */
    FIXED_HEIGHT(AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT),

    /** Stretch to fill the view, ignoring the content's aspect ratio. */
    FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL),

    /** Fill the view while preserving aspect ratio, cropping whatever overflows. */
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);

    internal companion object {
        /** Maps a Media3 resize-mode constant back to this enum, falling back to [FIT]. */
        fun fromMedia3(value: Int): ResizeMode =
            entries.firstOrNull { it.media3Value == value } ?: FIT
    }
}
