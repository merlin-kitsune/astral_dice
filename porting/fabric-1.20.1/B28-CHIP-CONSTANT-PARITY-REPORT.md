# B28 · fabric-1.20.1 筹码/立牌常量与 forge 数值一致性对齐报告

- 批次：**B28**（承接 B24，`porting/fabric-1.20.1/B24-LANG-DEDUP-DRIFT-REPORT.md` §2.3 D-N 14 条）
- 日期：2026-11-01；仓库 `F:\MCProject\astral_dice_multiloader`；分支 `multi-main`
- 权威基线：`forge-1.20.1`（P0 次基准）+ `neoforge-1.21.1`（P0 主基准）；`fabric-1.20.1` 为被对齐的移植线
- 本批范围：**只改 fabric-1.20.1 的 java**；lang / recipes / patchouli / `tools/**` / `platform/**` / `mixin/**` / build 文件 / `fabric.mod.json` / `astral_dice.mixins.json` 全部未触碰
- 纪律：未执行 commit / push / stash / add / checkout；未跑 Gradle；未启动游戏；改后逐文件 `bareLF = 0`、UTF-8 无 BOM

## 1. 结论摘要

| 项目 | 数量 | 处置 |
|---|---|---|
| B24 §D-N 条目 | 14 | 去重后 6 个议题（卡牌栏 4 键 / 书签 2 键 / 绿宝石 2 键 / 南希露 2 键 / 传送引擎 2 键 / 咪咪 2 键） |
| **A 类**（forge 有用户裁决证据，fabric 未跟进） | **12 键 / 6 议题** | 已改 fabric java（8 个文件） |
| **B 类**（两线 java 常量相同，fabric 文案写错） | **2 键 / 1 议题** | 本批不动，登记给 lang 批次（§5.1） |
| **C 类**（产品语义分叉，需裁决） | **0 条** | 原任务书把「骰子卡牌栏格数」列为 C 示例，取证后改判 A（§6，供否决） |
| **D 类**（取证不足） | **0 条** | — |

守门：`__javac_check.py` 的 REAL 两节为空；`verify_fabric_assets.py` PASS 8 项；`check_lang_sync.py --line fabric-1.20.1` OK 1094（原始输出见 §8）。

> **需要用户注意的一处判断**：任务书 §C 曾举例「骰子卡牌栏格数由品质还是星级决定」属 C 类（产品分叉）。本批取证后**改判 A 类并已改 fabric**（依据见 §6.2：交接文档逐字记录了用户原话与裁决落地、git 提交、三线实现、fabric 侧时间线）。若用户认为仍应按 C 类处理，请按 §6.3 的两套规则证据回滚该议题涉及的 4 个文件。

## 2. 判定口径与取证方法

每条 D-N 用「**定义（常量声明）+ 使用（调用点）+ 文案（lang 键）**」三方对照判定，不看单侧文案下结论：

- **A 类**：forge 侧有明确的新意图证据（用户裁决日期注释 / 带日期的交接记录 / 用户原话），fabric 只是没跟进 ⇒ 改 fabric java。
- **B 类**：两线 java 常量**相同**，只有 fabric 的文案写错 ⇒ 不在 java 里迁就文案，登记给 lang 批次。
- **C 类**：两条线的规则本身是产品语义分叉，需要产品裁决 ⇒ 不改。
- **D 类**：取证不足 ⇒ 不改并列出缺失证据。

硬约束（本批自守）：**严禁为了对上文案而改数值**；**严禁顺手改文案**（lang 三语一律不动）。

## 3. D-N 14 条逐条分类

| # | 文案键 | 语言 | 类别 | 判定依据（代码权威） | 本批处置 |
|---|---|---|---|---|---|
| 1 | `astral_dice.guide.entry.battle_dice.3` | zh_cn/en_us/ja_jp | **A** | 见 §6.2：handover §4.1am（用户原话 m23070，2026-10-28）+ 三线品阶制实现 | 改 fabric java（DiceTier / DiceCurioItem / ModItems） |
| 2 | `astral_dice.guide.entry.bookmark_chip.1` | 同上 | **A** | forge `BookmarkChipItem.java:17` `DAMAGE_BONUS = 2`，注释「2026-10-15 用户裁决:+1 → +2」 | fabric `:18` → 2 |
| 3 | `astral_dice.guide.entry.diamond_dice.1` | 同上 | **A** | 同 #1（钻石骰子 cardSlots=10） | 同 #1 |
| 4 | `astral_dice.guide.entry.emerald_dice.1` | 同上 | **A** | forge `EmeraldDiceTrade.java:64/67` 为 1:1 且无 `discount()`；handover `:996`（§4.1as，2026-10-29 用户 m01195 下达「移除绿宝石骰子折扣改 1:1」）+ `:1286` 第 22 条「P1 两线（26.1.2 / fabric）仍保留 0.8 折扣与旧 lang，解冻后需同步」 | fabric 改 1:1 并删 `discount()` |
| 5 | `astral_dice.guide.entry.golden_dice.1` | 同上 | **A** | 同 #1（黄金骰子 cardSlots=8） | 同 #1 |
| 6 | `astral_dice.guide.entry.mimi_sign.2` | 同上 | **B** | **两线 `MimiSignItem.RETURNED_CARD_THRESHOLD` 均 100**，fabric 文案写 25 与自身代码矛盾 | 不动，登记 lang（§5.1） |
| 7 | `astral_dice.guide.entry.nancy_lu_sign.2` | 同上 | **A** | forge `NancyLuSignItem.java:54` `PASSIVE_RANGE = 3.0`；fabric `:57` 原为 6.0 | fabric `:58` → 3.0 |
| 8 | `astral_dice.guide.entry.netherite_dice.1` | 同上 | **A** | 同 #1（下界合金骰子 cardSlots=12） | 同 #1 |
| 9 | `astral_dice.guide.entry.warp_engine_chip.1` | 同上 | **A** | forge `WarpEngineChipItem.java:40` `20 * 60 * 3`，注释「2026-10-15 用户裁决:5:00 → 3:00」；fabric 原 `20 * 60 * 5` | fabric `:39` → 3 分钟 |
| 10 | `tooltip.astral_dice.chip.bookmark` | 同上 | **A** | 与 #2 同源 | 随 #2 |
| 11 | `tooltip.astral_dice.chip.warp_engine` | 同上 | **A** | 与 #9 同源 | 随 #9 |
| 12 | `tooltip.astral_dice.emerald_dice_trade` | 同上 | **A** | 与 #4 同源 | 随 #4 |
| 13 | `tooltip.astral_dice.sign.mimi_passive` | 同上 | **B** | 与 #6 同源（java 均 100，fabric 文案 25；另 fabric 多「合成或」） | 不动，登记 lang（§5.1） |
| 14 | `tooltip.astral_dice.sign.nancy_lu_passive` | 同上 | **A** | 与 #7 同源 | 随 #7 |

三语言实测（本批用 python 直读 forge/fabric 的 zh_cn / en_us / ja_jp 逐键比对）：以上 14 个键在**三种语言上全部 DIFF**，即 fabric 三语都停在旧数值；不存在「只有中文写错」的情况。

## 4. A 类：改前 → 改后 + 溯源注释

溯源注释沿用本仓既有风格：行内 `// 对齐 forge-<线>/src/main/java/...:<行>`、javadoc 内 `<p>对齐 ...`；每处都写了 forge 侧的**文件:行**（8 个文件共 9 条溯源注释；`NetherStarDiceItem.java` 本批无常量改动故不加）。

### 4.1 书签筹码伤害加成 +1 → +2

文件：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BookmarkChipItem.java`

```java
// 改前 L16-17
    /** 伤害效果牌伤害加成(固定 +1) */
    public static final int DAMAGE_BONUS = 1;

// 改后 L16-18
    /** 伤害效果牌伤害加成(固定 +2;2026-10-15 用户裁决:+1 → +2) */
    // 对齐 forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BookmarkChipItem.java:17
    public static final int DAMAGE_BONUS = 2;
```

- forge 对照：`BookmarkChipItem.java:16-17` 同值同注释。
- 使用点：`BookmarkChipItem.java:35` `return isEquipped(player) ? DAMAGE_BONUS : 0;`（`damageBonus(Player)`）；伤害汇总侧见 `combat/SpellDamageRegistry.java:152` javadoc「+ 书签筹码固定 +DAMAGE_BONUS(装备时)」。
- 行为变化：装备书签筹码时，伤害类效果牌伤害加成由 +1 变 +2。

### 4.2 传送引擎共享冷却 5:00 → 3:00

文件：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/WarpEngineChipItem.java`

