# B2-2 报告：事件常量与存储层（3 个新文件 + 1 处平台层钩子）

批次 B2-2（事件体系移植第二块，紧跟 B2-1 契约层）。基线 `forge-1.20.1`（MC 1.20.1 + Forge 47.x，唯一蓝本）；目标 `fabric-1.20.1`（MC 1.20.1 + FAPI 0.92.12 + Loom，Mojang 映射，Java 17）。仓库 `F:/MCProject/astral_dice_multiloader`，分支 `multi-main`；取证时点 `2026-11-01 18:4x +08:00`。
本批**未跑 gradle、未启动游戏、未 commit**：整线当前编译失败，唯一根因仍是 `item/sign/LinglingSignManager` 缺失（另一批次在写，本批不碰、也不补）。设计依据 `porting/fabric-1.20.1/B2-EVENT-RECON.md` §1（A/C 阶段）、§2（映射 #6 #7）、§4、§8（R2 / R5 / R10）。

## 1 新增文件清单

目标目录前缀 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`，与源 `forge-1.20.1/...` 同路径、同包名（`package` 行未改）。行尾一律 CRLF（= 本线工作树现状，`.gitattributes` 未对 `*.java` 强制 eol，`core.autocrlf=true` 让 blob 存 LF、工作树 CRLF），无 BOM，文件尾有换行。

| # | 文件 | 行 | 字节 | sha256 前 12 | git 状态 |
|---|---|---|---|---|---|
| 1 | `event/EventTimerStore.java` | 158 | 7429 | `b1360a01c312` | `??` 新增 |
| 2 | `event/GlobalEventGate.java` | 173 | 10690 | `2a19e18fbcfb` | `??` 新增 |
| 3 | `event/EventAnimationLock.java` | 117 | 6605 | `69321a7c15ef` | `??` 新增 |
| 4 | `platform/FabricBridges.java` | 230（原 222） | 15156 | `5ae9093dc3c3` | ` M` 修改（+8 行） |

三者状态口径与蓝本一致：全部为**进程内静态字段**（`Map` / `ConcurrentHashMap` / `static long`），**不落盘、不 sync、不 copyOnDeath**；无 `SavedData`、无 `CompoundTag`、无 `PlayerEvent.Clone`。

## 2 逐文件 diff 结论（python difflib，`forge-1.20.1` 同名文件 ↔ 本线文件）

| 文件 | 行数 forge→fabric | unified diff hunk 数 | 结论 |
|---|---|---|---|
| `event/EventTimerStore.java` | 158 → 158 | **0**（空 diff） | 内容**逐字等价**；仅行尾归一化（该蓝本文件是 LF-only，本线写 CRLF ⇒ 字节差 158 = 行数）。零平台差异，与 §1 A 阶段判定一致 |
| `event/GlobalEventGate.java` | 156 → 173 | 10 | 仅：删 3 个 forge import + `@Mod.EventBusSubscriber` + `onServerStopping` 方法；新增类头「D1 唯一调用点」段 + 「平台差异 3 条」段；`canStart`/`notifyBlocked` 的 `{@link EventCardService…}` 改 `{@code}`（该类本线尚不存在，`{@link}` 会在 javadoc 期告警）。**方法体一字未改** |
| `event/EventAnimationLock.java` | 109 → 117 | 7 | 仅：import 换本线垫片（`platform.event.SubscribeEvent` / `platform.event.entity.living.LivingDeathEvent` / `platform.event.entity.player.PlayerEvent`）；删 `@Mod.EventBusSubscriber` + `onServerStopping`；新增「平台差异 4 条」段；`EventCardService#{DELAY_TICKS,showThenRun}` 的 `{@link}` 改 `{@code}`。**方法体一字未改**（含 `sync` 语义、`isAnimating` 到期即删、`begin` 覆盖式取新刻） |

保留的蓝本笔误：`EventTimerStore.java:42` 的重复 `/**`（javac 容忍，属注释）按原样保留，以保 diff=0。

## 3 唯一改动的既有文件：`platform/FabricBridges.java`（+8 行，无 import 变更）

