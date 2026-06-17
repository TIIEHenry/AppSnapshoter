---
title: "故障排除"
type: guide
status: active
updated: 2026-06-17
summary: "常见问题诊断与解决方案"
---

# 故障排除

## Root 权限问题

**症状**：启动后提示无 Root 权限

- 确认设备已安装 Magisk/KernelSU/APatch
- 在 Root 管理器中授予 AppSnapshoter Root 权限
- 检查 Root 管理器是否禁用了该应用

## 快照失败

**症状**：快照过程中报错

- 检查存储空间是否充足
- 确认目标应用未处于运行状态（部分文件可能被锁定）
- 查看 logcat 日志定位具体错误：`adb logcat -s AppSnapshoter`

## 恢复失败

**症状**：恢复后应用数据不正确

- 确认恢复的目标应用版本与快照时一致或兼容
- 检查目标应用是否正在运行，需先强制停止
- 确认存档文件未损坏

## 构建问题

**症状**：编译失败

- 确认 JDK 21 已安装：`java -version`
- 确认 NDK 版本正确：Android Studio → SDK Manager → NDK 25.2.9519653
- 执行 `./gradlew clean` 后重新编译
