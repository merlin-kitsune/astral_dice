# B1-EFFECTS 移植报告(forge-1.20.1 → fabric-1.20.1)

范围:15 个角色状态效果(13 个效果类 + 2 个复用 `ChipReadyEffect`)+ 2 个 mixin + `DorothyWarmthHealContext` 配套类 + `ModEffects` 注册 + 15 张图标 + 三语 lang。
基线:forge-1.20.1 同路径同名文件(同一 MC 1.20.1)。neoforge-1.21.1 未取源。
只读自检已跑;未运行 gradle、未启动游戏(按任务书由主 Agent 统一编译验证)。
未触碰:`item/ModItems.java`、`init/ModCreativeTabs.java`、`item/sign/**`、`AstralDiceMod.java`、`AstralDiceClient.java`、`network/ModNetwork.java`。

## 1. 实测差集(集合差,非照抄任务名单)

判据:从两线 `effect/ModEffects.java` 抽 `EFFECTS.register("<id>")` 的 id 集合做差。

| 项 | 结果 |
|---|---|
| forge-1.20.1 唯一 id | 67 |
| fabric-1.20.1 唯一 id(移植前) | 53 |
| 共有 id 的**注册顺序** | 两线完全一致(order identical on common = True) |

**MISSING in fabric(15,与任务名单逐项一致,无误报)**:
`airbag_guard_cd, alana_iron_maiden, ame_love, ame_overdose, deepseek_fishing, dorothy_socialite, dorothy_warmth, jill_mixed, kangel_fan, kangel_fan_indicator, lingling_surge, problem_delivery, red_heat_warning, storm_approaching, whetstone_guard_cd`

**EXTRA in fabric(1,正当差异,保留)**:`railgun_ready`(任务书已声明;其颜色 0x81D4FA,留在 `ELECTRIC_SWORD_READY` 之后的原位)。

分布判定:
- `whetstone_guard_cd` / `airbag_guard_cd` 复用的就是同包既有 `ChipReadyEffect`,**不新建类**,只补注册;
- 其余 13 个各有一个独立类文件(见 §2.1);
- 因共有 id 顺序一致,注册只需在 forge 的 3 个相对位置各插一段,即可让 68 项整体顺序与 forge 对齐(railgun_ready 除外)。

## 2. 交付清单

### 2.1 Java 新增 — 13 个效果类(与 forge 同文件**逐字节相同**)

