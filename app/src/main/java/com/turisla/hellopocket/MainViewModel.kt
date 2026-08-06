package com.turisla.hellopocket

import android.content.Context
import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turisla.hellopocket.data.PasswordRepository
import com.turisla.hellopocket.data.TotpRepository
import com.turisla.hellopocket.data.ImportDataFailureReason
import com.turisla.hellopocket.security.BiometricCipherManager
import com.turisla.hellopocket.security.VaultSessionController
import com.turisla.hellopocket.utils.ext.toast
import com.turisla.hellopocket.utils.loggerE
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import javax.crypto.Cipher
import kotlin.getValue

sealed class UiState {
    data object Loading : UiState()
    data object NeedsSetup : UiState()
    data object Locked : UiState()
    data object InvalidPassword : UiState()
    data object FileCorrupted : UiState()  // 新增：文件损坏
    data object IntegrityCheckFailed : UiState()  // 新增：完整性校验失败
    data object UpgradeFailed : UiState()
    data object UpgradeRequiresMasterPassword : UiState()
    data class UnsupportedVersion(val version: Int) : UiState()  // 新增：不支持的版本
    data object Unlocked : UiState()
}


sealed class MainPageInfo {
    data object ImportDataFailed : MainPageInfo()
    data object ImportUpgradeFailed : MainPageInfo()
    data object VaultSetupFailed : MainPageInfo()
}

