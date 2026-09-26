package com.example.omni.ui.omniplus

import androidx.activity.compose.BackHandler
import com.example.omni.data.model.Message
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.messages.ChatScreen
import com.example.omni.ui.messages.OpenChatState
import com.example.omni.ui.theme.OmniTheme
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
        // A consultation is always with a verified doctor, so the header carries the badge.
        otherVerified = true,
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

// ---- Previews -----------------------------------------------------------------------------------

private val PreviewMessages = listOf(
    Message(
        id = "m1",
        senderId = "patient",
        text = "Hi doctor, I've been getting chest tightness when I climb stairs. Should I be worried?",
        createdAt = System.currentTimeMillis() - 26 * 60_000L,
    ),
    Message(
        id = "m2",
        senderId = "dr_1",
        text = "Hello. Tightness on exertion is worth checking. Any pain at rest, or shortness of " +
            "breath when lying down?",
        createdAt = System.currentTimeMillis() - 18 * 60_000L,
    ),
    Message(
        id = "m3",
        senderId = "patient",
        text = "No, only when I exert myself. It goes away after a couple of minutes of rest.",
        createdAt = System.currentTimeMillis() - 12 * 60_000L,
    ),
)

private val ConsultationEmptyMessage = "Start your consultation. Describe your health question " +
    "in your own words — a licensed doctor will respond shortly."

/**
 * The screen is a thin state-mapping wrapper around [ChatScreen], so the previews drive ChatScreen
 * with the consultation's parameters — exactly what the wrapper passes at runtime. `selfUid` is the
 * patient, so the patient's messages render on the sender's side of the thread.
 */
@DevicePreviews
@Composable
private fun DoctorConsultationPreview() {
    OmniTheme {
        ChatScreen(
            state = OpenChatState(
                conversationId = "consultation_patient_dr_1",
                selfUid = "patient",
                otherUid = "dr_1",
                otherName = "Dr. Ayesha Rahman",
                messages = PreviewMessages,
            ),
            subtitle = "Omni+ Consultation",
            backContentDescription = "Back to doctor directory",
            emptyMessage = ConsultationEmptyMessage,
        )
    }
}

@DevicePreviews
@Composable
private fun DoctorConsultationEmptyPreview() {
    OmniTheme {
        ChatScreen(
            state = OpenChatState(
                conversationId = "consultation_patient_dr_1",
                selfUid = "patient",
                otherUid = "dr_1",
                otherName = "Dr. Ayesha Rahman",
            ),
            subtitle = "Omni+ Consultation",
            backContentDescription = "Back to doctor directory",
            emptyMessage = ConsultationEmptyMessage,
        )
    }
}
