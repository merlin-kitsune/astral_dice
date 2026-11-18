# B1-CHIPS 移植报告(forge-1.20.1 → fabric-1.20.1)

范围:6 个筹码物品类 + 配方条件类 + 注册/创造栏接线 + 模型/贴图/配方/进度/饰品标签/三语 lang。
基线:forge-1.20.1 同路径同名文件(同 MC 1.20.1)。neoforge-1.21.1 未作为源(其 loot_table/recipe 已改单数目录)。
只读自检已跑;未运行 gradle、未启动游戏(按任务书由主 Agent 统一编译验证)。

## 1. 交付清单

### 1.1 Java(新增)

| 文件 | 行数 | 与 forge 同文件 diff 变更行 |
|---|---|---|
| fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/AbyssReagentChipItem.java | 151 | 21 |
| .../item/chip/ExtraBatteryChipItem.java | 67 | 0(逐字节相同) |
| .../item/chip/FlashlightBurstChipItem.java | 121 | 6 |
| .../item/chip/FortuneStickChipItem.java | 125 | 12 |
| .../item/chip/InfiniteGridChipItem.java | 98 | 6 |
| .../item/chip/SensorChipItem.java | 75 | 4 |
| .../recipe/condition/ChipRecipesEnabledCondition.java(新建目录) | 78 | 占位重写,见 §3 |

item/chip 目录两线均为 57 个 .java(数量追平)。

### 1.2 Java(修改)

| 文件 | 改动 | 位置 |
|---|---|---|
| .../item/ModItems.java | 6 条 import + 6 个注册块 | import 段在 FlashlightChipItem 之后;注册块 line 833(extra_battery)/853(flashlight_burst)/860(fortune_stick)/866(abyss_reagent)/872(infinite_grid)/878(sensor) |
| .../init/ModCreativeTabs.java | 6 行 output.accept | 123 fortune_stick / 131 flashlight_burst / 137 abyss_reagent / 146 sensor / 158 infinite_grid / 189 extra_battery |
| .../component/ModAttachments.java | 3 个附件键 + 7 个访问方法 | 353-397(布局与 forge 同位:紧邻 setMosesDodgeCounterRewarded 之后) |
| .../datagen/ModItemModelProvider.java | 6 行 generateFlatItem | 168 extra_battery;171-175 flashlight_burst/fortune_stick/abyss_reagent/infinite_grid/sensor |
| .../datagen/ModRecipeProvider.java | 6 段 ShapedRecipeBuilder | 1104 infinite_grid / 1131 abyss_reagent / 1145 fortune_stick / 1158 flashlight_burst / 1172 sensor / 1251 extra_battery |

⚠️ component/ModAttachments.java 超出了「6 个物品类」的字面 scope,但属必要:缺这 3 个键时爆闪判重 / 财运签每周期一次 / 深渊装备闸门三个持久化语义无法 1:1。纯新增,未改动任何既有键。

### 1.3 资源与数据(源统一取 forge-1.20.1)

| 类别 | 路径 | 数量 |
|---|---|---|
| 贴图 | fabric-1.20.1/src/main/resources/assets/astral_dice/textures/item/<id>.png | 6(与 forge 字节一致,511-618 B) |
| 物品模型 | fabric-1.20.1/src/generated/resources/assets/astral_dice/models/item/<id>.json | 6(datagen 目录,与既有 137 个同模板) |
| 配方 | fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/<id>.json | 6 |
| 进度 | fabric-1.20.1/src/generated/resources/data/astral_dice/advancements/recipes/misc/<id>.json | 6 |
| 饰品标签 | fabric-1.20.1/src/main/resources/data/curios/tags/items/chip.json | 61 → 67(与 forge 该文件逐值相等) |
| 三语 lang | fabric-1.20.1/src/main/resources/assets/astral_dice/lang/{en_us,zh_cn,ja_jp}.json | 各 860 → 872(+12 = 6×(name + guide.entry)) |

<id> = flashlight_burst_chip / fortune_stick_chip / abyss_reagent_chip / infinite_grid_chip / sensor_chip / extra_battery_chip。

- data/trinkets/tags/items/astral_dice/chip.json 内容为 {"values":["#curios:chip"]} ⇒ 镜像引用,自动覆盖,无需改。
- 模型/配方/进度均在 datagen 产物目录(generated/resources),该目录已由 build.gradle sourceSets 纳入资源集,无需另放 main/resources。
- 配方/进度 JSON 由 forge 同名文件去顶层 conditions 块生成;变换口径已用两线共有的 big_bowl_stew_chip 做对照组,结果与 fabric 现有文件逐字节相等。

## 2. 1:1 要点(常量 / 耐久 / 冷却 / tick / 判定顺序)

