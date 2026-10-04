# 风水师立牌 / 符卡-福·祸 / 大当家「心意相连」— 接口与落点冻结规格（t1）

> **性质**：本文件是**契约冻结件**，不是实现报告。两条实现线（`v121` = `neoforge-1.21.1`，`v120` = `forge-1.20.1`）
> 与 `verifier` 一律以本文件的 **§1 最终 id 表** 为唯一命名依据。
> **证据口径**：所有结论均给「文件:行号」。仓库 = `F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`，
> 冻结时点 HEAD = `1970de2c`）。共享前置库 = `F:\MCProject\starengine_lib`（分支 `main`，**本批不改库**）。
> ⚠️ **行号基准 = `git show 1970de2c:<path>`（HEAD），不是并行实现中的工作区**：`v121`/`v120` 正在改动同一批文件，
> 工作区行号已在漂移（实测：`neoforge-1.21.1/.../item/sign/FenSignItem.java` 的 `MAX_RECHARGE` 在 HEAD 为 `:37`，
> 工作区已移到 `:39`）。复核任一引用请用 `git show 1970de2c:<path> | Select-String '<符号>'`。
> 原版证据取自 ModDevGradle 反编译源 jar：
> - `neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-sources.jar`
> - `forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar`
> 引用 jar 内条目时写作 `<jar 内路径>:<行号>`。
> **冻结期待迁移清单**：`neoforge-26.1.2` 按用户既有裁决**冻结**，本批零改动；其待迁移项见 **§11**（只登记）。
> **修订记录**：`t1`（初次冻结，行号基准 HEAD `1970de2c`）→ **`t6`（本版 = 裁决回填）**：写入 captain 的 4 项裁决
> （§14.1 符卡-祸容器边界 / §14.2 「出牌数 +1」语义与用例断言 / §14.3 冒烟级别 = 完整冒烟 / §14.4 符卡 id 保持
> `fu_card`·`huo_card` 不变），新增 **§14.5 待验证项（出牌计数归属，交 verifier 逐条实证）**，并把 2 条跨线实证差异
> 收进 **§15 跨线实证差异对照表**。
> → **`t7`（裁决② 改判，本版）**：captain 复核 `EffectCardPeriod` 后**推翻自己 `t6` 的裁决②读法** ——
> 「出牌数 +1」由**「每轮一次（`grantBonusPlay`）」改为「每张各 +1（专属本周期计数器，受 `min(9,…)` 封顶）」**，
> 用户已定夺（选项 B）。改判依据（槽位共用致静默失效 + 原「会刷爆」理由被证伪）见 **§14.2**；
> 连带更正 §14.5（Q3 期望值 + 出牌数收支表）、§12.2 第 3/11 条、§12.1 入口符号行，并把原先误指向
> 「§14.5 第 3 问」的**容器计数推论**独立为 **Q4**（交叉引用缺陷就地修正）。§14.1/§14.3/§14.4 不受影响。
> → **`t8`（`v120` 回报三点后的取证与裁决，本版）**：① 赏金池以 **AGENTS 文档约定优先** ⇒ 删 `zhao_sign`、
> 补 `fu_card`/`huo_card`；② 「心意相连」组队判据**必须走共享库** `EventTargetCollector.hasAnyTeam` +
> `collectTeamPlayers`（原生 `getTeam()` 会让 FTB/OPAC 组队失效）；③ `fu_card_cycle_bonus` 两侧同步性**已对等、无需改**；
> ④ **闸门自身缺陷就地修正**（卡牌判据改为「id 前缀 ∪ 卡牌标签」，消除对 `fu_card`/`huo_card` 的失明区），
> 并登记「`-Root` 默认 `'.'` 导致跨树**假绿**」这一运行陷阱。详见 **§14.7**；§15.1 的「新键同步策略」行同步更正为
> **6 个键、其中 `fu_card_cycle_bonus` 同步**。
> → **`t9`（用户裁决 · 最高优先级，本版）**：**专属效果牌禁止进入任何赏金池** —— 用户裁定「符卡-福 / 符卡-祸 均为专属效果牌，
> 严禁通过除风水师立牌以外的途径获得」⇒ **推翻 §14.7 ① 后半「补入两张符卡」**（captain 误按 `AGENTS.md:772`「全部卡牌」机械读法，
> 忽略专属牌全局例外；该指令已实际造成两侧池内出现这两条，`v121` 侧已删净、`v120` 侧由 `t8` 删除）。
> 闸门期望集合改为「卡牌 **− 专属效果牌」；`AGENTS.md` 规则行补「专属效果牌除外」+ 新增硬规则；**新暴露的既有违规**
> （`effect_card_living_page` / `effect_card_fate_guidance` 早已在池内）登记为**具名 WARN 并升级用户裁决**。详见 **§14.8**。
> → **`t33`（本轮用户裁决 · 就地更正，本版）**：**副手持有符卡-祸计入厄运层数** —— 用户裁决**推翻**本文件此前
> 「计数 = 主物品栏（`player.getInventory().items`）」的**旧口径**（旧口径下副手不计入）⇒ 现冻结为
> **主物品栏 + 副手**：`HuoCardItem#count()` 在遍历 `getInventory().items`（先例
> `item/chip/SmartWatchChipItem#countCards`）之后**单独读一次** `player.getOffhandItem()`
> （**禁止**用 1.20.1 含护甲与副手的 `Inventory#getContainerSize()` 遍历）；副手**不是**容器 ⇒ 放进副手照常计入。
> **「容器不计入」仍然成立**（放进普通容器后既不产生周期伤害、也不计入厄运层数，裁决① 的放行边界不变）。
> 连带更正 §3.3、§9.2、§12.2 第 4/5 条、§14.1、§14.5 Q4；另**登记**符卡-福 / 符卡-祸 **计数口径的不对称属待用户裁决项**（§14.10；**已于 `t36` 结案**，见下条）。
> → **`t36`（本轮用户裁决 · 定案，本版）**：三条裁决落地为**定案**（其中两条**不推翻**既有行为、只把口径固化）——
> ① **战斗爽保留粒子**（用户原话：「战斗爽不去除粒子（及时效果，不属于计数器类）」）：`item/sign/FenSignItem.java`
> `handleUse` 里 `FEN_FRENZY` 实例的 `visible=true` 是**有意行为**，**不得**按「`visible` 只管粒子」的注释当成遗留 bug 删掉；
> `FS1` 断言已用 `frenzy_visible=1` **正向锁定**（见 §14.12）。**不推翻**任何既有实现（现状即为 `visible=true`）——
> 推翻的是「把该处当遗留 bug 修掉」这一可能的处置。
> ② **符卡-福 `countFu()` 保持主栏口径**（§14.10 **结案**；用户原话：「本身持有并无特殊效果，因此是否计入没有意义」）——
> 推翻的是本文件 `t33` 版把它列为**待裁决项**的状态（「统一为 B 口径」**不再考虑**），并据此撤销「待用户裁决」措辞；
> `HuoCardItem#count()` = 主栏 **+ 副手**（`t33` 裁决）**不受影响、继续有效**。
> ③ **目标选择器仅主手生效**为**定案**（见 §14.11；现状即如此、**本轮零代码改动**）—— **不推翻**任何既有行为，
> 只是把此前未写进规格的口径固化并给出四条代码证据；**副手不唤起选择器**是该规则的直接推论。
> 本版即 `v121` / `v120` / `verifier` / `scribe` 共读的**同一版本**（两棵工作树同哈希）。

---

## 1. 最终 id 表（唯一命名来源）

| 类别 | 注册 id / 常量 | 落点（新增或修改） |
|---|---|---|
| 立牌物品 | `astral_dice:zhao_sign`（传奇，`ASTRAL_DICE_LEGENDARY`） | §2.1 |
| 立牌类 | `item/sign/ZhaoSignItem.java`（`extends BaseSignItem`） | §3.1 |
| 立牌主动动作 id | `zhao_blessing`（`TargetSelectionRegistry` 键；同时是 actionbar 键 `msg.astral_dice.target_select.skill.zhao_blessing` 的来源） | §3.2 |
| 白泽赐福效果 | `astral_dice:zhao_blessing`（`MobEffect`，类 `effect/ZhaoBlessingEffect.java`） | §2.4 |
| 厄运效果 | `astral_dice:misfortune`（`MobEffect`，类 `effect/MisfortuneEffect.java`） | §2.4 |
| 福卡物品 | `astral_dice:fu_card`（类 `item/card/FuCardItem.java`） | §3.1 |
| 祸卡物品 | `astral_dice:huo_card`（类 `item/card/HuoCardItem.java`） | §3.1 |
| 福卡攻击动作 id | `fu_card` | §3.2 |
| 祸卡攻击动作 id | `huo_card` | §3.2 |
| 立牌贴图 | `assets/astral_dice/textures/item/zhao_sign.png` | §2.3 |
| 白泽赐福贴图 | `assets/astral_dice/textures/mob_effect/zhao_blessing.png`（= 立牌图，复用） | §2.3 |
| 福卡贴图 | `assets/astral_dice/textures/item/fu_card.png` | §2.3 |
| 祸卡贴图 | `assets/astral_dice/textures/item/huo_card.png` | §2.3 |
| 厄运贴图 | `assets/astral_dice/textures/mob_effect/misfortune.png` | §2.3 |
| 养精蓄锐 | **复用既有附件 `fen_recharge`**（无新效果、无新图标） | §2.5 |
| 玩家可见名（中/英） | 风水师立牌 / Suggested EN = **`Fengshui Master Sign`**（沿既有 `Boss Sign`=大当家 / `Game Master Sign`=游戏大师 的“称号 + Sign”风格） | §2.6 |
| 符卡名（中/英） | 符卡-福 / `Blessing Talisman`；符卡-祸 / `Misfortune Talisman` | §2.6 |

**命名纪律核对**（用户裁决「风水师立牌全局命名 = `zhao`」）：立牌及其派生的一切内部 id 一律 `zhao` 词干
（先例：大当家 `fen_sign` → 效果 `fen_frenzy`、附件 `fen_recharge`、锁定标记 `astral_dice:fen_sign`
见 `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/FenSignItem.java:89`、
`component/ModAttachments.java:858`，以及游戏大师 `ren_sign` → `ren_shield`/`ren_counter`）。

---

## 2. 注册点清单（物品 / 立牌 / 卡牌 / 效果 / 附件 / 语言键 / 贴图 / 创造栏 / 配方 / 标签）

### 2.1 物品注册（`item/ModItems.java`）

| 事实 | 证据 |
|---|---|
| 物品注册器与注册入口 | `item/ModItems.java:101`（`DeferredRegister.Items ITEMS`）、`:887-889`（`registerItem(String, Supplier)`） |
| 立牌物品范例（大当家） | `item/ModItems.java:857-861`（`registerItem("fen_sign", () -> new FenSignItem(new Item.Properties().stacksTo(1).rarity(Rarity.<…>)))`） |
| 立牌物品范例（游戏大师，传奇=UNCOMMON） | `item/ModItems.java:882-885` |
| 传奇 = `ASTRAL_DICE_LEGENDARY`（金）的官方口径 | `item/ModItems.java` 顶部注释表（2026-09-25 起：白=COMMON / 浅蓝=稀有 / 粉紫=史诗 / 金=传奇 / 亮红=巅峰，权威在前置库 `item/Rarity`） |
| 效果牌物品范例（专属牌） | `item/ModItems.java:796-801`（`effect_card_living_page` → `LivingPageItem`）、`:808-812`（`effect_card_fate_guidance`） |
| 卡牌判定不是按名字前缀，而是按标签 | `item/ModItems.java:103-108`（`COMBAT_CARDS_TAG`/`EFFECT_CARDS_TAG`）、`:892-893`（`isCardItem` = `stack.is(...)`） |

> **落点**：`zhao_sign` 追加在 `:885`（`REN_SIGN` 块）之后；`fu_card`/`huo_card` 追加在 `:855`（`EXPRESS_DELIVERY` 块）之后。
> 若把两张符卡注册为 `fu_card`/`huo_card`（用户裁决），**不违反任何既有约定**：仓库中不存在以 `effect_card_` 前缀做分支的代码
> （全仓 `"effect_card_` 只出现在 `ModItems` 的物品 id 字符串与附件键名中，见 `item/ModItems.java:307-851`、`component/ModAttachments.java:37/46/79`）。

### 2.2 立牌/卡牌类模板

| 事实 | 证据 |
|---|---|
| 立牌基类 | `item/sign/BaseSignItem.java:24`；装备槽限定 `stand` 于 `:33`；主动技能入口 `performSkill` `:80`；**目标选择器门控**在 `:113-120`（`selectorActionId() != null` ⇒ 只开会话并 return）；确认后恢复点 `resumeGatedActiveSkill` `:179-193`；主动冷却/锁定写入 `:136-151` |
| 立牌最小实现（选择器类） | `item/sign/LuluSignItem.java:62`（`ACTION_ID`）、`:73-140`（`HealingSlimeAction implements TargetSelectionAction, SelfTargetable`）、`:142-144`（`static { TargetSelectionRegistry.register(new HealingSlimeAction()); }`）、`:153-156`（`selectorActionId()`） |
| 立牌被动钩子 | `item/sign/BaseSignItem.java:415-423`（`onKill`/`onHurt`）、`:426-449`（分发）、`:51-52`（`onCurioTick`）、`:406-407`（`clearSignData`） |
| 卡牌基类与出牌流程 | `item/card/BaseEffectCardItem.java:75`（类）、`:82-84`（`isExclusive()`）、`:164-166`（`selectorActionId()`）、`:426-474`（`tryUseCard`：专属校验 → 出牌锁 → 施效 → 出牌登记 → 各类钩子） |
| 卡牌目标选择器注册入口 | `item/card/BaseEffectCardItem.java:174-204`（三参/四参重载，内部 `TargetSelectionRegistry.register(new SelectorAction(...))`）、`:224-284`（`SelectorAction implements TargetSelectionAction, SelfTargetable, HoldToSelect`）、`:311-320`（`tickHeldSelector`：**主手手持即开局** —— 该「**仅主手生效**」口径**已由 `t36` 定案**，四条代码证据见 **§14.11**） |
| 专属效果牌范例（含选择器 + 专属绑定） | `item/card/LivingPageItem.java:15-21`（`ACTION_ID="living_page"` + `registerSelectorAction(ACTION_ID, TargetType.ENEMY, false, 32.0D)`）、`:32-40`（`cardTypeId()`/`selectorActionId()`/`isExclusive()`）、`:42-50`（`applyEffect` → `ExclusiveCardUtil.bindIfAbsent`） |

### 2.3 贴图落点与「images 中文名 → assets 路径」既有映射

贴图根：`neoforge-1.21.1/src/main/resources/assets/astral_dice/textures/`（`forge-1.20.1` 同构）。
**效果图标路径规则**：`textures/mob_effect/<效果注册路径>.png`（由注册 id 推导）。三处证据链（以「反击」为例）：

- 效果注册 id = `ren_counter`：`effect/ModEffects.java:157-158`；
- 贴图存在且**与源图逐字节相同**：`images/反击.png` sha256 `D71A7CB28C8799A6…` == `assets/astral_dice/textures/mob_effect/ren_counter.png`；
- 语言键 = `effect.astral_dice.ren_counter`：`assets/astral_dice/lang/zh_cn.json:650`（`effect.astral_dice.<id>` 规则）。

**既有映射先例（SHA-256 逐字节比对得出，非目视）**：

| `images/<中文名>.png` | assets 落点 | 备注 |
|---|---|---|
| `images/反击.png`（sha256 `D71A7CB2…`） | `textures/mob_effect/ren_counter.png` | 效果图标先例；`ModEffects.java:156` 注释亦自述「图标 = images/反击.png」 |
| `images/大当家立牌.png`（sha256 `7F067E67…`） | `textures/item/fen_sign.png` **与** `textures/mob_effect/fen_frenzy.png`（两处同一份字节） | **「立牌图标复用为该立牌主动效果图标」的现成先例**，正是本需求「白泽赐福复用风水师立牌图标」要照做的形状 |
| `images/游戏大师立牌.png`（sha256 `502197D3…`） | `textures/item/ren_sign.png`、`textures/mob_effect/ren_shield.png`、`textures/gui/head_player_sign.png` | 一图多落点 |
| `images/标记喷罐.png` | `textures/item/marker_sprayer_chip.png`；`images/标记.png` → `textures/mob_effect/marked.png` | 物品图/效果图分落点 |

**本批图标逐一核对（`Test-Path` + SHA-256）**：

| 需求图标 | `images/` 存在性 | 目标落点 | 处置（冻结） |
|---|---|---|---|
| `images/风水师立牌.png` | ✅ 存在（sha256 `D123BE3D…`，当前 assets 下无同字节文件） | `textures/item/zhao_sign.png` **+** `textures/mob_effect/zhao_blessing.png` | 复制两份（照 `images/大当家立牌.png` 先例） |
| `images/符卡-福.png` | ✅ 存在（sha256 `62E303DD…`） | `textures/item/fu_card.png` | 复制 |
| `images/符卡-祸.png` | ✅ 存在（sha256 `83D43CEB…`） | `textures/item/huo_card.png` | 复制 |
| `images/厄运.png` | ✅ 存在（sha256 `3619EABF…`） | `textures/mob_effect/misfortune.png` | 复制（`misfortune` = 厄运效果的注册 id，路径规则见本节开头） |
| `images/白泽赐福.png` | ❌ **不存在** | — | 按需求「白泽赐福复用风水师立牌图标」⇒ 用 `images/风水师立牌.png` 的第二份副本，**不新建源图** |
| `images/养精蓄锐.png` | ❌ **不存在** | — | 养精蓄锐是既有**附件计数**（`component/ModAttachments.java:857-861`，上限 `FenSignItem.MAX_RECHARGE`=`item/sign/FenSignItem.java:37`），**没有效果实例、没有 HUD 图标**，故本批**不需要**该图标 |

> 「白泽赐福／养精蓄锐 图标缺失」的候选处置（三者并列，**不擅自把某一个当结论**）：
> (a) 白泽赐福**复用**风水师立牌图（需求已明示，推荐且已冻结）；
> (b) 若用户后续要求独立图标 ⇒ 需用户补图 `images/白泽赐福.png`；
> (c) 养精蓄锐若将来要做成可见效果 ⇒ 需用户补图 `images/养精蓄锐.png` 并把附件计数镜像为效果层数（本批**不做**：需求只要求「给 1 层养精蓄锐」，而该层数的既有可见载体是立牌 tooltip `tooltip.astral_dice.sign.fen_recharge`，见 `lang/zh_cn.json:598`）。

