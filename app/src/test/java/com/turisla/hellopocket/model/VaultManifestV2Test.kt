package com.turisla.hellopocket.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultManifestV2Test {
    @Test
    fun `legacy manifest without snapshot metadata remains readable`() {
        val legacyJson = """
            {
              "schemaVersion": 1,
              "vaultId": "vault-id",
              "generation": 3,
              "createdAt": 1,
              "attachments": []
            }
        """.trimIndent()

        val manifest = Json.decodeFromString<VaultManifestV2>(legacyJson)

        assertTrue(manifest.fileDigests.isEmpty())
        assertTrue(manifest.fileSizes.isEmpty())
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