class MainViewModel(
    private val passwordRepository: PasswordRepository,
    private val biometricCipherManager: BiometricCipherManager,
    private val totpRepository: TotpRepository,
    private val vaultSessionController: VaultSessionController,
) : ViewModel(), KoinComponent {
    private val context: Context by inject()
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingAttachment = MutableStateFlow(false)
    val isLoadingAttachment: StateFlow<Boolean> = _isLoadingAttachment.asStateFlow()

    fun setAttachmentLoading(loading: Boolean) {
        _isLoadingAttachment.value = loading
    }

    private val _biometricUnlockEvent = MutableStateFlow<Cipher?>(null)
    val biometricUnlockEvent: StateFlow<Cipher?> = _biometricUnlockEvent.asStateFlow()

    private val _infoEvent = MutableStateFlow<MainPageInfo?>(null)
    val infoEvent: StateFlow<MainPageInfo?> = _infoEvent.asStateFlow()

    fun consumeBiometricUnlockEvent(cipher: Cipher) {
        _biometricUnlockEvent.compareAndSet(cipher, null)
    }

    fun consumeInfoEvent(event: MainPageInfo) {
        _infoEvent.compareAndSet(event, null)
    }

    val isVaultInitialized: Boolean
        get() = passwordRepository.isVaultInitialized()

    val isBiometricUnlockEnabled: StateFlow<Boolean> = passwordRepository.isBiometricEnabled
    
    val passwordHint = MutableStateFlow<String?>(null)

    private var authenticationJob: Job? = null

    init {
        checkVaultState()
        viewModelScope.launch {
            vaultSessionController.lockGeneration.drop(1).collect {
                authenticationJob?.cancel()
                authenticationJob = null
                _isLoading.value = false
                _isLoadingAttachment.value = false
                _uiState.value = UiState.Locked
            }
        }
    }

    private fun checkVaultState() {
        viewModelScope.launch {
            passwordRepository.cleanupStaleStagingDirectories()
            if (passwordRepository.isVaultInitialized()) {
                _uiState.value = UiState.Locked
            } else {
                _uiState.value = UiState.NeedsSetup
            }
        }
    }

    fun setupVault(masterPassword: String, passwordHint: String?) {
        authenticationJob?.cancel()
        passwordRepository.lock()
        totpRepository.lock()
        authenticationJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                passwordRepository.setupNewVault(masterPassword, passwordHint)
                val encryptionContext = passwordRepository.getVaultEncryptionContext()
                _uiState.value = if (
                    encryptionContext != null &&
                    totpRepository.initialize(encryptionContext) is com.turisla.hellopocket.model.VaultLoadResult.Success &&
                    vaultSessionController.isForeground()
                ) {
                    UiState.Unlocked
                } else {
                    vaultSessionController.lockVault()
                    if (vaultSessionController.isForeground()) UiState.FileCorrupted else UiState.Locked
                }
            } catch (error: CancellationException) {
                vaultSessionController.lockVault()
                throw error
            } catch (error: Exception) {
                loggerE(error)
                vaultSessionController.lockVault()
                _uiState.value = UiState.NeedsSetup
                _infoEvent.value = MainPageInfo.VaultSetupFailed
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importVault(uri: Uri, masterPassword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val importResult = totpRepository.withVaultReplacementLock {
                    passwordRepository.importData(uri, masterPassword)
                }
                if (importResult.success) {
                    checkVaultState() // Re-check to transition to Locked state
                } else {
                    _infoEvent.value = if (
                        importResult.failureReason == ImportDataFailureReason.UPGRADE_FAILED
                    ) {
                        MainPageInfo.ImportUpgradeFailed
                    } else {
                        MainPageInfo.ImportDataFailed
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun unlockVault(masterPassword: String) {
        authenticationJob?.cancel()
        passwordRepository.lock()
        totpRepository.lock()
        authenticationJob = viewModelScope.launch {
            _isLoading.value = true
            // 重置状态以清除上一次的"密码错误"提示
            _uiState.value = UiState.Locked
            try {
                val result = passwordRepository.loadAndDecryptData(masterPassword)
                _uiState.value = when (result) {
                    is com.turisla.hellopocket.model.VaultLoadResult.Success -> {
                        // TOTP 必须与核心保险库一起通过验证，不能把损坏文件静默当作空列表。
                        val encryptionContext = passwordRepository.getVaultEncryptionContext()
                        if (encryptionContext == null) {
                            UiState.FileCorrupted
                        } else {
                            when (val totpResult = totpRepository.initialize(encryptionContext)) {
                                is com.turisla.hellopocket.model.VaultLoadResult.Success -> {
                                    if (vaultSessionController.isForeground()) {
                                        cleanupOrphanedAttachmentsSafely()
                                        UiState.Unlocked
                                    } else {
                                        vaultSessionController.lockVault()
                                        UiState.Locked
                                    }
                                }
                                is com.turisla.hellopocket.model.VaultLoadResult.UnsupportedVersion -> {
                                    passwordRepository.lock()
                                    totpRepository.lock()
                                    UiState.UnsupportedVersion(totpResult.version)
                                }
                                is com.turisla.hellopocket.model.VaultLoadResult.SessionInvalidated -> {
                                    vaultSessionController.lockVault()
                                    UiState.Locked
                                }
                                else -> {
                                    passwordRepository.lock()
                                    totpRepository.lock()
                                    UiState.FileCorrupted
                                }
                            }
                        }
                    }
                    is com.turisla.hellopocket.model.VaultLoadResult.WrongPassword -> {
                        UiState.InvalidPassword
                    }
                    is com.turisla.hellopocket.model.VaultLoadResult.FileCorrupted -> {
                        UiState.FileCorrupted
                    }
                    is com.turisla.hellopocket.model.VaultLoadResult.IntegrityCheckFailed -> {
                        UiState.IntegrityCheckFailed
                    }
                    is com.turisla.hellopocket.model.VaultLoadResult.UpgradeFailed -> {
                        UiState.UpgradeFailed
                    }
                    is com.turisla.hellopocket.model.VaultLoadResult.UpgradeRequiresMasterPassword -> {
                        UiState.UpgradeRequiresMasterPassword
                    }
                    is com.turisla.hellopocket.model.VaultLoadResult.UnsupportedVersion -> {
                        UiState.UnsupportedVersion(result.version)
                    }
                    is com.turisla.hellopocket.model.VaultLoadResult.SessionInvalidated -> {
                        vaultSessionController.lockVault()
                        UiState.Locked
                    }
                }
                // Load hint if wrong password
                if (_uiState.value is UiState.InvalidPassword) {
                     passwordHint.value = passwordRepository.getPasswordHint()
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun requestBiometricUnlock() {
        // 确保功能已开启
        if (!isBiometricUnlockEnabled.value) return
        val biometricManager = BiometricManager.from(context)
        val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_STRONG)
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            val encryptedData = try {
                passwordRepository.getEncryptedDataKeyForBiometric()
            } catch (error: Exception) {
                loggerE(error)
                null
            }
            if (encryptedData == null) {
                disableBrokenBiometricUnlock()
                return
            }
            try {
                val cipher = biometricCipherManager.getCipherForDecryption(encryptedData.first)
                _biometricUnlockEvent.value = cipher
            } catch (e: Exception) {
                loggerE(e)
                disableBrokenBiometricUnlock()
            }
        } else {
            if (canAuthenticate == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                disableBrokenBiometricUnlock()
            } else {
                context.toast(R.string.biometric_not_available)
            }
        }
    }

    fun onBiometricUnlockSucceeded(cipher: Cipher) {
        authenticationJob?.cancel()
        passwordRepository.lock()
        totpRepository.lock()
        authenticationJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val start = System.currentTimeMillis()
                val encryptedData = try {
                    passwordRepository.getEncryptedDataKeyForBiometric()
                } catch (error: Exception) {
                    loggerE(error)
                    null
                }
                if (encryptedData == null) {
                    disableBrokenBiometricUnlock()
                    return@launch
                }
                val encryptedKey = encryptedData.second
                val decryptedKeyBytes = try {
                    cipher.doFinal(encryptedKey)
                } catch (error: Exception) {
                    loggerE(error)
                    disableBrokenBiometricUnlock()
                    return@launch
                }
                // V2: decryptedKeyBytes 是序列化的 KeysetHandle；无论加载是否抛错都立即擦除。
                val loadResult = try {
                    passwordRepository.loadDataWithKeysetBytes(decryptedKeyBytes)
                } finally {
                    decryptedKeyBytes.fill(0)
                }
                val offset = 700 - (System.currentTimeMillis() - start)
                if (offset > 0) {
                    delay(offset) // 不要让界面消失太快
                }
                if (loadResult is com.turisla.hellopocket.model.VaultLoadResult.Success) {
                    val encryptionContext = passwordRepository.getVaultEncryptionContext()
                    val totpResult = encryptionContext?.let { totpRepository.initialize(it) }
                        ?: com.turisla.hellopocket.model.VaultLoadResult.FileCorrupted
                    if (
                        totpResult is com.turisla.hellopocket.model.VaultLoadResult.Success &&
                        vaultSessionController.isForeground()
                    ) {
                        cleanupOrphanedAttachmentsSafely()
                        _uiState.value = UiState.Unlocked
                    } else {
                        passwordRepository.lock()
                        totpRepository.lock()
                        _uiState.value = when (totpResult) {
                            is com.turisla.hellopocket.model.VaultLoadResult.UnsupportedVersion -> {
                                UiState.UnsupportedVersion(totpResult.version)
                            }
                            is com.turisla.hellopocket.model.VaultLoadResult.SessionInvalidated -> {
                                UiState.Locked
                            }
                            else -> UiState.FileCorrupted
                        }
                        if (totpResult !is com.turisla.hellopocket.model.VaultLoadResult.SessionInvalidated) {
                            context.toast(R.string.biometric_unlock_failed)
                        }
                    }
                } else {
                    _uiState.value = when (loadResult) {
                        is com.turisla.hellopocket.model.VaultLoadResult.UnsupportedVersion -> {
                            UiState.UnsupportedVersion(loadResult.version)
                        }
                        is com.turisla.hellopocket.model.VaultLoadResult.IntegrityCheckFailed -> {
                            UiState.IntegrityCheckFailed
                        }
                        is com.turisla.hellopocket.model.VaultLoadResult.UpgradeFailed -> {
                            UiState.UpgradeFailed
                        }
                        is com.turisla.hellopocket.model.VaultLoadResult.UpgradeRequiresMasterPassword -> {
                            UiState.UpgradeRequiresMasterPassword
                        }
                        is com.turisla.hellopocket.model.VaultLoadResult.SessionInvalidated -> {
                            UiState.Locked
                        }
                        else -> UiState.FileCorrupted
                    }
                    if (
                        loadResult !is com.turisla.hellopocket.model.VaultLoadResult.SessionInvalidated &&
                        loadResult !is com.turisla.hellopocket.model.VaultLoadResult.UpgradeRequiresMasterPassword
                    ) {
                        context.toast(R.string.biometric_unlock_failed)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                loggerE(e)
                context.toast(R.string.biometric_unlock_failed)
            }
            finally {
                _isLoading.value = false
            }
        }
    }

    fun lockVault() {
        authenticationJob?.cancel()
        authenticationJob = null
        vaultSessionController.lockVault()
        _isLoadingAttachment.value = false
        _uiState.value = UiState.Locked
    }

    override fun onCleared() {
        authenticationJob?.cancel()
        vaultSessionController.lockVault()
        super.onCleared()
    }

    private fun disableBrokenBiometricUnlock() {
        passwordRepository.disableBiometricUnlock()
        biometricCipherManager.deleteBiometricKey()
        context.toast(R.string.biometric_key_invalidated)
    }

    private suspend fun cleanupOrphanedAttachmentsSafely() {
        try {
            passwordRepository.cleanupOrphanedAttachments()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // 清理失败不应阻止已通过认证的保险库解锁；下次解锁会再次尝试。
            loggerE(error)
        }
    }
}
