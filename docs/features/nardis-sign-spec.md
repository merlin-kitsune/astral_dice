# 绿洲女王立牌（nardis）— 接口与落点冻结规格（v1，2026-09-27）

> 本文件是**本机冻结件**：`docs/` 已被 `.gitignore` 忽略（不入库）。提交侧的正式记录落在 `AGENTS.md`（立牌技能表 / 品质表 / 效果与附件清单）与两份 CHANGELOG。
> 实施范围：**两条发布线**（`neoforge-1.21.1` 先落地 → `forge-1.20.1` 按 `docs/compat-1.20.1-forge.md` 同步）；`neoforge-26.1.2` 本批未落，随后按迁移清单补。

## 0. 需求（用户原话，2026-09-27）

> 创建立牌：绿洲女王立牌
> 被动技能（威压）：每装备一张攻击牌牌：攻击力+1；每装备一张防御牌：防御力+1
> 主动技能（女王特权）：立即获取3张随机临时牌，有效期 3:00。临时牌只能装备和使用，无法丢弃或转移。在计时结束后，丢弃所有临时牌（包括已装备的）。
> ⚠️ 上引原话里的「攻击力+1；…防御力+1」已被 **2026-09-21 用户裁决**改为 **+2**（见 §3）；原话保留仅作基准与追溯。
>
> 技能说明：临时牌为随机给牌，遵循全局限制避免给予专属牌。临时牌全部添加附魔光效以表示区别（如果可能，最好叠加多层）。移除临时牌应包括物品栏和已装备到骰子中的。女王特权生效时，应显示立牌图标为效果计时器。给临时牌应确保激活所有获取卡牌的触发器。需严格保持临时牌只能在物品栏和手中，block所有移至其他容器的请求。

## 1. 已裁决（用户，2026-09-27）

| # | 议题 | 裁决 |
|---|---|---|
| 1 | 配方档位 | **黄金骰子（无星盘）** —— 与史莱姆 lulu / 上班族 padman 同档（稀有 RARE） |
| 2 | 临时牌随机池 | ~~**`CardCategory.ALL`**（战斗牌 + 效果牌）~~ **已于 2026-09-27 变更**（见下方 #6）：改为 **2 张战斗牌 + 1 张效果牌** |
| 3 | 主动重复释放（效果生效中再次释放） | ~~**先清空全部旧临时牌（含已装配到骰子的），再发 3 张新的**~~ **已于 2026-09-27 被冻结规则取代**（见 #7）：正常玩法下重复释放**不可达**，该清空逻辑降级为**安全网**保留 |
| 4 | 附魔光效 | **单层**（原版渲染无多层强度路径）：**两线同法 = `Item#isFoil(ItemStack)` 覆写**（判据 = 该栈带临时牌标记；见 §6.3 的取舍说明 —— 早期草案曾写「1.21.1 用 glint 覆盖组件」，**已作废**）。⚠️ 2026-09-27 R1 发现该覆写只落在 `CardItem` ⇒ **效果牌无光效**，落点必须上移到效果牌也能命中的位置（R2，已修） |
| 5 | 冷却时长 | 用户未指定 ⇒ 采用立牌统一默认 **180 秒**（`GameplayConstants` 既有口径），若需另值请裁决 |
| **6** | **给牌构成（2026-09-27 变更）** | **2 张战斗牌 + 1 张效果牌**：战斗牌池 = `RandomCardHandler.CardCategory.BATTLE`（攻击 + 防御）**混合随机 2 张**（允许两张同类）；效果牌池 = `CardCategory.EFFECT` 随机 1 张；专属牌沿用既有池排除逻辑；仍走 `VitaminPillChipItem.giveCard` 漏斗（联动触发器照旧）；放不下就少发、**绝不落地** |
| **7** | ~~冻结（2026-09-27 新增）~~ | **已于 2026-09-27 当日被用户推翻**（见 #11）：主动技能**不再进入冻结**，效果生效期间可再次释放（仅受冷却限制） |
| **8** | ~~解冻条件（先到者）~~ | **已作废**（见 #11）：牌被用光**不再**提前结束效果；效果只由 3:00 自然到期 / 外力移除 / 死亡结束 |
| **9** | 冷却（2026-09-27 修订） | **释放当刻即开始玩家级冷却**（默认 180 秒，沿用既有减免机制）；因为不再有冻结，立牌走普通立牌的「不进锁定」路径 ⇒ 冷却自然在释放当刻起算，**无需**并行/不追加那套特判（该特判已随冻结一并撤回） |
| **10** | **安全门（2026-09-27 新增；同日放宽阈值）** | **主物品栏可用格数 < 2 ⇒ 阻止释放并提示玩家**（裁决：由 `< 3` 放宽为 `< 2` —— 卡牌物品 `stacksTo(64)` 可堆叠、且发牌前先清空临时牌~~、恰好 2 格时两张战斗牌常并进一格、效果牌仍可能放得下~~ <br>⚠️ 「发牌前先清空」已随 #11 取消，本条的阈值理由改为：卡牌可堆叠 + 每次释放固定再发 3 张，2 格时按「少发、绝不落地」截断）；拒绝必须**零消耗**（不冷却、不施效果、不发牌、不清既有临时牌），玩家腾出空间后可立刻再释放；提示走 `msg.astral_dice.nardis_inventory_full`（两线 `zh_cn`/`en_us` 同步）。**判据顺序**：冷却 → 安全门 |
| **11** | **主动技能改为「叠加补给 + 重置有效期」（2026-09-27 最终裁决，推翻 #7/#8）** | ① **不进入冻结**：效果生效期间可以再次释放，**只受冷却限制**；② **再次释放不清空旧临时牌**，每次释放都**再发一整套 3 张**（2 战斗 + 1 效果）**叠加**在现有临时牌上（旧牌不过期就不清；受背包空间与冷却限制）；③ **每次释放把 `nardis_privilege` 有效期重置为 3600 tick**；④ **牌被用光不再提前结束效果**（`TemporaryCardUtil.tick` 的「无牌 ⇒ 移除效果」分支删除，仅保留「**无效果 ⇒ 清空剩余临时牌**」这条安全网）；⚠️ **该安全网的实际触发只有三条**：**3:00 自然到期**、**玩家死亡**、**内部 `ModEffectRemoval`** —— **外部 `/effect clear` / 牛奶会被产品拒绝**（`ModEffectEvents#onModEffectRemovalPrevented` 取消对任何 `astral_dice:*` 效果的外部移除，属本模组长期既定设计，`ZHAO-SIGN` 用例早有同形断言；2026-09-27 本轮实测 M4Z 再次确认「效果与牌都保留」）；⑤ 产品侧为冻结而加的整台机械（`BaseSignItem` 的 `cooldownRunsDuringLock` / `endLockAndStartCooldown` 2 参重载 / F1 的 `gateEffectRemainingTicks` + 重对齐）**全部撤回**，`BaseSignItem` 恢复到冻结机制引入前的原文 |
| **12** | **临时牌上限 9 张（2026-09-27 同日追加）** | 常量 `TemporaryCardUtil.MAX_TEMPORARY_CARDS = 9`；`current = countTemporary(player) + countTemporaryEquipped(player)`（与旧「牌用光」判定同一对计数口径 ⇒ **已装配到骰子的也算**）；`current >= 9` ⇒ **阻止释放**（`msg.astral_dice.nardis_card_limit`，**零消耗**）；否则发放**夹取到上限**（`budget = 9 - current`，实际 = `min(既定张数, budget)`，仍战斗牌优先、绝不落地）⇒ **总数永不超 9**。**判据顺序**：冷却 → 上限 → 物品栏空位（`< 2 ⇒ 拒绝`）。设计动机：叠加补给若不设上限，只要冷却好了就再放，临时牌永不过期、每 180 秒净增 3 张 |


