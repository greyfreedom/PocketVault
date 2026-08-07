package com.turisla.hellopocket.ui.feature.addSecureNote

import androidx.compose.ui.text.input.TextFieldValue
import com.turisla.hellopocket.model.CustomFieldType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddSecureNoteDraftViewModelTest {

    @Test
    fun updatesPreserveTheRestOfTheDraft() {
        val viewModel = AddSecureNoteDraftViewModel()

        viewModel.updateTitle("Recovery codes")
        viewModel.updateContent(TextFieldValue("sensitive note"))
        viewModel.addCategory("category-1")
        viewModel.addAttachment("attachment-1")

        val draft = viewModel.draft.value
        assertEquals("Recovery codes", draft.title)
        assertEquals("sensitive note", draft.content.text)
        assertEquals(setOf("category-1"), draft.selectedCategoryIds)
        assertEquals(setOf("attachment-1"), draft.selectedAttachmentIds)
    }

    @Test
    fun clearRemovesAllSensitiveDraftData() {
        val viewModel = AddSecureNoteDraftViewModel()
        viewModel.updateTitle("Recovery codes")
        viewModel.updateContent(TextFieldValue("sensitive note"))
        viewModel.addCategory("category-1")
        viewModel.addAttachment("attachment-1")

        viewModel.clear()

        assertEquals(AddSecureNoteDraftState(), viewModel.draft.value)
        assertTrue(viewModel.draft.value.content.text.isEmpty())
    }

    @Test
    fun customFieldDraftIsKeptWithTheSecureNote() {
        val viewModel = AddSecureNoteDraftViewModel()
        val fieldId = requireNotNull(viewModel.addCustomField(CustomFieldType.CONCEALED))

        viewModel.updateCustomFieldName(fieldId, "Recovery answer")
        viewModel.updateCustomFieldValue(fieldId, "blue")

        val field = viewModel.draft.value.customFields.single()
        assertEquals(fieldId, field.id)
        assertEquals("Recovery answer", field.name)
        assertEquals("blue", field.value)
        assertEquals(CustomFieldType.CONCEALED, field.type)

        viewModel.clear()
        assertTrue(viewModel.draft.value.customFields.isEmpty())
    }
}
