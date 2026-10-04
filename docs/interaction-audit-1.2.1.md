# 筹码 / 立牌 / 骰子 / 卡牌 —— 交互触发顺序与冲突审计(1.2.1,双版本)

- 审计基线:`d48a529`(分支 `multi-1.20.1-1.21.1`,工作区干净;本轮**只读**,未改任何源码/配置)
- 范围:`neoforge-1.21.1`(NeoForge 1.21.1)+ `forge-1.20.1`(Forge 1.20.1),61 个事件订阅点 / 12+11 个 Mixin
- 方法:6 个切片并行取证 + **主代理逐条复核**(每条结论标注「已核验:证实 / 修正 / 证伪 / 未核验」)
- 产出:`docs/audit-slices/S0-host-verified.md`(主代理独立发现)、`S1-damage-pipeline.md`、`S2-blessing-chain.md`、`S3-kill-event-chain.md`、`S4-resources-counters.md`、`S5-effectcard-state.md`、`S6-sign-active-dice.md`、`_VERIFICATION.md`(复核记录)
- **六个切片(S1..S6)+ 主代理独立发现(S0)已全部落盘并逐条复核**;S4/S6 的冲突项见第 3 节末尾两小节(S6-C1/C2 由主代理独立复核,结论与切片报告有修正)

---

## 0. 平台事实(理解一切顺序的地基)

| 1.21.1 NeoForge | 1.20.1 Forge | 该阶段伤害的语义 |
|---|---|---|
| `LivingIncomingDamageEvent`(可取消) | `LivingAttackEvent`(可取消) | 减伤前(原始值) |
| (无对应) | `LivingHurtEvent`(可改 amount) | **护甲前**、附魔/药水前 |
| `LivingDamageEvent.Pre`(可改 amount) | `LivingDamageEvent`(可改 amount) | **护甲+附魔之后**;1.21.1 在**吸收(黄心)之前**,1.20.1 在**吸收之后** |

证据(反编译源码,非注释):
- 1.20.1 `LivingEntity.java`:`L1665 onLivingHurt` → `L1667 getDamageAfterArmorAbsorb` → `L1668 getDamageAfterMagicAbsorb` → `L1669-1671` 扣吸收 → **`L1680 onLivingDamage`** → `L1683 setHealth`。
- 1.21.1 `LivingEntity.java`:`L1787` ARMOR reduction → `L1788 getDamageAfterMagicAbsorb` → **`L1789 onLivingDamagePre`** → `L1790-1792` ABSORPTION → `L1801 setHealth`。
- ⇒ `LivingDamageEvent.Pre` ↔ 1.20.1 `LivingDamageEvent`(**不是** `LivingHurtEvent`)。`AGENTS.md` 现有映射句写错(代码基本正确)。

**同优先级顺序无契约**:37 个类走 `@EventBusSubscriber` 自动注册,加载器以 `ModFileScanData.annotations`(`LinkedHashSet`)+ 顺序 `forEach` 注册 ⇒ 同优先级内顺序 = **jar 内 `.class` 条目顺序**。当前 1.21.1 中 `EnderDiceHandler`(索引 75)先于 `PlayerLifecycleHandler`(84)(1.20.1:85/94),即"今天是安全的",但不是契约。**已核验**。
**取消语义**:`setCanceled(true)` 后同事件后续监听器若检查 `isCanceled()` 则跳过(NeoForge `SubscribeEventListener:46-53` / Forge `ASMEventHandler:65-77`)。

---

## 1. 交互枢纽(改任何一处都会影响多处)

`DiceCombatEvents`(骰战总控:骰点/攻防/最终伤害/溅射/吸血/反击注入)、`DiceCombatModifiers`(攻/防修饰器唯一注册表 27+1)、`SpellDamageRegistry`(法伤修饰器聚合 + `onHit` 副作用)、`CardRegistry`(卡牌点数与副作用)、`ChargeManager`/`HealingManager`/`StarLightManager`/`MarkManager`(四资源池)、`EffectCardPeriod`(出牌周期状态机)、`ModAttachments`(61 键;1.20.1 63)、`AstralEventSystem` + `InvestigationEventUtil`(事件/发牌)、`HostileTargets`(敌对判定唯一入口)、`DeathPreservedBonuses`、`EventTargetCollector`。

