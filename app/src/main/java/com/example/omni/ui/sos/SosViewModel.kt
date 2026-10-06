package com.example.omni.ui.sos

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni.data.model.EmergencyContact
import com.example.omni.data.model.Hospital
import com.example.omni.data.model.LatLng
import com.example.omni.data.model.NotificationType
import com.example.omni.data.model.Route
import com.example.omni.data.model.distanceMetersTo
import com.example.omni.data.model.position
import com.example.omni.data.model.withDistanceFrom
import com.example.omni.data.repo.AuthRepository
import com.example.omni.data.repo.EmergencyContactRepository
import com.example.omni.data.repo.HospitalRepository
import com.example.omni.data.repo.LocationRepository
import com.example.omni.data.repo.NotificationRepository
import com.example.omni.data.repo.PharmacyRepository
import com.example.omni.data.repo.RoutingRepository
import com.example.omni.data.repo.SosRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
 * Why the map has, or has not, got the user on it.
 *
 * Four different refusals that all look identical from inside a location callback — no emissions — and
 * that each need a different sentence and a different button. Collapsing them into one `hasLocation:
 * Boolean` is what makes an emergency screen say "locating…" forever at somebody who turned location
 * off three days ago.
 */
enum class LocationStatus {
    /** Permission granted (or not yet answered) and the first fix has not landed. */
    Locating,

    /** A fix is on the map. */
    Fixed,

    /** Refused this session. The screen offers to ask again. */
    Denied,

    /** Refused for good — only Settings can undo it, so that is what the screen offers. */
    Blocked,

    /** Granted, but the device's location master switch is off. */
    ServicesOff,
}

/**
 * The state of the one route the map draws.
 *
 * A sealed type rather than `route: Route?` + `routeError: String?`, because "loading" and "failed" and
 * "no route asked for yet" are three different cards and a nullable pair can express all four
 * combinations, two of which are nonsense.
 */
sealed interface RouteStatus {
    /** Nothing asked for — the card shows "Find the Route". */
    data object Idle : RouteStatus

    /** The provider is being asked. */
    data object Loading : RouteStatus

    /** A polyline to draw, with its distance and ETA. */
    data class Ready(val route: Route) : RouteStatus

    /**
     * No route, and why — shown on the card rather than thrown. The SOS screen never falls back to an
     * external maps app, so this sentence is the whole answer the user gets.
     */
    data class Failed(val reason: String) : RouteStatus
}

/**
 * Everything the SOS screen renders.
 *
 * @property hospitals the merged directory — hospitals and pharmacies — around the user, nearest
 *   first, already filtered by [query]. The map's markers and the sheet's cards read the *same*
 *   list, so a search that hides a card hides its pin too.
 * @property directorySize how many hospitals existed before [query] filtered them, which is what tells
 *   "your search matched nothing" apart from "the directory has not been seeded".
 * @property nearestId the closest hospital of all, or `null` with no fix — the pin that gets the
 *   "Nearest" treatment. Read from the unfiltered list, so the badge never lands on a hospital that is
 *   merely the closest *match*.
 * @property alert `null` when the account has saved no contacts, which is what turns the sheet's pill
 *   into the way to add one.
 */
data class SosUiState(
    val hospitals: List<Hospital> = emptyList(),
    val directorySize: Int = 0,
    val selected: Hospital? = null,
    val nearestId: String? = null,
    val userLocation: LatLng? = null,
    val locationStatus: LocationStatus = LocationStatus.Locating,
    val query: String = "",
    val routeStatus: RouteStatus = RouteStatus.Idle,
    /**
     * Bumped every time the camera should fly back to the user — the recentre button, and the first fix
     * of the session. A counter rather than a `Unit` event because Compose keys on values, and the
     * screen re-runs its camera effect whenever this changes.
     */
    val recenterTick: Int = 0,
    val alert: SosAlert? = null,
    /** True while the directory has not answered at all — the sheet's first frame. */
    val loading: Boolean = true,
) {
    /** What the sheet's pill counts. Zero and `alert == null` are the same state, said two ways. */
    val contactCount: Int get() = alert?.phones?.size ?: 0

    /** The route to draw, or `null` in every state that has none. */
    val route: Route? get() = (routeStatus as? RouteStatus.Ready)?.route

    /** Whether the user's own position can be drawn and measured from. */
    val hasLocation: Boolean get() = userLocation != null
}

/** How the location permission was last answered, as far as this ViewModel has been told. */
private enum class PermissionState { Unknown, Granted, Denied, Blocked }

/**
 * One pass over the directory, before the parts that have nothing to do with the map are added.
 */
