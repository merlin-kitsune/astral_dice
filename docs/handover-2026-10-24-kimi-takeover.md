# Kimi 接手交接 — 2026-10-24（multi-main @ 1.5.0-pre.6）

> 本文件是 **Kimi Code** 的接手单，**每批新建、不覆盖旧交接**。规则正本是 `docs/agent/AGENTS-kimi.md`；公共分派与共同红线见根 `AGENTS.md`；工程拓扑见 `docs/agent/PROJECT-REFERENCE.md`。
> 事实源原则：版本号 / HEAD / 未提交改动一律按**实读命令**，下面每个数字都是 2026-10-24 13:3x 的快照。

## 0. 一句话现状

四线已 bump 到 `1.5.0-pre.6`；Kimi 上一轮（`member-detective` / `misaki` / `筹码商店刷新源` / `规则分派`）的 47 个文件**已由 DSH 收编提交**，DSH 另完成三语语言同步与 `ame_note` 染色修复，全部已 commit、**未 push**。P0 两线编译绿、守门全绿；`build/libs` 与整合包**仍是 pre.5 产物**（本批未重建 jar）。

## 1. 必读顺序（实读，不凭记忆）

1. `git -C F:\MCProject\astral_dice_multiloader log -1 --oneline` + `git status --short -uall`（当前应只剩 `?? models/astral_anvil.bbmodel` 这条用户素材）。
2. `docs/agent/AGENTS-kimi.md`（你的规则正本：Git Bash 优先 / 长上下文全委派 / 版本迭代必建新交接）。
3. `docs/agent/AGENTS-dsh.md`（DSH 侧的规则与红线；**不是**你的，但冲突时以用户裁决为准）。
4. 本文件 §2–§5 → `docs/agent/PROJECT-REFERENCE.md`（拓扑与命令）→ `docs/handover-1.5.0-pre.6-main.md`（滚动正本；pre.5 及更早历史在同目录 `docs/handover-1.5.0-pre.5-main.md`）。
5. 领域规格按需：`docs/agent/AGENTS-full.md`（行号总表 `docs/agent/AGENTS-TOC.md`）、`scripts/test/TESTING-SPEC.md`、`docs/compat-1.5.0-porting.md`。

## 2. 本批已落地（2026-10-24，一次提交 `e2aed527`，53 文件）

### 2.1 Kimi 上一轮四批（此前未提交，已收编）

| 批次 | 关键点 | 判据 |
|---|---|---|
| member-detective | `item/chip/MemberRecommendationChipItem.java` 新增 `HAND_CARD_LIMIT = 5`：装备判断后、`giveCardTo` 前按**主背包 items + 副手**计数早退（**不含 Curios 卡牌栏**）；`item/sign/BonnieSignItem.java` 主动改为 隐匿调查 → `int markLevel = MarkManager.apply(target)` → 按施加后层数 `markLevel * 2` 返币 | `python tools/verify_member_recommendation_hand_gate.py` = PASS 14 项；`python tools/verify_bonnie_active_order.py` = PASS 12 项 |
| misaki 立牌 | 两线 `MISAKI_SIGN` `legendary()`→`epic()`；配方 `GOLDEN_STAR_PLATE`→`STAR_PLATE`（datagen + 生成 JSON 双向）；`PlayerLifecycleHandler` 删除死亡清 `MISAKI_SIGN_STACKS`（剑气留物品组件，`clearSignData` 卸下清零保留）；`astral_rews.json` `LEGENDARY`→`EPIC`（unitWorth 10000 未动） | `python tools/verify_misaki_balance.py` = PASS 26 项 |
| 筹码商店刷新源 | 两线**删除** `shop/ChipShopRegenTicker.java`（自动回满作废）；`needsReroll` 去 `diceKey` 比对；新增 `withRefreshGranted()`（`Math.max(0, Math.min(REFRESH_MAX, refreshesLeft+1))`，不重置）；唯一授予点 `event/AnvilUpgradeHandler`（取走升星成品 +1，封顶 5，升星花费 0/1/2★=15/20/25 与 preview 共用）；死亡保留：neo `.copyOnDeath()` / forge `AstralData` 白名单加 `ModAttachments.CHIP_SHOP_STATE.name()` | `pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1` = `CHIPUI_TOTAL_FAIL: 0` / `CHIPUI_RESULT: OK`（新增 G18c..G18h） |
| 规则分派 | 根 `AGENTS.md` 收为 41 行公共入口（2,452 字符）；新建 `docs/agent/AGENTS-dsh.md`（旧根逐字节留存 + 开头 DSH 专用段）与 `docs/agent/AGENTS-kimi.md`；`docs/agent/verify-split.py` 重写为四项（快照保序包含 / AGENTS.md 结构完整 / docs 引用有效 / 体量+驻留）；`AGENT-SPLIT.md` 追加 §六 Kimi 兼容说明 | `python docs/agent/verify-split.py` = EXIT 0（四项 [OK]，原「索引无空洞」判据随根文件重构一并替换） |

