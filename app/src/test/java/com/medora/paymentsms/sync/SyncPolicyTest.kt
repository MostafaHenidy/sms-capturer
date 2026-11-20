package com.medora.paymentsms.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPolicyTest {
    @Test
    fun `accepted 202 receipt marks the event sent`() {
        assertEquals(SyncAction.MARK_SENT, SyncPolicy.decide(202, true))
        assertEquals(SyncAction.MARK_SENT, SyncPolicy.decide(200, true))
    }

    @Test
    fun `revoked or unauthorised token stops syncing`() {
        assertEquals(SyncAction.STOP_AUTH, SyncPolicy.decide(401, null))
        assertEquals(SyncAction.STOP_AUTH, SyncPolicy.decide(403, null))
    }

    @Test
    fun `validation rejection is permanent but offline errors retry`() {
        assertEquals(SyncAction.FAIL_PERMANENT, SyncPolicy.decide(422, false))
        assertEquals(SyncAction.FAIL_PERMANENT, SyncPolicy.decide(400, false))
        assertEquals(SyncAction.RETRY, SyncPolicy.decide(503, null))
        assertEquals(SyncAction.RETRY, SyncPolicy.decide(429, null))
        assertEquals(SyncAction.RETRY, SyncPolicy.decide(404, null))
        assertEquals(SyncAction.RETRY, SyncPolicy.decide(202, false))
    }
}
