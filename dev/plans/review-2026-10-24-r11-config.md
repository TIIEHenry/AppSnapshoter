---
title: "r11 config 审查发现"
type: guide
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "user 0 应用配置会在新旧目录间丢设置或让重置失效；没读出的配置会被写成默认值；改路径后保存会盖掉目标目录。"
---

# r11 config 审查发现

## r11-1
- 严重度：高
- 症状：user 0 保存额外压缩项后，下次新建配置对象会丢掉快照项、排除规则和动作配置；保存后再刷新会把刚写入的内容退回旧文件；重置后下次启动又出现迁移前的配置。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/config/AppConfig.kt:12-28`、`app/src/main/java/tiiehenry/android/app/snapshot/config/AppConfig.kt:65-69`、`app/src/main/java/tiiehenry/android/app/snapshot/config/AppConfig.kt:75-91`、`app/src/main/java/tiiehenry/android/app/snapshot/config/AppConfig.kt:97-107`、`app/src/main/java/tiiehenry/android/app/snapshot/config/AppConfigManager.kt:18-30`、`app/src/main/java/tiiehenry/android/app/snapshot/config/AppConfigManager.kt:57`
- 为什么是问题：userId 为 0 时，读取目录只在第一次 `load()` 时决定：规范目录 `app_configs/<包名>@0` 还不存在、旧目录 `app_configs/<包名>` 存在，就把旧目录固定为读取路径。`save()` 和 `saveExtraItems()` 始终写规范目录，而且 `saveExtraItems()` 会先 `mkdirs()`，只写 `extra.json`。规范目录一旦出现（空目录也算存在），之后新建的 `AppConfig` 只读规范目录。旧目录里的 `items.json`、`excludes.json`、`action.json` 不再参与加载，内存里换成 `ShotConfig()`、`ExcludeConfig()`、`ActionConfig()` 的默认值。同一缓存实例在 `save()` 之后仍用旧目录执行 `load()`；`AppConfigManager.refreshConfig()` 会走这条路径，把内存退回旧文件，下一次 `save()` 再把旧内容写进规范目录。完整 `save()` 也不会删除旧目录。进程重启后的新实例以规范目录为 `configDir`，`reset()` 只删除规范目录；旧目录还在。再新建实例时规范目录已经没了，又从旧目录读回迁移前的配置，重置之后的修改和“已恢复默认”都不成立。`configDir` 还把第一次看到的 `globalRootPath` 写进绝对路径，`save()` 每次重新取当前全局根。两者不一致时，刷新读旧根，保存写新根，随后的保存会用旧配置覆盖新根下的应用配置。
- 方向：迁移时把旧目录里的四份配置写进规范目录并删掉旧目录，目录选择按文件是否存在判断，且每次读写都用当前全局根。

## r11-2
- 严重度：高
- 症状：配置文件这次没读成，或写入中途失败后，下一次保存变成默认快照项、默认可备份权限、空排除和空锁定列表，原来的分组设置被盖掉。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupConfig.kt:65-68`、`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupConfig.kt:81-86`、`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupConfig.kt:105-109`、`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupConfig.kt:129-136`、`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupConfigState.kt:13-34`、`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupSetConfig.kt:58-60`、`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupSetConfig.kt:76-92`、`app/src/main/java/tiiehenry/android/app/snapshot/config/AppConfig.kt:65-69`、`app/src/main/java/tiiehenry/android/app/snapshot/config/AppConfig.kt:109-125`、`app/src/main/java/tiiehenry/android/app/snapshot/config/ShotConfig.java:19-21`、`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupConfigData.java:14-28`
- 为什么是问题：读取遇到异常时返回 null，调用方换成全新的默认对象：`ShotConfig` 的 `permission` 为 true 且 `items` 含全部压缩项，分组锁定列表为空。`File.writeText` 通过非追加的 `FileOutputStream` 打开文件，打开时原内容已经被截断；写入失败只 `printStackTrace`，调用方仍认为保存成功。截断后的空文件或半截 JSON 下次读取同样变成默认对象。`GroupConfig.registerPath()` 在加载后无条件 `save()`，某一份文件没读出来就会用默认内容写回该目录的四份配置。`addToLockedList()` 也是改一个列表后重写四份文件，内存里那份默认快照配置会一起落盘。
- 方向：先写入临时文件再替换原文件，读失败时保留原文件并让本次保存失败。

## r11-3
- 严重度：高
- 症状：分组或分组集改目录后一保存，目标目录里原来的分组顺序、排除规则、快照项或锁定列表被改路径之前的内容盖掉；分组集强调色可能停在第一次从全局根读到的颜色上。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupConfig.kt:19-23`、`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupConfig.kt:53-61`、`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupConfig.kt:81-86`、`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupConfig.kt:105-109`、`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupSetConfig.kt:20-24`、`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupSetConfig.kt:37-66`、`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupSetConfig.kt:68-74`、`app/src/main/java/tiiehenry/android/app/snapshot/config/ConfigFiles.java:12-21`
- 为什么是问题：`rootPath` 缺键时，所有 `groupId` / `setId` 都落到同一个 `globalRootPath`，文件名却是固定的 `group.json`、`items.json`、`excludes.json`、`action.json`、`groupset.json`。构造函数里的 `load()` 在调用方能够设置目录之前就执行。setter 只写 MMKV，不重新加载。此时内存里要么是全局根上那份配置，要么是文件不存在时的空默认对象。随后 `save()` 或 `addToLockedList()` 按新的 `rootPath` 把这份内存写出去，目标目录里已有的分组顺序、排除、快照项和锁定列表被盖掉。`GroupSetConfig.load()` 在 MMKV 还没有强调色时，会把当前 `data.accentColor` 写进该 setId 的 MMKV；这次读到的若是全局根上的颜色，之后把 `rootPath` 改到真实目录再 `load()`，因为键已存在，真实文件里的颜色不再使用。`clearLocal()` 清掉 `rootPath` 后内存中的 `data` 仍在，再 `save()` 会把这份分组集顺序写进全局根的 `groupset.json`。空字符串不会触发 `?:` 回退，`File("", "group.json")` 变成进程工作目录下的相对路径。
- 方向：路径变更和首次登记必须先从目标目录重新加载再保存，强调色以目标目录文件为准，空路径按未设置处理。

## r11-4
- 严重度：低
- 症状：`group.json` 里 `lockedList` 为 null 时，锁定、取消锁定或查询是否锁定会直接崩溃。
- 代码：`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupConfigData.java:23-35`、`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupConfig.kt:105-127`、`app/src/main/java/tiiehenry/android/app/snapshot/config/GroupSetConfigData.java:20-28`
- 为什么是问题：`GroupConfigData.fromJson` 把解析结果原样返回。JSON 里 `"lockedList": null` 时，Fastjson2 会把字段设为 null，字段初始的空列表不会保留。`addToLockedList`、`removeFromLockedList`、`isLocked` 都直接调用 `lockedList.contains` / `add` / `remove`。同模块的 `GroupSetConfigData.fromJson` 已经对 null 的 `groupOrder` 补了空列表，`lockedList` 没有同样处理。
- 方向：`fromJson` 在 `lockedList` 或 `sortConfig` 为 null 时补上默认对象。
