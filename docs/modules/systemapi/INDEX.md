---
title: "systemapi 模块"
type: module
status: active
updated: 2026-06-17
summary: "Android 框架内部类桩，25 个文件，XML/Settings/系统属性实现"
---

# systemapi 模块

> 源码路径：`systemapi/src/main/java/`

## 概述

提供 Android Framework 内部类的完整实现桩（非空壳），使 `provider` 和 `hiddenapi` 模块在编译时可以引用这些类。25 个源文件。

## 关键类

### Android 核心
| 类 | 职责 |
|---|------|
| `android.os.SystemProperties` | 系统属性完整实现（native 方法、变更回调、摘要生成） |
| `android.util.TypedXmlSerializer` | 类型化 XML 序列化接口 |
| `android.util.TypedXmlPullParser` | 类型化 XML 解析接口 |
| `android.util.XmlApi30` | API 30+ XML 实现 |

### 内部工具（`com.android.internal.util`）
| 类 | 职责 |
|---|------|
| `BinaryXmlPullParser` / `BinaryXmlSerializer` | 二进制 XML 解析/序列化 |
| `FastDataInput` / `FastDataOutput` | 快速二进制 I/O |
| `FastXmlSerializer` | 快速 XML 序列化 |
| `HexDump`、`ModifiedUtf8` | 编码工具 |
| `XmlUtils`、`XmlPullParserWrapper`、`XmlSerializerWrapper` | XML 工具 |

### Settings Provider
| 类 | 职责 |
|---|------|
| `SettingsState` | 设置状态管理基类 |
| `SettingsStateApi26` | API 26+ 实现（SSAID 读写） |
| `SettingsStateApi31` | API 31+ 实现（SSAID 读写） |

### 其他
| 类 | 职责 |
|---|------|
| `KXmlParser` / `KXMLSerializer` | KXML 解析器 |
| `libcore.internal.StringPool` | 字符串池 |
| `libcore.io.IoUtils` | I/O 工具 |
| `libcore.util.HexEncoding`、`XmlObjectFactory` | 编码和工厂 |
| `DisplayControl` | 显示控制接口 |
| `BuildConfigUtil.kt` | 反射访问 BuildConfig 字段 |

## 依赖关系

- 被 `provider` 模块引用（SsaidManagementHandler 使用 SettingsState，FileSystemHandler 使用 XML 工具）
- 被 `hiddenapi` 模块引用
- 无模块依赖
