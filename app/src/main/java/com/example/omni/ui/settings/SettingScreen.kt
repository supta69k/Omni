package com.example.omni.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.omni.R
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.LocalDesignWindow
import com.example.omni.ui.components.VerifiedBadge
import com.example.omni.ui.theme.OmniAuthError
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniSetApply
import com.example.omni.ui.theme.OmniSetCardBody
import com.example.omni.ui.theme.OmniSetCardSurface
import com.example.omni.ui.theme.OmniSetCardTitle
import com.example.omni.ui.theme.OmniSetEmail
import com.example.omni.ui.theme.OmniSetGroupLabel
import com.example.omni.ui.theme.OmniSetLogOut
import com.example.omni.ui.theme.OmniSetName
import com.example.omni.ui.theme.OmniSetRowAction
import com.example.omni.ui.theme.OmniSetRowSubtitle
import com.example.omni.ui.theme.OmniSetRowTitle
import com.example.omni.ui.theme.OmniSetToggleCache
import com.example.omni.ui.theme.OmniSetToggleKnob
import com.example.omni.ui.theme.OmniSetToggleOff
import com.example.omni.ui.theme.OmniSetTogglePush
import com.example.omni.ui.theme.BodyFont
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.PlusJakartaSans
import com.example.omni.ui.theme.SettingsType
import com.example.omni.ui.motion.OmniMotion.pressEffect

/**
 * Settings — Figma frame `iPhone 14 & 15 Pro - 36` (node 163:14), a 415 x 1113 artboard.
 *
 * The only tabbed screen with **no** [com.example.omni.ui.components.OmniHeader]: it opens on its own
 * 55dp profile row at y=61, because the header's job — telling you who you are — is what this page is
 * about. Everything below that is a plain scrolling page, so the whole thing is one `verticalScroll`
 * column with the floating bar over it.
 *
 * The page's gutter is 16 on the left throughout, but the right edge is not: the three grouped
 * sections stop at 379/380 while the healthcare card and the log-out button run to 399. Each block
 * therefore carries its own Figma width instead of the column carrying symmetric padding.
 *
 * "Log Out" sits at y=940 and the bar Figma draws is at y=915..982, so in the source the button is
 * *behind* the bar and invisible — which is why Figma's own render of this frame appears to end at the
 * card. Here it is ordinary scrolling content, as on the dashboard, and scrolls clear of the floating
 * bar.
 *
 * Settings is no longer a bottom-bar tab — it opens from the header avatar — and it **draws no bar or
 * rail at all**. Keeping the bar meant drawing four pills with none of them lit, because Settings is not
 * one of the four destinations; a chrome that can only misreport its own state is worse than no chrome.
 * So this page joins its own children ([SavedEmergenciesScreen], [GoalsScreen],
 * [PersonalInformationScreen]): a bare [DesignFrame] whose only way out is the back arrow. That also
 * gives the content the full height back, which is what un-collided the card's "Apply for Verification"
 * button from the floating bar in portrait.
 *
 * **The back row above the profile is not in the frame** (§6 rule 9). The frame was drawn when this was
 * a tab, and a page you *enter* needs a way out — with the bar gone it is the *only* way out, so it is
 * load-bearing rather than a convenience. It is the same 40dp mirrored-arrow circle
 * [SavedEmergenciesScreen] and [GoalsScreen] use, so all three pages behind Settings now leave the same
 * way. It costs 23.3dp: the profile row moves from 36.7 below the status bar to 60, which the page
 * absorbs by scrolling. Everything below it keeps the design's own arithmetic.
 */
