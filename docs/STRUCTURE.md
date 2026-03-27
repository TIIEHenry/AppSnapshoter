# 软件架构

## 项目概述

本项目是一个 Android 数据备份应用，支持 Root 设备的应用数据备份与恢复。

## 模块架构

```
AppSnapshoter/
├── app/                    # 主应用程序（ViewBinding + DataBinding）
├── api/                    # API 接口定义（AIDL）
├── hiddenapi/              # Android 隐藏 API 访问
├── systemapi/              # 系统 API 封装
├── io-nativefs/            # 本地文件系统 native 实现
├── io-tar/                 # TAR 归档格式支持
├── io-zstd/                # ZSTD 压缩支持
├── provider/               # ContentProvider 实现
│   ├── appmanager/         # 应用管理器
│   └── filesystem/          # 文件系统操作
└── docs/                   # 文档
```

## 核心模块说明

### 1. app (主应用模块)
- **UI 框架**: ViewBinding + DataBinding（不使用 Compose）
- **语言**: Kotlin
- **功能**: 应用列表展示、备份/恢复操作、设置界面

### 2. api (API 接口模块)
- **用途**: 定义 AIDL 接口，供进程间通信使用
- **命名空间**: `tiiehenry.android.snapshot.api`

### 3. hiddenapi (隐藏 API 模块)
- **用途**: 访问 Android 隐藏 API（通过反射绕过 API 检查）
- **功能**: 获取应用数据路径、访问系统层功能

### 4. systemapi (系统 API 模块)
- **用途**: 封装系统级操作接口
- **功能**: 与系统服务交互

### 5. io-nativefs (Native 文件系统)
- **技术**: JNI + CMake（C++）
- **用途**: 高性能本地文件系统操作

### 6. io-tar (TAR 归档模块)
- **技术**: JNI + CMake
- **用途**: TAR 格式文件的读写操作

### 7. io-zstd (ZSTD 压缩模块)
- **技术**: JNI + CMake + zstd-jni
- **用途**: ZSTD 压缩算法的 Java/JNI 封装

### 8. provider (ContentProvider 模块)
- **appmanager**: 应用管理器，提供应用数据查询接口
- **filesystem**: 文件系统操作 Provider

## 技术栈

| 类别 | 技术 |
|------|------|
| **UI** | Material3, ViewBinding |
| **架构** | MVVM, Clean Architecture |
| **DI** | Hilt, Koin |
| **存储** | Room, MMKV, DataStore |
| **网络** | OkHttp, Retrofit |
| **异步** | Kotlin Coroutines, Flow |
| **Native** | JNI, CMake, libsu |
| **压缩** | ZSTD, TAR |
| **Root** | libsu (Magisk/KernelSU/APatch) |

## 依赖关系

```
app
├── api
├── hiddenapi
├── systemapi
├── provider
│   ├── api
│   ├── hiddenapi
│   └── systemapi
└── io-nativefs / io-tar / io-zstd
    └── JNI (C++)
```