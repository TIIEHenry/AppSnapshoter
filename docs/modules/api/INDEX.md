---
title: "api 模块"
type: module
status: active
updated: 2026-06-17
summary: "AIDL 接口 + Java/Kotlin 接口契约，7 个 AIDL + 19 个源文件"
---

# api 模块

> 源码路径：`api/src/main/aidl/` 和 `api/src/main/java/`

## 概述

契约层，定义 Root 服务 IPC 的全部接口和数据类。**必须保持纯净** — 仅接口和数据类，无实现。26 个源文件（7 AIDL + 19 Java/Kotlin）。

## AIDL 接口

| 文件 | 职责 |
|------|------|
| `IFileCompressor.aidl` | 压缩服务接口（异步、可取消） |
| `ICompressCallback.aidl` | 压缩进度回调（onProgress、onComplete、onError） |
| `ITaskHandler.aidl` | 任务控制（cancel、isCancelled） |
| `AppInfo.aidl` | 应用信息 Parcelable 声明 |
| `AppDetail.aidl` | 应用详情 Parcelable 声明 |
| `AppPermission.aidl` | 应用权限 Parcelable 声明 |
| `AppStorage.aidl` | 存储信息 Parcelable 声明 |

## 纯接口

| 文件 | 职责 |
|------|------|
| `IPackageManager.java` | 包查询、安装/卸载、生命周期控制、启动应用 |
| `IPermissionManager.java` | 权限查询/修改、AppOps、SSAID 管理 |
| `IAppManager.java` | **已弃用** — 合并的包+权限管理接口 |
| `IFileSystem.java` | 文件系统操作（类型、列表、大小计算、tar、压缩） |
| `IProvider.java` | 泛型 Provider 接口（install + provide） |
| `FileSystemProvider.java` | IFileSystem 的抽象 Provider |
| `AppManagerProvider.java` | IAppManager 的抽象 Provider |
| `Providers.java` | 集中 Provider 管理，处理 Root 服务绑定 |
| `IServiceClient.java` | 服务客户端接口 |
| `IServiceRemoteObserver.java` | 远程服务观察者 |

## 数据类

| 文件 | 职责 |
|------|------|
| `AppInfo.kt` | 应用信息（包名、用户 ID、详情） |
| `AppDetail.kt` | 应用详细信息 |
| `AppPermission.java` | 应用权限（Parcelable，含授权状态、模式、操作码） |
| `AppStorage.kt`, `AppStorageDetail.kt` | 存储信息 |
| `UserInfoHide.kt` | 隐藏用户信息 |
| `IFileType.java` | 文件类型常量（NONE=-1, FILE=0, DIR=1, SYMLINK=2, OTHER=3） |
| `CompressorAlgorithms.java` | 压缩算法常量（TAR, ZSTD） |
| `CompressState.kt` | 压缩状态追踪 |

## 设计约束

- Java 接口（非 Kotlin）：AIDL 兼容性要求
- 命名空间：`tiiehenry.android.snapshot.api`（接口）、`tiiehenry.android.snapshot.app`（AIDL 数据）
- 无任何实现类或 Android 框架依赖

## 混合 AIDL/纯接口设计

| 接口 | 类型 | 原因 |
|------|------|------|
| `IFileCompressor` | AIDL | 需要异步回调 + 可取消任务 |
| `IPackageManager` | 纯 Java | 同步调用，进程内执行 |
| `IPermissionManager` | 纯 Java | 同步调用，进程内执行 |
| `IFileSystem` | 纯 Java | 同步调用，进程内执行 |

## 相关架构

- [Root 服务架构](../../architecture/root-service.md)
- [压缩管线](../../architecture/compression-pipeline.md)