---

## 2. 关键场景的实测触发顺序(带证据)

**A. 一次近战命中(被赐福玩家 → 目标)**
1. `LivingIncomingDamageEvent`:末影骰雨中/水下 `×1.4`(`EnderDiceHandler:134-142`,HIGH)→ 黑曜石火焰 `×0.3`(`ObsidianDiceHandler:29-37`,HIGH)→ 破绽闪避取消(`DiceCombatEvents:1088-1100`,NORMAL)
2. 原版护甲+附魔
3. `LivingDamageEvent.Pre`:骰战总控(`DiceCombatEvents:184-…`,NORMAL)— 骰点(`:364` → 护法爆发 `:391-399` → 上班族强命 6 `:402-413`)→ 攻/防修饰器 → `:604 setNewDamage(finalDmg)` → 溅射嵌套 `hurt`(`:623-673`)→ 吸血 `:676-677` → 标记/诅咒/暗影 → LOW:**狂暴** `+1×层`(`:894-901`)、**虚弱印记** `×1.10`(`:940-952`)→ LOWEST:**安全气囊**致命无效化(`ChipDamageHandler:45-66`)、**吸血鬼受击回血**(`PaparaSignItem:56`)
4. 原版扣血 → `LivingDeathEvent`(HIGHEST 气囊兜底 `ChipDamageHandler:75-85` → NORMAL:末影骰保命、死亡清理、各击杀奖励钩子)

**B. 一次击杀**:`LivingDeathEvent` 上 10 个订阅者(1 个 HIGHEST + 9 个 NORMAL,同优先级顺序无契约);奖励发放统一走 `getSource().getEntity()`,发牌走 `applyRinSignPassive` 的 `(触发者,eventId)` 2 tick 去重。
**C. 一次出牌**:专属校验 → 出牌锁 → 施加效果 → `registerPlay`(出牌数 +1,达上限才启动 30 秒冷却)→ 复制计数 → 消耗;周期归零唯一入口 `clearRoundBonuses` + `disarmAoe`(`EffectCardPeriod:325-331`)。
**D. 一次保命**:气囊(HIGHEST 取消,或 LOWEST 把伤害改 0)→ 末影骰(NORMAL 取消 + `setHealth(1.0)` + 清效果 + 5:00 冷却)。

---

## 3. 冲突项总表

> 严重度按「玩家可见的玩法后果」定;`核验` 列为主代理复核结论。A1-A18 来自 S0/S1/S2/S3/S5;S4/S6 追加在第 3 节末尾两小节。

### 高危

