# B2-CORE 事件服务端核心移植报告（forge-1.20.1 → fabric-1.20.1）

- 批次：B2-CORE（B2 事件体系的**服务端核心层**）
- 日期：2026-11-01
- 蓝本：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/`（同 MC 版本，唯一蓝本）
- 产物目录：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/`
- 结论：**7 个工单文件全部落地 + 1 个必要补丁（AstralEventSystem.applyEventBonus）+ 1 个越出工单但无其它批次认领的第 8 文件（InvestigationArbitration）**。源码级验收全绿（difflib 共 30 个 hunk，逐处有因）。**整线仍编不过**，缺的符号全部属于别的批次（见「四、阻塞项」），不是本批产物的问题。未跑 gradle、未启动游戏、未 commit。

## 一、交付清单

| 文件（event/ 下） | 行数 | 变更 | 与 forge 关系 |
|---|---|---|---|
| EventEffects.java | 640 | 新建 | 搬运 + 4 类平台改写（12 hunk） |
| EventCardService.java | 151 | 新建 | 搬运 + 3 类平台改写（4 hunk） |
| AstralEventRegistry.java | 223 | 新建 | **逐字复制，0 hunk** |
| AstralEventDispatcher.java | 174 | 新建 | 搬运 + import + javadoc（1 hunk） |
| EventChoiceManager.java | 338 | 新建 | 搬运 + 注解→注释 + javadoc（3 hunk） |
| PlayerEffectProtection.java | 204 | 新建 | 搬运 + 注册表 API（3 hunk） |
| CurioDeathKeepHandler.java | 112 | 新建 | 搬运 + 饰品门面 helper（5 hunk） |
| InvestigationArbitration.java | 120 | 新建 | **超出工单的第 8 文件**（见「四-4」）（2 hunk） |
| AstralEventSystem.java | 132 | **修改**（唯一动到的既有文件） | 补 `public applyEventBonus`（117→132 行，+15 行，其余零改动） |

- 行尾：全部 CRLF（与 `event/api/`、B2-2 三文件、`porting/*.md` 一致），无 BOM，尾换行齐全。实证：`crlf == lf` 对 8 个新文件逐一成立。
- 未触碰禁改清单：`AstralDiceMod.java` / `network/ModNetwork.java` / `item/ModItems.java` / `init/ModCreativeTabs.java` / 三份 lang / `astral_dice.mixins.json` / `item/sign/**` / `component/**` / `shop|screen|client/**` / `mixin/**` / `build.gradle`。`git status` 在 event/ 下本批只新增 8 个未跟踪文件 + 修改 `AstralEventSystem.java`（另外 3 个 M 文件是别批次的，未碰）。

## 二、逐文件 difflib 结论（剥 import/package、LF 归一后比对）

比对脚本：`difflib.SequenceMatcher`（autojunk=False），逐 hunk 打印 `unified_diff`。总计 **30 个 hunk**，下面逐条给因。

### 1. EventEffects.java（forge 583 → fab 603 行，12 hunk）
1) 类 javadoc 追加 6 行「本线没有 `@Mod.EventBusSubscriber`，必须由主 Agent 登记」警示块。
2) **删** forge:17 的 `@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = ...)` —— 最易漏的全限定注解，本线一律去掉。
3) 新增 `private static final double MOB_ARMOR_PER_DEFENSE = 2.0D;`（10 行含 javadoc）：forge 用 `DiceCombatModifiers.MOB_ARMOR_PER_DEFENSE`（= `1.0 / CombatFormula.MOB_ARMOR_COEF`），本线 `combat/DiceCombatModifiers.java:211,920` 把同一系数**内联**成 `* 2.0`、无公开常量 ⇒ 本地声明，取值同口径。带 `TODO-PLATFORM`。
4) `2 * DiceCombatModifiers.MOB_ARMOR_PER_DEFENSE` → `2 * MOB_ARMOR_PER_DEFENSE`（唯一消费点）。
5-9) 「雷暴将至夜间刷怪 ×2」整段（forge:306-341 的 javadoc + `onFinalizeSpawn` 方法体）用 `/* ... */` 整体注释（fab:359-397），上方换成 16 行 `TODO-BRIDGE`：说明本线 `platform/event/**` 与 `mixin/**` 均无 `MobSpawnEvent.FinalizeSpawn` 垫片（`grep -rn 'MobSpawnEvent|FinalizeSpawn' fabric-1.20.1/src/main/java` 零命中，照抄即编译失败）、给出 mixin 注入点、并如实登记「`AP_EVENT_EFFECT: storm_night_spawn_x2` 现阶段永不打印」。**`placeStormCopy` / `hasStormWatcherNear` 两个 helper 保留在注释外**（桥接要复用，现为未引用私有方法，javac 不报错）。
10) `onPlayerTick` 的 javadoc 补一行：本线 `platform/FabricBridges.java` 只派发 END 相位 ⇒ `phase == END` 判据恒真但**必须保留**。
11-12) `@net.minecraftforge.eventbus.api.SubscribeEvent` + `net.minecraftforge.event.TickEvent.PlayerTickEvent` / `LivingHealEvent` 全限定写法 → 平台垫片 `@SubscribeEvent` / `TickEvent.PlayerTickEvent` / `LivingHealEvent`（forge 蓝本这一段用的是全限定名，import 里没有）。

