# Fabric 平台差异性清单（`fabric-1.20.1` 专项）

> **用户要求的「Fabric 平台单独差异性清单」**（2026-10-04 建制）。
> **优先级**：**P1 次要**（待 P0 = `neoforge-1.21.1` + `forge-1.20.1` 落地后再向下移植）。
> **基准**：功能面 = `neoforge-1.21.1`；**代码蓝本 = `forge-1.20.1`**（同为 MC 1.20.1 ⇒ 照搬 forge 文件 + 固定平台替换，天然保留 1.20.1 的配方 / 行为差异）。
> **可版本化载体**：本文件（`docs/` 被 `.gitignore`，不进提交）。
> **配套**：`porting/CROSS-VERSION-DIFFS.md`（四线总表）· `porting/fabric-1.20.1/{VERSION_PINS,CATCHUP-PLAN-2026-10-04,CATCHUP-PROGRESS-2026-10-04}.md` · `KNOWN-ISSUES.md` §9（KI-F*）与 KI-E3。

---

## 0. 定位与边界（不可与生产线混用）

| 项 | fabric-1.20.1 |
|---|---|
| MC / 加载器 | 1.20.1 / Fabric Loader 0.19.5 + Fabric API 0.92.12 |
| Java / 构建 | 17 / Fabric Loom |
| 优先级 | **P1**（次要；待 P0 完成后移植） |
| 定位 | **移植线**（非生产线）⇒ 不参与 P0 的「同批实施」 |
| 产物 / 发布 | `astral_dice-1.3.7-alpha.1+fabric_1.20.1.jar`；**绝不并入**生产线 Release；CI 单独出 `fabric-*` pre-release |
| 整合包 | `1.20.1-Fabric 模组测试`（本线专属测试环境） |
| 规则边界 | 见 `AGENTS.md`「多版本子项目矩阵」第四条线说明（九~十三项，仍全部有效） |

---

## 1. 平台适配差异（加载器层整体替换）

