package com.medora.paymentsms.sync

/**
 * Pure operational policy shared by the sync worker, retention worker and UI.
 * No Android APIs here so the thresholds can be unit tested on the JVM.
 */
object SyncHealth {
    /** A warning threshold, not a claim that a payment was missed. */
    const val STALE_AFTER_MS: Long = 15L * 60 * 1000
    const val RETENTION_DAYS: Int = 30

    fun isStale(lastSyncAtMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        lastSyncAtMs <= 0L || nowMs - lastSyncAtMs > STALE_AFTER_MS

    /** A pending backlog means the queue is not fully caught up, even if a sync just happened. */
    fun isDegraded(lastSyncAtMs: Long, pendingCount: Int, nowMs: Long = System.currentTimeMillis()): Boolean =
        isStale(lastSyncAtMs, nowMs) || pendingCount > 0

    fun healthLabel(lastSyncAtMs: Long, pendingCount: Int, nowMs: Long = System.currentTimeMillis()): String = when {
        isStale(lastSyncAtMs, nowMs) && pendingCount > 0 -> "Degraded — sync stale and events waiting"
        isStale(lastSyncAtMs, nowMs) -> "Degraded — no recent successful sync"
        pendingCount > 0 -> "Degraded — events waiting to sync"
        else -> "Healthy"
    }

    fun retentionCutoffMs(nowMs: Long = System.currentTimeMillis(), days: Int = RETENTION_DAYS): Long =
        nowMs - days.toLong() * 24L * 60L * 60L * 1000L
}
