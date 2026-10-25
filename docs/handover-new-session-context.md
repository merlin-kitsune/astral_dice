# 新会话交接上下文（可整份贴给新会话）— 1.5.0-rc.1

你接手的是 MC 模组仓库 `F:\MCProject\astral_dice_multiloader`，分支 `multi-main`，版本 `1.5.0-rc.1`（前置库 `1.1.0` 正式版），Windows 原生环境（git bash 只承担 git；构建与测试台一律 PowerShell 7）。

## 第 0 步：认领你自己的规则

先读根 `AGENTS.md`（41 行入口：宿主分派 + 共同红线 + 索引），按其中的表认领规则文件：

| 你的宿主 | 规则正本 |
|---|---|
| Kimi Code | `docs/agent/AGENTS-kimi.md` |
| DSH | `docs/agent/AGENTS-dsh.md` |
| Codex | `docs/agent/AGENT-SPLIT.md`（只读验证） |
| 其他 | 先问用户，**不得**套用上面两份宿主规则 |

## 第 1 步：实读现状（不凭记忆）

```powershell
git -C F:\MCProject\astral_dice_multiloader log -1 --oneline
git -C F:\MCProject\astral_dice_multiloader status --short -uall     # 应只剩 ?? models/astral_anvil.bbmodel（用户素材，禁 add）
Select-String -Path F:\MCProject\astral_dice_multiloader\*\gradle.properties -Pattern '^mod_version|^starengine_version'
```

再按顺序读：`docs/agent/PROJECT-REFERENCE.md`（工程拓扑与命令）→ `docs/handover-1.5.0-rc.1-main.md`（滚动正本 §0/§5/§6）→ 需要历史批次再回 `docs/handover-1.5.0-pre.5-main.md` → 领域规格按需 `docs/agent/AGENTS-full.md`（行号总表 `docs/agent/AGENTS-TOC.md`）。

## 基本盘

- 四子项目同树：`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`；交付面**只 P0** = 前两条，P1 两线在 `1.5.0` 正式版前不验证、不移植。
- 版本：四线 `mod_version = 1.5.0-rc.1+<加载器后缀>`；`starengine_version = 1.1.0`（**正式版**，前置库仓 `F:\MCProject\starengine` 提交 `16b48c4`）；jar 内硬前置 `[1.1.0,2.0.0)`（模板插值自动展开），`META-INF/jarjar/` 计数 0（库**不内嵌**，必须成对投放）。
- 产物：**rc.1 已重建并投放**（2026-10-25 15:3x，行为等价 pre.6；四目标 sha256 全 MATCH）——neo `astral_dice-1.5.0-rc.1+neoforge_1.21.1.jar` 2,586,701 B / `65a22d700926fbddedf5ced803930c9ef74a709426a614a3f3f311e410450610`；forge reobf 2,631,740 B / `7ad926ca072b1c4c9e87218b366da4e70de0746fb2dd218c3ca2b2ff08b2d8dd`；forge dev named 2,602,271 B / `926800675c850d2fdea51bd94439e66f6ec96c766629e2104624907e46878123`（投 `run/1.20.1/mods`）。**前置库 jar 成对换成 1.1.0**（四处旧 `1.1.0-pre.2` 已删）：neo 98,734 B / `62c92595b012929d2fba6098cc2d71e6ab2a9a14ddaac52869e2bbec6b476ac8`；forge reobf 103,605 B / `73da2e599e60dab047e3640eb396f1db6f6eedd5a0dd95494afde471f43af304`；forge dev named 102,013 B / `e280aeab6c4b37c43468f7cce32a8bf64c70b6bdbcd2203f6c5600776f987e0e`。再改源码后必须重跑 `build`（各线 `build` 已 `finalizedBy pushToDevRun/pushToGame/pushToRootBuild`，一条命令即可重建+投放）并重录 sha256。
- 未推送：本批提交**未 push**、**无 tag**（用户明确「暂不推送」）；push / tag / 实机 / 部署整合包均需用户逐次放行。
- 构建：`JAVA_HOME=C:\Program Files\Zulu\zulu-21`；`.\gradlew.bat :<线>:classes|jar`；forge dev 必须 `build/devlibs` 的 **named** jar。
- 测试台唯一入口：`pwsh -NoProfile -File scripts\test\mt.ps1 --version <线> --phase env|run|stop`；`--purge-saves` 会删 testworld。

