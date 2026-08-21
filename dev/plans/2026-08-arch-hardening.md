---
title: "架构加固实施计划（2026-08）"
type: plan
status: draft
created: 2026-08-12
updated: 2026-08-12
summary: "落实架构审查报告的 P1/P2 项：任务契约重写、FIFO 看门狗、root 边界收口、生命周期加固与结构清理"
---

# 架构加固实施计划

> 阶段：架构加固（Arch Hardening）
> 预计工期：A–C 约 9 天（正确性批次，A 与 B 需联合验收）；D–E 约 5 天（可独立排期）
> 状态：planning
> 关联：[架构审查报告 2026-08](../../docs/architecture/review-2026-08.md)

## 目标

消除审查报告识别的四个 P1 问题类——**任务契约名存实亡**（取消/状态机）、**FIFO 管线可永久挂起**、**错误被吞**、**root 边界泄漏**——并以可机械执行的不变量防止复发；P2 结构清理随后独立交付。

## 问题类与目标不变量

| # | 问题类 | 目标不变量（完成后必须成立） |
|---|--------|------------------------------|
| I1 | 契约漂移（取消/状态） | `ITaskHandler` 只有一种语义：`start()` 同步阻塞至终态并返回错误码；`cancel()` 可中断正在运行的 `start()`（含阻塞在 FIFO open/read 上的阶段）；会话级取消状态的 SSOT 仅为 `TaskSession`，禁止在其之外再造取消标志 |
| I2 | 失败与生命周期（FIFO） | 管线任一侧失败后，另一侧在有限时间内（超时或被解除阻塞）必然退出，不存在永久挂起路径 |
| I3 | 错误分层 | **压缩管线**的失败必带错误码跨越接口边界；root 侧 `FileSystemHandler` 所有失败路径至少留下含原因的日志（最低交付），非压缩路径的结构化错误码不在本批 |
| I4 | 边界泄漏 | `:app` 源码零引用 `provider.root.*` / `provider.utils.*` / `provider.service.*` / `provider.filesystem.*`（含 FQCN 引用，由单测机械检查），且 `:app` 业务代码零 `Shell.cmd(` 调用；特权文件操作只经 `IFileSystem` → root 服务。**残留面**：app 进程 main shell 本身（root 检测用途）本批不消除，见非目标 |
| I5 | 宽 root 原语 | `callTarCli` 仅接受白名单 argv（含短选项聚类形式），`--to-command` 等选项被 root 侧拒绝 |

---

## 设计决策

### D1 · `ITaskHandler` 契约：同步语义诚实化（Phase A）

**选定**：保留 AIDL 形态，重写语义——

```aidl
interface ITaskHandler {
    String id();
    int start();    // 同步阻塞至完成；返回 0 或负错误码；至多调用一次
    void cancel();  // 线程安全；中断正在运行的 start()，使其返回 ERR_CANCELLED
}
```

- **删除 `state()`**：调用方改用 `start()` 返回值判断成败。三处轮询/检查一并删除：`DataRestorer.kt:87-91` 与 `ApkInstaller.kt:74-77` 的死轮询、`SnapshotCreator.kt:134` 的无效 `state()==ERROR` 检查。
- **`cancel()` 真接线，且必须关闭 fd**（硬性实现约束）：`StreamParallelCopier.cancel()` 现状只置标志 + `Thread.interrupt()`，而 FIFO 上的阻塞 `read()`/`open()` **不会被 interrupt 唤醒**——因此 `cancel()` 的实现必须**显式 close 管线两端的 PFD/流**（close 使阻塞 read 返回、使 tar 写端收 EPIPE 退出），再辅以 copier 的取消标志；阻塞在 open 阶段的取消经 `FifoGuard.unblock` 解除（见 D3）。禁止仅依赖 `copier.cancel()`。
- **app 层单一取消入口 `TaskSession`**（`app/.../archive/make/TaskSession.kt`）：会话取消状态的 SSOT，持有当前 `ITaskHandler` 引用，区分两级语义——`cancel()`（soft：跳过后续项，当前项跑完）与 `cancel(force = true)`（中断当前 `ITaskHandler`）。`SnapshotCreator`、`GroupBatchArchiver`、`GroupBatchRestorer`、`TimelineBatchOperator` 现有的 6 处成对 `AtomicBoolean` 全部替换；此后业务层禁止在 `TaskSession` 之外新增取消标志（ADR + review 清单固化）。