```java
// 改前 L29-30 / L37-38 / L59
 * <p>传送门与 Waystone 触发共享 5:00 冷却:通过任意一种方式获得充能后,5 分钟内不能再通过传送门或 Waystone 触发;
    /** 传送门/Waystone 触发的共享冷却时长(5:00) */
    public static final int PORTAL_WAYSTONE_COOLDOWN_TICKS = 20 * 60 * 5;
    /** 传送门/Waystone 专属结算:共享 5:00 冷却 */

// 改后 L29-30 / L37-39 / L60
 * <p>传送门与 Waystone 触发共享 3:00 冷却(2026-10-15 用户裁决:5:00 → 3:00):通过任意一种方式获得充能后,3 分钟内不能再通过传送门或 Waystone 触发;
    /** 传送门/Waystone 触发的共享冷却时长(3:00;2026-10-15 用户裁决:5:00 → 3:00) */
    // 对齐 forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/WarpEngineChipItem.java:40
    public static final int PORTAL_WAYSTONE_COOLDOWN_TICKS = 20 * 60 * 3;
    /** 传送门/Waystone 专属结算:共享 3:00 冷却 */
```

- forge 对照：`WarpEngineChipItem.java:39` 注释「3:00;2026-10-15 用户裁决:5:00 → 3:00」、`:40` `= 20 * 60 * 3`。
- 使用点：`WarpEngineChipItem.java:66` `ModAttachments.setWarpEnginePortalCooldownEnd(player, now + PORTAL_WAYSTONE_COOLDOWN_TICKS);`（全文件唯一引用该常量处）。
- 同文件另两项 `STACK_GAIN = 2` / `SPEED_DURATION_TICKS = 200` 两线本就一致，未动。

### 4.3 绿宝石骰子：20% 折扣 → 1:1 等值代付

文件：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/EmeraldDiceTrade.java`

```java
// 改前 L64 / L67-73（javadoc 亦写「20% 折扣」）
    /** 绿宝石费用 → 星币费用(1:1),数量按 20% 折扣(至少 1)。非绿宝石费用原样返回。 */
        return new ItemStack(ModItems.STAR_COIN.get(), discount(stack.getCount()));
    }

    /** 20% 折扣(向下取整,至少 1)。 */
    public static int discount(int count) {
        return Math.max(1, (int) Math.floor(count * 0.8));
    }

// 改后 L64-65 / L68（discount() 整段删除）
    /** 绿宝石费用 → 星币费用(1:1),数量保持不变(至少 1)。非绿宝石费用原样返回。
     *  <p>对齐 forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/EmeraldDiceTrade.java:67(2026-10-29 用户裁决:0.8 折扣 → 1:1)。 */
        return new ItemStack(ModItems.STAR_COIN.get(), Math.max(1, stack.getCount()));
```

- forge 对照：`EmeraldDiceTrade.java:64` javadoc「1:1…数量保持不变(至少 1)」、`:67` `Math.max(1, stack.getCount())`；`grep -c 'discount('` 在 forge 该文件 = **0**，本批改后 fabric 亦 = **0**。
- 同文件代码注释同步（非 lang）：`:16`「…按 1:1 改为星币支付(等值替换)」、`:19`「…把绿宝石费用替换为星币(1:1)」、`:72`「(星币费用,1:1)」。
- 使用点（未改，仅记录）：`mixin/trade/MerchantOfferMixin.java:31,43,54,64` 与 `mixin/trade/ServerPlayerMixin.java:27` 调 `transform / transformOffers`。
- 内部一致性：`trade/CardTraderTrades.java:69` 的 `MIN_EMERALD_PRICE` 注释原本就写「星币支付按 `EmeraldDiceTrade` 的 1:1 折算 ⇒ 最低 5 星币」，改动前 fabric 实现是打折，与该注释自相矛盾；本批改动消除了这处矛盾（与 forge「最低价 4→5」口径一致）。

### 4.4 南希露立牌被动判定半径 6 格 → 3 格

文件：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/NancyLuSignItem.java`

```java
// 改前 L43 / L57
 * - 骰神赐福结束后,若周围 6 格内没有敌对生物:攻击力 +3 并获得一张随机战斗牌;
    public static final double PASSIVE_RANGE = 6.0;

// 改后 L43 / L57-58
 * - 骰神赐福结束后,若周围 3 格内没有敌对生物:攻击力 +3 并获得一张随机战斗牌;
    // 对齐 forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/NancyLuSignItem.java:54
    public static final double PASSIVE_RANGE = 3.0;
```

- forge 对照：`NancyLuSignItem.java:54` `PASSIVE_RANGE = 3.0`（无行内注释，故 fabric 侧溯源注释不带日期）。
- 使用点：`NancyLuSignItem.java:249` `player.getBoundingBox().inflate(PASSIVE_RANGE)`（唯一使用点）。
- 未动（属 D-M 机制分叉，不在 D-N 14 条内）：主动段 `ACTIVE_BONUS_MULTIPLIER = 2` / `ACTIVE_MIN_BONUS = 2`（forge 为固定 `ACTIVE_ATTACK_BONUS = 6`）。

### 4.5 骰子卡牌栏格数：星级制 → 品阶制（原 C 判，改判 A）

涉及 4 个文件，其中 3 个有常量/结构改动、1 个纯注释：

**（1）`item/dice/DiceTier.java` —— record 从 3 分量扩为 4 分量**

```java
// 改前 L10 / L12-13 / L26-27
 * 骰子阶层定义:统一描述某款骰子的 Curios 动态槽位规则(筹码栏)。
 * 卡牌栏格数不再属于品阶属性:已改为仅由骰子星级决定(0★=4、1★=6、2★=8、3★=12,
 * 攻防各半),四款骰子同星级格数相同,见 {@link DiceCurioItem#getCardSlots(ItemStack)}。
public record DiceTier(String id, Supplier<Item> item,
                      java.util.function.IntUnaryOperator chipBonus) {

// 改后 L10 / L12-19 / L25 / L34-35
 * 骰子阶层定义:统一描述某款骰子的 Curios 动态槽位规则(筹码栏与卡牌栏格数)。
 * 卡牌栏格数**由骰子品质(品阶)决定,与星级无关**(用户裁决 2026-10-28):
 * 基础 4(各 2) / T1 8(各 4) / T2 10(各 5) / T3 12(各 6) / T4 12(各 6),由 {@link #cardSlots()} 逐骰子登记;
 * 星级只影响费用上限与赐福强度。
 *
 * <p>溯源:cardSlots 记录分量对齐 forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/DiceTier.java:30,
 * 13 条骰子注册取值对齐 forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java:246。
 * @param cardSlots  卡牌栏总格数(按品阶固定,攻防各半;与星级无关)
public record DiceTier(String id, Supplier<Item> item,
                      java.util.function.IntUnaryOperator chipBonus,
                      int cardSlots) {
```

**（2）`item/dice/DiceCurioItem.java` —— 删星级数组，改读品阶**

```java
// 改前 L43-46 / L57
    // 卡牌放置栏总槽位数:由"卡牌配置星级"决定,与骰子品阶无关——0★=4(攻防各2)、1★=6(各3)、
    // 2★=8(各4)、3★=12(各6);星级超出 0-3 时按最近档钳制。
    // 下界之星骰子(T4):卡牌槽与费用点数始终按最高档 3★ 配置(12 格 / 费用上限各 6)。
    private static final int[] CARD_SLOTS_BY_STAR = {4, 6, 8, 12};
        return CARD_SLOTS_BY_STAR[configStarLevel(stack)];

// 改后 L44-48 / L58-61（另 L17 新增 import com.merlinkitsune.starengine.component.GameplayConstants;）
    // 卡牌放置栏总槽位数**由骰子品质(品阶)决定,与星级无关**(用户裁决 2026-10-28):
    // 基础 4(各 2) / T1 8(各 4) / T2 10(各 5) / T3 12(各 6) / T4 12(各 6),
    // 由 {@link DiceTier#cardSlots()} 逐骰子登记;星级只影响费用上限与赐福强度。
    // 未登记的骰子(理论上不存在)回退 GameplayConstants.CARD_SLOTS_TOTAL。
    // 对齐 forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/DiceCurioItem.java:59
        DiceTier tier = DiceTierRegistry.get(stack);
        return tier != null ? tier.cardSlots() : GameplayConstants.CARD_SLOTS_TOTAL;
```

**（3）`item/ModItems.java` —— 13 条骰子注册补第 4 参 cardSlots**