| 类 | 关键常量(forge 原值,未改) |
|---|---|
| AbyssReagentChipItem | EQUIP_POINTS=2、BOON_POINTS=2、ABSORPTION_RATIO=2.0F、MIN_ABSORPTION=2.0F、ABSORPTION_DURATION_TICKS=1200、ABSORPTION_CAP_PER_LEVEL=4.0D |
| ExtraBatteryChipItem | COOLDOWN_MULTIPLIER=0.5D |
| FlashlightBurstChipItem | STARLIGHT_PER_FIRST_ATTACK=1、STARLIGHT_PER_DAMAGE=2、MAX_TRACKED_TARGETS=256 |
| FortuneStickChipItem | RANGE=16、MIN_STARLIGHT=1、MAX_STARLIGHT=7、WEIGHT_SATURATION=10 |
| InfiniteGridChipItem | BROADCAST_RANGE=8、BROADCAST_STACKS=1 |
| SensorChipItem | MARK_PER_DICE_MALUS=3、MAX_DICE_MALUS=4 |

未被改动的语义(逐条核过):

- AbyssReagentChipItem.onHealingTriggered:先读 before=getAbsorptionAmount(),再 addEffect,before+grant 后 setAbsorptionAmount —— **读/写顺序原样**;放大等级 = max(当前层, ceil(target/4)-1);1.20.1 无 MAX_ABSORPTION 上限,不做 min 钳制(与 forge 一致)。
- FortuneStickChipItem:加权 roll 公式 w(v)=v·(1-b)+(8-v)·b、b=clamp(starlight/10,0,1)、starlight≥10 恒返 1、nextDouble()*total 逐档扣减 —— 原样。
- FlashlightBurstChipItem:去重载体仍是「该玩家自己的」flashlight_burst_targets 字符串(UUID 逗号分隔),已达 256 条不再发放;判定顺序(客户端 → target==attacker → isEquipped → isHostileTo)原样。
- InfiniteGridChipItem:static boolean broadcasting 重入保护 + try/finally 复位原样;一次性发放(一次加 N 层只广播一次)由调用方语义保证,类内未改。
- SensorChipItem:malus=min(4, getLevel(target)/3),返回 max(0, targetDice-malus);onDodgeSuccess 中 attacker==victim 直接 return —— 原样。
- ExtraBatteryChipItem.applySignActiveCooldownReduction:ticks<=1 原样返回、未佩戴原样返回、否则 max(1L,(long)(ticks*0.5)) —— 原样,纯查询无副作用。

## 3. 偏差与 seam(forge 原样 → fabric 改法 + 理由)

| # | 位置 | forge 原样 | fabric 改法 | 理由 |
|---|---|---|---|---|
| 1 | 6 个类 | import net.minecraftforge.eventbus.api.SubscribeEvent / net.minecraftforge.event.entity.living.LivingDeathEvent | platform.event.SubscribeEvent / platform.event.entity.living.LivingDeathEvent | 硬约束 1;fabric 线自建事件总线 |
| 2 | 6 个类 | CuriosCompat.getCuriosInventory(player)(starengine) | CuriosApi.getCuriosInventory(player)(compat.curios) | seal:饰品统一走 compat/curios/CuriosApi;返回类型同为 Optional<ICuriosItemHandler>,isPresent/findFirstCurio 调用一字未改 |
| 3 | AbyssReagent / FortuneStick 等 | top.theillusivec4.curios.api.SlotContext | compat.curios.SlotContext | 同上 |
| 4 | PartyRelations 引用(FortuneStick / FlashlightBurst / InfiniteGrid) | com.merlinkitsune.starengine.combat.PartyRelations | com.merlinkitsune.astral_dice.combat.PartyRelations | fabric 线 PartyRelations 已本地化(isSameTeam:97 / hasTeam:115 / isHostileTo:131,签名同) |
| 5 | AbyssReagentChipItem / FortuneStickChipItem 类级 | @Mod.EventBusSubscriber(modid = AstralDiceMod.MODID) | **删除注解** | fabric 无注解自动登记;改为入口类显式 LoaderBus.register(见 §4) |
| 6 | AbyssReagentChipItem.onHealingTriggered | PlayerEffectProtection.apply(player, new MobEffectInstance(MobEffects.ABSORPTION, 1200, amplifier, false, false, true)) | EffectTimerGuard.apply(同参数)+ 4 行 TODO(PLAYER-EFFECT-PROTECTION) | fabric 线无 event/PlayerEffectProtection(整线级缺口,MAPPING-1.5.0.md:195)。**退化点**:计时守卫在,但「被外部清除后下一 tick 补回」的快照兜底缺失。落线后把该行换回 PlayerEffectProtection.apply(参数不变) |
| 7 | recipe/condition/ChipRecipesEnabledCondition | implements ICondition + IConditionSerializer,CraftingHelper.register | 保留 ID/INSTANCE/getID/isEnabled/register 外形,**不实现任何 forge 接口**;isEnabled() 暂返 false;register() 空实现 + 2 处 TODO | Fabric 1.20.1 无配方条件注册面(原版 ResourceCondition 属 1.21.2+);方案见 TODO(CHIP-RECIPES-MECHANISM) |
| 8 | 6 个配方 JSON | 顶层 "conditions":[{ "type": "astral_dice:chip_recipes_enabled" }] | **去掉该块** | fabric 无 conditions 机制,留着会报未知字段;其余字段逐字保留 |
| 9 | ModRecipeProvider | .save(chipOutput) 包 ChipConditionalRecipe | .save(output::accept) | fabric 侧是 FabricRecipeProvider + Consumer<FinishedRecipe>,无 chipOutput;配方本体(图案/材料/unlockedBy)逐字来自 forge |

