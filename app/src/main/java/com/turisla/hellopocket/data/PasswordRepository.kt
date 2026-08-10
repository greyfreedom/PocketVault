package com.turisla.hellopocket.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.edit
import com.turisla.hellopocket.model.AttachmentManifestEntry
import com.turisla.hellopocket.model.Categories
import com.turisla.hellopocket.model.Category
import com.turisla.hellopocket.model.CustomField
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
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.protobuf.CodedInputStream
import com.turisla.hellopocket.utils.AppConstants
import com.turisla.hellopocket.utils.loggerE
import com.turisla.hellopocket.utils.loggerI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher

data class ChangePasswordResult(val success: Boolean, val biometricsWereDisabled: Boolean)
enum class ImportDataFailureReason {
    INVALID_BACKUP,
    UPGRADE_FAILED,
}

data class ImportDataFileResult(
    val success: Boolean,
    val biometricsWereDisabled: Boolean,
    val failureReason: ImportDataFailureReason? = null,
)

// 接受本地化数字键盘输入，但持久化前统一转为可互操作的 ASCII 数字。
private fun String.toAsciiDecimalDigits(): String = buildString(length) {
    this@toAsciiDecimalDigits.forEach { character ->
        character.digitToIntOrNull()?.let { digit -> append(('0'.code + digit).toChar()) }
    }
}

data class VaultEncryptionContext(
    val keysetHandle: KeysetHandle,
    val vaultId: String,
    val associatedDataVersion: Int = 1,
) {
    fun associatedData(logicalName: String): ByteArray {
        return if (associatedDataVersion >= 1) {
            "hellopocket|$vaultId|$logicalName".toByteArray(Charsets.UTF_8)
        } else {
            ByteArray(0)
        }
    }
}

