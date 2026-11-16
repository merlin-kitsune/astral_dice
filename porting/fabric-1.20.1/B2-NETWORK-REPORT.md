# B2 网络层移植报告（forge-1.20.1 → fabric-1.20.1）

- 批次：**B2-NETWORK**（事件系统 6 个网络消息类 + 注册），生成 2026-11-01。
- 蓝本（只读、未改）：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/` 下 6 个同名文件；id 顺序取自 `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:82-116`。
- 落点（只写了这 7 个文件，全部 CRLF / 无 BOM / UTF-8）：
  - 新增 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/` 下：`EventCardMessage.java`(50 行) `EventChoiceStartMessage.java`(70) `EventChoiceConfirmMessage.java`(48) `EventChoiceCloseMessage.java`(45) `EventChoiceTickMessage.java`(46) `EventChoiceCancelMessage.java`(56)
  - 修改 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java:97-113`（`register()` 末尾追加 6 条注册 + 5 行注释；文件 730 → 747 行，+20 行）。

## 1. id 映射表

本线注册到 id 21（`CardChoiceConfirmMessage`=20、`CardChoiceCloseMessage`=21），6 个新消息接 22..27；
**顺序 = 蓝本 forge `ModNetwork.register()` 的 id 顺序**（16 < 17 < 18 < 19 < 20 < 27）。

| 本线 id | 类 | 蓝本 forge id | 方向 / 语义 |
|---|---|---|---|
| 22 | `EventCardMessage` | 16 | S→C 事件效果卡（eventId + durationTicks + tier） |
| 23 | `EventChoiceStartMessage` | 17 | S→C 打开事件选择界面（token + 2 选项 + timeout） |
| 24 | `EventChoiceConfirmMessage` | 18 | C→S 确认所选选项（token + optionIndex） |
| 25 | `EventChoiceCloseMessage` | 19 | S→C 关闭选择界面（token） |
| 26 | `EventChoiceTickMessage` | 20 | S→C 倒计时同步（token + remainingTicks） |
| 27 | `EventChoiceCancelMessage` | 27 | C→S 退出界面 / 退出即结算（token） |

- 与「本线空位 22..27」**无冲突**（不需启用「空位优先」例外）。唯一同号巧合：`EventChoiceCancelMessage` 两条线都是 **27**；蓝本里它夹在 26(`BeamFxPayload`)/28(`TargetSelectCloseMessage`) 之间，本线落在最后一个空位，**相对位次（第 6 位 = 末尾）与蓝本一致**。
- 实测复核（按 `register()` 文本出现顺序自动编号，非人工数）：

```
22  EventCardMessage
23  EventChoiceStartMessage
24  EventChoiceConfirmMessage
25  EventChoiceCloseMessage
26  EventChoiceTickMessage
27  EventChoiceCancelMessage
total registered: 28
```

## 2. 每类字段清单与蓝本行号

| 类 | 字段（类型；蓝本行 → 本线行） | encode 字段顺序（蓝本行 → 本线行） |
|---|---|---|
| `EventCardMessage` | `eventId`(ResourceLocation; 13→22)、`durationTicks`(int; 14→23)、`tier`(int; 15→24) | `writeResourceLocation`→`writeVarInt`→`writeVarInt`（23-27→32-36） |
| `EventChoiceStartMessage` | `token`(int; 26→29)、`firstEvent`(RL; 27→30)、`secondEvent`(RL; 28→31)、`firstDurationTicks`(int; 29→32)、`secondDurationTicks`(int; 30→33)、`timeoutTicks`(int; 31→34) | token→firstEvent→secondEvent→firstDuration→secondDuration→timeout（43-50→46-53） |
| `EventChoiceConfirmMessage` | `token`(int; 17→22)、`optionIndex`(int; 18→23) | token→optionIndex（25-28→30-33） |
| `EventChoiceCloseMessage` | `token`(int; 17→23) | token（23-25→29-31） |
| `EventChoiceTickMessage` | `token`(int; 17→21)、`remainingTicks`(int; 18→22) | token→remainingTicks（25-28→29-32） |
| `EventChoiceCancelMessage` | `token`(int; 29→33) | token（35-37→39-41） |

- 字段数量、类型、声明顺序、读写顺序、构造器签名**逐条一致**，无精简、无增补（`decode(...)` 的实参顺序同样一一对应）。
- `handle` 语义一致：S2C 四条（22/23/25/26）走 `FMLEnvironment.dist == Dist.CLIENT` 守卫 + `enqueueWork`；C2S 两条（24/27）取 `getSender()`、判 null 后调 `EventChoiceManager.confirm(player, token, optionIndex)` / `EventChoiceManager.exitWithoutChoice(player, token, "esc")`（本线 `EventChoiceManager.java:147` / `:195` 已存在，签名一致）。

## 3. 等价改写点（逐条，仅 3 处 import 替换，类体零改动）

1. `net.minecraftforge.network.NetworkEvent` → `com.merlinkitsune.astral_dice.platform.network.NetworkEvent`（本线垫片；`getSender` / `enqueueWork` / `setPacketHandled` 三件套语义与 forge 一致，见 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/network/NetworkEvent.java:18-45`）。
2. `net.minecraftforge.api.distmarker.Dist` → `com.merlinkitsune.astral_dice.platform.api.distmarker.Dist`；`net.minecraftforge.fml.loading.FMLEnvironment` → `com.merlinkitsune.astral_dice.platform.fml.loading.FMLEnvironment`（范式与语义见同目录 `CardChoiceCloseMessage.java:3-4,40`）。
3. 蓝本**没有**用 `DistExecutor` / `StreamCodec` / `record` / `ResourceLocation.fromNamespaceAndPath`，故无需额外等价改写；序列化全部保持 1.20.1 存在的 `FriendlyByteBuf#writeVarInt` / `writeResourceLocation` / `readVarInt` / `readResourceLocation`。
4. 客户端分发结构保持蓝本原样：方法体**不出现** `Minecraft` / `Screen` 等客户端专属类型，S2C 客户端逻辑经 `client/EventCardOverlay`、`client/EventChoiceClientHandler` 转发（同 `CardChoiceCloseMessage` 口径）。
5. **类体逐字节等价性实测**（脚本取每个文件 `public final class` 起至文件尾，归一化行尾后逐字节比较）：

