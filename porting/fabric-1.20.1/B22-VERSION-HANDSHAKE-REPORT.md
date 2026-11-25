# B22 · Fabric 1.20.1 版本握手接入报告（VersionGate 接上真实运行时调用点）

- 仓库：`D:\MCProject\astral_dice_multiloader`，分支 `multi-main`
- 范围：只动 `fabric-1.20.1/src/main/java/**` 下 `network/VersionGate.java`、`network/ModNetwork.java`、`platform/network/{NetworkRegistry,SimpleChannel}.java`，新增 `network/VersionHandshake.java`、`network/VersionProbeMessage.java`、`network/VersionReplyMessage.java`
- 未触碰：lang / recipes / patchouli / tools/**（其他批次在写）；未 commit / push / stash / add / checkout；未跑 gradle、未启动游戏
- 日期：2026-11-01

---

## 0. 结论摘要

1. **`VersionGate` 不再是被丢弃参数喂出来的死代码**：fabric 线现有 **2 处真实运行时调用点** ——
   `network/VersionHandshake.java:179`（服务端判客户端）、`network/VersionHandshake.java:218`（客户端判服务端）。
2. `NetworkRegistry.newSimpleChannel` 的三个参数**不再被丢弃**：版本串进 `SimpleChannel.protocolVersion()`（握手包上线上报的值），两个谓词进 `clientAcceptedVersions()` / `serverAcceptedVersions()`（启动自检用）。
3. 新增协议 **id 28 = S→C VersionProbe**、**id 29 = C→S VersionReply**，既有 0..27 一条未动、顺序未变。
4. 失配表现**与 forge 对齐：断开连接**（不是只打日志）；断开文案以 forge 的逐字原文为前缀，并补上两端版本，玩家在断开界面直接可见。
5. 失败路径有固定机器行 `AP_NET_VERSION_REJECT: local={} remote={}`（另有 `_DETAIL` 行给原因/侧别）。
6. `python scripts/__javac_check.py` → **REAL errors 为空**（171 条全部是既有 NOISE_AW / NOISE_IFACE_INJECTION 噪声，与改动前基线一致）。

---

## 1. 取证：forge / neoforge 的版本门在哪判、失败怎么表现

### 1.1 forge-1.20.1 —— FML 登录握手 + 两个谓词（真正的硬门槛）

仓库里的声明点（原文）：

```text
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:32:    private static final String PROTOCOL_VERSION = VersionGate.interopVersion();
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:33:    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:35:            () -> PROTOCOL_VERSION, VersionGate::accepts, VersionGate::accepts);
```

非门槛（**只影响多人服务器列表的「兼容」标记**，不做连接拦截）：

```text
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java:93:        net.minecraftforge.fml.ModLoadingContext.get().registerDisplayTest(
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java:94:                VersionGate::interopVersion,
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java:95:                (remoteVersion, isFromServer) -> VersionGate.accepts(remoteVersion));
```

FML 实际实现（解 `forge-1.20.1-47.4.10-sources.jar` 逐条读）：

| 位置 | 行为 |
|---|---|
| `NetworkRegistry.java:102` | `newSimpleChannel(ResourceLocation, Supplier<String>, Predicate<String> clientAccepted, Predicate<String> serverAccepted)` |
| `NetworkRegistry.java:203` `validateClientChannels` | 客户端侧：用 **clientAcceptedVersions** 判**服务端**版本（`tryServerVersionOnClient`） |
| `NetworkRegistry.java:213` `validateServerChannels` | 服务端侧：用 **serverAcceptedVersions** 判**客户端**版本（`tryClientVersionOnServer`） |
| `NetworkRegistry.java:228` | 对端**没有该通道**时，把字符串 **`ABSENT`** 喂给谓词 |
| `NetworkRegistry.java:235` | `LOGGER.error("Channels [...] rejected their {} side version number")` |
| `NetworkInstance.java:76-82` | `tryServerVersionOnClient` / `tryClientVersionOnServer` = 谓词结果，**无兜底** |
| `HandshakeHandler.java:180-193` | 客户端发现失配 → error 日志 + 设 `FML_MOD_MISMATCH_DATA` + `Connection#disconnect(Component.literal("Connection closed - mismatched mod channel list"))` |
| `HandshakeHandler.java:237-249` | 服务端发现失配 → error 日志 + 回 `S2CChannelMismatchData` + **同一句** disconnect 文案 |
| `HandshakeHandler.java:253-264` | 客户端收到 mismatch 数据 → 追加明细 + 同一句 disconnect 文案 + `ModMismatchDisconnectedScreen` |

**逐条回答「判定发生在哪 / 失败后怎么表现」**：

1. 判定发生在 **LOGIN 阶段**（玩家进世界之前），由 FML `HandshakeHandler` 驱动；
2. **每个方向各判一次**：客户端判服务端、服务端判客户端，任一侧不通过即失败；
3. 失败 = **断开连接**（`Connection#disconnect(Component)`），**不是只打日志**；
4. 断开文案固定为 `Connection closed - mismatched mod channel list`；
5. 客户端侧还额外收到 `ModMismatchDisconnectedScreen`（表格列出失配模组与两端版本）；
6. **对端没装模组**时谓词收到 `"ABSENT"` 并被拒 ⇒ forge 服务器不接受「没有该通道」的客户端。

### 1.2 neoforge-1.21.1 —— 配置阶段通道协商

```text
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/network/ModPayloads.java:22:        PayloadRegistrar registrar = event.registrar(VersionGate.interopVersion());
```

其 `neoforge-1.21.1/.../network/VersionGate.java` 的 javadoc（:30-36）记录：互通号作为 `RegisterPayloadHandlersEvent#registrar(String)` 的网络版本号交给 NeoForge；NeoForge 在**配置阶段**逐通道 `String.equals` 比较（`NetworkComponentNegotiator#validateComponent`），不等即协商失败 —— 服务端先下发 `ModdedNetworkSetupFailedPayload`，随后断开；客户端据此显示 `ModMismatchDisconnectedScreen`。
（本条为仓库自述 + 上游 API 语义，本批未对 neo 单独做字节码取证。）

### 1.3 两线共性（fabric 必须对齐的部分）

- 判定发生在**登录期**，**双向各一次**；
- 失败 = **断开** + 可读文案（不是只写日志）；
- **对端缺失**同样判失败（forge 用 `ABSENT`）。

---

## 2. forge ↔ fabric 行为对照表

| 维度 | forge-1.20.1（FML） | neoforge-1.21.1 | fabric-1.20.1（本批 B22） |
|---|---|---|---|
| 机制 | FML 登录握手交换通道版本号 | 载荷注册器版本号 + 配置阶段协商 | 显式握手包 id 28/29（登录后首包） |
| 判定阶段 | **LOGIN** | 配置阶段 | **PLAY**（GameJoin 已收、首包） |
| 方向数 | 2（各端判对端） | 2 | 2（`VersionHandshake.java:179` / `:218`） |
| 判据 | `VersionGate.accepts` | `String.equals`（neo 自带） | `VersionGate.accepts`（与 forge **同一函数**） |
| 失败动作 | `Connection#disconnect(Component)` | 断开 + `ModdedNetworkSetupFailedPayload` | 服务端 `player.connection.disconnect(Component)`；客户端 `Connection#disconnect` + `ClientPacketListener#onDisconnect` |
| 断开文案 | `Connection closed - mismatched mod channel list` | 由 neo 屏幕呈现 | **同一句前缀** + ` (astral_dice main: local=<x>, remote=<y>)` |
| 客户端可见性 | 专用 `ModMismatchDisconnectedScreen`（模组/版本表格） | 同 | 原版 `DisconnectedScreen`，**两端版本已内联进文案**（本线不新增自定义屏幕） |
| 对端缺失 | 谓词收到 `ABSENT` → 拒 | 协商失败 | 200t 内 `canSend` 一直为假 → `remote=ABSENT` → 拒 |
| 日志 | `LOGGER.error("Channels [...] rejected ...")` | neo 自带 | `AP_NET_VERSION_REJECT: local={} remote={}` |

### 2.1 与 forge 的差异（明确登记，非疏漏）

1. **阶段不同**：forge 在 LOGIN 就拦下（玩家没进世界）；fabric 在 PLAY 首包拦下（玩家可能短暂进入世界再被踢）。Fabric API 1.20.1 没有对 LOGIN 阶段开放的自定义载荷通道（`minecraft:register` 之外），这是平台限制。
2. **客户端提示界面**：forge 有专门的 mismatch 屏幕；本线只有原版断开界面 ⇒ **把两端版本直接写进断开文案**，避免「只落在日志/crash-report，玩家看不到」的老问题。
3. **对端缺失的判定方式**：forge 从 mod list 交换直接知道；fabric 没有 mod list 交换，只能靠 `canSend` + 超时判定。

---

## 3. fabric 侧实现

### 3.1 协议（追加在末尾，不侵占 0..27）

| id | 方向 | 载荷 | 线格式 | 触发 |
|---|---|---|---|---|
| 0..27 | — | 既有 28 条消息 | 未改动 | 未改动 |
| **28** | S→C | `VersionProbeMessage` | `[VarInt 28][Utf 服务端互通号]` | 服务端 tick 循环中 `canSend` 为真时 |
| **29** | C→S | `VersionReplyMessage` | `[VarInt 29][Utf 客户端互通号]` | 客户端判定服务端版本通过后 |

登记位置：`network/ModNetwork.java:114-123`（注释 114-118、两条消息 119-122、`VersionHandshake.install()` 123）。

### 3.2 为什么是「服务端先发 + tick 重试 `canSend`」

读 `fabric-networking-api-v1 1.3.15+0caff8d077-sources.jar` 得到的三条事实：

1. `ServerPlayNetworkAddon.onClientReady()` **先**触发 `ServerPlayConnectionEvents.JOIN`，**然后**才 `sendInitialChannelRegistrationPacket()`；
2. `ClientPlayNetworkAddon.onServerReady()` 同样先触发 JOIN 再发注册包，且源码注释写明「客户端在收到 GameJoinS2CPacket 之前**不能**发任何包（含 `minecraft:register`）」；
3. `AbstractChanneledNetworkAddon#handle` 只要求**本端**有全局 receiver（`getHandler(channelName) == null` 才丢弃），**不要求对端先登记**。

⇒ 若在 JOIN 回调里直接发 probe，那一刻服务端还没收到客户端的 register，`canSend` 大概率为假（会被自己的守卫吞掉）。所以改为：**JOIN 只登记 pending，tick 循环里等 `canSend` 变真再发**；收包侧不依赖任何登记，所以握手包不会丢。

### 3.3 改动清单

| 文件 | 行数 | 改动 |
|---|---|---|
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionHandshake.java` | 268（新增） | 握手编排：pending 表、tick 重试、双向判定、机器行、断开 |
| `.../network/VersionProbeMessage.java` | 50（新增） | id 28 S→C 载荷 |
| `.../network/VersionReplyMessage.java` | 51（新增） | id 29 C→S 载荷 |
| `.../network/ModNetwork.java` | 747 → **757** | 末尾追加两条消息登记 + `VersionHandshake.install()`（0..27 未动） |
| `.../platform/network/SimpleChannel.java` | 172 → **209** | 保存 versionSupplier + 两个 Predicate；新增 `protocolVersion()` / `clientAcceptedVersions()` / `serverAcceptedVersions()`；保留单参构造 |
| `.../platform/network/NetworkRegistry.java` | 27 → **32** | `newSimpleChannel` 把三个参数真正传给 `SimpleChannel`（旧实现丢弃） |

`SimpleChannel` 关键新增（行号为准）：
- `:9` `import java.util.function.Predicate;`
- `:59-61` 三个新字段
- `:76-84` 四参构造（`SimpleChannel(ResourceLocation id) { this(id, null, null, null); }` 保留）
- `:92-105` 三个 getter

`NetworkRegistry`：
- `:27` `return new SimpleChannel(name, versionSupplier, clientAcceptedVersions, serverAcceptedVersions);`

### 3.4 机器行（全部落在 `AstralDiceMod.LOGGER`，前缀沿用 `AP_*`）

| 行 | 机器行 | 触发 |
|---|---|---|
| `VersionHandshake.java:108/112/117` | `AP_NET_VERSION_SELFCHECK_FAIL: ...` | 通道声明与 `VersionGate` 口径不一致 / 谓词缺失 / 本端版本被自家谓词拒 |
| `:133` | `AP_NET_VERSION_JOIN: local={} player={}` | 玩家加入，进入 pending |
| `:156` | `AP_NET_VERSION_PROBE: local={} player={}` | probe 已发出 |
| `:180` + `:182` | **`AP_NET_VERSION_REJECT: local={} remote={}`** + `AP_NET_VERSION_REJECT_DETAIL: side=server reason=client_version_not_accepted ...` | 服务端判客户端失败 |
| `:192` + `:194` | `AP_NET_VERSION_REJECT: local={} remote=ABSENT` + `... reason=client_channel_absent ...` | 客户端从未声明通道（没装/不是本模组客户端） |
| `:201` + `:203` | `AP_NET_VERSION_REJECT: local={} remote=NO_REPLY` + `... reason=client_no_reply ...` | 通道声明了但握手期内不回 |
| `:219` + `:221` | **`AP_NET_VERSION_REJECT: local={} remote={}`** + `... side=client reason=server_version_not_accepted ...` | 客户端判服务端失败 |
| `:226` | `AP_NET_VERSION_ACCEPT: local={} remote={} side=client` | 客户端通过 |
| `:187` | `AP_NET_VERSION_ACCEPT: local={} remote={} side=server player={}` | 服务端通过 |
| `:246` + `:248` | `AP_NET_VERSION_REJECT: local={} remote=ABSENT` + `... side=client reason=server_channel_not_registered` | 客户端想回包但服务端通道未登记（防御性） |

### 3.5 失败表现与文案（玩家可见处）

| 场景 | 动作 | 玩家看到的文案 |
|---|---|---|
| 服务端判客户端失败（版本不等） | `player.connection.disconnect(Component)` | `Connection closed - mismatched mod channel list (astral_dice main: local=<服务端>, remote=<客户端>)` |
| 客户端判服务端失败（版本不等） | `Connection#disconnect` + `ClientPacketListener#onDisconnect` | 同上（local/remote 以客户端视角给出） |
| 客户端没装/没声明通道 | 服务端断开 | `... (astral_dice main: local=<x>, remote=ABSENT; install the matching Astral Dice build on the client)` |
| 声明了但不回包 | 服务端断开 | `... (astral_dice main: local=<x>, remote=NO_REPLY; the client did not answer the version handshake)` |

> 前缀 `Connection closed - mismatched mod channel list` 与 forge FML `HandshakeHandler` 逐字一致；括号内是本模组补充的两端版本（forge 靠 `ModMismatchDisconnectedScreen` 的表格给出，本线没有该屏幕，故内联）。若要求**逐字**一致，删掉括号即可（单点改动，见 §7）。

### 3.6 守卫

- **方向防伪**：`VersionProbeMessage.handle` 见 `ctx.get().getSender() != null`（= 被 C→S 打上来）直接丢弃；`VersionReplyMessage.handle` 见 `getSender() == null` 直接丢弃。
- **专用服务端不加载客户端类**：所有 `net.minecraft.client.*` 调用收在嵌套 `VersionHandshake.ClientSide` 里（与既有 `SimpleChannel.ClientSendHolder` 同一套路），只有客户端真正收到 probe 时才会解析这些符号。
- **线程**：两个 handler 都走 `ctx.get().enqueueWork(...)` 切回主线程（服务端 = server 线程，客户端 = client 线程），pending 表只在服务端线程读写（`ConcurrentHashMap` 兜底 JOIN 回调可能来自别的线程）。
- **幂等**：`install()` 用 `volatile boolean installed` 保证重复调用无副作用。
- **不误伤既有包**：新消息只追加在 `register()` 末尾；`VersionHandshake` 只挂 3 个事件（JOIN / DISCONNECT / END_SERVER_TICK），不改既有事件顺序。

---

## 4. 三条路径的握手时序

### 4.1 单人（IntegratedServer）

```text
1. 客户端点「创建世界」→ 集成分服务端启动 → ServerPlayer 加入
2. 服务端：ServerPlayNetworkHandler 构造 → FAPI addon lateInit → onClientReady()
3.        → ServerPlayConnectionEvents.JOIN 触发 → VersionHandshake.onJoin 登记 pending(deadline = tickCount + 200)
4.        → FAPI 紧接着发本端 minecraft:register（S→C）
5. 服务端 → 客户端：GameJoin
6. 客户端：客户端 JOIN 触发 → 发 minecraft:register（含 astral_dice:main）
7. 服务端 END_SERVER_TICK：canSend(player, astral_dice:main) == true
8.        → 发 id 28 VersionProbe(服务端互通号) + AP_NET_VERSION_PROBE
9. 客户端收到 id 28 → enqueueWork → VersionHandshake.onProbeReceived
10.      → VersionGate.accepts(服务端互通号)?  否 → 机器行 + 客户端自我断开（玩家看到文案）
11.      → 是 → 回 id 29 VersionReply(客户端互通号) + AP_NET_VERSION_ACCEPT(side=client)
12. 服务端收到 id 29 → enqueueWork → VersionHandshake.onReplyReceived
13.      → VersionGate.accepts(客户端互通号)?
14.        否 → AP_NET_VERSION_REJECT + player.connection.disconnect(Component)
15.        是 → 清 pending + AP_NET_VERSION_ACCEPT(side=server)
```

要点：单人路径**不会**被超时误判 —— 第 7 步在同进程内通常 1 个 tick 内成立，200t 余量充足。

### 4.2 局域网（LAN / 别人开的集成服）

时序与 4.1 **完全相同**（客户端只认「我连的是一个真服务端」），差异只有两处：
- 第 7 步的 `canSend` 依赖**网络 RTT**（客户端 register 包过网络才到），故真正发出 probe 的 tick 会比单人晚 1 个 RTT；
- 第 9/12 步都要过网络 ⇒ 200t（10s）窗口内完成即可；主机的客户端自己加入时走的是 4.1。

### 4.3 专用服务器

同为 4.1 的时序，但：
- 服务端侧没有「本机客户端」，第 2-4 步照常；
- 服务端 `canSend` 只有在**客户端把 register 包传到服务端之后**才为真（1 个 RTT 量级）；
- 客户端若没装本模组：永远到不了第 7 步 ⇒ 第 200t 到期 → `AP_NET_VERSION_REJECT: local=<x> remote=ABSENT` + 断开（按 forge 的 `ABSENT` 语义）；
- 客户端装了但版本不符：走第 10 步或第 14 步断开，两条路径的文案都含两端版本。

---

## 5. 验收原始输出

### a) `python scripts/__javac_check.py` → REAL errors 为空

```text
EXIT 1 ERRORS 171 jars 685 srcs 525
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  164  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 984
```

对照基线（**改动前**）：`EXIT 1 ERRORS 171 jars 685 srcs 522`，同样 `164 NOISE_AW + 7 NOISE_IFACE_INJECTION`、REAL 为空。
`srcs` 522 → 525 即本批新增的 3 个源文件；**errors 总数与分类完全不变**。

中间过程（如实记录）：第一轮编译出过 **1 条 REAL 错误** ——
```text
...\network\VersionHandshake.java:259: error: cannot find symbol
                connection.disconnect(Component.literal(text));
  symbol:   method disconnect(MutableComponent)
  location: variable connection of type ClientPacketListener
```
归因：1.20.1 的 `ClientPacketListener` **没有** `disconnect(Component)`（那是 `ServerGamePacketListenerImpl` 上的方法，`javap` 首轮取证时把两个类的输出看串了）。修法：客户端改走 `listener.getConnection().disconnect(reason)` + `listener.onDisconnect(reason)`（与 `ClientPacketListener#handleDisconnect` 同序）。修复后 REAL 归零。

### b) `grep -rn "VersionGate.accepts" fabric-1.20.1/src/main/java`（原文）

```text
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:117:        // 由 VersionHandshake 在登录后首包驱动（两端各调一次 VersionGate.accepts，失配即断开）。
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionHandshake.java:34: *   客户端收到:      VersionGate.accepts(服务端互通号)
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionHandshake.java:37: *   服务端收到:      VersionGate.accepts(客户端互通号)
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionHandshake.java:179:        if (!VersionGate.accepts(clientVersion)) {
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionHandshake.java:218:        if (!VersionGate.accepts(serverVersion)) {
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionProbeMessage.java:14: * {@link VersionReplyMessage},两端各用 {@code VersionGate.accepts} 判一次(见 {@link VersionHandshake})。
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionReplyMessage.java:11: * {@code VersionGate.accepts(服务端版本)} 后,回发**本端**的互通版本号。
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionReplyMessage.java:13: * <p>服务端收到后用同一个 {@code VersionGate.accepts(客户端版本)} 再判一次 —— 两个方向各判一次,
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/network/NetworkRegistry.java:18: * 两端各调一次 {@code VersionGate.accepts})。
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/network/SimpleChannel.java:41: * 协议 id 28/29,两端各调一次 {@code VersionGate.accepts})。
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/network/SimpleChannel.java:74:     * 判定由 {@link VersionHandshake} 的显式握手包驱动(两端各调一次 {@code VersionGate.accepts})。
```

**代码层调用点 2 处**（其余是注释/javadoc）：
- `network/VersionHandshake.java:179` `if (!VersionGate.accepts(clientVersion))` → 服务端判客户端
- `network/VersionHandshake.java:218` `if (!VersionGate.accepts(serverVersion))` → 客户端判服务端

### c) 反面判据：把 `accepts` 改成「立刻 return false」后仍能编译

在 `VersionGate.accepts` 的**方法体开头**插入（非常量条件，避免 javac 的 unreachable 判定）：

```java
        // B22 反面判据占位:临时强制拒绝,用来证明调用点可达(非常量折叠)。取证后立刻删掉本块。
        if (System.nanoTime() != 0L) {
            return false;
        }
```

结果：

```text
d994b394f0b139767de2f74d6d5f3a10dbc70d31ffdef34df8c3212f468bd10b *fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionGate.java
EXIT 1 ERRORS 171 jars 685 srcs 525
=== buckets ... 164 NOISE_AW / 7 NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
```

⇒ 与正常态**逐项相同**：调用点既没有被常量折叠，也没有出现新的不可达/类型错误，两个调用点在编译期都是真实可达的引用（`VersionGate.accepts` 一旦恒假，运行时必然进入两条 `AP_NET_VERSION_REJECT` 分支）。

复原证据：

```text
改动前 SHA-256: da6f40afcabc5aacb72862c1080b8950aa2f992e6e272c8cdc9c9783600d555e  VersionGate.java
植入后 SHA-256: d994b394f0b139767de2f74d6d5f3a10dbc70d31ffdef34df8c3212f468bd10b  VersionGate.java
复原后 SHA-256: da6f40afcabc5aacb72862c1080b8950aa2f992e6e272c8cdc9c9783600d555e  VersionGate.java
```

外加 `grep -c "System.nanoTime" VersionGate.java` → `0`，`bareLF = 0`。
**复原后重新跑一遍 `__javac_check.py`：`EXIT 1 ERRORS 171 / REAL errors 为空`（`saved errors.txt lines= 984`）。**

### d) 三条路径的时序

见 §4（单人 / 局域网 / 专用服务器）。

---

## 6. 只能实机确认的清单（本批未启动游戏）

1. `canSend(player, astral_dice:main)` **实际在第几个 tick 变真**（推断 1 tick；高延迟下可能 2-3 tick）。
2. 断开界面里文案的**完整可见性**（是否换行/截断；长文案在 1.20.1 `DisconnectedScreen` 的表现）。
3. 真正做一次**版本失配演练**（把一端 `mod_version` 二号线改成别的值）确认玩家看到的文案与机器行。
4. 单人进世界**不被误踢**、局域网加入**不被误踢**（canSend 竞态的真实裕度）。
5. 纯原版客户端连专用服务器的表现（预期：200t 后 `remote=ABSENT` 断开）。
6. 客户端主动断开时，服务端同时断开 **是否出现双重断开/二次提示**。
7. 200t（10s）超时在真实网络下的裕度。
8. `AP_NET_VERSION_SELFCHECK_FAIL` 是否会在某些构建形态下误报（例如 `astral_dice_version.properties` 缺失时 `interopVersion()` 退回 `unresolved`）。

---

## 7. 需要裁决的事项

1. **纯原版客户端（对端没装）是否应被踢**：本批按 forge 的 `ABSENT` 语义**踢**（`remote=ABSENT`）。若希望 fabric 服务器保持「原版客户端可进」（只是功能不可用），把 `rejectAbsent` 改成只打 `AP_NET_VERSION_ABSENT` 警告即可（单函数改动）。
2. **断开文案形态**：现为 `forge 原文 + " (astral_dice main: local=…, remote=…)"`。若要求与 forge **逐字**一致，删掉括号部分（`mismatchText` 单点改动），代价是玩家看不到版本号。
3. **超时时长** `HANDSHAKE_DEADLINE_TICKS = 200`（10s）是否合适。
4. **握手阶段差异**（PLAY 而非 LOGIN）无法消除，是否需要写进玩家侧 changelog / 服务端管理说明。

---

## 8. 附：本批未做（越界或需另批）

- 未新增自定义断开屏幕（`ModMismatchDisconnectedScreen` 等价物）—— 需要 `screen/` 目录与 lang，本批禁改 lang。
- 未给 `network/VersionGate.java` 做任何口径调整（只做反面判据的临时植入并已复原）。
- 未触碰 forge / neo 两条线。

---

## 9. 裁决落地（B25）

> 本节由 B25 批次追加。任务书写的是「§8 裁决落地」，但本文档 §8 已被〈附：本批未做〉占用；
> 为**不改动他人正文**，本节顺延为 §9（内容即任务书所指的「裁决落地」）。
> 另外：本文 §3.4 / §3.5 / §4.3 / §7 第 1、2 条描述的是 **B22 当时**的行为，已被本节取代；
> 原文按「不重写他人正文」的要求保留不动，读时以本节为准。
> 前后对照、`disconnect` 调用点全表、验收原始输出见同目录 `B25-HANDSHAKE-RULINGS-REPORT.md`。

### 9.1 裁决 1 —— 对端未安装（ABSENT）不再断开

| 项 | 内容 |
|---|---|
| 落点 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionHandshake.java:239-247`（`giveUpAbsent`，由原 `rejectAbsent` 改名）+ 调用点 `:190-194` |
| 改动 | 超时且 `canSend` 始终为假 ⇒ 从 pending 移除 + 打 `AP_NET_VERSION_ABSENT` 两行 WARN；**不再调用 `disconnect`**（原实现会踢人并给出 `...; install the matching Astral Dice build on the client`） |
| 理由（已实读核实） | `fabric-1.20.1/src/main/resources/fabric.mod.json:13` = `"environment": "*"`；同文件 `:37-44` 的 `depends` 只含 `fabricloader` / `minecraft` / `fabric-api` / `fabric-data-attachment-api-v1` / `puzzleslib` / `starengine`，**不含自身**；`forge-1.20.1/src/main/templates/META-INF/mods.toml` 全文无 `displayTest` / `clientSideOnly`；`neoforge-1.21.1/src/main/templates/META-INF/neoforge.mods.toml` 同样都没有 ⇒ 三条线的默认语义是「对端没装 = 功能不可用」，**不是「客户端必需」**。原版客户端加入专用服务器必须能留在服里。 |

### 9.2 裁决 2 —— 超时只「放弃判定 + 告警」，绝不踢人

| 项 | 内容 |
|---|---|
| 常量 | `VersionHandshake.java:73` `HANDSHAKE_DEADLINE_TICKS = 200`（10s）**未改** |
| 落点 | `:190-194`（从未声明通道 ⇒ 超时）、`:197-201`（声明了但没回包 ⇒ 超时）、`:255-263`（`giveUpNoReply`，由原 `rejectNoReply` 改名） |
| 改动 | 到点动作由「断开」改为 `it.remove()` + WARN（含玩家名、已等 tick 数、最后一次 `canSend` 状态），移除后不再重试 |
| 证明 | `onServerTick`（`:164-203`，唯一评估超时的地方）整段不含 `disconnect`；`giveUpAbsent`（`:239-247`）/ `giveUpNoReply`（`:255-263`）也不含 |
| 「收到即判」核对 | **现有实现本来就是收到即判，未改判定时机**：服务端 `onReplyReceived:211` 在收到 id 29 的同一帧调 `VersionGate.accepts`（`:218`）；客户端 `onProbeReceived:277` 在收到 id 28 的同一帧调 `VersionGate.accepts`（`:279`）。两条 `disconnect`（`:225` / `:286`）都紧贴在这两处 `if (!VersionGate.accepts(...))` 之内，与超时路径无耦合。 |

### 9.3 裁决 3 —— 断开文案保留括号内版本诊断（有意差异）

| 项 | 内容 |
|---|---|
| 落点 | `VersionHandshake.java:294-307`（`mismatchText` 的 javadoc 与正文；正文 `:305-307`），前缀常量 `:85` |
| 现状 | `Connection closed - mismatched mod channel list (astral_dice main: local=<本端互通号>, remote=<对端互通号>)` —— **保持不变** |
| 理由（已写进代码注释） | forge 断开后进 `ModMismatchDisconnectedScreen`，用表格列出失配模组与两端版本；fabric 1.20.1 没有等价屏幕，断开界面只有原版 `DisconnectedScreen` 的一行文案 ⇒ 去掉括号玩家就完全不知道原因、也不知道该换哪个版本。故括号是**有意保留**的与 forge 的差异，不是对齐时的遗漏；将来若补了自定义断开屏幕才可收回。 |

### 9.4 机器行（B25 统一口径，供测试台断言）

| 机器行 | 位置 | 字段 | 触发 |
|---|---|---|---|
| `AP_NET_VERSION_OK` | `:228`（`side=server`）/ `:289`（`side=client`） | `player=` `local=` `remote=` `ticks=`（客户端侧无 ticks） | 判定通过 |
| `AP_NET_VERSION_REJECT` | `:219`（`side=server`）+ `:222`（`_DETAIL`）/ `:280`（`side=client`）+ `:283` | `player=` `local=` `remote=` `ticks=` `action=disconnect` | **唯一的断开场景**：两端都装了但互通号不兼容 |
| `AP_NET_VERSION_ABSENT` | `:241`（`reason=client_channel_absent`）+ `:244` / `:257`（`reason=client_no_reply`）+ `:260` / `:337`（`side=client reason=server_channel_not_registered`）+ `:340` | `player=` `local=` `remote=` `ticks=` `canSend=` `action=give_up` | 判定不出来（超时 / 对端未装）⇒ **不断开** |

> 语义划分：`OK` = 通过；`REJECT` = 版本不兼容（**唯一会断开的机器行**）；`ABSENT` = 放弃判定（**绝不断开**）。
> `ABSENT` 行的 `remote=` 取值为 `ABSENT`（从未声明通道）或 `NO_REPLY`（声明了没回包），用 `reason=` 区分原因。
> 旧口径里 `AP_NET_VERSION_REJECT: ... remote=ABSENT` 已不存在 —— 若测试台按旧串断言，需改用 `AP_NET_VERSION_ABSENT`。

