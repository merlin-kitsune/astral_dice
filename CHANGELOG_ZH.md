# 中文更新日志

> 本文件仅收录中文更新日志；英文版见 [`CHANGELOG.md`](CHANGELOG.md)。
> 两个文件按版本号一一对应：同一版本号在两边各出现一次，每次改动必须同时更新中英两份，禁止只改一侧。
> 约定：对当前版本已记录条目的后续改动，直接合并进原条目，仅保留改动后的最终版本，不追加“再次修改”条目。

## 26.4-SNAPSHOT.97

### 本线

- P2 归属批 C（用户 2026-11-19 裁决「constants拆分，下一批做」）。25 个星骰内容常量与 `cardCostForStar(int)` 从库 `com.merlinkitsune.starengine.component.GameplayConstants` 搬到 common 新类 `com.merlinkitsune.astral_dice.component.AstralGameplayConstants`，全部 `public static final`。库只保留通用 seam（actionbar、队伍开关、目标半径、枪械伤害、首次加入规则书）；`GameplayConfigValues` 仍为 7 字段。
- 179 个 Java 文件改写（143 个纯内容 + 36 个混用）：纯内容文件改 import 到新类；混用文件保留库 import，仅内容符号改走新类。615 insertions / 579 deletions。两个派生 tick 字段（`SIGN_ACTIVE_COOLDOWN_TICKS`、`DICE_BLESSING_DURATION_TICKS`）在新类里是常量表达式，五处 `applyConfig` 调用点未改。
- 依赖钉版：五线要求 StarEngine `26.4-SNAPSHOT.18`；NeoForge/Forge 区间维持 `[${starengine_version},27.0.0)`，Fabric 两线为 `>=26.4-SNAPSHOT.18 <27.0.0`。
- 验证：库 `.18` 已 clean 发布；五份库 jar 用 `javap` 确认无内容字段与 `cardCostForStar`。本仓五线 `clean assemble --offline` BUILD SUCCESSFUL（26 s，EXIT=0）；五份 `astral_dice-26.4-SNAPSHOT.97+<loader>.jar` 均含 `AstralGameplayConstants.class`（25 个 `public static final int` 字段）、未打包库侧 `GameplayConstants`，依赖区间为 `.18`。`work/verify_c.sh` 返回 0。
- 诚实边界：本批未跑实机。搬迁保值且已过编译；配置重载读的仍是未变的 7 字段 record，但实机配置推送与全部改写引用尚未在运行期复证。TLMDice 不引用这些类，未改。

## 26.4-SNAPSHOT.96

### 本线

- P2 归属批 A+B（用户 2026-11-19 裁决「先做AB」；Wallet 留库，GameplayConstants 拆分推迟）。18 个星骰 `MobEffect` 子类从 `starengine.effect` 搬入 `com.merlinkitsune.astral_dice.effect`；`Rarity` 从 `starengine.item` 搬入 common 的 `com.merlinkitsune.astral_dice.item`；五条线各自的 `AstralRarities` 平台接线路搬入同线 `com.merlinkitsune.astral_dice.item`。
- 45 个 Java 文件改到新包名，并清掉 95 条同包冗余 import（每线 18 条 effect import + `ChipReadyEffect` 1 条）。两份 NeoForge `META-INF/enumextensions.json` 各 5 条现指向 `com/merlinkitsune/astral_dice/item/AstralRarities`；若留下旧路径，`Rarity` 类加载时会启动失败。
- 依赖钉版：五线要求 StarEngine `26.4-SNAPSHOT.17`；NeoForge/Forge 区间维持 `[${starengine_version},27.0.0)`，Fabric 两线为 `">=26.4-SNAPSHOT.17 <27.0.0"`。
- 验证：库 `.17` 已 clean 发布，五份 jar 均不含被搬走的类。本仓五线 `clean assemble --offline` BUILD SUCCESSFUL（26 s，EXIT=0）；五份 `astral_dice-26.4-SNAPSHOT.96+<loader>.jar` 均用 `work/verify_ab.sh` 检查 —— 新 effect/Rarity/AstralRarities 类存在、旧 `starengine` 路径不存在，五个 jar 全部 `AB_VERIFY_OK`；六条源码根上的旧 FQN 残留 grep 为空。
- 诚实边界：本批未跑实机。`Rarity` / `AstralRarities` 除包名外行为未改；NeoForge 枚举扩展目前只在 jar/资源层取证，仍需一次真实启动做最终确认。`GameplayConstants`（C 批）与 `StarCoinWalletState` 按裁决未动。

## 26.4-SNAPSHOT.95

### 本线

- P0 归属修正：只有本模组使用的平台抽象搬回本模组。`com.merlinkitsune.starengine.api.astral.platform.AstralDicePlatform`（207 行、29 个 default）迁到 `com.merlinkitsune.astral_dice.platform.AstralDicePlatform`；库内不再有任何 `api/astral/platform` 类型，也删掉了指向它的两条未使用 import。它本来就不是门面契约：附属应继续调用 `AstralDiceApi`，不要碰平台类型。
- P1 去重：删掉三条线共 13 份同名本地副本。`fabric-1.20.1` 删 `combat/PartyRelations`、`event/EffectTimerGuard`、`combat/EliteTargets`、`target/HoldToSelect`、`target/SelfTargetable`；`fabric-1.21.1` 删后四者；`neoforge-26.1.2` 删 `combat/PartyRelations`、`combat/EliteTargets` 与两个 target 类型。所有引用改指 `com.merlinkitsune.starengine.{combat,event,target}`；共改写 134 个文件（import / FQN，以及被删类原同包文件的补 import）。
- 两条 Fabric 线在 mod 构造期注入计时表 seam：门面 install 之后立即 `EffectTimerGuard.setTimerSource(new SignTimerSourceAdapter())`，库版守卫因此读写既有的 `ModAttachments.EFFECT_TIMER_ENDS` 附件。适配器改为库版 `TimerEntry` 的直接类型拷贝，原来的双 `TimerEntry` 转换已删除。
- fabric-1.20.1 的 `PartyRelations` 改用库版，诊断前缀由 `AP_FAB_PARTY:` 变为 `AP_PARTY:`（字段仍是 `sw_* / back_* / why_*`）。同步更新实机用例 `PARTY-BACKENDS-fabric-1.20.1.json`、`FAB-PARTY-BACKENDS.json`、fabric 测试台 README 与 `docs/agent/AGENTS-full.md` 的规则说明。
- 依赖钉版：五线要求 StarEngine `26.4-SNAPSHOT.16`；NeoForge/Forge 区间仍为 `[${starengine_version},27.0.0)`，Fabric 两线为 `">=26.4-SNAPSHOT.16 <27.0.0"`。
- 验证：五线 `assemble --offline` BUILD SUCCESSFUL（37 s，EXIT=0）；逐 jar 检查，均不含 `astral_dice/combat/PartyRelations.class`、`event/EffectTimerGuard.class`、`combat/EliteTargets.class`、`target/HoldToSelect.class`、`target/SelfTargetable.class`（增量编译残留已排除）；三条改动源码根上的残留 FQN grep 为空；NeoForge jar 636 个 class 条目、Forge jar 660 个。
- 诚实边界：本批未跑实机。fabric-1.20.1 的队伍判定从本地 572 行 FTB/OPAC 反射实现换成库侧单一事实源 —— 该契约就是目标实现，但只有实机装 FTB/OPAC 才能确认后端报 `on`；计时 seam 在构造期注入，将来若某条线漏装，库侧会静默退回空守卫。

## 26.4-SNAPSHOT.94

### 本线

