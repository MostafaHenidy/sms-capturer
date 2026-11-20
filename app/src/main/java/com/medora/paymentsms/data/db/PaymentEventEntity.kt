package com.medora.paymentsms.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val STATUS_PENDING = "pending"
const val STATUS_SENT = "sent"
const val STATUS_FAILED = "failed"

@Entity(
    tableName = "payment_events",
    indices = [
        // Duplicate broadcasts collapse here: no lost event, no double queue.
        Index(value = ["content_digest"], unique = true),
        Index(value = ["status"]),
    ],
)
data class PaymentEventEntity(
    @PrimaryKey @ColumnInfo(name = "event_uuid") val eventUuid: String,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "provider_reference") val providerReference: String?,
    @ColumnInfo(name = "sender_hint") val senderHint: String,
    @ColumnInfo(name = "content_digest") val contentDigest: String,
    @ColumnInfo(name = "occurred_at_ms") val occurredAtMs: Long,
    @ColumnInfo(name = "status") val status: String = STATUS_PENDING,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sent_at_ms") val sentAtMs: Long? = null,
    @ColumnInfo(name = "attempts") val attempts: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
)