```java
// 改前 L232 / L235-236 / ...
        // 卡牌栏格数不再按品阶区分,仅由星级决定(见 DiceCurioItem.getCardSlots)
        DiceTierRegistry.register(new DiceTier("dice", () -> DICE.get(),
                s -> s));

// 改后 L242-243 / L245-246 / ...
        // 卡牌栏格数按品阶登记(第 4 参 cardSlots,与星级无关);星级只影响费用上限与赐福强度
        // 对齐 forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java:246
        DiceTierRegistry.register(new DiceTier("dice", () -> DICE.get(),
                s -> s, 4));
```

13 条取值（与 forge `ModItems.java:247` 起逐条同值，chipBonus 偏移一律保持原样）：

| 骰子 id | chipBonus | cardSlots | fabric 行 |
|---|---|---|---|
| `dice` | `s` | 4 | L245-246 |
| `golden_dice` | `1 + s` | 8 | L248-249 |
| `glass_dice` | `1 + s` | 8 | L251-252 |
| `diamond_dice` | `2 + s` | 10 | L254-255 |
| `netherite_dice` | `3 + s` | 12 | L257-258 |
| `emerald_dice` | `2 + s` | 10 | L260-261 |
| `obsidian_dice` | `2 + s` | 10 | L263-264 |
| `netherrack_dice` | `1 + s` | 8 | L266-267 |
| `weird_dice` | `2 + s` | 10 | L269-270 |
| `crimson_dice` | `3 + s` | 12 | L272-273 |
| `amethyst_dice` | `2 + s` | 10 | L275-276 |
| `ender_dice` | `3 + s` | 12 | L278-279 |
| `nether_star_dice` | `4 + s` | 12 | L281-282 |

同文件 L189 另有一处**代码注释**对齐（非 lang）：旧「佩戴后村民交易绿宝石费用改为星币并享 20% 折扣」→「佩戴后村民交易绿宝石费用按 1:1 改为星币」（与 forge `ModItems.java:193` 同句）。

**（4）`item/dice/NetherStarDiceItem.java` —— 纯 javadoc**

```java
// 改前 L18-19
 * - 卡牌槽与费用点数始终按最高档(3★:卡牌栏 12 格、攻防费用上限各 6,见
 *   {@link DiceCurioItem#getCardSlots} 与 {@link ...GameplayConstants#cardCostForStar});

// 改后 L18-20
 * - 卡牌栏 12 格(由 T4 品阶固定,与星级无关,见 {@link DiceCurioItem#getCardSlots});
 *   费用点数始终按最高档(3★:攻防费用上限各 6,见
 *   {@link com.merlinkitsune.starengine.component.GameplayConstants#cardCostForStar});
```

- 构造点收敛核验：`grep -rn 'new DiceTier('` 在 fabric 仅命中 `item/ModItems.java` 共 **13 处**，已全部补第 4 参；`.cardSlots()` 仅 `DiceCurioItem.java:60` 一处消费。
- 费用上限侧**未动**：fabric 仍由 `GameplayConstants.cardCostForStar`（0★=3 / 1★=4 / 2★=5 / 3★=6）决定，与 forge 口径一致（星级只影响费用上限与赐福强度）。

## 5. B 类清单（只登记，本批不动 java、不动 lang）

判定：**两线的 java 常量完全相同**，fabric 只是文案写错 —— 按任务书纪律「不为文案改数值」，本批不碰，登记给 lang 批次。

### 5.1 B 类主表（2 键 / 1 议题：咪咪立牌已返还卡牌阈值）

| java 常量值（forge = fabric，本批逐线核对） | 文案键 | 两线文案取值（forge ⇒ fabric） |
|---|---|---|
| `MimiSignItem.RETURNED_CARD_THRESHOLD = 100`<br>（forge `item/sign/MimiSignItem.java` / fabric 同路径，本批未改） | `astral_dice.guide.entry.mimi_sign.2` | <b>zh_cn</b><br>forge：被动技能-过期回收：返还卡牌时，每获得一张战斗牌获得 1 星币；装备时筹码栏位 +1；每主动返还累计 100 张战斗牌，获得一个随机筹码（每次主动最多 1 个）。<br>fabric：被动技能-过期回收：合成或返还卡牌时，每获得一张战斗牌获得 1 星币；装备时筹码栏位 +1；每主动返还累计 25 张战斗牌，获得一个随机筹码（每次主动最多 1 个）。<br><b>en_us</b><br>forge：Passive Skill - Recycle: when you gain a battle card through your active skill's returns, gain 1 Star Coin; while equipped, chip slots +1; every 100 battle cards returned by your active skill grants a random chip (at most 1 per use).<br>fabric：Passive Skill - Recycle: when you gain a battle card through crafting or your active skill's returns, gain 1 Star Coin; while equipped, chip slots +1; every 25 battle cards returned by your active skill grants a random chip (at most 1 per use).<br><b>ja_jp</b><br>forge：パッシブスキル-期限切れ回収：カードを返却した時、戦闘カードを 1 枚獲得する毎に 1 スターコインを獲得；装備時にチップスロット +1；アクティブで累計 100 枚の戦闘カードを返却する毎に、ランダムなチップを 1 つ獲得する（1 回のアクティブにつき最大 1 個）。<br>fabric：パッシブスキル-期限切れ回収：カードを合成または返却した時、戦闘カードを 1 枚獲得する毎に 1 スターコインを獲得；装備時にチップスロット +1；アクティブで累計 25 枚の戦闘カードを返却する毎に、ランダムなチップを 1 つ獲得する（1 回のアクティブにつき最大 1 個）。 |
| 同上（同一常量驱动的 tooltip） | `tooltip.astral_dice.sign.mimi_passive` | <b>zh_cn</b><br>forge：返还卡牌时，每获得一张战斗牌获得 §e1§7 星币 ⏎ 装备时筹码栏位 §e+1§r ⏎ 每主动返还累计 §e100§7 张战斗牌后获得一个随机筹码（每次主动最多 §e1§7 个）<br>fabric：合成或返还卡牌时，每获得一张战斗牌获得 §e1§7 星币 ⏎ 装备时筹码栏位 §e+1§r ⏎ 每主动返还累计 §e25§7 张战斗牌后获得一个随机筹码（每次主动最多 §e1§7 个）<br><b>en_us</b><br>forge：Gain §e1§7 Star Coin per battle card gained through your active skill's returns ⏎ while equipped, chip slots §e+1§r ⏎ every §e100§7 battle cards returned by your active skill grants a random chip (at most §e1§7 per use)<br>fabric：Gain §e1§7 Star Coin per battle card gained through crafting or your active skill's returns ⏎ while equipped, chip slots §e+1§r ⏎ every §e25§7 battle cards returned by your active skill grants a random chip (at most §e1§7 per use)<br><b>ja_jp</b><br>forge：カードを返却した時、戦闘カードを 1 枚獲得する毎に §e1§7 スターコインを獲得 ⏎ 装備時、チップスロット §e+1§r ⏎ アクティブで累計 §e100§7 枚の戦闘カードを返却する毎に、ランダムなチップを 1 つ獲得（1 回のアクティブにつき最大 §e1§7 個）<br>fabric：カードを合成または返却した時、戦闘カードを 1 枚獲得する毎に §e1§7 スターコインを獲得 ⏎ 装備時、チップスロット §e+1§r ⏎ アクティブで累計 §e25§7 枚の戦闘カードを返却する毎に、ランダムなチップを 1 つ獲得（1 回のアクティブにつき最大 §e1§7 個） |

逐键差异性质：**不是数值口径漂移**。fabric 文案把 `100` 写成 `25`（与自身 java 常量矛盾），并在「返还卡牌时」前多写了「合成或」。三语表现一致：zh_cn:DIFF / en_us:DIFF / ja_jp:DIFF。

> 反证（说明为什么本批不能改 java 来迁就文案）：fabric 的 `MimiSignItem` 与其使用点未出现任何 25；若为对齐文案把常量改成 25，等于**用代码去实现一个未被裁决的错误文案**，且会与 forge/neoforge 三线全部不一致。

### 5.2 附：A 类改动引入的 lang 残留漂移（8 键，同样交 lang 批次）

这些键的 java 已按 forge 对齐，但 fabric 的三语文案仍停在旧值；lang 不在本批允许范围内，故一并登记。

