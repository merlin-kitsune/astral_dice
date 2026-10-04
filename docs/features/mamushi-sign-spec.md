# 蛟龙立牌（mamushi）· 觉醒 / 真龙形态 / 连锁反应 / 撕咬 / 龙之咆哮 —— 冻结规格（2026-09-27 用户需求 + 4 项裁决）

> 本文件是**实现与验证的唯一语义基准**（`docs/` 已 gitignore，不入库）。
> 目标：两发布线（`neoforge-1.21.1` / `forge-1.20.1`）功能对等；`neoforge-26.1.2` **本批零改动**（该线连 nardis/zhao/teru 都未迁移）。

## 0. 用户裁决（2026-09-27，本批开工前锁定）

| # | 问题 | 裁决 |
|---|---|---|
| 1 | 觉醒「单次最多 3 层」怎么计 | **按受益人数**：一次发放中每有一名**其他角色**收到牌 +1 层，单次（同一发牌事件）封顶 3；同一人拿多张（含"额外一张"）只算 1 层 |
| 2 | 多张撕咬如何叠加 | **每张装备的撕咬在触发骰神赐福时各 +1 层**（N 张 = 单次 +N 层，受 8 层上限约束）；攻击加成恒为 `min(当前觉醒, 4)` |
| 3 | 真龙形态「主动不再有范围限制」 | **同维度全体**符合条件者（友方队友；未组队时为该维度所有其他玩家），仍受 **32 人**安全上限与**距离升序**次序 |
| 4 | 撕咬加成实时还是快照 | **实时**：加成 = `min(当前觉醒层数, 4)`，赐福期间层数变化立即体现，赐福结束即清除 |

主代理另按需求原文补的**默认口径**（实现时按此做，R1 报告里如实登记，供用户复核）：

| # | 点 | 默认口径 |
|---|---|---|
| D1 | 「目标当前未持有卡牌」的判据 | 主物品栏 + 双手 + 骰子卡牌栏内**没有任何本模组卡牌**（`ModItems.isCardItem`）⇒ 额外发 1 张 |
| D2 | 撕咬/龙之咆哮的**基础攻击贡献** | 撕咬 = 定值 **+3**（对齐同费用 2 的「暗影突袭」定值 3）；龙之咆哮 = 用户给定的 **+3** |
| D3 | 减少防御 4 点的时长 | **1:00**（与同批的缓慢 III 1:00 一致）；实现为 mob effect 携带 `ARMOR -8`（1 防御 = 2 护甲） |
| D4 | 8 层是否消耗 | **不消耗、不回落**：觉醒层数继续累加，真龙形态是**锁存态**（`觉醒 ≥ 8 且佩戴`）；只有**卸下立牌**才归零 |
| D5 | 主动技能是否起「锁定(生效中)」态 | **不起**（不施加自己的计时器 ⇒ `startActiveLockOnUse` 不覆写，走默认 false） |
| D6 | 真龙形态的范围判定时机 | 在**发放开始前**判定一次（本次发放过程中因给牌涨到 8 层不改变本次目标集） |
| D7 | 两张专属战斗牌的品质 | 照既有专属牌（符卡-福/祸）用 `Rarity.UNCOMMON`（本仓「传奇」） |
| D8 | 龙之咆哮破防是否叠加 | 重复命中只**刷新时长**（不叠层；`amplifier` 恒 0） |

---

## 1. id / 常量 / 键名（冻结，不得改名）

### 物品与卡牌
| 项 | 值 |
|---|---|
| 立牌 | `astral_dice:mamushi_sign`（已注册，传奇 `Rarity.UNCOMMON`） |
| 战斗牌 1 | id `attack_card_bite`（撕咬）；`CardRegistry` typeId **`bite`**；费用 **2**；耐久 **1**；对齐「暗影突袭」的定值攻击 **+3** |
| 战斗牌 2 | id `attack_card_dragon_roar`（龙之咆哮）；typeId **`dragon_roar`**；费用 **3**；耐久 **5**；攻击贡献 **+3** |
| 贴图来源 | `images/撕咬.png`（341 B）→ `textures/item/attack_card_bite.png`；`images/龙之咆哮.png`（494 B）→ `textures/item/attack_card_dragon_roar.png`（**逐字节复制**） |

