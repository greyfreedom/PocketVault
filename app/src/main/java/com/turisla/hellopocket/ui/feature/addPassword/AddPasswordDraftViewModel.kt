package com.turisla.hellopocket.ui.feature.addPassword

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.turisla.hellopocket.model.CustomFieldType
import com.turisla.hellopocket.ui.feature.common.CustomFieldDraft
import com.turisla.hellopocket.ui.feature.common.moveCustomField
import com.turisla.hellopocket.ui.feature.common.newCustomFieldDraft
import com.turisla.hellopocket.ui.feature.common.normalizeCustomFieldNameInput
import com.turisla.hellopocket.ui.feature.common.normalizeCustomFieldValueInput
import com.turisla.hellopocket.ui.feature.common.restoreCustomField
import com.turisla.hellopocket.ui.feature.common.updateCustomField
import com.turisla.hellopocket.utils.AppConstants
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
    val customFields: List<CustomFieldDraft> = emptyList(),
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

    fun addCustomField(type: CustomFieldType): String? {
        var addedId: String? = null
        _draft.update { state ->
            if (state.customFields.size >= AppConstants.MAX_CUSTOM_FIELDS_PER_ENTRY) {
                state
            } else {
                val field = newCustomFieldDraft(type)
                addedId = field.id
                state.copy(customFields = state.customFields + field)
            }
        }
        return addedId
    }

    fun updateCustomFieldName(fieldId: String, value: String) = _draft.update { state ->
        state.copy(
            customFields = state.customFields.updateCustomField(fieldId) { field ->
                field.copy(name = normalizeCustomFieldNameInput(value))
            }
        )
    }

    fun updateCustomFieldValue(fieldId: String, value: String) = _draft.update { state ->
        state.copy(
            customFields = state.customFields.updateCustomField(fieldId) { field ->
                field.copy(value = normalizeCustomFieldValueInput(value))
            }
        )
    }

    fun updateCustomFieldType(fieldId: String, type: CustomFieldType) = _draft.update { state ->
        require(type == CustomFieldType.TEXT || type == CustomFieldType.CONCEALED)
        state.copy(
            customFields = state.customFields.updateCustomField(fieldId) { field ->
                field.copy(type = type)
            }
        )
    }

    fun removeCustomField(fieldId: String) = _draft.update { state ->
        state.copy(customFields = state.customFields.filterNot { it.id == fieldId })
    }

    fun restoreCustomField(field: CustomFieldDraft, index: Int) = _draft.update { state ->
        state.copy(customFields = state.customFields.restoreCustomField(field, index))
    }

    fun moveCustomField(fieldId: String, targetIndex: Int) = _draft.update { state ->
        state.copy(customFields = state.customFields.moveCustomField(fieldId, targetIndex))
    }

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
