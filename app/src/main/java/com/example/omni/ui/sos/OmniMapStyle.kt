package com.example.omni.ui.sos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.omni.BuildConfig
import com.example.omni.R
import com.example.omni.ui.theme.OmniAlertRed
import com.example.omni.ui.theme.OmniAuthHeading
import com.example.omni.ui.theme.OmniBackground
import com.example.omni.ui.theme.OmniCallButton
import com.example.omni.ui.theme.OmniHeroPink
import com.example.omni.ui.theme.OmniInk
import com.example.omni.ui.theme.OmniScrim
import com.example.omni.ui.theme.OmniSheetSubtitle

/**
 * Where the map's tiles, style and key come from (BACKEND_PLAN §11 Phase 9, "The map").
 *
 * The key is read from `local.properties` through `BuildConfig` — it is never in a resource, never in
 * the manifest, and never committed. `local.properties` is gitignored; a checkout without one still
 * builds, and lands on [hasKey] `false`, which the screen draws as a named error rather than a blank
 * grey rectangle.
 *
 * The style itself is **bundled**, not fetched from MapTiler's gallery. The design asks for Omni's own
 * palette on the map — white land, the hero pink on primary roads, the call button's teal on the side
 * streets — and a hosted style would be somebody else's colours. `MAPTILER_STYLE_URL` is still honoured
 * so a hosted style can be swapped in from `local.properties` without a code change; leaving it empty,
 * which is the default, means "use the bundled one".
 */
object OmniMapConfig {

    /** False on a checkout with no `MAPTILER_KEY`, which is a state the screen names out loud. */
    val hasKey: Boolean get() = BuildConfig.MAPTILER_KEY.isNotBlank()

    /**
     * A hosted style to use instead of the bundled one, with the key appended if it needs one.
     * `null` — the default — means the bundled style.
     */
    val styleUrl: String?
        get() {
            val configured = BuildConfig.MAPTILER_STYLE_URL.takeIf { it.isNotBlank() } ?: return null
            if (configured.contains("key=")) return configured
            val separator = if (configured.contains('?')) '&' else '?'
            return "$configured${separator}key=${BuildConfig.MAPTILER_KEY}"
        }

    /**
     * The bundled style JSON with its `{MAPTILER_KEY}` placeholders filled in.
     *
     * The placeholder exists so the checked-in file holds no secret: `res/raw/omni_map_style.json` is
     * committed, the key that completes it is not.
     */
    fun styleJson(context: Context): String =
        context.resources.openRawResource(R.raw.omni_map_style)
            .bufferedReader()
            .use { it.readText() }
            .replace(KeyPlaceholder, BuildConfig.MAPTILER_KEY)

    private const val KeyPlaceholder = "{MAPTILER_KEY}"
}

// ---- Source and layer ids ----------------------------------------------------------------------
//
// One namespace, so a layer of Omni's can never be confused with one of the vector style's.

internal const val HospitalSourceId = "omni-hospitals"
internal const val RouteSourceId = "omni-route"
internal const val UserSourceId = "omni-user"

internal const val RouteCasingLayerId = "omni-route-casing"
internal const val RouteLayerId = "omni-route-line"
internal const val UserHaloLayerId = "omni-user-halo"
internal const val UserDotLayerId = "omni-user-dot"
internal const val HospitalLayerId = "omni-hospital"
internal const val NearestLayerId = "omni-hospital-nearest"
internal const val SelectedLayerId = "omni-hospital-selected"
internal const val PharmacyLayerId = "omni-pharmacy"
internal const val PharmacyNearestLayerId = "omni-pharmacy-nearest"
internal const val PharmacySelectedLayerId = "omni-pharmacy-selected"

/** The six images the pin layers draw — three states for each half of the directory. */
internal const val PinImagePlain = "omni-pin"
internal const val PinImageNearest = "omni-pin-nearest"
internal const val PinImageSelected = "omni-pin-selected"
internal const val PinImagePharmacyPlain = "omni-pin-pharmacy"
internal const val PinImagePharmacyNearest = "omni-pin-pharmacy-nearest"
internal const val PinImagePharmacySelected = "omni-pin-pharmacy-selected"

/** The feature properties the pin layers filter on: state × kind, six layers over one source. */
internal const val StateProperty = "state"
internal const val StatePlain = "plain"
internal const val StateNearest = "nearest"
internal const val StateSelected = "selected"

internal const val KindProperty = "kind"
internal const val KindHospital = "hospital"
internal const val KindPharmacy = "pharmacy"

/** The rest of what each hospital feature carries. */
internal const val IdProperty = "id"
internal const val LabelProperty = "label"

// ---- The pins ----------------------------------------------------------------------------------

/**
 * The three map pins, drawn in code rather than exported.
 *
 * Drawn because the design's pins are *stateful*: the same teardrop appears plain, as the nearest and
 * as the selected one, at three sizes and in three colours, and shipping nine PNGs (three states × 3x)
 * to express what is one shape and a palette lookup would put the colours somewhere
 * [com.example.omni.ui.theme] cannot see them. Everything below reads from the theme, so re-tinting the
 * app re-tints the map.
 *
 * Sizes are in *device* pixels, taken from the real display metrics — deliberately not from
 * `LocalDensity`, which inside [com.example.omni.ui.DesignFrame] is the artboard's scaled density and
 * would hand MapLibre a number 5% off.
 */
internal object OmniMapPins {

