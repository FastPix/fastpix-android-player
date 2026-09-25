package io.fastpix.media3.prerender

import android.graphics.Bitmap
import android.view.SurfaceView

/**
 * What a view showing a pre-rendering [io.fastpix.media3.core.FastPixPlayer] provides:
 * [io.fastpix.media3.PlayerView] implements it while the player is attached to it.
 */
internal interface PrerenderSurfaceHost {

    /**
     * A small, always-attached surface that upcoming entries are decoded into so their first frame
     * can be copied out. Never shown to the user.
     */
    val captureSurfaceView: SurfaceView

    /** Size of the video area, to scale captured frames to; 0 before layout. */
    val viewWidth: Int
    val viewHeight: Int

    /** Shows [frame] over the video until [hideBridge] — covering the main player's start-up. */
    fun showBridge(frame: Bitmap)

    fun hideBridge()
}
