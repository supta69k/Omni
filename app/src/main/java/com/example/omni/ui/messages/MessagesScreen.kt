package com.example.omni.ui.messages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.data.model.Conversation
import com.example.omni.data.model.relativeTimeOf
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.AdaptiveColumnGrid
import com.example.omni.ui.components.OmniTabScaffold
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFeedSurface
import com.example.omni.ui.theme.OmniFeedTimestamp
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSetCardSurface
import com.example.omni.ui.theme.OmniSetGroupLabel
import com.example.omni.ui.theme.OmniSetRowSubtitle
import com.example.omni.ui.theme.OmniSetRowTitle
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.SettingsType

/**
 * The thread list — the Messages tab (BACKEND_PLAN §11 Phase 11).
 *
 * **This frame does not exist in Figma.** `UI_ARCHITECTURE.md` §6 rule 11 therefore applies, and every
 * part below is borrowed rather than invented: the back row is `SavedEmergenciesScreen`'s, the rows are
 * the healthcare card's #F5F5F5 tray at radius 8, the avatar is the feed post's circle on the feed's own
 * grey with the author's initial as the stand-in, the two lines inside a row are `SettingScreen`'s
 * [SettingsType] pair, the timestamp is the feed's #8D84F9 `Meta12`, and the unread pill is the auth
 * pages' ink commitment shrunk to a count. The "New message" link is the settings groups' `Edit` link.
 *
 * It keeps the bottom bar, because it *is* a tab.
 *
 * Who you may write to is a product decision, not a UI one: the picker lists the people this user
 * follows *and* the verified directory, which is BACKEND_PLAN §12's answer plus the social half the
 * feed needs. There is still no search field and no way to type a uid — a stranger is reached by
 * opening their profile from search and tapping Message there, which is the same write one screen over.
 */
@Composable
fun MessagesScreen(
    state: MessagesUiState = PreviewMessages,
    onBack: () -> Unit = {},
    onNavigate: (OmniNavItem) -> Unit = {},
    onOpen: (Conversation) -> Unit = {},
    onTogglePicker: () -> Unit = {},
    onStartWith: (ProfessionalRowState) -> Unit = {},
) {
    BackHandler(onBack = onBack)

    DesignFrame {
        OmniTabScaffold(
            selected = OmniNavItem.Messages,
            onNavigate = onNavigate,
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = PagePadding),
            ) {
                Spacer(Modifier.height(HeaderTopGap))

                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            contentDescription = "Back",
                            modifier = Modifier
                                .size(24.dp)
                                .scale(scaleX = -1f, scaleY = 1f),
                        )
                    }

                    Spacer(Modifier.width(BackRowGap))

                    // Weighted so the link keeps its width and the title is what gives way, the same
                    // reasoning as the notifications page's header.
                    Text(
                        text = "Messages",
                        style = HomeType.SectionTitle,
                        color = OmniCardInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    Text(
                        text = if (state.pickerOpen) "Cancel" else "New message",
                        style = SettingsType.RowAction,
                        color = if (state.pickerOpen) OmniSetRowSubtitle else OmniSetRowTitle,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.clickable(onClick = onTogglePicker),
                    )
                }

                if (state.pickerOpen) {
                    Spacer(Modifier.height(PickerTop))
                    NewMessagePicker(
                        people = state.people,
                        professionals = state.professionals,
                        onStartWith = onStartWith,
                    )
                }

                Spacer(Modifier.height(ListTop))

                when {
                    state.loading -> Text(
                        text = "Loading your messages…",
                        style = SettingsType.RowSubtitle,
                        color = OmniFeedHint,
                    )

                    state.conversations.isEmpty() -> Text(
                        text = "No conversations yet. Tap “New message” to write to someone you follow, " +
                            "or to ask a verified doctor or nutritionist something — they can see your " +
                            "question, not your health data.",
                        style = HomeType.CardFootnoteWrapped,
                        color = OmniSetRowSubtitle,
                    )

                    else -> AdaptiveColumnGrid(
                        items = state.conversations,
                        verticalSpacing = RowGap,
                    ) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            onClick = { onOpen(conversation) },
                        )
                    }
                }

                Spacer(Modifier.height(OmniNavHeight + OmniNavBottomGap + ContentBottomGap))
            }
        }
        }
    }
}

