# P2 — §B 数值修饰器(乘法 / 加法 / 覆盖)第二轮只读审计

> 范围:仅 §B。载荷清单 = `docs/execution-order-checklist.md` §B(B1~B6);
> 配套第一轮扫描 `docs/interaction-audit-1.2.1.md`(其 §B/A3、§J 索引为本报告的起点)。
> 纪律:每条结论 = `文件:行号` + 代码原文(A = `neoforge-1.21.1` / B = `forge-1.20.1`)+ 可达性 +
> 最小实机验证步骤 + 严重度(blocker/high/medium/low)。不可达路径不列为缺陷。
> **本报告唯一写入的文件**(未修改任何源码/资源/配置)。

---

## 0. 审计基线与"改动中"标注(必读)

### 0.1 快照

```text
$ git rev-parse HEAD
d48a529ba5fcf14610dc7b7383a10780dbe49168
$ git diff --name-only      # 快照时刻 2026-09-15 15:45:19,共 34 个文件
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatModifiers.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/HostileTargets.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ChipDamageHandler.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/EnderDiceHandler.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/PlayerLifecycleHandler.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/PlayerTickEvents.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/CurioSlotUtil.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/EffectCardPeriod.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BaseChipItem.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/DiceCurioItem.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/NetherStarDiceItem.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BaseSignItem.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BonnieSignItem.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/HaiqingSignItem.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/MosesSignItem.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/resource/ResourceConversion.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatModifiers.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/HostileTargets.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/ChipDamageHandler.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/EnderDiceHandler.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/PlayerLifecycleHandler.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/PlayerTickEvents.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/CurioSlotUtil.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/card/EffectCardPeriod.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BaseChipItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/DiceCurioItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/NetherStarDiceItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BaseSignItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BonnieSignItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/HaiqingSignItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/MosesSignItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/resource/ResourceConversion.java
# 未跟踪(新增):bypasses_cooldown.json ×2、combat/PlayerHostilityTracker.java ×2
```

### 0.2 两种判断的读法

- **「改动前」= `git show HEAD:<path>`**(即第一轮扫描所依据的代码),**「改动后」= 快照时刻工作区**。
- `DiceCombatEvents` / `DiceCombatModifiers` / `EnderDiceHandler` / `ChipDamageHandler` /
  `DiceCurioItem` / `NetherStarDiceItem` / `BaseSignItem` / `CurioSlotUtil` / `BaseChipItem` /
  `EffectCardPeriod` / `PlayerLifecycleHandler` / `ResourceConversion` / `item/sign/*` **均处于改动中**:
  本报告里凡引用这些文件,**行号一律标注 `[HEAD]` 或 `[WT]`**;未被改动的文件(如
  `SpellDamageRegistry`、`ChargeManager`、`GameplayConstants`、`MosesSignItem#signCooldownTicks`)行号通用。
- 由于工作区在本报告写作期间仍在变化(实测 `CurioSlotUtil`→`BaseChipItem` 曾出现数分钟的
  半成品编译错误:`BaseChipItem` 调用了尚不存在的 `runOnRealUnequip`;快照时刻已自洽),
  **复核时请以文中引用的原文串为准重新定位行号**,不要只认行号。

### 0.3 与改动中批次的重叠结论(先看这张表,免得重复劳动)

| 本报告 ID | 第一轮编号 | HEAD(改动前) | WT(改动后,15:45 快照) |
|---|---|---|---|
| P2-C1 末影骰雨中 ×1.4 被骰战覆盖丢弃 | A3 | 缺陷(high) | **已修**(新增受击侧修饰器注册表 + `setNewDamage` 前重消费) |
| P2-C2 骰战覆盖同时丢弃抗性提升/保护附魔 | 未列 | 缺陷(high) | **未修**(仍存在) |
| P2-C3 加急加快按常量 −90s | §C2/C3 | 缺陷(high) | **已修**(路线 A:改取 `sign_active_max_cooldown`;见 C3) |
| P2-C4 命运指引基准取通用最大冷却 | §C2/C3 | 缺陷(high) | **已修**(路线 A + 新增附件键 `sign_active_max_cooldown`;见 C4) |
| P2-C5 能量过载 −30% 被 20 上限吞掉 | 未列 | 缺陷(medium) | **未修** |
| P2-C7 电击手套 AOE 基准跨版本 | 未列 | 单侧差异(medium) | **未修** |
| P2-C8 ×1.4 与七咒 `dice_curse_ratio` 的交叉 | A3 附注 | 1.20.1 单侧偏高 | **1.20.1 变为双重放大(新引入回归,未修)** |
| P2-C10 肾上腺素/能量回收 修饰器残留 | 未列 | 缺陷(medium) | **未修** |
| P2-C13 力量双重计入 | 未列 | 缺陷(medium) | **未修** |
| (顺带)安全气囊"致命"判定基准跨版本 | A6 | 单侧差异 | **已修**(WT `ChipDamageHandler:68` 换算吸收后) |

> ⚠️ **快照后仍在变化**:写作过程中该批次又陆续修改了 `FateGuidanceCardItem`、
> `JasmineSignItem`、`ModAttachments`(新增 `sign_active_max_cooldown`)等文件,
> 故上表 C3/C4 的"已修"是**快照后追加核对**得到的结论。复核时请以引用原文为准。

---

## 1. 载体清单与注册表全量

### 1.1 攻防修饰器唯一注册表 `DiceCombatModifiers`(键 = 注册顺序 = 执行顺序)

`[HEAD]` 行号;工作区在该文件插入了**两处**新内容:
① 「受击侧伤害修饰器」注册表小节(`neo [WT]:84-127` / `forge [WT]:87-130`)——其后的行号 **+48**(forge +49);
② 静态块末尾的内置受击侧修饰器(`neo [WT]:487-496` / `forge [WT]:490-499`)——其后(含
`record PowerRange` 与两个 `getDisplay*`)**再 +13/14**,合计 **+61~62**。
下表「WT」列只对 ① 生效(即两个静态块内的修饰器条目);引用显示方法时请用正文里给出的 WT 行号。

**攻击修饰器(27 个,`ATTACK_MODIFIERS`,`neo [HEAD]:123-413`)**

| # | 取值来源 | 基准 / 语义 | 行号(HEAD / WT) |
|---|---|---|---|
| 1 | 王之力 `KING_POWER`、狂暴 `BERSERK` | `ap += 5*(amp+1)` / `3*(amp+1)` | :123-133 / :171-181 |
| 2 | **原版力量 `DAMAGE_BOOST`** | `ap += (amp+1)*2`(**疑双重计入,见 P2-C13**) | :134-140 / :182-188 |
| 3 | 攻击牌掷骰 `CardRegistry.roll` × 每张已装卡 | 只写 `ctx.attackCardSum`,不改 `ap` | :144-153 / :192-201 |
| 4 | 护法 `misaki` 层数(1/2/3→+1/+2/+5)+ 爆发 +4 | `ctx.misakiStacks` / `ctx.misakiBurst` | :156-171 / :204-219 |
| 5 | 美工刀/锋利:满血(≥60%)或汲取期间 `+2+治愈` / `+4+治愈` | **阈值 0.6,不是"满血"** | :174-187 / :222-235 |
| 6 | 瞄具 +2 / 鹰眼 `+标记层数*2`(并施 1 层标记) | 作用于 `ctx.target` | :190-202 / :238-250 |
| 7 | 标靶 +1 / 手电筒 `星光/4`(整数除法) | 星光当前值 | :205-213 / :253-261 |
| 8/9 | 电流剑 `ElectricSwordChipItem.getAttackBonus` / 高级外设 | 充能层数 | :216-227 / :264-275 |
| 10 | 夹心饼干-美味:超过 20 的最大生命每 4 点 +1 | **当前最大生命** | :230-237 / :278-285 |
| 11 | 扫地机被动 `JASMINE_ATK_BONUS` | 物品数据组件 | :240-250 / :288-298 |
| 12 | 吸血鬼半血/汲取 +3 | 当前生命 ≤ 最大/2 | :253-261 / :301-309 |
| 13 | 拳套 +1/+3/+5(三档可叠加) | 常量 | :264-275 / :312-323 |
| 14 | 星币锤 `star_coin_hammer_bonus` | 赐福开始时按持有 30% | :278-283 / :326-331 |
| 15 | 诅咒之剑 `cursed_sword_bonus` | 每赐福 +1,上限配置 | :286-291 / :334-339 |
| 16 | 复仇之戟 +6 | 有指定负面效果时 | :294-301 / :342-349 |
| 17/18 | 大当家:养精蓄锐 >0 → +2;`FEN_FRENZY` → +3 | 附件 / 效果 | :304-319 / :352-367 |
| 19 | 骇客被动 + 主动 `nancy_lu_active_bonus` | 附件(有到期时刻) | :322-327 / :370-375 |
| 20 | 秘密侦探:目标带标记 +3 | `MarkManager.getLevel` | :330-336 / :378-384 |
| 21 | 枪匠弱点识破每层 +1 | `WeaknessRevealEffect` | :338-344 / :386-392 |
| 22 | 调查阶段:阶段≥3 `+2+标记`、阶段2 +2、Boss 阶段4 `+2+标记*2` | 效果 amplifier | :348-365 / :396-413(**工作区改用 `HostileTargets.isHostile(attacker,target)`**) |
| 23 | 上班族 `PADMAN_ATK_BONUS` + 破防标志 | 数据组件 / `ctx.baseDice==6` | :368-382 / :416-430 |
| 24 | 肾上腺素 50% 血线 +3/+8(汲取期间无条件) | 当前生命 | :385-392 / :433-440 |
| 25/26/27 | 原初核心赋能层 +1 / 电磁炮充能≥6 +5 / 磨刀石 50% 血 +4 | 附件/充能 | :395-413 / :443-461 |

**防御修饰器(1 个,`DEFENSE_MODIFIERS`,`neo [HEAD]:419-431`)**:仅防御牌掷骰,写
`ctx.defenseCardSum`;`modifierDefense` 恒 0。效果牌/立牌/筹码的防御力**不走本表**,统一折算为
真实护甲(`1 防御力 = 2 护甲`)。

