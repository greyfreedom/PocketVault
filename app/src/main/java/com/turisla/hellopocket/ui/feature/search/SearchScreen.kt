package com.turisla.hellopocket.ui.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.VaultItemType
import com.turisla.hellopocket.ui.feature.common.AppEmptyState
import com.turisla.hellopocket.ui.feature.common.AppIconTile
import com.turisla.hellopocket.ui.theme.AppSpacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onResultClick: (String) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching = query.isNotBlank() &&
        (searchResults.query != query || searchResults.isSearching)
    val visibleResults = if (searchResults.query == query) {
        searchResults.results
    } else {
        emptyList()
    }

    SearchScreenContent(
        query = query,
        results = visibleResults,
        isSearching = isSearching,
        onQueryChanged = viewModel::onQueryChanged,
        onClearQuery = viewModel::clearQuery,
        onNavigateBack = onNavigateBack,
        onResultClick = onResultClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreenContent(
    query: String,
    results: List<VaultSearchResult>,
    isSearching: Boolean,
    onQueryChanged: (String) -> Unit,
    onClearQuery: () -> Unit,
    onNavigateBack: () -> Unit,
    onResultClick: (String) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SearchTopAppBar(
                query = query,
                onQueryChanged = onQueryChanged,
                onClearQuery = onClearQuery,
                onNavigateBack = onNavigateBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        when {
            query.isBlank() -> SearchEmptyState(
                modifier = Modifier.padding(innerPadding),
                icon = Icons.Outlined.Search,
                title = stringResource(R.string.search_vault),
                message = stringResource(R.string.search_empty_message),
            )

            isSearching -> SearchLoadingState(
                modifier = Modifier.padding(innerPadding),
            )

            results.isEmpty() -> SearchEmptyState(
                modifier = Modifier.padding(innerPadding),
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.no_search_results),
                message = stringResource(R.string.search_no_results_hint),
            )

            else -> SearchResultList(
                modifier = Modifier.padding(innerPadding),
                query = query,
                results = results,
                onResultClick = onResultClick,
            )
        }
    }
}

@Composable
private fun SearchLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopAppBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClearQuery: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusRequester) {
        // FocusRequester 会在配置变更后重建，因此每次进入新的 composition 都重新聚焦。
        focusRequester.requestFocus()
    }

    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
                    // 普通输入保持 56dp，大字体模式仍可向上扩展，避免文字底部被裁切。
                    .heightIn(min = 56.dp)
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClearQuery) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.clear_search),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { keyboardController?.hide() }
                ),
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@Composable
private fun SearchEmptyState(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    message: String,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AppEmptyState(
            icon = icon,
            title = title,
            description = message,
        )
    }
}

@Composable
private fun SearchResultList(
    modifier: Modifier,
    query: String,
    results: List<VaultSearchResult>,
    onResultClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = AppSpacing.sm,
            end = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        item(contentType = SearchListContentType.HEADER) {
            Text(
                text = pluralStringResource(
                    id = R.plurals.search_result_count,
                    count = results.size,
                    results.size,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        items(
            items = results,
            key = { result -> result.entryId },
            contentType = { SearchListContentType.RESULT },
        ) { result ->
            SearchResultItem(
                result = result,
                query = query,
                onClick = { onResultClick(result.entryId) },
            )
        }
    }
}

private enum class SearchListContentType {
    HEADER,
    RESULT,
}

@Composable
private fun SearchResultItem(
    result: VaultSearchResult,
    query: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val isNote = result.entryType == VaultItemType.NOTE
            val iconColor = if (isNote) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            }
            AppIconTile(
                imageVector = if (isNote) Icons.Filled.NoteAlt else Icons.Outlined.Key,
                contentDescription = null,
                containerColor = Color.Transparent,
                contentColor = iconColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = iconColor.copy(alpha = 0.32f),
                ),
            )
            Spacer(modifier = Modifier.width(AppSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                HighlightedText(
                    text = result.displayTitle,
                    query = query,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                result.supportingText?.let { supportingText ->
                    Spacer(modifier = Modifier.size(4.dp))
                    Row {
                        Text(
                            text = stringResource(result.supportingLabelResId()) + ": ",
                            modifier = Modifier.alignByBaseline(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        HighlightedText(
                            text = supportingText,
                            query = query,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            modifier = Modifier
                                .weight(1f)
                                .alignByBaseline(),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HighlightedText(
    text: String,
    query: String,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val annotatedText = remember(text, query, highlightColor) {
        highlightMatches(text, query, highlightColor)
    }
    Text(
        text = annotatedText,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun VaultSearchResult.supportingLabelResId(): Int = when (supportingField) {
    SearchMatchField.ACCOUNT -> R.string.username
    SearchMatchField.NOTES -> if (entryType == VaultItemType.NOTE) {
        R.string.content
    } else {
        R.string.notes
    }
    SearchMatchField.TITLE, null -> R.string.notes
}

private fun highlightMatches(
    text: String,
    query: String,
    highlightColor: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)
    val terms = query
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotEmpty)
        .distinct()

    terms.forEach { term ->
        var startIndex = text.indexOf(term, ignoreCase = true)
        while (startIndex >= 0) {
            addStyle(
                style = SpanStyle(
                    color = highlightColor,
                    fontWeight = FontWeight.SemiBold,
                ),
                start = startIndex,
                end = startIndex + term.length,
            )
            startIndex = text.indexOf(
                string = term,
                startIndex = startIndex + term.length,
                ignoreCase = true,
            )
        }
    }
}
