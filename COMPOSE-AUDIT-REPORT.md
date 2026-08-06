# Jetpack Compose Audit Report

Target: `/Users/wangpeng/projects/self/HelloPocket`
Date: 2026-08-05
Scope: `app/src/main/java/com/turisla/hellopocket/ui/**`、`ui/theme/**`，以及本轮 UI 直接调用的导航、搜索与页面级 ViewModel
Excluded from scoring: `app/src/test/**`、`app/src/androidTest/**`、`@Preview` 函数、生成代码与 `app/build/**`
Confidence: High
Overall Score: 82/100

## Scorecard

| Category | Score | Weight | Status | Notes |
|----------|-------|--------|--------|-------|
| Performance | 8/10 | 35% | solid | 具名可重启 Composable 120/120 可跳过；共享组件仍有 3 类不稳定参数 |
| State management | 8/10 | 25% | solid | 页面状态整体遵循 ViewModel + StateFlow；少量内部组件仍直接依赖 ViewModel |
| Side effects | 9/10 | 20% | excellent | 生命周期、清理、导航和事务边界清晰；复杂相机生命周期仍需真机覆盖 |
| Composable API quality | 8/10 | 20% | solid | 新公共组件 API 一致；部分旧共享组件缺少 `modifier` 与设计令牌化 |

## Critical Findings

本轮修复后，没有尚未解决的 P2 或 P1 问题。

### Resolved During This Audit

1. **P2：页面作用域写操作可被返回导航取消**
   - Why it mattered: 新增密码、笔记、详情编辑/删除、分类写入、主密码修改和备份操作都可能因页面销毁取消；加载异常还可能让历史页永久锁在遮罩层。
   - Evidence: `ui/feature/addPassword/AddPasswordScreen.kt:140`、`ui/feature/detail/DetailScreen.kt:133`、`ui/feature/category/CategoryManagementScreen.kt:57`、`ui/feature/settings/HistoryViewModel.kt:35`
   - Resolution: 写入期间统一禁用返回和重复操作；所有加载分支在 `finally` 复位；`CancellationException` 保持向上传播。
   - References: <https://developer.android.com/develop/ui/compose/side-effects>, <https://developer.android.com/develop/ui/compose/architecture>

2. **P2：连续扫码可能并发写入同一 TOTP 条目**
   - Why it mattered: ZXing 连续解码会在相邻帧重复回调，可能启动多个保险库写任务。
   - Evidence: `ui/feature/totp/ScannerViewModel.kt:36`、`ui/feature/totp/ScannerScreen.kt:75`
   - Resolution: 使用原子处理锁只接收一次结果，处理中卸载扫码视图并禁止返回，导航完成后才释放锁。
   - References: <https://developer.android.com/develop/ui/compose/state>, <https://developer.android.com/develop/ui/compose/side-effects>

3. **P2：部分表单/对话框在横屏或大字体下操作区可能不可达**
   - Why it mattered: 固定高度内容和不可滚动选项会让保存、取消等关键操作落到视口之外。
   - Evidence: `ui/feature/common/CategoryDialogs.kt:105`、`ui/feature/settings/ChangePasswordScreen.kt:113`、`ui/feature/settings/AppearanceSettingsScreen.kt:150`
   - Resolution: 表单和长选项列表增加受约束滚动；搜索框采用单行 `bodyMedium` 与最小高度，避免正常输入改变高度且允许大字体扩展。
   - References: <https://developer.android.com/develop/ui/compose/api-guidelines>, <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>

## Category Details

### Performance — 8/10

**What is working**

- 主页、搜索、TOTP、分类和附件列表使用稳定业务 ID 作为 lazy key；主页和搜索异构列表同时声明 `contentType`。References: <https://developer.android.com/develop/ui/compose/lists>
- 附件过滤与搜索高亮文本使用输入作为 `remember` key，避免无关重组重复计算。References: <https://developer.android.com/develop/ui/compose/performance/bestpractices>
- Strong Skipping 已开启；编译器报告中 120 个具名可重启 Composable 全部可跳过。References: <https://developer.android.com/develop/ui/compose/performance/stability/strongskipping>, <https://developer.android.com/develop/ui/compose/performance/tooling>

**What is hurting the score**

