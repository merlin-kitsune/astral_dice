# 26.1.2：Waystone 软兼容修复 + 帕秋莉手册用例修复

- 仓库：`F:\MCProject\astral_dice_multiloader`（分支 `multi-main`，HEAD `fd990c15`）
- 日期：2026-11-02
- 改动范围：仅 `neoforge-26.1.2` 源码 1 个类 + `scripts/test/cases/RELEASE150-26.1.2.json` 1 行；未触碰 `run/**`、未触碰其它三条线的源码/资源/用例。
- 未执行任何 git add / commit / push / tag。

## 0. 结论速览

| 项 | 结果 |
|---|---|
| Waystone 根因 | 26.1.2 实装 jar（`waystones-neoforge-26.1.2-26.1.2.16.jar`）的 `api/event/` 里**没有** `WaystoneTeleportEvent$Pre`，改名为 `$Before`（并新增 `$Prepare`） |
| 修法 | 注册处改候选列表 `$Pre → $Before → $Prepare`，第一个 `Class.forName` 成功者胜出；语义（开始打标记 / 完成清标记）不变 |
| 日志分级 | `ClassNotFoundException` ⇒ DEBUG（预期软兼容缺席）；其它异常仍 WARN 且带 `toString()` 原文 |
| 其它线 | `neoforge-1.21.1` 实装 jar 有 `$Pre` ⇒ 三处绑定全部有效，**不改**；`forge-1.20.1` / `fabric-1.20.1` 本机**没有** waystones jar ⇒ 无法对实物取证 |
| 构建 | `BUILD SUCCESSFUL in 2s`，`EXIT=0`，6 tasks（2 executed / 4 up-to-date） |
| 新 jar | `2,878,499 B` / `sha256 14b6a2196706abc6887dbd08781c5c96d601aa8398d84988324b0dc16442d7b4`（旧 `2,877,947 B` / `b422d57d…878d`，**+552 B**） |
| jar 三项复核 | sounds.json 23 键含 8 条 `event_*` ✔；`item.astral_dice.abyss_reagent_chip` = 「深渊试剂」✔；新 `WaystoneWarpCompat.class` 内含三个候选类名字符串 ✔ |
| 用例 step 27 | `net.minecraft.resources.ResourceLocation` → `net.minecraft.resources.Identifier`（26.1.2 该类已改名，`ResourceLocation*` 在合并 jar 里 0 条） |
| validate | `MT_VALIDATE: OK — RELEASE150-26.1.2`，exit 0 |

---

## 1. 任务 1：`WaystoneWarpCompat` 候选类名

### 1.1 报错原文（实机日志）

`run/26.1.2/.tmp/prod-run.log` 已被另一个 agent 轮转，原行取自 `run/26.1.2/.tmp/prod-run.log.prev-20261102-0455` 第 519 行：

```text
[04:31:06] [modloading-worker-0/WARN] [co.me.as.ev.WaystoneWarpCompat/]: [Astral Dice] Failed to register Waystone teleport integration: java.lang.ClassNotFoundException: net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Pre
```

同一份日志第 159 行确认当时的 Waystones 版本：`Waystones 26.1.2.16 (waystones)`。

### 1.2 实物 jar 取证（`api/event/` 全量清单 + 五个目标类存在性）

```text
==============================================================================
[26.1.2] D:/.minecraft/versions/26.1.2-NeoForge 模组测试/mods/[传送石碑／指路石] waystones-neoforge-26.1.2-26.1.2.16.jar
    bytes=1172709 sha256=f4bf6dc11bda0e7286083f6b67984428b88705aa1b180511d4b5de3a9934eb07
    api/event total .class = 21
    WaystoneTeleport* = 8:
      - WaystoneTeleportEntityEvent$Post
      - WaystoneTeleportEntityEvent$Pre
      - WaystoneTeleportEntityEvent
      - WaystoneTeleportEvent$After
      - WaystoneTeleportEvent$Before
      - WaystoneTeleportEvent$Complete
      - WaystoneTeleportEvent$Prepare
      - WaystoneTeleportEvent
    EXISTS WaystoneTeleportEvent$Pre     = False
    EXISTS WaystoneTeleportEvent$Before  = True
    EXISTS WaystoneTeleportEvent$Prepare = True
    EXISTS WaystoneTeleportEntityEvent$Post = True
    EXISTS WaystoneTeleportEvent$Complete = True
==============================================================================
[1.21.1] run/1.21.1/prodgame/mods/[传送石碑／指路石] waystones-neoforge-1.21.1-21.1.46.jar
    bytes=1223229 sha256=ca76a4457160e8353b1f013a21ab377b264141f466d84ec8952a123058b8af78
    api/event total .class = 21
    WaystoneTeleport* = 8:
      - WaystoneTeleportEntityEvent$Post
      - WaystoneTeleportEntityEvent$Pre
      - WaystoneTeleportEntityEvent
      - WaystoneTeleportEvent$Complete
      - WaystoneTeleportEvent$Post
      - WaystoneTeleportEvent$Pre
      - WaystoneTeleportEvent$Prepare
      - WaystoneTeleportEvent
    EXISTS WaystoneTeleportEvent$Pre     = True
    EXISTS WaystoneTeleportEvent$Before  = False
    EXISTS WaystoneTeleportEvent$Prepare = True
    EXISTS WaystoneTeleportEntityEvent$Post = True
    EXISTS WaystoneTeleportEvent$Complete = True
```

要点：

- **26.1.2 线**：`WaystoneTeleportEvent$Pre` **不存在**（`False`），但 `$Before` / `$Prepare` 都在；另两个目标 `WaystoneTeleportEntityEvent$Post`、`WaystoneTeleportEvent$Complete` 均存在 ⇒ **只有 1 个类名需要改**，与实机日志的 1 条 WARN 完全对应（若另两个也缺，会再多 1~2 条 WARN）。
- **1.21.1 线**：`$Pre` 存在，`$Before` 不存在 ⇒ 该线现有三处绑定全部命中，因此按用户要求**不改**。

