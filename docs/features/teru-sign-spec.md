# 教主立牌（teru）/ 降神 · 狐光 —— 接口与落点冻结规格

> 批次：2026-09-27（单批实现，发布线双版本 `neoforge-1.21.1` + `forge-1.20.1`；`neoforge-26.1.2` **零改动**，待迁移项见 §7）。
> 用户口径（原文要点）：新立牌「教主立牌」（命名 `teru`，传奇）；主动「降神」，被动「狐光」。
> **2026-09-27 补充指令**：狐光增层必须**防刷** —— 阻止「丢弃后捡起」与「反复插入/卸除战斗牌」。

---

## 1. 冻结 id 与数值表（唯一命名来源）

| 类别 | 冻结值 | 落点 |
|---|---|---|
| 立牌物品 | `astral_dice:teru_sign`（传奇 = `Rarity.UNCOMMON`，本模组「金=传奇」映射） | `item/ModItems.java` |
| 立牌类 | `item/sign/TeruSignItem.java`（`extends BaseSignItem`） | 新增 |
| 主动动作 id | `teru_descent`（`TargetSelectionRegistry` 键；`msg.astral_dice.target_select.skill.teru_descent` 的键尾） | `TeruSignItem` 静态块 |
| 降神效果 | `astral_dice:teru_descent`（常驻时长；施加于**被指定目标**；图标 = 教主立牌图） | `effect/TeruDescentEffect.java` |
| 狐光效果 | `astral_dice:teru_huguang`（层数镜像 = `amplifier+1`；图标 = 狐光图） | `effect/HuguangEffect.java` |
| 立牌贴图 | `textures/item/teru_sign.png`（← `images/教主立牌.png`，sha256 `A127EB4E…`） | 两线各一份 |
| 降神贴图 | `textures/mob_effect/teru_descent.png`（同图第二份副本） | 两线各一份 |
| 狐光贴图 | `textures/mob_effect/teru_huguang.png`（← `images/狐光.png`，sha256 `742009B9…`） | 两线各一份 |
| 玩家可见名 | 中「教主立牌」；英 `Cult Leader Sign`（沿用「称号 + Sign」风格） | lang |
| 手册条目 | `entries/signs/teru_sign.json`，`sortnum: 18`（接 `zhao_sign` = 17） | 仅 `en_us` |

数值（全部冻结）：

| 项 | 值 |
|---|---|
| 施法者攻击加成 | `⌊目标攻击力 × 0.5⌋`（施法瞬间快照；经镜像缓存计入骰战攻击力） |
| 施法者防御加成 | `⌊目标防御力 × 0.5⌋`（经 `setDefenseArmorBonus` 折算真实护甲：1 防御 = 2 护甲） |
| 狐光攻击基数 `B` | **施加时的施法者攻击力（基础攻击力）+ `⌊目标攻击力 × 0.5⌋`** ＝ 施法者「获得目标 50% 加成后」的快照攻击力（施法瞬间快照；后续攻击力成长**不计入**）。等价写法 `⌊施法前施法者攻击力⌋ + ⌊目标攻击力 × 0.5⌋`。⚠️ **2026-09-21 用户口径（权威）**：追加值 = 施加者的快照攻击力（施加时的基础攻击力 + 从降神目标身上获得的 50% 攻击力）+ 施加者自身狐光层数（**按消耗后计算**） |
| 额外攻击（目标攻击新目标） | 消耗 `1` 层狐光 ⇒ 攻击力加算 `B + 消耗后剩余层数`（受目标防御力抵扣；参与全力攻击等既有倍率） |
| 狐光层数 | `0..20`（上限 20，超出丢弃） |
| 主动施放增层 | `+3` |
| 合成 / 获得 / 装备攻击牌 | `+1` 层/张（装备按 §4 守卫 ② 去重） |
| 降神结束锚点 | **被指定目标自己的下一次骰神赐福结束**（下降沿；施法时目标已在赐福 ⇒ `skip=1` 跳过当前这一次） |
| 重复施放 | 拒绝（只提示、不开选择会话、不进冷却、不发牌、不充能） |
| 目标选择范围 | `TargetType.PLAYER` + `GameplayConstants.TARGET_SELECT_RADIUS`（16 格，配置上限 32）；**不**实现 `SelfTargetable` ⇒ 只能选其它玩家，不含生物与自身 |
| 攻击牌定义 | `CardRegistry.itemToType(stack) != null && !CardRegistry.isDefense(type)`（= `astral_dice:combat_cards` 标签中非防御的 7 张：中/大/特大/暗影突袭/名刀/蓄力/全力攻击） |

