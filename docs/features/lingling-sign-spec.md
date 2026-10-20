# 怪力乱神立牌（Lingling Sign）实现规格

> 状态：设计已裁决并已实现（2026-10-18）。P0 两线：`neoforge-1.21.1`（已交付）+ `forge-1.20.1`（已移植完成，无遗留待办）。
> 需求来源：用户 2026-10-18 11:13 指令（逐字见 §1）。取证依据：`temp/recon_report.txt`（旧会话只读取证报告）。

## 1. 用户需求（逐字）

创建角色立牌：怪力乱神立牌（品质：史诗）
- 合成配方：P/D/P,D/R/D,P/T/P(P=星盘，D=导电线材，R=红石块，T=钻石骰子)
- 被动技能（拦截者）：第一次装备怪力乱神立牌时获得2张"强化拒止"，拾取或购买的"路障"卡牌会自动转换为"强化拒止"；"强化拒止"吸附的目标数量将会作为额外的充能层数；怪力乱神攻击被"强化拒止"吸附的目标时，造成的伤害+5
- 主动技能（能量突击）：立即获得一张"强化拒止"；使自身当前充能层数转换为攻击力+移动速度加成，持续 1:00；
- 补充说明：被动技能提供的额外充能层数不超过20上限，且按照自身放置的强化拒止正在吸附的目标数量提供，如果被吸附的目标脱离或死亡则减去相应的层数。主动技能提供的攻击力和移动速度加成是根据当前充能层数实时变化的。移动速度加成按百分比计算（比如5点充能就是5%）。怪力乱神攻击任意被吸附的目标都会获得攻击伤害加成（不论这个强化拒止是谁放的），但提供充能数值加成只能是自己放的强化拒止。玩家放入手中的路障牌会自动变成强化拒止牌，玩家如果试图丢出强化拒止牌，则丢出的掉落物会自动变成路障牌
  - ⛔ **2026-10-20 用户裁决修订**：本条的「按正在吸附的目标数量提供 / 目标脱离或死亡则减层」**作废**，改为「每控制一个新目标 +1 层、单个强化拒止最多 5 层（同一目标不重复计数、阵亡不减层）」，详见 §4.6 顶部裁决块。
另外对导电线材、星币尘、再生试剂、标记涂料解绑：不论是否启用筹码合成配方，这4种物品都允许被合成，且它们会参与到立牌合成中

## 2. 身份与命名（已定，不得擅自改）

| 项 | 值 |
|---|---|
| 注册 id | `lingling_sign`（`ModItems.LINGLING_SIGN`）—— **2026-10-18 用户补充指令：id = lingling**（此前草案名 wargod 一律作废） |
| 类 | `com.merlinkitsune.astral_dice.item.sign.LinglingSignItem extends BaseSignItem` |
| 短键（lang/tooltip 前缀） | `lingling` |
| 品质 | `EPIC`（史诗；钻石骰子 ± 星盘 => 史诗，见 AGENTS-full.md:1018） |
| 创造栏 | dice 页（`ModCreativeTabs` 立牌分组，模板同 Kangel/Ame） |
| 图标素材 | 仓库已有 `images/怪力乱神立牌.png`（487 B，32×32）⇒ 复制进 `assets/astral_dice/textures/item/lingling_sign.png`，不要重画 |

## 3. 配方

形状（3×3，`RecipeCategory.MISC`）：

```
P D P
D R D
P T P
```

- P = `astral_dice:star_plate`（星盘） · D = `astral_dice:conductive_wire`（导电线材）
- R = `minecraft:redstone_block`（红石块） · T = `astral_dice:diamond_dice`（钻石骰子）
- 模板：`datagen/ModRecipeProvider.java` 中 Kangel/Ame 立牌段落（旧会话取证件 :1286-1297）。
- **例外登记**：与 AGENTS-full.md:1088「BLANK_SIGN 固定 3×3 正中心 / 立牌骰子固定中下且仅 1 个」冲突 —— 用户显式给出 pattern，按「用户指令优先于工作区规则」处理，本例外**只对 lingling_sign 生效**；需在交付报告中登记，并把例外写进本 spec（已写）。
- runData 后必须跑 `scripts/verify/verify_crafting_recipe_uniqueness.ps1`。

## 4. 被动「拦截者」（佩戴立牌时生效）

判定「佩戴」：复用既有立牌佩戴判定（Curios `stand` 槽查 `ModItems.LINGLING_SIGN`；先例见 AmeSignItem / RenSignItem 的 wearing 判定，不要自造）。