### 1.3 javap 取证：候选类在语义上真的是「传送开始」

```text
### javap -p  net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Before
  public class net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Before extends net.blay09.mods.waystones.api.event.WaystoneTeleportEvent {
  public static final net.blay09.mods.balm.platform.event.BidirectionalEventMapper<java.util.function.Consumer<net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Before>> EVENT;
  private final net.blay09.mods.waystones.api.WaystoneTeleportContext context;
  private boolean canceled;
  public net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Before(net.blay09.mods.waystones.api.WaystoneTeleportContext);
  public net.blay09.mods.waystones.api.WaystoneTeleportContext getContext();
  public void addAdditionalEntity(net.minecraft.world.entity.Entity);
  public boolean isCanceled();
  public void setCanceled(boolean);
  static {};
  }

### javap -p  net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Prepare
  public class net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Prepare extends net.blay09.mods.waystones.api.event.WaystoneTeleportEvent {
  public static final net.blay09.mods.balm.platform.event.BidirectionalEventMapper<java.util.function.Consumer<net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Prepare>> EVENT;
  private final net.blay09.mods.waystones.api.WaystoneTeleportContext context;
  private final java.util.Set<net.minecraft.world.level.ChunkPos> chunkPositions;
  private final java.util.List<java.util.function.Function<com.mojang.datafixers.util.Either<java.lang.Void, net.blay09.mods.waystones.api.error.WaystoneTeleportError>, java.util.concurrent.CompletableFuture<com.mojang.datafixers.util.Either<java.lang.Void, net.blay09.mods.waystones.api.error.WaystoneTeleportError>>>> preparationTasks;
  public net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Prepare(net.blay09.mods.waystones.api.WaystoneTeleportContext, java.util.Set<net.minecraft.world.level.ChunkPos>);
  public net.blay09.mods.waystones.api.WaystoneTeleportContext getContext();
  public java.util.Set<net.minecraft.world.level.ChunkPos> getChunkPositions();
  public void addChunkPosition(net.minecraft.world.level.ChunkPos);
  public void addPreparationTask(java.util.function.Function<com.mojang.datafixers.util.Either<java.lang.Void, net.blay09.mods.waystones.api.error.WaystoneTeleportError>, java.util.concurrent.CompletableFuture<com.mojang.datafixers.util.Either<java.lang.Void, net.blay09.mods.waystones.api.error.WaystoneTeleportError>>>);
  public java.util.List<java.util.function.Function<com.mojang.datafixers.util.Either<java.lang.Void, net.blay09.mods.waystones.api.error.WaystoneTeleportError>, java.util.concurrent.CompletableFuture<com.mojang.datafixers.util.Either<java.lang.Void, net.blay09.mods.waystones.api.error.WaystoneTeleportError>>>> getPreparationTasks();
  static {};
  }

### javap -p  net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Complete
  public class net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Complete extends net.blay09.mods.waystones.api.event.WaystoneTeleportEvent {
  public static final net.blay09.mods.balm.platform.event.BidirectionalEventMapper<java.util.function.Consumer<net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Complete>> EVENT;
  private final net.blay09.mods.waystones.api.WaystoneTeleportContext context;
  private final net.blay09.mods.waystones.api.EntityTeleportResult primaryResult;
  private final java.util.List<net.blay09.mods.waystones.api.EntityTeleportResult> additionalResults;
  private final net.blay09.mods.waystones.api.error.WaystoneTeleportError teleportError;
  public net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Complete(net.blay09.mods.waystones.api.WaystoneTeleportContext, net.blay09.mods.waystones.api.EntityTeleportResult, java.util.List<net.blay09.mods.waystones.api.EntityTeleportResult>, net.blay09.mods.waystones.api.error.WaystoneTeleportError);
  public net.blay09.mods.waystones.api.WaystoneTeleportContext getContext();
  public java.util.Optional<net.blay09.mods.waystones.api.EntityTeleportResult> getPrimaryResult();
  public java.util.List<net.blay09.mods.waystones.api.EntityTeleportResult> getAdditionalResults();
  public java.util.List<net.blay09.mods.waystones.api.EntityTeleportResult> getResults();
  public java.util.List<net.minecraft.world.entity.Entity> getTeleportedEntities();
  public java.util.Optional<net.blay09.mods.waystones.api.error.WaystoneTeleportError> getTeleportError();
  static {};
  }

### javap -p  net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent$Post
  public class net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent$Post extends net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent {
  public static final net.blay09.mods.balm.platform.event.BidirectionalEventMapper<java.util.function.Consumer<net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent$Post>> EVENT;
  private final net.minecraft.world.entity.Entity teleportedEntity;
  private final net.blay09.mods.waystones.api.EntityTeleportResult result;
  public net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent$Post(net.blay09.mods.waystones.api.WaystoneTeleportContext, net.minecraft.world.entity.Entity, net.blay09.mods.waystones.api.EntityTeleportResult, net.blay09.mods.waystones.api.TeleportDestination, net.minecraft.server.level.ServerLevel, net.minecraft.world.phys.Vec3, net.minecraft.core.Direction);
  public net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent$Post(net.blay09.mods.waystones.api.WaystoneTeleportContext, net.minecraft.world.entity.Entity, net.minecraft.world.entity.Entity, net.blay09.mods.waystones.api.TeleportDestination, net.minecraft.server.level.ServerLevel, net.minecraft.world.phys.Vec3, net.minecraft.core.Direction);
  public net.minecraft.world.entity.Entity getTeleportedEntity();
  public net.blay09.mods.waystones.api.EntityTeleportResult getTeleportResult();
  static {};
  }

### javap -p  net.blay09.mods.waystones.api.WaystoneTeleportContext
  public interface net.blay09.mods.waystones.api.WaystoneTeleportContext extends net.blay09.mods.shogi.context.ShogiContext {
  public abstract net.minecraft.world.entity.Entity getEntity();
  public abstract net.blay09.mods.waystones.api.Waystone getTargetWaystone();
  public abstract java.util.List<net.minecraft.world.entity.Mob> getLeashedEntities();
  public abstract java.util.List<net.minecraft.world.entity.Entity> getAdditionalEntities();
  public abstract net.blay09.mods.waystones.api.WaystoneTeleportContext addAdditionalEntity(net.minecraft.world.entity.Entity);
  public abstract java.util.Optional<net.blay09.mods.waystones.api.Waystone> getFromWaystone();
  public abstract net.blay09.mods.waystones.api.WaystoneTeleportContext setFromWaystone(net.blay09.mods.waystones.api.Waystone);
  public abstract net.minecraft.world.item.ItemStack getWarpItem();
  public abstract net.blay09.mods.waystones.api.WaystoneTeleportContext setWarpItem(net.minecraft.world.item.ItemStack);
  public abstract net.minecraft.world.InteractionHand getWarpHand();
  public abstract net.blay09.mods.waystones.api.WaystoneTeleportContext setWarpHand(net.minecraft.world.InteractionHand);
  public abstract boolean isDimensionalTeleport();
  public abstract boolean playsSound();
  public abstract net.blay09.mods.waystones.api.WaystoneTeleportContext setPlaysSound(boolean);
  public abstract boolean playsEffect();
  public abstract net.blay09.mods.waystones.api.WaystoneTeleportContext setPlaysEffect(boolean);
  public abstract java.util.Set<net.minecraft.resources.Identifier> getFlags();
  public abstract net.blay09.mods.waystones.api.WaystoneTeleportContext addFlag(net.minecraft.resources.Identifier);
  public abstract net.blay09.mods.waystones.api.WaystoneTeleportContext removeFlag(net.minecraft.resources.Identifier);
  public abstract com.mojang.datafixers.util.Either<java.util.List<java.lang.Object>, java.util.List<java.lang.Object>> getRequirements();
  public abstract void setRequirements(com.mojang.datafixers.util.Either<java.util.List<java.lang.Object>, java.util.List<java.lang.Object>>);
  public default net.blay09.mods.waystones.api.WaystoneTeleportContext addFlags(java.util.Set<net.minecraft.resources.Identifier>);
  public default boolean hasFlag(net.minecraft.resources.Identifier);
  }

### javap -p  net.blay09.mods.waystones.api.EntityTeleportResult
  public final class net.blay09.mods.waystones.api.EntityTeleportResult {
  private final net.minecraft.world.entity.Entity entity;
  private final net.blay09.mods.waystones.api.TeleportDestination originalDestination;
  private final net.blay09.mods.waystones.api.TeleportDestination resolvedDestination;
  private final net.blay09.mods.waystones.api.error.WaystoneTeleportError error;
  private final net.minecraft.world.entity.Entity originalVehicle;
  private final int passengerIndex;
  public net.blay09.mods.waystones.api.EntityTeleportResult(net.minecraft.world.entity.Entity, net.blay09.mods.waystones.api.TeleportDestination, net.blay09.mods.waystones.api.TeleportDestination, net.blay09.mods.waystones.api.error.WaystoneTeleportError);
  public net.blay09.mods.waystones.api.EntityTeleportResult(net.minecraft.world.entity.Entity, net.blay09.mods.waystones.api.TeleportDestination, net.blay09.mods.waystones.api.TeleportDestination, net.blay09.mods.waystones.api.error.WaystoneTeleportError, net.minecraft.world.entity.Entity, int);
  public static net.blay09.mods.waystones.api.EntityTeleportResult success(net.minecraft.world.entity.Entity, net.blay09.mods.waystones.api.TeleportDestination, net.blay09.mods.waystones.api.TeleportDestination);
  public static net.blay09.mods.waystones.api.EntityTeleportResult failed(net.minecraft.world.entity.Entity, net.blay09.mods.waystones.api.TeleportDestination, net.blay09.mods.waystones.api.error.WaystoneTeleportError);
  public net.blay09.mods.waystones.api.EntityTeleportResult withOriginalVehicle(net.minecraft.world.entity.Entity, int);
  public boolean isSuccessful();
  public net.minecraft.world.entity.Entity entity();
  public net.blay09.mods.waystones.api.TeleportDestination originalDestination();
  public net.blay09.mods.waystones.api.TeleportDestination resolvedDestination();
  public net.blay09.mods.waystones.api.error.WaystoneTeleportError error();
  public java.util.Optional<net.minecraft.world.entity.Entity> originalVehicle();
  public int passengerIndex();
  }
```

