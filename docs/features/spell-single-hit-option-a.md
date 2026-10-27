# 法伤单次结算 · 方案A（折叠）可行性只读评估

> 任务：审计正本 `docs/features/attack-damage-chain-audit.md` 的 **N15**（一次法伤命中产生两次 `hurt()`）。
> 性质：**只读取证**。本文只给结论与 `文件:行号` 证据，不改任何源码/资源/版本号/CHANGELOG。
> 分支 `multi-main`；两条发布线 `neoforge-1.21.1` / `forge-1.20.1`；第三方取证对象 = `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods`（**508 个 jar**，502 个含 `mods.toml`）。
> 取证方式：读仓库源码；读两条线的 `build/moddev/artifacts/` sources jar（`neoforge-21.1.235-sources.jar` / `forge-1.20.1-47.4.10-sources.jar`）逐行核原版公式；对第三方 jar 用 `zipfile` 列条目 + `.class` 常量池扫描（**未起游戏、未跑 gradle**）。

---

## §0 结论速览

| 问题 | 判定 | 一句话结论 |
|---|---|---|
| **Q1 为什么拆两次 hurt** | **SUPPORTED** | 代码自述两条硬意图：① 加成必须按**真伤口径**结算（不吃护甲/韧性/保护附魔）；② 加成**不能被当成「玩家的直接攻击」**（否则重走骰战）。跳字合并（同组 `tag`）是顺带的第三意图。见 `DamageEffectCardHandler.java:76-98`（neo）/ `:76-104`（forge）。 |
| **Q2 路径0（折叠）** | **可行（SUPPORTED）** | 双线各只需改 **2 处**（折叠点 + 音效取值的 `+ bonus` 双算）。跳字**零退化**（现网本来就合并成一条，折叠后仍是一条且数值相同）。代价：加成**失去抗性/保护减免**（高护目标上最多放大约 **4.6×**，极端 13.9×），且折叠后加成的减免暴露面**跟随基础伤害源**（基础源若不 bypass 护甲，加成会被护甲削减）。 |
| **Q3 路径1（预补偿）** | **REFUTED（作为达成单次结算的路径）** | 需逆 6 层，其中**5 类不可逆**（抗性封顶→恒 0、无敌帧差额结算→完全丢失、吸收死区、`Math.max(1,…)` 类下限、第三方 mixin 改公式）。**更根本**：它的语义是「让**第二次** hurt 的输入经减免后恰好等于期望 D」⇒ **以保留两次 hurt 为前提**，与 N15 裁决「必须改回单次结算」自相矛盾；一旦不保留第二次 hurt，路径1 立即等价于路径0。 |
| **Q4 第三方干扰面** | **部分可判定** | 五个目标里 **3 实装 / 2 未装**：神化(apotheosis 8.9.0 + apothic_attributes 2.11.0)、Iron(irons_spellbooks 3.16.3)、新生魔艺(ars_nouveau 5.13.3)、诡厄巫法(goety 3.2.2)、神秘遗物+(enigmaticlegacyplus 1.1.2)；**血魔法 bloodmagic / 咒法学 hexcasting 均未安装**。最大「每次伤害事件」订阅面 = **神秘遗物+（43 个 LivingDamageEvent 类 + 47 个 LivingIncomingDamageEvent 类）**；唯一直接 mixin `hurt` 的是 **irons_spellbooks.LivingEntityMixin** 与 **goety.LivingEntityMixin**；直接改写原版伤害公式的是 **apothic_attributes 的 CombatRulesMixin / LivingEntityMixin**。 |
| **Q5 推荐** | **路径0（折叠），P1** | 「单次结算 + 期望数值 + 跳字不退化」三者中，折叠可**完整达成前两者之口径统一与第三者**；唯一需要裁决的是「加成是否接受抗性/保护减免」——建议接受（这正是 N11 要求的「true_damage 与法伤链一致」方向），不建议做补偿系数。额外发现：法伤白名单有 **3 处死键 + 1 处漏项**（与本缺陷同源，建议同批修）。 |

---

## §1 两次 hurt 的成因

### 1.1 机制：在「伤害事件回调内部」再发一次 hurt（嵌套 hurt）

