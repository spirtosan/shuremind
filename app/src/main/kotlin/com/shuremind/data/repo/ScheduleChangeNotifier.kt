package com.shuremind.data.repo

/**
 * Repository-level hook (not scattered UI calls) so a write that can change scheduling —
 * create/edit/complete/skip/snooze, stock change, quiet-hours or exact-alarm settings change —
 * triggers RecomputeAndRearm. Kept in `data.repo` (rather than depending on `system` directly) so
 * repositories stay free of any Android-alarm-specific dependency; AppContainer wires the real
 * RecomputeAndRearm-backed implementation in after construction (see its own comment).
 */
fun interface ScheduleChangeNotifier {
    suspend fun onScheduleChanged()

    companion object {
        val NONE = ScheduleChangeNotifier { }
    }
}
