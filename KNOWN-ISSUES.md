# 已知问题与未来版本修补清单（KNOWN-ISSUES）

> **本文件是「未修项」的唯一版本化载体**。本地 `docs/` 目录被 `.gitignore` 忽略、不随仓库分发，故凡需要跨会话/跨分支可见的未修项一律登记在此。
> 本文件**只登记「尚未实施」的事项**；每条的最终状态必须如实反映现实，**禁止为了缩短清单而删除条目**。

## 0. 使用纪律（必须遵守）

1. **实施前必须先复核，不得直接照文档改**：本清单多数条目的定性来自 **2026-09-15 15:45 的扫描快照**，其后本批已改动过 `combat/DiceCombatModifiers`、`combat/SpellDamageRegistry`、`combat/HostileTargets` 等文件 ⇒ 「仍存在/未修」**不等于当前 HEAD 仍存在**。按 `mc-two-round-verification` 流程，先做**独立代码复核**（第一遍，验证者 ≠ 实现者），把「文档定性」与「当前 HEAD 实况」逐条对齐，再交用户定夺。
2. **三版本同步**：所有修补一律 `neoforge-1.21.1` + `forge-1.20.1` + `neoforge-26.1.2` 同时实施，保持功能对等（项目默认规则）。
3. **必须能被验证**：每条修补都要有可判定的判据（游戏内用例、或新增用例；`scripts/test/cases/` 与 `scripts/test/TESTING-SPEC.md`）。**不得**用「代码看起来对了」结案。
4. **结案方式**：改为「已修复」并注明 **commit + 验证证据**（用例名/读数），**同时**在两个 `CHANGELOG` 的对应小节按「合并约定」记录；**不要**删条目。
5. **行号会漂移**：本文件引用的行号取自扫描时的快照，复核时请**以引用的原文串/标识符为准**重新定位。

## 1. 登记信息

- 登记日期：2026-09-15
- 登记来源：B7 交付后遗漏审计（用户裁定「纪录上述问题，作为未来版本修补内容」）
- 目标版本：**下一版本**（1.2.1 之后；是否并入 1.2.1 需用户另行裁定）
- 条目总数：**11**（数值修饰器 7 + 未核验 1 + 更早扫描未决 3）

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

## 5. 已闭环但当日文档未回写（列此以免重复劳动）

| 曾标记 | 现状（2026-09-15 实测） |
|---|---|
| `docs/scan2/P5-equip-paths-linkage.md` 的 **13 处 `✘1需改`**（上下文可用却仍用单参 `isHostile`） | **已全部闭合**：当前代码实测 **双参 27 处 / 单参 5 处**；5 处分别为重载定义（`HostileTargets.java:39`）、javadoc（`:31`）、内部委托（`:55`）、`isBlessingTarget`（P5 自标 `—1`，有意）、`PandamanSignItem`（文档化的「嘲讽不施予玩家」） |
| `NancyLuSignItem` 的 `isHostile(target) \|\| target instanceof Player`（P5 #23/#24 ⚠️ 过宽） | **已替换为两参判定**（源码注释明写「旧写法会把队友与无关玩家也当作可攻击目标」，1.21.1 `:273-274` / `:286-287`，1.20.1 同构） |
| P2-C1 / P2-C3 / P2-C4 / 安全气囊致命判定基准 | 文档自标「已修」 |
| `mt_assert` 断言窗口「自 launch 起」 | **已改为自本用例起**（commit `cc49f0d`；`assert.scope` = `case`/`launch`/`whole`） |
| `LOOT-MODIFIER` 反向断言丢启动期覆盖 | **已修**（补 `scope: whole`，commit `c0ad51f`，实测读数 `@0B(win=whole)`） |
| **追加 A**：《恋的规则书》「仅首次进入世界发放一次」被违反 —— 死亡重生后再登录会**补发**一本 | **已闭环（2026-09-16 实测）**：守卫附件 `guide_book_given` 原先不随死亡复制 ⇒ 死亡后新实体回默认 `false`，而发放挂在 `PlayerLoggedInEvent` ⇒ 重登必补发。修补 = 把该键纳入**死亡保留集合**（1.21.1 `component/ModAttachments.java` 的 `GUIDE_BOOK_GIVEN` 加 `.copyOnDeath()`；1.20.1 `component/AstralData.java#onPlayerClone` 死亡白名单加同键），两侧键集合保持三个（`rin_pages` / `komachi_damage_bonus` / `guide_book_given`）。用例 `GUIDE-BOOK-FIRST-JOIN-ONLY-{1.21.1,1.20.1}` 必须**跑两次**（首登 + 不跑 env 的重登）：1.21.1 两轮均 **7/7 PASS**（重登读数 `AP_G1_GUIDE:given=1:count=1`，2026-09-16 00:47，耗时 29 s）；**1.20.1 两轮同样 7/7 PASS**（首登 + 不跑 env 的重登，2026-09-16 00:52 前后，读数同为 `AP_G1_GUIDE:given=1:count=1` / `AP_G3_GUIDE:given=1:count=1`）⇒ **双版本均严格遵循「仅第一次进入世界发放一次」** |
| **追加 D**：`SPELL-TRUE-DAMAGE` 的**护甲读取在 1.21.1 上失效、且用例断言过弱**（测试资产缺陷） | **已登记，待修**（2026-09-16 实测）：同一用例两边都判 PASS，但取证读数质量天差地别 —— 1.20.1 读到 `AP_SD_ARMOR_CTRL:armor=rc=1:tough=rc=1:read=rc=2` 与 `AP_SD_ARMOR_EFFECTIVE:ctrl_arm=armor=20:tough=8:test_arm=armor=20:tough=8`（真实护甲 20 / 韧性 8），**1.21.1 读到 `rc=undefined`、四组全为 `armor=0:tough=0`**（护甲属性根本没读上）。因该用例的断言**只匹配键名前缀**（`AP_SD_ARMOR_EFFECTIVE:`），1.21.1 侧「基础伤害吃护甲」这一条实际是**空证据**仍判 PASS。修法：① 探针在 1.21.1 侧改用能读到护甲/韧性的属性入口；② 断言加严为「必须读到 armor=20:tough=8 之类非零值」，读不到即 `ERROR`（**禁止**为变绿而放宽） |
| **追加 B**：双版本「死亡保留集合」是否一致 | **一致**（两侧均为 `rin_pages` / `komachi_damage_bonus` / `guide_book_given`；`AGENTS.md` 与代码逐条对应），不属差异项 |
| **追加 C**：新用例 `ELECTRIC-GLOVE-AOE-BASE` 的**探针取数缺陷**（测试资产缺陷，**不是产品缺陷**） | **已登记，待修**（2026-09-16 实测）：首次执行即 FAIL，取证读数 `AP_GB_ERR:base_source:no_read:calls=0` + `AP_GB_GA:base=-1:raw=8:self=2.24:nbr=2.24:armor=20:rarmor=0:mode=forced_survival:armed=1:bsrc=no_read`。**判据**：`self == nbr == 2.24` —— 真正重要的**对等不变量成立**；失败的是「读 `base`」那一路（`bsrc=no_read`，注册进生产修饰器表的探针读取器**一次都没被调用**）⇒ 断言 7/8/9 因 `base=-1` 不匹配而 FAIL，断言 12（absent）又抓到探针自己吐出的 `AP_GB_ERR`。**结论**：该用例当前**不可用**，在任何版本上都会 FAIL；KI-5 的**游戏内**数值验证因此**尚未完成**（代码级证据见本文件 KI-5 段：事件映射 + jar 内 class 常量池 `LivingHurtEvent=0 / LivingDamageEvent=4`）。修法方向（择一，需先复现 `calls=0` 的原因）：① 让探针改从**已确定会被调用**的路径取 `ctx.event` 的值；② 或改判据为「同一版本内 `self == nbr` 且跨版本同值」这类不依赖生产修饰器被调用的对照。**禁止**为了让它变绿而放宽断言 |


---

## 6. 主线 → multi-dev-next 合并（2026-09-17）的并入裁决与遗留项

> 本节记录 `multi-1.20.1-1.21.1`（`8f68482`）→ `multi-dev-next` 的合并中，两套「立牌主动技能释放模型」的
> 取舍与遗留项。合并提交（merge commit）的提交信息里有同一份结论（本节为可版本化载体）。

### KI-M1 ＝ 旧「待命等待器」由目标选择器取代（已裁决，非缺陷）

- **dev-next 侧（采用）**：`target/TargetSelectionManager` + `TargetSelectionAction` /
  `TargetSelectionRegistry` / `TargetType` —— 按下主动键即进入**目标选择会话**，确认目标后**即时释放**；
  取消/超时不消耗冷却。
- **主线侧（未采用，已随合并移除）**：基于 `sign_ready_type` / `sign_ready_expire` 的「待命等待器」+
  `BaseSignItem.isSkillWaiting` / `tickSignReadyTimeout` + 三个立牌的 `*_ready` 提示效果
  （`haiqing_ready` / `bonnie_ready` / `moses_ready`）—— 按下主动键进入待命，**等下一次攻击命中**才释放。
- 取舍理由：两者是同一功能的两种实现，**不能并存**（否则同一按键存在两条释放路径）；dev-next 的选择器
  已是三线共享库的一部分，且 2.0.0 的界面、文案与服务端校验都按它书写。故合并方向 = **选择器胜出**，
  主线的等待器代码与其专属效果一并移除；`SIGN_READY_TYPE` / `SIGN_READY_EXPIRE` 附件保留为**废弃键**
  （已无任何写入方）。⚠️ 原「待库侧过渡符号清理任务一并删除」的预期已被实测**更正**：这两个键定义在
  **消费方**（三线各自的 `component/ModAttachments`），且三线仍有 22 处引用 ⇒ 去留见 **KI-M4②**，
  **不得静默删除**。
- **对脚本侧读数的影响（重要）**：自动化用例的 `astraldice_ts_*` 读数**只反映选择器会话**
  （`TargetSelectionManager` 的状态），`sign_ready_type` / `sign_ready_expire` 恒为 `0` / `0`；
  任何断言「按主动键后 `sign_ready_type > 0`」「等待窗口 30 秒内不进入冷却」的旧用例**必然失效**，
  必须改写为选择器语义（进会话 → 选目标 → 确认；取消/超时不计冷却）。
  `/astralparty dump` 仍会回显这两行（占位 0）以及 `is_sign_active_locked`，便于对照取证。

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