@Composable
fun SettingScreen(
    userName: String = "Sayed Mahir",
    userEmail: String = "sayedmahir69@gmail.com",
    photoUrl: String? = null,
    /** True while a picked photo is uploading — the row shows it on the avatar itself. */
    photoUploading: Boolean = false,
    /** The last upload's failure sentence, shown under the email until the next attempt. */
    photoError: String? = null,
    pushNotifications: Boolean = true,
    offlineCache: Boolean = true,
    onBack: () -> Unit = {},
    onChangePhoto: () -> Unit = {},
    /** The "Account Details" group's two rows, named like the two below them rather than dispatched
     *  on the trailing word — that string was a label, and routing on it made the label load-bearing. */
    onPersonalInformation: () -> Unit = {},
    onSecurity: () -> Unit = {},
    onSavedEmergencies: () -> Unit = {},
    onDailyGoals: () -> Unit = {},
    onPushNotificationsChange: (Boolean) -> Unit = {},
    onOfflineCacheChange: (Boolean) -> Unit = {},
    /** Exports the month's health report as a shareable PDF — Omni+ gated in the router. */
    onExportHealthReport: () -> Unit = {},
    onApplyForVerification: () -> Unit = {},
    onOmniPlus: () -> Unit = {},
    /** Omni+ active state — swaps the promo card for the current-plan card. Central RevenueCat state. */
    omniPlusActive: Boolean = false,
    /** "Monthly" / "Yearly" / null — the label under "Current plan", from RevenueCat's CustomerInfo. */
    omniPlusPlan: String? = null,
    canUpgradeToYearly: Boolean = false,
    onManageSubscription: () -> Unit = {},
    onUpgradeToYearly: () -> Unit = {},
    verified: Boolean = false,
    onLogOut: () -> Unit = {},
) {
    BackHandler(onBack = onBack)

    DesignFrame {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OmniBackground),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding(),
            ) {
                Spacer(Modifier.height(BackRowTop))

                Box(
                    modifier = Modifier
                        .padding(start = PagePadding)
                        .size(BackButtonSize)
                        .pressEffect()
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_set_arrow_right),
                        contentDescription = "Back",
                        modifier = Modifier
                            .size(24.dp)
                            .scale(scaleX = -1f, scaleY = 1f),
                    )
                }

                Spacer(Modifier.height(BackRowGap))

                ProfileRow(
                    name = userName,
                    email = userEmail,
                    photoUrl = photoUrl,
                    uploading = photoUploading,
                    error = photoError,
                    verified = verified,
                    omniPlus = omniPlusActive,
                    onChangePhoto = onChangePhoto,
                )

                Spacer(Modifier.height(AccountGroupGap))

                // Portrait keeps the design's single-column stack, byte-for-byte: each group carries its
                // own Figma width at the 16 left gutter, which is why the modifiers below are spelled
                // out per call rather than folded into [SettingsGroup]. Landscape unlocks a wide window,
                // so the three short groups reflow into two columns that fill the width instead of a
                // portrait column stranded at the left gutter (§ landscape reflow).
                if (LocalDesignWindow.current.isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PagePadding),
                        horizontalArrangement = Arrangement.spacedBy(LandscapeColumnGap),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(EmergencyGroupGap),
                        ) {
                            AccountDetailsGroup(
                                modifier = Modifier.fillMaxWidth(),
                                onPersonalInformation = onPersonalInformation,
                                onSecurity = onSecurity,
                            )
                            EmergencyGroup(
                                modifier = Modifier.fillMaxWidth(),
                                onSavedEmergencies = onSavedEmergencies,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            PreferencesGroup(
                                modifier = Modifier.fillMaxWidth(),
                                onDailyGoals = onDailyGoals,
                                pushNotifications = pushNotifications,
                                onPushNotificationsChange = onPushNotificationsChange,
                                offlineCache = offlineCache,
                                onOfflineCacheChange = onOfflineCacheChange,
                                onExportHealthReport = onExportHealthReport,
                            )
                        }
                    }
                } else {
                    AccountDetailsGroup(
                        modifier = Modifier.padding(start = PagePadding).width(AccountGroupWidth),
                        onPersonalInformation = onPersonalInformation,
                        onSecurity = onSecurity,
                    )

                    Spacer(Modifier.height(EmergencyGroupGap))

                    EmergencyGroup(
                        modifier = Modifier.padding(start = PagePadding).width(EmergencyGroupWidth),
                        onSavedEmergencies = onSavedEmergencies,
                    )

                    Spacer(Modifier.height(PreferencesGroupGap))

                    PreferencesGroup(
                        modifier = Modifier.padding(start = PagePadding).width(PreferencesGroupWidth),
                        onDailyGoals = onDailyGoals,
                        pushNotifications = pushNotifications,
                        onPushNotificationsChange = onPushNotificationsChange,
                        offlineCache = offlineCache,
                        onOfflineCacheChange = onOfflineCacheChange,
                        onExportHealthReport = onExportHealthReport,
                    )
                }

                Spacer(Modifier.height(UpgradeCardTopGap))

                // Free users see the promo; an active subscriber sees their plan with Manage/Upgrade.
                if (omniPlusActive) {
                    OmniPlusActiveCard(
                        plan = omniPlusPlan,
                        canUpgradeToYearly = canUpgradeToYearly,
                        onManage = onManageSubscription,
                        onUpgrade = onUpgradeToYearly,
                    )
                } else {
                    OmniPlusCard(onClick = onOmniPlus)
                }

                // A verified professional has nothing left to apply for — the card is their past.
                if (!verified) {
                    Spacer(Modifier.height(UpgradeCardBottomGap))

                    HealthcareCard(onApply = onApplyForVerification)
                }

                Spacer(Modifier.height(LogOutGap))

                LogOutButton(onClick = onLogOut)

                Spacer(Modifier.height(ContentBottomGap))
            }
        }
    }
}

