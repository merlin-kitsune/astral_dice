# B2-EVENT-RECON：事件体系（17 类缺口）移植前取证

批次：B2-EVENT-RECON（只读取证，不落源码）。基线 `forge-1.20.1`（必要时对照 `neoforge-1.21.1` 同路径），目标 `fabric-1.20.1`（MC 1.20.1 + FAPI 0.92.12 + Loom，Mojang 映射，Java 17）。
仓库：`D:/MCProject/astral_dice_multiloader`。本报告未运行 gradle、未启动游戏、未修改任何源码。

> **快照口径**：取证时点 `2026-11-01 18:0x +08:00`。期间 `fabric-1.20.1` 工作树**正被其它批次并发写入**
> （消失/新增的痕迹：`choice/CardChoiceManager.java`、`item/InvestigationEventUtil.java`、`event/BarricadeAggroHandler.java` 等）。
> 本报告结论只在上述时点成立；主 Agent 落线前请以 `git status` 实读复核。

## §1 类清单与依赖拓扑

阶段口径：**A** = 纯数据/契约（无平台差异，可整文件搬）；**B** = 纯服务端逻辑（无平台 import，但要登记/接线）；
**C** = 必须按垫片改写（事件/tick/网络/注册表）；**D** = 客户端（要挂 HUD/Screen 注册）。
「平台差异数」= 需要改动/新增的平台接点条数（import 替换、垫片调用、显式登记各计 1）。

| 类（`event/` 等包内） | 行数 | 阶段 | 依赖（建边证据：文件内 import） | 平台差异数 |
|---|---|---|---|---|
| `event/api/AstralEvent` | 54 | A | `AstralEventSystem.EVENT_TARGET_RADIUS`（:23 注释 + :171 调用方） | 0 |
| `event/api/AstralEventEffect` | 31 | A | 仅 vanilla `ServerLevel`/`Player` | 0 |
| `event/api/AstralEventPolarity` | 26 | A | `ChatFormatting` | 0 |
| `event/api/AstralEventTier` | 44 | A | 无 import（纯枚举 + `of(int)` 钳制） | 0 |
| `event/api/AstralEventTrigger` | 20 | A | 无 import | 0 |
| `event/AstralEventIds` | 39 | A | `ResourceLocation` | 0 |
| `event/EventTimerStore` | 158 | A | 仅 vanilla（静态 `HashMap`/`UUID`） | 0 |
| `event/EventAnimationLock` | 109 | C | vanilla + `ServerPlayer` | 4（类注解、死亡、登出、`ServerStoppingEvent`） |
| `event/InvestigationArbitration` | 115 | B | `ModAttachments`、`ModItems`、`PartyRelations`、`starengine.item.CuriosCompat` | 1（Curios 垫片名核对） |
| `event/PlayerEffectProtection` | 205 | C | `starengine.event.EffectTimerGuard`/`ModEffectRemoval`、`ForgeRegistries.MOB_EFFECTS`（:178、:203） | 2（改本线 `EffectTimerGuard`、注册表工厂） |
| `event/AstralEventRegistry` | 223 | B | `api/*` 四个、`EventEffects::*` 方法引用、`InvestigationArbitration`、`AstralEventSystem` | 0 |
| `event/AstralEventDispatcher` | 166 | B | `api/*`、`ModAttachments`、`PartyRelations`、`EventCardService`、`EventEffects`、`EventTimerStore`、`GlobalEventGate`、`InvestigationArbitration`、`AstralEventSystem` | 1（`applyEventBonus` 本线缺，见 §2-18） |
| `event/EventCardService` | 131 | C | `api/AstralEvent`、`ModSounds`、`SoundPlayback`、`ModNetwork`、`PacketDistributor`、`EventAnimationLock`、`EventChoiceManager`、`TargetSelectionManager` | 3（发包、声音登记、两处服务端取消） |
| `event/GlobalEventGate` | 156 | C | `ModNetwork.ActionBarMessage`、`GameplayConstants`、`EventCardService.DELAY_TICKS` | 2（类注解登记、`ServerStoppingEvent`） |
| `event/EventChoiceManager` | 337 | C | `api/*`、`ModNetwork`、`EventChoiceStart/Tick/CloseMessage`、`GameplayConstants`、`AstralEventRegistry`、`InvestigationArbitration`、`EventAnimationLock`、`AstralEventDispatcher` | 6（类注解、tick、死亡、登出、换维度、3 个消息类） |
| `event/EventEffects` | 617 | C | `api/*`、`AstralEventIds`、`ModAttachments`、`DiceCombatModifiers`、`DamageNumberAggregator`、`ModDamageTypes`、`ModItems`、`RandomCardHandler`、`VitaminPillChipItem`、`EventTimerStore`、`PlayerEffectProtection`、`EventCardService`、`AstralEventSystem` | 4（类注解登记、`MobSpawnEvent.FinalizeSpawn`、`LivingHealEvent`、tick） |
| `event/CurioDeathKeepHandler` | 86 | C | `CurioSlotUtil`、vanilla | 3（类注解、`LivingDropsEvent`、`PlayerEvent.Clone`） |
| `choice/CardChoiceType` | 49 | A | `ModItems`（`isCombatCard`/`isEffectCard`/`isCardItem`） | 0（**fabric 已有 68 行等价版**） |
| `choice/CardChoiceManager` | 257 | C | `CardChoiceType`、`screen/CardChoiceMenu`、`ModNetwork`、`CardChoiceCloseMessage` | 3（`NetworkHooks.openScreen`→`ExtendedScreenHandlerFactory`、类注解→登记、死亡/登出） |
| `block/EventDrawBlock` | 136 | — | `ModSounds`、`SoundPlayback`、`ModItems`、`ModNetwork`、`ModAttachments` | **已由另一批落地**，接口核对通过（见 §6 末） |
| `item/EventDrawItem` | 73 | B | `AstralEventDispatcher`、`AstralEventRegistry`、`EventAnimationLock`、`EventCardService`、`ModNetwork`、`GameplayConstants` | 0 import；需 1 条物品注册 |
| `client/EventCardOverlay` | 266 | D | `AstralEventRegistry`、`EventCardService`、`api/AstralEvent`、`GuiGraphics`、**`net.minecraftforge.client.gui.overlay.IGuiOverlay`（:12）** | 3（`IGuiOverlay` 垫片、HUD 注册点、`ResourceLocation` 工厂） |
| `client/EventChoiceClientHandler` | 63 | D | `Minecraft`、`EventChoiceScreen` | 1（dist 判定替换） |
| `client/EventChoiceScreen` | 361 | D | `GuiGraphics`、`Screen`、`ModNetwork`、`EventChoiceConfirm/CancelMessage`、`EventCardOverlay`、`GLFW` | 3（`ResourceLocation` 工厂、发包、`drawString` int 坐标已符合 1.20.1） |

