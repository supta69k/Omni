// Diagnostic script for isolating the 404 failure in gemini-2.5-flash-lite generateContent.
//
// Runs four tests (A/B/C/D) progressively adding components until the failure is triggered.
// Never exposes the API key in output.

const GEMINI_API_KEY = process.env.GEMINI_API_KEY;
const MODEL = 'gemini-2.5-flash-lite';

if (!GEMINI_API_KEY) {
  console.error('GEMINI_API_KEY not set');
  process.exit(1);
}

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

async function testRequest(label, body) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`;

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-goog-api-key': GEMINI_API_KEY,
      },
      body: JSON.stringify(body),
    });

    const rawBody = await response.text();
    let data;
    try {
      data = JSON.parse(rawBody);
    } catch {
      console.log(`${label}: HTTP ${response.status} (unparseable body)`);
      return;
    }

    if (!response.ok) {
      const reason = data?.error?.status || data?.error?.details?.[0]?.reason || 'UNKNOWN';
      console.log(`${label}: HTTP ${response.status} reason=${reason}`);
      return;
    }

    const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
    const finishReason = data?.candidates?.[0]?.finishReason;

    if (text) {
      console.log(`${label}: SUCCESS (finishReason=${finishReason}, ${text.length} chars)`);
    } else {
      console.log(`${label}: HTTP 200 but no candidate text (finishReason=${finishReason})`);
    }
  } catch (error) {
    console.log(`${label}: NETWORK ERROR (${error.message})`);
  }
}

(async () => {
  console.log('Isolating gemini-2.5-flash-lite generateContent 404 failure...\n');

  // TEST A — minimal generateContent
  console.log('TEST A: Minimal generateContent (no schema, no config)');
  await testRequest('TEST A', {
    contents: [
      {
        parts: [
          {
            text: 'Reply with exactly: OK',
          },
        ],
      },
    ],
  });

  // TEST B — add system instruction
  console.log('\nTEST B: Add systemInstruction');
  await testRequest('TEST B', {
    systemInstruction: { parts: [{ text: MEAL_SYSTEM_PROMPT }] },
    contents: [
      {
        parts: [
          {
            text: 'Reply with exactly: OK',
          },
        ],
      },
    ],
  });

  // TEST C — add JSON output WITHOUT schema
  console.log('\nTEST C: Add JSON output (no schema yet)');
  await testRequest('TEST C', {
    systemInstruction: { parts: [{ text: MEAL_SYSTEM_PROMPT }] },
    contents: [
      {
        parts: [
          {
            text: 'Reply with exactly: OK',
          },
        ],
      },
    ],
    generationConfig: {
      responseMimeType: 'application/json',
    },
  });

  // TEST D — add the existing MEAL_RESPONSE_SCHEMA
  console.log('\nTEST D: Add existing responseSchema');
  await testRequest('TEST D', {
    systemInstruction: { parts: [{ text: MEAL_SYSTEM_PROMPT }] },
    contents: [
      {
        parts: [
          {
            text: '2 eggs',
          },
        ],
      },
    ],
    generationConfig: {
      responseMimeType: 'application/json',
      responseSchema: MEAL_RESPONSE_SCHEMA,
      temperature: 0.2,
    },
  });

  console.log('\n✓ Diagnostic complete');
})();