## 2. 冻结 id 与字段

| 项 | 值 |
|---|---|
| 物品 id | `nardis_sign`（绿洲女王立牌） |
| 物品品质 | 稀有 = `Rarity.RARE`（本仓「传奇」才是 `UNCOMMON`，勿混） |
| 立牌类 | `item/sign/NardisSignItem`（继承 `BaseSignItem`） |
| 主动技能名 | 女王特权（`tooltip.astral_dice.sign.nardis_active`） |
| 被动技能名 | 威压（`tooltip.astral_dice.sign.nardis_passive`） |
| 效果 id | `nardis_privilege`（女王特权；HUD 计时器 + 图标 = 立牌贴图） |
| 效果类 | `effect/NardisPrivilegeEffect` |
| 临时牌标记 | `temporary_card`（1.21.1 数据组件 / 1.20.1 NBT 键）—— 落点见 §6 |
| 贴图 | `textures/item/nardis_sign.png`（源 `images/绿洲女王立牌.png`）；`textures/mob_effect/nardis_privilege.png`（**复用立牌贴图**，源同一文件） |
| 配方 | 黄金骰子 + 立牌（无星盘），datagen 生成 `recipe/nardis_sign.json` + `advancement/recipes/misc/nardis_sign.json` |
| 标签 | `data/astral_dice/tags/item/signs.json`、`data/curios/tags/item/stand.json`（1.20.1 为 `tags/items/`） |
| 手册 | `patchouli_books/astral_guide/en_us/entries/signs/nardis_sign.json` + `astral_dice.guide.entry.*` 键 |
| 创造栏 | `init/ModCreativeTabs`（与其余立牌同组） |

## 3. 被动「威压」

- 口径：**攻击力 +2 × 已装配攻击牌张数**；**防御力 +2 × 已装配防御牌张数**。
  ⚠️ **2026-09-21 用户裁决**：每张牌加成由 +1 小幅加强为 **+2**，常量化在 `NardisSignItem.BONUS_PER_CARD`（攻防两条链共用）—— 该裁决**覆盖** §0 原话里的「+1」，属用户指定的数值变更、非笔误修正。
- 「已装配」= 位于骰子物品的「卡牌栏 / 已装配卡牌」（`weapon_enhancement` 组件）中的卡牌；**背包里没装配的不算**。
- 计数按**张数**（同种多张按张计），按 `CardRegistry` 的类型归属判定攻击/防御（`isDefense`）。
- 读时计算、不落附件 ⇒ 卸下立牌 / 卸下卡牌立即失效；不做缓存（除非性能实测需要）。
- 攻击力加算落点与防御力加算落点：见 §6（recon 定稿）。
- 边界：临时牌**同样计入**（它就是一张普通战斗牌，只是被标记）；被清空时同时减去。

## 4. 主动「女王特权」时序

### 4.1 释放（按下主动键，经既有主动技能门控/冷却体系）

| 步 | 动作 | 备注 |
|---|---|---|
| 1 | 清空**全部**旧临时牌（物品栏 0..35、主手/副手、骰子装配栏），幂等 | 裁决 #3 |
| 2 | 发 3 张随机牌：`CardCategory.ALL`（专属牌已排除），**走既有标准发放路径**以保证「获得卡牌」类触发器全部生效 | 需求「激活所有获取卡牌的触发器」 |
| 3 | 给这 3 张打上临时牌标记 + 强制附魔光效 | 标记随栈复制（装配进骰子后仍是临时牌） |
| 4 | 施加 `nardis_privilege` 效果 **3:00**（180 秒）、`showIcon=true`（HUD 计时器）、图标 = 立牌贴图 | 需求「显示立牌图标为效果计时器」 |
| 5 | 进入主动冷却（默认 180 秒；不施加自己的计时器 ⇒ 与「三态化锁定」无关，除非实测需要） | §1 裁决 #5 |

### 4.2 临时牌的保护（效果生效期间）

