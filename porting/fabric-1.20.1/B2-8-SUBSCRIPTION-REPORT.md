# B2-8 Fabric 事件订阅者补登记报告

- 批次：B2-8（Fabric 1.20.1 自建总线 LoaderBus 补齐 `@Mod.EventBusSubscriber` 语义）
- 工作树：F:/MCProject/astral_dice_multiloader（分支 multi-main）
- 结论：11 个候选中 **4 个登记**（全部服务端）、**7 个判定不该登记**（零 `@SubscribeEvent` 方法，纯注释误报）；客户端 0 新增，未触碰 `AstralDiceClient.java` / `client/ModClientEvents.java`。

## 1. 定性判据（三步，禁猜）

1. 该类**是否真有** `@SubscribeEvent` 方法（运行时口径 = `SubscriptionAudit.hasSubscribeEventMethod`：`getDeclaredMethods()` + `isAnnotationPresent` + 参数数 1，见 `platform/event/SubscriptionAudit.java:173-188`）。注释里的字符串不算。
2. 基线（forge-1.20.1 / neoforge-1.21.1 / neoforge-26.1.2）同路径类是否靠类级注解自动订阅（含全限定写法 `@net.minecraftforge.fml.common.Mod.EventBusSubscriber`），或是否在别处显式注册。基线没有任何注册途径的，不发明新激活路径。
3. 侧判定：客户端专属类只进客户端登记点；服务端类进服务端登记点。

## 2. 逐候选判定表

| 候选 | 类内 @SubscribeEvent 方法（fabric 行号） | 基线注册途径 | 侧 | 判定 | 处置 |
|---|---|---|---|---|---|
| event/CurioDeathKeepHandler | `@SubscribeEvent(priority=LOWEST) onPlayerDrops(LivingDropsEvent)` :66-67；`onPlayerClone(PlayerEvent.Clone)` :99-100 | forge :32 `@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)`；neo1.21.1 :32、neo26.1.2 :32 `@EventBusSubscriber` | 服务端 | 真订阅；漏登记 = 死亡饰品照常掉落且重生不归还 | **登记** AstralDiceMod.java:153-155 |
| event/EventAnimationLock | `onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent)` :108-109；`onPlayerDeath(LivingDeathEvent)` :113-114 | forge :36；neo :38 / :38 `@EventBusSubscriber` | 服务端 | 真订阅；漏登记 = 弹卡窗口内死亡/掉线后操作永久冻结 | **登记** :158-160 |
| event/EventChoiceManager | `onPlayerTick(TickEvent.PlayerTickEvent)` :298-299；`onPlayerDeath` :306-307；`onPlayerLogout` :315-316；`onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent)` :331-332 | forge :51；neo :43 / :43 `@EventBusSubscriber`；全仓 grep 确认**无**别处显式 register（只命中注释与调用点） | 服务端 | 真订阅；类头 :49-52 自述「本线必须显式登记」；漏 = 15s 等待期永不推进、超时/死亡/登出/换维度都不结算 | **登记** :161-163 |
| event/EventEffects | `onPlayerTick(TickEvent.PlayerTickEvent)` :431-432（驱动 EventCardService.drain + 定时器清扫）；`onHeal(LivingHealEvent)` :449-450（isHealBlocked → setCanceled(true)） | forge :52 全限定 `@net.minecraftforge.fml.common.Mod.EventBusSubscriber(...)`；neo1.21.1 :52、neo26.1.2 :55 | 服务端 | 真订阅；`AstralEventRegistry` 里的 `EventEffects::redHeatWarning` 是**效果方法引用**，不替代订阅；漏 = 弹卡后永不结算 | **登记** :164-166 |
| event/GlobalEventGate | 无（全文只有 :42 注释提到 `@SubscribeEvent`） | forge :152-154 `onServerStopping(ServerStoppingEvent)`→`reset()`；neo :159-161 同 | 服务端 | 本线**无订阅方法**；停服清零已下移到 `platform/FabricBridges.java:88-94` 的 SERVER_STOPPING 回调（:93 `GlobalEventGate.reset()`） | 不登记 |
| event/RepulsionCreeperGuard | 无（:77 是无注解静态入口 `public static boolean handleExplosionStart(Level, Entity, LivingEntity)`） | forge :65 `@Mod.EventBusSubscriber(..., bus = Bus.FORGE)` + :75-76 `onExplosionStart(ExplosionEvent.Start)`；neo :50 | 服务端 | 本线缺 ExplosionEvent 垫片（`platform/event/level` 只有 BlockEvent，mixin 清单无爆炸桥）⇒ 登记也无钩子可接，属跨批次缺口 B-10 | 不登记 |
| item/sign/LuluSignItem | 0 个（:57-58 自述不订阅） | forge/neo 同路径同样无注解 | 服务端 | 注释误报 | 不登记 |
| item/sign/MegasSignItem | 0 个（:50 自述不订阅） | 同上 | 服务端 | 注释误报 | 不登记 |
| item/sign/MosesSignItem | 0 个（:42-45 自述不订阅，并记录 NeoForge 21.1 对「无订阅方法的类 register」抛 IllegalArgumentException） | 同上 | 服务端 | 注释误报 | 不登记 |
| item/sign/RenSignItem | 0 个（:45-46 自述不订阅） | 同上 | 服务端 | 注释误报 | 不登记 |
| client/ModernUITooltipCompat | 0 个（:26 注释描述的是 **Modern UI 自己**在 Forge 线用平台事件） | fabric 走 mixin：`fabric.mod.json` 声明 `mixins.modernui-fabric.json`，`MixinGuiGraphics` 注入 `net.minecraft.client.gui.GuiGraphics` | 客户端 | 不是订阅类；**不存在「条件登记」问题**——启用/停用由 mixin 配置 + ModCommonConfig 决定，不进 LoaderBus | 不登记 |

