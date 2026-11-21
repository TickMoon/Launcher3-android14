# Launcher3 项目笔记

## Gradle

**为什么修改 `quickstep/src/` 后，Android Studio 构建的 APK 看不到变化？**

当前 Gradle 配置启用的两个应用变体是 `aospWithoutQuickstepDebug` 和 `l3goWithoutQuickstepDebug`。两者都使用 `src/` 下的公共代码，并通过 `withoutQuickstep` 加入 `src_ui_overrides/`。声明 `quickstep/src/` 的 `withQuickstep` 源集及对应 flavor 均已注释，因此这里的修改不会编入这两个 APK。[构建配置](../build.gradle)

核对具体文件时，先在 Android Studio 的 **Build Variants** 查看所选变体，再到构建配置的 `android.sourceSets` 查 `main`、对应 flavor 和变体专属的声明。例如，Go 版还会从 `l3go` 源集加入 `go/src/` 和 `go/res/`。

## 主桌面交互

`aospWithoutQuickstepDebug` 和 `l3goWithoutQuickstepDebug` 使用 Workspace 和 Hotseat 作为应用入口。主桌面的触摸控制器、系统应用列表 Intent、键盘快捷键和无障碍操作都应与这一行为一致。Launcher 已移除显示 `DiscoveryBounce` 的调用，回到桌面后 Hotseat 保持静止。核对入口时，从 Home 上滑并发送 `android.intent.action.ALL_APPS`，两次操作都应停留在桌面。[Launcher](../src/com/android/launcher3/Launcher.java)

模型加载完成及应用新增、更新后，按组件和用户检查桌面、Hotseat 和普通文件夹，依次填充空闲 Hotseat 槽和桌面网格，必要时新增一页。比对升级前后 `favorites` 表中同一 `_id` 的容器、页号与坐标，并将 `profileId` 和 `intent` 中的组件与该用户可固定的启动入口对照。[SyncWorkspaceAppsTask](../src/com/android/launcher3/model/SyncWorkspaceAppsTask.java)

普通桌面翻到边界时，[Workspace](../src/com/android/launcher3/Workspace.java) 将另一端的页面显示在当前页旁。`Launcher.onCreateView()` 将布局声明的分页指示器替换为 [PageIndicatorDots](../src/com/android/launcher3/pageindicators/PageIndicatorDots.java)。核对时分别向两个方向跨过首尾，观察拖动中页面和高亮圆点的方向，以及松手后的落点。

Hotseat 使用持久化槽号保存图标顺序，按实际参与排列的槽数均分显示区域。拖入一个新图标时，有空位就为它预留一槽。检查推挤时，选用槽号不连续的布局，按住图标并等待邻居让位，比对松手前后的槽中心，再取消一次拖动核对原顺序。[Hotseat](../src/com/android/launcher3/Hotseat.java) · [ShortcutAndWidgetContainer](../src/com/android/launcher3/ShortcutAndWidgetContainer.java)

## Soong

**系统镜像中的 Launcher 是否包含 Quickstep 源码？**

先从目标产品的配置确认镜像选用的 Launcher 模块，再沿模块的 `static_libs` 和 `srcs` 查到对应的 `filegroup`。本仓库的 [Android.bp](../Android.bp) 给出了两条典型路径：

- `Launcher3` 直接引用 `launcher-src_ui_overrides`，其中的 Java 和 Kotlin 实现来自 `src_ui_overrides/`。
- `Launcher3QuickStep` 静态依赖 `Launcher3QuickStepLib`，该库引用 `launcher-quickstep-src`，其中的 Java 和 Kotlin 实现来自 `quickstep/src/`。
