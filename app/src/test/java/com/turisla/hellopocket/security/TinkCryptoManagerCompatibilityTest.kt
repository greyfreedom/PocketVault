package com.turisla.hellopocket.security

import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.CleartextKeysetHandle
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Suppress("DEPRECATION")
class TinkCryptoManagerCompatibilityTest {

    @Test
    fun `legacy binary encrypted keyset can be decrypted and rewrapped`() {
        val manager = TinkCryptoManager()
        val original = manager.generateVaultKeyset()
        val password = "correct horse battery staple"
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val legacyWrappingKey = deriveLegacyWrappingKey(password, salt)
        val legacyBytes = ByteArrayOutputStream().use { output ->
            original.write(BinaryKeysetWriter.withOutputStream(output), TestAesGcmAead(legacyWrappingKey))
            output.toByteArray()
        }

        val decrypted = manager.decryptKeysetBytes(
            encryptedKeyset = legacyBytes,
            password = password,
            salt = salt,
            iterations = TinkCryptoManager.LEGACY_V2_KDF_ITERATIONS,
        )
        assertTrue(original.equalsKeyset(decrypted))

        val upgradedBytes = manager.encryptKeysetBytes(
            keysetHandle = decrypted,
            password = password,
            salt = salt,
            iterations = TinkCryptoManager.DEFAULT_KDF_ITERATIONS,
        )
        val roundTripped = manager.decryptKeysetBytes(
            encryptedKeyset = upgradedBytes,
            password = password,
            salt = salt,
            iterations = TinkCryptoManager.DEFAULT_KDF_ITERATIONS,
        )
        assertTrue(original.equalsKeyset(roundTripped))
    }

    @Test
    fun `legacy work factor is read only`() {
        val manager = TinkCryptoManager()

        assertTrue(
            manager.isKdfIterationCountSupportedForDecryption(
                TinkCryptoManager.LEGACY_V2_KDF_ITERATIONS
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            manager.encryptKeysetBytes(
                keysetHandle = manager.generateVaultKeyset(),
                password = "password",
                salt = ByteArray(16),
                iterations = TinkCryptoManager.LEGACY_V2_KDF_ITERATIONS,
            )
        }
    }

    @Test
    fun `historical biometric keyset bytes remain readable`() {
        val manager = TinkCryptoManager()
        val original = manager.generateVaultKeyset()
        val historicalBytes = ByteArrayOutputStream().use { output ->
            CleartextKeysetHandle.write(original, BinaryKeysetWriter.withOutputStream(output))
            output.toByteArray()
        }

        assertTrue(original.equalsKeyset(manager.readKeysetFromBytes(historicalBytes)))
    }

    private fun deriveLegacyWrappingKey(password: String, salt: ByteArray): SecretKeySpec {
        val passwordChars = password.toCharArray()
        val spec = PBEKeySpec(
            passwordChars,
            salt,
            TinkCryptoManager.LEGACY_V2_KDF_ITERATIONS,
            256,
        )
        return try {
            val encoded = SecretKeyFactory.getInstance(TinkCryptoManager.PBKDF2_ALGORITHM)
                .generateSecret(spec)
                .encoded
            try {
                SecretKeySpec(encoded, "AES")
            } finally {
                encoded.fill(0)
            }
        } finally {
            spec.clearPassword()
            passwordChars.fill('\u0000')
        }
    }

    /** 与 2.4.x 包装 Keyset 时使用的 AES-GCM 字节布局保持一致。 */
    private class TestAesGcmAead(private val key: SecretKeySpec) : Aead {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            if (associatedData != null && associatedData.isNotEmpty()) {
                cipher.updateAAD(associatedData)
            }
            return cipher.iv + cipher.doFinal(plaintext)
        }

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, ciphertext.copyOfRange(0, 12)),
            )
            if (associatedData != null && associatedData.isNotEmpty()) {
                cipher.updateAAD(associatedData)
            }
            return cipher.doFinal(ciphertext.copyOfRange(12, ciphertext.size))
        }
    }
}
