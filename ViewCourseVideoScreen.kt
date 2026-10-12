package com.onlygyaan.presentation.screens

import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.res.painterResource
import com.onlygyaan.R
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import android.app.Activity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.onlygyaan.FastPixCache
import com.onlygyaan.FastPixPlayerView
import com.onlygyaan.fastPixToken
import com.onlygyaan.precachePlaybackId
import com.onlygyaan.data.remote.dto.CourseDto
import com.onlygyaan.data.remote.dto.LessonDto
import com.onlygyaan.presentation.viewmodels.HomeViewModel
import com.onlygyaan.ui.theme.SFPro
import com.onlygyaan.ui.theme.SFProBold
import com.onlygyaan.ui.theme.SFProMedium
import com.onlygyaan.ui.theme.SFProRegular
import com.onlygyaan.ui.theme.SFProSemiBold
import com.onlygyaan.ui.theme.Sora
import com.onlygyaan.ui.theme.SoraRegular
import com.onlygyaan.navigation.Screen
import com.onlygyaan.util.Resource
import io.fastpix.media3.core.FastPixPlayer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.onlygyaan.ui.theme.Gilroy
import com.onlygyaan.ui.theme.GilroyBold
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.onlygyaan.data.local.DataStoreManager
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

// ─── Theme Colors ─────────────────────────────────────────────────────────────
private val BackgroundDark = Color(0xFF111111)
private val SurfaceDark    = Color(0xFF111111)
private val CardDark       = Color(0xFF242424)
private val AccentPurple   = Color(0xFF7B5EA7)
private val AccentOrange   = Color(0xFFE8600A)
private val TextPrimary    = Color(0xFFFFFFFF)
private val TextSecondary  = Color(0xFFAAAAAA)
private val ProgressPurple = Color(0xFF9B6FD4)
private val StarYellow     = Color(0xFFFFC107)
// Player/info-sheet drag handle: a flick past this speed (px/s) snaps straight
// to the next anchor in that direction, overriding the release-position threshold.
private const val FLING_VELOCITY_THRESHOLD = 1000f

// `anchors` is ascending (e.g. [Full, Half, Default]).
private fun nearestAnchorIndex(value: Float, anchors: FloatArray): Int {
    var bestIdx = 0
    var bestDist = Float.MAX_VALUE
    for (i in anchors.indices) {
        val dist = kotlin.math.abs(anchors[i] - value)
        if (dist < bestDist) { bestDist = dist; bestIdx = i }
    }
    return bestIdx
}

private fun nextAnchorIndexAbove(value: Float, anchors: FloatArray): Int {
    for (i in anchors.indices) if (anchors[i] > value + 0.5f) return i
    return anchors.lastIndex
}

