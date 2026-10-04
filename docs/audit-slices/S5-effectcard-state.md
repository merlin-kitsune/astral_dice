# S5 效果牌状态机与叠层

> 基线提交 `d48a529ba5fcf14610dc7b7383a10780dbe49168`(分支 `multi-1.20.1-1.21.1`,工作区干净)。
> 版本:`neoforge-1.21.1`(NeoForge 1.21.1 / Java 21)与 `forge-1.20.1`(Forge 1.20.1 / Java 17)。
> 所有结论以**本仓 Java 源码 + moddev 反编译源码**为准,不依据 AGENTS.md 或代码注释下结论;
> 注释仅作为"实现者声称"被引用时明确标注。行号 = 本次读取的工作区行号。
> 只读审计:本文件是本次唯一的产出,未修改任何源码/配置。

本切片范围:出牌周期(effect card period)、出牌数(+1 来源)、冷却、复制计数、叠层上限、出牌守卫。

---

## 1. 参与方与入口清单

### 1.1 出牌数 +1 的来源(全部来源;`getMaxAllowed` 的三个加数)

`getMaxAllowed` 的全部加成项只有四处:固定来源表、临时来源表、活体书页本周期累计、出牌轮一次性追加。

```java
// neoforge-1.21.1/.../item/card/EffectCardPeriod.java:134-151(forge:135-152 同构)
public static int getMaxAllowed(Player player) {
    int extra = 0;
    for (ExtraPlaySource source : FIXED_SOURCES) { if (source.isActive(player)) extra += source.amount(); }
    for (ExtraPlaySource source : TEMPORARY_SOURCES) { if (source.isActive(player)) extra += source.amount(); }
    extra += ModAttachments.getLivingPageCycleBonus(player);      // 活体书页累计
    extra += getBonusPlays(player);                              // 立牌主动一次性
    if (extra < 0) extra = 0;
    return Math.min(GameplayConstants.MAX_EFFECT_CARD_PLAYS, 1 + extra);   // 封顶 9
}
```

| # | 来源 | 类型 | 登记入口(file:line) | 清除入口(file:line) |
|---|---|---|---|---|
| 1 | 基础出牌数 1 | 常量基准 | `EffectCardPeriod.java:134-151`(neo)/`:135-152`(forge) 的 `1 + extra`;常量 `component/GameplayConstants.java:28`(两版本同)= 9 封顶 | 无(常量) |
| 2 | 大背包筹码 | 固定 | 注册 `EffectCardPeriod.java:109`(neo)/`:110`(forge):`registerFixedSource(p -> hasCurio(p, ModItems.BIG_BACKPACK_CHIP.get()))`;谓词 `:334-337`(neo)/`:335-338`(forge) | 无写入项;实时计算,卸下即刻不再计入(无"清除"代码,也无残留风险) |
| 3 | 忍术飞镖筹码 | 固定 | 注册 `EffectCardPeriod.java:110`(neo)/`:111`(forge) | 同上 |
| 4 | 命运的指引(效果存在即 +1,覆盖式) | 临时 | 注册 `EffectCardPeriod.java:115`(neo)/`:116`(forge):`p.hasEffect(ModEffects.FATE_GUIDANCE)`;实际写入 `FateGuidanceCardItem.applyEffect` `:54-55`(neo)/`:53-54`(forge)(`FATE_ACTIVE_UNTIL` + 效果 6000t) | 效果自然到期;死亡移除效果 `PlayerLifecycleHandler.java:175`(neo)/`:171`(forge)。**注意:`FATE_ACTIVE_UNTIL` 不在 `clearRoundBonuses`(`EffectCardPeriod.java:182-187` neoforge / `:183-188` forge)内,与出牌数无关(出牌数只看效果)** |
| 5 | 可口糖果(满血时使用效果牌触发,每轮一次) | 临时 | 注册 `EffectCardPeriod.java:116`(neo)/`:117`(forge);写入 `chip/CandyChipItem.java:41-43`(neo)/`:42-43`(forge);附件定义 `component/ModAttachments.java:544`(neo)/`:487`(forge) | `clearRoundBonuses` `EffectCardPeriod.java:184`(neo)/`:185`(forge);卸下筹码 `CandyChipItem.java:46-49`(neo)/`:47-50`(forge) |
| 6 | 探天卫星(使用轨道炮后,每 1:00 一次) | 临时 | 注册 `EffectCardPeriod.java:117`(neo)/`:118`(forge);写入 `chip/SatelliteChipItem.java:63-71`(neo)/`:64-72`(forge);附件 `ModAttachments.java:558`(neo)/`:496`(forge) | `clearRoundBonuses` `EffectCardPeriod.java:185`(neo)/`:186`(forge);卸下筹码 `SatelliteChipItem.java:80-84`(neo)/`:81-85`(forge);触发闸门 `satellite_play_bonus_cooldown_end` 独立计时 1200t(`ModAttachments.java:565` neo / `:500` forge,`SatelliteChipItem.java:33`) |
| 7 | 立牌主动「忍术连击」一次性 +1 | 一次性 | `EffectCardPeriod.grantBonusPlay` `:170-174`(neo)/`:171-175`(forge);**全仓唯一调用点** `item/sign/KomachiSignItem.java:80`(两版本同行);附件 `ModAttachments.java:45-49`(neo)/`:49-50`(forge) | `clearRoundBonuses` `EffectCardPeriod.java:183`(neo)/`:184`(forge)。**故意不随立牌装卸回收**(`KomachiSignItem.clearSignData` `:44-52`(neo)/`:45-52`(forge)只清 `komachi_use_count`/`komachi_damage_bonus`) |
| 8 | 活体书页(每次使用本周期累计 +1) | 临时(累计) | `item/card/LivingPageItem.java:43-47`(两版本同行);附件 `ModAttachments.java:62-66`(neo)/`:63-64`(forge) | `clearRoundBonuses` `EffectCardPeriod.java:186`(neo)/`:187`(forge) |

> 复制计数(忍者/魔法秘典/魔法箭袋/小猪存钱罐)**不产生出牌数**,不属于本节;见 §2 场景 A 第 11-14 步。

### 1.2 状态权威(附件)

