# S4 资源/计数器/装卸与死亡清理

- 仓库:`F:\MCProject\astral_dice_multiloader` 分支 `multi-1.20.1-1.21.1`,基线 `d48a529`。**本次只读审计**:未改动任何源码/配置,只新增本报告文件(与 `_BASELINE.txt` 同一基线)。
- 路径缩写(S2 同款):
  - **A** = `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/`
  - **B** = `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`
- 切片范围:5 个玩家级资源池(充能/治愈/星光/标记/赋能)、计数器类附件、筹码/立牌的装卸清理、死亡清理与重生回写、跨周期清理。
- **判据只取代码**:注释与 AGENTS.md 一律不作为结论依据;凡注释与代码冲突处已单独标注(见 §5-1)。所有"跨版本"结论均逐键/逐行核对两个版本源码,不依赖注释描述。
- 外部依赖的语义也按代码核对(非注释):死亡事件先于掉落(`LivingEntity.die` 首行 `CommonHooks.onLivingDeath`)、附件克隆过滤(`AttachmentInternals.copyEntityAttachments`)、Curios 的 onEquip/onUnequip 轮询(`CuriosEventHandler.tick` 字节码)——证据见 §2 场景 4 / §5。
- 事件优先级语义:HIGHEST 最先 → HIGH → 默认 → LOW → LOWEST 最后;**同优先级 = 注册顺序(源码无法确定)**。

---

## 1. 资源池与消费方清单

### 1.1 五个资源池

| 资源 | 上限(证据) | 消费方(file:line,按实际执行顺序) | 产出方(file:line) |
|---|---|---|---|
| **充能** charge(`ChargeEffect`,amplifier+1) | 20 — A:`component/GameplayConstants.java:32` `CHARGE_MAX_STACKS = 20`;钳制在 A:`effect/ChargeEffect.java:32` `Math.min(..., getStacks(player)+stacks)` | ① 安全气囊 6 层/次 A:`item/chip/AirbagChipItem.java:51` `ChargeManager.consume(player, CHARGE_COST)`(50-52:先校验 `getStacks < CHARGE_COST` 再扣,不足则**不触发**)② 高级外设 1 层/赐福 A:`item/chip/AdvancedPeripheralsChipItem.java:51` ③ 电击手套 4 层/伤害牌 A:`item/chip/ElectricGloveChipItem.java:66-68` ④ 电磁炮 6 层/雷击 A:`item/chip/RailgunChipItem.java:150` ⑤ 电流核心 1~6 层/秒冷却 A:`item/chip/CurrentCoreChipItem.java:88-90` `for(cost) consumeOne` | 电流核心 +1/主动 A:`item/chip/CurrentCoreChipItem.java:58`;电击手套 +1/效果牌 A:`ElectricGloveChipItem.java:61`;电流剑 +2/10 杀 A:`item/chip/ElectricSwordChipItem.java:69`;永动机补足至 6 A:`item/chip/PerpetualMotionChipItem.java:32`;跃迁引擎 +2/传送 A:`item/chip/WarpEngineChipItem.java:69`;能量回收器 +1/150m A:`item/chip/EnergyRecyclerChipItem.java:120`;死亡保留回写 A:`item/ChargeManager.java:87` |
| **治愈** healing_points | 32 — A:`item/HealingManager.java:32` `HEALING_POINT_CAP = 32`(51-53 `getCap` 恒返回 32) | **无主动消费方**:A:`item/HealingManager.java:73-82` `spend(...)` **全仓库零调用**(`grep -rn "HealingManager.spend"` 仅命中定义本身);唯一下降路径 = 计时器到期减半 A:`HealingManager.java:128-129` `half = total/2`,与死亡清零 A:`HealingManager.java:85-91` | 医疗箱 赐福 +1/+3 A:`HealingManager.java:159-164`;史莱姆 受击 +1 / 主动 +3 A:`item/sign/LuluSignItem.java:58`、`:66`;缓冲盾牌 +2 A:`item/chip/BufferShieldChipItem.java:45`;友情徽章 双方 +2 A:`item/chip/FriendshipBadgeChipItem.java:65-66`;维生素药丸 +1/张 A:`item/chip/VitaminPillChipItem.java:68`;可口糖果 +1 A:`item/chip/CandyChipItem.java:37`;大碗炖肉 +1 A:`item/chip/BigBowlStewChipItem.java:64`;肉弹战车友方 +1 A:`item/sign/PandamanSignItem.java:160` |
| **星光** player_starlight | 32 — A:`component/GameplayConstants.java:15` `MAX_STARLIGHT = 32`;钳制在 A:`item/StarLightManager.java:55` `Math.max(base, Math.min(value, getCap()))` | **唯一消费方** = 星光→星币兑换 A:`resource/ResourceConversion.java:34` `StarLightManager.spend(player, coins*STARLIGHT_PER_COIN)`(调用链唯一入口 A:`item/sign/ParunanSignItem.java:43` `starlightToStarCoins(player, -1)`) | 经商被动 +1/60s A:`item/sign/ParunanSignItem.java:28`;经商赐福 `+骰点×2` A:`ParunanSignItem.java:63`;ATM 装备 +1 A:`item/chip/AtmChipItem.java:33`;银行卡-用不完 装备 +3 A:`item/chip/BankCardUnlimitedChipItem.java:40`;星币锤 装备 +5 A:`item/chip/StarCoinHammerChipItem.java:204`;手电筒 +1/新目标 A:`item/chip/FlashlightChipItem.java:63`;八面骰 每 8 累计点 +1 A:`combat/DiceCombatEvents.java:449-455`;**基础值(下限)** 银行卡 +4/+7 A:`item/StarLightManager.java:43-48` |
| **标记** MARKED(amplifier+1) | 32 — A:`component/GameplayConstants.java:17` `MAX_MARKER = 32`;钳制 A:`item/MarkManager.java:46` `Math.min(amplifier+1, MAX_MARKER-1)` | **无消费方**(只增):唯一下降路径 = 自然到期每周期 −1 层 A:`item/MarkManager.java:78-79` → 下一 tick 补写 A:`MarkManager.java:99-100` | 瞄具/鹰眼瞄具 骰战命中 A:`combat/DiceCombatModifiers.java:194`、`:199`;标靶 赐福时对最近敌对 A:`combat/DiceCombatEvents.java:329`;活体书页 法伤命中 A:`combat/SpellDamageRegistry.java:192`;标记喷罐 法伤命中 A:`SpellDamageRegistry.java:312`;魔法箭袋 触发时 A:`item/chip/MagicQuiverChipItem.java:61`;手持风扇-大 主动后 A:`item/chip/FanBigChipItem.java:48`、小 A:`FanSmallChipItem.java:34` |
| **赋能** EMPOWER(amplifier+1) | 10 — A:`effect/EmpowerEffect.java:23` `MAX_STACKS = 10`;钳制 `EmpowerEffect.java:54` | 每 0:30 递减 1 层 A:`item/EmpowerManager.java:81` `EmpowerEffect.consumeOne(player)`;卸下原初核心清空 A:`item/chip/PrimordialCoreChipItem.java:52` `EmpowerManager.removeAll(player)` | **唯一产出方** = 充能被消耗时 A:`item/ChargeManager.java:52-54` → A:`item/EmpowerManager.java:52-56`(`onChargeConsumed`,要求佩戴原初核心 A:`item/chip/PrimordialCoreChipItem.java:25-30`);上限/递减计时器 A:`EmpowerManager.java:44`(写 `empower_decay_at`) |

