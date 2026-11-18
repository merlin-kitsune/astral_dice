# C 组审计：经济 / 世界 / 数据 / 命令 / 配置 / 网络 / compat

**结论：本组共 27 项功能面 —— 已覆盖 12 项、部分覆盖 3 项、缺失 8 项、明确不 API 化 4 项。**
缺失的 8 项里，商店操作 1 项按既定裁决（删商店意图、远程商店后续单独设计）冻结，实际建议补 7 项。最痛的两条：**星币这张"钱"没有任何门面出口**——`economy/StarCoinCurrency` 的识别 / 面额 / 入袋全在内部包，外部件想发星币只能硬编码 `astral_dice:star_coin` 或 import `economy.*`；**配置零读取面**——钱包开关、自动入袋、枪械伤害、动作条时长改了，addon 无从得知，而 `ModCommonConfig` 在各加载器线源码根，common 侧根本引用不到。
反过来，**数据面（战利品 / 铁砧 / 商店）本来就是 datapack 驱动、全命名空间扫描，addon 自带 JSON 即可，不需要任何 API**——这是本组最健康的一块，缺的只是一份 schema 文档。

路径缩写：`neo/` = `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/`；`common/` = `common/src/main/java/com/merlinkitsune/astral_dice/`；`api/` = `common/src/main/java/com/merlinkitsune/astral_dice/api/`。门面行号均指 `api/AstralDiceApi.java`（625 行）。

