package com.medora.paymentsms.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.medora.paymentsms.MedoraApp
import com.medora.paymentsms.data.api.ApiFactory
import com.medora.paymentsms.data.api.EventRequest
import com.medora.paymentsms.data.repository.PaymentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Best-effort, idempotent upload. A 202 receipt from Laravel is the only success. */
class EventSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as MedoraApp
        val bearer = app.tokenStore.bearer()
        if (bearer == null || app.stateStore.authRevoked) return@withContext Result.success()

        // Heartbeat is cosmetic status; never block event upload on it.
        try {
            @Suppress("UNUSED_VARIABLE")
            val ignored = ApiFactory.create().heartbeat(bearer)
            app.stateStore.lastHeartbeatAt = System.currentTimeMillis()
        } catch (_: Exception) {
            // Offline: continue with local queue. Do not claim a heartbeat.
        }

        val dao = app.repository.dao
        val pending = dao.pending(limit = BATCH_SIZE)
        if (pending.isEmpty()) return@withContext Result.success()

        val api = ApiFactory.create()
        var retryNeeded = false

        for (event in pending) {
            val request = EventRequest(
                event_uuid = event.eventUuid,
                source_type = event.sourceType,
                source_id = event.sourceId,
                occurred_at = PaymentRepository.isoUtc(event.occurredAtMs),
                amount_piastres = event.amountMinor,
                provider_reference = event.providerReference,
                sender_hint = event.senderHint,
                content_digest = event.contentDigest,
            )

            val action = try {
                val response = api.sendEvent(bearer, request)
                SyncPolicy.decide(response.code(), response.body()?.success)
            } catch (_: Exception) {
                SyncAction.RETRY
            }

            when (action) {
                SyncAction.MARK_SENT -> {
                    dao.markSent(event.eventUuid, System.currentTimeMillis())
                    app.stateStore.lastSyncAt = System.currentTimeMillis()
                    app.stateStore.lastError = null
                }
                SyncAction.FAIL_PERMANENT -> {
                    // Validation rejected: do not hammer the server with a bad row.
                    dao.markFailed(event.eventUuid, "rejected by server (validation)")
                    app.stateStore.lastError = "An event was rejected by the server; review locally."
                }
                SyncAction.STOP_AUTH -> {
                    // Revoked/unauthorised token: stop syncing, keep evidence.
                    app.tokenStore.clearCredentials()
                    app.stateStore.authRevoked = true
                    app.stateStore.listeningEnabled = false
                    app.stateStore.lastError = "Device was disconnected or revoked. Pair again to resume."
                    cancelAll(applicationContext)
                    return@withContext Result.success()
                }
                SyncAction.RETRY -> {
                    dao.recordRetry(event.eventUuid, "offline or server error")
                    retryNeeded = true
                }
            }
        }

        // lastSyncAt means "at least one event was accepted", never "we tried".
        // It is set only in the MARK_SENT branch above.
        if (retryNeeded) {
            app.stateStore.lastError = "Some events could not be uploaded yet; retrying automatically."
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val BATCH_SIZE = 50
        private const val ONE_SHOT = "medora_event_sync_once"
        private const val PERIODIC = "medora_event_sync_periodic"

        private fun constraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<EventSyncWorker>()
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.KEEP, request)
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<EventSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(ONE_SHOT)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