判定要点（决定 `$Before` 能顶替 `$Pre`）：

- `WaystoneTeleportEvent$Before`：`getContext()` + `isCanceled()/setCanceled(boolean)` + `addAdditionalEntity(Entity)` ⇒ 与 1.x 的 `$Pre` 同为「传送开始前、可取消」事件，是本模组需要的那一个。
- `WaystoneTeleportEvent$Prepare`：同为前置阶段且有 `getContext()`（另带 `getChunkPositions()`/`addPreparationTask(...)`）⇒ 可作第三候选兜底。
- 本模组的使用面极小：`onWaystonePre` 只做 `getContext()` → `context.getEntity()` → `markPending(player)`，**从不取消事件**，所以 `$Before`/`$Prepare` 任一都能正确打标记。
- `WaystoneTeleportContext` 接口声明 `abstract Entity getEntity()`；`EntityTeleportResult` 有 `public boolean isSuccessful()` ⇒ 另两个目标类用到的反射访问器在 26.1.2 上同样成立，链路其余部分无需改动。

### 1.4 代码 diff（改前/改后）

```diff
diff --git a/neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/event/WaystoneWarpCompat.java b/neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/event/WaystoneWarpCompat.java
index 15bb66e0..7956f5b8 100644
--- a/neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/event/WaystoneWarpCompat.java
+++ b/neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/event/WaystoneWarpCompat.java
@@ -18,8 +18,9 @@ import java.util.function.Consumer;
  * <p>Waystones 事件经 Balm 的 fireEvent 最终会转发到 NeoForge 事件总线,
  * 因此这里在 {@code waystones} 模组加载时通过反射向 NeoForge.EVENT_BUS 注册:
  * <ul>
- *   <li>{@code WaystoneTeleportEvent$Pre}:标记正在进行的 Waystone 传送,避免随后的
- *       跨维度 {@code EntityTravelToDimensionEvent} 提前给跃迁引擎结算一次;</li>
+ *   <li>{@code WaystoneTeleportEvent$Pre}(Waystones 2.x 起改名为 {@code $Before}):标记正在进行
+ *       的 Waystone 传送,避免随后的跨维度 {@code EntityTravelToDimensionEvent} 提前给跃迁引擎
+ *       结算一次;实际类名按 {@link #TELEPORT_PRE_EVENT_CANDIDATES} 逐个探测;</li>
  *   <li>{@code WaystoneTeleportEntityEvent$Post}:实际传送成功后结算跃迁引擎;</li>
  *   <li>{@code WaystoneTeleportEvent$Complete}:清理 Pre 标记(无论成功/失败)。</li>
  * </ul>
@@ -34,6 +35,22 @@ public final class WaystoneWarpCompat {
     private static final Map<UUID, Long> PENDING = new HashMap<>();
     private static boolean registered = false;
 
+    /**
+     * 「传送开始」事件的候选类名,按 Waystones 各代 API 的实际形态排序。
+     *
+     * <p>取证(实物 jar):{@code waystones-neoforge-26.1.2-26.1.2.16.jar} 的
+     * {@code net/blay09/mods/waystones/api/event/} 下只有
+     * {@code WaystoneTeleportEvent$Before/$Prepare/$After/$Complete},<b>没有</b> {@code $Pre};
+     * 而 {@code waystones-neoforge-1.21.1-21.1.46.jar} 只有 {@code $Pre/$Prepare/$Post/$Complete}。
+     * {@code $Before} 与 {@code $Pre} 同为「传送开始前、可取消」事件(两者都提供
+     * {@code getContext()}),因此作为等效替代。
+     */
+    private static final String[] TELEPORT_PRE_EVENT_CANDIDATES = {
+            "net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Pre",
+            "net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Before",
+            "net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Prepare"
+    };
+
     private WaystoneWarpCompat() {
     }
 
@@ -43,7 +60,7 @@ public final class WaystoneWarpCompat {
             return;
         }
         try {
-            registerWaystoneTeleportEvent("net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Pre",
+            registerWaystoneTeleportEvent(TELEPORT_PRE_EVENT_CANDIDATES,
                     WaystoneWarpCompat::onWaystonePre);
             registerWaystoneTeleportEvent("net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent$Post",
                     WaystoneWarpCompat::onWaystoneEntityPost);
@@ -51,6 +68,10 @@ public final class WaystoneWarpCompat {
                     WaystoneWarpCompat::onWaystoneComplete);
             registered = true;
             LOGGER.info("[Astral Dice] Waystone teleport integration enabled for 跃迁引擎.");
+        } catch (ClassNotFoundException absent) {
+            // 类名不存在 = 该 Waystones 版本没有对应 API(未装 / 2.x 改名),属预期软兼容缺席,不是错误
+            LOGGER.debug("[Astral Dice] Waystone teleport integration not available (API absent): {}",
+                    absent.toString());
         } catch (Throwable t) {
             LOGGER.warn("[Astral Dice] Failed to register Waystone teleport integration: {}", t.toString());
         }
@@ -87,13 +108,40 @@ public final class WaystoneWarpCompat {
     }
 
     private static void registerWaystoneTeleportEvent(String className, Consumer<Object> handler) throws Exception {
-        Class<?> eventClass = Class.forName(className);
+        registerWaystoneTeleportEvent(new String[]{className}, handler);
+    }
+
+    /** 按候选顺序注册监听:第一个能被 {@code Class.forName} 加载的类胜出(跨 Waystones 版本的软兼容)。 */
+    private static void registerWaystoneTeleportEvent(String[] candidateClassNames, Consumer<Object> handler)
+            throws Exception {
+        Class<?> eventClass = resolveFirstPresentClass(candidateClassNames);
         Class<?> busClass = Class.forName("net.neoforged.neoforge.common.NeoForge");
         Object bus = busClass.getField("EVENT_BUS").get(null);
         Method addListener = bus.getClass().getMethod("addListener", Class.class, Consumer.class);
         addListener.invoke(bus, eventClass, handler);
     }
 
+    /**
+     * 逐个尝试候选类名,返回第一个真实存在的类。
+     *
+     * <p>只有 {@link ClassNotFoundException}(= 本版本没有这个类)会继续试下一个;其它异常
+     * ({@link LinkageError}、静态初始化失败等)属于真实故障,立即抛给调用方按 WARN 记录。
+     */
+    private static Class<?> resolveFirstPresentClass(String[] candidateClassNames) throws Exception {
+        ClassNotFoundException lastAbsent = null;
+        for (String candidateClassName : candidateClassNames) {
+            try {
+                return Class.forName(candidateClassName);
+            } catch (ClassNotFoundException absent) {
+                lastAbsent = absent;
+            }
+        }
+        if (lastAbsent != null) {
+            throw lastAbsent;
+        }
+        throw new ClassNotFoundException("empty candidate list for a Waystone teleport event");
+    }
+
     private static void onWaystonePre(Object event) {
         try {
             Object context = invoke(event, "getContext");
```