### 2.4 效果注册（`effect/ModEffects.java`）

| 事实 | 证据 |
|---|---|
| 效果注册器 | `effect/ModEffects.java:30-31`；条目范例 `:112-113`（`fen_frenzy`）、`:153-158`（`ren_shield` / `ren_counter`） |
| 「本模组全部效果只读视图」自动纳入新效果 | `:160-168`（`ALL = EFFECTS.getEntries()`，活视图 ⇒ `/astralparty cleareffect` 自动覆盖新效果，无需另改清单） |
| **新效果必须落在本模组包内** | `neoforge-1.21.1/.../effect/` 下已有本地效果类 11 个（`MisfortuneEffect.java`/`ZhaoBlessingEffect.java` 将加入此目录）；同名效果类在库 `starenginelib/effect/` 内的 17 个（`ModEffects.java:9-26` 的 import）**属另一个仓库**，本批不得动库 |

### 2.5 附件注册（`component/ModAttachments.java`，1.21.1 侧）

| 事实 | 证据 |
|---|---|
| 附件注册器 | `component/ModAttachments.java:20-21`（`DeferredRegister<AttachmentType<?>>`） |
| `.serialize()` + `.sync()` 形态 | `:23-27`（`player_starlight`，`serialize(Codec.INT).sync(ByteBufCodecs.INT)`） |
| 只看服务端、不同步的形态 | `:30-33`（`eight_sided_roll_accum`，仅 `serialize`）、`:435`（注释：服务端专用键一律不 `.sync()`） |
| **养精蓄锐（既有，本批复用，不得新建）** | `:854-877`（`FEN_RECHARGE`：`register("fen_recharge", …serialize(Codec.INT).sync(ByteBufCodecs.INT))` + `getFenRecharge`/`setFenRecharge`）、上限 `item/sign/FenSignItem.java:37`（`MAX_RECHARGE=5`）、**全部消费点（双侧枚举）**：攻击力 +2 `combat/DiceCombatModifiers.java:379-387`、防御折算护甲 `item/sign/FenSignItem.java:160-162`（`setDefenseArmorBonus(player, "fen_def_armor", …)`）、tooltip 计数 `event/ModTooltipHandler.java:1250-1252`、增减入口 `item/sign/FenSignItem.java:107-112`（治疗类效果牌 +1）/`:126-139`（赐福触发 -1 或满层转溅射）/`:158-175`（1 分钟未触发赐福 +1） |
| `.copyOnDeath()` 全仓仅 3 个键 | `:172-176`（`komachi_damage_bonus`）、`:276-280`（`rin_pages`）、`:948-951`（`guide_book_given`）；口径见证 `event/PlayerLifecycleHandler.java:133-139`（S4-C6 裁决：非 copyOnDeath 键在重生新实体上本就是默认值，禁止逐项清零） |

**1.20.1 侧附件形态（`forge-1.20.1/.../component/ModAttachments.java`）**：
`AttachedDataKey.builder("fen_recharge", Codec.INT, () -> 0).sync().build()` + `register(...)` 登记进 `SYNCED_KEYS`
（`forge-1.20.1/.../component/ModAttachments.java:25`（`SYNCED_KEYS`）、`:94-95`（`healing_points`，`.sync()`）、`:764-765`（`fen_recharge`，`.sync()`））。
⇒ **两条线的新键都要照抄“同一语义、平台各自的注册形状”**：1.21.1 用 `AttachmentType#sync`，1.20.1 用 `.sync()` 并入 `SYNCED_KEYS`。

### 2.6 语言键（`assets/astral_dice/lang/zh_cn.json` + `en_us.json`，每子项目各一份）

| 键类别 | 既有范例（zh_cn.json 行号） | 本批新增键（冻结） |
|---|---|---|
| 物品名 | `:326` `"item.astral_dice.fen_sign": "大当家立牌"`；英文 `en_us.json:326` `"Boss Sign"` | `item.astral_dice.zhao_sign` = 风水师立牌 / `Fengshui Master Sign`；`item.astral_dice.fu_card` = 符卡-福 / `Blessing Talisman`；`item.astral_dice.huo_card` = 符卡-祸 / `Misfortune Talisman` |
| 效果名（由 id 推导） | `:650` `"effect.astral_dice.ren_counter": "反击"`、`:651` `"effect.astral_dice.ren_counter.description"` | `effect.astral_dice.zhao_blessing`(+`.description`)、`effect.astral_dice.misfortune`(+`.description`) |
| 立牌 tooltip | `:596-598`（`tooltip.astral_dice.sign.fen_active` / `_passive` / `_recharge`） | `tooltip.astral_dice.sign.zhao_active` / `_passive`；符卡 tooltip `tooltip.astral_dice.fu_card` / `_huo_card` |
| 目标选择器技能名 | `:449` `"msg.astral_dice.target_select.skill.moses_apply_broken"`（**动作 id 即键尾**） | `msg.astral_dice.target_select.skill.zhao_blessing` / `.fu_card` / `.huo_card` |
| 选择器通用提示 | `:424-437`（`msg.astral_dice.target_select.*`、`hud.astral_dice.target_select.tag.*` `:266-271`） | 无需新增（复用） |
| 手册文本 | `:102-103`（`astral_dice.guide.entry.fen_sign.1/2`） | `astral_dice.guide.entry.zhao_sign.{1..n}`、`astral_dice.guide.entry.fu_card.1`/`huo_card.1` |

**同步纪律（有工具证据，不靠猜）**：`tools/check_lang_sync.ps1` 的比对单位是**每个子项目自己的 zh ↔ en 键集**
（`:418-430` 计算 `missingInEn` / `extraInEn`；`:499` 依次扫描三线的 lang 目录）
⇒ **新增中英键即可，不需要也不允许为对齐 key 集去改被冻结的 26.1.2**。
另外 `lang/*.json` 属 `TESTING-SPEC.md:27` 的「非核心」范围（仅需构建 + `check_lang_sync`）。

### 2.7 创造栏（`init/ModCreativeTabs.java`）

| 事实 | 证据 |
|---|---|
| 创造栏注册与 `displayItems` 入口 | `init/ModCreativeTabs.java:22-24`、`:26-30` |
| 立牌段（按稀有度 稀→史→传 排列） | `init/ModCreativeTabs.java:80` 起（`:80` 注释「立牌(按稀有度低→高)」，其后按 RARE/EPIC/UNCOMMON 依次 `output.accept(...)`） |
| 效果牌段 | `:63-77`（含专属牌 `FATE_GUIDANCE_CARD`） |

> **落点**：`zhao_sign`（传奇）追加到立牌段 `UNCOMMON` 分组（`REN_SIGN` 之后）；`fu_card`/`huo_card` 追加到功能效果牌段。

### 2.8 配方（`datagen/ModRecipeProvider.java`，datagen 产物落 `src/generated/resources/data/astral_dice/recipe/`）

| 事实 | 证据 |
|---|---|
| 立牌配方统一以 `BLANK_SIGN` 为材料 | `datagen/ModRecipeProvider.java:100`（`blank_sign` 本体）、`:1191-1200`（`FEN_SIGN`：`.define('E', ModItems.BLANK_SIGN.get())` + `.unlockedBy("has_blank_sign", …)`） |
| 其余立牌同构 | `:111-266`（lulu/parunan/jasmine/mimi/komachi/fanny/rin/haiqing/papara/padman/misaki/bonnie）、`:1204-1254`（nancy_lu/moses/pandaman/ren） |
| datagen 产物现状 | 生成配方 109 份（`src/generated/resources/data/astral_dice/recipe/`）；手写配方 14 份（`src/main/resources/data/astral_dice/recipe/`，仅骰子 + 手册 + 可疑 stew） |

> **落点**：在 `ModRecipeProvider` 末尾（`:1254` 之后）追加 `zhao_sign` 配方（材料 `BLANK_SIGN`，形状自定，须与已有立牌风格一致并带 `unlockedBy("has_blank_sign")`）。
> **物品模型不手写**：`datagen/ModItemModelProvider.java:15-141` 的 `basicItem(...)` 逐项生成
> （立牌范例 `:88` `basicItem(ModItems.FEN_SIGN.get())`），产物 `src/generated/resources/assets/astral_dice/models/item/<id>.json`（现存 124 份，含 `fen_sign.json`）
> ⇒ 新物品须在 `ModItemModelProvider` 追加 3 行并**由 captain 重跑 datagen**（本批成员禁跑 gradle）。

### 2.9 标签与赏金数据（data 包）

| 文件 | 现状 | 本批需要 |
|---|---|---|
| `data/curios/tags/item/stand.json` | 17 个立牌 id 白名单（含 `astral_dice:fen_sign`） | 追加 `astral_dice:zhao_sign`（否则 `BaseSignItem.canEquip` 直接拒绝，见 `item/sign/BaseSignItem.java:31-42`） |
| `data/astral_dice/tags/item/signs.json` | 18 项（含 `blank_sign`） | 追加 `astral_dice:zhao_sign` |
| `data/astral_dice/tags/item/effect_cards.json` | 15 张效果牌 | 追加 `astral_dice:fu_card`、`astral_dice:huo_card`（`ModItems.isCardItem` 的判定源：`item/ModItems.java:106-108/892-893`） |
| `data/astral_dice/tags/item/is_exclusive.json` | 2 项（`effect_card_living_page` / `effect_card_fate_guidance`） | 追加两张符卡以保持数据侧口径一致。**注意**：全仓 Java 代码对该标签**零引用**（`is_exclusive` 在 `*.java` 中 0 命中）⇒ 它只是数据侧台账，**不构成**专属判定的真值；真值见 §7 |
| `data/bountiful/bounty_pools/bountiful/astral_rews.json` | 赏金奖励池（传奇品质立牌/筹码不入池，见 `item/ModItems.java:117-122` 的注释口径） | `zhao_sign` 为**传奇** ⇒ 按既有口径**不入** `astral_rews`；若用户要求入池则属需求变更，须另裁 |

> ⚠️ **两线目录名不同（v120 必须按本线路径改，别照抄 v121）**：1.21.1 的标签目录是
> `src/main/resources/data/<ns>/tags/**item**/<tag>.json`（NeoForge 1.21 用 `item/`），
> 而 forge-1.20.1 是 `…/tags/**items**/<tag>.json`（Forge 1.20.1 用 `items/`）。
> 实测证据：`neoforge-1.21.1/src/main/resources/data/curios/tags/item/stand.json` 与
> `forge-1.20.1/src/main/resources/data/curios/tags/items/stand.json`（同一条例、两个目录名）。
> 另：Curios 槽位定义 1.21.1 侧在 `data/astral_dice/curios/slots/stand.json`（`{"size":1,"validators":["curios:tag"]}`），
> 新立牌**不需要**改它（槽位是共享的 `stand` 槽），只需进 `curios/tags/*/stand.json` 白名单。
>
> ✅ **本批冻结时点的实现对齐抽样（只读，仅作早期信号，不作为验收依据）**：两线的
> `data/curios/tags/*/stand.json`、`data/astral_dice/tags/*/signs.json`、`…/effect_cards.json`
> 均已含 `zhao_sign` / `fu_card` / `huo_card` 对应项，`lang/zh_cn.json` 含 `zhao_sign`，
> `init/ModCreativeTabs.java` 含 `ZHAO_SIGN`（`Select-String` 抽样，非逐条评审）。

### 2.10 手册（Patchouli）

| 事实 | 证据 |
|---|---|
| 手册条目目录（**只有 `en_us` 一份 JSON，文本靠语言键，故中文无需第二份 JSON**） | `assets/astral_dice/patchouli_books/astral_guide/en_us/entries/signs/fen_sign.json`（`name`/`text` 全为语言键，`"name": "item.astral_dice.fen_sign"`、`"text": "astral_dice.guide.entry.fen_sign.1"`、`"recipe": "astral_dice:fen_sign"`）；`zh_cn` 目录不存在（`Test-Path …/astral_guide/zh_cn/entries` = False） |
| 现有立牌条目文件 | `…/en_us/entries/signs/` 下 18 个（含 `fen_sign.json`、`ren_sign.json`） |

> **落点**：新增 `…/en_us/entries/signs/zhao_sign.json`（`sortnum` 接在既有序列后）与
> `…/en_us/entries/cards_effect/fu_card.json`、`huo_card.json`，文本一律引用 §2.6 的语言键。

---

## 3. 类与动作 id 的落点细化

### 3.1 三个新类

- `item/sign/ZhaoSignItem.java`：`extends BaseSignItem`；覆写
  - `selectorActionId()` → `"zhao_blessing"`（门控：`item/sign/BaseSignItem.java:113-120`）；
  - `static { TargetSelectionRegistry.register(new ZhaoBlessingAction()); }`（形状照 `item/sign/LuluSignItem.java:142-144`）；
  - 被动「福祸相倚」「完美帮手」的静态入口（由 `combat/DiceCombatEvents` 与 `ZhaoBlessingAction#apply` 调用）；
  - `isEquipped(Player)`（照 `item/sign/FenSignItem.java:99-104` 的 `CuriosApi … findFirstCurio(s -> s.is(ModItems.ZHAO_SIGN.get()))`）。
- `item/card/FuCardItem.java` / `item/card/HuoCardItem.java`：`extends BaseEffectCardItem`；覆写
  `cardTypeId()`（=`"fu_card"`/`"huo_card"`，须同时登记进 `BaseEffectCardItem.cardByTypeId` 的 switch，见 `item/card/BaseEffectCardItem.java:93-112`，
  否则忍者/魔法秘典/魔法箭袋的复制返还回退成「王之力」）、`selectorActionId()`、`isExclusive()`→`true`、`applyEffect(...)`。
- `effect/ZhaoBlessingEffect.java` / `effect/MisfortuneEffect.java`：`MobEffect` 子类（颜色/是否瞬时等自定；**必须是纯标记类**，
  行为一律走附件 + 玩家级 tick，避免效果实例持有状态）。可比范例：`effect/RenCounterEffect.java`（本地）、
  `effect/BlueCurseEffect.java`（本地）。

### 3.2 动作 id 注册点（唯一入口）

| 事实 | 证据 |
|---|---|
| 注册表本体（共享库） | `starengine_lib/common/src/main/java/com/merlinkitsune/starenginelib/target/TargetSelectionRegistry.java:22`（`register(TargetSelectionAction)`）、`:33`（`get(String)`） |
| 卡牌侧注册封装 | `item/card/BaseEffectCardItem.java:174-204`（`registerSelectorAction(...)` 三个重载） |
| 立牌侧注册范例（静态块） | `item/sign/LuluSignItem.java:142-144`；`item/sign/BonnieSignItem.java:57`、`HaiqingSignItem.java:53`、`MosesSignItem.java:50` |
| 动作 id → 会话键 | `target/TargetSelectionManager.java:162`（`start(ServerPlayer, String actionId)`）、`:199`（`confirm(...)`，内部 `TargetSelectionRegistry.get(actionId)` 于 `:242`/`:312`） |
| **会话唤起门控 = 仅主手**（**`t36` 定案**） | 自动开会话取主手 `item/card/BaseEffectCardItem.java:317`（1.20.1 `:315`）；卡牌↔动作匹配 `:266`（1.20.1 `:264`，`heldCardMatches(player.getMainHandItem(), actionId)`）；客户端 `client/TargetSelectionClient.java:278` + `:643`（1.20.1 `:646`）；`client/EffectCardUseGuard.java:71` ⇒ **副手持有不唤起选择器**（直接推论），**本轮零代码改动** —— 全文见 **§14.11** |

**本批 3 个动作 id（冻结）**：

| 动作 id | `targetType()` | `allowSelf()` | 半径 | 依据 |
|---|---|---|---|---|
| `zhao_blessing` | `TargetType.PLAYER` | `true` | 配置统一值 `GameplayConstants.TARGET_SELECT_RADIUS`（`GameplayConstants.java:84` = 16） | 「目标选择器可选玩家或自身」，同 `LuluSignItem.HealingSlimeAction`（`:80-87`） |
| `fu_card` | `TargetType.PLAYER` | `true` | 同上 | 「可选玩家（不限队伍）或自身」；`TargetType.PLAYER` 的库语义 = 玩家且非自身（`starengine_lib/common/.../target/TargetType.java:14-19`），`allowSelf` 走 `target/SelectorTargets.java:65-68` 放行自选 |
| `huo_card` | `TargetType.ENEMY_OR_RIVAL` | `false` | 同上 | 「只能选敌对目标（含非同队玩家）」⇒ 本模组唯一同时覆盖「敌对生物 ∪ 已被激怒中立生物 ∪ 非队友玩家」的类型（`target/SelectorTargets.java:49-51` + `TargetType.java:40-51`）；既有先例 `item/sign/HaiqingSignItem.java:61`、`item/sign/BonnieSignItem.java:65` |

⚠️ **不得**用 `TargetType.ENEMY` 给 `huo_card`：库的 `ENEMY` 只判 `instanceof Enemy`，本模组口径在
`target/SelectorTargets.java:46-48` 被收口为 `HostileTargets.isHostile(target)`，**且明确不含玩家**
（`SelectorTargets.java:24`）⇒ 不满足「含非同队玩家」。

### 3.3 符卡-祸「禁止丢弃」的落点与已登记边界（**裁决① 已定**，原文见 §14.1）

| 项 | 实现要求（冻结） | 证据 |
|---|---|---|
| 主动丢弃（Q 键 / 拖出到世界 / 背包丢弃） | 覆写 `Item#onDroppedByPlayer(ItemStack, Player)` 返回 **`false`**（= 阻止物品离开玩家背包） | 1.21.1：`net/neoforged/neoforge/common/extensions/IItemExtension.java:89`（javadoc 原文「returning false … will prevent the item from being removed from the players inventory」）；1.20.1：`net/minecraftforge/common/extensions/IForgeItem.java:74` |
| 装进潜影盒 / 收纳袋（Bundle） | 覆写 `canFitInsideContainerItems()` 返回 **`false`** | 1.21.1 原版 `net/minecraft/world/item/Item.java:422`（NeoForge 已改由 `IItemStackExtension#canFitInsideContainerItems()` 消费，见 `:417-418` 的 `@deprecated` 注）；1.20.1 原版 `Item.java:452` |
| **普通容器（箱 / 桶 / 漏斗 / 末影箱 / 交易槽 等）** | **放行**，作为**已登记边界**（不实现、不掩盖） | 原版唯一入口是 `Container#setItem`，物品级**无钩子**；**明确不做** `Container#setItem` mixin，理由见 §14.1 |
| 玩家可见文案建议（一条，供 lang/scribe 采用） | 符卡-祸 tooltip 增补：「持有期间无法丢弃；存入容器后不再计入厄运」 | 需求原文「持有期间禁止丢弃」+ §9.2 的「计数 = 主物品栏 **+ 副手** `huo_card` 张数和」口径（**本轮 `t33` 裁决更正**：旧「计数 = 主物品栏」口径已作废，见 §1 头部修订记录的 `t33` 条；主栏先例 `item/chip/SmartWatchChipItem.java:57-66`） |