**依赖拓扑（必须先有的在左）**：

```
A 层（纯数据，可先落线不接线）：api/AstralEventTrigger → api/AstralEventPolarity → api/AstralEventTier
                            → api/AstralEventEffect → api/AstralEvent → AstralEventIds → EventTimerStore
B 层：A 层 → InvestigationArbitration → AstralEventSystem.applyEventBonus（本线缺，须先补）
        → EventEffects → AstralEventRegistry → AstralEventDispatcher
        ModNetwork(ActionBarMessage) → GlobalEventGate ； EventAnimationLock（独立）
        → EventCardService（用 GlobalEventGate.DELAY_TICKS 处的常量侧反依赖：Gate→CardService）
        → EventChoiceManager → 三个 EventChoice*Message → client/EventChoice*
C 层并行支：CurioDeathKeepHandler（只依赖 CurioSlotUtil）
            choice/CardChoiceType → CardChoiceManager → CardChoiceCloseMessage（fabric 已有）
            item/EventDrawItem（依赖 Dispatcher + Registry + AnimationLock + CardService）
```

**「必须同时落线否则编译不过」的强连通块**（互相引用，缺一个即整块红）：

1. `AstralEventRegistry ↔ EventEffects`（`:173-184` 的方法引用）+ `EventEffects → PlayerEffectProtection` + `→ EventTimerStore`。
2. `AstralEventDispatcher ↔ EventCardService`（Dispatcher:125 调 `showThenRun`；CardService:74 调 `EventChoiceManager.cancelFor`）+ `EventCardService ↔ EventChoiceManager`（:130 调 `EventTimerStore.start`、:73 调 `EventAnimationLock.begin`）。
3. `EventCardOverlay ↔ EventChoiceScreen`（Screen:150 用 `EventCardOverlay.FRAME_W/FRAME_H`，:186 调 `drawCardAt`）。
4. `client/EventChoiceScreen ↔ network/EventChoiceConfirm|CancelMessage ↔ event/EventChoiceManager`。

## §2 事件映射表（forge 事件与注解 → fabric 落点）