### 效果
| 效果 id | 名称 | 类型 | 时长 | 图标 |
|---|---|---|---|---|
| `mamushi_dragon` | 真龙形态 | `BENEFICIAL` | `Integer.MAX_VALUE`（常驻；tick `refresh` + 条件消失时 `remove`；**不登记 `EffectTimerGuard`**，照 teru 降神/zhao 赐福） | `textures/mob_effect/mamushi_dragon.png` = **逐字节复制立牌贴图** |
| `dragon_roar_break` | 破防（龙之咆哮） | `HARMFUL` | `ROAR_DEBUFF_TICKS = 1200`（1:00） | `textures/mob_effect/dragon_roar_break.png` = **逐字节复制** `textures/item/attack_card_dragon_roar.png` |

`dragon_roar_break` 必须用 `addAttributeModifier(Attributes.ARMOR, <ResourceLocation>, AttributeModifier.Operation.ADD_VALUE, amp -> -8.0)`（照 `effect/UnwaveringEffect.java:21-24` 的正值版取负；**1 防御 = 2 护甲 ⇒ -8 护甲 = -4 防御**）。

### 玩家附件（两线键名逐字相同）
| 键 | 类型 | 默认 | 同步 | 死亡 | 用途 |
|---|---|---|---|---|---|
| `mamushi_awakening` | `int` | 0 | **`.sync()`**（tooltip 要读） | **`.copyOnDeath()`** | 觉醒层数 |
| `mamushi_bite_bonus_active` | `boolean` | false | 不同步（纯服务端） | 不保留 | 撕咬加成锁存（触发过赐福且当时有撕咬） |
| `mamushi_forced_cooldown_until` | `long` | 0 | 不同步 | 不保留 | 强制冷却硬闸门（绝对 gameTime） |

- 1.21.1：`AttachmentType.builder(...).serialize(Codec.INT/BOOL/LONG)...`，`sync` 与 `copyOnDeath` 照 `ModAttachments.java` 既有键写法。
- 1.20.1：`AttachedDataKey`；**同步键必须登记进 `SYNCED_KEYS`**（只 `mamushi_awakening`）；**死亡保留必须把 `mamushi_awakening` 加进 `component/AstralData.java` 的 `onPlayerClone` 白名单**。
- **两线都要把 `mamushi_awakening` 加进 `component/DeathPreservedBonuses.java` 作为第 3 个槽位**（既有 2 个：`RIN_PAGES` / `KOMACHI_DAMAGE_BONUS`）—— 理由：死亡时立牌从饰品槽掉出，Curios 的 tick 轮询会先触发 `onUnequip → clearSignData` 清零，**早于**克隆复制（AGENTS.md 已记录的 2026-09-15 缺陷）；`rin_pages` 同样是「`.copyOnDeath()` + DeathPreservedBonuses」两层齐备。

### 常量（写在 `MamushiSignItem` 内，两线同名同值）
```
AWAKEN_MAX = 8                      // 真龙形态阈值
AWAKEN_GRANT_CAP_PER_EVENT = 3      // 单次发牌封顶层数
BITE_BONUS_CAP = 4                  // 撕咬攻击加成上限
DRAGON_FORM_ATTACK_BONUS = 5        // 真龙形态攻击力 +5
ACTIVE_RANGE = 12.0                 // 连锁反应范围（真龙形态取消）
ACTIVE_MAX_TARGETS = 32             // 安全上限（人数）
ACTIVE_FORCED_COOLDOWN_TICKS = 1200 // 强制 1:00，不可减免
BITE_COST = 2 / BITE_USES = 1 / BITE_ATTACK = 3
ROAR_COST = 3 / ROAR_USES = 5 / ROAR_ATTACK = 3
ROAR_DEBUFF_TICKS = 1200
ROAR_SLOW_AMPLIFIER = 2             // 缓慢 III
ROAR_ARMOR_DELTA = -8.0             // = 减 4 点防御
```

---

## 2. 技能语义（冻结）

