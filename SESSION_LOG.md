# ShuRemind — SESSION_LOG.md
_Project memory across chat sessions._

## How to use (every session)
1. **Start of a planning session (Opus):** upload PROJECT.md + DECISIONS.md + this file. First line of your message: "Continue ShuRemind planning. Read the files, confirm current state in 3 lines, then we work."
2. **Start of a coding session (Claude Code):** CLAUDE.md is read automatically from the repo root; keep PROJECT.md / DATA_MODEL.md / DECISIONS.md in the repo too and reference them in prompts ("implement M1 per DATA_MODEL.md").
3. **End of every session:** the model proposes (a) a new Session entry for this file, (b) any new D-xx lines, (c) PROJECT.md changes if scope moved — then, per D-20, the updates are applied via a Claude Code prompt, never pasted by hand.

## Current state
- Phase: **M1 + M1.5 complete → ready for M2 (capture UI + main list)**
- Next concrete step: M2 with Claude Code; first M2 tasks: DI wiring so ViewModels get repos (never DAOs), Settings screen calling LanguageRepository, resolve app_language DataStore-vs-autoStoreLocales duplication (pick one source of truth, note the choice in DATA_MODEL.md).
- Open questions: final app/package name (placeholder `com.shuremind`); confirm minSdk 26 on both target phones.

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
