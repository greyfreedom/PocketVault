package com.turisla.hellopocket.ui.feature.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.turisla.hellopocket.R

/**
 * 一个通用的、可复用的删除确认对话框
 * @param itemName 要删除的条目的名称，用于显示在提示信息中。如果为null，则显示通用信息。
 * @param onConfirm 用户点击“确认”按钮时执行的回调
 * @param onDismiss 用户请求关闭对话框时执行的回调
 */
@Composable
fun ConfirmDeleteDialog(
    itemName: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val text = if (itemName != null) {
        stringResource(R.string.confirm_delete_target_message, itemName)
    } else {
        stringResource(R.string.confirm_delete_message)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.confirm_delete), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = { Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss() // 执行操作后也关闭对话框
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        })
}
