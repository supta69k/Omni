import { describe, it, expect, vi } from 'vitest';
import type { Request, Response } from 'express';
import {
  analyzeMealSchema,
  parseGeminiJson,
  validateAnalysis,
  computeTotals,
  createMealAnalyzeHandler,
  InvalidAnalysisError,
  normalizeUnit,
  maxQuantityForUnit,
} from '../src/meal-analyze.js';
import { GeminiClient, GeminiRateLimitError, GeminiUnavailableError } from '../src/gemini.js';

// A model that returns whatever canned text the test hands it — the whole point is to never call
// the real Gemini API (and never spend free-tier quota) from a unit test.
function fakeGemini(text: string): GeminiClient {
  return { analyzeMeal: vi.fn(async () => text) };
}
function throwingGemini(error: Error): GeminiClient {
  return { analyzeMeal: vi.fn(async () => { throw error; }) };
}


/** Minimal Express req/res doubles capturing status + json. */
function mockReqRes(body: unknown, uid: string | undefined) {
  const req = { body, user: uid ? { uid } : undefined } as unknown as Request;
  const captured: { status: number; json: any } = { status: 200, json: undefined };
  const res = {
    status(code: number) { captured.status = code; return this; },
    json(payload: any) { captured.json = payload; return this; },
  } as unknown as Response;
  return { req, res, captured };
}

const goodJson = JSON.stringify({
  mealName: 'Eggs, bread and peanut butter',
  items: [
    { name: 'Egg', quantity: 2, unit: 'large', calories: 144, proteinGrams: 12.6, carbsGrams: 0.8, fatGrams: 9.6 },
    { name: 'Bread', quantity: 2, unit: 'slice', calories: 160, proteinGrams: 6, carbsGrams: 30, fatGrams: 2 },
    { name: 'Peanut butter', quantity: 1, unit: 'tbsp', calories: 190, proteinGrams: 8, carbsGrams: 6, fatGrams: 16 },
  ],
  totals: { calories: 0, proteinGrams: 0, carbsGrams: 0, fatGrams: 0 },
  estimated: true,
  needsClarification: false,
  clarificationQuestion: null,
});

describe('analyzeMealSchema (1: request validation)', () => {
  const schema = analyzeMealSchema(500);

  it('accepts a normal meal description', () => {
    expect(schema.safeParse({ mealText: '2 eggs and toast' }).success).toBe(true);
  });

  it('rejects empty / whitespace-only text', () => {
    expect(schema.safeParse({ mealText: '   ' }).success).toBe(false);
    expect(schema.safeParse({ mealText: '' }).success).toBe(false);
  });

  it('rejects text longer than the configured cap (21: request size limit)', () => {
    expect(schema.safeParse({ mealText: 'x'.repeat(501) }).success).toBe(false);
  });

  it('rejects a missing / non-string field', () => {
    expect(schema.safeParse({}).success).toBe(false);
    expect(schema.safeParse({ mealText: 123 }).success).toBe(false);
  });
});

describe('parseGeminiJson (3, 4: structured parsing & invalid JSON)', () => {
  it('parses clean JSON', () => {
    expect(parseGeminiJson('{"a":1}')).toEqual({ a: 1 });
  });

  it('tolerates an accidental ```json fence', () => {
    expect(parseGeminiJson('```json\n{"a":1}\n```')).toEqual({ a: 1 });
  });

  it('throws InvalidAnalysisError on non-JSON', () => {
    expect(() => parseGeminiJson('sorry, I cannot help with that')).toThrow(InvalidAnalysisError);
  });
});

