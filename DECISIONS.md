# ShuRemind — DECISIONS.md
_ADR-lite. Append-only; never rewrite history, add a superseding entry instead._

- **D-01** v1 is fully offline; schema carries sync fields (uuid, updated_at, deleted_at, dirty) from day 1 so sync needs no migration. (2026-07-04)
- **D-02** Taxonomy: all reminders are one of 8 behaviors (EVENT, ANNIVERSARY, DEADLINE, WINDOW, NAG, RECURRING, CONSUMABLE, SOMEDAY) + quick-timer and meter extras. Features are behaviors, not screens. (2026-07-04)
- **D-03** Geofencing rejected for v1/v2-early: permission+battery cost outweighs value; manual #shop tag filter covers it. (2026-07-04)
- **D-04** Priority is computed at read time and grows as due approaches; never stored. Constants live in one tunable file. (2026-07-04)
- **D-05** Soft deletes only (deleted_at); CompletionLog is append-only. (2026-07-04)
- **D-06** All scheduling math is local wall-clock (java.time), fire instants derived; DST transitions unit-tested. Never "+N ms across days". (2026-07-04)
- **D-07** Single-next-alarm pattern: one armed AlarmManager alarm at a time; recompute+re-arm on fire/boot/open/TZ/housekeeping. No persistent foreground service — reminder apps don't need the fshu keepalive machinery. (2026-07-04)
- **D-08** Exact alarms are opt-in; inexact `setAndAllowWhileIdle` is the default and acceptable. `canScheduleExactAlarms()` branch lifted from fshu Section E. (2026-07-04)
- **D-09** v1 occurrence actions: Done/Skip/Snooze. Per-occurrence editing (RRULE-exception style) deferred to v2. (2026-07-04)
- **D-10** Missed-alarm recovery is a first-class feature: overdue summary on boot/open/housekeeping. (2026-07-04)
- **D-11** Import/export = versioned full-fidelity JSON (replace-all import in v1); daily auto-backup keeping last 7. Doubles as phone-migration path and future sync foundation. (2026-07-04)
- **D-12** Quiet hours are global (default 22:00–08:00); deferred delivery after quiet end. Per-task override deferred. (2026-07-04)
- **D-13** Wife's reminders run standalone on her phone in v1; export/import file is the sharing mechanism until sync. (2026-07-04)
- **D-14** Budget planning deferred (v2+). v1 keeps only estimated_cost field + not_before date (covers "after salary"). (2026-07-04)
- **D-15** Default currency EUR (Bulgaria post-2026 changeover), configurable. (2026-07-04)
- **D-16** Stack: Kotlin/Compose/Room/DataStore/AlarmManager/WorkManager, minSdk 26 + desugaring, single :app module, no GMS/Firebase in v1, no new libraries without explicit approval. (2026-07-04)
- **D-17** Reuse from fshu-next: Section-E alarm branching, boot-receiver re-arm pattern (simplified, no directBoot), permission onboarding structure (trimmed), GAPS section as OEM documentation. Everything else (wake locks, watchdogs, FCM, foreground service) explicitly NOT reused. (2026-07-04)
- **D-18** App is multilingual from v1: English (default), Bulgarian, Russian. All user-visible text lives in string resources (values/, values-bg/, values-ru/); plural forms via <plurals> resources (mandatory — Russian plural rules); dates/times via java.time locale formatting. UI language is user-selectable in Settings (AppCompatDelegate.setApplicationLocales + autoStoreLocales; locales_config.xml for Android 13+ system settings integration), defaulting to system language. Data stays language-neutral: DB, engine, export JSON, tag names never localized. androidx.appcompat approved for this purpose. (2026-07-05)
- **D-19** CONSUMABLE run-out math anchors to a new Task.stock_recorded_at date (set at creation and on every stock edit/restock), not to created_at or now — otherwise the reminder either slides forward forever or breaks after the first restock. Supersedes the M1 interim use of created_at. (2026-07-05)
- **D-20** Workflow: the user never manually edits project files unless unavoidable. All file changes — code AND planning docs (PROJECT.md, DATA_MODEL.md, DECISIONS.md, SESSION_LOG.md, CLAUDE.md) — are made by Claude Code from prompts. Session-end updates: Claude Code prints proposed entries for review, then writes them itself after approval. (2026-07-05)
- **D-21** UI language single source of truth is AppCompat autoStoreLocales. app_language is removed from DataStore settings; LanguageRepository is a thin wrapper over AppCompatDelegate.get/setApplicationLocales(). Export JSON includes the current language, read at export time. Works on Android 10-12 via AppCompat's own persistence; Android 13+ adds system settings integration. (2026-07-05)
- **D-22** Dependency rulings under D-16 from M2: androidx.compose.material:material-icons-core is approved (first-party Compose family, needed for Material icons in Compose UI); navigation-compose is NOT used — navigation is a manual sealed-interface Screen + BackHandler in MainActivity, sufficient for a 3-screen app. Revisit only if screen count grows materially. (2026-07-05)
- **D-23** Missed-fire detection via a single global delivered watermark (DataStore last_handled_at), advanced only after notifications post; fires in (watermark, now] surface as the overdue summary at boot/open/housekeeping. Failure mode is duplicate delivery, never loss. (2026-07-05)
- **D-24** Notification Snooze action applies one app-wide default snooze duration (initial 1h, configurable in Settings); no duration chooser on notifications; per-task snooze override deferred to v2+. (2026-07-05)
- **D-25** WINDOW→DEADLINE conversion: a "Date learned" action on WINDOW
  detail sets type=DEADLINE + due date/time, clears all rec_* fields and
  window_hint, replaces ReminderRules with the settings defaults for
  DEADLINE, in one transaction. (2026-07-05)
