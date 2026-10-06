# Word content

`app/src/main/assets/words.json` is the dictionary Voquab ships with. It is
**content only**. Learning progress lives separately in DataStore, keyed by
`id`, so this file can be regenerated or extended without resetting anyone's
history.

## Adding words

1. Append the new words to `words.txt`, exactly as they were looked up
   (inflected forms, capitals and typos are all fine).
2. Write an entry for each one in `words.json` (an AI agent can do this from
   the schema below).
3. Run `node tools/check-words.mjs --fix`. It validates every entry, checks
   that each line of `words.txt` is covered, and sorts the file by `id`.
4. Release as usual with `bin/changeset`.

**Never change an existing `id`.** Progress is stored against it.

## Schema

```jsonc
{
  "id": "adroit",                 // stable key: lowercase ascii kebab-case
  "term": "adroit",               // display form: accents/capitals allowed ("éminence grise")
  "pos": "adjective",             // noun · verb · adjective · adverb · preposition · prefix · name · exclamation
  "gloss": "skilful",             // ≤ 18 chars. Used on notification answer buttons
  "definition": "Clever and skilful, especially at handling people or tricky situations.",
  "examples": [                   // 1–2. The target word is wrapped in *asterisks*
    "She gave an *adroit* answer…"  // so cards can bold it or blank it out
  ],
  "hook": "French à droit, 'to the right'…",  // one-line origin story / memory hook
  "forms": ["adroitly", "adroitness"],        // related forms (also used to match words.txt)
  "synonyms": ["deft", "nimble"],
  "conversational": 3,            // 1 literary/technical · 2 usable · 3 easy to say in conversation
  "usefulness": 3,                // introduction order: 3 first, 1 last
  "openers": ["That was an adroit bit of parking."],  // 1–2 ready-to-say lines; required when conversational ≥ 2
  "lookedUp": "affably",          // optional: the form in words.txt, when it differs from term
  "shelved": "common"             // optional: starts on the Shelved list. One of
                                  // common · name · offensive · archaic · foreign · fragment
}
```

## Writing guidelines

- **Definitions:** one plain sentence a smart 15-year-old would follow. When
  the word was probably looked up for a less obvious sense (*temporal* as
  "worldly", *pious* as "preachy"), cover that sense.
- **Examples and openers: at most two each, and only two if they really
  differ.** Each should show a different sense (*congeal*: gravy vs. a
  plan), a different form (*furtive* vs. *furtively*), or a clearly
  different situation. Two sentences that say the same thing in a
  different setting don't count: keep one. Most single-sense concrete
  nouns (*ammonite*, *armoire*) only need one. The validator flags pairs
  that share too many words.
- **Examples** are modern and natural, not dictionary-stiff.
- **No respellings.** Pronunciation comes from the 🔊 button
  (text-to-speech in an Australian or British voice).
- **Hooks:** only real etymology. Say "possibly" when the origin is
  uncertain. Never invent one. A good hook links to a word you already know
  (gelid → gelato, incisive → incisor).
- **Openers:** everyday Australian situations like work, footy, share houses,
  coffee, family. They should sound like something you'd actually say, not a
  vocabulary exercise.
- **Shelve** words that are everyday (`success`), names (`Charlemagne`),
  slurs or dated offensive terms, archaic insults, non-English words, and
  prefixes. Give them a short definition anyway.
