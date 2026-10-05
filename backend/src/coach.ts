// POST /ai/coach/daily — the AI Food Coach's one server call (Omni+ metered).
//
// Split from meal-analyze on purpose: a coach answer is guidance, not an estimate, so it has its
// own prompt, its own schema and — the reason this file exists — its own **metering**. Meal
// analysis is unmetered (Gemini's own quota is the ceiling); the coach spends structured-output
// calls per user per day, so the free tier is capped server-side and Omni+ is unlimited.
//
// Server-side rules this file owns (the client is never trusted for any of them):
//   - identity: the UID comes from the verified ID token, never the body;
//   - the meter: `users/{uid}/ai_coach_usage/{utcDate}` counts *successful* generations only —
//     a failed or rate-limited Gemini call never burns a free user's one shot of the day;
//   - the entitlement: RevenueCat's REST read (fail-closed — an outage reads as FREE, so an
//     outage can only ever downgrade a subscriber to the daily limit for a moment);
//   - the data: the last 7 days of `days/{date}` documents are read server-side, so the payload
//     the model sees cannot be forged by the client. The client only supplies `today` — its own
//     device-local date — which picks the window's end (presentation, not authorization);
//   - no secrets: the Gemini key, the RevenueCat key and every error body stay in the logs' enum
//     vocabulary, and the raw model body is never returned to Android.

import { Request, Response } from 'express';
import admin from 'firebase-admin';
import { z } from 'zod';
import { CoachPayload } from './gemini.js';

/** Structural — index.ts passes the shared RestGeminiClient, which satisfies this exactly. */
export interface CoachGemini {
  coachGuidance(payload: CoachPayload): Promise<string>;
}

export interface CoachRevenueCat {
  hasOmniPlus(appUserId: string): Promise<boolean>;
}

export interface CoachDeps {
  db: admin.firestore.Firestore;
  gemini: CoachGemini;
  revenueCat: CoachRevenueCat;
  /** Free-tier daily cap. Omni+ subscribers are unlimited. */
  freeDailyLimit: number;
}

const coachRequestSchema = z.object({
  // The client's own device-local date — it only picks which 7 days the window ends on. Identity,
  // metering and the data itself are all server-side facts.
  today: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
});

const COACH_FOCUS_VALUES = ['steps', 'sleep', 'water', 'fiber', 'calories', 'balance'] as const;
type CoachFocus = (typeof COACH_FOCUS_VALUES)[number];

const DEFAULT_GOALS = { steps: 10_000, sleepHours: 8, waterGlasses: 8, fiberGrams: 31, calories: 2_000 };

const SEVEN_DAYS = 7;

interface CoachDay {
  date: string;
  steps: number;
  sleepHours: number;
  waterGlasses: number;
  fiberGrams: number;
  calories: number;
  logged: boolean;
}

/** Fails closed: every failure path reads as FREE, so an outage can only downgrade, never upgrade. */
async function hasOmniPlus(deps: CoachDeps, uid: string): Promise<boolean> {
  return deps.revenueCat.hasOmniPlus(uid);
}

function utcDate(offsetDays: number): string {
  return new Date(Date.now() + offsetDays * 86_400_000).toISOString().slice(0, 10);
}

/**
 * The whole coach flow for one request. Throws nothing the route doesn't map: every failure path
 * ends in a typed response, and the only thrown errors are Gemini's own (mapped to 503 below).
 */