- 折叠前的调用链（neoforge-1.21.1）：
  `LivingEntity.hurt()` `:1142` → `actuallyHurt()` `:1785` → `:1789 float damage = CommonHooks.onLivingDamagePre(this, peek())`（= `LivingDamageEvent.Pre`）→ **本模组** `DamageEffectCardHandler.onLivingDamagePre` @:51-52`（NORMAL）→ `:95 target.hurt(bonusSource, (float) bonus)` → **再次**进入 `LivingEntity.hurt()`，跑完整条管线（护甲→抗性/保护→Pre→吸收→Post）。
- forge-1.20.1 同构，只是事件类不同：`forge .../event/DamageEffectCardHandler.java:57-58`（`LivingDamageEvent`，非 `.Pre`）→ `:102 target.hurt(bonusSource, (float) bonus)`。
- 加成伤害源 = `ModDamageTypes.trueDamage(target.level(), player)`（neo `:90-91`、forge `:97-98`），定义在 `.../damage/ModDamageTypes.java:144-146`：`new DamageSource(holder(level, TRUE_DAMAGE), null, causing)` —— **直接伤害实体为 null**、causing entity = 施法玩家。
- 之所以「第二次 hurt 不会被无敌帧吃掉」，是因为 `true_damage` 已登记进 `bypasses_cooldown`：`neoforge-1.21.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json`（7 项，含 `true_damage`），原版闸门 `LivingEntity.java:1190` / forge `:1131` 因此直接放行。这是本设计**能生效**的前提，也意味着两段都会重写 `lastHurt` / `invulnerableTime`。
- 重入闸门：`DamageEffectCardHandler.java:31-33`（`ThreadLocal APPLYING_TRUE_BONUS`）+ `:43-45 isApplyingTrueBonus()`；注释 `:34-42` 已明说「该嵌套 hurt 会**再跑一遍**整条伤害管线 ⇒ 其它 `LivingDamageEvent` 处理器会把它当成第二次命中」。

### 1.2 原始设计意图（逐条对应代码）

| 意图 | 出处 | 证据 |
|---|---|---|
| ① **增伤必须绕开护甲/韧性/保护附魔**（真伤口径） | neo `DamageEffectCardHandler.java:76-81` 注释（2026-09-14 用户裁决）：「不再并进本次伤害事件——并进去的话这份加成会连同武器伤害一起吃护甲值/盔甲韧性/保护附魔的减免，与『伤害效果牌伤害纳入真伤机制』的口径不符」 | `true_damage` 登 `bypasses_armor`（`data/minecraft/tags/damage_type/bypasses_armor.json`，6 项）；`ModDamageTypes.java:19-25` 注释 |
| ② **不能被当成「玩家的直接攻击」**（否则重走骰战 / 被第三方判成玩家近战） | 同上注释后半：「直接伤害实体为空 + 击杀归属施法者（与旧 `explosion(null, player)` 同形状，不会被本模组或其它模组当成"玩家的直接攻击"重走骰战）」 | `ModDamageTypes.java:144-146` 第二个参数 = `null`；骰战主链入口 `combat/DiceCombatEvents.java:355` `if (!isMeleeWeaponAttack(player)) return;` |
| ③ **跳字仍合成一条**（视觉上不该出现两个数字） | `DamageEffectCardHandler.java:87` `DamageNumberAggregator.tag(target, source, Group.SPELL)`；`:92-94` 对 `bonusSource` 再 `tag(..., SPELL)` | `DamageNumberAggregator.java:88-91`（`TAGS` = (entityId, DamageSource 实例身份) → Group）、`:106-110`（`tag` 必须在 hurt 前）、`:125-141`（同槽 `acc.total += finalDamage`） |

> 结论：这不是「为了跳字分组」才拆的，而是**为了口径**（真伤 + 非玩家直接攻击）才拆；跳字分组是配套补丁。因此「折叠」是否会破坏设计意图，取决于抵消哪一条：折叠能保住 ②（同一个事件的 source 仍是基础法术源，本就不算玩家近战），但**放弃 ①**（加成开始吃基础源的减免）。

### 1.3 审计「翻倍清单」逐条复核（重要修正）

审计 N15 原文（`docs/features/attack-damage-chain-audit.md:25`、`:319`）称「Papara 回血、立牌受击钩子、弱印记 ×1.10、狂暴反噬、筹码减伤全部翻倍」。逐条核后**只有一半成立**：

| 效果 | 出处 | 判定 | 原因 |
|---|---|---|---|
| 弱印记 ×1.10（+命运指引 0.20） | `combat/DiceCombatEvents.java:1537-1556`（LOW） | **SUPPORTED（真翻倍）** | `:1555 event.setNewDamage(event.getNewDamage() * multiplier)` 无 `true_damage` 判定、无真伤闸门 ⇒ 两段各乘一次（1.10² = 1.21；含命运指引 1.30² = 1.69） |
| 受击侧倍率（雨中/水下 +40% 等 `victimFactor`） | `event/EnderDiceHandler.java:142-155`（HIGH） | **SUPPORTED（真翻倍）** | `:153 event.setNewDamage(applyVictimDamageModifiers(...))` 对**每个** Pre 事件生效 ⇒ 1.4² = 1.96 |
| 筹码固定点数减伤（磨刀石 `-N` / sherry `-layers-guardian` / kangel fan `-1`） | `event/ChipDamageHandler.java:48-135`（LOWEST），:95-96 判定 `unreducible`（= `CARD_COST` 或 `UNREDUCIBLE_DAMAGE` 之一），`true_damage` 不在其中 | **SUPPORTED（真翻倍）** | 固定点数对两段**各扣一次** |
| RenShield 黄心吸收 | `item/RenShieldManager.java:406-415`（LOWEST） | **SUPPORTED** | `:415 setNewDamage(remaining)` 对两段各钳一次 ⇒ 黄心被多消耗 |
| 狂暴反噬 | `combat/DiceCombatEvents.java:1474-1501`（LOW），:1485-1488 | **REFUTED（不翻倍）** | `if (isInternalAoe()` … `DamageEffectCardHandler.isApplyingTrueBonus()) return;`（早退） ⇒ 反噬**刻意跳过**加成段 |
| Papara 回血 | `item/sign/PaparaSignItem.java:72-89`（Post），`:87 int heal = Math.max(1, (int) event.getNewDamage() / 2)` | **REFUTED（总量≈不变）** | 两段各回 `max(1, base/2)` + `max(1, bonus/2)` ≈ 总伤害的一半；折叠后 = `max(1, (base+bonus)/2)`。差异只在 `Math.max(1,…)` 下限与「heal 被调用两次」 |
| 立牌受击钩子 `invokeHurtHooks` | `combat/DiceCombatEvents.java:291-296` | **REFUTED（法伤根本不触发）** | 该钩子只挂在**骰战主链**（近战路径）里；法伤链不经过它，两次 hurt 都不会触发 |
| 凌灵立牌 `+5` | `event/LinglingSignEvents.java:94`（neo）/ forge `:108` | **现状 REFUTED，折叠后新增行为** | 现用 `isApplyingTrueBonus()` 排除加成段 ⇒ 折叠后该 `+5` 会开始作用于合计（**行为变化，需登记**） |

> 另：`item/sign/PaparaSignItem` 依赖的 `isHostileAttack(Player, DamageSource)`（`DiceCombatEvents.java:1775-1790`）在 `:1787-1788` 会 `if (attacker == player) return false;` ⇒ 施法者给自己打不算敌对；真伤段的 causing entity 恰是施法者，因此**跨玩家 PvP 法伤才会触发第二次回血判定**。

---

## §2 路径0（折叠）评估

### 2.1 折叠点与所需改动量

| 线 | 折叠写法 | 行号 |
|---|---|---|
| neoforge-1.21.1 | `event.setNewDamage(event.getNewDamage() + (float) bonus)` | 替换 `DamageEffectCardHandler.java:82-98` 整段（gate + `tag` + `trueDamage` + @:95 hurt`） |
| forge-1.20.1 | `event.setAmount(event.getAmount() + (float) bonus)` | 替换 `DamageEffectCardHandler.java:91-104` 整段（含 @:102 hurt） |
| 双线 | 音效阈值取值改为**折叠后金额**（去掉 `+ bonus`，否则双算） | neo `:108 int dealt = (int) Math.round(event.getNewDamage() + bonus);` → `Math.round(event.getNewDamage())`（同一回调内 `:82` 已折叠）；forge `:115 int dealt = (int) Math.round(event.getAmount() + bonus);` → `Math.round(event.getAmount())` |