---

## 2. 注册点清单

### 2.1 1.21.1（`neoforge-1.21.1`）

| 项 | 落点 |
|---|---|
| 物品 | `item/ModItems.java`：`TERU_SIGN`（接 `ZHAO_SIGN` 之后），import `TeruSignItem` |
| 效果 | `effect/ModEffects.java`：`teru_descent` / `teru_huguang` 两条 `EFFECTS.register`（`ALL = EFFECTS.getEntries()` 活视图自动纳入 `/astralparty cleareffect`） |
| 附件（11 键） | `component/ModAttachments.java` 末尾（见 §2.3） |
| 攻击修饰器 | `combat/DiceCombatModifiers.java` 的 `static { … }` 注册块：两条 `registerAttackModifier`（施法者加成 / 目标额外攻击） |
| 快照口径 | `DiceCombatModifiers#attackPowerOf(Player)` / `#defensePowerOf(Player)`（**新增**），与 `getDisplayAttackRange` / `getDisplayDefenseRange` 的基础项**同源**（都由新抽出的 `attackPowerBase` / `defensePowerBase` 计算，数值逐字等价） |
| 每 tick | `event/PlayerTickEvents.java`：`TeruSignItem.tick(player)`，置于既有 `tickCount % 20` 早退**之前**（照 `ZhaoSignItem.tickBlessing` 的位置） |
| 生命周期 | `event/PlayerLifecycleHandler.java`：死亡段 `onOwnerDeathCleanup`、登录段 `onOwnerRelogin`；登出由 `TeruSignItem` 自身的 `@SubscribeEvent PlayerLoggedOutEvent` 处理 |
| 发牌漏斗 | `item/chip/VitaminPillChipItem#giveCard`：成功入包分支追加 `TeruSignItem.onAttackCardCount(player, amount)`（**必须在 `add(...)` 之前取数与判定**：add 会清空传入栈） |
| 装备判定 | `screen/CardInventoryMenu#saveToDice` 末尾：把本次写入的**攻击牌「类型 → 张数」**交给 `TeruSignItem.onAttackCardsEquipped` |
| 门控前置拒绝 | `item/sign/BaseSignItem`：新增 `protected boolean canBeginSelectorSession(Player)`（缺省 `true`），在门控分支调用 `TargetSelectionManager.start(...)` **之前**判定 |
| tooltip | `event/ModTooltipHandler.java`：`TERU_SIGN` 块（key hint → 主动标题「降神」+ `teru_active` → 被动标题「狐光」+ `teru_passive` → `teru_layers` 计数 → 冷却行） |
| 资源 | `data/astral_dice/tags/item/signs.json`、`data/curios/tags/item/stand.json` 追加 `astral_dice:teru_sign`；**不进** `astral_rews.json`（传奇品质不入赏金池，同 zhao 裁决） |
| 创造栏 / 模型 / 配方 | `init/ModCreativeTabs.java`（传奇段）、`datagen/ModItemModelProvider.java`（`basicItem`）、`datagen/ModRecipeProvider.java`（`GCG/RER/ZPZ`，材料 = 金锭/指南针/空白立牌/红石块/钻石骰子/黄金星盘，`unlockedBy("has_blank_sign")`）→ 产物 `src/generated/resources/{assets/astral_dice/models/item,data/astral_dice/recipe}/teru_sign.json` |
| 手册 | `assets/astral_dice/patchouli_books/astral_guide/en_us/entries/signs/teru_sign.json` |
| 语言键（15 个） | `assets/astral_dice/lang/{zh_cn,en_us}.json`：`item.astral_dice.teru_sign`、`effect.astral_dice.teru_descent[.description]`、`effect.astral_dice.teru_huguang[.description]`、`msg.astral_dice.teru_ready`、`msg.astral_dice.teru_descent_applied`、`msg.astral_dice.teru_descent_in_effect`、`msg.astral_dice.target_select.skill.teru_descent`、`tooltip.astral_dice.sign.teru_active/_passive/_layers`、`astral_dice.guide.entry.teru_sign.1..3` |

