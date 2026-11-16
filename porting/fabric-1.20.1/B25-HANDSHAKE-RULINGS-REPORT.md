# B25 · 握手三处裁决落地报告（fabric-1.20.1）

- 仓库：`F:\MCProject\astral_dice_multiloader`，分支 `multi-main`
- 批次：B25（承接 B22 版本握手，按 B22 报告 §7「需要裁决的事项」+ 本批任务书调整**行为语义**，不重构）
- 日期：2026-11-01
- 纪律：未 commit / push / stash / add / checkout；未跑 gradle；未启动游戏
- 关联：`porting/fabric-1.20.1/B22-VERSION-HANDSHAKE-REPORT.md`（已追加 §9「裁决落地」，编号说明见本报告 §9）

---

## 0. 结论摘要

| 验收项 | 结果 |
|---|---|
| 裁决 1：对端未装（ABSENT）**不断开** | ✅ 落地 |
| 裁决 2：超时只「放弃判定 + 告警」，**绝不踢人** | ✅ 落地 |
| 裁决 2 附条：「收到即判」核对 | ✅ **原实现本来就是收到即判**，未改判定时机（证据见 §3.3） |
| 裁决 3：断开文案保留括号内版本诊断 + 注释写明有意差异 | ✅ 落地（文案本就如此，补写注释与理由） |
| 机器行 ≥ 3 条（`OK` / `REJECT` / `ABSENT`） | ✅ 三族齐全（另有 `_SELFCHECK_FAIL` / `_JOIN` / `_PROBE`） |
| a) `python scripts/__javac_check.py` REAL errors | ✅ 为空（`ERRORS 171` = 基线） |
| d) `python tools/verify_fabric_assets.py` | ✅ `=== PASS: 8 项闭环全部通过 ===` |
| BLOCKED | **无** |

---

## 1. 改动清单

### 1.1 文件级

