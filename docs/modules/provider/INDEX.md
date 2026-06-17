---
title: "provider 模块"
type: module
status: active
updated: 2026-06-17
summary: "Root 服务实现 — 双 Root 服务架构，34 个源文件，12 个包"
---

# provider 模块

> 源码路径：`provider/src/main/java/` 和 `provider/src/main/aidl/`

## 概述

Root 服务实现层，运行在 Root 进程中。采用**双 Root 服务架构**：主 AIDL 服务 + FileSystemManager 服务。34 个源文件（30 Kotlin + 2 Java + 2 AIDL），12 个包。

## 双 Root 服务架构

```
App 进程                              Root 进程
─────────                            ─────────
ProvidersImpl ────── AIDL ─────────→ SnapshotRootService.Impl
  ├─ AppManagerImpl                    ├─ AppManagementHandler
  │    (IPackageManager,                 ├─ PackageManagerDelegate
  │     IPermissionManager)              ├─ ProcessManager
  │                                      └─ AppLauncher
  ├─ FileSystemProviderImpl            ├─ PermissionManagementHandler
  │    └─ FileSystemImpl              ├─ SsaidManagementHandler
  │         (IFileSystem)             └─ FileSystemHandler
  │
  └─ FileSystemProviderImpl ── AIDL ──→ FileSystemManagerRootService
       (libsu-nio FileSystemManager)       (FileSystemManager binder)
```

## 包结构与关键类

### `service` — Root 服务入口
| 类 | 职责 |
|---|------|
| `SnapshotRootService` | 主 Root 服务，继承 `RootService`，`onBind()` 创建 `Impl` 并初始化 4 个 Handler |
| `SnapShotRootServiceClient` | 客户端 Binder，单例 per 包，管理服务连接/断开 |
| `StatFsResult` | 文件系统统计数据类（Parcelable） |

**AIDL 接口 `ISnapShotRootService`** — 30+ 方法，覆盖：
- 连接测试：`testConnection()`
- 应用管理：`getInstalledAppInfos`、`installApk`、`uninstallApk`、`forceStopPackage` 等
- 权限管理：`grantRuntimePermission`、`revokeRuntimePermission`、`setOpsMode` 等
- SSAID 管理：`getPackageSsaidAsUser`、`setPackageSsaidAsUser`
- 文件系统：`readStatFs`、`callTarCli`、`calculateTreeSize`、`extractTar` 等

### `service.handler` — 4 个领域 Handler（Root 进程）

| 类 | 职责 |
|---|------|
| `AppManagementHandler` | 门面，委托给 3 个子 Handler |
| `PackageManagerDelegate` | 包枚举、信息查询、安装/卸载（`getInstalledPackagesAsUser()`、`PmShell.install()`） |
| `ProcessManager` | 进程管理（`am force-stop`、`pm clear`、`pm suspend`、`isRunning`） |
| `AppLauncher` | 应用启动（`am moveTaskToFront` 或 `am start --user`） |
| `PermissionManagementHandler` | 权限操作（`PackageManagerHidden` + `AppOpsManagerHidden` 反射） |
| `SsaidManagementHandler` | SSAID 读写（直接操作 `settings_ssaid.xml`，API 26/31 双实现） |
| `FileSystemHandler` | 文件系统操作（`calculateTreeSize`、`callTarCli`、`chown`、`md5`、`extractTar`） |

### `appmanager` — 客户端应用管理（App 进程）
| 类 | 职责 |
|---|------|
| `ProvidersImpl` | 实现 `Providers` 接口，服务入口，懒创建 `AppManagerImpl` 和 `FileSystemProviderImpl` |
| `AppManagerImpl` | 同时实现 `IAppManager`、`IPackageManager`、`IPermissionManager`，`runBlocking` 桥接协程 |

### `filesystem` — 文件系统层
| 类 | 职责 |
|---|------|
| `FileSystemProviderImpl` | 绑定 `FileSystemManagerRootService`，提供 `FileSystemImpl` |
| `FileSystemImpl` | 混合文件系统：libsu-nio `FileSystemManager`（普通操作）+ AIDL Binder（特权操作）。`createTarArchive()` 构建 `tar -cpf` 命令 |
| `FileCompressor` | 实现 `IFileCompressor.Stub()`，路由到 ZSTD/TAR 压缩器 |
| `IAlgorithmCompressor` | 压缩算法接口 |
| `MD5Utils` | MD5 计算 |

### `filesystem.compressors` — 压缩算法实现
| 类 | 职责 |
|---|------|
| `tar/TarCompressor` | TAR 打包：创建 stderr FIFO → 并发执行 `createTarArchive()` + 读 stderr |
| `tar/TarDecompressor` | TAR 解压：`fileSystem.extractTar()` |
| `zstd/ZstdCompressor` | ZSTD 三阶段管线：TAR 打包 → stderr 读取 → ZstdOutputStream 压缩。使用 `FlowableStreamParallelCopier` 高速流拷贝 |
| `zstd/ZstdDecompressor` | ZSTD 两阶段管线：ZstdInputStream 解压 → FIFO → `extractTar()` |

### `filesystem.root.fsm` — 第二 Root 服务
| 类 | 职责 |
|---|------|
| `FileSystemManagerRootService` | 独立 Root 服务，暴露 libsu-nio `FileSystemManager` binder |

### `root` — Shell 命令工具
| 类 | 职责 |
|---|------|
| `PmShell` | `pm` 命令封装（install 版本适配、session 安装、uninstall） |
| `SELinuxShell` | SELinux 操作（`chown`、`chcon`、`getContext`） |
| `ShellResult` | Shell 执行结果数据类 |

### `appmanager.util` — 工具类
| 类 | 职责 |
|---|------|
| `LogHelper` | 日志封装（前缀 `AppSnapshoter_`） |
| `PathHelper` | 标准 Android 应用目录路径 |
| `NotificationHelper` | 通知渠道管理 |
| `ParcelableHelper` | Parcel 序列化扩展 |

### 第三方内嵌（`nota` 命名空间）
| 类 | 职责 |
|---|------|
| `nota.io.StreamParallelCopier` | 高速并行流拷贝（读/写线程 + ArrayBlockingQueue，128KB 缓冲） |
| `nota.io.FlowableStreamParallelCopier` | 添加 `StateFlow<Progress>` 进度追踪 |
| `nota.lang.reflect.ReflectionCache` | 反射缓存（Class/Method/Field 查找） |

## 统计

| 指标 | 数量 |
|------|------|
| 源文件 | 34（30 Kotlin + 2 Java + 2 AIDL） |
| 包 | 12 |
| Root 服务 | 2（SnapshotRootService + FileSystemManagerRootService） |
| Handler | 4 领域 7 个类 |
| 压缩器 | 4（TarCompressor + TarDecompressor + ZstdCompressor + ZstdDecompressor） |

## 相关系统

| 系统 | 关系 |
|------|------|
| [快照系统](../../systems/snapshot/INDEX.md) | 核心流程编排 |
| [压缩系统](../../systems/compression/INDEX.md) | 压缩管线执行 |
| [Root 服务架构](../../architecture/root-service.md) | 双 Root 服务设计 |
| [压缩管线](../../architecture/compression-pipeline.md) | ZSTD/TAR 管线细节 |
