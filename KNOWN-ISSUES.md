# 已知问题与未来版本修补清单（KNOWN-ISSUES）

> **本文件是「未修项」的唯一版本化载体**。本地 `docs/` 目录被 `.gitignore` 忽略、不随仓库分发，故凡需要跨会话/跨分支可见的未修项一律登记在此。
> 本文件**只登记「尚未实施」的事项**；每条的最终状态必须如实反映现实。
> ⚠️ **未修项禁止为缩短清单而删除**；**已处理项**按 §0 第 4 条清理（移入 **§1.1 已处理索引**，正文留在 git 历史里）。

## 0. 使用纪律（必须遵守）

1. **实施前必须先复核，不得直接照文档改**：本清单多数条目的定性来自 **2026-09-15 15:45 的扫描快照**，其后本批已改动过 `combat/DiceCombatModifiers`、`combat/SpellDamageRegistry`、`combat/HostileTargets` 等文件 ⇒ 「仍存在/未修」**不等于当前 HEAD 仍存在**。按 `mc-two-round-verification` 流程，先做**独立代码复核**（第一遍，验证者 ≠ 实现者），把「文档定性」与「当前 HEAD 实况」逐条对齐，再交用户定夺。
2. **三版本同步**：所有修补一律 `neoforge-1.21.1` + `forge-1.20.1` + `neoforge-26.1.2` 同时实施，保持功能对等（项目默认规则）。
3. **必须能被验证**：每条修补都要有可判定的判据（游戏内用例、或新增用例；`scripts/test/cases/` 与 `scripts/test/TESTING-SPEC.md`）。**不得**用「代码看起来对了」结案。
4. **结案方式与清理规则（2026-10-01 修订）**：条目修好后**不再留在本清单正文里**。顺序是 ——
   ① 把结论与证据落到正确的位置：玩家可见的写两个 `CHANGELOG`；工程细节写 `scripts/test/TESTING-SPEC.md` 附录 A；
   长期约定写 `AGENTS.md`（若涉及）。② **整块删除**该条目。③ 在 **§1.1 已处理索引**留一行（id + 一句话结论 + 证据落点），
   并在 **变更记录**留一条日期行。
   ⚠️ 原文「**禁止为了缩短清单而删除条目**」**仍然有效** —— 它禁止的是**删未修项**（那是掩盖问题）；**已修项**的清理
   不属于该禁令（正文在 git 历史里可追）。
   ⚠️ 删除前必须 `grep` 该 id 的引用：若其它**未修**条目引用了它，把引用改指 §1.1（**不得删引用**）。
5. **行号会漂移**：本文件引用的行号取自扫描时的快照，复核时请**以引用的原文串/标识符为准**重新定位。

## 1. 登记信息

- 登记日期：2026-09-15
- 登记来源：B7 交付后遗漏审计（用户裁定「纪录上述问题，作为未来版本修补内容」）
- 目标版本：**下一版本**（1.2.1 之后；是否并入 1.2.1 需用户另行裁定）
- 条目总数（2026-10-01 清理后）：**26** 条未修/未决 = A 组 7（KI-1…KI-7）+ B 组 1（KI-8）+ C 组 3（KI-9…KI-11）
  + M 组 3（KI-M2/M3/M4）+ D 组 1（KI-D1）+ E 组 3（KI-E1/E2/E3）+ F 组 7（KI-F4/F7/F8/F20/F21/F22/F23）
  + G 组 1（KI-G2，2026-10-01 新增），
  另有 §5 的 **2** 条「测试资产待修项」。**已处理条目见 §1.1 索引。**
  ⚠️ **2026-10-02 澄清（发布前审计）**：上列 26 条里有 **3 条（`KI-F21`/`KI-F22`/`KI-F23`）标题自述「已完成 / 已修 / 已缓解」**，但正文各自写明**尚未做实机验证**（前两条各有一项「进世界未跑」）。它们**保留在正文**是为了不丢失「待实机确认」这个待办，**不计入「未修缺陷」**；数值 26 = **23 条未修/未决 + 3 条已修待实机**。

---

## 1.1 已处理索引（2026-10-01 首次清理）

> **已修好的条目不再留在本清单的正文里**（清理规则见 §0 第 4 条）。本表只留「id + 一句话结论 + 证据落点」；
> 条目的完整正文、机制分析与验证细节在 **git 历史**里（清理提交的前一个版本），玩家可见结论见 `CHANGELOG{,_ZH}.md`，
> 工程细节见 `scripts/test/TESTING-SPEC.md` 附录 A。

| KI | 结论（一句话） | 证据落点 |
| --- | --- | --- |
| KI-M1 | 旧「待命等待器」由**目标选择器**取代（已裁决，非缺陷；主线的等待器代码与三个 `*_ready` 效果已随合并移除；`SIGN_READY_*` 保留为废弃键） | 变更记录 2026-09-17；TESTING-SPEC 续 18；KI-M4② 仍跟踪这两个废弃键的去留 |
| KI-D2 | Iceberg `Tooltips.currentColors` 的**回灌**（1.3.2 一代）⇒ 已由 `client/IcebergTooltipCacheGuard` 缓解（四线，按 Iceberg 两代架构自适应跳过） | CHANGELOG 1.3.6；TESTING-SPEC 续 42；`AGENTS.md` 边框策略条；技能 `mc-thirdparty-tooltip-frame` §4.3 |
| KI-G1 | 看板娘立牌（mimi）筹码池**硬编码**（41 of 61）⇒ 改为**运行时从物品注册表派生**（`BaseChipItem` + `AstralRarities.tierOf` 分三档） | CHANGELOG 1.3.6；TESTING-SPEC 续 43 |
| KI-F1 | 两个「指定药水」配方已按 `astral_dice:nbt_shaped` **严格保真**（原「放宽为任意药水」方案作废） | TESTING-SPEC 附录 A F 组相关续；`NbtShapedRecipe` |
| KI-F2 | 跨加载器存档不互通（**平台差异、非缺陷、不修补**；饰品数据同理） | 同上；KI-F3 行 |
| KI-F3 | 两条饰品通道共存的槽位聚合语义（**已设计处理**；`data/curios/tags/items/*.json` 是本线物品清单的**单一事实源**，勿因命名空间而删） | 同上 |
| KI-F5 | **1.20.1 通用标签必须用 `forge:`（不是 `c:`）** —— 本线自建 `data/forge/tags/items/bricks.json`（= `#forge:ingots/brick` ∪ `#forge:ingots/nether_brick`）补齐 Forge 47.x 缺的汇总 `bricks` 标签；⚠️ 旧实现误用 `c:bricks` ⇒「对怪板砖」配方永不可合成（**已修**，2026-10-04） | 同上；`docs/compat-1.20.1-forge.md` 五 |
| KI-F6 | 自建总线的「登记完整性」由 `platform/event/SubscriptionAudit` 注解扫描兜底（**局限**：`dispatchReport()` 对「漏 `register`」仍是盲区） | 同上 |
| KI-F9 | （**仅 fabric 线**）客户端订阅**内部类**漏登记 ⇒ J（立牌主动）/ H（卡牌栏）两键完全不响应（已修） | TESTING-SPEC 附录 A；KI-F6 的审计能力 |
| KI-F10 | （**仅 fabric 线**）`MobEffectEvent.Remove` 构造器对 null 效果实例 NPE ⇒ 玩家无法进入存档（已修） | 同上 |
| KI-F11 | （**仅 fabric 线**）`ScreenEvent.Opening` 在 `setScreen(null)` 时 NPE ⇒ 每次进世界客户端必崩（已修） | 同上 |
| KI-F12 | fabric 测试台自身 7 个缺陷（**已修**；其中 2 个阻断级）—— 「测试流程完全不可用」的根因 | `scripts/test/fabric/README.md`；TESTING-SPEC 附录 A |
| KI-F13 | 库按**字符串名**反射原版字段 ⇒ 生产 100% 起不来（已修：库 `1.0.5-alpha.1`；生产映射冒烟 PASS） | TESTING-SPEC 附录 A；`scripts/test/fabric/ft_prod.ps1` |
| KI-F14 | tooltip 染色 mixin 与 Architectury 抢同一 `@ModifyConstant` 常量 ⇒ 悬停 GUI 必崩（**已修**：改 `@WrapOperation`） | TESTING-SPEC 附录 A |
| KI-F15 | 饰品栏前置从「Trinkets 硬必需」改为「**Trinkets 或 Accessories 二选一**」（已修，用户裁决） | TESTING-SPEC 附录 A；KI-F3 |
| KI-F16 | dev 开关 `-PtestAccessories=false` **从来无法通过编译**（已修）—— 「注释声称可用、实际从没跑过」 | 同上 |
| KI-F17 | 玩家持久化附件**注册晚于**数据反序列化 ⇒ 每次登录静默丢键（症状＝手册重复补发）（已修） | 同上 |
| KI-F18 | Accessories 槽位图标必须落在**原版 blocks 图集**目录 `assets/<ns>/textures/gui/slot/`（已修） | 同上 |
| KI-F19 | 五项「玩家可见」缺陷按 dev-next 已修方案同步落地（已修） | 同上；KI-F20 是其连带项 |
| KI-M5 | 26.1.2 接入库后的两项开放项 —— ①「库 jar 与 mod jar **成对推送**」规则**已作废**（库改为 **JarJar 内嵌**，整合包只放 `astral_dice-*.jar`；反过来**残留**独立库 jar 会被 JarJar 选择器按 `modId` 优先采用并盖掉内嵌件 ⇒ 应删）；② `effect/ReadyEffect` 本地副本已随波次 2b 删除（已关闭）。**实测四个整合包 `starengine` 残留 = 0** | 变更记录 2026-09-24；`AGENTS.md`「库的 jar 分发」 |
| §5 的 7 行（含 `P5-equip-paths-linkage` 13 处 `✘1需改`、`NancyLuSignItem` 的 `isHostile` 过宽、P2-C1/C3/C4 与安全气囊基准、`mt_assert` 断言窗口、`LOOT-MODIFIER` 反向断言、追加 A《恋的规则书》重复补发、追加 B 死亡保留集合一致） | 全部**已闭环**（2026-09-15/16 实测；含 commit `cc49f0d` / `c0ad51f`） | 变更记录 2026-09-15 / 2026-09-16 两行 |
| KI-F20 | 三线 `require = 2` 误写 —— **已全部闭环**：`forge-1.20.1` 两处已为 `require = 1`（`cba8e956`，早于 1.3.6 周期）；`neoforge-1.21.1` 的同名类走 `@Inject(... at = RETURN)`、**不涉及 `require`**；`neoforge-26.1.2` 无该文件（走 `GatherEffectScreenTooltipsEvent`）；全仓 `grep "require = 2"` 仅剩 fabric 类注释文字 | 2026-10-02 发布前审计逐文件核实（`KNOWN-ISSUES.md` 变更记录同日） |

---


## 2. A 组 — 数值与伤害修饰器（来源 `docs/scan2/P2-numeric-modifiers.md`）

> 该文档 2026-09-15 15:45 快照的 §0.3 汇总表把下列条目标为「**未列**」（**未**并入主差异表 `docs/scan2/D-discrepancy-table.md`）且「**未修**」；
> 同日 21:58 的主差异表与两个 `CHANGELOG` 中 **grep 命中 0** ⇒ 这些条目**从未进入裁定流程**。

### KI-1 ＝ P2-C2「骰战覆盖同时丢弃抗性提升 / 保护附魔」

- 文档定性：**缺陷（high）**，「未修（仍存在）」
- 现象（文档原文摘要）：骰战（dice-combat）的伤害覆盖在改写伤害时，会**连带丢弃**原版的抗性提升（`RESISTANCE`）与保护附魔减免。
- 待复核点：在**当前 HEAD** 上确认骰战覆盖路径（`combat/DiceCombatModifiers` / `combat/DiceCombatEvents` 的伤害改写处）是否仍绕过原版减免；并用带甲 + 抗性提升 II + 保护 IV 的对照靶实测。
- 建议动作：若仍存在，改为「只覆盖骰战自身那部分」，把原版减免在改写后**重新消费**（可参考 P2-C1 已修时新增的「受击侧伤害修饰器注册表 + `setNewDamage` 前重消费」范式）。
- 验证入口：可仿 `SPELL-TRUE-DAMAGE` 的对照设计（带甲/无甲 + 抗性提升 + 保护附魔四靶）。

### KI-2 ＝ P2-C8「×1.4 与七咒 `dice_curse_ratio` 的交叉 ⇒ 1.20.1 双重放大」

- 文档定性：**1.20.1 单侧偏高** ⇒「**1.20.1 变为双重放大（新引入回归，未修）**」
- 影响：**违反双版本对等**（同一构造在 1.20.1 被放大两次），属本组最紧的一条。
- 待复核点：确认 `dice_curse_ratio` 与雨中/水下 ×1.4 在 1.20.1 侧的相乘/相加顺序，与 1.21.1 逐条对齐。
- 建议动作：把两侧口径统一到同一处（唯一注册表 + 单次应用），并加跨版本对等断言。

### KI-3 ＝ P2-C5「能量过载 −30% 被 20 上限吞掉」

- 文档定性：**缺陷（medium）**，「未修」
- 现象（文档原文摘要）：能量过载（扫地机 `jasmine` 主动）的 −30% 护甲被「护甲 20 上限」吞掉，实际减益不生效。
- 待复核点：确认上限截断发生在减益**之前**还是之后；若在之前，减益会被静默吞掉。

### KI-4 ＝ P2-C6「青之诅咒 −20% 与 能量过载 −30% 顺序相乘（0.8×0.7=0.56），且乘在含防御力折算的总和上」

- 文档定性：**medium（未修）**，并明确注明「**是否需要修正待定夺**」（属**待用户裁定**，不是已认定缺陷）
- 文档原文锚点（快照行号）：
  - `BlueCurseEffect:18-23` → 护甲 −0.2 / 韧性 −1.0（MULTIPLY_TOTAL 之一）
  - `DiceCombatModifiers:87-102` → 防御力 ×2 → 护甲（ADD_VALUE，被上述乘数一并削减）
  - 「每提升 1 星级攻防 +2（+4 护甲）」`NetherStarDiceItem:74` 同样会被 −20%/−30% 乘数削减
- 核心争议：两个 `MULTIPLY_TOTAL` 是**相乘**（−44%）而非相加（−50%），且乘数是「base + 全部 `ADD_VALUE`（含 1 防御力 = 2 护甲的折算值）」。
- 待裁定：这是否为**设计意图**（乘数吃折算值）？若否，需把「防御力折算」挪出乘数作用域。

### KI-5 ＝ P2-C7「电击手套 AOE 基准跨版本」

- 文档定性：**单侧差异（medium）**，「未修」
- 待复核点：`electric_glove_aoe` 的范围/伤害基准在 1.20.1 与 1.21.1 是否同构（对照 `ELECTRIC-GLOVE-ROUND-RESET` 用例已有的读数口径）。

**→ 2026-09-16：已确认，且已按推荐方案修复（用户裁定「按推荐方案修补」）。**

- 根因（与 `AGENTS.md` 的伤害事件映射一致）：1.20.1 的法伤主链路原挂在 `LivingHurtEvent`，
  该事件在**护甲前**派发（`LivingEntity.java:1665`，早于 `:1667-1668` 的护甲/附魔减免），
  而 1.21.1 挂在 `LivingDamageEvent.Pre`（**护甲后**）⇒ 电击手套 3 格 AOE 在 1.20.1 以
  「护甲前原始值」为基准，带甲目标周围会多打一截。
- 修复（双版本对等，2026-09-15 用户裁决口径）：
  - `forge-1.20.1/.../event/DamageEffectCardHandler.java`：事件由 `LivingHurtEvent` 改为 `LivingDamageEvent`；
  - `forge-1.20.1/.../combat/SpellDamageContext.java`：`event` 字段与构造参数同步改为 `LivingDamageEvent`；
  - 唯一读取 `ctx.event` 的取值点 = `SpellDamageRegistry`（1.21.1 `getNewDamage()` / 1.20.1 `getAmount()`），签名不变。
- 回归用例：`scripts/test/cases/ELECTRIC-GLOVE-AOE-BASE-{1.21.1,1.20.1}.json`（探针 `/astralprobe glovebase <tag>`，
  8 断言；核心不变量 = 读数 `base=…:raw=8…:self=…:nbr=…` 中 **base/self/nbr 三者相等**）。
- 状态：**代码已改 + 已编译通过**；1.20.1 必须在**重新构建**后再跑该用例（否则读到的仍是旧的护甲前基准 `base=8`，用例会正确地 FAIL）。
- 残余（平台固有，不修）：目标带**吸收（黄心）**时两侧基准仍有差（1.20.1 在吸收后派发、1.21.1 在吸收前），普通目标无吸收故实际影响可忽略。

### KI-6 ＝ P2-C10「肾上腺素 / 能量回收 修饰器残留」

- 文档定性：**缺陷（medium）**，「未修」
- 现象（文档原文摘要）：修饰器在效果结束后**未被移除/归还**，残留影响后续结算。
- 待复核点：检查注册与反注册是否成对（含死亡/卸下/切维度等非正常退出路径）。

### KI-7 ＝ P2-C13「力量双重计入」

