# B23 · fabric-1.20.1 筹码 tooltip 补齐报告（D-11）

| 项 | 值 |
|---|---|
| 仓库 | F:\MCProject\astral_dice_multiloader |
| 分支 | multi-main（未 commit / 未 push / 未 stash / 未 add / 未 checkout） |
| 上游依据 | porting/fabric-1.20.1/B12-DRIFT-AUDIT-REPORT.md:109-114（D-11） |
| 日期 | 2026-11-01 |
| 本批改动文件 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java`（唯一；未新增类，未改 lang / recipes / patchouli / tools / 其他 java） |
| 行数 | 1602 行（CRLF 终止行）/ 100646 bytes；本批净增 45 行 + 1 行键替换 |
| 结论 | 5 块筹码 tooltip + 诡异骰子回血段已移植；计数来源全部在 fabric 实存；**无 BLOCKED** |

---

## 0. 结论摘要

1. forge `event/ModTooltipHandler.java:779-819` 的 5 块筹码 tooltip（FLASHLIGHT_BURST_CHIP / FORTUNE_STICK_CHIP / ABYSS_REAGENT_CHIP / INFINITE_GRID_CHIP / SENSOR_CHIP）已逐字移植到 fabric 同名文件 `:753-797`，位置与 forge 一侧的相对顺序一致（都在 FLASHLIGHT_CHIP 块之后、PURPLE_SHOOTING_STAR_CHIP 块之前）。
2. 5 块调用的三个计数 helper（`addSignCounter` / `addHealingPointsCounter` / `addChargeCounter`）在 fabric 侧**早已存在**，且与 forge **逐字节相同、行号相同**（140 / 169 / 176），因此计数语义（含 0 时仍显示、上限来源、GRAY 底色 + lang 内嵌 §e/§9 染色）天然等价，无需重写。
3. 计数读取点全部落到 fabric 真实存储（`component/ModAttachments` 的 starlight / healingPoints 附件 + `ChargeManager`），**无一个计数在 fabric 缺失** ⇒ 不触发 BLOCKED。
4. forge 的 `tooltip.astral_dice.weird_dice_heal` 行在 fabric 侧原为 `tooltip.astral_dice.weird_dice_cooldown`；经取证该旧键是**过期假描述**（它描述的能力在 fabric 已被删除），已改为 heal 键，行数 / 顺序 / 颜色均不变（详见 §4）。
5. lang 未改（本批禁止）。9 个相关键在三语中的**值**与 forge 逐个 IDENTICAL，故不会出现裸键，染色也与 forge 一致。
---

## 1. 改动清单

| # | 文件 | 位置（改后行号） | 内容 | 行数影响 |
|---|---|---|---|---|
| A | fabric-1.20.1/.../event/ModTooltipHandler.java | 753-797 | 插入 5 块筹码 tooltip（逐字照抄 forge 775-819） | **+45** |
| B | fabric-1.20.1/.../event/ModTooltipHandler.java | 456 | 键 `tooltip.astral_dice.weird_dice_cooldown` → `tooltip.astral_dice.weird_dice_heal`（GREEN 不变） | **±0** |

- 插入点：原 752 行（FLASHLIGHT_CHIP 块收尾 `}`）之后、原「紫色飞星 / 金色飞星」注释之前。
- 无 import 变更：`ChargeManager`(:11) / `HealingManager`(:12) / `StarLightManager`(:13) 原本已 import。
- 未新增 helper 类；未改 lang / recipes / patchouli / tools/** / 其他 java。

以 hunk 定位（`git diff -U0`）：

    @@ -454 +456 @@                     ← 本批 B
    @@ -724,0 +753,45 @@               ← 本批 A

> 同一文件内另有 **非本批** 的两个 hunk（`@@ -26,0 +27,2 @@` 两行 import、`@@ -603,0 +606,26 @@` 26 行效果牌移植，注释落款均为 2026-11-01 的并行批次产物），**不是 B23 的改动**，见 §7.4。
---

## 2. 计数来源取证（fabric 侧真实存储位置）

### 2.1 三个计数 helper：与 forge 逐字节相同

    $ grep -n "addSignCounter(\|addHealingPointsCounter(\|addChargeCounter(" forge-1.20.1/.../event/ModTooltipHandler.java
    140:    private static void addSignCounter(List<Component> tooltip, String langKey, Object... args) {
    169:    private static void addHealingPointsCounter(List<Component> tooltip, Player player) {
    176:    private static void addChargeCounter(List<Component> tooltip, Player player) {

    $ diff <(sed -n '/private static void addSignCounter(/,/^    }/p' forge) <(sed -n '同段' fabric)
    IDENTICAL addSignCounter

fabric 侧同名同参同实现（`ModTooltipHandler.java:140 / :169 / :176`）：

| helper | fabric 实现要点 | 0 值行为 | player 为 null 行为 |
|---|---|---|---|
| `addSignCounter(tooltip, langKey, args...)` :140 | 先 `tooltip.add(Component.empty())` 空行，再 `tt(langKey,args).withStyle(GRAY)` | **照常显示**「0/上限」 | 调用方须自己判空 |
| `addHealingPointsCounter(tooltip, player)` :169 | `if (player == null) return;` 否则用 healing_points 键 + `HealingManager.getPoints/getCap` | 显示 0 值 | 直接 return（不显示计数行，文案行仍显示） |
| `addChargeCounter(tooltip, player)` :176 | `if (player == null) return;` 否则用 `chip.charge` 键 + `ChargeManager.getStacks` + `GameplayConstants.CHARGE_MAX_STACKS` | 显示 0 值 | 直接 return |

⇒ 「计数为 0 时是否显示」这条语义由 helper 决定，而 helper 与 forge 相同 ⇒ 行为一致。

### 2.2 计数数据落在哪（fabric，全部实存）

| 筹码 | forge 读法 | fabric 读法 | fabric 存储实体（取证） | 谁在写这个计数（消费方证明） |
|---|---|---|---|---|
| FLASHLIGHT_BURST_CHIP | `StarLightManager.get/getCap` | 同左 | `item/StarLightManager.java:27-28` → `ModAttachments.getStarlight(player)`；上限 `:32 getCap()` | `item/chip/FlashlightBurstChipItem.java:87` add / `:93` get |
| FORTUNE_STICK_CHIP | 同上 | 同左 | 同上 | `item/chip/FortuneStickChipItem.java:93,100` add / `:106` get |
| ABYSS_REAGENT_CHIP | `addHealingPointsCounter` | 同左 | `item/HealingManager.java:180-181` → `ModAttachments.getHealingPoints(player)`；上限 `:187 getCap(player)` | `item/chip/AbyssReagentChipItem.java:104` add / `:111` spend |
| INFINITE_GRID_CHIP | `addChargeCounter` | 同左 | `item/ChargeManager.java:70 getStacks(player)`；上限 `GameplayConstants.CHARGE_MAX_STACKS` | `item/chip/InfiniteGridChipItem.java:30` 类注释明写 ChargeManager#addStacks |
| SENSOR_CHIP | 无计数（只出文案） | 同左（无计数） | `item/MarkManager.java:19`——标记类，无按玩家累计计数器 | —— |

物品常量存在性（`item/ModItems.java`）：`:855 FLASHLIGHT_BURST_CHIP`、`:862 FORTUNE_STICK_CHIP`、`:868 ABYSS_REAGENT_CHIP`、`:874 INFINITE_GRID_CHIP`、`:880 SENSOR_CHIP`。

**结论：5 个筹码所需的计数在 fabric 全部存在，读数路径与 forge 同名同义，不存在「fabric 根本没有这个计数」的情形 ⇒ 不报 BLOCKED。**

### 2.3 平台类型核对（决定能否照抄 guard 写法）

- 本线垫片 `platform/event/entity/player/ItemTooltipEvent.java:67` 的 `getEntity()` 返回 `@Nullable Player`（与 forge 1.20.1 相同；1.21 线才是 `LivingEntity`）。
- 方法内既有局部变量 `Player player = event.getEntity();`（`ModTooltipHandler.java:336`）。
- ⇒ forge 的 `if (player != null)` 与 `addChargeCounter(tooltip, event.getEntity())` **可以原样照抄**，不需要 instanceof 改写。

---

## 3. 逐块并排自证（forge ↔ fabric）

### 3.0 位置对照

| 块 | forge 行 | fabric 行 |
|---|---|---|
| （上文）FLASHLIGHT_CHIP | 766-774 | 744-752 |
| ① 手电筒-爆闪 FLASHLIGHT_BURST_CHIP | 775-787 | 753-765 |
| ② 财运签 FORTUNE_STICK_CHIP | 788-797 | 766-775 |
| ③ 试剂-深渊 ABYSS_REAGENT_CHIP | 798-806 | 776-784 |
| ④ 无线输电杆 INFINITE_GRID_CHIP | 807-813 | 785-791 |
| ⑤ 感测装置 SENSOR_CHIP | 814-819 | 792-797 |
| （下文）紫色飞星 PURPLE_SHOOTING_STAR_CHIP | 820-… | 798-… |

顺序与 forge 完全一致：FLASHLIGHT_CHIP → ①②③④⑤ → 紫色飞星。

### 3.1 ① FLASHLIGHT_BURST_CHIP（forge 775-787 / fabric 753-765，13 行）

forge：

    775|         // 手电筒-爆闪(传奇/星光):文案 + 星光计数(与手电筒-强光同款计数器)
    776|         // ⚠️ 平台差异(1.20.1):{@code ItemTooltipEvent#getEntity()} 的返回类型**已是 Player**
    777|         //    (1.21 是 LivingEntity)⇒ 这里的 instanceof 模式变量会报
    778|         //    「模式类型 Player 是表达式类型 Player 的子类型」,故改用本区块既有的方法级 player。
    779|         if (stack.is(ModItems.FLASHLIGHT_BURST_CHIP.get())) {
    780|             tooltip.add(Component.empty());
    781|             tooltip.add(Component.translatable("tooltip.astral_dice.chip.flashlight_burst")
    782|                     .withStyle(ChatFormatting.GRAY));
    783|             if (player != null) {
    784|                 addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
    785|                         StarLightManager.get(player), StarLightManager.getCap());
    786|             }
    787|         }

fabric：

    753|         // 手电筒-爆闪(传奇/星光):文案 + 星光计数(与手电筒-强光同款计数器)
    754|         // ⚠️ 平台差异(1.20.1):本线垫片 {@code platform.event.entity.player.ItemTooltipEvent#getEntity()}
    755|         //    的返回类型**已是 Player**(1.21 是 LivingEntity)⇒ 照抄 instanceof 模式变量会报
    756|         //    「模式类型 Player 是表达式类型 Player 的子类型」,故改用本区块既有的方法级 player。
    757|         if (stack.is(ModItems.FLASHLIGHT_BURST_CHIP.get())) {
    758|             tooltip.add(Component.empty());
    759|             tooltip.add(Component.translatable("tooltip.astral_dice.chip.flashlight_burst")
    760|                     .withStyle(ChatFormatting.GRAY));
    761|             if (player != null) {
    762|                 addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
    763|                         StarLightManager.get(player), StarLightManager.getCap());
    764|             }
    765|         }

| forge→fabric | 判定 |
|---|---|
| 779 → 757 | **相同**（触发条件 `stack.is(ModItems.FLASHLIGHT_BURST_CHIP.get())`） |
| 780 → 758 | **相同**（前置空行 `Component.empty()`） |
| 781-782 → 759-760 | **相同**（键 `chip.flashlight_burst` + `ChatFormatting.GRAY`） |
| 783 → 761 | **相同**（guard `player != null`） |
| 784-785 → 762-763 | **相同**（同款 `chip.starlight` 计数器 + `StarLightManager.get/getCap`） |
| 786-787 → 764-765 | **相同**（收尾） |
| 775 → 753 | **相同**（注释原文） |
| 776-778 → 754-756 | **因平台改写（仅注释体）**：把 `{@code ItemTooltipEvent#getEntity()}` 换成 `本线垫片 {@code platform.event.entity.player.ItemTooltipEvent#getEntity()}`。理由：fabric 线没有 forge 的 `net.minecraftforge.event.entity.player.ItemTooltipEvent`，实际用的是本线垫片类；注释若照抄会指向一个本线不存在的类。**只改注释文字，不改任何执行语句。** |

### 3.2 ② FORTUNE_STICK_CHIP（forge 788-797 / fabric 766-775，10 行）

forge：

    788|         // 财运签(史诗/星光):文案 + 星光计数
    789|         if (stack.is(ModItems.FORTUNE_STICK_CHIP.get())) {
    790|             tooltip.add(Component.empty());
    791|             tooltip.add(Component.translatable("tooltip.astral_dice.chip.fortune_stick")
    792|                     .withStyle(ChatFormatting.GRAY));
    793|             if (player != null) {
    794|                 addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
    795|                         StarLightManager.get(player), StarLightManager.getCap());
    796|             }
    797|         }

