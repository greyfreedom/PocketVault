package com.turisla.hellopocket.ui.feature.addPassword

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
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
}
