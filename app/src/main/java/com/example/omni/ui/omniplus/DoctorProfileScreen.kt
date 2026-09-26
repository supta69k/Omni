package com.example.omni.ui.omniplus

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.omni.R
import com.example.omni.data.model.Doctor
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.VerifiedBadge
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DesignFrameWidth
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCardInk
import com.example.omni.ui.theme.OmniFeedHint
import com.example.omni.ui.theme.OmniFeedSurface
import com.example.omni.ui.theme.OmniFeedTimestamp
import com.example.omni.ui.theme.OmniLavender
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSetApply
import com.example.omni.ui.theme.OmniSetCardBody
import com.example.omni.ui.theme.OmniSetRowSubtitle
import com.example.omni.ui.theme.OmniSetRowTitle
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.SettingsType
import com.example.omni.ui.motion.OmniMotion.pressEffect

/**
 * Doctor profile — Omni+'s doctor detail screen.
 *
 * Matches Omni's existing profile/detail pages: back row, circular avatar, specialty in
 * `OmniSetApply`, stats in the settings card's body style, and a bottom CTA button
 * using the same pill-and-`pressEffect()` idiom as `HealthcareCard`. No `Scaffold`,
 * `TopAppBar`, Material3 `Button`, hardcoded hex, or emoji.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoctorProfileScreen(
    state: DoctorProfileUiState,
    onStartConsultation: () -> Unit,
    onBack: () -> Unit,
) {
    DesignFrame {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Loading…",
                            style = SettingsType.RowSubtitle,
                            color = OmniFeedHint,
                        )
                    }
                }
                state.doctor == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Doctor not found",
                            style = SettingsType.RowSubtitle,
                            color = OmniFeedHint,
                        )
                    }
                }
                else -> {
                    val doctor = state.doctor

                    // Scrollable content + fixed bottom CTA
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = DesignFrameWidth)
                            .background(OmniBackground)
                            .statusBarsPadding(),
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = PagePadding),
                        ) {
                            Spacer(Modifier.height(HeaderTopGap))

                            // Back row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                        contentDescription = "Back to directory",
                                        modifier = Modifier
                                            .size(24.dp)
                                            .scale(scaleX = -1f, scaleY = 1f),
                                    )
                                }
                            }

                            Spacer(Modifier.height(AvatarTop))

                            // Avatar — centered, 96dp
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
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
                                        style = HomeType.HeroTitle,
                                        color = OmniFeedTimestamp,
                                        maxLines = 1,
                                    )
                                }
                            }

                            Spacer(Modifier.height(AvatarBottom))

                            // Name + verified badge
                            Row(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Dr. ${doctor.name}",
                                    style = HomeType.SectionTitle,
                                    color = OmniCardInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (doctor.verified) {
                                    VerifiedBadge(size = 18.dp)
                                }
                            }

                            // Specialty
                            Text(
                                text = doctor.specialty,
                                style = SettingsType.RowSubtitle,
                                color = OmniSetApply,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 4.dp),
                            )

                            Spacer(Modifier.height(StatsTop))

                            // Stats row — matches Omni's profile/metadata row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                if (doctor.yearsExperience > 0) {
                                    ProfileStat(label = "Experience", value = "${doctor.yearsExperience} yrs")
                                }
                                if (doctor.rating > 0) {
                                    ProfileStat(label = "Rating", value = "★ ${doctor.rating}")
                                }
                                if (doctor.reviewCount > 0) {
                                    ProfileStat(label = "Reviews", value = "${doctor.reviewCount}")
                                }
                            }

                            Spacer(Modifier.height(SectionTop))

                            // Bio
                            if (doctor.bio.isNotBlank()) {
                                SectionHeading("About")
                                Text(
                                    text = doctor.bio,
                                    style = SettingsType.CardBody,
                                    color = OmniSetCardBody,
                                )
                                Spacer(Modifier.height(SectionTop))
                            }

                            // Languages
                            if (doctor.languages.isNotEmpty()) {
                                SectionHeading("Languages")
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    doctor.languages.forEach { lang ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(LangChipRadius))
                                                .background(OmniFeedSurface)
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                        ) {
                                            Text(
                                                text = lang,
                                                style = SettingsType.GroupLabel,
                                                color = OmniCardInk,
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(SectionTop))
                            }

                            // Availability
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = SpacerBottom),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(AvailabilityDotSize)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(if (doctor.available) OmniSetApply else OmniFeedHint),
                                )
                                Text(
                                    text = if (doctor.available) "Available now" else "Currently unavailable",
                                    style = SettingsType.RowSubtitle,
                                    color = if (doctor.available) OmniSetApply else OmniFeedHint,
                                )
                            }
                        }

                        // Fixed bottom CTA — matches HealthcareCard's "Apply for Verification" pill
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(OmniBackground)
                                .navigationBarsPadding()
                                .padding(horizontal = PagePadding, vertical = 12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressEffect()
                                    .clip(RoundedCornerShape(CtaRadius))
                                    .background(OmniSetApply)
                                    .clickable(onClick = onStartConsultation)
                                    .padding(vertical = CtaPaddingV),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Start Consultation",
                                    style = SettingsType.CardButton,
                                    color = OmniOnInk,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = HomeType.CardValue,
            color = OmniCardInk,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = SettingsType.GroupLabel,
            color = OmniSetRowSubtitle,
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = SettingsType.RowTitle,
        color = OmniSetRowTitle,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

// ---- Geometry ----------------------------------------------------------------------------------
//
// Borrowed from ProfileScreen, MessagesScreen, and HealthcareCard:
//   PagePadding      16.dp  — gutter
//   HeaderTopGap     12.dp  — back row top gap
//   BackButtonSize   40.dp  — back circle
//   BackRowGap       10.dp  — back row spacing
//   AvatarSize       96.dp  — profile avatar
//   AvatarTop        16.dp  — below back row
//   AvatarBottom     16.dp  — below avatar
//   StatsTop         24.dp  — below name/specialty
//   SectionTop       20.dp  — between sections
//   LangChipRadius   50.dp  — pill
//   CtaRadius        23.dp  — HealthcareCard CTA pill radius
//   CtaPaddingV      10.dp  — CTA vertical padding
//   SpacerBottom     16.dp  — breathing room at scroll end
//   AvailabilityDotSize 8.dp

private val PagePadding = 16.dp
private val HeaderTopGap = 12.dp
private val BackButtonSize = 40.dp
private val BackRowGap = 10.dp
private val AvatarSize = 96.dp
private val AvatarTop = 16.dp
private val AvatarBottom = 16.dp
private val StatsTop = 24.dp
private val SectionTop = 20.dp
private val LangChipRadius = 50.dp
private val CtaRadius = 23.dp
private val CtaPaddingV = 10.dp
private val SpacerBottom = 16.dp
private val AvailabilityDotSize = 8.dp

// ---- Previews -----------------------------------------------------------------------------------

private val PreviewDoctor = Doctor(
    uid = "dr_1",
    name = "Ayesha Rahman",
    specialty = "Cardiology",
    bio = "Consultant cardiologist with twelve years of clinical practice. Focused on preventive " +
        "heart care, hypertension management and lifestyle-first treatment plans.",
    yearsExperience = 12,
    rating = 4.9f,
    reviewCount = 214,
    languages = listOf("English", "Bangla", "Hindi"),
    consultationFee = "৳500 / consultation",
)

@DevicePreviews
@Composable
private fun DoctorProfilePreview() {
    OmniTheme {
        DoctorProfileScreen(
            state = DoctorProfileUiState(doctor = PreviewDoctor, isLoading = false),
            onStartConsultation = {},
            onBack = {},
        )
    }
}

@DevicePreviews
@Composable
private fun DoctorProfileLoadingPreview() {
    OmniTheme {
        DoctorProfileScreen(state = DoctorProfileUiState(), onStartConsultation = {}, onBack = {})
    }
}
