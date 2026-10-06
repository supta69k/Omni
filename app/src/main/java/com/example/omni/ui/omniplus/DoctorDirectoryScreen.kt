package com.example.omni.ui.omniplus

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.omni.R
import com.example.omni.data.model.Doctor
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.VerifiedBadge
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DesignFrameWidth
import com.example.omni.ui.theme.FeedType
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFeedSurface
import com.example.omni.ui.theme.OmniFeedTimestamp
import com.example.omni.ui.theme.OmniLavender
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSetApply
import com.example.omni.ui.theme.OmniSetRowTitle
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.SettingsType
import com.example.omni.ui.motion.OmniMotion.pressEffect

/**
 * Doctor directory — Omni+'s doctor browsing screen.
 *
 * Matches Omni's existing card idiom (`ConversationRow` geometry): the same radius, avatar treatment,
 * row padding, text styles, and chevron. No Material3 `Scaffold`, `TopAppBar`, `Card`, `FilterChip`,
 * or `CircularProgressIndicator`. No emoji. `OmniLavender` (Omni's own colour for avatar fallbacks
 * throughout the app) for the initials circle.
 *
 * Specialty filter chips follow Omni's segment-pill idiom: `OmniSetApply` selected,
 * `OmniFeedSurface` unselected, `SettingsType.GroupLabel` text.
 *
 * No `DesignFrame` at the root — it is inside the content column to avoid nesting frames.
 */
