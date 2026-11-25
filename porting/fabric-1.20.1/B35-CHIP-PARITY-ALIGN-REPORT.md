# B35 — 筹码 / 星光功能代码对齐报告（fabric-1.20.1 ⟸ forge-1.20.1 主线）

- 工作树：`D:\MCProject\astral_dice_multiloader`
- 蓝本：**forge-1.20.1**（主基准），**neoforge-1.21.1**（次基准，逐项交叉核对）
- 目标线：`fabric-1.20.1`
- 批次范围：安全气囊 / 会员推荐 / 鹰眼镜 / 银行卡三档 + 星光锤 / ATM 五个筹码缺口；**本批不改任何 lang json**
- 结论：**五项缺口全部按主线对齐完成**；四条验收全绿（末段原文见 §6）
- 本批**额外**对齐 1 项（清单外、同一件筹码）：银行卡-用不完筹码的「无队伍 ⇒ 自身 32 格内」范围收紧，理由见 §5.1

---

## 1. 判定依据（为什么以 forge-1.20.1 为蓝本）

1. 用户已裁决：**一律以最新主线为准，旧版本差异全部放弃**。
2. 已知前置事实：neoforge-1.21.1 与 forge-1.20.1 在全部 1070 个语言共有键、三语上逐字相同 ⇒ 两侧实现高度一致。
3. 逐项复核结论：五个功能点在 forge-1.20.1 与 neoforge-1.21.1 上**文本与行为均一致**，唯二差异是
   - 受击事件类（forge `LivingHurtEvent` / neo `LivingDamageEvent.Pre`）—— 见 §4.1；
   - `BankCardUnlimitedChipItem` 的 `ALLY_RANGE`：**neo-1.21.1 没有**（neo `BankCardUnlimitedChipItem.java:24` 只有 `STARLIGHT_ON_EQUIP`），forge-1.20.1 有（`:31`）⇒ 该裁决（2026-10-15）**比 neo 线更新**，故 forge-1.20.1 是更「新」的蓝本，与本批裁决方向一致。
4. fabric-1.20.1 同为 MC 1.20.1，平台机制（Curios 语义、事件时序、NBT/附件）与 forge-1.20.1 最接近。

### 1.1 星光账本定位取证（任务要求先查清「账本在哪」）