改动前 6 行（HEAD `:82-87`，即既有 `SERVER_STOPPING` 先例）：
```java
82:         ServerLifecycleEvents.SERVER_STARTING.register(SimpleChannel::setServer);
83:         ServerLifecycleEvents.SERVER_STOPPING.register(s -> SimpleChannel.setServer(null));
84:         // 事件派发计数报告(诊断):关服时打印一次。
85:         ServerLifecycleEvents.SERVER_STOPPING.register(server ->
86:                 com.merlinkitsune.astral_dice.AstralDiceMod.LOGGER.info(
87:                         "[Astral Dice] 事件派发统计(关服){}", LoaderBus.INSTANCE.dispatchReport()));
```
改动后 6 行（工作区 `:90-95`，新增段尾部）：
```java
90:         // forge 侧靠 @Mod.EventBusSubscriber 订阅 ServerStoppingEvent;本线**没有**该事件垫片(全仓零命中),
91:         // 故按上面两行的先例直挂 FAPI —— 不新建事件类、不走 LoaderBus(事件类不存在,走总线既多余又易漏派发)。
92:         ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
93:             com.merlinkitsune.astral_dice.event.GlobalEventGate.reset();
94:             com.merlinkitsune.astral_dice.event.EventAnimationLock.reset();
95:         });
```
- `git diff --stat` = `1 file changed, 8 insertions(+)`（4 行注释 + 4 行代码），无删除、无既有行位移以外的改动，除本段外文件其余内容未动。
- 用**全限定名**直写两个 `reset()`，故**零 import 变更**（最小 diff；同文件 `:86` 已有 `com.merlinkitsune.astral_dice.AstralDiceMod.LOGGER` 全限定先例）。
- 钩子必要性（§8-R2）：`GlobalEventGate.gateUntilGameTime` 与 `EventAnimationLock.ANIM_UNTIL` 都是 **game time 绝对值**；单人「退出存档 → 换存档」后新存档 `gameTime` 可能仍小于旧值，不清零 ⇒ 事件卡被永久锁死。forge 侧该清零由 `ServerStoppingEvent` 承担，本线该事件全仓零命中 ⇒ 直挂 `ServerLifecycleEvents.SERVER_STOPPING`，与 `:83`/`:85` 两处先例同口径。

## 4 public 签名表（后续批次按此接线）

### event.EventTimerStore（158 行，纯 vanilla）
```
public final class EventTimerStore                                   // 私有构造，全静态
public static long    start(Player, ResourceLocation eventId, int ticks)
public static long    start(Player, ResourceLocation eventId, int ticks, Consumer<Player> onExpire)
public static long    remaining(Player, ResourceLocation eventId)   // 0 = 未计时；>0 剩余 tick
public static Set<ResourceLocation> activeEventIds(Player)
public static boolean isActive(Player, ResourceLocation eventId)
public static void    clear(Player, ResourceLocation eventId)
public static int     activeCount()
```
### event.GlobalEventGate（173 行）
```
public final class GlobalEventGate                                  // 私有构造，全静态
public static final long   GAP_TICKS = 20L                          // 事件结束后硬间隔(1s)
public static final String NOTICE_KEY = "msg.astral_dice.event_global_cooldown"
public static synchronized boolean canStart(ServerPlayer, long durationTicks)   // ⚠ 本线零调用点(历史 API)
public static synchronized boolean tryAcquire(ServerPlayer, long durationTicks) // 唯一活路径:Dispatcher ITEM_USE
public static synchronized long    gateUntilGameTime()
public static synchronized void    reset()                                       // 由 FabricBridges 停服调用
public static synchronized void    notifyBlocked(ServerPlayer, long durationTicks)
```
`canStart` 与 `notifyBlocked` 在 fabric 线**当前零调用点**（D1 之后不存在「先判后消耗」的调用方），保留只为签名对齐蓝本；`canStart` javadoc 已就地标注这一点。
### event.EventAnimationLock（117 行）
```
public final class EventAnimationLock                               // 私有构造，全静态
public static final String NOTICE_KEY = "msg.astral_dice.event_animating"
public static synchronized void    begin(ServerPlayer, int ticks)  // 覆盖式取新结束刻；ticks<=0 不建锁
public static synchronized boolean isAnimating(ServerPlayer)       // 到期项顺手删除
public static synchronized void    clear(ServerPlayer)
public static synchronized void    reset()                          // 由 FabricBridges 停服调用
@SubscribeEvent public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent)
@SubscribeEvent public static void onPlayerDeath(LivingDeathEvent)
```

## 5 两项已裁决事项的落地