/**
 * The three labelled sections, extracted so the portrait stack and the landscape two-column layout can
 * each place them without duplicating their rows. Each is exactly the block it was inline before.
 *
 * The [modifier] is what makes one body serve both: portrait hands it the design's own
 * `padding(start = 16).width(363/364)`, landscape hands it `fillMaxWidth()` inside a weighted column.
 */
@Composable
private fun AccountDetailsGroup(
    modifier: Modifier = Modifier,
    onPersonalInformation: () -> Unit,
    onSecurity: () -> Unit,
) {
    SettingsGroup(label = "Account Details", modifier = modifier) {
        // First row: Personal Information (name, DOB, gender)
        SettingsRow(
            title = "Personal Information",
            subtitle = "Name, DOB, Gender",
            textWidth = 164.dp,
            onClick = onPersonalInformation,
        ) {
            Text(
                text = "Edit",
                style = SettingsType.RowAction,
                color = OmniSetRowAction,
                maxLines = 1,
                softWrap = false,
            )
        }
        // Second row: Security (email, password) — was duplicated "Personal Information"
        SettingsRow(
            title = "Security",
            subtitle = "Email, Password",
            textWidth = 164.dp,
            onClick = onSecurity,
        ) {
            Text(
                text = "Change",
                style = SettingsType.RowAction,
                color = OmniSetRowAction,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun EmergencyGroup(
    modifier: Modifier = Modifier,
    onSavedEmergencies: () -> Unit,
) {
    SettingsGroup(label = "Emergency & Medical", modifier = modifier) {
        SettingsRow(
            title = "Saved Emergencies",
            subtitle = "Medical ID, Blood Type, Allergies",
            textWidth = 201.dp,
            onClick = onSavedEmergencies,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_set_arrow_right),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun PreferencesGroup(
    modifier: Modifier = Modifier,
    onDailyGoals: () -> Unit,
    pushNotifications: Boolean,
    onPushNotificationsChange: (Boolean) -> Unit,
    offlineCache: Boolean,
    onOfflineCacheChange: (Boolean) -> Unit,
    onExportHealthReport: () -> Unit,
) {
    SettingsGroup(label = "Preferences", modifier = modifier) {
        // First in the group because it is the only row here that changes what the other
        // screens *say* — the two below it change what the app does in the background.
        SettingsRow(
            title = "Export Health Report",
            subtitle = "A monthly PDF summary for your doctor · Omni+",
            textWidth = 201.dp,
            onClick = onExportHealthReport,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_set_arrow_right),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        SettingsRow(
            title = "Daily Goals",
            subtitle = "Water, steps, sleep, food",
            textWidth = 201.dp,
            onClick = onDailyGoals,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_set_arrow_right),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        SettingsRow(
            title = "Push Notifications",
            subtitle = "Alerts, reminders, community",
            textWidth = 201.dp,
            onClick = { onPushNotificationsChange(!pushNotifications) },
        ) {
            PreferenceSwitch(
                checked = pushNotifications,
                trackOn = OmniSetTogglePush,
                onCheckedChange = onPushNotificationsChange,
            )
        }
        SettingsRow(
            title = "Offline First Aid Cache",
            subtitle = "Download guides for offline use",
            textWidth = 201.dp,
            onClick = { onOfflineCacheChange(!offlineCache) },
        ) {
            PreferenceSwitch(
                checked = offlineCache,
                trackOn = OmniSetToggleCache,
                onCheckedChange = onOfflineCacheChange,
            )
        }
    }
}

/**
 * The profile row — Figma node 163:64, 363 wide at x=16.
 *
 * The avatar is 55 here against the header's 36, and the name is set two points larger, so this is not
 * [com.example.omni.ui.components.OmniHeader] with different padding — it is its own row. The photo is
 * the same asset the header uses, loaded the same way, and falls back to it when there is no
 * [photoUrl].
 *
 * Both lines are ellipsised inside Figma's own 165 slot. A real address is routinely longer than the
 * mock's — `softWrap = false` alone would clip it mid-glyph with no sign anything was missing.
 *
 * **This row is where a profile photo is set** (§6 rule 10: the tap now does something). Figma draws
 * the pencil as decoration with nowhere to go, and the app could read a `photoUrl` from fourteen
 * places while writing it from none — so the avatar *and* the pencil open the photo picker, and the
 * pencil's label says so. Editing a name or a date of birth stays on the "Personal Information" rows
 * below, which already have their own taps.
 *
 * Two states the frame has no room for (§6 rule 8): [uploading] dims the avatar behind the
 * background's own scrim rather than adding a spinner the design has no vocabulary for, and [error]
 * adds a third line to the 165 slot. That line is the only thing on this page that can move the rows
 * below it — by one text line, on a page that already scrolls, and only after a failure.
 */
@Composable
private fun ProfileRow(
    name: String,
    email: String,
    photoUrl: String?,
    uploading: Boolean,
    error: String?,
    verified: Boolean,
    omniPlus: Boolean,
    onChangePhoto: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(start = PagePadding)
            .width(363.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(55.dp)
                    .pressEffect(enabled = !uploading)
                    .clip(CircleShape)
                    .clickable(enabled = !uploading, onClick = onChangePhoto),
            ) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Change your profile photo",
                    placeholder = painterResource(R.drawable.home_avatar),
                    error = painterResource(R.drawable.home_avatar),
                    fallback = painterResource(R.drawable.home_avatar),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (uploading) {
                    // The page's own background at half strength — the one scrim this app has, the
                    // same trick `SleepEntrySheet` uses rather than naming a new colour.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(OmniBackground.copy(alpha = 0.6f)),
                    )
                }
            }
            Column(modifier = Modifier.width(165.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = SettingsType.ProfileName,
                        color = OmniSetName,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (verified) {
                        VerifiedBadge()
                    }
                    if (omniPlus) {
                        com.example.omni.ui.components.OmniPlusBadge()
                    }
                }
                Text(
                    text = if (uploading) "Uploading your photo…" else email,
                    style = SettingsType.ProfileEmail,
                    color = OmniSetEmail,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = SettingsType.ProfileEmail,
                        color = OmniAuthError,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Image(
            painter = painterResource(R.drawable.ic_set_edit),
            contentDescription = "Change your profile photo",
            modifier = Modifier
                .size(24.dp)
                .pressEffect(enabled = !uploading)
                .clickable(enabled = !uploading, onClick = onChangePhoto),
        )
    }
}

/**
 * One of the three labelled sections — Figma nodes 163:16, 163:56 and 163:29.
 *
 * All three are the same shape: a grey label, 12 of air, then rows 18 apart. Only the width differs,
 * and only by the one pixel the source gives the emergency group.
 *
 * The geometry lives entirely in the caller's [modifier]: portrait passes the design's own left gutter
 * and fixed width, landscape passes `fillMaxWidth()`. This body deliberately adds neither, because a
 * `fillMaxWidth()` baked in here was exactly the regression that ate the 16dp gutter in portrait and
 * pushed the rows off the left edge of the phone.
 */
@Composable
private fun SettingsGroup(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = SettingsType.GroupLabel,
            color = OmniSetGroupLabel,
            maxLines = 1,
            softWrap = false,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

/**
 * A title/subtitle pair with something on the right — a link, a chevron or a switch.
 *
 * `textWidth` is Figma's own slot for the two lines (164 in the account group, 201 in the other two),
 * which is what puts the trailing element where the design puts it.
 */
@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    textWidth: Dp,
    onClick: () -> Unit = {},
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressEffect()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(textWidth)) {
            Text(
                text = title,
                style = SettingsType.RowTitle,
                color = OmniSetRowTitle,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = subtitle,
                style = SettingsType.RowSubtitle,
                color = OmniSetRowSubtitle,
                maxLines = 1,
                softWrap = false,
            )
        }
        trailing()
    }
}

/**
 * A preference switch — Figma nodes 163:36 and 163:42, a 54 x 23 track with a 21 knob.
 *
 * The source draws both of them on, each with its own track colour, and never draws off. A switch that
 * cannot be switched is broken, so off reuses the page indicator's idle grey rather than inventing a
 * colour; on is untouched, which is the state the design specifies and the state the screen opens in.
 */
@Composable
private fun PreferenceSwitch(
    checked: Boolean,
    trackOn: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    // Figma centres the knob at cx=42.5 in a 54 track, i.e. 32 from the left edge and 1 clear of the
    // right; off mirrors that to 1 from the left.
    val knobStart by animateDpAsState(if (checked) 32.dp else 1.dp, label = "switchKnob")
    val track by animateColorAsState(if (checked) trackOn else OmniSetToggleOff, label = "switchTrack")

    Box(
        modifier = Modifier
            .width(54.dp)
            .height(23.dp)
            .clip(RoundedCornerShape(11.5.dp))
            .background(track)
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = knobStart)
                .size(21.dp)
                .clip(CircleShape)
                .background(OmniSetToggleKnob),
        )
    }
}

/**
 * "Healthcare Professional?" — Figma node 163:44, a 383 x 225 card at radius 13.
 *
 * Its three pieces are placed by the source at 22 / 83 / 169 from the top; those are vertical offsets,
 * which are the same physical distance in either orientation, so they stay. What used to pin the card
 * to a literal 383 and the button to `start = 46` — both of which jam against the left of a wide
 * landscape window — is now relative: the card fills its gutters (portrait keeps 383, since
 * `415 − 16 − 16 = 383`) and the button centres like the header and paragraph above it (the design's
 * symmetric 46/46 side gutters on a 383 card are a centred button spelled out in absolute x).
 */
@Composable
private fun HealthcareCard(onApply: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PagePadding, end = PagePadding)
            .height(225.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(OmniSetCardSurface),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_set_doctor),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "Healthcare Professional?",
                style = SettingsType.CardTitle,
                color = OmniSetCardTitle,
                maxLines = 1,
                softWrap = false,
            )
        }

        Text(
            text = "Apply for a verified badge to offer advice and consultations in the community. " +
                "Requires manual review of medical certifications.",
            style = SettingsType.CardBody,
            color = OmniSetCardBody,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 83.dp)
                .width(306.dp),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 169.dp)
                .pressEffect()
                .clip(RoundedCornerShape(23.dp))
                .background(OmniSetApply)
                .clickable(onClick = onApply)
                .padding(horizontal = 68.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Apply for Verification",
                style = SettingsType.CardButton,
                color = OmniOnInk,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * "Upgrade to Omni Plus" — Figma node 243-235, to the dp.
 *
 * The dark card pins four elements: "Upgrade to" at the top-left, the gradient-text "Omni Plus"
 * badge with its sparkles centred above the middle (the same five-stop gradient the paywall badge
 * uses), the one-line subtitle at the bottom-left, and the rotated arrow on the right. The badge's
 * sparkles are the same white-stroke asset the paywall badge uses — untinted, because the dark
 * surface is what the asset was drawn for.
 */
@Composable
private fun OmniPlusCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PagePadding, end = PagePadding)
            .height(78.dp)
            .pressEffect()
            .clip(RoundedCornerShape(13.dp))
            .background(OmniInk)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = "Upgrade to",
            style = TextStyle(
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
            ),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 20.dp, top = 18.dp),
        )

        // Figma anchors the badge group's centre at (50% − 31.06, 50% − 10.5) — just right of the
        // title's own centre line, not card-centred.
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-31).dp, y = (-10.5).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "Omni Plus",
                style = TextStyle(
                    brush = Brush.verticalGradient(
                        0.0277f to OmniPlusBadgeGradient[0],
                        0.1127f to OmniPlusBadgeGradient[1],
                        0.2849f to OmniPlusBadgeGradient[2],
                        0.5218f to OmniPlusBadgeGradient[3],
                        1f to OmniPlusBadgeGradient[4],
                    ),
                    fontFamily = BodyFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    letterSpacing = (-0.131).sp,
                ),
            )
            Image(
                painter = painterResource(R.drawable.ic_omniplus_sparkles),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        }

        Text(
            text = "Unlock premium tools and expert support with Omni+.",
            style = TextStyle(
                fontFamily = BodyFont,
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
            ),
            color = OmniUpgradeSubtitle,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 14.dp),
        )

        Image(
            painter = painterResource(R.drawable.ic_omniplus_arrow),
            contentDescription = "Open Omni+",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 13.dp)
                .size(24.dp),
        )
    }
}

