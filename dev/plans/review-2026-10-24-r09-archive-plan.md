---
title: "r09 archive 修改方案"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "4 条维持、1 条收窄：额外项重名、权限失败报成功、高级恢复线程、删除结果，以及会逃出存档目录的额外项名称。"
---

# r09 archive 修改方案

## r09-1
- 判定：hold
- 问题类：额外项名称与内置项或其他额外项相同时，后写入的任务和清单覆盖先登记的那份，备份不失败，恢复会把同一份内置数据再解到用户填写的额外路径。
- 不变量：启用的额外项名称不得与另一条额外项相同，也不得与会进入 `tasks` 的内置标识相同。冲突时快照制作失败，`tasks` 和 `extraItemsMap` 都不得留下被覆盖的前一项。已经写成「内置描述符与额外项共用同一个 json 名」的存档，恢复预检直接失败，不得把该描述符再解到额外路径。
- 要动的符号或文件：
  - `ArchiveMaker.makeSnapshot` 里注册额外项的循环。任务键是 `extraItem.getName()`，清单键是 `"$name.json"`。内置分支随后用同一个 `tasks[item]` 覆盖，包括 apk、data、user、user_de、obb、media；`meta-info` 在这之前已经占键，`uninstall-app` 在这之后占键。
  - 在任何 `compressor.compress` 之前，先检查全部启用的额外项。名称与上述标识冲突、与已占用的任务键冲突，或 `"$name.json"` 已经要写入 `extraItemsMap` 时，抛 `IOException`。沿用 `makeSnapshot` 现有的 `catch`：删掉 pending 目录并返回 `null`。
  - `RestorePreflight.prepare` 组装 `extras` 时，若某个 extra 的 json 名也出现在 `dataItems` 里，抛 `ArchiveValidationException`。这会走现有的 `archive_validation_failed` 路径。
- 明确不改什么：不改内置项自己的文件名和 `tasks[item]` 登记方式。不改 `restoreExtraItem` 对普通额外目录的直接解压，也不改源文件的暂存替换。不新增文案资源。
- 本地验证：把重名判断抽成 archive 包内的纯函数再断言。两条启用的额外项同名时判定为冲突；名称等于上述内置标识或 `meta-info`、`uninstall-app` 时判定为冲突；冲突时不调用 `compress`。扩展 `RestorePreflightTest`：extra 的 json 名与 `dataItems` 相交时抛 `ArchiveValidationException`，并且不产生 `PreparedRestore`。现有 `missing extra descriptor is not silently discarded` 与 `media-only restore plan excludes internal data` 保持通过。

## r09-2
- 判定：hold
- 问题类：`resetAppOps` 之后，固定权限被整段跳过，授权和 `setOpsMode` 的异常也被吞掉，恢复仍提示成功。
- 不变量：`resetAppOps` 之后，存档里每条 `op != OP_NONE` 的权限都要写回原来的模式，包括已经记在 `fixed_permissions.json` 里的名称。模式或授权写不回去时，`restorePermissions` 必须抛错，调用方不得记录恢复成功，也不得弹出成功提示。
- 要动的符号或文件：
  - `PermissionRestorer.restorePermissions`。保留 `resetAppOps` 在循环之前。对每条权限先 `setOpsMode`（`op` 为 `OP_NONE` 的仍不调用），固定列表里的名称只跳过 `grantRuntimePermission` / `revokeRuntimePermission`。
  - 去掉包住授权和 `setOpsMode` 的 `runCatching`。`setOpsMode` 抛错时让函数失败。授权遇到 `RemoteException` 且消息含 `not a changeable permission type` 时，仍把名称记入固定列表；其他授权异常让函数失败。
  - `uid == -1` 或拿不到 `user`、但存档里又有权限要恢复时，改为抛错，不要只打日志后正常返回。
  - `ArchiveRestorer.restoreArchive` 与 `restoreArchiveWithSelectedTypes` 保持先调用 `restorePermissions`、返回之后才 `RestoreRecordWriter.onRestoreSuccess` 和成功 Toast。函数抛错时，现有外层 `catch` 会弹出 `archive_restore_failed`。
- 明确不改什么：权限列表为空时仍跳过重置和写回，不把空列表当成失败。不改 SSAID 写回，不改 `fixed_permissions.json` 的格式，也不把这份文件本身写失败升级成恢复失败。不新增文案资源。不做已写入权限的回滚。
- 本地验证：没有现成测试。把「固定名称是否仍要写模式、哪种异常算失败」抽成可单测的决策或用假的 `IAppManager` 断言：`resetAppOps` 之后固定权限仍调用 `setOpsMode`；`setOpsMode` 或普通授权抛错时 `restorePermissions` 抛出；已知不可变更类型仍记入固定列表，且同一次恢复里该条的 `setOpsMode` 已经调用。

