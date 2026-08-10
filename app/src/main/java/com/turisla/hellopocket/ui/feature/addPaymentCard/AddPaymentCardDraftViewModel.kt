package com.turisla.hellopocket.ui.feature.addPaymentCard

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.turisla.hellopocket.model.CustomFieldType
import com.turisla.hellopocket.model.PaymentCardBrand
import com.turisla.hellopocket.ui.feature.common.CustomFieldDraft
import com.turisla.hellopocket.ui.feature.common.moveCustomField
import com.turisla.hellopocket.ui.feature.common.newCustomFieldDraft
import com.turisla.hellopocket.ui.feature.common.normalizeCardholderNameInput
import com.turisla.hellopocket.ui.feature.common.normalizeCustomFieldNameInput
import com.turisla.hellopocket.ui.feature.common.normalizeCustomFieldValueInput
import com.turisla.hellopocket.ui.feature.common.normalizePaymentCardNumberInput
import com.turisla.hellopocket.ui.feature.common.normalizeSecurityCodeInput
import com.turisla.hellopocket.ui.feature.common.restoreCustomField
import com.turisla.hellopocket.ui.feature.common.updateCustomField
import com.turisla.hellopocket.utils.AppConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AddPaymentCardDraftState(
    val title: String = "",
    val cardholderName: String = "",
    val cardNumber: String = "",
    val cardBrand: PaymentCardBrand = PaymentCardBrand.PAYMENT_CARD_BRAND_UNSPECIFIED,
    val expirationMonth: Int = 0,
    val expirationYear: Int = 0,
    val securityCode: String = "",
    val notes: TextFieldValue = TextFieldValue(""),
    val customFields: List<CustomFieldDraft> = emptyList(),
    val selectedCategoryIds: Set<String> = emptySet(),
    val selectedAttachmentIds: Set<String> = emptySet(),
)

/** 支付卡草稿只驻留在对应导航返回栈的 ViewModel 中，不进入 SavedState 或磁盘。 */
class AddPaymentCardDraftViewModel : ViewModel() {
    private val _draft = MutableStateFlow(AddPaymentCardDraftState())
    val draft = _draft.asStateFlow()

    fun updateTitle(value: String) = _draft.update { it.copy(title = value) }

    fun updateCardholderName(value: String) = _draft.update {
        it.copy(cardholderName = normalizeCardholderNameInput(value))
    }

    fun updateCardNumber(value: String) = _draft.update {
        it.copy(cardNumber = normalizePaymentCardNumberInput(value))
    }

    fun updateCardBrand(value: PaymentCardBrand) = _draft.update {
        require(value != PaymentCardBrand.UNRECOGNIZED)
        it.copy(cardBrand = value)
    }

    fun updateExpirationMonth(value: Int) = _draft.update {
        require(value == 0 || value in 1..12)
        it.copy(expirationMonth = value)
    }

    fun updateExpirationYear(value: Int) = _draft.update {
        require(value == 0 || value in AppConstants.MIN_EXPIRATION_YEAR..AppConstants.MAX_EXPIRATION_YEAR)
        it.copy(expirationYear = value)
    }

    fun updateSecurityCode(value: String) = _draft.update {
        it.copy(securityCode = normalizeSecurityCodeInput(value))
    }

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
            },
        )
    }

    fun updateCustomFieldValue(fieldId: String, value: String) = _draft.update { state ->
        state.copy(
            customFields = state.customFields.updateCustomField(fieldId) { field ->
                field.copy(value = normalizeCustomFieldValueInput(value))
            },
        )
    }

    fun updateCustomFieldType(fieldId: String, type: CustomFieldType) = _draft.update { state ->
        require(type == CustomFieldType.TEXT || type == CustomFieldType.CONCEALED)
        state.copy(
            customFields = state.customFields.updateCustomField(fieldId) { field ->
                field.copy(type = type)
            },
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
        _draft.value = AddPaymentCardDraftState()
    }

    override fun onCleared() {
        // 主动释放卡号与安全码等敏感字符串引用。
        clear()
        super.onCleared()
    }
}