### 1.5 编译产物取证（新 jar 内 `WaystoneWarpCompat.class` 的 `javap -p -c`）

只摘三段关键字节码：字段声明、`init()` 的注册与两级 catch、`resolveFirstPresentClass()`、以及静态初始化里候选数组的装载顺序。

```text
  private static final java.lang.String[] TELEPORT_PRE_EVENT_CANDIDATES;
  public static void init();
    Code:
       0: getstatic     #7                  // Field registered:Z
       3: ifne          17
       6: invokestatic  #13                 // Method net/neoforged/fml/ModList.get:()Lnet/neoforged/fml/ModList;
       9: ldc           #19                 // String waystones
      11: invokevirtual #21                 // Method net/neoforged/fml/ModList.isLoaded:(Ljava/lang/String;)Z
      14: ifne          18
      17: return
      18: getstatic     #25                 // Field TELEPORT_PRE_EVENT_CANDIDATES:[Ljava/lang/String;
      21: invokedynamic #29,  0             // InvokeDynamic #0:accept:()Ljava/util/function/Consumer;
      26: invokestatic  #33                 // Method registerWaystoneTeleportEvent:([Ljava/lang/String;Ljava/util/function/Consumer;)V
      29: ldc           #37                 // String net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent$Post
      31: invokedynamic #39,  0             // InvokeDynamic #1:accept:()Ljava/util/function/Consumer;
      36: invokestatic  #40                 // Method registerWaystoneTeleportEvent:(Ljava/lang/String;Ljava/util/function/Consumer;)V
      39: ldc           #43                 // String net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Complete
      41: invokedynamic #45,  0             // InvokeDynamic #2:accept:()Ljava/util/function/Consumer;
      46: invokestatic  #40                 // Method registerWaystoneTeleportEvent:(Ljava/lang/String;Ljava/util/function/Consumer;)V
      49: iconst_1
      50: putstatic     #7                  // Field registered:Z
      53: getstatic     #46                 // Field LOGGER:Lorg/slf4j/Logger;
      56: ldc           #50                 // String [Astral Dice] Waystone teleport integration enabled for ԾǨ����.
      58: invokeinterface #52,  2           // InterfaceMethod org/slf4j/Logger.info:(Ljava/lang/String;)V
      63: goto          99
      66: astore_0
      67: getstatic     #46                 // Field LOGGER:Lorg/slf4j/Logger;
      70: ldc           #60                 // String [Astral Dice] Waystone teleport integration not available (API absent): {}
      72: aload_0
      73: invokevirtual #62                 // Method java/lang/ClassNotFoundException.toString:()Ljava/lang/String;
      76: invokeinterface #66,  3           // InterfaceMethod org/slf4j/Logger.debug:(Ljava/lang/String;Ljava/lang/Object;)V
      81: goto          99
      84: astore_0
      85: getstatic     #46                 // Field LOGGER:Lorg/slf4j/Logger;
      88: ldc           #72                 // String [Astral Dice] Failed to register Waystone teleport integration: {}
      90: aload_0
      91: invokevirtual #74                 // Method java/lang/Throwable.toString:()Ljava/lang/String;
      94: invokeinterface #75,  3           // InterfaceMethod org/slf4j/Logger.warn:(Ljava/lang/String;Ljava/lang/Object;)V
      99: return
    Exception table:
       from    to  target type
          18    63    66   Class java/lang/ClassNotFoundException
          18    63    84   Class java/lang/Throwable


  private static java.lang.Class<?> resolveFirstPresentClass(java.lang.String[]) throws java.lang.Exception;
    Code:
       0: aconst_null
       1: astore_1
       2: aload_0
       3: astore_2
       4: aload_2
       5: arraylength
       6: istore_3
       7: iconst_0
       8: istore        4
      10: iload         4
      12: iload_3
      13: if_icmpge     39
      16: aload_2
      17: iload         4
      19: aaload
      20: astore        5
      22: aload         5
      24: invokestatic  #151                // Method java/lang/Class.forName:(Ljava/lang/String;)Ljava/lang/Class;
      27: areturn
      28: astore        6
      30: aload         6
      32: astore_1
      33: iinc          4, 1
      36: goto          10
      39: aload_1
      40: ifnull        45
      43: aload_1
      44: athrow
      45: new           #58                 // class java/lang/ClassNotFoundException
      48: dup
      49: ldc           #184                // String empty candidate list for a Waystone teleport event
      51: invokespecial #186                // Method java/lang/ClassNotFoundException."<init>":(Ljava/lang/String;)V
      54: athrow
    Exception table:
       from    to  target type
          22    27    28   Class java/lang/ClassNotFoundException


  static {};
    Code:
       0: ldc           #8                  // class com/merlinkitsune/astral_dice/event/WaystoneWarpCompat
       2: invokestatic  #231                // Method org/slf4j/LoggerFactory.getLogger:(Ljava/lang/Class;)Lorg/slf4j/Logger;
       5: putstatic     #46                 // Field LOGGER:Lorg/slf4j/Logger;
       8: new           #237                // class java/util/HashMap
      11: dup
      12: invokespecial #239                // Method java/util/HashMap."<init>":()V
      15: putstatic     #94                 // Field PENDING:Ljava/util/Map;
      18: iconst_0
      19: putstatic     #7                  // Field registered:Z
      22: iconst_3
      23: anewarray     #143                // class java/lang/String
      26: dup
      27: iconst_0
      28: ldc           #240                // String net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Pre
      30: aastore
      31: dup
      32: iconst_1
      33: ldc           #242                // String net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Before
      35: aastore
      36: dup
      37: iconst_2
      38: ldc           #244                // String net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Prepare
      40: aastore
      41: putstatic     #25                 // Field TELEPORT_PRE_EVENT_CANDIDATES:[Ljava/lang/String;
      44: return
}
```

