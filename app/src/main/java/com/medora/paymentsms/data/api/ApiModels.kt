package com.medora.paymentsms.data.api

/** Matches Laravel's App\Support\ApiResponse envelope. */
data class ApiEnvelope<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
    val code: String? = null,
)

data class PairRequest(val code: String, val name: String)
data class PairData(val device: PairedDevice, val token: String)
data class PairedDevice(val id: Long, val name: String, val paired_at: String)
data class HeartbeatData(val last_seen_at: String)

/** No status field is ever sent by the device. Laravel decides everything. */
data class EventRequest(
    val event_uuid: String,
    val source_type: String,
    val source_id: String,
    val occurred_at: String,
    val amount_piastres: Long,
    val provider_reference: String?,
    val sender_hint: String,
    val content_digest: String,
)

data class EventData(
    val event_uuid: String,
    val status: String,
    val parse_status: String,
    val server_received_at: String,
)
