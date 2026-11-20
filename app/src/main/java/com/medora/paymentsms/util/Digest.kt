package com.medora.paymentsms.util

import java.security.MessageDigest

/** Server-compatible content digest: lowercase SHA-256 hex (64 chars). */
object Digest {
    fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
    }
}