## 3. 落地改动（唯一改动文件）

`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java` 的 `registerListeners()`（:140）内**纯追加 12 行**（4 条登记 + 每条 2 行动机注释），插在既有字母序位置：

```java
// 紧接 :152 ChipDamageHandler 之后
// B2-8 批次:死亡保底 —— onPlayerDrops(LivingDropsEvent, LOWEST) 决定「死亡不掉落饰品」、
// onPlayerClone(PlayerEvent.Clone) 决定「重生时放回饰品」;漏登记 = 两条都静默失效(饰品掉落且不归还)。
LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.CurioDeathKeepHandler.class);
// 紧接 :157 EnderDiceHandler 之后（三条 Event* 类，注释见文件 :158-165）
LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.EventAnimationLock.class);
LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.EventChoiceManager.class);
LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.EventEffects.class);
```

- 插入后顺序仍是首字母序：ChipDamage(:152) → CurioDeathKeep(:155) → DamageEffectCard(:156) → EnderDice(:157) → EventAnimationLock(:160) → EventChoiceManager(:163) → EventEffects(:166) → FirstLootChest(:167)。
- 服务端 `LoaderBus.INSTANCE.register` 调用点 67 → **71**；客户端 `AstralDiceClient.java` 保持 14，本批 0 新增。
- 未改动任何已登记条目、方法签名、包结构；文件实测 `crlf 349 / bare_lf 0 / bom False`。

## 4. 登记后钩子确实有派发源（不是「登记了但没人 post」）

| 订阅方法入参 | fabric 侧派发点 |
|---|---|
| `LivingDropsEvent` | `platform/PuzzlesBridges.java:115-117`（LivingDropsCallback = `dropAllDeathLoot` TAIL） |
| `LivingDeathEvent` | `platform/PuzzlesBridges.java:110` |
| `PlayerEvent.Clone` | `platform/PuzzlesBridges.java:198` |
| `PlayerEvent.PlayerLoggedOutEvent` | `platform/FabricBridges.java:151` |
| `PlayerEvent.PlayerChangedDimensionEvent` | `mixin/bridge/ServerPlayerDimensionTravelBridgeMixin.java:68` |
| `LivingHealEvent` | `mixin/bridge/LivingHealBridgeMixin.java:30` |
| `TickEvent.PlayerTickEvent(END)` | `platform/FabricBridges.java:142` |

## 5. 自检原始输出

### 5.1 `python scripts/__sub_audit.py`

改动前：`subscribe_files 91 registered 73 unregistered 18`

改动后原始输出：
```
subscribe_files 91 registered 77 unregistered 14
UNREG fabric-1.20.1/src/main/java\com\merlinkitsune\astral_dice\AstralDiceClient.java
UNREG fabric-1.20.1/src/main/java\com\merlinkitsune\astral_dice\AstralDiceMod.java
UNREG fabric-1.20.1/src/main/java\com\merlinkitsune\astral_dice\platform\event\Event.java
UNREG fabric-1.20.1/src/main/java\com\merlinkitsune\astral_dice\platform\client\FabricClientBridges.java
UNREG fabric-1.20.1/src/main/java\com\merlinkitsune\astral_dice\event\GlobalEventGate.java
UNREG fabric-1.20.1/src/main/java\com\merlinkitsune\astral_dice\platform\event\IEventBus.java
UNREG fabric-1.20.1/src/main/java\com\merlinkitsune\astral_dice\item\sign\LuluSignItem.java
UNREG fabric-1.20.1/src/main/java\com\merlinkitsune\astral_dice\item\sign\MegasSignItem.java
UNREG fabric-1.20.1/src/main/java\com\merlinkitsune\astral_dice\client\ModernUITooltipCompat.java
UNREG fabric-1.20.1/src/main/java\com\merlinkitsune\astral_dice\item\sign\MosesSignItem.java
UNREG fabric-1.20.1/src/main/java\com\merlinkitsune\astral_dice\item\sign\RenSignItem.java
UNREG fabric-1.20.1/src/main/java\com\merlinkitsune\astral_dice\event\RepulsionCreeperGuard.java
UNREG fabric-1.20.1/src/main/java\com\merlinkitsune\astral_dice\platform\event\SubscribeEvent.java
UNREG fabric-1.20.1/src/main/java\com\merlinkitsune\astral_dice\platform\event\SubscriptionAudit.java
```

