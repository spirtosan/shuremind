# ShuRemind — PROJECT.md
_Last updated: 2026-07-04 (Planning session 1). This file is the single source of truth for scope. Upload it at the start of every planning/coding session._

## What & why
Offline-first Android reminder app for one power user (+ wife's phone). Goal: **the app holds the memory, the user never has to.** Handles everything from "buy eggs" to "yearly tax declaration with a moving deadline." Server sync is a later version; v1 is fully offline with import/export.

Design context: user prefers zero-memory-burden UX (ADHD-informed defaults). Consequences: capture in ≤2 taps, persistent nagging is normal (not annoying), snooze available everywhere, weekly review resurfaces anything stale, missed alarms recover loudly.

## The 8 reminder behaviors (core design)
| # | Type (enum) | Behavior | Real example |
|---|---|---|---|
| 1 | EVENT | Fixed date/time + lead reminders | Doctor visit, concert, call, invite |
| 2 | ANNIVERSARY | Yearly date + lead time | Birthday, wedding day |
| 3 | DEADLINE | Hard deadline + escalating reminders | Taxes, bills |
| 4 | WINDOW | Deadline exists but date unknown → recurring "check" reminder; converts to DEADLINE when date learned | Residence declaration (office moves the date) |
| 5 | NAG | Repeats every N hours until done; optional `not_before` date | Dentist (not before the 7th, then daily) |
| 6 | RECURRING | Repeats; anchor = CALENDAR (fixed grid, e.g. daily meds 08:00/20:00) or COMPLETION (interval since last done, e.g. water flowers every 3 days) | Meds intake, flowers, cleaning, routines |
| 7 | CONSUMABLE | Stock + daily dose → computed run-out date, remind N days before | Wife's medicine supply |
| 8 | SOMEDAY | No date, no alarms; surfaces in weekly review | Broken shower tray |

Extras: **quick timer** (UI shortcut creating an EVENT at now+X, e.g. washing machine) and **meter-based maintenance** (RECURRING task with km interval + meter readings; due when time OR km threshold crossed, e.g. car oil).

## v1 scope (offline)
- Quick capture: one text field + optional one-tap toggles (type preset, impact, urgency, tags, cost).
- The 8 behaviors above; lead-time reminders (multiple per task).
- Priority engine: score = f(impact, urgency, deadline proximity), grows as due date nears; list sorted by score.
- Tags/contexts (#shop, #home, #car) with filtered views.
- Notifications with actions: Done / Snooze / Skip. Quiet hours (global). Deadline escalation curve.
- Missed-alarm recovery: on boot / app open / daily housekeeping, overdue items surface as a loud summary.
- Completion log (powers "when did I last change oil", COMPLETION recurrence, future stats).
- Meter readings screen (car km) + monthly "log your km" prompt.
- Weekly review (SOMEDAY + stale items).
- Import/export: versioned JSON. Daily auto-backup to user-picked folder (keep last 7).
- Cost field (default currency EUR) — informational only in v1.
- Multilingual UI: English, Bulgarian, Russian; language selectable in Settings (default: system). All text in string resources. (D-18)

## v2+ (explicitly deferred)
Server sync (schema is sync-ready from day 1), budget planning, task dependencies/prerequisites, geofencing, per-occurrence editing beyond done/skip/snooze, shared lists between phones, statistics, per-task snooze duration override.

## Non-goals
No accounts, no cloud, no analytics, no GMS/Firebase dependency in v1. Not a calendar replacement.

## Tech stack
Kotlin 2.x, Jetpack Compose (Material 3), Room, DataStore (settings), AlarmManager (single-next-alarm pattern), WorkManager (daily housekeeping + auto-backup only), kotlinx.serialization (export), java.time with core library desugaring. minSdk 26, targetSdk = current requirement at build time. Single module `:app`. Package/applicationId `com.shuremind` is final; display name "ShuRemind" (D-35). No new libraries without explicit approval.

## Build phases (for Claude Code)
- **M1** Data layer + engine: Room schema, recurrence math, priority score, run-out computation. Pure-Kotlin engine, heavily unit-tested (incl. DST cases).
- **M2** Capture UI + main list: quick-add, type presets, toggles, sorted list, tag filters, done/snooze/skip.
- **M3** Alarms & notifications: scheduler, boot receiver, notification channels/actions, quiet hours, missed-alarm recovery, permission onboarding, deadline escalation curve.
- **M4** Advanced behaviors: WINDOW, CONSUMABLE, meters, weekly review.
- **M5** Import/export + auto-backup + polish.

## Acceptance test cases (the user's real life)
1. Dentist: NAG, not_before=2026-07-07, every 24h, impact 3 → nags daily from the 7th until done.
2. Shower tray: SOMEDAY, cost noted → appears in every weekly review, never alarms.
3. Car oil: RECURRING anchor=COMPLETION, 12 months + meter "car" every 10,000 km, last-done unknown → user enters estimate; monthly km prompt; due when either threshold crossed.
4. Property tax: DEADLINE with P14D/P7D/P1D reminders + escalation near due.
5. Residence declaration: WINDOW, monthly check from September → converts to DEADLINE when office publishes date.
6. Wife's meds intake (her phone): RECURRING CALENDAR daily at 08:00, 20:00.
7. Wife's meds stock: CONSUMABLE stock=30, dose 2/day, lead 5 days → reminder ~10 days later.
8. Flowers: RECURRING COMPLETION every 3 days.
9. Birthday: ANNIVERSARY, leads P14D (gift) + P1D.
10. Washing machine: quick timer +2h.
11. Eggs: plain task tagged #shop → visible when #shop filter tapped at the store.
