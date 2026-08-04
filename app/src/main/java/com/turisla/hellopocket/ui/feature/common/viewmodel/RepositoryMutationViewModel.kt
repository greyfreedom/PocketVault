package com.turisla.hellopocket.ui.feature.common.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turisla.hellopocket.R
import com.turisla.hellopocket.utils.loggerE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class UiErrorEvent(@param:StringRes val messageResId: Int)

/**
 * 统一承接 Repository 写操作异常，避免未捕获异常终止协程且 UI 没有反馈。
 */
abstract class RepositoryMutationViewModel : ViewModel() {

    private val _repositoryErrorEvents = MutableSharedFlow<UiErrorEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val repositoryErrorEvents: SharedFlow<UiErrorEvent> = _repositoryErrorEvents.asSharedFlow()

    protected fun launchRepositoryMutation(
        @StringRes errorMessageResId: Int = R.string.operation_failed_try_again,
        block: suspend () -> Unit,
    ): Job {
        return viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                loggerE(error)
                _repositoryErrorEvents.emit(UiErrorEvent(errorMessageResId))
            }
        }
    }
}