private fun nextAnchorIndexBelow(value: Float, anchors: FloatArray): Int {
    for (i in anchors.indices.reversed()) if (anchors[i] < value - 0.5f) return i
    return 0
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewCourseVideoScreen(navController: NavController, courseId: String = "", startLessonId: String = "") {

    val viewModel: HomeViewModel = koinViewModel()
    val courseState by viewModel.courseState.collectAsState()
    val lessonsState by viewModel.lessonsState.collectAsState()
    val courseAssignmentId    by viewModel.courseAssignmentId.collectAsState()
    val isAssignmentSubmitted by viewModel.isAssignmentSubmitted.collectAsState()
    val assignmentState       by viewModel.assignmentState.collectAsState()
    val assignmentDetail      = (assignmentState as? Resource.Success)?.data
    val lessonProgressCache by viewModel.lessonProgressCache.collectAsState()
    val showNextEpisode  by viewModel.showNextEpisode.collectAsState()
    val globalLanguage   by viewModel.globalLanguage.collectAsState()
    val courseReviewsState    by viewModel.courseReviewsState.collectAsState()
    val currentUserId         by viewModel.currentUserId.collectAsState()
    val courseSavedLessonIds  by viewModel.courseSavedLessonIds.collectAsState()
    val course = (courseState as? Resource.Success)?.data
    Log.d("VideoPlayback", "course=${course?.id} enrolledStudents=${course?.enrolledStudents} earnedAmount=${course?.earnedAmount}")
    // Merge server-side lesson data with the in-memory progress cache so the list
    // reflects watch progress as the user plays videos — without extra API calls.
    val lessons = ((lessonsState as? Resource.Success)?.data ?: emptyList()).map { lesson ->
        val cached = lessonProgressCache[lesson.id]
        if (cached != null) {
            android.util.Log.d("VideoPlayback", "lesson=${lesson.id} → using CACHE: lastPos=${cached.lastPosition} watchPct=${cached.watchPercent} completed=${cached.isCompleted}")
            lesson.copy(progress = cached)
        } else {
            android.util.Log.d("VideoPlayback", "lesson=${lesson.id} → using API progress: lastPos=${lesson.progress?.lastPosition} watchPct=${lesson.progress?.watchPercent} completed=${lesson.progress?.isCompleted}")
            lesson
        }
    }

    // Fetch assignment details once we know the assignment ID
    LaunchedEffect(courseAssignmentId) {
        if (courseAssignmentId.isNotBlank()) viewModel.fetchAssignment(courseAssignmentId)
    }

    LaunchedEffect(courseId) {
        android.util.Log.d("VideoPlayback", "LaunchedEffect courseId='$courseId'")
        if (courseId.isNotBlank()) {
            viewModel.fetchCourse(courseId)
            viewModel.fetchLessons(courseId)
            viewModel.fetchCourseReviews(courseId)
            viewModel.fetchCourseSavedLessons(courseId)
        } else {
            android.util.Log.e("VideoPlayback", "courseId is blank — skipping fetch")
        }
    }

    var isSaved           by remember { mutableStateOf(false) }
    var selectedTab       by rememberSaveable { mutableStateOf(0) }
    var isFullscreen      by remember { mutableStateOf(false) }
    var showLanguageSheet    by remember { mutableStateOf(false) }
    var audioTracks          by remember { mutableStateOf<List<io.fastpix.media3.tracks.AudioTrack>>(emptyList()) }
    // Label of the audio track currently playing — used to highlight the right row in the sheet.
    // Label-based (not ID-based) so it stays valid across separate getAudioTracks() calls.
    var currentAudioLabel    by remember { mutableStateOf<String?>(null) }
    // Persists the user's explicit in-player language choice across lesson changes.
    var selectedTrackLabel   by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
    val view        = LocalView.current

    val context = LocalContext.current
    val density = LocalDensity.current

    // Block screen recording and screenshots while the player is visible
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // "preview" → play preview clip with overlay; "lesson" → destroy preview player,
    // build a fresh one with the first lesson's playback ID.
    // rememberSaveable so navigating away and returning (e.g. story viewer) does not reset
    // the player back to preview phase.
    // Start directly in "lesson" phase when we already know (synchronously, from nav args)
    // that this is a resume — e.g. tapped from "Pick up where you left off". Skips building
    // a preview player at all, not just hiding its overlay.
    var playerPhase              by rememberSaveable { mutableStateOf(if (startLessonId.isNotBlank()) "lesson" else "preview") }
    // The "Start this course" button + info overlay is only for a genuinely fresh course —
    // defaults hidden, and is explicitly shown only once lessonsState confirms there's no
    // progress anywhere in the course (see LaunchedEffect(lessonsState) below). Previously
    // this defaulted true and flashed on-screen for every course open, fresh or not, until
    // that same effect caught up and hid it again a beat later.
    var showPreviewOverlay       by rememberSaveable { mutableStateOf(false) }
    var showLoader               by rememberSaveable { mutableStateOf(true) }
    var selectedLessonPlaybackId by rememberSaveable { mutableStateOf<String?>(null) }
    // Hoisted above the player's key() block (rather than declared inside it) so it survives
    // player rebuilds and can be read by the Modules list to show a pause icon on whichever
    // lesson card is actually playing right now.
    var isPlaying by remember { mutableStateOf(false) }

    // Per-playback-ID resume positions — each lesson/preview independently remembers
    // where the user left off. Persists across recompositions within the screen session
    // (episode switches, story viewer, write-review, etc.).
    val resumePositions = remember { mutableStateMapOf<String, Long>() }
    // Set to true by story-bubble click → picked up by LaunchedEffect inside the key block
    // to call player.pause() before the composable goes off-screen.
    var pausePlayerFlag  by remember { mutableStateOf(false) }
    // Incremented by tapping the "Currently Playing" bar in the Modules list → picked up by
    // a LaunchedEffect inside the key block to toggle play/pause on the live player, without
    // that list needing direct access to the player instance (which lives inside key()).
    var togglePlayPauseRequest by remember { mutableStateOf(0) }

    // Derived reactively — re-reads lessonsState and selectedLessonPlaybackId on every access.
    // Avoids the capture-at-launch-time bug of LaunchedEffect lambdas.
    val currentLessonId by remember {
        derivedStateOf {
            if (playerPhase == "preview") return@derivedStateOf null
            val list = (lessonsState as? Resource.Success)?.data ?: return@derivedStateOf null
            if (selectedLessonPlaybackId != null)
                list.find { it.playbackId == selectedLessonPlaybackId }?.id
            else
                list.firstOrNull()?.id
        }
    }

    val currentLesson by remember {
        derivedStateOf {
            val list = (lessonsState as? Resource.Success)?.data ?: return@derivedStateOf null
            if (selectedLessonPlaybackId != null)
                list.find { it.playbackId == selectedLessonPlaybackId }
            else
                list.firstOrNull()
        }
    }

    var isLiked by remember(currentLesson) { mutableStateOf(currentLesson?.isLiked ?: false) }

    // Playback IDs derived from real API data
    val previewPlaybackId     = course?.previewLessons?.fastpix?.playbackId
    val firstLessonPlaybackId = lessons.firstOrNull()?.playbackId

    // Pre-cache the next couple of upcoming lessons so switching feels instant.
    // Keyed on just the ordered playback-ID list (not the full `lessons` objects) — `lessons`
    // gets a new copy every ~5s while playing (the progress ticker merges updated watch
    // position into it), which would otherwise restart this effect that often and cancel
    // in-flight pre-caching for no reason, since the actual queue window never changed.
    val orderedLessonPlaybackIds = lessons.sortedBy { it.orderIndex }.map { it.playbackId }
    LaunchedEffect(selectedLessonPlaybackId, orderedLessonPlaybackIds) {
        if (orderedLessonPlaybackIds.isEmpty()) return@LaunchedEffect
        val currentIdx = orderedLessonPlaybackIds.indexOfFirst {
            it == (selectedLessonPlaybackId ?: firstLessonPlaybackId)
        }
        if (currentIdx < 0) return@LaunchedEffect
        val urls = (currentIdx + 1..currentIdx + 2)
            .mapNotNull { orderedLessonPlaybackIds.getOrNull(it) }
            .map { id -> FastPixCache.playbackUrl(id, viewModel.fastPixToken(id)) }
        FastPixCache.preCache(urls)
    }

    // Fetch + cache server progress whenever the active lesson changes —
    // covers both the auto-playing first lesson and any manually selected lesson.
    // Also hides the Next Episode button until the last-5-sec API call confirms it.
    LaunchedEffect(currentLessonId) {
        android.util.Log.d("VideoPlayback", "LaunchedEffect(currentLessonId) fired — currentLessonId=$currentLessonId playerPhase=$playerPhase")
        viewModel.resetNextEpisode()
        val lessonId = currentLessonId ?: run {
            android.util.Log.d("VideoPlayback", "skipped fetchLessonProgress — currentLessonId is null")
            isSaved = false
            return@LaunchedEffect
        }
        android.util.Log.d("VideoPlayback", "→ fetchLessonProgress($lessonId)")
        viewModel.fetchLessonProgress(lessonId)
        isSaved = lessonId in courseSavedLessonIds
    }

    // getCourse's progress object carries the resume lesson's playback_id directly, so we
    // can start warming its token/segments the moment THIS resolves — in parallel with
    // fetchLessons() — instead of waiting for whichever of the two calls is slower before
    // the queue-init effect even knows what to fetch a token for.
    LaunchedEffect(courseState) {
        val resumePlaybackId = (courseState as? Resource.Success)?.data?.progress?.playbackId
        if (!resumePlaybackId.isNullOrBlank()) {
            android.util.Log.d("VideoPlayback", "🔥 warming resume lesson from getCourse: $resumePlaybackId")
            viewModel.precachePlaybackId(resumePlaybackId)
        }
    }

    LaunchedEffect(courseState) {
        when (val s = courseState) {
            is Resource.Success -> {
                android.util.Log.d("VideoPlayback", "courseState=Success")
                android.util.Log.d("VideoPlayback", "course.id=${s.data?.id}")
                android.util.Log.d("VideoPlayback", "previewPlaybackId=${s.data?.previewLessons?.fastpix?.playbackId}")
            }
            is Resource.Error -> android.util.Log.e("VideoPlayback", "courseState=Error msg=${s.message}")
            else -> android.util.Log.d("VideoPlayback", "courseState=${s?.let { it::class.simpleName } ?: "null"}")
        }
    }
    LaunchedEffect(lessonsState) {
        when (val s = lessonsState) {
            is Resource.Success -> {
                android.util.Log.d("VideoPlayback", "lessonsState=Success count=${s.data?.size}")
                s.data.forEachIndexed { i, l ->
                    android.util.Log.d("VideoPlayback", "lesson[$i] orderIndex=${l.orderIndex} title=${l.title} playbackId=${l.playbackId} isCompleted=${l.progress?.isCompleted} watchPct=${l.progress?.watchPercent} lastPos=${l.progress?.lastPosition}")
                }

                // Auto-skip preview when launched from saved lesson OR when course has progress
                if (playerPhase == "preview") {
                    val sorted = s.data.sortedBy { it.orderIndex }
                    val hasProgress = sorted.any { l ->
                        l.progress != null && (l.progress.isCompleted || l.progress.watchPercent > 0)
                    }
                    if (startLessonId.isNotBlank() || hasProgress) {
                        android.util.Log.d("VideoPlayback", "📖 Skipping preview → lesson mode (startLessonId=$startLessonId hasProgress=$hasProgress)")
                        showPreviewOverlay = false
                        showLoader         = true
                        playerPhase        = "lesson"
                    } else {
                        // Genuinely fresh course — nothing started anywhere, no explicit
                        // resume target. Only now show the "Start this course" overlay.
                        android.util.Log.d("VideoPlayback", "🆕 Fresh course — showing Start Course overlay")
                        showPreviewOverlay = true
                        showLoader         = false
                    }
                }
            }
            is Resource.Error -> android.util.Log.e("VideoPlayback", "lessonsState=Error msg=${s.message}")
            else -> android.util.Log.d("VideoPlayback", "lessonsState=${s?.let { it::class.simpleName } ?: "null"}")
        }
    }

    LaunchedEffect(playerPhase, lessonsState) {
        if (playerPhase == "lesson") {
            val lessons = (lessonsState as? Resource.Success)?.data

            if (!lessons.isNullOrEmpty()) {
                if (selectedLessonPlaybackId == null) {
                    val sorted = lessons.sortedBy { it.orderIndex }

                    // If launched from saved lessons, jump directly to that lesson
                    val resumeLesson = if (startLessonId.isNotBlank()) {
                        val target = sorted.firstOrNull { it.id == startLessonId }
                        android.util.Log.d("VideoPlayback", "startLessonId=$startLessonId → target=${target?.title}")
                        target ?: sorted.firstOrNull { it.progress?.isCompleted != true } ?: sorted.last()
                    } else {
                        sorted.firstOrNull { it.progress?.isCompleted != true } ?: sorted.last()
                    }

                    selectedLessonPlaybackId = resumeLesson.playbackId

                    val lastPosSec = resumeLesson.progress?.lastPosition ?: 0
                    if (lastPosSec > 0 && resumeLesson.playbackId != null) {
                        resumePositions[resumeLesson.playbackId] = lastPosSec.toLong() * 1000L
                    }

                    android.util.Log.d("VideoPlayback", "✅ Resume → [${resumeLesson.orderIndex}] \"${resumeLesson.title}\" | playbackId=${resumeLesson.playbackId}")
                }
            } else {
                android.util.Log.d("VideoPlayback", "⏳ Lessons not ready yet")
            }
        }
    }

    // After 5 s: hide the overlay buttons — preview video keeps playing.
    // playerPhase stays "preview" so the player is NOT rebuilt.
    LaunchedEffect(showPreviewOverlay) {
        if (showPreviewOverlay) {
            delay(5_000)
            showPreviewOverlay = false
        }
    }
    // Use real display pixels — immune to any inset/constraint narrowing in the hierarchy
    val displayWidthPx  = remember { context.resources.displayMetrics.widthPixels }
    val displayHeightPx = remember { context.resources.displayMetrics.heightPixels }
    // Three anchors for the draggable player/info-sheet split:
    //  - Default (640dp player height) — what a user sees the moment they land on a
    //    course, before any drag; the info sheet only shows a sliver below the player.
    //  - Half (300dp player height) — the pre-existing drag ceiling; info sheet visible
    //    at roughly half the screen. This is the original "opens to half of screen" behavior.
    //  - Full (0dp player height) — new: player collapses away entirely, info sheet fills
    //    the whole screen, video hidden behind it.
    val defaultHeightPx = with(density) { 640.dp.toPx() }
    val halfHeightPx    = with(density) { 300.dp.toPx() }
    val fullHeightPx    = 0f
    // Ascending order — used to find the nearest/next anchor during drag & tap handling.
    val sheetAnchorsPx = remember(fullHeightPx, halfHeightPx, defaultHeightPx) {
        floatArrayOf(fullHeightPx, halfHeightPx, defaultHeightPx)
    }
    // Unrestricted scope for animating the settle-to-anchor: the handle's gesture
    // detection runs inside a restricted AwaitPointerEventScope that cannot itself
    // launch/await a suspend animation, so the launch happens on this scope instead.
    val sheetSettleScope = rememberCoroutineScope()
    // Hold state directly so the NestedScrollConnection can safely read/write it
    val playerHeightState = remember { mutableFloatStateOf(defaultHeightPx) }
    val playerHeightDp by remember { derivedStateOf { with(density) { playerHeightState.floatValue.toDp() } } }
    // True once the player has fully collapsed behind the expanded info sheet —
    // drives auto pause/resume of the video (see LaunchedEffect inside the player key() block).
    val isPlayerHidden by remember { derivedStateOf { playerHeightState.floatValue <= 0.5f } }
    // -1 while stepping Default→Half→Full, +1 while stepping Full→Half→Default —
    // lets a tap at the Half anchor know which direction to continue in.
    var sheetTapDirection by remember { mutableStateOf(-1) }

    // Intercept scroll: shrink player going down, expand player going up
    val nestedScrollConnection = remember(fullHeightPx, defaultHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val current = playerHeightState.floatValue
                return when {
                    // Scrolling up → shrink player first
                    delta < 0f && current > fullHeightPx -> {
                        val newHeight = (current + delta).coerceAtLeast(fullHeightPx)
                        playerHeightState.floatValue = newHeight
                        Offset(0f, newHeight - current)
                    }
                    // Scrolling down → expand player first (only if content is at top)
                    delta > 0f && current < defaultHeightPx && scrollState.value == 0 -> {
                        val newHeight = (current + delta).coerceAtMost(defaultHeightPx)
                        playerHeightState.floatValue = newHeight
                        Offset(0f, newHeight - current)
                    }
                    else -> Offset.Zero
                }
            }

            // Explicit override required — Live Edit cannot proxy default interface
            // methods whose parameters use Kotlin inline/value classes (like Offset)
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = Offset.Zero
        }
    }

    // MainActivity.enableEdgeToEdge() already sets setDecorFitsSystemWindows(false) for the
    // whole activity — we must NOT restore it to true on dispose, or the system will add its own
    // nav-bar padding on top of the AppBottomBar's navigationBarsPadding(), causing double space.
    // We only need to ensure system bars are visible again when leaving this screen.
    DisposableEffect(Unit) {
        onDispose {
            val window = (view.context as Activity).window
            WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }
    // Toggle bar visibility only — no window layout change because we're already edge-to-edge
    LaunchedEffect(isFullscreen) {
        val window = (view.context as Activity).window
        val controller = WindowInsetsControllerCompat(window, view)
        if (isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val playerGradient = Brush.verticalGradient(
        0f to Color(0xFF212121), 0.88f to Color(0xFF181818), 1f to Color(0xFF0D0D0D)
    )

    // Single composition tree — FastPixPlayerView is NEVER removed from the tree.
    // Fullscreen is handled by changing modifiers only, so the AndroidView surface
    // stays alive and the video never goes black.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .let { if (!isFullscreen) it.nestedScroll(nestedScrollConnection) else it }
    ) {
        // ── Top bar — hidden in fullscreen ────────────────────────────────────
        if (!isFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(BackgroundDark)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                }
                Text(
                    text       = course?.translatedTitle ?: course?.title ?: "Loading…",
                    color      = TextPrimary,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f).padding(end = 12.dp)
                )
            }
        }

        // ── Player area — wrapping Box lets the preview overlay use matchParentSize()
        //    so it is always exactly the same size as the player, regardless of the
        //    required* child inside playerBoxMod that can inflate fillMaxSize() constraints.
        Box {
            // key() keeps FastPixPlayerView in the same composition slot
            // regardless of sibling additions/removals (top bar, content).
            // Modifier changes resize the container; the AndroidView inside is never recreated.
            //
            // IMPORTANT: playerBoxMod must ALWAYS have the same modifier structure
            // (layout + clipToBounds) regardless of fullscreen state. Switching between
            // Modifier.fillMaxSize() and Modifier.layout{}.clipToBounds() creates/destroys a
            // Compose graphics layer on every toggle, which re-routes the AndroidView through
            // the new layer and destabilises the SurfaceView → video goes blank on collapse.
            // Keeping the structure identical means only the height *value* changes, and no
            // layer is ever created or destroyed during the fullscreen toggle.
            // Changing playerPhase destroys this entire block and rebuilds it fresh,
            // giving a brand-new FastPixPlayer with no state from the previous phase.
            key(playerPhase) {
                // ── Per-phase player + state (reset automatically on phase change) ──

                // Pre-create the PlayerView so AnalyticsConfig can wrap it before the player
                // is built. Both are frozen in this key slot — rebuilt only when key changes.
                val fpPlayerView = remember { io.fastpix.media3.PlayerView(context) }

                val player = remember {
                    android.util.Log.d("VideoPlayback", "🏗 building player — phase=$playerPhase")
                    // Course-level analytics only (not per-lesson): this player now persists
                    // across every lesson switch within "lesson" phase, and AnalyticsConfig
                    // can't be rebound on a live player — accepted trade-off, analytics is not
                    // a concern for this pass.
                    val videoTitle = if (playerPhase == "lesson")
                        course?.title ?: ""
                    else
                        "Preview — ${course?.title ?: ""}"
                    val videoDetails = io.fastpix.data.domain.model.VideoDataDetails(
                        videoId    = course?.id ?: "",
                        videoTitle = videoTitle
                    ).apply {
                        videoSeries = course?.title ?: ""
                    }
                    val analyticsConfig = io.fastpix.media3.analytics.AnalyticsConfig.Builder(
                        fpPlayerView, "1154926753932935170"
                    )
                        .setVideoDataDetails(videoDetails)
                        .setEnabled(true)
                        .build()
                    FastPixPlayer.Builder(context)
                        .setAnalyticsConfig(analyticsConfig)
                        .setBufferConfig(io.fastpix.media3.buffer.BufferConfig.FEED)
                        .setCacheConfig(FastPixCache.cacheConfig)
                        .setAbrConfig(FastPixCache.abrConfig)
                        .let {
                            // Lets FastPix warm ~5s of the next queued lesson while the
                            // current one plays — only useful with setMediaItems(), which the
                            // lesson branch below uses.
                            if (playerPhase == "lesson") it.setPreloadConfig(io.fastpix.media3.preload.PreloadConfig.FEED)
                            else it
                        }
                        .build()
                }
                var currentMs    by remember { mutableStateOf(0L) }
                var durationMs   by remember { mutableStateOf(0L) }
                var showControls by remember { mutableStateOf(false) }
                var videoEnded  by remember { mutableStateOf(false) }
                // Set right when a new lesson's queue is pushed via setMediaItems(); consumed
                // by the next onBufferingEnd so a normal mid-lesson rebuffer is ignored.
                var expectingNewLessonReady by remember { mutableStateOf(false) }

                // Preview: single clip, plays immediately once loaded. Untouched by the
                // queue-based rewrite below — preview isn't part of the lessons queue.
                LaunchedEffect(playerPhase, previewPlaybackId) {
                    if (playerPhase != "preview") return@LaunchedEffect
                    android.util.Log.d("VideoPlayback", "📼 preparing preview media item id=$previewPlaybackId")
                    if (!previewPlaybackId.isNullOrBlank()) {
                        val playbackToken = try {
                            viewModel.fastPixToken(previewPlaybackId)
                        } catch (e: Exception) {
                            android.util.Log.e("VideoPlayback", "💥 getFastPixToken threw: ${e::class.simpleName}: ${e.message}", e)
                            null
                        }
                        // Built as a raw MediaItem + manually-constructed URL — same as the
                        // lesson/queue path below — instead of the SDK's setFastPixMediaItem()
                        // builder. Confirmed on-device: setFastPixMediaItem() rejected a
                        // verified-valid, non-blank 642-char token with "code=9010 Token is
                        // empty" (a server-side error, not a client validation — the string
                        // doesn't exist anywhere in the decompiled SDK), while the exact same
                        // token via a manually-built URL works fine on the lesson path. Whatever
                        // that builder does internally with the token, it doesn't survive intact.
                        val previewUrl = FastPixCache.playbackUrl(previewPlaybackId, playbackToken)
                        val previewItem = MediaItem.Builder()
                            .setMediaId(previewPlaybackId)
                            .setUri(previewUrl)
                            .build()
                        FastPixCache.invalidateToken(previewPlaybackId)
                        player.setMediaItems(listOf(previewItem), 0, 0L)
                        player.play(); isPlaying = true
                    }
                }

                // Lesson: set the WHOLE course's queue onto the persistent player exactly
                // once (never rebuilt per lesson — see key(playerPhase) above), then navigate
                // between lessons by seeking within that same queue instead of calling
                // setMediaItems() again. Calling setMediaItems repeatedly (the previous
                // approach) was confirmed via decompiling the SDK to force a full
                // exoPlayer.prepare() every time — discarding whatever PreloadConfig had
                // already warmed for the "next" item, defeating the entire point of the
                // queue. Keyed on orderedLessonPlaybackIds (not `lessons`) since `lessons`
                // gets a new copy every ~5s while playing (progress ticker).
                var previousFlushLessonId by remember { mutableStateOf<String?>(null) }
                var queueInitialized by remember { mutableStateOf(false) }
                // Cheap guard against a same-instance re-trigger of this effect for the exact
                // same target. NOTE: on-device this turned out insufficient on its own — the
                // actual duplicate-token-request bug came from two separate composition
                // instances each with their own remembered state, which this can't see across.
                // The real fix is FastPixCache.dedupedTokenFetch(), which coalesces concurrent
                // requests for the same playbackId at a process-wide level; this guard is kept
                // as a harmless extra for the plain same-instance case.
                var lastFastStartedId by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(playerPhase, selectedLessonPlaybackId, orderedLessonPlaybackIds) {
                    if (playerPhase != "lesson") return@LaunchedEffect
                    if (orderedLessonPlaybackIds.isEmpty()) return@LaunchedEffect
                    // Wait for the resume-lesson decision (the other LaunchedEffect above,
                    // also keyed on lessonsState) to actually set this — guessing via
                    // firstLessonPlaybackId here let this effect fire once with a wrong/
                    // premature target, then get cancelled and re-fire moments later once
                    // the real value landed. Confirmed on-device: that guess-then-correct
                    // cycle was costing ~600ms of pure waste on every course open.
                    val targetId = selectedLessonPlaybackId ?: return@LaunchedEffect
                    val targetIdx = orderedLessonPlaybackIds.indexOfFirst { it == targetId }
                    if (targetIdx < 0) return@LaunchedEffect
                    if (!queueInitialized && targetId == lastFastStartedId) return@LaunchedEffect

                    // Flush the outgoing lesson's watch position — replaces what used to
                    // happen automatically in DisposableEffect.onDispose per lesson-key-rebuild.
                    previousFlushLessonId?.let { lessonId ->
                        if (currentMs > 0) {
                            android.util.Log.d("VideoPlayback", "✅ flushing cache on switch — lessonId=$lessonId pos=${currentMs / 1000}s")
                            viewModel.cacheProgress(lessonId, (currentMs / 1000).toInt(), (durationMs / 1000).toInt())
                        }
                    }

                    currentMs = 0L; durationMs = 0L; videoEnded = false
                    expectingNewLessonReady = true
                    val startPositionMs = resumePositions[targetId] ?: 0L

                    if (!queueInitialized) {
                        lastFastStartedId = targetId
                        // Fast-start: fetch + queue ONLY the lesson about to play, so
                        // playback can begin the moment its (often already-cached) token
                        // resolves — not after every lesson in the course has one. Waiting
                        // on the whole course's tokens before the first frame was the
                        // actual bottleneck (verified on-device: ~1.2s of dead time from a
                        // handful of concurrent token fetches racing the one lesson that
                        // mattered). The rest of the queue is backfilled below, in the
                        // background, without blocking this.
                        android.util.Log.d("VideoPlayback", "⏳ fast-start awaiting token for id=$targetId")
                        val targetToken = viewModel.fastPixToken(targetId)
                        android.util.Log.d("VideoPlayback", "✓ fast-start token resolved for id=$targetId isNull=${targetToken == null}")
                        val targetUrl = FastPixCache.playbackUrl(targetId, targetToken)
                        val targetItem = MediaItem.Builder()
                            .setMediaId(targetId)
                            .setUri(targetUrl)
                            .build()
                        FastPixCache.invalidateToken(targetId)
                        android.util.Log.d("VideoPlayback", "📼 setMediaItems (fast-start, single item) id=$targetId startIdx=0 isWarm=${FastPixCache.isWarm(targetUrl)}")
                        player.setMediaItems(listOf(targetItem), 0, startPositionMs)

                        // Backfill the rest of the course around it — addMediaItems() does
                        // not disturb the item currently playing (only setMediaItems/prepare
                        // does), so this cannot interrupt what the user is already watching.
                        launch {
                            val before = orderedLessonPlaybackIds.subList(0, targetIdx).filterNotNull()
                            val after  = orderedLessonPlaybackIds.subList(targetIdx + 1, orderedLessonPlaybackIds.size).filterNotNull()

                            suspend fun buildItems(ids: List<String>) = coroutineScope {
                                ids.map { id -> async { id to viewModel.fastPixToken(id) } }.awaitAll()
                            }.map { (id, token) ->
                                val item = MediaItem.Builder().setMediaId(id).setUri(FastPixCache.playbackUrl(id, token)).build()
                                FastPixCache.invalidateToken(id)
                                item
                            }

                            val beforeItems = buildItems(before)
                            val afterItems  = buildItems(after)

                            if (beforeItems.isNotEmpty()) player.getExoPlayer().addMediaItems(0, beforeItems)
                            if (afterItems.isNotEmpty()) player.getExoPlayer().addMediaItems(beforeItems.size + 1, afterItems)

                            android.util.Log.d("VideoPlayback", "📼 backfilled queue — before=${beforeItems.size} after=${afterItems.size}")
                            queueInitialized = true
                        }
                    } else {
                        // Navigate within the existing queue — no setMediaItems, so whatever
                        // PreloadConfig already warmed for this item survives the switch.
                        android.util.Log.d("VideoPlayback", "⏭ seekTo phase=lesson idx=$targetIdx")
                        player.getExoPlayer().seekTo(targetIdx, startPositionMs)
                        // Resume explicitly instead of waiting on onBufferingEnd: that callback
                        // is gated on expectingNewLessonReady but never fires when the target
                        // item is already warm from the backfill above, which left playback
                        // stuck paused (and the loader stuck on) after every auto-advance to a
                        // pre-buffered next lesson.
                        player.play()
                        isPlaying = true
                        showLoader = false
                        showControls = true
                        expectingNewLessonReady = false
                    }

                    previousFlushLessonId = currentLessonId
                }

                DisposableEffect(Unit) {
                    onDispose {
                        // Final safety-net flush — fires on the preview→lesson transition and
                        // on leaving the screen (key(playerPhase) only rebuilds on those).
                        val lessonId = currentLessonId
                        val posSec   = (currentMs / 1000).toInt()
                        val durSec   = (durationMs / 1000).toInt()
                        android.util.Log.d("VideoPlayback", "onDispose — lessonId=$lessonId currentMs=$currentMs posSec=$posSec durSec=$durSec phase=$playerPhase")
                        if (lessonId == null) {
                            android.util.Log.w("VideoPlayback", "⚠ skipped — currentLessonId is null")
                        } else if (currentMs <= 0) {
                            android.util.Log.w("VideoPlayback", "⚠ skipped — currentMs=$currentMs (video may not have started)")
                        } else {
                            android.util.Log.d("VideoPlayback", "✅ flushing cache — lessonId=$lessonId pos=${posSec}s dur=${durSec}s")
                            viewModel.cacheProgress(lessonId, posSec, durSec)
                        }
                        android.util.Log.d("VideoPlayback", "♻️ player.release() phase=$playerPhase")
                        player.release()
                    }
                }
                // Pause when app goes to background, resume when it comes back
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_PAUSE  -> player.pause()
                            Lifecycle.Event.ON_RESUME -> if (isPlaying) player.play()
                            else                      -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // Pause the player when the story viewer is about to open
                LaunchedEffect(pausePlayerFlag) {
                    if (pausePlayerFlag) {
                        player.pause()
                        pausePlayerFlag = false
                    }
                }

                // Toggle play/pause when requested from the Modules list's "Currently
                // Playing" bar. Baseline captured at the moment this player was built so a
                // stale request left over from a lesson switch doesn't fire on the new player.
                val toggleRequestBaseline = remember { togglePlayPauseRequest }
                LaunchedEffect(togglePlayPauseRequest) {
                    if (togglePlayPauseRequest != toggleRequestBaseline) {
                        if (isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                            // Bring the player back into view if it's hidden behind an
                            // expanded sheet — resuming playback should make it visible again.
                            sheetSettleScope.launch { scrollState.animateScrollTo(0) }
                            sheetSettleScope.launch {
                                Animatable(playerHeightState.floatValue).animateTo(
                                    targetValue   = halfHeightPx,
                                    animationSpec = tween(durationMillis = 220)
                                ) {
                                    playerHeightState.floatValue = value
                                }
                            }
                        }
                    }
                }

                // Auto pause when the info sheet expands to full screen (video is hidden
                // behind it) and auto resume when it collapses back and the video is
                // visible again — only if playback was actually running before it hid.
                var resumeAfterSheetCollapse by remember { mutableStateOf(false) }
                LaunchedEffect(isPlayerHidden) {
                    if (isPlayerHidden) {
                        if (isPlaying) {
                            resumeAfterSheetCollapse = true
                            player.pause()
                        }
                    } else if (resumeAfterSheetCollapse) {
                        resumeAfterSheetCollapse = false
                        player.play()
                    }
                }

                LaunchedEffect(showControls, videoEnded) {
                    if (showControls && !videoEnded) {
                        delay(3000)
                        showControls = false
                    }
                }

                // ── Progress reporting ────────────────────────────────────────
                // Ticker only runs while playing — cancelled automatically when isPlaying turns false
                LaunchedEffect(isPlaying) {
                    if (!isPlaying) {
                        // Flush position immediately on pause
                        val lessonId = currentLessonId ?: return@LaunchedEffect
                        if (currentMs > 0) {
                            val posSec = (currentMs / 1000).toInt()
                            val durSec = (durationMs / 1000).toInt()
                            android.util.Log.d("VideoPlayback", "⏸ pause flush — lessonId=$lessonId pos=${posSec}s")
                            viewModel.sendProgress(lessonId, posSec)
                            viewModel.cacheProgress(lessonId, posSec, durSec)
                        }
                        return@LaunchedEffect
                    }
                    android.util.Log.i("VideoPlayback", "⏱ ticker started — phase=$playerPhase")
                    while (true) {
                        delay(5_000)
                        val lessonId = currentLessonId
                        if (lessonId == null) { android.util.Log.w("VideoPlayback", "⚠ skipped: lessonId is null"); continue }
                        if (currentMs <= 0)   { android.util.Log.w("VideoPlayback", "⚠ skipped: currentMs=$currentMs"); continue }
                        val posSec = (currentMs / 1000).toInt()
                        val durSec = (durationMs / 1000).toInt()
                        android.util.Log.i("VideoPlayback", "✅ ticker firing — lessonId=$lessonId pos=${posSec}s calling sendProgress")
                        viewModel.sendProgress(lessonId, posSec)
                        viewModel.cacheProgress(lessonId, posSec, durSec)
                    }
                }
                val nearEnd = durationMs > 0 && currentMs > 0 && (durationMs - currentMs) < 1500L

                LaunchedEffect(nearEnd) {
                    if (nearEnd && !videoEnded) {
                        delay(1500L)

                        if (durationMs > 0 && (durationMs - currentMs) < 1500L) {

                            if (playerPhase == "preview") {
                                // ✅ AUTO SWITCH TO LESSON 1
                                android.util.Log.d("VideoPlayback", "🎬 Preview ended → switching to Lesson 1")

                                showPreviewOverlay = false
                                showLoader = true
                                playerPhase = "lesson"

                            } else {
                                // Existing behavior for lessons
                                isPlaying = false
                                videoEnded = true
                                showControls = true

                                // If this was the last lesson, the assignment/certificate
                                // unlock state may have just changed server-side — force a
                                // re-fetch so isCourseCompleted picks it up. fetchCourse()'s
                                // own "already loaded" guard would otherwise skip this, since
                                // it was already fetched once when the screen opened.
                                val sortedLessons = lessons.sortedBy { it.orderIndex }
                                val isLastLesson = sortedLessons.lastOrNull()?.id == currentLessonId
                                android.util.Log.d("VideoPlayback", "lesson ended — currentLessonId=$currentLessonId lastLessonId=${sortedLessons.lastOrNull()?.id} isLastLesson=$isLastLesson courseId=$courseId")
                                if (courseId.isNotBlank() && isLastLesson) {
                                    android.util.Log.d("VideoPlayback", "last lesson finished → forcing course re-fetch")
                                    viewModel.fetchCourse(courseId, forceRefresh = true)
                                }
                            }
                        }
                    }
                }

                val playerBoxMod = Modifier
                    .layout { measurable, constraints ->
                        val heightPx = if (isFullscreen) displayHeightPx else playerHeightDp.roundToPx()
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth  = displayWidthPx,
                                maxWidth  = displayWidthPx,
                                minHeight = heightPx,
                                maxHeight = heightPx
                            )
                        )
                        layout(displayWidthPx, heightPx) { placeable.place(0, 0) }
                    }
                    .clipToBounds()
                // surfaceMod is INTENTIONALLY constant — never changes size based on isFullscreen.
                val surfaceMod = Modifier
                    .requiredWidth(with(density) { displayWidthPx.toDp() })
                    .requiredHeight(with(density) { displayHeightPx.toDp() })

                Box(modifier = playerBoxMod) {
                    Box(
                        modifier = surfaceMod
                            .background(playerGradient)
                            .pointerInput(isFullscreen, playerPhase, lessons, selectedLessonPlaybackId) {
                                val swipeThresholdPx = 80.dp.toPx()
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var totalDy = 0f
                                    var totalDx = 0f
                                    var isDragging = false
                                    loop@ while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break@loop
                                        if (!change.pressed) {
                                            // Finger lifted — decide tap vs swipe
                                            if (!isDragging) {
                                                // Tap: toggle controls
                                                if (playerPhase == "lesson" || (playerPhase == "preview" && !showPreviewOverlay)) showControls = !showControls
                                            } else if (isFullscreen && playerPhase == "lesson") {
                                                val currentIdx = lessons.indexOfFirst {
                                                    it.playbackId == (selectedLessonPlaybackId ?: lessons.firstOrNull()?.playbackId)
                                                }
                                                if (totalDy < -swipeThresholdPx && currentIdx < lessons.size - 1) {
                                                    // Swipe up → next lesson
                                                    val next = lessons[currentIdx + 1]
                                                    if (next.playbackId != null && !resumePositions.containsKey(next.playbackId)) {
                                                        val s = next.progress?.lastPosition ?: 0
                                                        if (s > 0) resumePositions[next.playbackId] = s.toLong() * 1000L
                                                    }
                                                    selectedLessonPlaybackId = next.playbackId
                                                    showLoader = true
                                                    isPlaying  = false
                                                } else if (totalDy < -swipeThresholdPx && currentIdx >= 0 && currentIdx == lessons.lastIndex) {
                                                    // Swipe up on the last lesson — nothing to advance to.
                                                    // Exit fullscreen, collapse the player out of the way, and
                                                    // scroll the info sheet down to the last lesson/certificate.
                                                    isFullscreen = false
                                                    sheetSettleScope.launch {
                                                        Animatable(playerHeightState.floatValue).animateTo(
                                                            targetValue   = fullHeightPx,
                                                            animationSpec = tween(durationMillis = 220)
                                                        ) {
                                                            playerHeightState.floatValue = value
                                                        }
                                                    }
                                                    sheetSettleScope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                                                } else if (totalDy > swipeThresholdPx && currentIdx > 0) {
                                                    // Swipe down → prev lesson
                                                    val prev = lessons[currentIdx - 1]
                                                    if (prev.playbackId != null && !resumePositions.containsKey(prev.playbackId)) {
                                                        val s = prev.progress?.lastPosition ?: 0
                                                        if (s > 0) resumePositions[prev.playbackId] = s.toLong() * 1000L
                                                    }
                                                    selectedLessonPlaybackId = prev.playbackId
                                                    showLoader = true
                                                    isPlaying  = false
                                                }
                                            }
                                            break@loop
                                        }
                                        val dy = change.position.y - change.previousPosition.y
                                        val dx = change.position.x - change.previousPosition.x
                                        totalDy += dy
                                        totalDx += dx
                                        if (!isDragging && (kotlin.math.abs(totalDy) > 10f || kotlin.math.abs(totalDx) > 10f)) {
                                            isDragging = true
                                        }
                                        change.consume()
                                    }
                                }
                            }
                    ) {
                        // Shared by the preview's onPlayerReady and the lesson's gated
                        // onBufferingEnd (see below) — picks the right audio track for
                        // whatever is now playing.
                        fun applyAudioTrackSelection() {
                            val tracks = player.getAudioTracks()
                            val playable = tracks.filter { it.isPlayable }
                            // Normalise whatever format DataStore saved (full name or ISO code)
                            // to a reliable 2-letter code, then derive the display name from that.
                            val code     = languageToCode(globalLanguage)
                            val langName = languageCodeToName(code)
                            // Priority 1: user's explicit in-player choice (selectedTrackLabel is
                            // a display name like "Telugu" — use the same robust matching as P2).
                            val p1Target = selectedTrackLabel?.let { label ->
                                val selCode = languageToCode(label)
                                playable.firstOrNull { t ->
                                    val lbl = (t.label?.ifBlank { null } ?: t.languageName ?: "").lowercase()
                                    lbl == label.lowercase() || lbl == selCode ||
                                    lbl.startsWith(selCode) ||
                                    (lbl == "default" && selCode == "hi")
                                }
                            }
                            // Priority 2: global home language.
                            val p2Target = playable.firstOrNull { t ->
                                val lbl = (t.label?.ifBlank { null } ?: t.languageName ?: "").lowercase()
                                lbl == langName.lowercase() || lbl == code ||
                                lbl.startsWith(code) ||
                                (lbl == "default" && code == "hi")
                            }
                            // Priority 3: fallback to "default" track, then any track.
                            val target = p1Target ?: p2Target
                                ?: playable.firstOrNull { it.label?.lowercase() == "default" }
                                ?: playable.firstOrNull()
                            if (target != null) {
                                player.setAudioTrack(target.id)
                                // currentAudioLabel reflects what's actually playing:
                                // P1 → user's stored choice; P2 → global language name; P3 → "Hindi" (default).
                                currentAudioLabel = when {
                                    p1Target != null -> selectedTrackLabel!!
                                    p2Target != null -> langName
                                    else -> {
                                        val lbl = target.label?.takeIf { it.isNotBlank() }
                                            ?: target.languageName ?: ""
                                        if (lbl.lowercase() == "default") "Default"
                                        else languageCodeToName(languageToCode(lbl))
                                    }
                                }
                            }
                        }
                        FastPixPlayerView(
                            modifier               = Modifier.fillMaxSize(),
                            player                 = player,
                            preCreatedView         = fpPlayerView,
                            onPlay                 = { isPlaying = true },
                            onPause                = { isPlaying = false },
                            onTimeUpdate           = { pos, dur ->
                                currentMs = pos; durationMs = dur
                                // Save each playback ID's position independently so switching
                                // episodes never overwrites another lesson's saved position.
                                if (pos > 0L) {
                                    val pid = if (playerPhase == "lesson")
                                        selectedLessonPlaybackId
                                    else
                                        previewPlaybackId
                                    if (pid != null) resumePositions[pid] = pos
                                }
                            },
                            onPlayerReady          = { _ ->
                                // Lesson phase no longer relies on this — FastPixPlayer only
                                // ever fires onPlayerReady once per player lifetime (confirmed
                                // by decompiling the SDK), which breaks once the player stops
                                // being rebuilt per lesson. Its job moved to the gated
                                // onBufferingEnd handler below, which fires reliably for every
                                // lesson switch.
                                if (playerPhase == "preview") {
                                    val savedPos = resumePositions[previewPlaybackId] ?: 0L
                                    if (savedPos > 0L) {
                                        player.seekTo(savedPos)
                                    }
                                    applyAudioTrackSelection()
                                }
                            },
                            onBufferingEnd         = {
                                // Gated: onBufferingEnd also fires for an ordinary mid-lesson
                                // rebuffer, so only treat it as "the new lesson is ready" when
                                // we just pushed a new queue via setMediaItems.
                                if (playerPhase == "lesson" && expectingNewLessonReady) {
                                    expectingNewLessonReady = false
                                    showLoader = false
                                    showControls = true
                                    player.play()
                                    isPlaying = true
                                    applyAudioTrackSelection()
                                }
                            },
                            onPlaybackStateChanged = { playing -> isPlaying = playing }
                        )
                    }

                    // Controls: full controls for lesson, simplified for preview
                    if (playerPhase == "preview" && !showPreviewOverlay) {
                        VideoControls(
                            modifier             = Modifier.fillMaxSize(),
                            player               = player,
                            showControls         = showControls,
                            menuExpanded         = false,
                            onMenuExpandedChange = {},
                            isPlaying            = isPlaying,
                            videoEnded           = videoEnded,
                            currentMs            = currentMs,
                            durationMs           = durationMs,
                            isFullscreen         = isFullscreen,
                            showActions          = false,
                            showPreviewActions   = true,
                            onLanguageClick      = {
                                audioTracks = player.getAudioTracks()
                                showLanguageSheet = true
                            },
                            onFullscreenToggle   = { isFullscreen = !isFullscreen; showControls = true },
                            onPlayPause          = {
                                if (videoEnded) {
                                    player.seekTo(0); player.play()
                                    videoEnded = false; isPlaying = true; currentMs = 0L
                                } else {
                                    if (isPlaying) player.pause() else player.play()
                                }
                            },
                            onSeek               = { posMs ->
                                player.seekTo(posMs)
                                currentMs = posMs
                                if (videoEnded) videoEnded = false
                            }
                        )
                    }

                    if (playerPhase == "lesson") {
                        VideoControls(
                            modifier             = Modifier.fillMaxSize(),
                            player               = player,
                            showControls         = showControls,
                            menuExpanded         = false,
                            onMenuExpandedChange = {},
                            isPlaying            = isPlaying,
                            videoEnded         = videoEnded,
                            currentMs          = currentMs,
                            durationMs         = durationMs,
                            isLiked            = isLiked,
                            isSaved            = isSaved,
                            isFullscreen       = isFullscreen,
                            showNextEpisodeBtn = run {
                                val list = (lessonsState as? Resource.Success)?.data ?: emptyList()
                                val currentIdx = list.indexOfFirst { it.playbackId == selectedLessonPlaybackId }
                                val isLastLesson = currentIdx >= 0 && currentIdx == list.size - 1
                                // showNextEpisode is a backend flag from periodic sendProgress
                                // calls (every 5s) — it may not land before the video actually
                                // finishes. videoEnded is client-truth: the lesson genuinely
                                // reached its end, so fall back to that if the server signal
                                // hasn't arrived, rather than leaving the video sitting at the
                                // end with no next-lesson prompt. nearFinish surfaces the prompt
                                // proactively 3s before the lesson actually ends, instead of only
                                // after playback has already stopped.
                                val nearFinish = durationMs > 0 && (durationMs - currentMs) <= 3000L
                                (showNextEpisode || videoEnded || nearFinish) && !isLastLesson
                            },
                            onLikeToggle       = {
                                if (isLiked) {
                                    isLiked = false
                                    viewModel.unlikeLesson(currentLessonId ?: return@VideoControls)
                                } else {
                                    isLiked = true
                                    viewModel.likeLesson(currentLessonId ?: return@VideoControls)
                                }
                            },
                            onSaveToggle       = {
                                val lessonId = currentLessonId ?: return@VideoControls
                                if (isSaved) {
                                    isSaved = false
                                    viewModel.unsaveLesson(lessonId)
                                    viewModel.markLessonUnsaved(lessonId)
                                } else {
                                    isSaved = true
                                    viewModel.saveLesson(lessonId)
                                    viewModel.markLessonSaved(lessonId)
                                }
                            },
                            onFullscreenToggle = { isFullscreen = !isFullscreen; showControls = true },
                            onLanguageClick    = {
                                audioTracks = player.getAudioTracks()
                                showLanguageSheet = true
                            },
                            onPlayPause        = {
                                if (videoEnded) {
                                    player.seekTo(0); player.play()
                                    videoEnded = false; isPlaying = true; currentMs = 0L
                                } else {
                                    if (isPlaying) player.pause() else player.play()
                                }
                            },
                            onSeek             = { posMs ->
                                player.seekTo(posMs)
                                currentMs = posMs
                                if (videoEnded) videoEnded = false
                            },
                            onNextEpisode      = {
                                val list = (lessonsState as? Resource.Success)?.data ?: return@VideoControls
                                val currentIdx = list.indexOfFirst { it.playbackId == selectedLessonPlaybackId }
                                val nextLesson = if (currentIdx >= 0 && currentIdx + 1 < list.size) list[currentIdx + 1] else null
                                if (nextLesson?.playbackId != null) {
                                    if (!resumePositions.containsKey(nextLesson.playbackId)) {
                                        val s = nextLesson.progress?.lastPosition ?: 0
                                        if (s > 0) resumePositions[nextLesson.playbackId] = s.toLong() * 1000L
                                    }
                                    viewModel.resetNextEpisode()
                                    selectedLessonPlaybackId = nextLesson.playbackId
                                    showLoader = true
                                    isPlaying  = false
                                }
                            },
                            onShare            = {
                                val courseName   = course?.translatedTitle ?: course?.title ?: ""
                                val courseId     = course?.id ?: courseId
                                val deeplink     = "${com.onlygyaan.data.remote.api.CurrentEnvironment.courseWebUrl}/course/$courseId"
                                val rewardAmount = course?.earnedAmount?.value ?: 0L
                                val shareText    = buildString {
                                    append("Hey,\nI'm learning this course on OnlyGyaan: $courseName — it's really engaging and easy to follow.\n\nLet's upskill together.\nJoin me here: $deeplink")
                                    if (rewardAmount > 0L) {
                                        append("\n\nAnd guess what, I'm earning ₹$rewardAmount for doing assignments in this course. Literally paisa vasool.")
                                    }
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        },
                                        "Share via"
                                    )
                                )
                            }
                        )
                    }


                }

                // Language sheet lives inside key(playerPhase) so it can call player.setAudioTrack
                if (showLanguageSheet) {
                    LanguageBottomSheet(
                        tracks             = audioTracks,
                        currentAudioLabel  = currentAudioLabel,
                        onDismiss          = { showLanguageSheet = false },
                        onSelect           = { track, displayName ->
                            player.setAudioTrack(track.id)
                            // Store displayName (e.g. "Telugu"), not track.label which can be
                            // "default" or blank — so priority-1 re-selection on next lesson works.
                            selectedTrackLabel = displayName
                            currentAudioLabel  = displayName
                            showLanguageSheet  = false
                        }
                    )
                }
            }

            // Preview overlay — no controls, just START / info pills
            if (showPreviewOverlay) {
                CoursePreviewOverlay(
                    modifier         = Modifier.matchParentSize(),
                    course           = course,
                    enrolledStudents = course?.enrolledStudents ?: 0,
                    earnedAmount     = course?.earnedAmount?.value ?: 0L,
                    onStartCourse    = {
                        showPreviewOverlay = false
                        showLoader         = true
                        playerPhase        = "lesson"
                    }
                )
            }

            // Loader — shown between preview ending and first lesson being ready
            if (showLoader) {
                Box(
                    modifier         = Modifier.matchParentSize().background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color       = Color.White,
                        strokeWidth = 3.dp,
                        modifier    = Modifier.size(48.dp)
                    )
                }
            }

            // "Playing Module N" label — top-left of the player, shown while a lesson plays.
            // Module number matches the position shown in the Modules tab list below.
            if (playerPhase == "lesson") {
                val moduleNumber = lessons.indexOfFirst { it.id == currentLessonId } + 1
                if (moduleNumber > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0x4D000000), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text          = "Playing Module $moduleNumber",
                            color         = Color.White.copy(alpha = 0.7f),
                            fontSize      = 12.sp,
                            fontFamily    = SFPro,
                            fontWeight    = SFProMedium,
                            letterSpacing = 0.sp
                        )
                    }
                }
            }

            // "Playing Course Preview" banner — shown after overlay is dismissed, removed when a lesson starts
            if (!showPreviewOverlay && playerPhase == "preview") {
                val previewBannerTransition = rememberInfiniteTransition(label = "previewBannerBorder")
                val previewBannerProgress by previewBannerTransition.animateFloat(
                    initialValue  = 0f,
                    targetValue   = 1f,
                    animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
                    label         = "previewBannerProgress"
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 80.dp)  // above the seek bar (~68dp top) + gap
                        .height(24.dp)
                        .drawBehind {
                            val strokePx = 1.5.dp.toPx()
                            val r        = CornerRadius(size.height / 2f)
                            val rrPath = Path().apply {
                                addRoundRect(RoundRect(
                                    rect         = Rect(0f, 0f, size.width, size.height),
                                    cornerRadius = r
                                ))
                            }
                            val pm = PathMeasure().apply { setPath(rrPath, false) }
                            val totalLen = pm.length
                            drawPath(rrPath, Color(0x33FFFFFF), style = Stroke(strokePx))
                            val headDist = previewBannerProgress * totalLen
                            val tailFrac = 0.30f
                            val steps    = 30
                            for (i in 0 until steps) {
                                val t0 = i.toFloat() / steps
                                val t1 = (i + 1f) / steps
                                val d0 = ((headDist - tailFrac * totalLen * (1f - t0)) % totalLen + totalLen) % totalLen
                                val d1 = ((headDist - tailFrac * totalLen * (1f - t1)) % totalLen + totalLen) % totalLen
                                drawLine(
                                    color       = Color(0xFF06B6D4).copy(alpha = t0),
                                    start       = pm.getPosition(d0),
                                    end         = pm.getPosition(d1),
                                    strokeWidth = strokePx,
                                    cap         = StrokeCap.Round
                                )
                            }
                            drawCircle(
                                color  = Color.White,
                                radius = strokePx * 1.8f,
                                center = pm.getPosition(headDist % totalLen)
                            )
                        }
                        .padding(start = 14.dp, end = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_play_arrow_white), null,
                            tint = Color.Unspecified, modifier = Modifier.size(10.dp))
                        Text(
                            text          = "Playing Course Preview",
                            color         = Color.White,
                            fontSize      = 12.sp,
                            fontFamily    = SFPro,
                            fontWeight    = SFProMedium,
                            lineHeight    = 22.sp,
                            letterSpacing = 0.sp
                        )
                    }
                }
            }

            // Sheet peek — lives OUTSIDE key() and all overlays so it always renders
            // on top of the preview/loader overlays and is never tinted by them.
            if (!isFullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(SurfaceDark)
                        // Custom gesture (rather than Modifier.draggable) so a plain tap and a
                        // drag can be told apart in one recognizer. Drag free-moves across all
                        // three anchors (Default/Half/Full) and snaps to the nearer or
                        // flicked-toward one on release. Tap steps one anchor at a time in a
                        // ping-pong sequence: Default → Half → Full → Half → Default → ...
                        .pointerInput(fullHeightPx, halfHeightPx, defaultHeightPx) {
                            val touchSlop = viewConfiguration.touchSlop
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(down.uptimeMillis, down.position)
                                var isDragging = false
                                var totalDy = 0f
                                loop@ while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break@loop
                                    if (!change.pressed) {
                                        val current = playerHeightState.floatValue
                                        val target: Float
                                        if (isDragging) {
                                            val velocity = velocityTracker.calculateVelocity().y
                                            target = when {
                                                // Fast upward flick → shrink to the next anchor down
                                                velocity <= -FLING_VELOCITY_THRESHOLD ->
                                                    sheetAnchorsPx[nextAnchorIndexBelow(current, sheetAnchorsPx)]
                                                // Fast downward flick → grow to the next anchor up
                                                velocity >= FLING_VELOCITY_THRESHOLD ->
                                                    sheetAnchorsPx[nextAnchorIndexAbove(current, sheetAnchorsPx)]
                                                else ->
                                                    sheetAnchorsPx[nearestAnchorIndex(current, sheetAnchorsPx)]
                                            }
                                            // Keep the tap-cycle direction consistent with where the drag settled.
                                            if (target <= fullHeightPx) sheetTapDirection = 1
                                            else if (target >= defaultHeightPx) sheetTapDirection = -1
                                        } else {
                                            // Tap → step one anchor in the ping-pong sequence
                                            val idx = nearestAnchorIndex(current, sheetAnchorsPx)
                                            val targetIdx = when (idx) {
                                                0 -> { sheetTapDirection = 1; 1 }   // leaving Full → head toward Default
                                                2 -> { sheetTapDirection = -1; 1 }  // leaving Default → head toward Full
                                                else -> (idx + sheetTapDirection).coerceIn(0, 2)
                                            }
                                            target = sheetAnchorsPx[targetIdx]
                                        }
                                        sheetSettleScope.launch {
                                            Animatable(current).animateTo(
                                                targetValue   = target,
                                                animationSpec = tween(durationMillis = 220)
                                            ) {
                                                playerHeightState.floatValue = value
                                            }
                                        }
                                        break@loop
                                    }
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val dy = change.position.y - change.previousPosition.y
                                    totalDy += dy
                                    if (!isDragging && kotlin.math.abs(totalDy) > touchSlop) {
                                        isDragging = true
                                    }
                                    if (isDragging) {
                                        val current = playerHeightState.floatValue
                                        playerHeightState.floatValue =
                                            (current + dy).coerceIn(fullHeightPx, defaultHeightPx)
                                        change.consume()
                                    }
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp).height(4.dp)
                            .align(Alignment.TopCenter).offset(y = 10.dp)
                            .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                    )
                }
            }
        }

        // ── Scrollable content — hidden in fullscreen ─────────────────────────
        if (!isFullscreen) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .navigationBarsPadding()
            ) {
                CourseInfoSection(
                    course             = course,
                    selectedTab        = selectedTab,
                    onTabSelected      = { selectedTab = it },
                    lessons            = lessons,
                    reviews            = (courseReviewsState as? Resource.Success)?.data ?: emptyList(),
                    currentUserId      = currentUserId,
                    onPausePlayer      = { pausePlayerFlag = true },
                    viewModel          = viewModel,
                    navController      = navController,
                    isLiked                  = isLiked,
                    currentLessonLikesCount  = currentLesson?.likesCount ?: 0,
                    currentLessonIsLiked     = currentLesson?.isLiked ?: false,
                    currentPlaybackId  = if (playerPhase == "preview") previewPlaybackId
                                         else selectedLessonPlaybackId ?: firstLessonPlaybackId,
                    isPlaying             = isPlaying,
                    onTogglePlayPause     = { togglePlayPauseRequest++ },
                    courseAssignmentId    = courseAssignmentId,
                    isAssignmentSubmitted = isAssignmentSubmitted,
                    assignmentDetail      = assignmentDetail,
                    onPlayLesson          = { _, playbackId ->
                        val lessonForPlay = lessons.find { it.playbackId == playbackId }
                        val isCompleted = lessonForPlay?.progress?.isCompleted == true ||
                            (lessonForPlay?.duration != null &&
                             (lessonForPlay.progress?.lastPosition ?: 0) >= lessonForPlay.duration!!)
                        if (isCompleted) {
                            // Watch Again — always restart from the beginning
                            resumePositions[playbackId] = 0L
                        } else if (!resumePositions.containsKey(playbackId)) {
                            // Pre-seed from API last_position if not already tracked this session
                            val s = lessonForPlay?.progress?.lastPosition ?: 0
                            if (s > 0) resumePositions[playbackId] = s.toLong() * 1000L
                        }
                        selectedLessonPlaybackId = playbackId
                        showPreviewOverlay       = false
                        showLoader               = true
                        playerPhase              = "lesson"
                        isPlaying                = false
                        // Bring the player into view and settle the sheet to Half so the
                        // video is clearly visible right after tapping Start/Play in the list.
                        sheetSettleScope.launch { scrollState.animateScrollTo(0) }
                        sheetSettleScope.launch {
                            Animatable(playerHeightState.floatValue).animateTo(
                                targetValue   = halfHeightPx,
                                animationSpec = tween(durationMillis = 220)
                            ) {
                                playerHeightState.floatValue = value
                            }
                        }
                    },
                    onLikeCourse       = {
                        if (isLiked) {
                            isLiked = false
                            viewModel.unlikeLesson(currentLessonId ?: return@CourseInfoSection)
                        } else {
                            isLiked = true
                            viewModel.likeLesson(currentLessonId ?: return@CourseInfoSection)
                        }
                    }
                )
            }
        }
    }

}



