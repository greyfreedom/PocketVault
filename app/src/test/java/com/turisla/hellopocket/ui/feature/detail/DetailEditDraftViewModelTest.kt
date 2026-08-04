package com.turisla.hellopocket.ui.feature.detail

import androidx.compose.ui.text.input.TextFieldValue
import com.turisla.hellopocket.model.PasswordEntry
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

        val updatedEntry = viewModel.buildUpdatedEntry(originalEntry)
        assertEquals("Updated title", updatedEntry.title)
        assertEquals("updated@example.com", updatedEntry.username)
        assertEquals("generated-password", updatedEntry.password)
        assertEquals("Updated notes", updatedEntry.notes)
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

    private fun createPasswordEntry(): PasswordEntry = PasswordEntry.newBuilder()
        .setId("entry-1")
        .setTitle("Original title")
        .setUsername("original@example.com")
        .setPassword("original-password")
        .setNotes("Original notes")
        .setType(VaultItemType.PASSWORD)
        .addCategoryIds("category-1")
        .addAttachmentIds("attachment-1")
        .build()
}