> `:115-118 modifier.onHit(ctx, bonus)`（neo）/ forge `:122-125` **不变**（仍拿 bonus 原值，不读事件）。
> `isApplyingTrueBonus()` 的 3 个调用点（`DiceCombatEvents.java:1486`、`LinglingSignEvents.java:94`(neo)/`:108`(forge)）在折叠后恒 false；**建议保留方法体但改成 `return false;`**，避免牵动其它两条线（fabric-1.20.1 / neoforge-26.1.2 是否也引用**未验证**，见 §6）。

### 2.2 ① 折叠后「再乘算 / 再扣一次」的次序会变成什么

以 neo 1.21.1 为例（**基础事件 = CARD_SPELL 或第三方法术源**，加成折叠进同一事件）：

| # | 环节 | 位置 | 现状（两次 hurt） | 折叠后（一次事件） |
|---|---|---|---|---|
| 1 | `LivingIncomingDamageEvent`（可取消 / 改额） | `LivingEntity.hurt:1153`；`ObsidianDiceHandler.java:29-37`（HIGH，爆炸 -50%）、`NewSignEvents.java:33-46`（>10 清加成）、`DiceCombatEvents.java:1863-1882`（闪避取消） | base、bonus **各判一次** | 一次（对合计判定）⇒ `>10` 阈值更易触发 |
| 2 | 无敌帧闸门 / 差额结算 | `hurt:1190-1199`（`bypasses_cooldown` 使本模组类型跳过） | 两次进入，两次写 `lastHurt`/`invulnerableTime` | 一次（少一次外部无敌帧污染） |
| 3 | 护甲 + 韧性（`getDamageAfterArmorAbsorb`） | `actuallyHurt:1787` → `:1725-1734` → `CombatRules.java:16-30` | base：看基础源是否 `bypasses_armor`；bonus：**跳过** | **加成跟随基础源**：基础源若 bypass（`card_spell`/`goety` 多数类型/`ars` 的 crush·windshear 等）则不减免；若是 `irons` 的 14 类（未登 bypasses_armor）**则加成开始吃护甲** |
| 4 | 抗性 + 保护附魔（`getDamageAfterMagicAbsorb`） | `actuallyHurt:1788` → `:1739-1779` → `CombatRules.java:32-35` | **两段各乘一次** `R·P` | 一次，只作用于 base（加成**不再**吃 `R·P`） |
| 5 | `LivingDamageEvent.Pre` **HIGH** `victimFactor` | `EnderDiceHandler.java:153` | ×1.4 ×1.4 = **1.96** | **×1.4 一次** |
| 6 | `LivingDamageEvent.Pre` **NORMAL** 本处理器 | `DamageEffectCardHandler.java:82` | 发第二次 hurt | **折叠（`+bonus`）** |
| 7 | `LivingDamageEvent.Pre` **LOW** 弱印记 | `DiceCombatEvents.java:1555` | ×1.10 ×1.10 = **1.21**（命运指引 1.69） | **×1.10 一次**（命运指引 1.30） |
| 8 | `LivingDamageEvent.Pre` **LOWEST** 固定点数 / 黄心钳制 | `ChipDamageHandler.java:48-135`、`RenShieldManager.java:406-415` | 扣**两次** | **扣一次** |
| 9 | `LivingDamageEvent.Pre` **LOWEST** 凌灵 `+5` | `LinglingSignEvents.java:98` | 只作用于 base（`:92-94` 排除加成段） | **作用于合计（新增行为）** |
| 10 | 吸收 | `actuallyHurt:1790-1792` | 两段各吃一次 | 一次（合计吃）⇒ 与「两段各自吃」在数学上等价（`min` 语义） |
| 11 | `LivingDamageEvent.Post`：跳字 / Papara 回血 | `DamageNumberAggregator.java:112-117`；`PaparaSignItem.java:87` | 两条 Post | 一条 Post ⇒ 一跳字、一次 heal（量≈不变） |

**结论（①）**：折叠**不会**让加成被骰战再乘算——骰战主链只在近战路径（`DiceCombatEvents.java:355`），两次 hurt 都不进。真正会「再乘算」的是上表 **#1/#4/#5/#7/#8**（本模组自己的 `LivingIncomingDamageEvent`/Pre 处理器 + 原版减免），折叠后它们对加成**各只作用一次** —— 这正是 N15 想消灭的翻倍。反过来，加成**会失去** #4 的减免，这是路径0 唯一的数值代价。

**数值代价量化**（公式见 §3）：抗性 II（`i=(1+1)*5=10` ⇒ `R=15/25=0.6`）× 保护 IV（16 点 ⇒ `P=1-16/25=0.36`）= **0.216** ⇒ 折叠后该目标承受的加成从「本体的 21.6%」变成「100%」= **放大 4.6×**；若目标叠到抗性 IV（`R=0.2`）则为 0.072 ⇒ **放大 13.9×**。对无抗性无保护的普通目标：**0 变化**。

### 2.3 ② 跳字能否仍分两段

**现有实现不支持**，且**不需要**：
- `DamageNumberAggregator.java:36-48` 的取值是**管线终值**（`LivingDamageEvent.Post` `:112-117 record(target, source, getNewDamage())`）；`:125-141 record` 把同一 (目标, 组别) 的多次 final 值**累加**到 `acc.total`，`:179-190 flush` 每 `(目标, 组别, 收件人)` 只发**一条**包。
- `:73-86` `Group` 只有 `ATTACK`(0xFF5555) / `SPELL`(0x7CFC00) 两组，**颜色即客户端分槽键**。
- 现状两次 hurt 的第二段已被 `:92-94 tag(..., SPELL)` 归到同一槽 ⇒ **今天玩家看到的本来就是一条合并数字**。
- 折叠后：单事件 ⇒ 单槽 ⇒ 一条数字，且数值 = 折叠后的 `getNewDamage()` = 今天的 `base' + bonus'` ⇒ **视觉零退化**。
- 若真要做「两段跳字」（本体一段、加成一段），必须新增子槽/组别 + 改客户端分槽键（HUD 改动）+ 处理同 tick 时序 ⇒ **纯属自找，不建议**。