### 2.1 被动「湖沼之王」
1. **触发源**：佩戴者**使其他角色获得卡牌**时（`giver != receiver`）。覆盖全部既有与未来发牌路径 —— 唯一漏斗 = `item/chip/VitaminPillChipItem.giveCard`。
2. **计数**：同一发牌事件内按**受益人去重**，每名新受益人 **+1 层**，单次封顶 **3 层**（裁决 1）。
   - 事件边界（冻结口径）：**同一游戏刻（同一 tick）内由同一佩戴者连续发放的多次 `giveCard` 视为一次事件**。理由：游戏内所有发牌都在同一 tick 完成（主动技、你有我有各一次调用）；多 tick 缓慢发牌的卡牌目前不存在，若将来出现按 tick 计（登记为边界）。
   - 实现：`MamushiSignItem` 内**服务端静态**小表 `Map<UUID, Batch>`，`Batch { long tick; Set<UUID> receivers; }`；每次调用先按 `gameTime` 判断是否开新批次，`receivers.add` 成功且 `size() <= 3` 时 +1 层；顺带惰性清理过旧批次。
3. **加层入口**：`MamushiSignItem.onCardGivenToOther(Player giver, Player receiver)`（由漏斗调用，见 §3.1）。
4. **转换**：任意加层后 `觉醒 >= AWAKEN_MAX` 且此前未处于真龙形态 ⇒ 立即 `transformToDragon(giver)`（§2.3）。
5. **死亡不重置、卸牌重置**（裁决 D4 + 需求原文）：见 §1 附件表与 §2.5。

### 2.2 真龙形态（锁存态）
- 判据：`MamushiSignItem.isDragonForm(Player) = isEquipped(p) && getAwakening(p) >= AWAKEN_MAX`。
- 效果（`mamushi_dragon`）：佩戴且满足判据时常驻显示（图标 = 立牌贴图）；由立牌 tick 每 tick `refresh`，不满足时 `remove`。
- 收益：
  1. **攻击力 +5**（`DiceCombatModifiers` 攻击修饰器，实时谓词）。
  2. **主动技能无范围限制**（裁决 3：同维度全体，仍 32 人上限 + 距离升序）。
  3. **牌转换**：手牌/背包/副手中的 `撕咬` → `龙之咆哮`；骰子卡牌栏中的 `撕咬` → `龙之咆哮`（费用 2→3，逐个转换，**放不下的丢弃**）；此后主动技能自身发放的牌**直接是龙之咆哮**。

### 2.3 `transformToDragon(Player)`（转换一次，幂等）
1. **背包侧**（照 `HuoCardItem.removeAll` + `FuCardItem.give` 的既有形状）：遍历主物品栏全部格子 + 副手，把每张 `attack_card_bite` 替换为**等张数**的 `attack_card_dragon_roar`，**保留 `owner_uuid` 绑定**（`ExclusiveCardUtil.setOwner` 到同一玩家）。
2. **骰子侧**：读骰子物品的 `weapon_enhancement`，遍历 `appliedStones`；对每个 `type == "bite"` 的装配项：费用从 2 变 3。按装配顺序**逐个转换**，累计 `usedCost` 超过 `maxCost` 的那些**丢弃**（从 `appliedStones` 移除；`usedCost`/`usedDefenseCost` 按 `CardRegistry` 重算，照 `TemporaryCardUtil.purgeEquipped` 的写法）；未被丢弃的写回为 `dragon_roar`。
3. **后续发放**：`dragon` 形态下主动技给自身的是 `dragon_roar`（§2.4）。
4. 转换后立即刷新 `mamushi_dragon` 效果（若尚未施加）。

