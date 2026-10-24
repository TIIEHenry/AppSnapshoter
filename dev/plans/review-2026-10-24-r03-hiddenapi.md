---
title: "r03 hiddenapi 审查发现"
type: guide
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "通读 hiddenapi/src 全部源码，未发现正确性缺陷。"
---

# r03 hiddenapi 审查发现

已通读 `hiddenapi/src` 下 11 个源文件（`.kt` / `.java`；无 `.aidl`、`.cpp`、`.h`）。`Refine.castTo` 与 `UserInfo` 的 Parcel 读写是仅有的可执行逻辑，读写字段一致；其余类是 Rikka Refine 桩或空壳，方法体只抛 `RuntimeException("Stub!")`。未发现数据丢失、越权、用户可见行为错误或边界导致的错误结果。

0 条。
