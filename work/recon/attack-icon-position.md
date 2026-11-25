# 攻击力图标位置不对 — 根因定位报告（fabric-1.20.1 实机截图）

> 范围：只做 Phase 1–2（根因 + 差异对比 + 候选方案）。**未修改任何代码、未启停任何进程、未改配置。**
> 仓库：`D:/MCProject/astral_dice_multiloader`（四线同树）；git HEAD `be6bc10e`。

---

## 0. 结论速览

1. 画「攻击力图标」的代码是 **`client/CombatPowerHud.java` 的 `ATTACK_OVERLAY`**（fabric 线），贴图 `astral_dice:textures/gui/attack_full|attack_half.png`（9×9 白色小剑）。
2. 它的 y 恒为 **`height - 59`**：表达式是 `int y = height - gui.rightHeight;`（`CombatPowerHud.java:115`），而 `gui.rightHeight` 在本线是**桩件**，每帧被复位成硬编码的 `RESET_RIGHT = 59`（`platform/client/gui/overlay/ForgeGui.java:57` + `platform/client/FabricClientBridges.java:135`）。
3. 原版 1.20.1 右列只吃两行：饱食 `H-39`、氧气 `H-49`（字节码实证）。本线把右列起点设成 **59**，等于把**氧气条那一行也提前预留**了 ⇒ 攻击条比 forge/neo 三线整体**高 10 GUI px（一行）**，而陆地上永远不画气泡，那一行就空着。
4. **任务描述里的"右侧一排约 10 个红色小剑"不是模组的攻击条**，是**原版饥饿条的 10 个 `food_full` 图块**（逐像素命中）；模组的攻击条在截图里只画了 **1 个半剑图标**。
5. 截图实测（guiScale=4）：攻击条在 (738,78) = `H-59`，防御条在 (46,118) = `H-49`，爱心/饥饿条在 y=158 = `H-39` —— **代码与像素完全吻合，不存在运行时算错**。
6. 只有 fabric 一条线这样；forge-1.20.1 / neoforge-1.21.1 / neoforge-26.1.2 都读原版（或 Forge）真实的 `rightHeight` 累加量，攻击条落在 `H-49`（紧贴饥饿条上方）。

---

## 1. 取证环境与方法

| 项 | 内容 |
|---|---|
| 实机 | fabric-1.20.1 dev run；该会话跑的是 `fabric-1.20.1/build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar`（18:32 构建，与 `run/client/logs/latest.log` 18:35 会话打印的 `mod_version=1.5.0-hotfix+fabric_1.20.1` 一致） |
| 排除「构建过期」 | `javap -constants` 该 jar 的 `ForgeGui.class` → `public static final int RESET_LEFT = 49; public static final int RESET_RIGHT = 59;` |
| AutoJS 通道 | **不可用**：`mcp__autojs__status` → `fetch failed`；`netstat` 显示 **25585 无监听**；日志 18:38:05 已 "Saving chunks" 退出 ⇒ 无法实读 guiScale/属性值，改走**截图量化** |
| 截图 | 用户原图 `C:/Users/xmace/.dsh/attachments/v1/objects/95/95c9a42b6c2f97ed0754c1bb0540868eb5e6e261400ce3926fd8b29c59970314`（788×313），副本 `work/recon/user_screenshot.png` |
| 像素判据 | 9×9 图标贴图与**原版** `assets/minecraft/textures/gui/icons.png` 的 9×9 图块，按整数 GUI 缩放 2..6 做逐像素模板匹配（每通道阈值 6，只比对不透明像素），全图滑窗。脚本 `work/recon/_det.py` |
| 原版布局 | `javap -p -c` on `minecraft-merged-1.20.1-loom.mappings.1_20_1...jar` → `net.minecraft.client.gui.Gui#renderPlayerHealth` 与 `renderExperienceBar` |
| 平台对照 | Forge 1.20.1 源：`forge-1.20.1-47.3.0-sources.jar`；Neo 源/字节码：`neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-merged.jar`、`neoforge-26.1.2/build/moddev/artifacts/minecraft-patched-26.1.2.109-merged.jar` |

