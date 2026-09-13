package com.example.omni.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.User
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The four values the account page edits, as the page holds them: all `String`, all exactly what is in
 * the fields.
 *
 * The birthday is three boxes rather than one date, and rather than a calendar. A calendar that has to
 * reach 1994 from today is around four hundred taps of a month arrow, and one combined field would have
 * to decide whether `03/04/1994` is March or April — which is a different answer in Dhaka than in
 * Denver. Three labelled boxes have neither problem, and the ISO string they are assembled into is the
 * one thing Firestore ever sees.
 *
 * Blank in all three is a legitimate answer: an account that has never been asked for a birthday is not
 * an account with an invalid one.
 */
data class PersonalDraft(
    val name: String = "",
    val day: String = "",
    val month: String = "",
    val year: String = "",
    val gender: String = "",
) {
    /** True when all three boxes are empty — "not answered", which saves as a cleared field. */
    val dobEmpty: Boolean get() = day.isBlank() && month.isBlank() && year.isBlank()

    /**
     * The birthday as `yyyy-MM-dd`, `""` when the boxes are empty, `null` when they do not form a date
     * somebody could have been born on.
     *
     * `LocalDate.of` is what decides, not a digit count: it is the only check that knows February has 28
     * days in 2025 and 29 in 2024, and a hand-rolled one would let 31/02 through.
     */
    val dobIso: String?
        get() {
            if (dobEmpty) return ""
            val d = day.toIntOrNull() ?: return null
            val m = month.toIntOrNull() ?: return null
            val y = year.toIntOrNull() ?: return null
            if (y < EarliestBirthYear) return null
            val date = runCatching { LocalDate.of(y, m, d) }.getOrNull() ?: return null
            // A birthday in the future is the one wrong date that is also a real one, so it is refused
            // here rather than by LocalDate.
            return if (date.isAfter(LocalDate.now())) null else date.toString()
        }

    val nameValid: Boolean get() = name.trim().length >= MinNameLength
    val dobValid: Boolean get() = dobIso != null

    private companion object {
        /** Old enough that no living account holder predates it, young enough to catch a typo'd year. */
        const val EarliestBirthYear = 1900
        const val MinNameLength = 2
    }
}

/**
 * The personal-information page's state.
 *
 * [draft] is what the fields show and what Save would write; [stored] is what `users/{uid}` currently
 * says. Both, because that is what makes [dirty] answerable — and [dirty] is the page's whole
 * interaction model, exactly as it is on [GoalsViewModel]: the button is live only while the two
 * disagree, and it settles back to "Saved" by itself when Firestore echoes the write.
 */
