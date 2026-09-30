// One-time diagnostic: query Gemini ListModels to see what's available to this API key.
// Run with: node diagnose-gemini.js
// The key is read from the environment and never printed.

const https = require('https');

const apiKey = process.env.GEMINI_API_KEY;
if (!apiKey) {
  console.error('GEMINI_API_KEY not set');
  process.exit(1);
}

const url = `https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey}`;

https.get(url, (res) => {
  let data = '';
  res.on('data', chunk => { data += chunk; });
  res.on('end', () => {
    if (res.statusCode !== 200) {
      console.error(`ListModels returned HTTP ${res.statusCode}`);
      try {
        const parsed = JSON.parse(data);
        console.error(`Error: ${parsed.error?.status} - ${parsed.error?.message}`);
      } catch {
        console.error(data.substring(0, 500));
      }
      process.exit(1);
    }

    const models = JSON.parse(data).models || [];
    const flashLite = models.filter(m => m.name.includes('flash-lite'));

    console.log(`=== Total models available: ${models.length} ===\n`);

    console.log('=== Flash-Lite models ===');
    if (flashLite.length === 0) {
      console.log('NONE FOUND');
    } else {
      flashLite.forEach(m => {
        const name = m.name.replace('models/', '');
        const methods = m.supportedGenerationMethods || [];
        const hasGenerate = methods.includes('generateContent') ? '✓' : '✗';
        console.log(`${hasGenerate} ${name}`);
        console.log(`  Methods: ${methods.join(', ')}`);
      });
    }

    console.log('\n=== Checking gemini-2.5-flash-lite specifically ===');
    const target = models.find(m => m.name === 'models/gemini-2.5-flash-lite');
    if (target) {
      console.log('✓ FOUND');
      console.log(`  supportedGenerationMethods: ${target.supportedGenerationMethods?.join(', ')}`);
      console.log(`  inputTokenLimit: ${target.inputTokenLimit}`);
      console.log(`  outputTokenLimit: ${target.outputTokenLimit}`);
    } else {
      console.log('✗ NOT FOUND');
      console.log('\nThis explains the 404. Recommended alternatives:');
      const alternatives = flashLite.filter(m =>
        m.supportedGenerationMethods?.includes('generateContent')
      );
      alternatives.forEach(m => {
        console.log(`  - ${m.name.replace('models/', '')}`);
      });
    }
  });
}).on('error', (err) => {
  console.error('Network error:', err.message);
  process.exit(1);
});
