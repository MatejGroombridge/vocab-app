# Voquab — Build Plan

A vocabulary app for words met while reading. The goal is to **use** them,
not just recognise them on a flashcard. It is built on the Habit Tracker's
design language and architecture (see `agent.md` §10.7).

**Budget:** under 90 seconds a day, and you never have to open the app. All
learning can happen from the notification shade and the home screen.

---

## 1. Design principles

1. **The app comes to you.** Notifications and widgets deliver most of the
   learning, so opening the app is optional.
2. **Tap, never type.** Every answer is one tap. Most cards are multiple
   choice and grade themselves, so you don't have to rate how well you knew it.
3. **A small fixed daily amount with no backlog.** There is a hard cap on
   cards per day. Missed days don't pile up, and there is never a
   "247 due" counter.
4. **Meaning over memorisation.** Each word comes with the sentence you
   found it in, a one-line origin story and ready-to-say example lines.
   This makes reviews feel like curiosity rather than drilling.
5. **Using a word beats recalling it.** The weekly word, used in a real
   conversation, is the main goal. Flashcards are only scaffolding for it.
6. **No daily guilt.** The only streak is the weekly one. Daily reviews show
   progress but have no streak to break.

---

## 2. How a word moves through the app

```
 Inbox ──► Meet ──► Learning (stages 1–5) ──► Known ──(every ~4 months)──► check
   │         │            │   ▲                  ▲
   │         │      miss: │   │ hit: next stage   │
   │         │   back one stage, due tomorrow     │
   │         ├─ "Already knew it" ────────────────┘
   │         └─ "Not worth learning" ──► Shelved
   └─ ordered most-useful-first
 Using a word in conversation (weekly word or any word) ──► Known immediately
```

### Card types escalate with the stage

| Stage | Interval after a hit | Card | Graded by |
|---|---|---|---|
| Meet (0) | +1 day | Word, pronunciation, **your book sentence**, then definition + origin hook. Just read it. | — ("Got it" / "Already knew it" / "Not worth learning") |
| 1 | +3 days | Word in its sentence → pick the meaning (3 options) | auto |
| 2 | +7 days | Short meaning → pick the word (3 options) | auto |
| 3 | +16 days | Fill-in-the-blank sentence → pick the word (3 options) | auto |
| 4 | +35 days | Definition → *think* of the word → tap to reveal | self ("Had it" / "Didn't") |
| 5 | → Known | Same as 4, but a different example sentence | self |
| Known | +120 days | One multiple-choice card as an occasional check | auto |

- Wrong answers are other words from your own deck with the same part of
  speech, so every quiz gives you extra exposure to other words.
