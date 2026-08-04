package com.turisla.hellopocket.ui.feature.common.viewmodel

import androidx.lifecycle.ViewModel
import com.turisla.hellopocket.utils.loggerI

abstract class BaseViewModel : ViewModel() {
    init {
        loggerI("${this::class.simpleName} init...")
    }

    override fun onCleared() {
        super.onCleared()
        loggerI("${this::class.simpleName} onCleared...")
    }
}