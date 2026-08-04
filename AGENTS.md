# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

HelloPocket is an Android password manager built with Kotlin and Jetpack Compose. It uses Google Tink for encryption (AES-256-GCM-HKDF-1MB) with PBKDF2 key derivation (600,000 iterations for new vaults; 100,000 retained only for legacy compatibility). The app follows an offline-first architecture with MVVM pattern and Koin for dependency injection.

## 协作备忘录 (重要)

### 你需要注意的协作历史和重要结论

1.  **核心架构 - 数据可移植性**:
    *   项目**不使用数据库**，而是采用一种名为 **“保险库目录” (Vault Bundle)** 的存储模型。这是一个结构化的目录 (`hellopocket_vault`)，其中包含：一个核心的`manifest.json`清单文件、多个独立加密的数据文件（如`passwords.dat`, `categories.dat`）以及一个用于存放附件的子目录。

2.  **安全模型**:
    *   **主密码是唯一信源**: 主密码和 `vault_v2.json` 中的随机盐通过 PBKDF2-HMAC-SHA256（新保险库 600,000 次；旧保险库最低兼容 100,000 次）派生密钥加密密钥，用于包装随机生成的 `StreamingAead Keyset`；项目只持久化加密后的 Keyset，绝不存储主密码、派生密钥或明文 Keyset。
    *   **Google Tink 加密**: 采用 Google Tink 库的 `StreamingAead` (AES-256-GCM-HKDF-1MB) 流式认证加密方案。
    *   **流式加密优势**: 使用流式加密避免将整个附件同时加载到内存；可处理的实际大小仍受设备存储、Android 平台和应用安全限制影响。
    *   **数据完整性保护**: 内置 SHA-256 完整性校验，确保保险库数据未被篡改。同时 GCM 模式本身也提供认证加密 (AEAD)。
    *   **生物识别是便利性解锁**: 指纹解锁功能不是主密码的替代品，而是一种"快捷方式"。它的实现原理是：在用户用主密码成功登录一次后，将 `StreamingAead Keyset` 用一个需要指纹授权的硬件密钥（`biometricKey`）加密后存储在 Android Keystore。后续解锁时，通过指纹"解包装"出 `Keyset` 来使用。
    *   **修改主密码优化**: 修改主密码时，只需要重新加密 `Keyset`，而不需要重新加密所有数据文件，大幅提升了性能和用户体验。

3.  **关键功能实现决策**:
    *   **搜索**: 采用**主页内嵌式搜索**方案，点击搜索图标后，在 `TopAppBar` 原地展开搜索框，实时过滤列表，而不是跳转到新页面。
    *   **自动锁定**: 应用进入后台**1分钟**后自动锁定，返回前台时需重新认证。
    *   **剪贴板管理**: 复制的密码在**60秒**后，会自动从系统剪贴板中清除。
    *   **防截屏**: 应用主窗口统一添加 `FLAG_SECURE` 标志，以禁止常规截屏、录屏和最近任务缩略图。
    *   **启动时导入**: 在首次设置主密码的页面，提供“从文件导入”的入口，方便用户在全新安装后直接恢复数据。

4.  **架构演进 - 从单一文件到保险库目录**:
    *   为了支持安全笔记、附件等未来功能，我们将存储模型从单一的`hellopocket.dat`文件，重构为“保险库目录”方案。在此方案中，**分类数据 (`categories.dat`) 已从密码数据 (`passwords.dat`) 中剥离**，成为一个独立文件，为跨功能复用做好了准备。

5.  **你的项目规范和偏好**:
    *   **依赖注入**: 使用 **Koin**，并且模块按类别（`RepositoryModule`, `ViewModelsModule`）拆分，最后在 `KoinModules.kt` 中聚合。
    *   **导航**: 使用 **Compose Navigation**，并且路由（Route）以**类型安全**的方式（`@Serializable data object/class`）在 `router/RouterItems.kt` 中统一定义。
    *   **UI风格**: 简洁、大方、有呼吸感。使用更精致、完整的 `Material3` 色彩体系，组件（卡片、对话框）有更大的圆角和统一的视觉风格。
    *   **代码注释**: 在关键代码处需要有中文注释。
    *   **多语言支持**: 项目支持 **7 种语言**，所有新增文案必须同步更新到所有语言目录：
        - `values/` - English (默认)
        - `values-zh-rCN/` - 简体中文
        - `values-es/` - Español
        - `values-hi/` - हिन्दी
        - `values-ko/` - 한국어
        - `values-pt/` - Português
        - `values-vi/` - Tiếng Việt