- **D1（闸门只剩 Dispatcher 的 ITEM_USE）**：`GlobalEventGate` 类头新增 10 行「现行调用面」段，明写「本闸门整条线只有一个调用点 = `AstralEventDispatcher` 的 `ITEM_USE`」「`EventChoiceManager` 自 D1 起不再查询本闸门」「**不要**按 `docs/compat-1.5.0-porting.md:27` 的旧口径在 `EventChoiceManager` 里恢复预判（会重新引入『双获取』与『界面已选却被静默拒绝』两个已登记缺陷）」。签名与实现照蓝本保留。
- **D2（无「39t」，真实 `DELAY_TICKS = 38`）**：`GlobalEventGate:125` `eventEndsAtGameTime = now + EventCardService.DELAY_TICKS + duration;` **原样保留对常量的引用**，未复制字面量 38——38 = `CARD_ANIM_TICKS(28)` + `SETTLE_BUFFER_TICKS(10)`（forge `EventCardService.java:34/38` 实测），复制一份就会与 `EventAnimationLock` 的起锁时长漂移（§5.1）。

## 6 阻塞项（交主会话裁决，本批未自行消解）

1. **`event/EventCardService` 在本线不存在**（`grep -rn "EventCardService" fabric-1.20.1/src/main/java/` 仅命中本批 3 个文件的注释与 `GlobalEventGate:125`）。按 §7 它属 B2-5。后果：`GlobalEventGate` 会多出一个**编译期**依赖，使「唯一根因 = `item/sign/LinglingSignManager`」变成两个根因。已上报父代理并给出三选项：**A** 接受第二根因、等 B2-5 落地；**B** 本行临时写 `38` 并加 `TODO(B2-5)` 注释（与 D2 单一事实源相悖，不推荐）；**C** 本文件延后到 B2-5 之后落地。现状：**按 A 保留引用**。
2. **`EventAnimationLock` 的两个 `@SubscribeEvent` 需登记才派发**：须在 `AstralDiceMod#registerListeners()` 里加一行 `LoaderBus.INSTANCE.register(EventAnimationLock.class);`，而 `AstralDiceMod.java` 在本批禁改清单 ⇒ 未添加。**漏登记即静默失效**（编译、启动日志全绿，只是死人不解锁），已在类头 javadoc 写明要求与判据工具（`LoaderBus#dispatchReport()` 未派发段 × `SubscriptionAudit` 差集）。`GlobalEventGate` 无 `@SubscribeEvent` 方法，**不需要**登记。
3. 附注（蓝本既存、非本批引入）：`EventTimerStore#clear` 全仓仍无调用点（§8-R10 登出泄漏），本批照蓝本原样移植、未擅自加钩子。

## 7 自检读数（源码级证据；未跑 gradle，故不含编译结论）

| 判据 | EventTimerStore | GlobalEventGate | EventAnimationLock | FabricBridges |
|---|---|---|---|---|
| CRLF / 空 / 裸 LF | 158 / 158 / **0** | 173 / 173 / **0** | 117 / 117 / **0** | 230 / 230 / **0** |
| `net.minecraftforge` 命中 | **0** | **0** | **0** | **0** |
| `cpw.mods` 命中 | **0** | **0** | **0** | **0** |
| `fromNamespaceAndPath` 命中 | **0** | **0** | **0** | **0** |
| BOM | 无 | 无 | 无 | 无 |

- 三必核（`mc-multiloader-fix-transplant`）：① 本线特有内容未被覆盖——本批 3 个文件均为新增，`FabricBridges` 只增不删（`git diff` 0 删除行）；② 无 mixin 注入点（本批不涉及 `astral_dice.mixins.json`）；③ lang 键集未动（`NOTICE_KEY` 两个字符串沿用蓝本常量值，不新增键）。
- 平台垫片可用性均为**实读**结论：`platform/event/SubscribeEvent`（`RUNTIME/METHOD`，支持 `static` 方法，`priority`/`receiveCanceled` 字段）、`platform/event/entity/living/LivingDeathEvent`（`getEntity() → LivingEntity`，派发点 `platform/PuzzlesBridges` `LivingDeathCallback`）、`platform/event/entity/player/PlayerEvent.PlayerLoggedOutEvent`（`getEntity() → Player`，派发点 `FabricBridges:143` 的 `DISCONNECT`）。故 `instanceof ServerPlayer` 两处判据逐字成立。

