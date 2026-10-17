# 商店方块 Jade 冷却倒计时（2.0.0-SNAPSHOT.128）

> 需求（2026-10-17 用户原文）：
> 卡牌商店显示 `补货倒计时 0:00`；筹码商店和抽奖商店显示 `可进入倒计时 0:00`。
> 即玩家用 Jade 看向对应方块时，悬浮框追加一行倒计时（`M:SS`），**仅在冷却中（剩余 > 0）显示**；
> 冷却结束不显示该行。

## 1. 涉及方块与冷却数据源

三个方块都**没有 BlockEntity**，冷却全部是**玩家级**状态（按世界 `gameTime` 计时）：

| 方块 | 类 | 数据源 | 时长 |
|---|---|---|---|
| 卡牌商店 | `block/CardShopBlock` | `ModAttachments#getShopTransferCooldownEnd(Player)`（二级转账冷却） | 60 秒 = 1200 ticks |
| 筹码商店 | `block/ChipShopBlock` | `ChipShopState#get(Player)#cooldownUntil()`（购买冷却） | 6000 ticks = 5:00 |
| 抽奖盒 | `block/EventDrawBlock` | `ModAttachments#getEventDrawerCooldownUntil(Player)`（购买冷却） | 6000 ticks = 5:00 |

剩余 tick 一律 = `截止时刻 − level.getGameTime()`；`≤ 0` 视为无冷却。

## 2. 键名 / 格式 / 染色

| 项 | 取值 |
|---|---|
| Jade 数据包键（仅 NeoForge） | `astral_dice:shop_cooldown`（NBT long，带命名空间隔离） |
| 插件 UID（Jade 配置项 id） | `astral_dice:shop_cooldown` |
| 卡牌商店文案键 | `jade.astral_dice.restock_countdown` = `补货倒计时 §9%s§7` |
| 筹码商店 / 抽奖盒文案键 | `jade.astral_dice.enter_countdown` = `可进入倒计时 §9%s§7` |

- **格式**：`M:SS`（分钟 + 两位秒），且**秒向上取整**：`seconds = (ticks + 19) / 20`。
  与 `CardShopMenu#computeTransferCooldownSeconds` 的 `(remain + 19) / 20` 同口径
  （剩余 1 tick 也显示 `0:01`，不会提前显示 `0:00`）。
- **染色**：时间值按全仓 tooltip 口径用 `§9`（蓝），行尾 `§7` 复位；染色码写在 lang 值里
  （与本仓既有 tooltip 文案一致，不走 `withStyle`）。
- **只在冷却中显示**：剩余 ≤ 0 时**不写数据键 / 不追加行**，冷却结束后悬浮框不会残留 `0:00`。

## 3. 插件与两线实现差异（以 jar 内实际 API 为准）

两线各一个 `compat/jade/` 包，类名一致：

- `AstralDiceJadePlugin`（`@WailaPlugin("astral_dice")` + `IWailaPlugin`）
- `ShopCooldownClientProvider`（`IBlockComponentProvider`，三块共用）
- `ShopCooldownServerDataProvider`（仅 NeoForge，`IServerDataProvider<BlockAccessor>`）

**发现方式**：Jade 加载完成时扫描**全部模组 jar** 的 `@WailaPlugin("<modid>")` 注解
（`ModFileScanData`，见 Jade 15.10.6 / 11.13.3 的 `snownee.jade.util.CommonProxy`），
注解值只用来确认对应 modid 已加载，然后反射构造插件类并回调
`register(IWailaCommonRegistration)` / `registerClient(IWailaClientRegistration)`。
本插件不写 `mods.toml` / `neoforge.mods.toml` 依赖。

### neoforge-1.21.1（Jade 15.10.6）

- `IWailaCommonRegistration#registerBlockDataProvider` 接受 `Class<?>`（**方块类本身**）；
- 服务端按 `getBlockNBTProviders(Block, BlockEntity)` 查找，**BlockEntity 为 null 也照常调用**
  （`BlockAccessorImpl#handleRequest` 字节码：直接以 `getBlock()` + `getBlockEntity()` 查表）；
- ⇒ 服务端把**当前看向该方块的玩家**的剩余 tick 写进 Jade 数据包，客户端只渲染。数据包由 Jade
  按 `ObjectDataCenter.rateLimiter` 周期重取 ⇒ 悬浮框里的倒计时会**逐秒递减**。

### forge-1.20.1（Jade 11.13.3）

- `IWailaCommonRegistration#registerBlockDataProvider` 只接受 `Class<? extends BlockEntity>`；
- 服务端 `BlockAccessorImpl#handleRequest` 在 `getBlockEntity() == null` 时**直接 return，不发数据包**
  （`BlockAccessorClientHandler#shouldRequestData` 同样直接返回 false）；
- 本模组三个方块都没有 BlockEntity ⇒ **这一线无法用 IServerDataProvider**；
- ⇒ 改为客户端直接读**已同步到客户端**的冷却值，用户可见行为与 1.21.1 完全一致：
  - `ModAttachments.SHOP_TRANSFER_COOLDOWN_END` 补 `.sync()` + 进 `SYNCED_KEYS`；
  - `ModAttachments.CHIP_SHOP_STATE` 补 `.sync()` + 进 `SYNCED_KEYS`（客户端只读 `cooldownUntil()`）；
  - `EVENT_DRAWER_COOLDOWN_UNTIL` 原本已 `.sync()`，本批补进 `SYNCED_KEYS`（重登后仍能显示）。
- 上述同步只影响 1.20.1 线的客户端缓存，不改任何服务端判定。

## 4. 缺席降级

- **未安装 Jade**：插件类没有任何引用方（Jade 不扫描就不会加载它），不注册任何东西，
  不产生额外前置要求，也不影响启动。
- **Jade 已安装但客户端未连服务器 / 服务端未装本模组**：NeoForge 侧没有数据键 ⇒ 不加行；
  Forge 侧客户端同步缓存为默认值 0 ⇒ 不加行。
- **冷却结束 / 非目标方块 / 玩家为 null**：一律不加行（不写数据/不追加）。

## 5. 实机验证建议（需用户另行下达）

Jade 悬浮框是客户端视觉，静态编译与守门脚本无法确认实际渲染，建议用例：

1. 触发卡牌商店二级转账冷却后看向卡牌商店：显示「补货倒计时 M:SS」，且逐秒递减，到 0 后该行消失；
2. 筹码商店购买后看向筹码商店：显示「可进入倒计时 M:SS」；空白筹码投入后冷却清零 ⇒ 该行立即消失；
3. 抽奖盒购买后看向抽奖盒：显示「可进入倒计时 M:SS」；
4. 无冷却时看向三个方块：悬浮框**没有**倒计时行；
5. 未安装 Jade 启动：客户端正常进入、无崩溃、无缺失类报错（缺席降级）。

## 6. 已知边界

- 时间为**服务端 `gameTime`** 口径；1.20.1 客户端用本地 `ClientLevel#getGameTime` 与同步下来的
  截止时刻做差，误差受原版时间同步（约 1 秒）影响，显示上与 1.21.1 一致。
- 卡牌商店只展示**二级转账冷却**（用户指定的「补货」）；货架沙漏（`SHOP_SHELF`）不参与本行。