### 2.2 1.20.1（`forge-1.20.1`）平台差异（**同语义、平台写法**）

| 项 | 1.21.1 写法 | 1.20.1 写法 |
|---|---|---|
| 物品类型 | `DeferredItem<Item>` / `registerItem(name, supplier)` | `RegistryObject<Item>`（同名 `registerItem`） |
| 附件 | `DeferredHolder<AttachmentType<?>, AttachmentType<T>>` + `ATTACHMENTS.register(name, () -> AttachmentType.builder(...).build())` | `AttachedDataKey<T>` + `register(AttachedDataKey.builder(name, Codec, () -> 默认).build())`；`Optional<UUID>` 用 `UUIDUtil.CODEC.optionalFieldOf("id").codec()` |
| 死亡保留 | `AttachmentType.Builder#copyOnDeath()`（`teru_huguang_layers` / `teru_equip_watermark`） | 加入 `component/AstralData#onPlayerClone` 死亡分支白名单（现有 3 项 + 本批 2 项） |
| 效果取用 | `ModEffects.X`（`Holder`） | `ModEffects.X.get()` |
| Curios | `CuriosApi.getCuriosInventory` | `CuriosCompat.getCuriosInventory` |
| 网络 | `PacketDistributor.sendToPlayer(player, new ActionBarPayload(...))` | `ModNetwork.sendToPlayer(player, new ModNetwork.ActionBarMessage(...))` |
| 事件总线注解 | `@EventBusSubscriber(modid = …)`（NeoForge） | `@Mod.EventBusSubscriber(modid = …)`（Forge） |
| 物品组件读法 | `stack.getOrDefault(KEY.get(), 默认)` | `KEY.getOrDefault(stack, 默认)` / `KEY.set(stack, 值)` |
| 标签目录 | `data/<ns>/tags/**item**/` | `data/<ns>/tags/**items**/` |
| 每 tick 语义 | `PlayerTickEvent.Post` 每 tick 一次 | Forge 每 tick 派发 START + END **两次** ⇒ `tick` 必须幂等（§3.4） |

### 2.3 新增附件键（两线同名同型）

| 键 | 类型 | 位置 | 语义 | copyOnDeath |
|---|---|---|---|---|
| `teru_descent_caster` | `Optional<UUID>` | 目标 | 降神生效的**唯一真值** | ❌ |
| `teru_descent_atk_bonus` | `Integer` | 目标 | `⌊目标攻击力 × 0.5⌋` | ❌ |
| `teru_descent_def_bonus` | `Integer` | 目标 | `⌊目标防御力 × 0.5⌋` | ❌ |
| `teru_descent_attack_base` | `Integer` | 目标 | 狐光攻击基数 `B` | ❌ |
| `teru_descent_skip_cycles` | `Integer` | 目标 | 0/1（照 `zhao_blessing_skip_cycles`） | ❌ |
| `teru_prev_blessing` | `Boolean` | 目标 | 下降沿基准（照 `zhao_prev_blessing`） | ❌ |
| `teru_descent_new_targets` | `String` | 目标 | 本次降神已攻击目标 UUID 串（逗号分隔） | ❌ |
| `teru_huguang_layers` | `Integer` | 施法者 | 狐光层数 0..20 | ✅ |
| `teru_descent_target` | `Optional<UUID>` | 施法者 | 当前目标指针（可自愈重建） | ❌ |
| `teru_atk_bonus_cache` | `Integer` | 施法者 | 攻击加成镜像缓存（每 tick 派生） | ❌ |
| `teru_equip_watermark` | `String` | 行为者 | 装备计层防刷水位 `类型=历史最大同时装备张数;…` | ✅ |

全部键**不 `.sync()`**（仅服务端读写；可见载体是效果实例，客户端由原生效果同步）——与 `zhao_*` 五键同口径。
⇒ 1.21.1 侧 `.copyOnDeath()` 键由 3 个增至 **5 个**；1.20.1 侧克隆白名单同步增至 5 项。

---

