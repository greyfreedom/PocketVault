package com.turisla.hellopocket.ui.feature.addSecureNote

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.Category
import com.turisla.hellopocket.ui.feature.common.AttachmentSection
import com.turisla.hellopocket.ui.feature.common.CategoryCreationContent
import com.turisla.hellopocket.ui.feature.common.CustomFieldEditSection
import com.turisla.hellopocket.ui.feature.common.EntryCategorySelectionSection
import com.turisla.hellopocket.ui.feature.common.SelectCategoryContent
import com.turisla.hellopocket.ui.feature.common.getCategoryDisplayName
import com.turisla.hellopocket.ui.feature.common.toProtoCustomFields
import com.turisla.hellopocket.ui.feature.home.HomePageViewModel
import com.turisla.hellopocket.utils.AppConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSecureNoteScreen(
    onNavigateBack: () -> Unit,
    onLoading: (Boolean) -> Unit = {},
    viewModel: HomePageViewModel = koinViewModel(),
    draftViewModel: AddSecureNoteDraftViewModel = koinViewModel(),
) {
    val draft by draftViewModel.draft.collectAsStateWithLifecycle()
    val title = draft.title
    val content = draft.content
    val customFields = draft.customFields
    val selectedCategoryIds = draft.selectedCategoryIds
    val selectedAttachmentIds = draft.selectedAttachmentIds
    var error by remember { mutableStateOf<String?>(null) }
    var customFieldValidationAttempted by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val isAttachmentLoading by viewModel.isAttachmentLoading.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val currentOnLoading by rememberUpdatedState(onLoading)
    val isBusy = isSaving || isAttachmentLoading
    val scrollState = rememberScrollState()
    val customFieldSnackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(isSaving, isAttachmentLoading) {
        currentOnLoading(isSaving || isAttachmentLoading)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnLoading(false) }
    }

    BackHandler {
        // 持久化或附件写入期间不能退出，否则页面作用域会取消正在进行的事务。
        if (!isBusy) {
            draftViewModel.clear()
            onNavigateBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(customFieldSnackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.add_secure_note), 
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            draftViewModel.clear()
                            onNavigateBack()
                        },
                        enabled = !isBusy,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
            )
        }, 
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
                .pointerInput(focusManager) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            // 标题区域
            SectionHeader(title = stringResource(R.string.title))
            CardContainer {
                ItemSection(
                    title = stringResource(R.string.title_required), 
                    value = title, 
                    onValueChange = { draftViewModel.updateTitle(it); error = null },
                    isError = error != null && title.isBlank()
                )
            }

            // 内容区域
            SectionHeader(title = stringResource(R.string.content))
            CardContainer {
                ContentSection(
                    value = content, 
                    onValueChange = draftViewModel::updateContent,
                )
            }

            CustomFieldEditSection(
                fields = customFields,
                parentScrollState = scrollState,
                snackbarHostState = customFieldSnackbarHostState,
                showValidationErrors = customFieldValidationAttempted,
                onAdd = { type ->
                    error = null
                    customFieldValidationAttempted = false
                    draftViewModel.addCustomField(type)
                },
                onNameChange = { id, value ->
                    error = null
                    customFieldValidationAttempted = false
                    draftViewModel.updateCustomFieldName(id, value)
                },
                onValueChange = draftViewModel::updateCustomFieldValue,
                onTypeChange = draftViewModel::updateCustomFieldType,
                onRemove = draftViewModel::removeCustomField,
                onRestore = draftViewModel::restoreCustomField,
                onMove = draftViewModel::moveCustomField,
                onCopy = viewModel::copyCustomFieldValue,
            )

            // 分类选择区域
            SectionHeader(title = stringResource(R.string.category))
            CardContainer {
                EntryCategorySelectionSection(
                    categories = categories,
                    selectedCategoryIds = selectedCategoryIds,
                    onCategoryAdd = draftViewModel::addCategory,
                    onCategoryRemove = draftViewModel::removeCategory,
                    onCreateCategory = viewModel::addCategory,
                )
            }

            // 附件区域
            SectionHeader(title = stringResource(R.string.attachments))
            CardContainer {
                AttachmentSection(
                    attachmentIds = selectedAttachmentIds,
                    allAttachments = attachments,
                    onAddAttachment = { uri ->
                        scope.launch {
                            try {
                                val id = viewModel.addAttachment(uri)
                                draftViewModel.addAttachment(id)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: IllegalArgumentException) {
                                // 文件大小超限
                                Toast.makeText(context, R.string.attachment_add_failed, Toast.LENGTH_LONG).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, R.string.attachment_add_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onRemoveAttachment = { id ->
                        draftViewModel.removeAttachment(id)
                    },
                    loadThumbnail = viewModel::loadAttachmentThumbnail
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 错误提示
            if (error != null) {
                Text(
                    text = error!!, 
                    color = MaterialTheme.colorScheme.error, 
                    style = MaterialTheme.typography.bodySmall, 
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp), 
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        draftViewModel.clear()
                        onNavigateBack()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isBusy,
                ) {
                    Text(stringResource(R.string.cancel))
                }

                Button(
                    onClick = {
                        val hasInvalidCustomField = customFields.any { it.name.isBlank() }
                        customFieldValidationAttempted = title.isNotBlank() && hasInvalidCustomField
                        val validationError = when {
                            title.isBlank() -> context.getString(R.string.title_required)
                            hasInvalidCustomField -> {
                                context.getString(R.string.custom_field_name_required)
                            }
                            else -> null
                        }
                        if (validationError != null) {
                            error = validationError
                        } else {
                            scope.launch {
                                isSaving = true
                                try {
                                    viewModel.addSecureNote(
                                        title,
                                        content.text,
                                        selectedCategoryIds.toList(),
                                        selectedAttachmentIds.toList(),
                                        customFields.toProtoCustomFields(),
                                    )
                                    customFieldValidationAttempted = false
                                    draftViewModel.clear()
                                    onNavigateBack()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (e: Exception) {
                                    error = context.getString(R.string.save_failed)
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    }, 
                    modifier = Modifier.weight(1f),
                    enabled = !isBusy,
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun CardContainer(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        content()
    }
}

@Composable
private fun ItemSection(
    title: String, 
    value: String, 
    onValueChange: (String) -> Unit, 
    isError: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = "", 
                        style = MaterialTheme.typography.bodyLarge, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun ContentSection(
    value: TextFieldValue, 
    onValueChange: (TextFieldValue) -> Unit
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.content_optional),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            onTextLayout = { layoutResult ->
                coroutineScope.launch {
                    try {
                        if (value.selection.collapsed) {
                            val rect = layoutResult.getCursorRect(value.selection.end)
                            bringIntoViewRequester.bringIntoView(rect)
                        }
                    } catch (e: Exception) {
                         // Ignore layout errors during scrolling
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 120.dp) // Allow growing
                .bringIntoViewRequester(bringIntoViewRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                if (value.text.isEmpty()) {
                    Text(
                        text = "", 
                        style = MaterialTheme.typography.bodyLarge, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

/**
 * 分类选择区域
 */
@Composable
private fun CategorySelectionSection(
    categories: List<Category>, 
    selectedCategoryIds: Set<String>, 
    onCategoryAdd: (String) -> Unit, 
    onCategoryRemove: (String) -> Unit, 
    viewModel: HomePageViewModel
) {
    var showCategoryDialog by remember { mutableStateOf(false) }
    
    // 检查是否选中了收藏夹
    val isFavoritesSelected = remember(selectedCategoryIds) { 
        selectedCategoryIds.contains(AppConstants.CATEGORY_ID_FAVORITES) 
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.categories), 
                style = MaterialTheme.typography.bodyMedium, 
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 收藏按钮
                IconButton(
                    onClick = { 
                        if (isFavoritesSelected) {
                            onCategoryRemove(AppConstants.CATEGORY_ID_FAVORITES)
                        } else {
                            onCategoryAdd(AppConstants.CATEGORY_ID_FAVORITES)
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (isFavoritesSelected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavoritesSelected) stringResource(R.string.remove_from_favorites) else stringResource(R.string.add_to_favorites),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // 添加分类按钮
                IconButton(
                    onClick = { showCategoryDialog = true },
                ) {
                    Icon(
                        Icons.Default.Add, 
                        contentDescription = stringResource(R.string.add_category), 
                        tint = MaterialTheme.colorScheme.primary, 
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 显示已选分类
        val selectedCategories = remember(categories, selectedCategoryIds) {
            categories.filter { selectedCategoryIds.contains(it.id) }
        }
        if (selectedCategories.isEmpty()) {
            Text(
                text = stringResource(R.string.no_categories_selected), 
                style = MaterialTheme.typography.bodySmall, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp), 
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(selectedCategories, key = { it.id }) { category ->
                    SelectedCategoryChip(
                        category = category, 
                        onRemove = { onCategoryRemove(category.id) }
                    )
                }
            }
        }
    }

    // 分类选择对话框
    if (showCategoryDialog) {
        CategorySelectionDialog(
            categories = remember(categories) { categories.filter { !it.id.startsWith("default_") } },
            selectedCategoryIds = selectedCategoryIds,
            onCategorySelect = { categoryId ->
                onCategoryAdd(categoryId)
            },
            onDismiss = { showCategoryDialog = false },
            viewModel = viewModel,
        )
    }
}

/**
 * 已选分类切片（带删除按钮）
 */
@Composable
private fun SelectedCategoryChip(
    category: Category, 
    onRemove: () -> Unit
) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = {
            Text(
                text = getCategoryDisplayName(category = category),
                style = MaterialTheme.typography.labelMedium,
            )
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(category.color.toColorInt())),
            )
        },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.remove_category),
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
        shape = MaterialTheme.shapes.small,
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = Color(category.color.toColorInt()).copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/**
 * 分类选择对话框
 */
@Composable
private fun CategorySelectionDialog(
    categories: List<Category>,
    selectedCategoryIds: Set<String>,
    onCategorySelect: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: HomePageViewModel,
) {
    var showCreateCategory by remember { mutableStateOf(false) }
    var newlyCreatedCategoryId by remember { mutableStateOf<String?>(null) }
    var isCreatingCategory by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { if (!isCreatingCategory) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), 
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            if (showCreateCategory) {
                CategoryCreationContent(
                    onSave = { name, color ->
                        if (!isCreatingCategory) {
                            isCreatingCategory = true
                            // 分类持久化完成前保持对话框，避免页面作用域取消写入。
                            coroutineScope.launch {
                                try {
                                    val newCategoryId = viewModel.addCategory(name, color)
                                    newlyCreatedCategoryId = newCategoryId
                                    showCreateCategory = false
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        R.string.create_category_failed,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } finally {
                                    isCreatingCategory = false
                                }
                            }
                        }
                    },
                    onCancel = { showCreateCategory = false },
                    isSaving = isCreatingCategory,
                )
            } else {
                SelectCategoryContent(
                    categories = categories,
                    selectedCategoryIds = selectedCategoryIds,
                    onCategorySelect = { categoryId ->
                        onCategorySelect(categoryId)
                    },
                    onCreateNew = { 
                        newlyCreatedCategoryId = null
                        showCreateCategory = true 
                    },
                    onDismiss = onDismiss,
                    multiSelect = true,
                    newlyCreatedCategoryId = newlyCreatedCategoryId
                )
            }
        }
    }
}
