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
| **现代化 UI（Modern UI）** | ✅ 有 | **照常适配** ⇒ 见 §4 待办 P1-B-1 | ⏳ **待办** |

---

## 4. 平台独有待解决问题清单（逐项对照）

| # | 优先级 | 问题 | 状态 | 判据 / 落点 |
|---|---|---|---|---|
| **P1-B-1** | **高** | **Modern UI 提示框兼容未做** —— 客户端装 Modern UI 并启用现代化边框时，彩色边框不生效（Modern UI 用自身方法自绘描边，且 `setCanceled(true)` ⇒ 本模组 `RenderTooltipEvent.Color` **永不派发**） | ⏳ **待办**（唯一剩余功能缺口） | 需先下载 **fabric 版** Modern UI jar 取证其描边 API（类名/字段可能与 Forge 分支不同），再照 P0 两线 `client/ModernUITooltipCompat` 结构实现（`@SubscribeEvent(priority=HIGHEST)` 写入 + `priority=LOWEST, receiveCanceled=true` 还原）+ `ModCommonConfig` 的 `modernui_tooltip_frame_compat` 开关 |
| **P1-B-2** | **高** | **近战黑名单缺「非武器工具」四项** —— `DiceCombatEvents#isMeleeWeaponAttack` **无** `Items.{SHEARS,FISHING_ROD,FLINT_AND_STEEL,BRUSH}` 排除（实测：fabric 该文件命中 0，另三线各 1）⇒ 这四件工具在 fabric 算「近战武器攻击」 | ⏳ **待办** | `fabric-1.20.1/.../combat/DiceCombatEvents.java` `isMeleeWeaponAttack`；蓝本 = `forge-1.20.1` 同函数（同为 1.20.1）。⚠️ 该文件含已裁决差异（神秘遗物移除）⇒ **逐段局部补**，勿整文件照搬 |
| **P1-B-3** | 中 | **fabric 无测试探针**（`/astralprobe` 系列）⇒ 本线改动无法实机断言 | ⏳ 待补 | `CATCHUP-PROGRESS-2026-10-04.md` §9；fabric 测试机制自建（`scripts/test/fabric/`，**不得**直接复用 `mt.ps1` 生产线口径） |
| **P1-B-4** | 中 | **KI-E3：三个守门脚本不含 fabric** —— `audit_mixin_injection` / `check_lang_sync` / `audit_actionbar` 的 `LINES` 只登记三线 ⇒ 该线 **mixin 计数、三语一致、动作栏键存在性**三面**无自动守门** | ⚠️ 未决 | 候选处置 = 加入 `LINES`；⚠️ 会**立即暴露** fabric 既存全部 mixin 读数（含 intermediary 名/描述符解析差异）⇒ **必须先采 baseline 并逐条裁决**，不宜与功能修复混批 |
| **P1-B-5** | 中 | **KI-F24②：`DiceCombatEvents#onLivingDamagePre` 与三线结构不同构** —— fabric 把 `directEntity instanceof Player` 闸门提到计时器逻辑**之前** ⇒ 白泽赐福 / 降神计时器在 fabric 只认**近战**（用 `player`），三线挂在闸门之前、按「任意攻击」启表（用 `source.getEntity()`） | ⏳ **待裁决**（既存语义分叉，非 1.3.6 引入） | `KNOWN-ISSUES.md` KI-F24② |
| **P1-B-6** | — | KI-F24① `concealment`（秘密侦探「隐匿」）整条特性缺失 | ✅ **已闭环**（追平批 5：新增 `ConcealmentEffect` + 注册 + 解除逻辑 + 图标 + lang） | `KNOWN-ISSUES.md` KI-F24① |
| **P1-B-7** | 中 | **四线共有待裁决**：`KI-F8`（`hanna_sign.3` / `sherry_sign.3` 档位词与代码稀有度不符）、`KI-G2`（医疗箱筹码重登 / 切维度可反复利用） | ⏳ 待裁决 | `KNOWN-ISSUES.md` KI-F8 / KI-G2（**四线共有、与移植无关**） |
| **P1-B-8** | 低（纪律） | **KI-F4 缺陷模式**：重写构建脚本漏搬 `sourceSets.srcDir('src/generated/resources')` ⇒ 生成资源不进产物 | 已固化 | 收尾必开包核对资源条目数 |
| **P1-B-9** | — | 跨加载器**存档不互通**（附件 vs Capability/ForgeData；Trinkets/Accessories vs Curios） | 平台差异，**不修补** | `KNOWN-ISSUES.md` KI-F2 |
| **P1-B-10** | 低（纪律） | `verify_fabric_assets.py` 白名单现**为空**；新增项必须写「为何不能修 + 需谁裁决」 | 纪律 | 同左 |

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

> 剩余：§4 的 **P1-B-1（Modern UI）** 与 **P1-B-2（近战黑名单）** 两项功能缺口 + P1-B-3~B-5 的测试/守门/裁决项。

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
