// Test a specific Gemini model with the absolute minimum generateContent request.
// Usage: node test-model.js <model-name>

const model = process.argv[2];
const GEMINI_API_KEY = process.env.GEMINI_API_KEY;

if (!model) {
  console.error('Usage: node test-model.js <model-name>');
  process.exit(1);
}

if (!GEMINI_API_KEY) {
  console.error('GEMINI_API_KEY not set');
  process.exit(1);
}

const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`;

const body = {
  contents: [
    {
      parts: [
        {
          text: 'Reply with exactly: OK',
        },
      ],
    },
  ],
};

(async () => {
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
      console.log(`MODEL_TEST=${model}`);
      console.log(`HTTP_STATUS=${response.status}`);
      console.log(`ERROR_REASON=UNPARSEABLE`);
      console.log(`SUCCESS=false`);
      process.exit(1);
    }

    if (!response.ok) {
      const reason = data?.error?.status || data?.error?.details?.[0]?.reason || 'UNKNOWN';
      console.log(`MODEL_TEST=${model}`);
      console.log(`HTTP_STATUS=${response.status}`);
      console.log(`ERROR_REASON=${reason}`);
      console.log(`SUCCESS=false`);
      process.exit(1);
    }

    const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
    const finishReason = data?.candidates?.[0]?.finishReason;

    if (text) {
      console.log(`MODEL_TEST=${model}`);
      console.log(`HTTP_STATUS=200`);
      console.log(`ERROR_REASON=none`);
      console.log(`SUCCESS=true`);
      console.log(`FINISH_REASON=${finishReason}`);
      console.log(`RESPONSE_LENGTH=${text.length} chars`);
    } else {
      console.log(`MODEL_TEST=${model}`);
      console.log(`HTTP_STATUS=200`);
      console.log(`ERROR_REASON=NO_CANDIDATE_TEXT`);
      console.log(`SUCCESS=false`);
      console.log(`FINISH_REASON=${finishReason}`);
    }
  } catch (error) {
    console.log(`MODEL_TEST=${model}`);
    console.log(`HTTP_STATUS=network_error`);
    console.log(`ERROR_REASON=${error.message}`);
    console.log(`SUCCESS=false`);
  }
})();
