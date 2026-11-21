# Launcher3 项目笔记

## 应用版本与标签

标准版和 Go 版共用根目录 [build.gradle](../build.gradle) 的三段基础版本号 `主版本.次版本.修订号`。一次发布包含多类改动时，按影响最大的一类递增。

| 升级哪一位 | 适用改动 | 示例 |
| --- | --- | --- |
| 主版本 | 替换或移除主要用户操作方式，或引入不兼容的升级要求 | 从初始版 `1.0` 升到 `2.0.0`，移除 AllApps，改由 Workspace 和 Hotseat 展示应用 |
| 次版本 | 在保留已有操作方式的基础上新增功能 | `2.0.0` → `2.1.0` |
| 修订号 | 修复缺陷或改进性能，维持已有功能和操作方式 | `2.1.0` → `2.1.1` |

主版本递增时将次版本和修订号归零，次版本递增时将修订号归零。Android 的 `versionCode` 固定为五位十进制整数。主版本占一位（1–9），次版本和修订号各占两位（00–99）。Gradle 按 `主版本 × 10000 + 次版本 × 100 + 修订号` 自动生成，例如 `2.0.0` 对应 `20000`，`2.1.3` 对应 `20103`。两个变体共用这组版本数字。

Gradle 将 APK 的 `versionName` 拼成 `<基础版本>-<渠道>-<构建类型>-<分支>-<短哈希>`，例如 `2.0.0-go-debug-main-41996127`。渠道由 `app` 维度的 flavor 决定，`aosp` 对应 `aosp`，`l3go` 对应 `go`。构建类型取 `debug` 或 `release`，目前项目只启用了 Debug 变体。

在 Git 工作树中构建时，Gradle 读取当前分支，将分支名中的 `/` 替换为 `-`。检出标签或提交而处于 detached HEAD 时，可以通过 `-PbuildBranch=<分支名>` 传入来源分支；GitHub Actions 也可从 PR 源分支或分支事件取得名称。无分支信息时显示 `detached`。短哈希由 `git rev-parse --short=8 HEAD` 生成，可在包含该提交的仓库中用 `git rev-parse --verify "<短哈希>^{commit}"` 核对完整提交号。

Git 使用附注标签 `v<基础版本>`，同一次发布的两个渠道共用一个标签。发布时提交版本相关改动，从该提交构建两个 APK，核对版本名末尾的短哈希及 `versionCode`，再将标签打在该提交上。本次里程碑的标签为 `v2.0.0`。

## Gradle

**为什么修改 `quickstep/src/` 后，Android Studio 构建的 APK 看不到变化？**

为了让仓库脱离完整 AOSP 源码树也能在 Android Studio 中构建，Gradle 适配时只保留了不含 Quickstep 的桌面变体。`aospWithoutQuickstepDebug` 和 `l3goWithoutQuickstepDebug` 都使用 `src/` 下的公共代码，并通过 `withoutQuickstep` 加入 `src_ui_overrides/`。声明 `quickstep/src/` 的 `withQuickstep` 源集及对应 flavor 均已注释，因此这里的修改不会编入这两个 APK。[构建配置](../build.gradle)

核对具体文件时，先在 Android Studio 的 **Build Variants** 查看所选变体，再到构建配置的 `android.sourceSets` 查 `main`、对应 flavor 和变体专属的声明。例如，Go 版还会从 `l3go` 源集加入 `go/src/` 和 `go/res/`。

**真机测试时，怎样确认安装的是哪个 Gradle 变体，覆盖了哪些测试？**

两个变体的应用包名都是 `com.android.launcher3`。用 `adb shell pm path com.android.launcher3` 找到设备上的 `base.apk`，对该路径执行 `adb shell sha256sum <设备路径>`。在 Windows 上用 `Get-FileHash <本地APK路径>` 比对，再从同目录的 `output-metadata.json` 核对变体名。

标准版和 Go 版共用基础版本和 `versionCode`，各自的 `versionName` 标明渠道、构建类型、分支和提交。Gradle 将应用 APK 命名为 `<versionName>.apk`。核对产物时，查看 `output-metadata.json` 中的 `elements[0]`，确认 `outputFile` 等于 `versionName` 加上 `.apk`，并核对同目录中的实际文件和 APK 清单内的 `versionName`、`versionCode`。上游源码基线见 README。[构建配置](../build.gradle) · [项目来源](../README.md)

当前 Gradle 配置声明了测试 runner，但映射 `tests/src`、`tests/tapl` 和 `tests/res` 的 `androidTest` 源集已被注释。核对用例覆盖时，应检查该变体实际包含的测试类及 runner 报告的执行数量。仅生成测试 APK 不能证明 TAPL 用例已运行。[构建配置](../build.gradle)

