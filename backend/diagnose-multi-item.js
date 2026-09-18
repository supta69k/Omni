// Diagnostic: reproduce the multi-item meal failure stage by stage.
//
// Sends the EXACT production request body (systemInstruction + responseMimeType + responseSchema +
// temperature 0.2) for three inputs, then replays the backend's own parse + validate steps on the
// response so we can see which layer rejects a multi-item meal.
//
// Production code is not touched. The API key is read from the environment and never printed.
//
// Usage: node diagnose-multi-item.js

const crypto = require('crypto');

const RAW_KEY = process.env.GEMINI_API_KEY;
const MODEL = (process.env.GEMINI_MODEL || 'gemini-3.5-flash-lite').trim();

if (!RAW_KEY) {
  console.error('GEMINI_API_KEY not set');
  process.exit(1);
}

// CMD's `set /p` keeps trailing spaces and a pasted value can arrive wrapped in quotes; either one
// makes an otherwise-valid key fail as API_KEY_INVALID. Strip both, then report WHAT WAS WRONG
// without ever revealing key material.
const GEMINI_API_KEY = RAW_KEY.trim().replace(/^["']|["']$/g, '');

/**
 * Describes the key safely: length, shape, and a short hash so two runs can be compared for
 * equality. A SHA-256 prefix is one-way — it identifies the key without disclosing any of it.
 */
function keyPreflight() {
  const fingerprint = crypto.createHash('sha256').update(GEMINI_API_KEY).digest('hex').slice(0, 8);
  console.log('----- KEY PREFLIGHT (no key material printed) -----');
  console.log(`RAW_LENGTH=${RAW_KEY.length}`);
  console.log(`CLEANED_LENGTH=${GEMINI_API_KEY.length}`);
  console.log(`HAD_SURROUNDING_WHITESPACE=${RAW_KEY !== RAW_KEY.trim()}`);
  console.log(`HAD_SURROUNDING_QUOTES=${/^["']|["']$/.test(RAW_KEY.trim())}`);
  console.log(`CONTAINS_INNER_WHITESPACE=${/\s/.test(GEMINI_API_KEY)}`);
  console.log(`CONTAINS_CR_OR_LF=${/[\r\n]/.test(RAW_KEY)}`);
  console.log(`MATCHES_GOOGLE_KEY_SHAPE=${/^AIza[0-9A-Za-z_-]{35}$/.test(GEMINI_API_KEY)}`);
  console.log(`KEY_FINGERPRINT=sha256:${fingerprint}`);
  console.log(`MODEL=${MODEL}`);
}

/** Confirms the key itself works before blaming the meal text. Uses ListModels, which needs no body. */
async function keyLiveCheck() {
  try {
    const response = await fetch('https://generativelanguage.googleapis.com/v1beta/models', {
      headers: { 'x-goog-api-key': GEMINI_API_KEY },
    });
    const text = await response.text();
    if (!response.ok) {
      let reason = 'UNKNOWN';
      try {
        const err = JSON.parse(text);
        reason = err?.error?.details?.[0]?.reason || err?.error?.status || 'UNKNOWN';
      } catch {
        reason = 'UNPARSEABLE';
      }
      console.log(`KEY_LIVE_CHECK=FAIL http=${response.status} reason=${reason}`);
      return false;
    }
    const models = JSON.parse(text).models || [];
    const hasModel = models.some((m) => m.name === `models/${MODEL}`);
    console.log(`KEY_LIVE_CHECK=OK models_visible=${models.length} target_model_listed=${hasModel}`);
    return true;
  } catch (error) {
    console.log(`KEY_LIVE_CHECK=NETWORK_ERROR`);
    return false;
  }
}

// ---- Copied verbatim from backend/src/gemini.ts (do not edit here) ---------

const MEAL_SYSTEM_PROMPT = [
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

const MEAL_RESPONSE_SCHEMA = {
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
};

// ---- The REAL production parse + validate ---------------------------------
//
// Imported from the compiled backend, never re-implemented here. An earlier version of this file
// carried its own copy of the validator; the copy went stale after the quantity bounds were made
// unit-aware and reported a rejection production no longer performs. A diagnostic that does not run
// the real code can only tell you about itself, so this now requires `dist/` (run `npm run build`
// first) and any drift is impossible by construction.

let parseGeminiJson;
let productionValidate;
try {
  ({ parseGeminiJson, validateAnalysis: productionValidate } = require('./dist/meal-analyze.js'));
} catch (error) {
  console.error('Could not load ./dist/meal-analyze.js — run `npm run build` in backend/ first.');
  process.exit(1);
}

/**
 * Adapts the production validator to the { ok, rejectedBy } shape this runner prints. Production
 * throws InvalidAnalysisError; the endpoint turns that throw into AI_INVALID_RESPONSE (422), so a
 * caught throw here means exactly what a 422 means on the device.
 */
function validateAnalysis(raw) {
  try {
    const result = productionValidate(raw);
    return {
      ok: true,
      itemCount: result.items.length,
      totalCalories: result.totals.calories,
      clarification: result.needsClarification,
    };
  } catch (error) {
    return { ok: false, rejectedBy: error.message };
  }
}

// ---- The run --------------------------------------------------------------

async function runCase(label, mealText) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`;

  const body = {
    systemInstruction: { parts: [{ text: MEAL_SYSTEM_PROMPT }] },
    contents: [{ role: 'user', parts: [{ text: mealText }] }],
    generationConfig: {
      responseMimeType: 'application/json',
      responseSchema: MEAL_RESPONSE_SCHEMA,
      temperature: 0.2,
    },
  };

  console.log(`\n===== CASE ${label} =====`);
  console.log(`INPUT=${mealText}`);

  let response;
  let rawBody;
  try {
    response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': GEMINI_API_KEY },
      body: JSON.stringify(body),
    });
    rawBody = await response.text();
  } catch (error) {
    console.log(`GEMINI_HTTP_STATUS=network_error`);
    console.log(`GEMINI_RESPONSE_RECEIVED=no`);
    console.log(`FINAL_RESULT=NETWORK_ERROR`);
    return;
  }

  console.log(`GEMINI_HTTP_STATUS=${response.status}`);

  let data;
  try {
    data = JSON.parse(rawBody);
  } catch {
    console.log(`GEMINI_RESPONSE_RECEIVED=unparseable_envelope`);
    console.log(`FINAL_RESULT=AI_UNAVAILABLE (503)`);
    return;
  }

  if (!response.ok) {
    const reason = data?.error?.details?.[0]?.reason || data?.error?.status || 'UNKNOWN';
    console.log(`GEMINI_RESPONSE_RECEIVED=no`);
    console.log(`GEMINI_ERROR_REASON=${reason}`);
    console.log(`FINAL_RESULT=AI_UNAVAILABLE (503)`);
    return;
  }

  const candidate = data?.candidates?.[0];
  const parts = candidate?.content?.parts;
  const finishReason = candidate?.finishReason;
  const usage = data?.usageMetadata || {};

  console.log(`GEMINI_RESPONSE_RECEIVED=yes`);
  console.log(`FINISH_REASON=${finishReason}`);
  console.log(`PARTS_COUNT=${Array.isArray(parts) ? parts.length : 'none'}`);
  console.log(
    `TOKENS prompt=${usage.promptTokenCount ?? '?'} ` +
      `candidates=${usage.candidatesTokenCount ?? '?'} ` +
      `thoughts=${usage.thoughtsTokenCount ?? 0} ` +
      `total=${usage.totalTokenCount ?? '?'}`,
  );

  // Exactly what production reads: parts[0].text only.
  const productionText = parts?.[0]?.text;
  // What a parts-joining implementation would read instead.
  const joinedText = Array.isArray(parts)
    ? parts.filter((p) => typeof p?.text === 'string' && !p.thought).map((p) => p.text).join('')
    : undefined;

  console.log(`PARTS0_TEXT_LENGTH=${typeof productionText === 'string' ? productionText.length : 'none'}`);
  console.log(`JOINED_TEXT_LENGTH=${typeof joinedText === 'string' ? joinedText.length : 'none'}`);
  if (Array.isArray(parts)) {
    parts.forEach((p, i) => {
      const keys = Object.keys(p || {}).join(',');
      const len = typeof p?.text === 'string' ? p.text.length : 0;
      console.log(`  part[${i}] keys=[${keys}] thought=${p?.thought === true} textLen=${len}`);
    });
  }

  if (typeof productionText !== 'string' || productionText.length === 0) {
    console.log(`JSON_PARSE=not_attempted (no parts[0].text)`);
    console.log(`FINAL_RESULT=AI_UNAVAILABLE (503) — NO_CANDIDATE_TEXT`);
    return;
  }

  let parsed;
  try {
    parsed = parseGeminiJson(productionText);
    console.log(`JSON_PARSE=ok`);
  } catch (error) {
    console.log(`JSON_PARSE=FAILED (${error.message})`);
    console.log(`TEXT_TAIL_LAST_60_CHARS=${JSON.stringify(productionText.slice(-60))}`);
    console.log(`VALIDATION=not_reached`);
    console.log(`FINAL_RESULT=AI_INVALID_RESPONSE (422)`);
    return;
  }

  const itemCount = Array.isArray(parsed?.items) ? parsed.items.length : 'missing';
  console.log(`ITEM_COUNT=${itemCount}`);
  console.log(`NEEDS_CLARIFICATION=${parsed?.needsClarification}`);

  if (Array.isArray(parsed?.items)) {
    parsed.items.forEach((it, i) => {
      console.log(
        `  item[${i}] name=${JSON.stringify(it?.name)} qty=${it?.quantity} unit=${JSON.stringify(it?.unit)} ` +
          `kcal=${it?.calories} p=${it?.proteinGrams} c=${it?.carbsGrams} f=${it?.fatGrams}`,
      );
    });
  }

  const verdict = validateAnalysis(parsed);
  if (verdict.ok) {
    console.log(`VALIDATION=PASS`);
    console.log(`TOTAL_CALORIES=${verdict.totalCalories}`);
    console.log(`FINAL_RESULT=SUCCESS (200)`);
  } else {
    console.log(`VALIDATION=REJECTED — ${verdict.rejectedBy}`);
    console.log(`TOTAL_CALORIES=n/a`);
    console.log(`FINAL_RESULT=AI_INVALID_RESPONSE (422)`);
  }
}

(async () => {
  keyPreflight();
  const keyOk = await keyLiveCheck();
  console.log('--------------------------------------------------');

  if (!keyOk) {
    console.log('\nABORTING: the key itself was rejected, so nothing can be concluded about');
    console.log('multi-item meals. Fix the key entry and rerun before reading any meal result.');
    return;
  }

  await runCase('A', '2 eggs');
  await runCase('B', '2 eggs, 2 slices of bread and peanut butter');
  await runCase('C', '1 egg, 3 pieces of chicken, 200 grams of rice');
  console.log('\n===== DIAGNOSTIC COMPLETE =====');
})();