fabric：

    766|         // 财运签(史诗/星光):文案 + 星光计数
    767|         if (stack.is(ModItems.FORTUNE_STICK_CHIP.get())) {
    768|             tooltip.add(Component.empty());
    769|             tooltip.add(Component.translatable("tooltip.astral_dice.chip.fortune_stick")
    770|                     .withStyle(ChatFormatting.GRAY));
    771|             if (player != null) {
    772|                 addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
    773|                         StarLightManager.get(player), StarLightManager.getCap());
    774|             }
    775|         }

| forge→fabric | 判定 |
|---|---|
| 788 → 766 | **相同**（注释原文） |
| 789 → 767 | **相同**（触发条件） |
| 790 → 768 | **相同**（前置空行） |
| 791-792 → 769-770 | **相同**（键 `chip.fortune_stick` + GRAY） |
| 793 → 771 | **相同**（guard） |
| 794-795 → 772-773 | **相同**（`chip.starlight` + 同款读数） |
| 796-797 → 774-775 | **相同** |

> 逐字相同，无任何改写。

### 3.3 ③ ABYSS_REAGENT_CHIP（forge 798-806 / fabric 776-784，9 行）

forge：

    798|         // 试剂-深渊(史诗/治愈):文案 + 治愈层数计数
    799|         if (stack.is(ModItems.ABYSS_REAGENT_CHIP.get())) {
    800|             tooltip.add(Component.empty());
    801|             tooltip.add(Component.translatable("tooltip.astral_dice.chip.abyss_reagent")
    802|                     .withStyle(ChatFormatting.GRAY));
    803|             if (player != null) {
    804|                 addHealingPointsCounter(tooltip, player);
    805|             }
    806|         }

