# 交接文档 — 筹码商店批（`2.0.0-SNAPSHOT.97` → 落地于 `.98`）

> 生成：2026-10-12 22:00（Asia/Hong_Kong）｜工作树 `F:\MCProject\astral_dice_multiloader-next`｜分支 `multi-dev-next`
> **本批提交 `6c81b7fe`**（`feat(chip-shop): land the Chip Shop batch on both P0 lines with in-game PASS (four lines .97)`，192 文件）；**本交接文档随 `.98` 提交落地**（文档提交同样升版，见 §0）
> 四线版本：`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1` = **`2.0.0-SNAPSHOT.98`**（同号；以四份 `gradle.properties` 与 `git log -1` 实测为准，勿照抄）
> 上游常态交接：`docs/handover-2.0.0-dev-next.md`｜新会话提示词（可整份复制）：`docs/handover-new-session-context.md`
> 本批设计正本：`docs/chip-shop-logic-plan.md`（§1..§12，Q1–Q17 全部裁决落定）；界面正本：`docs/chip-shop-ui-spec.md`；平台差异正本：`docs/compat-1.20.1-forge.md` **§十七**

---

## 0. 一页速览

| 项 | 值 |
|---|---|
| 功能状态 | **P0 两线（`neoforge-1.21.1` + `forge-1.20.1`）已实现并对等**；**P1 两线（`neoforge-26.1.2` / `fabric-1.20.1`）尚未移植** |
| 实机状态 | **1.21.1 五条用例全 PASS**（机器行判据）；**forge 线无用例、未实机**（探针 `c*` 命令也尚未移植到 forge 线） |
| 静态判据 | `scripts/verify/verify_chip_shop_ui.ps1` → `CHIPUI_TOTAL_FAIL: 0` / `CHIPUI_RESULT: OK`（两线）；`scripts/verify/mutate_chip_shop_ui.ps1` → `MUT_TOTAL: 37，判红 37` / `MUT_RESULT: OK` |
| 产物 | `neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` 的 `build/libs` 已有 `…SNAPSHOT.97+<平台>.jar`（`.98` 只改文档 ⇒ 见 §4「产物与版本的错位」） |
| ⚠️ fabric | `fabric-1.20.1/build/libs` 最新仍是 `…SNAPSHOT.49+fabric_1.20.1.jar`（2026-10-08 16:55）—— **`.50`..`.98` 的 bump 都没重跑 fabric 构建**（`mt_build.ps1` 不认识 `fabric-1.20.1`，该线走 `scripts/test/fabric/ft_build.ps1`） |
| 未终结事项 | §4 共 9 条（P1 移植 / forge 实机欠账 / fabric 产线缺档 / 守门 6 条盲区 + F 系补变异 / P2 锚点语义裁决 / 报告里的历史红标 / `.97` 实机时点 / 仓库根 4 个垃圾目录 / 旧遗留） |
| 提交纪律 | 每次提交升版（四线同号）+ 两份 CHANGELOG 段头同步；**默认不 push** |

---

## 1. 本批做了什么（流程逐条，便于复核「有没有跳步」）

> 流程正本 = `AGENTS.md`〈子代理与测试环境硬规则〉**第 7 条**（2026-10-12 用户裁决）：① 先写设计计划 → ② **P0 基准线由主 Agent 亲自写** → ③ 自测（编译 / 守门）→ ④ `code_review` 复核 → ⑤ **派子代理做独立功能审查** → ⑥ 主 Agent 逐条回应、缺陷直接修复 → ⑦ 才派子代理做 `forge-1.20.1` 移植，移植线再走 `code_review` + **另派一个**独立子代理功能审查 → ⑧（实机须用户下达）。

