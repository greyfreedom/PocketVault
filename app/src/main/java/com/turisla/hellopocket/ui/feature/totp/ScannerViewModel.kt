package com.turisla.hellopocket.ui.feature.totp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.turisla.hellopocket.R
import com.turisla.hellopocket.data.TotpRepository
import com.turisla.hellopocket.security.OtpAuthParser
import kotlinx.coroutines.CancellationException
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

    private val _addSuccess = MutableStateFlow(false)
    val addSuccess: StateFlow<Boolean> = _addSuccess.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _errorMessage = MutableStateFlow<Int?>(null)
    val errorMessage: StateFlow<Int?> = _errorMessage.asStateFlow()

    /**
     * 处理扫码结果
     */
    fun handleScanResult(scannedText: String) {
        // 连续扫码回调可能在数帧内重复触发；只允许一个保险库写入任务。
        if (!_isProcessing.compareAndSet(expect = false, update = true)) return
        processScannedUri(scannedText)
    }

    /**
     * 处理扫描到的 URI
     */
    private fun processScannedUri(uriString: String) {
        viewModelScope.launch {
            try {
                // 解析并校验 otpauth:// URI 后再写入保险库。
                val otpData = OtpAuthParser.parse(uriString)
                if (otpData == null || !OtpAuthParser.isValidBase32(otpData.secret)) {
                    showError(R.string.totp_invalid_qr)
                    return@launch
                }

                // 添加到存储
                totpRepository.addTotpEntry(otpData)
                _addSuccess.value = true
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                showError(R.string.totp_add_failed)
            } finally {
                // 成功时保持锁定直到页面完成返回，失败时允许用户重新扫描。
                if (!_addSuccess.value) _isProcessing.value = false
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
        _isProcessing.value = false
    }
}
