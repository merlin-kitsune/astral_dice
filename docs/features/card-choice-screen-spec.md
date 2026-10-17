# 选卡界面（单卡 / 三卡）通用组件 — 设计说明

> 2026-10-17 用户裁决。本批范围：**通用组件 + 接入接口**，不接任何具体技能；
> 不移植 `neoforge-26.1.2` / `fabric-1.20.1`；不做超时强制选择；不新绘任何贴图。

## 1. 类清单与职责

| 类 | 线 | 职责 |
|---|---|---|
| `choice/CardChoiceType` | P0 两线 | 三态枚举 `COMBAT / EFFECT / ANY`；按标签判定 `ItemStack`；生成标题 lang 键 |
| `choice/CardChoiceManager` | P0 两线 | 服务端会话（每玩家最多 1 个未结算会话）、打开菜单、确认消费、关闭作废、回调调用者、掉线清理 |
| `screen/CardChoiceMenu` | P0 两线 | `AbstractContainerMenu`：N 个虚拟卡槽（隐藏坐标）+ 36 格玩家物品栏；`quickMoveStack` 双向搬牌；类型过滤；确认消费；关闭退牌 |
| `screen/CardChoiceScreen` | P0 两线 | `AbstractContainerScreen`：无背景图/无标题带、白字标题、卡框 + 卡面 + 卡名 marquee、物品栏底图、放满才出现的确认按钮；单击/双击/拖拽手势 |
| `network/CardChoiceConfirmPayload`（1.21.1） / `network/CardChoiceConfirmMessage`（1.20.1） | 各线 | C2S：只带会话 token |
| `network/CardChoiceClosePayload`（1.21.1） / `network/CardChoiceCloseMessage`（1.20.1） | 各线 | S2C：token 作用域关闭界面 |
| `client/CardChoiceClientHandler` | 各线 | 客户端 dist 隔离入口（关闭逻辑），保证双端 payload 类不出现 `net.minecraft.client.*` direct reference |

## 2. 布局（屏幕层自持 176×200）

坐标为 **GUI 内相对坐标**（`leftPos/topPos` 由原版居中给出）：

| 元素 | 坐标 / 尺寸 | 贴图 |
|---|---|---|
| 标题（白字居中） | 中心 x=88，文本顶边 y=8 | 无（`drawCenteredString`） |
| 卡框（N=3） | x=20 / 66 / 112，y=24，44×61 | `textures/gui/shop/shop_card.png` |
| 卡框（N=1） | x=66，y=24，44×61 | 同上 |
| 卡面 | 框内 (+3,+3)，38×38（64×64 等比缩放） | `textures/gui/shop/card/<物品注册名>.png` |
| 卡名 | 名称框内区 x∈[3,40]（38px）、y∈[43,58]（16px）；≤38px 居中，>38px 左对齐 + 左右滚动 | 无 |
| 确认按钮 | x=62，y=90，51×22；**仅放满后渲染/响应** | `fanny_skill_confirm.png` / hover `fanny_skill_confirm_highlight.png` |
| 物品栏底图 | (5,116)，166×80 | `shop_item_inventory.png` |
| 玩家物品栏槽位 | 主物品栏 (8,119)+(18j,18r)；快捷栏 (8,177)+(18j,0) | 原版物品渲染 |
| 虚拟卡槽（菜单侧） | 隐藏坐标 `(-10000,-10000)`，由屏幕自绘 | — |

菜单/屏幕常量必须同源：`CardChoiceMenu.INVENTORY_X/Y/HOTBAR_Y` = 底图 (5,116) + 内格偏移 (3,3) / 快捷栏 +61。

## 3. lang 键（三语 × 两线各 6 键）

`gui.astral_dice.card_choice.title_<single|triple>_<combat|effect|any>`：

| 键 | zh_cn | en_us | ja_jp |
|---|---|---|---|
| `title_single_combat` | 选择一张战斗牌 | Choose a Combat Card | 戦闘カードを1枚選択 |
| `title_single_effect` | 选择一张效果牌 | Choose an Effect Card | 効果カードを1枚選択 |
| `title_single_any` | 选择一张卡牌 | Choose a Card | カードを1枚選択 |
| `title_triple_combat` | 选择三张战斗牌 | Choose Three Combat Cards | 戦闘カードを3枚選択 |
| `title_triple_effect` | 选择三张效果牌 | Choose Three Effect Cards | 効果カードを3枚選択 |
| `title_triple_any` | 选择三张卡牌 | Choose Three Cards | カードを3枚選択 |