/**
 * The active-subscriber card: the promo card's dark Omni Plus surface and gradient badge, but showing
 * the current plan with Manage and (for a monthly plan) Upgrade actions instead of the "Upgrade to"
 * pitch. No Figma frame for this state (§6 rule 11), so it borrows the promo card's surface/radius/badge.
 */
@Composable
private fun OmniPlusActiveCard(
    plan: String?,
    canUpgradeToYearly: Boolean,
    onManage: () -> Unit,
    onUpgrade: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PagePadding, end = PagePadding)
            .clip(RoundedCornerShape(13.dp))
            .background(OmniInk)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "Omni Plus",
                style = TextStyle(
                    brush = Brush.verticalGradient(
                        0.0277f to OmniPlusBadgeGradient[0],
                        0.1127f to OmniPlusBadgeGradient[1],
                        0.2849f to OmniPlusBadgeGradient[2],
                        0.5218f to OmniPlusBadgeGradient[3],
                        1f to OmniPlusBadgeGradient[4],
                    ),
                    fontFamily = BodyFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    letterSpacing = (-0.131).sp,
                ),
            )
            Image(
                painter = painterResource(R.drawable.ic_omniplus_sparkles),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        }
        // PLACEHOLDER_ACTIVECARD_BODY
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Current plan",
                style = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 10.sp),
                color = OmniUpgradeSubtitle,
            )
            Text(
                text = plan ?: "Active",
                style = TextStyle(
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                ),
                color = Color.White,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.12f)),
        )

        OmniPlusActionRow(label = "Manage subscription", accent = false, onClick = onManage)
        if (canUpgradeToYearly) {
            OmniPlusActionRow(label = "Upgrade to Yearly", accent = true, onClick = onUpgrade)
        }
    }
}