// ─── Video surface only (no controls) ────────────────────────────────────────

// ─── Controls overlay — caller sizes this to the visible clip area ────────────
@Composable
private fun VideoControls(
    modifier: Modifier = Modifier,
    player: FastPixPlayer,
    showControls: Boolean,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    isPlaying: Boolean,
    videoEnded: Boolean = false,
    currentMs: Long,
    durationMs: Long,
    isLiked: Boolean = false,
    isSaved: Boolean = false,
    isFullscreen: Boolean,
    showNextEpisodeBtn: Boolean = false,
    showActions: Boolean = true,
    showPreviewActions: Boolean = false,
    onLikeToggle: () -> Unit = {},
    onSaveToggle: () -> Unit = {},
    onFullscreenToggle: () -> Unit,
    onPlayPause: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onNextEpisode: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = showControls,
        enter   = fadeIn(),
        exit    = fadeOut(),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Scrim — bottom 140dp only, not full height
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))
                    )
            )

            // ── Center playback controls ───────────────────────────────────
            Row(
                modifier              = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                SeekButton(seconds = -5) { onSeek((currentMs - 5000).coerceAtLeast(0)) }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            onPlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(targetState = isPlaying, label = "playPause") { playing ->
                        Icon(
                            painter            = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = if (playing) "Pause" else "Play",
                            tint               = Color.Unspecified,
                            modifier           = Modifier.size(64.dp)
                        )
                    }
                }
                SeekButton(seconds = 5) {
                    if (videoEnded) onPlayPause()   // at end → restart from beginning
                    else if (durationMs > 0) onSeek((currentMs + 5000).coerceAtMost(durationMs))
                }
            }

            if (showActions) {
            Column(
                modifier              = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 76.dp),
                verticalArrangement   = Arrangement.spacedBy(10.dp),
                horizontalAlignment   = Alignment.End
            ) {
                VideoActionButton(onClick = onLikeToggle) {
                    Icon(
                        painter            = painterResource(if (isLiked) R.drawable.ic_like_white_filled else R.drawable.ic_like),
                        contentDescription = "Like",
                        tint               = Color.White,
                        modifier           = Modifier.size(36.dp)
                    )
                }
                VideoActionButton(onClick = onLanguageClick) {
                    Icon(painterResource(R.drawable.ic_language), "Language",
                        tint = Color.White, modifier = Modifier.size(36.dp))
                }

                VideoActionButton(onClick = onShare) {
                    Icon(painterResource(R.drawable.ic_share), "Share",
                        tint = Color.White, modifier = Modifier.size(36.dp))
                }

                // fullscreen
                VideoActionButton(onClick = onFullscreenToggle) {
                    Icon(
                        painter            = painterResource(if (isFullscreen) R.drawable.ic_collapse else R.drawable.ic_expand),
                        contentDescription = if (isFullscreen) "Collapse" else "Fullscreen",
                        tint               = Color.White,
                        modifier           = Modifier.size(36.dp)
                    )
                }

                // save — replaces the old three-dots button
                VideoActionButton(onClick = onSaveToggle) {
                    Icon(painterResource(if (isSaved) R.drawable.ic_saved_filled else R.drawable.ic_save), "Save",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp))
                }
            } // end Column
            } // end if (showActions)

            // ── Preview-only actions: language + expand/collapse ───────────
            if (showPreviewActions) {
                Column(
                    modifier            = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 76.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    VideoActionButton(onClick = onLanguageClick) {
                        Icon(painterResource(R.drawable.ic_language), "Language",
                            tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    VideoActionButton(onClick = onFullscreenToggle) {
                        Icon(
                            painter            = painterResource(if (isFullscreen) R.drawable.ic_collapse else R.drawable.ic_expand),
                            contentDescription = if (isFullscreen) "Collapse" else "Fullscreen",
                            tint               = Color.White,
                            modifier           = Modifier.size(36.dp)
                        )
                    }
                }
            }

                // ── Next Episode + Progress bar ────────────────────────────
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Next Episode button — shown only when show_next_episode == false
                    if (showNextEpisodeBtn) {
                        var targetProgress by remember(showNextEpisodeBtn) { mutableStateOf(0f) }
                        val nextEpProgress by animateFloatAsState(
                            targetValue   = targetProgress,
                            animationSpec = tween(durationMillis = 5000, easing = LinearEasing),
                            label         = "nextEpBorderProgress"
                        )

                        LaunchedEffect(showNextEpisodeBtn) {
                            targetProgress = 1f
                            delay(5_000)
                            onNextEpisode()
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xCC000000), RoundedCornerShape(20.dp))
                                .drawBehind {
                                    val strokePx = 1.5.dp.toPx()
                                    val r = CornerRadius(size.height / 2f)
                                    // Build the full rounded-rect path
                                    val rrPath = Path().apply {
                                        addRoundRect(RoundRect(
                                            rect         = Rect(0f, 0f, size.width, size.height),
                                            cornerRadius = r
                                        ))
                                    }
                                    val pm = PathMeasure().apply { setPath(rrPath, false) }
                                    val totalLen = pm.length
                                    // Faint base ring
                                    drawPath(rrPath, Color(0x33FFFFFF), style = Stroke(strokePx))
                                    // Growing arc from fixed start point
                                    if (nextEpProgress > 0f) {
                                        val segPath = Path()
                                        pm.getSegment(0f, nextEpProgress * totalLen, segPath, true)
                                        drawPath(
                                            path  = segPath,
                                            color = Color(0xFF06B6D4),
                                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                        )
                                        // Bright dot at the moving head
                                        drawCircle(
                                            color  = Color.White,
                                            radius = strokePx * 1.8f,
                                            center = pm.getPosition(nextEpProgress * totalLen)
                                        )
                                    }
                                }
                                .clickable(
                                    indication        = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onNextEpisode() }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("→|", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("NEXT LESSON", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Seek bar + time labels
                    val progress = if (durationMs > 0) currentMs.toFloat() / durationMs else 0f
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentMs),
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 10.dp, end = 10.dp)
                        )
                        Canvas(
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                                .pointerInput(durationMs) {
                                    detectTapGestures { offset ->
                                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                                        onSeek((newProgress * durationMs).toLong())
                                    }
                                }
                                .pointerInput(durationMs) {
                                    detectHorizontalDragGestures { change, _ ->
                                        val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                                        onSeek((newProgress * durationMs).toLong())
                                    }
                                }
                        ) {
                            val cy = size.height / 2f
                            val trackH = 4.dp.toPx()
                            val thumbR = 6.dp.toPx()

                            // Inactive track
                            drawLine(
                                color = Color.White.copy(alpha = 0.3f),
                                start = Offset(0f, cy),
                                end = Offset(size.width, cy),
                                strokeWidth = trackH,
                                cap = StrokeCap.Round
                            )
                            // Active track
                            val thumbX = size.width * progress
                            drawLine(
                                color = ProgressPurple,
                                start = Offset(0f, cy),
                                end = Offset(thumbX, cy),
                                strokeWidth = trackH,
                                cap = StrokeCap.Round
                            )
                            // Thumb circle — perfectly centered on track
                            drawCircle(
                                color = ProgressPurple,
                                radius = thumbR,
                                center = Offset(thumbX, cy)
                            )
                        }
                        Text(
                            text = formatTime(durationMs),
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 10.dp, end = 10.dp)
                        )
                    }
                }
            }
        }
    }


