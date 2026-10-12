# 筹码商店界面规范（第一版：仅界面，逻辑待定）

> 生成 2026-10-11（Asia/Hong_Kong）｜工作树 F:\MCProject\astral_dice_multiloader-next（分支 multi-dev-next）
> 范围：**P0 两线**（neoforge-1.21.1 + forge-1.20.1 同批）｜状态：**界面骨架 + 素材接线**；购买/刷新的业务逻辑待用户后续下达
> 守门：scripts/verify/verify_chip_shop_ui.ps1（本规范的机器判据；先失败后实现）

## 1. 范围（本批做什么 / 不做什么）

做：
1. 素材入库：images/shop 下 13 张 chipshop_* 贴图 → 两线 assets/astral_dice/textures/gui/shop/（逐字节一致）；
2. 屏幕类 screen/ChipShopScreen.java：布局常量、绘制顺序、交互骨架（单击选中 / 确认按钮购买 / 刷新按钮）、贴图按下态、只读探针；
3. 客户端状态占位 client/ChipShopClientState.java（offers/cost/refresh/selectedIndex）；
4. 菜单骨架 shop/ChipShopMenu.java（**只有玩家物品栏** 9x3 + 快捷栏 9x1，无商店逻辑槽）；
5. 接线：ModMenuTypes 注册 chip_shop、客户端把 ChipShopScreen 绑到该菜单；
6. 三语 UI 文案（zh_cn / en_us / ja_jp，按仓库现有 lang 目录实况）；
7. 守门脚本 + 本规范文档。

不做（逻辑批）：价格与折扣、刷新次数与随机抽签、余额校验与扣款、C2S/S2C 网络包、方块/物品/配方/JEI/手册、开屏入口（命令或方块交互）、实机冒烟。

## 2. 素材清单（源 images/shop；入库后文件名不变）

| 源文件 | 尺寸 | 用途 | 入 jar |
|---|---|---|---|
| chipshop_bg.png | 284x183 | 整屏背景（含外框与顶部装饰） | 是 |
| chipshop_frame.png | 216x78 | 选择位外框（深底 + 顶部装饰） | 是 |
| chipshop_frame_1.png | 216x78 | 内部框架 A：5 格轮廓（透明底） | 是 |
| chipshop_frame_2.png | 216x78 | 内部框架 B：深底外框（与 frame 仅 1260 px 差异，本批取它作外层） | 是 |
| chipshop_inventory.png | 172x86 | 玩家物品栏面板 | 是 |
| chipshop_confirm.png / chipshop_confirm_push.png | 24x24 | 确认购买按钮（常态 / 按下） | 是 |
| chipshop_refresh.png / chipshop_refresh_push.png | 24x24 | 刷新筹码列表按钮（常态 / 按下） | 是 |
| chipshop_quality_1.png | 28x28 | 稀有品质外框（蓝） | 是 |
| chipshop_quality_2.png | 28x28 | 史诗品质外框（紫） | 是 |
| chipshop_quality_3.png | 28x28 | 传奇品质外框（金） | 是 |
| chipshop_select.png | 42x15 | 选中标记（画在所选筹码的标签框内） | 是 |
| chip_shop_GUI.png | 284x183 | **布局参考图（仅设计参考）** | **否（禁止入 jar）** |

## 3. 实测布局（GUI 284x183；以下坐标为 GUI 内绝对像素，条带内坐标另行标注）