/** One action row inside [OmniPlusActiveCard] — a label and the white arrow, lavender when [accent]. */
@Composable
private fun OmniPlusActionRow(label: String, accent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressEffect()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = BodyFont,
                fontWeight = if (accent) FontWeight.Medium else FontWeight.Normal,
                fontSize = 14.sp,
            ),
            color = if (accent) OmniSetApply else Color.White,
        )
        Image(
            painter = painterResource(R.drawable.ic_omniplus_arrow),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The badge text's five-stop gradient — the paywall badge's own, from Figma node 243-235. */
private val OmniPlusBadgeGradient = listOf(
    Color(0xFF8D84F9),
    Color(0xFFAE96EF),
    Color(0xFFF1BBDC),
    Color(0xFFFEEBA9),
    Color(0xFFAFDFDF),
)

private val OmniUpgradeSubtitle = Color(0xFFDADADA)

/** "Log Out" — Figma node 163:54, a 383 x 46 dark button at radius 8. */
@Composable
private fun LogOutButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = PagePadding)
            .width(383.dp)
            .height(46.dp)
            .pressEffect()
            .clip(RoundedCornerShape(8.dp))
            .background(OmniSetLogOut)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Log Out",
            style = SettingsType.LogOut,
            color = OmniOnInk,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The trailing link on each account row — kept for reference but no longer used in the loop. */