### 1.2 `setDefenseArmorBonus` 全部键 × 四条路径对称性

注册/注销入口唯一:`DiceCombatModifiers.setDefenseArmorBonus(player, key, points)`
(`neo [HEAD]:87-102` / `forge [HEAD]:88-105`),`points ≤ 0` 即 `removeModifier`;
`neo` 用 `ResourceLocation.fromNamespaceAndPath(MODID, key)`,`forge` 用
`UUID.nameUUIDFromBytes(MODID+":"+key)` —— **两版本键标识都稳定**(名字/UUID 都可由 key 唯一推出,
不随会话/实例变化)。

| 键 | 写入点 | 卸下注销 | 死亡 | 到期/状态失效 | 对称 |
|---|---|---|---|---|---|
| `papara_def_armor` | `PaparaSignItem[HEAD]:45` | `:52 clearSignData` | 实体重建(不残留) | `curioTick` 每 tick 按血线重算 → 3/0 | ✅ |
| `padman_def_armor` | `PadmanSignItem[HEAD]:30` | `:53` | 同上 | `curioTick` 每 tick | ✅ |
| `nancy_lu_def_armor` | `NancyLuSignItem[HEAD]:85` | `:103` | 同上 | `curioTick` 每 tick | ✅ |
| `moses_weakness_armor` | `MosesSignItem:49` | `:90` | 同上 | 赐福结束 `:174` 重算 | ✅ |
| `jasmine_def_armor` | `JasmineSignItem[HEAD]:43` | `:55` | 同上 | `curioTick` 每 tick | ✅ |
| `fen_def_armor` | `FenSignItem:146-147` | —(无需) | — | **玩家级 `tick()` 每 20t 按 `isEquipped&&recharge>0` 重算** | ✅(自愈) |
| `revenge_halberd_def_armor` | `RevengeHalberdChipItem:85` | `onChipUnequip:91` | — | `PlayerTickEvents` 每 tick 重算 | ✅(自愈) |
| `primordial_core_def_armor` | `PrimordialCoreChipItem:46` | `onChipUnequip:51` | — | 每 tick 重算 | ✅(自愈) |
| `adrenaline_def_armor3` / `…8` | `AdrenalineChipItem:66-67`(`"adrenaline_def_armor"+bonus`) | `onChipUnequip:71-73` | 实体重建 | 无 | ⚠️ 见 P2-C10 |
| `dice_nether_star_dice_armor` | `NetherStarDiceItem[HEAD]:74` | `onUnequip[HEAD]:67` | 实体重建 | `curioTick` 每 20t 重算 | ✅ |

> **同名键被 `set` 覆盖的可能(§B5)**:全表 10+ 个键**互不相同**(含肾上腺素按 `bonus` 后缀区分两档),
> 未发现两个系统写同一 key;同一 key 的多个写入点都源自同一状态量,故 `set` 覆盖不会丢别人的数值。
> 该检查点在本仓**证伪**(无缺陷)。
> 强化修饰器键:青之诅咒 `blue_curse_armor/-toughness`、能量过载 `jasmine_sweep_speed/-armor`、
> 岿然不动 `unwavering_armor`(curve 重载,`8.0*(amp+1)`);筹码属性键由
> `BaseChipItem.attributeModifierId(suffix)` 按注册名派生 → 同名冲突结构性不存在。

### 1.3 法伤链 `SpellDamageRegistry`

- **聚合顺序**:`DamageEffectCardHandler[HEAD]:49-54` 按**注册顺序**单遍遍历,
  `bonus = modifier.apply(ctx, bonus)`;11 个内置修饰器**全部是 `bonus + X`(纯加法)**
  → 求和与顺序**无关**;唯一顺序敏感处是 `onHit` 副作用(活体书页施标记 → 忍术飞镖/贯穿之铳
  读的是 `isActive`/`apply` 阶段的**旧**标记层数,故同样与顺序无关)。
- **哪一部分吃护甲**:聚合出的 `bonus` **整体**走
  `target.hurt(ModDamageTypes.trueDamage(level, player), bonus)`
  (`neo DamageEffectCardHandler:65-66` / `forge :62-63`)→ 真伤只登记在
  `minecraft:bypasses_armor` ⇒ **这份加成不吃护甲/韧性,但吃抗性提升与保护附魔**
  (见 `damage/ModDamageTypes.java:19-29` 注释);武器/弹射物本体仍走原版全链路(吃护甲+保护+抗性)。
- **范围波及**两处,基准**不同**:
  - 定向爆破 `SpellDamageRegistry:232-266`:半径 6、敌对过滤、
    `aoeDamage = (int) Math.max(1.0, 5 + effectCardDamageBonus(attacker))[:254]`
    —— 用**常量 5 + 忍者/书签加成**,与主目标那份**聚合 bonus 不等**(少算忍术飞镖的标记加成、
    贯穿之铳的目标防御加成);真伤 + `aoeProcessing` 闸门。
  - 电击手套 `:352-386`:半径 3、`float total = ctx.event.getNewDamage()[:365@neo]` /
    `ctx.event.getAmount()[:365@forge]` → **基准跨版本不同**(P2-C7)。
- **加成以嵌套 `hurt` 结算**:它发生在原伤害事件的 `LivingDamageEvent.Pre` 内,会**再次**触发
  目标侧的整条 `LivingDamageEvent.Pre` 链(末影骰 ×1.4、吸血鬼受击回血、安全气囊/磨刀石、
  `BaseSignItem.invokeHurtHooks`)。改动中已新增未跟踪文件
  `src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json`
  (`values: ["astral_dice:true_damage"]`)处理无敌帧差额吞伤(§B6/A5),本报告只记录不重复判定。

---

## 2. 结论

### P2-C1 骰战「覆盖式写入」丢弃受击侧 ×1.4(末影骰雨中/水下 +40%)

```text
ID:P2-C1  严重度:high(改动前已证实;改动后已修,降为信息)
核验:证实(HEAD)/ 已修(WT)
现象:[HEAD] PvP 中,目标佩戴末影骰子且处于雨中/水下时被"赐福+骰子+近战"的玩家命中,
      雨中/水下 +40% 完全不生效(伤害与不带末影骰子时一致)。
顺序依赖:LivingDamageEvent.Pre 同事件内 HIGH(EnderDiceHandler)先于 NORMAL(DiceCombatEvents);
      后者 setNewDamage(finalDmg) 整段覆写,不读旧值 ⇒ 前置倍率被丢。
      反转(骰战改 HIGH / 末影改 LOW)会把 ×1.4 乘到骰战最终伤害上,但会连带把该值再喂给
      后续 LOW/LOWEST(狂暴/虚弱印记/气囊)链。
可达性:可达。最小复现:双人 PvP;A 持骰子+近战武器;让 B 佩戴末影骰子并站雨中;
      A 打出触发骰神赐福的一击,记录 B 掉血量;B 卸下末影骰子后重复 —— [HEAD] 两次相同。
```

**证据 A(neoforge-1.21.1)**

`combat/DiceCombatEvents.java:604 [HEAD]`(工作区 `:616`):
```java
        event.setNewDamage((float) finalDmg);
```
`event/EnderDiceHandler.java:134-142 [HEAD]`(工作区 `:147-153`):
```java
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        ...
        // 雨(含被雨淋到)/水下/气泡柱都满足"雨中或水下"
        if (!player.isInWaterRainOrBubble()) return;
        event.setNewDamage(event.getNewDamage() * RAIN_WATER_DAMAGE_MULTIPLIER);
```
`combat/DiceCombatEvents.java:184-185 [HEAD]`(无优先级 = NORMAL):
```java
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
```
`event/EnderDiceHandler.java:48`: `public static final float RAIN_WATER_DAMAGE_MULTIPLIER = 1.4F;`
「HIGH 早于 NORMAL」在本仓内另有书面依据:`event/ChipDamageHandler.java:32-34` 注释
「本处理器使用 EventPriority.LOWEST,即排在所有同事件伤害修正(雨中/水下放大、狂暴/虚弱印记加成等)之后执行」。

**证据 B(forge-1.20.1)**

`event/EnderDiceHandler.java:135-143 [HEAD]`(工作区 `:147-153`)——**另一条事件链**:
```java
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        ...
        event.setAmount(event.getAmount() * RAIN_WATER_DAMAGE_MULTIPLIER);
```
`combat/DiceCombatEvents.java:601 [HEAD]`(工作区 `:613`):
```java
        event.setAmount((float) finalDmg);
```
`LivingHurtEvent` 在护甲/附魔/吸收**之前**(反编译 `forge-1.20.1-47.4.10-sources.jar`
`net/minecraft/world/entity/LivingEntity.java:1665` `onLivingHurt` → `:1667` 护甲 → `:1668` 魔法吸收
→ `:1669` 吸收扣除 → `:1680` `onLivingDamage`),而骰战在 `LivingDamageEvent` 整段覆写 ⇒ 同样丢弃 ×1.4。

**改动后(WT,34 文件批次的 A3 修复)**:
新增 `DiceCombatModifiers.VictimDamageModifier` 注册表(`neo [WT]:87-127` / `forge [WT]:90-129`);
骰战在覆盖写入**之前**重消费一次:
`DiceCombatEvents.java:614 [WT]` / `:611 [WT]`
```java
        finalDmg = DiceCombatModifiers.applyVictimDamageModifiers(target, source, finalDmg);
```
末影骰 HIGH 监听器改为消费同一注册表(`EnderDiceHandler.java:152 [WT]`),
内置谓词 `DiceCombatModifiers[WT]:487-496`(neo)/`:490-499`(forge)。
⇒ **P2-C1 在工作区已被修复**(两处消费不会叠加:骰战整段覆写数值)。**但见 P2-C8 的副产物**。

修法:已在改动中批次实现;建议保留「受击侧修饰器注册表 + 骰战覆盖前重消费」这一形状,勿回退为常数相乘。

---

### P2-C2 同一处覆盖**同时丢弃抗性提升与保护附魔**的减免(骰战伤害完全不吃抗性/保护)

