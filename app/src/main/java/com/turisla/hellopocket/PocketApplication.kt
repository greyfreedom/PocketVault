package com.turisla.hellopocket

import android.app.Application
import com.turisla.hellopocket.data.UserPreferencesRepository
import com.turisla.hellopocket.di.koinModules
import com.turisla.hellopocket.security.VaultSessionController
import androidx.lifecycle.ProcessLifecycleOwner
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin

class PocketApplication : Application() {

    private val userPreferencesRepository: UserPreferencesRepository by inject()
    private val vaultSessionController: VaultSessionController by inject()
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@PocketApplication)
            modules(koinModules)
        }

        // 在 Koin 启动后，立即应用保存的语言设置
        userPreferencesRepository.applyLanguage()

        // 使用进程生命周期统一管理前后台会话；不再依赖某个 Activity 是否存活。
        ProcessLifecycleOwner.get().lifecycle.addObserver(vaultSessionController)
    }
}
