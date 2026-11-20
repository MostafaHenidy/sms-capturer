package com.medora.paymentsms.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.medora.paymentsms.MedoraApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Daily local retention: redact completed events older than the policy window. */
class RetentionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as MedoraApp
        val cutoff = SyncHealth.retentionCutoffMs()
        app.repository.dao.redactCompletedBefore(cutoff)
        app.stateStore.lastPurgeAt = System.currentTimeMillis()
        Result.success()
    }

    companion object {
        private const val PERIODIC = "medora_retention_daily"

        fun scheduleDaily(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val request = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
