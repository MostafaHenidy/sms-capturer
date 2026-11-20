package com.medora.paymentsms.sync

/** Pure decision table so retry/revoke behaviour is unit-testable without Android. */
enum class SyncAction {
    MARK_SENT, RETRY, FAIL_PERMANENT, STOP_AUTH,
}

object SyncPolicy {
    fun decide(httpCode: Int, envelopeSuccess: Boolean?): SyncAction = when {
        httpCode in 200..299 && envelopeSuccess == true -> SyncAction.MARK_SENT
        httpCode == 401 || httpCode == 403 -> SyncAction.STOP_AUTH
        httpCode == 400 || httpCode == 422 -> SyncAction.FAIL_PERMANENT
        else -> SyncAction.RETRY // network, 404 (flag off), 429, 5xx: never clear credentials
    }
}
