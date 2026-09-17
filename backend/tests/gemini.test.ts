import { describe, it, expect, vi, afterEach } from 'vitest';
import {
  describeGeminiFailure,
  RestGeminiClient,
  GeminiRateLimitError,
  GeminiUnavailableError,
} from '../src/gemini.js';

// A stand-in API key, used only to prove it never reaches a log line. Not a real credential.
const FAKE_KEY = 'AIzaSyFakeKeyForTestsOnly_0123456789xy';
const MEAL_TEXT = '2 eggs';

/** Captures everything the client writes to the console during one call. */
function captureLogs() {
  const lines: string[] = [];
  const record = (...args: unknown[]) => { lines.push(args.map(String).join(' ')); };
  vi.spyOn(console, 'error').mockImplementation(record);
  vi.spyOn(console, 'log').mockImplementation(record);
  return lines;
}

function stubFetch(status: number, body: string) {
  const fetchSpy = vi.fn(async () => new Response(body, { status }));
  vi.stubGlobal('fetch', fetchSpy);
  return fetchSpy;
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

// Real shapes returned by the Generative Language API, trimmed to the fields the parser reads.
const permissionDeniedBody = JSON.stringify({
  error: {
    code: 403,
    message: 'Method doesn\'t allow unregistered callers (callers without established identity).',
    status: 'PERMISSION_DENIED',
  },
});
const apiKeyInvalidBody = JSON.stringify({
  error: {
    code: 400,
    message: 'API key not valid. Please pass a valid API key.',
    status: 'INVALID_ARGUMENT',
    details: [{ '@type': 'type.googleapis.com/google.rpc.ErrorInfo', reason: 'API_KEY_INVALID' }],
  },
});
const invalidArgumentBody = JSON.stringify({
  error: { code: 400, message: 'Invalid JSON payload received.', status: 'INVALID_ARGUMENT' },
});
const notFoundBody = JSON.stringify({
  error: { code: 404, message: 'models/x is not found for API version v1beta', status: 'NOT_FOUND' },
});

describe('describeGeminiFailure (sanitized reason extraction)', () => {
  it('reads the top-level status when there are no details', () => {
    expect(describeGeminiFailure(permissionDeniedBody)).toBe('PERMISSION_DENIED');
    expect(describeGeminiFailure(invalidArgumentBody)).toBe('INVALID_ARGUMENT');
    expect(describeGeminiFailure(notFoundBody)).toBe('NOT_FOUND');
  });

  it('prefers the more specific details[].reason over error.status', () => {
    // A rejected key is INVALID_ARGUMENT at the top level; API_KEY_INVALID is the useful half.
    expect(describeGeminiFailure(apiKeyInvalidBody)).toBe('API_KEY_INVALID');
  });

  it('never throws on a body that is not the expected shape', () => {
    expect(describeGeminiFailure('<html>502 Bad Gateway</html>')).toBe('UNPARSEABLE_ERROR_BODY');
    expect(describeGeminiFailure('')).toBe('UNPARSEABLE_ERROR_BODY');
    expect(describeGeminiFailure('{}')).toBe('UNKNOWN');
    expect(describeGeminiFailure(JSON.stringify({ error: {} }))).toBe('UNKNOWN');
    expect(describeGeminiFailure(JSON.stringify({ error: { status: 42 } }))).toBe('UNKNOWN');
  });

  it('discards anything that is not a Google status enum, so a body cannot smuggle a secret out', () => {
    // The filter is SCREAMING_SNAKE_CASE only: a key, a URL or a free-text message cannot pass it.
    const hostile = JSON.stringify({
      error: {
        status: FAKE_KEY,
        details: [
          { reason: `https://generativelanguage.googleapis.com/v1beta/models/x?key=${FAKE_KEY}` },
          { reason: 'user ate 2 eggs' },
        ],
      },
    });
    const reason = describeGeminiFailure(hostile);
    expect(reason).toBe('UNKNOWN');
    expect(reason).not.toContain(FAKE_KEY);
  });
});

describe('RestGeminiClient diagnostics (status + reason, no secrets)', () => {
  it('logs status and reason for a 403 and still throws GeminiUnavailableError', async () => {
    const logs = captureLogs();
    stubFetch(403, permissionDeniedBody);

    await expect(new RestGeminiClient(FAKE_KEY, 'gemini-2.5-flash-lite').analyzeMeal(MEAL_TEXT))
      .rejects.toBeInstanceOf(GeminiUnavailableError);

    expect(logs).toContain('[AI_MEAL_GEMINI_ERROR] status=403 reason=PERMISSION_DENIED');
  });

  it('logs API_KEY_INVALID for a rejected key', async () => {
    const logs = captureLogs();
    stubFetch(400, apiKeyInvalidBody);

    await expect(new RestGeminiClient(FAKE_KEY, 'gemini-2.5-flash-lite').analyzeMeal(MEAL_TEXT))
      .rejects.toBeInstanceOf(GeminiUnavailableError);

    expect(logs).toContain('[AI_MEAL_GEMINI_ERROR] status=400 reason=API_KEY_INVALID');
  });

  it('logs NOT_FOUND when the model name is rejected', async () => {
    const logs = captureLogs();
    stubFetch(404, notFoundBody);

    await expect(new RestGeminiClient(FAKE_KEY, 'made-up-model').analyzeMeal(MEAL_TEXT))
      .rejects.toBeInstanceOf(GeminiUnavailableError);

    expect(logs).toContain('[AI_MEAL_GEMINI_ERROR] status=404 reason=NOT_FOUND');
  });

  it('keeps 429 a rate-limit error while still logging it', async () => {
    const logs = captureLogs();
    stubFetch(429, JSON.stringify({ error: { status: 'RESOURCE_EXHAUSTED' } }));

    await expect(new RestGeminiClient(FAKE_KEY, 'gemini-2.5-flash-lite').analyzeMeal(MEAL_TEXT))
      .rejects.toBeInstanceOf(GeminiRateLimitError);

    expect(logs).toContain('[AI_MEAL_GEMINI_ERROR] status=429 reason=RESOURCE_EXHAUSTED');
  });

  it('names a missing key instead of sending an empty one', async () => {
    const logs = captureLogs();
    const fetchSpy = stubFetch(200, '{}');

    await expect(new RestGeminiClient('', 'gemini-2.5-flash-lite').analyzeMeal(MEAL_TEXT))
      .rejects.toBeInstanceOf(GeminiUnavailableError);

    expect(logs).toContain('[AI_MEAL_GEMINI_ERROR] status=none reason=MISSING_API_KEY');
    expect(fetchSpy).not.toHaveBeenCalled(); // never spend a round-trip on a key we know is absent
  });

  it('reports a 200 with no usable candidate, using the model\'s own finish reason', async () => {
    const logs = captureLogs();
    stubFetch(200, JSON.stringify({ candidates: [{ finishReason: 'SAFETY', content: { parts: [] } }] }));

    await expect(new RestGeminiClient(FAKE_KEY, 'gemini-2.5-flash-lite').analyzeMeal(MEAL_TEXT))
      .rejects.toBeInstanceOf(GeminiUnavailableError);

    expect(logs).toContain('[AI_MEAL_GEMINI_ERROR] status=200 reason=SAFETY');
  });

  it('never writes the API key, the request URL or the meal text to the log', async () => {
    const logs = captureLogs();
    // A body that echoes both secrets back, to prove the sanitizer — not the happy path — is what
    // keeps them out.
    stubFetch(403, JSON.stringify({
      error: { status: 'PERMISSION_DENIED', message: `key=${FAKE_KEY} text=${MEAL_TEXT}` },
    }));

    await expect(new RestGeminiClient(FAKE_KEY, 'gemini-2.5-flash-lite').analyzeMeal(MEAL_TEXT))
      .rejects.toBeInstanceOf(GeminiUnavailableError);

    const all = logs.join('\n');
    expect(all).toContain('status=403 reason=PERMISSION_DENIED');
    expect(all).not.toContain(FAKE_KEY);
    expect(all).not.toContain('key=');
    expect(all).not.toContain('generativelanguage.googleapis.com');
    expect(all).not.toContain(MEAL_TEXT);
  });

  it('still returns the model text on a healthy response', async () => {
    captureLogs();
    const payload = '{"mealName":"Eggs"}';
    stubFetch(200, JSON.stringify({ candidates: [{ content: { parts: [{ text: payload }] } }] }));

    const text = await new RestGeminiClient(FAKE_KEY, 'gemini-2.5-flash-lite').analyzeMeal(MEAL_TEXT);
    expect(text).toBe(payload);
  });
});
