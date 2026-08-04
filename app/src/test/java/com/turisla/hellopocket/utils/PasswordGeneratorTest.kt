package com.turisla.hellopocket.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun generatedPasswordHasRequestedLengthAndEveryEnabledCharacterType() {
        repeat(200) {
            val password = PasswordGenerator.generatePassword(
                length = 32,
                includeUppercase = true,
                includeLowercase = true,
                includeNumbers = true,
                includeSymbols = true
            )

            assertEquals(32, password.length)
            assertTrue(password.any(Char::isUpperCase))
            assertTrue(password.any(Char::isLowerCase))
            assertTrue(password.any(Char::isDigit))
            assertTrue(password.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun confusingCharactersAreExcludedWhenRequested() {
        repeat(200) {
            val password = PasswordGenerator.generatePassword(
                length = 50,
                includeSymbols = false,
                avoidConfusing = true
            )
            assertFalse(password.any { it in "O01l" })
        }
    }

    @Test
    fun invalidConfigurationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PasswordGenerator.generatePassword(length = 5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PasswordGenerator.generatePassword(
                includeUppercase = false,
                includeLowercase = false,
                includeNumbers = false,
                includeSymbols = false
            )
        }
    }
}
