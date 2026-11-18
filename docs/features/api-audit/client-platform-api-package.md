# D 组审计：客户端 / 平台 / 扩展点 / api 包

**结论：本组共 20 项功能面 —— 已覆盖 5 项、部分覆盖 4 项、缺失 9 项、明确不 API 化 2 项。**
最痛的是三条：第三方骰子拿不到筹码格（门面返回内部类型 `item.dice.DiceTier`）、骰子原生 tooltip 硬编码 13 个 `ModItems`（注册进来的骰子吃不到）、门面零客户端/服务端约束（`common` 全包 0 处 `isClientSide` 判断，`refreshDiceChipSlots` 这类服务端方法在客户端调也不报错）。

| 功能面 | 证据（文件:行） | 现状（内部可达性） | 门面覆盖 | 建议 API 签名 | 优先级 |
|---|---|---|---|---|---|
| 平台身份查询 | `common/src/main/java/com/merlinkitsune/astral_dice/platform/AstralDicePlatforms.java:37,42`；`api/AstralDiceApi.java:622` | `AstralDicePlatforms.get()/id()` 公开，未安装返回 `null` / `"uninstalled"` | ✅ 覆盖 | 无需新增 | P2 |
| 平台抽象能力位 | `platform/AstralDicePlatform.java:32-59` | 4 个 default 方法全是**静默 no-op**（`activateSign`→false、`hasDiceBlessing`→false），实现方漏写不报错 | ⚠️ 部分 | `public static boolean platformSupports(String feature)`（由 `AstralDicePlatform` 新增 `default boolean supports(String){return false;}` 驱动） | P1 |
| 类玩家注册 | `api/AstralDiceApi.java:322,332`；`platform/AstralDicePlatform.java:42` | 内部 `target/PlayerLikeTargets.register` 可达；26.1.2 线签名差异已由平台层吸收 | ✅ 覆盖 | 补 `public static boolean unregisterPlayerLike(String id)` | P2 |
| 类玩家能力判定 | `api/AstralDiceApi.java:340,345,356` | `isPlayerLike` / `matchesPlayerSelection` / `supportsPlayerLikeCapability` 齐全 | ✅ 覆盖 | 无需新增 | P2 |
| 类玩家钱包 | `api/AstralDiceApi.java:476-526`；`api/playerlike/PlayerLikeWallet.java` | 钱包注册/余额/存取全在门面；玩家恒走 StarEngine 账户（`PlayerLikeWallet.java` 类头已写明） | ✅ 覆盖 | 无需新增 | P2 |
| 立牌激活（非玩家宿主） | `api/AstralDiceApi.java:426`；`platform/AstralDicePlatform.java:52` | 内部 `item/sign/BaseSignItem#activateFor` 可达；返回只有 `boolean`，玩家宿主恒 `false`，无冷却查询 | ⚠️ 部分 | `public static int signCooldownRemainingTicks(LivingEntity holder)` + `public static int signCooldownTotalTicks(ItemStack sign)` | P1 |
| 骰子祝福 | `api/AstralDiceApi.java:437`；`platform/AstralDicePlatform.java:57` | 只有布尔；时长/等级/施加全在内部 `effect.*`，addon 只能读 `true/false` | ⚠️ 部分 | `public static int diceBlessingRemainingTicks(LivingEntity entity)`；`public static boolean applyDiceBlessing(LivingEntity entity, int durationTicks)`（平台层补同签名 default） | P1 |
| 骰子阶层 → 物品栈解析 | `api/AstralDiceApi.java:85,104,109`；`item/dice/DiceTier.java:26-38` | 5 参 `registerDiceTier` 的**返回类型是内部包 `com.merlinkitsune.astral_dice.item.dice.DiceTier`**（全限定写死，:85-86）；`diceTier(id)/diceTiers()` 返回的是库侧同名 `starengine...DiceTier` —— 两个 `DiceTier` 同名不同类，addon 必须 import 内部包才能拿 `targetChipSlots` | ❌ 缺 | `public static String diceTierId(ItemStack stack)`（无阶层返回 `null`）；`public static boolean hasDiceTier(ItemStack stack)`；新增 `api/DiceTierView.java`：`record DiceTierView(String id, int cardSlots, int chipSlotsAtStar, Supplier<Item> item)` + `public static DiceTierView diceTierView(ItemStack stack)`；旧 5 参方法返回类型收窄为 `DiceTierView` 并保留 `@Deprecated` 重载 | P0 |
| 骰子筹码格数 | `api/AstralDiceApi.java:404`（只有 `diceCardSlots`）；`item/dice/DiceCurioItem.java:223-227` | `targetChipSlots(ItemStack)` 是**私有静态**，addon 无法按物品栈读筹码格 | ❌ 缺 | `public static int diceChipSlots(ItemStack stack)`（非骰子返回 0） | P0 |
| 骰子原生 tooltip | `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java:348-353` | 判据是 13 个 `ModItems.*_DICE` 的**硬编码 `stack.is(...)` 串联**；登记进 `DiceTierRegistry` 的第三方骰子**一行原生提示都没有**（星级/费用/石子/说明全丢） | ❌ 缺 | 判据换成 `AstralDiceApi.hasDiceTier(stack)`；并补 `public static List<Component> diceTooltipLines(ItemStack stack, Player viewer)` 供 addon 复用原生行 | P0 |
| 立牌 / 筹码 tooltip 扩展点 | `neoforge-1.21.1/.../event/ModTooltipHandler.java:333` | 只有一个总 `ItemTooltipEvent` 处理器，没有注册器；addon 想插行只能自己订阅平台事件（能做事，但拿不到 astral 的解析结果） | ❌ 缺 | `public static void registerItemTooltipContributor(Predicate<ItemStack> applies, TooltipContribution contribution)`；新增 `api/TooltipContribution.java`：`@FunctionalInterface void append(ItemStack stack, List<Component> lines)` | P2 |
| 客户端 HUD 层注册 | `neoforge-1.21.1/.../client/ModClientEvents.java:87-106` | 6 个 `registerAbove(...)` 全部写死（damage_number / target_select / action_bar / 攻防条 / 事件卡）；`LayeredDraw.Layer` 没有任何对外注册口 | ❌ 缺 | 新增 `api/client/AstralHudLayer.java`：`@FunctionalInterface void render(GuiGraphics g, DeltaTracker d)`；`public static void registerHudLayer(ResourceLocation id, AstralHudLayer layer)`（类放 `api/client/`，禁止从双端类引用） | P1 |
| 客户端按键注册 | `neoforge-1.21.1/.../client/KeyBindingSetup.java:17,24`；`ModClientEvents.java:122` | 只有 2 个自有 `KeyMapping`（J / H），分类写死 `key.categories.astral_dice`；addon 自注册需要自己造分类，无法共享 astral 分类 | ❌ 缺 | `public static KeyMapping registerKeyMapping(String id, int defaultKeyCode, String category)`（`api/client/`） | P2 |
| 键位显示名（给 tooltip 用） | `neoforge-1.21.1/.../client/ClientKeyNames.java:35,44` | 在**加载器线的 client 包**里，`common` 无此概念；fabric 线 addon 拿不到 | ❌ 缺 | `public static String keyDisplayName(String keyId)`（未绑定时返回 `null`，客户端专用） | P2 |
| 打开卡牌栏 / 商店 | `neoforge-1.21.1/.../network/OpenCardInventoryPayload.java`（`network/` 共 30 个 payload，全在该线） | 打开界面要走自有 payload + 服务端 `openMenu`；addon 想“从自己的按钮打开 astral 卡牌栏”只能复制这套协议 | ❌ 缺 | `public static void openCardInventory(ServerPlayer player)`；`public static boolean openCardShop(ServerPlayer player)`（纯服务端，不引入客户端类型） | P1 |
| 自定义界面 / 菜单注册 | `neoforge-1.21.1/.../screen/ModMenuTypes.java:18-38`；`ModClientEvents.java:41-47` | 5 个 `MenuType` 私有注册表；addon 其实用平台自己的 `MenuScreens`/`MenuType` 就能注册**自己的**界面 | ❌ 缺（且不需要） | 不建议包一层；见「不建议 API 化」 | P2 |
| 战斗属性钩子 | `api/combat/CombatStatHooks.java:44-63`；`CombatStat.java`、`CombatStatStage.java`、`CombatStatContext.java:14`、`CombatStatHandle.java:16,21` | 8 个类型全部 `public`；`apply(...)` 也 public，addon 可算 HUD 数值；`CombatStat` 是**枚举**，只能 ATTACK/DEFENSE，加不了新属性 | ✅ 覆盖 | 若要扩属性：`CombatStat` 改 `final class + public static CombatStat register(String id)`（破坏性，建议 P2 再做） | P2 |
| 伤害分类解析 | `api/combat/DamageCategoryResolver.java:11`；`api/AstralDiceApi.java:254,259,264` | `installDamageCategoryResolver` 是**单槽覆盖**（`private static volatile DamageCategoryResolver`），两个 addon 同时装 = 后装者静默赢 | ⚠️ 部分 | `public static void registerDamageCategoryResolver(int priority, DamageCategoryResolver resolver)`；`public static boolean unregisterDamageCategoryResolver(DamageCategoryResolver resolver)` | P1 |
| 客户端 / 服务端调用约束 | `common/src/main/java/com/merlinkitsune/astral_dice/` 全包 `isClientSide|Dist.CLIENT|assertServer` **0 命中**；`api/AstralDiceApi.java:409` | `refreshDiceChipSlots` / `giveCardTo` / 钱包四个方法都是纯服务端语义，但**没有任何 javadoc 标注、没有运行时校验**；客户端误调会静默改本地副本→回滚或幽灵槽位 | ❌ 缺 | 门面内私有 `private static void requireServer(String operation, Entity holder)`（`holder.level().isClientSide()` 时抛 `IllegalStateException`）；每个方法 javadoc 首行加 `[服务端]` / `[双端]` / `[仅客户端]` 标记 | P0 |
| mixin / fixes | `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/mixin/`（26 个：client 8、container 3、trade 5、jade 2、sculk 2、fixes 1、其余 5） | 原版行为修补，全部内部 | ➖ 不 API 化 | 无 | P2 |
| 平台安装入口 | `platform/AstralDicePlatforms.java:28-34` | `install` 允许重复安装、后装覆盖先装；addon 若调用会**顶掉主模组实现** | ➖ 不 API 化（建议加保护） | 安装时若已有实现且 `id()` 不同则 `LOGGER.warn` + 拒绝（不改签名） | P2 |

