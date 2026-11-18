# fabric-1.20.1 新立牌 tooltip 缺失：定位、修复与实机取证

日期：2026-11-02　线：fabric-1.20.1（1.5.0 正式版）

## 1. 现象
玩家侧：1.20.1 Fabric 上后加的 7 张立牌（alana / ame / deepseek / dorothy / jill / kangel / lingling）鼠标悬停时只显示物品名，没有「按下 J 触发」「主动技能」「被动技能」「计数」等内容；其余 24 张老立牌显示正常。

## 2. 根因
`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java` 的 `onItemTooltip` 只有 **25** 个立牌分支，而 neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 三线都是 **32** 个；七个新立牌的 `if (stack.is(ModItems.X_SIGN.get()))` 分支在这一线整体缺失。
事件链路本身是通的：`platform/client/FabricClientBridges.java:98-102` 用 `ItemTooltipCallback.EVENT` 把 `platform.event.entity.player.ItemTooltipEvent` post 到 LoaderBus（`:80 installTooltips()`），老立牌正常即证明。
语言键也齐备：三语 `sign.<id>.active/.passive/_active/_passive/note/fans/love_layers` 全部存在，缺的只有代码分支。

## 3. 修复
按 forge-1.20.1 的同源分支补齐 7 段（+89 行，CRLF 保持），其中：
- KAngel：`SignAttachments.getKangelFanCount(player)` / `KangelSignItem.MAX_FANS`（=10）计数行；
- Ame：`SignAttachments.getAmeLoveLayers(player)` / `AmeSignItem.getLoveCap(player)` 计数行；
- 其余 5 张走 active/passive 两段 + `addSignCooldownRemaining(tooltip, event.getEntity())`。

校验：`addSignActiveTitle` 命中 33 次 = helper 定义 1 + 32 个立牌分支；文件 1695 行。
构建：`gradlew :fabric-1.20.1:build -x pushToGame` ⇒ BUILD SUCCESSFUL，产物 `build/libs/astral_dice-1.5.0+fabric_1.20.1.jar`。

## 4. 实机取证（真客户端，非静态推导）
客户端：`gradlew :fabric-1.20.1:runClient -PautojsTestMod=1 -PtestSodium=false -Pquickplay=testworld`，AutoJS 端点 http://127.0.0.1:25585/mcp = READY。

(a) 直接调用真实处理器（AutoJS eval，客户端线程）：
```
ModTooltipHandler.onItemTooltip(new ItemTooltipEvent(stack, player, lines, null))
alana 9 行 / ame 12 行 / deepseek 9 行 / dorothy 10 行 / jill 11 行 / kangel 11 行 / lingling 13 行
对照（老立牌 parunan）13 行
```

(b) 像素级截图（hoveredSlot 实测命中 + `Screenshot.grab`）：
- 悬停命中校验：`{"hover":"idx=42 1 kangel_sign"}` 等，逐张确认光标下的物品就是目标立牌；
- 截图（1920x1080，落在 `fabric-1.20.1/run/client/screenshots/`，副本见 `work/shots/`）：
  `fabric-alana_sign.png`（修女立牌：按下 J 触发 / 主动技能（铁处女）… / 被动技能（鼓起勇气）…）、
  `fabric-kangel_sign.png`（超天酱立牌：主动技能（网络天使）… / 被动技能（小天使请安）… / 一心同体… / 粉丝数: 0 / 10）、
  `fabric-ame_sign.png`、`fabric-lingling_sign.png`、`fabric-dorothy_sign.png`、`fabric-deepseek_sign.png`、`fabric-jill_sign.png`。

## 5. 发布处置
- 提交 `72d2048a`（代码修复）+ `6fe38138`（四份 changelog 记录），两者都带 `[republish-assets]` 标记；
- tag `1.5.0` 移至 `6fe38138`，CI 重建并以同名附件覆盖（Release step 的一次性放行通道）；
- CurseForge：fabric 件需按新产物重传（旧件 9402509 / 9402707 需在 CF 后台归档，见最终汇报）。

## 6. 遗留
CF 上 1.5.0 的 n26 与 fabric 各存在「旧内嵌 StarEngine 构建」与「外置前置构建」两份同名不同字节文件；归档需要 CurseForge API Key，本会话无该凭据 ⇒ 留给用户手工归档。