| # | forge 位置（文件:行 + 摘录） | fabric 落点 |
|---|---|---|
| 1 | `EventChoiceManager.java:51` `@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)` + `:21` `import net.minecraftforge.fml.common.Mod` | **注解无效**：改 `LoaderBus.INSTANCE.register(EventChoiceManager.class)`，加在 `AstralDiceMod.registerListeners()`（本线范式见 `AstralDiceMod.java:141-198`，共 56 处） |
| 2 | `EventChoiceManager.java:297-302` `@SubscribeEvent onPlayerTick(TickEvent.PlayerTickEvent)`，`:300` `if (event.phase != TickEvent.Phase.END) return;` | `platform/event/TickEvent.java:97-105` 已有同形 `PlayerTickEvent(Phase, Player)`；`platform/FabricBridges.java:134` 只派发 `Phase.END` ⇒ 判据保留（恒真）以防将来改桥 |
| 3 | `EventChoiceManager.java:305-311` `onPlayerDeath(LivingDeathEvent)` | `platform/event/entity/living/LivingDeathEvent`；派发点 `platform/PuzzlesBridges.java:108-113`（`LivingDeathCallback.EVENT`） |
| 4 | `EventChoiceManager.java:314-319` `onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent)`，`:316` `event.getEntity().getUUID()` | `platform/event/entity/player/PlayerEvent.java:48` `getEntity()` 返回 **`Player`**（同 forge 1.20.1）；派发 `FabricBridges.java:143` |
| 5 | `EventChoiceManager.java:330-336` `onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent)` | `PlayerEvent.java:488-491` 已有该内部类；派发 `mixin/bridge/ServerPlayerDimensionTravelBridgeMixin.java:68`（RETURN 处 `LoaderBus.INSTANCE.post`） |
| 6 | `GlobalEventGate.java:9-11,152-155` `@SubscribeEvent onServerStopping(ServerStoppingEvent)` → `reset()` | **fabric 无 `ServerStoppingEvent` 垫片**（`grep -rn ServerStoppingEvent fabric-1.20.1/src/main/java` 零命中）⇒ 直接挂 FAPI：`ServerLifecycleEvents.SERVER_STOPPING.register(...)`（先例 `FabricBridges.java:83,85`）；**不可**用 `LoaderBus` 注册（事件类不存在，编译不过） |
| 7 | `EventAnimationLock.java:94-107` 三个 `@SubscribeEvent`（登出 / `LivingDeathEvent` / `ServerStoppingEvent`） | 同 #3/#4/#6；类注解同 #1（`LoaderBus` 登记） |
| 8 | `CurioDeathKeepHandler.java:40-41` `@SubscribeEvent(priority = EventPriority.LOWEST) onPlayerDrops(LivingDropsEvent)` | `platform/event/entity/living/LivingDropsEvent.java:58` `getDrops()` 返回 `Collection<ItemEntity>`（`it.remove()`/`drop.setCount` 语义同样可用）；派发 `PuzzlesBridges.java:116-120` |
| 9 | `CurioDeathKeepHandler.java:73-78` `onPlayerClone(PlayerEvent.Clone)`，`:75` `event.isWasDeath()`，`:76` `event.getEntity()` 已是 `Player` | `PlayerEvent.java:215-239` 有 `Clone`（`isWasDeath()`/`getOriginal()`）；派发 `PuzzlesBridges.java:197-198`（`PlayerEvents.COPY`） |
| 10 | `CardChoiceManager.java:225-245` `onPlayerDeath(LivingDeathEvent)`、`:248-256` `onPlayerLogout` | 同 #3/#4。⚠️ fabric 侧 `choice/CardChoiceManager.java`（302 行，并发落地）已改为 `ChoiceMenuFactory`，钩子需按本线版核对，不要照抄 forge |
| 11 | `EventEffects.java:355-356` `@net.minecraftforge.eventbus.api.SubscribeEvent onFinalizeSpawn(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn)` | **fabric 无 `MobSpawnEvent` 垫片**（`grep -rn 'MobSpawnEvent\|FinalizeSpawn' fabric-1.20.1/src/main/java` 零命中）⇒ 必须新写 mixin（`Mob#finalizeSpawn` HEAD，post 自建事件）或本批放弃「雷暴将至夜间 ×2」 |
| 12 | `EventEffects.java:408-415` `onPlayerTick(TickEvent.PlayerTickEvent)`（`:410` `phase != END` 返回；`:413` `EventCardService.drain`） | 同 #2。⚠️ 这是**唯一**驱动卡动画到期结算的钩子，漏登记 = 弹卡后永不结算 |
| 13 | `EventEffects.java:426-431` `onHeal(LivingHealEvent)`，`:429` `event.setCanceled(true)` | `platform/event/entity/living/LivingHealEvent`；派发 `mixin/bridge/LivingHealBridgeMixin.java:30` |
| 14 | `EventEffects.java:52` 类注解写成**全限定** `@net.minecraftforge.fml.common.Mod.EventBusSubscriber(...)`（普通 grep `@Mod.EventBusSubscriber` 会漏） | 同 #1：`LoaderBus.INSTANCE.register(EventEffects.class)`。**这是最容易漏登记的一类**（注解形态与其它类不同） |
| 15 | `EventCardService.java:11` `import net.minecraftforge.network.PacketDistributor;` + `:65-67` `ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new EventCardMessage(...))` | `ModNetwork.sendToPlayer(sp, new EventCardMessage(event.id(), event.defaultDurationTicks(), tier))`（`ModNetwork.java:96-98`）；`PacketDistributor` 垫片 `platform/network/PacketDistributor.java:90` 也可直用 |
| 16 | `PlayerEffectProtection.java:178` / `:203` `net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(effect).toString()` | `net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect)`（本线先例 `event/EffectTimerGuard.java:83,110`）；另把 `starengine.event.EffectTimerGuard`/`ModEffectRemoval` 换成**本线同名类** `event/EffectTimerGuard`（`apply`/`INFINITE_THRESHOLD` 同名）与 `starengine...ModEffectRemoval`（`ModEffectEvents.java:12,18` 已在用） |
| 17 | `EventChoiceStartMessage.java:59` `if (FMLEnvironment.dist == Dist.CLIENT)` | `platform/api/distmarker/Dist` + `platform/fml/loading/FMLEnvironment`（逐字范式：`network/CardChoiceCloseMessage.java:3-4,40`） |
| 18 | `AstralEventDispatcher.java:137` `AstralEventSystem.applyEventBonus(bonusFinal, event.id().toString(), targetsFinal)` | **本线 `AstralEventSystem` 缺该 public 方法**（`grep -n 'public static'` 只有 `onEventTriggered`/`triggerInvestigationEvent`/`applyRinSignPassive`×3）。需补 3 行包装体：`applySignBuffs(targets); applyRinSignPassive(triggerer, eventId, targets);`（`private applySignBuffs` 已在 `AstralEventSystem.java:44`） |
| 19 | `CardChoiceManager.java:109-116` `NetworkHooks.openScreen(player, new SimpleMenuProvider(...), buf -> {...})` | **fabric 已落地**（`choice/CardChoiceManager.java:94` `ChoiceMenuFactory implements ExtendedScreenHandlerFactory`），本批只需补网络消息登记，不要重写 |
| 20 | `EventCardOverlay.java:12` `import net.minecraftforge.client.gui.overlay.IGuiOverlay;` + `:80` `public static final IGuiOverlay OVERLAY = (gui, g, partialTick, width, height) -> render(g);` | `platform/client/gui/overlay/IGuiOverlay.java:13-15`（签名一致：`render(ForgeGui, GuiGraphics, float, int, int)`）；注册走 `client/ModClientEvents.java:39-55` 的 `registerGuiOverlays(RegisterGuiOverlaysEvent)` |