## r09-3
- 判定：hold
- 问题类：高级恢复的收尾回调在 IO 线程上直接刷新当前行。
- 不变量：`restoreAdvanced`、`restoreArchiveItem`、`restoreLatest` 三条路径都在主线程调用 `updateCurrent`。成功提示或失败提示发出之后的这次刷新也在主线程。
- 要动的符号或文件：`ArchiveRestorer.restoreAdvanced` 的 `finally`。改成与同文件另外两处相同的 `withContext(Dispatchers.Main) { updateCurrent() }`。协程仍用 `Dispatchers.IO` 做恢复工作。
- 明确不改什么：不改 `restoreArchiveItem` 和 `restoreLatest` 里已经切回主线程的写法。不改 `itemCallback` 通过 `loadingDialog.post` 更新进度的方式。
- 本地验证：没有现成测试。断言 `restoreAdvanced` 的 `finally` 在主线程调用 `updateCurrent`；成功和失败两条路径都经过这次调用，不在 `Dispatchers.IO` 上直接调用该回调。

## r09-4
- 判定：hold
- 问题类：清空全部存档和彻底删除应用时，`fs.delete` 返回 false 仍被当成成功。
- 不变量：实际调用过的 `fs.delete` 全部返回 true，清空和彻底删除才返回 true。有一次返回 false，或抛出异常，就返回 false。
- 要动的符号或文件：
  - `ArchiveManager.clearAllArchives`。继续跳过实例根目录、仍有实例时的 `apks`、以及等于 `iconFile` 的路径，这些跳过不参与成败。对其余路径保留现在的逐个删除，把每次返回值纳入结果，任一 false 则整个函数返回 false。异常仍由现有 `catch` 返回 false。
  - `ArchiveManager.deleteAppCompletely`。应用目录删除返回 false 时，整个函数返回 false。图标路径在应用目录之内且目录删除已经返回 true 时，目录删除成功即算图标已消失，不要再因为随后的图标删除返回 false 而失败。图标不在应用目录内时，两次删除都要返回 true。
  - 布尔值汇总做成与 `deleteArchiveThenCleanup` 同类的纯函数，供上述两个入口使用。
- 明确不改什么：不改 `deleteArchive` 和 `deleteArchiveThenCleanup` 的语义，不改单份删除失败时保留 APK 引用的行为，不改 `withPackageOp` 拿不到守卫时返回 false 的行为。
- 本地验证：现有 `ArchiveDeletionTest` 的 `failed deletion preserves referenced APK`、`throwing filesystem preserves referenced APK`、`successful deletion permits APK cleanup` 保持通过。为新的汇总函数追加断言：清空侧任一删除为 false 时整体为 false，全部为 true 或没有需要删除的路径时为 true；彻底删除侧目录删除失败为 false，目录外图标两次都为 true 才为 true，目录内图标在目录删除成功后不为第二次 false 失败。

## r09-5
- 判定：weaken（降级后的严重度：高，触发面收窄。反证：`Paths.get(存档目录, 名称 + ".tar.zst")` 和 `File(存档目录, 名称 + ".json")` 规范化之后，`foo/bar`、`/etc/passwd`、单独的 `..`、`foo..bar` 仍在存档目录内；`../evil`、`foo/../../x`、`../` 会离开存档目录。`ArchiveMaker` 143–148 行和 `MetaInfoHelper.saveDataItem` 52–54 行都是把名称原样拼进路径，后一类名称会把压缩包和描述符写到存档目录外。）
- 问题类：额外项名称在拼上扩展名或 `.json` 后，若规范化结果离开存档目录，压缩包和描述符会写到存档目录外面。
- 不变量：`存档目录.resolve(名称 + 后缀).normalize()` 必须仍在该存档目录之内。`../evil`、`foo/../../x`、`../` 制作直接失败。`foo/bar`、`foo..bar`、单独拼上后缀后仍落在目录内的 `..` 名称保持可写。空名称仍按现有空值校验处理，不另加产品规则。
- 要动的符号或文件：
  - 在 archive 包增加一个纯函数，判断「目录 + 名称 + 后缀」规范化后是否仍在该目录内。
  - `ArchiveMaker.makeSnapshot` 在调用 `compress` 之前用它检查 `extraItem.getName()` 的 `.tar.zst` 路径。不通过则抛 `IOException`，走现有 catch，删除 pending 目录并返回 `null`，不要开始压缩。这一检查与 r09-1 的重名检查放在同一次预检里，都通过后才进入压缩循环。
  - `MetaInfoHelper.saveDataItem` 在构造描述符文件并写盘之前，对 `.json` 路径做同一检查。内置项的单个分量名称必须仍能写入。
- 明确不改什么：不改 `RestorePreflight.childPath` 对恢复读取的限制。不改内置项 `data`、`user`、`user_de`、`obb`、`media` 的文件名规则。不把含 `/` 但规范化后仍在目录内的名称改成拒绝。
- 本地验证：扩展 `MetadataWriteTest`。名称 `../evil` 时 `saveDataItem` 抛错，临时目录外不出现新文件；名称 `foo/bar` 仍可写入且文件在存档目录内；名称 `user` 仍可写入。现有 `data descriptor write failure propagates instead of returning a filename` 保持通过。制作侧断言：规范化后离开存档目录时不调用 `compress`。现有 `missing checksum and paths outside archive are rejected` 只覆盖恢复时的 `item.file`，不能当作制作侧已经挡住这类名称。

## 协作取消（供 r15-2）

`ArchiveRestorer.restoreArchiveSuspend` 在每次准备覆盖应用数据之前调用 `ensureActive()`。协程已取消时不再继续写。不改变未取消时的恢复顺序和成功记录。调用方负责取消协程，本文件不读取 `isForceCancelled`。