| ID | 核验 | 现象 | 证据(两版本皆有除非注明) |
|---|---|---|---|
| **A1 法伤加成被无敌帧削减/吞掉** | **证实** | 效果牌法伤加成以嵌套 `hurt(true_damage)` 结算,而此刻原版已写 `lastHurt=外层伤害`、`invulnerableTime=20`,且 `true_damage` **未登记 `bypasses_cooldown`** ⇒ 实落 `bonus−lastHurt`,`bonus≤lastHurt` 时**整段丢弃**(箭1点+激光+4 只落地 3) | `DamageEffectCardHandler:59-68`(A)/`62-71`(B);`LivingEntity` L1140-1142(B)/L1201-1202(A);本仓只有 `bypasses_armor.json`。**对照**:大当家溅射专门保存/清零无敌帧 `DiceCombatEvents:643-656`。现有 `SPELL-TRUE-DAMAGE` 用例只断言"重甲/无甲加成相同",抓不到 |
| **A2 骰战防御点数把攻击牌也算进去** | **证实** | 防御修饰器遍历**全部**已装卡掷骰求和,而 GUI 防御范围按 `isDefense` 过滤 ⇒ 实战/GUI 不一致;攻击侧 `:474` 同样不过滤(`CardRegistry:118-121` 注释自述"包含全部已装卡")⇒ **双向不一致** | `DiceCombatModifiers:419-431` vs 显示 `:489-493`;`CardRegistry.roll:100-107` 对攻击牌返回真实点数(非 0) |
| **A3 骰战覆盖式写入丢弃前置修饰** | **证实(可达场景收窄)** | `:604 setNewDamage(finalDmg)` 不读旧值 ⇒ 高优先级改过的伤害被丢。**可达**:末影骰雨中 `×1.4` 在被赐福玩家近战命中时失效(PvP);黑曜石火焰那条**不可达**(火焰≠近战)。**1.20.1 额外**:该 `×1.4` 走 `LivingHurtEvent` 并污染七咒 `ratio` 后被乘进骰战最终伤害 ⇒ 两版本数值不同 | `DiceCombatEvents:604`;`EnderDiceHandler:134-142`;`FateGuidanceCardItem:104-106` → `DiceCombatEvents:920-929` |
| **A4 保命取消 vs 死亡清理同优先级竞态** | **证实**(S1/S3/主代理三处独立命中) | 末影骰取消死亡与死亡清理同为 NORMAL、不同类 ⇒ 顺序由 `.class` 条目顺序决定;顺序反转时"救活了却已按死亡清理"(玻璃骰子销毁、赐福/治愈/出牌轮/立牌数据全清) | `EnderDiceHandler:146-147` / `PlayerLifecycleHandler:114-119`;气囊用 HIGHEST 取消故安全(`ChipDamageHandler:75-85`) |

### 中危

| ID | 核验 | 现象 |
|---|---|---|
| **A5 电击手套 AOE 跨版本数值不一致** | **证实** | `SpellDamageRegistry:365` 读事件伤害作 AOE 值:1.21.1 `getNewDamage()`(护甲后)vs 1.20.1 `getAmount()`(护甲前)⇒ 打重甲目标时 1.20.1 AOE 明显更高。根因:1.20.1 `DamageEffectCardHandler` 挂在 `LivingHurtEvent` |
| **A6 气囊"致命"判定基准跨版本** | **证实** | 1.21.1 在**吸收(黄心)之前**拿数值、1.20.1 在**吸收之后**(`L1669-1680`)⇒ 有黄心时 1.21.1 会把"其实不致命"的一击判为致命,白耗 6 充能 + 1:00 冷却 |
| **A7 "骰点=6"读被抬高的值** | **证实** | 护法爆发 `+星级`(`:391-399`)后可触发上班族破防(`DiceCombatModifiers:376-377`)与占星师 +6 星币(`:424`);反向:枪匠最低骰点(`:988-990`)使 `baseDice==1` 永假 ⇒ 上班族"骰出1→下次必6"(`:409-411`)无法置位 |
| **A8 1.20.1 "以毒攻毒"完全失效** | **证实(仅 1.20.1)** | `FightPoisonWithPoisonCardItem:66` 用 `getDescriptionId()`(=`effect.minecraft.poison`)去匹配 `"minecraft:"` 前缀 ⇒ 条件恒假,一个负面效果都移除不了;1.21.1 用 `getRegisteredName()` 正确 |
| **A9 未打满的一轮永不结束** | **证实** | `EffectCardPeriod.tick:317-319` 在 `cooldown<=0 && played<max` 直接 return,而周期结束只由"冷却到期"或 `registerPlay` 边界驱动,冷却只在 `count>=max` 启动 ⇒ 计数/糖果/卫星/活体书页周期态可跨任意长时间(含重登)残留,糖果"每轮一次"长期不刷新。**修法需先定夺语义** |
| **A10 末影骰保命的"清效果"对本模组无效** | **证实** | 先 `setHealth(1.0)` 再清 ⇒ `isDeadOrDying()` 失效 ⇒ `ModEffectEvents`(HIGH)按 `astral_dice:` 命名空间取消移除。**据此修正**:跨版本差异应表述为「1.20.1 清掉全部**非本模组**效果(含有益增益),1.21.1 只清**有害**效果」 |
| **A11 1.20.1 火焰/法伤挤在同一事件同一优先级** | **证实** | `ObsidianDiceHandler`(1.20.1 为 `LivingHurtEvent` NORMAL,1.21.1 为 `LivingIncomingDamageEvent` HIGH)与 `DamageEffectCardHandler` 在 1.20.1 同事件同优先级 ⇒ 相对顺序无保证(1.21.1 分处两事件,顺序确定) |
| **A12 去重不对称(发奖 vs 发牌)** | 未核验 | 大侦探 +3 星币无去重(`AstralEventSystem:34-39`),调查员发牌有 2 tick 去重;`"investigation"` 被复用于不同真实事件 ⇒ 同 tick 多杀时星币翻倍/书页被吞 |