### 2.2 DSH 本轮三件

1. **三语语言同步**（用户 m07951 下达，`zh_cn.json` 已由用户手改）：
   - 用户手改 **39 条**（38 条 `tooltip.astral_dice.*` + `msg.astral_dice.card_shop_no_starlight`）是**事实权威**，tooltip 一字未被我改动。
   - 按其新语义改写 **21 个手册正文键** `astral_dice.guide.entry.*`（atm_chip.1 / battle_system_changes.3 / bonnie_sign.1 / candy_chip.1 / abyss_reagent_chip.1 / sensor_chip.1 / integration.3 / komachi_sign.2 / lulu_sign.1 / magic_tome_chip.1 / misaki_sign.3 / ninja_star_chip.1 / piercing_gun_chip.1 / rin_sign.1 / member_recommendation_chip.1 / perpetual_motion_chip.1 / piggy_bank_chip.1 / airbag_chip.1 / extra_battery_chip.1 / ame_sign.2 / lingling_sign.2）。原则：用户删掉的括注视为**简化**而非纠正，只要代码仍成立就保留 guide 精度（repulsion 窄口径、railgun 下限 5 点、padman 含护甲值、airbag 无敌致死、electric_glove 延迟 1 秒且含主目标…）。
   - `zh_cn.json` 同步到 `forge-1.20.1`：**按 forge 自身键序只替换 60 个值**（不许整文件复制 —— forge 键序在 index 955 起与 neo 分叉：`block.astral_dice.barricade_collision` vs `block.astral_dice.chip_shop`，且 forge 有 6 行缺 2 空格缩进、保留用户删掉的 1 个空行）。
   - `en_us.json` / `ja_jp.json` 两线各 **60 键**重译，格式保真逐键校验：§ 码 234/234（相对位置一致）、`%%` 10/10、`%s` 6/6、\n 39/39、`" | "` 3/3。
2. **`tooltip.astral_dice.sign.ame_note`（地雷女立牌备注区）染色修复**：备注区由 `event/ModTooltipHandler.java` 的 `addSignNoteLines` 强制 `§d` 底色（并把行内 `§7` 替换成 `§d`），原 7 处数值/时间是裸文本 ⇒ 补 `§e`（数值）/ `§9`（时间）/ `§d`（回落），**正文一字未改**（仅删「层数 +1 ，」逗号前多余空格）。6 个文件（两线 × 三语）。
3. **版本 bump**：四线 `mod_version = 1.5.0-pre.6+<加载器后缀>`；`starengine_version` 仍 `1.1.0-pre.2`。

## 3. 判据读数（2026-10-24 13:3x 实测，可直接复跑）

| 命令 | 读数 |
|---|---|
| `gradlew.bat :neoforge-1.21.1:classes :forge-1.20.1:classes` | `BUILD SUCCESSFUL in 4s`，EXIT=0 |
| `pwsh -NoProfile -File tools\check_lang_sync.ps1` | EXIT=0；四线 key 全一致（1046 / 1046 / 836 / 844）；**3 条既有 WARN**（`dorothy_passive` zh 10 vs ja 14 颜色码、`jill_active` zh 14 vs en/ja 10） |
| `python tools/audit_patchouli_keys.py` | 引用 1,694 处，悬空 **0** |
| `python docs/agent/verify-split.py` | EXIT=0（四项 [OK]；若 docs 指针缺失只判 WARN，`--strict` 才致命） |
| `python tools/verify_misaki_balance.py` / `verify_bonnie_active_order.py` / `verify_member_recommendation_hand_gate.py` | PASS 26 / 12 / 14 项 |
| `pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1` | `CHIPUI_TOTAL_FAIL: 0` / `CHIPUI_RESULT: OK` |
| `pwsh -NoProfile -File scripts/audit/tooltip_color_audit.ps1` | 违反 **130**（本批 ame_note 由 40 → 0；EXIT 仍 1，存量欠账见 §4-3） |

⚠️ `build/libs` 里 **仍是 pre.5 产物**（2026-10-23 17:10：neo 2,570,968 B / forge 2,616,502 B），本批**只跑 classes 未打 jar**；`run/<线>/mods` 与两个 P0 整合包也仍指向 pre.5。⇒ **实机/交付前必须先 `:jar` 重建 pre.6 并重投**。

## 4. 遗留（接手第一件事就是这批）