| 功能面 | 证据（文件:行） | 现状（内部可达性） | 门面覆盖 | 建议 API 签名 | 优先级 |
|---|---|---|---|---|---|
| 钱包读写 / 转账 | `api/AstralDiceApi.java:187-245`（`resolveWallet`:247） | 内部 `StarEngineEconomy` / `WalletRegistry`，已被门面完全包住 | ✅ 覆盖 | 无需新增 | P2 |
| 星币物品识别与面额 | `neo/economy/StarCoinCurrency.java:38,40,48,56,64,92` | 全部 `public static`，但在 `economy.*`（内部包）；门面 wallet 系列只收 `long` 金额，**不认物品** | ❌ 缺 | `public static boolean isStarCoin(ItemStack stack)`；`public static long starCoinValue(ItemStack stack)`（星币 1 / 星币袋 9 / 其它 0）；`public static long countCarriedStarCoins(Player player)`（36 格 + 副手） | P0 |
| 背包星币存入钱包（自动入袋） | `neo/economy/StarCoinCurrency.java:115,148,174,200` | `depositAllFromInventory` 先入账后清物；`tryAbsorbIntoWallet` 是发币漏斗唯一入口，均为内部 | ❌ 缺 | `public static long depositCarriedStarCoins(Player player)`（返回实际入账额，0 = 无星币或账本不可用） | P1 |
| 面向玩家的发物品漏斗 | `api/AstralDiceApi.java:387-389`；`neo/target/PlayerLikeTargets.java:165-170`；`neo/resource/ResourceConversion.java:58-65` | `giveItemTo` 对真玩家**恒返回 false**（只服务类玩家 handler），且不经过 `ResourceConversion.giveItem` ⇒ addon 自己 `inventory.add` 星币时**不触发自动入袋**，与模组内所有发币路径口径不一致 | ⚠️ 部分 | `public static boolean giveItemToPlayer(Player player, ItemStack stack)`（内部固定走 `ResourceConversion.giveItem`：入袋 → 物品栏 → 掉落） | P1 |
| 钱包 / 自动入袋开关 | `neo/config/ModCommonConfig.java:94,95`；`neo/economy/StarCoinCurrency.java:71,80` | 读 COMMON 配置；`walletAvailable`:187 只查钱包能否解析，**不反映开关** | ❌ 缺 | `public static boolean walletFeatureEnabled()`；`public static boolean autoDepositOnObtain()` | P1 |
| 其它配置读取 | `neo/config/ModCommonConfig.java:36,37,45,56,72,73-75,85,93` | 全为 `ModConfigSpec` 值；`snapshot()`:187 返回内部 `GameplayConfigValues` | ❌ 缺 | `public static boolean firearmDamageAllowed()`；`public static int actionBarDurationTicks()`；`public static int actionBarFadeTicks()` | P2 |
| 商店询价 / 购买 / 候选池 / 折扣 / 开店 | `neo/shop/ChipShopService.java:55,65`；`ChipShopOpener.java:41,61`；`CardShopOpener.java:25`；`ChipShopMenu.java:356,438`；`ChipShopCatalog.java:376` | public static 内部可达，但状态在 `ChipShopState`（CODEC 落存档）、流程绑菜单与 12 条 C2S payload | ❌ 缺（冻结） | 现在不提；`openCardShop` 已由 D 组提出，落地时合并为单一入口 | P2 |
| 商店数据包（shop.json） | `neo/data/ShopDataReloadListener.java:22-27,30-65` | `SimpleJsonResourceReloadListener` 扫 `data/<ns>/astral_dice/shop/*.json`，多文件合并、同 id 先到先得 | ✅ 覆盖 | 不改代码，只补 schema 文档 | P2 |
| 类玩家"能用商店"能力位 | `neo/compat/playerlike/PlayerLikeAbilities.java:32`；`neo/target/PlayerLikeTargets.java:78-82,160`；`api/playerlike/PlayerLikeCapability.java:4-10` | 内部有 `Capability.SHOP`，门面枚举只有 SIGNS / CHIPS / WALLET ⇒ `supportsPlayerLikeCapability` 到不了 SHOP | ⚠️ 部分 | `PlayerLikeCapability` 增 `SHOP`，`:363-369` 分派到 `Capability.SHOP`（与商店重构同批） | P2 |
| 商店 / 活动抽屉 / 铁砧方块引用 | `neo/block/ModBlocks.java:16,25,66,73` | 注册对象：`BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("astral_dice","card_shop"))` 即可拿到，**无需 import** | ✅ 覆盖 | 无需新增（registry id 即公共契约） | P2 |
| 活动抽屉价格 / 铁砧升星费用 | `neo/block/EventDrawBlock.java:58`；`common/data/AnvilUpgradeData.java:136,162,172`；`neo/compat/jei/AstralAnvilJeiCategory.java:173-181` | 价格与费用表在 `common/data`（public）但不在 `api/`；Jade / JEI 各展示一份 | ⚠️ 部分 | `public static int eventDrawerPrice()`；`public static int anvilStarCoinCost(int targetStarLevel)`；`public static int anvilXpCost(ItemStack dice)`；`public static int anvilMaxStar()` | P2 |
| 村民职业 / POI / 工作点 tag | `neo/world/ModVillagerProfessions.java:32,45`；`ModPoiTypes.java:35-50,72,77` | 注册对象 id 可达；job-site `TagKey` 在 `ModPoiTypes` 内部，但 datapack 可用 `#astral_dice:card_trader_job_sites` 引用 | ✅ 覆盖 | 无需新增（tag 名补文档） | P2 |
| 村民交易注入 | `neo/trade/ChipTraderTrades.java`、`CardTraderTrades.java`（订阅 `VillagerTradesEvent`） | addon 订阅同一个原版事件即可自行加交易 | ✅ 覆盖 | 无需新增 | P2 |
| 绿宝石骰交易变换 | `neo/trade/EmeraldDiceTrade.java`；`neo/mixin/trade/`（5 个） | 内部状态机，绑 `MerchantOffer` / `MerchantMenu` / `MerchantContainer` / `MerchantResultSlot` / `ServerPlayer` 五个 mixin | ➖ 不 API 化 | 无 | P2 |
| 战利品注入规则（datapack） | `neo/data/LootInjectionReloadListener.java:33,40-43,46-79`；`common/data/LootRule.java:58` | 全命名空间扫描、多文件按 `priority` 合并、规则 id 全局唯一先到先得、非法单条跳过 | ✅ 覆盖 | 不改代码；补 schema 文档（`LootRule` 类头 `:11-27` 已是完整字段口径） | P2 |
| 铁砧数值（datapack） | `neo/data/AnvilUpgradeReloadListener.java:28,32-34,46-63` | 同机制；多文件按资源 id 字典序取最后一份并记 WARN | ✅ 覆盖 | 无需新增 | P2 |
| 数据包快照运行时读取 | `common/data/AstralDiceDataStore.java:54-85`；`LootInjectionData.java:109,122` | public 但在 `common/data`（内部包）：addon 想问"我这张表会掉哪些星骰道具"必须 import | ❌ 缺 | `public static java.util.List<LootRuleView> lootRules()`；`public static java.util.Optional<LootRuleView> bestLootRule(String kind, ResourceLocation tableId)`；新增 `api/LootRuleView.java`：`record LootRuleView(String id, int priority, String targetKind, List<String> targets, String table, boolean requirePlayerKill, boolean monsterOnly)` | P2 |
| 管理命令 /astralhook、/astralparty | `neo/command/GrapplingHookCommand.java:33,45`；`AstralPartyCommand.java:135,188,347` | 带 `hasPermission` 门的运维 / 排障命令（清实体、清效果、dump、结束冷却、清传送门） | ➖ 不 API 化 | 无 | P2 |
| 自定义网络通道 | `neo/network/ModPayloads.java:22`（payload 注册 `:25-196`，28 条；`network/` 共 30 个文件，另 2 个是 `ModPayloads` / `VersionGate`） | NeoForge 1.20.5+ **每个 payload type 即独立通道**，28 条全带 `astral_dice` 命名空间，没有共享通道；addon 用自己的命名空间 + 自己的 `event.registrar(version)` 注册即可，两套版本串互不影响 | ✅ 覆盖（无需本模组 API） | 无 | P2 |
| 复用本模组展示通道 | `neo/network/ModPayloads.java:22`；`ActionBarPayload.java`、`StarCoinBalancePayload.java`、`DamageNumberPayload.java` | payload 类与发包器全在 `network.*`；addon 想用同款动作条 / 余额条只能重写一份显示 | ❌ 缺 | `public static void showActionBar(ServerPlayer player, Component text, int durationTicks)`（内部转 `ActionBarPayload`，经平台层） | P2 |
| JEI 配方类别 / 催化剂 | `neo/compat/jei/`（11 个，如 `AstralAnvilJeiPlugin.java:22` `@JeiPlugin`） | JEI 自动发现插件，addon 写自己的 `@JeiPlugin` 即可 | ✅ 覆盖 | 无需新增（`AstralAnvilJeiBridge.setRecipeOpener`:20 仅供本模组按钮回跳） | P2 |
| Jade tooltip | `neo/compat/jade/AstralDiceJadePlugin.java:34-50`；`ShopCooldownServerDataProvider.java:33`（`NBT_KEY="astral_dice:shop_cooldown"`） | Jade 插件机制；addon 写自己的 `IWailaPlugin`，冷却 NBT key 是固定字符串可直接读 | ✅ 覆盖 | 无需新增 | P2 |
| 类玩家能力与交付 | `api/AstralDiceApi.java:340-389`；`neo/compat/playerlike/PlayerLikeAbilities.java:24-60` | 门面已覆盖 `isPlayerLike` / `matchesPlayerSelection` / `supportsPlayerLikeCapability` / `giveCardTo` / `giveItemTo` | ✅ 覆盖 | 无需新增（SHOP 见「类玩家能用商店能力位」一行） | P2 |
| 音效播放 | `neo/audio/ModSounds.java:55,58,77,106-113`；`SoundPlayback.java:29-49` | `BuiltInRegistries.SOUND_EVENT.get(id)` + `Level#playSound` 即可，`SoundPlayback` 只是糖 | ✅ 覆盖 | 无需新增 | P2 |
| 自定义粒子（179 变体库） | `neo/particle/LibraryVariants.java:14,376,415`；`ModParticles.java:36,41`；`LibraryParticleOptions` | 粒子类型 id 可引用；`LibraryParticleOptions(variant,scale,rgb)` 是 record，但 variant 语义绑美术库 | ➖ 不 API 化 | 不建议包装（见下节） | P2 |
| 星光 → 星币兑换 | `neo/resource/ResourceConversion.java:16,29-48` | 内部；依赖 `item/StarLightManager` 与多处分账 | ❌ 缺 | `public static int convertStarlightToCoins(Player player, int amount)`（`amount=-1` 消耗全部；返回实得星币） | P2 |
| 加载期检查 / 伤害栈消毒 / datagen / mixin | `neo/init/ModCompatibilityCheck.java:80`；`neo/fixes/DamageStackSanitizer.java:39-135`；`neo/datagen/DataGenerators.java:17`；`neo/mixin/**`（26 个：client 8、trade 5、container 3、jade 2、sculk 2、根 5、fixes 1） | 平台修补与构建期工具，无对外面 | ➖ 不 API 化 | 无 | P2 |

