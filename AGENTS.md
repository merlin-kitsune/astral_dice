# Agent Instructions

This repository is a Minecraft NeoForge 1.21.1 mod development workspace for an Astral Dice mod.

Project baseline:
- Target Minecraft version: 1.21.1
- NeoForge loader version: 21.1.235
- Java language level: 21
- Build system: Gradle with the NeoForge ModDev plugin
- Base package/group: com.merlinkitsune.astral_dice

When extending this workspace:
- Prefer editing the existing Gradle configuration before creating new files.
- Keep NeoForge and Minecraft version properties synchronized in gradle.properties and build.gradle.
- New external Java libraries should be added as Gradle Maven dependencies in the dependencies block and, if needed, a corresponding repository should be declared in the repositories block.
- If a new third-party mod dependency is required, use a Maven repository URL (for example Modrinth) and declare it in the build.gradle repositories block.
- Reuse the existing NeoForge MDK-style structure instead of scaffolding a different mod layout.
- When new source/resources are added, keep the mod metadata generation task and resource declaration intact.

## 包结构规范（Package Layout）— 必须遵守

`item` 包按物品类别拆分子包，新增物品类必须放入对应子包；**公共/共享类保留 `item` 根包**：

| 子包 | 内容 |
|---|---|
| `item`(根) | `ModItems`(注册中心)、`CurioSlotUtil`(通用装备工具)、`StarLightManager`/`HealingManager`/`MarkManager`(玩家资源管理器)、`BossEntityUtil`、`InvestigationEventUtil` |
| `item.dice` | 骰子:`DiceCurioItem`、`DiceTier`、`DiceTierRegistry` |
| `item.card` | 卡牌:`CardItem`、`BaseEffectCardItem` + 全部效果牌、`RandomCardHandler`、`EffectCardPeriod`、`EffectCardUtil`、`ExclusiveCardUtil` |
| `item.chip` | 筹码:`BaseChipItem` + 全部筹码(医疗箱/瞄具/美工刀/新筹码等) |
| `item.sign` | 立牌:`BaseSignItem` + 全部立牌 |

规则:新增骰子/卡牌/筹码/立牌类分别放入 `item.dice`/`item.card`/`item.chip`/`item.sign`;跨包引用需显式 import(禁止依赖同包隐式可见性)。

## 立牌命名规范（Sign Naming Convention）— 必须遵守

立牌使用**固定的英文 id** 作为其唯一标识，贯穿 Java 代码（类名/字段/方法/注册名/注释中的英文标识）与 MC 数据包（lang key、效果注册 id、纹理文件名）。**禁止**再使用中文翻译生成的英文词（如 guardian/sweeper/business/ninja/vampire/investigator）作为标识符。

### 现有立牌 id 对照表

| 中文名 | 英文 id | 物品注册名 | 类名 |
|---|---|---|---|
| 看板 | mimi | mimi_sign | MimiSignItem |
| 经商 | parunan | parunan_sign | ParunanSignItem |
| 扫地机 | jasmine | jasmine_sign | JasmineSignItem |
| 护法 | misaki | misaki_sign | MisakiSignItem |
| 史莱姆 | lulu | lulu_sign | LuluSignItem |
| 忍者 | komachi | komachi_sign | KomachiSignItem |
| 上班族 | padman | padman_sign | PadmanSignItem |
| 大侦探 | fanny | fanny_sign | FannySignItem |
| 调查员 | rin | rin_sign | RinSignItem |
| 占星师 | haiqing | haiqing_sign | HaiqingSignItem |
| 吸血鬼 | papara | papara_sign | PaparaSignItem |
| 秘密侦探 | bonnie | bonnie_sign | BonnieSignItem |
| 大当家 | fen | fen_sign | FenSignItem |

### 已统一命名的效果/数据（参考）