- API 归属翻转（用户约定 2026-11-19：main 线跟随 main 库、dev-next 线跟随 next 库，禁止跨区；API 一律放 StarEngine，主模组只做实现）。对外契约整批离开主模组 jar：36 个接口/枚举从 `com.merlinkitsune.astral_dice.api.*` 搬到 `com.merlinkitsune.starengine.api.astral.{client,combat,data,event,choice,platform,playerlike}`；`AstralDiceApi` 改为库侧门面 + 新增 `AstralDiceService` SPI（保留 100 个方法；摘掉 `platform()` 与已弃用的、返回模组内部类型的 `registerDiceTier(String,Supplier,int,IntUnaryOperator)`）；主模组提供 `apiimpl.AstralDiceServiceImpl`，五线 `AstralDiceMod` 构造器在平台 install 之后立即装入门面。主模组 jar 里不再有任何 `api/` 包。
- 拆掉两处契约里的模组耦合：`CardChoiceType.matches(ItemStack)` 改为委托新门面方法 `matchesCardChoice(type, stack)`（由主模组用 `ModItems` 实现）；`AstralEvent` 的 javadoc 不再链接主模组的 `AstralEventSystem`。
- `AstralEvent` 在四条线用 `ResourceLocation`、26.1.2 用 `Identifier`，库侧因此把它放在 `common/src/main/java-rl` 供四条线编译，26.1.2 线另用一份仅两个字段类型不同的专用副本。
- 依赖钉版：五线要求 StarEngine `26.4-SNAPSHOT.15`（带 `api/astral` 的发布）；NeoForge/Forge 区间维持 `[${starengine_version},27.0.0)`，Fabric 两线 `lib_version_range` 为 `">=26.4-SNAPSHOT.15 <27.0.0"`。
- 验证：五线 `assemble --offline` BUILD SUCCESSFUL；各 `build/libs` 产出 `astral_dice-26.4-SNAPSHOT.94+<loader>.jar`；`unzip -l` 确认无 `com/merlinkitsune/astral_dice/api/` 类（只剩 `apiimpl/`），包内 `neoforge.mods.toml` 为 `versionRange="[26.4-SNAPSHOT.15,27.0.0)"`、`fabric.mod.json` 为 `">=26.4-SNAPSHOT.15 <27.0.0"`。库侧五条线均以 `api/astral` 通过编译（26.1.2 用 Identifier 副本，其余四条线包含 `common/src/main/java-rl`）。
- 复查时顺带修掉一个真实的构建元数据缺陷：`generateBuildInfo` 没有声明对 `compileJava` 的依赖，`clean` 构建时会与 `jar` 并行、把 `classCount` 写成 0（增量构建因为读到上一轮旧 class 反而"正常"）。现已在 `neoforge-1.21.1/build.gradle` 与 `forge-1.20.1/build.gradle` 加 `dependsOn tasks.named("compileJava")`，两件 P0 产物分别报 635 / 659，与 jar 实际 class 数一致。同时把 `gradle.properties` 里的本机 JDK 路径修正到实际存在的安装（Zulu 21 的 `bin/` 已丢失）。
- 诚实边界：对附属是源码级/二进制级破坏性改动（import 变、`AstralDiceApi.platform()` 消失）；门面未安装时任何调用抛 `IllegalStateException`，安装发生在 mod 构造期，附属不得更早调用。本批未跑实机；主线（`multi-main` + StarEngine main `1.2.0-pre.1`）刻意不动，等 dev-next 实机验证后再搬。

## 26.4-SNAPSHOT.93

### 本线

- 对外 API 收口，不留别名：附属可见类型统一收到 `com.merlinkitsune.astral_dice.api.*`。五线同步搬迁 —— `event.api.*` → `api.event.*`（5 个类型）、`choice.CardChoiceType` → `api.choice.CardChoiceType`、`platform.AstralDicePlatform` → `api.platform.AstralDicePlatform`。库侧同样把 `target.TargetType` → `api.target.TargetType`、`combat.HostileTargets` → `api.combat.HostileTargets`；本体 231 个源文件改引用，库内调用点一并改完。附属与 KubeJS `Java.type` 字符串需同步改 import。
- 依赖钉版：五线要求 StarEngine `26.4-SNAPSHOT.14`（携带搬包的那次发布）。NeoForge/Forge 的 `mods.toml` 区间原是 `[\${starengine_version},2.0.0)`，下界升到 26.4 后区间已空，现改为 `[\${starengine_version},27.0.0)`；Fabric 两线继续走 `fabric.mod.json` 的 `lib_version_range`。
- 修掉一处真实不同源：`AstralDiceApi.diceTierIds()` 读库侧 `DiceTiers`，而 `diceTierViews()` 读本体 `DiceTierRegistry`，导致 id 列表可能漏掉已注册的可玩骰面、又混进仅库侧的弃用档位。现统一读 `DiceTierRegistry`，与视图同源。
- `AstralDiceApi` 类 javadoc 补上「附属须同时依赖 `astral_dice` 与 `starengine` 两个 jar」以及新包路径说明。
- 验证：五线 `assemble --offline`（JDK 21）BUILD SUCCESSFUL、无 `error:` 行；五个 `build/libs` 均产出 `astral_dice-26.4-SNAPSHOT.93+<loader>.jar`；`jar tf` 确认 `astral_dice/api/{event,choice,platform}/` 存在，且无残留 `astral_dice/event/api/`、`astral_dice/choice/CardChoiceType.class`、`astral_dice/platform/AstralDicePlatform.class`；包内 `neoforge.mods.toml` 的 starengine 依赖为 `versionRange="[26.4-SNAPSHOT.14,27.0.0)"`。
- 诚实边界：对附属是源码级/二进制级破坏性改动（类名变了，不提供过时桩）。受影响仅限被搬的类型 —— `combat.TargetCategory`、`api.playerlike.*` 与门面签名不变，只用 `AstralDiceApi` 的消费方重编即过。

## 26.4-SNAPSHOT.92

### 本线

- 修掉 B2 战斗门面的静默空转：`AstralDiceApi.registerSpellDamageModifier(...)` 转发到 `AstralDicePlatform.registerSpellDamageModifier`，而那个 default 方法是空实现，五线平台实现又都没覆写它。调用照样打 `AP_COMBAT_API: registered=spell_damage_modifier`，但 `SpellDamageRegistry.registerModifier` 根本没被调到，附属模组注册的加成被无声丢掉。五线现均补上覆写并转调 `CombatApiAdapters.registerSpellDamageModifier`，与 `registerSpellDamageMatcher` 既有形状一致。
- 由 LT-4 实机断言发现：经门面注册 +40 修饰器后 `SpellDamageRegistry.modifiers()` 仍是 10，任何法术伤害都没有加成；直接走内部注册的路径不受影响，所以 B2 批次从源码面看是正常的。
- 验证（实机，neoforge-1.21.1 开发端，超平坦存档 `testworld`，护甲 0、MAX_HEALTH 1000 的尸壳，伤害经 `/damage ... by @s` 注入）：基线 `player_attack` 10 → 990；经门面注册 +40 但没有 matcher → 仍 990；注册 `msgId == player` 的 matcher 后 → 950；换成不被匹配的 `mob_attack` → 990。`SpellDamageRegistry.modifiers()` 从 10 变成 11 —— 修前停在 10，正是这条读数暴露了缺陷。
- 真实法术对照，同一只尸壳、同一发法术（`irons_spellbooks:firebolt` 等级 1）：无修饰器 1000 → 992（8 点）；经门面注册 +40 后重新施放 1000 → 952（48 = 8 + 40）。该伤害已被内置 `fire_magic` matcher 命中，无需自定义 matcher。
- 诚实边界：`SpellDamageModifier.onHit` 无法在探针里取证 —— Rhino 的 `JavaAdapter` 不覆写接口 default 方法（直接调 `m.onHit(view, 1)` 返回 `undefined` 并走空实现）。`isActive` / `apply` 是抽象方法、覆写正常且已断言；`onHit` 需要 Java 侧实现者。

## 26.4-SNAPSHOT.91

### 本线

- 类型整理（API 覆盖计划的 B6 批，S2/S3）：新增 `api/data/DiceTierView`（id / 卡牌栏格数 / 星级→筹码格数，`chipSlotsAt(int)`），门面新增 `registerDiceTierView(...)`、`diceTierView(String)`、`diceTierViews()`。旧 `registerDiceTier`（5 参与 4 参）、`diceTier`、`diceTiers` 全部保留并标 `@Deprecated`，源码兼容，删除推迟到下个大版本。
- 缺线补齐（S3）：`item/chip/ChipEquip` 此前只有 neoforge-1.21.1 与 forge-1.20.1，本批补 fabric-1.20.1 / fabric-1.21.1 / neoforge-26.1.2（fabric 走兼容层静态 `getStacksHandler`，26.1.2 走 `getCuriosInventory → getStacksHandler`，判据同为 `chip` 槽 + `BaseChipItem`）。
- 正式声明不补线：`item/sign/SignHooks` 与 `SignHookDispatcher` 是立牌分发体系的一部分（十个嵌套接口 + 硬编码优先级），fabric 两线没有立牌子系统，单独补一套等于引入半个立牌实现；`CardUseService` 已在 B1 补满五线；`OpenCardInventoryPayload` 在 B5 已由平台 seam 取代。
- 诚实边界：`DiceTierView` 只读，不带物品栈与持久化语义，也没有改名 / 注销能力；`diceTierViews()` 只覆盖「可玩骰子」（绑定物品的那张表），不含只注册 StarEngine 描述符的条目；旧方法标 `@Deprecated` 只是编译期提示，不改变运行期行为。
- 验证：五线 `assemble --offline` BUILD SUCCESSFUL；`javap` 核对五份产物同时含新方法（`registerDiceTierView` / `diceTierView` / `diceTierViews`）与旧签名（5 参 `registerDiceTier`、`diceTier`、`diceTiers`）；`ChipEquip` 在五线均存在同名类。