**拒绝方案**：
- 真异步化（`start()` 立即返回 + 内部协程）——所有调用方已在 IO 协程中同步等待，异步化引入 scope 归属与回调时序新问题，改动面更大，收益为零。
- 移除 AIDL、改纯 `suspend` 接口——保留 AIDL 形态是既有决策（为未来跨进程预留），本计划不推翻；若未来推翻应另立 ADR。

### D2 · 错误码通道（Phase A，与 D1 同批）

**选定**：
- `api` 新增 `CompressErrorCode` 常量类：`OK=0`、`ERR_SOURCE_NOT_FOUND`、`ERR_TARGET_EXISTS`、`ERR_TAR_FAILED`、`ERR_ZSTD_FAILED`、`ERR_FIFO_TIMEOUT`、`ERR_CANCELLED`、`ERR_IO`、`ERR_ARGV_REJECTED`。取消路径上 `ERR_CANCELLED` 的判定**优先于** tar 退出码（tar 收 EPIPE 的非零退出不得掩盖取消语义）。
- `ICompressCallback.onError(String msg)` → `onError(int code, String msg)`；`ITaskHandler.start()` 返回同一错误码。
- root 侧 `FileSystemHandler.extractTar` / `callTarCli` 失败时把 tar stderr 内容带进错误消息（现已读取 stderr，只是丢弃）。
- 最低交付：`FileSystemHandler` 其余 `runCatching{}.getOrNull()` 路径统一补"失败必打含原因日志"（不改签名）。

**范围收敛**：`IFileSystem` 全面 errno 化（`mkdirs`/`delete` 等布尔方法）**不做**——见非目标。结构化错误码只覆盖压缩管线契约与恢复使用点。

### D3 · FIFO 看门狗（Phase B）

**选定**：`provider/.../filesystem/FifoGuard.kt` 内部 helper，两条管线（compress: tar→zstd；decompress: zstd→tar）共用：

1. **open 超时**（硬性实现约束：**`withTimeout` 无法打断阻塞的 `open()` syscall**，禁止用它包裹阻塞 open）——实现为 `O_NONBLOCK` open + 有限重试：读端 `O_NONBLOCK` open 立即成功后等数据；写端 `O_NONBLOCK` open 在无读者时得 `ENXIO`，按间隔重试至 `FIFO_OPEN_TIMEOUT`。两侧语义不对称，封装在 `FifoGuard` 内。
2. **失败解除阻塞**：任一侧异常退出后，`FifoGuard.unblock(fifo)` 以 `O_NONBLOCK` 短暂打开对端并立即关闭（对端阻塞在 `open(O_RDONLY)` 时开写端送 EOF；阻塞在 `open(O_WRONLY)` 时开读端），使对端返回。root 进程 tar 侧（`jni.cpp` fork 前的阻塞 `open(O_WRONLY)`）同样可被 app 侧 unblock 解开。
3. **删除假检查**：`ZstdCompressor.kt:446-456` 的 `exists()` 轮询（FIFO 在 `mkfifo` 后必然存在，条件恒真）删除。
4. stderr FIFO 读取同样纳入超时保护。
5. **可测试性**：`FifoGuard` 的 open/unblock/超时逻辑写成可脱离 root 的形式（普通目录内 mkfifo 即可测），配 JVM 或仪器化单测覆盖：写端 ENXIO 重试、读端 unblock、stderr 超时三个场景——不只靠手测。

**拒绝方案**：完整的 `FifoPipeline` 框架抽象——当前只需超时 + 解除阻塞两个能力，先做小 helper，避免为模式而模式。

**与 Phase A 的耦合**：取消（D1）在 open 阶段依赖 `FifoGuard.unblock`，运行阶段依赖 close fd——I1 的完整验收必须在 A+B 都完成后联合进行。

### D4 · 属主/SELinux 操作下沉 root 服务（Phase C）

**选定**：
- `ISnapShotRootService` 新增三个方法（补全 `FileSystemHandler` 既有非递归 `setUid`/`setGid`/`getGid`，不是从零新造）：
  - `String getFileSecurityContext(String path)`
  - `boolean setFileSecurityContext(String path, String context, boolean recursive)`
  - `boolean chownRecursive(String path, int uid, int gid)`
- root 进程实现：递归 chown 用 `walkTopDown` + `Os.lchown`；SELinux 优先经 `:hiddenapi` 新增 `android.os.SELinux` 精化（`getFileContext`/`setFileContext`），该 API root 进程可用；若设备兼容性问题则退化为 root 进程内**参数化**受控 shell（不拼接调用方字符串进命令行）。
- `IFileSystem` 转发这三个方法；`DataRestorer` 改用 `IFileSystem`，删除 `SELinuxShell` import。
- `AppInfo.kt:12` 的 `provider.utils.drawableToBitmap` 在 app 自己的 `utils` 内实现同等小函数，去掉跨界 import。
- `SELinuxShell` 收编为 root 进程侧 fallback 实现细节，标记 `internal`。