### 2.4 ③ 第三方可见差异（一次 vs 两次）

| 面 | 现状（两次 hurt） | 折叠后（一次 hurt） |
|---|---|---|
| 事件次数 | 两条独立伤害实例：`DamageSource` 不同（基础类型 vs `astral_dice:true_damage`，后者 direct entity = null） | 一条，source 只有基础类型 |
| 平台事件 | `LivingIncomingDamageEvent` / `LivingAttackEvent` / `LivingHurtEvent` / `LivingDamageEvent` / `Post` **各触发两次** | 各一次 |
| 反击/反伤/吸血/暴击类第三方 | 按「每次受击」计数 ⇒ 双计（§4 里 `irons`/`goety`/`enigmaticlegacyplus` 的订阅面都受影响） | 归一 |
| 统计/CombatTracker | 两条记录（总伤相同） | 一条记录（总伤相同） |
| 外部近战无敌帧 | 第二次 hurt 会重写 `lastHurt`/`invulnerableTime`（本模组类型 bypasses_cooldown，但**写**仍发生）⇒ 紧随其后的近战可能被吞或走差额 | 只有 base 那次写 |
| 击杀归属 | 可能由加成段完成击杀（`causing = 玩家`） | 由基础事件完成（两种都归玩家） |

> 结论：折叠在第三方可见面上是**纯收敛**（少一条伪造的伤害实例），符合 N15「第三方会捕获成多次判定」的修法方向。

---

## §3 路径1（数值预补偿）评估

### 3.1 需要逆运算的环节（按原版结算顺序，逐项 + 可逆性）

以 neo 1.21.1 `LivingEntity.java` / `CombatRules.java` 与本模组库 `starengine` 为准（forge 1.20.1 差异另标）：

| 层 | 公式（源码行） | 逆向可行性 |
|---|---|---|
| L0 期望值 | 目标：健康扣减 = D | — |
| L1 无敌帧闸门 | neo `hurt:1190 if ((float)this.invulnerableTime > 10.0F && !source.is(BYPASSES_COOLDOWN))` → `:1191-1194 if (amount <= this.lastHurt) { pop(); return false; }` / `:1196-1198 setReduction(INVULNERABILITY, lastHurt) + actuallyHurt(amount - lastHurt)` | **不可逆**：① 命中「`amount <= lastHurt`」直接 **0 伤害**（信息全失）；② 差额分支只结算差值 ⇒ 输出依赖上一跳历史；③ `lastHurt` 在 `:1198/@:1201-1205` 被改写。仅当 `BYPASSES_COOLDOWN`（本模组 `true_damage`/`card_spell` 已登）才整段跳过 |
| L2 头盔耐久 | neo `hurt:1178-1185` `IS_FREEZING ⇒ ×5`、`DAMAGES_HELMET ⇒ ×0.75`；forge `hurt:1147-1150`（**在 actuallyHurt 之后**，只改返回值不改扣血） | 常数可逆（但方向双线不同：neo 影响扣血、forge 不影响） |
| L3 护甲 + 韧性 | neo `getDamageAfterArmorAbsorb:1725-1734` → `CombatRules.getDamageAfterAbsorb` `:16-30`：`f = 2.0 + toughness/4`；`f1 = Mth.clamp(armor - damage/f, armor*0.2, 20)`；`f2 = f1/25`；1.21 追加 `f3 = clamp(EnchantmentHelper.modifyArmorEffectiveness(...), 0, 1)`；输出 `damage*(1-f3)` | **分段可逆但脆弱**：`f1` 含 `damage`（自指线性），纯原版可解析求根；但 ① `armor*0.2` 下限与 20 上限 ⇒ 三段夹层；② 1.21 的 `modifyArmorEffectiveness` 依赖武器附魔（含第三方 `apothic_enchanting`）⇒ 无法闭式复刻。**注意**：法伤本体 `CARD_SPELL` 已登 `bypasses_armor` ⇒ 这一层在法伤链**本就不参与**（若折叠进非 bypass 的第三方魔法源则会参与） |
| L4 抗性 | neo `getDamageAfterMagicAbsorb:1743-1758`：`i = (amplifier+1)*5`；`j = 25 - i`；`Math.max(damage * j / 25, 0)` | **部分不可逆**：`i >= 25`（抗性 V）⇒ 因子 0 ⇒ **任何输入都得 0**（且 `Math.max(...,0)` 是硬下限）；I/II/III/IV = 0.8/0.6/0.4/0.2 时可逆 |
| L5 保护附魔 | neo `:1767 float f3 = EnchantmentHelper.getDamageProtection(serverlevel, this, source)` → `CombatRules.getDamageAfterMagicAbsorb` `:32-35`：`f = clamp(enchantModifiers, 0, 20)`；`damage*(1-f/25)`（保护 IV×4 件 = 16 点 ⇒ 0.36） | 可逆（20 点封顶 ⇒ 常数 0.2 地板）；但 `getDamageProtection` 的值**会被第三方附魔/属性改写**（`apothic_attributes:prot_shred/prot_pierce` + `CombatRulesMixin`）⇒ 复刻不准 |
| L6 吸收 | neo `actuallyHurt:1790-1792`；forge `:1669-1670 f1 = max(damage - absorption, 0)` + `:1682-1684` 再扣一次 | **不可逆（死区）**：$$	ext{damage} le 	ext{absorption}$$ 时输出 0；且吸收是**有限资源**，跨事件累积 |
| L7 本模组层 | `starengine .../combat/VanillaMitigation.java:54-60`（抗性 `max((25-i)/25,0)`）、`:69-72`（保护 `clamp(0,20)`）、`:81-83`（两者相乘）、`CombatFormula.java:62-64/73-75`（`2+armor*0.30+0.85*toughness`，生物 `0.40/1.0`）、`DiceBattleResolver.java:39/46/77-80`（`MIN_DAMAGE=1.0`、`floor = max(1.0, attackPower*0.15)`） | 可逆，但 ① `Math.max(1,…)` / 15% 相对地板 ⇒ 低输入段压缩；② 与 L3/L4 的 `Math.max(0/1,…)` 叠加后出现**平坦区** |
| L8 本模组固定点数 | `ChipDamageHandler.java:74` `max(0, damage - absorption)`、`:109-118` sherry 扣到 0、`:127-134` fan `-1`、`:75-78` 气囊致命→`setNewDamage(0)`；`RenShieldManager.java:415 setNewDamage(remaining)` | **不可逆**：扣到 0 / 整段无效化 / 钳到剩余黄心，都是多对一映射 |
| L9 第三方公式改写 | `apothic_attributes`：`CombatRulesMixin`（含 `getDamageAfterAbsorb` + `getDamageAfterMagicAbsorb`）、`LivingEntityMixin`（含 `getDamageAfterMagicAbsorb`）、`PlayerMixin`（含 `hurt`）+ 属性 `armor_pierce/prot_pierce/armor_shred/prot_shred`；`goety utils.MobUtil`（引用 `getDamageAfterArmorAbsorb`/`getDamageAfterMagicAbsorb`/`CombatRules`，自建改写链）；`goety` 31 个类覆写 `getDamageAfterMagicAbsorb`、irs/goety 自研实体覆写 `actuallyHurt` | **不可逆（未知因子）**：mixin 在原名版方法内部改公式，外部拿不到系数；不同目标实体走不同 override ⇒ 「同一条公式解全局」不成立 |