### KI-M4 ＝ 待用户裁决 / 待办的两项开放项（2026-09-17 登记）

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
   这两个附件键是旧「待命等待器」的载体（见 KI-M1），等待器已在合并中由目标选择器取代、**无任何写入方**，
   但**三线仍在引用**：实测 `git grep -E 'SIGN_READY_TYPE|SIGN_READY_EXPIRE' -- '*.java'` 命中 **22 处**，
   全部落在三条线各自的 `component/ModAttachments`（键定义 + `get/set` 包装器；`1.20.1` 8 处 /
   `1.21.1` 8 处 / `26.1.2` 6 处，其中 1.20.1 还有 `SYNCED_KEYS` 登记，属同步协议面）。
   **候选**：(a) 三线同步删除（注意 1.20.1 的同步协议面与老存档里的废弃键残留）；
   (b) 保留为「废弃但可用」的占位键（**现状**），仅在注释里标注废弃；
   (c) 其它（如仅在下一大版本随存档迁移一起清）。**裁决前维持现状（b）。**

### KI-M5 ＝ 26.1.2 接入 starengine_lib 后的两项开放项（2026-09-17 登记）

1. **三个整合包目录目前都没有 `starengine_lib-*.jar` ⇒ 迁移后把 26.1.2 产物推入整合包会被 FML 拒绝启动。**
   26.1.2 自 2026-09-17 起接入前置库，其 `neoforge.mods.toml` 已声明 `starengine_lib` 为 `required`
   （`versionRange="[1.0.0-SNAPSHOT.5,2.0)"`）；实测 `D:/.minecraft/versions/26.1.2 模组测试/mods`、
   `狐の航空学 Voxy Edition/mods`（1.21.1）、`1.20.1 模组测试/mods` **三个目录都没有库 jar**，
   而 1.21.1 / 1.20.1 两条*已接入库*的线的整合包同样缺库 jar（属既有缺口，非本次迁移引入）。
   **待裁决**：(a) 推整合包时成对放入 `starengine_lib-<平台>-1.0.0-SNAPSHOT.5.jar`；
   (b) 暂不推该线产物（现状）。推送守卫未改动：`multi-dev-next` 上 `pushToGame` 仍为 skipped，
   仅 `-PdeployToPack` 可强推；`pushToGame` 已按另两线同形补**纯防御**的「库 jar 成对自检」（只告警，不阻断）。
   **限域（2026-09-17 用户裁决补记）**：本风险**在 `multi-dev-next` 上不会触发** —— 用户裁决「该分支内所有内容均不推送整合包（严格），只推送到游戏测试目录。除非用户另行规定。」该分支上 `pushToGame` 在**执行期**即因分支不在白名单而 `skipped`（实测三线均为 skipped；本次未跑 Gradle，证据引自 t12 报告 §5 与 `temp/merge-t2/t12-verify-build-*.log`），整合包目录不会被写入、旧 jar 也不会被删。
   只有当**发布线 `multi-1.20.1-1.21.1`**（或用户显式 `-PdeployToPack` 强推）真正推送整合包时，才必须把**库 jar 与本模组 jar 成对推送** —— 即 `starengine_lib-<平台>-1.0.0-SNAPSHOT.5.jar` 与 `astral_dice-*.jar` 同批放入同一个 `mods` 目录，否则 FML 在依赖排序阶段以「缺必需前置」拒绝启动。2. **【2026-09-19 已关闭】`effect/ReadyEffect` 是本线唯一保留的本地重复类（库内已按 KI-M3 删除）** —— 波次 2b（立牌主动前置门控收口移植）已把该线的立牌主动收口到发布线形态，本副本与三处注册**已实际删除**（见下方保留理由段的关闭说明）。
   保留理由：本线的旧「待命等待器」仍在用（`ModEffects` 的三处注册 `haiqing_ready`/`bonnie_ready`/`moses_ready`），
   而库 `.5` 已删除该符号 —— 若改为库引用会引用库中不存在的类，若删本地副本则三处注册编译失败。
   **禁止**为它复活库中已删符号、**禁止** bump 库版本、**禁止**改 CI 的库检出 ref（均属用户已明确暂缓事项）。
   **已于 2026-09-19 执行（本条关闭）**：26.1.2 的立牌主动已随波次 2b 迁到目标选择器（`BaseSignItem.handleUse` 由 `abstract` 改为发布线同款「默认实现 + WARN」、删 `isSkillWaiting`/`tickSignReadyTimeout`），`effect/ReadyEffect.java` 与 `ModEffects` 的 `haiqing_ready`/`bonnie_ready`/`moses_ready` 三处注册随另两线一并删除（该线效果注册数 **33→32**，与两个发布线 id 集合逐项一致），并一并删掉该线多出的 3 个 `effect.astral_dice.*_ready` lang 键与 3 张 `mob_effect/*_ready.png`（lang 686→683、贴图 35→32）。上面那四条「禁止」中只有后三条仍适用（属库侧政策：不为库复活已删符号、不 bump 库版本、不改 CI 检出 ref）；「禁止删本地副本」一条随本副本删除而失效。
**⚠️ 2026-09-24 更新：本条①（「库 jar 与 mod jar 必须成对推送」）的前提已消失，该规则作废。**
消费方已把前置库改为 **JarJar 内嵌**（`48976cb1`，三线 `build.gradle`；`pushToGame` 末尾留「整合包内不应
存在独立库 jar」的自检告警），且**库侧 `pushToPack` 已整体移除**（库仓同批提交）⇒
① **整合包 `mods` 里只应放 `astral_dice-*.jar`**，库由它内嵌携带，不再需要「成对推送」；
② 方向反过来才是风险：整合包里若**残留**独立库 jar，会被 FML 的 JarInJar 选择器按 `modId` 优先采用并
盖掉内嵌副本（独立件更旧时 ⇒ `NoSuchMethodError` / `NoClassDefFoundError`）⇒ **残留件应删除**。
实测 2026-09-24：`1.20.1 模组测试/mods` 与 `26.1.2 模组测试/mods` **仍各有一个**
`starengine_lib-*-1.0.3.jar`（1.21.1 的 `狐の航空学 Voxy Edition/mods` 已无），属待清理残留（未代删）。

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

## 9. F 组 — Fabric 1.20.1 移植线（2026-09-29 起）

> 本条线（子项目 `fabric-1.20.1` / 分支 `1.20.1-fabric`）与三条生产线**不共用存档、不共用前置**，
> 且单独以 **pre-release** 发布。平台差异的完整清单见 `porting/fabric-1.20.1/VERSION_PINS.md`「裁剪」，
> 规则边界见 `AGENTS.md`「多版本子项目矩阵」的第四条线说明。
> ⚠️ 下列 KI-F* **只适用于 fabric 线**；不要把它们当成三条生产线的缺陷，反向亦然。

### KI-F1 ＝ 两个「指定药水」配方（**已按 `astral_dice:nbt_shaped` 严格保真；原「放宽为任意药水」方案作废**）

- **背景**：`adrenaline_low_chip`（肾上腺素·低）与 `friendship_badge_chip`（心意相连徽章）两个配方，
  在 Forge 版要求**指定药水**（再生 / 治疗）。2026-09-29 移植初期因「1.20.1 原版没有 `IngredientType`」
  而一度放宽为「任意药水」（`"Z": {"item": "minecraft:potion"}`）—— 该放宽**已作废**，同日晚改为严格保真。
- **原版为什么做不到（实证，非推测）**：原版 `Ingredient` 是 `final class`、构造器 `private`、
  内部 `Value` 接口**包私有** —— 三个方向都封死（javap 实证）；且其 `test(ItemStack)` 的字节码
  **只调 `ItemStack.is(Item)`**，连 `getItems()` 里的 NBT 都不比较。
  ⇒ 无法在原版 `Ingredient` 层表达「指定药水」（Forge 是靠 patch 原版类塞进 `forge:nbt` /
  `forge:partial_nbt` 分支，Fabric 侧没有等价补丁）。1.20.1 亦**没有** `IngredientType`
  （`unzip -l` 原版 jar 只有 `Ingredient$Value/$ItemValue/$TagValue`）。
- **本线的实现（不依赖任何第三方模组）**：
  1. 自建序列化器 `astral_dice:nbt_shaped`（`crafting/AstralRecipeSerializers`）—— JSON 与网络
     **都委托原版 `ShapedRecipe.Serializer`**（`fromJson` / `toNetwork` / `fromNetwork`），
     只额外读写一个 `astral_nbt` 字段 ⇒ 不随版本补丁产生格式漂移；
  2. `crafting/NbtShapedRecipe extends ShapedRecipe`，覆写 `matches(CraftingContainer, Level)`：
     先 `super.matches` 过形状与种类级，再按**原版同一套 offset / 镜像枚举**复算一遍并附加 NBT 校验；
  3. `crafting/StackConstraint` 承载单格约束：严格模式 = `ItemStack.isSameItemSameTags`
     （等价于 NeoForge `DataComponentIngredient.of(true, …)` 内部的 `isSameItemSameComponents`）；
     子集模式 = `NbtUtils.compareNbt(expected, actual, true)`（对齐 Forge 的 `PartialNBTIngredient`，
     **当前无配方使用**，保留以备回归对照）。
- **与另两条线的语义对齐（2026-09-29 以 1.21.1 为基准校正）**：

  | 线 | 肾上腺素·低 的实现 | 状态 |
  |---|---|---|
  | `neoforge-1.21.1` | `DataComponentIngredient.of(true, …)`（strict） | **基准** |
  | `neoforge-26.1.2` | `DataComponentIngredient.of(true, …)`（strict） | 与基准一致 |
  | `fabric-1.20.1` | 原「放宽为任意药水」 | **已改为 `astral_nbt` + `strict=true`**（本条目落地） |
  | `forge-1.20.1` | `PartialNBTIngredient`（NBT **子集**） | ⚠️ **保持原样** |

  ⚠️ **这是一条有意保留的三线差异**（2026-09-29 用户裁决：**本分支只修改 fabric 端**）：
  `forge-1.20.1` 的「肾上腺素·低」仍是 NBT **子集**语义（带额外 NBT 的再生药水也能用），
  与另三线不同；该线的 `potionTag(...)` 辅助方法**保留**。**不是遗漏**，勿在 fabric 分支顺手改它。
- **取证**：原版类清单与 `test` 字节码 = `javap -p -c <fabric-loom 的 minecraft-merged jar> net.minecraft.world.item.crafting.Ingredient`；
  生成结果 = `fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/adrenaline_low_chip.json`
  与 `friendship_badge_chip.json` 的 `astral_nbt` 字段；加载期取证 = 起一次 fabric 服务端，
  日志内 `Parsing error loading recipe` 与「未知 recipe serializer」均为 **0 次**（实测）。
