package com.example.omni.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Omni "Premium Soft-Bento" palette.
 *
 * Values are taken directly from the Figma source (PROJECT_OMNI_BACKUP). The Figma file does not
 * define published variables yet, so these constants are the single source of truth in code —
 * do not hardcode hex values in screens.
 */

// Surfaces
val OmniBackground = Color(0xFFFFFFFF)

/**
 * The one true red in the app: the SOS swipe track ([OmniSosTrack]) and a failed sign-in
 * ([OmniAuthError]). Declared up here because top-level properties initialise in file order — an
 * alias may only ever point at something declared **above** it, or it silently reads as transparent.
 */
val OmniAlertRed = Color(0xFFFF6962)

// Text
val OmniInk = Color(0xFF302E2E)        // headings + primary button fill
val OmniBody = Color(0xFF6C6C6C)       // supporting paragraph copy
val OmniMuted = Color(0xFF6B7280)      // secondary labels ("Already a omni member?")
val OmniOnInk = Color(0xFFFFFFFF)      // label on the dark button

// Accents used for the highlighted word in each headline
val OmniLavender = Color(0xFFB088D6)
val OmniPeriwinkle = Color(0xFFA391DE)
val OmniCream = Color(0xFFFCE6C2)
val OmniPink = Color(0xFFFF9AD8)

// Page indicator
val OmniIndicatorActive = OmniPink
val OmniIndicatorInactive = Color(0xFFE3E1E4)

// Auth screens (sign up / sign in)
val OmniAuthHeading = Color(0xFF8D84F9)   // "Create An Account" / "Welcome Back"
val OmniFieldSurface = Color(0xFFF5F5F5)  // text field + social button fill
val OmniPlaceholder = Color(0xFFABABAB)   // field placeholder text and icon strokes
val OmniAuthLink = Color(0xFF000000)      // "Sign In" / "Sign Up" footer link, terms label
val OmniDivider = OmniBody                // the two hairlines flanking "or"

/**
 * The message under a failed sign-in. The Figma frames draw no error state at all, so it reuses
 * [OmniAlertRed] rather than introducing a second red.
 */
val OmniAuthError = OmniAlertRed          // #FF6962

// ---- Home screen (Figma `iPhone 14 & 15 Pro - 22`) ----

// Header
val OmniHomeGreeting = OmniMuted          // "Good morning!" — same #6B7280 as the auth footer
val OmniHomeName = Color(0xFF21241D)      // "Sayed Mahir"
val OmniHomeWelcome = Color(0xFF5A5757)   // "Welcome back Mahir"

// Bento cards
val OmniHeroPink = Color(0xFFF1BBDC)      // first-aid card fill, and its button label
val OmniHeroInk = Color(0xFF2C0A09)       // first-aid card heading, and its button fill
val OmniWaterTeal = Color(0xFFAFDFDF)     // hydration card fill
val OmniStepsCream = Color(0xFFFEEBA9)    // step-count card fill
val OmniStatLabel = Color(0xFF3E3C3C)     // "Water today" / "Today's steps" and their hints

// "Daily updates & Recomindation" section
val OmniSectionTitle = Color(0xFF1E1E1E)
val OmniTabInactive = Color(0xFFC8C8C8)   // the progress-bar axis labels
val OmniTabActiveSurface = Color(0xFFFFFFFF)  // still shared with the feed's selected pill
val OmniTabShadow = Color(0x40BEBEBE)     // rgba(190,190,190,0.25) glow under that pill

/** Pure black — the active tab label, the update-card headings, and their big values. */
val OmniCardInk = Color(0xFF000000)

// Update cards
val OmniCardSurface = OmniFieldSurface    // #F5F5F5
val OmniProgressTrack = Color(0xFFFFFFFF)
val OmniFootnote = OmniBody               // #6C6C6C body of "You're 13g away from your Goal"
val OmniFootnoteStrong = Color(0xFF3F3838)  // the emphasised figure inside that sentence
val OmniStatusLabel = OmniInk             // "Good" / "Fair" / "Action Needed"

/**
 * The four stops of the progress-bar gradient, shared by all three update cards.
 * Figma angles it at 89.587° (89.648° on the CPR card), i.e. essentially horizontal.
 */
val OmniProgressStops = listOf(
    0.0085f to Color(0xFF8D84F9),
    0.4949f to Color(0xFFFFFFFF),
    0.7617f to Color(0xFFFEEBA9),
    0.9244f to Color(0xFFB3E1E1),
)

// Bottom navigation
val OmniNavBar = OmniInk                  // #302E2E
val OmniNavPill = Color(0xFFFFFFFF)

// ---- Community feed (Figma `iPhone 14 & 15 Pro - 28`) ----

val OmniFeedHint = OmniTabInactive        // #C8C8C8 — "Search here" and "Recently Post"
val OmniFeedSurface = OmniFieldSurface    // #F5F5F5 — search pill, story tile, action pills
val OmniFeedTimestamp = OmniAuthHeading   // #8D84F9 — "3 min ago", and the story tile's plus
val OmniFeedVerified = OmniBody           // #6C6C6C — "Verified by omni"

