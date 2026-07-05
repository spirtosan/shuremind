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
quiet_hours (start/end, default 22:00–08:00), default_all_day_time (09:00), default currency (EUR), default reminder offsets per type, snooze presets (1h/4h/1d), auto_backup (on, folder URI, keep 7), exact_alarms_opt_in, app_language (system|en|bg|ru, default system).

## Priority engine (tunable constants in one file)
`score = round(100 × (0.4×impact + 0.6×urgency_eff) / 3)` where `urgency_eff = min(3, urgency + boost)` and boost by time-to-due: >30d→0, ≤30d→+0.5, ≤14d→+1, ≤7d→+1.5, ≤2d→+2, overdue→+3. Computed at read time; never stored. SOMEDAY and far-future items sort below scored items. Ties → earlier due first.

## Deadline escalation (DEADLINE only, v1 built-in curve, not per-task)
≤14d: 1 reminder/day; ≤7d: 2/day; ≤2d: 3/day; overdue: every 4h. Respects quiet hours (deferred to quiet end).

## Consumable math
`run_out_date = stock_recorded_at + floor(stock_qty / (dose_per_intake × intakes_per_day))`; remind at `run_out − restock_lead_days`, then daily NAG-style until user updates stock (restock action asks "how many did you buy?" and adds).

## Scheduling architecture (decision D-07)
Exactly **one** AlarmManager alarm is armed at any time = the globally nearest pending fire instant (from next_fire_at, reminder offsets, nag repeats, snoozes, escalation slots). On fire: BroadcastReceiver posts all due notifications, writes nothing it can't recover, recomputes, arms the next alarm. On BOOT_COMPLETED / app open / TZ change / daily WorkManager housekeeping: recompute everything, deliver overdue summary if anything was missed, re-arm. Exact vs inexact per Section-E pattern from fshu audit (`canScheduleExactAlarms()` branch); inexact is acceptable default, exact is opt-in.

## DST / timezone rule (decision D-06)
Canonical schedule data = local wall-clock fields. Fire instants are computed via `ZonedDateTime` at scheduling time and cached in next_fire_at. Never add fixed millisecond periods across days. Nonexistent local times (spring-forward) → shift to next valid instant; ambiguous (fall-back) → first occurrence. Unit-test Bulgaria transitions (last Sun of March/October).

## Recurrence semantics
- CALENDAR anchor: next occurrence from the fixed grid (freq/interval/days/times), independent of completion. Missed occurrences don't pile up: completing marks the *current* one; older ones auto-log as SKIPPED at housekeeping.
- COMPLETION anchor: next = last DONE completion + interval (falls back to created_at if never done).
- Meter tasks: due when (time rule due) OR (latest MeterReading − last_done_meter ≥ meter_interval).
- Occurrence actions in v1: Done / Skip / Snooze only. "Edit this occurrence only" is v2 (D-09).

## Export JSON (schema_version: 1)
`{ schema_version, app_version, exported_at, tasks[], reminder_rules[], tags[], task_tags[], completions[], meter_readings[], settings{} }` — full-fidelity, includes soft-deleted rows (sync foundation). Import modes: replace-all (v1) — merge is v2.
