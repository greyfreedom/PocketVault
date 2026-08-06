package com.turisla.hellopocket.ui.feature.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.turisla.hellopocket.R
import com.turisla.hellopocket.data.PasswordRepository
import com.turisla.hellopocket.model.Category
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 分类管理页面的 ViewModel
 */
class CategoryManagementViewModel(private val passwordRepository: PasswordRepository) : ViewModel() {

    // 分类列表
    val categories: StateFlow<List<Category>> = passwordRepository.categories

    // 事件流
    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    fun consumeEvent(event: Event) {
        _event.compareAndSet(event, null)
    }

    /**
     * 界面事件
     */
    sealed class Event {
        object NavigateBack : Event()
        data class ShowMessage(@param:StringRes val messageRes: Int) : Event()
    }

    /**
     * 添加新分类
     */
    fun addCategory(name: String, color: String) {
        launchMutation(
            successMessage = R.string.category_created_success,
            failureMessage = R.string.create_category_failed,
        ) {
            passwordRepository.addCategory(name, color)
        }
    }

    /**
     * 更新分类
     */
    fun updateCategory(category: Category) {
        launchMutation(
            successMessage = R.string.category_updated_success,
            failureMessage = R.string.update_category_failed,
        ) {
            passwordRepository.updateCategory(category)
        }
    }

    /**
     * 删除分类
     */
    fun deleteCategory(categoryId: String) {
        launchMutation(
            successMessage = R.string.category_deleted_success,
            failureMessage = R.string.delete_category_failed,
        ) {
            passwordRepository.deleteCategory(categoryId)
        }
    }

    private fun launchMutation(
        @StringRes successMessage: Int,
        @StringRes failureMessage: Int,
        block: suspend () -> Unit,
    ) {
        if (!_isMutating.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                block()
                _event.value = Event.ShowMessage(successMessage)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _event.value = Event.ShowMessage(failureMessage)
            } finally {
                _isMutating.value = false
            }
        }
    }

    /**
     * 获取分类下的密码数量
     */
    fun getCategoryPasswordCount(categoryId: String): Int {
        return passwordRepository.getCategoryPasswordCount(categoryId)
    }

    /**
     * 检查分类是否为默认分类（不能删除）
     */
    fun isDefaultCategory(categoryId: String): Boolean {
        return categoryId.startsWith("default_")
    }
}