- 编译器仍将共享组件参数中的 `List`、`Set`、`SuspendFunction1` 三类类型标记为不稳定，因此按量化规则将性能分数上限设为 8。References: <https://developer.android.com/develop/ui/compose/performance/stability>, <https://developer.android.com/develop/ui/compose/performance/stability/fix>
- 每个展开的 TOTP 条目都以 100ms 周期更新 Compose state；同时展开多个条目时会产生持续重组。References: <https://developer.android.com/develop/ui/compose/performance/phases>, <https://developer.android.com/develop/ui/compose/performance/bestpractices>

**Evidence**

- `ui/feature/home/HomePage.kt:385` — 条目使用唯一 ID key 和条目类型 `contentType`。References: <https://developer.android.com/develop/ui/compose/lists>
- `ui/feature/search/SearchScreen.kt:286` — 搜索结果同样声明唯一 key 与固定 `contentType`。References: <https://developer.android.com/develop/ui/compose/lists>
- `ui/feature/common/AttachmentSection.kt:32`、`ui/feature/common/CategoryDialogs.kt:76` — 共享 API 暴露编译器判定为不稳定的集合/挂起函数参数。References: <https://developer.android.com/develop/ui/compose/performance/stability/fix>
- `ui/feature/totp/TotpListItem.kt:72` — 展开状态下每 100ms 更新一次验证码倒计时。References: <https://developer.android.com/develop/ui/compose/performance/phases>

Compiler measurement:

- Named-only: `120 / 120 = 100%` skippable（用于评分上限）
- Module-wide: `545 / 706 = 77.2%` skippable；该值包含结构上不可跳过的编译器生成 lambda，不用于具名上限判断
- Strong Skipping: enabled
- Reports: `app/build/compose_audit/app_googlePlayDebug-composables.csv`、`app/build/compose_audit/googlePlayDebug/app_googlePlayDebug-module.json`

### State Management — 8/10

**What is working**

- Android UI 状态统一使用 `collectAsStateWithLifecycle()`，过滤结果使用 `stateIn(... WhileSubscribed(5_000) ...)`。References: <https://developer.android.com/develop/ui/compose/state>, <https://developer.android.com/develop/ui/compose/architecture>
- 搜索、草稿、删除/加载状态都有明确单一所有者；添加与详情草稿绑定对应导航返回栈 ViewModel。References: <https://developer.android.com/develop/ui/compose/state-hoisting>, <https://developer.android.com/develop/ui/compose/architecture>
- 主导航 tab 使用 `rememberSaveable` 和 `mutableIntStateOf`；导航路由使用 `@Serializable` 类型安全定义。References: <https://developer.android.com/develop/ui/compose/state>, <https://developer.android.com/develop/ui/compose/navigation>

**What is hurting the score**

- 添加密码/笔记的内部分类组件仍直接接收 `HomePageViewModel`，使展示组件依赖页面状态持有者，降低复用和测试隔离。References: <https://developer.android.com/develop/ui/compose/architecture>, <https://developer.android.com/develop/ui/compose/state-hoisting>

**Evidence**

- `ui/feature/home/HomePageViewModel.kt:82` — 过滤流由 ViewModel 持有并按订阅生命周期共享。References: <https://developer.android.com/develop/ui/compose/architecture>
- `ui/feature/search/SearchScreen.kt:76` — 搜索页面在入口层生命周期感知地收集状态，再向下传值与事件。References: <https://developer.android.com/develop/ui/compose/state-hoisting>
- `ui/feature/addPassword/AddPasswordScreen.kt:534`、`ui/feature/addSecureNote/AddSecureNoteScreen.kt:416` — 私有展示组件继续接收完整 ViewModel。References: <https://developer.android.com/develop/ui/compose/architecture>

### Side Effects — 9/10

**What is working**

- 导航、Toast、仓库写入和焦点请求均位于事件回调或 `LaunchedEffect`，没有在 composition body 直接执行外部操作。References: <https://developer.android.com/develop/ui/compose/side-effects>, <https://developer.android.com/develop/ui/compose/navigation>
- 相机生命周期观察器在 `DisposableEffect` 中注册并在 `onDispose` 清理；加载回调使用 `rememberUpdatedState` 避免捕获旧 lambda。References: <https://developer.android.com/develop/ui/compose/side-effects>
- 用户触发的写任务使用 `rememberCoroutineScope`，并通过 busy state、`BackHandler` 和取消异常传播维护事务边界。References: <https://developer.android.com/develop/ui/compose/side-effects>

**What is hurting the score**

