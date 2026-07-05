# ShuRemind — SESSION_LOG.md
_Project memory across chat sessions._

## How to use (every session)
1. **Start of a planning session (Opus):** upload PROJECT.md + DECISIONS.md + this file. First line of your message: "Continue ShuRemind planning. Read the files, confirm current state in 3 lines, then we work."
2. **Start of a coding session (Claude Code):** CLAUDE.md is read automatically from the repo root; keep PROJECT.md / DATA_MODEL.md / DECISIONS.md in the repo too and reference them in prompts ("implement M1 per DATA_MODEL.md").
3. **End of every session:** ask the model to output (a) a new Session entry for this file, (b) any new D-xx lines for DECISIONS.md, (c) updated PROJECT.md sections if scope changed. Paste them into the files in C:\Users\spirt\shuremind.

## Current state
- Phase: **planning complete → ready for M1 (data layer + engine)**
- Next concrete step: run EXTRACT_FSHU_PATTERNS.md in the fshu-next repo, drop the output into shuremind, then start M1 with Claude Code (Sonnet).
- Open questions: none blocking. Minor: final app/package name (placeholder `com.shuremind`); confirm minSdk 26 is low enough for both target phones.

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
