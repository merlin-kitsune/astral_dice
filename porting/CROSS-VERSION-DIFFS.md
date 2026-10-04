# 跨版本功能差异与待解决问题总表（移植作业索引）

> **基准（功能面）**：`neoforge-1.21.1`。
> **优先级口径**：本表与 `AGENTS.md`「版本优先级与跨线移植纪律（2026-10-04 用户裁决）」同源，冲突时以 AGENTS 为准。
> **可版本化载体**：本文件与 `porting/fabric-1.20.1/FABRIC-DIFFS.md`（`docs/` 被 `.gitignore`，不进提交）。
> **用途**：AGENT 做功能移植时的**逐项对照清单** —— 每条给出「优先级 / 差异项 / 处置 / 待解决问题」。

---

## 0. 使用方式（移植前必读）

1. **先定优先级与时序**：P0（`neoforge-1.21.1` + `forge-1.20.1`）**先落地**，P1（`neoforge-26.1.2` + `fabric-1.20.1`）**后移植**。
2. **再判「滞后 vs 合理差异」**：见第 2 节的「三类合理差异」。⚠️ **禁止**把「对端独有行」直接当滞后 —— 实测其中很大比例是**有意的平台适配**（例：`EliteTargets` 读 Forge 补丁方法 `Entity#getPersistentData()`；fabric 已改用附件）。
3. **逐项对照本表**：按「差异项」定位文件 → 按「处置」执行 → 按「待解决问题」收尾。
4. **测绘判据**：⚠️ **禁用**「方法名集合差」（漏数值/实现变化）与「跨版本 diff 行数」（26.1.2 实测 1097 行差异中仅 1 项是真缺口，余者皆平台差异）。**用语义差判据**（对端独有实质语句行本线不存在）。复算脚本：`porting/fabric-1.20.1/gap-report/{semantic_gap.py,forge_only_lines.py}`。

---

## 1. 优先级与基准（速览）

| 线 | 优先级 | 代号 | 基准 | 时序 | 一致性要求 |
|---|---|---|---|---|---|
| `neoforge-1.21.1` | **P0** | 基准线 | 自身 | 与 `forge-1.20.1` 同批 | —（差异以此为基准） |
| `forge-1.20.1` | **P0** | 同侪 | `neoforge-1.21.1` | 与基准线同批 | **功能对等**；只允许第 2 节三类差异 |
| `neoforge-26.1.2` | **P1** | 向上移植 | `neoforge-1.21.1`（代码蓝本同线） | **待 P0 完成后** | 逐项对等 + 平台/版本差异登记 |
| `fabric-1.20.1` | **P1** | 向下移植 | `neoforge-1.21.1`（功能面）+ **`forge-1.20.1`（代码蓝本，同 MC 1.20.1）** | **待 P0 完成后** | 逐项对等 + 平台差异登记 + 联动裁剪 |

**移植方向**：`26.1.2` = 1.21.1 → 26.1.2（向上）；`fabric` = 1.20.1/1.21.1 → fabric（向下）。
**关键省工判据**：fabric 与 forge **同为 MC 1.20.1** ⇒ 凡 forge 已实现的功能，**照搬 forge 文件 + 固定平台替换**，天然保留 1.20.1 的配方 / 行为差异；只有 forge 也没有的功能才从 1.21.1 降级移植。
⚠️ **但「整文件照搬」只适用于平台中性文件** —— 平台敏感类**必须逐段局部改**（实测对 35 文件整文件照搬 ⇒ 61 编译错 / 26 文件失败，已回滚）。

---

## 2. 三类合理差异（P0 内部允许；P1 另加平台差异）

| # | 类别 | 判据 | 示例 |
|---|---|---|---|
| ① | **游戏版本差异** | MC 版本存废的原版 API / 物品 / 效果 | `MaceItem` / `Items.MACE`（1.21 新增）；`MobEffects.{WIND_CHARGED,WEAVING,OOZING,INFESTED,RAID_OMEN,TRIAL_OMEN}`（1.21 新增）；`ItemTags.SPEARS`（26.1.2 才有） |
| ② | **平台差异** | 加载器（Forge ↔ NeoForge ↔ Fabric）的机制 | 事件系统、注册 API、Capability ↔ 附件、datagen 事件、GUI 渲染入口、Curios ↔ Trinkets/Accessories |
| ③ | **联动模组本身差异** | 某线装/未装某模组，或其注册名不同 | 1.20.1 神秘遗物注册名 = `enigmaticlegacy:` 而非 `enigmaticlegacyplus:` |