// ─── Seek Button (+5 / -5) ────────────────────────────────────────────────────
@Composable
private fun SeekButton(seconds: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(Color(0x40000000), CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter            = painterResource(if (seconds < 0) R.drawable.ic_rewind else R.drawable.ic_forward),
            contentDescription = if (seconds < 0) "Rewind" else "Forward",
            tint               = Color.White,
            modifier           = Modifier.width(38.dp).height(42.dp)
        )
    }
}

// ─── Course Preview Overlay ───────────────────────────────────────────────────
@Composable
private fun CoursePreviewOverlay(
    modifier: Modifier = Modifier,
    course: CourseDto?,
    enrolledStudents: Int = 0,
    earnedAmount: Long = 0L,
    onStartCourse: () -> Unit
) {
    val modulesCount = course?.totalLessons ?: 0
    val durationSec  = course?.totalCourseDuration ?: 0
    val hasCert      = course?.certificateAvailable ?: false

    Box(
        modifier = modifier
            .background(Color(0xBB000000))
            // Consume all taps so they don't reach the controls beneath
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(16.dp)
        ) {
            // ── START THIS COURSE button ───────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xBFFFFFFF))
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {  onStartCourse() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier              = Modifier.padding(horizontal = 10.dp)
                ) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_play_black),
                        contentDescription = null,
                        tint               = Color.Unspecified,
                        modifier           = Modifier.size(24.dp)
                    )
                    Text(
                        text          = "START THIS COURSE",
                        color         = colorResource(R.color.black),
                        fontFamily    = SFPro,
                        fontWeight    = SFProBold,
                        fontSize      = 14.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            // ── Info pills ─────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                PreviewInfoPill(icon = R.drawable.ic_modules, label = "$modulesCount LESSONS")
                PreviewInfoPill(icon = R.drawable.ic_clock,   label = formatCourseDuration(durationSec))
                if (hasCert) PreviewInfoPill(icon = R.drawable.ic_certificate, label = "CERTIFICATE")
            }

            // ── Social proof ───────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                // ── Learners / earned pill — below social proof ────────────
                if (enrolledStudents > 0 || earnedAmount > 0L) {
                    val studentsText = if (enrolledStudents > 0) "${enrolledStudents}+" else ""
                    val amountText = when {
                        earnedAmount >= 1_00_00_000L -> "₹${earnedAmount / 1_00_00_000L}Cr+"
                        earnedAmount >= 1_00_000L    -> "₹${earnedAmount / 1_00_000L}L+"
                        earnedAmount >= 1_000L       -> "₹${earnedAmount / 1_000L}K+"
                        earnedAmount > 0             -> "₹$earnedAmount"
                        else                         -> ""
                    }
                    val pillLabel = buildString {
                        if (studentsText.isNotBlank()) append("$studentsText Students have earned")
                        if (amountText.isNotBlank()) append(" $amountText")
                        if (isNotBlank()) append(" using this course")
                    }
                    Row(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x80000000))
                            .padding(horizontal = 10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier         = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("₹", color = Color.White, fontSize = 8.sp, lineHeight = 8.sp)
                        }
                        Text(
                            text       = pillLabel,
                            color      = Color.White,
                            fontSize   = 11.sp,
                            fontFamily = SFPro,
                            fontWeight = SFProMedium,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewInfoPill(icon: Int, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            painter            = painterResource(icon),
            contentDescription = null,
            tint               = Color.White,
            modifier           = Modifier.size(13.dp)
        )
        Text(
            text       = label,
            color      = Color.White,
            fontSize   = 11.sp,
            fontFamily = SFPro,
            fontWeight = SFProSemiBold
        )
    }
}

