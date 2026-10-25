# 新会话交接上下文（可整份贴给新会话）— 1.5.0-pre.6

你接手的是 MC 模组仓库 `F:\MCProject\astral_dice_multiloader`，分支 `multi-main`，版本 `1.5.0-pre.6`，Windows 原生环境（git bash 只承担 git；构建与测试台一律 PowerShell 7）。

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

再按顺序读：`docs/agent/PROJECT-REFERENCE.md`（工程拓扑与命令）→ `docs/handover-1.5.0-pre.6-main.md`（滚动正本 §0/§5/§6）→ 需要历史批次再回 `docs/handover-1.5.0-pre.5-main.md` → 领域规格按需 `docs/agent/AGENTS-full.md`（行号总表 `docs/agent/AGENTS-TOC.md`）。

## 基本盘

- 四子项目同树：`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`；交付面**只 P0** = 前两条，P1 两线在 `1.5.0` 正式版前不验证、不移植。
- 版本：四线 `mod_version = 1.5.0-pre.6+<加载器后缀>`；`starengine_version = 1.1.0-pre.2`；jar 内硬前置 `[1.1.0-pre.2,2.0.0)`，`META-INF/jarjar/` 计数 0（库**不内嵌**，必须成对投放）。
- 产物：**pre.6 已重建并投放**（2026-10-25 11:55，四目标 sha256 全 MATCH）——neo `astral_dice-1.5.0-pre.6+neoforge_1.21.1.jar` 2,577,145 B / `7995d714…5b5ff9e`；forge reobf 2,622,362 B / `2f14b079…c8f71e9`；forge dev named 2,592,867 B / `40bfe716…6a32f274`（投 `run/1.20.1/mods`）。再改源码后必须重跑 `build`（各线 `build` 已 `finalizedBy pushToDevRun/pushToGame/pushToRootBuild`，一条命令即可重建+投放）并重录 sha256。
- 未推送：本批提交**未 push**、**无 tag**（用户明确「暂不推送」）；push / tag / 实机 / 部署整合包均需用户逐次放行。
- 构建：`JAVA_HOME=C:\Program Files\Zulu\zulu-21`；`.\gradlew.bat :<线>:classes|jar`；forge dev 必须 `build/devlibs` 的 **named** jar。
- 测试台唯一入口：`pwsh -NoProfile -File scripts\test\mt.ps1 --version <线> --phase env|run|stop`；`--purge-saves` 会删 testworld。

## 本批（2026-10-24）已落地

1. 收编 Kimi 四批（此前未提交）：member-detective 筹码 5 张闸门 + bonnie 按标记层数返币；misaki 立牌 epic 化 / 星盘配方 / 死亡保留剑气；筹码商店**删除自动 ticker**、刷新只来自升星 +1（死亡保留）；规则分派（根 `AGENTS.md` 收口 + 两宿主规则文件 + `verify-split.py` 重写 + `AGENT-SPLIT` §六）。
2. DSH：三语语言同步（用户手改 39 条 tooltip/msg 为权威 → 改写 21 条手册 guide 正文 → zh_cn 按 forge 自身键序同步 60 值 → 两线 en_us/ja_jp 各 60 键重译，格式保真全过）。
2. DSH：`tooltip.astral_dice.sign.ame_note`（地雷女备注区）补 7 对染色码（`§e` 数值 / `§9` 时间 / `§d` 回落），正文未改。
3. 版本 bump 到 `1.5.0-pre.6`。
4. DSH：**星光兑换改造**（`1ff14106`，24 文件 +1380/−217）——装备时一次性发放 + 每次兑换后按佩戴补星（`StarLightManager` 4bit 账本严格记账，封顶只记实际量），经商立牌 +50% 保留；新增守门 `tools/verify_starlight_exchange_refill.py`（196 项 PASS）。**已双版本实机验证 5 组判据全 PASS**（读 `docs/handover-1.5.0-pre.6-main.md` §4.1）。

判据：P0 两线 `:classes` `BUILD SUCCESSFUL`；`check_lang_sync` EXIT=0；`audit_patchouli_keys` 悬空 0；`verify-split` EXIT=0；`verify_chip_shop_ui` `CHIPUI_TOTAL_FAIL: 0`；三道专项 python 判据 PASS（26 / 12 / 14 项）。

## 立即待办（按优先级）

1. ~~实机复验~~ → **已完成**（2026-10-25，双版本）：星光兑换 5 组（handover §4.1）+ 筹码商店刷新源 R0–R5 / 38 tooltip + 21 手册文案 / `ame_note` 染色（§4.1b）全 PASS。剩余须用户下达的实机项见 handover §5。
2. **`CHIP-REGEN-1.20.1/1.21.1` 用例退役或改写**（仍断言「等 tick 自动 +1」，已随 ticker 删除失效）。
3. **`tooltip_color_audit` 存量 130 处**（10 键；重灾 `jill_active` 22 / `storm_approaching.description` 18 / `problem_delivery.description` 18 / `dorothy_active` 18）——是否清理待用户裁决；其中 `card.barricade`/`card.repulsion` 的 6 处是**用户自写** `§e20:00§7`，按「tooltip 以用户为准」未动。
4. **8 条「代码与文案都不符」**已报用户、未改 tooltip，等裁决（bite 觉醒层数 / huo_card 选择器 / magic_quiver 闸门 / railgun 真伤口径 / 三张卡 NON_HOSTILE / member_recommendation 只数主背包+副手 / repulsion 单次 5 点耐久 / airbag 掉虚空）。
5. **P1 两线欠账**：JEI 筹码 / `BeamFx` / `GlobalEventGate` / `RepulsionEntity` / `CardShopMenu#planPlacement` / `EventDrawBlock`；筹码图标边框版；史莱姆 20 tick；misaki 未同步；pre.5 冷却指示器未移植。
6. **陈旧锁** `F:\MCProject\.agent-locks\kimi.lock`（`PID=26580` 已不存在）处置待用户裁决。
7. push / tag 待用户放行。

## 禁止项（共同红线，摘自根 `AGENTS.md`）

不自行开实机 / 不部署整合包 / 不擅自 commit·push·打 tag / 不动 `models\` `images\` `sound\` 素材与其他宿主的改动（发现即停手并报告）/ 不删他人的活跃锁（陈旧锁也不自行删，交用户）/ 不代改他人职责。git 远程操作一律走代理 `http://127.0.0.1:7897`。
