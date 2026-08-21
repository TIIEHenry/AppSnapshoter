---
title: "术语表"
type: guide
status: active
updated: 2026-08-21
summary: "项目中使用的专业术语和缩写定义"
---

# 术语表

## 核心概念

| 术语 | 定义 |
|------|------|
| **快照 (Snapshot)** | 应用数据的压缩备份，包含 APK、data、obb、media 等，格式为 `.tar.zst` |
| **存档 (Archive)** | 快照文件的统称，每个应用可有多个历史存档 |
| **分组 (Group)** | 应用的逻辑集合（`SnapGroup`），用于批量操作和管理。每个分组有独立的存档目录和配置 |
| **分组集 (Group Set)** | 同一父目录下多个分组的组织容器（`SnapGroupSet`）。该目录即分组集；添加时扫描直接子目录自动登记为分组。快照/恢复仍以分组为单元。见 [分组集](systems/snapshot/GROUP_SET.md) |
| **独占组 (Exclusive Group)** | `membershipMode=exclusive`（默认）。成员占用应用归属；同一 `(packageName, userId)` 至多属于一个独占组。见 [分组应用归属与移动](systems/snapshot/GROUP_MEMBERSHIP.md) |
| **共享组 (Shared Group)** | `membershipMode=shared`。成员不占用归属、不参与冲突检测；适合收藏/主题型集合。见同上 |
| **未分组应用** | 不在任何**独占**组中的应用（可仅存在于共享组）。应用 Tab「未分组」筛选按此定义 |
| **应用移动 (Move App)** | 将某应用在源组下的 `packageDir`（含存档）迁到目标独占组，并清理源侧成员关系。与「仅加成员」分离 |
| **排除规则 (Exclude Pattern)** | 配置中指定不纳入快照的文件/目录匹配模式，按压缩类型分类 |
| **额外压缩项 (Extra Item)** | 除默认目录外，用户自定义添加的额外压缩目录 |
| **保留策略 (RetentionPolicy)** | 自动清理旧存档的规则，由 `RetentionPolicyExecutor` 执行 |

## 系统架构

| 术语 | 定义 |
|------|------|
| **Root Service** | 通过 libsu 运行在 Root 进程中的服务。本项目有两个：`SnapshotRootService`（主 AIDL）和 `FileSystemManagerRootService`（libsu-nio） |
| **Handler** | Root 服务内部的领域委托类（`AppManagementHandler`、`PermissionManagementHandler` 等），隔离各功能域 |
| **Providers** | App 层访问 Root 服务的统一入口接口，由 `ProvidersImpl` 实现 |
| **AIDL** | Android Interface Definition Language，用于跨进程通信接口定义。本项目 `ISnapShotRootService` 有 30+ 方法 |
| **libsu** | topjohnwu 开发的 Android Root Shell 管理库，本项目用于 Root 服务 IPC |
| **libsu-nio** | libsu 的 NIO 扩展，提供 `FileSystemManager` 用于高效 Root 文件 I/O |

## 数据格式与压缩

| 术语 | 定义 |
|------|------|
| **TAR** | Tape Archive，归档格式，将多个文件打包为单个文件，不做压缩。本项目内嵌 GNU tar |
| **ZSTD (Zstandard)** | Facebook 开发的高性能压缩算法。本项目内嵌 zstd-jni，支持级别 1-19 |
| **FIFO** | 先进先出管道（Named Pipe），通过 `Os.mkfifo()` 创建，用于 TAR→ZSTD 流式传输 |
| **FlowableStreamParallelCopier** | 高速并行流拷贝器，读/写双线程 + 128KB 缓冲队列，带 `StateFlow` 进度追踪 |
| **ParcelFileDescriptor** | Android 跨进程文件描述符传递机制，用于压缩服务的输入/输出 |

## JNI 与原生层

| 术语 | 定义 |
|------|------|
| **JNI** | Java Native Interface，本项目 3 个原生模块（io-nativefs、io-tar、io-zstd）均使用 |
| **FTS** | File Tree Walk (POSIX `fts_open/fts_read/fts_close`)，高效目录遍历 API，io-nativefs 使用 |
| **NDK** | Android Native Development Kit，版本 25.2.9519653 |
| **CMake** | 跨平台构建工具，版本 3.22.1，用于编译 JNI 模块 |

## Android 平台

| 术语 | 定义 |
|------|------|
| **SSAID** | Android Software Signing Identifier，每应用唯一标识，`SsaidManagementHandler` 可读写 |
| **AppOps** | Android 应用操作权限管理框架，`PermissionManagementHandler` 通过 `AppOpsManagerHidden` 控制 |
| **OBB** | Opaque Binary Blob，Android 扩展文件，游戏等大型应用的数据包 |
| **APK** | Android Package，应用安装包文件 |
| **SAF** | Storage Access Framework，Android 存储访问框架，`GroupPathPickerHelper` 用于路径选择 |
| **MMKV** | 腾讯开发的高性能 KV 存储，本项目用于全部配置持久化 |

## UI 框架

| 术语 | 定义 |
|------|------|
| **ViewBinding** | Android 视图绑定，编译时生成视图引用类，零运行时开销 |
| **DataBinding** | Android 数据绑定，支持在 XML 中绑定表达式 |
| **MVVM** | Model-View-ViewModel 架构模式 |
| **BottomSheet** | Material3 底部弹出面板，本项目有 10 个 BottomSheetDialogFragment |
| **热力图 (Heatmap)** | `TimelineHeatmapView` 自定义 View，颜色深浅表示快照密度 |

## Root 方案

| 术语 | 定义 |
|------|------|
| **Magisk** | 主流 Android Root 方案，systemless 模式 |
| **KernelSU** | 基于内核的 Root 方案 |
| **APatch** | Android 内核补丁 Root 方案 |

## 同步

| 术语 | 定义 |
|------|------|
| **Syncthing** | 开源 P2P 文件同步工具，可跨设备同步快照数据和 JSON 配置 |

## 第三方内嵌

| 术语 | 定义 |
|------|------|
| **nota.io** | 内嵌的并行流拷贝库（`StreamParallelCopier` + `FlowableStreamParallelCopier`） |
| **nota.lang.reflect** | 内嵌的反射缓存库（`ReflectionCache`），用于隐藏 API 调用 |
| **Rikka Refine** | 编译时字节码精化工具，`@RefineAs` 注解替换隐藏 API 桩代码为真实实现 |
