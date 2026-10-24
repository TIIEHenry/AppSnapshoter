---
title: "r02 provider 修改方案"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "多文件 tar 成员名去重并避开选项解析，符号链接按 lstat 只动链接本身，解压成功后再替换目标，权限恢复传播失败且不把缺失模式写成允许，取消要打断 tar 与管道。"
---

# r02 provider 修改方案

## r02-1
- 判定：hold
- 问题类：多路径打 tar 时成员名只用最后一段，同名源会叠成同一个成员，且路径参数前没有结束选项的分隔。
- 不变量：同一归档里每个源对应的成员路径互不覆盖，解压后各自内容仍分开。各源最后一段互不相同时，成员名仍是这段名字。最后一段以 `-` 开头时，tar 仍把它当文件名。单路径 `createTarArchive` 继续用现有的 `--` 和成员名（文件用文件名，目录用 `.`）。
- 要动的符号或文件：`FileSystemImpl.createTarArchiveForMultiple`。在这里为每个绝对路径生成成员名：最后一段唯一时用 `File.name`；有重复时改成相对各路径公共父目录的相对路径（`/data/user/0/<包名>` 与 `/data/user_de/0/<包名>` 因此会分开）。参数按「`-C` 父目录或公共根 + 该文件的名字」逐个追加。名字用 GNU tar 的 `--add-file=` 传入。`--` 会让它后面的参数都变成文件名，多段 `-C` 不能再放在同一个 `--` 后面，所以这里用 `--add-file=`，不要把一个 `--` 放在第一段 `-C` 之前。
- 明确不改什么：`PathHelper` 的路径字符串；`TarCompressor.streamCompressMultiple` 和 `ZstdCompressor.streamCompressMultiple` 仍把原始路径列表传进去；单路径 `createTarArchive` 的参数形态。
- 本地验证：应新增断言：`/data/user/0/pkg` 与 `/data/user_de/0/pkg` 打进同一个 tar 后，成员名不同，解压得到两套内容。另两条最后一段不同的路径，成员名仍等于各自最后一段。文件名为 `-secret` 时 tar 退出码为 0，且归档里有该成员。

## r02-2
- 判定：hold
- 问题类：root 侧删除、复制、覆盖写文本和 `chown` 会顺着目录符号链接作用到链接目标。
- 不变量：删除或覆盖一个符号链接时，只去掉链接本身，链接目标里的文件和属主保持原样。复制符号链接时得到新的链接，数据写到目标路径本身。普通文件、普通目录的删除、复制、写文本结果与现在相同。`FileSystemImpl.deleteEntry` 继续按已有的「不进入链接」方式删本地项。
- 要动的符号或文件：`FileSystemHandler.deleteRecursively`、`writeText`、`copyRecursively`、`setUid`、`setGid`。用 `Os.lstat` 判断类型。链接则 `unlink` 或按 `readlink` 重建链接，子项是链接时同样停止下钻。`setUid` / `setGid` 改为 `Os.lchown`。`writeText` 遇到链接时只删链接再创建新文件。`copyRecursively` 不再调用会跟随目录链接的 `File.copyRecursively`；`overwrite` 时对链接只删链接。`FileSystemImpl.copyRecursively` 已转到 root 实现，改 handler 即可。
- 明确不改什么：`FileSystemImpl.deleteEntry` 与 `cleanDir`；`getUid` / `getGid` 仍用 `Os.stat`；`fileType` 的判断方式。
- 本地验证：应新增断言：目录符号链接上 `deleteRecursively` 后，目标目录里的文件还在，链接路径消失。`writeText` 写到该链接路径后，目标树还在，新文本出现在链接路径上。`copyRecursively` 的目标是目录符号链接时，字节写到调用方给出的目标路径，目标目录的原有文件还在。`setUid` 之后用 `lstat` 看到链接属主已变，目标文件属主未变。普通目录递归删除仍会删掉其下普通文件。

## r02-3
- 判定：hold
- 问题类：解压在 tar 成功之前已经 `cleanDir` 目标目录，失败后原内容回不来。
- 不变量：解压失败或取消后，目标目录在调用前已有的直接子项仍在。成功后，目标路径上的内容与归档一致，回调仍是 `onDone`，状态为完成。源归档打不开时，仍不改目标内容。失败回调仍是 `onError`，状态为 `COMPRESS_STATE_ERROR`。
- 要动的符号或文件：`TarDecompressor.streamDecompress`、`ZstdDecompressor.decompress`。先把归档解到目标的同级临时目录，成功后再替换：原目标改名为备份，临时目录改名为目标，然后删除备份。替换中途失败则把备份改回原目标，并删掉临时目录。取消与 tar 失败走同一条「不提交临时目录」的路径。`ZstdDecompressor` 仍先打开源归档，再动目标侧的目录。
- 明确不改什么：`FileSystemHandler.extractTarCommand` 的 `tar -xpf - -C` 参数；`FileSystemImpl.cleanDir` / `deleteEntry` 的实现。成功前不再对最终目标调用 `cleanDir`。
- 本地验证：应新增断言：目标目录里预先放一个文件，解压用损坏归档或让 `extractTar` / `extractTarFromFd` 返回 false，该文件仍在，且回调为 `onError`。同一预置文件在成功解压后被归档内容替换。取消发生在解压过程中时，预置文件仍在。

