---
title: "hiddenapi 模块"
type: module
status: active
updated: 2026-06-17
summary: "反射访问 Android 隐藏 API，11 个文件，@RefineAs 字节码精化"
---

# hiddenapi 模块

> 源码路径：`hiddenapi/src/main/`（java + kotlin）

## 概述

通过 Rikka Refine 的 `@RefineAs` 注解，在编译时进行字节码精化，访问 Android 隐藏 API。11 个源文件（10 Java + 1 Kotlin）。

## 关键类

### Android 应用框架
| 类 | 隐藏方法 |
|---|----------|
| `ActivityManagerHidden` | `getRunningAppProcesses()`、`forceStopPackageAsUser()` |
| `ActivityThread` | `systemMain()`、`getSystemContext()`（Root 服务获取系统 Context） |
| `AppOpsManagerHidden` | `permissionToOpCode()`、`setMode()` |
| `ContextImpl` | 隐藏 Context 实现 |

### 包管理
| 类 | 隐藏方法 |
|---|----------|
| `PackageManagerHidden` | `getInstalledPackagesAsUser()`、`getPackageInfoAsUser()`、权限 grant/revoke/flags |
| `UserInfo` | 隐藏 UserInfo 类 |

### 用户管理
| 类 | 隐藏方法 |
|---|------|
| `UserManagerHidden` | 多用户枚举和管理 |
| `UserHandleHidden` | UserHandle 隐藏方法 |

### WiFi（保留）
| 类 | 说明 |
|---|------|
| `WifiManagerHidden` | WiFi 管理隐藏 API |
| `WifiConfigurationHidden` | WiFi 配置隐藏 API |

### 工具
| 类 | 职责 |
|---|------|
| `Refine.kt` | `castTo<T>()` 扩展函数，安全类型转换 |

## 技术方案

所有类使用 `dev.rikka.tools.refine.RefineAs` 注解，方法体为 `throw RuntimeException("Stub!")` — 编译时由 Rikka Refine 插件替换为真实实现。

## 依赖关系

- 被 `app` 和 `provider` 模块引用
- 无模块依赖