## api/ 包公开类型清单

**`api/AstralDiceApi.java`** —— `public final class`，构造私有，全部 `public static`（约 40 个）。按段：骰面阶层 :85/:99/:104/:109；立牌 :116/:121；筹码 :128/:134；钱包 :141；伤害钩子 :148/:279；注册表视图 :155-180；钱包读写 :187-229；伤害分类 :254-264；战斗属性 :299-309；类玩家 :322-356；发牌 :381/:387；骰子/立牌查询 :394-416；祝福 :437；骰战参与者 :448-458；类玩家钱包 :476-526；敌对探针 :548-562；收口 :614-624。**唯二返回内部类型的地方**：`registerDiceTier(...)` → `item.dice.DiceTier`（:85），其余均返回库侧 `api.*` 类型。

**`api/combat/`（8 类型，全部 public，无包私有）**
- `DamageCategory`（enum，6 值，顺序 `SKILL→GUN→MAGIC→RANGED→DICE_MELEE→OTHER`）：`String id()`、`static DamageCategory byId(String)`
- `DamageCategoryResolver`（`@FunctionalInterface`）：`DamageCategory resolve(DamageSource)`；javadoc 要求纯函数、服务端任意线程可调
- `CombatStat`（enum，ATTACK / DEFENSE）：`id()`、`byId(String)`
- `CombatStatStage`（enum，BASE / ADDITIVE / MULTIPLICATIVE / FINAL）：`id()`、`byId(String)`
- `CombatStatContext`（public record，4 组件 `attacker/target/source/display`，紧凑构造器做 null 校验；`display=true` 时公共注册表**不执行**钩子）
- `CombatStatHook`（`@FunctionalInterface`）：`double apply(CombatStatContext, double value)`；异常被注册表吞并记日志
- `CombatStatHandle`（final class，构造包私有）：`unregister()`、`isActive()`
- `CombatStatHooks`（final class，全静态）：`install()`、`isInstalled()`、`register(CombatStat, CombatStatStage, int, CombatStatHook) → CombatStatHandle`、`apply(CombatStatContext, CombatStat, CombatStatStage, double) → double`、`clear()`；未接线时 `register` 抛 `IllegalStateException`