> 钳制方向已逐条核对,**未发现方向错误**:`ChargeEffect.addStacks`(A:`effect/ChargeEffect.java:32`,min 到 20)、`EmpowerEffect.addStacks`(A:`effect/EmpowerEffect.java:54`)、`MarkManager.apply`(A:`item/MarkManager.java:46`,上限取 `MAX_MARKER-1` 才使 `getLevel()` 最大 = 32)、`StarLightManager.set/add`(A:`item/StarLightManager.java:55`、`:60`,下限 base、上限 cap)、`HealingManager.add`(A:`item/HealingManager.java:64`)、`HealingManager.tick` 的上限收缩(A:`HealingManager.java:187-190`)。逐层递减的三处(`ChargeEffect.consume`/`consumeOne`、`EmpowerEffect.consumeOne`、`HealingManager.updateEffect`)都已按"先移除旧实例再写低层"实现(否则原版 `MobEffectInstance#update` 会吞掉降级):A:`effect/ChargeEffect.java:52-70`、A:`effect/EmpowerEffect.java:76-82`、A:`item/HealingManager.java:243-249`。

### 1.2 计数器类附件(读改写位置 + 清除时机)

| 计数器键 | 本人读改写(RMW)位置 | 周期清除 | 卸下清除 | 死亡清除 | 其它 |
|---|---|---|---|---|---|
| `magic_tome_use_count` | A:`item/chip/MagicTomeChipItem.java:31-33`(+1 并记 lastCard) | 满 3 归零 A:`MagicTomeChipItem.java:47` | A:`MagicTomeChipItem.java:66-67` | **是** A:`event/PlayerLifecycleHandler.java:159` | — |
| `magic_tome_last_card` | A:`MagicTomeChipItem.java:33` | 不清(仅作复制来源) | 不清 | 不清 | 与 count 口径不一致(见 §5-5) |
| `komachi_use_count` | A:`item/sign/KomachiSignItem.java:106-108` | 满 3 归零 A:`KomachiSignItem.java:127` | A:`KomachiSignItem.java:50` | **是** A:`PlayerLifecycleHandler.java:158` | 同类计数器在 A:`PlayerLifecycleHandler.java:155/158/159` 统一清零 |
| `komachi_damage_bonus` | A:`KomachiSignItem.java:125-126`(+1) | **不清**(设计:无上限累计) | A:`KomachiSignItem.java:51` | **否(死亡保留)** A:`component/ModAttachments.java:176` `.copyOnDeath()` | 两层保留机制,见 §2 场景 4 |
| `rin_pages` | A:`item/card/LivingPageItem.java:38`(+1) | 不清(永久累计) | A:`item/sign/RinSignItem.java:26` | **否(死亡保留)** A:`ModAttachments.java:280` | 同上 |
| `piggy_bank_use_count` | A:`item/chip/PiggyBankChipItem.java:36-41` | 满 2 归零并给 3 星币 `:37-40` | A:`PiggyBankChipItem.java:46-47` | **未列入清单**(真实死亡由克隆过滤兜底;仅"被救回的死亡"会保留) | 与 §1.2 同类计数口径不一致(见 S4-C6) |
| `mimi_returned_card_count` | A:`item/sign/MimiSignItem.java:121-127` | 满 25 归零并发随机筹码 `:122-124` | A:`MimiSignItem.java:44-47` | **未列入清单**(同上) | 同上 |
| `eight_sided_roll_accum` | A:`combat/DiceCombatEvents.java:448-454` | 每满 8 换算 1 星光 `:449-452` | A:`item/chip/EightSidedDiceChipItem.java:18-19` | **未列入清单**(同上) | 同上 |
| `magic_quiver_tracking` / `_first_card` | A:`item/chip/MagicQuiverChipItem.java:44-45` | 触发后清 tracking A:`:71` | tracking 清 A:`:78` | **是** A:`PlayerLifecycleHandler.java:135-136` | 冷却 `_cooldown_end` 死亡也清 A:`:137` |
| `flashlight_granted_targets` | A:`item/chip/FlashlightChipItem.java:56-62`(List 读写再 join) | 不清 | A:`FlashlightChipItem.java:84-85` | **是** A:`PlayerLifecycleHandler.java:142` | — |
| `nancy_lu_passive_type` / `_active_bonus` / `_until` / `_hidden_until` / `_ender_pearl_immune_until` | A:`item/sign/NancyLuSignItem.java` 各处 | 定期/到期 | — | **是(5 键全清)** A:`PlayerLifecycleHandler.java:144-148` | — |
| `fen_recharge` | A:`item/sign/FenSignItem.java:96`、`:121`、`:136`、`:157` | 赐福触发 -1 `:121` | — | **是** A:`PlayerLifecycleHandler.java:134` | — |
| `effect_card_play_count` | A:`item/card/EffectCardPeriod.java:285-286` | 周期边界 `:280`、tick `:326` | 不回收(设计) | **是** A:`PlayerLifecycleHandler.java:155` | — |
| `electric_glove_aoe`(开关,非计数) | 置位 A:`item/chip/ElectricGloveChipItem.java:69` | **仅 tick 解除** A:`EffectCardPeriod.java:331` | **不清** | **不清** | 见 S4-C3 |
| `candy_chip_play_bonus` | 置位 A:`item/chip/CandyChipItem.java:41-43` | `clearRoundBonuses` A:`EffectCardPeriod.java:184` | A:`CandyChipItem.java:46-49` | 是(经 clearRoundBonuses A:`PlayerLifecycleHandler.java:157`) | — |
| `satellite_play_bonus` / `_cooldown_end` | 置位/计时 A:`item/chip/SatelliteChipItem.java:67-70` | 标志清 A:`EffectCardPeriod.java:185` | 标志清 A:`SatelliteChipItem.java:80-84`(冷却**故意保留**) | 标志清;`satellite_give_cooldown_end` 清 A:`PlayerLifecycleHandler.java:143`,但 `satellite_play_bonus_cooldown_end` **不清** | 见 S4-C6 |
| `living_page_cycle_bonus` | A:`item/card/LivingPageItem.java:43-47`(封顶到 9) | `clearRoundBonuses` A:`EffectCardPeriod.java:186` | 不回收(设计) | 是(经 clearRoundBonuses) | 见 S4-C4 |
| `ElectricSwordChipItem.KILL_COUNTS`(静态表,非附件) | A:`item/chip/ElectricSwordChipItem.java:66-71` | 满 10 归零并 +2 充能 `:67-69` | A:`ElectricSwordChipItem.java:54-58` | **否** | 见 S4-C12 |

---

## 2. 读写与清除顺序(按场景)

### 场景 1:一次近战命中里"多个筹码都要扣充能"

执行顺序(攻击方链,**同一 `LivingDamageEvent.Pre` 实例内**;A 行号):

1. A:`combat/DiceCombatEvents.java:193` `BaseSignItem.invokeHurtHooks` + A:`:195` `BufferShieldChipItem.onHurt`(受击者侧钩子,与本场景无关但同事件内先跑)。
2. A:`DiceCombatEvents.java:207-208` `RailgunChipItem.onAttack(player, target, event.getNewDamage())` —— **只登记**延迟雷击,不扣资源(A:`item/chip/RailgunChipItem.java:105-110` 仅校验冷却与 `getStacks>=6`)。
3. A:`DiceCombatEvents.java:212` `isMeleeWeaponAttack(player)` 不通过则**整段骰战与赐福跳过**(但第 2 步已登记的雷击仍会在 1 秒后结算 → S4-C8)。
4. 赐福触发块(A:`DiceCombatEvents.java:288-351`),其中充能相关两条按**固定顺序**:
   - A:`:341` `PerpetualMotionChipItem.onBlessingStart(player)` → A:`item/chip/PerpetualMotionChipItem.java:30-32` `if (current >= 6) return; addStacks(6-current)`(**先补足**)。
   - A:`:343` `AdvancedPeripheralsChipItem.onBlessingStart(player)` → A:`item/chip/AdvancedPeripheralsChipItem.java:48-52` `ChargeManager.consume(player, 1)`(**再扣 1**)。
