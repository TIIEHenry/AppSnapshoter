# AppSnapshoter 架构深度分析与重构建议

> 基于对 8 个模块、~12,000+ 行代码的全面分析

---

## 一、架构现状总览

```
┌─────────────────────────────────────────────────────┐
│  :app (UI层, ~2800行)                                │
│  Activities(3) + Fragments(9) + ViewModels(3)        │
│  Adapter(5+) + Helpers(10+)                          │
├─────────────────────────────────────────────────────┤
│  :api (契约层, ~900行)                                │
│  IAppManager(30方法) + IFileSystem(20方法)           │
│  AIDL: IFileCompressor + ICompressCallback + ITaskHandler │
│  IServiceClient(455行) + Data Classes                │
├─────────────────────────────────────────────────────┤
│  :provider (实现层, ~4100行)                          │
│  Root Service + Handlers + Compressors               │
│  AppManagerImpl + FileSystemImpl                     │
├────────────────┬──────────────┬─────────────────────┤
│  :hiddenapi    │  :systemapi  │  :io-* (JNI层)      │
│  (Refine反射)   │  (框架桩类)   │  nativefs/tar/zstd  │
└────────────────┴──────────────┴─────────────────────┘
```

**模块分层清晰，契约层设计优秀** — `:api` 模块零实现依赖，接口定义合理。但实现层和 UI 层有明显的架构债务。

### 项目依赖图

```
:app  →  :api, :hiddenapi, :provider
:provider  →  :api, :hiddenapi, :systemapi, :io-nativefs, :io-tar, :io-zstd
:api  →  (无内部依赖；仅 AndroidX core/appcompat)
:hiddenapi  →  (无内部依赖；仅 Refine 注解处理器)
:systemapi  →  (无内部依赖；仅 AndroidX appcompat)
:io-nativefs  →  (无内部依赖；独立 JNI)
:io-tar  →  (无内部依赖；独立 JNI + 内置 GNU tar)
:io-zstd  →  (无内部依赖；独立 JNI + 内置 zstd-jni)
```

---

## 二、关键问题（按严重程度排序）

### P0: SnapshotViewModel 生命周期失控

**问题**: `SnapshotViewModel` 不是真正的 AndroidX ViewModel，而是在 `Application.onCreate()` 中手动创建的单例，拥有独立的 `CoroutineScope(SupervisorJob + Dispatchers.IO)` 且永不取消。

**风险**: 内存泄漏、协程泄漏、屏幕旋转时数据不一致

**建议**:

方案A（推荐）— 拆分为两个 ViewModel：
- `SnapshotViewModel` → 标准 AndroidViewModel，通过 ViewModelProvider 管理
- `AppDataRepository` → 单例数据仓库，管理 IO 协程和缓存

方案B — 引入 Koin，用 `viewModel` DSL 注入

**补充**: `SnapshotViewModel` 同时管理分组配置、应用列表、缓存状态等多种职责，拆分时需先梳理其内部数据流，确定哪些归属 `AppDataRepository`、哪些保留在 ViewModel。

### P0: 全局静态单例链

`SnapshotApp.getInstance()` → `getViewModel()` → `appManager`/`fileSystem`，这条链让 Adapter、Helper、工具类都紧耦合到 Application。

**影响范围**: 几乎所有文件都直接或间接依赖 `SnapshotApp`

**目标** — 构造函数注入替代全局访问：

```kotlin
// 当前（问题代码）
val vm = SnapshotApp.getViewModel()  // 全局可达

// 目标：显式依赖
class GroupItemAdapter(
    private val appManager: IAppManager,
    private val fileSystem: IFileSystem,
    private val onAction: (Action) -> Unit
) : RecyclerView.Adapter<...>()
```

### P1: Fat ViewHolder — 业务逻辑混入 UI 层

