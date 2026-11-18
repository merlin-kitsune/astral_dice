# 交接：multi-dev-next 分叉为下一大版本独立开发线（2026-11-18）

> 状态：本文件记录分叉与最后一次主线同步落地后的实读状态。分支 / HEAD / 版本号一律以命令实读为准，
> 本文数字只是 2026-11-18 13:1x 的快照。

## 1. 结论

- `multi-dev-next` 自本次起**与 `multi-main` 分叉**，作为下一大版本的独立开发线；主线发现的 bug **只在本线修复**，
  不再从主线整体合并。主线若要拿本线修复，按单提交拣选（cherry-pick），不做反向同步。
- 分叉前把主线最后一次更改合回本线：合并提交 **`7c37e99f`**（`merge: bring multi-main 1.6.0-pre.1 into multi-dev-next (final sync)`，
  2026-11-18 13:06:28 +0800），父提交 `5582a2a0`（本线）+ `ce6e85f4`（`multi-main`，1.6.0-pre.1 收尾）。
- 五线 `mod_version` 统一 **`26.4-SNAPSHOT.78`**；前置库 **StarEngine `1.2.0-pre.1`**（五平台）。
- 本线当前 `git rev-list --left-right --count multi-main...multi-dev-next` = **0 / 2**（主线无本线缺少的提交）；
  远端 `origin/multi-dev-next` 仍停在 `5120738c`，**尚未 push**（红线：默认不 push）。

## 2. 分支拓扑（实读）

| 分支 | HEAD | 说明 |
|---|---|---|
| `multi-dev-next`（当前） | `7c37e99f` | 独立开发线；本批起点 |
| `multi-main` | `ce6e85f4` | 1.6.0-pre.1 发布线，自本次起**只出不进**（本线修好的东西可拣选回去） |
| `release/1.6.0-pre.1` | `fdd08f9e` | 发布线工作树 `F:/MCProject/astral_dice_release_merge` |
| `multi-26.1.2-neoforge` | `f29f51a3` | 26.1.2 线历史锚点 |

## 3. 本次合并带入了什么

`git diff --stat 5582a2a0 7c37e99f` = **818 files changed, 83608 insertions(+), 568 deletions(-)**，按面拆：

- **版本与前置**：五线 `mod_version` → `26.4-SNAPSHOT.78`；`starengine_version` → `1.2.0-pre.1`（上界 `2.0.0`）。
  这是 `next` 上同一提交换名后的发布裁剪（原 `26.4-SNAPSHOT.13` 的 `f86b9b9`），不是新代码线。
- **UI 定稿**：转账面板玩家名列宽 42 → **52px**（x=24..76），粉色选中箭头 66 → **78**（右缘 91，距滚动条滑块 2px），
  取代 `.77` 构建的 50/77（`26c65b65` + `ce6e85f4` 文档补记）。
- **发布线修复与门禁**：语言修正、工具门禁、AutoJS 用例（`bb9e3d01` Rhino 兼容与断言噪点收敛）、`.github/workflows/build.yml`。
- **追踪化资产**：把主线一直在追踪的 `images/shop/**`（107+68）、`work/**`（recon / fix / shots / evidence…）、
  `porting/fabric-1.20.1`、`docs/archive/**`（含 `card-recipes-2026-10-09` 配方归档与 `work/ysm-prodlaunch-1.21.1.md`）
  一并带入开发线；`立牌计划表.txt` 也随之从「本线未跟踪」变成「已跟踪」（内容来自主线版本，工作区无差异）。
- **规则**：`AGENTS.md`、`docs/agent/AGENTS-dsh.md`、`docs/agent/AGENTS-full.md`、`docs/agent/PROJECT-REFERENCE.md` 同步到发布线版本。
- **CHANGELOG**：中英两份各 +24 行，新增 `## 26.4-SNAPSHOT.78` 段，其中「分支定位」一条写明本次分叉。

## 4. 开发进度现状（本线已落地的大块）

自上一个大版本收尾以来，开发线（`26.4-SNAPSHOT.69` → `.78`）已落地的方向：