---

## 2. 「攻击力图标」是哪段代码画的

**类**：`com.merlinkitsune.astral_dice.client.CombatPowerHud`（`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/CombatPowerHud.java`，`public final class`，182 行）

| 环节 | 位置 |
|---|---|
| 攻击条 overlay 定义 | `CombatPowerHud.java:108-118`（`ATTACK_OVERLAY`，`IGuiOverlay` 的 lambda） |
| 贴图 | `CombatPowerHud.java:69-70` `ATTACK_FULL = textures/gui/attack_full.png`、`ATTACK_HALF = textures/gui/attack_half.png`（9×9 白色/银色小剑） |
| 防御条 overlay（同一排盾） | `CombatPowerHud.java:98-106`，贴图 `:65-68` `DEFENSE_FULL/HALF/EMPTY` |
| 注册 | `client/ModClientEvents.java:57-58`：`event.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(), "attack_bar", CombatPowerHud.ATTACK_OVERLAY);`（防御条 `:55-56` registerAbove ARMOR_LEVEL） |
| 实际派发（Fabric） | `platform/client/FabricClientBridges.java:129-140`：一个 `HudRenderCallback.EVENT`，按**注册顺序**逐条 `entry.overlay().render(...)` |
| 数值来源 | `CombatPowerHud.java:137-143` → `DiceCombatModifiers.attackPowerDisplayOf(player)` / `defensePowerOf(player)`（攻击**不含手持武器**显示） |

**grep 原始片段（可直接复现）**

```bash
cd /d/MCProject/astral_dice_multiloader
grep -rn "ATTACK_FULL\|ATTACK_HALF" fabric-1.20.1/src/main/java
grep -rn "attack_bar\|defense_bar"   fabric-1.20.1/src/main/java
grep -rn "attackPowerDisplayOf"      fabric-1.20.1/src
```

本仓**没有** `AttributeHudRenderer` / `ClientHudRenderer` / `HudOverlay` / `AttributeLayer` / `ModGui` 这类命名；带 "attack" 的 HUD 代码只有 `CombatPowerHud` 与 `attack_*.png`。

> 干扰项：`attack_dot.png`（16×16 **红叉**）与 `defense_dot.png`（蓝叉）在代码里**零引用**（`grep -rn "attack_dot\|defense_dot\" ` 全仓 0 命中），是遗留素材；不要把它们误认成截图里饥饿条的红点。

---

## 3. 坐标是怎么算的

### 3.1 本模组（fabric 线）原文

```java
// CombatPowerHud.java:102-105  防御条
int y = height - gui.leftHeight;
drawRow(guiGraphics, DEFENSE_FULL, DEFENSE_HALF, width / 2 - 91, y, defense, false);
gui.leftHeight += ROW_HEIGHT;                 // ROW_HEIGHT = 10 (:78)

// CombatPowerHud.java:108-118  攻击条
Entity vehicle = player.getVehicle();
if (vehicle instanceof LivingEntity mount && mount.showVehicleHealth()) return;
refreshValues(player);
int y = height - gui.rightHeight;                                    // :115
drawRow(guiGraphics, ATTACK_FULL, ATTACK_HALF, width / 2 + 91, y, attack, true);  // :116
gui.rightHeight += ROW_HEIGHT;                                       // :117
```

x 锚点：`width / 2 + 91`（右侧）与 `width / 2 - 91`（左侧）；右对齐图标 `iconX = anchorX - k * ICON_STEP - ICON_SIZE`（`:174-176`，`ICON_STEP=8`、`ICON_SIZE=9`）—— 与原版饱食条公式**逐字同形**。

### 3.2 `gui.rightHeight` 在这条线上不是原版的

fabric 线的 `ForgeGui` 是**桩件**（`platform/client/gui/overlay/ForgeGui.java`，61 行，只有两个 int 字段）：

```java
// ForgeGui.java:42,45
public int leftHeight;
public int rightHeight;
// ForgeGui.java:51,57
public static final int RESET_LEFT  = 49;
public static final int RESET_RIGHT = 59;
```