## §3 持久化键表

**结论先说**：事件体系的 5 个状态容器**全部是进程内静态字段，不落盘、不 sync、不 copyOnDeath**；
真正进存档的只有 4 个 `AttachedDataKey`，其中 3 个属事件体系外延（抽奖盒/商店）。

| 键名 / 容器 | 类型 | 归属类:行 | sync | copyOnDeath | 备注 |
|---|---|---|---|---|---|
| `EXPIRY`（`Map<UUID, Map<ResourceLocation, Long>>`） | 内存 | `EventTimerStore.java:33` | — | — | 服务端静态；类注释 `:20-24` 自述「登出后条目滞留、`clear` 全仓无调用点」 |
| `ON_EXPIRE`（`Map<UUID, Map<ResourceLocation, Consumer<Player>>>`） | 内存 | `EventTimerStore.java:36` | — | — | 到期回调；`start` 时**先回放旧回调**（:64-77） |
| `eventEndsAtGameTime` / `gateUntilGameTime`（`long`） | 内存 | `GlobalEventGate.java:47,50` | — | — | 只在 `reset()`（:116）清；`ServerStoppingEvent` 是唯一清理点 |
| `LAST_NOTICE_AT`（`Map<UUID, Long>`） | 内存 | `GlobalEventGate.java:53` | — | — | 提示节流表 |
| `ANIM_UNTIL`（`ConcurrentHashMap<UUID, Long>`） | 内存 | `EventAnimationLock.java:44` | — | — | 到期即删（:71-74），无外部清理依赖 |
| `SESSIONS`（`ConcurrentHashMap<UUID, Session>`） | 内存 | `EventChoiceManager.java:63` | — | — | 会话含 `token/firstEvent/secondEvent/expireTick/resolving/syncedSecond`（:69-95） |
| `SESSIONS`（同上） | 内存 | `CardChoiceManager.java:55` | — | — | 选卡会话（另一批已落 fabric） |
| `STASH`（`ConcurrentHashMap<UUID, List<ItemStack>>`） | 内存 | `CurioDeathKeepHandler.java:38` | — | — | 死亡→重生暂存，登出即丢（类注释 :30） |
| `investigation_stage` | `AttachedDataKey<Integer>`，`Codec.INT`，默认 `1` | `ModAttachments.java:327-328`（forge）/ `:323-324`（fabric，已存在） | 否 | 否 | 写入钳制 `Math.max(1, value)`（:419-421 两线同）|
| `shop_promotion_until` | `AttachedDataKey<Long>`，`Codec.LONG`，默认 `0L` | forge `ModAttachments.java:532-533` / fabric `:1763-1764`（已存在） | 否 | 否 | 商店大酬宾用 `now + 6000`（`EventEffects.java:438`） |
| `event_drawer_cooldown_until` | `AttachedDataKey<Long>`，`Codec.LONG`，默认 `0L` | forge `ModAttachments.java:102` | **是**（`.sync()`） | 否 | **fabric 缺**（`grep -n EventDrawer fabric/.../ModAttachments.java` 零命中） |
| `event_drawer_blank_insert_at` | 同上 | forge `ModAttachments.java:117` | 否 | 否 | **fabric 缺** |

`sync()`/`copyOnDeath()` 在本线的落点：`component/AttachedDataKey.java:178`（`sync`）、`:196`（`copyOnDeath`）；
注意本线 `sync()` **仅登记 S2C 同步资格**，真正下发仍走 `ModNetwork#syncAttachment/syncSnapshot`（该类头 :36 明写）。

## §4 网络消息表 + 建议 id 段

forge 侧事件相关消息（协议 = `SimpleChannel#registerMessage(id++, …)`，**顺序即 id**）：

| forge id | 类 | 方向 | 字段（encode 顺序） | fabric 现状 |
|---|---|---|---|---|
| 16 | `EventCardMessage` | S→C | `ResourceLocation eventId`、`VarInt durationTicks`、`VarInt tier` | 缺 |
| 17 | `EventChoiceStartMessage` | S→C | `VarInt token`、`RL firstEvent`、`RL secondEvent`、`VarInt firstDuration`、`VarInt secondDuration`、`VarInt timeoutTicks` | 缺 |
| 18 | `EventChoiceConfirmMessage` | C→S | `VarInt token`、`VarInt optionIndex` | 缺 |
| 19 | `EventChoiceCloseMessage` | S→C | `VarInt token` | 缺 |
| 20 | `EventChoiceTickMessage` | S→C | `VarInt token`、`VarInt remainingTicks` | 缺 |
| 27 | `EventChoiceCancelMessage` | C→S | `VarInt token`（`handle` 内固定 reason `"esc"`，:47） | 缺 |
| 24/25 | `CardChoiceConfirmMessage` / `CardChoiceCloseMessage` | C→S / S→C | `VarInt token` | **类已在、未登记**（见风险 §8-R4） |

fabric `network/ModNetwork.java` 现有注册顺序（`register()` :41-92，实读 **20 条**）：

```
0 DamageNumber 1 ActionBar 2 SignActivate 3 OpenCardInventory 4 AttachmentSync
5 TargetSelectStart 6 TargetSelectConfirm 7 TargetSelectCancel 8 EnderDieTotem
9 StarCoinWallet 10 StarCoinBalance 11 RenShieldState
12 ChipShopBuy 13 ChipShopRefresh 14 ChipShopDeny 15 BeamFx
16 CardShopTransferList 17 CardShopTransfer 18 CardShopBuy 19 CardShopDeny
```

