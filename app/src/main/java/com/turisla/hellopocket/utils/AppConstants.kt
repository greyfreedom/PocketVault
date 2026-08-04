package com.turisla.hellopocket.utils

/**
 * @author Turisla
 * @date 2024/9/18 21:46
 * @description
 */
object AppConstants {
    const val CATEGORY_ID_ALL = "default_all"
    const val CATEGORY_ID_UNCATEGORIZED = "default_uncategorized"
    const val CATEGORY_ID_FAVORITES = "default_favorites"
    const val CONTACT_EMAIL = "richonenight@gmail.com"

    const val FLAVOR_GOOGLE_PLAY = "googlePlay"
    const val FLAVOR_APK_PURE = "apkpure"
    const val FLAVOR_HUAWEI = "huawei"

    // 剪贴板自动清除延迟（毫秒）
    const val CLIPBOARD_CLEAR_DELAY_MS = 60000L

    // 最大附件大小限制（50MB）
    const val MAX_ATTACHMENT_SIZE_BYTES = 50L * 1024 * 1024 // 50MB

    // 主密码用于抵抗离线破解，不能沿用普通 PIN 的最低长度
    const val MIN_MASTER_PASSWORD_LENGTH = 12
}