## 建议实现顺序

1. **P0 · 三个方法 · 纯平台透传（半天）**：`isStarCoin` / `starCoinValue` / `countCarriedStarCoins`。实现路径固定——`AstralDicePlatform` 加 `default long starCoinValue(ItemStack){return 0L;}`，五线转发各自 `economy.StarCoinCurrency.valueOf`（`neo/economy/StarCoinCurrency.java:48`）；门面 `isStarCoin` 实现为 `starCoinValue(stack) > 0`，只留一条判据，避免两套口径漂移。**注意 default 返回 0 的静默风险**：javadoc 必须标"未接线线 = 0"，否则 addon 会把星币误判成普通物品。
2. **P1 · 两条经济写入（1 天）**：`depositCarriedStarCoins`、`giveItemToPlayer`。后者必须复用 `ResourceConversion.giveItem`（`neo/resource/ResourceConversion.java:58`），否则钱包开关一开，addon 发币与模组内发币就走成两条路径，前者不自动入袋。
3. **P1 · 配置只读（半天）**：`walletFeatureEnabled` / `autoDepositOnObtain` / `firearmDamageAllowed` / `actionBarDurationTicks` / `actionBarFadeTicks`。只做读——配置所有者是玩家和整合包，addon 写配置会打架。事件系统三项（`EVENT_APPLY_MC_TEAM` / `FB_TEAM` / `OPAC`）暂不暴露，等事件侧自身的 API 定稿再加。
4. **P2 · 两条纯 common 就能做完（1 天，不动五线）**：`anvilStarCoinCost` / `anvilXpCost` / `anvilMaxStar` / `eventDrawerPrice`，以及 `lootRules` / `bestLootRule` + `api/LootRuleView`。实现只依赖 `common/data`（`AnvilUpgradeData.java:162,172,136`、`AstralDiceDataStore.java:54-85`），没有平台层返工风险，最适合顺手做。
5. **P2 · `showActionBar`（1 天）**：给 addon 一个"用本模组样式说话"的出口。伤害数字与余额条先不做——它们各有专门 store（冻结坐标、登录重发），API 化要连带定义生命周期。
6. **商店面：等重构，一次补齐**：`openShop` / `quote` / `purchase` / `PlayerLikeCapability.SHOP` 四项同批做，并与 D 组的 `openCardShop` 合并成单一入口。现在做等于把菜单协议、存档状态形状、候选概率算法同时冻成契约。
7. **第 0 步（文档，随时可做）**：把 `data/<ns>/astral_dice/{loot,shop,anvil}/*.json` 三份 schema 写进 wiki。这是 addon 今天唯一零风险就能接入的扩展面，而 `LootRule`（`common/data/LootRule.java:11-27`）与 `AnvilUpgradeData`（`common/data/AnvilUpgradeData.java:16-26`）的类头已经把字段口径写全了，搬运即可。星光兑换（`convertStarlightToCoins`）跨组：`item/StarLightManager` 属点数 / 战斗组，等那边统一签名后再补，本组只登记需求。