⇒ **当前下一个可用 id = 20**。但 20/21 已被 B1-SCREEN-CHOICE 报告声明占用（`CardChoiceConfirm` :19 写「本线 id 16」、`CardChoiceClose` :21 写「本线 id 17」，B1 报告 §0 则写 20/21 —— 三处互不一致），只是**尚未写进 `register()`**。

**建议 id 段（先补 20/21，再追加事件段 22-27，严格追加、不插队）**：

```
20 CardChoiceConfirmMessage（C→S，补登记）
21 CardChoiceCloseMessage  （S→C，补登记）
22 EventCardMessage            （S→C）
23 EventChoiceStartMessage      （S→C）
24 EventChoiceConfirmMessage    （C→S）
25 EventChoiceCloseMessage      （S→C）
26 EventChoiceTickMessage       （S→C）
27 EventChoiceCancelMessage     （C→S）
```

纪律依据：`ModNetwork.java:70` 注释「四条一律追加在末尾，不得插进既有 payload 的顺序里（顺序即协议，改序会让旧客户端错包）」；
线格式 `[VarInt messageId][payload]` 见 `platform/network/SimpleChannel.java:26-31`。
**与 forge 编号不要求一一对应**（forge 事件段是 16-20 + 27，fabric 已有不同前缀），只要求本线内部单调追加且相对顺序与 forge 一致。

## §5 时序约束与错误顺序的后果

### 5.1 卡动画 → 结算：**38t，不是 39t**

`docs/compat-1.5.0-porting.md` 与本任务描述里都没有独立证据支持「39t」；代码里唯一来源是：

```java
// event/EventCardService.java:34-38
public static final int DELAY_TICKS = 38;
public static final int CARD_ANIM_TICKS = 28;
public static final int SETTLE_BUFFER_TICKS = DELAY_TICKS - CARD_ANIM_TICKS;
```

客户端同源推导（**禁止写死毫秒**）：`client/EventCardOverlay.java:45` `ANIM_MS = EventCardService.CARD_ANIM_TICKS * 50L`、`:55` `TOTAL_MS = EventCardService.DELAY_TICKS * 50L`（= 1900ms）。
另：`QUESTION_COOLDOWN_TICKS = DELAY_TICKS + 20 * 10`（:45，= 238t，抽卡物冷却）。

**写错时长的后果（推断，基于代码语义）**：若把客户端 `TOTAL_MS` 或服务端 `DELAY_TICKS` 各自改成不同值，
`EventAnimationLock.begin(sp, DELAY_TICKS)`（:73）起锁与 `QUEUE` 结算刻（:81）将不再同源，
出现「锁已放行但事件未结算」或「卡还在屏上、效果已生效」——正是 `EventAnimationLock` 类头（:29-31）明确要消除的那类漂移。

### 5.2 `EventChoiceManager` 与 `GlobalEventGate` 的顺序**已不是文档写的那个约束**

文档原文（`docs/compat-1.5.0-porting.md:27`）：

> `EventChoiceManager` 的顺序不可颠倒：**先判硬间隔 → 再 `remove(session)`**，反了会把玩家会话误删成「无会话」而被放行。

**现行代码与该条目冲突**（取证：`EventChoiceManager.java:150-154`）：

```java
// 2026-10-29 用户裁决 D1：此处**不再**查询全局事件闸门（原 canStart / notifyBlocked 预判已删除）。
```

`forceFirst(...)` 处同口径（:228 `// 强制结算（超时/死亡/登出）同样不再看全局闸门（口径同 confirm）`）。
闸门现在**只拦 `ITEM_USE`**：`AstralEventDispatcher.java:113-118`
`if (source == AstralEventTrigger.ITEM_USE && triggerer instanceof ServerPlayer gatePlayer && !GlobalEventGate.tryAcquire(...))`；
`GlobalEventGate.canStart` 的 javadoc（:58-76）仍写着「EventChoiceManager 必须先确认闸门放行、再移除会话」——**注释级残留，不要当实现**。

若按文档「先判后删」重写，会踩两个已登记坑：
① **双占位**：`canStart` 只读、`tryAcquire` 写占位；预判放行后再由派发器 `tryAcquire` 等于把窗口占两次（`GlobalEventGate.java:59-66` javadoc 明写「若直接用 tryAcquire 预判，一旦放行就会写掉一次占位（双获取）」）。
② **界面已选却被静默拒绝**：`confirm` 若在删会话前被闸门拒掉，玩家看到「点了确认毫无反应」且会话仍存活 15 秒——这正是 2026-10-29 裁决删除该分支的理由（:151-154）。

### 5.3 真正不可颠倒的**现役**顺序（这些必须逐字保留）