```text
ID:P2-C2  严重度:high(head 与 WT 均存在)
核验:证实(两侧代码 + 反编译源码链已核实)
现象:骰神赐福近战命中时,受伤方的「抗性提升」(含岿然不动牌的 抗性提升 II、信标/药水)与
      「保护附魔」对这次伤害**完全不生效**;而 tooltip/手册把「抗性提升 II」列为岿然不动的收益之一。
顺序依赖:原版在容器上先记 ARMOR(1787)→ MOB_EFFECTS/ENCHANTMENTS(1788)三段减免,
      再派发 LivingDamageEvent.Pre(1789);骰战在此以 setNewDamage(finalDmg) 把 newDamage
      整段替换 ⇒ 已记的三段减免只剩统计元数据,不再参与 `setHealth` 的扣血值。
      反转(骰战改为 setNewDamage(旧值 - 骰战减伤)而非覆写)会改变全部骰战数值口径。
可达性:可达。最小复现:给目标喝「抗性提升 II」或穿保护 IV 全套,攻击方触发骰神赐福近战命中,
      对比目标无抗性/无保护附魔时的掉血量 —— 两者相同即为证实(A/B 两版本均可复现)。
```

**证据 A**:`combat/DiceCombatEvents.java:604 [HEAD]`(WT `:616`)`event.setNewDamage((float) finalDmg);`
+ 反编译 `neoforge-21.1.235-sources.jar` `net/minecraft/world/entity/LivingEntity.java`:
```text
1787: this.damageContainers.peek().setReduction(...Reduction.ARMOR, this.damageContainers.peek().getNewDamage() - this.getDamageAfterArmorAbsorb(...));
1788: this.getDamageAfterMagicAbsorb(damageSource, this.damageContainers.peek().getNewDamage());
1789: float damage = net.neoforged.neoforge.common.CommonHooks.onLivingDamagePre(this, this.damageContainers.peek());
```
`net/neoforged/neoforge/common/damagesource/DamageContainer.java:163-167`:
```java
    public void setReduction(Reduction reduction, float amount) {
        float modifiedReduction = modifyReduction(reduction, amount);
        this.reductions.put(reduction, modifiedReduction);
        this.newDamage -= modifiedReduction;
    }
```
`net/neoforged/neoforge/common/CommonHooks.java:319-321`:`return NeoForge.EVENT_BUS.post(new LivingDamageEvent.Pre(entity, container)).getNewDamage();`

**证据 B**:`forge .../LivingEntity.java:1663-1683`:
```text
1665: damageAmount = net.minecraftforge.common.ForgeHooks.onLivingHurt(this, damageSource, damageAmount);
1667: damageAmount = this.getDamageAfterArmorAbsorb(damageSource, damageAmount);
1668: damageAmount = this.getDamageAfterMagicAbsorb(damageSource, damageAmount);
1669: float f1 = Math.max(damageAmount - this.getAbsorptionAmount(), 0.0F);
1680: f1 = net.minecraftforge.common.ForgeHooks.onLivingDamage(this, damageSource, f1);
1683: this.setHealth(this.getHealth() - f1);
```
`combat/DiceCombatEvents.java:601 [HEAD]`(WT `:613`)`event.setAmount((float) finalDmg);`

修法:需用户先定夺口径。两种方向:① 承认"骰战接管全部减伤",则必须同步 tooltip/手册口径
(岿然不动的抗性提升 II 对骰战无效)——并考虑把 `抗性提升/保护附魔` 折进 `defensePower`;
② 改为增量式(`finalDmg = 现有 newDamage - 骰战减伤`),但那会推翻现有骰战数值与显示口径。
**建议先定夺口径再动代码**;至少要在 §B 报告里显式声明"骰战不吃抗性/保护"。

---

### P2-C3 「加急加快 → 主动冷却 −最大冷却的 50%」实为**常量 −90 秒**,枪匠场景过度减免甚至直接归零

```text
ID:P2-C3  严重度:high(HEAD 已证实)/ 改动后已修(路线 A),残留 1 项口径待确认
核验:证实(HEAD)/ 已修(WT)
现象:[HEAD] 文案写"减少最大冷却的 50%",实现是"减去常量 3600/2 = 1800t(90s)"。
      · 枪匠(实际最大冷却 120s=2400t):一次减掉 90s,只剩 30s(应为 60s,多减 30s);
      · 枪匠 + 诡异骰子 + 充能(实际 48s=960t):cdEnd - 1800 < now ⇒ 被 max(now,…) 夹成 now
        ⇒ **冷却直接归零 = 免费立即完成**(整段冷却被白送)。
顺序依赖:[HEAD] 减免量与被减的冷却值来自**两个不同的基准**(常量 180s vs 当前实际最大冷却),
      且 `Math.max(now, …)` 把"过度减免"静默变成"立即完成";反转(先钳制再减/按剩余比例减)会改变数值。
可达性:可达。最小复现:① 装备枪匠立牌 → 按主动(进入 30s 待命)→ 命中目标释放(冷却 now+2400)
      → 立刻使用「加急加快」牌 → 观察 ActionBar/再次按主动的可用时间 ≈30s 而非 60s;
      ② 再加诡异骰子 + ≥1 层充能重复一次 → 应可直接再次按主动。
```

**证据 A**:`item/sign/JasmineSignItem.java:82-92`,核心 `:90`:
```java
    // 使用「加急加快」效果牌后调用:立牌主动技能冷却立即减少最大冷却的 50%
    public static void onExpressDeliveryUsed(Player player) {
        ...
            ModAttachments.setSignActiveCooldownEnd(player,
                    Math.max(now, cdEnd - GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS / 2));
```
`component/GameplayConstants.java:43-45`:`SIGN_ACTIVE_COOLDOWN_SECONDS = 180;` → `SIGN_ACTIVE_COOLDOWN_TICKS = 3600;`
`item/sign/MosesSignItem.java:117-125`:`ACTIVE_COOLDOWN_SECONDS = 120;` → `? 2400` → 诡异 `:122 ticks/2=1200`
→ 充能 `:124 ChargeManager.cooldownTicks → ceil(1200*0.8)=960`
`item/ChargeManager.java:64-68`:`return Math.max(1, (long) Math.ceil(reduced));`
文案:`assets/astral_dice/lang/zh_cn.json:559` `…使用「加急加快」后，主动技能冷却立即减少最大冷却的 §e50%%§r`;
`en_us.json:559` `…reduces active skill's max cooldown by §e50%%§r`。

**证据 B**:`forge .../item/sign/JasmineSignItem.java:84-91`,核心 `:91` 与 A 同文同值
(`Math.max(now, cdEnd - GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS / 2)`);
`forge .../MosesSignItem.java:117-124` 与 A 同构。**两侧同缺陷,无单侧差异。**

**改动后(WT,快照后追加核对——该批次已按"路线 A"修复)**:
`item/sign/JasmineSignItem.java:82-99 [WT]`(forge `:83-100 [WT]`)改为取"起冷却时记录的最大冷却":
```java
    // 使用「加急加快」效果牌后调用:立牌主动技能冷却立即减少最大冷却的 50%
    // (路线 A:基准取起冷却时记录的 sign_active_max_cooldown,记录缺失时兜底回退旧行为——
    //  按通用常量 SIGN_ACTIVE_COOLDOWN_TICKS 计算)
    public static void onExpressDeliveryUsed(Player player) {
        ...
        if (cdEnd > now) {
            long maxCooldown = ModAttachments.getSignActiveMaxCooldown(player);
            if (maxCooldown <= 0) {
                maxCooldown = GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;
            }
            ModAttachments.setSignActiveCooldownEnd(player,
                    Math.max(now, cdEnd - maxCooldown / 2));
        }
    }
```
⇒ 枪匠 + 加急加快 = 2400 − 1200 = 1200(60s),与文案一致;不再出现"免费立即完成"。
**残留(待口径确认)**:记录缺失时仍回退旧行为(通用 180s);且 `Math.max(now, …)` 允许
"剩余 < max/2"时直接归零——若玩家持有两张「加急加快」(忍者复制器可复制已用效果牌 / 看板随机返还),
第二次使用仍会把剩余冷却归零(数学上与"每张减 max/2"一致,但等价于"该次主动免费")。
建议明确该口径并写进文案。

修法:已在改动中批次实现;两者共用同一附件键 `sign_active_max_cooldown`
(neo `component/ModAttachments.java:416-426` / forge `:386-394`)。
**写入点配对已核对(7 个 `SIGN_ACTIVE_COOLDOWN_END` 写入方)**:
4 个"开始冷却"入口(`BaseSignItem:122-124 [WT]` + `DiceCombatEvents:245/248`、`269/272`、`286/289 [WT]`)
**全部**同时写 max;3 个"减免/清零"入口(`KomachiSignItem:138` 剩余×0.7、`LuluSignItem:55-56` 绝对 −200t、
`CurrentCoreChipItem:93` 归零)**不写 max**——语义正确(本次冷却总量不变),无需修改。

---

### P2-C4 「命运的指引 → 主动冷却减半」的基准取自**通用最大冷却**(180s),与实际写入的冷却不一致

```text
ID:P2-C4  严重度:high(HEAD 已证实)/ 改动后已修(路线 A)
核验:证实(HEAD)/ 已修(WT)
现象:[HEAD] 使用命运的指引后,减免量恒按 WeirdDiceHandler.signCooldownTicks(player)/2 计算,
      与被减的那条冷却(枪匠 120s)无关 ⇒ 枪匠场景一次减 90s 而非 60s;更严重的是
      **基准随"当前装备"浮动**:若该玩家此刻又戴了诡异骰子/有充能,减免量变大,
      可在剩余时间不足时把冷却夹成 0(立即完成)。
顺序依赖:[HEAD] 减免量 = f(当前装备) 而不是 f(开始冷却时的装备);`Math.max(0, remaining - reduction)`
      把过度减免静默变成"立即完成"。
可达性:可达。最小复现:装备枪匠 → 释放主动(冷却 2400t)→ 立刻用命运的指引 → 再次按主动的
      可用时间 ≈30s(应为 60s);若期间再获得充能,减免量升至 720t(1440/2),
      在剩余 <720t 时使用则可直接再次释放。
```