- **数据包驱动**：商店卡池 / 筹码池（`5b4a4254`、`91b627d4`）、掉落注入（`ae477745`，两线 loot_modifier 硬编码文件全部退场）、
  星砧（`astral_anvil` 方块/界面/JEI）、五线共享 aegis 数据包（`a3d1571f`）。
- **内容注册与冻结**：内置立牌与筹码先注册再在服务端启动后冻结（`3cd9bd10`、`86b917e4`）。
- **战斗与 API**：StarEngine 四阶段伤害钩子桥接到平台事件（`a2f1cfb0`、`17cf32cb`）、战斗数值钩子与伤害分类
  （`45a9113f`）、`AstralDiceApi` 契约冻结成文档（`5120738c`）。
- `.78` 两线 jar 的增量实测：各 **+62 class / 0 删除**；neo 侧另有 15 个 `loot_modifiers` json 被移除（数据包化后果）。
  新增面集中在 `api/combat`、`data/`、`content/`、`platform/`、`combat/`、`screen/astral anvil`、`compat/jei`、
  `inkshadow` 套装、`z3000_sign`、三张新筹码。

## 5. 本批验证读数

- 五线 `assemble` exit 0（`--offline`，`BUILD SUCCESSFUL in 11s`，28 tasks：18 executed / 5 from cache / 5 up-to-date）。
- 生产件：`neoforge-1.21.1/build/libs/astral_dice-26.4-SNAPSHOT.78+neoforge_1.21.1.jar`
  3,045,823 B / 643 class / sha256 `f47bba8f37a2ffa6de0d00b7d6e43c6b4be25a626d0cf8efb78a0d5927112dd7`；
  `forge-1.20.1/build/libs/astral_dice-26.4-SNAPSHOT.78+forge_1.20.1.jar`
  3,097,777 B / 668 class / sha256 `54b3974bb0fa66c16c309525fdef55488cfea6630a9b81ca8f067066edd4809db`。
  两件 `META-INF/jarjar/` 条目数 **0**（三前置都不许 jarJar）。
- `build-info.json`：`commit=7c37e99f…`、`builtAt=2026-11-18T13:06:36+08:00` ⇒ **构建提交与 HEAD 一致**。
- 本批**未做实机检查**（红线：实机必须用户明确下达）。

## 6. 双仓工作树布局（本次一并确立）

| 工作树 | 分支 | 角色 |
|---|---|---|
| `F:/MCProject/astral_dice_multiloader-next` | `multi-dev-next` | 星之骰戏主开发线（本文件所在） |
| `F:/MCProject/touhou_little_maid_astraldice-next` | `dev-next` | TLMDice（女仆前哨）。下游消费方，前置已钉 `.78` + `1.2.0-pre.1` |
| `F:/MCProject/astral_dice_multiloader` | `multi-main` | 主线工作树（发布线） |
| `F:/MCProject/astral_dice_release_merge` | `release/1.6.0-pre.1` | 发布线工作树 |

TLMDice 侧本次换件要点：`libs/astra/dist/` 归档件由 `.11` 换成 `.78`（`SHA256SUMS` 重钉、`README.md` 整篇重写），
根 `gradle.properties` 四处版本、两子项目 `mod_version`、`AGENTS.md` §1/§2、`tools/install-astra-maven-local.sh`、
`docs/upstream/astra/01-baseline.md` 同批更新；安装脚本已把两坐标装进 `~/.m2` 并按 `SHA256SUMS` 逐件校验通过。

## 7. 已知缺口 / 下一步

1. TLMDice 双线 `build` 本批正在跑（升前置的回归验证），结果回填到其仓 `docs/upstream/astra/01-baseline.md`。
2. 实机冒烟（NeoForge 1.21.1 / Forge 1.20.1）**未做**，等用户下达；测试台端口 25565/25585 需独占。
3. 远端同步：`origin/multi-dev-next` 落后本线 2 个提交，**push 等用户授权**。
4. 工作区仍有用户自有未跟踪产物（`models/*.bbmodel`、`work/audit`、`work/confluence` 等），
   按红线**不删不改不提交**。
