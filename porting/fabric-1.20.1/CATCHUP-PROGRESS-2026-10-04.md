# fabric-1.20.1 追平进度与语义差测绘（2026-10-04 第二轮）

> 承接 `CATCHUP-PLAN-2026-10-04.md`。本轮完成批 2 ~ 批 5（部分），并按用户要求
> 排除全部第三方模组联动（fabric 端不存在的模组）。
> ⚠️ **重要更正**：首轮清单（14 项）是按「方法名集合差」测绘的，**会漏掉纯数值/实现变化**
> （如 `HEALING_POINTS` 1→2、`onHurt` 增加 `DamageSource` 参数、判定条件收紧）。
> 本轮改用**语义差判据**重测，实际滞后面显著更大 —— 见第四节。

## 一、本轮已完成批次

| 批 | 内容 | 关键文件 |
|---|---|---|
| 2 | 效果牌目标口径族 | `BerserkCardItem` / `ExpressDeliveryCardItem` / `LuxuryFeastCardItem`（改 `TargetType.NON_HOSTILE` + 奢华大餐亡灵魔法伤害）、`BaseEffectCardItem#isEffectCardAction`、`LivingPageItem#isLivingPageAction` |
| 3 | 射程系统 | **新增** `target/SelectorRangeModifiers`、**新增** `effect/RinPageRangeEffect`、`ModEffects` 注册 `rin_page_range`、`TargetSelectionManager` 接入两段夹取、`SatelliteChipItem` 删击杀返牌改常驻 +50%、`RinSignItem` 主动追加状态、图标 `mob_effect/rin_page_range.png` |
| 4 | 立牌滞后族 | `BaseSignItem`（受击被动闸门 `isHostileAttack` + `onHurt(DamageSource)`）、`LuluSignItem`、`PaparaSignItem`、`ZhaoSignItem`、`TeruSignItem`、`FenSignItem#fillRecharge`、`BigBowlStewChipItem`（`HEALING_POINTS` 1→2）、`BufferShieldChipItem` |
| 5 | 隐匿效果链 | **新增** `effect/ConcealmentEffect`、`ModEffects` 注册 `concealment`、`InvestigationEventUtil`（四阶段统一 1:00 + 隐匿取代原版隐身）、`DiceCombatModifiers`（隐匿标记加伤 + 降神追击独立加伤通道）、`PlayerLifecycleHandler` 死亡清隐匿、`DiceCombatEvents` 两处接入、图标 `mob_effect/concealment.png` |

**批 1** 为上一轮完成（`normalizeLegacyInfiniteDurations`）。
**批 6 已按用户裁决收窄**：Iron 法术联动（#12）**整体剔除**（fabric 端无该模组）。

## 二、lang 同步

| 来源 | 内容 |
|---|---|
| 批 2 | 3 卡 tooltip 改「非敌对生物」措辞（3 语） |
| 批 3 | 新增 `effect.astral_dice.rin_page_range{,.description}`（3 语）+ `sign.rin_active` 补第 3 行 |
| 批 4 | 14 处手册条目 `guide.entry.*` 对齐 forge（= neo 口径，3 语） |
| 批 5 | 新增 `effect.astral_dice.concealment{,.description}`（3 语） |

**已确认不补的键**（按用户裁决）：
- `tooltip.astral_dice.card.fate_spell_mana`（Iron 联动，fabric 端无该模组）
- `astral_dice.guide.entry.integration.*`（内容即神秘遗物，整条已删）
- `bountiful.decree.astral.name`（fabric 无 bountiful data 联动）

## 三、照搬策略与安全约束（本轮固化）

fabric 与 forge **同为 MC 1.20.1** ⇒ 凡 forge 已实现的功能，直接照搬 forge 对应文件 +
固定平台替换，天然保留 1.20.1 的配方/行为差异。

**平台替换表**（forge → fabric）：
```
starenginelib.item.CuriosCompat       -> astral_dice.compat.curios.CuriosApi
top.theillusivec4.curios.api.*        -> astral_dice.compat.curios.*
net.minecraftforge.eventbus.api.*     -> astral_dice.platform.event.*
net.minecraftforge.event.entity[.x].* -> astral_dice.platform.event.entity[.x].*
net.minecraftforge.event.TickEvent    -> astral_dice.platform.event.TickEvent
net.minecraftforge.fml.common.Mod     -> 删
@Mod.EventBusSubscriber(...)          -> 删（改 LoaderBus.INSTANCE.register 显式登记）
MinecraftForge.EVENT_BUS.post(        -> LoaderBus.INSTANCE.postEvent(
```

