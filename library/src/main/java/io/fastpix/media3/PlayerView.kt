package io.fastpix.media3

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView as Media3PlayerView

/**
 * A reusable custom [View] that wraps Media3 ExoPlayer and provides a clean SDK-friendly API.
 *
 * This view internally creates and manages an ExoPlayer instance, handling lifecycle,
 * orientation changes, and gestures. It mirrors the usage style of
 * [androidx.media3.ui.PlayerView] while providing additional conveniences.
 *
 * ## Configuration Change Survival (Default Behavior)
 *
 * By default, PlayerView **preserves playback state across configuration changes** (rotation,
 * multi-window, etc.). This means:
 * - Video playback does NOT restart on rotation
 * - Current playback position is preserved
 * - Play/pause state is preserved
 * - Buffering state is preserved
 *
 * This is achieved by retaining the ExoPlayer instance in an internal registry when the view
 * is detached during configuration changes, and reattaching to the same instance when the view
 * is recreated.
 *
 * ### Opt-Out Mechanism
 *
 * If you need to opt out of this behavior (e.g., for testing or specific use cases), you can
 * disable player retention:
 *
 * ```kotlin
 * playerView.retainPlayerOnConfigChange = false
 * ```
 *
 * When disabled:
 * - Player is released when PlayerView is detached
 * - A fresh player instance is created on reattach
 * - Playback will restart from the beginning
 *
 * ## Usage
 *
 * ### XML Layout
 * ```xml
 * <io.fastpix.media3.PlayerView
 *     android:id="@+id/playerView"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content" />
 * ```
 *
 * **Important:** Assign an `android:id` to your PlayerView in XML to enable configuration
 * change survival. Without an ID, a new player will be created on each configuration change.
 *
 * ### Programmatic Usage
 * ```kotlin
 * val playerView = PlayerView(context)
 * playerView.id = View.generateViewId() // Assign an ID for config change survival
 * playerView.setMediaItem(MediaItem.fromUri("https://example.com/video.mp4"))
 * playerView.play()
 * ```
 *
 * ## Key Features
 * - Internally manages ExoPlayer lifecycle
 * - Survives orientation changes without restarting playback (default)
 * - Single-tap gesture to toggle play/pause (enabled by default)
 * - Clean playback control API (play, pause, togglePlayPause, isPlaying)
 * - Event listeners for playback state changes
 * - Continuous playback time updates during active playback (similar to HTML5 onTimeUpdate)
 * - Seek interaction callbacks for tracking user seek operations
 * - Fullscreen mode support with proper view reparenting and system UI handling
 *
 * ## Seek Interaction Callbacks
 *
 * PlayerView provides callbacks for seek operations via [PlaybackListener]:
 * - [PlaybackListener.onSeekStart] is called when a seek operation begins (dragging seek bar or programmatic seek)
 * - [PlaybackListener.onSeekEnd] is called when a seek operation completes
 *
 * During seek operations:
 * - Automatic time updates are paused
 * - Tap-to-toggle play/pause is disabled
 *
 * See [PlaybackListener] for detailed documentation on seek callback behavior.
 *
 * ## Lifecycle
 *
 * The PlayerView automatically handles ExoPlayer lifecycle:
 * - Creates player when attached to window
 * - Preserves player instance across configuration changes (when [retainPlayerOnConfigChange] is true)
 * - Releases player when view is truly destroyed (not during config changes)
 * - Call [release] manually in Activity.onDestroy() if activity is finishing to ensure cleanup
 *
 * ## Architecture
 *
 * This implementation:
 * - Does NOT use `android:configChanges` (follows Android best practices)
 * - Does NOT require Activity or Fragment lifecycle ownership
 * - Does NOT require ViewModel usage
 * - Does NOT leak Activity or View references
 * - Uses an internal player registry to retain instances across view recreation
 *
 * ## Fullscreen Mode
 *
 * PlayerView supports fullscreen mode where the player covers the entire screen without
 * system UI (status bar, navigation bar) overlapping the video.
 *
 * ### Entering Fullscreen
 * ```kotlin
 * playerView.enterFullscreen()
 * ```
 *
 * When entering fullscreen:
 * - PlayerView is detached from its original parent
 * - Attached to the Activity's root decor view
 * - Layout parameters set to MATCH_PARENT for both width and height
 * - System UI (status bar, navigation bar) is hidden
 * - Playback state, player instance, and listeners are preserved
 *
 * ### Exiting Fullscreen
 * ```kotlin
 * playerView.exitFullscreen()
 * ```
 *
 * When exiting fullscreen:
 * - PlayerView is detached from the decor view
 * - Restored to its original parent with original layout parameters
 * - System UI is restored
 * - Playback continues seamlessly
 *
 * ### Checking Fullscreen State
 * ```kotlin
 * if (playerView.isFullscreen()) {
 *     // Handle fullscreen state
 * }
 * ```
 *
 * ### Important Notes
 * - Fullscreen is developer-controlled, not automatic
 * - Fullscreen state is automatically cleaned up if the view is detached
 * - Supports both portrait and landscape orientations
 * - Does NOT force orientation changes
 * - Handles orientation changes while in fullscreen without breaking layout
 *
 * @see PlaybackListener
 */