**`api/playerlike/`（4 类型，全部 public）**
- `PlayerLikeCapability`（enum，3 个能力位：立牌 / 筹码 / 钱包）
- `PlayerLikeHandler`（interface）：`matches(LivingEntity)`、`giveCard(ServerPlayer, LivingEntity, ItemStack)`、`default giveItem(...)`、`default supports(PlayerLikeCapability)`
- `PlayerLikeWallet`（interface）：`isAvailable()`、`default isAvailable(LivingEntity)`、`balance()`、`withdraw(int)`、`default deposit(LivingEntity,int)`
- `PlayerLikeTargetsAdapter`（final class，public 构造器）—— **public 但类头明写「仅供各线平台实现调用，附属模组不要直接使用」**：这是唯一“该公开却不能给 addon 用”的类型。它必须跨源码根可见（平台实现要用），建议改为 `@Internal` 风格注解或搬进 `platform/` 包，而不是留在 `api/` 里误导 addon。

**可见性判断结论**：没有“该公开却还是 private / 包私有”的类型——`DamageHook` / `DamagePhase` / `HookHandle` 都在 StarEngine 自己的 `com.merlinkitsune.starengine.api.combat.damage` 包里，本来就是公开依赖，astral 只是透传；`HookHandle` 的使用约束（注销、幂等）写在 StarEngine 侧，astral 没再加限制。真正的问题是**反向**的一处：`registerDiceTier` 把**内部包类型**当返回值暴露了。