**三条硬约束**（违反会引入回归）：
1. 🚨 **不可照搬的混合文件**（含已裁决差异或 fabric 独有内容）——
   `combat/DiceCombatEvents`（已删神秘遗物）、`item/card/FateGuidanceCardItem`、`combat/SpellDamageRegistry`（Iron 词条）、
   `event/ModTooltipHandler`（已删 4 处神秘遗物判定）、`effect/ModEffects`（本轮已加 2 个新效果）、
   `datagen/ModRecipeProvider`（`forge:bricks` 属 1.20.1 专属）、
   **`event/PlayerLifecycleHandler`（含 fabric 独有 `AP_FAB_GUIDEBOOK` 诊断代码 + `countGuideBooks`）**。
   ⇒ 这些文件必须**逐段局部补**。
2. 🚨 **删块后必跑花括号平衡自检**（上轮 `45405f0e` 曾因残留孤立 `}` 与 `@SubscribeEvent` 在 BUILD FAILED 状态下提交）。
3. ⚠️ 照搬后**必须确认事件注册覆盖**：删掉 `@Mod.EventBusSubscriber` 后，若类里有 `@SubscribeEvent`
   而 `AstralDiceMod` 未登记该 `X.class`，则事件**静默不生效**（`LoaderBus.scan` 不递归内部类）。

## 四、语义差测绘（本轮新方法，发现清单不完整）

**判据**：把 forge 与 fabric 的共有 java 逐文件读入，剔除 import / 平台标识符 / `@Mod.EventBusSubscriber` /
纯注释行后，取 **forge 独有且 fabric 不存在的「实质语句行」**。未命中行 = 候选滞后。

**结果**：66 个文件命中、合计 505 行。剔除「本来就属平台实现」的文件（`datagen/*`、入口类
`AstralDiceMod`、`component/AttachedDataKey`、`component/AstralData`、`init/MixinRuntimeGate`）与
「已裁决差异」（含 `enigmaticlegacy` / `irons_spellbooks` / `forge:bricks` 的行）后，
**仍需移植的真滞后文件约 30 个**，其中最典型的：

| 文件 | 滞后内容（示例） |
|---|---|
| `event/LootInjectionHandler` | 战利品注入细则 |
| `event/FirstLootChestHandler` | 首箱赠礼 |
| `event/HiddenCurseEnchantment` | 隐藏诅咒附魔 |
| `event/ModTooltipHandler` | tooltip 分支（需避开已删的神秘遗物块） |
| `client/TargetSelectionClient` / `client/KeyBindingSetup` | 输入收口 / 按键 |
| `client/gui/StarCoinWalletButtons` | 钱包叠加 GUI |
| `client/RarityTooltipFrame` / `client/IcebergTooltipCacheGuard` | 稀有度边框 / Iceberg 缓存 |
| `combat/PartyRelations` / `combat/PlayerHostilityTrackerEvents` / `combat/EliteTargets` | 队友判定 / 敌对追踪 / 精英 |
| `combat/DiceCombatEvents` | 近战黑名单补入非武器工具（`BRUSH`/`FLINT_AND_STEEL`）等 |
| `item/sign/{Nardis,Sherry,Mamushi,Hanna,Komachi,Parunan}SignItem` | 若干细节 |
| `item/chip/{CurrentCore,Airbag,CursedSword,FanBig,FanSmall}ChipItem` | 若干细节 |
| `effect/ModEnchantments` / `damage/RailgunBolts` / `economy/StarCoinWalletActions` / `config/ModCommonConfig` | 若干细节 |
| `item/ModItems` / `init/ModCreativeTabs` / `screen/*` / `network/VersionGate` / `mixin/*` | 若干细节 |

**复算方式**：见 `tools/` 下的测绘脚本（本目录 `gap-report/`），或按上文判据重跑。

## 五、剩余待办

1. 第四节表中的**真滞后文件**（约 30 个）—— 建议按「事件/战利品」→「客户端」→「余项」分 3 批推进。
2. **Modern UI 提示框兼容**（`ModernUITooltipCompat`，fabric 端有 Modern UI，需照抄两线实现并适配）。
3. **实机验证**：`/astralprobe` 需先补 fabric 端探针（当前 fabric 无对应命令）。
4. 每批收尾：`:fabric-1.20.1:build` + 开包核验 + `check_lang_sync` / `tooltip_color_audit` / `audit_patchouli_keys`。


## 六、第二批（批 7~8，提交 fd3461a8）