## 26.4-SNAPSHOT.90

### 本线

- 客户端面门面（API 覆盖计划的 B5 批）：`AstralDiceApi.openCardInventory(ServerPlayer)` 在服务端打开卡牌界面，走与按键路径相同的「是否佩戴骰子」检查，返回是否真的打开。新增 `api/client` 包：`AstralHudLayer`（`render(Object graphics, float partialTick)`）、`AstralHudLayers`（字符串 id、拒绝重复、`pathOf`、有序快照）、`AstralKeyBindings`（id + 默认 GLFW 键码，`translationKey()`）。
- 五线客户端启动时都消费这两个注册表：HUD 循环挂在既有的 GUI 层 / overlay 注册里，附属图层统一锚在准星之上；按键循环挂在既有的按键注册里，每个请求创建一个 `KeyMapping`。
- 服务端侧别策略（P0-10）：门面类 javadoc 标注哪些调用是服务端语义；新增私有 `requireServer` 守卫，客户端实体调用 `giveCardTo`、`giveItemTo`、`refreshDiceChipSlots`、`walletDeposit`、`walletWithdraw`、`walletTransfer`、`playerLikeWalletWithdraw`、`playerLikeWalletDeposit`、`openCardInventory` 时抛 `IllegalStateException`。
- 诚实边界：HUD 渲染签名用 `Object`，因为 1.20.1/1.21.1 是 `GuiGraphics`、26.1.2 是 `GuiGraphicsExtractor`，附属必须强转本线类型；HUD 与按键注册必须发生在 mod 构造期、早于客户端 setup，晚注册只记录不显示；1.20.1 的 overlay id 只接受 path（命名空间固定为本模组），所以五线统一按 path 注册；附属按键需要自带 `key.astral_dice.<path>` 的 lang；商店按既有裁决不出面。
- 验证：五线 `assemble --offline` BUILD SUCCESSFUL；`javap` 核对门面含 `openCardInventory`，五份产物均含三个 `api/client` 类。实机 dev 客户端：佩戴骰子时 `openCardInventory` 返回 `true` 并打开 `CardInventoryMenu`，清空骰子槽后返回 `false`；HUD 与按键注册表拒绝重复与空白 id；客户端调用 `walletDeposit` / `giveItemTo` 抛服务端专用 `IllegalStateException`。

## 26.4-SNAPSHOT.89

### 本线

- 经济与配置门面（API 覆盖计划的 B4 批）：`AstralDiceApi` 新增 `isStarCoin`、`starCoinValue`（1 星币 = 1、1 星币袋 = 9，其它一律 0）、`countCarriedStarCoins`（物品栏 36 格 + 副手，按**面额**折算而不是物品件数）、`depositCarriedStarCoins`（同范围，入账并移除物品，返回实际入账额）与 `giveItemToPlayer`（本模组的中央发放漏斗：钱包与「获得即入袋」同时开启时星币直接入钱包，否则进物品栏，满则掉地）。配置只读：`walletFeatureEnabled`、`autoDepositOnObtain`、`firearmDamageAllowed`、`actionBarDurationTicks`、`actionBarFadeTicks`。
- 五线的 `StarCoinCurrency` / `ResourceConversion` 签名本就一致，所以 B4 是 common 加各线转发：`AstralDicePlatform` 新增 8 个 default（`starCoinValue`、`depositCarriedStarCoins`、`giveItemToPlayer`、三个配置读取、两个 actionbar 时长），五线各一份 `@Override` 转发。`countCarriedStarCoins` 留在 common，因为它只需要 `starCoinValue` 加原版物品栏。
- 诚实边界：平台 default 在某线未接线时静默返回 `0` / `false`，新增加载器线必须实现，否则门面会无声降级；钱包与入袋调用是服务端语义（客户端调用返回 `0` / `false`）；`starCoinValue` 只认星币与星币袋，不覆盖其它货币物品；配置读取是只读的，不能在运行期改值；同一审计条目下的铁砧费用、战利品规则与 actionbar 显示助手（P2）不在本批范围内。
- 验证：五线 `assemble --offline` BUILD SUCCESSFUL；`javap` 核对五份产物均含 10 个 B4 门面方法与 8 个平台 default。

## 26.4-SNAPSHOT.88

### 本线

- 选择器 / 选卡 / 事件 / 效果门面（API 覆盖计划的 B3 批）：`AstralDiceApi` 新增 `startTargetSelection`、`isSelectingTarget`、`targetSelectionToken`、`confirmTargetSelection`、`cancelTargetSelection`；`startCardChoice`（槽位数重载与最小槽位重载，均返回会话 token，被拒绝时返回 `-1`）；`registerAstralEvent`、`astralEvent`、`triggerAstralEvent`（带/不带星级拥有者两个重载）；`registerCombatResolutionListener`；`effectLevel`、`effectRemainingTicks`、`applyAstralEffect`、`astralEffectIds`。效果 id 解析、事件查找与触发经六个新增 `AstralDicePlatform` default 方法落到五线实现，附属不必再遍历 `ModEffects.ALL` 或直接调用 `AstralEventDispatcher`。
- 新增只读 `api/combat/CombatResolutionView`（攻击方、目标、最终伤害、落地量、已结算额外加伤、骰子物品栈、星级）与 `CombatResolutionListener`；骰战两条路径 —— 玩家攻击与生物攻击 —— 都在额外加伤结算、攻击牌耐久步之后触发它，并带重入闸门，监听器不能自我嵌套触发。
- 诚实边界：结算视图是结算后的快照，不能回写伤害；`registerCombatResolutionListener` 只在骰战触发，不覆盖任意原版伤害；选择器 / 选卡 API 需要 `ServerPlayer`，且只暴露会话 token（界面仍在客户端）；`triggerAstralEvent` 走内部派发器，冷却与资格判断仍在内部。
- 验证：五线 `assemble --offline` BUILD SUCCESSFUL；`javap` 核对五份产物均含 16 个 B3 门面方法，且五线 `DiceCombatEvents` 都有玩家 / 生物两个结算触发点。

## 26.4-SNAPSHOT.87

### 本线

- 战斗注册口门面（API 覆盖计划的 B2 批）：新增 `api/combat` 下的 `DiceBattleView` / `SpellDamageView` 只读记录，以及 `AttackPowerModifier`、`DefensePowerModifier`、`ExtraDamageModifier`、`VictimDamageModifier`、`DiceCombatFactor`、`SpellDamageMatcher`、`SpellDamageModifier` 七个接口。门面新增 `registerAttackModifier`、`registerDefenseModifier`、`registerExtraDamageModifier`、`registerVictimDamageModifier`、`registerDiceCombatFactor`、`registerSpellDamageMatcher`、`registerSpellDamageModifier` 七个注册方法，各经 `AstralDicePlatform` 新 default 转发。附属模组从此可以调骰战的攻击力 / 防御力、额外加伤、任意来源的受击倍率、最终伤害、法伤作用域与法伤加成，不必碰内部类，也不必碰这些内部类挂着的加载器事件对象。
- 审计把 B2 定成「纯 common」是错的：`DiceCombatContext`、`SpellDamageContext`、`DiceCombatModifiers`、`SpellDamageRegistry`、`DiceCombatEvents` **每个加载器线各有一份**（各自挂着 `LivingDamageEvent.Pre` / `LivingIncomingDamageEvent`），`common` 只能出门面视图。因此五线各新增一份等价的 `combat/CombatApiAdapters`（门面视图 ↔ 本线内部上下文），平台实现转发到它。注册只允许在初始化阶段完成 —— 内部注册表是普通 `ArrayList`，战斗期会被遍历。
- 诚实边界：视图是**只读**的，附属不能回写内部上下文的「结果写入区」字段；`DiceCombatFactor` 与两个法术接口的 attacker 是 `Player`，类玩家宿主不在该通道（与内部现状一致）；「让一个生物**发起**远程 / 魔法攻击」的入口仍然没有（P0-5 只解决识别与加成）。
- 验证：五线 `assemble --offline` BUILD SUCCESSFUL；`javap` 核对五份产物含 7 个门面方法、7 个平台 default、`CombatApiAdapters` 与 9 个 `api/combat` 类。