`%` 规则照旧：值内字面百分号必须写 `%%`（本批无百分号）。

## 4. 打开与回传链路（服务端权威）

```
调用者
  └─ CardChoiceManager.start(serverPlayer, slotCount, CardChoiceType, handler)   // 或带 token 的重载
       ├─ 校验：slotCount ∈ {1,3}、无未结算会话、token > 0
       ├─ 建 Session(token, slotCount, type, handler)
       └─ openMenu / NetworkHooks.openScreen
            └─ S2C: 原版 ClientboundOpenScreenPacket + extra data
                    (token:VarInt, slotCount:VarInt, type.ordinal():VarInt)
                    ⇒ 客户端 IContainerFactory 构造同一布局的 CardChoiceMenu
玩家：双击 / 拖拽 / Shift+点击  ←→  服务端 quickMoveStack（卡牌真实移入菜单容器）
玩家点确认（仅放满）
  └─ C2S CardChoiceConfirm(token)
       └─ CardChoiceManager.confirm
            ├─ token 一致 + player.containerMenu 就是本 token 的 CardChoiceMenu
            ├─ consumeSelection：先整体校验（非空 + 类型匹配），再每槽 1 张
            │    1.21.1 = ItemStack#consume(1, player)；1.20.1 = ItemStack#shrink(1)
            ├─ 会话 resolved + 移出 map
            ├─ S2C CardChoiceClose(token)  → 客户端 token 匹配才关屏
            ├─ player.closeContainer()      → removed：只退**未选中的余量**
            └─ handler.accept(List<ItemStack> picked)   // 每张 count=1，服务端 tick 线程内同步执行
Esc / 关闭界面 / 掉线
  └─ CardChoiceMenu.removed：卡槽内全部卡牌退回物品栏（placeItemBackInInventory）
       └─ CardChoiceManager.abandon：会话作废，**零消耗、零回调**
```

### 4.1 回调接口

```java
// 一步式（自动分配 token）
int token = CardChoiceManager.start(player, 3, CardChoiceType.COMBAT, picked -> { ... });

// 自带 token（便于与调用者自己的状态机对齐）
boolean ok = CardChoiceManager.start(player, 1, CardChoiceType.ANY, myToken, picked -> { ... });

// 两步式（先开会话，稍后补回调）
CardChoiceManager.start(player, 3, CardChoiceType.EFFECT, myToken, NOOP);
CardChoiceManager.registerHandler(player, myToken, picked -> { ... });
```

- `handler` 只在**确认成功**时调用一次；参数是服务端实际消耗掉的卡牌（每张 `count=1`，已脱离玩家物品栏）。
- `handler` 在服务端 tick 线程执行，可安全地继续开会话/发事件/改玩家状态。
- 关闭/取消**不会**调用 `handler`；调用者若需要「取消」通知，可在自己的状态机里用 `hasOpenSession` 轮询或扩展接口（本批未做）。

## 5. 与冻结计划的 4 处技术性偏差（均为平台约束，非口径变更）

1. **没有额外的自定义「打开」payload**。菜单路线下客户端无法凭 payload 创建容器（容器必须由服务端
   `openMenu`/`NetworkHooks.openScreen` 创建并同步 containerId），真正的 S2C 打开就是**原版
   `ClientboundOpenScreenPacket`**，本批把 token/slotCount/type 放进它的 **extra data**。再发一个自定义
   open payload 只会与原生打开包竞争或重复。`EventChoice` 之所以有 start payload，是因为那条线是**纯 Screen**
   （无容器），不适用于本组件。Dist 防护先例仍被 `CardChoiceClosePayload/Message` 沿用。
2. **虚拟卡槽 `mayPickup = true`（计划写的是 false）**。原版在 `ClickType.QUICK_MOVE`（双击 / Shift+点击）
   之前会检查 `slot.mayPickup(player)`；恒 false 会让「双击取回」**静默失效**。本槽没有商店货架那种
   「白拿货」风险（槽里就是玩家自己的卡），允许光标直接取回同样合理。
3. **拖拽的「跟手幽灵」= 原版光标携带物**。升格（位移 ≥ 4px）时才向服务端发 `PICKUP`，把卡真正拿到光标上；
   松手落空/类型不符/槽已占用 ⇒ 再发一次 `PICKUP` 放回原槽。这样不需要自绘幽灵，也不会出现
   「屏幕显示有卡、服务端其实没搬」的状态。落空还原、失焦/左键未抬起清理（`containerTick`）均已实现。