### 2. EventCardService.java（118 → 139，4 hunk）
1) 类 javadoc 追加 13 行平台差异块（发包 + 目标选择器取消）。
2) forge `ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), msg)` → `ModNetwork.sendToPlayer(sp, msg)`（`network/ModNetwork.java:101`；本线 SimpleChannel 无 `PacketDistributor`）。
3) `TargetSelectionManager.cancel(sp, "animation")` → `TargetSelectionManager.cancelSessionForTests(sp)` + 5 行 TODO-BRIDGE：本线**没有** `cancel(ServerPlayer, String)` 重载（只有 token 版 `cancel(ServerPlayer,int)` 与 `cancelSessionForTests(Player)`）；后者 = `clearSessionState`（清会话 + 清抑制闩 + 清门控，**不写闩**）⇒ 满足 D5「动画结束即可选」，但**不发 TargetSelectClose 关闭包**。
4) `playTriggerSounds` javadoc 补 4 行：本线 `audio/ModSounds.java` 缺 6 个 `EVENT_*` 常量（属 B2-7）。
- 常量原样：`DELAY_TICKS = 38`、`CARD_ANIM_TICKS = 28`、`SETTLE_BUFFER_TICKS = DELAY_TICKS - CARD_ANIM_TICKS`、`QUESTION_COOLDOWN_TICKS = DELAY_TICKS + 20 * 10`。

### 3. AstralEventRegistry.java（207 → 207，**0 hunk**）
逐字复制（唯一 import 差异为零：纯 vanilla + 同包）。13 条事件注册、`validate()` 用 `AstralEventRegistry.class.getResource(path)` 查图标，全部与 forge 同字节（仅行尾按本线 CRLF）。

### 4. AstralEventDispatcher.java（153 → 161，1 hunk）
- 业务体 **0 差异**：`ITEM_USE → GlobalEventGate.tryAcquire(...)` 原样保留（全仓唯一调用点，裁决 D1）；`secretOnly` 三档作用域；`showThenRun` 回调里 `timer → effect → afterDispatch → applyEventBonus → playTriggerSounds` 顺序不变。
- 唯一 hunk：类 javadoc 追加 8 行「移植口径（勿按旧文档改）」块。
- import 级（difflib 剥掉了）：`com.merlinkitsune.starengine.combat.PartyRelations` → `com.merlinkitsune.astral_dice.combat.PartyRelations`（本线该类在本模组 `combat/`，forge 在 starengine 库）。

### 5. EventChoiceManager.java（311 → 314，3 hunk）
1) javadoc:19-20 平台措辞（`{@link TickEvent.PlayerTickEvent}` → `{@code TickEvent.PlayerTickEvent}`；「死亡 = Forge 的」→「死亡 = 平台垫片的」）。
2) **删** `@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)`，原位换 4 行注释（`LoaderBus.INSTANCE.register(EventChoiceManager.class)` 待办 + 漏了会怎样的后果）。
3) `onPlayerChangedDimension` 的 javadoc 换成实证版本：本线该事件由 `mixin/bridge/ServerPlayerDimensionTravelBridgeMixin.java:68` 在 RETURN 处 `LoaderBus.INSTANCE.post(...)` 派发（已 grep 证实存在）；`PlayerEvent#getEntity()` 在本线返回 `Player`（`platform/event/entity/player/PlayerEvent.java:48`）⇒ `instanceof` 判定必须保留。
- 业务体 0 差异：`OPTION_COUNT=2`、`CHOICE_TIMEOUT_TICKS=15*20`、`start/confirm/exitWithoutChoice/cancelFor/forceFirst/firstDispatchable/isDispatchable/tick` + 4 个钩子；**没有** `GlobalEventGate.canStart` 预判（D1）；`forceFirst` 里 `instanceof` 早退在 `SESSIONS.remove` 之前（P2 修复，勿互换）。

