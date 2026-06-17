---
title: "压缩系统"
type: system
status: active
updated: 2026-06-17
summary: "TAR+ZSTD 压缩管线，双线程并行流拷贝，三阶段协程管线"
---

# 压缩系统

## 概述

压缩系统负责将应用数据打包并压缩为高效的 `.tar.zst` 格式。全链路基于 JNI 原生实现，通过 FIFO 管道实现流式处理。

## 技术方案

| 阶段 | 模块 | 实现类 | 技术 |
|------|------|--------|------|
| 文件遍历 | io-nativefs | `NativeFileSystem` | FTS + stat (C++) |
| TAR 打包 | io-tar | `TarJNI` → fork + GNU tar | fork() + dup2() (C++) |
| ZSTD 压缩 | io-zstd | `ZstdOutputStream` | zstd-jni (C) |
| 流式传输 | provider | `FlowableStreamParallelCopier` | FIFO + 双线程 |
| 管线编排 | provider | `FileCompressor` → `ZstdCompressor` | 协程三阶段 |

## 压缩算法

### ZSTD（默认）

用户级压缩级别 1-9 映射到 ZSTD 级别 1-19（非线性映射，`ZstdCompressor.mapToZstdLevel()`）。

### TAR（APK 专用）

`.apk` 文件路由到 `TarCompressor`，仅打包不压缩（APK 本身已压缩，再压缩无收益）。

## 解压流程

| 格式 | 实现类 | 流程 |
|------|--------|------|
| `.tar.zst` | `ZstdDecompressor` | ZstdInputStream → FIFO → extractTar() |
| `.tar` | `TarDecompressor` | 直接 extractTar() |

## 校验

`FileCompressor.checkFileValid()` 通过文件大小 + MD5 校验完整性。

## 涉及模块

| 模块 | 说明 |
|------|------|
| [`io-tar`](../../modules/io-tar/INDEX.md) | 内嵌 GNU tar，fork 进程隔离 |
| [`io-zstd`](../../modules/io-zstd/INDEX.md) | 内嵌 zstd-jni，流式 API |
| [`io-nativefs`](../../modules/io-nativefs/INDEX.md) | FTS 遍历、stat 查询 |
| [`provider`](../../modules/provider/INDEX.md) | 管线编排、压缩器实现 |
| [`api`](../../modules/api/INDEX.md) | `IFileCompressor`、`ICompressCallback` |

## 相关文档

- [压缩管线架构](../../architecture/compression-pipeline.md) — 详细流程图和性能设计