## 3. 时序与状态机

### 3.1 施法（`TeruDescentAction#apply` → `TeruSignItem#castDescent`）
目标校验（必须是**其它玩家**）→ 生效中重复施放拒绝（服务端二次校验）→ 快照 `A_c / A_t / D_t`（`attackPowerOf` / `defensePowerOf`）→
写目标 7 键（`skip = 目标已在骰神赐福 ? 1 : 0`；`prev` 同值；`newTargets` 清空）→ 施加 `teru_descent` 效果 → 施法者侧写指针 + 镜像缓存 + 护甲折算 → `+3` 层狐光 →
玩家级冷却（`WeirdDiceHandler.signCooldownTicks`）+ 电流核心充能 +1 → actionbar `msg.astral_dice.teru_descent_applied`。

### 3.2 结束（唯一收敛点 `endDescent(target)`）
清目标 7 键 + 移除效果实例；施法者在线 ⇒ 镜像缓存归 0 + 护甲修饰器移除 + 指针清空。狐光层数与装备水位**不动**。

### 3.3 结束路径（四类边界，全部收敛到 `endDescent`）

| 触发 | 机制 |
|---|---|
| 目标骰神赐福下降沿 | `tickTargetSide`：`prev && !hasDice` ⇒ `skip>0` 则 `skip-1`，否则 `endDescent` |
| 效果被外力移除（`/effect clear` 等） | `tickTargetSide` 自检：真值仍在而效果实例没了 ⇒ `endDescent` |
| 目标死亡 / 登出 / 重登 | `PlayerLifecycleHandler`（死亡、登录段）+ `PlayerLoggedOutEvent` 订阅 ⇒ `endDescent` |
| 施法者死亡 / 登出 | **不**结束目标身上的降神；施法者侧派生值清零（死亡）或随 tick 自然消失（登出），回来后由 `resolveTarget` 自愈重建 |

### 3.4 每 tick（`PlayerTickEvents`，`tickCount % 20` 早退之前）
- 施法者侧 `tickCasterSide`：`resolveTarget` 解析链接（指针失效 ⇒ **扫描在线玩家**自愈重建），有效 ⇒ 镜像攻击加成 + `setDefenseArmorBonus`；无效 ⇒ 两者归零。
- 目标侧 `tickTargetSide`：下降沿状态机 + 效果自检/续期 + 末行写 `prev`。
- 双方：`HuguangEffect.mirror(player, layers)`。
- **幂等要求**（1.20.1 每 tick 两次派发）：下降沿在第一次调用即落 `prev=false`，第二次不会再消费 `skip`；其余分支都是"同值不写"。
- ⚠️ 链接解析必须用 `getServer().getPlayerList().getPlayer(uuid)`（**跨维度**），**不得**用 `level.getPlayerByUUID`（只查本维度，跨维度会误判为"链接失效"而错误清零）。

---

## 4. 狐光增层与**两条防刷守卫**（本轮重点）

### 4.1 守卫 ①：拾取不计层（封堵「丢弃 → 捡起」）
`TeruSignItem` **刻意不订阅任何拾取事件**（1.21.1 `ItemEntityPickupEvent` / 1.20.1 `EntityItemPickupEvent`、`PlayerEvent.ItemPickupEvent` 一律不挂；源码中不出现这些 import 即为"未挂"）。
口径依据（仓库既有先例）：`item/chip/VitaminPillChipItem` 的类注释「拾取地面卡牌不会触发，避免反复丢弃/拾取刷治愈点」；`item/sign/MimiSignItem` 亦注明「不包含拾取/奖励/复制」。
⇒ 丢弃后重新捡起、地面掉落物、他人抛来的卡牌**一律不计层**（**已知边界**，如需覆盖"他人赠予"须另立机制）。

### 4.2 守卫 ②：装备计层按「历史同时装备张数水位」去重（封堵「反复插入/卸除」）
触发点 = `screen/CardInventoryMenu#saveToDice`（每次关闭卡牌栏都会执行）：
1. 统计本次写入的**攻击牌「类型 → 张数」**（非防御类型）；
2. 读该玩家的 `teru_equip_watermark`，`delta = Σ_type max(0, now[type] − watermark[type])`；
3. `delta > 0` ⇒ 计 `delta` 层；随后把水位**抬到** `max(watermark, now)` 并写回（**只升不降**）。