- **残留（已知并登记）**：客户端配方查看器（JEI 等）显示的仍是基础 ingredient（`minecraft:potion`），
  不体现 NBT 条件 —— 这是原版 `Ingredient` 不可扩展导致的**显示层**措辞差异；服务端匹配与
  客户端配方书 ghost 预览**都按约束执行**（约束随 `toNetwork` 同步到客户端），非功能缺口。

### KI-F2 ＝ 跨加载器存档不互通（**平台差异，非缺陷，不修补**）

- 本线的持久化由 **Fabric API 附件**（`fabric-data-attachment-api-v1`，随 fabric-api 分发）承载；
  Forge / NeoForge 侧为 Capability + `ForgeData` 段 / 数据组件。落盘位置与结构不同
  ⇒ **存档不能跨加载器迁移**。
- 饰品数据同理：本线用 **Trinkets 或 Accessories（二选一，见 KI-F15）**，另三线用 **Curios**。
- 写安装说明与迁移指引时必须如实说明「换加载器 = 换存档」。

### KI-F3 ＝ 两条饰品通道共存时的槽位聚合语义（**已设计处理，登记以免日后误改**）

- 本线的 `data/curios/tags/items/{dice,stand,chip}.json` **不是** Curios 的运行时数据
  （本线没有 Curios）——它是「物品清单的单一事实源」，Trinkets 侧用 `#curios:<slot>` 引用它；
  Accessories 侧走自定义 predicate `astral_dice:curio_slot`。**不要因为命名空间写着 `curios` 就删除它**。
  ⚠️ 它是普通物品标签（由原版标签加载器读，不依赖任何饰品模组）⇒ 两个饰品栏模组**都没装**时它依然生效。
- ⚠️ **前置是「二选一」不是「都要」**（2026-09-29 用户裁决，见 **KI-F15**）：`trinkets` 与 `accessories`
  都只进 `fabric.mod.json` 的 `recommends`，两个都没有时才由运行时守卫拒绝启动。
- Trinkets 与 Accessories **同装**时，`compat/curios/CuriosApi` 作为多源聚合门面：
  `findCurios` 取并集（按 `ItemStack` 实例去重，防「官方兼容层把两源指向同一库存」时重复）、
  `getStacksHandler` 同名冲突时返回 `MergedHandler`（读优先非空侧、写落持有侧）、
  槽位修饰符**两侧同写**（保证「筹码栏位数 = 骰子星级」在两套系统里一致）。
  ⚠️ 改动该门面时必须同步验证**四种组合**：仅 Trinkets / 仅 Accessories / 两者同装 / 两者皆无
  （最后一态验证的是「拒绝启动」这条路径本身，dev 用 `-PtestTrinkets=false -PtestAccessories=false` 构造）。

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

### KI-F5 ＝ `c:bricks` 标签由本线自建（**补齐无提供者**）

- **现象**：`effect_card_monster_brick`（对怪板砖）配方需要 `#c:bricks`。该标签在
  **Forge 47.x 上没有任何提供者**（`unzip -l forge-*-universal.jar` 只有 `data/forge/**`，
  `Tags.Items` 里也只有 `INGOTS_BRICK` / `FENCES_NETHER_BRICK`，**无 `BRICKS`**）
  ⇒ 原版/Forge 会创建**空标签**，配方**永不可合成**（静默失效，不报错）。
- **处置（2026-09-29）**：在**本线（fabric）**自建该标签，内容**对齐 NeoForge 的定义**：
  ```
  c:bricks          = [ #c:bricks/normal, #c:bricks/nether ]
  c:bricks/normal   = [ minecraft:brick ]
  c:bricks/nether   = [ minecraft:nether_brick ]
  ```
  写入 `fabric-1.20.1/src/main/resources/data/c/tags/items/{bricks,bricks/normal,bricks/nether}.json`
  （⚠️ 1.20.1 的目录是 **`tags/items`（复数）**；1.21+ 才是 `tags/item`）。
- ⚠️ **`forge-1.20.1` 侧未动**（2026-09-29 用户裁决：**本分支只修改 fabric 端**）⇒ 该线的
  `#c:bricks` **仍然没有提供者**，「对怪板砖」配方在那条线上依旧不可合成。
  这是一条**已知且已登记的既有缺口**（真实存在、不报错），留待该线自己的批次处理，别在 fabric 分支顺手补。
- **依据（逐字核对，非推测）**：`neoforge-21.1.235-universal.jar` 的 `data/c/tags/item/bricks.json`
  = `["#c:bricks/normal", "#c:bricks/nether"]`，`bricks/normal.json` = `["minecraft:brick"]`，
  `bricks/nether.json` = `["minecraft:nether_brick"]`；`neoforge-26.1.2.109` 的 `bricks.json`
  多一项 `#c:bricks/resin`（= `minecraft:resin_brick`），但**树脂砖是 1.21.5+ 的物品，1.20.1 不存在**
  ⇒ 本线按 1.21.1 的形态对齐（两个子标签）。
- **兼容性**：标签默认**合并**（不写 `replace: true`），若玩家整合包另有模组提供 `c:bricks`，
  两方内容取并集，不会互相覆盖。
- **注意**：`c:bricks` 的内容是**砖物品**（`minecraft:brick` / `minecraft:nether_brick`），
  **不是**砖块方块（`minecraft:bricks`）—— 名字相近，改动该标签前务必对照 NeoForge 的原定义。
- 两条 NeoForge 线**无需自建**（由 NeoForge 自身提供）；它们各自的 `.minecraft` 整合包内
  该标签由 NeoForge 注入，不存在此问题。

### KI-F6 ＝ 自建总线的「登记完整性」靠注解扫描兜底（**工具化，含局限**）

- **风险源**：本线的 `platform/event/LoaderBus` 是**反射式自建总线**，订阅类必须被
  `LoaderBus.INSTANCE.register(X.class)` 显式登记（Fabric 侧没有 `@EventBusSubscriber` 的自动注册）。
  漏登记 ⇒ 处理器**永不触发**，且不报错（2026-09-29 真实事故：`onCommonSetup` 因漏登记，
  连带兼容性校验 / 网络注册 / 卡牌注册表三者从未执行）。
- **两道护栏**：
  1. `LoaderBus#dispatchReport()` —— 列出每个**已注册**事件类的派发次数，**0 次的也列**
     （抓「桥没接 / 注入点写错」）；判据：`ServerTickEvent` 必须为正数。
  2. `platform/event/SubscriptionAudit` —— 扫描本模组类根下所有带 `@SubscribeEvent` 的类，
     与**已登记集合**做差集，列出「带注解却从未登记」的类（抓 `dispatchReport()` 看不见的盲区）。
     - 扫描方式：`FabricLoader#getModContainer(...).getRootPaths()` ⇒ dev 走目录 `Files.walk`、
       生产走 jar 条目；类用 `Class.forName(name, false, loader)` **不初始化**加载。
     - 默认 **WARN** + 列清单；加 `-Dastral_dice.strictBusAudit=true` 升级为**抛异常**（供测试台断言）。
     - 入口：服务端 `AstralDiceMod#onInitialize` 末尾（排除客户端专属包 —— 此时客户端尚未登记），
       客户端 `AstralDiceClient#onInitializeClient` 末尾（不排除，最完整）。
     - ⚠️ **局限**：若一个类都没扫到，只打「审计未生效」WARN（**不得**当成通过）；
       且客户端专属包在**服务端**那次审计里被有意跳过（属预期，非漏检）。
- **与另三条线的差异**：它们用 `@EventBusSubscriber`（编译期自动注册），不存在「漏登记」这一类风险
  —— 但存在**对称的另一类**风险（注解参数写错、或注解类里无 `@SubscribeEvent` 方法，NeoForge 会直接抛）。
  两条线的护栏**不可互相替代**，故本线保留自有审计而不去复用生产线的检查。

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
- **影响**：玩家在手册里读到的品质档位与物品实际稀有度（tooltip 边框 / 文字色，见 KI-F1 的 `RarityTooltipFrame`）
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

### KI-F9 ＝ 客户端订阅**内部类**漏登记 ⇒ **J / H 两个按键完全不响应**（已修，仅 fabric）

- **现象**：`KeyBindingSetup.ClientEvents.onClientTick` 负责 **J**（立牌主动技能激活 / 取消目标选择）
  与 **H**（打开卡牌栏），但该**内部类从未被 `LoaderBus.register(...)` 登记** ⇒ 两个键完全无响应。
- **根因（移植引入）**：另三线（forge / 1.21.1 / 26.1.2）都把 `@EventBusSubscriber` 标在
  `KeyBindingSetup.ClientEvents` 这个**内部类**上、由平台自动注册；fabric 侧必须显式登记，
  而移植时只登记了**外层类** `KeyBindingSetup`（它本身没有任何处理器）。
  ⚠️ `LoaderBus.scan` 只遍历「该类自己声明的方法 + 父类」，**不递归内部类**。
- **修复**：`AstralDiceClient#onInitializeClient` 增加
  `LoaderBus.INSTANCE.register(KeyBindingSetup.ClientEvents.class)`（外层那行保留，无害）。
- **取证**：修复前客户端日志 `事件订阅审计:发现 1 个带 @SubscribeEvent 却**未登记**的类 → [KeyBindingSetup$ClientEvents]`；
  修复后 `事件订阅审计通过:61 个订阅类全部已登记`。
  ⚠️ **这正是 KI-F6 那套审计存在的意义** —— `dispatchReport()` 只看得见「已注册」的事件类，对这类漏接是盲区。

### KI-F10 ＝ `MobEffectEvent.Remove` 构造器对 null 效果实例 NPE ⇒ **玩家无法进入存档**（已修）

- **现象**：客户端进入世界瞬间 `PlayerXXX lost connection: 无效的玩家数据`，玩家被踢回主菜单、
  **存档无法进入**；服务端日志报 `Couldn't place player in world`。
- **根因链**：玩家登录 → `PlayerLoggedInEvent` → `PlayerLifecycleHandler#onPlayerLoggedInClearDiceBlessing`
  清理「骰神赐福」→ 库的 `ModEffectRemoval.remove` 直接走 `LivingEntity#removeEffect`
  （**不先判该效果是否存在**）→ Puzzles Lib 的 `MobEffectEvents.REMOVE` 回调以 **null** 的
  `effectInstance` 触发 → `PuzzlesBridges` 原样构造 `new MobEffectEvent.Remove(entity, null)`
  → 构造器 `this.effect = effectInstance.getEffect()` **解引用 null** ⇒ NPE。