- 结论：fabric 线星光账本 = **本仓 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/StarLightManager.java`**，**不在 starengine jar 里**。
- 取证 A（jar 扫描）：用 python zipfile 打开 `fabric-1.20.1/build/processIncludeJars/starengine-fabric-1.20.1-1.1.0.jar` → 106 个条目，**0 个**名字匹配 `starlight/StarLight`；`com/merlinkitsune/starengine/item/` 下只有 `AstralRarities`、`BossEntityUtil`、`Rarity`、`TrinketsCompat*`。
- 取证 B（写入面）：fabric 线 `grep "setStarlight("` 只命中 `component/ModAttachments.java:448`（附件 setter）与 `item/StarLightManager.java:114`、`:119` ⇒ 账本唯一写入口就是 StarLightManager。
- 取证 C（消费面）：fabric 线共 **15** 个文件 `import com.merlinkitsune.astral_dice.item.StarLightManager`。
- ⇒ 因此 `replenishOnExchange` 补在 fabric 既有的 `item/StarLightManager.java` 门面上（**未复制逻辑、未新建类**）。

---

## 2. 主基准取证（file:line）→ fabric 落地位置（file:line）

| # | 功能点 | 主基准 forge-1.20.1 | 次基准 neoforge-1.21.1 | fabric 对齐后 |
|---|---|---|---|---|
| 1 | 安全气囊：受击充能常量 | `item/chip/AirbagChipItem.java:36` `HIT_CHARGE_GAIN = 1`；`:38` `HIT_CHARGE_COOLDOWN_TICKS = 20 * 15` | 同（`:36`/`:38`） | `item/chip/AirbagChipItem.java:37`、`:39` |
| 1 | 安全气囊：受击入口 + 15s 冷却 | `:124` `onIncomingDamage(LivingHurtEvent)`；`:131-132` `setAirbagHitChargeCooldownEnd(...)` + `ChargeManager.addStacks(...)` | `neoforge-1.21.1/.../AirbagChipItem.java:113` `onIncomingDamage(LivingDamageEvent.Pre)`；`:120-121` 同两次调用 | `:121-130`（`@SubscribeEvent` :121，方法 :122-130） |
| 1 | 安全气囊：冷却附件 | `component/ModAttachments.java:1200-1201` `AIRBAG_HIT_CHARGE_COOLDOWN_END`（**无 .sync()**、不进 SYNCED_KEYS）；`:1263-1269` 两个访问器 | 同（服务端专用） | `component/ModAttachments.java:1079`（key）、`:1141` `getAirbagHitChargeCooldownEnd`、`:1145` `setAirbagHitChargeCooldownEnd` |
| 2 | 会员推荐：手牌闸门 | `item/chip/MemberRecommendationChipItem.java:21` `HAND_CARD_LIMIT = 5`；`:47` `countHandCards`；`:63` 闸门 | 同（`:21`/`:47`/`:63`） | `:21`、`:47`、`:63`（闸门在 `giveCardTo` :64 之前） |
| 2 | 会员推荐：价格 -1 | `:58-65` `shopPriceReduction` = `isEquipped ? 1 : 0` | 同 | 原样保留（行为本就一致，仅类注释对齐主线） |
| 3 | 鹰眼镜：标记加伤常量 | `item/chip/EagleScopeChipItem.java:17` `DAMAGE_PER_MARK = 2`；`:6-14` javadoc「额外加伤通道」 | 同（`:17`/`:8`） | `:17` + 主线 javadoc（`:6-14`） |
| 3 | 鹰眼镜：命中前层数载体 | `combat/DiceCombatContext.java:57-61` `public int eagleScopeMarkLevel;` | 同（`:57-61`） | `combat/DiceCombatContext.java:61` |
| 3 | 鹰眼镜：攻击修饰器只记录层数 | `combat/DiceCombatModifiers.java:476` `ctx.eagleScopeMarkLevel = MarkManager.getLevel(ctx.target);`（在 `MarkManager.apply` 之前） | `:490` 同 | `combat/DiceCombatModifiers.java:436`（apply 在 `:437`） |
| 3 | 鹰眼镜：额外加伤修饰器 | `combat/DiceCombatModifiers.java:490-495`，`:494` `return ctx.eagleScopeMarkLevel * EagleScopeChipItem.DAMAGE_PER_MARK;` | `:490` 同 | `combat/DiceCombatModifiers.java:450-455`（`:454` 为同一表达式） |
| 4 | 银行卡三档：改名 + 一次性发放 | `item/chip/BankCardChipItem.java:15`/`:17` `STARLIGHT_LOW/HIGH`；`:44` `grantStarlightOnEquip(player, grantBitForStarlight(), starlightAmount)`；`:56` revoke | 同（`:15`/`:17`/`:44`/`:56`） | `:15`/`:17`/`:44`/`:56` |
| 4 | 银行卡三档：注册点 | `item/ModItems.java:632/:638` | `:631/:637` | `item/ModItems.java:585/:591` |
| 4 | 用不完：装备常量 + 范围 | `item/chip/BankCardUnlimitedChipItem.java:25` `STARLIGHT_ON_EQUIP = 3`、`:31` `ALLY_RANGE = 32.0`、`:47` onEquip、`:76-97` 范围收紧 | `:24` 只有 `STARLIGHT_ON_EQUIP`（**无 ALLY_RANGE**） | `:24`、`:30`、`:51`、`:75-96`（与 forge `:76-97` 逐行一致） |
| 4 | 星光锤 | `item/chip/StarCoinHammerChipItem.java:37` `STARLIGHT_ON_EQUIP = 5`；`:222` 使用 | `:36`/`:221` | `:37`/`:222` |
| 4 | 兑换时补星光 | `item/StarLightManager.java:143` `replenishOnExchange(Player)`（`:149/:152/:155/:158` 四件装备判定后 `return add(player, total)`） | `:140` | `item/StarLightManager.java:143`（`:149/:152/:155/:158`） |
| 4 | 调用点 | `resource/ResourceConversion.java:44`（在 `if (gained > 0)` 内、`giveItem` 之前） | `:44` 同 | `resource/ResourceConversion.java:44`（`if (gained > 0)` :42） |
| 5 | ATM 筹码移除装备账本 | `item/chip/AtmChipItem.java:14` doc「故本类不再有 onEquip/onChipUnequip 账本钩子」，`:20` class，**无任何 override** | `:14`/`:16` 同 | `item/chip/AtmChipItem.java:14`/`:16`，无 override |
| 5 | 附带：`BaseChipItem` 示例引用 | `item/chip/BaseChipItem.java:32`「原示例 AtmChipItem#onEquip 已随 ATM 机筹码重写而删除」 | 同 | `:32` 已对齐 |

---

## 3. 逐文件改动（fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/…）

行数为「工作树实测 before → after（Δ）」。

| 文件 | 行数 | 改动要点 |
|---|---|---|
| `component/ModAttachments.java` | 1841 → 1859（+18） | 新增服务端专用 key `AIRBAG_HIT_CHARGE_COOLDOWN_END`（`:1079`，**不加 .sync()、不进 SYNCED_KEYS**，与主线一致）+ 访问器 `:1141`/`:1145` |
| `item/chip/AirbagChipItem.java` | 89 → 132（+43） | 补 `HIT_CHARGE_GAIN`（`:37`）、`HIT_CHARGE_COOLDOWN_TICKS = 20 * 15`（`:39`）、`@SubscribeEvent onIncomingDamage(LivingHurtEvent)`（`:121-130`）与主线全部注释/取证段；类上不是 `@Mod.EventBusSubscriber` 而是显式登记（见 §4.2） |
| `AstralDiceMod.java` | 350 → 353（+3） | `registerListeners` 中新增 3 行（`:189-191`）：两行注释 + `LoaderBus.INSTANCE.register(AirbagChipItem.class);` |
| `combat/DiceCombatContext.java` | 74 → 79（+5） | `:61` `public int eagleScopeMarkLevel;`（含主线 javadoc） |
| `combat/DiceCombatModifiers.java` | 1020 → 1038（+18） | `:18` 引入 `EagleScopeChipItem`；`:416-422` 段头补 2026-10-15 裁决说明；鹰眼镜分支 `:432-439` 改为**只记录命中前层数**；新增额外加伤修饰器 `:443-455`（`registerExtraDamageModifier`，`:454` = `层数 × DAMAGE_PER_MARK`） |
| `item/chip/EagleScopeChipItem.java` | 13 → 22（+9） | 主线 javadoc（额外加伤通道 / 命中前层数）+ `DAMAGE_PER_MARK = 2`（`:17`） |
| `item/chip/MemberRecommendationChipItem.java` | 42 → 66（+24） | `HAND_CARD_LIMIT = 5`（`:21`）、`countHandCards`（`:47-56`，遍历 `getInventory().items` + `offhand`，`ModItems.isCardItem` 命中即累加 `getCount()`）、闸门 `:63`；补 `ItemStack` import（`:8`） |
| `item/chip/AtmChipItem.java` | 49 → 30（−19） | 删除 `onEquip`（原 `:40` `grantStarlightOnEquip(..., GRANT_BIT_ATM, 1)`）与 `onChipUnequip`（原 `:47` revoke）及随之失效的 import（`ItemStack`/`SlotContext`/`ResourceConversion`/`StarLightManager`/重复的 `CuriosApi`）；类注释改为主线 2026-10-08 版本 |
| `item/chip/BankCardChipItem.java` | 59 → 59（0） | `BASE_LOW/BASE_HIGH` → `STARLIGHT_LOW = 4`（`:15`）/`STARLIGHT_HIGH = 7`（`:17`）；`baseStarlight`/`getBaseStarlight` → `starlightAmount`（`:19`）/`getStarlightAmount`（`:27`）；`grantBitForBase` → `grantBitForStarlight`（`:48-50`）；`onEquip`（`:44`）改走 `grantStarlightOnEquip`（弃用已被主线删除的 `grantStarlightFloorOnEquip`） |
| `item/ModItems.java` | 1189 → 1189（0） | `:585/:591` 构造参数改用 `BankCardChipItem.STARLIGHT_LOW/HIGH`；`:581/:589` 注释随主线改写 |
| `item/StarLightManager.java` | 169 → 163（−6） | **删除** `getBasePoints`、`grantStarlightFloorOnEquip`；`set()`（`:117-119`）改为主线 `Math.max(0, Math.min(value, getCap()))`（去掉「底盘」概念）；**新增** `replenishOnExchange(Player)`（`:143-162`，四件星光类筹码按佩戴求和后 `return add(player, total)`，不写发放账本、不做闸门、不触发卸除扣回） |
| `resource/ResourceConversion.java` | 69 → 66（−3） | `:32` `int spendable = StarLightManager.get(player);`（去掉 `- getBasePoints` 底盘）；`:33` `amount < 0 ? spendable : Math.min(amount, spendable)`；删除「ATM 机筹码：兑换量 +40%」块（及其 import）；`:42-46` `if (gained > 0) { StarLightManager.replenishOnExchange(player); giveItem(...); }` |
| `item/chip/BankCardUnlimitedChipItem.java` | 79 → 101（+22） | `STARLIGHT_ON_EQUIP = 3`（`:24`）、`ALLY_RANGE = 32.0`（`:30`）；`onEquip`（`:51`）改用常量；`onBlessingEnd`（`:65-96`）改为主线：`hasTeam ? collectTeamPlayers : List.of()`，无队伍时只发**自身 32 格内**；类注释同步 |
| `item/chip/StarCoinHammerChipItem.java` | 229 → 231（+2） | `STARLIGHT_ON_EQUIP = 5`（`:37`）；`onEquip`（`:222`）改用常量 |
| `item/chip/BaseChipItem.java` | 103 → 103（0） | `:32` javadoc 示例从 `AtmChipItem#onEquip` 改为 `MedkitEmergencyChipItem#onEquip`（主线已删该示例所指方法） |