1. **设计正本**（`.90`~`.92` 提交）：`docs/chip-shop-logic-plan.md` 27 个问题（Q1–Q17 含子项）全部与用户裁决、冻结后才开工；界面骨架 `.86`（`bb48a206`）先落 13 张贴图 + 三语 4 键 + 守门脚本。
2. **1.21.1 实现**（主 Agent 亲自写）：`shop/ChipShopCatalog|Roller|State|Opener|RegenTicker|Menu`、`block/ChipShopBlock`、`screen/ChipShopScreen`、`client/ChipShopClientState`、三个网络包（neo：`network/ChipShop{Buy,Refresh,Deny}Payload` + `ModPayloads`）、`component/ModAttachments`、`item/CurioSlotUtil` 的 `forEachCurioStack` 收敛、方块与 BlockItem 注册、创造栏条目、资源（blockstates / 物品模型 / 配方 / 掉落表 / 61 张筹码图标 / 三语 7 键）。
3. **`code_review` 复核（4 批：A / B1 / B2 / C）**：结论「可进入移植」，其中 B2 的 S8（**配方撞车**）被判 REFUTED 并已修（`chip_shop` 与 `card_shop` 归一化后逐字节相同、原版「首个匹配即返回」⇒ 一个方块静默不可合成；现 `chip_shop` 中排为 `B S B`（`B=star_coin`、`S=blank_chip`），并新增守门 **G22** 专治此病）。
4. **独立子代理功能审查（1.21.1）**：静态取证 + 逐条对账，指出了「守门过松」清单 ⇒ 主 Agent 把 G21 拆成 G21b 并按线分支，新增 **G22..G27**（配方撞车 / DataSlot 与拒绝码 / 星等来源 / 无折扣无标题 / 语义锚定）。
5. **`forge-1.20.1` 移植（一次性子代理，DeepSeek）**：按 `docs/compat-1.20.1-forge.md` 差异映射落地；随后 **`code_review` 复核**（14 条判据全 SUPPORTED/符合，唯一 P2 = 变异覆盖偏窄）+ **另一个独立子代理功能审查**（结论「可进入实机」）。
6. **复核结论落地（主 Agent）**：G21b 改**按线分支**（1.21.1 查 `codec()`+`useWithoutItem`、forge 查六参 `use` 且禁止 1.21.1 专有字面）；G21/G22 改读**本线实际生效目录**（forge 复数 `recipes/`+`loot_tables/`、neo 单数 `recipe/`+`loot_table/`）；删掉移植线为骗守门而造的「假台账常量」`BLUEPRINT_1211_CODEC_DECL`；变异脚本新增 forge 侧 M27–M36；7 处陈旧 javadoc 修正。
7. **实机（用户下达）**：1.21.1 五条用例全 PASS（§3）；收尾 `--phase stop --force --purge-saves` 已执行；`--phase report --allow-auto` 已出报告。
8. **提交**：四线升版 `.96`→`.97` + 两份 CHANGELOG 段头 + `AGENTS.md` 版本句 → 三条线构建 → `git commit`（`6c81b7fe`，**未 push**）。

---

## 2. 功能语义正本（改代码前必读，全部有字面判据）

**常量**（`shop/ChipShopCatalog.java:43-76`，守门按字面校验）：`OFFER_COUNT = 3`；品质码 `TIER_RARE/EPIC/LEGENDARY = 1/2/3`；`QUALITY_PROB`（按骰子星阶 0/1/2/3★）= `{0.60,0.37,0.03}` · `{0.60,0.37,0.03}` · `{0.40,0.45,0.15}` · `{0.25,0.50,0.25}`；`SCHOOL_ALPHA = 0.60`（已装备同流派 N 枚 ⇒ 权重 ×(1+0.60N)）；`CATEGORY_ALPHA = 2.0`、`SLOT_DECAY = 0.25`（首个格子 ×2、第 2/3 格 ×1.25）；`PRICE_INITIAL/STEP/MAX = 10/5/100`；`REFRESH_INITIAL/REFRESH_MAX/REFRESH_REGEN_TICKS = 3/5/36000`（36000 tick = 30 分钟）；`ICON_DIR = "textures/gui/chip_shop/chips/"`。

**池**：61 枚（`POOL = List.of(...)`，顺序 = 计划表 §1）；品质分布 **稀有 19 / 史诗 24 / 传奇 18**；流派 `STARLIGHT/HEALING/MARK/CHARGE/GENERIC` 各 9/9/8/10/25；类别 `ATTACK/REINFORCE/SUPPORT/HEAL/ECONOMY` 各 24/15/8/7/7。图标 = `assets/astral_dice/textures/gui/chip_shop/chips/<chip_id>.png`（61 张，32×32，源素材 `images/shop/unbord_chips/*.png` 按 id 重命名入库）。

**价格**：`price = min(100, 10 + 5 × purchasedCount)`，三格同价、**无任何折扣路径**（Q13）；`purchasedCount` 永久落盘。

**重抽的三个入口（唯一）**：① 进店补抽（`ChipShopOpener.ensureOffers`，仅当 `needsReroll` 为真：候选为空 / 数量不符 / 含已拥有）；② 刷新（`ChipShopMenu.handleRefresh`）；③ 购买成功分支。菜单构造只**只读载入**，不得掷骰。

