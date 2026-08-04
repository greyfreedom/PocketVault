package com.turisla.hellopocket.data

import com.turisla.hellopocket.model.VaultConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VaultConfigBindingTest {
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
}
