# Seeding the `hospitals` collection (Phase 9)

The SOS screen reads a public, read-only Firestore collection called `hospitals`. This folder holds
everything needed to populate it once, from real OpenStreetMap data (ODbL license, attribution
required — see the end of this file).

## Files

| File | What it is |
|---|---|
| `hospitals_raw.json` | The raw Overpass API response — 200 hospitals within ~15 km of Chittagong and Dhaka city centres. Do not edit by hand. |
| `hospitals_seed.json` | The same data shaped as the app's `Hospital` model (177 kept; 23 had no name and were dropped). |
| `hospitals_import.jsonl` | **The file you import.** One compact JSON object per line — Firestore's console import format. |

## How to import (Firebase console, ~5 minutes)

1. Open the Firebase console → project **omni-2c987** → **Firestore Database**.
2. Start (or open) the database — region `asia-south1` is right for Bangladesh.
3. In Firestore, click the **⋯ menu on the Collections panel → Import documents** (or "Start
   collection" if it is empty — see the note below).
4. Upload `hospitals_import.jsonl`.
5. **Collection ID:** `hospitals`. **Document ID:** leave blank — the import uses each line's `id`
   field, so the app and the OSM node ids stay in step.
6. The security rules already deployed with the app (`firestore.rules`) allow every signed-in client
   to *read* this collection and nobody to write it from a client. Nothing else to configure.

> **If your console shows no bulk-import option** (it is available on the Firestore *data* view's
> three-dot menu — not the Cloud Storage import), the fallback is `npx -y node-firestore-import
> --path hospitals_import.jsonl` from this folder with a service-account key, or asking me to write
> a one-shot seeding screen into the app. The console option exists on every project I have checked;
> try the three-dot menu on the Collections header first.

## Field reference

```
id      string   "osm-<openstreetmap node id>" — stable across re-seeds
name    string   the hospital's OSM name (Bengali or English, as surveyed)
type    string   "Emergency | 24/7" when OSM tags emergency=yes, else "Hospital"
lat/lng number   WGS-84 coordinates
phone   string?  the OSM phone / contact:phone tag, or null
rating  null     OSM has no ratings; the card shows the design's default 4.3
source  string   "openstreetmap" — provenance for the report
```

## Re-generating after OSM data changes

The queries used (15 km around Chittagong 22.3569,91.7832 and Dhaka 23.7806,90.4074):

```
https://overpass-api.de/api/interpreter?data=[out:json][timeout:20];
(node(around:15000,22.3569,91.7832)[amenity=hospital];
 node(around:15000,23.7806,90.4074)[amenity=hospital];);
out center 200;
```

Fetch it, re-run the conversion in the git history of this file, and re-import. The app needs no
change: it re-reads whatever the collection holds.

## Attribution (required by the ODbL)

The hospital data is © OpenStreetMap contributors, licensed under the Open Database License
(ODbL). A project using it should credit OpenStreetMap — the app's About/report page should carry
"Hospital directory data © OpenStreetMap contributors (ODbL)".
