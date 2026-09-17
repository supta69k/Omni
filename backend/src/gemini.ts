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
  '- calories are whole or decimal numbers of kilocalories; protein, carbs and fat are in grams.',
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
        },
        required: ['name', 'quantity', 'unit', 'calories', 'proteinGrams', 'carbsGrams', 'fatGrams'],
      },
    },
    totals: {
      type: 'object',
      properties: {
        calories: { type: 'number' },
        proteinGrams: { type: 'number' },
        carbsGrams: { type: 'number' },
        fatGrams: { type: 'number' },
      },
      required: ['calories', 'proteinGrams', 'carbsGrams', 'fatGrams'],
    },
    estimated: { type: 'boolean' },
    needsClarification: { type: 'boolean' },
    clarificationQuestion: { type: 'string', nullable: true },
  },
  required: ['mealName', 'items', 'totals', 'estimated', 'needsClarification'],
} as const;

/** REST implementation against the official Generative Language API. */
export class RestGeminiClient implements GeminiClient {
  constructor(
    private readonly apiKey: string,
    private readonly model: string,
    // Injected so a slow model can't hang a request forever; also lets tests avoid real timers.
    private readonly timeoutMs = 20_000,
  ) {}

  async analyzeMeal(mealText: string): Promise<string> {
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
      // Abort, DNS, refused connection — all mean "no answer". The URL (which carries the key) is
      // deliberately not part of this message.
      throw new GeminiUnavailableError();
    } finally {
      clearTimeout(timer);
    }

    if (response.status === 429) {
      throw new GeminiRateLimitError();
    }
    if (!response.ok) {
      throw new GeminiUnavailableError();
    }

    const data = (await response.json()) as any;
    const text: string | undefined = data?.candidates?.[0]?.content?.parts?.[0]?.text;
    if (typeof text !== 'string' || text.length === 0) {
      // A 200 with no usable candidate (e.g. a safety block) is still "no answer" to the caller.
      throw new GeminiUnavailableError();
    }
    return text;
  }
}
