package com.turisla.hellopocket.ui.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Password
import com.turisla.hellopocket.model.VaultItemType
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.Category
import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.router.RouteDetail
import com.turisla.hellopocket.ui.feature.common.ConfirmDeleteDialog
import androidx.core.graphics.toColorInt
import com.turisla.hellopocket.ui.feature.common.getCategoryDisplayName
import com.turisla.hellopocket.utils.AppConstants

@Composable
fun HomePage(
    navController: NavController,
    passwordEntries: List<PasswordEntry>,
    viewModel: HomePageViewModel,
    onCategoryManagementClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val selectedItemType by viewModel.selectedItemType.collectAsStateWithLifecycle()
    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, context) {
        viewModel.repositoryErrorEvents.collect { event ->
            android.widget.Toast.makeText(
                context,
                context.getString(event.messageResId),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    HomePageContent(
        passwordEntries = passwordEntries,
        categories = categories,
        selectedCategoryId = selectedCategoryId,
        selectedItemType = selectedItemType,
        isGridView = isGridView,
        searchQuery = searchQuery,
        onPasswordClick = { entry -> navController.navigate(RouteDetail(id = entry.id)) },
        onDeletePassword = viewModel::deletePassword,
        onCategorySelected = viewModel::onCategorySelected,
        onItemTypeSelected = viewModel::onItemTypeSelected,
        onToggleViewMode = viewModel::toggleViewMode,
        onGetCategoryPasswordCount = viewModel::getCategoryPasswordCount,
        onToggleFavorite = viewModel::toggleFavorite,
        onCategoryManagementClick = onCategoryManagementClick
    )
}

@Composable
private fun HomePageContent(
    passwordEntries: List<PasswordEntry>,
    categories: List<Category>,
    selectedCategoryId: String,
    selectedItemType: VaultItemType?,
    isGridView: Boolean,
    searchQuery: String,
    onPasswordClick: (PasswordEntry) -> Unit,
    onDeletePassword: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onItemTypeSelected: (VaultItemType?) -> Unit,
    onToggleViewMode: () -> Unit,
    onGetCategoryPasswordCount: (String) -> Int,
    onToggleFavorite: (String) -> Unit,
    onCategoryManagementClick: () -> Unit
) {
    var entryToDelete by remember { mutableStateOf<PasswordEntry?>(null) }

    // 删除确认对话框
    entryToDelete?.let { entry ->
        ConfirmDeleteDialog(
            itemName = entry.title,
            onConfirm = { onDeletePassword(entry.id) },
            onDismiss = { entryToDelete = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 分类选择和视图切换栏
        CategorySelectionBar(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            selectedItemType = selectedItemType,  // 传递类型筛选状态
            isGridView = isGridView,
            onCategorySelected = onCategorySelected,
            onItemTypeSelected = onItemTypeSelected,  // 传递类型筛选方法
            onViewModeToggle = onToggleViewMode,
            onCategoryManagementClick = onCategoryManagementClick
        )

        // 主内容区域
        if (isGridView) {
            CategoryGridView(
                categories = categories,
                onCategoryClick = { categoryId ->
                    onCategorySelected(categoryId)
                    onToggleViewMode() // 切换回列表视图
                },
                getCategoryPasswordCount = onGetCategoryPasswordCount
            )
        } else {
            PasswordListView(
                passwordEntries = passwordEntries,
                searchQuery = searchQuery,
                onPasswordClick = onPasswordClick,
                onPasswordDelete = { entryToDelete = it },
                onFavoriteToggle = onToggleFavorite,
                isFavorited = { entry ->
                    entry.categoryIdsList.contains(AppConstants.CATEGORY_ID_FAVORITES)
                }
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            // 根据类型显示不同的icon
            Icon(
                imageVector = if (entry.type == VaultItemType.NOTE)
                    Icons.Filled.NoteAlt  // 笔记使用Add图标（临时）
                else
                    Icons.Outlined.Key,  // 密码使用钥匙图标
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 笔记类型显示内容片段，密码类型显示用户名
                Text(
                    text = if (entry.type == VaultItemType.NOTE) {
                        if (entry.content.isNotBlank())
                            entry.content.take(50)  // 显示前50个字符
                        else
                            ""
                    } else {
                        entry.username
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.clickable(onClick = onFavoriteToggle)) {
                Icon(
                    imageVector = if (isFavorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorited) stringResource(R.string.remove_from_favorites) else stringResource(R.string.add_to_favorites),
                    modifier = Modifier
                        .width(24.dp)
                        .fillMaxHeight(),
                    tint = if (isFavorited) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.clickable(onClick = onDelete)) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete), modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight(), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * 分类选择和视图切换栏
 */
@Composable
private fun CategorySelectionBar(
    categories: List<Category>,
    selectedCategoryId: String,
    selectedItemType: VaultItemType?,  // 新增：类型筛选状态
    isGridView: Boolean,
    onCategorySelected: (String) -> Unit,
    onItemTypeSelected: (VaultItemType?) -> Unit,  // 新增：类型筛选方法
    onViewModeToggle: () -> Unit,
    onCategoryManagementClick: () -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }
    val selectedCategory = remember(categories, selectedCategoryId) {
        categories.find { it.id == selectedCategoryId }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 筛选器区域（类型 + 分类）
        if (!isGridView) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 类型筛选器
                ItemTypeSelector(
                    selectedItemType = selectedItemType,
                    onItemTypeSelected = onItemTypeSelected
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 分类筛选器
                CategorySelector(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    onCategorySelected = onCategorySelected,
                    onCategoryManagementClick = onCategoryManagementClick
                )
            }
        } else {
            Box {}
        }


        // 视图切换按钮
        IconButton(onClick = onViewModeToggle) {
            Icon(
                imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                contentDescription = if (isGridView) stringResource(R.string.switch_to_list_view) else stringResource(R.string.switch_to_grid_view),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 分类网格视图
 */
@Composable
private fun CategoryGridView(
    categories: List<Category>,
    onCategoryClick: (String) -> Unit,
    getCategoryPasswordCount: (String) -> Int
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
    ) {
        items(categories, key = { it.id }) { category ->
            CategoryGridItem(
                category = category,
                passwordCount = getCategoryPasswordCount(category.id),
                onClick = { onCategoryClick(category.id) }
            )
        }
    }
}

/**
 * 分类网格项
 */
@Composable
private fun CategoryGridItem(
    category: Category,
    passwordCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(category.color.toColorInt()))
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = getCategoryDisplayName(category),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = pluralStringResource(R.plurals.password_count, passwordCount, passwordCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 密码列表视图
 */
@Composable
private fun PasswordListView(
    passwordEntries: List<PasswordEntry>,
    searchQuery: String,
    onPasswordClick: (PasswordEntry) -> Unit,
    onPasswordDelete: (PasswordEntry) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    isFavorited: (PasswordEntry) -> Boolean
) {
    if (passwordEntries.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val text = if (searchQuery.isEmpty()) {
                stringResource(R.string.vault_empty_message)
            } else {
                stringResource(R.string.no_search_results)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            item { Spacer(modifier = Modifier.size(8.dp)) } // Top padding
            items(passwordEntries, key = { it.id }) { entry ->
                PasswordListItem(
                    entry = entry,
                    onClick = { onPasswordClick(entry) },
                    onDelete = {
                        onPasswordDelete(entry)
                    },
                    onFavoriteToggle = {
                        onFavoriteToggle(entry.id)
                    },
                    isFavorited = isFavorited(entry),
                )
            }
            item { Spacer(modifier = Modifier.size(8.dp)) } // Bottom padding
        }
    }
}

/**
 * 类型筛选器组件
 */
@Composable
private fun ItemTypeSelector(
    selectedItemType: VaultItemType?,
    onItemTypeSelected: (VaultItemType?) -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clickable {
                    showDropdown = true
                }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getItemTypeIcon(selectedItemType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = getItemTypeName(selectedItemType),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Icon(
                Icons.Outlined.ArrowDropDown,
                contentDescription = stringResource(R.string.select_type),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
            containerColor = MaterialTheme.colorScheme.background
        ) {
            // 全部类型
            DropdownMenuItem(
                text = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.all_types))
                    }
                },
                onClick = {
                    onItemTypeSelected(null)
                    showDropdown = false
                },
                trailingIcon = {
                    if (selectedItemType == null) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
            
            // 密码
            DropdownMenuItem(
                text = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Password,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.type_password))
                    }
                },
                onClick = {
                    onItemTypeSelected(VaultItemType.PASSWORD)
                    showDropdown = false
                },
                trailingIcon = {
                    if (selectedItemType == VaultItemType.PASSWORD) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
            
            // 安全笔记
            DropdownMenuItem(
                text = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Note,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.type_note))
                    }
                },
                onClick = {
                    onItemTypeSelected(VaultItemType.NOTE)
                    showDropdown = false
                },
                trailingIcon = {
                    if (selectedItemType == VaultItemType.NOTE) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    }
}

/**
 * 分类筛选器组件
 */
@Composable
private fun CategorySelector(
    categories: List<Category>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    onCategoryManagementClick: () -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }
    val selectedCategory = remember(selectedCategoryId) { categories.find { it.id == selectedCategoryId } }

    Box {
        Row(
            modifier = Modifier
                .clickable {
                    showDropdown = true
                }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 分类颜色指示器
            selectedCategory?.let {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(it.color.toColorInt()))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = if (selectedCategory != null) getCategoryDisplayName(selectedCategory) else stringResource(R.string.all_categories),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Icon(
                Icons.Outlined.ArrowDropDown,
                contentDescription = stringResource(R.string.select_category),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        DropdownMenu(
            modifier = Modifier.heightIn(max = 350.dp),
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
            containerColor = MaterialTheme.colorScheme.background
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_categories)) },
                onClick = {
                    onCategoryManagementClick()
                    showDropdown = false
                }
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(category.color.toColorInt()))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(getCategoryDisplayName(category = category))
                        }
                    },
                    onClick = {
                        onCategorySelected(category.id)
                        showDropdown = false
                    }, trailingIcon = {
                        if (selectedCategoryId == category.id) {
                            Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Box {  }
                                }
                            }
                        )
                    }
                }
            }
}

@Composable
private fun getItemTypeName(type: VaultItemType?): String {
    return when (type) {
        null -> stringResource(R.string.all_types)
        VaultItemType.PASSWORD -> stringResource(R.string.type_password)
        VaultItemType.NOTE -> stringResource(R.string.type_note)
        else -> stringResource(R.string.all_types)
    }
}

@Composable
private fun getItemTypeIcon(type: VaultItemType?): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        null -> Icons.Default.Apps
        VaultItemType.PASSWORD -> Icons.Default.Password
        VaultItemType.NOTE -> Icons.AutoMirrored.Filled.Note
        else -> Icons.Default.Apps
    }
}