| 位置 | 正确顺序 | 顺序写反的后果 |
|---|---|---|
| `EventChoiceManager.java:221-231` `forceFirst` | **先** `!（player instanceof ServerPlayer）return`，**再** `SESSIONS.remove(uuid, session)` | 注释记为 2026-10-29 P2 修复：反了会在类型判断处早退时「会话已消失、`resolving` 已置 0」⇒ **本次强制结算永久丢失且不可重试**；`PlayerLoggedOutEvent#getEntity()` 在 1.20.1 返回 `Player` 而非 `ServerPlayer`，登出路径正好命中这一处 |
| `EventTimerStore.java:58-80` `start` | **先无条件回放旧回调**，再 `put(EXPIRY)`/登记新回调 | 注释 :61-63：若挪进 `if (onExpire != null)`，派发器「先登记计时器、后 apply 效果」（`AstralEventDispatcher.java:128-134`）时会把**新生效的状态刚写上就被旧回调撤掉** |
| `EventCardService.java:86-99` `drain` | 快照迭代 + **先 `QUEUE.remove(pending)` 再执行** | 注释 :89-90 记录实测：直接 `removeIf` 里执行会 `ConcurrentModificationException` ⇒「治疗已生效、1 秒后的伤害永远不执行」 |
| `EventCardService.java:73-78` `showThenRun` | 弹卡同一 tick 内 `begin` → `cancelFor` → `TargetSelectionManager.cancel` | 注释 :69-72：晚一个 tick 就可能「旧界面还开着、新事件已在演」，确认动作抢在动画结算前触发第二条事件 |
| `EventChoiceManager.java:252-256` `firstDispatchable` | 首选项不可派发时改派同屏第二项 | 否则 15 秒窗口内侦探卸牌 ⇒ 结算按 `AP_EVENT_NO_DETECTIVE` 白丢（:233-237） |

## §6 客户端接线清单

| 客户端件 | 依赖的 1.20.1 API | fabric 对应垫片 / 注册点 | 现状 |
|---|---|---|---|
| `client/EventCardOverlay` | `GuiGraphics`（`blit`/`drawString`/`pose`/`flush`/`setColor`）、`Minecraft.font`、`Mth`、`RenderSystem.setShaderColor`、`NativeImage.read`、`IGuiOverlay` | 垫片 `platform/client/gui/overlay/IGuiOverlay.java:13-15`（同名同签名）；HUD 渲染由 `AstralDiceClient` 逐帧回调（`IGuiOverlay` 类头 :7-11） | 类缺失；`IGuiOverlay` 垫片**已就绪** |
| HUD 注册点 | forge `RegisterGuiOverlaysEvent#registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "event_card", OVERLAY)`（`forge client/ModClientEvents.java:48`） | `client/ModClientEvents.java:39-55` `registerGuiOverlays(...)`（已有 6 条），锚点枚举 `platform/client/gui/overlay/VanillaGuiOverlay.java:15-19` | 只需加 1 行 `event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "event_card", EventCardOverlay.OVERLAY);`；⚠️ 本线锚点**只作记录**，实际顺序=注册顺序（`VanillaGuiOverlay` 类头 :10-14） |
| `client/EventChoiceClientHandler` | `Minecraft.getInstance().setScreen(...)`、`mc.screen instanceof ...` | 无垫片需求；`FMLEnvironment.dist` 判定改 `platform/api/distmarker/Dist` + `platform/fml/loading/FMLEnvironment`（`network/CardChoiceCloseMessage.java:3-4,40` 逐字范式） | 类缺失；先例已有（`client/CardChoiceClientHandler.java`） |
| `client/EventChoiceScreen` | `Screen`（`render`/`mouseClicked`/`mouseReleased`/`keyPressed`/`keyReleased`/`tick`/`shouldCloseOnEsc`/`onClose`/`removed`/`isPauseScreen`）、`GuiGraphics.blit/drawString`（**int 坐标**，1.20.1 无 float 重载）、`GLFW.GLFW_KEY_ESCAPE`、`Player#displayClientMessage(Component, boolean)` | 无垫片需求（纯原版 API）；发送 `ModNetwork.sendToServer(msg)`（`ModNetwork.java:115-117`） | 类缺失；`screen/CardChoiceScreen`（591 行）是同类样板，可照其结构 |
| 键盘/输入 | 本界面不用 `KeyMapping`（ESC 走 `Screen#keyPressed`，`KeyBindingSetup` 不参与） | — | 无需改动 |
| `item/EventDrawItem` 物品注册 | `ModItems.EVENT_TICKET = registerItem("event_ticket", () -> new EventDrawItem(...))`（forge `item/ModItems.java:455-456`） | `item/ModItems.java`（本线注册器 `platform/registry/DeferredRegister`） | **fabric 缺 `EVENT_TICKET`**（`grep -n EVENT_TICKET` 零命中） |
| `block/EventDrawBlock`（已落地，只核接口） | `public EventDrawBlock(Properties)`、`public static int priceStarCoins()`、`public InteractionResult use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)` | 两线签名一致（fabric :60 / :65 / :70；forge :60 / :56 / :65） | **接口对得上**；但 fabric 版内部仍是 4 个 `TODO(B0-ATTACHMENTS)`/`TODO(B0-ITEMS)` 桩（:140-165），需 `ModItems.EVENT_TICKET` + `event_drawer_*` 两键补齐后才真生效 |

客户端与语言/素材前置（缺则编译不过或裸键名）：`ModSounds` 的 8 个 `event_*` 音效（forge `audio/ModSounds.java:105-112`）在 fabric **全缺**，
且 `assets/astral_dice/sounds/event_*.ogg` 与 `sounds.json` 条目本线同样没有（forge 侧各 8 个）。

## §7 建议施工拆批（每批 ≤6 文件、可独立编译）