### D5 · `callTarCli` argv 白名单（Phase C）

**选定**：root 侧 `FileSystemHandler.callTarCli` 入口校验：`argv[0]=="tar"`；选项白名单 `{c, x, p, f, C, X}` + `--exclude=*`，**必须支持短选项聚类形式**——现网调用方实际传的是 `-cpf`（`FileSystemImpl.createTarArchive`）与 `-xpf`（`FileSystemHandler.extractTar`），校验器需把 `-cpf` 展开为 `{c,p,f}` 逐字符比对白名单；出现任何白名单外的长/短选项（含 `--to-command`）返回 `ERR_ARGV_REJECTED`。校验器为纯函数，放 `provider/.../service/handler/TarArgvValidator.kt`，单测必须覆盖现网三个调用点的真实 argv（`createTarArchive` / `createTarArchiveForMultiple` / `extractTar`）与拒绝用例。

### D6 · 边界机械检查（Phase C）

**选定**：
- Kotlin 侧：root 进程专属类（`SELinuxShell`、`PmShell`、`handler/*`、`FileSystemManagerRootService`）加 `internal`——跨模块编译期即不可见，是最强约束。注意 `internal` 对 **Java 调用方与反射无效**，故必须配下一条兜底。
- 兜底：`app` 模块新增 JVM 单测 `BoundaryImportTest`，扫描 `app/src/main/java` 全部源文件，断言：(1) 不含禁止前缀的 import **及 FQCN 内联引用**（I4 列表，按包名字符串匹配全文而非仅 import 行）；(2) 不含 `Shell.cmd(` 调用（`AppShell` 的 shell 构建与 `Shell.getShell().isRoot` root 检测除外，白名单显式列出）。零新工具依赖，`./gradlew test` 即执行。
- **`:provider` 按进程角色分包（client/ 与 service/ 两棵树）暂不搬**——`internal` + 扫描测试已能机械守住不变量，物理搬包收益边际且 git 历史成本高；若后续 client/service 代码继续增长再立独立计划。

### D7 · libsu-nio binder 重连（Phase D）

**选定**：`FileSystemProviderImpl` 对 fsm binder 注册 `DeathRecipient`；死亡后重置 `fsmFuture` 并重绑；`FileSystemImpl` 经 volatile holder 取 `FileSystemManager`（不再构造期固化引用）；`ProvidersImpl.getFileSystem()` 的缓存在 binder 死亡时失效。参照 `IServiceClient` 既有 DeathRecipient 模式。

---

## 任务清单

### Phase A: 任务契约重写（P1，约 3 天）

- [ ] ADR：`dev/decisions/002-task-handler-sync-contract.md`——冻结 D1/D2 语义，明确"取消 SSOT 仅为 `TaskSession`，其外禁止新增取消标志"与"cancel 必须 close fd"两条不变量
- [ ] `api`：`ITaskHandler.aidl` 改 `int start()` / 删 `state()`；`ICompressCallback.aidl` 的 `onError(int, String)`；新增 `CompressErrorCode`
- [ ] `provider`：`ZstdCompressor`/`TarCompressor`/`ZstdDecompressor`/`TarDecompressor` 适配——`start()` 返回错误码、`cancel()` 显式 close 管线 PFD/流（辅以 copier 取消标志）、压缩侧补终态语义（体现在返回值）
- [ ] `app`：新增 `TaskSession`（soft/force 两级）；`SnapshotCreator`、`GroupBatchArchiver`、`GroupBatchRestorer`、`TimelineBatchOperator` 替换成对 `AtomicBoolean`；删除 `DataRestorer.kt:87-91` 与 **`ApkInstaller.kt:74-77`** 的轮询、`SnapshotCreator` 的 `state()` 检查，改用返回码；失败清理半成品目录逻辑挂到非零返回码上
- [ ] `ArchiveMaker` 内 meta-info / uninstall 任务适配新契约
- [ ] 单测：`TaskSession`（取消传播、soft/force 语义）、错误码映射

### Phase B: FIFO 看门狗（P1，约 2 天）

