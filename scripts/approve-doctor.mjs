// Approve a pending doctor/nutritionist verification request.
//
// Usage (from the project root, Node 18+):
//   OMNI_ADMIN_EMAIL="you@example.com" OMNI_ADMIN_PASSWORD="yourpassword" \
//     node scripts/approve-doctor.mjs <doctorUid> [DOCTOR|NUTRITIONIST]
//
// Requirements:
//   - Your Omni account must already have the admin claim (see the bootstrap note at the bottom).
//   - Find <doctorUid> in the Firebase console: Firestore -> verificationRequests -> the document
//     whose status is "pending". The document id IS the doctor's uid.
//
// It signs you in with Firebase (email/password) to get a valid ID token, then calls the backend's
// admin approve endpoint. Nothing is stored; the password is only read from the environment for this run.

const WEB_API_KEY = process.env.OMNI_WEB_API_KEY || "AIzaSyCqaEfzgI-0LT4ZuLMz9NcvpclI79SvEtE";
const BACKEND_URL = process.env.OMNI_BACKEND_URL || "https://omni-jx01.onrender.com";

const uid = process.argv[2];
const profession = (process.argv[3] || "DOCTOR").toUpperCase();
const email = process.env.OMNI_ADMIN_EMAIL;
const password = process.env.OMNI_ADMIN_PASSWORD;

function fail(msg) {
  console.error("ERROR: " + msg);
  process.exit(1);
}

if (!uid) fail("Pass the doctor's uid as the first argument.");
if (!["DOCTOR", "NUTRITIONIST"].includes(profession)) fail("Profession must be DOCTOR or NUTRITIONIST.");
if (!email || !password) fail("Set OMNI_ADMIN_EMAIL and OMNI_ADMIN_PASSWORD environment variables.");

const signIn = await fetch(
  `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${WEB_API_KEY}`,
  {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, returnSecureToken: true }),
  },
);
const signInBody = await signIn.json();
if (!signIn.ok) fail("Sign-in failed: " + (signInBody.error?.message || signIn.status));
const idToken = signInBody.idToken;
console.log("Signed in as " + email + " (uid " + signInBody.localId + ")");

const approve = await fetch(`${BACKEND_URL}/verification/approve`, {
  method: "POST",
  headers: { "Content-Type": "application/json", Authorization: "Bearer " + idToken },
  body: JSON.stringify({ uid, profession }),
});
const approveBody = await approve.json().catch(() => ({}));
if (!approve.ok) {
  fail(`Approve failed (HTTP ${approve.status}): ${JSON.stringify(approveBody)}` +
    (approve.status === 403 ? "\nYour account is not an admin — see the bootstrap note in this file." : "") +
    (approve.status === 404 ? "\nNo pending request for that uid (already decided, or wrong uid)." : ""));
}
console.log("Approved:", JSON.stringify(approveBody));
console.log(approveBody.comped === false
  ? "Note: comped=false — the doctor was verified but the RevenueCat Omni+ comp did not apply (check the REVENUECAT_SECRET_API_KEY env on Render)."
  : "Doctor is verified and comped Omni+.");

// --- One-time admin bootstrap (only if /verification/approve returns 403) ---
// 1. On Render, set env  ADMIN_BOOTSTRAP_UID = <your uid>  and redeploy.
// 2. Run this once (replace the last call): POST /admin/grant with body { "targetUid": "<your uid>" }
//    using the same signed-in idToken above.
// 3. Remove ADMIN_BOOTSTRAP_UID from Render. Sign out/in of the app once so your token picks up the claim.
