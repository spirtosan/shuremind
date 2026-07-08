# ShuRemind — DATA_MODEL.md (v1)
_Room/SQLite. Sync-ready but offline. All IDs are UUIDv4 strings. Timestamps are epoch millis unless noted. Dates/times that drive scheduling are stored as LOCAL wall-clock components (ISO strings) — never as pre-computed UTC instants (see DST rule)._

## Entity: Task (single table, nullable type-specific fields)
| Field | Type | Notes |
|---|---|---|
| id | TEXT PK | UUIDv4 |
| title | TEXT NOT NULL | |
| notes | TEXT NULL | |
| type | TEXT NOT NULL | EVENT, ANNIVERSARY, DEADLINE, WINDOW, NAG, RECURRING, CONSUMABLE, SOMEDAY. Mutable (WINDOW→DEADLINE, SOMEDAY→anything). |
| status | TEXT NOT NULL | ACTIVE, DONE (one-shots), ARCHIVED. Recurring stay ACTIVE. |
| impact | INTEGER | 0–3, default 1 ("what happens if I skip it") |
| urgency | INTEGER | 0–3, default 1 (base value; engine boosts it near due) |
| estimated_cost | REAL NULL | informational; currency in settings (default EUR) |
| due_local_date | TEXT NULL | 'YYYY-MM-DD' — event date / deadline / anniversary (MM-DD reused yearly) |
| due_local_time | TEXT NULL | 'HH:MM'; NULL = all-day (default reminder time from settings, e.g. 09:00) |
| not_before | TEXT NULL | 'YYYY-MM-DD'; task invisible/silent before this (dentist-after-salary) |
| rec_freq | TEXT NULL | DAILY, WEEKLY, MONTHLY, YEARLY |
| rec_interval | INTEGER | default 1 (every N units) |
| rec_anchor | TEXT NULL | CALENDAR or COMPLETION |
| rec_days_of_week | TEXT NULL | CSV 'MO,WE,FR' (WEEKLY) |
| rec_day_of_month | INTEGER NULL | 1–31, clamp to month end (MONTHLY) |
| rec_times_of_day | TEXT NULL | CSV '08:00,14:00,20:00' — multi-dose days |
| rec_end_date | TEXT NULL | optional stop |
| nag_interval_hours | REAL NULL | NAG: repeat cadence until done (e.g. 24) |
| stock_qty | REAL NULL | CONSUMABLE: current units |
| dose_per_intake | REAL NULL | CONSUMABLE: units per intake (intakes/day = count of rec_times_of_day) |
| restock_lead_days | INTEGER NULL | CONSUMABLE: remind this many days before run-out |
| stock_recorded_at | TEXT NULL | 'YYYY-MM-DD' — date stock_qty was last known true; set on create + every stock change; anchors run-out math (D-19) |
| meter_name | TEXT NULL | e.g. 'car' (joins MeterReading.meter_name) |
| meter_interval | REAL NULL | e.g. 10000 (km since last_done_meter) |
| last_done_meter | REAL NULL | meter value at last completion (user estimate allowed) |
| window_hint | TEXT NULL | WINDOW: free text, e.g. 'usually Sep–Nov' |
| alarm_mode | INTEGER NOT NULL DEFAULT 0 | opt-in per-task alarm (D-42); available on all types except SOMEDAY; added in schema v3 (Room migration, additive) |
| snoozed_until | INTEGER NULL | suppress notifications until then |
| next_fire_at | INTEGER NULL | **derived cache**, indexed — next occurrence instant; recomputed on edit/complete/boot/TZ change |
| created_at / updated_at | INTEGER | |
| deleted_at | INTEGER NULL | soft delete |
| dirty | INTEGER | default 1; unused in v1, reserved for sync |

Indices: (next_fire_at), (status, deleted_at), (type).