- 背景：chipshop_bg.png @ (0, 0, 284, 183)。
- 条带：chipshop_frame_2.png 再 chipshop_frame_1.png 叠加 @ (34, 6, 216, 78)。
- 五格（格距 43、格宽 40，**条带内相对 x**）：CELL_X = {3, 45, 88, 131, 174}；语义 = 0 确认按钮、1/2/3 筹码位、4 刷新按钮。
- 按钮 24x24（条带内相对）：CONFIRM @ (11, 36)、REFRESH @ (181, 36)；按下/hover 用 *_push 贴图。
- 品质外框 28x28（条带内相对）：QUALITY_X = {51, 94, 137}（= 对应筹码格起点 + 6）、QUALITY_Y = 31；筹码物品图标 24x24，内缩 2 px。
- 标签框 42x15 @ 条带内 y = 61：LABEL_X = {1, 44, 87, 130, 172}（= 该格中心 - 21）；cell0 显示星币图标+花费数字（无文字前缀，图标与数字间隔 2px）、cell1/2/3 显示筹码名、cell4 只显示剩余刷新次数；筹码名超宽时左右滚动（停 20t→2t/px→停 20t→反向）、选中格文字改黑色（选中标记画在文字下层）。
- 选中标记 chipshop_select.png（42x15）画在**所选筹码**的标签框内（与标签框同坐标）。
- 物品栏面板 chipshop_inventory.png @ (56, 89, 172, 86)；玩家槽位：主栏 9x3，槽原点 (61, 94)，槽距 18；快捷栏行 y = 152；槽内物品图标内缩 1 px（即 item x = 61 + 18i + 1、y = 94 + 18j + 1）。
- 依据：上述坐标由「素材 alpha 通道与参考图逐像素全等搜索」定位（唯一命中，见 temp/t86/analyze.py、analyze2.py、analyze3.py、analyze5.py）；参考图是手工拼图，反向合成无法 100% 复现（条带区最小失配 1938 px）⇒ **以本表常量为准，不以参考图逐像素比对为判据**。

(2026-10-13 修订：图标 24x24/内缩 2、花费改星币图标+数字、刷新只显剩余)
(2026-10-13 二次修订：筹码名 marquee、图标间距 2px、选中黑字+顺序互换)

## 4. 绘制顺序

1. 背景 → 2. 条带深底（frame_2）→ 3. 格轮廓（frame_1）→ 4. 品质外框（按筹码实际品质选 quality_1/2/3；无筹码则不画）→ 5. 筹码物品图标 → 6. 选中标记（仅所选筹码格；画在筹码名文字下层）→ 7. 三处文本（花费=星币图标+数字 / 筹码名（超宽左右滚动、选中格黑字） / 剩余刷新次数）→ 8. 两个按钮（常态，或 hover/按下 2 tick 内用 *_push）→ 9. 物品栏面板 + 原版槽位物品与悬停高亮 → 10. tooltip（逻辑批再补内容）。

## 5. 交互口径（用户裁决，硬约束）

- **单击筹码格 = 选中**（改 selectedIndex），不产生任何购买请求；再次单击同一格仍只是选中，**不做双击判定**。
- **购买只能点确认按钮**：hover 高亮、按下显示 *_push、点击触发 onConfirmPressed()。
- **刷新只能点刷新按钮**：同上。
- **禁止拖拽购买与双击购买**：不覆写 mouseDragged；不得引入 DOUBLE_CLICK_MS / DRAG_THRESHOLD / dragIndex 之类拖拽/双击状态（守门脚本会拒绝这些标识符）。
- 点击空白区域不清除已选。
- 未选中任何筹码时点确认：不产生请求（逻辑批再决定是否给提示）。

## 6. 数据契约（占位；逻辑批只填这里 + 两个回调）

client/ChipShopClientState.java：
- offers：ItemStack[3]（初始全 EMPTY）；
- cost：int（确认按钮下方框内显示的花费，初始 0）；
- remainingRefreshes / totalRefreshes：int（初始 0/0；界面刷新按钮下方框内只显示剩余次数，总次数不再显示）；
- selectedIndex：int（-1 = 未选中）；
- clear() / tick() / setOffers / setCost / setRefreshes / select。
品质 → 外框映射：原版/库 Rarity：rare → quality_1、epic → quality_2、legendary → quality_3；pinnacle / bizarre 暂用 quality_3（**待用户裁决**）。

## 7. 只读探针（供测试台与后续实机断言；一律不得改状态）

- selectedChipIndex()：当前选中筹码的**筹码位序号**（0..2；-1 = 未选中）。
- chipShopLayoutProbe()：返回实测绘制坐标串，格式 cells=3,45,88,131,174:labelY=61:panel=56,89,172,86:hotbarY=152:selected=<n>:offers=<n>。
- buyRequestCount() / refreshRequestCount()：界面生命周期内发出的购买/刷新请求计数（拖拽或双击若不产生请求，实机断言即可区分）。
- confirmPressTicks()：确认按钮按下态的剩余 tick（0 = 常态）。

## 8. 验收（scripts/verify/verify_chip_shop_ui.ps1 的 G1..G6；G7/G8 另行执行）

