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
} as const;

export function validateConfig(): void {
  if (!config.firebaseServiceAccount) {
    console.warn('⚠️ FIREBASE_SERVICE_ACCOUNT not set - using Application Default Credentials');
  }
  if (!config.sendGridApiKey) {
    console.warn('⚠️ SENDGRID_API_KEY not set - emails will not be sent');
  }
}