| # | 差异项 | NeoForge/Forge 侧 | Fabric 侧 | 硬约束 / 踩坑记录 |
|---|---|---|---|---|
| A1 | **饰品框架** | Curios（库 shim `CuriosCompat`） | **Trinkets**（主）/ **Accessories**（软依赖） | 「二选一」；`depends` 是 **AND 语义** ⇒ 两者都只进 `recommends`，权威判定 = `ModCompatibilityCheck#verifyAccessoryProviderOrThrow()`（`onInitialize()` 第一条语句；两个都不在才抛 `ModLoadingException`）。⚠️ 硬引用 `dev.emi.trinkets.api.*` 的类缺席时**整个类加载不了** ⇒ 守卫必须放**调用点**（KI-F15） |
| A2 | **持久化** | Capability / `Entity#getPersistentData()`（Forge 补丁） | **Fabric API 附件**（`ModAttachments`，109 键） | ⚠️ **附件键注册必须早于玩家数据反序列化**：`onInitialize()` 必须调 `ModAttachments#ensureRegistered()`（空实现，只为触发 `<clinit>`）；漏掉 ⇒ 一次登录**静默丢 33 键**（含 `guide_book_given` ⇒ 每次登录补发手册）（KI-F17）。凡改附件键集合/持久化 schema，收尾必须 grep `Unknown attachment type` = 0 |
| A3 | **事件系统** | `@Mod.EventBusSubscriber` 自动注册 / `Forge EVENT_BUS` | 自建 **`LoaderBus`** + Puzzles Lib / FAPI 回调 / mixin | 🚨 **禁 `@Mod.EventBusSubscriber` 与 `@EventBusSubscriber`**（Fabric 无效）⇒ 必须 `LoaderBus.INSTANCE.register(X.class)`；🚨 **`LoaderBus.scan` 不递归内部类** ⇒ 内部类处理器要**单独登记**（KI-F9 曾致 J/H 两键完全不响应）；护栏 = `platform/event/SubscriptionAudit`（收尾必读 `[Astral Dice] 事件订阅审计…` 行，`-Dastral_dice.strictBusAudit=true` 可升级为致命） |
| A4 | **战利品注入** | GLM（`global_loot_modifiers` / `LootTableLoadEvent`） | **`LootTableEvents.MODIFY`**（`loot/FabricLootInjector`） | 上游 forge 新增的 `onLootTableLoad(LootTableLoadEvent)` 属 **Forge 事件** ⇒ 移植时**整方法舍去**，只保留纯逻辑方法 |
| A5 | **网络** | `net.neoforged.neoforge.network.*` | `platform/network/{SimpleChannel,PacketDistributor,…}` | 动作栏唯一通道 = `ModNetwork.ActionBarMessage`（**禁裸 `displayClientMessage(…,true)`**） |
| A6 | **注册表** | `DeferredRegister` / `RegistryObject` | `platform.registry.*` | import 一律走 `com.merlinkitsune.astral_dice.platform.*`；**禁** `net.minecraftforge.*` / `net.neoforged.*`（自查：`grep -rn` 命中须为 0） |
| A7 | **配置** | Forge/NeoForge Config | Forge Config API Port（经 Puzzles Lib） | — |
| A8 | **指定药水配方** | 1.21.1/26.1.2 `DataComponentIngredient.of(true,…)`；forge `PartialNBTIngredient` | **自建 `astral_dice:nbt_shaped`**（`NbtShapedRecipe` + `StackConstraint`） | 语义基准 = `DataComponentIngredient.of(true,…)`（strict 精确匹配）；⚠️ **只改 fabric 端**，forge 的 `PartialNBTIngredient` / `potionTag(...)` **保持原样**（有意差异，勿统一） |
| A9 | **通用标签** | NeoForge 自带 `c:`；Forge `forge:` | **自建 `c:`**（对齐 NeoForge 定义） | `c:bricks` = **砖物品**（`minecraft:brick` / `minecraft:nether_brick`），**不是**砖块方块；1.20.1 标签目录 = `data/<ns>/tags/items/`（**复数**） |
| A10 | **datagen** | NeoForge/Forge `GatherDataEvent` | Fabric `FabricDataGenerator` | ⚠️ 重写构建脚本易**漏搬** `sourceSets.main.resources.srcDir('src/generated/resources')` ⇒ 生成资源**不进产物**（编译 + 启动期均无感，只见「物品紫黑、配方不存在」）⇒ 改构建脚本后**必须开包核对资源条目数**（KI-F4） |
| A11 | **内嵌库（JiJ）** | 产物内 `META-INF/jarjar/` | 同（但 Loom 会剥离 `fabric.mod.json` 的 `jars`） | ⚠️ **Loom 重映射第三方 mod 时剥掉 `jars` 声明** ⇒ dev 里 Puzzles Lib / Accessories / Patchouli / Cloth Config / KubeJS 全部起不来 ⇒ 跑 dev 前先 `ft_env.ps1 --side both --install-embedded`（= `tools/loom_embedded_jars.py --apply`）；换 `-PtestTrinkets`/`-PtestAccessories` 后**必须重跑**（KI-F23） |
| A12 | **生产映射** | 单套映射 | **dev(named) 与 prod(intermediary) 两套** | 🚨 **禁按字符串名反射原版成员**（`getDeclaredField("color")` 在 dev 命中、prod 是 `field_8908` ⇒ 启动 100% 崩，且 dev 全绿测不出）（KI-F13）⇒ **按类型/修饰符查找**；凡改**库/源码/依赖/构建配置**，除 dev 冒烟外**必须**跑 `ft_prod.ps1`（`Sound engine started` = PASS）；改过 mixin 还须加 **`-PreloadClasses`**（intermediary 名）（KI-F14） |
| A13 | **Mixin 注入** | `@ModifyConstant` 可用 | 🚨 **禁 `@ModifyConstant`** | 它与其它模组的同点注入**互斥** ⇒ 会让对方 `InjectionError` 崩游戏（KI-F14）⇒ 一律用 MixinExtras **`@WrapOperation`** 包裹**调用指令**（不同字节码位置 ⇒ 可共存） |
| A14 | **槽位图标（Accessories）** | Curios 用 `icon` 路径 | **必须落原版 `minecraft:blocks` 图集** | 图标放 `assets/<ns>/textures/gui/slot/*.png`，`icon` 写**图集 sprite 名** `astral_dice:gui/slot/…`（Trinkets 路径直连 + Accessories 图集，同一字符串对两通道同时成立）；放错 = **文件在、位置错** ⇒ 紫黑格且无日志（KI-F18）⇒ 两道门：`verify_fabric_assets.py` 第 8 项 + `AccessoriesClientIconCheck`（**须同时查「文件存在」与「图集收录」**） |
| A15 | **玩家名** | 固定 | Loom 默认**随机**用户名 | ⇒ 离线 UUID 每次变 ⇒ **跨会话缺陷在 dev 根本不可能复现** ⇒ 用 `-PdevUsername=<name>` 固定（KI-F17 的定位关键手段） |