## Entity: ReminderRule (lead-time reminders; N per task)
| Field | Type | Notes |
|---|---|---|
| id | TEXT PK | |
| task_id | TEXT FK→Task | cascade delete |
| offset_iso | TEXT | ISO-8601 duration **before** due: 'P14D', 'P1D', 'PT2H', 'PT0S' (=at due). Applies to EVENT/ANNIVERSARY/DEADLINE. RECURRING/NAG fire at occurrence time. |

## Entity: Tag / TaskTag
Tag(id TEXT PK, name TEXT UNIQUE lowercase, color TEXT NULL). TaskTag(task_id, tag_id, PK both, cascade). Contexts are just tags: #shop, #home, #car.

## Entity: CompletionLog
| Field | Type | Notes |
|---|---|---|
| id | TEXT PK | |
| task_id | TEXT FK | |
| occurrence_local | TEXT | which occurrence this settles ('YYYY-MM-DD[ HH:MM]') |
| action | TEXT | DONE or SKIPPED |
| completed_at | INTEGER | actual moment |
| meter_value | REAL NULL | odometer at completion (updates Task.last_done_meter) |
| note | TEXT NULL | |
Powers: COMPLETION-anchored recurrence, "last done" display, future stats. Never hard-delete.

## Entity: MeterReading
(id TEXT PK, meter_name TEXT, value REAL, recorded_at INTEGER). Generic: car km today, anything countable later. Monthly "log km" prompt is itself a RECURRING task the app seeds on first meter use.

## Settings (DataStore, not Room)
quiet_hours (start/end, default 22:00–08:00), default_all_day_time (09:00), default currency (EUR), default reminder offsets per type, snooze presets (1h/4h/1d), auto_backup (on, folder URI, keep 7), exact_alarms_opt_in. UI language is not a DataStore key (D-21): LanguageRepository wraps AppCompatDelegate.get/setApplicationLocales() directly, persisted by AppCompat's own autoStoreLocales.

## Priority engine (tunable constants in one file)
`score = round(100 × (0.4×impact + 0.6×urgency_eff) / 3)` where `urgency_eff = min(3, urgency + boost)` and boost by time-to-due: >30d→0, ≤30d→+0.5, ≤14d→+1, ≤7d→+1.5, ≤2d→+2, overdue→+3. Computed at read time; never stored. SOMEDAY and far-future items sort below scored items. Ties → earlier due first.

## Deadline escalation (DEADLINE only, v1 built-in curve, not per-task)
≤14d: 1 reminder/day; ≤7d: 2/day; ≤2d: 3/day; overdue: every 4h. Respects quiet hours (deferred to quiet end).

## Consumable math
`run_out_date = stock_recorded_at + floor(stock_qty / (dose_per_intake × intakes_per_day))`; remind at `run_out − restock_lead_days`, then daily NAG-style until user updates stock (restock action asks "how many did you buy?" and adds).

## Scheduling architecture (decision D-07)
Exactly **one** AlarmManager alarm is armed at any time = the globally nearest pending fire instant (from next_fire_at, reminder offsets, nag repeats, snoozes, escalation slots). On fire: BroadcastReceiver posts all due notifications, writes nothing it can't recover, recomputes, arms the next alarm. On BOOT_COMPLETED / app open / TZ change / daily WorkManager housekeeping: recompute everything, deliver overdue summary if anything was missed, re-arm. Exact vs inexact per Section-E pattern from fshu audit (`canScheduleExactAlarms()` branch); inexact is acceptable default, exact is opt-in.