describe('validateAnalysis', () => {
  it('(3) accepts a well-formed analysis and marks it estimated', () => {
    const result = validateAnalysis(JSON.parse(goodJson));
    expect(result.estimated).toBe(true);
    expect(result.needsClarification).toBe(false);
    expect(result.items).toHaveLength(3);
    expect(result.mealName).toBe('Eggs, bread and peanut butter');
  });

  it('(7, 8) recomputes totals from items, ignoring the model’s own totals', () => {
    const result = validateAnalysis(JSON.parse(goodJson));
    expect(result.totals.calories).toBe(144 + 160 + 190); // 494
    expect(result.totals.proteinGrams).toBeCloseTo(12.6 + 6 + 8, 5); // 26.6
    expect(result.totals.carbsGrams).toBeCloseTo(0.8 + 30 + 6, 5); // 36.8
    expect(result.totals.fatGrams).toBeCloseTo(9.6 + 2 + 16, 5); // 27.6
  });

  it('(5) rejects negative calories', () => {
    const bad = { ...JSON.parse(goodJson), items: [{ name: 'X', quantity: 1, unit: 'x', calories: -5, proteinGrams: 0, carbsGrams: 0, fatGrams: 0 }] };
    expect(() => validateAnalysis(bad)).toThrow(InvalidAnalysisError);
  });

  it('(5) rejects negative macros', () => {
    const bad = { ...JSON.parse(goodJson), items: [{ name: 'X', quantity: 1, unit: 'x', calories: 10, proteinGrams: -1, carbsGrams: 0, fatGrams: 0 }] };
    expect(() => validateAnalysis(bad)).toThrow(InvalidAnalysisError);
  });

  it('(6) rejects an item with a missing name', () => {
    const bad = { ...JSON.parse(goodJson), items: [{ name: '  ', quantity: 1, unit: 'x', calories: 10, proteinGrams: 1, carbsGrams: 1, fatGrams: 1 }] };
    expect(() => validateAnalysis(bad)).toThrow(/missing a name/i);
  });

  it('rejects a missing meal name', () => {
    const bad = { ...JSON.parse(goodJson), mealName: '' };
    expect(() => validateAnalysis(bad)).toThrow(/meal name/i);
  });

  it('(8) rejects an unreasonable quantity', () => {
    const bad = { ...JSON.parse(goodJson), items: [{ name: 'Rice', quantity: 9999, unit: 'cup', calories: 200, proteinGrams: 4, carbsGrams: 45, fatGrams: 0 }] };
    expect(() => validateAnalysis(bad)).toThrow(/quantity/i);
  });

  describe('unit normalization (alias resolution)', () => {
    // Every variant a model has been seen to emit must land on the right bound. "grams" resolving
    // anywhere but `gram` is what rejected a real 200 g rice portion.
    const cases: Array<[string, string, number]> = [
      // Weight
      ['g', 'gram', 5_000], ['gram', 'gram', 5_000], ['grams', 'gram', 5_000],
      ['gramme', 'gram', 5_000], ['grammes', 'gram', 5_000],
      ['kg', 'kilogram', 5], ['kilogram', 'kilogram', 5], ['kilograms', 'kilogram', 5],
      ['kilogramme', 'kilogram', 5], ['kilogrammes', 'kilogram', 5],
      ['oz', 'ounce', 180], ['ounce', 'ounce', 180], ['ounces', 'ounce', 180],
      ['lb', 'pound', 11], ['lbs', 'pound', 11], ['pound', 'pound', 11], ['pounds', 'pound', 11],
      // Count
      ['piece', 'count', 50], ['pieces', 'count', 50], ['pc', 'count', 50], ['pcs', 'count', 50],
      ['whole', 'count', 50], ['egg', 'count', 50], ['eggs', 'count', 50],
      // Volume
      ['ml', 'millilitre', 5_000],
      ['millilitre', 'millilitre', 5_000], ['millilitres', 'millilitre', 5_000],
      ['milliliter', 'millilitre', 5_000], ['milliliters', 'millilitre', 5_000],
      ['l', 'litre', 5], ['litre', 'litre', 5], ['litres', 'litre', 5],
      ['liter', 'litre', 5], ['liters', 'litre', 5],
      // Food-serving
      ['slice', 'count', 50], ['slices', 'count', 50],
      ['cup', 'cup', 20], ['cups', 'cup', 20],
      ['tbsp', 'tablespoon', 50], ['tablespoon', 'tablespoon', 50], ['tablespoons', 'tablespoon', 50],
      ['tsp', 'teaspoon', 150], ['teaspoon', 'teaspoon', 150], ['teaspoons', 'teaspoon', 150],
    ];

    it.each(cases)('normalizes %s to %s (bound %i)', (input, kind, bound) => {
      expect(normalizeUnit(input)).toBe(kind);
      expect(maxQuantityForUnit(input)).toBe(bound);
    });

    it('normalizes case, surrounding and repeated whitespace, and punctuation', () => {
      expect(normalizeUnit('  GRAMS  ')).toBe('gram');
      expect(normalizeUnit('Grams')).toBe('gram');
      expect(normalizeUnit('g.')).toBe('gram');
      expect(normalizeUnit('fluid  ounce')).toBe('fluidOunce');
      expect(normalizeUnit('fluid-ounce')).toBe('fluidOunce');
    });

    it('falls back to the strictest (count) bound for an unknown unit', () => {
      expect(normalizeUnit('ruti')).toBe('count');
      expect(maxQuantityForUnit('roti')).toBe(50);
    });
  });

  describe('unit-aware quantity validation', () => {
    /** Builds a one-item analysis so a single quantity/unit pair can be asserted in isolation. */
    const withItem = (quantity: number, unit: string) => ({
      ...JSON.parse(goodJson),
      items: [{ name: 'Rice', quantity, unit, calories: 260, proteinGrams: 5, carbsGrams: 58, fatGrams: 0.5 }],
    });

    it.each([
      [100, 'grams'], [200, 'grams'], [500, 'grams'], [5_000, 'grams'],
      [200, 'g'], [500, 'gram'],
      [1, 'kg'], [5, 'kg'],
      [2, 'pieces'], [3, 'pieces'], [3, 'piece'],
      [2, 'tablespoons'], [2, 'tbsp'],
    ])('accepts %i %s', (quantity, unit) => {
      expect(validateAnalysis(withItem(quantity, unit)).items[0].quantity).toBe(quantity);
    });

    it.each([
      [5_001, 'grams'], [6, 'kg'], [10, 'kg'],
      [200, 'pieces'], [200, 'tablespoons'], [200, 'tablespoon'],
    ])('rejects %i %s', (quantity, unit) => {
      expect(() => validateAnalysis(withItem(quantity, unit))).toThrow(/quantity/i);
    });

    it('accepts the exact item Gemini returned for "200 grams of rice"', () => {
      // Verbatim from the live diagnostic's CASE C, which this validation used to reject.
      const result = validateAnalysis({
        ...JSON.parse(goodJson),
        items: [
          { name: 'Egg', quantity: 1, unit: 'whole', calories: 72, proteinGrams: 6.3, carbsGrams: 0.4, fatGrams: 4.8 },
          { name: 'Chicken', quantity: 3, unit: 'pieces', calories: 220, proteinGrams: 35, carbsGrams: 0, fatGrams: 7.5 },
          { name: 'Rice', quantity: 200, unit: 'grams', calories: 260, proteinGrams: 5.4, carbsGrams: 57.2, fatGrams: 0.6 },
        ],
      });
      expect(result.items).toHaveLength(3);
      expect(result.items[2]).toMatchObject({ name: 'Rice', quantity: 200, unit: 'grams' });
      expect(result.totals.calories).toBe(72 + 220 + 260);
    });

    it('returns the unit as the model wrote it, normalizing only the lookup', () => {
      expect(validateAnalysis(withItem(200, 'Grams')).items[0].unit).toBe('Grams');
    });
  });

  it('rejects an empty item list on a non-clarification result', () => {
    const bad = { ...JSON.parse(goodJson), items: [] };
    expect(() => validateAnalysis(bad)).toThrow(InvalidAnalysisError);
  });

  it('(9) passes a clarification result through with empty totals', () => {
    const clar = validateAnalysis({
      mealName: 'Rice and fish',
      items: [],
      totals: { calories: 0, proteinGrams: 0, carbsGrams: 0, fatGrams: 0 },
      estimated: true,
      needsClarification: true,
      clarificationQuestion: 'Approximately how much rice and fish did you eat?',
    });
    expect(clar.needsClarification).toBe(true);
    expect(clar.clarificationQuestion).toMatch(/how much/i);
    expect(clar.totals.calories).toBe(0);
    expect(clar.items).toHaveLength(0);
  });
});