5. A:`DiceCombatEvents.java:466-468` 遍历攻击修饰器读**扣完之后的**层数:`DiceCombatModifiers.java:216-220` 电流剑(`getStacks/4`)、`:223-227` 高级外设(≥4 → +4)、`:405-409` 电磁炮(≥6 → +5)。
6. A:`DiceCombatEvents.java:604` `event.setNewDamage(finalDmg)` → A:`:607` `RailgunChipItem.applyFinalDamage(pending, finalDmg)` 回填雷击伤害。
7. **1 秒后**(`RailgunStrikeScheduler`):A:`item/chip/RailgunChipItem.java:141-152` `executeStrike`:A:`:146` 无目标 → 整次作废;A:`:149` 再次校验 `getStacks<6` → 作废(**不扣、不进冷却**);否则 A:`:150` 扣 6、A:`:151` 起 1:00 冷却、A:`:153-155` 逐个降雷。
8. 受击方链(同事件,**LOWEST**,最后):A:`event/ChipDamageHandler.java:56` `damage >= health && AirbagChipItem.tryNegateFatal(player)` → A:`item/chip/AirbagChipItem.java:49` 校验层数 → A:`:51` 扣 6 → A:`:52` 起 1:00 冷却 → `setNewDamage(0)`;A:`ChipDamageHandler.java:81` 的死亡兜底在 `LivingDeathEvent`(HIGHEST)再次调用同一入口,但冷却已置位 → `isOnCooldown` A:`AirbagChipItem.java:39` 直接返回 false,**不会二次扣除**。

**退化行为汇总**:

- 充能不足 6:电磁炮**整次作废**(不扣充能、不进冷却、不降雷)——A:`RailgunChipItem.java:149`;这是"先校验后扣"的正确形态。
- 同一击里"永动机补足 6 → 高级外设扣 1"使该击的电磁炮 +5(读层数在扣之后)与雷击**同时失效**(层数 5 < 6):A:`DiceCombatEvents.java:341-343` → `:466-468` → A:`RailgunChipItem.java:76`。属资源竞争导致的退化,已在 S4-C9 记为低危交互项。
- 安全气囊层数不足:直接返回 false(不消耗、不进冷却、伤害照常):A:`AirbagChipItem.java:49`。
- 电流核心层数不足:提示并返回 `FINISH_NOT_ENOUGH`(不扣、冷却不变):A:`item/chip/CurrentCoreChipItem.java:84-87`。
- 电击手套层数不足(含本次 +1 之后仍 <4):不扣、不武装 A:`ElectricGloveChipItem.java:66`。

### 场景 2:使用一张效果牌的完整读改写顺序

`BaseEffectCardItem.tryUseCard`(A:`item/card/BaseEffectCardItem.java:206-254`,两条入口 `use`/`interactLivingEntity` 共用):

1. A:`:208` 专属牌归属校验 → A:`:212` `EffectCardPeriod.isBlocked`(出牌数上限 / 冷却 / 效果待定,`EffectCardPeriod.java:254-258`)。
2. A:`:217` `applyEffect(...)` —— **先写资源**:活体书页在此 `rin_pages +1` 与 `living_page_cycle_bonus +1`(A:`item/card/LivingPageItem.java:38`、`:43-47`);命运的指引在此减主动冷却并写 `fate_active_until`(A:`item/card/FateGuidanceCardItem.java:52-57`)。
3. A:`:220` `EffectCardPeriod.registerPlay(player)` —— A:`item/card/EffectCardPeriod.java:277-284` 若命中**周期边界**则 `setEffectCardCooldownEnd(0)` + `setEffectCardPlayCount(0)` + `clearRoundBonuses(player)`(`:182-187` 清 `effect_card_bonus_plays`/`candy_chip_play_bonus`/`satellite_play_bonus`/`living_page_cycle_bonus`)→ **第 2 步刚写的本周期 +1 会被抹掉**(S4-C4)。
4. A:`:223` 可口糖果(治愈 +1、满血置本周期 +1 标志)→ A:`:226` 电击手套(充能 +1,伤害牌且 ≥4 则扣 4 并置 `electric_glove_aoe` A:`item/chip/ElectricGloveChipItem.java:61-69`)→ A:`:230` 探天卫星 → A:`:235` 扫地机减冷却 → A:`:240` 大当家 → A:`:244` 肉弹战车。
5. A:`:248-252` 四个计数器 RMW(忍者 → 魔法秘典 → 魔法箭袋 → 小猪存钱罐;各自内部"先判断在佩戴、再读改写"),顺序固定且两版本逐行一致(B:`item/card/BaseEffectCardItem.java:212,217,220,223,226,230,235,240,244,248,249,250,252`)。
6. 返回 true 后由调用方 `stack.consume(1, player)`(A:`BaseEffectCardItem.java:161`/`:182`)。

**法伤命中链的读改写顺序**(独立于骰战):`DamageEffectCardHandler` A:`event/DamageEffectCardHandler.java:47-54` 先按注册表顺序**求值 + 累加** `bonus`(第一轮),A:`:62-71` 用真伤源结算 bonus,A:`:74-76` 再按同一顺序跑 `onHit`。而 `SpellDamageRegistry` 的注册顺序是:活体书页(`:178-194`,onHit 施加标记)→ 激光/板砖/轨道炮(无 onHit)→ 定向爆破(`:232-266`)→ 忍术飞镖(`:268-283`)→ 贯穿之铳(`:285-301`)→ 标记喷罐(`:303-315`,onHit 施加标记)→ 魔法箭袋(`:317-337`,onHit 触发 `tryProc`)→ 紫晶骰 → 电击手套(`:352-386`,onHit 消耗武装并解除)。由此产生两个顺序效应:箭袋的 `isActive` 在第一轮求值、早于任何 onHit 施加标记(S4-C10);瞄具在骰战链中先施加标记,后面的 bonnie/调查阶段/忍术飞镖/全局 +1 立即吃到该层标记(S4-C11)。

### 场景 3:装卸(筹码/立牌)的清除时机

- 筹码:A:`item/chip/BaseChipItem.java:78-84` `onUnequip` → A:`item/CurioSlotUtil.java:97-104` `runOnRealUnequip` → A:`CurioSlotUtil.java:87-94` `isRealUnequip`(槽位仍为该物品则视为 Curios 重载、**不清理**)→ 子类 `onChipUnequip`。
- 立牌:A:`item/sign/BaseSignItem.java:164-179` 自带一份等价判定(`stillInSlot`),真正离开槽位才 `clearSignData`;A:`:182-183` 基类空实现,子类覆写:`KomachiSignItem.java:44-52`(use_count + damage_bonus)、`RinSignItem.java:22-27`(rin_pages)、`MimiSignItem.java:43-47`(returned_card_count)、`HaiqingSignItem.java:82-91`(中断待命)、`PandamanSignItem.java:82-87`(max_health_bonus)。
- 装备侧钩子:`AtmChipItem.java:27-34`、`BankCardUnlimitedChipItem.java:34-41`、`StarCoinHammerChipItem.java:198-205` 都在 `onEquip` 里 `StarLightManager.add`;三者的 `onChipUnequip` 只清各自的**其它**状态(`StarCoinHammerChipItem.java:208-211` 只清 `star_coin_hammer_bonus`),**不回收星光** → S4-C5。

### 场景 4:死亡 → 卸下清理 → 克隆 → 回写的完整链路

