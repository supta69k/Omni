package com.example.omni.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * On-device state that is not part of any user's Firestore document — the onboarding flag, and the step
 * counter's per-device bookkeeping.
 *
 * DataStore rather than Room (BACKEND_PLAN §6): there is no schema here worth a database, and
 * DataStore needs no KSP — which is the riskiest thing to add to this AGP 9 build.
 *
 * Everything is a [Flow], including the reads, because DataStore is asynchronous by design. The auth
 * gate treats "not answered yet" as a third state and shows the splash rather than guessing, exactly
 * as it does for `AuthState.LOADING`.
 */
class PreferencesStore(private val context: Context) {

    /** Every read shares this: a corrupt file falls back to defaults instead of killing the collector. */
    private val preferences: Flow<Preferences> = context.dataStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }

    /**
     * False until the carousel has been passed once. Sign-out deliberately does **not** clear it: a
     * returning user who logs out should land on sign-in, not be walked through onboarding again.
     *
     * A corrupt preferences file surfaces as an [IOException] on the flow, which would otherwise
     * propagate into the collector and take the whole app down at launch. Falling back to the default
     * costs one replayed carousel and nothing else.
     */
    val onboardingSeen: Flow<Boolean> = preferences.map { prefs -> prefs[OnboardingSeen] ?: false }

    suspend fun setOnboardingSeen(seen: Boolean = true) {
        context.dataStore.edit { prefs -> prefs[OnboardingSeen] = seen }
    }

    /**
     * The step counter's bookkeeping — see [StepState] for why any of it has to be remembered.
     *
     * This is the *session truth* for steps: Firestore holds a cross-device record that lags behind by
     * design (BACKEND_PLAN §11 Phase 4), while this is written on every reading, so a process death
     * loses nothing. Deliberately not in Firestore: it is device-specific — another phone's anchor and
     * boot id mean nothing here — and it changes far too often to pay for a document write each time.
     */
    val stepState: Flow<StepState> = preferences.map { prefs ->
        StepState(
            bootId = prefs[StepBootId] ?: 0L,
            anchorRaw = prefs[StepAnchorRaw] ?: 0,
            total = prefs[StepTotal] ?: 0,
            syncedTotal = prefs[StepSyncedTotal] ?: 0,
            date = prefs[StepDate] ?: "",
        )
    }

    suspend fun setStepState(state: StepState) {
        context.dataStore.edit { prefs ->
            prefs[StepBootId] = state.bootId
            prefs[StepAnchorRaw] = state.anchorRaw
            prefs[StepTotal] = state.total
            prefs[StepSyncedTotal] = state.syncedTotal
            prefs[StepDate] = state.date
        }
    }

    private companion object {
        val OnboardingSeen = booleanPreferencesKey("onboarding_seen")

        val StepBootId = longPreferencesKey("step_boot_id")
        val StepAnchorRaw = intPreferencesKey("step_anchor_raw")
        val StepTotal = intPreferencesKey("step_total")
        val StepSyncedTotal = intPreferencesKey("step_synced_total")
        val StepDate = stringPreferencesKey("step_date")
    }
}

/**
 * One store for the whole process. The delegate must sit at the top level — DataStore throws if two
 * instances are created for the same file name, and a property inside the class would do exactly that
 * on every recreation.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "omni_prefs")
