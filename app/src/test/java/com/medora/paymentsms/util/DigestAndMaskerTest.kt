package com.medora.paymentsms.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DigestAndMaskerTest {
    @Test
    fun `sha256 matches the standard vector and is lowercase hex`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Digest.sha256Hex("abc"),
        )
    }

    @Test
    fun `full phone numbers are reduced to last four digits`() {
        assertEquals("+****4321", SenderMasker.mask("+201987654321"))
        assertEquals("****7890", SenderMasker.mask("0101234567890"))
    }

    @Test
    fun `sender ids and short codes are left readable`() {
        assertEquals("VODAFONE CASH", SenderMasker.mask(" VODAFONE CASH "))
        assertEquals("8888", SenderMasker.mask("8888"))
        assertEquals("unknown", SenderMasker.mask(""))
    }

    @Test
    fun `arabic digit sender is normalized before masking`() {
        assertEquals("****1234", SenderMasker.mask("****١٢٣٤"))
    }
}
