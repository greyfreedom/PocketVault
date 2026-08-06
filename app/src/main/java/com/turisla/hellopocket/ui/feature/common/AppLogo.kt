package com.turisla.hellopocket.ui.feature.common

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.turisla.hellopocket.R
import com.turisla.hellopocket.ui.theme.HelloPocketTheme

@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 80.dp) {
    // 应用内品牌标识直接复用现有图标，启动图标及其资源保持不变。
    Image(
        painter = painterResource(R.drawable.app_icon),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f)),
        contentScale = ContentScale.Fit,
    )
}

@Preview(showBackground = true)
@Composable
private fun AppLogoPreview() {
    HelloPocketTheme {
        AppLogo()
    }
}