合计：**15 个文件**，净 +93 行（+143 / −50；其中 3 个文件为纯注释对齐、无行数变化）。

### 3.1 未改动（明确声明）

- **任何 `assets/astral_dice/lang/*` 未改**（`check_lang_sync.py` 四条键集与批前一致，fabric 仍 1094 键）。
- 其它筹码行为未动；`Item#getAttributeModifiers` / 全局属性管线未动。
- `shop/CardShopMenu.java`（兑换调用点 `:573`）、`screen/CardShopScreen.java` 未改：与主线逐行一致（见 §5.3）。

---

## 4. 平台差异与垫片理由

### 4.1 受击事件：不需要任何垫片

- fabric 线自带 forge 形状的事件层：`platform/event/SubscribeEvent.java`、`platform/event/EventPriority.java`、`platform/event/entity/living/LivingHurtEvent.java`（与 forge 同名同签名）。
- 触发点：`mixin/bridge/LivingHurtBridgeMixin.java` `@WrapOperation` 挂在 `LivingEntity#hurt` 内对 `actuallyHurt` 的调用上 ⇒ 事件在**护甲/吸收结算之前**派发，取消则不再走 `actuallyHurt`——与 Forge `LivingHurtEvent` 语义等价（forge-1.20.1 自身 javadoc 亦记载：1.21.1 的 `LivingDamageEvent.Pre` 在 1.20.1 Forge 被重映射为 `LivingHurtEvent`）。
- ⇒ fabric 侧只是把 `@Mod.EventBusSubscriber` 换成**显式登记**（fabric 无该注解机制）。

