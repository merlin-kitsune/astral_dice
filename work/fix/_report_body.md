# fabric-1.20.1 攻击力条 y 坐标缺陷修复报告

- 仓库：F:/MCProject/astral_dice_multiloader（分支 multi-main）
- 目标子项目：fabric-1.20.1（**只改本线**）
- 缺陷：攻击力条比另三线（forge-1.20.1 / neoforge-1.21.1 / neoforge-26.1.2）**高出一整行（10 GUI px）**
- 修复方案：**方案 C** —— 攻击条基准按「原版这一帧是否画气泡行」动态取 59 / 49
- 取证手段：Mojang 映射反编译源码 + 本线实际运行的 merged jar 字节码 + 实机 AutoJS 读数 + 截图模板匹配（像素级）

---

## 一、结论摘要

| 状态 | 原版右列占用 | 修复后攻击条基准 B | 攻击条 y | 实测（guiScale=4） |
|---|---|---|---|---|
| 陆地常态（原版不画气泡行） | 只有饱食度行 H-39 | **49**（`RESET_RIGHT_NO_AIR`） | H-49 | gui y=221，饱食度行 gui y=231 ⇒ 差 **-10 gui px = 正好一行** |
| 水下（原版画气泡行） | 饱食度 H-39 + 气泡 H-49 | **59**（`RESET_RIGHT`） | H-59 | gui y=211，气泡行 gui y=221 ⇒ 差 **-10 gui px = 让开气泡行** |

判定条件与 1.20.1 原版 `Gui#renderPlayerHealth` 的「是否画气泡行」条件**逐字等价**（源码取证见第三节，实机判别性证据见 5.2 的水下 v3 读数）。

---

## 二、改动摘要（文件:行号）

### 2.1 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/CombatPowerHud.java`

| 行号 | 改动 |
|---|---|
| `:13` | 新增 `import net.minecraft.tags.FluidTags;`（插在 `net.minecraft.resources.ResourceLocation` 之后） |
| `:115-131` | `ATTACK_OVERLAY` 改为动态基准：`:126` `int base = airRowShown(player) ? ForgeGui.RESET_RIGHT : ForgeGui.RESET_RIGHT_NO_AIR;` → `:127` `int y = height - base;` → `:130` `gui.rightHeight = base + ROW_HEIGHT;`（替换原来的 `int y = height - gui.rightHeight;` + `gui.rightHeight += ROW_HEIGHT;`） |
| `:122-125` | 注释说明「右列只可能占两处：饱食度行恒在 H-39、气泡行（仅当原版决定画时）在 H-49」以及两种基准的取舍 |
| `:158-190` | 新增 `private static boolean airRowShown(LocalPlayer player)`（实现 `:186-190`）：`int maxAir = player.getMaxAirSupply(); int air = Math.min(player.getAirSupply(), maxAir); return player.isEyeInFluid(FluidTags.WATER) || air < maxAir;`；javadoc（`:158-185`）含原版原文、源码行号、字节码 offset、以及气泡行 y 的推导 |
| `:37-41` | 类头第 3 条平台差异：新增「攻击条的基准按原版此刻是否占气泡行动态取」 |
| `:44-52` | 类头「坐标来源」：攻击条 bullet 改写为 `y = height - B`，B 由本帧原版实况决定，画完把 `B + 10` 写回 |
| `:33-36` | 类头图层顺序段：右列复位值改写为「59 这个**上限基准**，本类 overlay 再按本帧原版实况取值并写回」 |

### 2.2 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/client/gui/overlay/ForgeGui.java`

| 行号 | 改动 |
|---|---|
| `:19-38` | 类头「Fabric 侧的差异」整段改写为新实况：`RESET_LEFT=49`（顶替原版护甲行）、`RESET_RIGHT=59`（**原版画气泡行时**）、`RESET_RIGHT_NO_AIR=49`（**原版不画气泡行时**，与 forge/neoforge 一致）；并注明旧实况「恒取 59 ⇒ 陆地上永远空掉一整行」 |
| `:49` | `rightHeight` 字段注释：攻击条每帧把「本帧真实基准 + 10」写回 |
| `:58-62` | `RESET_RIGHT = 59` 注释收窄为「**原版画气泡行时**」 |
| `:64-68` | 新增常量 `public static final int RESET_RIGHT_NO_AIR = 49;`（原版不画气泡行时） |

