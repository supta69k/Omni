import { describe, it, expect, vi } from 'vitest';
import type { Request, Response } from 'express';
import { createCoachHandler, validateCoachResponse } from '../src/coach.js';

/**
 * The AI Food Coach's server-side rules — the ones the client is never trusted for:
 *
 *  1. metering: a free user gets ONE coach session a day (server-side counter), Omni+ unlimited;
 *  2. the meter runs BEFORE any Gemini spend, and only a *successful* generation consumes quota —
 *     a failed, blocked or unparseable answer never burns a free user's one shot of the day;
 *  3. the week's data is read server-side, so the payload the model sees cannot be forged;
 *  4. an all-empty week gets starter guidance with NO Gemini call and NO quota consumption;
 *  5. entitlement is fail-closed: a RevenueCat failure reads as FREE.
 */
const TODAY = '2026-10-02';
const WINDOW = ['2026-09-26', '2026-09-27', '2026-09-28', '2026-09-29', '2026-09-30', '2026-10-01', TODAY];

function mockReqRes(body: unknown = { today: TODAY }, uid: string | undefined = 'uid-1') {
  const req = { user: uid ? { uid } : undefined, params: {}, body } as unknown as Request;
  const captured: { status: number; json: any } = { status: 200, json: undefined };
  const res = {
    status(code: number) {
      captured.status = code;
      return this;
    },
    json(payload: any) {
      captured.json = payload;
      return this;
    },
  } as unknown as Response;
  return { req, res, captured };
}

interface DayData {
  steps?: number;
  sleepHours?: number;
  waterGlasses?: number;
  fiber?: number;
  calories?: number;
}

interface DepsOptions {
  used?: number;
  hasOmniPlus?: boolean;
  /** Days documents keyed by date — a missing date means no document exists (unlogged). */
  days?: Record<string, DayData>;
  geminiThrows?: boolean;
  geminiBody?: unknown;
}

function makeDeps(options: DepsOptions = {}) {
  const usageSet = vi.fn(async () => {});

  const db = {
    collection: vi.fn((name: string) => ({
      doc: vi.fn((id: string) => {
        if (name === 'users') {
          return {
            get: vi.fn(async () => ({
              data: () => ({ goals: { steps: 10_000, sleepHours: 8, water: 8, fiberGrams: 31, calories: 2_000 } }),
            })),
            collection: vi.fn((sub: string) => ({
              doc: vi.fn((date: string) => {
                if (sub === 'ai_coach_usage') {
                  return { get: vi.fn(async () => ({ data: () => (options.used === undefined ? undefined : { count: options.used }) })), set: usageSet };
                }
                // days subcollection — the ref only needs an identity for getAll
                return { __date: date };
              }),
            })),
          };
        }
        return { doc: vi.fn(() => ({})) };
      }),
    })),
    getAll: vi.fn((...refs: Array<{ __date: string }>) =>
      refs.map((ref) => ({ data: () => (ref.__date ? options.days?.[ref.__date] : undefined) })),
    ),
  };

  const gemini = {
    coachGuidance: vi.fn(async () => {
      if (options.geminiThrows) throw new Error('GeminiUnavailable');
      return JSON.stringify(
        options.geminiBody ?? {
          summary: 'Your steps are trending up while fiber lags behind.',
          focus: 'fiber',
          tips: ['Add a pear at breakfast.', 'Swap white rice for brown at dinner.', 'Keep the morning walk.'],
        },
      );
    }),
  };

  const revenueCat = { hasOmniPlus: vi.fn(async () => options.hasOmniPlus === true) };

  return { deps: { db, gemini, revenueCat, freeDailyLimit: 1 }, usageSet, gemini, revenueCat };
}

function loggedDay(over: DayData = {}): DayData {
  return { steps: 8_000, sleepHours: 7.5, waterGlasses: 6, fiber: 24, calories: 1_800, ...over };
}