1. 死亡伤害落地 → `LivingEntity.die()`:**第一行**即派发死亡事件(NeoForge 21.1.235 反编译源 `net/minecraft/world/entity/LivingEntity.java:1408-1410`:`public void die(...) { if (CommonHooks.onLivingDeath(this, damageSource)) return; ... }`),而掉落(`dropAllDeathLoot`)在同方法 `:1430`。
2. A:`event/PlayerLifecycleHandler.java:115-179`(默认优先级):A:`:121` `ChargeManager.preserveOnDeath`(写静态表 A:`item/ChargeManager.java:71-79`)→ A:`:124` `DeathPreservedBonuses.preserveOnDeath`(写静态表 A:`component/DeathPreservedBonuses.java:39-45`,**此刻读到的仍是原值**)→ A:`:126` `DiceCurioItem.removeGlassDiceOnDeath`(A:`item/dice/DiceCurioItem.java:176-189` 把骰子槽清空)→ A:`:127` `HealingManager.clear` → A:`:129` `EffectTimerGuard.clear` → A:`:131-161` 23 个键/开关逐一复位 → A:`:157` `EffectCardPeriod.clearRoundBonuses` → A:`:163-170` 把装备中的护法立牌 `misaki_sign_stacks` 写 0 → A:`:171-178` 移除 10 个效果。
3. 掉落阶段:`LivingDropsEvent` → Curios `CuriosEventHandler.playerDrops` 把饰品移出槽位;随后 Curios 的 tick 轮询(同类内 `tick(LivingEvent$LivingTickEvent)` 的 lambda 直接调用 `ICurio.onUnequip(SlotContext, ItemStack)`)判定"已离身" → A:`BaseSignItem.java:164-179` `clearSignData` → A:`item/sign/RinSignItem.java:26` `rin_pages = 0`、A:`item/sign/KomachiSignItem.java:51` `komachi_damage_bonus = 0`。
4. 重生/克隆:`PlayerEvent.Clone`。A 侧由 NeoForge 完成复制——NeoForge 源 `net/neoforged/neoforge/attachment/AttachmentInternals.java:56-59` `onPlayerClone`(默认优先级)调用 `copyAttachmentsFrom(original, wasDeath)`,`:52-53` `isDeath ? type -> type.copyOnDeath : type -> true`;本模组只有两个键带 `.copyOnDeath()`:A:`component/ModAttachments.java:176`(komachi_damage_bonus)、A:`ModAttachments.java:280`(rin_pages)。B 侧对应 B:`component/AstralData.java:74-99`(`:79-82` `kept = {RIN_PAGES.name(), KOMACHI_DAMAGE_BONUS.name()}`;非死亡分支 `:93-96` 全量复制)。
    - **必须注意的推论(由上面的过滤式得出,不是注释)**:**真实死亡**时新生实体只继承那 2 个键,其余 59 个附件键在新实体上**一律回到默认值**——也就是说第 2 步那份长长的"死亡清理清单"对附件而言在真实死亡路径上**基本是冗余的**(清理写在旧实体上,新实体本来就是默认值),它对**附件类**键真正起作用的唯一路径是"死亡被取消"(§S4-C1 的末影骰子保命路径,不产生克隆,清理改的是同一个实体)。
    - 清单里**不冗余**的一项是把 0 写进**物品**数据组件的 `misaki_sign_stacks`(A:`PlayerLifecycleHandler.java:163-170`):物品在 `keepInventory=true` 或掉落被捡回后仍存在,该写入才真正决定"剑气是否跨死亡保留"。
5. 回写:A:`PlayerLifecycleHandler.java:184-192` 以 **LOWEST** 调 `ChargeManager.restoreAfterDeath` + `DeathPreservedBonuses.restoreAfterDeath`(A:`DeathPreservedBonuses.java:48-59` 取较大值回写、表项取走即空操作);B:`event/PlayerLifecycleHandler.java:180-188` 同构。因复制发生在默认优先级、回写在 LOWEST,**回写必然晚于复制**(代码可证,非注释)。
6. 兜底:A:`PlayerLifecycleHandler.java:210-221` / B:`:208-219` `PlayerRespawnEvent` 再调一次同样两个 restore(表项已空 → 空操作)。
7. 客户端侧(B 独有):B:`component/ModCapabilities.java:43-61` 在登录/重生/切维度发全量快照;B:`client/ClientSessionEvents.java:25-28` 断线清缓存。

### 场景 5:跨出牌周期

- 周期结束有两个入口:`registerPlay` 的边界分支(A:`EffectCardPeriod.java:277-284`)与 `tick` 的到期分支(A:`:312-332`,由 A:`event/PlayerTickEvents.java:133` 的 `tickCount % 20` 门控)。
- 两处都调 `clearRoundBonuses`(A:`:182-187`),**但只有 `tick` 解除电击手套武装**(A:`:331`),`registerPlay` 的边界分支不解除 → S4-C3(a)。
- 死亡清理(A:`PlayerLifecycleHandler.java:157`)也只调 `clearRoundBonuses`,不解除武装;不过**真实死亡**时新实体本来就不继承该键(§2 场景 4 第 4 步),故该分支只在"死亡被取消"的路径上造成残留 → S4-C3(b)。

### 场景 6:重生后

- 效果层资源(充能/赋能/治愈/青之诅咒…)随旧实体消失:NeoForge 源 `net/minecraft/server/level/ServerPlayer.java:1437-1464` `restoreFrom` 只在 `keepEverything`(末地传送门)分支复制 mob 效果(`:1449-1451`),`keepInventory=true` 的 `else if` 分支(`:1458-1464`)只换背包与经验、**不复制效果**。故充能必须靠静态表恢复(A:`item/ChargeManager.java:82-89`),赋能靠 `empower_decay_at` 在 tick 自愈(A:`item/EmpowerManager.java:62-66` 层数为 0 时把计时器归 0),治愈靠死亡清零(A:`item/HealingManager.java:85-91`)。
- `pandaman_max_health_bonus` 等"佩戴期累计值"复活后由 `onCurioTick` 每 tick 重设属性修饰器(A:`item/sign/PandamanSignItem.java:75-80` → `:186-203`),不存在"附件有值但属性没生效"的残留。

---

## 3. 冲突项

### S4-C1 | **high**(顺序依赖) | 末影骰子保命取消死亡 与 死亡清理同为默认优先级:被保命的玩家可能仍被清空计数并永久丢失玻璃骰子

- 现象:末影骰子伪图腾在**死亡事件**里 `setCanceled(true)` 救回玩家(A:`event/EnderDiceHandler.java:146-157`,`:157` `event.setCanceled(true)`),同事件上的死亡清理(A:`event/PlayerLifecycleHandler.java:114-179`)仅在**已被取消**时提前返回(A:`:119` `if (event.isCanceled()) return;`)。两者都是默认优先级,谁先执行由扫描/注册顺序决定:若清理先执行,则本次"未真正死亡"的玩家会照常走完清理——其中 A:`:126` `DiceCurioItem.removeGlassDiceOnDeath` 会把玻璃骰子整格清空(A:`item/dice/DiceCurioItem.java:181-184` `diceHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY)` + 收缩筹码栏,**无任何返还**),连同 `WEAPON_ENHANCEMENT` 里已装配的全部卡牌一起丢失;同时计数/治愈/待命状态全部清零,而 `ChargeManager.preserveOnDeath`/`DeathPreservedBonuses.preserveOnDeath` 写入的暂存项因**不会发生重生**而长期滞留。
- 证据:A:`event/PlayerLifecycleHandler.java:114-126`(`@SubscribeEvent` 无 priority)、A:`event/EnderDiceHandler.java:146-157`(`@SubscribeEvent` 无 priority)、A:`item/dice/DiceCurioItem.java:176-189`。B 侧同构:B:`event/PlayerLifecycleHandler.java:111-123`、B:`event/EnderDiceHandler.java:147-158`(对比:`ChipDamageHandler` 的死亡兜底**显式**用 HIGHEST,A:`event/ChipDamageHandler.java:75-76`,说明作者已意识到该问题但只修了气囊)。
- 建议修法:把 `PlayerLifecycleHandler.onPlayerDeathClearEffects` 降到 `EventPriority.LOW`(或 LOWEST),或把 `EnderDiceHandler.onLivingDeath` 提到 HIGH;并给"死亡暂存"加"未重生则过期/清空"的兜底。
- 两版本:**皆有**(A/B 完全同构)。无法从源码判定当前实际顺序 → §5-2 需实测。