4. **双击窗口 280ms 由屏幕层翻译成原版 `ClickType.QUICK_MOVE`**。1.21.1 的原生双击语义是
   `PICKUP_ALL`（把同类物品收进光标），不能直接复用；因此第一次按下只记录相位、不交给原版，
   280ms 内同一槽再次按下才发 `QUICK_MOVE`。单击因此**只选中不拿起**（与商店手势一致），
   需要拿起时拖动超过阈值即可。

## 6. 端口差异（neoforge-1.21.1 ↔ forge-1.20.1）

| 项 | 1.21.1 | 1.20.1 |
|---|---|---|
| MenuType 注册 | `IMenuTypeExtension.create(CardChoiceMenu::new)` | `IForgeMenuType.create(CardChoiceMenu::new)` |
| 打开菜单（带 extra data） | `ServerPlayer#openMenu(provider, buf -> …)` → `OptionalInt` | `NetworkHooks#openScreen(player, provider, buf -> …)` |
| 网络载体 | `CustomPacketPayload` + `StreamCodec`，`ModPayloads` 注册 | `SimpleChannel` 消息类，`ModNetwork#register()` **末尾追加 id 24/25** |
| 发 C2S | `PacketDistributor.sendToServer` | `ModNetwork.sendToServer` |
| 发 S2C | `PacketDistributor.sendToPlayer` | `ModNetwork.sendToPlayer` |
| 消耗 | `ItemStack#consume(1, player)` | `ItemStack#shrink(1)` |
| 屏幕注册 | `RegisterMenuScreensEvent` | `FMLClientSetupEvent` + `MenuScreens.register` |
| `renderSlot` 覆写 | `protected`，可跳过隐藏槽 | **`private`**，不可覆写 ⇒ 隐藏槽物品由原版画在 -10000（与商店同口径，无副作用） |
| `ResourceLocation` 构造 | `fromNamespaceAndPath` | `new ResourceLocation` |

## 7. 服务端权威与安全

- 所选卡牌**不由客户端上报**：卡牌在打开期间已被原版点击/`quickMoveStack` 真实移入菜单的后备容器，
  确认时服务端只读该容器 ⇒ 客户端无法伪造「我选了哪张牌」。
- `mayPlace` 按 `CardChoiceType` 过滤（标签口径 `combat_cards` / `effect_cards`），错误类型无法进入卡槽。
- 确认三重校验：会话 token、`player.containerMenu` 是否为本 token 的菜单、每槽非空且类型匹配。
- 未放满 ⇒ 服务端静默拒绝，界面与卡牌保持原样（客户端按钮此时也不存在）。
- 关闭菜单时服务端把卡槽余量退回物品栏；确认后只退**未选中的余量**，已选中的每槽 1 张不再回来。

## 8. 本批未做

- 不接具体技能（调用者接入方式见 §4.1）。
- 不移植 `neoforge-26.1.2` / `fabric-1.20.1`。
- 不加超时强制选择。
- 不新绘任何贴图（全部复用 `textures/gui/shop/` 既有素材）。

## 9. 实机验证建议（需用户明确下达）

建议用 KubeJS/命令先 `give` 若干战斗牌 + 效果牌，再由临时测试钩子调用
`CardChoiceManager.start(player, 3, CardChoiceType.COMBAT, handler)` 打开界面。断言项：

1. **双击放入**：物品栏战斗牌双击一次 → 卡槽出现 1 张；再次双击卡槽 → 回到物品栏。
2. **拖回**：按住物品栏卡牌移动 ≥4px → 卡牌跟手；松手在空卡槽 → 放入；松手在空白处 → 回到原槽（不丢失）。
3. **类型过滤**：用效果牌拖到「战斗牌」会话的卡槽 → 拒绝（原地还原）；双击效果牌 → 不进入卡槽。
4. **未满无按钮**：只放 1/3 时确认按钮**不渲染**，点击该区域无任何反应。
5. **放满确认**：放满 3 张 → 按钮出现；点击 → 界面关闭、卡牌各消耗 1 张、回调拿到的 3 个 `ItemStack`
   与所选一致（服务端日志 `AP_CARD_CHOICE: confirm:...:picked=3`）。
6. **Esc 零消耗**：放 2 张后按 Esc → 界面关闭、2 张全部回到物品栏、无回调
   （服务端日志 `AP_CARD_CHOICE: abandon:...:consumed=0`）。

> 收尾按 AGENTS：`pwsh -File scripts/test/mt.ps1 --version <线> --phase stop --purge-saves`。
