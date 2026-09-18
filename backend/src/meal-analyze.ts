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
const MAX_ITEM_CALORIES = 20_000;
const MAX_TOTAL_CALORIES = 99_999;
const MAX_MACRO_GRAMS = 2_000;

/**
 * The upper bound on an item's `quantity` DEPENDS ON ITS UNIT, because the number means completely
 * different things per unit: "200" is absurd as pieces of chicken but ordinary as grams of rice.
 * A single numeric cap across all units rejected "200 grams of rice" as though the user had claimed
 * 200 servings, which is the bug this table fixes.
 *
 * Each bound is the largest amount one person could plausibly eat in one meal, with headroom — the
 * job here is to catch a hallucinating model, not to second-guess a large portion. Anything above
 * the bound is treated as model error rather than a real meal.
 */
const QUANTITY_BOUNDS = {
  /** Countable things: servings, pieces, slices, whole items. 50 pieces is already a stretch. */
  count: 50,
  /** Grams. 5 kg of one food in a single meal is far beyond any real portion. */
  gram: 5_000,
  /** Kilograms. Same ceiling as grams, expressed in kg. */
  kilogram: 5,
  /** Ounces (~5 kg). */
  ounce: 180,
  /** Pounds (~5 kg). */
  pound: 11,
  /** Millilitres. 5 L of one drink/food in a meal is beyond real. */
  millilitre: 5_000,
  /** Litres. Same ceiling expressed in L. */
  litre: 5,
  /** Cups (~5 L). */
  cup: 20,
  /** Tablespoons. Generous for a condiment, absurd at 200. */
  tablespoon: 50,
  /** Teaspoons — three to a tablespoon, so a correspondingly higher cap. */
  teaspoon: 150,
  /** Fluid ounces (~5 L). */
  fluidOunce: 170,
} as const;

type QuantityKind = keyof typeof QUANTITY_BOUNDS;

/**
 * Maps the unit strings a model actually emits onto a bound category. The model is asked for natural
 * units, not a controlled vocabulary, so the same unit arrives as "g" / "gram" / "grams"; all of them
 * must be read as weight. Lookup is on a lowercased, punctuation-stripped form of the unit.
 */
const UNIT_ALIASES: Record<string, QuantityKind> = {
  // Count / portion
  serving: 'count', servings: 'count',
  piece: 'count', pieces: 'count', pc: 'count', pcs: 'count',
  item: 'count', items: 'count',
  whole: 'count', unit: 'count', units: 'count',
  slice: 'count', slices: 'count',
  large: 'count', medium: 'count', small: 'count',
  bowl: 'count', bowls: 'count',
  plate: 'count', plates: 'count',
  glass: 'count', glasses: 'count',
  handful: 'count', handfuls: 'count',
  scoop: 'count', scoops: 'count',
  // A model sometimes answers with the food itself as the unit ("2 eggs" -> unit "eggs").
  egg: 'count', eggs: 'count',
  // Weight. British "gramme"/"kilogramme" spellings included — the model is not held to one locale.
  g: 'gram', gm: 'gram', gms: 'gram', gr: 'gram', gram: 'gram', grams: 'gram',
  gramme: 'gram', grammes: 'gram',
  kg: 'kilogram', kgs: 'kilogram', kilo: 'kilogram', kilos: 'kilogram',
  kilogram: 'kilogram', kilograms: 'kilogram',
  kilogramme: 'kilogram', kilogrammes: 'kilogram',
  oz: 'ounce', ounce: 'ounce', ounces: 'ounce',
  lb: 'pound', lbs: 'pound', pound: 'pound', pounds: 'pound',
  // Volume
  ml: 'millilitre', milliliter: 'millilitre', milliliters: 'millilitre',
  millilitre: 'millilitre', millilitres: 'millilitre', cc: 'millilitre',
  l: 'litre', liter: 'litre', liters: 'litre', litre: 'litre', litres: 'litre',
  cup: 'cup', cups: 'cup',
  tbsp: 'tablespoon', tbs: 'tablespoon', tablespoon: 'tablespoon', tablespoons: 'tablespoon',
  tsp: 'teaspoon', teaspoon: 'teaspoon', teaspoons: 'teaspoon',
  floz: 'fluidOunce', fluidounce: 'fluidOunce', fluidounces: 'fluidOunce',
};

/**
 * Resolves a model-supplied unit string to its bound category. Everything that is not a letter is
 * stripped before lookup, which collapses case, surrounding and repeated whitespace, and the
 * punctuation a model attaches to an abbreviation ("g.", "(g)", "fluid-ounce") onto the same key.
 * Only the LOOKUP is normalized — the item's own `unit` is returned to the client as the model wrote
 * it, and the numeric quantity is never touched.
 *
 * An unrecognised unit (e.g. "ruti", "roti") resolves to COUNT: it is the strictest bound, so an
 * unknown unit can never smuggle an absurd number past validation, while still allowing the
 * everyday 1-50 range a real portion lives in.
 */
export function normalizeUnit(unit: string): QuantityKind {
  const normalized = unit.toLowerCase().replace(/[^a-z]/g, '');
  return UNIT_ALIASES[normalized] ?? 'count';
}

/** The quantity ceiling for a unit, via [normalizeUnit]. */
export function maxQuantityForUnit(unit: string): number {
  return QUANTITY_BOUNDS[normalizeUnit(unit)];
}

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

    // The unit is resolved BEFORE the quantity, because it decides what counts as reasonable:
    // 200 is absurd for "pieces" and ordinary for "grams".
    const unit =
      typeof it.unit === 'string' && it.unit.trim().length > 0 ? it.unit.trim() : 'serving';

    const quantity = finiteNonNegative(it.quantity);
    if (quantity === null || quantity === 0 || quantity > maxQuantityForUnit(unit)) {
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
      unit,
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
