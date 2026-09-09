package com.example.omni.ui.sos

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.AuthState
import com.example.omni.data.model.EmergencyContact
import com.example.omni.data.model.Hospital
import com.example.omni.data.model.NotificationType
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.EmergencyContactRepository
import com.example.omni.data.repo.HospitalRepository
import com.example.omni.data.repo.LocationRepository
import com.example.omni.data.repo.NotificationRepository
import com.example.omni.data.repo.SosRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One outgoing SOS message, ready to hand to the user's own messaging app.
 *
 * The numbers and the text are assembled here rather than in `MainActivity` so the *whole* decision —
 * who gets told, and what they are told — is testable without an `Intent`. The Activity's only job is
 * `ACTION_SENDTO`, which is deliberate for the same reason the hospital dial uses `ACTION_DIAL`: no
 * `SEND_SMS` permission, the message shows in the user's own thread history, and nobody is texted
 * without a visible Send.
 */
data class SosAlert(
    val phones: List<String>,
    val body: String,
)

/**
 * Everything the SOS screen renders.
 *
 * @property hospitals the directory around the user, nearest first, or the directory alphabetically
 *   when there is no location — which is every hospital, because the directory itself is small.
 * @property hasLocation false until a fix arrives, and permanently when the permission is refused:
 *   the screen names the state rather than pretending to sort.
 * @property alert `null` when the account has saved no contacts, which is what turns the sheet's pill
 *   into the way to add one.
 */
data class SosUiState(
    val hospitals: List<Hospital> = emptyList(),
    val hasLocation: Boolean = false,
    val alert: SosAlert? = null,
    /** True while the directory has not answered at all — the sheet's first frame. */
    val loading: Boolean = true,
) {
    /** What the sheet's pill counts. Zero and `alert == null` are the same state, said two ways. */
    val contactCount: Int get() = alert?.phones?.size ?: 0
}

/**
 * The SOS screen's reads and writes (BACKEND_PLAN §11 Phase 9).
 *
 * Location is re-read when the permission is granted rather than continuously observed: the screen
 * needs "where am I" for one sort, one activation record and one message body, not a breadcrumb. The
 * permission is asked for by `MainActivity` (the same pattern as the step counter's) and handed down
 * through [onLocationPermission], because a ViewModel cannot hold a launcher.
 */