| java 常量（fabric 本批已对齐 forge） | 文案键 | 三语差异 |
|---|---|---|
| `WarpEngineChipItem.PORTAL_WAYSTONE_COOLDOWN_TICKS = 20*60*3` | `astral_dice.guide.entry.warp_engine_chip.1` | zh_cn:DIFF / en_us:DIFF / ja_jp:DIFF<br>zh_cn：forge「…珍珠、末影骰子不死图腾后的安全瞬移、维度传送门、Waystone（需安装 Waystones）。传送门与 Waystone 触发共享 3:00 冷却；末影珍珠与末影骰子瞬移无冷却。」<br>fabric：旧值（见 §5.4 全文） |
| 同上（tooltip） | `tooltip.astral_dice.chip.warp_engine` | zh_cn:DIFF / en_us:DIFF / ja_jp:DIFF<br>zh_cn：forge「…触发瞬移时：充能 §e+2§7，并获得 §9迅捷 (0:10)§r ⏎ 维度传送门与 Waystone 共享 §93:00§7 冷却；末影珍珠与末影骰子瞬移无冷却」<br>fabric：旧值（见 §5.4 全文） |
| `BookmarkChipItem.DAMAGE_BONUS = 2` | `astral_dice.guide.entry.bookmark_chip.1` | zh_cn:DIFF / en_us:DIFF / ja_jp:DIFF<br>zh_cn：forge「…使伤害类效果牌伤害加成 +2。」<br>fabric：旧值（见 §5.4 全文） |
| 同上（tooltip） | `tooltip.astral_dice.chip.bookmark` | zh_cn:DIFF / en_us:DIFF / ja_jp:DIFF<br>zh_cn：forge「…使伤害类效果牌伤害加成 §e+2§r」<br>fabric：旧值（见 §5.4 全文） |
| `EmeraldDiceTrade.transform* → 1:1`（`discount()` 已删） | `astral_dice.guide.entry.emerald_dice.1` | zh_cn:DIFF / en_us:DIFF / ja_jp:DIFF<br>zh_cn：forge「…绿宝石骰子。与钻石骰子同阶，可由钻石骰子升级；佩戴后与村民交易时按 1:1 用星币代替绿宝石支付。」<br>fabric：旧值（见 §5.4 全文） |
| 同上（tooltip） | `tooltip.astral_dice.emerald_dice_trade` | zh_cn:DIFF / en_us:DIFF / ja_jp:DIFF<br>zh_cn：forge「…与村民交易时用星币代替绿宝石支付（§e1:1§2）」<br>fabric：旧值（见 §5.4 全文） |
| `NancyLuSignItem.PASSIVE_RANGE = 3.0` | `astral_dice.guide.entry.nancy_lu_sign.2` | zh_cn:DIFF / en_us:DIFF / ja_jp:DIFF<br>zh_cn：forge「…被动技能-网络防火墙：免疫末影珍珠传送伤害。骰神赐福结束后，若周围 3 格内没有敌对生物，则攻击力 +3 并获得一张随机战斗牌；否则防御力 +3。」<br>fabric：旧值（见 §5.4 全文） |
| 同上（tooltip） | `tooltip.astral_dice.sign.nancy_lu_passive` | zh_cn:DIFF / en_us:DIFF / ja_jp:DIFF<br>zh_cn：forge「…免疫末影珍珠传送时的伤害。骰神赐福效果结束后，若周围 §e3§7 格范围内没有敌对生物，则攻击力 §e+3§7 并获得一张随机战斗牌，否则防御力 §e+3§r」<br>fabric：旧值（见 §5.4 全文） |

### 5.3 卡牌栏 4 键：建议整段替换为 forge 文本（非逐词修改）

这 4 键在 fabric 里**整段**是按「星级决定卡牌栏」写的，与已对齐的 java（品阶制）冲突；逐词改会留下语义残渣，建议整段以 forge 文本为准：

- `astral_dice.guide.entry.battle_dice.3`：zh_cn:DIFF / en_us:DIFF / ja_jp:DIFF —— 替换源见 §5.4 的 forge 全文。
- `astral_dice.guide.entry.diamond_dice.1`：zh_cn:DIFF / en_us:DIFF / ja_jp:DIFF —— 替换源见 §5.4 的 forge 全文。
- `astral_dice.guide.entry.golden_dice.1`：zh_cn:DIFF / en_us:DIFF / ja_jp:DIFF —— 替换源见 §5.4 的 forge 全文。
- `astral_dice.guide.entry.netherite_dice.1`：zh_cn:DIFF / en_us:DIFF / ja_jp:DIFF —— 替换源见 §5.4 的 forge 全文。

### 5.4 逐键全文（lang 批次可直接照抄的 forge 值）

#### `astral_dice.guide.entry.mimi_sign.2`

```text
[zh_cn] forge  : 被动技能-过期回收：返还卡牌时，每获得一张战斗牌获得 1 星币；装备时筹码栏位 +1；每主动返还累计 100 张战斗牌，获得一个随机筹码（每次主动最多 1 个）。
[zh_cn] fabric : 被动技能-过期回收：合成或返还卡牌时，每获得一张战斗牌获得 1 星币；装备时筹码栏位 +1；每主动返还累计 25 张战斗牌，获得一个随机筹码（每次主动最多 1 个）。
[en_us] forge  : Passive Skill - Recycle: when you gain a battle card through your active skill's returns, gain 1 Star Coin; while equipped, chip slots +1; every 100 battle cards returned by your active skill grants a random chip (at most 1 per use).
[en_us] fabric : Passive Skill - Recycle: when you gain a battle card through crafting or your active skill's returns, gain 1 Star Coin; while equipped, chip slots +1; every 25 battle cards returned by your active skill grants a random chip (at most 1 per use).
[ja_jp] forge  : パッシブスキル-期限切れ回収：カードを返却した時、戦闘カードを 1 枚獲得する毎に 1 スターコインを獲得；装備時にチップスロット +1；アクティブで累計 100 枚の戦闘カードを返却する毎に、ランダムなチップを 1 つ獲得する（1 回のアクティブにつき最大 1 個）。
[ja_jp] fabric : パッシブスキル-期限切れ回収：カードを合成または返却した時、戦闘カードを 1 枚獲得する毎に 1 スターコインを獲得；装備時にチップスロット +1；アクティブで累計 25 枚の戦闘カードを返却する毎に、ランダムなチップを 1 つ獲得する（1 回のアクティブにつき最大 1 個）。
```

#### `tooltip.astral_dice.sign.mimi_passive`

```text
[zh_cn] forge  : 返还卡牌时，每获得一张战斗牌获得 §e1§7 星币\n装备时筹码栏位 §e+1§r\n每主动返还累计 §e100§7 张战斗牌后获得一个随机筹码（每次主动最多 §e1§7 个）
[zh_cn] fabric : 合成或返还卡牌时，每获得一张战斗牌获得 §e1§7 星币\n装备时筹码栏位 §e+1§r\n每主动返还累计 §e25§7 张战斗牌后获得一个随机筹码（每次主动最多 §e1§7 个）
[en_us] forge  : Gain §e1§7 Star Coin per battle card gained through your active skill's returns\nwhile equipped, chip slots §e+1§r\nevery §e100§7 battle cards returned by your active skill grants a random chip (at most §e1§7 per use)
[en_us] fabric : Gain §e1§7 Star Coin per battle card gained through crafting or your active skill's returns\nwhile equipped, chip slots §e+1§r\nevery §e25§7 battle cards returned by your active skill grants a random chip (at most §e1§7 per use)
[ja_jp] forge  : カードを返却した時、戦闘カードを 1 枚獲得する毎に §e1§7 スターコインを獲得\n装備時、チップスロット §e+1§r\nアクティブで累計 §e100§7 枚の戦闘カードを返却する毎に、ランダムなチップを 1 つ獲得（1 回のアクティブにつき最大 §e1§7 個）
[ja_jp] fabric : カードを合成または返却した時、戦闘カードを 1 枚獲得する毎に §e1§7 スターコインを獲得\n装備時、チップスロット §e+1§r\nアクティブで累計 §e25§7 枚の戦闘カードを返却する毎に、ランダムなチップを 1 つ獲得（1 回のアクティブにつき最大 §e1§7 個）
```

#### `astral_dice.guide.entry.warp_engine_chip.1`