private fun formatCourseDuration(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0:00"
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}

// ─── Right Side Circular Action Button ───────────────────────────────────────
@Composable
private fun VideoActionButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(Color(0x40000000), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ─── Course Info Section ──────────────────────────────────────────────────────
@Composable
private fun CourseInfoSection(
    course: CourseDto?,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    lessons: List<LessonDto>,
    reviews: List<com.onlygyaan.data.remote.dto.ReviewDto> = emptyList(),
    currentUserId: String = "",
    onPausePlayer: () -> Unit = {},
    viewModel: HomeViewModel,
    navController: androidx.navigation.NavController,
    isLiked: Boolean = false,
    currentLessonLikesCount: Int = 0,
    currentLessonIsLiked: Boolean = false,
    onLikeCourse: () -> Unit = {},
    currentPlaybackId: String? = null,
    isPlaying: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    courseAssignmentId: String = "",
    isAssignmentSubmitted: Boolean = false,
    assignmentDetail: com.onlygyaan.data.remote.dto.AssignmentDetailDto? = null,
    onPlayLesson: (lessonId: String, playbackId: String) -> Unit = { _, _ -> }
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(start = 10.dp, end = 10.dp, bottom = 20.dp)
    ) {
        // ── Title ──────────────────────────────────────────────────────────
        Text(
            text          = course?.translatedTitle ?: course?.title ?: "Loading…",
            color         = TextPrimary,
            fontSize      = 20.sp,
            fontFamily    = SFPro,
            fontWeight    = SFProMedium,
            lineHeight    = 22.sp,
            letterSpacing = 0.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Tags ───────────────────────────────────────────────────────────
        val tags = course?.tags.orEmpty()
        if (tags.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tags.size) { index ->
                    TagChip(label = tags[index], styleIndex = index)
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))

        // ── Stats chips ────────────────────────────────────────────────────
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // ⭐ Rating
            if ((course?.avgRating ?: 0f) > 0f) {
                item {
                    StatChip(
                        icon  = R.drawable.ic_star_yellow,
                        iconTint = Color.Unspecified,
                        label = course!!.avgRating.let {
                            if (it == it.toLong().toFloat()) it.toLong().toString()
                            else "%.1f".format(it)
                        }
                    )
                }
            }
            // ♡ Likes
            item {
                StatChip(
                    icon     = if (isLiked) R.drawable.ic_like_white_filled else R.drawable.ic_like_white,
                    iconTint = Color.White,
                    label    = formatStatCount(currentLessonLikesCount + if (isLiked && !currentLessonIsLiked) 1 else 0),
                    onClick  = onLikeCourse
                )
            }
            // 📋 Modules
            if ((course?.totalLessons ?: 0) > 0) {
                item {
                    StatChip(
                        icon  = R.drawable.ic_modules,
                        label = "${course!!.totalLessons} Lesson"
                    )
                }
            }
            // 🎓 Certificate
            if (course?.certificateAvailable == true) {
                item {
                    StatChip(
                        icon  = R.drawable.ic_crown,
                        label = "VideoPlayback"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // ── About ──────────────────────────────────────────────────────────
        Text(
            text          = "About this course",
            color         = Color(0xFFD5D5D5),
            fontSize      = 16.sp,
            lineHeight    = 22.sp,
            letterSpacing = 0.sp,
            fontFamily    = SFPro,
            fontWeight    = SFProMedium
        )

        Spacer(modifier = Modifier.height(10.dp))

        var expanded by remember { mutableStateOf(false) }
        var isOverflowing by remember { mutableStateOf(false) }
        var lastVisibleCharIndex by remember { mutableStateOf(0) }
        val description = course?.translatedDescription ?: course?.description ?: ""
        if (description.isNotBlank()) {
            val descStyle = androidx.compose.ui.text.TextStyle(
                color         = TextSecondary,
                fontSize      = 12.sp,
                lineHeight    = 22.sp,
                letterSpacing = 0.sp,
                fontFamily    = SFPro,
                fontWeight    = SFProRegular
            )
            if (expanded) {
                Text(
                    text  = description,
                    style = descStyle
                )
                Text(
                    text     = "Show less",
                    style    = descStyle.copy(color = Color(0xFF3E7BFA)),
                    modifier = Modifier.clickable { expanded = false }
                )
            } else {
                val suffix = "… Read more"
                val annotated = buildAnnotatedString {
                    if (isOverflowing && lastVisibleCharIndex > 0) {
                        append(description.take(lastVisibleCharIndex).trimEnd())
                        withStyle(SpanStyle(color = Color(0xFF3E7BFA))) {
                            append(suffix)
                        }
                    } else {
                        append(description)
                    }
                }
                ClickableText(
                    text     = annotated,
                    style    = descStyle,
                    maxLines = 2,
                    overflow = if (isOverflowing) TextOverflow.Clip else TextOverflow.Ellipsis,
                    onTextLayout = { result: TextLayoutResult ->
                        if (result.hasVisualOverflow) {
                            if (lastVisibleCharIndex == 0) {
                                // First pass: detect overflow on original text
                                lastVisibleCharIndex = maxOf(0, result.getLineEnd(1) - suffix.length)
                            } else {
                                // Suffix still doesn't fit — trim 5 more chars until it does
                                lastVisibleCharIndex = maxOf(0, lastVisibleCharIndex - 5)
                            }
                            isOverflowing = true
                        }
                    },
                    onClick  = { _ ->
                        if (isOverflowing) expanded = true
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Modules / Reviews toggle ───────────────────────────────────────
        VideoBottomTabs(selectedTab = selectedTab, onTabSelected = onTabSelected)

        Spacer(modifier = Modifier.height(10.dp))
        // ── Tab content ────────────────────────────────────────────────────
        when (selectedTab) {
            0 -> ModulesContent(
                lessons                = lessons,
                courseThumbnailUrl     = course?.translatedThumbnailUrl?.takeIf { it.isNotBlank() } ?: course?.thumbnailUrl,
                viewModel              = viewModel,
                navController          = navController,
                currentPlaybackId      = currentPlaybackId,
                isPlaying              = isPlaying,
                onTogglePlayPause      = onTogglePlayPause,
                courseId               = course?.id ?: "",
                // course?.hasAssignment comes from the course endpoint and can disagree with
                // the lessons endpoint's courseAssignmentId (same kind of cross-endpoint
                // staleness as the course-completion flag) — treat a non-blank assignment ID
                // as equally valid proof an assignment exists.
                hasAssignment          = (course?.hasAssignment ?: false) || courseAssignmentId.isNotBlank(),
                isCourseCompleted      = course?.progress?.isCompleted ?: false,
                courseAssignmentId     = courseAssignmentId,
                isAssignmentSubmitted  = isAssignmentSubmitted,
                assignmentDetail       = assignmentDetail,
                certificateAvailable   = course?.certificateAvailable ?: false,
                onPlayLesson           = onPlayLesson
            )

            1 -> ReviewsContent(
                reviews        = reviews,
                currentUserId  = currentUserId,
                navController  = navController,
                onPausePlayer  = onPausePlayer,
                courseId       = course?.id ?: "",
                hasAssignment  = (course?.hasAssignment ?: false) || courseAssignmentId.isNotBlank()
            )
        }
    }
}

// ─── Modules Tab Content ──────────────────────────────────────────────────────
@Composable
private fun ModulesContent(lessons: List<LessonDto>, courseThumbnailUrl: String?, viewModel: HomeViewModel, navController: NavController, currentPlaybackId: String?, isPlaying: Boolean = false, onTogglePlayPause: () -> Unit = {}, courseId: String = "", hasAssignment: Boolean = false, isCourseCompleted: Boolean = false, courseAssignmentId: String = "", isAssignmentSubmitted: Boolean = false, assignmentDetail: com.onlygyaan.data.remote.dto.AssignmentDetailDto? = null, certificateAvailable: Boolean = false, onPlayLesson: (lessonId: String, playbackId: String) -> Unit = { _, _ -> }) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (lessons.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No lessons available", color = TextSecondary, fontSize = 14.sp)
            }
            return@Column
        }
        lessons.forEachIndexed { index, lesson ->
            LessonCard(
                index             = index + 1,
                lesson            = lesson,
                thumbnailUrl      = courseThumbnailUrl,
                isCurrentlyPlaying = !currentPlaybackId.isNullOrBlank() && lesson.playbackId == currentPlaybackId,
                isPlaying         = isPlaying,
                onTogglePlayPause = onTogglePlayPause,
                onLike            = { isLiked ->
                    if (isLiked) viewModel.unlikeLesson(lesson.id)
                    else viewModel.likeLesson(lesson.id)
                },
                onPlay            = {
                    android.util.Log.d("VideoPlayback", "onPlay tapped — lessonId=${lesson.id} playbackId=${lesson.playbackId}")
                    if (lesson.playbackId.isNullOrBlank()) {
                        android.util.Log.w("VideoPlayback", "⛔ playbackId is null/blank — video will NOT play")
                    } else {
                        android.util.Log.d("VideoPlayback", "✅ playing lessonId=${lesson.id} playbackId=${lesson.playbackId}")
                        onPlayLesson(lesson.id, lesson.playbackId!!)
                    }
                }
            )

            if (!lesson.quizId.isNullOrBlank()) {
                DashedDivider()   // top of quiz card
                QuizCard(
                    navController = navController,
                    quizId        = lesson.quizId ?: "",
                    courseId      = courseId
                )
            }
            if (index < lessons.lastIndex) {
                if (!lesson.quizId.isNullOrBlank()) {
                    DashedDivider()   // bottom of quiz card
                } else {
                    Divider(
                        color     = Color.White.copy(alpha = 0.06f),
                        thickness = 1.dp,
                        modifier  = Modifier.padding(vertical = 20.dp)
                    )
                }
            }
        }

        val isApproved = assignmentDetail?.userSubmission?.status?.value
            ?.equals("approved", ignoreCase = true) == true
        // Fallback for when the server's course-completion flag hasn't caught up yet
        // (it's only refreshed when the screen opens, or right after the last lesson
        // finishes) — unlock immediately if every lesson is already completed locally.
        val allLessonsCompletedLocally = lessons.isNotEmpty() && lessons.all { it.progress?.isCompleted == true }
        val courseUnlocked = isCourseCompleted || allLessonsCompletedLocally
        android.util.Log.d(
            "VideoPlayback",
            "lock check — hasAssignment=$hasAssignment (courseAssignmentId='$courseAssignmentId') isApproved=$isApproved " +
                "isCourseCompleted(server)=$isCourseCompleted allLessonsCompletedLocally=$allLessonsCompletedLocally " +
                "→ courseUnlocked=$courseUnlocked | lessons=${lessons.map { it.id to it.progress?.isCompleted }}"
        )

        if (hasAssignment) {
            DashedDivider()
            when {
                isApproved       -> YouvePassedCard()
                !courseUnlocked  -> LockedAssignmentCard()
                else                -> FinalExamCard(
                    navController    = navController,
                    assignmentId     = courseAssignmentId,
                    courseId         = assignmentDetail?.courseId ?: "",
                    userSubmission   = assignmentDetail?.userSubmission,
                    maxAttempts      = assignmentDetail?.maxAttempts ?: 0
                )
            }
            DashedDivider()
        }

        // ── Estimated Earnings Card ───────────────────────────────────────────
        val rewardValue = assignmentDetail?.rewardAmount?.value ?: 0
        if (rewardValue > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            EstimatedEarningsCard(rewardValue = rewardValue, credited = isApproved)
            Spacer(modifier = Modifier.height(8.dp))
        }

        android.util.Log.d("VideoPlayback", "certificateAvailable=$certificateAvailable isApproved=$isApproved → show=$certificateAvailable canDownload=$isApproved")
        if (certificateAvailable) {
            Spacer(modifier = Modifier.height(8.dp))
            CertificateCard(canDownload = isApproved, courseId = courseId)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─── Locked Assignment Card ───────────────────────────────────────────────────
@Composable
private fun LockedAssignmentCard() {
    val examGradient = Brush.linearGradient(
        colorStops = arrayOf(
            0.1041f to Color(0xFFFF8C00),
            0.8983f to Color(0xFFFB39A7)
        )
    )
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF421F41)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Gradient header — same as FinalExamCard but with lock + dimmed overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(brush = examGradient),
            contentAlignment = Alignment.Center
        ) {
            // Dim overlay to signal non-interactive
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0x55000000))
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter            = painterResource(R.drawable.ic_lock),
                    contentDescription = null,
                    tint               = Color.Black,
                    modifier           = Modifier.size(16.dp)
                )
                Text(
                    text          = "FINAL EXAM",
                    color         = Color.Black,
                    fontSize      = 16.sp,
                    lineHeight    = 16.sp,
                    fontFamily    = SFPro,
                    fontWeight    = SFProSemiBold,
                    letterSpacing = 0.sp
                )
            }
        }
        // Subtitle
        Text(
            text          = "COMPLETE ALL LESSONS TO UNLOCK",
            color         = Color.White.copy(alpha = 0.6f),
            fontSize      = 12.sp,
            lineHeight    = 16.sp,
            fontFamily    = SFPro,
            fontWeight    = SFProMedium,
            letterSpacing = 0.5.sp,
            modifier      = Modifier.padding(vertical = 14.dp)
        )
    }
}