### 2.4 主动「连锁反应」（非选择器类，直接生效）
```
if (level.isClientSide) return InteractionResultHolder.success(stack);
boolean dragon = isDragonForm(player);                      // D6：发放前判定一次
// ① 自身
giveExclusiveCard(player, player, dragon ? ROAR : BITE, 1); // 走专属发放形状（setOwner + 漏斗）
// ② 其他目标
List<Player> targets = collectActiveTargets(player, dragon); // 见 §3.3（有队伍只取同队 / 未组队不过滤；同维度；12 格或真龙不限；距离升序；32 上限）
for (Player t : targets) {
    RandomCardHandler.giveCardTo(player, t, CardCategory.ALL);           // 每人 1 张随机牌（giver-aware 重载）
    if (!hasAnyCard(t)) RandomCardHandler.giveCardTo(player, t, CardCategory.ALL); // D1
}
return InteractionResultHolder.success(stack);
```
- **无任何合格目标时仍然成功**（自身那张照发、照常进冷却）。
- **强制冷却 1:00，不可被任何减免绕过**（见 §3.4）。
- 目标集合为空白（例如单机无其他玩家）不视为失败。
- 「友方队友」= **有队伍时只取同队成员**（`EventTargetCollector.hasAnyTeam` + `collectTeamPlayers`），**未组队时不做队伍过滤**（取该维度在线玩家）；两者都**只取佩戴者所在维度**（库的「无队伍 ⇒ 全服在线」回退**不得**使用）+ 距离过滤（非真龙 12 格 / 真龙形态不限）+ 距离升序 + 32 上限。⚠️ **本节原写「沿用 `GiveoutScope.aroundWithTeam`」已被 R1 推翻并修正**（见 §7 第 1 条：该作用域是「范围内全部玩家 ∪ 队友（不限距离）」，会让 12 格内的非队友与超距队友也拿到牌）。

### 2.5 死亡 / 卸下
- **卸下立牌**（`clearSignData`，只在 `CurioSlotUtil.isIntentionalUnequip` 为真时到达）：`mamushi_awakening = 0`、`mamushi_bite_bonus_active = false`、`remove(mamushi_dragon)`、清静态批次表。**不清** `sign_active_cooldown_end` / `mamushi_forced_cooldown_until`（冷却为玩家级，既有口径不随装卸重置）。
- **死亡**：`mamushi_awakening` 必须保留（`.copyOnDeath()` + `DeathPreservedBonuses` 第 3 槽位 + 1.20.1 白名单）；`mamushi_dragon` 效果实例死亡会被清（允许），重生后由 tick 依据层数自动补回；**禁止**在 `PlayerLifecycleHandler` 的死亡清理里清觉醒层数。

### 2.6 撕咬（`bite`）
- 战斗牌：费用 2 / 耐久 1 / 攻击贡献 +3。
- **触发**：装备（在骰子卡牌栏内）且**触发骰神赐福**时 —— 每张装备的撕咬 **+1 层觉醒**（裁决 2），并置 `mamushi_bite_bonus_active = true`。
  - 挂点：`combat/DiceCombatEvents` 的赐福触发块（`addEffect(DICE_BLESSING)` 附近，与既有 6 个 `onBlessingStart` 同位）。
  - ⚠️ **必须锁存**：撕咬耐久 1 ⇒ 同一击稍后就会被消耗掉（`consumeAttackCardDurabilityOnce`），若不锁存，"加成需要撕咬仍在装备中"会立即失效。锁存值在**赐福结束时**清除。
- **加成**：`bite_bonus_active && 玩家仍有骰神赐福` ⇒ 攻击力 `+= min(getAwakening(p), 4)`（**实时**，裁决 4）。实现 = `DiceCombatModifiers` 攻击修饰器（实时谓词，不落地状态）。
- **7 层 + 撕咬触发赐福** ⇒ 加层后 8 层 ⇒ §2.3 立即转换（自然结果，无需特判）。
- **多张叠加**：N 张各 +1 层（单次触发；不受 §2.1 的 3 层封顶约束 —— 封顶只作用于"给他人发牌"的被动计数）。

### 2.7 龙之咆哮（`dragon_roar`）
- 战斗牌：费用 3 / 耐久 5 / 攻击贡献 **+3**。
- **命中效果**：装备龙之咆哮的玩家在骰战中命中目标时，对**被攻击的目标**施加：`缓慢 III`（`MOVEMENT_SLOWDOWN`, amplifier 2）**1:00** + `dragon_roar_break`（`ARMOR -8` = **-4 防御**）**1:00**（D3/D8：重复命中刷新时长，不叠层）。
  - 六参构造照 AGENTS 规则：`new MobEffectInstance(effect, ticks, amp, false, false, true)`（关粒子、留图标）。
  - 施加点：骰战攻击结算路径（伤害定稿/命中附近），以"本次攻击方装备了 `dragon_roar`"为条件；目标 = 该次伤害的受击者。
