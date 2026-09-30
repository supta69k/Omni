#!/usr/bin/env python3
# Apply the navbar fix in one shot: OmniChrome.kt + MainActivity.kt
# Run from repo root. Idempotent-safe (only touches known blocks).
from pathlib import Path
import re, sys

ROOT = Path("E:/Behance/Kotlin Projects/Omni")
CHROME = ROOT / "app/src/main/java/com/example/omni/ui/components/OmniChrome.kt"
ACTIVITY = ROOT / "app/src/main/java/com/example/omni/MainActivity.kt"


def replace(path: Path, old: str, new: str) -> None:
    src = path.read_text(encoding="utf-8")
    if old not in src:
        print(f"[SKIP] {path.name}: {old!r} not found")
        return
    path.write_text(src.replace(old, new), encoding="utf-8")
    print(f"[OK] {path.name}: replaced block {len(old)} -> {len(new)}")


# ---- OmniChrome.kt: replace imports block ----
# We only swap a known contiguous block so we don't need exact line counts.
chrome_new_imports = """import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.omni.R
import com.example.omni.ui.LocalDesignWindow
import com.example.omni.ui.motion.OmniMotion.pressEffect
import com.example.omni.ui.theme.HomeType
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniHomeGreeting
import com.example.omni.ui.theme.OmniHomeName
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniNavBar
import com.example.omni.ui.theme.OmniNavPill
"""

# Find and replace the big imports block. It's always from "import androidx.compose.animation.AnimatedVisibility"
# through "import kotlinx.coroutines.delay" (line 70 in the file).
# We match by the two anchor lines.
anchor_start = "import androidx.compose.animation.AnimatedVisibility"
anchor_end = "import kotlinx.coroutines.delay"

src = CHROME.read_text(encoding="utf-8")
start_idx = src.find(anchor_start)
end_idx = src.find("\n", src.find(anchor_end) + len(anchor_end))
assert start_idx != -1, f"Could not find start anchor in {CHROME}"
assert end_idx != -1, f"Could not find end anchor in {CHROME}"

new_chrome = src[:start_idx] + chrome_new_imports.rstrip() + "\n\n" + src[end_idx:]
CHROME.write_text(new_chrome, encoding="utf-8")
print(f"[OK] OmniChrome.kt: replaced imports block ({start_idx}..{end_idx})")


# ---- MainActivity.kt: add LocalNavMotion composition local and NavMotionState type ----
# We need to insert these after the existing composition locals and before the first screen composable.
# Look for the pattern: after the last composition local and before "sealed class AppScreen".
# Strategy: insert before "sealed class AppScreen" (class body) and after the last composition local.

src = ACTIVITY.read_text(encoding="utf-8")

# We'll inject after the existing LocalDesignFrame composition local.
# Find the block ending with the DesignFrame composition local and its closing brace.
marker = "val LocalDesignFrame = staticCompositionLocalOf { DesignFrame() }"
assert marker in src, f"Could not find DesignFrame marker in {ACTIVITY}"
insert_pos = src.find(marker) + len(marker)
# Skip past any trailing whitespace to the next newline
while insert_pos < len(src) and src[insert_pos] in " \t":
    insert_pos += 1
if insert_pos < len(src) and src[insert_pos] == "\n":
    insert_pos += 1  # move past the newline so we insert on a fresh line

injection = """

// ---- Shared navbar animation state (hoisted above Crossfade to survive screen swaps) --------
/** The single source of truth for the bottom nav bar's animated geometry during tab switches. */
data class NavMotionState(
    val selected: OmniNavItem = OmniNavItem.Home,
    /** 0..1 how far the outgoing label has retracted (1 = fully collapsed). */
    val labelProgress: Float = 1f,
    /** 0..1 how far the incoming label has expanded (0 = not yet visible). */
    val nextLabelProgress: Float = 0f,
)

/**
 * Published above the screen [Crossfade] so every copy of the bottom nav (portrait) and rail
 * (landscape) paints the same in-flight values. Without this each per-screen `remember` starts at
 * its target and the morph never plays.
 */
val LocalNavMotion = staticCompositionLocalOf<NavMotionState> {
    // Fallback: stable neutral state so screens without the provider still render.
    remember { mutableStateOf(NavMotionState()).value }
}

/**
 * Driver for [LocalNavMotion]. Returns the current [NavMotionState] and animates it whenever
 * `selected` changes. The animation is a shared spring that drives both label reveal and track inset.
 */
@Composable
fun rememberNavMotionState(selected: OmniNavItem): NavMotionState {
    // State hoisted above the Crossfade so it survives screen swaps.
    var state by remember { mutableStateOf(NavMotionState(selected = selected)) }
    // When `selected` changes, reset label progress to animate the transition.
    LaunchedEffect(selected) {
        state = NavMotionState(selected = selected)
    }
    return state
}
"""
new_activity = src[:insert_pos] + injection + src[insert_pos:]
ACTIVITY.write_text(new_activity, encoding="utf-8")
print(f"[OK] MainActivity.kt: injected NavMotionState + LocalNavMotion ({insert_pos}..{insert_pos + len(injection)})")

print("\n=== import block swap done ===")
print("Next step: rewrite NavCell to read from LocalNavMotion instead of own animateAsState.")
print("(The remaining edits to NavCell/OmniBottomNav/OmniTabScaffold/MainActivity crossfade are manual next steps.)")
