package me.kavishdevar.librepods.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveLogRedactorTest {
    @Test
    fun `redacts bluetooth addresses and serials`() {
        val redacted = SensitiveLogRedactor.redact(
            "device=AA:BB:CC:DD:EE:FF serialNumber=ABC123456"
        )

        assertFalse(redacted.contains("AA:BB:CC:DD:EE:FF"))
        assertFalse(redacted.contains("ABC123456"))
        assertTrue(redacted.contains("<redacted-bt-address>"))
        assertTrue(redacted.contains("serialNumber=<redacted>"))
    }

    @Test
    fun `redacts proximity keys and raw hexadecimal payloads`() {
        val redacted = SensitiveLogRedactor.redact(
            "ENC_KEY=ABCDEFGHIJKLMNOPQRSTUVWXYZ 01 02 03 04 05 06 07 08"
        )

        assertFalse(redacted.contains("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
        assertFalse(redacted.contains("01 02 03 04 05 06 07 08"))
        assertTrue(redacted.contains("ENC_KEY=<redacted>"))
        assertTrue(redacted.contains("<redacted-binary-payload>"))
    }

    @Test
    fun `preserves ordinary status messages`() {
        val line = "AirPodsService: AAP socket connected"
        assertTrue(SensitiveLogRedactor.redact(line) == line)
    }
}