- **获取**：只能由处于真龙形态的 mamushi 佩戴者获得（主动技自身发放 / §2.3 转换）；**无配方、不进任何随机池、不进赏金池**。
- **专属绑定**：与符卡-福/祸同口径 —— `ExclusiveCardUtil.setOwner` 绑定获得者，`owner_uuid` 不同者**不得装备**（§3.5）。

---

## 3. 实现落点（冻结接口）

### 3.1 发牌漏斗加 giver（**唯一** 挂点）
- `item/chip/VitaminPillChipItem.giveCard(Player receiver, ItemStack card)` **保留**（委托）；新增重载
  `public static void giveCard(@Nullable Player giver, Player receiver, ItemStack card)`。
- 成功入包分支（`else`，既有 `onCardGained` / `TeruSignItem.onAttackCardCount` 同处）追加：
  `if (giver != null) MamushiSignItem.onCardGivenToOther(giver, receiver);`
  ⚠️ `amount` / `isAttackCard` 必须在 `add(...)` **之前**快照（既有注释已说明 `add` 会清空传入栈）。
- **必须改传入 giver 的两处入口**：`item/card/RandomCardHandler.giveCards/giveCardTo`（发牌者=主动技施放者）与 `item/card/YouHaveIHaveCardItem.applyEffect`（发牌者=使用者）。
- 其余 16 处直调保持两参（等价于 `giver = null`）⇒ 不误计其他立牌的给牌。

### 3.2 觉醒计数入口（`MamushiSignItem` 提供，漏斗调用）
```java
public static void onCardGivenToOther(Player giver, Player receiver);   // §2.1：去重 + 单次封顶 3 + 触发转换
public static int  getAwakening(Player p);  public static void setAwakening(Player p, int v);
public static boolean isDragonForm(Player p);
public static boolean isEquipped(Player p);                              // 照既有立牌 isEquipped（Curios stand 槽）
public static int  countEquippedType(Player p, String typeId);           // 骰子 appliedStones 计数（bite / dragon_roar）
public static boolean hasAnyCard(Player p);                              // D1 判据
public static void giveExclusiveCard(Player giver, Player receiver, boolean roar, int count); // setOwner + 漏斗（giver 透传）
public static void onDiceBlessingTriggered(Player p);                    // §2.6
public static void onDiceBlessingEnded(Player p);                        // §2.6 清锁存
public static void applyRoarDebuff(LivingEntity victim);                 // §2.7
```

### 3.3 目标收集（距离升序 + 32 上限）
- ⚠️ **R1 复核后已改为「自管集合」（本节原写「沿用 `GiveoutScope` 工厂」，已作废）**：`MamushiSignItem.collectActiveTargets(Player wearer, boolean dragon)` —— 严格同维度 → 排除自己（且 `isAlive()`，沿用旧 `RandomCardHandler.collectTargets` 口径）→ **有队伍只取同队**（库侧成员可能跨维度 ⇒ 仍按维度过滤）/ **未组队不做队伍过滤**（自己取 `ServerLevel.players()`）→ 距离 ≤ `ACTIVE_RANGE`（真龙形态不限）→ 距离升序（UUID 平局稳定）→ **排序后**截断 `ACTIVE_MAX_TARGETS`。
- `RandomCardHandler` 的 `GiveoutScope` 工厂（含 `aroundWithTeam(range,maxTargets)` / `wholeDimensionWithTeam(maxTargets)`）与 `collectTargets` **保留为通用 API，当前零调用**；`maxTargets >= 0` 的语义已由随机抽样改为距离升序取最近 N。
- **已登记边界**：① `isAlive()` 过滤（死亡未重生者不收牌，沿用既有口径）；② 若服务器把库的 `EVENT_APPLY_MC_TEAM/FTB_TEAM/OPAC` 三开关**全关**而玩家仍有队伍 ⇒ `hasAnyTeam` 为真但 `collectTeamPlayers` 返回空 ⇒ 目标集为空（默认三开关皆 true，仅在自定义配置下可达；待用户裁决是否按「未组队」处理）。

