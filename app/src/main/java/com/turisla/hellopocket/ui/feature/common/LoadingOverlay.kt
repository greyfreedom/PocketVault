package com.turisla.hellopocket.ui.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 一个通用的加载中遮罩层
 * 会阻止用户手势穿透，防止在加载时误触其他操作
 * @param modifier
 */
@Composable
fun LoadingOverlay(modifier: Modifier = Modifier, showBackground: Boolean = true) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (showBackground) 0.32f else 0f))
            .clickable(
                indication = null, // 移除点击波纹效果
                interactionSource = remember { MutableInteractionSource() } // 消费手势事件
            ) {
                // 空的点击处理，用于拦截手势，防止穿透到下层UI
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }
    }
}