- `AndroidView + CaptureManager + LifecycleEventObserver` 是本项目最复杂的外部生命周期桥接；静态检查正确，但尚无仪器测试覆盖暂停/恢复、授权变化和快速返回组合。References: <https://developer.android.com/develop/ui/compose/side-effects>

**Evidence**

- `ui/feature/totp/ScannerScreen.kt:214` — 观察器注册、暂停、销毁和移除均在同一 `DisposableEffect` 内。References: <https://developer.android.com/develop/ui/compose/side-effects>
- `ui/feature/addPassword/AddPasswordScreen.kt:130`、`ui/feature/detail/DetailScreen.kt:154` — 长生命周期回调使用 `rememberUpdatedState`。References: <https://developer.android.com/develop/ui/compose/side-effects>
- `ui/feature/settings/HistoryViewModel.kt:35` — I/O 分支在 `finally` 释放加载状态并保留协程取消语义。References: <https://developer.android.com/develop/ui/compose/side-effects>

### Composable API Quality — 8/10

**What is working**

- 新的 `AppSectionSurface`、`AppIconTile`、`AppListRow`、`AppEmptyState` 均提供单一 `modifier: Modifier = Modifier`，并将其应用在根节点。References: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>
- 组件使用 `ColumnScope`、`RowScope`、`BoxScope` slot API，并统一从 `MaterialTheme` 和 `AppSpacing` 取视觉令牌。References: <https://developer.android.com/develop/ui/compose/api-guidelines>, <https://developer.android.com/develop/ui/compose/designsystems/material3>
- 所有新增用户文案复用资源，没有在 UI 中新增不可本地化字符串。References: <https://developer.android.com/develop/ui/compose/resources>

**What is hurting the score**

- `SelectCategoryContent` 与 `CategoryCreationContent` 仍未暴露 `modifier`，调用方无法安全控制根布局。References: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>
- 旧的分类/附件共享组件仍混用较多硬编码 dp，而非统一间距令牌。References: <https://developer.android.com/develop/ui/compose/designsystems/material3>, <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>

**Evidence**

- `ui/feature/common/AppUiComponents.kt:31` — 新组件遵循 modifier、参数顺序和 slot 约定。References: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>
- `ui/feature/common/CategoryDialogs.kt:76`、`ui/feature/common/CategoryDialogs.kt:308` — 两个可复用内容组件缺少 `modifier` 参数。References: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>
- `ui/feature/common/AttachmentSection.kt:48` — 共享组件仍直接使用固定 padding/尺寸。References: <https://developer.android.com/develop/ui/compose/designsystems/material3>

## Prioritized Fixes

1. 将共享分类/附件组件的 `List`、`Set` 参数迁移到不可变展示集合，或增加经验证的 Compose 稳定性配置；目标是解除性能 8 分上限。
2. 从添加密码/笔记的内部分类组件移除 `HomePageViewModel`，改为只传入 `onCreateCategory` 等状态与事件回调。
3. 将 TOTP 100ms 倒计时更新限制为单个展开项，或把连续进度移动到 draw 阶段，避免多个条目持续重组。
4. 为 `SelectCategoryContent`、`CategoryCreationContent` 增加 `modifier`，并把共享组件中的固定间距迁移到 `AppSpacing`。

## Notes And Limits

- 审核覆盖单 app 模块的完整 Compose 生产 UI；测试和 Preview 仅用于验证，不参与评分。
- Weight choice: 默认 `35/25/20/20`，未调整。
- Renormalization: 无 N/A 类别，不需要重新归一化。
- Compiler diagnostics used: yes — `app/build/compose_audit/`。
- Verification: `:app:compileGooglePlayDebugKotlin` 通过；47 个 `googlePlayDebug` 单元测试通过；Lint 为 0 errors、35 warnings。现有 warnings 均为依赖版本、未使用资源或密度目录类告警。
- 本轮仅做编译、单测、Lint 与静态 Compose 诊断；未安装 APK，因此相机权限、IME、TalkBack、横屏和字体缩放仍需设备矩阵验证。
- 启动图标资源未修改；应用内品牌图仍直接复用现有 `R.drawable.app_icon`。

## Suggested Follow-Up

- 在真机上补一轮 Material 3/无障碍视觉验收：1.0x/1.3x/2.0x 字体、深浅色、横屏、手势导航、TalkBack，以及扫码页的授权/暂停/恢复路径。
