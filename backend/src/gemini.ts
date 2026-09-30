// Gemini client for AI meal analysis (Phase 7).
//
// The Android app never talks to Gemini directly and never holds the API key — every call goes
// through this backend, which reads GEMINI_API_KEY from its own environment. The key is placed in
// the request URL's query string (as Google's REST API requires) and is NEVER logged, returned in a
// response, or included in an error message.
//
// The transport is Node's built-in `fetch` (Node >= 20) rather than an SDK, so no dependency is
// added and the free-tier posture stays obvious. Everything network-touching sits behind
// `GeminiClient` so unit tests inject a fake and never spend real free-tier quota.

/** A model whose parsing/validation the endpoint drives. Real calls or a test fake both fit here. */
export interface GeminiClient {
  /**
   * Sends one meal description and returns the model's raw response text, which the caller parses
   * and validates. Throws [GeminiUnavailableError] / [GeminiRateLimitError] on API trouble.
   */
  analyzeMeal(mealText: string): Promise<string>;
}

/** Gemini (or the network) failed in a way the user can retry later — surfaced as AI_UNAVAILABLE. */
export class GeminiUnavailableError extends Error {
  constructor(message = 'AI service unavailable') {
    super(message);
    this.name = 'GeminiUnavailableError';
  }
}

/** Gemini's own free-tier quota is exhausted (HTTP 429) — distinct from our per-user daily cap. */
export class GeminiRateLimitError extends Error {
  constructor(message = 'AI service quota exceeded') {
    super(message);
    this.name = 'GeminiRateLimitError';
  }
}

/**
 * The controlled system prompt. It pins the model to the one job — parse a meal, estimate nutrition,
 * ask for clarification when the description is too vague — and forbids medical claims, invented
 * quantities, and any output outside the JSON schema (§29 of the master spec).
 */
export const MEAL_SYSTEM_PROMPT = [
  'You are a nutrition estimation assistant inside a health app.',
  'Given a short natural-language description of a meal, identify the food items, interpret the',
  'quantities, and ESTIMATE the nutrition for each item and the meal overall.',
  '',
  'Rules:',
  '- These are ESTIMATES, never exact measurements. Always set "estimated" to true.',
  '- Use common, realistic serving sizes when the user does not give an amount.',
  '- Never invent impossible quantities or absurd calorie values.',
  '- If the description is too vague to estimate even roughly (e.g. "some food"), set',
  '  "needsClarification" to true and put a single short question in "clarificationQuestion".',
  '  Otherwise set "needsClarification" to false and "clarificationQuestion" to null.',
  '- calories are whole or decimal numbers of kilocalories; protein, carbs, fat and fiber are in grams.',
  '- Estimate fiber when the food plausibly contains it (whole grains, vegetables, fruit, legumes,',
  '  nuts); use 0 when the food has none or it cannot be estimated at all.',
  '- All nutrition numbers must be zero or positive. Never return negative values.',
  '- Do NOT diagnose disease, give medical advice, or prescribe diets.',
  '- Return ONLY the structured JSON defined by the schema. No markdown, no prose, no code fences.',
].join('\n');

/**
 * The JSON schema Gemini's structured-output mode is bound to. Keeps the response shape predictable;
 * the caller still validates the *values* afterwards, because a well-formed shape does not make the
 * numbers nutritionally sane (§28).
 */
export const MEAL_RESPONSE_SCHEMA = {
  type: 'object',
  properties: {
    mealName: { type: 'string' },
    items: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          name: { type: 'string' },
          quantity: { type: 'number' },
          unit: { type: 'string' },
          calories: { type: 'number' },
          proteinGrams: { type: 'number' },
          carbsGrams: { type: 'number' },
          fatGrams: { type: 'number' },
          fiberGrams: { type: 'number' },
        },
        required: ['name', 'quantity', 'unit', 'calories', 'proteinGrams', 'carbsGrams', 'fatGrams', 'fiberGrams'],
      },
    },
    totals: {
      type: 'object',
      properties: {
        calories: { type: 'number' },
        proteinGrams: { type: 'number' },
        carbsGrams: { type: 'number' },
        fatGrams: { type: 'number' },
        fiberGrams: { type: 'number' },
      },
      required: ['calories', 'proteinGrams', 'carbsGrams', 'fatGrams', 'fiberGrams'],
    },
    estimated: { type: 'boolean' },
    needsClarification: { type: 'boolean' },
    clarificationQuestion: { type: 'string', nullable: true },
  },
  required: ['mealName', 'items', 'totals', 'estimated', 'needsClarification'],
} as const;

/**
 * A Google API error reason, reduced to something safe to write to a log line.
 *
 * Google's error payloads are `{ error: { code, message, status, details: [{ reason, ... }] } }`.
 * Only the *symbolic* fields are ever taken — never `message`, which can echo request content, and
 * never the URL, which carries the API key in its query string. On top of that the extracted token
 * must look like a Google status enum (`SCREAMING_SNAKE_CASE`, <= 64 chars) or it is discarded: an
 * API key (mixed case, hyphens, ~39 chars) cannot pass that filter, so a malformed or hostile body
 * cannot smuggle a secret into the logs.
 */
