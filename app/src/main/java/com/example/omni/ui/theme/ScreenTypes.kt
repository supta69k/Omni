package com.example.omni.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Text styles for the five frames added after the dashboard: the community feed
 * (`iPhone 14 & 15 Pro - 28`), the nutrition log (`- 35`), and the three map screens
 * (`- 31`, `- 30`, `- 33`).
 *
 * Same contract as [HomeType]: sizes, leadings and letter spacings are Figma's own numbers with the
 * fractions kept, colour is never baked in, and every value is applied at the call site from
 * `Color.kt`. Where a style genuinely coincides with one the dashboard already defines it is still
 * written out here rather than aliased, so the two frames can drift apart without surprising each
 * other.
 */

// ---- Community feed -----------------------------------------------------------------------------

object FeedType {

    /** "Search here" in the search pill, and the "Recently Post" label above the first post. */
    val Hint16 = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.16).sp,
    )

    /** "Share Your healthy meal" under the plus button on the first story tile. */
    val StoryCaption = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.5.sp,
        letterSpacing = (-0.1).sp,
    )

    /**
     * "Discover" and "Following". Both states are the same run in the source — only the colour and
     * the white pill behind the active one change, unlike the dashboard's tabs which also swap face.
     */
    val SegmentLabel = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.16).sp,
    )

    /** "Dr.Ben" / "Dr.Kelly" */
    val AuthorName = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.16).sp,
    )

    /** "Verified by omni" under the author, and the "3 min ago" opposite it. */
    val Meta12 = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.12).sp,
    )

    /** The post copy — the only place in the app the display face sets a paragraph. */
    val PostBody = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.14).sp,
    )

    /** "1k" / "100" / "50" in the like, comment and repost pills. */
    val ActionValue = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.16).sp,
    )
}

// ---- Nutrition / food log -----------------------------------------------------------------------

object NutritionType {

    /** "S" / "M" / "T" … on the weekday chips. */
    val DayWeekday = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.156).sp,
    )

    /** "01" … "07" under each weekday. The source sets no tracking on this run. */
    val DayNumber = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )

    /** "80%" at the centre of the arc. Figma sets `leading: normal`, so the font decides. */
    val GaugePercent = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
    )

    /**
     * "You're 20% away to hit your daily goal." — one run of mixed weights and two colours, so the
     * screen builds it as an [androidx.compose.ui.text.AnnotatedString] over this base.
     */
    val GaugeSentence = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 16.681.sp,
        letterSpacing = (-0.08).sp,
    )

    /** The "59" and "5.7" of the weight and height flanking the arc. */
    val StatValue = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 17.sp,
        letterSpacing = (-0.14).sp,
    )

    /** The "/" between them, which drops to Regular but keeps the size. */
    val StatSeparator = StatValue.copy(fontWeight = FontWeight.Normal)

    /** The "kg" and "foot" suffixes, two points smaller on the same baseline run. */
    val StatUnit = StatSeparator.copy(fontSize = 14.sp)

    /** "30" / "5.6/10k" at the centre of each macro ring. */
    val RingValue = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.997.sp,
        lineHeight = 20.64.sp,
        letterSpacing = (-0.17).sp,
    )

    /** The trailing "g" on the first page's rings, which is Regular and a shade larger. */
    val RingUnit = RingValue.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp)

    /** "Protein" / "Carbs" / "Fat" / "Steps" / "water" / "Sleep" under each ring. */
    val MacroLabel = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 19.426.sp,
        lineHeight = 20.64.sp,
        letterSpacing = (-0.1943).sp,
    )

    /** "Food Log" */
    val SectionTitle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 21.sp,
    )

    /** "Eggs & Toast" */
    val FoodName = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    )

    /** "404cal" */
    val FoodCalories = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )

    /** "12g" / "15g" / "35g" in the three macro chips on each row. */
    val FoodChip = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 12.sp,
    )
}

// ---- SOS and hospital maps ----------------------------------------------------------------------

object MapType {

    /** "Search here" in the pill floating over the map. */
    val SearchHint = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.16).sp,
    )

    /** "Swipe to active SOS" — the widest tracking anywhere in the app. */
    val SosLabel = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.315).sp,
    )

    /** "Nearest Hospitals" */
    val SheetTitle = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )

    /** "Found 5 facilities within 5 miles", and each card's "4 mins (0.8 mi)". */
    val SheetSubtitle = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.14).sp,
    )

    /** "City Central Hospital", and both action-button labels. */
    val HospitalName = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.18).sp,
    )

    /** "Emergency Room • 24/7" */
    val HospitalType = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.13).sp,
    )

    /** "4.3" beside the star. */
    val HospitalRating = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.16).sp,
    )
}