| ViewHolder | 行数 | 混入的职责 |
|-----------|------|-----------|
| `GroupsAdapter.GroupViewHolder` | ~260 | 拖拽排序、弹窗菜单、批量归档、折叠状态 |
| `GroupItemAdapter.ViewHolder` | ~300 | 快照创建/恢复、弹窗、归档删除、应用启动、锁定状态 |

**建议** — 抽取为独立 Controller/UseCase：
- `GroupActionsController` → 处理弹窗菜单、批量操作
- `SnapshotOperationUseCase` → 创建/恢复/删除快照的业务编排
- ViewHolder 只负责 `bind(data)` 和 UI 事件转发

### P1: ArchiveRestorer — 981 行上帝类

整个 app 模块最大的文件，包含全部恢复逻辑。应按职责拆分：

```
ArchiveRestorer (981行) →
  ├── RestoreCoordinator    # 编排恢复流程
  ├── ArchiveExtractor      # 解压逻辑（委托给 IFileCompressor）
  ├── DataRestorer           # 数据目录恢复（权限、SELinux、时间戳）
  └── ApkInstaller           # APK 安装逻辑
```

### P1: AppManagementHandler — 551 行混合职责

根服务端的上帝类，混合了包查询、安装/卸载、进程管理、应用启动。其中 `launchApp()` 单方法 ~80 行，含复杂的 `dumpsys` 输出解析和多级回退。

```
AppManagementHandler (551行) →
  ├── PackageManagerDelegate    # 包查询、安装、卸载
  ├── ProcessManager            # force-stop、suspend
  └── AppLauncher               # launchApp() 独立出来
```

### P2: 压缩管道代码重复

`ZstdCompressor` 和 `TarCompressor` 之间以及 `compress()`/`compressMultiple()` 之间存在大量重复：

| 重复模式 | 涉及文件 |
|---------|---------|
| `streamCompress` / `streamCompressMultiple` | ZstdCompressor 内部 80% 重复 |
| FIFO 创建 + 协程 + 错误处理 | ZstdCompressor, TarCompressor, ZstdDecompressor |
| `normalizeTarStdErr()` | ZstdCompressor, TarCompressor |
| `drawableToBitmap()` | AppManagerImpl, AppManagementHandler |

**建议** — 其中 `normalizeTarStdErr` 和 `drawableToBitmap` 值得直接提取为独立工具函数；但 FIFO 管道不建议抽象基类——tar 和 zstd 的 FIFO 用法细节差异较大（tar fork 子进程、zstd 是 JNI 内），强行模板化收益不如心智负担。

### P1: runBlocking 泛滥 — ~42 处阻塞调用

provider 模块中 `IAppManager`/`IFileSystem` 的同步接口方法全部通过 `runBlocking` 桥接到 root 服务的异步调用：
- `AppManagerImpl.kt` — ~28 处（几乎所有方法）
- `FileSystemImpl.kt` — ~8 处
- 压缩器中 — ~6 处

**风险**: root IPC 耗时不确定，在主线程路径上触发 ANR；在 IO 线程上浪费协程线程池资源。

**建议** — 长期将 `IAppManager`/`IFileSystem` 接口改为 suspend 或返回 `CompletableFuture`；短期优先标记高耗时方法（`installApk`、`createTarArchive`）为异步。

### P2: IAppManager 接口膨胀 — 30 个方法

单一接口承担了包管理、权限管理、SSAID、进程控制、应用启动等多个领域。

**建议** — 按领域拆分为 2 个子接口（拆 4 个对 root 备份工具而言过重，增加服务绑定复杂度）：

```kotlin
interface IPackageManager { ... }      // 包查询/安装/卸载/进程控制 (~16 方法)
interface IPermissionManager { ... }   // 权限/AppOps/SSAID (~14 方法)

interface Providers {
    fun packages(): IPackageManager
    fun permissions(): IPermissionManager
    fun files(): IFileSystem
}
```

### P2: 全局静态依赖链需改造为构造函数注入