---

## 3. 四线功能差异清单（相对 `neoforge-1.21.1` 基准）

### 3.0 `neoforge-1.21.1`（P0 · 基准）— 无差异

差异判定一律以本线为准。本线自身未决项见 `KNOWN-ISSUES.md` §2~§8。

### 3.1 `forge-1.20.1`（P0）— 差异清单

| # | 差异项 | 类别 | 处置（已落地 / 需做） | 载体 |
|---|---|---|---|---|
| F1 | Gradle 插件 = `legacyforge`；Java **17** | ② | 已落地 | `forge-1.20.1/build.gradle` |
| F2 | **dev / prod 两套产物**：dev = `build/devlibs`（Mojmap），prod = `build/libs`（SRG，经 `reobfJar`） | ② | 已落地（`pushToDevRun` 取 devlibs、`pushToGame` 取 libs + `dependsOn reobfJar`） | 同左 |
| F3 | `mods.toml` 依赖用 `mandatory=true/false`（NeoForge 的 `type="required"` 格式**无效**） | ② | 已落地 | `mods.toml` |
| F4 | 事件重映射（33 处理器）；`@SubscribeEvent` 成员**不能 private**（Forge EventAccessTransformer 类加载期检查） | ② | 已落地 | `compat/` shim + `ModEventHandlers` |
| F5 | `TickEvent.PlayerTickEvent` 用 `event.player` 字段（无 `getEntity()`） | ② | 已落地 | 各 `PlayerTickEvents` |
| F6 | `ItemTooltipEvent.getEntity()` 直接返回 `Player` | ② | 已落地 | `ModTooltipHandler` |
| F7 | `MobEffectInstance.getEffect()` 直接返回 `MobEffect`；`MobEffects.X` 直接引用（**无 `.value()`**） | ② | 已落地 | `effect/*` |
| F8 | `RegisterGuiOverlaysEvent.registerAbove` 的 id 是**纯 path**（传 `modid:id` 会崩） | ② | 已落地 | GUI overlay 注册 |
| F9 | `RegisterMenuScreensEvent` **不存在** ⇒ `FMLClientSetupEvent` + `MenuScreens.register` | ② | 已落地 | 客户端 setu p |
| F10 | API 机械替换：`ResourceLocation.fromNamespaceAndPath/parse` → `new ResourceLocation`；`BuiltInRegistries.MOB_EFFECT.getHolder` → `ForgeRegistries.MOB_EFFECTS`；`Capabilities.ItemHandler` → `ForgeCapabilities.ITEM_HANDLER`（`LazyOptional` 需 `.orElse(null)`）；`lookupOrThrow` → `registryOrThrow` | ② | 已落地（33 处） | 各调用点 |
| F11 | 饰品走库 shim `CuriosCompat`（`top.theillusivec4.curios.api.*`） | ② | 已落地 | `compat/curios` |
| F12 | **`MaceItem` / `Items.MACE` 无** ⇒ 近战「重锤」分支移除；**星币锤配方的中位格**用 `Items.ANVIL` | ① | 已落地（**配方差异须保留**，不得按 1.21.1 覆盖） | `DiceCombatEvents` / `ModRecipeProvider` |
| F13 | `MobEffects.{WIND_CHARGED,WEAVING,OOZING,INFESTED,RAID_OMEN,TRIAL_OMEN}` 无 ⇒ **复仇之戟**对应加成条件移除 | ① | 已落地（**行为差异须保留**，1.20.1 不含 1.21.1 新增效果） | `RevengeHalberd*` |
| F14 | 标签目录 `data/<ns>/tags/items/`（**复数**）；配方目录 `data/<ns>/recipes/`（**复数**） | ② | 已落地 | 资源树 |
| F15 | **通用标签命名空间 = `forge:`**：Forge 47.4.10 **无汇总 `forge:bricks`** ⇒ 自建 `data/forge/tags/items/bricks.json`（= `#forge:ingots/brick` ∪ `#forge:ingots/nether_brick`）。⚠️ fabric 自建 `c:`、NeoForge 用自带 `c:` —— **三套不同** | ② | **已落地**（2026-10-04 修；此前误写 `c:bricks` ⇒「对怪板砖」永不可合成） | `ModRecipeProvider` + `data/forge/tags/items/bricks.json` |
| F16 | datagen：`RecipeOutput` → `Consumer<FinishedRecipe>`（`.save(output::accept)`） | ② | 已落地 | `datagen/*` |
| F17 | 药水原料：`NBTIngredient` → `StrictNBTIngredient.of(...)`；`potionTag(...)` **保持**（1.20.1 专属，**不得**按 fabric / 1.21.1 统一） | ② | 已落地（有意保留的差异） | `ModRecipeProvider` |
| F18 | 神秘遗物注册名 = **`enigmaticlegacy:*`**（非 `enigmaticlegacyplus:*`）；`ModList.isLoaded("enigmaticlegacy")` | ③ | 已落地（`docs/compat-1.20.1-forge.md` §6 附核对清单） | `ModEventHandlers` / `ModTooltipHandler` |
| F19 | Iron / Ars / Goety 白名单为 **damage-type 字符串**（版本无关）；FTB / OPAC / 女仆走反射 | ②③ | 已落地 | `SpellDamageRegistry` / `PartyRelations` |
| F20 | 其它 API：`AttributeModifier(UUID,String,double,Operation)`；`ADDITION/MULTIPLY_TOTAL`；`FoodProperties.getSaturationModifier()`；`ItemStack.consume` → `shrink`；`AbstractContainerScreen.renderSlot` **私有**（卡牌栏「大卡缩放」退化为常规大小，**视觉差异**）；`mouseScrolled` 3 参；`LivingChangeTargetEvent.getNewTarget()`；`NbtOps` 在 `net.minecraft.nbt`；`ForgeGui`/`IGuiOverlay`/`VanillaGuiOverlay` 在 `client.gui.overlay` | ①② | 已落地 | 各调用点 |
| F21 | 持久化走 Forge Capability / `Entity#getPersistentData()`（**Forge 补丁方法**，原版无） | ② | 已落地 | `component/*` / `EliteTargets` |
| F22 | 常驻效果时长归一（旧存档 `MAX_VALUE` 递减残留 ⇒ 界面显示超长而非 ∞） | —（跨线同源） | **已落地**（三线同批；判据 `PlayerTickEvents#normalizeLegacyInfiniteDurations`） | 同左 |
| F23 | Modern UI 提示框边框兼容（`ModernUITooltipCompat`） | ② | **已落地三条线**（P0 两线 + `fabric-1.20.1`，2026-10-04）；⚠️ **26.1.2 无该机制**（无 `RarityTooltipFrame`）⇒ 平台差异，不做兼容层 | `client/ModernUITooltipCompat` |