构建 Go 版可执行 `gradlew.bat :assembleL3goWithoutQuickstepDebug`。该变体使用 4×4 桌面网格，[Go 版 WidgetsModel](../go/src/com/android/launcher3/model/WidgetsModel.java) 禁用了微件和通知圆点。核对网格配置时查看 [Go 版设备配置](../go/res/xml/device_profiles.xml)。

**怎样确认主桌面的应用入口和返回行为？**

两个 Gradle 变体的主桌面通过 Workspace 和 Hotseat 启动应用。用 `adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME` 核对默认桌面。按 Home 后上滑或向 Launcher 发送 `android.intent.action.ALL_APPS`，可用 UI Automator 确认界面仍包含 `workspace` 和 `hotseat`。在 Android 11 上，[Launcher.onBackPressed()](../src/com/android/launcher3/Launcher.java) 调用父类的返回处理。按返回后若切到其他桌面，可在 `adb logcat -b events` 中查 `wm_finish_activity` 和 `wm_set_resumed_activity`，确认当前 Activity 是否结束及随后恢复的窗口。

## 主桌面交互

`aospWithoutQuickstepDebug` 和 `l3goWithoutQuickstepDebug` 使用 Workspace 和 Hotseat 作为应用入口。主桌面的触摸控制器、系统应用列表 Intent、键盘快捷键和无障碍操作都应与这一行为一致。Launcher 已移除显示 `DiscoveryBounce` 的调用，回到桌面后 Hotseat 保持静止。核对入口时，从 Home 上滑并发送 `android.intent.action.ALL_APPS`，两次操作都应停留在桌面。[Launcher](../src/com/android/launcher3/Launcher.java)

模型加载完成及应用新增、更新后，按组件和用户检查桌面、Hotseat 和普通文件夹，依次填充空闲 Hotseat 槽和桌面网格，必要时新增一页。比对升级前后 `favorites` 表中同一 `_id` 的容器、页号与坐标，并将 `profileId` 和 `intent` 中的组件与该用户可固定的启动入口对照。[SyncWorkspaceAppsTask](../src/com/android/launcher3/model/SyncWorkspaceAppsTask.java)

普通桌面翻到边界时，[Workspace](../src/com/android/launcher3/Workspace.java) 将另一端的页面显示在当前页旁。`Launcher.onCreateView()` 将布局声明的分页指示器替换为 [PageIndicatorDots](../src/com/android/launcher3/pageindicators/PageIndicatorDots.java)。核对时分别向两个方向跨过首尾，观察拖动中页面和高亮圆点的方向，以及松手后的落点。

两个 Gradle 变体都预留了负一屏接口。`Launcher.getDefaultOverlay()` 返回空实现；接入自定义内容时，`setLauncherOverlay()` 可将首页起始边缘的越界手势交给 Overlay。`Workspace.onViewAdded()` 只接受 `CellLayout` 作为直接子页。核对接入时，先查 `setLauncherOverlay()` 的实际调用方，再分别用单页和多页布局从首页向外滑动：`PagedView` 仅在 `getLoopPageStep() == 0` 时将拖动交给边缘效果，普通多页浏览会优先循环到末页。[Launcher](../src/com/android/launcher3/Launcher.java) · [Workspace](../src/com/android/launcher3/Workspace.java) · [PagedView](../src/com/android/launcher3/PagedView.java)