效果：同一批战斗牌「插入 → 卸除 → 再插入」**一层都刷不到**；只有"同时装备张数超过历史最大值"才继续计层，而这必须真的多持有/多合成卡牌。

**为什么必须落在玩家侧、而不是卡牌物品上的标记（关键证据）**：
`saveToDice` 只把卡牌写成 `AppliedStone(type, uses)` 并**销毁物品栈**；再次打开卡牌栏由 `loadFromDice` 经 `CardRegistry.typeToItem` **重建全新 `ItemStack`**（`stoneToItem`）。
⇒ 任何打在卡牌上的"已计层"标记都会被这一轮「销毁 + 重建」抹掉 ⇒ **装备去重只能靠玩家侧持久水位**。
水位若在死亡时归 0，玩家就能靠「死一次 → 再装备一次」重新领取同装备张数的一份 ⇒ 故与层数同为**跨死亡保留**键。

### 4.3 三个计层触发点（全部事件式、不可循环）
| # | 触发 | 挂点 | 说明 |
|---|---|---|---|
| 1 | 合成攻击牌 | `TeruSignItem#onItemCrafted`（`PlayerEvent.ItemCraftedEvent`，与维生素药丸/看板娘同一事件机制） | 升级合成（2 中 → 1 特大）也按一次合成计 `+1` |
| 2 | 获得攻击牌（模组发牌） | `VitaminPillChipItem#giveCard` 成功入包分支 | 全仓卡牌发放漏斗（18 个调用点：`RandomCardHandler`、各立牌、筹码、事件系统、充能退回等）；**掉落不计**（背包满时走 drop 分支） |
| 3 | 装备攻击牌 | `CardInventoryMenu#saveToDice` → `onAttackCardsEquipped` | 受守卫 ② 去重 |

「行为者 → 持有者」映射：行为者佩戴教主立牌 ⇒ 本人 `+N`；行为者是某人的降神目标 ⇒ 该**施法者** `+N`（两者可同时成立，各自独立结算）。
施法者离线时无法写离线玩家数据 ⇒ **不计层、不加成、不消耗**（已知边界）。

### 4.4 已知余量（本批不封堵，登记备查）
带自身冷却/代价的机制反复「返还/奖励」卡牌（看板娘主动回收返还、卫星/魔法书等筹码发牌）仍按张计层 —— 各路径均有冷却或消耗，且总层数受 `20` 上限与「同装备水位」双重约束，不构成无成本循环。

---

## 5. 死亡 / 重登 / 登出 / 卸牌 汇总

| 事件 | 目标侧 7 键 + 降神效果 | `teru_huguang_layers` | `teru_equip_watermark` | 施法者侧派生值 |
|---|---|---|---|---|
| 目标死亡 / 登出 / 重登 | 全部清除（`endDescent`） | 保留 | 保留 | 施法者在线则同 tick 清零 |
| 施法者死亡 | 目标侧**不受影响** | 保留（copyOnDeath / 克隆白名单） | 保留 | 旧实体清零；重生后由 tick 重建 |
| 施法者登出 | 目标侧**不受影响** | 保留（NBT） | 保留 | 离线即自然失效；重登后由 tick 重建 |
| 卸下教主立牌 | **不清**（真值不在立牌物品上） | **不清** | **不清** | — |

依据：`zhao` 批「效果属于目标、不属于立牌」的口径（同 `ren` 护盾「卸下不清除已有护盾」、`rin`/`komachi` 累计值）。

---

## 6. 「不影响」举证