### 2.3 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/client/FabricClientBridges.java`

| 行号 | 改动 |
|---|---|
| `:130-134` | 仅注释（4 行）：复位值仍是 `RESET_RIGHT=59`（「饱食 + 气泡」的上限），**真实基准由 `CombatPowerHud#airRowShown` 按本帧原版实况再定并写回**，故复位必须发生在 overlay 之前 |

行为改动只有 2.1 的 `ATTACK_OVERLAY` + `airRowShown`；2.2 / 2.3 是注释与常量（2.2 的新常量被 2.1 直接引用）。

差异统计（`git diff --stat`）：3 文件、79 插入 / 20 删除；未 commit / push / tag。

---

## 三、原版条件取证（Mojang 映射，非记忆）

### 3.1 反编译源码（`forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar` → `net/minecraft/client/gui/Gui.java`）

`renderPlayerHealth` 的气泡段 **933-949 行**原文：

```java
933  this.minecraft.getProfiler().popPush("air");
934  int l5 = player.getMaxAirSupply();
935  int i6 = Math.min(player.getAirSupply(), l5);
936  if (player.isEyeInFluid(FluidTags.WATER) || i6 < l5) {
937     int j6 = this.getVisibleVehicleHeartRows(k5) - 1;
938     l2 -= j6 * 10;
939     int k6 = Mth.ceil((double)(i6 - 2) * 10.0D / (double)l5);
940     int l6 = Mth.ceil((double)i6 * 10.0D / (double)l5) - k6;
941
942     for (int j5 = 0; j5 < k6 + l6; ++j5) {
943        if (j5 < k6) {
944           guiGraphics.blit(GUI_ICONS_LOCATION, j1 - j5 * 8 - 9, l2, 16, 18, 9, 9);
945        } else {
946           guiGraphics.blit(GUI_ICONS_LOCATION, j1 - j5 * 8 - 9, l2, 25, 18, 9, 9);
947        }
948     }
949  }
```

**判定「是否画气泡行」的条件就是 936 行**：`player.isEyeInFluid(FluidTags.WATER) || i6 < l5`（`l5 = getMaxAirSupply()`，`i6 = Math.min(getAirSupply(), l5)`）。本修复的 `airRowShown` 逐字复刻该两行（同算法：先取 `maxAir`、再 `Math.min(air, maxAir)`、再 `isEyeInFluid(WATER) || air < maxAir`），**没有**用「有氧气值」之类近似条件。

同一方法的右列 y 计算（用于确认「气泡行占 H-49」）：
- `:867` `int k1 = this.screenHeight - 39;`（血量 / 饱食度那一行）
- `:873` `int l2 = k1 - 10;`（⇒ H-49，气泡行初值）
- `:903-931` 饱食度段：`if (k5 == 0) { ... }` 块尾 `:930 l2 -= 10;`
- `:937-938` `l2 -= (getVisibleVehicleHeartRows(k5) - 1) * 10;`
- `:944` / `:946` 气泡 blit 的 y 就是 `l2`

### 3.2 字节码（本线实际运行的那份映射）

`.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-0b0f9d549f/1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/minecraft-merged-0b0f9d549f-*.jar` → `net.minecraft.client.gui.Gui#renderPlayerHealth`：

| offset | 指令 | 含义 |
|---|---|---|
| 313-317 | `iload 12` / `bipush 10` / `isub` / `istore 18` | `l2 = (screenHeight-39) - 10 = H-49` |
| 752 | `iinc 18, -10` | 饱食度块尾 `l2 -= 10` |
| 770-782 | `getMaxAirSupply` / `getAirSupply` / `Math.min` | `l5` / `i6` |
| 787-801 | `isEyeInFluid(FluidTags.WATER)` + `ifne` / `if_icmpge` | **936 行的短路判据**（首项为真即 `ifne` 进块） |
| 804-822 | `getVisibleVehicleHeartRows` `- 1` `* 10` `isub` `istore 18` | 937-938 行 |