- **不可丢弃**：`Item#onDroppedByPlayer(...)` 对带标记的栈返回 `false`（Q 键 / 丢弃路径都被拒）。
- **不可移入其它容器**：任何把带标记栈放进「非玩家自身容器」的请求一律拒绝（箱子/木桶/潜影盒/末影箱/漏斗等），**只允许**：玩家主背包 0..35、主手、副手、**骰子装配栏（卡牌栏 UI）**。
- **不可放入物品内的容器**（如收纳袋类，若目标平台存在对应 API）。
- 允许的正常消耗：效果牌**使用**、战斗牌**装配/消耗次数**、技能回收类效果对它的消耗 —— 清理逻辑对这些情况一律幂等。

### 4.3 到期 / 移除 / 死亡

| 触发 | 行为 |
|---|---|
| 效果时长自然到期 | 清空全部临时牌（含已装配到骰子的） |
| 效果被外力移除（`/effect clear`、经本模组统一移除通道等） | 同上（与 zhao 批「双保险」同口径：即时回收 + tick 自检，两者幂等） |
| 死亡 | 清空（避免临时牌随尸体掉落进世界） |
| 重登 | 若身上仍有带标记的牌而效果已不在（过期/被清），**登录即清理** |

### 4.4 状态迁移表

| 当前状态 | 事件 | 结果 |
|---|---|---|
| 无效果、无临时牌 | 释放主动 | 发 3 张带标记牌 + 效果 3:00 |
| 有效果、有临时牌 | 再次释放 | **先清空旧的** → 再发 3 张新的 → 效果刷新为 3:00 |
| 有效果、有临时牌 | 计时到 0 | 效果结束 → 清空全部临时牌（含装配栏内） |
| 有效果、有临时牌 | 玩家死亡 | 效果移除 + 清空 |
| 有效果、有临时牌 | 玩家离线再上线（计时已过） | 效果已不在 → 登录清理 |
| 无效果、有残留标记牌（异常路径） | 玩家级 tick 自检 | 立即清理（不留残留） |

## 5. 边界与口径登记（需在 R1 报告中逐条核对）

1. **骰子本身可自由移动**：只有「临时牌」受限，装它的骰子进箱子/被丢弃不受拦截 ⇒ 到期清理时**遍历玩家所有可及骰子**（背包 + 副手 + curios 槽）；已不在玩家身上的骰子，其内部临时牌在骰子被取回时由 tick 自检补清（如实登记，不声称"绝无残留"）。
2. **效果期间玩家切换维度/离线**：效果按世界时间计时，登录后由自检收口。
3. **与看板娘 mimi 主动「回收全部卡牌返还随机牌」的交互**：临时牌被回收 = 正常消耗（允许），返还的新牌**不是**临时牌（除非再次释放主动）。
4. **与「卡牌栏」UI 的交互**：必须允许把临时牌放进卡牌栏（那是"装备"），因此容器拦截白名单必须包含本模组自己的卡牌栏容器。
5. **临时牌的张数上限**：效果生效期间不重复发牌（裁决 #3 是"清空重发"），因此任意时刻最多 3 张临时牌；清理与发放都必须幂等。
6. **附魔光效不可多层**：原版渲染只有单层强度（用户已裁决接受）。

## 6. 注册与落点

### 6.1 「临时牌」标记的落点（**已实测冻结**，2026-09-27）

实测证据链（1.21.1 侧）：

| 事实 | 证据 |
|---|---|
| 已装配卡牌存在**骰子物品栈**的组件里 | `component/WeaponEnhancement.java:12`（record 含 `List<AppliedStone> appliedStones`）+ `component/ModDataComponents.java:19-23`（`WEAPON_ENHANCEMENT`，`.persistent(CODEC).networkSynchronized(STREAM_CODEC)`） |
| 该 record **只有** `(type, uses)` —— 不持有原物品栈 | `component/AppliedStone.java:15` |
| **装配会销毁卡牌物品栈**，卸下时按 `(type, uses)` 重建全新栈 | `screen/CardInventoryMenu.java:316-320` 的既有注释（原话："装备会销毁卡牌物品栈，卸除时由 loadFromDice 重建全新栈，任何物品级标记都不可能在『插入→卸除→再插入』之间存活"） |

> ⇒ **仅靠物品栈组件/NBT 标记临时牌是不够的**：牌一旦装配进骰子，标记就丢。这是本批最关键的设计分叉。

**冻结方案**：给 `AppliedStone` 增加第 3 个分量 `boolean temporary`。

1. `record AppliedStone(String type, int uses, boolean temporary)`；**保留兼容构造器** `AppliedStone(String type, int uses) → this(type, uses, false)`，既有 ~10 处调用点不改也能编译。
2. `CODEC` 用 `Codec.BOOL.optionalFieldOf("temporary", false)`（老存档/老数据包仍可读）；`STREAM_CODEC` 追加一列（两线同版本、无跨版本兼容问题）。
3. 回环（保证 UI 与标记一致）：
   - `screen/CardInventoryMenu.loadFromDice():268-284` 重建栈时，若 `stone.temporary()` ⇒ 给重建栈写临时牌标记 + 强制光效（⇒ 卡牌栏 UI 里也一眼可辨）；
   - `screen/CardInventoryMenu.saveToDice():295-310` 写回时从栈读回该标记（`new AppliedStone(type, uses, isTemporary(stack))`）。
4. 清理已装配的临时牌 = 过滤 `appliedStones` 中 `temporary()` 者，并按被移除卡的费用重算 `usedCost` / `usedDefenseCost`（费用口径 `stoneCost(type)`，重算路径参考 `saveToDice()`；`maxCost` 不变）。
5. 被动「威压」读取入口：`WeaponEnhancement.appliedStones()` + `CardRegistry.isDefense(type)` 计数（`CardInventoryMenu` 内有同名私有助手 `isDefenseType` 可参考）。
6. 装备事件先例（可选复用）：`CardInventoryMenu.saveToDice():316-326` 已在装备时调用 `TeruSignItem.onAttackCardsEquipped(player, map)` —— 说明「装配完成」有一个天然挂点。

