# 已知问题与未来版本修补清单（KNOWN-ISSUES）

> **本文件是「未修项」的唯一版本化载体**。本地 `docs/` 目录被 `.gitignore` 忽略、不随仓库分发，故凡需要跨会话/跨分支可见的未修项一律登记在此。
> 本文件**只登记「尚未实施」的事项**；每条的最终状态必须如实反映现实，**禁止为了缩短清单而删除条目**。

## 0. 使用纪律（必须遵守）

1. **实施前必须先复核，不得直接照文档改**：本清单多数条目的定性来自 **2026-09-15 15:45 的扫描快照**，其后本批已改动过 `combat/DiceCombatModifiers`、`combat/SpellDamageRegistry`、`combat/HostileTargets` 等文件 ⇒ 「仍存在/未修」**不等于当前 HEAD 仍存在**。按 `mc-two-round-verification` 流程，先做**独立代码复核**（第一遍，验证者 ≠ 实现者），把「文档定性」与「当前 HEAD 实况」逐条对齐，再交用户定夺。
2. **双版本同步**：所有修补一律 `neoforge-1.21.1` + `forge-1.20.1` 同时实施，保持功能对等（项目默认规则）。
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
  （已无任何写入方），待库侧过渡符号清理任务一并删除。
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

### KI-M3 ＝ 合并后仍需在游戏内复核/由后续任务收口的三项（未实施）

1. 选择器类立牌的**冷却与锁定**实际表现（见 KI-M2 的已知取舍）。
2. `moses_ready.png` / `komachi_count.png` 等随主线/本分支删除的贴图是否有残留引用
   （2026-09-17 静态核验：0 处；游戏内未复核）。
3. 库侧过渡符号在合并后已**无消费方**（静态核验 0 处引用），由库侧清理任务删除：
   `GameplayConstants.EVENT_RANGE` / `EVENT_APPLY_MAID` / `KOMACHI_EXTRA_PLAYS_CAP`、
   `EventTargetCollector.collectTargets` + 女仆收集、`event/AstralEventType|EventContext|EventEffect`、
   `ReadyEffect`（清单见 `temp/sink-manifest-20260917.md` §6）。

## 7. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-09-15 | 建档：登记 B7 交付后审计发现的 11 项未修/未决项（用户裁定「作为未来版本修补内容」） |
| 2026-09-16 | KI-5 确认根因并**按推荐方案修复**（1.20.1 法伤主链路事件 `LivingHurtEvent` → `LivingDamageEvent`，双版本对等）；新增回归用例 `ELECTRIC-GLOVE-AOE-BASE-{1.21.1,1.20.1}` |
| 2026-09-16 | 追加 A（《恋的规则书》赠书守卫随死亡保留）**闭环**：1.21.1 首登 + 重登两轮用例各 7/7 PASS；追加 B（双版本死亡保留集合一致性）核为一致 |
| 2026-09-16 | 新增「测试工具链」纪律（见 `AGENTS.md` / `scripts/test/TESTING-SPEC.md` §12）：严格分层硬预算、`MT_WAIT` 心跳、进度信标 `cases/.mt_progress.json`、看门狗语义判据、探针自动同步、点火入口 `mt_fire.ps1`（根治「长驻孙进程持有调用方管道 ⇒ 命令早已结束却看起来永不返回」） |
| 2026-09-17 | 主线 `multi-1.20.1-1.21.1`(8f68482)→ `multi-dev-next` 合并:旧「待命等待器」由目标选择器取代(见 §6 KI-M1)、三态化与选择器共存(KI-M2);登记选择器类立牌锁定保护的待修项与库侧过渡符号清理项(KI-M3) |
