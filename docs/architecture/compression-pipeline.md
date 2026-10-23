---
title: "压缩管线架构"
type: architecture
status: active
updated: 2026-10-23
summary: "TAR+ZSTD 流式压缩；压缩使用 FIFO，恢复使用带错误通道的匿名管道"
---

# 压缩管线架构

## 概述

压缩管线将应用数据打包压缩为 `.tar.zst` 格式。TAR 与 ZSTD 的核心算法使用 JNI；压缩经 FIFO 流式传输，恢复经匿名管道传输，均不生成中间 TAR 文件。

## ZSTD 压缩流程（主路径）

```mermaid
graph LR
    A[应用数据目录] -->|createTarArchive| B[TAR stdout FIFO]
    B -->|FlowableStreamParallelCopier| C[ZstdOutputStream]
    C -->|ParcelFileDescriptor| D[.tar.zst 输出]
    E[stderr FIFO] -->|并行读取| F[诊断日志]
```

### 三阶段管线（`ZstdCompressor`）

**文件**：`provider/.../filesystem/compressors/zstd/ZstdCompressor.kt`

```
协程 1: TAR 打包
  └─ FileSystemImpl.createTarArchive()
       └─ TarJNI.callCli() (fork 子进程)
            └─ GNU tar → stdout FIFO

协程 2: stderr 读取
  └─ 从 stderr FIFO 读取 tar 诊断输出

协程 3: ZSTD 压缩
  └─ FlowableStreamParallelCopier 从 stdout FIFO 读取
       └─ ZstdOutputStream 压缩（可配置级别 1-19）
            └─ 输出到 ParcelFileDescriptor
```

### ZSTD 解压流程（`ZstdDecompressor`）

```
阶段 1: 打开压缩源文件 → ZstdInputStream → 匿名管道写端
阶段 2: extractTarFromFd() 经 AIDL 传递读端 → JNI dup2 到 stdin → GNU tar
```

恢复不再依赖 FIFO 两端阻塞 `open()` 的配对时序。源文件在启动消费端前打开；任一阶段失败都关闭管道并向上层传播失败，避免错误被吞掉后无限等待另一阶段。描述符由各进程按所有权关闭，tar 子进程结束后必须回收。目标目录的 root 清理失败时中止恢复，不能继续混合新旧文件。

### TAR 打包流程（`TarCompressor`）

```
1. 创建 stderr FIFO
2. 并发执行：
   - createTarArchive() → stdout（文件或 FIFO）
   - 读取 stderr FIFO
3. 报告完成（原始大小、压缩大小、MD5）
```

## 核心组件

### 文件遍历

**文件**：`io-nativefs/.../NativeFileSystem.kt` + `native-filesystem.cpp`

- POSIX `fts_open`/`fts_read`/`fts_close` 遍历目录
- `stat()` 查询 UID/GID
- 性能远超 Java 递归

### TAR 打包

**文件**：`io-tar/.../TarJNI.kt` + `jni.cpp`

- `fork()` 子进程执行 GNU tar
- `dup2()` 重定向 STDIN/STDOUT/STDERR
- 支持 FIFO 输入（管道接收数据流）
- 支持已有文件描述符输入，供 ZSTD 恢复的匿名管道使用
- `prctl(PR_SET_PDEATHSIG)` 防止孤儿进程

### ZSTD 压缩

**文件**：`io-zstd` 内嵌 zstd-jni

- `ZstdOutputStream` 流式压缩
- `ZstdInputStream` 流式解压
- 支持字典压缩（`ZstdDictCompress`）
- 压缩级别映射：用户级 1-9 → ZSTD 级 1-19（非线性）

### 高速流拷贝

**文件**：`provider/.../nota/io/StreamParallelCopier.java` + `FlowableStreamParallelCopier.kt`

- 读线程 + 写线程 + `ArrayBlockingQueue<DataItem>`
- 128KB 缓冲区
- 两线程均 `Thread.MAX_PRIORITY`
- `StateFlow<Progress>` 追踪字节数和速度（每秒更新）

## 压缩算法路由

**文件**：`provider/.../filesystem/FileCompressor.kt`

```
FileCompressor (IFileCompressor.Stub)
├─ compress() → 检测算法
│   ├─ .apk 文件 → TarCompressor（仅打包不压缩）
│   └─ 其他 → ZstdCompressor（TAR + ZSTD）
├─ compressMultiple() → 多源合并压缩
├─ decompress()
│   ├─ .zst → ZstdDecompressor
│   └─ .tar → TarDecompressor
└─ checkFileValid() → 大小 + MD5 校验
```

单源 TAR 打包区分源类型：目录使用 `-C <目录> .`；普通文件使用 `-C <父目录> -- <文件名>`。普通文件的目标替换与暂存目录由 app 恢复层管理。

## FIFO 管道使用

```kotlin
// 创建 FIFO
Os.mkfifo(path, 438 /* 0666 */)

// TAR 写入 stdout FIFO → ZSTD 从 stdout FIFO 读取
// TAR 写入 stderr FIFO → 并行读取诊断信息
```

- **零中间文件**：TAR 输出直接流入 ZSTD 输入
- **内存效率**：流式处理，内存占用与文件大小无关
- **内核级传输**：管道传输，用户态零拷贝

## 性能设计

| 设计 | 收益 |
|------|------|
| JNI 原生 TAR/ZSTD | 避免 Java 字节操作开销 |
| FIFO 流式管线 | 零中间文件、低内存占用 |
| 并行流拷贝（双线程） | 充分利用 I/O 带宽 |
| 128KB 缓冲区 | 平衡内存和吞吐 |
| `Thread.MAX_PRIORITY` | 减少线程调度延迟 |
| FTS 遍历 | 比 Java 递归快数倍 |

## 相关模块

| 模块 | 职责 |
|------|------|
| [`io-tar`](../modules/io-tar/INDEX.md) | TAR 归档（内嵌 GNU tar） |
| [`io-zstd`](../modules/io-zstd/INDEX.md) | ZSTD 压缩（内嵌 zstd-jni） |
| [`io-nativefs`](../modules/io-nativefs/INDEX.md) | 文件遍历（FTS + stat） |
| [`provider`](../modules/provider/INDEX.md) | 管线编排、压缩器实现 |
| [`api`](../modules/api/INDEX.md) | `IFileCompressor`、`ICompressCallback` |