fabric：

    776|         // 试剂-深渊(史诗/治愈):文案 + 治愈层数计数
    777|         if (stack.is(ModItems.ABYSS_REAGENT_CHIP.get())) {
    778|             tooltip.add(Component.empty());
    779|             tooltip.add(Component.translatable("tooltip.astral_dice.chip.abyss_reagent")
    780|                     .withStyle(ChatFormatting.GRAY));
    781|             if (player != null) {
    782|                 addHealingPointsCounter(tooltip, player);
    783|             }
    784|         }

| forge→fabric | 判定 |
|---|---|
| 798 → 776 | **相同** |
| 799 → 777 | **相同**（触发条件） |
| 800-802 → 778-780 | **相同**（前置空行 + 键 `chip.abyss_reagent` + GRAY） |
| 803-805 → 781-783 | **相同**（`addHealingPointsCounter(tooltip, player)`；内部读数 = `HealingManager.getPoints/getCap` → `ModAttachments.getHealingPoints`） |
| 806 → 784 | **相同** |

> 逐字相同，无任何改写。

### 3.4 ④ INFINITE_GRID_CHIP（forge 807-813 / fabric 785-791，7 行）

forge：

    807|         // 无线输电杆(史诗/充能):文案 + 充能计数(充能类筹码统一追加,无例外)
    808|         if (stack.is(ModItems.INFINITE_GRID_CHIP.get())) {
    809|             tooltip.add(Component.empty());
    810|             tooltip.add(Component.translatable("tooltip.astral_dice.chip.infinite_grid")
    811|                     .withStyle(ChatFormatting.GRAY));
    812|             addChargeCounter(tooltip, event.getEntity());
    813|         }