- 文档定性：**缺陷（medium）**，「未修」
- 现象（文档原文摘要）：`力量`（`STRENGTH`）在骰战结算中被计入两次。
- 待复核点：确认加成入口是否在两个阶段各应用一次。

> 对照：同文档的 **P2-C1**（末影骰雨中 ×1.4 被骰战覆盖丢弃，A3）、**P2-C3**（加急加快按常量 −90s）、**P2-C4**（命运指引基准取通用最大冷却）文档已标「已修」，本清单不再登记。

---

## 3. B 组 — 未核验

### KI-8 ＝ P1 U5「立牌主动冷却的 tooltip 显示在 ≤ 1 秒时不出现」

- 来源：`docs/scan2/P1-cooldowns-timers.md`（标「**未核验**」）
- 现象（文档原文摘要）：`ModTooltipHandler.addSignCooldownRemaining` 用 `remainingTicks / 20`（**向下取整**）且仅当 `remainingTicks > 0` 才显示 ⇒ 冷却不足 1 秒（或整秒取整为 0）时该行**不显示**。
- 待复核点：是否属**预期行为**（不足 1 秒不显示）还是显示缺陷；两版本是否一致。

---

## 4. C 组 — 更早扫描的未决项（来源 `docs/project-scan-report.md` §八）

> 该节共 5 项，其中 **#2 CI 分支过滤器**（`build.yml` 已写 `multi-1.20.1-1.21.1`）与 **#4 `AGENTS.md` 入库**（已纳入版本库）已于 2026-09-15 确认闭环，**不再登记**。

### KI-9 ＝ R1/R2「两处 1.20.1 功能缺失」

- 文档选项：**立即补齐（双版本对等）** ／ **记录为已知差异**
- 待办：先在当前 HEAD 上复核这「两处」具体是什么、是否仍缺失（原文未在本文件展开，复核时以 `docs/project-scan-report.md` 为准，或按 R 编号重新取证）。

### KI-10 ＝ R4「五个筹码的获取途径」

- 文档选项：**各建配方** ／ **纳入 Bountiful 奖励池** ／ **明确设计为仅创造可得**
- 关联能力：仓库已有 `mc-recipe-manual-audit` 与 `mc-bountiful-pool-sync` 两套审计方法可直接复用。

### KI-11 ＝「充能 6 筹码是否另入赏金池」

- 文档选项：**入池** ／ **仅合成**

---

## 5. 测试资产待修项（原「已闭环但当日文档未回写」表 —— **仅剩这 2 条**未闭环）

> 该表原 9 行已于 2026-10-01 清理（其余 7 行均已闭环，见 **§1.1 已处理索引**）；下表是**仍未闭环**的两条
> 「测试资产自身」缺陷（**不是产品缺陷**，故不写进 CHANGELOG）。

| 曾标记 | 现状 |
|---|---|
| **追加 D**：`SPELL-TRUE-DAMAGE` 的**护甲读取在 1.21.1 上失效、且用例断言过弱**（测试资产缺陷） | **已登记，待修**（2026-09-16 实测）：同一用例两边都判 PASS，但取证读数质量天差地别 —— 1.20.1 读到 `AP_SD_ARMOR_CTRL:armor=rc=1:tough=rc=1:read=rc=2` 与 `AP_SD_ARMOR_EFFECTIVE:ctrl_arm=armor=20:tough=8:test_arm=armor=20:tough=8`（真实护甲 20 / 韧性 8），**1.21.1 读到 `rc=undefined`、四组全为 `armor=0:tough=0`**（护甲属性根本没读上）。因该用例的断言**只匹配键名前缀**（`AP_SD_ARMOR_EFFECTIVE:`），1.21.1 侧「基础伤害吃护甲」这一条实际是**空证据**仍判 PASS。修法：① 探针在 1.21.1 侧改用能读到护甲/韧性的属性入口；② 断言加严为「必须读到 armor=20:tough=8 之类非零值」，读不到即 `ERROR`（**禁止**为变绿而放宽） |
| **追加 C**：新用例 `ELECTRIC-GLOVE-AOE-BASE` 的**探针取数缺陷**（测试资产缺陷，**不是产品缺陷**） | **已登记，待修**（2026-09-16 实测）：首次执行即 FAIL，取证读数 `AP_GB_ERR:base_source:no_read:calls=0` + `AP_GB_GA:base=-1:raw=8:self=2.24:nbr=2.24:armor=20:rarmor=0:mode=forced_survival:armed=1:bsrc=no_read`。**判据**：`self == nbr == 2.24` —— 真正重要的**对等不变量成立**；失败的是「读 `base`」那一路（`bsrc=no_read`，注册进生产修饰器表的探针读取器**一次都没被调用**）⇒ 断言 7/8/9 因 `base=-1` 不匹配而 FAIL，断言 12（absent）又抓到探针自己吐出的 `AP_GB_ERR`。**结论**：该用例当前**不可用**，在任何版本上都会 FAIL；KI-5 的**游戏内**数值验证因此**尚未完成**（代码级证据见本文件 KI-5 段）。修法方向（择一，需先复现 `calls=0` 的原因）：① 让探针改从**已确定会被调用**的路径取 `ctx.event` 的值；② 或改判据为「同一版本内 `self == nbr` 且跨版本同值」这类不依赖生产修饰器被调用的对照。**禁止**为了让它变绿而放宽断言 |

## 6. 主线 → multi-dev-next 合并（2026-09-17）的并入裁决与遗留项

> 本节记录 `multi-1.20.1-1.21.1`（`8f68482`）→ `multi-dev-next` 的合并中，两套「立牌主动技能释放模型」的
> 取舍与遗留项。合并提交（merge commit）的提交信息里有同一份结论（本节为可版本化载体）。

### KI-M2 ＝ 三态化（锁定-生效中）与选择器**共存**（已裁决）

- 主线的第二批「三态化」（`sign_active_lock_*` 附件、`BaseSignItem#isSignActiveLocked` /
  `tickSignActiveLock` / `startActiveLockOnUse` / `endLockAndStartCooldown` / `onEffectCardRoundReset`）
  **完整保留** —— 8 个立牌子类（`FannySign` / `FenSign` / `JasmineSign` / `KomachiSign` / `MisakiSign` /
  `NancyLuSign` / `PaparaSign` / `ParunanSign`）与 `PlayerTickEvents` / `EffectCardPeriod` 都引用它，
  删除即无法编译，且它本身是独立功能（「释放后的生效期不算冷却空档」）。
- 与选择器的分工：选择器决定「**何时**释放」，锁定决定「释放后的生效期是否算作冷却空档」；
  两者在同一 `performSkill` 内串联（第 2 步 = 选择器会话检查，第 6 步 = 选择器类延后起冷却 + 锁定判定）。
- 已知取舍（**待修项，登记于此，勿删**）：三个选择器类立牌（占星师/秘密侦探/枪匠）**没有**覆写
  `startActiveLockOnUse` / `isGateEffectActive`，故它们不会进入锁定态；若游戏内实测发现这三者的主动在
  生效期内可被再次触发（或缺少与主线等待器等价的保护），需按各自效果时长补 `startActiveLockOnUse`
  覆写并加对应用例 —— **本轮合并只保证编译与既有语义不被破坏，未做该补强**。
- **已知缺陷（2026-09-19 登记，待修）**：门控路径**漏写 `sign_active_max_cooldown`** —— 现有四个门控立牌
  （游戏大师 `RenSignItem` / 秘密侦探 `BonnieSignItem` / 占星师 `HaiqingSignItem` / 枪匠 `MosesSignItem`，两发布线同）
  的 `TargetSelectionAction#apply` 里都只调 `ModAttachments#setSignActiveCooldownEnd`。非门控路径（`BaseSignItem` 第 6 步）
  与 `ModAttachments` 的注释口径都是**成对写入**，而电流核心 `CurrentCoreChipItem` 的消耗档位分母读的正是
  `sign_active_max_cooldown`（缺失时回退 180 s）⇒ 这四个立牌的档位定价会偏。2026-09-19 新增/重写的史莱姆立牌
  （`LuluSignItem`）**已按正确口径成对写入**，可直接照抄；补修时两发布线同改并加对应用例。

### KI-M3 ＝ 合并后仍需在游戏内复核的两项（未实施）+ 库侧过渡符号清理（**已实施**，2026-09-17）

1. 选择器类立牌的**冷却与锁定**实际表现（见 KI-M2 的已知取舍）。
2. `moses_ready.png` / `komachi_count.png` 等随主线/本分支删除的贴图是否有残留引用
   （2026-09-17 静态核验：0 处；游戏内未复核）。
3. **库侧过渡符号清理 —— 已实施（2026-09-17）**。（旧结论「合并后已无消费方、静态核验无任何引用」**不准确**，已更正为下方实测口径。）
   - **引用口径（实测）**：消费方共 **2 处未使用 import**（按符号计 = 库的 `event/AstralEventType` 与
     `event/EventContext`；两侧各 2 行、合计 4 行：`neoforge-1.21.1` 第 17-18 行、`forge-1.20.1` 第 15-16 行，
     两侧文件内均 0 使用），**已于 `7dc64cb` 清除**；清除后**三条线对这几个符号 0 处实际使用**：
     `git grep -E 'GameplayConstants\.(EVENT_RANGE|EVENT_APPLY_MAID|KOMACHI_EXTRA_PLAYS_CAP)|starenginelib\.(event\.(AstralEventType|EventContext|EventEffect)|effect\.ReadyEffect)|EventTargetCollector\.(collectTargets|collectMaids|isMaidOwnedBy)'`
     逐线命中均为 **0**。
     ⚠️ 两处「形似命中」不要误判：`item/card/RandomCardHandler.collectTargets` 是本模组**自己的同名方法**
     （与库的收集链无关）；`neoforge-26.1.2/effect/ReadyEffect.java` 是该线**自带的本地类**
     （26.1.2 不依赖 starengine_lib，属既知平台差异，不在本次清理范围）。
   - **实施结果**：库提交 **`d5b0776`（main）**，版本 **`1.0.0-SNAPSHOT.5`**；mavenLocal 已重新发布
     （2026-09-17 19:22，三个平台 jar）；开包核对：事件框架三件套与 `ReadyEffect` 已不在 jar 内
     （仅剩在用的 `CutterReadyEffect` 与 `EventTargetCollector`）。消费方 `ce583a9` 已把 CI 库检出 ref
     对齐到该提交（见 **KI-M4①**），消费方 `starengine_lib_version` = `1.0.0-SNAPSHOT.5`。
   - **实际删除**：`effect/ReadyEffect`；事件框架**三件套** `event/AstralEventType` / `event/EventContext` /
     `event/EventEffect`（`d5b0776`）；**收集链** `EventTargetCollector.collectTargets` / `collectTeamTargets` /
     `collectMaids` / `isMaidOwnedBy`；**三个事件常量** `GameplayConstants.EVENT_RANGE` / `EVENT_APPLY_MAID` /
     `KOMACHI_EXTRA_PLAYS_CAP`（收集链与三个常量在 `7e0046d`）。
   - **保留（仍在使用，勿删）**：`GameplayConstants.SKILL_WAIT_SECONDS`、`TARGET_SELECT_RADIUS`、
     `EVENT_APPLY_MC_TEAM` / `EVENT_APPLY_FTB_TEAM` / `EVENT_APPLY_OPAC` 与
     `EventTargetCollector.collectTeamPlayers`（清单原件见 `temp/sink-manifest-20260917.md` §6）。

### KI-M4 ＝ 待用户裁决 / 待办的开放项（2026-09-17 登记；**原 2 项，第 1 项已于 2026-09-24 闭环**，仅剩下面第 2 项）

1. **库仓库未 push ⇒ CI 的前置库 checkout 在推送前必然失败。** 消费方 `.github/workflows/build.yml`
   已把检出 ref 钉到 `d5b077692058ebca752e9ef68ef18ffcc18e5fa2`（= `1.0.0-SNAPSHOT.5` 终态，改动提交
   `ce583a9`），但库仓库 `merlin-kitsune/starengine_lib` 的 `main` 本地**领先 `origin/main` 8 个提交**
   （实测 `git rev-list --left-right --count origin/main...main` = `0  8`；`d5b0776` 尚未推送）
   ⇒ **库推送之前 CI 的该步必然 `checkout` 失败**（本机无 token / `gh`，按用户裁决不代推送、不监视 CI）。
   **待办**：用户推送库 `main` 后本条即闭环；若希望推送前 CI 也能跑，可把 ref 临时退回已推送的
   `1.0.0-SNAPSHOT.4` 对应提交 —— **须由用户裁决**（本清单不改上游仓库、不改 CI 配置）。
   ✅ **2026-09-24 已闭环**：库 `main` 已推送到远端（远端 `main` = `e407844`，= `1.0.3`），其 CI 自动打 tag
   `1.0.3` 并发布三平台 jar 的 Release；消费方 CI（库检出 `ref: e407844`）同日跑绿 ⇒ 上述两条「待办」路径均不再需要。
   ⚠️ 本条正文里的 `d5b0776` / `1.0.0-SNAPSHOT.5` / `ce583a9` 均为 2026-09-17 登记时的快照，现仅作历史记录。
2. **消费方 `SIGN_READY_TYPE` / `SIGN_READY_EXPIRE` 废弃键的去留（待裁决，不得静默删除）。**
   这两个附件键是旧「待命等待器」的载体（见 §1.1 索引中的 KI-M1），等待器已在合并中由目标选择器取代、**无任何写入方**，
   但**三线仍在引用**：实测 `git grep -E 'SIGN_READY_TYPE|SIGN_READY_EXPIRE' -- '*.java'` 命中 **22 处**，
   全部落在三条线各自的 `component/ModAttachments`（键定义 + `get/set` 包装器；`1.20.1` 8 处 /
   `1.21.1` 8 处 / `26.1.2` 6 处，其中 1.20.1 还有 `SYNCED_KEYS` 登记，属同步协议面）。
   **候选**：(a) 三线同步删除（注意 1.20.1 的同步协议面与老存档里的废弃键残留）；
   (b) 保留为「废弃但可用」的占位键（**现状**），仅在注释里标注废弃；
   (c) 其它（如仅在下一大版本随存档迁移一起清）。**裁决前维持现状（b）。**

## 7. D 组 — 平台 / 第三方冲突（2026-09-18 起）

> 本组登记**不属于本模组缺陷**、但在本仓测试环境里会真实发生的问题。处置口径 = **记录 + 规避 +（可选）上报上游**，
> **不以改本模组代码的方式去「修」**；若曾被误判为本模组缺陷，须把「被排除的过程与证据」一并写清，避免重复劳动。

### KI-D1 ＝ 26.1.2 + 光影（Iris + Complementary Unbound）⇒ `Missing sampler Sampler1` 崩溃（**已确证与本模组无关；A/B 三组排除 ImmediatelyFast；源码级定案 = Iris 侧缺陷，且为 dev-only 断言**）

**现象**（2026-09-18 用户实测 1 次 + 自动化复现 3 次）：26.1.2 线装 Sodium + Iris + Complementary Unbound（HIGH）+ ImmediatelyFast 后，游戏进行中（约 1–3 分钟内）渲染线程抛 `java.lang.IllegalStateException: Missing sampler Sampler1` 并崩溃。用户最初把它归因于「击杀被施加虚弱印记的目标」，**该因果不成立**（见下方对照组）。

**崩溃栈（三份崩溃报告逐帧相同）**：
```
GlCommandEncoder.trySetup(:531) ← GlCommandEncoder.executeDraw(:406) ← GlRenderPass.drawIndexed(:145)
 ← RenderPass.drawIndexed(:95) ← RenderType.draw(:112) ← MultiBufferSource$BufferSource.endBatch(:99)
 ← ImmediatelyFast BatchableBufferSource.drawDirect(:178) / endBatch(:148) / endBatch(:138)
 ← LevelRenderer.lambda$addMainPass$0(LevelRenderer.java:707)      ← 原版主通道的 bufferSource.endBatch()
```
⚠️ 上栈是 **A 组（IF 开）** 的形态。**B 组（IF 关）** 的栈在 `MultiBufferSource$BufferSource.endBatch(:99)` 与 `LevelRenderer.lambda$addMainPass$0:707` 之间**没有任何 ImmediatelyFast 帧**，其余逐帧相同（`temp/t48/ab-if-off/latest.log:539` 起）——即去掉 IF 的批处理层后崩溃**照旧复现**。

三份报告内 **`com.merlinkitsune.*` 帧数 = 0**（`astral_dice` 字样只出现在「资源包 / 模组清单」行）。

**定案证据（对照组）**：同环境、同召唤物、同 45 s 用例，**唯一差别 = 去掉「进入目标选择器」这一步**（`SELECTOR-PRISM-CONTROL-26.1.2` 不执行 `/astral_dice targetselect …`）——
`capture hook active` = **0**、`astral_dice:pipeline/target_prism` = **0**、`TargetOutlineCapture` = **0**，**客户端照旧崩在同一行**。
⇒ 本模组的自定义几何**一次都没提交**、外框捕获路径**一次都没进入**，崩溃照样发生 ⇒ 与本模组无关。

**归因（2026-09-19 A/B 三组定案）**：**光影（Iris）是该崩溃的必要条件；ImmediatelyFast 不是。** 三组实测（同一客户端、同一世界，只改这一个变量）：