- 效果注册 id：`misaki_burst`（爆发）、`papara_bite`（嘬一口）、`jasmine_sweep`（清扫）、`komachi_count`（忍者出牌计数）
- 数据组件注册 id：`misaki_sign_charge`/`misaki_sign_stacks`、`komachi_sign_charge`、`jasmine_atk_bonus`/`jasmine_def_bonus`/`jasmine_armor_penalty_end`、`card_uses`（战斗牌剩余次数）
- 玩家附件注册 id：`komachi_use_count`/`komachi_last_card`、`komachi_extra_play_active`（忍者主动临时出牌数+1，仅当前效果牌周期）、`healing_points`（治愈点数，单一数值池）/`healing_prev_blessing`（赐福结束边沿检测标记，服务端）、`rin_pages`
- 纹理：`textures/mob_effect/misaki_burst.png`、`jasmine_sweep.png`、`papara_bite.png`、`komachi_count.png`
- 立牌主动技能冷却为**玩家级**（`sign_active_cooldown_end`），等待类技能状态类型：占星师=1、秘密侦探=2
- **立牌主动技能不再掷骰**：经商/扫地机/护法/大侦探等主动效果均为"随机获得以下任一效果"（代码内 ThreadLocalRandom 直接随机选，不再有 1d10 掷骰/骰点显示）。`roll_result`/`roll_cooldown` 数据组件已删除。
- **立牌联动规则**：大侦探(fanny)与秘密侦探(bonnie)之间**无主动技能联动**（fanny 不再因附近有 bonnie 而能抽取"调查阶段"事件）；但 fanny/bonnie 触发事件时均会触发调查员(rin)立牌被动（`AstralEventSystem.applyRinSignPassive`，佩戴 rin 的玩家获活体书页）。"调查阶段"事件仅由 bonnie 击杀"隐匿调查"目标触发。

### 新增立牌时的要求

1. 选定一个新的唯一英文 id（3~8 个小写字母，语义化，如 `dragon`），将中文名与 id 追加到上方对照表。
2. 类名 `XxxSignItem extends BaseSignItem`，物品注册名 `xxx_sign`。
3. 关联的效果注册 id / 数据组件 id / 附件 id 一律以该 id 为前缀（如 `xxx_burst`、`xxx_sign_stacks`）。
4. 纹理图标文件名与效果注册 id 一致（`textures/mob_effect/xxx_*.png`）。
5. lang 中物品/效果/tooltip key 使用该 id。
6. 在 `ModItems` 注册、`ModCreativeTabs` 加入创造栏、`datagen/ModItemModelProvider` 加入模型、`datagen/ModRecipeProvider` 加入配方、`curios/tags/item/stand.json` 加入标签。
   - **配方统一使用 shape(`ShapedRecipeBuilder`)**:空白立牌/空白筹码(升级配方为上一级物品)始终在 3×3 **中央**,mod 物品(星币/星盘/骰子等)在**中轴**(中间行/列),原版材料在四角;空槽用空格,禁止用 `_`。
7. **禁止**将中文名直译为英文标识符（如把"护法"写成 guardian、"扫地机"写成 sweeper）——一律使用约定 id。
8. 若立牌有专属卡牌/事件等联动，专属卡注册到 `RandomCardHandler.registerExclusiveCard`，事件类型注册到 `AstralEvents`。

### 注意：非立牌物品不受此规范约束

筹码（chip，如忍术飞镖 `ninja_star`、魔法秘典 `magic_tome`）、卡牌、骰子等物品的英文名按其自身命名约定，与立牌 id 无关（例如 `NinjaStarChipItem` 是筹码而非立牌，保留 ninja 词根是正确行为）。

## 效果牌命名与结构规范（Effect Card Convention）— 必须遵守

效果牌**不再区分"功能效果牌/伤害效果牌"**,全部统一继承 `BaseEffectCardItem`(item.card 包),共用同一套使用流程(专属校验→出牌锁→施加效果→出牌登记→复制计数→消耗)。

### 结构要求
1. 类名 `XxxCardItem extends BaseEffectCardItem`,物品注册名 `xxx`(如 `monster_laser`),在 `ModItems` 注册。
2. 效果实现二选一:
   - **简单状态牌**:覆写 `getEffect()` 返回效果引用(基类自动施加 `getEffectDuration()` 默认 60 秒);
   - **复杂逻辑牌**:覆写 `applyEffect(Level, Player, LivingEntity, ItemStack)`(施加实际效果)。
3. 按需覆写:
   - `canUseOnOtherPlayers()`:是否可对他人使用(下蹲+右键/点击实体);
   - `countsForCopy()` + `cardTypeId()`:是否参与忍者立牌/魔法秘典的复制计数(功能效果牌= true);
   - `isExclusive()`:专属牌(绑定获得者,见 `ExclusiveCardUtil`;活体书页/命运的指引= true)。