手动 Service Locator 模式导致依赖隐式化。当前代码中 `runBlocking {}` 遍地开花也与缺少异步注入有关。

**建议** — 不引入 DI 框架（项目规模不必要），直接改造构造函数注入：
- `ProvidersImpl` 已经是手动 DI root，只需改传递链路
- 通过构造函数/`onAttach` 将 `Providers` 接口传入 Fragment/Adapter
- `SnapshotViewModel` 改为标准 `by activityViewModels()`，自然获得 `viewModelScope` 生命周期

### P3: AppConfigActivity 是冗余的

`AppConfigActivity`（26 行）仅是 `AppConfigFragment` 的薄壳。直接从其他 Fragment 打开 BottomSheet 即可，不需要一个额外的 Activity。

### P3: 无 Navigation Component

手动 `FragmentTransaction.replace()` + 大量 BottomSheet，状态管理复杂。迁移到 Jetpack Navigation 可以：
- 统一 Fragment 间通信（Safe Args 替代手动 setArguments）
- 自动处理返回栈
- 支持 deep link

---

## 三、:app 模块详细分析

### Activity/Fragment 清单

**Activities (3)**

| Class | 行数 | 职责 |
|-------|------|------|
| `MainActivity` | 402 | 启动页，底部导航两个 Tab（LauncherFragment, AppsFragment），启动时检查 root 权限和存储权限 |
| `SettingsActivity` | 102 | 设置页，RecyclerView 列表，目前仅包含"忽略应用"入口 |
| `AppConfigActivity` | 26 | 薄壳 Activity，立即打开 AppConfigFragment 作为 BottomSheet |

**Fragments (9+)**

| Class | 行数 | 类型 | 职责 |
|-------|------|------|------|
| `LauncherFragment` | 111 | Fragment | 主 Launcher Tab，RecyclerView 显示 SnapGroup 列表 |
| `AppsFragment` | 54 | Fragment (extends BaseAppsFragment) | 应用列表 Tab |
| `BaseAppsFragment<VB>` | 97 | Abstract Fragment | 应用列表通用基类，委托给 AppsListComponent |
| `SelectAppFragment` | 162 | BottomSheetDialogFragment | 应用选择器，支持单选和多选 |
| `AppConfigFragment` | 229 | BottomSheetDialogFragment | 应用配置编辑器，5 个配置管理器 |
| `GroupConfigFragment` | 210 | BottomSheetDialogFragment | 分组配置编辑器 |
| `GroupSettingFragment` | 198 | BottomSheetDialogFragment | 分组元数据编辑器 |
| `AddGroupBottomSheet` | 127 | BottomSheetDialogFragment | 创建新分组 |
| `IgnoreAppsFragment` | 234 | BottomSheetDialogFragment | 管理忽略应用列表 |

### ViewModel 清单

| Class | 行数 | 创建方式 | 依赖 |
|-------|------|---------|------|
| `SnapshotViewModel` | 207 | 手动实例化于 `SnapshotApp.onCreate()` | 通过 `SnapshotApp.getInstance()` 访问 appManager/fileSystem |
| `LauncherViewModel` | 45 | 标准 `AndroidViewModel` via `by activityViewModels()` | 委托给 ArchiveRestorer |
| `AppsViewModel` | 152 | 标准 `ViewModel` via `by activityViewModels()` | 读取 SnapshotApp.getViewModel() 获取分组数据 |

### SnapshotApp Application 类

`onCreate()` 执行流程：
1. 设置静态单例 `instance = this`
2. 设置 `globalRootPath` 为 `/sdcard/Android/snapshot`
3. 初始化 MMKV（腾讯键值存储）
4. 初始化 `AppShell.initMainShell(this)` — 设置 libsu 主 shell
5. 手动创建 `SnapshotViewModel`（非生命周期感知）
6. 创建 `ProvidersImpl(this)` — DI 根
7. 检查 root 可用性 → `_providers.bindRootService()`