export function createCoachHandler(deps: CoachDeps) {

  async function coachDaily(req: Request, res: Response): Promise<void> {
    const uid = (req as any).user?.uid as string | undefined;
    if (!uid) {
      res.status(401).json({ error: 'Unauthenticated' });
      return;
    }

    const parsed = coachRequestSchema.safeParse(req.body);
    if (!parsed.success) {
      res.status(400).json({ error: 'Invalid request', details: parsed.error.flatten() });
      return;
    }
    const today = parsed.data.today;

    // ---- Metering (before any Gemini spend) ------------------------------------------------------
    const usageRef = deps.db.collection('users').doc(uid).collection('ai_coach_usage').doc(utcDate(0));
    const usageSnap = await usageRef.get();
    const used = (usageSnap.data()?.count as number | undefined) ?? 0;
    const omniPlus = await hasOmniPlus(deps, uid);
    if (!omniPlus && used >= deps.freeDailyLimit) {
      console.log('[AI_COACH_LIMIT] uid=' + uid + ' used=' + used);
      res.status(429).json({
        error: 'Your daily AI Coach session is used up. Omni+ gives unlimited coaching.',
        code: 'AI_COACH_LIMIT',
      });
      return;
    }

    // ---- The week's data, read server-side so the model cannot be fed forged numbers -------------
    const windowStart = utcDate(-(SEVEN_DAYS - 1));
    // The window ends at the client's own `today`, so a device ahead of UTC still sees its latest
    // day. The dates themselves come from the server's arithmetic — only the END is client-supplied.
    const dates: string[] = [];
    const end = new Date(today + 'T00:00:00Z').getTime();
    for (let i = SEVEN_DAYS - 1; i >= 0; i -= 1) {
      dates.push(new Date(end - i * 86_400_000).toISOString().slice(0, 10));
    }
    void windowStart;

    const userSnap = await deps.db.collection('users').doc(uid).get();
    const goals = { ...DEFAULT_GOALS, ...((userSnap.data()?.goals as object) ?? {}) };

    const daySnaps = await deps.db.getAll(
      ...dates.map((date) => deps.db.collection('users').doc(uid).collection('days').doc(date)),
    );
    const days: CoachDay[] = dates.map((date, index) => {
      const data = daySnaps[index].data() as Record<string, unknown> | undefined;
      const logged = data !== undefined;
      return {
        date,
        steps: (data?.steps as number | undefined) ?? 0,
        sleepHours: (data?.sleepHours as number | undefined) ?? 0,
        waterGlasses: (data?.waterGlasses as number | undefined) ?? 0,
        fiberGrams: (data?.fiber as number | undefined) ?? 0,
        calories: (data?.calories as number | undefined) ?? 0,
        logged,
      };
    });

    // ---- Nothing logged: the starter guidance costs no Gemini call and no free-tier quota --------
    if (days.every((day) => !day.logged)) {
      console.log('[AI_COACH_STARTER] uid=' + uid);
      res.json({
        success: true,
        starter: true,
        coach: {
          summary:
            'Welcome! I am your AI Coach. Log a few days of meals, steps and sleep and I will start ' +
            'spotting patterns and suggesting small, concrete changes.',
          focus: 'balance',
          tips: [
            'Log at least one meal today — even a rough one helps me learn your habits.',
            'Carry your phone as usual; steps are the easiest metric to start a streak with.',
            'Set your Daily Goals in Settings so I can measure progress against something.',
          ],
        },
        usage: { used, limit: omniPlus ? null : deps.freeDailyLimit },
      });
      return;
    }

    // ---- The one Gemini call ---------------------------------------------------------------------
    const payload: CoachPayload = { goals, days };
    let raw: string;
    try {
      raw = await deps.gemini.coachGuidance(payload);
    } catch (error) {
      // Gemini's own quota (429) and everything else both read as "unavailable" to the caller —
      // and neither consumes the free tier's one shot of the day.
      console.log('[AI_COACH_GEMINI_ERROR] uid=' + uid + ' ' + (error as Error).name);
      res.status(503).json({
        error: 'AI coaching is temporarily unavailable. You can try again in a moment.',
        code: 'AI_UNAVAILABLE',
      });
      return;
    }

    let coach: { summary: string; focus: CoachFocus; tips: string[] };
    try {
      coach = validateCoachResponse(JSON.parse(raw));
    } catch {
      console.log('[AI_COACH_INVALID_RESPONSE] uid=' + uid);
      res.status(422).json({
        error: 'The AI Coach could not read your week. Please try again in a moment.',
        code: 'AI_INVALID_RESPONSE',
      });
      return;
    }

    // Only a *successful* generation consumes quota — a failed or blocked call never burns the
    // free tier's one shot of the day.
    await usageRef.set(
      { count: admin.firestore.FieldValue.increment(1), updatedAt: admin.firestore.FieldValue.serverTimestamp() },
      { merge: true },
    );

    res.json({
      success: true,
      starter: false,
      coach,
      usage: { used: used + 1, limit: omniPlus ? null : deps.freeDailyLimit },
    });
  }

  return { coachDaily };
}

/** Semantic guardrail over the model's answer — same philosophy as meal-analyze's validateAnalysis. */
export function validateCoachResponse(raw: unknown): { summary: string; focus: CoachFocus; tips: string[] } {
  if (raw === null || typeof raw !== 'object') {
    throw new Error('not an object');
  }
  const obj = raw as Record<string, unknown>;

  const summary = typeof obj.summary === 'string' ? obj.summary.trim() : '';
  if (summary.length === 0 || summary.length > 600) {
    throw new Error('invalid summary');
  }

  const focus = COACH_FOCUS_VALUES.includes(obj.focus as CoachFocus) ? (obj.focus as CoachFocus) : 'balance';

  if (!Array.isArray(obj.tips) || obj.tips.length < 1 || obj.tips.length > 5) {
    throw new Error('invalid tips');
  }
  const tips = obj.tips
    .map((tip) => (typeof tip === 'string' ? tip.trim() : ''))
    .filter((tip) => tip.length > 0 && tip.length <= 200);
  if (tips.length === 0) {
    throw new Error('invalid tips');
  }

  return { summary, focus, tips };
}