### 3.4 强制冷却（不可被任何减免绕过）
- `item/sign/BaseSignItem`：
  - 新增 `protected int forcedActiveCooldownTicks() { return 0; }`（0 = 无强制）；`performSkill` 第 ⑧ 步（冷却入账）在 `forced > 0` 时**直接使用该值**、**不经过** `WeirdDiceHandler.signCooldownTicks`，并同时写 `sign_active_max_cooldown = forced`、`sign_active_cooldown_end = now + forced`。
  - 新增 `protected long forcedCooldownUntil(Player player) { return 0L; }`；`performSkill` 第 ② 步（冷却判定）：`now < forcedCooldownUntil(player)` ⇒ **无条件视为冷却中**（早退、提示既有冷却文案），优先级**高于** `CurrentCoreChipItem.tryFinishCooldown`。
- `MamushiSignItem` 覆写两者：`forcedActiveCooldownTicks() = 1200`；`forcedCooldownUntil(p) = getForcedCooldownUntil(p)`（释放当刻写 `now + 1200`）。
- `item/chip/CurrentCoreChipItem`：`tryFinishCooldown` 内，若当前佩戴立牌是 mamushi 且 `now < forcedCooldownUntil` ⇒ **拒绝**（返回 false、不扣充能；提示走既有电流核心失败文案，如无既有键则新增 `msg.astral_dice.mamushi_cooldown_locked`）。
- **不覆写** `startActiveLockOnUse`（D5：不起锁定态）。

### 3.5 专属守门（**既有缺口，必须新写**）
- `item/card/ExclusiveCardUtil.isExclusive(ItemStack)` 目前是**硬编码列表**（LivingPage / FateGuidance / Fu / Huo）⇒ 必须加入 `attack_card_bite` / `attack_card_dragon_roar`。
- `CardItem` **没有任何 owner 守门**（无 `use()` 覆写）⇒ 战斗牌的"只能由获得者使用"落在**装备入口**：`screen/CardInventoryMenu` 的攻击牌/防御牌槽 `mayPlace`（1.21.1 `:456-471` / `:485-499`；1.20.1 `:447 :460 :475`）追加
  `if (ExclusiveCardUtil.isExclusive(stack) && !ExclusiveCardUtil.canUse(player, stack)) return false;`
  （`canUse` 无主时放行并首次绑定，与效果牌同语义。）
- 两张牌**不加入** `RandomCardHandler.attackCards()`（⇒ 天然不在随机池）；但要 `registerExclusiveCard(...)`（既有静态块 `:98-106`，`:105` 已预留「未来专属战斗牌(撕咬/龙之咆哮等)在此注册」注释）。
- 两张牌**无配方**；`data/astral_dice/tags/item(s)/is_exclusive.json` **必须加入**（否则 `verify_bountiful_pools.ps1` 会要求它们出现在 `astral_rews.json`）；**不加入** `astral_rews.json` / `astral_objs.json`。
- `data/astral_dice/tags/item(s)/combat_cards.json` 是否加入：**加入**（`attack_card_*` 属战斗牌汇总标签，供按标签检索的联动使用）。

### 3.6 骰战挂点
| 挂点 | 文件:行（1.21.1 参考） | 动作 |
|---|---|---|
| 赐福触发 | `combat/DiceCombatEvents.java:250-319`（`addEffect(DICE_BLESSING)` :256 附近） | `MamushiSignItem.onDiceBlessingTriggered(player)` |
| 赐福结束 | `DiceCombatEvents.java:819-882` 的 `onDiceBlessingExpired`（:829 附近与其他牌同位） | `MamushiSignItem.onDiceBlessingEnded(player)`；并由立牌 tick 做兜底（无赐福效果且锁存为真 ⇒ 清） |
| 攻击力修饰器 | `combat/DiceCombatModifiers.java` 静态块（内置末条约 :541） | ① `isDragonForm` ⇒ `+5`；② `bite_bonus_active && hasEffect(DICE_BLESSING)` ⇒ `+min(awakening, 4)` |
| 破防施加 | `combat/DiceCombatEvents.java` 伤害定稿附近（`:593` 一带） | 攻击方 `countEquippedType(attacker, "dragon_roar") > 0` ⇒ `applyRoarDebuff(victim)` |