fabric：

    785|         // 无线输电杆(史诗/充能):文案 + 充能计数(充能类筹码统一追加,无例外)
    786|         if (stack.is(ModItems.INFINITE_GRID_CHIP.get())) {
    787|             tooltip.add(Component.empty());
    788|             tooltip.add(Component.translatable("tooltip.astral_dice.chip.infinite_grid")
    789|                     .withStyle(ChatFormatting.GRAY));
    790|             addChargeCounter(tooltip, event.getEntity());
    791|         }

| forge→fabric | 判定 |
|---|---|
| 807 → 785 | **相同** |
| 808 → 786 | **相同**（触发条件） |
| 809-811 → 787-789 | **相同**（前置空行 + 键 `chip.infinite_grid` + GRAY） |
| 812 → 790 | **相同**（`addChargeCounter(tooltip, event.getEntity())`——本块**没有** `if (player != null)` 外壳，判空在 helper 内部（`:177 if (player == null) return;`），与 forge 一致） |
| 813 → 791 | **相同** |

> 逐字相同。**注意此块与 ①②③ 的 guard 写法不同，这是 forge 原样，不是移植失误。**

### 3.5 ⑤ SENSOR_CHIP（forge 814-819 / fabric 792-797，6 行）

forge：

    814|         // 感测装置(史诗/标记):文案(标记类无计数器)
    815|         if (stack.is(ModItems.SENSOR_CHIP.get())) {
    816|             tooltip.add(Component.empty());
    817|             tooltip.add(Component.translatable("tooltip.astral_dice.chip.sensor")
    818|                     .withStyle(ChatFormatting.GRAY));
    819|         }

