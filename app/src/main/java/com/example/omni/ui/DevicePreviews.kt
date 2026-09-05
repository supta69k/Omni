package com.example.omni.ui

import androidx.compose.ui.tooling.preview.Preview

/**
 * The standard preview set for a top-level screen.
 *
 * Annotate a screen's `@Composable` preview with `@DevicePreviews` instead of stacking `@Preview`
 * lines by hand. It renders the screen at four widths — the 415 design artboard plus the small (360),
 * medium (393) and large (412) phone widths the project targets — and once at `fontScale = 1.5` to
 * prove the pixel-locked layout no longer breaks when the system font is enlarged (see [DesignFrame],
 * which pins `fontScale` to 1). Because every screen is laid out inside [DesignFrame], all five panes
 * should look proportionally identical; a pane that differs is the signal that something bypassed the
 * frame or introduced a device-width assumption.
 *
 * This is the one approved multi-width preview pattern. Do not invent per-file preview matrices.
 */
@Preview(name = "Design 415", showBackground = true, widthDp = 415, heightDp = 920)
@Preview(name = "Small 360", showBackground = true, widthDp = 360, heightDp = 800)
@Preview(name = "Medium 393", showBackground = true, widthDp = 393, heightDp = 851)
@Preview(name = "Large 412", showBackground = true, widthDp = 412, heightDp = 892)
@Preview(name = "Font 1.5x", showBackground = true, widthDp = 393, heightDp = 851, fontScale = 1.5f)
annotation class DevicePreviews