| 文件 | 改前 | 改后 | 说明 |
|---|---|---|---|
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionHandshake.java` | 268 行 | **361 行 / 19,483 B** | 本批唯一实质改动文件 |
| `.../network/VersionProbeMessage.java` | 50 行 | **未改** | B22 版本已符合：只带版本串、方向防伪、`enqueueWork` |
| `.../network/VersionReplyMessage.java` | 51 行 | **未改** | 同上 |
| `.../network/ModNetwork.java` | 757 行 | **未改** | `:117` 注释「失配即断开」在新语义下**仍然准确**（失配 = 版本不兼容 = 仍断开），故不动 |
| `porting/fabric-1.20.1/B22-VERSION-HANDSHAKE-REPORT.md` | 334 行 | **383 行** | 追加 §9「裁决落地」，未重写任何既有正文 |
| `porting/fabric-1.20.1/B25-HANDSHAKE-RULINGS-REPORT.md` | — | **新增** | 本报告 |

### 1.2 未触碰（纪律）

- `platform/network/**`（`SimpleChannel` / `NetworkRegistry`）：B22 已改好，本批**只读**使用 `protocolVersion()` / `clientAcceptedVersions()` / `serverAcceptedVersions()` 三个 getter，一行未改。
- `event/ModTooltipHandler.java`：另一批次在写，未打开。
- `lang` / `recipes` / `patchouli` / `tools/**`：未改。
- `network/VersionGate.java`：口径未动（`accepts` 语义仍是唯一判据来源）。
- forge / neoforge 两条线：未改。

### 1.3 `VersionHandshake.java` 结构级差异（B22 → B25）

| 位置 | B22 | B25 |
|---|---|---|
| `Pending` 字段 | `deadline` + `probeSent` | `startTick` + `deadline`（= `startTick + 200`，构造器算）+ `probeSent` + **`lastCanSend`** |
| 超时且从未声明通道 | `rejectAbsent` → **断开** | 改名 **`giveUpAbsent`** → 只 WARN，不断开（`:239-247`） |
| 超时且声明了没回包 | `rejectNoReply` → **断开** | 改名 **`giveUpNoReply`** → 只 WARN，不断开（`:255-263`） |
| 机器行 | `REJECT ... remote=ABSENT` / `remote=NO_REPLY` / 通过记 `ACCEPT` | `ABSENT` 独立成行（`action=give_up`）/ 通过改记 **`OK`** / `REJECT` **只留给版本不兼容**（`action=disconnect`） |
| 新增工具方法 | — | `waitedTicks(Pending, now)`（`:310-315`）、`serverTickOf(ServerPlayer)`（`:317-320`）——给机器行补 `ticks=` |
| 类 javadoc | 只有协议与时序 | 新增「B25 裁决」小节（`:44-56`），把三条裁决与理由写进代码 |

---

## 2. 裁决 1：对端未安装（ABSENT）不断开

### 2.1 改前（B22 版行为，引自 B22 报告 §3.4 表 `:192`/`:194` 与 §3.5 表第 3 行）

```text
AP_NET_VERSION_REJECT: local=<服务端> remote=ABSENT
AP_NET_VERSION_REJECT_DETAIL: side=server reason=client_channel_absent ...
→ player.connection.disconnect(Component)
文案: Connection closed - mismatched mod channel list
      (astral_dice main: local=<x>, remote=ABSENT; install the matching Astral Dice build on the client)
```

即：200t 内 `canSend` 一直为假 ⇒ 判定对端没装 ⇒ **踢掉玩家**。

### 2.2 改后（`VersionHandshake.java:190-194` + `:239-247`，原文）

```java
                if (now >= pending.deadline) {
                    // B25 裁决 1/2:对端从未声明通道(没装本模组)⇒ 放弃判定 + WARN,**不断开**。
                    it.remove();
                    giveUpAbsent(player, local, waitedTicks(pending, now), pending.lastCanSend);
                }
```

```java
    private static void giveUpAbsent(ServerPlayer player, String local, int ticks, boolean canSend) {
        AstralDiceMod.LOGGER.warn(
                "AP_NET_VERSION_ABSENT: player={} local={} remote={} ticks={} canSend={}",
                nameOf(player), local, ABSENT, ticks, canSend);
        AstralDiceMod.LOGGER.warn(
                "AP_NET_VERSION_ABSENT_DETAIL: side=server reason=client_channel_absent player={} local={}"
                        + " remote={} ticks={} canSend={} action=give_up",
                nameOf(player), local, ABSENT, ticks, canSend);
    }
```

### 2.3 证据链（为什么「没装」不等于「该踢」）

| 声明点 | 实读结果 | 语义 |
|---|---|---|
| `fabric-1.20.1/src/main/resources/fabric.mod.json:13` | `"environment": "*"` | 双端都可加载，不声明客户端必需 |
| 同文件 `:37-44`（`depends`） | `fabricloader` / `minecraft` / `fabric-api` / `fabric-data-attachment-api-v1` / `puzzleslib` / `starengine` | **不含自身** ⇒ 不会因为对端缺本模组而拒绝连接 |
| `forge-1.20.1/src/main/templates/META-INF/mods.toml` | 全文无 `displayTest` / `clientSideOnly` | 默认 `MATCH_VERSION`，只影响服务器列表标记 |
| `neoforge-1.21.1/src/main/templates/META-INF/neoforge.mods.toml` | 同样无 | 同上 |

⇒ 三条线的**声明面**语义一致：对端没装 = **功能不可用**，不是「客户端必需」。
既然声明面没说必需，运行时就不该比对端更严格 —— 原版客户端加入专用服务器必须能留在服里。

### 2.4 后果（这是本次**有意**放宽的能力面）

- 原版客户端 / 未装本模组的客户端：10s 后打 `AP_NET_VERSION_ABSENT ... action=give_up`，**留在服里**，只是拿不到本模组的网络功能（既有 0..27 条消息在它那侧本来就不通）。
- 服务端**不会**因此进入任何降级状态：pending 表条目被移除，不再重试、不再占资源。

---

## 3. 裁决 2：超时只「放弃判定 + 告警」

### 3.1 改前（B22 版行为，引自 B22 报告 §3.4 表 `:192`/`:201` 与 §3.5 表第 3/4 行）

```text
超时 + 从未声明通道 → AP_NET_VERSION_REJECT ... remote=ABSENT  → 断开
超时 + 声明了没回包   → AP_NET_VERSION_REJECT ... remote=NO_REPLY → 断开
```

两条超时路径都会踢人。

### 3.2 改后（`VersionHandshake.java:190-201` + `:255-263`，原文）

```java
                if (now >= pending.deadline) {
                    // B25 裁决 1/2:对端从未声明通道(没装本模组)⇒ 放弃判定 + WARN,**不断开**。
                    it.remove();
                    giveUpAbsent(player, local, waitedTicks(pending, now), pending.lastCanSend);
                }
                continue;
            }
            if (now >= pending.deadline) {
                // B25 裁决 2:通道声明了但没回包 ⇒ 同样只放弃判定 + WARN,**不断开**。
                it.remove();
                giveUpNoReply(player, local, waitedTicks(pending, now), pending.lastCanSend);
            }
```

```java
    private static void giveUpNoReply(ServerPlayer player, String local, int ticks, boolean canSend) {
        AstralDiceMod.LOGGER.warn(
                "AP_NET_VERSION_ABSENT: player={} local={} remote={} ticks={} canSend={}",
                nameOf(player), local, NO_REPLY, ticks, canSend);
        AstralDiceMod.LOGGER.warn(
                "AP_NET_VERSION_ABSENT_DETAIL: side=server reason=client_no_reply player={} local={}"
                        + " remote={} ticks={} canSend={} action=give_up",
                nameOf(player), local, NO_REPLY, ticks, canSend);
    }
```

- 常量未改：`VersionHandshake.java:73` `private static final int HANDSHAKE_DEADLINE_TICKS = 200;`（10s）。
- 到点动作 = `it.remove()` + 两行 WARN；WARN 三个必备字段全部带上：**玩家名**（`player=`）、**已等待 tick 数**（`ticks=`，`waitedTicks` 现算）、**最后一次 `canSend`**（`canSend=`，由 `pending.lastCanSend` 存档，`:181` 每次 tick 覆盖）。
- 移除后**不再重试**：`PENDING` 里已无该玩家，`onServerTick` 不会再看到它。

### 3.3 「收到即判」核对（任务书要求核实，不是就改）

**结论：B22 的实现本来就是「收到回包立刻判」，不是「攒到超时才判」，本批未改判定时机。**

| 判据 | 代码位置 | 事实 |
|---|---|---|
| 服务端收到 id 29 当帧就判 | `:211` `onReplyReceived` → `:216` `PENDING.remove(...)` → `:218` `if (!VersionGate.accepts(clientVersion))` | 判定在 handler 内，只依赖收到的版本串；**没有任何 tick/超时条件包着它** |
| 服务端不兼容同帧断开 | `:225` `disconnect(player, mismatchText(local, clientVersion));` | 同一分支内立即断开 |
| 客户端收到 id 28 当帧就判 | `:277` `onProbeReceived` → `:279` `if (!VersionGate.accepts(serverVersion))` | 同上 |
| 客户端不兼容同帧断开 | `:286` `ClientSide.disconnect(mismatchText(local, serverVersion));` | 同上 |
| 超时路径不参与判定 | `:164-203` `onServerTick` 全文**不含** `VersionGate.accepts` | 超时只做「有没有等到回包」的簿记，不做版本比较 |
| 超时移除后迟到的回包 | `:216` `PENDING.remove` 返回 `null` ⇒ `waitedTicks` 返回 `-1` | 仍然**照常判一次**：通过记 `OK`（`ticks=-1`），不兼容**仍然断开**（属「双方都装了但不兼容」，不在豁免范围） |

### 3.4 `ticks=` 字段的来源（`Pending` 结构变化）

```java
    private static final class Pending {
        private final int startTick;
        private final int deadline;
        private volatile boolean probeSent;
        private volatile boolean lastCanSend;

        private Pending(int startTick) {
            this.startTick = startTick;
            this.deadline = startTick + HANDSHAKE_DEADLINE_TICKS;
        }
    }
```

- `startTick` 在 `onJoin`（`:159` `new Pending(server.getTickCount())`）登记，`waitedTicks`（`:310-315`）`Math.max(0, now - startTick)`。
- 回包路径的 `now` 用 `serverTickOf(player)`（`:317-320`）取，取不到（玩家已离线）返回 `-1`，机器行里能区分「正常判定」与「离线/迟到判定」。

---

## 4. 裁决 3：断开文案保留括号内版本诊断（有意差异）

### 4.1 现状（文案与 B22 **完全相同**，任务书要求「保留」，故未改文案本体）

`VersionHandshake.java:305-307`：

```java
    private static String mismatchText(String local, String remote) {
        return DISCONNECT_PREFIX + " (astral_dice main: local=" + local + ", remote=" + remote + ")";
    }
```

`VersionHandshake.java:85`：

```java
    private static final String DISCONNECT_PREFIX = "Connection closed - mismatched mod channel list";
```

实际断开文案形如：

```text
Connection closed - mismatched mod channel list (astral_dice main: local=1.5.0-rc.7, remote=1.4.0-rc.1)
```

### 4.2 本批**新增**的东西：把「为什么保留括号」写进代码注释（`VersionHandshake.java:293-304` 原文）

```java
    /**
     * 断开文案(B25 裁决 3:**括号内的版本诊断保留**,这是与 forge 的**有意差异**)。
     *
     * <p>前缀 {@value #DISCONNECT_PREFIX} 与 forge FML {@code HandshakeHandler} 逐字一致,后面补
     * {@code (astral_dice main: local=<本端互通号>, remote=<对端互通号>)}。</p>
     *
     * <p>为什么 fabric 这侧**必须**留括号:forge 断开后会进 {@code ModMismatchDisconnectedScreen},
     * 用表格列出失配模组与两端版本;fabric(1.20.1 Fabric API)没有等价的断开屏幕,断开界面只有原版
     * {@code DisconnectedScreen} 的一行文案 —— 去掉括号,玩家就**完全不知道**自己为什么被踢、
     * 也不知道该换哪个版本。所以括号是**有意保留**的差异,不是与 forge 对齐时的遗漏;
     * 若将来本线补了自定义断开屏幕,才可以把括号收回日志。</p>
     */