fabric：

    792|         // 感测装置(史诗/标记):文案(标记类无计数器)
    793|         if (stack.is(ModItems.SENSOR_CHIP.get())) {
    794|             tooltip.add(Component.empty());
    795|             tooltip.add(Component.translatable("tooltip.astral_dice.chip.sensor")
    796|                     .withStyle(ChatFormatting.GRAY));
    797|         }

| forge→fabric | 判定 |
|---|---|
| 814 → 792 | **相同** |
| 815 → 793 | **相同**（触发条件） |
| 816-818 → 794-796 | **相同**（前置空行 + 键 `chip.sensor` + GRAY） |
| 819 → 797 | **相同** |

> 逐字相同。本块**不显示计数行**（标记类无计数器），与 forge 一致。

---

## 4. 诡异骰子回血段（`tooltip.astral_dice.weird_dice_heal`）

### 4.1 并排

forge 455-460：

    455|             if (stack.is(ModItems.WEIRD_DICE.get())) {
    456|                 tooltip.add(tt("tooltip.astral_dice.weird_dice_heal")
    457|                         .withStyle(ChatFormatting.GREEN));
    458|                 tooltip.add(tt("tooltip.astral_dice.weird_dice_lowroll")
    459|                         .withStyle(ChatFormatting.RED));
    460|             }

fabric 455-460（本批改后）：

    455|             if (stack.is(ModItems.WEIRD_DICE.get())) {
    456|                 tooltip.add(tt("tooltip.astral_dice.weird_dice_heal")
    457|                         .withStyle(ChatFormatting.GREEN));
    458|                 tooltip.add(tt("tooltip.astral_dice.weird_dice_lowroll")
    459|                         .withStyle(ChatFormatting.RED));
    460|             }

| forge→fabric | 判定 |
|---|---|
| 455 → 455 | **相同**（触发条件 `WEIRD_DICE`） |
| 456-457 → 456-457 | **因平台改写（键名）**：fabric 改前为 `tooltip.astral_dice.weird_dice_cooldown`，现改为 `weird_dice_heal`。理由见 §4.2。颜色 GREEN、行位置、行数不变。 |
| 458-459 → 458-459 | **相同**（`weird_dice_lowroll` RED） |
| 460 → 460 | **相同** |