注:两线 data/astral_dice/recipes/ 现有 JSON **没有任何一个**使用 conditions ⇒ 偏差 7/8 当前不影响行为,只是设施就位。

## 4. 需主 Agent 接线(确切位置)

### 4.1 事件登记(AstralDiceMod.java,必须)

registerListeners() 内按字母序插入两行(anchor:169 行 AdrenalineChipItem / 173 行 FriendshipBadgeChipItem):

- 在 `...item.chip.AdrenalineChipItem.class` **之前**加:`LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.AbyssReagentChipItem.class);`
- 在 `...item.chip.FlashlightChipItem.class`(172)与 `...FriendshipBadgeChipItem.class`(173)**之间**加:`LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.FortuneStickChipItem.class);`

对应方法:AbyssReagentChipItem.java:115-116 `@SubscribeEvent public static void onPlayerDeath(LivingDeathEvent)`;FortuneStickChipItem.java:71-72 `@SubscribeEvent public static void onHostileKill(LivingDeathEvent)`。不登记则两处静默失效。

### 4.2 调用点(forge 有、fabric 全缺 —— grep 确认 fabric 侧 0 命中)

| fabric 落点 | 插入内容 | forge 出处 |
|---|---|---|
| item/HealingManager.java:270 `triggerHealing(Player)` 内,`player.heal(requested);` 之后、`ModAttachments.setHealingTimerEnd(...)` 之前 | `AbyssReagentChipItem.onHealingTriggered(player, effective);`(需先按 forge 口径算 effective = min(total*2, maxHealth-health),且**在 heal 之前**求值) | forge HealingManager.java:275-296 |
| item/HealingManager.java:280 `addChipPoints(Player)` 内 | 追加私有方法 `addAbyssReagentPoints(player)` 并调用:`if (!AbyssReagentChipItem.isEquipped(player)) return; add(player, AbyssReagentChipItem.BOON_POINTS);` | forge HealingManager.java:294-296、305-308 |
| item/ChargeManager.java:74 `addStacks(Player,int)` | gained = total-before;`if (gained > 0) InfiniteGridChipItem.onChargeGained(player, gained);` | forge ChargeManager.java:85-92 |
| item/ChargeManager.java:110 `signCooldownTicks(Player,long)` | 把 `capByCharge(...)` 的结果再经 `ExtraBatteryChipItem.applySignActiveCooldownReduction(player, capped)` 返回 | forge ChargeManager.java:138-144 |
| combat/DiceCombatEvents.java:~285(「电磁炮筹码」注释块**之前**,现 293 行 RailgunChipItem.onAttack 那段的上一行) | `FlashlightBurstChipItem.onAttack(player, target);` | forge DiceCombatEvents.java:661 |
| combat/DiceCombatEvents.java:1701 附近(闪避成功、BufferShieldChipItem.onHurt 之后) | `SensorChipItem.onDodgeSuccess(player, event.getSource());` | forge DiceCombatEvents.java:2284 |
| combat/DiceCombatEvents.java:625-665(defenseBaseDice 求值处,两处) | 用 `SensorChipItem.reduceTargetDice(player, targetPlayer/target, defenseBaseDice)` 包住基准骰点 | forge DiceCombatEvents.java:1031-1055 |
| combat/DiceCombatEvents.java:342/344(新赐福周期复位块)与 354/355(玩家对玩家分支) | 各加一行 `ModAttachments.setFortuneStickBlessingClaimed(player / targetPlayer, false);` | forge DiceCombatEvents.java:729、741 |
| combat/DiceCombatModifiers.java(攻击力修饰器注册链,与 NancyLuSignItem 那段并列) | `ap += FlashlightBurstChipItem.extraDamage(ctx.attacker);`(在 `ctx.attacker.level().isClientSide()` 守卫之后) | forge DiceCombatModifiers.java:518 |