| 批 | 内容 | 关键点 |
|---|---|---|
| 7 | **输入收口** | `KeyBindingSetup`：J 键会话期一律「收起」（撤销手持类豁免）、H 键对「手持即选择」放行；`TargetSelectionClient`：右键（allowSelf=false）改**收起**、中键/侧键**放行** |
| 8 | **动作栏通道与染色收口** | 9 处立牌提示改 `sendSignActionBarColored(..., RED, ...)`；`CurrentCoreChipItem` 私有方法加色参；`AirbagChipItem`/`ModMenuTypes`/`StarCoinWalletActions` 由**裸 `displayClientMessage`** 改为 `ModNetwork.ActionBarMessage`（黄/红） |

## 七、⚠️ 一次失败尝试的教训（批 6 已回滚）

对「语义差测绘」出的 35 个文件**整文件照搬 forge + 平台替换表** ⇒ 构建 **61 错 / 26 文件失败**，已全量 `git checkout` 回滚。根因：
1. 替换规则 `net.minecraftforge.fml.common.Mod;` **漏写 `import ` 前缀** ⇒ 吃掉换行后与下一行 import 合并成 `import import ...`；
2. **fabric 的 `platform.*` 与 forge 包路径并非一一对应**：`platform.event.BuildCreativeModeTabContentsEvent` 不存在、fabric 侧 `ICuriosItemHandler` 拼作 `ICursiosItemHandler`、`Compat` 私有方法签名不同。

**结论（已写入 MEMORY）**：**平台敏感的类必须逐段局部改，不可整文件照搬**；「照搬」只适用于平台中性的文件。

## 八、⚠️ 重要判定修正：forge 独有行中很大比例是「有意的平台适配」

逐文件核对后确认以下**不是滞后、不应移植**：

| 文件 | 为何不是滞后 |
|---|---|
| `combat/EliteTargets` | 读 `Entity#getPersistentData()`（**Forge 补丁**，原版无）+ Apotheosis（**无 1.20.1 Fabric 版**）⇒ fabric 已按既定口径裁剪该 Forge-only 联动 |
| `event/FirstLootChestHandler` | 同因；fabric 已改用 `ModAttachments.FIRST_LOOT_CHEST_GIVEN`（与另 107 键同一持久化通道） |
| `component/AttachedDataKey`、`ModAttachments`、`ModCapabilities`、`AstralData` | fabric attachments 平台实现（FAPI 附件 vs Forge Capability） |
| `datagen/*`、`AstralDiceMod` | 平台 datagen / 入口（Forge `DeferredRegister` ↔ fabric `platform.registry`） |
| `ModItems`、`ModEnchantments`、`EffectTimerGuard`、`ModEffectEvents`、`RandomCardHandler` | 注册 API 与 `ForgeRegistries` ↔ `BuiltInRegistries` 映射 |
| `network/VersionGate`、`event/WaystoneWarpCompat` | `fml.ModList` / 反射探测 Forge Waystones（fabric 版实现不同） |
| `client/*`（`GlowingDustParticle`/`RenShieldRenderer`/`IcebergTooltipCacheGuard`/`SpellDamageContext`/`DiceCurioItem`/`CardInventoryMenu`/`StarCoinWalletButtons`） | `@OnlyIn`、`RenderLevelStageEvent`、Curios `type.*` 包、GUI 坐标 API 的平台映射 |
| `init/ModCreativeTabs`、`item/card/CardItem`、`item/chip/*`（CursedSword/Railgun）、`damage/RailgunBolts`、`event/TemporaryCardEvents`、`mixin/*` | 单项平台 API（`withTabsBefore`、`getMaxStackSize`、`ItemTossEvent` 包名等） |

⇒ **判据要更新为「先判滞后 vs 平台适配，再动手」**，不能只看「对端独有行」。

## 九、剩余待办（完全追平的最后一段）

1. **Modern UI 提示框兼容**（清单 #13）—— fabric 端确有 Modern UI，但需**从 fabric 版 Modern UI jar 取证**其描边 API（fabric 分支类名/字段可能不同于 Forge 分支），再照两线 `ModernUITooltipCompat` 结构实现；配套 `ModCommonConfig` 的 `modernui_tooltip_frame_compat` 开关。
2. `combat/DiceCombatEvents` 余下的「近战黑名单补入非武器工具」（`BRUSH`/`FLINT_AND_STEEL` 等）—— 该文件含已裁决差异，需逐段局部改。
3. `combat/PartyRelations` 的 `AP_PARTY` 机器行（测试探针用，fabric 无探针，可选）。
4. fabric 端**测试探针**（`/astralprobe` 系列）补齐后，实机验证本批全部改动。
5. `audit_actionbar.py` **只覆盖三条线**（不含 fabric）—— 建议扩到四线。