## r02-4
- 判定：hold
- 问题类：运行时权限或 AppOp 设置失败仍向上返回成功，备份时没读到的 AppOp 模式被写成 0（允许）。
- 不变量：`grantRuntimePermission` / `revokeRuntimePermission` 返回 0，或 `setOpsMode` 返回 false 时，`setAppPermission` 返回 false，`setAppPermissions` 在该条停止并返回 false。ops 表里没有该 op 时，恢复不调用 `setOpsMode`，因此不会把模式设成 `MODE_ALLOWED`。已经读到的模式原样写回。`grantRuntimePermission` / `revokeRuntimePermission` 因「is not a changeable permission type」返回 -1 时，仍只记 warning，不让这一条把整批恢复判失败。
- 要动的符号或文件：`PermissionManagementHandler.getPermissions`、`setAppPermission`；`AppManagerImpl.setAppPermission`、`setAppPermissions`。`getPermissions` 在 `ops.get(op)` 为空时不要用 `mode ?: 0`。`AppPermission.mode` 若只能表示整数，就加一个「未读到模式」的约定值或标志，`setAppPermission` 见到该标志则跳过 `setOpsMode`。`setAppPermission` 读取授权/撤销的返回值，0 则返回 false；读取 `setOpsMode` 的返回值，false 则返回 false。`IAppManager.setAppPermission` 与 `setAppPermissions` 改为返回 Boolean。`AppManagerImpl` 把 root 侧的布尔传回调用方，这两处不再用返回 Unit 的 `safeRun` 丢掉结果。
- 明确不改什么：`-1` 的「不可变更权限类型」分支维持 warning，不改成失败。`getPermissions` 的授权标志 `isGranted` 仍来自 `REQUESTED_PERMISSION_GRANTED`。不改 `updatePermissionFlags`。
- 本地验证：应新增断言：`grantRuntimePermission` 返回 0 时 `setAppPermission` 为 false，且应用侧能拿到 false。`setOpsMode` 返回 false 时同样为 false。ops 映射缺少该 op 时，保存的权限不会在恢复时以模式 0 调用 `setOpsMode`。返回 -1 且 `setOpsMode` 成功时，这一条仍为成功。读到的模式为非 0 时，`setOpsMode` 使用该值。

## r02-5
- 判定：hold
- 本轮约束：不重写 `ITaskHandler`，不新增 `TaskSession`。只让现有 `cancel()` 关掉这次任务阻塞中的 FIFO 或输出流，并在 `onDone` 前再看已有取消标志。
- 问题类：tar 打包、zstd 打包和 tar 解压的 `cancel()` 只把标志置真，阻塞中的 tar 会跑完并在成功时 `onDone`。
- 不变量：`cancel()` 之后本次任务不再调用 `onDone`。已写出的半成品目标被删除。回调走现有的取消失败路径（`onError`，状态 `COMPRESS_STATE_ERROR`）。未取消且 tar 与压缩都成功时，仍 `onDone` 并带上大小和 md5。`ZstdDecompressor` 已有的「读写循环里看 `cancelled`，并且 `abort` 管道」保持不变。
- 要动的符号或文件：`TarCompressor` 与 `ZstdCompressor` 里两处 `ITaskHandler.cancel`、`doCompress`、`streamCompress`、`doCompressMultiple`、`streamCompressMultiple`，以及 `ZstdCompressor.compressTarStream`；`TarDecompressor.cancel`、`streamDecompress`、`extractTar`。把 `isCancel` 传入这些流程。`cancel()` 在置标志的同时关掉本次 tar 正在使用的 FIFO 或输出流，让 `callTarCli`、`FlowableStreamParallelCopier.startAndWait`、`extractTar` 从阻塞里返回，并取消对应协程。`streamCompress` 在调用 `onDone` 前再读标志；已取消则抛 `CancellationException`，进入现成的 `onError` 加删除目标文件的分支。`TarDecompressor.extractTar` 要读已经传入的 `isCancel`；tar 返回后若已取消，按取消清理目标侧产物，不调用 `onDone`。
- 明确不改什么：`ZstdDecompressor` 的取消实现。不新增一套与 `CancellationException` 平行的成功状态。单路径 `createTarArchive` 的 tar 参数不因取消改语义。
- 本地验证：应新增断言：打包或 tar 解压开始后调用 `cancel()`，`onDone` 不被调用，`onError` 被调用，目标路径上没有完整产物。不调用 `cancel()` 的成功路径仍收到 `onDone`。标志在 `callTarCli` 返回前已经为真时，等待返回后仍不进入 `onDone`。