| 键 | 定义 | 语义 | 写入点 |
|---|---|---|---|
| `effect_card_play_count` | `ModAttachments.java:36-40`(neo)/`:43-44`(forge) | 本轮已出牌数,**仅在周期存活时有意义** | `EffectCardPeriod.registerPlay:285-286`(neo)/`:286-287`(forge);清零 `:279-283`/`:280-284`(registerPlay 边界)、`:326`/`:327`(tick)、`PlayerLifecycleHandler.java:155`/`:152`(死亡) |
| `effect_card_cooldown_end` | `ModAttachments.java:78-82`(neo)/`:76-77`(forge) | **周期的唯一权威标志**:>now 表示周期已打满且在倒计时 | `registerPlay:288-292`(neo)/`:289-293`(forge);`tick:322`(neo)/`:323`(forge)(不变式修复补冷却);清零 `:279`/`:280`、`:325`/`:326`、死亡 `:156`/`:153` |
| `effect_card_bonus_plays` | `:45-49`(neo)/`:49-50`(forge) | 一次性 +1(0/1) | 仅 `grantBonusPlay:172`/`:173` |
| `living_page_cycle_bonus` | `:62-66`(neo)/`:63-64`(forge) | 本周期活体书页累计 | 仅 `LivingPageItem:47` |
| `candy_chip_play_bonus` | `:544`(neo)/`:487`(forge) | 每轮一次标记 | `CandyChipItem:42` |
| `satellite_play_bonus`(+`_cooldown_end`) | `:558`/`:565`(neo)、`:496`/`:500`(forge) | 每 1:00 一次标记 + 独立冷却 | `SatelliteChipItem:69-70` |

> 两版本**均已同步到客户端**:neo 侧 `.sync(ByteBufCodecs.INT/VAR_INT/VAR_LONG)`(`ModAttachments.java:39/48/65/81`);
> forge 侧 5 个键全部列入 `SYNCED_KEYS`(`ModAttachments.java:824-827、837-839`)且 `AttachedDataKey.set` 每次写入即推送(`component/AttachedDataKey.java:74-75`)。
> `effect_card_cooldown_end` 的注释称 "-1 表示待定冷却"(`ModAttachments.java:77` neo / `:75` forge),但全仓**没有任何写入 -1 的代码**(`git grep setEffectCardCooldownEnd` 仅命中 `EffectCardPeriod` 的 0/now+cd 与死亡清零),该分支为死注释。

### 1.3 其余参与方

| 参与方 | 位置 | 作用 |
|---|---|---|
| 出牌流程基类 | `item/card/BaseEffectCardItem.java:136-254`(两版本同构) | 唯一 `registerPlay` 调用点(`:220`) |
| 周期状态机 | `item/card/EffectCardPeriod.java`(neo/forge 同构) | `getMaxAllowed/isBurstFull/isCooldownActive/isEffectPending/isBlocked/registerPlay/tick/grantBonusPlay/clearRoundBonuses` |
| 周期驱动 | `event/PlayerTickEvents.java:119-143`(neo,`PlayerTickEvent.Post`)/`:117-141`(forge,`TickEvent.PlayerTickEvent`) | 每 20 tick 调 `EffectCardPeriod.tick`(`:137` neo / `:135` forge) |
| 死亡清理 | `event/PlayerLifecycleHandler.java:155-157`(neo)/`:152-154`(forge) | 清计数/冷却 + `clearRoundBonuses` |
| 效果待定来源 | `EffectCardPeriod.java:122-130`(neo)/`:123-131`(forge) | 9 个效果:living_page/monster_laser/monster_brick/orbital_strike/directional_blast/fate_guidance/king_power/berserk/unwavering |
| 出牌守卫(客户端) | `client/EffectCardUseGuard.java:26-77` + `client/ClientTickHandler.java:16-21` + `mixin/client/AstralUseItemGuardMixin.java:21-29`(两版本同文) | 同一次按住只出一张 |
| 复制计数钩子 | `KomachiSignItem.java:100-129`(neo)/`:101-130`(forge)、`chip/MagicTomeChipItem.java:25-50`、`chip/MagicQuiverChipItem.java:39-46` | 全效果牌计数,无类型过滤 |
| 叠层牌 | `BerserkCardItem.java:32-38`、`EffectCardItem.java:27-37`、`UnwaveringCardItem.java:34-43`(两版本) | 层数上限/时长刷新 |

---

## 2. 使用与周期顺序

### 场景 A:手持效果牌右键(服务端实际顺序)

| 步 | 动作 | 位置(neo / forge) |
|---|---|---|
| 0 | 客户端 `startUseItem` 守卫(见场景 C) | `mixin/.../AstralUseItemGuardMixin.java:24-28` |
| 1 | 客户端预检(专属 + 满牌/锁),被挡则 `fail`,不消耗、不播动画 | `BaseEffectCardItem.java:140-145` / 同 |
| 2 | 服务端决定受益目标(下蹲+`canUseOnOtherPlayers` → 面前玩家) | `:148-154` / 同 |
| 3 | **专属校验**:`isExclusive() && !ExclusiveCardUtil.canUse` → 失败 | `:208-210` / 同;`item/card/ExclusiveCardUtil.java:25-28`(owner 比对 uuid) |
| 4 | **出牌锁**:`EffectCardPeriod.isBlocked` → 失败 | `:212-214` / 同;`EffectCardPeriod.java:254-258` / `:255-259` |
| 5 | **施加效果**:子类 `applyEffect`(活体书页/命运指引在此**先**抬高上限,见场景 H) | `:217` / 同 |
| 6 | **出牌登记**:`registerPlay`(周期边界 → count+1 → 打满则起冷却) | `:220` / 同;`EffectCardPeriod.java:268-293` / `:269-294` |
| 7 | 可口糖果:治愈+1、回血+1、满血置"每轮一次"标记(**在登记之后**) | `:223` / 同;`CandyChipItem.java:29-44` / `:30-45` |
| 8 | 电击手套:充能+1;伤害牌且充能≥4 → 扣 4 层并武装本周期法伤扩散 | `:226` / 同;`ElectricGloveChipItem.java:57-70` |
| 9 | 探天卫星:若是轨道炮 → 置"每 1:00 一次"标记(**在登记之后**) | `:229-231` / 同;`SatelliteChipItem.java:63-71` / `:64-72` |
| 10 | 扫地机(加急加快)/大当家(治疗类)/肉弹战车(汉堡·蛋糕)钩子 | `:234-245` / 同 |
| 11 | **复制计数**:忍者(每 3 张复制最后一张 + 主动冷却 -30% + 伤害增益 +1) | `:248` / 同;`KomachiSignItem.java:100-129` / `:101-130` |
| 12 | 魔法秘典(每 3 张复制) | `:249` / 同;`MagicTomeChipItem.java:25-50` |
| 13 | 魔法箭袋(记录第一张,命中带标记目标时返还) | `:250` / 同;`MagicQuiverChipItem.java:39-46,53-73` |
| 14 | 小猪存钱罐(每 2 张给 3 星币) | `:252` / 同;`PiggyBankChipItem.java:33-42` |
| 15 | **消耗 1 张** | `:161` / 同 |

