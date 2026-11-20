package com.medora.paymentsms.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Models the offline pilot flow at the persistence layer: an event queued while
 * offline survives a process restart (DB reopen), is retried, then is sent once.
 * Requires a device/emulator to execute; run connectedDebugAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
class DurableQueueInstrumentationTest {
    @Test
    fun queuedEventSurvivesRestartAndIsSentOnceAfterConnectivityReturns() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "medora_queue_test_${UUID.randomUUID()}.db"
        val event = PaymentEventEntity(
            eventUuid = UUID.randomUUID().toString(),
            sourceType = "sms",
            sourceId = "vodafone_cash",
            amountMinor = 50_000L,
            providerReference = "VF1234567890",
            senderHint = "Vodafone Cash ****1234",
            contentDigest = "e".repeat(64),
            occurredAtMs = 1_000_000L,
        )

        val first = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries().build()
        first.paymentEventDao().insert(event)
        first.close() // simulates process death / app restart while offline

        val second = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries().build()
        val pending = second.paymentEventDao().pending(10)
        assertEquals(1, pending.size)
        assertEquals(event.eventUuid, pending.first().eventUuid)

        second.paymentEventDao().recordRetry(event.eventUuid, "offline")
        assertEquals(1, second.paymentEventDao().pendingCount())
        second.paymentEventDao().markSent(event.eventUuid, 4_000L)
        assertEquals(0, second.paymentEventDao().pendingCount())
        assertEquals(1, second.paymentEventDao().sentCount())

        second.close()
        context.deleteDatabase(dbName)
    }
}
