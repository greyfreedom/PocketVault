package com.turisla.hellopocket.ui.feature.home

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.Category
import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.model.VaultItemType
import com.turisla.hellopocket.router.RouteDetail
import com.turisla.hellopocket.ui.feature.common.AppEmptyState
import com.turisla.hellopocket.ui.feature.common.AppIconTile
import com.turisla.hellopocket.ui.feature.common.ConfirmDeleteDialog
import com.turisla.hellopocket.ui.feature.common.getCategoryDisplayName
import com.turisla.hellopocket.ui.theme.AppSpacing
import com.turisla.hellopocket.utils.AppConstants

@Composable
fun HomePage(
    navController: NavController,
    passwordEntries: List<PasswordEntry>,
    viewModel: HomePageViewModel,
    onCategoryManagementClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val selectedItemType by viewModel.selectedItemType.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, context) {
        viewModel.repositoryErrorEvents.collect { event ->
            Toast.makeText(
                context,
                context.getString(event.messageResId),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    HomePageContent(
        passwordEntries = passwordEntries,
        categories = categories,
        selectedCategoryId = selectedCategoryId,
        selectedItemType = selectedItemType,
        onPasswordClick = { entry -> navController.navigate(RouteDetail(id = entry.id)) },
        onDeletePassword = viewModel::deletePassword,
        onCategorySelected = viewModel::onCategorySelected,
        onItemTypeSelected = viewModel::onItemTypeSelected,
        onToggleFavorite = viewModel::toggleFavorite,
        onCategoryManagementClick = onCategoryManagementClick,
    )
}

@Composable
private fun HomePageContent(
    passwordEntries: List<PasswordEntry>,
    categories: List<Category>,
    selectedCategoryId: String,
    selectedItemType: VaultItemType?,
    onPasswordClick: (PasswordEntry) -> Unit,
    onDeletePassword: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onItemTypeSelected: (VaultItemType?) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCategoryManagementClick: () -> Unit,
) {
    var entryToDelete by remember { mutableStateOf<PasswordEntry?>(null) }

    entryToDelete?.let { entry ->
        ConfirmDeleteDialog(
            itemName = entry.title,
            onConfirm = { onDeletePassword(entry.id) },
            onDismiss = { entryToDelete = null },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        VaultFilterBar(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            selectedItemType = selectedItemType,
            onCategorySelected = onCategorySelected,
            onItemTypeSelected = onItemTypeSelected,
            onCategoryManagementClick = onCategoryManagementClick,
        )

        PasswordListView(
            passwordEntries = passwordEntries,
            onPasswordClick = onPasswordClick,
            onPasswordDelete = { entryToDelete = it },
            onFavoriteToggle = onToggleFavorite,
        )
    }
}

/** 类型与分类各占一个下拉筛选入口，避免窄屏上必须横向滑动才能选择分类。 */
@Composable
private fun VaultFilterBar(
    categories: List<Category>,
    selectedCategoryId: String,
    selectedItemType: VaultItemType?,
    onCategorySelected: (String) -> Unit,
    onItemTypeSelected: (VaultItemType?) -> Unit,
    onCategoryManagementClick: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = AppSpacing.md,
            top = AppSpacing.xs,
            end = AppSpacing.md,
            bottom = AppSpacing.sm,
        ),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        item(key = "type") {
            VaultTypeFilterChip(
                selectedItemType = selectedItemType,
                onItemTypeSelected = onItemTypeSelected,
            )
        }
        item(key = "category") {
            CategoryFilterChip(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onCategorySelected = onCategorySelected,
                onCategoryManagementClick = onCategoryManagementClick,
            )
        }
    }
}

@Composable
private fun VaultTypeFilterChip(
    selectedItemType: VaultItemType?,
    onItemTypeSelected: (VaultItemType?) -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }
    val isFiltered = selectedItemType == VaultItemType.PASSWORD ||
        selectedItemType == VaultItemType.NOTE
    val labelResId = when (selectedItemType) {
        VaultItemType.PASSWORD -> R.string.type_password
        VaultItemType.NOTE -> R.string.type_note
        VaultItemType.UNRECOGNIZED, null -> R.string.all_types
    }
    val leadingIcon = when (selectedItemType) {
        VaultItemType.PASSWORD -> Icons.Outlined.Password
        VaultItemType.NOTE -> Icons.AutoMirrored.Filled.Note
        VaultItemType.UNRECOGNIZED, null -> Icons.Filled.Apps
    }

    Box {
        FilterChip(
            selected = isFiltered,
            onClick = { showDropdown = true },
            label = { Text(stringResource(labelResId)) },
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = stringResource(R.string.select_type),
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            shape = MaterialTheme.shapes.small,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = isFiltered,
                borderColor = Color.Transparent,
                selectedBorderColor = Color.Transparent,
            ),
        )

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.all_types)) },
                leadingIcon = { Icon(Icons.Filled.Apps, contentDescription = null) },
                trailingIcon = {
                    if (selectedItemType == null) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onClick = {
                    onItemTypeSelected(null)
                    showDropdown = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.type_password)) },
                leadingIcon = { Icon(Icons.Outlined.Password, contentDescription = null) },
                trailingIcon = {
                    if (selectedItemType == VaultItemType.PASSWORD) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onClick = {
                    onItemTypeSelected(VaultItemType.PASSWORD)
                    showDropdown = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.type_note)) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.Note, contentDescription = null)
                },
                trailingIcon = {
                    if (selectedItemType == VaultItemType.NOTE) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onClick = {
                    onItemTypeSelected(VaultItemType.NOTE)
                    showDropdown = false
                },
            )
        }
    }
}