每帧复位（`platform/client/FabricClientBridges.java:129-140`）：

```java
HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
    ForgeGui.INSTANCE.leftHeight  = ForgeGui.RESET_LEFT;    // :134 → 49
    ForgeGui.INSTANCE.rightHeight = ForgeGui.RESET_RIGHT;   // :135 → 59
    for (RegisterGuiOverlaysEvent.Entry entry : overlays) {
        entry.overlay().render(ForgeGui.INSTANCE, guiGraphics, tickDelta,
                guiGraphics.guiWidth(), guiGraphics.guiHeight());
    }
});
```

⇒ **攻击条恒 `y = height - 59`，防御条恒 `y = height - 49`**（每帧无其它消费者改这两个值：`grep -rn "leftHeight|rightHeight" fabric-1.20.1/src/main/java` 只命中 `CombatPowerHud`、`FabricClientBridges`、`ForgeGui` 三处）。

### 3.3 原版 1.20.1 的递进顺序（字节码实证，非记忆）

`Gui#renderPlayerHealth(GuiGraphics)` 局部变量：

| 字节码 offset | 局部变量 | 语义 |
|---|---|---|
| 207-216 | local10 | `screenWidth/2 - 91`（左锚点） |
| 218-227 | local11 | `screenWidth/2 + 91`（右锚点） |
| 229-236 | local12 | `screenHeight - 39`  ← **基础行 = 血量/饱食** |
| 273-282 | local15 | `healthRows = Mth.ceil((maxHealth + absorption) / 2 / 10)` |
| 284-295 | local16 | `bob = Math.max(10 - (healthRows - 2), 3)` |
| 297-310 | local17 | `local12 - (healthRows-1)*bob - 10`  ← **护甲/吸收行** |
| 312-317 | local18 | `local12 - 10`  ← **氧气行** |
| 787-801 | — | 氧气条条件：`isEyeInFluid(WATER) || air < maxAir` |
| 814-822 | local18 | 有坐骑心行时 `local18 -= (visibleVehicleHeartRows - 1) * 10` |
| 320 / 371 | local19 | `getArmorValue()` → `ifle`：护甲=0 时整行不画 |
| 642-652 | — | 饱食图标 x：`local11 - i*8 - 9`（右对齐），y = local24（来自 local12） |

⇒ **1.20.1 右列只有两行：饱食 `H-39`、氧气 `H-49`**；左列：血 `H-39`、护甲 `H-49`（一排心时）。

### 3.4 判断：不是"漏算"，是"多算一行"

本模组**没有漏算任何已存在的属性条**；相反，它把**右列起点从 39 抬到 59**，即：
`39（基础） + 10（饱食，原版已画） + 10（氧气，原版通常不画） `。
氧气条那一行**恒被预留**，所以攻击条与饥饿条之间永远空一行。这正是 `CombatPowerHud.java:35-36` 与 `ForgeGui.java:28-32` 自述的"与 Forge 有意差一行"。

---

## 4. 截图实测（像素级）

脚本 `work/recon/_det.py`（模板匹配，guiScale 扫描 2..6，每通道阈值 6）：

| 元素 | 图块 | 命中 | 实测左上角(phys) | GUI y | 代码推算 |
|---|---|---|---|---|---|
| 原版爱心 | `icons.png` `heart_full=(52,0)` | **10** | (46,158)…(334,158)，步长 32 | 39.5 | `H-39` ✓ |
| 原版饥饿 | `icons.png` `food_full=(52,27)` | **10** | (450,158)…(738,158)，步长 32 | 39.5 | `H-39` ✓ |
| 模组防御盾 | `defense_full.png` | **1**（66/66 像素） | (46,118) | 29.5 | `H-49` ✓ |
| 模组攻击剑 | `attack_half.png` | **1**（45/45 像素） | (738,78) | 19.5 | **`H-59`** ✓ |
| 原版护甲 | `armor_full=(34,9)` / `armor_empty=(16,9)` | **0** | — | — | 护甲行已取消 ✓（ArmorBarCancelMixin） |
| 原版气泡 | `air_full=(34,18)` | **0** | — | — | 陆地上无氧气行（攻击条下面那行是空的） |
| 模组满剑/半盾/空盾 | `attack_full`/`defense_half`/`defense_empty` | **0** | — | — | 与数值口径一致 |