### 3.2 不可逆点清单（必然导致预补偿不精确）

1. **无敌帧「`amount <= lastHurt` ⇒ 0」**（neo `:1191-1194` / forge `:1132-1134`）—— 除非强制 `BYPASSES_COOLDOWN`，否则存在整段丢失。
2. **差额结算**（`amount - lastHurt`）—— 输出依赖上一跳历史，同一输入在不同时刻结果不同。
3. **抗性封顶**（`i>=25 ⇒ 因子 0`）—— 目标叠到抗性 V 时，任何输入都只能得 0（且 `Math.max(…,0)` 不让负值回头）。
4. **吸收死区**（`max(damage - absorption, 0)`）—— 小输入全被吸收吞掉。
5. **固定点数减伤扣到 0 / 气囊整段无效化 / 黄心钳制**（本模组层）—— 多对一。
6. **第三方 mixin 改公式 + 属性（`prot_shred/armor_pierce` 等）+ 实体 override** —— 系数不可枚举，且随目标实体类型变化。
7. **`Math.max(1.0, …)` / 15% 相对地板**（`DiceBattleResolver.java:77-80`）—— 最小值语义不可逆。

> 因此「输入 X 经原版与第三方减免后恰好等于 D」**一般无解**；只能做到「按目标状态查表 + 数值近似」，误差在含抗性/高保护/吸收/黄心的目标上会放大。

### 3.3 决定性论证：路径1 与 N15 的裁决互斥

路径1 的定义是「**让第二次 hurt 的输入**经减免后 = 期望 D」⇒ 它**必须保留第二次 hurt**（这正是要消灭的东西）。
若改成单次 hurt，则「输入经减免 = 期望值」这件事就退化成「在事件里直接把金额设成期望值」，即路径0。
⇒ **路径1 不是一个独立可行的路径**，而是「保留双 hurt 前提下把数值调准」的补丁；作为达成 N15 目标的手段判 **REFUTED**。

---

## §4 第三方干扰矩阵（针对本机整合包 508 jar）

### 4.1 名称 ↔ jar ↔ modid 映射（反查依据：jar 内 `assets/<ns>/lang/zh_cn.json` + `mods.toml` 的 `[[mods]] modId`）

| 用户名称 | 认定 modid | 实物 jar |
|---|---|---|
| 神化 | `apotheosis` | `[神化] Apotheosis-1.21.1-8.9.0.jar`（内含 apotheosis / apothic_attributes / apothic_enchanting / apothic_spawners / curios / gateways / placebo）＋独立 `ApothicAttributes-1.21.1-2.11.0.jar`、`ApothicEnchanting-1.21.1-1.6.4.jar`、`ApothicSpawners-1.21.1-1.4.0.jar`、`irons_apothic-2.2.8.jar`（Iron×神化联动） |
| Iron 的法术与魔法书 | `irons_spellbooks` | `[Iron的法术与魔法书] irons_spellbooks-1.21.1-3.16.3.jar` |
| 新生魔艺 | `ars_nouveau` | `[新生魔艺] ars_nouveau-1.21.1-5.13.3.jar`（另有 ars_additions / ars_creo / ars_elemental / ars_polymorphia / ars_sable / ars_unification / arsdelight / arseng / kubejsarsnouveau / starbunclemania 等附属） |
| 诡厄巫法 | `goety` | `[诡厄巫法] goety-3.2.2.jar`（另有 goety_cataclysm / goetydelight / GoetyTwilight / GoetyIron） |
| 神秘遗物+ | `enigmaticlegacyplus` | `[神秘遗物+] enigmaticlegacyplus-1.21.1-1.1.2.jar`（⚠️ 原版 `enigmaticlegacy` **未安装**） |
| 血魔法 | `bloodmagic` | **ABSENT**（508 jar 的 `[[mods]]` 中无该 modid；文件名仅 `[森罗酒馆：血酒] kaleidoscope_bloodwine-1.1.0.jar`） |
| 咒法学 | `hexcasting` | **ABSENT**（kw=`hex` 仅命中 `crashexploitfixer-neoforge-2.0.0+1.21.4.jar`；kw=`occult` 无命中；`occultism`/`malum`/`eidolon` 亦 ABSENT） |

### 4.2 逐模组核对（① 伤害类型/标签 ② mixin 或平台事件 ③ 属性/效果）

