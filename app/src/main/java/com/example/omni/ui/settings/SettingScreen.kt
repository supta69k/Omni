package com.example.omni.ui.settings

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.omni.R
import com.example.omni.ui.DesignFrame
import com.example.omni.ui.DevicePreviews
import com.example.omni.ui.components.OmniBottomNav
import com.example.omni.ui.components.OmniNavBottomGap
import com.example.omni.ui.components.OmniNavHeight
import com.example.omni.ui.components.OmniNavItem
import com.example.omni.ui.theme.OmniBackground
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
import com.example.omni.ui.theme.OmniTheme
import com.example.omni.ui.theme.SettingsType

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
 * Settings is no longer a bottom-bar tab — it opens from the header avatar. The bar is still drawn so
 * the user can jump back to a tab, but passing the (now bar-absent) [OmniNavItem.Setting] simply
 * lights no pill, which is the honest read: this page is not one of the four destinations.
 */
@Composable
fun SettingScreen(
    userName: String = "Sayed Mahir",
    userEmail: String = "sayedmahir69@gmail.com",
    photoUrl: String? = null,
    onNavigate: (OmniNavItem) -> Unit = {},
    onEditProfile: () -> Unit = {},
    onAccountAction: (String) -> Unit = {},
    onSavedEmergencies: () -> Unit = {},
    onApplyForVerification: () -> Unit = {},
    onLogOut: () -> Unit = {},
) {
    var pushNotifications by remember { mutableStateOf(true) }
    var offlineCache by remember { mutableStateOf(true) }

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
                Spacer(Modifier.height(ProfileTop))

                ProfileRow(
                    name = userName,
                    email = userEmail,
                    photoUrl = photoUrl,
                    onEdit = onEditProfile,
                )

                Spacer(Modifier.height(AccountGroupGap))

                SettingsGroup(label = "Account Details", width = 363.dp) {
                    // The source repeats "Personal Information" verbatim on both rows and changes only
                    // the trailing link. Kept as drawn.
                    AccountRows.forEach { action ->
                        SettingsRow(
                            title = "Personal Information",
                            subtitle = "Name, DOB, Gender",
                            textWidth = 164.dp,
                            onClick = { onAccountAction(action) },
                        ) {
                            Text(
                                text = action,
                                style = SettingsType.RowAction,
                                color = OmniSetRowAction,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(EmergencyGroupGap))

                SettingsGroup(label = "Emergency & Medical", width = 364.dp) {
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

                Spacer(Modifier.height(PreferencesGroupGap))

                SettingsGroup(label = "Preferences", width = 363.dp) {
                    SettingsRow(
                        title = "Push Notifications",
                        subtitle = "Alerts, reminders, community",
                        textWidth = 201.dp,
                        onClick = { pushNotifications = !pushNotifications },
                    ) {
                        PreferenceSwitch(
                            checked = pushNotifications,
                            trackOn = OmniSetTogglePush,
                            onCheckedChange = { pushNotifications = it },
                        )
                    }
                    SettingsRow(
                        title = "Offline First Aid Cache",
                        subtitle = "Download guides for offline use",
                        textWidth = 201.dp,
                        onClick = { offlineCache = !offlineCache },
                    ) {
                        PreferenceSwitch(
                            checked = offlineCache,
                            trackOn = OmniSetToggleCache,
                            onCheckedChange = { offlineCache = it },
                        )
                    }
                }

                Spacer(Modifier.height(HealthcareCardGap))

                HealthcareCard(onApply = onApplyForVerification)

                Spacer(Modifier.height(LogOutGap))

                LogOutButton(onClick = onLogOut)

                Spacer(Modifier.height(OmniNavHeight + OmniNavBottomGap + ContentBottomGap))
            }

            OmniBottomNav(
                selected = OmniNavItem.Setting,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = OmniNavBottomGap),
                onSelect = onNavigate,
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
 */
@Composable
private fun ProfileRow(
    name: String,
    email: String,
    photoUrl: String?,
    onEdit: () -> Unit,
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
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                placeholder = painterResource(R.drawable.home_avatar),
                error = painterResource(R.drawable.home_avatar),
                fallback = painterResource(R.drawable.home_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(55.dp)
                    .clip(CircleShape),
            )
            Column(modifier = Modifier.width(165.dp)) {
                Text(
                    text = name,
                    style = SettingsType.ProfileName,
                    color = OmniSetName,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = email,
                    style = SettingsType.ProfileEmail,
                    color = OmniSetEmail,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Image(
            painter = painterResource(R.drawable.ic_set_edit),
            contentDescription = "Edit your profile",
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onEdit),
        )
    }
}

/**
 * One of the three labelled sections — Figma nodes 163:16, 163:56 and 163:29.
 *
 * All three are the same shape: a grey label, 12 of air, then rows 18 apart. Only the width differs,
 * and only by the one pixel the source gives the emergency group.
 */
@Composable
private fun SettingsGroup(
    label: String,
    width: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(start = PagePadding)
            .width(width),
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
 * Its three pieces are absolutely placed in the source (header at 22, paragraph at 83, button at 169)
 * and the paragraph's own centre is half a pixel left of the card's, so the card stays a [Box] with
 * Figma's offsets rather than becoming a column of spacers.
 */
@Composable
private fun HealthcareCard(onApply: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = PagePadding)
            .width(383.dp)
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
                .align(Alignment.TopStart)
                .padding(start = 46.dp, top = 169.dp)
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

/** "Log Out" — Figma node 163:54, a 383 x 46 dark button at radius 8. */
@Composable
private fun LogOutButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = PagePadding)
            .width(383.dp)
            .height(46.dp)
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

/** The trailing link on each account row, which is the only thing separating the two. */
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
 * 61 − 24.336: the profile row's y, less the mock iOS status bar the other frames draw and
 * `statusBarsPadding` replaces. This frame has no status bar node of its own, but it is the same 415
 * artboard measured from the same origin.
 */
private val ProfileTop = 36.664.dp

/** 153 − 116 */
private val AccountGroupGap = 37.dp

/** 341 − 308 */
private val EmergencyGroupGap = 33.dp

/** 458 − 428 */
private val PreferencesGroupGap = 30.dp

/** 653 − 613 */
private val HealthcareCardGap = 40.dp

/** 940 − 878 */
private val LogOutGap = 62.dp

/** Breathing room so the log-out button can scroll clear of the floating bar. */
private val ContentBottomGap = 24.dp

@DevicePreviews
@Composable
private fun SettingScreenPreview() {
    OmniTheme { SettingScreen() }
}