| 改动入口 | 定义点 | 消费点核对 | 结论 |
|---|---|---|---|
| `BaseSignItem#canBeginSelectorSession`（新增） | 1.21.1 / 1.20.1 `BaseSignItem` | 仅门控分支 1 处调用；缺省 `true` ⇒ 其余 18 个立牌（含 zhao/ren 等选择器类）行为逐字不变 | 无行为变更 |
| `DiceCombatModifiers#attackPowerOf/defensePowerOf`（新增） | 同上 | 仅 `TeruSignItem` 施法快照调用；`getDisplayAttackRange`/`getDisplayDefenseRange` 改为调用抽出的同名基础项方法（数值逐字等价，仅去重） | 显示数值不变 |
| 两条新增 `registerAttackModifier` | `static {}` 注册块 | 无降神状态时：第一条读缓存恒 0；第二条在"非降神目标"时立即返回 0 | 无状态时为恒等变换 |
| `VitaminPillChipItem#giveCard` 追加一行 | 同上 | 仅"是攻击牌且成功入包"时才调用；非攻击牌 / 掉落分支零影响 | 既有发牌行为不变 |
| `CardInventoryMenu#saveToDice` 追加一段 | 同上 | 只读 `stones` 并写 `teru_equip_watermark`（无变化时不写）；不修改 `WeaponEnhancement` 的写入内容 | 卡牌栏保存语义不变 |
| 拾取事件 | — | **本批零订阅**（`grep` 可证） | 拾取路径零影响 |
| `consumeHuguangForNewTarget` 的**自目标防护** | `TeruSignItem` | 显示/快照路径（`getDisplayAttackRange` 与 `attackPowerOf`）都以 `ctx.target == attacker` 复用同一套攻击修饰器链 ⇒ **必须**保留 `if (victim == attacker) return 0`，否则"打开卡牌栏看攻击力"或"施法瞬间快照"会**误消耗 1 层狐光**并把施法者自己记进已攻击目标集；真实骰战链路 `target == player` 直接 return，故实战零影响 | 必须保留 |

---

## 7. `neoforge-26.1.2` 待迁移项登记（本批**零改动**）

| # | 待迁移项 | 备注 |
|---|---|---|
| 1 | 物品/立牌类/两个效果类/11 个附件键 | 26.1.2 走 NeoForge `AttachmentType`（同 1.21.1 形态） |
| 2 | 攻击修饰器 + `attackPowerOf/defensePowerOf` | 该线 `DiceCombatModifiers` 同构，但需按该线 `CurioApi`/组件 API 复核 |
| 3 | 门控钩子 `canBeginSelectorSession` | 该线 `BaseSignItem` 门控分支需同步（该线立牌类数量与 1.21.1 不同） |
| 4 | 两段式数据生成（**必经**） | `:neoforge-26.1.2:runClientData` + `:runServerData`，双输出根 `src/generated/{resources,clientResources}`；`assets/<ns>/items/<id>.json` 硬需求 |
| 5 | 语言键 15 个 | 该线 lang 另有一套；迁移后三线各自 zh↔en 一致（`tools/check_lang_sync.ps1`） |
| 6 | 手册条目 + 标签 | 注意该线**缺 zhao 批次**（`entries/signs` 17 vs 18）⇒ 迁移顺序必须「先补 zhao、再补 teru」 |
| 7 | 不得回移项 | 该线专有差异（如 `ItemTags.SPEARS` 近战判定）不得反向回移 |

---

## 8. 验收自检

| 验收条 | 证据/判据 |
|---|---|
| 双线构建 | `scripts/test/mt_build.ps1 --version 1.21.1` / `--version 1.20.1` ⇒ `MT_BUILD: OK` |
| 资源落盘 | `src/generated/resources/.../teru_sign.json`（模型 + 配方 + 配方解锁 advancement）存在且时间戳更新 |
| 产物内容 | `jar tf` 含 `TeruSignItem` / `TeruDescentEffect` / `HuguangEffect` / 三张贴图 / 手册条目 / 模型 / 配方 |
| 语言同步 | `tools/check_lang_sync.ps1` 退出 0（三线各自「key 完全一致」，含新增 15 键） |
| **防刷守卫 ①** | `TeruSignItem` 源码内**不出现**任何拾取事件订阅（grep 命中数 = 0） |
| **防刷守卫 ②** | `saveToDice` 的水位写入只在 `delta > 0` 时发生；`teru_equip_watermark` 为 copyOnDeath（1.21.1 `.copyOnDeath()` 计数 = 5）/ 已在 `AstralData#onPlayerClone` 白名单（1.20.1 = 5 项） |
| 生产物洁净 | 两线 `build/libs/*.jar` 内 `.cache` 条目 = 0（本批顺带修复了 `-next` 两线的 `exclude` 写法） |
| 游戏内行为 | ✅ **2026-09-20 完成（双侧全绿）**：`TERU-SIGN-<V>`（91 断言）/`TERU-EXTRA-ATTACK-<V>`（53）/`TERU-HUGUANG-ANTIFARM-<V>`（83）/选择器门控回归 `LULU-SIGN-<V>`（43），1.21.1 与 1.20.1 **各 4/4 PASS**；读数逐字段一致（跨线仅 eid/tick 不同）。详见 §9 |

