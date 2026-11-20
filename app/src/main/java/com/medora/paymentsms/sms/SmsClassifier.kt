package com.medora.paymentsms.sms

import com.medora.paymentsms.util.Digest
import com.medora.paymentsms.util.SenderMasker
import java.util.Locale
import java.util.UUID
import kotlin.math.round

data class RawSms(val sender: String, val body: String, val receivedAtMs: Long)

/** Only redacted metadata. The raw body is never held in a persisted object. */
data class PaymentEventDraft(
    val eventUuid: String,
    val sourceType: String = "sms",
    val sourceId: String,
    val amountMinor: Long,
    val providerReference: String?,
    val senderHint: String,
    val contentDigest: String,
    val occurredAtMs: Long,
)

sealed interface Classification {
    data class Relevant(val draft: PaymentEventDraft) : Classification
    data class Ignored(val reason: Reason) : Classification

    enum class Reason {
        EMPTY, UNKNOWN_SENDER, OTP, OTHER_CURRENCY, NO_CURRENCY,
        OUTGOING, BALANCE, AMBIGUOUS, NO_AMOUNT, MALFORMED_AMOUNT,
    }
}

/**
 * Conservative, source-specific local filter. It mirrors the server-side
 * Vodafone Cash fixture parser: unfamiliar templates are dropped, never
 * guessed. It produces no payment confirmation of any kind.
 */
class SmsClassifier(
    private val allowlist: SenderAllowlist = SenderAllowlist(),
) {
    fun classify(sms: RawSms): Classification {
        val sourceId = allowlist.match(sms.sender)
            ?: return Classification.Ignored(Classification.Reason.UNKNOWN_SENDER)

        val text = normalize(sms.body)
        if (text.isEmpty()) return Classification.Ignored(Classification.Reason.EMPTY)
        if (OTP_PATTERN.containsMatchIn(text)) return Classification.Ignored(Classification.Reason.OTP)
        if (OTHER_CURRENCY_PATTERN.containsMatchIn(text)) {
            return Classification.Ignored(Classification.Reason.OTHER_CURRENCY)
        }
        if (!EGP_PATTERN.containsMatchIn(text)) return Classification.Ignored(Classification.Reason.NO_CURRENCY)

        val incoming = INCOMING_PATTERN.containsMatchIn(text)
        val outgoing = OUTGOING_PATTERN.containsMatchIn(text)
        if (incoming && outgoing) return Classification.Ignored(Classification.Reason.AMBIGUOUS)
        if (outgoing) return Classification.Ignored(Classification.Reason.OUTGOING)
        if (!incoming) return Classification.Ignored(Classification.Reason.BALANCE)

        val amountMinor = extractAmountMinor(text)
            ?: return Classification.Ignored(Classification.Reason.NO_AMOUNT)

        // Second-level precision lets duplicate broadcasts of the same message
        // collapse to one digest, while genuine repeats remain distinct.
        val digestInput = "$text\n${sms.receivedAtMs / 1000}"
        val digest = Digest.sha256Hex(digestInput)

        return Classification.Relevant(
            PaymentEventDraft(
                eventUuid = UUID.randomUUID().toString(), // v4
                sourceId = sourceId,
                amountMinor = amountMinor,
                providerReference = extractReference(text),
                senderHint = SenderMasker.mask(sms.sender),
                contentDigest = digest,
                occurredAtMs = sms.receivedAtMs,
            ),
        )
    }

    private fun normalize(body: String): String {
        var text = TAG_PATTERN.replace(body, "")
        ARABIC_TO_ASCII.forEach { (from, to) -> text = text.replace(from, to) }
        text = text.replace('٫', '.').replace('٬', ',')
        return WHITESPACE_PATTERN.replace(text, " ").trim().lowercase(Locale.ROOT)
    }

    private fun extractAmountMinor(text: String): Long? {
        val match = AMOUNT_PATTERN.find(text) ?: return null
        val major = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (major <= 0.0 || major > MAX_MAJOR) return null
        return round(major * 100).toLong()
    }

    private fun extractReference(text: String): String? {
        REFERENCE_PATTERN.find(text)?.let { match ->
            return (match.groupValues[1].ifEmpty { match.groupValues[2] }).uppercase(Locale.ROOT)
        }
        return null
    }

    private companion object {
        const val MAX_MAJOR = 1_000_000.0
        val TAG_PATTERN = Regex("<[^>]*>")
        val WHITESPACE_PATTERN = Regex("\\s+")
        val ARABIC_TO_ASCII = mapOf(
            '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
            '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
            '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
            '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        )
        val OTP_PATTERN = Regex(
            "otp|one[- ]time (?:code|password)|verification code|كود التحقق|رمز التحقق",
        )
        val OTHER_CURRENCY_PATTERN = Regex("\\b(usd|sar|eur|aed)\\b|دولار|ريال|يورو|درهم")
        val EGP_PATTERN = Regex("\\begp\\b|جنيه|ج\\.م")
        val INCOMING_PATTERN = Regex(
            "received|credited|deposit|cash[ -]?in|تم الاستلام|تم إيداع|إيداع|استلام|تمت إضافة",
        )
        val OUTGOING_PATTERN = Regex(
            "you sent|transferred|debited|cash[ -]?out|تم الإرسال|تم التحويل|تحويل",
        )
        val AMOUNT_PATTERN = Regex("([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:egp|جنيه|ج\\.م)")
        val REFERENCE_PATTERN = Regex(
            "\\b(vf\\d{6,}|bal\\d{4,})\\b|(?:reference|ref|رقم العملية)[:\\s#]*([a-z0-9]{6,})",
        )
    }
}