4. 效果注册:在 `ModEffects` 注册对应 MobEffect;法伤/攻防加成结算分别注册到 `SpellDamageRegistry`/`DiceCombatModifiers` 修饰器。

### 冷却机制(EffectCardPeriod 统一管理,无需在各牌实现)
- **出牌数**:基础 1 + 固定来源(大背包/忍术飞镖)+ 临时来源(活体书页/命运指引/忍者立牌主动),由 `registerFixedSource`/`registerTemporarySource` 注册。
  - 忍者立牌主动的临时出牌数+1 使用**玩家附件标记** `komachi_extra_play_active`(仅当前效果牌周期生效,周期归零时由 `EffectCardPeriod.tick`/`registerPlay` 清除),不再使用状态效果。
  - **忍者立牌被动**(`KomachiSignItem.onEffectCardUsed`,每使用 3 张效果牌触发一次):复制最后一张使用的效果牌到物品栏 + 主动技能冷却立即减少 30%(剩余部分) + 伤害类效果牌伤害加成 +1(计数器"效果牌伤害增益",附件 `komachi_damage_bonus`,上限由配置 `komachi_damage_bonus_max` 控制,默认 10,最大 16,卸下立牌重置)。伤害加成经 `SpellDamageRegistry` 计入激光/板砖/轨道炮/定向爆破/活体书页的法伤;tooltip 按观看者实时显示加成后的伤害数值。
- **效果待定**:新增效果牌后,在 `EffectCardPeriod` 静态块用 `registerEffectPendingSource` 注册"效果是否在生效"的判定(冷却归零但效果未结束则禁止开新轮)。
- 出牌即开始 30 秒冷却(`registerPlay`),冷却归零出牌数归零(`tick` 由 ModEventHandlers.onPlayerTick 驱动)。

### 新增效果牌检查清单
1. `ModItems` 注册 + `ModCreativeTabs` + `datagen/ModItemModelProvider` + `datagen/ModRecipeProvider`;
2. `ModEffects` 注册效果(如需);
3. `EffectCardPeriod` 注册效果待定源(如需);
4. 参与随机发放则加入 `RandomCardHandler` 卡牌池;专属牌注册 `registerExclusiveCard`;
5. 法伤/攻防加成注册到对应修饰器注册表;
6. 禁止在牌内自行实现 `use()` 出牌/冷却逻辑(统一由基类处理)。

## 治愈流派规范（Healing System）— 必须遵守

治愈点数由 `HealingManager` 统一管理(玩家级共享资源,与具体饰品解耦)。
**治愈体系无独立计时器**,完全遵循"骰神赐福"效果的生命周期。

### 点数构成
- **治愈点为单一数值池**(存储于附件 `healing_points`,恒 ≥ 0,上限由 `HealingManager.getCap` 提供):
  上限 = max(10, 玩家最大生命值 ÷ 2)(即 MC 中 ♥ 的数量;20 HP → 10 点;下限固定 10,
  避免神秘遗物+ 七咒之戒死亡丢失生命值上限时点数过低)。
- 来源:史莱姆立牌被动(受击 +1)/主动(+3)、缓冲盾牌(受击 +2)、医疗箱(触发赐福时,见下)。

### 运行规则(跟随骰神赐福)
1. **触发骰神赐福时**(`ModEventHandlers` 赐福触发块末尾调用 `HealingManager.onBlessingTriggered`):
   1. 先增加装备的医疗箱筹码治愈点(紧急 +1、完备 +3,可叠加,受上限);
   2. 再获得 **当前治愈点 × 2** 的治疗量(回血,不扣点;对应 MC 1♥/层)。
2. **骰神赐福结束时**(由 `HealingManager.tick` 边沿检测驱动,附件 `healing_prev_blessing` 记录上一周期状态):
   治愈点**减半(向下取整)**。边沿检测而非 Expired 事件,避免登录/死亡强制移除效果时误减半。
3. **执行优先级最后**:触发赐福时的回血结算置于赐福触发块末尾,晚于同事件内所有影响治愈点数量的效果
   (史莱姆受击/缓冲盾牌钩子在伤害事件更早处执行;医疗箱加点在回血前完成)。

### 医疗箱筹码
- **装备时**:立即恢复生命(1 治愈单位 = 2 点血量;紧急 +2、完备 +6),经 `HealingManager.onMedkitEquipped`。
- **触发骰神赐福时**:增加治愈点(紧急 +1、完备 +3),由 `onBlessingTriggered` 统一结算。
- **卸下无副作用**:不扣治愈点(治愈点是玩家资源,与装备状态解耦)。