---

## 2. 必须保留的 1.20.1 差异（用户裁决，**不得**按 1.21.1 覆盖）

| # | 项 | 说明 |
|---|---|---|
| K1 | **配方差异** | 星币锤等 1.20.1 专属配方 |
| K2 | **行为差异** | 复仇之戟等 —— 1.20.1 **不含** 1.21.1 新增效果（`MobEffects.{WIND_CHARGED,WEAVING,OOZING,INFESTED,RAID_OMEN,TRIAL_OMEN}` 无） |
| K3 | **药水原料写法** | `potionTag(...)` / 本线 `NbtShapedRecipe`（与 forge 的 `PartialNBTIngredient` 差异属有意保留） |
| K4 | **通用标签** | 自建 `c:`（NeoForge 自带 `c:`、Forge 用 `forge:`） |
| K5 | **饰品框架** | `trinkets` / `accessories`（不是 Curios） |

---

## 3. 联动模组：裁剪 / 保留表（按「平台存在性」）

> **口径**（2026-10-04 用户裁决）：**排除 fabric 端不存在的模组的全部联动**（数据 / lang 键 / 手册条目 / 伤害类型 key / tooltip 分支一并删除）；fabric 端**存在**的模组照常适配。

| 联动模组 | fabric 端 | 处置 | 状态 |
|---|---|---|---|
| `irons_spellbooks`（Iron 法术） | ❌ 无 1.20.1 Fabric 版 | **整体移除**（`IronSpellbooksCompat` 不存在；`card.fate_spell_mana` 键不补；`SpellDamageRegistry` 不含 `irons_spellbooks:*`） | ✅ 已落地（2026-09-29；`ModTooltipHandler` 只留说明注释） |
| `enigmaticlegacy`（神秘遗物） | ❌ 无 | **整体移除**：删类 `MosesEnigmaticLink`（387 行）+ `AstralDiceMod` 注册行 + `DiceCombatEvents` 4 常量/3 方法 + `FateGuidanceCardItem#onCurseMitigation` + `ModTooltipHandler` 4 处判定 + lang 7 键 + 手册 `integration.json` | ✅ 已落地（2026-10-04） |
| `bountiful`（赏金板） | ❌ 无 data 联动 | 无需处理（fabric 无 `data/bountiful/`） | ✅ |
| `patchouli`（手册） | ✅ 有 | **保留** | 无需处置 |
| `waystones` | ✅ 有 | **保留** | 无需处置 |
| `trinkets` / `accessories` | ✅ 有（fabric 特有） | **保留**（饰品「二选一」，见 A1） | 无需处置 |
| **现代化 UI（Modern UI）** | ✅ 有 | **照常适配** ⇒ 已实现（见 §4 P1-B-1） | ✅ **已适配**（2026-10-04） |

---

## 4. 平台独有待解决问题清单（逐项对照）
> ✅ **2026-10-04：P1-B-1 ~ P1-B-5 全部闭环**（下表保留原条目与本轮证据；B-6 已于追平批 5 闭环，B-7~B-10 为四线共有待裁决项 / 纪律项，非本线缺口）。