**刷新**：初始 3、上限 5；**第一次成功刷新时**才把锚点 `regenAnchor` 写成 `now + 36000`（0 表示从未刷新过 ⇒ 不恢复）；每 36000 tick +1 并把锚点顺延；已达上限则**不顺延**（`if (anchor <= 0L || state.refreshesLeft() >= ChipShopCatalog.REFRESH_MAX)`，`shop/ChipShopRegenTicker.java:43`；块内 `continue` 跳过 +1）。玩家离线也走 `ServerTickEvent`（按世界时间）。

**购买**（`ChipShopMenu.handleBuy`）校验序：归属 → 索引范围 → 该格有候选 → 余额（`StarEngineEconomy`）→ 找空位 → 扣款 → 写物品栏 → `purchasedCount += 1` 并记类别 → 重抽落盘 → 同步余额 → actionbar → **`closeContainer()` 是方法体最后一条语句**（`forge-1.20.1/.../shop/ChipShopMenu.java:371`）。

**拒绝码**（`ChipShopMenu`）：`DENY_NONE=-1`、`DENY_NO_COIN=0`、`DENY_NO_REFRESH=1`、`DENY_INVENTORY_FULL=2`；三条拒绝**只有 actionbar + S2C 包、没有服务端日志** ⇒ 实机判据必须是「状态未变 + 无购买行」，不能找 deny 日志。⚠️ **服务端 `denyReason()` 恒返 -1**（`return player.level().isClientSide() ? clientDenyReason : DENY_NONE;`）⇒ 探针在服务端读到的 `deny=` 字段**不是**拒绝证据。

**开店拒绝**：`ownsEverything`（已集齐 61 枚）或物品栏无空位 ⇒ `ChipShopOpener.open` 直接拒绝并给提示，**不开店**（⇒「先填满物品栏再开屏」在实机上根本到不了购买路径）。

**方块**：满格碰撞盒（模型装饰件高过 17/16，贴合无意义）+ 四向 `FACING`（`getStateForPlacement` 用 `getHorizontalDirection().getOpposite()`）；1.21.1 需 `CODEC = simpleCodec(...)` + `codec()` 覆写（**只有编译 1.21.1 才暴露**）。


---

## 3. 判据与读数（全部可复算）

### 3.1 实机（1.21.1，机器行）

| 用例（`scripts/test/cases/`） | 规模 | 结果 |
|---|---|---|
| `CHIP-LOGIC-1.21.1.json` | 58 步 / 34 断言 | **PASS** |
| `CHIP-REGEN-1.21.1.json` | 38 步 / 11 断言 | **PASS** |
| `CHIP-CLIENT-BUY-1.21.1.json` | 19 步 / 10 断言 | **PASS** |
| `CHIP-CLIENT-REFRESH-1.21.1.json` | 19 步 / 10 断言 | **PASS** |
| `CHIP-CLIENT-DENY-1.21.1.json` | 19 步 / 10 断言 | **PASS** |

- 入口：`pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase cases --case cases/<名>.json`（**整目录 `run-dir` 被闸门禁用**，且用例目录里有 73 条 `*-1.21.1.json`，**不要**整目录跑）。
- 断言通道：客户端探针行 → `logs/kubejs/client.log`（`source=kubejs_client`）；服务端探针 `ctx.source.sendFailure` 行 → 以 `[CHAT]` 落 `latest.log`（`source=latest`）。
- 覆盖：池 61 与三档分布 / 进店 `needsReroll` / 价格曲线 0→10·1→15·18→100·30→100 与菜单同值 / 开屏数值与三格品质码 / 关店重开候选恒定 / 刷新 3→2 + 首次消耗才起算锚点 / 真购买（扣款 1000→990、物品栏 +1、`purchasedCount+1`、候选重抽、关店）/ 索引 -1 与 3 不扣款 / 物品栏满开店被拒 / 空物品栏负控开店成功 / 边界序列 `cseq` 五段 / 方块朝向与右键接入 / 刷新恢复五形态（due 恢复、max 不恢复、zero 不恢复、first 首次消耗才起算、pend 被立即补回）/ 客户端真发包购买（770）/ 真发包刷新与第二次点击只红闪（771）/ 屏内填满物品栏后确认 ⇒ `deny=2` 红闪不扣款（772）。

### 3.2 静态守门与变异（两线）

```powershell
pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1            # 无参 = 两线
pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1 forge      # 只查移植线
pwsh -NoProfile -File scripts/verify/mutate_chip_shop_ui.ps1            # 变异（37 条，约 5 分钟）
```