**证据 A**:`item/card/FateGuidanceCardItem.java:62-71 [HEAD]`:
```java
    private static void reduceActiveSkillCooldown(Player player) {
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        if (cdEnd > 0) {
            long now = player.level().getGameTime();
            long remaining = cdEnd - now;
            long maxCooldown = com.merlinkitsune.astral_dice.event.WeirdDiceHandler.signCooldownTicks(player);
            long reduction = maxCooldown / 2;
            ModAttachments.setSignActiveCooldownEnd(player, now + Math.max(0, remaining - reduction));
        }
    }
```
`event/WeirdDiceHandler.java:36-42`:`int ticks = GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;` → 诡异 `ticks/2`
→ `ChargeManager.cooldownTicks`(即 180s / 90s / 72s 三档,**永不为枪匠的 120s**)。
文案:`lang/zh_cn.json:434` `2. 立刻减少主动技能最大冷却倒计时一半的时间`;
`lang/en_us.json:434` `2. Instantly halve the current maximum active skill cooldown countdown`。

**证据 B**:`forge .../item/card/FateGuidanceCardItem.java:61-70 [HEAD]` —— 逐字同构
(`:66 maxCooldown = WeirdDiceHandler.signCooldownTicks(player); :67 reduction = maxCooldown / 2;`)。

**改动后(WT,快照后追加核对——已按"路线 A"修复)**:`item/card/FateGuidanceCardItem.java:60-76 [WT]`
(forge `:59-75 [WT]`):
```java
    // 主动技能冷却时间减半(实时功能):冷却中则从剩余时间中扣除「本次冷却实际使用的最大冷却时长」的 50%
    // (路线 A:减少量 = 起冷却时记录的 sign_active_max_cooldown ÷ 2,例:180 秒冷却减 90 秒、枪匠 120 秒冷却减 60 秒;
    //  记录缺失时兜底回退旧行为——按通用最大值重算;玩家级冷却,不受立牌装卸影响)
    private static void reduceActiveSkillCooldown(Player player) {
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        long now = player.level().getGameTime();
        if (cdEnd > now) {
            long remaining = cdEnd - now;
            long maxCooldown = ModAttachments.getSignActiveMaxCooldown(player);
            if (maxCooldown <= 0) {
                maxCooldown = com.merlinkitsune.astral_dice.event.WeirdDiceHandler.signCooldownTicks(player);
            }
            long reduction = maxCooldown / 2;
            ModAttachments.setSignActiveCooldownEnd(player, now + Math.max(0, remaining - reduction));
        }
    }
```
⇒ 枪匠 + 命运指引 = 2400 − 1200 = 1200(60s),与文案一致;基准不再随"当前装备"漂移。
**依赖前提(本次已核验)**:所有"开始冷却"入口都要写 `sign_active_max_cooldown` ——
`BaseSignItem:122-124 [WT]` 与 `DiceCombatEvents:245/248、269/272、286/289 [WT]` 共 4 处**全部配套**;
3 个"减免/清零"入口不写(语义正确)。唯一缺口是"记录缺失时回退通用 180s",对老存档/漏写路径仍会过度减免。
附带修正:`cdEnd > 0` 改为 `cdEnd > now`(原先"已过期但非 0"的陈旧值也会走进减免分支,现在不会)。

修法:已实现;建议补一条回归用例(枪匠 + 命运指引 → 剩余应为 60s)并确认"两张加急加快是否允许把冷却直接归零"的口径。

---

### P2-C5 「能量过载 −30% 护甲」在高护甲配装下**完全无效**(钳制发生在乘法之后,且上限 20 < 基准值)

```text
ID:P2-C5  严重度:medium(未修)
核验:证实(代码 + 反编译源码)
现象:文案把「护甲值 −30%」写成能量过载的代价。但当玩家的 ADD_VALUE 总护甲 ≥ 28.5714 时,
      ×0.7 后的护甲仍 ≥ 20,而骰战 `min(getArmorValue(), 20)` 与原版 `CombatRules` 的上限 20
      都在乘法**之后** ⇒ 该 −30% 对骰战防御力与原版减伤**双双为 0 效果**,代价凭空消失。
顺序依赖:乘(attribute 层,`MULTIPLY_TOTAL`)→ 截断/钳制(`getArmorValue()`、`min(…,20)`)。
      反转(先钳到 20 再乘 0.7)会让 −30% 永远有效,但会改变全部高护甲配装的数值。
可达性:可达。最小复现:全套下界合金(20 护甲)+ 摩托头盔-高级(+12 护甲)= 32;
      开能量过载前后各挨同一次骰战攻击 / 同一段原版伤害 —— 掉血完全相同(2 + 20/2 = 12 防御力不变)。
```

**证据 A**:`effect/JasmineSweepEffect.java:20-22`:
```java
        this.addAttributeModifier(Attributes.ARMOR,
                ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "jasmine_sweep_armor"),
                -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
```
`combat/DiceCombatEvents.java:563-565 [HEAD]`(WT `:570-572`):
```java
                double rawArmor = Math.min(target.getArmorValue(), 20);
                double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
                double effectiveArmor = Math.max(0, Math.min(rawArmor + modifierDefense * 2.0, 20));
```
反编译 `neoforge-21.1.235-sources.jar` `net/minecraft/world/entity/ai/attributes/Attributes.java:16`
`"generic.armor", new RangedAttribute(..., 0.0, 0.0, 30.0)`;`net/minecraft/world/damagesource/CombatRules.java:16-18`
`float f1 = Mth.clamp(armorValue - damage / f, armorValue * 0.2F, 20.0F);`
文案:`lang/zh_cn.json:557`(tooltip)/`:117`(手册)`护甲值 §e-30%%§7`。

**证据 B**:`forge .../effect/JasmineSweepEffect.java` 使用同一操作语义
(`addAttributeModifier(Attributes.ARMOR, "<uuid>", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL)`);
`forge .../combat/DiceCombatEvents.java:561-563 [HEAD]` 同构;反编译
`forge-1.20.1-...-sources.jar Materials/` 同名文件:`Attributes.java:45` ARMOR max 30.0、
`CombatRules.java:12-15` 同为 `clamp(..., totalArmor * 0.2F, 20.0F)`。**两侧同缺陷。**

修法:最小改动是让文案与语义一致——建议把 能量过载 的减益改为**降低防御力点数**
(经 `setDefenseArmorBonus` 的负值路径,或在 `defensePower` 上扣绝对点),
避免被 20 上限吞掉;或把乘数作用在"钳制后的有效护甲"上。**需用户定夺口径**。

---

### P2-C6 「青之诅咒 −20%」与「能量过载 −30%」是**顺序相乘**(0.8×0.7=0.56),且都乘在"含防御力折算的总和"上

```text
ID:P2-C6  严重度:medium(未修)
核验:证实(语义已核实;是否需要修正待定夺)
现象:两个 MULTIPLY_TOTAL 不是相加(−50%)而是相乘(−44%);更要紧的是它们乘的是
      「base + 全部 ADD_VALUE(含 1 防御力=2 护甲的折算值)」⇒ 在两种效果下
      "1 防御力 = 2 护甲值"这个规范值不再成立(变 1.12~1.6 护甲),而 tooltip 仍按 2 折算显示。
顺序依赖:ADD_VALUE 求和 → 逐个 MULTIPLY_TOTAL 相乘 → sanitizeValue(属性上限)。
      两个乘数的迭代顺序由 Set 决定,但乘法可交换,结果与顺序无关。
可达性:可达。最小复现:装备诅咒之剑筹码(-20% 护甲)+ 开能量过载;打开 F3 或骰战界面,
      对比显示的防御力 与 按 `2 + 护甲/2` 手算值 —— 会看到 −44% 而非 ×2 折算。
```

**证据 A**:`effect/BlueCurseEffect.java:18-23`:
```java
        this.addAttributeModifier(Attributes.ARMOR,
                ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "blue_curse_armor"),
                -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.addAttributeModifier(Attributes.ARMOR_TOUGHNESS,
                ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "blue_curse_toughness"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
```
+ `JasmineSweepEffect:20-22`(−0.3)+ 反编译 `AttributeInstance.java:145-163`:
```text
158:        for (AttributeModifier attributemodifier2 : this.getModifiersOrEmpty(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)) {
159:            d1 *= 1.0 + attributemodifier2.amount();
160:        }
```
+ `combat/DiceCombatModifiers.java:87-102 [HEAD]` 用 `Operation.ADD_VALUE` 把防御力折成护甲
(∴ 折出来的护甲也吃这两个乘数)。
文案:`lang/zh_cn.json:464` `§c青之诅咒§7：护甲值 §e-20%%§7，盔甲韧性 §e-100%%§7（护甲值向下取整）`。

**证据 B**:`forge .../effect/BlueCurseEffect.java`(同操作语义 `MULTIPLY_TOTAL`)+
反编译 `forge .../AttributeInstance.java:140-158`(同为 `d1 *= 1.0D + amount`)。
`forge .../DiceCombatModifiers.java:88-105 [HEAD]` 同 ADD_VALUE 折算。**两侧一致。**

修法:若希望"防御力折算恒为 2 护甲",应把折算值改为不受乘数影响的通道
(例如在 `defensePower` 里单独加 `防御点数` 而不是挂 ARMOR 属性);
若接受当前语义,则只需在文案里明确"护甲值(含防御力折算)统一乘算"。**需用户定夺口径**。

---

### P2-C7 电击手套 AOE 的**基准跨版本不同**(1.21.1 = 护甲后,1.20.1 = 护甲前)

