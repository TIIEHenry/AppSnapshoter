---
title: "r05 io-nativefs 审查发现"
type: guide
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "目录大小在打开失败和遍历中断时会当成成功返回，非 BMP 路径会按修改版 UTF-8 查错。"
---

# r05 io-nativefs 审查发现

读完 `io-nativefs/src` 下全部 `.kt` / `.cpp` 源码（`NativeFileSystem.kt`、`native-filesystem.cpp`）。共 3 条。

## r05-1
- 严重度：中
- 症状：路径不存在、无权限或 `fts_open` 因其他原因失败时，`calculateTreeSize` 返回 0，调用方会把它当成占用为 0 的成功结果。
- 代码：`io-nativefs/src/main/jni/native-filesystem.cpp:25-29`、`io-nativefs/src/main/jni/native-filesystem.cpp:50-55`、`io-nativefs/src/main/kotlin/nota/android/io/NativeFileSystem.kt:11`
- 为什么是问题：`calculate_tree_size` 在 `fts_open` 失败时返回 -1，并且不会改写 `*size`（JNI 里初始值是 0）。`Java_nota_android_io_NativeFileSystem_calculateTreeSize` 丢掉这个返回值，直接 `return total_size`。Kotlin 声明的返回类型也是 `Long`，没有单独的失败通道，界面或上层会把 0 当成这棵树的真实大小。
- 方向：`fts_open` 失败时向 Java 传递明确错误，避免用 0 表示失败。

## r05-2
- 严重度：中
- 症状：目录不可读、文件 `stat` 失败、路径过长变成 `FTS_ERR`，或 `fts_read` 中途失败时，返回的大小小于整棵树，调用方仍会当作完整结果。
- 代码：`io-nativefs/src/main/jni/native-filesystem.cpp:31-44`
- 为什么是问题：循环把 `fts_read` 的任何 `NULL` 都当成遍历结束；进入循环前没有把 `errno` 清零，结束后也没有检查 `errno`，随后执行 `*size += matchedSize; return 0`。`FTS_DNR`、`FTS_ERR`、`FTS_NS` 不在 `switch` 里，这些节点以及因此没有读到的子树不会计入 `matchedSize`，函数仍然返回成功。
- 方向：读失败或 `fts_read` 出错时向调用方返回失败，并丢弃这次的部分累加。

## r05-3
- 严重度：低
- 症状：路径里含有 emoji 或其他非 BMP 字符时，`calculateTreeSize` 得到 0，`getUid` / `getGid` 得到 -1。
- 代码：`io-nativefs/src/main/jni/native-filesystem.cpp:52`、`io-nativefs/src/main/jni/native-filesystem.cpp:61-64`、`io-nativefs/src/main/jni/native-filesystem.cpp:73-76`
- 为什么是问题：三处都用 `GetStringUTFChars` 取路径。JNI 修改版 UTF-8 会把增补字符编成代理项的 CESU-8 字节，和文件系统上的 UTF-8 文件名不一致。`fts_open` 和 `stat` 会找不到该路径：大小走失败路径变成 0，`stat` 失败则 `getUid` / `getGid` 返回 -1。
- 方向：把 Java 字符串转成标准 UTF-8 后再交给 `fts_open` 和 `stat`。