- 守门判据 = **`CHIPUI_TOTAL_FAIL: 0` + `CHIPUI_RESULT: OK`**；判据组：`G1..G6` 界面骨架（贴图逐字节 / 布局常量 / 禁拖拽与唯一确认入口 / 三语键 / MenuType / 36 玩家槽 / 客户端绑定）+ `G9..G21` 逻辑批（池 61 逐行对账 / 图标 61 逐字节 + 32×32 / 类别与品质码 / 价格曲线 / `G11..G20` 语义）+ `G21b` 方块朝向语义（**按线分支**：neo 查 `simpleCodec`+`useWithoutItem`，forge 查六参 `use` 且**禁止**出现 1.21.1 专有字面）+ `G22` shaped 配方撞车 + `G23..G27`（DataSlot 与拒绝码 / 星等来源 / 无折扣无标题 / 语义锚定）。
- 变异判据 = **`MUT_TOTAL: 37，判红 37` + `MUT_RESULT: OK`**，每条跑完按 SHA256 校验字节还原（输出里应有 37 行 `MUT_RESTORE: …`）。37 条 = `$mutations` 35 条 + `$swaps` 2 条；按 `file` 配平 neo **24** / forge **13**。**退出码 2 = harness 自身失败**（锚点没命中 / 文件缺失 / 还原不符 ⇒ 该条判据根本没被注入，与「漏判」分开报告，禁止当判红）。

### 3.3 构建、收停与报告

- 构建（bump 之后才 build）：`mt_build.ps1 --version 1.21.1` = `MT_BUILD: OK (2s) … SNAPSHOT.97+neoforge_1.21.1.jar`；`--version 1.20.1` = `OK (9s) … SNAPSHOT.97+forge_1.20.1.jar`；`--version 26.1.2` = `OK (2s) … SNAPSHOT.97+neoforge_26.1.2.jar`。⚠️ `mt_build.ps1` **不认识 `fabric-1.20.1`**。
- 收停（用户硬规则，必须带 `--purge-saves`）：`MT_CLEANUP_SAVES: PURGED 1` / `MT_CLEANUP_RESULT: OK` / `MT_STOP: OK (1s) — 收停完成，无本流程残留进程`（同时清掉 `run\1.21.1\saves\testworld`）。
- 报告：`--phase report` **会被批量编排闸门拦下**（stderr `MT_AUTO_DISABLED: --phase report（收集自动跑出来的结果） —— 已被禁用。`、退出码 2）⇒ 要出报告需加 **`--allow-auto`**（`MT_ALLOW_AUTO=1` 等效）。加开关后：`MT_REPORT: OK — …\scripts\test\reports\20260921-191522\1.21.1\report.md（结论 FAIL）`。
- ⚠️ **报告总判 FAIL 的成因**：报告按「本轮世代」（`gen=20261009-181536`）聚合 `scripts/test/cases/.mt_run_state.json` 里的**条目级 PASS/FAIL 平面映射**（无时间戳）；本批五条筹码用例**全 PASS**，FAIL 只来自同世代里两条与本批无关的历史红标 `SHOP-M-1.21.1` / `DIAG-1.21.1`。处置见 §4-6。


---

## 4. 未完成 / 待裁决（按「需要什么才能继续」写）

