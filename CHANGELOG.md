# Changelog

本文件记录 AppSnapshoter 各版本的面向用户变更。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Changed

- 应用 Tab 筛选行改为用户 Tab + 系统/用户图标切换 + 搜索图标（`layout_apps_filter_row`），与选应用/忽略应用页共用；`apps_filter_header` 统一水平边距（左 12dp / 右 8dp）
- 筛选图标改用 `FilterRowIcon`（`AppCompatImageButton` + `fitCenter`），修复系统/用户/搜索/展开按钮未居中
- 标签筛选支持单行横向滚动与展开/收起（`TagsFilterLayout`）；Tag Chip 缩小为 22dp / 11sp
- 权限检查对话框与部分 UI 文案抽取为字符串资源（含 `values-zh-rCN`、`values-en`）

### Fixed

- 应用 Tab 标签筛选展开/收起时 `ClassCastException`（`HorizontalScrollView` 子 View 须使用 `MarginLayoutParams`）
- 应用 Tab 标签列表因 `SnapGroup.name` 未初始化导致的 `NullPointerException`（`AppTagHelper` 改为从已加载 `groupList` 取名称）
- JNI `GetStringUTFChars` 未释放导致的 native 内存泄漏（`io-nativefs`）
- Root 服务未连接时 `FileSystemImpl` / `AppManagerImpl` 强制解包 `client` 崩溃
- `FileSystemProviderImpl.provide()` 无限阻塞（`fsmFuture.get()` 增加 10s 超时）
- `TimelineViewModel` 重复 `observeForever` 导致观察者泄漏
- `ArchivedApp.isRunning` 在 `appInfo` 未初始化时访问 `lateinit` 崩溃
- 时间线列表项内容区/展开按钮长按进入多选

## [1.1.0] - 2026-06-18

### Added

- 时间线 Tab：按时间范围跨分组浏览快照，支持多选批量恢复、删除与导出
- 快照目录写入 `.nomedia`，避免图库扫描应用图标缓存
- 悬浮底栏支持分组批量恢复与归档

### Changed

- 刷新主界面 UI（Fluent 2 主题、紧凑顶栏、悬浮毛玻璃底栏；时间线/应用 Tab 支持可折叠搜索）
- 用自定义悬浮底栏替代系统 BottomNavigationView，改善横屏切换时的布局问题

### Fixed

- 添加分组后归档列表不立即显示新分组
- 添加应用到分组后列表与「全部归档」偶发不同步；空分组点击添加应用无法弹出选应用对话框
- 批量还原对话框因损坏的还原记录崩溃
- 设置页缺少顶栏，与主界面风格不一致
- 锁定竖屏，避免旋转屏幕时对话框与进度框丢失
- 多用户环境下额外排除模式文件选择器路径错误
- 分组组头在真机上标题与按钮布局异常、触控区过小

## [1.0] - 2026-05-21

### Added

- 一键存档 / 一键恢复：长按应用快速创建快照并还原
- 基于 JNI 的 TAR + ZSTD 压缩管道，支持 FIFO 流式打包
- APK 智能去重、多存档管理、自定义压缩目录
- 分组管理：按组批量快照与恢复
- Root 服务架构（AIDL + libsu），UI 与 Root 逻辑隔离
- 保留策略与排除策略配置 UI
