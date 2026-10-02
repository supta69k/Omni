// One-time admin bootstrap + approve, for when your account does not yet have the admin claim.
//
// PREREQUISITE: on Render, set env  ADMIN_BOOTSTRAP_UID = <your uid>  and let it redeploy. That is the
// only thing that lets /admin/grant give your account admin. Remove that env var again once this
// succeeds.
//
// Usage (Git Bash / macOS / Linux):
//   OMNI_ADMIN_EMAIL="you@example.com" OMNI_ADMIN_PASSWORD="yourpassword" \
//     node scripts/grant-admin-and-approve.mjs <doctorUid> [DOCTOR|NUTRITIONIST]
//
// PowerShell:
//   $env:OMNI_ADMIN_EMAIL="you@example.com"
//   $env:OMNI_ADMIN_PASSWORD="yourpassword"
//   node scripts/grant-admin-and-approve.mjs <doctorUid> DOCTOR      (no angle brackets around the uid)
//
// It: signs you in, self-grants admin via /admin/grant, signs in again for a fresh token that carries
// the new claim, then approves the doctor.

// The Firebase WEB API key is public-by-design (it ships in the app), but it is read from the
// environment here rather than hardcoded — hardcoded keys in source trip secret scanners and let the
// key drift per project.
const WEB_API_KEY = process.env.OMNI_WEB_API_KEY;
const BACKEND_URL = process.env.OMNI_BACKEND_URL || "https://omni-jx01.onrender.com";

const doctorUid = process.argv[2];
const profession = (process.argv[3] || "DOCTOR").toUpperCase();
const email = process.env.OMNI_ADMIN_EMAIL;
const password = process.env.OMNI_ADMIN_PASSWORD;

function fail(msg) {
  console.error("ERROR: " + msg);
  process.exit(1);
}
if (!doctorUid) fail("Pass the doctor's uid as the first argument (no < > brackets).");
if (!WEB_API_KEY) fail("Set OMNI_WEB_API_KEY (the Firebase Web API key — Project Settings → General).");
if (!email || !password) fail("Set OMNI_ADMIN_EMAIL and OMNI_ADMIN_PASSWORD.");

async function signIn() {
  const res = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${WEB_API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, returnSecureToken: true }),
    },
  );
  const body = await res.json();
  if (!res.ok) fail("Sign-in failed: " + (body.error?.message || res.status));
  return { idToken: body.idToken, uid: body.localId };
}

const first = await signIn();
console.log("Signed in (uid " + first.uid + "). Granting admin…");

const grant = await fetch(`${BACKEND_URL}/admin/grant`, {
  method: "POST",
  headers: { "Content-Type": "application/json", Authorization: "Bearer " + first.idToken },
  body: JSON.stringify({ targetUid: first.uid }),
});
const grantBody = await grant.json().catch(() => ({}));
if (!grant.ok) {
  fail(`Admin grant failed (HTTP ${grant.status}): ${JSON.stringify(grantBody)}` +
    (grant.status === 404 ? "\n-> ADMIN_BOOTSTRAP_UID is not set on Render (add it = your uid, redeploy)." : "") +
    (grant.status === 403 ? "\n-> ADMIN_BOOTSTRAP_UID on Render is set to a DIFFERENT uid than yours." : ""));
}
console.log("Admin granted. Re-signing in for a fresh token that carries the claim…");

// A fresh sign-in mints a new token that includes the just-set admin claim.
const second = await signIn();
const approve = await fetch(`${BACKEND_URL}/verification/approve`, {
  method: "POST",
  headers: { "Content-Type": "application/json", Authorization: "Bearer " + second.idToken },
  body: JSON.stringify({ uid: doctorUid, profession }),
});
const approveBody = await approve.json().catch(() => ({}));
if (!approve.ok) {
  fail(`Approve failed (HTTP ${approve.status}): ${JSON.stringify(approveBody)}` +
    (approve.status === 404 ? "\n-> No pending request for that uid (already decided, or wrong uid)." : ""));
}
console.log("Approved:", JSON.stringify(approveBody));
console.log("Done. Now remove ADMIN_BOOTSTRAP_UID from Render — your admin claim is permanent.");
