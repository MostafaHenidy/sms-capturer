package com.medora.paymentsms.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncHealthTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `a recent sync with no backlog is healthy`() {
        val last = now - 60_000L
        assertFalse(SyncHealth.isStale(last, now))
        assertFalse(SyncHealth.isDegraded(last, 0, now))
        assertEquals("Healthy", SyncHealth.healthLabel(last, 0, now))
    }

    @Test
    fun `no sync at all is degraded but never claims a payment was missed`() {
        assertTrue(SyncHealth.isStale(0L, now))
        assertTrue(SyncHealth.isDegraded(0L, 0, now))
        assertEquals("Degraded — no recent successful sync", SyncHealth.healthLabel(0L, 0, now))
    }

    @Test
    fun `a backlog is degraded even immediately after a successful sync`() {
        assertFalse(SyncHealth.isStale(now, now))
        assertTrue(SyncHealth.isDegraded(now, 1, now))
        assertEquals("Degraded — events waiting to sync", SyncHealth.healthLabel(now, 1, now))
    }

    @Test
    fun `stale plus backlog reports both conditions`() {
        assertEquals(
            "Degraded — sync stale and events waiting",
            SyncHealth.healthLabel(0L, 3, now),
        )
    }

    @Test
    fun `retention cutoff uses whole days and is configurable`() {
        val day = 24L * 60L * 60L * 1000L
        assertEquals(now - 30 * day, SyncHealth.retentionCutoffMs(now))
        assertEquals(now - 7 * day, SyncHealth.retentionCutoffMs(now, 7))
    }
}