| 模组 | ① 自定义伤害类型 + tag patch | ② mixin / 平台事件 | ③ 属性 / MobEffect（注册名） |
|---|---|---|---|
| **Apotheosis 8.9.0** | 2 类：`apotheosis:execute`、`apotheosis:psychic`；**patch 原版**：`bypasses_armor` += [execute, psychic]、`bypasses_effects` += [execute]、`bypasses_enchantments` += [execute]、`data/neoforge/tags/damage_type/is_magic` += [psychic] | mixin 23 项（`dev.shadowsoffire.apotheosis.mixin`）：`DamageSourceMixin / EntityMixin / LivingEntityInvoker / LivingEntityMixin / MHFMixinLivingEntity`；常量池 needle：**无 hurt / 无 actuallyHurt / 无 getDamageAfter***（LivingEntityMixin 命中 `[LivingEntity, setHealth]`）；事件：`AdventureEvents` 订阅 **LivingIncomingDamageEvent + LivingDamageEvent 各 1 处** | 属性在 `apothic_attributes` 模块（见下）；lang 无 `attribute.name.*` |
| **ApothicAttributes 2.11.0**（神化子模块，★关键） | 5 类：`bleeding / cold_damage / current_hp_damage / detonation / fire_damage`；**patch 原版**：`bypasses_armor` += [bleeding, detonation, fire_damage, cold_damage…]、`is_magic` += [detonation, fire_damage, cold_damage]、`is_physical` += [current_hp_damage]、自有 `cannot_critically_strike` / `is_non_physical` | mixin 8 项：**`CombatRulesMixin`（含 getDamageAfterAbsorb + getDamageAfterMagicAbsorb）**、**`LivingEntityMixin`（含 getDamageAfterMagicAbsorb）**、`EntityMixin`、**`PlayerMixin`（含 hurt）**、`IItemExtensionMixin`、`NearestAttackableTargetGoalMixin`；事件：`impl.AttributeEvents` 订阅 LivingIncomingDamageEvent + LivingDamageEvent 各 1 处 | 注册属性（字节常量实测）：`apothic_attributes:armor_pierce`、`:prot_pierce`、`:armor_shred`、`:prot_shred`、`:crit_chance`、`:crit_damage`、`:life_steal`、`:current_hp_damage`、`:overheal`、`:healing_received`、`:arrow_damage`、`:arrow_velocity`、`:experience_gained`；效果 `knowledge / vitality / grievous / flying / sundering / bleeding / detonation` |
| **irons_spellbooks 3.16.3** | 14 类（与 `SpellDamageRegistry.java:78-91` 白名单逐项吻合）：blood_cauldron, blood_magic, dragon_breath_pool, eldritch_magic, ender_magic, evocation_magic, fire_field, fire_magic, heartstop, holy_magic, ice_magic, lightning_magic, nature_magic, poison_cloud；**patch 原版**：`no_knockback` += [heartstop, blood_cauldron]、`is_magic` += 各魔法 tag；自有 bypass_evasion / long_cast_ignore | mixin 33 项（`io.redspace.ironsspellbooks.mixin`）：**`LivingEntityMixin` 常量池含 hurt / LivingEntity / DamageSource**（唯一直接挂 hurt 的）、`LivingEntityAccessor / EntityAccessor / EntityMixin`；事件：`LivingIncomingDamageEvent 9 类`（DamageSources、effect.BlightEffect、OakskinEffect、SpiderAspectEffect、TrueInvisibilityEffect、curios.BetrayerSignetRingItem、ExpulsionRing、LurkerRing…）、`LivingDamageEvent 3 类`（damage.DamageSources、effect.EchoingStrikesEffect、player.ServerPlayerEvents） | lang 无 `attribute.name.*`；`effect.*` 63 个（blood_slashed, angel_wings, heartstop, abyssal_shroud, true_invisibility, ascension, fortify, enchanted_ward, evasion, charged, rend, spider_aspect, blight, guided, airborne, vigor, instant_mana, oakskin, planar_sight, antigravity…） |
| **ars_nouveau 5.13.3** | 6 类：`ars_nouveau:spell`(msg=player)、`frost`(freeze)、`flare`(fire)、`crush`(player)、`windshear`(player)、`sourceberry_bush`；**patch 原版**：`bypasses_armor` += [crush, windshear]、`is_freezing` += [frost]、`always_hurts_ender_dragons` += 5 项、`no_knockback` += [sourceberry_bush]、`is_fall` += [windshear]、`is_fire` += [flare]、`is_magic` += [spell, frost, flare, crush, windshear] | mixin 41 项（`com.hollingsworth.arsnouveau.common.mixin`）：`DamageSourceMixin`（含 LivingEntity+DamageSource）、`LivingEntityMixin`（**无 hurt**）、`LivingAccessor / EntityAccessor / EntityMixin`、`perks.PerkLivingEntity`、`elytra.MixinLivingEntity`；事件：**LivingIncomingDamageEvent 0 类**、**LivingDamageEvent 8 类**（ArsNouveau、BubbleEntity、event.ReactiveEvents、FamiliarEvents、EventHandler、camera.CameraEvents、potions.GravityEffect、BounceEffect） | lang 无 `attribute.name.*`；`effect.*` 38 个（blasting, bounce, flight, freezing, glide, gravity, **hex**, immolate, looting, magic_find…） |
| **goety 3.2.2** | **41 类**（本模组白名单 22 项是其子集，全部命中）：acid, boiling, bubble_stream, choke, death, direct_drench, direct_freeze, direct_shock, dismissed, doom, drench, fire_breath, frost_breath, hellfire, ice_bouquet, ice_spike, indirect_*, life_leech, lightning, loot_explode(_owned), magic_bolt, magic_fire(_ball), no_owner_magic_fireball, phobia, rage, rot, shock, soul_burn, soul_leech, spike, summon, swarm, sword, venom, voided, wind_blast；**patch 原版**：`bypasses_armor` += 14 项、`bypasses_cooldown` += [hellfire, indirect_hellfire]、`bypasses_effects/enchantments/resistance` += [doom, dismissed, voided]、`bypasses_invulnerability` += [dismissed]；自有 elemental/fire/frost/shock/water_attacks 等 | 3 个 mixin 配置：`goety.mixins.json`（`com.Polarice3.Goety.mixin`，40 项，**LivingEntityMixin 常量池含 hurt**、EntityMixin）、`vivideru.mixins.json`（32 项，多渲染）、`vivideru.compat.mixins.json`（1 项）；事件：**LivingIncomingDamageEvent 11 类**、**LivingDamageEvent 5 类**（ItemEvents / LichEvents / ModEvents / PotionEvents / apotheosis compat GemCombatEffects） | **`attribute.name.goety.*` 54 个**（spell_potency, spell_duration, spell_range, spell_radius, spell_burning, spell_velocity, casting_speed, cooldown_discount, soul_discount, abyss_potency…）；`effect.*` 194 个（**sapped = "+20% damage taken per level"**、summon_down、gold_touched、illague, burn_hex, soul_hunger…） |
| **enigmaticlegacyplus 1.1.2** | 4 类：`abyss / darkness / evil_curse / nemesis_curse`；**patch 原版**：`bypasses_armor` += [nemesis_curse, evil_curse, darkness]、`bypasses_cooldown` += [evil_curse, darkness]、`bypasses_effects` += [evil_curse, abyss]、`bypasses_enchantments` += [darkness, evil_curse]、`bypasses_invulnerability` += [abyss]、`bypasses_resistance` += [darkness, evil_curse, abyss]、`bypasses_shield` += [evil_curse, abyss]、`no_impact/no_knockback` += [abyss, evil_curse]、`wither_immune_to` += [darkness]；另有 20 个 `tags/damage_type/spellstone/<石名>/{immune_to,vulnerable_to,resistant_to,is_melee}`（illusion_lantern/resistant_to 引用 4 个原版 bypasses 标签） | mixin 38 类（`auviotre.enigmatic.legacy.mixin`）：**`MixinLivingEntity`（含 hurt）**、`MixinItemStack`（含 hurt）、`MixinMob`、`MixinPlayer`、`MixinApothMenu/MixinApothScreen`（神化 compat）；事件面**最大**：**LivingIncomingDamageEvent 47 类**、**LivingDamageEvent 43 类**（LifestealAttribute$Events、BlazingMight、IchorCorrosion、PureResistance、StarlightBlessing、ViolenceCurse、EldritchAmulet$Events、TheBless$Events、TheInfinitum$Events…） | `attribute.name.*` 5 个（fortune_level, looting_level, etherium_shield, projectile_deflect, lifesteal）；`effect.*` 9 个（abyss_corruption, blazing_might, blood_chalice, ichor_corrosion, ichor_curse, molten_heart, pure_resistance, starlight_blessing, violence_curse） |
| 血魔法 / 咒法学 | 未安装 | 未安装 | 未安装 |

