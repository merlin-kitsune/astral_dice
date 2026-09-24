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

## 9. 变更记录

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
