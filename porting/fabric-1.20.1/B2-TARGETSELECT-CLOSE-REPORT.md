# B2 缺陷 C-2 报告：fabric-1.20.1 补齐 TargetSelectClose 关闭包

- 仓库：`D:/MCProject/astral_dice_multiloader`（Windows 原生 / Git Bash），分支 `multi-main`，HEAD `fd990c15`
- 批次：B2 / 缺陷 C-2（服务端强拆选择会话时缺关闭包）
- 日期：2026-11-01
- 结论：**4 处源码改动全部落地，`:fabric-1.20.1:compileJava` 通过（含一次 `--rerun-tasks` 全量重编）；剩余仅「客户端是否真的退出选择模式」需实机确认。**

## 0. 摘要

| 项 | 结果 |
|---|---|
| 改动处数 | 4（ModNetwork / TargetSelectionManager / TargetSelectionClient / EventCardService） |
| 新增协议 payload | `TargetSelectCloseMessage`，本线 **id 30**（追加在注册表末尾） |
| 与 forge 源码等价 | 三段方法体与 forge **逐字节相同**（diff 空） |
| 与 forge 线格式等价 | `encode`/`decode` 字节码指令与常量池索引**完全一致**（均 VarInt token） |
| 编译 | `BUILD SUCCESSFUL in 2s`（增量）/ `BUILD SUCCESSFUL in 4s`（`--rerun-tasks` 全量重编） |
| 实机 | **未做**（本任务硬约束：不启动游戏） |

## 1. 缺陷定义与基线

### 1.1 现象（补齐前）

`event/EventCardService.showThenRun` 在弹卡那一刻要强拆玩家**已打开**的目标选择会话（用户裁决 D5）。fabric 线当时没有「服务端强制取消」重载，只能借测试辅助清场入口；该路径清掉服务端会话但**不发关闭包**，于是客户端仍停在选择模式：吞鼠标、左键被当确认，玩家在事件卡动画期间会误触出牌。

### 1.2 参照实现（forge-1.20.1，已实证）

| 位置 | 内容 |
|---|---|
| `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:119-120` | `CHANNEL.registerMessage(id++, TargetSelectCloseMessage.class, ...)`（该线第 29 条 payload = id 28） |
| `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:464-491` | `public static class TargetSelectCloseMessage`（字段 `private final int token` + 构造器 + encode/decode/handle） |
| `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/target/TargetSelectionManager.java:301-319` | `public static void cancel(ServerPlayer player, String reason)` |
| `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/TargetSelectionClient.java:383-398` | `public static void closeFromServer(int closeToken)` |
| `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/EventCardService.java:78` | 调用点 `TargetSelectionManager.cancel(sp, "animation")` |

neoforge-1.21.1 线另有独立文件 `network/TargetSelectClosePayload.java`，语义相同。

## 2. 改动清单（文件:行号）

