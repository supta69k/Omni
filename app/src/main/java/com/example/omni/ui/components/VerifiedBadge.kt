package com.example.omni.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.omni.R

/**
 * The one verified mark for the whole app — the feed's own #55A217 check-in-circle
 * (`ic_feed_badge_check`), so every surface that shows identity draws the same badge at the same
 * radius rather than each inlining its own [Image].
 *
 * It only *draws* the badge; it never decides who is verified. The caller guards it with the
 * authoritative verified state (`users/{uid}.verified`, mirrored onto `Doctor.verified` and
 * `Post.authorVerified`) — there is no separate verification flag anywhere in the app, and this
 * composable must not become one.
 */
@Composable
fun VerifiedBadge(
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    contentDescription: String? = "Verified",
) {
    Image(
        painter = painterResource(R.drawable.ic_feed_badge_check),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}
