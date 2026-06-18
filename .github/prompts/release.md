# AppSnapshoter 发布 Prompt

人类可读的完整说明见 [docs/guides/getting-started/release.md](../../docs/guides/getting-started/release.md)。

将下方「任务 Prompt」整段复制给 AI，或在 Cursor 中 `@.github/prompts/release.md` 引用后说明目标版本（例如「发布 v1.1.0」）。

---

## 任务 Prompt

你是 AppSnapshoter 仓库的发布助手。请根据自上一个 Git tag 以来的变更，撰写更新日志并完成发布准备。

### 仓库约定

- 更新日志文件：`CHANGELOG.md`（[Keep a Changelog](https://keepachangelog.com/zh-CN/) 格式，中文、面向用户）
- 版本号：`app/build.gradle.kts` 中的 `versionCode`（递增整数）与 `versionName`（与 tag 一致，不含 `v` 前缀）
- 发布触发：向 GitHub 推送 `v*` 格式 tag 后，`.github/workflows/release.yml` 自动构建 Release APK 并创建 GitHub Release
- Release 正文：CI 从 `CHANGELOG.md` 提取对应 `## [版本号]` 段落（脚本 `scripts/extract-changelog.sh`）

### 你必须执行的步骤

1. **确定版本号**
   - 列出已有 tag：`git tag -l 'v*' --sort=-v:refname`
   - 若用户未指定版本，根据变更类型建议 semver（`MAJOR.MINOR.PATCH`），并说明理由

2. **收集变更**
   - 找到最新 tag，执行：`git log <最新tag>..HEAD --pretty=format:'%h %s'`
   - 阅读相关 PR/commit diff，区分用户可见功能、修复、内部重构与纯文档/CI 变更

3. **更新 CHANGELOG.md**
   - 将 `## [Unreleased]` 下内容整理后移动到新版本段落，例如 `## [1.1.0] - YYYY-MM-DD`
   - 分类使用：`Added` / `Changed` / `Fixed` / `Removed` / `Deprecated` / `Security`
   - 每条用用户能理解的中文，不写 commit hash；合并同类项，省略仅开发者关心的 refactor/chore/docs（除非影响构建或发布）
   - 在文件顶部保留空的 `## [Unreleased]` 段落供下次累积

4. ** bump 版本**
   - 编辑 `app/build.gradle.kts`：`versionName` = 新版本（无 `v`），`versionCode` 比上一版 +1

5. **本地校验**
   - 运行：`scripts/extract-changelog.sh <版本号>`（不含 `v`），确认能输出非空 Release 正文
   - 可选：`./gradlew assembleRelease` 确认能编译

6. **提交**
   - 仅提交 `CHANGELOG.md` 与 `app/build.gradle.kts`（除非用户要求包含其他文件）
   - commit message 示例：`chore(release): prepare v1.1.0`

7. **打 tag 并推送（需用户明确同意后才执行 push）**
   - `git tag -a v<版本号> -m "Release v<版本号>"`
   - `git push origin main`（或当前发布分支）
   - `git push origin v<版本号>`
   - 推送 tag 后告知用户在 GitHub Actions → Release workflow 查看构建进度，Release 页：https://github.com/TIIEHenry/AppSnapshoter/releases

### CHANGELOG 写作要求

- 面向安装 APK 的用户，不是给开发者看 commit 列表
- 新功能写「能做什么」；修复写「解决了什么问题」
- 同一功能的多条 commit 合并为一条
- 不要编造未实现的变更

### 禁止事项

- 不要 force push
- 不要未经用户确认就 push 或创建 tag
- 不要跳过 CHANGELOG 对应版本段落（否则 CI 会因找不到 Release 正文而失败）
- `versionName`、CHANGELOG 标题、`v*` tag 三者版本号必须一致

### 输出给用户

完成后简要汇报：

- 版本号与 `versionCode`
- CHANGELOG 摘要（3–5 条要点）
- 是否已 push / 打 tag；若未 push，给出用户可自行执行的命令

---

## 快速触发示例

```
@.github/prompts/release.md 请发布 v1.1.0
```

```
@.github/prompts/release.md 根据 main 上自 v1.0 以来的变更建议版本号，写好 CHANGELOG，准备好发布但不要 push
```