## 26.4-SNAPSHOT.86

### 本线

- 卡牌栏门面（API 覆盖计划的 B1 批）：新增只读记录 `api/data/InstalledCard`、`api/data/DiceLoadout`，以及与加载器无关的服务 `item/card/DiceCardLoadout`，逐行复刻 `CardInventoryMenu#saveToDice` / `#loadFromDice` 的私有语义（攻/防各占一半槽位、各自独立计费、上限取 `cardCostForStar(configStarLevel(dice))`、持久化的是骰子**实际**星级）。门面新增 `diceLoadout`、`canEquipCard`、`equipCard`、`unequipAllCards`。装配依旧会**销毁**卡牌物品栈（卡牌栏真值在骰子组件里）—— 门面不代扣，由调用方扣减；卸下按原归还路径重建物品栈（玩家进包 → 满包掉地；类玩家宿主走 `giveItemTo` → 掉地），非玩家宿主的**临时牌标记无法还原**（标记 API 需要 `Player`）。
- `useCardOn(LivingEntity, ItemStack)` 暴露 `CardUseService.useCardFor`：玩家走原版 `ItemStack#use` 管线；非玩家实体仍落到默认 `useCardForEntity`（返回 `false` 并打 WARN）—— 类玩家宿主还**用不了效果牌**，这是上游缺口，调用方必须处理 `false`。`CardUseService` 此前只有两条线有（neoforge-1.21.1 / forge-1.20.1），本版补齐 fabric-1.20.1、fabric-1.21.1、neoforge-26.1.2，五线内容一致。
- 新增 `diceChipSlots(ItemStack)`（背后是五线新增的公开 `DiceCurioItem#chipSlots`）暴露「由骰子推算的筹码格数」，以及 `hasDiceTier(ItemStack)` 判定「是否为骰阶注册表里的骰子」。骰子 tooltip 判据由硬编码自家 13 件骰子改为 `hasDiceTier`，附属注册的骰子从此也能拿到费用 / 星级 / 卡牌行。
- 验证：五线 `assemble --offline`；五份产物均含新门面方法与卡牌栏服务。

## 26.4-SNAPSHOT.85

### 本线

- `PlayerLikeTargets.giveItem` 的玩家分支改为真正交付（原先直接返回 `false`）：玩家走中央漏斗 `ResourceConversion.giveItem`（星币自动入钱包 → 进背包 → 就地掉地），使 玩家→玩家、玩家→女仆、女仆→玩家、女仆→女仆 四条路径共用同一交付契约。旧的提前返回使门面 `AstralDiceApi.giveItemTo(player, stack)` 对真玩家恒为 `false`，逼得 addon 自己重复写「进包 / 掉地」，且静默跳过星币入钱包。
- 新增 `AstralDiceApi.isCardItem(ItemStack)`：战斗牌（`CardItem`）与效果牌（`BaseEffectCardItem`）没有共同父类，addon 原先只能引内部物品类才能分辨「卡牌 / 普通物品」。
- 验证：五线 `assemble --offline` BUILD SUCCESSFUL；`AstralDiceApi` / `PlayerLikeTargets` 的编译产物含新成员。

## 26.4-SNAPSHOT.80

### 本线

- 修 `AstralDiceApi.giveCardTo` 的 `giver != null` 守卫：类玩家实体（女仆 / 召唤物等）本身无法充当 `ServerPlayer`，原守卫使「女仆发牌给玩家」恒返回 `false`。现在发牌者允许为 `null`，按「发牌者未知」处理 —— 卡牌照常交付，只是不结算依赖发牌者身份的被动（与 `VitaminPillChipItem.giveCard` 对 `null` 的既有约定一致）。缺陷由 TLMDice 纯门面重写的实机验收发现（女仆 → 玩家转移意图恒失败）。
- 验证：五线 `assemble --offline` exit 0；实机复验见提交说明。

## 26.4-SNAPSHOT.79

### 本线

- 补齐 `AstralDiceApi` 门面缺口（2026-11-18 裁决：本分支开始需要补齐门面）：新增类玩家注册与能力查询、骰子/立牌只读查询与立牌激活、骰子祝福判定、骰战参与者登记、类玩家钱包注册表、复合敌对探针六组公开面。附属模组此后不再需要 import 主模组内部实现类。
- 平台接缝 `AstralDicePlatform` 新增三个默认方法（`registerPlayerLike` / `activateSign` / `hasDiceBlessing`），五条加载器线各自覆写，用来吸收三处五线差异：`ModEffects.DICE_BLESSING` 的注册器类型、`BaseSignItem.activateFor` 在 forge-1.20.1 是静态方法、neoforge-26.1.2 的 `ResourceLocation` 已改名 `Identifier`。
- `DiceCombatEvents` 五线静态块改为向门面登记「试验假人也算敌对目标」探针；`PlayerLikeWallets.resolve` 在内部绑定未命中时回落到门面钱包表（fabric 两线无该文件，不接线）。
- 冻结语义不变：`freezeContent()` 仍只冻结骰阶 / 立牌 / 筹码四张表，新增注册表不受冻结。

- 验证：五线 `assemble --offline` exit 0（`BUILD SUCCESSFUL in 18s`，28 tasks：17 executed / 11 up-to-date；门面改动后先在同版本上编译通过，再统一升到 `.79` 复验）。本批未做实机检查。

## 26.4-SNAPSHOT.78

### 本线

- 把 `multi-main` 1.6.0-pre.1 并入开发线：五线前置库统一钉 `1.2.0-pre.1`（它是 `next` 上同一个提交换名后的发布裁剪，原 `26.4-SNAPSHOT.13`），并一并带入发布线的语言修正、工具门禁、AutoJS 用例与文档。版本号仍留在 `26.4-SNAPSHOT.N` 开发序列。
- 采用发布线定稿的转账面板布局：玩家名列宽 42 -> 52px（x=24..76），粉色选中箭头 66 -> 78（右缘 91，距 x=93 的滚动条滑块留 2px），取代 `26.4-SNAPSHOT.77` 构建的 50/77。
- 分支定位：自本版本起 `multi-dev-next` 从 `multi-main` 分叉，作为下一大版本的独立开发线；主线中发现的 bug 直接在本线修复，不再从主线合并。

- 验证：五线 `assemble` exit 0（带 `--offline`，`BUILD SUCCESSFUL in 11s`；28 tasks：18 executed / 5 from cache / 5 up-to-date；五枚 `.78` jar，neo `classCount` 643 / forge 668，无 `META-INF/jarjar/`）。首次在线构建在本机解析 `maven.neoforged.net` 失败，故本次验证走本地 Gradle 缓存与 `mavenLocal()`；本批未做实机检查。

## 26.4-SNAPSHOT.77

### 本线

- 卡牌商店转账面板的玩家名列宽由 42px 加到 50px（x=24..74），粉色选中箭头由 x=66 右移到 x=77。箭头右缘 x=90，与 x=93 的滚动条控件保留 3px 间隙；行悬停底色注释同步改为实际的 (2,+0)-(93,+18)。
- 验证：五线 `assemble` exit 0（`BUILD SUCCESSFUL in 28s`，28 tasks：23 executed / 5 up-to-date；五枚 `.77` jar）。本批未做实机视觉检查。

## 1.6.0-pre.1 (2026-11-16)

### 本次发布

