import dotenv from 'dotenv';
dotenv.config();

export const config = {
  // Firebase
  firebaseServiceAccount: process.env.FIREBASE_SERVICE_ACCOUNT
    ? JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT)
    : undefined,

  // SendGrid
  sendGridApiKey: process.env.SENDGRID_API_KEY,
  sendGridFromEmail: process.env.SENDGRID_FROM_EMAIL || 'noreply@omni.app',

  // Server
  port: parseInt(process.env.PORT || '3000', 10),

  // OTP Settings
  otp: {
    length: 6,
    expiryMinutes: 10,
    maxAttempts: 5,
  },

  // Rate Limits
  rateLimits: {
    send: { points: 3, durationMinutes: 10, blockMinutes: 30 },
    verify: { points: 10, durationMinutes: 10, blockMinutes: 10 },
  },

  // Firebase Hosting URL for email verification link
  firebaseHostingUrl: process.env.FIREBASE_HOSTING_URL || 'https://omni-2c987.firebaseapp.com',

  // Gemini AI meal analysis (Phase 7). The key lives ONLY here, read from the Render
  // environment — never shipped in the Android app. It is never logged or returned in a response.
  gemini: {
    apiKey: process.env.GEMINI_API_KEY,
    // A stable, free-tier Gemini Flash-Lite model (verified against ai.google.dev pricing/models,
    // Sep 2026): free of charge for input and output, supports structured JSON output. Overridable
    // by env so a future free model can be swapped in without a code change.
    model: process.env.GEMINI_MODEL || 'gemini-2.5-flash-lite',
    // Guards Gemini (and the free-tier token budget) from an arbitrarily large prompt.
    maxTextLength: 500,
  },
} as const;

export function validateConfig(): void {
  if (!config.firebaseServiceAccount) {
    console.warn('⚠️ FIREBASE_SERVICE_ACCOUNT not set - using Application Default Credentials');
  }
  if (!config.sendGridApiKey) {
    console.warn('⚠️ SENDGRID_API_KEY not set - emails will not be sent');
  }
  if (!config.gemini.apiKey) {
    console.warn('⚠️ GEMINI_API_KEY not set - AI meal analysis will be unavailable');
  }
}