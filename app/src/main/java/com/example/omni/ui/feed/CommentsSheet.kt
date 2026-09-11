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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.omni.R
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniAuthError
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
    /**
     * Sends the draft. Called back with `null` on success — which is what clears the field — and a
     * sentence to show otherwise, the same shape the composer and the story sheet already use: the
     * draft survives a refused write, because retyping a comment the app silently dropped is worse
     * than any error line.
     */
    onSend: (String, (String?) -> Unit) -> Unit,
    /** Opens a commenter's public page — the same tap the feed's post avatar carries. */
    onOpenProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Closing the sheet takes the keyboard with it. Without this the IME stays up over a feed
        // with no field on it, and the only way back is the system back button.
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val dismiss: () -> Unit = {
            focus.clearFocus()
            keyboard?.hide()
            onDismiss()
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(SheetMillis)),
            exit = fadeOut(tween(SheetMillis)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OmniScrim)
                    .noRipple(onClick = dismiss),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(SheetMillis)) { it } + fadeIn(tween(SheetMillis)),
            exit = slideOutVertically(tween(SheetMillis)) { it } + fadeOut(tween(SheetMillis)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            CommentsPanel(
                comments = comments,
                onDismiss = dismiss,
                onSend = onSend,
                onOpenProfile = onOpenProfile,
            )
        }
    }
}

@Composable
private fun CommentsPanel(
    comments: List<CommentRow>,
    onDismiss: () -> Unit,
    onSend: (String, (String?) -> Unit) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }

    // Dismissing the keyboard is the sheet's job, not the system's: the IME is raised by the field
    // and nothing lowers it when the sheet slides away, so it would be left covering the feed.
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ListMaxHeight),
                verticalArrangement = Arrangement.spacedBy(CommentGap),
            ) {
                // Keyed, so a comment arriving on a live listener slides the others rather than
                // rebuilding them — and so only what fits inside [ListMaxHeight] is ever composed
                // instead of all fifty the query now carries.
                items(items = comments, key = { it.id }) { comment ->
                    CommentRowCard(
                        comment = comment,
                        onOpenProfile = { onOpenProfile(comment.authorId) },
                    )
                }
            }
        }

        Spacer(Modifier.height(ComposerTop))

        // The composer: the feed's grey surface with the auth pages' ink button beside it.
        //
        // Bottom-aligned rather than centred because the field *grows*: a reply long enough to wrap
        // pushes its own top up while the Send button stays on the baseline it started on, which is
        // how every messaging composer on the platform behaves.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ComposerGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = ComposerHeight)
                    .clip(RoundedCornerShape(ComposerCorner))
                    .background(ComposerSurface),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = {
                        draft = it.take(MaxCommentLength)
                        sendError = null
                    },
                    enabled = !sending,
                    // Multiline, capped at [ComposerMaxLines]. Past that the field scrolls inside
                    // itself rather than eating the comment list above it — 280 characters at this
                    // width is about six lines, and a composer taller than the conversation it is
                    // part of is the wrong trade.
                    singleLine = false,
                    maxLines = ComposerMaxLines,
                    // Sentence capitalisation and a newline key: Enter adds a line rather than
                    // sending, because the field is multiline and a send key on a wrapping field
                    // truncates replies people meant to finish. Send is the button.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                    ),
                    textStyle = FeedType.Hint16.copy(color = OmniInk),
                    cursorBrush = SolidColor(OmniInk),
                    // Placeholder and field in *one* box, the project's own `decorationBox` shape.
                    // They used to be siblings with the field boxed at `width(0.dp)`: the hint drew,
                    // so the composer looked present, while the thing you type into had no width and
                    // therefore no caret and no hit area. That is why there was "no way to comment".
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (draft.isEmpty()) {
                                Text(
                                    text = "Add a comment…",
                                    style = FeedType.Hint16,
                                    color = OmniFeedHint,
                                    maxLines = 1,
                                )
                            }
                            inner()
                        }
                    },
                    // 20 (Hint16's line) + 13 + 13 = 46, so an empty composer is exactly the height
                    // it was before it could wrap.
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                )
            }

            val canSend = draft.isNotBlank() && !sending
            Box(
                modifier = Modifier
                    .size(SendSize)
                    .clip(CircleShape)
                    .background(if (canSend) OmniInk else OmniFeedHint)
                    .clickable(enabled = canSend) {
                        val body = draft.trim()
                        // Whitespace-only is caught by `canSend`, but a draft that is text *plus*
                        // trailing newlines is not — it is sent trimmed, so the comment that lands
                        // is the comment that was written.
                        if (body.isEmpty()) return@clickable
                        sending = true
                        sendError = null
                        onSend(body) { failure ->
                            sending = false
                            sendError = failure
                            // Only a send that landed empties the field — and takes the keyboard
                            // with it, so the comment that was just written is the first thing
                            // visible rather than hidden behind the IME.
                            if (failure == null) {
                                draft = ""
                                focus.clearFocus()
                                keyboard?.hide()
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (sending) "…" else "Send",
                    style = FeedType.Meta12,
                    color = OmniOnInk,
                    maxLines = 1,
                )
            }
        }

        // Why the comment is still sitting in the field. Drawn only when there is something to say,
        // so an ordinary sheet keeps its spacing exactly.
        if (sendError != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = sendError.orEmpty(),
                style = FeedType.Meta12,
                color = OmniAuthError,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(SheetBottom))
    }
}

@Composable
private fun CommentRowCard(comment: CommentRow, onOpenProfile: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // The commenter's own photo, resolved live from their `authorId` by the ViewModel, with
        // their initial underneath it — an initial cannot fail to load, and a comment written before
        // they had a photo still shows the photo they have now. It is the tap that opens their page,
        // the convention the feed's post avatar already teaches: an avatar is a person.
        Box(
            modifier = Modifier
                .size(CommentAvatarSize)
                .clip(CircleShape)
                .background(ComposerSurface)
                .clickable(onClick = onOpenProfile),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = comment.authorName.take(1).uppercase(),
                style = FeedType.Meta12,
                color = OmniFeedTimestamp,
                maxLines = 1,
            )
            if (comment.authorPhotoUrl != null) {
                AsyncImage(
                    model = comment.authorPhotoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
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
/** The composer's *resting* height — `Hint16`'s 20sp line plus 13 above and 13 below. */
private val ComposerHeight = 46.dp
private val ComposerCorner = 23.dp
private val ComposerGap = 10.dp
private val SendSize = 46.dp

/**
 * How far the field is allowed to grow before it scrolls inside itself.
 *
 * Four lines is about 46 + 3 × 20 = 106dp of composer. Past that it would start eating the comment
 * list above it, which is the conversation the reply is part of.
 */
private const val ComposerMaxLines = 4

/** #F5F5F5 — the field surface the auth pages and the search pill already use. */
private val ComposerSurface = com.example.omni.ui.theme.OmniFieldSurface

/** Long enough for a thoughtful reply, short enough that three fit the list. */
private const val MaxCommentLength = 280

/** `SosScreen`'s own reveal timing, the same as the sleep and meal sheets. */
private const val SheetMillis = 280