> 完整平台差异（含 33 处查找 API 机械替换、§8 无需处理项、§8.5 编译循环发现的补充差异）见 `docs/compat-1.20.1-forge.md`。

### 3.2 `neoforge-26.1.2`（P1 · 向上移植）— 差异清单

| # | 差异项 | 类别 | 处置（已落地 / 需做） | 载体 |
|---|---|---|---|---|
| N1 | 类/包重命名：`Identifier` ↔ `ResourceLocation`；**包结构拆分**（`monster.zombie.*` 含 `Zombie`/`ZombifiedPiglin`/`Husk`/`Drowned`/`ZombieVillager`；`monster.skeleton.WitherSkeleton`）；`net.minecraft.util.Util` | ① | 已落地（照抄 1.21.1 import 会「找不到符号」） | 各 import |
| N2 | `Item.Properties#itemIdOrThrow` ⇒ **物品注册必须** `registerItem(name, props -> new XxxItem(props…))`（旧写法注册期 NPE「Item id not set」） | ① | 已落地 | `ModItems` |
| N3 | **datagen 两段式**：无 `data` 运行类型，只有 `runClientData` + `runServerData`；**双输出根**（`src/generated/resources` + `src/generated/clientResources`）；`HashCache` 会互删；`GatherDataEvent` 是**抽象类**（须注册 `.Client`/`.Server`）；`@EventBusSubscriber` 只剩 `value()`/`modid()`；`ExistingFileHelper` / NeoForge `client.model.generators.ItemModelProvider` **不存在** | ① | 已落地 | `datagen/*` / `build.gradle` |
| N4 | 配方 ingredient **必须字符串**（`{"item":"X"}` 会整份丢弃、只留一行日志）；`global_loot_modifiers.json` **索引已废除**；`crafting_special_suspiciousstew` 序列化器**不存在** | ① | 已落地 | `data/**` |
| N5 | gamerule：**键名 snake_case**（`naturalRegeneration`→`natural_health_regeneration`；`doFireTick` **已删** ⇒ 用 `fire_spread_radius_around_player 0`）；存 `saves/<world>/data/minecraft/game_rules.dat`（**不再在 `level.dat`**） | ① | 已落地 | `scripts/test/mt_env.ps1` |
| N6 | 客户端「提取-渲染状态」架构（`GuiGraphicsExtractor` / `GuiLayer`）；`blit` / `text` 语义陷阱；禁止跨版本抄投影公式 | ① | 已落地 | `client/*` |
| N7 | Curios 15：饰品栏尺寸**只能**经「槽位修饰符 → `update()`」改写（禁直接 `grow/shrink`，否则崩服） | ① | 已落地 | `DiceCurioItem` |
| N8 | Mixin 目标漂移：`PiglinAi#isWearingSafeArmor`、`Gui#renderEffects→extractEffects`、`EffectRenderingInventoryScreen→EffectsInInventory` | ① | 已落地 | `mixin/*` |
| N9 | **已删 mixin**：`fixes/DamageStackSanitizer`、`LivingEntityDamageContainerMixin`（26.1.2 上游**已修**，删除正确） | ① | 已落地 | — |
| N10 | **26.1.2 独有**：`ItemTags.SPEARS`（7 种长矛）已纳入骰神赐福近战判定。⚠️ **该常量本线独有，不得回移 1.21.1 / 1.20.1** | ① | 已落地 | `DiceCombatEvents` |
| N11 | 物品模型定义 `assets/<ns>/items/<id>.json` 是**硬需求**（非 `ModItems` 物品须手工补） | ① | 已落地 | `assets/**/items/` |
| N12 | `RecipeSerializer` record 化；`runData` 前须移出 `run/26.1.2/mods` 的探针运行时（KubeJS/Rhino/BAT） | ① | 已落地 | `datagen/*` / 工具链 |
| N13 | **Iron 联动 = 反射版保留**（本线无法声明 `compileOnly` 依赖；模组未装时 `init()` 直接返回）—— **唯一特例** | ②③ | **已落地**（2026-10-04） | `event/IronSpellbooksCompat` |
| N14 | **神秘遗物+（`enigmaticlegacyplus`）联动整体移除**（平台不存在该模组） | ③ | **已落地**（2026-10-04：删类 `MosesEnigmaticLink` + 4 常量/3 方法 + 4 处 tooltip 判定 + lang 4 键） | — |
| N15 | 保留联动：`patchouli` / `waystones` / `curios` / `bountiful` / JEI | ③ | 无需处置 | — |
| N16 | Modern UI 提示框机制**本线不存在**（无 `RarityTooltipFrame`）⇒ 属**平台差异**，不做兼容层 | ② | 无需处置（已登记） | — |

