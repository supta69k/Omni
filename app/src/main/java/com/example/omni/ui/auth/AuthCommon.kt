package com.example.omni.ui.auth

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.omni.R
import com.example.omni.ui.theme.OmniAuthHeading
import com.example.omni.ui.theme.OmniAuthLink
import com.example.omni.ui.theme.OmniBody
import com.example.omni.ui.theme.OmniDivider
import com.example.omni.ui.theme.OmniFieldSurface
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniMuted
import com.example.omni.ui.theme.OmniOnInk
import com.example.omni.ui.theme.OmniPlaceholder

/*
 * Pieces shared by the sign-up (Figma `iPhone 14 & 15 Pro - 23`) and sign-in (`- 24`) frames.
 *
 * Both frames are built from the same auto-layout column: logo + copy, a stack of pill fields, the
 * dark primary button, then an "or" divider and the social/footer block. Every measurement below is
 * the Figma value verbatim, including the fractional ones — they come from the source's auto-layout
 * and are kept rather than rounded so the two screens stay pixel-registered with each other.
 */

// Logo — Figma `Frame 141`
private val LogoWidth = 80.0810546875.dp
private val LogoHeight = 86.95075225830078.dp
private val LogoBlurRadius = 60.84589385986328.dp
private val TorusWidth = 77.23739624023438.dp
private val TorusHeight = 80.09803771972656.dp
private val TorusOffsetX = 1.0810546875.dp

// Fields — Figma `Frame 140`
internal val FieldHeight = 52.dp
internal val FieldCorner = 35.dp
internal val FieldStartPadding = 32.dp

/** Right inset of the sign-in password field, whose inner row is 310 wide starting at x=32. */
internal val FieldEndPadding = 41.dp

// Primary button — Figma `Frame 130`
internal val ButtonHeight = 50.dp
internal val ButtonCorner = 20.dp

// Footer — Figma `Frame 165` / `Frame 131`
private val FooterWidth = 364.00592041015625.dp
private val SocialRowWidth = 102.5.dp
private val SocialButtonSize = 35.dp

/**
 * The Omni mark: a gradient torus over a heavily blurred gradient ellipse.
 *
 * The ellipse (`Ellipse 12`) carries a 60.8px layer blur, which `Modifier.blur` could only render
 * from API 31 — minSdk here is 26 — so it ships as Figma's own PNG render of that node. Figma
 * expands an effect's export bounds by exactly the blur radius on every side, which is where the
 * glow's size and its negative offset come from. The torus itself is a 4x export of the `Image`
 * group, so the multiply/colour-dodge gradient stack baked into the source is preserved intact.
 */
@Composable
internal fun AuthLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(LogoWidth, LogoHeight),
        contentAlignment = Alignment.Center,
    ) {
        // `requiredSize`, not `size`: the glow is 2.5x wider than the box it sits in, and `size` is
        // coerced into the parent's constraints, which silently shrank it to the box and left it
        // hanging above and to the left of the mark instead of behind it. `requiredSize` overrides
        // the incoming constraints, and centring it is exact — the blur expands Figma's export
        // bounds by the same radius on all four sides, so the blob is dead centre in its own canvas.
        Image(
            painter = painterResource(R.drawable.auth_logo_glow),
            contentDescription = null,
            modifier = Modifier.requiredSize(
                width = LogoWidth + LogoBlurRadius * 2,
                height = LogoHeight + LogoBlurRadius * 2,
            ),
        )
        Image(
            painter = painterResource(R.drawable.auth_logo),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = TorusOffsetX)
                .size(TorusWidth, TorusHeight),
        )
    }
}

/** Logo, title and subtitle — Figma `Frame 143`, a 328-wide centred column. */
@Composable
internal fun AuthHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(328.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AuthLogo()

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = OmniAuthHeading,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OmniBody,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * One of the pill inputs.
 *
 * The design only ever shows the empty state, so the placeholder is what you see at rest; the field
 * is a real [BasicTextField] underneath so the screens are usable rather than a picture of a form.
 * Leading icon sizes and the gap after them differ per field in the source, hence the parameters.
 */
@Composable
internal fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    @DrawableRes icon: Int,
    iconSize: Dp,
    iconGap: Dp,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FieldHeight)
            .clip(RoundedCornerShape(FieldCorner))
            .background(OmniFieldSurface)
            .padding(
                start = FieldStartPadding,
                end = if (trailing == null) FieldStartPadding else FieldEndPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
        )

        Spacer(Modifier.width(iconGap))

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.titleMedium.copy(color = OmniInk),
            singleLine = true,
            cursorBrush = SolidColor(OmniInk),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.titleMedium,
                            color = OmniPlaceholder,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )

        trailing?.invoke()
    }
}

/** The dark call-to-action at the bottom of the form — Figma `Frame 130`. */
@Composable
internal fun AuthPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .clip(RoundedCornerShape(ButtonCorner))
            .background(OmniInk)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = OmniOnInk,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Everything below the button: the "or" rule, the two social buttons, and the cross-link to the
 * other auth screen — Figma `Frame 165`.
 *
 * [innerWidth] is the only thing that differs between the two frames (227 on sign up, 225 on sign
 * in); it is the width of the column holding the social buttons and the footer sentence.
 */
@Composable
internal fun AuthFooter(
    prompt: String,
    action: String,
    onAction: () -> Unit,
    innerWidth: Dp,
    modifier: Modifier = Modifier,
    onGoogle: () -> Unit = {},
    onFacebook: () -> Unit = {},
) {
    Column(
        modifier = modifier.width(FooterWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The source gives each rule a fixed 169.003 width, which together with the 6px gaps and
            // the 14-wide "or" adds up to exactly the footer width. Weighting them instead keeps that
            // sum exact even though the stand-in body font measures "or" slightly differently.
            DividerLine(Modifier.weight(1f))
            Text(
                text = "or",
                style = MaterialTheme.typography.labelMedium,
                color = OmniInk,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
            DividerLine(Modifier.weight(1f))
        }

        Column(
            modifier = Modifier.width(innerWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // `Frame 151` is 102.5 wide but its two 35px buttons only span 98, which nudges them
            // fractionally left of the column's centre. Keeping the frame reproduces that.
            Box(modifier = Modifier.width(SocialRowWidth)) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    SocialButton(R.drawable.ic_auth_google, "Continue with Google", onGoogle)
                    SocialButton(R.drawable.ic_auth_facebook, "Continue with Facebook", onFacebook)
                }
            }
        }

        // Deliberately outside the `innerWidth` column above. Figma sizes this row to the prompt as
        // Satoshi measures it; the stand-in body font is wider, so inside a 225 column the action
        // link was squeezed to a few pixels and broke one letter per line. It gets the footer's full
        // width, and both halves refuse to wrap.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = OmniMuted,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = action,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = OmniAuthLink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
    }
}

@Composable
private fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(OmniDivider),
    )
}

@Composable
private fun SocialButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Image(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = Modifier
            .size(SocialButtonSize)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    )
}


