# EXTRACT_FSHU_PATTERNS.md
_Run Claude Code in the **fshu-next** repo root and paste the prompt below. Then copy the generated file into C:\Users\spirt\shuremind\extracted\._

---

READ-ONLY extraction task. Do not modify, create, or delete any file in this repo except one new output file: `extracted/shuremind_patterns.md`. No git operations.

Context: I'm building a separate reminder app (ShuRemind). I want three battle-tested patterns from this codebase, generalized and sanitized, as reference code.

Produce `extracted/shuremind_patterns.md` with these sections:

**1. AlarmScheduler helper.** Generalize the exact-alarm scheduling logic used in `FshuService.scheduleAlarmCheck()` and `ServiceRestartReceiver` (the `canScheduleExactAlarms()` API-31 branch with `setExactAndAllowWhileIdle` / `setAndAllowWhileIdle` / pre-31 fallback, RTC_WAKEUP) into a standalone Kotlin object:
`object AlarmScheduler { fun schedule(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent, exactOptIn: Boolean) }`
plus a `cancel(...)` counterpart. Include required manifest permission lines (SCHEDULE_EXACT_ALARM, USE_EXACT_ALARM) with a one-line comment on each.

**2. Boot re-arm pattern.** From the boot receiver (`ServiceRestartReceiver`): a minimal receiver skeleton handling ONLY `BOOT_COMPLETED` and `ACTION_TIMEZONE_CHANGED` that calls a `RescheduleAllUseCase` placeholder, plus the matching manifest `<receiver>` snippet with RECEIVE_BOOT_COMPLETED. Strip: LOCKED_BOOT_COMPLETED, USER_UNLOCKED, directBootAware, all fshu custom actions, all service-start logic.

**3. Permission onboarding flow.** From `PermissionSetupActivity`: the step-sequencing structure only (how steps are ordered, skipped when already granted, and resumed), reduced to three steps: POST_NOTIFICATIONS runtime request (API 33+), SCHEDULE_EXACT_ALARM settings intent (API 31+, optional/skippable), REQUEST_IGNORE_BATTERY_OPTIMIZATIONS dialog (optional/skippable). Skeleton with TODO markers, not full UI. Strip: mic, camera, location, overlay, full-screen-intent, FCM steps.

Sanitization rules: rename every fshu/Manya identifier to neutral names; remove all package names, server URLs, secrets, tokens, user-visible strings specific to the messenger. Each section: a short "source: file/lines" note, the code, and a 2–3 line "why it's shaped this way" note (e.g., why setAndAllowWhileIdle is the safe fallback).

Do not refactor beyond what's specified. Do not add libraries. Output must compile conceptually against plain Android SDK (it's reference code, not a drop-in module).