> 完整编译期 API 映射与运行期差异见 `docs/compat-26.1.2-neoforge.md` + `AGENTS.md`「neoforge-26.1.2 关键差异速记（相对 neoforge-1.21.1）」。

### 3.3 `fabric-1.20.1`（P1 · 向下移植）— 差异清单

> ⚠️ 本线的**详细清单见 `porting/fabric-1.20.1/FABRIC-DIFFS.md`**（用户要求的「Fabric 平台单独差异性清单」）。下表为摘要。

| # | 差异项 | 类别 | 处置 |
|---|---|---|---|
| B1 | 加载器层**整体替换**：Curios → Trinkets(+Accessories 软依赖)；Capability → Fabric API **附件**；Forge EventBus → 自建 `LoaderBus` + Puzzles Lib / FAPI 回调 / mixin；GLM → `LootTableEvents.MODIFY` | ② | 已落地 |
| B2 | **禁 `@Mod.EventBusSubscriber`** ⇒ `LoaderBus.INSTANCE.register(X.class)`；`LoaderBus.scan` **不递归内部类**；`SubscriptionAudit` 护栏 | ② | 已落地 |
| B3 | **dev / prod 两套映射**（intermediary）：**禁按字符串名反射原版成员**；`ft_prod.ps1` 生产冒烟 + `-PreloadClasses` | ② | 已落地 |
| B4 | **禁 `@ModifyConstant`** ⇒ MixinExtras `@WrapOperation`（与其它模组同点注入**可共存**） | ② | 已落地 |
| B5 | 附件键注册**必须早于玩家数据反序列化**（`ModAttachments#ensureRegistered()`，空实现只触发 `<clinit>`） | ② | 已落地 |
| B6 | Accessories 槽位图标**必须**落原版 `minecraft:blocks` 图集目录 `textures/gui/slot/` | ② | 已落地 |
| B7 | `depends` 是 **AND 语义** ⇒ 饰品「二选一」走 `recommends` + 运行时守卫 `verifyAccessoryProviderOrThrow()` | ② | 已落地 |
| B8 | 通用标签**自建 `c:`**（对齐 NeoForge 定义） | ② | 已落地 |
| B9 | 配方「指定药水」走自建 `astral_dice:nbt_shaped` 序列化器 | ② | 已落地 |
| B10 | Loom 重映射剥掉 `fabric.mod.json` 的 `jars` ⇒ `tools/loom_embedded_jars.py` + `ft_env.ps1 --install-embedded` | ② | 已落地 |
| B11 | 保留的 **1.20.1 差异**：星币锤配方 / 复仇之戟行为 / `potionTag` 与 `NbtShapedRecipe`（**不得**按 1.21.1 覆盖） | ① | 已落地（用户裁决） |
| B12 | 联动裁剪：`irons_spellbooks` ❌ / `enigmaticlegacy` ❌ / `bountiful` ❌ 无 data ⇒ 全部移除；`patchouli` / `waystones` / `trinkets` / `accessories` / Modern UI ✅ 保留 | ③ | 大部分已落地；**Modern UI 待适配** |
| B13 | 常驻时长归一 / 射程系统 / 隐匿效果 / 效果牌目标口径 / 立牌受击族 / 输入收口 / 动作栏收口 | —（跨线同源） | **已落地**（追平批 1~8） |