推导：9×9 图标在 **4 倍缩放**下逐像素全中、行距 40 px = 10 GUI px ⇒ **guiScale = 4**；由 `W/2 - 91 = 46/4` 得 **guiWidth ≈ 205**，由 `H - 39 = 158/4` 得 **guiHeight ≈ 78**（±0.5 舍入）。三者互相自洽。

### 4.1 证伪任务描述里的两个前提

1. **"血条右侧紧跟着一排约 10 个红色小剑"**：那 10 个是**原版饥饿条**的 `food_full` 图块（红肉+棕+白骨头），逐像素匹配 `icons.png (52,27)`，位置 `y=158`（与爱心同排）、右锚 `W/2+91`，**完全正确**。模组的攻击条在截图里**只画了 1 个图标**（`attack_half`）。
2. **"血条正下方还有一条暗色空条"**：那是**原版空经验条**。`Gui#renderExperienceBar` 字节码：`int j = screenHeight - 32 + 3;`（= `H-29`），`182×5` GUI px ⇒ 4×(78-29)=196..216 px，与截图暗条吻合。不是模组画的。

### 4.2 为什么攻击条只有 1 个"半剑"

`CombatPowerHud.java:22` 类头：`两者下限为 2 / 1，恒 > 0 ⇒ 两条 bar 永远显示`；`drawRow`（`:156-171`）在 `points = 1` 时 k=0：`index = 1 == points` ⇒ 画 `attack_half`。
截图只命中 `attack_half`、`attack_full` 零命中 ⇒ **攻击力显示值 = 1**（**数值本身未实读**，见 §6 未核验；AutoJS 通道已死）。

---

## 5. 四线差异表

| 线 | 攻击条 y 表达式（文件:行） | 累加量来源 | 攻击条实际行 | 与原版氧气条 |
|---|---|---|---|---|
| **fabric-1.20.1** | `height - gui.rightHeight`（`client/CombatPowerHud.java:115`） | **桩件** `ForgeGui.rightHeight`，每帧复位 **59**（`platform/client/gui/overlay/ForgeGui.java:57` + `platform/client/FabricClientBridges.java:135`） | **`H-59`（比另三线高一行）** | 避开（本线氧气条固定 `H-49`，不受模组控制） |
| forge-1.20.1 | `height - gui.rightHeight`（`client/CombatPowerHud.java:134`） | **真 Forge** `ForgeGui.rightHeight`（39 起，`renderFood` 已 +10 ⇒ 49） | `H-49` | 把空气条顶到 `H-59` |
| neoforge-1.21.1 | `guiGraphics.guiHeight() - gui.rightHeight`（`client/CombatPowerHud.java:163`） | **原版 `Gui.rightHeight` 真字段** | `H-49` | 同上（图层序列） |
| neoforge-26.1.2 | `guiGraphics.guiHeight() - gui.rightHeight`（`client/CombatPowerHud.java:157`） | **原版 `Gui.rightHeight` 真字段** | `H-49` | 同上 |

支撑证据：

- **1.20.1 原版 `Gui` 没有这两个字段**：`javap -p` 只有 `screenWidth`/`screenHeight`。
- **1.21.1 / 26.1.2 有 public 字段**：`javap -p -cp neoforge-21.1.235-merged.jar net.minecraft.client.gui.Gui` → `public int leftHeight; public int rightHeight;`（26.1.2 merged 同样命中）。⇒ 三线读的是**原版真累加量**，只有 fabric 读**自造桩件**。
- **Forge 1.20.1 的累加语义**（`forge-1.20.1-47.3.0-sources.jar` → `net/minecraftforge/client/gui/overlay/ForgeGui.java`）：
  - `:60-61` `public int leftHeight = 39; public int rightHeight = 39;`
  - `:109-110` 每帧 `rightHeight = 39; leftHeight = 39;`
  - `:328-329` `renderFood: int top = height - rightHeight; rightHeight += 10;`  ⇒ 饱食 `H-39`，随后 49
  - `:246/258` `renderAir: top = height - rightHeight;`，`rightHeight += 10` 在"潜水/缺氧"分支内
  - `:305-307` `renderHealth: leftHeight += (healthRows * rowHeight); if (rowHeight != 10) leftHeight += 10 - rowHeight;`