在这两个 Gradle 变体中，`setLauncherOverlay()` 接收 Launcher 进程内的 Java 回调。`OverlayEdgeEffect` 向它传递触摸事件、拖动进度和速度，跨进程连接与页面展示需由接入方实现。仓库中的桌面网格预览可供对照：`PreviewSurfaceRenderer` 调用 `SurfaceControlViewHost.setView()` 挂载布局，`GridCustomizationsProvider.getPreview()` 向调用方返回 `SurfacePackage`。这套远程渲染 API 从 Android 11（API 30）起可用，项目最低支持 API 28。[手势转发](../src/com/android/launcher3/util/OverlayEdgeEffect.java) · [预览渲染](../src/com/android/launcher3/graphics/PreviewSurfaceRenderer.java) · [预览提供方](../src/com/android/launcher3/graphics/GridCustomizationsProvider.java) · [构建配置](../build.gradle) · [Android 接口说明](https://developer.android.com/reference/android/view/SurfaceControlViewHost)

Hotseat 使用持久化槽号保存图标顺序，按实际参与排列的槽数均分显示区域。拖入一个新图标时，有空位就为它预留一槽。检查推挤时，选用槽号不连续的布局，按住图标并等待邻居让位，比对松手前后的槽中心，再取消一次拖动核对原顺序。[Hotseat](../src/com/android/launcher3/Hotseat.java) · [ShortcutAndWidgetContainer](../src/com/android/launcher3/ShortcutAndWidgetContainer.java)

## Soong

**系统镜像中的 Launcher 是否包含 Quickstep 源码？**

先从目标产品的配置确认镜像选用的 Launcher 模块，再沿模块的 `static_libs` 和 `srcs` 查到对应的 `filegroup`。本仓库的 [Android.bp](../Android.bp) 给出了两条典型路径：

- `Launcher3` 直接引用 `launcher-src_ui_overrides`，其中的 Java 和 Kotlin 实现来自 `src_ui_overrides/`。
- `Launcher3QuickStep` 静态依赖 `Launcher3QuickStepLib`，该库引用 `launcher-quickstep-src`，其中的 Java 和 Kotlin 实现来自 `quickstep/src/`。

**普通桌面 APK 能否接管系统最近任务？**

Gradle 的 `withoutQuickstep` 变体通过 `Launcher` 提供 HOME。用 `git show e87ff1f -- build.gradle settings.gradle` 可以核对 Android Studio 适配时一并注释的 `withQuickstep`、`SharedLibWrapper` 和 AOSP 预构建产物 `sysui_shared.jar`。系统镜像目标 `Launcher3QuickStep` 使用平台 API，作为 `system_ext` 特权应用构建，并要求产品提供对应的权限白名单。[HOME 声明](../AndroidManifest.xml) · [Soong 配置](../Android.bp)

AOSP SystemUI 从 `config_recentsComponentName` 取得目标包名，按系统应用条件查找 `QUICKSTEP_SERVICE`。绑定后，Launcher 的 `TouchInteractionService.onInitialize()` 接收 SystemUI 和 WM Shell 提供的 Binder 接口。核对设备时可在 SystemUI 的 `OverviewProxyService` dump 中查看 `mRecentsComponentName` 和 `isConnected`，分别确认配置的组件和实际连接状态。默认 HOME 由系统单独解析。[SystemUI 连接逻辑](https://android.googlesource.com/platform/frameworks/base/+/4ee4adbbc21f81fc90fe66cb3ba1b27bfe4a40ae/packages/SystemUI/src/com/android/systemui/recents/OverviewProxyService.java) · [Launcher 服务](../quickstep/src/com/android/quickstep/TouchInteractionService.java)

## 桌面图标

**为什么在标准桌面中，图标缓存与桌面布局都可能保存位图？**

在 `aospWithoutQuickstepDebug` 中，`app_icons.db` 的 `icons` 表按组件和用户缓存渲染结果。完整备份包含网格布局数据库；`app_icons.db` 未列入备份清单。添加或完整写回桌面项目时，`WorkspaceItemInfo` 和 `ContentWriter` 可以将高分辨率、非默认占位的位图写入 `favorites.icon`。恢复时，`RestoreDbTask` 给项目设置恢复标记，`LoaderCursor.getRestoredItemInfo()` 会尝试读取随布局保存的位图。因此，备份前的普通应用图标也可能在恢复期间用到这份快照。[备份清单](../res/xml/backupscheme.xml) · [项目写入](../src/com/android/launcher3/model/data/WorkspaceItemInfo.java) · [位图写入](../src/com/android/launcher3/util/ContentWriter.java) · [恢复标记](../src/com/android/launcher3/provider/RestoreDbTask.java) · [恢复时读图](../src/com/android/launcher3/model/LoaderCursor.java)

**在真机上怎样比较图标数据库与应用资源的加载成本？**

比较 `aospWithoutQuickstepDebug` 的彩色位图时，两组输入使用同一用户和同一组件集合，输出约定相同的尺寸、像素格式与色彩空间。A 只查询组件键和彩色 `icon` BLOB，解码 PNG。B 通过 `IconProvider` 取 Drawable，复用 `BaseIconFactory` 的归一化与带阴影绘制步骤，在生成彩色位图处停止。计时之外逐项比较像素，记录候选数、匹配数和排除数。单图与批量分别统计，交替 A/B 顺序，注明缓存温度。[PNG 写入](../iconloaderlib/src/com/android/launcher3/icons/GraphicsUtils.java) · [图标绘制步骤](../iconloaderlib/src/com/android/launcher3/icons/BaseIconFactory.java)

`BaseIconCache.updateTitleAndIconLocked()` 还读取标题、生成用户标签并处理可用的单色图层；直接对比它和 `LauncherActivityCachingLogic.loadIcon()` 会混入两边不同的工作量。测整个桌面的加载耗时则应保留业务路径，在系统跟踪中分别核对 `loadIconSubsectionWithDatabase` 和 `loadIconSubsectionWithFallback` 的样本、缓存命中与产物。[缓存读取](../iconloaderlib/src/com/android/launcher3/icons/cache/BaseIconCache.java) · [资源加载](../src/com/android/launcher3/icons/LauncherActivityCachingLogic.java) · [跟踪区段](../src/com/android/launcher3/icons/IconCache.java)