要点：

- 静态初始化里 `iconst_3 / anewarray String` 后按 `iconst_0 → $Pre`、`iconst_1 → $Before`、`iconst_2 → $Prepare` 顺序 `aastore` ⇒ 候选**顺序**与源码一致（先 `$Pre`，1.21.1 那种老 API 仍然命中第一个）。
- `init()` 的 `Exception table`：`from 18 to 63 → target 66 (ClassNotFoundException)`、`from 18 to 63 → target 84 (Throwable)`。66 号段落调 `Logger.debug("... not available (API absent): {}", absent.toString())`，84 号落调 `Logger.warn("... Failed to register ...: {}", t.toString())`，之后 `goto 99 / return`。⇒「类不存在降 DEBUG、其它异常仍 WARN 且带原文」在字节码层已生效，且两者互斥（同一区间先匹配更具体的 CNF）。
- `registerWaystoneTeleportEvent(String, Consumer)` 现在只做一件事：把单类名包成单元素数组转调 `registerWaystoneTeleportEvent(String[], Consumer)`（`invokestatic #33`），因此另外两个目标类（`WaystoneTeleportEntityEvent$Post`、`WaystoneTeleportEvent$Complete`）的行为**零变化**。
- `resolveFirstPresentClass(String[])`：循环 `Class.forName`，只 `catch ClassNotFoundException` 继续下一个；全部缺席时抛最后一个 CNF（日志走 DEBUG）。`LinkageError` / 静态初始化失败不在 catch 内 ⇒ 直接冒出到 `init()` 的 `Throwable` 分支 ⇒ WARN。

