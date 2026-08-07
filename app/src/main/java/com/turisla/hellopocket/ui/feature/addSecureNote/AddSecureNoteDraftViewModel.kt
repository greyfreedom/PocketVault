package com.turisla.hellopocket.ui.feature.addSecureNote

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
 * 新建安全笔记页面的内存草稿。
 *
 * 草稿绑定到 RouteAddSecureNote 的返回栈条目，只在内存中保存；退出页面或应用锁定并清空
 * 返回栈后会随 ViewModel 销毁，不使用 SavedStateHandle，避免敏感正文进入 Bundle 或磁盘。
 */
data class AddSecureNoteDraftState(
    val title: String = "",
    val content: TextFieldValue = TextFieldValue(""),
    val customFields: List<CustomFieldDraft> = emptyList(),
    val selectedCategoryIds: Set<String> = emptySet(),
    val selectedAttachmentIds: Set<String> = emptySet(),
)

class AddSecureNoteDraftViewModel : ViewModel() {
    private val _draft = MutableStateFlow(AddSecureNoteDraftState())
    val draft = _draft.asStateFlow()

    fun updateTitle(value: String) = _draft.update { it.copy(title = value) }

    fun updateContent(value: TextFieldValue) = _draft.update { it.copy(content = value) }

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
        _draft.value = AddSecureNoteDraftState()
    }

    override fun onCleared() {
        // 主动释放安全笔记正文等敏感字符串引用，缩短明文在内存中的生命周期。
        clear()
        super.onCleared()
    }
}
