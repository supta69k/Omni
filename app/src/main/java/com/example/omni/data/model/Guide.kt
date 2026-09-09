package com.example.omni.data.model

/**
 * One offline first-aid guide — the whole Phase 7 content model (BACKEND_PLAN §7).
 *
 * The guides ship in the APK rather than Firestore, so a fresh install works in airplane mode. The
 * content is served from [`com.example.omni.data.repo.BundledGuideRepository`], which builds [Guide]s
 * from a small Kotlin DSL; this model is the shape both the bundle and the UI resolve to.
 *
 * [steps] are ordered by `order`. [severity] drives the list screen's pill colour; [warnings] are the
 * "call an ambulance if…" sentences, read in the order the presenter wants them shown.
 */
data class Guide(
    val id: String,
    val title: String,
    val category: String,
    val severity: GuideSeverity,
    val intro: String,
    val steps: List<GuideStep>,
    val warnings: List<String>,
)

/**
 * How urgent a guide is. Drives the list pill's colour, and therefore the call site's emphasis:
 * the "call an ambulance if…" line is shown on the detail screen regardless, but an
 * [GuideSeverity.Emergency] guide leads with it rather than trailing it.
 */
enum class GuideSeverity {
    /** CPR, choking, anaphylaxis, severe bleeding — start now, and call first. */
    Emergency,

    /** Burns, stroke, heart attack, seizures — get care promptly. */
    Urgent,

    /** Minor wounds, heat, or anything where self-care is the point. */
    Minor,
}

/**
 * One instruction in a guide. [audioHint] names a system sound the guide reads when the user steps
 * through it; `null` plays none.
 */
data class GuideStep(
    val order: Int,
    val title: String,
    val detail: String,
    val audioHint: String? = null,
)