- 1.6.0 的首个预发布：把 `26.4-SNAPSHOT.1`–`.76` 开发线并入 `multi-main`。四条发布线仍是 NeoForge 1.21.1 / Forge 1.20.1 / NeoForge 26.1.2 / Fabric 1.20.1；Fabric 1.21.1 仍是 P1 脚手架。
- StarEngine 1.2.0-pre.1 五平台：内容注册表、钱包 API、四阶段伤害钩子、伤害分类、攻击/防御属性钩子、内置 GeckoLib，以及新增的 fabric-1.21.1 库线。主模组五线统一钉 `1.2.0-pre.1`，版本上界改为 `2.0.0`。
- `AstralDiceApi` 新增内容查询、钱包读写/转账、按分类过滤的伤害钩子，以及 `BASE`/`ADDITIVE`/`MULTIPLICATIVE`/`FINAL` 攻击/防御属性钩子。NeoForge 1.21.1 与 Forge 1.20.1 安装分类器与属性支持；其余三线只编译、不安装。
- 配套 KubeJS 附属 `0.0.2`（独立仓库）：服务端脚本可注册/查询内容、读写钱包、注册伤害钩子与属性钩子；带仓库根 `logo.png`，并把 `astral_dice` 声明为必需前置 `[1.6.0-pre.1,)`。脚本重载时清空钩子，客户端脚本拒绝调用。
- 把最新开发素材与文档带入 `multi-main`：`images/`、六个新增 `.bbmodel` 源文件、刷新后的 AGENTS/DSH 规则，以及 1.5.1 的发布修复（名刀减费 NPE、Forge 47.1.47 兼容、四线 CurseForge 上传）。
- 验证：五线发布构建与附属双线构建通过；NeoForge 1.21.1 与 Forge 1.20.1 的实机 KubeJS 探针（来自 `26.4-SNAPSHOT.76`）覆盖内容计数、冻结状态、伤害分类、钱包与伤害/属性钩子注册。本次发布除版本 pin 与附属元数据外未改动该代码路径。
- 立牌显示名对齐官方 wiki：Z3000 立牌为 `Z3000 Sign` / `ゼット3000のスタンド`（英文页名与 `CharPlayable.nameJapanese`），中文保留社区惯用「垃圾桶立牌」、不采用 wiki 职称「垃圾箱」；`mamushi_note` 两语文本改按 Souri 立牌物品名（`Amakawa Souri Sign` / `天川蒼鯉のスタンド`）指代。`tools/sign_display_names.json` 增至 37 条，`verify_sign_display_names` 通过。

- 转账面板布局：选中行粉色箭头右移（TP_SELECT_X 66 -> 78，距滚动条滑块左缘留 2px），玩家名列可显示宽度由 42px 加宽到 52px；五线同步。

## 26.4-SNAPSHOT.76

### 本线

- 新增公共战斗 API 包 `com.merlinkitsune.astral_dice.api.combat`：`DamageCategory`（`DICE_MELEE`、`RANGED`、`MAGIC`、`GUN`、`SKILL`、`OTHER`）、`DamageCategoryResolver`、`CombatStat`（`ATTACK`、`DEFENSE`）、`CombatStatStage`（`BASE`、`ADDITIVE`、`MULTIPLICATIVE`、`FINAL`）、`CombatStatContext`、`CombatStatHook`、`CombatStatHandle` 与 `CombatStatHooks`。钩子按优先级降序执行，异常被隔离并记录日志；展示态上下文跳过钩子；未安装支持前注册会被拒绝。
- `AstralDiceApi` 新增 `signs()`、`chips()`、`signIds()`、`chipIds()`、`diceTierIds()`、`isContentFrozen()`；钱包 `walletAvailable`/`walletBalance`/`walletHasBalance`/`walletDeposit`/`walletWithdraw`/`walletTransfer`（尽力转账，失败退款）；伤害分类 `installDamageCategoryResolver`/`damageCategory`/`isDamageCategorySupported`；按分类过滤的 `registerDamageHook`；以及 `installCombatStatSupport`/`isCombatStatSupported`/`registerCombatStatHook`。原有仅按阶段的 `registerDamageHook` 与 `freezeContent` 保留。
- NeoForge 1.21.1 与 Forge 1.20.1 在 common setup 安装 `SpellDamageRegistry::damageCategory` 与属性钩子支持；其余三线只编译、不安装。已接线的两线按 `SKILL` → `GUN` → `MAGIC` → `RANGED` → `DICE_MELEE` → `OTHER` 分类，复用现有枪械 / 法术证据表。GUN 归类不受 `GameplayConstants.ALLOW_FIREARM_DAMAGE` 影响，该配置只影响法术白名单。
- 两线 `DiceCombatEvents` 接入属性钩子：攻击 `BASE` 在基础攻击力之后、内置攻击修正之前；攻击 `ADDITIVE`/`MULTIPLICATIVE` 在内置循环之后；攻击 `FINAL` 在 `DiceBattleResolver.resolve` 之前；防御 `BASE` 在公式之后、累加 `defenseBaseDice`/`defenseCardSum` 之前；防御 `ADDITIVE`/`MULTIPLICATIVE`/`FINAL` 在 padman bypass 之后、结算之前。战斗上下文使用 `display=false`；HUD 与 tooltip 数值不包含脚本钩子。
- 新增独立 KubeJS 附属 `0.0.1`（独立仓库），两线共享反射绑定：内容注册 / 查询、钱包读写、按分类的伤害钩子、攻击 / 防御钩子、仓库根 `logo.png` 图标，元数据要求 `[26.4-SNAPSHOT.76,)`。附属仅限服务器脚本；`/reload` 会清空钩子。
- 验证：五线 `.76` `build` exit 0（`BUILD SUCCESSFUL in 8s`，44 tasks：32 executed / 12 up-to-date；五枚 `.76` jar），附属两线构建通过。NeoForge 1.21.1 实机（`scripts/test/win/logs/1.21.1-20261115-202859.log`）：`Astral Dice KubeJS 0.0.1 loaded (KubeJS 7, NeoForge 1.21.1)`；探针 `signs=36;chips=70;diceTiers=13;frozen=true;categorySupported=true;statSupported=true;magicCategory=magic;diceCategory=dice_melee;walletBalance=166`；行为探针 `cancelHealth=20->20`、`plainHealth=20->16`，攻击 `BASE` 钩子 `runs=1`，防御 `BASE` 钩子 `runs=1`。Forge 1.20.1 实机（`scripts/test/win/logs/1.20.1-20261115-203634.log`，测试期通过临时 Gradle init 脚本排除了无关的 Carpet 测试依赖）：`Astral Dice KubeJS 0.0.1 loaded (KubeJS 6, Forge 1.20.1)`；探针 `signs=36;chips=70;diceTiers=13;frozen=true;categorySupported=true;statSupported=true`；行为 `cancelSeen=magic/incoming/4/4;cancelHealth=20->20;plainHealth=20->16;statSeen=attack/base/0.20800000056624413;statTargetHealth=0;defenseSeen=defense/base/0;defenseTargetHealth=19`。

## 26.4-SNAPSHOT.75

### 本线

- 把主模组内置立牌 / 筹码接入 StarEngine 内容注册表。`BuiltinContent` 在五线 common setup 阶段注册 36 枚立牌与 70 枚筹码，早于服务器启动后的冻结；排除 `blank_sign` 与 `blank_chip`。立牌描述符的学派为 `generic`，冷却取 `GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS`；筹码描述符的学派 / 分类来自商店数据，基础价格为 10，按品阶取权重 6/3/2（稀有 / 史诗 / 传说）。两张表为静态数据，需与物品 / 商店数据同步维护。
- 验证：五线 `.75` `build` exit 0（`BUILD SUCCESSFUL in 12s`，44 tasks：32 executed / 12 up-to-date；五枚 `.75` jar 在 `build/libs`）。NeoForge 1.21.1 实机（`scripts/test/win/logs/1.21.1-20261115-191546.log`）：`Signs.size()=36`、`Chips.size()=70`，两者在服务器启动后均已冻结；样例描述符 `parunan_sign/generic/3600` 与 `atm_chip/STARLIGHT/ECONOMY/10/6`；`blank_sign` / `blank_chip` 均解析为 null；70 枚筹码描述符全部非 null。冻结日志在 `:6421`，mod loaded 在 `:4795`。探针完成后已停止游戏。

## 26.4-SNAPSHOT.74

### 本线

- 内容注册表（`DiceTierRegistry`、`DiceTiers`、`Signs`、`Chips`）改为在服务器启动后冻结（NeoForge/Forge 为 `ServerStartedEvent`，Fabric 为 `ServerLifecycleEvents.SERVER_STARTED`），不再在 common setup 阶段冻结。KubeJS 服务器脚本在 common setup 之后才加载，若提前冻结会让脚本侧的 `AstralDice.registerSign/registerChip` 直接被拒；冻结后的迟到注册仍抛 `IllegalStateException`。
- 在 `DamageBridge` 注释中写明 DEATH 阶段取消语义：取消只阻止平台继续处理死亡（掉落、死亡消息等），不会恢复已扣生命值，也不会让实体回到存活状态；需要目标存活时应在 POST 阶段补回生命值，或在 INCOMING/AFTER_ARMOR 阶段改写伤害使其不致死。
- 验证：五线 `.74` `build` exit 0（`BUILD SUCCESSFUL in 24s`，44 tasks：37 executed / 7 up-to-date；五枚 `.74` jar 在 `build/libs`）。NeoForge 1.21.1 实机（`scripts/test/win/logs/1.21.1-20261115-185541.log`）：KubeJS 预冻结脚本在 :5964 注册 `kubejs_prefreeze_sign`，:6422 输出冻结日志；探针返回 `sign=true;signCount=1;frozenSign=true;frozenChip=true;frozenTiers=true;frozenRegistry=true;lateSign=...IllegalStateException: content registry sign is frozen`。本批未做客户端视觉检查。