### 低危 / 信息

| ID | 核验 | 现象 |
|---|---|---|
| A13 反击伤害同样受无敌帧差额结算 | 证实 | `injectCounterDamage` 嵌套 `hurt(dice_damage)`,而 `dice_damage` 不在任何标签 ⇒ PvP 对拼时反击被削减/吞掉 |
| A14 5 处死亡处理器缺 `isCanceled()` | 部分证实(4 处已核) | `CursedSword`/`Satellite`/`Bonnie`/`Haiqing`(+S3 指出 `InvestigationEventUtil`)⇒ 被保命的玩家/被取消的死亡仍可能推进调查阶段、发星币与命运指引 |
| A15 周期在 `registerPlay` 内结束时不解除电击手套 AoE | 未核验 | 唯一周期侧 `disarmAoe` 在 `tick:331`,`clearRoundBonuses` 无该项 ⇒ 新周期可白嫖 4 层充能 AoE |
| A16 卸牌仍得星币 / 智能手表阈值同 tick 竞争 / 星盘掉落无归属 / "全服"实为同维度 | 未核验 | 见 `S3-C5..C8` |
| A17 static 状态与关闭的闪避 | 证实 | `counterDepth`/`aoeProcessing` 非 ThreadLocal(单服务端线程假设);`PLAYER_DODGE_ENABLED=false` 使玩家侧闪避不可达 |
| A18 forge 周期 tick 每 20 tick 跑两次 | 未核验 | 未按 `TickEvent.Phase` 过滤(`PlayerTickEvents`);对状态机幂等 |

### 已核验为**非冲突**(避免误改)

- 骇客隐身攻击**不存在**时序不确定(近战走 `AttackEntityEvent`,伤害事件分支被前置守卫变为 no-op)。
- 骰战修饰器注册顺序**两版本逐条一致**(27 攻 + 1 防)。
- 多个 `+1` 出牌来源**不可能**超过封顶 9(`Math.min` + 阻塞判定 + 活体书页钳制)。
- 充能 −20% 冷却与 tooltip **同源**(同一 `ChargeManager.cooldownTicks`)。
- 15 个 `cardTypeId` 与复制计数映射**全对齐**。
- **S1-C1(子代理报的 blocker)经主代理复核为「证伪」**:大当家溅射的受害者集合按 `HostileTargets.isHostile` 过滤(`A:633-635`/`B:630-632`),而玩家既非 `Enemy` 也非 `NeutralMob` ⇒ **戴气囊的玩家永远不可能被溅射选中**,该机制不可达。**但顺带发现一处真实描述/行为差**:注释与 `isMainTarget` 逻辑声称"含主目标",而玩家作主目标时被 `isHostile` 过滤掉 ⇒ **PvP 中溅射不会打到被攻击的玩家**(仅命中周围敌对生物)。

### S4 追加(资源池 / 计数器 / 死亡清理)—— 13 条,E4 报告:`docs/audit-slices/S4-resources-counters.md`

