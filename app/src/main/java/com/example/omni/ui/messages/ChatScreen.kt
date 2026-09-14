package com.example.omni.ui.messages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.Message
import com.example.omni.data.model.relativeTimeOf
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DesignFrameWidth
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFeedTimestamp
import com.example.omni.ui.theme.OmniFieldSurface
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniPlaceholder
import com.example.omni.ui.theme.OmniSetCardSurface
import com.example.omni.ui.theme.OmniSetRowSubtitle
import com.example.omni.ui.theme.OmniTheme

/**
 * One conversation (BACKEND_PLAN §11 Phase 11).
 *
 * **This frame does not exist in Figma.** `UI_ARCHITECTURE.md` §6 rule 11 therefore applies: the back row
 * and the avatar are [MessagesScreen]'s, the bubbles are the healthcare card's #F5F5F5 tray and the auth
 * pages' ink at the same radius, the body type is the feed's `PostBody`, the timestamps are the feed's
 * #8D84F9 `Meta12`, and the input is the post composer's field with the SOS page's circular button on it.
 *
 * There is no bottom bar, like the guide detail and the composer: this is a page *inside* Messages, and
 * both the back arrow and the system back return to the thread list rather than leaving the tab.
 *
 * The column scrolls rather than lazily recycling, which is safe because the thread is capped at
 * [com.example.omni.data.model.MessagePageSize] messages by the query that feeds it — there is no
 * unbounded list here to be lazy about.
 */
@Composable
fun ChatScreen(
    state: OpenChatState = PreviewChat,
    onBack: () -> Unit = {},
    onDraftChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

    val scroll = rememberLazyListState()

    // A chat opens at the bottom and stays there as messages arrive — including the one just sent, which
    // Firestore's local cache appends before the round-trip. Keyed on the count rather than the list so a
    // re-render that changes nothing does not fight a user who has scrolled up to read.
    //
    // Asking for the last index rather than a pixel offset: the list clamps the request to its own
    // maximum, so "put the newest message at the top" resolves to "show the end of the thread", which
    // is the same place `maxValue` used to mean and does not need the whole thread measured to find.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            scroll.animateScrollToItem(state.messages.lastIndex)
        }
    }

    DesignFrame {
        // A reading-and-writing surface: cap the page at the artboard width and centre it. Portrait
        // stays byte-identical (the frame is already 415 wide, so the cap and the centring Box are
        // both inert); landscape centres the chat in the wide viewport instead of leaving the messages
        // stretched to the new long edge. No orientation branch needed.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = DesignFrameWidth)
                .background(OmniBackground)
                .statusBarsPadding()
                .imePadding(),
        ) {
            Spacer(Modifier.height(HeaderTopGap))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PagePadding),
                horizontalArrangement = Arrangement.spacedBy(BackRowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(BackButtonSize)
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_set_arrow_right),
                        contentDescription = "Back to messages",
                        modifier = Modifier
                            .size(24.dp)
                            .scale(scaleX = -1f, scaleY = 1f),
                    )
                }

                Avatar(
                    name = state.otherName,
                    photoUrl = state.otherPhotoUrl,
                    size = HeaderAvatarSize,
                )

                Text(
                    text = state.otherName,
                    style = HomeType.SectionTitle,
                    color = OmniCardInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(ThreadTop))

            LazyColumn(
                state = scroll,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = PagePadding,
                    end = PagePadding,
                    bottom = ThreadBottomGap,
                ),
                verticalArrangement = Arrangement.spacedBy(BubbleGap),
            ) {
                if (state.messages.isEmpty()) {
                    item(key = EmptyThreadKey) {
                        Text(
                            text = "Say hello. Describe what's wrong in your own words — this thread is " +
                                "only between the two of you.",
                            style = HomeType.CardFootnoteWrapped,
                            color = OmniSetRowSubtitle,
                        )
                    }
                }

                // Keyed on the message id, which is what lets the list survive the one update a chat
                // gets constantly: a new message on the end. Without a key every bubble above it is
                // considered new and re-measured; with one, only the arrival is.
                items(
                    items = state.messages,
                    key = { it.id },
                    contentType = { BubbleKey },
                ) { message ->
                    MessageBubble(message = message, mine = message.senderId == state.selfUid)
                }
            }

            ChatInput(
                draft = state.draft,
                canSend = state.canSend,
                onDraftChange = onDraftChange,
                onSend = onSend,
            )
        }
        }
    }
}

/**
 * One message.
 *
 * The sender is carried by side *and* colour: a bubble on the right in ink is mine, one on the left in
 * #F5F5F5 is theirs. Side alone would be the only signal in a screenshot with the colour stripped, and
 * colour alone would be the only one for someone who cannot tell the two apart.
 *
 * The width is capped rather than left to wrap at the gutter, so a one-word reply is a small bubble and a
 * paragraph still leaves the other side of the column visibly empty — which is what makes the thread
 * readable as a conversation rather than as a list.
 */