### 4.1 首次装备发 2 张强化拒止
- 语义：**每位玩家一生一次**（跨死亡保留，重登/重复 equip 不重复发）。不要用 `StarLightManager.claimEquipGrant`（它在卸下时 release ⇒ 可刷牌）。
- 实现：新增持久化标记（attachment boolean `lingling_granted`，`.copyOnDeath()`）；装备检测点必须**幂等**（Curios 会重放 equip / 登录重生），用「标记未置位 ⇒ 发 2 张 ⇒ 置位」的原子顺序。
- 发卡：`BaseEffectCardItem.createCopyFor("repulsion", player)`（= `EFFECT_CARD_REPULSION` / 强化拒止）；先尝试塞背包（`player.getInventory().add`），塞不下 `player.drop(stack, false)`。

### 4.2 拾取「路障」自动转「强化拒止」
- neo：`ItemEntityPickupEvent.Post`（可安全改写）；forge：`PlayerEvent.ItemPickupEvent`。仅在佩戴 lingling 时把到手 stack 替换为等量 `EFFECT_CARD_REPULSION`（独占卡用 `createCopyFor`，数量保持）。
- 禁止在 Pre 阶段 `setItem`（旧取证明确：Pre 阶段改写无效/被覆盖）。

### 4.3 购买「路障」自动转「强化拒止」
- 购买出口：`shop/CardShopMenu.java` handleBuy（旧取证 :494-495 `target.set(bought)`）。佩戴 lingling 且购买物为 `EFFECT_CARD_BARRICADE` ⇒ 写入 `createCopyFor("repulsion", player)` 的同数量 stack。
- 购买不触发拾取事件，所以必须在这里单独改。

### 4.4 手持「路障」自动变「强化拒止」
- 佩戴 lingling 时，每 tick（10t 节流）检查主手 + 副手，若是路障则就地替换（数量保留）。幂等：替换后不再命中。
- neo 挂 `event/PlayerTickEvents`（Pre 或 Post 单相位即可）；forge 的 PlayerTickEvents 无 phase 且每 tick 双跑 ⇒ 实现必须幂等。

### 4.5 丢出「强化拒止」掉落实体变「路障」
- `event/TemporaryCardEvents`（`ItemTossEvent`，实体入世前触发）；佩戴 lingling 且丢出的是 `EFFECT_CARD_REPULSION` ⇒ 把 `event.getEntity().getItem()` 换成等量路障（不是取消事件）。
- 只做服务端替换即可（ItemTossEvent 服务端触发覆盖 Q 键 / 拖出背包 / `Player#drop`）。

### 4.6 吸附目标数 => 额外充能层数（独立加成层，用户 2026-10-18 裁决）

> **⛔ 2026-10-20 用户裁决（覆盖本节以下旧口径）**：废弃「吸附目标时充能 +1，该目标被击杀后充能 -1」。
> 新口径 = **每控制一个「新目标」+1 层充能**（同一目标重复吸附**不**重复计数），
> **单个强化拒止最多提供 5 点充能**（上限按**每个力场实体**各 5 点，**不是**全局 5 点）；
> 「击杀 / 脱离 ⇒ 减层」整条**废弃**。实现：力场侧持久化 `controlledTargets`（最多 5 个 UUID，
> 落盘 `RepulsionControlledTargets`），立牌侧按「自己放置的各力场 `chargeStacks()` 之和」重算，
> 合计仍由 `effectiveCharge` 夹在 20 层。

- **不写进 ChargeEffect**；新增 attachment int `lingling_bonus_charge`（`.copyOnDeath()`）。
- 重算（每 10t，佩戴时）：bonus = **自己放置的** `RepulsionEntity` 正在吸附的有效目标数（去重；目标需 `isTracking(uuid)` 且实体仍能在该 level 取回且存活）。
- 有效充能：`LinglingSignManager.effectiveCharge(player) = Math.min(20, ChargeManager.getStacks(player) + bonus)`；上限 20 是「真实 + 额外」合计上限（用户补充说明）。
- 目标脱离/死亡 ⇒ 下一轮重算自动减层（不残留）。
- **前置改动**：`entity/RepulsionEntity` 目前**没有 owner 字段**（只有 `Set<UUID> trackedTargets`）⇒ 必须新增持久化 `ownerUUID`（NBT 落盘，参照 trackedTargets 的 UUID 落盘写法），并在 `RepulsionCardItem` 召唤点（旧取证 :129 附近）写入放置者 UUID。forge 同构。
- 扫描范围口径：从玩家出发取附近 radius 内 RepulsionEntity（先例 `event/RepulsionCreeperGuard.java:88-95` 用 inflate(16)；本立牌按**32** 格扫描并在 spec 记录），`ownerUUID.equals(player.getUUID())` 才计入。
- 其他系统（筹码消耗等）**不感知**额外层（这是用户选择的口径）。