```
EventCardMessage:            class-body identical = true  (forge 30 lines / fabric 30 lines)
EventChoiceStartMessage:     class-body identical = true  (forge 43 lines / fabric 43 lines)
EventChoiceConfirmMessage:   class-body identical = true  (forge 28 lines / fabric 28 lines)
EventChoiceCloseMessage:     class-body identical = true  (forge 24 lines / fabric 24 lines)
EventChoiceTickMessage:      class-body identical = true  (forge 27 lines / fabric 27 lines)
EventChoiceCancelMessage:    class-body identical = true  (forge 25 lines / fabric 25 lines)
```

6. **注册写法沿用本线既有 `id++`（未改成字面量 22..27）**，理由：`register()` 末尾若写字面量，局部变量 `id` 会停在 22，下一个批次继续 `id++` 就撞号；`id++` 追加后 `id` 停在 28，且实测编号正是 22..27（§1）。具体 id 已写进 `ModNetwork.java:97-101` 的注释块。

## 4. javac 自检原始输出（`python scripts/__javac_check.py`）

### 4.1 改动前（REAL 17 条）

```
EXIT 1 ERRORS 187 jars 674 srcs 496
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  163  NOISE_AW
   17  REAL
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
    3  cannot find symbol | symbol:   class EventChoiceCloseMessage location: class EventChoiceManager
    2  cannot find symbol | symbol:   variable EVENT_HEAL location: class ModSounds
    2  cannot find symbol | symbol:   variable EVENT_DAMAGE location: class ModSounds
    1  cannot find symbol | symbol:   class EventChoiceCloseMessage location: package com.merlinkitsune.astr
    1  cannot find symbol | symbol:   class EventChoiceStartMessage location: package com.merlinkitsune.astr
    1  cannot find symbol | symbol:   class EventChoiceTickMessage location: package com.merlinkitsune.astra
    1  cannot find symbol | symbol:   class EventCardMessage location: package com.merlinkitsune.astral_dice
    1  cannot find symbol | symbol:   variable EVENT_FADE_IN location: class ModSounds
    1  cannot find symbol | symbol:   variable EVENT_POISONING location: class ModSounds
    1  cannot find symbol | symbol:   variable EVENT_TRANSPORT location: class ModSounds
    1  cannot find symbol | symbol:   variable EVENT_BONUS location: class ModSounds
    1  cannot find symbol | symbol:   class EventChoiceStartMessage location: class EventChoiceManager
    1  cannot find symbol | symbol:   class EventChoiceTickMessage location: class EventChoiceManager
```

