package com.medora.paymentsms.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The revocable device token lives only in Keystore-backed encrypted prefs.
 * It is never logged, never put in a notification, and never backed up.
 */
class TokenStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "medora_device_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(token: String, deviceId: Long, deviceName: String, pairedAt: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_DEVICE_ID, deviceId)
            .putString(KEY_DEVICE_NAME, deviceName)
            .putString(KEY_PAIRED_AT, pairedAt)
            .apply()
    }

    fun token(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() }

    fun bearer(): String? = token()?.let { "Bearer $it" }

    fun deviceId(): Long = prefs.getLong(KEY_DEVICE_ID, -1L)

    fun deviceName(): String? = prefs.getString(KEY_DEVICE_NAME, null)

    fun pairedAt(): String? = prefs.getString(KEY_PAIRED_AT, null)

    fun isPaired(): Boolean = token() != null

    /** Disconnect/revocation: clear credentials but keep local review evidence. */
    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_DEVICE_NAME)
            .remove(KEY_PAIRED_AT)
            .apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_PAIRED_AT = "paired_at"
    }
}