### 6.2 关键落点（两份只读侦察 + 主代理实测，2026-09-27）

| 项 | 落点（1.21.1 证据） |
|---|---|
| 攻击力 +2/攻击牌 | `combat/DiceCombatModifiers.java` 静态注册块新增修饰器（照 jasmine `:315-326`、padman `:454-469`）；读 `ctx.enhancement.appliedStones()` 中 `!CardRegistry.isDefense(type)` 的张数；实战 `DiceCombatEvents:439-448`、显示 `attackPowerBase:604-625` / `attackPowerOf:568-581` 同源 ⇒ 无需新增链路 |
| 防御力 +2/防御牌 | `DiceCombatModifiers.setDefenseArmorBonus(player, "nardis_def_armor", defCount)`（1 防御 = 2 护甲；`:163-178`），**每 tick 在 `NardisSignItem#onCurioTick` 现算现写**，`clearSignData` 清零（先例 jasmine `:43-46`/`:56`）；防御修饰器链**不**读 `appliedStones`（`DefensePowerModifier:5-6`） |
| 主动技能模板 | 非选择器类：`selectorActionId()` 默认 `null`（`BaseSignItem:165-167`），效果主体 = `handleUse`（默认 WARN，`:479-483`）；最近样例 `MimiSignItem.handleUse:49-71`；ActionBar 用 `sendSignActionBar:214-221` |
| 效果注册/图标 | `effect/ModEffects`（两线现各 **34** → 35）；六参 `(…, false, false, true)`；图标 `textures/mob_effect/nardis_privilege.png` **逐字节复制**立牌图（先例：`zhao_blessing.png` 与 `zhao_sign.png` SHA256 相同、各 516 B；`teru_descent.png` 同理）；`EffectTimerGuard.apply` 保时长且不丢 `showIcon`（`:46/122-140`） |
| 资产触点 | `item/ModItems`（RARE 样例 `:371-379,386-389,879-882`）、`init/ModCreativeTabs:87-93`、`datagen/ModItemModelProvider`、`datagen/ModRecipeProvider`（**黄金骰子无星盘档 = lulu/padman**）、`data/astral_dice/tags/item/signs.json:21` + `data/curios/tags/item/stand.json:20`（1.20.1 为 `tags/items/`）、`assets/…/lang/{zh_cn,en_us}.json`（各 714 键基数）、手册 `…/entries/signs/nardis_sign.json`（**仅 en_us 目录**，`entries/signs/` 现 19 条）、贴图两张 |
| Curios 注册 | 最新先例（zhao/teru）**未**在 `AstralDiceMod` 里逐物品 `registerCurio`，但游戏内可装备 ⇒ 照先例不加（R1 若判定必需再补） |

### 6.3 实施期冻结决策（2026-09-27，实现前定稿）

| 议题 | 决策 | 理由/证据 |
|---|---|---|
| 光效实现 | **`item/card/CardItem#isFoil(ItemStack)` 覆写** + 临时牌组件判定（两线同法） | 1.21.1 也可用 `DataComponents.ENCHANTMENT_GLINT_OVERRIDE`，但那会往物品写入额外组件；1.20.1 **没有** per-stack 覆盖，若塞真附魔会**改变物品名稀有度颜色**并多出附魔行（`Item.getRarity` 受 `isEnchanted()` 影响）。`isFoil` 覆写在两线都干净且天然 per-stack。 |
| 是否起「锁定(生效中)」态 | **不起**（`startActiveLockOnUse` 保持默认 `false`，触发后立即进入玩家级冷却） | 裁决③「先清空再发」只有在**冷却被减免后能于 3:00 效果仍生效时再次释放**才有意义；若起锁定，冷却要到效果结束才开始（下次 6:00）⇒ 裁决③ 永不可达。⚠️ 与 jasmine/fen/papara/teru 的「挂自身计时器即锁定」惯例**不同**，属本技能特有取舍，需在 R1 差异表里显式登记。 |
| 是否新增玩家附件 | **不新增** | HUD 计时器 = 原生效果实例（原版同步）；临时性 = 物品组件 + `AppliedStone.temporary`；清理靠「有临时牌但无效果」的 tick 自检 ⇒ 不需要 1.20.1 的 `SYNCED_KEYS` 改动，附件计数 78/80 不变。 |
| 保护三件套 | `CardItem#onDroppedByPlayer` 返回 false；`ItemTossEvent` 取消 + 尽力退还；mixin `Slot#mayPlace` + mixin `AbstractContainerMenu#moveItemStackTo`（1.21.1 另加 `canFitInsideContainerItems(ItemStack)` 返回 false） | 丢弃：`ServerPlayer#drop(boolean)` 在移除前判定（1.21.1 `:2053`）；合并分支绕过 `mayPlace`（`moveItemStackTo` 实测 `:637-663`）⇒ 必须两个 mixin；潜影盒/收纳袋在 1.21.1 是 stack-aware（`ShulkerBoxBlockEntity:238`、`BundleItem:57`）。 |
| 1.20.1 潜影盒/收纳袋 | 1.20.1 无 stack-aware 钩子，需 `ShulkerBoxSlot#mayPlace` 等 mixin；**由 1.20.1 镜像 subagent 实现并在报告里列出**（若某处无干净注入点，如实登记为缺口，不得用整类物品禁止来蒙混） | 1.20.1 的调用点全部是 `stack.getItem()...`（类型级）⇒ 整类禁止会连带永久牌。 |
| 背包满时不落地 | 发牌前先查可用空槽，最多发 `min(3, 空槽数)` 张 | 满足「临时牌只能在物品栏和手中」；不产生落地实体。 |
| 探针可测性 API（public static） | `TemporaryCardUtil.grantRandom(player,n)` / `countTemporary(player)` / `countTemporaryEquipped(player)` / `isPlacementBlocked(stack, targetIsPlayerOwnedSlot)` | mixin 与探针共用同一条判据；探针可直接 `new Slot(container,…).mayPlace(带标记卡)` 验证 mixin 真身（无需 GUI 注入）。 |

