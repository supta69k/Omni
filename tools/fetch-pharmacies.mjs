/**
 * One-shot, re-runnable downloader for the pharmacy half of the SOS directory.
 *
 * Queries OpenStreetMap's Overpass API for `amenity=pharmacy` nodes across the same Chittagong +
 * Dhaka window the hospital download used, and writes the two files the seeders read:
 *
 *  - `pharmacies_raw.json` — Overpass's answer, verbatim. The seed script re-joins the original
 *    `addr:*`, website and opening-hours tags out of this file, exactly as it does for hospitals.
 *  - `pharmacies_import.jsonl` — one record per node, flattened to the six fields the seed script
 *    validates: `id` ("osm-<node id>"), `name`, `type`, `lat`, `lng`, `phone`, `rating`, `source`.
 *
 * ## Usage — from the tools/ folder
 *
 *     node fetch-pharmacies.mjs          # fetch and write both files
 *     node fetch-pharmacies.mjs --stdout # print the first few records instead of writing
 *
 * ## Provenance and licence
 *
 * Data © OpenStreetMap contributors, ODbL — the same terms the hospital directory carries. Overpass
 * is a free shared service: the query is bounded to two cities and the script refuses to re-fetch
 * more often than once an hour (a marker file), because hammering it from a tutorial project is how
 * projects get rate-limited.
 *
 * No credential is read and no Firebase connection is opened here — this script only talks to
 * Overpass. Seeding is `seed-hospitals.mjs`'s job.
 */

import { existsSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const RAW_PATH = join(HERE, "pharmacies_raw.json");
const JSONL_PATH = join(HERE, "pharmacies_import.jsonl");
const FETCHED_AT = join(HERE, ".pharmacies-fetched-at");

/** The hospital download's own extent, widened slightly so no pharmacy next to a seeded hospital is missed. */
const BBOX = "22.25,90.20,23.95,91.95";

const OVERPASS_URL = "https://overpass-api.de/api/interpreter";
const MIN_REFETCH_MS = 60 * 60 * 1000;
const STDOUT = process.argv.includes("--stdout");

/** One query, two selectors: `amenity` is the canonical tag, `healthcare` the alias some mappers use. */
const QUERY = `[out:json][timeout:90];
(
  node["amenity"="pharmacy"](${BBOX});
  node["healthcare"="pharmacy"](${BBOX});
);
out body;`;

/** A name is the one field the app cannot render without — an unnamed node is not a card. */
function recordOf(element) {
  const tags = element.tags ?? {};
  const name = typeof tags.name === "string" ? tags.name.trim() : "";
  if (!name) return null;

  const phone = [tags.phone, tags["contact:phone"], tags["contact:mobile"]]
    .find((value) => typeof value === "string" && value.trim());

  // The card's subtitle. A 24/7 pharmacy is the single most useful thing to know at SOS o'clock,
  // and OSM carries it — fold it into the same field the hospital seed uses for "Emergency | 24/7".
  const alwaysOpen = /24\/7/.test(tags.opening_hours ?? "");
  const type = alwaysOpen ? "Pharmacy • 24/7" : "Pharmacy";

  return {
    id: `osm-${element.id}`,
    name,
    type,
    lat: element.lat,
    lng: element.lon,
    phone: phone?.trim() ?? null,
    rating: null,
    source: "openstreetmap",
  };
}

async function main() {
  if (
    !STDOUT &&
    existsSync(FETCHED_AT) &&
    Date.now() - statSync(FETCHED_AT).mtimeMs < MIN_REFETCH_MS
  ) {
    console.error(
      "Overpass was queried within the last hour (see .pharmacies-fetched-at). " +
        "Delete that marker to force a re-fetch.",
    );
    process.exitCode = 1;
    return;
  }

  console.log(`Querying Overpass for pharmacies in ${BBOX}…`);
  const response = await fetch(OVERPASS_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      // Overpass 406s anonymous requests with no UA; name the project honestly instead of faking one.
      "User-Agent": "Omni/1.0 (student health app; directory seeding; contact: omni app repo)",
    },
    body: `data=${encodeURIComponent(QUERY)}`,
  });
  if (!response.ok) {
    console.error(`Overpass answered ${response.status} ${response.statusText}`);
    process.exitCode = 1;
    return;
  }

  const payload = await response.json();
  const elements = payload.elements ?? [];

  // Overpass can hand back the same node twice when both selectors match; the id is the dedup key,
  // the same one the seeder will use as the Firestore document id.
  const byId = new Map();
  let unnamed = 0;
  for (const element of elements) {
    if (element.type !== "node") continue;
    const record = recordOf(element);
    if (record == null) {
      unnamed += 1;
      continue;
    }
    byId.set(record.id, record);
  }

  const records = [...byId.values()].sort((a, b) => a.id.localeCompare(b.id));
  console.log(`${elements.length} elements, ${records.length} named pharmacies, ${unnamed} unnamed skipped`);

  if (STDOUT) {
    for (const record of records.slice(0, 8)) console.log(JSON.stringify(record));
    return;
  }

  writeFileSync(RAW_PATH, `${JSON.stringify(payload, null, 2)}\n`);
  writeFileSync(
    JSONL_PATH,
    records.map((record) => JSON.stringify(record)).join("\n") + "\n",
  );
  writeFileSync(FETCHED_AT, new Date().toISOString());

  // Validate the write by reading it back the way the seeder will.
  const readBack = readFileSync(JSONL_PATH, "utf8").trim().split("\n").map((line) => JSON.parse(line));
  console.log(`Wrote ${RAW_PATH} and ${JSONL_PATH} (${readBack.length} records read back clean).`);
  const withPhone = readBack.filter((record) => record.phone).length;
  const alwaysOpen = readBack.filter((record) => record.type.includes("24/7")).length;
  console.log(`  ${withPhone} with a phone, ${alwaysOpen} open 24/7.`);
}

main().catch((cause) => {
  console.error(`Fetch failed: ${cause.message}`);
  process.exitCode = 1;
});
