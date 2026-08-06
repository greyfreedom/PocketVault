package com.turisla.hellopocket.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turisla.hellopocket.data.PasswordRepository
import com.turisla.hellopocket.data.TotpRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class HistoryViewModel(
    private val passwordRepository: PasswordRepository,
    private val totpRepository: TotpRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = MutableStateFlow<Event?>(null)
    val event = _event.asStateFlow()

    val isBiometricUnlockEnabled = passwordRepository.isBiometricEnabled

    fun consumeEvent(event: Event) {
        _event.compareAndSet(event, null)
    }

    init {
        loadBackupHistory()
    }

    private fun loadBackupHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val historyFiles = passwordRepository.getBackupHistory()
                _uiState.update { it.copy(backupFiles = historyFiles) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _event.value = Event.LoadFailed
            } finally {
                // 页面会在加载中禁止返回，因此所有结果都必须释放加载状态。
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun restoreBackup(backupFile: File, masterPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val success = totpRepository.withVaultReplacementLock {
                    passwordRepository.restoreFromBackup(backupFile, masterPassword)
                }
                _event.value = if (success) Event.RestoreSuccess else Event.RestoreFailed
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _event.value = Event.RestoreFailed
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun deleteBackup(backupFile: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val success = passwordRepository.deleteFromBackup(backupFile)
                if (success) {
                    _event.value = Event.DeleteSuccess
                    _uiState.update { state ->
                        state.copy(
                            backupFiles = state.backupFiles.filter { it.path != backupFile.path },
                        )
                    }
                } else {
                    _event.value = Event.DeleteFailed
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _event.value = Event.DeleteFailed
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    data class HistoryUiState(
        val isLoading: Boolean = false,
        val backupFiles: List<File> = emptyList()
    )

    sealed class Event {
        data object RestoreSuccess : Event()
        data object RestoreFailed : Event()
        data object DeleteSuccess : Event()
        data object DeleteFailed : Event()
        data object LoadFailed : Event()
    }
}
