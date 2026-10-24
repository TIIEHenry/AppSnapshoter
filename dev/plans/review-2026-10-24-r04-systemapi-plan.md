---
title: "r04 systemapi 修改方案"
type: plan
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "超长二进制长度拒绝写入，落盘失败要重试且坏条目不得当成成功，截断文件不能当读完，锁内拷字段，DisplayControl 初始化失败返回 null。"
---

# r04 systemapi 修改方案

## r04-1
- 判定：hold
- 问题类：超过 65535 字节的 UTF-8 或字节数组仍把长度截成低 16 位写入，设置文件在没有异常时会被 `finishWrite` 提交。
- 不变量：0 到 65535 字节（含）的字符串和字节数组仍是 2 字节大端长度加原始载荷。超限时在写下该长度和载荷之前抛出 `IOException`，`SettingsStateApi31.doWriteState` 因此到不了 `finishWrite`。
- 要动的符号或文件：`FastDataOutput.writeUTF` 在 `writeShort(tmp.length)` 之前，若 `tmp.length > MAX_UNSIGNED_SHORT` 则抛 `IOException`。`BinaryXmlSerializer.attributeBytesHex` 和 `attributeBytesBase64` 在 `writeShort(value.length)` 之前做同样的判断。`attribute` 的值继续走 `writeUTF`，由那里拒绝超限字符串。
- 明确不改什么：`FastDataInput.readUTF` / `readUnsignedShort` 的 16 位读法，`XmlApi30.ENABLE_BINARY_DEFAULT`，以及 `resolveSerializer` 的二进制默认路径。失败后的重试放在 r04-2。
- 本地验证：引用文件里没有测试名。应新增断言：UTF-8 长度 65535 能写读还原；长度为 65536 的字符串，以及 `attributeBytesHex` / `attributeBytesBase64` 的 65536 字节数组，抛 `IOException`，且输出里没有被截断的 2 字节长度。

## r04-2
- 判定：hold
- 问题类：`doWriteState` 在写入前清掉脏标记，失败不重新排队；空消息的 `IOException` 让 Api31 到不了 `failWrite`；无法编码的 `id` / `name` / `packageName` 被跳过，整次写入仍算成功。
- 不变量：方法返回后，只有 `finishWrite` 成功且快照之后没有新修改，`mDirty` 与 `mWriteScheduled` 才保持清除。失败或快照后仍有修改时，必须再排上现有的延迟写。`IOException.getMessage()` 为 null 时仍要 `failWrite`。`id`、`name` 或 `packageName` 为 null 或 `isBinary` 时，本次不得 `finishWrite`。写盘期间到来的新修改仍要能再排一次，不能被这一轮清掉。
- 要动的符号或文件：`SettingsStateApi26` 与 `SettingsStateApi31` 的 `scheduleWriteIfNeededLocked`、`doWriteState`、`writeSingleSetting`。在锁内每次进入 `scheduleWriteIfNeededLocked` 时递增一个只在 `mLock` 下使用的代数（不要动文件格式用的 `mVersion`）。`doWriteState` 锁内只取这个代数和快照，先不要清 `mDirty` / `mWriteScheduled`。`finally` 里再拿 `mLock`：`wroteState` 为真且代数未变时清两个标记；否则保持 `mDirty` 为真，并调用现有的 `writeStateAsyncLocked()` 排下一次。Api31 的 `catch` 里把 `t.getMessage()` 收成局部变量，为 null 时不要调用 `contains`；`failWrite` 放到失败路径上无条件执行的位置（`out` 的关闭仍用现有的 `IoUtils.closeQuietly`）。两个 `writeSingleSetting` 在现在直接 return 的条件上改为抛 `IOException`，让 `doWriteState` 的 `catch` 接到，从而跳过 `finishWrite`。
- 明确不改什么：`isBinary` 的字符规则，`value` / `tag` 的 base64 编码，`WRITE_SETTINGS_DELAY_MILLIS` 的延迟策略。校验失败沿用这次重新排队，不另加忙等。
- 本地验证：引用文件里没有测试名。应新增断言：`finishWrite` 或 `startWrite` 抛错后，没有新的 `insertSettingLocked` 也会再次进入 `doWriteState`；写盘过程中又一次修改，成功返回后脏标记仍在且还有一次排队；`IOException` 且 `getMessage()` 为 null 时仍调用 `failWrite`；`packageName == null` 时抛 `IOException` 且不调用 `finishWrite`；可编码条目仍能落盘。