```text
[zh_cn] forge  : 触发瞬移时，充能 +2 并获得迅捷 (0:10)。计入的传送：末影珍珠、末影骰子不死图腾后的安全瞬移、维度传送门、Waystone（需安装 Waystones）。传送门与 Waystone 触发共享 3:00 冷却；末影珍珠与末影骰子瞬移无冷却。
[zh_cn] fabric : 触发瞬移时，充能 +2 并获得迅捷 (0:10)。计入的传送：末影珍珠、末影骰子不死图腾后的安全瞬移、维度传送门、Waystone（需安装 Waystones）。传送门与 Waystone 触发共享 5:00 冷却；末影珍珠与末影骰子瞬移无冷却。
[en_us] forge  : On teleport, charge +2 and gain Speed (0:10). Counted warps: ender pearl, the Ender Die's safe warp after the death totem, dimension portals, and Waystones (requires Waystones). Portals and Waystones share a 3:00 cooldown; ender pearl and Ender Die warps have no cooldown.
[en_us] fabric : On teleport, charge +2 and gain Speed (0:10). Counted warps: ender pearl, the Ender Die's safe warp after the death totem, dimension portals, and Waystones (requires Waystones). Portals and Waystones share a 5:00 cooldown; ender pearl and Ender Die warps have no cooldown.
[ja_jp] forge  : テレポート発動時、チャージ +2 と移動速度上昇 (0:10) を獲得。対象となるテレポート：エンダーパール、エンダーのダイスの不死のトーテム後の安全テレポート、ディメンション間ポータル、Waystone（Waystones が必要）。ポータルと Waystone は 3:00 のクールダウンを共有；エンダーパールとエンダーのダイスのテレポートはクールダウンなし。
[ja_jp] fabric : テレポート発動時、チャージ +2 と移動速度上昇 (0:10) を獲得。対象となるテレポート：エンダーパール、エンダーのダイスの不死のトーテム後の安全テレポート、ディメンション間ポータル、Waystone（Waystones が必要）。ポータルと Waystone は 5:00 のクールダウンを共有；エンダーパールとエンダーのダイスのテレポートはクールダウンなし。
```

#### `tooltip.astral_dice.chip.warp_engine`

```text
[zh_cn] forge  : 触发瞬移时：充能 §e+2§7，并获得 §9迅捷 (0:10)§r\n维度传送门与 Waystone 共享 §93:00§7 冷却；末影珍珠与末影骰子瞬移无冷却
[zh_cn] fabric : 触发瞬移时：充能 §e+2§7，并获得 §9迅捷 (0:10)§r\n维度传送门与 Waystone 共享 §95:00§7 冷却；末影珍珠与末影骰子瞬移无冷却
[en_us] forge  : On teleport: charge §e+2§7 and gain §9Speed (0:10)§r\nDimension portals and Waystones share a §93:00§7 cooldown; ender pearl and Ender Die warps have no cooldown
[en_us] fabric : On teleport: charge §e+2§7 and gain §9Speed (0:10)§r\nDimension portals and Waystones share a §95:00§7 cooldown; ender pearl and Ender Die warps have no cooldown
[ja_jp] forge  : テレポート発動時：チャージ §e+2§7、§9移動速度上昇 (0:10)§r を獲得\nディメンション間ポータルと Waystone は §93:00§7 のクールダウンを共有；エンダーパールとエンダーのダイスのテレポートはクールダウンなし
[ja_jp] fabric : テレポート発動時：チャージ §e+2§7、§9移動速度上昇 (0:10)§r を獲得\nディメンション間ポータルと Waystone は §95:00§7 のクールダウンを共有；エンダーパールとエンダーのダイスのテレポートはクールダウンなし
```

#### `astral_dice.guide.entry.bookmark_chip.1`

```text
[zh_cn] forge  : 使伤害类效果牌伤害加成 +2。
[zh_cn] fabric : 使伤害类效果牌伤害加成 +1。
[en_us] forge  : Damage effect cards gain +2 damage bonus.
[en_us] fabric : Damage effect cards gain +1 damage bonus.
[ja_jp] forge  : ダメージ系効果カードのダメージボーナス +2。
[ja_jp] fabric : ダメージ系効果カードのダメージボーナス +1。
```

#### `tooltip.astral_dice.chip.bookmark`

```text
[zh_cn] forge  : 使伤害类效果牌伤害加成 §e+2§r
[zh_cn] fabric : 使伤害类效果牌伤害加成 §e+1§r
[en_us] forge  : Damage effect card damage bonus §e+2§r
[en_us] fabric : Damage effect card damage bonus §e+1§r
[ja_jp] forge  : ダメージ系効果カードのダメージボーナス §e+2§r
[ja_jp] fabric : ダメージ系効果カードのダメージボーナス §e+1§r
```

#### `astral_dice.guide.entry.emerald_dice.1`

```text
[zh_cn] forge  : 绿宝石骰子。与钻石骰子同阶，可由钻石骰子升级；佩戴后与村民交易时按 1:1 用星币代替绿宝石支付。
[zh_cn] fabric : 绿宝石骰子。与钻石骰子同阶，可由钻石骰子升级；佩戴后与村民交易时用星币代替绿宝石，并享受 20%% 折扣。
[en_us] forge  : Emerald die. Same tier as the diamond die and upgradeable from it; while equipped, villager trades use Star Coins instead of Emeralds at a 1:1 rate.
[en_us] fabric : Emerald die. Same tier as the diamond die and upgradeable from it; while equipped, villager trades use Star Coins instead of Emeralds with a 20%% discount.
[ja_jp] forge  : エメラルドのダイス。ダイヤモンドのダイスと同品位で、ダイヤモンドのダイスからアップグレードできる；装着すると村人との取引でエメラルドの代わりにスターコインを 1:1 で使用できる。
[ja_jp] fabric : エメラルドのダイス。ダイヤモンドのダイスと同品位で、ダイヤモンドのダイスからアップグレードできる；装着すると村人との取引でエメラルドの代わりにスターコインを使用し、20%% の割引を受ける。
```

#### `tooltip.astral_dice.emerald_dice_trade`

```text
[zh_cn] forge  : 与村民交易时用星币代替绿宝石支付（§e1:1§2）
[zh_cn] fabric : 与村民交易时用星币代替绿宝石，并享受 §e20%%§2 折扣
[en_us] forge  : Uses Star Coins instead of Emeralds in villager trades (§e1:1§2)
[en_us] fabric : Uses Star Coins instead of Emeralds in villager trades, with a §e20%%§2 discount
[ja_jp] forge  : 村人との取引でエメラルドの代わりにスターコインで支払う（§e1:1§2）
[ja_jp] fabric : 村人との取引でエメラルドの代わりにスターコインを使用し、§e20%%§2 の割引を受ける
```

#### `astral_dice.guide.entry.nancy_lu_sign.2`

```text
[zh_cn] forge  : 被动技能-网络防火墙：免疫末影珍珠传送伤害。骰神赐福结束后，若周围 3 格内没有敌对生物，则攻击力 +3 并获得一张随机战斗牌；否则防御力 +3。
[zh_cn] fabric : 被动技能-网络防火墙：免疫末影珍珠传送伤害。骰神赐福结束后，若周围 6 格内没有敌对生物，则攻击力 +3 并获得一张随机战斗牌；否则防御力 +3。
[en_us] forge  : Passive Skill - Network Firewall: immune to Ender Pearl teleport damage. After a Dice Blessing ends, if there are no hostiles within 3 blocks, gain +3 attack and a random battle card; otherwise gain +3 defense.
[en_us] fabric : Passive Skill - Network Firewall: immune to Ender Pearl teleport damage. After a Dice Blessing ends, if there are no hostiles within 6 blocks, gain +3 attack and a random battle card; otherwise gain +3 defense.
[ja_jp] forge  : パッシブスキル-ネットワークファイアウォール：エンダーパール転送ダメージを無効化。ダイス神の加護終了後、周囲 3 ブロック以内に敵対モブがいない場合は攻撃力 +3 とランダムな戦闘カードを 1 枚獲得；それ以外は防御力 +3。
[ja_jp] fabric : パッシブスキル-ネットワークファイアウォール：エンダーパール転送ダメージを無効化。ダイス神の加護終了後、周囲 6 ブロック以内に敵対モブがいない場合は攻撃力 +3 とランダムな戦闘カードを 1 枚獲得；それ以外は防御力 +3。
```

#### `tooltip.astral_dice.sign.nancy_lu_passive`

