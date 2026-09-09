package com.example.omni.data.repo

import com.example.omni.data.model.Guide
import com.example.omni.data.model.GuideSeverity
import com.example.omni.data.model.GuideStep

/**
 * The bundled first-aid guides — grounded in the APK, not Firestore (BACKEND_PLAN §7).
 *
 * Every guide is compiled into the package as [Guide]s and served from memory by [list] and [byId], so
 * a fresh install with the device in airplane mode — the phase's acceptance test — opens every guide
 * with no fetch, no cache-warming run and no failure state. There is deliberately no `Flow`: the
 * content cannot change under the user, so exposing it as a cold `Flow` would only add collect-and-
 * settle latency to a screen whose data is already present.
 *
 * Per-user progress is a separate concern and lives in [GuideProgressRepository]; this repository owns
 * what is true for everyone.
 */
interface GuideRepository {

    /** Every guide in the bundle, in the order the user should see them. */
    fun list(): List<Guide>

    /** One guide by id, or `null` if the id is not in the bundle. */
    fun byId(id: String): Guide?

    /**
     * How many steps there are across every guide — the second half of the list screen's "7 guides ·
     * 26 steps" line.
     *
     * Here rather than at the call site so the screen never sums a list it has just been handed; one
     * guide's own step count is `guide.steps.size`, which needs no repository at all.
     */
    val totalSteps: Int
}

/**
 * The in-memory source of truth for the guide content, built from a small DSL so the copy keeps its
 * shape and the author does not have to write (or maintain) a JSON parser at runtime.
 */
object BundledGuideRepository : GuideRepository {

    private data class StepBlueprint(
        val title: String,
        val detail: String,
        val audioHint: String? = null,
    )

    private data class GuideBlueprint(
        val id: String,
        val title: String,
        val category: String,
        val severity: GuideSeverity,
        val intro: String,
        val steps: List<StepBlueprint>,
        val warnings: List<String>,
    )

    override fun list(): List<Guide> = guides.map { it.toGuide() }

    override fun byId(id: String): Guide? = guides.firstOrNull { it.id == id }?.toGuide()

    override val totalSteps: Int get() = guides.sumOf { it.steps.size }

    private fun GuideBlueprint.toGuide(): Guide = Guide(
        id = id,
        title = title,
        category = category,
        severity = severity,
        intro = intro,
        steps = steps.mapIndexed { index, step -> GuideStep(index + 1, step.title, step.detail, step.audioHint) },
        warnings = warnings,
    )

    private fun guide(
        id: String,
        title: String,
        category: String,
        severity: GuideSeverity,
        intro: String,
        steps: List<StepBlueprint>,
        warnings: List<String>,
    ) = GuideBlueprint(id, title, category, severity, intro, steps, warnings)

    private fun step(title: String, detail: String, audioHint: String? = null) =
        StepBlueprint(title, detail, audioHint)