| 组 | 变量 | 结果 | 硬证据 |
|---|---|---|---|
| A | IF 开 + **光影开** | **崩** | 崩栈含 `BatchableBufferSource.drawDirect:178 / endBatch:148,138` |
| B | **IF 关** + 光影开 | **仍崩**（同一异常、同一调用链） | `run/26.1.2/mods` 里 IF `.jar`=0 / `.disabled`=1；本组 `latest.log` 内 `(immediatelyfast)`=**0**、`ImmediatelyFast`=**0**、`BatchableBufferSource`=**0** |
| 第三组 | IF 关 + **光影关** | **不崩**，用例 16/16 PASS，客户端存活 | `SHADERS=disabled(iris.properties)`、`Using shaderpack:`=**0**；IF 仍只有 `.disabled` |

⇒ 只把「光影」这一个变量翻面即由崩转不崩 ⇒ **必要条件落在光影/Iris 一侧**。

**机制（2026-09-19 Iris 源码级确认，非推断）**：Iris 把 item 管线替换成光影程序 —— `IrisPipelines.java:36-37`（`ITEM_CUTOUT`→`getCutout` `:188-198` = `ENTITIES_CUTOUT_DIFFUSE`；`ITEM_TRANSLUCENT`→`getTranslucent` `:212-222` = `ENTITIES_TRANSLUCENT`）+ `MixinShaderManager_Overrides.java:53-66`（在 `GlDevice.getOrCompilePipeline` 的 HEAD 处换成 `new GlRenderPipeline(renderPipeline, program)`，`cancellable = true`）。而这个光影程序的 sampler 名单**是按顶点格式推出来的**：`ShaderKey.java:42-43` 用 `IrisVertexFormats.ENTITY`，`IrisVertexFormats.java:52` 含 UV1 ⇒ `ExtendedShader.java:119-121` 执行 `samplerList.add("Sampler1")`，再经 `MixinUniform.java:36-38` 把 `Sampler1` 映射到光影包的 `iris_overlay`（该 uniform 由 `EntityPatcher.java:46,55` 在 `inputs.hasOverlay()`（即格式含 UV1）时注入并使用，`ShaderAttributeInputs.java:42-44`）⇒ **program 里确实声明了 `Sampler1`**。
反过来，**通道侧的 sampler 只按原版 `RenderSetup.useOverlay()` 绑定**（`RenderType.java:70,94-97,107-112`、`RenderSetup.java:83-120`：`Sampler1` 仅 `useOverlay`、`Sampler2` 仅 `useLightmap`），而原版 item 管线**故意**只声明 `Sampler0`/`Sampler2` 且不 `useOverlay`（`RenderPipelines.java:75-82`、`RenderTypes.java:151-174`）⇒ 原版自洽；**只有 Iris 的这次替换会索要 `Sampler1`**，于是原版校验 `GlCommandEncoder.java:526-532` 抛出。⚠️ **不存在「Iris 跳过 sampler 重绑」**：原版绑定照常发生，Iris 自己的绑定在校验**之后**（`MixinGlCommandEncoder.java:162` 的 `@At("RETURN")` → `ExtendedShader.java:210-219`），且它自带「缺 `Sampler1` 就绑 1×1 白像素」的兜底（`ExtendedShader.java:213-217`）—— 说明 Iris 本预期这是合法情况。

**判定**：**Iris 侧缺陷**（置信度：机制链 = **确认**（源码级，且与 A/B/C 三组读数一致 —— 光影关 ⇒ 没有程序替换 ⇒ 不需要 `Sampler1` ⇒ 不崩）｜命中的具体几何类型 = **推断**（崩点在 fixedBuffers 循环，9 个 fixed buffer 中只有 4 个 item sheet 的顶点格式含 UV1）｜IF 与本模组 = **确认无关**）。若按宽松口径也可记为「Iris × 原版交互面」，但责任在 Iris：原版口径（管线的 `withSampler` + `RenderSetup.useOverlay`）自洽，而 Iris 用顶点格式推出的 overlay 需求与它自己的替换结果不一致。⚠️ **dev-only 性质（写上游 issue 必须带上）**：整段校验被关在 `GlRenderPass.java:25` 的 `VALIDATION = SharedConstants.IS_RUNNING_IN_IDE && !Boolean.getBoolean("neoforge.disableGlValidation")` 之内 ⇒ **生产环境缺 sampler 只会 `continue` 静默跳过**（`GlCommandEncoder.java:609-613`），玩家侧实际影响很小，本崩溃是**开发期断言**。

⚠️ **两处旧记载同时作废（源码核实后更正）**：① 「`trySetup` 的 sampler 校验被 Iris 的覆盖程序替换」**不成立** —— 抛错的是**原版**逻辑；Iris 只在 `trySetup` 的 HEAD 对「Iris 自定义通道」**条件性 cancel**（`MixinGlCommandEncoder.java:91-104`：只有 `glRenderPass.iris$getCustomPass() != null` 时才 `cir.setReturnValue(true)`）并在 RETURN 追加自己的状态（`:162-169`），对通道的 `samplers` 表**只读不写**。② 「`getOrCompilePipeline` 抛 `Throwable`」**不成立**（见下方第 2 条）。

⚠️ **口径更正（勿再沿用旧说法）**：① 本文件与 `scripts/test/TESTING-SPEC.md` 早前的「光影可用、只是默认关掉省性能」结论**只覆盖启动期**，而本崩溃可在进入世界后 **1–3 分钟内**出现（用户实测 1 次 + 自动化 3 次）⇒ 应以「26.1.2 开光影长时间运行会崩」为准。② 「IF 是栈里的直接调用者 ⇒ IF 有嫌疑」已被 B 组实测**排除**（去掉 IF 后调用链只是少一层批处理帧，异常与崩点不变）。

**排除本模组嫌疑的两条（含被否决的方案，防止后人重走）**：
1. 「复用原版 entity 管线（`RenderPipelines.ENTITY_SOLID` 声明 `Sampler1`）导致缺绑定」——**不成立**：`RenderSetup.getTextures()`（`:83-118`）里 `Sampler1` = overlay 纹理、`Sampler2` = lightmap，而 `RenderTypes.entitySolid(tex)`（`RenderTypes.java:435-437`）本身就带 `useOverlay()/useLightmap()` ⇒ 该 RenderType 绑定的是三 sampler 的**超集**（最安全）。
2. 「自建仅 `Sampler0` 的管线以规避」——**已实测反而引入新问题并回退**：Iris 对非 `minecraft:` 位置的新管线打 `Missing program astral_dice:pipeline/target_prism in override list`（`temp/t48/A-if-on/debug.log:3530`）；**但该日志并非崩溃原因**：对应代码 `MixinShaderManager_Overrides.java:67-72` **只打日志、不抛异常**（`Iris.logger.fatal(…, new Throwable())` 里的 `Throwable` 只是日志参数），override 返回 `null` 后**回落到原版 program**（只声明 `Sampler0`）⇒ 它本身不会造成缺 `Sampler1`，且时刻早于崩溃约 15 s。⚠️ **旧记载「在 `getOrCompilePipeline` 抛 `Throwable`」作废**（源码核实后更正）；「panorama / blur / gui_* 的 Iris FATAL」出现在崩溃时刻**之后**，属错误屏 GUI 管线，只是「Iris 覆盖不完整」的旁证，**不得**当作同帧证据。该方案（提交 `2596ec2` 中的 Fix 2 部分）已按裁决回退、保留原 `entitySolid`，回退理由是**该自建管线的等价性未证 + 属非必要改动**（而非「它会抛异常」）。

**规避（2026-09-19 更正，旧口径作废）**：26.1.2 上**只要启用光影就有触发风险，与装不装 ImmediatelyFast 无关**（B 组实测）⇒ 当前有效规避有三条：① **在该线关闭光影**（第三组实测不崩；命令是 **`mt_env.ps1 shaders --version 26.1.2 --state off`** —— ⚠️ 2026-09-19 实测 `mt_env.ps1 debug --version 26.1.2 --shaders off` 会以 `MT_ERROR: 未知参数 --shaders`（exit 2）失败，见 `scripts/test/TESTING-SPEC.md` 附录 A 的同日条目）；② **开发环境加 `-Dneoforge.disableGlValidation=true`**（`GlRenderPass.java:25` 的显式开关 ⇒ 关掉这段 dev 校验；因该断言本身只在 IDE/dev 下生效，这等价于「按生产语义运行」，不掩盖生产行为）；③ 接受风险并在崩溃后立即归档现场。**本模组侧没有任何规避手段**（对照组证明崩溃不依赖本模组的自定义几何）。⚠️ 旧建议「用光影时不要同时装 ImmediatelyFast」**已作废**（它基于「IF 关就不崩」的推断，而该推断被 B 组实测否定）。

**A/B 三组（原「未完成项」，2026-09-19 已完成）**：曾在工具链上失败两次 —— ① 把 `ImmediatelyFast-*.jar` 改名 `.disabled` 后，`mt.ps1 --phase env` 会调 `mt_env mods`（`mt.ps1:396`）**把它装回来**（同哈希 `.jar` 与 `.jar.disabled` 并存，游戏加载 `.jar`）；② `gradlew runClient` 按**工作区源码**重编译 ⇒「修复前构建」跑不到。**已由「绕过 `mt.ps1`、直接驱动 `mt_launch.ps1`（改名放在 `env`/`world` 之后）」解决，两轮实测都保住了停用状态，且未改任何工具脚本、未新增开关。** 每组都先过两条硬证据（`run/26.1.2/mods` 里 IF 只有 `.disabled` 且无新 `.jar`；`latest.log` 已加载清单无 `(immediatelyfast)`），缺任一即判该组无效。第三组另加 `SHADERS=disabled` + `Using shaderpack:` 0 命中的光影关机证据。

**证据归档**：`temp/t48/`（A / A2 / C1 / EXTREME 四组日志 + `jar-prefix` 与 `jar-fixed` 两份 jar + 报告 `26.1.2-prism-crash-fix-verify.md`）；**A/B 三组**（`temp/t48/ab-if-off/`＝B 组 IF 关、`temp/t48/ab3-noshader/`＝第三组 IF 关 + 光影关、`temp/t48/revert/`＝Fix 2 回退与构建）与**选择器功能验证**全部归档于 `temp/t49/26.1.2-prism-ab3-and-func-verify.md`（报告 sha256 `729B22C0B71FF27C1C311AD5DAC3A659CEF4C16F07877439B71666FBEC5B83A2`）；定案报告 = `temp/t48/C1-if-on-control/crash-2026-09-18_21.16.52-client.txt`，B 组崩溃栈副本 = `temp/t48/ab-if-off/latest.log:539` 起（该组 crash-report txt 已被下一次 launch 的 `mt_launch.ps1:307-308` 启动清空行为删除，属工具链既有行为、非人为删除）。原始现场另有 `temp/t47-crash-2bugs/`（用户实测那次：crash report + latest.log + debug.log，均带 sha256）。

**上报材料（目标已由 A/B 锁定为 Iris；仍未执行）**：**Iris `1.11.4+mc26.1.2`**（环境 = MC 26.1.2 + Sodium `0.9.1+mc26.1.2` + Complementary Unbound `r5.9.3`，`enableShaders=true`），进入世界后 **1–3 分钟内**抛 `Missing sampler Sampler1` @ `GlCommandEncoder.trySetup:531`，调用链见上；**A/B 已实测与 ImmediatelyFast 无关**（去掉 IF 后同一异常、同一崩点照旧复现，且该组 `latest.log` 内 `ImmediatelyFast` / `BatchableBufferSource` / `(immediatelyfast)` 命中均为 **0**）、**与本模组无关**（对照组不画任何自定义几何也崩）；Iris 日志自证 `Missing program … in override list. This is likely an Iris bug!!!`。**上游 issue 草稿已备好**（可直接粘贴的英文稿）：`temp/t50/iris-sampler1-source-analysis.md` §4.2 —— 含环境 / 栈 / 源码链 / 影响面（dev-only）/ 建议修法（`ExtendedShader` 的 sampler 名单应与管线声明或 `RenderSetup` 实际槽位**取交集**，或把缺 `Sampler1`/`Sampler2` 视为可选）。**仍未提交上游**（本机无 token/gh，且是否上报待用户裁决）。

**源码取证（用户 2026-09-18 提供 IF 仓库地址，后续按源码分析；2026-09-19 依 A/B 结果把主体改为 Iris）**：
- **Iris（主嫌，必要条件方）**：<https://github.com/IrisShaders/Iris> —— 按本仓「第三方模组源码核验规则」（`AGENTS.md`）取源：**克隆到本仓之外**（如 `F:\MCProject\temp_iris\26.1`）、经代理 `http://127.0.0.1:7897`、按版本定位（**目标 = 本仓 `run\26.1.2\mods` 内的 `iris-neoforge-1.11.4+mc26.1.2.jar`**，2 756 643 B），结论须给「分支/tag + commit + 二进制版本」三件套与 `文件:行号 + 原文片段`；**不得**用 `javap` 反汇编或旧版本源码副本代替，且须用该 jar 内的 mixin 配置与 `META-INF/neoforge.mods.toml` 做**源码↔二进制交叉核验**（证明源码即现场二进制）。**已执行（2026-09-19）**：分支 `26.1` / commit **`bff1e69cb6c5519d8745784aa9c8b92984de67e7`**（`Fix normalMatrix in core profile (#3310)`，2026-09-14）/ 源码版本常量 `build.gradle.kts:20 MOD_VERSION=1.11.4` + `:7 MINECRAFT_VERSION=26.1.2` ⇒ 版本串 `1.11.4+mc26.1.2`（= jar 内 `neoforge.mods.toml` 的 `version`）；jar sha256 `32D672A890D3C74F75227ABC7E7EF048BBB83A799BF46632F03F0583D4C22BE5`（`temp/probe_mods/26.1.2` 与 `run/26.1.2/mods` **同哈希**，2 756 643 B）。⚠️ **取源偏差（引用时必须一并写明）**：Iris 的 26.1 线**没有任何 tag**（`ls-remote --tags` 内无 `1.11.*`）⇒ 钉不到发布 commit，只能用「分支 + commit + 三重替代证据」补强 —— 引用源文件自 `636937361`（把 `MOD_VERSION` 改成 1.11.4 的那次提交）起零变更、5 份 mixin 配置与 jar 逐字节同哈希（`mixins.iris.json` 等）、jar 内 `MixinGlCommandEncoder.class` 的 `javap -c -l` 行号表（93…160 / 164…172 / 176…178）与源码逐行一致。完整取证见 `temp/t50/iris-sampler1-source-analysis.md`。
  - **已回答（2026-09-19）**：见上方「机制（源码级确认）」段 —— 根因不是「Iris 跳过重绑」，而是 **Iris 用顶点格式推出 overlay 需求（`Sampler1`）并替换了 program，而通道侧仍只按原版 `RenderSetup.useOverlay` 绑 sampler**。已观测到的直接线索 `temp/t48/A-if-on/debug.log:1712` = `mixins.iris.json:MixinShaderManager_Overrides from mod iris->@Inject::redirectIrisProgram(…RenderPipeline;…)` 正是该替换点。
- **ImmediatelyFast（已由 A/B 实测排除，降级为附录）**：<https://github.com/RaphiMC/ImmediatelyFast> —— 克隆点 `F:\MCProject\temp_immediatelyfast\26.1`，已按版本钉到 **tag `v1.15.3` / commit `c010c5d5`**（对应现场 `ImmediatelyFast-NeoForge-1.15.3+26.1.jar`，312 276 B）。其既有分析**不得**作为崩溃成因引用；只在「为何 A 组栈里多一层 `BatchableBufferSource` 帧」这类旁证语境下使用（IF 自身确有两个 `GlCommandEncoder` mixin：`immediatelyfast-common.mixins.json:avoid_redundant_framebuffer_switching.MixinGlCommandEncoder`、`fix_slow_buffer_upload_on_apple_gpu.MixinGlCommandEncoder` —— 但 B 组证明没有它们**照样崩**）。

## 8. E 组 — 测试工具链与探针口径（2026-09-19 起）

### KI-E1 ＝ `AP_NOAI` 心跳/闸门读数恒为 `mobs=0`（**假阴性 ⇒ 进入世界那道「禁用生物 AI」闸门形同空洞**）

- **现象（2026-09-19 实测，只读取证）**：同一测试世界里**确有**2 只靶生物（`luluprep` 放的无 AI 猪与蜘蛛；用原版
  `/execute as @e[type=!player,distance=..16] run data get entity @s Pos` 逐只打印坐标确认：猪 `[7.5,-60,1.5]`、
  蜘蛛 `[7.5,-60,-0.5]`，玩家 `[7.5,-60,-4.5]`），而 `run/1.21.1/logs/latest.log` 里连续 6 条心跳一律为
  `AP_NOAI:mobs=0:noai=0:radius=128:forced=0:total=0:tick=…`。
- **后果**：`mt_launch` 的硬闸门判据是「读到 `AP_NOAI:` 且 `mobs == noai`」⇒ `0 == 0` **恒成立**，该闸门不提供任何保护；
  同时「每 2 tick 强制 `setNoAi(true)`」这条清扫本身是否命中过任何生物也无法由该读数证明。