```text
ID:P2-C7  严重度:medium(单侧差异,未修)
核验:证实
现象:同一个"本次远程/魔法伤害同时命中 3 格内其他敌对目标"的效果,1.21.1 复制的数值是
      **护甲/附魔/抗性结算之后**的伤害,1.20.1 复制的是**护甲之前**的原始数值 ⇒
      高护甲目标身上 1.20.1 的 AOE 明显更高(可能高 1.5~1.8 倍)。
顺序依赖:1.21.1 处理器挂在 LivingDamageEvent.Pre(容器已被 1787/1788 扣减);
      1.20.1 处理器挂在 LivingHurtEvent(LivingEntity.java:1665,护甲前)。
可达性:可达。最小复现:同一存档两侧对照——给目标穿全套下界合金,用「对怪激光/板砖」等
      效果牌命中主目标,记录 3 格内第二只敌对目标受到的 AOE 数字;两版本数值不等即证实。
```

**证据 A**:`combat/SpellDamageRegistry.java:364-365`:
```java
            public void onHit(SpellDamageContext ctx, double bonus) {
                float total = ctx.event.getNewDamage();
```
`combat/SpellDamageContext.java:26`: `public final LivingDamageEvent.Pre event;`

**证据 B**:`forge .../combat/SpellDamageRegistry.java:364-365`:
```java
            public void onHit(SpellDamageContext ctx, double bonus) {
                float total = ctx.event.getAmount();
```
`forge .../combat/SpellDamageContext.java:26`: `public final LivingHurtEvent event;`
(反编译 `forge .../LivingEntity.java:1665` 证明 `LivingHurtEvent` 在护甲之前)。

修法:统一语义即可 —— 若想要"护甲前的同伤害",1.21.1 应改用 `event.getOriginalAmount()`/
`DamageContainer.getOriginalDamage()`;若想要"护甲后",1.20.1 应改挂 `LivingDamageEvent`。
**需用户定夺想要的语义**(会被写入 CHANGELOG,故必须二选一)。

---

### P2-C8 末影骰 ×1.4 与七咒 `dice_curse_ratio` 的交叉:**1.20.1 在改动后变成双重放大**

```text
ID:P2-C8  严重度:high(改动后新引入的回归,仅 1.20.1)
核验:证实(代码路径)/ 建议实机确认
现象:[改动前] 1.20.1 的 ×1.4 会先被"七咒倍率捕获"吸收进 dice_curse_ratio,再乘进骰战最终伤害
      (即"没丢,但被算进了诅咒因子");1.21.1 的捕获点在 LivingIncomingDamageEvent,
      早于 ×1.4,故 1.21.1 丢、1.20.1 不丢 ⇒ 单侧差异。
      [改动后] 两版本都新增了"骰战覆盖前重消费受击侧修饰器";1.20.1 的 ratio 里**仍然**含着
      ×1.4,于是 ×1.4 被应用**两次**(ratio 一次 + 受击侧修饰器一次)⇒ 1.4×1.4 = **1.96 倍**。
顺序依赖:1.20.1 捕获 = LivingHurtEvent(LOWEST)读 current/original;末影骰 = LivingHurtEvent(HIGH)
      ⇒ 捕获**晚于** ×1.4,ratio 被污染。1.21.1 捕获 = LivingIncomingDamageEvent(LOWEST),
      ×1.4 在其后的 LivingDamageEvent.Pre ⇒ 不污染。
可达性:可达(需"受伤方同时佩戴七咒之戒 + 末影骰子 + 处于雨中/水下"且被赐福近战命中)。
      最小复现:目标戴七咒之戒+末影骰子站雨中,攻击方触发骰神赐福命中;
      对比"目标不站雨"的同一击 —— 改动后 1.20.1 的比值应 ≈1.96(1.21.1 应为 ≈1.4)。
```

**证据 A**:`item/card/FateGuidanceCardItem.java:85-97 [HEAD]`(捕获点在护甲**之前**的入站事件):
```java
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onCurseMitigation(
            net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        ...
        float ratio = current > original ? current / original : 1.0f;
```
`combat/DiceCombatEvents.java:926-936 [HEAD]`(WT `:939-949`)因子:
```java
                if (ratio > 1.0f) {
                    damage *= FateGuidanceCardItem.isFateGuidanceActive(attacker) ? (1.0 + (ratio - 1.0) * 0.5) : ratio;
                }
```

**证据 B**:`forge .../item/card/FateGuidanceCardItem.java:86-106 [HEAD]`:
```java
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onCurseOriginalCapture(LivingAttackEvent event) { ... }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onCurseMitigation(LivingHurtEvent event) {
        ...
        float current = event.getAmount();
        float ratio = current > original && original > 0 ? current / original : 1.0f;
```
同文件 `:135-143 [HEAD]` 的 ×1.4 就在**同一个 `LivingHurtEvent`** 上以 HIGH 先执行 ⇒ `current` 含 1.4。
`forge .../combat/DiceCombatEvents.java:604-611 [WT]`:
```java
        for (DiceCombatFactor factor : EXTERNAL_DAMAGE_FACTORS) { ... }
        finalDmg = DiceCombatModifiers.applyVictimDamageModifiers(target, source, finalDmg);
```
⇒ 1.20.1 同一份 ×1.4 进了两个乘数。

修法:1.20.1 的捕获必须**排除受击侧修饰器**再算 ratio:在 `onCurseMitigation` 里
`current = event.getAmount() / 受击侧倍率`(或把捕获点前移到 `LivingAttackEvent` 的 LOWEST 之后
但仍在末影骰之前——不可行,故建议直接反算),或者在 `applyVictimDamageModifiers` 之前把
`dice_curse_ratio` 里的 ×1.4 除掉。**建议改动中批次一并收敛该回归**(它是新引入的)。

---

### P2-C9 反击路径的七咒 −40% 作用在**全量**上,正常路径只作用在「骰点+卡牌」上

```text
ID:P2-C9  严重度:medium(未修)
核验:证实(两条链路的基准不同;是否算缺陷需按文案口径定夺)
现象:正常骰战:`−40%` 只作用于 `baseDice + attackCardSum`;
      反击(枪匠破绽闪避反击 / 肉弹战车嘲讽反击):`−40%` 作用于
      `武器基础伤害 + 卡牌 + 全部攻击修饰器`(不含骰点),等于把"骰子伤害加成降低 40%"
      扩大成"反击总伤害降低 40%"。
顺序依赖:两条链路各自独立计算,无共享入口 ⇒ 不存在"谁覆盖谁",但**基准口径分叉**。
可达性:可达。最小复现:戴七咒之戒 + 枪匠,让带破绽的敌人攻击你触发闪避反击,
      对比"不戴七咒"的同一反击数值 —— 差值包含武器基础伤害的 40%。
```

**证据 A**:`combat/DiceCombatEvents.java:486 [HEAD]`(正常路径)
```java
        double diceAttackBonus = applyCurseToDicePoints(player, baseDice + attackCardSum);
```
`combat/DiceCombatEvents.java:1188-1190 [HEAD]`(WT `:1201-1203`,反击路径):
```java
        double total = weaponBase + ctx.attackCardSum + modifiersSum;
        // 七咒减益作用于反击总伤害(含修正物:启示之证/倒转之启/恩惠之典/护法爆发)
        total = applyCurseToDicePoints(player, total);
```
`combat/DiceCombatEvents.java:166-181` 是 `applyCurseToDicePoints` 本体(`cursePenalty = 0.4`)。

**证据 B**:`forge .../DiceCombatEvents.java:483 [HEAD]` 与 `:1200 [HEAD]`,与 A 逐字同构。

修法:若口径应为"骰子伤害加成",反击路径应改为
`total = weaponBase + modifiersSum + applyCurseToDicePoints(player, ctx.attackCardSum)`。
**需用户定夺**(手册未写反击的七咒口径)。

---

### P2-C10 护甲/移速修饰器的注销遗漏:只挂在 `curioTick` + `onChipUnequip`,**Curios 自身移除路径不回调**

```text
ID:P2-C10  严重度:medium
核验:部分证实(代码只有两个清除点);Curios 具体移除路径需实机确认 → 标注"未核验"的那一半
现象:肾上腺素筹码的 `adrenaline_def_armor<N>`(护甲)与 能量回收 筹码的 `charge_speed`(移速)
      只在「佩戴期间 tick」与「玩家有意卸下」两处被清除;若饰品由 Curios 自己移出槽位
      (死亡掉落 / 骰子换装导致筹码栏收缩而溢出掉落),**没有第三处**兜底 ⇒ 玩家不再持有该筹码,
      却继续吃 +3/+8 护甲(或 +5% 移速),直到重登/重生(实体重建才清)。
顺序依赖:清除入口 = `curioTick`(需仍在槽位)+ `onUnequip`(需 Curios 回调);
      两者都不成立时无任何重算 ⇒ 残留是"永久到实体重建"。
可达性:已证实(代码层:除这两处无其它写入点);跨槽位收缩路径**未核验**。
      最小实机步骤:① 装 T4 下界之星骰子(筹码栏最多)+ 肾上腺素-高效筹码(半血触发,吃 +16 护甲);
      ② 半血触发后把骰子换成基础骰子(筹码栏收缩 → 筹码被 Curios 弹出);
      ③ 观察 F3 护甲值是否仍带那份加成、`/attribute <你> minecraft:generic.armor get` 是否仍含修饰器;
      ④ 另一组:直接死亡(keepInventory=false)后立刻看尸体/重生前的数值,以及重生后是否清零。
```

**证据 A**:`item/chip/AdrenalineChipItem.java:61-74`:
```java
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        ...
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(
                player, "adrenaline_def_armor" + bonus, isLowHp(player) ? bonus : 0);
    }
    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(
                player, "adrenaline_def_armor" + bonus, 0);
    }
```
`item/chip/EnergyRecyclerChipItem.java:55-68`(`curioTick` → `updateSpeedBonus`;`onChipUnequip` → `removeSpeedBonus`),
`:74-94`(仅这两处)。对照:**自愈型**写入点(每 tick 按 `isEquipped` 重算)不在此列——
`FenSignItem:146-147`、`RevengeHalberdChipItem:84-87`、`PrimordialCoreChipItem:45-47`
均由 `event/PlayerTickEvents.java:133-136,145` 每 tick 重算,故**没有**残留风险。