- **⚠️ 自相矛盾的证据（说明这是纯粹的漏防御）**：同类的 `getEffectInstance()` javadoc **早已声明**
  「In the remove event, this can be **null** if the entity does not have a MobEffect of the right type active」，
  且**全部 4 处订阅者**都写了 `if (instance == null || instance.getEffect() == null) return;` ——
  **只有构造器漏了判空**。
- **对照 Forge（语义基准）**：Forge 的 `removeEffect` 补丁形如
  `MobEffectInstance i = activeEffects.remove(effect); if (i != null) post(new Remove(this, i, effect));`
  ⇒ **效果不存在时不派发事件**。
- **修复（主 + 防两处）**：
  1. `PuzzlesBridges` 的 REMOVE 回调：`effectInstance == null` 时**直接 return PASS**（不派发）—— 对齐 Forge 语义；
  2. `MobEffectEvent.Remove(living, instance)` 构造器：`effectInstance == null ? null : effectInstance.getEffect()`
     —— 第二道防线（与自身 javadoc 一致，任何未来的调用方传 null 也不会崩）。
- **取证**：修复前 `Couldn't place player in world` + 完整 NPE 堆栈；修复后同一存档同一路径
  **无异常、玩家正常留在世界**。

### KI-F11 ＝ `ScreenEvent.Opening` 在 `setScreen(null)` 时 NPE ⇒ **每次进世界客户端必崩**（已修）

- **现象**：`java.lang.NullPointerException: Ticking screen`，堆栈
  `Objects.requireNonNull → ScreenEvent.<init> → ScreenEvent$Opening.<init> → Minecraft.setScreen 的 mixin
   → ReceivingLevelScreen.onClose()` ⇒ 客户端直接崩溃退出。
- **根因**：MC 内部**合法地**用 `setScreen(null)` 关闭当前屏幕（最典型的是「正在加载地形」的
  `ReceivingLevelScreen` 加载完成后 `onClose()`），而 `ClientScreenBridgeMixin` 在 `setScreen` 的 HEAD
  **无条件**构造 `ScreenEvent.Opening(current, newScreen)`；`ScreenEvent` 基类构造器是
  `Objects.requireNonNull(screen)` ⇒ NPE。
- **对照 Forge（语义基准）**：Forge 的 `ScreenEvent` 构造器**同样**是 `Objects.requireNonNull(screen)`
  （javap 实证），而 Forge 客户端能正常进世界 ⇒ **Forge 必然在 screen 为 null 时不构造事件**。
- **修复**：`ClientScreenBridgeMixin#astralDice$bridgeScreenOpening` 开头加 `if (newScreen == null) return;`。
- **顺带排查**：全仓平台事件类只剩 1 处 `Objects.requireNonNull`（`ProjectileImpactEvent` 的 **setter**，
  不是构造器）⇒ 该类风险已闭合。
- **取证**：修复前每次进世界必崩；修复后 `PlayerXXX logged in`、客户端连续运行 600+ tick 并正常渲染。

> ⚠️ **这三条都只在「真带玩家进世界」的客户端验证里才会暴露**：
> 服务端空跑（无玩家登录）与客户端主菜单阶段**都测不出来** ——
> KI-F9 要客户端入口、KI-F10 要玩家登录、KI-F11 要真正走完「加载地形」并关闭该屏幕。
> ⇒ **移植线收尾必须做一次「客户端进世界」验证，不能只跑服务端 + 编译。**

### KI-F12 ＝ fabric 测试台自身的 7 个缺陷（**已修；其中 2 个是阻断级**）—— 「测试流程完全不可用」的根因

- **背景**：`fabric-1.20.1` 是「自建事件总线 + 四路桥接」的移植线，测试台只能另起一套
  （`scripts/test/fabric/`，见该目录 `README.md`）。此前这套台子**只做过静态校验与部分读数**，
  从未真机跑过 `env → launch → inject` 这条主干 ⇒ 下面 7 个缺陷一直没暴露（**D6/D7 是后来真跑冒烟时才暴露的**，见本条末的冒烟记录）。
- **本轮真机实跑**（服务端 + 客户端各起一次、RCON 注入、收停、5 条用例）后修复；D6/D7 由随后的**完整冒烟**（批次 A/B/C，用户显式要求）暴露并修好：

  | 编号 | 缺陷 | 影响 | 根因（实测，非推测） |
  |---|---|---|---|
  | D1 | 就绪游标被 log4j「启动轮转」击穿 | **每次启动必然假超时**（`rc=12`），其后一切读数/注入/用例统统不可用 | latest.log **每一次冷启动**都被改名成 `YYYY-MM-DD-N.log.gz` 并**重建**；旧实现拿「启动前的字节长度」当游标 ⇒ 判定条件 `len > preLen` 永不成立（实测：服务端 **9 秒**就打出 `Done (0.333s)!`，而 `ft_launch` 干等 240 秒后报 TIMEOUT，进程还成了 orphan）。**次生伤害**：新文件长过旧偏移之后，窗口偏移落进**新文件中间** ⇒ `case`/`launch` 窗口**静默**返回错误内容，是「假 PASS / 假 FAIL」的来源 |
  | D2 | 游戏运行时 `latest.log` 被独占 | **「服务端活着时读日志」全线不可用**（`ft_assert` / `ft_dispatchreport` / 启动轮询 / 回显校验） | `[System.IO.File]::ReadAllBytes` 的共享模式是 `FileShare.Read`，而 Windows 的共享检查是**对称**的 ⇒ 无法与仍持有**写**句柄的游戏进程共存（实测抛 `The process cannot access the file … being used by another process`）。此前因 D1 使读分支**从未真正执行**而被掩盖 |
  | D3 | KubeJS 命令队列通道**从根上不可实现** | 观察者**从未成功执行过一条命令**（每 20 tick 抛 `TypeError: Cannot call method "get" of null`，`FT_Paths` 恒为 null） | KubeJS 6+ 三重封死：① 类过滤不放行 `java.nio.file.*` / `java.io.*`（`Java.loadClass('java.nio.file.Paths')` 返回 null，无 `Loaded Java class '…'` 行）；② 面向脚本的 bindings **无任何文件/路径包装器**（实测 `bindings/` 只有 8 个 Wrapper）；③ `java` / `Packages` 全局已移除（实测 `'java()' is no longer supported!`）⇒ 无法构造 `Path` |
  | D4 | `ft_launch` 无法向 gradlew 透参 | 客户端在本机 dev 环境**必然**启动失败，无绕过手段 | Sodium 要求 LWJGL **3.3.1**、而 dev 环境装的是 **3.3.2-snapshot** ⇒ 启动期硬失败；旧实现只能起裸 `gradlew :fabric-1.20.1:runClient`，带不上工程既有的 `-PtestSodium=false` |

  | D5 | 用例里的**固定 `wait`** 把「等待条件未满足」伪装成「断言失败」 | 采样点未到时报 6 条断言失败，**现场看起来像产品缺陷**（桥没派发？内嵌库坏了？），把排查方向带偏 | 本线有读数只在**开局 600 tick（约 30 s）**才打印；旧用例靠固定 `wait 25000` + 注释提醒纪律 ⇒ 就绪后 25 s 跑用例实测：`FAB-BOOT-EMBED` **1/10**、`FAB-DISPATCH-BASIC` **6/8**，报「未找到派发报告」。**修法两层**：① 新增 `wait_for` 步骤（有界轮询，命中即继续；超时报 **TIMEOUT(12)** 并给手动读数指引 —— 与「断言失败 = 1」明确区分，语义边界同 TESTING-RULES §7）；② 把 `事件派发统计` 断言从 `FAB-BOOT-EMBED` **移出**（10 → 9 条），消除隐藏的 30 s 时间耦合 |

  | D6 | `Invoke-FtChildProcess` 的 **`-Wait` 会等整棵进程树** | **`ft.ps1 --phase launch` 挂到游戏退出为止**（把游戏拉起来这条唯一编排入口从设计上就不可能返回） | `ft_launch.ps1` 是「孵化游戏后立刻返回」的语义（游戏进程 detached），而 `Invoke-FtChildProcess` 用 `Start-Process -Wait` 等它 ⇒ 一并等到了 detached 的游戏进程。**冒烟实测**：批处理驱动卡在 launch 步 **6 分钟**不返回，而子脚本输出文件里早已有 `AP_FAB_LAUNCH: OK`（就绪 11 s 就捕获了）、服务端日志里连 600-tick 派发报告都打出来了。⚠️ 这个坑**项目自己的文档里就写着**（`TESTING-RULES-OVERVIEW.md` §13「`-Wait` 会等整棵进程树」），但该封装仍然用了它。修法 `-PassThru` + 只对该进程本身 `WaitForExit()`；验证 `ft.ps1 --phase launch` **11.4 s** 返回 rc=0。影响面精确为这一处（`ft_build` 用按进程的 `WaitForExit(timeout)`、`ft_stop` 的 `taskkill /T` 是**故意**杀树，都正确） |

  | D7 | 子进程输出回读撞上**同一个共享模式陷阱**（D2 的镜像） | 修好 D6 后 `ft.ps1 --phase launch` 立刻以 rc=1 失败：`ReadAllBytes … The process cannot access the file …ft_child_*.out.txt … used by another process` | `Invoke-FtChildProcess` 用 `[System.IO.File]::ReadAllBytes` 回读子进程的重定向输出，而该文件在子进程退出后仍被**本方（父进程）的写句柄**持有一小段时间 ⇒ 与 D2 同一个根因（`ReadAllBytes` 共享模式 = `FileShare.Read`）。`-Wait` 顺带等整棵树，恰好把这个竞态**掩盖**了。修法：① `WaitForExit()` 后先 `$proc.Dispose()`；② 回读改走 `Read-FtFileBytesShared`（与日志读取**同一条**共享读写实现）。教训 = **同一类问题要收敛到同一套原语**（D2 修好后没把「读文件」收敛成一个入口，才有了 D7） |