### 6.4 其余落点（侦察返回值待补）

> 待两份只读侦察返回后补齐：给牌触发器全清单与 `RandomCardHandler` 精确 API、丢弃/容器拦截的实现方式（含现有 mixin 先例与两线差异）、glint 两线写法、攻击力/防御力加算的确切方法与行号、立牌主动技能模板（`MimiSignItem`/`BaseSignItem`）、datagen/lang/手册/标签/创造栏写法、两线差异清单、文档更新点。

## 7. 测试计划（B1 / R2 用；两线探针**逐字节相同**）

### 7.0 R1/R2 回归契约（2026-09-27 追加；现有 NARDIS 用例**不覆盖**，必须单独取证）

R1 明确指出：原用例全程只用 `attack_card_medium` ⇒ **R1（效果牌的容器缺口）与 R2（效果牌无光效）在游戏内不可见**。因此追加一组**只读**读数 + 一条独立小用例（`NARDIS-TEMP-GUARD-{1.21.1,1.20.1}.json`），制造方式与 `narditemp` 同源（临时标记直接写在**效果牌物品**上）：

| 读数（同一条命令 `nardiguard <tag> <effect|attack>`） | 断言 |
|---|---|
| `foil_t=1` / `foil_n=0` | **R2**：同一 id 的临时效果牌发光、普通效果牌不发光（攻击牌同断言一组） |
| `guard_t=0` / `guard_n=1` | **R1（1.21.1）**：`ItemStack#canFitInsideContainerItems()`（栈级，五处容器入口的唯一判据）对临时牌为 false、普通牌为 true |
| `face_t=0` / `face_n=1` | **R1 行为面**：真实放置的潜影盒方块实体 `canPlaceItemThroughFace(0, stack, UP)` 对临时牌返回 false —— **1.20.1 侧靠这条取证**（该线无栈级钩子，由 `ShulkerBoxBlockEntityGuardMixin` 拦） |
| `perm_t=1` / `perm_n=1` | 非临时牌逐字节等价：**永久牌不受影响**（不会出现第二个拦截点） |

覆盖边界（如实登记）：潜影盒 **GUI 槽**路径 1.20.1 侧无法从 JS 直取（`ShulkerBoxSlot` 不可实例化），由 1.21.1 的 `guard_t=0`（同一条 `canFitInsideContainerItems` 调用）+ 代码证据覆盖；1.20.1 的 `Container#canPlaceItem`（模组管道 `InvWrapper`）路径是该线**已登记**的平台差异（见 `ShulkerBoxBlockEntityGuardMixin` 类注释），本组不覆盖。

### 7.3 新语义（2026-09-27 裁决）断言契约

| # | 断言组 | 要求 |
|---|---|---|
| N1 | **给牌构成** | 一次释放发放 **恰好 3 张 = 1 张效果牌 + 2 张战斗牌**（战斗牌 = 攻击 ∪ 防御，允许两张同类）；**全部非专属**；`granted` 与实际入包张数一致、**地面掉落物 0** |
| N2 | **冻结** | 释放后处于「使用中」：**重复释放被拒绝**（`granted=0`、`n_temp` 不变、冷却值不变），并给出「使用中」+ 剩余时间的既有立牌提示 |
| N3 | **解冻（牌用光）** | 把 3 张临时牌全部消耗/丢弃到 0 ⇒ **立刻解冻**（`n_temp=0:n_temp_eq=0`）**且 `n_fx=0`（效果被移除、计时器消失）** |
| N4 | **解冻（到期）** | 3:00 到期 ⇒ 清空剩余临时牌（`n_temp=0:n_temp_eq=0`）并解冻 |
| N5 | **冷却并行、解冻不追加** | 牌在 t≈10s 用光后仍 `cool>0`（冷却未过 ⇒ 仍不可释放）；用 `nardiprep … clear` 复位冷却后 ⇒ **立即可释放**（证明解冻没有额外冷却） |
| N6 | **安全门** | 主物品栏可用格 **< 2** ⇒ 释放**被拒绝**且**零消耗**（`cool` 不变、`n_fx=0`、`n_temp` 不变、`granted=0`），并出现新 lang 键的提示（断言提示文本或键名）；腾出空间后立刻可释放。**另需覆盖边界**：可用格 **= 2** ⇒ **允许**释放，实发张数按「战斗牌优先、各张独立重算空槽」断言（两张战斗牌同 id 才凑满 3 张，否则 2 张） |
| N7 | **防抖** | 上述流程不得在日志里出现效果/临时牌被反复增删的证据（同一 tick 或相邻 tick 反复 `n_fx` 0/1 跳变、`n_temp` 抖动） |
| N8 | **冻结分歧重对齐（F1 修复回归）** | 单机测试世界**无法**复现「多人在线离线跨越剩余冻结时长」（停客户端=停服务器，gameTime 不推进）⇒ 改由脚手架**直接构造分歧**：在「效果仍在 + 临时牌仍在」的前提下把 `sign_active_lock_end` 写到**过去**，然后 tick ⇒ 断言 ①**仍处于冻结**（`lock=1` 且 `lock_end > now`，即上界按效果剩余时长被重新对齐）、②`cooldown_end` **不被归零/不跳跃**、③`n_temp` 与 `n_fx_on` **不变**（不得免费刷新、不得提前清牌） |

> ⚠️ **§7.3 中 N2/N3/N4/N5/N7/N8 一切依赖「冻结 / 使用中 / 解冻 / 牌用光即结束效果」的断言已于 2026-09-27 当日作废**（裁决 #11 推翻冻结；F1 机械已撤回）。**N1（给牌构成）、N6（安全门）仍然有效**（N6 的阈值理由按 #10 修订）。替代契约见下方 §7.4。

