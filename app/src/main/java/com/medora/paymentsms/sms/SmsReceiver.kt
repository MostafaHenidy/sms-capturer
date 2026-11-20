package com.medora.paymentsms.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.medora.paymentsms.MedoraApp
import com.medora.paymentsms.sync.EventSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Survives receiver destruction while goAsync() work completes. */
private object ReceiverScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val app = context.applicationContext as MedoraApp
        if (!app.stateStore.listeningEnabled || app.stateStore.authRevoked || !app.tokenStore.isPaired()) return

        val pendingResult = goAsync()
        ReceiverScope.scope.launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
                val classifier = SmsClassifier()

                // Multipart SMS arrive as several PDUs from one sender; join in order.
                messages.groupBy { it.displayOriginatingAddress.orEmpty() }
                    .forEach { (sender, parts) ->
                        if (sender.isEmpty()) return@forEach
                        val ordered = parts.sortedBy { it.timestampMillis }
                        val body = ordered.joinToString(separator = "") { it.messageBody.orEmpty() }
                        val timestamp = ordered.maxOf { it.timestampMillis }
                        val occurredAt = if (timestamp > 0) timestamp else System.currentTimeMillis()

                        when (val result = classifier.classify(RawSms(sender, body, occurredAt))) {
                            is Classification.Relevant -> {
                                // Dedupe is enforced by Room; enqueue only on a fresh insert.
                                if (app.repository.enqueue(result.draft)) {
                                    EventSyncWorker.enqueue(context.applicationContext)
                                }
                            }
                            is Classification.Ignored -> Unit // never persisted
                        }
                    }
            } catch (_: Exception) {
                // Never crash the receiver and never log message content.
                app.stateStore.lastError = "Could not process an incoming message."
            } finally {
                pendingResult.finish()
            }
        }
    }
}