> ⚠️ **由裁决①必然导出的口径推论（如实登记，交 verifier 实证）**：因为「持有」的判定源是**主物品栏 + 副手**（§9.2；
> **本轮 `t33` 用户裁决已把副手「计入」—— 旧的「计数 = 主物品栏」口径作废**），
> 把符卡-祸放进普通容器会让它**既不产生周期伤害、也不计入厄运层数** —— 这是「放行普通容器」的必然结果，
> **不是实现缺陷**（⚠️ 容器 ≠ 副手：**放进副手照常计入**，见 §9.2）。若用户不接受该推论，需改选更强口径（全背包 + 末影箱 + 最近访问容器计数）或回到容器封堵，
> 两者都属后续批次。实证要求见 **§14.5 Q4**。

---

## 4. 白泽赐福（主动）时序状态机

### 4.1 状态量

| 状态量 | 类型 | 语义 |
|---|---|---|
| 效果 `astral_dice:zhao_blessing` | `MobEffectInstance` | **唯一可见载体**（图标/剩余时长）。时长由玩家级 tick 持续刷新（形状照 `item/HealingManager.java:216-250` 的 `updateEffect`） |
| 附件 `zhao_blessing_active` | `Boolean` | 是否生效中（真值） |
| 附件 `zhao_blessing_skip_cycles` | `Integer` | 还需「跳过」几次骰神赐福结束（0 或 1） |
| 附件 `zhao_prev_blessing` | `Boolean` | 上一拍的骰神赐福存在性（下降沿检测用） |
| 附件 `zhao_overflow_bonus` | `Integer` | 白泽赐福期间累计的溢出治疗量（= 攻击力加成），移除时清零（§5） |

### 4.2 骰神赐福的既有生命周期（判定/施加/结束入口）

| 事件 | 入口（文件:行号） |
|---|---|
| 施加（攻击者触发） | `combat/DiceCombatEvents.java:250-258`（守卫 `!player.hasEffect(ModEffects.DICE_BLESSING)` + `addEffect(new MobEffectInstance(ModEffects.DICE_BLESSING, GameplayConstants.DICE_BLESSING_DURATION_TICKS, 0, false, false, true))`） |
| 施加（被攻击方同时持骰） | `combat/DiceCombatEvents.java:264-276` |
| 时长常量 | `GameplayConstants.java:80/86`（60 秒 = 1200 tick；可由配置改写 `:125`） |
| **自然结束钩子** | `combat/DiceCombatEvents.java:804-812`（`@SubscribeEvent onDiceBlessingExpired(MobEffectEvent.Expired)`，仅对 `DICE_BLESSING` 生效） |
| 系统外移除 | `event/PlayerLifecycleHandler.java:177`（死亡）、`:207`（**登录/重连强制移除**，防跨会话残留）；统一移除通道 `starenginelib.event.ModEffectRemoval`（用点 `PlayerLifecycleHandler.java:207`、`item/HealingManager.java:90/220/229`） |
| 边沿检测既有先例 | `item/HealingManager.java:199-206`（`prevBlessing && !hasBlessing` ⇒ 判定「赐福周期结束」，且**刻意不订阅 `Expired`**，因为效果被外力移除时 `Expired` 不触发） |

### 4.3 施加（`ZhaoBlessingAction#apply(ServerPlayer caster, LivingEntity target)`）

前置（由 `BaseEffectCardItem`/`BaseSignItem` 门控与 `TargetSelectionManager` 保证）：会话已确认、目标为玩家或自身。

| 步骤 | 行为 | 依据 |
|---|---|---|
| A1 | **判定分支**：读 `target.hasEffect(ModEffects.DICE_BLESSING)` | `combat/DiceCombatEvents.java:250` 的同款读法 |
| A2 | 分支①（**未处于**骰神赐福）：`skip_cycles = 0`；分支②（**已处于**骰神赐福）：`skip_cycles = 1`（跳过当前这次） | 需求原文 |
| A3 | 置 `zhao_blessing_active = true`，`zhao_prev_blessing = target.hasEffect(DICE_BLESSING)`（分支②时记为 `true`，避免把「当前这次赐福的结束」误当成「第一次结束」） | — |
| A4 | 施加/刷新 `zhao_blessing` 效果（时长 ≥ 常驻值，由 tick 续期） | `item/HealingManager.java:216-250` 同形 |
| A5 | **施法者副作用（无论目标是谁，一律执行）**：① 施法者获得 1 张 `fu_card`（绑定施法者，§7）；② 把施法者持有的**全部** `huo_card` 就地转换为 `fu_card`（张数不变、绑定施法者） | 需求原文 + t2 验收 |
| A6 | **完美帮手**：若**目标**装备大当家立牌（`CuriosApi … s.is(ModItems.FEN_SIGN.get())`）⇒ 目标 `fen_recharge +1`（不超 `FenSignItem.MAX_RECHARGE`） | 复用既有附件：`component/ModAttachments.java:857-877`、上限 `item/sign/FenSignItem.java:37`、同形先例 `item/sign/FenSignItem.java:107-112`（`onHealingCardUsed`） |
| A7 | 冷却/电流核心（门控类主动必做）| 照 `item/sign/LuluSignItem.java:120-130`：`WeirdDiceHandler.signCooldownTicks(player)` → `setSignActiveCooldownEnd` + `setSignActiveMaxCooldown` + `CurrentCoreChipItem.onActiveSkillUsed` |
| A8 | 反馈 actionbar | `msg.astral_dice.target_select.skill.zhao_blessing` 等，照 `item/sign/LuluSignItem.java:133-135` |

### 4.4 结束（唯一判定点 = 玩家级 tick 的下降沿检测）

在 `item/ZhaoSignItem.tick(Player)`（由 `event/PlayerTickEvents.java:128-157` 的 `onPlayerTick(PlayerTickEvent.Post)` 调用，与
`HealingManager.tick`（`:132`）、`FenSignItem.tick`（`:154`）并列）中执行：

```
has = player.hasEffect(ModEffects.DICE_BLESSING)
prev = getZhaoPrevBlessing(player)
if (active) {
    if (prev && !has) {                      // 一次骰神赐福周期结束（自然到期 或 被外力移除，两路皆覆盖）
        if (getZhaoBlessingSkipCycles(player) > 0) setZhaoBlessingSkipCycles(player, 0);   // ⑤-a 跳过当前这次
        else removeZhaoBlessing(player);     // ④ 到期：移除效果 + 清附件 + 清溢出加成
    }
    setZhaoPrevBlessing(player, has)
    refreshZhaoBlessingEffect(player)        // 续期（同 HealingManager.updateEffect:239-249 的“已存在且充足则不重写”）
}
```

**为什么用「tick 下降沿」而不用 `MobEffectEvent.Expired`（冻结决定）**：`Expired` 在「效果被外力移除
（`removeEffect` / `ModEffectRemoval` / 死亡 / 重连清场）」时**不触发**；而下降沿检测把两条路径统一
（先例 = `item/HealingManager.java:199-206` 明说 `Expired` 不可依赖）。
若同时订阅 `Expired`（`combat/DiceCombatEvents.java:805`）再调同一个 helper，会**重复消费** skip 计数 ⇒ **禁止**。

### 4.5 状态迁移表（两个分支）

| # | 当前态 | 事件 | 迁移后 | 判定入口（文件:行号） |
|---|---|---|---|---|
| ① | 未施加 | 主动确认 | `active=true, skip=0, prev=hasBlessing(此时通常 false)` | `target/TargetSelectionManager.java:199` → 动作 `apply`（§4.3 A1-A3） |
| ② | `active, skip=0, prev=false`（施加时无赐福） | 目标触发骰神赐福 | 无变化（只记 `prev=true`） | `DiceCombatEvents.java:250-258`；我们的 tick 读 `has=true` |
| ③ | `active, skip=0, prev=true` | 该次赐福结束 | **移除白泽赐福**（效果 + 全部附件 + 溢出加成） | tick 下降沿（§4.4）；赐福侧结束入口 `DiceCombatEvents.java:804-812` |
| ④ | 施加时目标**已**有赐福 | 主动确认 | `active=true, skip=1, prev=true` | §4.3 A2/A3 |
| ⑤ | `active, skip=1, prev=true` | 当前这次赐福结束 | `skip=0`（**不**移除），继续等下一次 | tick 下降沿 |
| ⑥ | `active, skip=0, prev=true` | 下一次赐福结束 | **移除白泽赐福** | tick 下降沿 + `DiceCombatEvents.java:804-812`（同一次结束） |

### 4.6 三种边界的显式处理（冻结）

| 边界 | 处理 | 证据/理由 |
|---|---|---|
| **① 连续两次骰神赐福** | 赐福不可被「攻击」刷新：施加点有 `!player.hasEffect(DICE_BLESSING)` 守卫（`combat/DiceCombatEvents.java:250`），故一次赐福只有一次结束事件；玩家连续触发 = 两次独立周期 ⇒ 状态机天然按「一次结束 = 一次下降沿」推进。若未来出现「同实例被刷新时长」（外部 mod 把时长改长），下降沿仍只在真正结束时出现一次，**不会**多算。 | `DiceCombatEvents.java:250`、`:804-812` |
| **② 目标死亡** | 死亡清理处（`event/PlayerLifecycleHandler.java:130-183`，与 `:177` 的 `DICE_BLESSING` 同段）追加：`ModEffectRemoval.remove(player, ModEffects.ZHAO_BLESSING)`、移除 `misfortune`、并清零 `zhao_blessing_active` / `zhao_blessing_skip_cycles` / `zhao_prev_blessing` / `zhao_overflow_bonus` / `huo_card_next_damage_tick`。**注意**：这里保住的是「移除 MobEffect」与「清零有真实读取方的附件」两类**真实副作用**；其余非 copyOnDeath 键按 `:133-139` 的 S4-C6 口径**不得**逐项写默认值。 | `PlayerLifecycleHandler.java:133-139/177` |
| **③ 换维度** | **不注册任何处理器**。原版 `ServerPlayer.changeDimension` 对玩家是**同一实体的迁移**（`net/minecraft/server/level/ServerPlayer.java:887-925`：同维度走 `connection.teleport`，跨维度走 `removePlayerImmediately` + `revive` + `setServerLevel`，**不创建新实体、不触发 `PlayerEvent.Clone`**）⇒ 附件与效果原样保留，白泽赐福跨维度继续生效；若跨维度时因其它路径走了 `Clone`（`isWasDeath=false`），本批**不复制**任何 `zhao_*` 键 ⇒ 白泽赐福自然断开（无残留，效果实例也随旧实体消失）。 | `ServerPlayer.java:887-925`；`event/PlayerLifecycleHandler.java:188-196`（只在 `event.isWasDeath()` 时恢复） |
| **④ 效果被外力移除** | 由 §4.4 的下降沿统一覆盖：`ModEffectRemoval` / 其它 mod / `/effect clear` 移除赐福后，下一 tick 的 `prev=true, has=false` 即触发同一出口（skip 递减或移除白泽赐福）。**唯一的例外方向**：白泽赐福自身被外力移除（`/effect clear`）⇒ 由 tick 的自检把 `active` 复位并清溢出加成（同 §5.4 的回收路径）。 | `item/HealingManager.java:199-206`；`starenginelib.event.ModEffectRemoval`（用点 `PlayerLifecycleHandler.java:207`） |

### 4.7 重登（补充边界）

`event/PlayerLifecycleHandler.java:199-212` 已在登录时强制清除 `DICE_BLESSING`（防跨会话保留战斗状态）。
**冻结**：同一处追加 `ModEffectRemoval.remove(player, ModEffects.ZHAO_BLESSING)` 与
`zhao_blessing_active=false`、`zhao_prev_blessing=false`（清 `prev` 是防「重登被误判为一次赐福结束」），
使「白泽赐福不跨会话残留」与骰神赐福同口径。附件本身（未列入 `copyOnDeath`）在**重登**时随玩家数据保留、在**死亡**时随新实体归默认。

---

## 5. 溢出治疗 → 攻击力

### 5.1 1.21.1 侧**全部**治疗入口枚举（双侧：原版 API + 本仓调用点）

| # | 入口 | 是否覆盖「溢出」 | 证据 |
|---|---|---|---|
| 1 | **`LivingEntity.heal(float)`（原版唯一“增加生命值”API）** | ✅ **覆盖点** | `net/minecraft/world/entity/LivingEntity.java:1117-1123`：方法体首行即 `healAmount = EventHooks.onLivingHeal(this, healAmount)`（`:1118`），随后 `setHealth(f + healAmount)`（`:1122`，`setHealth` 内部 `Mth.clamp(..., 0, getMaxHealth())` `:1130-1132`） |
| 2 | 瞬时治疗效果 `minecraft:instant_health` | ✅ 经 `heal()` 覆盖 | `net/minecraft/world/effect/HealOrHarmMobEffect.java:18`（`livingEntity.heal((float)Math.max(4 << amplifier, 0))`）、`:30`（harm 分支同函数体） |
| 3 | 生命恢复效果 `minecraft:regeneration` | ✅ 经 `heal()` 覆盖 | 同 `HealOrHarmMobEffect`（Regeneration 走 `LivingEntity#heal` 周期调用） |
| 4 | 玩家自然回血（饥饿/饱和） | ✅ 经 `heal()` 覆盖 | `net/minecraft/world/entity/player/Player.java:540`（`this.heal(1.0F)`） |
| 5 | 本仓全部显式治疗调用 | ✅ 经 `heal()` 覆盖 | `combat/DiceCombatEvents.java:655`、`item/card/LuxuryFeastCardItem.java:61`、`item/card/HamburgerCardItem.java:32`、`item/card/ChocolateCakeCardItem.java:32`、`item/sign/FenSignItem.java:76`、`item/sign/PaparaSignItem.java:77`、`item/sign/PandamanSignItem.java:161`、`item/HealingManager.java:142`、`item/chip/CandyChipItem.java:38`、`item/chip/BigBowlStewChipItem.java:65`、`:72` |
| 6 | 本模组显式施加的瞬时治疗 | ✅ 经 `heal()` 覆盖 | `item/sign/LuluSignItem.java:98`、`:112`（`new MobEffectInstance(MobEffects.HEAL, 1, 0, …)`） |
| 7 | 共享前置库内的治疗 | ✅ 空集（无绕过） | `starengine_lib` 全仓 `*.java` 中 `.heal(` / `setHealth(` **0 命中**（递归 grep 结果为空） |
| 8 | **`LivingEntity.setHealth(float)` 直接写入** | ❌ **不覆盖** | 原版全部调用点（`net/minecraft/world/entity/LivingEntity.java`）：`:278`（构造期置满）、`:766`（**NBT 读档**）、`:1104`（**只降不升**：`if (getHealth() > maxHealth) setHealth(maxHealth)`）、`:1122`（heal 内）、`:1328`（不死图腾置 1.0）、`:1801`（受伤扣血）、`:1909`（`die`）。本仓 2 处：`event/EnderDiceHandler.java:177`（置 1.0F）、`event/ChipDamageHandler.java:99`（`Math.max(1.0F, getHealth())`，只升到 1）。**结论**：运行期**没有**“把血写高”的 `setHealth` 路径（`:766` 是读档、`:278` 是构造期），故不影响本需求；但这是**有据的不覆盖**，不是“全覆盖” |
| 9 | 吸收黄心 / 最大生命提升 | ❌ 不覆盖（**有意**） | 吸收与生命是两套字段；最大生命提升由属性系统处理，不经 `heal`（`LivingEntity.java:1104` 只做降档） |

### 5.2 「溢出量 = 请求治疗量 − 实际恢复量」的索取点（冻结）

**唯一挂点 = NeoForge `LivingHealEvent`**（`net/neoforged/neoforge/event/entity/living/LivingHealEvent.java`：
`class LivingHealEvent extends LivingEvent implements ICancellableEvent`，字段 `amount` 带 `getAmount()/setAmount()`，
javadoc 明示「fired whenever an Entity is healed in `LivingEntity#heal(float)`」）。计算式：

```
float before = player.getHealth();                 // 事件先于 setHealth 触发 ⇒ 读到的是治疗前值（LivingEntity.java:1118 vs :1122 的顺序）
float missing = Math.max(0F, player.getMaxHealth() - before);
float actual  = Math.min(event.getAmount(), missing);   // setHealth 内部 clamp 到 maxHealth（:1130-1132）
float overflow = Math.max(0F, event.getAmount() - actual);
if (overflow <= 0) return;                          // 溢出 ≤ 0 不加攻击力
```

**语义边界（冻结）**：
- 仅当「被治疗者 = 自身持有 `zhao_blessing` 效果的玩家」且 `zhao_blessing_active=true` 时累计；
- 优先级取 `EventPriority.LOWEST`（在 `LivingHealEvent` 上；与 `combat/DiceCombatEvents.java:872`/`:918` 用 `EventPriority.LOW` 收尾同一习惯）；
- 处理器内再判 `event.isCanceled()`（被其它处理器取消 ⇒ 不产生溢出）；
- 累计写入 `zhao_overflow_bonus`（整数化：累计 `float`，取整写入时用 `(int) Math.floor(overflow)`，把余数留在浮点累加器里，避免逐次取整丢失——**实现须把余数也存进附件**或明确采用「按次向下取整」并在报告里声明，二者择一，禁止无声丢数）。
- 1.20.1 等价物：`net/minecraftforge/event/entity/living/LivingHealEvent`（`forge-1.20.1-47.4.10-sources.jar` 内存在）+ `LivingEntity.heal` 同样先 `ForgeEventFactory.onLivingHeal`（`net/minecraft/world/entity/LivingEntity.java:…` 该 jar 内 `heal(float)` 体首行） ⇒ 两线同构。