/** Post body copy. Nearly black but faintly green, which is why it is not [OmniCardInk]. */
val OmniFeedPostBody = Color(0xFF041F1E)

// ---- Nutrition / food log (Figma `iPhone 14 & 15 Pro - 35`) ----

val OmniNutriDaySurface = OmniFieldSurface  // #F5F5F5 — the six unselected weekday chips
val OmniNutriDayIdle = OmniBody           // #6C6C6C — their letter and date
val OmniNutriDaySelected = OmniAuthHeading  // #8D84F9 — the "W 04" chip
val OmniNutriDayShadow = Color(0x408D84F9)  // rgba(141,132,249,0.25) under that chip
val OmniNutriGaugeLabel = OmniStatLabel   // #3E3C3C — "You're 20% away to hit your daily goal."
val OmniNutriMacroLabel = Color(0xFF787878)  // "Protein" / "Carbs" / "Fat"
val OmniNutriUnit = Color(0xFF5A5A5A)     // the "kg"/"foot" suffixes, and "404cal"
val OmniNutriFoodName = OmniInk           // #302E2E — "Eggs & Toast"

/** The three macro chips on each food-log row, in Figma's own order. */
val OmniNutriChipProtein = Color(0xFFF996D3)
val OmniNutriChipCarbs = Color(0xFFB184E1)
val OmniNutriChipFat = OmniAuthHeading    // #8D84F9

// ---- SOS and hospital maps (Figma `- 31`, `- 30`, `- 33`) ----

/** The "Swipe to active SOS" track — [OmniAlertRed], the app's only red. */
val OmniSosTrack = OmniAlertRed

val OmniSheetSurface = Color(0xFFFFFDFD)  // the hospitals bottom sheet, a hair warmer than white
val OmniSheetTitle = Color(0xFF4B4B4B)    // "Nearest Hospitals"
val OmniSheetSubtitle = Color(0xFF7D7D7D) // "Found 5 facilities…", and each hospital's type
val OmniSheetDetail = OmniNutriUnit       // #5A5A5A — drive time and rating
val OmniSheetShadow = Color(0x33000000)   // rgba(0,0,0,0.2), the sheet's lift off the map
val OmniSheetGrab = Color(0x338D84F9)     // the grab handle, #8D84F9 at 20%
val OmniCallButton = Color(0xFFB3E1E1)    // "Call the Hospital"

val OmniMapSearchShadow = Color(0x40000000)  // rgba(0,0,0,0.25) under the search pill
val OmniHospitalCard = OmniFieldSurface   // #F5F5F5 — the tray each hospital's photo and details sit in
val OmniHospitalName = OmniInk            // #302E2E — "City Central Hospital"
val OmniRouteButton = OmniInk             // #302E2E — "Find the Route"

// ---- Settings (Figma `iPhone 14 & 15 Pro - 36`) ----
//
// The frame introduces no new hex: every value it uses is already in the palette above, so this
// section is named aliases only. They exist so the screen reads in its own vocabulary and so a later
// change to, say, the settings row title cannot silently repaint the feed.

val OmniSetName = OmniHomeName            // #21241D — "Sayed Mahir", the same as in the header
val OmniSetEmail = OmniNutriUnit          // #5A5A5A — "sayedmahir69@gmail.com"
val OmniSetGroupLabel = OmniNutriMacroLabel  // #787878 — "Account Details" / "Preferences"
val OmniSetRowTitle = OmniCardInk         // #000000 — "Personal Information"
val OmniSetRowSubtitle = OmniNutriMacroLabel  // #787878 — "Name, DOB, Gender"
val OmniSetRowAction = OmniCardInk        // #000000 — the "Edit" and "Change" links
val OmniSetCardSurface = OmniFieldSurface  // #F5F5F5 — the "Healthcare Professional?" card
val OmniSetCardTitle = OmniInk            // #302E2E — its heading
val OmniSetCardBody = OmniNutriUnit       // #5A5A5A — its paragraph
val OmniSetApply = OmniNutriChipCarbs     // #B184E1 — "Apply for Verification"
val OmniSetLogOut = OmniInk               // #302E2E — the log-out button

/** The two preference switches. Both are drawn on in the source, each with its own track colour. */
val OmniSetTogglePush = OmniAuthHeading   // #8D84F9 — "Push Notifications"
val OmniSetToggleCache = OmniHeroPink     // #F1BBDC — "Offline First Aid Cache"
val OmniSetToggleKnob = OmniBackground    // the 21 white knob riding in both tracks

/** Off is drawn nowhere in the source, so a switch that can be turned off borrows the idle grey. */
val OmniSetToggleOff = OmniIndicatorInactive  // #E3E1E4

// ---- The sleep entry sheet (no Figma source) ----
//
// The one screen element the design does not contain: sleep is the only metric neither the app nor the
// phone can observe, so it has to be typed, and Figma draws no way to type it. Everything it needs is
// borrowed from the hospitals sheet — the same surface, shadow and grab handle — except the dim behind
// it, because that sheet floats over a map dark enough not to need one.

/** rgba(0,0,0,0.25), matched to [OmniMapSearchShadow] so the app has one depth of black, not two. */
val OmniScrim = Color(0x40000000)
