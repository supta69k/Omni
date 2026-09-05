package com.example.omni.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.omni.R

@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/**
 * Display face for headlines and buttons — matches the Figma source exactly.
 *
 * Both families are shipped as OFL-licensed variable fonts, so a single file covers every weight;
 * [variableFont] pins the `wght` axis per entry (supported from API 26, which is our minSdk).
 */
val PlusJakartaSans = FontFamily(
    variableFont(R.font.plus_jakarta_sans, FontWeight.Light),
    variableFont(R.font.plus_jakarta_sans, FontWeight.Normal),
    variableFont(R.font.plus_jakarta_sans, FontWeight.Medium),
    variableFont(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    variableFont(R.font.plus_jakarta_sans, FontWeight.Bold),
)

/**
 * Body face. The Figma file specifies Satoshi, which is not open-licensed and so is not bundled
 * here — Manrope stands in for it as the closest geometric grotesque.
 *
 * To use the real thing: drop Satoshi's TTFs into `res/font/` and repoint this one declaration.
 * Nothing else changes, because every screen references this constant rather than a font name.
 */
val BodyFont = FontFamily(
    variableFont(R.font.manrope, FontWeight.Normal),
    variableFont(R.font.manrope, FontWeight.Medium),
)

/**
 * Type scale lifted from the onboarding frame. Letter spacing in Figma is expressed as -0.02em;
 * Compose wants an absolute value, hence the per-size figures.
 */
val Typography = Typography(
    // 26sp headline (onboarding page 1)
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.52).sp,
    ),
    // 32sp headline (onboarding pages 2 and 3)
    headlineLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.64).sp,
    ),
    // Supporting paragraph copy
    bodyLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.4).sp,
    ),
    // Footer / secondary labels
    bodyMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.24).sp,
    ),
    // Primary button label
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.45).sp,
    ),

    // ---- Auth screens (sign up / sign in) ----
    // Screen title: "Create An Account" / "Welcome Back". Figma sets `leading: normal`, so the
    // line height is deliberately left to the font (26sp Plus Jakarta Sans resolves to ~33sp,
    // which is exactly the height Figma reports for the node).
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        letterSpacing = (-0.26).sp,
    ),
    // Screen subtitle under the title
    bodySmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.28).sp,
    ),
    // Field placeholders and "Forget Password?" — again `leading: normal` in the source
    titleMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = (-0.16).sp,
    ),
    // Auth button label. Onboarding's button is Plus Jakarta Sans at the same size; these two
    // screens switch to the body face, so they get their own slot rather than reusing labelLarge.
    titleSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.45).sp,
    ),
    // The "or" between the form and the social buttons
    labelMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = (-0.21).sp,
    ),
    // "Agree the terms of use and privacy policy"
    labelSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = (-0.12).sp,
    ),
)