| 文件(均在 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/effect/`) | 行数 | 与 forge diff |
|---|---|---|
| JillMixedEffect.java | 15 | 0 |
| DorothyWarmthEffect.java | 18 | 0 |
| DorothySocialiteEffect.java | 38 | 0 |
| AlanaIronMaidenEffect.java | 13 | 0 |
| DeepseekFishingEffect.java | 16 | 0 |
| RedHeatWarningEffect.java | 20 | 0 |
| StormApproachingEffect.java | 20 | 0 |
| ProblemDeliveryEffect.java | 20 | 0 |
| KangelFanEffect.java | 59 | 0 |
| KangelFanIndicatorEffect.java | 68 | 0 |
| AmeLoveEffect.java | 66 | 0 |
| AmeOverdoseEffect.java | 57 | 0 |
| LinglingSurgeEffect.java | 33 | 0 |

这 13 个类**不需要任何平台改写**:import 面只有原版 Mojmap(`net.minecraft.world.effect.*`、`net.minecraft.world.entity.ai.attributes.*`、`net.minecraft.world.entity.LivingEntity/Player`)+ `com.merlinkitsune.starengine.event.ModEffectRemoval`(fabric 线已依赖 starengine-fabric-1.20.1,同包 `SherryReasoningEffect.java:55/:65` 已在用同一 API)。
对 `ModAttachments` / `*SignItem` / `ChipDamageHandler` 的引用**全部在 javadoc 的 `{@code ...}` 里**,不是编译期依赖。

### 2.2 Java 新增 — 配套类与 mixin

| 文件 | 行数 | 与 forge 差异 |
|---|---|---|
| `event/DorothyWarmthHealContext.java` | 78 | 1 行(javadoc,见 §4) |
| `mixin/HealOrHarmMobEffectMixin.java` | 42 | 0(仅 LF→CRLF) |
| `mixin/RegenerationMobEffectMixin.java` | 38 | 0(仅 LF→CRLF) |

### 2.3 Java 修改

| 文件 | 改动 | 插入位置 |
|---|---|---|
| `effect/ModEffects.java` | 305 → 413 行,`EFFECTS.register` 53 → **68** 项 | 见下 |
| `resources/astral_dice.mixins.json` | `"mixins"` 28 → 30 项 | 见 §5 |

`ModEffects.java` 三处插入(三段内容**原样取自 forge 原文行**,含中文注释与长 javadoc,以保 1:1):

1. **组 1**(组1 = 4 个立牌状态指示器 + 交际花/铁处女/钓鱼):插在 `EFFECTS.register("marked", MarkedEffect::new);` 之后、`DICE_BLESSING` 之前 → `jill_mixed, dorothy_warmth, dorothy_socialite, alana_iron_maiden, deepseek_fishing`(含 forge 的两行中文分节注释)。
2. **组 2**(筹码「保命」冷却倒计时指示器):插在 `EFFECTS.register("railgun_ready", ...)` 之后、`// 对怪激光` 之前 → `whetstone_guard_cd (ChipReadyEffect, 0xCFD8DC)`、`airbag_guard_cd (ChipReadyEffect, 0xFFB74D)`,含 forge 的 4 行裁决注释块。
3. **组 3**:插在 `EFFECTS.register("rin_page_range", ...)` 之后、`ALL` 的 javadoc 之前 → `red_heat_warning, storm_approaching, problem_delivery`(各 2 行无 javadoc)+ 双空行 + `// ── 超天酱立牌(kangel)+ 地雷女立牌(ame)(2026-10-16 冻结规格)──` 注释 + `kangel_fan, kangel_fan_indicator, ame_love, ame_overdose, lingling_surge` 的长 javadoc 与注册行。

### 2.4 资源

| 类别 | 路径 | 数量 |
|---|---|---|
| 效果图标 | `fabric-1.20.1/src/main/resources/assets/astral_dice/textures/mob_effect/<id>.png` | 15(逐字节取自 forge,均 32×32,292–567 B) |
| 三语 lang | `assets/astral_dice/lang/{en_us,zh_cn,ja_jp}.json` | 各 888 → **912** 键(+24) |

lang 24 键/语言 = 15 个 `effect.astral_dice.<id>` + 9 个 `.description`(`ame_love, ame_overdose, dorothy_socialite, kangel_fan, kangel_fan_indicator, lingling_surge, red_heat_warning, storm_approaching, problem_delivery`;forge 本身**没有** description 的 6 个:`airbag_guard_cd, alana_iron_maiden, deepseek_fishing, dorothy_warmth, jill_mixed, whetstone_guard_cd`)——即「forge 有什么就补什么」,未凭空造描述。
插入点:`"effect.astral_dice.rin_page_range.description"` 行之后,按注册顺序、每条先 name 后 description,既有键序不动;值逐字取 forge 同键原文(保留 `§e/§7/§9` 与字面百分号 `%%`)。

## 3. 1:1 要点(颜色 / 持续时间 / 属性修饰器,值全部为 forge 原值未改)

| 类 | 分类 | 颜色 | 持续时间 |
|---|---|---|---|
| JillMixedEffect | BENEFICIAL | 0xFF9AD1 | — |
| DorothyWarmthEffect | BENEFICIAL | 0xFFB74D | — (MAX_STACKS = 5) |
| DorothySocialiteEffect | BENEFICIAL | 0xFF7FA8 | DURATION_TICKS = 1200 |
| AlanaIronMaidenEffect | BENEFICIAL | 0xB0BEC5 | — |
| DeepseekFishingEffect | BENEFICIAL | 0x4FC3F7 | — |
| WhetstoneGuardCd(ChipReadyEffect) | — | 0xCFD8DC | — |
| AirbagGuardCd(ChipReadyEffect) | — | 0xFFB74D | — |
| RedHeatWarningEffect | BENEFICIAL | 0xE04A2F | DURATION_TICKS = 3600 |
| StormApproachingEffect | NEUTRAL | 0x4A6FA5 | DURATION_TICKS = 2400 |
| ProblemDeliveryEffect | HARMFUL | 0x6B8E23 | DURATION_TICKS = 300 |
| KangelFanEffect | HARMFUL | 0xFF8FB1 | MobEffectInstance.INFINITE_DURATION |
| KangelFanIndicatorEffect | BENEFICIAL | 0xFFCD3C | INFINITE_DURATION |
| AmeLoveEffect | BENEFICIAL | 0xE0447A | INFINITE_DURATION |
| AmeOverdoseEffect | BENEFICIAL | 0xD81B60 | DURATION_TICKS = 1200 |
| LinglingSurgeEffect | BENEFICIAL | 0xFF6B2A | DURATION_TICKS = 1200 |

属性修饰器(两条,原样):
- `DorothySocialiteEffect`:`ATTACK_DAMAGE`, uuid `d4b20452-7270-5185-ab1e-46ce1b3325ab`, `ATTACK_BONUS = 1.0D`, `Operation.ADDITION`;并覆写 `getAttributeModifierValue(int amplifier, AttributeModifier modifier)` 恒返回 `ATTACK_BONUS`(不随等级缩放)。
- `AmeOverdoseEffect`:`MOVEMENT_SPEED`, uuid `e275a2a9-48d8-5bb9-a4bb-396b2aff00d7`, `MOVEMENT_SPEED_BONUS = 0.2D`, `Operation.MULTIPLY_TOTAL`。

其它保真项:`KangelFanEffect.applyMark(LivingEntity)` 用 `(…, DURATION_TICKS, 0, false, false, true)` 的显示参数;`KangelFanIndicatorEffect.getCount/mirrorCount/clear` 与 `AmeLoveEffect.getLayers/mirror/clear` 均先用 `com.merlinkitsune.starengine.event.ModEffectRemoval.remove(player, <X>.get())` 清层再重挂 `amplifier = count - 1`;`DorothyWarmthHealContext` 的 `MAX_NESTING = 64`、`ThreadLocal<Deque<Source>>`、触顶清栈语义未改。

## 4. 偏差(forge 原样 → fabric 改法 + 理由)

| # | forge 原样 | fabric 改法 | 理由 |
|---|---|---|---|
| 1 | 属性修饰器 uuid 用**字面量**字符串 | **保持字面量,未改成 `UUID.nameUUIDFromBytes(...)`** | 任务书给的写法是 `UUID.nameUUIDFromBytes("astral_dice:" + key)`,但实测它**算不出这两个既有 UUID**(算出的是 `ffef0cff-8e3c-39f3-9189-3559ad17eee4` / `068230ad-0fee-3ac5-a4e4-0e50ceab7583`)。1.20.1 的 `MobEffect#addAttributeModifier(Attribute, String uuid, double, Operation)` 接受字符串 UUID,改成派生 UUID 等于**换掉修饰器身份** ⇒ 属性叠加/去重行为会变,违反「数值不得改」。fabric 同包既有文件(`BlueCurseEffect:20`、`HannaFloatEffect:42`、`JasmineSweepEffect:19`、`DragonRoarBreakEffect:51`)同样是字面量写法,一致。 |
| 2 | 两个 mixin 源文件行尾为 **LF** | 转 **CRLF** | 本仓红线(UTF-8 无 BOM + CRLF)。仅行尾差异,内容逐字未动。 |
| 3 | `DorothyWarmthHealContext` javadoc 写 `{@link NewSignEvents#...}` | 改为 `{@code NewSignEvents#...}` | fabric 线**没有** `event/NewSignEvents.java`(见 §6.2),保留 `{@link}` 会指到不存在的类;仓库未配 javadoc 任务,不影响编译,但避免悬空链接。 |
| 4 | 平台 import | **无需改** | 13 个效果类与 2 个 mixin 只用原版 Mojmap + 已在依赖里的 starengine `ModEffectRemoval`;`MobEffectInstance` 引用写法(`ModEffects.X.get()`)与 fabric 既有文件一致,无 forge `RegistryObject` 语义差。 |

## 5. mixin 登记(fabric 是显式 config 文件,非 forge 的 build.gradle 条目)

`fabric-1.20.1/src/main/resources/astral_dice.mixins.json`:`"mixins"` 数组在 `"trade.ServerPlayerMixin"` 之后追加两项,数组 28 → 30,插在数组末尾(排在 `trade.*` 之后,与本线既有组织一致):

```json
    "trade.ServerPlayerMixin",
    "HealOrHarmMobEffectMixin",
    "RegenerationMobEffectMixin"
  ],
```

注意:本线 `required: true`、`injectors.defaultRequire = 1`,两个 mixin 的目标 `MobEffect#applyInstantenousEffect` 与 `MobEffect#applyEffectTick` 在 1.20.1 **均存在且单处定义**,HEAD/RETURN 各命中 1 次,不需要 `require = 0` 兜底。refmap 由 Loom 在 `remapJar` 阶段按 config 自动生成(build.gradle 注释已声明),无需手写。

mixin 语义(未改):`@Mixin(MobEffect.class)`;`applyInstantenousEffect` HEAD/RETURN 守卫 `if ((Object) this != MobEffects.HEAL) return;` 后 `push(Kind.INSTANT, amplifier + 1)` / `pop()`;`applyEffectTick` 同理守卫 `MobEffects.REGENERATION`。

## 6. 需主 Agent 接线处(本批未做,已实测定位)

### 6.1 两个 `*_guard_cd` 指示器**没有 tick 驱动**(必须补,否则指示器永不出现)

- forge:`event/PlayerTickEvents.java:243` 起的 `updateChipCooldownIndicators(Player)`,以及它的私有辅助 `refreshIndicatorRemaining(Player, MobEffect, boolean equipped, long endTick)`(含 `REMAINING_TOLERANCE = 40`)。
- fabric:`event/PlayerTickEvents.java` **只有** `updateChipBonusIndicators(Player)`(:167,调用点 :62)与 `refreshIndicatorInfinite(...)`(:197),**缺**上述两者。
- 依赖面 fabric **已齐备**,无需另建:`component/ModAttachments.java:1094-1106` 有 `getAirbagCooldownEnd/setAirbagCooldownEnd/getWhetstoneGuardCooldownEnd/setWhetstoneGuardCooldownEnd`;`item/chip/WhetstoneChipItem.java:42 isEquipped`、`:70 isGuardOnCooldown`;`item/chip/AirbagChipItem.java:33 isEquipped`、`:43 isOnCooldown`。
- 插入位置:`PlayerTickEvents#onPlayerTick` 里 `updateChipBonusIndicators(player);`(:62)**紧邻一行**加 `updateChipCooldownIndicators(player);`,再整体搬入 forge 的两个私有方法(仅需把 forge 的 `ModAttachments.getXxx(...)` 调用对齐 fabric 同名方法签名)。

### 6.2 多萝西「温暖」的**消费侧不存在** —— 目前只有生产端没有读者

- 本批已移植生产端:`HealOrHarmMobEffectMixin`(瞬间治疗)与 `RegenerationMobEffectMixin`(生命恢复)把治疗来源压栈到 `DorothyWarmthHealContext`,`DorothyWarmthEffect.MAX_STACKS = 5` 也已就位。
- 但 fabric 线**没有** `event/NewSignEvents.java`(forge 的读取方是其中 `onLivingHeal`,`EventPriority.LOWEST`,读 `DorothyWarmthHealContext.current()`,null ⇒ 按 1 层处理,再走 `DorothySignItem.isEquippedForWarmth/grantWarmth/refreshWarmthModifier`),也**没有** `item/sign/DorothySignItem.java`。
- fabric 的等价 heal 消费点:`item/sign/ZhaoSignItem.java:398` 的 `onLivingHeal(LivingHealEvent)`(事件类 `platform/event/entity/living/LivingHealEvent` 已存在,由 `mixin/bridge/LivingHealBridgeMixin` 从 `heal(F)V` HEAD 派发)。
- **需主 Agent 裁决**:是先在本线补 `item/sign/DorothySignItem` + 在 `ZhaoSignItem#onLivingHeal` 旁接入 `DorothyWarmthHealContext.current()` 的读取,还是把 `DorothyWarmthHealContext` 暂标为「暂未接线」。当前状态**编译无碍、功能未启用**。

### 6.3 施加点未在本批范围(需另批核对)

本批只交付「效果注册 + 常量类 + 图标 + 文案」;下列施加点在 fabric 线是否存在未逐一核实:`KangelSignItem#applyFanTo/setFanCount`、`AmeSignItem#handleUse/onOverdoseExpired`、`JillSignItem`、`LinglingSignManager`、Deepseek 钓竿、`event/ChipDamageHandler` 里的 KANGEL_FAN −1 固定点数减法段与 `ModAttachments#KANGEL_FAN_COUNT / KANGEL_FAN_TARGETS / AME_LOVE_LAYERS / AME_LOVE_CAP`。若这些类在本线缺失,对应效果会「注册但在游戏里不会被施加」。

## 7. 无法判定项 / 风险

1. **未编译**:按任务书不跑 gradle。全部 13 个效果类与 forge **逐字节相同**,且 import 面只有原版 + starengine 既有 API,预期可编译;但 `ModEffectRemoval` 的实际可用性须由主 Agent 编译确认(fabric 线 `SherryReasoningEffect` 已在用同一方法,风险低)。
2. **refmap**:本仓 Loom 1.14 起 AP 默认关闭、refmap 在 `remapJar` 生成;两个新 mixin 的注入点写的是 Mojmap 名(`applyInstantenousEffect` / `applyEffectTick`),remap 正确性只能在构建后核 `astral_dice.refmap.json`。
3. **lang 竞态(重要)**:写入前(18:00:20 左右)另一个 agent 正在改同一批三个 lang 文件(那一次把行尾从 CRLF 改成 LF、并新增 45 行 item 键)。我基于其**改后**内容插入 24 键并写回 CRLF,`git autocrlf` 会归一化行尾,`git diff` 只应显示我这 24 行新增。**若对方之后再次整文件重写,本批 24 键可能被覆盖**——收尾验收请复跑 `python tools/check_lang_sync.py` 与 `grep -c 'effect.astral_dice.jill_mixed'`(三个文件各应为 1)。
4. **`net.minecraftforge` 字样**:fabric 线全树现有 7 处命中(`event/FirstLootChestHandler.java:41`、`network/CardChoiceCloseMessage.java:16-17`、`network/CardChoiceConfirmMessage.java:16`、`platform/event/OnDatapackSyncEvent.java:9`、`platform/fml/IModInfo.java:4`、`platform/fml/ModContainer.java:4`),**全部是 javadoc 里 `{@code net.minecraftforge...}` 的平台对照说明,不是 import**,且非本批引入。本批 16 个文件 0 命中。终检 grep 请限定 `^import`(`grep -rn '^import .*net\.\(minecraftforge\|neoforged\)'`)。

## 8. 自检输出

```
$ python tools/verify_effect_icons.py
AP_EFFECT_ICONS: line=neoforge-1.21.1    registered=67 covered=67 missing=0 bad=0 sizes=[18x18×1, 32x32×66]
AP_EFFECT_ICONS: line=forge-1.20.1       registered=67 covered=67 missing=0 bad=0 sizes=[18x18×1, 32x32×66]
AP_EFFECT_ICONS: line=neoforge-26.1.2    registered=68 covered=68 missing=0 bad=0 sizes=[18x18×1, 32x32×67]
AP_EFFECT_ICONS: line=fabric-1.20.1      registered=68 covered=68 missing=0 bad=0 sizes=[18x18×1, 32x32×67]

RESULT: PASS —— 四线共 270 个 MOD_EFFECT 注册，图标 100% 覆盖   (EXIT=0)
```

```
$ python tools/check_lang_sync.py
[OK]   neoforge-1.21.1: en_us=1070 键，3 份语言键集一致
[OK]   forge-1.20.1: en_us=1070 键，3 份语言键集一致
[OK]   neoforge-26.1.2: en_us=1094 键，3 份语言键集一致
[OK]   fabric-1.20.1: en_us=912 键，3 份语言键集一致            (EXIT=0)
```

行尾/BOM 复核(实测):13 个新类 + 3 个新类文件全部 CRLF、0 裸 LF、UTF-8 无 BOM;`ModEffects.java` 413 行 / 413 CRLF;`astral_dice.mixins.json` 56 行 / 56 CRLF;三个 lang 文件各 916 CRLF / 0 裸 LF / 结尾带 CRLF。