@Composable
fun DoctorDirectoryScreen(
    state: DoctorDirectoryUiState,
    onDoctorClick: (String) -> Unit,
    onBack: () -> Unit,
    onSelectSpecialty: (String?) -> Unit = {},
) {
    DesignFrame {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = DesignFrameWidth)
                    .background(OmniBackground)
                    .statusBarsPadding(),
            ) {
                Spacer(Modifier.height(HeaderTopGap))

                // Back row — same as every other Omni sub-page
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PagePadding),
                    horizontalArrangement = Arrangement.spacedBy(BackRowGap, Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(BackButtonSize)
                            .pressEffect()
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_set_arrow_right),
                            contentDescription = "Back to Omni+",
                            modifier = Modifier
                                .size(24.dp)
                                .scale(scaleX = -1f, scaleY = 1f),
                        )
                    }

                    Text(
                        text = "Doctor Directory",
                        style = HomeType.SectionTitle,
                        color = OmniCardInk,
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.height(ListTop))

                // Specialty filter chips
                if (state.specialties.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = PagePadding),
                        horizontalArrangement = Arrangement.spacedBy(ChipGap),
                    ) {
                        item {
                            SpecialtyChip(
                                label = "All",
                                selected = state.selectedSpecialty == null,
                                onClick = { onSelectSpecialty(null) },
                            )
                        }
                        items(state.specialties) { specialty ->
                            SpecialtyChip(
                                label = specialty,
                                selected = state.selectedSpecialty == specialty,
                                onClick = { onSelectSpecialty(specialty) },
                            )
                        }
                    }
                    Spacer(Modifier.height(ListTop))
                }

                // Filtered list
                val visible = if (state.selectedSpecialty != null) {
                    state.doctors.filter { it.specialty == state.selectedSpecialty }
                } else {
                    state.doctors
                }

                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Loading doctors…",
                                style = SettingsType.RowSubtitle,
                                color = OmniFeedHint,
                            )
                        }
                    }
                    visible.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(R.drawable.ic_set_doctor),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    alpha = 0.4f,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "No doctors available yet",
                                    style = SettingsType.RowSubtitle,
                                    color = OmniFeedHint,
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                start = PagePadding,
                                end = PagePadding,
                                bottom = ContentBottomGap,
                            ),
                            verticalArrangement = Arrangement.spacedBy(RowGap),
                        ) {
                            items(visible) { doctor ->
                                DoctorCard(
                                    doctor = doctor,
                                    onClick = { onDoctorClick(doctor.uid) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One doctor row — matches Omni's `ConversationRow` geometry (radius 8, avatar gap 12,
 * row padding 14/12, `SettingsType.RowTitle`/`RowSubtitle`, chevron).
 */
@Composable
private fun DoctorCard(
    doctor: Doctor,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressEffect()
            .clip(RoundedCornerShape(RowCorner))
            .background(OmniFeedSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = RowPaddingH, vertical = RowPaddingV),
        horizontalArrangement = Arrangement.spacedBy(AvatarGap, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar — same treatment as MessagesScreen's Avatar
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .clip(RoundedCornerShape(percent = 50))
                .background(OmniLavender.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            if (doctor.photoUrl != null) {
                AsyncImage(
                    model = doctor.photoUrl,
                    contentDescription = doctor.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = doctor.name.take(1).uppercase(),
                    style = FeedType.AuthorName,
                    color = OmniFeedTimestamp,
                    maxLines = 1,
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Dr. ${doctor.name}",
                    style = SettingsType.RowTitle,
                    color = OmniSetRowTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (doctor.verified) {
                    VerifiedBadge()
                }
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = doctor.specialty,
                style = SettingsType.RowSubtitle,
                color = OmniSetApply,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (doctor.rating > 0 || doctor.consultationFee.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (doctor.rating > 0) {
                        Text(
                            text = "★ ${doctor.rating} (${doctor.reviewCount})",
                            style = FeedType.Meta12,
                            color = OmniFeedTimestamp,
                            maxLines = 1,
                        )
                    }
                    if (doctor.consultationFee.isNotBlank()) {
                        Text(
                            text = doctor.consultationFee,
                            style = FeedType.Meta12,
                            color = OmniFeedHint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // Chevron
        Image(
            painter = painterResource(R.drawable.ic_set_arrow_right),
            contentDescription = "View profile",
            modifier = Modifier.size(ChevronSize),
            alpha = 0.4f,
        )
    }
}

/**
 * Specialty filter chip — follows Omni's segment pill idiom.
 * Selected: `OmniSetApply` background; unselected: `OmniFeedSurface`.
 */
@Composable
private fun SpecialtyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .pressEffect()
            .clip(RoundedCornerShape(ChipRadius))
            .background(if (selected) OmniSetApply else OmniFeedSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = ChipPaddingH, vertical = ChipPaddingV),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = SettingsType.GroupLabel,
            color = if (selected) OmniOnInk else OmniCardInk,
            maxLines = 1,
        )
    }
}

// ---- Geometry ----------------------------------------------------------------------------------
//
// Borrowed from MessagesScreen's ConversationRow + FeedScreen's segment pill:
//   PagePadding       16.dp  — gutter
//   HeaderTopGap      12.dp  — same as every sub-page
//   BackButtonSize    40.dp  — back row circle
//   BackRowGap        10.dp  — back row spacing
//   ListTop           20.dp  — space below back row before content
//   RowCorner         8.dp   — ConversationRow card radius
//   RowPaddingH       14.dp  — card horizontal padding
//   RowPaddingV       12.dp  — card vertical padding
//   AvatarSize        44.dp  — MessagesScreen AvatarSize
//   AvatarGap         12.dp  — avatar to text gap
//   ChevronSize       20.dp  — icon size
//   RowGap            10.dp  — between cards

private val PagePadding = 16.dp
private val HeaderTopGap = 12.dp
private val BackButtonSize = 40.dp
private val BackRowGap = 10.dp
private val ListTop = 20.dp
private val RowCorner = 8.dp
private val RowPaddingH = 14.dp
private val RowPaddingV = 12.dp
private val AvatarSize = 44.dp
private val AvatarGap = 12.dp
private val ChevronSize = 20.dp
private val RowGap = 10.dp
private val ContentBottomGap = 24.dp

private val ChipGap = 8.dp
private val ChipRadius = 19.dp
private val ChipPaddingH = 14.dp
private val ChipPaddingV = 8.dp

// ---- Previews -----------------------------------------------------------------------------------

private val PreviewDoctors = listOf(
    Doctor(
        uid = "dr_1",
        name = "Ayesha Rahman",
        specialty = "Cardiology",
        bio = "Consultant cardiologist with a focus on preventive heart care and hypertension management.",
        yearsExperience = 12,
        rating = 4.9f,
        reviewCount = 214,
        languages = listOf("English", "Bangla"),
        consultationFee = "৳500 / consultation",
    ),
    Doctor(
        uid = "dr_2",
        name = "Tanvir Hasan",
        specialty = "Dermatology",
        bio = "Skin, hair and nail health, from acne care to minor procedures.",
        yearsExperience = 8,
        rating = 4.7f,
        reviewCount = 158,
        languages = listOf("English", "Bangla"),
        consultationFee = "৳400 / consultation",
    ),
    Doctor(
        uid = "dr_3",
        name = "Nusrat Jahan",
        specialty = "Nutrition",
        bio = "Clinical dietitian helping people build sustainable eating habits.",
        yearsExperience = 6,
        rating = 4.8f,
        reviewCount = 96,
        languages = listOf("English"),
        consultationFee = "৳350 / consultation",
        available = false,
    ),
)

private val PreviewDirectoryState = DoctorDirectoryUiState(
    doctors = PreviewDoctors,
    isLoading = false,
    specialties = PreviewDoctors.map { it.specialty }.distinct().sorted(),
)

@DevicePreviews
@Composable
private fun DoctorDirectoryPreview() {
    OmniTheme {
        DoctorDirectoryScreen(state = PreviewDirectoryState, onDoctorClick = {}, onBack = {}, onSelectSpecialty = {})
    }
}

@DevicePreviews
@Composable
private fun DoctorDirectoryLoadingPreview() {
    OmniTheme {
        DoctorDirectoryScreen(state = DoctorDirectoryUiState(), onDoctorClick = {}, onBack = {}, onSelectSpecialty = {})
    }
}

@DevicePreviews
@Composable
private fun DoctorDirectoryEmptyPreview() {
    OmniTheme {
        DoctorDirectoryScreen(
            state = DoctorDirectoryUiState(isLoading = false),
            onDoctorClick = {},
            onBack = {},
            onSelectSpecialty = {},
        )
    }
}
