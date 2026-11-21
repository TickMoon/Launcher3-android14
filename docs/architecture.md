# Launcher3 架构与功能概览

本项目基于 AOSP Launcher3（Android 14，`android-14.0.0_r75`），支持在 Android Studio 构建。当前主桌面通过 Workspace（分页桌面）和 Hotseat（固定应用栏）启动应用。主桌面已移除应用抽屉入口，模型仍使用已安装应用列表 `AllAppsList` 补齐桌面图标。[项目来源](../README.md) · [主桌面入口](../src/com/android/launcher3/Launcher.java) · [应用上桌逻辑](../src/com/android/launcher3/model/SyncWorkspaceAppsTask.java)

## 哪个构建目标运行这段代码

| 目标 | 从哪里定位 |
| --- | --- |
| Android Studio 的标准版和 Go 版 | `aospWithoutQuickstepDebug` 与 `l3goWithoutQuickstepDebug` 共用 `src/` 和 `src_ui_overrides/`，Go 版额外引入 `go/src/` 和 `go/res/`。两者的 HOME Activity 都是 `Launcher`。[Gradle 配置](../build.gradle) · [HOME 声明](../AndroidManifest.xml) |
| AOSP 系统镜像 | 从产品选择的 Soong 模块查起。`Launcher3` 使用 `src_ui_overrides/`，`Launcher3QuickStep` 通过 `Launcher3QuickStepLib` 引入 `quickstep/src/`。最近任务与系统手势问题先确认产品选用了哪个模块。[Soong 配置](../Android.bp) · [Quickstep 入口](../quickstep/AndroidManifest-launcher.xml) |

## 应用怎样出现在桌面上

1. **触发加载或更新。** HOME Activity `Launcher` 建立桌面视图，向 `LauncherModel` 注册回调并请求加载。首次加载由 `LoaderTask` 在加载事务提交后安排桌面同步；运行中新增或更新应用则由 `PackageUpdatedTask` 触发同一个同步任务。[Launcher](../src/com/android/launcher3/Launcher.java) · [LoaderTask](../src/com/android/launcher3/model/LoaderTask.java) · [PackageUpdatedTask](../src/com/android/launcher3/model/PackageUpdatedTask.java)
2. **决定是否上桌及放在哪里。** `SyncWorkspaceAppsTask` 将可固定的启动入口与 `BgDataModel` 中的布局按「组件和用户」比对。桌面、Hotseat、普通文件夹中的已有应用都计入去重。缺少的入口先填 Hotseat 空槽，再填桌面空格，已有页面放满后才新建页。[SyncWorkspaceAppsTask](../src/com/android/launcher3/model/SyncWorkspaceAppsTask.java)
3. **持久化与界面绑定。** 同步任务调用 `ModelWriter.addItemToDatabase()` 保存桌面项的容器、页或槽号和格子坐标，再通过 `bindAppsAdded` 通知桌面绑定新增项。排查“安装后看不到图标”时，可先查布局库的 `favorites` 表：缺少记录就查触发、去重和写入；已有记录就按容器与位置追到绑定回调。[ModelWriter](../src/com/android/launcher3/model/ModelWriter.java) · [Launcher](../src/com/android/launcher3/Launcher.java)

## 哪些交互需要一起核对

| 用户操作 | 预期行为与负责的代码 |
| --- | --- |
| 回到主桌面并上滑 | 保持桌面应用入口。调整手势时，一并核对 `ALL_APPS` Intent、键盘快捷键和无障碍操作，避免重新暴露抽屉入口。[Launcher](../src/com/android/launcher3/Launcher.java) · [键盘入口](../src/com/android/launcher3/util/KeyboardShortcutsDelegate.java) · [无障碍入口](../src/com/android/launcher3/views/AccessibilityActionsView.java) |
| 从首页或末页继续横滑 | 普通浏览时能沿手指方向跨过首尾，分页圆点同步越过边界。`Workspace.getLoopPageStep()` 决定何时启用循环；拖动图标或进入桌面模态状态时关闭循环。`PagedView` 处理滚动和落页，`PageIndicatorDots` 绘制相应进度。[Workspace](../src/com/android/launcher3/Workspace.java) · [PagedView](../src/com/android/launcher3/PagedView.java) · [PageIndicatorDots](../src/com/android/launcher3/pageindicators/PageIndicatorDots.java) |
| 拖入或移出 Hotseat | 持久化槽号保存顺序，屏幕上的图标中心按当前参与排列的槽数均分。`Workspace` 通知拖入和离开，`Hotseat` 计算预留槽和偏移，`ShortcutAndWidgetContainer` 将偏移应用到图标。修改排列时一起核对拖动预览、松手落点和取消后的顺序。[Workspace](../src/com/android/launcher3/Workspace.java) · [Hotseat](../src/com/android/launcher3/Hotseat.java) · [图标布局](../src/com/android/launcher3/ShortcutAndWidgetContainer.java) |

按具体任务继续查阅[Gradle 变体](project-notes.md#gradle)、[系统镜像目标](project-notes.md#soong)或[主桌面交互验证](project-notes.md#主桌面交互)。