    /**
     * @param state one of [StatePlain], [StateNearest], [StateSelected].
     * @param pharmacy true for the pharmacy half of the directory — the same teardrop in the call
     *   button's teal, carrying a "P". The fill, not the shape, is the legend: both halves of the
     *   directory are medical facilities, and the map already teaches teal as Omni's own colour.
     */
    fun bitmap(context: Context, state: String, pharmacy: Boolean = false): Bitmap {
        val density = context.resources.displayMetrics.density
        val glyphChar = if (pharmacy) "P" else "H"
        val spec = when (state) {
            StateSelected -> PinSpec(
                widthDp = 40f,
                heightDp = 52f,
                fill = OmniInk,
                halo = OmniCallButton,
                glyph = OmniBackground,
                glyphText = glyphChar,
            )
            StateNearest -> PinSpec(
                widthDp = 38f,
                heightDp = 49f,
                fill = if (pharmacy) OmniCallButton else OmniAlertRed,
                halo = OmniHeroPink,
                glyph = OmniBackground,
                glyphText = glyphChar,
            )
            else -> PinSpec(
                widthDp = 30f,
                heightDp = 39f,
                fill = if (pharmacy) OmniCallButton else OmniAlertRed,
                halo = null,
                glyph = OmniBackground,
                glyphText = glyphChar,
            )
        }
        return spec.render(density)
    }

    private data class PinSpec(
        val widthDp: Float,
        val heightDp: Float,
        val fill: Color,
        /** The ring around the head that says "this one" — absent on a plain pin. */
        val halo: Color?,
        val glyph: Color,
        /** The letter in the head: "H" for hospital, "P" for pharmacy. */
        val glyphText: String,
    ) {
        fun render(density: Float): Bitmap {
            // The shadow needs room to fall outside the shape, or it is clipped into a hard edge.
            val pad = ShadowPadDp * density
            val w = widthDp * density
            val h = heightDp * density
            val bitmap = Bitmap.createBitmap(
                (w + pad * 2).toInt(),
                (h + pad * 2).toInt(),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bitmap)
            canvas.translate(pad, pad)

            val stroke = StrokeDp * density
            val cx = w / 2f
            val headRadius = cx - stroke

            if (halo != null) {
                // The "chosen" ring: a soft disc behind the head, wider than the white outline, so the
                // eye finds the nearest and the selected pin before it reads any label.
                canvas.drawCircle(
                    cx,
                    cx,
                    headRadius + HaloDp * density,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = halo.toArgb()
                        alpha = HaloAlpha
                    },
                )
            }

            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fill.toArgb()
                setShadowLayer(pad * 0.6f, 0f, pad * 0.25f, OmniScrim.toArgb())
            }
            val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = OmniBackground.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = stroke
            }

            val path = teardrop(w, h, stroke)
            canvas.drawPath(path, body)
            canvas.drawPath(path, outline)

            // "H" for hospital, "P" for pharmacy — the design's own marker glyph, and the one symbol
            // that reads at 30dp without a legend.
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = glyph.toArgb()
                textAlign = Paint.Align.CENTER
                textSize = headRadius * GlyphScale
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val baseline = cx - (label.descent() + label.ascent()) / 2f
            canvas.drawText(glyphText, cx, baseline, label)

            return bitmap
        }

        /**
         * A map pin: a near-full circle for the head, then two curves converging on the point.
         *
         * The arc runs from the bottom-left shoulder (135°) clockwise through the top and back to the
         * bottom-right (45°), which leaves exactly the gap the two flanks close.
         */
        private fun teardrop(w: Float, h: Float, stroke: Float): Path {
            val cx = w / 2f
            val cy = w / 2f
            val r = cx - stroke
            val tipY = h - stroke / 2f
            val shoulder = r * ShoulderCos

            return Path().apply {
                addArc(RectF(cx - r, cy - r, cx + r, cy + r), 135f, 270f)
                quadTo(cx + shoulder * FlankPull, cy + (tipY - cy) * FlankRise, cx, tipY)
                quadTo(cx - shoulder * FlankPull, cy + (tipY - cy) * FlankRise, cx - shoulder, cy + shoulder)
                close()
            }
        }
    }

    /** cos(45°) — where the arc leaves off and the flanks pick up. */
    private const val ShoulderCos = 0.7071f

    /** How far out the flanks bow before converging, and how far down they hold that width. */
    private const val FlankPull = 1.25f
    private const val FlankRise = 0.45f

    private const val StrokeDp = 2.4f
    private const val HaloDp = 4.5f
    private const val HaloAlpha = 235

    /** Room for the drop shadow to fall outside the pin's own box. */
    private const val ShadowPadDp = 3f

    /** The "H" against the head's radius — big enough to read, small enough to keep its margin. */
    private const val GlyphScale = 1.18f
}

// ---- Layer paint -------------------------------------------------------------------------------
//
// The map's own colours, kept beside the pins so the whole overlay is repainted from one place.

/** The drawn route — the app's purple, the same one the feed timestamps and the day chip use. */
internal val RouteColor = OmniAuthHeading

/** A white sleeve under the route, so the line stays legible over pink roads and blue water. */
internal val RouteCasingColor = OmniBackground

/** "You are here" — the same purple as the route it starts from. */
internal val UserDotColor = OmniAuthHeading
internal val UserDotRingColor = OmniBackground
internal val UserHaloColor = OmniAuthHeading

/** Pin labels: ink for the two that matter, the sheet's grey for the rest. */
internal val PinLabelColor = OmniSheetSubtitle
internal val PinLabelStrongColor = OmniInk
internal val PinLabelHaloColor = OmniBackground
