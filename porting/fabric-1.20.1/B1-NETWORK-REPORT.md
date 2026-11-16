# B1-NETWORK 移植报告：筹码商店网络层 + 激光光束特效包（fabric-1.20.1）

> 批次：B1-NETWORK　基准：forge-1.20.1（主，同 MC 版本同路径同名文件）／neoforge-1.21.1（仅语义对照，未搬任何 1.21 专属 API）
> 迁移线：fabric-1.20.1（Fabric API 0.92.12 / officialMojangMappings / Java 17 / Loom）　报告日期：2026-11-01

## 0. 结论摘要

- **6 个文件**：新建 5 个（4 条消息 + 光束渲染器）、改 1 个（`network/ModNetwork.java` 追加 4 条注册）。
- 载荷与渲染**语义逐行 1:1**：剥离 import／注解／注释／空行后，forge↔fabric 的 unified diff **5/5 全空**（脚本比对，见 §2）。
- 协议 id：fabric 追加为 **12 / 13 / 14 / 15**，依次对应 forge 的 **21 / 22 / 23 / 26**（顺序一致；绝对号随其它批次追加而顺延，见 §3）。
- **未接线（按硬约束留给 Lead）**：`client/BeamFxRenderer` 需在 `AstralDiceClient#onInitializeClient()` 显式登记一行（§6）。客户端/服务端**收包器无需接线**，`SimpleChannel` 已按 EnvType 自动安装。
- **未编译**（硬约束：本任务不跑 gradle）。当前有 2 个未解析类型、分属并行批次：`shop/ChipShopMenu`（2 个方法）与 `client/ChipShopClientState`（1 个方法），见 §7。

## 1. 交付文件