### 6. PlayerEffectProtection.java（194 → 194，3 hunk）
1) javadoc:29 `ForgeRegistries.MOB_EFFECTS` → `BuiltInRegistries.MOB_EFFECT`。
2) `removeNow` 里效果 id 反查：`net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(effect)` → `net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect)`。
3) `effectIdOf` 同上。
- import 级：删 `com.merlinkitsune.starengine.event.EffectTimerGuard`（本线 `event/EffectTimerGuard.java` **同包**，无需 import）；保留 `com.merlinkitsune.starengine.event.ModEffectRemoval`（库类，本线多处已用）。
- 本类无 `@SubscribeEvent`（由 `event/ModEffectEvents` 调用）⇒ **不需要 LoaderBus 登记**。

### 7. CurioDeathKeepHandler.java（68 → 96，5 hunk）
1) 删 forge 第 16 行的空行（import 分组重排的副产物）。
2) **删** `@Mod.EventBusSubscriber`，原位换 4 行登记注释。
3) 新增 26 行 `private static List<ItemStack> allEquipped(Player)`：forge 用 `CurioSlotUtil.allEquipped(player)`，本线 `item/CurioSlotUtil.java` **没有该方法**（只有 `hasSameItemEquipped / hasAnyCurioMatching / tryAutoEquip / isIntentionalUnequip / runOnIntentionalUnequip / hasDiceEquipped`）⇒ 按 `compat/curios/CuriosApi.getCuriosInventory → getCurios() → getStacks() → 逐槽` 内联等价实现，遍历口径与 `CurioSlotUtil.hasAnyCurioMatching:56-70` 完全一致（返回槽位内实例本身，非副本）。带 `TODO-PLATFORM`（建议下沉回 `CurioSlotUtil`）。
4) `CurioSlotUtil.allEquipped(player)` → `allEquipped(player)`（唯一消费点）。
5) `Clone#getEntity()` 的行尾注释改为「1.20.1 两线同形 + 垫片位置」。
- **没有** import `top.theillusivec4.curios.*`；饰品门面只用本线 `compat/curios/CuriosApi`。
- 事件 import 换垫片：`platform.event.{EventPriority, SubscribeEvent}` + `platform.event.entity.living.LivingDropsEvent` + `platform.event.entity.player.PlayerEvent`。派发点已实证：`PuzzlesBridges.java:117`（LivingDropsEvent）、`PuzzlesBridges.java:198`（Clone）。

### 8. InvestigationArbitration.java（104 → 109，2 hunk）— **超出工单的第 8 文件**
1) javadoc 追加 5 行移植口径。
2) `CuriosCompat.getCuriosInventory(player)` → `CuriosApi.getCuriosInventory(player)`。
- import 级：`starengine.combat.PartyRelations` → `astral_dice.combat.PartyRelations`、`starengine.item.CuriosCompat` → `compat/curios/CuriosApi`。业务体（`teamDetectives / teamHasSecretDetective / canDispatchSecretOnly / pickHighestTier / alreadyUnderInvestigation` + `AP_EVENT_TIER_PICK` 机器行）逐字保留。

## 三、public 签名表（本批产物对外面）