6. **重要提示**:
    *   **build**: 每次代码修改完成后，默认只需运行 `./gradlew :app:compileGooglePlayDebugKotlin`，确认代码没有编译问题；**不需要**运行 `./gradlew assembleGooglePlayDebug` 生成 APK。只有在用户明确要求打包、安装或验证 APK 时，才运行对应的 `assemble` 或 `install` 任务。
    *   **禁止省略代码**: 在修改任何文件时，**必须提供完整、全量的代码**，严禁使用 `/* ... */` 或其他方式省略已有代码。
    *   **修改前先读取**: 在对一个已存在的文件进行复杂修改前，**必须先使用 `read_file` 工具读取其最新内容**，确保操作是基于最新状态，而不是依赖可能有偏差的短期记忆。
    *   **注意依赖关系**: 在引入需要特定库支持的功能时，必须检查并确保相关的Gradle依赖已被正确添加。
    *   **禁止回退方案**: 在没有征得同意的情况下，严禁私自放弃某个技术方案并回退到旧方案。

## Build Commands

```bash
# Default verification after code changes (compile only, do not generate APK)
./gradlew :app:compileGooglePlayDebugKotlin

# Build all debug variants
./gradlew assembleDebug

# Build all release variants
./gradlew assembleRelease

# Build specific flavor only when packaging or installation is explicitly requested
./gradlew assembleGooglePlayDebug
./gradlew installGooglePlayDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Run lint checks
./gradlew lint
./gradlew lintFix
```

## Build Flavors

项目只保留一个 `googlePlay` product flavor。Google Play 与 GitHub Release 使用同一个
`googlePlayRelease` AAB 来源和 `com.turisla.hellopocket` applicationId。GitHub 只发布从
Play Console 下载、由 Play App Signing 签名的 Universal APK，不能发布使用 upload key、
其他本地 release key 或 debug key 签名的 APK。

所有 debug 构建额外使用 `.debug` applicationId 后缀，可与正式版共存，且不会被误认为官方签名包。

## Architecture

### Package Structure

```
com.turisla.hellopocket/
├── security/           # Encryption (TinkCryptoManager), biometric auth
├── data/              # Repositories, preferences, data layer
├── ui/
│   └── feature/       # Feature-based UI organization
│       ├── auth/      # Setup, unlock screens
│       ├── home/      # Main password list
│       ├── detail/    # Password/note detail view
│       ├── addPassword/
│       ├── addSecureNote/
│       ├── settings/  # Settings (appearance, security, data, other)
│       ├── passwordGenerator/
│       ├── category/
│       ├── totp/      # TOTP (双因素认证) 功能
│       ├── mainPage/  # Main navigation
│       └── common/    # Reusable UI components
├── utils/             # Constants, dimens, utilities
├── di/                # Koin modules (viewModelsModule, repositoryModule)
└── router/            # Navigation routes (RouteSetup, RouteUnlock, etc.)
```

### Key Components

- **TinkCryptoManager** (`security/TinkCryptoManager.kt`) - Core encryption using Google Tink StreamingAead. Handles vault keyset generation, password-based encryption via PBKDF2.
- **BiometricCipherManager** (`security/BiometricCipherManager.kt`) - Biometric authentication integration
- **Router** (`router/RouterItems.kt`) - Kotlin-serialized navigation routes using Jetpack Navigation Compose

### Technology Stack

- Kotlin 2.2.10, Android Gradle Plugin 8.10.1
- Jetpack Compose BOM for UI
- Koin for dependency injection
- Google Tink for encryption
- Protocol Buffers + Kotlinx.serialization for data
- Strictly offline: no Internet permission, analytics, telemetry, or crash-reporting SDK
- Coil for image loading
- **kotlin-onetimepassword** (dev.turingcomplete:kotlin-onetimepassword:2.4.1) - TOTP/HOTP 算法实现
- **zxing-android-embedded** - QR 码扫描
- **accompanist-permissions** - 权限请求处理

## Security Considerations

- All sensitive screens use `FLAG_SECURE` to prevent screenshots
- Clipboard auto-clears after 60 seconds
- Auto-lock after 1 minute in background
- SHA-256 hash for data integrity verification
- Never log or expose sensitive data in error messages

## Development Environment

- Min SDK: 24 (Android Nougat)
- Target SDK: 36
- JDK: 17
