package com.turisla.hellopocket.ui.feature.home

import androidx.lifecycle.viewModelScope
import com.turisla.hellopocket.data.PasswordRepository
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.Category
import com.turisla.hellopocket.model.CustomField
import com.turisla.hellopocket.model.PasswordEntry
import com.turisla.hellopocket.model.VaultItemType
import com.turisla.hellopocket.ui.feature.common.viewmodel.RepositoryMutationViewModel
import com.turisla.hellopocket.utils.AppConstants
import com.turisla.hellopocket.utils.ClipboardManagerHelper
import com.turisla.hellopocket.utils.loggerI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 首页的 ViewModel
 * @param passwordRepository 数据仓库，用于获取密码列表
 */
class HomePageViewModel(
    private val passwordRepository: PasswordRepository,
    private val clipboardManagerHelper: ClipboardManagerHelper,
) : RepositoryMutationViewModel() {

    // 分类相关的状态
    private val _selectedCategoryId = MutableStateFlow(AppConstants.CATEGORY_ID_ALL)
    val selectedCategoryId = _selectedCategoryId.asStateFlow()

    // 类型筛选状态，null 表示显示所有类型
    private val _selectedItemType = MutableStateFlow<VaultItemType?>(null)
    val selectedItemType = _selectedItemType.asStateFlow()

    // 并发附件任务共享同一个可重放状态，避免 SharedFlow 丢失开始/结束事件。
    private val attachmentLoadingLock = Any()
    private var activeAttachmentOperations = 0
    private val _isAttachmentLoading = MutableStateFlow(false)
    val isAttachmentLoading = _isAttachmentLoading.asStateFlow()

    // 从数据仓库直接暴露密码列表的状态流
    private val passwordEntries: StateFlow<List<PasswordEntry>> = passwordRepository.passwordEntries

    // 分类列表
    val categories: StateFlow<List<Category>> = passwordRepository.categories
    
    // 监听分类列表变化，如果当前选中的分类不存在了，自动切换到全部分类
    init {
        viewModelScope.launch {
            categories.collect { categoryList ->
                val currentSelectedId = _selectedCategoryId.value
                // 如果当前选中的不是默认分类，且在新的分类列表中找不到，则切换到全部分类
                if (currentSelectedId != AppConstants.CATEGORY_ID_ALL &&
                    currentSelectedId != AppConstants.CATEGORY_ID_UNCATEGORIZED &&
                    categoryList.none { it.id == currentSelectedId }) {
                    _selectedCategoryId.value = AppConstants.CATEGORY_ID_ALL
                }
            }
        }
    }

    // 根据类型和选中分类过滤后的密码列表（包含密码和笔记）。
    // 搜索已拆分到独立页面，主页状态不再与搜索状态互相影响。
    val filteredPasswordEntries: StateFlow<List<PasswordEntry>> = combine(
        passwordEntries,
        selectedCategoryId,
        selectedItemType
    ) { entries, categoryId, itemType ->
        // 1. 按类型筛选
        val typeFilteredEntries = itemType?.let { type ->
            entries.filter { it.type == type }
        } ?: entries
        
        // 2. 按分类筛选
        val categoryFilteredEntries = when (categoryId) {
            AppConstants.CATEGORY_ID_ALL -> typeFilteredEntries
            AppConstants.CATEGORY_ID_UNCATEGORIZED -> typeFilteredEntries.filter { it.categoryIdsList.isEmpty() }
            else -> typeFilteredEntries.filter { it.categoryIdsList.contains(categoryId) }
        }
        
        categoryFilteredEntries
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 切换选中的分类
     */
    fun onCategorySelected(categoryId: String) {
        _selectedCategoryId.value = categoryId
    }

    /**
     * 切换选中的类型
     * @param itemType 要筛选的类型，null 表示显示所有类型
     */
    fun onItemTypeSelected(itemType: VaultItemType?) {
        _selectedItemType.value = itemType
    }

    // 附件列表
    val attachments = passwordRepository.attachments

    /**
     * 添加一个新的密码条目
     */
    suspend fun addPassword(
        title: String,
        username: String,
        plainTextPassword: String,
        notes: String,
        categoryIds: List<String> = emptyList(),
        attachmentIds: List<String> = emptyList(),
        customFields: List<CustomField> = emptyList(),
    ) {
        passwordRepository.addEntry(
            title,
            username,
            plainTextPassword,
            notes,
            categoryIds,
            attachmentIds,
            customFields,
        )
    }

    /**
     * 删除指定ID的密码条目
     */
    fun deletePassword(id: String) {
        launchRepositoryMutation {
            passwordRepository.deleteEntry(id)
        }
    }

    /**
     * 添加新分类
     * @return 新创建的分类ID
     */
    suspend fun addCategory(name: String, color: String): String {
        return passwordRepository.addCategory(name, color)
    }

    /**
     * 切换密码的收藏状态
     */
    fun toggleFavorite(passwordId: String) {
        launchRepositoryMutation {
            passwordRepository.togglePasswordFavorite(passwordId)
        }
    }

    /**
     * 检查密码是否已收藏
     */
    fun isPasswordFavorited(passwordEntry: PasswordEntry): Boolean {
        return passwordEntry.categoryIdsList.contains(AppConstants.CATEGORY_ID_FAVORITES)
    }

    suspend fun addAttachment(uri: android.net.Uri): String {
        beginAttachmentOperation()
        return try {
            passwordRepository.addAttachment(uri)
        } finally {
            endAttachmentOperation()
        }
    }

    fun deleteAttachment(attachmentId: String) {
        launchRepositoryMutation {
            passwordRepository.deleteAttachment(attachmentId)
        }
    }

    /**
     * 添加安全笔记
     */
    suspend fun addSecureNote(
        title: String,
        content: String,
        categoryIds: List<String> = emptyList(),
        attachmentIds: List<String> = emptyList(),
        customFields: List<CustomField> = emptyList(),
    ) {
        passwordRepository.addSecureNote(title, content, categoryIds, attachmentIds, customFields)
    }

    fun copyCustomFieldValue(value: String) {
        clipboardManagerHelper.copyTextToClipboard(R.string.custom_field, value)
    }

    suspend fun loadAttachmentThumbnail(attachmentId: String): Any? {
        return passwordRepository.getThumbnailFile(attachmentId)
    }

    private fun beginAttachmentOperation() {
        synchronized(attachmentLoadingLock) {
            activeAttachmentOperations++
            _isAttachmentLoading.value = true
        }
    }

    private fun endAttachmentOperation() {
        synchronized(attachmentLoadingLock) {
            activeAttachmentOperations = (activeAttachmentOperations - 1).coerceAtLeast(0)
            _isAttachmentLoading.value = activeAttachmentOperations > 0
        }
    }

    override fun onCleared() {
        synchronized(attachmentLoadingLock) {
            activeAttachmentOperations = 0
            _isAttachmentLoading.value = false
        }
        // viewModelScope 已取消，明文缓存必须同步清理；密文孤儿会在下次解锁后统一回收。
        passwordRepository.clearAttachmentCache()
        super.onCleared()
    }
}
