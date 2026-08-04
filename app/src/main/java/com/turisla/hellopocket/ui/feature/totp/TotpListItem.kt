package com.turisla.hellopocket.ui.feature.totp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.TotpEntry
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * TOTP 列表项组件
 */
@Composable
fun TotpListItem(
    entry: TotpEntry,
    isExpanded: Boolean,
    generateCode: () -> TotpCode,
    onToggleExpand: () -> Unit,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var totpCode by remember(entry.id) { mutableStateOf<TotpCode?>(null) }

    // 高频时间状态只存在于展开的单个 item，避免让整个 TOTP 页面每 100ms 重组。
    LaunchedEffect(isExpanded, entry.id) {
        if (!isExpanded) {
            totpCode = null
            return@LaunchedEffect
        }
        while (true) {
            totpCode = generateCode()
            delay(100)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 第一行：图标 + 标题/账号 + 操作按钮（固定不跳动）
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧图标
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.issuer.firstOrNull()?.toString() ?: "T",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 发行者和账号
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = entry.issuer,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = entry.account,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 操作按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 显示/隐藏按钮
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) {
                                Icons.Outlined.Visibility
                            } else {
                                Icons.Outlined.VisibilityOff
                            },
                            contentDescription = stringResource(R.string.totp_toggle_visibility),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 复制按钮
                    IconButton(
                        onClick = { totpCode?.code?.let(onCopy) },
                        modifier = Modifier.size(40.dp),
                        enabled = totpCode != null,
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.totp_copy_code),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 更多菜单按钮
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.totp_more),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 下拉菜单
                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp)
                            )
                        ) {
                            // 编辑完整 TOTP 条目
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.totp_edit_entry))
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                }
                            )
                            // 删除菜单项
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.totp_delete))
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            // 第二行：动态码和倒计时（展开时显示）
            val currentCode = totpCode
            if (isExpanded && currentCode != null) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 动态码
                    Text(
                        text = currentCode.code,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 32.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp,
                        maxLines = 1
                    )

                    // 圆形倒计时进度条（中间显示剩余秒数）
                    CountdownProgressWithText(
                        remainingSeconds = currentCode.remainingSeconds,
                        remainingMillis = currentCode.remainingMillis,
                        period = currentCode.period
                    )
                }
            }
        }
    }

}

/**
 * 圆形倒计时进度条（中间显示剩余秒数）
 */
@Composable
fun CountdownProgressWithText(
    remainingSeconds: Int,
    remainingMillis: Int,
    period: Int
) {
    val progress = max(0f, remainingMillis.toFloat() / (period * 1000))
    val progressColor = getProgressColor(progress)
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier.size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val strokeWidth = 2.5.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val centerX = size.width / 2
            val centerY = size.height / 2

            // 背景圆环
            drawCircle(
                color = surfaceVariantColor,
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(width = strokeWidth)
            )

            // 进度圆环（顺时针，从12点方向开始）
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth),
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(centerX - radius, centerY - radius)
            )
        }

        // 中间显示剩余秒数
        Text(
            text = "$remainingSeconds",
            style = MaterialTheme.typography.labelSmall,
            color = progressColor,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 根据进度获取颜色
 */
@Composable
private fun getProgressColor(progress: Float): Color {
    return when {
        progress > 0.5f -> MaterialTheme.colorScheme.primary
        progress > 0.2f -> Color(0xFFFFA726)
        else -> Color(0xFFEF5350)
    }
}