### 4.2 改动后（REAL 17 → 4）

```
EXIT 1 ERRORS 174 jars 674 srcs 502
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  163  NOISE_AW
    7  NOISE_IFACE_INJECTION
    4  REAL
=== REAL errors ===
    3  cannot find symbol | symbol:   class EventChoiceClientHandler location: package com.merlinkitsune.ast
    1  cannot find symbol | symbol:   class EventCardOverlay location: package com.merlinkitsune.astral_dice
saved errors.txt lines= 998
```
- **复跑快照**（并行批次仍在落盘，仅作对照）：`EXIT 1 ERRORS 177 jars 680 srcs 504` → `163 NOISE_AW / 7 NOISE_IFACE_INJECTION / 7 REAL` = 上表 4 条 + 3 条 `ShopCooldownClientProvider`（`AstralDiceJadePlug`，属**并行 Jade 批次**，非本批）。

- 6 个目标类自身的 `cannot find symbol`（`EventCardMessage` / `EventChoiceStartMessage` / `EventChoiceCloseMessage` / `EventChoiceTickMessage` 在 `EventChoiceManager` 与 import 处的 9 条）**全部消失**；8 条 `ModSounds.EVENT_*`（并行批次职责）也已由并行批次补齐，本批未触碰 `audio/ModSounds.java`。
- 残留 4 条的 `symbol` 均为 **client 包类**，不是本批 6 个消息类：

```
EventCardMessage.java:45         -> com.merlinkitsune.astral_dice.client.EventCardOverlay
EventChoiceCloseMessage.java:40  -> com.merlinkitsune.astral_dice.client.EventChoiceClientHandler.closeIfToken
EventChoiceStartMessage.java:63  -> com.merlinkitsune.astral_dice.client.EventChoiceClientHandler.open
EventChoiceTickMessage.java:41   -> com.merlinkitsune.astral_dice.client.EventChoiceClientHandler.updateCountdown
```

  取证：`grep -rn 'class EventCardOverlay|class EventChoiceClientHandler' fabric-1.20.1/src/main/java` → **0 命中**（只有 forge 线有），三者属 `porting/fabric-1.20.1/B2-EVENT-RECON.md` §7 的 **B2-6 客户端批次**，且 B2-EVENT-RECON.md:236 已写明「B2-5 与 B2-6 双向依赖，建议同一次提交」。本批落点在任务里被限定为 `network/` 下 6 新文件 + `ModNetwork.java`，故**未越界创建**这三个客户端类。

### 4.3 禁用符号自检

```
$ grep -c 'net.minecraftforge\|net.neoforged\|fromNamespaceAndPath' <6 个新文件>
EventCardMessage.java 0
EventChoiceStartMessage.java 0
EventChoiceConfirmMessage.java 0
EventChoiceCloseMessage.java 0
EventChoiceTickMessage.java 0
EventChoiceCancelMessage.java 0
```

### 4.4 `git status --porcelain` 实况（network 目录）

```
 M fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java
?? fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/EventCardMessage.java
?? fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/EventChoiceCancelMessage.java
?? fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/EventChoiceCloseMessage.java
?? fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/EventChoiceConfirmMessage.java
?? fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/EventChoiceStartMessage.java
?? fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/EventChoiceTickMessage.java
```
（同目录另有 6 个 `??`：`BeamFxPayload` / `CardChoiceClose|ConfirmMessage` / `ChipShop*Payload` —— 均为**先前批次**遗留的未跟踪文件，本批未触碰。）

## 5. 未做项 / 已知缺口

1. **B2-6 客户端三件套未做**（越界，需另批）：`client/EventCardOverlay`、`client/EventChoiceClientHandler`、`client/EventChoiceScreen` —— 它们是上面 4 条 REAL 的唯一成因，落地后本批 6 个消息类即全绿。
2. **未在 fabric 线跑 gradle / 未启动游戏 / 未验证运行期收发**（任务禁令）：结论只来自 javac 静态检查（mojmap jar + loom `remapped_mods` classpath）；22..27 只是登记，实际 S2C 下发由 `EventChoiceManager#tick/confirm/...` + 客户端 `EventChoiceScreen`（B2-6）驱动，需 B2-6 才能验协议闭环。另 `EventChoiceManager` 需在 `AstralDiceMod.registerListeners` 显式 `LoaderBus.INSTANCE.register(...)`（其类头 `:49-52` 已注明），属 B2-8 收尾项。
