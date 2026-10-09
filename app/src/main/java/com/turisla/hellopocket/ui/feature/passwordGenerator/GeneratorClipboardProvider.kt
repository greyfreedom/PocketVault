package com.turisla.hellopocket.ui.feature.passwordGenerator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard

/** 系统选择菜单与键盘快捷键也走敏感剪贴板；读取仍交给系统以保留粘贴能力。 */
@Composable
internal fun GeneratorClipboardProvider(onCopy: (String) -> Unit, content: @Composable () -> Unit) {
    val platformClipboard = LocalClipboard.current
    val copy by rememberUpdatedState(onCopy)
    val protectedClipboard = remember(platformClipboard) {
        object : Clipboard by platformClipboard {
            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                if (clipEntry == null) {
                    platformClipboard.setClipEntry(null)
                } else if (clipEntry.clipData.itemCount > 0) {
                    clipEntry.clipData.getItemAt(0).text?.toString()?.let(copy)
                }
            }
        }
    }
    CompositionLocalProvider(LocalClipboard provides protectedClipboard, content = content)
}
