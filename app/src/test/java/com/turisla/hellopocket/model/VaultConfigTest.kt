package com.turisla.hellopocket.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultConfigTest {
    @Test
    fun `current v2 config emitted before hardening remains readable`() {
        // 旧编码器会省略等于默认值的 kdfAlgorithm；这仍属于当前安全格式。
        val currentV2Json = """
            {
              "version": 2,
              "salt": "salt",
              "encryptedKeyset": "keyset",
              "integrityHash": "digest",
              "kdfIterations": 600000,
              "vaultId": "vault-id",
              "associatedDataVersion": 1
            }
        """.trimIndent()

        val config = Json.decodeFromString<VaultConfig>(currentV2Json)

        assertEquals("PBKDF2WithHmacSHA256", config.kdfAlgorithm)
        assertEquals(600_000, config.kdfIterations)
        assertEquals("vault-id", config.vaultId)
        assertEquals(1, config.associatedDataVersion)
    }

    @Test
    fun `legacy config without current security fields is rejected`() {
        val legacyJson = """
            {
              "version": 2,
              "salt": "salt",
              "encryptedKeyset": "keyset",
              "integrityHash": "digest"
            }
        """.trimIndent()

        assertThrows(SerializationException::class.java) {
            Json.decodeFromString<VaultConfig>(legacyJson)
        }
    }

    @Test
    fun `current config round trips`() {
        val config = VaultConfig(
            version = 2,
            salt = "salt",
            encryptedKeyset = "keyset",
            integrityHash = "digest",
            kdfIterations = 600_000,
            vaultId = "vault-id",
            associatedDataVersion = 1,
        )

        val decoded = Json.decodeFromString<VaultConfig>(Json.encodeToString(config))

        assertEquals(config, decoded)
    }
}
