package com.turisla.hellopocket.ui.feature.totp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.TotpEntry
import com.turisla.hellopocket.ui.feature.common.AppIconTile
import com.turisla.hellopocket.ui.theme.AppSpacing
import kotlinx.coroutines.delay
import kotlin.math.max

private const val CODE_EXIT_ANIMATION_MILLIS = 300L

/** TOTP 条目使用平面容器；动态码只在展开时刷新，避免整页高频重组。 */
@Composable
fun TotpListItem(
    entry: TotpEntry,
    isExpanded: Boolean,
    generateCode: () -> TotpCode,
    onToggleExpand: () -> Unit,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var totpCode by remember(entry.id) { mutableStateOf<TotpCode?>(null) }

    LaunchedEffect(isExpanded, entry.id) {
        if (!isExpanded) {
            // 等收起动画结束后再清理验证码，避免内容先消失导致动画被截断。
            delay(CODE_EXIT_ANIMATION_MILLIS)
            totpCode = null
            return@LaunchedEffect
        }
        while (true) {
            totpCode = generateCode()
            delay(100)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(AppSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconTile(
                    containerColor = Color.Transparent,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                    ),
                ) {
                    Text(
                        text = entry.issuer.firstOrNull()?.uppercase() ?: "T",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.width(AppSpacing.sm))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.issuer,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.account,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = stringResource(R.string.totp_toggle_visibility),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.totp_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.totp_edit_entry)) },
                            leadingIcon = {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.totp_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            AnimatedVisibility(
                // 可见性只由眼睛按钮控制。重新进入已展开的 Tab 时，验证码刷新不会再次触发展开动画。
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                val currentCode = totpCode
                if (currentCode != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppSpacing.sm),
                        shape = MaterialTheme.shapes.medium,
                        color = Color.Transparent,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = AppSpacing.md,
                                end = AppSpacing.xxs,
                                top = AppSpacing.xs,
                                bottom = AppSpacing.xs,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = currentCode.code,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 2.sp,
                                maxLines = 1,
                            )
                            CountdownProgressWithText(
                                remainingSeconds = currentCode.remainingSeconds,
                                remainingMillis = currentCode.remainingMillis,
                                period = currentCode.period,
                            )
                            IconButton(onClick = { onCopy(currentCode.code) }) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = stringResource(R.string.totp_copy_code),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CountdownProgressWithText(
    remainingSeconds: Int,
    remainingMillis: Int,
    period: Int,
) {
    val progress = max(0f, remainingMillis.toFloat() / (period * 1000))
    val progressColor = getProgressColor(progress)
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier.size(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.5.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val centerX = size.width / 2
            val centerY = size.height / 2

            drawCircle(
                color = trackColor,
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(width = strokeWidth),
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth),
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(centerX - radius, centerY - radius),
            )
        }

        Text(
            text = "$remainingSeconds",
            style = MaterialTheme.typography.labelSmall,
            color = progressColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun getProgressColor(progress: Float): Color = when {
    progress > 0.5f -> MaterialTheme.colorScheme.primary
    progress > 0.2f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}