### 5.3 攻击力加成挂点（消费）

**唯一消费点 = `DiceCombatModifiers` 攻击修饰器注册表**：`combat/DiceCombatModifiers.java:70-72`（`registerAttackModifier`）、
`:79-81`（`attackModifiers()`）、`:545` 与 `combat/DiceCombatEvents.java:434`/`:1192`（唯三消费循环）。
新增修饰器形如（照 `item/sign/NancyLuSignItem.java` 的附件驱动加成，附件读法见 `component/ModAttachments.java:686-694`）：

```
registerAttackModifier((ctx, ap) -> ap + ModAttachments.getZhaoOverflowBonus(ctx.attacker));
```

> 选择该挂点的理由（有证据）：溢出治疗是「固定点数直接加到攻击力」，与既有 `nancy_lu_active_bonus`
> （攻击力整数加成，修饰器见 `combat/DiceCombatModifiers.java:406-410`，附件读写见 `component/ModAttachments.java:686-694`）同形；
> 而防御侧另有规范（1 防御力 = 2 护甲，`combat/DiceCombatModifiers.java:50-53/163-169`），本需求不涉及。

### 5.4 回收（移除时不留残留）

| 触发 | 动作 | 依据 |
|---|---|---|
| 白泽赐福到期/被移除（§4.4 出口） | `setZhaoOverflowBonus(player, 0)` + `ModEffectRemoval.remove(player, ModEffects.ZHAO_BLESSING)` | 同 `item/sign/NancyLuSignItem.java:78-83`（到期清零 + 移除效果）与 `:90-104`（`clearSignData` 同时清附件与效果） |
| 卸下风水师立牌 | **不清**溢出加成（立牌不是加成来源，加成来自白泽赐福效果）；`clearSignData` 只清「立牌自身授予」的状态（口径见 `item/sign/NancyLuSignItem.java:92-93` 的注释） | — |
| 死亡 / 重登 | §4.6/§4.7 |

---

## 6. 骰点 1/6 挂点（「福祸相倚」）

### 6.1 全仓掷骰路径枚举（双侧：定义点 → 调用点）

| # | 掷骰入口 | 定义 | 调用点 | 是否属于本需求 |
|---|---|---|---|---|
| 1 | `rollCombatDie(Player)`（**战斗骰**，含诡异/绯红偏置 + 枪匠弱点识破最低点抬升） | `combat/DiceCombatEvents.java:949-970` | **攻击者结算** `:332`（`int baseDice = rollCombatDie(player)`）、**防守方结算** `:493`（`defenseBaseDice = rollCombatDie(targetPlayer)`）、**反击结算** `:1177`（`int baseDice = rollCombatDie(player)`） | ✅ **仅 `:332` 这一处**（见 §6.2） |
| 2 | `DiceCombatEvents.rollDice(int max)`（本地 1dN） | `:945-947` | `:407`（八面骰筹码 1d10，非战斗骰） | ❌（1d10，非 1..6 战斗骰） |
| 3 | `DiceCombatModifiers.rollDice(int max)` / `rollDice(int min,int max)`（通用掷骰，公开 API） | `combat/DiceCombatModifiers.java:181-188` | `combat/CardRegistry.java:171`、`:172`、`:207`（攻击/防御牌点数） | ❌（卡牌点数，非立牌被动要的「骰点」） |
| 4 | 闪避对骰（`ThreadLocalRandom.nextInt(1,7)`，特性开关关闭） | `combat/DiceCombatEvents.java:484-485`（`PLAYER_DODGE_ENABLED=false`，`：466-469` 注释） | 同上 | ❌（已停用路径） |
| 5 | 防御骰 | `:493` | 目标为持骰玩家时 | ❌（防守方点数） |
| 6 | 反击骰 | `:1177` | 反击（`counterDepth > 0` 链） | ❌（反击链自算伤害，且 `:1143` 注释说明该链会二次掷骰） |

### 6.2 冻结口径：以「本次攻击最终使用的攻击者战斗骰点」为判定值

- **取值位置**：`combat/DiceCombatEvents.java` 中 `baseDice` 经全部骰点类修饰后、
  `DiceCombatContext` 构造之前 —— 即**在 `:369-381`（上班族立牌「骰点为 1 → 下次必 6」块）之后、`:428-431` 之前**。
  理由（有证据）：`baseDice` 在 `:332` 生成后仍被 `:358-367`（护法立牌星级追加）与 `:369-381`（上班族强制 6）改写，
  而 `:428-431` 把它作为**本场结算的骰点**写入上下文，`:437`/`:454` 之后用于伤害 —— 只有此处才是玩家看到的「本次骰点」。
- **不含**：防守骰（`:493`）、反击骰（`:1177`）、1d10（`:407`）、卡牌点数（`CardRegistry`）。
- **要武器攻击 + 赐福结算才触发**吗？**不要求**：需求说的是「骰点 1/6 ⇒ 得牌」，而骰点只在骰战结算路径存在，
  该路径本身已由 `isMeleeWeaponAttack(player)`（`:221`）+ 持骰（`:226-233`）+ 赐福（`:322`）三道守卫限定
  ⇒ 挂点天然落在「持骰近战攻击的骰战结算」内，无需再自加守卫（不得另写一套判定）。
- **前置副作用顺序**：置牌逻辑必须放在 `fenSplashArmed` 计算之后（`:315`）不构成依赖，但**必须放在**
  `:322` 的 `if (!player.hasEffect(DICE_BLESSING)) return;` **之前还是之后**？**冻结为「之后、`:428` 之前」**：
  理由 = 1/6 只在「赐福生效的骰战结算」里才有意义；放在 `:322` 之后可保证与「本次结算同步进行」，
  且不会在结算被 `return` 掉（如 `:323` 无骰、`:830-831` 无骰）时白送牌。
- **一次结算一次判定**：同一挥击命中多目标会多次进入事件（`:207-211`/`:246` 注释说明「同一挥击命中多目标也仅触发一次」只针对*赐福触发*）
  ⇒ 冻结：**以「本地结算已判定过」的实例内标记防重**（同 `DiceCombatModifiers.instanceVictim` 的「本次伤害实例」写法，`:105-110`），
  禁止把标记写进附件（跨实例残留）。

---

## 7. 专属绑定策略（符卡-福 / 符卡-祸）

### 7.1 「获得即绑定」与「首次使用绑定」两种既有语义的证据

| 语义 | 证据 | 现状用途 |
|---|---|---|
| **首次使用绑定**（无主牌可用，用后绑） | `item/card/ExclusiveCardUtil.java:25-28`（`canUse`：`owner.isEmpty() \|\| owner == player`）、`:36-40`（`bindIfAbsent`）；调用点 `item/card/LivingPageItem.java:44`、`item/card/FateGuidanceCardItem.java:57` | 活体书页 / 命运的指引 |
| **获得即绑定**（发放处写 owner） | `item/card/ExclusiveCardUtil.java:20-22`（`setOwner`）；发放/复制侧调用点：`item/sign/RinSignItem.java:47-48`（赠予活体书页）、`item/sign/HaiqingSignItem.java:107-108`（击杀奖励）、`event/AstralEventSystem.java:85`（事件发牌）、`item/sign/KomachiSignItem.java:135-136`（复制：`if (ExclusiveCardUtil.isExclusive(card)) setOwner(...)`）、`item/chip/MagicTomeChipItem.java:41-42` | 主张“牌归获得者”的发放链路 |
| 专属牌清单（`isExclusive`） | `item/card/ExclusiveCardUtil.java:31-33`（硬编码 2 个物品）、`item/card/RandomCardHandler.java:77-103`（`EXCLUSIVE_CARDS` 注册表 + 静态块，随机池强制排除）、`item/card/EffectCardUtil.java:17`（= `RandomCardHandler.isExclusive`） | 随机池排除 + 发放绑定 |
| 使用侧校验（三条路径） | `item/card/BaseEffectCardItem.java:428-430`（服务端 `tryUseCard`）、`:409-410`（客户端预检）、`:328-330`（选择器开局前） | 拒绝非获得人 |

### 7.2 冻结：本批采用 **获得即绑定**

- **依据**：需求文本「禁止非获得人使用」；且获得即绑定是唯一能覆盖「牌被转手后原主仍能反制」的语义
  （首次使用绑定允许“谁先拿到谁先用”，见 `ExclusiveCardUtil.java:25-28`）。
- **绑定落点（全部发放路径都必须写 owner）**：
  1. 福祸相倚掷骰发牌（§6.2 挂点）→ `ExclusiveCardUtil.setOwner(stack, player)`（照 `Event/AstralEventSystem.java:85`）；
  2. 白泽赐福施法者获得 1 张 `fu_card` 与祸→福转换后的牌 → `setOwner(stack, caster)`；
  3. 大当家「心意相连」发给队友的 `fu_card` → `setOwner(stack, 受赠队友)`（**不是**施法者）；
  4. **兜底**：把 `fu_card`/`huo_card` 追加进 `item/card/ExclusiveCardUtil.java:31-33` 的 `isExclusive` 清单，
     使 `KomachiSignItem:135` / `MagicTomeChipItem:41` 这类通用复制路径也会自动绑定；
  5. **兜底 2**：把两张符卡登记进 `item/card/RandomCardHandler.java:98-103` 的静态块，保证永不进随机池
     （`getCardPool` 的过滤在 `:165-170`）。
- **使用侧仍是硬门**：`isExclusive()` 覆写为 `true`（`item/card/BaseEffectCardItem.java:82-84` 默认 false），
  三条校验路径（`:328`/`:409`/`:428`）自动生效，**无需改基类**。
- **保留 `bindIfAbsent` 兜底**（`item/card/LivingPageItem.java:44` 同款）：`/give`、赏金、其它模组给的**无主**牌
  在首次使用时绑定 —— 这是既有语义，本批不改；与「获得即绑定」并不冲突（我方发放路径都会带上 owner）。

---

## 8. 组队判定（大当家「心意相连」）

### 8.1 「同队」统一入口（三套系统一处收口，**在共享库内**）

| 事实 | 证据（`starengine_lib/common/src/main/java/com/merlinkitsune/starenginelib/event/EventTargetCollector.java`） |
|---|---|
| 统一入口 | `:30-55` `collectTeamPlayers(Player triggerer)` |
| 三套系统分别收集 | MC 原生 `:36-38` → `collectMcTeamPlayers` `:65-74`（`sp.getTeam() == team`）；FTB Teams `:39-41` → `collectFtbTeamPlayers` `:93-120`（反射 `dev.ftb.mods.ftbteams.api.FTBTeamsAPI`，`:77-91`）；OPAC `:42-44` → `collectOpacPartyPlayers`（反射 `dev.darkhax.opac.api.OpenPartiesAndClaimsAPI`，`:123-130`） |
| **无队伍时的回退（关键陷阱）** | `:45-53`：`members.isEmpty() && anyTeamSystemEnabled && !hasAnyTeam(triggerer)` ⇒ **把全服在线玩家当队友返回** |
| 「是否已加入任意队伍」判定 | `:57-62` `hasAnyTeam(Player)`：`:60` MC 原生 `getTeam() != null`；`:61` FTB / OPAC 反射结果非 null |
| 配置开关 | `config/ModCommonConfig.java:29-31`（`EVENT_APPLY_MC_TEAM/FTB_TEAM/OPAC`）、`:50-55`（`event_apply_mc_team`/`ftb_team`/`opac`，默认均 true）、`:82-84`（写入 `GameplayConstants`）、`GameplayConstants.java:48/50/52`（消费） |
| 既有消费点（本仓） | `event/AstralEventSystem.java:67`、`item/InvestigationEventUtil.java:77`、`item/card/LuxuryFeastCardItem.java:57`、`item/card/RandomCardHandler.java:204`、`item/chip/BigBowlStewChipItem.java:60`、`item/chip/BankCardUnlimitedChipItem.java:53` |

### 8.2 「未组队 ⇒ 心意相连不生效」的判定写法（冻结）

```java
if (!EventTargetCollector.hasAnyTeam(triggerer)) return;          // ★ 必须先判，否则会被 :45-53 的回退放大成全服
for (Player ally : EventTargetCollector.collectTeamPlayers(triggerer)) {
    if (ally != triggerer && ZhaoSignItem.isEquipped(ally)) {      // 只发给「装备风水师立牌」的同队者
        ItemStack fu = new ItemStack(ModItems.FU_CARD.get());
        ExclusiveCardUtil.setOwner(fu, ally);                      // §7.2
        VitaminPillChipItem.giveCard(ally, fu);                    // 统一发牌入口（背包满则掉落）
    }
}
```

- 发牌必须经 `item/chip/VitaminPillChipItem.java:38-47` 的 `giveCard`（`player.getInventory().add` 失败则 `player.drop`），
  与全仓 14 个发牌点一致（`RandomCardHandler.java:188`、`AstralEventSystem.java:102`、`DiceCombatEvents.java:861` 等）。
- **触发时机**：大当家立牌的**主动技能成功触发**。非门控立牌在 `item/sign/BaseSignItem.java:121-151`（第 3-6 步）；
  若大当家将来改门控，则恢复点 `:179-193`。**冻结**：挂在 `FenSignItem.handleUse`（`item/sign/FenSignItem.java:64-82`）返回 SUCCESS 的路径上，
  **即“技能真正生效”那一刻**（拒绝冷却/锁定/取消 ⇒ 不发牌）。
- **单人与多人行为差异（冻结）**：

| 场景 | `hasAnyTeam` | `collectTeamPlayers` | 结果 |
|---|---|---|---|
| 单人（无队伍、无其它玩家） | false | 空（`:45-53` 的回退也无人可加） | **不生效**（符合需求） |
| 多人、未组队 | false | 全服在线玩家（回退） | **不生效**（被 `hasAnyTeam` 提前 return 挡住） |
| 多人、有 MC 队伍 | true | 同队在线者（`:65-74`） | 生效，只发给同队的风水师持牌者 |
| 多人、FTB Teams / OPAC | true | 对应队友（`:93-120` / `:123-130`，反射失败静默跳过） | 生效 |
| 三个开关全关 | — | 空 | 不生效（配置可控） |

---

## 9. 状态与新增附件清单（含同步 / 死亡 / 重登策略）

### 9.1 新增附件键（1.21.1 侧）

| 键 | 类型 | `.serialize` | `.sync` | `.copyOnDeath` | 理由 |
|---|---|---|---|---|---|
| `zhao_blessing_active` | `Boolean` | ✅ `Codec.BOOL` | ❌ | ❌ | 真值只需服务端；可见载体是效果实例（客户端由原生效果同步） |
| `zhao_blessing_skip_cycles` | `Integer` | ✅ `Codec.INT` | ❌ | ❌ | 纯服务端状态机 |
| `zhao_prev_blessing` | `Boolean` | ✅ `Codec.BOOL` | ❌ | ❌ | 下降沿检测专用；不同步避免每 tick 写包（同口径先例：`component/ModAttachments.java:115-116` 的 `healing_prev_blessing`「仅服务端使用，无需同步」；`:432-435` 的「三态化键一律不 `.sync()`」） |
| `zhao_overflow_bonus` | `Integer` | ✅ `Codec.INT` | ❌ | ❌ | 攻击力加成只在服务端骰战读取（同 `nancy_lu_active_bonus`：`ModAttachments.java:686-694` 未 `.sync()`，注释见 `:435`） |
| `huo_card_next_damage_tick` | `Long` | ✅ `Codec.LONG` | ❌ | ❌ | 周期伤害计时；不显示（若要显示剩余时间，走 `misfortune` 效果实例的 duration，无需同步此键） |

- **不新增**：养精蓄锐（复用 `fen_recharge`，`component/ModAttachments.java:857-861`）、厄运层数（**不缓存**，每 tick 由持有张数现算，见 §9.2）。
- **1.20.1 形状**：5 个键分别用 `AttachedDataKey.builder("…", Codec.X, () -> 默认).build()` + `register(...)`（**不加 `.sync()`**），
  不并入 `SYNCED_KEYS`（`forge-1.20.1/.../component/ModAttachments.java:25`、`:94-95` 是加 `.sync()` 的对照）。

### 9.2 厄运层数镜像

- 计数口径（**本轮 `t33` 用户裁决更正**）= **主物品栏 + 副手**：主物品栏 = `player.getInventory().items` 里
  `huo_card` 的 `getCount()` 之和；副手 = `player.getOffhandItem()` 里的 `huo_card` 张数。
  落点 = `item/card/HuoCardItem#count()`（`neoforge-1.21.1/.../item/card/HuoCardItem.java:110-132`、
  `forge-1.20.1/.../item/card/HuoCardItem.java:111-133`）：**先**遍历 `getInventory().items`
  （现成先例 `item/chip/SmartWatchChipItem.java:57-66` 的 `countCards`，只覆盖主栏），**再单独读一次**
  `player.getOffhandItem()`。
  - ⚠️ **禁止**用 `Inventory#getContainerSize()` 遍历代替副手判定：**1.20.1 的 `getContainerSize()` 含护甲槽与副手槽**
    ⇒ 用它遍历会误计护甲并重复计数（1.21.1 侧 `HuoCardItem.java:119-120`、1.20.1 侧 `:119-121` 的 javadoc 已写明）。
  - ⚠️ **「容器不计入」仍然成立**：放进普通容器（箱 / 桶 / 漏斗 / 末影箱 / 交易槽）后**既不产生周期伤害、也不计入厄运层数**
    （§3.3 的 ⚠️ 与 §14.1 裁决① 放行边界不变，实证见 §14.5 Q4）；副手**不是**容器 ⇒ 副手持有照常计入。
  - 删除/转换路径与计数取**同一持有集合**：`HuoCardItem#removeAll()`（主物品栏 + 副手；1.21.1 `:134-157`、1.20.1 `:139-155`）
    与 `convertAllToFu()`（"全部转换为符卡-福"，一张都不能漏）同口径。
- 镜像写法照 `item/HealingManager.java:216-250`：`amplifier = count - 1`；count 下降时**必须先移除旧实例**再 `addEffect`
  （`:243-247` 的踩坑注释：原版只接受更高 amplifier，否则 HUD 停在旧层）；`count <= 0` ⇒ `ModEffectRemoval.remove`
  （`:219-222`）+ 清 `huo_card_next_damage_tick`。
