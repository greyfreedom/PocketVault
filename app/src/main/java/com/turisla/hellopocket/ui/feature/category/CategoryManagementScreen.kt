package com.turisla.hellopocket.ui.feature.category

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.Category
import com.turisla.hellopocket.ui.feature.common.CategoryEditDialog
import com.turisla.hellopocket.ui.feature.common.ConfirmDeleteDialog
import org.koin.androidx.compose.koinViewModel
import androidx.core.graphics.toColorInt
import com.turisla.hellopocket.ui.feature.common.getCategoryDisplayName
import com.turisla.hellopocket.ui.feature.common.AppIconTile
import com.turisla.hellopocket.ui.feature.common.LoadingOverlay

/**
 * 分类管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoryManagementViewModel = koinViewModel()
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    // 分类写入依赖当前页面 ViewModel，提交期间禁止返回和重复操作。
    BackHandler(enabled = isMutating) {}

    // 监听事件
    LaunchedEffect(event) {
        event?.let { currentEvent ->
            viewModel.consumeEvent(currentEvent)
            when (currentEvent) {
                is CategoryManagementViewModel.Event.NavigateBack -> onNavigateBack()
                is CategoryManagementViewModel.Event.ShowMessage -> {
                    Toast.makeText(context, currentEvent.messageRes, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 删除确认对话框
    categoryToDelete?.let { category ->
        ConfirmDeleteDialog(
            itemName = getCategoryDisplayName(category = category),
            onConfirm = {
                viewModel.deleteCategory(category.id)
                categoryToDelete = null
            },
            onDismiss = { categoryToDelete = null }
        )
    }

    // 添加分类对话框
    if (showAddDialog) {
        CategoryEditDialog(
            title = stringResource(R.string.add_category),
            category = null,
            onSave = { name, color ->
                viewModel.addCategory(name, color)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // 编辑分类对话框
    if (showEditDialog && editingCategory != null) {
        CategoryEditDialog(
            title = stringResource(R.string.edit_category),
            category = editingCategory,
            onSave = { name, color ->
                editingCategory?.let { category ->
                    val updatedCategory = category.toBuilder()
                        .setName(name)
                        .setColor(color)
                        .build()
                    viewModel.updateCategory(updatedCategory)
                }
                showEditDialog = false
                editingCategory = null
            },
            onDismiss = {
                showEditDialog = false
                editingCategory = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.category_management),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !isMutating) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }, actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        enabled = !isMutating,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_category))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(categories, key = { it.id }) { category ->
                    CategoryItem(
                        category = category,
                        passwordCount = viewModel.getCategoryPasswordCount(category.id),
                        isDefaultCategory = viewModel.isDefaultCategory(category.id),
                        enabled = !isMutating,
                        onEdit = {
                            editingCategory = category
                            showEditDialog = true
                        },
                        onDelete = {
                            categoryToDelete = category
                        },
                    )
                }
            }
            if (isMutating) {
                LoadingOverlay()
            }
        }
    }
}

/**
 * 分类项组件
 */
@Composable
private fun CategoryItem(
    category: Category,
    passwordCount: Int,
    isDefaultCategory: Boolean,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showActions by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 分类颜色指示器
            AppIconTile(
                containerColor = Color(category.color.toColorInt()).copy(alpha = 0.18f),
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(category.color.toColorInt())),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getCategoryDisplayName(category),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = pluralStringResource(R.plurals.password_count, passwordCount, passwordCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 操作按钮
            if (!isDefaultCategory) {
                Box {
                    IconButton(
                        onClick = { showActions = true },
                        enabled = enabled,
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.totp_more),
                        )
                    }
                    DropdownMenu(
                        expanded = showActions,
                        onDismissRequest = { showActions = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_category)) },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            },
                            onClick = {
                                showActions = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
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
}
