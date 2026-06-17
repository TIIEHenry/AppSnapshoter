---
title: "io-tar 模块"
type: module
status: active
updated: 2026-06-17
summary: "JNI TAR 归档，内嵌 GNU tar 实现，fork+dup2 进程隔离"
---

# io-tar 模块

> 源码路径：`io-tar/src/main/kotlin/` 和 `io-tar/src/main/jni/`

## 概述

JNI 封装 GNU tar，通过 `fork()` 创建子进程执行 tar 命令，支持 FIFO 管道重定向。

## 源文件

| 文件 | 语言 | 职责 |
|------|------|------|
| `TarJNI.kt` | Kotlin | JNI 接口 |
| `jni.cpp` | C++ (141行) | JNI 实现：fork + dup2 + 调用 GNU tar main() |
| `tar/gnu/` | C (~64,000行) | 内嵌 GNU tar 完整实现 |
| `tar-gnu-embedded/` | C | 内嵌系统/POSIX 头文件（交叉编译用） |
| `tar-config/config.h` | C | 构建配置 |

## API

```kotlin
object TarJNI {
    fun callCli(pipeFile: String?, stdOut: String, stdErr: String, argv: Array<String>): Int
}
```

## 实现细节

1. `fork()` 创建子进程
2. 子进程通过 `dup2()` 重定向 STDIN/STDOUT/STDERR 到文件描述符
3. STDIN 支持 FIFO（命名管道）输入
4. `prctl(PR_SET_PDEATHSIG)` 设置父进程死亡信号
5. 调用 GNU tar `main()` 执行归档操作
6. 父进程通过 `waitpid()` 捕获退出状态

## 构建

- NDK 25.2.9519653 + CMake 3.22.1
- CMakeLists.txt 位于 `src/main/jni/`

## 相关系统

- [压缩系统](../../systems/compression/INDEX.md) — TAR 打包是压缩管线第一阶段
- [压缩管线架构](../../architecture/compression-pipeline.md)
