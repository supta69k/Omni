package com.example.omni.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.EmergencyContact
import com.example.omni.data.model.MaxContactNameLength
import com.example.omni.data.model.MaxContactPhoneLength
import com.example.omni.data.model.MaxContactRelationLength
import com.example.omni.data.model.MaxEmergencyContacts
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.EmergencyContactRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The half-typed contact.
 *
 * Held in the ViewModel rather than in the screen's `remember`, so a rotation mid-form does not throw
 * away a phone number somebody was copying off a fridge magnet.
 *
 * @property canSave a number is the whole point — a contact with a name and no number cannot be
 *   texted, and the model layer drops it on the way back in ([com.example.omni.data.model.toEmergencyContact]).
 */
data class ContactDraft(
    val name: String = "",
    val phone: String = "",
    val relation: String = "",
) {
    val canSave: Boolean get() = phone.isNotBlank()
}

/**
 * Everything the saved-emergencies page renders.
 *
 * @property loading true only until the listener's first emission; an empty list afterwards means the
 *   user genuinely has no contacts, which is a different sentence on screen.
 * @property atCapacity the [MaxEmergencyContacts] ceiling, computed here so the screen never has to
 *   know the number.
 */
data class EmergencyContactsUiState(
    val contacts: List<EmergencyContact> = emptyList(),
    val draft: ContactDraft = ContactDraft(),
    val formOpen: Boolean = false,
    val saving: Boolean = false,
    val loading: Boolean = true,
) {
    val atCapacity: Boolean get() = contacts.size >= MaxEmergencyContacts
}

/**
 * Saved emergency contacts — `users/{uid}/emergencyContacts` (BACKEND_PLAN §7, §11 Phase 9).
 *
 * The list the SOS screen texts. It is edited here and read there, which is why both this and
 * [com.example.omni.ui.sos.SosViewModel] take the same repository rather than one passing a list to the
 * other: two screens observing one collection is two listeners on the same cached documents, and
 * Firestore serves the second from the cache.
 */
class EmergencyContactsViewModel(
    private val authRepository: AuthRepository,
    private val contactRepository: EmergencyContactRepository,
) : ViewModel() {

    /** `null` whenever nobody is signed in — the key the read restarts on. */
    private val uid: StateFlow<String?> = authRepository.sessionUid

    private val draft = MutableStateFlow(ContactDraft())
    private val formOpen = MutableStateFlow(false)
    private val saving = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val contacts: Flow<List<EmergencyContact>> = uid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            contactRepository.observe(uid).catch { cause ->
                Log.w("Omni", "users/$uid/emergencyContacts could not be read", cause)
                emit(emptyList())
            }
        }
    }

    /**
     * `loading` is false on every emission and true only in the initial value: `combine` does not
     * produce anything until [contacts] has spoken once, so "the state object exists" and "the listener
     * has answered" are the same moment.
     */
    val uiState: StateFlow<EmergencyContactsUiState> =
        combine(contacts, draft, formOpen, saving) { contacts, draft, formOpen, saving ->
            EmergencyContactsUiState(
                contacts = contacts,
                draft = draft,
                formOpen = formOpen,
                saving = saving,
                loading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = EmergencyContactsUiState(),
        )

    /** Opens the form, unless the account is already holding as many contacts as it may. */
    fun openForm() {
        if (uiState.value.atCapacity) return
        formOpen.value = true
    }

    /** Closes the form and forgets the draft — cancelling means cancelling. */
    fun closeForm() {
        formOpen.value = false
        draft.value = ContactDraft()
    }

    // Clamped where the text enters rather than where it is drawn: each of these is one line in a
    // 415dp frame, and the row below has nothing to gain from the rest of a pasted paragraph.
    fun onNameChange(value: String) {
        draft.value = draft.value.copy(name = value.take(MaxContactNameLength))
    }

    fun onPhoneChange(value: String) {
        draft.value = draft.value.copy(phone = value.take(MaxContactPhoneLength))
    }

    fun onRelationChange(value: String) {
        draft.value = draft.value.copy(relation = value.take(MaxContactRelationLength))
    }

    /**
     * Writes the draft and closes the form.
     *
     * The form closes on success only. Offline is a success: Firestore applies the write to its local
     * cache and re-fires the listener before the round-trip, so the new row appears on the same frame
     * and `add` returns in airplane mode. A genuine failure here is the rules rejecting the write, and
     * that keeps the typed contact on screen rather than swallowing it.
     */
    fun save() {
        val current = uiState.value
        val draftNow = current.draft
        if (current.saving || current.atCapacity || !draftNow.canSave) return
        val uid = authRepository.currentUid ?: return

        saving.value = true
        viewModelScope.launch {
            try {
                contactRepository.add(
                    uid = uid,
                    name = draftNow.name.trim(),
                    phone = draftNow.phone.trim(),
                    relation = draftNow.relation.trim(),
                )
                formOpen.value = false
                draft.value = ContactDraft()
            } catch (cause: Exception) {
                Log.w("Omni", "Saving an emergency contact failed", cause)
            } finally {
                saving.value = false
            }
        }
    }

    /**
     * Removes one contact.
     *
     * No confirmation dialog: `UI_ARCHITECTURE.md` §6 rule 11 would have it hand-built, and a list of at
     * most five rows that can be re-typed in ten seconds does not earn one. The delete affordance is a
     * deliberate second tap instead — the row itself is not the target.
     */
    fun delete(contactId: String) {
        val uid = authRepository.currentUid ?: return
        viewModelScope.launch {
            try {
                contactRepository.delete(uid, contactId)
            } catch (cause: Exception) {
                Log.w("Omni", "Deleting emergency contact $contactId failed", cause)
            }
        }
    }

    private companion object {
        /** Matches the other screens: a rotation keeps the listener, a backgrounded app does not. */
        const val ListenerGraceMillis = 5_000L
    }
}
