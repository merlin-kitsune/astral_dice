# 星币钱包（Star Coin Wallet）实现规格

> 依据：用户 2026-09-21 指令（逐条对应见 §1）+ `docs/features/star-coin-wallet-research.md`（Magic Coins / SG-Economy 机制调研）。
> 版权：**只做功能层模仿**，自有类名/结构/命名，不复制不改编对方任何代码（SirGrantd License 硬约束）。
> 范围：**1.21.1 + 1.20.1 双版本**；`neoforge-26.1.2` 冷冻不动（库的 26.1.2 模块只做「不破坏编译」）。

## 1. 需求 → 实现映射

| # | 用户要求 | 实现落点 |
|---|---|---|
| 1 | 物品栏左上角的钱包 GUI 按钮，用我们自己的「星币钱包」图标，坐标/尺寸与 Magic Coin 一致 | 客户端事件把按钮注入**原版物品栏界面**；基座坐标 `(2, 生存 -24 / 创造 -74)`、尺寸 **18×20**（= Magic Coins 的 COLLECT 按钮）；图标 = `images/星币钱包.png`（32×32，复制进两版 `textures/gui/`）|
| 2 | 面额机制：1 星币 = 1，1 星币袋 = 9 | 余额以**星币为单位**存 `long`；物品面额 `star_coin = 1`、`star_coin_bag = 9`（常量集中在 `StarCoinCurrency`）|
| 3 | 点钱包按钮 → 物品栏+手中所有星币/袋进钱包 | 服务端动作：扫 `inventory.items`（36 格）+ **副手**，累加价值 → `deposit` → 移除对应物品 |
| 4 | 星币与玩家数据绑定、不受死亡掉落影响 | 余额存**玩家持久化数据**（非物品），`PlayerEvent.Clone` 显式复制 ⇒ 死亡不丢 |
| 5 | 钩子：玩家获得/拾取星币时默认直接入钱包 | ① 中央发币漏斗（见 §4）② 拾取事件（`ItemEntityPickupEvent.Pre` / `EntityItemPickupEvent`）|
| 6 | 物品栏加「兑换星币 / 星币袋」按钮，与 Magic Coin 实现方式一致，区分生存/创造坐标 | 两个按钮，注入同一界面；基座坐标沿用 Magic Coin 的面额按钮位（生存 `x=77`、创造 `x=127`；y 分别 `7/5`、`23/21`），尺寸 **13×13** |
| 7 | 经商立牌联动，兑换的星币直接入钱包 | **无需专门改**：`ParunanSignItem` 走 `ResourceConversion.starlightToStarCoins` → 该漏斗已接钩子 ⇒ 自动入钱包 |
| 8 | 星币锤等应消耗**物品栏**星币/袋，不是钱包 | 保持现状（`StarCoinHammerChipItem` 全程只读 `player.getInventory()`）；本功能**不**改它的任何判定 |
| 9 | 常量 + 5 个配置项 | 见 §3 |
| 10 | 未提及的功能不实现，预留星币掉落 | 不实现：余额显示 HUD、`/starcoin pay`、战利品注入、钱包物品、跨币种 | 
| 11 | 必要类下沉 starengine_lib + 自研 API 供 FTB 等联动 | 库新增 `economy/` 包：`StarEngineEconomy`（对外 API）+ `EconomyStorage`（平台 seam）+ `StarCoinCommand`；平**不注册任何注册表条目**（沿用库既有不变量：用玩家持久化 NBT）|
| 12 | `/starcoin` 调试命令，按原模组功能 | `add / set / remove / get / rank`（权限 2/2/2/0/0）。⚠️ **不实现 `pay`**（原模组的 `pay` 需要一个 `isActivePayCommand` 配置项，而你的配置清单里没有；且它属玩家功能而非调试）|

## 2. 架构（两仓分工，对齐 SG-Economy / Magic-Coins 的切法）

```
starengine_lib（底层：账本 + API + 命令）          astral_dice（表现层：物品语义 + GUI + 钩子 + 配置）
  common/economy/
    StarEngineEconomy   ← 对外 API（余额读写/存取/转账/离线读）
    EconomyStorage      ← 平台 seam（读/写/离线读 + 名字）
    StarCoinCommand     ← /starcoin 构建器（vanilla Brigadier，平台无关）
  neoforge-1.21.1/…/economy/NeoForgeEconomyStorage   ← 持久化 NBT + Clone 复制 + 命令注册
  forge-1.20.1/…/economy/ForgeEconomyStorage         ← 同上（Forge 事件）
                                                   item/StarCoinCurrency  ← 面额常量 + 物品栏计数/存/取
                                                   client/gui/StarCoinWalletButtons ← 3 个按钮
                                                   network/*Payload     ← C2S 点击意图
                                                   event/StarCoinPickupHandler ← 拾取钩子
                                                   config/ModCommonConfig ← 5 个配置项 + CONFIG_VERSION 4
```

- **库不引入注册表条目**（沿用库 `StarEngineLib` 的既有不变量），因此余额存**玩家持久化数据**：
  - NeoForge：`player.getPersistentData()` 下 `starengine_lib.wallet`（`{balance:long, name:string}`）
  - Forge：同一形状（`getPersistentData()` 在 1.20.1 同样可用）
  - 死亡：`PlayerEvent.Clone` 显式复制（默认不复制 ⇒ 显式写才不丢）
- **不做客户端同步**：本批需求不含余额显示 ⇒ 无需 S2C（客户端只发点击意图）。未来加 HUD 时再补。
- 平台差异（必须登记）：事件类名与注册事件（见上）；其余语义逐字一致。

