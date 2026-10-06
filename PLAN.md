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
4. **Meaning over memorisation.** Each word comes with natural example
   sentences, a one-line origin story and ready-to-say example lines.
   This makes reviews feel like curiosity rather than drilling.
5. **Using a word beats recalling it.** The weekly word, used in a real
   conversation, is the main goal. Flashcards are only scaffolding for it.
6. **No daily guilt.** The only streak is the weekly one. Daily reviews show
   progress but have no streak to break.

---

## 2. How a word moves through the app

```
 New ────► Meet ──► Learning (stages 1–5) ──► Known ──(every ~4 months)──► check
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
| Meet (0) | +1 day | Word, pronunciation, an example sentence, then definition + origin hook. Just read it. | — ("Got it" / "Already knew it" / "Not worth learning") |
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
  A swapped-away word never comes back as a weekly word.
- **The first week** starts the day the app is first opened. If fewer than
  4 days are left in it, it runs on to the end of the following week.
- **Weeks the app sleeps through** are filled in as missed (or as paused,
  during a break), so the history never has gaps.

**Notifications** (on their own "Weekly word" channel):

| When | What | Buttons |
|---|---|---|
| Week start, 8:00 (configurable) | "This week: **adroit** — skilful, clever" + one example line | `Swap` |
| Mid-week (4th day), 18:00, only if not yet used | Nudge with a *different* example line | `I used it` |
| Last day, 18:00, only if not yet used | Last call | `I used it` |
| Morning after the week ends, if unused | "Did you use it last week?" (grace period) | `I used it` |

These come from two daily alarms (the announcement time and 18:00). Each
time one fires, the app checks the current week and usually sends nothing.
This means changing the week start, pausing or swapping never leaves a
stale alarm behind.

**Logging a use** is one tap from the notification, the widget or the app.
- An optional one-line note ("told Sam his parking was adroit") can be
  added from the card or by tapping a past week. You're never prompted for
  it, because a prompt would hide the confetti and add a step to a one-tap
  action. The notes build up into a small journal.
- **Grace period:** a use can still be logged for last week until 12:00 on
  the first day of the new week, so forgetting to tap on Sunday night
  doesn't break the streak.
- Logging a use marks the word **Known** and fires the confetti (delight
  moment #1).

**The tab shows:**
- A large weekly-word card with definition, 🔊 and **"Ways to use it"**:
  one or two ready-to-say lines for ordinary situations like work, sport
  or food, so using the word doesn't depend on inspiration.
- Stat tiles reused from the habit overview dialog: Current 🔥, Best 🏆,
  Total used ✅.
- A "Past weeks" list (newest first): each week's word, its dates, and
  whether it was Used ✓, Missed, Paused or still "Log by noon", plus its
  note. I chose a list over a grid because there's only one cell per week,
  so a list has room for the word and the note.
- **Pause** (holiday mode, the ⏸ in the header) freezes the streak, the
  same way pausing works in the Habit Tracker. A week already used stays
  on screen. The break starts the following week.

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

- Search plus filter chips: All · New · Learning · Known · Shelved. "All"
  leaves out shelved words, which only appear under their own filter (so
  there is no separate Shelved screen).
- A 2-column grid of word cards, coloured by status from the existing
  palette:
  - New = Sky
  - Learning = Butter (→ Peach as the stage rises, Phase 3)
  - Known = Mint
  - Shelved = Fog, faded toward the background
  - Weekly word = Lavender (Phase 2)
- **Tap or long-press** opens the word detail dialog, adapted from the
  habit overview dialog. It shows:
  - Word, pronunciation and 🔊.
  - Definition, examples with the word in bold, the origin hook, related
    forms and synonyms, and the form you originally looked up.
  - Actions: **I used this today** (a use of *any* word counts and marks
    it Known; tap again to undo), Mark known, Shelve.
  - *Phase 3 adds* stat tiles (Stage · Reviews · Times used) and Edit.
- A header line such as "190 words · 41 learning · 27 known".
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
   - Navigation rows: Import words, Back up / restore progress.
6. **About**

---

## 4. Word content

You supply a plain list; I write the definitions, examples and everything
else into `app/src/main/assets/words.json`, and it ships in a release.
- **The first batch is done:** 209 words from your Kindle list
  (`tools/words.txt`). 190 are active and 19 start shelved. 151 of the
  active words are conversational enough to be a weekly word, which is
  about three years of weekly words.
- **The format and writing guidelines** are in [`tools/README.md`](tools/README.md).
  Each word has a short meaning for notification buttons, a definition,
  one or two examples with the word marked, a real origin story, related
  forms, synonyms, and one or two conversation openers for weekly-word
  candidates.
- **Two sentences only when they really differ** in sense, form or
  situation; 66 words have a single example. There's no written
  pronunciation: the 🔊 button speaks the word in an Australian voice (or
  failing that, a British one).
- **Adding words:** append them to `tools/words.txt`, ask me to write the
  entries, then run `node tools/check-words.mjs --fix`. It validates
  every entry and confirms each listed word is covered.
- **Looked-up forms are reduced to the dictionary form** (affably →
  affable, castigation → castigate). The original is kept and shown as
  "You looked up …". `ennobl` was read as *ennoble*.
- **Shelved from the start** (each can be unshelved in the Library):
  - Everyday words: success, practical, severe, navel, disown, dang,
    slang, paella, sorority, quasi.
  - Names: Charlemagne, Mennonite, Moulin Rouge.
  - Offensive or archaic terms: coolie, negroid, transvestite, whoreson.
  - Not standalone English: fraile (Spanish), demi (a prefix).
- **Words are introduced most useful first:** 55 high-value words, then
  87 mid, then 48 niche ones (*ammonite*, *pericope*).
- **The word content is kept separate from your progress** (DataStore,
  keyed by `id`), so regenerating the file never resets learning history.
- *Later, optional:* a Kindle Vocabulary Builder export
  (`system/vocabulary/vocab.db`) would add the original book sentence
  for each word, which is a strong memory aid. It's not needed for
  anything else.

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
| `ArchivedHabitsScreen` | Shelved filter in the Library | Replace |
| `Settings` / `SettingsRepository` / `SettingsScreen` | Same pattern, new fields (§3.7) | Adapt |
| `Notifications`, `ReminderScheduler`, `ReminderReceiver` | One scheduler handling 4 alarm types: quiz, announcement, nudge, last call | Adapt |
| `widget/*` + config activity | `WordWidget`, `WeeklyWordWidget` | Adapt |
| `HabitEditorDialog` (1,065 lines) | Small `WordEditorDialog` (fix a definition or add your own word) | Replace |
| `nfc/*`, `WriteNfcTagScreen`, `ReorderHabitsScreen`, `HabitRemindersScreen`, `HabitBackfill`, `HabitFrequency`, `HabitIcons` | — | **Delete** |

### New code

```
data/model/      Word (asset content) · WordProgress (progress) · WeeklyState / WeekEntry · Review
data/repository/ WordRepository (asset + custom words) · ProgressRepository (DataStore)
domain/          Day · WeekMath · WeeklyEngine · WeeklyPicker · Scheduler · DailySet · Distractors   ← plain Kotlin, unit-tested
weekly/          WeeklyService · WeeklyAlarms · WeeklyAlarmReceiver · WeeklyActionReceiver · WeeklyNotifications
notifications/   QuizNotification · QuizAnswerReceiver   (Phase 4)
tts/             Speaker (wrapper around Android's TextToSpeech)
ui/screens/      TodayScreen · WeekScreen · LibraryScreen · SettingsScreen
ui/components/   ReviewCard (Meet / MultipleChoice / Cloze / Recall) · WordCard · WordDetailDialog
widget/          WordWidget · WeeklyWordWidget (+ receivers)
```

**Storage:** the same pattern as the Habit Tracker, one JSON value per
DataStore key:
- `progress`: map from word ID to `WordProgress` (status and use days
  now; Phase 3 adds stage, due day and lapses)
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

**Phase 0: Repo surgery** — ✔ done
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

**Phase 1: Words + Library** — ✔ built
- Generate `words.json`, add `WordRepository`, the Library tab, the word
  detail dialog and text-to-speech. Also add the 4am day rollover
  (`domain/Day`), which every later phase uses.
- ✅ All your words can be browsed and searched, with full details.

**Phase 2: Weekly word + streak** — ✔ built
- Picker, Week tab, the three notifications with their buttons, the grace
  period, swap, pause, confetti, and streak logic with tests.
- ✅ The weekly word notification arrives, the use can be logged from the
  notification, and the streak is correct across week boundaries.
  (Checked on an emulator by moving its clock forward: announcement, swap
  from the shade, mid-week nudge → "I used it", grace period on Monday
  morning, pause and resume. The rules have 23 JVM unit tests in
  `app/src/test`.)

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
  apps. New words land in the New list. An optional definition lookup via
  dictionaryapi.dev would need the INTERNET permission; otherwise words
  wait until the next time I regenerate the word file.
- Back up and restore progress as JSON, a launcher icon, the
  "used this month" stats.

---

## 7. Decisions

1. **Word source:** a plain list. There are no book sentences for now, and
   the format allows adding them later from `vocab.db`.
2. **Defaults:** 1 new word a day, 5 cards a day. At that pace the 190
   active words take about 6 months to introduce, most useful first.
3. **Streak rule:** one logged use per week keeps the streak. There's no
   "use it N times" option. It would add friction, and the goal is a
   habit, not a quota.

## 8. Deliberately left out

Typed answers · Anki's 4-button grading · due counts and backlog numbers ·
a daily streak · leaderboards and badges · sound effects · more than two
celebration animations.

---

> ✔ **The link to Habit Tracker has been cut** (Phase 0). The repo has
> fresh history with no remote, and the app ID is now
> `dev.matejgroombridge.voquab`. Before the first release, create the
> `voquab` GitHub repo, add the 5 secrets (`agent.md` §13 D), and push.