- **修法**：
  - D1/D2 —— 把「字节位置」换成「**文件身份锚点**」＝ 创建时间(UTC ticks) + 头部 4 KiB 的 SHA1 + 长度
    （`Ft.Common.psm1` 新增 `Get-FtLogAnchor` / `Test-FtAnchorReplaced` / `Get-FtLogWindowFromAnchor` /
    `Read-FtFileBytesShared`）：身份变了 ⇒ 整个新文件就是「本次新增」；身份没变才做前向字节切分。
    另加**反向自检**：超时时若整份日志里**已有**就绪标记，报 `reason=ready-but-undetected`
    （与普通 timeout 明确分开）—— 把「工具缺陷」与「游戏没起来」彻底剥离，不制造第二个假绿。
  - D3 —— **整体撤除**该通道与观察脚本 `ft_cmd_watcher.js`；`--channel kubejs` 保留为**显式失败**
    （`reason=unsupported-by-platform` + 完整实测理由）。理由：一条「看起来能用、实际永远失败」的通道
    比没有更危险。KubeJS 在本台的定位收敛为**「产出读数的探针」**（`ft_env --install-probe` 装
    `event_bridge_probe.js` 一类），**命令注入统一走 RCON**（vanilla 原生、同步返回回显）。
  - D4 —— 新增可重复的 `--gradle-arg <x>` 透传（同时写进状态文件与 `AP_FAB_LAUNCH_TASK` 读数行）。
- **验收读数（全部实跑）**：`ft_launch --side server` rc=0（`cursor=22827`）、
  `--side client --gradle-arg -PtestSodium=false` rc=0（`Sound engine started`、`cursor=23798`）；
  服务端存活期间 `ft_assert` / `ft_dispatchreport` 正常出数；RCON 注入 rc=0 且 `/say` 回声落日志
  （`[Not Secure] [Rcon] <tag>`）；`ft_stop` 对真在跑的实例 rc=0 且不残留；5 条用例全绿 ——
  `FAB-BOOT-EMBED` 10/10、`FAB-DISPATCH-BASIC` 8/8、`FAB-JAR-ASSETS` 1/1、
  **`FAB-CLIENT-BOOT` 8/8（本轮新增）**、`FAB-INJECT-ROUNDTRIP` 3/3（本轮改为 rcon 通道）；
  闸门与显式失败路径退出码逐条正确（`MT_AUTO_DISABLED`=2 / 断言不满足=1 / PASS=0）。
- **冒烟（2026-09-29 18:40–18:44，用户显式要求，全清单）**：分三批执行，**全绿** ——
  批 A（服务端）：构建+开包核对 rc=0（1.7s）、env rc=0、**经编排入口 `ft.ps1 --phase launch` 起服务端 rc=0（11.5s，D6 回归）**、
  在线断言 rc=0（0.6s，D2 回归）、`FAB-JAR-ASSETS` 1/1、`FAB-BOOT-EMBED` 9/9、`FAB-DISPATCH-BASIC` 8/8（27.8s，其中 ~27s 是 wait_for 等采样点）、
  `FAB-INJECT-ROUNDTRIP` 3/3、在线派发统计 rc=0（`ServerTickEvent=599 fired=10 idle=26`）、收停 rc=0；
  批 B（客户端）：经编排入口带 `-PtestSodium=false` 起客户端 rc=0（12.4s）、`FAB-CLIENT-BOOT` 8/8、收停 rc=0；
  批 C（静态闸门）：lang 三语 829×3 一致 rc=0、工具链语法门 53 文件 0 失败 rc=0、模组来源 `violations=0` rc=0、
  本线资源/注册闭环 7 项 PASS rc=0。**合计 5/5 用例 + 4/4 静态闸门 + 无残留进程。**
  未纳入：`tooltip_color_audit` / `verify_chip_*` / `verify_bountiful_*` / `verify_forge_loader_gate`（作用域只覆盖两条生产线，与本线无关）。
  逐项读数与耗时见 README §7.2.1；驱动脚本 `temp/smoke.ps1` / `temp/smoke_c.ps1`（temp 被 .gitignore 忽略）。
- **取证与全文**：`scripts/test/fabric/README.md` —— §7.2（实跑清单 34 项，含退出码）、
  §7.3（4 个缺陷的根因与现场证据：轮转时间戳、异常原文、`javap`/`unzip` 读数）、
  §7.4（仍未实跑项）、§9（依据索引，含「latest.log 每次启动被轮转」「运行时被独占」
  「KubeJS 类过滤与全局移除」三条平台事实）。
- **等级说明**：D1–D7 属**测试台缺陷**，不是产品缺陷；但它们会让「测试台报绿」这件事本身失去意义
  （D1 让启动阶段永远失败、D2 让在线读数整体不可用），故按缺陷等级登记。
- **遗留**：客户端 GUI 键鼠注入仍未覆盖（只有服务端命令通道）；客户端**世界内**用例
  （渲染 / HUD / tooltip）与 `ft.ps1 --phase report` 的放行后路径仍未实跑，均已在 README §7.4 / §8 登记。

### KI-F13 ＝ 整合包启动崩溃：库里**按字符串名反射原版字段**（dev=named / 生产=intermediary 名字不同）—— **生产环境 100% 起不来，但 dev 与所有现有测试都看不见**（**已修：库 1.0.5-alpha.1；生产冒烟 PASS**）

- **现象**：把 `fabric-1.20.1` 的产物 jar 推进整合包（`D:\.minecraft\versions\1.20.1-Fabric 模组测试`）后，
  客户端**启动期直接崩溃**（`crash-2026-09-29_18.53.24-client.txt`）：
  ```
  java.lang.RuntimeException: Could not execute entrypoint stage 'main' due to errors,
      provided by 'astral_dice' at 'com.merlinkitsune.astral_dice.AstralDiceMod'!
  Caused by: java.lang.ExceptionInInitializerError
      at com.merlinkitsune.starenginelib.item.AstralRarities.<clinit>(AstralRarities.java:95)
      at com.merlinkitsune.astral_dice.item.ModItems.lambda$static$1(ModItems.java:153)
      …  → AstralDiceMod.onInitialize(AstralDiceMod.java:50)
  Caused by: java.lang.NoSuchFieldException: color
      at java.base/java.lang.Class.getDeclaredField(Class.java:2382)
      at com.merlinkitsune.starenginelib.item.AstralRarities.<clinit>(AstralRarities.java:90)
  ```
- **根因**：`starengine_lib_fabric/fabric-1.20.1/.../AstralRarities.java` 的静态块用**字符串字段名**
  反射原版 `Rarity`：
  ```java
  RARITY_COLOR_OFFSET = UNSAFE.objectFieldOffset(Rarity.class.getDeclaredField("color"));   // line 90
  Field values        = Rarity.class.getDeclaredField("$VALUES");                            // line 91
  ```
  而 **Fabric 的 dev 与生产用的是两套映射**：
  - **dev（Loom named/Mojang 映射）**：`Rarity` = `net.minecraft.world.item.Rarity`，
    字段真的是 `color` / `$VALUES` ⇒ **代码能跑**（所以历次 dev 冒烟、服务端/客户端验证全绿）；
  - **生产（intermediary + fabric-loader 运行期重映射）**：同一个类是 `net.minecraft.class_1814`，
    字段被重命名 ⇒ `getDeclaredField("color")` 抛 `NoSuchFieldException`。
- **证据链（全部实测，可复算）**：

  | 环节 | 读数 |
  |---|---|
  | 整合包里的 jar = 仓库产物？ | **sha1 完全相同** `241f9af97319d48306c76c3ba63124372b4b8c5e`（排除"陈旧产物"） |
  | dev（named）侧字段名 | `javap -p <loom 的 minecraft-merged jar> net.minecraft.world.item.Rarity` → `public final net.minecraft.ChatFormatting **color**;`、`private static final Rarity[] **$VALUES**;` |
  | 生产（intermediary）侧字段名 | `javap -p <minecraft-merged-**intermediary** jar> net.minecraft.class_1814` → `public final net.minecraft.class_124 **field_8908**;`、`private static final class_1814[] **field_8905**;` |
  | 两侧结构 | 完全同构（4 个静态常量 + `values()`/`valueOf()` + `private ctor(ChatFormatting)` + `static{}`），**只有字段名不同** |
  | 类定位方式 | 枚举常量的**字符串字面量不会被重映射** ⇒ 用 `class_124`（ChatFormatting，常量池里 `LIGHT_PURPLE` 等仍在）+ `java/lang/Enum` 双重特征反查出 `class_1814` |
- **⚠️ 有两个断点，本轮只崩了第一个**：`"color"`（line 90）先抛；紧随其后的 `"$VALUES"`（line 91）
  在生产环境**同样会抛**（`$VALUES` → `field_8905`）。修只修一个等于没修。
- **影响面**：**fabric 线的生产环境 100% 启动失败**（该路径 = 玩家装整合包/正常游戏）。
  且**所有现有验证手段都测不出来** —— dev 冒烟、服务端/客户端用例、5 条 case、静态闸门，**全部跑在 named 映射下**。
  这是本仓"测试与发布环境非同构"造成的一次**假绿**。
- **修法（库仓 `starengine_lib_fabric`，映射无关化：按**类型**找字段，不按名字）**：
  ```java
  // ① color：Rarity 里唯一的 ChatFormatting 字段
  Field colorField = null;
  for (Field f : Rarity.class.getDeclaredFields()) {
      if (f.getType() == ChatFormatting.class) { colorField = f; break; }
  }
  if (colorField == null) throw new IllegalStateException("Rarity 里找不到 ChatFormatting 字段");
  RARITY_COLOR_OFFSET = UNSAFE.objectFieldOffset(colorField);

  // ② $VALUES：Rarity[] 类型的静态字段
  Field values = null;
  for (Field f : Rarity.class.getDeclaredFields()) {
      if (Modifier.isStatic(f.getModifiers()) && f.getType().isArray()
              && f.getType().getComponentType() == Rarity.class) { values = f; break; }
  }
  if (values == null) throw new IllegalStateException("Rarity 里找不到 $VALUES 数组字段");
  ```
  其余三处反射是安全的：`Unsafe.class.getDeclaredField("theUnsafe")`、`Enum.class.getDeclaredField("name"/"ordinal")`
  **都是 JDK 成员，不参与 MC 重映射**。
- **全仓扫描结论**：`starengine_lib_fabric` 里只有 `AstralRarities:90/91` 两处属于该模式；
  `EventTargetCollector`（FTB Teams / OPAC 反射）与 `BossEntityUtil`（`getBossEvent`/`getBossBar` 约定名 + try/catch）
  反射的是**模组自有名字**，安全；`astral_dice` 侧 `WaystoneWarpCompat`（`common.MinecraftForge` / `EVENT_BUS`）
  与 `SubscriptionAudit`（自身类名）同样安全。另一个库仓 `starengine_lib`（非 fabric）源码里**没有**该模式。
