package io.fastpix.media3

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
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
     * - Player is released when view is detached
     * - A fresh player instance is created on reattach
     * - Playback will restart from the beginning
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
    private val media3PlayerView: Media3PlayerView = Media3PlayerView(context, attrs).apply {
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

    init {
        addView(media3PlayerView)

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
            // Detach current player from view
            media3PlayerView.player = null

            fastPixPlayer = value

            // Attach new player to view
            if (value != null && isAttachedToWindow) {
                media3PlayerView.player = value.getExoPlayer()
            }
        }

    /**
     * Creates a new FastPixPlayer instance if one doesn't exist, or retrieves an existing
     * instance from the store if available (after configuration change).
     *
     * Creates a player with default settings (loop = false, autoplay = false) using the builder pattern.
     * For custom configuration, create the player externally using FastPixPlayer.Builder
     * and set it via the player property (Media3 pattern).
     *
     * Ensures proper attachment to the view surface.
     * Called automatically when the view is attached to window.
     */
    private fun createPlayerIfNeeded() {
        if (fastPixPlayer == null) {
            val viewId = id

            // Try to recover existing player instance after config change
            if (retainPlayerOnConfigChange && viewId != View.NO_ID) {
                val storedPlayer = PlayerStore.getPlayer(viewId)
                if (storedPlayer != null) {
                    fastPixPlayer = storedPlayer
                }
            }

            // Create new player if not found or retention is disabled
            // Use builder pattern with default settings
            if (fastPixPlayer == null) {
                fastPixPlayer = FastPixPlayer.Builder(context).build()

                // Store player instance if retention is enabled and view has an ID
                // Store immediately so it's available even if view is quickly detached
                if (retainPlayerOnConfigChange && viewId != View.NO_ID) {
                    PlayerStore.putPlayer(viewId, fastPixPlayer!!)
                }
            }

            // Attach player to view surface
            // This does NOT reset playback state - ExoPlayer preserves state when reattached
            if (isAttachedToWindow) {
                media3PlayerView.player = fastPixPlayer!!.getExoPlayer()
            }
        }
    }

    /**
     * Releases the FastPixPlayer instance.
     *
     * If [retainPlayerOnConfigChange] is true and the view has an ID, the player instance
     * is preserved in the store for recovery after configuration changes.
     * Otherwise, the player is fully released.
     *
     * @param forceRelease If true, always release the player regardless of retention setting.
     */
    private fun releasePlayer(forceRelease: Boolean = false) {
        val player = fastPixPlayer ?: return

        val viewId = id
        val shouldRetain = retainPlayerOnConfigChange && !forceRelease && viewId != View.NO_ID

        if (shouldRetain) {
            // Store player in registry before detaching (in case it wasn't stored yet)
            PlayerStore.putPlayer(viewId, player)

            // Detach player from view surface (player state is preserved)
            media3PlayerView.player = null

            // Clear local reference but keep player in store
            fastPixPlayer = null
        } else {
            // Detach player from view surface
            media3PlayerView.player = null

            // Truly release the player
            player.release()

            // Remove from store if it exists
            if (viewId != View.NO_ID) {
                PlayerStore.removePlayer(viewId)
            }

            fastPixPlayer = null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttachedToWindow = true

        // Create or recover player instance if not set externally
        if (fastPixPlayer == null) {
            createPlayerIfNeeded()
        }

        // Attach player to view surface
        fastPixPlayer?.let { player ->
            media3PlayerView.player = player.getExoPlayer()
        }
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
     * This will force release the player even if [retainPlayerOnConfigChange] is true.
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