### 4.3 对「折叠」的结论

- 会**直接改写伤害公式**的只有 **apothic_attributes**（`CombatRulesMixin` / `LivingEntityMixin` + `armor_pierce/prot_pierce/armor_shred/prot_shred` 属性）与 **goety**（`utils.MobUtil` 自建链 + 31 个类的 `getDamageAfterMagicAbsorb` override）。这两者**同时影响「基础事件」与「加成段」**，所以折叠并不额外引入它们的差异；反而因为事件数减半，它们的每事件逻辑（暴击/穿透/减伤）少触发一次。
- 会**按「每次受击」计数**的面很大（神秘遗物+ 43+47、诡厄巫法 11+5、Iron 9+3、新生魔艺 0+8、神化 1+1）⇒ 折叠让这些第三方逻辑**少一半触发**，这既是修 N15 的收益，也意味着**平衡会随之下移**（尤其吸血/反伤/暴击类）。
- **死键/漏项（同批建议修，但不属本任务）**：`SpellDamageRegistry.java:53 ars_nouveau:generic_spell_damage` 与 `:55 ars_nouveau:cold_snap` **不存在**（实装为 `ars_nouveau:spell` / `ars_nouveau:frost`）；`:91 irons_spellbooks:cauldron` **不存在**（实装为 `irons_spellbooks:blood_cauldron`）；`apotheosis:psychic`（`is_magic` 标签成员）**不在白名单**。⇒ 现网包中「新生魔艺直伤法术」「Iron 炼药锅血伤」「神化心灵伤害」**不会被本模组识别为法伤**（与 N15 无关，但同源）。

---

## §5 推荐与代价

### 5.1 推荐：路径0（折叠），优先级 P1

| 项 | 结论 |
|---|---|
| 单次结算 | **达成**：双线各 1 处折叠 + 1 处音效取值修正；嵌套 `hurt` 与 `ThreadLocal` 闸门用途消失 |
| 期望数值 | **需要一次裁决**：加成不再吃抗性/保护（高抗高护目标最多放大 4.6×，极端 13.9×；普通目标 0 变化），并且加成**开始跟随基础源**是否 bypass 护甲（Iron 的 14 类不登 `bypasses_armor` ⇒ 这些法伤的加成会开始吃护甲） |
| 跳字不退化 | **达成**：现状本就是一条合并数字，折叠后一条且数值相同 ⇒ **HUD/跳字时序零改动** |
| 第三方适配 | **零适配**：事件数从 2 → 1，是纯收敛；无需为神化/Iron/新生魔艺/诡厄巫法/神秘遗物+ 逐个写兼容 |

### 5.2 需要改的文件（实施时）

1. `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/DamageEffectCardHandler.java`：`:82-98` 折叠；`:108` 去掉 `+ bonus`；`:31-45` 闸门保留方法体 `return false`（或删，需先确认其它两线的引用）。
2. `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/DamageEffectCardHandler.java`：`:91-104` 折叠；`:115` 去掉 `+ bonus`；`:27-42` 同上。
3. 调用点复核：`combat/DiceCombatEvents.java:1485-1488`（反噬闸门）、`event/LinglingSignEvents.java:92-94`（neo）/ forge `:106-108`（凌灵 `+5` 闸门）——折叠后闸门恒 false ⇒ **凌灵 `+5` 会开始作用于法伤合计**（行为变化，需在 CHANGELOG 级口径里登记）。
4. `bypasses_cooldown` 里 `true_damage` 条目与 `ModDamageTypes.TRUE_DAMAGE` 类型**全部保留**——除本处外还有 **4 处**独立使用者：`combat/ShootingStarManager.java:405`（破夜落星）、`combat/DiceCombatEvents.java:898`（大当家溅射）、`mixin/EntityThunderHitMixin.java:61`（雷击改造）、`item/card/HuoCardItem.java:257`（火卡自伤）。折叠只摘掉 `DamageEffectCardHandler` 这一处。

### 5.3 代价与风险（按优先级）