### S4-C2 | **high** | 星光"基础值(下限)"与 `spend` 的返回值口径冲突:净扣 0 却按请求量发放星币(可无限兑换)

- 现象:`StarLightManager.spend` 用**请求量**而非**实际差额**作返回值:A:`item/StarLightManager.java:66-71` `int spent = Math.min(current, amount); set(player, current - spent); return spent;`;而 `set` 会把结果抬回基础值:A:`StarLightManager.java:52-56` `Math.max(base, Math.min(value, getCap()))`。佩戴银行卡(基础值 4/7,A:`item/chip/BankCardChipItem.java:14-17`、`StarLightManager.java:43-48`)时,`current == base` 的兑换恒为"扣到 0 → 抬回 base",星币照发:A:`resource/ResourceConversion.java:30-43`(`:32` `coins = use/2`、`:34` `spend(...)`、`:35` `gained = spent/2`、`:42` 发币)。结合 ATM:+40% 且下限 1(`:38-40`)。
- 复现式(静态推演,无需运行):星光=4(银行卡-少基础值)、`starlightToStarCoins(player,-1)` → `use=4, coins=2, spent=4, gained=2` 枚星币,函数返回后星光照旧为 4。入口是经商立牌主动(A:`item/sign/ParunanSignItem.java:37-43`),受玩家级主动冷却 180s 限制 → 每 3 分钟稳定白拿 ≥2(ATM 时 ≥2+1)星币。
- 建议修法:`spend` 返回 `current - StarLightManager.get(player)`(实际差额),或让兑换只花**高于基础值**的部分。
- 两版本:**皆有**(B:`item/StarLightManager.java:70-75`、B:`item/StarLightManager.java:56-60`、B:`resource/ResourceConversion.java:34`)。

### S4-C3 | **medium** | `electric_glove_aoe` 跨周期残留(以及"死亡被取消"路径下跨死亡残留):周期边界与死亡清理都不解除,只有 tick 与命中解除

- 现象:写入点只有 A:`item/chip/ElectricGloveChipItem.java:69`;清除点只有 A:`ElectricGloveChipItem.java:78-80`(`disarmAoe`),其调用者只有 A:`combat/SpellDamageRegistry.java:384`(命中后)与 A:`item/card/EffectCardPeriod.java:331`(**tick 的到期分支**)。`clearRoundBonuses`(A:`EffectCardPeriod.java:182-187`)不含该键;`registerPlay` 的周期边界(A:`:277-284`)不解除;死亡清理(A:`event/PlayerLifecycleHandler.java:157`)只调 `clearRoundBonuses`。
- 后果分两条,可达性不同:
  - **(a) 跨周期(两版本皆可复现,不依赖死亡)**:周期以 `registerPlay` 的边界分支结束时,武装跨到下一周期 —— 下一周期首次法伤**不重新扣 4 层充能**即触发扩散(判定入口 A:`item/chip/ElectricGloveChipItem.java:73-75` 只要求佩戴 + 标志)。
  - **(b) 跨死亡(仅"死亡被取消"路径)**:真实死亡时该键在新实体上回到默认 false(§2 场景 4 第 4 步的克隆过滤),故不残留;但末影骰子保命(S4-C1)不产生克隆,清理清单不含该键 → 救回后仍"已武装",随后首次法伤免费触发扩散。
- 建议修法:把 `ElectricGloveChipItem.disarmAoe(player)` 并入 `EffectCardPeriod.clearRoundBonuses`(一并覆盖死亡清理与周期边界),或改为在写入时记录"本轮周期标识",由读取方比对。
- 两版本:**皆有**(B:`item/chip/ElectricGloveChipItem.java:69`/`:79`、B:`item/card/EffectCardPeriod.java:183-188`、`:278-285`、`:332`)。

### S4-C4 | **medium** | 出牌周期边界窗口内使用活体书页:本周期 `living_page_cycle_bonus` 的 +1 被同一次出牌里的边界清理抹掉

- 现象与顺序:`tryUseCard` 先 `applyEffect`(A:`item/card/BaseEffectCardItem.java:217`;活体书页在 A:`item/card/LivingPageItem.java:43-47` 写 `living_page_cycle_bonus+1`)→ 再 `registerPlay`(A:`BaseEffectCardItem.java:220`),后者在边界分支里 `clearRoundBonuses`(A:`item/card/EffectCardPeriod.java:282` → `:186` 把该键归 0),随后才 `setEffectCardPlayCount(1)`(A:`:286`)。边界条件 ①`cooldown > 0 && now >= cooldown`(A:`:277-278`)在"冷却已到期但 tick 未清理"的窗口(≤20 tick,A:`event/PlayerTickEvents.java:133` 门控)内**可达**:此时 `isBlocked` 为 false(冷却已过、无待定效果,A:`EffectCardPeriod.java:207-210`、`:254-258`),出牌照常放行。
- 后果:该次活体书页的"本周期出牌数 +1"被丢弃(需再打一张才能补回);`rin_pages` 不受影响(不在 clearRoundBonuses 内)。
- 建议修法:把周期边界判定/清理挪到 `tryUseCard` 的最前面(校验之后、`applyEffect` 之前),或在 `applyEffect` 后再做一次边界判定。
- 两版本:**皆有**(B:`item/card/BaseEffectCardItem.java:217`、`:220`;B:`item/card/LivingPageItem.java:43-47`;B:`item/card/EffectCardPeriod.java:278-285`)。

### S4-C5 | **medium** | 三个"装备即得星光"筹码卸下不回收 → 反复装卸刷星光(与 S4-C2 组合成星币产线)

- 现象:`AtmChipItem.onEquip` A:`item/chip/AtmChipItem.java:27-34`(`:33` `add(player, 1)`)、`BankCardUnlimitedChipItem.onEquip` A:`item/chip/BankCardUnlimitedChipItem.java:34-41`(`:40` `+3`)、`StarCoinHammerChipItem.onEquip` A:`item/chip/StarCoinHammerChipItem.java:198-205`(`:204` `+5`)。三者的卸下钩子都**不清星光**:`StarCoinHammerChipItem.java:208-211` 只清 `star_coin_hammer_bonus`;`AtmChipItem`/`BankCardUnlimitedChipItem` 无 `onChipUnequip` 覆写。`onEquip` 里的 `if (!prevStack.isEmpty()) return;` 只挡 Curios 重载,正常"卸下→装回"会重新发星光。
- 后果:每次装卸净增 1/3/5 星光(上限 32 后停),叠加 S4-C2 的兑换即可把"星币"变成可反复再生的资源;星币锤还额外可用"装备→主动套现"循环。
- 建议修法:按装备会话记账(记录本次装备发放额,卸下时扣回),或给 `onEquip` 发放加"每 N 秒一次"的冷却,或把这三处改为"佩戴期间持续提供基础值"(与银行卡基础值同口径)。
- 两版本:**皆有**(B:`item/chip/AtmChipItem.java:34`、`BankCardUnlimitedChipItem.java:41`、`StarCoinHammerChipItem.java:205`/`:210-211`)。

