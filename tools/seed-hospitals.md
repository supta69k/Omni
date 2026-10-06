# Seeding the SOS directory: `hospitals` and `pharmacies` (Phase 9)

The SOS screen reads public, read-only Firestore collections called `hospitals` and `pharmacies`,
seeded from real OpenStreetMap data (Chittagong + Dhaka, ~15 km around each city centre; ©
OpenStreetMap contributors, ODbL — carry that credit in the project report).

Both collections share one document schema (`name`, `type`, `lat`, `lng`, `phone`, `rating`,
`source`, plus the OSM provenance fields) and are read by the app through the same mapper
(`DocumentSnapshot.toHospital()`), which is why one script seeds both.

## Files

| File | What it is |
|---|---|
| `hospitals_raw.json` | The raw Overpass API response for hospitals (200 nodes). Do not edit by hand. |
| `hospitals_seed.json` | The same data shaped as records (177 kept; 23 had no name). |
| `hospitals_import.jsonl` | **The hospital seed data the script reads.** One compact JSON object per line. |
| `pharmacies_raw.json` | The raw Overpass response for pharmacies (2204 nodes), from `fetch-pharmacies.mjs`. |
| `pharmacies_import.jsonl` | **The pharmacy seed data the script reads** (2204 records, 12 open 24/7). |
| `fetch-pharmacies.mjs` | The pharmacy downloader — Overpass → the two `pharmacies_*` files. Re-runnable. |
| `seed-hospitals.mjs` | **The seeding script** for both collections. Firebase Admin SDK, idempotent, batched. |
| `package.json` | The scripts' only dependency (`firebase-admin`). |
| `service-account.json` | *You download this* — the private key. Gitignored; never commit it. |

## Seeding the pharmacies

```
node fetch-pharmacies.mjs                                  # Overpass → pharmacies_raw.json + .jsonl
node seed-hospitals.mjs --collection pharmacies \
    --data pharmacies_import.jsonl --raw pharmacies_raw.json --dry-run
node seed-hospitals.mjs --collection pharmacies \
    --data pharmacies_import.jsonl --raw pharmacies_raw.json
```

The same idempotence rules as the hospital import: deterministic `osm-<node id>` document ids,
merge writes, no deletes. `fetch-pharmacies.mjs` throttles itself to one Overpass query per hour
(`.pharmacies-fetched-at` marker file).

## How to run the seed (one-time setup, ~5 minutes)

1. **Download a service-account key**
   Firebase console → ⚙️ **Project settings** → **Service accounts** tab → **Generate new private
   key** → confirm. Save the downloaded file as:
   `E:\Behance\Kotlin Projects\Omni\tools\service-account.json`

   That key grants full read/write on the whole database and bypasses every security rule. It is
   `.gitignore`d and belongs on a developer machine only — never inside `app/`, never in the APK,
   never in a commit. To keep it somewhere else, set `GOOGLE_APPLICATION_CREDENTIALS` to its path.

2. **Install the dependency** (already done once on this machine; repeat after cloning elsewhere):
   ```
   cd "E:\Behance\Kotlin Projects\Omni\tools"
   npm install
   ```

3. **Preview first — this writes nothing and opens no connection:**
   ```
   node seed-hospitals.mjs --dry-run
   ```
   It validates all 177 records, shows what one finished document will look like, and reports
   anything it would skip. Add `--verbose` to list every skip rather than the first ten.

4. **Run the import:**
   ```
   node seed-hospitals.mjs
   ```
   Expected output:
   ```
   Reading ...\hospitals_import.jsonl
     177 records found, 177 valid, 0 skipped
     56 enriched with an address or website from hospitals_raw.json

   Connecting to project omni-2c987, (default) database
   Writing 177 documents to "hospitals"
     committed 177/177

   ------------------------------------------------------------
   records found      : 177
   imported           : 177
   skipped (invalid)  : 0
   failed (on write)  : 0
   ------------------------------------------------------------

   Collection "hospitals" now holds 177 documents.
   ```

## What makes it safe to run again

- **Deterministic ids.** The document id is the record's own `osm-<node id>`, so the same hospital
  always lands on the same document and a second run updates in place.
- **Merge writes.** `set(..., { merge: true })`, so a field added later by hand survives a re-run.
- **No deletes, ever.** A record dropped from the JSONL is not removed from Firestore.
- **Validate first, write second.** Nothing reaches the network until every record is checked, so a
  malformed file cannot leave the collection half-seeded. Invalid records are skipped and reported
  by line number, not silently dropped and not fatal to the rest.
- **Wrong-project guard.** The key's `project_id` is compared with `app/google-services.json` and
  the run aborts if they differ — seeding the wrong project is quiet and annoying to undo.
- **Per-document failure attribution.** A batch is all-or-nothing, so if a commit fails the script
  retries that batch one document at a time to name the record that actually caused it.

## Verifying afterwards

- The script's last line prints the server-side document count (expect **177**).
- Or the Firebase console → Firestore Database → the `hospitals` collection, ~177 documents,
  ids like `osm-266877864`, Bengali names visible.
- Or on the phone: SOS tab → swipe to activate → the sheet lists hospitals sorted by distance
  (location permission granted) or unsorted with an honest label (not granted).

## Field reference

The first seven are the schema `Hospital.toSeedMap()` declares and `DocumentSnapshot.toHospital()`
reads. **Renaming one here means renaming it there** — the app reads by string key, so a mismatch is
a silently empty map, not a compile error.

```
id      string   "osm-<openstreetmap node id>" — stable across re-seeds (the document id)
name    string   the hospital's OSM name (Bengali or English, as surveyed)
type    string   "Emergency | 24/7" when OSM tags emergency=yes, else "Hospital"
lat/lng number   WGS-84 coordinates
phone   string?  the OSM phone / contact:phone tag, or null
rating  null     OSM has no ratings; the card shows the design's default 4.3
source  string   "openstreetmap" — provenance for the report
```

These are re-joined from `hospitals_raw.json`, which the JSONL flattening had dropped. The app
ignores unknown fields, so they cost nothing today and mean the address does not have to be
re-downloaded from OSM the day a hospital card wants to show one. Written only when present:

```
osmId        number   the numeric OSM node id (177 of 177)
osmType      string   "node"
address      string   "House 34, Road 4, …, Uttara, Dhaka, 1230" — joined addr:* tags (56)
nameEn       string   the name:en tag, when it differs from `name` (32)
website      string   website / contact:website (5)
openingHours string   the opening_hours tag, e.g. "24/7" (5)
```

## Re-generating after OSM data changes

The Overpass queries used (15 km around Chittagong 22.3569,91.7832 and Dhaka 23.7806,90.4074):

```
https://overpass-api.de/api/interpreter?data=[out:json][timeout:20];
(node(around:15000,22.3569,91.7832)[amenity=hospital];
 node(around:15000,23.7806,90.4074)[amenity=hospital];);
out center 200;
```

Fetch it, re-run the PowerShell conversion from this file's git history to rebuild
`hospitals_import.jsonl`, then re-run `node seed-hospitals.mjs`. The app needs no change.
