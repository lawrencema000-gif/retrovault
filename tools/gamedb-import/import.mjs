#!/usr/bin/env node
// GameDB importer (P12): parses PPSSPP's pinned compat.ini (GPL-2.0-or-later) into
//   1. the baked APK snapshot   app/src/main/assets/gamedb/snapshot.json
//   2. chunked SQL upserts      tools/gamedb-import/out/upsert-*.sql   (run via Supabase)
//
// compat.ini format: [FlagName] sections listing `SERIAL = true` lines. One serial can
// appear in many sections → entry = { serial, flags: [FlagName…] }.
//
// Usage: node import.mjs [path-to-compat.ini]

import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, "..", "..");
const iniPath =
  process.argv[2] ??
  join(repoRoot, "app", "src", "main", "assets", "coresystem", "PPSSPP", "compat.ini");

const SOURCE = "ppsspp-compat.ini@v1.20.4";

// ---- parse ----------------------------------------------------------------
const text = readFileSync(iniPath, "utf8");
const flagsBySerial = new Map(); // "ULJM-05500" -> Set(flags)
let section = null;
let sections = 0;

for (const rawLine of text.split(/\r?\n/)) {
  const line = rawLine.trim();
  if (!line || line.startsWith("#") || line.startsWith(";")) continue;
  const sec = line.match(/^\[([A-Za-z0-9_]+)\]$/);
  if (sec) {
    section = sec[1];
    sections++;
    continue;
  }
  if (!section) continue;
  const kv = line.match(/^([A-Za-z0-9]+)\s*=\s*true\b/i);
  if (!kv) continue;
  const canonical = canonicalSerial(kv[1]);
  if (!canonical) continue;
  if (!flagsBySerial.has(canonical)) flagsBySerial.set(canonical, new Set());
  flagsBySerial.get(canonical).add(section);
}

function canonicalSerial(raw) {
  const s = raw.toUpperCase();
  const m = s.match(/^([A-Z]{2,4})([0-9]{3,6})$/);
  if (!m) return null; // skip non-serial keys
  return `${m[1]}-${m[2]}`;
}

// ---- emit snapshot ---------------------------------------------------------
const entries = [...flagsBySerial.entries()]
  .map(([serial, flags]) => ({ serial, flags: [...flags].sort() }))
  .sort((a, b) => a.serial.localeCompare(b.serial));

const snapshot = {
  schemaVersion: 1,
  source: SOURCE,
  generatedFrom: "compat.ini",
  entryCount: entries.length,
  entries,
};

const snapshotPath = join(repoRoot, "app", "src", "main", "assets", "gamedb", "snapshot.json");
mkdirSync(dirname(snapshotPath), { recursive: true });
writeFileSync(snapshotPath, JSON.stringify(snapshot));
console.log(`snapshot: ${entries.length} serials, ${sections} sections -> ${snapshotPath}`);

// ---- emit SQL upserts (chunked) --------------------------------------------
const outDir = join(here, "out");
mkdirSync(outDir, { recursive: true });
const CHUNK = 200;
let fileIdx = 0;
for (let i = 0; i < entries.length; i += CHUNK) {
  const chunk = entries.slice(i, i + CHUNK);
  const values = chunk
    .map((e) => {
      const flags = JSON.stringify(e.flags).replace(/'/g, "''");
      return `('${e.serial}', '${flags}'::jsonb, '{}'::jsonb, '${SOURCE}')`;
    })
    .join(",\n");
  const sql =
    `insert into gamedb_entries (serial, flags, settings, source) values\n${values}\n` +
    `on conflict (serial) do update set flags = excluded.flags, source = excluded.source, updated_at = now();\n`;
  writeFileSync(join(outDir, `upsert-${String(fileIdx).padStart(2, "0")}.sql`), sql);
  fileIdx++;
}
console.log(`sql: ${fileIdx} chunk file(s) -> ${outDir}`);
