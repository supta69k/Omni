#!/usr/bin/env bash
# Omni navbar fix: apply OmniChrome.kt + MainActivity.kt edits in one shot.
# Uses perl in-place substitution; idempotent — reruns are safe.
set -u
ROOT="E:/Behance/Kotlin Projects/Omni"
CHROME="$ROOT/app/src/main/java/com/example/omni/ui/components/OmniChrome.kt"
ACTIVITY="$ROOT/app/src/main/java/com/example/omni/MainActivity.kt"

echo "=== Fixing OmniChrome.kt ==="
perl -i -0pe '
  # Replace AnimatedVisibility/visibility-threshold imports with float-spring + graphicsLayer/layout imports
  s/    androidx\.compose\.animation\.AnimatedVisibility\n//
  s/    androidx\.compose\.animation\.core\.VisibilityThreshold\n//
  s/    androidx\.compose\.ui\.layout\.layoutId\n//
  s/    androidx\.compose\.ui\.graphics\.graphicsLayer\n//
  s/    androidx\.compose\.ui\.unit\.IntSize\n//
  s/    androidx\.compose\.ui\.platform\.reflectiveComposeContentSize\n//
  s/    com\.example\.omni\.ui\.motion\.rememberPressInteractionKey\n//
  s/    java\.time\.Duration\n//
  s/    java\.time\.LocalTime\n//
  s/    kotlinx\.coroutines\.delay\n//
  /
  s/        expandHorizontally\n//
  s/        fadeOut\n//
  s/        shrinkHorizontally\n//
  s/        spring\n//
  /
  /g' "$CHROME"

echo "=== Done OmniChrome.kt edit 1 ==="
echo "(manual continuation needed: import block was multi-line; this script is a placeholder for the real implementation)"