- [ ] `provider`：新增 `FifoGuard`（`O_NONBLOCK` open 重试 + unblock；两侧不对称语义内部封装）；`streamCompress` / `streamCompressMultiple` / `streamDecompress` 接入
- [ ] 删除 `streamCompressMultiple` 的 exists 假检查
- [ ] stderr FIFO 读取纳入超时
- [ ] 单测：`FifoGuard`（写端 ENXIO 重试、读端 unblock、超时三场景，普通目录 mkfifo 即可脱离 root 运行）
- [ ] **A+B 联合验收**：open 阶段取消、运行中取消（close fd 生效）、对端崩溃三个时序均无挂起
- [ ] 手测：压缩中 `kill -9` root 进程 → 任务在超时内以 `ERR_FIFO_TIMEOUT`/`ERR_TAR_FAILED` 失败，无挂起

### Phase C: 边界收口（P1，约 4 天）

- [ ] `hiddenapi`：新增 `android.os.SELinux` 精化
- [ ] `api`+`provider`：`ISnapShotRootService` 三个新方法 + `FileSystemHandler` 实现（D4）；`IFileSystem` 转发
- [ ] `provider`：`FileSystemHandler` 全部失败路径补含原因日志（D2 最低交付）
- [ ] `app`：`DataRestorer` 去 `SELinuxShell`；`AppInfo` 去 `provider.utils` import
- [ ] `provider`：`TarArgvValidator` + `callTarCli` 接入（D5）；root 侧专属类 `internal` 化（D6）
- [ ] `app`：新增 `BoundaryImportTest` 源码扫描单测（import + FQCN + `Shell.cmd(`）
- [ ] 单测：`TarArgvValidator`（现网三个调用点真实 argv 通过；`-cpf` 聚类展开；拒绝 `--to-command` 等）
- [ ] 手测：恢复含中文/空格路径的应用数据后 `ls -Z` 与备份前一致；uid/gid 正确

### Phase D: 生命周期加固（P2，约 1 天）

- [ ] `FileSystemProviderImpl` DeathRecipient + 重绑（D7）
- [ ] `FileSystemImpl` 经 holder 取 manager；`ProvidersImpl` 缓存失效
- [ ] 手测：杀死 fsm root 进程后下一次文件操作自动重连成功

### Phase E: 结构清理（P2，约 4 天，可独立排期）

- [ ] `AppInfo`（app 模块）去 `fs`/`appManager` 构造参数，退化为纯数据；调用点改经 repository/helper 注入
- [ ] `SnapGroup.loadApps` 目录扫描/图标落盘迁至 `AppDataRepository`（或专职 loader）；`apps` 集合改返回不可变快照
- [ ] `ArchivedApp.archives` 同样快照化
- [ ] `IServiceClient` 迁出 `:api` 至 `:provider`；`api` 仅留 `IServiceRemoteObserver`
- [ ] 顺带：`FileSystemImpl.createTempFile` 去 `/tmp` fallback；`SnapshotCreator` 压缩任务改 `Dispatchers.IO`；`SnapshotRootService.Impl` Handler 移入构造函数

### Phase F: 数据项表驱动（P3，不排期）

触发条件：下一次新增数据项类型时随需求实施（`ItemSpec` 注册表，见审查报告发现 9）。本计划仅登记，不作为交付物。

## 涉及文件（核心触点）

| 文件 | 操作 | 阶段 |
|------|------|------|
| `api/src/main/aidl/.../task/ITaskHandler.aidl` | 修改（删 state、start 返回 int） | A |
| `api/src/main/aidl/.../file/ICompressCallback.aidl` | 修改（onError 加 code） | A |
| `api/src/main/java/.../fs/CompressErrorCode.java` | 新增 | A |
| `provider/.../filesystem/compressors/**` | 修改（契约适配、cancel 接线） | A/B |
| `provider/.../filesystem/FifoGuard.kt` | 新增 | B |
| `app/.../archive/make/TaskSession.kt` | 新增 | A |
| `app/.../makearchive/SnapshotCreator.kt`、`GroupBatchArchiver.kt`、`batch/GroupBatchRestorer.kt`、`timeline/TimelineBatchOperator.kt` | 修改（TaskSession 替换标志） | A |
| `app/.../archive/restore/DataRestorer.kt` | 修改（去轮询、去 SELinuxShell） | A/C |
| `app/.../archive/restore/ApkInstaller.kt` | 修改（去 `state()` 轮询，改返回码） | A |
| `hiddenapi/.../android/os/SELinux.java` | 新增 | C |
| `provider/.../service/handler/FileSystemHandler.kt` | 修改（新方法、argv 校验、stderr 透传） | C |
| `provider/.../service/handler/TarArgvValidator.kt` | 新增（纯函数 + 单测） | C |
| `app/src/test/.../BoundaryImportTest.kt` | 新增 | C |
| `provider/.../filesystem/FileSystemProviderImpl.kt` | 修改（DeathRecipient 重连） | D |
| `app/.../app/AppInfo.kt`、`group/SnapGroup.kt`、`repository/AppDataRepository.kt` | 修改 | E |
| `api/.../app/IServiceClient.java` | 迁移至 provider | E |