## 客户端-服务端一致性

- **纯服务端**（必须带 `requireServer` 或 javadoc `[服务端]`）：`giveCardTo`、`giveItemTo`、`refreshDiceChipSlots`、`openCard*`（新增）、`walletAvailable/Balance/HasBalance/Deposit/Withdraw/Transfer`、`registerPlayerLikeWallet` 及四个 `playerLikeWallet*` 读写、`activateSign`（玩家流程在服务端 `performSkill`）、`freezeContent`。
- **双端安全**：`isDiceItem`、`diceStarLevel`、`diceCardSlots`、`isSignItem`、`diceTier*`、`hasDiceBlessing`（客户端也有 `MobEffectInstance`）、`damageCategory`、`isHostileTarget`、`platform()/platformId`。
- **只在客户端有意义**：HUD 层、按键、键位显示名——这三类必须放 `api/client/`，且门面里的注册方法要从 `client/` 包调用；`ModTooltipHandler`（双端加载）不得引用，参照 `ClientKeyNames.java:9-24` 已经总结过的“运行期 dist 判断挡不住符号解析”这条纪律。
- **已知副作用**：`refreshDiceChipSlots` 会改 Curios 槽位（`DiceCurioItem.java:132-147`），客户端调用只改本地视图；`activateSign` 在客户端调用走平台实现，四条线是实例方法，会执行到 `performSkill` 的服务端前置检查后静默返回 `false`，调用方无从区分“玩家宿主”和“失败”。建议返回值拆成三态枚举而不是 `boolean`。

## 建议实现顺序

