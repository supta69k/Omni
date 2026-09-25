package com.example.omni.ui.omniplus

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.omni.data.model.Doctor
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniLavender
import com.example.omni.ui.theme.OmniTheme

private val PremiumPurple = Color(0xFF8B5CF6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorDirectoryScreen(
    state: DoctorDirectoryUiState,
    onDoctorClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🩺 Doctor Directory",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                    }
                },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Specialty filter chips
            if (state.specialties.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedSpecialty == null,
                            onClick = { /* selectSpecialty(null) */ },
                            label = { Text("All") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PremiumPurple,
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }
                    items(state.specialties) { specialty ->
                        FilterChip(
                            selected = state.selectedSpecialty == specialty,
                            onClick = { /* selectSpecialty(specialty) */ },
                            label = { Text(specialty) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PremiumPurple,
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }
                }
            }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = PremiumPurple)
                }
            } else if (state.doctors.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🩺", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No doctors available yet",
                            color = Color(0xFF6C6C6C),
                            fontSize = 16.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.doctors) { doctor ->
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

@Composable
private fun DoctorCard(
    doctor: Doctor,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            if (doctor.photoUrl != null) {
                AsyncImage(
                    model = doctor.photoUrl,
                    contentDescription = doctor.name,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(OmniLavender.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = doctor.name.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = OmniLavender,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Dr. ${doctor.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = OmniInk,
                    )
                    if (doctor.verified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("✓", color = Color(0xFF16A34A), fontSize = 14.sp)
                    }
                }

                Text(
                    text = doctor.specialty,
                    fontSize = 14.sp,
                    color = PremiumPurple,
                    fontWeight = FontWeight.Medium,
                )

                if (doctor.rating > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text("⭐", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${doctor.rating} (${doctor.reviewCount})",
                            fontSize = 12.sp,
                            color = Color(0xFF6C6C6C),
                        )
                    }
                }

                if (doctor.consultationFee.isNotBlank()) {
                    Text(
                        text = doctor.consultationFee,
                        fontSize = 13.sp,
                        color = Color(0xFF6C6C6C),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Consult",
                tint = PremiumPurple,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

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
        DoctorDirectoryScreen(state = PreviewDirectoryState, onDoctorClick = {}, onBack = {})
    }
}

@DevicePreviews
@Composable
private fun DoctorDirectoryLoadingPreview() {
    OmniTheme {
        DoctorDirectoryScreen(state = DoctorDirectoryUiState(), onDoctorClick = {}, onBack = {})
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
        )
    }
}
