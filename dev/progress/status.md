---
title: "当前开发状态"
type: progress
status: active
updated: 2026-10-23
summary: "快照安全修复已集成；123 项单元测试、8 项隔离设备测试、双 ABI Debug 构建和 lint 通过"
---

# 当前开发状态

> 最后更新：2026-10-23

## 当前阶段

v1.3.x 稳定性加固；本轮修复集成于 `codex/fix-snapshot-safety`。

## 已完成功能

- [x] 一键存档/恢复
- [x] TAR + ZSTD 流式压缩管线
- [x] APK 智能去重
- [x] 多存档管理
- [x] 自定义压缩项
- [x] 分组管理（创建、排序、配置）
- [x] 时间线视图（v1.2）
- [x] 批量恢复/删除/导出
- [x] Syncthing 同步支持
- [x] 文档系统建设（docs/ + dev/ 两层体系）
- [x] 主界面 UI 焕新（Fluent 2 主题、紧凑顶栏、悬浮毛玻璃底栏、可折叠搜索）
- [x] 应用 Tab 筛选行图标化（系统/用户切换 + 标签展开/收起）
- [x] 崩溃修复批次（标签 LayoutParams、AppTag NPE、JNI 泄漏、Root 服务空指针、Timeline 观察者泄漏等）
- [x] 应用 catalog loading SSOT（`isAppsLoading` + `isAppsCatalogLoaded`；未拉取保持 loading；可见时有限次退避重试）
- [x] 分组 body 三态可见性（空组优先加号；有应用才折/展）
- [x] 分组集（`archiveList` 投影、集折叠块、两级排序、底栏长按快跳含拖选）
- [x] 分组集强调色（预设 + 自定义；投影快照刷新）
- [x] 选择应用 BottomSheet：打开重置过滤、「未分组」开关
- [x] 统一添加分组/集 BottomSheet；集内色条与一键折叠
- [x] 存档列表打磨：SetHeader 吸顶 overlay、整行按压无 ripple、组内网格超过 3 行自滚

- [x] 分组集折展性能 Phase A（`loadedGroups`/`loadedSets` 工作集；折展/一键折叠只再投影，不扫盘）
- [x] 快照安全发布：暂存目录、元数据写入失败传播、内容校验、发布后卸载
- [x] 恢复预检与选择性目录恢复；共享 APK 高级恢复；额外普通文件暂存与精确替换
- [x] 删除分组保留原始路径、删除失败保留登记；重新登记现有目录保留配置
- [x] Root 目录清理保留远程文件语义并检查结果；删除失败保留共享 APK
- [x] ZSTD 恢复改用可靠匿名管道；JNI 描述符关闭；Root 文件服务断连后重新解析服务
- [x] 分组刷新转 repository IO；时间线本地日期与夏令时边界；程序化 Chip 同步不弹日期选择器

## 本轮验证

- 单元测试：123 项通过，0 失败、0 跳过；新增 34 项回归用例。
- 构建：arm64-v8a / x86_64 Debug APK、provider 隔离测试 APK 均已生成。
- 设备测试：Android 16 上 8 项全部通过，覆盖真实跨进程 Binder PFD 往返、生产端失败、tar 提前退出、padding 排空、无效 FD、普通文件归档和符号链接清理。
- 国际化：三份 locale 各 397 项；缺项、格式参数和调用参数检查均通过。
- 静态检查：`:app:lintDebug`、`:provider:lintDebug` 通过；保留既有警告。

设备用例直接通过 `adb shell am instrument` 执行，仅使用 `snapshot.provider.appmanager.test` 的缓存目录；测试结束后已卸载该隔离测试包。

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :provider:assembleDebugAndroidTest
adb install -r -t provider/build/outputs/apk/androidTest/debug/provider-debug-androidTest.apk
adb shell am instrument -w -r -e class tiiehenry.android.snapshot.provider.filesystem.ReliablePipeTransferTest snapshot.provider.appmanager.test/androidx.test.runner.AndroidJUnitRunner
adb uninstall snapshot.provider.appmanager.test
```

## 进行中

_无活跃开发任务_

## 已知问题

- 恢复预检后的设备写入失败不提供整组应用数据事务回滚，见 [ADR-002](../decisions/002-snapshot-operation-safety.md)。
- Root 服务死亡后重连的完整应用端到端验收仍待 Root 设备验证；当前设备用例使用隔离测试进程验证描述符管线。

## 下一步

- [架构加固计划](../plans/2026-08-arch-hardening.md)（planning）：继续处理[架构审查报告 2026-08](../../docs/architecture/review-2026-08.md) 中的任务取消契约、压缩侧 FIFO 失败退出和 root 边界收口；恢复侧已改用匿名管道
- （可选）时间线条目显示「集名 / 分组名」；折展 Phase B（展开绑定）若真机仍掉帧再开
- 真机验：时间线跳转折叠集内分组（`requestNavigateToGroup`）
