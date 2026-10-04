# 多版本分支矩阵 / Version Branch Matrix

> **历史文档**:2026-08-29 起仓库已迁移为 multiloader 单仓,分支矩阵被子项目矩阵取代
> (`neoforge-1.21.1` ← 本文档 `1.21.1-main` 分支;`forge-1.20.1` ← `1.20.1-forge` 分支)。
> 迁移映射与现行结构见 `docs/multiloader-layout.md`;两版本 API 差异审查仍见 `docs/compat-1.20.1-forge.md`。
> 下文保留作为原 `astra_dice` 仓库 branch-per-version 架构的记录。
> **注意**:下文「分支修改默认规则」为迁移前的历史规则,现行规则已改为**功能/修复默认同步修改两个子项目**(见 AGENTS.md「子项目修改默认规则」)。

本仓库采用 **branch-per-version(分支即版本)** 架构维护多个 Minecraft 版本的 Astral Dice。

## 分支矩阵

| 分支 | MC 版本 | 加载器 | Java | 构建插件 | 版本号格式 | 产物命名 |
|---|---|---|---|---|---|---|
| `1.21.1-main` | 1.21.1 | NeoForge 21.1.x | 21 | `net.neoforged.moddev` | `x.y.z[-rcN]` | `astral_dice-1.1.2-rc1.jar` |
| `1.20.1-forge` | 1.20.1 | Forge 47.4.x | 17 | `net.neoforged.moddev.legacyforge` | `x.y.z[-rcN]+1.20.1` | `astral_dice-1.1.2-rc1+1.20.1.jar` |

## 统一构建入口(所有分支一致)

- `gradlew build` — 编译 + 部署(双目标:`run/<版本>/mods` 版本隔离开发目录 + 各自的整合包测试目录);旧 jar 清理按**版本后缀隔离**:
  - 1.21.1 分支清理 `astral_dice-*.jar`(不带 `+` 后缀);
  - 1.20.1 分支仅清理 `astral_dice-*+1.20.1.jar`,不会误删 1.21.1 产物。
- `gradlew runData` — 数据生成(datagen)。
- `python tools/check_lang_sync.ps1` — 语言文件同步检查(CI 构建前强制执行)。
- `deploy.ps1` — 版本递增(支持 `+MC` 后缀,递增仅作用于主干)+ 构建 + 本地提交。

## 分支修改默认规则

- **功能/修复默认只更新 `1.21.1-main`**:所有功能、修复、平衡性调整一律先在 1.21.1 分支实施。
- **仅在用户明确要求更新 `1.20.1-forge` 时才同步更新 1.20.1**:未经指示不切换到 1.20.1-forge 分支、不移植/cherry-pick;两个分支的同一变更不得同时实施(先 1.21.1,用户要求后再同步)。
- 用户在 1.20.1 测试时报告的 BUG/需求,默认先在 `1.21.1-main` 修复,是否同步需先询问用户。
- 各分支差异(如联动模组 ID 不同)记录在 AGENTS.md「多版本分支矩阵」章节与 `docs/compat-1.20.1-forge.md`。

## 新增版本分支检查清单

1. 从 `1.21.1-main` HEAD 新建分支 `<mcversion>-<loader>`(如 `1.20.1-forge`)。
2. 改造 `build.gradle` / `gradle.properties`:
   - 构建插件(MDG / MDG legacyforge / ForgeGradle)、Java toolchain、依赖坐标(Curios/Iron's/KubeJS 按版本选择 Modrinth maven 版本 id);
   - `mod_version` 追加 `+<mcversion>` 后缀;部署目录按版本区分;`staleJarMatcher` 只清理本分支后缀的产物。
3. 移植源代码(参照 `docs/compat-1.20.1-forge.md` 的差异审查清单)。
4. 数据包目录名按目标版本规范调整(1.21+: `recipe/advancement/loot_table`;1.20.1: `recipes/advancements/loot_tables`),`data/neoforge` ↔ `data/forge` 及前缀同步;`pack.mcmeta` pack_format 按版本(mcmeta 参考:1.20.1=15)。
5. 模组元数据:`neoforge.mods.toml` ↔ `META-INF/mods.toml`(loaderId/loaderVersion 相应调整)。
6. CI(`build.yml`):JDK 版本按分支矩阵条件化;触发分支追加新分支名。
7. `deploy.ps1`:`+MC` 后缀已通用,无需修改。
8. AGENTS.md 分支矩阵表追加一行;CHANGELOG 增加新分支首版条目。
9. 验证:`python tools/check_lang_sync.ps1` + `gradlew build`;游戏内行为验证由用户手动执行(禁止自动 runClient)。
10. 本地提交;**不自动 push**(遵守「编译产物上传规则」第 6 条)。