- **嫌疑（未定；需在 `astral_test_noai.js` 侧定位，不属本模组产品代码）**：`noaiSweep` 取 `level.players()` 为空 ⇒ `continue`；
  或 `getEntitiesOfClass(Java.loadClass("net.minecraft.world.entity.Mob"), AABB.ofSize(...))` 的**类过滤**在 KubeJS/Rhino 下不匹配
  （同位置、同写法的 `LivingEntity` 过滤在探针里**可用** —— `luluDiscardNearby` 实测清掉了旧靶 ⇒ 差别只在类对象）。
- **规避**：涉及「世界级差值」的用例不要依赖该闸门的通过与否；本轮 lulu 用例改走**句柄读数** ＋ 靶自设 `setNoAi(true)`。
- ⚠️ 该文件属**另一会话的改动范围**（本轮未改它）；登记于此只为不丢失证据。

### KI-E2 ＝ 探针的 `typeIdOf(entity)` 对**所有**实体返回同一类型串 ⇒ 按类型取靶/计数不可用（**已改走句柄；根因未定**）

- **现象（2026-09-19 实测，同一轮内取证）**：`luluactive` 打印的诊断普查
  `census=minecraft:pig@12|minecraft:pig@6|minecraft:pig@16` —— 同一 ±8 盒内的三条 `LivingEntity`
  分别是**施放者（12 血）/ 那只猪（6 血）/ 那只蜘蛛（16 血）**（血量与 prep 布置逐条吻合，`pig_eid=1058` /
  `spider_eid=1059` / `p_eid=1` 三者互不相同），但 `typeIdOf(entity)` 对三者返回**同一个** `minecraft:pig`。
  ⇒ 同一实体的**身份与血量读数都是对的**，只有**类型串**是常量。
- **后果**：任何 `typeIdOf(x) == minecraft:xxx` 的取靶/计数都不可用 —— 前一轮 `luluprep` + `luluactive` 因此
  「按 `minecraft:pig` 取靶取到了施放者自己」（读数 `pig_hp=12->16` = 玩家血量）、`minecraft:spider` 一条也匹配不到。
  ⚠️ **前序解释的更正**：把症状解释为「技能 `apply` 之后按 AABB 搜索搜不到实体」**不成立** —— 同一轮诊断字段
  `scan8=3` 与 `census` 三条实体全部正确，搜索本身正常；**唯一的故障点就是类型匹配**。
- **可能成因（未定，嫌疑按序）**：Rhino / KubeJS 对 `entity.getType()`（或 `Registry#getKey(T)`）的方法分派缓存了
  **首次调用**的结果（本会话里 `BuiltInRegistries.ENTITY_TYPE.get(...)` 的首次调用 = `luluSpawnAt` 造猪）。
- **处置（本仓已实施）**：探针取靶改为由 setup 命令**发布句柄**（`luluprep` → 全局 `luluState` → `luluactive`
  跨命令沿用），判据一律读句柄；类型匹配只留在诊断字段里。⇒ **不要**再用 `typeIdOf` 写新判据；依赖它的既有命令
  （如 `countLightning` 的通用回退分支、`slimecheck` 等）需一并复核。

### KI-E3 ＝ **三个守门脚本不覆盖 fabric 线**（`audit_mixin_injection` / `check_lang_sync` / `audit_actionbar`）⇒ 该线对应面无自动守门（2026-10-01 登记）

- **现象**：该脚本的 `LINES = ["neoforge-1.21.1", "forge-1.20.1", "neoforge-26.1.2"]`（`tools/audit_mixin_injection.py:48`）
  **不含 `fabric-1.20.1`** ⇒ fabric 线（`astral_dice.mixins.json` 里登记的全部 mixin，含 `bridge/` 平台桥）
  的 `require` / `expect` 计数、`method` 目标、`@Mixin` 目标**完全不在任何自动守门覆盖内**；
  守门读数「合计注解 77 | 硬违规 0」**只统计三线**。
- **风险（本轮实证）**：2026-10-01 新增的 `bridge/ServerPlayerDimensionTravelBridgeMixin#astralDice$afterPlayerChangeDimension`
  （`@At("RETURN")` 派发 `PlayerChangedDimensionEvent`，供医疗箱筹码的「切维度触发」使用）**恰好落在这个缺口里** ——
  它的可用性当时只有「人工反汇编 + 与既有同构 mixin 逐字比对」这一条证据链
  （已实测通过：`@Inject` 的 `method` 被 Loom 重映射为 `method_5731(Lnet/minecraft/class_3218;)Lnet/minecraft/class_1297;`，
  与既有 `EntityDimensionTravelBridgeMixin` **逐字相同**）。**「守门全绿」在此不等于「fabric mixin 被守门」。**
- **同源缺口（2026-10-01 同日补记）**：本仓另有**两个**守门脚本同样只登记三线、不含 fabric ——
  - `tools/check_lang_sync.ps1`（硬编码 `@('neoforge-1.21.1','forge-1.20.1','neoforge-26.1.2')`）⇒
    **fabric 的三语一致性没有任何自动守门**（fabric 的键数也与三线不同：832 vs 830，属既存差异）；
  - `tools/audit_actionbar.py` ⇒ fabric 的动作栏键存在性未自动核验。
  ⇒ 凡改动 fabric 的 lang / 动作栏文案，**必须人工逐键核验**（本轮改看板娘 tooltip 与手册条目时即如此）。
- **候选处置（需裁决）**：把 `fabric-1.20.1` 加进上述脚本的 `LINES`。⚠️ 但这会让守门**立即暴露 fabric 既存全部 mixin 的读数**
  —— 可能出现既存硬违规（含 `intermediary` 名与描述符的解析差异），需要先做一次 baseline 采集与逐条裁决，
  **不宜与功能修复混批**。
- **判据（可重跑）**：`python tools/audit_mixin_injection.py` 的输出里**只有三线小节、没有 fabric 小节**。

## 9. F 组 — Fabric 1.20.1 移植线（2026-09-29 起）

> 本条线（子项目 `fabric-1.20.1` / 分支 `1.20.1-fabric`）与三条生产线**不共用存档、不共用前置**，
> 且单独以 **pre-release** 发布。平台差异的完整清单见 `porting/fabric-1.20.1/VERSION_PINS.md`「裁剪」，
> 规则边界见 `AGENTS.md`「多版本子项目矩阵」的第四条线说明。
> ⚠️ 下列 KI-F* **只适用于 fabric 线**；不要把它们当成三条生产线的缺陷，反向亦然。

### KI-F4 ＝ 测试资产与 datagen 的当前状态（**2026-09-29 登记**）

- **datagen**：已按 Fabric 的 `FabricDataGenerator` 体系重建（见 `fabric-1.20.1/build.gradle` 的
  datagen 运行配置与 `src/generated/resources` 的 sourceSets 接入）。
  ⚠️ 重建前本线曾出现「生成了 137 个物品模型 + 118 个配方却**不进产物**」——根因是移植时重写
  `build.gradle` **漏搬** `sourceSets.main.resources.srcDir('src/generated/resources')`。
  该类缺陷编译期与启动期**均无感**，只在游戏里表现为「物品紫黑、配方不存在」⇒
  改动构建脚本后必须**开包核对** `unzip -l` 的资源条目数，而不是只看 `BUILD SUCCESSFUL`。
- **测试资产**：fabric 线与三条生产线的测试机制差异极大（自建 `LoaderBus` + Puzzles Lib + FAPI 回调 +
  自写 mixin，而非 Forge 的 `@Mod.EventBusSubscriber`），故**测试流程与守门脚本需要单独一套**；
  详见 `scripts/test/fabric/` 与该目录下的说明。**不得**直接复用 `mt.ps1` 的生产线口径。

### KI-F7 ＝ 手册 `teru_sign.3` 缺键（**已补齐**，仅 fabric；另三线仍缺）

- **现象（修复前）**：`assets/astral_dice/patchouli_books/astral_guide/*/entries/signs/teru_sign.json`
  写了 **3 个文本页**（`.1` 主动 / `.2` 被动 / `.3`），但 `lang/{zh_cn,en_us,ja_jp}.json` 只定义了
  **`.1` 与 `.2`** ⇒ 玩家翻到第 3 页会看到**原始键名** `astral_dice.guide.entry.teru_sign.3`。
- **范围**：对照 23 个立牌条目，**只有 teru_sign 页数与键数不成对**（其余 22 个全部一致）
  ⇒ 这一条的**漏写**，不是系统性机制问题。⚠️ forge / 1.21.1 / 26.1.2 **同样缺**
  （既有内容缺失、非移植引入）；按「本分支只改 fabric 端」的裁决**只补了 fabric**，
  另三线若要一并补，直接套用下表同样的文案即可。
- **补齐依据（三条全部可复算，无杜撰）**：
  1. **档位词** = `AstralRarities` 的档位 —— `TERU_SIGN` 是 `legendary()`；同档的 `megas_sign.3`
     手册里写的是「传奇档 / Legendary tier / レジェンダリー段階」⇒ 用 megas 交叉验证了
     「手册档位词 ↔ 代码稀有度」的对应关系（该对应关系也是判定 **KI-F8** 的依据）；
  2. **材料** = 配方文件逐字读出：pattern `GCG/LEL/ZPZ`，`Z` = `diamond_dice`、`P` = `golden_star_plate`
     且 `P` 在 pattern 中出现 **2 次** ⇒「钻石骰子 + 黄金星盘 ×2」；
  3. **句式与术语** = 对齐 `hanna_sign.3` 的既有结构；物品译名取自各语言文件里已有的 `item.astral_dice.*`。
- **落地文案**（三语各 +1 行，键数 828 → **829**，三语仍互为一致）：

  | 语言 | 值 |
  |---|---|
  | `zh_cn` | `配方：钻石骰子 + 黄金星盘 ×2（传奇档）。` |
  | `en_us` | `Recipe: diamond dice + golden star plate ×2 (Legendary tier).` |
  | `ja_jp` | `レシピ：ダイヤモンドのダイス + 黄金の星盤 ×2（レジェンダリー段階）。` |

- **验收**：`tools/verify_fabric_assets.py` 第 6 项（手册闭环）已由 FAIL 转 **PASS**，
  该键也从脚本的 `LANG_KEY_ALLOW` 白名单**移除**（白名单重新为空）。

### KI-F8 ＝ 两个立牌手册的「档位词」与代码稀有度不符（**四线共有；只登记，未改**）

- **现象**：`hanna_sign.3` 写「稀有档 / Rare tier」、`sherry_sign.3` 写「史诗档 / Epic tier」，
  但两者的代码稀有度**都是** `AstralRarities.bizarre()`（奇特）。
- **判据（第三个样本交叉验证）**：`megas_sign` 代码 = `legendary()`，手册写「传奇档 / Legendary tier」
  ⇒ **手册的档位词应与代码稀有度一致**；据此 hanna / sherry 的手册文案**至少有一条错**
  （实际是两条都与代码不符）。
- **影响**：玩家在手册里读到的品质档位与物品实际稀有度（tooltip 边框 / 文字色，见 §1.1 索引中的 KI-F1 与 `RarityTooltipFrame`）
  **不一致** ⇒ 属**内容错误**（与 KI-F7 的"显示键名"性质不同）。
- **档位体系（照 `starengine_lib` 的 `Rarity`，供后续裁决用）**：
  `RARE`（稀有 #55FFFF）/ `EPIC`（史诗 #FF55FF）/ `LEGENDARY`（传奇 #FFC24B）/
  `PINNACLE`（巅峰 #FF4D4D）/ `BIZARRE`（奇特 #FF4D4D）。
- **为什么只登记不改**：修它必须先定「以代码为准还是以文案为准」——
  - 以**代码**为准：两条要改成「奇特档」，但 **`奇特(bizarre)` 这个档位词在手册里没有先例**，
    英/日文写法需要定（`BIZARRE` 的序列化名是 `astral_dice:bizarre`，可作英文候选；日文无先例）；
  - 以**文案**为准：要改**代码里的稀有度**（`bizarre()` → `rare()` / `epic()`），
    会连带改 tooltip 配色与整个稀有度体系的分布。
  两条路都需要裁决，故本轮不动。
- **注意**：这是**内容层面的既有问题、与移植无关**，四条线表现一致。

### 附：本线的资源/注册闭环守门脚本（发现 KI-F7 的那套检查）

`tools/verify_fabric_assets.py` —— 7 项闭环一次跑完（物品↔模型↔贴图 / 标签↔提供者 /
音效↔sounds.json↔ogg / 粒子↔贴图清单 / 三语键集 + java 引用键 / 手册引用物品·配方·键 /
创意标签覆盖）。**只读，退出码 0=PASS、1=存在缺陷、2=缺产物 jar**。
⚠️ 脚本内的 `LANG_KEY_ALLOW` 是「已知但暂不修」的**显式白名单**（仍会打印出来，不静默）；
新增白名单项必须写明「为什么不能修 + 需要谁裁决什么」。当前**白名单为空**。

### KI-F21 ＝ 1.3.5（`1.3.4..multi-main`）同步落地到本线（**已完成，2026-10-01**）—— 含两处**平台机制**适配与一处上游缺陷

- **范围界定**：`multi-main` 上 `1.3.4..HEAD` 共 **20+ 提交**（1.3.5 开发周期），`git diff --name-only 1.3.4..multi-main | grep '^fabric'` = **0**
  ⇒ 与 KI-F19（已处理，见 §1.1）同类：**改动只落在三线 + tools/release，不含 `fabric-1.20.1`**，故继续按文件移植（`forge-1.20.1` 为蓝本 + 路径改写 + `git apply --3way`）。
- **移植规模**：`git diff 1.3.4..multi-main -- forge-1.20.1/`（排除 `gradle.properties`）= **65 个文件 / 3452 行**；
  其中 62 个（非语言文件）一次性 `--3way` 应用，**11 个文件 15 处冲突**逐处手工解决；lang 三语**不**走 patch，
  改用 **JSON 键级合并**（基线 `1.3.4` ↔ 目标 `multi-main`，只把「新增 / 值变更」写进本线，保留本线独有的键）。
- **11 处冲突的解决口径**：
  | 文件 | 冲突性质 | 解法 |
  |---|---|---|
  | `DiceCombatEvents`（2 块） | ours 为空、theirs 新增 `isHostileAttack` + 敌对判定 | 取 theirs |
  | `ModTooltipHandler`（3 块） | modId `enigmaticlegacyplus` ↔ `enigmaticlegacy` | 取 theirs 的 modId，**保留本线 `ModList` 入口** |
  | 4 个 chip + `NancyLuSignItem`（各 1 块） | import：Forge 事件 ↔ 本线 `platform.event.*` | **保留本线 import**，只追加 `<br>` 新增的 `PartyRelations` |
  | `KomachiSignItem`（1 块） | theirs 删除了「冷却中拒绝」闸门 | 取 theirs（连带下线 `msg.*_cooldown` 键） |
  | `EffectRenderingInventoryScreenMixin`（1 块） | 注释：两线**都**已把 `require` 修成 1 | 取 theirs（注释更完整） |
  | `ModRecipeProvider`（1 块） | theirs 用 `PartialNBTIngredient`（**Forge 专有类**） | ⚠️ **手工**：Z 保留本线写法（`Items.POTION` + 下方 `NbtAugmentedRecipe` 的 `regenerationPotion()` 约束），**只采纳 X / D 换料**（末影珍珠 / 灵魂沙） |
  | `LootInjectionHandler`（2 块） | theirs 含 `onLootTableLoad(LootTableLoadEvent)` —— **Forge 事件** | ⚠️ **手工**：只保留 `starPlateDropCount` / `rollKillStarCoin`，**舍去整个箱子注入方法**（本线由 `loot/FabricLootInjector` 用 Fabric API 的 `LootTableEvents.MODIFY` 承担） |
- **编译暴露的平台差异（cleanly 应用的补丁也带 Forge API）**：`grep -rn "net.minecraftforge" src/main/java/` 命中 2 处需改 ——
  - **`item/sign/MosesEnigmaticLink.java`（新移植文件）**：4 行 `net.minecraftforge.*` import + `@Mod.EventBusSubscriber`。
    本线的 `platform/event/TickEvent.ServerTickEvent` **已有** `phase` / `getServer()`（与 Forge 同形）⇒ 只换 import；
    ⚠️ `@Mod.EventBusSubscriber` 在本线**无对应机制**（Fabric 注解不参与事件注册）⇒ 删除注解，改为在
    `AstralDiceMod#onInitialize` 里 `LoaderBus.INSTANCE.register(MosesEnigmaticLink.class)`（按字母序插在 `MimiSignItem` 之后）。
  - **`ModTooltipHandler:1299`**：脚本的 `modid` 规则只覆盖了 3 个块，第 4 处（非冲突区、由补丁 cleanly 带入）残留
    `net.minecraftforge.fml.ModList` ⇒ 一并改为本线 `ModList`。
- **datagen 必须重跑（本仓硬要求）**：`ModRecipeProvider` 改了 16 件配方 ⇒ `:fabric-1.20.1:runDatagen` **重新生成 17 个文件**
  （`written: 17`），实测产物取证：`komachi_sign.json` 用 `minecraft:amethyst_shard`、`adrenaline_low_chip.json` 用
  `minecraft:ender_pearl` + `minecraft:soul_sand` 且 `astral_nbt` 的再生药水约束保留 ✅。⚠️ datagen **正常退出**（11s），
  未出现 1.3.5 文档提到的「datagen JVM 不退出」（本线 `require` 已修，见 KI-F20）。
