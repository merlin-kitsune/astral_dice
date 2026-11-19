# 审计：主线全量对齐（2026-11-19）

> 状态：本地审计完成；未 push、未发布、未跑实机。设计说明见 [09-mainline-full-parity.md](../design/09-mainline-full-parity.md)。

## 范围

审计对象是主模组 `F:/MCProject/astral_dice_multiloader` 与库 `F:/MCProject/starengine` 在 `feature/mainline-full-parity` 分支上的阶段 1–5 改动：P1 去重、A/B 内容归属、C 常量拆分、API 全量、pre.2 版本收口。基线 tag 为 `mainline-parity-baseline-2026-11-19`。

## 证据

| 项 | 命令 / 方法 | 结果 |
|---|---|---|
| 版本键 | 实读两仓五线 `gradle.properties` | 主模组 `1.6.0-pre.2+<loader>`；库 `1.2.0-pre.2+<loader>`；主模组 pin `1.2.0-pre.2` |
| 库发布 | `./gradlew clean publishToMavenLocal --offline --no-daemon` | `BUILD SUCCESSFUL in 13s`；五份 m2 jar 均含 47 个 `api/astral` 条目 |
| 主模组构建 | `./gradlew clean assemble --no-daemon --no-configuration-cache` | `BUILD SUCCESSFUL in 11s`；34 任务（27 执行 / 7 缓存） |
| 门面计数 | `javap` 库 `AstralDiceApi` / `AstralDiceService` | 103 个 `public static` / 101 个 `abstract` |
| 旧 API 清理 | 五份主模组 jar 内 `unzip -l` 计数 | 旧 `astral_dice/api/AstralDiceApi.class`、旧 `event/api/AstralEvent.class`、旧 `choice/CardChoiceType.class` 全为 0 |
| 新实现 | 五份主模组 jar 内 `unzip -l` 计数 | `apiimpl/AstralDiceServiceImpl.class` 均为 1 |
| build-info | 解包 Forge 1.20.1 / NeoForge 1.21.1 jar | `commit=1d376ae0ad9a453d62fb565702a918aacfd0a0df`；classCount 681 / 657 |
| 基线干净 | `git status --short` 两仓 | 仅版本文件在版本提交前修改；提交后 tracked 干净，未跟踪仅历史 `work/**`、日志等 |
| 远程边界 | 未执行 `git push` / tag / 发布 | 无远程写入 |

## 关键计数

- 库 API：38 个物理文件 / 37 个逻辑类。根 2、combat 20、choice 1、client 3、data 3、event 6 物理（5 逻辑，含 `AstralEvent` 双变体）、playerlike 3。
- 主模组旧 API：39 个物理文件 / 15 个唯一类，全部删除。
- 目标独有 22 类：19 个从 dev 移植，3 个 `CombatResolution*` 实现并接线；0 个 defer。
- 门面缺口：旧 31 个 `public static`（29 唯一名）到 103 个；净差 72，按唯一名补齐 74 个（含重载）。
- 内容归属：18 个 effect、`Rarity`、五线 `AstralRarities` 已从库搬到主模组；25 个内容常量与 `cardCostForStar` 已拆到 `AstralGameplayConstants`。

## 产物

| 线 | 大小 (B) | SHA-256 |
|---|---:|---|
| fabric-1.20.1 | 3,362,853 | `b90eb47080de757a96b27b191def9305fc595a7ff21afc4a6c2e2a001314944b` |
| fabric-1.21.1 | 3,399,442 | `b4f71bee4acfed3aebc8e9ae5a39e8ecc50ad9b22ff9ab5482b620ce1a61fcf0` |
| forge-1.20.1 | 3,121,422 | `93c2580eb62e537c61e054188afbba38c1dfc27efa1622eb8f4505b5ef614b46` |
| neoforge-1.21.1 | 3,071,135 | `f63fee78050201ae2f991ec93c47e42c9861a934b34cd14b2f476e0f7b75b2d4` |
| neoforge-26.1.2 | 3,290,021 | `8f3adab0a47f92466b7775be6c78e9a6cae19a59cdf695f2fb5c2c2dc07c3910` |

库 jar 大小：fabric-1.20.1 692,160 / fabric-1.21.1 804,405 / forge-1.20.1 620,190 / neoforge-1.21.1 659,974 / neoforge-26.1.2 822,378 B。

## 未覆盖

- 未启动实机客户端/服务端；CombatResolution 监听器、PlayerLike 转账/交付、CardChoiceType 运行期匹配、事件 GUI 均未做行为验证。
- 未验证第三方消费方；库的 `TargetType` / `HostileTargets` 包迁移只同步了主模组。
- 未 push、未打 tag、未上传 CurseForge；上述产物是本地预发布候选，不是发布版本。

## 结论

阶段 1–5 的代码、版本、文档与五线构建在本地通过；API 面、旧包清理、服务安装与产物类清单与设计一致。因未做实机冒烟与远程发布，本审计只能证明「本地可编译、可组装、类清单正确」，不能证明「运行期无回归」或「可发布」。
