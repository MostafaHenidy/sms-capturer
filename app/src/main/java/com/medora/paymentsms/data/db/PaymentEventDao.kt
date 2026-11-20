package com.medora.paymentsms.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PaymentEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: PaymentEventEntity): Long

    @Query("SELECT * FROM payment_events WHERE status = 'pending' ORDER BY created_at_ms ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<PaymentEventEntity>

    @Query("UPDATE payment_events SET status = 'sent', sent_at_ms = :sentAtMs, last_error = NULL WHERE event_uuid = :uuid")
    suspend fun markSent(uuid: String, sentAtMs: Long)

    @Query("UPDATE payment_events SET status = 'failed', attempts = attempts + 1, last_error = :error WHERE event_uuid = :uuid")
    suspend fun markFailed(uuid: String, error: String)

    @Query("UPDATE payment_events SET status = 'pending', last_error = NULL WHERE event_uuid = :uuid")
    suspend fun requeue(uuid: String)

    /** Transient failure: stay pending so the next sync picks it up, but count attempts. */
    @Query("UPDATE payment_events SET attempts = attempts + 1, last_error = :error WHERE event_uuid = :uuid")
    suspend fun recordRetry(uuid: String, error: String)

    @Query("SELECT COUNT(*) FROM payment_events WHERE status = 'pending'")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM payment_events WHERE status = 'sent'")
    suspend fun sentCount(): Int

    @Query("SELECT COUNT(*) FROM payment_events")
    suspend fun totalCount(): Int

    @Query("SELECT * FROM payment_events ORDER BY created_at_ms DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<PaymentEventEntity>

    @Query("SELECT MAX(sent_at_ms) FROM payment_events")
    suspend fun lastSentAt(): Long?

    @Query("SELECT EXISTS(SELECT 1 FROM payment_events WHERE content_digest = :digest)")
    suspend fun existsByDigest(digest: String): Boolean

    @Query("SELECT COUNT(*) FROM payment_events WHERE status != 'pending' AND created_at_ms < :cutoffMs")
    suspend fun purgeCandidateCount(cutoffMs: Long): Int

    /**
     * Retention is redaction, not deletion: keep the event id/amount/status as
     * evidence, but drop the provider reference and sender hint after the
     * window. Pending events are never touched.
     */
    @Query(
        "UPDATE payment_events SET provider_reference = NULL, sender_hint = 'redacted' " +
            "WHERE status != 'pending' AND created_at_ms < :cutoffMs",
    )
    suspend fun redactCompletedBefore(cutoffMs: Long): Int
}