**证据 B**:`forge .../item/chip/AdrenalineChipItem.java`、`.../EnergyRecyclerChipItem.java` 同构(仅 `CuriosCompat` 包装差异)。

修法:把这两个修饰器改为**玩家级每 tick 重算**(与 fen / 复仇之戟 / 原初核心同一形状),即
`PlayerTickEvents.onPlayerTick` 里按 `isEquipped + 条件` 重算;或给 `ModItems` 的筹码在
`PlayerLifecycleHandler` 的死亡清理里补一次统一 `clearChipModifiers`。**推荐前者**(自愈,不依赖回调语义)。

---

### P2-C11 取整/截断在链式运算中的位置(具体算例)

```text
ID:P2-C11  严重度:low
核验:证实
现象/算例:
 (1) 伤害数字 vs 实际伤害:`sendDamageNumber(target, (int) finalDmg)` 截断显示,
     而 `setNewDamage((float) finalDmg)` 用未截断值 ⇒ finalDmg=11.9 时玩家看到 11、实扣 11.9。
 (2) 吸血鬼回血:`player.heal(Math.max(1, (int) finalDmg / 2))` —— `(int)` 先截断再整除:
     finalDmg=7.9 → (int)7 → 7/2=3(真值 3.95);finalDmg=3.9 → 1(真值 1.95)。
     同款写法还有 `PaparaSignItem:61` 的受击回血。
 (3) 全力攻击:`attackPower = Math.ceil(attackPower * 1.5)` 在**减防御之前**取整。
     算例:attackPower=9.2(基础 5 + 七咒后骰点 4.2)、defensePower=2.5
     → ceil(13.8)=14 → 14−2.5=**11.5**;若改为"先减再取整"则为 13.8−2.5=11.3。
     (实际掉血走 float,Minecraft 不做二次取整,故这 0.2 是真实差异。)
 (4) 充能 −20%:`ChargeManager.cooldownTicks` = `Math.max(1, (long) Math.ceil(baseTicks*0.8))`
     —— **向上取整**;当前调用点基准都是整数且能被 5 整除(3600/2400/1200/600),
     故现状无感;但若将来出现奇数基准(如 1233 → 986.4 → 987),玩家拿到的冷却**比 −20% 更长**。
 (5) 星光兑换 ATM +40%:`gained += Math.max(1, (int) (gained * 0.4))` —— 先截断再加,且加成下限 1;
     `gained=2` → +1(50%),`gained=4` → +2(50%),`gained=1` → +1(100%)。
 (6) 绿宝石骰子 20% 折扣:`Math.max(1, (int) Math.floor(count * 0.8))`(`trade/EmeraldDiceTrade.java:70`)。
顺序依赖:全部为"同一表达式内的取整位置"问题;反转(先算总量再取整)会改变小数值场景 1 点上下。
可达性:可达(每一项都可单步验证)。
```

**证据 A**:`combat/DiceCombatEvents.java:605 [HEAD]`(WT `:617`)、`:677 [HEAD]`(WT `:690`)、`:494 [HEAD]`(WT `:501`)、
`item/ChargeManager.java:64-68`、`resource/ResourceConversion.java:44`、`trade/EmeraldDiceTrade.java:70`、`item/sign/PaparaSignItem.java:61`。

**证据 B**:`forge .../DiceCombatEvents.java:602 [HEAD]`(WT `:614`)、`:674 [HEAD]`(WT `:687`)、`:491 [HEAD]`(WT `:498`);
其余同文。**两侧一致。**

修法:低优先级。(1) 用 `Math.round` 或直接显示 float;(2) 用 `finalDmg / 2` 再转 int(即 `(int)(finalDmg/2)`);
(4) 若要严格 −20%,改用 `baseTicks - baseTicks/5` 或向下取整口径并写进文案。

---

### P2-C12 下限(`max(1, …)`)发生在乘法**之前**,以及溅射下限与主目标伤害脱钩

```text
ID:P2-C12  严重度:low
核验:证实
现象:① `finalDmg = Math.max(1, attackPower - defensePower)` 之后还有
      `+1(带标记)`、`×1.10/×1.30(虚弱印记)`、`×ratio(七咒)`、受击侧 ×1.4 ⇒
      最低伤害从 1 变成 1.1 / 1.3 / 1.4(标签写"至少 1 点"实际上会溢出小数)。
      ② 大当家溅射:`Math.max(5.0, finalDmg * 0.88)` —— 下限 5 是**绝对常量**,
      与主目标伤害无关:主目标只吃 1 点时,范围 6 格内所有敌对目标各吃 5 点。
顺序依赖:`max(1,…)` 在乘法前;其后的乘法不受该下限保护。反转(乘法后再 max)会改变低伤害末梢。
可达性:可达。最小复现:① 让骰战伤害算到 1 点(高护甲目标)再叠虚弱印记,跳字显示 1、实扣 1.1;
      ② 养精蓄锐满层触发赐福,用最低伤害的一击命中一群怪,观察周围怪各掉 5 点。
```

**证据 A**:`combat/DiceCombatEvents.java:580 / 584-596 / 600-602 [HEAD]`(WT `:587` 起);
`:628-630 [HEAD]`(WT `:640-642`):
```java
                float splashDmg = (float) Math.max(
                        com.merlinkitsune.astral_dice.item.sign.FenSignItem.SPLASH_DAMAGE_MIN,
                        finalDmg * com.merlinkitsune.astral_dice.item.sign.FenSignItem.SPLASH_RATIO);
```
常量 `item/sign/FenSignItem.java:47,49`:`SPLASH_RATIO = 0.88` / `SPLASH_DAMAGE_MIN = 5.0`。

**证据 B**:`forge .../DiceCombatEvents.java:577 / 582-593 / 597-599 [HEAD]`、`:625-627 [HEAD]`;常量同值。

修法:文案若已写"下限 5 点"(AGENTS.md:619「伤害 = 本次攻击伤害的 88%(下限 5 点)」),则 ② 属**已声明行为**,
不必改;① 建议在最终写回前统一 `Math.max(1, finalDmg)`。低优先级。

---

### P2-C13 原版「力量」被**双重计入**骰战攻击力(注册表 +3/级 已在属性里,再加 +2/级)

```text
ID:P2-C13  严重度:medium(未修;需口径定夺)
核验:证实(代码 + 反编译源码)
现象:力量 I 的原版效果已经给 `Attributes.ATTACK_DAMAGE +3`,而 `attackPower` 的起点就是
      `getAttributeValue(ATTACK_DAMAGE)`,修饰器链又加 `(amp+1)*2` ⇒ 力量 I 对骰战合计 +5,
      力量 II 合计 +10(原版 tooltip 写的是每级 +3)。
      对照:王之力/狂暴是**本模组**效果,除注册表外没有属性修饰器(不重复);力量是唯一重复项。
      反击路径用 `highestHeldMeleeBaseDamage`(武器自身数值,不含属性)⇒ 只有 +2/级,**两条链不一致**。
顺序依赖:属性(含力量)→ 修饰器链(再 +2/级)→ 骰点/卡牌 → 防御减。
可达性:可达。最小复现:喝力量 II,记录骰神赐福命中的最终伤害;换喝力量 I 再测 ——
      差值应为 5(而不是原版属性口径的 3)。
```

**证据 A**:`combat/DiceCombatEvents.java:465 [HEAD]`(WT `:472`):
```java
        double attackPower = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
```
`combat/DiceCombatModifiers.java:134-140 [HEAD]`(WT `:182-188`):
```java
        registerAttackModifier((ctx, ap) -> {
            var strength = ctx.attacker.getEffect(MobEffects.DAMAGE_BOOST);
            if (strength != null) {
                ap += (strength.getAmplifier() + 1) * 2;
            }
            return ap;
        });
```
反编译 `neoforge-21.1.235-sources.jar` `net/minecraft/world/effect/MobEffects.java:47-52`
(`.addAttributeModifier(Attributes.ATTACK_DAMAGE, ..., 3.0, ADD_VALUE)`)+
`net/minecraft/world/effect/MobEffect.java:166-173`(`addAttributeModifiers` → `addPermanentModifier`)
+ `:213-218`(`create(level)` 返回 `amount * (level + 1)`)。

**证据 B**:`forge .../DiceCombatModifiers.java:137-143 [HEAD]` 同文;
反编译 `forge-1.20.1-...-sources.jar` `net/minecraft/world/effect/MobEffects.java:18`
`new AttackDamageMobEffect(MobEffectCategory.BENEFICIAL, 16762624, 3.0D)` ⇒ 同为 +3/级;
`forge .../DiceCombatEvents.java:462 [HEAD]` 同 `getAttributeValue(ATTACK_DAMAGE)` 起点。
反击路径 `forge .../DiceCombatEvents.java` `highestHeldMeleeBaseDamage` 同 A。

修法:若口径是"力量只经原版属性生效",删除该修饰器(或改为只对"本模组效果"生效);
若口径是"骰战里力量额外 +2/级",则必须在 tooltip/手册写明(且反击路径应对齐)。
**需用户定夺**;两项改动都会显著影响平衡值。

---

### P2-C14(信息)显示路径会跑完整攻击修饰器链,并调用 `CardRegistry.roll`;防御显示里有恒零项

```text
ID:P2-C14  严重度:info / low
核验:证实
现象:`getDisplayAttackRange`(骰子/界面 tooltip 用)复用 `attackModifiers()` 全链,
      其中第 3 个修饰器会 `CardRegistry.roll(...)`(消耗随机数、写 `ctx.attackCardSum`),
      第 6/20 个会读 `MarkManager`/`HostileTargets`。带副作用的标记施加已用 `ctx.event != null`
      保护(`:194,199`),但**每次渲染 tooltip 都会推进一步 RNG 序列**。
      `getDisplayDefenseRange` 里 `modifierDefense` 恒为 0(防御表只写 `ctx.defenseCardSum`),
      故 `rawArmor + modifierDefense * 2.0` 是死代码。
顺序依赖:显示与实际共用同链 ⇒ 显示值会随实时装备变化(设计如此);RNG 消耗无契约。
可达性:可达(纯代码事实)。
```