- ~~**修好之后需要做的（版本契约，待裁决）**：库版本 bump（1.0.7 → ？）~~ ⇒ **已按用户裁决完成，见下方「修复实施与验证」**：
  消费方 `astral_dice/fabric-1.20.1` 的库依赖与 `lib_version_range` 同步 → 重新构建 →
  重新 `pushToGame` → 整合包再启动一次验证。
- **顺带发现的流程缺口（**已落地为工具**：`scripts/test/fabric/ft_prod.ps1`，见 README §7.2.2）**：本仓的"验证"与"发布"长期不在同一映射下 ——
  **dev 全绿 ≠ 产物可用**。建议补一道**生产映射守门**（二选一或都做）：
  ① **静态**：扫产物 jar 里 `getDeclaredField("…")` / `getField("…")` / `getMethod("…")` 的**字符串常量**，
     凡不在 intermediary 名集合里、且不是 JDK 成员的 ⇒ 报缺陷；
  ② **动态**：每次改动后除了 dev 冒烟，**把推给整合包的那份 jar 真启动一次**（生产映射下的最小启动用例）。
- **取证**：崩溃报告 `D:\.minecraft\versions\1.20.1-Fabric 模组测试\crash-reports\crash-2026-09-29_18.53.24-client.txt`；
  整合包日志 `…/logs/latest.log`（`astral_dice 1.3.2+fabric_1.20.1` / `\-- starengine_lib 1.0.7`，其余仅良性 WARN）。

#### KI-F13 修复实施与验证（2026-09-29 19:05–19:14，跨两仓）

**① 库侧修源码** —— `starengine_lib_fabric/fabric-1.20.1/src/main/java/com/merlinkitsune/starenginelib/item/AstralRarities.java`
改为**按类型 / 修饰符**定位，两套映射同时成立：
```java
private static Field lookupColorField(Class<?> rarityClass) {
    for (Field field : rarityClass.getDeclaredFields())
        if (field.getType() == ChatFormatting.class) return field;
    throw new IllegalStateException("vanilla Rarity has no ChatFormatting-typed field: " + rarityClass.getName());
}
private static Field lookupValuesField(Class<?> rarityClass) {
    for (Field field : rarityClass.getDeclaredFields())
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                && field.getType().isArray()
                && field.getType().getComponentType() == rarityClass) return field;
    throw new IllegalStateException("vanilla Rarity has no Rarity[] static field: " + rarityClass.getName());
}
```
static 块的 `catch (ReflectiveOperationException)` 一并放宽为 `catch (Throwable)` —— 新抛的是 `IllegalStateException`，
不属 `ReflectiveOperationException`，需同样包成 `ExceptionInInitializerError`（否则失败形态变成裸 RuntimeException）。
⚠️ **只改 fabric 子项目**：forge-1.20.1 / neoforge-1.21.1 / neoforge-26.1.2 生产用 **Mojang 官方映射**（`color` 就叫 `color`），
不受影响；本仓其它三个子项目同文件**未改动**（属**平台必需差异**，已在文件 javadoc 里写明理由）。

**② 版本号（按用户 2026-09-29 裁决：退回 1.0.5 基线 + `-alpha.x` 预发布后缀）**
| 位置 | 值 |
|---|---|
| 库 `fabric-1.20.1/gradle.properties` | `lib_version=1.0.5-alpha.1` / `mod_version=1.0.5-alpha.1+fabric_1.20.1` |
| 消费方 `fabric-1.20.1/gradle.properties` | `starengine_lib_version=1.0.5-alpha.1` / `starengine_lib_version_range=>=1.0.5-alpha.1 <2.0` |
| 消费方主模组 | `mod_version=1.3.2-alpha.1+fabric_1.20.1` |

预发布号在库仓 CI 里**不会**打 tag（workflow 只认裸 `x.y.z`）；Fabric 的版本比较按 semver ⇒ `1.0.5-alpha.1 < 1.0.5 < 1.0.6`
⇒ 该 range 下界同时收 alpha 与后续正式号。

**③ 发布与内嵌**：库 `:fabric-1.20.1:build publishToMavenLocal` → `~/.m2/.../1.0.5-alpha.1/` 出新件 →
消费方 `:fabric-1.20.1:build` → Loom `include` 内嵌。产物实证：
```
pushToGame: pushed astral_dice-1.3.2-alpha.1+fabric_1.20.1.jar -> …\1.20.1-Fabric 模组测试\mods
pushToGame: 内嵌前置自检 OK — META-INF/jars/starengine_lib-fabric-1.20.1-1.0.5-alpha.1.jar
pushToGame: 整合包内无独立库 jar(符合内嵌口径)
fabric.mod.json: version=1.3.2-alpha.1+fabric_1.20.1 / depends.starengine_lib=">=1.0.5-alpha.1 <2.0"
```
（三个分发任务均执行：`pushToRootBuild` / `pushToDevRun` / `pushToGame`；旧版 jar 按 `+fabric_1.20.1` 后缀先行清理。）

**④ 修复真的进了产物**（javap 反编译**内嵌件**，不是只看源码）：
```
59: ldc #139  // class net/minecraft/class_1814        ← 原版 Rarity 在产物里确实叫 class_1814
61: invokestatic lookupColorField                       ← 已改为按类型查找
（String color / String $VALUES 已从常量池消失）
（保留的 String theUnsafe / name / ordinal 指向 sun.misc.Unsafe 与 java.lang.Enum —— JDK 成员，不参与重映射）
```

**⑤ 生产映射冒烟**（本轮**新增的工具**，见 `scripts/test/fabric/ft_prod.ps1` 与 README §7.2.2）
| 项 | 修复前（19:07） | 修复后（19:11） |
|---|---|---|
| 判定 | `VERDICT = CRASH_REPORT` | **`AP_FAB_PROD_READY: elapsed=23s`** |
| 证据 | 新增 `crash-2026-09-29_19.07.04-client.txt`（`NoSuchFieldException: color`） | 日志出现 `Sound engine started`；`crash-reports/` **无新增** |
| 加载清单 | `astral_dice 1.3.2+fabric_1.20.1 \-- starengine_lib 1.0.7` | `astral_dice 1.3.2-alpha.1+fabric_1.20.1 \-- starengine_lib 1.0.5-alpha.1` |

**⑥ dev 侧回归**（确认修复未破坏 dev）：`ft_launch --side client -PtestSodium=false` →
`AP_FAB_LAUNCH_READY pattern=Sound engine started`，日志内 `starengine_lib 1.0.5-alpha.1`，无崩溃。
⇒ 同一份源码在 **named 与 intermediary 两套映射下都能定位到字段**（这正是"按类型找"的意义）。

- ⚠️ **仍未做（跨仓发布面，属策略，未自行决定）**：库提交尚未 `push` 到远端；消费方 CI 若钉了库 `ref` 需同步。
  本批只做**本地闭环**（源码 → mavenLocal → 构建 → 推整合包 → 生产冒烟 → dev 回归）。
- ⚠️ **mavenLocal 残留**：本轮先试过 `1.0.8`（随后按用户裁决改基线为 1.0.5-alpha.1）⇒
  `~/.m2/.../1.0.8/` 目录仍在。因 `starengine_lib_version` 已不再指向它，**不会被任何构建解析到**；
  若日后要 bump 到 1.0.8，须先删该目录（同号覆盖会静默取旧件，见 `mc-prereq-lib-version-contract` §2①）。

### KI-F14 ＝ tooltip 染色 mixin 与 **Architectury** 抢同一 `@ModifyConstant` 常量 ⇒ **进世界 / 悬停 GUI 时必崩**（已修）

- **现象**：修好 KI-F13 后游戏**能到主菜单**，但**创建世界 / 悬停 GUI 时崩溃**
  （`crash-2026-09-29_19.22.31-client.txt`，`Description: Rendering screen`）：
  ```
  java.lang.RuntimeException: Mixin transformation of net.minecraft.class_8002 failed
  Caused by: MixinTransformerError: An unexpected critical error was encountered
  Caused by: InjectionError: Critical injection failure: Constant modifier method
      modifyTooltipBackgroundColor(I)I in architectury.mixins.json:client.MixinTooltipRenderUtil
      from mod architectury failed injection check, (0/1) succeeded. Scanned 0 target(s).
      Using refmap architectury-fabric-refmap.json
  ```
- **根因（日志里已有一句话的定论）**：
  ```
  @ModifyConstant conflict. Skipping architectury.mixins.json:client.MixinTooltipRenderUtil
    from mod architectury->@ModifyConstant::modifyTooltipBackgroundColor(I)I with priority 1000,
    already redirected by astral_dice.mixins.json:bridge.TooltipRenderUtilColorMixin
    from mod astral_dice->@ModifyConstant::astralDice$backgroundColor(I)I with priority 1000
  ```
  本模组的 `TooltipRenderUtilColorMixin` 原用 3 个 `@ModifyConstant` 改写
  `TooltipRenderUtil#renderTooltipBackground` 里的三个 ldc 常量
  （`0xF0100010` / `0x505000FF` / `0x5028007F`），而 **Architectury API 的
  `client.MixinTooltipRenderUtil` 改的正是同一批常量**（那是它「让模组自定义 tooltip 颜色」的 API）。
  Mixin 对 `@ModifyConstant` 是**排他**语义（同一个 ldc 只能被一个 modifier 改写）⇒ 后到者被 skip；
  双方 priority 同为 1000、**本模组先应用** ⇒ architectury 的注入落空，而它自身
  `injectors.defaultRequire = 1` ⇒ 抛 `InjectionError`（致命）→ 崩。
- **⚠️ 为什么"能启动、进世界才崩"**：目标 `net.minecraft.class_8002` = `TooltipRenderUtil`，
  **只在首次渲染 tooltip 时**才被类加载 ⇒ 主菜单不加载它，KI-F13 修复后的冒烟（止于主菜单）也看不见。
- **⚠️ 为什么 dev 全绿**：dev 的 `fabric-1.20.1/run/client/mods` 只有 **5** 个 jar
  （endec / fiber / gson / netty / puzzlesaccessapi），**根本没有 architectury** ⇒ 又一次「生产独有」缺陷。
  （本轮实测环境里用户还自行加了 Carpet / PlaceholderAPI / AttributeFix / Carpet-Fixes
  ⇒ `Loading 148 mods`，覆盖比 143 时更广。）