证据(登记与消耗的确切位置):

```java
// BaseEffectCardItem.java:206-220(两版本同)
private boolean tryUseCard(Level level, Player player, LivingEntity applyTo, ItemStack stack) {
    if (isExclusive() && !ExclusiveCardUtil.canUse(player, stack)) return false;   // 专属
    if (EffectCardPeriod.isBlocked(player)) return false;                          // 出牌锁
    applyEffect(level, player, applyTo, stack);                                    // 施加效果
    EffectCardPeriod.registerPlay(player);                                         // 出牌登记
```

**顺序要点(与卷首描述有出入的点,均有源码证据)**:
1. 「出牌登记」与「消耗」之间**还夹着 4 组钩子**(糖果/手套/卫星/立牌被动)与 4 组复制计数 —— 不是"登记→复制→消耗"三步。
2. 活体书页/命运指引的上限抬升发生在**第 5 步**(`applyEffect`),糖果/卫星的上限抬升发生在**第 7/9 步**(登记之后)——见 `S5-C6`。

### 场景 B:对实体使用(`canUseOnOtherPlayers`)

`interactLivingEntity:166-184`(两版本):客户端预检 `:171-175` → 服务端 `tryUseCard`(同场景 A 第 3-14 步)→ `:182` 消耗。`canUseOnOtherPlayers` 为 true 的牌:狂暴(`BerserkCardItem.java:21-23`)、加急加快、奢华大餐、你有我有;其余效果牌返回 `PASS`(`:168-170`),不响应实体交互。

### 场景 C:同一次按住只出一张

| 步 | 位置 |
|---|---|
| 注入点 | `Minecraft#startUseItem` HEAD:`AstralUseItemGuardMixin.java:24-28`(`@Inject(method="startUseItem()V", at=HEAD, cancellable=true)`) |
| 反编译核对:方法确实存在且是"按下+长按自动重复"的共同入口 | 1.20.1 `Minecraft.java:1678` 定义、调用点 `:1999-2001`(`while(keyUse.consumeClick())`)与 `:2008-2010`(`if(keyUse.isDown() && rightClickDelay==0 && !player.isUsingItem())`);1.21.1 `:1712` 定义、`:2034-2036`、`:2043-2045` |
| 判定 | `EffectCardUseGuard.beginOrSuppressUse:50-68`:`!isDown()` → 复位并放行;非效果牌 → 放行;`playedDuringHold` → 抑制;否则置位放行 |
| 复位点 | `onClientTick:38-44`(松开即 `playedDuringHold=false`),由 `ClientTickHandler.java:16-21`(neo `ClientTickEvent.Post` / forge `TickEvent.ClientTickEvent`)每客户端 tick 调用 |
| 为什么必须在输入层 | 两版本 `MultiPlayerGameMode.useItem` 都**无条件返回并发送** `ServerboundUseItemPacket`(1.20.1 `MultiPlayerGameMode.java` `useItem` 内 `startPrediction` 的 `return serverbounduseitempacket`,含 `ItemCooldowns` 命中分支;1.21.1 同结构),故 `Item#use` 返回 fail 拦不住服务端 |

### 场景 D:出牌登记的周期边界(`registerPlay`)

```java
// EffectCardPeriod.java:268-293(neo;forge:269-294)
public static void registerPlay(Player player) {
    long now = player.level().getGameTime();
    long cooldown = ModAttachments.getEffectCardCooldownEnd(player);
    int played = ModAttachments.getEffectCardPlayCount(player);
    if ((cooldown > 0 && now >= cooldown)                                   // ① 冷却已到期但 tick 未清
            || (cooldown <= 0 && played > 0 && played >= getMaxAllowed(player))) {  // ② 计数≥上限却无冷却
        ModAttachments.setEffectCardCooldownEnd(player, 0);
        ModAttachments.setEffectCardPlayCount(player, 0);
        clearRoundBonuses(player);        // 一次性 +1 / 糖果 / 卫星 / 活体书页累计
        cooldown = 0;
    }
    int count = ModAttachments.getEffectCardPlayCount(player) + 1;
    ModAttachments.setEffectCardPlayCount(player, count);
    if (count >= getMaxAllowed(player)) {                                   // 打满才起冷却
        long cooldownTicks = ChargeManager.cooldownTicks(player, GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L);
        ModAttachments.setEffectCardCooldownEnd(player, now + cooldownTicks);
    }
}
```

### 场景 E:周期结算(`tick`,每 20 tick 由 `PlayerTickEvents` 驱动)

```java
// EffectCardPeriod.java:312-332(neo;forge 为 313-333,逐行同构)
312  public static void tick(Player player) {
313      long now = player.level().getGameTime();
314      long cooldown = ModAttachments.getEffectCardCooldownEnd(player);
315      int played = ModAttachments.getEffectCardPlayCount(player);
316      if (cooldown > 0 && now < cooldown) return;          // 冷却进行中:不动
317      if (cooldown <= 0) {
318          if (played <= 0) return;                          // 无残留
319          if (played < getMaxAllowed(player)) return;       // 正常累积中(未打满、无冷却)→ S5-C2
320          long recoverTicks = ChargeManager.cooldownTicks(player,
321                  GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L);
322          ModAttachments.setEffectCardCooldownEnd(player, now + recoverTicks);   // 不变式修复:补冷却
323          return;
324      }
325      ModAttachments.setEffectCardCooldownEnd(player, 0);   // 冷却到期:周期结束
326      ModAttachments.setEffectCardPlayCount(player, 0);
327-329 // 周期归零注释
330      clearRoundBonuses(player);
331      ElectricGloveChipItem.disarmAoe(player);             // ← 周期侧唯一解除点,见 S5-C3
332  }
```

**"计数≥上限但无冷却"是否真的被覆盖(逐条)**:

