package com.turisla.hellopocket.ui.feature.search

import com.turisla.hellopocket.model.CustomFieldType
import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.model.VaultItemType
import java.util.Locale

internal enum class SearchMatchField {
    TITLE,
    ACCOUNT,
    CUSTOM_FIELD,
    NOTES,
}

internal data class VaultSearchResult(
    val entryId: String,
    val entryType: VaultItemType,
    val displayTitle: String,
    val matchedField: SearchMatchField,
    val supportingField: SearchMatchField?,
    val supportingText: String?,
)

/**
 * 保险库本地搜索引擎。
 *
 * 索引标题、账号、自定义字段名、明文自定义字段值和备注/笔记内容。
 * 密码正文与隐藏型自定义字段值始终不进入索引，避免搜索结果意外暴露秘密。
 */
internal object VaultSearchEngine {
    private val whitespaceRegex = Regex("\\s+")

    fun search(
        entries: List<PasswordEntry>,
        rawQuery: String,
        checkCancellation: () -> Unit = {},
    ): List<VaultSearchResult> {
        val terms = rawQuery
            .trim()
            .split(whitespaceRegex)
            .filter(String::isNotEmpty)
            .distinctBy { term -> term.lowercase(Locale.ROOT) }
        if (terms.isEmpty()) return emptyList()
        val queryPhrase = terms.joinToString(" ")

        return entries.mapNotNull { entry ->
            checkCancellation()
            val fields = entry.searchableFields()
            val isMatch = terms.all { term ->
                fields.any { field -> field.contains(term) }
            }
            if (!isMatch) return@mapNotNull null

            val bestField = fields.bestMatchFor(terms)
            val supportingField = if (bestField.type == SearchMatchField.TITLE) {
                fields.firstOrNull { field -> field.type != SearchMatchField.TITLE }
            } else {
                bestField
            }
            RankedSearchResult(
                result = VaultSearchResult(
                    entryId = entry.id,
                    entryType = entry.type,
                    displayTitle = fields.first().originalText.toSearchSnippet(
                        terms = terms,
                        maxLength = 160,
                    ),
                    matchedField = bestField.type,
                    supportingField = supportingField?.type,
                    supportingText = supportingField?.toSupportingText(terms),
                ),
                rank = fields.rankFor(queryPhrase, terms),
                updatedAt = entry.updatedAt,
                title = entry.title,
            )
        }.also { checkCancellation() }.sortedWith(
            compareBy<RankedSearchResult> { it.rank }
                .thenByDescending { it.updatedAt }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        ).also { checkCancellation() }.map { it.result }
    }

    private fun PasswordEntry.searchableFields(): List<SearchableField> = buildList {
        add(SearchableField(FieldKind.TITLE, title))
        if (type == VaultItemType.NOTE) {
            if (content.isNotBlank()) {
                add(SearchableField(FieldKind.NOTES, content))
            }
        } else {
            if (username.isNotBlank()) {
                add(SearchableField(FieldKind.ACCOUNT, username))
            }
            if (notes.isNotBlank()) {
                add(SearchableField(FieldKind.NOTES, notes))
            }
        }

        customFieldsList.forEach { field ->
            if (field.name.isNotBlank()) {
                add(SearchableField(FieldKind.CUSTOM_NAME, field.name))
            }
            if (field.type == CustomFieldType.TEXT && field.value.isNotBlank()) {
                add(
                    SearchableField(
                        kind = FieldKind.CUSTOM_VALUE,
                        originalText = field.value,
                        supportingPrefix = field.name.takeIf(String::isNotBlank)?.let { "$it: " },
                    )
                )
            }
        }
    }

    private fun List<SearchableField>.bestMatchFor(terms: List<String>): SearchableField {
        // 单一字段完整命中时优先展示它，便于用户理解结果为何出现。
        FieldKind.entries.forEach { kind ->
            firstOrNull { it.kind == kind && it.matchesAll(terms) }?.let { return it }
        }

        // 多词分布在不同字段时，展示命中词最多且更具辨识度的字段。
        return maxWithOrNull(
            compareBy<SearchableField> { field -> field.matchCount(terms) }
                .thenBy { field -> FieldKind.entries.size - field.kind.ordinal }
        ) ?: first()
    }

