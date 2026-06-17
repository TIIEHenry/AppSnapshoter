---
title: "快速开始"
type: guide
status: active
updated: 2026-06-17
summary: "安装和首次使用 AppSnapshoter 的完整流程"
---

# 快速开始

## 前置条件

- 已获取 Root 权限的 Android 设备（Magisk/KernelSU/APatch）
- Android 9+ (API 28)

## 安装

1. 从 [GitHub Releases](https://github.com/TIIEHenry/AppSnapshoter/releases) 下载最新 APK
2. 安装并授予 Root 权限

## 首次使用

### 1. 授予 Root 权限

首次启动会请求 Root 权限，请允许。

### 2. 创建分组

进入分组设置（右下角齿轮图标），创建至少一个分组用于归类应用。

### 3. 添加应用

在主页（存档 Tab）将需要快照的应用添加到对应分组中。

### 4. 配置快照（可选）

点击应用进入配置页：
- 选择包含的目录（data、obb、media）
- 添加自定义压缩目录
- 决定是否包含分包 APK

### 5. 创建快照

长按应用即可快速制作快照，进度实时显示（预处理 → 打包中）。

### 6. 查看存档

切换到「存档」标签页，查看已完成的快照文件（时间戳、文件名、大小）。

### 7. 时间线浏览

切换到「时间线」标签页，按时间区域筛选快照，支持多选批量操作。

### 8. 恢复快照

长按应用选择目标存档，一键还原应用数据。

## 构建开发版本

```bash
./gradlew assembleDebug          # 编译 Debug APK
./gradlew :app:installDebug      # 安装到连接的设备
```

详见 [构建指南](build.md)。