| # | 优先级 | 问题 | 状态 | 判据 / 落点 |
|---|---|---|---|---|
| **P1-B-1** | **高** | **Modern UI 提示框兼容未做** —— 客户端装 Modern UI 并启用现代化边框时，彩色边框不生效（Modern UI 用自身方法自绘描边，且 `setCanceled(true)` ⇒ 本模组 `RenderTooltipEvent.Color` **永不派发**） | ✅ **已完成**（2026-10-04） | 取证：`ModernUI-Fabric-1.20.1-3.12.0.1-universal.jar`（Modrinth `modern-ui`，sha1 `90b40fcb82e8…`）——`MixinGuiGraphics` 注入原版 `GuiGraphics`、字段与 P0 **同名同型**。实现：`client/ModernUITooltipCompat`（反射逻辑照 P0）+ `mixin/bridge/ClientTooltipBridgeMixin` 在 `renderTooltip(Font,ItemStack,II)` 的 `HEAD` 写 / `RETURN` 还 + `ModCommonConfig#MODERNUI_TOOLTIP_FRAME_COMPAT`（CONFIG_VERSION 5→6）。⚠️ Fabric 无事件优先级/`receiveCanceled` ⇒ **不能**照抄 P0 的事件写法；实机读数见 README §7.2.7.1 |
| **P1-B-2** | **高** | **近战黑名单缺「非武器工具」四项** —— `DiceCombatEvents#isMeleeWeaponAttack` **无** `Items.{SHEARS,FISHING_ROD,FLINT_AND_STEEL,BRUSH}` 排除（实测：fabric 该文件命中 0，另三线各 1）⇒ 这四件工具在 fabric 算「近战武器攻击」 | ✅ **已完成**（2026-10-04） | `combat/DiceCombatEvents#isMeleeWeaponAttack` 已补四项排除；实机断言 `AP_CATCHUP_MELEE: empty=0 shears=0 rod=0 flint=0 brush=0 sword=1`；与该函数 `forge-1.20.1` 版本**规范化平台名后逐行一致** |
| **P1-B-3** | 中 | **fabric 无测试探针**（`/astralprobe` 系列）⇒ 本线改动无法实机断言 | ✅ **已完成**（2026-10-04，**已实跑取数**） | 新增 `scripts/test/fabric/astral_catchup_probe.js`（`/astralcatchup env|melee|range|conc|legacydur`）+ 用例 `cases/FAB-CATCHUP-PARITY.json`；**用 Fabric API 自带 `FakePlayer` ⇒ 零前置、无人值守**（不必再投放 Carpet）。装法/判据/三条实测教训见 `scripts/test/fabric/README.md` **§7.2.7 / §7.2.7.1**；⚠️ 归一效果本身仍需真人玩家（KI-F25④） |
| **P1-B-4** | 中 | **KI-E3：三个守门脚本不含 fabric** —— `audit_mixin_injection` / `check_lang_sync` / `audit_actionbar` 的 `LINES` 只登记三线 ⇒ 该线 **mixin 计数、三语一致、动作栏键存在性**三面**无自动守门** | ✅ **已闭环**（2026-10-04，KI-E3 结案） | 三个脚本的 `LINES` 均已含 `fabric-1.20.1`；实测**硬违规 0 / PASS / exit=0**。⚠️ 纳入前先修掉真实滞后：11 个 `msg.astral_dice.*` 键 × 三语的值内嵌 `§`（已对齐 P0 值）+ `TargetSelectionClient` 的裸 `displayClientMessage` 改走库 `ActionBarManager.show(..., RED)` |
| **P1-B-5** | 中 | **KI-F24②：`DiceCombatEvents#onLivingDamagePre` 与三线结构不同构** —— fabric 把 `directEntity instanceof Player` 闸门提到计时器逻辑**之前** ⇒ 白泽赐福 / 降神计时器在 fabric 只认**近战**（用 `player`），三线挂在闸门之前、按「任意攻击」启表（用 `source.getEntity()`） | ✅ **已对齐 P0**（2026-10-04） | `KNOWN-ISSUES.md` KI-F24②（**可结案**）：计时器块已前移到 `directEntity instanceof Player` 闸门**之前**并改用 `source.getEntity()`；与 `forge-1.20.1` 同方法前段**逐行一致**（归一化 `CuriosApi`→`CuriosCompat` 后 diff=0） |
| **P1-B-6** | — | KI-F24① `concealment`（秘密侦探「隐匿」）整条特性缺失 | ✅ **已闭环**（追平批 5：新增 `ConcealmentEffect` + 注册 + 解除逻辑 + 图标 + lang） | `KNOWN-ISSUES.md` KI-F24① |
| **P1-B-7** | 中 | **四线共有待裁决**：`KI-F8`（`hanna_sign.3` / `sherry_sign.3` 档位词与代码稀有度不符）、`KI-G2`（医疗箱筹码重登 / 切维度可反复利用） | ⏳ 待裁决 | `KNOWN-ISSUES.md` KI-F8 / KI-G2（**四线共有、与移植无关**） |
| **P1-B-8** | 低（纪律） | **KI-F4 缺陷模式**：重写构建脚本漏搬 `sourceSets.srcDir('src/generated/resources')` ⇒ 生成资源不进产物 | 已固化 | 收尾必开包核对资源条目数 |
| **P1-B-9** | — | 跨加载器**存档不互通**（附件 vs Capability/ForgeData；Trinkets/Accessories vs Curios） | 平台差异，**不修补** | `KNOWN-ISSUES.md` KI-F2 |
| **P1-B-10** | 低（纪律） | `verify_fabric_assets.py` 白名单现**为空**；新增项必须写「为何不能修 + 需谁裁决」 | 纪律 | 同左 |

