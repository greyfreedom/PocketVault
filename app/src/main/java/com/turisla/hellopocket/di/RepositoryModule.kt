package com.turisla.hellopocket.di

import com.turisla.hellopocket.data.PasswordRepository
import com.turisla.hellopocket.data.TotpRepository
import com.turisla.hellopocket.data.UserPreferencesRepository
import com.turisla.hellopocket.security.BiometricCipherManager
import com.turisla.hellopocket.security.CryptoManager
import com.turisla.hellopocket.security.TinkCryptoManager
import com.turisla.hellopocket.security.VaultSessionController
import com.turisla.hellopocket.security.VaultSessionGuard
import com.turisla.hellopocket.utils.ClipboardManagerHelper
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin 模块，负责数据层和安全层的依赖注入
 */
val repositoryModule = module {
    // 将 CryptoManager 声明为单例（用于向后兼容）
    single { CryptoManager() }

    // 将 TinkCryptoManager 声明为单例
    single { TinkCryptoManager(androidContext()) }

    // 将 BiometricCipherManager 声明为单例
    single { BiometricCipherManager() }

    // 解锁结果发布闸门必须是进程级单例，供仓库与生命周期控制器共同使用。
    single { VaultSessionGuard() }

    // 将 PasswordRepository 声明为单例
    single { PasswordRepository(androidContext(), get(), get(), get()) }

    // 将 ClipboardManagerHelper 声明为单例
    single { ClipboardManagerHelper(androidContext()) }

    // 用户偏好设置仓库
    single { UserPreferencesRepository(androidContext()) }

    // TOTP 仓库
    single { TotpRepository(androidContext(), get(), get(), get()) }

    // 自动锁定不绑定 Activity，避免 Activity 销毁后计时任务一并消失。
    single { VaultSessionController(get(), get(), get(), get()) }
}