> 备注：javap 打印 `init()` 里那句中文 INFO 文案时显示为乱码（`ԾǨ����` = 「跃迁引擎」被按 GBK 解码）。这是 `javap` 控制台字符集问题，不是 class 内容问题——常量池文本仍是 UTF-8 的 `[Astral Dice] Waystone teleport integration enabled for 跃迁引擎.`，运行期无影响。

### 1.6 日志分级行为矩阵（改后）

| 情形 | 触发点 | 改前 | 改后 |
|---|---|---|---|
| 没装 Waystones | `ModList.isLoaded("waystones")` 为假，直接 return | 无日志 | 无日志 |
| 装了 Waystones 但三个候选类都不存在（未来再改名） | `resolveFirstPresentClass` 抛 `ClassNotFoundException` | WARN（误导为故障） | **DEBUG** 「not available (API absent)」 |
| 类存在但 `addListener` 签名不符 / `LinkageError` / 静态初始化失败 | `resolveFirstPresentClass` 之外或内层直接抛出 | WARN | **仍 WARN**，带 `t.toString()` |
| 仍沿用「失败即不再尝试」 | `registered` 布尔，只有全成功才置 true | 一致 | 一致 |

`resolveFirstPresentClass` 只在 `ClassNotFoundException` 时继续试下一个候选；`LinkageError` 之类不在 `catch` 范围内，直接冒出到 `init()` 的 `catch (Throwable)` ⇒ 不会把真实故障降级成 DEBUG。

---

## 2. 各线 Waystone 类名核对（只读）

### 2.1 源码侧：四条线的类名写法完全相同

| 线 | 文件 | 第 46/48/50 行的三个类名 |
|---|---|---|
| neoforge-1.21.1 | `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/WaystoneWarpCompat.java` | `$Pre` / `WaystoneTeleportEntityEvent$Post` / `$Complete` |
| forge-1.20.1 | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/WaystoneWarpCompat.java` | 同上 |
| fabric-1.20.1 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/WaystoneWarpCompat.java` | 同上 |
| neoforge-26.1.2 | `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/event/WaystoneWarpCompat.java` | **本次改为候选列表** / `$Post` / `$Complete` |

### 2.2 实物侧（本机磁盘全量搜 `*waystone*` 的 jar）

```text
/d/.minecraft/versions/26.1.2-NeoForge 模组测试/mods/[传送石碑／指路石] waystones-neoforge-26.1.2-26.1.2.16.jar
/d/.minecraft/versions/狐の航空学/mods/waystonessable-1.0.7.jar
/d/.minecraft/versions/狐の航空学/mods/[传送石碑／指路石] waystones-neoforge-1.21.1-21.1.46.jar
/d/.minecraft/versions/狐の航空学 Voxy Edition/mods/{waystonessable-1.0.7.jar, [传送石碑／指路石] waystones-neoforge-1.21.1-21.1.46.jar}
/d/.minecraft/versions/狐の航空学 低配优化版/mods/{waystonessable-1.0.7.jar, [传送石碑／指路石] waystones-neoforge-1.21.1-21.1.46.jar}
run/1.21.1/prodgame/mods/{waystonessable-1.0.7.jar, [传送石碑／指路石] waystones-neoforge-1.21.1-21.1.46.jar}
run/26.1.2/prodgame/mods/[传送石碑／指路石] waystones-neoforge-26.1.2-26.1.2.16.jar
```

### 2.3 各线结论

| 线 | 本机有无 waystones 实物 | 目标类是否齐全 | 结论 |
|---|---|---|---|
| neoforge-26.1.2 | 有（26.1.2.16） | `$Pre` 缺、`$Before`/`$Prepare` 在 | **已修**（本次唯一改动） |
| neoforge-1.21.1 | 有（21.1.46，`run/1.21.1` + 三个整合包同 jar） | 三处全在（`$Pre` / `$Post` / `$Complete`） | **不动**（符合「有 `$Pre` 就不要动」） |
| forge-1.20.1 | **无**（`run/1.20.1`、`D:/.minecraft/versions/1.20.1-Forge 模组测试/mods` 都没有） | 无法取证 | 不动；代码路径只在装了 Waystones 时才会执行，当前环境无从触发 |
| fabric-1.20.1 | **无**（`run/fabric-1.20.1`、`1.20.1-Fabric 模组测试/mods` 都没有） | 无法取证 | 同上 |

关于 1.20.1 两条线的**风险提示**（不改，仅记录）：Waystones 的类名从 `$Pre` 改成 `$Before` 发生在 2.x 代码库（26.1.2 线）。若将来某天给 1.20.1 的整合包装上同时期的 Waystones 2.x，同样会出现「WARN + 功能静默缺席」。届时的最小修法与本次完全一致：把 `$Pre` 换成同一份候选列表即可（三条线代码结构逐字相同，可整段搬运）。

---

## 3. 构建

命令（在仓库根目录执行，三个推送任务显式排除）：

