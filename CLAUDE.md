# CLAUDE.md — ShuRemind (Android)
Rules for Claude Code in this repo. Read PROJECT.md, DATA_MODEL.md, DECISIONS.md before non-trivial work. If a change conflicts with a D-xx decision, stop and ask.

## Stack (fixed — see D-16)
Kotlin 2.x · Jetpack Compose + Material 3 · androidx.appcompat (approved, D-18) · Room · DataStore · AlarmManager · WorkManager (housekeeping + backup only) · kotlinx.serialization · java.time (core library desugaring). minSdk 26. Single module `:app`, package `com.shuremind`. **No new dependencies without explicit approval. No GMS/Firebase. No foreground service.**

## Architecture
- Layers: `data` (Room entities/DAOs/repos) → `engine` (pure Kotlin, no Android imports) → `ui` (Compose + ViewModel + StateFlow) → `system` (alarms, receivers, notifications, workers).
- `engine` holds ALL logic: recurrence math, priority score, escalation slots, consumable run-out, meter-due, next-fire computation. It must be testable without a device.
- Scheduling follows D-07 (single-next-alarm) and D-06 (wall-clock local, DST-safe). Alarm branching follows D-08 / extracted Section-E helper.
- Notifications: channels = reminders(HIGH), nags(HIGH), overdue(HIGH), review(DEFAULT). Every notification has Done/Snooze (+Skip for recurring) actions handled by a receiver, app open not required.

## Hard rules
- UUID string ids; soft delete only (deleted_at); CompletionLog append-only; keep `dirty`/`updated_at` maintained even though sync is v2.
- Never compute occurrences by adding fixed millisecond spans across day boundaries.
- Recompute + re-arm alarms on: alarm fire, boot, app open, timezone change, daily housekeeping.
- All engine constants (priority weights, escalation curve, snooze presets) live in one `EngineTuning.kt`.
- Schema changes: update DATA_MODEL.md + add a D-xx line in the same commit; Room migration required (no destructive fallback).
- i18n (D-18): zero hardcoded user-visible strings — everything through R.string/plurals with values-bg/ and values-ru/ translations added in the same commit as the English string. Counts always use <plurals> (Russian). Language picker via AppCompatDelegate.setApplicationLocales; MainActivity extends AppCompatActivity; keep locales_config.xml in sync.
- CONSUMABLE run-out anchors to stock_recorded_at (D-19), never created_at or now.

## Testing (non-negotiable)
Unit tests for `engine`: each of the 8 behaviors' next-occurrence math; COMPLETION vs CALENDAR anchors; month-end clamping; Bulgaria DST transitions (last Sunday March/October); priority boost table; consumable run-out; meter-OR-time due; missed-occurrence auto-skip. Run tests before declaring any M-phase done.

## Workflow
- Work in small verifiable steps; after each step: build + tests green, then summarize what changed and why in ≤5 lines.
- Follow the M1–M5 phases in PROJECT.md; do not pull v2 items (sync, budget, dependencies, geofencing, per-occurrence edits) into scope.
- Acceptance = the 11 test cases in PROJECT.md work end-to-end.
- D-20: the user does not hand-edit files. When asked to update planning docs (session logs, new D-xx lines, scope changes), print the proposed text for review first, then write it yourself on approval — never instruct the user to paste anything.
