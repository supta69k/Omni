package com.example.omni.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni.R
import com.example.omni.ui.theme.BodyFont
import com.example.omni.ui.theme.OmniAuthHeading

/**
 * The Omni+ membership mark, shown beside a subscriber's name wherever their profile identity appears
 * (home/navbar header, Settings, profile). Subtle and brand-consistent: the paywall's own sparkles
 * glyph tinted in the Omni lavender ([OmniAuthHeading]) on a faint lavender wash, with the "Omni+"
 * wordmark — not a loud generic badge.
 *
 * It only draws the mark; whether to show it is the caller's decision, taken from the single central
 * `RevenueCatRepository.isOmniPlusActive` — never a per-screen or persisted flag.
 */
@Composable
fun OmniPlusBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(OmniAuthHeading.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_omniplus_sparkles),
            contentDescription = null,
            colorFilter = ColorFilter.tint(OmniAuthHeading),
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = "Omni+",
            style = TextStyle(
                fontFamily = BodyFont,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = (-0.11).sp,
            ),
            color = OmniAuthHeading,
        )
    }
}