| # | 代价 | 说明 |
|---|---|---|
| 1 | 加成失去抗性/保护减免 | 设计口径变化：原先「加成走真伤»只绕护甲，仍吃抗性/保护；折叠后连抗性/保护也绕开（当基础源 bypass 时）或改吃护甲（当基础源不 bypass 时）。**必须由用户裁决**，可用「折叠 + 预乘 `VanillaMitigation.magicAbsorbFactor`」近似保数（但第三方 mixin 层无法复刻 ⇒ 只作近似，不建议默认开） |
| 2 | 双线残余差异 | forge 的折叠点在 `LivingDamageEvent`（**吸收之后**，`actuallyHurt:1680`），neo 在 `Pre`（**吸收之前**，`:1789`）⇒ 折叠后的加成在 forge 不吃吸收、在 neo 吃。与现状差异（第二段同样受平台事件时机影响）**并不扩大**，但 N11「true_damage 与法伤链一致」仍须另判 |
| 3 | 受击类效果整体下移 | 弱印记 `1.21 → 1.10`、雨中/水下 `1.96 → 1.40`、固定点数减伤由双扣变单扣 ⇒ 法伤在「有弱印记/有抗性」目标上的实测数值会下降，属修 bug 的必然结果 |
| 4 | 凌灵 `+5` 行为变化 | 见 5.2 第 3 条 |
| 5 | 音效阈值双算 | 若不改 `:108`（neo）/ `:115`（forge），折叠后会把 bonus 计两次 ⇒ 大音效误触发（`LIVING_PAGE_BIG_HIT_THRESHOLD = 8`） |

### 5.4 为什么不选路径1（复述关键）

- 5 类不可逆点（§3.2）+ 第三方 mixin 未知因子 ⇒ 预补偿只能近似。
- 语义上以「保留两次 hurt」为前提 ⇒ 与 N15 裁决直接冲突。
- 实施面反而更大：要改 HUD/跳字时序（审计 :310 已记），还要为每个第三方魔法模组逐个核对减免链。
- 结论：**路径0 是唯一同时满足「单次结算 + 跳字不退化」的路径；路径1 只在用户改口「保留双 hurt、只修数值」时才有意义。**

---

## §6 未验证项

1. **未起游戏、未跑构建**：本报告所有数值（0.216 / 4.6× / 13.9× / 1.4² / 1.10²）都是**按源码公式推演**，没有实测伤害数。
2. **ars_nouveau 白名单死键**：`ars_nouveau:generic_spell_damage` / `cold_snap` 在 5.13.3 里不存在（实装 `spell` / `frost`）；不能排除「本模组列表来自旧版 ars」，也未验证 ars 是否另外通过 `message_id` 或自有 tag（`is_magic`）走了本模组的其它 matcher。
3. **`irons_spellbooks:cauldron` → `blood_cauldron`** 同上，仅凭 jar 内 `data/irons_spellbooks/damage_type/` 条目名判定。
4. **`Apotheosis.MHFMixinLivingEntity`** 的具体注入目标（MHF = ？）未用 `javap -v` 逐条确认；只确认常量池含 `[LivingEntity, setHealth]`、不含 `hurt` / `actuallyHurt` / `getDamageAfter*`。
5. **`apothic_attributes.AttributeEvents` 的暴击（`crit_chance/crit_damage`）注入时机**未逐行读：若它挂在 `LivingIncomingDamageEvent`（护甲前），则现状「加成段也可能吃暴击倍率」，折叠后只吃一次 ⇒ 数值结论会再变一档。
6. **神秘遗物+ 43/47 个订阅类的具体逻辑**未逐个读（只做了类级常量扫描 + 事件类名统计），因此「哪些效果会双触发」只给量级不给清单。
7. **`isApplyingTrueBonus()` 在 fabric-1.20.1 / neoforge-26.1.2 两条线是否被引用**未查（本任务范围只覆盖两条 P0 线）；折叠方案若走「保留方法体 return false」可规避该风险。
8. **第三方 `LivingEntityMixin（irons / goety / enigmaticlegacyplus）对 `hurt` 的注入内容是 @Inject 还是 @Redirect/计数**未反编译确认；只知道它们确实命中 `hurt` 的常量池。
9. **折叠后 `victimFactor`（EnderDice HIGH）与弱印记（LOW）的先后**已按优先级推定（HIGH 先于 NORMAL 先于 LOW 先于 LOWEST），未在运行期打印验证。
10. 咒法学 / 血魔法 **未安装**的判定依据是 508 个 jar 的 `[[mods]] modId` + 文件名 + lang 名反查；若存在以别的 id 实现的同类模组（例如 `occultism` 系），不在本报告映射内。

---

## §7 落地复盘（2026-10-27 实施后回填）

1. **本报告 §2 的折叠点需要修正**：报告按「折进 `LivingDamageEvent`」推演（neo `onLivingDamagePre` / forge `onLivingDamage`），但这两个派发点都在**护甲与魔法吸收之后**（neo `LivingEntity.java:1787-1789`、forge `:1667-1680`）⇒ 折进去的加成**不吃抗性 / 保护附魔**：抗性 II + 保护 IV 放大 **4.63×**、抗性 IV + 保护 IV 放大 **13.9×**（裸目标 ×1.0 不变）。这与「加成与本体同管道」的目标语义相反。
2. **实际落点**：折叠点上移到**护甲之前** —— neo `LivingIncomingDamageEvent`（`LivingEntity.java:1153` 唯一派发点）/ forge `LivingHurtEvent`（`:1665` 唯一派发点），均 `EventPriority.HIGHEST`。乘性因子相对改造前**完全不变**（×1.0 / ×0.216 / ×0.072），「只结算一次」的收益全部保留。
3. **音效拆分**：活体书页命中音效留在原桶（`LivingDamageEvent` NORMAL）读管线值，**不再 `+ bonus`**（加成已在同一实例里）⇒ 档位与 HUD 跳字同源；对带保护/抗性的目标，新值比旧代码手算的 `getAmount()+bonus` 略低，这是「加成也被减免」的必然结果。
4. **对 §5「未验证项」的回应**：第 1 条（数值均为源码推演、未实测）**仍成立** —— 本批只做了双线编译 + javap 类面核对，未起游戏；§5 其余各条（第三方 mixin 注入形态、暴击倍率时机等）状态不变，已在 `docs/features/neo-to-forge-porting-mapping.md` §13 与本文 §5 保留。
