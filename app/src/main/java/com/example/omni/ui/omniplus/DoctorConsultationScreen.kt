package com.example.omni.ui.omniplus

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.omni.data.model.Message
import com.example.omni.data.model.relativeTimeOf
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFieldSurface
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniPlaceholder
import com.example.omni.ui.theme.OmniTheme

private val PremiumPurple = Color(0xFF8B5CF6)
private val BubbleGap = 8.dp
private val ThreadBottomGap = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorConsultationScreen(
    state: DoctorConsultationUiState,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val scroll = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            scroll.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.otherPhotoUrl != null) {
                            AsyncImage(
                                model = state.otherPhotoUrl,
                                contentDescription = state.otherName,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = state.otherName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = OmniInk,
                            )
                            Text(
                                text = "Omni+ Consultation",
                                fontSize = 12.sp,
                                color = PremiumPurple,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OmniInk,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground)
                .padding(padding)
                .imePadding(),
        ) {
            LazyColumn(
                state = scroll,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(BubbleGap),
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Text(
                            text = "Start your consultation. Describe your health question in your own words — " +
                                "a licensed doctor will respond shortly.",
                            color = OmniPlaceholder,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }

                items(
                    items = state.messages,
                    key = { it.id },
                ) { message ->
                    ConsultationBubble(
                        message = message,
                        isMine = false, // TODO: check against currentUid when available
                    )
                }
            }

            ConsultationInput(
                text = state.inputText,
                isLoading = state.isSending,
                onTextChange = { /* updateInput handled by parent */ },
                onSend = {
                    if (state.inputText.isNotBlank()) {
                        onSend(state.inputText)
                    }
                },
            )
        }
    }
}

@Composable
private fun ConsultationBubble(
    message: Message,
    isMine: Boolean,
) {
    val bubbleColor = if (isMine) OmniInk else OmniFieldSurface
    val textColor = if (isMine) OmniOnInk else OmniCardInk
    val boxAlignment = if (isMine) Alignment.BottomEnd else Alignment.BottomStart

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = boxAlignment,
    ) {
        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMine) 16.dp else 4.dp,
                            bottomEnd = if (isMine) 4.dp else 16.dp,
                        )
                    )
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.text,
                    color = textColor,
                    fontSize = 15.sp,
                )
            }

            Text(
                text = relativeTimeOf(message.createdAt),
                fontSize = 11.sp,
                color = OmniPlaceholder,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun ConsultationInput(
    text: String,
    isLoading: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(OmniFieldSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = OmniInk,
                    fontSize = 15.sp,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(PremiumPurple),
                singleLine = false,
                maxLines = 5,
                decorationBox = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                text = "Ask your question...",
                                color = OmniPlaceholder,
                                fontSize = 15.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        Spacer(modifier = Modifier.size(12.dp))

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (text.isNotBlank()) PremiumPurple else OmniFieldSurface)
                .clickable(enabled = text.isNotBlank() && !isLoading, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank()) Color.White else OmniPlaceholder,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
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

private val PreviewConsultationState = DoctorConsultationUiState(
    conversationId = "consultation_patient_dr_1",
    messages = PreviewMessages,
    otherName = "Dr. Ayesha Rahman",
    isLoading = false,
)

@DevicePreviews
@Composable
private fun DoctorConsultationPreview() {
    OmniTheme {
        DoctorConsultationScreen(
            state = PreviewConsultationState,
            onSend = {},
            onBack = {},
        )
    }
}

@DevicePreviews
@Composable
private fun DoctorConsultationEmptyPreview() {
    OmniTheme {
        DoctorConsultationScreen(
            state = DoctorConsultationUiState(otherName = "Dr. Ayesha Rahman", isLoading = false),
            onSend = {},
            onBack = {},
        )
    }
}