    // The content is authored here. When a `guides.json` asset replaces it, this object goes away and
    // the two `*Blueprint` types with it — the [Guide]/[GuideStep] model above is what stays.
    private val guides: List<GuideBlueprint> = listOf(
        guide(
            id = "cpr",
            title = "CPR",
            category = "Cardiac",
            severity = GuideSeverity.Emergency,
            intro = "Cardiopulmonary resuscitation keeps blood flowing to the brain and heart of a person whose heart has stopped.",
            steps = listOf(
                step("Check and call", "Check the person is unresponsive and not breathing normally. Send someone to call an ambulance, or call yourself.", audioHint = "alarm"),
                step("Chest compressions", "Push hard and fast in the centre of the chest: 5–6 cm down, at 100–120 per minute. Let the chest spring back fully between pushes."),
                step("Two rescue breaths", "Tilt the head back, lift the chin, pinch the nose, and give two breaths. Then return to compressions.", audioHint = "beat"),
                step("Keep going", "Continue 30 compressions to 2 breaths until help arrives, an AED is ready, or the person shows a sign of life."),
            ),
            warnings = listOf("Call an ambulance if the person is unresponsive and not breathing normally."),
        ),
        guide(
            id = "choking",
            title = "Choking",
            category = "Airway",
            severity = GuideSeverity.Emergency,
            intro = "A blocked airway kills in minutes. The right response depends on whether the person can still cough, speak, or breathe.",
            steps = listOf(
                step("Mild — encourage coughing", "If they can cough, speak or breathe, do not hit their back. Encourage them to keep coughing while you watch."),
                step("Severe — five back blows", "If they cannot breathe, speak or cough, lean them forward and give up to five sharp blows between the shoulder blades."),
                step("Five abdominal thrusts", "Stand behind, wrap your arms around their waist, and pull inwards and upwards strongly, up to five times."),
                step("Repeat", "Alternate five back blows and five thrusts until the object comes out or they become unresponsive.", audioHint = "alarm"),
            ),
            warnings = listOf("If the person is unresponsive, lower them to the ground and start CPR while an ambulance is called."),
        ),
        guide(
            id = "bleeding",
            title = "Severe Bleeding",
            category = "Wounds",
            severity = GuideSeverity.Emergency,
            intro = "Direct pressure and elevation are the two fast treatments for a wound that is pouring blood.",
            steps = listOf(
                step("Press hard", "Press directly on the wound with a clean cloth, pad or your gloved hand. Hold without peeking."),
                step("Keep the pressure on", "If blood soaks through, add more material on top — do not lift the first pad, which breaks the clot."),
                step("Lay and elevate", "Lie the person down and raise the injured limb above heart level unless you suspect a fracture.", audioHint = "alarm"),
            ),
            warnings = listOf("Call an ambulance for any wound that pulses, or that keeps bleeding after ten minutes of firm pressure."),
        ),
        guide(
            id = "burns",
            title = "Burns",
            category = "Injury",
            severity = GuideSeverity.Urgent,
            intro = "Cooling the burn fast limits how deep it goes. The closer to cold running water, the better, within the first twenty minutes.",
            steps = listOf(
                step("Cool under water", "Hold the burn under cool running water for 20 minutes. Do not use ice — it makes the damage worse."),
                step("Remove tight things", "Take off rings, watches and clothing near the burn before it swells, as long as they are not stuck to it."),
                step("Cover lightly", "Lay a clean, non-fluffy dressing or cling film over the burn. Do not burst blisters or apply creams.", audioHint = "beat"),
            ),
            warnings = listOf("Seek medical help for any burn larger than the person's palm, or any burn to the face, hands, joints or genitals."),
        ),
        guide(
            id = "stroke",
            title = "Recognising a Stroke",
            category = "Medical",
            severity = GuideSeverity.Urgent,
            intro = "A stroke is a race against time. The FAST test picks up the signs most strokes show.",
            steps = listOf(
                step("Face", "Ask them to smile. Does one side of the face droop?"),
                step("Arms", "Ask them to raise both arms. Does one arm drift or fall?", audioHint = "beat"),
                step("Speech", "Ask them to repeat a sentence. Is their speech slurred or strange?"),
                step("Time", "If any one of these signs is new, call an ambulance immediately and note when they started."),
            ),
            warnings = listOf("Any one FAST sign — face, arm or speech — is a call-an-ambulance moment."),
        ),
        guide(
            id = "seizures",
            title = "Seizures",
            category = "Medical",
            severity = GuideSeverity.Urgent,
            intro = "The job during a seizure is to protect the person from harm, not to hold them still or put anything in their mouth.",
            steps = listOf(
                step("Cushion the head", "Ease them to the floor and put something soft under their head."),
                step("Clear the space", "Move furniture and hard objects away. Loosen anything tight around the neck.", audioHint = "alarm"),
                step("Time it", "Note the time the seizure starts. Look for a medical bracelet or epilepsy card."),
                step("After it", "Once it stops, roll them on their side and stay with them until they are fully awake."),
            ),
            warnings = listOf("Call an ambulance if a seizure lasts more than five minutes, repeats, or the person is injured, pregnant, or does not wake fully."),
        ),
        guide(
            id = "allergy",
            title = "Anaphylaxis",
            category = "Allergy",
            severity = GuideSeverity.Emergency,
            intro = "A sudden severe allergic reaction closes the airway. An auto-injector is the treatment, and it is never too early to use it.",
            steps = listOf(
                step("Use the auto-injector", "If the person has an adrenaline auto-injector, use it immediately — a jab into the outer thigh.", audioHint = "alarm"),
                step("Call an ambulance", "Call for an ambulance even if they seem to improve, because symptoms can come back."),
                step("Lie them flat", "Lie them down and raise the legs. If breathing is hard, sit them slightly upright."),
                step("Repeat if needed", "If there is no improvement in five minutes, use a second auto-injector if one is available."),
            ),
            warnings = listOf("Call an ambulance for any difficulty breathing, or any swelling of the face, lips or tongue, after an exposure."),
        ),
    )
}