---

## 9. 游戏内验证记录（2026-09-20，发布线双版本）

### 9.1 用例与命令

```
pwsh -File scripts/test/mt.ps1 --version 1.21.1 --phase build          # 或 env / launch
pwsh -File scripts/test/mt.ps1 --version 1.21.1 --phase cases --case cases/TERU-SIGN-1.21.1.json
# 另三条：TERU-EXTRA-ATTACK-1.21.1 / TERU-HUGUANG-ANTIFARM-1.21.1 / LULU-SIGN-1.21.1（1.20.1 同名换版本号）
```

| 用例 | 断言 | 1.21.1 | 1.20.1 |
|---|---|---|---|
| `TERU-SIGN-<V>` | 91 | PASS | PASS |
| `TERU-EXTRA-ATTACK-<V>` | 53 | PASS | PASS |
| `TERU-HUGUANG-ANTIFARM-<V>` | 83 | PASS | PASS |
| `LULU-SIGN-<V>`（`canBeginSelectorSession` 回归） | 43 | PASS | PASS |

### 9.2 关键判据（两侧读数逐字段一致）

| 条款 | 读数 |
|---|---|
| §3.1 快照 50% / 攻击基数 / +3 层 | `AP_F_CASTFAKE:cast=1:A_t=20:D_t=12:A_c=6:bonus_atk=10:bonus_def=6:base=16:exp_*同值:atk_ok=1:def_ok=1:base_ok=1:layers=3:layers_ok=1:cache=10:cache_ok=1:armor=0>12:armor_ok=1:skip=0:prev=0:fx_on_fake=1:caster_on_fake=1:newt=0` |
| §3.1 只能选其它玩家 | `AP_C_CAST:reg=teru_descent:reg_type=PLAYER:self_match=0:mob_match=0:self_cast=0:mob_cast=0:session=1:session2=1:…:cd=0` + debug `action=teru_descent … allowSelf=false`、`confirm FAIL: target_type_mismatch ×2` |
| §2 注册与冻结值 | `AP_REG_REG:item=astral_dice:teru_sign:desc=item.astral_dice.teru_sign:rarity=UNCOMMON:stack=1:signs_tag=1:curios_stand_tag=1:fx_d=effect.astral_dice.teru_descent:fx_h=effect.astral_dice.teru_huguang:action=teru_descent:max_layers=20:on_active=3:ratio=0.5` |
| 生效中拒绝重复施放 | `AP_GA_GATE:session=0:token_seen=0:recast_other=0:layers=7>7:layers_ok=1:fake_effect=0:cd=0`（同时 `AP_LK1_LINK` 仍 `caster=1:cache=10:armor=12`） |
| §4.3 自目标防护 | `AP_G_GUARD:ap=16:dp=8:dmg_range=16-16:layers=7>7:layers_ok=1:newt=0>0:newt_ok=1`（显示/快照路径零消耗） |
| §3.3 下降沿（skip 分支） | `AP_RD2_R2:skip=0:prev=0:caster=1:base=20:layers=5`（第一次结束被跳过）；`AP_RD3_R3:caster=0:base=0:layers=5`（第二次结束才真收敛） |
| §3.3 效果自检 / 死亡清场 | `AP_X_FX:removed=1:fx_d_on=0` → `AP_RD4_R4:caster=0:fx_d_on=0:layers=3`；`AP_D_DIE:called=1:caster=0:cache=0:armor=0:layers=4` |
| §3.1 逐层消耗 + 追加 | `AP_H1_HIT:layers0=5:newt0=0:h0_hp=1000>792:h0_dmg=208:h0_api=player_attack:h0_layers=5>4:newt=1`（base=200 ⇒ 追加 204） |
| 同一目标只结算一次 | `AP_H2_HIT:h0_dmg=8:h0_layers=4>4:newt=1`；换新目标 `AP_H3_HIT:h0_dmg=207:h0_layers=4>3:newt=2` |
| 层数 0 不追加 | `AP_H5_HIT:layers0=0:newt0=0:h0_dmg=3:h0_layers=0>0:newt=0` |
| §4.2 守卫②（真实卡牌栏） | `AP_E1_EQ:wm=->medium=1:layers=0>1:dl=1`（首次真的计层）→ `AP_E2_EQ:wm=medium=1>medium=1:layers=1>1:dl=0` → `AP_E3_EQ:cards_after=0:astral_dice:attack_card_mediumx1:inv=1>0:wm=medium=1>medium=1:layers=1>1:dl=0`（**插进去了却一层不加**） |
| §4.1 守卫① | `AP_DR_DROP:spawn=1:added=1:ground=1` → `AP_RB5_RB5:inv_atk=2:ground_atk=0:layers=1:wm=medium=1`（真被捡起、层数不变） |
| §4.3 发牌漏斗 + 掉落分支 | `AP_C1_CARD:is_atk=1:inv=0>2:layers=1>3:dl=2`；`AP_C2_CARD`（防御）`dl=0`；`AP_C3_CARD`（非卡牌）`dl=0`；`AP_C4_CARD:filled=36:inv=0>0:ground=1:layers=1>1:dl=0` |
| §5 跨死亡保留 | 真死 + 重生后 `AP_RB7_RD:layers=1:wm=medium=1:caster=0:target=0:fx_h_on=1`（层数与水位原样保留、派生值归零、HUD 镜像自动补回） |