1. **P1 两线移植（工作量最大）**：把 `.61`~`.97` 全批（13 事件体系 + 事件卡 GUI + 事件选择界面 + 分阶图标与计时器 + 发币漏斗 + 手册/tooltip 文案 + 卡牌商店方块 + **筹码商店整批**）移植到 `neoforge-26.1.2` 与 `fabric-1.20.1`。已核：两线目录下**根本没有** `client/EventCardOverlay.java`、`block/CardShopBlock.java`、`shop/ChipShopCatalog.java` 等 ⇒ 属**整体新建**；差异映射见 `docs/compat-26.1.2-neoforge.md` 与 `porting/fabric-1.20.1/FABRIC-DIFFS.md`（fabric 侧另有 Loom 内嵌库前置，见 `AGENTS.md` 的 fabric 规则边界）。
2. **forge 线没有实机**：需要 ① 把服务端探针的 `c*` 命令族（见 §5 速查）与客户端 770/771/772 场景移植到 `scripts/test/resources/kubejs/1.20.1/**` —— 注意 1.20.1 探针的 `sp_closeMenu` / `SP_ItemStack` / 六参 `use(...)` 形态与 neo 不同（`cblock` 的右键路径要按六参 `use` 重写）；② 复制 5 条用例为 `CHIP-*-1.20.1.json`（`version` 字段改 `1.20.1`，`--phase launch` 会自动同步探针到 `run/1.20.1/kubejs/`）；③ 再走「用户下达 ⇒ env/launch/case ⇒ stop --purge-saves」。**这是本批登记在案的欠账**，不得据此声称「两线已实机对等」。
3. **fabric 线产物缺档**：`.49` 之后从未重跑（见 §0 表）。要不要补跑（`scripts/test/fabric/ft_build.ps1`）**需用户裁决**；若补跑，fabric dev 侧先 `ft_env.ps1 --side both --install-embedded`。
4. **守门 6 条盲区 + F 系补变异（审查建议，未动手）**：① 越线字面只覆盖 `block/ChipShopBlock.java` 且只 2 个字面（`verify_chip_shop_ui.ps1:636` 的 `$bforbid`、`:619` 的 `$bsrc`）⇒ Payload / ModAttachments / State 混入 1.21.1 API 照样绿；② G22 不校验 T/M 材料值；③ G23 只数 DataSlot 站点数与顺序、不校验下标常量数值；④ G6 只查子串 `chip_shop`；⑤ **无网络包 id 唯一性/顺序/方向判据**；⑥ 无跨线值比对。建议补成 forge 侧变异并成对补判据的六条：F1 `ChipShop` 三条移到 `CardShop` 之前、F4 附件键名与 `.sync()`、F6 删 `anchor <= 0L ||`、F8 删 `ownsEverything` 分支、F13 `DATA_PRICE` 下标、F14 `HIDDEN_X` 常量。
5. **P2 待用户裁决（计划表 §11 Q2 遗留）**：刷新**已达上限**时锚点停在过去 ⇒ 玩家花 1 次刷新后被**下一 tick 立即补回**（对资源恢复而言偏「免费」）。忠实于 §6 字面，但与「资源恢复」意图相悖；最小修法 = 刷新成功后 `anchor = Math.max(anchor, now + REFRESH_REGEN_TICKS)`。实机已用 `cregen CG pend` 取证（`CREFRESH:refreshes=5->4` 紧接 `CST:refreshes=5/5`）。

6. **报告里的历史红标**：`SHOP-M-1.21.1` 与 `DIAG-1.21.1` 的 FAIL 标记残留在 `scripts/test/cases/.mt_run_state.json` 的**当前世代**里 ⇒ 报告总判恒为 FAIL。处置选项：重跑这两条（若已修好会自动转绿）或走世代轮转（`scripts/test/mt_report.ps1:246-286`）。**未动手**。
7. **`.97` 产物的实机时点**：五用例读数对应的产品源码与提交内容一致；其后只改过**注释 / 文档 / 版本号 / 测试资产**（forge `ChipShopCatalog` 注释纠错、compat §十七、两个守门脚本头部、`ChipShopBlock` 注释引用、四线版本号、探针与用例）。按「改动后旧实机证据作废」的严格口径，**若要求 `.97`/`.98` 产物自身的实机证据，需重跑一轮**（约 15 分钟）。
8. **仓库根 4 个垃圾未跟踪目录**：`%SystemDrive%/ProgramData/Microsoft/Windows/Caches/*.db`（字面量 `%SystemDrive%`，某进程按 cwd 写入未展开路径所致；内容 = Windows 字体缓存）。**未删、未纳入提交**；因此 dev 树 `git status --short -uall` 恒有这 4 行。
9. **更早的遗留（与筹码批无关，仍在账上）**：`sound/` 约 21 个 wav 未转 ogg；`docs/handover-2.0.0-dev-next.md` §5-B 的 5 条设计待裁决（高亮与选中共用同一素材 / 冷却 180s 与面板 15s 的关系 / `tier<=0` 退回基础图无实机取证 / card_shop 顶面道具簇正向判定 / 1.20.1 dev 里 Patchouli 手册手持物紫黑格）。

---

## 5. 复现 / 继续的精确命令