全部改动均在 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/` 下，未触碰任何其它文件。

| # | 文件:行号 | 改动 |
|---|---|---|
| 1 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:123-128` | 新增注释 + `CHANNEL.registerMessage(id++, TargetSelectCloseMessage.class, TargetSelectCloseMessage::encode, TargetSelectCloseMessage::decode, TargetSelectCloseMessage::handle)`；位置：`VersionReplyMessage`（id 29）之后、`VersionHandshake.install()` 之前 |
| 1b | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:464-503` | 新增 `public static class TargetSelectCloseMessage`（含 javadoc）；位置：既有 `TargetSelectCancelMessage` 类之后、`// === 末影骰子不死图腾动画(S→C) ===` 之前 |
| 2 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/target/TargetSelectionManager.java:291-313` | 新增 `public static void cancel(ServerPlayer player, String reason)`（含 javadoc 291-301）；位置：既有 `cancel(ServerPlayer, int)`（现 :316）之前。既有 `cancel(ServerPlayer,int)` 与 `cancelSessionForTests`（:158）**未改一字** |
| 3 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/TargetSelectionClient.java:371-390` | 新增 `public static void closeFromServer(int closeToken)`（含 javadoc 371-381）；位置：`cancel(String)` 之后、`private static void deactivate()`（:392）之前；复用既有 `deactivate()` |
| 4 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/EventCardService.java:35-40` + `:88-96` | 类级「平台差异」javadoc 由「本线无该重载」改为「自 2026-11-01 起已补齐（缺陷 C-2），调用形式与 forge 逐字一致，只差协议 id（本线 30 / forge 28）」；调用点由测试辅助入口改为 `com.merlinkitsune.astral_dice.target.TargetSelectionManager.cancel(sp, "animation")`，并写明清会话 + 清门控 + 发关闭包、不写抑制闩 |

### 2.1 逐处改动要点

**ModNetwork（发包侧 / 注册）** — 类体与 forge `:471-491` 逐字节相同：

```java
public static class TargetSelectCloseMessage {
    private final int token;
    public TargetSelectCloseMessage(int token) { this.token = token; }
    public static void encode(TargetSelectCloseMessage msg, FriendlyByteBuf buf) { buf.writeVarInt(msg.token); }
    public static TargetSelectCloseMessage decode(FriendlyByteBuf buf) { return new TargetSelectCloseMessage(buf.readVarInt()); }
    public static void handle(TargetSelectCloseMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                com.merlinkitsune.astral_dice.client.TargetSelectionClient.closeFromServer(msg.token));
        ctx.get().setPacketHandled(true);
    }
}
```

与既有三件套（`TargetSelectStartMessage` / `Confirm` / `Cancel`）同一内部类风格、同一 `handle` 写法（`enqueueWork` + `setPacketHandled(true)`），因此**不引入新的线程语义**。

**TargetSelectionManager（服务端强拆）** — 语义链：取会话 → 无则返回 → 移出 `SESSIONS` → 发关闭包 → `SignSelectionGate.clear` → debug 日志（带 `reason`）。**不写 `HOLD_SUPPRESSED` 抑制闩**，故动画窗结束后玩家仍手持效果牌时会自然自动重开选择界面（D5 与 neo/forge 同口径）。

**TargetSelectionClient（客户端收包）** — `token` 匹配且本地 `active` 才 `deactivate()`；不匹配只 debug 忽略，避免「旧包误关动画窗内刚开的新会话」。**不回发**取消包（服务端已拆完，回发会产生无主 token 的取消）。清理复用既有 `deactivate()`（清 `active`/目标快照/`allowSelf`/`holdToSelect` + `mc.options.keyAttack.setDown(false)`），**未另写状态机**。

**EventCardService（调用点）** — 由「借测试辅助入口」改为与 forge `EventCardService.java:78` 逐字一致的调用形式；注释由 TODO-BRIDGE 改为「已补齐（2026-11-01，缺陷 C-2）」。

### 2.2 文件卫生

```
ModNetwork.java:            bytes=39173 BOM=False CRLF=795 bare_LF=0 bare_CR=0 lines=795
TargetSelectionManager.java: bytes=26273 BOM=False CRLF=416 bare_LF=0 bare_CR=0 lines=416
TargetSelectionClient.java:  bytes=42960 BOM=False CRLF=761 bare_LF=0 bare_CR=0 lines=761
EventCardService.java:       bytes=10266 BOM=False CRLF=153 bare_LF=0 bare_CR=0 lines=153
```

（四个文件均 UTF-8 无 BOM、全 CRLF、无裸 LF / 无裸 CR。）

## 3. 协议 id 分配：决策与对照

### 3.1 决策：**追加在注册表末尾（id 30）**

`ModNetwork.register()` 用 `id++` 顺序分配，**顺序即协议**。当时共 30 条（id 0..29），下一个空闲 id = 30。

选「末尾追加」而非「插在 TargetSelect 三件套（id 5/6/7）之后」，理由：

1. 插队会让既有 **id 8..29 共 22 条** payload 全部顺移。线格式是 `[VarInt messageId][payload]`，id 一变，旧客户端 / 旧服务端互连就会把包按错类型解码 —— 直接错包、乃至解码异常。
2. 追加只新增一个 id：旧端收到未知 id 30 时，`SimpleChannel` 的接收侧 `byId.get(messageId) == null` 会**静默 return**（见 `platform/network/SimpleChannel.java` 的 `installReceivers` 与 `ClientReceiverHolder.install`），既不错包也不崩 —— 等价于修复前行为，**零回归**。
3. forge 线同样采取「末尾追加」（它当时是最后一条 ⇒ id 28），两线策略一致；差异只剩协议序号，字段序列逐字节同构。

### 3.2 id 对照表（实测：`grep -n 'CHANNEL.registerMessage(id++' ModNetwork.java` 共 31 条）

| id | payload | 备注 |
|---|---|---|
| 0 | `DamageNumberMessage` | 既有 |
| 1 | `ActionBarMessage` | 既有 |
| 2 | `SignActivateMessage` | 既有 |
| 3 | `OpenCardInventoryMessage` | 既有 |
| 4 | `AttachmentSyncMessage` | 既有 |
| 5 | `TargetSelectStartMessage` | 既有 |
| 6 | `TargetSelectConfirmMessage` | 既有 |
| 7 | `TargetSelectCancelMessage` | 既有 |
| 8 | `EnderDieTotemMessage` | 既有 |
| 9 | `StarCoinWalletMessage` | 既有 |
| 10 | `StarCoinBalanceMessage` | 既有 |
| 11 | `RenShieldStateMessage` | 既有 |
| 12 | `ChipShopBuyPayload` | 既有 |
| 13 | `ChipShopRefreshPayload` | 既有 |
| 14 | `ChipShopDenyPayload` | 既有 |
| 15 | `BeamFxPayload` | 既有 |
| 16 | `CardShopTransferListMessage` | 既有 |
| 17 | `CardShopTransferMessage` | 既有 |
| 18 | `CardShopBuyMessage` | 既有 |
| 19 | `CardShopDenyMessage` | 既有 |
| 20 | `CardChoiceConfirmMessage` | 既有 |
| 21 | `CardChoiceCloseMessage` | 既有 |
| 22 | `EventCardMessage` | 既有 |
| 23 | `EventChoiceStartMessage` | 既有 |
| 24 | `EventChoiceConfirmMessage` | 既有 |
| 25 | `EventChoiceCloseMessage` | 既有 |
| 26 | `EventChoiceTickMessage` | 既有 |
| 27 | `EventChoiceCancelMessage` | 既有 |
| 28 | `VersionProbeMessage` | 既有（B22） |
| 29 | `VersionReplyMessage` | 既有（B22） |
| 30 | `TargetSelectCloseMessage` | **本批新增** |

**新旧 id 对照**：既有 0..29 **一条都没有移动**；新增只占 30。forge 同一条消息在其线上是 **id 28**（第 29 条），本线是 **id 30**（第 31 条）—— 差异仅来自两线既有注册条数不同，**不是**字段或语义差异。

## 4. 与 forge 的逐行等价说明

### 4.1 源码级：三段方法体逐字节相同（diff 为空）

```
$ cmp3 (diff 进程替换，去 CR 前比较，均 CRLF)
IDENTICAL bytes: fabric.../target/TargetSelectionManager.java:302-313  vs  forge.../target/TargetSelectionManager.java:308-319  (12 lines)
IDENTICAL bytes: fabric.../client/TargetSelectionClient.java:382-390    vs  forge.../client/TargetSelectionClient.java:390-398    (9 lines)
IDENTICAL bytes: fabric.../network/ModNetwork.java:483-503              vs  forge.../network/ModNetwork.java:471-491              (21 lines)
```

### 4.2 字节码级：`encode`/`decode` 指令与常量池索引完全一致（证明线格式同构）

```
===== forge-1.20.1
  public static void encode(...TargetSelectCloseMessage, net.minecraft.network.FriendlyByteBuf);
       0: aload_1
       1: aload_0
       2: getfield      #7   // Field token:I
       5: invokevirtual #13  // Method net/minecraft/network/FriendlyByteBuf.writeVarInt:(I)Lnet/minecraft/network/FriendlyByteBuf;
       8: pop
       9: return
  public static ...decode(net.minecraft.network.FriendlyByteBuf);
       0: new           #8   // class ...ModNetwork$TargetSelectCloseMessage
       3: dup
       4: aload_0
       5: invokevirtual #19  // Method net/minecraft/network/FriendlyByteBuf.readVarInt:()I
       8: invokespecial #23  // Method "<init>":(I)V
      11: areturn

