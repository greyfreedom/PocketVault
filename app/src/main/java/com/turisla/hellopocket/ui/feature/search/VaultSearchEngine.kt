package com.turisla.hellopocket.ui.feature.search

import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.model.VaultItemType
import java.util.Locale

internal enum class SearchMatchField {
    TITLE,
    ACCOUNT,
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
 * 只索引标题、账号和用户填写的备注/笔记内容，绝不把密码正文加入索引。
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
        add(SearchableField(SearchMatchField.TITLE, title))
        if (type == VaultItemType.NOTE) {
            if (content.isNotBlank()) {
                add(SearchableField(SearchMatchField.NOTES, content))
            }
        } else {
            if (username.isNotBlank()) {
                add(SearchableField(SearchMatchField.ACCOUNT, username))
            }
            if (notes.isNotBlank()) {
                add(SearchableField(SearchMatchField.NOTES, notes))
            }
        }
    }

    private fun List<SearchableField>.bestMatchFor(terms: List<String>): SearchableField {
        // 单一字段完整命中时优先展示它，便于用户理解结果为何出现。
        firstOrNull { it.type == SearchMatchField.TITLE && it.matchesAll(terms) }?.let { return it }
        firstOrNull { it.type == SearchMatchField.ACCOUNT && it.matchesAll(terms) }?.let { return it }
        firstOrNull { it.type == SearchMatchField.NOTES && it.matchesAll(terms) }?.let { return it }

        // 多词分布在不同字段时，展示命中词最多的字段；同分时优先账号和备注。
        return maxWithOrNull(
            compareBy<SearchableField> { field -> field.matchCount(terms) }
                .thenBy { field ->
                    when (field.type) {
                        SearchMatchField.TITLE -> 0
                        SearchMatchField.NOTES -> 1
                        SearchMatchField.ACCOUNT -> 2
                    }
                }
        ) ?: first()
    }

    private fun List<SearchableField>.rankFor(
        queryPhrase: String,
        terms: List<String>,
    ): Int {
        val title = first { it.type == SearchMatchField.TITLE }
        val account = firstOrNull { it.type == SearchMatchField.ACCOUNT }
        val notes = filter { it.type == SearchMatchField.NOTES }

        return when {
            title.originalText.equals(queryPhrase, ignoreCase = true) -> 0
            title.originalText.startsWith(queryPhrase, ignoreCase = true) -> 1
            title.matchesAll(terms) -> 2
            account?.originalText?.equals(queryPhrase, ignoreCase = true) == true -> 3
            account?.originalText?.startsWith(queryPhrase, ignoreCase = true) == true -> 4
            account?.matchesAll(terms) == true -> 5
            notes.any { it.matchesAll(terms) } -> 6
            else -> 7
        }
    }

    private fun SearchableField.matchesAll(terms: List<String>): Boolean =
        originalText.isNotEmpty() && terms.all { term -> contains(term) }

    private fun SearchableField.contains(term: String): Boolean =
        originalText.contains(term, ignoreCase = true)

    private fun SearchableField.matchCount(terms: List<String>): Int =
        terms.count { term -> contains(term) }

    private fun SearchableField.toSupportingText(terms: List<String>): String =
        originalText.toSearchSnippet(terms)

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
        val type: SearchMatchField,
        val originalText: String,
    )

    private data class RankedSearchResult(
        val result: VaultSearchResult,
        val rank: Int,
        val updatedAt: Long,
        val title: String,
    )
}