- 周期伤害（每 2:00 = 2400 tick）在**同一玩家级 tick** 内判定：到期 ⇒ 伤害 = **结算时刻的当前张数**（`count` 现算）；
  计时的重置与张数增减**完全解耦**（张数变化不重置 `huo_card_next_damage_tick`）——这是 t2 验收第 6 条的“计时器与结算分离”证据要求。
- 伤害源（冻结）：`damage/ModDamageTypes.java:55`（`trueDamage(Level)`，无来源实体、记入 `bypasses_armor` 标签、
  不会被当成玩家攻击而重走骰战 —— 理由见 `:51-53` 与 `:59-62` 的 javadoc）。
- 1.20.1 等价：`damage` 包的同一 API（`forge-1.20.1/.../damage/ModDamageTypes.java` 同构，伤害类型 JSON `data/astral_dice/damage_type/*.json` 两线各一份）。

### 9.3 死亡 / 重登 / 卸牌 汇总

| 事件 | zhao_* 附件 | zhao_blessing 效果 | misfortune 效果 | 依据 |
|---|---|---|---|---|
| 死亡 | 新实体归默认（非 copyOnDeath）；旧实体清理处显式移除效果 + 清 `zhao_overflow_bonus` | 移除 | 移除 | `event/PlayerLifecycleHandler.java:130-183`（`:133-139` 口径）、`:188-196`（只在 `isWasDeath` 时恢复保留项） |
| 重登 | 随玩家 NBT 保留（`.serialize` 键）；但显式复位 `active/prev`（§4.7） | 移除 | 保留（其真值是“持有张数”，张数还在） | `event/PlayerLifecycleHandler.java:199-212` |
| 死亡重生 | `ChargeManager`/`DeathPreservedBonuses` 恢复（本批无新增保留项） | — | — | `event/PlayerLifecycleHandler.java:214-225`、`component/DeathPreservedBonuses.java` |
| 卸下风水师立牌 | **不清**白泽赐福（效果属于目标，不属于立牌） | — | — | 口径同 `item/sign/NancyLuSignItem.java:92-93` |

---

## 10. 文件级 owner 映射（同一文件只有一个 owner）

> 依据成员执行提示：`v121` 只动 `neoforge-1.21.1/**` 与 `scripts/test/cases/*-1.21.1.json`、`scripts/test/resources/kubejs/1.21.1/**`；
> `v120` 只动 `forge-1.20.1/**` 与对应 `*-1.20.1.json`；`scribe` 只动 `CHANGELOG*.md` / `AGENTS.md` / `scripts/test/TESTING-SPEC.md` / `docs/**`；
> `captain` 独占构建、冒烟、提交与根级工具链。**本表按物理文件唯一归属**。

### 10.1 `v121` 独占（`neoforge-1.21.1/**`）

新增：
`src/main/java/.../item/sign/ZhaoSignItem.java`、`src/main/java/.../item/card/FuCardItem.java`、
`src/main/java/.../item/card/HuoCardItem.java`、`src/main/java/.../effect/ZhaoBlessingEffect.java`、
`src/main/java/.../effect/MisfortuneEffect.java`、`src/main/java/.../event/ZhaoHealOverflowHandler.java`（**建议**：`LivingHealEvent` 处理器独立成类，避免再挤 `DiceCombatEvents` —— **captain 裁决（2026-09-26）：不强制拆文件**。`v121` 的实现落在 `item/sign/ZhaoSignItem#onLivingHeal`，**已满足该建议的本意**「不再挤 `DiceCombatEvents`」⇒ **接受，不返工**；本节原先列出的文件名 `event/ZhaoHealOverflowHandler.java` **作废**，两线一律以 `ZhaoSignItem#onLivingHeal` 为准）、
`src/main/resources/assets/astral_dice/textures/{item/zhao_sign.png,item/fu_card.png,item/huo_card.png,mob_effect/zhao_blessing.png,mob_effect/misfortune.png}`、
`src/main/resources/assets/astral_dice/patchouli_books/astral_guide/en_us/entries/signs/zhao_sign.json`、
`…/entries/cards_effect/{fu_card,huo_card}.json`、`scripts/test/cases/*-1.21.1.json`、`scripts/test/resources/kubejs/1.21.1/**`。

修改（1.21.1 侧单份，互不冲突）：
`item/ModItems.java`、`effect/ModEffects.java`、`component/ModAttachments.java`、`init/ModCreativeTabs.java`、
`datagen/ModItemModelProvider.java`、`datagen/ModRecipeProvider.java`、`item/card/BaseEffectCardItem.java`（`cardByTypeId` switch）、
`item/card/EffectCardPeriod.java`（出牌数入口）、`item/card/ExclusiveCardUtil.java`、`item/card/RandomCardHandler.java`、
`item/sign/FenSignItem.java`（心意相连）、`event/PlayerTickEvents.java`、`event/PlayerLifecycleHandler.java`、
`combat/DiceCombatEvents.java`（1/6 挂点 + 赐福结束转发）、`combat/DiceCombatModifiers.java`（溢出加成修饰器）、
`assets/astral_dice/lang/zh_cn.json`、`assets/astral_dice/lang/en_us.json`、
`data/curios/tags/item/stand.json`、`data/astral_dice/tags/item/signs.json`、`…/effect_cards.json`、`…/is_exclusive.json`。

### 10.2 `v120` 独占（`forge-1.20.1/**`）

与 10.1 **逐文件同名镜像**（`item/sign/`、`item/card/`、`effect/`、`component/ModAttachments.java`（`AttachedDataKey` 形态）、
`event/PlayerTickEvents.java`、`event/PlayerLifecycleHandler.java`、`combat/DiceCombatEvents.java`、`combat/DiceCombatModifiers.java`、
`init/ModCreativeTabs.java`、`datagen/{ModItemModelProvider,ModRecipeProvider}.java`、`lang/*.json`、`data/**`、
`assets/**`），外加 `scripts/test/cases/*-1.20.1.json`、`scripts/test/resources/kubejs/1.20.1/**`。
平台差异（`docs/compat-1.20.1-forge.md` 口径）：附件 → `AttachedDataKey` + Capability；治疗钩子同样是
`net.minecraftforge.event.entity.living.LivingHealEvent`（forge 源 jar 内存在，且 `LivingEntity.heal` 体首行同形）。

### 10.3 `scribe` 独占

`CHANGELOG.md`、`CHANGELOG_ZH.md`（五类分组、逐条对应）、`AGENTS.md`（玩家可见口径 + 实现契约 + **26.1.2 冻结期待迁移清单追加**）、
`scripts/test/TESTING-SPEC.md`（附录 A 工程记录）、`docs/**`（含本规格文件的后续维护）。

### 10.4 `captain` 独占

根 `build.gradle` / `settings.gradle` / 三子项目 `gradle.properties` 与 `build.gradle`、`gradle/**`、`gradlew*`、
`tools/**`、`scripts/devtools/**`、`scripts/test/mt_*.ps1`、根级 `images/**`（**注意**：图标源在仓库根 `images/`，本批**只读复制**到各子项目，
**不需要新增源图**，见 §2.3）；以及全部构建、datagen、冒烟、提交动作。

### 10.5 冲突风险点（单 owner 校验）

| 共享概念 | 是否跨 owner | 处置 |
|---|---|---|
| `lang/*.json`、`ModItems`、`ModEffects`、`ModAttachments` | 物理上**每个子项目各一份** ⇒ 不冲突 | v121 改 1.21.1 份，v120 改 forge 份 |
| `RandomCardHandler` / `ExclusiveCardUtil` | 同上（两线各一份） | 同上 |
| `scripts/test/cases/*.json` | 按文件名后缀 `-1.21.1` / `-1.20.1` 分属 | 文件名必须带后缀 |
| `scripts/test/resources/kubejs/<版本>/**` | 按目录分属 | 不得写对方目录 |
| `DiceCombatEvents`（两线各一份，改动量大） | 单 owner | v121/v120 各自完成，禁止交叉改 |

---

## 11. `neoforge-26.1.2` 待迁移项登记（本批**零改动**）

> 用户既有裁决：26.1.2 冻结，本批不改。以下**只登记**，供解冻后一次性迁移（附只读证据）。

| # | 待迁移项 | 26.1.2 侧落点证据（只读核对） |
|---|---|---|
| 1 | 物品/立牌/卡牌注册 | `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/item/{ModItems.java, sign/, card/}`（`item/sign/` 现有 18 个类，与 1.21.1 同数） |
| 2 | 效果注册（**id 集有差**） | `neoforge-26.1.2/.../effect/ModEffects.java` 的 `EFFECTS.register(` 命中 **32** 处，而 1.21.1 同文件 **34** 处 ⇒ 迁移时必须先按 `AGENTS.md` 的「效果注册条目」口径核对 id 集 |
| 3 | 附件（平台形态不同） | 26.1.2 走 NeoForge `AttachmentType`（与 1.21.1 同类）；1.20.1 才是 `AttachedDataKey` |
| 4 | **两段式数据生成（必经）** | `neoforge-26.1.2/.../datagen/{ClientDataGenerators,ServerDataGenerators,ModItemModelProvider,ModRecipeProvider}.java` + 双输出根 `src/generated/{resources,clientResources}`（见 `AGENTS.md` 26.1.2 速记第 2 条）；物品模型另有 1.21.4+ 的 `assets/<ns>/items/<id>.json` 硬需求（`src/generated/clientResources/assets/astral_dice/items/*.json` 现存） |
| 5 | 语言键 | 26.1.2 `lang/zh_cn.json` 现 685 行 = 1.21.1 685 行；新增键需三线各自 zh↔en 一致（`tools/check_lang_sync.ps1:499`） |
| 6 | 长矛近战判定 | `ItemTags.SPEARS` **只在 26.1.2 线存在**（`AGENTS.md` 26.1.2 速记第 10 条）⇒ 若本批触碰 `isMeleeWeaponAttack`（`combat/DiceCombatEvents.java:973-981`），迁移时**不得**把 26.1.2 版本回移 |
| 7 | 立牌主动门控（`BaseSignItem` 第 2.5 步） | 26.1.2 现有 18 个立牌类，需确认门控分支与新选择器 API 一致 |

---

## 12. 「不影响」举证与冒烟级别判定（`TESTING-SPEC.md` §1.1 第 7 条）

### 12.1 改动入口符号 → 全仓调用点/消费点双侧枚举

| 改动入口符号 | 定义点 | 消费/调用点（双侧枚举） | 与用例的映射（最小集） |
|---|---|---|---|
| `ModEffects.DICE_BLESSING` | `effect/ModEffects.java:45-46` | `combat/DiceCombatEvents.java:250/256/268/270/322/491/588/789/925`、`combat/DiceCombatModifiers.java:579`、`item/HealingManager.java:130/199/225`、`screen/CardInventoryScreen.java:104/115`、`screen/CardInventoryMenu.java:390/399`、`item/card/FateGuidanceCardItem.java:114`、`item/chip/CursedSwordChipItem.java:87`、`event/ModTooltipHandler.java:356` | 白泽赐福两分支用例（§4.5 ①②④⑤⑥）+ 骰神赐福既有回归 |
| `DiceCombatEvents.onDiceBlessingExpired`（**骰神赐福自身**的结束订阅，与白泽赐福无关） | `:804-812` | 事件总线（NeoForge `MobEffectEvent.Expired`）；本批**不新增任何调用** —— 白泽赐福的结束判定走 §4.4 的**玩家级 tick 下降沿**（基准键 `zhao_prev_blessing`）；⚠️ 本行为 `t11` 就地更正（原写「本批新增调用 `ZhaoSignItem.onDiceBlessingEnded`」，与 §4.4 的冻结决定自相矛盾） | 骰神赐福**既有回归**（两线各 9 处 `Expired` 引用保持不变，不得误删） |
| `DiceCombatEvents` 骰战主体（全局伤害结算出入口） | `:189-190`（`@SubscribeEvent onLivingDamagePre(LivingDamageEvent.Pre)`） | 全仓唯一的骰战入口；攻击修饰器消费 `:434`/`:1192` | **命中「完整冒烟」第②条**（见 12.3） |
| `DiceCombatModifiers.attackModifiers()` | `:79-81` | 消费点仅 3 处：`DiceCombatModifiers.java:545`、`DiceCombatEvents.java:434`、`:1192` | 溢出治疗→攻击力用例（含 `getDisplayAttackRange` tooltip 路径） |
| `EffectCardPeriod.isBlocked(...)` | `:388` | `item/card/BaseEffectCardItem.java:331/417/432`、`client/TargetSelectionClient.java:648`、`command/AstralPartyCommand.java:364` | 符卡出牌/锁定相关用例 |
| `EffectCardPeriod.grantBonusPlay(...)` | `:240-244` | 既有唯一调用点 `item/sign/KomachiSignItem.java:85`；**本批不再新增调用点**（`t7` 改判：符卡-福 改走专属计数，见 §14.2） | 忍者主动 +1 的**既有回归**；与符卡-福 +1 **可叠加性**的交叉验证（A/B 口径的分辨点） |
| **符卡-福专属本周期计数器**（新增附件 / `AttachedDataKey`） | **新增**（`t7` 改判引入） | 唯一写入点 = `EffectCardPeriod.grantFuCardBonusPlay(...)`（§14.2）；读取点 = `getMaxAllowed` 的 extra + `clearRoundBonuses` | 「每次使用都净 0」+「连续使用后 `getMaxAllowed ≤ 9`」用例 |
| `ExclusiveCardUtil.canUse(...)` | `:25-28` | `item/card/BaseEffectCardItem.java:328/409/428`（两线各一份） | 「非获得人被拒」用例 |
| `ModItems.isCardItem(...)` | `:892-893` | `item/sign/FannySignItem.java:145`、`item/sign/MimiSignItem.java:136`、`item/chip/SmartWatchChipItem.java:61`、`item/chip/VitaminPillChipItem.java:55`、`event/AstralEventSystem.java:101` | 符卡计入卡牌（维他命/看板娘计数）用例 |
| `EventTargetCollector.collectTeamPlayers` / `hasAnyTeam`（**库**） | 库 `EventTargetCollector.java:30` / `:58` | 本仓 6 处消费（`AstralEventSystem.java:67`、`InvestigationEventUtil.java:77`、`LuxuryFeastCardItem.java:57`、`RandomCardHandler.java:204`、`BigBowlStewChipItem.java:60`、`BankCardUnlimitedChipItem.java:53`） | 「未组队 ⇒ 心意相连不生效」用例（**只新增判定，不改库** ⇒ 既有 6 处零影响） |
| `LivingHealEvent`（**新挂点，仓内既有消费点为空**） | 原版 `LivingEntity.java:1118` 触发 | 本仓 0 命中（递归 grep 空） | 溢出治疗用例；**枚举为空** = 不改变任何既有治疗路径的行为 |
| `IItemExtension#onDroppedByPlayer` + `canFitInsideContainerItems`（新挂点，**裁决①**） | `net/neoforged/neoforge/common/extensions/IItemExtension.java:89` / 原版 `Item.java:422`（1.20.1：`net/minecraftforge/common/extensions/IForgeItem.java:74` / 原版 `Item.java:452`） | 本仓 0 命中（两处均是全新覆写点） | 「持有期间禁止丢弃」用例（Q 键/拖出 + 潜影盒/收纳袋；普通容器为已登记边界，见 §3.3 / §14.1） |

### 12.2 本批必含用例集（**完整冒烟**全清单中必须包含的子集；级别见 §12.3 / §14.3）

> 本批已裁决为**完整冒烟**（§14.3）⇒ 下表**不是**「可裁剪的最小集」，而是**全清单必须覆盖的下限**；
> 无关模块的既有用例本批**照跑**，不得按「定向最小冒烟」取消。

1. 立牌/卡牌注册与创造栏可拾取（含 curios `stand` 槽可装备）；
2. 福祸相倚：骰点 1 ⇒ `huo_card` +1、骰点 6 ⇒ `fu_card` +1，且绑定获得者；
3. 符卡-福：目标选择器（可自选/可选玩家）⇒ 目标 +2 治疗、本轮出牌数 +1（**断言：每次使用都净 0（出牌数不减少）；且连续多次使用后 `getMaxAllowed` 不超过 9** —— 见 §14.2 `t7` 改判，`t6` 的「第二次不再 +1」断言作废）；非获得人被拒（客户端预检 + 服务端权威）；
4. 符卡-祸：仅 `ENEMY_OR_RIVAL` 可选 ⇒ 1 点伤害；持有 N 张（**N = 主物品栏 + 副手之和**，§9.2）⇒ `misfortune` 层数 = N；N=0 ⇒ 无效果；丢弃被拒（Q 键/拖出）+ 潜影盒/收纳袋被拒（§3.3），放入普通容器后停止计层与周期伤害（**已登记边界，需实测确认，见 §14.5 Q4**）；
5. 符卡-祸周期伤害：2400 tick 结算「结算时刻当前张数」（**当前张数 = 主物品栏 + 副手**，§9.2）；中途增减张数不重置计时；
6. 白泽赐福分支①（未在赐福）：触发赐福→结束⇒移除；分支②（已在赐福）：跳过当前、下一次结束⇒移除；
7. 溢出治疗：满血时治疗 ⇒ 溢出量等量转攻击力（读 `getDisplayAttackRange`/实战伤害）；溢出 ≤ 0 不增加；移除时加成归零（无残留）；
8. 完美帮手：对装备大当家立牌者施放 ⇒ `fen_recharge +1`（≤5）；
9. 心意相连：同队装备风水师立牌者各得 1 张 `fu_card`（绑定受赠者）；**未组队不生效**；
10. 边界：目标死亡 / 换维度 / 外力移除效果 / 重登（§4.6、§4.7）各一条；
11. **出牌计数归属（§14.5 Q1/Q2/Q3）**：符卡-福/符卡-祸各消耗 1 次出牌、参与 `EffectCardPeriod` 周期与忍者/魔法秘典/魔法箭袋计数；符卡-福的 +1 使净效果等于「出牌数收支：消耗 1、返还 1」，**且每次使用都成立**（`t7` 改判后的 B 口径：专属本周期计数按次累加、受 `min(9,…)` 封顶）；**另需交叉验证「忍者主动 +1」与「符卡-福 +1」可叠加**（这是 A/B 两口径唯一可在游戏内区分的差异点）。

### 12.3 冒烟级别判定（结论 + 依据 + **captain 裁决**）