| 路径 | 覆盖? | 证据 |
|---|---|---|
| `tick` 的正常到期分支 | 覆盖 | `:316` `cooldown > 0 && now < cooldown` → 不住;`:325-331` 清零(需冷却值>0) |
| `tick` 的不变式修复分支 | 覆盖 | `:317-323`:`cooldown<=0` 且 `played >= getMaxAllowed` → 写入 30s(充能时 24s)冷却;下一轮到期即清零 |
| `registerPlay` 分支①(冷却已到期) | 覆盖 | `:277-284`:先清零再登记 |
| `registerPlay` 分支②(`cooldown<=0 && played>=max`) | 代码覆盖,但**玩家路径不可达** | 该状态下 `isBurstFull` 恒为真(`:198-204`),`tryUseCard:212-214` 在到达 `registerPlay` 前即拒绝;分支②只能由"绕过 `isBlocked` 的调用方"触发,而全仓 `registerPlay` 只有 `BaseEffectCardItem:220` 一个调用点 |
| 上限中途变小(卸筹码/效果到期) | 覆盖(≤20 tick 内) | 由分支②/tick 修复分支接管;`isBurstFull` 在冷却未到期时仍为真(`:199-203`),玩家在 ≤1s 内被拒,随后被补一轮完整冷却 |
| 冷却已到期但 tick 未跑的窗口(≤19 tick) | 视为新周期 | `isBurstFull:200` `if (cdEnd > 0 && now >= cdEnd) return false;`;`registerPlay` 分支① |

### 场景 F:死亡

`PlayerLifecycleHandler.java:155-157`(neo)/`:152-154`(forge):`setEffectCardPlayCount(0)` → `setEffectCardCooldownEnd(0)` → `EffectCardPeriod.clearRoundBonuses(player)`。同处 `:175`/`:171` 移除 `FATE_GUIDANCE` 效果。
`electric_glove_aoe` **不在**该清单,但两版本的该类键**均未标记死亡复制**(neo `ELECTRIC_GLOVE_AOE` 定义 `ModAttachments.java:873` 无 `.copyOnDeath()`;全文件仅 `:176`/`:280` 两处 `.copyOnDeath()`;forge 只在 `AstralData.onPlayerClone:74-95` 复制显式白名单),故重生后为新实体的默认值 false → 死亡路径**不构成** AoE 残留。

### 场景 G:立牌主动一次性 +1(忍者)

`KomachiSignItem.handleUse:64-85`(两版本):客户端直接 success(`:65-67`)→ 服务端三条前置,任一不满足返回 `fail`:
1. `EffectCardPeriod.isCooldownActive(player)` → 提示 `komachi_active_cooldown`(`:70-73`);
2. `getMaxAllowed(player) >= GameplayConstants.MAX_EFFECT_CARD_PLAYS` → 提示 `komachi_active_capped`(`:74-78`);
3. `!grantBonusPlay(player)`(本轮已授予)→ 提示 `komachi_active_used`(`:80-83`)。

`fail` 不进入主动冷却:`BaseSignItem.performSkill` 只有 `result.getResult() == SUCCESS` 才写 `setSignActiveCooldownEnd` —— `:98-99` 判定、`:114-115` 写入(neo);`:99-100` 判定、`:115-116` 写入(forge)。

### 场景 H:活体书页(上限抬升在 `registerPlay` **之前**)

`LivingPageItem.applyEffect:34-50`:绑定 owner → `rin_pages + 1`(永久,用于法伤)→ **本周期累计 +1 并钳制到 9** → 施加 60s 效果:

```java
// LivingPageItem.java:43-47(两版本同)
int nextCycleBonus = ModAttachments.getLivingPageCycleBonus(user) + 1;
if (nextCycleBonus > GameplayConstants.MAX_EFFECT_CARD_PLAYS) nextCycleBonus = GameplayConstants.MAX_EFFECT_CARD_PLAYS;
ModAttachments.setLivingPageCycleBonus(user, nextCycleBonus);
```

后果:使用活体书页时,上限先涨 1 再计数 ⇒ **单张活体书页自己不会把本轮打到上限**(例:基础上限 1 时,用前 `bonus=0/max=1`,用后 `bonus=1/max=2`,登记 `count=1<2` 不起冷却)。这条钳制是本状态机不变量的一部分:去掉它 `max` 仍受 `Math.min(9,...)` 封顶,但附件会无界增长(注释声称的原因,代码上确实实现了钳制)。

### 场景 I:可口糖果 / 探天卫星(上限抬升在 `registerPlay` **之后**)

`CandyChipItem.onEffectCardUsed:29-44`、`SatelliteChipItem.onOrbitalStrikeUsed:63-71` 均由 `tryUseCard` 在**登记之后**调用,故本次出牌仍按**抬升前**的上限判定是否起冷却;抬升后 `count < max` 且冷却在跑 ⇒ `isBlocked` 第二分支放行(`EffectCardPeriod.java:256-257`),玩家因此多得一张。二者行为一致;与活体书页的顺序差异见 `S5-C6`。

### 场景 J:上限实时变小(卸下筹码 / 效果到期)

`getMaxAllowed` 不缓存(每次遍历来源表 + 读附件),故卸下即变。三条收尾路径:
1. 冷却未到期 → `isBurstFull` 仍真 → 玩家被拒;冷却到期后 `tick:325-331` 清零(正常);
2. 冷却已到期未清零(≤19 tick 窗口)→ `isBurstFull:200` 判为陈旧 → 允许出牌 → `registerPlay` 分支①清零;
3. 无冷却且 `count>=max`(只可能来自上限变小)→ `isBurstFull` 真 → 玩家被拒;≤20 tick 后 `tick:317-323` **补一轮完整 30s(充能 24s)冷却**,再走路径 1 清除。

### `isBlocked` 真值表(代码推导)

```java
// EffectCardPeriod.java:254-258(neo;forge:255-259)
public static boolean isBlocked(Player player) {
    if (isBurstFull(player)) return true;          // count>=max 且冷却未到期/未起
    if (isCooldownActive(player)) return false;    // 冷却进行中:忽略效果待定,放行(上限内)
    return isEffectPending(player);                // 无冷却:效果未结束则锁新周期
}
```

| count vs max | 冷却状态 | 有效果待定 | 结果 |
|---|---|---|---|
| count ≥ max | 未到期 | 任意 | 阻止(第一分支) |
| count ≥ max | 已到期(stale) | 任意 | 不阻止 → `registerPlay` 分支①开新周期 |
| count < max | 进行中 | 任意(含待定) | **放行** → 这是"冷却期间继续出牌"的唯一可达路径 |
| count < max | 无 | 有 | 阻止(锁新周期) |
| count < max | 无 | 无 | 放行 |