`getVisibleVehicleHeartRows(x)` = `Mth.ceil(x / 10.0)`。非骑乘时 `k5 = 0`（`getVehicleMaxHearts` 在 `vehicle == null || !showVehicleHealth()` 时返回 0）⇒ `rows - 1 = -1` ⇒ 把饱食度段扣掉的 10 **又加回来** ⇒ 气泡行净结果仍是 **H-49**（骑 1 行血载具同样 H-49；骑 2 行血才是 H-59，而本模组在该情形不画攻击条，见第六节）。

---

## 四、构建结果

```
> ./gradlew.bat :fabric-1.20.1:build --console=plain -x pushToGame
BUILD SUCCESSFUL in 5s
8 actionable tasks: 5 executed, 3 up-to-date
（compileJava / jar / remapJar 均执行；pushToRootBuild → build/libs，pushToDevRun → run/fabric-1.20.1/mods）
```

产物：
- `fabric-1.20.1/build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar` = **2,944,566 B（2875.6 KiB）**
- 根 `build/libs/` 同名副本 = 2,944,566 B（pushToRootBuild）
- 旧包 `astral_dice-1.5.0+fabric_1.20.1.jar` = 2,944,296 B（14:14，早于本次，未动）

---

## 五、实机取证

### 5.1 客户端与环境

- 起跑前 25565 / 25585 **均无监听**；已 `rm -f fabric-1.20.1/build/loom-cache/argFiles/runClient`（删除时 27,019 B）。
- 后台启动：`./gradlew.bat :fabric-1.20.1:runClient --console=plain -PautojsTestMod=1 -PtestSodium=false -Pquickplay=testworld`，输出重定向到 `work/fix/runclient.log`。
- 游戏目录 = `fabric-1.20.1/run/client`；入口日志 `MCP endpoint listening on http://127.0.0.1:25585/mcp`（`No token configured`）；`Total time to load game and open world was 16.69 seconds`（ModernFix，已进 testworld）。
- 端点客户端自建：`work/fix/aj.py`（stdlib urllib，POST JSON-RPC `tools/call` → `eval_js`，`side=client|server`）。
- GUI 缩放：`guiScale = 4.0`、`guiScaledWidth = 480`、`guiScaledHeight = 270`，截图 1920×1080。

### 5.2 AutoJS 实况读数

| 时刻 / 状态 | 判定输入 | `rightHeight` | `leftHeight` | attack / defense | 结论 |
|---|---|---|---|---|---|
| 陆地常态 | air 300/300、`eyeInWater=false`、`inWater=false` | **59** | 59 | 1 / 2 | base **49** + 行高 10 ⇒ 攻击条 H-49 ✔ |
| 水下 v1（单次 fill） | air **211/300**、`eyeInWater=true` | **69** | 59 | 1 / 2 | base **59** + 10 ⇒ 攻击条 H-59 ✔ |
| 水下 v3（最终取证） | air **300/300（= max）**、`eyeInWater=true`、`inWater=true` | **69** | 59 | 1 / 2 | base **59**：**air 未低于 max、只因眼睛在水里就占行** |

**v3 是「逐字等价」的判别性证据**：这一帧 `airSupply == maxAirSupply == 300`（玩家带 `water_breathing`），若改用「氧气值未满」这类近似条件会判成「不画气泡行」⇒ 攻击条压到气泡上；而原版条件第一项 `isEyeInFluid(FluidTags.WATER)` 为真 ⇒ 画气泡行 ⇒ 攻击条必须让到 H-59。实测 `rightHeight=69`（= base 59 + 10）证明代码走的就是这个分支。

读数示例（客户端侧）：

```javascript
var FG  = Java.loadClass("com.merlinkitsune.astral_dice.platform.client.gui.overlay.ForgeGui");
var DCM = Java.loadClass("com.merlinkitsune.astral_dice.combat.DiceCombatModifiers");
var FT  = Java.loadClass("net.minecraft.tags.FluidTags");
({ guiH: mc.getWindow().getGuiScaledHeight(), rightHeight: FG.INSTANCE.rightHeight,
   attack: DCM.attackPowerDisplayOf(mc.player), defense: DCM.defensePowerOf(mc.player),
   air: mc.player.getAirSupply(), maxAir: mc.player.getMaxAirSupply(),
   eyeInWater: mc.player.isEyeInFluid(FT.WATER) });
```