| ID | 核验 | 现象 | 证据 |
|---|---|---|---|
| **S4-C1 保命竞态的真实后果:玻璃骰子连同卡牌销毁** | 竞态**证实**(与 A4 同一处);后果行号未复核 | 与 A4 是同一竞态,但补出玩家可见后果:顺序反转时救回的玩家仍走 `removeGlassDiceOnDeath`,把玻璃骰整格置 EMPTY(**WEAPON_ENHANCEMENT 内全部卡牌一并消失,无返还**),并清空计数/治愈 | `PlayerLifecycleHandler:126` → `DiceCurioItem:181-184`(待复核);对照气囊显式用 HIGHEST(`ChipDamageHandler:75`) |
| **S4-C2 星光 `spend` 口径冲突 ⇒ 净扣 0 仍按请求量发币** | **证实(主代理已逐行复核)** | `spend` 返回 `min(current, amount)` 作为"实际消耗",而 `set` 又把结果抬回 `base` ⇒ 佩银行卡(base 4/7)时 `current == base`,`spend` 恒返回请求量而余额不掉 ⇒ `ResourceConversion` 恒定按 `spent/2` 发星币。经商"套现"每 3 分钟白拿 ≥2 星币(ATM 再 +40%,下限 1) | `StarLightManager:52-56,66-71`;`ResourceConversion:28-44`;`BankCardChipItem:14-17` |
| S4-C6 死亡清理清单只对"死亡被取消"路径生效,且同类计数器两套待遇 | 部分证实(2 键 `copyOnDeath` 与清单位置为**证实**;清单逐项未复核) | 真实死亡只复制 `rin_pages`/`komachi_damage_bonus` 两个键,其余 59 键在新实体回默认 ⇒ `PlayerLifecycleHandler` 那份 23+4+3 清理清单**只在死亡被取消时有意义**;清单本身不完整(`piggy_bank_use_count`/`mimi_returned_card_count`/`eight_sided_roll_accum`/`satellite_play_bonus_cooldown_end`/`lulu_last_hurt_tick`/`player_starlight` 未列,`komachi_*`/`magic_tome_*` 却列入) | `PlayerLifecycleHandler:114-179`;`ModAttachments:176,280`;`AstralData:79-82` |
| S4-C3 电击手套武装跨周期不清 | 未核验 | `electric_glove_aoe` 只在 `EffectCardPeriod.tick:331` 解除;`registerPlay` 周期边界与死亡清理都不解除 ⇒ 跨周期免费扩散 | `EffectCardPeriod:277-284,331`;`PlayerLifecycleHandler:157` |
| S4-C4 活体书页本周期 +1 被边界清理抹掉 | 未核验 | `applyEffect:217` 先写 `living_page_cycle_bonus`,`registerPlay:220` 的边界清理随后抹掉 | 同左 |
| S4-C5 装备即发星光、卸下不回收 | 未核验 | ATM/银行卡-用不完/星币锤的 `onEquip` 发星光但 `onUnequip` 不回收 ⇒ 反复装卸刷星光 | 见切片文件 |
| S4-C7 `sign_active_cooldown_end` 有 7 个写入方、三套减免基准 | 未核验(与 S2-C9 同源,建议合并) | 含史莱姆写入"过去值"`max(0, cdEnd-200)` | 见切片文件 |
| S4-C8 / C13 | 未核验 | 电磁炮登记早于近战武器判定;`star_coin_hammer_bonus` 只由赐福 Expired 清,而登录/重连显式移除赐福(`PlayerLifecycleHandler:203`,`ModEffectRemoval` 不派发 Expired)⇒ 旧加成残留,下次赐福星币 ≤20 时白吃 | 见切片文件 |
| **负结论(避免误改)** | 证实 | 五个资源池的上限钳制方向、三处"先移除旧实例再写低层"的降级实现**均正确**;`HealingManager.spend` 与 `ChargeManager.clearDeathPreserved` **全仓库零调用**(`AGENTS.md` 的"登出兜底"描述与代码不符) | 见切片文件 |

### S6 追加(立牌主动 / 骰子属性 / Curios 参数绑定)—— E6 报告:`docs/audit-slices/S6-sign-active-dice.md`

**S6-C1 Curios `onUnequip` 第 2 参数被当成"被卸下的物品"**(切片报 blocker;**主代理复核:证实,且影响面与后果需修正**)

