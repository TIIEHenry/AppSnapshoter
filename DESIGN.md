# AppSnapshoter 设计哲学

> 本文档描述项目的核心设计理念和取舍原则，供开发者理解"为什么这样做"。

## 核心原则

### 1. 原生性能优先

快照/恢复是性能敏感操作，用户期望"秒级完成"。为此：
- TAR 打包用 C 实现（内嵌 GNU tar），非 Java 归档库
- ZSTD 压缩用 C 实现（内嵌 zstd-jni），非 Java 压缩流
- 文件遍历用 POSIX FTS API，非 Java `File.listFiles()`
- FIFO 管道流式传输，零中间文件

### 2. Root 操作安全隔离

Root 权限是双刃剑。设计上：
- UI 层**永远不直接接触** Root 内部逻辑
- Root 操作通过 AIDL 接口暴露，仅包含预定义方法，无任意命令执行
- 双 Root 服务分离：主服务（业务逻辑）和文件 I/O 服务（libsu-nio）
- Handler 模式隔离各领域（应用管理、权限、SSAID、文件系统）

### 3. 配置简单，存储透明

- MMKV 作为唯一配置持久化（快、简单、可靠）
- 快照文件平铺在文件系统上，用户可直接访问和同步
- 不使用数据库（SQLite/Room），快照元数据从文件系统直接读取
- 支持 Syncthing 透明同步，无需额外适配

## 设计取舍

### ViewBinding + DataBinding 而非 Compose

- 项目启动时 Compose 尚未成熟
- ViewBinding 零运行时开销，DataBinding 减少样板代码
- 不引入 Compose 避免增大 APK 体积和最低版本要求

### Kotlin 单例 ViewModel 而非 ViewModelProvider

`SnapshotViewModel` 在 `SnapshotApp.onCreate()` 直接实例化为顶层属性，而非通过 `ViewModelProvider`。
原因：它是全局状态管理器，生命周期等同于 Application，不需要 Activity 级别的生命周期管理。

### 混合 AIDL/纯接口

- 异步+回调场景（压缩进度）用 AIDL
- 同步查询场景（应用列表）用纯 Java 接口（进程内执行，避免 IPC 开销）

### 内嵌 GNU tar 和 zstd-jni

- 不依赖系统 `tar` 命令（不同设备版本不一致）
- 不使用 Java 归档/压缩库（性能差距数倍）
- 内嵌源码确保跨设备一致性和可控性

## 相关文档

- [系统架构总览](docs/architecture/overview.md)
- [压缩管线](docs/architecture/compression-pipeline.md)
- [Root 服务架构](docs/architecture/root-service.md)
- [文档系统](docs/README.md)
