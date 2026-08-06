package com.turisla.hellopocket.data

import com.turisla.hellopocket.model.VaultConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.security.MessageDigest

class VaultConfigBindingTest {
    @Serializable
    private data class PreviousCurrentVaultConfig(
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

    private val config = VaultConfig(
        version = 2,
        salt = "salt",
        encryptedKeyset = "keyset",
        integrityHash = "public-hash",
        passwordHint = "hint",
        kdfIterations = 600_000,
        vaultId = "vault-id",
        associatedDataVersion = 1,
    )

    @Test
    fun `public integrity hash is excluded from authenticated config binding`() {
        val first = calculateVaultConfigBindingBytes(config)
        val second = calculateVaultConfigBindingBytes(config.copy(integrityHash = "recomputed"))

        assertArrayEquals(first, second)
    }

    @Test
    fun `key wrapping changes alter authenticated config binding`() {
        val original = calculateVaultConfigBindingBytes(config)
        val changed = calculateVaultConfigBindingBytes(
            config.copy(salt = "new-salt", encryptedKeyset = "new-keyset")
        )

        assertFalse(original.contentEquals(changed))
    }

    @Test
    fun `current v2 binding remains compatible with the previous encoder defaults`() {
        val previousConfig = PreviousCurrentVaultConfig(
            version = config.version,
            salt = config.salt,
            encryptedKeyset = config.encryptedKeyset,
            integrityHash = config.integrityHash,
            passwordHint = config.passwordHint,
            kdfAlgorithm = config.kdfAlgorithm,
            kdfIterations = config.kdfIterations,
            vaultId = config.vaultId,
            associatedDataVersion = config.associatedDataVersion,
        )
        val previousCanonicalJson = Json.encodeToString(previousConfig.copy(integrityHash = null))
        val previousBinding = MessageDigest.getInstance("SHA-256")
            .digest(previousCanonicalJson.toByteArray(Charsets.UTF_8))

        assertArrayEquals(previousBinding, calculateVaultConfigBindingBytes(config))
    }
}
