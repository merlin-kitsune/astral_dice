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
- 产物：`build/libs` 目前仍是 **pre.5**（2026-10-23 17:10）⇒ 实机/交付前必须 `:jar` 重建 pre.6 并重投四个目标（两个 `run/<线>/mods` + 两个 P0 整合包 `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods`、`D:\.minecraft\versions\1.20.1-Forge 模组测试\mods`），每处核 sha256。
- 未推送：本批提交**未 push**、**无 tag**（用户明确「暂不推送」）；push / tag / 实机 / 部署整合包均需用户逐次放行。
- 构建：`JAVA_HOME=C:\Program Files\Zulu\zulu-21`；`.\gradlew.bat :<线>:classes|jar`；forge dev 必须 `build/devlibs` 的 **named** jar。
- 测试台唯一入口：`pwsh -NoProfile -File scripts\test\mt.ps1 --version <线> --phase env|run|stop`；`--purge-saves` 会删 testworld。

## 本批（2026-10-24）已落地

1. 收编 Kimi 四批（此前未提交）：member-detective 筹码 5 张闸门 + bonnie 按标记层数返币；misaki 立牌 epic 化 / 星盘配方 / 死亡保留剑气；筹码商店**删除自动 ticker**、刷新只来自升星 +1（死亡保留）；规则分派（根 `AGENTS.md` 收口 + 两宿主规则文件 + `verify-split.py` 重写 + `AGENT-SPLIT` §六）。
2. DSH：三语语言同步（用户手改 39 条 tooltip/msg 为权威 → 改写 21 条手册 guide 正文 → zh_cn 按 forge 自身键序同步 60 值 → 两线 en_us/ja_jp 各 60 键重译，格式保真全过）。
3. DSH：`tooltip.astral_dice.sign.ame_note`（地雷女备注区）补 7 对染色码（`§e` 数值 / `§9` 时间 / `§d` 回落），正文未改。
4. 版本 bump 到 `1.5.0-pre.6`。

判据：P0 两线 `:classes` `BUILD SUCCESSFUL`；`check_lang_sync` EXIT=0；`audit_patchouli_keys` 悬空 0；`verify-split` EXIT=0；`verify_chip_shop_ui` `CHIPUI_TOTAL_FAIL: 0`；三道专项 python 判据 PASS（26 / 12 / 14 项）。

## 立即待办（按优先级）

1. **重建并投放 pre.6 产物**（`:jar` → sha256 → 两个 `run/<线>/mods` + 两个 P0 整合包，成对带 starengine 库）。
2. **实机复验**（须用户下达）：① 筹码商店「摆形态不再自动回满 / 升星取走 +1 / 死亡不自愈」；② 39 条 tooltip + 21 条手册文案的换行与染色；③ `ame_note` 备注区观感。
3. **`CHIP-REGEN-1.20.1/1.21.1` 用例退役或改写**（仍断言「等 tick 自动 +1」，已随 ticker 删除失效）。
4. **`tooltip_color_audit` 存量 130 处**（10 键；重灾 `jill_active` 22 / `storm_approaching.description` 18 / `problem_delivery.description` 18 / `dorothy_active` 18）——是否清理待用户裁决；其中 `card.barricade`/`card.repulsion` 的 6 处是**用户自写** `§e20:00§7`，按「tooltip 以用户为准」未动。
5. **8 条「代码与文案都不符」**已报用户、未改 tooltip，等裁决（bite 觉醒层数 / huo_card 选择器 / magic_quiver 闸门 / railgun 真伤口径 / 三张卡 NON_HOSTILE / member_recommendation 只数主背包+副手 / repulsion 单次 5 点耐久 / airbag 掉虚空）。
6. **P1 两线欠账**：JEI 筹码 / `BeamFx` / `GlobalEventGate` / `RepulsionEntity` / `CardShopMenu#planPlacement` / `EventDrawBlock`；筹码图标边框版；史莱姆 20 tick；misaki 未同步；pre.5 冷却指示器未移植。
7. **陈旧锁** `F:\MCProject\.agent-locks\kimi.lock`（`PID=26580` 已不存在）处置待用户裁决。
8. push / tag 待用户放行。

## 禁止项（共同红线，摘自根 `AGENTS.md`）

不自行开实机 / 不部署整合包 / 不擅自 commit·push·打 tag / 不动 `models\` `images\` `sound\` 素材与其他宿主的改动（发现即停手并报告）/ 不删他人的活跃锁（陈旧锁也不自行删，交用户）/ 不代改他人职责。git 远程操作一律走代理 `http://127.0.0.1:7897`。
