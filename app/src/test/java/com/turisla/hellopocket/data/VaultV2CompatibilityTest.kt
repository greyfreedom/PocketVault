package com.turisla.hellopocket.data

import com.turisla.hellopocket.model.VaultConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class VaultV2CompatibilityTest {

    @Test
    fun `missing early v2 fields decode to historical defaults`() {
        val decoded = decodeCompatibleVaultConfig(
            """
                {
                  "version": 2,
                  "salt": "salt",
                  "encryptedKeyset": "keyset"
                }
            """.trimIndent()
        )

        assertEquals(100_000, decoded.kdfIterations)
        assertEquals(0, decoded.associatedDataVersion)
        assertEquals("", decoded.vaultId)
        assertTrue(decoded.requiresOneTimeUpgrade())
    }

    @Test
    fun `current config does not enter compatibility path`() {
        val decoded = CompatibleVaultConfig(
            version = 2,
            salt = "salt",
            encryptedKeyset = "keyset",
            integrityHash = "digest",
            kdfIterations = 600_000,
            vaultId = "vault-id",
            associatedDataVersion = 1,
        )

        assertFalse(decoded.requiresOneTimeUpgrade())
        assertEquals(
            VaultConfig(
                version = 2,
                salt = "salt",
                encryptedKeyset = "keyset",
                integrityHash = "digest",
                kdfIterations = 600_000,
                vaultId = "vault-id",
                associatedDataVersion = 1,
            ),
            decoded.toVaultConfig(),
        )
    }

    @Test
    fun `missing public digest alone does not relax encrypted metadata`() {
        val decoded = CompatibleVaultConfig(
            version = 2,
            salt = "salt",
            encryptedKeyset = "keyset",
            integrityHash = null,
            kdfIterations = 600_000,
            vaultId = "vault-id",
            associatedDataVersion = 1,
        )

        assertTrue(decoded.requiresOneTimeUpgrade())
        assertFalse(decoded.allowsLegacyEncryptedMetadata())
    }

    @Test
    fun `legacy config binding preserves historical default omission`() {
        val config = CompatibleVaultConfig(
            version = 2,
            salt = "salt",
            encryptedKeyset = "keyset",
            integrityHash = "public-digest",
            kdfIterations = 100_000,
            vaultId = "vault-id",
            associatedDataVersion = 1,
        )
        val historical = HistoricalVaultConfig(
            version = config.version,
            salt = config.salt,
            encryptedKeyset = config.encryptedKeyset,
            integrityHash = null,
            vaultId = config.vaultId,
            associatedDataVersion = config.associatedDataVersion,
        )
        val expected = MessageDigest.getInstance("SHA-256").digest(
            Json.encodeToString(historical).toByteArray(Charsets.UTF_8)
        )

        assertArrayEquals(expected, calculateCompatibleVaultConfigBindingBytes(config))
    }

    @Serializable
    private data class HistoricalVaultConfig(
        val version: Int,
        val salt: String,
        val encryptedKeyset: String,
        val integrityHash: String? = null,
        val passwordHint: String? = null,
        val kdfAlgorithm: String = "PBKDF2WithHmacSHA256",
        val kdfIterations: Int = 100_000,
        val vaultId: String = "",
        val associatedDataVersion: Int = 0,
    )
}