### 7.4 最终语义（2026-09-27 #11：不冻结 / 叠加补给 / 重置有效期）断言契约

| # | 断言组 | 要求 |
|---|---|---|
| M1 | **首次释放** | `granted=3`、`n_temp=3`、`t_battle=2:t_effect=1:t_excl=0`、`fx_dur≈3600`、`ground_temp=0`；**无锁定**（`lock=0:lock_end=0`）；冷却已起（`cool≈3600`） |
| M2 | **叠加再发（核心新增）** | 效果仍生效时（先复位冷却、**不传 `reset`**）再释放 ⇒ **旧牌不清**：`temp_before=3 → temp_after=6`、`granted=3`；且 **`fx_dur` 被重置回 ≈3600**（必须明显大于「旧效果剩余」，以证明是**重置**而非沿用旧计时） |
| M3 | **牌用光不再结束效果** | 把临时牌消耗/清到 0 ⇒ **`n_temp=0` 而 `n_fx_on=1`**；**多读 2–3 拍**确认效果继续走、**不得**出现 `n_fx_on` 归零（旧「无牌⇒移除效果」分支已删） |
| M4 | **3:00 到期** | 效果自然到期 ⇒ 清空剩余临时牌（`n_temp=0:n_temp_eq=0`）且效果消失 |
| M5 | **冷却门** | 刚释放后立刻再释放（不复位冷却）⇒ **被冷却拒绝**（`granted=0`），且**旧牌不得被清**（`n_temp` 不变）、`fx_dur` **未被重置** |
| M6 | **安全门** | 空槽 **1** ⇒ 拒绝且零消耗（`granted=0`、`cool` 不变、`n_temp` 不变、`n_fx_on=0`）；空槽 **2** ⇒ **允许**（`granted∈{2,3}` + `n_temp==granted` + 类型自洽 + `ground_temp=0`） |
| M7 | **防抖** | 上述流程不得出现效果/临时牌被反复增删的读数抖动 |
| M8 | **临时牌上限 9 张** | ① `current >= 9` ⇒ **拒绝释放**（`granted=0`、`cool` 不变、`n_temp`/`n_temp_eq` 不变、`n_fx` **未被重置**）+ 出现 `msg.astral_dice.nardis_card_limit` 提示（按静态键名核对）；② `current = 7` ⇒ **允许**且**实发 ≤ 2**（`n_temp + n_temp_eq` 恰为 9，**永不 > 9**）；③ `current = 8` ⇒ **允许**且实发 ≤ 1；④ 全程断言 `n_temp + n_temp_eq <= 9` 恒成立（含 M2 叠加再发之后）；⑤ 上限判定排在**物品栏空位**之前（判据顺序：冷却 → 上限 → 空位） |


### 7.1 探针命令（`nardi*`，读数前缀 `AP_<tag>_`，除基线/脚手架外全只读）

| 命令 | 作用 | 关键读数 |
|---|---|---|
| `nardiprep <tag> [clear] [name]` | 给目标（缺省自身）装**绿洲女王立牌 + 骰子**；`clear` ⇒ 额外清临时牌与效果、复位冷却 | `sign_err`/`dice_err`/`n_eq=1`/`n_temp=0` |
| `nardiread <tag> <phase> [name]` | 全量只读 | `n_eq` `atk_cards` `def_cards` `n_temp` `n_temp_eq` `n_fx`（效果剩余 tick）`ap` `dp` `pill`（治愈点）`teru`（狐光层数）`cool` |
| `nardicast <tag> [name]` | 走**真实主动**（`BaseSignItem.performSkillForCurio(owner)`） | `granted`（本次发放张数）`n_temp` `fx_dur` `cool` |
| `nardigive <tag> <n> [name]` | 直接 `TemporaryCardUtil.grantRandom(owner, n)`（绕开冷却，测发牌本身） | `granted` `n_temp` `slots_free` |
| `narditemp <tag> <inv\|assemble>` | 造一张临时牌：`inv` = 主手塞一张带标记的卡；`assemble` = 直接往骰子 `WEAPON_ENHANCEMENT` 里塞一个 `temporary=true` 的 `AppliedStone` | `where` `n_temp` `n_temp_eq` `used_cost` |
| `nardifoil <tag>` | 读光效真值 | `hand_id` `foil=0/1`（临时牌 1、普通牌 0） |
| `nardidrop <tag>` | `ServerPlayer#drop(true)`（主手为临时牌） | `dropped` `hand_before` `still_in_inv` |
| `nardislot <tag>` | **直接验证 mixin**：`new Slot(new SimpleContainer(9),0,0,0).mayPlace(临时牌)` 与 `new Slot(player.getInventory(),0,0,0).mayPlace(临时牌)` | `blocked_container=1` `allow_inventory=1` |
| `nardiexpire <tag> [name]` | 移除 `nardis_privilege`（**内部通道** `ModEffectRemoval`）并驱动一次自检 | `purged` `n_temp` `n_temp_eq` |
| `nardinuclear <tag>` | 收尾：清临时牌 + 清效果 + 卸立牌 + 清主手 + 复位冷却 | `cleared=1` |

### 7.2 用例断言清单（`NARDIS-SIGN-{1.21.1,1.20.1}.json`）

