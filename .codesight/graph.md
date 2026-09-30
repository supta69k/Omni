# Dependency Graph

## Most Imported Files (change these carefully)

- `backend\src\types.ts` — imported by **8** files
- `backend\src\fcm.ts` — imported by **6** files
- `backend\src\utils.ts` — imported by **3** files
- `backend\src\email-template.ts` — imported by **3** files
- `backend\src\verification.ts` — imported by **3** files
- `backend\src\config.ts` — imported by **2** files
- `backend\src\gemini.ts` — imported by **2** files
- `backend\src\revenuecat.ts` — imported by **2** files
- `backend\src\like.ts` — imported by **2** files
- `backend\src\comment.ts` — imported by **2** files
- `backend\src\message.ts` — imported by **2** files
- `backend\src\sendgrid.ts` — imported by **1** files
- `backend\src\rate-limiter.ts` — imported by **1** files
- `backend\src\meal-analyze.ts` — imported by **1** files

## Import Map (who imports what)

- `backend\src\types.ts` ← `backend\src\comment.ts`, `backend\src\email-template.ts`, `backend\src\index.ts`, `backend\src\like.ts`, `backend\src\meal-analyze.ts` +3 more
- `backend\src\fcm.ts` ← `backend\src\comment.ts`, `backend\src\index.ts`, `backend\src\like.ts`, `backend\src\message.ts`, `backend\src\verification.ts` +1 more
- `backend\src\utils.ts` ← `backend\src\email-template.ts`, `backend\src\index.ts`, `backend\tests\otp.test.ts`
- `backend\src\email-template.ts` ← `backend\src\index.ts`, `backend\src\sendgrid.ts`, `backend\tests\otp.test.ts`
- `backend\src\verification.ts` ← `backend\src\index.ts`, `backend\tests\doctor-verification.test.ts`, `backend\tests\phase12r.test.ts`
- `backend\src\config.ts` ← `backend\src\index.ts`, `backend\src\sendgrid.ts`
- `backend\src\gemini.ts` ← `backend\src\index.ts`, `backend\tests\meal-analyze.test.ts`
- `backend\src\revenuecat.ts` ← `backend\src\index.ts`, `backend\src\verification.ts`
- `backend\src\like.ts` ← `backend\src\index.ts`, `backend\tests\phase12r.test.ts`
- `backend\src\comment.ts` ← `backend\src\index.ts`, `backend\tests\phase12r.test.ts`
