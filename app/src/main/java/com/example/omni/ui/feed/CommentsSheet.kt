package com.example.omni.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFeedTimestamp
import com.example.omni.ui.theme.OmniFeedVerified
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniScrim
import com.example.omni.ui.theme.OmniSectionTitle
import com.example.omni.ui.theme.OmniSheetShadow
import com.example.omni.ui.theme.OmniSheetSurface

/**
 * The comments sheet — how a post's conversation is read and joined (Phase 8).
 *
 * The same invented-UI rule as the sleep and meal sheets (`UI_ARCHITECTURE.md` §6 rule 11): the
 * hospitals sheet's surface, shadow, grab handle and reveal timing, with the feed's own type. The
 * composer at its foot is the grey field surface with the ink button the auth pages use.
 *
 * The comment list scrolls inside a height-capped box rather than the sheet growing with it, so the
 * composer stays pinned above the keyboard (`imePadding`) and the sheet never taller than the page.
 */
@Composable
internal fun CommentsSheet(
    visible: Boolean,
    comments: List<CommentRow>,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(SheetMillis)),
            exit = fadeOut(tween(SheetMillis)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OmniScrim)
                    .noRipple(onClick = onDismiss),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(SheetMillis)) { it } + fadeIn(tween(SheetMillis)),
            exit = slideOutVertically(tween(SheetMillis)) { it } + fadeOut(tween(SheetMillis)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            CommentsPanel(comments = comments, onDismiss = onDismiss, onSend = onSend)
        }
    }
}

@Composable
private fun CommentsPanel(
    comments: List<CommentRow>,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, SheetShape, ambientColor = OmniSheetShadow, spotColor = OmniSheetShadow)
            .clip(SheetShape)
            .background(OmniSheetSurface)
            .noRipple(onClick = {})
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = SheetPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(GrabTop))
        Image(
            painter = painterResource(R.drawable.ic_hosp_grab),
            contentDescription = "Close",
            modifier = Modifier
                .width(59.dp)
                .height(5.dp)
                .noRipple(onClick = onDismiss),
        )

        Spacer(Modifier.height(HeadingTop))
        Text(
            text = "Comments",
            style = HomeType.SectionTitle,
            color = OmniSectionTitle,
            maxLines = 1,
        )

        Spacer(Modifier.height(ListTop))

        // The list: capped, scrollable, and empty-stated. An empty list is the normal state of a
        // post nobody has replied to, and the honest line beats an invisible blank.
        if (comments.isEmpty()) {
            Text(
                text = "No comments yet. Start the conversation.",
                style = HomeType.CardFootnote,
                color = OmniFeedVerified,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ListMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CommentGap),
            ) {
                comments.forEach { comment ->
                    CommentRowCard(comment = comment)
                }
            }
        }

        Spacer(Modifier.height(ComposerTop))

        // The composer: the feed's grey surface with the auth pages' ink button beside it.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ComposerGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(ComposerHeight)
                    .clip(RoundedCornerShape(ComposerCorner))
                    .background(ComposerSurface),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(MaxCommentLength) },
                    singleLine = true,
                    textStyle = FeedType.Hint16.copy(color = OmniInk),
                    cursorBrush = SolidColor(OmniInk),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text("Add a comment…", style = FeedType.Hint16, color = OmniFeedHint)
                        }
                        Box(Modifier.width(0.dp)) { inner() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            Box(
                modifier = Modifier
                    .size(SendSize)
                    .clip(CircleShape)
                    .background(
                        if (draft.isBlank()) OmniFeedHint else OmniInk,
                    )
                    .clickable(enabled = draft.isNotBlank()) {
                        onSend(draft)
                        draft = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Send",
                    style = FeedType.Meta12,
                    color = OmniOnInk,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(SheetBottom))
    }
}

@Composable
private fun CommentRowCard(comment: CommentRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // The commenter's initial on the feed's grey — no photo is denormalised onto comments, and
        // an initial cannot fail to load.
        Box(
            modifier = Modifier
                .size(CommentAvatarSize)
                .clip(CircleShape)
                .background(ComposerSurface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = comment.authorName.take(1).uppercase(),
                style = FeedType.Meta12,
                color = OmniFeedTimestamp,
                maxLines = 1,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = comment.authorName,
                    style = FeedType.Meta12,
                    color = OmniCardInk,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = comment.timeText,
                    style = FeedType.Meta12,
                    color = OmniFeedTimestamp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Text(
                text = comment.body,
                style = FeedType.PostBody,
                color = OmniInk,
            )
        }
    }
}

@Composable
private fun Modifier.noRipple(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

/** Rounded at the top only, like the sleep and meal sheets. */
private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/** The hospitals sheet's own gutter and handle inset, borrowed as the other sheets do. */
private val SheetPadding = 18.dp
private val GrabTop = 6.5.dp
private val HeadingTop = 22.dp
private val ListTop = 16.dp
private val ComposerTop = 18.dp
private val SheetBottom = 20.dp

/** Tall enough for five comments without pushing the composer under the keyboard. */
private val ListMaxHeight = 320.dp

private val CommentGap = 14.dp
private val CommentAvatarSize = 34.dp
private val ComposerHeight = 46.dp
private val ComposerCorner = 23.dp
private val ComposerGap = 10.dp
private val SendSize = 46.dp

/** #F5F5F5 — the field surface the auth pages and the search pill already use. */
private val ComposerSurface = com.example.omni.ui.theme.OmniFieldSurface

/** Long enough for a thoughtful reply, short enough that three fit the list. */
private const val MaxCommentLength = 280

/** `SosScreen`'s own reveal timing, the same as the sleep and meal sheets. */
private const val SheetMillis = 280
