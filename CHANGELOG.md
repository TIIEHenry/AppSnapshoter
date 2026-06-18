# Changelog

本文件记录 AppSnapshoter 各版本的面向用户变更。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added

- 时间线 Tab：按时间范围跨分组浏览快照，支持多选批量恢复、删除与导出
- 快照目录写入 `.nomedia`，避免图库扫描应用图标缓存

### Changed

- 刷新主界面 UI（顶栏、悬浮底栏、内容区布局）

### Fixed

- 添加分组后归档列表不立即显示新分组（`viewModelScope` 失效导致刷新静默失败）
- 添加应用到分组后列表与「全部归档」偶发不同步（`loadGroups` 改走 repository 互斥刷新）

## [1.0] - 2026-05-21

### Added

- 一键存档 / 一键恢复：长按应用快速创建快照并还原
- 基于 JNI 的 TAR + ZSTD 压缩管道，支持 FIFO 流式打包
- APK 智能去重、多存档管理、自定义压缩目录
- 分组管理：按组批量快照与恢复
- Root 服务架构（AIDL + libsu），UI 与 Root 逻辑隔离
- 保留策略与排除策略配置 UI