- G1 素材：两线 assets/astral_dice/textures/gui/shop/ 各含 13 张 chipshop_*.png 且与 images/shop 源逐字节相同；**不得**出现 chip_shop_GUI.png。
- G2 常量：ChipShopScreen.java 含 §3 全部常量的字面量（GUI_WIDTH=284 / GUI_HEIGHT=183 / STRIP_X=34 / STRIP_Y=6 / STRIP_W=216 / STRIP_H=78 / CELL_X={3,45,88,131,174} / BUTTON_SIZE=24 / CONFIRM_X=11 / CONFIRM_Y=36 / REFRESH_X=181 / REFRESH_Y=36 / QUALITY_SIZE=28 / QUALITY_X={51,94,137} / QUALITY_Y=31 / CHIP_ICON_SIZE=24 / CHIP_ICON_OFF=2 / LABEL_W=42 / LABEL_H=15 / LABEL_Y=61 / LABEL_X={1,44,87,130,172} / PANEL_X=56 / PANEL_Y=89 / PANEL_W=172 / PANEL_H=86 / SLOT_X0=61 / SLOT_Y0=94 / SLOT_PITCH=18 / SLOT_COLS=9 / SLOT_ROWS=3 / HOTBAR_Y=152）。
- G3 引用（**可达性判据**，2026-10-11 复核后加固）：13 张贴图名都要有 TEX_* 常量定义；除白名单 `TEX_STRIP_FRAME`（素材登记、不入绘制路径）外，每个贴图常量必须**能经绘制路径取到** —— 直接出现在 `tex(...)` 实参里（含三元表达式），或出现在「被 `tex(...)` 调用的字符串返回助手」方法体内；源码不含 `chip_shop_GUI`。
- G4 交互：类内不出现 mouseDragged / DOUBLE_CLICK_MS / DRAG_THRESHOLD / dragIndex；`onConfirmPressed()` 恰出现 2 次（声明 + 调用），且**调用点必须落在 mouseClicked 的确认按钮分支内**（位置在 `CONFIRM_X` 命中之后、`chipIndexAt(` 之前）；buyRequestCount 初值 0。
- G5 文案：三语 lang 各含 gui.astral_dice.chip_shop / .cost / .refresh_counter / .empty。
- G6 接线：ModMenuTypes 注册 "chip_shop"；ChipShopMenu 玩家槽恰 36 个（9x3 + 9x1）；客户端绑定要求**同一行**同时出现 `CHIP_SHOP` 与 `ChipShopScreen::new`（剔除 `//` 注释后再判）。
- G7 编译：pwsh -NoProfile -File scripts/test/mt_build.ps1 --version 1.21.1（及 1.20.1）--timeout 300 ⇒ MT_BUILD: OK。
- G8 既有资源守门：pwsh -NoProfile -File scripts/verify/verify_resource_integrity.ps1 两线 PASS。

### 守门脚本自身的反证（变异测试，2026-10-11）

对 `ChipShopScreen` 做四处临时变异并跑守门，四处**全部被判红**，变异还原后两文件 SHA256 与变异前一致、两线守门恢复 OK：

| 变异 | 期望 | 实测 |
|---|---|---|
| M2 把 `onConfirmPressed();` 从确认按钮分支挪到筹码格分支 | G4 红 | FAIL：onConfirmPressed() 调用点不在确认按钮分支内 |
| M3 客户端绑定把 `CHIP_SHOP` 换成 `CARD_SHOP` | G6 红 | FAIL：未把 ChipShopScreen 绑到 chip_shop 菜单 |
| M4 `qualityTex` 不再返回 `TEX_QUALITY_2` | G3 红 | FAIL：TEX_QUALITY_2(chipshop_quality_2.png) 取不到绘制路径 |
| M5 按钮三元改成固定 `TEX_CONFIRM`（不再用 `*_push`） | G3 红 | FAIL：TEX_CONFIRM_PUSH(chipshop_confirm_push.png) 取不到绘制路径 |

## 9. 逻辑批的输入清单（等用户告知）

价格与折扣规则、刷新次数的来源与上限、是否限购、余额不足/未选中等失败提示文案、开屏入口（方块右键 / 命令 / 立牌）、是否需要服务端权威的抽签与同步字段（对照 CardShopMenu 的 60 秒计数器与 DataSlot 做法）。