/**
 * One thread.
 *
 * The preview line is prefixed "You: " when this user sent it, which is worked out from [lastSenderId]
 * against [Conversation.otherUid] rather than from the signed-in uid — the row does not need to know who
 * is signed in to know who is *not* the other person.
 *
 * Unread is carried twice, as on the notifications page: the tray is filled *and* a count is drawn, so
 * the row still reads on a greyscale screen.
 */
@Composable
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
) {
    val unread = conversation.unread > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RowCorner))
            .background(if (unread) OmniSetCardSurface else OmniFeedSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = RowPaddingH, vertical = RowPaddingV),
        horizontalArrangement = Arrangement.spacedBy(AvatarGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            name = conversation.otherName,
            photoUrl = conversation.otherPhotoUrl,
            size = AvatarSize,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.otherName,
                style = SettingsType.RowTitle,
                color = OmniSetRowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(TitleGap))

            Text(
                text = conversation.previewLine(),
                style = SettingsType.RowSubtitle,
                color = OmniSetRowSubtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = relativeTimeOf(conversation.lastMessageAt),
                style = FeedType.Meta12,
                color = OmniFeedTimestamp,
                maxLines = 1,
                softWrap = false,
            )

            if (unread) {
                Spacer(Modifier.height(TitleGap))
                Box(
                    modifier = Modifier
                        .height(UnreadPillHeight)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(OmniInk)
                        .padding(horizontal = UnreadPillPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (conversation.unread > MaxShownUnread) "$MaxShownUnread+"
                        else conversation.unread.toString(),
                        style = FeedType.Meta12,
                        color = OmniOnInk,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * The "new message" list — two groups, one screen.
 *
 * **People you follow** is the social half: anyone this user follows can be written to, which is what
 * makes the Messages tab reachable from the feed rather than a healthcare-only cul-de-sac. **Verified
 * professionals** is the healthcare half, unchanged — BACKEND_PLAN §12's own answer, and the reason the
 * group label below is word-for-word what it always was.
 *
 * Each group carries its own empty line, and neither of them is a dead end: they say what to do next.
 *
 * A whole row is tappable because the tap does one unambiguous thing (§6 rule 10): it opens the thread
 * with that person, which is idempotent, so a mis-tap costs a back gesture rather than a message.
 */
@Composable
private fun NewMessagePicker(
    people: List<ProfessionalRowState>,
    professionals: List<ProfessionalRowState>,
    onStartWith: (ProfessionalRowState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PickerGap)) {
        PickerGroup(
            label = "People you follow",
            rows = people,
            empty = "You're not following anyone yet. Follow someone from the feed or from search and " +
                "they'll appear here.",
            onStartWith = onStartWith,
        )

        Spacer(Modifier.height(PickerGroupGap))

        PickerGroup(
            label = "Verified professionals",
            rows = professionals,
            empty = "No verified doctors or nutritionists have been reviewed yet. They'll appear here " +
                "when they are.",
            onStartWith = onStartWith,
        )
    }
}

@Composable
private fun PickerGroup(
    label: String,
    rows: List<ProfessionalRowState>,
    empty: String,
    onStartWith: (ProfessionalRowState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PickerGap)) {
        Text(
            text = label,
            style = SettingsType.GroupLabel,
            color = OmniSetGroupLabel,
            maxLines = 1,
            softWrap = false,
        )

        if (rows.isEmpty()) {
            Text(
                text = empty,
                style = HomeType.CardFootnoteWrapped,
                color = OmniSetRowSubtitle,
            )
            return@Column
        }

        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RowCorner))
                    .background(OmniSetCardSurface)
                    .clickable { onStartWith(row) }
                    .padding(horizontal = RowPaddingH, vertical = RowPaddingV),
                horizontalArrangement = Arrangement.spacedBy(AvatarGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    name = row.name,
                    photoUrl = row.photoUrl,
                    size = PickerAvatarSize,
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.name,
                        style = SettingsType.RowTitle,
                        color = OmniSetRowTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = row.discipline,
                        style = SettingsType.RowSubtitle,
                        color = OmniSetRowSubtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Image(
                    painter = painterResource(R.drawable.ic_set_arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(ChevronSize),
                )
            }
        }
    }
}

/**
 * The feed post's avatar, at whatever size the caller needs.
 *
 * The initial on the feed's grey is the stand-in that cannot fail to load, which matters more here than
 * on the feed: a thread list with no photos is the normal state of a term project's database.
 */