路径均相对 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`。

| # | 文件 | 行数 | forge 对应行数 | 性质 |
|---|---|---|---|---|
| 1 | `network/ChipShopBuyPayload.java` | 54 | 52 | 新建，1:1（+2 行 javadoc 平台说明） |
| 2 | `network/ChipShopRefreshPayload.java` | 39 | 36 | 新建，1:1（+3 行 javadoc） |
| 3 | `network/ChipShopDenyPayload.java` | 50 | 48 | 新建，1:1（+2 行 javadoc） |
| 4 | `network/BeamFxPayload.java` | 79 | 72 | 新建，1:1（+7 行 javadoc） |
| 5 | `client/BeamFxRenderer.java` | 272 | 266 | 新建，1:1（删类注解，+1 段 javadoc 说明 seam） |
| 6 | `network/ModNetwork.java` | 572（原 558） | —（改既有） | **改**：L66-79 追加 4 条 `registerMessage` + 6 行注释 |

`AstralDiceMod.java` / `AstralDiceClient.java` **未改动**（硬约束）。assets 侧无需新增：`textures/special/blank.png` 本线已存在。

## 2. 自检结果（可复现）

| 判据 | 结果 |
|---|---|
| 行尾 / BOM | 6 文件全部 **CRLF**（每文件 CRLF 计数 == LF 计数）、**无 BOM** |
| 禁用 import | 本批 5 个新建文件 + ModNetwork **0 命中** `net.minecraftforge.|net.neoforged.` |
| 第三方饰品库 | 本批文件 **0 命中** `trinkets|wispforest`（未触碰饰品链路） |
| 1.21 网络 API | 本批文件**无 import**（javadoc 里为解释「为何不用」而提到 `StreamCodec` / `CustomPacketPayload`，与既有 `ModNetwork.java:180` 同风格，无引用） |
| 语义 1:1 | 剥 import/注解/注释/空行后 forge↔fabric `unified_diff` **5/5 全空** |

注：全 `fabric-1.20.1/src` 里 `net.minecraftforge.|net.neoforged.` 共有 4 处命中，全部是**既有垫片类的 javadoc**（`platform/fml/ModContainer`、`platform/fml/IModInfo`、`platform/event/OnDatapackSyncEvent`、`event/FirstLootChestHandler`），不是 import、不在本批 scope。

## 3. 协议 id 对照表

| fabric id | 消息类 | 方向 | forge id | 说明 |
|---|---|---|---|---|
| 12 | `ChipShopBuyPayload` | C→S | 21 | 购买第 N 个候选 |
| 13 | `ChipShopRefreshPayload` | C→S | 22 | 刷新候选（空载荷） |
| 14 | `ChipShopDenyPayload` | S→C | 23 | 拒绝 + 原因码 |
| 15 | `BeamFxPayload` | S→C | 26 | 一段激光/轨道炮光束 |

**顺序即协议**：4 条一律**追加在 12 条既有消息之后**（既有 id 0..11 一个未动、一行未重排），四条之间的相对顺序与 forge 的 21/22/23/26 **完全一致**。绝对号 12-15 只在本线当前注册数（12）下成立；若其它批次先追加，绝对号会整体后移 —— **相对顺序不变即可**，故本批与既有写法一致，使用 `id++` 递增、**未硬编码任何绝对号**。

## 4. 1:1 要点（逐文件）

### 4.1 ChipShopBuyPayload（C→S）
- 字段 `private final int offerIndex` + 构造器 + 访问器 `offerIndex()`；线格式 `writeVarInt` / `readVarInt`。
- `handle`：先 `enqueueWork(...)`，**再** `setPacketHandled(true)`（调用顺序照抄 forge）。
- 服务端守卫：`ServerPlayer sp = ctx.get().getSender(); if (sp != null && sp.containerMenu instanceof ChipShopMenu menu)` 才调 `menu.handleBuy(sp, msg.offerIndex)`；越界由 `handleBuy` 二次校验（注释已写明 `OFFER_SLOTS` 语义）。

### 4.2 ChipShopRefreshPayload（C→S）
- **无字段**：`encode` 不写任何字节，`decode` 返回 `new`（对应 1.21 的 unit 语义）。
- 同一 `instanceof ChipShopMenu` 守卫 → `menu.handleRefresh(sp)`。

### 4.3 ChipShopDenyPayload（S→C）
- 字段 `int reason`；`writeVarInt` / `readVarInt`；`handle` 直接 `ChipShopClientState.notifyDeny(msg.reason)`。
- 常量语义（0 `DENY_NO_COIN` / 1 `DENY_NO_REFRESH` / 2 `DENY_INVENTORY_FULL` / -1 `DENY_NONE` 不发）与「30t 红闪、花费文本认 0/2、N/N 文本认 1、码 2 自 2026-10-14 起不再是购买路径结果」全部按 forge 注释原样保留。

### 4.4 BeamFxPayload（S→C）
- 字段：`double fromX/fromY/fromZ/toX/toY/toZ` + `int colorArgb` + `float widthBlocks` + `int ttlTicks`。
- 线格式严格同序：6×`writeDouble` → `writeInt` → `writeFloat` → `writeVarInt`（**逐字节等价**）。
- `handle`：`if (FMLEnvironment.dist == Dist.CLIENT)` 才 FQN 调 `client.BeamFxRenderer.add(...)` 9 参（forge 同为 FQN，客户端类只在客户端分支解析，专用服务端不加载）。

### 4.5 BeamFxRenderer（客户端）
- 常量：`LASER_TEXTURE`=`astral_dice:textures/special/blank.png`、`BEACON_TEXTURE`=`minecraft:textures/entity/beacon_beam.png`、`BEACON_COLOR`=`0xFFCF4A`、`BEACON_V_TILE`=`8.0D`。
- `createBeam` 8 个 shard：`SHADER_ENTITY_TRANSLUCENT` / 贴图 / `TRANSPARENCY_ADDITIVE` / `DEPTH_TEST_LEQUAL` / `WRITE_MASK_COLOR` / `CULL_NONE` / `LIGHTMAP_ON` / `OVERLAY_ON`，`RenderType.create(name, NEW_ENTITY, QUADS, 256, false, true, ...createCompositeState(false))`。
- `States extends RenderStateShard` 的「protected-static 再导出」子类手法照抄（与 `client/RenShieldRenderer` 同一套，1.20.1 这些常量都是 protected static final）。
- `add()`：`mc.level == null` 早退；**按 from/to/颜色/线宽内容去重**，命中只把 `expireTick` 推后；`expireTick = gameTime + Math.max(1, ttlTicks)`；`synchronized (BEAMS)`。
- `onRenderLevelStage`：非 `Stage.AFTER_ENTITIES` 早退 → 剔除 `expireTick < now` → 空表早退（不取 BufferSource、不碰 GL）→ `pushPose` + `translate(-cam)` → 每条光束两片交叉四边形 → **只对用过的批次 `endBatch`** → `popPose`。
- 顶点细节：`alpha <= 0.01` 强制 `1.0`（本仓颜色常量是 0xRRGGBB，alpha 位为 0）；信标贴图 `v1 = len / 8.0`，激光 `v1 = 1.0`；`uv2 = LightTexture.FULL_BRIGHT`、`overlayCoords = NO_OVERLAY`、带 `normal`。
- 退化保护：`len < 1.0E-4` 跳过；视线与轴平行时换参照方向再叉积。

## 5. 偏差与 seam（逐条：forge 原样 → fabric 改法 + 理由）

| # | forge 原样 | fabric 改法 | 理由 |
|---|---|---|---|
| 1 | `net.minecraftforge.network.simple.SimpleChannel` + `NetworkEvent` | `platform/network/SimpleChannel` + `platform/network/NetworkEvent`（本线既有垫片） | 本线既有 12 条消息全走该垫片；线格式同为 `[VarInt messageId][payload]`；硬约束禁 1.21 payload/codec |
| 2 | `@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)` 自动订阅 | **删除类注解**，改由 `LoaderBus.INSTANCE.register(BeamFxRenderer.class)` 显式登记 | Fabric 无自动事件订阅；`LoaderBus` 是自建总线，只扫「目标类自己声明的带 `@SubscribeEvent` 的方法」（不递归内部类），必须显式登记 |
| 3 | `net.minecraftforge.client.event.RenderLevelStageEvent` | `platform/client/event/RenderLevelStageEvent` | 本线垫片，`Stage.AFTER_ENTITIES` 同名同值；派发源是 `FabricClientBridges.install()`（`WorldRenderEvents.AFTER_ENTITIES`），与 forge 的 after-entities 时点对应 |
| 4 | `net.minecraftforge.api.distmarker.Dist` + `net.minecraftforge.fml.loading.FMLEnvironment` | `platform/api/distmarker/Dist` + `platform/fml/loading/FMLEnvironment` | 同名同值（`CLIENT` / `DEDICATED_SERVER`），`dist` 由 `FabricLoader` 派生 ⇒ 判定语义不变 |
| 5 | 消息类为顶层 `final class`（非 record） | 保持顶层 `final class` | 1:1；本线既有消息为 `ModNetwork` 内部类，但新消息按 forge 原样落**独立文件**（任务要求同包同名），注册处用简单类名引用 |
| 6 | `ASTALDICE...` 无 | 文件行数 +2~+7 | 全部来自 javadoc 平台差异段落（说明 id 归属/为何不用 1.21 网络层），**代码行与 forge 一一对应** |

**无行为差异**：常量、冷却、tick 间隔、去重键、`max(1, ttl)`、`endBatch` 条件、编码顺序均未改动；本批没有任何「为了编译而改写语义」的地方（见 §7 的未解析符号是**保留 forge 签名**的结果，不是改用临时桩）。

## 6. 需主 Agent 接线（确切位置）

### 6.1 唯一必须接线项：客户端渲染器登记

文件 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceClient.java`，方法 `onInitializeClient()`，在**方法体第一行**（现 L34 `LoaderBus.INSTANCE.register(...ClientKeyNames.class...)` 之前）插入：