describe('computeTotals', () => {
  it('sums and rounds item nutrition', () => {
    const totals = computeTotals([
      { name: 'a', quantity: 1, unit: 'x', calories: 100, proteinGrams: 1.25, carbsGrams: 2.35, fatGrams: 0.15 },
      { name: 'b', quantity: 1, unit: 'x', calories: 50, proteinGrams: 0.15, carbsGrams: 0.15, fatGrams: 0.15 },
    ]);
    expect(totals.calories).toBe(150);
    expect(totals.proteinGrams).toBe(1.4);
  });
});

describe('createMealAnalyzeHandler (end to end with fakes)', () => {
  const deps = (gemini: GeminiClient) =>
    createMealAnalyzeHandler({ gemini, maxTextLength: 500 });

  it('(2) returns 401 when no verified uid is present', async () => {
    const handler = deps(fakeGemini(goodJson));
    const { req, res, captured } = mockReqRes({ mealText: '2 eggs' }, undefined);
    await handler(req, res);
    expect(captured.status).toBe(401);
    expect(captured.json.code).toBe('AUTH_REQUIRED');
  });

  it('(1) returns 400 on invalid input without calling Gemini', async () => {
    const gemini = fakeGemini(goodJson);
    const handler = deps(gemini);
    const { req, res, captured } = mockReqRes({ mealText: '   ' }, 'uid-a');
    await handler(req, res);
    expect(captured.status).toBe(400);
    expect(gemini.analyzeMeal).not.toHaveBeenCalled();
  });

  it('returns a validated result on the happy path', async () => {
    const handler = deps(fakeGemini(goodJson));
    const { req, res, captured } = mockReqRes({ mealText: '2 eggs, 2 slices of bread and peanut butter' }, 'uid-a');
    await handler(req, res);
    expect(captured.status).toBe(200);
    expect(captured.json.success).toBe(true);
    expect(captured.json.result.totals.calories).toBe(494);
    expect(captured.json.result.estimated).toBe(true);
  });

  it('allows many AI meal analyses without an application-level quota', async () => {
    // Users were hitting the old 10/day limit. Prove the limit is gone: 20 analyses succeed.
    const gemini = fakeGemini(goodJson);
    const handler = deps(gemini);
    for (let i = 0; i < 20; i++) {
      const { req, res, captured } = mockReqRes({ mealText: '2 eggs' }, 'uid-a');
      await handler(req, res);
      expect(captured.status).toBe(200);
      expect(captured.json.success).toBe(true);
    }
    expect(gemini.analyzeMeal).toHaveBeenCalledTimes(20);
  });

  it('(4) returns AI_INVALID_RESPONSE when the model returns non-JSON', async () => {
    const handler = deps(fakeGemini('I cannot help with that'));
    const { req, res, captured } = mockReqRes({ mealText: '2 eggs' }, 'uid-a');
    await handler(req, res);
    expect(captured.status).toBe(422);
    expect(captured.json.code).toBe('AI_INVALID_RESPONSE');
  });

  it('(5) returns AI_INVALID_RESPONSE when the model returns negative calories', async () => {
    const negJson = JSON.stringify({
      mealName: 'Bad', items: [{ name: 'X', quantity: 1, unit: 'x', calories: -100, proteinGrams: 0, carbsGrams: 0, fatGrams: 0 }],
      totals: { calories: 0, proteinGrams: 0, carbsGrams: 0, fatGrams: 0 }, estimated: true, needsClarification: false, clarificationQuestion: null,
    });
    const handler = deps(fakeGemini(negJson));
    const { req, res, captured } = mockReqRes({ mealText: 'x' }, 'uid-a');
    await handler(req, res);
    expect(captured.status).toBe(422);
    expect(captured.json.code).toBe('AI_INVALID_RESPONSE');
  });

  it('surfaces Gemini quota (429) as AI_UNAVAILABLE (503)', async () => {
    const handler = deps(throwingGemini(new GeminiRateLimitError()));
    const { req, res, captured } = mockReqRes({ mealText: '2 eggs' }, 'uid-a');
    await handler(req, res);
    expect(captured.status).toBe(503);
    expect(captured.json.code).toBe('AI_UNAVAILABLE');
  });

  it('surfaces a Gemini outage as AI_UNAVAILABLE (503)', async () => {
    const handler = deps(throwingGemini(new GeminiUnavailableError()));
    const { req, res, captured } = mockReqRes({ mealText: '2 eggs' }, 'uid-a');
    await handler(req, res);
    expect(captured.status).toBe(503);
    expect(captured.json.code).toBe('AI_UNAVAILABLE');
  });

  it('(9) returns a clarification result to the client', async () => {
    const clarJson = JSON.stringify({
      mealName: 'Rice and fish', items: [], totals: { calories: 0, proteinGrams: 0, carbsGrams: 0, fatGrams: 0 },
      estimated: true, needsClarification: true, clarificationQuestion: 'How much rice and fish did you eat?',
    });
    const handler = deps(fakeGemini(clarJson));
    const { req, res, captured } = mockReqRes({ mealText: 'rice and fish' }, 'uid-a');
    await handler(req, res);
    expect(captured.status).toBe(200);
    expect(captured.json.result.needsClarification).toBe(true);
  });

  it('(11) processes under the token uid and rejects when only a body uid is present', async () => {
    // Account isolation: ownership comes from the verified token, never a client-supplied body uid.
    // With no verified uid, a body uid must NOT be accepted as a stand-in — the request is 401.
    const handler = deps(fakeGemini(goodJson));
    const { req, res, captured } = mockReqRes({ mealText: '2 eggs', uid: 'uid-attacker' }, undefined);
    await handler(req, res);
    expect(captured.status).toBe(401);
    expect(captured.json.code).toBe('AUTH_REQUIRED');
  });

  it('processes under the verified token uid, ignoring a spoofed body uid', async () => {
    const handler = deps(fakeGemini(goodJson));
    const { req, res, captured } = mockReqRes({ mealText: '2 eggs', uid: 'uid-attacker' }, 'uid-real');
    await handler(req, res);
    expect(captured.status).toBe(200);
    expect(captured.json.success).toBe(true);
  });
});