    private fun List<SearchableField>.rankFor(
        queryPhrase: String,
        terms: List<String>,
    ): Int {
        val title = first { it.kind == FieldKind.TITLE }
        val account = firstOrNull { it.kind == FieldKind.ACCOUNT }
        val customNames = filter { it.kind == FieldKind.CUSTOM_NAME }
        val customValues = filter { it.kind == FieldKind.CUSTOM_VALUE }
        val notes = filter { it.kind == FieldKind.NOTES }

        return when {
            title.originalText.equals(queryPhrase, ignoreCase = true) -> 0
            title.originalText.startsWith(queryPhrase, ignoreCase = true) -> 1
            title.matchesAll(terms) -> 2
            account?.originalText?.equals(queryPhrase, ignoreCase = true) == true -> 3
            account?.originalText?.startsWith(queryPhrase, ignoreCase = true) == true -> 4
            account?.matchesAll(terms) == true -> 5
            customNames.any { it.originalText.equals(queryPhrase, ignoreCase = true) } -> 6
            customNames.any { it.originalText.startsWith(queryPhrase, ignoreCase = true) } -> 7
            customNames.any { it.matchesAll(terms) } -> 8
            customValues.any { it.originalText.equals(queryPhrase, ignoreCase = true) } -> 9
            customValues.any { it.originalText.startsWith(queryPhrase, ignoreCase = true) } -> 10
            customValues.any { it.matchesAll(terms) } -> 11
            notes.any { it.matchesAll(terms) } -> 12
            else -> 13
        }
    }

    private fun SearchableField.matchesAll(terms: List<String>): Boolean =
        originalText.isNotEmpty() && terms.all { term -> contains(term) }

    private fun SearchableField.contains(term: String): Boolean =
        originalText.contains(term, ignoreCase = true)

    private fun SearchableField.matchCount(terms: List<String>): Int =
        terms.count { term -> contains(term) }

    private fun SearchableField.toSupportingText(terms: List<String>): String =
        supportingPrefix.orEmpty() + originalText.toSearchSnippet(terms)

    /**
     * 只截取命中位置附近的小窗口，避免为展示摘要而复制整段大型笔记。
     */
    private fun String.toSearchSnippet(
        terms: List<String>,
        maxLength: Int = 100,
    ): String {
        val sourceLength = length
        val firstMatch = terms.mapNotNull { term ->
            indexOf(term, ignoreCase = true).takeIf { index -> index >= 0 }
        }.minOrNull() ?: 0

        var start = (firstMatch - maxLength / 3).coerceAtLeast(0)
        val end = (start + maxLength).coerceAtMost(length)
        if (end - start < maxLength) {
            start = (end - maxLength).coerceAtLeast(0)
        }
        val excerpt = substring(start, end)
            .replace(whitespaceRegex, " ")
            .trim()

        return buildString(excerpt.length + 2) {
            if (start > 0) append('…')
            append(excerpt)
            if (end < sourceLength) append('…')
        }
    }

    private data class SearchableField(
        val kind: FieldKind,
        val originalText: String,
        val supportingPrefix: String? = null,
    ) {
        val type: SearchMatchField
            get() = kind.matchField
    }

    private enum class FieldKind(val matchField: SearchMatchField) {
        TITLE(SearchMatchField.TITLE),
        ACCOUNT(SearchMatchField.ACCOUNT),
        CUSTOM_NAME(SearchMatchField.CUSTOM_FIELD),
        CUSTOM_VALUE(SearchMatchField.CUSTOM_FIELD),
        NOTES(SearchMatchField.NOTES),
    }

    private data class RankedSearchResult(
        val result: VaultSearchResult,
        val rank: Int,
        val updatedAt: Long,
        val title: String,
    )
}
