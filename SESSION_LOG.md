# ShuRemind — SESSION_LOG.md
_Project memory across chat sessions._

## How to use (every session)
1. **Start of a planning session (Opus):** upload PROJECT.md + DECISIONS.md + this file. First line of your message: "Continue ShuRemind planning. Read the files, confirm current state in 3 lines, then we work."
2. **Start of a coding session (Claude Code):** CLAUDE.md is read automatically from the repo root; keep PROJECT.md / DATA_MODEL.md / DECISIONS.md in the repo too and reference them in prompts ("implement M1 per DATA_MODEL.md").
3. **End of every session:** the model proposes (a) a new Session entry for this file, (b) any new D-xx lines, (c) PROJECT.md changes if scope moved — then, per D-20, the updates are applied via a Claude Code prompt, never pasted by hand.

## Current state
- Phase: v1 installed on both phones → M6 UX pass (part 1 + part 1.5 done, part 2 = translation review pending) → M7 (per-task alarm mode) implemented, on-device verification pending.
- Next concrete step: on-device alarm-mode verification (checklist in Session 10) + translation review table + a week of real-life use.
- Watch item from M2: ViewModels are activity-scoped singletons incl. TaskDetailViewModel reused across tasks via load(taskId) — still unresolved, revisit if a stale-data flash appears when switching tasks.
- Open questions: real launcher icon design (placeholder monogram ships in v1, → D-35). Wife's phone confirmed Android 10 (M6 smoke test).

