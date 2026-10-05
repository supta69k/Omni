// Live probe for POST /ai/coach/daily against the deployed Render backend.
//
// Signs in through Firebase's identitytoolkit REST API (same as the app does), then calls the
// coach endpoint with the account's ID token. Credentials and the web key come from the
// environment only — OMNI_EMAIL, OMNI_PASSWORD, OMNI_WEB_API_KEY.
//
// Usage: OMNI_EMAIL=... OMNI_PASSWORD=... OMNI_WEB_API_KEY=... node scripts/probe-coach.mjs [count]
//   count = optional number of calls to make (default 2), to watch the free-tier metering trip.

import { readFileSync } from 'node:fs';

const WEB_API_KEY = process.env.OMNI_WEB_API_KEY;
const BACKEND_URL = process.env.OMNI_BACKEND_URL || 'https://omni-jx01.onrender.com';
const EMAIL = process.env.OMNI_EMAIL;
const PASSWORD = process.env.OMNI_PASSWORD;
const CALLS = Number(process.argv[2] ?? 2);

if (!WEB_API_KEY || !EMAIL || !PASSWORD) {
  console.error('Set OMNI_EMAIL, OMNI_PASSWORD and OMNI_WEB_API_KEY in the environment.');
  process.exit(1);
}

async function signIn() {
  const response = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${WEB_API_KEY}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: EMAIL, password: PASSWORD, returnSecureToken: true }),
    },
  );
  const body = await response.json();
  if (!response.ok) throw new Error('sign-in failed: ' + (body.error?.message ?? response.status));
  return body.idToken;
}

const token = await signIn();
console.log('signed in as', EMAIL);

const today = new Date().toISOString().slice(0, 10);
for (let i = 1; i <= CALLS; i += 1) {
  const started = Date.now();
  const response = await fetch(`${BACKEND_URL}/ai/coach/daily`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ today }),
  });
  const text = await response.text();
  console.log(`--- call ${i}: HTTP ${response.status} (${Date.now() - started}ms)`);
  console.log(text.length > 900 ? text.slice(0, 900) + '…' : text);
  if (response.status !== 200) break;
}