// ─── You've Passed Card ───────────────────────────────────────────────────────
@Composable
private fun YouvePassedCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
            .height(60.dp)
            .border(1.5.dp, Color(0xFF00DF7B), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A1F0E)),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painter            = painterResource(R.drawable.ic_correct_answer),
            contentDescription = null,
            tint               = Color(0xFF00DF7B),
            modifier           = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text          = "YOU'VE PASSED",
            color         = Color(0xFF00DF7B),
            fontFamily    = SFPro,
            fontWeight    = SFProSemiBold,
            fontSize      = 16.sp,
            letterSpacing = 1.sp
        )
    }
}

// ─── Final Exam Card ─────────────────────────────────────────────────────────
@Composable
private fun FinalExamCard(
    navController: androidx.navigation.NavController,
    assignmentId: String,
    courseId: String = "",
    userSubmission: com.onlygyaan.data.remote.dto.UserSubmissionDto? = null,
    maxAttempts: Int = 0
) {
    val viewModel: HomeViewModel = org.koin.androidx.compose.koinViewModel()
    val scope = rememberCoroutineScope()

    val statusValue    = userSubmission?.status?.value ?: ""
    val statusMsg      = userSubmission?.status?.msg ?: ""
    val attemptNumber  = userSubmission?.attemptNumber ?: 0
    val attemptsLeft   = maxAttempts - attemptNumber
    val isInitiated    = statusValue.equals("initiated",  ignoreCase = true)
    val isRejected     = statusValue.equals("rejected",   ignoreCase = true)
    val isApproved     = statusValue.equals("approved",   ignoreCase = true)
    val canResubmit    = isRejected  && attemptsLeft > 0
    val canStartNew    = isApproved  && attemptsLeft > 0
    val hasSubmission  = userSubmission != null

    val examGradient = Brush.linearGradient(
        colorStops = arrayOf(
            0.1041f to Color(0xFFFF8C00),
            0.8983f to Color(0xFFFB39A7)
        )
    )

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF421F41)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasSubmission) {
            // ── Submitted: label + destination vary by status ─────────────────
            // initiated  → "VIEW STATUS" → assignment screen (continue filling)
            // rejected + attempts left → "RE-SUBMIT" → assignment screen
            // everything else → "VIEW STATUS" → status screen
            val buttonLabel = when {
                isInitiated && attemptNumber == 0 -> "START ASSIGNMENT"
                isInitiated                       -> "COMPLETE ASSIGNMENT"
                canResubmit                       -> "RE-SUBMIT"
                canStartNew                       -> "START ASSIGNMENT"
                else                              -> "VIEW STATUS"
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(brush = examGradient)
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (assignmentId.isNotBlank()) {
                            if (canResubmit || isInitiated || canStartNew) {
                                navController.navigate(com.onlygyaan.navigation.Screen.Assignment.createRoute(assignmentId))
                            } else {
                                navController.navigate(com.onlygyaan.navigation.Screen.AssignmentStatus.createRoute(assignmentId))
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text          = buttonLabel,
                    color         = Color.Black,
                    fontSize      = 16.sp,
                    lineHeight    = 16.sp,
                    fontFamily    = SFPro,
                    fontWeight    = SFProSemiBold,
                    letterSpacing = 0.sp
                )
            }

            // ── Subtitle: ATTEMPT X/Y • STATUS ───────────────────────────────
            val attemptsLabel = if (maxAttempts > 0) "ATTEMPT $attemptNumber/$maxAttempts" else "ATTEMPT $attemptNumber"
            val subtitle      = if (statusMsg.isNotBlank()) "$attemptsLabel • ${statusMsg.uppercase()}" else attemptsLabel
            Text(
                text          = subtitle,
                color         = Color.White.copy(alpha = 0.6f),
                fontSize      = 12.sp,
                lineHeight    = 16.sp,
                fontFamily    = SFPro,
                fontWeight    = SFProMedium,
                letterSpacing = 0.5.sp,
                modifier      = Modifier.padding(vertical = 14.dp)
            )
        } else {
            // ── Not submitted / initiated: gradient "FINAL EXAM" header ──────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(brush = examGradient),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text          = "FINAL EXAM",
                    color         = Color.Black,
                    fontSize      = 16.sp,
                    lineHeight    = 16.sp,
                    fontFamily    = SFPro,
                    fontWeight    = SFProSemiBold,
                    letterSpacing = 0.sp
                )
            }

            // ── START ASSIGNMENT tap area ─────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp)
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        android.util.Log.d("VideoPlayback", "START ASSIGNMENT clicked — assignmentId='$assignmentId' isInitiated=$isInitiated")
                        if (assignmentId.isNotBlank()) {
                            // If no prior submission at all, call initiate API (fire-and-forget)
                            if (userSubmission == null) {
                                scope.launch {
                                    android.util.Log.d("VideoPlayback", "initiating assignment — assessmentId=$assignmentId courseId=$courseId")
                                    viewModel.submitAssignment(assignmentId, courseId, status = "initiated")
                                }
                            }
                            navController.navigate(com.onlygyaan.navigation.Screen.Assignment.createRoute(assignmentId))
                        } else {
                            android.util.Log.w("VideoPlayback", "assignmentId is blank — course_assignment_id missing from API response")
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text          = "START ASSIGNMENT",
                    color         = Color.White.copy(alpha = 0.6f),
                    fontSize      = 12.sp,
                    fontFamily    = SFPro,
                    fontWeight    = SFProMedium,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ─── Estimated Earnings Card ─────────────────────────────────────────────────
@Composable
private fun EstimatedEarningsCard(rewardValue: Int, credited: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(147.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0.0048f to Color(0xFF6048FF),
                        0.9952f to Color(0xFF31A3C9)
                    ),
                    start = Offset(0f, Float.POSITIVE_INFINITY),
                    end   = Offset(Float.POSITIVE_INFINITY, 0f)
                )
            )
    ) {
        // Faded ₹ watermark
        Text(
            text       = "₹",
            fontSize   = 100.sp,
            color      = Color.White.copy(alpha = 0.10f),
            fontFamily = SFPro,
            fontWeight = SFProBold,
            modifier   = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 12.dp)
        )

        Column(
            modifier            = Modifier
                .fillMaxHeight()
                .padding(all = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text          = "Estimated earnings after completion",
                color         = Color.White,
                fontFamily    = SFPro,
                fontWeight    = SFProRegular,
                fontSize      = 13.sp,
                lineHeight    = 18.sp,
                letterSpacing = 0.sp
            )
            Text(
                text          = "₹${"%,d".format(rewardValue)}",
                color         = Color.White,
                fontFamily    = SFPro,
                fontWeight    = SFProBold,
                fontSize      = 26.sp,
                lineHeight    = 30.sp,
                letterSpacing = 0.sp
            )
            Box(
                modifier         = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x33000000))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter            = painterResource(if (credited) R.drawable.ic_correct_answer else R.drawable.ic_lock),
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.size(12.dp)
                    )
                    Text(
                        text          = if (credited) "CREDITED" else "LOCKED",
                        color         = Color.White,
                        fontFamily    = SFPro,
                        fontWeight    = SFProSemiBold,
                        fontSize      = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// ─── Certificate Card ────────────────────────────────────────────────────────
@Composable
private fun CertificateCard(canDownload: Boolean, courseId: String = "") {
    val context       = LocalContext.current
    val dataStore     = koinInject<DataStoreManager>()
    val scope         = rememberCoroutineScope()
    val borderGradient = Brush.linearGradient(
        colorStops = arrayOf(
            0.0048f to Color(0xFFFF4E02),
            0.9952f to Color(0xFFFF8400)
        ),
        start = Offset(0f, Float.POSITIVE_INFINITY),
        end   = Offset(Float.POSITIVE_INFINITY, 0f)
    )
    val fillGradient = Brush.linearGradient(
        colorStops = arrayOf(
            0.0048f to Color(0xFFFF6600),
            0.9952f to Color(0xFFFFB300)
        ),
        start = Offset(0f, Float.POSITIVE_INFINITY),
        end   = Offset(Float.POSITIVE_INFINITY, 0f)
    )

    // Outer box = 3dp gradient border
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(195.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(borderGradient)
            .padding(3.dp)
    ) {
        // Inner box = gradient fill
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(fillGradient)
        ) {
            Icon(
                painter            = painterResource(R.drawable.ic_certificate_badge),
                contentDescription = null,
                tint               = Color.White.copy(alpha = 0.90f),
                modifier           = Modifier
                    .width(86.dp)
                    .height(112.dp)
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            )

            // Text + pill column — on top of badge
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(start = 30.dp, end = 30.dp, top = 40.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text          = "Certificate Of Excellence",
                    color         = Color.White.copy(alpha = 0.85f),
                    fontFamily    = SFPro,
                    fontWeight    = SFProSemiBold,
                    fontSize      = 11.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text          = "CERTIFICATE",
                    color         = Color.White,
                    fontFamily    = Gilroy,
                    fontWeight    = GilroyBold,
                    fontSize      = 28.sp,
                    lineHeight    = 30.sp,
                    letterSpacing = 0.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Action pill — exact spec
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (canDownload) Color.White else Color(0x33FFFFFF)
                        )
                        .clickable(
                            enabled           = canDownload,
                            indication        = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            scope.launch {
                                val token = dataStore.authToken.first() ?: ""
                                val url   = "${com.onlygyaan.data.remote.api.CurrentEnvironment.academyUrl}/api/app/certificate/$courseId/pdf"
                                android.util.Log.d("VideoPlayback", "downloading → GET $url")
                                try {
                                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                    val request = DownloadManager.Request(Uri.parse(url))
                                        .addRequestHeader("Authorization", token)
                                        .setTitle("Course Certificate")
                                        .setDescription("Downloading your certificate…")
                                        .setMimeType("application/pdf")
                                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        .setDestinationInExternalPublicDir(
                                            Environment.DIRECTORY_DOWNLOADS,
                                            "certificate_$courseId.pdf"
                                        )
                                    dm.enqueue(request)
                                    android.util.Log.d("VideoPlayback", "✅ download enqueued for courseId=$courseId")
                                    Toast.makeText(context, "Downloading certificate…", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.util.Log.e("VideoPlayback", "❌ download failed: ${e.message}", e)
                                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        painter            = painterResource(
                            if (canDownload) R.drawable.ic_certificate else R.drawable.ic_lock
                        ),
                        contentDescription = null,
                        tint               = if (canDownload) Color(0xFFFF6600) else Color.White,
                        modifier           = Modifier.size(14.dp)
                    )
                    Text(
                        text          = if (canDownload) "DOWNLOAD CERTIFICATE" else "LOCKED",
                        color         = if (canDownload) Color(0xFFFF6600) else Color.White,
                        fontFamily    = SFPro,
                        fontWeight    = SFProSemiBold,
                        fontSize      = 14.sp,
                        lineHeight    = 16.sp,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}

// ─── Dashed Divider ───────────────────────────────────────────────────────────
// border: 0.5px dashed #FFFFFF33, dashes: 4px on / 4px off
@Composable
private fun DashedDivider() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
            .height(1.dp)
    ) {
        drawLine(
            color       = Color(0x33FFFFFF),
            start       = androidx.compose.ui.geometry.Offset(0f, 0f),
            end         = androidx.compose.ui.geometry.Offset(size.width, 0f),
            strokeWidth = 0.5.dp.toPx(),
            pathEffect  = PathEffect.dashPathEffect(
                intervals = floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                phase     = 0f
            )
        )
    }
}

// ─── Quiz Card ────────────────────────────────────────────────────────────────
// Figma: gradient button h=60dp over #1F3442 bg h=97dp, both radius=16dp, h-padding=10dp
@Composable
private fun QuizCard(
    navController: androidx.navigation.NavController,
    quizId: String,
    courseId: String = ""
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // ── Background card (#1F3442, full 97dp) ───────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(97.dp)
                .background(Color(0xFF1F3442), RoundedCornerShape(16.dp))
        ) {
            // Center text in the bottom 37dp section (below the 60dp gradient button)
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .height(37.dp)
                    .align(Alignment.BottomCenter),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text          = "QUICK QUIZ",
                    color         = Color.White,
                    fontSize      = 12.sp,
                    lineHeight    = 16.sp,
                    fontFamily    = SFPro,
                    fontWeight    = SFProSemiBold,
                    letterSpacing = 0.sp,
                    textAlign     = TextAlign.Center
                )
            }
        }
        // ── Gradient button (60dp, overlays top of card) ───────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF11FF98), Color(0xFF1E8AFF))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(
                    indication        = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    navController.navigate(
                        com.onlygyaan.navigation.Screen.QuizIntro.createRoute(quizId, courseId)
                    )
                }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter            = painterResource(R.drawable.ic_quiz),
                    contentDescription = null,
                    tint               = Color.Black,
                    modifier           = Modifier.size(18.dp)
                )
                Text(
                    text          = "TAKE A QUICK QUIZ!",
                    color         = Color.Black,
                    fontSize      = 16.sp,
                    lineHeight    = 16.sp,
                    fontFamily    = SFPro,
                    fontWeight    = SFProSemiBold,
                    letterSpacing = 0.sp
                )
            }
        }
    }
}

