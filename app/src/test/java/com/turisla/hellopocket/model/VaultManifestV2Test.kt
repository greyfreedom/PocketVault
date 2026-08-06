package com.turisla.hellopocket.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultManifestV2Test {
    @Test
    fun `manifest without authenticated snapshot metadata is rejected`() {
        val legacyJson = """
            {
              "schemaVersion": 1,
              "vaultId": "vault-id",
              "generation": 3,
              "createdAt": 1,
              "attachments": []
            }
        """.trimIndent()

        assertThrows(SerializationException::class.java) {
            Json.decodeFromString<VaultManifestV2>(legacyJson)
        }
    }

    @Test
    fun `authenticated snapshot metadata round trips`() {
        val manifest = VaultManifestV2(
            vaultId = "vault-id",
            generation = 4,
            createdAt = 1L,
            fileDigests = mapOf("passwords.dat" to "digest"),
            fileSizes = mapOf("passwords.dat" to 42L),
            configBinding = "config-digest",
        )

        val decoded = Json.decodeFromString<VaultManifestV2>(Json.encodeToString(manifest))

        assertEquals(manifest, decoded)
    }
}
