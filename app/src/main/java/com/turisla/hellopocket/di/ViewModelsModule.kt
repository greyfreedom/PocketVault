package com.turisla.hellopocket.di

import com.turisla.hellopocket.MainViewModel
import com.turisla.hellopocket.ui.feature.addPassword.AddPasswordDraftViewModel
import com.turisla.hellopocket.ui.feature.addPaymentCard.AddPaymentCardDraftViewModel
import com.turisla.hellopocket.ui.feature.addSecureNote.AddSecureNoteDraftViewModel
import com.turisla.hellopocket.ui.feature.category.CategoryManagementViewModel
import com.turisla.hellopocket.ui.feature.detail.DetailEditDraftViewModel
import com.turisla.hellopocket.ui.feature.detail.DetailViewModel
import com.turisla.hellopocket.ui.feature.home.HomePageViewModel
import com.turisla.hellopocket.ui.feature.search.SearchViewModel
import com.turisla.hellopocket.ui.feature.passwordGenerator.PasswordGeneratorViewModel
import com.turisla.hellopocket.ui.feature.settings.ChangePasswordViewModel
import com.turisla.hellopocket.ui.feature.settings.HistoryViewModel
import com.turisla.hellopocket.ui.feature.settings.SettingsPageViewModel
import com.turisla.hellopocket.ui.feature.totp.ManualTotpViewModel
import com.turisla.hellopocket.ui.feature.totp.ScannerViewModel
import com.turisla.hellopocket.ui.feature.totp.TotpViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelsModule = module {
    // MainViewModel 有多个依赖，为了清晰，我们使用标准方式定义
    viewModel { MainViewModel(get(), get(), get(), get()) }
    viewModelOf(::HomePageViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::PasswordGeneratorViewModel)
    viewModelOf(::AddPasswordDraftViewModel)
    viewModelOf(::AddPaymentCardDraftViewModel)
    viewModelOf(::AddSecureNoteDraftViewModel)
    viewModel { SettingsPageViewModel(get(), get(), get(), get()) }
    viewModelOf(::ChangePasswordViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::CategoryManagementViewModel)
    viewModelOf(::DetailEditDraftViewModel)
    // DetailViewModel 有多个依赖，我们使用标准方式定义
    viewModel { DetailViewModel(get(), get(), get()) }
    // TOTP 相关 ViewModel
    viewModel { TotpViewModel(get(), get()) }
    viewModelOf(::ScannerViewModel)
    viewModelOf(::ManualTotpViewModel)
}