describe('AI Food Coach metering', () => {
  it('a free user with quota left gets coached, and the day is metered', async () => {
    const { deps, usageSet, gemini } = makeDeps({ used: 0, days: { [TODAY]: loggedDay() } });
    const { coachDaily } = createCoachHandler(deps);
    const { req, res, captured } = mockReqRes();

    await coachDaily(req, res);

    expect(captured.status).toBe(200);
    expect(captured.json.success).toBe(true);
    expect(captured.json.coach.summary).toContain('fiber');
    expect(gemini.coachGuidance).toHaveBeenCalledTimes(1);
    expect(usageSet).toHaveBeenCalledTimes(1);
    expect(captured.json.usage).toEqual({ used: 1, limit: 1 });
  });

  it('a free user with the day used up gets 429, and Gemini is never called', async () => {
    const { deps, usageSet, gemini } = makeDeps({ used: 1, days: { [TODAY]: loggedDay() } });
    const { coachDaily } = createCoachHandler(deps);
    const { req, res, captured } = mockReqRes();

    await coachDaily(req, res);

    expect(captured.status).toBe(429);
    expect(captured.json.code).toBe('AI_COACH_LIMIT');
    expect(gemini.coachGuidance).not.toHaveBeenCalled();
    expect(usageSet).not.toHaveBeenCalled();
  });

  it('an Omni+ subscriber is unlimited', async () => {
    const { deps, usageSet, gemini } = makeDeps({ used: 9, hasOmniPlus: true, days: { [TODAY]: loggedDay() } });
    const { coachDaily } = createCoachHandler(deps);
    const { req, res, captured } = mockReqRes();

    await coachDaily(req, res);

    expect(captured.status).toBe(200);
    expect(gemini.coachGuidance).toHaveBeenCalledTimes(1);
    expect(usageSet).toHaveBeenCalledTimes(1);
    expect(captured.json.usage.limit).toBeNull();
  });

  it('an all-empty week gets starter guidance with no Gemini call and no quota burned', async () => {
    const { deps, usageSet, gemini } = makeDeps({ used: 0, days: {} });
    const { coachDaily } = createCoachHandler(deps);
    const { req, res, captured } = mockReqRes();

    await coachDaily(req, res);

    expect(captured.status).toBe(200);
    expect(captured.json.starter).toBe(true);
    expect(captured.json.coach.summary).toContain('Log a few days');
    expect(gemini.coachGuidance).not.toHaveBeenCalled();
    expect(usageSet).not.toHaveBeenCalled();
  });

  it('a Gemini failure reads as 503 AI_UNAVAILABLE and never burns the free shot', async () => {
    const { deps, usageSet, gemini } = makeDeps({ used: 0, days: { [TODAY]: loggedDay() }, geminiThrows: true });
    const { coachDaily } = createCoachHandler(deps);
    const { req, res, captured } = mockReqRes();

    await coachDaily(req, res);

    expect(captured.status).toBe(503);
    expect(captured.json.code).toBe('AI_UNAVAILABLE');
    expect(gemini.coachGuidance).toHaveBeenCalledTimes(1);
    expect(usageSet).not.toHaveBeenCalled();
  });

  it('an unparseable coach answer reads as 422 and never burns the free shot', async () => {
    const { deps, usageSet } = makeDeps({
      used: 0,
      days: { [TODAY]: loggedDay() },
      geminiBody: { summary: 42, focus: 'nonsense', tips: 'one tip' },
    });
    const { coachDaily } = createCoachHandler(deps);
    const { req, res, captured } = mockReqRes();

    await coachDaily(req, res);

    expect(captured.status).toBe(422);
    expect(captured.json.code).toBe('AI_INVALID_RESPONSE');
    expect(usageSet).not.toHaveBeenCalled();
  });
});

describe('validateCoachResponse (semantic guardrail)', () => {
  it('accepts a well-formed coach answer and clamps focus to balance when unknown', () => {
    const parsed = validateCoachResponse({ summary: 'Solid week.', focus: 'mystery', tips: ['Walk more.'] });
    expect(parsed.focus).toBe('balance');
    expect(parsed.tips).toEqual(['Walk more.']);
  });

  it('rejects an empty summary, an empty tips list and a non-object', () => {
    expect(() => validateCoachResponse({ summary: '', focus: 'steps', tips: ['a'] })).toThrow();
    expect(() => validateCoachResponse({ summary: 'x', focus: 'steps', tips: [] })).toThrow();
    expect(() => validateCoachResponse(null)).toThrow();
  });
});
