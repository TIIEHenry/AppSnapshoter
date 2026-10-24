---
title: "r02 provider 审查发现"
type: guide
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "provider 有 5 条正确性问题：多文件打包撞名、符号链接被跟随、解压前清空目标、权限恢复假成功、取消压缩无效。"
---

# r02 provider 审查发现

读完 `provider/src` 下源码（`.kt` `.java` `.aidl`，无 `.cpp` `.h`；跳过 build）。5 条。

## r02-1
- 严重度：高
- 症状：多路径打进同一个 tar 时，最后一段路径相同的条目变成同名成员。解压落到同一路径，同名文件被盖掉，不同目录的内容混在一起。
- 代码：`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/FileSystemImpl.kt:272-280`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/tar/TarCompressor.kt:333-338`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/zstd/ZstdCompressor.kt:455-460`、`provider/src/main/java/tiiehenry/android/snapshot/provider/appmanager/util/PathHelper.kt:14-30`
- 为什么是问题：`createTarArchiveForMultiple` 对每个路径只把 `file.name` 放进 tar 参数。`PathHelper` 的 user 目录和 user_de 目录都以包名结尾，两条一起打包时成员名都是包名。tar 会按成员名解到同一处。单路径打包在成员前写了 `--`（`FileSystemImpl.kt:224-225`），多路径没有；文件名以 `-` 开头会被 tar 当成选项。
- 方向：成员名改成互不冲突的相对路径，并在路径参数前插入 `--`。

## r02-2
- 严重度：高
- 症状：递归删除、递归复制或覆盖写文本时，目录符号链接的目标里的文件会被删掉或写进去。
- 代码：`provider/src/main/java/tiiehenry/android/snapshot/provider/service/handler/FileSystemHandler.kt:63-71`、`provider/src/main/java/tiiehenry/android/snapshot/provider/service/handler/FileSystemHandler.kt:119-125`、`provider/src/main/java/tiiehenry/android/snapshot/provider/service/handler/FileSystemHandler.kt:143-160`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/FileSystemImpl.kt:78-85`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/FileSystemImpl.kt:372-375`
- 为什么是问题：`File.deleteRecursively` 用 `isDirectory` 走目录，会进入指向目录的符号链接，再删除链接里面的路径。`writeText` 在路径已存在时调用它，指向目录的链接会把目标整树删掉。`copyRecursively` 同样按目录遍历，复制进一个指向目录的目标路径时会写进链接目标；`overwrite` 时对目录调用 `deleteRecursively`。`IFileSystem.copyRecursively` 直接转到这个实现。`setUid` / `setGid` 调用 `Os.chown`，会改链接目标的所有者，而不是链接本身。`FileSystemImpl.deleteEntry` 已说明这个行为，并在本地删除时只卸掉链接。
- 方向：删除和复制按 lstat 处理符号链接，只动链接本身；改属主改用 `Os.lchown`。

## r02-3
- 严重度：高
- 症状：解压先清空目标目录。随后 tar 失败时，清空已经生效，原来的文件不会回来，调用方只收到失败。
- 代码：`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/tar/TarDecompressor.kt:98-101`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/tar/TarDecompressor.kt:139-141`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/tar/TarDecompressor.kt:81-84`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/zstd/ZstdDecompressor.kt:47-50`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/zstd/ZstdDecompressor.kt:75-77`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/zstd/ZstdDecompressor.kt:88-91`
- 为什么是问题：`cleanDir` 删掉目标目录的直接子项之后才开始解包。`extractTar` / `extractTarFromFd` 返回失败时抛错，状态改为 ERROR 并 `onError`，没有把已删内容放回去。目标里如果已有数据，失败后只剩下空目录或写到一半的新文件。
- 方向：先解到临时目录，校验成功后再替换目标。

## r02-4
- 严重度：中
- 症状：权限恢复在授权或撤销失败时仍返回成功。备份时没读到的 AppOp 模式被写成 0，恢复时变成允许。
- 代码：`provider/src/main/java/tiiehenry/android/snapshot/provider/service/handler/PermissionManagementHandler.kt:79-83`、`provider/src/main/java/tiiehenry/android/snapshot/provider/service/handler/PermissionManagementHandler.kt:111-126`、`provider/src/main/java/tiiehenry/android/snapshot/provider/service/handler/PermissionManagementHandler.kt:169-197`、`provider/src/main/java/tiiehenry/android/snapshot/provider/service/handler/PermissionManagementHandler.kt:314-329`、`provider/src/main/java/tiiehenry/android/snapshot/provider/appmanager/AppManagerImpl.kt:133-140`
- 为什么是问题：`grantRuntimePermission` / `revokeRuntimePermission` 失败时返回 0 或 -1，不抛异常。`setAppPermission` 不看这个返回值，也不看 `setOpsMode` 的返回值，最后返回 true。`getPermissions` 在 ops 表里没有该 op 时用 `mode ?: 0`；0 是 `MODE_ALLOWED`。恢复时 `setOpsMode` 会把没记录的操作设成允许。应用侧 `setAppPermission` 再用 `safeRun` 丢掉布尔结果，调用方看不到失败。
- 方向：授权、撤销和 setOpsMode 任一失败就返回 false；没读到的模式不要写成 0。

## r02-5
- 严重度：中
- 症状：取消 tar 打包、zstd 打包或 tar 解压后，任务继续跑完，成功时仍回调 onDone。
- 代码：`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/tar/TarCompressor.kt:66-67`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/tar/TarCompressor.kt:90-92`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/tar/TarCompressor.kt:233-234`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/tar/TarCompressor.kt:255-257`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/zstd/ZstdCompressor.kt:76-77`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/zstd/ZstdCompressor.kt:101-103`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/zstd/ZstdCompressor.kt:344-345`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/zstd/ZstdCompressor.kt:367-369`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/tar/TarDecompressor.kt:43-45`、`provider/src/main/java/tiiehenry/android/snapshot/provider/filesystem/compressors/tar/TarDecompressor.kt:129-141`
- 为什么是问题：这些 `cancel()` 只执行 `isCancel.set(true)`。打包流程没有读取该标志，tar 解压把 `isCancel` 传入 `extractTar` 后也没有读取。已发起的 `callTarCli` 会继续把归档写完并走成功回调。同模块的 `ZstdDecompressor` 会在读写循环里检查 `cancelled`。
- 方向：打包和解压循环检查取消标志，并中断 tar 与管道。
