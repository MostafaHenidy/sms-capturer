package com.medora.paymentsms.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsClassifierTest {
    private val classifier = SmsClassifier()

    @Test
    fun `incoming english fixture produces exactly one relevant event`() {
        val result = classifier.classify(sms("vodafone_cash", "vodafone_incoming_en.txt"))

        val draft = (result as Classification.Relevant).draft
        assertEquals("sms", draft.sourceType)
        assertEquals("vodafone_cash", draft.sourceId)
        assertEquals(50_000L, draft.amountMinor)
        assertEquals("VF1234567890", draft.providerReference)
        assertEquals("Vodafone Cash ****1234", draft.senderHint)
        assertTrue(draft.contentDigest.matches(Regex("[a-f0-9]{64}")))
    }

    @Test
    fun `arabic fixtures including arabic digits are classified identically`() {
        for (name in listOf("vodafone_incoming_ar.txt", "vodafone_incoming_ar_digits.txt")) {
            val draft = (classifier.classify(sms("vodafone_cash", name)) as Classification.Relevant).draft
            assertEquals(name, 50_000L, draft.amountMinor)
            assertEquals(name, "VF1234567890", draft.providerReference)
            assertEquals(name, "Vodafone Cash ****1234", draft.senderHint)
        }
    }

    @Test
    fun `outgoing, balance and otp messages are dropped`() {
        assertEquals(
            Classification.Reason.OUTGOING,
            ignoredReason("vodafone_cash", "vodafone_outgoing_en.txt"),
        )
        assertEquals(
            Classification.Reason.BALANCE,
            ignoredReason("vodafone_cash", "vodafone_balance_ar.txt"),
        )
        assertEquals(
            Classification.Reason.OTP,
            ignoredReason("vodafone_cash", "vodafone_otp_en.txt"),
        )
    }

    @Test
    fun `wrong currency malformed amount and provider redesign are dropped`() {
        assertEquals(
            Classification.Reason.OTHER_CURRENCY,
            ignoredReason("vodafone_cash", "vodafone_wrong_currency.txt"),
        )
        assertEquals(
            Classification.Reason.NO_AMOUNT,
            ignoredReason("vodafone_cash", "vodafone_malformed_amount.txt"),
        )
        assertEquals(
            Classification.Reason.NO_CURRENCY,
            ignoredReason("vodafone_cash", "vodafone_redesign.txt"),
        )
    }

    @Test
    fun `unknown sender and unrelated personal message produce no event`() {
        assertEquals(
            Classification.Reason.UNKNOWN_SENDER,
            ignoredReason("+201000000111", "unrelated_landlord.txt"),
        )
        assertEquals(
            Classification.Reason.UNKNOWN_SENDER,
            ignoredReason("UNKNOWN_BANK", "vodafone_incoming_en.txt"),
        )
    }

    @Test
    fun `injected markup is stripped and extracted fields stay safe`() {
        val draft = (classifier.classify(sms("vodafone_cash", "vodafone_script_injection.txt"))
            as Classification.Relevant).draft

        for (value in listOfNotNull(draft.providerReference, draft.senderHint)) {
            assertFalse(value.contains("<"))
            assertFalse(value.contains(">"))
        }
    }

    @Test
    fun `full phone sender is masked before a draft is created`() {
        val draft = (classifier.classify(
            RawSms("Vodafone Cash +201987654321", fixture("vodafone_incoming_en.txt"), RECEIVED_AT),
        ) as Classification.Relevant).draft

        assertEquals("Vodafone Cash +****4321", draft.senderHint)
        assertFalse(draft.senderHint.contains("1987654321"))
    }

    @Test
    fun `duplicate broadcast yields the same digest but a distinct uuid`() {
        val raw = sms("vodafone_cash", "vodafone_incoming_en.txt")
        val first = (classifier.classify(raw) as Classification.Relevant).draft
        val second = (classifier.classify(raw) as Classification.Relevant).draft

        assertEquals(first.contentDigest, second.contentDigest)
        assertFalse(first.eventUuid == second.eventUuid)
        assertNotNull(first.eventUuid)
    }

    @Test
    fun `a genuine repeat at a different time has a different digest`() {
        val body = fixture("vodafone_incoming_en.txt")
        val first = (classifier.classify(RawSms("vodafone_cash", body, 1_000_000L)) as Classification.Relevant).draft
        val second = (classifier.classify(RawSms("vodafone_cash", body, 1_050_000L)) as Classification.Relevant).draft

        assertFalse(first.contentDigest == second.contentDigest)
    }

    @Test
    fun `empty body is dropped without a digest`() {
        assertEquals(
            Classification.Reason.EMPTY,
            (classifier.classify(RawSms("vodafone_cash", "", RECEIVED_AT)) as Classification.Ignored).reason,
        )
    }

    private fun ignoredReason(sender: String, fixture: String): Classification.Reason =
        (classifier.classify(sms(sender, fixture)) as Classification.Ignored).reason

    private fun sms(sender: String, fixture: String) =
        RawSms(if (sender == "vodafone_cash") "Vodafone Cash ****1234" else sender, fixture(fixture), RECEIVED_AT)

    private fun fixture(name: String): String = requireNotNull(
        javaClass.getResourceAsStream("/payment_sms/$name"),
    ) { "Missing fixture $name" }.bufferedReader().use { it.readText() }

    private companion object {
        const val RECEIVED_AT = 1_764_500_000_000L
    }
}