- **Forge 默认 overlay 顺序**（`VanillaGuiOverlay.java` 枚举声明序）：`PLAYER_HEALTH(:89) → ARMOR_LEVEL(:96) → FOOD_LEVEL(:103) → AIR_LEVEL(:112)`；模组 `registerAbove(FOOD_LEVEL, "attack_bar")` 插在 FOOD 与 AIR 之间 ⇒ 攻击条拿到 `rightHeight = 49`，空气条被顶到 59。forge 线自己 `client/ModClientEvents.java:55-58` 的注释写的就是这个语义。

**⇒ 四线唯一实质差异 = fabric 的攻击条固定高 10 GUI px（一行）。不是四线同错。**

### 5.1 为什么只有 fabric 会这样（排除其它嫌疑）

- 1.20.1 原版布局硬编码且**由原版 `Gui.render` 自己画**；fabric 模组只能在 `HudRenderCallback`（原版之后）叠画，**没有能力**把原版氧气条推走 ⇒ 只能抬高自己（这是设计妥协，不是实现笔误）。
- `mixin/client/GuiMixin.java`：只 `@Redirect` `endsWithin`（低血量抖心/受击闪白），**不碰任何布局常量**。
- `mixin/client/ArmorBarCancelMixin.java:45-50`：`@Mixin(Gui.class) @Redirect(method="renderPlayerHealth", target="...Player;getArmorValue()I")` 返回 0 ⇒ 只取消护甲行（截图里 armor 图块 0 命中，吻合）。
- 实机 87 个模组清单里**没有任何属性条/HUD 类模组**（无 Iron's Spells、无 OverloadedArmorBar 等）⇒ 无第三方 HUD 抢位或改基准。
- `git log --oneline -- <ForgeGui.java> <CombatPowerHud.java>`（fabric 线）只有 **1 个提交** `693850ce feat(fabric): 并入 1.20.1-fabric 第四条线`，源文件 mtime 2026-10-20 ⇒ 无"最近回归"可查。

---

## 6. 判定分级

### 证实（file:line + 实测/字节码）

1. 攻击条由 `client/CombatPowerHud.java:108-118` 绘制，贴图 `:69-70`，注册 `client/ModClientEvents.java:57-58`，派发 `platform/client/FabricClientBridges.java:129-140`。
2. 攻击条 y 恒 `height - 59`（`CombatPowerHud.java:115` + `ForgeGui.java:57` + `FabricClientBridges.java:135`）；防御条恒 `height - 49`（`:102` + `ForgeGui.java:51`）。截图实测 (738,78)/(46,118) @ guiScale 4 与之一致。
3. 原版 1.20.1 右列只有饱食 `H-39` 与氧气 `H-49` 两行（`Gui#renderPlayerHealth` 字节码 offset 229-236 / 312-317 / 787-801 / 814-822）。
4. forge/neo 三线用原版（或 Forge）真累加量 ⇒ 攻击条 `H-49`；fabric 用桩件 ⇒ `H-59`，差一行（10 GUI px）。
5. 截图护甲行为空，与 `ArmorBarCancelMixin.java:45-50` 一致。
6. fabric 的 `HudRenderCallback` 在 `Gui.render` 的 **TAIL** 处触发（`fabric-rendering-v1-3.0.x-sources.jar` → `net/fabricmc/fabric/mixin/client/rendering/InGameHudMixin.java`：`@Inject(method = "render", at = @At(value = "TAIL"), slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/PlayerListHud;render(...)")))`，接口 javadoc「Called after rendering the whole hud」）⇒ 原版 HUD 全部画完才轮到模组，模组元素**叠画在最上层**。

