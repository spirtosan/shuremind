# ShuRemind reference patterns

Extracted and generalized from a messenger app's background-service plumbing. All identifiers renamed, no app-specific strings, no secrets/URLs. Reference code only — not a drop-in module.

---

## 1. AlarmScheduler helper

**Source:** `service/FshuService.kt` (`scheduleAlarmCheck()`, ~L411-431, and the identical inline logic in `onTaskRemoved()`, ~L390-399) and `service/ServiceRestartReceiver.kt` (`scheduleNextAlarm()`, ~L72-91). All three call sites duplicated the same API-31 branch; generalized here into one object.

```kotlin
object AlarmScheduler {

    /**
     * Schedules a one-shot wake alarm.
     *
     * @param exactOptIn true if this alarm must fire at (approximately) the exact
     *   requested time; false if an inexact/batched fire is acceptable. When true,
     *   exactness is still gated by the API-31+ user grant — this flag expresses
     *   the caller's *intent*, not a guarantee.
     */
    fun schedule(
        context: Context,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
        exactOptIn: Boolean
    ) {
        val am = context.getSystemService(AlarmManager::class.java)
        val canFireExact = exactOptIn &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms())

        if (canFireExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(context: Context, pendingIntent: PendingIntent) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
    }
}
```

Manifest permissions:

```xml
<!-- Lets the app request exact-alarm scheduling; user must grant it via Settings on API 31+ (checked at runtime with canScheduleExactAlarms()). -->
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>

<!-- Grants exact alarms WITHOUT a user prompt, but Play Store restricts it to apps whose core function is alarms/timers/calendar — a reminder app plausibly qualifies, a messenger does not. -->
<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>
```

**Why it's shaped this way:** Below API 31, exact alarms are granted at install time via the manifest permission alone, so the code path always calls `setExactAndAllowWhileIdle` unconditionally. From API 31 on, the OS requires a runtime check (`canScheduleExactAlarms()`) because the user can revoke exact-alarm scheduling in Settings at any time; `setAndAllowWhileIdle` is the safe fallback because it still survives Doze/idle maintenance windows, it just isn't guaranteed to fire at the precise millisecond — for a reminder that's late by a few minutes, that's an acceptable degradation instead of a crash or a silently-dropped alarm.

---

## 2. Boot re-arm pattern

**Source:** `service/ServiceRestartReceiver.kt`. The original handles five actions (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `USER_UNLOCKED`, and two app-custom actions for service-restart/watchdog) because it's re-arming a persistent WebSocket connection, not a scheduled alarm. Stripped here to just the two triggers a reminder app's scheduled alarms actually need to survive: a device boot (all pending `AlarmManager` alarms are wiped on boot) and a timezone change (wall-clock-anchored reminders need re-deriving against the new zone).

```kotlin
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // TODO: obtain your DI entry point / repository here
                RescheduleAllUseCase().rescheduleAll(context)
            }
        }
    }
}
```

Manifest:

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>

<receiver
    android:name=".BootRescheduleReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED"/>
        <action android:name="android.intent.action.TIMEZONE_CHANGED"/>
    </intent-filter>
</receiver>
```

**Why it's shaped this way:** `RescheduleAllUseCase` is a placeholder for "read every pending reminder from storage and call `AlarmScheduler.schedule()` for each" — kept as a single re-entrant call so both triggers (boot and timezone change) share one code path instead of duplicating the rescheduling logic. `directBootAware`, `LOCKED_BOOT_COMPLETED`, and `USER_UNLOCKED` were dropped because those exist in the source only to restart a service before the user unlocks the device — a reminder app has no pre-unlock service to restart, alarms just need to be re-registered once boot completes normally.

---

## 3. Permission onboarding flow

**Source:** `ui/permission/PermissionSetupActivity.kt`. Keeping only the step-sequencing structure (a `Step` list walked by index, `handleStep()` dispatching per-step logic, `advance()` moving forward and finishing when the list is exhausted), reduced from 9 steps to 3: notifications, exact-alarm opt-in, battery-optimization exemption.

```kotlin
class PermissionSetupActivity : AppCompatActivity() {

    private data class Step(
        val title: String,
        val description: String,
        val buttonLabel: String,
        val isSkippable: Boolean
    )

    private lateinit var steps: List<Step>
    private var currentStep = 0

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { advance() }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { advance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: inflate layout / view binding

        steps = buildList {
            add(Step(
                title = "Notifications",
                description = "TODO: why the app needs to post reminder notifications.",
                buttonLabel = "Allow Notifications",
                isSkippable = false
            ))
            add(Step(
                title = "Battery optimization",
                description = "TODO: why exemption keeps reminders firing on time in the background.",
                buttonLabel = "Open Settings",
                isSkippable = true
            ))
            // Kept last: only exists on API 31+, so it can't shift the fixed
            // indices (0, 1) that handleStep() below switches on.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Step(
                    title = "Exact alarms",
                    description = "TODO: why exact timing needs this opt-in permission.",
                    buttonLabel = "Allow Exact Alarms",
                    isSkippable = true
                ))
            }
        }

        // TODO: wire "Allow"/"Skip" buttons to handleStep() / advance()
        showStep(currentStep)
    }

    private fun showStep(index: Int) {
        val step = steps[index]
        // TODO: bind step.title / step.description / step.buttonLabel to views
        // TODO: show/hide a skip affordance based on step.isSkippable
    }

    private fun handleStep() {
        when (currentStep) {
            0 -> { // notifications
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    advance()
                }
            }
            1 -> { // battery optimization
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                settingsLauncher.launch(intent)
            }
            2 -> { // exact alarms (only reached when the step above added it, i.e. API 31+)
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                settingsLauncher.launch(intent)
            }
        }
    }

    private fun advance() {
        currentStep++
        if (currentStep >= steps.size) {
            // TODO: persist "setup done" flag, navigate to next screen
            finish()
        } else {
            showStep(currentStep)
        }
    }
}
```

**Why it's shaped this way:** The API-gated step is appended at the *end* of the list rather than inserted in the middle — this is lifted directly from the source, where the one conditional step (full-screen-intent, API 34+) is also last. It means `handleStep()`'s `when (currentStep)` can switch on fixed literal indices without needing to recompute an offset depending on which steps got included for the current OS version. `isSkippable` is carried on the `Step` data class rather than inferred from index so the UI layer can decide whether to show a "Skip" affordance without the activity needing a second parallel list.