**结论（本文件冻结 = captain 裁决③，原文见 §14.3）：本批判定为「完整冒烟」，必须跑全清单 —— 两条发布线
（`neoforge-1.21.1` + `forge-1.20.1`）各跑一遍阶段 L + 阶段 C **全清单**；`neoforge-26.1.2` 因用户冻结**不跑**
（只登记待迁移项，见 §11）；构建与冒烟由 captain 执行，成员不得运行 gradle。**

依据（`scripts/test/TESTING-SPEC.md`）：

- 第②条原文 = 「跨模块公共路径（共享库 `starengine_lib`、注册表与数据组件结构、网络协议、存档/读档结构、**全局伤害结算出入口**）」（`:29`）；
- 本批**同时**触达其中至少 4 项：
  (a) **全局伤害结算出入口**：1/6 挂点落在 `combat/DiceCombatEvents.java:189-190` 的骰战主体内（`:332-436` 区间）；
  (b) **全局伤害/治疗出入口**：新增 `LivingHealEvent` 全局治疗钩子（`LivingEntity.java:1118`）；
  (c) **注册表与数据组件结构**：`ModItems`/`ModEffects`/`ModAttachments`/`ModDataComponents(OWNER_UUID)` 与 4 个 data 标签；
  (d) **存档/读档结构**：5 个新附件键（`.serialize`）参与玩家 NBT 读写（`component/ModAttachments.java:23-27` 形态），且涉及死亡/重登策略（§9.3）。
- 同一批（同一 commit）以最高级别为准（`:34`）、拿不准一律按更高一级（`:35`）⇒ 结论不变；
- 「第 7 条不影响举证」已在本节 12.1 给出双侧枚举；但按 `:40`（第 9 条），触及共享前置库或跨模块公共路径时**直接按完整冒烟处理**——故 12.1 的枚举仅用于**说明影响面**，**不得**用于缩小冒烟范围；
- 若 `mt_scope.ps1` 得到「核心（定向最小冒烟）」的建议级别，**以本节结论为准**（`TESTING-SPEC.md:42` 的冲突口径：以 `AGENTS.md` 为准，而 `AGENTS.md` 的完整冒烟情形含「全局伤害结算出入口」）。

---

## 13. 逐条验收自检（t1 acceptance + t6 回填项 → 本文件章节）

| 验收条 | 章节 |
|---|---|
| 物品/立牌/卡牌/效果/附件/语言键/贴图/创造栏/配方注册点 + 目标选择器动作 id 注册点 | §1、§2、§3.2 |
| 图标映射（存在性逐一核对 + ≥2 个既有映射先例含 `images/反击.png` + 两个缺失图标的候选处置） | §2.3 |
| 白泽赐福时序（两分支状态迁移表 + 三边界 + 判定/移除入口行号） | §4 |
| 溢出治疗挂点（全部入口枚举 + 未覆盖项 + 计算式） | §5 |
| 骰点 1/6 挂点（哪一次掷骰 + 多条路径逐一标注适用范围） | §6 |
| 专属绑定（两种既有语义证据 + 采用哪一种 + 落点） | §7 |
| 组队判定（三套入口统一落点 + 未组队写法 + 单/多人差异） | §8 |
| 文件级 owner 映射（v121/v120/scribe/captain，单 owner） | §10 |
| 状态与附件清单（新键 + 是否 sync + 死亡/重登保留策略） | §9 |
| 「不影响」举证与冒烟级别 | §12 |
| 26.1.2 冻结（只登记待迁移项） | §11 |
| **t6 裁决回填**：符卡-祸容器/丢弃边界（①）、「出牌数 +1」语义与用例断言要求（②）、冒烟级别 = 完整冒烟（③）、符卡 id 保持 `fu_card`·`huo_card`（④） | §14.1/§14.2/§14.3/§14.4、§3.3、§12.2、§12.3 |
| **t6 新增待验证项**：出牌计数归属三问 + 出牌数收支表 + verifier 实证读数清单 | §14.5 |
| **`t7` 裁决② 改判**（用户定夺选项 B）：「出牌数 +1」由「每轮一次（`grantBonusPlay`）」改为**「每张各 +1（专属本周期计数 + `min(9,…)` 封顶）」**；附改判依据（该槽位与忍者主动共用 ⇒ 静默失效；原「会刷爆」理由证伪）与**可判别差异**（两来源可叠加） | §14.2、§14.5 Q3 + 收支表、§12.1 入口符号行、§12.2 第 3/11 条、文件头修订记录 |
| **`t7` 交叉引用修正**：原「§14.5 第 3 问」实指容器计数推论（与 Q3 出牌数并非同一问）⇒ 独立为 **Q4**，§3.3 ⚠️ 与 §12.2 第 4 条引用同步更正 | §14.5 Q4、§3.3、§12.2 |
| **t6 跨线实证差异对照**：`tags/items`（v120）vs `tags/item`（v121）、行号基准 `git show 1970de2c:<path>` | §15、§1 头部 |
| **`t33` 用户裁决（本轮）**：**副手持有符卡-祸计入厄运层数** —— 计数口径由「主物品栏」**更正为「主物品栏 + 副手」**（旧口径作废）；「容器不计入」仍然成立；写入头部修订记录 | §1 头部修订记录、§9.2、§3.3、§12.2 第 4/5 条、§14.1、§14.5 Q4 |
| **`t33` 新增待用户裁决项**：符卡-福 / 符卡-祸 **计数口径的不对称**（`countFu()` 仍为主物品栏口径，本轮未动） | §14.10 —— **已于 `t36` 结案**（见下两行） |
| **`t36` 定案 ①**：**符卡-福 `countFu()` 保持主栏口径**（§14.10 由「待裁决」**结案**；引用户理由「本身持有并无特殊效果，因此是否计入没有意义」；「统一为 B 口径」不再考虑） | §14.10、§1 头部修订记录 `t36` 条 |
| **`t36` 定案 ②**：**目标选择器仅主手生效**（现状实现、本轮零代码改动；四条代码证据：`BaseEffectCardItem:317/:315`、`:266/:264`、`TargetSelectionClient:278` + `:643/:646`、`EffectCardUseGuard:71`；**副手不唤起**为推论） | §14.11、§3.2、§2 注册入口行 |
| **`t36` 定案 ③**：**战斗爽保留粒子**（`FEN_FRENZY` 的 `visible=true` 有意为之、不得误删；`FS1` 已用 `frenzy_visible=1` 正向锁定） | §14.12、`item/sign/FenSignItem.java:79-80`（1.20.1 `:80-81`）、cases `ZHAO-SIGN-*.json:437/:359` |

---

## 14. 裁决回填（captain 裁决，**已定**）+ 待验证项 + 残余项 + **`t36` 定案**

> 本节是 **t6 的回填结果**：t1 时登记在旧「未冻结/待裁决项」下的各项现已按 captain 裁决收敛。
> **§14.1–§14.4 = 已裁决 ⇒ 实现、评审、文档一律按此执行**（旧的「请 captain/用户裁决」措辞作废）；
> **§14.5 = 待验证项（交 verifier 逐条实证，**不得只当命名问题**）**；**§14.6 = 仍属实现方处置的残余项**；
> **§14.10 = 已裁决结案（`t36`：符卡-福保持主栏口径）**、**§14.11 = 定案（目标选择器仅主手生效）**、
> **§14.12 = 定案（战斗爽保留粒子）** —— 三条均为**定案**，评审/verifier 按此执行、**不得再当缺陷上报**。

### 14.1 裁决① 符卡-祸「禁止丢弃」：采纳 (a) —— 拦主动丢弃 + 潜影盒/收纳袋，**普通容器作为已登记边界放行**

- **实现要求（冻结）**：
  1. `Item#onDroppedByPlayer(ItemStack, Player)` 返回 **`false`**（拦 Q 键 / 拖出到世界 / 背包丢弃）——
     1.21.1 钩子 `net/neoforged/neoforge/common/extensions/IItemExtension.java:89`（javadoc 原文保证「物品不会被移出玩家背包」）；
     1.20.1 同名钩子 `net/minecraftforge/common/extensions/IForgeItem.java:74`；
  2. `canFitInsideContainerItems()` 返回 **`false`**（拦潜影盒 / 收纳袋）——1.21.1 原版 `Item.java:422`、1.20.1 原版 `Item.java:452`。
- **已登记边界（放行）**：普通容器（箱 / 桶 / 漏斗 / 末影箱 / 交易槽等）。
  **明确不做** `Container#setItem` mixin，**理由（captain 裁决原文）**：全局容器入口的「拒绝写入」会带来
  「物品已从玩家侧扣掉但目标容器未接收」的**丢失 / 复制风险**，且该 mixin 的**验证面**是全局容器（含其它模组与自动化）
  而非本模组用例，成本与风险不对称；**该项已升级给用户**，如需全量封堵属**后续独立批次**。
- **玩家可见文案建议（一条，供 lang/scribe 采用）**：符卡-祸 tooltip 增补
  「**持有期间无法丢弃；存入容器后不再计入厄运**」——与「厄运层数 = 当前持有张数」（**= 主物品栏 + 副手**，§9.2）口径一致：
  **副手持有照常计入**（本轮 `t33` 用户裁决），只有「放进容器」才停止计入。
- 落点表见 **§3.3**；入口符号枚举行见 **§12.1**；由此裁决导出的口径推论见 §3.3 的 ⚠️ 与 §14.5 末段。

### 14.2 裁决② 「出牌数 +1」= **每张各 +1**（`t7` 改判；`t6` 的 `grantBonusPlay` 一次性读法**作废**）

- **实现口径（冻结）**：`FuCardItem` 每次成功打出时，给**本周期专属计数器** +1（附件语义 = 本周期内「打出符卡-福 的次数」）；
  该计数进入 `EffectCardPeriod.getMaxAllowed` 的 extra（与 `getBonusPlays` / 活体书页累加项同级），并随 `clearRoundBonuses` 清零；
  当轮上限仍受全局封顶 **9** 约束（`GameplayConstants.java:31` `MAX_EFFECT_CARD_PLAYS = 9`；
  `EffectCardPeriod.java:203-221` = `min(9, 1 + 固定 + 临时 + 本周期追加)`）。
- **不采用** `EffectCardPeriod.grantBonusPlay`（`:240-244`）—— **改判依据（`t7` 复核，如实记录推翻过程）**：
  1. 该方法是**每轮单个二进制槽位**：`:241` `if (getBonusPlays(player) > 0) return false;`（只写 1，不累加）；
  2. 该槽位**与忍者立牌主动技能共用**（既有唯一调用点 `item/sign/KomachiSignItem.java:85`）⇒ 忍者先授予过 +1 时，
     符卡-福 的 +1 会**静默失效**，该卡不再「净 0」而是**白耗一次出牌**（玩家可见的静默失效，`t6` 未预见）；
  3. `t6` 拒绝「每张各 +1」的理由（原话：「需新增每轮计数附件并改动上限汇总语义」+ 隐含的「会无上限刷爆」）**已被证伪**：
     它走的是**同一条** `min(9, …)` 汇总，且既有先例 `grantLivingPageCycleBonus`（`:264-271`）本身就是
     「按次累加 + 封顶」⇒ **有界，不可能刷爆**；原裁决的省钱理由因此不成立。
- **可判别差异（verifier 必须交叉验证）**：B 口径下「忍者主动 +1」与「符卡-福 +1」**可叠加**（互不吞噬）；
  A 口径下不可叠加。这是两种读法**唯一能在游戏内区分**的行为点，必须有用例覆盖。
- **调用时序（冻结，由证据推导的硬约束，改判后依然成立）**：本卡的 +1 必须在**本张牌**的 `applyEffect` 阶段授予
  （`item/card/BaseEffectCardItem.java:437`），即**早于** `EffectCardPeriod.registerPlay`（同文件 `:440`）——
  否则 `registerPlay` 的 `count >= getMaxAllowed(...)` 判据（`EffectCardPeriod.java:438`）会在上限仍为旧值时就把本轮打满并立即起 30 秒冷却，
  「消耗 1 / 返还 1」的净效果不成立。
- **实现约束（captain 指定的防返工写法）**：授予动作必须收敛进**单一方法**（建议
  `EffectCardPeriod.grantFuCardBonusPlay(Player)`），调用点只写一处；两线方法签名与语义**逐字对齐**
  （1.21.1 用 NeoForge 附件、1.20.1 用 `AttachedDataKey`，是否入 `SYNCED_KEYS` 由「是否仅服务端判定」决定并在报告中声明）。
- **用例断言要求（硬，**替代** `t6` 版本）**：必须显式断言
  ①「**每次使用符卡-福都净 0**（出牌数不减少）」、
  ②「**连续多次使用后 `getMaxAllowed` 不超过 9**」（读专属计数器 / `getMaxAllowed` / `effect_card_play_count` / `effect_card_cooldown_end`）、
  ③（若含该组合用例）「**忍者主动 +1 与符卡-福 +1 可叠加**」。
- 见 §12.2 第 3 条与第 11 条。

### 14.3 裁决③ 冒烟级别 = **「定向最小冒烟」**（`t12` 就地推翻原「完整冒烟」裁决）

> ⚠️ **本裁决已按用户 2026-09-19 指令推翻了 `t6`/`t8` 期的「完整冒烟」结论**。用户原话：
> 「清理测试脚本链，删除全部无关全局冒烟测试脚本，仅针对改动部分进行测试。除非用户下达要求，否则禁止无意义反复进行完整测试。」

- **正确级别（冻结）**：**定向最小冒烟** —— 只跑本批**直接触及模块**的用例。本批最小集 =
  `neoforge-1.21.1`：`ZHAO-SIGN-1.21.1` / `HUO-CURSE-1.21.1` / `ZHAO-BLESSING-1.21.1`；
  `forge-1.20.1`：`ZHAO-SIGN-1.20.1`（该线把这批断言并入单一用例）。
  `neoforge-26.1.2` 因用户冻结**不跑**（只登记待迁移项，见 §11）。
- **唯一入口**：`temp/t76/run-change-scoped.ps1`（`stop → build → env → launch → 逐条用例 → stop --purge-saves`，探针改动由冷启动生效）；
  **不提供全量模式**。启动期硬闸门（noai / 史莱姆压制 / preflight）与 §9 静态闸门照旧必过。
- **我原先的误判（如实记录，含推翻过程）**：我把「本批新增全局治疗钩子 `LivingHealEvent` + 改 `DiceCombatEvents`（全局伤害结算）+
  新增注册表/存档键」当作 `TESTING-SPEC §1.1` 例外②「跨模块公共路径」，并据此要求**两线各跑阶段 L + 阶段 C 全清单**。
  实测后果：全清单跑到的是 `AIRBAG` / `ANVIL` / `CARD-SELECTOR`(TIMEOUT) / `CHARGE-COOLDOWN`(FAIL) / `CHIP-EQUIP` /
  `DIRECTIONAL-BLAST-AOE`(ERROR) 等**与本批无关的既有模块用例**（尤其**另一张效果牌**的 AOE 伤害公式），**根本没触及本批用例**，
  纯属开销与噪声，且其失败极易被误读为本批回归。规则第 8 条本来就要求：无关模块**取消并登记**（不计入遗留），而不是全跑。
- **「效果牌」的边界口径（本批）**：本批确实改了效果牌**共享基础设施**（`BaseEffectCardItem` / `EffectCardPeriod`），
  故**符卡-福/祸自身的用例必须在最小集内**；但**其它效果牌的功能用例（如定向爆破 AOE）与本批无因果关系** ⇒ 取消。
- 结论与依据链见 §12.3；§12.2 的清单按本条**降级为「候选集」**——本批只取其符卡/立牌/白泽赐福相关条目。

- **（历史记录，已作废）原「完整冒烟」裁决的原文理由**：命中 `TESTING-SPEC.md §1.1` 例外②「跨模块公共路径」—— 全局伤害结算出入口
  （`combat/DiceCombatEvents.java:189-190`、`:332-436`）+ **新增全局治疗出入口**（`LivingHealEvent`）+ 注册表/数据组件结构
  （`ModItems`/`ModEffects`/`ModAttachments`/`ModDataComponents.OWNER_UUID` 与 4 个 data 标签）+ 存档结构新键（§9.1 的 `.serialize` 键）
  ⇒ 据此要求**两线各跑阶段 L + 阶段 C 全清单**。**该结论已作废**：按用户指令，升级为全清单**必须由用户明确要求**，
  captain **不得自行开跑**（`AGENTS.md` 变更分级节第 13 条）。`neoforge-26.1.2` 始终按用户冻结不跑（§11）。
- **不受本条更正影响的约束**：构建与冒烟**一律由 captain 亲自执行**（成员不得运行 gradle，`AGENTS.md` 第 12 条）。

### 14.4 裁决④ 两张符卡的 id **保持** `fu_card` / `huo_card`（不改名）

- 用户已就本批命名作出裁决（风水师立牌 = `zhao`），captain 已把 `fu_card` / `huo_card` 作为**最终 id** 转达 `v121`/`v120`；
  改名成本与风险都不划算 ⇒ **保持不变**（§1 最终 id 表不变）。
- 保持不变的成本依据（已实证）：全仓**不存在**按名字前缀分支的代码 —— `"effect_card_` 只出现在
  `item/ModItems.java:307-851` 的物品 id 字符串与 `component/ModAttachments.java:37/46/79` 的附件键名里；
  卡牌判定走**标签**（`item/ModItems.java:103-108/892-893`）与**显式清单**（`item/card/RandomCardHandler.java:108-142`），与注册名无关。
- ⚠️ **命名差异背后的真问题不是命名问题**（出牌计数归属）⇒ 已单列为 §14.5。

### 14.5 待验证项（**交 verifier 逐条实证**；每问都要给可复现读数或代码行号级证据）

