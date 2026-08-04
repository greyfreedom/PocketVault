package com.turisla.hellopocket.ui.feature.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turisla.hellopocket.R
import com.turisla.hellopocket.data.PasswordRepository
import com.turisla.hellopocket.data.TotpRepository
import com.turisla.hellopocket.data.UserPreferencesRepository
import com.turisla.hellopocket.security.BiometricCipherManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import javax.crypto.Cipher

class SettingsPageViewModel(
    private val passwordRepository: PasswordRepository,
    private val biometricCipherManager: BiometricCipherManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val totpRepository: TotpRepository,
) : ViewModel(), KoinComponent {

    private val context: Context by inject()

    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event.asStateFlow()

    fun consumeEvent(event: Event) {
        _event.compareAndSet(event, null)
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val isBiometricUnlockEnabled: StateFlow<Boolean> = passwordRepository.isBiometricEnabled

    private val _showLanguageDialog = MutableStateFlow(false)
    val showLanguageDialog: StateFlow<Boolean> = _showLanguageDialog.asStateFlow()

    private val _currentLanguage = MutableStateFlow(userPreferencesRepository.getLanguage())
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()
    
    val passwordHint = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
             passwordHint.value = passwordRepository.getPasswordHint()
        }
    }

    fun onLanguageClicked() {
        _showLanguageDialog.value = true
    }

    fun onLanguageDialogDismissed() {
        _showLanguageDialog.value = false
    }

    fun onLanguageSelected(language: String) {
        viewModelScope.launch {
            _showLanguageDialog.value = false
            userPreferencesRepository.saveLanguage(language)
            _currentLanguage.value = language
            userPreferencesRepository.applyLanguage()
        }
    }

    // Theme Settings
    private val _showThemeDialog = MutableStateFlow(false)
    val showThemeDialog: StateFlow<Boolean> = _showThemeDialog.asStateFlow()

    private val _currentTheme = MutableStateFlow(userPreferencesRepository.getTheme())
    val currentTheme: StateFlow<String> = _currentTheme.asStateFlow()

    fun onThemeClicked() {
        _showThemeDialog.value = true
    }

    fun onThemeDialogDismissed() {
        _showThemeDialog.value = false
    }

    fun onThemeSelected(theme: String) {
        viewModelScope.launch {
            _showThemeDialog.value = false
            userPreferencesRepository.saveTheme(theme)
            _currentTheme.value = theme
            userPreferencesRepository.applyTheme()
        }
    }

    fun onBiometricUnlockToggled(enable: Boolean) {
        viewModelScope.launch {
            if (enable) {
                val biometricManager = BiometricManager.from(context)
                val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_STRONG)
                when (canAuthenticate) {
                    BiometricManager.BIOMETRIC_SUCCESS -> {
                        // 生物识别可用，继续加密流程
                        try {
                            val cipher = biometricCipherManager.getCipherForEncryption()
                            _event.value = Event.RequestBiometricEncryption(cipher)
                        } catch (_: Exception) {
                            // 即使检查通过，创建密钥时也可能失败
                            _event.value = Event.ShowBiometricError(context.getString(R.string.unknown_error))
                        }
                    }
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                        // 未注册指纹，提示用户去设置
                        _event.value = Event.ShowEnrollBiometricDialog
                    }

                    else -> {
                        // 其他错误
                        _event.value = Event.ShowBiometricError(context.getString(R.string.biometric_not_available))
                    }
                }
            } else {
                // 禁用时，不仅要清除 SharedPreferences 中的密钥，还要删除 AndroidKeyStore 中的密钥
                passwordRepository.disableBiometricUnlock()
                biometricCipherManager.deleteBiometricKey()
                _event.value = Event.BiometricSetupDisabled
            }
        }
    }
    
    fun onBiometricEncryptionSucceeded(cipher: Cipher) {
        viewModelScope.launch {
            if (passwordRepository.setupBiometricUnlock(cipher)) {
                _event.value = Event.BiometricSetupSuccess
            } else {
                // 新密钥与旧包装数据不能混用；失败时恢复为明确的禁用状态。
                passwordRepository.disableBiometricUnlock()
                biometricCipherManager.deleteBiometricKey()
                _event.value = Event.ShowBiometricError(context.getString(R.string.unknown_error))
            }
        }
    }

    fun shareData(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val startTs = System.currentTimeMillis()
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val fileName = "pocketvault_$timestamp.hpb"
                val file = passwordRepository.exportToCache(fileName)
                // 如果时间比1秒快，那么等一会儿，防止出现Loading闪现的情况
                val waitDuration = 1000 - (System.currentTimeMillis() - startTs)
                if (waitDuration > 0) {
                    delay(waitDuration)
                }
                if (file != null) {
                    _event.value = Event.ShareFile(file)
                } else {
                    Toast.makeText(context, R.string.generate_file_failed, Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (passwordRepository.exportData(uri)) {
                    _event.value = Event.ExportSuccess
                } else {
                    _event.value = Event.ExportFailed
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importData(uri: Uri, masterPassword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val importResult = totpRepository.withVaultReplacementLock {
                    passwordRepository.importData(uri, masterPassword)
                }
                if (importResult.success) {
                    _event.value = Event.ImportSuccess(importResult.biometricsWereDisabled)
                } else {
                    _event.value = Event.ImportFailed
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun updatePasswordHint(hint: String?) {
        viewModelScope.launch {
            passwordRepository.updatePasswordHint(hint)
            passwordHint.value = hint
            _event.value = Event.PasswordHintUpdated
        }
    }

    sealed class Event {
        data class RequestBiometricEncryption(val cipher: Cipher) : Event()
        data object BiometricSetupSuccess : Event()
        data object BiometricSetupDisabled : Event()
        data object ExportSuccess : Event()
        data object ExportFailed : Event()
        data class ImportSuccess(val biometricsWereDisabled: Boolean) : Event()
        data object ImportFailed : Event()
        data object ShowEnrollBiometricDialog : Event()
        data class ShowBiometricError(val message: String) : Event()
        data class ShareFile(val file: java.io.File) : Event()
        data object PasswordHintUpdated : Event()
    }
}
