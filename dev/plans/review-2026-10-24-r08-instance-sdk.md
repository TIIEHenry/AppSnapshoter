---
title: "r08 instance-sdk 审查发现"
type: guide
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "通读 instance-sdk/src 源码，仅有 IInstanceControl 接口声明，正确性发现 0 条。"
---

# r08 instance-sdk 审查发现

已通读 `instance-sdk/src` 下全部 `.kt`、`.java`、`.aidl`。该前缀只有 `main/aidl/tiiehenry/android/snapshot/instance/IInstanceControl.aidl`：声明 `String call(String requestJson)`，无实现、无状态、无数据路径。正确性发现 0 条。
