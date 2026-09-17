// POST /ai/meal/analyze — parse a natural-language meal into estimated nutrition (Phase 7).
//
// This module has NO import-time side effects (it never touches Firebase Admin, SendGrid, or the
// HTTP listener), so the Vitest suite can import and exercise it directly with a fake Gemini client
// and never spend real free-tier quota. `index.ts` supplies the real dependencies.
//
// The endpoint is an ESTIMATOR: it labels every result as estimated and validates the model's
// numbers before returning them. A well-formed JSON shape does not make the values nutritionally
// sane, so the semantic checks below are the real guardrail, not the schema.

import { Request, Response } from 'express';
import { z } from 'zod';
import {
  GeminiClient,
  GeminiRateLimitError,
  GeminiUnavailableError,
} from './gemini.js';
import { MealAnalysisResult, MealItem, MealTotals } from './types.js';

/** A dependency shaped like FirestoreRateLimiter, narrowed to what this handler calls. */
export interface DailyLimiter {
  consume(key: string): Promise<unknown>;
}

/** What `index.ts` injects; also the seam every unit test fills with fakes. */
export interface MealAnalyzeDeps {
  gemini: GeminiClient;
  limiter: DailyLimiter;
  maxTextLength: number;
}

// Sanity bounds. Generous enough for any real meal, tight enough to reject a model hallucinating a
// five-figure single item or a hundred servings. Kept in step with the Android Meal caps
// (MaxCalories = 99_999, MaxGrams = 2_000f) so a value that passes here also survives the client.
const MAX_ITEMS = 30;
const MAX_ITEM_QUANTITY = 100;
const MAX_ITEM_CALORIES = 20_000;
const MAX_TOTAL_CALORIES = 99_999;
const MAX_MACRO_GRAMS = 2_000;

/** Thrown when the model's output is unusable — surfaced to the client as AI_INVALID_RESPONSE. */
export class InvalidAnalysisError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'InvalidAnalysisError';
  }
}

/** Request body schema. A trimmed, non-empty description no longer than the configured cap. */
export function analyzeMealSchema(maxTextLength: number) {
  return z.object({
    mealText: z.string().trim().min(1, 'mealText is required').max(maxTextLength, 'mealText too long'),
  });
}

/** Parses the model's text into an object, tolerating an accidental ```json fence. */
export function parseGeminiJson(raw: string): unknown {
  const trimmed = raw.trim();
  // The schema forbids fences, but a stray one must not crash the endpoint — strip and retry.
  const unfenced = trimmed
    .replace(/^```(?:json)?\s*/i, '')
    .replace(/\s*```$/,'')
    .trim();
  try {
    return JSON.parse(unfenced);
  } catch {
    throw new InvalidAnalysisError('Model did not return valid JSON');
  }
}

function finiteNonNegative(value: unknown): number | null {
  const n = typeof value === 'number' ? value : NaN;
  if (!Number.isFinite(n) || n < 0) return null;
  return n;
}

function round(n: number, decimals = 1): number {
  const f = 10 ** decimals;
  return Math.round(n * f) / f;
}

/**
 * Turns the raw model object into a sanitized [MealAnalysisResult], or throws
 * [InvalidAnalysisError]. This is the semantic guardrail (§8): it rejects negative or non-finite
 * nutrition, absurd quantities, a missing meal name, and missing item names, and it ALWAYS recomputes
 * the totals from the validated items so the number the user confirms is the sum of what they see.
 */
export function validateAnalysis(raw: unknown): MealAnalysisResult {
  if (raw === null || typeof raw !== 'object') {
    throw new InvalidAnalysisError('Response was not an object');
  }
  const obj = raw as Record<string, unknown>;

  const mealName = typeof obj.mealName === 'string' ? obj.mealName.trim() : '';
  if (mealName.length === 0) {
    throw new InvalidAnalysisError('Missing meal name');
  }

  const needsClarification = obj.needsClarification === true;
  const clarificationQuestion =
    typeof obj.clarificationQuestion === 'string' && obj.clarificationQuestion.trim().length > 0
      ? obj.clarificationQuestion.trim()
      : null;

  // A clarification result carries no numbers to validate; return it early with empty totals so the
  // app can prompt the user for more detail rather than saving a guess.
  if (needsClarification) {
    return {
      mealName,
      items: [],
      totals: { calories: 0, proteinGrams: 0, carbsGrams: 0, fatGrams: 0 },
      estimated: true,
      needsClarification: true,
      clarificationQuestion:
        clarificationQuestion ?? 'Could you describe the amount you ate in a little more detail?',
    };
  }

  if (!Array.isArray(obj.items) || obj.items.length === 0) {
    throw new InvalidAnalysisError('No food items returned');
  }
  if (obj.items.length > MAX_ITEMS) {
    throw new InvalidAnalysisError('Too many food items');
  }

  const items: MealItem[] = obj.items.map((entry, index) => {
    if (entry === null || typeof entry !== 'object') {
      throw new InvalidAnalysisError(`Item ${index} was not an object`);
    }
    const it = entry as Record<string, unknown>;

    const name = typeof it.name === 'string' ? it.name.trim() : '';
    if (name.length === 0) {
      throw new InvalidAnalysisError(`Item ${index} is missing a name`);
    }

    const quantity = finiteNonNegative(it.quantity);
    if (quantity === null || quantity === 0 || quantity > MAX_ITEM_QUANTITY) {
      throw new InvalidAnalysisError(`Item "${name}" has an unreasonable quantity`);
    }

    const calories = finiteNonNegative(it.calories);
    const proteinGrams = finiteNonNegative(it.proteinGrams);
    const carbsGrams = finiteNonNegative(it.carbsGrams);
    const fatGrams = finiteNonNegative(it.fatGrams);
    if (calories === null || proteinGrams === null || carbsGrams === null || fatGrams === null) {
      throw new InvalidAnalysisError(`Item "${name}" has invalid nutrition values`);
    }
    if (calories > MAX_ITEM_CALORIES) {
      throw new InvalidAnalysisError(`Item "${name}" has an unreasonable calorie value`);
    }
    if (proteinGrams > MAX_MACRO_GRAMS || carbsGrams > MAX_MACRO_GRAMS || fatGrams > MAX_MACRO_GRAMS) {
      throw new InvalidAnalysisError(`Item "${name}" has an unreasonable macro value`);
    }

    return {
      name,
      quantity: round(quantity, 2),
      unit: typeof it.unit === 'string' && it.unit.trim().length > 0 ? it.unit.trim() : 'serving',
      calories: Math.round(calories),
      proteinGrams: round(proteinGrams),
      carbsGrams: round(carbsGrams),
      fatGrams: round(fatGrams),
    };
  });

  const totals = computeTotals(items);
  if (totals.calories > MAX_TOTAL_CALORIES) {
    throw new InvalidAnalysisError('Total calories are unreasonably high');
  }

  return {
    mealName,
    items,
    totals,
    estimated: true,
    needsClarification: false,
    clarificationQuestion: null,
  };
}