由此直接得到两条可核验的玩法事实:
- 效果牌(9 个待定效果之一)用出后 60s/3:00/5:00 内,只要**没有冷却在跑**,任何效果牌都会被 `isEffectPending` 挡住 —— "9 张上限"只能在**冷却窗口内**(`isCooldownActive` 为真时忽略待定)被推满;
- 冷却窗口内每张活体书页会因 `count>=max` 而**重写**冷却结束时刻(`registerPlay:288-292`),故连打会持续续期。

---

## 3. 冲突项

> 严重度口径:高=功能不可用/可被利用的永久锁死;中=功能缺失或长期残留状态;低=显示/边界/跨版本细节。
> 每条给两版本证据;"两版本是否皆有"指该缺陷是否在两个子项目都存在。
> 本切片**未发现"高"级冲突**(无永久锁死路径:见 §2 的 `isBlocked` 真值表与"已核验"表)。

| ID | 严重度 | 现象(一行) | 两版本是否皆有 |
|---|---|---|---|
| S5-C1 | 中 | forge-1.20.1「以毒攻毒」用 `getDescriptionId()` 比对 `minecraft:` 前缀 ⇒ 永不移除负面效果 | 仅 1.20.1 |
| S5-C2 | 中 | 未打满的一轮永不结束 ⇒ 计数/糖果/卫星/活体书页本周期状态可长期残留 | 皆有 |
| S5-C3 | 低 | 周期在 `registerPlay` 内结束时(`tick` 之外)不解除电击手套 AoE | 皆有 |
| S5-C4 | 低 | forge 未按 `TickEvent.Phase` 过滤 ⇒ 周期 tick 每 20 tick 执行两次 | 仅 1.20.1 |
| S5-C5 | 低 | "剩余冷却 %s 秒"把效果待定时长当冷却显示;陈旧计数窗口 tooltip 显示旧值 | 皆有 |
| S5-C6 | 低 | 活体书页(pre)与糖果/卫星(post)抬升上限的时机不一致 | 皆有 |
| S5-C7 | 低 | 创造模式消耗:1.20.1 `shrink` 照常消耗,1.21.1 `consume` 不消耗 | 差异项 |

### 已核验的疑似项(判定为非冲突,附证据)

| 疑似项 | 结论 | 证据 |
|---|---|---|
| 多个 +1 来源叠加超过 9 | **不成立** | `getMaxAllowed:150`(neo)/`:151`(forge) `Math.min(9, 1+extra)`;`count` 只在 `registerPlay:285-286`/`:286-287` +1,且调用前必经 `isBlocked`(count<max 或冷却 stale);活体书页每张至多把 `living_page_cycle_bonus` 累到 9(`LivingPageItem:44-46`)。构造验证(代码推演,全部发生在冷却窗口内):大背包+忍术飞镖 ⇒ max=3;用 3 张牌打满,其中**最后一张**若触发可口糖果(`CandyChipItem:41-43`,在 `registerPlay` 之后)⇒ 结算后 max=4、count=3,冷却仍在跑 ⇒ 出现 1 张余量;此后每张活体书页使 `max` 与 `count` 各 +1(钳制到 9)⇒ 逐张推高到 `count=9=max=9`,`isBurstFull` 为真 ⇒ 第 10 张被拒(忍者主动在冷却中会被 `KomachiSignItem:70` 直接拒绝,故不构成第二条推高路径)。`Math.min` 保证 max 永不 > 9,`isBlocked` 保证 count 不会超过当次 max |
| 负值附件导致上限退化/永久"已打满" | **不成立** | `getMaxAllowed:148` `if (extra < 0) extra = 0;` + `1 + extra` ⇒ max ≥ 1;`setEffectCardBonusPlays:56`/`setLivingPageCycleBonus:73-74` 均 `Math.max(0,value)` |
| 充能 -20% 与 tooltip 显示值不一致 | **不成立(两版本一致)** | 显示 `ModTooltipHandler.effectCardCooldownSeconds:1307-1313`(neo)/`:1304-1310`(forge) 与写入 `registerPlay:289-291`/`:290-292`、修复分支 `tick:320-322`/`:321-323` 走**同一个** `ChargeManager.cooldownTicks`(`:64-68` 两版本同;600t→480t=24s,无充能 30s) |
| 复制计数漏计/多计某张牌 | **未发现**(15 个 `cardTypeId()` 与 `cardByTypeId` 的 15 个 case 完全对齐;`tryUseCard` 的 4 个计数钩子对所有效果牌无过滤) | `BaseEffectCardItem.java:70-89` vs 各牌 `cardTypeId()`(Berserk:28/Unwavering:30/EffectCardItem:23/FateGuidance:39/LivingPage:24/MonsterLaser:14/MonsterBrick:14/OrbitalStrike:14/DirectionalBlast:14/ChocolateCake:21/Hamburger:21/LuxuryFeast:29/YouHaveIHave:22/ExpressDelivery:25/FightPoison:34);战斗牌 `CardItem` 不经过 `tryUseCard` ⇒ 不计入(与"仅效果牌计数"口径一致) |
| 最大叠层公式有分歧 | **不成立(三张牌同式)** | 见 §4 表 |
| 长按导致永久只能出一张 | **未复现到永久路径**(残余边界见 §5) | 复位依赖每 tick 的 `onClientTick`(`EffectCardUseGuard:38-44`)+ `beginOrSuppressUse:56-59` 的惰性复位;两处都在 `keyUse.isDown()==false` 时复位,不存在"只在长按分支复位"的死角 |

### S5-C1 【中】forge-1.20.1「以毒攻毒」永远移除不了任何负面效果(跨版本功能不对等)