### 5.3 截图（`work/shots/fabric-attackbar/`）

| 文件 | 大小 | 状态 | 造状态的方式 |
|---|---|---|---|
| `land_no_air.png` | 923,995 B | 陆地常态，**无气泡行** | 进入 testworld 后直接截图 |
| `underwater_air_row.png` | 759,981 B | 水下，**气泡行存在** | 服务端：`effect give @p minecraft:water_breathing 120 0` + `fill ~-3 ~-1 ~-3 ~3 ~-1 ~3 minecraft:stone`（地板）+ `fill ~-2 ~0 ~-2 ~2 ~4 ~2 minecraft:water`（5×5×5 水体）+ `fill ~-2 ~5 ~-2 ~2 ~5 ~2 minecraft:glass`（玻璃天花板防上浮） |

截图用 `Screenshot.grab(mc.gameDirectory, 名字, mc.getMainRenderTarget(), Consumer)`，**grab 后等 2–3 秒**再复制（写入走 `Util.ioPool` 异步，否则 0 字节）。

两个必须记的坑：
1. 截图前必须 `mc.gui.getChat().clearMessages(true)`，且**要在清空后的下一帧再 grab**（grab 读的是上一帧的渲染目标）。第一版水下截图里 `fill` 的命令回显（聊天条深色背景 + 中文「块」字样）正好盖住左列 H-49 那一行，导致 `defense_full` 整幅扫描 0 命中——这是**假阴性**，不是没画。
2. 窗口失焦会触发 `pauseOnLostFocus` → `PauseScreen` → **单人世界暂停 → HUD 完全不画**（读数表现为 `eyeInWater=false`、`where=paused`）。取证时临时 `mc.options.pauseOnLostFocus=false` + `mc.setScreen(null)`（只改内存；复核 `fabric-1.20.1/run/client/options.txt` 仍为 `pauseOnLostFocus:true`，未落盘）。

### 5.4 像素级核验（模板匹配，不是肉眼）

方法：Pillow / numpy 把截图按 guiScale=4 下采样成 GUI 坐标网格，逐精灵做「不透明贴图素全等」匹配（阈值每通道 10/255）。模组贴图取 `assets/astral_dice/textures/gui/attack_full.png` / `attack_half.png`（9×9）；原版贴图从 `~/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar` 取出 `assets/minecraft/textures/gui/icons.png` 后切 9×9 精灵（`food_full=(52,27)`、`air_full=(16,18)`、`air_half=(25,18)`、`heart_full=(52,0)`）。
脚本：`work/fix/_pix.py`（严格匹配）、`work/fix/_pix2.py`（近失打分）、`work/fix/_crop4.py`（裁剪放大目视复核）。

**陆地 `land_no_air.png`（1920×1080，gui 480×270）**

| 精灵 | 设备坐标 dev | GUI 坐标 | 匹配 |
|---|---|---|---|
| `attack_half` | (1288, 884) | **(322, 221)** | 45/45 texel |
| `defense_full` | (596, 884) | (149, 221) | 66/66 |
| `food_full` ×10 | y=924，x=1000…1288 步距 32 | y=**231**，x=250…322 步距 8 | 各 20/20 |
| `heart_full` ×10 | y=924，x=596…884 | y=**231**，x=149…221 | 各 34/34 |
| `air_full` / `air_half` | — | **NO MATCH**（无气泡行，符合预期） | 0 |

⇒ 攻击条 221 与饱食度行 231 相差 **10 gui px = 40 设备像素 = 正好一行**；H=270 ⇒ 攻击条 y = 270-49 = **221** ✔（与 forge / neoforge 同一条线）

**水下 `underwater_air_row.png`（1920×1080）**