```

### 4.3 理由（与 B22 §7 第 2 条的裁决结论）

- 若要求「与 forge 逐字一致」⇒ 删括号 ⇒ 玩家只看到一句 `Connection closed - mismatched mod channel list`，**既不知是哪两个不兼容、也不知该换哪边**。
- forge 之所以敢只给一句话，是因为它有 `ModMismatchDisconnectedScreen`（表格：模组 / 期望版本 / 实际版本）。fabric 1.20.1 侧没有等价屏幕（新增屏幕要 `screen/` + lang，超出本批与 B22 的范围）。
- 结论：**保留括号**，并把它登记为「有意差异」，避免后来者当成对齐遗漏顺手删掉。

---

## 5. 机器行（任务书 4：统一口径，供测试台断言）

### 5.1 全表

| 机器行 | 位置 | 侧 | 字段 | 触发 | 是否断开 |
|---|---|---|---|---|---|
| `AP_NET_VERSION_OK` | `:228` | server | `player=` `local=` `remote=` `side=server` `ticks=` | 服务端判通过 | 否 |
| `AP_NET_VERSION_OK` | `:289` | client | `local=` `remote=` `side=client` | 客户端判通过 | 否 |
| `AP_NET_VERSION_REJECT` | `:219` + `_DETAIL:222` | server | `player=` `local=` `remote=` `side=server` `ticks=` `action=disconnect` | **版本不兼容**（双方都装了） | **是** |
| `AP_NET_VERSION_REJECT` | `:280` + `_DETAIL:283` | client | `local=` `remote=` `side=client` `action=disconnect` | **版本不兼容**（双方都装了） | **是** |
| `AP_NET_VERSION_ABSENT` | `:241` + `_DETAIL:244` | server | `player=` `local=` `remote=ABSENT` `ticks=` `canSend=` `reason=client_channel_absent` `action=give_up` | 对端从未声明通道 | 否 |
| `AP_NET_VERSION_ABSENT` | `:257` + `_DETAIL:260` | server | `player=` `local=` `remote=NO_REPLY` `ticks=` `canSend=` `reason=client_no_reply` `action=give_up` | 声明了但没回包 | 否 |
| `AP_NET_VERSION_ABSENT` | `:337` + `_DETAIL:340` | client | `local=` `remote=ABSENT` `side=client` `canSend=false` `reason=server_channel_not_registered` `action=give_up` | 客户端回不出去（服务端没登记收包端） | 否 |
| `AP_NET_VERSION_SELFCHECK_FAIL` | `:135` / `:139` / `:144` | 双端（启动） | `channel_declared=` `version_gate=` / `channel=` / `local=` | 声明面与 `VersionGate` 口径不一致 | 否（仅 error 日志） |
| `AP_NET_VERSION_JOIN` | `:160` | server | `player=` `local=` `ticks=0` | 玩家进入 pending | 否 |
| `AP_NET_VERSION_PROBE` | `:186` | server | `player=` `local=` `ticks=` | probe 已发出 | 否 |

**语义划分（这是本批最重要的一条约定）**：

- `OK` = 判定通过；
- `REJECT` = **版本不兼容**，且是**唯一**会断开的机器行；
- `ABSENT` = **判定不出来**（超时 / 对端没装 / 回包发不出去），一律 `action=give_up`、**绝不断开**。

> ⚠️ 口径变更提示：B22 时代「没装」和「没回包」都记 `AP_NET_VERSION_REJECT: ... remote=ABSENT|NO_REPLY`。
> 本批起这两者改记 `AP_NET_VERSION_ABSENT`。若测试台（如 `scripts/test/cases/VERSION-GATE-fabric-1.20.1.json`）
> 仍按旧串断言，需要改成 `AP_NET_VERSION_ABSENT` —— 本批**未改**任何测试用例文件（属越界范围）。

### 5.2 `grep -n "AP_NET_VERSION"` 原文（任务书 4 要求粘贴）

```text
36: *                      不通过 ⇒ AP_NET_VERSION_REJECT + 断开(文案含两端版本)
39: *                      不通过 ⇒ AP_NET_VERSION_REJECT + player.connection.disconnect(Component)
40: *                      通过   ⇒ 清 pending + AP_NET_VERSION_OK
41: *   200t(10s) 到点: 拿不到对端版本 ⇒ 从 pending 移除 + AP_NET_VERSION_ABSENT，**不断开**
135:            AstralDiceMod.LOGGER.error("AP_NET_VERSION_SELFCHECK_FAIL: channel_declared={} version_gate={}",
139:            AstralDiceMod.LOGGER.error("AP_NET_VERSION_SELFCHECK_FAIL: channel={} reason=predicates_not_declared",
144:            AstralDiceMod.LOGGER.error("AP_NET_VERSION_SELFCHECK_FAIL: local={} reason=own_version_rejected", local);
160:        AstralDiceMod.LOGGER.info("AP_NET_VERSION_JOIN: player={} local={} ticks=0",
186:                    AstralDiceMod.LOGGER.info("AP_NET_VERSION_PROBE: player={} local={} ticks={}",
219:            AstralDiceMod.LOGGER.warn("AP_NET_VERSION_REJECT: player={} local={} remote={} side=server ticks={}",
222:                    "AP_NET_VERSION_REJECT_DETAIL: side=server reason=client_version_not_accepted player={}"
228:        AstralDiceMod.LOGGER.info("AP_NET_VERSION_OK: player={} local={} remote={} side=server ticks={}",
241:                "AP_NET_VERSION_ABSENT: player={} local={} remote={} ticks={} canSend={}",
244:                "AP_NET_VERSION_ABSENT_DETAIL: side=server reason=client_channel_absent player={} local={}"
257:                "AP_NET_VERSION_ABSENT: player={} local={} remote={} ticks={} canSend={}",
260:                "AP_NET_VERSION_ABSENT_DETAIL: side=server reason=client_no_reply player={} local={}"
280:            AstralDiceMod.LOGGER.warn("AP_NET_VERSION_REJECT: local={} remote={} side=client",
283:                    "AP_NET_VERSION_REJECT_DETAIL: side=client reason=server_version_not_accepted local={}"
289:        AstralDiceMod.LOGGER.info("AP_NET_VERSION_OK: local={} remote={} side=client", local, serverVersion);
337:                        "AP_NET_VERSION_ABSENT: local={} remote={} side=client canSend=false",
340:                        "AP_NET_VERSION_ABSENT_DETAIL: side=client reason=server_channel_not_registered"
```

计 **21 行**（任务书要求 ≥ 3 条），三族齐全：`OK` 2 处、`REJECT` 2 处（+2 条 DETAIL）、`ABSENT` 3 处（+3 条 DETAIL），另有 javadoc 4 处 + `SELFCHECK_FAIL` 3 处 + `JOIN` / `PROBE` 各 1 处。

---

## 6. `disconnect` 调用点全表 + 代码级证明（任务书 c）

### 6.1 `grep -n "disconnect"` 原文

```text
39: *                      不通过 ⇒ AP_NET_VERSION_REJECT + player.connection.disconnect(Component)
223:                            + " local={} remote={} ticks={} action=disconnect",
225:            disconnect(player, mismatchText(local, clientVersion));
265:    private static void disconnect(ServerPlayer player, String text) {
266:        player.connection.disconnect(Component.literal(text));
284:                            + " remote={} action=disconnect",
286:            ClientSide.disconnect(mismatchText(local, serverVersion));
348:        private static void disconnect(String text) {
356:            // 1.20.1 的 ClientPacketListener 没有 disconnect(Component) —— 那是 ServerGamePacketListenerImpl 上的。
357:            listener.getConnection().disconnect(reason);
```

### 6.2 逐行定性（全表）

| 行 | 性质 | 所在方法 | 唯一调用者 / 分支 | 会断开的场景 |
|---|---|---|---|---|
| `:39` | javadoc 注释 | 类注释 | — | 无 |
| `:223` | 日志串片段 `action=disconnect` | `onReplyReceived` | 与 `:219` 同分支 | — |
| **`:225`** | **真实断开调用** | `onReplyReceived` | 紧跟 `:218` `if (!VersionGate.accepts(clientVersion))` | 双方都装了、服务端不接受客户端 |
| `:265-266` | 断开函数**定义**（服务端） | `disconnect(ServerPlayer,String)` | 仅被 `:225` 调用 | — |
| `:284` | 日志串片段 `action=disconnect` | `onProbeReceived` | 与 `:280` 同分支 | — |
| **`:286`** | **真实断开调用** | `onProbeReceived` | 紧跟 `:279` `if (!VersionGate.accepts(serverVersion))` | 双方都装了、客户端不接受服务端 |
| `:348` | 断开函数**定义**（客户端） | `ClientSide.disconnect(String)` | 仅被 `:286` 调用 | — |
| `:356` | 注释 | `ClientSide.disconnect` 体内 | — | 无 |
| `:357` | 底层 `Connection#disconnect` | `ClientSide.disconnect` 体内 | 体内唯一 `if` 是 `:351 if (listener == null) return;` | — |

⇒ **`disconnect` 的真实调用只有 2 处（`:225` / `:286`），且都在 `!VersionGate.accepts(...)` 分支内**；
`onServerTick`（唯一评估超时的地方）与两个 `giveUp*` 里**一个 `disconnect` 都没有**。

### 6.3 静态逐行证明（脚本输出原文）

检查脚本：按花括号配对切出每个方法体，统计体内是否出现 `disconnect(` 与 `VersionGate.accepts`；再对每个 `disconnect(` 调用点回溯最近的 `if (` 行。

```text
== method scan ==
onJoin             lines 155-162  contains_disconnect=False  contains_VersionGate_accepts=False
selfCheck          lines 130-146  contains_disconnect=False  contains_VersionGate_accepts=False
onServerTick       lines 164-203  contains_disconnect=False  contains_VersionGate_accepts=False
onReplyReceived    lines 211-230  contains_disconnect=True  contains_VersionGate_accepts=True
giveUpAbsent       lines 239-247  contains_disconnect=False  contains_VersionGate_accepts=False
giveUpNoReply      lines 255-263  contains_disconnect=False  contains_VersionGate_accepts=False
disconnect         lines 265-267  contains_disconnect=True  contains_VersionGate_accepts=False
onProbeReceived    lines 277-291  contains_disconnect=True  contains_VersionGate_accepts=True
waitedTicks        lines 310-315  contains_disconnect=False  contains_VersionGate_accepts=False
serverTickOf       lines 317-320  contains_disconnect=False  contains_VersionGate_accepts=False
reply              lines 332-346  contains_disconnect=False  contains_VersionGate_accepts=False
install            lines 113-122  contains_disconnect=False  contains_VersionGate_accepts=False

== disconnect( call sites: nearest enclosing if ==
L39 CALL method=None(?) nearest if L?: ?
L225 CALL method=onReplyReceived(?) nearest if L218: if (!VersionGate.accepts(clientVersion)) {
L266 CALL method=disconnect(?) nearest if L?: ?
L286 CALL method=onProbeReceived(?) nearest if L279: if (!VersionGate.accepts(serverVersion)) {
L356 CALL method=None(?) nearest if L351: if (listener == null) {
L357 CALL method=None(?) nearest if L351: if (listener == null) {
```

逐条读法：

1. `onServerTick`（164-203）`contains_disconnect=False` 且 `contains_VersionGate_accepts=False` ⇒ **超时路径既不比对也不断开**，裁决 1/2 的「不断开」由此成立。
2. `giveUpAbsent`（239-247）/ `giveUpNoReply`（255-263）`contains_disconnect=False` ⇒ 两条超时收尾只有日志。
3. `onReplyReceived` / `onProbeReceived` 两者 `contains_disconnect=True` 且 `contains_VersionGate_accepts=True` ⇒ 断开与版本判定**同处一个方法**，且回包处理是在收到包的那一帧被调用的（`VersionReplyMessage` / `VersionProbeMessage` 的 `enqueueWork`），即「收到即判、判否即断」。
4. `L225` / `L286` 的「最近 enclosing if」正好是两处 `!VersionGate.accepts(...)` ⇒ 断开**只**发生在版本不兼容分支。
5. `L266` / `L357` 是函数体内部，回溯到的是 `disconnect` 自己（或 `listener == null` 的空判）⇒ 不构成新的断开条件。`L39` / `L356` 是注释。

---

## 7. 验收原始输出（任务书 a / b / d）

### a) `python scripts/__javac_check.py` —— REAL errors 为空

```text
EXIT 1 ERRORS 171 jars 685 srcs 525
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  164  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 984
```

- `ERRORS 171` 与基线逐项一致（B22 报告 §5a 记为 `EXIT 1 ERRORS 171 jars 685 srcs 525`，同 `164 + 7`）。
- `=== REAL errors ===` 与 `=== REAL by file ===` 之后**为空** ⇒ REAL errors 为空。
- `EXIT 1` 是脚本在「有噪声桶」时的既有返回码，不是本次引入的失败。

### b) `grep -n "AP_NET_VERSION" ...` —— 见 §5.2（20 行、三族齐全）

### c) `disconnect` 只在版本不兼容分支被调用 —— 见 §6（全表 + 静态逐行证明）

### d) `python tools/verify_fabric_assets.py`

```text
jar = fabric-1.20.1\build\libs\astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar

[!] build/libs 内 jar 版本 1.3.7-alpha.2+fabric_1.20.1 与 mod_version 1.5.0-rc.7+fabric_1.20.1 不符，改为扫描源码资源目录
[1] 物品闭环: 注册 157 (方块物品 3) / 模型 158 / 贴图 155 (方块贴图另计)
[2] 标签闭环: 引用 11 / 自建 29 / 有提供者 11
[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
[4] 粒子闭环: 注册 2 / particles/*.json 2
[5] 语言闭环: zh_cn 1094 / en_us 1094 / ja_jp 1094
[6] 手册闭环: 条目 184 / 引用配方 147
[7] 创意标签覆盖: 156/157
    已知与基线一致（不阻塞）: ['repulsion']
[8] 饰品槽位图标: 检查 7 条 icon

=== PASS: 8 项闭环全部通过 ===
```

`[!]` 那行是**既有现象**（`build/libs` 里躺着旧 jar），脚本自动改用源码资源目录，8 项仍全过。

### e) 文件格式自检（CRLF + UTF-8 无 BOM）

```text
file= fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionHandshake.java
CRLF= 361 LF= 361 bareLF= 0 BOM= False bytes= 19483 endswith_newline= True
file= porting/fabric-1.20.1/B22-VERSION-HANDSHAKE-REPORT.md
CRLF= 383 LF= 383 bareLF= 0 BOM= False bytes= 29988 endswith_newline= True
```

（字节级统计：`CRLF` 计数 == `LF` 计数 ⇒ **bareLF = 0**；`BOM=False`；`endswith_newline=True`。）

---

## 8. 只能实机确认的清单（本批未启动游戏）

| # | 待确认 | 预期 | 判定方式 |
|---|---|---|---|
| 1 | **原版（未装本模组）客户端连专用服务器不再被踢** | 进服后约 10s 打 `AP_NET_VERSION_ABSENT ... remote=ABSENT action=give_up`，玩家**留在服里** | 服务端 `latest.log` 找该行 + 客户端仍可走动 |
| 2 | 装了但没回包的路径（旧客户端/丢包） | 同上报 `remote=NO_REPLY action=give_up`，不断开 | 同上 |
| 3 | 真正的版本不兼容**仍立即断开**（不是等 10s） | 回包到达的那一帧就断，文案含两端版本 | 两端 `mod_version` 或互通号故意错开；观察断开时刻 ≪ 200t |
| 4 | 断开界面文案完整可见 | 玩家能在 `DisconnectedScreen` 看到 `... (astral_dice main: local=…, remote=…)` 未被截断 | 截图 |
| 5 | `canSend` 实际在第几 tick 变真 | 推断 1 tick（高延迟 2-3 tick） | `AP_NET_VERSION_PROBE` 的 `ticks=` 分布 |
| 6 | 单人 / 局域网不被误踢 | 200t 内完成，无 `ABSENT` 行 | 日志 |
| 7 | 客户端主动断开时服务端是否二次断开/重复提示 | 无异常 | 日志 + 客户端界面 |
| 8 | `AP_NET_VERSION_SELFCHECK_FAIL` 是否会在某些构建形态误报 | 无输出 | 启动日志 |
| 9 | 超时后迟到的回包（`ticks=-1`）行为 | 通过记 `OK ticks=-1`；不兼容**仍断开** | 模拟慢回包 |

---

## 9. 未做 / 越界 / 编号说明

1. **报告编号**：任务书要求「在 B22 报告追加 §8 裁决落地」，但 B22 报告 §8 已被〈附：本批未做〉占用。为遵守「不要重写其他人的正文」，本批把该节追加为 **§9「裁决落地（B25）」**，并在 §9 开头写明了编号顺延与「§3.4 / §3.5 / §4.3 / §7 第 1、2 条已被取代」。
2. **未做**：没新增自定义断开屏幕（要 `screen/` + lang，越界）；没改 `VersionGate` 口径；没改测试用例 `scripts/test/cases/VERSION-GATE-fabric-1.20.1.json`（旧断言串需下游同步，见 §5.1 提示）。
3. **未做**：没碰 `ModNetwork.java`。`:117` 注释「失配即断开」在新语义下仍然准确，无需改写。
4. **越界防护**：本批只在 `VersionHandshake.java` 与两个 `porting/fabric-1.20.1/*.md` 上落笔，`platform/network/**` 仅只读调用。
5. **遗留临时文件（仓库外、未跟踪，不影响任何验收）**：`F:\MCProject\.b25-eolcheck.py`（字节级 CRLF/BOM 检查）、`F:\MCProject\.b25-append.py`（CRLF 归一 + 追加）、`F:\MCProject\.b25-appendix.md`（B22 §9 正文源）、`F:\MCProject\.b25-probe.py`（§6.3 静态分析脚本）。本批结束时尝试删除，**被 NTFS ACL 拒绝**（`PermissionError: [WinError 5] 拒绝访问`，与仓库根目录既有的 ACL 问题同源，仓库内另有 `acl-backup-*.txt` 记录），故如实登记；它们都在仓库 `F:\MCProject\astral_dice_multiloader` **之外**，不进 `git status`，需要清理时手工删即可。

---

## 10. 复现命令

```bash
cd /f/MCProject/astral_dice_multiloader

# a) 编译门禁（REAL errors 应为空）
python scripts/__javac_check.py

# b) 机器行（应 >= 3 条）
grep -n "AP_NET_VERSION" fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionHandshake.java

# c) 断开点
grep -n "disconnect" fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionHandshake.java

# d) 资产闭环（应 PASS 8/8）
python tools/verify_fabric_assets.py
```

（Windows 宿主同目录下把 `cd /f/...` 换成 `cd /d F:\...` 或直接在仓库根执行即可。）