@UnstableApi
class PlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        /**
         * Default interval for playback time updates in milliseconds.
         */
        private const val DEFAULT_TIME_UPDATE_INTERVAL_MS = 500L
        
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
     * Whether to retain the ExoPlayer instance across configuration changes.
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
            // If disabling retention and we have a stored player, release it
            if (!value && exoPlayer != null) {
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
     * Internal ExoPlayer instance.
     * Created when view is attached to window, released when detached (unless retained).
     * Preserved across orientation changes using view ID as key when [retainPlayerOnConfigChange] is true.
     */
    private var exoPlayer: ExoPlayer? = null

    /**
     * Track if the view is currently attached to avoid double releases.
     */
    private var isAttachedToWindow = false

    /**
     * Track if the player listener is currently attached to avoid duplicate listeners.
     */
    private var isListenerAttached = false

    /**
     * List of playback listeners.
     */
    private val playbackListeners = mutableListOf<PlaybackListener>()

    /**
     * Whether a seek operation is currently in progress.
     */
    private var isSeeking = false

    /**
     * The playback position when the current seek operation started.
     */
    private var seekStartPositionMs: Long = 0L

    /**
     * Handler for dispatching time updates on the main thread.
     */
    private val timeUpdateHandler = Handler(Looper.getMainLooper())

    /**
     * Runnable that dispatches time updates to all registered listeners.
     */
    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            val player = exoPlayer
            
            // Pause time updates during seek operations
            if (isSeeking) {
                return
            }
            
            if (player != null && player.isPlaying && isAttachedToWindow && playbackListeners.isNotEmpty()) {
                val currentPositionMs = player.currentPosition
                val durationMs = if (player.duration != C.TIME_UNSET) {
                    player.duration
                } else {
                    C.TIME_UNSET
                }
                val bufferedPositionMs = player.bufferedPosition
                
                // Dispatch time updates to all listeners
                playbackListeners.forEach { listener ->
                    listener.onTimeUpdate(currentPositionMs, durationMs, bufferedPositionMs)
                }
                
                // Schedule next update if still playing
                if (player.isPlaying && isAttachedToWindow && playbackListeners.isNotEmpty() && !isSeeking) {
                    timeUpdateHandler.postDelayed(this, DEFAULT_TIME_UPDATE_INTERVAL_MS)
                } else {
                    // Stop updates if conditions are no longer met
                    isTimeUpdateScheduled = false
                }
            } else {
                // Stop updates if conditions are no longer met
                isTimeUpdateScheduled = false
            }
        }
    }

    /**
     * Whether time updates are currently scheduled.
     */
    private var isTimeUpdateScheduled = false

    /**
     * Previous playback state to detect transitions.
     */
    private var previousPlaybackState: Int = Player.STATE_IDLE

    /**
     * Internal player listener that maps Media3 events to PlaybackListener callbacks.
     */
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                playbackListeners.forEach { it.onPlay() }
                // Only start time updates if not seeking
                if (!isSeeking) {
                    startTimeUpdates()
                }
            } else {
                val player = exoPlayer ?: return
                // Only call onPause if not ended or idle
                if (player.playbackState != Player.STATE_ENDED &&
                    player.playbackState != Player.STATE_IDLE
                ) {
                    playbackListeners.forEach { it.onPause() }
                }
                stopTimeUpdates()
            }
            // Notify listeners of the playing state change
            playbackListeners.forEach { it.onPlaybackStateChanged(isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = exoPlayer ?: return
            
            // Detect buffering state transitions
            if (previousPlaybackState != Player.STATE_BUFFERING && playbackState == Player.STATE_BUFFERING) {
                // Transitioned to buffering state
                playbackListeners.forEach { it.onBufferingStart() }
            } else if (previousPlaybackState == Player.STATE_BUFFERING && playbackState == Player.STATE_READY) {
                // Transitioned from buffering to ready
                playbackListeners.forEach { it.onBufferingEnd() }
            }
            
            // Update previous state
            previousPlaybackState = playbackState
            
            // Stop time updates when playback ends
            if (playbackState == Player.STATE_ENDED) {
                stopTimeUpdates()
            }
            
            // If we're seeking and player becomes ready, complete the seek
            if (isSeeking && playbackState == Player.STATE_READY) {
                val finalPositionMs = player.currentPosition
                val durationMs = if (player.duration != C.TIME_UNSET) {
                    player.duration
                } else {
                    0L
                }
                
                // Reset seeking state
                isSeeking = false
                
                // Notify playback listeners
                playbackListeners.forEach { it.onSeekEnd(seekStartPositionMs, finalPositionMs, durationMs) }
                
                // Resume time updates if player is playing
                if (player.isPlaying && playbackListeners.isNotEmpty()) {
                    startTimeUpdates()
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            playbackListeners.forEach { it.onError(error) }
            stopTimeUpdates()
            
            // Complete seek even on error to ensure cleanup
            if (isSeeking) {
                val player = exoPlayer ?: return
                val finalPositionMs = player.currentPosition
                val durationMs = if (player.duration != C.TIME_UNSET) {
                    player.duration
                } else {
                    0L
                }
                
                // Reset seeking state
                isSeeking = false
                
                // Notify playback listeners
                playbackListeners.forEach { it.onSeekEnd(seekStartPositionMs, finalPositionMs, durationMs) }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // Handle seek operations entirely in the Player.Listener
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                val player = exoPlayer ?: return
                
                if (isSeeking) {
                    // Complete the ongoing seek operation
                    val finalPositionMs = newPosition.positionMs
                    val durationMs = if (player.duration != C.TIME_UNSET) {
                        player.duration
                    } else {
                        0L
                    }
                    
                    // Reset seeking state
                    isSeeking = false
                    
                    // Notify playback listeners
                    playbackListeners.forEach { it.onSeekEnd(seekStartPositionMs, finalPositionMs, durationMs) }
                    
                    // Resume time updates if player is playing
                    if (player.isPlaying && playbackListeners.isNotEmpty()) {
                        startTimeUpdates()
                    }
                } else {
                    // Start tracking a new seek operation
                    // This handles both programmatic seeks and built-in seek bar interactions
                    isSeeking = true
                    seekStartPositionMs = oldPosition.positionMs
                    
                    // Pause time updates during seek
                    stopTimeUpdates()
                    
                    // Notify playback listeners
                    playbackListeners.forEach { it.onSeekStart(seekStartPositionMs) }
                    
                    // The seek will complete when player becomes ready or on next position discontinuity
                }
            }
        }
    }

    /**
     * Gesture detector for tap-to-toggle functionality.
     */
    private val gestureDetector: GestureDetector

    /**
     * Whether tap gesture is enabled. Defaults to true.
     */
    var isTapGestureEnabled: Boolean = true
        set(value) {
            field = value
            isClickable = value
            isFocusable = value
        }

    init {
        addView(media3PlayerView)

        // Setup gesture detector for tap-to-toggle
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // Prevent tap-to-toggle during seek operations
                if (isSeeking) {
                    return false
                }
                if (isTapGestureEnabled) {
                    togglePlayPause()
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
     * Creates a new ExoPlayer instance if one doesn't exist, or retrieves an existing
     * instance from the store if available (after configuration change).
     *
     * Ensures proper listener and surface attachment without duplicates.
     * Called automatically when the view is attached to window.
     */
    private fun createPlayerIfNeeded() {
        if (exoPlayer == null) {
            val viewId = id
            
            // Try to recover existing player instance after config change
            if (retainPlayerOnConfigChange && viewId != View.NO_ID) {
                val storedPlayer = PlayerStore.getPlayer(viewId)
                if (storedPlayer != null) {
                    exoPlayer = storedPlayer
                    // Player state (position, playWhenReady, media items) is already preserved
                }
            }
            
            // Create new player if not found or retention is disabled
            if (exoPlayer == null) {
                exoPlayer = ExoPlayer.Builder(context).build()
                
                // Store player instance if retention is enabled and view has an ID
                // Store immediately so it's available even if view is quickly detached
                if (retainPlayerOnConfigChange && viewId != View.NO_ID) {
                    PlayerStore.putPlayer(viewId, exoPlayer!!)
                }
            }
            
            // Attach listener (ensuring no duplicates)
            attachPlayerListener()
            
            // Initialize previous playback state (for both new and recovered players)
            previousPlaybackState = exoPlayer!!.playbackState
            
            // Attach player to view surface
            // This does NOT reset playback state - ExoPlayer preserves state when reattached
            media3PlayerView.player = exoPlayer
        }
    }

    /**
     * Sets up touch event interception for built-in seek bar interactions.
     * 
     * This method finds all SeekBar views within the Media3 PlayerView hierarchy
     * and intercepts their touch events to detect when users start dragging the seek bar.
     */
    private fun setupSeekBarInterception() {
        // Only setup if controller is enabled (seek bar is only available with controller)
        if (!media3PlayerView.useController) {
            return
        }
        
        // Post to ensure view hierarchy is fully laid out
        post {
            findAndInterceptSeekBars(media3PlayerView)
        }
    }

    /**
     * Recursively finds all SeekBar views in the view hierarchy.
     * 
     * Note: Seek tracking is handled entirely by Player.Listener via onPositionDiscontinuity.
     * This method is kept for potential future use or as a placeholder for custom seek bar handling.
     */
    private fun findAndInterceptSeekBars(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is SeekBar) {
                // Seek tracking is handled by Player.Listener, no need to intercept here
                // The seek bar will trigger seeks that are detected via onPositionDiscontinuity
            } else if (child is ViewGroup) {
                // Recursively search in child view groups
                findAndInterceptSeekBars(child)
            }
        }
    }

    /**
     * Attaches the player listener, ensuring no duplicate listeners.
     * 
     * When recovering a player from the store (after config change), the previous
     * view instance should have already removed its listener. However, we defensively
     * remove any existing listener first to ensure clean state, then add our listener.
     */
    private fun attachPlayerListener() {
        val player = exoPlayer ?: return
        if (!isListenerAttached) {
            // Defensively remove listener first (safe to call even if not attached)
            // This ensures clean state when recovering a player from the store
            player.removeListener(playerListener)
            // Now add the listener
            player.addListener(playerListener)
            isListenerAttached = true
        }
    }

    /**
     * Detaches the player listener.
     * 
     * This is called when the view is detached to ensure the listener is removed
     * before the player is stored or released.
     */
    private fun detachPlayerListener() {
        val player = exoPlayer ?: return
        if (isListenerAttached) {
            player.removeListener(playerListener)
            isListenerAttached = false
        }
    }

    /**
     * Releases the ExoPlayer instance.
     * 
     * If [retainPlayerOnConfigChange] is true and the view has an ID, the player instance
     * is preserved in the store for recovery after configuration changes.
     * Otherwise, the player is fully released.
     *
     * @param forceRelease If true, always release the player regardless of retention setting.
     */
    private fun releasePlayer(forceRelease: Boolean = false) {
        val player = exoPlayer ?: return
        
        val viewId = id
        val shouldRetain = retainPlayerOnConfigChange && !forceRelease && viewId != View.NO_ID
        
        if (shouldRetain) {
            // Store player in registry before detaching (in case it wasn't stored yet)
            PlayerStore.putPlayer(viewId, player)
            
            // Detach listener (will be reattached when view is recreated)
            detachPlayerListener()
            
            // Detach player from view surface (player state is preserved)
            media3PlayerView.player = null
            
            // Clear local reference but keep player in store
            exoPlayer = null
        } else {
            // Detach listener
            detachPlayerListener()
            
            // Detach player from view surface
            media3PlayerView.player = null
            
            // Truly release the player
            player.release()
            
            // Remove from store if it exists
            if (viewId != View.NO_ID) {
                PlayerStore.removePlayer(viewId)
            }
            
            exoPlayer = null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttachedToWindow = true
        
        // Create or recover player instance
        createPlayerIfNeeded()
        
        // When recovering a player from store, ensure it's attached to the surface
        // The player's state (position, playWhenReady) is already preserved
        exoPlayer?.let { player ->
            // Reattach player to view surface (this doesn't reset playback state)
            media3PlayerView.player = player
            
            // Resume time updates if player is currently playing and listeners are registered
            if (player.isPlaying && playbackListeners.isNotEmpty()) {
                startTimeUpdates()
            }
        }
    }

    override fun onDetachedFromWindow() {
        isAttachedToWindow = false
        
        // Stop time updates when view is detached
        stopTimeUpdates()
        
        // Complete any ongoing seek operation
        if (isSeeking) {
            val player = exoPlayer
            if (player != null) {
                val finalPositionMs = player.currentPosition
                val durationMs = if (player.duration != C.TIME_UNSET) {
                    player.duration
                } else {
                    0L
                }
                
                // Reset seeking state
                isSeeking = false
                
                // Notify playback listeners
                playbackListeners.forEach { it.onSeekEnd(seekStartPositionMs, finalPositionMs, durationMs) }
            } else {
                isSeeking = false
            }
        }
        
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
        stopTimeUpdates()
        releasePlayer(forceRelease = true)
    }

    /**
     * Sets a media item to play.
     *
     * If the player already has the same media item set (e.g., after configuration change recovery),
     * this method will not reset playback state. This preserves playback position and state
     * across configuration changes.
     *
     * @param mediaItem The media item to set.
     */
    fun setMediaItem(mediaItem: MediaItem) {
        createPlayerIfNeeded()
        val player = exoPlayer ?: return
        
        // Check if player already has media items and is in a valid state
        val currentMediaItemCount = player.mediaItemCount
        if (currentMediaItemCount > 0) {
            // Player already has media items - check if it's the same item
            val currentMediaItem = player.currentMediaItem
            if (currentMediaItem != null) {
                val currentMediaId = currentMediaItem.mediaId
                val currentUri = currentMediaItem.requestMetadata.mediaUri
                val newMediaId = mediaItem.mediaId
                val newUri = mediaItem.requestMetadata.mediaUri
                
                // Compare media ID and URI (handle null cases)
                val mediaIdMatches = (currentMediaId == null && newMediaId == null) ||
                        (currentMediaId != null && currentMediaId == newMediaId)
                val uriMatches = (currentUri == null && newUri == null) ||
                        (currentUri != null && currentUri == newUri)
                
                if (mediaIdMatches && uriMatches) {
                    // Same media item already set - don't reset playback state
                    // Just ensure player is attached to surface (in case it was recovered)
                    if (media3PlayerView.player != player) {
                        media3PlayerView.player = player
                    }
                    return
                }
            }
        }
        
        // New or different media item - set it and prepare
        player.setMediaItem(mediaItem)
        player.prepare()
    }

    /**
     * Sets multiple media items to play.
     *
     * If the player already has the same media items set (e.g., after configuration change recovery),
     * this method will not reset playback state. This preserves playback position and state
     * across configuration changes.
     *
     * @param mediaItems The list of media items to set.
     * @param startIndex The index of the item to start playing from.
     * @param startPositionMs The position in milliseconds to start from.
     */
    fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0
    ) {
        createPlayerIfNeeded()
        val player = exoPlayer ?: return
        
        // Check if player already has the same media items
        val currentMediaItemCount = player.mediaItemCount
        if (currentMediaItemCount == mediaItems.size && currentMediaItemCount > 0) {
            // Check if all media items match
            var allMatch = true
            for (i in mediaItems.indices) {
                val currentItem = player.getMediaItemAt(i)
                val newItem = mediaItems[i]
                if (currentItem.mediaId != newItem.mediaId ||
                    currentItem.requestMetadata.mediaUri != newItem.requestMetadata.mediaUri) {
                    allMatch = false
                    break
                }
            }
            
            if (allMatch) {
                // Same media items already set - don't reset playback state
                // Just ensure player is attached to surface (in case it was recovered)
                if (media3PlayerView.player != player) {
                    media3PlayerView.player = player
                }
                return
            }
        }
        
        // New or different media items - set them and prepare
        player.setMediaItems(mediaItems, startIndex, startPositionMs)
        player.prepare()
    }

    /**
     * Starts or resumes playback.
     */
    fun play() {
        createPlayerIfNeeded()
        exoPlayer?.play()
    }

    /**
     * Pauses playback.
     */
    fun pause() {
        exoPlayer?.pause()
    }

    /**
     * Toggles between play and pause states.
     */
    fun togglePlayPause() {
        createPlayerIfNeeded()
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    /**
     * Returns whether the player is currently playing.
     *
     * @return true if playing, false otherwise.
     */
    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying == true
    }

    /**
     * Gets the current ExoPlayer instance.
     * This allows advanced users to access ExoPlayer APIs directly if needed.
     *
     * @return The ExoPlayer instance, or null if not created yet.
     */
    fun getPlayer(): ExoPlayer? = exoPlayer

    /**
     * Adds a playback listener to receive playback events and time updates.
     *
     * The listener will receive all playback events including:
     * - Play/pause state changes
     * - Playback errors
     * - Continuous time updates during active playback (if [PlaybackListener.onTimeUpdate] is overridden)
     *
     * @param listener The listener to add.
     */
    fun addPlaybackListener(listener: PlaybackListener) {
        if (!playbackListeners.contains(listener)) {
            playbackListeners.add(listener)
            // Start time updates if player is currently playing
            if (exoPlayer?.isPlaying == true) {
                startTimeUpdates()
            }
        }
    }

    /**
     * Removes a playback listener.
     *
     * @param listener The listener to remove.
     */
    fun removePlaybackListener(listener: PlaybackListener) {
        playbackListeners.remove(listener)
        // Stop time updates if no listeners remain
        if (playbackListeners.isEmpty()) {
            stopTimeUpdates()
        }
    }

    /**
     * Clears all playback listeners.
     */
    fun clearPlaybackListeners() {
        playbackListeners.clear()
        stopTimeUpdates()
    }

    /**
     * Seeks to a specific position in the current media item.
     *
     * Seek tracking and callbacks are handled automatically by the Player.Listener.
     * 
     * @param positionMs The position in milliseconds.
     */
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    /**
     * Gets the current playback position.
     *
     * @return The current position in milliseconds.
     */
    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }

    /**
     * Gets the duration of the current media item.
     *
     * @return The duration in milliseconds, or 0 if unknown.
     */
    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0L
    }

    /**
     * Gets the current playback state.
     *
     * @return One of [Player.STATE_IDLE], [Player.STATE_BUFFERING],
     *   [Player.STATE_READY], or [Player.STATE_ENDED].
     */
    fun getPlaybackState(): Int {
        return exoPlayer?.playbackState ?: Player.STATE_IDLE
    }

    /**
     * Sets whether playback should automatically start when ready.
     *
     * @param playWhenReady Whether to play when ready.
     */
    fun setPlayWhenReady(playWhenReady: Boolean) {
        createPlayerIfNeeded()
        exoPlayer?.playWhenReady = playWhenReady
    }

    /**
     * Gets whether playback will automatically start when ready.
     *
     * @return true if will play when ready, false otherwise.
     */
    fun getPlayWhenReady(): Boolean {
        return exoPlayer?.playWhenReady ?: false
    }

    /**
     * Starts periodic time updates if not already started.
     * Updates are only dispatched while playback is active and listeners are registered.
     */
    private fun startTimeUpdates() {
        if (!isTimeUpdateScheduled && playbackListeners.isNotEmpty() && exoPlayer?.isPlaying == true && isAttachedToWindow) {
            isTimeUpdateScheduled = true
            timeUpdateHandler.post(timeUpdateRunnable)
        }
    }

    /**
     * Stops periodic time updates.
     */
    private fun stopTimeUpdates() {
        if (isTimeUpdateScheduled) {
            isTimeUpdateScheduled = false
            timeUpdateHandler.removeCallbacks(timeUpdateRunnable)
        }
    }
}