```powershell
# 0) 先确认基线（勿照抄任何哈希/版本号）
git -C F:\MCProject\astral_dice_multiloader-next status --short -uall
git -C F:\MCProject\astral_dice_multiloader-next log --oneline -3

# 1) 静态判据（不需要游戏）
pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1          # 期望 CHIPUI_TOTAL_FAIL: 0 / CHIPUI_RESULT: OK
pwsh -NoProfile -File scripts/verify/mutate_chip_shop_ui.ps1          # 期望 MUT_TOTAL: 37，判红 37 / MUT_RESULT: OK（约 5 分钟）
pwsh -NoProfile -File scripts/test/mt_build.ps1 --version 1.21.1 --timeout 300

# 2) 实机（必须用户明确下达；收尾一律带 --purge-saves）
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase env
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase launch
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase cases --case cases/CHIP-LOGIC-1.21.1.json
# …再逐条 CHIP-REGEN / CHIP-CLIENT-BUY / CHIP-CLIENT-REFRESH / CHIP-CLIENT-DENY
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase report --allow-auto
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase stop --force --purge-saves
```

**探针速查（服务端 `/astralshop <cmd> <tag> [参数]`，机器行前缀 `AP_<tag>_*`）**：`creset`（把附件重置为全新初始态 —— **用例自洽的关键**，跨用例/跨轮次残留全靠它清）/ `cpool` / `cstate` / `cprice <n>`（**有副作用**：会把 `purchasedCount` 设成 n，用完记得 `cprice <tag> 0` 复位）/ `copen`（开店并**立即自关屏**）/ `copenkeep`（留屏）/ `cmenu` / `cstable` / `crefresh` / `cbuy <idx>` / `cinv fill|clear` / `cdeny` / `cseq` / `cblock` / `cregen <due|max|zero|first|pend>`。

**哨兵与 tag 对照**：客户端探针只在**已武装**（客户端可见余额 ∈ [770,799]）且**当前屏是 `ChipShopScreen`** 时跑场景：770 = 购买（tag `CB`）、771 = 刷新（tag `CR`）、772 = 物品栏满（tag `CD`）；服务端用例用 tag `CL`（逻辑）/ `CG`（恢复），余额固定 1000（∉ [770,799]，**故意不让客户端探针武装**）。

**用例 JSON 约定**：`case_id` / `title` / `version` / `target:{type,ref}` / `on_fail` / `steps[]` / `asserts[]`；步骤 op = `note` / `wait(ms)` / `inject_command(command,no_esc:true)`（**命令带前导斜杠**；`no_esc` 必填 `true`）/ `inject_key` / `inject_mouse` / `screenshot(tag,mode)`；断言 = `log` / `absent` / `crash`（**`log` 只判「命中 ≥1」，没有计数断言** ⇒ 每条判据都要设计成单行可判）。文件保持 **CRLF + 末尾换行 + 无 BOM + 2 空格缩进**。

---

## 6. 本批新增的坑（逐条带判据，都是真踩过的）