---

## 4. 各平台独有待解决问题清单

### 4.1 `forge-1.20.1`（P0）

| # | 问题 | 状态 | 判据 / 落点 |
|---|---|---|---|
| P0-F-1 | **KI-9：R1/R2「两处 1.20.1 功能缺失」** —— 原文未展开，需按 R 编号重新取证 | **未决** | `KNOWN-ISSUES.md` §4 KI-9；`docs/project-scan-report.md` §八 |
| P0-F-2 | **KI-2：×1.4 与七咒 `dice_curse_ratio` 交叉 ⇒ 1.20.1 双重放大** | 未决 | `KNOWN-ISSUES.md` KI-2 |
| P0-F-3 | 神秘遗物联动的 **1.20.1 注册名核对**（七咒之戒 `enigmaticlegacy:cursed_ring` / 启示之证 / 倒转之启） | 待游戏内确认 | `docs/compat-1.20.1-forge.md` §6 核对清单 |
| P0-F-4 | 七咒倍率捕获管线依赖神秘遗物在 `LivingHurtEvent` 内应用第一诅咒倍率；若其在其它管线生效 ⇒ 仅**倍率捕获退化** | 已知限制 | `docs/compat-1.20.1-forge.md` §6 末条 |
| P0-F-5 | **dev / prod 产物混用风险**（写错方向 ⇒ `NoSuchFieldError`：dev 报 `f_256929_`、prod 报 `MOB_EFFECT`） | 机制已防护 | F2 |
| P0-F-6 | Carpet 只能经 `modImplementation` 提供（生产 SRG jar + SRG refmap）；手工放 `run/mods` 会**静默失效** | 已固化 | `forge-1.20.1/build.gradle` |
| P0-F-7 | `docs/compat-1.20.1-forge.md` §9「风险与后续验证（用户手动）」5 项（Curios 槽位 / 骰战结算 / 附件同步 / 卡牌栏菜单 / 神秘遗物核对） | 待实机 | 同文件 §9 |