1. 基线：`n_eq=1`、`n_temp=0`、`n_fx=0`。
2. **被动**：装 2 张攻击牌 + 1 张防御牌 ⇒ `ap` 相对基线 **+4**（`ap=5`）、`dp` **+2**（`dp=4`、`armor=4`）；卸下 1 张攻击牌 ⇒ `ap` 回落 +2（读时现算，不缓存）。
3. **主动发放**：`nardicast` ⇒ `granted=3`（空槽充足时）、`n_temp=3`、`fx_dur≈3600`、`cool` 起算。
4. **光效**：临时牌 `foil=1`；同 id 的普通牌 `foil=0`（**单层**，用户已裁决）。
5. **保护·丢弃**：`nardidrop` ⇒ `dropped=0` 且 `hand_after` 仍是那张牌（`onDroppedByPlayer` 拒绝，不损失）。
6. **保护·容器**：`nardislot` ⇒ `blocked_container=1`（`Slot#mayPlace` mixin 在非玩家容器上拒绝）且 `allow_inventory=1`（玩家自己的物品栏放行）。
7. **装配存活**：`narditemp assemble` ⇒ `n_temp_eq=1`；`nardiexpire` ⇒ `n_temp_eq=0` 且 `used_cost` 回落（过滤 `AppliedStone` 后重算）。
8. **重复释放**：`nardicast` → `n_temp=3` → 再 `nardicast` ⇒ 旧临时牌先清空、`granted=3`、`n_temp=3`（裁决③）。
9. **触发器**：佩戴维生素药丸筹码时 `pill` 增长随发放张数；佩戴教主立牌时 `teru` 层数随发放的**攻击牌**张数增长（走 `VitaminPillChipItem.giveCard` 漏斗）。
10. **到期/死亡清理**：`nardiexpire` 与死亡路径后 `n_temp=0`、`n_temp_eq=0`、`n_fx=0`，且无 `AP_*_ERR`、无落地实体。
11. 收尾 `nardinuclear` ⇒ 全量读数归零 + `MT_ASSERT_KUBJS`/`MT_ASSERT_CRASH` 闸门通过。

### 7.3 覆盖边界（如实登记）

- GUI 里的**真实鼠标点击/Shift 点击**无法用注入命令驱动 ⇒ 用 `nardislot` 直接调用**真实 mixin 目标方法**（`Slot#mayPlace`）取证；`AbstractContainerMenu#moveItemStackTo` 的合并分支只由 R1 代码验证覆盖（探针无法调用 protected 方法）。
- 客户端**渲染层**的“光效可见”不做截图断言（`hasFoil` 真值 + 1.21.1/1.20.1 的 `ItemRenderer` 均以 `hasFoil` 为门控，R1 以代码证据覆盖）。

## 9. 本批执行状态与剩余步骤（2026-09-27 交接）

**2026-09-27 二次需求（用户当日追加，均已裁决并落地，见 §1 表 #6–#10 与 §7.0/§7.3）**
- 给牌构成改为 **2 战斗 + 1 效果**；新增**冻结（使用中）**；解冻先到者（牌用光 ⇒ 立刻解冻**且移除效果** / 3:00 到期 ⇒ 清剩余牌）；冷却**自释放并行计时、解冻不追加**；新增**安全门**（空槽 < 3 ⇒ 拒绝且**零消耗** + 提示）。
- 实现落点（两线）：`TemporaryCardUtil`（`GRANT_BATTLE_COUNT/GRANT_EFFECT_COUNT/grantNardisPrivilege/grantRandom(Player,Category,int)` + `tick` 尾段解冻(a)三重防抖）、`NardisSignItem`（安全门 + `startActiveLockOnUse`/`isGateEffectActive`/`cooldownRunsDuringLock`）、`BaseSignItem`（**唯一结构性改动**：新增 `cooldownRunsDuringLock` 钩子，默认 `false`；`endLockAndStartCooldown` 拆 2 参私有重载）、lang 4 文件（新 msg 键 + 4 个旧文案改写，键数 736 → **737**）。
- **主代理独立核实**：`mt_build` 两线 `OK`；`javap` 在 `run/<V>/mods/` 的 jar 里看到全部新方法两线齐备；**解包 jar 的 lang 确认新文案在位、旧文案（`§e3§7 张随机临时牌` / `效果生效中再次释放` / `从全部战斗牌与效果牌中随机`）已消失**；`tools/check_lang_sync.ps1` exit 0（两线 737/737、26.1.2 683 未动）；`tooltip_color_audit` **12 处 = 既有基线**（teru `50%`），本批**新增 0**；CHANGELOG 两文件 **367:367 条 / 578:578 行**、各子节逐节相等。
- 文档：`AGENTS.md` 立牌技能表改新语义 + 迁移清单**第 14 条**（ESC 输入放行，26.1.2 同构代码仍在）/ **第 15 条**（nardis 新语义 + 落点 + 三条已登记推论 + 文案同步口径）；CHANGELOG 按「合并进原条目」改写 nardis 条目 + 新增 ESC 修复条目。
- **已登记不修（实现方主动纠正/推导，主代理采信）**：① 卡牌物品 `stacksTo(64)` 可堆叠 ⇒ 「空槽 < 3 即拒绝」最坏保守 ≈1/9（保留确定性口径，**是否放宽待用户**）；② 解锁时冷却已过完 ⇒ 冻结期减免池作废；③ `cooldown_end` 为绝对 gameTime 而效果时长离线冻结 ⇒ 长离线重登可能「冷却已过、冻结尚存」。
- **未验证**：新语义的两线游戏内行为（B1）——断言契约见 §7.3（N1–N7，含「牌用光后 `n_fx` 多拍稳定为 0、不得抖动」这条防环主判据）。**产品改动与测试资产尚未提交。**

