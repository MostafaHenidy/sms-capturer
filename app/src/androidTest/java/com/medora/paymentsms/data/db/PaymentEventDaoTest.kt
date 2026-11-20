package com.medora.paymentsms.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentEventDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: PaymentEventDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.paymentEventDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun duplicateBroadcastIsDeduplicatedBeforeQueueing() = runBlocking {
        val digest = "a".repeat(64)
        val event = sample(digest)

        assertTrue(dao.insert(event) != -1L)
        assertEquals(-1L, dao.insert(sample(digest))) // same body+second, different uuid
        assertEquals(1, dao.pendingCount())
        assertEquals(1, dao.totalCount())
    }

    @Test
    fun sentEventLeavesThePendingQueueAndRecordsLastSync() = runBlocking {
        val event = sample("b".repeat(64))
        dao.insert(event)
        assertEquals(1, dao.pendingCount())

        dao.markSent(event.eventUuid, 5_000L)
        assertEquals(0, dao.pendingCount())
        assertEquals(1, dao.sentCount())
        assertEquals(5_000L, dao.lastSentAt())
    }

    @Test
    fun transientRetryKeepsTheEventPendingButCountsAttempts() = runBlocking {
        val event = sample("c".repeat(64))
        dao.insert(event)

        dao.recordRetry(event.eventUuid, "offline")
        assertEquals(1, dao.pendingCount())
        assertEquals(1, dao.recent(10).first().attempts)
        assertEquals("offline", dao.recent(10).first().lastError)
    }

    @Test
    fun permanentFailureRemovesTheEventFromTheRetryQueue() = runBlocking {
        val event = sample("d".repeat(64))
        dao.insert(event)

        dao.markFailed(event.eventUuid, "validation rejected")
        assertEquals(0, dao.pendingCount())
        assertEquals(1, dao.totalCount()) // evidence retained locally
    }

    private fun sample(digest: String) = PaymentEventEntity(
        eventUuid = java.util.UUID.randomUUID().toString(),
        sourceType = "sms",
        sourceId = "vodafone_cash",
        amountMinor = 50_000L,
        providerReference = "VF1234567890",
        senderHint = "****1234",
        contentDigest = digest,
        occurredAtMs = 1_000_000L,
    )
}