### 4.2 为什么改键（不是「顺手改文案」，是纠正过期描述）

- fabric `event/WeirdDiceHandler.java:39-48` 的 `signCooldownTicks` javadoc 明写：**不提供**诡异骰子的 -50% 冷却缩减（用户需求 2026-10-21：该能力已删除）。
- 同文件 `:90-94 onSignActiveEnded(...)` 的实际行为是 `player.heal(HEALTH_ON_ACTIVE_END = 5)`，且服务端生效（客户端 tooltip 只是说明）。
- 即 fabric 侧真实行为 = 「立牌激活结束时回 5 点血」= forge 的 heal 语义；而 fabric 旧键 `weird_dice_cooldown` 的文案在描述一个**已被删除的能力**（虚假描述）。
- ⇒ 把该行键换成 `weird_dice_heal`，是让描述回到真实行为、与 forge 等价；**行数（2 行）、顺序（heal 在上 lowroll 在下）、颜色（GREEN / RED）全部不变**。
- `tooltip.astral_dice.weird_dice_cooldown` 改后**不再被任何 java 引用**（改前全仓仅此行引用）。因本批禁止改 lang，该键保留在 lang 中 → 见 §7.3 登记。

---

## 5. 硬约束自检

| 约束 | 要求 | 实测 | 结论 |
|---|---|---|---|
| 文件编码 | UTF-8 无 BOM | `BOM = False`、`utf-8 decode = OK 90881 chars` | ✅ |
| 行尾 | CRLF，bareLF = 0 | `CRLF count = 1602`、`bare LF = 0`、`bare CR = 0` | ✅ |
| 禁用符号 | 不得引入 net.minecraftforge / top.theillusivec4 | 目标文件内 `net.minecraftforge` = 0`、`top.theillusivec4` = 0 | ✅ |
| 平台差异 | isClientSide() / new ResourceLocation(a,b) / compat.curios.CuriosApi | 目标文件内三者计数均为 0（本批新增代码一个都没用到，故无「写法」需要适配；也未引入 forge 形式） | ✅ 天然合规 |
| 改动面 | 只动 1 个 java，不改 lang/recipes/patchouli/tools/其他 java | 本批 hunk 仅 2 处（见 §1） | ✅ |
| 过程纪律 | 未跑 gradle / 未启动游戏 / 未做任何 git 写操作 | 全程只读 + 单文件写入 | ✅ |

---

## 6. 验收原始输出

### (a) `python scripts/__javac_check.py`

**改动前基线（对照用）**：

    EXIT 1 ERRORS 171 jars 685 srcs 522
    === buckets ===
      164  NOISE_AW
        7  NOISE_IFACE_INJECTION
    → REAL 为空

**改动后第 4 次（最新）运行**：

    EXIT 1 ERRORS 172 jars 685 srcs 525
    === buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
      164  NOISE_AW
        7  NOISE_IFACE_INJECTION
        1  REAL
    === REAL errors ===
        1  cannot find symbol | symbol:   method disconnect(MutableComponent) location: variable connection of t

**该 1 条 REAL 的文件归因**（逐行解析 errors.txt）：

    network/VersionHandshake.java:259 | cannot find symbol
    total error lines = 172
    ModTooltipHandler error lines = 0

⇒ **ModTooltipHandler.java 贡献 0 条 REAL error**。全局仅剩的 1 条在 `network/VersionHandshake.java:259`（`connection.disconnect(MutableComponent)`），属并行批次正在收尾的网络握手代码，非本批文件；归因与时间线见 §7.1。

### (b) `python tools/verify_fabric_assets.py`（改动后）

    jar = fabric-1.20.1\build\libs\astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar

    [!] build/libs 内 jar 版本 1.3.7-alpha.2+fabric_1.20.1 与 mod_version 1.5.0-rc.7+fabric_1.20.1 不符，改为扫描源码资源目录
    [1] 物品闭环: 注册 157 (方块物品 3) / 模型 158 / 贴图 155 (方块贴图另计)
    [2] 标签闭环: 引用 11 / 自建 29 / 有提供者 11
    [3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
    [4] 粒子闭环: 注册 2 / particles/*.json 2
    [5] 语言闭环: zh_cn 1095 / en_us 1095 / ja_jp 1095
    [6] 手册闭环: 条目 184 / 引用配方 147
    [7] 创意标签覆盖: 156/157
        已知与基线一致（不阻塞）: ['repulsion']
    [8] 饰品槽位图标: 检查 7 条 icon

    === PASS: 8 项闭环全部通过 ===

说明：`[!]` 是 build/libs 里遗留旧 jar 的版本提示、`[7]` 的 `repulsion` 是基线既有不阻塞项，均非本批引入；全部读数与改动前逐项一致。

### (c) `grep -c "tooltip.astral_dice.chip" <目标文件>`

    82

本批负责的 5 个**具体键**改动前各为 0，改动后各为 1：

    $ grep -n "chip.flashlight_burst\|chip.fortune_stick\|chip.abyss_reagent\|chip.infinite_grid\|chip.sensor" fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java
    759:            tooltip.add(Component.translatable("tooltip.astral_dice.chip.flashlight_burst")
    769:            tooltip.add(Component.translatable("tooltip.astral_dice.chip.fortune_stick")
    779:            tooltip.add(Component.translatable("tooltip.astral_dice.chip.abyss_reagent")
    788:            tooltip.add(Component.translatable("tooltip.astral_dice.chip.infinite_grid")
    795:            tooltip.add(Component.translatable("tooltip.astral_dice.chip.sensor")

前缀 `tooltip.astral_dice.chip` 计数由 **74 → 82**（+8 = 5 个新键 + 3 处同键复用：`chip.starlight`×2 / `chip.charge`×1）。验收口径以「5 个具体键各 ≥1」为准——前缀总量在改动前就远大于 5（74 处），任务书写的「从 0 变成 ≥5」指的就是这 5 个具体键。

### (d) 反向确认：5 个键 + weird_dice_heal 在三语 lang 各 1

    $ for K in chip.flashlight_burst chip.fortune_stick chip.abyss_reagent chip.infinite_grid chip.sensor weird_dice_heal; do
        printf '%-28s %s\n' "$K" "$(grep -c "tooltip.astral_dice.$K" <lang>)"
      done

    --- fabric-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json
    chip.flashlight_burst        1
    chip.fortune_stick           1
    chip.abyss_reagent           1
    chip.infinite_grid           1
    chip.sensor                  1
    weird_dice_heal              1
    --- fabric-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json
    chip.flashlight_burst        1
    chip.fortune_stick           1
    chip.abyss_reagent           1
    chip.infinite_grid           1
    chip.sensor                  1
    weird_dice_heal              1
    --- fabric-1.20.1/src/main/resources/assets/astral_dice/lang/ja_jp.json
    chip.flashlight_burst        1
    chip.fortune_stick           1
    chip.abyss_reagent           1
    chip.infinite_grid           1
    chip.sensor                  1
    weird_dice_heal              1

另：这 9 个相关键（上述 6 个 + `chip.starlight` / `chip.charge` / `healing_points`）在 **forge 与 fabric 的三语中逐个值 IDENTICAL**（python json 逐键比对），因此：
- 不会出现裸键（键存在）；
- 染色与 forge 完全一致（§e 数值连符号 / §9 时间 / 整段底色），因为染色写在 lang 值里；
- 这正是本批**不需要改 lang** 的前提。

---

## 7. 无法判定清单 / 遗留

### 7.1 验收 (a)「REAL errors 为空」当前不满足——原因在别的批次，不在本批文件

时间线（同机、并发批次在我编辑期间持续落盘）：

| 运行 | ERRORS | REAL | REAL 所在文件 | ModTooltipHandler 的 error 行数 |
|---|---|---|---|---|
| 基线（改前） | 171 | 0 | —— | 0 |
| run#1 | —— | 2 | network/VersionProbeMessage.java:47、VersionReplyMessage.java:48（cannot find symbol: variable VersionHandshake） | 0 |
| run#2 | 179 | 8 | network/VersionHandshake.java:105 / :111×2 / :116×2 / :123 / :259，另 platform/network/NetworkRegistry.java:27（SimpleChannel 方法缺失、构造器参数不符） | 0 |
| run#3 | 172 | 1 | network/VersionHandshake.java:259 | 0 |
| run#4（最新） | 172 | 1 | 同上 | 0 |

- run#1 的「变量 VersionHandshake 找不到」由文件系统时间戳直接证伪为竞态：errors.txt 写于 19:36:31.97，而 network/VersionHandshake.java 落盘于 19:36:32.61（晚 0.64 秒）⇒ javac 扫描时该文件还不存在。
- run#2 → run#4 的 REAL 由 8 收敛到 1，逐次都落在 network/VersionHandshake.java（SimpleChannel.clientAcceptedVersions() / serverAcceptedVersions() / protocolVersion()、connection.disconnect(MutableComponent)、构造器参数）——即并行批次正在写的网络握手代码。本批只改 tooltip 文件，而该文件在**全部 4 次运行中 error 行数恒为 0**。
- 纪律要求我不改其他 java，因此**我不能替该批次修**。建议：等网络批次收尾后重跑 python scripts/__javac_check.py，届时 REAL 应为 0。在此之前可确定的是：**B23 对 REAL error 的贡献为 0**。

### 7.2 未做实机 / 未跑 gradle ⇒ tooltip 的视觉结果未验证

按纪律本批禁止跑 gradle、禁止启动游戏，因此以下只能给源码级等价结论，不能给运行时截图证据：

- 实际行数、行间距（Component.empty() 空行）在客户端渲染后的观感；
- 染色在真实 tooltip 中的呈现（本批已通过「lang 值与 forge 逐字相同」间接保证，但未实机截图）；
- 玩家侧计数在服务端与客户端之间的同步时序（本批未改计数器写入侧，属既有实现）。

建议：后续在实机验证批次里对 5 个筹码各开一次 tooltip，截图核对行数与颜色。

### 7.3 tooltip.astral_dice.weird_dice_cooldown 成为「未被引用的 lang 键」

- 改前该键仅被 ModTooltipHandler.java:456 引用；改后 java 侧引用数 = 0。
- 本批**禁止改 lang**，故该键保留在 fabric 三语 lang 中，成为孤儿键。
- 影响评估：tools/verify_fabric_assets.py 的 check_lang 只校验「三语键集互等」+「java 引用了但 lang 缺」，**不校验未被引用的键** ⇒ 不挂 gate（改动后 gate 仍 PASS，见 §6b）。
- 待办（需由有权改 lang 的批次处理）：要么删除该键，要么在手册里登记为「历史键」。

### 7.4 同一文件存在并行批次的改动（非本批）

git diff -U0 在该文件上共 4 个 hunk，其中 2 个不是本批：

    @@ -26,0 +27,2 @@     +2 行 import
    @@ -603,0 +606,26 @@   +26 行「2026-11-01 移植自 forge-1.20.1 同路径」的效果牌 / 强化拒止 tooltip

⇒ git diff --numstat 显示的 74/1 **不能整体归因给 B23**；B23 只占 +45（筹码 5 块）与 ±0（weird_dice 键替换）。写入顺序上两条线互不覆盖（本批 hunk 均为纯插入，未删他人行）。

---

## 8. 回滚方式

本批只有 2 个 hunk，且都在单一文件：

1. 删除 ModTooltipHandler.java:753-797（45 行）；
2. 把 :456 的 tooltip.astral_dice.weird_dice_heal 改回 tooltip.astral_dice.weird_dice_cooldown。

补丁脚本留存于 fabric-1.20.1/build/b23_patch.py（build/ 已被 .gitignore:60 忽略，不入库），脚本为「断言先行 + 幂等闸门」形式，可用于复核插入内容与行尾。