| # | 问题 | 本规格冻结的期望 | verifier 需要的实证 |
|---|---|---|---|
| **Q1** | 使用「符卡-福」「符卡-祸」是否**消耗一次出牌**（计入出牌计数）？ | **是**：两张都是 `BaseEffectCardItem` 子类，走同一 `tryUseCard`（`item/card/BaseEffectCardItem.java:426-474`），其中 `EffectCardPeriod.registerPlay(player)`（`:440`）对**全部**效果牌无排除项 | 出牌前后 `effect_card_play_count` 的只读读数（键定义 `component/ModAttachments.java:36-40`；`/astralparty dump` 输出该行见 `command/AstralPartyCommand.java:344`） |
| **Q2** | 两张符卡是否被既有「效果牌」集合/汇总入口统计（`EffectCardPeriod` 出牌周期、忍者立牌复制计数、魔法秘典计数、魔法箭袋计数）？ | **是**（三处计数均无类型过滤：`item/card/BaseEffectCardItem.java:467-470`；但 `cardTypeId()` 必须登记进 `:93-112` 的 `cardByTypeId` switch，否则复制/返还回退成王之力，见 `:110` 的 `default`） | 出牌后 `komachi_use_count`（`ModAttachments.java:159-160`）/ `magic_tome_use_count`（`:146-147`）/魔法箭袋计数键的只读读数；并核对忍者/秘典返还的物品**确实是符卡**而非王之力 |
| **Q3** | 「符卡-福」的 +1 与上述计数归属组合后，玩家侧净效果是否等于需求文本「出牌数 +1」（即**出牌数收支 = 消耗 1、返还 1**）？ | **是，且每次使用都成立**（§14.2 `t7` 改判后的 B 口径：按次累加 + `min(9, …)` 封顶） | 同一出牌轮连续使用两次符卡-福，逐格对照下表；读数 = **专属计数器** / `getMaxAllowed` / `effect_card_play_count` / `effect_card_cooldown_end`；**另需一条交叉验证**：「忍者主动先给 +1，再打符卡-福」在 B 口径下仍应净 0（A 口径下会失败 ⇒ 这是可判别差异） |
| **Q4** | 符卡-祸放进**普通容器**后（裁决①放行），是否**同时**停止计入厄运层数与周期伤害？ | **是**（计数源 = **主物品栏 + 副手**，§9.2；⚠️ **本轮 `t33` 用户裁决更正** —— 副手持有**计入**，旧的「计数 = 主物品栏」口径**作废** ⇒ 只有「放进容器」才停止计入；`item/chip/SmartWatchChipItem.java:57-66` 只是**主栏**写法的先例，副手由 `HuoCardItem#count()` 额外单独读一次 `getOffhandItem()`） | 只读读数对照「在主物品栏 / **在副手**」vs「在容器内」三种状态的 `misfortune` 层数与周期伤害记录；**若实现采用更强口径（全背包 / 末影箱 / 最近访问容器）则必须报出实际口径**并交 captain 决定是否接受该偏离（⚠️ **副手已由本轮裁决纳入冻结口径，不算偏离**） |

**出牌数收支表（`t7` 改判后 = B 口径的期望值；verifier 逐格对照实测，同一出牌轮、无其它加成来源）**：

| 步骤 | 消耗 | 返还 | `effect_card_play_count` | `getMaxAllowed` | 本轮冷却 | 依据 |
|---|---|---|---|---|---|---|
| 基线（未出牌） | 0 | 0 | 0 | 1 | 无 | `EffectCardPeriod.java:203-221`（基础 1） |
| 使用第 1 张符卡-福 | 1 | **+1**（专属计数 = 1） | 1 | **2** | 未启动 | 专属计数进 extra + `:438-444`（`1 < 2` ⇒ 不打满、不起冷却） |
| 同一轮再用第 2 张符卡-福 | 1 | **+1**（专属计数 = 2） | 2 | **3** | 未启动 | 同上（`2 < 3`）；**这是与 A 口径的关键分叉格** |
| 先用忍者主动 +1，再打符卡-福 | 1 | **+1**（专属计数独立记） | 1 | **3**（1 + 忍者 1 + 本卡 1） | 未启动 | B 口径与 `grantBonusPlay`（`:240-244`）**解耦** ⇒ 两来源不再互相吞噬 |
| 连续使用到封顶 | 1 | +1 | n | `min(9, …)` | **启动 30 秒**（首次出现 `count ≥ max`） | `GameplayConstants.java:31` 封顶 9 + `:438-444` |
| 同一轮再用符卡-祸（冷却进行中仍可出牌） | 1 | 0（祸卡无返还） | n+1 | 同左 | 保持原到期时刻不变 | `:388-392`（冷却进行中不拦截）+ `:418-427`（不重开周期）+ `:441`（不重置冷却） |
| 冷却到期后 | — | — | 归 0、加成清空 | 回到 1 | 结束 | `:288-295`（`clearRoundBonuses`，专属计数一并清零）+ `tick` 情形 1 |

> 判据小结（可直接写进用例）：**符卡-福的净效果 = 消耗 1 + 返还 1 = 出牌数收支平衡，且每次使用都成立**（`t7` 改判）；
> 连续使用只会把当轮上限逐次抬高、最终受全局封顶 **9** 约束，一旦 `count ≥ max` 即进入 30 秒冷却——这是**期望行为**，不是缺陷。
> `t6` 版本「第二次使用不再返还 ⇒ 必然以 `2 ≥ 2` 打满」的判据**已作废**。

**Q4 说明**：该问（符卡-祸进普通容器后的计数与周期伤害）的期望值与实证要求已并入**上表 Q4 行**，
不再重复展开；§3.3 ⚠️ 与 §12.2 第 4 条同指此问。⚠️ **交叉引用缺陷就地更正**：本文件 `t6` 版把该问写成
「§14.5 第 3 问」，而 §14.5 的第 3 问（Q3）实为**出牌数净效果**，两者并非同一问 ⇒ 现已独立为 **Q4** 并同步修正引用。

### 14.6 残余项（仍属实现方处置，须在报告中声明）

- **溢出治疗的取整余数**：§5.2 已冻结「不得无声丢数」，但「余数继续存在附件里累加」与「按次向下取整」两种实现**择一即可**，
  实现方须在报告中声明选了哪一种（不影响验收，只影响可复现性）。
  （`t8`/`t9` 实测：`v121` 选的是**整数化 + 余数留档**版本 —— `ModAttachments#addZhaoOverflowBonus`：`total = 余数 + overflow`、
  `whole = floor(total)`、余数写回 `zhao_overflow_remainder`(Float)；`whole > 0` 才加进 `zhao_overflow_bonus`(Int)。
  **无静默丢数**依据：小数部分每次都落盘（`.serialize` 键，跨重登保留），后续溢出与余数凑成新整数点（0.5 + 0.5 ⇒ bonus +1、余数归 0）；
  回收时 `clearZhaoOverflowBonus` 把 bonus 与余数**一并**归零（四条路径幂等：状态机收尾 / tick 自检 / `MobEffectEvent.Remove` / 死亡·重登）。
  ⇒ 属 §14.6 允许的两种实现之一（「余数继续累加」），**已声明、可接受**。）

### 14.7 `t8` 裁决（`v120` 回报的三点；captain 取证后的终裁）

> 取证来源：`scripts/verify/verify_bountiful_pools.ps1`（**用正确 root** 跑，见下方陷阱）+ `AGENTS.md:764-785` + 库 `EventTargetCollector.java:30-62`。

| # | 事项 | 终裁 | 证据 |
|---|---|---|---|
| **①** | `v120` 问：验收要求维护赏金池，但文档约定「传奇立牌不入 `astral_rews`」，以哪个为准？ | **文档约定优先**：**删除** `astral_rews` 里的 `zhao_sign` 条目（传奇立牌一律不进任何池）；⚠️ **本行原「同时补入 `fu_card` / `huo_card`」已被用户裁决推翻、作废 ⇒ 见 §14.8**（两张符卡是**专属效果牌**，禁止进入任何赏金池） | `AGENTS.md:768`（`rews` = `objs` ∪ 全部卡牌 ∪ 非传奇筹码 ∪ **非传奇立牌**）、`:772`（卡牌不受传奇排除）、`:773`（传奇骰子/筹码/立牌一律不进任何池）、`:779`（传奇卡价值带 2000~6500）、`:782`（条目顺序按类别插入）；同类专属牌先例 `effect_card_living_page`（`:361`）/ `effect_card_fate_guidance`（`:371`）均在池内；闸门实测两侧均报 `多余/应排除条目(1): ['zhao_sign']` + `缺失条目(2): ['fu_card','huo_card']` |
| **②** | `v120` 报「心事相连用 `getTeam()`+`isAlliedTo`」——是否接受？ | **不接受，必须改为共享库路径**：闸门 = `EventTargetCollector.hasAnyTeam(p)`；接收者 = `EventTargetCollector.collectTeamPlayers(owner)` ∩ 装备风水师立牌 | 库 `hasAnyTeam`（`:58-62`）= 原版 team ∪ FTB Teams ∪ OPAC；`collectTeamPlayers`（`:30-55`）同三源并排除自己，其「未组队 ⇒ 全服在线玩家」兜底（`:45`）必须由显式闸门挡住 —— 这正是 §12.1「只新增判定，不改库」的含义；库侧三开关默认全 `true`（`GameplayConstants:48/50/52`）。原生口径的后果：FTB/OPAC 组队玩家心意相连**永不生效**，而同服奢华大餐/银行卡/随机卡（`LuxuryFeastCardItem:57` 等 6 处）却生效 ⇒ 玩家可见不一致 |
| **③** | `v120` 报「`FU_CARD_CYCLE_BONUS` 键名/同步性待与 `v121` 对齐」 | **无需改动，两侧已对等**：键名同为 `fu_card_cycle_bonus`；1.21.1 用内联 `.sync(ByteBufCodecs.VAR_INT)`（`ModAttachments:85-89`），1.20.1 用 `.sync()` + `SYNCED_KEYS`（`:955`/`:1093-1094`） | 与两个同级计数器 `EFFECT_CARD_BONUS_PLAYS`（`:45-49`）/`LIVING_PAGE_CYCLE_BONUS`（`:62-66`）同址同步，依据 = 客户端 `isBlockedOnClient → isBurstFull → getMaxAllowed` 需看到同一份额外出牌数 |
| **④** | 闸门自身缺陷（`t8` 新发现，非 `v120` 提出） | **就地修正**：卡牌判据由「id 前缀」改为「id 前缀 **∪ 卡牌标签**」 | 原判据只认 `attack_card_`/`defense_card_`/`effect_card_` 前缀 ⇒ `fu_card`/`huo_card` 被归入 materials ⇒ **闸门对这两张卡是否入池完全失明**（实测：加入池前后分类都是 `cards 25 / materials 8`，条数不变，**1 多余 + 2 缺失恰好互相抵消**）；修正后读 `data/astral_dice/tags/{item|items}/{combat_cards,effect_cards}.json`（与 `ModItems.isCardItem` 同一判据），分类变为 `cards 27 / materials 6`；主工作树（发布线）回归复跑仍 ALL OK ⇒ 无假失败。**随后按 §14.8 的用户裁决把期望集合再改为「卡牌 − 专属效果牌」**（读 `is_exclusive.json`，并对**所有**类别剔除），新增输出行 `专属效果牌(禁止进入任何赏金池): N 项` ⇒ 池内残留**任一**专属 id 都会被 `extra` 差集报成**致命偏差**（强制力不再依赖「分类是否看得见它」） |

> ⚠️ **闸门运行陷阱（`t8` 亲历的假绿，必须遵守）**：`verify_bountiful_pools.ps1` 的 `-Root` 默认 **`'.'`**，
> 在**非目标工作树**的 cwd 下运行会去校验**另一棵树**并给出 `结果: ALL OK` 的**假绿**（实测：在会话根
> `F:\MCProject\astral_dice_multiloader` 下运行，校验的是不含本批改动的发布线树 ⇒ 全绿，而 dev 树实为 4 项致命偏差）。
> ⇒ 任何赏金池判定**必须**显式 `-Root F:\MCProject\astral_dice_multiloader-next`（或在 dev 树根下运行）。
> 该假绿与 ④ 的失明区是**同类**问题：验证工具与被验证对象不同源时，闸门给出的绿不成立。

### 14.8 用户裁决（2026-09-26，**最高优先级**）：专属效果牌**禁止进入赏金池** —— 推翻 §14.7 ① 的后半

> **用户原话**：「"符卡-福"与"符卡-祸"均为专属效果牌。严禁通过除风水师立牌以外的途径获得，遵守全局专属效果牌规则，因此禁止进入赏金池。」

- **结论（冻结）**：`fu_card` / `huo_card` **不得出现在任何赏金池**（`astral_objs` / `astral_rews`）；§14.7 ① 中「补入 `fu_card`/`huo_card`」**作废**。
  `zhao_sign` 的删除（传奇立牌）**不受影响、继续有效**。
- **captain 的错误（如实记录，含推翻过程）**：§14.7 ① 我按 `AGENTS.md:772`「全部卡牌（卡牌不受传奇排除）」的**机械读法**要求补入两张符卡，
  忽略了「**专属效果牌**」这一全局例外 —— 而该例外在本仓有**三处一致**的权威判据（下方）。该指令已**实际造成**两侧池文件出现这两个条目
  （`astral_rews.json:381`/`:391`），本次纠正：`v121` 侧已删净（闸门 `条目 93 与规则一致`），`v120` 侧由 **`t8`** 删除。
- **专属效果牌判据（双权威，必须一致）**：
  1. 数据层 `data/<ns>/tags/{item|items}/`**`is_exclusive.json`**（1.21.1 = `tags/item/`，1.20.1 = `tags/items/`）；
  2. 代码层 `item/card/RandomCardHandler`（`EXCLUSIVE_CARDS` / `registerExclusiveCard`，`:100-104`）与 `item/card/ExclusiveCardUtil#isExclusive`（`:31-33`）。
  当前集合 4 项 = `effect_card_living_page` / `effect_card_fate_guidance` / `fu_card` / `huo_card`。
- **理由（用户口径）**：赏金板本身就是一种**获取途径**；专属牌只允许由对应立牌发放。随机卡牌池早已由 `RandomCardHandler` 强制排除，赏金池属同一规则的适用面。
- **闸门已强制**：期望集合改为「**卡牌 − 专属效果牌**」（其余类别同样剔除）⇒ 池内残留**任一**专属 id 即报**致命偏差**；
  新增输出行 `专属效果牌(禁止进入任何赏金池): N 项`。
- **获取途径枚举（`fu_card`/`huo_card`，实测）**：① 风水师立牌被动「福祸相倚」/ 主动「白泽赐福」/ 大当家「心意相连」= **唯一允许途径**；
  ② `RandomCardHandler` 随机池 —— 已由 `registerExclusiveCard` 排除；③ 合成 —— **无任何配方**（全仓 `*.json` 无 `fu_card`/`huo_card` 配方引用）；
  ④ 战利品表 —— 无引用；⑤ 赏金池 —— **本次禁止**；⑥ 创造模式物品栏（`ModCreativeTabs:109-110`）—— 创造专用、非生存获取途径，
  与既有专属牌（活体书页 / 命运的指引）同级处理，**保留**。
- **两张既有专属牌的立牌来源（实测，确认清出后不会变成「无来源」）**：`effect_card_living_page` ← 调查员立牌被动
  （`event/AstralEventSystem.applyRinSignPassive:63-88`，**只发给佩戴 `rin_sign` 者** `:70`，属立牌驱动、非独立途径）；
  `effect_card_fate_guidance` ← 海清立牌（`item/sign/HaiqingSignItem:106`）。两者另有创造栏条目（`ModCreativeTabs:79/85`，创造专用）。
- ✅ **既有违规已一并清出（用户同一轮选定「一并清出」）**：`effect_card_living_page`（原 `astral_rews.json:361`）与
  `effect_card_fate_guidance`（原 `:371`）**同属专属效果牌却早已在池内**（本批之前的既存状态，非本批引入）。我按「清出会改动已发布内容」
  升级询问，用户选定「**一并清出**」⇒ 两线各自删除（`t9` = 1.21.1 / `t10` = 1.20.1），且闸门例外白名单
  `$LEGACY_POOL_EXCEPTIONS` **已清空** ⇒ 此后池内出现**任一**专属 id 一律**致命偏差**，无例外、无 WARN。
- **玩家侧影响（必须进 CHANGELOG）**：这是**玩家可见的平衡性变更**（赏金板少两个可兑换奖励：活体书页 / 命运的指引）⇒
  必须写入两份 CHANGELOG 的「内容与平衡性调整 / Content & Balance」小节（由 `scribe` / `t5` 落实）；
  工程侧规则变更（闸门判据改「卡牌 − 专属牌」+ 例外清空 + `-Root` 假绿陷阱）写 `TESTING-SPEC.md` 附录 A。
  删除**不影响**价值平衡式（这两条不是 rews top2：top2 = 9500 + 7500 = 17000，均高于 5000 / 2800）。
- **`AGENTS.md` 已同步更正**（规则行原缺该例外，正是误判源头）：`astral_rews` 定义与入池判定两处均加「**专属效果牌除外**」，
  并新增一条硬规则（含双权威判据、途径枚举、既有违规登记、`-Root` 假绿陷阱）。

### 14.9 `t11` 镜像裁决（`v121` 交付 t2 后暴露的两线偏离；captain 用**机械集合差**取证）

- **取证工具（只读）**：`temp/t75/parity-zhao.ps1` —— 抽两线 `src/main/java` 的「附件 id 字面量 / Java 常量名 / 访问器方法名」三类集合做**对称差**，
  并断言下降沿基准键存在。当前基线 `PARITY_FAIL=7`：
```
[附件 id] 1.21.1=11 / 1.20.1=7
  ONLY-1.21.1(6): huo_card_next_damage_tick, zhao_blessing_active, zhao_blessing_skip_cycles,
                  zhao_overflow_bonus, zhao_overflow_remainder, zhao_prev_blessing
  ONLY-1.20.1(2): zhao_blessing_skip_ends, zhao_overflow_attack_bonus        ← 旧键
[访问器] ONLY-1.21.1(10)：get/setZhaoBlessingSkipCycles、setZhaoBlessingActive、setZhaoPrevBlessing、
        get/set/add/clearZhaoOverflowBonus、get/setZhaoOverflowRemainder
        ONLY-1.20.1(5)：get/setZhaoBlessingSkipEnds、get/set/addZhaoOverflowAttackBonus   ← 旧名
[机制] forge 侧 zhao_prev_blessing=0 ⇒ 仍是旧 Expired 路径
```
- **成因（不是「写错」，而是基准移动）**：`v121` 在 `t3` 镜像**之后**才按规格冻结口径重构（§9.1 键名重建、§4.4 下降沿、
  §4.6②/§4.7 生命周期清理、§6.2 去重、§5.2 余数器）⇒ 已立 **`t11`**（repair，`v120`），以 `neoforge-1.21.1` 工作区为基准逐符号对齐，
  验收 = 本仪器 `PARITY_FAIL=0` + 赏金池闸门 exit 0 且零 WARN；平台写法差异（`AttachmentType` vs `AttachedDataKey` + `SYNCED_KEYS`）照 §15.1 保留。