### 4.2 `neoforge-26.1.2`（P1）

| # | 问题 | 状态 | 判据 / 落点 |
|---|---|---|---|
| P1-N-1 | **库侧缺口：`starengine_lib` 的 26.1.2 子项目无平台侧经济存储实现** ⇒ `StarEngineEconomy.isAvailable()` 恒 `false` ⇒ `/starcoin` 不注册、拾取星币不吸收、**余额条永远显示 0**（钱包**只有 UI、没有可用余额**） | **未决（库侧）** | `docs/compat-26.1.2-neoforge.md` §8 首条 |
| P1-N-2 | 1.21.1 的 **4088 行完整回归探针未迁移**（现仅最小 7 条子命令 + 1 冒烟条目） | 未决 | `docs/compat-26.1.2-neoforge.md` §8 第 1 条 |
| P1-N-3 | `opprobe` 的 `level` 读数退化为 `-1`（读数残缺，不影响闸门） | 未决（低危） | §8 第 2 条 |
| P1-N-4 | 手册 / 立牌技能行为 / GUI 屏幕**未做视觉-行为取证** | 未做 | §8 第 3 条 |
| P1-N-5 | **二重验证第二轮（修改后回归）未跑** | 未做 | §8 第 5 条 |
| P1-N-6 | **KI-D1：26.1.2 + 光影（Iris）⇒ `Missing sampler Sampler1` 崩溃**（**dev-only 断言**、与本模组无关；规避 = `-Dneoforge.disableGlValidation=true`，**代价**是会静音自家渲染管线校验） | 已确认 + 已规避 | `KNOWN-ISSUES.md` KI-D1 |
| P1-N-7 | `ItemTags.SPEARS` 等 26.1.2 独有常量**不得回移** 1.21.1 / 1.20.1 | 纪律 | N10 |
| P1-N-8 | `docs/` 为**本机目录**（`.gitignore`）⇒ 平台差异文档不随 git 提交，需连同主线 `docs/` 一起备份；**可版本化载体在 `porting/`** | 已知 | 本文件 |

### 4.3 `fabric-1.20.1`（P1）

> ✅ **2026-10-04：P1-B-1 ~ P1-B-5 全部闭环**（详细证据见 `porting/fabric-1.20.1/FABRIC-DIFFS.md` §4）。
| # | 问题 | 状态 | 判据 / 落点 |
|---|---|---|---|
| P1-B-1 | **Modern UI 提示框兼容未做** —— fabric 端确有 Modern UI；需先下载 **fabric 版** Modern UI jar 取证其描边 API（类名/字段可能与 Forge 分支不同），再照两线 `ModernUITooltipCompat` 结构实现 + `modernui_tooltip_frame_compat` 开关 | ✅ **已完成**（2026-10-04）：Fabric 版 jar 取证 → `ModernUITooltipCompat` + mixin 接线 + 配置开关 | `porting/fabric-1.20.1/FABRIC-DIFFS.md` §4 |
| P1-B-2 | **近战黑名单缺「非武器工具」四项** —— fabric `DiceCombatEvents#isMeleeWeaponAttack` **无** `Items.{SHEARS,FISHING_ROD,FLINT_AND_STEEL,BRUSH}` 排除（实测 grep 命中 0；另三线各 1 处） | ✅ **已完成**（2026-10-04）：四项排除已补，与 forge 同函数逐行一致 | 同左 |
| P1-B-3 | **fabric 无测试探针**（`/astralprobe` 系列）⇒ 本线改动无法实机验证 | ✅ **已完成**（2026-10-04）：新增 `/astralcatchup` 探针 + `FAB-CATCHUP-PARITY` 用例（零前置、无人值守） | `porting/fabric-1.20.1/CATCHUP-PROGRESS-2026-10-04.md` §9 |
| P1-B-4 | **KI-E3：三个守门脚本不含 fabric**（`audit_mixin_injection` / `check_lang_sync` / `audit_actionbar`）⇒ 该线三面对应面**无自动守门**，须人工核验 | ✅ **已闭环**（2026-10-04，KI-E3 结案）：三守门 `LINES` 均含 fabric，实测 0 违规 | `KNOWN-ISSUES.md` KI-E3 |
| P1-B-5 | **KI-F24②：`DiceCombatEvents#onLivingDamagePre` 结构分叉** —— fabric 把 `directEntity instanceof Player` 闸门提到计时器逻辑**之前** ⇒ 白泽赐福 / 降神计时器在 fabric 只认**近战**，三线按「任意攻击」启表 | ✅ **已对齐 P0**（2026-10-04）：计时器块前移 + 改用 `source.getEntity()`，与 forge 逐行一致 | `KNOWN-ISSUES.md` KI-F24 |
| P1-B-6 | KI-F24① `concealment` 整条特性缺失 | **已闭环**（追平批 5） | 同左（本表标注闭环） |
| P1-B-7 | 四线共有待裁决：**KI-F8**（`hanna_sign.3` / `sherry_sign.3` 档位词与代码稀有度不符）、**KI-G2**（医疗箱筹码重登 / 切维度可反复利用） | 待裁决 | `KNOWN-ISSUES.md` |
| P1-B-8 | **KI-F4：重写构建脚本漏搬 `sourceSets.srcDir('src/generated/resources')`** ⇒ 生成资源不进产物（编译期 + 启动期均无感）⇒ 改构建脚本后**必须开包核对资源条目数** | 缺陷模式已固化 | `KNOWN-ISSUES.md` KI-F4 |
| P1-B-9 | 跨加载器**存档不互通**（附件/Capability；Trinkets/Accessories ↔ Curios） | 平台差异，**不修补** | `KNOWN-ISSUES.md` KI-F2 |
| P1-B-10 | `verify_fabric_assets.py` 白名单现为空（8 项闭环）；新增白名单项必须写「为何不能修 + 需谁裁决」 | 纪律 | 同左 |