### 关键实现
- `tick`(onPlayerTick 驱动):上限收缩(最大生命降低时收缩点数)+ 赐福结束边沿检测 + 效果刷新。
- `add`/`spend`/`clear`(死亡清零):纯点数增减,不触发回血。
- 效果显示:等级 = 当前治愈点;时长 = 骰神赐福剩余 tick(赐福中)/固定常显时长(无赐福,持续刷新);归 0 自动移除。
- 死亡:清空治愈点与边沿标记(`HealingManager.clear`)。

## 星光流派规范（Starlight System）— 必须遵守

星光点数由 `StarLightManager` 统一管理(玩家级共享资源,与具体饰品解耦)。

- **星光为固定点数,不随时间衰减,无计数器,只有增加与减少**(存储于附件 `player_starlight`,上限由配置 `max_starlight` 控制,默认 32)。
- **基础值(下限)默认 0**:预留接入点 `StarLightManager.getBasePoints`,未来"固定增加星光"的筹码在装备期间提供常驻基础值(与治愈基础点模式一致)。
- **消耗后自动补回**:`spend()` 消耗星光后若低于基础值,`set()` 自动补充回基础值(`Math.max(base, ...)`)。
- **显示**:所有影响星光的立牌/筹码 tooltip 显示当前星光点数(经商立牌计数器;手电筒/八面骰筹码经 `tooltip.astral_dice.chip.starlight`)。
- 获取来源:经商立牌被动/赐福加成、手电筒筹码攻击加成、八面骰累计等。
- **看板立牌(mimi)被动与星光无关**:每获得/变换 1 张战斗牌获得 1 星币(获得:经 `RandomCardHandler.giveCardTo` 发放战斗牌;变换:看板主动变化已装备卡牌并插入 1 张,每张 1 星币)。

## 立牌 tooltip 格式规范（Sign Tooltip Format）— 必须遵守

所有立牌 tooltip 由 `ModEventHandlers` 中的统一辅助方法渲染,格式如下:

```
主动技能（按下 <按键> 触发）:
<标准项>
<带子项>:
- <子项>
被动技能:
<标准项>
<带子项>:
- <子项>

<备注信息(紫色,无符号)>

<立牌计数器>
```

- 标题:金(§6),结尾带冒号;主动技能标题不含冷却倒计时(冷却由底部"冷却中"行单独显示)。
- 列表:普通项无符号、无缩进;子项带 `- ` 前缀(以 lang 中两个空格开头的行识别);**无前导空格缩进**。
- 备注区:浅紫(§d)、无标题、无列表符号。
- 颜色约定:标题=金(§6)、时间=蓝(§9)、数值=黄(§e)、效果=青(§b)、负面/冷却中=红(§c)、普通=灰(§7)、备注=浅紫(§d)。
- 时间格式:持续时间统一 `§9MM:SS§r`(蓝),不加外括号;速率/数值保持原样。
- 战斗牌 tooltip:费用置于**最上方**、黄色,用 `⨀` 符号按费用重复(1费=⨀、2费=⨀⨀),费用由 `CardRegistry.cost(type, player)` 动态提供(含护法名刀折扣);下方为描述行(`点数 | 剩余次数: X`)。

## 骰子槽位与配置规范（Dice Slots & Config）— 必须遵守