## 26.4-SNAPSHOT.73

### 本线

- 修复共享商店数据中 9 张卡牌的物品 ID 与注册名不一致（`monster_laser_card` → `effect_card_monster_laser`、`chocolate_cake` → `effect_card_chocolate_cake`、`you_have_i_have` → `effect_card_you_have_i_have`、`monster_brick_card` → `effect_card_monster_brick`、`hamburger` → `effect_card_hamburger`、`luxury_feast` → `effect_card_luxury_feast`、`express_delivery` → `effect_card_express_delivery`、`orbital_strike_card` → `effect_card_orbital_strike`、`directional_blast_card` → `effect_card_directional_blast`）。这 9 个 ID 都解析为 `minecraft:air`，导致卡牌交易校验器打印 9 条「命中被排除项：<空气>」，商店池里出现空条目。
- `CardShopCatalog.applyData` 现在会跳过无法解析或未注册的卡牌物品 ID 并输出错误日志，不再把空气 `PoolEntry` 放进池子；错误的数据包不会再让空卡上架。
- 验证：五线 `.73` `build` exit 0（`BUILD SUCCESSFUL in 14s`，44 tasks：32 executed / 12 up-to-date；五枚 `.73` jar 在 `build/libs`）。NeoForge 1.21.1 实机（`scripts/test/win/logs/1.21.1-20261115-184222.log`）：23 张商店卡牌全部解析到已注册物品，`CardShopCatalog.entries()` 为 23 条，`CardTradeFilter.validatePool` 异常 0；日志 `:6137`（商店池 23 张、异常 0）与 `:7731`（smoke_reload 23 张、异常 0）。三条 Curios `scroll/feet/spellbook is not a registered slot type!` 错误与本次改动无关。

## 26.4-SNAPSHOT.72

### 本线

- Forge 1.20.1 与两条 Fabric 线补齐 StarEngine 的四个伤害阶段。Forge 仍无原生「扣血之后」事件，POST 改由 `LivingEntity#actuallyHurt` 的生命值差补齐：HEAD 记录进入时生命值，RETURN 仅在生命值真的下降时派发。
- Fabric 1.20.1 / 1.21.1 通过 `LivingDamageBridgeMixin` 增加 AFTER_ARMOR 与 POST：在 `LivingEntity#actuallyHurt` 内包裹 `setHealth` 调用，先按「护甲/吸收之后」的伤害派发 AFTER_ARMOR（可取消或改写），再按实际生命值损失派发 POST。该路径同时覆盖 Fabric 1.21.1 的 `AFTER_DAMAGE` 在致死伤害时不派发的缺口。
- 移除 Fabric 1.21.1 的 `AFTER_DAMAGE` 注册，避免 POST 重复并消除致死伤害漏派发；`ALLOW_DAMAGE` 仍只映射 INCOMING 且只能取消。
- Mixin 注册：Forge 为 `bridge.LivingDamagePostBridgeMixin`，两条 Fabric 线为 `bridge.LivingDamageBridgeMixin`。伤害来源用 ThreadLocal 栈保存，嵌套伤害仍能正确配对。
- 验证：五线 `build` exit 0（`BUILD SUCCESSFUL in 15s`，44 tasks：27 executed / 17 up-to-date；`.72` jar 在 `build/libs`）。Forge refmap 已映射到 `m_6475_`；Fabric 最终 jar 内已重映射为 `method_6074` / `method_6033`。本批未做实机客户端测试。

## 26.4-SNAPSHOT.71

### 本线

- 把 StarEngine 伤害钩子链接入主伤害事件。NeoForge 1.21.1 / 26.1.2 派发四阶段（INCOMING、AFTER_ARMOR、POST、DEATH）；Forge 1.20.1 派发 INCOMING、AFTER_ARMOR、DEATH（该线没有原生 Post 事件）；Fabric 1.21.1 派发 INCOMING、POST、DEATH；Fabric 1.20.1 派发 INCOMING、DEATH。
- Fabric 的 `ALLOW_DAMAGE` 只能取消、不能改写进入伤害；桥接层把 amount 改写视为建议值。Forge 缺原生 Post，Fabric 1.20.1 缺 Post/AfterArmor；这些差异已在桥接类注释中写明。
- 默认不注册任何钩子，因此未注册时事件接线不改变行为。
- 验证：五线 `build` exit 0（`BUILD SUCCESSFUL in 15s`，44 tasks：32 executed / 12 up-to-date；`.71` jar 在 `build/libs`）。本批未做实机客户端测试。

## 26.4-SNAPSHOT.70

### 本线

- 五线卡牌商店与筹码商店的池改为数据包驱动：`data/<namespace>/astral_dice/shop/*.json` 声明 23 张卡牌池与 70 枚筹码池（id、tier、school、category、weight、combat、星级门槛）；没有有效文件时继续使用内置池。
- 新增 `ShopData` 与 `AstralDiceDataStore` 的商店快照；五线各注册一个商店 reload listener；重载后 `ChipShopCatalog` / `CardShopCatalog` 直接切换 active pool/index，无需重启服务器。
- Fabric 1.20.1 / 1.21.1 使用同一份默认数据；路障卡仍通过 Fabric 的 `byItemId` 助手延迟解析，因此 Fabric 池与 Neo / Forge 语义一致。
- 验证：五线 `build` exit 0（`BUILD SUCCESSFUL in 15s`，44 tasks：32 executed / 12 up-to-date；`.70` jar 在 `build/libs`）。本批未做实机客户端测试。

## 26.4-SNAPSHOT.69

### 本线

- 五线星愿铁砧与战利品注入改为数据包驱动：`data/<namespace>/astral_dice/anvil/*.json` 控制星币成本、经验成本、最高星阶、材料标签与逐物品面额；`data/<namespace>/astral_dice/loot/*.json` 通过原版战利品表控制箱子与实体注入。
- 删除旧硬编码铁砧数值与 NeoForge 的 GLM / `onLivingDrops` / 四池通道。默认数据放在 `common/src/main/resources`；NeoForge / Forge 用延迟重载队列，Fabric 从 `LootTableEvents.MODIFY` 解析数据包（Fabric 1.21.1 在服务器启动前回退到内置默认数据）。
- 原生表覆盖 common / end_city / buried_treasure 箱子、星币与特殊星盘档位（凋灵、监守者、远古守卫者、恶魂、凋灵骷髅、守卫者、通用 Monster）。普通怪星盘统一 1%；凋灵 5；末影龙 100%。
- 验证：五线 `build` exit 0（`BUILD SUCCESSFUL in 14s`，44 tasks：32 executed / 12 up-to-date；`.69` jar 在 `build/libs`）。本批未做实机客户端测试。

## 26.4-SNAPSHOT.68

### 本线

- 五线星愿铁砧界面不再在玩家物品栏上方绘制灰色「物品栏」（`playerInventoryTitle`）。NeoForge 1.21.1 的界面不再调用 `super.renderLabels`；其余四线删除各自显式的物品栏标签绘制。
- 铁砧标题改为固定使用 `container.astral_dice.astral_anvil` 组件并带中文兜底，不再依赖菜单传入的标题，避免部分加载器传入空标题或原始 ID 时把界面标题显示成 `astral_dice:astral_anvil`。
- 验证：五线 `:build` exit 0（`BUILD SUCCESSFUL in 18s`，44 tasks：37 executed / 7 up-to-date；日志 `temp/build-68.log`）。NeoForge 1.21.1、Forge 1.20.1、NeoForge 26.1.2、Fabric 1.20.1、Fabric 1.21.1 五线实机打开星愿铁砧界面，标题均为「星愿铁砧」，物品栏上方均无灰色「物品栏」标签：`run/neoforge-1.21.1/screenshots/2026-11-15_14.26.40.png`、`run/forge-1.20.1/screenshots/2026-11-15_14.28.57.png`、`run/neoforge-26.1.2/screenshots/2026-11-15_14.29.25.png`、`fabric-1.20.1/run/client/screenshots/2026-11-15_14.30.00.png`、`fabric-1.21.1/run/client/screenshots/2026-11-15_14.30.28.png`。