### 4.7 攻击被吸附目标 +5 伤害（任意强化拒止，不分归属）
- 触发面：**玩家本人（佩戴 lingling）对目标造成的任意伤害**（近战/远程/法术都算），目标被**任意** RepulsionEntity 吸附即可（`getEntitiesOfClass(RepulsionEntity.class, target.getBoundingBox().inflate(16))` + `isTracking(target.getUUID())`，先例 `event/RepulsionCreeperGuard.java:88-95`）。
- neo：`LivingDamageEvent.Pre`，`EventPriority.LOWEST`（护甲后、吸收前；`event.setNewDamage(event.getNewDamage() + 5)` **就在同一次伤害上加固定值**）。forge：**`LivingHurtEvent`**（1.20.1 无 `LivingDamageEvent.Pre`，而 `LivingDamageEvent` 派发在吸收/黄心之后 ⇒ `LivingHurtEvent` 才是「护甲后、吸收前」的等价通道，先例 `event/RenShieldManager.java:399-408`），同样 `event.setAmount(event.getAmount() + 5)`。**不嵌套新伤害、不清 i-frame、不绕过护甲**（照 `event/ChipDamageHandler.java:48-123` 先例）。
- **防递归**：必须排除 `ModDamageTypes.extraDamage` 来源（骰战额外伤害通道会再次触发 LivingDamageEvent）。
- 只对「玩家为伤害来源」生效（`source.getEntity()` 是玩家且佩戴 lingling）；宠物/荆棘不算。

## 5. 主动「能量突击」

- 触发：立牌主动键（BaseSignItem 技能链：ACTIVATE_SIGN_KEY → SignActivatePayload → performSkillForCurio → `performSkill`），**不走右键**。
- 效果：
  1. 立即获得 1 张强化拒止（同 §4.1 发卡口径）。
  2. 施加 buff：新 `MobEffect` `lingling_surge`（`effect/LinglingSurgeEffect`），时长 **1200t（1:00）**，无原版属性修饰符（属性由 tick 刷新）。
- 实时数值（用户明确「实时变化」）：
  - 攻击力：`Attributes.ATTACK_DAMAGE` **ADD_VALUE** `+effectiveCharge`（先例 `event/EventEffects.java:57`、`item/NetherStarDiceItem.java:82`）。
  - 移动速度：`Attributes.MOVEMENT_SPEED` **ADD_MULTIPLIED_TOTAL**（1.21.1）/ **MULTIPLY_TOTAL**（1.20.1），`+effectiveCharge / 100.0`（5 层 = +5%）。
  - 每 10～20t 重算并更新同一 modifier（固定 id：1.21.1 `ResourceLocation`，1.20.1 `UUID`）；buff 结束/移除立牌时**必须移除** modifier（防止残留）。
- 冷却：**沿用全局立牌主动冷却**——基准 = `GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS`（配置项 `SIGN_ACTIVE_COOLDOWN_SECONDS`，默认 **180 秒 / 3600t**，见 starengine_lib 同名常量），再走 `ChargeManager.signCooldownTicks(player, …)` 既有减免链（诡异骰子 −50% / 充能 −20%）。本立牌**不覆写基准**（与 moses 的自定 120 秒基准不同）。时长 1200t 与冷却是互不影响的两层。（2026-10-18 实机实测 `cd=3600` / `dur=1200`；原稿误写 120 秒，已按实测订正。）
- 允许在 buff 期间再次触发（覆盖时长与数值，不叠加 modifier）。

## 6. 资源与数据清单（两线各一份）

1. `item/ModItems` 注册 `LINGLING_SIGN`（`Item.Properties` 模板同 KANGEL_SIGN）。
2. `init/ModCreativeTabs` dice 页。
3. `datagen/ModItemModelProvider` `basicItem`。
4. 贴图 `assets/astral_dice/textures/item/lingling_sign.png`（从 `images/怪力乱神立牌.png` 复制）+ 生成的 model json。
5. lang 三语（`zh_cn` / `en_us` / `ja_jp`？按 `tools/check_lang_sync.ps1` 现有的语言集合为准）：`item.astral_dice.lingling_sign`、`tooltip.astral_dice.sign.lingling_active`、`_passive`、`_note`、`astral_dice.guide.entry.lingling.N`、效果名/描述 `effect.astral_dice.lingling_surge` + `effect.astral_dice.lingling_surge.description`；字面 `%` 写 `%%`。
6. tooltip：`event/ModTooltipHandler` 新增 lingling 段（`addSignActiveTitle`（GOLD）/ `addSignPassiveTitle` / `addSignNoteLines`）；染色按 `scripts/audit/tooltip_color_audit.ps1` 规则（时间值蓝 §9、其他数值黄 §e）。
7. 标签：`data/curios/tags/item/stand.json`、`data/astral_dice/tags/item/signs.json`。
8. 赏金池：`data/bountiful/bounty_pools/astral_rews.json`（参照 `diamond_dice` :33-42，unitWorth 7500）。
9. 帕秋莉手册：`astral_dice.guide.entry.lingling.N` 页（键必须被 `tools/audit_patchouli_keys.py` 认可）。
10. 效果图标：32×32 + 既有生成器；`tools/verify_effect_icons.py` 四线必须通过；AGENTS 效果清单计数**实测后订正**（不得在旧数字上 +1）。