const REASON_SHAPE = /^[A-Z][A-Z0-9_]{0,63}$/;

function safeReason(candidate: unknown): string | null {
  if (typeof candidate !== 'string') return null;
  return REASON_SHAPE.test(candidate) ? candidate : null;
}

/**
 * Turns a failed Gemini response into one sanitized reason token, e.g. `PERMISSION_DENIED`,
 * `API_KEY_INVALID`, `INVALID_ARGUMENT`. `details[].reason` wins over `error.status` because it is
 * the more specific of the two (a bad key is `400 INVALID_ARGUMENT` at the top level but
 * `API_KEY_INVALID` in the details). Never throws: an unparseable body is simply `UNKNOWN`.
 */
export function describeGeminiFailure(bodyText: string): string {
  let parsed: any;
  try {
    parsed = JSON.parse(bodyText);
  } catch {
    return 'UNPARSEABLE_ERROR_BODY';
  }

  const details = parsed?.error?.details;
  if (Array.isArray(details)) {
    for (const detail of details) {
      const reason = safeReason(detail?.reason);
      if (reason) return reason;
    }
  }
  return safeReason(parsed?.error?.status) ?? 'UNKNOWN';
}

/**
 * The one diagnostic line this phase adds. Status and reason only — enough to tell a missing key
 * (403 PERMISSION_DENIED) from an invalid one (400 API_KEY_INVALID) from a rejected model
 * (404 NOT_FOUND) from a rejected schema (400 INVALID_ARGUMENT), which the previous code collapsed
 * into a single indistinguishable failure. Nothing user-identifying and no secret is written.
 */
function logGeminiFailure(status: number | string, reason: string): void {
  console.error(`[AI_MEAL_GEMINI_ERROR] status=${status} reason=${reason}`);
}

/** Reads a response body without letting a body-read failure mask the real status. */
async function readBodySafely(response: Response): Promise<string> {
  try {
    return await response.text();
  } catch {
    return '';
  }
}

/** REST implementation against the official Generative Language API. */
export class RestGeminiClient implements GeminiClient {
  constructor(
    private readonly apiKey: string,
    private readonly model: string,
    // Injected so a slow model can't hang a request forever; also lets tests avoid real timers.
    private readonly timeoutMs = 20_000,
  ) {}

  async analyzeMeal(mealText: string): Promise<string> {
    // An unset GEMINI_API_KEY reaches here as an empty string (index.ts passes `apiKey ?? ''`) and
    // would otherwise be indistinguishable from any other rejection. Name it explicitly.
    if (!this.apiKey) {
      logGeminiFailure('none', 'MISSING_API_KEY');
      throw new GeminiUnavailableError();
    }

    const url =
      `https://generativelanguage.googleapis.com/v1beta/models/${this.model}:generateContent` +
      `?key=${this.apiKey}`;

    const body = {
      systemInstruction: { parts: [{ text: MEAL_SYSTEM_PROMPT }] },
      contents: [{ role: 'user', parts: [{ text: mealText }] }],
      generationConfig: {
        responseMimeType: 'application/json',
        responseSchema: MEAL_RESPONSE_SCHEMA,
        // Low temperature: estimation should be steady, not creative.
        temperature: 0.2,
      },
    };

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    let response: Response;
    try {
      response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        signal: controller.signal,
      });
    } catch (error) {
      // Abort, DNS, refused connection — all mean "no answer". The URL (which carries the key) and
      // the error's own message (which can contain the URL) are deliberately never logged; only
      // which of the two cases it was.
      const aborted = (error as any)?.name === 'AbortError';
      logGeminiFailure('none', aborted ? 'REQUEST_TIMEOUT' : 'NETWORK_ERROR');
      throw new GeminiUnavailableError();
    } finally {
      clearTimeout(timer);
    }

    if (!response.ok) {
      const reason = describeGeminiFailure(await readBodySafely(response));
      logGeminiFailure(response.status, reason);
      // Gemini's own quota (429) stays a distinct error; everything else is "unavailable". The raw
      // body is never returned to Android — the handler maps both onto AI_UNAVAILABLE / 503.
      throw response.status === 429 ? new GeminiRateLimitError() : new GeminiUnavailableError();
    }

    const rawBody = await readBodySafely(response);
    let data: any;
    try {
      data = JSON.parse(rawBody);
    } catch {
      logGeminiFailure(response.status, 'UNPARSEABLE_SUCCESS_BODY');
      throw new GeminiUnavailableError();
    }

    const text: string | undefined = data?.candidates?.[0]?.content?.parts?.[0]?.text;
    if (typeof text !== 'string' || text.length === 0) {
      // A 200 with no usable candidate (e.g. a safety block) is still "no answer" to the caller.
      // `finishReason` / `promptFeedback.blockReason` say which, and are Google enums, so they pass
      // the same SCREAMING_SNAKE_CASE filter as an error reason.
      const blocked =
        safeReason(data?.candidates?.[0]?.finishReason) ??
        safeReason(data?.promptFeedback?.blockReason);
      logGeminiFailure(response.status, blocked ?? 'NO_CANDIDATE_TEXT');
      throw new GeminiUnavailableError();
    }
    return text;
  }
}
