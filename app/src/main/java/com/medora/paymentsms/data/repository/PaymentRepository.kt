package com.medora.paymentsms.data.repository

import com.medora.paymentsms.data.api.ApiEnvelope
import com.medora.paymentsms.data.api.DeviceApi
import com.medora.paymentsms.data.api.PairData
import com.medora.paymentsms.data.api.PairRequest
import com.medora.paymentsms.data.db.AppDatabase
import com.medora.paymentsms.data.db.PaymentEventDao
import com.medora.paymentsms.data.db.PaymentEventEntity
import com.medora.paymentsms.data.db.STATUS_PENDING
import com.medora.paymentsms.data.prefs.AppStateStore
import com.medora.paymentsms.data.prefs.TokenStore
import com.medora.paymentsms.sms.PaymentEventDraft
import java.time.Instant

sealed interface PairResult {
    data class Success(val deviceName: String) : PairResult
    data class InvalidCode(val message: String) : PairResult
    data class Failure(val message: String) : PairResult
}

/** Single entry point for local queueing, pairing and disconnect. */
class PaymentRepository(
    val dao: PaymentEventDao,
    private val api: DeviceApi,
    private val tokenStore: TokenStore,
    private val stateStore: AppStateStore,
) {
    suspend fun enqueue(draft: PaymentEventDraft): Boolean {
        if (dao.existsByDigest(draft.contentDigest)) return false // duplicate broadcast
        return dao.insert(
            PaymentEventEntity(
                eventUuid = draft.eventUuid,
                sourceType = draft.sourceType,
                sourceId = draft.sourceId,
                amountMinor = draft.amountMinor,
                providerReference = draft.providerReference,
                senderHint = draft.senderHint,
                contentDigest = draft.contentDigest,
                occurredAtMs = draft.occurredAtMs,
                status = STATUS_PENDING,
            ),
        ) != -1L
    }

    suspend fun pair(rawCode: String, deviceName: String): PairResult {
        val code = rawCode.filter { it.isDigit() }
        if (code.length != 8) return PairResult.InvalidCode("Enter the 8-digit code from the dashboard.")

        return try {
            val response = api.pair(PairRequest(code = code, name = deviceName.trim().take(120)))
            val body: ApiEnvelope<PairData>? = response.body()
            when {
                response.code() == 422 -> PairResult.InvalidCode(body?.message ?: "Code is invalid or expired.")
                response.isSuccessful && body?.success == true && body.data?.token != null -> {
                    val data = body.data
                    tokenStore.save(data.token, data.device.id, data.device.name, data.device.paired_at)
                    stateStore.authRevoked = false
                    PairResult.Success(data.device.name)
                }
                else -> PairResult.Failure(body?.message ?: "Could not pair (HTTP ${response.code()}).")
            }
        } catch (e: Exception) {
            PairResult.Failure(e.message ?: "Network unavailable. Try again when online.")
        }
    }

    suspend fun disconnect() {
        tokenStore.clearCredentials()
        stateStore.listeningEnabled = false
        stateStore.authRevoked = false
        stateStore.lastError = null
        // Event rows are intentionally kept as local review evidence.
    }

    companion object {
        fun create(
            database: AppDatabase,
            api: DeviceApi,
            tokenStore: TokenStore,
            stateStore: AppStateStore,
        ) = PaymentRepository(database.paymentEventDao(), api, tokenStore, stateStore)

        fun isoUtc(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()
    }
}