---

### 4.1 待解决问题与可选处置方案（2026-10-04 汇总；**全部待用户裁决，未擅自实施**）

> §4 的 P1-B-1 ~ P1-B-6 已闭环、KI-F7 / KI-F22 / KI-F23 / KI-E3 已处理；下表是**仍然开放**的项
> （含「四线共有」中影响本线的部分，以及库侧同类缺陷）。每项给出「问题 / 现状 / 可选方案」，请逐项裁决。

| # | 问题 | 现状 | 可选方案（含代价） | 建议 |
|---|---|---|---|---|
| **Q1** | **KI-F8 手册档位词 ≠ 代码稀有度**：`hanna_sign.3` 写「稀有档 Rare」、`sherry_sign.3` 写「史诗档 Epic」，而两者代码都是 `AstralRarities.bizarre()`（奇特） | 只登记未改（**四线共有**，与移植无关） | ①**以代码为准**：两条文案改「奇特档」——需先定 `bizarre` 的英/日写法（`astral_dice:bizarre` 可作英文候选；**日文无先例**）；②**以文案为准**：改代码稀有度（`bizarre()`→`rare()`/`epic()`）——连带改 tooltip 配色与整个稀有度分布；③维持现状 | **①**（改动最小、不动玩法数值）；需先敲定日文术语 |
| **Q2** | **KI-G2 医疗箱筹码「重登 / 切维度」可反复白刷回血**：`PlayerLoggedInEvent` / `PlayerChangedDimensionEvent` 均 `refreshMedkitEquipSession + triggerMedkitOnEquip`，而 `equipTrigger` 的 `triggerHealing` **无条件** ⇒ 每次重登 / 每次过门再完整触发一次（层数满 32 时直接按 32×2 回血） | 只登记未改 —— 属用户 2026-10-01 裁决「这两个时点各触发一次」的**数学必然推论** | ①**接受为设计**（0 改动，文档如实披露）；②**节流**（同玩家 N 秒内最多一次，或仅当「闸门上次因**死亡**释放」时才触发）；③**撤掉这两个时点**（只留「装备时」+「重生后」；= 删两处调用 + 手册文案回退） | **②**（保留裁决意图、堵住无限刷）；⚠️ 三条均不得改「装备时 / 重生后必触发」与三档概率 / `HEALING_TIMER_SECONDS=60` / `HEALING_POINT_CAP=32` |
| **Q3** | **KI-F25② 裸 `removeEffect` 在效果不存在时抛 `CancellationException`**（`astral_dice:` 效果的外部移除被**设计性**拦截 ⇒ 拦截器取消了一个未被声明为可取消的回调） | 已登记，**未做决定性复现** | ①跑一次决定性复现（三条路径：裸 `removeEffect`(效果不存在) / `/effect clear` / 喝牛奶），确认后修（拦截器加 `cancellable()` 判定或前置存在性检查）；②维持现状（原版与本模组自身都先判存在性，影响面小） | **①**（探针可做的无人值守项，一次即定性） |
| **Q4** | **KI-F25④ `FakePlayer` 不进世界 tick 循环** ⇒ 依赖「玩家级每 N tick」的行为（如 `normalizeLegacyInfiniteDurations`）**无法**在本线端到端断言 | 已如实登记（测试能力边界，非产品缺陷） | ①带**真人玩家 / 客户端进世界**验证（需用户在场）；②维持（该特性已有静态判据 + 另三线实机读数） | **②**（不影响产品正确性） |
| **Q5** | **KI-F21-① 进世界验证未跑**：`ft_prod.ps1` 的 quickplay 通道曾**误删存档** ⇒ 世界内行为（附件注册 / ∞ 显示 / 效果面板悬停注释 / 配方实际可合成）未在 fabric 实机确认 | 刻意未跑（该参数已加护栏，需显式 `-AcknowledgeQuickPlayDestructive`） | ①授权该参数 + **先备份存档**后跑一次；②由**用户手动**进世界目视；③维持不做 | **①或②**（用户在场时一次性做完，覆盖面最大） |
| **Q6** | **KI-F21-② 上游缺陷（主仓）**：`multi-main` 删了 lang 键 `astral_dice.guide.entry.special_effects.6`，但 `forge-1.20.1` 手册 `getting_started/special_effects.json` 仍引用它 ⇒ 手册该行显示**原始键名**（fabric 线已用「保留键」规避） | 主仓未裁决 | ①改手册（删 / 改该 page）；②恢复该 lang 键；③维持（fabric 已规避） | **①**（与手册条目结构一致；P0 两线同批） |
| **Q7** | **库侧 2 处同类反射缺陷**（KI-F22 附带，**影响全部四线**）：`EventTargetCollector` 在 `TeamManager` 上反射 `getTeamForPlayer(Player)/(UUID)`（**均不存在**）；OPAC 类名 `dev.darkhax.opac.*` **不存在** | 已登记，交库侧下次发版 | ①库侧修（下次 bump 一并，`tools/verify_party_api.py` 已能自动比对契约）；②维持（该后端本就不生效，仅影响第三方联动） | **①**（修复成本低、已有自动守门） |
| **Q8** | **纪律项（非缺陷）**：P1-B-8 = 改构建脚本后**必须开包核对**资源条目数（否则生成资源静默不进产物）；P1-B-9 = 跨加载器存档不互通（附件 vs Capability；Trinkets/Accessories vs Curios）**平台差异、不修补**；P1-B-10 = `verify_fabric_assets.py` 白名单为空，新增项须写「为何不能修 + 需谁裁决」 | 已固化 | ①维持现有纪律（人工执行）；②把 P1-B-8 的「开包核对」**自动化**为守门脚本（构建后自动比对产物资源条目数）；P1-B-9 维持（无解，仅文档披露） | **②**（一次性投入，之后自动守门） |