- **D-26** CONSUMABLE remaining stock is always derived:
  max(0, stock_qty − dose×intakes/day×days since stock_recorded_at).
  Restock sets stock_qty = remaining + bought and stock_recorded_at =
  today (per D-19). Entry points: detail Restock button + a Restock
  notification action that deep-links into detail with the dialog
  pre-opened. (2026-07-05)
- **D-27** First MeterReading for a meter_name seeds a monthly
  "log your <meter> reading" RECURRING task; idempotent via a DataStore
  string-set, never re-seeded even after user deletion. Meter-due
  (OR-logic) surfaces in the list's Overdue section at read time only —
  it produces no alarm instant and is NOT fed into the D-23
  watermark/overdue-summary notification, because it is a continuous
  condition, not a missed instant. (2026-07-05)
- **D-28** Weekly review screen = SOMEDAY (all) + stale (ACTIVE, overdue
  ≥7 days) + all ACTIVE WINDOW tasks. Entry: main-list button + a seeded
  "Weekly review" RECURRING task (weekly, SU 18:00), idempotent via
  DataStore; the seeded task's id is stored in DataStore (not a schema
  column) and its notification tap routes to the review screen instead
  of task detail. (2026-07-05)
- **D-29** material-icons-core ships only ~50 icons. Icon choices must
  stay within that set (verified: Archive/Speed/EventNote absent; used
  AutoMirrored ExitToApp, AutoMirrored List, DateRange instead). Do not
  add material-icons-extended without explicit approval. (2026-07-05)
- **D-30** Export settings{} includes: quiet hours, default all-day time,
  currency, per-type default reminder offsets, snooze presets + default
  snooze duration, seed markers (weekly-review task id, seeded-meters
  string set), and ui_language read from AppCompatDelegate at export time
  (D-21). Excluded as device-specific: backup folder URI, auto-backup
  toggle, exact-alarms opt-in, delivery watermark. (2026-07-06)
- **D-31** Import is replace-all: wipe all Room tables and restore the D-30
  settings subset transactionally; delivery watermark is set to import
  time (prevents overdue-summary flood from imported history);
  ui_language is NOT applied on import (D-13 — wife's phone keeps her
  language); confirmation dialog shows entity counts and warns the import
  replaces everything; before wiping, attempt a safety export to the
  backup folder if configured, else to app cacheDir (failure to write the
  safety copy aborts the import with an error). Finish with
  RecomputeAndRearm. (2026-07-06)
- **D-32** Auto-backup is a sibling daily unique periodic BackupWorker (not
  part of housekeeping), enqueued only while the toggle is on AND a folder
  URI with a persisted SAF permission exists; cancelled when either goes
  away. Files: shuremind-backup-yyyyMMdd-HHmmss.json; retention keeps the
  newest 7 files matching that pattern in the picked folder. (2026-07-06)
- **D-33** Import restores the D-31 settings subset as sequential
  DataStore writes after the Room transaction commits — Room and
  DataStore cannot share a transaction. Accepted failure mode: task data
  imported but settings partially restored; recoverable by re-running the
  import, and the pre-import safety export exists regardless. No
  compensation logic in v1. Clarifies D-31's "in one transaction". (2026-07-06)
- **D-34** Export serializes engine enums (TaskType, TaskStatus,
  RecurrenceFrequency, RecurrenceAnchor, CompletionAction) by constant
  name; those names are part of the export wire format. Renaming any
  constant is an export-schema change and requires a schema_version bump. (2026-07-06)
