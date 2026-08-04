package com.turisla.hellopocket.ui.feature.addSecureNote

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 新建安全笔记页面的内存草稿。
 *
 * 草稿绑定到 RouteAddSecureNote 的返回栈条目，只在内存中保存；退出页面或应用锁定并清空
 * 返回栈后会随 ViewModel 销毁，不使用 SavedStateHandle，避免敏感正文进入 Bundle 或磁盘。
 */
data class AddSecureNoteDraftState(
    val title: String = "",
    val content: TextFieldValue = TextFieldValue(""),
    val selectedCategoryIds: Set<String> = emptySet(),
    val selectedAttachmentIds: Set<String> = emptySet(),
)

class AddSecureNoteDraftViewModel : ViewModel() {
    private val _draft = MutableStateFlow(AddSecureNoteDraftState())
    val draft = _draft.asStateFlow()

    fun updateTitle(value: String) = _draft.update { it.copy(title = value) }

    fun updateContent(value: TextFieldValue) = _draft.update { it.copy(content = value) }

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
        _draft.value = AddSecureNoteDraftState()
    }

    override fun onCleared() {
        // 主动释放安全笔记正文等敏感字符串引用，缩短明文在内存中的生命周期。
        clear()
        super.onCleared()
    }
}
