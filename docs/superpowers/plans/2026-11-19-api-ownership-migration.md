# API 归属迁移（dev-next 先行）实施计划

> **For agentic workers:** 按任务顺序执行；每个任务以可核验的构建/读数收尾。

**Goal:** 把 astral 主模组 38 个对外类型与门面全部搬进 StarEngine（api.astral.*），主模组只留实现。

**Architecture:** 库侧门面 `AstralDiceApi` 静态委托 `AstralDiceService`；主模组 `AstralDiceServiceImpl` 承载原方法体；平台接口搬库、五线实现只实现不定义。

**Tech Stack:** Java 21 / Gradle / 五线（neoforge-1.21.1、forge-1.20.1、neoforge-26.1.2、fabric-1.20.1、fabric-1.21.1）。

## Task 1：修 fabric 跨区依赖区间 + 记录约定

- [ ] fabric-1.20.1/gradle.properties:42、fabric-1.21.1/gradle.properties:32 的 `starengine_version_range` 改为 `>=26.4-SNAPSHOT.15 <27.0.0`（与库口径一致；.15 在 Task 2 产出后成立）。
- [ ] 在 docs/features/api-audit/00-master-audit.md 增加 §10「API 归属约定」。
- [ ] 验证：`grep -n starengine_version_range` 两线与 fabric.mod.json 展开一致。
- [ ] 提交（astral）。

## Task 2：库侧建 api.astral.* 并发布 26.4-SNAPSHOT.15

- [ ] git mv 37 个契约类 + 平台接口到 `common/src/main/java/com/merlinkitsune/starengine/api/astral/<子包>`，package 行与相互 import 同步。
- [ ] 新增 `AstralDiceService`（100 方法）与门面 `AstralDiceApi`（install/service/委托）；摘除 platform() 与弃用重载。
- [ ] `CardChoiceType.matches` 改为委托门面；`AstralEvent` javadoc 去模组链接。
- [ ] 五子项目 lib_version → 26.4-SNAPSHOT.15；`gradlew.bat publishToMavenLocal --offline`（JAVA_HOME=Zulu 21）。
- [ ] 验证：BUILD SUCCESSFUL；~/.m2 五个 artifact 有 .15；jar 内含 api/astral 各子包。
- [ ] 提交（starengine_next）。

## Task 3：主模组切库、只留实现

- [ ] 删除 `common/.../api/**` 与五线 `api/choice`、`api/event` 旧文件；全仓 import 重写为 `starengine.api.astral.*`。
- [ ] 生成 `common/.../apiimpl/AstralDiceServiceImpl.java`（原 100 个方法体，实例化）与五线平台实现改实现库接口。
- [ ] 五线 `AstralDiceMod` 初始化安装 Service；`freezeContent` 调用点不变。
- [ ] 验证：`./gradlew.bat assemble --offline` 五线成功；jar 内无 `astral_dice/api/`，有 Service 实现。
- [ ] 提交（astral）。

## Task 4：版本 .94 + 文档 + 发布

- [ ] 五线 mod_version → 26.4-SNAPSHOT.94；CHANGELOG 双语补段（破坏性：import 全改）。
- [ ] docs/features/api-audit/00-master-audit.md 补 §10 验证读数；starengine_next CHANGELOG 补 .15。
- [ ] `publishToMavenLocal` 两件（neo/forge）；记录 sha256 与 build-info。
- [ ] 提交（astral / starengine_next）。

## Task 5：TLMDice 级联

- [ ] gradle.properties：astraldice_version/astral_dice_version → 26.4-SNAPSHOT.94（neo 带 +neoforge_1.21.1），starengine_version → 26.4-SNAPSHOT.15；mod_version → .89。
- [ ] import 改 `starengine.api.astral.*`；libs/astra/dist 换 .94 两件 + SHA256SUMS；README 溯源更新。
- [ ] 两线 assemble 成功；docs/upstream/astra/01-baseline.md:3 改 .94/.15。
- [ ] 提交（TLMDice）。

## 验证清单（全部任务完成后）

- 五线 `jar tf` 读数；TLMDice 两线 build 读数；三仓 git log 三条提交；无跨区 import（grep `astral_dice.api` 应为空）。
