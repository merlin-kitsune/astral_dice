# fabric-1.20.1 全量追平作业清单（2026-10-04 测绘）

> 基准（功能面）：`neoforge-1.21.1`；MC 版本参照：`forge-1.20.1`（同为 1.20.1）；
> 目标：`fabric-1.20.1`。
> 用户口径：**参考 1.21.1 neoforge 执行移植**，但**必须保留 1.20.1 的配方差异**（如星币锤）
> 与**行为差异**（如复仇之戟不含 1.21.1 新增效果）。

## 一、测绘方法（可复算）

| 判据 | 结论 |
|---|---|
| 文件集差（相对包路径） | fabric 有 367 java（含 `platform/` 抽象层），比 neo 多；**缺 22 个文件**（多为 `network/*`、`fixes/*` 等平台特有） |
| 注册面（`ModItems` / `ModDataComponents` 字段名集） | fabric **138 / 11**，与 forge **完全一致** ⇒ 物品与组件注册面**无缺** |
| lang 键集差 | fabric 仅缺 **7 个键**（见下） |
| 方法名集合差（共有 244 文件） | 38 个文件 / 58 个方法名，剔除平台假阳性后剩 **14 项真缺失** |
| 逐提交核对（自 `c059ead8` 引入起，涉及两线的 72 个提交） | **30 个提交改了 neo 线但从未同步 fabric** |

## 二、确认缺失清单（14 项）

| # | 功能 | 特征标识符 | 来源提交 |
|---|---|---|---|
| 1 | 常驻效果时长归一 | `normalizeLegacyInfiniteDurations` / `isModEffect` | `e417d371` (10-04) |
| 2 | 选择器射程（两段夹取） | `SelectorRangeModifiers` / `MAX_ENHANCED_RADIUS` | `749457fc` (10-03) |
| 3 | 书页射程 | `RinPageRangeEffect` / `rin_page_range`（含 lang） | `749457fc` + `0f92eeec` |
| 4 | 隐匿效果 | `ConcealmentEffect` / `concealment`（含 lang） | `37a964e4` → `5b7ab20b` |
| 5 | 奢华大餐对亡灵造成伤害 | `LuxuryFeastCardItem.isUndead` / `applyFeast` | `d3a1cce9` |
| 6 | 效果牌动作判定 | `isEffectCardAction` | `d3a1cce9` / `fb853425` |
| 7 | 活体书页动作判定 | `isLivingPageAction` | `d3a1cce9` / `fb853425` |
| 8 | 蛟龙卡牌转换 | `convertBiteToRoar` / `convertEquipped` / `convertInventory` / `hasAnyCard` / `toRoarStack` | 蛟龙批 |
| 9 | 降神追击伤害 | `descendChaseDamage` | 教主批 |
| 10 | 养精蓄锐补满 | `fillRecharge` | 风水师批 |
| 11 | 咬击加成激活 | `isMamushiBiteBonusActive` | 蛟龙批 |
| 12 | Iron 法术联动 | `IronSpellbooksCompat` / `card.fate_spell_mana` | 命运批 |
| 13 | Modern UI 提示框兼容 | `ModernUITooltipCompat` | `fb73c2b3` (10-04) |
| 14 | 骰战近战黑名单补入非武器工具 | `Items.BRUSH` / `FLINT_AND_STEEL` | `4c50095b` (10-03) |

**待核（暂不列缺失）**：`ActionBarPayload` —— fabric 用 `platform/network/{SimpleChannel,PacketDistributor,…}`，
类名不同属平台差异；需另行确认其是否满足「唯一通道」口径。

**已确认无需处理**（方法名差中的平台假阳性）：`ActionBarPayload`、`RenShieldStatePayload`、
`WeaponEnhancement`、`ItemCost`、`codec`/`streamCodec`、`canFitInsideContainerItems`/`getMaxStackSize`、
`registerGuiLayers`/`registerScreens`、`basicItem`/`registerModels`、`addVertex/setColor/setUv*`、
`onLootTableLoad`/`onLivingDamagePre`/`onRenderTooltipPre`/`onBuildCreativeTab`（事件名差异）。

**已确认 fabric 已有**：蛟龙觉醒累加（`addAwakening`）、容器体积判定、效果描述通道、输入收口、队友判定库。

## 三、lang 缺失键（7 个，四线三语同批）

```
astral_dice.guide.entry.integration.3     神秘遗物联动条目
bountiful.decree.astral.name              赏金板条目名
effect.astral_dice.concealment            隐匿
effect.astral_dice.concealment.description
effect.astral_dice.rin_page_range         书页射程
effect.astral_dice.rin_page_range.description
tooltip.astral_dice.card.fate_spell_mana  Iron 法术联动
```

## 四、必须保留的 1.20.1 差异（用户裁决）

| 项 | 说明 |
|---|---|
| **配方差异** | 星币锤等 1.20.1 专属配方 —— **不得**按 1.21.1 覆盖 |
| **行为差异** | 复仇之戟等 —— 1.20.1 **不含** 1.21.1 新增效果 |
| 通用标签命名空间 | fabric 用**自建 `c:`**（NeoForge 自带 `c:`、Forge 用 `forge:`） |
| 饰品框架 | fabric 用 `trinkets` / `accessories`（不是 Curios） |

## 五、平台适配要点（写代码时必须遵守）

1. 事件订阅：**禁 `@Mod.EventBusSubscriber`**（Fabric 无效）⇒ 必须在入口类 `LoaderBus.INSTANCE.register(X.class)`；
   ⚠️ `LoaderBus.scan` **不递归内部类**，内部类处理器要单独登记。
2. import 一律走 `com.merlinkitsune.astral_dice.platform.*`，**禁** `net.minecraftforge.*` / `net.neoforged.*`。
3. 网络包走 `platform/network/SimpleChannel` + `PacketDistributor`。
4. 附件走 Fabric API（`ModAttachments` 已适配，禁止整份覆盖）。
5. 写完后自查：`grep -rn "net\.minecraftforge\|net\.neoforge" <改动文件>` 必须 0 命中。

## 六、建议批次（按依赖与影响面）

| 批 | 内容 | 说明 |
|---|---|---|
| 1 | #1 常驻时长归一 | 独立、最小、与三线同源 |
| 2 | #5 #6 #7（效果牌目标口径族） | 同一提交族，含奢华大餐亡灵 |
| 3 | #2 #3（射程系统） | 选择器射程 + 书页射程 + 图标 |
| 4 | #8 #9 #10 #11（立牌滞后族） | 蛟龙 / 教主 / 风水师 |
| 5 | #4 #13（客户端） | 隐匿效果 + Modern UI 兼容 |
| 6 | #12 #14 + lang 7 键 + 收尾 | Iron 联动 + 近战黑名单 + 键补齐 |
