---
title: "io-nativefs 模块"
type: module
status: active
updated: 2026-06-17
summary: "JNI 原生文件系统操作，2 个文件，FTS 遍历 + stat 查询"
---

# io-nativefs 模块

> 源码路径：`io-nativefs/src/main/kotlin/` 和 `io-nativefs/src/main/jni/`

## 概述

通过 JNI + C++ 实现高性能文件系统操作。仅 2 个源文件（1 Kotlin + 1 C++）。

## 源文件

| 文件 | 语言 | 职责 |
|------|------|------|
| `NativeFileSystem.kt` | Kotlin | JNI 接口声明 |
| `native-filesystem.cpp` | C++ (76行) | JNI 实现 |

## API

```kotlin
object NativeFileSystem {
    fun calculateTreeSize(path: String): Long  // 递归目录大小
    fun getUid(path: String): Int              // 文件/目录 UID
    fun getGid(path: String): Int              // 文件/目录 GID
}
```

## 实现细节

- **目录遍历**：使用 POSIX `fts_open`/`fts_read`/`fts_close`（File Tree Walk），性能远超 Java `File.listFiles()` 递归
- **UID/GID 查询**：使用 `stat()` 系统调用
- **构建**：NDK 25.2.9519653 + CMake 3.22.1

## 依赖关系

- 被 `provider` 模块引用（`FileSystemHandler`、`PackageManagerDelegate` 用于计算目录大小）