暴露静态访问器：`getInstance()`、`getContext()`、`getViewModel()`

---

## 四、:provider 模块详细分析

### 文件清单（27 个源文件，~4137 行）

| 文件 | 行数 | 角色 |
|------|------|------|
| `AppManagementHandler.kt` | 551 | 根服务端应用管理处理器 |
| `ZstdCompressor.kt` | 502 | Zstd 压缩管道 |
| `FileSystemImpl.kt` | 390 | IFileSystem 实现（NIO + root 服务） |
| `TarCompressor.kt` | 360 | Tar 压缩管道 |
| `PermissionManagementHandler.kt` | 355 | 根服务端权限处理器 |
| `AppManagerImpl.kt` | 332 | 客户端 IAppManager 门面 |
| `ZstdDecompressor.kt` | 255 | Zstd 解压 |
| `FileSystemHandler.kt` | 251 | 根服务端文件系统处理器 |
| `SnapshotRootService.kt` | 226 | Root 服务 AIDL 桩 + 系统上下文初始化 |
| `PmShell.kt` | 157 | pm 命令 Shell 封装 |
| `TarDecompressor.kt` | 147 | Tar 解压 |
| `FileCompressor.kt` | 138 | IFileCompressor 路由/分发 |
| `SsaidManagementHandler.kt` | 83 | SSAID 设置管理 |
| `SELinuxShell.kt` | 78 | SELinux 上下文/chown Shell 封装 |
| `SnapShotRootServiceClient.java` | 74 | Root 服务客户端单例 |
| `NotificationHelper.kt` | 64 | 通知工具 |
| `PathHelper.kt` | 60 | 应用目录路径构建 |
| `ParcelableHelper.kt` | 56 | Parcel 序列化辅助 |
| `ProvidersImpl.kt` | 55 | 组合根 |
| `FileSystemProviderImpl.kt` | 58 | IFileSystem 工厂 |
| `FlowableStreamParallelCopier.kt` | 50 | 带进度的流并行复制 |
| `LogHelper.kt` | 49 | 统一日志封装 |
| `MD5Utils.kt` | 41 | MD5 哈希计算 |
| `IAlgorithmCompressor.kt` | 29 | 压缩器接口 |
| `ShellResult.kt` | 16 | Shell 结果数据类 |
| `StatFsResult.kt` | 13 | 文件系统统计结果 |
| `FileSystemManagerRootService.kt` | 11 | Libsu FileSystemManager 绑定器 |

### AIDL 双服务绑定架构

项目运行两个独立的 root 服务绑定：

**A) SnapShotRootService（自定义 AIDL）**
- ~30 个方法，覆盖连接测试、应用管理（16 方法）、权限管理（7 方法）、AppOps（4 方法）、SSAID（2 方法）、文件系统操作（15 方法）
- 服务端 `SnapshotRootService` 通过 `ActivityThread.systemMain().systemContext` 获取特权系统服务
- 委托给 4 个 Handler：AppManagementHandler、PermissionManagementHandler、FileSystemHandler、SsaidManagementHandler

**B) FileSystemManagerRootService（libsu 内置）**
- 极简：`onBind()` 直接返回 `FileSystemManager.getService()`
- 提供 root 级 NIO 文件访问

### 压缩管道数据流

```
应用数据目录
    │
    ▼
IFileSystem.createTarArchive()     — 通过 JNI 调用 GNU tar (io-tar)，fork 子进程
    │                                  tar stdout 输出到 FIFO 管道
    ▼
ZstdOutputStream (io-zstd zstd-jni) — 从 FIFO 读取，写入压缩 .tar.zst
    │
    ▼
ICompressCallback.onProgress()     — 上报进度 bytes/s
    │
    ▼
ICompressCallback.onDone()         — 上报 originalSize, targetSize, md5
```