### 修正

- 用户描述"图标贴错锚点 / 与血条同一行"：**x 锚点没错**（`iconX` 与原版食条公式同形），**y 也不是运行时算错**，而是**基准常量比另三线多算一行**。所以"位置不对"成立，"锚点贴错"不成立；且它确实**不在血同一行**（在血上方两行）。

### 证伪

- "血条右侧一排 10 个红色剑图标" ⇒ 原版饥饿条（`food_full (52,27)` 逐像素 10 命中）。
- "血条正下方的暗色空条是模组的空状态条" ⇒ 原版空经验条（`renderExperienceBar`: `screenHeight - 32 + 3`）。
- "四线同错" ⇒ 不成立，另三线为 `H-49`。

### 未核验

- 攻击力/防御力的**实时数值**（AutoJS 端点 25585 无监听、客户端已退出；任务禁止启进程）。仅由 `CombatPowerHud.java:22` 的下限 + 截图只出现 `attack_half` 推断 attack=1。
- forge/neoforge 三线的**实机观感**（本轮未启动任何游戏进程，只读源码/字节码）。

---

## 7. 顺带发现（同源缺陷，未实机）

1. **有吸收心时防御盾会与上排心撞行**：`RESET_LEFT` 固定 49（`ForgeGui.java:51`），而原版护甲行是 `local12 - (healthRows-1)*bob - 10`（字节码 297-310）。一排心 ⇒ `H-49`（对得上）；**两排（有吸收心）⇒ `H-59`**，防御盾却仍在 `H-49`，会压住第二排心。Forge 侧 `renderHealth` 会把 `healthRows*rowHeight` 累加进去（`ForgeGui.java:305-307`），fabric 桩件不累加。截图里无吸收心（`heart_half`/`heart_empty` 0 命中），与本次现象无关。
2. **桩件注释与 Forge 实际语义不符**：`ForgeGui.java:13` 写 `renderHealth: leftHeight += 10`，真 Forge 是 `+= healthRows * rowHeight` 再补差（`ForgeGui.java:305-307`）—— 这是 (1) 的根源。
3. `attack_dot.png` / `defense_dot.png` 零引用（遗留素材，排查时易误导）。
4. **改行位不能只改 anchor**：fabric 的 `registerAbove(...)` 参数"只作记录"（`ModClientEvents.java:52-54`、`CombatPowerHud.java:30-34`），实际顺序 = 注册顺序，真实行位只由两个 `RESET_*` 常量决定。

---

## 8. 候选修复方案（不实施，等裁决）

### 方案 A：`RESET_RIGHT` 59 → 49（1 行）

- 改动：`platform/client/gui/overlay/ForgeGui.java:57`
- 效果：攻击条落到 `H-49`，与另三线观感一致（紧贴饥饿条上方）。
- 代价：极低（1 个常量）。
- 风险：**高**。水下/缺氧时原版气泡也在 `H-49`，而已证实模组在 `Gui.render` 的 TAIL 之后叠画（`InGameHudMixin`，见 §6 第 6 条）⇒ 攻击条会**盖住气泡**（不是被气泡盖住）。坐骑还要再核 `local18 -= (vehicleHeartRows-1)*10`（字节码 814-822）；模组在 `showVehicleHealth()` 时早退（`CombatPowerHud.java:109-111`）能挡一部分，但 boat 这类不挡。

### 方案 B：mixin 推走原版氧气条 + `RESET_RIGHT` 改 49（对齐 Forge 语义，最"正确"）