@Composable
internal fun Avatar(
    name: String,
    photoUrl: String?,
    size: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 50))
            .background(OmniFeedSurface),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl != null) {
            coil3.compose.AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = name.take(1).uppercase(),
                style = FeedType.AuthorName,
                color = OmniFeedTimestamp,
                maxLines = 1,
            )
        }
    }
}

/**
 * The second line of a row.
 *
 * A thread that has just been created has no last message at all, and rendering an empty line would make
 * the row look broken rather than new.
 */
private fun Conversation.previewLine(): String = when {
    lastMessage.isBlank() -> "No messages yet"
    lastSenderId.isNotEmpty() && lastSenderId != otherUid -> "You: $lastMessage"
    else -> lastMessage
}

// ---- Geometry ----------------------------------------------------------------------------------
//
// No Figma frame. The gutter, the back row and the tray are `SavedEmergenciesScreen`'s to the dp; the
// avatar is the feed post's circle at the size a two-line row can hold; the unread pill is the height of
// the feed's own action chips.

private val PagePadding = 16.dp
private val HeaderTopGap = 12.dp
private val BackButtonSize = 40.dp
private val BackRowGap = 10.dp
private val PickerTop = 20.dp
private val PickerGap = 10.dp
/** Enough that the two group labels read as two groups rather than one long list. */
private val PickerGroupGap = 10.dp
private val ListTop = 20.dp
private val RowGap = 10.dp
private val RowCorner = 8.dp
private val RowPaddingH = 14.dp
private val RowPaddingV = 12.dp
private val AvatarSize = 44.dp
private val PickerAvatarSize = 38.dp
private val AvatarGap = 12.dp
private val ChevronSize = 20.dp
private val TitleGap = 2.dp
private val UnreadPillHeight = 18.dp
private val UnreadPillPadding = 7.dp

/** Two digits is as wide as the pill goes before it starts pushing the name's column. */
private const val MaxShownUnread = 99

/** Clears the floating bar, matching every other page that keeps one. */
private val ContentBottomGap = 24.dp

/**
 * What the previews render, and the screen's own default — one unread thread and one read, because the
 * difference between those two rows is the whole design.
 */
val PreviewMessages = MessagesUiState(
    conversations = listOf(
        Conversation(
            id = "preview-0",
            otherUid = "dr-rahman",
            otherName = "Dr. Sadia Rahman",
            lastMessage = "Take the paracetamol with food, and send me a photo if the rash spreads.",
            lastMessageAt = System.currentTimeMillis() - 9 * 60_000L,
            lastSenderId = "dr-rahman",
            unread = 2,
        ),
        Conversation(
            id = "preview-1",
            otherUid = "nut-karim",
            otherName = "Tanvir Karim",
            lastMessage = "Thanks! I'll log the meals this week and send them over.",
            lastMessageAt = System.currentTimeMillis() - 3 * 24 * 60 * 60_000L,
            lastSenderId = "self",
        ),
    ),
    loading = false,
)

/** The picker's own preview data — the same two accounts `PreviewUserRepository` seeds. */
private val PreviewProfessionalRows = listOf(
    ProfessionalRowState("dr-rahman", "Dr. Sadia Rahman", "Doctor"),
    ProfessionalRowState("nut-karim", "Tanvir Karim", "Nutritionist"),
)

/** Two ordinary accounts, so the preview shows the social group beside the healthcare one. */
private val PreviewFollowedRows = listOf(
    ProfessionalRowState("uid-ben", "Ben Ahmed", "You follow them"),
    ProfessionalRowState("uid-carla", "Carla Nunes", "You follow them"),
)

@DevicePreviews
@Composable
private fun MessagesScreenPreview() {
    OmniTheme { MessagesScreen() }
}

@DevicePreviews
@Composable
private fun MessagesScreenPickerPreview() {
    OmniTheme {
        MessagesScreen(
            state = PreviewMessages.copy(
                pickerOpen = true,
                people = PreviewFollowedRows,
                professionals = PreviewProfessionalRows,
            ),
        )
    }
}

/** Both groups empty — the state that used to be a dead end, and the one worth looking at. */
@DevicePreviews
@Composable
private fun MessagesScreenPickerEmptyPreview() {
    OmniTheme {
        MessagesScreen(state = MessagesUiState(loading = false, pickerOpen = true))
    }
}

@DevicePreviews
@Composable
private fun MessagesScreenEmptyPreview() {
    OmniTheme { MessagesScreen(state = MessagesUiState(loading = false)) }
}