- **现象**:1.21.1 使用后中毒结束时移除最多 3 个原版负面效果;1.20.1 **一个都不移除**(正则式永假),玩家只得到"中毒 8 秒 + 生命恢复 II"。
- **证据**:
  ```java
  // forge-1.20.1/.../item/card/FightPoisonWithPoisonCardItem.java:65-68
  MobEffect effect = instance.getEffect();
  String id = instance.getEffect().getDescriptionId();          // ← "effect.minecraft.poison"
  if (id != null && id.startsWith("minecraft:") && effect.getCategory() == MobEffectCategory.HARMFUL) {
  ```
  ```java
  // neoforge-1.21.1/.../item/card/FightPoisonWithPoisonCardItem.java:65-68
  MobEffect effect = instance.getEffect().value();
  String id = instance.getEffect().getRegisteredName();         // ← "minecraft:poison"
  if (id != null && id.startsWith("minecraft:") && ...)
  ```
  语义核对(反编译源码):1.20.1 `net/minecraft/world/effect/MobEffect.java:143-150` `descriptionId = Util.makeDescriptionId("effect", BuiltInRegistries.MOB_EFFECT.getKey(this))` ⇒ `getDescriptionId()` 返回 `effect.<ns>.<path>`,**永不**以 `minecraft:` 开头;1.21.1 NeoForge 的 `Holder.getRegisteredName()`(`net/minecraft/core/Holder.java:40`)返回注册名 `minecraft:poison`,条件成立。
- **建议修法**:forge 侧改用注册名,例如 `BuiltInRegistries.MOB_EFFECT.getKey(effect)`/`ForgeRegistries.MOB_EFFECTS.getKey(effect)` 的 `toString()`,或改为 `effect.getCategory()==HARMFUL && registryKey.getNamespace().equals("minecraft")`。
- **两版本是否皆有**:仅 forge-1.20.1(1.21.1 正确)。

### S5-C2 【中】未打满的一轮永不结束 ⇒ "每轮一次"标记与本周期累计可无限期残留

- **现象**:若某轮出牌数始终没到上限(例如只出 1-2 张后不打了),该轮**没有冷却**,`tick` 的"未打满"分支直接 return ⇒ 计数、`candy_chip_play_bonus`、`satellite_play_bonus`、`living_page_cycle_bonus` 全部保留到玩家再次出牌/死亡为止,可以跨越任意长的真实时间(包括重新登录:这些键均有 Codec 序列化)。
- **证据**:
  ```java
  // EffectCardPeriod.java:316-320(neo;forge:317-321)
  if (cooldown > 0 && now < cooldown) return;
  if (cooldown <= 0) {
      if (played <= 0) return;
      if (played < getMaxAllowed(player)) return;   // ← 未打满:周期不结束、bonus 不清
  ```
  唯一的周期结束入口是"冷却到期"(`:325-331`/`:326-332`)与 `registerPlay` 的边界(`:277-284`/`:278-285`);两者都以 `effect_card_cooldown_end` 为条件,而冷却只在 `count>=max` 时启动(`:288-292`/`:289-293`)。
  可观测后果:`CandyChipItem:41-43` 的 `if (wasFull && !isCandyChipPlayBonusActive)` 使得同一份"每轮一次"标记在跨越长时间后仍阻止第二次触发;`living_page_cycle_bonus` 同理一直挂着(上限被钳制在 9,故不会无限增长)。
- **建议修法(需先定夺口径,见 §5)**:两种方向之一 ——(a) 给周期加空闲超时(如 N 秒无出牌且无效果待定即视为周期结束,`tick` 中补清零);(b) 明确"周期=直到打满为止",并把文档/工具提示的"每轮一次"改成"每装满一轮一次"。任选其一都要求改动仍集中在 `clearRoundBonuses` 这一个入口。
- **两版本是否皆有**:皆有(两版本代码同构)。

### S5-C3 【低】周期在 `registerPlay` 内结束时不解除电击手套法伤扩散(AoE 可跨周期白嫖 4 层充能)

- **现象**:本周期已武装的 `electric_glove_aoe` 只在 `tick` 的"冷却到期"分支被解除;若周期是被 `registerPlay` 的分支①/②结束的,武装状态会带进下一个周期。因为 `ElectricGloveChipItem.onEffectCardUsed:65` 遇到"已武装"直接 return,新周期第一张伤害效果牌**不再扣 4 层充能**即可获得一次 AoE。
- **证据**:
  ```java
  // EffectCardPeriod.java:182-187(neo;forge:183-188)clearRoundBonuses —— 无 glove 项
  public static void clearRoundBonuses(Player player) {
      ModAttachments.setEffectCardBonusPlays(player, 0);
      ModAttachments.setCandyChipPlayBonusActive(player, false);
      ModAttachments.setSatellitePlayBonusActive(player, false);
      ModAttachments.setLivingPageCycleBonus(player, 0);
  }
  // EffectCardPeriod.java:277-284(neo;forge:278-285)周期边界只调 clearRoundBonuses
  // EffectCardPeriod.java:331(neo;forge:332)  ← 唯一的周期侧 disarmAoe
  com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem.disarmAoe(player);
  ```
  ```java
  // chip/ElectricGloveChipItem.java:64-69(两版本同)
  if (ModAttachments.isElectricGloveAoe(player)) return;      // 已武装:不再扣充能
  if (ChargeManager.getStacks(player) < CHARGE_REQUIRED) return;
  ChargeManager.consume(player, CHARGE_REQUIRED);
  ModAttachments.setElectricGloveAoe(player, true);
  ```
  另一解除点只有"实际触发扩散时"`combat/SpellDamageRegistry.java:384`(两版本同)。死亡路径不构成泄漏(该键无 `copyOnDeath`,见 §2 场景 F)。
  可达序列(推演,未实测):上限 3 时"伤害牌武装(60s 效果)→ 等效果过 → 两张无待定效果的牌填满上限 → 冷却到期后的 ≤19 tick 窗口内再出一张" ⇒ 分支① 清周期但保留 AoE。
- **建议修法**:把 `disarmAoe` 也放进 `clearRoundBonuses`(或让 `registerPlay` 的两个边界与 `tick` 共用同一收尾方法),并在死亡清单里补 `setElectricGloveAoe(player,false)`。
- **两版本是否皆有**:皆有。

### S5-C4 【低】forge-1.20.1 的周期 tick 每个 20 tick 窗口执行两次(1.21.1 只一次)

