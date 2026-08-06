package com.turisla.hellopocket.ui.feature.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import com.turisla.hellopocket.R

/**
 * 导入激活前请求“导入保险库自身”的主密码。
 * 密码只保存在当前 composition 的内存中，不进入 saved state。
 */
@Composable
fun ImportMasterPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onDismissRequest = {
            password = ""
            onDismiss()
        },
        title = { Text(stringResource(R.string.import_data)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.master_password)) },
                supportingText = { Text(stringResource(R.string.import_master_password_prompt)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false
                ),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val submittedPassword = password
                    password = ""
                    onConfirm(submittedPassword)
                },
                enabled = password.isNotEmpty(),
            ) {
                Text(stringResource(R.string.continue_action))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                password = ""
                onDismiss()
            }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