## 26.4-SNAPSHOT.67

### 本线

- 星愿铁砧不再在 `experimental_star_upgrade` 开启时重置星级或归还卡牌；星愿铁砧与原版铁砧现在都固定只加 1 星，并保留输入骰子已装配的卡牌。
- 把重置移到骰子升阶合成（普通→黄金→钻石）：开启实验开关后，合成出的新骰子重置为 0★、清空已装配卡牌、已用攻/防费用归零，卡牌上限回到 0★ 基础值；母体骰子已装配的卡牌退回玩家。合成投入与卡牌费用不退（B 方案）。
- 五线新增 `DiceUpgradeCraftHandler`（仅服务端、仅实验开关开启时生效）；两条 Fabric 线在 `AstralDiceMod` 显式注册。删除铁砧菜单/事件处理器中的重置分支与 `ANVIL_STAR_RESET_CARDS_RETURNED` 日志。
- JEI `astral_dice:astral_anvil` 页面不再显示「重置 X★ → 0★」；15 份语言文件已删除 `star_reset` 键，配置注释改为描述升阶合成重置，而不是铁砧重置。
- 验证：五线 `compileJava` exit 0（`BUILD SUCCESSFUL in 17s`，8 tasks；日志 `temp/compile-b-all-2.log`）。五线 `.67` `:build` exit 0（`BUILD SUCCESSFUL in 21s`，44 tasks；37 executed，7 up-to-date；日志 `temp/build-67.log`）；五枚 `.67` jar 均含 `DiceUpgradeCraftHandler`，并已部署到 `run/<line>/mods`。NeoForge 1.21.1 实机（`experimental_star_upgrade=true`，`scripts/test/win/logs/1.21.1-20261115-134430.log`）：工作台用带 medium 卡的 2★ `astral_dice:dice` + 4 金锭 + 4 星币合成 `golden_dice`，产物为 0★、`appliedStones` 为空、已用攻/防费用 0/0、上限 3/3。取走后退回 1 张 `astral_dice:attack_card_medium`（日志 `:9453`：`CRAFT_STAR_RESET_CARDS_RETURNED player=Dev source=astral_dice:dice star=2->0 cards=1`），金锭与星币均未返还。另一枚带卡 2★ 骰子在星愿铁砧升到 3★、medium 卡保留，消耗 25 星币（64→39），确认铁砧不再重置。点击箭头打开 JEI `astral_dice:astral_anvil` 页面，显示「星阶 0→1 · 15 星币 / 升级花费：5」，没有「重置」文案；截图 `temp/astral-anvil-evidence/neoforge-1.21.1/jei-67-no-reset.png`（sha256 `1f58b4ca2bb801358fd1539dc6873b79661d2d89bdb23d2172d89a0d9c639a92`）。其余四线本批仅构建、未实机。

## 26.4-SNAPSHOT.66

### 本线

- 修复星愿铁砧的 JEI 空槽入口：不放入任何物品时，左键点击箭头也会打开 `astral_dice:astral_anvil` 类别并列出全部 39 条升星配方（13 种骰子 × 0/1/2 星），不再要求输入槽或结果槽有物品；「查看配方」tooltip 同样在空槽时显示。实现上 `AstralAnvilJeiBridge` 允许空栈，插件收到空栈时把完整配方列表交给 `RecipesGui`。
- 验证：五线 `.66` `:build` exit 0（`BUILD SUCCESSFUL in 15s`，44 tasks；32 executed，12 up-to-date；日志 `temp/build-66.log`）。NeoForge 1.21.1 实机（`scripts/test/win/logs/1.21.1-20261115-132255.log`，加载 `astral_dice-26.4-SNAPSHOT.66+neoforge_1.21.1.jar`，`experimental_star_upgrade=false`）：清空背包、在 `(8,-60,5)` 放星愿铁砧、打开空界面后，左键点击箭头，`mc.screen` 从 `AstralAnvilScreen` 变为 JEI `RecipesGui`，类别内共 39 条配方，页码 `1/14`，全部为升星页（星阶 0→1 / 1→2 / 2→3，升级花费 10）；截图 `run/neoforge-1.21.1/screenshots/2026-11-15_13.23.52.png`。其余四线本批仅构建、未实机。

## 26.4-SNAPSHOT.65

### 本线

- 修复五线星愿铁砧 JEI 页面的整体布局：类别宽度由 180 px 改为 128 px，JEI 会把配方面板居中，不再在右侧留下大片空白。
- 点击铁砧箭头时，现在会把全部 39 条升星页（13 种骰子 × 0/1/2 星）交给 JEI，并把与输入骰子、当前星阶匹配的那条排到最前。此前只传这一条，所以界面恒为 `1/1`，也只显示普通骰子的费用。
- 每条页面现在读取自己的骰子与星阶：关闭 `experimental_star_upgrade` 时所有骰子都显示 10 级费用；开启时 `dice_t0`…`dice_t4` 分别显示 5/10/15/20/30，未分阶骰子显示 5；星币成本仍按星阶为 15/20/25。
- 把 JEI 的费用文案从「附魔花费」改为「升级花费」（英文 `Upgrade Cost`，日文「アップグレード費用」），15 份语言文件均已更新。
- 验证：五线 `.65` `:build` exit 0（`BUILD SUCCESSFUL in 15s`，44 tasks；32 executed，12 up-to-date；日志 `temp/build-65.log`）；五枚 `.65` jar 均含 `AstralAnvilJeiCategory.recipesStartingWith` 与改名后的 `Upgrade Cost` 文案。NeoForge 1.21.1 普通模式实机（`scripts/test/win/logs/1.21.1-20261115-123420.log`，`experimental_star_upgrade=false`）：`diamond_dice` + 64 星币时 `cost=10`；点击箭头打开 `astral_dice:astral_anvil`，面板居中、页码 `1/14`，39 条（13 种骰子 × 0/1/2 星）全部显示 `升级花费：10`；截图 `temp/astral-anvil-evidence/neoforge-1.21.1/jei-65-normal.png`。实验模式（`scripts/test/win/logs/1.21.1-20261115-123622.log`，开关开启）：`nether_star_dice`（t4）+ 64 星币时 `cost=30`；JEI 页首条为「重置 0★ → 0★」「升级花费：30」，39 条按经验 5/10/15/20/30 的分布为 3/9/15/9/3；截图 `.../jei-65-experimental.png`。其余四线本批已构建、未实机。

## 26.4-SNAPSHOT.64

### 本线

- 五线星愿铁砧界面新增 JEI 配方入口：左键点击材料槽与结果槽之间的金色箭头，现在会打开专属的 `astral_dice:astral_anvil` JEI 类别，列出 13 种骰子 × 0/1/2 星共 39 条升星规则，并以星愿铁砧为催化剂显示星币成本与经验成本。具体页面按输入骰子的物品与当前星阶精确匹配，因此显示的是星愿铁砧升星规则，而不是骰子的工作台合成配方。JEI 可用时悬停箭头显示「查看配方」；界面只依赖不引用 JEI 类型的 `AstralAnvilJeiBridge`，未安装 JEI 时模组仍可正常加载。NeoForge/Forge 通过 `@JeiPlugin` 发现 `AstralAnvilJeiPlugin`，两条 Fabric 线在 `fabric.mod.json` 的 `jei_mod_plugin` 中注册；15 份语言文件均已加入新类别与提示键。
- 新增 `DiceStarUpgrade.starLevel(ItemStack)`（非骰子返回 -1），让 JEI 类别与箭头在不重复组件读取逻辑的前提下匹配到精确的升星页。
- 让该 JEI 页随模式切换：`experimental_star_upgrade` 开启时，每条规则显示「重置 X★ → 0★」，并保持与游戏内铁砧一致的 15/20/25 星币与分级经验成本；关闭时仍显示「星阶 X→Y · N 星币」。15 份语言文件均已加入新的重置键。
- 从玩法中隐藏残留的 `astral_dice:vacuum_piece`「管道原型」：五线创造栏均已移除，NeoForge 1.21.1 的三份语言名称也已删除。物品注册与物品模型保留，因为 `GrapplingHookRenderer` 仍用 `vacuum_piece` 作为 Z3000 抓钩管道的内部模型载体；直接删除会破坏该渲染器。
- 验证：五线 `.64` `:build` exit 0（`BUILD SUCCESSFUL in 3s`，44 tasks；21 executed，23 up-to-date；日志 `temp/build-64.log`）。五枚 jar 均含 `AstralAnvilJeiCategory.class`，五份 `zh_cn.json` 均含 `重置 %s★ → 0★`。NeoForge 1.21.1 实机（`scripts/test/win/logs/1.21.1-20261115-114726.log`）：打开星愿铁砧界面，把 1 枚 `astral_dice:dice` 与 64 枚 `astral_dice:star_coin` 放入输入槽，左键点击箭头；JEI 打开 `astral_dice:astral_anvil`，页码 `1/1`：铁砧 + 骰子 + 15 星币 → 骰子，显示「重置 0★ → 0★」「附魔花费: 5」（测试实例 `experimental_star_upgrade=true`；该配方自身 `coinCost=15`、`xpCost=5`）。JEI 运行时物品清单共 1928 个物品栈，`vacuum_piece` 命中 0；创造栏也无「管道原型」条目。其余四线本批已构建、未实机。截图证据：`temp/astral-anvil-evidence/neoforge-1.21.1/jei-astral-anvil.png` 与 `...-crop.png`（未提交）。