```bat
./gradlew.bat ":neoforge-26.1.2:build" -x ":neoforge-26.1.2:pushToGame" -x ":neoforge-26.1.2:pushToDevRun" -x ":neoforge-26.1.2:pushToRootBuild"
```

输出尾部（完整日志 `work/_n26_build2.log`）：

```text
> Task :neoforge-26.1.2:compileTestJava NO-SOURCE
> Task :neoforge-26.1.2:testClasses UP-TO-DATE
> Task :neoforge-26.1.2:test NO-SOURCE
> Task :neoforge-26.1.2:check UP-TO-DATE
> Task :neoforge-26.1.2:jar
> Task :neoforge-26.1.2:assemble
> Task :neoforge-26.1.2:build

BUILD SUCCESSFUL in 2s
6 actionable tasks: 2 executed, 4 up-to-date
Configuration cache entry reused.
EXIT=0
```

日志中 `warning` / `deprecat` 命中数 = 0（无编译告警）。`compileJava` 与 `jar` 均为 executed（非 up-to-date），说明改动确实进了产物。

目标 jar 未被写进任何 `run/**` 或整合包目录（三个 push 任务全部 `-x` 排除）。

---

## 4. 新 jar 与三项复核

| 项 | 旧 | 新 |
|---|---|---|
| 文件名 | `astral_dice-1.5.0+neoforge_26.1.2.jar` | 同名（覆盖） |
| 字节数 | 2,877,947 | **2,878,499**（+552） |
| sha256 | `b422d57d2103219522eef792822da1656a1f9e197f91c6b0d8e82d381ec4878d` | **`14b6a2196706abc6887dbd08781c5c96d601aa8398d84988324b0dc16442d7b4`** |
| zip 条目数 | — | 2452 |

三项复核（脚本 `work/_n26_jar_verify.py`，直接读新 jar）：

```text
jar: neoforge-26.1.2/build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar
bytes: 2878499
sha256: 14b6a2196706abc6887dbd08781c5c96d601aa8398d84988324b0dc16442d7b4
entries: 2452

[sounds.json] top-level keys = 23
[sounds.json] event_* keys = 8 ['event_bonus', 'event_damage', 'event_fade_in', 'event_fade_out', 'event_heal', 'event_poisoning', 'event_tick', 'event_transport']
[sounds.json] all keys = ['boost_effect_card_use', 'coin_bag_withdraw', 'coin_withdraw', 'damage_effect_card_bighit', 'damage_effect_card_cast', 'damage_effect_card_hit', 'directional_blast_hit', 'effect_card_use', 'event_bonus', 'event_damage', 'event_fade_in', 'event_fade_out', 'event_heal', 'event_poisoning', 'event_tick', 'event_transport', 'glass_dice_broken', 'megas_blast', 'monster_brick_hit', 'monster_laser_hit', 'orbital_strike_hit', 'skill_use', 'wallet_deposit']

[zh_cn] abyss_reagent_chip = '深渊试剂'
[zh_cn] any key ending abyss_reagent_chip: {'astral_dice.guide.entry.abyss_reagent_chip.1': '装备时治愈层数 +2；触发治愈回血时，获得「实际治疗量 ×2」的伤害吸收。', 'item.astral_dice.abyss_reagent_chip': '深渊试剂'}

[class] present: True size: 7968
  contains $Pre = True
  contains $Before = True
  contains $Prepare = True
  contains WaystoneTeleportEvent$Before = True
```

三项判据全部成立：

1. `sounds.json` 顶层仍是 **23** 个键，其中 `event_*` **8** 条（bonus/damage/fade_in/fade_out/heal/poisoning/tick/transport）；
2. `assets/astral_dice/lang/zh_cn.json` 的 `item.astral_dice.abyss_reagent_chip` 仍是「**深渊试剂**」；
3. 打包后的 `com/merlinkitsune/astral_dice/event/WaystoneWarpCompat.class` 里三个候选类名常量都在（`$Pre` / `$Before` / `$Prepare`），即候选列表真的编进了产物。

---

## 5. 任务 2：用例 step 27（帕秋莉手册）

### 5.1 报错与根因取证

报错：`guide_open=ERR:InternalError: Failed to load Java class 'net.minecraft.resources.ResourceLocation'`。

对 26.1.2 的合并 jar（`neoforge-26.1.2/build/moddev/artifacts/minecraft-patched-26.1.2.109-merged.jar`）做 zipfile 检索：

```text
net/minecraft/resources/Identifier.class        -> 存在
net/minecraft/resources/ResourceLocation.class  -> 不存在（ResourceLocation* 命中 0 条）
```

`javap` 于 `net.minecraft.resources.Identifier`：

```text
public static net.minecraft.resources.Identifier fromNamespaceAndPath(java.lang.String, java.lang.String);
public static net.minecraft.resources.Identifier parse(java.lang.String);
public static net.minecraft.resources.Identifier withDefaultNamespace(java.lang.String);
public static java.util.Optional<net.minecraft.resources.Identifier> tryParse(java.lang.String);
public java.lang.String getPath();
public java.lang.String getNamespace();
```

⇒ 26.1.2 里 `ResourceLocation` 已整体改名为 `net.minecraft.resources.Identifier`，`fromNamespaceAndPath(String,String)` 依然是同名同形方法。

再核手册侧签名，确认改完真的能命中方法（实装 jar `run/26.1.2/prodgame/mods/[帕秋莉手册] patchouli-neoforge-26.1-94.jar`，672,884 B）：

```text
PatchouliAPI.get()  ->  PatchouliAPI$IPatchouliAPI
  public abstract void openBookGUI(net.minecraft.resources.Identifier);
  public abstract void openBookGUI(net.minecraft.server.level.ServerPlayer, net.minecraft.resources.Identifier);
```

⇒ 用例里的两参「不带玩家」调用对应的就是 `openBookGUI(Identifier)` 这个重载，`Identifier` 正是它要的类型，所以这处改名不只是「消掉报错」，而是让断言真的能走到方法体。

