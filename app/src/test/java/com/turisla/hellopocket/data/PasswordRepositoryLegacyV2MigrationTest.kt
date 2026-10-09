package com.turisla.hellopocket.data

import android.app.Application
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
import com.turisla.hellopocket.model.CustomField
import com.turisla.hellopocket.model.CustomFieldType
import com.turisla.hellopocket.model.GeneratorRule
import com.turisla.hellopocket.model.GeneratorSegment
import com.turisla.hellopocket.model.GeneratorStep
import com.turisla.hellopocket.model.GeneratorTemplate
import com.turisla.hellopocket.model.GeneratorVaultData
import com.turisla.hellopocket.model.PasswordEntries
import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.model.PaymentCardBrand
import com.turisla.hellopocket.model.TotpEntries
import com.turisla.hellopocket.model.TotpEntry
import com.turisla.hellopocket.model.VaultConfig
import com.turisla.hellopocket.model.VaultItemType
import com.turisla.hellopocket.model.VaultLoadResult
import com.turisla.hellopocket.model.VaultManifestV2
import com.turisla.hellopocket.security.TinkCryptoManager
import com.turisla.hellopocket.security.VaultSessionGuard
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
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
    fun `schema one first core write creates a restorable backup before current schema activation`() =
        runBlocking {
            val password = "schema-one-master-password"
            val manager = TinkCryptoManager()
            val guard = VaultSessionGuard().apply { enterForeground() }
            val repository = PasswordRepository(context, manager, guard)

            repository.setupNewVault(password)
            repository.addEntry(
                title = "Before upgrade",
                username = "before@example.com",
                plainTextPassword = "before-secret",
                notes = "",
            )
            val encryptionContext = requireNotNull(repository.getVaultEncryptionContext())
            rewriteCurrentVaultAsSchema(encryptionContext, schemaVersion = 1)
            repository.lock()

            val upgradingRepository = PasswordRepository(context, manager, guard)
            assertTrue(upgradingRepository.loadAndDecryptData(password) is VaultLoadResult.Success)
            assertTrue(upgradingRepository.getBackupHistory().isEmpty())

            val customField = CustomField.newBuilder()
                .setId("field-1")
                .setName("Recovery email")
                .setValue("recovery@example.com")
                .setType(CustomFieldType.TEXT)
                .build()
            upgradingRepository.addEntry(
                title = "After upgrade",
                username = "after@example.com",
                plainTextPassword = "after-secret",
                notes = "",
                customFields = listOf(customField),
            )

            val upgradedContext = requireNotNull(upgradingRepository.getVaultEncryptionContext())
            assertEquals(
                PasswordRepository.CURRENT_SCHEMA_VERSION,
                readCurrentManifest(upgradedContext).schemaVersion,
            )
            assertEquals(
                PasswordRepository.CURRENT_SCHEMA_VERSION,
                readCurrentPasswords(upgradedContext).schemaVersion,
            )
            assertEquals(
                listOf(customField),
                upgradingRepository.passwordEntries.value
                    .single { it.title == "After upgrade" }
                    .customFieldsList,
            )

            val schemaOneBackup = upgradingRepository.getBackupHistory().single()
            assertTrue(schemaOneBackup.isFile && schemaOneBackup.length() > 0L)
            assertTrue(upgradingRepository.restoreFromBackup(schemaOneBackup, password))
            assertTrue(upgradingRepository.loadAndDecryptData(password) is VaultLoadResult.Success)
            assertEquals(
                listOf("Before upgrade"),
                upgradingRepository.passwordEntries.value.map(PasswordEntry::getTitle),
            )
            assertTrue(upgradingRepository.passwordEntries.value.single().customFieldsList.isEmpty())
        }

    @Test
    fun `schema two payment card write upgrades safely and keeps a restorable backup`() = runBlocking {
        val password = "schema-two-card-master-password"
        val manager = TinkCryptoManager()
        val guard = VaultSessionGuard().apply { enterForeground() }
        val repository = PasswordRepository(context, manager, guard)

        repository.setupNewVault(password)
        repository.addEntry(
            title = "Existing password",
            username = "before@example.com",
            plainTextPassword = "before-secret",
            notes = "",
        )
        rewriteCurrentVaultAsSchema(
            encryptionContext = requireNotNull(repository.getVaultEncryptionContext()),
            schemaVersion = 2,
        )
        repository.lock()

        val upgradingRepository = PasswordRepository(context, manager, guard)
        assertTrue(upgradingRepository.loadAndDecryptData(password) is VaultLoadResult.Success)
        assertTrue(upgradingRepository.getBackupHistory().isEmpty())

        upgradingRepository.addPaymentCard(
            title = "Travel card",
            cardholderName = "Alex Example",
            cardNumber = "٤١١١ ١١١١ ١١١١ ١١١١",
            cardBrand = PaymentCardBrand.VISA,
            expirationMonth = 12,
            expirationYear = 2032,
            securityCode = "١٢٣",
            notes = "Use abroad",
        )

        val upgradedContext = requireNotNull(upgradingRepository.getVaultEncryptionContext())
        assertEquals(
            PasswordRepository.CURRENT_SCHEMA_VERSION,
            readCurrentManifest(upgradedContext).schemaVersion,
        )
        assertEquals(
            PasswordRepository.CURRENT_SCHEMA_VERSION,
            readCurrentPasswords(upgradedContext).schemaVersion,
        )
        val schemaTwoBackup = upgradingRepository.getBackupHistory().single()
        assertTrue(schemaTwoBackup.isFile && schemaTwoBackup.length() > 0L)

        upgradingRepository.lock()
        assertTrue(upgradingRepository.loadAndDecryptData(password) is VaultLoadResult.Success)
        val restoredCard = upgradingRepository.passwordEntries.value.single {
            it.type == VaultItemType.PAYMENT_CARD
        }
        assertEquals("4111111111111111", restoredCard.cardNumber)
        assertEquals("123", restoredCard.securityCode)

        assertTrue(upgradingRepository.restoreFromBackup(schemaTwoBackup, password))
        assertTrue(upgradingRepository.loadAndDecryptData(password) is VaultLoadResult.Success)
        assertEquals(
            listOf("Existing password"),
            upgradingRepository.passwordEntries.value.map(PasswordEntry::getTitle),
        )
    }

    @Test
    fun `schema one first totp write keeps mixed core schema readable after upgrade`() = runBlocking {
        val password = "schema-one-totp-master-password"
        val manager = TinkCryptoManager()
        val guard = VaultSessionGuard().apply { enterForeground() }
        val repository = PasswordRepository(context, manager, guard)

        repository.setupNewVault(password)
        repository.addEntry(
            title = "Existing password",
            username = "existing@example.com",
            plainTextPassword = "existing-secret",
            notes = "",
        )
        rewriteCurrentVaultAsSchema(
            encryptionContext = requireNotNull(repository.getVaultEncryptionContext()),
            schemaVersion = 1,
        )
        repository.lock()

        val upgradingRepository = PasswordRepository(context, manager, guard)
        assertTrue(upgradingRepository.loadAndDecryptData(password) is VaultLoadResult.Success)
        val totp = TotpEntry.newBuilder()
            .setId("totp-after-schema-upgrade")
            .setIssuer("Example")
            .setAccount("existing@example.com")
            .setSecret(ByteString.copyFromUtf8("JBSWY3DPEHPK3PXP"))
            .setAlgorithm("SHA1")
            .setDigits(6)
            .setPeriod(30)
            .setCreatedAt(2L)
            .setUpdatedAt(2L)
            .build()
        upgradingRepository.persistTotpEntries(listOf(totp))

        val upgradedContext = requireNotNull(upgradingRepository.getVaultEncryptionContext())
        assertEquals(
            PasswordRepository.CURRENT_SCHEMA_VERSION,
            readCurrentManifest(upgradedContext).schemaVersion,
        )
        assertEquals(1, readCurrentPasswords(upgradedContext).schemaVersion)
        assertEquals(
            PasswordRepository.CURRENT_SCHEMA_VERSION,
            readCurrentTotp(upgradedContext).schemaVersion,
        )
        assertTrue(upgradingRepository.getBackupHistory().single().isFile)

        upgradingRepository.lock()
        val reloadedRepository = PasswordRepository(context, manager, guard)
        assertTrue(reloadedRepository.loadAndDecryptData(password) is VaultLoadResult.Success)
        assertEquals(
            listOf("Existing password"),
            reloadedRepository.passwordEntries.value.map(PasswordEntry::getTitle),
        )
        val totpRepository = TotpRepository(context, manager, reloadedRepository, guard)
        assertTrue(
            totpRepository.initialize(requireNotNull(reloadedRepository.getVaultEncryptionContext()))
                is VaultLoadResult.Success
        )
        assertEquals(listOf(totp), totpRepository.totpEntries.value)
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

    @Test
    fun `schema three generator upgrade preserves rule snapshots and backup round trips`() = runBlocking {
        val password = "generator-master-password"
        val manager = TinkCryptoManager()
        val guard = VaultSessionGuard().apply { enterForeground() }
        val vault = PasswordRepository(context, manager, guard)
        vault.setupNewVault(password)
        vault.addEntry("Existing", "user", "existing-secret", "")
        rewriteCurrentVaultAsSchema(requireNotNull(vault.getVaultEncryptionContext()), 3)
        vault.lock()
        assertTrue(vault.loadAndDecryptData(password) is VaultLoadResult.Success)
        assertTrue(vault.getBackupHistory().isEmpty())
        val repository = GeneratorRepository(vault)
        val session = requireNotNull(repository.session.value)
        val rule = GeneratorRule("fixed", "Word", GeneratorSegment.Text("private-fixed-word"))
        val digits = GeneratorRule("digits", "Digits", GeneratorSegment.Digits(6))
        repository.saveRule(session, rule)
        repository.saveRule(session, digits)
        val template = GeneratorTemplate("template", "Work", listOf(
            GeneratorStep("one", rule.name, rule.segment),
            GeneratorStep("two", digits.name, digits.segment),
        ))
        repository.saveTemplate(session, template)
        assertEquals(4, readCurrentManifest(requireNotNull(vault.getVaultEncryptionContext())).schemaVersion)
        val oldBackup = vault.getBackupHistory().single()
        val generatorFile = File(File(context.filesDir, PasswordRepository.VAULT_DIRECTORY_NAME), PasswordRepository.GENERATOR_DATA_FILE)
        assertFalse(generatorFile.readBytes().toString(Charsets.ISO_8859_1).contains("private-fixed-word"))

        repository.saveRule(session, rule.copy(segment = GeneratorSegment.Text("changed-word")))
        repository.deleteRule(session, rule.id)
        assertEquals(template, repository.data.value.templates.single())
        // 普通条目、TOTP 与改密路径也必须保留规则文件的认证摘要。
        vault.addEntry("Another", "user", "another-secret", "")
        vault.persistTotpEntries(emptyList())
        val rulesBeforePasswordChange = generatorFile.readBytes()
        assertTrue(vault.changeMasterPassword(password, "new-generator-master-password").success)
        assertArrayEquals(rulesBeforePasswordChange, generatorFile.readBytes())
        val expected = repository.data.value
        val exported = requireNotNull(vault.exportToCache("generator-round-trip.hpb"))
        vault.lock()
        assertEquals(GeneratorVaultData(), repository.data.value)
        assertNull(repository.session.value)
        assertTrue(vault.loadAndDecryptData("new-generator-master-password") is VaultLoadResult.Success)
        assertEquals(expected, repository.data.value)
        repository.deleteTemplate(requireNotNull(repository.session.value), template.id)
        assertTrue(vault.restoreFromBackup(exported, "new-generator-master-password"))
        assertTrue(vault.loadAndDecryptData("new-generator-master-password") is VaultLoadResult.Success)
        assertEquals(expected, repository.data.value)
        assertEquals(2, vault.passwordEntries.value.size)

        assertTrue(vault.restoreFromBackup(oldBackup, password))
        assertTrue(vault.loadAndDecryptData(password) is VaultLoadResult.Success)
        assertEquals(GeneratorVaultData(), repository.data.value)
        assertEquals(listOf("Existing"), vault.passwordEntries.value.map { it.title })
        assertEquals(3, readCurrentManifest(requireNotNull(vault.getVaultEncryptionContext())).schemaVersion)
    }

    @Test
    fun `missing or tampered generator files fail authentication without publishing rules`() = runBlocking {
        val manager = TinkCryptoManager()
        val guard = VaultSessionGuard().apply { enterForeground() }
        val vault = PasswordRepository(context, manager, guard)
        vault.setupNewVault("generator-test-password")
        val repository = GeneratorRepository(vault)
        repository.saveRule(requireNotNull(repository.session.value), GeneratorRule("digits", "Digits", GeneratorSegment.Digits(6)))
        val file = File(File(context.filesDir, PasswordRepository.VAULT_DIRECTORY_NAME), PasswordRepository.GENERATOR_DATA_FILE)
        val original = file.readBytes()
        vault.lock()
        assertTrue(file.delete())
        assertTrue(vault.loadAndDecryptData("generator-test-password") is VaultLoadResult.IntegrityCheckFailed)
        assertEquals(GeneratorVaultData(), repository.data.value)
        val changed = original.copyOf()
        changed[changed.lastIndex] = (changed.last().toInt() xor 1).toByte()
        file.writeBytes(changed)
        assertTrue(vault.loadAndDecryptData("generator-test-password") is VaultLoadResult.IntegrityCheckFailed)
        assertNull(repository.session.value)
        file.writeBytes(original)
        assertTrue(vault.loadAndDecryptData("generator-test-password") is VaultLoadResult.Success)
        assertEquals(1, repository.data.value.rules.size)
    }

    @Test
    fun `stale generator sessions and invalid templates never write the vault`() = runBlocking {
        val manager = TinkCryptoManager()
        val guard = VaultSessionGuard().apply { enterForeground() }
        val vault = PasswordRepository(context, manager, guard)
        vault.setupNewVault("generator-test-password")
        val repository = GeneratorRepository(vault)
        val staleSession = requireNotNull(repository.session.value)
        vault.lock()
        assertTrue(vault.loadAndDecryptData("generator-test-password") is VaultLoadResult.Success)
        val directory = File(context.filesDir, PasswordRepository.VAULT_DIRECTORY_NAME)
        val before = snapshotFiles(directory)
        var rejected = false
        try {
            repository.saveRule(staleSession, GeneratorRule("old", "Old", GeneratorSegment.Text("old-vault-secret")))
        } catch (_: CancellationException) { rejected = true }
        assertTrue(rejected)
        rejected = false
        try {
            repository.saveTemplate(requireNotNull(repository.session.value), GeneratorTemplate("bad", "Bad", listOf(
                GeneratorStep("fixed", "Fixed", GeneratorSegment.Text("no-random-part")),
            )))
        } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        val after = snapshotFiles(directory)
        assertEquals(before.keys, after.keys)
        before.forEach { (name, bytes) -> assertArrayEquals(bytes, after.getValue(name)) }
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

    private fun rewriteCurrentVaultAsSchema(
        encryptionContext: VaultEncryptionContext,
        schemaVersion: Int,
    ) {
        require(
            schemaVersion in PasswordRepository.MIN_SUPPORTED_SCHEMA_VERSION until
                PasswordRepository.CURRENT_SCHEMA_VERSION
        )
        val vaultDirectory = File(context.filesDir, PasswordRepository.VAULT_DIRECTORY_NAME)
        val streamingAead = TinkCryptoManager().getStreamingAead(encryptionContext.keysetHandle)
        val passwords = readCurrentPasswords(encryptionContext)
        val categoriesFile = File(vaultDirectory, PasswordRepository.CATEGORIES_DATA_FILE)
        val categories = readEncryptedBytes(
            categoriesFile,
            streamingAead,
            encryptionContext.associatedData(PasswordRepository.CATEGORIES_DATA_FILE),
        ).let(Categories::parseFrom)
        val manifest = readCurrentManifest(encryptionContext)
        // 旧 schema 没有规则文件，测试夹具也必须准确模拟旧版文件集合。
        File(vaultDirectory, PasswordRepository.GENERATOR_DATA_FILE).delete()

        writeLegacyMessage(
            File(vaultDirectory, PasswordRepository.PASSWORDS_DATA_FILE),
            passwords.toBuilder().setSchemaVersion(schemaVersion).build(),
            streamingAead,
            encryptionContext.associatedData(PasswordRepository.PASSWORDS_DATA_FILE),
        )
        writeLegacyMessage(
            categoriesFile,
            categories.toBuilder().setSchemaVersion(schemaVersion).build(),
            streamingAead,
            encryptionContext.associatedData(PasswordRepository.CATEGORIES_DATA_FILE),
        )

        val snapshotFiles = buildMap {
            put(
                PasswordRepository.PASSWORDS_DATA_FILE,
                File(vaultDirectory, PasswordRepository.PASSWORDS_DATA_FILE),
            )
            put(PasswordRepository.CATEGORIES_DATA_FILE, categoriesFile)
            File(vaultDirectory, PasswordRepository.TOTP_DATA_FILE)
                .takeIf(File::isFile)
                ?.let { put(PasswordRepository.TOTP_DATA_FILE, it) }
        }
        val downgradedManifest = manifest.copy(
            schemaVersion = schemaVersion,
            fileDigests = snapshotFiles.mapValues { (_, file) -> calculateFileDigest(file) },
            fileSizes = snapshotFiles.mapValues { (_, file) -> file.length() },
        )
        writeLegacyBytes(
            File(vaultDirectory, PasswordRepository.MANIFEST_DATA_FILE),
            Json.encodeToString(downgradedManifest).toByteArray(Charsets.UTF_8),
            streamingAead,
            encryptionContext.associatedData(PasswordRepository.MANIFEST_DATA_FILE),
        )

        val config = readCurrentConfig().copy(
            integrityHash = calculateCurrentVaultHash(vaultDirectory),
        )
        File(vaultDirectory, PasswordRepository.VAULT_CONFIG_FILE_NAME)
            .writeText(Json.encodeToString(config))
    }

    private fun readCurrentPasswords(encryptionContext: VaultEncryptionContext): PasswordEntries {
        val streamingAead = TinkCryptoManager().getStreamingAead(encryptionContext.keysetHandle)
        return readEncryptedBytes(
            File(
                File(context.filesDir, PasswordRepository.VAULT_DIRECTORY_NAME),
                PasswordRepository.PASSWORDS_DATA_FILE,
            ),
            streamingAead,
            encryptionContext.associatedData(PasswordRepository.PASSWORDS_DATA_FILE),
        ).let(PasswordEntries::parseFrom)
    }

    private fun readCurrentManifest(encryptionContext: VaultEncryptionContext): VaultManifestV2 {
        val streamingAead = TinkCryptoManager().getStreamingAead(encryptionContext.keysetHandle)
        val bytes = readEncryptedBytes(
            File(
                File(context.filesDir, PasswordRepository.VAULT_DIRECTORY_NAME),
                PasswordRepository.MANIFEST_DATA_FILE,
            ),
            streamingAead,
            encryptionContext.associatedData(PasswordRepository.MANIFEST_DATA_FILE),
        )
        return Json.decodeFromString(bytes.toString(Charsets.UTF_8))
    }

    private fun readCurrentTotp(encryptionContext: VaultEncryptionContext): TotpEntries {
        val streamingAead = TinkCryptoManager().getStreamingAead(encryptionContext.keysetHandle)
        return readEncryptedBytes(
            File(
                File(context.filesDir, PasswordRepository.VAULT_DIRECTORY_NAME),
                PasswordRepository.TOTP_DATA_FILE,
            ),
            streamingAead,
            encryptionContext.associatedData(PasswordRepository.TOTP_DATA_FILE),
        ).let(TotpEntries::parseFrom)
    }

    private fun readEncryptedBytes(
        file: File,
        streamingAead: StreamingAead,
        associatedData: ByteArray,
    ): ByteArray = file.inputStream().use { input ->
        streamingAead.newDecryptingStream(input, associatedData).use { it.readBytes() }
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