- **现象**:`PlayerTickEvents.onPlayerTick` 未过滤 `TickEvent.Phase`,而 Forge 的 `TickEvent.PlayerTickEvent` 每 tick 触发 START 与 END 两次;`player.tickCount % 20 == 0` 的守卫在**同一个 tick 的两个阶段**都为真 ⇒ tick 体(含 `EffectCardPeriod.tick`)连续执行两次。
- **证据**:`forge-1.20.1/.../event/PlayerTickEvents.java:117-141`(`onPlayerTick(TickEvent.PlayerTickEvent event)`,无 `event.phase` 判断,守卫在 `:131`);同文件 `:108-113` 的 `onPlayerTickPre` 同样无 phase 过滤。
  反编译证据:`forge-1.20.1-47.4.10-sources.jar → net/minecraftforge/event/ForgeEventFactory.java:938-946`(`onPlayerPreTick`→Phase.START / `onPlayerPostTick`→Phase.END);`net/minecraft/world/entity/player/Player.java:217`(START,方法头)与 `:288`(END,方法尾);`net/minecraft/server/level/ServerLevel.java:690-693`(`++p_entity.tickCount;` 在 `p_entity.tick()` **之前**)⇒ 两阶段看到同一个 `tickCount`。
  对照:同一代码库在别处**正确**过滤了 phase —— `client/KeyBindingSetup.java:35`、`item/MarkManager.java:90`、`event/RailgunStrikeScheduler.java:123` 均 `if (event.phase != TickEvent.Phase.END) return;`。1.21.1 用 NeoForge 的 `PlayerTickEvent.Pre/Post`(`neoforge-21.1.235-sources.jar → net/neoforged/neoforge/event/EventHooks.java:966-980` 各一次),不存在重复。
- **对本切片的影响:不构成状态机缺陷**。`EffectCardPeriod.tick` 两次调用幂等:第一次若"冷却进行中"直接 return;若"冷却到期"则清零,第二次因 `played<=0` return;若"补冷却",第二次因 `cooldown>0 && now<cooldown` return(见 §2 场景 E 代码)。
- **建议修法**:给 `onPlayerTick`/`onPlayerTickPre` 加 `if (event.phase != TickEvent.Phase.END) return;`(与 `RailgunStrikeScheduler` 一致)。**注意**:同文件的 `HealingManager.tick`/`FenSignItem.tick`/`updateCutterEffect` 也在此体内,超出本切片(`EmpowerManager.tick:59-84` 经 `now < next` 守卫已幂等,可作为核对方法)。
- **两版本是否皆有**:仅 forge-1.20.1 有重复执行;1.21.1 无。

### S5-C5 【低】"本轮出牌数已用完!剩余冷却 %s 秒"把效果待定时长当作冷却显示

- **现象**:满牌提示的秒数取 `max(冷却结束时刻, 所有待定效果中最晚结束时刻)`(`getRemainingBlockTicks:225-235`/`:226-236`),故冷却只剩 24s 而命运指引还剩 4 分钟时,提示会显示"剩余冷却 ~300 秒",而那个时间其实是效果锁、且期间冷却早已结束;另在"冷却已到期但 tick 未清零"的 ≤19 tick 窗口里,`isBurstFull:200` 已判为陈旧不再拦截,但 tooltip 仍显示旧计数(如 `3/3`)。
- **证据**:
  ```java
  // BaseEffectCardItem.java:191-196(neo;forge:191-196)
  if (EffectCardPeriod.isBurstFull(player)) {
      int seconds = EffectCardPeriod.getRemainingBlockSeconds(player);
      player.displayClientMessage(Component.translatable("msg.astral_dice.effect_card_burst_full", seconds), true);
      return true;
  }
  // EffectCardPeriod.java:226-235:maxEnd 同时含 cooldownEnd 与各待定效果 remainingEffectTicks
  ```
  文案 `assets/astral_dice/lang/zh_cn.json:399`,tooltip 计数 `ModTooltipHandler.java:294-303`(neo)/`:290-299`(forge)。
- **建议修法**:满牌提示区分两种原因——冷却未结束时只报 `cooldownEnd - now`,效果待定锁时报效果剩余(或换 key);tooltip 计数按 `isBurstFull` 判定后再显示,陈旧窗口内显示"新周期"或按 0 计。
- **两版本是否皆有**:皆有。

### S5-C6 【低】同一"抬高上限"动作的登记时机不一致(活体书页 pre-registerPlay vs 糖果/卫星 post-registerPlay)

- **现象**:活体书页在 `applyEffect` 内抬升上限(第 5 步),因此**本次出牌按抬高后的上限**判定;糖果/卫星在 `registerPlay` 之后抬升(第 7/9 步),本次出牌按抬高前的上限判定。差别可观测:基础上限 1 时用活体书页**不会**进入冷却(count 1 < max 2),用一张会触发糖果的牌**会**进入冷却(冷却时长 30s/24s),随后糖果的 +1 只在冷却窗口内提供额外一张。
- **证据**:`BaseEffectCardItem.java:217`(applyEffect)早于 `:220`(registerPlay);`LivingPageItem.java:43-47`(抬升)在 `applyEffect` 内;`:223`(糖果)与 `:229-231`(卫星)在登记之后;判定处 `EffectCardPeriod.java:288-292`(neo)/`:289-293`(forge)。
- **建议修法**:统一口径——要么把糖果/卫星的标记写入提前到 `applyEffect`/`registerPlay` 之前(需改成"本次使用先置标记"),要么在 `registerPlay` 前集中收集"本次使用的加成"。任选其一须同步两版本并复算 §3 中"上限 9"的构造。
- **两版本是否皆有**:皆有(两版本同序同构)。

### S5-C7 【低】创造模式下效果牌的消耗行为两版本不同

- **现象**:1.21.1 创造模式使用效果牌不消耗;1.20.1 创造模式照常消耗。
- **证据**:`neoforge-1.21.1/.../BaseEffectCardItem.java:161,182` 用 `stack.consume(1, player)`;`forge-1.20.1/.../BaseEffectCardItem.java:161,182` 用 `stack.shrink(1)`。语义核对:`neoforge-21.1.235-sources.jar → net/minecraft/world/item/ItemStack.java` `consume(int, LivingEntity)` = `if (entity == null || !entity.hasInfiniteMaterials()) shrink(amount);`;`forge-1.20.1-47.4.10-sources.jar → ItemStack.java` **不存在** `consume(int, LivingEntity)`,`shrink` 直接 `grow(-n)`。
- **建议修法**:forge 侧改为 `if (!player.hasInfiniteMaterials()) stack.shrink(1);`(1.20.1 的 `Player#hasInfiniteMaterials` 为 `isCreative()`)或封装一个两版本一致的消耗助手。
- **两版本是否皆有**:差异项(非"皆有"),仅在 create 模式可观测。

---

## 4. 跨版本对等性差异

### 4.1 已核对为"仅 API 适配、语义等价"的部分(`git diff --no-index` 于两子项目 card/chip/sign 包逐行比对)