- Curios 官方签名(**两加载器缓存 jar 的 `LocalVariableTable` 均如此**):`ICurioItem.onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack)` —— 第 2 参 `newStack` = **将要占用槽位的栈**(真正卸下时为 `EMPTY`),第 3 参 `stack` 才是被卸下的饰品。
- 本仓 4 处把第 2 参当作"被卸下的物品":`BaseSignItem:165`(命名 `stack`;第 3 参 `prevStack` **完全未使用**)、`BaseChipItem:79`(命名 `curio`)、`DiceCurioItem:81`、`NetherStarDiceItem:62`。
- **修正①筹码/骰子侧无害(已核验)**:16 个 `onChipUnequip` 实现与 `tryRemoveChipBonus`(`DiceCurioItem:120-127`)全部只用 `player`,不读该栈 ⇒ 该参数传错无行为后果。
- **修正②立牌侧确有两处真实后果**:
  1. `MisakiSignItem:44` / `JasmineSignItem:51-52` / `PadmanSignItem:50-52` 的 `clearSignData` 把归零写在**传入的栈**上 ⇒ 真正卸下时写的是 `newStack`,**被卸下的立牌栈上的累计值不会被清除**(护法剑气 / 扫地机攻防 / 上班族攻防的"卸下清除"失效)。
  2. 传入 `ItemStack.EMPTY` 时 `EMPTY.set(...)` 是**对全局单例的写入**:`ItemStack.EMPTY = new ItemStack((Void)null)`(`ItemStack.java:271-274`)的 `components` 是**可变** `PatchedDataComponentMap`,`set` 直接写 `patch`(`PatchedDataComponentMap.java:66-77`)⇒ 全局 `EMPTY` 被塞入补丁项(潜在全局状态污染;`isEmpty()` 因 `this == EMPTY` 短路仍为真,故暂无显性崩坏)。
  3. `stillInSlot` 用第 2 参比对槽位内容 ⇒ **换装(A→B)时槽位恰持有 `newStack`,判定为"仍在槽位"而整段跳过清理**。
- 修法:改用第 3 参;`CurioSlotUtil.isRealUnequip:87-93` 同源同修(两版本一致)。

**S6-C2 残留 `sign_ready_expire` ⇒ 立牌主动永不冷却**(机制**证实**;可达性**未实测**)

- 冷却门槛只看单键:`if (ModAttachments.getSignReadyExpire(player) <= 0)`(`BaseSignItem:112`)——既不看 `sign_ready_type`,也不看 `gameTime < expire`。
- 三个待命立牌的"超时清除"写在**各自**的 `onCurioTick`(`HaiqingSignItem:48-57`,Bonnie/Moses 同构),`clearSignData` 也只在 `getSignReadyType(player) == 自身 READY_TYPE` 时清除(`HaiqingSignItem:86-89`)⇒ 类型不匹配的陈旧正数 `expire` 无人清理,而立牌离身后 `onCurioTick` 也不再运行。
- ⇒ 一旦留下这种残留,**该玩家任何立牌主动都不再进入冷却**(可无限连发),直到死亡(`PlayerLifecycleHandler:132-133` 清除)或重新戴上原立牌并等待超时。
- 可达路径依赖 S6-C1 的"换装跳过清理"(即 Curios 在回调前已把槽位更新为新栈,该内部顺序未实测)。**实测建议**:占星师按主动进入 30 秒待命 → 待命期内换装成另一枚立牌 → 之后连按主动键,若每次成功且不进冷却即命中。

S6 其余项(C3 属性残留 / C4 扇子奖励 / C5 待命效果残留 / 骰子属性等 9 条)未核验,见切片文件。

---

## 4. 跨版本差异清单(同一操作数值/行为不同)