## 本批（2026-10-24）已落地

1. 收编 Kimi 四批（此前未提交）：member-detective 筹码 5 张闸门 + bonnie 按标记层数返币；misaki 立牌 epic 化 / 星盘配方 / 死亡保留剑气；筹码商店**删除自动 ticker**、刷新只来自升星 +1（死亡保留）；规则分派（根 `AGENTS.md` 收口 + 两宿主规则文件 + `verify-split.py` 重写 + `AGENT-SPLIT` §六）。
2. DSH：三语语言同步（用户手改 39 条 tooltip/msg 为权威 → 改写 21 条手册 guide 正文 → zh_cn 按 forge 自身键序同步 60 值 → 两线 en_us/ja_jp 各 60 键重译，格式保真全过）。
2. DSH：`tooltip.astral_dice.sign.ame_note`（地雷女备注区）补 7 对染色码（`§e` 数值 / `§9` 时间 / `§d` 回落），正文未改。
3. 版本 bump：`1.5.0-pre.6` → **`1.5.0-rc.1`**（进入预发布版），同时前置库 `1.1.0-pre.2` → **`1.1.0` 正式版**（库仓 `16b48c4`；库内 Java 源码零改动），两仓各自提交、四平台 `publishToMavenLocal`、P0 两线重建并投放、**库 jar 与 mod 成对替换**（旧 pre.2 已删）。判据与未做项见 handover §4.1f（**rc.1 已双版本冒烟实机 PASS**，见 handover §4.1f-a）。
4. DSH：**星光兑换改造**（`1ff14106`，24 文件 +1380/−217）——装备时一次性发放 + 每次兑换后按佩戴补星（`StarLightManager` 4bit 账本严格记账，封顶只记实际量），经商立牌 +50% 保留；新增守门 `tools/verify_starlight_exchange_refill.py`（196 项 PASS）。**已双版本实机验证 5 组判据全 PASS**（读 `docs/handover-1.5.0-rc.1-main.md` §4.1）。
5. DSH：**强化拒止改造**（P0 两线）——玩家近战命中力场第 5 次 ⇒ 回收成新物品 `astral_dice:repulsion`（不是效果牌），携带耐久 / 已存活 tick / 已提供充能；再次放置按**与效果牌同一份** `item/RepulsionPlacement.resolve` 恢复三份状态。两线 `:classes` 通过、两线 `runData` 生成模型、`verify_resource_integrity` PASS（157/157）、`check_lang_sync` EXIT 0、三语 + CHANGELOG 双文件已改。**未实机**（判据清单见 handover §4.1c）。
6. DSH：**强化拒止双版本实机**（2026-10-25）：1.21.1 与 1.20.1 各跑 A–G 七项 —— 满耐久不画条 / 近战 5 次回收成物品（耐久 31 + elapsed + charge 3）/ 重放恢复三份状态 / 近寿命到点自行消失 / 非 owner 拒止 / 客户端耐久条 `barWidth 8` + tooltip 九行，**全 PASS**（读数见 handover §4.1c）。
7. DSH：**强化拒止加一层拦截**（2026-10-25）—— `RepulsionEntity#hurt` 增加 owner 闸门：非放置者的近战命中被直接拒绝（红字「这个强化拒止不属于你」，不计命中 / 不播受击反馈），三语 lang 四处与 CHANGELOG 双文件同步改为「放置者近战命中 5 次」；两线编译/打包/投放全绿，**已双版本实机验证四态（放置 / 放置者命中 / 外来 owner 三次全拒 / 无主开放 / 放置者第 5 次回收）全 PASS**（读数见 handover §4.1c「补充拦截 · owner 闸门实机」）。
8. DSH：**手册补充强化拒止物品形态**（2026-10-25，用户裁决「连带补充帕秋莉手册，路障不动」）—— 既有条目 `effect_card_repulsion.json` 追加第 4 页（spotlight `astral_dice:repulsion`：与效果牌共用落点判据、三份状态随物品保存）与第 5 页（获取方式 / 仅获得者 / 悬停三行），两线 × 三语各 +2 键（P0 1058→1060）；`audit_patchouli_keys` 1698 处悬空 0、`check_lang_sync` EXIT 0、`tooltip_color_audit` 仍 144/9 键（路障按裁决保持原样）；产物已重建投放。
6. DSH：**手册修正**（2026-10-25）—— 战斗系统介绍页正文 `2.0.0` → `1.5.0`（两线 × 三语），条目图标 `astral_dice:dice` → `minecraft:iron_sword`（避开骰神赐福）；`PMFIX-TIP-*` 的陈旧断言同步（否则必红）。判据见 handover §4.1d。
9. DSH：**HUD 攻击力显示口径统一**（2026-10-25，handover §4.1g）—— 攻击修饰器链上 23（neo）/ 24（forge）处客户端模板守卫收敛为 `skipOnClient(ctx)` + 新显示口径标记 `DiceCombatContext.displaySnapshot` ⇒ HUD 现在包含**全部**攻击力修饰器（原先只有 10 块），只排除手持武器（`attackDamageExcludingHeldWeapon`）与骰战随机点数（攻击卡在显示口径下不掷）；新增守门 `tools/verify_hud_attack_parity.py`（`GATE: PASS (21 项)`），两线 `:build` 并投放六处，jar 内字节码命中 `skipOnClient`/`displaySnapshot`。**未实机**。