## r04-3
- 判定：hold
- 问题类：截断的二进制或文本设置文件会以 `END_DOCUMENT` 结束并被当成读完；主文件中途解析失败后已放入的键会留下来；fallback 拷回主文件时异常被吞掉。
- 不变量：根元素未闭合、二进制流在读完显式 `END_DOCUMENT` 标记之前结束、或文本解析时深度仍大于 0，加载必须失败并转 fallback。换 fallback 之前内存里的设置和 namespace hash 是空的。拷回主文件要么完整替换，要么主文件保持拷贝前的字节，并且失败要抛出去。
- 要动的符号或文件：`BinaryXmlPullParser.nextToken` 捕获 `EOFException` 后继续抛出，不再改成 `END_DOCUMENT`。`setInput` 里对魔数和可选 `START_DOCUMENT` 的现有失败路径保持抛错。`KXmlParser.next` 在 `END_DOCUMENT` 分支上，若 `depth > 0` 则用已有的 `UNEXPECTED_EOF` 抛 `XmlPullParserException`；`depth == 0` 仍表示文本正常结束。`SettingsStateApi31.parseSettingsLocked` 与 `parseNamespaceHash` 若循环因 `END_DOCUMENT` 退出，抛 `XmlPullParserException`。`parseStateLocked` 在从未进入 `TAG_SETTINGS` 就结束时同样抛错。`parseStateFromXmlStreamLocked` 开始解析前清空 `mSettings` 和 `mNamespaceBannedHashes`。fallback 成功后的拷贝改为对主文件使用现有的 `AtomicFile.startWrite` / `finishWrite` / `failWrite`；`IOException` 时 `failWrite` 后抛 `IllegalStateException`。fallback 也解析失败时，先清掉半份结果再抛现有的 `IllegalStateException`。
- 明确不改什么：`XmlApi30.resolvePullParser` 的魔数分流，文本 XML 在深度为 0 时把 EOF 当作文档结束，fallback 文件名后缀 `FALLBACK_FILE_SUFFIX`。
- 本地验证：引用文件里没有测试名。应新增断言：带魔数和 `START_DOCUMENT` 但没有 settings 结束标签的二进制流，`parseStateFromXmlStreamLocked` 返回 false；主文件先放入仅主文件才有的键再失败时，fallback 读完后该键不在 `mSettings`；空文件不会返回 true；拷贝中途抛 `IOException` 时异常向外抛，主文件内容仍是拷贝前的字节。

## r04-4
- 判定：hold
- 问题类：落盘只浅拷贝了 `ArrayMap`，锁外读的仍是会被 `update` 逐字段改写的同一个 `Setting`。
- 不变量：`mLock` 释放之后，`writeSingleSetting` 读到的 `id`、`name`、`value`、`defaultValue`、`packageName`、`tag`、`defaultFromSystem`，以及 Api31 的 `isValuePreservedInRestore`，都来自锁内已经拷好的字段，和锁外对原对象的 `update` 无关。
- 要动的符号或文件：`SettingsStateApi26.doWriteState` 与 `SettingsStateApi31.doWriteState` 在现有的 `synchronized (mLock)` 里，把 `new ArrayMap<>(mSettings)` 的每个值换成 `new Setting(setting)`。两个类已有的 `Setting(Setting)` 会拷贝上述字段。锁外仍把这份副本交给 `writeSingleSetting`。
- 明确不改什么：`update` / `init` 的赋值顺序，`mLock` 与 `mWriteLock` 的加锁顺序，`mNamespaceBannedHashes` 的浅拷贝（值是不可变 `String`）。
- 本地验证：引用文件里没有测试名。应新增断言：锁内快照后、锁外读取前，对原 `Setting` 调用 `update` 改掉 `value` 和 `packageName`，写入参数仍是快照时的整组字段；快照对象与 map 里的活对象不是同一个实例。

## r04-5
- 判定：hold
- 本轮不改：全仓库没有调用方。不要改 `DisplayControl`。
- 问题类：静态初始化失败后 `CLASS` 保持 null，两个 public 方法在 `CLASS.getMethod` 上抛出 `NullPointerException`，现有的 `ReflectiveOperationException` 接不住。
- 不变量：`services.jar` 或 `android_servers` 初始化失败时，`getPhysicalDisplayToken` 与 `getPhysicalDisplayIds` 返回 null。初始化成功时仍按现有反射调用返回 token 或 id 数组。
- 要动的符号或文件：`DisplayControl.getPhysicalDisplayToken` 与 `getPhysicalDisplayIds` 在取 `Method` 之前，若 `CLASS == null` 则返回 null。两处 `catch` 同时接住 `NullPointerException`，记录后返回 null。
- 明确不改什么：静态初始化块继续吞掉异常并只打日志，类加载与 `loadLibrary0` 的参数保持原样。
- 本地验证：引用文件里没有测试名。应新增断言：`CLASS` 为 null 时两个 public 方法都返回 null，且不抛 `NullPointerException`。
