# Launcher3 项目指引

项目来源和 Android Studio 适配目标见 [README](README.md)。排查行为或选择修改位置时，先确认用户运行的构建目标，再定位该目标实际编入的实现。

## 按问题查阅

- 首次了解项目、定位跨模块问题或评估功能改动影响时，读[架构与功能概览](docs/architecture.md)。
- Android Studio 中修改未生效，或真机测试需要核对 APK 变体、源码目录和测试覆盖时，读[项目笔记的 Gradle 小节](docs/project-notes.md#gradle)。
- 排查系统镜像里的桌面或最近任务时，读[项目笔记的 Soong 小节](docs/project-notes.md#soong)。
- 排查桌面图标的缓存、备份恢复或比较加载耗时时，读[桌面图标小节](docs/project-notes.md#桌面图标)。
- 拟写提交信息或执行提交时，读[提交信息规范](docs/commit-messages.md)。
- 修改应用上桌、首尾翻页、分页圆点或 Hotseat 拖放时，读[主桌面交互小节](docs/project-notes.md#主桌面交互)。

## 维护项目知识

任务中确认了可复用的项目结论，就在对应笔记小节写明适用目标、代码依据和核对方法。发现旧结论过期时直接修订原条目。新增主题时在上面的索引补充适用场景。单次任务进度和待验证的推测留在当前聊天。