**证据 A**:`combat/DiceCombatModifiers.java:438-467 [HEAD]`(WT `:500-529`)、
`:144-153 [HEAD]`(WT `:192-201`,`CardRegistry.roll`)、`:481-484 [HEAD]`(WT `:543-546`)。
**证据 B**:`forge .../DiceCombatModifiers.java:441-470 / :147-156 / :484-487 [HEAD]` 同构。

修法:低优先级。可给 `DiceCombatContext` 加 `display` 标志让掷骰修饰器跳过;或显示路径改用
`minRoll/maxRoll` 直接累加(它已经这么算了)。

---

## 3. 百分比 / 倍率基准总表(逐处列明"乘在什么之上"+ 钳制位置)

| # | 位置(文件:行 [HEAD]) | 数值 | **基准** | 钳制 | 顺序 |
|---|---|---|---|---|---|
| 1 | `DiceCombatEvents:168-181` | −40%(启示之证 −20% → 合计 −60%) | **骰点+卡牌**之和(`baseDice+attackCardSum`) | `Math.max(0, cursePenalty-0.2)` | 乘法前钳 |
| 2 | `DiceCombatEvents:1188-1190` | 同上 −40% | **武器基础+卡牌+全部攻修饰器**(不含骰点) | — | 见 P2-C9 |
| 3 | `DiceCombatEvents:494` | ×1.5 全力攻击 | **`attackPower`(含骰点/卡牌/七咒后)** | `Math.ceil(...)` | 减防御**之前**取整(C11-3) |
| 4 | `DiceCombatEvents:580` | 下限 1 | `attackPower - defensePower` | `Math.max(1,…)` | **乘法之前** → 后续 ×1.1/×1.3/×1.4 会溢出(C12) |
| 5 | `DiceCombatEvents:584-585` | +1 | 带标记目标的 `finalDmg` | — | 在 max(1) 之后 |
| 6 | `DiceCombatEvents:590-596` | ×1.10 / ×1.30 | `finalDmg` | — | 加法式叠加(1.10+0.20=1.30,不是 1.32) |
| 7 | `DiceCombatEvents:600-602` + `:925-936` | ×ratio(七咒实际倍率;命运指引时 `1+(r-1)/2`) | `finalDmg` | — | 用后即清零 `setDiceCurseRatio(1.0f)` |
| 8 | `DiceCombatEvents:614 [WT]` + `EnderDiceHandler:56,152 [WT]` | ×1.4 雨中/水下 | `finalDmg`(**护甲后**) / 任意伤害事件的当前值 | `damage<=0` 早退 | 两处消费,骰战路径不叠加 |
| 9 | `EnderDiceHandler:143 [HEAD forge]` | ×1.4 | **护甲前**(LivingHurtEvent) | — | 单侧差异基线(C7/C8) |
| 10 | `ObsidianDiceHandler:35-36`(neo)/`:34-35`(forge) | ×0.3(−70% 火焰) | 入站伤害(`LivingIncomingDamageEvent` / `LivingHurtEvent`,护甲前) | — | 火焰无玩家直伤 ⇒ 不遇骰战覆盖(P2-C1 可达性排除) |
| 11 | `JasmineSweepEffect:17-22` | 移速 +0.2 / 护甲 −0.3 | `ADD_MULTIPLIED_TOTAL`:base+ADD_VALUE 的**总和** | 属性上限 30 / 后续 `min(…,20)` | 乘法**之后**才钳 ⇒ P2-C5 |
| 12 | `BlueCurseEffect:18-23` | 护甲 −0.2 / 韧性 −1.0 | 同上 | 同上 | 与 11 顺序相乘(0.56)→ P2-C6 |
| 13 | `UnwaveringEffect:21-24` | 护甲 +8×(amp+1) | ADD_VALUE(不吃乘数之外的运算) | 属性上限 | — |
| 14 | `DiceCombatModifiers:87-102` | 防御力×2 → 护甲 | ADD_VALUE | 放进总和(吃 11/12 的乘数) | P2-C6 |
| 15 | `DiceCombatEvents:563-565` / `PiercingGunChipItem:22-25` | 防御 = 2+护甲/2+1.4×韧性 | `min(getArmorValue(),20)`;**两个上限都在乘数之后** | `min(…,20)` | P2-C5 |
| 16 | `MosesSignItem:117-125` / `WeirdDiceHandler:36-42` | 冷却 120s/180s,诡异 −50%,充能 −20% | **该立牌的基准秒数** | `Math.max(1, ticks/2)` → `ceil(×0.8)` | 半衰先于 −20% |
| 17 | `JasmineSignItem:90` | −90s(文案写 −50%) | **常量 180s/2** [HEAD] → **起冷却时记录的 max** [WT] | `Math.max(now, …)` | ⇒ 可归零(P2-C3,**WT 已修**) |
| 18 | `FateGuidanceCardItem:67-69` | −(最大冷却/2) | `WeirdDiceHandler.signCooldownTicks` [HEAD] → `sign_active_max_cooldown` [WT] | `Math.max(0, remaining-reduction)` | ⇒ 过度减免(P2-C4,**WT 已修**) |
| 19 | `ChargeManager:64-68` | ×0.8(−20%) | 传入的 `baseTicks` | `Math.max(1, ceil(...))` | 向上取整 |
| 20 | `KomachiSignItem:138` | 剩余×0.7(−30%) | **剩余时间** | — | 口径正确(与 17/18 对比) |
| 21 | `PaparaSignItem:61` / `DiceCombatEvents:677` | ÷2 回血 | `event.getNewDamage()` / `finalDmg` | `Math.max(1,(int)x/2)` | 先截断后整除(C11-2) |
| 22 | `PandamanSignItem:174-203` | +2 最大生命,上限 100 | `getMaxHealth()` **当前值**(不是"加成总量") | `min(2, 100-当前)` | 其它来源撑高最大生命时本项加不进去 |
| 23 | `ResourceConversion:44` | +40%(ATM) | `gained`(已按实际扣除额算) | `max(1,(int)(g*0.4))` | 先截断 |
| 24 | `EmeraldDiceTrade:70` | ×0.8(−20% 折扣) | 报价数量 | `max(1, floor(...))` | 先 floor |
| 25 | `SpellDamageRegistry:185-349` | +2/+4/+6/+8/+5 + 忍者 + 书签 + 标记/防御加成 | **加法聚合**(与顺序无关)→ 单次真伤 | 真伤只 bypasses_armor(吃抗性/保护) | 见 §1.3 |
| 26 | `SpellDamageRegistry:254` | AOE = `max(1, 5 + effectCardDamageBonus)` | **常量 5 + 忍者/书签**(≠ 主目标聚合 bonus) | `max(1,…)` | 与主目标不等值 |
| 27 | `SpellDamageRegistry:365` | AOE 电击手套 = 事件当前值 | **neo 护甲后 / forge 护甲前** | `(int)` 截断显示 | P2-C7 |
| 28 | `FenSignItem:47,49` + `DiceCombatEvents:628` | 溅射 = `max(5, finalDmg×0.88)` | `finalDmg`(已含七咒/虚弱/标记) | 常量下限 5 | 与主目标伤害脱钩(C12) |
| 29 | `DiceCombatModifiers:134-140` | +2×(amp+1) 力量 | 属性攻击力(**已含 +3/级**) | — | 双重计入(C13) |
| 30 | `EffectCardPeriod`(改动中)+ `ChargeManager` | 出牌冷却 30s × 0.8 | 常量 600t | `ceil` | 与 §C 交界,改动中批次已加"未打满也进冷却" |

---

## 4. 必答三问

### ① 有没有哪一处**覆盖式写入**会丢弃另一个系统已经算好的数值?→ 有,两处(同一条语句的两类牺牲品)

- **位置**(唯一):`DiceCombatEvents.java:604 [HEAD]` / `:616 [WT]`(forge `:601 [HEAD]` / `:613 [WT]`)
  `event.setNewDamage((float) finalDmg);` —— 不读旧值。
- **牺牲品 1(已修)**:末影骰雨中/水下 ×1.4(`EnderDiceHandler:142 [HEAD]`,HIGH 早于 NORMAL)。
  可达场景:**双人 PvP**,受伤方戴末影骰子站雨中,攻击方赐福近战命中 ⇒ ×1.4 归零。
  改动后批次已通过"受击侧修饰器注册表 + 覆盖前重消费"修复(P2-C1)。
- **牺牲品 2(未修)**:容器上已经记好的 **ARMOR/MOB_EFFECTS/ENCHANTMENTS** 三段减免
  (`LivingEntity:1787-1789` + `DamageContainer:163-167`)⇒ **抗性提升、保护附魔**对骰战伤害完全无效(P2-C2)。
  可达场景:目标喝抗性提升 II / 穿保护 IV,被赐福近战命中,掉血量与无抗性/无保护相同。
- **不会再被覆盖的**:`onBerserkDamageTaken`(LOW)、`onWeakMarkDamage`(LOW,骰战命中时显式 return)、
  `ChipDamageHandler`(LOWEST)都在 NORMAL 之后;`ObsidianDiceHandler` 作用于火伤(无玩家直伤 ⇒ 不会走到骰战路径,**不可达**,不列缺陷)。
- **新引入的风险**:P2-C8(1.20.1 的 ×1.4 被同时折进 `dice_curse_ratio` 又被受击侧修饰器乘一次 = 1.96 倍)。

### ② 有没有哪一处的**基准**与 tooltip/手册文案不符?→ 有三处,其中两处同源