private val AccountRows = listOf("Edit", "Change")

// ---- Geometry ----------------------------------------------------------------------------------
//
// From frame `iPhone 14 & 15 Pro - 36`, 415 x 1113. The vertical map is: profile row 61..116, account
// group 153..308, emergency group 341..428, preferences 458..613, healthcare card 653..878, log out
// 940..986. Every gap below is that map's arithmetic. Each labelled group measures
// 25 (label) + 12 + rows, and each row is two 25-leading lines, so 50.

/** The page's left gutter. The right edge is per-block, because the design's is. */
private val PagePadding = 16.dp

/**
 * The three groups' own Figma widths, at the 16 left gutter — 363 / 364 / 363. The odd one out is the
 * source's, not a typo here. Portrait applies them; landscape ignores them for `fillMaxWidth()`.
 */
private val AccountGroupWidth = 363.dp
private val EmergencyGroupWidth = 364.dp
private val PreferencesGroupWidth = 363.dp

/**
 * The back row's own top gap.
 *
 * It stands in for the design's 36.664 (= 61 − 24.336, the profile row's y less the mock iOS status bar
 * the other frames draw and `statusBarsPadding` replaces). The frame has no back button, so there is no
 * measurement to copy: 12 above and 8 below the 40dp circle is [GoalsScreen]'s spacing, which puts the
 * profile row at 60 instead of 36.7. Everything below the profile row is still the frame's arithmetic.
 */
private val BackRowTop = 12.dp

/** [SavedEmergenciesScreen]'s and [GoalsScreen]'s back button, unchanged so all three pages match. */
private val BackButtonSize = 40.dp

private val BackRowGap = 8.dp

/** 153 − 116 */
private val AccountGroupGap = 37.dp

/** 341 − 308 */
private val EmergencyGroupGap = 33.dp

/** 458 − 428 */
private val PreferencesGroupGap = 30.dp

/**
 * The air between the two landscape columns. Not a Figma value — the frame is portrait-only — so it
 * matches [com.example.omni.ui.components.AdaptiveRow]'s own 16 inter-column default, which every other
 * reflowed screen uses.
 */
private val LandscapeColumnGap = 16.dp

/** 653 − 613 */
private val HealthcareCardGap = 40.dp

/** Figma 243-235: the upgrade card's breathing room above and below. */
private val UpgradeCardTopGap = 24.dp
private val UpgradeCardBottomGap = 24.dp

/** 940 − 878 */
private val LogOutGap = 62.dp

/** Breathing room under the log-out button, now that no floating bar overlaps it. */
private val ContentBottomGap = 24.dp

@DevicePreviews
@Composable
private fun SettingScreenPreview() {
    OmniTheme { SettingScreen() }
}
