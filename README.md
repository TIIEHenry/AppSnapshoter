# AppSnapshoter

中文 | [English](README_EN.md)

一款基于 Root 权限的 Android 应用快照工具，可将应用的 APK、数据目录、OBB、媒体文件等快速打包为 **压缩快照**，用于备份与恢复，同时支持Syncthing同步。

## Star History

<a href="https://www.star-history.com/?repos=TIIEHenry%2FAppSnapshoter&type=date&legend=top-left">

 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=TIIEHenry/AppSnapshoter&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=TIIEHenry/AppSnapshoter&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=TIIEHenry/AppSnapshoter&type=date&legend=top-left" />
 </picture>
</a>

## 功能特性

- **一键存档 / 一键恢复**: 长按应用即可快速创建快照，选择存档即可一键恢复，操作极简
- **极速快照**: 基于原生 JNI 实现 TAR 打包 + ZSTD 压缩，制作应用快照速度极快，远超纯 Java 方案
- **APK 智能去重**: 多个应用的 APK 不重复压缩，节省存储空间和快照时间
- **多存档管理**: 每个应用可保留多个历史存档，随时回溯到任意版本
- **自定义压缩项**: 除默认目录外，可自由添加额外的压缩目录，灵活满足不同需求
- **流式管道**: 通过 FIFO 管道和 `ParcelFileDescriptor` 实现流式压缩，无需生成中间文件，节省存储空间
- **Root 服务架构**: 基于 AIDL + libsu 的 Root 服务 IPC，UI 层不直接接触 Root 内核逻辑，安全可靠
- **分组管理**: 支持将应用分组，按组批量操作快照和恢复
- **纯原生 UI**: ViewBinding + DataBinding 构建，无 Compose 依赖，体积轻量

## 截图

| 主页 | 快照中 | 存档列表 |
|:---:|:---:|:---:|
| ![主页](docs/screenshots/home.png) | ![快照中](docs/screenshots/snapshotting.png) | ![存档列表](docs/screenshots/archives.png) |

| 应用配置 | 分组设置 |
|:---:|:---:|
| ![应用配置](docs/screenshots/app-config.png) | ![分组设置](docs/screenshots/group-config.png) |

## 使用说明

### 前置条件

- 已获取 Root 权限的 Android 设备
- 最低支持 Android 9 (API 28)

### 基本流程

1. **授予权限**: 首次启动会请求 Root 权限，请允许
2. **创建分组**: 进入分组设置，创建至少一个分组（用于归类应用和存档）
3. **添加应用**: 在主页将需要快照的应用添加到对应分组中
4. **配置快照**（可选）: 点击应用进入配置页，选择需要包含的目录（data、obb、media）及自定义目录，决定是否包含分包 APK
5. **创建快照**: 长按应用即可快速制作快照，进度会实时显示当前阶段（预处理 → 打包中）
6. **查看存档**: 切换到「存档」标签页，查看已完成的快照文件，包含时间戳、文件名和大小。每个应用可保留多个存档
7. **一键恢复**: 长按应用选择目标存档，即可一键还原应用数据

### 分组管理

点击右下角设置图标进入分组设置，可创建分组并将应用归类，方便按组批量执行快照或恢复操作。

### Syncthing 跨设备同步

配合 [Syncthing-Android](https://github.com/researchxxl/syncthing-android) 可实现多台设备之间的快照数据和配置同步。

**原理**: AppSnapshoter 的所有数据存储在以下位置：

| 内容 | 路径 |
|------|------|
| 全局配置 / 应用配置 / 快照存档（默认） | `/storage/emulated/0/Android/snapshot/` |
| 每个分组的存档目录 | 分组设置中可自定义 `rootPath` |

**操作步骤**:

1. 在两台设备上安装 [Syncthing-Android](https://github.com/researchxxl/syncthing-android)
2. 在 Syncthing 中添加共享文件夹，路径设为 AppSnapshoter 的全局根目录 `/storage/emulated/0/Android/snapshot/`
3. 如果某些分组使用了自定义 `rootPath`，也需要将对应目录添加为共享文件夹
4. 等待同步完成后，另一台设备即可看到相同的配置和快照存档，直接执行恢复即可

**注意**: MMKV 内部存储（`{filesDir}/mmkv/`）位于应用私有目录，不在 Syncthing 同步范围内。分组列表和排序信息存储在 MMKV 中，首次在新设备上需要手动重建分组，之后的配置变更会通过 JSON 文件同步。

## 致谢

本项目参考了 [Android-DataBackup](https://github.com/XayahSuSuSu/Android-DataBackup.git) 的设计思路。

感谢本人：我的第一款稍微结合了一点VibeCoding的项目，大部分还是纯手写，可能不太优雅，但是古法编程的应用更香。

## 捐助

如果您觉得这个项目对您有帮助，欢迎捐赠支持开发。您的捐赠将全部用于购买 Claude API Token，以持续完善和优化应用功能。

| 微信支付 | 支付宝 |
|:---:|:---:|
| ![微信支付](docs/screenshots/weixin.png) | ![支付宝](docs/screenshots/alipay.jpg) |

## 许可证

本项目采用 [GNU General Public License v3.0](LICENSE) 许可证。