**EventEffects**：`redHeatWarning / swiftStride / stormApproaching / shopBonanza / handErasure / worldCollapse / giftFromSky / angelDescent / problemDelivery / crowdedPassage / battleFrenzy (ServerLevel, Player, Player, AstralEvent, …)`、`learnFromOthersApply(Player, List<Player>)`、`afterDispatch(Player, AstralEvent, List<Player>)`、`onPlayerTick(TickEvent.PlayerTickEvent)`、`onHeal(LivingHealEvent)`、`isHealBlocked(Player)`
**EventCardService**：`DELAY_TICKS=38`、`CARD_ANIM_TICKS=28`、`SETTLE_BUFFER_TICKS=10`、`QUESTION_COOLDOWN_TICKS=238`；`schedule(ServerLevel,int,Consumer<ServerLevel>)`、`showThenRun(ServerLevel, List<Player>, AstralEvent, int, boolean, Runnable)`、`drain(ServerLevel)`、`playTo(Player, SoundEvent)`、`playTriggerSounds(List<Player>, AstralEvent)`
**AstralEventRegistry**：`register(AstralEvent)`、`get(ResourceLocation)`、`all()`、`publicPool()`、`publicPool(Player)`、`publicPool(Player, AstralEventTrigger)`、`dispatchableFor(Player, AstralEvent, AstralEventTrigger)`、`drawRandom(RandomSource, Player)`
**AstralEventDispatcher**：`trigger(Player, ResourceLocation, AstralEventTrigger)`、`trigger(Player, Player, ResourceLocation, AstralEventTrigger)`、`trigger(Player, ResourceLocation)`、`isLegacy(String)`
**EventChoiceManager**：`OPTION_COUNT=2`、`CHOICE_TIMEOUT_TICKS=300`；`start(ServerPlayer)`、`confirm(ServerPlayer,int,int)`、`exitWithoutChoice(ServerPlayer,int,String)`、`cancelFor(ServerPlayer,String)`、`onPlayerTick / onPlayerDeath / onPlayerLogout / onPlayerChangedDimension`
**PlayerEffectProtection**：`apply(LivingEntity, MobEffectInstance)`、`isDeclaredClearable(String)`、`onExternalRemoval(Player,String)`、`tick(Player)`、`removeNow(LivingEntity, MobEffect)`、`clear(Player)`
**CurioDeathKeepHandler**：`onPlayerDrops(LivingDropsEvent)`、`onPlayerClone(PlayerEvent.Clone)`
**InvestigationArbitration**：`teamDetectives(Player)`、`teamHasSecretDetective(Player)`、`canDispatchSecretOnly(Player)`、`pickHighestTier(Player)`、`alreadyUnderInvestigation(LivingEntity)`
**AstralEventSystem（新增那一行）**：`public static void applyEventBonus(Player triggerer, String eventId, java.util.List<Player> targets)` —— 体 = `if (isClientSide) return; applySignBuffs(targets); applyRinSignPassive(triggerer, eventId, targets);`（与 forge:34-38 逐字同形；**不得**简化成只调 `applyRinSignPassive`，否则丢大侦探 +3 星币，见 B2-EVENT-RECON R7）

## 四、待主 Agent 完成的接线清单（本批不能自己改 AstralDiceMod / ModNetwork）

**4.1 LoaderBus 登记（3 行，缺一行就是静默失效）** —— 加在 `AstralDiceMod.registerListeners`：
```java
LoaderBus.INSTANCE.register(EventEffects.class);           // 缺 ⇒ 弹卡后永不结算（drain 不跑）
LoaderBus.INSTANCE.register(EventChoiceManager.class);     // 缺 ⇒ 15s 倒计时不推进、死亡/登出/换维度不强制结算
LoaderBus.INSTANCE.register(CurioDeathKeepHandler.class);  // 缺 ⇒ 饰品死亡照常掉落、重生不放回
```
（`PlayerEffectProtection` / `AstralEventRegistry` / `AstralEventDispatcher` / `InvestigationArbitration` / `AstralEventSystem` 无 `@SubscribeEvent`，**不需要**登记。）

**4.2 需要注册的网络消息（本批代码已按 forge 调用点写好，类由 B2-5 提供）** —— `network/ModNetwork.java` 的 `register()` 当前只到 id 19，建议接续：
| id | 消息 | 方向 | 本批消费点 |
|---|---|---|---|
| 20 | CardChoiceConfirmMessage | C→S | 既有 |
| 21 | CardChoiceCloseMessage | C→S | 既有 |
| 22 | EventCardMessage | S→C | `EventCardService:showThenRun` |
| 23 | EventChoiceStartMessage | S→C | `EventChoiceManager:start` |
| 24 | EventChoiceConfirmMessage | C→S | `EventChoiceManager:confirm` |
| 25 | EventChoiceCloseMessage | S→C | `EventChoiceManager:exitWithoutChoice / forceFirst` |
| 26 | EventChoiceTickMessage | S→C | `EventChoiceManager:tick` |
| 27 | EventChoiceCancelMessage | C→S | 客户端主动退出 |