### 3.7 立牌 tick 与效果维护
- 在 `MamushiSignItem` 的 `onCurioTick`（或既有玩家级 tick 挂点）内：
  - `isDragonForm(p)` ⇒ `MamushiDragonEffect.refresh(p)`；否则 `MamushiDragonEffect.remove(p)`。
  - 兜底清锁存：`!p.hasEffect(ModEffects.DICE_BLESSING) && getBiteBonusActive(p)` ⇒ `setBiteBonusActive(p, false)`。
  - 清理静态批次表中过旧条目。

---

## 4. lang 键清单（中英各 +N，**由「立牌本体」owner 统一添加**）

| 键 | zh_cn | en_us |
|---|---|---|
| `item.astral_dice.attack_card_bite` | 撕咬 | Bite |
| `item.astral_dice.attack_card_dragon_roar` | 龙之咆哮 | Dragon's Roar |
| `tooltip.astral_dice.sign.mamushi_active` | 主动「连锁反应」正文（含 12 格 / 32 人 / 强制 1:00 / 真龙形态取消范围） | 对应英文 |
| `tooltip.astral_dice.sign.mamushi_passive` | 被动「湖沼之王」正文（觉醒 / 单次 3 层 / 8 层真龙 / +5 攻击 / 牌转换 / 死亡不重置） | 对应英文 |
| `tooltip.astral_dice.sign.mamushi_awaken` | 觉醒：%s/%s（走 `addSignCounter`） | Awakening: %s/%s |
| `tooltip.astral_dice.sign.mamushi_dragon_form` | 真龙形态（金/紫色标注行） | True Dragon Form |
| `tooltip.astral_dice.card.bite` | 卡牌描述：触发赐福后 +1 层觉醒、攻击力 +当前觉醒（最多 4），持续到赐福结束；专属 | 对应英文 |
| `tooltip.astral_dice.card.dragon_roar` | 卡牌描述：攻击力 +3；命中使目标缓慢 III 1:00 并减 4 点防御 1:00；专属（真龙形态专属） | 对应英文 |
| `effect.astral_dice.mamushi_dragon` / `.description` | 真龙形态 / 描述 | True Dragon Form / … |
| `effect.astral_dice.dragon_roar_break` / `.description` | 破防（龙之咆哮）/ 描述（防御力 -4，1:00） | Armor Break (Dragon's Roar) / … |
| `msg.astral_dice.mamushi_dragon_form` | 觉醒已达 8 层：进入真龙形态！ | … |
| `msg.astral_dice.mamushi_cooldown_locked` | 连锁反应冷却中，无法被充能加速 | … |
| 手册 | `astral_dice.guide.entry.mamushi_sign.1..3`（被动 / 主动 / 真龙形态与两张专属牌）+ `attack_card_bite` / `attack_card_dragon_roar` 条目文案 | 对应英文 |

- 文案里的 `%` 必须写 `%%`（既有 teru `50%` 的既存告警教训）；时间一律 `§9`、非时间数值 `§e`（tooltip 染色守门）。
- **中英条目数必须一致**；手册条目走 `assets/.../patchouli_books/astral_guide/en_us/entries/...`（中英共用一个 en_us 目录 + lang 键，照 nardis/zhao 现状）。

---

## 5. 明确不做（本批边界）
1. 不加配方、不进任何随机池/赏金池（专属牌规则）。
2. 不改 26.1.2 任何文件。
3. 不做"真龙形态"外观渲染（只做效果图标）。
4. 不改鼠标/按键/选择器路径（该主动不是选择器类）。
5. 不新增 `astral_objs.json` 条目（两张牌是奖励类，且专属 ⇒ 不入池）。
6. 不动 `DeathPreservedBonuses` 之外的死亡清理逻辑（尤其不得在 `PlayerLifecycleHandler` 清觉醒）。

## 6. 交叉验证清单（R1 用）
1. 觉醒计数：给 1 人 / 给 5 人 / 同一人 2 张 / 自己给自己 ⇒ 期望 +1 / +3（封顶）/ +1 / +0。
2. 撕咬：0 张 ⇒ 无加成；1 张 ⇒ 触发赐福 +1 层且锁存、耐久消耗后加成仍在；3 张 ⇒ 单次 +3 层；7 层 + 撕咬 ⇒ 立即真龙。
3. 真龙形态：+5 攻击；主动范围=同维度全体；32 上限按距离取近者；背包/副手/骰子栏撕咬全部转换、溢出丢弃、绑定保留。
4. 强制冷却：释放后 60 秒内无法再放；电流核心被拒；诡异骰子/充能/命运的指引/其它立牌减免一律无效。
5. 专属守门：非获得者无法装备；`isExclusive` 覆盖两张新牌；不在随机池；赏金闸门通过。
6. 死亡/卸下：死亡后觉醒保留（`keepInventory=false` 场景由两层机制保证）；卸下归零且效果移除。

## 7. R1 独立代码验证结论（2026-09-27）与随后的修正

R1 判决：**本批引入的真实缺陷 0 条**（11/12 要点 PASS；唯一 FAIL 是既有的 tooltip 染色 12 处 teru `50%`）。R1 另提出 3 条，处理如下：

| # | R1 发现 | 主代理处置 |
|---|---|---|
| 1 | 非真龙形态的目标集合 = `GiveoutScope.aroundWithTeam(12)` = **「12 格内全部玩家 ∪ 队友（不限距离）」**，与用户原文「12 格内所有**友方队友**」不符；真龙形态下还可能并入**其它维度**的 FTB/OPAC 队友 | **修**（本批）：改为自管集合 —— 严格同维度 → 排除自己 → **有队伍只取同队**（无队伍不过滤）→ 距离 ≤ 12（真龙形态不限）→ 距离升序（UUID 平局稳定）→ 截断 32。⇒ 语义与用户原文一致，并消灭跨维度旁路 |
| 2 | 无主（`owner_uuid` 缺失）撕咬转龙之咆哮后的归属：1.21.1 **绑定转换者**、1.20.1 **保持无主**（可被任意玩家装备） | **修**（本批）：forge 对齐 1.21.1，统一为**绑定转换者**；有主时原样保留 |
| 3 | `AGENTS.md` 附件计数 81/83 与机械计数（`ATTACHMENTS.register(` 行数 93/95）不一致 | **改文档**：在该行显式写明「本行口径 = 玩家级附件；机械计数为 93/95，差额为非玩家作用域键，勿混用」 |

R1 自证边界（**需游戏内读数**，本批未做）：数值落地（+5 / +min(觉醒,4) 是否进入伤害跳字）、破防对 mob 的实际减伤幅度（受 `min(护甲,20)` 口径影响）、效果图标与 tooltip 渲染、多维度多人下的实际收牌集合、`keepInventory=false` 下的端到端死亡保留。⇒ 这些与「探针与用例尚未编写」一并留待 B1 轮。

### 7.1 R2 独立回归验证（2026-09-27，两条修复之后）
判决：**两条修复代码层全部成立、本批真实缺陷 0 条**（6 条作用域条件 + 修复 2 的两种归属分支逐条 PASS；两线 `collectActiveTargets` 归一后**逐字相同**、各 22 行零差异；`GiveoutScope`/`collectTargets` 本体保留、无未使用 import；改动面 private + 单一调用点 ⇒ 无其它立牌共用）。

R2 另确认：
- `p.level() == wearer.level()`（`ServerLevel` **引用相等**）是「同维度」的规范判据 —— `MinecraftServer` 每维度唯一实例，跨维度传送当拍最坏只漏发（假阴），**不会误发到其它维度**（假阳）。
- 两条边界已登记（见 §3.3）：`isAlive()` 过滤沿用旧口径；三开关全关 + 有队伍 ⇒ 空集（默认不可达）**待用户裁决**（(a) 接受为边界 / (b) 跨仓在共享库补「队伍系统是否启用」判定）。
- R2 报的两条文档问题已修：`AGENTS.md` 迁移条 18 ④ 改为「自管集合 + R1 两修复」口径（原写法会让 26.1.2 迁移重建被推翻的旧语义）；本文件 §3.3/§2.4 伪代码同步为 `collectActiveTargets`。