- **lang 合并结果**：三语各 **832 键**（合并前 832；`+1` 新增 `tooltip.astral_dice.sign.moses_enigmatic`、`-1` 移除
  `msg.astral_dice.komachi_active_cooldown`、21 个键值更新）。⚠️ `main` 侧另删了 `astral_dice.guide.entry.special_effects.6`，
  **但本线手册 `patchouli_books/.../en_us/entries/getting_started/special_effects.json:28` 仍引用该键** ⇒
  **本线刻意保留该键**（删掉会让手册显示原始键名）。⚠️ 同一问题在 `main` 侧同样存在（其手册未改、键被删）⇒ 属**上游缺陷**，见下。
- **版本号**：本线 `1.3.5-alpha.1+fabric_1.20.1`（与主线 `1.3.5` 对齐、保留本线 `-alpha.x` 口径）；库不动（仍 `1.0.5-alpha.1`）。
- **验证读数（全部实测）**：
  | 批 | 内容 | 结果 |
  |---|---|---|
  | A | 构建 + 开包核对 / env（both + rcon）/ 服务端启动 13.4s / 4 条用例 / 在线派发 / 收停 | rc 全 0；`FAB-JAR-ASSETS` 1、`FAB-BOOT-EMBED` 9、`FAB-DISPATCH-BASIC` 8、`FAB-INJECT-ROUNDTRIP` 3 —— **全 PASS**；无残留进程 |
  | B | 客户端启动 19.5s + `FAB-CLIENT-BOOT` | rc 0；**8/8 PASS**；无残留 |
  | C | lang 三语一致 / 工具链语法门 54 文件 / 模组来源口径 / 资源闭环 | 三语 **832/832/832** 一致；语法失败 **0**；`violations=0`；**8 项闭环全 PASS** |
  | **生产** | `ft_prod.ps1` 在**真实整合包**启动 36s + `-PreloadClasses 'class_485,class_329,class_310,class_746,class_8002'` | **PASS**（`Sound engine started`；加载清单 `astral_dice 1.3.5-alpha.1+fabric_1.20.1` + `starengine_lib 1.0.5-alpha.1`）⇒ **生产映射下 mixin 注入成功** |
- **⚠️ 未做（如实说明）**：**进世界**的验证未跑 —— `ft_prod.ps1` 的 quickplay 通道曾于 2026-09-29 误删玩家存档
  （`Deleting level 新的世界`），故本轮**刻意不使用** `--quickPlay*`（该参数已加安全护栏，须显式
  `-AcknowledgeQuickPlayDestructive` 才放行）。⇒ 世界内的行为（附件注册、效果面板悬停注释、∞ 显示、配方实际可合成）
  **尚未在实机确认**，需要时请明确授权并接受存档风险（或先备份存档）。
- **⚠️ 顺带发现的上游缺陷（登记，未改主仓）**：`multi-main` 删除了 lang 键 `astral_dice.guide.entry.special_effects.6`，
  但 `forge-1.20.1` 的手册条目 `getting_started/special_effects.json` **仍引用它** ⇒ 手册该行会显示原始键名。
  本线以「保留键」规避；主仓需在其工作树内单独裁决（改手册 or 恢复键）。

### KI-F22 ＝ 队友判定（FTB Teams / OPAC）后端**恒为未启用**：反射契约 5 处签名对不上，且失败只打一条 debug（**已修，2026-10-01**）

**一句话**：`PartyRelations` 里接 FTB Teams 的那段反射**从来没有生效过** —— 它取的 4 个访问器方法在**嵌套接口**上、
外层类上没有；取客户端队伍用的 `ClientTeamManager#getTeamForPlayer(Player)` 这个方法也**不存在**。
而 `resolve()` 把所有解析放在同一个 `try` 里 ⇒ 第一处就抛 `NoSuchMethodException` ⇒ 整个 FTB 后端被关掉，
**只留一条 debug 日志**。装 FTB Teams 的整合包里，队友照样可以互相伤害、电磁炮照样把队友算敌对目标。

#### 为什么一直没被发现

| 因素 | 说明 |
|---|---|
| 不会编译报错 | 全是字符串 + 反射，`Class.forName` / `getMethod` 的签名不参与编译期检查 |
| 不会崩溃 | `catch (Throwable)` 吞掉，`enabled=false`，退回原版计分板 —— 「失败方向安全」这个设计**同时**掩盖了「从来没成功过」 |
| 只打 debug | `resolve()` 失败走 `LOGGER.debug`；正式日志级别下不可见 |
| 三个整合包都没装 | 1.20.1 包完全没有 FTB、三个包都没装 OPAC ⇒ 连「装了却不生效」都没人碰到（`why_ftb` 一直是 `ClassNotFoundException`） |

#### 核验基准（可复跑）

- **上游源码**：FTB Teams `1.20.1/main` @ `f7dcaa9c`（`mod_version=2001.3.2`），并**回溯核对最早 1.20.1 版本 `v2001.1.2-alpha`** —— 两版 API 形态一致（即此契约**从未**匹配过任何 1.20.1 版本）；
  OPAC `1.20` @ `3d73aae`（与类注释里记的 SHA 一致）。
- **发布产物**（比源码更权威）：`ftb-teams-fabric-2001.3.2.jar`、`ftb-library-fabric-2001.2.0.jar`、`open-parties-and-claims-fabric-1.20.1-0.31.6.jar`，逐个 `javap`。
- **脚本**：`tools/verify_party_api.py` —— 自动从 `PartyRelations.java` 抽取反射契约、与 jar 逐个比对，输出 PASS/FAIL。

#### 逐条契约对照（`javap` 实测）

| # | 本类原写法 | 真实产物 | 结论 |
|---|---|---|---|
| 1 | `FTBTeamsAPI.isManagerLoaded()` | **不存在**（在嵌套接口 `FTBTeamsAPI$API` 上） | ❌ |
| 2 | `FTBTeamsAPI.getManager()` | 同上 | ❌ |
| 3 | `FTBTeamsAPI.isClientManagerLoaded()` | 同上 | ❌ |
| 4 | `FTBTeamsAPI.getClientManager()` | 同上 | ❌ |
| 5 | `ClientTeamManager.getTeamForPlayer(Player)` | **无此方法**；真实入口 `getKnownPlayer(UUID) → Optional<KnownClientPlayer>` | ❌ |
| — | `FTBTeamsAPI.api()` | `static FTBTeamsAPI$API api()` | ✅ |
| — | `TeamManager.arePlayersInSameTeam(UUID,UUID)` / `getTeamForPlayerID(UUID)` | 一致（返回 `Optional<Team>`） | ✅ |
| — | `Team.getId()` | 一致 | ✅ |
| — | OPAC 五处（`OpenPACServerAPI.get/getPartyManager`、`IPartyManagerAPI.getPartyByMember`、`IServerPartyAPI.getId/isAlly`） | **全部一致** | ✅ |

#### 修法（不止「让方法找到」）

1. **访问器改在嵌套接口上解析**：`Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API")`；
   若某版把方法挪回外层类，代码里保留了 fallback（先试嵌套接口，失败退回外层类）。
2. **客户端改走 `getKnownPlayer(UUID)`**；⚠️ `KnownClientPlayer` 是 **record**，访问器是 **`teamId()`（没有 `get` 前缀）**。
3. **「同队」比的必须是 party 团队 id**：`Team#getId()` 对玩家队伍等于**该玩家自己的 UUID**
   （同一 party 的两名成员 `getId()` 各不相同）⇒ 客户端改用 `KnownClientPlayer#teamId()`
   （其值在 FTB 侧就是 `PlayerTeam#getTeamId()`，与服务端 `arePlayersInSameTeam` 同源）。
4. **`hasTeam` 不能用「存在 Team 对象」判定**：FTB 给**每个玩家**都建了个人队伍 ⇒ 恒为真
   ⇒ 会把 `collectTeamPlayers` 的「未组队 ⇒ 友方作用于全服」兜底彻底堵死。
   判据改为 `Team#isPartyTeam() || Team#isServerTeam()`（缺这两个方法时退回「有队伍」，
   刻意的偏向 = 宁可少走兜底，也不要把它放大成对全服生效）。
5. **日志噪音**：失败原因分两档 —— `ClassNotFoundException`（= 没装，绝大多数玩家的正常状态）走 debug；
   其余（= 装了但签名不符，开发者才需要看）走 warn。
6. **新增可断言机器行** `AP_FAB_PARTY`（`PartyRelations#reportBackends()`，挂在 `AstralDiceMod#onCommonSetup`，
   与其它 `AP_FAB_*` 同族）：
   ```
   AP_FAB_PARTY: sw_mc=.. sw_ftb=.. sw_opac=.. back_ftb=on|off back_opac=on|off why_ftb=.. why_opac=..
   ```
   从此「装了 FTB Teams 却 `back_ftb=off`」在日志里一眼可见，`why_*` 直接写明是哪个类/方法没找到。

#### 同一轮附带修掉的同类问题

- **绕过统一入口 1 处**：`BigBowlStewChipItem#isOwnedByAlly` 用裸 `owner.getTeam() == petOwner.getTeam()`
  ⇒ FTB / OPAC 的队友被漏判，改为 `PartyRelations.isSameTeam`。
- **失效引用 9 处**：「同队收集」从库侧 `EventTargetCollector` 迁到模组侧 `PartyRelations` 时只改了调用点，
  留下 9 个文件的 `import`（其中 4 个已完全无引用）与 3 处仍指向库的 javadoc `{@link}`（误导性文档）。
  已全部清除；`grep -rn "EventTargetCollector" fabric-1.20.1/src/main/java/` 现在只命中 `PartyRelations`
  自身「为什么不用库那份」的说明注释。
- **库侧仍未修（交库处理）**：`starengine_lib` 的 `EventTargetCollector` 有两个同类缺陷，本线已全部改走
  `PartyRelations`、不再依赖它：
  1. `findFtbTeam` 在 `TeamManager` 上反射 `getTeamForPlayer(Player)` / `getTeamForPlayer(UUID)` —— 两者都不存在
     （真实签名 `getTeamForPlayer(ServerPlayer)` / `getTeamForPlayerID(UUID)`）⇒ 恒返回 `null`；
  2. `findOpacParty` 查的类名 `dev.darkhax.opac.api.OpenPartiesAndClaimsAPI` **不存在**（真实为 `xaero.pac.*`）。
  ⚠️ 该缺陷影响**另外三条线**（它们没有 `PartyRelations`，仍走库那份），应在库下一次发版中一并修。

#### 验证读数（四态运行时 A/B/C/D + 两道静态闸门，全部实测）

| 轮次 | 环境 | `AP_FAB_PARTY` 读数 |
|---|---|---|
| A | dev 客户端，未装 FTB / OPAC | `back_ftb=off back_opac=off why_ftb=ClassNotFoundException:…FTBTeamsAPI why_opac=ClassNotFoundException:…OpenPACServerAPI` |
| B | dev 客户端 + FTB Teams 2001.3.2 + FTB Library 2001.2.0 + Architectury 9.1.13 | `back_ftb=on back_opac=off why_ftb=OK` |
| C | 同 B + OPAC 0.31.6 | `back_ftb=on back_opac=on why_ftb=OK why_opac=OK` |
| D | **专用服务端** + FTB + OPAC（队友判定的实际执行侧） | `back_ftb=on back_opac=on why_ftb=OK why_opac=OK`，启动干净、零告警 |

- **静态契约闸门** `tools/verify_party_api.py`：**修复前 `PASS=9 / FAIL=5`**（红灯精确指出那 5 处）
  → **修复后 `PASS=17 / FAIL=0`**。
- **静态资源闸门** `tools/verify_fabric_assets.py`：**8/8 PASS**（三语 832/832/832）。
- **测试台**：新增用例 `FAB-PARTY-BACKENDS`（6 条断言）已入批 A，**PASS**；批 A 五条用例全绿、派发 `ServerTickEvent=599`、收停无残留。
- 原始读数留档：`temp/party_verify/READINGS.txt`；jar 与修复前快照：`temp/party_verify/`。

⚠️ **未做**：进世界的**行为级**确认（两名玩家组队后互相攻击是否真的免伤）—— 需要双人实机，dev 单进程无法覆盖；
当前证据到「后端已启用 + 契约与发布产物逐条一致」为止。

### KI-F23 ＝ 🚨 **dev 环境启动阻断**：Loom 重映射第三方 mod 时**剥掉 `fabric.mod.json` 的 `jars` 声明**，导致依赖内嵌库（JiJ）的 mod 在本线 dev 里全部起不来（**已缓解并落工具，2026-10-01**）

**一句话**：本线 dev 环境下，凡「自带内嵌库」的第三方 mod（Puzzles Lib / Accessories / Patchouli / Cloth Config / KubeJS）
都**无法启动** —— Loom 把它们重映射进 `.gradle/loom-cache/remapped_mods/` 时，把产物 `fabric.mod.json` 里的
`jars` 字段**整条删掉**，而 `META-INF/jars/*.jar` 文件本体仍留在包里（且**未经重映射**，仍是 intermediary 名称）。

#### 复现与判定（可复跑）

| 证据 | 读数 |
|---|---|
| 原始 Modrinth 产物 | `puzzles-lib-…jar` 的 `fabric.mod.json` = `jars: [{"file": "META-INF/jars/puzzlesaccessapi-fabric-20.1.1.jar"}]` |
| Loom 重映射后 | `.gradle/loom-cache/remapped_mods/…/puzzles-lib-c2d2b86c-N8gFdljq.jar` 的 `jars = None`；`META-INF/jars/puzzlesaccessapi-fabric-20.1.1.jar` **仍在**，内含 `class_xxxx` ⇒ 未重映射 |
| 不是缓存陈旧 | 把整棵 `remapped_mods` 移走、删配置缓存后**重新生成**，结果**内容相同**（`jars=None`、`fabric.mod.json` sha1 一致、条目数一致；⚠️ **原始字节不同**—— zip 时间戳，由独立子代理实测抓出，勿写成“逐字相同”）（Loom 1.14.10 的确定行为） |
| 启动期故障 | `HARD_DEP_NO_CANDIDATE puzzleslib … {depends puzzlesaccessapi}`；补上后又依次暴露 `NoClassDefFoundError: io/wispforest/endec/util/MapCarrier`（Accessories）、`io/github/fablabsmc/fablabs/api/fiber/v1/…/ConfigType`（Patchouli） |
| 命中面 | 口径必须写清：**宿主 jar 8 个**含 `META-INF/jars`，其中 **7 个内嵌库的 mod id 在 Loom classpath 上不存在**（另 60 个是 Fabric API 子模块 / CCA，classpath 上已有独立条目，**不能重复投放**，否则 Loader 判 duplicate mod 直接拒启） |

#### 缓解（已落地）

`tools/loom_embedded_jars.py` —— 扫描 Loom 重映射产物，把「内嵌且 classpath 上不存在」的 jar 抽出来投放到
`run/<side>/mods/`。**之所以投放即可生效**：Loom 的 dev 启动配置写了
`-Dfabric.remapClasspathFile=<subproject>/.gradle/loom-cache/remapClasspath.txt`
（见 `fabric-1.20.1/.gradle/loom-cache/launch.cfg`）⇒ **Fabric Loader 在 dev 模式会对 `mods/` 目录里的 jar 做运行时重映射**，
故原样投放未重映射的内嵌 jar 即可，无需自行 remap。与测试台既有的「第三方 jar 丢进 run 目录」手法同源（README §7.2.5）。

- 只报告：`python tools/loom_embedded_jars.py`
- 投放：`python tools/loom_embedded_jars.py --apply`（默认两侧）；或经 `ft_env.ps1 --install-embedded`
- 回收：`python tools/loom_embedded_jars.py --clean`（按 `run/<side>/mods/.astral_embedded_jars.json` 清单精确回收，
  **不动**他人手工投放的 jar）

⚠️ **换后端态后必须重跑一次**（`-PtestTrinkets` / `-PtestAccessories` 会改变 classpath ⇒ 去重集合会变）。

⚠️ **未做的**：根因在 Loom 一侧，本仓只能缓解。若将来 Loom 修好（保留 `jars`），本工具会退化为「无待补项」并打印
`need=0`，可安全保留。

### KI-F24 ＝ Fabric 移植线的两处**内容缺口 / 结构分叉**（**只登记，未改** —— 2026-10-02 发布前审计新增）

**来源**：1.3.6 发布前审计（`03eb2297`/`e654dae5` 那批的连带发现）。此前**未见于任何登记**，容易被当成「未修 bug」反复排查。

- ① **`concealment`（秘密侦探「隐匿」）整条特性在 fabric 线不存在**：三线各有 `effect/ConcealmentEffect.java` 与
  `ModEffects.CONCEALMENT` 注册，并在 `DiceCombatEvents#onLivingDamagePre` 里有「玩家对非玩家实体造成有效伤害即解除隐匿」的判定；
  fabric 线**既无该类、也无该效果注册、也无那段解除逻辑**。判据：`tools/verify_effect_icons.py` 的注册计数
  **52（三线）vs 51（fabric）**，差集恰为 `concealment`；`grep -rn "ConcealmentEffect|concealment" fabric-1.20.1/src` 命中 0。
  ⚠️ 这是**整条玩家侧特性缺失**，不是键或文案问题。
