# OTP Backend Deployment Guide

## Quick Deploy Options

### Option 1: Google Cloud Run (Recommended)
```bash
cd backend
# Set your Firebase service account
export FIREBASE_SERVICE_ACCOUNT='{"type":"service_account",...}'
# Deploy
gcloud run deploy omni-otp-backend \
  --source . \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars=FIREBASE_SERVICE_ACCOUNT="$FIREBASE_SERVICE_ACCOUNT"
```

### Option 2: Firebase Cloud Functions (TypeScript)
```bash
# In your existing functions directory
npm install firebase-admin express cors helmet rate-limiter-flexible
# Copy index.js logic to functions/src/otp.ts
# Deploy: firebase deploy --only functions
```

### Option 3: Any Node.js Hosting (Render, Railway, Fly.io, etc.)
```bash
cd backend
npm install
npm start
# Set FIREBASE_SERVICE_ACCOUNT env var
```

## Required Environment Variables
| Variable | Description |
|----------|-------------|
| `FIREBASE_SERVICE_ACCOUNT` | Full JSON service account key (single line) |
| `PORT` | Server port (default 3000) |
| `SENDGRID_API_KEY` | For sending real emails |
| `SENDGRID_FROM_EMAIL` | Verified sender email |

## Android App Configuration
Update `FirebaseAuthRepository.kt`:
```kotlin
private val backendBaseUrl = "https://your-cloud-run-url.run.app"
```

## Firestore Index (Required)
Create composite index for `otp_codes` collection:
- Collection: `otp_codes`
- Fields: `uid` (Ascending), `expiresAt` (Ascending)

## Security Rules
```javascript
// In firestore.rules
match /otp_codes/{uid} {
  allow read, write: if request.auth != null && request.auth.uid == uid;
}
```

## Email Integration
Replace `sendOtpEmail()` in `index.js` with your provider:
- **SendGrid**: `@sendgrid/mail`
- **Nodemailer**: `nodemailer` + SMTP
- **Mailgun**: `mailgun-js`
- **Firebase Extensions**: "Trigger Email" extension

## Testing
```bash
# Health check
curl https://your-url/health

# Send OTP (requires Firebase ID token)
curl -X POST https://your-url/otp/send \
  -H "Authorization: Bearer <ID_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{}'

# Verify OTP
curl -X POST https://your-url/otp/verify \
  -H "Authorization: Bearer <ID_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"code": "123456"}'
```

## Rate Limits (Configured)
- **Send OTP**: 3 per 10 min per user
- **Verify OTP**: 10 per 10 min per user
- **Max verify attempts**: 5 per code
- **Code expiry**: 10 minutes

## Production Checklist
- [ ] Deploy backend with real Firebase service account
- [ ] Configure email provider (SendGrid recommended)
- [ ] Update Android app `backendBaseUrl`
- [ ] Add Firestore index for `otp_codes`
- [ ] Add Firestore security rules
- [ ] Test end-to-end on device
- [ ] Monitor Cloud Run / Functions logs