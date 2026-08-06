package com.turisla.hellopocket.data

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import com.turisla.hellopocket.model.AttachmentManifestEntry
import com.turisla.hellopocket.model.Categories
import com.turisla.hellopocket.model.Category
import com.turisla.hellopocket.model.PasswordEntries
import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.model.TotpEntries
import com.turisla.hellopocket.model.TotpEntry
import com.turisla.hellopocket.model.VaultConfig
import com.turisla.hellopocket.model.VaultItemType
import com.turisla.hellopocket.model.VaultLoadResult
import com.turisla.hellopocket.security.TinkCryptoManager
import com.turisla.hellopocket.security.VaultSessionGuard
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
@Suppress("DEPRECATION")
class PasswordRepositoryLegacyV2MigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        clearTestFiles()
    }

    @After
    fun tearDown() {
        clearTestFiles()
    }

    @Test
    fun `early v2 unlock and backup restore both normalize before activation`() = runBlocking {
        val password = "legacy-master-password"
        val manager = TinkCryptoManager()
        val guard = VaultSessionGuard().apply { enterForeground() }
        val fixture = createEarlyV2Vault(manager, password)
        val repository = PasswordRepository(context, manager, guard)

        assertTrue(repository.loadAndDecryptData(password) is VaultLoadResult.Success)
        assertEquals(listOf("entry-1"), repository.passwordEntries.value.map(PasswordEntry::getId))
        assertArrayEquals(
            fixture.attachmentPlaintext,
            repository.getAttachmentFile(fixture.attachmentId)?.readBytes(),
        )
        assertArrayEquals(
            fixture.thumbnailPlaintext,
            repository.getThumbnailFile(fixture.attachmentId)?.readBytes(),
        )

        val totpRepository = TotpRepository(context, manager, repository, guard)
        assertTrue(
            totpRepository.initialize(requireNotNull(repository.getVaultEncryptionContext()))
                is VaultLoadResult.Success
        )
        assertEquals(listOf("totp-1"), totpRepository.totpEntries.value.map(TotpEntry::getId))

        val firstUpgradedConfig = readCurrentConfig()
        assertEquals(TinkCryptoManager.DEFAULT_KDF_ITERATIONS, firstUpgradedConfig.kdfIterations)
        assertEquals(1, firstUpgradedConfig.associatedDataVersion)
        assertTrue(firstUpgradedConfig.integrityHash?.isNotBlank() == true)
        assertTrue(firstUpgradedConfig.vaultId.isNotBlank())
        assertNotEquals(fixture.legacyVaultId, firstUpgradedConfig.vaultId)

        val legacyBackup = repository.getBackupHistory().single()
        assertTrue(legacyBackup.isFile && legacyBackup.length() > 0L)

        // 自动备份保存的是切换前的旧 V2；重新导入时也必须先标准化，不能原样激活。
        assertTrue(repository.restoreFromBackup(legacyBackup, password))
        assertTrue(repository.loadAndDecryptData(password) is VaultLoadResult.Success)
        val restoredConfig = readCurrentConfig()
        assertEquals(TinkCryptoManager.DEFAULT_KDF_ITERATIONS, restoredConfig.kdfIterations)
        assertEquals(1, restoredConfig.associatedDataVersion)
        assertTrue(restoredConfig.integrityHash?.isNotBlank() == true)
        assertTrue(restoredConfig.vaultId.isNotBlank())
        assertNotEquals(firstUpgradedConfig.vaultId, restoredConfig.vaultId)
        assertArrayEquals(
            fixture.attachmentPlaintext,
            repository.getAttachmentFile(fixture.attachmentId)?.readBytes(),
        )
        totpRepository.lock()
        assertTrue(
            totpRepository.initialize(requireNotNull(repository.getVaultEncryptionContext()))
                is VaultLoadResult.Success
        )
        assertEquals(listOf("totp-1"), totpRepository.totpEntries.value.map(TotpEntry::getId))
        assertFalse(
            context.filesDir.listFiles().orEmpty().any { file ->
                file.name.startsWith(".${PasswordRepository.VAULT_DIRECTORY_NAME}.migration-") ||
                    file.name.startsWith(".${PasswordRepository.VAULT_DIRECTORY_NAME}.import-")
            }
        )
    }

    @Test
    fun `v2 4 authenticated vault with omitted historical kdf is upgraded`() = runBlocking {
        val password = "legacy-master-password"
        val manager = TinkCryptoManager()
        val guard = VaultSessionGuard().apply { enterForeground() }
        val fixture = createEarlyV2Vault(
            manager = manager,
            password = password,
            includeV24AuthenticatedMetadata = true,
        )
        val legacyConfigFile = File(
            File(context.filesDir, PasswordRepository.VAULT_DIRECTORY_NAME),
            PasswordRepository.VAULT_CONFIG_FILE_NAME,
        )
        // 2.4.0 的序列化器会省略默认的 100k 字段，这正是 2.5.0 回归的主要触发形态。
        assertFalse(legacyConfigFile.readText().contains("\"kdfIterations\""))

        val repository = PasswordRepository(context, manager, guard)

        assertTrue(repository.loadAndDecryptData(password) is VaultLoadResult.Success)
        assertEquals(listOf("entry-1"), repository.passwordEntries.value.map(PasswordEntry::getId))
        assertArrayEquals(
            fixture.attachmentPlaintext,
            repository.getAttachmentFile(fixture.attachmentId)?.readBytes(),
        )
        val upgradedConfig = readCurrentConfig()
        assertEquals(TinkCryptoManager.DEFAULT_KDF_ITERATIONS, upgradedConfig.kdfIterations)
        assertEquals(1, upgradedConfig.associatedDataVersion)
        assertTrue(upgradedConfig.integrityHash?.isNotBlank() == true)
        assertNotEquals(fixture.legacyVaultId, upgradedConfig.vaultId)
        assertTrue(repository.getBackupHistory().single().isFile)
    }

    @Test
    fun `failed legacy attachment authentication leaves original vault unchanged`() = runBlocking {
        val password = "legacy-master-password"
        val manager = TinkCryptoManager()
        val guard = VaultSessionGuard().apply { enterForeground() }
        val fixture = createEarlyV2Vault(manager, password)
        val vaultDirectory = File(context.filesDir, PasswordRepository.VAULT_DIRECTORY_NAME)
        File(
            File(vaultDirectory, PasswordRepository.ATTACHMENTS_DIR),
            "${fixture.attachmentId}.dat",
        ).writeBytes("not-authenticated-ciphertext".toByteArray())
        val before = snapshotFiles(vaultDirectory)
        val repository = PasswordRepository(context, manager, guard)

        assertTrue(repository.loadAndDecryptData(password) is VaultLoadResult.UpgradeFailed)

        val after = snapshotFiles(vaultDirectory)
        assertEquals(before.keys, after.keys)
        before.forEach { (path, bytes) -> assertArrayEquals(path, bytes, after.getValue(path)) }
        assertTrue(repository.getBackupHistory().isEmpty())
        assertFalse(
            context.filesDir.listFiles().orEmpty().any { file ->
                file.name.startsWith(".${PasswordRepository.VAULT_DIRECTORY_NAME}.migration-")
            }
        )
    }

    private fun createEarlyV2Vault(
        manager: TinkCryptoManager,
        password: String,
        includeV24AuthenticatedMetadata: Boolean = false,
    ): LegacyFixture {
        val vaultDirectory = File(context.filesDir, PasswordRepository.VAULT_DIRECTORY_NAME)
        val attachmentsDirectory = File(vaultDirectory, PasswordRepository.ATTACHMENTS_DIR)
        assertTrue(attachmentsDirectory.mkdirs())

        val keyset = manager.generateVaultKeyset()
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val encryptedKeyset = createHistoricalEncryptedKeyset(keyset, password, salt)
        val legacyVaultId = if (includeV24AuthenticatedMetadata) "legacy-v24-vault" else ""
        val associatedDataVersion = if (includeV24AuthenticatedMetadata) 1 else 0
        val configWithoutHash = CompatibleVaultConfig(
            version = PasswordRepository.VAULT_VERSION_V2,
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            encryptedKeyset = Base64.encodeToString(encryptedKeyset, Base64.NO_WRAP),
            passwordHint = "legacy hint",
            vaultId = legacyVaultId,
            associatedDataVersion = associatedDataVersion,
        )

        val attachmentId = "attachment-1"
        val attachmentPlaintext = "legacy attachment".toByteArray()
        val thumbnailPlaintext = "legacy thumbnail".toByteArray()
        val attachment = AttachmentManifestEntry(
            id = attachmentId,
            encryptedFileName = "$attachmentId.dat",
            originalFileName = "legacy.txt",
            mimeType = "text/plain",
            size = attachmentPlaintext.size.toLong(),
            createdAt = 1L,
        )
        val category = Category.newBuilder()
            .setId("category-1")
            .setName("Legacy")
            .setColor("#336699")
            .setCreatedAt(1L)
            .build()
        val entry = PasswordEntry.newBuilder()
            .setId("entry-1")
            .setTitle("Legacy entry")
            .setUsername("legacy@example.com")
            .setPassword("secret")
            .setType(VaultItemType.PASSWORD)
            .addCategoryIds(category.id)
            .addAttachmentIds(attachmentId)
            .setCreatedAt(1L)
            .setUpdatedAt(1L)
            .build()
        val totp = TotpEntry.newBuilder()
            .setId("totp-1")
            .setIssuer("Legacy")
            .setAccount("legacy@example.com")
            .setSecret(ByteString.copyFromUtf8("JBSWY3DPEHPK3PXP"))
            .setAlgorithm("SHA1")
            .setDigits(6)
            .setPeriod(30)
            .setCreatedAt(1L)
            .setUpdatedAt(1L)
            .build()
        val streamingAead = manager.getStreamingAead(keyset)
        writeLegacyMessage(
            File(vaultDirectory, PasswordRepository.PASSWORDS_DATA_FILE),
            PasswordEntries.newBuilder().addEntries(entry).build(),
            streamingAead,
            associatedData(configWithoutHash, PasswordRepository.PASSWORDS_DATA_FILE),
        )
        writeLegacyMessage(
            File(vaultDirectory, PasswordRepository.CATEGORIES_DATA_FILE),
            Categories.newBuilder().addCategories(category).build(),
            streamingAead,
            associatedData(configWithoutHash, PasswordRepository.CATEGORIES_DATA_FILE),
        )
        writeLegacyMessage(
            File(vaultDirectory, PasswordRepository.TOTP_DATA_FILE),
            TotpEntries.newBuilder().addEntries(totp).build(),
            streamingAead,
            associatedData(configWithoutHash, PasswordRepository.TOTP_DATA_FILE),
        )
        writeLegacyBytes(
            File(attachmentsDirectory, attachment.encryptedFileName),
            attachmentPlaintext,
            streamingAead,
            associatedData(configWithoutHash, "attachment:${attachment.id}"),
        )
        writeLegacyBytes(
            File(attachmentsDirectory, "${attachment.id}_thumb.dat"),
            thumbnailPlaintext,
            streamingAead,
            associatedData(configWithoutHash, "attachment-thumbnail:${attachment.id}"),
        )
        val snapshotFiles = listOf(
            PasswordRepository.PASSWORDS_DATA_FILE,
            PasswordRepository.CATEGORIES_DATA_FILE,
            PasswordRepository.TOTP_DATA_FILE,
        ).associateWith { File(vaultDirectory, it) }
        writeLegacyBytes(
            File(vaultDirectory, PasswordRepository.MANIFEST_DATA_FILE),
            Json.encodeToString(
                CompatibleVaultManifestV2(
                    schemaVersion = if (includeV24AuthenticatedMetadata) 1 else 0,
                    vaultId = legacyVaultId,
                    generation = if (includeV24AuthenticatedMetadata) 7L else 0L,
                    createdAt = 1L,
                    attachments = listOf(attachment),
                    fileDigests = if (includeV24AuthenticatedMetadata) {
                        snapshotFiles.mapValues { (_, file) -> calculateFileDigest(file) }
                    } else {
                        emptyMap()
                    },
                    fileSizes = if (includeV24AuthenticatedMetadata) {
                        snapshotFiles.mapValues { (_, file) -> file.length() }
                    } else {
                        emptyMap()
                    },
                    configBinding = if (includeV24AuthenticatedMetadata) {
                        Base64.encodeToString(
                            calculateCompatibleVaultConfigBindingBytes(configWithoutHash),
                            Base64.NO_WRAP,
                        )
                    } else {
                        ""
                    },
                )
            ).toByteArray(Charsets.UTF_8),
            streamingAead,
            associatedData(configWithoutHash, PasswordRepository.MANIFEST_DATA_FILE),
        )
        val config = if (includeV24AuthenticatedMetadata) {
            configWithoutHash.copy(integrityHash = calculateCurrentVaultHash(vaultDirectory))
        } else {
            configWithoutHash
        }
        File(vaultDirectory, PasswordRepository.VAULT_CONFIG_FILE_NAME)
            .writeText(Json.encodeToString(config))
        return LegacyFixture(
            attachmentId = attachmentId,
            attachmentPlaintext = attachmentPlaintext,
            thumbnailPlaintext = thumbnailPlaintext,
            legacyVaultId = config.vaultId,
        )
    }

    private fun associatedData(config: CompatibleVaultConfig, logicalName: String): ByteArray {
        return if (config.associatedDataVersion >= 1) {
            "hellopocket|${config.vaultId}|$logicalName".toByteArray(Charsets.UTF_8)
        } else {
            ByteArray(0)
        }
    }

    private fun writeLegacyMessage(
        file: File,
        message: MessageLite,
        streamingAead: StreamingAead,
        associatedData: ByteArray,
    ) {
        FileOutputStream(file).use { output ->
            streamingAead.newEncryptingStream(output, associatedData).use(message::writeTo)
        }
    }

    private fun writeLegacyBytes(
        file: File,
        bytes: ByteArray,
        streamingAead: StreamingAead,
        associatedData: ByteArray,
    ) {
        FileOutputStream(file).use { output ->
            streamingAead.newEncryptingStream(output, associatedData).use { encrypted ->
                encrypted.write(bytes)
            }
        }
    }

    private fun calculateFileDigest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var count: Int
            while (input.read(buffer).also { count = it } != -1) {
                digest.update(buffer, 0, count)
            }
        }
        return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }

    private fun calculateCurrentVaultHash(directory: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(
            PasswordRepository.PASSWORDS_DATA_FILE,
            PasswordRepository.CATEGORIES_DATA_FILE,
            PasswordRepository.MANIFEST_DATA_FILE,
        ).forEach { logicalName ->
            val file = File(directory, logicalName)
            digest.update(logicalName.toByteArray(Charsets.UTF_8))
            digest.update(file.length().toString().toByteArray(Charsets.UTF_8))
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var count: Int
                while (input.read(buffer).also { count = it } != -1) {
                    digest.update(buffer, 0, count)
                }
            }
        }
        return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }

    private fun createHistoricalEncryptedKeyset(
        keyset: KeysetHandle,
        password: String,
        salt: ByteArray,
    ): ByteArray {
        val wrappingKey = deriveHistoricalWrappingKey(password, salt)
        return ByteArrayOutputStream().use { output ->
            keyset.write(BinaryKeysetWriter.withOutputStream(output), TestAesGcmAead(wrappingKey))
            output.toByteArray()
        }
    }

    private fun deriveHistoricalWrappingKey(password: String, salt: ByteArray): SecretKeySpec {
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

    private fun readCurrentConfig(): VaultConfig {
        return Json.decodeFromString(
            File(
                File(context.filesDir, PasswordRepository.VAULT_DIRECTORY_NAME),
                PasswordRepository.VAULT_CONFIG_FILE_NAME,
            ).readText()
        )
    }

    private fun snapshotFiles(directory: File): Map<String, ByteArray> {
        return directory.walkTopDown()
            .filter(File::isFile)
            .associate { file -> file.relativeTo(directory).invariantSeparatorsPath to file.readBytes() }
    }

    private fun clearTestFiles() {
        context.filesDir.listFiles()?.forEach(File::deleteRecursively)
        context.cacheDir.listFiles()?.forEach(File::deleteRecursively)
    }

    private data class LegacyFixture(
        val attachmentId: String,
        val attachmentPlaintext: ByteArray,
        val thumbnailPlaintext: ByteArray,
        val legacyVaultId: String,
    )

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