@Composable
private fun MessageBubble(
    message: Message,
    mine: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = BubbleMaxWidth)
                .clip(RoundedCornerShape(BubbleCorner))
                .background(if (mine) OmniInk else OmniSetCardSurface)
                .padding(horizontal = BubblePaddingH, vertical = BubblePaddingV),
        ) {
            Text(
                text = message.text,
                style = FeedType.PostBody,
                color = if (mine) OmniOnInk else OmniCardInk,
            )
        }

        Spacer(Modifier.height(StampGap))

        // "just now" while the server timestamp is still pending, which is exactly what a message that
        // has left the device but not yet reached Firestore is.
        Text(
            text = relativeTimeOf(message.createdAt),
            style = FeedType.Meta12,
            color = OmniFeedTimestamp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The composer — the post composer's #F5F5F5 field with a circular send button beside it.
 *
 * The field grows to [InputMaxLines] and then scrolls: a symptom description is a paragraph, and forcing
 * it onto one line would hide most of what the user is about to send. The button is drawn disabled rather
 * than hidden, because it is the one control on this page and a page whose only button vanishes when the
 * field is empty reads as broken (`UI_ARCHITECTURE.md` §6 rule 10 is about *dead* controls; this one
 * becomes live the moment a character is typed, and says so by changing colour).
 */
@Composable
private fun ChatInput(
    draft: String,
    canSend: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = PagePadding, vertical = InputPaddingV),
        horizontalArrangement = Arrangement.spacedBy(InputGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(InputCorner))
                .background(OmniFieldSurface)
                .padding(horizontal = InputPaddingH, vertical = InputTextPaddingV),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                maxLines = InputMaxLines,
                textStyle = FeedType.PostBody.copy(color = OmniInk),
                cursorBrush = SolidColor(OmniInk),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (draft.isEmpty()) {
                            Text(
                                text = "Write a message…",
                                style = FeedType.PostBody,
                                color = OmniPlaceholder,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Box(
            modifier = Modifier
                .size(SendButtonSize)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (canSend) OmniInk else OmniFeedHint)
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_set_arrow_right),
                contentDescription = "Send",
                modifier = Modifier.size(SendIconSize),
            )
        }
    }
}

// ---- Geometry ----------------------------------------------------------------------------------
//
// No Figma frame. The gutter and the back row are `SavedEmergenciesScreen`'s to the dp, the field is the
// post composer's, the send button is the SOS sheet's circular control, and the bubble's radius is the
// hero card's — the one rounding this app uses for something that holds prose.

private val PagePadding = 16.dp
private val HeaderTopGap = 12.dp
private val BackButtonSize = 40.dp
private val BackRowGap = 10.dp
private val HeaderAvatarSize = 36.dp
private val ThreadTop = 16.dp
private val BubbleGap = 12.dp
private val BubbleCorner = 14.dp
private val BubblePaddingH = 14.dp
private val BubblePaddingV = 10.dp
private val StampGap = 4.dp

/** Two thirds of the 415dp artboard's content width, so the empty side of the column stays visible. */
private val BubbleMaxWidth = 250.dp

/** Clears the input row when the thread is scrolled to the bottom. */
private val ThreadBottomGap = 8.dp

/** The thread list's item identities — see the `key` note at the `items` call. */
private const val BubbleKey = "bubble"
private const val EmptyThreadKey = "empty-thread"

private val InputGap = 10.dp
private val InputCorner = 20.dp
private val InputPaddingH = 14.dp
private val InputPaddingV = 10.dp
private val InputTextPaddingV = 12.dp
private val SendButtonSize = 44.dp
private val SendIconSize = 22.dp

/** Five lines of `PostBody` is about a paragraph; past that the field scrolls rather than eating the thread. */
private const val InputMaxLines = 5

/** What the preview renders, and the screen's own default — both sides of a real consultation. */
val PreviewChat = OpenChatState(
    conversationId = "preview-thread",
    selfUid = "self",
    otherUid = "dr-rahman",
    otherName = "Dr. Sadia Rahman",
    messages = listOf(
        Message(
            id = "m0",
            senderId = "self",
            text = "Good evening doctor. I've had a fever since last night, around 101.",
            createdAt = System.currentTimeMillis() - 22 * 60_000L,
        ),
        Message(
            id = "m1",
            senderId = "dr-rahman",
            text = "Take the paracetamol with food, and send me a photo if the rash spreads.",
            createdAt = System.currentTimeMillis() - 9 * 60_000L,
        ),
    ),
)

@DevicePreviews
@Composable
private fun ChatScreenPreview() {
    OmniTheme { ChatScreen() }
}

@DevicePreviews
@Composable
private fun ChatScreenEmptyPreview() {
    OmniTheme {
        ChatScreen(
            state = OpenChatState(
                conversationId = "preview-thread",
                selfUid = "self",
                otherUid = "tanvir-karim",
                otherName = "Tanvir Karim",
                draft = "Hi, I'd like some help planning meals.",
            ),
        )
    }
}