1. **筹码屏的命中坐标是「条带内相对」**：`ChipShopScreen` 的 `CELL_X={3,45,88,131,174}` / `CELL_Y=0` / `CELL_H=STRIP_H=78` / `CONFIRM_X=11,Y=36` / `REFRESH_X=181,Y=36` / `LABEL_*` 全部相对条带，命中判定走 `isHoveringStrip → stripX/stripY`（再叠加 `STRIP_X=34` / `STRIP_Y=6` 与 `leftPos/topPos`）。探针若少加偏移，`mouseClicked` 会**落空但仍返回 true**（`ret=1` 是假信号）⇒ 实测表现为 `sel` 恒 -1、确认键空转、屏不关。正确中心：筹码格 (99,45)/(142,45)/(185,45)、标签框 (99,74)/(142,74)/(185,74)、确认键 (57,54)、刷新键 (227,54)。
2. **`inject_command` 走聊天通道，界面开着会被吞**：容器屏（筹码屏/卡牌屏）开着时注入的命令**不执行且不报错** ⇒ 屏内只能由客户端探针用 `csCmd`（`mc.player.connection.sendCommand`，**不带前导斜杠**、GUI 开着也能发；772 用例的 `cinv fill` 与关屏回读的 `cstate` 都靠它）。
3. **购买成功后服务端同拍关店 ⇒ 屏内的 `step N` 读数永远来不及跑**：`closeContainer()` 是 `handleBuy` 最后一条语句，客户端下一 tick 就进关屏分支、场景机被复位。正解 = 在**关屏那一拍**补落读数（客户端态是**类级静态**、不随屏销毁），并在同一拍用 `csCmd` 回读服务端 `AP_<tag>_CST`。
4. **跨用例/跨轮次状态泄漏**：附件（候选 / `lastPickCategory` / `purchasedCount` / `refreshesLeft` / `regenAnchor`）与**世界里的方块**都会留存 ⇒ 用例必须自带 `creset`；`cblock` 的 `set=1` 判据在二次运行时因方块已在位变成 `set=0`，需在用例里先 `/setblock ~2 ~ ~ air` 清位（探针 `sp_doChipBlock` 用 `player.blockPosition().offset(2,0,0)`，命令侧 `~2 ~ ~` 等价）。
5. **服务端 `denyReason()` 恒为 `DENY_NONE`**：`player.level().isClientSide() ? clientDenyReason : DENY_NONE` ⇒ 探针在服务端读到的 `deny=` 不是拒绝证据；三条拒绝也无服务端日志 ⇒ 判据只能是「状态未变 + 余额未变 + 无购买行」。
6. **`run_code` 自身有 120s 硬 deadline**：`timeoutMs` 必须传给 `run_code`（只传给内层 `tools.pwsh` 无效）；链式跑多条用例会被拦腰掐断（本批踩过：LOGIC 第 85/90 行被杀）。长任务改后台 job + 轮询。
7. **`--phase report` 被批量编排闸门禁用**（`MT_AUTO_DISABLED`、退出码 2）⇒ 加 `--allow-auto`；报告总判 FAIL 可能只反映**同世代的历史标记**（`.mt_run_state.json` 无时间戳平面映射）。
8. **`mt_build.ps1` 只认 `1.21.1 / 1.20.1 / 26.1.2`**：`--version fabric-1.20.1` 直接 `MT_ERROR: 未知版本`；fabric 走 `scripts/test/fabric/ft_build.ps1`。
9. **变异 harness 的行尾陷阱（已修）**：脚本内锚点用 LF 拼，而 forge 线源码/资源是 CRLF ⇒ 多行锚点 `IndexOf = -1`、「变异根本没注入」被静默当成一条结果。现已在比对前按**目标文件自己的行尾**归一化（`ConvertTo-FileEol`），并把 harness 失败（锚点/文件/还原）与「守门漏判」分桶（`MUT_HARNESS` + `exit 2`）。新增 **M37** 用多行 CRLF 锚点长期守住这条。
10. **测试环境的第三方引脚会漂移**：用户升级整合包后 `mt_env.ps1 mods` 会 `MT_MODS: BLOCKED — 整合包缺少 *modernfix-neoforge*X*`（本批实测 5.27.24 → **5.27.26**）⇒ 同步改 `scripts/test/mt_env.ps1:48` 与 `AGENTS.md` 测试环境表那一行。
11. **工具调用侧两个硬性小事**：`tools.list_agents` / `tools.job_list` 等**必须传空对象**（`await tools.list_agents({})`），否则报 `ToolCallError: binding arguments must be lossless JSON`；`tools.edit` / `tools.write` 改**已存在**文件前必须先用 `read` 工具读过（`fs.readFileSync` 不算），否则报 `file has not been read`。
12. **写多行锚点/插入块要按目标文件的行尾来**：本仓 `neoforge-1.21.1` 的 Java 与 `docs/handover-2.0.0-dev-next.md` 这一族是 **LF**；`forge-1.20.1` 的 Java、`scripts/test/resources/kubejs/1.21.1/*.js`、`docs/compat-1.20.1-forge.md`、`AGENTS.md` 是 **CRLF**（`git config core.autocrlf=true`，提交时归一化，故 diff 不会整文件重写）。

---

## 7. 变更清单与关键路径

**本批提交**（`6c81b7fe`，192 文件 / 6143 插入 / 140 删除）：