- ② **`DiceCombatEvents#onLivingDamagePre` 与三线**结构不同构：fabric 把 `directEntity instanceof Player` 闸门提到计时器逻辑**之前**，
  因此「白泽赐福 / 降神计时器」在 fabric 只认近战（用 `player`），而三线挂在闸门**之前**、按「任意攻击」启表（用 `source.getEntity()`）。
  ⇒ **既存**语义分叉（非 1.3.6 引入），需单独裁决是否对齐。
- **另**：本轮已顺带修掉一条同源缺陷 —— fabric 的 `BonnieSignItem` / `HaiqingSignItem` 调用了三线有、fabric **三语全缺**的两个
  `msg.astral_dice.*` 键（动作栏会直接显示原始键名）。修法与判据见 `TESTING-SPEC.md` 附录 A 续 46 §A。


## 10. G 组 — 游戏内内容与获取途径（2026-10-01 重建）

> 本组登记**玩法内容层面的缺陷**，以及**「用户裁决的必然推论」形成的可刷路径**：
> 获取途径缺失、池子漏项、文案与实际不符等。处置口径 = **先登记、后由用户裁决**；
> 改动前必须确认「这是不是有意的设计取舍」。

### KI-G2 ＝ 医疗箱筹码的「重登 / 切维度触发」可被反复利用（**用户裁决的必然推论；只登记，未改**）

- **背景**：2026-10-01 用户裁决医疗箱筹码在**四个时点**各完整触发一次治愈（加点 → 按当前层数×2 回血 →
  起/重置 1:00 计时器），其中包含「**重新登录后**」与「**切换维度后**」（筹码仍在槽位）。
- **现象（该裁决的数学推论）**：这两条路径的触发判据是「闸门未置位」，而登录 / 切维度事件都会**先释放闸门**
  （`HealingManager#refreshMedkitEquipSession`，语义 = 「新的装备会话开始」）⇒ **每次重登、每次过门都会再完整触发一次**：
  未满层时 +1/+3 层；**层数已满 32 时仍会直接按 32×2 回血**（`equipTrigger` 里的 `triggerHealing` 与层数是否满无关）。
- **性质**：这不是实现缺陷，而是「裁决要求这两处触发」的**必然结果**。但它在效果上等价于
  **「反复重登 / 反复过门 ⇒ 反复免费回血」**，与项目既有红线（星光「卸除即扣除」是为了防「反复装卸刷资源」）
  属同一类风险面；差别在于治愈点**不能兑换任何东西**、且 1:00 会自然减半 ⇒ 危害等级低于星光那条。
- **判据（可重跑）**：`event/PlayerLifecycleHandler` 的 `PlayerLoggedInEvent` 与 `PlayerChangedDimensionEvent`
  处理器里都有 `HealingManager.refreshMedkitEquipSession(player);` + `HealingManager.triggerMedkitOnEquip(player);`；
  且 `item/HealingManager#equipTrigger` 中 `triggerHealing(player)` 是**无条件**调用。
- **候选处置（需用户裁决，勿擅自实施）**：
  1. **接受为设计**（改动量 0）：把「重登 / 过门各触发一次」视为裁决的一部分，仅在文档里如实披露；
  2. **节流**：为这两条路径加冷却（例如同一玩家 N 秒内最多一次），或只在「闸门上次因**死亡**释放」时才触发；
  3. **撤掉这两个时点**：只保留「装备时」与「重生后」（改动量 = 删除两处调用 + 手册文案回退）。
  ⚠️ 无论选哪条，都**不得**改动已裁决的「装备时 / 重生后必须触发」，也不得改动三档概率、
  `HEALING_TIMER_SECONDS = 60`、`HEALING_POINT_CAP = 32`。
- **相关文档**：`AGENTS.md`「治愈流派规范 → 医疗箱筹码」；两份 `CHANGELOG` 的 `1.3.6`；
  `scripts/test/TESTING-SPEC.md` 附录 A 续 44。

## 11. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-09-15 | 建档：登记 B7 交付后审计发现的 11 项未修/未决项（用户裁定「作为未来版本修补内容」） |
| 2026-09-16 | KI-5 确认根因并**按推荐方案修复**（1.20.1 法伤主链路事件 `LivingHurtEvent` → `LivingDamageEvent`，双版本对等）；新增回归用例 `ELECTRIC-GLOVE-AOE-BASE-{1.21.1,1.20.1}` |
| 2026-09-16 | 追加 A（《恋的规则书》赠书守卫随死亡保留）**闭环**：1.21.1 首登 + 重登两轮用例各 7/7 PASS；追加 B（双版本死亡保留集合一致性）核为一致 |
| 2026-09-16 | 新增「测试工具链」纪律（见 `AGENTS.md` / `scripts/test/TESTING-SPEC.md` §12）：严格分层硬预算、`MT_WAIT` 心跳、进度信标 `cases/.mt_progress.json`、看门狗语义判据、探针自动同步、点火入口 `mt_fire.ps1`（根治「长驻孙进程持有调用方管道 ⇒ 命令早已结束却看起来永不返回」） |
| 2026-09-17 | 主线 `multi-1.20.1-1.21.1`(8f68482)→ `multi-dev-next` 合并:旧「待命等待器」由目标选择器取代(见 §6 KI-M1)、三态化与选择器共存(KI-M2);登记选择器类立牌锁定保护的待修项与库侧过渡符号清理项(KI-M3) |
| 2026-09-17 | 库侧过渡符号清理**完成**（库 `d5b0776` / `1.0.0-SNAPSHOT.5`：删 `ReadyEffect`、事件框架三件套 `AstralEventType`/`EventContext`/`EventEffect`、收集链、三个事件常量；**保留** `SKILL_WAIT_SECONDS` / `TARGET_SELECT_RADIUS` / `EVENT_APPLY_MC_TEAM|FTB|OPAC` / `collectTeamPlayers`）；消费方 2 处未使用 import 已由 `7dc64cb` 清除、三线 0 处实际使用；KNOWN-ISSUES 据此更正 **KI-M3 第 3 条**（旧「无消费方 / 未实施」结论 → 实测口径 + 已实施），并登记 **KI-M4** 两条开放项（① 库未 push ⇒ CI 钉住的 ref `d5b0776…` 在推送前必然 checkout 失败；② 消费方 `SIGN_READY_TYPE`/`SIGN_READY_EXPIRE` 废弃键去留待裁决，三线 22 处引用不得静默删除） |
| 2026-09-17 | **26.1.2 接入 starengine_lib**：构建/元数据接线（`mavenLocal()` + `implementation` 库坐标 + 三个版本键 + `starengine_lib` required 依赖段）+ 删 26 个库已提供的本地副本并改写引用（83 处 FQN/import 就地改写、27 条同包补 import、配置缝改走 `applyConfig(GameplayConfigValues)`）；唯一保留本地副本 `effect/ReadyEffect`；旧「待命等待器」与 33 个效果注册**行为未变**；三线构建 + 模组来源闸门 + lang 同步全绿。登记 **KI-M5** 两项开放项（整合包缺库 jar、ReadyEffect 本地副本例外） |
| 2026-09-18 | 新增 **§7 D 组（平台/第三方冲突）** 与 **KI-D1**：26.1.2 + 光影（Complementary/Iris）+ ImmediatelyFast 的 `Missing sampler Sampler1` 崩溃，经**对照组**（不执行选择器仍崩、`capture hook active`/`target_prism` 均 0、崩溃报告内本模组帧数 0）确证**与本模组无关**；同时登记「复用 entitySolid 缺 Sampler1」假设**被证伪**、「自建仅 Sampler0 管线」方案**被实测否决并回退**；IF 开/关 A/B 因工具链限制**未完成**（可行路径已写明） |
| 2026-09-19 | **KI-D1 归因更正（A/B 三组实测完成）**：A（IF 开 + 光影开）崩、B（**IF 关** + 光影开）**仍崩且日志内无任何 IF 帧**、第三组（IF 关 + **光影关**）**不崩** ⇒ **排除 ImmediatelyFast**、必要条件锁定为**光影/Iris**；旧的规避建议「用光影时不要同时装 IF」**作废**，改为「26.1.2 开光影即有风险，规避 = 关光影 / dev 加 `-Dneoforge.disableGlValidation=true` / 接受风险」；前序「0.9.1 下光影正常」的口径更正为**仅覆盖启动期**（本崩溃在进入世界 1–3 分钟内出现）。源码取证主体由 IF 改为 **Iris 1.11.4+mc26.1.2**（IF 已钉 `v1.15.3`/`c010c5d5` 并降级为附录） |
| 2026-09-19 | **KI-D1 源码级定案 = Iris 侧缺陷 + 两处旧记载更正**：Iris 26.1 线 commit `bff1e69c…`（无 tag，用三重替代证据补强，jar sha256 `32D672A8…22BE5`）。机制链确认：Iris 用**顶点格式**推出 overlay 需求（`ExtendedShader.java:119-121` + `IrisVertexFormats.java:52`）并**替换 item 管线的 program**（`IrisPipelines.java:36-37`/`:188-222`、`MixinShaderManager_Overrides.java:53-66`），而通道侧只按原版 `RenderSetup.useOverlay` 绑 sampler（`RenderPipelines.java:75-82`、`RenderTypes.java:151-174`）⇒ 只有这次替换会索要 `Sampler1`，由**原版**校验 `GlCommandEncoder.java:526-532` 抛出。**更正①**：「`trySetup` 被 Iris 替换」不成立（Iris 仅在 HEAD 对自定义通道条件性 cancel、RETURN 追加状态，对 `samplers` 表只读不写）。**更正②**：「`getOrCompilePipeline` 抛 `Throwable`」不成立（`MixinShaderManager_Overrides.java:67-72` 只打日志，返回 `null` 回落原版 program）。**新增定性**：该断言 **dev-only**（`GlRenderPass.java:25` 的 `VALIDATION`，生产只 `continue`），故玩家侧影响面小；上游 issue 英文草稿已备（`temp/t50/iris-sampler1-source-analysis.md` §4.2），未提交 |
| 2026-09-19 | 新增 **§8 E 组（测试工具链与探针口径）**：**KI-E1** = `AP_NOAI` 心跳/闸门读数在确有靶生物时恒为 `mobs=0`（假阴性 ⇒ 「禁用生物 AI」硬闸门 `mobs == noai` 恒成立、形同空洞；嫌疑在 `astral_test_noai.js` 侧，未改该文件）；**KI-E2** = 探针 `typeIdOf(entity)` 对**所有**实体返回同一类型串（实测 `census=minecraft:pig@12\|minecraft:pig@6\|minecraft:pig@16` ⇒ 三条实体的身份/血量全对、只有类型串是常量），按类型取靶因此不可用 —— 探针已改为「setup 发布句柄 + 判据只读句柄」，并**更正**前序把症状解释为「`apply` 之后搜不到实体」的误判（`scan8=3` 证明搜索正常）。KI-M2 补登既有缺陷：四个门控立牌（ren/bonnie/haiqing/moses）**漏写 `sign_active_max_cooldown`**（电流核心档位分母偏），史莱姆立牌已按正确口径成对写入 |
| 2026-09-27 | **KI-D1 复现 + dev 规避落地（已实测闭环）**：用户手动 `/time set 18000` 后崩，报告 `run/26.1.2/crash-reports/crash-2026-09-27_10.28.05-client.txt`，栈与 §7 记载**逐帧一致**（B 组形态：`MultiBufferSource$BufferSource.endBatch(:99)` 与 `LevelRenderer.lambda$addMainPass$0(:707)` 之间**没有任何 ImmediatelyFast 帧**）；Iris jar sha256 `32d672a8…22be5` 与定案版一致；报告内 `com.merlinkitsune.*` 帧数 = **0**。⚠️ **`/time set` 是触发条件而非根因**——它只改变主通道（`addMainPass`）的渲染内容，把上游缺陷更早"踩"出来；KI-D1 既有对照组（去掉任何玩家操作、同环境同召唤物）已证明崩溃不依赖特定玩家操作。**已落地规避**：`neoforge-26.1.2/build.gradle` 的 `client` run 加 `jvmArgument '-Dneoforge.disableGlValidation=true'`，正对那处 **dev-only** 断言 （`GlRenderPass.VALIDATION = SharedConstants.IS_RUNNING_IN_IDE && !Boolean.getBoolean("neoforge.disableGlValidation")`，`GlCommandEncoder.trySetup` 的整段 sampler 校验被 `if (GlRenderPass.VALIDATION)` 包住）⇒ 生产环境校验收起、不受影响。**验证证据**（同环境 + 光影）：会话 `latest.log` 10:36:15→10:37:37，10:36:28 `Time from main menu to in-game was 3.89s`（真进世界），10:37:16 注入 `/time set 18000` → `[Dev: 已将minecraft:overworld设为18000刻]`（真执行），其后 21 s 观察窗内 **new crashes = 0 / `Missing sampler` 命中 = 0 / 客户端存活**。⚠️ **代价**：该校验是 dev 下唯一会报「自家渲染管线 sampler/vertex-format 配错」的闸门，关掉后这类问题会被一并静音 ⇒ 一旦怀疑本模组自定义几何（target_prism 等）有渲染问题，**须临时注释该行**恢复校验。 |
| 2026-09-29 | 新增 **§9 F 组（Fabric 1.20.1 移植线）** —— 本仓第四条线（子项目 `fabric-1.20.1` / 分支 `1.20.1-fabric`）的已知问题：**KI-F1** = 两个配方因 1.20.1 原版**无 `IngredientType`**（`unzip -l` 实证）而从「指定药水」放宽为「任意药水」，属**平台能力限制的真缺口**，已按「保功能 + 显式登记」处理，严格保真需自建 `RecipeSerializer`+`Ingredient`（待裁决）；**KI-F2** = 跨加载器存档不互通（附件 vs Capability/ForgeData；Trinkets/Accessories vs Curios），平台差异、不修补；**KI-F3** = 双饰品通道的槽位聚合语义（`data/curios/tags/items/*.json` 是本线的物品清单单一事实源，**勿因命名空间而删**；门面改动须验三种组合）；**KI-F4** = datagen 已按 Fabric 体系重建，并登记「重写构建脚本漏搬 `sourceSets.srcDir('src/generated/resources')` ⇒ 255 个生成资源不进产物」这一**编译期与启动期均无感**的缺陷模式（收尾须开包核对）。同轮修复的四个 fabric 线硬缺陷（产物资源缺失 / `onCommonSetup` 未注册导致网络与卡牌注册表从未初始化 / 12 个事件有 handler 无派发源 / `data/bountiful` 未裁剪）已并入上述条目与 CHANGELOG。 |
| 2026-09-29 | **KI-F1 定案（严格保真）+ 新增 KI-F5 / KI-F6**：1) 两个「指定药水」配方改为自建序列化器 `astral_dice:nbt_shaped` + `NbtShapedRecipe`（覆写 `matches`；JSON 与网络层均委托原版 `ShapedRecipe.Serializer`）⇒ 原「放宽为任意药水」方案作废；语义基准取 **1.21.1 / 26.1.2 的 `DataComponentIngredient.of(true, …)`**（`forge-1.20.1` 的 `PartialNBTIngredient` 按「本分支只改 fabric 端」的裁决**保持原样**，`potionTag(...)` 保留）；2) **KI-F5** = `c:bricks` 在 Forge 47.x 上无任何提供者（`Tags.Items` 里没有 `BRICKS`）⇒「对怪板砖」配方原本永不可合成，现按 NeoForge 的定义在**本线**自建（`#c:bricks/normal` + `#c:bricks/nether` = `minecraft:brick` / `minecraft:nether_brick`），`forge-1.20.1` 侧**未动**；3) **KI-F6** = 新增 `platform/event/SubscriptionAudit`：扫描本包 `@SubscribeEvent` 与已登记集合做差集，补 `dispatchReport()` 看不见的「忘了 register」盲区，`-Dastral_dice.strictBusAudit=true` 可升级为致命错误。 |
| 2026-09-29 | **撤销 forge 侧改动 + 新增 KI-F7 与本线资源闭环守门**：1) 按用户裁决「本分支只改 fabric 端」，把 `ae76390b` 里属于 forge 的部分全部恢复为 `aadaf8a7`（`ModRecipeProvider` 的 `PartialNBTIngredient` / `potionTag(...)` / generated 配方 / 三个自建 `c:` 标签）⇒ `git diff aadaf8a7 -- forge-1.20.1/` 为空；KI-F1 表格与 KI-F5 的表述同步改为「有意保留的三线差异」；2) **新增 KI-F7** = 手册 `teru_sign.3` 在四线都缺键（玩家会看到原始键名），已取到配方与稀有度依据但**档位词口径不明**（`HANNA_SIGN` / `SHERRY_SIGN` 代码同为 `bizarre()` 而手册写着 `Rare` / `Epic`）⇒ 只登记不补写，附两个可选修法；3) **新增守门脚本 `tools/verify_fabric_assets.py`**（7 项闭环：物品↔模型↔贴图 / 标签↔提供者 / 音效三件套 / 粒子清单 / 三语键集 + java 引用键 / 手册引用 / 创意标签覆盖）—— 本轮体检 12 项里 11 项 PASS、仅 KI-F7 一项 FAIL（已白名单 + 可见打印）。 |
| 2026-09-29 | **KI-F7 已补齐 + 新增 KI-F8**：为 `teru_sign` 手册第 3 页补上三语键（zh_cn「配方：钻石骰子 + 黄金星盘 ×2（传奇档）。」/ en_us / ja_jp）—— 档位词用**交叉验证**确立（同档的 `megas_sign.3` 手册写「传奇档 / Legendary tier」而代码是 `legendary()`），材料从配方文件逐字读出（Z=diamond_dice、P=golden_star_plate ×2），句式对齐 `hanna_sign.3`；三语键数 828 → 829 且仍互为一致，守门脚本第 6 项 FAIL → PASS，并从 `LANG_KEY_ALLOW` 移除（白名单重新为空）。同时**新增 KI-F8**：`hanna_sign.3` / `sherry_sign.3` 的档位词（稀有档 / 史诗档）与两者代码稀有度（均为 `bizarre()` 奇特）**不符** ⇒ 按同一判据二者至少一条错；因「以代码为准」（需先定 `奇特` 的英/日写法）与「以文案为准」（要改稀有度体系与 tooltip 配色）两条路都需裁决，**只登记未改**。 |
| 2026-09-29 | **客户端进世界验证：修复 3 个阻断级缺陷（KI-F9 / KI-F10 / KI-F11）** —— 首次带玩家进世界的客户端验证暴露：① **KI-F9** `KeyBindingSetup.ClientEvents` 内部类漏登记 ⇒ **J（立牌主动技能）/ H（卡牌栏）两键完全不响应**（另三线靠 `@EventBusSubscriber` 自动注册内部类，fabric 需显式登记而移植时只登记了外层类；由本轮新增的 `SubscriptionAudit` 抓出）；② **KI-F10** `MobEffectEvent.Remove` 构造器对 null 效果实例 NPE ⇒ **玩家无法进入存档**（登录清理骰神赐福 → 库 remove 不判存在性 → Puzzles 回调传 null → 构造器解引用；Forge 侧同路径**不派发**，且该类 javadoc 与全部 4 处订阅者都按「可为 null」写，只有构造器漏了防御）⇒ 修两处：PuzzlesBridges 回调跳过 null + 构造器判空；③ **KI-F11** `ScreenEvent.Opening` 在 `setScreen(null)`（`ReceivingLevelScreen.onClose`）时 NPE ⇒ **每次进世界必崩**（Forge 的 `ScreenEvent` 构造器同样 requireNonNull ⇒ 它在 null 时不构造事件）⇒ mixin 加 null guard。同轮为 fabric 线补上另三线早已有的 `-Pquickplay` 与固定窗口尺寸的 client run 参数（⚠️ Loom 用 `programArg`，不是 ModDevGradle 的 `programArgument`）。修复后客户端正常进世界、连续运行 600+ tick，`RenderLevelStageEvent=10326` / `RenderHandEvent=3441` 证明世界内渲染链路在派发；物品栏内本模组立牌渲染正常（非紫黑格）。 | 
| 2026-09-29 | **测试台修复：fabric 侧「完全不可用」的根因（新增 KI-F12）** —— 首次真机跑通 `env→launch→inject→stop` 主干，修掉 4 个测试台缺陷：**D1** `ft_launch` 的就绪游标被 log4j「每次冷启动轮转 latest.log」击穿 ⇒ 启动阶段**必然假超时**（实测服务端 9 s 就绪、台子干等 240 s）；**D2** 游戏运行时 latest.log 被独占而 `File.ReadAllBytes` 是 `FileShare.Read` ⇒ 「在线读日志」全线不可用（此前被 D1 掩盖，读分支从未真正执行）。二者改用**文件身份锚点**（创建时间 + 头部 SHA1 + 长度）+ 共享读写打开，并加「超时时若日志已有就绪标记则报 ready-but-undetected」的反向自检；**D3** KubeJS 命令队列通道经实测**从根上不可实现**（类过滤挡住 `java.nio.file`/`java.io`、bindings 无文件包装器、`java`/`Packages` 全局已移除）⇒ 整体撤除，命令注入统一走 RCON，KubeJS 收敛为读数探针；**D4** 补 `--gradle-arg` 透传，客户端得以带 `-PtestSodium=false` 启动。新增客户端启动用例 `FAB-CLIENT-BOOT`，`FAB-INJECT-ROUNDTRIP` 改用 rcon；5 条用例全绿，闸门 / FAIL / PASS 退出码逐条实证。 |
| 2026-09-29 | **fabric 侧完整冒烟（用户显式要求）跑通并抓出 D6/D7（KI-F12 扩为 7 项）** —— 按 `TESTING-SPEC.md` §1.1，用户显式要求是全清单的唯一自动放行条件。三批全绿：批 A（构建 / env / **经编排入口起服务端 11.5s** / 在线断言 / 4 条用例 / 在线派发读 / 收停）、批 B（客户端 12.4s + `FAB-CLIENT-BOOT` 8/8）、批 C（4 个本线静态闸门）。合计 5/5 用例 + 4/4 闸门、无残留。**首次跑批 A 时卡在 launch 步 6 分钟不返回**，顺「子脚本早已打印 OK」这条线索查出两个新缺陷：**D6** `Invoke-FtChildProcess` 用 `Start-Process -Wait`（会等整棵进程树）⇒ `ft.ps1 --phase launch` 挂到游戏退出（项目自己的 §13 就记过这个坑）；**D7** 修好 D6 后暴露：该封装用 `ReadAllBytes` 回读子进程重定向输出，与 D2 是同一个 `FileShare.Read` 共享模式陷阱 ⇒ 改走 `Read-FtFileBytesShared` + `Dispose()`。 |

