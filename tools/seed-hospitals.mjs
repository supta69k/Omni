/**
 * One-shot, re-runnable importer for the `hospitals` Firestore collection.
 *
 * Reads `hospitals_import.jsonl` (177 real Chittagong + Dhaka hospitals, © OpenStreetMap
 * contributors, ODbL) and writes one document per hospital into project `omni-2c987`.
 *
 * ## Usage — from the tools/ folder
 *
 *     npm install
 *     node seed-hospitals.mjs --dry-run     # validates and reports; touches no network
 *     node seed-hospitals.mjs               # the real import
 *
 * `--dry-run` never loads the credential and never opens a connection, so it is safe to run at any
 * time. Add `--verbose` to list every skipped record instead of the first few.
 *
 * ## The credential
 *
 * Firebase console → Project settings → Service accounts → "Generate new private key" → save the
 * download as `tools/service-account.json`. That path is in `.gitignore` and must stay there: the
 * key grants full read/write on the whole database and bypasses every security rule. It belongs on
 * a developer machine only — never in `app/`, never in the APK, never in a commit.
 *
 * Point the script somewhere else with `GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json`.
 *
 * ## What makes it safe to run twice
 *
 *  - **Deterministic ids.** The document id is the record's own `id` ("osm-266877864"), a stable
 *    OpenStreetMap node id. The same hospital always lands on the same document, so a second run
 *    updates in place and cannot create a duplicate.
 *  - **Merge writes.** Every write is `set(..., { merge: true })`, so a field this script does not
 *    know about — one added later by hand or by the app — survives the re-run untouched.
 *  - **No deletes, ever.** A record removed from the JSONL is *not* removed from Firestore. Cleanup
 *    is a deliberate act, not a side effect of seeding.
 *  - **Validate first, write second.** Nothing reaches the network until every record has been
 *    checked, so a malformed file cannot leave the collection half-seeded.
 *  - **Wrong-project guard.** The credential's project is compared against the one the Android app
 *    is configured for (`app/google-services.json`) and the run aborts if they differ.
 */

import { existsSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const DATA_PATH = join(HERE, "hospitals_import.jsonl");
const OSM_PATH = join(HERE, "hospitals_raw.json");
const APP_CONFIG_PATH = join(HERE, "..", "app", "google-services.json");
const KEY_PATH = process.env.GOOGLE_APPLICATION_CREDENTIALS
  ? resolve(process.env.GOOGLE_APPLICATION_CREDENTIALS)
  : join(HERE, "service-account.json");

const COLLECTION = "hospitals";

/** Firestore's hard limit is 500 operations per batch; 400 leaves room and keeps commits small. */
const BATCH_LIMIT = 400;

const DRY_RUN = process.argv.includes("--dry-run");
const VERBOSE = process.argv.includes("--verbose");

// ---- Validation ---------------------------------------------------------------------------------

/**
 * Firestore rejects some document ids outright, and a rejected id fails the whole batch it is in.
 * Cheaper to catch here, by name, than to read it out of a commit error.
 */
function documentIdProblem(id) {
  if (id.includes("/")) return `contains "/"`;
  if (id === "." || id === "..") return `is "${id}"`;
  if (/^__.*__$/.test(id)) return "is reserved (__…__)";
  if (Buffer.byteLength(id, "utf8") > 1500) return "is longer than 1500 bytes";
  return null;
}

/**
 * Returns a human-readable reason the record cannot be imported, or `null` if it is good.
 *
 * Every check names the offending value, because "record 91 is invalid" is not something anyone can
 * act on — the point of the skip report is that you can go and fix the line.
 */
function validationProblem(record, where, seen) {
  if (typeof record.id !== "string" || record.id.trim().length === 0) {
    return `${where}: missing or empty "id"`;
  }
  const id = record.id.trim();

  const idProblem = documentIdProblem(id);
  if (idProblem) return `${where}: "id" ${idProblem} — not a legal Firestore document id`;

  if (seen.has(id)) return `${where}: duplicate "id" ${id} (already used by ${seen.get(id)})`;

  if (typeof record.name !== "string" || record.name.trim().length === 0) {
    return `${where} (${id}): missing or empty "name"`;
  }
  for (const [field, value, min, max] of [
    ["lat", record.lat, -90, 90],
    ["lng", record.lng, -180, 180],
  ]) {
    if (typeof value !== "number" || !Number.isFinite(value)) {
      return `${where} (${id}): "${field}" must be a finite number, got ${JSON.stringify(value)}`;
    }
    if (value < min || value > max) {
      return `${where} (${id}): "${field}" ${value} is outside [${min}, ${max}]`;
    }
  }
  // 0,0 is in the Gulf of Guinea. It is what a failed geocode looks like, not a hospital.
  if (record.lat === 0 && record.lng === 0) {
    return `${where} (${id}): "lat"/"lng" are both 0 — almost certainly a missing coordinate`;
  }
  if (record.type != null && typeof record.type !== "string") {
    return `${where} (${id}): "type" must be a string or absent`;
  }
  if (record.phone != null && typeof record.phone !== "string") {
    return `${where} (${id}): "phone" must be a string or null`;
  }
  if (record.rating != null && (typeof record.rating !== "number" || !Number.isFinite(record.rating))) {
    return `${where} (${id}): "rating" must be a finite number or null`;
  }
  return null;
}

/** Splits the seed file into what can be imported and what cannot, with a reason for each skip. */
function readAndValidate() {
  const raw = readFileSync(DATA_PATH, "utf8");
  const lines = raw.split(/\r?\n/).filter((line) => line.trim().length > 0);

  const valid = [];
  const skipped = [];
  const seen = new Map();

  for (const [index, line] of lines.entries()) {
    const where = `line ${index + 1}`;

    let record;
    try {
      record = JSON.parse(line);
    } catch (cause) {
      skipped.push(`${where}: not valid JSON — ${cause.message}`);
      continue;
    }

    const problem = validationProblem(record, where, seen);
    if (problem) {
      skipped.push(problem);
      continue;
    }

    const id = record.id.trim();
    seen.set(id, where);
    valid.push({ ...record, id });
  }

  return { found: lines.length, valid, skipped };
}

// ---- The document -------------------------------------------------------------------------------

/**
 * The tags OpenStreetMap holds for each node, indexed by the id the seed file uses.
 *
 * The JSONL was flattened to the six fields the app reads, which dropped the address, the English
 * name and the website that the original download does carry. Re-joining them here means the
 * directory keeps what OSM actually knows instead of a lossy projection of it. Optional: if the raw
 * download is not next to the script, the import still runs, just without the extras.
 */
function readOsmTags() {
  if (!existsSync(OSM_PATH)) return new Map();
  try {
    const elements = JSON.parse(readFileSync(OSM_PATH, "utf8")).elements ?? [];
    return new Map(elements.map((element) => [`osm-${element.id}`, element.tags ?? {}]));
  } catch (cause) {
    console.warn(`  (could not read ${OSM_PATH}: ${cause.message} — importing without extras)`);
    return new Map();
  }
}

/** "House 12, Road 4, Banani, Dhaka, 1213" from OSM's separate `addr:*` tags. */
function addressOf(tags) {
  const street = [tags["addr:housenumber"], tags["addr:street"]].filter(Boolean).join(" ");
  const parts = [street, tags["addr:suburb"], tags["addr:city"], tags["addr:postcode"]]
    .map((part) => (typeof part === "string" ? part.trim() : ""))
    .filter((part) => part.length > 0);
  const unique = parts.filter((part, index) => parts.indexOf(part) === index);
  return unique.length > 0 ? unique.join(", ") : null;
}

/**
 * One Firestore document.
 *
 * The first seven fields are the schema `Hospital.toSeedMap()` declares and `DocumentSnapshot
 * .toHospital()` reads — `name`, `type`, `lat`, `lng`, `phone`, `rating`, `source`. **Do not rename
 * them here without renaming them there**; the app reads by string key, so a typo is a silently
 * empty map rather than a compile error.
 *
 * Everything after that is provenance and detail the app does not read *yet*. `toHospital()` ignores
 * unknown fields, so they cost nothing today and mean the address does not have to be re-downloaded
 * from OSM the day a hospital card wants to show one.
 */
function toDocument(record, tags) {
  const document = {
    name: record.name.trim(),
    type: (typeof record.type === "string" && record.type.trim()) || "Hospital",
    lat: record.lat,
    lng: record.lng,
    phone: (typeof record.phone === "string" && record.phone.trim()) || null,
    // The app drops any rating that is not finite and positive, so an unrated hospital is stored as
    // null rather than 0 — 0 would read as "rated zero stars" instead of "not rated".
    rating: typeof record.rating === "number" && Number.isFinite(record.rating) && record.rating > 0
      ? record.rating
      : null,
    source: (typeof record.source === "string" && record.source.trim()) || "openstreetmap",
  };

  const osmId = Number.parseInt(String(record.id).replace(/^osm-/, ""), 10);
  if (Number.isFinite(osmId)) {
    document.osmId = osmId;
    document.osmType = "node";
  }

  const address = addressOf(tags);
  if (address) document.address = address;

  const website = tags.website ?? tags["contact:website"];
  if (typeof website === "string" && website.trim()) document.website = website.trim();

  const nameEn = tags["name:en"];
  if (typeof nameEn === "string" && nameEn.trim() && nameEn.trim() !== document.name) {
    document.nameEn = nameEn.trim();
  }

  const hours = tags.opening_hours;
  if (typeof hours === "string" && hours.trim()) document.openingHours = hours.trim();

  return document;
}

// ---- Write --------------------------------------------------------------------------------------

/**
 * Commits in batches, then re-tries a failed batch one document at a time.
 *
 * A batch is all-or-nothing: without the retry, one rejected document would be reported as 400
 * failures with one shared message, and the actual culprit would be unnamed. The retry costs one
 * slow pass in the rare case something is wrong and nothing at all when the commit succeeds.
 */
async function importAll(db, prepared) {
  let imported = 0;
  const failures = [];

  for (let start = 0; start < prepared.length; start += BATCH_LIMIT) {
    const slice = prepared.slice(start, start + BATCH_LIMIT);
    const batch = db.batch();
    for (const { id, document } of slice) {
      batch.set(db.collection(COLLECTION).doc(id), document, { merge: true });
    }

    try {
      await batch.commit();
      imported += slice.length;
    } catch (batchError) {
      console.warn(`\n  batch starting at ${start + 1} failed — retrying individually`);
      for (const { id, document } of slice) {
        try {
          await db.collection(COLLECTION).doc(id).set(document, { merge: true });
          imported += 1;
        } catch (cause) {
          failures.push({ id, reason: cause.message });
        }
      }
    }

    console.log(`  committed ${Math.min(start + slice.length, prepared.length)}/${prepared.length}`);
  }

  return { imported, failures };
}

// ---- Reporting ----------------------------------------------------------------------------------

function reportList(title, entries, limit) {
  if (entries.length === 0) return;
  console.log(`\n${title}`);
  const shown = VERBOSE ? entries : entries.slice(0, limit);
  for (const entry of shown) console.log(`  - ${entry}`);
  if (shown.length < entries.length) {
    console.log(`  … and ${entries.length - shown.length} more (re-run with --verbose to see all)`);
  }
}

function summarise({ found, imported, skipped, failures }) {
  console.log(`\n${"-".repeat(60)}`);
  console.log(`records found      : ${found}`);
  console.log(`imported           : ${imported}`);
  console.log(`skipped (invalid)  : ${skipped.length}`);
  console.log(`failed (on write)  : ${failures.length}`);
  console.log("-".repeat(60));

  reportList("Skipped, and why:", skipped, 10);
  reportList("Failed, and why:", failures.map((f) => `${f.id}: ${f.reason}`), 10);
}

// ---- Main ---------------------------------------------------------------------------------------

/**
 * Refuses to write into a project the Android app is not pointed at.
 *
 * Seeding the wrong Firebase project is quiet and annoying to undo — the script reports success, the
 * app shows an empty map, and nothing says why. The app's own `google-services.json` is the truth
 * about which project it talks to, so it is the thing to check against.
 */
function assertProjectMatches(keyProjectId) {
  if (!existsSync(APP_CONFIG_PATH)) return;
  let appProjectId;
  try {
    appProjectId = JSON.parse(readFileSync(APP_CONFIG_PATH, "utf8")).project_info?.project_id;
  } catch {
    return;
  }
  if (appProjectId && appProjectId !== keyProjectId) {
    throw new Error(
      `the service-account key is for project "${keyProjectId}", but the Android app is ` +
      `configured for "${appProjectId}" (app/google-services.json).\n` +
      `Seeding "${keyProjectId}" would leave the app's map empty. Use the matching key.`,
    );
  }
}

async function main() {
  console.log(`Reading ${DATA_PATH}`);
  const { found, valid, skipped } = readAndValidate();
  console.log(`  ${found} records found, ${valid.length} valid, ${skipped.length} skipped`);

  const tagsById = readOsmTags();
  const prepared = valid.map((record) => ({
    id: record.id,
    document: toDocument(record, tagsById.get(record.id) ?? {}),
  }));

  const enriched = prepared.filter(({ document }) => document.address || document.website).length;
  console.log(`  ${enriched} enriched with an address or website from ${OSM_PATH.split(/[\\/]/).pop()}`);

  if (DRY_RUN) {
    console.log("\n--dry-run: nothing was written and no connection was opened.");
    console.log(`\nA document would look like this — ${prepared[0]?.id}:`);
    console.log(JSON.stringify(prepared[0]?.document, null, 2));
    summarise({ found, imported: 0, skipped, failures: [] });
    console.log(`\nWould write ${prepared.length} documents to "${COLLECTION}".`);
    console.log("Run without --dry-run to import.");
    return;
  }

  if (prepared.length === 0) {
    console.error("\nNothing valid to import — stopping before connecting.");
    process.exitCode = 1;
    summarise({ found, imported: 0, skipped, failures: [] });
    return;
  }

  // The key is read here and nowhere else, and only `project_id` is ever printed from it.
  let key;
  try {
    key = JSON.parse(readFileSync(KEY_PATH, "utf8"));
  } catch (cause) {
    console.error(
      `\nCould not read the service-account key at:\n  ${KEY_PATH}\n\n` +
      `Firebase console → Project settings → Service accounts → "Generate new private key",\n` +
      `then save the download as tools/service-account.json (it is already .gitignore'd).\n\n` +
      `(${cause.message})`,
    );
    process.exitCode = 1;
    return;
  }

  assertProjectMatches(key.project_id);

  const { initializeApp, cert, deleteApp } = await import("firebase-admin/app");
  const { getFirestore } = await import("firebase-admin/firestore");

  console.log(`\nConnecting to project ${key.project_id}, (default) database`);
  const app = initializeApp({ credential: cert(key) });
  const db = getFirestore(app);

  try {
    console.log(`Writing ${prepared.length} documents to "${COLLECTION}"`);
    const { imported, failures } = await importAll(db, prepared);

    summarise({ found, imported, skipped, failures });

    // Server-side truth after the write: what the app's listener will actually see.
    try {
      const total = (await db.collection(COLLECTION).count().get()).data().count;
      console.log(`\nCollection "${COLLECTION}" now holds ${total} documents.`);
      if (total > imported) {
        console.log(
          `${total - imported} of them were already there and were not written by this run — ` +
          `this script never deletes.`,
        );
      }
    } catch (cause) {
      console.warn(`\n(could not read back the collection count: ${cause.message})`);
    }
  } finally {
    await deleteApp(app);
  }
}

main().catch((cause) => {
  console.error(`\nImport failed: ${cause.message}`);
  process.exitCode = 1;
});
