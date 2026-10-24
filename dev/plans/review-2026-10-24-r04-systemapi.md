---
title: "r04 systemapi 审查发现"
type: guide
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "5 条：超长二进制 XML 会把设置文件写坏，落盘失败不再重试，截断文件会被当成读完，并发更新会写出撕裂字段，DisplayControl 初始化失败会崩溃。"
---

# r04 systemapi 审查发现

通读 `systemapi/src` 下 25 个 `.java`/`.kt` 源文件（无 `.aidl`/`.cpp`/`.h`，已跳过 build）。下面 5 条。

## r04-1
- 严重度：高
- 症状：单条字符串的 UTF-8 或字节数组超过 65535 字节时，二进制 XML 长度被截成低 16 位，整份设置文件仍被提交；下次读出的值错位，或文件直接作废，原有设置不再可用。
- 代码：`systemapi/src/main/java/com/android/internal/util/FastDataOutput.java:186`、`systemapi/src/main/java/com/android/internal/util/FastDataOutput.java:194`、`systemapi/src/main/java/com/android/internal/util/FastDataOutput.java:238`、`systemapi/src/main/java/com/android/internal/util/FastDataInput.java:203`、`systemapi/src/main/java/com/android/internal/util/BinaryXmlSerializer.java:200`、`systemapi/src/main/java/com/android/internal/util/BinaryXmlSerializer.java:224`、`systemapi/src/main/java/com/android/internal/util/BinaryXmlSerializer.java:235`、`systemapi/src/main/java/android/util/XmlApi30.java:68`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi31.java:459`
- 为什么是问题：`writeUTF` 用 `writeShort(tmp.length)` 只写下长度的低 16 位，随后 `write` 仍把全部字节写入。读取端按截断后的长度切字符串，后面的字节被当成后续 token。`attributeBytesHex` / `attributeBytesBase64` 对 `value.length` 同样 `writeShort`。`BinaryXmlSerializer` 自己写明上限是 65535 字节，但超限不抛错。`ENABLE_BINARY_DEFAULT` 默认 true，`SettingsStateApi31` 经 `resolveSerializer` 走这条路径，`finishWrite` 会在没有异常时提交这份坏流。
- 方向：长度超过无符号 16 位时抛 `IOException`，不要截断后继续写完并提交。

## r04-2
- 严重度：高
- 症状：写盘失败后修改只留在内存里，没有下一次修改就不会再写；`id`、`name` 或 `packageName` 为空或含 XML 非法字符的条目每次都被跳过，进程重启后这些设置不在文件里。
- 代码：`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi26.java:343`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi26.java:391`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi26.java:409`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi31.java:441`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi31.java:464`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi31.java:506`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi31.java:519`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi31.java:563`
- 为什么是问题：`doWriteState` 在真正写入之前就把 `mDirty` 和 `mWriteScheduled` 清掉。失败分支只 `failWrite`，没有重新排队；此后若没有新的修改，`scheduleWriteIfNeededLocked` 不会再跑。Api31 里若 `IOException.getMessage()` 为 null，`contains` 会先抛出，`failWrite` 执行不到。`writeSingleSetting` 在 `id`/`name`/`packageName` 为 null 或 `isBinary` 时直接 return，调用方仍把整次写入当成成功，这些条目之后每次落盘都会被同样跳过。
- 方向：只有 `finishWrite` 成功才清脏标记，失败则重新排队；不能写入的条目应让本次写入失败，而不是跳过并报成功。

## r04-3
- 严重度：高
- 症状：设置文件被截断或只写出一半时，加载会把残缺内容当成完整状态；有 fallback 也不会用，之后一次正常保存会把残缺结果写成正式文件。
- 代码：`systemapi/src/main/java/com/android/internal/util/BinaryXmlPullParser.java:166`、`systemapi/src/main/java/com/android/org/kxml2/io/KXmlParser.java:993`、`systemapi/src/main/java/com/android/org/kxml2/io/KXmlParser.java:378`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi31.java:670`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi31.java:686`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi31.java:702`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi31.java:763`
- 为什么是问题：二进制解析器把 `EOFException` 收成 `END_DOCUMENT`；`KXmlParser.peekType` 在补不满缓冲时同样返回 `END_DOCUMENT`，`next` 不再检查是否还有未闭合标签。`parseStateFromXmlStreamLocked` 因此返回 true，`readStateSyncLocked` 直接结束，不会读 `.fallback`。主文件若在中途抛出可捕获的解析错误，已经 `put` 进 `mSettings` 的项不会清空，fallback 只是覆盖同名键，残缺键还在。fallback 拷回主文件用的是直接 `FileOutputStream`，`IOException` 被忽略，拷到一半会留下截断主文件，下一次启动又走上一条“截断即成功”的路径。
- 方向：文档在根元素未闭合或流提前结束时必须失败；换 fallback 之前清空已读入的设置，拷回主文件失败不能吞掉。

## r04-4
- 严重度：中
- 症状：落盘过程中若同一条设置被更新，文件里会留下同一次修改对不上的组合，例如新 value 配旧 package 或旧 id。
- 代码：`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi26.java:345`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi26.java:372`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi26.java:722`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi31.java:443`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi31.java:468`、`systemapi/src/main/java/com/android/providers/settings/SettingsStateApi31.java:1009`
- 为什么是问题：锁内只做了 `new ArrayMap<>(mSettings)`，值仍是原来的 `Setting` 对象。`update` 在锁内逐个字段改这个对象，而 `doWriteState` 释放锁之后才读 `getValue`、`getDefaultValue`、`getPackageName`。写盘线程可以看见一次更新做了一半的字段。
- 方向：在锁内把要写的字段拷成不可变快照，锁外只序列化这份快照。

## r04-5
- 严重度：中
- 症状：`services.jar` 或 `android_servers` 初始化失败时，获取物理显示 token 或显示 id 会直接崩溃，而不是返回 null。
- 代码：`systemapi/src/main/java/com/android/server/display/DisplayControl.java:38`、`systemapi/src/main/java/com/android/server/display/DisplayControl.java:52`、`systemapi/src/main/java/com/android/server/display/DisplayControl.java:59`、`systemapi/src/main/java/com/android/server/display/DisplayControl.java:69`、`systemapi/src/main/java/com/android/server/display/DisplayControl.java:76`
- 为什么是问题：静态初始化失败时 `CLASS` 保持 null，注释写的是调用方法时再失败。`CLASS.getMethod` 抛出的是 `NullPointerException`，不在 `ReflectiveOperationException` 里，两个 public 方法的 catch 接不住。
- 方向：`CLASS` 为 null 时直接返回 null，把 `NullPointerException` 纳入失败路径。
