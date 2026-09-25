package com.example.omni.ui.omniplus

import android.app.Activity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni.data.revenuecat.EntitlementIds
import com.example.omni.data.revenuecat.RevenueCatState
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniLavender
import com.example.omni.ui.theme.OmniPeriwinkle
import com.example.omni.ui.theme.OmniTheme

private val PremiumGold = Color(0xFFFFD700)
private val PremiumPurple = Color(0xFF8B5CF6)

@Composable
fun OmniPlusPaywallScreen(
    state: OmniPlusUiState,
    onPurchase: (String) -> Unit,
    onRestore: () -> Unit,
    onBrowseDoctors: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onDismiss) {
                    Text("✕", fontSize = 20.sp, color = OmniInk)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(PremiumPurple.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "✨ Omni+",
                    color = PremiumPurple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Headline
            Text(
                text = "Your Health, ",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = OmniInk,
            )
            Text(
                text = "Verified by Experts",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = PremiumPurple,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Get verified doctor consultations, priority support, and exclusive health insights — all in one subscription.",
                textAlign = TextAlign.Center,
                color = Color(0xFF6C6C6C),
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Feature list
            FeatureItem(
                icon = "🩺",
                title = "Verified Doctor Consultation",
                description = "Text-based consultations with licensed doctors",
            )
            FeatureItem(
                icon = "🔒",
                title = "Priority Support",
                description = "Skip the queue with dedicated support",
            )
            FeatureItem(
                icon = "📊",
                title = "Advanced Insights",
                description = "Deep-dive analytics for your health data",
            )
            FeatureItem(
                icon = "🎯",
                title = "Exclusive Content",
                description = "Premium guides and personalized recommendations",
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Error message
            if (state.error != null) {
                Text(
                    text = state.error,
                    color = OmniAlertRed,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            // Success: show active state
            if (state.successMessage != null && state.subscriptionState is RevenueCatState.Active) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "✓ ${state.successMessage}",
                        color = Color(0xFF16A34A),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onBrowseDoctors,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumPurple),
                    ) {
                        Text("Browse Doctors", color = Color.White)
                    }
                }
                return@Scaffold
            }

            // Subscription packages
            if (state.availablePackages.isNotEmpty()) {
                state.availablePackages.forEach { pkg ->
                    PackageCard(
                        name = pkg.name,
                        price = pkg.price.ifBlank { pkg.id },
                        isLoading = state.isPurchasing && state.purchasePackageId == pkg.id,
                        onSelect = {
                            if (activity != null) {
                                onPurchase(pkg.id)
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else {
                // Loading state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Restore
            OutlinedButton(
                onClick = onRestore,
                enabled = !state.isRestoring,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isRestoring) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("Restore Purchases")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Cancel anytime. By subscribing, you agree to our Terms of Service.",
                textAlign = TextAlign.Center,
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun FeatureItem(
    icon: String,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(PremiumPurple.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = 20.sp)
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = OmniInk,
                fontSize = 15.sp,
            )
            Text(
                text = description,
                color = Color(0xFF6C6C6C),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun PackageCard(
    name: String,
    price: String,
    isLoading: Boolean,
    onSelect: () -> Unit,
) {
    Button(
        onClick = onSelect,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PremiumPurple),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                )
                Text(
                    text = price,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// ---- Previews -----------------------------------------------------------------------------------

private val PreviewPackages = listOf(
    PackageOption(id = "omni_plus_monthly", name = "Omni+ Monthly", price = "$4.99 / month"),
    PackageOption(id = "omni_plus_yearly", name = "Omni+ Yearly", price = "$39.99 / year"),
)

private val PreviewPaywallState = OmniPlusUiState(
    subscriptionState = RevenueCatState.Inactive,
    availablePackages = PreviewPackages,
)

@DevicePreviews
@Composable
private fun OmniPlusPaywallPreview() {
    OmniTheme {
        OmniPlusPaywallScreen(
            state = PreviewPaywallState,
            onPurchase = {},
            onRestore = {},
            onBrowseDoctors = {},
            onDismiss = {},
        )
    }
}

/** The subscribed state: packages collapse to the success row and the Browse Doctors button. */
@DevicePreviews
@Composable
private fun OmniPlusPaywallSubscribedPreview() {
    OmniTheme {
        OmniPlusPaywallScreen(
            state = OmniPlusUiState(
                subscriptionState = RevenueCatState.Active(mapOf(EntitlementIds.OMNI_PLUS to true)),
                successMessage = "Welcome to Omni+!",
            ),
            onPurchase = {},
            onRestore = {},
            onBrowseDoctors = {},
            onDismiss = {},
        )
    }
}