## 明确不建议 API 化的项

1. **商店全套（询价 / 购买 / 候选池 / 折扣 / 开店 / `SHOP` 能力位）** —— 用户已裁决"删商店意图、远程商店后续单独设计"。现在暴露会把三样内部东西同时冻成契约：菜单协议（`ChipShopMenu.handleBuy:356` 与 12 条 C2S payload 一一对应）、状态形状（`ChipShopState` 的 CODEC 落存档，改字段就是存档兼容问题）、候选算法（`ChipShopCatalog.pickWeighted:376` 的概率常量与权重）。等远程商店设计定稿再一次性收口，成本远低于"先补再改"。
2. **`/astralhook`、`/astralparty`** —— 两者都带 `hasPermission` 门，语义是运维与排障（清实体、清效果、dump、结束冷却、清传送门），没有一个动作该由 addon 程序化触发；addon 要清场自己写，不必共享一条带权限门的命令路径。
3. **`mixin/**`（26 个）与 `fixes/`** —— 全是"补原版行为"的补丁（客户端渲染 alpha、容器搬运守卫、村民交易定制、幽匿振动、伤害栈消毒）。暴露等于把内部实现变成跨 MC 版本的兼容契约，而这些 mixin 的目标签名逐版本不同、五线差异最大，API 化后每次 MC 升级都要连带保 addon 兼容。
4. **`datagen/`、`init/ModCompatibilityCheck`、JEI / Jade 注册点** —— 前两者是构建期与加载期工具；JEI / Jade 本身就是插件发现机制（`@JeiPlugin`、`IWailaPlugin`），addon 直接写自己的插件，本模组再加一层注册器只是无收益的中间层，还会把 `AstralAnvilJeiBridge` 这种内部回跳桥暴露成契约。
5. **音效与粒子变体** —— 音效走 `BuiltInRegistries.SOUND_EVENT` + `Level#playSound` 已经完全够用；`LibraryVariants` 的 179 个 int 是**美术库内部编号**（`NAMES[]:376`、`name(int):415`），暴露成 API 就把美术编号变成向后兼容承诺，附属要粒子自己注册类型更便宜。
6. **村民交易 / 职业 / POI 与 `EmeraldDiceTrade`** —— 前三者 vanilla 的 `VillagerTradesEvent` + registry id + datapack tag 已经覆盖；`EmeraldDiceTrade` 依赖 5 个 trade mixin 的内部状态，API 化会把交易补丁变成契约（同第 3 条）。
7. **网络通道本身** —— NeoForge 每个 payload type 即一条通道，本模组 28 条全带 `astral_dice` 命名空间，addon 无法也不应复用；真正值得做的是 `showActionBar` 这种"展示服务"，不是通道。