class PasswordRepository(
    private val context: Context,
    private val tinkCryptoManager: TinkCryptoManager,
    private val sessionGuard: VaultSessionGuard,
) {

    companion object {
        const val VAULT_VERSION_V2 = 2
        const val VAULT_DIRECTORY_NAME = "hellopocket_vault"
        const val VAULT_CONFIG_FILE_NAME = "vault_v2.json" // V2
        const val MANIFEST_DATA_FILE = "manifest.dat" // V2: 加密的manifest
        const val PASSWORDS_DATA_FILE = "passwords.dat"
        const val CATEGORIES_DATA_FILE = "categories.dat"
        const val TOTP_DATA_FILE = "totp.dat"
        const val ATTACHMENTS_DIR = "attachments"
        
        // 公开保险库仍为 V2；内部 schema 3 增加支付卡条目及其专用字段。
        const val MIN_SUPPORTED_SCHEMA_VERSION = 1
        const val CURRENT_SCHEMA_VERSION = 3

        private const val SALT_SIZE_BYTES = 16
        private const val LEGACY_VAULT_VERSION = 1
        private const val LEGACY_MANIFEST_FILE_NAME = "manifest.json"

        private const val BACKUP_FILE_PREFIX = "pocketvault_backup_"
        private const val BACKUP_FILE_SUFFIX = ".hpb"
        private const val MAX_AUTOMATIC_BACKUP_COUNT = 5
        private const val MAX_AUTOMATIC_BACKUP_TOTAL_BYTES = 1024L * 1024 * 1024

        private const val BIOMETRIC_PREFS = "biometric_prefs"
        private const val PREF_ENCRYPTED_DATA_KEY = "encrypted_data_key"
        private const val PREF_DATA_KEY_IV = "data_key_iv"
        private const val PREF_BIOMETRIC_ENABLED = "biometric_enabled"

        private const val MAX_IMPORT_ENTRY_COUNT = 10_000
        private const val MAX_IMPORT_UNCOMPRESSED_BYTES = 1024L * 1024 * 1024
        private const val MAX_IMPORT_SINGLE_FILE_BYTES = AppConstants.MAX_ATTACHMENT_SIZE_BYTES + (2L * 1024 * 1024)
        private const val MIN_IMPORT_FREE_SPACE_RESERVE_BYTES = 64L * 1024 * 1024
        private const val MAX_VAULT_CONFIG_BYTES = 1024L * 1024
        private const val MAX_MANIFEST_PLAINTEXT_BYTES = 8L * 1024 * 1024
        private const val MAX_PASSWORDS_PLAINTEXT_BYTES = 32L * 1024 * 1024
        private const val MAX_CATEGORIES_PLAINTEXT_BYTES = 4L * 1024 * 1024
        private const val MAX_TOTP_PLAINTEXT_BYTES = 16L * 1024 * 1024
        private const val MAX_THUMBNAIL_PLAINTEXT_BYTES = 2L * 1024 * 1024
        private const val MAX_THUMBNAIL_DIMENSION = 200
        private const val STREAMING_CIPHERTEXT_OVERHEAD_ALLOWANCE_BYTES = 2L * 1024 * 1024
        private const val MAX_PASSWORD_ENTRY_COUNT = 100_000
        private const val MAX_CATEGORY_COUNT = 10_000
        private const val MAX_ATTACHMENT_COUNT = 5_000
        private const val MAX_TOTP_ENTRY_COUNT = 10_000
        private const val PROTOBUF_RECURSION_LIMIT = 100
        private val DISCARDING_OUTPUT = object : OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
        }
    }


    private val vaultDir = File(context.filesDir, VAULT_DIRECTORY_NAME)
    private val legacyManifestFile = File(vaultDir, LEGACY_MANIFEST_FILE_NAME)
    private val vaultConfigFile = File(vaultDir, VAULT_CONFIG_FILE_NAME) // V2

    private val prefs = context.getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)

    private val _passwordEntries = MutableStateFlow<List<PasswordEntry>>(emptyList())
    val passwordEntries: StateFlow<List<PasswordEntry>> = _passwordEntries.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _attachments = MutableStateFlow<List<AttachmentManifestEntry>>(emptyList())
    val attachments: StateFlow<List<AttachmentManifestEntry>> = _attachments.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(isBiometricUnlockEnabled())
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    @Volatile
    private var streamingAeadKeysetHandle: KeysetHandle? = null // V2 only (唯一的密钥集)
    
    @Volatile
    private var vaultConfig: VaultConfig? = null // V2 only
    
    @Volatile
    private var vaultManifestV2: VaultManifestV2? = null // V2 only: 加密的manifest

    @Volatile
    private var sessionEpoch: Long = 0L

    // 统一保护会话发布、最终磁盘提交、解密缓存发布与锁库清理。
    private val vaultSessionLock = Any()
    private val vaultSessionCommitGate = VaultSessionCommitGate(vaultSessionLock)

    @Volatile
    private var recoveryFailed: Boolean = false
    
    // 并发保护：防止多个协程同时写入保险库文件
    private val saveMutex = kotlinx.coroutines.sync.Mutex()
    // 保护“读取当前 StateFlow → 计算新状态 → 提交”的完整读改写序列，防止并发更新丢失。
    private val stateMutationMutex = kotlinx.coroutines.sync.Mutex()

    private data class LoadSession(
        val guardToken: VaultSessionGuard.Token,
        val repositoryEpoch: Long,
    )

    private data class LoadedVaultData(
        val manifest: VaultManifestV2,
        val passwords: List<PasswordEntry>,
        val categories: List<Category>,
    )

    private data class LoadedLegacyV2Data(
        val manifest: CompatibleVaultManifestV2,
        val passwords: List<PasswordEntry>,
        val categories: List<Category>,
        val totpEntries: List<TotpEntry>,
        val hasTotpFile: Boolean,
    )

    private data class PreparedVaultUpgrade(
        val directory: File,
        val config: VaultConfig,
        val manifest: VaultManifestV2,
    )

    private class VaultUpgradeException(cause: Throwable) : Exception(cause)

    private data class VaultWriteSession(
        val epoch: Long,
        val config: VaultConfig,
        val keyset: KeysetHandle,
        val manifest: VaultManifestV2,
    )

    private fun captureActiveVaultWriteSession(): VaultWriteSession? = synchronized(vaultSessionLock) {
        VaultWriteSession(
            epoch = sessionEpoch,
            config = vaultConfig ?: return@synchronized null,
            keyset = streamingAeadKeysetHandle ?: return@synchronized null,
            manifest = vaultManifestV2 ?: return@synchronized null,
        )
    }

    /**
     * 最终文件替换和会话校验必须在锁库使用的同一临界区内完成。
     * 不能只在提交后决定是否向 UI 发布，否则旧会话仍可能修改磁盘保险库。
     */
    private fun runVaultMutationForSession(
        expectedEpoch: Long,
        isContextCurrent: () -> Boolean = { true },
        mutation: () -> Unit,
    ) {
        val committed = vaultSessionCommitGate.commitIf(
            isSessionCurrent = {
                sessionEpoch == expectedEpoch && isContextCurrent()
            },
            commit = mutation,
        )
        if (!committed) {
            throw CancellationException("Vault session was invalidated before commit")
        }
    }

    private fun commitVaultTransactionForSession(
        expectedEpoch: Long,
        expectedKeyset: KeysetHandle,
        writes: List<VaultFileTransaction.PendingWrite>,
        onCommitted: () -> Unit,
    ) {
        runVaultMutationForSession(
            expectedEpoch = expectedEpoch,
            isContextCurrent = { streamingAeadKeysetHandle === expectedKeyset },
            mutation = {
                VaultFileTransaction.commit(vaultDir, writes)
                onCommitted()
            },
        )
    }

    init {
        // 进程异常退出后可能遗留上一次会话的明文缓存，启动时立即清理。
        clearAttachmentCache()
        try {
            VaultFileTransaction.recoverDirectoryTransactions(context.filesDir, VAULT_DIRECTORY_NAME)
            if (vaultDir.exists()) VaultFileTransaction.recover(vaultDir)
        } catch (error: Exception) {
            // 不在构造阶段崩溃或猜测性删除；保留现场，并在解锁页显示“文件损坏”。
            recoveryFailed = true
            loggerE(error)
        }
    }


    fun isVaultInitialized(): Boolean {
        if (recoveryFailed && vaultDir.exists()) return true
        if (!vaultDir.isDirectory) return false
        // 旧格式仍识别为“已初始化”，以便明确拒绝，而不是误入新建流程覆盖用户数据。
        return vaultConfigFile.isFile || legacyManifestFile.isFile
    }

    /**
     * 清理未被目录事务引用的 setup / migration / import 暂存目录。
     * 删除放到 IO 协程并支持取消，避免构造仓库时在主线程清理超大导入残留。
     */
    suspend fun cleanupStaleStagingDirectories() = withContext(Dispatchers.IO) {
        val stagingPrefixes = listOf(
            ".$VAULT_DIRECTORY_NAME.pending-",
            ".$VAULT_DIRECTORY_NAME.migration-",
            ".$VAULT_DIRECTORY_NAME.import-",
        )
        context.filesDir.listFiles()
            ?.filter { candidate -> stagingPrefixes.any(candidate.name::startsWith) }
            ?.forEach { stalePath ->
                stalePath.walkBottomUp().forEach { file ->
                    currentCoroutineContext().ensureActive()
                    file.delete()
                }
            }
    }

    suspend fun setupNewVault(masterPassword: String, passwordHint: String? = null) = withContext(Dispatchers.IO) {
        require(masterPassword.length >= AppConstants.MIN_MASTER_PASSWORD_LENGTH)
        val loadSession = captureLoadSession()
        saveMutex.withLock {
            val stagedDir = File(context.filesDir, ".$VAULT_DIRECTORY_NAME.pending-${UUID.randomUUID()}")
            try {
                stagedDir.mkdirs()
                File(stagedDir, ATTACHMENTS_DIR).mkdirs()

                val salt = tinkCryptoManager.generateSalt()
                val keyset = tinkCryptoManager.generateVaultKeyset()
                val vaultId = UUID.randomUUID().toString()
                val encryptedKeyset = tinkCryptoManager.encryptKeyset(
                    keyset,
                    masterPassword,
                    salt,
                    TinkCryptoManager.DEFAULT_KDF_ITERATIONS
                )
                val configWithoutHash = VaultConfig(
                    version = VAULT_VERSION_V2,
                    salt = Base64.encodeToString(salt, Base64.NO_WRAP),
                    encryptedKeyset = encryptedKeyset,
                    passwordHint = passwordHint,
                    kdfAlgorithm = TinkCryptoManager.PBKDF2_ALGORITHM,
                    kdfIterations = TinkCryptoManager.DEFAULT_KDF_ITERATIONS,
                    vaultId = vaultId,
                    associatedDataVersion = 1
                )
                val encryptionContext = VaultEncryptionContext(keyset, vaultId)
                val streamingAead = tinkCryptoManager.getStreamingAead(keyset)
                val defaultCategories = createDefaultCategories()

                writeEncryptedMessage(
                    File(stagedDir, PASSWORDS_DATA_FILE),
                    PasswordEntries.newBuilder().setSchemaVersion(CURRENT_SCHEMA_VERSION).build(),
                    streamingAead,
                    encryptionContext.associatedData(PASSWORDS_DATA_FILE)
                )
                writeEncryptedMessage(
                    File(stagedDir, CATEGORIES_DATA_FILE),
                    Categories.newBuilder()
                        .setSchemaVersion(CURRENT_SCHEMA_VERSION)
                        .addAllCategories(defaultCategories)
                        .build(),
                    streamingAead,
                    encryptionContext.associatedData(CATEGORIES_DATA_FILE)
                )
                val initialMetadata = calculateSnapshotMetadata(
                    mapOf(
                        PASSWORDS_DATA_FILE to File(stagedDir, PASSWORDS_DATA_FILE),
                        CATEGORIES_DATA_FILE to File(stagedDir, CATEGORIES_DATA_FILE),
                    )
                )
                val newManifest = VaultManifestV2(
                    schemaVersion = CURRENT_SCHEMA_VERSION,
                    vaultId = vaultId,
                    createdAt = System.currentTimeMillis(),
                    fileDigests = initialMetadata.digests,
                    fileSizes = initialMetadata.sizes,
                    configBinding = calculateConfigBinding(configWithoutHash),
                )
                writeEncryptedManifest(
                    File(stagedDir, MANIFEST_DATA_FILE),
                    newManifest,
                    streamingAead,
                    encryptionContext.associatedData(MANIFEST_DATA_FILE)
                )

                val newVaultConfig = configWithoutHash.copy(
                    integrityHash = calculateVaultIntegrityHash(stagedDir)
                        ?: error("Failed to calculate initial vault hash")
                )
                writePlainTextSynced(File(stagedDir, VAULT_CONFIG_FILE_NAME), Json.encodeToString(newVaultConfig))
                currentCoroutineContext().ensureActive()
                if (!sessionGuard.publishIfValid(loadSession.guardToken) {}) {
                    throw CancellationException("Vault setup session is no longer active")
                }
                runVaultMutationForSession(loadSession.repositoryEpoch) {
                    VaultFileTransaction.replaceDirectory(vaultDir, stagedDir)
                }

                publishLoadedV2(
                    loadSession = loadSession,
                    config = newVaultConfig,
                    keyset = keyset,
                    data = LoadedVaultData(newManifest, emptyList(), defaultCategories),
                )
                disableBiometricUnlock()
            } finally {
                stagedDir.deleteRecursively()
            }
        }
    }


    suspend fun loadAndDecryptData(masterPassword: String): VaultLoadResult = withContext(Dispatchers.IO) {
        if (recoveryFailed) return@withContext VaultLoadResult.FileCorrupted
        if (!isVaultInitialized()) return@withContext VaultLoadResult.FileCorrupted
        if (!vaultConfigFile.isFile) {
            return@withContext VaultLoadResult.UnsupportedVersion(LEGACY_VAULT_VERSION)
        }
        val loadSession = captureLoadSession()
        return@withContext loadV2Vault(masterPassword, loadSession)
    }
    
    private suspend fun loadV2Vault(
        masterPassword: String,
        loadSession: LoadSession,
    ): VaultLoadResult = withContext(Dispatchers.IO) {
        try {
            val configJson = readUtf8FileWithLimit(vaultConfigFile, MAX_VAULT_CONFIG_BYTES)
            val compatibleConfig = decodeCompatibleVaultConfig(configJson)
            validateCompatibleVaultConfig(compatibleConfig)?.let { return@withContext it }
            val config = compatibleConfig.toVaultConfig()
            val salt = Base64.decode(config.salt, Base64.NO_WRAP)
            if (salt.size != SALT_SIZE_BYTES) return@withContext VaultLoadResult.FileCorrupted
            
            // 1. 解密唯一的 Keyset
            val keyset = try {
                tinkCryptoManager.decryptKeyset(
                    config.encryptedKeyset,
                    masterPassword,
                    salt,
                    config.kdfIterations
                )
            } catch (e: Exception) {
                loggerE(e)
                return@withContext VaultLoadResult.WrongPassword
            }
            
            val requiresUpgrade = compatibleConfig.requiresOneTimeUpgrade()

            // 2. 旧 V2 可验证历史摘要；缺失摘要时仍必须依赖后续 AEAD 全量认证。
            if (!verifyVaultIntegrity(
                    directory = vaultDir,
                    expectedHash = config.integrityHash,
                    allowLegacyFormat = requiresUpgrade,
                )
            ) {
                loggerI("Vault integrity check failed!")
                return@withContext VaultLoadResult.IntegrityCheckFailed
            }

            if (requiresUpgrade) {
                val (legacyResult, legacyData) = loadLegacyV2Data(
                    directory = vaultDir,
                    compatibleConfig = compatibleConfig,
                    keyset = keyset,
                    allowMissingAuthenticatedMetadata = compatibleConfig
                        .allowsLegacyEncryptedMetadata(),
                )
                if (legacyResult !is VaultLoadResult.Success || legacyData == null) {
                    return@withContext legacyResult
                }

                val upgraded = try {
                    upgradeLegacyV2Vault(
                        masterPassword = masterPassword,
                        loadSession = loadSession,
                        compatibleConfig = compatibleConfig,
                        keyset = keyset,
                        legacyData = legacyData,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    loggerE(error)
                    return@withContext VaultLoadResult.UpgradeFailed
                }

                currentCoroutineContext().ensureActive()
                if (!publishLoadedV2(
                        loadSession = loadSession,
                        config = upgraded.config,
                        keyset = keyset,
                        data = LoadedVaultData(
                            manifest = upgraded.manifest,
                            passwords = legacyData.passwords,
                            categories = legacyData.categories,
                        ),
                    )
                ) {
                    return@withContext VaultLoadResult.SessionInvalidated
                }
                disableBiometricUnlock()
                return@withContext if (isLoadSessionCurrent(loadSession, keyset)) {
                    VaultLoadResult.Success
                } else {
                    lock()
                    VaultLoadResult.SessionInvalidated
                }
            }

            validateVaultConfig(config)?.let { return@withContext it }

            // 3. 所有文件先解密到局部变量，通过会话闸门后才一次性发布。
            val (loadResult, loadedData) = loadVaultDataFromStreamingAead(
                tinkCryptoManager.getStreamingAead(keyset),
                keyset,
                config,
            )
            if (loadResult !is VaultLoadResult.Success || loadedData == null) return@withContext loadResult
            currentCoroutineContext().ensureActive()
            if (!publishLoadedV2(loadSession, config, keyset, loadedData)) {
                return@withContext VaultLoadResult.SessionInvalidated
            }

            return@withContext if (isLoadSessionCurrent(loadSession, keyset)) {
                VaultLoadResult.Success
            } else {
                lock()
                VaultLoadResult.SessionInvalidated
            }
        } catch (error: CancellationException) {
            lock()
            throw error
        } catch (e: Exception) {
            loggerE(e)
            lock()
            return@withContext VaultLoadResult.FileCorrupted
        }
    }
    /**
     * 使用 Keyset 的字节表示来加载 V2 保险库（用于生物识别解锁）
     */
    suspend fun loadDataWithKeysetBytes(keysetBytes: ByteArray): VaultLoadResult = withContext(Dispatchers.IO) {
        if (recoveryFailed) return@withContext VaultLoadResult.FileCorrupted
        if (!vaultConfigFile.isFile) return@withContext VaultLoadResult.FileCorrupted
        val loadSession = captureLoadSession()
        try {
            // 1. 先读取配置；旧 V2 必须输入主密码才能提升 KDF，不能用生物识别绕过。
            val configJson = readUtf8FileWithLimit(vaultConfigFile, MAX_VAULT_CONFIG_BYTES)
            val compatibleConfig = decodeCompatibleVaultConfig(configJson)
            validateCompatibleVaultConfig(compatibleConfig)?.let { return@withContext it }
            if (compatibleConfig.requiresOneTimeUpgrade()) {
                return@withContext VaultLoadResult.UpgradeRequiresMasterPassword
            }
            val config = compatibleConfig.toVaultConfig()
            validateVaultConfig(config)?.let { return@withContext it }
            if (!verifyVaultIntegrity(vaultDir, config.integrityHash, allowLegacyFormat = false)) {
                return@withContext VaultLoadResult.IntegrityCheckFailed
            }

            // 2. 从字节恢复 Keyset
            val keyset = tinkCryptoManager.readKeysetFromBytes(keysetBytes)

            // 3. 使用 Keyset 加载数据，但仅在会话仍有效时发布。
            val (loadResult, loadedData) = loadVaultDataFromStreamingAead(
                tinkCryptoManager.getStreamingAead(keyset),
                keyset,
                config,
            )
            if (loadResult !is VaultLoadResult.Success || loadedData == null) return@withContext loadResult
            currentCoroutineContext().ensureActive()
            if (!publishLoadedV2(loadSession, config, keyset, loadedData)) {
                return@withContext VaultLoadResult.SessionInvalidated
            }
            return@withContext if (isLoadSessionCurrent(loadSession, keyset)) {
                VaultLoadResult.Success
            } else {
                lock()
                VaultLoadResult.SessionInvalidated
            }
        } catch (error: CancellationException) {
            lock()
            throw error
        } catch (e: Exception) {
            loggerE(e)
            lock()
            return@withContext VaultLoadResult.FileCorrupted
        }
    }

    /**
     * V2 保险库的核心加载逻辑，复用于主密码解锁和生物识别解锁
     * @param streamingAead 从有效 Keyset 获取的 StreamingAead 实例
     */
    private suspend fun loadVaultDataFromStreamingAead(
        streamingAead: StreamingAead,
        keyset: KeysetHandle,
        config: VaultConfig,
    ): Pair<VaultLoadResult, LoadedVaultData?> = withContext(Dispatchers.IO) {
        try {
            val encryptionContext = VaultEncryptionContext(
                keyset,
                config.vaultId,
            )
            // 1. 流式加载 Manifest (获取附件列表)
            val manifest = loadManifestV2FromStream(streamingAead, encryptionContext)
                ?: return@withContext VaultLoadResult.FileCorrupted to null
            require(manifest.attachments.size <= MAX_ATTACHMENT_COUNT) {
                "Vault contains too many attachments"
            }
            if (!isSchemaVersionSupported(manifest.schemaVersion)) {
                return@withContext VaultLoadResult.UnsupportedVersion(manifest.schemaVersion) to null
            }
            if (manifest.vaultId != config.vaultId) {
                return@withContext VaultLoadResult.FileCorrupted to null
            }
            if (!verifyConfigBinding(manifest, config)) {
                return@withContext VaultLoadResult.IntegrityCheckFailed to null
            }
            if (!verifyAuthenticatedSnapshotMetadata(manifest, vaultDir)) {
                return@withContext VaultLoadResult.IntegrityCheckFailed to null
            }

            // 2. 流式加载 Passwords
            val passwordsFile = File(vaultDir, PASSWORDS_DATA_FILE)
            if (!passwordsFile.isFile) return@withContext VaultLoadResult.FileCorrupted to null
            val loadedPasswords = FileInputStream(passwordsFile).use { fis ->
                streamingAead.newDecryptingStream(
                    fis,
                    encryptionContext.associatedData(PASSWORDS_DATA_FILE)
                ).use { decStream ->
                    parseProtobufWithLimits(decStream, MAX_PASSWORDS_PLAINTEXT_BYTES) {
                        PasswordEntries.parseFrom(it)
                    }
                }
            }
            require(loadedPasswords.entriesCount <= MAX_PASSWORD_ENTRY_COUNT) {
                "Vault contains too many password entries"
            }
            if (!isSchemaVersionSupported(loadedPasswords.schemaVersion)) {
                return@withContext VaultLoadResult.UnsupportedVersion(loadedPasswords.schemaVersion) to null
            }

            // 3. 流式加载 Categories
            val categoriesFile = File(vaultDir, CATEGORIES_DATA_FILE)
            if (!categoriesFile.isFile) return@withContext VaultLoadResult.FileCorrupted to null
            val loadedCategories = FileInputStream(categoriesFile).use { fis ->
                streamingAead.newDecryptingStream(
                    fis,
                    encryptionContext.associatedData(CATEGORIES_DATA_FILE)
                ).use { decStream ->
                    parseProtobufWithLimits(decStream, MAX_CATEGORIES_PLAINTEXT_BYTES) {
                        Categories.parseFrom(it)
                    }
                }
            }
            require(loadedCategories.categoriesCount <= MAX_CATEGORY_COUNT) {
                "Vault contains too many categories"
            }
            if (!isSchemaVersionSupported(loadedCategories.schemaVersion)) {
                return@withContext VaultLoadResult.UnsupportedVersion(loadedCategories.schemaVersion) to null
            }

            VaultSemanticValidator.validateCore(
                passwords = loadedPasswords.entriesList,
                categories = loadedCategories.categoriesList,
                attachments = manifest.attachments,
            )
            return@withContext VaultLoadResult.Success to LoadedVaultData(
                manifest = manifest,
                passwords = loadedPasswords.entriesList,
                categories = loadedCategories.categoriesList,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            loggerE(e)
            return@withContext VaultLoadResult.FileCorrupted to null
        }
    }

    /**
     * 读取并认证早期 V2。此路径只有在公开配置明确带有旧格式特征时才会进入，
     * 且这里只产生局部明文，不会发布状态或修改磁盘。
     */
    private suspend fun loadLegacyV2Data(
        directory: File,
        compatibleConfig: CompatibleVaultConfig,
        keyset: KeysetHandle,
        allowMissingAuthenticatedMetadata: Boolean,
    ): Pair<VaultLoadResult, LoadedLegacyV2Data?> = withContext(Dispatchers.IO) {
        try {
            val streamingAead = tinkCryptoManager.getStreamingAead(keyset)
            val encryptionContext = VaultEncryptionContext(
                keysetHandle = keyset,
                vaultId = compatibleConfig.vaultId,
                associatedDataVersion = compatibleConfig.associatedDataVersion,
            )
            val manifest = decryptImportedFile(
                File(directory, MANIFEST_DATA_FILE),
                streamingAead,
                encryptionContext.associatedData(MANIFEST_DATA_FILE),
            ) { input ->
                decodeCompatibleVaultManifest(
                    readUtf8WithLimit(input, MAX_MANIFEST_PLAINTEXT_BYTES)
                )
            }
            require(manifest.attachments.size <= MAX_ATTACHMENT_COUNT) {
                "Legacy vault contains too many attachments"
            }
            val manifestSchemaSupported = if (allowMissingAuthenticatedMetadata) {
                manifest.schemaVersion in 0..CURRENT_SCHEMA_VERSION
            } else {
                isSchemaVersionSupported(manifest.schemaVersion)
            }
            if (!manifestSchemaSupported) {
                return@withContext VaultLoadResult.UnsupportedVersion(manifest.schemaVersion) to null
            }
            if (
                compatibleConfig.associatedDataVersion >= 1 &&
                manifest.vaultId != compatibleConfig.vaultId
            ) {
                return@withContext VaultLoadResult.FileCorrupted to null
            }
            if (!verifyCompatibleConfigBinding(
                    manifest = manifest,
                    config = compatibleConfig,
                    allowMissing = allowMissingAuthenticatedMetadata,
                )
            ) {
                return@withContext VaultLoadResult.IntegrityCheckFailed to null
            }
            if (!verifyCompatibleSnapshotMetadata(
                    manifest = manifest,
                    directory = directory,
                    allowMissing = allowMissingAuthenticatedMetadata,
                )
            ) {
                return@withContext VaultLoadResult.IntegrityCheckFailed to null
            }

            val passwords = decryptImportedFile(
                File(directory, PASSWORDS_DATA_FILE),
                streamingAead,
                encryptionContext.associatedData(PASSWORDS_DATA_FILE),
            ) { input ->
                parseProtobufWithLimits(input, MAX_PASSWORDS_PLAINTEXT_BYTES) {
                    PasswordEntries.parseFrom(it)
                }
            }
            require(passwords.entriesCount <= MAX_PASSWORD_ENTRY_COUNT) {
                "Legacy vault contains too many password entries"
            }
            val passwordSchemaSupported = if (allowMissingAuthenticatedMetadata) {
                passwords.schemaVersion in 0..CURRENT_SCHEMA_VERSION
            } else {
                isSchemaVersionSupported(passwords.schemaVersion)
            }
            if (!passwordSchemaSupported) {
                return@withContext VaultLoadResult.UnsupportedVersion(passwords.schemaVersion) to null
            }

            val categories = decryptImportedFile(
                File(directory, CATEGORIES_DATA_FILE),
                streamingAead,
                encryptionContext.associatedData(CATEGORIES_DATA_FILE),
            ) { input ->
                parseProtobufWithLimits(input, MAX_CATEGORIES_PLAINTEXT_BYTES) {
                    Categories.parseFrom(it)
                }
            }
            require(categories.categoriesCount <= MAX_CATEGORY_COUNT) {
                "Legacy vault contains too many categories"
            }
            val categorySchemaSupported = if (allowMissingAuthenticatedMetadata) {
                categories.schemaVersion in 0..CURRENT_SCHEMA_VERSION
            } else {
                isSchemaVersionSupported(categories.schemaVersion)
            }
            if (!categorySchemaSupported) {
                return@withContext VaultLoadResult.UnsupportedVersion(categories.schemaVersion) to null
            }

            val totpFile = File(directory, TOTP_DATA_FILE)
            val totpEntries = if (totpFile.isFile) {
                val totp = decryptImportedFile(
                    totpFile,
                    streamingAead,
                    encryptionContext.associatedData(TOTP_DATA_FILE),
                ) { input ->
                    parseProtobufWithLimits(input, MAX_TOTP_PLAINTEXT_BYTES) {
                        TotpEntries.parseFrom(it)
                    }
                }
                require(totp.entriesCount <= MAX_TOTP_ENTRY_COUNT) {
                    "Legacy vault contains too many TOTP entries"
                }
                val totpSchemaSupported = if (allowMissingAuthenticatedMetadata) {
                    totp.schemaVersion in 0..CURRENT_SCHEMA_VERSION
                } else {
                    isSchemaVersionSupported(totp.schemaVersion)
                }
                if (!totpSchemaSupported) {
                    return@withContext VaultLoadResult.UnsupportedVersion(totp.schemaVersion) to null
                }
                totp.entriesList
            } else {
                emptyList()
            }

            VaultSemanticValidator.validateCore(
                passwords = passwords.entriesList,
                categories = categories.categoriesList,
                attachments = manifest.attachments,
            )
            VaultSemanticValidator.validateTotpEntries(totpEntries)
            VaultLoadResult.Success to LoadedLegacyV2Data(
                manifest = manifest,
                passwords = passwords.entriesList,
                categories = categories.categoriesList,
                totpEntries = totpEntries,
                hasTotpFile = totpFile.isFile,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            loggerE(error)
            VaultLoadResult.FileCorrupted to null
        }
    }

    /**
     * 已认证的旧 V2 先完整写入同文件系统的暂存目录，再创建自动备份并原子替换目录。
     * 任一步失败都会保留原保险库，绝不在旧目录上逐个覆盖文件。
     */
    private suspend fun upgradeLegacyV2Vault(
        masterPassword: String,
        loadSession: LoadSession,
        compatibleConfig: CompatibleVaultConfig,
        keyset: KeysetHandle,
        legacyData: LoadedLegacyV2Data,
    ): PreparedVaultUpgrade = saveMutex.withLock {
        val stagedDirectory = File(
            context.filesDir,
            ".$VAULT_DIRECTORY_NAME.migration-${UUID.randomUUID()}",
        )
        try {
            val prepared = createUpgradedLegacyVault(
                sourceDirectory = vaultDir,
                targetDirectory = stagedDirectory,
                masterPassword = masterPassword,
                compatibleConfig = compatibleConfig,
                keyset = keyset,
                legacyData = legacyData,
            )
            currentCoroutineContext().ensureActive()
            require(sessionGuard.publishIfValid(loadSession.guardToken) {}) {
                "Vault upgrade session is no longer active"
            }

            // 自动备份必须先可靠落盘；失败时不允许开始目录切换。
            val migrationBackup = requireNotNull(backupCurrentVault()) {
                "Failed to back up the legacy V2 vault"
            }
            currentCoroutineContext().ensureActive()
            require(sessionGuard.publishIfValid(loadSession.guardToken) {}) {
                "Vault upgrade session is no longer active"
            }
            runVaultMutationForSession(loadSession.repositoryEpoch) {
                VaultFileTransaction.replaceDirectory(vaultDir, stagedDirectory)
            }
            // 只有新目录成功激活后才执行保留策略，失败路径不能顺带删除既有历史备份。
            pruneAutomaticBackups(migrationBackup)
            recoveryFailed = false
            prepared.copy(directory = vaultDir)
        } finally {
            stagedDirectory.deleteRecursively()
        }
    }

    /**
     * 将已经完整认证的旧 V2 内容标准化为当前格式。该方法只写 [targetDirectory]，
     * 可同时复用于本地自动升级和旧备份导入。
     */
    private suspend fun createUpgradedLegacyVault(
        sourceDirectory: File,
        targetDirectory: File,
        masterPassword: String,
        compatibleConfig: CompatibleVaultConfig,
        keyset: KeysetHandle,
        legacyData: LoadedLegacyV2Data,
    ): PreparedVaultUpgrade {
        require(!targetDirectory.exists() || targetDirectory.list().isNullOrEmpty()) {
            "Vault upgrade target must be empty"
        }
        require(targetDirectory.mkdirs() || targetDirectory.isDirectory) {
            "Cannot create vault upgrade directory"
        }
        val targetAttachments = File(targetDirectory, ATTACHMENTS_DIR)
        require(targetAttachments.mkdirs() || targetAttachments.isDirectory) {
            "Cannot create upgraded attachment directory"
        }

        val newSalt = tinkCryptoManager.generateSalt()
        val newVaultId = UUID.randomUUID().toString()
        val encryptedKeyset = tinkCryptoManager.encryptKeyset(
            keysetHandle = keyset,
            password = masterPassword,
            salt = newSalt,
            iterations = TinkCryptoManager.DEFAULT_KDF_ITERATIONS,
        )
        val configWithoutHash = VaultConfig(
            version = VAULT_VERSION_V2,
            salt = Base64.encodeToString(newSalt, Base64.NO_WRAP),
            encryptedKeyset = encryptedKeyset,
            integrityHash = null,
            passwordHint = compatibleConfig.passwordHint,
            kdfAlgorithm = TinkCryptoManager.PBKDF2_ALGORITHM,
            kdfIterations = TinkCryptoManager.DEFAULT_KDF_ITERATIONS,
            vaultId = newVaultId,
            associatedDataVersion = 1,
        )
        val streamingAead = tinkCryptoManager.getStreamingAead(keyset)
        val oldContext = VaultEncryptionContext(
            keysetHandle = keyset,
            vaultId = compatibleConfig.vaultId,
            associatedDataVersion = compatibleConfig.associatedDataVersion,
        )
        val newContext = VaultEncryptionContext(keyset, newVaultId)

        writeEncryptedMessage(
            File(targetDirectory, PASSWORDS_DATA_FILE),
            PasswordEntries.newBuilder()
                .setSchemaVersion(CURRENT_SCHEMA_VERSION)
                .addAllEntries(legacyData.passwords)
                .build(),
            streamingAead,
            newContext.associatedData(PASSWORDS_DATA_FILE),
        )
        writeEncryptedMessage(
            File(targetDirectory, CATEGORIES_DATA_FILE),
            Categories.newBuilder()
                .setSchemaVersion(CURRENT_SCHEMA_VERSION)
                .addAllCategories(legacyData.categories)
                .build(),
            streamingAead,
            newContext.associatedData(CATEGORIES_DATA_FILE),
        )
        if (legacyData.hasTotpFile) {
            writeEncryptedMessage(
                File(targetDirectory, TOTP_DATA_FILE),
                TotpEntries.newBuilder()
                    .setSchemaVersion(CURRENT_SCHEMA_VERSION)
                    .addAllEntries(legacyData.totpEntries)
                    .build(),
                streamingAead,
                newContext.associatedData(TOTP_DATA_FILE),
            )
        }

        legacyData.manifest.attachments.forEach { attachment ->
            val source = safeVaultAttachmentFile(sourceDirectory, attachment.encryptedFileName)
            require(source.isFile) { "Legacy vault attachment is missing" }
            reencryptVaultFile(
                source = source,
                target = safeVaultAttachmentFile(targetDirectory, attachment.encryptedFileName),
                streamingAead = streamingAead,
                oldAssociatedData = oldContext.associatedData("attachment:${attachment.id}"),
                newAssociatedData = newContext.associatedData("attachment:${attachment.id}"),
                maxPlaintextBytes = AppConstants.MAX_ATTACHMENT_SIZE_BYTES,
            )

            val thumbnailName = "${attachment.id}_thumb.dat"
            val sourceThumbnail = safeVaultAttachmentFile(sourceDirectory, thumbnailName)
            if (sourceThumbnail.isFile) {
                reencryptVaultFile(
                    source = sourceThumbnail,
                    target = safeVaultAttachmentFile(targetDirectory, thumbnailName),
                    streamingAead = streamingAead,
                    oldAssociatedData = oldContext.associatedData(
                        "attachment-thumbnail:${attachment.id}"
                    ),
                    newAssociatedData = newContext.associatedData(
                        "attachment-thumbnail:${attachment.id}"
                    ),
                    maxPlaintextBytes = MAX_THUMBNAIL_PLAINTEXT_BYTES,
                )
            }
        }

        val snapshotFiles = mutableMapOf(
            PASSWORDS_DATA_FILE to File(targetDirectory, PASSWORDS_DATA_FILE),
            CATEGORIES_DATA_FILE to File(targetDirectory, CATEGORIES_DATA_FILE),
        )
        File(targetDirectory, TOTP_DATA_FILE).takeIf(File::isFile)?.let {
            snapshotFiles[TOTP_DATA_FILE] = it
        }
        val snapshotMetadata = calculateSnapshotMetadata(snapshotFiles)
        val nextGeneration = legacyData.manifest.generation.let { generation ->
            if (generation >= 0L && generation < Long.MAX_VALUE) generation + 1L else 1L
        }
        val newManifest = VaultManifestV2(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            vaultId = newVaultId,
            generation = nextGeneration,
            createdAt = legacyData.manifest.createdAt.takeIf { it > 0L }
                ?: System.currentTimeMillis(),
            attachments = legacyData.manifest.attachments,
            fileDigests = snapshotMetadata.digests,
            fileSizes = snapshotMetadata.sizes,
            configBinding = calculateConfigBinding(configWithoutHash),
        )
        writeEncryptedManifest(
            File(targetDirectory, MANIFEST_DATA_FILE),
            newManifest,
            streamingAead,
            newContext.associatedData(MANIFEST_DATA_FILE),
        )
        val completedConfig = configWithoutHash.copy(
            integrityHash = calculateVaultIntegrityHash(targetDirectory)
                ?: error("Failed to hash upgraded V2 vault")
        )
        writePlainTextSynced(
            File(targetDirectory, VAULT_CONFIG_FILE_NAME),
            Json.encodeToString(completedConfig),
        )

        // 切换目录前再次从新盐和 600k KDF 开始验证全部新密文及附件。
        require(
            verifyVaultIntegrity(
                directory = targetDirectory,
                expectedHash = completedConfig.integrityHash,
                allowLegacyFormat = false,
            )
        ) { "Upgraded V2 vault failed its integrity check" }
        validateImportedVaultCryptographically(targetDirectory, completedConfig, masterPassword)
        return PreparedVaultUpgrade(targetDirectory, completedConfig, newManifest)
    }

    private suspend fun reencryptVaultFile(
        source: File,
        target: File,
        streamingAead: StreamingAead,
        oldAssociatedData: ByteArray,
        newAssociatedData: ByteArray,
        maxPlaintextBytes: Long,
    ) {
        val operationJob = currentCoroutineContext()[Job]
        decryptImportedFile(source, streamingAead, oldAssociatedData) { plainInput ->
            writeEncryptedStream(target, streamingAead, newAssociatedData) { encryptedOutput ->
                copyWithLimit(plainInput, encryptedOutput, maxPlaintextBytes) {
                    operationJob?.ensureActive()
                }
            }
        }
    }

    private suspend fun updateAndSaveChanges() {
        persistVaultState(_passwordEntries.value, _categories.value, _attachments.value)
    }

    private suspend fun persistVaultState(
        passwords: List<PasswordEntry>,
        categories: List<Category>,
        attachments: List<AttachmentManifestEntry>
    ) = withContext(Dispatchers.IO) {
        VaultSemanticValidator.validateCore(passwords, categories, attachments)
        saveMutex.withLock {
            VaultFileTransaction.recover(vaultDir)
            val writeSession = captureActiveVaultWriteSession()
                ?: throw CancellationException("Vault is locked")
            // 旧 schema 第一次升级写入前保留完整备份，便于使用旧版本回退恢复。
            val schemaUpgradeBackup = if (
                writeSession.manifest.schemaVersion < CURRENT_SCHEMA_VERSION
            ) {
                requireNotNull(backupCurrentVault()) {
                    "Failed to back up the vault before schema upgrade"
                }
            } else {
                null
            }
            val config = writeSession.config
            val keyset = writeSession.keyset
            val streamingAead = tinkCryptoManager.getStreamingAead(keyset)
            val encryptionContext = VaultEncryptionContext(keyset, config.vaultId)

            val passwordTarget = File(vaultDir, PASSWORDS_DATA_FILE)
            val categoryTarget = File(vaultDir, CATEGORIES_DATA_FILE)
            val manifestTarget = File(vaultDir, MANIFEST_DATA_FILE)
            val configTarget = File(vaultDir, VAULT_CONFIG_FILE_NAME)
            val passwordPending = VaultFileTransaction.createPendingFile(passwordTarget)
            val categoryPending = VaultFileTransaction.createPendingFile(categoryTarget)
            val manifestPending = VaultFileTransaction.createPendingFile(manifestTarget)
            val configPending = VaultFileTransaction.createPendingFile(configTarget)

            try {
                writeEncryptedMessage(
                    passwordPending,
                    PasswordEntries.newBuilder()
                        .setSchemaVersion(CURRENT_SCHEMA_VERSION)
                        .addAllEntries(passwords)
                        .build(),
                    streamingAead,
                    encryptionContext.associatedData(PASSWORDS_DATA_FILE)
                )
                writeEncryptedMessage(
                    categoryPending,
                    Categories.newBuilder()
                        .setSchemaVersion(CURRENT_SCHEMA_VERSION)
                        .addAllCategories(categories)
                        .build(),
                    streamingAead,
                    encryptionContext.associatedData(CATEGORIES_DATA_FILE)
                )
                val snapshotFiles = mutableMapOf(
                    PASSWORDS_DATA_FILE to passwordPending,
                    CATEGORIES_DATA_FILE to categoryPending,
                )
                File(vaultDir, TOTP_DATA_FILE).takeIf(File::isFile)?.let {
                    snapshotFiles[TOTP_DATA_FILE] = it
                }
                val snapshotMetadata = calculateSnapshotMetadata(snapshotFiles)
                val newManifest = writeSession.manifest.copy(
                    schemaVersion = CURRENT_SCHEMA_VERSION,
                    vaultId = config.vaultId,
                    generation = writeSession.manifest.generation + 1,
                    attachments = attachments,
                    fileDigests = snapshotMetadata.digests,
                    fileSizes = snapshotMetadata.sizes,
                    configBinding = calculateConfigBinding(config),
                )
                writeEncryptedManifest(
                    manifestPending,
                    newManifest,
                    streamingAead,
                    encryptionContext.associatedData(MANIFEST_DATA_FILE)
                )

                val updatedConfig = config.copy(
                    integrityHash = calculateVaultIntegrityHash(
                        mapOf(
                            PASSWORDS_DATA_FILE to passwordPending,
                            CATEGORIES_DATA_FILE to categoryPending,
                            MANIFEST_DATA_FILE to manifestPending
                        )
                    ) ?: error("Failed to calculate vault hash")
                )
                writePlainTextSynced(configPending, Json.encodeToString(updatedConfig))

                currentCoroutineContext().ensureActive()
                commitVaultTransactionForSession(
                    expectedEpoch = writeSession.epoch,
                    expectedKeyset = keyset,
                    writes = listOf(
                        VaultFileTransaction.PendingWrite(passwordTarget, passwordPending),
                        VaultFileTransaction.PendingWrite(categoryTarget, categoryPending),
                        VaultFileTransaction.PendingWrite(manifestTarget, manifestPending),
                        VaultFileTransaction.PendingWrite(configTarget, configPending)
                    ),
                    onCommitted = {
                        // 磁盘提交和明文状态发布共享同一个会话临界区。
                        _passwordEntries.value = passwords
                        _categories.value = categories
                        _attachments.value = attachments
                        vaultManifestV2 = newManifest
                        vaultConfig = updatedConfig
                    },
                )
                schemaUpgradeBackup?.let(::pruneAutomaticBackups)
            } finally {
                listOf(passwordPending, categoryPending, manifestPending, configPending).forEach(File::delete)
            }
        }
    }

    /**
     * TOTP 与加密清单、公开配置共享同一个 saveMutex 和文件事务，
     * 防止导出或崩溃恢复时看到跨代快照。
     */
    suspend fun persistTotpEntries(entries: List<TotpEntry>) = withContext(Dispatchers.IO) {
        require(entries.size <= MAX_TOTP_ENTRY_COUNT) { "Vault contains too many TOTP entries" }
        VaultSemanticValidator.validateTotpEntries(entries)
        saveMutex.withLock {
            VaultFileTransaction.recover(vaultDir)
            val writeSession = captureActiveVaultWriteSession()
                ?: throw CancellationException("Vault is locked")
            val schemaUpgradeBackup = if (
                writeSession.manifest.schemaVersion < CURRENT_SCHEMA_VERSION
            ) {
                requireNotNull(backupCurrentVault()) {
                    "Failed to back up the vault before schema upgrade"
                }
            } else {
                null
            }
            val config = writeSession.config
            val keyset = writeSession.keyset
            val currentManifest = writeSession.manifest
            val streamingAead = tinkCryptoManager.getStreamingAead(keyset)
            val encryptionContext = VaultEncryptionContext(
                keyset,
                config.vaultId,
            )

            val totpTarget = File(vaultDir, TOTP_DATA_FILE)
            val manifestTarget = File(vaultDir, MANIFEST_DATA_FILE)
            val configTarget = File(vaultDir, VAULT_CONFIG_FILE_NAME)
            val totpPending = VaultFileTransaction.createPendingFile(totpTarget)
            val manifestPending = VaultFileTransaction.createPendingFile(manifestTarget)
            val configPending = VaultFileTransaction.createPendingFile(configTarget)
            try {
                writeEncryptedMessage(
                    totpPending,
                    TotpEntries.newBuilder()
                        .setSchemaVersion(CURRENT_SCHEMA_VERSION)
                        .addAllEntries(entries)
                        .build(),
                    streamingAead,
                    encryptionContext.associatedData(TOTP_DATA_FILE),
                )
                val snapshotMetadata = calculateSnapshotMetadata(
                    mapOf(
                        PASSWORDS_DATA_FILE to File(vaultDir, PASSWORDS_DATA_FILE),
                        CATEGORIES_DATA_FILE to File(vaultDir, CATEGORIES_DATA_FILE),
                        TOTP_DATA_FILE to totpPending,
                    )
                )
                val newManifest = currentManifest.copy(
                    schemaVersion = CURRENT_SCHEMA_VERSION,
                    vaultId = config.vaultId,
                    generation = currentManifest.generation + 1,
                    fileDigests = snapshotMetadata.digests,
                    fileSizes = snapshotMetadata.sizes,
                    configBinding = calculateConfigBinding(config),
                )
                writeEncryptedManifest(
                    manifestPending,
                    newManifest,
                    streamingAead,
                    encryptionContext.associatedData(MANIFEST_DATA_FILE),
                )
                val updatedConfig = config.copy(
                    integrityHash = calculateVaultIntegrityHash(
                        mapOf(
                            PASSWORDS_DATA_FILE to File(vaultDir, PASSWORDS_DATA_FILE),
                            CATEGORIES_DATA_FILE to File(vaultDir, CATEGORIES_DATA_FILE),
                            MANIFEST_DATA_FILE to manifestPending,
                        )
                    ) ?: error("Failed to calculate TOTP snapshot hash")
                )
                writePlainTextSynced(configPending, Json.encodeToString(updatedConfig))
                currentCoroutineContext().ensureActive()
                commitVaultTransactionForSession(
                    expectedEpoch = writeSession.epoch,
                    expectedKeyset = keyset,
                    writes = listOf(
                        VaultFileTransaction.PendingWrite(totpTarget, totpPending),
                        VaultFileTransaction.PendingWrite(manifestTarget, manifestPending),
                        VaultFileTransaction.PendingWrite(configTarget, configPending),
                    ),
                    onCommitted = {
                        vaultManifestV2 = newManifest
                        vaultConfig = updatedConfig
                    },
                )
                schemaUpgradeBackup?.let(::pruneAutomaticBackups)
            } finally {
                listOf(totpPending, manifestPending, configPending).forEach(File::delete)
            }
        }
    }

    private fun captureLoadSession(): LoadSession {
        val guardToken = sessionGuard.capture()
        return synchronized(vaultSessionLock) {
            LoadSession(guardToken = guardToken, repositoryEpoch = sessionEpoch)
        }
    }

    private fun publishLoadedV2(
        loadSession: LoadSession,
        config: VaultConfig,
        keyset: KeysetHandle,
        data: LoadedVaultData,
    ): Boolean {
        var didPublish = false
        val guardAccepted = sessionGuard.publishIfValid(loadSession.guardToken) {
            synchronized(vaultSessionLock) {
                if (sessionEpoch == loadSession.repositoryEpoch) {
                    vaultConfig = config
                    streamingAeadKeysetHandle = keyset
                    vaultManifestV2 = data.manifest
                    _passwordEntries.value = data.passwords
                    _categories.value = data.categories
                    _attachments.value = data.manifest.attachments
                    didPublish = true
                }
            }
        }
        return guardAccepted && didPublish
    }

    private fun isLoadSessionCurrent(loadSession: LoadSession, keyset: KeysetHandle): Boolean {
        var isCurrent = false
        val guardAccepted = sessionGuard.publishIfValid(loadSession.guardToken) {
            synchronized(vaultSessionLock) {
                isCurrent = sessionEpoch == loadSession.repositoryEpoch &&
                    streamingAeadKeysetHandle === keyset
            }
        }
        return guardAccepted && isCurrent
    }


    fun lock() {
        // 先让所有正在执行的解锁令牌失效，再清除内存，杜绝晚到结果重新发布。
        sessionGuard.invalidate()
        vaultSessionCommitGate.withLock {
            sessionEpoch++
            streamingAeadKeysetHandle = null
            vaultConfig = null
            vaultManifestV2 = null
            clearAttachmentCacheLocked()
        }
        // Common
        _passwordEntries.value = emptyList()
        _categories.value = emptyList()
        _attachments.value = emptyList()
    }

    private fun writeEncryptedMessage(
        target: File,
        message: com.google.protobuf.MessageLite,
        streamingAead: StreamingAead,
        associatedData: ByteArray
    ) {
        writeEncryptedStream(target, streamingAead, associatedData) { encryptedOutput ->
            message.writeTo(encryptedOutput)
        }
    }

    private fun writeEncryptedManifest(
        target: File,
        manifest: VaultManifestV2,
        streamingAead: StreamingAead,
        associatedData: ByteArray
    ) {
        writeEncryptedStream(target, streamingAead, associatedData) { encryptedOutput ->
            encryptedOutput.write(Json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
        }
    }

    private fun writeEncryptedStream(
        target: File,
        streamingAead: StreamingAead,
        associatedData: ByteArray,
        write: (OutputStream) -> Unit
    ) {
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { fileOutput ->
            val nonClosingOutput = object : FilterOutputStream(fileOutput) {
                override fun close() {
                    flush()
                }
            }
            streamingAead.newEncryptingStream(nonClosingOutput, associatedData).use(write)
            fileOutput.fd.sync()
        }
    }

    private fun writePlainTextSynced(target: File, content: String) {
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }

    /**
     * 流式读取 Manifest (JSON)
     * 使用 StreamingAead 解密 manifest.dat
     */
    private fun loadManifestV2FromStream(
        streamingAead: StreamingAead,
        encryptionContext: VaultEncryptionContext
    ): VaultManifestV2? {
        val file = File(vaultDir, MANIFEST_DATA_FILE)
        if (!file.exists()) return null
        return try {
            FileInputStream(file).use { fis ->
                streamingAead.newDecryptingStream(
                    fis,
                    encryptionContext.associatedData(MANIFEST_DATA_FILE)
                ).use { decStream ->
                    val jsonString = readUtf8WithLimit(decStream, MAX_MANIFEST_PLAINTEXT_BYTES)
                    Json.decodeFromString<VaultManifestV2>(jsonString)
                }
            }
        } catch (e: Exception) {
            loggerE(e)
            null
        }
    }

    private fun validateVaultConfig(config: VaultConfig): VaultLoadResult? {
        if (config.version != VAULT_VERSION_V2) return VaultLoadResult.UnsupportedVersion(config.version)
        if (config.kdfAlgorithm != TinkCryptoManager.PBKDF2_ALGORITHM) {
            return VaultLoadResult.FileCorrupted
        }
        if (config.kdfIterations !in TinkCryptoManager.DEFAULT_KDF_ITERATIONS..5_000_000) {
            return VaultLoadResult.FileCorrupted
        }
        if (config.associatedDataVersion != 1 || config.vaultId.isBlank()) {
            return VaultLoadResult.FileCorrupted
        }
        if (config.integrityHash.isNullOrBlank()) {
            return VaultLoadResult.FileCorrupted
        }
        return null
    }

    private fun validateCompatibleVaultConfig(
        config: CompatibleVaultConfig,
    ): VaultLoadResult? {
        if (config.version != VAULT_VERSION_V2) {
            return VaultLoadResult.UnsupportedVersion(config.version)
        }
        if (config.kdfAlgorithm != TinkCryptoManager.PBKDF2_ALGORITHM) {
            return VaultLoadResult.FileCorrupted
        }
        if (!tinkCryptoManager.isKdfIterationCountSupportedForDecryption(config.kdfIterations)) {
            return VaultLoadResult.FileCorrupted
        }
        if (config.associatedDataVersion !in 0..1) {
            return VaultLoadResult.FileCorrupted
        }
        if (config.associatedDataVersion == 1 && config.vaultId.isBlank()) {
            return VaultLoadResult.FileCorrupted
        }
        return null
    }

    private fun isSchemaVersionSupported(schemaVersion: Int): Boolean =
        schemaVersion in MIN_SUPPORTED_SCHEMA_VERSION..CURRENT_SCHEMA_VERSION

    suspend fun addEntry(
        title: String,
        username: String,
        plainTextPassword: String,
        notes: String,
        categoryIds: List<String> = emptyList(),
        attachmentIds: List<String> = emptyList(),
        customFields: List<CustomField> = emptyList(),
    ) = stateMutationMutex.withLock {
        require(title.isNotBlank()) { "Password title must not be blank" }
        require(plainTextPassword.isNotBlank()) { "Password value must not be blank" }
        val currentTime = System.currentTimeMillis()
        val newEntry = PasswordEntry.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTitle(title)
            .setUsername(username)
            .setPassword(plainTextPassword)
            .setNotes(notes)
            .setType(VaultItemType.PASSWORD)  // 显式设置为密码类型
            .addAllCategoryIds(categoryIds)
            .addAllAttachmentIds(attachmentIds)
            .addAllCustomFields(customFields)
            .setCreatedAt(currentTime)
            .setUpdatedAt(currentTime)
            .build()
        persistVaultState(_passwordEntries.value + newEntry, _categories.value, _attachments.value)
    }

    suspend fun updateEntry(updatedEntry: PasswordEntry) = stateMutationMutex.withLock {
        requireValidEntry(updatedEntry)
        val currentList = _passwordEntries.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == updatedEntry.id }
        require(index != -1) { "Password entry does not exist" }
        currentList[index] = updatedEntry.toBuilder().setUpdatedAt(System.currentTimeMillis()).build()
        persistVaultState(currentList, _categories.value, _attachments.value)
    }

    suspend fun deleteEntry(entryId: String) = stateMutationMutex.withLock {
        persistVaultState(
            _passwordEntries.value.filterNot { it.id == entryId },
            _categories.value,
            _attachments.value
        )
    }

    /**
     * 添加安全笔记
     * @param title 笔记标题（必填）
     * @param content 笔记内容（可选）
     * @param categoryIds 分类ID列表
     * @param attachmentIds 附件ID列表
     * @param customFields 有序自定义字段列表
     */
    suspend fun addSecureNote(
        title: String,
        content: String,
        categoryIds: List<String> = emptyList(),
        attachmentIds: List<String> = emptyList(),
        customFields: List<CustomField> = emptyList(),
    ) = stateMutationMutex.withLock {
        require(title.isNotBlank()) { "Secure note title must not be blank" }
        val currentTime = System.currentTimeMillis()
        val newNote = PasswordEntry.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTitle(title)
            .setContent(content)
            .setType(VaultItemType.NOTE)  // 设置为笔记类型
            .addAllCategoryIds(categoryIds)
            .addAllAttachmentIds(attachmentIds)
            .addAllCustomFields(customFields)
            .setCreatedAt(currentTime)
            .setUpdatedAt(currentTime)
            .build()
        persistVaultState(_passwordEntries.value + newNote, _categories.value, _attachments.value)
    }

    /**
     * 添加支付卡。卡号和安全码只会进入已加密的 passwords.dat，绝不写入公开配置或清单。
     */
    suspend fun addPaymentCard(
        title: String,
        cardholderName: String,
        cardNumber: String,
        cardBrand: PaymentCardBrand,
        expirationMonth: Int,
        expirationYear: Int,
        securityCode: String,
        notes: String,
        categoryIds: List<String> = emptyList(),
        attachmentIds: List<String> = emptyList(),
        customFields: List<CustomField> = emptyList(),
    ) = stateMutationMutex.withLock {
        val normalizedCardNumber = cardNumber.toAsciiDecimalDigits()
        val normalizedSecurityCode = securityCode.toAsciiDecimalDigits()
        require(title.isNotBlank()) { "Payment card title must not be blank" }
        require(
            normalizedCardNumber.length in
                AppConstants.MIN_PAYMENT_CARD_NUMBER_LENGTH..AppConstants.MAX_PAYMENT_CARD_NUMBER_LENGTH
        ) { "Payment card number has an invalid length" }
        require(
            cardNumber.all {
                it.digitToIntOrNull() != null || it.isWhitespace() || it == '-'
            }
        ) {
            "Payment card number contains unsupported characters"
        }
        require(cardholderName.length <= AppConstants.MAX_CARDHOLDER_NAME_LENGTH) {
            "Payment card holder name is too long"
        }
        require(
            expirationMonth == 0 || expirationMonth in 1..12
        ) { "Payment card expiration month is invalid" }
        require(
            expirationYear == 0 || expirationYear in
                AppConstants.MIN_EXPIRATION_YEAR..AppConstants.MAX_EXPIRATION_YEAR
        ) { "Payment card expiration year is invalid" }
        require((expirationMonth == 0) == (expirationYear == 0)) {
            "Payment card expiration month and year must be set together"
        }
        require(
            normalizedSecurityCode.isEmpty() || normalizedSecurityCode.length in
                AppConstants.MIN_SECURITY_CODE_LENGTH..AppConstants.MAX_SECURITY_CODE_LENGTH
        ) { "Payment card security code has an invalid length" }
        require(securityCode.all { it.digitToIntOrNull() != null }) {
            "Payment card security code contains unsupported characters"
        }
        require(cardBrand != PaymentCardBrand.UNRECOGNIZED) {
            "Payment card brand is unsupported"
        }

        val currentTime = System.currentTimeMillis()
        val newCard = PasswordEntry.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setTitle(title)
            .setType(VaultItemType.PAYMENT_CARD)
            .setCardholderName(cardholderName)
            .setCardNumber(normalizedCardNumber)
            .setCardBrand(cardBrand)
            .setExpirationMonth(expirationMonth)
            .setExpirationYear(expirationYear)
            .setSecurityCode(normalizedSecurityCode)
            .setNotes(notes)
            .addAllCategoryIds(categoryIds)
            .addAllAttachmentIds(attachmentIds)
            .addAllCustomFields(customFields)
            .setCreatedAt(currentTime)
            .setUpdatedAt(currentTime)
            .build()
        persistVaultState(_passwordEntries.value + newCard, _categories.value, _attachments.value)
    }

    private fun requireValidEntry(entry: PasswordEntry) {
        require(entry.title.isNotBlank()) { "Vault entry title must not be blank" }
        when (entry.type) {
            VaultItemType.PASSWORD -> {
                require(entry.password.isNotBlank()) { "Password value must not be blank" }
            }
            VaultItemType.PAYMENT_CARD -> {
                require(
                    entry.cardNumber.length in
                        AppConstants.MIN_PAYMENT_CARD_NUMBER_LENGTH..AppConstants.MAX_PAYMENT_CARD_NUMBER_LENGTH &&
                        entry.cardNumber.all { it in '0'..'9' }
                ) { "Payment card number is invalid" }
                require(entry.cardholderName.length <= AppConstants.MAX_CARDHOLDER_NAME_LENGTH) {
                    "Payment card holder name is too long"
                }
                require(entry.expirationMonth == 0 || entry.expirationMonth in 1..12) {
                    "Payment card expiration month is invalid"
                }
                require(
                    entry.expirationYear == 0 || entry.expirationYear in
                        AppConstants.MIN_EXPIRATION_YEAR..AppConstants.MAX_EXPIRATION_YEAR
                ) { "Payment card expiration year is invalid" }
                require((entry.expirationMonth == 0) == (entry.expirationYear == 0)) {
                    "Payment card expiration month and year must be set together"
                }
                require(
                    entry.securityCode.isEmpty() ||
                        entry.securityCode.length in
                            AppConstants.MIN_SECURITY_CODE_LENGTH..AppConstants.MAX_SECURITY_CODE_LENGTH
                ) { "Payment card security code is invalid" }
                require(entry.securityCode.all { it in '0'..'9' }) {
                    "Payment card security code is invalid"
                }
                require(entry.cardBrand != PaymentCardBrand.UNRECOGNIZED) {
                    "Payment card brand is unsupported"
                }
            }
            VaultItemType.NOTE -> Unit
            VaultItemType.UNRECOGNIZED -> error("Unsupported vault item type")
        }
    }

    private fun createDefaultCategories(): List<Category> {
        val currentTime = System.currentTimeMillis()
        return listOf(
            Category.newBuilder().setId(AppConstants.CATEGORY_ID_ALL).setName("All").setColor("#2196F3").setCreatedAt(currentTime).build(),
            Category.newBuilder().setId(AppConstants.CATEGORY_ID_UNCATEGORIZED).setName("Uncategorized").setColor("#9E9E9E").setCreatedAt(currentTime).build(),
            Category.newBuilder().setId(AppConstants.CATEGORY_ID_FAVORITES).setName("Favorites").setColor("#FF9800").setCreatedAt(currentTime).build()
        )
    }

    suspend fun addCategory(name: String, color: String): String = stateMutationMutex.withLock {
        val categoryId = UUID.randomUUID().toString()
        val newCategory = Category.newBuilder().setId(categoryId).setName(name).setColor(color).setCreatedAt(System.currentTimeMillis()).build()
        persistVaultState(_passwordEntries.value, _categories.value + newCategory, _attachments.value)
        return categoryId
    }

    suspend fun updateCategory(updatedCategory: Category) = stateMutationMutex.withLock {
        val currentList = _categories.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == updatedCategory.id }
        if (index != -1) {
            currentList[index] = updatedCategory
            persistVaultState(_passwordEntries.value, currentList, _attachments.value)
        }
    }

    suspend fun deleteCategory(categoryId: String) = stateMutationMutex.withLock {
        if (categoryId.startsWith("default_")) return@withLock

        val updatedCategories = _categories.value.filterNot { it.id == categoryId }
        val updatedEntries = _passwordEntries.value.map { entry ->
            if (entry.categoryIdsList.contains(categoryId)) {
                entry.toBuilder().clearCategoryIds().addAllCategoryIds(
                    entry.categoryIdsList.filter { it != categoryId }).build()
            } else {
                entry
            }
        }
        persistVaultState(updatedEntries, updatedCategories, _attachments.value)
    }

    suspend fun togglePasswordFavorite(passwordId: String) = stateMutationMutex.withLock {
        val entryIndex = _passwordEntries.value.indexOfFirst { it.id == passwordId }
        if (entryIndex == -1) return@withLock

        val entry = _passwordEntries.value[entryIndex]
        val isFavorited = entry.categoryIdsList.contains(AppConstants.CATEGORY_ID_FAVORITES)

        val updatedIds = if (isFavorited) {
            entry.categoryIdsList.filter { it != AppConstants.CATEGORY_ID_FAVORITES }
        } else {
            entry.categoryIdsList + AppConstants.CATEGORY_ID_FAVORITES
        }

        val updatedEntry = entry.toBuilder().clearCategoryIds().addAllCategoryIds(updatedIds).build()
        val currentEntries = _passwordEntries.value.toMutableList()
        currentEntries[entryIndex] = updatedEntry
        persistVaultState(currentEntries, _categories.value, _attachments.value)
    }

    fun getCategoryPasswordCount(categoryId: String): Int {
        val allPasswords = _passwordEntries.value
        return when (categoryId) {
            AppConstants.CATEGORY_ID_ALL -> allPasswords.size
            AppConstants.CATEGORY_ID_UNCATEGORIZED -> allPasswords.count { it.categoryIdsList.isEmpty() }
            else -> allPasswords.count { it.categoryIdsList.contains(categoryId) }
        }
    }

    /**
     * 创建并同步当前保险库备份，但不在这里执行保留策略。
     * 调用方必须等新保险库成功激活后再清理旧备份，确保失败路径不会造成历史备份丢失。
     */
    private suspend fun backupCurrentVault(): File? {
        if (!isVaultInitialized()) return null
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date())
        val backupFile = File(context.filesDir, "$BACKUP_FILE_PREFIX$timestamp$BACKUP_FILE_SUFFIX")
        try {
            FileOutputStream(backupFile).use { fileOutput ->
                val nonClosingOutput = object : FilterOutputStream(fileOutput) {
                    override fun close() {
                        flush()
                    }
                }
                zipVaultToOutputStream(nonClosingOutput)
                fileOutput.fd.sync()
            }
            return backupFile
        } catch (error: CancellationException) {
            backupFile.delete()
            throw error
        } catch (e: Exception) {
            backupFile.delete()
            throw e
        }
    }

    /**
     * 自动备份采用数量和总容量双限制，防止应用私有目录长期无界增长。
     */
    private fun pruneAutomaticBackups(protectedBackup: File) {
        try {
            val backupEntries = context.filesDir.listFiles { _, name ->
                name.startsWith(BACKUP_FILE_PREFIX) && name.endsWith(BACKUP_FILE_SUFFIX)
            }?.map(::BackupRetentionEntry).orEmpty()

            BackupRetentionPolicy.selectForDeletion(
                entries = backupEntries,
                protectedBackup = protectedBackup,
                maxCount = MAX_AUTOMATIC_BACKUP_COUNT,
                maxTotalBytes = MAX_AUTOMATIC_BACKUP_TOTAL_BYTES,
            ).forEach { backup ->
                if (!backup.delete()) {
                    loggerI("Automatic backup retention could not delete an expired backup")
                }
            }
        } catch (error: Exception) {
            // 清理失败不能让已经成功的保险库备份或后续导入失败。
            loggerE(error)
        }
    }

    @SuppressLint("UsableSpace")
    suspend fun importData(sourceUri: Uri, masterPassword: String): ImportDataFileResult = withContext(Dispatchers.IO) {
        val tempVaultDir = File(context.filesDir, ".$VAULT_DIRECTORY_NAME.import-${UUID.randomUUID()}")
        val upgradedImportDir = File(
            context.filesDir,
            ".$VAULT_DIRECTORY_NAME.migration-${UUID.randomUUID()}",
        )
        val importSessionToken = sessionGuard.capture()
        val importRepositoryEpoch = synchronized(vaultSessionLock) { sessionEpoch }
        try {
            saveMutex.withLock {
                require(masterPassword.isNotEmpty()) { "Master password is required" }
                tempVaultDir.mkdirs()
                val rootDirectory = tempVaultDir.canonicalFile
                val rootPathPrefix = rootDirectory.path + File.separator
                var entryCount = 0
                var totalExtracted = 0L
                val currentVaultBytes = if (vaultDir.exists()) {
                    vaultDir.walkTopDown().filter(File::isFile).sumOf(File::length)
                } else {
                    0L
                }
                val importDiskBudget = minOf(
                    MAX_IMPORT_UNCOMPRESSED_BYTES,
                    context.filesDir.usableSpace - currentVaultBytes - MIN_IMPORT_FREE_SPACE_RESERVE_BYTES
                )
                require(importDiskBudget > 0L) { "Not enough free space to import this vault" }

                context.contentResolver.openInputStream(sourceUri)?.use { fis ->
                    ZipInputStream(fis).use { zis ->
                        var zipEntry = zis.nextEntry
                        while (zipEntry != null) {
                            currentCoroutineContext().ensureActive()
                            entryCount++
                            require(entryCount <= MAX_IMPORT_ENTRY_COUNT) { "Import contains too many entries" }
                            require(!zipEntry.name.contains('\u0000')) { "Import contains an invalid path" }
                            require('\\' !in zipEntry.name) { "Import contains a non-portable path" }

                            val newFile = File(rootDirectory, zipEntry.name).canonicalFile
                            require(newFile.path.startsWith(rootPathPrefix)) {
                                "Import entry escaped the staging directory"
                            }
                            if (zipEntry.isDirectory) {
                                require(zipEntry.name.trimEnd('/') == ATTACHMENTS_DIR) {
                                    "Import contains an unexpected directory"
                                }
                                require(newFile.mkdirs() || newFile.isDirectory)
                            } else {
                                val entrySizeLimit = importEntrySizeLimit(zipEntry.name)
                                val remainingBudget = importDiskBudget - totalExtracted
                                require(remainingBudget > 0L) { "Import exceeds available storage" }
                                require(newFile.parentFile?.mkdirs() != false || newFile.parentFile?.isDirectory == true)
                                FileOutputStream(newFile).use { output ->
                                    val extracted = copyWithLimitCancellable(
                                        zis,
                                        output,
                                        minOf(entrySizeLimit, remainingBudget)
                                    )
                                    output.fd.sync()
                                    totalExtracted += extracted
                                    require(totalExtracted <= importDiskBudget) {
                                        "Import exceeds the uncompressed size limit"
                                    }
                                    if (zipEntry.compressedSize > 0L && extracted > 10L * 1024 * 1024) {
                                        require(extracted / zipEntry.compressedSize.coerceAtLeast(1L) <= 100L) {
                                            "Import entry has a suspicious compression ratio"
                                        }
                                    }
                                }
                            }
                            zis.closeEntry()
                            zipEntry = zis.nextEntry
                        }
                    }
                } ?: error("Cannot open import source")

                val compatibleConfig = validateImportedVault(tempVaultDir)
                val importedConfig = compatibleConfig.toVaultConfig()
                val requiresUpgrade = compatibleConfig.requiresOneTimeUpgrade()
                require(
                    verifyVaultIntegrity(
                        directory = tempVaultDir,
                        expectedHash = importedConfig.integrityHash,
                        allowLegacyFormat = requiresUpgrade,
                    )
                ) {
                    "Imported vault failed its integrity check"
                }

                // 在替换现有保险库前，必须用导入文件自己的主密码完成真实解密和认证校验。
                val directoryToActivate = if (requiresUpgrade) {
                    val salt = Base64.decode(importedConfig.salt, Base64.NO_WRAP)
                    val keyset = tinkCryptoManager.decryptKeyset(
                        importedConfig.encryptedKeyset,
                        masterPassword,
                        salt,
                        importedConfig.kdfIterations,
                    )
                    val (legacyResult, legacyData) = loadLegacyV2Data(
                        directory = tempVaultDir,
                        compatibleConfig = compatibleConfig,
                        keyset = keyset,
                        allowMissingAuthenticatedMetadata = compatibleConfig
                            .allowsLegacyEncryptedMetadata(),
                    )
                    require(legacyResult is VaultLoadResult.Success && legacyData != null) {
                        "Imported legacy V2 vault failed authentication"
                    }
                    try {
                        createUpgradedLegacyVault(
                            sourceDirectory = tempVaultDir,
                            targetDirectory = upgradedImportDir,
                            masterPassword = masterPassword,
                            compatibleConfig = compatibleConfig,
                            keyset = keyset,
                            legacyData = legacyData,
                        ).directory
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        throw VaultUpgradeException(error)
                    }
                } else {
                    require(validateVaultConfig(importedConfig) == null) {
                        "Imported vault config is unsupported"
                    }
                    validateImportedVaultCryptographically(
                        tempVaultDir,
                        importedConfig,
                        masterPassword,
                    )
                    tempVaultDir
                }

                // 备份失败则终止导入；旧保险库始终保留到新目录原子激活成功。
                currentCoroutineContext().ensureActive()
                require(sessionGuard.publishIfValid(importSessionToken) {}) {
                    "Import session is no longer active"
                }
                val currentVaultBackup = backupCurrentVault()
                currentCoroutineContext().ensureActive()
                require(sessionGuard.publishIfValid(importSessionToken) {}) {
                    "Import session is no longer active"
                }
                runVaultMutationForSession(importRepositoryEpoch) {
                    VaultFileTransaction.replaceDirectory(vaultDir, directoryToActivate)
                }
                // 导入成功前不清理任何旧备份，避免激活失败时丢失用户正在恢复的历史副本。
                currentVaultBackup?.let(::pruneAutomaticBackups)
                recoveryFailed = false

                val wasBiometricEnabled = isBiometricUnlockEnabled()
                disableBiometricUnlock()
                lock()
                if (!isVaultInitialized()) error("Failed to initialize vault after import")

                ImportDataFileResult(true, wasBiometricEnabled)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: VaultUpgradeException) {
            loggerE(error)
            ImportDataFileResult(
                success = false,
                biometricsWereDisabled = false,
                failureReason = ImportDataFailureReason.UPGRADE_FAILED,
            )
        } catch (e: Exception) {
            loggerE(e)
            ImportDataFileResult(
                success = false,
                biometricsWereDisabled = false,
                failureReason = ImportDataFailureReason.INVALID_BACKUP,
            )
        } finally {
            tempVaultDir.deleteRecursively()
            upgradedImportDir.deleteRecursively()
        }
    }

    private fun validateImportedVault(directory: File): CompatibleVaultConfig {
        val requiredFiles = listOf(
            VAULT_CONFIG_FILE_NAME,
            PASSWORDS_DATA_FILE,
            CATEGORIES_DATA_FILE,
            MANIFEST_DATA_FILE
        )
        require(requiredFiles.all { File(directory, it).isFile && File(directory, it).length() > 0L }) {
            "Imported vault is missing required files"
        }
        val config = decodeCompatibleVaultConfig(
            readUtf8FileWithLimit(File(directory, VAULT_CONFIG_FILE_NAME), MAX_VAULT_CONFIG_BYTES)
        )
        require(validateCompatibleVaultConfig(config) == null) {
            "Imported vault config is unsupported"
        }
        require(Base64.decode(config.salt, Base64.NO_WRAP).size == SALT_SIZE_BYTES) { "Invalid vault salt" }
        require(Base64.decode(config.encryptedKeyset, Base64.NO_WRAP).isNotEmpty()) { "Invalid encrypted keyset" }

        val attachmentsDir = File(directory, ATTACHMENTS_DIR)
        if (attachmentsDir.exists()) {
            require(attachmentsDir.isDirectory)
            attachmentsDir.walkTopDown().filter(File::isFile).forEach { file ->
                require(file.length() <= MAX_IMPORT_SINGLE_FILE_BYTES) { "Imported attachment is too large" }
            }
        }
        return config
    }

    private suspend fun validateImportedVaultCryptographically(
        directory: File,
        config: VaultConfig,
        masterPassword: String
    ) {
        val salt = Base64.decode(config.salt, Base64.NO_WRAP)
        val keyset = tinkCryptoManager.decryptKeyset(
            config.encryptedKeyset,
            masterPassword,
            salt,
            config.kdfIterations
        )
        val streamingAead = tinkCryptoManager.getStreamingAead(keyset)
        val encryptionContext = VaultEncryptionContext(
            keysetHandle = keyset,
            vaultId = config.vaultId,
        )

        val importedManifest = decryptImportedFile(
            File(directory, MANIFEST_DATA_FILE),
            streamingAead,
            encryptionContext.associatedData(MANIFEST_DATA_FILE)
        ) { input ->
            Json.decodeFromString<VaultManifestV2>(
                readUtf8WithLimit(input, MAX_MANIFEST_PLAINTEXT_BYTES)
            )
        }
        require(isSchemaVersionSupported(importedManifest.schemaVersion)) {
            "Imported manifest schema is unsupported"
        }
        require(importedManifest.vaultId == config.vaultId) { "Imported vault id does not match" }
        require(importedManifest.attachments.size <= MAX_ATTACHMENT_COUNT) {
            "Imported vault contains too many attachments"
        }
        require(verifyConfigBinding(importedManifest, config)) {
            "Imported vault config binding is invalid"
        }
        require(verifyAuthenticatedSnapshotMetadata(importedManifest, directory)) {
            "Imported vault snapshot metadata is invalid"
        }

        val importedPasswords = decryptImportedFile(
            File(directory, PASSWORDS_DATA_FILE),
            streamingAead,
            encryptionContext.associatedData(PASSWORDS_DATA_FILE)
        ) { input ->
            parseProtobufWithLimits(input, MAX_PASSWORDS_PLAINTEXT_BYTES) {
                PasswordEntries.parseFrom(it)
            }
        }
        require(importedPasswords.entriesCount <= MAX_PASSWORD_ENTRY_COUNT) {
            "Imported vault contains too many password entries"
        }
        require(isSchemaVersionSupported(importedPasswords.schemaVersion)) {
            "Imported password schema is unsupported"
        }

        val importedCategories = decryptImportedFile(
            File(directory, CATEGORIES_DATA_FILE),
            streamingAead,
            encryptionContext.associatedData(CATEGORIES_DATA_FILE)
        ) { input ->
            parseProtobufWithLimits(input, MAX_CATEGORIES_PLAINTEXT_BYTES) {
                Categories.parseFrom(it)
            }
        }
        require(importedCategories.categoriesCount <= MAX_CATEGORY_COUNT) {
            "Imported vault contains too many categories"
        }
        require(isSchemaVersionSupported(importedCategories.schemaVersion)) {
            "Imported category schema is unsupported"
        }

        val attachmentIds = mutableSetOf<String>()
        importedManifest.attachments.forEach { attachment ->
            require(attachment.id.isNotBlank() && attachmentIds.add(attachment.id)) {
                "Imported manifest contains duplicate attachment ids"
            }
            val encryptedFile = safeImportedAttachmentFile(directory, attachment.encryptedFileName)
            require(encryptedFile.isFile) { "Imported attachment is missing" }
            decryptImportedFile(
                encryptedFile,
                streamingAead,
                encryptionContext.associatedData("attachment:${attachment.id}")
            ) { input ->
                copyWithLimitCancellable(input, DISCARDING_OUTPUT, AppConstants.MAX_ATTACHMENT_SIZE_BYTES)
            }

            val thumbnail = safeImportedAttachmentFile(directory, "${attachment.id}_thumb.dat")
            if (thumbnail.isFile) {
                decryptImportedFile(
                    thumbnail,
                    streamingAead,
                    encryptionContext.associatedData("attachment-thumbnail:${attachment.id}")
                ) { input ->
                    copyWithLimitCancellable(input, DISCARDING_OUTPUT, MAX_THUMBNAIL_PLAINTEXT_BYTES)
                }
            }
        }

        val totpFile = File(directory, TOTP_DATA_FILE)
        val importedTotpEntries = if (totpFile.isFile) {
            val importedTotp = decryptImportedFile(
                totpFile,
                streamingAead,
                encryptionContext.associatedData(TOTP_DATA_FILE)
            ) { input ->
                parseProtobufWithLimits(input, MAX_TOTP_PLAINTEXT_BYTES) {
                    com.turisla.hellopocket.model.TotpEntries.parseFrom(it)
                }
            }
            require(importedTotp.entriesCount <= MAX_TOTP_ENTRY_COUNT) {
                "Imported vault contains too many TOTP entries"
            }
            require(isSchemaVersionSupported(importedTotp.schemaVersion)) {
                "Imported TOTP schema is unsupported"
            }
            importedTotp.entriesList
        } else {
            emptyList()
        }

        VaultSemanticValidator.validateCore(
            passwords = importedPasswords.entriesList,
            categories = importedCategories.categoriesList,
            attachments = importedManifest.attachments,
        )
        VaultSemanticValidator.validateTotpEntries(importedTotpEntries)
    }

    private fun safeVaultAttachmentFile(directory: File, fileName: String): File {
        require(fileName.isNotBlank() && '/' !in fileName && '\\' !in fileName) {
            "Vault attachment has an invalid file name"
        }
        val attachmentsDirectory = File(directory, ATTACHMENTS_DIR).canonicalFile
        val file = File(attachmentsDirectory, fileName).canonicalFile
        require(file.parentFile == attachmentsDirectory) { "Vault attachment escaped its directory" }
        return file
    }

    private fun safeImportedAttachmentFile(directory: File, fileName: String): File {
        return safeVaultAttachmentFile(directory, fileName)
    }

    private suspend fun <T> decryptImportedFile(
        file: File,
        streamingAead: StreamingAead,
        associatedData: ByteArray,
        read: suspend (java.io.InputStream) -> T
    ): T {
        require(file.isFile && file.length() > 0L) { "Imported encrypted file is missing" }
        val input = FileInputStream(file)
        try {
            val decryptingInput = streamingAead.newDecryptingStream(input, associatedData)
            try {
                return read(decryptingInput)
            } finally {
                decryptingInput.close()
            }
        } finally {
            input.close()
        }
    }

    suspend fun exportData(targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (!isVaultInitialized()) return@withContext false
        return@withContext try {
            saveMutex.withLock {
                val output = context.contentResolver.openOutputStream(targetUri)
                    ?: return@withLock false
                output.use { zipVaultToOutputStream(it) }
                true
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            loggerE(error)
            false
        }
    }

    suspend fun exportToCache(fileName: String): File? = withContext(Dispatchers.IO) {
        if (!isVaultInitialized()) return@withContext null
        var file: File? = null
        try {
            val cacheDir = context.cacheDir
            val safeFileName = File(fileName).name
            require(safeFileName.endsWith(BACKUP_FILE_SUFFIX)) { "Invalid backup file extension" }
            file = File(cacheDir, safeFileName)
            // 如果文件已存在，先删除
            if (file.exists()) {
                file.delete()
            }
            saveMutex.withLock {
                FileOutputStream(file).use { fos -> zipVaultToOutputStream(fos) }
            }
            return@withContext file
        } catch (error: CancellationException) {
            file?.delete()
            throw error
        } catch (e: Exception) {
            file?.delete()
            loggerE(e)
            return@withContext null
        }
    }

    private suspend fun zipVaultToOutputStream(outputStream: java.io.OutputStream) {
        ZipOutputStream(outputStream).use { zos ->
            vaultDir.walkTopDown().filter(File::isFile).forEach { file ->
                currentCoroutineContext().ensureActive()
                val entryName = file.relativeTo(vaultDir).invariantSeparatorsPath
                // 事务 pending / backup / journal 都以点开头，绝不能进入备份。
                if (entryName.split('/').any { it.startsWith('.') }) return@forEach
                val zipEntry = ZipEntry(entryName)
                zos.putNextEntry(zipEntry)
                FileInputStream(file).use { fis -> copyToCancellable(fis, zos) }
                zos.closeEntry()
            }
        }
    }

    suspend fun getBackupHistory(): List<File> = withContext(Dispatchers.IO) {
        context.filesDir.listFiles { _, name ->
            name.startsWith(BACKUP_FILE_PREFIX) && name.endsWith(BACKUP_FILE_SUFFIX)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    suspend fun restoreFromBackup(backupFile: File, masterPassword: String): Boolean = withContext(Dispatchers.IO) {
        if (!backupFile.exists()) return@withContext false
        return@withContext importData(Uri.fromFile(backupFile), masterPassword).success
    }

    suspend fun deleteFromBackup(backupFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            backupFile.delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 配置更新必须与 manifest 绑定同一事务，避免攻击者只替换旧 config 来撤销改密或提示更新。
     */
    private suspend fun commitConfigUpdate(
        configBase: VaultConfig,
        writeSession: VaultWriteSession,
    ): VaultConfig {
        val currentManifest = writeSession.manifest
        val keyset = writeSession.keyset
        val newManifest = currentManifest.copy(
            generation = currentManifest.generation + 1,
            configBinding = calculateConfigBinding(configBase),
        )
        val streamingAead = tinkCryptoManager.getStreamingAead(keyset)
        val encryptionContext = VaultEncryptionContext(
            keyset,
            configBase.vaultId,
        )
        val manifestTarget = File(vaultDir, MANIFEST_DATA_FILE)
        val configTarget = File(vaultDir, VAULT_CONFIG_FILE_NAME)
        val manifestPending = VaultFileTransaction.createPendingFile(manifestTarget)
        val configPending = VaultFileTransaction.createPendingFile(configTarget)
        try {
            writeEncryptedManifest(
                manifestPending,
                newManifest,
                streamingAead,
                encryptionContext.associatedData(MANIFEST_DATA_FILE),
            )
            val completedConfig = configBase.copy(
                integrityHash = calculateVaultIntegrityHash(
                    mapOf(
                        PASSWORDS_DATA_FILE to File(vaultDir, PASSWORDS_DATA_FILE),
                        CATEGORIES_DATA_FILE to File(vaultDir, CATEGORIES_DATA_FILE),
                        MANIFEST_DATA_FILE to manifestPending,
                    )
                ) ?: error("Failed to hash config update")
            )
            writePlainTextSynced(configPending, Json.encodeToString(completedConfig))
            currentCoroutineContext().ensureActive()
            commitVaultTransactionForSession(
                expectedEpoch = writeSession.epoch,
                expectedKeyset = keyset,
                writes = listOf(
                    VaultFileTransaction.PendingWrite(manifestTarget, manifestPending),
                    VaultFileTransaction.PendingWrite(configTarget, configPending),
                ),
                onCommitted = {
                    vaultManifestV2 = newManifest
                    vaultConfig = completedConfig
                },
            )
            return completedConfig
        } finally {
            manifestPending.delete()
            configPending.delete()
        }
    }

    
    /**
     * V2修改主密码：只换锁芯，不搬家
     * 只需重新加密keyset，不需要重新加密所有数据
     */
    suspend fun changeMasterPassword(oldPassword: String, newPassword: String): ChangePasswordResult = withContext(Dispatchers.IO) {
        if (newPassword.length < AppConstants.MIN_MASTER_PASSWORD_LENGTH) {
            return@withContext ChangePasswordResult(false, false)
        }
        val changeSessionToken = sessionGuard.capture()
        saveMutex.withLock {
            val writeSession = captureActiveVaultWriteSession()
                ?: return@withContext ChangePasswordResult(false, false)
            val config = writeSession.config
            val salt = Base64.decode(config.salt, Base64.NO_WRAP)

            // 1. 验证旧密码（尝试解密Keyset）
            val currentKeyset = try {
                tinkCryptoManager.decryptKeyset(
                    config.encryptedKeyset,
                    oldPassword,
                    salt,
                    config.kdfIterations
                )
            } catch (e: Exception) {
                return@withContext ChangePasswordResult(false, false)
            }

            // 2. 生成新盐值，并使用当前安全基线升级 KDF 工作因子。
            val newSalt = tinkCryptoManager.generateSalt()
            val newEncryptedKeyset = tinkCryptoManager.encryptKeyset(
                currentKeyset,
                newPassword,
                newSalt,
                TinkCryptoManager.DEFAULT_KDF_ITERATIONS
            )
            currentCoroutineContext().ensureActive()
            if (!sessionGuard.publishIfValid(changeSessionToken) {}) {
                return@withContext ChangePasswordResult(false, false)
            }

            // 3. 配置与认证清单原子提交，失败时旧密码仍保持有效。
            val newConfigBase = config.copy(
                salt = Base64.encodeToString(newSalt, Base64.NO_WRAP),
                encryptedKeyset = newEncryptedKeyset,
                kdfAlgorithm = TinkCryptoManager.PBKDF2_ALGORITHM,
                kdfIterations = TinkCryptoManager.DEFAULT_KDF_ITERATIONS
            )
            commitConfigUpdate(newConfigBase, writeSession)

            // 4. 重置生物识别
            val wasBiometricEnabled = isBiometricUnlockEnabled()
            if (wasBiometricEnabled) {
                disableBiometricUnlock()
            }
        
            ChangePasswordResult(true, wasBiometricEnabled)
        }
    }

    fun isBiometricUnlockEnabled(): Boolean {
        return prefs.getBoolean(PREF_BIOMETRIC_ENABLED, false)
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    fun disableBiometricUnlock() {
        prefs.edit().clear().commit()
        _isBiometricEnabled.value = false
    }
    
    fun getPasswordHint(): String? {
        vaultConfig?.passwordHint?.let { return it.takeIf(String::isNotBlank) }
        if (!vaultConfigFile.isFile) return null
        return try {
            decodeCompatibleVaultConfig(
                readUtf8FileWithLimit(vaultConfigFile, MAX_VAULT_CONFIG_BYTES)
            ).passwordHint?.takeIf(String::isNotBlank)
        } catch (error: Exception) {
            loggerE(error)
            null
        }
    }

    suspend fun updatePasswordHint(hint: String?) = withContext(Dispatchers.IO) {
        val updateSessionToken = sessionGuard.capture()
        saveMutex.withLock {
            val writeSession = captureActiveVaultWriteSession() ?: return@withContext
            val config = writeSession.config
            val newConfigBase = config.copy(passwordHint = hint?.takeIf(String::isNotBlank))
            if (!sessionGuard.publishIfValid(updateSessionToken) {}) return@withContext
            commitConfigUpdate(newConfigBase, writeSession)
        }
    }

    suspend fun setupBiometricUnlock(cipher: Cipher): Boolean = withContext(Dispatchers.IO) {
        val setupSessionToken = sessionGuard.capture()
        val keyset = streamingAeadKeysetHandle ?: return@withContext false

        // 1. 序列化 Keyset 为字节
        val keysetBytes = tinkCryptoManager.writeKeysetToBytes(keyset)

        // 2. 用 Biometric Cipher 加密这些字节
        val encryptedKey = try {
            cipher.doFinal(keysetBytes)
        } catch (e: Exception) {
            loggerE(e)
            return@withContext false
        } finally {
            keysetBytes.fill(0)
        }
        val iv = cipher.iv
        currentCoroutineContext().ensureActive()
        if (!sessionGuard.publishIfValid(setupSessionToken) {}) return@withContext false

        // 3. 保存到 Prefs
        val persisted = prefs.edit()
            .putString(PREF_ENCRYPTED_DATA_KEY, Base64.encodeToString(encryptedKey, Base64.NO_WRAP))
            .putString(PREF_DATA_KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putBoolean(PREF_BIOMETRIC_ENABLED, true)
            .commit()
        if (!persisted) return@withContext false
        _isBiometricEnabled.value = true
        true
    }

    fun getEncryptedDataKeyForBiometric(): Pair<ByteArray, ByteArray>? {
        val encryptedKeyB64 = prefs.getString(PREF_ENCRYPTED_DATA_KEY, null)
        val ivB64 = prefs.getString(PREF_DATA_KEY_IV, null)
        if (encryptedKeyB64 == null || ivB64 == null) return null
        val encryptedKey = Base64.decode(encryptedKeyB64, Base64.NO_WRAP)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        return Pair(iv, encryptedKey)
    }

    suspend fun addAttachment(uri: Uri): String = stateMutationMutex.withLock {
        withContext(Dispatchers.IO) {
        val attachmentSessionToken = sessionGuard.capture()
        val attachmentId = UUID.randomUUID().toString()
        val encryptedFileName = "$attachmentId.dat"
        val attachmentsDir = File(vaultDir, ATTACHMENTS_DIR)
        if (!attachmentsDir.exists()) attachmentsDir.mkdirs()
        val outputFile = File(attachmentsDir, encryptedFileName)
        val thumbnailFile = File(attachmentsDir, "${attachmentId}_thumb.dat")

        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        
        // 获取原始文件名和大小
        var originalFileName = "unknown"
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (nameIndex != -1) originalFileName = cursor.getString(nameIndex) ?: "unknown"
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
        require(size <= 0L || size <= AppConstants.MAX_ATTACHMENT_SIZE_BYTES) {
            "Attachment exceeds the configured size limit"
        }
        originalFileName = sanitizeDisplayFileName(originalFileName)

        val encryptionContext = getVaultEncryptionContext() ?: error("Vault locked or no streaming keyset")
        val streamingAead = tinkCryptoManager.getStreamingAead(encryptionContext.keysetHandle)
        val operationJob = currentCoroutineContext()[Job]
        try {
            // 即使 ContentProvider 不报告大小，也在实际流式复制时强制执行上限。
            var actualSize = 0L
            contentResolver.openInputStream(uri)?.use { inputStream ->
                writeEncryptedStream(
                    outputFile,
                    streamingAead,
                    encryptionContext.associatedData("attachment:$attachmentId")
                ) { encryptedOutput ->
                    actualSize = copyWithLimit(
                        inputStream,
                        encryptedOutput,
                        AppConstants.MAX_ATTACHMENT_SIZE_BYTES,
                    ) {
                        operationJob?.ensureActive()
                    }
                }
            } ?: throw IllegalStateException("Cannot read attachment file")
            size = actualSize

            // 生成并保存缩略图（如果是图片或视频）
            try {
                val thumbnail = generateThumbnail(uri, mimeType)
                if (thumbnail != null) {
                    ByteArrayOutputStream().use { baos ->
                        thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                        ByteArrayInputStream(baos.toByteArray()).use { input ->
                            writeEncryptedStream(
                                thumbnailFile,
                                streamingAead,
                                encryptionContext.associatedData("attachment-thumbnail:$attachmentId")
                            ) { encryptedOutput ->
                                input.copyTo(encryptedOutput)
                            }
                        }
                    }
                    thumbnail.recycle()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                loggerE(e)
                thumbnailFile.delete()
            }

            val newAttachmentEntry = AttachmentManifestEntry(
                id = attachmentId,
                encryptedFileName = encryptedFileName,
                originalFileName = originalFileName,
                mimeType = mimeType,
                size = size,
                createdAt = System.currentTimeMillis()
            )
            currentCoroutineContext().ensureActive()
            if (!sessionGuard.publishIfValid(attachmentSessionToken) {}) {
                throw CancellationException("Attachment session is no longer active")
            }
            persistVaultState(
                _passwordEntries.value,
                _categories.value,
                _attachments.value + newAttachmentEntry
            )
            return@withContext attachmentId
        } catch (error: Exception) {
            outputFile.delete()
            thumbnailFile.delete()
            throw error
        }
        }
    }

    suspend fun deleteAttachment(attachmentId: String) = stateMutationMutex.withLock {
        withContext(Dispatchers.IO) {
        val currentAttachments = _attachments.value.toMutableList()
        val entry = currentAttachments.find { it.id == attachmentId } ?: return@withContext
        
        currentAttachments.remove(entry)
        val updatedEntries = _passwordEntries.value.map { passwordEntry ->
            if (attachmentId in passwordEntry.attachmentIdsList) {
                passwordEntry.toBuilder()
                    .clearAttachmentIds()
                    .addAllAttachmentIds(passwordEntry.attachmentIdsList.filterNot { it == attachmentId })
                    .build()
            } else {
                passwordEntry
            }
        }
        // 条目引用和 manifest 在同一事务中移除，再删除物理文件，避免产生悬空引用。
        persistVaultState(updatedEntries, _categories.value, currentAttachments)
        val attachmentsDir = File(vaultDir, ATTACHMENTS_DIR)
        File(attachmentsDir, entry.encryptedFileName).delete()
        File(attachmentsDir, "${entry.id}_thumb.dat").delete()
        }
    }

    suspend fun getAttachmentFile(attachmentId: String): File? = withContext(Dispatchers.IO) {
        val entry = _attachments.value.find { it.id == attachmentId } ?: return@withContext null
        val decryptionSession = captureAttachmentDecryptionSession() ?: return@withContext null
        val encryptedFile = File(File(vaultDir, ATTACHMENTS_DIR), entry.encryptedFileName)
        if (!encryptedFile.exists()) return@withContext null

        // 保存到缓存目录供查看
        val cacheDir = File(context.cacheDir, "decrypted_attachments")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        // 使用原始扩展名
        val extension = entry.originalFileName.substringAfterLast('.', "")
            .filter(Char::isLetterOrDigit)
            .take(10)
        val cacheKey = attachmentCacheKey(entry.id)
        val fileName = if (extension.isNotEmpty()) "$cacheKey.$extension" else cacheKey
        val outputFile = File(cacheDir, fileName)
        val pendingFile = File.createTempFile("attachment-", ".pending", cacheDir)

        try {
            synchronized(vaultSessionLock) {
                if (decryptionSession.isCurrent() && outputFile.isFile) {
                    pendingFile.delete()
                    return@withContext outputFile
                }
            }
            val encryptionContext = decryptionSession.encryptionContext
            val streamingAead = tinkCryptoManager.getStreamingAead(encryptionContext.keysetHandle)
            FileInputStream(encryptedFile).use { fis ->
                streamingAead.newDecryptingStream(
                    fis,
                    encryptionContext.associatedData("attachment:${entry.id}")
                ).use { decStream ->
                    FileOutputStream(pendingFile).use { fos ->
                        copyToCancellable(decStream, fos)
                        fos.fd.sync()
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            synchronized(vaultSessionLock) {
                if (!decryptionSession.isCurrent()) return@withContext null
                if (outputFile.exists() && !outputFile.delete()) return@withContext null
                if (!pendingFile.renameTo(outputFile)) return@withContext null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            return@withContext null
        } finally {
            pendingFile.delete()
        }

        return@withContext outputFile
    }

    /**
     * 生成缩略图（200x200）
     */
    private suspend fun generateThumbnail(uri: Uri, mimeType: String): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            when {
                mimeType.startsWith("image/") -> {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeStream(inputStream, null, options)
                        
                        val sampleSize = calculateInSampleSize(
                            options,
                            MAX_THUMBNAIL_DIMENSION,
                            MAX_THUMBNAIL_DIMENSION,
                        )
                        options.inJustDecodeBounds = false
                        options.inSampleSize = sampleSize
                        
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, options)
                        }
                    }
                }
                mimeType.startsWith("video/") -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                        // 旧 API 只能先解出完整视频帧，高分辨率视频可能造成明显内存峰值。
                        null
                    } else {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, uri)
                            val sourceWidth = retriever
                                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                                ?.toIntOrNull()
                                ?: MAX_THUMBNAIL_DIMENSION
                            val sourceHeight = retriever
                                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                                ?.toIntOrNull()
                                ?: MAX_THUMBNAIL_DIMENSION
                            val rotation = retriever
                                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                                ?.toIntOrNull()
                                ?: 0
                            val displayWidth = if (rotation == 90 || rotation == 270) {
                                sourceHeight
                            } else {
                                sourceWidth
                            }
                            val displayHeight = if (rotation == 90 || rotation == 270) {
                                sourceWidth
                            } else {
                                sourceHeight
                            }
                            val (targetWidth, targetHeight) = calculateThumbnailDimensions(
                                displayWidth,
                                displayHeight,
                                MAX_THUMBNAIL_DIMENSION,
                            )
                            retriever.getScaledFrameAtTime(
                                0,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                targetWidth,
                                targetHeight,
                            )
                        } finally {
                            retriever.release()
                        }
                    }
                }
                else -> null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateThumbnailDimensions(
        width: Int,
        height: Int,
        maxDimension: Int,
    ): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return maxDimension to maxDimension
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(width, height))
        return (width * scale).toInt().coerceAtLeast(1) to
            (height * scale).toInt().coerceAtLeast(1)
    }

    /**
     * 计算图片采样率
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * 获取解密后的缩略图文件
     */
    suspend fun getThumbnailFile(attachmentId: String): File? = withContext(Dispatchers.IO) {
        val decryptionSession = captureAttachmentDecryptionSession() ?: return@withContext null
        val thumbnailEncrypted = File(File(vaultDir, ATTACHMENTS_DIR), "${attachmentId}_thumb.dat")
        
        if (!thumbnailEncrypted.exists()) return@withContext null
        
        val cacheDir = File(context.cacheDir, "decrypted_thumbnails")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val outputFile = File(cacheDir, "${attachmentCacheKey(attachmentId)}.jpg")
        val pendingFile = File.createTempFile("thumbnail-", ".pending", cacheDir)

        try {
            synchronized(vaultSessionLock) {
                if (decryptionSession.isCurrent() && outputFile.isFile) {
                    pendingFile.delete()
                    return@withContext outputFile
                }
            }
            val encryptionContext = decryptionSession.encryptionContext
            val streamingAead = tinkCryptoManager.getStreamingAead(encryptionContext.keysetHandle)
            FileInputStream(thumbnailEncrypted).use { fis ->
                streamingAead.newDecryptingStream(
                    fis,
                    encryptionContext.associatedData("attachment-thumbnail:$attachmentId")
                ).use { decStream ->
                    FileOutputStream(pendingFile).use { fos ->
                        copyToCancellable(decStream, fos)
                        fos.fd.sync()
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            synchronized(vaultSessionLock) {
                if (!decryptionSession.isCurrent()) return@withContext null
                if (outputFile.exists() && !outputFile.delete()) return@withContext null
                if (!pendingFile.renameTo(outputFile)) return@withContext null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            return@withContext null
        } finally {
            pendingFile.delete()
        }
        return@withContext outputFile
    }

    fun clearAttachmentCache() {
        synchronized(vaultSessionLock) {
            clearAttachmentCacheLocked()
        }
    }

    private fun clearAttachmentCacheLocked() {
        // 清理所有解密缓存目录
        listOf("decrypted_attachments", "decrypted_thumbnails").forEach { dirName ->
            val cacheDir = File(context.cacheDir, dirName)
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        }
    }

    private data class AttachmentDecryptionSession(
        val encryptionContext: VaultEncryptionContext,
        val epoch: Long,
        val repository: PasswordRepository
    ) {
        fun isCurrent(): Boolean {
            return repository.sessionEpoch == epoch &&
                repository.streamingAeadKeysetHandle === encryptionContext.keysetHandle
        }
    }

    private fun captureAttachmentDecryptionSession(): AttachmentDecryptionSession? {
        return synchronized(vaultSessionLock) {
            val encryptionContext = getVaultEncryptionContext() ?: return@synchronized null
            AttachmentDecryptionSession(encryptionContext, sessionEpoch, this)
        }
    }

    private fun attachmentCacheKey(attachmentId: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(attachmentId.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private suspend fun copyToCancellable(
        input: java.io.InputStream,
        output: OutputStream
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
    }


    suspend fun cleanupOrphanedAttachments() = stateMutationMutex.withLock {
        withContext(Dispatchers.IO) {
        val attachmentsDir = File(vaultDir, ATTACHMENTS_DIR)
        if (!attachmentsDir.exists()) return@withContext

        val allAttachmentIds = _passwordEntries.value.flatMap { it.attachmentIdsList }.toSet()
        val currentAttachments = _attachments.value.toMutableList()
        val iterator = currentAttachments.iterator()

        while (iterator.hasNext()) {
            val attachment = iterator.next()
            if (!allAttachmentIds.contains(attachment.id)) {
                // 先从清单移除；物理文件在 durable commit 后统一清理。
                iterator.remove()
            }
        }

        if (currentAttachments.size != _attachments.value.size) {
            persistVaultState(_passwordEntries.value, _categories.value, currentAttachments)
        }

        // 同时清理由中断的附件写入产生、且未被当前清单引用的密文与缩略图。
        val referencedFileNames = currentAttachments.flatMap { attachment ->
            listOf(attachment.encryptedFileName, "${attachment.id}_thumb.dat")
        }.toSet()
        attachmentsDir.listFiles()
            ?.filter(File::isFile)
            ?.filterNot { it.name in referencedFileNames }
            ?.forEach(File::delete)
        }
    }

    private fun importEntrySizeLimit(entryName: String): Long {
        return when (entryName) {
            VAULT_CONFIG_FILE_NAME -> MAX_VAULT_CONFIG_BYTES
            PASSWORDS_DATA_FILE -> {
                MAX_PASSWORDS_PLAINTEXT_BYTES + STREAMING_CIPHERTEXT_OVERHEAD_ALLOWANCE_BYTES
            }
            CATEGORIES_DATA_FILE -> {
                MAX_CATEGORIES_PLAINTEXT_BYTES + STREAMING_CIPHERTEXT_OVERHEAD_ALLOWANCE_BYTES
            }
            MANIFEST_DATA_FILE -> {
                MAX_MANIFEST_PLAINTEXT_BYTES + STREAMING_CIPHERTEXT_OVERHEAD_ALLOWANCE_BYTES
            }
            TOTP_DATA_FILE -> {
                MAX_TOTP_PLAINTEXT_BYTES + STREAMING_CIPHERTEXT_OVERHEAD_ALLOWANCE_BYTES
            }
            else -> {
                val attachmentPrefix = "$ATTACHMENTS_DIR/"
                require(entryName.startsWith(attachmentPrefix)) { "Import contains an unexpected file" }
                val relativeName = entryName.removePrefix(attachmentPrefix)
                require(relativeName.isNotBlank() && '/' !in relativeName) {
                    "Import contains an invalid attachment path"
                }
                MAX_IMPORT_SINGLE_FILE_BYTES
            }
        }
    }

    private fun readUtf8FileWithLimit(file: File, maxBytes: Long): String {
        require(file.isFile && file.length() <= maxBytes) { "Text file exceeds its size limit" }
        return FileInputStream(file).use { input -> readUtf8WithLimit(input, maxBytes) }
    }

    private fun readUtf8WithLimit(input: java.io.InputStream, maxBytes: Long): String {
        val initialCapacity = minOf(maxBytes, 64L * 1024).toInt()
        return ByteArrayOutputStream(initialCapacity).use { output ->
            copyWithLimit(input, output, maxBytes)
            output.toString(Charsets.UTF_8.name())
        }
    }

    private fun <T> parseProtobufWithLimits(
        input: java.io.InputStream,
        maxBytes: Long,
        parse: (CodedInputStream) -> T
    ): T {
        require(maxBytes in 1..Int.MAX_VALUE.toLong()) { "Invalid protobuf size limit" }
        val codedInput = CodedInputStream.newInstance(input).apply {
            setSizeLimit(maxBytes.toInt())
            setRecursionLimit(PROTOBUF_RECURSION_LIMIT)
        }
        return parse(codedInput)
    }

    private fun copyWithLimit(
        input: java.io.InputStream,
        output: OutputStream,
        maxBytes: Long,
        cancellationCheck: () -> Unit = {},
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            cancellationCheck()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "Input exceeds the configured size limit" }
            output.write(buffer, 0, count)
        }
        return total
    }

    private suspend fun copyWithLimitCancellable(
        input: java.io.InputStream,
        output: OutputStream,
        maxBytes: Long,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "Input exceeds the configured size limit" }
            output.write(buffer, 0, count)
        }
        return total
    }

    private fun sanitizeDisplayFileName(fileName: String): String {
        val sanitized = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filter { it >= ' ' && it != '\u007F' }
            .take(255)
        return sanitized.ifBlank { "attachment" }
    }

    private data class SnapshotMetadata(
        val digests: Map<String, String>,
        val sizes: Map<String, Long>,
    )

    private fun calculateSnapshotMetadata(files: Map<String, File>): SnapshotMetadata {
        val orderedNames = listOf(PASSWORDS_DATA_FILE, CATEGORIES_DATA_FILE, TOTP_DATA_FILE)
            .filter(files::containsKey)
        val digests = linkedMapOf<String, String>()
        val sizes = linkedMapOf<String, Long>()
        orderedNames.forEach { logicalName ->
            val file = files.getValue(logicalName)
            require(file.isFile) { "Snapshot file is missing: $logicalName" }
            digests[logicalName] = calculateFileDigest(file)
            sizes[logicalName] = file.length()
        }
        require(PASSWORDS_DATA_FILE in digests && CATEGORIES_DATA_FILE in digests) {
            "Snapshot metadata is missing a core file"
        }
        return SnapshotMetadata(digests, sizes)
    }

    private fun verifyAuthenticatedSnapshotMetadata(
        manifest: VaultManifestV2,
        directory: File,
    ): Boolean {
        return verifySnapshotMetadata(
            fileDigests = manifest.fileDigests,
            fileSizes = manifest.fileSizes,
            directory = directory,
            allowBothMissing = false,
        )
    }

    private fun verifyCompatibleSnapshotMetadata(
        manifest: CompatibleVaultManifestV2,
        directory: File,
        allowMissing: Boolean,
    ): Boolean {
        return verifySnapshotMetadata(
            fileDigests = manifest.fileDigests,
            fileSizes = manifest.fileSizes,
            directory = directory,
            allowBothMissing = allowMissing,
        )
    }

    private fun verifySnapshotMetadata(
        fileDigests: Map<String, String>,
        fileSizes: Map<String, Long>,
        directory: File,
        allowBothMissing: Boolean,
    ): Boolean {
        if (fileDigests.isEmpty() && fileSizes.isEmpty()) return allowBothMissing
        if (fileDigests.isEmpty() || fileSizes.isEmpty()) return false

        return try {
            val expectedNames = buildSet {
                add(PASSWORDS_DATA_FILE)
                add(CATEGORIES_DATA_FILE)
                if (File(directory, TOTP_DATA_FILE).isFile) add(TOTP_DATA_FILE)
            }
            if (fileDigests.keys != expectedNames || fileSizes.keys != expectedNames) {
                return false
            }
            expectedNames.all { logicalName ->
                val file = File(directory, logicalName)
                if (!file.isFile) return@all false
                val expectedDigest = Base64.decode(fileDigests.getValue(logicalName), Base64.NO_WRAP)
                val actualDigest = Base64.decode(calculateFileDigest(file), Base64.NO_WRAP)
                file.length() == fileSizes.getValue(logicalName) &&
                    MessageDigest.isEqual(expectedDigest, actualDigest)
            }
        } catch (error: Exception) {
            loggerE(error)
            false
        }
    }

    private fun calculateFileDigest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            var count: Int
            while (input.read(buffer).also { count = it } != -1) {
                digest.update(buffer, 0, count)
            }
        }
        return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }

    private fun calculateConfigBinding(config: VaultConfig): String {
        return Base64.encodeToString(calculateVaultConfigBindingBytes(config), Base64.NO_WRAP)
    }

    private fun verifyConfigBinding(manifest: VaultManifestV2, config: VaultConfig): Boolean {
        if (manifest.configBinding.isBlank()) return false
        return try {
            MessageDigest.isEqual(
                Base64.decode(manifest.configBinding, Base64.NO_WRAP),
                Base64.decode(calculateConfigBinding(config), Base64.NO_WRAP),
            )
        } catch (error: Exception) {
            loggerE(error)
            false
        }
    }

    private fun verifyCompatibleConfigBinding(
        manifest: CompatibleVaultManifestV2,
        config: CompatibleVaultConfig,
        allowMissing: Boolean,
    ): Boolean {
        // 最早的 V2 清单没有绑定；只有已由旧公开配置触发的兼容路径才允许缺失。
        if (manifest.configBinding.isBlank()) return allowMissing
        return try {
            val actual = Base64.decode(manifest.configBinding, Base64.NO_WRAP)
            val historical = calculateCompatibleVaultConfigBindingBytes(config)
            val current = calculateVaultConfigBindingBytes(config.toVaultConfig())
            MessageDigest.isEqual(actual, historical) || MessageDigest.isEqual(actual, current)
        } catch (error: Exception) {
            loggerE(error)
            false
        }
    }
    
    /**
     * 计算保险库数据文件的完整性哈希
     * 按顺序计算 passwords.dat + categories.dat + manifest.dat 的 SHA-256 哈希值
     */
    private fun calculateVaultIntegrityHash(baseDir: File = vaultDir): String? {
        return calculateVaultIntegrityHash(
            mapOf(
                PASSWORDS_DATA_FILE to File(baseDir, PASSWORDS_DATA_FILE),
                CATEGORIES_DATA_FILE to File(baseDir, CATEGORIES_DATA_FILE),
                MANIFEST_DATA_FILE to File(baseDir, MANIFEST_DATA_FILE)
            )
        )
    }

    private fun calculateVaultIntegrityHash(files: Map<String, File>): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            
            // 按固定顺序读取文件并更新哈希
            listOf(PASSWORDS_DATA_FILE, CATEGORIES_DATA_FILE, MANIFEST_DATA_FILE).forEach { fileName ->
                val file = files[fileName] ?: return null
                if (!file.isFile) return null
                // 同时纳入逻辑文件名和长度，避免边界歧义。
                digest.update(fileName.toByteArray(Charsets.UTF_8))
                digest.update(file.length().toString().toByteArray(Charsets.UTF_8))
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                    }
                }
            }
            
            Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
        } catch (e: Exception) {
            loggerE(e)
            null
        }
    }
    
    /**
     * 验证公开配置中的保险库完整性摘要。早期 V2 可能没有摘要，或使用只串接密文字节的历史算法；
     * 这些例外只允许在公开配置已经明确标记为旧格式时使用。
     */
    private fun verifyVaultIntegrity(
        directory: File,
        expectedHash: String?,
        allowLegacyFormat: Boolean,
    ): Boolean {
        if (expectedHash.isNullOrBlank()) return allowLegacyFormat
        if (base64DigestMatches(expectedHash, calculateVaultIntegrityHash(directory))) return true
        return allowLegacyFormat &&
            base64DigestMatches(expectedHash, calculateLegacyVaultIntegrityHash(directory))
    }

    private fun calculateLegacyVaultIntegrityHash(baseDir: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            listOf(PASSWORDS_DATA_FILE, CATEGORIES_DATA_FILE, MANIFEST_DATA_FILE).forEach { fileName ->
                val file = File(baseDir, fileName)
                if (!file.isFile) return null
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        digest.update(buffer, 0, count)
                    }
                }
            }
            Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
        } catch (error: Exception) {
            loggerE(error)
            null
        }
    }

    private fun base64DigestMatches(expectedHash: String, actualHash: String?): Boolean {
        if (expectedHash.isBlank() || actualHash.isNullOrBlank()) return false
        return try {
            MessageDigest.isEqual(
                Base64.decode(expectedHash, Base64.NO_WRAP),
                Base64.decode(actualHash, Base64.NO_WRAP),
            )
        } catch (error: Exception) {
            loggerE(error)
            false
        }
    }

    /**
     * 获取当前的 KeysetHandle 用于初始化其他需要加密的组件
     */
    fun getKeysetHandle(): KeysetHandle? = streamingAeadKeysetHandle

    fun getVaultEncryptionContext(): VaultEncryptionContext? {
        val keyset = streamingAeadKeysetHandle ?: return null
        val config = vaultConfig ?: return null
        return VaultEncryptionContext(keyset, config.vaultId)
    }
}
