package com.example.omni.ui.omniplus

import androidx.activity.compose.BackHandler
import com.example.omni.ui.messages.ChatScreen
import com.example.omni.ui.messages.OpenChatState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Doctor consultation — `ChatScreen` reused verbatim.
 *
 * `UI_ARCHITECTURE.md` §6 rule 11 and the Omni+ design brief both say the consultation must be
 * the existing chat design, not a second one. This screen maps its own state onto [OpenChatState]
 * and calls through: the bubbles, the input, the header row, and the empty state are all in one
 * place, so they cannot drift apart.
 *
 * The only differences from a normal message thread are cosmetic:
 * - the subtitle line reads "Omni+ Consultation"
 * - the empty thread has a health-specific prompt
 * - the back arrow goes to the doctor directory
 *
 * No `DesignFrame` here — it is inside [ChatScreen].
 * No second input field — [ChatScreen.ChatInput] is used.
 * No second bubble component — [ChatScreen.MessageBubble] is used.
 */
@Composable
fun DoctorConsultationScreen(
    viewModel: DoctorConsultationViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val chatState = OpenChatState(
        conversationId = state.conversationId ?: "",
        selfUid = state.selfUid,
        otherUid = "", // not used in MessageBubble (only selfUid matters for bubble side)
        otherName = state.otherName,
        otherPhotoUrl = state.otherPhotoUrl,
        messages = state.messages,
        draft = state.inputText,
    )

    ChatScreen(
        state = chatState,
        onBack = onBack,
        onDraftChange = { viewModel.updateInput(it) },
        onSend = {
            if (state.inputText.isNotBlank()) {
                viewModel.send(state.inputText)
            }
        },
        subtitle = "Omni+ Consultation",
        backContentDescription = "Back to doctor directory",
        emptyMessage = "Start your consultation. Describe your health question in your own " +
            "words — a licensed doctor will respond shortly.",
    )
}