```text
[zh_cn] forge  : 免疫末影珍珠传送时的伤害。骰神赐福效果结束后，若周围 §e3§7 格范围内没有敌对生物，则攻击力 §e+3§7 并获得一张随机战斗牌，否则防御力 §e+3§r
[zh_cn] fabric : 免疫末影珍珠传送时的伤害。骰神赐福效果结束后，若周围 §e6§7 格范围内没有敌对生物，则攻击力 §e+3§7 并获得一张随机战斗牌，否则防御力 §e+3§r
[en_us] forge  : Immune to Ender Pearl teleport damage. After Dice Blessing ends, if there are no hostiles within §e3§7 blocks, gain §e+3§7 attack and a random battle card; otherwise gain §e+3§7 defense
[en_us] fabric : Immune to Ender Pearl teleport damage. After Dice Blessing ends, if there are no hostiles within §e6§7 blocks, gain §e+3§7 attack and a random battle card; otherwise gain §e+3§7 defense
[ja_jp] forge  : エンダーパール転送時のダメージを無効化。ダイス神の加護終了後、周囲 §e3§7 ブロック以内に敵対モブがいない場合は攻撃力 §e+3§7 とランダムな戦闘カード 1 枚を獲得、それ以外は防御力 §e+3§r
[ja_jp] fabric : エンダーパール転送時のダメージを無効化。ダイス神の加護終了後、周囲 §e6§7 ブロック以内に敵対モブがいない場合は攻撃力 §e+3§7 とランダムな戦闘カード 1 枚を獲得、それ以外は防御力 §e+3§r
```

#### `astral_dice.guide.entry.battle_dice.3`

```text
[zh_cn] forge  : 骰子可经铁砧以星币升星：星级越高赐福加成越强、卡牌费用上限越高（0★=3、1★=4、2★=5、3★=6）；卡牌栏格数由骰子品质决定（基础 4 格、黄金/玻璃/下界岩 8 格、钻石/绿宝石/黑曜石/诡异/紫晶 10 格、下界合金/绯红/末影 12 格，攻防各半），与星级无关；基础→黄金→钻石→下界合金的升级链解锁更多筹码栏位。
[zh_cn] fabric : 骰子可经铁砧以星币升星，星级越高赐福加成越强，卡牌栏格数也越多（0★=4、1★=6、2★=8、3★=12，攻防各半）；基础→黄金→钻石→下界合金的升级链解锁更多筹码栏位（卡牌栏格数由星级决定，各品阶同星级格数相同；同一阶层的骰子均可作为升级基础）。
[en_us] forge  : Upgrade a die on the anvil with Star Coins: higher star levels grant stronger blessings and a higher card cost cap (0 star=3, 1 star=4, 2 star=5, 3 star=6); the number of card slots is set by the die tier (base 4, Golden/Glass/Netherrack 8, Diamond/Emerald/Obsidian/Weird/Amethyst 10, Netherite/Crimson/Ender 12, half attack and half defense) and does not change with stars; the base - golden - diamond - netherite chain unlocks more chip slots.
[en_us] fabric : Upgrade a die on the anvil with Star Coins to raise its star level for stronger blessings and more card slots (0★=4, 1★=6, 2★=8, 3★=12, half attack/half defense); the base → golden → diamond → netherite chain unlocks more chip slots (card slots follow the star level only, identical across tiers at the same star; any die of the same tier can be used as the upgrade base).
[ja_jp] forge  : ダイスは金床でスターコインを使って★を上げられる：★が高いほど加護ボーナスが強く、カードのコスト上限も上がる（0★=3、1★=4、2★=5、3★=6）；カード欄の枠数はダイスの品位で決まり（基本 4 枠、金・ガラス・ネザーラック 8 枠、ダイヤモンド・エメラルド・黒曜石・奇妙・アメジスト 10 枠、ネザライト・クリムゾン・エンダー 12 枠、攻防で半分ずつ）、★とは無関係；基本→金→ダイヤモンド→ネザライトの強化でチップスロットが増える。
[ja_jp] fabric : ダイスは金床でスターコインを使って★を上げられ、★が高いほど加護ボーナスが強くなり、カード欄の枠数も増える（0★=4、1★=6、2★=8、3★=12、攻防で半分ずつ）；基本→金→ダイヤモンド→ネザライトのアップグレードチェーンでより多くのチップスロットが解放される（カード欄の枠数は★レベルで決まり、各品位で同じ★なら枠数も同じ；同じ段階のダイスはいずれもアップグレードの基礎にできる）。
```

#### `astral_dice.guide.entry.diamond_dice.1`

```text
[zh_cn] forge  : 钻石骰子。更高品阶，解锁更多筹码栏位；卡牌栏格数由骰子品质决定（攻防各 5，共 10 格），与星级无关。
[zh_cn] fabric : 钻石骰子。更高品阶，解锁更多筹码栏位；卡牌栏格数由星级决定（各品阶同星级相同）。
[en_us] forge  : Diamond die. A higher tier that unlocks more chip slots; card slots are set by the die tier (5 per side, 10 total) and do not change with stars.
[en_us] fabric : Diamond die. A higher tier that unlocks more chip slots; card slots follow the star level only (identical across tiers at the same star).
[ja_jp] forge  : ダイヤモンドのダイス。より高い品位で、より多くのチップスロットを解放する；カード欄の枠数はダイスの品位で決まる（攻防それぞれ 5、計 10 枠）ため、★とは無関係。
[ja_jp] fabric : ダイヤモンドのダイス。より高い品位で、より多くのチップスロットを解放する；カード欄の枠数は★レベルで決まる（各品位で同じ★なら同じ）。
```

#### `astral_dice.guide.entry.golden_dice.1`

```text
[zh_cn] forge  : 黄金骰子。比基础骰子品阶更高，解锁更多筹码栏位；卡牌栏格数由骰子品质决定（攻防各 4，共 8 格），与星级无关。
[zh_cn] fabric : 黄金骰子。比基础骰子品阶更高，解锁更多筹码栏位；卡牌栏格数由星级决定（各品阶同星级相同）。
[en_us] forge  : Golden die. A higher tier than the base die that unlocks more chip slots; card slots are set by the die tier (4 per side, 8 total) and do not change with stars.
[en_us] fabric : Golden die. A higher tier than the base die that unlocks more chip slots; card slots follow the star level only (identical across tiers at the same star).
[ja_jp] forge  : 金のダイス。基本のダイスより高い品位で、より多くのチップスロットを解放する；カード欄の枠数はダイスの品位で決まる（攻防それぞれ 4、計 8 枠）ため、★とは無関係。
[ja_jp] fabric : 金のダイス。基本のダイスより高い品位で、より多くのチップスロットを解放する；カード欄の枠数は★レベルで決まる（各品位で同じ★なら同じ）。
```

#### `astral_dice.guide.entry.netherite_dice.1`

```text
[zh_cn] forge  : 下界合金骰子。最高阶的骰子，解锁最多筹码栏位；卡牌栏格数由骰子品质决定（攻防各 6，共 12 格），与星级无关。
[zh_cn] fabric : 下界合金骰子。最高阶的骰子，解锁最多筹码栏位；卡牌栏格数由星级决定（各品阶同星级相同）。
[en_us] forge  : Netherite die. The highest tier, unlocking the most chip slots; card slots are set by the die tier (6 per side, 12 total) and do not change with stars.
[en_us] fabric : Netherite die. The highest tier, unlocking the most chip slots; card slots follow the star level only (identical across tiers at the same star).
[ja_jp] forge  : ネザライトのダイス。最高品位のダイスで、最も多くのチップスロットを解放する；カード欄の枠数はダイスの品位で決まる（攻防それぞれ 6、計 12 枠）ため、★とは無関係。
[ja_jp] fabric : ネザライトのダイス。最高品位のダイスで、最も多くのチップスロットを解放する；カード欄の枠数は★レベルで決まる（各品位で同じ★なら同じ）。
```

## 6. C 类：0 条（含一次改判与一条范围外待裁决）

### 6.1 结论

**B24 §D-N 的 14 条中，C 类 0 条。** 任务书列举的两个 C 类候选逐一核过：

- 「骰子卡牌栏格数由品质还是星级决定」：取证后**改判 A 类并已执行**（依据链见 §6.2；两套规则对照留在 §6.3，供否决时回滚）。任务书原文给的是「例如」，此处是取证后的事实修正，依据是交接文档逐字记录的用户原话 + 已落地提交，不是擅自扩大改动范围。
- 「NancyLu 固定 +6 vs 倍率 ×2/最低 +2」：**不属于 D-N 14 条**（14 条里只有 #7 `nancy_lu_sign.2` 与 #14 `tooltip.sign.nancy_lu_passive`，对应的是被动判定半径）。该分叉属另一议题（B24 的 D-M 机制分叉区），本批**未动**，作为「范围外待裁决」单列于 §6.4，**不计入 C 类计数**。

### 6.2 「骰子卡牌栏」为何判 A 而非 C：依据链

六条证据互相独立，合起来构成「新意图已裁决并已发布、fabric 只是没跟进」：

