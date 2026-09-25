package com.example.omni.ui.omniplus

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.omni.data.model.Doctor
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniLavender
import com.example.omni.ui.theme.OmniTheme

private val PremiumPurple = Color(0xFF8B5CF6)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DoctorProfileScreen(
    state: DoctorProfileUiState,
    onStartConsultation: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Doctor Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = OmniInk,
                ),
            )
        },
        bottomBar = {
            if (state.doctor != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(16.dp),
                ) {
                    Button(
                        onClick = onStartConsultation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumPurple),
                    ) {
                        Text(
                            text = "Start Consultation",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = PremiumPurple)
            }
        } else if (state.doctor == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Doctor not found", color = Color(0xFF6C6C6C))
            }
        } else {
            val doctor = state.doctor
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Avatar
                if (doctor.photoUrl != null) {
                    AsyncImage(
                        model = doctor.photoUrl,
                        contentDescription = doctor.name,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(OmniLavender.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = doctor.name.firstOrNull()?.uppercase() ?: "?",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = OmniLavender,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Dr. ${doctor.name}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = OmniInk,
                    )
                    if (doctor.verified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("✓", color = Color(0xFF16A34A), fontSize = 18.sp)
                    }
                }

                Text(
                    text = doctor.specialty,
                    fontSize = 16.sp,
                    color = PremiumPurple,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatItem("Experience", "${doctor.yearsExperience} years")
                    if (doctor.rating > 0) {
                        StatItem("Rating", "⭐ ${doctor.rating}")
                    }
                    if (doctor.reviewCount > 0) {
                        StatItem("Reviews", "${doctor.reviewCount}")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bio
                if (doctor.bio.isNotBlank()) {
                    Text(
                        text = "About",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = OmniInk,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = doctor.bio,
                        fontSize = 15.sp,
                        color = Color(0xFF6C6C6C),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Languages
                if (doctor.languages.isNotEmpty()) {
                    Text(
                        text = "Languages",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = OmniInk,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        doctor.languages.forEach { lang ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFF3F4F6))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(lang, fontSize = 14.sp, color = OmniInk)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Availability
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (doctor.available) Color(0xFF16A34A) else Color(0xFFDC2626)),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (doctor.available) "Available now" else "Currently unavailable",
                        fontSize = 14.sp,
                        color = if (doctor.available) Color(0xFF16A34A) else Color(0xFFDC2626),
                    )
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OmniInk)
        Text(text = label, fontSize = 13.sp, color = Color(0xFF6C6C6C))
    }
}

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