## 3. 常量与配置项（需求 9）

| 名称 | 类型 | 默认 | 位置 |
|---|---|---|---|
| `STAR_COIN_VALUE` | 常量 `long` = **1** | — | `StarCoinCurrency` |
| `STAR_COIN_BAG_VALUE` | 常量 `long` = **9** | — | `StarCoinCurrency` |
| `WALLET_BUTTON_*` / `CONVERT_BUTTON_*` 基座坐标与尺寸 | 常量 | 见 §1 | `StarCoinWalletButtons` |
| `enable_star_coin_wallet` | 配置 bool | **true** | `ModCommonConfig`（两版）|
| `deposit_star_coin_on_obtain` | 配置 bool | **true**；**仅当钱包启用时生效** | 同上 |
| `star_coin_wallet_offset_x/y` | 配置 int | 0 / 0 | 同上 |
| `star_coin_convert_offset_x/y` | 配置 int | 0 / 0 | 同上 |
| `star_coin_bag_convert_offset_x/y` | 配置 int | 0 / 0 | 同上 |

- `CONFIG_VERSION` **3 → 4**（配置项变更 ⇒ 旧配置自动备份）。
- 配置只留在消费方（库不读配置、不持 schema，沿用库既定约定）⇒ **不动** 库的 `GameplayConfigValues`。

## 4. 钩子（需求 5 / 7 / 8）

- **中央漏斗**（已有）：`resource/ResourceConversion.giveItem(player, stack)` —— 所有「给玩家星币」的路径都经它或 `ResourceConversion.starlightToStarCoins`。本功能在该处判断：若 stack 是星币/星币袋 且 `钱包启用 && 获得即入钱包` ⇒ 折算价值入钱包；否则原样进背包。
- **拾取**：世界里的星币（战利品箱、`popResource`）由拾取事件拦截 → 取消物品拾取 + 价值入钱包（关闭选项时保持原版行为）。
- 已知发放点（须逐一确认走漏斗，必要时改道）：`combat/DiceCombatEvents:420,435`（6/8 枚）、`event/AstralEventSystem:97`、`item/chip/BankCardUnlimitedChipItem:62`、`BufferShieldChipItem:46`、`PiggyBankChipItem:38`、`item/sign/ParunanSignItem:47`；世界产出 `event/LootInjectionHandler:146`、`event/NetherrackDiceHandler:59`（后两者由拾取钩子覆盖）。
- **不动**：`StarCoinHammerChipItem`（背包口径，需求 8）、`AnvilUpgradeHandler`（消耗背包星币）。

## 5. 待裁决的两处口径（已按最贴近「与 magic coin 一致」的字面实现，若不符请指出）

1. **「兑换」按钮的语义**：Magic Coins 的面额按钮是「**左键=从钱包取出 1 枚 / Shift+左键=取尽可能多（≤64 且受背包空间限制） / 右键=存入 1 枚 / Shift+右键=存入尽可能多**」，其 `Convert*` 是另一组独立按钮。
   本规格按「**星币兑换按钮 = 星币的取/存**、**星币袋兑换按钮 = 星币袋的取/存**」实现（= 原模组面额按钮口径，也解释了需求 8 里「锤子花背包星币」的钱从哪来）。
   ⇒ 若你想要的其实是「**背包内 星币 ↔ 星币袋 互换**」（原模组 `Convert*` 口径），改动仅在按钮→动作映射一处。
2. **`/starcoin pay`**：按 §1#12 的推理**不实现**；要的话我补（并需新增一个开关配置项）。

## 6. 分阶段落地（每阶段结束必须编译通过）

| 阶段 | 内容 | 验收 |
|---|---|---|
| **P1** | 库 `.13 → .14`：`common/economy/*` + 两平台 storage + `/starcoin` + 三平台 CHANGELOG/版本号；`publishToMavenLocal` | 库三平台 `build` 通过；mavenLocal 出现 `.14` |
| **P2** | 消费方引脚 `.12 → .14`（两版 gradle.properties）+ 推送库 jar 到 `run/*/mods` 与整合包；配置项 + CONFIG_VERSION 4 + `StarCoinCurrency` + 漏斗钩子 + 拾取钩子 | 双版本 `build` 通过；开 jar 核对库坐标 |
| **P3** | 客户端 3 按钮 + 图标资源 + 偏移配置读取 + C2S 包 + 服务端动作（存全部 / 取存 1 与全部） | 双版本 `build` 通过 |
| **P4** | 文档收尾：两版 CHANGELOG（内容与平衡性调整）、`AGENTS.md` 钱包小节、库 CHANGELOG 中英同步 | 守门脚本（lang 同步 / 配方 / 池）全绿 |

## 7. 验证计划（编译后交给用户测试）

- 双版本 `build` + jar 时间戳 + **开包核对**（库坐标版本、新类、图标资源、配置项）。
- 静态检查：`ChipCommon`/`verify_*` 守门不回归（本功能不动配方与池）。
- **留给你实机测试**：按钮位置（生存/创造）、存入全部、取/存 1 与全部、拾取入钱包、死亡不掉、经商立牌兑换入钱包、星币锤仍花背包星币。

> **贴图来源变更（2026-09-21，玩家提供原画）**：界面 7 张贴图（`textures/gui/star_coin_*`）改由**玩家提供**
> —— 余额条改**金色描边**、钱包按钮改成金币叠图（`images/星币钱包.png` 此前已换成 64×64）；
> 换版后两版（`neoforge-1.21.1` / `forge-1.20.1`）必须**逐字节同步**（md5 核对 7 张）；26.1.2 无钱包资产、不涉及。
