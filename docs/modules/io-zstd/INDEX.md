---
title: "io-zstd 模块"
type: module
status: active
updated: 2026-06-17
summary: "JNI ZSTD 压缩，内嵌 zstd-jni 库，流式压缩/解压"
---

# io-zstd 模块

> 源码路径：`io-zstd/src/main/kotlin/` 和 `io-zstd/src/main/jni/`

## 概述

内嵌 zstd-jni 库，提供 ZSTD 压缩/解压的 Java API 和 JNI 绑定。

## 源文件结构

| 路径 | 内容 |
|------|------|
| `ZstdVersion.kt` | Kotlin 版本元数据 |
| `zstd-jni/src/main/java/com/github/luben/zstd/` | Java API（20+ 类） |
| `zstd-jni/src/main/native/` | C JNI 绑定 + ZSTD 库源码 |

## Java API 关键类

| 类 | 职责 |
|---|------|
| `Zstd` | 主压缩/解压 API |
| `ZstdOutputStream` / `ZstdOutputStreamNoFinalizer` | 流式压缩 |
| `ZstdInputStream` / `ZstdInputStreamNoFinalizer` | 流式解压 |
| `ZstdCompressCtx` / `ZstdDecompressCtx` | 压缩/解压上下文 |
| `ZstdDirectBuffer*` (6个) | DirectByteBuffer 压缩/解压 |
| `ZstdDict*` (3个) | 字典压缩（Compress、Decompress、Trainer） |
| `BufferPool` / `RecyclingBufferPool` | 缓冲池管理 |

## Native 层

| 文件 | 职责 |
|------|------|
| `jni_zstd.c` | 核心 ZSTD JNI 绑定 |
| `jni_directbuffercompress_zstd.c` | Direct buffer 压缩 |
| `jni_directbufferdecompress_zstd.c` | Direct buffer 解压 |
| `jni_inputstream_zstd.c` / `jni_outputstream_zstd.c` | 流式 JNI |
| `jni_fast_zstd.c` | 快速压缩 JNI |
| `jni_zdict.c` | 字典 JNI |
| `common/` | ZSTD 核心实现 (~21,000行) |
| `legacy/` | 旧版格式支持 (v04-v07) |
| `decompress/` | 解压专用实现 |

## 构建

- NDK 25.2.9519653 + CMake 3.22.1
- 内置完整 zstd C 源码

## 相关系统

- [压缩系统](../../systems/compression/INDEX.md) — ZSTD 压缩是压缩管线第二阶段
- [压缩管线架构](../../architecture/compression-pipeline.md)