| 批 | 文件 | 判定 |
|---|---|---|
| B2-1 契约层 | `event/api/AstralEventTrigger` `AstralEventPolarity` `AstralEventTier` `AstralEventEffect` `AstralEvent`（5） | 纯数据、无接线，可立即编译通过 |
| B2-2 常量与存储 | `event/AstralEventIds` `event/EventTimerStore` `event/EventAnimationLock`（3） | 需 `LoaderBus.register(EventAnimationLock.class)`（**但 3 个钩子里的 `ServerStoppingEvent` 要先解决 §2-6**，否则改为直挂 FAPI） |
| B2-3 效果前置 | `event/PlayerEffectProtection` + 补 `AstralEventSystem.applyEventBonus`（2） | 依赖本线 `EffectTimerGuard`；必须先做，否则 `EventEffects` 编译不过 |
| B2-4 效果与派发 | `event/EventEffects` `event/GlobalEventGate` `event/AstralEventRegistry` `event/AstralEventDispatcher`（4） | 强连通块；需 `RegisterGuiOverlaysEvent` 之外的 3 处 `LoaderBus` 登记 + `SERVER_STOPPING` 挂钩；`MobSpawnEvent` mixin 可后置（先注释/空实现会静默失效，建议同批给判据） |
| B2-5 会话与消息 | `event/EventChoiceManager` `network/EventCardMessage` `network/EventChoiceStartMessage` `network/EventChoiceConfirmMessage` `network/EventChoiceCloseMessage` `network/EventChoiceTickMessage`（6，含 `EventChoiceCancelMessage` 则 7） | 需先补 `CardChoiceConfirm/Close` 的 20/21 登记，再追加 22-27 |
| B2-6 客户端 | `client/EventCardOverlay` `client/EventChoiceClientHandler` `client/EventChoiceScreen`（3）+ `client/ModClientEvents` 加 1 行注册 | 需 `ResourceLocation` 工厂改写（§2 / §8-R5）；`EventChoiceScreen` 依赖 B2-5 的两个消息类 |
| B2-7 物品与音效 | `item/EventDrawItem` + `item/ModItems`（1 行 `EVENT_TICKET`）+ `audio/ModSounds`（8 行）+ `sounds.json` + 8 个 `.ogg` + `component/ModAttachments`（2 键）（5 文件 + 素材） | 与 `block/EventDrawBlock` 的 TODO 桩解耦；素材可直接从 forge 线复制 |
| B2-8 收尾 | `event/CurioDeathKeepHandler`（1）+ `AstralDiceMod` 登记表 | 依赖 `PlayerEvent.Clone` 桥（已就绪） |

顺序理由：B2-1/2/3 是「纯数据 + 无消费者」先行块，可独立编译；B2-4 是强连通块必须整块落；
B2-5 与 B2-6 有 `EventChoice*Message` 双向依赖，**建议同一次提交**（否则 `EventChoiceScreen` 编译不过）。

## §8 无法判定项（逐条注明缺什么证据）

| # | 项 | 缺什么 |
|---|---|---|
| U1 | 「39t」出处 | 未在 `docs/` 与代码中找到任何 39t 证据；`docs/compat-1.5.0-porting.md` 全文无该数字（`grep -n '39'` 无命中）。需用户给出出处，或确认以代码 38t 为准 |
| U2 | `docs/compat-1.5.0-porting.md:27` 与代码冲突谁为准 | 文档是 2026-10-22 登记、代码是 2026-10-29 D1 裁决；缺该裁决的原文记录（仅存类内注释）。本报告按「代码为准」取证，但**移植口径需用户确认** |
| U3 | `MobSpawnEvent.FinalizeSpawn` 的 fabric 落点 | 本线无垫片、无 mixin；是否本批实现「雷暴将至夜间 ×2」未定。若要实现，需指定注入点（`Mob#finalizeSpawn` HEAD/TAIL 还是 `LevelRenderer` 侧） |
| U4 | 8 个 `event_*.ogg` 的音量/字幕键规范 | 只核到 forge 侧文件存在与 `sounds.json` 条目（:26-39 段），未核 `subtitle` 字段与三语 lang 键；素材复制属另一批（B0 资源） |
| U5 | `neoforge-1.21.1` 与 `forge-1.20.1` 这 17 类是否逐字节同源 | 只对照了 `AstralEventSystem`（方法名差异：neo/forge 有 `applyEventBonus`，fabric 无）与 `CardChoiceType`/`EventDrawBlock`；其余类未做双 P0 线 diff。若需确认「两线同源」，需逐文件 `cmp` |
| U6 | fabric 侧 `CardChoiceManager` 的钩子细节 | 该文件在我取证时点**正在被并发写入**（302 行、`ChoiceMenuFactory`）；其 `onPlayerDeath/onPlayerLogout` 是否已接 `LoaderBus` 需在落线前实读 |
| U7 | `docs/compat-1.5.0-porting.md:27` 提到的「两处入口」 | 文档说 `AstralEventDispatcher` 有「两处入口」查闸门；现行代码只有 1 处（`:113`，`ITEM_USE`）。第二处是否指已被删除的 `EventChoiceManager` 分支，需用户确认 |
| U8 | 效果数值口径（攻击力 +5 / 防御力映射） | 本报告只核接线，未核 `EventEffects` 12 条效果的数值与 `DiceCombatModifiers` 折算是否正确（`docs/compat-1.5.0-porting.md` §4-4 另有 1:1 折算欠账） |

### 附：本次核对到的既有缺陷（供主 Agent 决定是否单独立项）

**R1 漏登记即静默失效**：forge 侧靠 `@Mod.EventBusSubscriber`（其中 `EventEffects:52` 是**全限定写法**）自动注册；fabric 无此机制，只能显式 `LoaderBus.INSTANCE.register(X.class)`（`AstralDiceMod.java` 现 56 处）。漏一处 = 该钩子永不派发，且**编译、启动日志全绿**。判别工具已现成：`platform/event/LoaderBus.java` 的 `dispatchReport()`（「已派发/未派发」计数）与 `SubscriptionAudit`（带注解但未登记的差集）。