// ─── Lesson button state ──────────────────────────────────────────────────────
private sealed class LessonButtonState {
    object Start : LessonButtonState()
    object Resume : LessonButtonState()
    object WatchAgain : LessonButtonState()
    data class InProgress(val progressFraction: Float, val timeLeftLabel: String) : LessonButtonState()
}

// ─── Lesson action button ─────────────────────────────────────────────────────
// Layout for Start / Resume / WatchAgain:
//   [14dp] [icon 10dp] [6dp] [text] [14dp]   height = 24dp
// Layout for InProgress:
//   [10dp] [icon 10dp] [7dp] [bar] [7dp] [text] [10dp]   height = 24dp, fillMaxWidth
@Composable
private fun LessonActionButton(state: LessonButtonState, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    when (state) {

        // ── Start — blue filled ────────────────────────────────────────────
        is LessonButtonState.Start -> {
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .background(Color(0xFF3E47FF), RoundedCornerShape(50.dp))
                    .clickable(indication = null, interactionSource = interactionSource) { onClick() }
                    .padding(start = 14.dp, end = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_play_arrow_white), null,
                        tint = Color.Unspecified, modifier = Modifier.size(10.dp))
                    Text("Start", color = Color.White, fontSize = 12.sp,
                        fontFamily = SFPro, fontWeight = SFProMedium,
                        lineHeight = 22.sp, letterSpacing = 0.sp)
                }
            }
        }

        // ── Resume — white outline ─────────────────────────────────────────
        is LessonButtonState.Resume -> {
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .border(1.dp, Color.White, RoundedCornerShape(50.dp))
                    .clickable(indication = null, interactionSource = interactionSource) { onClick() }
                    .padding(start = 14.dp, end = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_play_arrow_white), null,
                        tint = Color.White, modifier = Modifier.size(10.dp))
                    Text("Resume", color = Color.White, fontSize = 12.sp,
                        fontFamily = SFPro, fontWeight = SFProMedium,
                        lineHeight = 22.sp, letterSpacing = 0.sp)
                }
            }
        }

        // ── Watch Again — dark filled ──────────────────────────────────────
        is LessonButtonState.WatchAgain -> {
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .background(Color(0xFF212121), RoundedCornerShape(50.dp))
                    .clickable(indication = null, interactionSource = interactionSource) { onClick() }
                    .padding(start = 14.dp, end = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_play_arrow_white), null,
                        tint = Color.White, modifier = Modifier.size(10.dp))
                    Text("Watch Again", color = Color.White, fontSize = 12.sp,
                        fontFamily = SFPro, fontWeight = SFProMedium,
                        lineHeight = 22.sp, letterSpacing = 0.sp)
                }
            }
        }

        // ── In Progress — dark filled, play + progress bar + "X mins left" ─
        is LessonButtonState.InProgress -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(Color(0xFF212121), RoundedCornerShape(50.dp))
                    .clickable(indication = null, interactionSource = interactionSource) { onClick() }
                    .padding(start = 10.dp, end = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    Icon(painterResource(R.drawable.ic_play_arrow_white), null,
                        tint = Color.White, modifier = Modifier.size(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF3A3A3A))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(state.progressFraction.coerceIn(0f, 1f))
                                .background(Color(0xFF3E47FF))
                        )
                    }
                    Text(state.timeLeftLabel,
                        color = Color.White, fontSize = 12.sp,
                        fontFamily = SFPro, fontWeight = SFProMedium,
                        lineHeight = 22.sp, letterSpacing = 0.sp)
                }
            }
        }
    }
}

// ─── Lesson Card ─────────────────────────────────────────────────────────────
@Composable
private fun LessonCard(index: Int, lesson: LessonDto, thumbnailUrl: String?, isCurrentlyPlaying: Boolean = false, isPlaying: Boolean = false, onTogglePlayPause: () -> Unit = {}, onLike: (isLiked: Boolean) -> Unit, onPlay: () -> Unit = {}) {
    val title      = lesson.translatedTitle.ifBlank { lesson.title }
    var isLiked    by remember { mutableStateOf(lesson.isLiked) }
    var likeCount  by remember { mutableStateOf(lesson.likesCount) }
    val resolvedThumbnail = lesson.translatedThumbnailUrl.takeIf { it.isNotBlank() }
        ?: lesson.thumbnailUrl.takeIf { it.isNotBlank() }
        ?: thumbnailUrl

    val buttonState: LessonButtonState = run {
        val p        = lesson.progress
        val duration = lesson.duration
        android.util.Log.d("VideoPlayback", "lesson=${lesson.id} duration=$duration progress=$p")
        when {
            p == null                                              -> LessonButtonState.Start.also {
                android.util.Log.d("VideoPlayback", "→ Start (no progress)")
            }
            p.isCompleted                                          -> LessonButtonState.WatchAgain.also {
                android.util.Log.d("VideoPlayback", "→ WatchAgain (isCompleted=true)")
            }
            duration != null && p.lastPosition >= duration        -> LessonButtonState.WatchAgain.also {
                android.util.Log.d("VideoPlayback", "→ WatchAgain (lastPosition=${p.lastPosition} >= duration=$duration)")
            }
            p.lastPosition > 0                                    -> {
                // duration may be null (API didn't return it) — show progress bar using watchPercent
                val fraction  = if (duration != null) p.lastPosition.toFloat() / duration.toFloat()
                                else p.watchPercent / 100f
                val secsLeft  = if (duration != null) (duration - p.lastPosition).coerceAtLeast(0) else -1
                val label     = when {
                    secsLeft < 0  -> "${p.watchPercent}% watched"
                    secsLeft >= 60 -> { val m = secsLeft / 60; val s = secsLeft % 60; if (s == 0) "${m}m left" else "${m}m ${s}s left" }
                    else           -> "${secsLeft}s left"
                }
                android.util.Log.d("VideoPlayback", "→ InProgress (lastPosition=${p.lastPosition} duration=$duration fraction=$fraction secsLeft=$secsLeft label=$label)")
                LessonButtonState.InProgress(fraction, label)
            }
            p.watchPercent > 0                                    -> {
                // lastPosition is 0 but watchPercent exists — use percent alone
                val fraction     = p.watchPercent / 100f
                val secsLeft     = if (duration != null) (duration * (100 - p.watchPercent) / 100).coerceAtLeast(0) else -1
                val label        = when {
                    secsLeft < 0   -> "${p.watchPercent}% watched"
                    secsLeft >= 60 -> { val m = secsLeft / 60; val s = secsLeft % 60; if (s == 0) "${m}m left" else "${m}m ${s}s left" }
                    else           -> "${secsLeft}s left"
                }
                android.util.Log.d("VideoPlayback", "→ InProgress via watchPercent (watchPercent=${p.watchPercent} duration=$duration fraction=$fraction)")
                LessonButtonState.InProgress(fraction, label)
            }
            else                                                   -> LessonButtonState.Start.also {
                android.util.Log.d("VideoPlayback", "→ Start (fallback: lastPosition=${p.lastPosition} watchPercent=${p.watchPercent} duration=$duration)")
            }
        }
    }

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(SurfaceDark),
        verticalAlignment = Alignment.Top
    ) {
        // ── Left: thumbnail (90×160, radius 16) ───────────────────────────
        if (!resolvedThumbnail.isNullOrBlank()) {
            AsyncImage(
                model              = resolvedThumbnail,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .width(90.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardDark)
            )
        }

        // ── Right: content ─────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text          = "Module $index",
                color         = Color(0xFFD5D5D5),
                fontSize      = 12.sp,
                fontFamily    = SFPro,
                fontWeight    = SFProMedium,
                lineHeight    = 12.sp,
                letterSpacing = 0.sp
            )

            // Lesson title — SF Pro Medium 510, 14sp, #FFFFFF, lineHeight 22sp
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text          = title,
                color         = Color(0xFFFFFFFF),
                fontSize      = 14.sp,
                fontFamily    = SFPro,
                fontWeight    = SFProMedium,
                lineHeight    = 22.sp,
                letterSpacing = 0.sp,
                maxLines      = 3,
                overflow      = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action button — replaced with "Currently Playing" tag if this lesson is active
            if (isCurrentlyPlaying) {
                // Real watch-progress fraction — covers both a lesson being watched for the
                // first time (InProgress) and one that was already fully viewed before and is
                // now playing again (WatchAgain/completed → full ring).
                val currentProgressFraction = when (buttonState) {
                    is LessonButtonState.InProgress -> buttonState.progressFraction.coerceIn(0f, 1f)
                    is LessonButtonState.WatchAgain  -> 1f
                    is LessonButtonState.Resume      -> 0f
                    is LessonButtonState.Start       -> 0f
                }
                val animatedProgress by animateFloatAsState(
                    targetValue   = currentProgressFraction,
                    animationSpec = tween(400),
                    label         = "playingProgress"
                )
                // Same horizontal track-bar look as the in-progress action button below —
                // just filled with real progress, no label.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(Color(0xFF212121), RoundedCornerShape(50.dp))
                        .clickable(
                            indication        = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onTogglePlayPause() }
                        .padding(start = 10.dp, end = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector        = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF3A3A3A))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                    .background(Color(0xFF3E47FF))
                            )
                        }
                    }
                }
            } else {
                LessonActionButton(state = buttonState, onClick = { onPlay() })
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ❤ like pill — #212121 bg, radius 10dp, 28dp tall
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .background(Color(0xFF212121), RoundedCornerShape(10.dp))
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (isLiked) {
                            isLiked = false
                            likeCount -= 1
                            onLike(true)
                        } else {
                            isLiked = true
                            likeCount += 1
                            onLike(false)
                        }
                    }
                    .padding(top = 6.dp, bottom = 6.dp, start = 10.dp, end = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter            = painterResource(
                            if (isLiked) R.drawable.ic_like_white_filled else R.drawable.ic_like_white
                        ),
                        contentDescription = "Like",
                        tint               = Color.White,
                        modifier           = Modifier.size(if (isLiked) 16.dp else 13.dp)
                    )
                    Text(
                        text       = formatStatCount(likeCount),
                        color      = Color.White,
                        fontSize   = if (isLiked) 14.sp else 12.sp,
                        fontFamily = SFPro,
                        fontWeight = SFProMedium,
                        lineHeight = if (isLiked) 14.sp else 12.sp
                    )
                }
            }
        }
    }
}