### 4.2 唯一的「非 1:1」适配：注解 → 显式登记

- 主线：类上 `@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)`。
- fabric：`AstralDiceMod#registerListeners` 里 `LoaderBus.INSTANCE.register(AirbagChipItem.class);`（`AstralDiceMod.java:191`），并在类 javadoc（`AirbagChipItem.java:28`）与登记处注释（`:189-190`）**双向**写明「漏登记 = 静默失效，编译与构建都不报错」。
- 理由：fabric 线的全部 `@SubscribeEvent` 监听器都靠显式登记（`AstralDiceMod` 中数十条，如 `:143` `DiceCombatEvents`、`:152` `ChipDamageHandler`）；这是本线**既有**机制，不是新设计。

### 4.3 其它平台替换（纯 import 级）

| 主线 | fabric | 说明 |
|---|---|---|
| `com.merlinkitsune.starengine.item.CuriosCompat` | `com.merlinkitsune.astral_dice.compat.curios.CuriosApi` | fabric 线自带的 Curios 垫片（`compat/curios/`），fabric 无 Curios 官方 API |
| `top.theillusivec4.curios.api.SlotContext` | `com.merlinkitsune.astral_dice.compat.curios.SlotContext` | 同上 |
| `com.merlinkitsune.starengine.combat.PartyRelations` | `com.merlinkitsune.astral_dice.combat.PartyRelations` | fabric 线的 PartyRelations 在本仓（starengine 只留 `TrinketsCompat` 等跨平台件） |