**R2 `ServerStoppingEvent` 无垫片**：`GlobalEventGate.reset()` 与 `EventAnimationLock.reset()` 是静态时间戳的唯一清理点。不接 `ServerLifecycleEvents.SERVER_STOPPING` ⇒ 单人「退出存档 → 换存档」后 `gateUntilGameTime` 残留旧 `gameTime`，表现为**新存档里事件卡被永久锁住**（`isOpenAt` 恒 false）。

**R3 `MobSpawnEvent.FinalizeSpawn` 缺失**：「雷暴将至」夜间刷怪 ×2 整段静默失效（`EventEffects.java:355-375`），无报错、无日志（`AP_EVENT_EFFECT: storm_night_spawn_x2` 永不打印）。

**R4 协议已声明未登记**（**现网即存在**）：`network/CardChoiceConfirmMessage.java:19` 注释写「本线 id 16」、`CardChoiceCloseMessage.java:21` 写「本线 id 17」，B1 报告写 20/21；而 `ModNetwork.register()`（:41-92）**实际只到 19**。后果：`SimpleChannel.encode`（:109-120）`byClass.get()` 取不到 ⇒ `throw new IllegalArgumentException("unregistered message type")` —— 选卡界面的确认/关屏整条链断（服务端会话靠 `abandon` 才能清）。

**R5 `ResourceLocation.fromNamespaceAndPath` 在本目标线不存在**：实测 loom 产出的 `minecraft-merged-…-1.20.1-loom…jar` 的 `net/minecraft/resources/ResourceLocation.class` **不含该符号**（`tryBuild` 有、`fromNamespaceAndPath`/`withDefaultNamespace` 无）；本仓 fabric 侧自己的注释也写着「1.20.1 没有 fromNamespaceAndPath」（`client/BarricadeModel.java:38`）。而 forge 侧线的 `ResourceLocation` 被 Forge 补丁加回了该工厂（`forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar` 内该文件确有 `public static ResourceLocation fromNamespaceAndPath(String namespace, String path)`）。⇒ `EventCardOverlay.java:24,59,115,168` 与 `EventChoiceScreen.java:120` **必须改写为 `new ResourceLocation(ns, path)`**，照抄即编译失败；若「顺手」改成 `tryParse` 或 `withDefaultNamespace`，命名空间语义会静默改变。

**R6 音效层全缺**：`ModSounds`（forge `:105-112` 8 个 `event_*`）+ `sounds.json` + 8 个 `.ogg` 在 fabric 线 0 命中 ⇒ `EventCardService.playTriggerSounds`（:112-130）与 `:68` 的 `EVENT_FADE_IN` 无法编译（这是**硬错**，比静默失效好）；但若有人用 `try/catch` 包裹规避，弹卡变哑剧静默。

**R7 附加效果层名字漂移**：`AstralEventDispatcher:137` 调 `applyEventBonus`，本线只有 `applyRinSignPassive`。若直接改成后者，**丢掉 `applySignBuffs`**（大侦探立牌 +3 星币，`AstralEventSystem.java:44-72`）⇒ 事件照常结算、星币静默不发。

**R8 附件键未提前初始化**：`event_drawer_cooldown_until`（`.sync()`）/`event_drawer_blank_insert_at` 在 fabric `ModAttachments` 不存在，`block/EventDrawBlock` 现有桩返回 0 ⇒ 抽奖盒**冷却恒失效 / 免费连抽**，且 `AttachmentSyncMessage` 不会带该键（客户端无缓存，静默）。

**R9 客户端渲染器未注册**：`EventCardOverlay.OVERLAY` 不进 `RegisterGuiOverlaysEvent` ⇒ 服务端照常 38t 结算、客户端永不显示卡；且机器行只会出现服务端 `AP_EVENT_CARD: shown`，缺客户端 `AP_EVENT_CARD_CLIENT: show`（`EventCardOverlay.java:75`）——**这条机器行差异是唯一可断言的判据，务必保留两侧机器行**。

**R10 计时器登出泄漏**：`EventTimerStore` 类注释自述（:20-24）「登出后 UUID 条目滞留，`clear` 全仓无调用点」。移植时若只搬代码不接 `PlayerLoggedOutEvent` 清理，`EXPIRY`/`ON_EXPIRE` 跨会话残留 ⇒ 上一条命的效果/属性修饰符被带到下一条命甚至下一个存档（属性修饰符按 UUID 键，`EventEffects.java:81-83`）。

---

取证命令（可复跑，均只读）：

```bash
# 待移植面（forge 有、fabric 无）逐个确认
for c in AstralEventDispatcher AstralEventRegistry EventCardService EventChoiceManager \
         EventEffects EventTimerStore GlobalEventGate EventAnimationLock \
         PlayerEffectProtection InvestigationArbitration CurioDeathKeepHandler \
         EventCardOverlay EventChoiceScreen EventDrawItem; do
  echo "$c: $(grep -rl \"$c\" fabric-1.20.1/src/main/java 2>/dev/null | wc -l)"; done
# 垫片缺口
grep -rn 'ServerStoppingEvent' fabric-1.20.1/src/main/java   # 期望 0：须直挂 SERVER_STOPPING
grep -rn 'MobSpawnEvent'      fabric-1.20.1/src/main/java   # 期望 0：须新写 mixin
# 协议现状
grep -n 'registerMessage' fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java | wc -l
```
