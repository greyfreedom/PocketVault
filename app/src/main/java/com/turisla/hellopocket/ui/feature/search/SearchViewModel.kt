package com.turisla.hellopocket.ui.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turisla.hellopocket.data.PasswordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

internal data class SearchResultsState(
    val query: String = "",
    val results: List<VaultSearchResult> = emptyList(),
    val isSearching: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    passwordRepository: PasswordRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    internal val query: StateFlow<String> = _query.asStateFlow()

    internal val searchResults: StateFlow<SearchResultsState> = combine(
        _query,
        passwordRepository.passwordEntries,
        ::Pair,
    ).transformLatest { (currentQuery, entries) ->
        if (currentQuery.isBlank()) {
            emit(SearchResultsState(query = currentQuery))
            return@transformLatest
        }

        emit(
            SearchResultsState(
                query = currentQuery,
                isSearching = true,
            )
        )

        // 保险库最多允许 100,000 条记录，搜索必须离开主线程并能被后续按键取消。
        val results = withContext(Dispatchers.Default) {
            val searchContext = currentCoroutineContext()
            VaultSearchEngine.search(
                entries = entries,
                rawQuery = currentQuery,
                checkCancellation = searchContext::ensureActive,
            )
        }
        emit(
            SearchResultsState(
                query = currentQuery,
                results = results,
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = 5_000,
            replayExpirationMillis = 0,
        ),
        initialValue = SearchResultsState(),
    )

    fun onQueryChanged(value: String) {
        _query.value = value
    }

    fun clearQuery() {
        _query.value = ""
    }
}
