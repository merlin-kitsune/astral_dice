# B2-6 事件体系客户端层：forge-1.20.1 → fabric-1.20.1 移植报告

日期 2026-11-01 · 施工线 fabric-1.20.1 · 蓝本线 forge-1.20.1 · 批次 B2-6（客户端层）
本次只补「类的不存在」：把 forge 的 3 个客户端类按文件 1:1 搬到 fabric，使 4 个事件网络消息类的调用点可解析。
服务端会话管理（`EventChoiceManager`）、事件闸门（`GlobalEventGate` / `AstralEventDispatcher` 的 ITEM_USE 单点）**一律未动**；`EventChoiceManager`
自 2026-10-29 起不预判硬间隔的口径保持原样，未做任何「顺序修复」。

## 1. 落点与规模

| # | 文件 | forge（行） | fabric（行） | 状态 |
|---|---|---|---|---|
| 1 | `client/EventCardOverlay.java` | 266 | 281 | 新建 |
| 2 | `client/EventChoiceClientHandler.java` | 63 | 70 | 新建 |
| 3 | `client/EventChoiceScreen.java` | 361 | 368 | 新建 |
| 4 | `client/ModClientEvents.java` | — | 42–44 | 只加注册（2 行注释 + 1 行注册） |

全部落在 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/`；无其它新增文件。

## 2. 三类逐项对照（forge 行号 → fabric 行号）

偏移是**常量**：EventCardOverlay `+15`（类 javadoc 加长）、Handler `+7`、Screen `+7` ⇒ `fabric 行 = forge 行 + 偏移`；下列锚点全部落在该偏移上。

**EventCardOverlay（+15）**：`LAYER_ID` 23→38 · `FRAME_W/H` 27/29→42/44 · `PANE_X/Y/W/H` 30–33→45–48 ·
`NAME_Y_OFF/TIME_Y_OFF` 36/37→51/52 · `IN_MS/OUT_MS` 38/39→53/54 · `ANIM_MS/HOLD_MS` 45/47→60/62 ·
`TOTAL_MS/OUT_END_SCALE` 55/57→70/72 · `FRAME_TEX` 58→73 · `CARDS` 61→76 · `show` 71→86 · `OVERLAY` 80→95 ·
`render` 82→97 · `drawCard` 113→128 · `drawCardAt` 165→180 · `iconPath` 193→208 · `formatTicks` 202→217 ·
`RECT_CACHE` 209→224 · `drawFaceCentered` 217→232 · `measureContent` 232→247。

**EventChoiceClientHandler（+7）**：构造器 29→36 · `open` 33→40 · `updateCountdown` 41→48 · `closeIfToken` 56→63。

**EventChoiceScreen（+7）**：`PANEL_W/H` 48/49→55/56 · `TITLE_X/Y/W/H` 51–54→58–61 · `GLYPH_QUAD_H` 56→63 ·
`CARD_Y` 58→65 · `CARD_FIRST_X/CARD_SECOND_X` 59/60→66/67 · `CONFIRM_X/Y/W/H` 62–65→69–72 · 贴图常量 67–72→74–79 ·
构造器 105→112 · `token` 115→122 · `tex` 119→126 · `originX/originY/cardX/cardY` 123/127/131/135→130/134/138/142 ·
`confirmX/confirmY/overCard/overConfirm` 139/143/147/153→146/150/154/160 · `render` 160→167 ·
`setServerRemainingTicks` 200→207 · `formatCountdown` 205→212 · `mouseClicked/mouseReleased` 211/229→218/236 ·
`isPauseScreen/keyPressed/keyReleased` 244/256/286→251/263/293 · `tick/shouldCloseOnEsc` 301/314→308/321 ·
`onClose/removed/requestExit/markClosedByServer` 326/341/349/358→333/348/356/365。

**机械比对（LCS 逐行 diff，剔除注释行）**：Handler 非注释差异 **0 行**；Overlay 差异 **5 对**（import 1 + `ResourceLocation` 工厂 4）；
Screen 差异 **1 对**（`tex()` 的工厂 1）。`static final` 常量集合 19/19、0/0、21/21；其中 Overlay 唯一不同的两条是 `LAYER_ID`/`FRAME_TEX`
**同一 ns/path 的工厂改写**——坐标常量、颜色值、键位、鼠标行为、倒计时口径（`DELAY_TICKS=38 = CARD_ANIM 28 + SETTLE 10`）零变动。

## 3. IGuiOverlay 垫片适配点

- 垫片 `platform/client/gui/overlay/IGuiOverlay.java`：`void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height)`，与 forge 同名接口**同形**。
- `EventCardOverlay.java:12` 只换 import；`OVERLAY` 的 lambda（80→95）**逐字未改**：形参类型由函数式目标推断，不需 import `ForgeGui`，方法体也不用 `gui`。
- **语义差异（必须记）**：forge 的 `registerAbove(anchor,…)` 是相对原版元素定位；fabric 垫片只记条目，逐帧派发在 `FabricClientBridges`（`HudRenderCallback`），
  **没有锚点排序**，渲染顺序 = 注册顺序。故 `event_card` 排到 `registerGuiOverlays` 第一条以对齐 forge 的「准星之上」；该等价性需实机复核（第 7 节）。
- 客户端隔离：`EventCardOverlay` 仅被客户端入口 `ModClientEvents` 与 4 个消息类的 `handle()` 引用，未出现在服务端初始化链。

## 4. ResourceLocation 工厂改写清单（1.20.1 merged jar 无 `ResourceLocation.fromNamespaceAndPath`）

| # | 位置 | forge | fabric | 改写 |
|---|---|---|---|---|
| 1 | `EventCardOverlay.LAYER_ID` | 24 | 39 | `new ResourceLocation(AstralDiceMod.MODID, "event_card")` |
| 2 | `EventCardOverlay.FRAME_TEX` | 59 | 74 | 同上，path=`textures/gui/event/card_frame.png` |
| 3 | `drawCard` 的 icon | 115 | 130 | `new ResourceLocation(card.eventId().getNamespace(), path)` |
| 4 | `drawCardAt` 的 icon | 168 | 183 | `new ResourceLocation(eventId.getNamespace(), path)` |
| 5 | `EventChoiceScreen.tex(String)` | 120 | 127 | `new ResourceLocation(AstralDiceMod.MODID, "textures/gui/shop/" + name)` |

`EventChoiceScreen` 109→116 的 `new ResourceLocation[] {…}` 是数组构造，非工厂调用，未改。三个新文件已无该工厂调用点（第 6 节 grep）。

## 5. 与 4 个消息类的签名对账表

| 消息类调用点 | 调用 | 目标（fabric 定义） | 结果 |
|---|---|---|---|
| `EventCardMessage.java:45` | `EventCardOverlay.show(eventId, durationTicks, tier)` | `EventCardOverlay.java:86 static void show(ResourceLocation, int, int)` | 匹配 |
| `EventChoiceStartMessage.java:63` | `EventChoiceClientHandler.open(token, firstEvent, secondEvent, firstDurationTicks, secondDurationTicks, timeoutTicks)` | `EventChoiceClientHandler.java:40 static void open(int, ResourceLocation, ResourceLocation, int, int, int)` | 匹配 |
| `EventChoiceCloseMessage.java:40` | `EventChoiceClientHandler.closeIfToken(token)` | `EventChoiceClientHandler.java:63 static void closeIfToken(int)` | 匹配 |
| `EventChoiceTickMessage.java:41` | `EventChoiceClientHandler.updateCountdown(token, remainingTicks)` | `EventChoiceClientHandler.java:48 static void updateCountdown(int, int)` | 匹配 |

`EventChoiceScreen` 对 handler 暴露的面：构造器 `:112 (int, ResourceLocation, ResourceLocation, int, int, int)`、`token() :122`、
`setServerRemainingTicks(int) :207`、`markClosedByServer() :365`；`EventChoiceConfirmMessage(int,int)` / `EventChoiceCancelMessage(int)` 的既有构造未改。

## 6. ModClientEvents 注册（唯一改动点）

`client/ModClientEvents.java:42-44`（forge 蓝本 `ModClientEvents.java:47-48`）：

```java
// 事件效果卡(B2-6,1:1 移植自 forge-1.20.1 同名注册):接在准星之上(瞬时动画,不遮准星本身)。
// 注册顺序与 forge 蓝本一致 —— event_card 排在 damage_number 之前。
event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "event_card", EventCardOverlay.OVERLAY);
```

插在 `registerGuiOverlays` 方法体第一行，与 forge 顺序一致（event_card 先于 damage_number）；同包无需 import。
该文件另有**并行批次**改动（`CardChoiceScreen`/`CardShopScreen`/`ChipShopScreen` 的 import 与 `onClientSetup` 段），非本批次所为，未触碰。

## 7. javac 自检（REAL 分桶前后对比，原始输出）

### 7.1 官方脚本 `python scripts/__javac_check.py`

```text
改动前（本会话首跑）：EXIT 1  ERRORS 174  jars 680  srcs 502
  163  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===（4 条）
  cannot find symbol | symbol: class EventChoiceClientHandler   ×3
  cannot find symbol | symbol: class EventCardOverlay           ×1