```java
LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.BeamFxRenderer.class);
```

- 该列表按类名**字母序**排列，`BeamFxRenderer` 排在 `ClientKeyNames` 之前，因此插在 L34 之前正好维持顺序；`LoaderBus` 已在 L3 import，无需新增 import（沿用 FQN 写法）。
- 传 `Class` 而非实例：`LoaderBus.register(Class)` 扫 static 方法；`BeamFxRenderer.onRenderLevelStage` 为 `public static`，与 `RenShieldRenderer`（L49 同写法）一致。
- **未接线的可观测症状**：`SubscriptionAudit.verifyClientSide()`（L63）会把 `BeamFxRenderer` 列为「带 `@SubscribeEvent` 却从未登记」——光束不显示、不抛异常。

### 6.2 无需接线（已自动生效）

- **网络注册**：`network/ModNetwork.java#register()` 已含 4 条（L66-79），随既有入口一次性完成。
- **客户端接收器**：`SimpleChannel` 在 `EnvType.CLIENT` 下自动装 `ClientReceiverHolder`；`BeamFxPayload.handle` 内的 `Dist.CLIENT` 守卫是**第二道**保险。
- **纹理资源**：`assets/astral_dice/textures/special/blank.png` 本线已存在，无需搬运。

## 7. 待并行批次落线（当前编译残留，已按 forge 签名调用，勿改）

| 缺失类型 | 本批引用点 | 并行批次需提供的确切签名（取自 forge-1.20.1） |
|---|---|---|
| `shop/ChipShopMenu` | `ChipShopBuyPayload.java:4,47,48`；`ChipShopRefreshPayload.java:4,32,33` | `public void handleBuy(ServerPlayer buyer, int offerIndex)`（forge `shop/ChipShopMenu.java:349`）；`public void handleRefresh(ServerPlayer buyer)`（forge `:431`） |
| `client/ChipShopClientState` | `ChipShopDenyPayload.java:3,46` | `public static void notifyDeny(int reason)`（forge `client/ChipShopClientState.java:234`）；另需 `denyBlinkRed()` 供屏幕侧 30t 红闪 |

落线时点补充（本报告生成时实测）：`shop/ChipShopState.java`（239 行）**已在**（并行批次已落），`shop/ChipShopMenu.java` 与 `client/ChipShopClientState.java` **尚不在**。故 `:fabric-1.20.1:classes` 当前预期报 **3 个文件 / 2 个未解析类型**，与本批无关。

## 8. 无法判定项

1. **编译真实性**未验（硬约束禁跑 gradle）。除 §7 两处未解析类型外，本批未发现其它可疑符号；`RenderType.CompositeState` / `RenderStateShard` 受保护常量 / `renderBuffers().bufferSource()` 均为 1.20.1 真实 API，且与同线 `RenShieldRenderer` 用法同源。
2. **渲染观感**只在 forge 线有实机经验；fabric 侧需实机看一次（交叉四边形 + 加法混合在 iris/光影下的表现未测）。
3. **绝对 id 归属**：12-15 只在「本批紧随既有 12 条」时成立；若其它批次先追加，绝对号后移但相对顺序不变。forge 侧 21/22/23/26 的绝对号同理依赖 forge 线自身注册顺序。
4. `ChipShopDenyPayload` 的**最终客户端落点**（红闪实现、屏幕文本判定）由并行批次决定；本批只负责按 forge 签名调用 `notifyDeny(int)`。
