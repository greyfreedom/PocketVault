package com.turisla.hellopocket.router

import kotlinx.serialization.Serializable

@Serializable
data object RouteSetup

@Serializable
data object RouteUnlock

@Serializable
data object RouteMainPage

@Serializable
data class RouteDetail(val id: String)

@Serializable
data object RouteChangePassword

@Serializable
data object RouteHistory

@Serializable
data object RouteAddPassword

@Serializable
data object RoutePasswordGenerator

@Serializable
data object RouteCategoryManagement

@Serializable
data object RouteAbout

@Serializable
data object RoutePrivacyPolicy

@Serializable
data object RouteOpenSourceLicenses

@Serializable
data object RouteAddSecureNote  // 添加安全笔记路由

@Serializable
data object RouteTotpScanner    // TOTP 二维码扫描页面

@Serializable
data object RouteAddTotpManual  // 手动添加 TOTP 页面

@Serializable
data class RouteEditTotp(val id: String)  // 编辑完整 TOTP 条目

// 设置子页面路由
@Serializable
data object RouteAppearanceSettings

@Serializable
data object RouteSecuritySettings

@Serializable
data object RouteDataSettings

@Serializable
data object RouteOtherSettings

@Serializable
data object RouteQA