| 精灵 | 设备坐标 dev | GUI 坐标 | 匹配 |
|---|---|---|---|
| `attack_half` | (1288, 844) | **(322, 211)** | 45/45 |
| `air_full` ×10 | y=884，x=1000…1288 步距 32 | y=**221**，x=250…322 步距 8 | 各 39/39 |
| `defense_full` | (596, 884) | (149, **221**) | 66/66 |
| `food_full` ×10 | y=924 | y=**231** | 各 20/20 |

⇒ 攻击条 211 比气泡行 221 **高整整一行（10 gui px = 40 dev px）**，**没有盖住气泡** ✔；防御条仍在左列 221 = H-49 ✔（与陆地态同值，未被本次改动影响）

**防御条对照**：两态 `defensePowerOf = 2 > 0`，`defense_full` 均命中 gui (149, 221)（左列 H-49），陆地 / 水下一致 ⇒ 本次改动只动了右列攻击条基准。

---

## 六、未确证项 / 已知边界 / 环境副作用

1. **air 恰为 0 的瞬态**：原版在 `i6 = 0` 时 `k6 = ceil((0-2)*10/l5) = 0`、`l6 = ceil(0) - k6 = 0`（Gui.java:939-940）⇒ 循环 `k6 + l6 = 0`（`:942`）**一颗气泡都不画**。本修复的判定仍视为「占行」⇒ 该瞬态攻击条会比实际内容高一行（留下一行空隙）。已由源码 + 字节码确认判据，但**未实机复验**（需把 air 精确打到 0 且不死亡，单人环境未做）。
2. **骑乘 2 行血载具**：`ATTACK_OVERLAY` 在 `mount.showVehicleHealth()` 时直接 return（既有逻辑，未改），原版此时气泡行会落到 H-59；本模组该情形不画攻击条，故无重叠。未实机复验。
3. **只核了 guiScale=4**：其余缩放未逐档截图（坐标全部以 GUI 单位换算，理论等比）。未实机复验。
4. **F1（hideGui）/ 旁观 / 死亡态**：未截图；`renderTarget()` 的门控未改。
5. **世界副作用**：testworld 出生点附近（约 -5,63,0）被填入石头地板 + 5×5×5 水体 + 玻璃天花板；玩家在第一次失败尝试中淹死过一次并重生到该出生点。均在 `fabric-1.20.1/run/client` 的 dev 世界里，不影响构建产物。
6. **进程收尾**：本次启动的客户端（PID **27680**，18:56:23）与 gradle wrapper（PID **33100**，18:56:23）已按 PID `Stop-Process -Force` 精确停止；25565 / 25585 复核 **无监听**。事务前就存在的 45608（18:44:16）、19324（18:44:24）未动。本次 runClient 顺带启动的 Gradle daemon **39372（18:55:24）仍在后台空转**（daemon，非客户端/非 wrapper），按「不杀早于本次启动的 daemon、按 PID 精确停客户端与 wrapper」的口径**保留**。
7. **纪律核对**：
   - CRLF：三个改动文件均 `CRLF 正常 / 裸 LF = 0 / 孤立 CR = 0 / 以 CRLF 结尾`（`CombatPowerHud.java` 229 CRLF，`ForgeGui.java` 72 CRLF，`FabricClientBridges.java` 全 CRLF）。
   - 未动 `neoforge-26.1.2` / `neoforge-1.21.1` / `forge-1.20.1`、`docs/agent/*`、`models/*`。
   - 未 commit / push / tag，未发 CurseForge。
   - 期间仓库 HEAD 由他人推进（`be6bc10e` → `2bc97366`；工作树本就含大量他人在改的文件），本任务只碰上述 3 个 fabric 源码文件。
   - `F:/MCProject/.agent-locks/dsh.lock` 为本会话写入，收尾删除。

---

## 七、最终判定

**陆地态攻击条 y = 221（= H-49，紧贴饱食度行上方一整行）与水下态攻击条 y = 211（= H-59，气泡行上方一整行、不遮气泡）两种状态的基准都正确**；防御条在两态均稳定在左列 H-49，未被波及。这与 `ForgeGui` / `CombatPowerHud` 的新实况以及原版 `Gui#renderPlayerHealth` 第 936 行的逐字等价判定一致。