### 4.3 配置项与其它

- config/ModCommonConfig.java:当前无 ENABLE_CHIP_RECIPES(forge config/ModCommonConfig.java:55,默认 false)。加同名布尔项后,把 ChipRecipesEnabledCondition.isEnabled() 的 `return false;` 换成 `ModCommonConfig.ENABLE_CHIP_RECIPES.get()`。
- ChipRecipesEnabledCondition.register():fabric 无注册面,保持空实现;若后续要真做门控,两条候选路径已写在 TODO(CHIP-RECIPES-MECHANISM) 注释里。
- event/PlayerEffectProtection.java 整线缺失(可延后,不影响本批其余筹码)。

## 5. 自检输出

### 5.1 python tools/check_lang_sync.py → EXIT=0

```
四线语言文件校验通过(节选):
  fabric-1.20.1: en_us=872 / zh_cn=872 / ja_jp=872,键集一致
```

### 5.2 python tools/verify_fabric_assets.py

默认读**旧 jar**(fabric-1.20.1/build/libs/astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar,本批改动之前构建):EXIT=1,失败项含本批 6 个筹码的「缺模型/缺贴图」(旧 jar 里本就没有),属假阴性。

为排除假阴性,临时用 `src/main/resources + src/generated/resources` 合成源码 jar 并以 `--jar` 重跑(结果贴下),跑完删除临时产物:

```
[1] 物品闭环: 注册 146 (方块物品 3) / 模型 147 / 贴图 144
[2] 标签闭环: 引用 10 / 自建 29 / 有提供者 10
[3] 音效闭环: 注册 15 / sounds.json 15 / ogg 15
[4] 粒子闭环: 注册 2 / particles/*.json 2
[5] 语言闭环: zh_cn 872 / en_us 872 / ja_jp 872
[6] 手册闭环: 条目 156 / 引用配方 138
[7] 创意标签覆盖: 146/146
[8] 饰品槽位图标: 检查 7 条 icon
```

即:本批 6 个筹码不再出现在「缺模型 / 缺贴图」失败项里,其余 8 项全通过。

残留 FAIL(与本批无关):三语各缺 16 个 `gui.astral_dice.card_shop*` / `gui.astral_dice.chip_shop*` 键。
来源 = 其他批次在途的 fabric `screen/CardShopScreen.java`、`screen/ChipShopScreen.java`、`shop/CardShopOpener.java`、`shop/ChipShopOpener.java`(均为未跟踪新文件),forge 线 lang 已有这 18 条。属 B1-CARDSHOP / B1-CHIPSHOP 批次待办。

### 5.3 禁 import 终检

对 item/chip/、recipe/、component/ModAttachments.java、datagen/、init/ModCreativeTabs.java、item/ModItems.java 递归 grep `net.minecraftforge.` / `net.neoforged.` ⇒ **0 命中**。

### 5.4 格式

全部新增/修改文本文件:UTF-8 无 BOM、行尾 CRLF(与仓库一致)。generated 目录 6 个模型 JSON 与既有 137 个同模板(2 空格缩进、无末尾换行)。

## 6. 无法判定 / 未完成

1. `tools/verify_fabric_assets.py` 未能在**真实构建产物**上验证:本会话不跑 gradle。上面的合成 jar 只证明「资源侧闭环」,不证明打包与运行时;需主 Agent 构建后再跑一次默认形态。
2. 编译正确性未验证(本会话禁跑 gradle)。已人工核实引用的每个 fabric 符号存在且签名一致(ModEffects.DICE_BLESSING:46、ChargeEffect.addStacks:29、StarLightManager.get:27/add:156、MarkManager.apply:33/40、getLevel:50、CurioSlotUtil.hasDiceEquipped:172/hasAnyCurioMatching:56、PartyRelations.isSameTeam:97/hasTeam:115/isHostileTo:131、HealingManager.add:197/spend:209)。
3. 三语 lang 的 12×3 键值**逐字取自 forge 同键原文**(含 §/颜色码与转义),未做任何措辞或术语调整;若 forge 侧后续改动,需同步。
4. 配方在 fabric 侧**没有** chip_recipes_enabled 门控(见 §3 偏差 8)。若产品口径要求「默认关闭、开关后启用」,需主 Agent 决定走哪条门控方案。
5. 16 个 gui.* shop 键缺失属他批(见 §5.2),本批未碰。

---

临时文件已清理(.tmp_b1chips_*.py、合成 jar、探针文件),未触碰 AGENTS.md / scripts/test/mt*.ps1 / neoforge-26.1.2 / models 等他人改动。