## Session 1 — 2026-07-04 (Fable, planning)
- Reviewed Gemini's architecture plan; kept offline-first/Room/WorkManager core, added recurrence as day-1 concern, time-growing priority, deferred geofencing, flagged Android 13/14 permission reality.
- Audited fshu-next KEEPALIVE_AUDIT.md: verdict — persistent-connection machinery not applicable; reuse alarm branching (Section E), boot re-arm (Layer 8), onboarding flow (Section G), GAPS as docs (→ D-17).
- Collected real-life requirements (dentist, shower tray, car maintenance, taxes, moving-window declaration, meds intake+stock, flowers, birthdays, routines, timers, #shop items) → collapsed into the 8-behavior taxonomy (→ D-02, PROJECT.md).
- Added commonly-forgotten items: missed-alarm recovery, notification actions, quiet hours, completion log, recurring-edit semantics, DST, wife's-phone reality.
- Scope frozen: v1 offline with import/export + auto-backup; sync/budget/dependencies/geofencing deferred.
- Produced: PROJECT.md, DATA_MODEL.md, DECISIONS.md (D-01…D-17), CLAUDE.md, EXTRACT_FSHU_PATTERNS.md, this file.
- Addendum 2026-07-04: fshu extraction done and reviewed (shuremind_patterns.md ✓).
  M3 notes: add TIME_CHANGED + MY_PACKAGE_REPLACED to boot receiver; add
  REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission; USE_EXACT_ALARM ok for sideload,
  revisit for Play. Next: M1 with Claude Code.

## Session 2 — 2026-07-05 (Fable planning + Claude Code, M1 + M1.5)
- Claude Code executed M1: Gradle/Kotlin/Compose scaffold, full Room schema (v1) matching DATA_MODEL.md, pure-Kotlin engine for all 8 behaviors + priority/consumable/meter math, 70 unit tests incl. Bulgaria DST, clean build.
- Planning review of M1 found: consumable run-out anchoring gap (→ D-19), missing repository layer, unverified meter-OR-time wiring, no git repo.
- New requirement (user): multilingual UI from v1 — English, Bulgarian, Russian, language switchable in Settings (→ D-18, androidx.appcompat approved under D-16).
- M1.5 executed: docs updated for D-18/D-19; git init (one bundled first commit — user's call when commit-ordering instructions conflicted); stock_recorded_at column (schema v2, no migration, dev-only DB); verification confirmed TaskEntity columns + NagEngine not_before-silence correct, and found+fixed a real gap — MeterEngine's OR-due logic was unreachable from OccurrenceEngine (added isDue() + 4 tests); repository layer added (writes stamp updated_at/dirty; meter completion = one transaction); D-18 scaffolding (appcompat 1.7.1, AppCompatActivity, locales_config, autoStoreLocales, bg/ru string stubs, LanguageRepository — Settings UI is M2).
- Clean build green after every step: 75 tests, 0 failures.
- Design note for M3/M4: meter-due produces no alarm instant by design — it's detected at recompute points (app open, housekeeping, new reading), not alarm-fired. Do not "fix" this.
- New workflow rule: user never hand-edits files (→ D-20).
- Next: M2.

## Session 3 — 2026-07-05 (Fable planning + Claude Code, M2)
- D-21 decided in planning (autoStoreLocales is the single source of truth for UI language; app_language DataStore key dropped; export reads current language at export time; works on Android 10–12 via AppCompat persistence). Turned out mostly moot in code — M1.5's LanguageRepository was already AppCompat-only; docs updated.
- Claude Code executed M2: manual DI (AppContainer + ViewModelFactory, DAOs made internal so ViewModels can only see repositories), quick capture bar (≤2-tap plain add), sectioned main list (Overdue/Today/Upcoming/Someday) sorted by read-time priority engine, Done/Skip/Snooze wired through CompletionRepository/TaskRepository (snooze writes snoozed_until only — alarms are M3), tag filter chips, full per-type task detail/edit screen, Settings screen (language picker live; auto-backup + exact-alarms shown disabled with "available after M5/M3" notes), complete en/bg/ru strings incl. Russian 4-form plurals.
- Mid-run refactor: repositories converted to interfaces with Room*/DataStore* implementations to enable fake-repo ViewModel unit tests. Navigation is a manual sealed interface (no navigation-compose). material-icons-core added (→ D-22 ratifies both rulings).
- Result: 46 files changed, 82 tests green (75 engine + 7 MainViewModel), assembleDebug clean. Remaining lint error is local.properties only (gitignored, machine path quirk) — ignore.
- Next: M3.

## Session 4 — 2026-07-05 (Claude Code, M3)
- Claude Code executed M3: FireInstantEngine (pure Kotlin) combining occurrence/reminder-lead/escalation/snooze/quiet-hours into one cursor-based "next fire" oracle, reused unchanged for both alarm-arming (`globalNext`) and missed-fire recovery (`missedSince`) — 38 new tests incl. Bulgaria DST (both directions, via the new date-based reminder-offset arithmetic), quiet-hour boundaries, escalation transitions, snooze-into-quiet-hours, a CONSUMABLE daily-follow-up recovery chain.
- D-23 delivered watermark (DataStore `last_handled_at`) implemented via `DeliveryWatermarkRepository`; AlarmScheduler (Section-E exact/inexact branch) + `RecomputeAndRearm` single entry point wired from AlarmReceiver, BootReceiver (BOOT_COMPLETED/TIME_SET/TIMEZONE_CHANGED/MY_PACKAGE_REPLACED), MainActivity.onStart, the daily HousekeepingWorker, and repository-level hooks (`ScheduleChangeNotifier`) on task/completion/settings writes — guarded by a non-reentrant Mutex in `RecomputeAndRearm` since its own writes flow through those same hooked repositories.
- Notifications: reminders/nag/overdue_summary channels, per-occurrence Done/Snooze(+Skip for recurring) actions via `NotificationActionReceiver`, D-24 default-snooze-duration setting, grouped overdue summary with RU 4-form plurals, tap-to-task-detail (MainActivity singleTop + onNewIntent, no trampolines).
- Permission onboarding: POST_NOTIFICATIONS runtime request on first open, exact-alarm opt-in row now live (launches ACTION_REQUEST_SCHEDULE_EXACT_ALARM when needed), battery-optimization exemption row (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) — all graceful-degrade on denial.
- Refactored `ReminderRuleRepository` from a concrete class to an interface (matching M2's TaskRepository/TagRepository pattern) so `RecomputeAndRearm` could be unit-tested with fakes.
- Added `androidx.work:work-runtime-ktx` (already in the D-16 approved stack, first actual use).
- Result: 120 tests green (82 + 38), assembleDebug clean, lintDebug clean bar the pre-existing local.properties path issue.
- Next: M4.
- Addendum (post-session, commit 9236e28): RecomputeAndRearm concurrency
  fix — tryLock no longer drops concurrent triggers; a rerunRequested
  flag loops the recompute instead. +1 test (121 green at that point).

## Session 5 — 2026-07-06 (Fable planning + Claude Code, M4)
- Planning ratified D-25..D-28 (WINDOW conversion, restock flow, meter
  seeding/surfacing, weekly review; review reminder SU 18:00 confirmed).
- Claude Code executed M4: WindowConversionRepository (transactional
  type flip + rule replacement), ConsumableEngine.remainingStock +
  restock dialog/notification action (deep-link via intent extras,
  singleTop path), meter readings screen + first-use monthly-prompt
  seeding (DataStore idempotency), meter-due OR-logic surfacing in the
  Overdue list section (read-time only, no alarm instant — Session 2
  note honored), weekly review screen (SOMEDAY/stale≥7d/WINDOW) +
  seeded weekly task with tap-through routing, en/bg/ru strings.
- Three uncovered decisions surfaced by Claude Code and ratified in
  planning: meter-due stays out of the watermark summary (folded into
  D-27), weekly-review task id in DataStore (folded into D-28), icons
  ruling (→ D-29).
- Result: 35 files (20 modified, 15 new), 139 tests, assembleDebug
  clean, lint clean bar the known local.properties issue.
- Found: RecomputeAndRearmTest used real wall-clock time, failing
  whenever run during quiet hours (~21:00–08:00) — fixed this session
  by pinning the test clock (see below).
- Next: M5 (import/export, auto-backup, polish).
- Addendum (post-session): first real-device launch crashed at startup —
  Theme.ShuRemind still had the M1 android:Theme.Material parent while
  MainActivity has been an AppCompatActivity since M1.5 (D-18); fixed to
  Theme.AppCompat.DayNight.NoActionBar. Weekly-review seeding hardened:
  try/catch+log at the launch site, markSeeded only after a successful
  write — so its failure mode is a duplicate seeded task on a crashed
  first run (observed during repro), never loss or a startup crash.
  Verified on device (moto g 60): clean launch, main list renders.
  Commit 41d4383.
- Pre-M5 device smoke test now unblocked and pending: Restock
  notification action, weekly-review notification tap, first meter
  reading + monthly prompt seeding, language switch.

## Session 6 — 2026-07-06 (Fable planning + Claude Code, M5)
- Planning ratified D-30..D-32 (export settings subset incl. seed
  markers, replace-all import w/ watermark=import-time + safety export +
  ui_language never applied, sibling daily BackupWorker + keep-7).
- Claude Code executed M5: @Serializable DTO layer (snake_case, decoupled
  from Room entities), ExportEngine/ImportEngine (pure Kotlin, typed
  parse errors, ignoreUnknownKeys), RoomImportRepository (replace-all in
  one Room transaction via new TransactionRunner seam, watermark set to
  import time, seed markers restored, RecomputeAndRearm via existing
  hook), BackupManager (SAF via DocumentsContract, no new libraries),
  BackupWorker (unique periodic, enqueue/cancel from settings writes +
  launch-time reconciliation), Settings rows live (folder picker w/
  persisted permission, Back up now, export via CREATE_DOCUMENT, import
  w/ counts confirmation dialog), en/bg/ru strings w/ RU 4-form plurals.
- Post-review: fixed first-enable toggle bug (folder pick from the
  toggle now also enables auto-backup); ratified D-33 (settings restore
  outside the Room transaction — accepted) and D-34 (enum names are wire
  format).
- Result: 155 tests green, assembleDebug clean, lint clean bar the
  known local.properties issue. DATA_MODEL.md Export JSON section
  rewritten per D-30/D-31/D-32.
- Next: device smoke test (M4 items + M5 backup/export/import flows),
  then M5.5 polish (launcher icon, final name/package, minSdk
  confirmation) pending user input.

## Session 7 — 2026-07-06 (Fable planning + Claude Code, M5.5)
- User ratified: package com.shuremind final, display name ShuRemind,
  real icon deferred (→ D-35); release build unminified + local-keystore
  signing w/ debug fallback, versioning 1.0.0/1 (→ D-36).
- Claude Code executed M5.5: placeholder adaptive monogram icon
  (+ monochrome themed-icon layer), release buildType + key.properties
  signing scaffold (gitignored), version set, SMOKE_TEST.md checklist
  (pending M4/M5 device items + the 11 PROJECT.md acceptance cases),
  PROJECT.md package wording finalized.
- Result: 155 tests green, assembleDebug + assembleRelease clean, lint —
  the missing-launcher-icon warning is gone; the local.properties error
  remains the only known exception.
- Next: user runs SMOKE_TEST.md on the moto g 60 (+ wife's phone when
  its Android version is confirmed ≥8.0), then v1 install. Real icon
  whenever ready.

## Session 8 — 2026-07-06 (Claude Code, M6 part 1)
- User completed the pending device smoke test on both phones (moto g 60 +
  wife's phone, confirmed Android 10), release APK installed on both, no
  functional failures — but the first real-use pass surfaced a real bug:
  reminder offsets were raw ISO-8601 text fields with no validation, so a
  bad string silently broke scheduling. That plus general UX intelligibility
  gaps became M6 part 1 (this change set); translation review is part 2.
- Claude Code executed M6 part 1: structured reminder-offset picker
  (ReminderOffsetFormat pure conversion + ReminderOffsetEditor composable —
  number + unit dropdown + before-due/at-due toggle; unparseable stored
  values show in an error state instead of crashing) replacing every raw
  ISO text field (task edit + Settings defaults) — fixes the validation bug
  (D-37); plain-language task type labels via string resources only, enum
  names untouched (D-38); priority chip with color bands + word labels on
  the impact/urgency selectors + a tag toggle-chip picker in task edit
  (D-39); a contextual HelpDot "?" system wired into every field/section
  called out in the spec, including a dedicated all-types dialog for the
  type picker (D-40).
- Result: 183 tests green (155 + 28 new: offset conversion round-trip/
  validation/invalid-input, priority band boundaries), assembleDebug +
  assembleRelease clean, lint clean bar the known local.properties issue.
  UI changes were not exercised on a real device or emulator this session
  (none available in this environment) — recommend a quick manual pass
  before the next real-life week, especially the offset picker and the
  dark-theme priority chip colors.
- Next: translation review table (part 2) + a week of real-life use.

## Session 9 — 2026-07-09 (Claude Code, M6 part 1.5)
- On-device use after M6 part 1 surfaced four more gaps: quick capture had
  no Notes field; quick capture's tag picker was free-text-only (no toggle
  chips like task edit, D-39); no way to delete a stale/mistyped tag
  anywhere in the app; and both task edit and quick-capture forms couldn't
  scroll far enough to reach fields under the keyboard. Also, the language
  picker showed each language's name translated into the *current* UI
  language (e.g. Russian UI showed "Английский" for English) instead of
  each option naming itself.
- Notes field: added directly under the title in the quick-capture
  expanded panel, mapped to Task.notes (already schema-present); localized
  hint in en/bg/ru.
- Tag toggle chips: quick capture now shows existing tags as FilterChip
  toggles (same D-39 pattern as task edit) alongside the free-text
  new-tag field; only genuinely new tags show as removable InputChips.
- Tag management (D-41): a "Tags" row in Settings opens a dialog listing
  every tag with a per-tag delete button; delete shows a confirmation
  naming the tag, then removes the tag and its TaskTag rows via an
  explicit transaction (TagRepository.deleteTag), covered by a
  repository-level unit test. No rename in v1.
- Keyboard scroll bug: MainActivity already ran
  decorFitsSystemWindows(false) via enableEdgeToEdge(), so the fix was
  entirely in Compose — task detail's scrollable Column gained
  .imePadding()/.imeNestedScroll(); the quick-capture panel had no
  scrollable container at all (it sat above a separate LazyColumn), so the
  main screen's capture bar, tag filter, priority legend and task list
  were merged into one LazyColumn with .imePadding()/.imeNestedScroll(),
  so the whole screen scrolls as one unit past the keyboard.
- Language endonyms: AppLanguage now carries a hardcoded endonym per
  non-system language (English/Български/Русский); the picker shows that
  instead of the old lang_en/lang_bg/lang_ru string resources (removed),
  which always matched the current UI language rather than naming each
  option in itself. The system-default option is unaffected (still the
  localized "System default" string).
- Result: 187 tests green (183 + 4 new: notes trim/blank-to-null,
  tag-toggle add/remove, tag-delete cascade, Settings tag-delete),
  assembleDebug clean, lint clean bar the known local.properties issue and
  one pre-existing unrelated failure (RecomputeAndRearmTest's concurrency
  test — confirmed failing identically on a clean checkout of master via a
  throwaway worktree, unrelated to this change set). UI changes not
  exercised on a real device this session (none available); recommend a
  manual pass on the tag-management dialog and keyboard scroll behavior
  before the next real-life week.
- Next: translation review table (M6 part 2) + a week of real-life use.

## Session 10 — 2026-07-09 (Claude Code, M7)
- Resolved Session 9's watch item: RecomputeAndRearmTest's concurrency
  test read the real wall clock directly (ZonedDateTime.now(zone)) in its
  rerun loop instead of through a seam, so a rerun triggered mid-test used
  the actual current date instead of the test's fixed instant — an
  unpinned-clock bug in the same family as Session 5's DST fixtures,
  exactly as suspected. Fixed by adding an injectable nowProvider
  (matching every other clock seam in the codebase); production behavior
  unchanged (still reads the real clock by default). Landed as its own
  commit before M7 started, confirmed stable across repeated runs.
- Implemented M7 (per-task alarm mode, D-42): Task.alarm_mode (Room v2→3,
  additive migration — first real, non-destructive migration, both phones
  carry live data now); FireInstantEngine exposes which fire is an alarm
  (isAlarm, occurrence-reason only) and exempts it from quiet-hours
  deferral; AlarmScheduler branches to setAlarmClock() for that case; a
  new alarm notification channel (USAGE_ALARM, insistent) posts through a
  full-screen intent to a new minimal AlarmRingActivity (Done/Snooze/
  Dismiss, all three routing through the existing NotificationActionReceiver
  so watermark/completion logic has one code path); a sibling one-shot
  AlarmManager timer auto-silences the ring after 5 minutes without
  removing the notification. "Ring as alarm" toggle added to task edit and
  quick capture (hidden for SOMEDAY).
- Export/import: TaskDto.alarm_mode defaults to false so pre-M7 backup
  files (missing the key) import cleanly; schema_version stays 1
  (additive field). No instrumentation available in this environment for
  Room's MigrationTestHelper, so the migration is instead verified by a
  JVM test asserting the exported schema JSON is strictly additive (v2's
  tasks columns all survive into v3, exactly one new column, every other
  table unchanged) — real migration of each phone's live v2 database is an
  on-device checklist item, not something this session could exercise.
- Result: 199 tests green (187 + 12 new: alarm-arming branch selection,
  alarm-mode quiet-hours exemption + DST composition + mixed-queue arming,
  additive-schema assertions, export/import backward tolerance, toggle
  load/save), assembleDebug + assembleRelease + lint clean bar the known
  local.properties issue and two new, expected UnusedAttribute warnings
  (AlarmRingActivity's showWhenLocked/turnScreenOn manifest attributes
  only apply API 27+; minSdk is 26, and the code already has an explicit
  window-flag fallback for API 26 itself — same shape as the existing
  localeConfig warning already accepted for M6). Nothing exercised on a
  real device this session (none available) — see the on-device
  checklist below.
- On-device checklist (alarm feature): locked-screen ring; ring during
  quiet hours; Done/Snooze/Dismiss from the ring screen (note: Snooze
  re-fires as a normal notification, not another ring — confirm this
  matches expectations); 5-minute auto-silence; migration of the real v2
  DB on both phones; import of a pre-M7 backup file.
- Next: on-device alarm-mode verification (checklist above) + translation
  review table (M6 part 2) + a week of real-life use.