**已完成并验证（首轮）**
- 两条发布线产品代码 + 资产全部落地：1.21.1（Java 逻辑/mixin/组件/资产）+ 1.20.1 完整镜像（含 3 个 1.20.1 专有容器 mixin：`ShulkerBoxSlot#mayPlace`、`ShulkerBoxBlockEntity#canPlaceItemThroughFace`、`BundleItem#add`）。
- **两线构建均由主代理亲自跑通**：`mt_build.ps1 --version 1.21.1` 与 `--version 1.20.1` 均 `MT_BUILD: OK`。
- **已提交 `8e59e923`**（72 文件：两条线实现 + 新增资产 + AGENTS.md 五处表格 + 两份 CHANGELOG 玩家侧条目），**未 push**。
- AGENTS.md 已更新：立牌品质表（`黄金骰子(无星盘)` 行加 nardis）、立牌技能表（新增 nardis 行）、效果清单、数据组件清单（10→11，含 `temporary_card`）、26.1.2 迁移清单**第 13 条**（8 子项）。
- **效果计数勘误（2026-09-27，R1 发现 + 主代理实测复核）**：AGENTS.md 原写「34→35」「实测各 35 个」「两线现各 34 个」**全部过时**。实测（`Select-String 'register\(\s*"'` 计数）：**1.21.1 = 37、1.20.1 = 37、26.1.2 = 32**（迁移前基线：发布线 `HEAD~1` = 36 ⇒ nardis 使其 **36→37**）。26.1.2 的 id 集合是两个发布线的**真子集**，差额恰为**尚未迁移**的 5 个：`zhao_blessing`/`misfortune`（风水师）、`teru_descent`/`teru_huguang`（照）、`nardis_privilege`（女王特权）⇒ AGENTS.md 里「26.1.2 收口后 id 集合与两个发布线逐项一致」这句已加时效注记；`PlayerTickEvents` 里「调 `temporaryCardTick`」更正为**真实名 `TemporaryCardUtil.tick`**。
- 静态守门基线（R1 比对用）：语言 736/736 键一致（26.1.2 = 683）✅；`tooltip_color_audit` **既有 12 处违规**（全是 teru 的 `50%`，4 文件各 3 处）——本批**新增 0 处**；`check_mod_sources` ✅。
- 26.1.2 线**零改动**（`git status` 为空）。

**进行中**
- **B1 游戏内验证**：首跑 `NARDIS-SIGN-1.21.1` = FAIL，24 条断言失配。根因**全部在测试资产**（已用探针真实读数取证）：① 用例期望值系静态推演（真实基线 `ap=1`、`foil=-1` 表示主手非卡牌）；② `nardigive`/`narditemp` 发的牌被产品 tick 自检按 spec §4.4 正确清空（无效果 ⇒ 清空）⇒ 这两条命令必须同时施加效果；③ `nardicast` 参数位把 `reset` 当成了玩家名；④ `nardiarm` 抛异常（`err=J…`）；⑤ 步骤间 `dice_hand`/`n_stones` 漂移需显式补前置。**修复循环已授权专职 subagent 执行**（含收停/冷启动/单用例），完成判据 = 两线用例 PASS 且**核心差值断言不得弱化**。
- **R1 独立代码验证：已回报**（12 条需求 8 条 PASS）。静态守门与 26.1.2 的零改动核实与基线逐条一致；偏离 A（耐久重建透传 `temporary`）经全量复核**成立且无漏点**（5 处 `new AppliedStone(` / 8 处 `new WeaponEnhancement(` 逐一核过）；C（临时牌两层标记闭环，8 个进出路径）成立；E（单一拦截判据）成立。**发现两处真实缺陷**：
  - **R1（高危，仅 1.21.1 独有，玩家可达）**：临时**效果牌**可放进**潜影盒 GUI 槽 / 收纳袋并永久留存**。1.21.1 唯一防线挂在 `CardItem.canFitInsideContainerItems`（约 `:93-96`），而效果牌走 **`BaseEffectCardItem`（`extends Item`，约 `:75`，不继承 `CardItem`、未覆写）**；`ShulkerBoxSlot#mayPlace` 是覆写且不调 super ⇒ 槽 mixin 不生效；tick 自检只扫物品栏/副手 ⇒ 塞进潜影盒后**永不清理**。1.20.1 的三个 mixin 用类别无关判据 ⇒ **无此缺口** ⇒ 两线功能不对等且此前未登记。
  - **R2（中，两线同有）**：临时**效果牌无附魔光效**（`isFoil` 只写在 `CardItem`）⇒ 需求「临时牌全部添加附魔光效」对效果牌未达成。
  - 同修方案：让临时牌判据/光效的覆盖面 = **全部牌类**（把 `isFoil` 与 `canFitInsideContainerItems(ItemStack)` 的落点上移到效果牌也能命中的位置；已派专职 subagent 两线同修）。
  - 其余登记项：**R3**（spec §5.1 承诺「遍历背包+副手+curios 的所有可及骰子」，实现只查 curios ⇒ 骰子取下放背包/箱子后到期不清，仅「重新装备后补清」成立）、**R4**（1 防御=2 护甲 + 护甲上限 20 ⇒ 满甲时防御被动无体感）、**R5**（`purgeEquipped` 费用重算与装配基准可能不同源）、**R6**（`giveCard` 落地兜底语义）、**R7**（临时**攻击牌**经 `giveCard` 漏斗给教主狐光计层 ⇒ 冷却被减免后可刷层，需求 9 的必然结果，**待用户定夺**）、**R8**（槽归属判据有两份实现：`SlotPlaceGuardMixin:41-43` 内联复刻 `isPlayerOwnedOrPermissive`，可维护性）、**R9/R10**（效果计数与 tick 名，已修正）。
  - ⚠️ **现有 B1 用例发现不了 R1/R2**（全程只用 `attack_card_medium`）⇒ 必须给探针/用例**补效果牌 + 潜影盒/收纳袋断言**。

**剩余步骤**
1. 收齐 B1（两线 PASS 证据）与 R1 报告 → 形成**差异表**（含两处需用户定夺：**刻意不起锁定态**（为让「重复释放先清后发」可达）、**单层光效**（原版无多层渲染路径））。
2. 提交测试资产：两线探针 `nardi*` 段（**逐字节相同**，已独立校验 913 行 + 95 行块同哈希、纯 CRLF）+ `NARDIS-SIGN-{1.21.1,1.20.1}.json`（97 步 / 37 断言）。
3. `scripts/test/TESTING-SPEC.md` **附录 A 追加本条工程记录**（含 run id、逐用例断言统计、报告路径）。
4. 把 R1/B1 结论与两处取舍交用户定夺（二重验证的 D 阶段），再落 AGENTS.md 口径条目（**D 阶段前不得写入差异结论**）。
