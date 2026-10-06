#!/usr/bin/env node
// Validates app/src/main/assets/words.json against the schema in
// tools/README.md, and checks every entry in tools/words.txt is covered.
// Run after any edit to the word list:  node tools/check-words.mjs
// Pass --fix to rewrite words.json sorted by id with stable formatting.

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const wordsPath = join(root, "app/src/main/assets/words.json");
const sourcePath = join(root, "tools/words.txt");

const SHELVED_REASONS = new Set(["common", "name", "offensive", "archaic", "foreign", "fragment"]);
const GLOSS_MAX = 18;

const words = JSON.parse(readFileSync(wordsPath, "utf8"));
const errors = [];
const err = (id, msg) => errors.push(`${id}: ${msg}`);

const ids = new Set();
for (const w of words) {
  const id = w.id ?? "(missing id)";
  if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(w.id ?? "")) err(id, "id must be lowercase kebab-case ascii");
  if (ids.has(w.id)) err(id, "duplicate id");
  ids.add(w.id);

  for (const f of ["term", "pos", "say", "gloss", "definition"]) {
    if (typeof w[f] !== "string" || !w[f].trim()) err(id, `missing ${f}`);
  }
  if (w.gloss && w.gloss.length > GLOSS_MAX) err(id, `gloss "${w.gloss}" is over ${GLOSS_MAX} chars`);
  for (const f of ["conversational", "usefulness"]) {
    if (![1, 2, 3].includes(w[f])) err(id, `${f} must be 1, 2 or 3`);
  }
  for (const f of ["examples", "forms", "synonyms"]) {
    if (!Array.isArray(w[f])) err(id, `${f} must be an array`);
  }
  if (w.shelved !== undefined && !SHELVED_REASONS.has(w.shelved)) err(id, `unknown shelved reason "${w.shelved}"`);

  const active = w.shelved === undefined;
  const examples = w.examples ?? [];
  if (examples.length < (active ? 2 : 1)) err(id, `needs at least ${active ? 2 : 1} examples`);
  // Every example marks the target word with *asterisks* so cards can
  // bold it (recognition) or blank it out (fill-in-the-blank).
  for (const ex of examples) {
    if (!/\*[^*]+\*/.test(ex)) err(id, `example is missing *marked* word: ${ex}`);
  }
  // Weekly-word candidates need ready-to-say lines.
  if (active && w.conversational >= 2 && (w.openers ?? []).length < 2) {
    err(id, "conversational >= 2 needs at least 2 openers");
  }
}

// Coverage: each looked-up word must match some entry's id, term,
// lookedUp, or forms (case/accent-insensitive).
const norm = (s) => s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().trim();
const known = new Set();
for (const w of words) {
  [w.id, w.term, w.lookedUp, ...(w.forms ?? [])].filter(Boolean).forEach((s) => known.add(norm(s)));
}
const source = readFileSync(sourcePath, "utf8").split(/[,\n]/).map((s) => s.trim()).filter(Boolean);
const missing = source.filter((s) => !known.has(norm(s)));
missing.forEach((s) => errors.push(`words.txt: "${s}" has no entry`));

const active = words.filter((w) => w.shelved === undefined);
const candidates = active.filter((w) => w.conversational >= 2);
console.log(
  `${words.length} words · ${active.length} active · ${words.length - active.length} shelved · ` +
    `${candidates.length} weekly-word candidates · ${source.length} in words.txt`,
);

if (errors.length) {
  console.error(`\n${errors.length} problem(s):\n  ` + errors.join("\n  "));
  process.exit(1);
}

if (process.argv.includes("--fix")) {
  const sorted = [...words].sort((a, b) => a.id.localeCompare(b.id));
  writeFileSync(wordsPath, "[\n" + sorted.map((w) => JSON.stringify(w)).join(",\n") + "\n]\n");
  console.log("Rewrote words.json sorted by id.");
}
console.log("OK");
