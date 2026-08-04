package com.turisla.hellopocket.ui.feature.totp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.turisla.hellopocket.R
import com.turisla.hellopocket.data.TotpRepository
import com.turisla.hellopocket.security.OtpAuthParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 扫码页面 ViewModel
 */
class ScannerViewModel(
    private val totpRepository: TotpRepository
) : ViewModel() {

    private val _scanResult = MutableStateFlow<Result<String>?>(null)
    val scanResult: StateFlow<Result<String>?> = _scanResult.asStateFlow()

    private val _addSuccess = MutableStateFlow(false)
    val addSuccess: StateFlow<Boolean> = _addSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<Int?>(null)
    val errorMessage: StateFlow<Int?> = _errorMessage.asStateFlow()

    /**
     * 处理扫码结果
     */
    fun handleScanResult(scannedText: String) {
        _scanResult.value = Result.success(scannedText)
    }

    /**
     * 处理扫描到的 URI
     */
    fun processScannedUri(uriString: String) {
        viewModelScope.launch {
            // 解析 otpauth:// URI
            val otpData = OtpAuthParser.parse(uriString)
            if (otpData == null) {
                showError(R.string.totp_invalid_qr)
                return@launch
            }

            // 验证 Base32 格式
            if (!OtpAuthParser.isValidBase32(otpData.secret)) {
                showError(R.string.totp_invalid_qr)
                return@launch
            }

            try {
                // 添加到存储
                totpRepository.addTotpEntry(otpData)
                _addSuccess.value = true
            } catch (e: Exception) {
                showError(R.string.totp_add_failed)
            }
        }
    }

    /**
     * 显示错误消息
     */
    fun showError(@StringRes message: Int?) {
        _errorMessage.value = message
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 重置添加成功状态
     */
    fun resetAddSuccess() {
        _addSuccess.value = false
    }
}