> ⚠️ 上表**所有方案均待用户裁决**；未获裁决前不实施任何一条。
> 复算/取证入口：`tools/verify_fabric_assets.py`、`tools/verify_party_api.py`、
> `scripts/test/fabric/{ft.ps1,ft_prod.ps1}`、`scripts/test/fabric/cases/FAB-CATCHUP-PARITY.json`。

---

## 5. 平台适配要点（写代码时必须遵守 — 速查）

```
平台替换表（forge → fabric）
  starenginelib.item.CuriosCompat       -> astral_dice.compat.curios.CuriosApi
  top.theillusivec4.curios.api.*        -> astral_dice.compat.curios.*
  net.minecraftforge.eventbus.api.*     -> astral_dice.platform.event.*
  net.minecraftforge.event.entity[.x].* -> astral_dice.platform.event.entity[.x].*
  net.minecraftforge.event.TickEvent    -> astral_dice.platform.event.TickEvent
  import net.minecraftforge.fml.common.Mod; -> 删（⚠️ 必须带 `import ` 前缀，否则吃掉换行 → `import import ...`）
  @Mod.EventBusSubscriber(...)          -> 删（改 LoaderBus.INSTANCE.register 显式登记）
  MinecraftForge.EVENT_BUS.post(        -> LoaderBus.INSTANCE.postEvent(
```

