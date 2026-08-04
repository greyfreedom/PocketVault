package com.turisla.hellopocket.ui.feature.addPassword

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 添加密码页面的内存草稿。
 *
 * 该 ViewModel 绑定到 RouteAddPassword 的返回栈条目，因此进入密码生成器时草稿仍然存在；
 * 返回栈条目销毁后状态随 ViewModel 一起释放，不使用 SavedStateHandle，也不会写入磁盘。
 */
data class AddPasswordDraftState(
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val notes: TextFieldValue = TextFieldValue(""),
    val selectedCategoryIds: Set<String> = emptySet(),
    val selectedAttachmentIds: Set<String> = emptySet(),
)

class AddPasswordDraftViewModel : ViewModel() {
    private val _draft = MutableStateFlow(AddPasswordDraftState())
    val draft = _draft.asStateFlow()

    fun updateTitle(value: String) = _draft.update { it.copy(title = value) }

    fun updateUsername(value: String) = _draft.update { it.copy(username = value) }

    fun updatePassword(value: String) = _draft.update { it.copy(password = value) }

    fun updateNotes(value: TextFieldValue) = _draft.update { it.copy(notes = value) }

    fun addCategory(categoryId: String) = _draft.update {
        it.copy(selectedCategoryIds = it.selectedCategoryIds + categoryId)
    }

    fun removeCategory(categoryId: String) = _draft.update {
        it.copy(selectedCategoryIds = it.selectedCategoryIds - categoryId)
    }

    fun addAttachment(attachmentId: String) = _draft.update {
        it.copy(selectedAttachmentIds = it.selectedAttachmentIds + attachmentId)
    }

    fun removeAttachment(attachmentId: String) = _draft.update {
        it.copy(selectedAttachmentIds = it.selectedAttachmentIds - attachmentId)
    }

    fun clear() {
        _draft.value = AddPasswordDraftState()
    }

    override fun onCleared() {
        // 主动释放所有敏感字符串引用，缩短草稿的内存生命周期。
        clear()
        super.onCleared()
    }
}