| 项 | 1.21.1 | 1.20.1 | 判定 |
|---|---|---|---|
| 效果引用类型 | `Holder<MobEffect>` + `getRegisteredName()` | `MobEffect` + 注册表 key | 等价(除 S5-C1) |
| Curios 取用 | `CuriosApi.getCuriosInventory`(`EffectCardPeriod:335`) | `CuriosCompat.getCuriosInventory`(`:336`) | 等价(包装为 Optional) |
| 附件同步 | `.sync(ByteBufCodecs.*)`(`ModAttachments.java:39/48/65/81`) | `SYNCED_KEYS` 白名单(`:824-827,837-839`)+ 写时推送(`AttachedDataKey.java:74-75`) | 等价(5 个状态键都在) |
| 死亡复制 | `.copyOnDeath()`(仅 `:176`/`:280`) | `AstralData.onPlayerClone` 显式白名单(`:74-95`) | 等价(出牌状态键都不复制,死亡由 `PlayerLifecycleHandler` 清) |
| 出牌周期逻辑 | `EffectCardPeriod.java` 全文件 | 同名文件(行号 +1 起偏移) | **逐行同构**(唯一差别:类型/Holder、`CuriosCompat`) |
| 出牌数来源清单 | `:109-117` | `:110-118` | 完全相同(2 固定 + 3 临时) |
| 效果待定清单 | `:122-130` | `:123-131` | 完全相同(9 个) |
| `clearRoundBonuses` 内容 | `:182-187` | `:183-188` | 完全相同(4 项,缺 AoE 两版本同缺) |
| 叠层公式(3 张牌) | Berserk `:34-37`、EffectCardItem `:33-36`、Unwavering `:37-40`:`Math.min(existing.getAmplifier()+1, MAX_EFFECT_STACKS-1)`,时长 `Math.max(existing.getDuration(), 3600)` | 同三行(仅 `.get()`) | 完全相同,不存在"不同牌用不同钳制公式" |
| 岿然不动护甲缩放 | `effect/UnwaveringEffect.java:21-24` `addAttributeModifier(..., amp -> 8.0*(amp+1))` | `:24-31` `getAttributeModifierValue` = `8.0*(amp+1)` | 等价(amp 上限 2 ⇒ +24 护甲) |
| 出牌守卫 | `client/EffectCardUseGuard.java` + mixin | 同名同文 | 完全相同 |

### 4.2 差异清单(本切片相关)

| # | 差异 | 影响 | 关联冲突 |
|---|---|---|---|
| D1 | forge 使用 `MobEffect.getDescriptionId()` 而非注册名 | 「以毒攻毒」在 1.20.1 完全失效 | S5-C1 |
| D2 | forge 的 `TickEvent.PlayerTickEvent` 未按 phase 过滤 ⇒ 周期 tick 每窗口执行两次 | 状态机幂等,无功能差异;节奏统计口径不同 | S5-C4 |
| D3 | `ItemStack.consume` vs `shrink` | 创造模式消耗行为不同 | S5-C7 |
| D4 | 命运的指引减伤/饱食实现走不同事件(neo `LivingIncomingDamageEvent` LOWEST;forge `LivingAttackEvent` HIGHEST + `LivingHurtEvent` LOWEST) | 与出牌状态机无关(属 S0 范围);两版本 `applyEffect`/效果时长/`isFateGuidanceActive` 一致 | — |
| D5 | `chip/` 多数文件在 1.20.1 多一行 `CuriosCompat` import(如 `CandyChipItem`/`SatelliteChipItem` 整体 +1 行;`ElectricGloveChipItem`/`sign` 包的行号与 1.21.1 相同) | 仅影响审计引用行号,不影响行为 | — |

---

## 5. 未能判定 / 需人工确认

1. **未打满的一轮是否应当自动结束(S5-C2 的口径)**:代码事实清楚(只有冷却到期才结束周期);但"每轮一次"的正确语义(每次打满 vs 每次效果窗口)需要裁决才能定修法。改法会同时影响糖果/卫星/活体书页/忍者一次性 +1 的表现。
2. **上限中途下降时"补一轮完整冷却"是否符合预期**:`tick:317-323` 会在卸下大背包/忍术飞镖等操作后给玩家补 30s(充能 24s)冷却;这是代码内的不变式修复口径,不是可配置项。是否接受"玩家被动吃到一次完整冷却"需确认。
3. **S5-C3 的可达序列为静态推演**,未在游戏内实测(需构造"伤害牌武装 → 效果过期 → 无效果牌填满上限 → 冷却到期 ≤19 tick 窗口内再出牌")。建议用 `scripts/test` 工具链或手工复现后再改。
4. **出牌守卫的极端边界**:同一客户端 tick 内"按下-松开-再按下"(宏/自动点击器)时,`beginOrSuppressUse:56-59` 只看到 `isDown()==true`,第二次按下会被抑制(丢一次出牌意图);两版本一致。人手不可复现,是否需要在 `consumeClick` 分支额外放行需确认。
5. **服务端无节流**:守卫纯客户端,服务端对 `ServerboundUseItemPacket` 无速率限制(两版本 `MultiPlayerGameMode.useItem` 每次调用必发包,反编译已核);构造型客户端可在 1 tick 内把一轮打满(仍受 9 张/其它上限约束)。是否为多人公平性问题、要不要服务端补最小间隔需裁决。
6. **`effect_card_cooldown_end` 的 `-1`("待定冷却")语义已无写入方**(`ModAttachments.java:77` neo / `:75` forge 注释 vs 全仓无 `setEffectCardCooldownEnd(…,-1)`),确认该分支是废弃残留后建议删注释;若仍要保留,需要说明谁写 -1。
7. **同体 tick 的连带影响未评估**(超出本切片):`PlayerTickEvents.onPlayerTick` 体内还有 `HealingManager.tick`、`FightPoisonWithPoisonCardItem.tick`、`FenSignItem.tick`、`updateCutterEffect`(`:122-141` neo / `:120-139` forge)在 1.20.1 会被执行两次;本文仅验证了 `EffectCardPeriod.tick`(幂等)与 `EmpowerManager.tick`(`now < next` 守卫幂等),其余需另行核验。
8. **`getRemainingBlockSeconds` 的取整**:`Math.ceil(ticks/20.0)`(`:238-240`/`:239-241`)不含效果剩余为 0 的边界复核(仅显示层)。
