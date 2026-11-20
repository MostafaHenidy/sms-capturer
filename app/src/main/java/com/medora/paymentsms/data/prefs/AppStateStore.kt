package com.medora.paymentsms.data.prefs

import android.content.Context

/** Non-sensitive UI/operational state. */
class AppStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("medora_app_state", Context.MODE_PRIVATE)

    var listeningEnabled: Boolean
        get() = prefs.getBoolean(KEY_LISTENING, false)
        set(value) = prefs.edit().putBoolean(KEY_LISTENING, value).apply()

    var authRevoked: Boolean
        get() = prefs.getBoolean(KEY_REVOKED, false)
        set(value) = prefs.edit().putBoolean(KEY_REVOKED, value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    var lastHeartbeatAt: Long
        get() = prefs.getLong(KEY_LAST_HEARTBEAT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_HEARTBEAT, value).apply()

    var lastPurgeAt: Long
        get() = prefs.getLong(KEY_LAST_PURGE, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_PURGE, value).apply()

    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_ERROR, value).apply()

    fun reset() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_LISTENING = "listening_enabled"
        const val KEY_REVOKED = "auth_revoked"
        const val KEY_LAST_SYNC = "last_sync_at"
        const val KEY_LAST_HEARTBEAT = "last_heartbeat_at"
        const val KEY_LAST_PURGE = "last_purge_at"
        const val KEY_LAST_ERROR = "last_error"
    }
}