- **立牌栏固定 1**(`stand.json size=1`),所有骰子一致,不随骰子/星级变化——`DiceTier` 无立牌加成字段,`DiceCurioItem` 不做立牌槽位动态调整。
- **立牌无独立升星**:铁砧立牌升星与 `SIGN_STAR_LEVEL` 数据组件已移除;护法立牌(misaki)爆发/名刀的星级加成一律取**玩家装备骰子的星级**(`WeaponEnhancement.starLevel`,经 `DiceCombatContext.misakiStar` 传递),未装备骰子或 0 星骰子则无星级加成。
- 筹码栏必须佩戴骰子才有(`chip.json size=0`),数量由 `DiceTier.chipBonus` 按星级计算(基础/金:1~3;钻石:1+星;合金:2+星),由 `DiceCurioItem` 动态调整。
- 卡牌栏槽位由 `DiceTier.cardSlots` 定义(基础 4 / 金 6 / 钻石 8 / 合金 12,攻防各半)。
- 骰神赐福持续时长由配置 `dice_blessing_duration_seconds` 控制(默认 60),代码经 `GameplayConstants.DICE_BLESSING_DURATION_TICKS` 引用,禁止硬编码。
- 新增骰子:在 `ModItems` 静态块注册 `DiceTier` 即可(item 参数必须传 `Supplier` 延迟解析,禁止静态初始化调用 `.get()`)。
- 新增筹码:在 `ModItems` 注册 + `ModCreativeTabs` + `datagen/ModItemModelProvider` + `datagen/ModRecipeProvider` + `curios/tags/item/chip.json` + lang(名称/tooltip),并加入 `ModItems.isChipItem`;属性类筹码(速度轮滑/摩托头盔/夹心饼干)覆写 `ICurioItem.getAttributeModifiers(SlotContext, ResourceLocation, ItemStack)`,修饰器 id 用 `BaseChipItem.attributeModifierId` 按物品派生(同属性不同筹码不得共用 id,否则后装覆盖先装)。

## 新筹码一览（Chips）

| 中文名 | 注册 id | 效果 |
|---|---|---|
| ATM机 | `atm` | 装备时星光+1;使用星光兑换星币时兑换量(星币产出)+40%(`ResourceConversion.starlightToStarCoins`) |
| 银行卡-余额少/余额多 | `bank_card_low`/`bank_card_high` | 装备期间星光基础值 +4/+7(下限,`StarLightManager.getBasePoints` 实时计算,卸下回落;装备时 `set` 自动补回) |
| 银行卡-用不完 | `bank_card_unlimited` | 装备时星光+3;每次骰神赐福结束后,自身及团队(MC 同队)成员获得 3 星币(`BankCardUnlimitedChipItem.onBlessingEnd`,死亡清场不发放) |
| 拳击手套-初/中/高级 | `boxing_gloves_low/medium/high` | 骰神赐福攻击力 +1/+3/+5(`DiceCombatModifiers` 攻击修饰器) |
| 速度轮滑-初/中/高级 | `speed_skates_low/medium/high` | 移动速度 +5%/+10%/+20%(属性修饰器) |
| 摩托头盔-一般/中级/高级 | `moto_helmet_low/medium/high` | 护甲值 +2/+4/+8;盔甲韧性 +2 仅高级(属性修饰器) |
| 夹心饼干-一般/可口/美味 | `sandwich_low/medium/high` | 最大生命值 +2/+4/+8(属性修饰器) |
| 魔法箭袋 | `magic_quiver` | 使用效果牌后对带标记目标造成法伤 → 施加一层标记并返还第一张使用的效果牌(每分钟一次;追踪附件 `magic_quiver_tracking`/`magic_quiver_first_card`,冷却 `magic_quiver_cooldown_end`) |
| 缓冲盾牌 | `buffer_shield` | 受到攻击时 +2 治愈 +3 星币(每分钟一次,冷却附件 `buffer_shield_cooldown_end`) |
| 星币锤 | `star_coin_hammer` | 装备时星光+5;持有星币超过 20 枚时,每次进入骰神赐福消耗 3 星币并按持有总数 30% 提升攻击力(星币袋按 9 算;加成附件 `star_coin_hammer_bonus`,赐福结束清除) |

注意事项:
- 属性类筹码(速度轮滑/摩托头盔/夹心饼干)通过 Curios 属性修饰器即时生效,不进入骰战修饰器注册表。
- 魔法箭袋追踪仅在 `countsForCopy()` 的效果牌(王之力/狂暴/岿然不动)使用后开启;返还卡类型映射见 `MagicQuiverChipItem.effectCardByType`。
- 星币锤/银行卡-用不完的赐福开始/结束钩子位于 `ModEventHandlers.onLivingDamagePre`(triggeredBlessing 块)与 `onDiceBlessingExpired`(顶部,早于骰子检查)。

## 新效果牌与大当家立牌（Cards & Boss Sign）

