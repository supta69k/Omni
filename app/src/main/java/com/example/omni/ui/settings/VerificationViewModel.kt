package com.example.omni.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.Profession
import com.example.omni.data.model.VerificationRequest
import com.example.omni.data.model.VerificationStatus
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.UserRepository
import com.example.omni.data.repo.VerificationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The apply-for-verification page's state.
 *
 * @property verified the account is already a listed professional — there is nothing here to submit,
 *   and the page says so rather than offering a form whose write the rules would reject anyway.
 * @property request the application on file, `null` when there has never been one.
 * @property profession the chooser's selection; `null` until one is picked, which is also what makes
 *   [canSubmit] false on a page nobody has touched.
 * @property error the last submission's failure sentence, cleared by the next edit.
 */
data class VerificationUiState(
    val loading: Boolean = true,
    val verified: Boolean = false,
    val request: VerificationRequest? = null,
    val profession: Profession? = null,
    val licenseNumber: String = "",
    val specialty: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
) {

    /**
     * Whether the form is the page's content at all.
     *
     * Three states share this page and only one of them is a form: a verified account has finished,
     * a pending application is somebody else's turn, and a rejected one is the applicant's again.
     * The rules say the same thing — a pending document may not be overwritten — so this is the
     * screen agreeing with the database rather than a second opinion about it.
     */
    val editable: Boolean
        get() = !verified && (request == null || request.status == VerificationStatus.REJECTED)

    val canSubmit: Boolean
        get() = editable && !submitting && profession != null &&
            licenseNumber.trim().length >= MinLicenceLength &&
            // Only a doctor application needs one: it is what the Omni+ directory lists. A
            // nutritionist's application has no directory tile to fill.
            (profession != Profession.DOCTOR || specialty.trim().length >= MinSpecialtyLength)

    companion object {
        /**
         * Short enough to accept a real registration number from any board, long enough that a
         * stray keystroke is not an application. The page never claims to validate the number
         * itself — only a human reviewer can, which is the entire reason this flow exists.
         */
        const val MinLicenceLength = 4

        /** Enough for the longest registration format; the field is one line and does not scroll. */
        const val MaxLicenceLength = 40

        /** "Eye" would technically clear a validator; a directory line needs two characters. */
        const val MinSpecialtyLength = 3

        /** The specialty line's cap, matching the licence field's one-line budget. */
        const val MaxSpecialtyLength = 40
    }
}

/**
 * Applying to be listed as a healthcare professional (Phase 12).
 *
 * The client's half is one write. Approval sets `verified` and `role` on the profile, and the rules
 * forbid the phone from touching either — so this ViewModel can *ask*, watch, and nothing else. It
 * reads the profile only for the applicant's name (denormalised onto the request so a reviewer sees
 * who they are approving without a second read) and for the `verified` flag that ends the flow.
 */
class VerificationViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val verificationRepository: VerificationRepository,
) : ViewModel() {

    private val uid: StateFlow<String?> = authRepository.sessionUid

    @OptIn(ExperimentalCoroutinesApi::class)
    private val request: Flow<VerificationRequest?> = uid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(null)
        } else {
            verificationRepository.observeRequest(uid).catch { cause ->
                Log.w("Omni", "The verification request for $uid could not be read", cause)
                emit(null)
            }
        }
    }

    /**
     * The applicant's name and whether they are already listed — the two things the profile owns.
     *
     * The name is also latched into [latestName] as it passes, through [onEach] on this flow rather
     * than a collector of its own: the flow is folded into [uiState], so the latch costs no second
     * `users/{uid}` listener and stops the moment the page does.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val profile: Flow<Pair<String, Boolean>> = uid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf("" to false)
        } else {
            userRepository.observeUser(uid)
                .map { (it?.name.orEmpty()) to (it?.verified == true) }
                .catch { cause ->
                    Log.w("Omni", "users/$uid could not be read for the verification form", cause)
                    emit("" to false)
                }
        }
    }.onEach { (name, _) -> latestName = name }

    /** The name the profile listener last carried — see [profile], and [submit] for why it exists. */
    @Volatile
    private var latestName: String = ""

    /**
     * The form's own two values, `null` until the page is touched.
     *
     * Held apart from the listener for [GoalsViewModel]'s reason: once there is a draft it must own
     * the field, or the snapshot echoing the submission back would clear what is being typed. A
     * rejected application seeds them, so re-applying starts from what was sent rather than from an
     * empty form the applicant has to retype.
     */
    private val draft = MutableStateFlow<Draft?>(null)
    private val submitting = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<VerificationUiState> =
        combine(request, profile, draft, submitting, error) { request, profile, draft, busy, failure ->
            val (_, verified) = profile
            VerificationUiState(
                loading = false,
                verified = verified,
                request = request,
                profession = draft?.profession ?: request?.profession,
                licenseNumber = draft?.licenseNumber ?: request?.licenseNumber.orEmpty(),
                specialty = draft?.specialty ?: request?.specialty.orEmpty(),
                submitting = busy,
                error = failure,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = VerificationUiState(),
        )

    fun onProfessionChange(value: Profession) {
        draft.value = current().copy(profession = value)
        error.value = null
    }

    fun onLicenseNumberChange(value: String) {
        draft.value = current().copy(
            licenseNumber = value.take(VerificationUiState.MaxLicenceLength),
        )
        error.value = null
    }

    fun onSpecialtyChange(value: String) {
        draft.value = current().copy(
            specialty = value.take(VerificationUiState.MaxSpecialtyLength),
        )
        error.value = null
    }

    /**
     * Sends the application.
     *
     * The name travels with it, read off the profile at the moment of sending rather than typed:
     * a reviewer comparing a registration number against a certificate needs the name on the
     * account, and letting the applicant type a different one here would be the one field in the
     * form that could be used to impersonate somebody.
     *
     * The draft is cleared on success so the page falls back to the listener — which by then is
     * carrying the pending application, and that is what turns the form into the "under review"
     * card without this ViewModel deciding it had.
     */
    fun submit() {
        val state = uiState.value
        val profession = state.profession ?: return
        if (!state.canSubmit) return
        val uid = authRepository.currentUid ?: return

        submitting.value = true
        error.value = null
        viewModelScope.launch {
            try {
                verificationRepository.submit(
                    uid = uid,
                    // The profile's name, never a typed one — see this method's KDoc. A rejected
                    // application's name is the fallback for the moment before the listener answers.
                    name = latestName.ifBlank { state.request?.name.orEmpty() },
                    profession = profession,
                    licenseNumber = state.licenseNumber.trim(),
                    specialty = state.specialty.trim(),
                )
                draft.value = null
            } catch (cause: Exception) {
                Log.w("Omni", "Submitting the verification request failed", cause)
                error.value = cause.message?.takeIf { it.isNotBlank() }
                    ?.let { "Your application could not be sent — $it" }
                    ?: "Your application could not be sent — check your connection and try again."
            } finally {
                submitting.value = false
            }
        }
    }

    private fun current(): Draft = draft.value ?: Draft(
        profession = uiState.value.profession,
        licenseNumber = uiState.value.licenseNumber,
        specialty = uiState.value.specialty,
    )

    /** The form's editable values, together — see [draft] for why they are not read-through. */
    private data class Draft(
        val profession: Profession? = null,
        val licenseNumber: String = "",
        val specialty: String = "",
    )

    private companion object {
        const val ListenerGraceMillis = 5_000L
    }
}