### S4-C6 | **medium** | "死亡清理清单"只对"死亡被取消"路径真正生效,且该清单本身不完整 + 同类计数器口径不一致

- 机制前提(见 §2 场景 4 第 4 步):真实死亡时新生实体只继承 2 个 `copyOnDeath` 键,其余附件一律回默认值。因此清单(甲 23 键 A:`event/PlayerLifecycleHandler.java:131-161` + 乙 4 键 A:`item/card/EffectCardPeriod.java:183-186` + 丙 3 键 A:`item/HealingManager.java:87-89`)的实际作用域 = **被救回的死亡**(末影骰子/气囊取消死亡,同一实体,无克隆)。
- 现象:在该路径上,`piggy_bank_use_count`、`mimi_returned_card_count`、`eight_sided_roll_accum`、`satellite_play_bonus_cooldown_end`、`lulu_last_hurt_tick`、`player_starlight` 等**不会**被复位,而 `komachi_use_count`、`magic_tome_use_count` 会被复位;`satellite_play_bonus`(标志)被 `clearRoundBonuses` 清掉而它的 `_cooldown_end` 不被清 → 救回后最长 1:00 内无法再触发"轨道炮出牌数 +1"。同类计数器在同一事件里两套待遇。
- 建议修法:把"死亡清零清单"与"周期清零清单"收敛到单一注册表(每个键声明 deathClear 标志),避免逐项罗列漏项;并明确 `player_starlight`/`eight_sided_roll_accum` 是否应跨"被救回的死亡"保留。若认为真实死亡路径的清单完全冗余,可整块删除并在"死亡被取消/重生"两个明确边界上各清一次。
- 两版本:**皆有**(B:`event/PlayerLifecycleHandler.java:128-156` 逐键与 A 相同——已用归一化逐行 diff 验证,仅差 `ItemDataKey`/`ItemStack#set` 的 API 形态)。

### S4-C7 | **medium** | `sign_active_cooldown_end` 单键 7 个写入方、三套减免基准,且史莱姆会写入"已过去/0"的值

- 写入方与基准:
  1. 立牌主动释放 A:`item/sign/BaseSignItem.java:112-118` = `now + WeirdDiceHandler.signCooldownTicks(player)`(基准随诡异骰子减半);
  2. 占星师/秘密侦探/枪匠的待命释放 A:`combat/DiceCombatEvents.java:238-239`、`:259-260`、`:273-274`(同一 helper);
  3. 命运的指引 A:`item/card/FateGuidanceCardItem.java:62-71` = `now + max(0, remaining − maxCooldown/2)`,基准取自 `WeirdDiceHandler.signCooldownTicks`(受诡异骰子影响);
  4. 扫地机 A:`item/sign/JasmineSignItem.java:83-92` = `max(now, cdEnd − SIGN_ACTIVE_COOLDOWN_TICKS/2)`,基准是**固定常量 180s**(不受诡异骰子影响);
  5. 史莱姆 A:`item/sign/LuluSignItem.java:52-56` = `max(0, cdEnd − 200)`,基准固定 200t,**且先用 `cdEnd > 0` 而非 `cdEnd > now` 判定** → 冷却已过期(残留过去时刻)时会把值写成 0/过去值;
  6. 电流核心 A:`item/chip/CurrentCoreChipItem.java:92` `setSignActiveCooldownEnd(player, now)`(立即完成);
  7. 忍者被动 A:`item/sign/KomachiSignItem.java:132-141` = `now + remaining*0.7`。
- 后果:多来源 RMW 都落在服务端单线程,不存在数据竞争;但**基准不一致**导致"诡异骰子 + 扫地机"下 90s 常量减免可一次抹平整段冷却,而史莱姆的 `max(0,…)` 与其余各处的 `max(now,…)` 口径不同(会留下非 0 的过去时刻)。同一玩家同时具备多个减冷却来源时,减免量叠加无上限(指数式 0.7^n)。
- 建议修法:抽一个 `SignCooldown.reduce(player, ticks)` / `SignCooldown.finish(player)` 单入口,内部统一把结果钳到 `max(now, …)` 并统一基准(`WeirdDiceHandler.signCooldownTicks`)。与 S2-C9 同源,建议合并处理。
- 两版本:**皆有**(B:`item/sign/BaseSignItem.java:113-118`、`item/card/FateGuidanceCardItem.java:68`、`item/sign/JasmineSignItem.java:90`、`item/sign/LuluSignItem.java:54`、`item/chip/CurrentCoreChipItem.java:92`、`item/sign/KomachiSignItem.java:139`、`combat/DiceCombatEvents.java:~236/257/271`)。

### S4-C8 | **low** | 电磁炮登记早于"近战武器"判定:空手/非近战武器的攻击也会扣 6 层充能并起 1:00 冷却

- 现象:A:`combat/DiceCombatEvents.java:207-208` 先 `RailgunChipItem.onAttack(...)`,A:`:212` 才 `isMeleeWeaponAttack(player)` 并可能 `return`。因此主手为空/盾牌/非剑斧锤三叉戟时(如空手攻击、锄头),骰战与赐福被跳过,但已登记的雷击仍会在 1 秒后 A:`item/chip/RailgunChipItem.java:141-152` 结算:只要 3 格内有可命中敌对目标就扣 6 充能、起 1:00 冷却;伤害用的是 `applyFinalDamage` 未回填时的兜底值(A:`RailgunChipItem.java:110` 传入的 `immediateDamage`)。
- 证据:A:`combat/DiceCombatEvents.java:200-212`;`:604-607`(回填只在通过近战判定后才可达)。
- 建议修法:若口径是"仅近战武器攻击触发",把 `onAttack` 移到 `isMeleeWeaponAttack` 之后;若是"任意玩家实体攻击皆可",请在 tooltip/手册明确。
- 两版本:**皆有**(B:`combat/DiceCombatEvents.java:197-208`、B:`item/chip/RailgunChipItem.java:99-111`)。

### S4-C9 | **low** | 同一击内的充能"多消费者"顺序:永动机补足 → 高级外设扣 1,导致该击电磁炮 +5 与雷击双双失效

- 现象:A:`combat/DiceCombatEvents.java:341`(永动机补足到 6)→ A:`:343`(高级外设扣 1,`AdvancedPeripheralsChipItem.java:51`)→ A:`:466-468` 修饰器读层数(A:`combat/DiceCombatModifiers.java:405-409` 电磁炮 ≥6 才 +5;`:223-227` 高级外设 ≥4 才 +4)→ 1 秒后 A:`item/chip/RailgunChipItem.java:149` 再校验 <6 → 雷击整次作废。
- 后果:充能恰为 6(或 5,被永动机补到 6)时,该击**既拿不到电磁炮 +5,也拿不到雷击**,且不产生冷却惩罚;不是"漏扣"而是"竞争退化"。高级外设自身 +4 仍生效(扣后 5 ≥ 4)。
- 建议修法:把高级外设的扣层挪到攻击加成聚合之后(或在扣层前缓存本击的充能快照给修饰器读取)。
- 两版本:**皆有**(B:`combat/DiceCombatEvents.java:338`/`:340`、B:`combat/DiceCombatModifiers.java:408`、B:`item/chip/RailgunChipItem.java:149`)。

### S4-C10 | **low** | 魔法箭袋的生效判定早于 onHit 施加标记:同一击内刚拿到的标记不生效,箭袋自身却又 +1 层