D-42: the single armed instant may itself be an alarm-mode task's occurrence fire (never a lead-time reminder or escalation slot — those are never alarms). When it is, the arming call branches to `AlarmManager.setAlarmClock()` instead of the Section-E exact/inexact branch — always exact, no `exact_alarms_opt_in` needed, shows the status-bar alarm icon (tapping it opens MainActivity). Alarm-mode occurrence fires also ignore quiet-hours deferral entirely; everything else (lead-time reminders, DEADLINE escalation slots, snoozes) keeps deferring as before, on both alarm-mode and normal tasks alike. Delivery for an alarm fire uses a dedicated `alarm` notification channel (USAGE_ALARM audio attributes, insistent looping sound) with a full-screen intent to a minimal ring screen (AlarmRingActivity); a separate one-shot AlarmManager timer (keyed by notification id, outside this single-slot mechanism — a sibling like D-32's BackupWorker) auto-silences it after 5 minutes, leaving the notification posted.

## DST / timezone rule (decision D-06)
Canonical schedule data = local wall-clock fields. Fire instants are computed via `ZonedDateTime` at scheduling time and cached in next_fire_at. Never add fixed millisecond periods across days. Nonexistent local times (spring-forward) → shift to next valid instant; ambiguous (fall-back) → first occurrence. Unit-test Bulgaria transitions (last Sun of March/October).

## Recurrence semantics
- CALENDAR anchor: next occurrence from the fixed grid (freq/interval/days/times), independent of completion. Missed occurrences don't pile up: completing marks the *current* one; older ones auto-log as SKIPPED at housekeeping.
- COMPLETION anchor: next = last DONE completion + interval (falls back to created_at if never done).
- Meter tasks: due when (time rule due) OR (latest MeterReading − last_done_meter ≥ meter_interval).
- Occurrence actions in v1: Done / Skip / Snooze only. "Edit this occurrence only" is v2 (D-09).

## Export JSON (schema_version: 1)
`{ schema_version, app_version, exported_at, tasks[], reminder_rules[], tags[], task_tags[], completions[], meter_readings[], settings{} }` — full-fidelity, includes soft-deleted rows and `dirty` (sync foundation). Each entity array mirrors its Room columns 1:1 (DTOs, not the live entities — decoupled from the runtime schema on purpose). Import modes: replace-all (v1) — merge is v2. Unknown JSON keys are ignored on import (forward tolerance); `schema_version != 1` and malformed JSON are rejected with typed, localized errors.

D-42: `tasks[].alarm_mode` was added after schema_version 1 shipped; the field carries a Kotlin/serialization default of `false`, so a pre-M7 backup file (missing the key entirely) still imports cleanly with every task's alarm mode off — no `schema_version` bump needed for a purely additive, defaulted field.

`settings{}` (D-30) carries a deliberate *subset* of the DataStore settings — the parts that describe the data, not the device:
- Included: `quiet_hours_start`/`quiet_hours_end`, `default_all_day_time`, `currency`, `default_reminder_offsets` (per task type), `snooze_presets_minutes` + `default_snooze_duration_minutes`, the D-27/D-28 seed markers (`weekly_review_task_id`, `seeded_meter_names`), and `ui_language` (read from AppCompatDelegate at export time, D-21) — exported for reference only.
- Excluded (device-specific): backup folder URI, the auto-backup toggle, `exact_alarms_opt_in`, and the D-23 delivered watermark.

Import (D-31) is replace-all: every Room table is wiped and repopulated verbatim inside one transaction, then the settings subset above is restored — except `ui_language`, which is *never* applied on import (D-13: a phone keeps its own language even after importing someone else's export). The delivered watermark is set to the import instant (not restored from the file), so importing old history doesn't flood an overdue-summary notification. Before wiping, a safety copy of the device's current state is written to the configured backup folder (or the app's cache dir if none is set); if that safety write fails, the import is aborted before anything is touched. The Settings import flow shows a confirmation dialog with entity counts and a destructive-action warning before proceeding, and finishes with a RecomputeAndRearm pass.

Auto-backup (D-32) is a daily unique periodic worker, independent of housekeeping, enqueued only while the toggle is on **and** a folder with a persisted SAF permission is set (cancelled the moment either condition stops holding). Files are named `shuremind-backup-yyyyMMdd-HHmmss.json`; only the newest 7 matching files in the picked folder are kept.