改动后（当前复跑）：EXIT 1  ERRORS 170  jars 685  srcs 516
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  163  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===          （空）
=== REAL by file ===          （空）
saved errors.txt lines= 978
```

`errors.txt` 归属核对（同一份输出）：`grep -c 'EventCardOverlay|EventChoiceClientHandler|EventChoiceScreen'` = **0**；
`ModClientEvents` 8 处命中全部是 `MenuScreens.register has private access`（NOISE_AW，第 64–68 行的并行批次代码），与本批次第 44 行无关。

### 7.2 受控 A/B（同一 javac、同一 classpath，只差本批次改动）

期间 `~/.gradle/caches/fabric-loom/minecraftMaven/…` 下的 1.20.1 merged jar 曾被并行进程清空（本批次全程未跑 gradle），
为拿到可比对的证据，把项目内 `.gradle/loom-cache/minecraftMaven/…/minecraft-merged-1f7f9a6284-….jar` 补到 cp 首位后做 A/B：

```text
BEFORE（移除我新建的 3 个文件 + 还原 ModClientEvents 的 3 行）：exit=1  errors=4
  network/EventCardMessage.java:45: error: cannot find symbol → EventCardOverlay.show(...)
  network/EventChoiceCloseMessage.java:40: error: cannot find symbol → EventChoiceClientHandler.closeIfToken(...)
  network/EventChoiceStartMessage.java:63: error: cannot find symbol → EventChoiceClientHandler.open(...)
  network/EventChoiceTickMessage.java:41: error: cannot find symbol → EventChoiceClientHandler.updateCountdown(...)