- **修法：改用 `@WrapOperation`（MixinExtras）包裹「调用指令」**，四个 wrapper 覆盖全部 6 个调用点：
  ```
  renderHorizontalLine(g,x,y-1,w,z,BG)  ×2   → astralDice$background$horizontal
  renderRectangle     (g,x,y,w,h,z,BG)  ×1   → astralDice$background$rectangle
  renderVerticalLine  (g,x∓1,y,h,z,BG)  ×2   → astralDice$background$vertical
  renderFrameGradient (g,x,y+1,w,h,z,TOP,BOT) → astralDice$borderGradient
  ```
  `@WrapOperation` 包裹的是 **INVOKESTATIC**，`@ModifyConstant` 改的是 **LDC** —— **不同字节码位置 ⇒ 可共存**
  （MixinExtras 的注入器本就是为「多模组对同一调用点各自注入」设计的；本项目 `LivingHurtBridgeMixin`
  早已按这个理由这么写，只有本 mixin 还停留在老的 `@ModifyConstant`）。
- **产物取证（三级，全是字节码/文件级，不靠"看日志感觉"）**：
  1. **注解已被 Loom 直接重映射**（jar 内**无** refmap 文件 ⇒ 那条
     `Reference map 'astral_dice.refmap.json' … could not be read` 警告**无害**）：
     ```
     target="Lnet/minecraft/class_8002;method_47951(Lnet/minecraft/class_332;IIIII)V"    // renderHorizontalLine
     target="Lnet/minecraft/class_8002;method_47950(Lnet/minecraft/class_332;IIIIIII)V"  // renderRectangle
     target="Lnet/minecraft/class_8002;method_47949(Lnet/minecraft/class_332;IIIII)V"    // renderVerticalLine
     target="Lnet/minecraft/class_8002;method_47948(Lnet/minecraft/class_332;IIIIIII)V"  // renderFrameGradient
     ```
  2. **注入结果**（`-Dmixin.debug.export=true` 导出的 `class_8002.class` 反编译）——
     4 个 wrapper 覆盖全部 **6 个调用点**，且 **architectury 的注入同时存在**（19 处引用）：
     ```
     invokestatic wrapOperation$…$astralDice$background$horizontal  ×2
     invokestatic wrapOperation$…$astralDice$background$rectangle   ×1
     invokestatic wrapOperation$…$astralDice$background$vertical    ×2
     invokestatic wrapOperation$…$astralDice$borderGradient         ×1
     ```
  3. **运行期**：生产冒烟（`-PreloadClasses net.minecraft.class_8002` 强制加载该类）
     ⇒ `[preload] OK   net.minecraft.class_8002 -> net.minecraft.class_8002` + `Sound engine started` +
     `crash-reports/` 无新增；dev 侧同参数回归同绿。

#### 顺带修的第二个缺陷：`astral_dice:astral_guide` 配方解析失败

- **现象**（同一次启动的日志）：
  ```
  [Render thread/ERROR]: Parsing error loading recipe astral_dice:astral_guide
  com.google.gson.JsonSyntaxException: Unknown item 'patchouli:guide_book'
  ```
- **根因**：该配方的产出是 `patchouli:guide_book`，而**整合包并未安装 Patchouli**
  （`fabric.mod.json` 里 patchouli 既不是 `depends` 也不是 `recommends`
  ⇒ 属**可选**前置，模组本该优雅降级）。
- **修法**：加 **`fabric:load_conditions`**（`condition: fabric:mod_loaded` / `modid: patchouli`）
  ⇒ 未装 Patchouli 时**整条配方不加载**，不再报 `Unknown item`。
  取证：`fabric-resource-conditions-api-v1` 的 mixin 列表含 **`JsonDataLoaderMixin`**
  （覆盖通用 JSON 数据加载器 ⇒ 对 `recipes` 生效）；修后日志**不再出现**该 `Parsing error`。
- ⚠️ **仍然要提醒用户**：整合包没有 Patchouli ⇒ **《星之骰戏》手册在该整合包内不可用**
  （配方被条件隐藏，也再无其它获取入口）—— 需要手册请自行安装 Patchouli。

#### 本轮新增的测试能力（把这个「看不见的缺陷」变成可测）

1. **类预加载钩子** `-Dastral_dice.preloadClasses=<intermediary 名,逗号分隔>`
   （`AstralDiceClient#preloadClassesForSmokeTest`）：
   Mixin 注入在**目标类加载时**才应用，而这类目标类常常「要玩家交互才加载」⇒ 把目标类提前到**启动期**加载，
   缺陷当场爆出（**fail-loud：记 ERROR 后重新抛出**，不吞异常 —— 吞掉就成了假绿）。
   类名一律按 **intermediary** 解析（`MappingResolver.mapClassName("intermediary", …)`）
   ⇒ **同一个参数在 dev 与生产都能用**（生产恒等、dev 转成 named）。
2. **`ft_prod.ps1` 新增 `-PreloadClasses` / `-ExtraJvmArg`（可重复）**，并新增两条判据：
   日志出现 `InjectionError|failed injection check` ⇒ 判定 `MIXIN_FAIL`（rc=1，并打印那一行）；
   启动前**删除实例 `latest.log`** 作为判定基线（否则上一轮崩溃留下的 ERROR 会污染判定 ⇒ **假红**）。
3. **Loom 侧对称支持**：`:fabric-1.20.1:runClient -PpreloadClasses=<…>`（`vmArg`；
   ⚠️ **API 不通用** —— ModDevGradle/ForgeGradle 是 `jvmArgument`/`vmArgs`）。

### KI-F15 ＝ 饰品栏前置：从「Trinkets 硬必需」改为「Trinkets 或 Accessories 二选一」（**已修，2026-09-29 用户裁决**）

- **现象**：只装 **Accessories** 的玩家**完全无法启动** —— 加载器直接拒绝（实测 19:59）：
  ```
  Immediate reason: [HARD_DEP_NO_CANDIDATE astral_dice 1.3.2-alpha.1+fabric_1.20.1 {depends trinkets @ [>=3.7.2]}]
  Fix: add [add:trinkets 3.7.2 ([[3.7.2,∞)])], remove [], replace []
  ```
  ⚠️ 这与本线的**既有设计自相矛盾**：`CuriosApi` 早就把 Accessories 定为**主通道**（KI-F3），
  可声明面却把 Trinkets 写成**硬前置** ⇒ 「主通道」在声明层面根本进不来。
- **根因（两层）**：
  1. **声明层**：`fabric.mod.json` 的 `trinkets` 写在 **`depends`** 里。而 Fabric 的依赖声明是
     **AND 语义** —— **没有「任选其一」的写法**（`recommends` / `suggests` 都只是软提示，
     无法表达「这两个里至少一个」）⇒ **二选一只能运行时判定**。
  2. **代码层**：三处把 Trinkets 当硬前置，缺席时会以**类加载失败**（一堆 `NoClassDefFoundError`
     堆栈）而非一句人话收场：
     - `compat/curios/TrinketBridge` —— 硬引用 `dev.emi.trinkets.api.*`（含内部
       `record Adapter implements Trinket`）；
     - `compat/curios/CuriosApi#getCuriosInventory` —— **无条件**查询 Trinkets 源；
     - `AstralDiceMod#onInitialize` —— **无条件**调 `TrinketBridge.registerAll()`。
- **修法（四处）**：
  1. `trinkets` 从 `depends` **移入 `recommends`**（与 `accessories` 并列）；
  2. 新增 `ModCompatibilityCheck#verifyAccessoryProviderOrThrow()`：在 **`onInitialize()` 的第一条语句**
     执行（必须先于任何触碰 Trinkets 的代码），**两个都不在**时才抛 `ModLoadingException`
     （完整中文说明：装哪个、去哪装、两者差异、同时装也可以）；每次启动打一条机器行
     `AP_FAB_ACCESSORY_PROVIDER: OK|FAIL (trinkets=… accessories=…)` 供冒烟断言；
  3. `CuriosApi` 的 Trinkets 源加 `TRINKETS_LOADED` 守卫（与既有的 `ACCESSORIES_LOADED` 对称）；
  4. `TrinketBridge.registerAll()` 的**调用点**加守卫 —— ⚠️ **守卫必须在调用点**：
     `TrinketBridge` 缺席时**整个类都加载不了**，写在方法体内等于没写（方法体要先加载类才有机会执行）；
     方法体内再留一道防线仅为将来调用方的兜底。
  另：两个 modId 常量与两个布尔**单一权威**在 `ModCompatibilityCheck`，`CuriosApi` 委托它（避免两处各判一次而漂移）。
- **实测（dev 四态 + 生产）**：
  | 态 | 构造方式 | 读数 |
  |---|---|---|
  | **仅 Accessories**（= 用户整合包现状） | `-PtestTrinkets=false` | 加载 82 模组；`AP_FAB_ACCESSORY_PROVIDER: OK (trinkets=false accessories=true)`；`Sound engine started`；Accessories 适配器已注册、**无** Trinkets 适配器；**无** `NoClassDefFoundError` |
  | **仅 Trinkets** | `-PtestAccessories=false` | 加载 81 模组；`OK (trinkets=true accessories=false)`；`Sound engine started`；Trinkets 适配器已注册 |
  | **两者同装** | 默认 | 加载 83 模组；`OK (trinkets=true accessories=true)`；**两个适配器都注册**（聚合门面语义不变，见 KI-F3） |
  | **两者皆无** | `-PtestTrinkets=false -PtestAccessories=false` | 加载 80 模组；`AP_FAB_ACCESSORY_PROVIDER: FAIL (trinkets=false accessories=false)` ⇒ **拒绝启动**。⚠️ 文案**不在 `latest.log`**（日志在抛出处断掉），而在 `run/client/crash-reports/crash-*.txt`：`Description: Initializing game` → `Caused by: …ModLoadingException: [星之骰戏] 缺少饰品栏前置：既没有检测到 Trinkets，也没有检测到 Accessories。` + 一整段中文操作指引（首行即可读，与既有黑名单检查同一可见性口径） |
  | **生产**（整合包实况 = 只有 Accessories） | `ft_prod.ps1` | **PASS**（修复前为 `HARD_DEP_NO_CANDIDATE` 拒绝启动） |
- **对下游的影响**：饰品栏提供方变了但**槽位与物品清单不变** —— `data/curios/tags/items/*.json`
  是普通物品标签（由**原版**标签加载器读，不依赖任何饰品模组），Accessories 侧走自定义 predicate
  `astral_dice:curio_slot`，两侧共用同一份清单（KI-F3）。

