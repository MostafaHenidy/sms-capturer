package com.medora.paymentsms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medora.paymentsms.MedoraApp
import com.medora.paymentsms.service.ListeningForegroundService
import com.medora.paymentsms.sync.EventSyncWorker

/** Restores listening/sync after reboot or app update, only if still enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MedoraApp
        if (!app.stateStore.listeningEnabled || app.tokenStore.isPaired().not() || app.stateStore.authRevoked) return

        EventSyncWorker.schedulePeriodic(context.applicationContext)
        try {
            ListeningForegroundService.start(context.applicationContext)
        } catch (_: Exception) {
            // Android may restrict background FGS starts; the broadcast receiver
            // still captures SMS and WorkManager still syncs when constraints allow.
        }
    }
}
