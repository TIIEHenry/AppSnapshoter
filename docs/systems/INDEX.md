---
title: "系统文档索引"
type: index
status: active
updated: 2026-08-21
summary: "跨模块功能系统文档导航"
---

# 系统文档

系统文档描述跨模块协作的功能域，按功能而非模块组织。

| 系统 | 说明 | 关键模块 |
|------|------|----------|
| [快照系统](snapshot/INDEX.md) | 备份/恢复核心流程 | provider, io-* |
| [分组集](snapshot/GROUP_SET.md) | 父目录组织多个分组；`archiveList` 连续成块 | app/launch/ |
| [分组集折展性能](snapshot/group-set-expand-perf.md) | 折展/一键折叠只内存再投影（Phase A 已落地） | app/launch/ |
| [存档 Tab 搜索](snapshot/ARCHIVE_SEARCH.md) | 主页按集名/组名/应用过滤（已落地） | app/launch/ |
| [压缩系统](compression/INDEX.md) | TAR + ZSTD 压缩管线 | io-tar, io-zstd, io-nativefs |
| [时间线系统](timeline/INDEX.md) | 按时间浏览快照、批量操作 | app/timeline/ |
| [配置系统](config/INDEX.md) | MMKV 持久化、分组配置 | app/config/ |
