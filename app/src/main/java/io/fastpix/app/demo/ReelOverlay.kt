package io.fastpix.app.demo

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import java.util.Locale

/**
 * The Instagram-style chrome drawn over a reel: profile picture, name, Follow, caption and audio
 * line bottom-left; like, comment, share and more down the right.
 *
 * UI only — likes and follows live in [ReelSocialState] in memory, and comment, share and more do
 * nothing. Nothing here touches the player, and only the controls themselves take touches, so a tap
 * anywhere else still reaches the video (play/pause) and swipes still reach the pager.
 */
@UnstableApi
@Composable
fun ReelOverlay(reel: DemoVideo, social: ReelSocialState, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        // A scrim under the text, so white type reads on any video.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))),
        )
        CreatorAndCaption(
            reel = reel,
            social = social,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 76.dp, bottom = 20.dp),
        )
        ActionColumn(
            reel = reel,
            social = social,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 10.dp, bottom = 20.dp),
        )
    }
}

/** Likes and follows the user made in this session. Follows are per creator, as on Instagram. */
class ReelSocialState {
    private val liked = mutableStateMapOf<String, Boolean>()
    private val following = mutableStateMapOf<String, Boolean>()

    fun isLiked(uid: String): Boolean = liked[uid] == true
    fun toggleLike(uid: String) {
        liked[uid] = !isLiked(uid)
    }

    fun isFollowing(creator: String): Boolean = following[creator] == true
    fun toggleFollow(creator: String) {
        following[creator] = !isFollowing(creator)
    }
}

@UnstableApi
@Composable
private fun CreatorAndCaption(reel: DemoVideo, social: ReelSocialState, modifier: Modifier) {
    var expanded by rememberSaveable(reel.uid) { mutableStateOf(false) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(
                reel.avatarUrl,
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(reel.creator.removePrefix("@"), style = overlayText(14, FontWeight.SemiBold))
            Spacer(Modifier.width(10.dp))
            val following = social.isFollowing(reel.creator)
            Text(
                text = if (following) "Following" else "Follow",
                style = overlayText(13, FontWeight.SemiBold),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xB3FFFFFF), RoundedCornerShape(8.dp))
                    .clickable { social.toggleFollow(reel.creator) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        // Two lines, with "more" when it doesn't fit; tap to read it all, tap again to fold.
        var overflowing by remember(reel.uid) { mutableStateOf(false) }
        val toggle = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { expanded = !expanded }
        Text(
            text = reel.description,
            style = overlayText(14),
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflowing = it.hasVisualOverflow },
            modifier = Modifier
                .animateContentSize()
                .then(toggle),
        )
        if (overflowing && !expanded) {
            Text(
                "more",
                style = overlayText(13, FontWeight.SemiBold).copy(color = Color(0xCCFFFFFF)),
                modifier = toggle,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("♫  ") }
                append(reel.creator.removePrefix("@"))
                append(" · Original audio")
            },
            style = overlayText(12),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .background(Color(0x33FFFFFF), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@UnstableApi
@Composable
private fun ActionColumn(reel: DemoVideo, social: ReelSocialState, modifier: Modifier) {
    val liked = social.isLiked(reel.uid)
    // A little pop when liking, like the real thing.
    val heartScale by animateFloatAsState(
        targetValue = if (liked) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "heart",
    )
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ActionButton(
            label = compactCount(reel.likes + if (liked) 1 else 0),
            onClick = { social.toggleLike(reel.uid) },
        ) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (liked) "Unlike" else "Like",
                tint = if (liked) Color(0xFFFF3040) else Color.White,
                modifier = Modifier
                    .size(30.dp)
                    .scale(heartScale),
            )
        }
        ActionButton(label = compactCount(reel.comments), onClick = {}) {
            Icon(CommentIcon, "Comments", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        ActionButton(label = compactCount(reel.shares), onClick = {}) {
            Icon(ShareIcon, "Share", tint = Color.White, modifier = Modifier.size(27.dp))
        }
        ActionButton(label = null, onClick = {}) {
            Icon(Icons.Filled.MoreVert, "More", tint = Color.White, modifier = Modifier.size(26.dp))
        }
        // The audio's cover, as Instagram shows at the foot of the column.
        Thumbnail(
            reel.avatarUrl,
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, Color.White, RoundedCornerShape(6.dp)),
        )
    }
}

@Composable
private fun ActionButton(label: String?, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        icon()
        if (label != null) {
            Spacer(Modifier.height(4.dp))
            Text(label, style = overlayText(12, FontWeight.Medium))
        }
    }
}

/** White text with a soft shadow, legible over bright frames too. */
private fun overlayText(sizeSp: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = Color.White,
    fontSize = sizeSp.sp,
    fontWeight = weight,
    shadow = Shadow(color = Color(0x80000000), blurRadius = 6f),
)

/** 1234 → "1.2K", 2500000 → "2.5M". */
private fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000f).replace(".0M", "M")
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000f).replace(".0K", "K")
    else -> n.toString()
}

private fun outlinedIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()

/** A speech bubble with its tail bottom-right, like Instagram's comment icon. */
private val CommentIcon: ImageVector = outlinedIcon(
    "Comment",
    "M21.99,4c0,-1.1 -0.89,-2 -1.99,-2L4,2c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h14l4,4 " +
        "-0.01,-18zM20,4v13.17L18.83,16L4,16L4,4h16z",
)

/** An outlined paper plane, like Instagram's share icon. */
private val ShareIcon: ImageVector = outlinedIcon(
    "Share",
    "M4.01,6.03l7.51,3.22 -7.52,-1 0.01,-2.22m7.5,8.72L4,17.97v-2.22l7.51,-1M2.01,3L2,10l15,2 " +
        "-15,2 0.01,7L23,12 2.01,3z",
)
