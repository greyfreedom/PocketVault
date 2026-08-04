package com.turisla.hellopocket.ui.feature.mainPage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turisla.hellopocket.data.PasswordRepository
import kotlinx.coroutines.launch

class MainPageViewModel(private val passwordRepository: PasswordRepository) : ViewModel() {

    /**
     * 添加一个新的密码条目
     */
    fun addPassword(title: String, username: String, plainTextPassword: String, notes: String) {
        viewModelScope.launch {
            passwordRepository.addEntry(title, username, plainTextPassword, notes)
        }
    }
}