关键设计决策：
- **Tar 在 fork 的子进程中运行** — JNI 层直接调用 GNU tar 的 `main()`，获得完整 CLI 功能
- **Zstd 通过 JNI 进程内运行** — 使用 `ZstdOutputStream`，支持多线程
- **FIFO 管道桥接 tar 和 zstd** — 实现流式压缩，无需中间文件
- **仅 `IFileCompressor` 使用 AIDL** — 因为需要异步回调和可取消任务；`IAppManager` 和 `IFileSystem` 使用普通 Java 接口

---

## 五、:api 模块接口分析

### IAppManager（24 个方法，接口膨胀）

| 领域 | 方法 |
|------|------|
| 用户管理 | `getUsers()` |
| 包查询 | `getInstalledPackages()`, `getPackageInfo()`, `getApplicationInfo()`, `loadLabel()`, `loadIcon()`, `getDir()` |
| 权限 | `getPermissions()`, `setAppPermission()`, `setAppPermissions()` |
| 安装/卸载 | `isInstalled()`, `installApk()`, `installApks()`, `uninstallApk()` |
| 生命周期 | `forceStopPackage()`, `clearAppData()`, `suspendPackage()`, `unsuspendPackage()` |
| 运行时权限 | `grantRuntimePermission()`, `revokeRuntimePermission()`, `getPermissionFlags()`, `updatePermissionFlags()` |
| AppOps | `getPackageUid()`, `getUserHandle()`, `setOpsMode()`, `resetAppOps()` |
| SSAID | `getPackageSsaidAsUser()`, `setPackageSsaidAsUser()` |
| 进程/启动 | `isPackageRunning()`, `launchApp()` |

### IFileSystem（20 个方法）

| 领域 | 方法 |
|------|------|
| 文件信息 | `fileType()`, `listDir()`, `calculateSize()`, `exists()`, `length()`, `getParent()` |
| 文件操作 | `mkdirs()`, `delete()`, `move()`, `cleanDir()` |
| 时间戳 | `getLastModifiedTime()`, `setLastModifiedTime()` |
| 所有权 | `getUid()`, `setUid()`, `getGid()`, `setGid()` |
| I/O | `openFile()`, `openInputStream()`, `openOutputStream()`, `createTempFile()` |
| 归档 | `createTarArchive()`, `createTarArchiveForMultiple()`, `extractTar()` |
| FIFO | `mkfifo()`, `isFifo()` |
| 压缩 | `getCompressor()` → `IFileCompressor` |
| 哈希 | `md5()` |

### IServiceClient（455 行基类）

复杂的并发绑定逻辑，包含：
- `CompletableFuture` 异步绑定
- `DeathRecipient` 死亡监听
- 多个 `ServiceConnection` 实现
- `synchronized` 线程安全
- 三个 `bindRemote()` 重载

---

## 六、:hiddenapi 和 :systemapi 模块

### :hiddenapi — 隐藏 Android API 访问

使用 Rikka Refine（`@RefineAs`）在编译时将公开 API 对象转型为隐藏框架类。所有隐藏类抛出 `RuntimeException("Stub!")`，Refine 注解处理器生成实际转型代码。

| 类 | @RefineAs | 隐藏方法 |
|---|---|---|
| `ActivityManagerHidden` | `ActivityManager.class` | `forceStopPackageAsUser()`, `getRunningAppProcessesAsUser()` |
| `PackageManagerHidden` | `PackageManager.class` | `getInstalledPackagesAsUser()`, `grantRuntimePermission()`, `revokeRuntimePermission()` |
| `UserManagerHidden` | `UserManager.class` | `getUsers()` |
| `UserHandleHidden` | `UserHandle.class` | `of(int)`, `getIdentifier()` |
| `AppOpsManagerHidden` | `AppOpsManager.class` | `permissionToOpCode()`, `setMode()` |

### :systemapi — 框架桩类