---

## 5. 移植作业的固定收尾动作（四线通用）

1. **构建**：目标线 `build` SUCCESSFUL（⚠️ 构建日志须用 **Python `subprocess` 直捕 stderr 再 `decode`**，别走管道；本机 PowerShell 工具回显失效 ⇒ 输出 `*> 文件` 落盘再读）。
2. **产物三件套**：jar 时间戳 + **开包 `javap` 核符号** + 资源条目核对（fabric 尤须）。
3. **守门**：`scripts/verify/verify_*.ps1` + `scripts/audit/tooltip_color_audit.ps1` + `tools/{check_lang_sync,check_mod_sources,audit_actionbar,verify_party_api,verify_firearm_detection,audit_mixin_injection,audit_patchouli_keys,verify_fabric_assets,verify_effect_icons}` —— ⚠️ **守门 0 ≠ 全绿**（散三处）。⚠️ `audit_mixin_injection` / `check_lang_sync` / `audit_actionbar` **自 2026-10-04 起已含 `fabric-1.20.1`**（此前不含，见 P1-B-4 ⇒ 已闭环）。
4. **一致性检查**：抹平平台样板后逐行对比本线 ↔ `neoforge-1.21.1` 的**同一段实现**。
5. **二次验证**：非平凡改动 / 发布动作 / 批量或跨线改动 ⇒ **必派独立子代理对账**（`AGENTS.md`「二次验证规范」）。
6. **文档**：`CHANGELOG{,_ZH}.md`（1.21.1 与 20.1 同批；26.1.2/fabric 按其节奏）+ 本表「待解决问题」状态更新 + `KNOWN-ISSUES.md`（未修项禁删、已修项移 §1.1 索引）。
7. **提交**：代理**自动本地提交**；⚠️ **默认不 push**（push 顺序 = 先库后消费方）。

---

## 6. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-04 | 建档：按用户裁决「重新划分优先级等级」建立跨版本差异总表（四线功能差异 + 各平台待解决问题），配套 `AGENTS.md`「版本优先级与跨线移植纪律」与 `porting/fabric-1.20.1/FABRIC-DIFFS.md`。 |
| 2026-10-04 | **fabric P1-B-1~B-5 全部闭环**（Modern UI 兼容 / 近战黑名单 / `onLivingDamagePre` 对齐 / 三守门纳入 fabric / 追平探针）。§4.3 状态列已更新。 |