> 方法体一律**逐行照抄主线**，未发明第三种写法。常量（如 `DAMAGE_PER_MARK`、`STARLIGHT_ON_EQUIP`）会被 javac 内联，因此**不能**在 remapJar 产物里用「字符串是否出现」来验证使用点——见 §6.4 的补充取证。

### 4.4 已知语义偏差（与主线完全一致，如实登记）

`LivingHurtEvent` 在护甲结算之前 ⇒ **被护甲/吸收完全抵消的那一击也计 1 次充能**。forge-1.20.1 与 neoforge-1.21.1 同样如此（主线 javadoc 已自述），非本线新增偏差。

---

## 5. 越界项 / 判定说明

### 5.1 【清单外，已对齐】银行卡-用不完筹码的「无队伍 ⇒ 自身 32 格内」

- 该漂移**不在** B32 缺口清单里，但它属于同一件筹码（缺口 #4），且是**行为级**差异：旧 fabric 在无队伍时走 `PartyRelations.collectTeamPlayers` 的「未组队 ⇒ 全服在线玩家」兜底 ⇒ 一次赐福可给全服发 3 星币。
- 依据用户裁决「一律以最新主线为准，旧版本差异全部放弃」，**已对齐为 forge-1.20.1:76-97 的写法**（fabric `:75-96` 逐行一致，仅 `PartyRelations` 包名不同）。
- 复核：`hasTeam` 在 fabric 的 `combat/PartyRelations.java:115` 存在；`ServerLevel#players()`、`Entity#distanceToSqr` 均为 1.20.1 通用 API ⇒ **无需垫片**。
- 若上级认为此项应留在下一批，回滚方式：把 `onBlessingEnd` 恢复为 `PartyRelations.collectTeamPlayers(player)` 单行，并删 `ALLY_RANGE` 常量（`STARLIGHT_ON_EQUIP` 应保留）。

### 5.2 `item/ModItems.java:575` 的 ATM 旧注释（刻意保留）

forge-1.20.1 `:622` **自己**也仍留着「装备时星光 +1；星光兑换星币时额外增加 40%」这条过期注释 ⇒ 为与主线保持一致（「主线怎么实现就怎么实现」），fabric 侧该注释未额外「顺手修正」。

### 5.3 `shop/CardShopMenu.java` / `screen/CardShopScreen.java`

`CardShopMenu.java` 兑换调用点（`:573` `ResourceConversion.starlightToStarCoins(exchanger, -1)`）与主线逐行一致，无需改动；`CardShopScreen.canExchange` 亦已是 `StarLightManager.get` 口径。

---

## 6. 验收实跑原始末段

### 6.1 `:fabric-1.20.1:classes`

```
Reusing configuration cache.
> Task :fabric-1.20.1:compileJava UP-TO-DATE
> Task :fabric-1.20.1:processResources UP-TO-DATE
> Task :fabric-1.20.1:classes UP-TO-DATE

BUILD SUCCESSFUL in 454ms
2 actionable tasks: 2 up-to-date
Configuration cache entry reused.
```

