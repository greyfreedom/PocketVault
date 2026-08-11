package com.turisla.hellopocket.ui.feature.addPaymentCard

import androidx.compose.ui.text.input.TextFieldValue
import com.turisla.hellopocket.model.CustomFieldType
import com.turisla.hellopocket.model.PaymentCardBrand
import com.turisla.hellopocket.utils.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddPaymentCardDraftViewModelTest {

    @Test
    fun normalizesSensitiveNumericFieldsAndPreservesTheDraft() {
        val viewModel = AddPaymentCardDraftViewModel()
        viewModel.updateTitle("Travel card")
        viewModel.updateCardholderName("Alex Example")
        viewModel.updateCardNumber("4111-1111 1111 1111 extra")
        viewModel.updateCardBrand(PaymentCardBrand.VISA)
        viewModel.updateExpirationMonth(12)
        viewModel.updateExpirationYear(2032)
        viewModel.updateSecurityCode("12a34")
        viewModel.updateNotes(TextFieldValue("Use abroad"))
        viewModel.addCategory("category-1")
        viewModel.addAttachment("attachment-1")
        val fieldId = requireNotNull(viewModel.addCustomField(CustomFieldType.CONCEALED))
        viewModel.updateCustomFieldName(fieldId, "PIN")
        viewModel.updateCustomFieldValue(fieldId, "5678")

        val draft = viewModel.draft.value
        assertEquals("4111111111111111", draft.cardNumber)
        assertEquals("1234", draft.securityCode)
        assertEquals(PaymentCardBrand.VISA, draft.cardBrand)
        assertEquals("Use abroad", draft.notes.text)
        assertEquals(setOf("category-1"), draft.selectedCategoryIds)
        assertEquals(setOf("attachment-1"), draft.selectedAttachmentIds)
        assertEquals(CustomFieldType.CONCEALED, draft.customFields.single().type)
    }

    @Test
    fun clearRemovesCardNumberSecurityCodeAndTheRestOfTheDraft() {
        val viewModel = AddPaymentCardDraftViewModel()
        viewModel.updateTitle("Bank card")
        viewModel.updateCardNumber("5555555555554444")
        viewModel.updateSecurityCode("123")

        viewModel.clear()

        assertEquals(AddPaymentCardDraftState(), viewModel.draft.value)
        assertTrue(viewModel.draft.value.cardNumber.isEmpty())
        assertTrue(viewModel.draft.value.securityCode.isEmpty())
    }

    @Test
    fun canonicalizesLocalizedDigitsAndDoesNotSplitCardholderSurrogatePairs() {
        val viewModel = AddPaymentCardDraftViewModel()
        val prefix = "a".repeat(AppConstants.MAX_CARDHOLDER_NAME_LENGTH - 1)

        viewModel.updateCardNumber("٤١١١ ١١١١ ١١١١ ١١١١")
        viewModel.updateSecurityCode("١٢3")
        viewModel.updateCardholderName(prefix + "💳")

        assertEquals("4111111111111111", viewModel.draft.value.cardNumber)
        assertEquals("123", viewModel.draft.value.securityCode)
        assertEquals(prefix, viewModel.draft.value.cardholderName)
    }
}
