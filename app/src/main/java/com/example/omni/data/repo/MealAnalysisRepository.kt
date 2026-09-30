package com.example.omni.data.repo

import com.example.omni.data.model.MealAnalysisResult

/**
 * The AI meal analyzer (Phase 7) — the one call that turns "2 eggs and toast" into an estimated,
 * reviewable [com.example.omni.data.model.MealAnalysis].
 *
 * Deliberately not part of [MealRepository]: this speaks HTTP to the Omni Render backend, while
 * [MealRepository] speaks Firestore, and folding the two together would drag the network client into
 * every day-log read. The estimate this returns is *not* saved — the ViewModel hands the confirmed,
 * possibly-edited result to [MealRepository.saveMeal], so there is exactly one write path and one
 * source of truth for the food log.
 *
 * The Gemini API key never lives on the device: this interface's real implementation only ever sends
 * the user's Firebase ID token to the backend, which holds the key in its own environment.
 */
interface MealAnalysisRepository {

    /**
     * Sends [mealText] to the backend for estimation and returns a typed result — a validated
     * estimate on success, or a [com.example.omni.data.model.MealAnalysisError] the UI turns into a
     * specific message. Never throws for an expected failure (limit reached, offline, model down);
     * those are [MealAnalysisResult.Failure] so the caller can always fall back to manual logging.
     */
    suspend fun analyzeMeal(mealText: String): MealAnalysisResult
}