**4.3 mixin 桥接点（1 处）**：`Mob#finalizeSpawn(ServerLevelAccessor, DifficultyInstance, MobSpawnType, SpawnGroupData, CompoundTag)` 的 **TAIL**，条件 `spawnType == NATURAL` 且未取消（`isSpawnCancelled()`），然后回调 `EventEffects` 里被注释的那段逻辑或 post 自建事件。落点见 `EventEffects.java:359-397` 的 TODO-BRIDGE。补齐后「雷暴将至」夜间刷怪 ×2 才生效。
**4.4 建议由 target/ 批次补的重载**：`TargetSelectionManager.cancel(ServerPlayer player, String reason)`（强制取消 + 发 `TargetSelectCloseMessage`），补好后把 `EventCardService` 里的 `cancelSessionForTests(sp)` 换回去（该行带 TODO-BRIDGE）。

## 五、阻塞项（不是本批能解决的，且都不是本批产物引入）

1. **B2-5 网络消息缺失**：`network/` 下 `EventCardMessage`、`EventChoiceStartMessage`、`EventChoiceCloseMessage`、`EventChoiceTickMessage`（以及 `Confirm`/`Cancel`）**全仓 fabric 零命中** ⇒ `EventCardService` 与 `EventChoiceManager` 现在**编不过**（硬错，非静默）。本批所有本模组 import 已逐个核对，未解析项只有这几条 + starengine 库类（库类不在本源码树，属正常外部依赖）。
2. **B2-7 音效层缺失**：`audio/ModSounds.java` 现有 15 条非事件音效，`EVENT_FADE_IN / EVENT_HEAL / EVENT_DAMAGE / EVENT_POISONING / EVENT_TRANSPORT / EVENT_BONUS` 全缺，`sounds.json` 与 `.ogg` 也没有 ⇒ `EventCardService.playTriggerSounds` 编不过。本批**照 forge 调用点原样写**，常量补齐即生效。
3. **线级根因（与本批无关）**：`item/sign/LinglingSignManager` 尚不存在（另一批）。
4. **`InvestigationArbitration` 原本在 fabric 完全不存在**，但本批 `AstralEventRegistry:84,117`、`AstralEventDispatcher:86`、`EventChoiceManager:261` 都要用它 ⇒ 作为第 8 文件补移植。**请确认无其它批次重复认领**（否则会出现两份同名类）。

## 六、需裁决项

1. `EventEffects.MOB_ARMOR_PER_DEFENSE = 2.0D` 目前是本类私有字面量；建议 combat/ 批次在 `DiceCombatModifiers` 补 `public static final double MOB_ARMOR_PER_DEFENSE` 后改为引用（消除双源风险）。
2. `CurioDeathKeepHandler.allEquipped` 建议下沉为 `CurioSlotUtil.allEquipped(LivingEntity)`（本批为避跨文件冲突未动 `item/CurioSlotUtil.java`）。
3. `TargetSelectionManager.cancel(ServerPlayer, String)` 是否需要（影响「动画结束时客户端会不会仍停在选择模式」）。
4. 「雷暴将至 ×2」的 mixin 桥接由谁做（本批已把注入点与条件写死在 TODO-BRIDGE 里）。

## 七、验收证据（可复跑）

- difflib：30 个 hunk，逐条在第二节给因；`AstralEventRegistry` 0 hunk。
- 无 forge/curios 残留（除注释）：`grep -rn 'net\.minecraftforge|top\.theillusivec4' event/` 仅命中 6 处，全部是注释文字或已被 `/* */` 注释掉的整段。
- 本模组 import 全部可解析（除 4.2 的 4 条消息类 + starengine 外部库类）。
- 事件派发点已实证存在：`LivingDropsEvent`(PuzzlesBridges:117)、`PlayerEvent.Clone`(PuzzlesBridges:198)、`LivingHealEvent`(LivingHealBridgeMixin:30)、`PlayerLoggedOutEvent`(FabricBridges:151)、`PlayerChangedDimensionEvent`(ServerPlayerDimensionTravelBridgeMixin:68)。
- **未运行 gradle**（遵工单）；因此「编译通过」不在本报告主张范围内，本报告只主张「源码级逐处与蓝本对齐 + 缺失符号全部归因于其它批次」。
