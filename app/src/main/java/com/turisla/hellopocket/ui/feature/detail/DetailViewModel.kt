package com.turisla.hellopocket.ui.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.turisla.hellopocket.data.PasswordRepository
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.ui.feature.common.viewmodel.RepositoryMutationViewModel
import com.turisla.hellopocket.utils.ClipboardManagerHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val passwordRepository: PasswordRepository,
    private val clipboardManagerHelper: ClipboardManagerHelper,
) : RepositoryMutationViewModel() {

    private val entryId: String = checkNotNull(savedStateHandle["id"])
    
    // 跟踪当前正在进行的解密Job，用于取消
    private var currentDecryptionJob: Job? = null

    private val _event = MutableStateFlow<Event?>(null)
    val event = _event.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting = _isDeleting.asStateFlow()

    fun consumeEvent(event: Event) {
        _event.compareAndSet(event, null)
    }
    
    // 使用计数而不是瞬时事件，避免并发附件任务让全局加载状态过早结束。
    private val attachmentLoadingLock = Any()
    private var activeAttachmentOperations = 0
    private val _isAttachmentLoading = MutableStateFlow(false)
    val isAttachmentLoading = _isAttachmentLoading.asStateFlow()

    val passwordEntry = passwordRepository.passwordEntries.map {
        it.firstOrNull { entry -> entry.id == entryId }
    }.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = null
    )

    // 分类列表
    val categories = passwordRepository.categories

    fun onCopyUsername() {
        passwordEntry.value?.username?.let {
            clipboardManagerHelper.copyTextToClipboard(R.string.username, it)
        }
    }

    fun onCopyPassword() {
        passwordEntry.value?.password?.let {
            clipboardManagerHelper.copyTextToClipboard(R.string.password, it)
        }
    }

    fun onCopyCardNumber() {
        passwordEntry.value?.cardNumber?.takeIf(String::isNotBlank)?.let {
            clipboardManagerHelper.copyTextToClipboard(R.string.card_number, it)
        }
    }

    fun onCopySecurityCode() {
        passwordEntry.value?.securityCode?.takeIf(String::isNotBlank)?.let {
            clipboardManagerHelper.copyTextToClipboard(R.string.security_code, it)
        }
    }

    fun onCopyCustomField(fieldId: String) {
        passwordEntry.value?.customFieldsList
            ?.firstOrNull { it.id == fieldId }
            ?.value
            ?.let { value ->
                clipboardManagerHelper.copyTextToClipboard(R.string.custom_field, value)
            }
    }

    fun copyCustomFieldValue(value: String) {
        clipboardManagerHelper.copyTextToClipboard(R.string.custom_field, value)
    }

    /**
     * 更新密码条目
     * @param entry 包含更新后信息的新密码条目对象
     */
    suspend fun updatePassword(entry: PasswordEntry) {
        passwordRepository.updateEntry(entry)
    }

    /**
     * 删除当前密码条目
     */
    fun deletePassword() {
        if (!_isDeleting.compareAndSet(expect = false, update = true)) return
        launchRepositoryMutation {
            try {
                passwordRepository.deleteEntry(entryId)
                // 持久化完成后再返回，避免页面销毁取消写入。
                _event.value = Event.NavigateBack
            } finally {
                _isDeleting.value = false
            }
        }
    }

    /**
     * 添加新分类
     * @return 新创建的分类ID
     */
    suspend fun addCategory(name: String, color: String): String {
        return passwordRepository.addCategory(name, color)
    }

    // 附件列表
    val attachments = passwordRepository.attachments

    suspend fun addAttachment(uri: android.net.Uri): String {
        beginAttachmentOperation()
        return try {
            passwordRepository.addAttachment(uri)
        } finally {
            endAttachmentOperation()
        }
    }

    fun deleteAttachment(attachmentId: String) {
        launchRepositoryMutation {
            passwordRepository.deleteAttachment(attachmentId)
        }
    }

    suspend fun getAttachmentFile(attachmentId: String): java.io.File? {
        val operationJob = currentCoroutineContext()[Job]
        // 取消之前的解密操作
        if (currentDecryptionJob !== operationJob) currentDecryptionJob?.cancel()
        currentDecryptionJob = operationJob

        beginAttachmentOperation()
        return try {
            passwordRepository.getAttachmentFile(attachmentId)
        } finally {
            endAttachmentOperation()
            if (currentDecryptionJob === operationJob) currentDecryptionJob = null
        }
    }

    suspend fun loadAttachmentThumbnail(attachmentId: String): Any? {
        return passwordRepository.getThumbnailFile(attachmentId)
    }

    fun clearCache() {
        passwordRepository.clearAttachmentCache()
    }

    private fun beginAttachmentOperation() {
        synchronized(attachmentLoadingLock) {
            activeAttachmentOperations++
            _isAttachmentLoading.value = true
        }
    }

    private fun endAttachmentOperation() {
        synchronized(attachmentLoadingLock) {
            activeAttachmentOperations = (activeAttachmentOperations - 1).coerceAtLeast(0)
            _isAttachmentLoading.value = activeAttachmentOperations > 0
        }
    }

    override fun onCleared() {
        // 取消正在进行的解密
        currentDecryptionJob?.cancel()
        synchronized(attachmentLoadingLock) {
            activeAttachmentOperations = 0
            _isAttachmentLoading.value = false
        }
        // viewModelScope 此时已被取消，安全清理必须同步执行。
        clearCache()
        super.onCleared()
    }

    sealed class Event {
        data object NavigateBack : Event()
    }
}