提供 Android 框架内部类的可编译桩实现：
- **XML 系统**: `TypedXmlPullParser/Serializer`, `BinaryXmlPullParser/Serializer`, `FastXmlSerializer`, `XmlApi30`
- **Settings State**: `SettingsState` 接口 + `SettingsStateApi26`/`SettingsStateApi31` 实现
- **系统属性**: `SystemProperties`（`@FastNative`/`@CriticalNative` 注解）
- **工具类**: `IoUtils`, `HexEncoding`, `XmlObjectFactory`, `HexDump`, `ModifiedUtf8`

---

## 七、JNI 模块分析

### :io-nativefs — 原生文件系统

`libnative-filesystem.so`，3 个 JNI 函数：
- `calculateTreeSize()` — `fts_open/fts_read/fts_close` 遍历目录树
- `getUid()` / `getGid()` — `stat()` 获取所有权

### :io-tar — GNU Tar

`libtar-jni.so`，1 个 JNI 函数 `TarJNI.callCli()`：
- fork 子进程 → dup2 重定向 stdin/stdout/stderr → 调用 GNU tar `main()`
- 3 个静态库：`libgnu.a`（~130 个 C 文件）、`libtar.a`（6 文件）、`tar.a`（22 文件）

### :io-zstd — ZSTD 压缩

内置 zstd-jni，提供：
- `ZstdOutputStream` / `ZstdInputStream` — 流式压缩/解压
- 多线程支持（`setWorkers()`）
- 字典压缩支持

---

## 八、推荐重构路线图

### 阶段 1：止血（1-2 周）
1. **SnapshotViewModel 生命周期修复** — 引入 AppDataRepository，将协程作用域绑定到进程生命周期（P0，优先处理）
2. **消除重复代码** — 抽取 `drawableToBitmap`、`normalizeTarStdErr`（零风险，立竿见影）
3. **删除 AppConfigActivity** — 直接从 Intent 启动 BottomSheet（25 行，改动小）

### 阶段 2：解耦（2-3 周）
4. **构造函数注入改造** — 替换 `SnapshotApp.getInstance()` 静态链，通过 `onAttach` 将 `Providers` 接口传入 Fragment/Adapter
5. **拆分 Fat ViewHolder** — 抽取 GroupActionsController
6. **拆分 ArchiveRestorer** — 按职责分离

### 阶段 3：长期优化（3-4 周）
7. **拆分 IAppManager** — 按领域拆分为 `IPackageManager` + `IPermissionManager`
8. **拆分 AppManagementHandler** — 根服务端职责分离
9. **减少 runBlocking** — 将 `IAppManager`/`IFileSystem` 改为 suspend 或 `CompletableFuture`，优先处理高耗时方法（`installApk`、`createTarArchive`）
10. **引入 Jetpack Navigation** — 统一导航和参数传递

---

## 九、架构亮点（值得保留）

| 设计 | 评价 |
|------|------|
| `:api` 模块零依赖 | 优秀的契约隔离，保持不变 |
| AIDL 仅用于需要异步回调的 `IFileCompressor` | 合理的 IPC 边界选择 |
| JNI fork GNU tar | 聪明的工程决策，利用成熟工具而非重写 |
| FIFO 管道实现流式压缩 | 避免中间文件，内存效率高 |
| Rikka Refine 访问隐藏 API | 优雅的反射替代方案 |
| `AppsListComponent<VB>` 泛型复用 | ViewBinding 共享的好模式 |

---

## 十、不建议做的事

- **不要引入 Compose** — 项目明确选择了 ViewBinding，且 root 应用的 UI 复杂度不需要 Compose
- **不要引入 DI 框架（Koin/Hilt）** — 项目规模不必要，手动构造函数注入 + `ProvidersImpl` 组合根已够用
- **不要重写 JNI 层** — tar/zstd/nativefs 模块工作良好，风险远大于收益
- **不要过度抽象** — 当前 7 个模块的划分已经合理，不要再拆新模块；FIFO 管道差异大，不值得模板化
- **不要一次性重构所有内容** — 按路线图分阶段执行，每个阶段可独立验证