1. **用户原话**：`docs/handover-1.5.0-main.md:800`（§4.1am）记录用户 m23070 的裁决——卡牌栏按骰子品质决定格数、与升星无关，升星只影响费用上限。
2. **落地细节**：同文件 `:802`——`DiceTier` 扩 `cardSlots` 记录分量、`DiceCurioItem:57-60` 改读品阶、删除 `CARD_SLOTS_BY_STAR`、`configStarLevel` 仅保留给费用上限。
3. **13 条取值**：同文件 `:803`——dice 4 / golden 8 / glass 8 / diamond 10 / netherite 12 / emerald 10 / obsidian 10 / netherrack 8 / weird 10 / crimson 12 / amethyst 10 / ender 12 / nether_star 12，并特别记明 `chipBonus` 偏移**必须保持原样**（第一版补丁曾把 `1 + s` 压成 `s -> s` 被修回）——本批 13 条芯片偏移逐条照抄，未重演该错误。
4. **提交**：`git show -s 45966455` → `2026-10-28 01:02:27 +0800 MerlinKitsune「fix(dice): 卡牌栏格数改为按骰子品质固定,清除诡异骰子立牌冷却残留」`（39 files, +136/−167；交接文档 `:810` 同号）。
5. **三线一致**：`forge-1.20.1` 与 `neoforge-1.21.1` 均为 `DiceTier.java:32 int cardSlots) {` / `DiceCurioItem.java:59`；`neoforge-26.1.2` 为 `DiceTier.java:32` / `DiceCurioItem.java:54`。fabric 是全仓**唯一**仍为星级制的线。
6. **fabric 是「没跟进」而非「有意保留」**：`git log -S'CARD_SLOTS_BY_STAR' -- fabric-1.20.1/.../DiceCurioItem.java` 只命中 `c059ead8`（2026-09-29 移植脚手架），此后无任何同步提交 ⇒ 冻结在 2026-10-28 裁决之前。

旁证：交接文档 `:807` 记录三语（zh_cn / en_us / ja_jp）的 5 个卡牌栏相关键当时已在两线同步改过——说明该口径是**已发布的产品规则**，不是内部草案。

### 6.3 两套规则对照（若用户否决改判，按此回滚）

| 维度 | 甲：品阶制（forge / neoforge 三线；本批已落到 fabric） | 乙：星级制（fabric 改前） |
|---|---|---|
| 决定字段 | `DiceTier.cardSlots`（record 第 4 分量） | `DiceCurioItem.CARD_SLOTS_BY_STAR = {4, 6, 8, 12}` 按 `configStarLevel(stack)` 索引 |
| 数据结构 | `public record DiceTier(String id, Supplier<Item> item, IntUnaryOperator chipBonus, int cardSlots)` | `public record DiceTier(String id, Supplier<Item> item, IntUnaryOperator chipBonus)` |
| 取值 | 每款骰子单独登记：base 4 / T1 8 / T2 10 / T3 12 / T4 12 | 只看星级：0★=4、1★=6、2★=8、3★=12，各品阶同星级相同 |
| 消费点 | `DiceCurioItem.getCardSlots(stack)` → `tier.cardSlots()`，未登记回退 `GameplayConstants.CARD_SLOTS_TOTAL` | `DiceCurioItem.getCardSlots(stack)` → `CARD_SLOTS_BY_STAR[configStarLevel(stack)]` |
| 与星级的关系 | 无关 | 唯一决定因素 |
| 玩家可见结果（钻石骰子 1★） | 10 格（攻防各 5） | 6 格（攻防各 3） |
| 文案口径 | 「卡牌栏格数由骰子品质决定…与星级无关」 | 「卡牌栏格数由星级决定，各品阶同星级相同」 |

回滚面（**本批未执行任何 git 操作**，仅供裁决后使用）：4 个文件 —— `item/dice/DiceTier.java`、`item/dice/DiceCurioItem.java`、`item/ModItems.java`、`item/dice/NetherStarDiceItem.java`；改前文本见 `fabric-1.20.1/build/b28_mydiff.txt`（`git diff -U0` 全文）。

### 6.4 范围外待裁决（不计入 C 类计数）

**南希露立牌主动段：固定 +6 还是倍率 ×2（最低 +2）**

- forge `item/sign/NancyLuSignItem.java`：主动段为固定 `ACTIVE_ATTACK_BONUS = 6`，赐福结束时一次性 +6。
- fabric `item/sign/NancyLuSignItem.java`：主动段为 `ACTIVE_BONUS_MULTIPLIER = 2` + `ACTIVE_MIN_BONUS = 2`，即「当前攻击力 ×2，最低 +2」。
- 该差异**不在** B24 §D-N 的 14 条内（14 条只覆盖 `nancy_lu_sign.2` 被动半径与其 tooltip），故本批不动。
- 需要的裁决：+6 是固定值还是相对当前攻击力的倍率。两者在高攻击力配装下差异极大（当前攻击力 >3 时倍率更高，≤2 时固定更高）。

## 7. D 类：0 条

14 条全部具备「java 常量定义 + 全部使用点 + 三语文案」三方证据，无一条落入「取证不足」。所有 A 类判定都有 forge 侧代码注释或带日期的交接记录支撑，不存在仅凭文案反推数值的情况。

## 8. 验收原始输出

### 8.1 (a) `python scripts/__javac_check.py`

```text
EXIT 1 ERRORS 171 jars 686 srcs 525
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  164  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 984
```

与基线一致：`ERRORS 171 = 164 NOISE_AW + 7 NOISE_IFACE_INJECTION`；`=== REAL errors ===` 与 `=== REAL by file ===` 之间无任何条目（**为空**）。

补充取证（说明该结论覆盖到了本批改动）：

- `scripts/__javac_check.py:27` 输出目录 `fabric-1.20.1/build/javac-check`；`:31` 以 `os.walk("fabric-1.20.1/src/main/java")` 收集源码 ⇒ 编译面就是本线 java 源码。
- `args.txt`（525 个 `.java` 入口）中，本批 8 个文件 **8/8 在列**。
- `errors.txt`（本次运行写入）对本批 8 个文件的命中数 = **0**。
- 现存 171 条报错全部集中在既有 client 渲染层：`RenShieldRenderer.java:152`（`TransparencyStateShard has protected access in RenderStateShard`）、`BeamFxRenderer.java:102`（`CompositeState has protected access in RenderType`）、`ModClientEvents.java:64`（`MenuScreens` private access）等，由 Loom AW / 接口注入提供，javac 侧无法复现。

### 8.2 (b) `python tools/verify_fabric_assets.py`

```text
jar = fabric-1.20.1\build\libs\astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar

[!] build/libs 内 jar 版本 1.3.7-alpha.2+fabric_1.20.1 与 mod_version 1.5.0-rc.7+fabric_1.20.1 不符，改为扫描源码资源目录
[1] 物品闭环: 注册 157 (方块物品 3) / 模型 158 / 贴图 155 (方块贴图另计)
[2] 标签闭环: 引用 11 / 自建 29 / 有提供者 11
[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
[4] 粒子闭环: 注册 2 / particles/*.json 2
[5] 语言闭环: zh_cn 1094 / en_us 1094 / ja_jp 1094
[6] 手册闭环: 条目 184 / 引用配方 147
[7] 创意标签覆盖: 156/157
    已知与基线一致（不阻塞）: ['repulsion']
[8] 饰品槽位图标: 检查 7 条 icon

=== PASS: 8 项闭环全部通过 ===
```

首行 jar 陈旧提示为既有状态（`build/libs` 是 `1.3.7-alpha.2`，`mod_version` 已是 `1.5.0-rc.7`），脚本已自动降级为扫描源码资源目录，与本批无关。

### 8.3 (c) `python tools/check_lang_sync.py --line fabric-1.20.1`

```text
[OK]   fabric-1.20.1: en_us=1094
```

（终端以 GBK 输出，尾部中文提示乱码；判据 `[OK]` + `1094` 成立。本批未改任何 lang，键数不变属预期。）

### 8.4 (d) `git diff --numstat` 原始输出

```text
168	16	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java
3	2	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BookmarkChipItem.java
6	5	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/WarpEngineChipItem.java
8	5	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/DiceCurioItem.java
13	5	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/DiceTier.java
5	4	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/NetherStarDiceItem.java
4	3	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/NancyLuSignItem.java
6	10	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/EmeraldDiceTrade.java
```

**其中属于本批（我）的行**：