/** Sums a meal's items. Server-authoritative — the model's own `totals` are ignored on purpose. */
export function computeTotals(items: MealItem[]): MealTotals {
  const totals = items.reduce(
    (acc, it) => ({
      calories: acc.calories + it.calories,
      proteinGrams: acc.proteinGrams + it.proteinGrams,
      carbsGrams: acc.carbsGrams + it.carbsGrams,
      fatGrams: acc.fatGrams + it.fatGrams,
    }),
    { calories: 0, proteinGrams: 0, carbsGrams: 0, fatGrams: 0 },
  );
  return {
    calories: Math.round(totals.calories),
    proteinGrams: round(totals.proteinGrams),
    carbsGrams: round(totals.carbsGrams),
    fatGrams: round(totals.fatGrams),
  };
}

/**
 * Builds the Express handler. Assumes `authenticate` ran first, so `req.user.uid` is a verified UID
 * — ownership is never taken from the request body (§6). The per-user daily limit is charged before
 * Gemini so a throttled account never reaches the model (§20).
 */
export function createMealAnalyzeHandler(deps: MealAnalyzeDeps) {
  const schema = analyzeMealSchema(deps.maxTextLength);

  return async function analyzeMeal(req: Request, res: Response): Promise<void> {
    const uid = (req as any).user?.uid as string | undefined;
    if (!uid) {
      // Defense in depth: the route is always mounted behind `authenticate`, but never trust that.
      res.status(401).json({ error: 'Unauthenticated', code: 'AUTH_REQUIRED' });
      return;
    }

    const parsed = schema.safeParse(req.body);
    if (!parsed.success) {
      res.status(400).json({ error: 'Please describe your meal with a little more detail.', code: 'INVALID_INPUT' });
      return;
    }

    // Daily cap first. The limiter throws when the account is over budget for the day.
    try {
      await deps.limiter.consume(uid);
    } catch {
      console.log('[AI_MEAL_LIMIT_REACHED] uid=' + uid);
      res.status(429).json({
        error: 'Daily AI limit reached. Please try again later or log this meal manually.',
        code: 'AI_MEAL_LIMIT_REACHED',
      });
      return;
    }

    // The meal text is personal data: log only that a request happened and its length, never the
    // text itself (§22).
    console.log('[AI_MEAL_ANALYZE_REQUEST] uid=' + uid + ' len=' + parsed.data.mealText.length);

    let rawText: string;
    try {
      rawText = await deps.gemini.analyzeMeal(parsed.data.mealText);
    } catch (error) {
      if (error instanceof GeminiRateLimitError) {
        console.log('[AI_MEAL_GEMINI_QUOTA] uid=' + uid);
        res.status(503).json({
          error: 'AI meal analysis is busy right now. You can still log this meal manually.',
          code: 'AI_UNAVAILABLE',
        });
        return;
      }
      if (error instanceof GeminiUnavailableError) {
        console.log('[AI_MEAL_GEMINI_UNAVAILABLE] uid=' + uid);
        res.status(503).json({
          error: 'AI meal analysis is temporarily unavailable. You can still log this meal manually.',
          code: 'AI_UNAVAILABLE',
        });
        return;
      }
      console.error('[AI_MEAL_ANALYZE_ERROR] uid=' + uid, error instanceof Error ? error.name : 'unknown');
      res.status(500).json({ error: 'Something went wrong analyzing your meal.', code: 'AI_ERROR' });
      return;
    }

    let result: MealAnalysisResult;
    try {
      result = validateAnalysis(parseGeminiJson(rawText));
    } catch (error) {
      // The model answered but the answer is unusable. Do not save a guess; tell the app to fall
      // back to manual logging.
      console.log('[AI_MEAL_INVALID_RESPONSE] uid=' + uid);
      res.status(422).json({
        error: 'The AI could not estimate this meal. Please add a little more detail or log it manually.',
        code: 'AI_INVALID_RESPONSE',
      });
      return;
    }

    res.json({ success: true, result });
  };
}
