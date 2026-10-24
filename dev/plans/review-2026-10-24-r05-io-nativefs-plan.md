---
title: "r05 io-nativefs 修改方案"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "fts 打开或遍历失败改为明确失败，路径改为标准 UTF-8 再交给 fts 和 stat。"
---

# r05 io-nativefs 修改方案

## r05-1
- 判定：hold
- 问题类：`fts_open` 失败后 JNI 仍返回 0。
- 不变量：`calculate_tree_size` 返回 0 时，Java 得到的 `Long` 就是本次写入 `*size` 的累计值，累计为 0 时仍然返回 0。返回值非 0 时，调用以 `java.io.IOException` 结束，调用方拿不到成功的大小。`fts_open` 失败时函数继续保持不写 `*size` 并返回 -1。
- 要动的符号或文件：`Java_nota_android_io_NativeFileSystem_calculateTreeSize`（`io-nativefs/src/main/jni/native-filesystem.cpp`）。保存 `calculate_tree_size` 的 `int` 返回值。先释放路径字符串，再分支：返回值非 0 时用 `ThrowNew` 抛出 `java/io/IOException`，消息里带上路径和 `errno`；返回值为 0 时 `return total_size`。异常 pending 之后 JNI 的返回值不会成为 Java 的成功结果。
- 明确不改什么：不改 `calculate_tree_size` 里 `fts_open` 失败即返回 -1 的逻辑；不改 `NativeFileSystem.kt` 的函数签名；不改 `getUid` / `getGid`；不在本项处理遍历中断和字符串编码。
- 本地验证：引用源码里没有测试名。应新增断言：`fts_open` 失败时 Java 调用抛出 `IOException`，且不会得到成功的 0；可打开且累计为 0 的树仍成功返回 0。

## r05-2
- 判定：hold
- 问题类：目录不可读、`stat` 失败或 `fts_read` 中断时，部分大小仍以成功返回。
- 不变量：仅当遍历中没有 `FTS_DNR`、`FTS_ERR`、`FTS_NS`，且 `fts_read` 以 `errno == 0` 结束时，才执行 `*size += matchedSize` 并返回 0。出现上述任一失败时，`*size` 保持进入函数前的值，函数返回 -1。成功路径仍只累计 `FTS_D`、`FTS_DEFAULT`、`FTS_F`、`FTS_SL`、`FTS_SLNONE` 的 `st_blocks * 512`。
- 要动的符号或文件：`NativeNS::calculate_tree_size`（`io-nativefs/src/main/jni/native-filesystem.cpp` 中当前 31–44 行的循环和收尾）。循环前执行 `errno = 0`。`switch` 遇到 `FTS_DNR`、`FTS_ERR`、`FTS_NS` 时记失败并停止继续读。`fts_read` 返回空后、调用 `fts_close` 前，若 `errno != 0` 也记失败。先记下 `errno`，再 `fts_close`。失败时跳过 `*size += matchedSize`，返回 -1；成功时维持现在的 `+=` 并返回 0。这个 -1 交给 r05-1 的 JNI 入口抛出，本项只改 `calculate_tree_size`。
- 明确不改什么：不改 `FTS_PHYSICAL | FTS_NOCHDIR | FTS_XDEV`；不把 `FTS_SL` / `FTS_SLNONE` 改成失败；不把 `FTS_DC`、`FTS_DOT`、`FTS_DP` 纳入失败或大小；失败时不提交部分和；不把 `*size +=` 改成覆盖赋值。
- 本地验证：引用源码里没有测试名。应新增断言：目录不可读、`FTS_ERR`（含路径过长）、`FTS_NS`，或 `fts_read` 结束时 `errno != 0`，函数都返回 -1 且 `*size` 不变；全部可读时返回 0，增量仍是上述节点的 `st_blocks * 512`；`FTS_SLNONE` 仍计入且整次成功。

## r05-3
- 判定：hold
- 问题类：三处 JNI 用修改版 UTF-8 路径调用 `fts_open` 和 `stat`，非 BMP 文件名会查错。
- 不变量：只含 BMP 且不含 U+0000 的路径，交给系统调用的字节与现在的 `GetStringUTFChars` 相同。非 BMP 字符以标准 UTF-8 四字节序列传递。转换失败时 `calculateTreeSize` 走 r05-1 的异常，`getUid` / `getGid` 返回 -1。`stat` 成功时仍返回 `st_uid` / `st_gid`。
- 要动的符号或文件：在 `io-nativefs/src/main/jni/native-filesystem.cpp` 增加仅本文件使用的转换函数：`GetStringChars` 与 `GetStringLength` 取 UTF-16，把代理项对解码成码点后编码为标准 UTF-8，再 `ReleaseStringChars`。`Java_nota_android_io_NativeFileSystem_calculateTreeSize`、`Java_nota_android_io_NativeFileSystem_getUid`、`Java_nota_android_io_NativeFileSystem_getGid` 改为把该结果交给现有的 `calculate_tree_size` 和 `stat`，并去掉这三处 `GetStringUTFChars` / `ReleaseStringUTFChars`。孤立代理项或字符串中的 U+0000 视为转换失败：`calculateTreeSize` 抛 r05-1 的 `IOException`，`getUid` / `getGid` 在释放后返回 -1。转换失败时不调用 `fts_open` 或 `stat`。
- 明确不改什么：不改 `NativeFileSystem.kt` 的三个 `external` 签名；不改大小公式和 `fts` 标志；不把 `stat` 失败时的 -1 改成异常；不把含 U+0000 的字符串截成更短路径再查询。
- 本地验证：引用源码里没有测试名。应新增断言：存在且目录名含非 BMP 字符（如 U+1F600）时，`calculateTreeSize` 与同内容的 BMP 目录名结果相同，`getUid` / `getGid` 返回该路径的 uid/gid；仅含 BMP 的 ASCII 或中文路径与修改前一致；孤立代理项和含 U+0000 的字符串不发起 `stat` / `fts_open`。