- **A miss** drops the word one stage and brings it back **tomorrow**, never
  again in the same session (that's how cramming loops start). Two misses
  in a row send it back to Meet, so you see the full explanation again.
- Intervals get ±15% random variation so that words introduced together
  don't all come due on the same day.
- I chose a fixed interval ladder (Leitner-style) over FSRS or SM-2 because
  it is predictable, easy to unit-test and works well for recognition
  vocabulary.
- The day rolls over at **4am**, so late-night reviews count for the day
  you're still in.

---

## 3. Features

### 3.1 Today: the daily set *(centre tab, the landing page)*

- Default **5 cards a day** (adjustable 1–10). The header shows progress dots
  (`●●●○○`), not a due count.
- The set is built each day in this order: the most overdue reviews first,
  then **new words** if there's room.
  - New words default to **1 a day**. The app stops adding them
    automatically while more than ~20 words are in stages 1–3, so the
    workload can't snowball.
  - Overdue words beyond the cap roll to tomorrow without being mentioned.
    Their next interval is counted from when you actually reviewed them,
    so skipping a week causes no penalty or pile-up.
- A card answered **anywhere** (app, notification, widget) counts toward
  today's set.
- Done state: "That's today ✓", a small reminder of the weekly word, and
  an optional "One more" button. It's never pushed, and the cap still
  applies.
- Tap 🔊 on any card to hear the word, using the phone's built-in
  text-to-speech (no extra libraries).

### 3.2 Weekly word + streak *(left tab, "This Week")*

**Picking the word** happens at the start of each week (uses the existing
Week Start setting).
- It only picks words with a conversation-friendliness score of 2 or more
  (see §4). *Adroit* qualifies; *ammonite* doesn't, because it's hard to
  bring a fossil into small talk.
- It prefers words you're already partway through learning (stages 2–5),
  so the weekly word reinforces what the reviews are already teaching.
- It never repeats a word that has already been a weekly word.
- **Swap:** one free re-roll per week, available until you've logged a use.

**Notifications** (on their own "Weekly word" channel):

| When | What | Buttons |
|---|---|---|
| Week start, 8:00 (configurable) | "This week: **adroit** — skilful, clever" + one example line | `Swap` |
| Mid-week, 18:00, only if not yet used | Nudge with a *different* example line | `I used it` |
| Last day, 18:00, only if not yet used | Last call | `I used it` |

**Logging a use** is one tap from the notification, the widget or the app.
- In the app you can then add an optional one-line note
  ("told Sam his parking was adroit"). The notes build up into a small
  journal you can scroll back through.
- **Grace period:** a use can still be logged for last week until 12:00 on
  the first day of the new week, so forgetting to tap on Sunday night
  doesn't break the streak.
- Logging a use marks the word **Known** and fires the confetti (delight
  moment #1).

**The tab shows:**
- A large weekly-word card with definition, pronunciation and
  **"3 ways to drop it into conversation"**. These are ready-to-say lines
  for ordinary situations like work, sport or food, so using the word
  doesn't depend on inspiration.
- Stat tiles reused from the habit overview dialog: Current 🔥, Best 🏆,
  Total used ✅.
- A week-by-week history grid reused from the All Time grid: one cell per
  week, filled if you used the word, tap a cell to see its note.
- **Pause** (holiday mode) freezes the streak, the same way pausing works
  in the Habit Tracker.

### 3.3 Notification quizzes *(the main way of learning)*

- Up to N quizzes a day (default 3), spread evenly between a first and a
  last time. The habit tracker's `ReminderScheduler` already does this
  spacing and can be reused.
- Each quiz takes the next unanswered card from today's set and shows it
  as a multiple-choice question with **up to 3 answer buttons**:
  > **adroit** — which fits?   `[ skilful ]  [ hostile ]  [ sluggish ]`
- After a tap, the notification turns into "✓ adroit — skilful, clever",
  shows the example sentence, and dismisses itself after ~6s.
  A wrong answer shows the correct meaning instead.
- No notification fires if today's set is already done.
- Notifications are visible on the lock screen, so you can learn while
  glancing at your phone.
- They use a separate low-importance "Quizzes" channel, so they can be
  silent without muting the weekly word.

### 3.4 Home-screen widgets (Glance, the same setup as Habit Tracker)

1. **Word widget (2×2 / 4×2):** cycles through words you're learning every
   few hours, showing the word, its short meaning and an example sentence.
   Tap to open the word's detail dialog. Seeing it while unlocking your
   phone gives repeated exposure without any reviews.
2. **Weekly word widget (2×1 / 4×1):** shows the word, the 🔥 streak and a
   **"Used it ✓"** button that logs the use in the background.

### 3.5 Library *(right tab)*

- Search plus filter chips: All · Learning · Known · Inbox · Shelved.
- A 2-column grid of word cards reused from `HabitCard`, coloured by stage
  from the existing palette:
  - Inbox = Fog
  - Learning = Butter → Peach as the stage rises
  - Known = Mint
  - Weekly word = Lavender
- **Long-press** opens the word detail dialog, reused from the habit
  overview dialog. It shows:
  - Word, pronunciation and 🔊.
  - Definition, example, the **"From *Book Title*"** sentence, the origin
    hook, related forms and synonyms.
  - Stat tiles: Stage · Reviews · Times used.
  - Actions: **I used this** (a use of *any* word counts and marks it
    Known), Mark known, Shelve, Edit.
- A header line such as "312 words · 41 learning · 27 known · 4 used this
  month".
- When a word becomes Known, it gets a small sparkle (delight moment #2,
  the last one; see `agent.md` §10.7.16).

### 3.6 Passive mode *(this app's version of Zen mode)*

When on, the Today session is hidden. The daily set arrives only through
notification quizzes and the widget. The Week and Library tabs stay
available. This is for weeks when even opening the app feels like effort.

### 3.7 Settings *(same section order as the family, `agent.md` §10.7.19)*

1. **Mode:** Passive mode.
2. **Appearance:** Theme, AMOLED.
3. **Notifications:**
   - Notification quizzes on/off, how many per day, first and last time.
   - Weekly word announcement time.
   - Mid-week nudges on/off.
4. **Learning:** Cards per day, New words per day, Week start.
5. **General:**
   - Swipe to navigate, Pronunciation on/off.
   - Navigation rows: Shelved words, Import words, Back up / restore
     progress.
6. **About**

---

## 4. Word content

A plain word list isn't enough. The app needs definitions, wrong-answer
options and example lines. **The plan is that you give me the list and I
generate `app/src/main/assets/words.json` with the extra details**, then it
ships in a release.
- Adding words later works the same way: append to the source list,
  regenerate, release.
- The enrichment format is documented in `tools/README.md`, so any agent
  can repeat it.

```jsonc
{
  "id": "adroit",
  "term": "adroit",
  "pos": "adjective",
  "ipa": "/əˈdrɔɪt/",
  "gloss": "skilful",                // ≤18 chars, used on notification buttons
  "definition": "Clever or skilful with your hands or mind.",
  "examples": ["She gave an adroit answer that dodged the question.", "…"],
  "hook": "French à droit, 'to the right': the right hand was the skilful one.",
  "forms": ["adroitly", "adroitness", "maladroit"],
  "synonyms": ["deft", "nimble"],
  "conversational": 3,               // 1 = literary/technical … 3 = easy to say in conversation
  "usefulness": 2,                   // controls the order words are introduced in
  "openers": ["That was an adroit bit of parking.", "…", "…"],
  "source": { "sentence": "…", "book": "…" }   // only if exported from Kindle
}
```

- **Words are introduced most useful first**, so the words that pay off
  soonest come up first and obscure ones wait. While generating the file I
  will mark archaic or very specialised words, which start Shelved.
  You can bring any of them back from the Library.
- **The "From *Book*" sentence** is the strongest memory aid on the list
  (you remember where you met the word). It needs Kindle's
  **Vocabulary Builder** database: plug the Kindle into a computer over
  USB and copy `system/vocabulary/vocab.db`. Its `LOOKUPS` table stores
  every word you looked up with its sentence and book. A plain list or
  `My Clippings.txt` also works, just without the sentence.
- The word content (the asset) is kept separate from your progress
  (DataStore), so regenerating the asset never resets learning history.

---

## 5. Architecture: what to keep from Habit Tracker

The same stack: Compose + Material 3, a single Activity with a 3-page
pager, DataStore storing JSON, AlarmManager for notifications, Glance for
widgets. No new big dependencies; the only addition is JUnit for the
scheduling tests.

| Habit Tracker | Voquab | Action |
|---|---|---|
| `ui/theme/*`, `Haptics`, `Confetti`, `WeekMath`, `WeekStart` | same | **Keep** |
| `HabitColors` | `Palette` (fixed colour per stage, not chosen by you) | Adapt |
| `MainActivity` pager (Past Week · **Today** · All Time) | This Week · **Today** · Library | Adapt |
| `HabitCard`, `HabitOverviewDialog` | `WordCard`, `WordDetailDialog` | Adapt |
| `AnalyticsScreen` grid | Weekly streak history grid (inside Week tab) | Adapt |
| `ArchivedHabitsScreen` | `ShelvedWordsScreen` | Adapt |
| `Settings` / `SettingsRepository` / `SettingsScreen` | Same pattern, new fields (§3.7) | Adapt |
| `Notifications`, `ReminderScheduler`, `ReminderReceiver` | One scheduler handling 4 alarm types: quiz, announcement, nudge, last call | Adapt |
| `widget/*` + config activity | `WordWidget`, `WeeklyWordWidget` | Adapt |
| `HabitEditorDialog` (1,065 lines) | Small `WordEditorDialog` (fix a definition or add your own word) | Replace |
| `nfc/*`, `WriteNfcTagScreen`, `ReorderHabitsScreen`, `HabitRemindersScreen`, `HabitBackfill`, `HabitFrequency`, `HabitIcons` | — | **Delete** |

### New code

```
data/model/      Word (asset content) · CardState (progress) · WeeklyWeek · Review
data/repository/ WordRepository (asset + custom words) · ProgressRepository (DataStore)
domain/          Scheduler · DailySet · Distractors · WeeklyWordPicker · Streak   ← plain Kotlin, unit-tested
notifications/   QuizNotification · QuizAnswerReceiver · WeeklyWordAlarms
tts/             Speaker (wrapper around Android's TextToSpeech)
ui/screens/      TodayScreen · WeekScreen · LibraryScreen · ShelvedWordsScreen · SettingsScreen
ui/components/   ReviewCard (Meet / MultipleChoice / Cloze / Recall) · WordCard · WordDetailDialog
widget/          WordWidget · WeeklyWordWidget (+ receivers)
```

**Storage:** the same pattern as the Habit Tracker, one JSON value per
DataStore key:
- `progress`: map from word ID to `CardState` (stage, due day, lapses,
  uses)
- `weekly`: list of weekly-word entries (week, word, used on, note,
  swapped)
- `custom_words`
- `daily`: today's set and which cards have been answered, so the app,
  notifications and widget all agree on it

All of this is small, even for 1,000 words.

**Tests:** the habit tracker shipped a streak-reset bug (v2.2.1). The
scheduling and streak logic here is subtler, so the pure `domain/`
functions get JVM unit tests. Cases to cover:
- week rollover across different week-start days
- the grace period
- missed days not causing a pile-up
- the cap on new words

---

## 6. Phases (each one ends with a `bin/changeset` release)

**Phase 0: Repo surgery** *(do this first, see the warning below)*
- Start fresh git history and create a new `voquab` GitHub repo with the
  5 secrets.
- Rename everything from `habittracker` to `voquab`:
  - package and `applicationId`
  - `namespace`, ProGuard rules
  - `rootProject.name`, `app_name`
  - `DISPLAY_NAME` and `DESCRIPTION` in `release.yml`
  - the widget refresh action
- Reset to version `0.1.0` / versionCode `1` and reset `CHANGELOG.md`.
- Remove the NFC code, permission and URL handling.
- Delete the habit-only files from §5.
- Rewrite `README.md`.
- ✅ A blank 3-tab app builds (`assembleDebug`) and installs **next to**
  Habit Tracker without replacing it.

**Phase 1: Words + Library** *(needs your list)*
- Generate `words.json`, add `WordRepository`, the Library tab, the word
  detail dialog and text-to-speech.
- ✅ All your words can be browsed and searched, with full details.

**Phase 2: Weekly word + streak** *(your core requirement, so it ships early)*
- Picker, Week tab, the three notifications with their buttons, the grace
  period, swap, pause, confetti, and streak logic with tests.
- ✅ The weekly word notification arrives, the use can be logged from the
  notification, and the streak is correct across week boundaries.

**Phase 3: Learning engine + Today**
- Scheduler, daily-set builder, wrong-answer generator, the 4 card types,
  the new-word cap, the 4am rollover, and tests.
- ✅ 5 cards a day. Skipping 5 days leaves no backlog.

**Phase 4: Ambient learning**
- Notification quizzes with answer buttons, both widgets, passive mode.
- ✅ A full day's set can be completed without opening the app.

**Phase 5: Polish + capture**
- **Add a word from anywhere:** an Android `PROCESS_TEXT` handler puts
  "Add to Voquab" in the text-selection menu of Chrome and most other
  apps. New words land in the Inbox. An optional definition lookup via
  dictionaryapi.dev would need the INTERNET permission; otherwise words
  wait until the next time I regenerate the word file.
- Back up and restore progress as JSON, a launcher icon, the
  "used this month" stats.

---

## 7. Open questions

1. **What does your Kindle export look like?** Is it the Vocabulary
   Builder `vocab.db` (best, because it includes the sentence and book),
   `My Clippings.txt`, or a plain list?
2. **Roughly how many words?** That decides the new-words default. At
   1 a day, 300 words take about 10 months. For a larger list I'd still
   keep the default low and let the most-useful-first order do the work.
3. **Use count:** is one logged use per week enough for the streak, or do
   you want "use it N times" as an option?

## 8. Deliberately left out

Typed answers · Anki's 4-button grading · due counts and backlog numbers ·
a daily streak · leaderboards and badges · sound effects · more than two
celebration animations.

---

> ⚠️ **The copied folder is still wired to Habit Tracker.**
> - `voquab/.git` is a full copy of Habit Tracker's history.
> - `origin` points to `MatejGroombridge/habit-tracker-app`.
> - `applicationId` is still `dev.matejgroombridge.habittracker`.
>
> If you run `bin/changeset` from here, it would tag and release into the
> **habit tracker repo**. Groom Hub would then push this app over your
> real Habit Tracker as an "update". Phase 0 fixes all three.
