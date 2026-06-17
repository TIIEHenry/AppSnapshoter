---
title: "安全策略"
type: architecture
status: active
updated: 2026-06-17
summary: "Root 权限管理、IPC 安全、数据安全和威胁模型"
---

# 安全策略

## 威胁模型

| 威胁 | 风险等级 | 缓解措施 |
|------|---------|----------|
| Root 权限滥用 | 高 | AIDL 接口仅暴露预定义操作，无任意命令执行 |
| IPC 数据泄露 | 中 | Root 服务运行在独立进程，`ParcelFileDescriptor` 传递文件数据 |
| 快照文件被篡改 | 低 | 存储在外部存储，设备 Root 后文件系统本身已不安全 |
| 中途 Root 权限被撤销 | 中 | 服务连接断开，操作中止，下次使用需重新授权 |
| 恶意应用伪造请求 | 低 | AIDL 接口无暴露的 ContentProvider 或 Broadcast |

## Root 权限管理

### 授权流程

1. `SnapshotApp.onCreate()` 检查 `Shell.getShell().isRoot`
2. 首次启动通过 libsu 请求 Root 权限
3. 拒绝后所有 Root 功能不可用，UI 提示用户授权
4. 支持 Magisk、KernelSU、APatch 三种 Root 方案

### Root 方案差异

| 方案 | 权限管理 | 特点 |
|------|---------|------|
| Magisk | Magisk Manager 授权弹窗 | 最广泛，systemless 模式 |
| KernelSU | KernelSU Manager 授权 | 内核级，更深层控制 |
| APatch | APatch Manager 授权 | 内核补丁方案 |

三种方案对 AppSnapshoter 透明 — libsu 统一处理 Shell 获取。

### Shell 生命周期

- libsu 管理 Root Shell 连接，`RootService.bind()`/`unbind()` 控制生命周期
- Shell 超时由 libsu 内部管理
- `ProcessManager` 中的 shell 命令有显式超时：`forceStopPackage` 30s、`clearAppData` 60s
- Root 服务崩溃时 libsu 自动重连，客户端 `runBlocking` 抛出异常由 `safeCall` 捕获

### 中途撤销 Root

- Root 权限被用户或管理器撤销后，`SnapShotRootServiceClient.client` 变为 null
- 后续 Root 操作通过 `safeCall`/`safeRun` 捕获异常，UI 显示错误
- 已打开的存档文件句柄不受影响（`ParcelFileDescriptor` 已传递到 App 进程）

## IPC 安全

### 进程隔离

```
App 进程 (UID: app)          Root 进程 (UID: root)
──────────────              ──────────────
ProvidersImpl ←── AIDL ──→ SnapshotRootService.Impl
```

- Root 服务运行在独立 root 进程，UI 层不直接接触 Root 操作
- Root 进程崩溃不影响 UI 进程
- AIDL 接口仅暴露预定义方法，**无任意命令执行能力**

### ParcelFileDescriptor 安全

- 用于压缩/解压的文件数据传输，避免跨进程传递文件路径
- 调用方打开 FD 后传递给 Root 服务，Root 服务只读/写 FD 不选择文件
- 避免路径注入攻击（Root 服务不接受文件路径参数来打开文件）

### Handler 领域隔离

Root 服务内 4 个 Handler 各自封装一个领域（应用管理、权限、SSAID、文件系统），互不越界：
- `AppManagementHandler` 不能操作文件系统
- `FileSystemHandler` 不能修改权限
- `SsaidManagementHandler` 独立 HandlerThread，不共享状态

## 数据安全

### 快照存储

- 快照文件存储在 `/storage/emulated/0/Android/snapshot/`
- **不加密存储** — 设备 Root 后加密无实际意义，且影响 Syncthing 同步
- `.nomedia` 文件防止图库扫描快照目录

### 配置存储

- MMKV 配置存储在应用私有目录 (`{filesDir}/mmkv/`)
- 其他应用无法访问（标准 Android 沙箱）
- Root 进程通过 AIDL 读取配置，不直接访问 MMKV 文件

### 应用签名

- 未实现签名验证或防篡改检测
- 项目开源（GPLv3），完整性可通过源码审计保证

## 权限模型

| 权限 | 用途 | 获取方式 |
|------|------|---------|
| Root (su) | 读取/写入其他应用数据目录 | libsu 请求 |
| 存储权限 | 访问快照文件输出目录 | Android 运行时权限 |
