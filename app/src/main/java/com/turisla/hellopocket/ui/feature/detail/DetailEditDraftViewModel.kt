package com.turisla.hellopocket.ui.feature.detail

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.model.VaultItemType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 详情页的内存编辑草稿。
 *
 * 该 ViewModel 绑定到 RouteDetail 的返回栈条目，因此进入密码生成器时编辑模式和已填写内容
 * 都会继续保留；离开详情页后草稿随返回栈条目释放，不会写入 SavedStateHandle 或磁盘。
 */
data class DetailEditDraftState(
    val isEditing: Boolean = false,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val notes: TextFieldValue = TextFieldValue(""),
    val selectedCategoryIds: Set<String> = emptySet(),
    val selectedAttachmentIds: Set<String> = emptySet(),
)

class DetailEditDraftViewModel : ViewModel() {
    private val _draft = MutableStateFlow(DetailEditDraftState())
    val draft = _draft.asStateFlow()

    fun beginEditing(entry: PasswordEntry) {
        // 从生成器返回时不能用仓库中的旧数据覆盖现有草稿。
        if (_draft.value.isEditing) return

        val notesText = if (entry.type == VaultItemType.NOTE) entry.content else entry.notes
        _draft.value = DetailEditDraftState(
            isEditing = true,
            title = entry.title,
            username = entry.username,
            password = entry.password,
            notes = TextFieldValue(notesText),
            selectedCategoryIds = entry.categoryIdsList.toSet(),
            selectedAttachmentIds = entry.attachmentIdsList.toSet(),
        )
    }

    fun updateTitle(value: String) = _draft.update { it.copy(title = value) }

    fun updateUsername(value: String) = _draft.update { it.copy(username = value) }

    fun updatePassword(value: String) = _draft.update { state ->
        if (state.isEditing) state.copy(password = value) else state
    }

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

    fun buildUpdatedEntry(entry: PasswordEntry): PasswordEntry {
        val state = _draft.value
        check(state.isEditing) { "Password entry is not being edited" }

        val builder = entry.toBuilder()
            .setTitle(state.title)
            .clearCategoryIds()
            .addAllCategoryIds(state.selectedCategoryIds.toList())
            .clearAttachmentIds()
            .addAllAttachmentIds(state.selectedAttachmentIds.toList())

        return if (entry.type == VaultItemType.NOTE) {
            builder.setContent(state.notes.text).build()
        } else {
            builder
                .setUsername(state.username)
                .setPassword(state.password)
                .setNotes(state.notes.text)
                .build()
        }
    }

    fun clear() {
        _draft.value = DetailEditDraftState()
    }

    override fun onCleared() {
        // 主动释放密码等敏感字符串引用，缩短明文在内存中的生命周期。
        clear()
        super.onCleared()
    }
}