### KI-F16 ＝ dev 开关 `-PtestAccessories=false` **从来无法通过编译**（**已修**）—— 「注释声称可用、实际从没跑过」

- **现象**：`./gradlew :fabric-1.20.1:compileJava -PtestAccessories=false` 直接失败：
  ```
  StarCoinWalletButtons.java:311: 错误: 方法不会覆盖或实现超类型的方法
          @Override
  ```
  而 `build.gradle` 的注释**一直写着**「跑『仅 Trinkets』那一态时用 `-PtestAccessories=false` 关掉」
  ⇒ **该开关从来没有被真正执行过**（所以这个矛盾一直没被发现）。
- **根因**：Loom 的**依赖方接口注入**会随「依赖是否进入**运行期**」而开关。
  Accessories 的 `fabric.mod.json` 声明 `custom.loom:injected_interfaces: net/minecraft/class_4264 →
  AbstractButtonExtension`，把 `AbstractButtonExtension` 注入 `AbstractButton`；把 Accessories
  移出**运行期**后注入**消失** ⇒ 为「必须补上注入来的抽象方法」而写的
  `@Override getRenderingEvent()` 立刻变成「不覆盖任何方法」⇒ 编译失败。
  ⚠️ 佐证这不是类路径问题：`compileClasspath` 里 accessories **仍在**（`modCompileOnly` 是无条件的）
  —— 失效的是**注入**本身。
- **修法**：去掉该方法的 `@Override`（**保留方法体、返回类型与整段 javadoc**）⇒ **两种注入态都成立**：
  注入在 ⇒ 按签名实现该接口方法（仍满足「必须补上抽象方法」）；注入不在 ⇒ 它就是普通方法、无人引用。
  行为与改动前**完全一致**（该方法体返回 `null`，Accessories 只对**它自己的**按钮调用
  `ButtonEvents.adjustRendering(...)`，从不调本模组的）。
- **实测**：四态 `compileJava` 均 `BUILD SUCCESSFUL`：
  默认 / `-PtestAccessories=false` / `-PtestTrinkets=false` / `-PtestTrinkets=false -PtestAccessories=false`。
- **教训（通用）**：**「给了开关」≠「开关可用」** —— 凡在注释/文档里承诺「用某参数可以跑某态」，
  就必须至少实跑一次该参数；否则它只是一个会把人引向错误结论的假入口（本轮四态矩阵正是靠实跑才暴露它）。

### KI-F17 ＝ 玩家持久化附件**每次登录被静默丢弃**：附件键**注册晚于**玩家数据反序列化（症状＝「帕秋莉手册每次登录都补发一本」）（**已修**）

- **现象**（用户 2026-09-29 第二次反馈，原话「重复发放帕秋莉手册的问题再次出现」）：整合包玩家**每次重开游戏**，
  背包里都会多出一本《恋的规则书》。⚠️ 该缺陷**此前修过一次**（2026-09-15，死亡路径的赠书守卫），
  所以第一次看到「又出现了」很容易被误判为「上次没修干净」——**实际是两条完全不同的根因**。
- **根因**：`ModAttachments` 的**静态初始化是惰性的**。Java 的 `<clinit>` 只在**第一次真正使用该类**时才跑，
  而 mod 初始化路径上原本**没有任何代码触碰 `ModAttachments`** ⇒ 它的静态初始化被推迟到
  「**第一次有代码读写附件**」的那一刻 —— 也就是**玩家登录处理器** `PlayerLifecycleHandler#giveGuideBookOnFirstJoin`，
  而那时玩家的 NBT **已经反序列化完了**。
  Fabric 的附件反序列化**按 id 查注册表**：查不到就打一条
  `Unknown attachment type astral_dice:xxx found when deserializing, skipping` 并**静默丢弃该键**（不报错、不崩）。
- **影响面（实测）**：一次登录被丢弃 **33 个键**。除 `guide_book_given`（⇒ 发放守卫永远读到 `false`
  ⇒ **每次登录补发一本**）之外，其余是**玩家可感知的战斗状态**：治疗点数、立牌锁定与冷却、
  白泽赐福、教主降神、怪力侦探层数等。
- **为什么此前测不出来**：① 缺陷是**静默的**（无异常、无崩溃、无 WARN 级以上的本模组栈帧）；
  ② **测试世界的玩家数据少** —— 键丢得少、也更不容易被察觉；③ 玩家 NBT 里的标记**一直是 1**
  （写是写进去了的），只是**读不出来**，所以「打开 NBT 看有没有」这种取证方式会得到「有」。
- **修法**：`ModAttachments#ensureRegistered()`（**空实现，只为触发 `<clinit>`**）+ 打一条机器行
  `AP_FAB_ATTACHMENTS: 附件键已注册 109 个(必须先于任何玩家数据反序列化)`；调用点放在
  `AstralDiceMod#onInitialize()` 里、**紧随前置守卫之后**（`FabricBridges.installEarly()` 之前）。
  ⚠️ **不要再删除这个调用**：删掉它**不会编译报错、也不会在测试世界暴露**，只会让线上玩家每次重开游戏
  丢掉一次战斗状态。
  另加**常驻诊断**（`PlayerLifecycleHandler`）：每次登录打
  `AP_FAB_GUIDEBOOK: uuid=… call=… given=… patchouli=… enabled=… books=…`，
  并在**写回后立刻回读**打出 `GIVEN uuid=… reread=… books=…` —— 这一条「写后回读」是把病因
  从「发放时机」区分到「**存取通路**」的决定性读数（本次正是靠它把方向从「守卫写错」扭到「值读不出」）。
- **实测证据（跨会话 A/B，2026-09-29 20:55 / 21:01，`-PdevUsername=AstralDev` 固定玩家名 ⇒ 两轮是同一玩家）**：
  - session1（首登）：`given=false … books=0` → 发放 → `GIVEN … reread=true books=1`
    （`reread=true` 证明 109 个键**注册成功且没有丢**）。
  - session2（重登，同一 UUID）：`AP_FAB_ATTACHMENTS: 附件键已注册 109 个` 打在 **21:01:37**、
    登录在 **21:01:41**（注册**早于**玩家数据反序列化）→ `given=true … books=1` ⇒ **不补发、仍只有 1 本**。
  - `latest.log` 与 `debug.log` 全文扫描 `Unknown attachment type` 命中数 = **0**。
- **教训（通用）**：Fabric 附件 API 的**键注册必须在 mod 初始化期完成**；凡改动附件键集合或
  任何持久化 schema，收尾**必须 grep 日志确认没有 `Unknown attachment type`**（这是唯一能看见它的读数）。

### KI-F18 ＝ Accessories 槽位图标「文件存在但不显示」：图标必须落在**原版 blocks 图集**的目录 `assets/<ns>/textures/gui/slot/`（**已修**）

- **现象**（用户 2026-09-29 反馈，附截图）：**Accessories 饰品栏的槽位图标显示为紫黑格 / 空白**。
  ⚠️ 提交证据时用户明确说「问题依旧」——即上一轮「把图标文件放到 `textures/slot/`」并没有解决。
- **根因**：**两个饰品通道取图标的机制完全不同**：
  | 通道 | 取图标方式 | 因此图标该放哪 | `icon` 字段该写什么 |
  |---|---|---|---|
  | Trinkets | **路径直连**：拼 `textures/<icon>.png` | `assets/<ns>/textures/<任意路径>.png` | **文件路径**（如 `astral_dice:slot/x`） |
  | Accessories | 走**原版 `minecraft:blocks` 图集** | **必须** `assets/<ns>/textures/gui/slot/*.png` | **图集内 sprite 名**（如 `astral_dice:gui/slot/x`） |
  Accessories 的图集成员资格来自原版 `assets/minecraft/atlases/blocks.json` 的声明
  `{"type": "directory", "source": "gui/slot", "prefix": "gui/slot/"}` ⇒ **只有该目录下的贴图会被收录**。
  原先图标放在 `textures/slot/`（Trinkets 口径）⇒ **文件明明存在，但 Accessories 在图集里找不到对应 sprite**
  ⇒ 紫黑格。**这就是「改一轮还没好」的原因：缺陷不是「文件缺失」，而是「路径不在图集目录里」。**
- **修法（同时满足两条通道）**：把图标移入图集目录，`icon` **统一改写为图集 sprite 名**——
  对 Accessories 是 sprite 名 `gui/slot/…`，对 Trinkets 它同时就是文件路径 `textures/gui/slot/….png`
  ⇒ **同一份文件、同一个 `icon` 字符串，两条通道都成立**：
  - `assets/astral_dice/textures/slot/*.png` → `assets/astral_dice/textures/gui/slot/*.png`（3 个）；
  - `icon` 由 `astral_dice:slot/empty_*_slot` 改为 `astral_dice:gui/slot/empty_*_slot`，共 7 处：
    `data/astral_dice/accessories/slot/{chip,dice,stand}.json`、
    `data/astral_dice/accessories/group/astral_dice.json`、
    `data/trinkets/slots/astral_dice/{chip,dice,stand}.json`。
- **双层守门（本轮新增）**：
  1. **静态**：`tools/verify_fabric_assets.py` 新增第 8 项「槽位图标必须位于图集目录」，已做**正/反向**各验一次
     （反向 = 故意把图标放回 `textures/slot/` 时该项必须 FAIL）。
  2. **客户端自检**：新增 `compat/accessories/AccessoriesClientIconCheck`（仅当 Accessories 在场）
     —— 进世界后跑一次，**同时检查「文件存在」与「是否被图集收录」**，打
     `AP_FAB_SLOT_ICON: 槽位图标自检 槽位=15 文件缺失=0 图集未收录=0` + 逐槽位明细。
     ⚠️ **只查「文件存在」是不够的** —— 本缺陷恰恰是「文件在、路径错」，必须查图集成员资格。
- **实测证据**（2026-09-29 21:01:42，客户端日志）：自检读数 `槽位=15 文件缺失=0 图集未收录=0`；
  明细里本模组 3 个槽位为 `icon=astral_dice:gui/slot/empty_{chip,dice,stand}_slot  file=yes inBlocksAtlas=yes`，
  其余 12 个为 Accessories 自带槽位（同样 `inBlocksAtlas=yes`）。
  ⚠️ 该自检跑在 dev（named）下，但**图集是资源层产物、与映射无关**（同一份 jar 的资源），故对生产同样成立。

## 10. 变更记录

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
