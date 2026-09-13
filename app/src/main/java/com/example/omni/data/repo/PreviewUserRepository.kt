package com.example.omni.data.repo

import com.example.omni.data.model.HealthGoals
import com.example.omni.data.model.Profession
import com.example.omni.data.model.User
import com.example.omni.data.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The offline twin of [FirestoreUserRepository], matching [PreviewAuthRepository]'s role.
 *
 * Holds one profile in memory and streams it, so a preview or a unit test sees a real name rather
 * than an empty state. The name is the one the Figma frames show, which keeps a preview that binds
 * live data pixel-identical to a preview that uses the composable's defaults.
 */
class PreviewUserRepository(
    initial: User = PreviewUser,
) : UserRepository {

    private val user = MutableStateFlow(initial)

    override fun observeUser(uid: String): Flow<User?> = user.asStateFlow()

    /** The one profile this fake holds, plus the two professionals, matched by uid. */
    override suspend fun getUser(uid: String): User? =
        (listOf(user.value) + PreviewProfessionals).firstOrNull { it.uid == uid }

    override fun observeProfessionals(): Flow<List<User>> = MutableStateFlow(PreviewProfessionals)

    /**
     * The same prefix rule as Firestore's, applied to the three people this fake holds — case-folded
     * here because in memory there is no byte ordering to work around, and a preview that could only
     * find "Dr." by typing the capital would be testing the wrong thing.
     */
    override suspend fun searchByName(query: String, limit: Int): List<User> {
        val term = query.trim()
        if (term.isBlank()) return emptyList()
        return (listOf(user.value) + PreviewProfessionals)
            .filter { it.name.startsWith(term, ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
            .take(limit)
    }

    /**
     * The fields the account pages actually write, applied in memory.
     *
     * Blank is dropped rather than stored, which is [FirestoreUserRepository]'s reading rule seen from
     * the writing side — clearing a field on the real backend stores `""` and reads back as `null`, and
     * a fake that kept the empty string would make a preview of "nothing answered yet" impossible.
     */
    override suspend fun updateProfile(uid: String, fields: Map<String, Any?>) {
        fun field(key: String): String? = (fields[key] as? String)?.takeIf { it.isNotBlank() }
        user.update { current ->
            current.copy(
                name = field("name") ?: current.name,
                email = field("email") ?: current.email,
                photoUrl = if ("photoUrl" in fields) field("photoUrl") else current.photoUrl,
                dob = if ("dob" in fields) field("dob") else current.dob,
                gender = if ("gender" in fields) field("gender") else current.gender,
            )
        }
    }

    override suspend fun updateGoals(uid: String, goals: HealthGoals) {
        val safe = goals.clamped()
        user.update { current ->
            current.copy(
                waterGoal = safe.water,
                stepsGoal = safe.steps,
                sleepGoal = safe.sleepHours,
                fiberGoal = safe.fiberGrams,
                calorieGoal = safe.calories,
            )
        }
    }

    private companion object {
        val PreviewUser = User(
            uid = "preview-uid",
            name = "Sayed Mahir",
            email = "sayedmahir69@gmail.com",
        )

        /** Two, so a preview shows both disciplines the app knows about. */
        val PreviewProfessionals = listOf(
            User(
                uid = "dr-rahman",
                name = "Dr. Sadia Rahman",
                email = "sadia@omni.health",
                role = UserRole.PROFESSIONAL,
                profession = Profession.DOCTOR,
                verified = true,
            ),
            User(
                uid = "nut-karim",
                name = "Tanvir Karim",
                email = "tanvir@omni.health",
                role = UserRole.PROFESSIONAL,
                profession = Profession.NUTRITIONIST,
                verified = true,
            ),
        )
    }
}