@Composable
private fun CategoryFilterChip(
    categories: List<Category>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    onCategoryManagementClick: () -> Unit,
) {
    var showDropdown by remember { mutableStateOf(false) }
    val selectedCategory = remember(categories, selectedCategoryId) {
        categories.firstOrNull { it.id == selectedCategoryId }
    }
    val isFiltered = selectedCategoryId != AppConstants.CATEGORY_ID_ALL

    Box {
        FilterChip(
            selected = isFiltered,
            onClick = { showDropdown = true },
            label = {
                Text(
                    text = selectedCategory?.let { getCategoryDisplayName(it) }
                        ?: stringResource(R.string.all_categories),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                if (selectedCategory != null && isFiltered) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(selectedCategory.color.toColorInt())),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                }
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = stringResource(R.string.select_category),
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            shape = MaterialTheme.shapes.small,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = isFiltered,
                borderColor = Color.Transparent,
                selectedBorderColor = Color.Transparent,
            ),
        )

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
            modifier = Modifier.heightIn(max = 420.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_categories)) },
                leadingIcon = {
                    Icon(Icons.Outlined.Tune, contentDescription = null)
                },
                onClick = {
                    showDropdown = false
                    onCategoryManagementClick()
                },
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(getCategoryDisplayName(category)) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(category.color.toColorInt())),
                        )
                    },
                    trailingIcon = {
                        if (selectedCategoryId == category.id) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        onCategorySelected(category.id)
                        showDropdown = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PasswordListView(
    passwordEntries: List<PasswordEntry>,
    onPasswordClick: (PasswordEntry) -> Unit,
    onPasswordDelete: (PasswordEntry) -> Unit,
    onFavoriteToggle: (String) -> Unit,
) {
    if (passwordEntries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppEmptyState(
                icon = Icons.Outlined.Key,
                title = stringResource(R.string.no_passwords_found),
                description = stringResource(R.string.vault_empty_message),
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppSpacing.md,
            top = AppSpacing.xxs,
            end = AppSpacing.md,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        items(
            items = passwordEntries,
            key = { it.id },
            contentType = { it.type },
        ) { entry ->
            PasswordListItem(
                entry = entry,
                onClick = { onPasswordClick(entry) },
                onDelete = { onPasswordDelete(entry) },
                onFavoriteToggle = { onFavoriteToggle(entry.id) },
                isFavorited = entry.categoryIdsList.contains(
                    AppConstants.CATEGORY_ID_FAVORITES,
                ),
            )
        }
    }
}

@Composable
fun PasswordListItem(
    entry: PasswordEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onFavoriteToggle: () -> Unit,
    isFavorited: Boolean,
    modifier: Modifier = Modifier,
) {
    var showActions by remember { mutableStateOf(false) }
    val subtitle = if (entry.type == VaultItemType.NOTE) {
        entry.content.replace(Regex("\\s+"), " ").trim()
    } else {
        entry.username.trim()
    }
    val iconColor = if (entry.type == VaultItemType.NOTE) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(start = AppSpacing.md, end = AppSpacing.xxs, top = AppSpacing.xs, bottom = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconTile(
                imageVector = if (entry.type == VaultItemType.NOTE) {
                    Icons.Filled.NoteAlt
                } else {
                    Icons.Outlined.Key
                },
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
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (isFavorited) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = stringResource(R.string.favorites),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Box {
                IconButton(onClick = { showActions = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.totp_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = showActions,
                    onDismissRequest = { showActions = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isFavorited) {
                                    stringResource(R.string.remove_from_favorites)
                                } else {
                                    stringResource(R.string.add_to_favorites)
                                },
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isFavorited) {
                                    Icons.Filled.Favorite
                                } else {
                                    Icons.Outlined.FavoriteBorder
                                },
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showActions = false
                            onFavoriteToggle()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            showActions = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