**三条硬约束**（违反会引入回归）：
1. 🚨 **不可整文件照搬的混合文件**（含已裁决差异或 fabric 独有内容）：`combat/DiceCombatEvents`、`item/card/FateGuidanceCardItem`、`combat/SpellDamageRegistry`、`event/ModTooltipHandler`、`effect/ModEffects`、`datagen/ModRecipeProvider`（`forge:bricks` 属 1.20.1 专属）、**`event/PlayerLifecycleHandler`（含 fabric 独有 `AP_FAB_GUIDEBOOK` 诊断 + `countGuideBooks`）** ⇒ **逐段局部补**。
2. 🚨 **删块后必跑花括号平衡自检**（曾因残留孤立 `}` 与 `@SubscribeEvent` 在 **BUILD FAILED** 状态下提交）。⚠️ 删方法的起点必须**包含其注解行**。
3. ⚠️ 照搬后**必须确认事件注册覆盖**（删注解后若类里有 `@SubscribeEvent` 而 `LoaderBus` 未登记该 `X.class` ⇒ 事件**静默不生效**）。

---

## 6. 已完成批次（截至 2026-10-04）

| 批 | 内容 | 提交 |
|---|---|---|
| 1 | 常驻效果时长残留归一（`normalizeLegacyInfiniteDurations`） | `87035d20` |
| 2 | 效果牌目标口径族（狂暴 / 加急加快 / 奢华大餐 → `TargetType.NON_HOSTILE`；奢华大餐亡灵魔法伤害；`isEffectCardAction` / `isLivingPageAction`） | `0688d692` |
| 3 | 射程系统（`SelectorRangeModifiers` 两段夹取；`RinPageRangeEffect`；卫星改常驻 +50%） | 同上 |
| 4 | 立牌受击族（`BaseSignItem` 受击闸门 + `onHurt(DamageSource)`；Lulu / Papara / Zhao / Teru / Fen / BigBowlStew / BufferShield） | 同上 |
| 5 | 隐匿效果链（`ConcealmentEffect` + 注册 + `InvestigationEventUtil` + `DiceCombatModifiers` + 图标） | 同上 |
| — | 神秘遗物联动整体移除（含编译修复） | `45405f0e` + `7ff6d652` |
| 7 | 输入收口（`KeyBindingSetup` J/H；`TargetSelectionClient` 右键收起 + 中键/侧键放行） | `fd3461a8` |
| 8 | 动作栏通道与染色收口（9 处立牌 + `CurrentCore` / `Airbag` / `ModMenuTypes` / `StarCoinWalletActions`） | 同上 |

| 9 | **追平收尾批**：Modern UI 提示框边框兼容 / 近战黑名单四项 / `onLivingDamagePre` 计时器启表对齐 P0 / 三守门脚本纳入 fabric（并修掉 11 键 `§` 内嵌与裸通道）/ fabric 追平探针 + 用例（**已实跑取数**） | 见本批提交 |
> ✅ **2026-10-04：以下 5 项已全部闭环** —— 见 §4 与批 9。

---

## 7. 复算方式（可重跑）

- **滞后测绘**：`porting/fabric-1.20.1/gap-report/semantic_gap.py`（语义差）、`forge_only_lines.py`（forge 独有实质语句行）。
- **资源闭环**：`python tools/verify_fabric_assets.py`（8 项；0=PASS / 1=缺陷 / 2=缺产物 jar）。
- **队友判定契约**：`python tools/verify_party_api.py`（四线分线校验）。
- **生产映射冒烟**：`pwsh -NoProfile -File scripts/test/fabric/ft_prod.ps1 -Instance <整合包目录> -McRoot <D:\.minecraft> -Java <java.exe> [-PreloadClasses 'net.minecraft.class_XXXX']`。
- **dev 冒烟**：`scripts/test/fabric/ft.ps1` / `ft_env.ps1`（**不得**复用 `mt.ps1` 生产线口径）。

---

## 8. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-04 | 建档：按用户裁决建立 Fabric 平台专项差异性清单（平台适配 15 项 + 保留差异 5 项 + 联动裁剪表 + 待解决问题 10 项 + 已完成批次 1~8）。 |
| 2026-10-04 | **剩余 5 项待办全部完成**：Modern UI 兼容（Fabric 版 jar 取证）/ 近战黑名单四项 / `onLivingDamagePre` 对齐 P0 / 三守门纳入 fabric（KI-E3 闭环）/ 追平探针 + 用例（实机取数见 README §7.2.7.1）。§4 状态列已逐项更新。 |