## 7. 创造栏解绑（用户 2026-10-18 裁决：可合成 + 创造栏始终可见）

- 现状：4 材料配方本就无条件（可合成已成立）；`init/ModCreativeTabs.java:39-40` 用 `if (ModCommonConfig.ENABLE_CHIP_RECIPES.get())` 包住它们的创造栏注册。
- 改动：把这 4 个物品（`regeneration_reagent` / `conductive_wire` / `star_coin_dust` / `mark_paint`）**移出**该闸门，始终可见；**其余筹码相关条目保持原闸门不变**；注释同步改写。
- 连带：更新 `scripts/test/cases/CHIP-ECON-OFF-1.21.1.json` + `CHIP-ECON-OFF-1.20.1.json` 里「创造栏隐藏材料」的期望与标题/说明，以及 `CHIP-ECON-ON-*` 里「4 种材料可见」的表述；`scripts/test/resources/kubejs/**` 或 `scripts/**/astral_shop_client.js` 中依赖该行为的探针若存在，同步订正。

## 8. 守门（实现后必须全绿）

```
pwsh -NoProfile -File scripts/verify/verify_resource_integrity.ps1
pwsh -NoProfile -File tools/check_lang_sync.ps1
pwsh -NoProfile -File tools/audit_patchouli_keys.py
pwsh -NoProfile -File tools/verify_rarity_consistency.py
pwsh -NoProfile -File scripts/verify/verify_bountiful_pools.ps1
pwsh -NoProfile -File scripts/audit/tooltip_color_audit.ps1
pwsh -NoProfile -File scripts/verify/verify_crafting_recipe_uniqueness.ps1
pwsh -NoProfile -File scripts/verify/verify_event_registry.ps1
```
外加 `./gradlew :neoforge-1.21.1:classes`（及 forge 线）编译通过；`verify_effect_icons.py` 四线通过。

> 状态（2026-10-18）：`forge-1.20.1` 移植完成 —— `lingling_surge` 已落地（效果数 1.20.1 = 61，两 P0 线 id 集合一致）。守门实测（本机）：`verify_resource_integrity.ps1` PASS、`check_lang_sync.ps1` OK、`verify_effect_icons.py` PASS（forge 61/61）、`verify_rarity_consistency.py` PASS、`audit_patchouli_keys.py` 悬空 0、`verify_crafting_recipe_uniqueness.ps1` collisions=0、`verify_event_registry.ps1` OK。两项**既有**偏差与本立牌无关、不在本批范围（均已实测对照 HEAD 基线）：① `verify_bountiful_pools.ps1` 6 项致命偏差（HEAD 基线 7 项，本批消灭了 2 条 lingling 相关项）—— 余项全部来自 1.20.1 独有的 `card_shop`/`chip_shop`/`event_drawer` 与 P1 线 26.1.2 尚未移植的 10 个条目（含 `ame_sign`/`kangel_sign`）；P0 两线的 `astral_rews.json` 已逐字节一致（md5 `c86d449c…`）。② `audit/tooltip_color_audit.ps1` 64 处违规（HEAD 基线同为 64，且无一条命中 lingling 键）。**本规格无「forge 待移植」遗留待办**。

## 9. 不做 / 待用户

- **实机测试**：按 AGENTS 第 5 条，未经用户明确下达不得启动 dev 客户端；交付报告登记未实机项。
- 渲染相关（强化拒止吸附视觉）不属于本立牌范围。

## 10. 变更记录

- **2026-10-18 晚（tooltip 文案）**：用户原话「将怪力乱神立牌和强化拒止tooltip中分号改为换行；怪力乱神tooltip注释部分移除充能上限描述（这是充能体系的全局规则，没必要重复描述）」+「路障和强化拒止耐久度描述向下移（放在描述最后，在出牌数上面）」。落地（三语 × P0 两线共 6 个 lang 文件、键集不变 1001 键）：`tooltip.astral_dice.sign.lingling_active` 拆 2 行、`_passive` 拆 3 行、`_note` 拆 2 行且删除「充能上限为 §e20§7 层；」（合计上限 20 是本立牌**独立加成层**的规则，记在本文件 §4.6 与 `GameplayConstants.CHARGE_MAX_STACKS`，tooltip 不再重复）。判据：`tools/check_lang_sync.ps1` OK、`scripts/audit/tooltip_color_audit.ps1` 64 处（与基线同数、无一条命中 lingling 键）。