| 2026-09-29 | **整合包启动崩溃定位（新增 KI-F13）** —— 用户报「整合包启动失败」。崩溃链 `NoSuchFieldException: color` → `AstralRarities.<clinit>` → `astral_dice` 的 main entrypoint 失败。根因 = 库 `starengine_lib_fabric` 的 `AstralRarities` 静态块**按字符串名反射原版 `Rarity` 字段**（`getDeclaredField("color")` / `getDeclaredField("$VALUES")`），而 **Fabric 的 dev 与生产是两套映射**：dev(named) 字段就叫 `color`/`$VALUES`（所以历次 dev 冒烟全绿），生产(intermediary) 同一个类是 `class_1814`、字段是 `field_8908`/`field_8905` ⇒ **生产环境 100% 启动失败，且所有现有验证手段都测不出来**。证据：整合包 jar 与仓库产物 **sha1 完全相同**（排除陈旧产物）+ 两侧 `javap` 对照（结构同构、仅字段名不同；用「枚举字符串字面量不参与重映射」反查出 `class_1814`）。⚠️ 有两个断点（`color` 先崩、`$VALUES` 紧随其后同样会崩）。全仓扫描确认只有这两处（其余反射目标是 JDK 成员或模组自有名字，安全）；另一库仓 `starengine_lib` 无此模式。修法 = 改为**按类型/修饰符找字段**（映射无关）；修复需库版本 bump + 重发布 + 消费方同步 + 重推整合包（版本号待裁决）。并登记流程缺口：dev 全绿 ≠ 产物可用，建议补「生产映射守门」（静态扫反射字符串常量 / 动态把推给整合包的 jar 真启动一次）。 |