## 验收标准

- [ ] I1（A+B 联合）：压缩 3GB 级目录时 force 取消，任务 ≤1s 内中止（含 fd close 生效）、半成品目录被清理；open 阶段取消同样在超时内返回 `ERR_CANCELLED`；取消 SSOT 检查——除 `TaskSession` 外无新增取消标志（code review + `rg` 辅助，不以 `rg AtomicBoolean` 为唯一手段）
- [ ] I2：`FifoGuard` 三场景单测通过；压缩过程中杀死 root 进程，任务在超时内失败返回，无永久挂起（手测 + 日志确认）
- [ ] I3：人为制造 tar 失败（如源目录权限异常），UI 错误信息含 tar stderr 摘要与错误码；`FileSystemHandler` 失败路径日志抽查有原因输出
- [ ] I4：`BoundaryImportTest`（import + FQCN + `Shell.cmd(`）通过且纳入 `./gradlew test`
- [ ] I5：`TarArgvValidator` 单测——现网三调用点 argv 全通过、`-cpf`/`-xpf` 聚类正确展开、`--to-command` 等被拒绝
- [ ] 全部既有单测通过；快照→卸载→恢复→启动 回归手测通过（含多用户、含中文/空格路径应用）

## 风险与依赖

| 风险/依赖 | 缓解措施 |
|-----------|----------|
| `ICompressCallback`/`ITaskHandler` AIDL 签名变更需全量调用方同步 | 均为进程内调用、无版本兼容负担；一次 PR 内完成编译期强制 |
| `android.os.SELinux` 隐藏 API 在部分 ROM 不可用/行为差异 | 保留 root 进程内参数化 shell fallback（`internal`），启动时探测选择 |
| FIFO `O_NONBLOCK` open 语义在读/写端不对称（写端 ENXIO） | `FifoGuard` 单元化封装两侧差异；真机手测覆盖 tar 先/后崩溃两个时序 |
| tar 收 SIGPIPE 的退出码非 0 会被当作错误 | 取消路径上把 `ERR_CANCELLED` 优先于 tar 退出码判定 |
| `internal` 化可能碰到 Java 调用方（Java 无法感知 Kotlin internal） | 逐类核实调用方；Java 侧靠 `BoundaryImportTest` 兜底 |
| Phase E 的 `AppInfo` 改动触点多（约 30+ 使用点） | 独立 PR、机械替换 + 编译期驱动，不与 A–C 混批 |

## 非目标

- `IFileSystem` 布尔方法全面 errno 化（收益/改动比低，错误通道先覆盖压缩管线）
- `:provider` 物理拆分 client/service 模块（`internal` + 扫描测试已守住不变量）
- **消除 app 进程 main shell 本身**：`AppShell` 初始化的 root 会话（root 检测、libsu RootService 依赖）本批保留——本批消除的是**业务代码经它执行 root 命令**（I4 扫描强制）；main shell 的彻底治理待 client/service 拆分时一并评估
- `IFileCompressor` 真跨进程化、DI 框架、Compose、数据库（同审查报告非目标）
- 快照加密（安全观察 3 属产品取舍，另行讨论）

## 文档更新（随各阶段交付）

| 文档 | 更新内容 | 阶段 |
|------|----------|------|
| `dev/decisions/002-task-handler-sync-contract.md` | 新增 ADR | A |
| `dev/decisions/INDEX.md` | 登记 002 | A |
| `docs/modules/api/INDEX.md` | `ITaskHandler`/`ICompressCallback` 新签名，清除过时描述 | A |
| `docs/systems/compression/INDEX.md` | 契约语义、错误码、看门狗 | A/B |
| `docs/architecture/compression-pipeline.md` | 看门狗与错误码通道 | B |
| `DESIGN.md` | "无任意命令执行"改为"argv 白名单强制"；root 通道描述更新 | C |
| `docs/architecture/overview.md` | 分层图标注进程归属、三条 root 通道（以 `root-service.md` 客户端图为准） | C |
| `docs/architecture/cross-cutting/security.md` | callTarCli 白名单、威胁模型同步 | C |
| `docs/architecture/root-service.md` | libsu-nio 重连行为 | D |
| `AGENTS.md` | "app 只经 Providers"改为指向机械检查的可执行不变量 | C |
| `docs/architecture/review-2026-08.md` | 各发现标注处置状态 | 各阶段 |