===== fabric-1.20.1   （逐条一致）
  encode: aload_1 / aload_0 / getfield #7 token:I / invokevirtual #13 writeVarInt:(I) / pop / return
  decode: new #8 / dup / aload_0 / invokevirtual #19 readVarInt:()I / invokespecial #23 <init>:(I)V / areturn
```

即：两端都是「一个 VarInt 的 token，无其它字段、无版本前缀、无分支」，**逐字节同构**。

### 4.3 逐行等价表

| forge | fabric | 说明 |
|---|---|---|
| `TargetSelectionManager.java:308-319` | `TargetSelectionManager.java:302-313` | **逐字节相同**：取会话 → null 早返回 → `SESSIONS.remove` → `ModNetwork.sendToPlayer(..., new TargetSelectCloseMessage(session.token))` → `SignSelectionGate.clear(player)` → `LOGGER.debug(..., reason)`；不写 `HOLD_SUPPRESSED` |
| `TargetSelectionClient.java:390-398` | `TargetSelectionClient.java:382-390` | **逐字节相同**：`!active || token != closeToken` ⇒ debug + return；否则 debug + `deactivate()` |
| `ModNetwork.java:471-491` | `ModNetwork.java:483-503` | **逐字节相同**：字段 + 构造器 + encode(`writeVarInt`) + decode(`readVarInt`) + handle(`enqueueWork`→`closeFromServer` + `setPacketHandled(true)`) |
| `ModNetwork.java:119-120`（id 28） | `ModNetwork.java:123-128`（id 30） | 唯一差异：协议序号（见 §3） |
| `EventCardService.java:78` | `EventCardService.java:96` | **调用形式逐字一致** |

平台差异说明：本线 `SimpleChannel` 没有 `PacketDistributor.PLAYER`，`sendToPlayer` 是等价的按玩家发送入口；除此之外无改写（`TargetSelectionManager` 与 `TargetSelectionClient` 两段甚至一行都不用改，直接是 forge 原文）。

## 5. 验收输出原文

### 5.1 `grep -rn "TargetSelectClose" fabric-1.20.1/src/main/java`

```
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/TargetSelectionClient.java:372:     * 服务端单方面关闭选择会话（{@code ModNetwork.TargetSelectCloseMessage} 处理器调用，主线程）。
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/EventCardService.java:39: *       D5「不写抑制闩」，但**不发 TargetSelectClose 关闭包** ⇒ 客户端可能仍停在选择模式
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/EventCardService.java:92:                //   清会话 + 清门控 + **发 TargetSelectCloseMessage**（客户端 token 匹配才退出选择模式）；
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:127:        CHANNEL.registerMessage(id++, TargetSelectCloseMessage.class,
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:128:                TargetSelectCloseMessage::encode, TargetSelectCloseMessage::decode, TargetSelectCloseMessage::handle);
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:483:    public static class TargetSelectCloseMessage {
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:486:        public TargetSelectCloseMessage(int token) {
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:490:        public static void encode(TargetSelectCloseMessage msg, FriendlyByteBuf buf) {
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:494:        public static TargetSelectCloseMessage decode(FriendlyByteBuf buf) {
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:495:            return new TargetSelectCloseMessage(buf.readVarInt());
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:498:        public static void handle(TargetSelectCloseMessage msg, Supplier<NetworkEvent.Context> ctx) {
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/target/TargetSelectionManager.java:308:        ModNetwork.sendToPlayer(player, new ModNetwork.TargetSelectCloseMessage(session.token));
```

命中 **3 个类**：`network/ModNetwork.java`（注册 + 类体）、`target/TargetSelectionManager.java`（发关闭包）、`client/TargetSelectionClient.java`（收包入口文档）。判定：**通过**。

### 5.2 `grep -rn "cancelSessionForTests" fabric-1.20.1/src/main/java`

```
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/target/TargetSelectionManager.java:158:    public static void cancelSessionForTests(Player player) {
```

只剩**定义处**，`EventCardService` 不再调用。判定：**通过**（该方法按任务要求保留、语义未改）。

### 5.3 关闭入口调用链（补充取证）

```
$ grep -rn "closeFromServer" fabric-1.20.1/src/main/java
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/TargetSelectionClient.java:382:    public static void closeFromServer(int closeToken) {
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:500:                    com.merlinkitsune.astral_dice.client.TargetSelectionClient.closeFromServer(msg.token));
```

### 5.4 编译验证

命令（仓库根）：`cmd.exe //c "gradlew.bat :fabric-1.20.1:compileJava --console=plain"`

第一次（增量，改完 4 个文件后）：

```
Reusing configuration cache.
> Task :fabric-1.20.1:compileJava

BUILD SUCCESSFUL in 2s
1 actionable task: 1 executed
Configuration cache entry reused.
```

第二次（`--rerun-tasks` 强制全量重编，排除增量缓存假绿）：

```
Reusing configuration cache.

> Task :fabric-1.20.1:compileJava
注: 某些输入文件使用或覆盖了已过时的 API。
注: 有关详细信息, 请使用 -Xlint:deprecation 重新编译。
注: 某些输入文件使用了未经检查或不安全的操作。
注: 有关详细信息, 请使用 -Xlint:unchecked 重新编译。

[Incubating] Problems report is available at: file:///D:/MCProject/astral_dice_multiloader/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 4s
1 actionable task: 1 executed
Configuration cache entry reused.
```

（`注:` 是 javac 的 deprecation / unchecked **警告**，非 error；本线原有同类警告。）

产物核对（证明新类确实被编译出来，不只是缓存命中）：

```
-rw-r--r-- 1 xmace 197609  2902 2026-11-01 23:12:37 ModNetwork$TargetSelectCloseMessage.class
$ javap -p -cp fabric-1.20.1/build/classes/java/main 'com.merlinkitsune.astral_dice.network.ModNetwork$TargetSelectCloseMessage'
public class com.merlinkitsune.astral_dice.network.ModNetwork$TargetSelectCloseMessage {
  private final int token;
  public ...TargetSelectCloseMessage(int);
  public static void encode(...TargetSelectCloseMessage, net.minecraft.network.FriendlyByteBuf);
  public static ...TargetSelectCloseMessage decode(net.minecraft.network.FriendlyByteBuf);
  public static void handle(...TargetSelectCloseMessage, java.util.function.Supplier...NetworkEvent$Context>);
}

$ javap -p ... com.merlinkitsune.astral_dice.target.TargetSelectionManager | grep cancel
13:  public static void cancelSessionForTests(net.minecraft.world.entity.player.Player);
17:  public static void cancel(net.minecraft.server.level.ServerPlayer, java.lang.String);
18:  public static void cancel(net.minecraft.server.level.ServerPlayer, int);

$ javap -p ... com.merlinkitsune.astral_dice.client.TargetSelectionClient | grep closeFromServer
43:  public static void closeFromServer(int);
```

判定：**编译通过**（并已确认三个新成员的字节码签名存在，`cancel(ServerPlayer,int)` 与 `cancelSessionForTests` 仍在）。

## 6. 无法判定 / 需要实机确认

1. **客户端是否真的退出选择模式（本缺陷的最终效果）** — 静态只能证明「服务端发了包、客户端 handler 会按 token 调 `deactivate()`」。真实网络往返、主线程 `enqueueWork` 时序、以及鼠标抓取是否立即释放，都必须实机确认。判定点：弹事件卡那一刻之后，鼠标是否恢复可见/可动、左键是否不再被当确认。
2. **动画窗内是否会被「手持即选择」立刻重开** — 本线 D5 明令「不写抑制闩」，因此 `cancel` 之后下一 tick 若玩家仍主手持有选择器类效果牌，`BaseEffectCardItem.tickHeldSelector` 会尝试重新开会话。理论上 `EventAnimationLock.begin(sp, DELAY_TICKS)` 应拦住这段时间，但**本线 `tickHeldSelector` / `tryStartSelector` 里没有 `EventAnimationLock.isAnimating(...)` 闸门**（见 §7 附带发现）。所以「关闭包生效后客户端退出 → 又被重开」的净观感只能实机看。
3. **旧客户端 × 新服务端** — 静态判断是「未知 id 30 被 `SimpleChannel` 静默丢弃，等价修复前行为、无回归」。但这条只在**同一协议版本区间**成立；是否真的不出现 `ClientPlayNetworking` 警告日志，需实机或日志取样确认。
4. **`token` 覆盖竞态** — 「动画窗内刚开的新会话」被旧包误关的路径已由 `token != closeToken` 挡住，但「同一 tick 内 cancel→start」的实际交错顺序无法静态穷举。
5. **实机环境未验证** — 本批次按硬约束**未启动游戏**，也没有跑任何客户端/服务端联机用例。

## 7. 附带发现（本批**未**改动，超出 C-2 范围）

fabric 线 `item/card/BaseEffectCardItem.java` 的 hold-to-select 自动开会话路径**缺少事件动画锁闸门**，而 forge 线有：

| 位置 | forge | fabric |
|---|---|---|
| `tickHeldSelector` 首行 | `if (EventAnimationLock.isAnimating(player)) return;`（`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/BaseEffectCardItem.java:374`，注释「D4: 事件卡动画窗口期内不得自动开选择会话」） | 无（fabric 同名文件 `:366` 起直接 `isSelecting` 判定） |
| `tryStartSelector` 门槛 | `EventAnimationLock.isAnimating(serverPlayer)` ⇒ 拒绝并提示（同文件 `:393`） | 无（`ExclusiveCardUtil` / `TemporaryCardUtil` / `EffectCardPeriod` / `isSelecting` 四道，独缺动画锁） |

即：缺陷 D4「动画窗口期内任一入口都不得再打开目标选择器」在 fabric 线的**自动重开路径**上仍缺一处覆盖。这与 C-2 是**两件事**（C-2 是「不发关闭包」，D4 是「窗口期不许重开」），本批按约束未改；建议单独立批次补齐，并在补齐后用实机验证 §6.2。

（附：`grep -rn 'EventAnimationLock' fabric-1.20.1/src/main/java` 显示该锁在 fabric 的调用面只有 `AstralDiceMod.java:160` 注册、`EventCardService.java:86` 起锁、`EventChoiceManager.java:108` 与 `EventDrawItem.java:47` 判定，`item/card/` 下 0 命中。）

## 8. 环境与约束遵守

- 未 `commit` / `push` / `stash` / `tag`；未启动游戏；未删除任何既有文件。
- 本批**只**用文件工具改了 4 个 fabric 源文件（+ 本报告）；`AGENTS.md`、`docs/**`、`src/generated` 下的他人改动一律未触碰。
- **工作树在我作业期间是并发的**：`git status --porcelain -- fabric-1.20.1/src` 当时有 198 个 `M`，其中 `combat/DiceCombatEvents.java`（23:13）、`item/sign/AlanaSignItem.java`（23:11）等文件的 mtime 落在同分钟 —— 那些是同批其它批次/代理的改动，**不是我改的**（我只调用了 4 次 edit，目标即上文 4 个文件）。
- 因此上面的编译结果对应 **23:12–23:13 那一刻**的工作树状态；若其它批次随后又改动了 fabric 源码，需要重跑 `:fabric-1.20.1:compileJava` 才能代表最终树。
- 编译：Gradle daemon 属于当前用户（产物 class 归属 `xmace`，且编译期成功写盘），故未被跳过。
- 日期口径：报告里的「2026-11-01」与源码注释一致。

