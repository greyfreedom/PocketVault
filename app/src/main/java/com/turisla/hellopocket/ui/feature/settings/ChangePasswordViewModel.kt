package com.turisla.hellopocket.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turisla.hellopocket.data.PasswordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context
import com.turisla.hellopocket.R
import com.turisla.hellopocket.utils.AppConstants.MIN_MASTER_PASSWORD_LENGTH
import com.turisla.hellopocket.utils.loggerE
import kotlinx.coroutines.CancellationException

sealed class ChangePasswordUiState {
    data object Idle : ChangePasswordUiState()
    data object Loading : ChangePasswordUiState()
    data class Success(val biometricsWereDisabled: Boolean) : ChangePasswordUiState()
    data class Error(val message: String) : ChangePasswordUiState()
}

class ChangePasswordViewModel(
    private val passwordRepository: PasswordRepository) : ViewModel() {

    data class FormState(
        val currentPassword: String = "",
        val newPassword: String = "",
        val confirmPassword: String = ""
    )

    private val _formState = MutableStateFlow(FormState())
    val formState = _formState.asStateFlow()

    private val _uiState = MutableStateFlow<ChangePasswordUiState>(ChangePasswordUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun updateCurrentPassword(value: String) {
        _formState.update { it.copy(currentPassword = value) }
    }

    fun updateNewPassword(value: String) {
        _formState.update { it.copy(newPassword = value) }
    }

    fun updateConfirmPassword(value: String) {
        _formState.update { it.copy(confirmPassword = value) }
    }

    fun consumeResult() {
        _uiState.value = ChangePasswordUiState.Idle
    }

    fun attemptChangePassword(context: Context) {
        viewModelScope.launch {
            val form = _formState.value
            if (form.newPassword.length < MIN_MASTER_PASSWORD_LENGTH) {
                _uiState.value = ChangePasswordUiState.Error(
                    context.resources.getQuantityString(
                        R.plurals.master_password_too_short,
                        MIN_MASTER_PASSWORD_LENGTH,
                        MIN_MASTER_PASSWORD_LENGTH,
                    )
                )
                return@launch
            }
            if (form.newPassword != form.confirmPassword) {
                _uiState.value = ChangePasswordUiState.Error(context.getString(R.string.password_mismatch_new))
                return@launch
            }

            _uiState.value = ChangePasswordUiState.Loading
            try {
                val result = passwordRepository.changeMasterPassword(form.currentPassword, form.newPassword)
                if (result.success) {
                    _formState.value = FormState()
                    _uiState.value = ChangePasswordUiState.Success(result.biometricsWereDisabled)
                } else {
                    _uiState.value = ChangePasswordUiState.Error(context.getString(R.string.current_password_incorrect))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                loggerE(error)
                _uiState.value = ChangePasswordUiState.Error(
                    context.getString(R.string.operation_failed_try_again)
                )
            }
        }
    }
}