- 现象:A:`event/DamageEffectCardHandler.java:47-54` 先聚合 `isActive`+`apply`(含箭袋 `isActive`,A:`combat/SpellDamageRegistry.java:319-326` 要求 `MarkManager.getLevel(target) > 0`),A:`:74-76` 才按同一顺序跑 `onHit`。而同一击里"活体书页"(`SpellDamageRegistry.java:191-193`)与"标记喷罐"(`:309-314`)的标记是在 onHit 阶段施加的,箭袋的 onHit(`:333-336` → A:`item/chip/MagicQuiverChipItem.java:53-73`,其中 `:61` 又 `MarkManager.apply`)排在其后。
- 后果:目标原本 0 层标记时,本击即使由活体书页/喷罐施加了标记,箭袋也**不会**当次触发(需下一击);反之目标已有标记时,箭袋会再叠 1 层并返还第一张牌。
- 建议修法:把箭袋的 `isActive` 判定下移到 `onHit` 阶段(或把 onHit 的标记施加提前到聚合前)。
- 两版本:**皆有**(B:`event/DamageEffectCardHandler.java` 同构;B:`combat/SpellDamageRegistry.java:192/312/325/335/384` 行号一致)。

### S4-C11 | **low** | 骰战内"标记"顺序效应:瞄具先施加标记,同击的 bonnie/调查阶段/忍术飞镖与全局 +1 立即吃加成

- 现象:攻击修饰器注册顺序 A:`combat/DiceCombatModifiers.java:190-202`(瞄具 `MarkManager.apply`,鹰眼先读层数再 apply)早于 `:329-336`(bonnie 读层数 +3)、`:348-365`(调查阶段读层数)、`:405-409` 等;最终 A:`combat/DiceCombatEvents.java:584-586` `if (getLevel(target) > 0) finalDmg += 1` 也读同一击刚施加的标记。
- 后果:目标"首次被命中"即享受标记加成(+1 伤害 / bonnie +3 / 调查阶段 +markLevel),等价于标记在命中判定的**同击内即时生效**;鹰眼瞄具自身的 `markLevel*2` 则读的是**加标记之前**的层数(第一击为 0)。
- 建议修法:确认口径(是否接受"同击即时生效");若要与 tooltip 文案严格一致,建议统一为"命中前已存在的标记层数"或"命中后层数"其中之一。
- 两版本:**皆有**(B:`combat/DiceCombatModifiers.java:197-202`、`:335`、`:355`;B:`combat/DiceCombatEvents.java:~581-583`)。

### S4-C12 | **low** | `ElectricSwordChipItem.KILL_COUNTS` 静态击杀计数只在卸下时清除(死亡/登出不清)
- 现象:A:`item/chip/ElectricSwordChipItem.java:35` `private static final Map<UUID,Integer> KILL_COUNTS`;只在 A:`:54-58` `onChipUnequip` 移除;死亡清理(A:`event/PlayerLifecycleHandler.java:131-161`)不含它,登出也没有清理钩子。
- 后果:① 它是**静态 Java 表而非附件**,因此不受克隆过滤约束:真实死亡/重生/切维度后进度全部保留(同类计数 `komachi_use_count`/`magic_tome_use_count` 在死亡路径上归零 → 口径不一致,见 S4-C6);② 单机跨世界/跨存档时表项残留,永不卸载的 UUID 条目长期驻留(每玩家 1 条,量级小)。
- 建议修法:纳入死亡清零清单,并在玩家登出/换世界时清表(与 `JasmineSignItem` 的静态表做法对齐:A:`item/sign/JasmineSignItem.java:48-55` 在 `clearSignData` 里移除 `lastPosMap`/`walkAccumMap`)。
- 两版本:**皆有**(B:`item/chip/ElectricSwordChipItem.java:35`、`:56`、`:66-71`)。

### S4-C13 | **low** | `star_coin_hammer_bonus` 依赖 `MobEffectEvent.Expired` 清除:赐福被**显式移除**(登录/重连路径)时不清,下一次赐福在星币不足 21 枚时白吃旧加成

- 现象:该加成的清除**唯一**入口是 `onBlessingEnd`(A:`item/chip/StarCoinHammerChipItem.java:193-196`),其唯一调用点在赐福**自然到期**的 `MobEffectEvent.Expired` 里(A:`combat/DiceCombatEvents.java:826-840`,其中 `:840` 调 `StarCoinHammerChipItem.onBlessingEnd`)。而赐福还有两条**显式移除**路径:死亡 A:`event/PlayerLifecycleHandler.java:171`、登录/重连 A:`PlayerLifecycleHandler.java:203`(`ModEffectRemoval.remove(player, ModEffects.DICE_BLESSING)`)。`ModEffectRemoval.remove` 走原版 `player.removeEffect(effect)`(A:`event/ModEffectRemoval.java:27-35`),**不派发 `Expired`**(原版只在 `tickEffects` 的时长归零分支发 Expired),因此不会触发该清除。
- 后果:登录路径不清(`star_coin_hammer_bonus` 的清理写在**死亡**方法 A:`PlayerLifecycleHandler.java:139`,登录方法不调用它),旧值随玩家 NBT 保留;下一次赐福的 `onBlessingStart` 在"持有星币 ≤ 20"时**提前返回**(A:`item/chip/StarCoinHammerChipItem.java:184` `if (total <= THRESHOLD_COINS) return;`)——既不扣币也不改写旧值,于是旧加成在整个新赐福期间继续计入攻击力(A:`combat/DiceCombatModifiers.java:278-283`;骰战链本身只在有赐福时运行,A:`combat/DiceCombatEvents.java:354`)。玩家可把星币花到 ≤20,再以"重连后开打"白嫖上一轮 30% 加成的绝对值。
- 建议修法:① 登录/重连清理补上 `StarCoinHammerChipItem.onBlessingEnd(player)`(与死亡清理同口径);② 或把 `onBlessingStart` 的提前返回改为"先把加成置 0 再判断门槛";③ 更稳妥:改为由"当前是否有赐福效果"实时判定,不落附件。
- 两版本:**皆有**(B:`item/chip/StarCoinHammerChipItem.java:184`、`:193-196`;B:`event/PlayerLifecycleHandler.java:167` 死亡移除赐福、`:200` 登录清理移除赐福——对照可见登录分支无星币锤清理)。

---

## 4. 跨版本对等性差异(附件/键集合)

逐键对照方法:脚本解析两个 `ModAttachments.java` 的注册名与 `.sync()`/`.sync`、`.serialize()`/`.inMemory()`、`.copyOnDeath()` 标志后取差集,再逐条人工回读代码确认。