1. **伤害事件阶段映射**:`LivingDamageEvent.Pre` ↔ 1.20.1 `LivingDamageEvent`(文档 `AGENTS.md` 写错)。
2. **电击手套 AOE 取值阶段**(A5)⇒ 重甲目标下 1.20.1 更高。
3. **气囊致命判定基准**(A6)⇒ 有黄心时 1.21.1 更易误判。
4. **末影骰保命清效果范围**(A10)⇒ 1.20.1 清掉全部非本模组效果(含有益增益),1.21.1 只清有害。
5. **1.20.1 火焰 -70% 与法伤并入同事件同优先级**(A11)⇒ 顺序不可预期。
6. **1.20.1 骰战 ratio 被雨中 ×1.4 污染**(A3)⇒ 同一场景两版本伤害不同。
7. **1.20.1 以毒攻毒失效**(A8)。
8. forge 周期 tick 双跑 / 创造模式消耗 API 差异(A18、S5)。
9. **S4 明确其 13 条全部"两版本皆有";S6 涉及的立牌 `onUnequip`/`clearSignData` 两版本该段**逐行同构**(仅 `CuriosApi`/`CuriosCompat` 包装不同,主代理并排复核)** ⇒ 本轮审计未新增单侧差异,跨版本清单仍以上 8 条为准。

## 5. 需你定夺 / 未能判定

**需定夺**
1. **A1 修法二选一**:给 `astral_dice:true_damage` 补 `minecraft:bypasses_cooldown`(全局生效,溅射/AOE 也会穿无敌帧)vs 只在嵌套结算处照溅射做法清零 `invulnerableTime`(局部,两版本各写一次)。
2. **A3 口径**:骰战"接管最终伤害"是设计,那雨中/水下 ×1.4 应改为注册 `DiceCombatFactor` 而不是改事件?还是让骰战叠加而非覆盖?
3. **A9 周期语义**:「出牌数未打满、玩家不再出牌」时,本周期是否应在 N 秒后自动结束?
4. **A6 气囊口径**:有黄心时"致命"应以吸收前还是吸收后为准?两版本要不要统一?
5. **A10 是否统一**:1.20.1 保命清光有益增益是否可接受(各自符合各自原版)?
6. **气囊挡 `/kill`、虚空**(无 tag 判定)vs 末影骰与原版图腾不可(`BYPASSES_INVULNERABILITY`)——是否有意?
7. **A4 修法**:死亡清理降到 `LOWEST`(推荐)还是保命提到 `HIGH`?
8. 大当家溅射是否应命中被攻击的玩家(PvP)?
9. **S4-C2**:星光兑换是"净扣 0 也照发星币"(银行卡 base 等于每 3 分钟白给)还是让 `spend` 返回真实差额?
10. **S6-C1**:参数改回第 3 参后,"换装(A→B)"也会触发立牌数据清理(目前因参数错而被跳过)——这是否即你想要的语义?
11. **S6-C2**:冷却门槛是否改为同时要求 `sign_ready_type == 0`(或 `expire <= now`)?
12. **S4-C1**:玻璃骰子死亡销毁是否应给返还(或与 A4 一并把死亡清理降到 LOWEST)?
13. **S4-C6**:死亡清理清单是补齐,还是明确"仅取消路径生效"并删掉无效项?

**未能判定(需实机/反编译补齐)**
- 同优先级真实注册顺序:建议用"玩家被末影骰保命后玻璃骰子是否消失"一条实测区分。
- `AppliedStone.cost(String)` 传 null 玩家进入 `MisakiSignItem.hasMisakiEquipped(null)` 是否 NPE(Curios 二进制未能反编译)。
- `handleEntityEvent((byte)3)` 服务端自派发是否可达(若可达,重复计数升级为必然)。
- dev(`runClient` 目录扫描)与 jar 中同优先级先后是否一致。
- S4-C1 的竞态实测(玩家被末影骰保命后玻璃骰子是否消失)——与上一条共用同一次实测即可一并区分。
- S6-C2 可达性实测:占星师待命期内换装后,立牌主动是否永不进入冷却。
- Curios 在 `onUnequip` 回调前是否已把槽位更新为 `newStack`(即"换装跳过清理"的前提;缓存 jar 未反编译到该方法体,`javap -c` 只能看到 lambda 转发)。