剩余 14 项构成：4 个平台垫片自身（`platform/event/{Event,IEventBus,SubscribeEvent,SubscriptionAudit}.java`）+ 2 个入口类（脚本按 `".AstralDiceMod.class"` 匹配不到 `register(AstralDiceMod.class)`，入口类永远算未登记）+ 1 个客户端垫片 `platform/client/FabricClientBridges.java` + 本批判定不登记的 7 项。**未登记数 18 → 14，减掉的正是本批 4 条。**

口径差异（重要）：python 脚本是**纯文本**匹配（注释里的 `@SubscribeEvent` 也算），运行时 `SubscriptionAudit` 走反射注解，那 7 项在运行时不构成「未登记」告警。

### 5.2 `python scripts/__javac_check.py`

改动前（18:44，原始输出）：
```
EXIT 1 ERRORS 170 jars 684 srcs 508
  163  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 978
```

改动后（同一条命令、当前工作树，原始输出）：
```
EXIT 1 ERRORS 170 jars 685 srcs 516
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  163  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 978
```

⇒ **REAL 分桶 0 → 0，未新增任何 REAL 错误**；错误总数 170 与基线逐项相同，`errors.txt` 行数 978 一致。`jars 684→685`、`srcs 508→516` 是并行批次新增源码/依赖所致（本批只加 12 行、未加任何文件）。

时间窗说明（如实记录）：18:45–18:50 期间**同一命令**曾返回 `EXIT 4 ERRORS 5000`（5000 条全是 `package net.minecraft.* does not exist`），根因是并行批次 B-BUILD-LOOM 把 `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/` 清空（即 `porting/fabric-1.20.1/B-BUILD-LOOM-REPORT.md:128` 建议的「删掉让它重来」动作）。期间我用工程内 loom-cache 同名 artifact（同 mappings hash）复测，结果同样是 `EXIT 1 ERRORS 170 / REAL 0 / 978`；18:50 该 jar 重建为 26,688,459 B 后原脚本恢复正常，两条路径结论一致。复测脚本留在 `fabric-1.20.1/build/__javac_b28.py`（`build/` 已 gitignore）。

遗留风险：该脚本是**未跟踪**临时脚本（`?? scripts/__javac_check.py`），MC jar 路径硬编码单候选，缓存被清时会把「classpath 缺失」误报成上千条 REAL。建议改成多候选回退（复测脚本即该写法）；我未改它，避免与其他批次撞车。

### 5.3 `git status --porcelain`

本批只改一个已跟踪文件（该文件在本批开始前已是 `M`，来自并行批次）：
```
 M fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java
```
本批自身两条 `git status` 记录：上面这条 `M`（代码改动）+ `?? porting/fabric-1.20.1/B2-8-SUBSCRIPTION-REPORT.md`（本报告）。`git diff --stat` 对该文件 = `52 insertions(+)`，其中 40 行属此前批次、**12 行是本批**；全工作树改动数本批开始时 329 条、收尾时 334 条（并行批次在推进），除上面这一条 `M` 外全部不是本批所改。`client/ModClientEvents.java` 仍为 `M` 且内容确为 B2-6 的 `EventCardOverlay` 注册（:44），本批**没有**修改它。

## 6. 存疑 / 建议裁决

1. **EventChoiceManager 与 choice/CardChoiceManager 并存**：语义不同（前者 = 事件牌选卡会话 15s 超时，后者 = 卡牌商店选卡），都已在册，无重复结算；但 `event/EventChoiceManager` 的四个处理器在 fabric 侧此前**从未执行过**，本批只恢复订阅面，其与 `EventCardService`（:86 `EventChoiceManager.cancelFor(sp, "animation")`）的时序**仍未实测**（禁启动游戏）。
2. **RepulsionCreeperGuard 是本线真实功能缺口（B-10），不是漏登记**：基线靠 `ExplosionEvent.Start` 订阅；fabric 既无该事件垫片也无 mixin 桥，`handleExplosionStart(...)` 目前**无调用方**。按「不发明新激活路径」不动它，建议单独立项补桥。
3. **GlobalEventGate 的停服清零只剩一条路径**：`platform/FabricBridges.java:88-94` 的 SERVER_STOPPING 回调。若该 `installEarly` 未被调用，闸门到期刻（game time 绝对值）会跨会话残留。该回调调用点本批未核（属 B2-2 范围）。
4. **「顺手都登记」在本线不报错，但会把审计判据做成假绿**：NeoForge 21.1 对无订阅方法的类 register 会抛 IllegalArgumentException（`MosesSignItem:44` 注释有实测记录），而 fabric `LoaderBus.scan`（`platform/event/LoaderBus.java:130-175`）对这种情况静默无害，只把类塞进 `registeredOwners`。故本批对 7 个非订阅类明确不登记。
5. **javac 自检脚本本身不可复现**：`scripts/__javac_check.py` 是未跟踪临时脚本（`?? scripts/__javac_check.py`），MC jar 路径硬编码且已被并行批次清空，后续批次重跑同命令会看到 5000 REAL 的假红。建议把 mc jar 解析改成多候选回退（即 §5.2 写法）；我未改该脚本，避免与其他批次撞车。
