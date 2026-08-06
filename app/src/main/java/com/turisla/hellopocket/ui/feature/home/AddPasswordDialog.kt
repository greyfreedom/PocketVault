package com.turisla.hellopocket.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.turisla.hellopocket.R

@Composable
fun AddPasswordDialog(
    onDismissRequest: () -> Unit,
    onSave: (title: String, username: String, password: String, notes: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.add_new_password), style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = title, onValueChange = {
                        title = it
                        error = null // Clear error on change
                    }, label = { Text(stringResource(R.string.title_required)) }, modifier = Modifier.fillMaxWidth(), isError = error != null && title.isBlank(), shape = MaterialTheme.shapes.medium
                    )
                    OutlinedTextField(
                        value = username, onValueChange = {
                        username = it
                        error = null
                    }, label = { Text(stringResource(R.string.username_optional)) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
                    )
                    OutlinedTextField(
                        value = password, onValueChange = {
                        password = it
                        error = null
                    }, label = { Text(stringResource(R.string.password_required)) }, modifier = Modifier.fillMaxWidth(), isError = error != null && password.isBlank(), shape = MaterialTheme.shapes.medium
                    )
                    OutlinedTextField(
                        value = notes, onValueChange = {
                        notes = it
                        error = null
                    }, label = { Text(stringResource(R.string.notes_optional)) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
                    )
                }

                // Error message area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp) // Reserve space for one line of text
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = error ?: "", // Always present, but empty when no error
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterStart)
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        if (title.isBlank() || password.isBlank()) {
                            error = context.getString(R.string.title_and_password_required)
                        } else {
                            onSave(title, username, password, notes)
                        }
                    }) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}
