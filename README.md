# ShuRemind

Offline-first Android reminder app for one power user (+ a secondary user's phone). Goal: **the app holds the memory, the user never has to.**

Handles everything from "buy eggs" to "yearly tax declaration with a moving deadline" — designed around zero-memory-burden UX: quick capture, persistent nagging until done, snooze everywhere, weekly review for stale items, and loud recovery from missed alarms.

## The 8 reminder behaviors

| # | Type | Behavior | Example |
|---|---|---|---|
| 1 | EVENT | Fixed date/time + lead reminders | Doctor visit, concert |
| 2 | ANNIVERSARY | Yearly date + lead time | Birthday |
| 3 | DEADLINE | Hard deadline + escalating reminders | Taxes, bills |
| 4 | WINDOW | Deadline exists but date unknown → recurring check; converts to DEADLINE once known | Residence declaration |
| 5 | NAG | Repeats every N hours until done; optional `not_before` | Dentist |
| 6 | RECURRING | CALENDAR (fixed grid) or COMPLETION (interval since last done) anchor | Meds intake, flowers |
| 7 | CONSUMABLE | Stock + daily dose → computed run-out date | Medicine supply |
| 8 | SOMEDAY | No date, no alarms; surfaces in weekly review | Broken shower tray |

Plus quick timers (EVENT at now+X) and meter-based maintenance (time OR distance threshold, e.g. car oil).

## Stack

Kotlin 2.x · Jetpack Compose (Material 3) · androidx.appcompat · Room · DataStore · AlarmManager (single-next-alarm) · WorkManager (housekeeping + backup only) · kotlinx.serialization · java.time (core library desugaring). minSdk 26. Single module `:app`, package `com.shuremind`.

No accounts, no cloud, no analytics, no GMS/Firebase dependency. Fully offline; sync is a later, unimplemented version.

## Project layout

```
app/src/main/kotlin/com/shuremind/
  data/     Room entities, DAOs, repositories
  engine/   Pure Kotlin — recurrence math, priority score, escalation, run-out (no Android imports)
  ui/       Compose screens, ViewModels, StateFlow
  system/   Alarms, receivers, notifications, workers
app/src/test/kotlin/com/shuremind/engine/   Unit tests for the engine layer
```

## Building

```
./gradlew assembleDebug
./gradlew test
```

## Documentation

- [`PROJECT.md`](PROJECT.md) — scope, behaviors, build phases, acceptance tests
- [`DATA_MODEL.md`](DATA_MODEL.md) — schema
- [`DECISIONS.md`](DECISIONS.md) — architecture decision log (D-xx)
- [`SESSION_LOG.md`](SESSION_LOG.md) — cross-session project memory
- [`SMOKE_TEST.md`](SMOKE_TEST.md) — on-device pre-release checklist
- [`CLAUDE.md`](CLAUDE.md) — rules for AI-assisted development in this repo

## License

[GPL-3.0](LICENSE)
