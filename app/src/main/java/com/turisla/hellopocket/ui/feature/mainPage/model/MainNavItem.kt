package com.turisla.hellopocket.ui.feature.mainPage.model

import androidx.compose.ui.graphics.vector.ImageVector

data class MainNavItem(
    val name: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)
