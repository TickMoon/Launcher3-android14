# Launcher3 项目笔记

## Gradle

**为什么修改 `quickstep/src/` 后，Android Studio 构建的 APK 看不到变化？**

当前 Gradle 配置启用的两个应用变体是 `aospWithoutQuickstepDebug` 和 `l3goWithoutQuickstepDebug`。两者都使用 `src/` 下的公共代码，并通过 `withoutQuickstep` 加入 `src_ui_overrides/`。声明 `quickstep/src/` 的 `withQuickstep` 源集及对应 flavor 均已注释，因此这里的修改不会编入这两个 APK。[构建配置](../build.gradle)

核对具体文件时，先在 Android Studio 的 **Build Variants** 查看所选变体，再到构建配置的 `android.sourceSets` 查 `main`、对应 flavor 和变体专属的声明。例如，Go 版还会从 `l3go` 源集加入 `go/src/` 和 `go/res/`。

## Soong

**系统镜像中的 Launcher 是否包含 Quickstep 源码？**

先从目标产品的配置确认镜像选用的 Launcher 模块，再沿模块的 `static_libs` 和 `srcs` 查到对应的 `filegroup`。本仓库的 [Android.bp](../Android.bp) 给出了两条典型路径：

- `Launcher3` 直接引用 `launcher-src_ui_overrides`，其中的 Java 和 Kotlin 实现来自 `src_ui_overrides/`。
- `Launcher3QuickStep` 静态依赖 `Launcher3QuickStepLib`，该库引用 `launcher-quickstep-src`，其中的 Java 和 Kotlin 实现来自 `quickstep/src/`。
