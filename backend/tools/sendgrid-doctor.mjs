#!/usr/bin/env node
/**
 * sendgrid-doctor — why did an accepted email never arrive?
 *
 * `/otp/send` returns 200 only after `await sendOtpEmail()` resolves, and that resolves only when
 * SendGrid answers 202 Accepted. So a 200 proves SendGrid took the message — and proves nothing at
 * all about delivery. Everything that silently eats a 202'd message lives on SendGrid's side or at
 * the receiving mailbox: a suppressed recipient, an unauthenticated sender domain, an account under
 * compliance review, or a spam-foldered inbox.
 *
 * This script asks SendGrid those questions directly. It is READ-ONLY unless you pass --unsuppress.
 *
 * Usage:
 *   node tools/sendgrid-doctor.mjs <recipient-email> [--unsuppress]
 *
 * The API key is read from SENDGRID_API_KEY in the environment or in backend/.env (gitignored).
 * The key is never printed, logged, or written anywhere by this script.
 */

import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const envPath = join(here, '..', '.env');

function loadApiKey() {
  if (process.env.SENDGRID_API_KEY) return process.env.SENDGRID_API_KEY.trim();
  if (!existsSync(envPath)) return null;
  for (const line of readFileSync(envPath, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*SENDGRID_API_KEY\s*=\s*(.*)$/);
    if (m) return m[1].trim().replace(/^["']|["']$/g, '');
  }
  return null;
}

const recipient = process.argv[2];
const doUnsuppress = process.argv.includes('--unsuppress');

if (!recipient || recipient.startsWith('--')) {
  console.error('Usage: node tools/sendgrid-doctor.mjs <recipient-email> [--unsuppress]');
  process.exit(2);
}

const apiKey = loadApiKey();
if (!apiKey) {
  console.error('✗ No SENDGRID_API_KEY found.');
  console.error('  Put it in backend/.env as:  SENDGRID_API_KEY=SG.xxxxx');
  console.error('  (backend/.gitignore already excludes .env, and this script never prints the key.)');
  process.exit(2);
}

const API = 'https://api.sendgrid.com';

async function sg(path, method = 'GET') {
  const res = await fetch(API + path, {
    method,
    headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
  });
  let body = null;
  const text = await res.text();
  if (text) {
    try { body = JSON.parse(text); } catch { body = text; }
  }
  return { status: res.status, ok: res.ok, body };
}

const line = (s = '') => console.log(s);
const head = (s) => { line(); line('═'.repeat(70)); line(s); line('═'.repeat(70)); };

/** SendGrid's suppression groups. A hit on any of these means 202 then silently dropped. */
const SUPPRESSIONS = [
  ['Bounces',          '/v3/suppression/bounces'],
  ['Blocks',           '/v3/suppression/blocks'],
  ['Spam reports',     '/v3/suppression/spam_reports'],
  ['Invalid emails',   '/v3/suppression/invalid_emails'],
  ['Global unsubscribes', '/v3/asm/suppressions/global'],
];

const findings = [];

head(`SendGrid delivery diagnosis for: ${recipient}`);

// ---------------------------------------------------------------- 1. key + account
head('1. API key and account');
const scopes = await sg('/v3/scopes');
if (scopes.status === 401) {
  line('✗ The API key is INVALID or revoked (401 from SendGrid).');
  line('  This key cannot send mail at all. Regenerate it in SendGrid → Settings → API Keys,');
  line('  then update SENDGRID_API_KEY on Render and redeploy.');
  process.exit(1);
}
if (!scopes.ok) {
  line(`⚠ Could not read scopes (HTTP ${scopes.status}). Continuing.`);
} else {
  const s = scopes.body?.scopes || [];
  const canSend = s.includes('mail.send');
  line(`${canSend ? '✓' : '✗'} mail.send permission: ${canSend ? 'present' : 'MISSING'}`);
  if (!canSend) findings.push('The API key lacks the mail.send scope.');
  const canReadActivity = s.some((x) => x.startsWith('messages.read'));
  line(`${canReadActivity ? '✓' : '·'} messages.read (Email Activity): ${canReadActivity ? 'present' : 'absent'}`);
  const canSuppress = s.some((x) => x.includes('suppression'));
  line(`${canSuppress ? '✓' : '·'} suppression read: ${canSuppress ? 'present' : 'absent — suppression checks below may 403'}`);
}

const account = await sg('/v3/user/account');
if (account.ok) {
  line(`· Account type: ${account.body?.type ?? 'unknown'}`);
  if (account.body?.reputation !== undefined) line(`· Sender reputation: ${account.body.reputation}`);
} else {
  line(`· Account details unavailable (HTTP ${account.status}) — key may be scoped narrowly.`);
}

// ---------------------------------------------------------------- 2. sender identity
head('2. Sender identity (the From address)');
const senders = await sg('/v3/verified_senders');
if (senders.ok) {
  const list = senders.body?.results || [];
  if (list.length === 0) {
    line('✗ NO verified senders on this account.');
    findings.push('No verified Single Sender exists — SendGrid would reject every send with 403.');
  } else {
    line(`Verified Single Senders (${list.length}):`);
    for (const s of list) {
      line(`   • ${s.from_email}${s.from_name ? ` ("${s.from_name}")` : ''} — verified: ${s.verified}`);
      const domain = String(s.from_email).split('@')[1]?.toLowerCase();
      if (['gmail.com', 'yahoo.com', 'outlook.com', 'hotmail.com', 'aol.com'].includes(domain)) {
        findings.push(
          `From address "${s.from_email}" is on a consumer domain (${domain}). ` +
          `Mail sent through SendGrid claiming to be from ${domain} fails DMARC alignment, ` +
          `so Gmail routinely spam-folders or rejects it. This is the #1 cause of "accepted but never arrived".`
        );
      }
    }
  }
} else {
  line(`⚠ Could not list verified senders (HTTP ${senders.status}).`);
}

const domains = await sg('/v3/whitelabel/domains');
if (domains.ok) {
  const list = domains.body || [];
  if (!Array.isArray(list) || list.length === 0) {
    line('✗ No authenticated (whitelabeled) sending domain — no SPF/DKIM signing of your own domain.');
    findings.push('No domain authentication set up. Unauthenticated mail is heavily spam-filtered by Gmail.');
  } else {
    line(`Authenticated domains (${list.length}):`);
    for (const d of list) {
      line(`   • ${d.domain} — valid: ${d.valid}, dkim: ${d.dkim?.valid ?? '?'}, spf: ${d.spf ?? '?'}`);
      if (!d.valid) findings.push(`Domain ${d.domain} is added but NOT validated — its DNS records are missing or wrong.`);
    }
  }
} else {
  line(`⚠ Could not list authenticated domains (HTTP ${domains.status}).`);
}

// ---------------------------------------------------------------- 3. suppression
head(`3. Is ${recipient} on a suppression list?`);
line('A suppressed address is the classic "202 Accepted, nothing delivered" case:');
line('SendGrid takes the message, then drops it without ever attempting delivery.');
line();
let suppressedIn = [];
for (const [label, base] of SUPPRESSIONS) {
  const r = await sg(`${base}/${encodeURIComponent(recipient)}`);
  if (r.status === 404) { line(`   ✓ ${label}: not listed`); continue; }
  if (r.status === 403) { line(`   · ${label}: no permission to check (key scope)`); continue; }
  if (!r.ok) { line(`   ⚠ ${label}: HTTP ${r.status}`); continue; }
  const hit = Array.isArray(r.body) ? r.body.length > 0 : !!r.body;
  if (hit) {
    suppressedIn.push([label, base]);
    line(`   ✗ ${label}: LISTED  ${JSON.stringify(r.body).slice(0, 300)}`);
  } else {
    line(`   ✓ ${label}: not listed`);
  }
}
if (suppressedIn.length) {
  findings.push(
    `${recipient} is suppressed in: ${suppressedIn.map(([l]) => l).join(', ')}. ` +
    `Every send to it is accepted and silently discarded until the entry is removed.`
  );
  if (doUnsuppress) {
    line();
    line('--unsuppress given: removing the entries…');
    for (const [label, base] of suppressedIn) {
      const del = await sg(`${base}/${encodeURIComponent(recipient)}`, 'DELETE');
      line(`   ${del.ok ? '✓ removed from' : `✗ failed to remove from (HTTP ${del.status})`} ${label}`);
    }
  } else {
    line();
    line('   Re-run with --unsuppress to remove these entries and allow delivery again.');
  }
}

// ---------------------------------------------------------------- 4. activity feed
head('4. Email Activity — what actually happened to the messages');
const q = encodeURIComponent(`to_email="${recipient}"`);
const msgs = await sg(`/v3/messages?query=${q}&limit=20`);
if (msgs.status === 403 || msgs.status === 401) {
  line('· Email Activity is not available on this key/plan (HTTP ' + msgs.status + ').');
  line('  The Activity Feed add-on is free for 3 days of history but must be enabled in');
  line('  SendGrid → Activity. Check it in the dashboard instead: it shows Processed →');
  line('  Delivered / Bounced / Blocked / Dropped / Deferred per message, which names the cause outright.');
} else if (!msgs.ok) {
  line(`⚠ Activity query failed (HTTP ${msgs.status}): ${JSON.stringify(msgs.body).slice(0, 300)}`);
} else {
  const list = msgs.body?.messages || [];
  if (list.length === 0) {
    line(`✗ No messages to ${recipient} in the Activity Feed's retention window.`);
    line('  If sends were 202-accepted recently, that points at the address being wrong or');
    line('  the account not recording activity — verify the recipient address on the account.');
    findings.push(`SendGrid has no Activity record of any message to ${recipient}.`);
  } else {
    line(`${list.length} message(s) found:`);
    for (const m of list) {
      line(`   • ${m.last_event_time}  status=${m.status}  from=${m.from_email}  subject="${String(m.subject).slice(0, 50)}"`);
      if (m.status && m.status !== 'delivered') {
        findings.push(`A message to ${recipient} ended in status "${m.status}" — see SendGrid Activity for the reason string.`);
      }
    }
  }
}

// ---------------------------------------------------------------- verdict
head('VERDICT');
if (findings.length === 0) {
  line('No blocking problem found on the SendGrid side.');
  line('If mail still is not arriving, the remaining candidates are, in order:');
  line('  1. It is in Gmail Spam / Promotions — search Gmail for  from:(sendgrid OR omni) in:anywhere');
  line('  2. The recipient address on the Firebase account is not the inbox you are watching.');
  line('  3. The SendGrid account is new and under compliance review (mail accepted, then quarantined).');
} else {
  line(`${findings.length} problem(s) found:`);
  findings.forEach((f, i) => { line(); line(`  ${i + 1}. ${f}`); });
}
line();
