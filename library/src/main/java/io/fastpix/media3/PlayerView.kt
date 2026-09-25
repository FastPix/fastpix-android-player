package io.fastpix.media3

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import io.fastpix.media3.core.FastPixPlayer
import io.fastpix.media3.prerender.PrerenderSurfaceHost
import io.fastpix.player.R
import androidx.media3.ui.PlayerView as Media3PlayerView

@UnstableApi
class PlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        /**
         * Cleanup method to release all stored players.
         * Useful for testing or cleanup scenarios.
         */
        @JvmStatic
        fun releaseAllPlayers() {
            PlayerStore.releaseAllPlayers()
        }
    }

    /**
     * Whether to retain the FastPixPlayer instance across configuration changes.
     *
     * Default: `true`
     *
     * When `true` (default):
     * - Player instance is preserved when view is detached during configuration changes
     * - Playback state (position, play/pause, buffering) is maintained
     * - Video does not restart on rotation
     *
     * When `false`:
     * - A player the view created itself is released when the view is detached
     * - Playback will restart from the beginning
     *
     * Either way, a player assigned through [player] belongs to the app: the view never releases
     * it, only unbinds its surface on detach and binds it again on re-attach. Release it yourself,
     * or call [release].
     *
     * **Note:** This property should be set before the view is attached to the window
     * for best results. Changing it after attachment may not have the expected effect
     * until the next attach/detach cycle.
     */
    var retainPlayerOnConfigChange: Boolean = true
        set(value) {
            field = value
            // If disabling retention and we have a stored player, it will be released on next detach
            if (!value && fastPixPlayer != null) {
                val viewId = id
                if (viewId != View.NO_ID && PlayerStore.hasPlayer(viewId)) {
                    // Player will be released on next detach
                }
            }
        }

    /**
     * Internal Media3 PlayerView used for rendering.
     * This is wrapped inside our custom view.
     */
    internal val media3PlayerView: Media3PlayerView = Media3PlayerView(context, attrs).apply {
        // Disable default controls since we're managing our own
        useController = false
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
    }

    /**
     * The FastPixPlayer instance used for playback.
     * Can be set externally or auto-created when needed.
     */
    private var fastPixPlayer: FastPixPlayer? = null

    /**
     * Whether [fastPixPlayer] was created by this view (lazily, for view-level media calls) rather
     * than assigned by the app. Only a player the view created may be released by the view.
     */
    private var ownsPlayer = false

    /**
     * 1x1 surface a pre-rendering player decodes upcoming entries into, to copy their first frames
     * out. Tucked behind the video; created only for a player that pre-renders.
     */
    private var captureSurface: SurfaceView? = null

    /** Shows a captured first frame over the video while the player starts that entry. */
    private var bridgeView: ImageView? = null

    /** The player this view has reported itself as showing, while attached. */
    private var displayedPlayer: FastPixPlayer? = null

    private val prerenderHost = object : PrerenderSurfaceHost {
        override val captureSurfaceView: SurfaceView
            get() = captureSurface ?: SurfaceView(context).also { surface ->
                // Behind everything; its size is irrelevant, the decoder writes full-size frames.
                addView(surface, 0, LayoutParams(1, 1))
                captureSurface = surface
            }

        override val viewWidth: Int get() = media3PlayerView.width
        override val viewHeight: Int get() = media3PlayerView.height

        override fun showBridge(frame: Bitmap) {
            val view = bridgeView ?: ImageView(context).also { image ->
                image.setBackgroundColor(Color.BLACK)
                addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                bridgeView = image
            }
            view.scaleType = bridgeScaleType()
            view.setImageBitmap(frame)
            view.visibility = View.VISIBLE
        }

        override fun hideBridge() {
            bridgeView?.apply {
                visibility = View.GONE
                setImageDrawable(null)
            }
        }
    }

    /**
     * Track if the view is currently attached to avoid double releases.
     */
    private var isAttachedToWindow = false

    /**
     * Whether tap gesture is enabled. Defaults to true.
     */
    var isTapGestureEnabled: Boolean = true
        set(value) {
            field = value
            isClickable = value
            isFocusable = value
        }

    /**
     * Gesture detector for tap-to-toggle functionality.
     */
    private val gestureDetector: GestureDetector

    /**
     * How video is scaled inside this view.
     *
     * Defaults to [ResizeMode.FIT], which letterboxes content whose aspect ratio differs from the
     * view's. Full-screen feeds usually want [ResizeMode.ZOOM] so a source of any shape fills the
     * page, cropping the overflow.
     *
     * Can also be set in XML with `app:fastPixResizeMode="zoom"`.
     */
    var resizeMode: ResizeMode
        get() = ResizeMode.fromMedia3(media3PlayerView.resizeMode)
        set(value) {
            media3PlayerView.resizeMode = value.media3Value
        }

    init {
        addView(media3PlayerView)

        applyResizeModeAttribute(attrs)

        // Setup gesture detector for tap-to-toggle
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (isTapGestureEnabled) {
                    fastPixPlayer?.togglePlayPause()
                    return true
                }
                return false
            }
        })

        // Enable clickable for tap gestures
        isClickable = true
        isFocusable = true

        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }

    /**
     * The FastPixPlayer instance used for playback.
     *
     * **Media3 Pattern:** Create a FastPixPlayer instance with your desired configuration
     * (loop, autoplay, etc.) using FastPixPlayer.Builder, then pass it to this property.
     * All playback-related configurations should be set during player creation, not on the view.
     *
     * Setting this property attaches the player to the view for rendering.
     * If set to null, the view will auto-create a player with default settings when needed.
     *
     * A player you assign stays yours: the view never releases it on detach, so the same player
     * can move between views, or survive a page being recycled in a pager. Release it when you are
     * done with it.
     *
     * Getting this property will return the current player instance, or create one
     * automatically if no player has been set. The player can be created even if
     * the view is not yet attached to the window (it will be attached when the
     * view becomes attached).
     *
     * This follows the same pattern as Media3's PlayerView.player property.
     *
     * Example:
     * ```
     * val player = FastPixPlayer.Builder(context)
     *     .setLoop(true)
     *     .setAutoplay(false)
     *     .build()
     * playerView.player = player
     * ```
     */
    var player: FastPixPlayer?
        get() {
            if (fastPixPlayer == null) {
                createPlayerIfNeeded()
            }
            return fastPixPlayer
        }
        set(value) {
            val previous = fastPixPlayer
            previous?.let { unbindDisplayedPlayer(it) }

            // Detach current player from view
            media3PlayerView.player = null

            // A player this view created is only reachable through the view, so replacing it
            // would otherwise leak it.
            if (previous != null && previous !== value && ownsPlayer) {
                val viewId = id
                if (viewId != View.NO_ID && PlayerStore.getPlayer(viewId) === previous) {
                    PlayerStore.removePlayer(viewId)
                }
                previous.release()
            }

            fastPixPlayer = value
            ownsPlayer = false

            // Attach new player to view
            if (value != null && isAttachedToWindow) {
                media3PlayerView.player = value.getExoPlayer()
                bindDisplayedPlayer()
            }
        }

    /**
     * Applies `app:fastPixResizeMode` if the layout declares it.
     *
     * Only touched when explicitly present: the wrapped Media3 view receives the same
     * [AttributeSet] and may already have resolved its own `resize_mode`, which we must not
     * silently override.
     */
    private fun applyResizeModeAttribute(attrs: AttributeSet?) {
        if (attrs == null) return
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.FastPixPlayerView)
        try {
            if (typedArray.hasValue(R.styleable.FastPixPlayerView_fastPixResizeMode)) {
                val value = typedArray.getInt(
                    R.styleable.FastPixPlayerView_fastPixResizeMode,
                    ResizeMode.FIT.media3Value,
                )
                resizeMode = ResizeMode.fromMedia3(value)
            }
        } finally {
            typedArray.recycle()
        }
    }

    /**
     * Recovers the player stored for this view across a configuration change, if any.
     *
     * Never creates a player: a view that is merely attached — a page in a pager waiting for the
     * app to assign one, say — must not spin up a player of its own.
     */
    private fun recoverStoredPlayer() {
        if (fastPixPlayer != null) return
        val viewId = id
        if (!retainPlayerOnConfigChange || viewId == View.NO_ID) return
        val storedPlayer = PlayerStore.getPlayer(viewId) ?: return
        fastPixPlayer = storedPlayer
        ownsPlayer = PlayerStore.isOwnedByView(viewId)
    }

    /**
     * Returns a player for view-level calls ([setMediaItem], reading [player]) when the app has not
     * assigned one: recovers the stored instance after a configuration change, or creates one with
     * default settings (loop = false, autoplay = false), which this view then owns.
     *
     * For custom configuration, create the player with FastPixPlayer.Builder and assign it through
     * [player] instead (Media3 pattern).
     */
    private fun createPlayerIfNeeded() {
        if (fastPixPlayer == null) {
            recoverStoredPlayer()

            if (fastPixPlayer == null) {
                fastPixPlayer = FastPixPlayer.Builder(context).build()
                ownsPlayer = true

                // Store immediately so it's available even if view is quickly detached
                val viewId = id
                if (retainPlayerOnConfigChange && viewId != View.NO_ID) {
                    PlayerStore.putPlayer(viewId, fastPixPlayer, ownedByView = true)
                }
            }

            // Attach player to view surface
            // This does NOT reset playback state - ExoPlayer preserves state when reattached
            if (isAttachedToWindow) {
                media3PlayerView.player = fastPixPlayer?.getExoPlayer()
            }
        }
    }

    /**
     * Unbinds the player from this view on detach, releasing it only when that is the view's call.
     *
     * - [forceRelease]: always released — the app asked for it through [release].
     * - [retainPlayerOnConfigChange] with a view id: kept in the store for recovery after a
     *   configuration change.
     * - Otherwise a player the view created is released, and a player the app assigned is left
     *   alone: only its surface is unbound, and the view keeps the reference so re-attaching binds
     *   it again. Views in pagers and Compose `AndroidView`s detach and re-attach routinely, and
     *   releasing the app's player there would leave it dead with no error.
     *
     * @param forceRelease If true, always release the player regardless of retention setting.
     */
    private fun releasePlayer(forceRelease: Boolean = false) {
        val player = fastPixPlayer ?: return
        val viewId = id

        unbindDisplayedPlayer(player)

        // Detach player from view surface (player state is preserved)
        media3PlayerView.player = null

        when {
            forceRelease -> {
                player.release()
                if (viewId != View.NO_ID) PlayerStore.removePlayer(viewId)
                fastPixPlayer = null
                ownsPlayer = false
            }

            retainPlayerOnConfigChange && viewId != View.NO_ID -> {
                // Clear local reference but keep player in store
                PlayerStore.putPlayer(viewId, player, ownedByView = ownsPlayer)
                fastPixPlayer = null
                ownsPlayer = false
            }

            ownsPlayer -> {
                player.release()
                fastPixPlayer = null
                ownsPlayer = false
            }

            else -> Unit // The app's player: surface unbound above, reference kept for re-attach.
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttachedToWindow = true

        // Recover the player after a configuration change. A view with no player stays empty until
        // the app assigns one or makes a view-level media call.
        recoverStoredPlayer()

        // Attach player to view surface
        fastPixPlayer?.let { player ->
            media3PlayerView.player = player.getExoPlayer()
        }
        bindDisplayedPlayer()
    }

    /**
     * Tells the player this attached view is showing it — so a pool will not hand it to another
     * page — and, for a pre-rendering player, lets it capture and show frames through this view.
     */
    private fun bindDisplayedPlayer() {
        val player = fastPixPlayer ?: return
        if (!isAttachedToWindow) return
        if (displayedPlayer !== player) {
            displayedPlayer?.onViewDisplayChanged(this, false)
            player.onViewDisplayChanged(this, true)
            displayedPlayer = player
        }
        if (player.prerendersThroughView) player.setPrerenderHost(prerenderHost)
    }

    private fun unbindDisplayedPlayer(player: FastPixPlayer) {
        if (displayedPlayer === player) {
            player.onViewDisplayChanged(this, false)
            displayedPlayer = null
        }
        player.clearPrerenderHost(prerenderHost)
        prerenderHost.hideBridge()
    }

    /** Scales a captured frame the way [resizeMode] scales the video, so the handoff lines up. */
    private fun bridgeScaleType(): ImageView.ScaleType = when (resizeMode) {
        ResizeMode.ZOOM -> ImageView.ScaleType.CENTER_CROP
        ResizeMode.FILL -> ImageView.ScaleType.FIT_XY
        else -> ImageView.ScaleType.FIT_CENTER
    }

    override fun onDetachedFromWindow() {
        isAttachedToWindow = false

        // Detach player from view but preserve instance for config changes (if retention enabled)
        releasePlayer(forceRelease = false)

        super.onDetachedFromWindow()
    }

    /**
     * Manually release the player instance.
     *
     * Call this method when you're certain the player should be released,
     * such as in Activity.onDestroy() when the activity is finishing.
     *
     * This will force release the player even if [retainPlayerOnConfigChange] is true, including a
     * player the app assigned through [player].
     */
    fun release() {
        releasePlayer(forceRelease = true)
    }

    /**
     * Sets a media item to play.
     * Delegates to the underlying FastPixPlayer instance.
     *
     * @param mediaItem The media item to set.
     */
    fun setMediaItem(mediaItem: MediaItem) {
        createPlayerIfNeeded()
        fastPixPlayer?.setMediaItem(mediaItem)
    }
}