### 9.3 本轮新增的 Rhino/环境坑（写探针必看）

`ServerPlayer#getUUID` / `closeContainer`、`ItemEntity#setPickupDelay`、`ItemStack#getOrDefault`（1.20.1）、`EntityType#getDescriptionId`、`Item#getClass`、`AttributeInstance#getModifier(UUID)`（1.20.1）、`Java.loadClass(X).cast(obj)` 在 KubeJS Rhino 下**均不可见**（`TypeError: Cannot find function …`）。其中两条会造成**静默失效**，必须按下述方式绕开：

1. `closeContainer` 是「`containerMenu.removed(player)`（`saveToDice` 挂点）+ 发 `ClientboundContainerClosePacket`」的唯一入口 —— 只补前半段会让客户端**停在卡牌栏界面**，之后所有注入的聊天命令被 GUI 吃掉（整条链零读数）。探针 `teruCloseMenu` 手动补齐两半并按真实 `containerId` 发包。
2. `setPickupDelay` 若与 `addFreshEntity` 写在同一个 `try` 里，异常会跳过添加 ⇒ 实体压根没进世界（读数 `spawn=1` 却永远捡不到）。

另：**不要用 `typeIdOf()`（`getKey(getType())`）判实体类型** —— `BuiltInRegistries.ENTITY_TYPE` 是 DefaultedRegistry，反查失败一律回落 `minecraft:pig`（本批实跑把蜘蛛报成猪）；可靠判据 = `mob instanceof Spider` 与命令声明的 `type_req`。修饰器读数同理：判据用**属性真值**（`p.getArmorValue()`）。死亡相位的读数要在 `/gamerule doImmediateRespawn true` 下取（否则 `/kill @s` 后的下一次注入会被当成「重生」点击）。

### 9.4 未覆盖（如实登记）

1. **「另一名真实玩家」路径**：测试世界只有一名玩家 ⇒ §3.1 的真实施法用 `FakePlayer`、§3.3 的目标侧用「自身同时充当施法者与目标」的脚手架（与 LULU 用例直接 `apply(锚点)` 同口径）；**双真人**下「左键选中另一名玩家」这条按键路径仍未实测。
2. 施法者对 FakePlayer 的「每 tick 派生值维持」不可判（FakePlayer 不在玩家列表 ⇒ `resolveTarget` 必然解析失败），该路径由脚手架相位覆盖。
3. 跨维度链接解析（`getServer().getPlayerList().getPlayer`）未做双维度实测。
4. 26.1.2 一致性测试未做（该线本批零改动、连 zhao 批次都未迁）。