- **全力攻击不在随机卡牌池**(`RandomCardHandler.attackCards`/`ALL` 已移除):仅能通过骰子内"蓄力"在骰神赐福结束时返还获得(`ModEventHandlers.onDiceBlessingExpired`)。
- **新效果牌**(均继承 `BaseEffectCardItem`):
  - 巧克力蛋糕 `chocolate_cake`:恢复 4 点生命值(治疗类);
  - 汉堡 `hamburger`:恢复 8 点生命值(治疗类);
  - 奢华大餐 `luxury_feast`:可对自身/他人(下蹲右键),治疗目标及周围 6 格内所有玩家 6 点(治疗类);
  - 你有我有 `you_have_i_have`:仅能对其他玩家右键使用(覆写 `use()` 禁止对己、`interactLivingEntity` 仅响应玩家),自身与目标各获得一张随机卡牌(`RandomCardHandler.giveCardTo(ALL)`);
  - 加急加快 `express_delivery`:可对自身/他人(下蹲右键),目标获得 迅捷 II 1:00。
  - **治疗类效果牌**通过 `BaseEffectCardItem.isHealingCard()` 标识(默认 false),使用后触发大当家被动 `FenSignItem.onHealingCardUsed`;治疗牌不参与复制计数(countsForCopy=false)。
- **大当家立牌**(`fen_sign`/`FenSignItem`):
  - 计数器**养精蓄锐**(玩家级附件 `fen_recharge`,上限 5):1 分钟未触发赐福 +1(附件 `fen_last_blessing_tick`,`FenSignItem.tick` 驱动);触发赐福 -1(`onBlessingTriggered`);使用治疗类效果牌 +1。
  - 被动:拥有养精蓄锐时攻击/防御 +2(`DiceCombatModifiers` 修饰器,动态判断)。
  - 主动"战斗爽"(1:00):攻击 +3(`fen_frenzy` 效果);若拥有养精蓄锐则恢复 6 血 + 迅捷 1:00;若已达 5 层则消耗 2 层,置 `fen_cleave_pending`,下次赐福启用"战斗爽·扩散"(附件 `fen_cleave_active`):每次攻击将总伤害 80% 施加给目标 6 格内其他敌对目标(伤害源 `ModDamageTypes.diceDamage`),赐福结束清除(`onDiceBlessingExpired`)。

## 骰战闪避与防御规范（Dodge & Defense）— 必须遵守

骰战结算位于 `ModEventHandlers.onLivingDamagePre`(需攻击者赐福激活 + 佩戴骰子 + 近战):

- **玩家侧闪避判定已停用**(`PLAYER_DODGE_ENABLED = false`):未佩戴骰子的玩家不再进行闪避对骰,直接进入常规防御结算。
  闪避代码**保留供未来使用**——对骰与闪避失败结算仍在 `targetDiceResult.isEmpty()` 分支内,改回 `true` 即可恢复。
  - **闪避失败伤害 = 基础伤害值 + 攻击方骰点 + 卡牌加成**;基础伤害值 = 属性攻击 + 立牌/筹码/效果攻击修饰器;
    攻击方骰点/卡牌加成均取本次实际掷出的值(非最大值);全力攻击倍率在最终伤害处适用。
- **怪物(含无护甲)**:不闪避,始终防御——每次受击掷 1d6 防御骰计入防御力,最终伤害 = 攻击力 - 防御力(按双方骰点计算)。
- 其余目标(佩戴骰子的玩家)维持原防御结算:防御 = 2 + 护甲 + 1.8×韧性 + 防御骰(赐福中) + 防御卡/修饰器。

## 编译产物上传规则（Build Deploy Rule）— 必须遵守

`build.gradle` 已内置两种分发任务，**构建后自动触发**，无需手动指定任务：

| 场景 | 触发方式 | 上传目标 |
|---|---|---|
| **默认（非用户指定）** | `gradlew build` | 仅推送到**项目自身测试环境** `run/mods`（用于本机 runClient 调试） |
| **用户指定推送整合包** | `gradlew build -PdeployToPack`，或显式 `gradlew build pushToGame` | **额外**（在推送 run/mods 之后）推送到整合包 `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods` |

规则要点：
1. **禁止**在用户未指定时向整合包目录写入任何文件——默认构建只触碰 `run/mods`。
2. 推送时自动删除目标目录下旧的 `astral_dice-*.jar`，再复制新产物。
3. 若需在开发中手动强制推送整合包，使用 `-PdeployToPack`（与 `build` 组合）。
4. 相关 Gradle 任务：`pushToDevRun`（默认）、`pushToGame`（用户指定）；两任务均 `dependsOn jar`，产物来自 `build/libs/astral_dice-*.jar`。