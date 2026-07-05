# ShuRemind — SESSION_LOG.md
_Project memory across chat sessions._

## How to use (every session)
1. **Start of a planning session (Opus):** upload PROJECT.md + DECISIONS.md + this file. First line of your message: "Continue ShuRemind planning. Read the files, confirm current state in 3 lines, then we work."
2. **Start of a coding session (Claude Code):** CLAUDE.md is read automatically from the repo root; keep PROJECT.md / DATA_MODEL.md / DECISIONS.md in the repo too and reference them in prompts ("implement M1 per DATA_MODEL.md").
3. **End of every session:** the model proposes (a) a new Session entry for this file, (b) any new D-xx lines, (c) PROJECT.md changes if scope moved — then, per D-20, the updates are applied via a Claude Code prompt, never pasted by hand.

## Current state
- Phase: **M4 complete → ready for M5 (import/export + auto-backup +
  polish)**
- Next concrete step: M5 with Claude Code — versioned full-fidelity
  JSON export/import (replace-all, D-11), daily auto-backup to
  user-picked folder keeping last 7, Settings rows for backup go live.
- Watch item from M2: ViewModels are activity-scoped singletons incl. TaskDetailViewModel reused across tasks via load(taskId) — still unresolved, revisit if a stale-data flash appears when switching tasks.
- Open questions: final app/package name (placeholder `com.shuremind`); confirm minSdk 26 on both target phones; app has no launcher icon resource yet (pre-existing gap, not M3 scope).

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