class SosViewModel(
    private val authRepository: AuthRepository,
    private val hospitalRepository: HospitalRepository,
    private val locationRepository: LocationRepository,
    private val sosRepository: SosRepository,
    private val contactRepository: EmergencyContactRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    /** The last fix, re-read whenever the screen is told the permission state changed. */
    private val location = MutableStateFlow<Location?>(null)

    /** `null` whenever nobody is signed in — the key the contact read restarts on. */
    private val uid: Flow<String?> = authRepository.authState
        .map { if (it == AuthState.AUTHENTICATED) authRepository.currentUid else null }
        .distinctUntilChanged()

    /**
     * The directory paired with the fix it was arranged around.
     *
     * Paired rather than read back off [location], so the flag and the distances on the cards can
     * never describe two different fixes — the list and the sentence above it come from one emission.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val directory: Flow<Pair<List<Hospital>, Location?>> =
        location.flatMapLatest { loc ->
            // No fix means no "nearest": the whole directory, alphabetical, with no distance on any
            // card. Measuring from a stand-in centre would print a confident number for somebody
            // standing somewhere else entirely.
            val hospitals = if (loc == null) {
                hospitalRepository.observeAll()
            } else {
                hospitalRepository.observeNearby(loc.latitude, loc.longitude, NearbyRadiusKm)
            }
            hospitals.map { it to loc }
        }

    /**
     * The people to text. Edited on the saved-emergencies page and read here — two listeners on one
     * collection, the second of which Firestore serves from the cache.
     */
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

    val uiState: StateFlow<SosUiState> =
        combine(directory, contacts) { (hospitals, fix), contacts ->
            SosUiState(
                hospitals = hospitals,
                hasLocation = fix != null,
                alert = contacts.takeIf { it.isNotEmpty() }?.let { list ->
                    SosAlert(phones = list.map { it.phone }, body = alertBody(fix))
                },
                loading = false,
            )
        }
            .catch { cause ->
                Log.w("Omni", "The hospital directory could not be read", cause)
                emit(SosUiState(loading = false))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
                initialValue = SosUiState(),
            )

    /**
     * Told by `MainActivity` whenever the location permission's state is known or changes. Grants
     * trigger a fresh read; refusals leave the directory unsorted, which the screen labels honestly.
     */
    fun onLocationPermission(granted: Boolean) {
        if (!granted) {
            location.value = null
            return
        }
        viewModelScope.launch {
            location.value = locationRepository.lastKnown()
        }
    }

    /**
     * Records one activation — the write the swipe gesture has been missing (BACKEND_PLAN §11
     * Phase 9: "The SOS activation currently reports nothing outward").
     *
     * Fires-and-forgets: the sheet opens on the gesture's frame regardless of the write, because an
     * emergency UI must never wait on a network round-trip. The write itself queues offline.
     *
     * It also refreshes the fix, so the message body the sheet's alert pill is about to carry is the
     * location at the moment of the swipe rather than whenever the screen last opened.
     */
    fun onActivated() {
        val uid = authRepository.currentUid ?: return
        viewModelScope.launch {
            val fix = locationRepository.lastKnown() ?: location.value
            if (fix == null) {
                Log.w("Omni", "SOS activated without a location to record")
                return@launch
            }
            location.value = fix
            try {
                sosRepository.recordActivation(uid, fix.latitude, fix.longitude, fix.accuracy)

                // The receipt. Every other outcome of this gesture happens somewhere the user cannot
                // check afterwards — the messages leave through their own SMS app, the record goes into
                // a collection with no screen — so without this line there is nothing that says the
                // alert was ever stored. It is the one notification the app can write for itself
                // (`firestore.rules` DEVIATION 3); everything cross-account is a Cloud Function's.
                notificationRepository.post(
                    uid = uid,
                    type = NotificationType.SOS,
                    title = "SOS recorded",
                    body = receiptBody(uiState.value.contactCount),
                )
            } catch (cause: Exception) {
                Log.w("Omni", "Recording the SOS activation failed", cause)
            }
        }
    }

    /**
     * The receipt's second line.
     *
     * It says what actually happened, which depends on whether the account has anybody saved: with no
     * contacts nothing was texted, and claiming otherwise in an emergency log would be the worst kind of
     * wrong. The sentence is well inside [com.example.omni.data.model.MaxNotificationBodyLength] at every
     * contact count the app allows.
     */
    private fun receiptBody(contacts: Int): String = when (contacts) {
        0 -> "Your alert was saved with your location. No emergency contacts are saved yet, so nobody " +
            "was messaged."
        1 -> "Your alert was saved with your location, and 1 contact was opened in your messaging app."
        else -> "Your alert was saved with your location, and $contacts contacts were opened in your " +
            "messaging app."
    }

    private companion object {
        /** Matches the other screens: a rotation keeps the listener, a backgrounded app does not. */
        const val ListenerGraceMillis = 5_000L

        /** The plan's own "within 5 miles", converted: the screen shows kilometres. */
        const val NearbyRadiusKm = 8.0

        /**
         * The text the contacts receive.
         *
         * Five decimal places is about a metre — more would imply a precision no phone fix has. A
         * `maps.google.com/?q=` link rather than a `geo:` URI, because this is going into somebody
         * else's SMS app and has to open in whatever they have; the plan uses `geo:` only for the
         * in-app "Find the Route", where the app choosing a handler is the point.
         *
         * With no fix the message says so rather than omitting the line. "I need help" with a silent
         * gap where the address should be is the worst version of this message.
         */
        fun alertBody(fix: Location?): String {
            val where = if (fix == null) {
                "My phone can't get a location right now."
            } else {
                "I'm here: https://maps.google.com/?q=" +
                    "%.5f,%.5f".format(fix.latitude, fix.longitude)
            }
            return "SOS from Omni — I need help. $where"
        }
    }
}