| 文件 | 我的 +/− | 同文件他人未提交改动 |
|---|---|---|
| `item/ModItems.java` | **+16 / −15** | +152 / −1（import 10 行、诡异骰子注释 1/1、额外电池 6 行、手电筒-爆闪 32 行、卡牌商店方块物品 93 行、isCombatCard/isEffectCard 10 行）——未触碰 |
| `item/chip/BookmarkChipItem.java` | **+3 / −2** | 无 |
| `item/chip/WarpEngineChipItem.java` | **+6 / −5** | 无 |
| `item/dice/DiceCurioItem.java` | **+8 / −5** | 无 |
| `item/dice/DiceTier.java` | **+13 / −5** | 无 |
| `item/dice/NetherStarDiceItem.java` | **+3 / −2** | +2 / −2（`1÷护甲系数` 措辞）——未触碰 |
| `item/sign/NancyLuSignItem.java` | **+3 / −2** | +1 / −1（`1÷护甲系数` 措辞）——未触碰 |
| `trade/EmeraldDiceTrade.java` | **+6 / −10** | 无 |

拆分方法：对 8 个文件逐个 `git diff -U0` 读原文 hunk，按「是否属于本批语义」归类；`-U0` 全文的增删行求和与 numstat 逐文件精确相符。工作树另有约 689 条他人脏文件，本批未触碰、未 `add`/`commit`/`stash`/`checkout`。

### 8.5 逐文件自检（CRLF / UTF-8 无 BOM / bareLF=0）

| 文件 | 字节 | CRLF 行 | bareLF | BOM | 溯源注释 |
|---|---|---|---|---|---|
| `item/chip/WarpEngineChipItem.java` | 4704 | 94 | **0** | 无 | 1 — L38 |
| `item/chip/BookmarkChipItem.java` | 1741 | 37 | **0** | 无 | 1 — L17 |
| `trade/EmeraldDiceTrade.java` | 5955 | 127 | **0** | 无 | 1 — L65 |
| `item/sign/NancyLuSignItem.java` | 19411 | 343 | **0** | 无 | 1 — L57 |
| `item/dice/DiceTier.java` | 2382 | 46 | **0** | 无 | 2 — L18、L19 |
| `item/dice/DiceCurioItem.java` | 18681 | 319 | **0** | 无 | 1 — L48 |
| `item/ModItems.java` | 75613 | 1189 | **0** | 无 | 2 — L243（我的）+ L1126（他人既有） |
| `item/dice/NetherStarDiceItem.java` | 4966 | 99 | **0** | 无 | 0 — 无（无常量改动） |

## 9. 改动清单（fabric-1.20.1，8 文件 / 15 处）

| # | 文件 | 行 | 改前 | 改后 |
|---|---|---|---|---|
| 1 | `item/chip/WarpEngineChipItem.java` | L38-39 | `PORTAL_WAYSTONE_COOLDOWN_TICKS = 20 * 60 * 5`（注释「5:00」） | `= 20 * 60 * 3`（注释「3:00;2026-10-15 用户裁决:5:00 → 3:00」+ 溯源 L38） |
| 2 | `item/chip/WarpEngineChipItem.java` | L29-30、L60 | javadoc 与使用方法注释「5:00 / 5 分钟」 | 「3:00 / 3 分钟」（与常量同步，非 lang） |
| 3 | `item/chip/BookmarkChipItem.java` | L16-18 | `DAMAGE_BONUS = 1`（注释「固定 +1」） | `= 2`（注释 +2 + 用户裁决 + 溯源 L17） |
| 4 | `trade/EmeraldDiceTrade.java` | L64-68 | `discount(stack.getCount())` + `discount()` 20% 折扣 | `Math.max(1, stack.getCount())`（1:1），`discount()` 整段删除，溯源 L65 |
| 5 | `trade/EmeraldDiceTrade.java` | L16、L19、L72 | 「20% 折扣」代码注释与 javadoc | 「1:1 等值代付」 |
| 6 | `item/sign/NancyLuSignItem.java` | L57-58 | `PASSIVE_RANGE = 6.0` | `= 3.0` + 溯源 L57 |
| 7 | `item/sign/NancyLuSignItem.java` | L43 | javadoc「周围 6 格内」 | 「周围 3 格内」 |
| 8 | `item/dice/DiceTier.java` | L34-35 | 3 分量 record（无 cardSlots） | 4 分量 record（+ `int cardSlots`）+ `@param` + javadoc 重写 |
| 9 | `item/dice/DiceCurioItem.java` | L44-48 | `CARD_SLOTS_BY_STAR = {4, 6, 8, 12}` + 星级注释 | 删除数组，改品阶制注释 + 溯源 L48 |
| 10 | `item/dice/DiceCurioItem.java` | L58-61 | `CARD_SLOTS_BY_STAR[configStarLevel(stack)]` | `tier != null ? tier.cardSlots() : GameplayConstants.CARD_SLOTS_TOTAL` |
| 11 | `item/dice/DiceCurioItem.java` | L17 | 无 import | + `import com.merlinkitsune.starengine.component.GameplayConstants;` |
| 12 | `item/ModItems.java` | L242-243 | 注释「卡牌栏格数不再按品阶区分,仅由星级决定」 | 「按品阶登记(第 4 参 cardSlots,与星级无关)」+ 溯源 L243 |
| 13 | `item/ModItems.java` | L245-282 | 13 条 `new DiceTier(id, item, chipBonus)` | 13 条补第 4 参 cardSlots（4/8/8/10/12/10/10/8/10/12/10/12/12），chipBonus 偏移原样保留 |
| 14 | `item/ModItems.java` | L189 | 注释「…并享 20% 折扣」 | 「…按 1:1 改为星币」（与 forge `ModItems.java:193` 同句） |
| 15 | `item/dice/NetherStarDiceItem.java` | L18-20 | 「卡牌槽与费用点数始终按最高档(3★…12 格…)」 | 「卡牌栏 12 格(由 T4 品阶固定,与星级无关)」+ 费用点数 3★ 段 |

## 10. 遗留与转交

1. **lang 10 键 × 3 语言**（本批禁改）：§5.1 的 2 个 B 类键 + §5.2 的 8 个 A 类残留键；取值一律以 §5.4 的 forge 全文为准。注意 fabric 文案里的 `20%%` 是字面百分号转义，照抄时勿改成单个 `%`。
2. **fabric 未移植 forge 的兑换条目过滤**：forge `EmeraldDiceTrade` 侧有 `isEmeraldToStarCoin` / `rawCostA` / `MerchantOfferRawAccess` / `filteredFor`（绿宝石价替换后仍保持报价过滤语义），fabric 侧 `grep` 命中 **0**。本批只对齐数值口径（1:1），该过滤属功能缺口，建议单开批次。
3. **文档漂移**：`docs/agent/AGENTS-full.md:869`、`:1864` 与 `docs/features/dice-special-verification.md:41/83/304/371` 仍写「卡牌栏由星级决定」，与已落地口径不符，需文档批次修订。
4. **fabric `build/libs` jar 陈旧**（`1.3.7-alpha.2` vs `mod_version 1.5.0-rc.7`）属既有状态；本批禁跑 gradle，未重建，`verify_fabric_assets.py` 已自动降级扫源码。
5. **`DiceTier` 构造签名变更（3 参 → 4 参）是源码级 API 变更**：本线 13 处构造点已全部更新；若后续有测试或外部代码构造 `DiceTier`，需同步补第 4 参。

## 11. 未触碰项与纪律声明

- **未改**：任何 lang 文件（zh_cn / en_us / ja_jp）、recipes、patchouli、`tools/**`、`platform/**`、`mixin/**`、build 文件、`fabric.mod.json`、`astral_dice.mixins.json`。
- **未执行**：commit / push / stash / add / checkout；未跑 Gradle；未启动游戏。
- **未触碰他人改动**：`item/ModItems.java` 的 152 行、`NetherStarDiceItem.java` 的 2 行、`NancyLuSignItem.java` 的 1 行「护甲系数」措辞改动均原样保留。
- 每处常量改动均带「对齐 forge `<文件>:<行>`」溯源注释，路径用正斜杠，风格与 forge 既有溯源注释一致。
- 行尾：全部改动文件 CRLF、UTF-8 无 BOM、逐文件 `bareLF = 0`（见 §8.5）。

---

过程物留存于 `fabric-1.20.1/build/`：`b28_mydiff.txt`（`git diff -U0` 全文）、`b28_gate_a.txt` / `b28_gate_b.txt` / `b28_gate_c.txt`（验收原始输出）、`b28_numstat.txt`。