1. **P0-a 只读补齐**（1 个 PR，不动平台层）：`diceChipSlots(ItemStack)`、`hasDiceTier(ItemStack)`、`diceTierId(ItemStack)`；同时把 `registerDiceTier` 的返回类型从内部 `item.dice.DiceTier` 收窄到 `api/DiceTierView`（旧签名 `@Deprecated` 保留一轮）。
2. **P0-b tooltip 去硬编码**：`ModTooltipHandler.java:348-353` 换成 `AstralDiceApi.hasDiceTier(stack)`，第三方骰子立刻吃到星级/费用/石子/说明四段原生提示；这一步不改任何签名，风险最低、收益最大。
3. **P0-c 侧别标注与校验**：给门面 40 个方法逐个补 `[服务端]/[双端]/[仅客户端]` 首行 javadoc，并对 6 个服务端写方法加私有 `requireServer`。`refreshDiceChipSlots` 是 addon 最容易误用的一个（它长得像“刷新一下”，实际改槽位）。
4. **P1-a 平台能力面扩张**：`AstralDicePlatform` 加 `diceBlessingRemainingTicks` / `signCooldownRemainingTicks` 两个 default（返回 `-1` 表示“本线未实现”，不要让 0 和 -1 混用），五线各写一遍；门面据此提供 P1 方法。
5. **P1-b 多播化伤害分类**：单槽 `installDamageCategoryResolver` 改优先级链（内部用 `CopyOnWriteArrayList`，与 `PLAYER_LIKE_WALLETS` :465 同款写法），避免两个 addon 互相踩。
6. **P1-c 客户端扩展面**：`api/client/AstralHudLayer` + `registerHudLayer`、`registerKeyMapping`，落地在 `ModClientEvents.registerGuiLayers/registerKeyMappings` 里追加，五线各一次。
7. **P2**：tooltip contributor、`keyDisplayName`、`openCardInventory`、`platformSupports`、`unregisterPlayerLike`。

## 明确不建议 API 化的项

- **`mixin/` 全部 26 个**：它们修的是原版行为（`LivingEntityDamageContainerMixin`、`MerchantOfferMixin`、`SculkShriekerVibrationUserMixin`、`EffectRenderingInventoryScreenMixin` 等）。API 化等于把“我们认定原版哪里有 bug”变成兼容承诺，第三方版本一动就得跟着改；addon 要改行为，订阅平台事件或自己写 mixin。
- **`screen/` 的 5 个 `MenuType` 与对应 `Screen`（`ModMenuTypes.java:18-38`）**：这是内部 UI 契约（槽位布局、打开参数 `token + slotCount + type.ordinal()`，见 :31-38 注释）。addon 用平台自己的 `MenuType` + `MenuScreens` 注册自己的界面更稳，包一层只会把 astral 的界面重构变成破坏性变更。
- **`client/CardShopClientState`、`ChipShopClientState`、`DamageNumberStore`、`TargetSelectionClient`、`EventCardOverlay`、`TargetOutlineCapture`**：都是内部状态机/视觉缓存，暴露等于把渲染实现细节写成 ABI。
- **`client/ModernUITooltipCompat`**：靠反射读 Modern UI 的 `sStrokeColor` / `sAdaptiveColors` 静态槽（`ModernUITooltipCompat.java:42-48` 注释），第三方 jar 一升级就碎；不 API 化，也不该有 addon 依赖它。
- **`client/RarityTooltipFrame` 的档位取色规则**：属于美学裁决（史诗显式写色、稀有暂不写），开放改写会互相抢 `RenderTooltipEvent.Color`。
- **`AstralDicePlatforms.install` / `AstralDicePlatform.install()`**：只能由主模组调用（`AstralDicePlatforms.java:28-34` 允许后装覆盖先装），addon 调用会顶掉主实现且无回退。建议只加防覆盖告警，不开放。
- **`EnderDieTotemAnimator`、`BeamFxRenderer`、`GlowingDustParticle`、`LibraryParticle`**：自有特效渲染管线，addon 要画东西自己注册粒子/渲染器即可，不需要 astral 中转。
