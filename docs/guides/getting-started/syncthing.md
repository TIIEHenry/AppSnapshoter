---
title: "Syncthing 跨设备同步"
type: guide
status: active
updated: 2026-06-17
summary: "使用 Syncthing 配置跨设备快照数据和配置同步"
---

# Syncthing 跨设备同步

配合 [Syncthing-Android](https://github.com/researchxxl/syncthing-android) 可实现多台设备之间的快照数据和配置同步。

## 数据存储位置

| 内容 | 路径 |
|------|------|
| 全局配置 / 应用配置 / 快照存档（默认） | `/storage/emulated/0/Android/snapshot/` |
| 每个分组的存档目录 | 分组设置中可自定义 `rootPath` |
| MMKV 内部存储 | `{filesDir}/mmkv/`（应用私有目录） |

## 操作步骤

1. 在两台设备上安装 Syncthing-Android
2. 在 Syncthing 中添加共享文件夹，路径设为 `/storage/emulated/0/Android/snapshot/`
3. 如果某些分组使用了自定义 `rootPath`，也需要将对应目录添加为共享文件夹
4. 等待同步完成后，另一台设备即可看到相同的配置和快照存档

## 注意事项

- MMKV 数据在应用私有目录，**不在** Syncthing 同步范围内
- 分组列表和排序信息存储在 MMKV 中，首次在新设备上需要手动重建分组
- 之后的配置变更会通过 JSON 文件同步
