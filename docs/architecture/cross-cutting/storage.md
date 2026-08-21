---
title: "存储策略"
type: architecture
status: active
updated: 2026-08-21
summary: "MMKV 配置存储、快照文件布局、group.json 格式和 Syncthing 同步设计"
---

# 存储策略

## 文件布局

```
/storage/emulated/0/Android/snapshot/
├── group1/                          # 分组存档目录（rootPath 可自定义）
│   ├── com.example.app/
│   │   ├── 2026-01-01_120000.tar.zst
│   │   ├── 2026-01-01_120000.meta  # 存档元数据（JSON）
│   │   └── 2026-01-15_090000.tar.zst
│   ├── com.other.app/
│   │   └── ...
│   └── group.json                   # 分组配置文件
├── group2/
│   └── ...
└── .nomedia                         # 防止图库扫描快照目录
```

每个分组的 `rootPath` 可在分组设置中自定义（通过 SAF 路径选择器）。

### 分组集

父目录组织多个分组，见 [分组集功能设计](../../systems/snapshot/GROUP_SET.md)。集目录含 `groupset.json`（`name` + basename `groupOrder`）；直接子目录仍是现有分组（各有 `group.json`）。

```
{setPath}/
├── groupset.json
├── work/
│   └── group.json
└── play/
    └── group.json
```

### .nomedia

- 在快照根目录下创建 `.nomedia` 文件
- 告知 MediaScanner 跳过此目录，防止快照文件出现在图库/文件管理器中
- 由 `SnapshotApp` 在初始化时创建

### 存档文件命名

- 格式：`{yyyy-MM-dd}_{HHmmss}.tar.zst`
- 时间戳为快照创建时间
- 元数据文件 `.meta` 包含 `MetaInfo` JSON（应用版本、包含的数据项等）

## group.json 格式

```json
{
  "name": "分组名称",
  "apps": [
    {
      "packageName": "com.example.app",
      "userId": 0
    }
  ]
}
```

- `name` 字段**可选** — 缺省时 `SnapGroup.name` 回退逻辑：
  1. `group.json` 中的 `name`
  2. 分组目录的 basename
  3. 分组 `id`（目录名）
- `apps` 列表存储分组内的应用（包名 + 用户 ID）

## MMKV 存储

### 存储位置

- 默认实例：`{filesDir}/mmkv/`（应用私有目录）
- 分组实例：`{filesDir}/mmkv/{groupId}/`（每分组独立目录）

### 默认实例（GlobalConfig）

| 键 | 类型 | 说明 |
|---|------|------|
| `groups` / `groups_order` | `List<String>` | 本机全部 SnapGroup ID **登记表**（顺序无 UI 语义） |
| `archive_roots` | `String`（`s:{setId}` / `g:{groupId}` 逗号串） | 存档 Tab 顶层块顺序；本机集登记 == 其中的 `s:` |
| `timelinePreset` | `String` | 时间线筛选预设：`today`/`yesterday`/`7d`/`30d`/`custom` |
| `timelineCustomStart` | `Long` | 自定义起始时间戳（毫秒） |
| `timelineCustomEnd` | `Long` | 自定义结束时间戳（毫秒） |

`archive_roots` **仅键不存在**时由 `groups` 迁成全 `g:`；已写出的空串不得再 flatten。

**文件**：`app/.../config/GlobalConfig.kt`

### 分组实例（GroupConfig）

每个分组独立 MMKV 实例，存储分组内应用的配置（快照选项、排除规则等）。

### 应用配置（AppConfig）

每应用配置由 `AppConfigManager` 管理，缓存在内存中，持久化到分组 MMKV：

| 子配置 | 内容 |
|--------|------|
| `ShotConfig` | 快照启用状态、包含项（DATA/OBB/MEDIA/APK） |
| `ExcludeConfig` | 按压缩类型的排除模式列表 |
| `ActionConfig` | 快照后动作 |
| `ExtraItemsConfig` | 额外压缩目录列表 |

## MMKV 特性与限制

- **格式**：MMKV 使用 mmap 内存映射，写入即持久化，无需显式 flush
- **进程安全**：支持多进程模式，但 AppSnapshoter 仅在单进程使用
- **升级兼容**：MMKV 内置版本迁移机制，库升级时自动处理
- **数据丢失风险**：极罕见（mmap + CRC 校验），但 MMKV 不适合存储关键业务数据（快照文件本身在文件系统上）

## 存储限制

| 限制 | 说明 |
|------|------|
| 分组数量 | 无硬限制，受 MMKV 内存和文件系统约束 |
| 每组存档数 | 无硬限制，受存储空间约束 |
| 存档大小 | 取决于应用数据量和压缩率 |
| 外部存储 | 依赖 `/storage/emulated/0` 可用，SD 卡需 SAF 授权 |

### 外部存储不可用

- 如果外部存储不可用或只读，快照创建/恢复会失败
- `FileSystemHandler.readStatFs()` 可检查可用空间
- 分组路径可通过 SAF 选择 SD 卡等其他位置

## Syncthing 同步

| 内容 | 路径 | 可同步 |
|------|------|--------|
| 快照文件 | `{rootPath}/{pkg}/*.tar.zst` | 是 |
| 分组配置 | `{rootPath}/group.json` | 是 |
| 分组集配置 | `{setPath}/groupset.json` | 是 |
| MMKV 数据 | `{filesDir}/mmkv/` | **否**（应用私有目录） |

### 同步注意事项

- MMKV 数据不在同步范围内，新设备首次需重建本机登记（添加一次分组集即可发现子分组，见 [分组集](../../systems/snapshot/GROUP_SET.md)）
- 分组创建后，后续配置变更通过 `group.json` 同步
- 快照文件直接同步，另一台设备可直接恢复
- 详见 [Syncthing 同步指南](../../guides/getting-started/syncthing.md)
