package com.turisla.hellopocket.ui.feature.addPassword

import androidx.compose.ui.text.input.TextFieldValue
import com.turisla.hellopocket.model.CustomFieldType
import com.turisla.hellopocket.utils.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddPasswordDraftViewModelTest {

    @Test
    fun generatedPasswordOnlyReplacesPasswordAndPreservesExistingDraft() {
        val viewModel = AddPasswordDraftViewModel()
        viewModel.updateTitle("Email")
        viewModel.updateUsername("user@example.com")
        viewModel.updateNotes(TextFieldValue("personal account"))
        viewModel.addCategory("category-1")
        viewModel.addAttachment("attachment-1")

        viewModel.updatePassword("generated-password")

        val draft = viewModel.draft.value
        assertEquals("Email", draft.title)
        assertEquals("user@example.com", draft.username)
        assertEquals("generated-password", draft.password)
        assertEquals("personal account", draft.notes.text)
        assertEquals(setOf("category-1"), draft.selectedCategoryIds)
        assertEquals(setOf("attachment-1"), draft.selectedAttachmentIds)
    }

    @Test
    fun clearRemovesAllSensitiveDraftData() {
        val viewModel = AddPasswordDraftViewModel()
        viewModel.updateTitle("Email")
        viewModel.updateUsername("user@example.com")
        viewModel.updatePassword("secret")
        viewModel.updateNotes(TextFieldValue("notes"))
        viewModel.addCategory("category-1")
        viewModel.addAttachment("attachment-1")

        viewModel.clear()

        assertEquals(AddPasswordDraftState(), viewModel.draft.value)
        assertTrue(viewModel.draft.value.password.isEmpty())
    }

    @Test
    fun customFieldsCanBeEditedReorderedRemovedAndRestored() {
        val viewModel = AddPasswordDraftViewModel()
        val textFieldId = requireNotNull(viewModel.addCustomField(CustomFieldType.TEXT))
        val hiddenFieldId = requireNotNull(viewModel.addCustomField(CustomFieldType.CONCEALED))

        viewModel.updateCustomFieldName(textFieldId, "Account\nID")
        viewModel.updateCustomFieldValue(textFieldId, "A-100")
        viewModel.updateCustomFieldName(hiddenFieldId, "PIN")
        viewModel.updateCustomFieldValue(hiddenFieldId, "1234")
        viewModel.moveCustomField(hiddenFieldId, 0)

        assertEquals(listOf(hiddenFieldId, textFieldId), viewModel.draft.value.customFields.map { it.id })
        assertEquals("Account ID", viewModel.draft.value.customFields[1].name)

        val removed = viewModel.draft.value.customFields[1]
        viewModel.removeCustomField(textFieldId)
        assertEquals(listOf(hiddenFieldId), viewModel.draft.value.customFields.map { it.id })

        viewModel.restoreCustomField(removed, 1)
        assertEquals(listOf(hiddenFieldId, textFieldId), viewModel.draft.value.customFields.map { it.id })
        assertEquals(CustomFieldType.CONCEALED, viewModel.draft.value.customFields.first().type)
    }

    @Test
    fun customFieldCountIsBounded() {
        val viewModel = AddPasswordDraftViewModel()
        repeat(AppConstants.MAX_CUSTOM_FIELDS_PER_ENTRY) {
            requireNotNull(viewModel.addCustomField(CustomFieldType.TEXT))
        }

        assertNull(viewModel.addCustomField(CustomFieldType.TEXT))
        assertEquals(AppConstants.MAX_CUSTOM_FIELDS_PER_ENTRY, viewModel.draft.value.customFields.size)
    }

    @Test
    fun customFieldLimitsNeverSplitUnicodeSurrogatePairs() {
        val viewModel = AddPasswordDraftViewModel()
        val fieldId = requireNotNull(viewModel.addCustomField(CustomFieldType.TEXT))
        val emoji = "😀"

        viewModel.updateCustomFieldName(fieldId, "x".repeat(99) + emoji + "tail")
        viewModel.updateCustomFieldValue(
            fieldId,
            "y".repeat(AppConstants.MAX_CUSTOM_FIELD_VALUE_LENGTH - 1) + emoji + "tail",
        )

        val field = viewModel.draft.value.customFields.single()
        assertEquals(99, field.name.length)
        assertEquals(AppConstants.MAX_CUSTOM_FIELD_VALUE_LENGTH - 1, field.value.length)
        assertTrue(field.name.lastOrNull()?.isHighSurrogate() != true)
        assertTrue(field.value.lastOrNull()?.isHighSurrogate() != true)

        viewModel.updateCustomFieldName(fieldId, "x".repeat(98) + emoji + "tail")
        assertEquals("x".repeat(98) + emoji, viewModel.draft.value.customFields.single().name)
    }
}
