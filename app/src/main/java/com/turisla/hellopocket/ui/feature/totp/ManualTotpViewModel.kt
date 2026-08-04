package com.turisla.hellopocket.ui.feature.totp

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turisla.hellopocket.R
import com.turisla.hellopocket.data.TotpRepository
import com.turisla.hellopocket.security.OtpAuthData
import com.turisla.hellopocket.security.OtpAuthParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ManualTotpUiState(
    val editingEntryId: String? = null,
    val issuer: String = "",
    val account: String = "",
    val secret: String = "",
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: String = "30",
    @param:StringRes val issuerError: Int? = null,
    @param:StringRes val accountError: Int? = null,
    @param:StringRes val secretError: Int? = null,
    @param:StringRes val periodError: Int? = null,
    @param:StringRes val saveError: Int? = null,
    val isInitialized: Boolean = false,
    val entryMissing: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
)

/**
 * 添加与编辑 TOTP 共用的表单状态与保存逻辑。
 *
 * 手动填写和二维码扫描最终都会构造同一个 [OtpAuthData]，
 * 从而复用仓库的加密存储流程，避免两套数据格式产生差异。
 */
class ManualTotpViewModel(
    private val totpRepository: TotpRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualTotpUiState())
    val uiState: StateFlow<ManualTotpUiState> = _uiState.asStateFlow()
    private var initialized = false

    /**
     * 编辑模式下从内存中的已解锁保险库读取条目并预填表单。
     * ViewModel 会跨配置变更保留，因此只初始化一次，避免覆盖用户尚未保存的输入。
     */
    fun initialize(entryId: String?) {
        if (initialized) return
        initialized = true
        if (entryId == null) {
            _uiState.value = _uiState.value.copy(isInitialized = true)
            return
        }

        val entry = totpRepository.getTotpEntryById(entryId)
        if (entry == null) {
            _uiState.value = _uiState.value.copy(
                editingEntryId = entryId,
                isInitialized = true,
                entryMissing = true,
                saveError = R.string.totp_entry_not_found,
            )
            return
        }

        _uiState.value = ManualTotpUiState(
            editingEntryId = entry.id,
            issuer = entry.issuer,
            account = entry.account,
            secret = entry.secret.toStringUtf8(),
            algorithm = entry.algorithm.takeIf { it in SUPPORTED_ALGORITHMS } ?: "SHA1",
            digits = entry.digits.takeIf { it in SUPPORTED_DIGITS } ?: 6,
            period = entry.period.takeIf { it in MIN_PERIOD..MAX_PERIOD }?.toString() ?: "30",
            isInitialized = true,
        )
    }

    fun updateIssuer(value: String) {
        _uiState.value = _uiState.value.copy(
            issuer = value.take(MAX_TEXT_LENGTH),
            issuerError = null,
            saveError = null,
        )
    }

    fun updateAccount(value: String) {
        _uiState.value = _uiState.value.copy(
            account = value.take(MAX_TEXT_LENGTH),
            accountError = null,
            saveError = null,
        )
    }

    fun updateSecret(value: String) {
        _uiState.value = _uiState.value.copy(
            secret = value.take(MAX_FORMATTED_SECRET_LENGTH),
            secretError = null,
            saveError = null,
        )
    }

    fun updateAlgorithm(value: String) {
        if (value !in SUPPORTED_ALGORITHMS) return
        _uiState.value = _uiState.value.copy(algorithm = value, saveError = null)
    }

    fun updateDigits(value: Int) {
        if (value !in SUPPORTED_DIGITS) return
        _uiState.value = _uiState.value.copy(digits = value, saveError = null)
    }

    fun updatePeriod(value: String) {
        _uiState.value = _uiState.value.copy(
            period = value.filter(Char::isDigit).take(MAX_PERIOD_LENGTH),
            periodError = null,
            saveError = null,
        )
    }

    fun save() {
        val current = _uiState.value
        if (!current.isInitialized || current.isSaving || current.entryMissing) return

        val issuer = current.issuer.trim()
        val account = current.account.trim()
        val secret = normalizeSecret(current.secret)
        val period = current.period.toIntOrNull()

        val issuerError = if (issuer.isBlank()) R.string.totp_issuer_required else null
        val accountError = if (account.isBlank()) R.string.totp_account_required else null
        val secretError = when {
            secret.isBlank() -> R.string.totp_secret_required
            !OtpAuthParser.isValidBase32(secret) -> R.string.totp_secret_invalid
            else -> null
        }
        val periodError = if (period == null || period !in MIN_PERIOD..MAX_PERIOD) {
            R.string.totp_period_invalid
        } else {
            null
        }

        if (issuerError != null || accountError != null || secretError != null || periodError != null) {
            _uiState.value = current.copy(
                issuer = issuer,
                account = account,
                secret = secret,
                issuerError = issuerError,
                accountError = accountError,
                secretError = secretError,
                periodError = periodError,
                saveError = null,
            )
            return
        }

        val otpData = OtpAuthData(
            issuer = issuer,
            account = account,
            secret = secret,
            algorithm = current.algorithm,
            digits = current.digits,
            period = checkNotNull(period),
        )

        _uiState.value = current.copy(
            issuer = issuer,
            account = account,
            secret = secret,
            issuerError = null,
            accountError = null,
            secretError = null,
            periodError = null,
            saveError = null,
            isSaving = true,
        )

        viewModelScope.launch {
            try {
                val editingEntryId = current.editingEntryId
                if (editingEntryId == null) {
                    totpRepository.addTotpEntry(otpData)
                } else {
                    check(totpRepository.updateTotpEntry(editingEntryId, otpData)) {
                        "TOTP entry no longer exists"
                    }
                }
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveError = if (current.editingEntryId == null) {
                        R.string.totp_add_failed
                    } else {
                        R.string.totp_update_failed
                    },
                )
            }
        }
    }

    fun consumeSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }

    companion object {
        private const val MAX_TEXT_LENGTH = 256
        private const val MAX_FORMATTED_SECRET_LENGTH = 512
        private const val MAX_PERIOD_LENGTH = 3
        private const val MIN_PERIOD = 15
        private const val MAX_PERIOD = 120
        private val SUPPORTED_ALGORITHMS = setOf("SHA1", "SHA256", "SHA512")
        private val SUPPORTED_DIGITS = setOf(6, 8)

        /**
         * 服务商展示的密钥常包含空格或短横线，保存前统一移除并转为大写 Base32。
         */
        internal fun normalizeSecret(value: String): String {
            return value
                .filterNot { it.isWhitespace() || it == '-' }
                .uppercase()
        }
    }
}