- **裁决 A（`v121` 问 §10.1 是否拆文件）**：**不拆**。§10.1 原话是**建议**，其本意（不再挤 `DiceCombatEvents`）已由落在
  `item/sign/ZhaoSignItem#onLivingHeal` 满足 ⇒ 接受现状、不返工；§10.1 已就地更正（原文件名 `event/ZhaoHealOverflowHandler.java` 作废）。
- **裁决 B（`v121` 报告 acceptance 第 1 条已被取代）**：**确认**。「`fengshui_sign`」与「赏金板奖励池注册」两项均已被后续用户裁决取代
  （命名 = `zhao_sign`；专属效果牌禁止入池）⇒ 以最新裁决为准，`v121` 的实现与声明**无需返工**。
- **更正一处误读（如实记录）**：`v121` 报告称闸门「`$LEGACY_POOL_EXCEPTIONS` 已声明但 `Get-Expected` 尚未消费，故显示 FAIL 而非具名 WARN」——
  **不成立**。那是 captain 按用户「**一并清出**」裁决**刻意清空**白名单后的**正确行为**（两张既有专属牌此刻仍是违规 ⇒ 必须报致命偏差），
  并非漏消费；`t9` 落地后该项自然消失。
- **`t11` 验证结果（captain 独立复跑，2026-09-26）**：`temp/t75/parity-zhao.ps1` → **`PARITY_FAIL=0`**（附件 id 11:11、Java 常量 9:9、访问器方法名 12:12 **三类全等**；两线 `zhao_prev_blessing` 均在 ⇒ 下降沿机制双侧生效；`MobEffectEvent.Expired` 各 **9 处未被误删** ⇒ 骰神赐福既有逻辑完好）；赏金池闸门 → `exit 0 / ALL OK`（两线 91 条、md5 `ea5f23a2a1a3b3864860c94068ab9ea4`、零 WARN）。
- **裁决 C（`v120` 报「Expired vs tick 下降沿与 captain 旧话冲突」）**：**按 §4.4 实现，不回退**。取证：§4.4（`:318` 唯一判定点 = 玩家级 tick 的下降沿检测；`:336-339` **冻结决定**：`Expired` 在效果被外力移除时**不触发**，且**双挂会重复消费 skip ⇒ 禁止**）。
  ⚠️ **captain 在 `t3` 期对 `v120` 说的「`Expired` 正是规格指定的既有挂点」是错的** —— 依据的是 §12.1 那一行（`:657`），它与 §4.4 **自相矛盾**、属陈旧行；已就地更正为「本批**不新增调用**，`Expired` 订阅仅服务**骰神赐福自身**」。`v120` 对「§4.1 的 Expired 钩子属骰神赐福自身、二者不冲突」的判断**正确**。
- **`t11` 附带关闭的真缺陷（`v120` 发现，特此记账）**：forge 侧原缺 `tryClaimDiceJudgment` 守卫 ⇒ **一次挥击命中 N 个目标会发 N 张符卡**（1.21.1 有守卫、forge 无 ⇒ 两线行为不等）。已按 1.21.1 同形补齐（该方法 + 调用点守卫）。
  ⚠️ **不进 CHANGELOG**：该功能属**未发布**内容（`2.0.0-SNAPSHOT.10`），从未发货 ⇒ 按「未发货的批内不一致不写玩家侧修复条目」处理；如需留痕写 `TESTING-SPEC.md` 附录 A 的工程口径。
- **一处过期信息（如实记录）**：`v120` 报告称「`v121` 的 md5 目标 `7d159519…` 已达成」—— 该 md5 对应 **93 条目**（裁决前旧态，仍含 `effect_card_living_page` / `effect_card_fate_guidance`），已随 `t9` 作废；两线联合目标 = **91 条目 / `ea5f23a2a1a3b3864860c94068ab9ea4`**（已实测达成）。

### 14.10 **已裁决（`t36` 结案）**：符卡-福 `countFu()` **保持主物品栏口径**（计数口径的不对称 = 定案）

> **性质**：本条原为 `t33` 登记的**待裁决项**，**本轮（`t36`）已由用户裁决结案** ⇒ 以下为**定案**，
> 评审 / verifier / 用例**一律按此执行**；「待用户裁决」措辞**已撤销**。**该不对称不再是缺陷，也不得再当缺陷上报。**

- **用户原话（本轮，逐字）**：「符卡-福 在副手计入的意义是？本身持有并无特殊效果，因此是否计入没有意义。而目标选择器只有置于主手才应该生效。」
- **定案（冻结）**：`FuCardItem#countFu()` **保持主物品栏（`player.getInventory().items`）口径**，**不含副手** ——
  即**当前实现现状**（`t12` 已对齐，两线一致），**本轮零代码改动**（不改 `FuCardItem`、不改任何行为代码）。
  依据用户理由：符卡-福**本身持有并无特殊效果** ⇒ 「副手是否计入」在玩法上**没有意义**；而真正需要约束的是
  **目标选择器只在主手生效**（后者见 §14.11）。
- **落点与现状证据（两侧 javadoc 已写明对应关系）**：
  1.21.1 `neoforge-1.21.1/.../item/card/FuCardItem.java:103-119`、1.20.1 `forge-1.20.1/.../item/card/FuCardItem.java:99-117`；
  两侧 javadoc 显式写明「⚠️ 与 `HuoCardItem#count` 口径不同（**当前是有意的不对称**）……**本方法逻辑不得擅自改动**」
  （1.21.1 原文 `:106-110`、1.20.1 原文 `:102-108`）。
- **与 `t33` 裁决的关系（互不影响）**：
  - `HuoCardItem#count()` = **主物品栏 + 副手**（`t33` 用户裁决，§9.2）—— **继续有效、不得回退**；
  - `FuCardItem#countFu()` = **主物品栏**（本条定案）—— 两者不对称是**有意设计**，不是遗漏。
- **被推翻/作废的处置**：
  - ❌ 本文件 `t33` 版把它列为**待裁决项**（「规格不替用户拍板」）⇒ **作废**，本条即结论；
  - ❌ `t33` 版的**选项 B（把 `countFu()` 也统一为「主栏 + 副手」）** ⇒ **不再考虑**，两线**不得**据此改代码；
  - ⚠️ 副作用如实登记（**已接受**，非缺陷）：立牌 tooltip 的「符卡-福 / 符卡-祸」两栏在「牌放进副手」时**不一致**
    （祸那栏算、福那栏不算）—— 用户已就「无意义」作出裁决，评审/verifier **不得**据此报缺陷。
- **若将来要改口径**：属**新的用户裁决**，需另立任务在**两线同时**改 `countFu()`（同样**禁止**用
  `Inventory#getContainerSize()` 遍历，理由见 §9.2），并连带评估其调用点（tooltip / 探针读数）与用例断言。

### 14.11 **已裁决（`t36` 定案）**：目标选择器**仅主手生效**

> **性质**：**定案**。该行为**已是现状实现**，本轮**零代码改动**（不改 `BaseEffectCardItem` / `TargetSelectionClient` /
> `EffectCardUseGuard`）；本条只是把此前未写进规格的口径**固化**。用户原话（节选）：
> 「**而目标选择器只有置于主手才应该生效。**」

- **结论（冻结）**：本批三个动作（`zhao_blessing` / `fu_card` / `huo_card`）的目标选择器会话，
  **只有在把对应立牌 / 卡牌置于主手（main hand）时**才会被唤起；**副手持有不唤起选择器**——
  这是本规则的**直接推论**（不被任何证据路径覆盖），**不是遗漏、也不是缺陷**。
- **四条独立代码证据（行号为本工作树实测值；1.21.1 / 1.20.1 并列）**：
  1. **自动开会话入口取主手**：`item/card/BaseEffectCardItem.java:317`（1.21.1）/ `:315`（1.20.1）——
     `ItemStack stack = player.getMainHandItem();`（`tickHeldSelector`，同一方法 `:311-320` / `:309-318`；规格既有表述见 §2 的「主手手持即开局」）；
  2. **卡牌↔动作匹配只认主手**：`:266`（1.21.1）/ `:264`（1.20.1）——
     `return player != null && heldCardMatches(player.getMainHandItem(), actionId);`（`SelectorAction#matches`）；
  3. **客户端选择器**：`client/TargetSelectionClient.java:278`（两线同号，`mc.player.getMainHandItem().getItem()`）
     + `:643`（1.21.1）/ `:646`（1.20.1）（`player.getMainHandItem().getItem()` 判定）；
  4. **效果牌使用守卫**：`client/EffectCardUseGuard.java:71`（两线同号，`isEffectCard(minecraft.player.getMainHandItem())`）。
- **推论（副手不唤起）**：副手持有 ⇒ 上述 1/2/3/4 四条判据**全部不成立** ⇒ **既不自动开会话、也不匹配动作、
  客户端也不给选择提示** ⇒ 玩家侧表现为「放进副手就不触发选择器」。
- **本轮动作**：**无需改任何行为代码**；若用例要断言，建议「主手唤起（对照读数）/ 副手不唤起」两条成对读数
  （属 t35 的用例/断言口径，不在本条范围）。
- 相关：§3.2 动作 id 注册点（卡牌侧注册封装 `BaseEffectCardItem.java:174-204`）、§2 的注册入口行。

### 14.12 **已裁决（`t36` 定案）**：战斗爽**保留粒子**（`FEN_FRENZY` 的 `visible=true` 有意为之）

> **性质**：**定案**。用户原话：「**战斗爽不去除粒子（及时效果，不属于计数器类）**」。

- **定案（冻结）**：`item/sign/FenSignItem.java` 的 `handleUse` 里，**战斗爽** `FEN_FRENZY` 实例的
  **`visible=true` 是刻意保留的有意行为**，**不得**以「注释写着 `visible` 只管粒子」为由改成 `false`、或删除其漂浮粒子；
  用户理由 = 战斗爽属**即时类效果**（**不属于计数器类**）⇒ 粒子是**预期表现**。
- **实现落点（实参语义必须读对）**：
  - 1.21.1 `item/sign/FenSignItem.java:79-80`：`player.addEffect(new MobEffectInstance(ModEffects.FEN_FRENZY, FRENZY_DURATION_TICKS, 0, false, true, true));`
  - 1.20.1 `item/sign/FenSignItem.java:80-81`（`ModEffects.FEN_FRENZY.get()`）；
  - 实参 = `ambient=false, visible=true, showIcon=true` ⇒ **HUD 图标由第 6 参 `showIcon` 决定，与第 5 参 `visible` 无关**；
    `visible` 只控制「是否产生漂浮粒子」（既有措辞注释见同文件 `:72-78` / `:73-79`）。
- **对照：同一方法内另一处是**有意不同**的口径**（**不得**把两处拉平）：「养精蓄锐 ⇒ 迅捷」实例按用户 2026-09-19
  裁决已改 `ambient=false, visible=false, showIcon=true`（1.21.1 `:90-91`、1.20.1 `:91-92`，去漂浮粒子）；
  战斗爽**保留**粒子 ⇒ 两处口径不同是**有意**的。
- **正向锁定（已落地，去掉粒子即判失败）**：`scripts/test/cases/ZHAO-SIGN-1.21.1.json:437` 与
  `ZHAO-SIGN-1.20.1.json:359` 的 `FS1` 断言把 **`frenzy_visible=1`** 写成**期望值**（探针产出点：
  `scripts/test/resources/kubejs/<版本>/server_scripts/astral_bugfix_probe.js` 的 `:6064`（1.21.1）/ `:6015`（1.20.1））。
- **是否推翻既有口径**：**不推翻**任何既有实现（现状即为 `visible=true`）；推翻的是「把该处当遗留 bug 修掉」的处置。

---

## 15. 跨线实证差异对照表（`v121` ↔ `v120` ↔ 冻结线 26.1.2）

> 供两条实现线**照表施工**、供 26.1.2 解冻后**按行迁移核对**；每行都是实测 / 只读证据，不是推测。

### 15.1 同一能力在三线的落点差异

| 能力 | `neoforge-1.21.1`（v121） | `forge-1.20.1`（v120） | `neoforge-26.1.2`（冻结，只登记） |
|---|---|---|---|
| **物品标签目录名** | `data/<ns>/tags/**item**/<tag>.json`（**单数**） | `data/<ns>/tags/**items**/<tag>.json`（**复数**，Forge 1.20.1 约定） | 同 1.21.1（`tags/item/`） |
| 标签实证（Curios 立牌白名单） | `neoforge-1.21.1/src/main/resources/data/curios/tags/item/stand.json` | `forge-1.20.1/src/main/resources/data/curios/tags/items/stand.json` | 待迁移时核对 |
| 玩家数据（附件）形状 | NeoForge `AttachmentType.builder(...).serialize(Codec).sync(ByteBufCodecs)`（`component/ModAttachments.java:23-27`） | `AttachedDataKey.builder("k", Codec, () -> def).sync().build()` + 登记进 `SYNCED_KEYS`（`component/ModAttachments.java:25/94-95/764-765`） | 同 1.21.1（NeoForge `AttachmentType`） |
| 本批新键的同步策略（§9.1 的 **5** 个 + `t7` 新增的**第 6 个**） | §9.1 的 5 个一律 `.serialize(...)`、**不** `.sync(...)`；**例外**：`fu_card_cycle_bonus`（`t7` 改判引入）**必须** `.serialize(...).sync(ByteBufCodecs.VAR_INT)`（`ModAttachments:85-89`） | §9.1 的 5 个一律 `serialize`、**不** `.sync()`、**不**入 `SYNCED_KEYS`；**例外**：`FU_CARD_CYCLE_BONUS` 必须 `.sync()` 且入 `SYNCED_KEYS`（`:955`/`:1093-1094`） | 待迁移（同 1.21.1） |
| 全局治疗钩子（溢出治疗唯一挂点） | `net.neoforged.neoforge.event.entity.living.LivingHealEvent`（`LivingEntity.heal` 首行 `EventHooks.onLivingHeal`，`LivingEntity.java:1117-1123`） | `net.minecraftforge.event.entity.living.LivingHealEvent`（`LivingEntity.heal` 首行 `ForgeEventFactory.onLivingHeal`，同形同序） | 同 1.21.1 |
| 阻止主动丢弃（裁决①） | `IItemExtension#onDroppedByPlayer`（`IItemExtension.java:89`） | `IForgeItem#onDroppedByPlayer`（`IForgeItem.java:74`） | 同 1.21.1 |
| 容器内物品门槛（裁决①） | 原版 `Item#canFitInsideContainerItems()`（`Item.java:422`；NeoForge 起经 `IItemStackExtension` 消费，`Item.java:417-418` 注） | 原版 `Item#canFitInsideContainerItems()`（`Item.java:452`） | 待核对（该线另有 `assets/<ns>/items/<id>.json` 物品模型硬需求） |
| 效果注册 id 集 | `effect/ModEffects.java` 的 `EFFECTS.register(` 命中 **34** 处 | 与 1.21.1 逐条镜像 | **32** 处 ⇒ 迁移前必须先核对 id 集（§11 第 2 项） |
| 数据生成 | 单段 `runData`；物品模型由 `basicItem(...)` 生成到 `src/generated/resources/assets/.../models/item/` | 同 1.21.1（`forge-1.20.1/.../datagen/`），另有 reobf / `pushToDevRun` 产物差异（见 `AGENTS.md`） | **两段式** `runClientData` + `runServerData` + 双输出根（§11 第 4 项） |
| 目标选择器基础设施 | `target/` 包（5 类）+ 库 `starenginelib/target/`，**两发布线一致** | 同左（`forge-1.20.1/.../target/` 5 类） | 需核对立牌门控分支与选择器 API（§11 第 7 项） |
| 语言键同步单位 | 每子项目**自身** zh↔en 键集相等（`tools/check_lang_sync.ps1:418-430`，三线依次扫描 `:499`）⇒ **新增中英两键即可，不得为对齐去改被冻结的 26.1.2** | 同左 | 同左（解冻后随迁移一起补） |

### 15.2 行号基准（**读本文件任何 `文件:行号` 之前必读**）

- 全部行号基准 = **HEAD `1970de2c`**，复核方式 `git show 1970de2c:<path> | Select-String '<符号>'`；
  **不要**直接读工作区行号 —— `v121`/`v120` 正在并行修改同一批文件，工作区行号已漂移
  （实测：`neoforge-1.21.1/.../item/sign/FenSignItem.java` 的 `MAX_RECHARGE` 在 HEAD 为 `:37`、工作区为 `:39`）。
- 库文件（`F:\MCProject\starengine_lib`，分支 `main`）本批**零改动** ⇒ 其行号即当前 `main` 工作区行号。
- 原版 / 加载器证据取自 §1 头部列出的两个反编译源 jar，引用写作 `<jar 内路径>:<行号>`。

---

### 附：本文件生成时的只读证据命令（可复现）

```powershell
# 图标映射（逐字节比对）
$img='F:\MCProject\astral_dice_multiloader-next\images'
Get-FileHash "$img\反击.png","$img\风水师立牌.png","$img\符卡-福.png","$img\符卡-祸.png","$img\厄运.png" -Algorithm SHA256
# 缺失图标（应报“路径不存在”）
Test-Path "$img\白泽赐福.png"; Test-Path "$img\养精蓄锐.png"
# 治疗/掷骰消费点双侧枚举
#   （grep 工具：'\.heal\(' / 'setHealth\(' / 'rollCombatDie|rollDice' / 'attackModifiers\(\)'）
# 冒烟级别
pwsh -NoProfile -File scripts/test/mt_scope.ps1
# t6 回填后的自检（规格文件完整性 + 两树同哈希）
cd F:\MCProject\astral_dice_multiloader-next
pwsh -NoProfile -Command "Select-String -LiteralPath docs/features/fengshui-sign-spec.md -Pattern 'onDroppedByPlayer|grantBonusPlay|完整冒烟|tags/items' | Measure-Object | Select-Object -ExpandProperty Count"
pwsh -NoProfile -Command "(Get-FileHash docs/features/fengshui-sign-spec.md).Hash -eq (Get-FileHash 'F:\MCProject\astral_dice_multiloader\docs\features\fengshui-sign-spec.md').Hash"
```
