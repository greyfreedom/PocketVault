package com.turisla.hellopocket.ui.feature.detail

import androidx.compose.ui.text.input.TextFieldValue
import com.turisla.hellopocket.model.CustomField
import com.turisla.hellopocket.model.CustomFieldType
import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.model.PaymentCardBrand
import com.turisla.hellopocket.model.VaultItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailEditDraftViewModelTest {

    @Test
    fun generatedPasswordKeepsEditModeAndOtherDraftFields() {
        val originalEntry = createPasswordEntry()
        val viewModel = DetailEditDraftViewModel()

        viewModel.beginEditing(originalEntry)
        viewModel.updateTitle("Updated title")
        viewModel.updateUsername("updated@example.com")
        viewModel.updateNotes(TextFieldValue("Updated notes"))
        viewModel.addCategory("category-2")
        viewModel.addAttachment("attachment-2")
        viewModel.updateCustomFieldName("field-text", " Account alias ")
        viewModel.updateCustomFieldValue("field-text", "alex")
        viewModel.moveCustomField("field-hidden", 0)
        viewModel.updatePassword("generated-password")

        // 模拟从密码生成器返回后详情页再次请求初始化，现有草稿不能被旧条目覆盖。
        viewModel.beginEditing(originalEntry)

        val draft = viewModel.draft.value
        assertTrue(draft.isEditing)
        assertEquals("Updated title", draft.title)
        assertEquals("updated@example.com", draft.username)
        assertEquals("generated-password", draft.password)
        assertEquals("Updated notes", draft.notes.text)
        assertEquals(setOf("category-1", "category-2"), draft.selectedCategoryIds)
        assertEquals(setOf("attachment-1", "attachment-2"), draft.selectedAttachmentIds)
        assertEquals(listOf("field-hidden", "field-text"), draft.customFields.map { it.id })

        val updatedEntry = viewModel.buildUpdatedEntry(originalEntry)
        assertEquals("Updated title", updatedEntry.title)
        assertEquals("updated@example.com", updatedEntry.username)
        assertEquals("generated-password", updatedEntry.password)
        assertEquals("Updated notes", updatedEntry.notes)
        assertEquals(listOf("field-hidden", "field-text"), updatedEntry.customFieldsList.map { it.id })
        assertEquals("Account alias", updatedEntry.customFieldsList[1].name)
        assertEquals("alex", updatedEntry.customFieldsList[1].value)
        assertEquals(CustomFieldType.CONCEALED, updatedEntry.customFieldsList.first().type)
    }

    @Test
    fun generatedPasswordIsIgnoredAfterEditingEnds() {
        val viewModel = DetailEditDraftViewModel()
        viewModel.beginEditing(createPasswordEntry())
        viewModel.clear()

        viewModel.updatePassword("stale-generated-password")

        assertFalse(viewModel.draft.value.isEditing)
        assertTrue(viewModel.draft.value.password.isEmpty())
    }

    @Test
    fun paymentCardFieldsSurviveDetailEditingAndAreNormalized() {
        val originalEntry = PasswordEntry.newBuilder()
            .setId("card-1")
            .setTitle("Travel card")
            .setType(VaultItemType.PAYMENT_CARD)
            .setCardholderName("Alex Example")
            .setCardNumber("4111111111111111")
            .setCardBrand(PaymentCardBrand.VISA)
            .setExpirationMonth(12)
            .setExpirationYear(2032)
            .setSecurityCode("123")
            .setNotes("Original notes")
            .build()
        val viewModel = DetailEditDraftViewModel()

        viewModel.beginEditing(originalEntry)
        assertEquals("4111111111111111", viewModel.draft.value.cardNumber)
        viewModel.updateCardholderName("Taylor Example")
        viewModel.updateCardNumber("5555-5555-5555-4444")
        viewModel.updateCardBrand(PaymentCardBrand.MASTERCARD)
        viewModel.updateExpirationMonth(8)
        viewModel.updateExpirationYear(2035)
        viewModel.updateSecurityCode("9a876")
        viewModel.updateNotes(TextFieldValue("Updated notes"))

        val updatedEntry = viewModel.buildUpdatedEntry(originalEntry)
        assertEquals("Taylor Example", updatedEntry.cardholderName)
        assertEquals("5555555555554444", updatedEntry.cardNumber)
        assertEquals(PaymentCardBrand.MASTERCARD, updatedEntry.cardBrand)
        assertEquals(8, updatedEntry.expirationMonth)
        assertEquals(2035, updatedEntry.expirationYear)
        assertEquals("9876", updatedEntry.securityCode)
        assertEquals("Updated notes", updatedEntry.notes)
    }

    private fun createPasswordEntry(): PasswordEntry = PasswordEntry.newBuilder()
        .setId("entry-1")
        .setTitle("Original title")
        .setUsername("original@example.com")
        .setPassword("original-password")
        .setNotes("Original notes")
        .setType(VaultItemType.PASSWORD)
        .addCategoryIds("category-1")
        .addAttachmentIds("attachment-1")
        .addCustomFields(
            CustomField.newBuilder()
                .setId("field-text")
                .setName("Alias")
                .setValue("old")
                .setType(CustomFieldType.TEXT)
        )
        .addCustomFields(
            CustomField.newBuilder()
                .setId("field-hidden")
                .setName("PIN")
                .setValue("1234")
                .setType(CustomFieldType.CONCEALED)
        )
        .build()
}
