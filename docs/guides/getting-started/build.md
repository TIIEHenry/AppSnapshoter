---
title: "构建指南"
type: guide
status: active
updated: 2026-06-18
summary: "编译、调试和安装 AppSnapshoter 开发版本"
---

# 构建指南

## 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 21 |
| Android SDK | compileSdk 36 |
| NDK | 25.2.9519653 |
| CMake | 3.22.1 |
| Gradle | 9.2.1 (wrapper) |

## 常用命令

```bash
./gradlew assembleDebug          # 编译 Debug APK
./gradlew assembleRelease        # 编译 Release APK
./gradlew :app:installDebug      # 安装 Debug 到连接设备
./gradlew test                   # 运行单元测试
./gradlew connectedAndroidTest   # 运行仪器测试（需设备）
./gradlew :provider:build        # 编译单个模块
```

## 项目结构

```
app/           → api, hiddenapi, provider
provider/      → api, hiddenapi, systemapi, io-nativefs, io-tar, io-zstd
```

## 发布

正式版本发布（CHANGELOG、打 tag、GitHub Actions）见 [发布指南](release.md)。

## 调试

- Debug APK 可直接安装到 Root 设备
- logcat 过滤标签 `AppSnapshoter` 查看日志
- Root 服务日志在 root 进程中，需 `adb logcat` 全局查看