private data class MapSlice(
    val hospitals: List<Hospital>,
    val directorySize: Int,
    val selected: Hospital?,
    val nearestId: String?,
    val userLocation: LatLng?,
    val fix: Location?,
    val query: String,
    val routeStatus: RouteStatus,
)

/**
 * The map's own arithmetic, decided once and tested pure (see `SosDirectoryTest`).
 *
 * Measure against the fix, trim to the radius — with the fallback that keeps a radius that found
 * nothing from producing an empty emergency screen — filter by the search, then choose what the
 * sheet and the camera land on. Deliberately free of Android types: the fix arrives as two nullable
 * doubles so the whole decision runs on the JVM with a list of data classes and nothing else.
 */
internal data class DirectorySlice(
    val visible: List<Hospital>,
    val nearbySize: Int,
    val nearestId: String?,
    val selected: Hospital?,
)

/** Name or type — "Metro" and "Pharmacy" are both things a user would reasonably type. */
internal fun Hospital.matches(query: String): Boolean =
    name.contains(query, ignoreCase = true) || type.contains(query, ignoreCase = true)

internal fun selectFacilities(
    all: List<Hospital>,
    fixLat: Double?,
    fixLng: Double?,
    typed: String,
    chosenId: String?,
    nearbyRadiusKm: Double,
    fallbackCount: Int,
): DirectorySlice {
    // With no fix there is no "nearest" and no distance: measuring from a stand-in centre would
    // print a confident "0.4 km" for somebody standing in another district.
    val hasFix = fixLat != null && fixLng != null
    val measured = if (!hasFix) {
        all.sortedBy { it.name }
    } else {
        all.map { it.withDistanceFrom(fixLat, fixLng) }.sortedBy { it.distanceKm }
    }

    val nearby = if (!hasFix) {
        measured
    } else {
        measured
            .filter { (it.distanceKm ?: Double.MAX_VALUE) <= nearbyRadiusKm }
            // A radius that finds nothing must still find something: an empty emergency screen is
            // never the right answer while a facility exists anywhere in the directory.
            .ifEmpty { measured.take(fallbackCount) }
    }

    val nearestId = if (hasFix) nearby.firstOrNull()?.id else null
    val visible = if (typed.isBlank()) nearby else nearby.filter { it.matches(typed) }

    // The selection, in order of preference: what the user tapped, the nearest, the first thing on
    // the map. The fallbacks are what make "the nearest facility is selected on open" true without
    // a write, and what keeps a card on screen when a search hides the tapped pin.
    val selected = visible.firstOrNull { it.id == chosenId }
        ?: visible.firstOrNull { it.id == nearestId }
        ?: visible.firstOrNull()

    return DirectorySlice(
        visible = visible,
        nearbySize = nearby.size,
        nearestId = nearestId,
        selected = selected,
    )
}

/**
 * The SOS map's reads and writes (BACKEND_PLAN §11 Phase 9).
 *
 * ## What changed, and why
 *
 * The first version of this screen re-read the location once per permission grant and handed the route
 * to Google Maps through a `geo:` intent. Both are gone. The map is now in-app (MapLibre over MapTiler
 * tiles), which means the location has to be a *stream* — a marker that does not move is worse than no
 * marker — and the route has to come back as data this app can draw, which is what
 * [RoutingRepository] is for.
 *
 * ## The three throttles
 *
 * A live map is a battery and quota problem if every one of its inputs is allowed to fire freely, so
 * each one is deliberately damped:
 *
 *  - **The GPS subscription** lives inside the `uiState` graph, so `WhileSubscribed` tears it down a
 *    few seconds after the screen leaves. Nothing here is collected in an `init` block for exactly that
 *    reason.
 *  - **The Firestore listeners** are [HospitalRepository.observeAll] and its pharmacy twin —
 *    location-independent, so walking never restarts them. Distances, the radius and the sort are
 *    all computed locally against the live fix, which is free.
 *  - **The routing call** only repeats when the destination changes or the user has left the route's
 *    origin by [RerouteThresholdMeters]. A phone on a table re-routes never.
 *
 * The permission is asked for by `MainActivity` (the same pattern as the step counter's) and handed
 * down through [onLocationPermission], because a ViewModel cannot hold a launcher.
 */
