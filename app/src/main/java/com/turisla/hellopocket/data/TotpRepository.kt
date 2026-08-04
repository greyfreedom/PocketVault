package com.turisla.hellopocket.data

import android.content.Context
import com.google.protobuf.ByteString
import com.google.protobuf.CodedInputStream
import com.turisla.hellopocket.model.TotpEntry
import com.turisla.hellopocket.model.TotpEntries
import com.turisla.hellopocket.model.VaultLoadResult
import com.turisla.hellopocket.security.OtpAuthData
import com.turisla.hellopocket.security.TinkCryptoManager
import com.turisla.hellopocket.security.VaultSessionGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/**
 * TOTP 数据仓库
 *
 * 负责 TOTP 条目的加密存储和管理
 * 数据存储在 hellopocket_vault/totp.dat 文件中，使用与密码相同的流式加密方式
 */
class TotpRepository(
    private val context: Context,
    private val tinkCryptoManager: TinkCryptoManager,
    private val passwordRepository: PasswordRepository,
    private val sessionGuard: VaultSessionGuard,
) {
    companion object {
        private const val VAULT_DIRECTORY_NAME = "hellopocket_vault"
        private const val TOTP_DATA_FILE = PasswordRepository.TOTP_DATA_FILE
        private const val CURRENT_SCHEMA_VERSION = 1
        private const val MAX_TOTP_PLAINTEXT_BYTES = 16 * 1024 * 1024
        private const val MAX_TOTP_ENTRY_COUNT = 10_000
        private const val PROTOBUF_RECURSION_LIMIT = 100
    }

    private val vaultDir = File(context.filesDir, VAULT_DIRECTORY_NAME)

    private val _totpEntries = MutableStateFlow<List<TotpEntry>>(emptyList())
    val totpEntries: StateFlow<List<TotpEntry>> = _totpEntries.asStateFlow()

    @Volatile
    private var encryptionContext: VaultEncryptionContext? = null

    @Volatile
    private var sessionEpoch: Long = 0L

    private val stateMutationMutex = Mutex()

    /**
     * 初始化并加载 TOTP 数据
     * 在主密码解锁后调用
     */
    suspend fun initialize(context: VaultEncryptionContext): VaultLoadResult = withContext(Dispatchers.IO) {
        val guardToken = sessionGuard.capture()
        val repositoryEpoch = sessionEpoch
        loadTotpData(context, guardToken, repositoryEpoch)
    }

    /**
     * 从加密文件加载 TOTP 数据
     */
    private suspend fun loadTotpData(
        context: VaultEncryptionContext,
        guardToken: VaultSessionGuard.Token,
        repositoryEpoch: Long,
    ): VaultLoadResult = withContext(Dispatchers.IO) {
        try {
            val streamingAead = tinkCryptoManager.getStreamingAead(context.keysetHandle)

            val totpFile = File(vaultDir, TOTP_DATA_FILE)
            val loadedEntries = if (totpFile.exists()) {
                FileInputStream(totpFile).use { fis ->
                    streamingAead.newDecryptingStream(
                        fis,
                        context.associatedData(TOTP_DATA_FILE)
                    ).use { decStream ->
                        val codedInput = CodedInputStream.newInstance(decStream).apply {
                            setSizeLimit(MAX_TOTP_PLAINTEXT_BYTES)
                            setRecursionLimit(PROTOBUF_RECURSION_LIMIT)
                        }
                        val totpEntries = TotpEntries.parseFrom(codedInput)
                        require(totpEntries.entriesCount <= MAX_TOTP_ENTRY_COUNT) {
                            "Vault contains too many TOTP entries"
                        }
                        if (totpEntries.schemaVersion !in 0..CURRENT_SCHEMA_VERSION) {
                            return@withContext VaultLoadResult.UnsupportedVersion(totpEntries.schemaVersion)
                        }
                        totpEntries.entriesList
                    }
                }
            } else {
                // 文件不存在，创建空数据
                passwordRepository.persistTotpEntries(emptyList())
                emptyList()
            }
            VaultSemanticValidator.validateTotpEntries(loadedEntries)
            currentCoroutineContext().ensureActive()
            var didPublish = false
            val guardAccepted = sessionGuard.publishIfValid(guardToken) {
                if (sessionEpoch == repositoryEpoch) {
                    encryptionContext = context
                    _totpEntries.value = loadedEntries
                    didPublish = true
                }
            }
            if (guardAccepted && didPublish) {
                VaultLoadResult.Success
            } else {
                VaultLoadResult.SessionInvalidated
            }
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            VaultLoadResult.FileCorrupted
        }
    }

    /**
     * 保存 TOTP 数据到加密文件
     */
    private suspend fun saveTotpData(entries: List<TotpEntry>) = withContext(Dispatchers.IO) {
        val context = encryptionContext
            ?: throw IllegalStateException("Vault locked or no streaming keyset")
        val writeSessionEpoch = sessionEpoch
        passwordRepository.persistTotpEntries(entries)
        if (writeSessionEpoch == sessionEpoch && encryptionContext === context) {
            _totpEntries.value = entries
        }
    }

    /**
     * 添加 TOTP 条目
     */
    suspend fun addTotpEntry(data: OtpAuthData): String = stateMutationMutex.withLock {
        withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        val newEntry = TotpEntry.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setIssuer(data.issuer)
            .setAccount(data.account)
            .setSecret(ByteString.copyFrom(data.secret.encodeToByteArray()))
            .setAlgorithm(data.algorithm)
            .setDigits(data.digits)
            .setPeriod(data.period)
            .setCreatedAt(currentTime)
            .setUpdatedAt(currentTime)
            .build()

        saveTotpData(_totpEntries.value + newEntry)
        newEntry.id
        }
    }

    /**
     * 更新 TOTP 条目的全部可编辑字段。
     *
     * ID 和创建时间沿用原条目，更新时间由仓库统一刷新。
     * @return 条目存在且成功更新时返回 true
     */
    suspend fun updateTotpEntry(entryId: String, data: OtpAuthData): Boolean = stateMutationMutex.withLock {
        withContext(Dispatchers.IO) {
        val currentList = _totpEntries.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == entryId }
        if (index == -1) return@withContext false

        currentList[index] = currentList[index].toBuilder()
            .setIssuer(data.issuer)
            .setAccount(data.account)
            .setSecret(ByteString.copyFrom(data.secret.encodeToByteArray()))
            .setAlgorithm(data.algorithm)
            .setDigits(data.digits)
            .setPeriod(data.period)
            .setUpdatedAt(System.currentTimeMillis())
            .build()
        saveTotpData(currentList)
        true
        }
    }

    /**
     * 删除 TOTP 条目
     */
    suspend fun deleteTotpEntry(entryId: String) = stateMutationMutex.withLock {
        withContext(Dispatchers.IO) {
        saveTotpData(_totpEntries.value.filterNot { it.id == entryId })
        }
    }

    /**
     * 根据ID获取 TOTP 条目
     */
    fun getTotpEntryById(id: String): TotpEntry? {
        return _totpEntries.value.find { it.id == id }
    }

    /**
     * 导入/恢复整个保险库时，等待已有 TOTP 提交完成并阻止新写入，避免旧目录写入穿透目录切换。
     */
    suspend fun <T> withVaultReplacementLock(block: suspend () -> T): T {
        return stateMutationMutex.withLock {
            block()
        }
    }

    /**
     * 锁定（清除内存中的密钥和数据）
     */
    fun lock() {
        sessionEpoch++
        encryptionContext = null
        _totpEntries.value = emptyList()
    }

    /**
     * 获取当前 TOTP 条目数量
     */
    fun getCount(): Int = _totpEntries.value.size
}