## 26.4-SNAPSHOT.59

### 本线

- 修复五线星愿铁砧的物品栏图标。方块模型缺少 `minecraft:block/block` 父模型，物品状态没有继承标准 GUI 显示变换，图标会变成扁平、过大的一个面，而不是正常方块图标。现改为继承原版方块 GUI 变换（30°/225° 旋转、0.625 缩放），与铁砧物品图标一致；世界内模型、UV、贴图、配方与物品模型均未改动。
- 验证：五线 `:build` exit 0（BUILD SUCCESSFUL in 10s，44 tasks；32 executed，12 up-to-date）；五枚 `.59` jar 均含带父模型的方块模型；`check_changelog_parity.py` 输出 `CHANGELOG_PARITY: OK sections=81 entries=1080/1080`。本轮未实机：改动只是原版方块 GUI 显示变换，不涉及新渲染代码。

## 26.4-SNAPSHOT.58

### 本线

- 五线新增 **星愿铁砧**（`astral_dice:astral_anvil`）：继承原版铁砧的下落、落地/破坏音效与水平朝向，且没有耐久、不会损坏。界面为 176×166 的铁砧式布局，三个输入槽（一个骰子 + 两个材料）和一个结果槽；材料槽由 `astral_dice:astral_anvil_materials` 物品标签控制，初始只接受 `astral_dice:star_coin`。方块由 `minecraft:anvil` + 2×`astral_dice:star_coin_bag` 无序合成。星愿铁砧**不**加入 `minecraft:anvil` 方块标签，菜单也不会调用 `AnvilBlock.damage`，因此原版铁砧的损坏/移除逻辑不会作用到它。
- 原版铁砧的骰子升星仍然保留。新增公共配置项 `experimental_star_upgrade`（默认 `false`，配置版本 8），同时控制**两种铁砧**上的实验性行为：开启后，升星成功产出 0★ 骰子，已装备卡牌退回玩家物品栏（生存模式满栏时掉落，创造模式直接丢弃），经验消耗按输入骰子的 `astral_dice:dice_t0`…`dice_t4` 标签分级（5/10/15/20/30，未命中回退 5）。关闭时两种铁砧均保持现有 10 级上限、星阶 +1、卡牌保留和 15/20/25 星币材料成本不变。
- 补齐五线资源与接线：176×166 GUI 贴图、32×32 方块贴图、方块模型/blockstate/物品模型、无序配方、战利品表、三语名称与提示、镐/铁工具标签、创造栏条目、菜单类型、Screen 注册，以及 26.1.2 的 blockstate/item model datagen 注册。
- 验证：五线 `:build` exit 0；增量复跑 BUILD SUCCESSFUL in 2s。五枚 `.58` jar 均含星愿铁砧类、GUI/方块贴图、模型、配方、战利品表与材料标签。复核跟进：菜单的经验消耗 `DataSlot` 改为只在逻辑服务端写入，专用服务端与客户端配置不同时不会再被客户端本地值覆盖。五线实机（NeoForge 1.21.1、Forge 1.20.1、NeoForge 26.1.2、Fabric 1.20.1、Fabric 1.21.1）均通过：关闭开关时，带卡 2★ 骰升到 3★ 且卡牌保留、快捷移动不复制；星愿铁砧界面/下落方块/音效正常，原版铁砧方块不被移除。开启开关时，两种铁砧均产出 0★ 骰、卡牌退回玩家、t0..t4 经验 5/10/15/20/30；满栏时生存掉落归还卡牌、创造直接丢弃。每线均截图留证：方块模型、两种 GUI、取走后背包与 t4 经验。
- 五线修复星愿铁砧方块模型：原 UV 按 32×32 贴图像素空间编写，超出 Minecraft 模型 0–16 的 UV 范围，导致上部结构采样到图集里的相邻贴图、显示成透明彩色框架。现已把全部 UV 归一化到 0–16；方块贴图与模型形状未改。
- 修复星愿铁砧菜单在快捷移动（Shift 点击）下的卡牌复制：`quickMoveStack` 会先把结果槽搬空再回调 `onTake`，传入的栈因此为空，0★/空卡判定会把输入骰子的卡牌退回，而产物其实仍带着这些卡。现改为在清空输入前从输入栈重算预期产物；NeoForge 1.21.1、Forge 1.20.1、NeoForge 26.1.2、Fabric 1.21.1 四线修复。另把 `cost.set(0)` 改为只在逻辑服务端执行（复核 P3-A）。Fabric 1.20.1 原已有空栈回退，只补了成本守卫。
- 修复五线原版铁砧 `onAnvilRepair` 的同一空栈问题：修复事件的 `output` 是 `onTake` 传入的栈，快捷移动后该栈为空，原逻辑会同时跳过卡牌归还与筹码商店刷新。现在改用 `DiceStarUpgrade.resultFor(...)` 从左槽输入重算预期产物，再用它做 0★/星阶 +1 判定，随后照常归还卡牌并授予刷新；实验开关关闭时，快捷移动曾静默丢失的刷新也随之恢复。

## 26.4-SNAPSHOT.53

### 本线

- 四个扁平筹码流派标签补齐 Fabric 1.20.1 / Fabric 1.21.1，五线现在全部带有 `astral_dice:school_starlight`（11 枚）、`school_healing`（10 枚）、`school_mark`（9 枚）、`school_charge`（11 枚），共 41 枚。Fabric 标签取值与 P0 三线逐字节一致，按 `ChipShopCatalog.POOL` 顺序，泛用 29 枚与 `blank_chip` 仍排除在外。
- 两条 Fabric 的 `ModTags.Items` 新增 `SCHOOL_STARLIGHT` / `SCHOOL_HEALING` / `SCHOOL_MARK` / `SCHOOL_CHARGE` 四个 `TagKey<Item>` 常量，沿用各线 `TagKey.create(Registries.ITEM, ...)` 工厂。标签目前仍只作数据用途，没有消费方，留给未来的研究系统。
- `tools/verify_chip_school_tags.py` 改为五线全部必需：缺文件、部分缺失、`item`/`items` 目录放错、多出 school 文件或常量、值顺序错或重复、GENERIC / `blank_chip` 泄漏、非 `chips.json` 成员，都会在对应线失败；Fabric 不再跳过。
- 验证：门禁五线 exit 0，均 `total=41 chips=71`；五线 `:build` → BUILD SUCCESSFUL in 9s（44 tasks；32 executed，12 up-to-date），五枚 `.53` jar 均含四个流派标签 JSON，Fabric 两枚也已包含。8 个新 Fabric JSON 与 NeoForge 1.21.1 副本逐字节一致，CRLF、无 BOM、末尾换行。本轮未实机：标签目前仍无游戏内消费方。

## 26.4-SNAPSHOT.52

### 本线

- NeoForge 1.21.1、Forge 1.20.1、NeoForge 26.1.2 三线新增四个扁平筹码流派物品标签：`astral_dice:school_starlight`（11 枚）、`school_healing`（10 枚）、`school_mark`（9 枚）、`school_charge`（11 枚），共 41 枚。取值按 `ChipShopCatalog.POOL` 顺序过滤；泛用 29 枚不打标签，`blank_chip` 不入标签。
- 三线 `ModTags.Items` 暴露 `SCHOOL_STARLIGHT` / `SCHOOL_HEALING` / `SCHOOL_MARK` / `SCHOOL_CHARGE` 四个 `TagKey<Item>` 常量；目前只作为数据标签，没有消费方，留给未来的研究系统接入。