1. **pre.6 产物未建未投**：`:neoforge-1.21.1:jar :forge-1.20.1:jar` → 记 sha256 → 投 `run/1.21.1/mods`、`run/1.20.1/mods`（forge 用 `build/devlibs` 的 **named** jar）、`D:\.minecraft\versions\狐の航空学 Voxy Edition\mods`、`D:\.minecraft\versions\1.20.1-Forge 模组测试\mods`，**成对**带 `starengine-*-1.1.0-pre.2.jar`。
2. **未实机**：本批全部为静态判据 + 编译，没有开过客户端。筹码商店刷新源改造（ticker 删除）与语言改动都需一轮实机；`scripts/test/cases/CHIP-REGEN-*.json` 仍描述「摆形态→等 tick→自动 +1」，**已失效**，须退役或改写。
3. **`tooltip_color_audit` 存量 130 处**（10 键）：`jill_active` 22 / `storm_approaching.description` 18 / `problem_delivery.description` 18 / `dorothy_active` 18 / `red_heat_warning.description` 16 / `fanny_active` 12 / `jill_passive` 8 / `card.barricade` 6 / `card.repulsion` 6 / `deepseek_passive` 6。其中 `card.barricade` / `card.repulsion` 的 6 处是**用户自己写的 `§e20:00§7`**（规则上时间应为 §9），**按「tooltip 以用户为准」未动**，等用户裁决。
4. **8 条「代码与新旧文案都不符」**（已报用户，**未改 tooltip**）：bite 觉醒按撕咬张数非 1 层 / huo_card 选择器 = `CREATURE_OR_RIVAL`（含未驯服可驯服生物）/ magic_quiver 无敌对判据（真闸门 = 目标已有标记 + 先用过伤害效果牌）/ railgun 真伤只 `bypasses_armor` / 三张卡 `berserk`·`express_delivery`·`luxury_feast` 选择器是 `NON_HOSTILE` / member_recommendation 只数主背包+副手 / repulsion 单次结算最多扣 5 点耐久 / airbag 掉虚空救不了（guide 已补注）。
5. **P1 两线欠账**（`1.5.0` 正式版前不验证不移植，仅登记）：JEI 筹码 / `BeamFx` / `GlobalEventGate` / `RepulsionEntity` / `CardShopMenu#planPlacement` / `EventDrawBlock`；另 misaki 在 P1 仍 `legendary()` + 黄金星盘配方 + 死亡清零剑气。
6. **push / tag**：本批**未推送**（用户明确「暂不推送」），无 tag。
7. **陈旧锁 `F:\MCProject\.agent-locks\kimi.lock`**（`PID=26580` / `TIME=2026-10-24T12:07:28+08:00` / `TASK=misaki-chip-shop-balance-p0`，进程已不存在）。按规则**不自行删除**，交用户裁决 —— 你接手后请先问用户，或直接以本轮自己的 `kimi.lock` 覆盖并说明。
8. **Kimi 原计划 Task 2/3/4 状态**：Task 1（根 AGENTS.md 收口 + 两宿主规则文件 + AGENT-SPLIT §六）已完成并提交；verify-split.py 已重写通过；`docs/agent/PROJECT-REFERENCE.md` 与 `docs/handover-2026-10-24-kimi-takeover.md`（本文件）由 DSH 于本轮补齐，`docs/agent/README.md` 已同步一行 → **原计划实际已闭环**，无需重做。

## 5. 踩过的坑（避免重复）

1. **`git show HEAD:<file>` 输出是 LF**（仓库内存 LF，工作树 CRLF 由 autocrlf 转换）⇒ 行级改写前先 `replace(/\r\n/g,"\n").split("\n")`，再 `join("\r\n")`，否则会把文件写成 LF。
2. **forge 与 neo 的 lang 键序不一致**（见 §2.2）⇒ 禁止整文件复制；一律「按目标文件自身键序、只换值」。
3. **`tools.edit` 插多行会把整文件转 LF**（历史事故）⇒ 大文件改写用 node `fs` 全量读改写。
4. **子代理对 `temp/` 无写/删权限**（沙箱），只能由主会话用 `pwsh` 清；`.gitignore` 里 `temp/` 是任意层级匹配，残留物不会进 `git status`。
5. **`ChipShopState.withRefreshGranted` 不能写成重置**（`min(MAX, left+1)`），且**死亡必须保留**（否则重生等于变相补满）；forge 侧靠 `AstralData` 白名单，不是 `copyOnDeath`。
6. **`MemberRecommendationChipItem` 的 5 张闸门只数主背包 + 副手**，别把 Curios 卡牌栏算进去（用户文案口径即如此）。
7. 仓库根曾有误建目录 `%SystemDrive%\ProgramData\...`（Windows 路径拼接事故，已删）；`undefined/temp/` 下有 2026-10-23 的历史临时脚本残留（被 `temp/` 规则忽略），未清理，勿当源码。

## 6. 你开工前

1. 按 `docs/agent/AGENTS-kimi.md` 的锁流程处理 `kimi.lock`；**先 `git status --short -uall`**，只应看到 `?? models/astral_anvil.bbmodel`（用户素材，**禁 add**）。
2. 需要新交接时**新建文件**（`docs/handover-<日期>-kimi-<主题>.md`），不要覆盖本文件与 DSH 的正本。
3. 默认不 push / 不打 tag / 不实机 / 不部署整合包 —— 全部等用户逐次放行。