（代码落盘后首次编译的那一次为 `> Task :fabric-1.20.1:compileJava` 实际执行、`BUILD SUCCESSFUL in 4s`；此后增量运行如上。）

### 6.2 `:fabric-1.20.1:remapJar` + 产物目录

```
Reusing configuration cache.
> Task :fabric-1.20.1:processIncludeJars UP-TO-DATE
> Task :fabric-1.20.1:compileJava UP-TO-DATE
> Task :fabric-1.20.1:processResources UP-TO-DATE
> Task :fabric-1.20.1:classes UP-TO-DATE
> Task :fabric-1.20.1:jar
> Task :fabric-1.20.1:remapJar

BUILD SUCCESSFUL in 4s
5 actionable tasks: 2 executed, 3 up-to-date
Configuration cache entry reused.
```

```
$ ls -l fabric-1.20.1/build/libs/
total 2968
-rw-r--r-- 1 xmace 197609 3035669 Nov  1 20:36 astral_dice-1.5.0-rc.7+fabric_1.20.1.jar
```

⇒ 目录中**只有** `astral_dice-1.5.0-rc.7+fabric_1.20.1.jar`（无旧版本残留）。

### 6.3 `python tools/verify_fabric_assets.py`

```
jar = fabric-1.20.1\build\libs\astral_dice-1.5.0-rc.7+fabric_1.20.1.jar

[1] 物品闭环: 注册 157 (方块物品 3) / 模型 158 / 贴图 155 (方块贴图另计)
[2] 标签闭环: 引用 10 / 自建 29 / 有提供者 10
[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
[4] 粒子闭环: 注册 2 / particles/*.json 2
[5] 语言闭环: zh_cn 1094 / en_us 1094 / ja_jp 1094
[6] 手册闭环: 条目 184 / 引用配方 125
[7] 创意标签覆盖: 156/157
    已知与基线一致（不阻塞）: ['repulsion']
[8] 饰品槽位图标: 检查 7 条 icon

=== PASS: 8 项闭环全部通过 ===
```

### 6.4 `python tools/check_lang_sync.py`

```
[OK]   neoforge-1.21.1: en_us=1070 键，3 份语言键集一致
[OK]   forge-1.20.1: en_us=1070 键，3 份语言键集一致
[OK]   neoforge-26.1.2: en_us=1094 键，3 份语言键集一致
[OK]   fabric-1.20.1: en_us=1094 键，3 份语言键集一致
```

⇒ 四线全 `[OK]`；fabric 键集未变（本批未碰 lang）。

### 6.5 追加取证（不属于四条验收，供复核）

**CRLF 自检**（15 个改动文件全部）：`CRLF == LF`、`bareLF == 0`、`bareCR == 0`、无 BOM、以单个 CRLF 结尾。

**remapJar 字节码断言**（python zipfile 扫 class 常量池）：

| 类 | 命中 | 说明 |
|---|---|---|
| `item/chip/AirbagChipItem` | `HIT_CHARGE_GAIN`、`HIT_CHARGE_COOLDOWN_TICKS`、`onIncomingDamage` ✓ | — |
| `component/ModAttachments` | `airbag_hit_charge_cooldown_end`、两个访问器 ✓ | — |
| `item/StarLightManager` | `replenishOnExchange` ✓；`grantStarlightFloorOnEquip`、`getBasePoints` **均不存在** ✓ | — |
| `item/chip/BankCardChipItem` | `STARLIGHT_LOW/HIGH`、`grantStarlightOnEquip` ✓；`grantStarlightFloorOnEquip` 不存在 ✓ | — |
| `combat/DiceCombatContext` / `DiceCombatModifiers` | `eagleScopeMarkLevel` ✓ / `registerExtraDamageModifier` ✓ | — |
| `item/chip/MemberRecommendationChipItem` | `HAND_CARD_LIMIT`、`countHandCards`、`isCardItem` ✓ | — |
| `item/chip/AtmChipItem` | `onEquip` / `onChipUnequip` / `GRANT_BIT_ATM` **全部不存在** ✓ | 两个 override 已彻底移除 |
| `resource/ResourceConversion` | `replenishOnExchange` ✓；`getBasePoints` 不存在 ✓ | — |
| `item/chip/BankCardUnlimitedChipItem` | `ALLY_RANGE`、`STARLIGHT_ON_EQUIP`、`hasTeam`、`collectTeamPlayers` ✓ | remap 后原版方法名变 intermediary，故另取**未重映射**的 `build/classes/java/main/.../BankCardUnlimitedChipItem.class` 复核：`distanceToSqr`、`contains`、`isDeadOrDying` 均在位 ⇒ 32 格收紧确实编译进字节码 |

