package com.turisla.hellopocket.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.annotation.StringRes
import com.turisla.hellopocket.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 一个辅助类，用于管理剪贴板操作，特别是支持定时清除。
 */
class ClipboardManagerHelper(private val context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var clearJob: Job? = null
    private var ownsSensitiveClip = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 复制文本到剪贴板，并在指定延迟后清除。
     * @param labelRes 用于剪贴板UI和提示的本地化标签。
     * @param text 要复制的实际文本。
     * @param clearAfterMillis 延迟清除的时间（毫秒），默认为60秒。
     */
    fun copyTextToClipboard(
        @StringRes labelRes: Int,
        text: String,
        clearAfterMillis: Long = AppConstants.CLIPBOARD_CLEAR_DELAY_MS
    ) {
        // 如果已有清除任务，先取消它
        clearJob?.cancel()

        // 复制新内容
        val label = context.getString(labelRes)
        val clip = ClipData.newPlainText(label, text)
        val sensitiveKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            "android.content.extra.IS_SENSITIVE"
        }
        clip.description.extras = PersistableBundle().apply {
            putBoolean(sensitiveKey, true)
        }
        clipboard.setPrimaryClip(clip)
        ownsSensitiveClip = true
        Toast.makeText(context, context.getString(R.string.copied_to_clipboard, label), Toast.LENGTH_SHORT).show()

        // 启动新的协程任务以在延迟后清除剪贴板
        clearJob = scope.launch {
            delay(clearAfterMillis)

            // 在清除前，检查剪贴板内容是否仍是我们设置的那个，以避免误清用户自己复制的内容
            if (clipboard.hasPrimaryClip() && clipboard.primaryClip?.getItemAt(0)?.text == text) {
                clearPrimaryClip()
                Toast.makeText(context, context.getString(R.string.cleared_from_clipboard, label), Toast.LENGTH_SHORT).show()
            }
            ownsSensitiveClip = false
            clearJob = null
        }
    }

    /**
     * 应用退到后台时立即清除由本工具写入的敏感剪贴板。
     * 此路径不读取后台剪贴板，因此不会受 Android 后台读取限制影响。
     */
    fun clearSensitiveClipboardIfOwned() {
        clearJob?.cancel()
        clearJob = null
        if (!ownsSensitiveClip) return
        clearPrimaryClip()
        ownsSensitiveClip = false
    }

    private fun clearPrimaryClip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText(null, ""))
        }
    }
}
