package com.medora.paymentsms.util

/** Never persist a full phone number, even if the sender field contains one. */
object SenderMasker {
    private const val MAX_LENGTH = 64
    private val LONG_DIGITS = Regex("\\d{5,}")
    private val ARABIC_TO_ASCII = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
    )

    fun mask(sender: String): String {
        val trimmed = sender.trim()
        if (trimmed.isEmpty()) return "unknown"

        var normalized = trimmed
        ARABIC_TO_ASCII.forEach { (from, to) -> normalized = normalized.replace(from, to) }

        return LONG_DIGITS.replace(normalized) { match ->
            "****${match.value.takeLast(4)}"
        }.take(MAX_LENGTH)
    }
}
