package com.turisla.hellopocket.ui.feature.search

import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.model.VaultItemType
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSearchEngineTest {

    @Test
    fun blankQueryDoesNotExposeEveryVaultEntry() {
        val entries = listOf(passwordEntry(id = "one", title = "Email"))

        assertTrue(VaultSearchEngine.search(entries, "   ").isEmpty())
    }

    @Test
    fun searchesPasswordTitleAccountAndNotes() {
        val entry = passwordEntry(
            id = "work-email",
            title = "Work email",
            username = "alex@example.com",
            notes = "Recovery codes are in the safe",
        )

        assertEquals("work-email", VaultSearchEngine.search(listOf(entry), "work").single().entryId)
        assertEquals(
            SearchMatchField.ACCOUNT,
            VaultSearchEngine.search(listOf(entry), "ALEX@example.com").single().matchedField,
        )
        assertEquals(
            "alex@example.com",
            VaultSearchEngine.search(listOf(entry), "ALEX@example.com").single().supportingText,
        )
        assertEquals(
            SearchMatchField.NOTES,
            VaultSearchEngine.search(listOf(entry), "recovery codes").single().matchedField,
        )
    }

    @Test
    fun searchesSecureNoteTitleAndContent() {
        val note = passwordEntry(
            id = "wifi-note",
            title = "Office Wi-Fi",
            type = VaultItemType.NOTE,
            content = "Guest network instructions",
        )

        assertEquals("wifi-note", VaultSearchEngine.search(listOf(note), "office").single().entryId)
        assertEquals(
            SearchMatchField.NOTES,
            VaultSearchEngine.search(listOf(note), "network instructions").single().matchedField,
        )
    }

    @Test
    fun multipleTermsCanMatchAcrossDifferentFields() {
        val entry = passwordEntry(
            id = "cross-field",
            title = "GitHub",
            notes = "Work organization",
        )

        assertEquals(
            "cross-field",
            VaultSearchEngine.search(listOf(entry), "github work").single().entryId,
        )
    }

    @Test
    fun longNoteProducesABoundedSnippetAroundTheMatch() {
        val note = passwordEntry(
            id = "long-note",
            title = "Reference",
            type = VaultItemType.NOTE,
            content = "x".repeat(10_000) + " target phrase " + "y".repeat(10_000),
        )

        val result = VaultSearchEngine.search(listOf(note), "target phrase").single()

        assertTrue(result.supportingText.orEmpty().contains("target phrase"))
        assertTrue(result.supportingText.orEmpty().length <= 102)
    }

    @Test
    fun longTitleProducesABoundedDisplayTitleAroundTheMatch() {
        val entry = passwordEntry(
            id = "long-title",
            title = "x".repeat(10_000) + " target phrase " + "y".repeat(10_000),
        )

        val result = VaultSearchEngine.search(listOf(entry), "target phrase").single()

        assertTrue(result.displayTitle.contains("target phrase"))
        assertTrue(result.displayTitle.length <= 162)
    }

    @Test
    fun passwordValueIsNeverSearchable() {
        val entry = passwordEntry(
            id = "secret",
            title = "Bank",
            password = "unique-secret-value",
        )

        assertTrue(VaultSearchEngine.search(listOf(entry), "unique-secret-value").isEmpty())
    }

    @Test
    fun fieldsHiddenByEntryTypeAreNotSearchable() {
        val passwordWithHiddenContent = passwordEntry(
            id = "password",
            title = "Password entry",
            content = "hidden note content",
        )
        val noteWithHiddenLoginFields = passwordEntry(
            id = "note",
            title = "Note entry",
            username = "hidden-account",
            notes = "hidden password notes",
            type = VaultItemType.NOTE,
            content = "visible note content",
        )

        assertTrue(
            VaultSearchEngine.search(
                listOf(passwordWithHiddenContent),
                "hidden note content",
            ).isEmpty()
        )
        assertTrue(
            VaultSearchEngine.search(
                listOf(noteWithHiddenLoginFields),
                "hidden-account",
            ).isEmpty()
        )
        assertEquals(
            "note",
            VaultSearchEngine.search(
                listOf(noteWithHiddenLoginFields),
                "visible note content",
            ).single().entryId,
        )
    }

    @Test(expected = CancellationException::class)
    fun searchStopsWhenCancellationIsRequested() {
        val entries = List(10) { index ->
            passwordEntry(id = index.toString(), title = "Entry $index")
        }
        var checks = 0

        VaultSearchEngine.search(
            entries = entries,
            rawQuery = "entry",
            checkCancellation = {
                checks++
                if (checks == 3) throw CancellationException("superseded query")
            },
        )
    }

    @Test
    fun titleMatchesAreRankedBeforeAccountAndNotesMatches() {
        val notesMatch = passwordEntry(
            id = "notes",
            title = "Other",
            notes = "GitHub",
            updatedAt = 30,
        )
        val accountMatch = passwordEntry(
            id = "account",
            title = "Another",
            username = "github-user",
            updatedAt = 20,
        )
        val titleMatch = passwordEntry(
            id = "title",
            title = "GitHub",
            updatedAt = 10,
        )

        assertEquals(
            listOf("title", "account", "notes"),
            VaultSearchEngine.search(
                listOf(notesMatch, accountMatch, titleMatch),
                "github",
            ).map(VaultSearchResult::entryId),
        )
    }

    private fun passwordEntry(
        id: String,
        title: String,
        username: String = "",
        password: String = "",
        notes: String = "",
        type: VaultItemType = VaultItemType.PASSWORD,
        content: String = "",
        updatedAt: Long = 0,
    ): PasswordEntry = PasswordEntry.newBuilder()
        .setId(id)
        .setTitle(title)
        .setUsername(username)
        .setPassword(password)
        .setNotes(notes)
        .setType(type)
        .setContent(content)
        .setUpdatedAt(updatedAt)
        .build()
}