| 文案 | 位置 | 代码实际 | 判定 |
|---|---|---|---|
| 「使用「加急加快」后，主动技能冷却立即减少最大冷却的 50%」`zh_cn.json:559` | `JasmineSignItem:90 [HEAD]` | 减去**常量** 180s/2 = 90s;枪匠场景=75%,枪匠+诡异+充能=100%(归零) | **不符**(P2-C3);**改动后已修**(改取起冷却时记录的 max) |
| 「立刻减少主动技能最大冷却倒计时一半的时间」`zh_cn.json:434` / `halve the current maximum active skill cooldown countdown` `en_us.json:434` | `FateGuidanceCardItem:67-68 [HEAD]` | 基准取**通用** `WeirdDiceHandler.signCooldownTicks`(180/90/72s),与当前生效的冷却(枪匠 120s)无关 | **不符**(P2-C4);**改动后已修**(路线 A + 附件 `sign_active_max_cooldown`) |
| 「岿然不动：每层护甲 +8…+ 抗性提升 II」`AGENTS.md:241`、`zh_cn.json` 对应条目 | 骰战 `defensePower` 只含护甲/韧性/骰/牌 | 骰战时**抗性提升 II 不参与减伤** | **不符**(P2-C2 的一部分) |
| 「立牌主动技能冷却时间 −50%」(诡异骰子) | `WeirdDiceHandler:39` | `ticks/2` 后再 ×0.8(充能)= 净 −60%(两段各自与自身文案一致) | 相符 |
| 「受到的火焰伤害减少 70%」 | `ObsidianDiceHandler:35` | ×0.3,护甲前,乘法关系 ⇒ 最终比值仍 0.3 | 相符 |
| 「雨中/水下受到的伤害 +40%」 | `EnderDiceHandler` 1.4F | 1.21.1 护甲后 / 1.20.1 护甲前;骰战场景 HEAD 归零 | 数值相符、**生效范围不符**(P2-C1/C7) |
| 「移动速度 +20%、护甲值 −30%」 | `JasmineSweepEffect:17-22` | 护甲 −30% 在高护甲配装下被 20 上限吞掉(0 效果) | **代价不符**(P2-C5) |
| 「每提升 1 星级攻防 +2(+4 护甲)」 | `NetherStarDiceItem:74` | ADD_VALUE 折 2 护甲/点,但会被 −20%/−30% 乘数削减 | 见 P2-C6 |

### ③ 有没有修饰器**注销遗漏**导致跨死亡/跨装配残留?

- **跨死亡**:`Attributes` 的瞬态修饰器挂在 `AttributeInstance` 上,而死亡→重生会**重建 ServerPlayer 实体**
  ⇒ 跨死亡**不残留**(已证实:所有 `setDefenseArmorBonus` 键、`pandaman_max_health`、
  `charge_speed`、`dice_nether_star_dice_*` 都随实体消失)。**"死亡被取消"路径**(末影骰/气囊)
  不会掉落饰品 ⇒ 保留加成是正确的。故**未发现跨死亡残留**。
- **跨装配**:有两条可达/待核验路径(P2-C10):
  1. **已证实(代码层)**:`adrenaline_def_armor<N>` 与 `charge_speed` 只有 `curioTick`(需仍在槽位)
     与 `onChipUnequip`(需 Curios 回调)两个清除点;
  2. **未核验**:Curios 自身移除路径(死亡掉落 `handleDrops` / 骰子换装导致筹码栏收缩而溢出掉落)
     **是否回调 `onUnequip`**。仓内注释(`BaseSignItem:158-163 [WT]`)明确写"如死亡掉落 `handleDrops`
     ——根本不会回调 `onUnequip`",但该结论是代码注释而非实测。
  最小实机步骤见 P2-C10。
- **对称性总评**:10 个 `setDefenseArmorBonus` 键中,7 个由**玩家级每 tick 重算**自愈
  (fen / 复仇之戟 / 原初核心 / papara / padman / nancy_lu / moses / jasmine / nether_star 的 tick 重算),
  仅 **肾上腺素**(护甲)与 **能量回收**(移速)依赖回调 ⇒ 建议把这 2 个也改成玩家级每 tick 重算。

---

## 5. 单侧(仅一个版本存在)差异汇总

| # | 差异 | A(1.21.1) | B(1.20.1) | 影响 |
|---|---|---|---|---|
| S1 | 受击侧伤害事件阶段 | `LivingDamageEvent.Pre`(**护甲+附魔+抗性后**、吸收前) | `LivingHurtEvent`(**护甲前**) | 末影骰 +40% 的基准不同 ⇒ 高护甲/高伤害段比值 1.4 vs **1.77**(算例见 §6-3) |
| S2 | 电击手套 AOE 取值 | `ctx.event.getNewDamage()`(护甲后) | `ctx.event.getAmount()`(护甲前) | 同一 AOE 数值差 1.5~1.8 倍(P2-C7) |
| S3 | 七咒倍率捕获点 | `LivingIncomingDamageEvent` LOWEST(早于 ×1.4) | `LivingAttackEvent` HIGHEST 记原值 + `LivingHurtEvent` LOWEST 读现值(**晚于** ×1.4) | 1.21.1 丢 ×1.4;1.20.1 折进 ratio,改动后**双重放大 = 1.96**(P2-C8) |
| S4 | 安全气囊"致命"判定基准 | 改动后已换算成"吸收后"(WT `ChipDamageHandler:68`) | 事件本身已在吸收后(WT `:53-68`) | **改动后已对齐**(改动前为单侧差异) |
| S5 | 骰战伤害与吸收(黄心)的交互 | `onLivingDamagePre` 在吸收前,吸收按 `min(吸收值, finalDmg)` 扣一次 | 原版先扣一次吸收,`LivingDamageEvent` 后 `setAbsorptionAmount` 再扣一次(`LivingEntity:1670`+`:1684`,`≥0` 钳制) | 骰战伤害**都**不吃吸收减免;1.20.1 的吸收被多扣一次 ⇒ 大吸收盾(末影骰吸收 II)可承受的后续击数不同(low,需实机) |
| S6 | `SIGN_ACTIVE_COOLDOWN_END` 写入方与"待命"门槛 | 改动中已统一(state 与 timer 分离,`BaseSignItem.tickSignReadyTimeout`) | 同批改动,两侧一致 | 无单侧差(仅记录) |
| S7 | `MosesSignItem#signCooldownTicks` 基准 | 120s(枪匠)与 180s(通用)两函数并存 | 同 | **两侧一致**(非单侧;缺陷在 P2-C3/C4) |

---

## 6. 取整/截断具体算例

1. **整数除法在链中的位置**
   `player.heal(Math.max(1, (int) finalDmg / 2))`:`finalDmg = 7.9` → `(int)7` → `7/2 = 3`
   (若写成 `(int)(finalDmg/2)` 则为 3,若四舍五入为 4)。末影骰不死图腾后回血类效果同理。
2. **`Math.ceil` 放在减防御之前**
   base 5 + 七咒后骰点 4.2 = 9.2;全体攻击 → `ceil(13.8) = 14`;defensePower 2.5 → `11.5`。
   若改为先减后取整:`13.8 − 2.5 = 11.3`。**差 0.2 真实掉血**。
3. **护甲公式的非线性 + ×1.4 的位置(单侧差异算例)**
   护甲 20、韧性 8 ⇒ `f = 2 + 8/4 = 4`;`f1 = clamp(20 − d/4, 4, 20)`;`伤害 = d×(1 − f1/25)`。
   - 基础 `d = 40` → `f1 = clamp(10,4,20) = 10` → **24.0**
   - **1.20.1**(先 ×1.4):`d' = 56` → `f1 = clamp(20−14,4,20) = 6` → `56×0.76 = **42.56**`
   - **1.21.1**(后 ×1.4):`24.0 × 1.4 = **33.6**`
   ⇒ 同一击、同一配装,两版本差 **8.96(相对 26.7%)**;且 1.20.1 的实际放大是 **1.77 倍**而非 1.4 倍。
4. **充能 −20% 的向上取整**:`1233 → 986.4 → ceil 987`(比严格 −20% 的 986.4 长)。现状基准均为整十数,无感;属潜在项。
5. **星币兑换 ATM +40% 的下限**:`gained=1 → +max(1, (int)0.4) = +1`(实际 +100%);`gained=2 → +1`(+50%);
   `gained=5 → +2`(+40%)。文案只说"增加 40%"。
6. **衰减/上限的位置(肉弹战车最大生命)**:`room = 100 − 当前最大生命`(整数 floor),
   `gain = min(2, room)` ⇒ 已有其它来源把最大生命推到 100 时,吃汉堡**完全不涨**;
   且 `refreshMaxHealthBonus` 只在 `bonus>0` 时挂修饰器 ⇒ "卸下清除"是绝对清除(不是减去增量)。

---

## 7. 不确定点 / 未核验项(供下一轮或实机收口)

1. **Curios 移除路径是否回调 `onUnequip`**(P2-C10 的一半):死亡掉落 / 槽位收缩溢出掉落。
   需要一次实机:`/attribute <player> minecraft:generic.armor get` 在上述两种路径前后对比。
2. **吸收(黄心)在骰战下的跨版本差异**(S5):需实机用末影骰不死图腾的伤害吸收 II 连挨两击对比。
3. **P2-C2 的口径**(骰战是否应当吃抗性/保护):必须由用户定夺,不宜由代理单方面改数。
4. **P2-C3 / P2-C4 的口径**:改动中批次已选"**路线 A**"(减「起冷却时记录的最大冷却」的一半),
   HEAD 的缺陷已在工作区消失。仍需确认两点:①"记录缺失回退通用 180s"是否可接受(老存档首击);
   ②两张「加急加快」是否允许把剩余冷却直接归零(忍者复制器可复制该牌)。
5. **P2-C13 力量口径**:是"额外 +2/级"还是"仅原版属性"。会显著改变平衡数值。
6. **改动中批次的最终形态**:本报告的 WT 行号取自 2026-09-15 15:45 快照;该批次仍在写入
   (`DiceCombatEvents`/`DiceCombatModifiers`/`EnderDiceHandler`/`ChipDamageHandler`/`FateGuidanceCardItem`/
   `JasmineSignItem`/`ModAttachments` 在本次审计期间被多次修改,期间还出现过数分钟的编译不自洽)。
   **所有 WT 结论请在批次提交后按引用原文复核一次**;C3/C4 的"已修"结论即来自快照后的追加核对。
7. 未覆盖:属性修饰器在**维度切换**/**Curios 重载**(from=to)下的增删次数统计(本次只核对了注册键与
   清除点是否成对,未做每 tick 调用次数度量)。
```
