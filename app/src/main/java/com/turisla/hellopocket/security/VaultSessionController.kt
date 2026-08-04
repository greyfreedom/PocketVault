package com.turisla.hellopocket.security

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.turisla.hellopocket.data.PasswordRepository
import com.turisla.hellopocket.data.TotpRepository
import com.turisla.hellopocket.utils.ClipboardManagerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 进程级保险库会话控制器。
 *
 * 它不依赖 Activity 是否仍存在，因此退出页面、配置变更或进程在后台冻结后恢复时，
 * 都能遵守统一的自动锁定规则。
 */
class VaultSessionController(
    private val sessionGuard: VaultSessionGuard,
    private val passwordRepository: PasswordRepository,
    private val totpRepository: TotpRepository,
    private val clipboardManagerHelper: ClipboardManagerHelper,
) : DefaultLifecycleObserver {
    companion object {
        private const val LOCK_DELAY_MS = 60_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _lockGeneration = MutableStateFlow(0L)
    val lockGeneration: StateFlow<Long> = _lockGeneration.asStateFlow()

    private var backgroundLockJob: Job? = null
    private var backgroundedAtElapsedRealtime: Long? = null

    override fun onStart(owner: LifecycleOwner) {
        backgroundLockJob?.cancel()
        backgroundLockJob = null

        val backgroundedAt = backgroundedAtElapsedRealtime
        backgroundedAtElapsedRealtime = null
        if (
            backgroundedAt != null &&
            SystemClock.elapsedRealtime() - backgroundedAt >= LOCK_DELAY_MS
        ) {
            // 缓存进程可能冻结协程；恢复前台前用单调时钟补做超时锁定。
            lockVault()
        }
        sessionGuard.enterForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        // 先关闭发布闸门，正在运行的 KDF/解密结果不能在后台进入内存。
        sessionGuard.enterBackground()
        clipboardManagerHelper.clearSensitiveClipboardIfOwned()
        backgroundedAtElapsedRealtime = SystemClock.elapsedRealtime()
        backgroundLockJob?.cancel()
        backgroundLockJob = scope.launch {
            delay(LOCK_DELAY_MS)
            lockVault()
        }
    }

    fun lockVault() {
        // PasswordRepository.lock() 会先使全部解锁令牌失效，再清除密钥和明文。
        passwordRepository.lock()
        totpRepository.lock()
        _lockGeneration.update { it + 1L }
    }

    fun isForeground(): Boolean = sessionGuard.isForeground()
}