判据：P0 两线 `:classes` `BUILD SUCCESSFUL`；`check_lang_sync` EXIT=0；`audit_patchouli_keys` 悬空 0；`verify-split` EXIT=0；`verify_chip_shop_ui` `CHIPUI_TOTAL_FAIL: 0`；三道专项 python 判据 PASS（26 / 12 / 14 项）。

## 立即待办（按优先级）

0. ~~强化拒止物品形态待实机~~ → **已完成**（2026-10-25，双版本 7 项判据全 PASS，见 handover §4.1c）。手册第 4/5 页已补（§4.1e）；仅剩「实机翻开该条目看排版」待用户下达。
1. ~~实机复验~~ → **已完成**（2026-10-25，双版本）：星光兑换 5 组（handover §4.1）+ 筹码商店刷新源 R0–R5 / 38 tooltip + 21 手册文案 / `ame_note` 染色（§4.1b）全 PASS。剩余须用户下达的实机项见 handover §5。
2. ~~`CHIP-REGEN-1.20.1/1.21.1` 用例退役或改写~~ → **已退役**（2026-10-25 用户裁决，两文件删除）；新语义判据见 handover §4.1b R2/R3/R5。
3. **`tooltip_color_audit` 存量 144 处**（9 键；重灾 `jill_active` 36 / `storm_approaching.description` 18 / `problem_delivery.description` 18 / `dorothy_active` 18）——是否清理待用户裁决。`card.repulsion` 的 6 处 `§e20:00§7` 已在强化拒止批次按规则改成 `§9`（时间值必须 §9），复跑后 repulsion 相关 0 处；`card.barricade` 的 6 处是**用户自写**，仍未动（是否一并改 §9 待裁决）。
4. **8 条「代码与文案都不符」**已报用户、未改 tooltip，等裁决（bite 觉醒层数 / huo_card 选择器 / magic_quiver 闸门 / railgun 真伤口径 / 三张卡 NON_HOSTILE / member_recommendation 只数主背包+副手 / repulsion 单次 5 点耐久 / airbag 掉虚空）。
5. **P1 两线欠账**：JEI 筹码 / `BeamFx` / `GlobalEventGate` / `RepulsionEntity` / `CardShopMenu#planPlacement` / `EventDrawBlock`；筹码图标边框版；史莱姆 20 tick；misaki 未同步；pre.5 冷却指示器未移植。
6. **陈旧锁** `F:\MCProject\.agent-locks\kimi.lock`（`PID=26580` 已不存在）处置待用户裁决。
7. push / tag 待用户放行。
8. **HUD 攻击力口径统一（§4.1g）已静态全绿并投放六处，实机读数待用户下达**（要看的是：戴电流剑等 13 块原被吞的修饰器时 HUD 数字是否随之变化；手持武器换刀剑时数字不变；骰战随机卡点不进 HUD）。

## 禁止项（共同红线，摘自根 `AGENTS.md`）

不自行开实机 / 不部署整合包 / 不擅自 commit·push·打 tag / 不动 `models\` `images\` `sound\` 素材与其他宿主的改动（发现即停手并报告）/ 不删他人的活跃锁（陈旧锁也不自行删，交用户）/ 不代改他人职责。git 远程操作一律走代理 `http://127.0.0.1:7897`。