| 2026-09-29 | **KI-F13 已修复并验证** —— 库 `starengine_lib_fabric/fabric-1.20.1` 的 `AstralRarities` 原按**字符串名**反射原版 `Rarity` 字段（`getDeclaredField("color")`/`("$VALUES")`），在 dev(named) 能跑、在整合包(intermediary) **100% 启动崩**。改为按**类型/修饰符**查找（映射无关），只改 fabric 子项目（另三线用 Mojang 官方映射，`color` 本就是 `color`，不受影响）。版本号按用户裁决**退回 1.0.5 基线 + `-alpha.x` 后缀** ⇒ 库 `1.0.5-alpha.1`、主模组 `1.3.2-alpha.1+fabric_1.20.1`。**新增生产映射冒烟工具 `scripts/test/fabric/ft_prod.ps1`**（本轮先以临时探针跑通，后落为正式工具）：在**真实整合包实例**（生产 intermediary 环境）启动一次并按「崩溃报告新增 / 入口点失败 / 到主菜单」三类判据裁决。实测：修复前 `CRASH_REPORT`（19:07，新增崩溃报告）→ 修复后 **`AP_FAB_PROD_READY: elapsed=23s`、crash-reports 无新增**；dev 侧回归同绿。产物实证：内嵌件 `starengine_lib-fabric-1.20.1-1.0.5-alpha.1.jar`；javap 反编译确认 `class_1814` + `lookupColorField`、`String color`/`String $VALUES` 已消失。⚠️ 仍未做：库提交未 push、CI 的库 ref 待同步（属跨仓发布策略）。 |
| 2026-09-29 | **KI-F14 已修 + 生产冒烟能力再升级** —— 用户报「创建世界时崩，提示 architectury 报错」。定位：本模组 `TooltipRenderUtilColorMixin` 用 3 个 `@ModifyConstant` 改写 `TooltipRenderUtil#renderTooltipBackground` 的三个 ldc 常量，而 **Architectury API 改的正是同一批常量** ⇒ Mixin 的 `@ModifyConstant` 排他语义使 architectury 被 skip、其 `defaultRequire=1` 抛 `InjectionError` → 崩。⚠️ 目标类 `class_8002` 只在**首次渲染 tooltip** 时才加载 ⇒ 「能进主菜单、进世界/悬停 GUI 才崩」；且 dev 的 `run/client/mods` 无 architectury ⇒ **dev 永远绿**（再次「生产独有」）。修法 = 改用 **`@WrapOperation`**（包裹 INVOKESTATIC，与改 LDC 的 `@ModifyConstant` 是不同字节码位置 ⇒ 可共存），四个 wrapper 覆盖全部 6 个调用点。**产物取证三级**：注解已被 Loom 直重映射（jar 内无 refmap ⇒ 那条警告无害）、`-Dmixin.debug.export` 导出的类里 4 个 wrapper 全在**且 architectury 注入同时在**、生产冒烟 PASS（并已强制 preload 该类）。顺带修 **`astral_guide` 配方**（整合包无 Patchouli ⇒ `Unknown item` 报错）：加 `fabric:load_conditions`。**新增能力**：类预加载钩子 `-Dastral_dice.preloadClasses`（把「类加载期才暴露的 mixin 缺陷」提前到启动期，fail-loud）+ `ft_prod.ps1` 的 `-PreloadClasses`/`-ExtraJvmArg` 与 `MIXIN_FAIL` 判据 + Loom 侧 `-PpreloadClasses`。 |
| 2026-09-29 | **饰品栏前置改为「二选一」+ 修 dev 开关（新增 KI-F15 / KI-F16）** —— 用户报「调整前置要求：装了 accessories 就不再强制 Trinkets，反之亦然，只有两个都不存在才报错」。**KI-F15**：`fabric.mod.json` 原把 `trinkets` 写在 `depends` ⇒ 只装 Accessories 的整合包被加载器直接拒绝（`HARD_DEP_NO_CANDIDATE … {depends trinkets @ [>=3.7.2]}`），与本线把 Accessories 定为「主通道」的既有设计**自相矛盾**。Fabric 的 `depends` 是 **AND 语义、表达不了 OR** ⇒ `trinkets` / `accessories` 双双移入 `recommends`，权威判定改为运行时 `ModCompatibilityCheck#verifyAccessoryProviderOrThrow()`（在 `onInitialize()` 的**第一条**语句执行，两个都不在才抛 `ModLoadingException` 并给出完整中文说明；每次启动打 `AP_FAB_ACCESSORY_PROVIDER: OK|FAIL (trinkets=… accessories=…)` 供冒烟断言）。同时补齐三处硬引用守卫（`CuriosApi` 的 Trinkets 源 + `TrinketBridge.registerAll()` 的**调用点** + 方法体内第二道防线）—— ⚠️ `TrinketBridge` 硬引用 `dev.emi.trinkets.api.*`，缺席时**整个类都加载不了**，守卫必须放在**调用点**。**KI-F16**：dev 开关 `-PtestAccessories=false` **从来不能通过编译**（`StarCoinWalletButtons:311 错误: 方法不会覆盖或实现超类型的方法`）—— 根因是 Loom 的**依赖方接口注入**随「依赖是否进入**运行期**」而开关（Accessories 的 `custom.loom:injected_interfaces` 把 `AbstractButtonExtension` 注入 `AbstractButton`；移出运行期后注入消失 ⇒ 那个为「必须补上注入来的抽象方法」而写的 `@Override` 失效），而 `compileClasspath` 里 accessories **仍在** ⇒ 失效的是**注入**而不是类路径；修法 = 去掉该 `@Override`（保留方法体与 javadoc）⇒ **两种注入态都成立**。⚠️ 该开关的注释一直声称可用 ⇒ **「给了开关」≠「开关可用」**（凡承诺「用某参数可跑某态」就必须实跑一次）。实测：dev 四态（仅 Accessories / 仅 Trinkets / 两者同装 / 两者皆无）各起一次客户端 —— 前三种均 `Sound engine started` 且 `AP_FAB_ACCESSORY_PROVIDER` 读数与预期逐条一致（仅 Accessories 时**无** `NoClassDefFoundError`、Accessories 适配器注册而 Trinkets 适配器不注册；两者同装时两个适配器都注册），第四种按设计**拒绝启动**并给出中文说明。 |
| 2026-09-29 | **修「饰品栏贴图错误」+「手册重复发放」（新增 KI-F17 / KI-F18，两条都是**玩家可见**缺陷）** —— 用户报「饰品栏贴图错误（附截图）+ 重复发放帕秋莉手册再次出现」，并在上一轮修复后回复「问题依旧」⇒ 两条都**重新定位根因**（不是上次没修干净）。**KI-F17**＝**Fabric 附件键注册晚于玩家数据反序列化** ⇒ 一次登录**静默丢弃 33 个键**（含 `guide_book_given` ⇒ 守卫永远读 `false` ⇒ 每次登录补发一本；其余为治疗点数 / 立牌锁定冷却 / 白泽赐福 / 教主降神 / 怪力侦探层数等可感知战斗状态）。根因是 `ModAttachments` 的**静态初始化惰性**：mod 初始化路径上没代码触碰它，`<clinit>` 被推迟到玩家登录处理器 ⇒ 那时 NBT 已反序列化完 ⇒ Fabric 打 `Unknown attachment type … skipping` **静默丢键**（⚠️ 缺陷静默、测试世界数据少、NBT 里标记一直是 1 只是**读不出** ⇒ 三种取证方式都会漏掉）。修法 = `ModAttachments#ensureRegistered()`（空实现，只为触发 `<clinit>`，打 `AP_FAB_ATTACHMENTS: 附件键已注册 109 个`）+ 在 `onInitialize()` 紧随前置守卫后调用；另加常驻诊断 `AP_FAB_GUIDEBOOK`（含**写后立刻回读** `reread=`，正是靠它把方向从「发放时机」扭到「存取通路」）。**A/B 跨会话实测**（`-PdevUsername=AstralDev` 固定玩家名、两轮同一玩家）：session1 `given=false books=0`→`GIVEN reread=true books=1`；session2（21:01:37 注册 → 21:01:41 登录）`given=true books=1` ⇒ **不补发**；`latest.log`/`debug.log` 中 `Unknown attachment type` 命中 **0**。**KI-F18**＝**Accessories 槽位图标走原版 `minecraft:blocks` 图集**（`assets/minecraft/atlases/blocks.json` 声明 `{type:directory, source:gui/slot}`），而 Trinkets 是 `icon` 路径直连 ⇒ 图标必须在 `assets/<ns>/textures/gui/slot/`、`icon` 写**图集 sprite 名** `astral_dice:gui/slot/…`（同时满足两条通道）；原先放 `textures/slot/` ⇒ **文件在、图集里没有** ⇒ 紫黑格（这就是「改一轮还没好」的原因：不是文件缺失而是路径不在图集目录）。改 3 个文件移动 + 7 处 `icon` 改写；新增守门 `tools/verify_fabric_assets.py` 第 8 项（已做正/反向验证）+ 客户端自检 `AccessoriesClientIconCheck`（**同时查文件存在与图集成员资格**，⚠️ 只查文件存在不够）⇒ 实测 `AP_FAB_SLOT_ICON: 槽位=15 文件缺失=0 图集未收录=0`，本模组 3 槽位逐条 `file=yes inBlocksAtlas=yes`。同轮新增测试能力 **`-PdevUsername` 固定玩家名**（Loom 默认给**随机**用户名 ⇒ 离线 UUID 每次都变 ⇒ **跨会话缺陷在 dev 里根本不可能复现**，这才是「重复发放」这类缺陷长期隐藏的结构性原因）。详见 `scripts/test/fabric/README.md` §7.2.4。 |
| 2026-09-30 | **五项玩家可见缺陷按 dev-next 已修方案同步落地（新增 KI-F19）+ 发现并修正一处三线共有的 mixin 误写（新增 KI-F20）** —— 用户报「在主线版本中发现的 bug，在该分支中也应该存在」并要求「**直接同步 `multi-dev-next` 的改动，避免重复造轮**」。核实 dev-next HEAD = **`731e3855`**，与用户报的 5 条**逐条对应**。⚠️ **未做整分支 merge**：该提交只改三线（neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2）、**不含 fabric**，而 dev-next 领先本分支 **30 个提交**（2.0.0-SNAPSHOT.14 版本号 / 目标选择器 / 工具链）⇒ 合并只会污染移植线、带不来任何 fabric 代码改动；故改为**以 forge-1.20.1 为蓝本按文件移植**（路径改写 + `git apply --3way`，冲突逐处手工合并）。落地五项：① 王之力自伤改用新类型 `astral_dice:card_cost`（登记 `bypasses_cooldown`、**刻意不登记 `bypasses_armor`**）⇒ 不再被受击无敌帧整段吞掉（旧口径走 `dice_damage`，不在 `bypasses_cooldown` 内 ⇒ `invulnerableTime > 10 && amount <= lastHurt` 时 `hurt` 直接 false）；② 删除 `PlayerLifecycleHandler` 里**主动清零** jasmine 攻/防计数的那段（padman 保留），清零唯一路径回归 `clearSignData` ⇒ 扫地机加成死亡保留；③ `magic_tome_count`（原 10000 tick）与 8 个效果类的 `DURATION_TICKS` + 2 处施加点统一为 `MobEffectInstance.INFINITE_DURATION`（唯一渲染 ∞ 的值）；④ 连带修 `EffectTimerGuard` 永续判据（`-1` **不满足** `>= INFINITE_THRESHOLD` ⇒ 会被当成有限时长 forceRemove + 重加，常驻效果一施加就没）与 `MamushiDragonEffect.refresh` 判据；⑤ 1.20.1 无 NeoForge 的 `GatherEffectScreenTooltipsEvent` ⇒ 扩展 `EffectRenderingInventoryScreenMixin`（双 `@Redirect` + `ThreadLocal`，`formatDuration` 捕获实例 → `List.of` 改写列表）把 `effect.<id>.description` 追加进悬停 tooltip，且**与本线原有的等级角标 `@Inject` 并存**。**KI-F20**＝该 patch 的两处 `@Redirect` 写了 `require = 2` 并注释为「至少命中 1 次」——⚠️ `require` 语义是**最少**命中次数，而 `javap -c` 实证 `renderEffects`（50–273 行）内 `formatDuration` 与 `List.of` **各只 1 次**（另一次 `formatDuration` 在 `renderLabels`，已被 method 限定排除）⇒ 必然 `InjectionError`、**触发时机是打开物品栏**；之所以没炸是因为该提交自述「实机验证未做」（mt_launch 防撞预检拦下）。本线改为 `require = 1` 并留证；**三线待回补**（按「只改 fabric 端」裁决未动）。**验证**：compileJava / build SUCCESSFUL、产物已推整合包、静态守门 8/8 PASS（语言三语 **832/832/832** = 新增 3 个 `death.attack.card_cost*` 键）、**客户端预加载** `[preload] OK …EffectRenderingInventoryScreen` 且 `InjectionError` 命中 **0**。⚠️ 未做：进世界的**视觉确认**（注释行 / ∞ 符）与**生产映射冒烟**。 |
| 2026-10-01 | **1.3.5（`1.3.4..multi-main`）同步落地本线（新增 KI-F21）+ 三批冒烟与生产映射冒烟全绿** —— 用户要求「合并 1.3.5 更新内容，并执行行为测试和游戏内测试（冒烟）」。范围实测：`1.3.4..multi-main` 20+ 提交、**`grep '^fabric'` = 0** ⇒ 依旧只改三线，按文件移植（`forge-1.20.1` 为蓝本，65 文件 / 3452 行，`--3way` 后 11 文件 15 处冲突逐处手工解决）。**三处需要平台判断的地方**（本任务的技术核心）：① `ModRecipeProvider` 的肾上腺素配方 —— 上游用 `PartialNBTIngredient`（Forge 专有）⇒ 本线保留 `Items.POTION` + `NbtAugmentedRecipe` 约束、只采纳 X/D 换料；② `LootInjectionHandler` 上游新增的 `onLootTableLoad(LootTableLoadEvent)`（**Forge 事件**）⇒ **整方法舍去**（本线箱子注入由 `loot/FabricLootInjector` 的 `LootTableEvents.MODIFY` 承担），只保留 `starPlateDropCount` / `rollKillStarCoin`；③ 新移植的 `MosesEnigmaticLink` 带 4 行 `net.minecraftforge.*` import + `@Mod.EventBusSubscriber` ⇒ 换本线 `platform.event.*`（`TickEvent.ServerTickEvent` 的 `phase`/`getServer()` 与 Forge 同形）并删除注解、改 `LoaderBus.INSTANCE.register(...)`（Fabric 无注解自动注册）。lang 三语**不走 patch** 改用 **JSON 键级合并**（基线 `1.3.4` ↔ `multi-main`）⇒ 832/832/832 一致。**datagen 必跑并已跑**（`written: 17`，实测 `komachi_sign` 用紫水晶碎片、`adrenaline_low_chip` 用末影珍珠+灵魂沙且 `astral_nbt` 约束保留）。**验证**：批 A（服务端 13.4s + 4 用例 1/9/8/3 全 PASS + 收停无残留）、批 B（客户端 19.5s + `FAB-CLIENT-BOOT` 8/8）、批 C（三语一致 / 语法门 54 文件 0 失败 / 模组来源 `violations=0` / 资源闭环 **8/8**）、**生产映射冒烟 `ft_prod.ps1` PASS（36s 到主菜单，带 `-PreloadClasses class_485,class_329,class_310,class_746,class_8002`）**。⚠️ **未做**：进世界验证（quickplay 曾误删存档，本轮刻意不用；该参数已有护栏需显式 `-AcknowledgeQuickPlayDestructive`）⇒ 世界内行为待用户授权。⚠️ **上游缺陷登记**：`multi-main` 删了 lang 键 `guide.entry.special_effects.6` 但其手册仍引用 ⇒ 本线保留该键规避，主仓需单独裁决。 |
| 2026-10-01 | **队友判定后端「从来没生效过」定位并修复（新增 KI-F22）+ 四态运行时取证** —— 用户要求「搜索 FTB Teams / OPAC 的 1.20.1 Fabric 源代码，执行团队功能实现验证」。取证方式 = **克隆上游源码 + 下载发布 jar + `javap` 逐条比对反射契约**（并回溯到 1.20.1 最早版 `v2001.1.2-alpha`，确认该契约**从未**匹配过任何 1.20.1 版本）。**核心发现**：`PartyRelations` 接 FTB Teams 的反射**从头到尾没生效** —— 它取的 4 个访问器（`isManagerLoaded` / `getManager` / `isClientManagerLoaded` / `getClientManager`）声明在**嵌套接口 `FTBTeamsAPI$API`** 上、外层类上没有（`Class#getMethod` 不会跨到嵌套接口），客户端入口 `ClientTeamManager#getTeamForPlayer(Player)` 也**不存在**（真实为 `getKnownPlayer(UUID) → Optional<KnownClientPlayer>`）；而 `resolve()` 把全部解析放在同一个 `try` 内 ⇒ 第一处即抛 `NoSuchMethodException` ⇒ **整个 FTB 后端恒为未启用、只打一条 debug**（「失败方向安全」的设计同时掩盖了「从来没成功过」）。**修法**：访问器改从嵌套接口解析（保留「方法挪回外层类」的 fallback）；客户端改走 `getKnownPlayer(UUID)`，⚠️ `KnownClientPlayer` 是 **record**、访问器为 **`teamId()`（无 `get` 前缀）**；「同队」改比 **party 团队 id**（`Team#getId()` 对玩家队伍=该玩家自己的 UUID，同 party 两人各不相同）；`hasTeam` 判据改 `isPartyTeam() \|\| isServerTeam()`（FTB 给每个玩家都建个人队伍 ⇒ 用「存在 Team 对象」会恒真、把「未组队⇒友方作用于全服」的兜底堵死）；失败日志分两档（`ClassNotFoundException`=没装 ⇒ debug，其余=签名不符 ⇒ warn，避免给绝大多数玩家制造日志噪音）。**新增可断言机器行** `AP_FAB_PARTY: sw_* back_ftb/back_opac why_ftb/why_opac`（`PartyRelations#reportBackends()`，挂 `AstralDiceMod#onCommonSetup`，与其它 `AP_FAB_*` 同族）。**同轮附带**：修 `BigBowlStewChipItem#isOwnedByAlly` 一处**绕过统一入口**的裸 `getTeam()`；清掉迁移遗留的 **9 处失效引用**（9 个 `import` 中 4 个已完全无引用 + 3 处仍指向库的 javadoc `{@link}`）；登记**库侧两个同类缺陷**（`EventTargetCollector` 在 `TeamManager` 上反射 `getTeamForPlayer(Player)/(UUID)` 均不存在；OPAC 类名 `dev.darkhax.opac.*` 不存在）—— 影响另三条线，交库侧下次发版。**验证**：四态运行时 A/B/C/D（无第三方 / 装 FTB / 再加 OPAC / **专用服务端**）读数逐态符合预期（`off/off` → `on/off` → `on/on` → `on/on`，`why_*` 分别给出 `ClassNotFoundException:…` 与 `OK`）；新增静态闸门 **`tools/verify_party_api.py`**（自动抽取契约 ↔ 真实 jar 比对）**修复前 `PASS=9/FAIL=5` ⇒ 修复后 `PASS=17/FAIL=0`**；新增测试台用例 **`FAB-PARTY-BACKENDS`**（6 条断言）入批 A 并 PASS；批 A 五条用例全绿、派发 `ServerTickEvent=599`、收停无残留；资源闸门 **8/8 PASS**（三语 832/832/832）。⚠️ **未做**：进世界的**双人行为级**确认（组队后互相攻击是否真的免伤）—— dev 单进程覆盖不到。 |
| 2026-10-01 | **新增 KI-D2（Iceberg tooltip 颜色缓存残留）+ 缓解措施落地**：四线各新增 `client/IcebergTooltipCacheGuard`，在每次 tooltip 渲染开头把 `Tooltips.currentColors` 反射复位为 `DEFAULT_COLORS`（并按 Iceberg 是否自己管色自适应跳过）（三条 NeoForge/Forge 线走 `RenderTooltipEvent.Pre`，fabric 线走 `ClientTooltipBridgeMixin` 的 `renderTooltipInternal` HEAD），使**其它模组物品与原版物品**也不再继承上一个 tooltip 的颜色；同时**新增 §10 G 组**并登记 **KI-G1**（看板娘立牌 25 张战斗牌送筹码的池子硬编码、缺 20 个后加筹码，含充能类 10 个与飞星 2 个；只登记未改） |
| 2026-10-01 | **KNOWN-ISSUES 首次清理**（用户指令「检查是否还有未处理项，清理所有已处理项」）：把 20 个**已处理**条目（KI-M1 / KI-M5 / KI-D2 / KI-G1 / KI-F1·F2·F3·F5·F6·F9·F10·F11·F12·F13·F14·F15·F16·F17·F18·F19）与 §5 表中 7 行已闭环记录**移出正文**，改为 **§1.1 已处理索引**（id + 结论 + 证据落点）；§0 第 4 条「不要删条目」修订为「**已修项清理、未修项禁删**」并补「删前 grep 引用」要求；§1 计数更新为 **23 条未修/未决 + §5 的 2 条测试资产待修项**；KI-M4 标题更正为「原 2 项，第 1 项已闭环」；§10 G 组因条目清空而撤销、变更记录顺位为 §10。同批修复 **KI-G1**（看板娘筹码池改派生式，见 CHANGELOG 1.3.6 与 TESTING-SPEC 续 43） |
| 2026-10-01 | **新增 §10 G 组 与 KI-G2**：登记「医疗箱筹码的重登 / 切维度触发可被反复利用」（用户裁决「这两个时点各触发一次」的必然推论 —— 层数满 32 时也会按 32×2 回血）；同时记录 §1 计数 23 → 24，原 §10 变更记录顺位为 §11 |
| 2026-10-01 | **新增 KI-E3**：`tools/audit_mixin_injection.py` 的 `LINES` 不含 `fabric-1.20.1` ⇒ fabric 全部 mixin 无自动守门（本轮新增的切维度 mixin 正落在该缺口内，仅有「人工反汇编 + 与既有同构 mixin 逐字比对」一条证据链）；§1 计数 24 → 25（E 组 2 → 3） |
| 2026-10-01 | **新增 KI-F23（dev 环境启动阻断）**：Loom 1.14.10 重映射第三方 mod 时剥离 `fabric.mod.json` 的 `jars` 声明 ⇒ 本线 dev 里 Puzzles Lib / Accessories / Patchouli / Cloth Config / KubeJS 全部起不来（`HARD_DEP_NO_CANDIDATE` 与连续两条 `NoClassDefFoundError`）。判定链：原始产物有 `jars` / 重映射后 `jars=None` 而文件本体仍在且仍是 intermediary / 移走整棵缓存重新生成结果逐字相同（⇒ 确定行为，非缓存陈旧）；命中面 8 个 jar，其中 7 个内嵌库 id 在 classpath 上不存在。缓解 = 新增 `tools/loom_embedded_jars.py`（按 id 去重后投放到 `run/<side>/mods/`，靠 Loom 的 `fabric.remapClasspathFile` 运行时重映射生效）+ `ft_env.ps1 --install-embedded` 开关；§1 计数 25 → 26（F 组 6 → 7） |
| 2026-10-01 | **KI-E3 扩项**：该覆盖缺口不止 `audit_mixin_injection` —— `tools/check_lang_sync.ps1` 与 `tools/audit_actionbar.py` 同样只登记三线（fabric 的 lang / 动作栏变更须人工核验） |
| 2026-10-02 | 发布前审计处置：`KI-F20` **整块移出**（三面已全部闭环，证据见 §1.1）→ 移入 §1.1 索引；**新增 `KI-F24`**（fabric 移植线两处内容缺口 / 结构分叉，此前无登记）；§1 补澄清「26 = 23 条未修 + 3 条已修待实机」；顺带修掉 fabric 两个动作栏语言键缺失（见 `TESTING-SPEC.md` 附录 A 续 46 §A） |
| 2026-10-04 | **1.20.1 Forge 端两项用户实报缺陷修复（KI-F5 结案 + 新增常驻时长归一）**：① **通用标签命名空间写错** —— `forge-1.20.1` 的 `datagen/ModRecipeProvider` 照搬 NeoForge 侧的 `c:bricks`，而 1.20.1 Forge **只提供 `forge:` 命名空间、没有任何 `c:` 标签的提供者** ⇒「对怪板砖」配方材料永不可满足、**彻底无法合成**（开包实证：旧 jar 内既无 `data/c/tags/items/bricks.json` 也无 `data/forge/tags/...`，配方却写 `"tag": "c:bricks"`）。修法 = 配方改用 `forge:bricks` + 本模组自建 `src/main/resources/data/forge/tags/items/bricks.json`（= `#forge:ingots/brick` ∪ `#forge:ingots/nether_brick`；Forge 47.4.10 实测自带这两条、**无汇总 `forge:bricks`**，`Tags$Items` 亦无 `BRICKS` 常量）⇒ 与 NeoForge `c:bricks` 语义等价。**只改 Forge 端**（NeoForge 两线用自带 `c:bricks`、Fabric 线用其自建 `c:bricks`）。② **常驻效果在 Forge 端仍显示超长倒计时** —— 子代理在整合包存档中取到决定性证据：`astral_dice:charge Duration=2147482289`（旧版 `Integer.MAX_VALUE` 的递减产物，界面显示 `29826:08:34` 而非 `∞`）⇒ 根因是 1.3.4 的「常驻效果统一 ∞」**只改了施加点**，存量实例不会被任何既有路径修正（`EffectTimerGuard.record()` 对 `>= INFINITE_THRESHOLD` 的值直接跳过 ⇒ 不登记 ⇒ 永不校正；`tick()` 只遍历已登记条目且只写有限值；各施加点只在重新获得/消耗/切换装备时走到）。修法 = 三线 `PlayerTickEvents` 新增 `normalizeLegacyInfiniteDurations`（每秒一次，把**本模组**效果里 `duration >= Integer.MAX_VALUE/2` 的实例改写为 `-1`，只改时长、保留层数/粒子/图标位；判据用超长阈值而非效果清单 ⇒ 本模组最大合法有限时长 24000 tick（`RenShieldManager` 护盾 / 抗性刷新窗口;其余 ≤ 3600）、相差约 4.6 个数量级，双态效果 2400 tick 亦不受影响）。**验证**：三线 `build` SUCCESSFUL 且产物落地（实测 jar 内 `PlayerTickEvents` 含新方法、forge jar 含新标签文件、配方 JSON 已为 `forge:bricks`）；三线 jar 时间戳一致并已自动推送至各自整合包。 ⚠️ **未做**：进世界的视觉确认（∞ 是否如期显示）。⚠️ **Fabric 线未同步本批**（按其「独立批次」口径）。 |
