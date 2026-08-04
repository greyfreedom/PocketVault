package com.turisla.hellopocket.security

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class TotpGeneratorTest {

    @Test
    fun matchesRfc6238VectorsAtTimestamp59Seconds() {
        assertVector("12345678901234567890", HmacAlgorithm.SHA1, "94287082")
        assertVector("12345678901234567890123456789012", HmacAlgorithm.SHA256, "46119246")
        assertVector(
            "1234567890123456789012345678901234567890123456789012345678901234",
            HmacAlgorithm.SHA512,
            "90693936"
        )
    }

    private fun assertVector(secret: String, algorithm: HmacAlgorithm, expected: String) {
        val generator = TimeBasedOneTimePasswordGenerator(
            secret.toByteArray(Charsets.US_ASCII),
            TimeBasedOneTimePasswordConfig(
                codeDigits = 8,
                hmacAlgorithm = algorithm,
                timeStep = 30,
                timeStepUnit = TimeUnit.SECONDS
            )
        )

        assertEquals(expected, generator.generate(59_000L))
    }
}