// ─── Reviews Tab Content ──────────────────────────────────────────────────────
@Composable
private fun ReviewsContent(
    reviews: List<com.onlygyaan.data.remote.dto.ReviewDto>,
    currentUserId: String = "",
    navController: NavController,
    onPausePlayer: () -> Unit = {},
    courseId: String = "",
    hasAssignment: Boolean = true
) {
    val hasReviewed = currentUserId.isNotBlank() && reviews.any { it.userInfo.userId == currentUserId }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Write a Review CTA — hidden if user already submitted a review ─
        if (!hasReviewed) Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1A1A1A))
                .border(
                    width = 1.dp,
                    color = Color(0xFF2A2A2A),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(
                    indication        = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    onPausePlayer()
                    navController.navigate(Screen.WriteReview.createRoute(courseId))
                }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text       = "Write a Review",
                        color      = TextPrimary,
                        fontFamily = SFPro,
                        fontWeight = SFProSemiBold,
                        fontSize   = 14.sp
                    )
                    Text(
                        text       = "Share your experience with others",
                        color      = TextSecondary,
                        fontFamily = SFPro,
                        fontWeight = SFProRegular,
                        fontSize   = 11.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF2563EB)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_edit),
                        contentDescription = "Write Review",
                        tint               = Color.White,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (reviews.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No reviews yet", color = TextSecondary, fontSize = 14.sp)
            }
            return@Column
        }

        // ── Story bubbles row (same as HomeScreen StoriesSection) ─────────
        val storyBorderBrush = Brush.verticalGradient(
            listOf(Color(0xFFAE00FF), Color(0xFF36E8FF))
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding        = PaddingValues(horizontal = 4.dp)
        ) {
            itemsIndexed(reviews) { index, review ->
                val firstName = review.userInfo.fullName
                    .split(" ").firstOrNull { it.isNotBlank() } ?: review.userInfo.fullName
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        android.util.Log.i("VideoPlayback", "bubble clicked → index=$index courseId=$courseId route=${Screen.StoryViewer.createRoute(index, courseId)}")
                        onPausePlayer()
                        navController.navigate(Screen.StoryViewer.createRoute(index, courseId))
                    }
                ) {
                    // Gradient ring → dark gap → avatar
                    Box(
                        modifier         = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(storyBorderBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier         = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF212121)),
                            contentAlignment = Alignment.Center
                        ) {
                            val imgUrl = review.userInfo.profileImage?.src
                            if (!imgUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model              = imgUrl,
                                    contentDescription = firstName,
                                    contentScale       = ContentScale.Crop,
                                    modifier           = Modifier.size(60.dp).clip(CircleShape)
                                )
                            } else {
                                Text(
                                    text       = firstName.take(1).uppercase(),
                                    color      = TextPrimary,
                                    fontSize   = 22.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text          = firstName,
                        color         = TextPrimary,
                        fontFamily    = Sora,
                        fontWeight    = SoraRegular,
                        fontSize      = 10.sp,
                        lineHeight    = 10.sp,
                        letterSpacing = 0.sp,
                        textAlign     = TextAlign.Center,
                        maxLines      = 1,
                        overflow      = TextOverflow.Ellipsis,
                        modifier      = Modifier.widthIn(max = 68.dp)
                    )
                }
            }
        }

        // ── Divider ───────────────────────────────────────────────────────
        Divider(color = Color.White.copy(alpha = 0.06f), thickness = 1.dp)

        // ── Review cards ──────────────────────────────────────────────────
        reviews.forEach { review ->
            ReviewCard(review = review)
        }
    }
}

@Composable
private fun ReviewCard(review: com.onlygyaan.data.remote.dto.ReviewDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardDark, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // ── Header row: avatar + name + stars ─────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            val imageUrl = review.userInfo.profileImage?.src
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model              = imageUrl,
                    contentDescription = null,
                    modifier           = Modifier.size(40.dp).clip(CircleShape),
                    contentScale       = ContentScale.Crop
                )
            } else {
                Box(
                    modifier         = Modifier
                        .size(40.dp)
                        .background(Color(0xFF3A3A3A), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = review.userInfo.fullName.take(1).uppercase(),
                        color      = TextPrimary,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = review.userInfo.fullName,
                    color      = TextPrimary,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier              = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC292929))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    repeat(5) { i ->
                        Icon(
                            imageVector        = Icons.Filled.Star,
                            contentDescription = null,
                            tint               = if (i < review.rating) Color(0xFFFFB800) else Color.White.copy(alpha = 0.3f),
                            modifier           = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // ── Review title ──────────────────────────────────────────────────
        Text(
            text       = review.header,
            color      = TextPrimary,
            fontFamily = SFPro,
            fontWeight = SFProSemiBold,
            fontSize   = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(6.dp))
        // ── Review body ───────────────────────────────────────────────────
        Text(
            text       = review.body,
            color      = TextSecondary,
            fontFamily = SFPro,
            fontWeight = SFProRegular,
            fontSize   = 13.sp,
            lineHeight = 20.sp
        )
    }
}

// ─── Tag Chip ─────────────────────────────────────────────────────────────────
// 3-color cycle: Green → Orange → Purple → …
// Uniform padding: top=12dp, bottom=12dp, start=10dp, end=10dp
private val tagTextColors = listOf(Color(0xFF00DF7B), Color(0xFFFF7300), Color(0xFFD83EFF))
private val tagBgColors   = listOf(Color(0x3300DF7B), Color(0x33FF7300), Color(0x33D83EFF))
private val tagRadii      = listOf(12.dp,              10.dp,             10.dp)

@Composable
private fun TagChip(label: String, styleIndex: Int) {
    val i = styleIndex % 3
    Box(
        modifier = Modifier
            .background(tagBgColors[i], RoundedCornerShape(tagRadii[i]))
            .padding(top = 10.dp, bottom = 10.dp, start = 12.dp, end = 12.dp)
    ) {
        Text(
            text       = label.uppercase(),
            color      = tagTextColors[i],
            fontSize   = 10.sp,
            lineHeight = 11.sp,
            fontFamily = SFPro,
            fontWeight = SFProMedium
        )
    }
}

// ─── Stat Chip ────────────────────────────────────────────────────────────────
// height=32dp, radius=50dp, padding top/bottom=7dp start/end=10dp, bg=#212121
@Composable
private fun StatChip(
    icon: Int,
    label: String,
    iconTint: Color = Color.White,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .background(Color(0xFF212121), RoundedCornerShape(50.dp))
            .then(if (onClick != null) Modifier.clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() } else Modifier)
            .padding(top = 7.dp, bottom = 7.dp, start = 10.dp, end = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter            = painterResource(icon),
                contentDescription = null,
                tint               = iconTint,
                modifier           = Modifier.size(14.dp)
            )
            Text(
                text          = label,
                color         = Color(0xFFFFFFFF),
                fontSize      = 14.sp,
                lineHeight    = 16.sp,
                fontFamily    = SFPro,
                fontWeight    = SFProRegular,
                letterSpacing = 0.sp
            )
        }
    }
}

private fun formatStatCount(count: Int): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000f).removeSuffix(".0M") + "M"
    count >= 1_000     -> "%.1fK".format(count / 1_000f).removeSuffix(".0K") + "K"
    else               -> count.toString()
}

/**
 * Normalises any language identifier (full name like "malayalam" OR ISO code like "ml")
 * to a 2-letter lowercase ISO 639-1 code.
 */
private fun languageToCode(language: String): String = when (language.trim().lowercase()) {
    "en", "english"   -> "en"
    "hi", "hindi"     -> "hi"
    "bn", "bengali"   -> "bn"
    "ml", "malayalam" -> "ml"
    "gu", "gujarati"  -> "gu"
    "ta", "tamil"     -> "ta"
    "mr", "marathi"   -> "mr"
    "te", "telugu"    -> "te"
    "kn", "kannada"   -> "kn"
    "pa", "punjabi"   -> "pa"
    else              -> language.take(2).lowercase()
}

private fun languageCodeToName(code: String): String = when (code.take(2).lowercase()) {
    "en" -> "English"
    "hi" -> "Hindi"
    "bn" -> "Bengali"
    "ml" -> "Malayalam"
    "gu" -> "Gujarati"
    "ta" -> "Tamil"
    "mr" -> "Marathi"
    "te" -> "Telugu"
    "kn" -> "Kannada"
    "pa" -> "Punjabi"
    else -> code.replaceFirstChar { it.uppercaseChar() }
}

private fun nativeScript(languageName: String): String = when (languageName.lowercase()) {
    "hindi"     -> "हिंदी"
    "marathi"   -> "मराठी"
    "tamil"     -> "தமிழ்"
    "malayalam" -> "മലയാളം"
    "gujarati"  -> "ગુજરાતી"
    "bengali"   -> "বাংলা"
    "telugu"    -> "తెలుగు"
    "kannada"   -> "ಕನ್ನಡ"
    "punjabi"   -> "ਪੰਜਾਬੀ"
    "english"   -> "English"
    else        -> languageName
}

// ─── Language Bottom Sheet ────────────────────────────────────────────────────
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageBottomSheet(
    tracks: List<io.fastpix.media3.tracks.AudioTrack>,
    currentAudioLabel: String?,
    onDismiss: () -> Unit,
    onSelect: (io.fastpix.media3.tracks.AudioTrack, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest   = onDismiss,
        sheetState         = sheetState,
        containerColor     = Color(0xDD111111),
        scrimColor         = Color.Black.copy(alpha = 0.6f),
        shape              = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle         = null
    ) {
        Column(modifier = Modifier.fillMaxWidth().height(550.dp)) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter            = painterResource(R.drawable.ic_language),
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text          = "Change audio language",
                    color         = Color.White,
                    fontSize      = 16.sp,
                    lineHeight    = 16.sp,
                    fontFamily    = SFPro,
                    fontWeight    = SFProSemiBold,
                    letterSpacing = 0.sp,
                    modifier      = Modifier.weight(1f)
                )
                Icon(
                    painter            = painterResource(R.drawable.ic_close),
                    contentDescription = "Close",
                    tint               = Color.White,
                    modifier           = Modifier
                        .size(20.dp)
                        .clickable(
                            indication        = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onDismiss() }
                )
            }

            // ── Divider ───────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            )

            // ── Language list — built entirely from FastPix audio tracks ──────
            val playableTracks = tracks.filter { it.isPlayable }
            Column(modifier = Modifier.fillMaxWidth()) {
                playableTracks.forEach { track ->
                    val rawLabel  = track.label?.takeIf { it.isNotBlank() }
                        ?: track.languageName?.takeIf { it.isNotBlank() }
                        ?: "Unknown"
                    val code        = languageToCode(rawLabel)
                    val displayName = if (rawLabel.lowercase() == "default") "Default"
                                      else languageCodeToName(code)
                    val isSelected  = currentAudioLabel != null && (
                        displayName.equals(currentAudioLabel, ignoreCase = true) ||
                        rawLabel.equals(currentAudioLabel, ignoreCase = true)
                    )
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable(
                                indication        = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onSelect(track, displayName) }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text          = displayName,
                                color         = Color.White,
                                fontSize      = 15.sp,
                                lineHeight    = 15.sp,
                                fontFamily    = SFPro,
                                fontWeight    = SFProMedium,
                                letterSpacing = 0.sp
                            )
                            val native = nativeScript(displayName)
                            if (native != displayName) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text          = native,
                                    color         = Color(0xFF888888),
                                    fontSize      = 13.sp,
                                    lineHeight    = 13.sp,
                                    fontFamily    = SFPro,
                                    fontWeight    = SFProRegular,
                                    letterSpacing = 0.sp
                                )
                            }
                        }
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .border(1.dp, Color.White, RoundedCornerShape(50.dp))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text          = "Selected",
                                    color         = Color.White,
                                    fontSize      = 12.sp,
                                    lineHeight    = 12.sp,
                                    fontFamily    = SFPro,
                                    fontWeight    = SFProMedium,
                                    letterSpacing = 0.sp
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.06f))
                    )
                }
            }
        }
    }
}

// ─── Bottom Tabs ──────────────────────────────────────────────────────────────
@Composable
private fun VideoBottomTabs(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        listOf("Lessons", "Reviews").forEachIndexed { index, label ->
            val isSelected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .background(
                        color = if (isSelected) TextPrimary else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onTabSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.Black else TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}