class SosViewModel(
    private val authRepository: AuthRepository,
    private val hospitalRepository: HospitalRepository,
    private val pharmacyRepository: PharmacyRepository,
    private val locationRepository: LocationRepository,
    private val routingRepository: RoutingRepository,
    private val sosRepository: SosRepository,
    private val contactRepository: EmergencyContactRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val permission = MutableStateFlow(PermissionState.Unknown)

    /** The device's master location switch, re-read whenever the screen reports the permission. */
    private val locationServicesEnabled = MutableStateFlow(true)

    private val selectedId = MutableStateFlow<String?>(null)

    private val query = MutableStateFlow("")

    private val routeStatus = MutableStateFlow<RouteStatus>(RouteStatus.Idle)

    private val recenter = MutableStateFlow(0)

    /** The in-flight routing call, so a second request cancels the first instead of racing it. */
    private var routeJob: Job? = null

    /** Where the drawn route starts, which is what "has the user moved far enough?" measures against. */
    private var routeOrigin: LatLng? = null

    /** Which hospital the drawn route ends at, so a re-route keeps the same destination. */
    private var routeDestinationId: String? = null

    /** `null` whenever nobody is signed in — the key the contact read restarts on. */
    private val uid: StateFlow<String?> = authRepository.sessionUid

    /**
     * The location stream — one fused listener, however many parts of the graph read it.
     *
     * `lastKnown()` is emitted first: it is the fix the device already has, and on an emergency screen a
     * marker that appears immediately and is refined a second later beats an empty map that is
     * eventually correct. `WhileSubscribed` is what stops the listener when the screen goes away
     * ([awaitClose][kotlinx.coroutines.channels.awaitClose] inside the repository does the removal), and
     * it is why nothing here is collected from an `init` block — that would keep the GPS on for the life
     * of the ViewModel, which outlives this tab.
     *
     * `.value` is read directly by the route calls, which need "where am I *now*" rather than a stream.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val streamFix: StateFlow<Location?> = permission
        .flatMapLatest { state ->
            if (state != PermissionState.Granted) {
                flowOf(null)
            } else {
                flow<Location?> {
                    emit(locationRepository.lastKnown())
                    emitAll(locationRepository.observeLocation())
                }.catch { cause ->
                    // No Play Services, or the provider died. The map keeps its last marker and the
                    // screen says why rather than crashing the one screen that is about emergencies.
                    Log.w("Omni", "Location updates stopped", cause)
                    emit(null)
                }
            }
        }
        // Upstream of `stateIn`, so `streamFix.value` is still the *previous* fix while this runs —
        // which is exactly how "is this the first fix of the session?" is asked below.
        .onEach { fix -> onFix(fix) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = null,
        )

    /**
     * The one-shot fix [onActivated] reads at the moment of the swipe, so the message body carries the
     * location the user pressed the button at rather than wherever the stream last got to.
     */
    private val manualFix = MutableStateFlow<Location?>(null)

    /** The freshest of the two, by the fix's own timestamp. */
    private val fix: Flow<Location?> = combine(streamFix, manualFix) { streamed, manual ->
        listOfNotNull(streamed, manual).maxByOrNull { it.time }
    }

    /**
     * The whole directory — hospitals and pharmacies — once.
     *
     * Not keyed on the location: see [HospitalRepository.observeAll]. Two independent listeners feed
     * one merged list, so a pharmacy import that fails or is slow to first sync cannot delay or
     * empty the hospital half, which is the one an emergency actually needs. Everything positional
     * about the list is computed downstream, where it costs nothing to redo on every step the user
     * takes.
     */
    private val directory: Flow<List<Hospital>> = combine(
        hospitalRepository.observeAll().catch { cause ->
            Log.w("Omni", "The hospital directory could not be read", cause)
            emit(emptyList())
        },
        pharmacyRepository.observeAll().catch { cause ->
            Log.w("Omni", "The pharmacy directory could not be read", cause)
            emit(emptyList())
        },
    ) { hospitals, pharmacies -> hospitals + pharmacies }

    /**
     * The search box, damped.
     *
     * Blank is instant — clearing a field should feel like clearing a field — and typed text waits out
     * [SearchDebounceMillis], so a six-letter hospital name re-filters the marker layer once rather than
     * six times.
     */
    @OptIn(FlowPreview::class)
    private val debouncedQuery: Flow<String> = query
        .debounce { typed -> if (typed.isBlank()) 0L else SearchDebounceMillis }
        .distinctUntilChanged()

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

    /**
     * The map's own arithmetic: measure, trim, search, choose — decided in [selectFacilities], pure
     * and unit-tested; this lambda only marries it to the flows.
     *
     * Split from [uiState] because `combine` takes five typed flows and this screen has eight
     * inputs; the two halves are one derivation.
     */
    private val mapState: Flow<MapSlice> = combine(
        directory,
        fix,
        debouncedQuery,
        selectedId,
        routeStatus,
    ) { all, fix, typed, chosenId, route ->
        val slice = selectFacilities(
            all = all,
            fixLat = fix?.latitude,
            fixLng = fix?.longitude,
            typed = typed,
            chosenId = chosenId,
            nearbyRadiusKm = NearbyRadiusKm,
            fallbackCount = FallbackCount,
        )

        MapSlice(
            hospitals = slice.visible,
            directorySize = slice.nearbySize,
            selected = slice.selected,
            nearestId = slice.nearestId,
            userLocation = fix?.toLatLng(),
            fix = fix,
            query = typed,
            routeStatus = route,
        )
    }
        // ...then overwrite the query with the *undebounced* one. [debouncedQuery] is what filtered
        // the list, and that is all it should ever be: a search box whose text arrives 250 ms after
        // the key was pressed drops characters, because every keystroke re-renders the field with the
        // value from before it. The list lags on purpose; the text box must not.
        .combine(query) { slice, typed -> slice.copy(query = typed) }

    val uiState: StateFlow<SosUiState> = combine(
        mapState,
        contacts,
        recenter,
        permission,
        locationServicesEnabled,
    ) { slice, contacts, recenterTick, permission, servicesOn ->
        SosUiState(
            hospitals = slice.hospitals,
            directorySize = slice.directorySize,
            selected = slice.selected,
            nearestId = slice.nearestId,
            userLocation = slice.userLocation,
            locationStatus = locationStatus(permission, servicesOn, slice.userLocation != null),
            query = slice.query,
            routeStatus = slice.routeStatus,
            recenterTick = recenterTick,
            alert = contacts.takeIf { it.isNotEmpty() }?.let { list ->
                SosAlert(phones = list.map { it.phone }, body = alertBody(slice.fix))
            },
            loading = false,
        )
    }
        .catch { cause ->
            Log.w("Omni", "The SOS screen's state could not be assembled", cause)
            emit(SosUiState(loading = false))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ListenerGraceMillis),
            initialValue = SosUiState(),
        )

    /**
     * Told by `MainActivity` whenever the location permission's state is known or changes — including on
     * every resume, which is how a trip to Settings gets noticed.
     *
     * @param permanentlyDenied true when the system will no longer show the dialog, so the screen must
     *   send the user to Settings instead of asking a question that can no longer be answered.
     */
    fun onLocationPermission(granted: Boolean, permanentlyDenied: Boolean = false) {
        locationServicesEnabled.value = locationRepository.isLocationEnabled()
        permission.value = when {
            granted -> PermissionState.Granted
            permanentlyDenied -> PermissionState.Blocked
            else -> PermissionState.Denied
        }
        // A revoked permission has to take the marker with it; the stream's own `null` covers the
        // live half, this covers the fix the swipe once parked.
        if (!granted) manualFix.value = null
    }

    /** The search field. Filtering happens [SearchDebounceMillis] later; the text is immediate. */
    fun onQueryChange(text: String) {
        query.value = text.take(MaxQueryLength)
    }

    /**
     * A tapped pin or card.
     *
     * A route belongs to a destination, so changing the destination cannot leave the old polyline on the
     * map. If one was already drawn (or being drawn) the user has asked for directions once already, and
     * the new destination gets the same treatment without a second tap.
     */
    fun onSelectHospital(hospitalId: String) {
        if (selectedId.value == hospitalId) return
        selectedId.value = hospitalId

        val wasRouting = routeStatus.value is RouteStatus.Ready || routeStatus.value is RouteStatus.Loading
        clearRoute()
        if (!wasRouting) return

        val hospital = uiState.value.hospitals.firstOrNull { it.id == hospitalId } ?: return
        val origin = latestFix()?.toLatLng() ?: return
        requestRoute(origin, hospital)
    }

    /**
     * The "Find the Route" button — the whole reason the `geo:` intent could be deleted.
     *
     * Takes the hospital rather than reading [SosUiState.selected], and selects it on the way through.
     * The card that carries this button is one of several on a rail, so "route to this one" has to be
     * answerable in a single tap; going through [onSelectHospital] first would set [selectedId] and then
     * read a `selected` that the flow graph has not recomputed yet, and route to the previous hospital.
     */
    fun onFindRoute(hospital: Hospital) {
        selectedId.value = hospital.id
        val origin = latestFix()?.toLatLng()
        if (origin == null) {
            routeStatus.value = RouteStatus.Failed(
                "Omni needs your location to draw a route. Turn location on and try again.",
            )
            return
        }
        requestRoute(origin, hospital)
    }

    /** Fly the camera back to the user. Also fired once, by [onFix], when the first fix lands. */
    fun onRecenter() {
        recenter.value++
    }

    /** Drops the drawn route — the card's "clear", and what a changed destination does implicitly. */
    fun clearRoute() {
        routeJob?.cancel()
        routeJob = null
        routeOrigin = null
        routeDestinationId = null
        routeStatus.value = RouteStatus.Idle
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
            val fix = locationRepository.lastKnown() ?: latestFix()
            if (fix == null) {
                Log.w("Omni", "SOS activated without a location to record")
                return@launch
            }
            manualFix.value = fix
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
     * Every new fix, in one place: open the camera on the first one, and re-route if the user has
     * walked far enough for the drawn line to be wrong.
     *
     * Parking the value is `stateIn`'s job, one step downstream — which is why `streamFix.value` still
     * reads as the *previous* fix in here, and why the "first fix of the session" test works.
     */
    private fun onFix(fix: Location?) {
        if (fix == null) return
        // The first fix of the session is what the camera opens on, without the user asking for it.
        if (streamFix.value == null) recenter.value++
        maybeReroute(fix)
    }

    /** The freshest fix the ViewModel has, from either source, or `null` when it has none. */
    private fun latestFix(): Location? =
        listOfNotNull(streamFix.value, manualFix.value).maxByOrNull { it.time }

    /**
     * Re-routes only when the drawn route's origin is [RerouteThresholdMeters] behind the user.
     *
     * Silently: the existing polyline stays on the map while the new one is fetched, and a failed
     * refresh leaves the old route rather than replacing a working line with an error. Only a route the
     * user explicitly asked for is allowed to show a spinner or a failure.
     */
    private fun maybeReroute(fix: Location) {
        if (routeStatus.value !is RouteStatus.Ready) return
        val origin = routeOrigin ?: return
        val destinationId = routeDestinationId ?: return
        if (origin.distanceMetersTo(fix.toLatLng()) < RerouteThresholdMeters) return

        val hospital = uiState.value.hospitals.firstOrNull { it.id == destinationId } ?: return
        requestRoute(fix.toLatLng(), hospital, silent = true)
    }

    private fun requestRoute(origin: LatLng, destination: Hospital, silent: Boolean = false) {
        routeJob?.cancel()
        routeOrigin = origin
        routeDestinationId = destination.id
        if (!silent) routeStatus.value = RouteStatus.Loading

        routeJob = viewModelScope.launch {
            routingRepository.getRoute(origin, destination.position()).fold(
                onSuccess = { routeStatus.value = RouteStatus.Ready(it) },
                onFailure = { cause ->
                    Log.w("Omni", "Routing to ${destination.name} failed", cause)
                    if (silent) {
                        // The old line is still the best answer available; keep it and try again at the
                        // next threshold rather than blanking a route the user is following.
                        routeOrigin = origin
                    } else {
                        routeOrigin = null
                        routeDestinationId = null
                        routeStatus.value = RouteStatus.Failed(
                            "The route couldn't be loaded. Check your connection and try again.",
                        )
                    }
                },
            )
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

        /** How many to show when nothing at all is inside [NearbyRadiusKm]. */
        const val FallbackCount = 3

        /** Long enough that a typed word filters once, short enough to feel like live search. */
        const val SearchDebounceMillis = 250L

        /** A hospital name nobody could exceed; a guard on the field, not a rule about search. */
        const val MaxQueryLength = 60

        /**
         * How far the user has to leave a drawn route's start before it is worth asking OSRM again.
         *
         * 150 m is roughly a city block: far enough that GPS jitter and a walk to the corner never
         * trigger a request, close enough that a route the user has actually diverged from is corrected
         * before it becomes misleading.
         */
        const val RerouteThresholdMeters = 150.0

        fun Location.toLatLng(): LatLng = LatLng(latitude, longitude)

        fun locationStatus(
            permission: PermissionState,
            servicesEnabled: Boolean,
            hasFix: Boolean,
        ): LocationStatus = when {
            permission == PermissionState.Denied -> LocationStatus.Denied
            permission == PermissionState.Blocked -> LocationStatus.Blocked
            hasFix -> LocationStatus.Fixed
            permission == PermissionState.Granted && !servicesEnabled -> LocationStatus.ServicesOff
            else -> LocationStatus.Locating
        }

        /**
         * The text the contacts receive.
         *
         * Five decimal places is about a metre — more would imply a precision no phone fix has. A
         * `maps.google.com/?q=` link rather than a `geo:` URI, because this is going into somebody
         * else's SMS app and has to open in whatever they have. (The app's *own* routing never leaves
         * Omni; this link exists only because the recipient's phone is not running it.)
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
