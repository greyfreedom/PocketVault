package com.turisla.hellopocket.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class OtpAuthParserTest {

    @Test
    fun parsesValidTotpUriAndNormalizesAlgorithm() {
        val parsed = OtpAuthParser.parse(
            "otpauth://totp/Example%20Co:user%40example.com" +
                "?secret=JBSWY3DPEHPK3PXP&issuer=Example%20Co&algorithm=sha256&digits=8&period=30"
        )

        assertNotNull(parsed)
        assertEquals("Example Co", parsed?.issuer)
        assertEquals("user@example.com", parsed?.account)
        assertEquals("SHA256", parsed?.algorithm)
        assertEquals(8, parsed?.digits)
        assertEquals(30, parsed?.period)
    }

    @Test
    fun rejectsUnsafeOrUnsupportedParameters() {
        val base = "otpauth://totp/Example:user?secret=JBSWY3DPEHPK3PXP&issuer=Example"

        assertNull(OtpAuthParser.parse("$base&period=0"))
        assertNull(OtpAuthParser.parse("$base&period=-30"))
        assertNull(OtpAuthParser.parse("$base&digits=7"))
        assertNull(OtpAuthParser.parse("$base&algorithm=MD5"))
        assertNull(OtpAuthParser.parse("$base&secret=AAAAAAAA"))
    }

    @Test
    fun rejectsMalformedAndOversizedInput() {
        assertNull(OtpAuthParser.parse("https://example.com/not-otp"))
        assertNull(OtpAuthParser.parse("otpauth://hotp/Example:user?secret=JBSWY3DPEHPK3PXP"))
        assertNull(OtpAuthParser.parse("x".repeat(4097)))
    }
}
