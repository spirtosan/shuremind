package com.shuremind.data.repo

/**
 * D-23: single global delivered watermark (DataStore last_handled_at). Everything at/before the
 * watermark has already been notified about; advancing it must only happen AFTER a batch of
 * notifications has actually been posted for it — the failure mode is duplicate delivery, never loss.
 */
interface DeliveryWatermarkRepository {

    /** Epoch millis. On first-ever read (no stored value yet) this defaults to *now* and persists
     * that as the watermark, so a fresh install doesn't retroactively flood an overdue summary for
     * every pre-existing task. */
    suspend fun get(): Long

    suspend fun advanceTo(instant: Long)
}
