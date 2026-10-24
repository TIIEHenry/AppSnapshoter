---
title: "r11 config 修改方案"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "修 user 0 目录迁移与当前根、读失败不写默认、改路径先重载、lockedList 空值补默认。"
---

# r11 config 修改方案

## r11-1
- 判定：hold
- 问题类：user 0 的读取目录被第一次 `load` 固定在旧路径，写入和重置却只作用于规范目录。
- 不变量：同一次进程里，`load`、`save`、`saveExtraItems`、`reset` 都按当前 `globalRootPath` 计算目录。user 0 在规范目录缺文件、旧目录有文件时，先把旧目录里缺失的那几份配置补进规范目录，再删旧目录；复制未完成时保留旧目录，并继续从旧目录读。规范目录里已经存在的文件保持不动。`reset` 之后规范目录和旧目录都不留配置，新建实例得到默认值。
- 要动的符号或文件：`AppConfig.canonicalConfigDir`、`configDir`、`load`、`save`、`saveExtraItems`、`reset`。去掉 `configDir` 的 `lazy` 绝对路径，改为每次从当前全局根解析。user 0 的迁移按 `items.json`、`excludes.json`、`action.json`、`extra.json` 是否存在判断，不按目录是否存在判断。`saveExtraItems` 在 `mkdirs` 和写 `extra.json` 之前先做迁移。`reset` 同时删除规范目录和 `app_configs/<包名>`。
- 明确不改什么：`AppConfigManager.configKey`、缓存键和 `refreshConfig` 的调用方式；非 0 用户的目录名 `app_configs/<包名>@<userId>`；`ConfigFiles` 的文件名。非 0 用户只跟着去掉过期绝对路径，不做旧目录迁移。
- 本地验证：应新增断言：旧目录有三份主配置、规范目录不存在时，`saveExtraItems` 后规范目录含旧的快照项、排除、动作以及新的额外项，旧目录消失；同一缓存实例 `save` 后再 `refreshConfig`，内存仍是刚写入的内容；`reset` 后新实例为默认配置且两个目录都没有这些文件；中途改 `globalRootPath` 后，`load` 与 `save` 都落在新根，不会把旧根内容写进新根。

## r11-2
- 判定：hold
- 问题类：读失败被换成默认可保存对象，写入又先截断原文件并吞掉异常。
- 不变量：文件不存在时可以用默认对象，并允许创建新文件。文件已存在但读取或解析失败时，原字节保留，本次保存失败，内存中的默认对象不得写回该文件。写入时先写同目录临时文件，完成后替换原文件；任一步失败则删除临时文件，原文件内容不变，失败要传出调用方。
- 要动的符号或文件：`AppConfig.loadConfigFromFile`、`AppConfig.saveConfigToFile`、`AppConfig.load`、`AppConfig.save`；`GroupConfigState.read`、`GroupConfigState.load`；`GroupConfig.saveConfigToFile`、`GroupConfig.save`、`GroupConfig.registerPath`、`GroupConfig.addToLockedList`；`GroupSetConfig.loadConfigFromFile`、`GroupSetConfig.saveConfigToFile`、`GroupSetConfig.load`、`GroupSetConfig.save`。读结果要区分“缺失”和“失败”。`registerPath` 与 `addToLockedList` 在任一已存在文件读失败时不得调用会落盘默认值的 `save`。
- 明确不改什么：`ShotConfig`、`ExcludeConfig`、`ActionConfig`、`GroupConfigData`、`GroupSetConfigData` 在文件缺失时的默认字段值；四份配置的 JSON 字段含义。
- 本地验证：应新增断言：已有 `items.json` 在打开写入后失败，文件内容与失败前一致；该文件内容无法解析时，`registerPath`、`addToLockedList` 和 `GroupConfig.save` 失败，且 `items.json`、`excludes.json`、`action.json`、`group.json` 都不变；文件缺失时 `load` 得到默认对象，随后保存能新建该文件。`AppConfig` 与 `GroupSetConfig` 各做一次同样的读失败与替换失败断言。

## r11-3
- 判定：hold
- 问题类：目录键在构造之后才写入，内存仍是旧目录或默认值，保存会盖掉目标目录；强调色会被第一次读到的值钉在 MMKV 里。
- 不变量：`rootPath` 变更后，下一次 `save` 或 `addToLockedList` 写出的内容来自新目录刚加载的结果。空字符串与空白按未设置，回退到当前 `globalRootPath`。`GroupSetConfig.load` 使用目标目录文件中的强调色；从错误目录带进 MMKV 的颜色不得挡住目标文件。`clearLocal` 之后、重新加载或重新登记路径之前，`save` 不得把清掉之前的 `data` 写到全局根的 `groupset.json`。
- 要动的符号或文件：`GroupConfig.rootPath`、`GroupConfig.load`、`GroupConfig.save`、`GroupConfig.addToLockedList`；`GroupSetConfig.rootPath`、`GroupSetConfig.accentColor`、`GroupSetConfig.load`、`GroupSetConfig.save`、`GroupSetConfig.clearLocal`。setter 只更新 MMKV 并重载目标目录，不在 setter 里保存。路径变更时清掉该 setId 的 `accentColor` 键，再按目标 `groupset.json` 写入。`clearLocal` 丢弃内存中的 `data`，并让后续 `save` 在再次 `load` 或登记路径前直接失败。
- 明确不改什么：MMKV 的 id 与键名 `rootPath`、`isCollapsed`、`accentColor`；键缺失时回退全局根这一规则；`ConfigFiles` 的文件名；`registerPath` 已有的“先 `forRegistration` 再 `save`”顺序，除 r11-2 的失败不落盘外不改其成功路径。
- 本地验证：应新增断言：构造时键缺失因而载入全局根配置，随后把 `rootPath` 设到已有分组目录再 `save`，目标目录的 `group.json`、`items.json`、`excludes.json`、`action.json` 仍是该目录原来的内容。分组集先在全局根读到强调色，再改到含另一强调色的目录并 `load`，`accentColor` 等于目标文件的颜色。`clearLocal` 后立刻 `save` 不创建或修改全局根 `groupset.json`。`rootPath` 存空字符串时读写位置与键缺失相同。

## r11-4
- 判定：hold
- 问题类：`group.json` 里显式 `null` 的 `lockedList` 会在锁定操作上触发空指针。
- 不变量：`GroupConfigData.fromJson` 返回的对象里，`lockedList` 与 `sortConfig` 始终非 null。JSON 显式写成 null 时分别补空列表和新的 `SortConfig`。缺字段时保持类型初始化的默认值。
- 要动的符号或文件：`GroupConfigData.fromJson`。解析结果为 null 时返回新的 `GroupConfigData`。`lockedList == null` 时赋 `new ArrayList<>()`，`sortConfig == null` 时赋 `new SortConfig()`。
- 明确不改什么：`addToLockedList`、`removeFromLockedList`、`isLocked` 的判断和保存条件；`GroupSetConfigData.fromJson` 已有的 `groupOrder` 补空；其它字段的解析方式。
- 本地验证：应新增断言：`{"lockedList":null}` 解析后 `isLocked`、`addToLockedList`、`removeFromLockedList` 不抛异常，且锁定列表按空列表工作；`{"sortConfig":null}` 解析后 `sortConfig` 非 null；`{"lockedList":["a.b"]}` 仍能查出已锁定。
