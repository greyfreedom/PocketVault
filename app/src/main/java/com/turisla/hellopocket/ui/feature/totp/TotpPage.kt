package com.turisla.hellopocket.ui.feature.totp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.turisla.hellopocket.R
import com.turisla.hellopocket.router.RouteEditTotp
import com.turisla.hellopocket.ui.feature.common.AppEmptyState
import com.turisla.hellopocket.ui.feature.common.ConfirmDeleteDialog
import com.turisla.hellopocket.ui.theme.AppSpacing
import org.koin.androidx.compose.koinViewModel

/**
 * TOTP 页面
 * 显示已保存的 TOTP 条目列表
 */
@Composable
fun TotpPage(
    navController: NavController,
    viewModel: TotpViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val totpEntries by viewModel.totpEntries.collectAsStateWithLifecycle()
    val expandedIds by viewModel.expandedIds.collectAsStateWithLifecycle()

    var entryToDelete by remember { mutableStateOf<com.turisla.hellopocket.model.TotpEntry?>(null) }

    LaunchedEffect(viewModel, context) {
        viewModel.repositoryErrorEvents.collect { error ->
            android.widget.Toast.makeText(
                context,
                context.getString(error.messageResId),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // 删除确认对话框
    entryToDelete?.let { entry ->
        ConfirmDeleteDialog(
            itemName = "${entry.issuer}: ${entry.account}",
            onConfirm = {
                viewModel.deleteTotpEntry(entry.id)
                entryToDelete = null
            },
            onDismiss = { entryToDelete = null }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (totpEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 72.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppEmptyState(
                    icon = Icons.Default.QrCodeScanner,
                    title = stringResource(R.string.totp_empty_message),
                    description = stringResource(R.string.totp_empty_hint),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AppSpacing.md,
                    top = AppSpacing.sm,
                    end = AppSpacing.md,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                items(
                    items = totpEntries,
                    key = { it.id },
                    contentType = { "totp" },
                ) { entry ->
                    val isExpanded = expandedIds.contains(entry.id)

                    TotpListItem(
                        entry = entry,
                        isExpanded = isExpanded,
                        generateCode = { viewModel.generateCode(entry) },
                        onToggleExpand = { viewModel.toggleExpanded(entry.id) },
                        onCopy = { code -> viewModel.copyCode(code) },
                        onDelete = { entryToDelete = entry },
                        onEdit = { navController.navigate(RouteEditTotp(entry.id)) },
                    )
                }
            }
        }
    }
}