### 5.2 改法（先备份，后单行替换）

- 备份：`scripts/test/cases/RELEASE150-26.1.2.json.bak-guidefix-20261102-044824`
- 改动：仅 step 27 的 `code` 里 `'net.minecraft.resources.ResourceLocation'` → `'net.minecraft.resources.Identifier'`，1 处
- 文件：16,215 B → 16,209 B（LF 保持，无 CRLF 混入）
- sha256：`010d68e81d73e3cfb1ecae5d35e98a03bd8f0a377973b9e13ef8c8b85fe970a7` → `04e09f59f928c536eb9aa4bfa5bc93c668057ac97b8976903ac96a3ee17150e6`
- `asserts` 与 step 数量**逐字节未变**（改前脚本自检：`assert len(a["asserts"]) == len(b["asserts"])`、`json.dumps(asserts)` 全等 ⇒ 没有删断言、没有放宽）

```diff
--- scripts/test/cases/RELEASE150-26.1.2.json.bak-guidefix-20261102-044824	2026-11-02 03:44:52.917933200 +0800
+++ scripts/test/cases/RELEASE150-26.1.2.json	2026-11-02 04:48:24.330139900 +0800
@@ -147,7 +147,7 @@
     {
       "op": "eval_js",
       "side": "client",
-      "code": "(function () {\n  var out = 'skip';\n  try {\n    var API = Java.loadClass('vazkii.patchouli.api.PatchouliAPI');\n    var RL = Java.loadClass('net.minecraft.resources.ResourceLocation');\n    API.get().openBookGUI(RL.fromNamespaceAndPath('astral_dice', 'astral_guide'));\n    out = 'ok';\n  } catch (e1) { out = 'ERR:' + e1; }\n  console.info('[REL150] guide_open=' + out);\n  return out;\n})()",
+      "code": "(function () {\n  var out = 'skip';\n  try {\n    var API = Java.loadClass('vazkii.patchouli.api.PatchouliAPI');\n    var RL = Java.loadClass('net.minecraft.resources.Identifier');\n    API.get().openBookGUI(RL.fromNamespaceAndPath('astral_dice', 'astral_guide'));\n    out = 'ok';\n  } catch (e1) { out = 'ERR:' + e1; }\n  console.info('[REL150] guide_open=' + out);\n  return out;\n})()",
       "timeout_ms": 20000,
       "save_as": "guide"
     },
```

改后 step 27 的完整脚本（供对照）：

```js
(function () {
  var out = 'skip';
  try {
    var API = Java.loadClass('vazkii.patchouli.api.PatchouliAPI');
    var RL = Java.loadClass('net.minecraft.resources.Identifier');
    API.get().openBookGUI(RL.fromNamespaceAndPath('astral_dice', 'astral_guide'));
    out = 'ok';
  } catch (e1) { out = 'ERR:' + e1; }
  console.info('[REL150] guide_open=' + out);
  return out;
})()
```

> 说明：本用例的 `asserts` 里没有针对 `guide`/`guide_open` 的断言，也没有 `release150_guide` 的 vision 断言（vision 只覆盖 `abyss_reagent_tooltip` 与 `chip_shop_ui`）。本次**没有**新增断言——加断言会改变用例语义，超出本条授权；step 27 的作用仍是把 `guide_open=<结果>` 打进日志留证。

### 5.3 validate 输出

```text
$ pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case scripts/test/cases/RELEASE150-26.1.2.json
MT_VALIDATE: OK — RELEASE150-26.1.2
EXIT=0
```

（`mt_case.ps1` 的 `Invoke-MtCaseValidate`（`scripts/test/mt_case.ps1:3124-3149`）是纯 schema 校验，不触达游戏，因此这条命令只证明用例结构合法；step 27 的真实结果仍需实机跑一次 `run` 才能看到 `guide_open=ok`。）

---

## 6. 其它线同类写法（只读核对，未改）

含 `net.minecraft.resources.ResourceLocation` 的地方（**对 1.20.1/1.21.1 而言是正确的类名**，26.1.2 才是改名的那一个）：

| 文件 | 说明 |
|---|---|
| `scripts/test/cases/AUTOJS-SMOKE-1.21.1.json` | 1.21.1 用例 |
| `scripts/test/cases/JILL-HEAL-PERCARD-1.21.1.json` | 1.21.1 用例 |
| `scripts/test/cases/RELEASE150-1.21.1.json` | 1.21.1 用例 |
| `scripts/test/cases/RELEASE150-1.20.1.json` | 1.20.1 用例 |
| `scripts/test/cases/RELEASE150-fabric-1.20.1.json` | fabric 1.20.1 用例 |
| `scripts/test/fabric/astral_fabric_curio_probe.js` | fabric 探针 |

结论：这些线的 MC 版本里 `net.minecraft.resources.ResourceLocation` 仍然存在，`Java.loadClass(...)` 能正常解析 ⇒ **不改**（未经允许也不该改）。

---

## 7. 未做 / 需要 owner 决定

1. **实机复验**：本次只做到「构建 + 开 jar 静态复核 + 用例 validate」，没有启动 26.1.2 客户端（`run/**` 由另一个 agent 占用）。要确认 WARN 消失，需在实机跑一次：新日志里应**没有** `Failed to register Waystone teleport integration`，且 DEBUG 级别能看到 `Waystone teleport integration enabled for 跃迁引擎.`（INFO）——即走了 `$Before` 分支且三条注册全成功。
2. **step 27 行为复验**：用例 `run` 后日志应出现 `[REL150] guide_open=ok`（而不是 `ERR:...`）。
3. **1.20.1 两线的 Waystones 2.x 风险**：本机无实物，无法判定；若后续给它们装 2.x，按第 2.3 节的备注整段搬候选列表即可。
4. **旧 jar 保留**：`build/libs` 里旧 sha256 的产物已被新构建覆盖（历史版本 jar 仍在同目录）。若要留档旧件，需要从 git/其它副本取回。