- 改动：新增 `mixin/client/AirRowShiftMixin.java`（`@Mixin(Gui.class)`，在 `renderPlayerHealth` 的 `local18` 计算处 `@ModifyVariable`/`@Redirect`，在"气泡将要绘制"时再 `-10`），同步 `ForgeGui.RESET_RIGHT = 49`，`*.mixins.json` 加条目。
- 代价：中高（新 mixin + 注入点按字节码取证 + 三态实机验证：陆地/潜水/坐骑）。
- 风险：中高。注入点要锚 offset 312-317 的 `iload 12; bipush 10; isub; istore 18`；映射漂移、与第三方 HUD mixin 抢同一方法、`require` 写大了会在目标类加载时直接 `InjectionError`（本仓有同类教训）。
- 收益：fabric 与另三线语义一致；将来第三方 bar 接 `rightHeight` 时不再需要 59 特例。

### 方案 C：按"原版此刻是否占氧气行"动态选基准（无 mixin，折中，**推荐先做**）

- 改动：只改 `CombatPowerHud.ATTACK_OVERLAY`（约 `:115`）：
```java
boolean airRowShown = player.isEyeInFluid(FluidTags.WATER)
        || player.getAirSupply() < player.getMaxAirSupply();   // 对齐字节码 787-801
int base = airRowShown ? 59 : 49;      // 无气泡时紧贴饥饿条，有气泡时避让
int y = height - base;
```
- 代价：低（10 行内，不动 mixin/注册/桩件）。
- 风险：低-中。入水/出水那一帧 y 会跳一行（可接受，或加插值）；条件必须与 `Gui` 的 `isEyeInFluid(WATER) || air < maxAir` **逐字对齐**，否则边界会错；本质仍是"手工判断原版行占用"，方案 D 才治本。

### 方案 D（治本）：让 fabric 桩件真正复刻 Forge 的累加语义

- 做法：mixin 在 `renderPlayerHealth` 里把 `local15/local16/local18`（血量行数、bob、氧气行）回填给 `ForgeGui.INSTANCE`，使 `leftHeight/rightHeight` 成为"原版已占高度"的真值；`CombatPowerHud` 的 102/115 两行表达式不用改。
- 代价：最高（一个 mixin 覆盖三处局部变量 + 与 `ArmorBarCancelMixin` 协同）。
- 风险：最高（原版方法局部变量多，`@ModifyVariable` 要按 slot/ordinal 精确定位）。
- 收益：顺手修掉 §7 的吸收心撞行 bug，59 特例消失。

**建议**（只给意见，不实施）：先 C（成本/风险最低，立刻消除"悬空一行"的观感），实机看效果后再决定要不要 B/D。若用户要"与另三线完全一致"且能接受 mixin 风险，B 更正确。

---

## 9. 遗留未核验（需开客户端或裁决后补）

- 攻击力/防御力实时值（需 AutoJS 通道恢复或开客户端）。
- 潜水状态下 `H-59` 方案的实际观感（气泡是否被顶开/是否撞）。
- 有吸收心时防御盾的位置（§7 预测撞第二排心）。

---

## 10. 证据文件

- `work/recon/user_screenshot.png`（用户原图副本）
- `work/recon/textures_montage.png`（五个 9×9 图标放大对照）
- `work/recon/vanilla_cmp.png`、`icon_zoom.png`、`lone_sword_vs_tex.png`、`zoom_band.png`、`zoom_top.png`
- 脚本：`work/recon/_tex.py`（图标颜色统计/拼图）、`_det.py`（模板匹配）、`_z*.py`（裁剪放大）；`_an.py` 的直方图法不可靠，已弃用。

---

## 11. 最可能的单一根因（一句话）

**fabric 线的攻击条不是"锚点贴错"，而是它的 y 基准 `ForgeGui.RESET_RIGHT = 59`（`platform/client/gui/overlay/ForgeGui.java:57`，每帧由 `platform/client/FabricClientBridges.java:135` 复位）把原版氧气条那一行也一并预留了，于是攻击条恒定落在 `height - 59`、比饥饿条行高两行、比 forge/neoforge 三线高一行，中间空出来的正是陆地上永远不画气泡的那一行 —— 视觉上就成了"悬空错位的一排攻击力图标"；截图里血条右侧那 10 个"红色小剑"其实是原版饥饿条，另三线没有这个问题。**