- **产品代码（两线各一份）**：新建 `shop/ChipShop{Catalog,Roller,State,Opener,RegenTicker,Menu}.java`、`block/ChipShopBlock.java`、`screen/ChipShopScreen.java`、`client/ChipShopClientState.java`、`network/ChipShop{Buy,Refresh,Deny}Payload.java`（neo 另接 `network/ModPayloads.java`；forge 接 `network/ModNetwork.java` 并在**末尾**追加协议 id **21/22/23**，该通道**顺序即协议**）；改：`block/ModBlocks.java`、`item/ModItems.java`、`init/ModCreativeTabs.java`、`component/ModAttachments.java`（键 `chip_shop_state`，**不 .sync()**）、`item/CurioSlotUtil.java`（新增 `forEachCurioStack`）、`screen/ModMenuTypes.java`(forge)、三语 `lang/{zh_cn,en_us,ja_jp}.json`（各 +7 键）。
- **资源**：`assets/astral_dice/blockstates/chip_shop.json`、`models/item/chip_shop.json`、`textures/gui/chip_shop/chips/*.png`（**61 张**）、`data/astral_dice/recipe|recipes/chip_shop.json`、`data/astral_dice/loot_table|loot_tables/blocks/chip_shop.json`（1.20.1 用**复数**目录）。配方中排 `BSB`（`B=astral_dice:star_coin`、`S=astral_dice:blank_chip`），**必须与 `card_shop` 的中排 `BBB` 不同**（否则一个方块静默不可合成，守门 G22 守这条）。
- **测试资产**：`scripts/test/cases/CHIP-{LOGIC,REGEN,CLIENT-BUY,CLIENT-REFRESH,CLIENT-DENY}-1.21.1.json`；`scripts/test/resources/kubejs/1.21.1/server_scripts/astral_shop_probe.js`（+563 行：`c*` 命令族与 `creset`）、`client_scripts/astral_shop_client.js`（+121 行：770/771/772 场景、关屏回读钩子）；`scripts/verify/verify_chip_shop_ui.ps1`（+544/−12 行：G21b / G22..G27）、`scripts/verify/mutate_chip_shop_ui.ps1`（**新建**，37 条）。
- **文档**：`docs/chip-shop-logic-plan.md`（Q1–Q17 裁决与实作口径）、`docs/chip-shop-ui-spec.md`（`.86` 入库）、`docs/compat-1.20.1-forge.md` **§十七**、`CHANGELOG.md` / `CHANGELOG_ZH.md`（玩家侧条目）、`AGENTS.md`（测试环境 ModernFix 引脚）。

| 主题 | 关键路径 |
|---|---|
| 池 / 常量 / 价格 | `<线>/src/main/java/com/merlinkitsune/astral_dice/shop/ChipShopCatalog.java` |
| 抽取与权重 | 同目录 `ChipShopRoller.java`（`starLevel` / `isOwned` / `ownedChipItems` / `availablePool`） |
| 存档态与恢复 | 同目录 `ChipShopState.java`（附件 record + CODEC + `needsReroll`/`offersKey`）、`ChipShopRegenTicker.java` |
| 开店与补抽 | 同目录 `ChipShopOpener.java`（`ownsEverything` / `hasNoRoom` 两条拒绝 + `ensureOffers`） |
| 结算 / DataSlot / 关店 | 同目录 `ChipShopMenu.java`（`handleBuy` / `handleRefresh` / `OfferSlot` / `playerSlotOffset`） |
| 界面与命中 | `<线>/src/main/java/com/merlinkitsune/astral_dice/screen/ChipShopScreen.java`（`STRIP_*` / `CELL_*` / `isHoveringStrip` / `chipIndexAt`） |
| 客户端态 | `<线>/src/main/java/com/merlinkitsune/astral_dice/client/ChipShopClientState.java` |
| 守门 / 变异 | `scripts/verify/verify_chip_shop_ui.ps1`、`scripts/verify/mutate_chip_shop_ui.ps1` |
| 实机探针 | `scripts/test/resources/kubejs/1.21.1/server_scripts/astral_shop_probe.js`、`…/client_scripts/astral_shop_client.js` |
| 设计 / 界面 / 平台差异正本 | `docs/chip-shop-logic-plan.md`、`docs/chip-shop-ui-spec.md`、`docs/compat-1.20.1-forge.md` §十七 |

---

## 8. 新会话第一步（30 秒版）

1. `git -C F:\MCProject\astral_dice_multiloader-next log --oneline -3` + `status --short -uall`（期望：HEAD ≥ `6c81b7fe`；未跟踪仅 4 行 `%SystemDrive%/…db`）。
2. 读本文件 → `docs/handover-2.0.0-dev-next.md`（常态规范 / 进度 / 坑）→ `docs/handover-new-session-context.md`（照抄提示词）→ `AGENTS.md` 四节（工作树纪律 / 二次验证 / 子代理与测试环境硬规则 / 版本优先级与跨线移植纪律）。
3. 想动筹码商店：先跑 §5 的静态判据（守门 + 变异 + build）确认基线绿再改；改完重跑同一组，必要时实机（**先问用户**）。
4. 想推进 P1 移植 / forge 实机：按 §4 的第 1、2 条准备，先读对应平台的 compat 文档。
5. 任何提交：**升四线版本 + 两份 CHANGELOG 段头**（只改 `<N>`），bump 在 build 之前，**不 push**；收尾核对另一工作树为空且 HEAD 不变。