AFTER （当前工作树）：exit=0  errors=0（517 个源文件全部编译通过）
```

### 7.3 结论

官方脚本 REAL 桶的 4 条 `cannot find symbol`（EventCardOverlay / EventChoiceClientHandler）**全部消失**；新增 3 个文件自身 0 错误；
`NOISE_AW` / `NOISE_IFACE_INJECTION` 为既有噪声，未处理；`audio/ModSounds` 的 EVENT_* 缺失属并行批次，未动。

## 8. grep 铁律（fabric-1.20.1/src/main/java，mine = 本批次 4 个文件）

| 模式 | 全树命中 | 本批次命中 |
|---|---|---|
| `net.minecraftforge` | 9 | **0** |
| `net.neoforged` | 1 | **0** |
| `top.theillusivec4.curios` | 10 | **0** |
| `ResourceLocation.fromNamespaceAndPath` | 6 | **0** |

全树其余命中全部落在**他人文件的 javadoc / 块注释**里（说明「本线没有这个 API」的历史注记），本批次未改他人文件。文件编码 UTF-8 无 BOM、行尾 CRLF（逐字节核对：3 个新文件 bare LF = 0、BOM = false）。

## 9. 未做项 / 需实机 / 无法判定

1. **需实机**：fabric 无锚点排序 ⇒ `event_card` 与其他 HUD 元素的真实叠放次序、是否遮挡准星/热键栏，需进游戏目视确认（代码侧只能保证注册顺序）。
2. **需实机**：卡面 alpha/缩放动画（`IN_MS/OUT_MS/OUT_END_SCALE`）、`RenderSystem.setShaderColor` + `g.flush()` 的时序观感、`a>=4` 才画文字的边界表现。
3. **需实机**：`measureContent`（`NativeImage` 量 alpha>8 bbox + `RECT_CACHE`）在真实贴图集上的取框结果与缓存命中。
4. **需实机**：选择界面的两段式 ESC、`escHeld` 防 REPEAT、失焦复位、拖选/点击命中面、服务端权威倒计时（`serverRemainingTicks`）在真实延迟下的显示。
5. 未跑 gradle / 未启动游戏 / 未 commit / 未删文件 / 未改 `build.gradle`；未碰 `audio/`。
6. **无法判定**：`FabricClientBridges` 派发时 `ForgeGui.INSTANCE` 的 `leftHeight/rightHeight` 复位与本 overlay 无交互（本类不读写这两个字段），但若后续有 overlay 依赖它们，需另测。