---

## 7. 无法判定 / 需上级裁决

1. **运行时行为未验证**：按硬约束未启动 Minecraft、未实机 —— 上述全部结论为源码/字节码级静态对齐，**受击充能、手牌闸门、额外加伤、兑换补星光、ATM 移除**的实机手感（数值与时序）未跑。
2. `screen/CardShopScreen` 主线有一个**调试探针**（forge `screen/CardShopScreen.java:939-950`，输出 `get=..:base=..:spendable=..:cd=..:ready=..` 形式日志），fabric 无。该方法是测试资产、不参与玩法，**未移植**（属于测试台范畴，非本批代码对齐）。如需移植请单独下批。
3. 主线 `item/ModItems.java:622` 的 ATM 过期注释在 fabric 侧同步保留（§5.2），是否要**反向**清理主线注释，不在本批权限内。
4. `BankCardUnlimitedChipItem` 的 `ALLY_RANGE` 属清单外对齐（§5.1），若与其它批次的排期冲突请指示回滚。
5. 星光类筹码的 `replenishOnExchange` 与「兑换成功」判定耦合在 `ResourceConversion` 的 `gained > 0`（主线口径）；fabric 侧 `STARLIGHT_PER_COIN = 2` 与主线一致，无额外差异。

---

## 8. 附：功能点对照（对齐前 vs 对齐后）

| 功能点 | 对齐前（fabric） | 对齐后（= forge-1.20.1 主线） |
|---|---|---|
| 安全气囊-受击充能 | 无 `HIT_CHARGE_GAIN` / 无 15 秒冷却 / 无受击入口（只有保命） | 受击（护甲前）若无冷却则 +1 充能，冷却 15 秒（1200 tick），附件 `airbag_hit_charge_cooldown_end` 服务端专用 |
| 会员推荐-手牌闸门 | 无闸门，任意手牌量都发牌 | 手牌（物品栏 + 副手，按堆叠张数）≥ 5 时**不发牌**；价格 -1 不受闸门影响 |
| 鹰眼镜-加伤通道 | 攻击力 `ap += 标记层数 × 2`（吃暴击 ×1.5、参与攻击点差值结算、进反击） | 攻击力通道移除；改为额外加伤（`astral_dice:extra_damage`，层数取命中前；不进反击伤害） |
| 银行卡三档-发放方式 | `grantStarlightFloorOnEquip`（底板） | `grantStarlightOnEquip`（装备会话闸门 + 实际量记账，卸下按账本扣回） |
| 星光兑换 | 兑换后无补充；ATM 额外 +40% 兑换量 | 兑换成功后按当前佩戴四件星光类筹码补充星光（普通 add，不记账、不撤销）；ATM 的 +40% 与装备 +1 星光**全部移除** |
| 用不完筹码-范围 | 无队伍 ⇒ 全服在线玩家 | 无队伍 ⇒ **自身 32 格内**；有队伍仍为同队不限半径（清单外对齐，见 §5.1） |

---

## 9. 复现命令

```bash
cd /d/MCProject/astral_dice_multiloader
cmd.exe //c "gradlew.bat :fabric-1.20.1:classes --console=plain"
cmd.exe //c "gradlew.bat :fabric-1.20.1:remapJar --console=plain"
ls -l fabric-1.20.1/build/libs/
PYTHONIOENCODING=utf-8 python tools/verify_fabric_assets.py
PYTHONIOENCODING=utf-8 python tools/check_lang_sync.py
```