| 对照项 | 1.21.1(A) | 1.20.1(B) | 差集结论 |
|---|---|---|---|
| 附件键总数 | **61**(A:`component/ModAttachments.java:23-888`) | **63**(B:`component/ModAttachments.java:31-781`) | B 多 2 个:`damage_effect_bonus`(B:`ModAttachments.java:39-40`)、`curse_original_amount`(B:`ModAttachments.java:243-244`) |
| 同步(synced)键 | **27** | **28**(B:`ModAttachments.java:820-852` `SYNCED_KEYS`) | 差集**只有 B 的 `damage_effect_bonus`(B:`:823`)**;其余 27 个两版本完全一致 → 客户端可见值不会因版本不同而缺失 |
| 死键核查 | — | `damage_effect_bonus` 的 get/set(B:`ModAttachments.java:364-369`)除定义处外**全仓库零调用**;`curse_original_amount`(B:`:244-251`)同样零调用 | 属"仅 1.20.1 存在的死键",不产生行为差异 |
| 持久化(in-memory)集合 | 无 `.serialize(...)` 的仅 `dice_curse_ratio`(A:`ModAttachments.java:263-265`) | `.inMemory()` 的为 `dice_curse_ratio`(B:`:231`)、`curse_original_amount`(B:`:244`) | 共有部分一致 |
| 死亡复制键(`copyOnDeath` / clone kept) | **2 个**:`komachi_damage_bonus`(A:`ModAttachments.java:176`)、`rin_pages`(A:`ModAttachments.java:280`) | **2 个**:B:`component/AstralData.java:79-82` `kept = {RIN_PAGES.name(), KOMACHI_DAMAGE_BONUS.name()}` | **键集合逐字一致** |
| 死亡清理键 | 23(甲)+4(乙)+3(丙),写在 `LivingDeathEvent` 的旧实体上 | 23+4+3,同结构 | 逐键一致(归一化逐行 diff 仅剩 `ItemDataKey.set`/`ItemStack#set` 的 API 差异)。注意:真实死亡时新实体只继承 2 个 `copyOnDeath` 键,清单对附件类键实际只在"死亡被取消"路径生效 |
| 死亡暂存表 | A:`component/DeathPreservedBonuses.java:33-59` | B:`component/DeathPreservedBonuses.java:33-59` | **代码逐行一致**(归一化后 0 差异,仅类注释不同) |
| 资源管理器 | A:`item/{ChargeManager,HealingManager,StarLightManager,MarkManager,EmpowerManager}.java` | B 同名文件 | `HealingManager` 归一化后**仅差一行 import**(⇒ 逻辑等价);`ChargeManager` 逐行一致;`StarLightManager`/`MarkManager` 仅差 `.get()` 与 `CuriosCompat`/`TickEvent.Phase.END`;`EmpowerManager` 调用点逐行一致 |
| 事件注册形态差异 | `LivingDamageEvent.Pre` / `LivingIncomingDamageEvent` / `ServerTickEvent.Post` | `LivingDamageEvent` / `LivingAttackEvent` / `TickEvent.ServerTickEvent(Phase.END)` | 平台差异(非本切片结论),但 S4 相关调用点在两版本行号/顺序一致(已核 `DiceCombatEvents` 的 railgun/永动机/高级外设/healing/八面骰/回填/手电筒 七个点) |
| 未使用/残留代码 | `ChargeManager.clearDeathPreserved`(A:`item/ChargeManager.java:92-96`)**零调用** | 同(B:`item/ChargeManager.java:92-96`) | 两版本一致;AGENTS.md 所述"登出/异常路径兜底"在代码中并不存在(注释/文档性描述,**已按代码判定**) |
| 出牌周期/RMW 结构 | `EffectCardPeriod` / `BaseEffectCardItem` | B 同构 | 归一化后仅 API 差异;`registerPlay` 边界不清 `electric_glove_aoe`、`tick` 才解除——两版本**完全一致**(故 S4-C3/C4 均为双版本问题) |

> 结论:**本切片未发现"仅一个版本存在"的资源/计数器/死亡清理行为差异**。所有 S4-C1…S4-C13 在两版本同时成立(每条均已给出 A/B 两侧 file:line)。跨版本仅存的差异是 1.20.1 的两个死键(S4 无影响)与平台 API 形态。

---

## 5. 未能判定 / 需人工确认

1. **注释与代码不一致(全部以代码为准,已按代码出结论)**:① `ModAttachments.java:98-100`(A)描述"治愈体系已无独立计时器 / 赐福结束时减半",但代码里 `HEALING_TIMER_END` 仍在用且减半由 `onTimerEnded`(A:`item/HealingManager.java:125-136`)执行;② `ChargeManager.clearDeathPreserved` 注释称"登出/异常路径兜底",但全仓库无调用者;③ `DeathPreservedBonuses` 与 AGENTS.md 描述的两层机制**方向正确**(已用 NeoForge 源码 `AttachmentInternals.java:52-59`、`LivingEntity.java:1408-1430` 证实),但其"Curios 的 onUnequip 早于克隆"这一点只能靠"掉落发生在 `die()` 内、克隆发生在重生时"间接成立(见下条)。
2. **Curios 回调的精确时序未能完全证实**:`CuriosEventHandler` 只有二进制 jar(工作区 `curios-IPQlZkz1.jar`,无 sources)。已用 `javap` 证实其 `tick(LivingEvent$LivingTickEvent)` 内的合成方法会调用 `ICurio.onEquip` / `ICurio.onUnequip`(退化为 lambda 的 `invokeinterface ... ICurio.onUnequip`),且掉落走 `playerDrops(LivingDropsEvent)`;但"掉落 → 轮询 → onUnequip → clearSignData"相对 `PlayerEvent.Clone` 的具体 tick 差没有运行时证据。**这不影响 §2 场景 4 的结论**(回写在 LOWEST、必然晚于复制,已由源码证实),但影响"两层机制是否都必要"的因果强弱判断。建议实测点:`keepInventory=false` 世界死一次后检查 `rin_pages`/`komachi_damage_bonus` 是否为 0,以及是否被回写回来。
3. **S4-C1 的实际触发与否取决于同优先级注册顺序**(本次无法从源码确定,NeoForge 的类扫描/注册顺序不由源码文本决定)。需实测:佩戴末影骰子 + 玻璃骰子,受一次致命伤(末影骰子触发),检查玻璃骰子与已装卡牌是否还在;两版本各测一次。
4. **S4-C7 的"累积减免"上界与平衡口径**属产品裁决:当前代码允许命运指引(÷2)、扫地机(固定 90s)、史莱姆(−10s/次受击)、忍者(×0.7/3 张牌)、电流核心(花充能直接清零)叠加,无总上限;是不是设计意图需确认。
5. **`magic_tome_last_card` / `komachi_last_card` / `magic_quiver_first_card` 的清除口径**:三者在卸下时都不清(只清计数/追踪),下一次满阈值触发时会"复制上一轮最后一张牌"(读回附件,如 A:`item/chip/MagicTomeChipItem.java:37`、A:`item/sign/KomachiSignItem.java:112`)。这是否会造成"跨装卸复制到旧牌",需按设计口径确认(本次不判定为缺陷)。
6. **赐福被"本模组之外的途径"显式移除时的挂账残留(与 S4-C13 同源,仅此处未穷举)**:本切片已确认死亡路径(A:`event/PlayerLifecycleHandler.java:171`,由 A:`:139` 清星币锤加成兜底)与登录路径(A:`PlayerLifecycleHandler.java:203`,**无**清理由 S4-C13 记录);其余可能移除赐福的外部来源(指令 `/effect clear`、其它模组/联动)是否被 `ModEffectEvents` 的移除拦截挡住(A:`event/ModEffectRemoval.java:10-13` 提到拦截器)本次**未逐条穷举**;若存在绕过拦截的路径,`star_coin_hammer_bonus`/`defense_card_consumed_blessing`/`cursed_sword_blessing_triggered` 三个"赐福周期标志"都会以同样方式残留。建议统一改为"由赐福效果存在与否实时判定"。
7. **`empower_decay_at` 未列入死亡清理清单是否构成缺陷——已核对,判定为"无实际影响"(不列为冲突项)**:真实死亡时该键在新实体上回默认 0(克隆过滤),旧实体的残留值不再被读取;末影骰子保命路径下**没有真正死亡**,`EMPOWER` 效果与计时器都保持有效(NeoForge `ServerPlayer.restoreFrom` 未被调用),两者一致。因此 `EmpowerManager.tick` 的"层数为 0 却计时器非 0"自愈分支(A:`item/EmpowerManager.java:62-66`)只是防御性代码。若将来把附件改成"随死亡复制",此键需要一并纳入清单。