data class PersonalInfoUiState(
    val draft: PersonalDraft = PersonalDraft(),
    val stored: PersonalDraft = PersonalDraft(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    /** The last attempt's failure sentence, shown above the button until the next one. */
    val error: String? = null,
) {
    val dirty: Boolean get() = draft != stored

    /** What the button says about itself — and the same condition that decides whether it responds. */
    val canSave: Boolean
        get() = !loading && !saving && dirty && draft.nameValid && draft.dobValid
}

/**
 * Name, date of birth and gender — the `users/{uid}` document's own top-level fields.
 *
 * Reads [UserRepository] rather than Auth for all three, because all three are profile data: the display
 * name in the header, the feed's author row and the settings row are the same string this page writes,
 * so a save moves them on the frame Firestore's listener fires. Credentials are the other page's job —
 * see [SecurityViewModel], which is the split the Settings group's two rows now describe.
 */
class PersonalInformationViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val uid: StateFlow<String?> = authRepository.sessionUid

    /** The profile as the page would fill its boxes, or `null` until the listener has said anything. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val stored: StateFlow<PersonalDraft?> = uid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(PersonalDraft())
            } else {
                userRepository.observeUser(uid)
                    .map { it?.toDraft() ?: PersonalDraft() }
                    .catch { cause ->
                        Log.w("Omni", "users/$uid could not be read for its personal details", cause)
                        emit(PersonalDraft())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(ListenerGraceMillis), null)

    /**
     * `null` until the first keystroke.
     *
     * Before that the page simply mirrors the profile, so the listener's first emission *is* the initial
     * value and there is nothing to seed and nothing to race. After an edit the draft owns the fields and
     * a snapshot can no longer overwrite what is under the caret — including the echo of this page's own
     * write, which arrives as an ordinary snapshot a moment after Save. [GoalsViewModel] makes the same
     * bargain for the same reason.
     */
    private val draft = MutableStateFlow<PersonalDraft?>(null)
    private val saving = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PersonalInfoUiState> =
        combine(stored, draft, saving, error) { stored, draft, saving, error ->
            PersonalInfoUiState(
                draft = draft ?: stored ?: PersonalDraft(),
                stored = stored ?: PersonalDraft(),
                loading = stored == null,
                saving = saving,
                error = error,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = PersonalInfoUiState(),
        )

    fun onNameChange(value: String) = edit { it.copy(name = value) }

    /**
     * The three date boxes, each capped at its own width.
     *
     * Filtered to digits and clipped on the way in rather than validated on the way out: a box that
     * simply cannot hold "1st" or a fifth digit is one fewer error message the page has to write, and the
     * cap is what stops a stuck key from pushing the year out of its box (`UI_ARCHITECTURE.md` §6 rule 8).
     */
    fun onDayChange(value: String) = edit { it.copy(day = value.digits(DayDigits)) }

    fun onMonthChange(value: String) = edit { it.copy(month = value.digits(MonthDigits)) }

    fun onYearChange(value: String) = edit { it.copy(year = value.digits(YearDigits)) }

    /** Tapping the chip that is already chosen clears it — "prefer not to say" without a fifth chip. */
    fun onGenderChange(value: String) = edit {
        it.copy(gender = if (it.gender.equals(value, ignoreCase = true)) "" else value)
    }

    /**
     * Writes all three fields, once.
     *
     * Blank is written as `""` rather than dropped, so clearing a birthday actually clears it — the
     * mapper reads blank back as absent, which is the same rule the photo URL has followed since it was
     * first written (see `FirestoreUserRepository.toUser`).
     *
     * A failure leaves the draft alone: the page still shows what was typed, the button goes back to
     * offering the save, and the sentence above it says why. Offline is not a failure — Firestore applies
     * the write to its cache and re-fires the listener before the round-trip, so the button reads "Saved"
     * in airplane mode too, which is the truth about what the app will do.
     */
    fun save() {
        val state = uiState.value
        if (!state.canSave) return
        val uid = authRepository.currentUid ?: return
        val dob = state.draft.dobIso ?: return

        saving.value = true
        error.value = null
        viewModelScope.launch {
            try {
                userRepository.updateProfile(
                    uid = uid,
                    fields = mapOf(
                        "name" to state.draft.name.trim(),
                        "dob" to dob,
                        "gender" to state.draft.gender.trim(),
                    ),
                )
                // Hand the fields back to the profile stream. The draft held what was *typed* — an
                // untrimmed name, a day keyed as "5" — while the snapshot echo carries the *normalised*
                // form the mapper produces ("05", trimmed). Left in place the two never compare equal, so
                // [PersonalInfoUiState.dirty] would stay true and the button would sit on "Save Changes"
                // forever after a save that actually landed. Dropping the draft mirrors [discard]: the
                // page now shows `stored`, which the echo has already made authoritative.
                draft.value = null
            } catch (cause: Exception) {
                Log.w("Omni", "The personal details for $uid could not be saved", cause)
                error.value = "Your details could not be saved — check your connection and try again."
            } finally {
                saving.value = false
            }
        }
    }

    /**
     * Throws the unsaved edits away.
     *
     * This ViewModel lives on the Activity's store, so leaving the page and coming back does *not* forget
     * a draft — the right default, since an accidental back press should not cost the edit, but it does
     * mean an abandoned one would sit there looking like a saved profile. This is the way out, and the
     * reason the screen offers it only while [PersonalInfoUiState.dirty] is true.
     */
    fun discard() {
        draft.value = null
        error.value = null
    }

    private fun edit(change: (PersonalDraft) -> PersonalDraft) {
        val base = draft.value ?: stored.value ?: return
        draft.value = change(base)
        // The failure line belongs to one attempt. Leaving it up while the fields change would have the
        // page explaining a save the user has already moved past.
        error.value = null
    }

    private companion object {
        /** Matches the other settings pages: a rotation keeps the listener, a backgrounded app does not. */
        const val ListenerGraceMillis = 5_000L

        const val DayDigits = 2
        const val MonthDigits = 2
        const val YearDigits = 4
    }
}

/** The profile as the page's five boxes — the birthday split back out into three. */
private fun User.toDraft(): PersonalDraft {
    val birthday = dob?.let {
        runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    }
    return PersonalDraft(
        name = name,
        // Zero-padded, so a saved birthday and a freshly typed one are the same string and the page does
        // not read as dirty the moment it loads.
        day = birthday?.let { "%02d".format(Locale.US, it.dayOfMonth) }.orEmpty(),
        month = birthday?.let { "%02d".format(Locale.US, it.monthValue) }.orEmpty(),
        year = birthday?.year?.toString().orEmpty(),
        gender = gender.orEmpty(),
    )
}

/** Digits only, at most [max] of them. */
private fun String.digits(max: Int): String = filter { it.isDigit() }.take(max)

/**
 * The chips the gender row offers.
 *
 * A short list plus nothing-chosen rather than a free text field: the value is only ever read back onto
 * this page, so an open box would collect variants of the same four answers with no way to use any of
 * them. Deselecting the chosen chip is how "prefer not to say" is said, which is why there is no fifth
 * chip claiming to mean it.
 */
val GenderOptions = listOf("Male", "Female", "Non-binary", "Other")
