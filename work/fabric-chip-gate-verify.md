# Fabric 1.20.1 实机验证：筹码配方开关（enable_chip_recipes）

- 验证日期：2026-11-02 14:17–14:25（Asia/Hong_Kong）
- 仓库：`F:/MCProject/astral_dice_multiloader`（分支 `multi-main`，HEAD `6fe38138`）
- 只读源码、未改任何默认值；运行时配置 `fabric-1.20.1/run/client/config/astral_dice-common.toml` 在收尾时已改回 `enable_chip_recipes = false`
- 未执行 git add / commit / push

## 0. 结论

| 判据 | 阶段 1（false） | 阶段 2（true） |
|---|---|---|
| `Failed to parse resource conditions`（latest.log） | **0 次** | **0 次** |
| RecipeManager 总配方数 | 1232 | 1299（**+67**） |
| 67 条 chip 配方命中 | **0 / 67** | **67 / 67** |
| id 含 `_chip` 且被加载的配方 | 1（仅 `blank_chip_duplicate` 对照） | 68（67 + 对照） |
| `/recipe give @a astral_dice:abyss_reagent_chip` | `未知的配方"astral_dice:abyss_reagent_chip"` | `已为Player10解锁了1条配方` |
| 真合成台（同 9 格材料）产出槽 | **空** | **`astral_dice:abyss_reagent_chip` ×1** |
| 同 GUI 未门控对照配方 `blank_chip_duplicate` | `astral_dice:blank_chip` ×2（正常产出） | — |
| JEI 该物品的配方分类 | 只有「筹码商店」，无「合成」页签 | 「合成」页签在列，3×3 图案 1/1 |

结论：**该开关在 fabric 线真实生效**——false 时 67 条 chip 配方在数据加载阶段被 fabric 资源条件剔除（RecipeManager 层面不存在、原版合成匹配不到、命令报未知配方、JEI 无合成分类），true 并完整重启后被加载（+67 条、命令解锁成功、同材料可合成、JEI 有合成页签）。

## 1. 环境与入口

- 启动命令（唯一入口，未用 launch.sh）：
  ```
  rm -f fabric-1.20.1/build/loom-cache/argFiles/runClient
  ./gradlew.bat :fabric-1.20.1:runClient --console=plain -PautojsTestMod=1 -PtestSodium=false -Pquickplay=testworld
  ```
- 启动前已确认 25565/25585 无监听（无残留游戏进程，避免串端点假绿）。
- 客户端 run 目录：`fabric-1.20.1/run/client`；世界 `testworld`（显示名「新的世界」，玩家处于 ~100,72,100）。
- AutoJS 端点：`http://127.0.0.1:25585/mcp`（`run/client/config/autojs.json` 已是 25585；unrestricted=true、evalTimeoutMs=5000、logEval=true）。
  - 两次 `status` 均返回：server 侧 "server running with 1 player(s) online, scripts loaded"，client 侧 "client running, world loaded, scripts loaded"。
- 模组本体来自 Loom classpath（dev run，`src/generated/resources` 在 sourceSets 内），不是 `run/fabric-1.20.1/mods` 里那份 jar；产物 `fabric-1.20.1/build/libs/astral_dice-1.5.0+fabric_1.20.1.jar` = 2,944,296 B。
- 目标 mod 的 67 条 chip 配方 id 取自 `fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/*_chip.json` 文件名（abyss_reagent_chip … whetstone_chip，共 67）。所有 chip 配方都是原版 shaped（65 条 `minecraft:crafting_shaped` + 2 条 `astral_dice:nbt_shaped`，均为 3×3 或 shapeless 形态），因此合成台与 JEI 都可作为可视化判据。

## 2. 阶段 1：`enable_chip_recipes = false`

配置实测：`fabric-1.20.1/run/client/config/astral_dice-common.toml:15 → enable_chip_recipes = false`。

### 2.1 启动日志干净

- 本轮 `latest.log`（后被归档为 `logs/2026-11-02-7.log.gz`）：
  - `zcat logs/2026-11-02-7.log.gz | grep -c "Failed to parse resource conditions"` → **0**
  - `[14:18:05] [Render thread/WARN] (ModernFix) Total time to load game and open world was 14.80177 seconds`（第 405 行，说明本轮日志确实是新起的）
  - 模组列表里 `fabric-resource-conditions-api-v1 2.3.9+1802ada577` 在列（条件 API 由模组自带 jar-in-jar 提供）。
- 即：谓词 `astral_dice:chip_recipes_enabled` 注册成功，没有任何 "Failed to parse resource conditions"。

### 2.2 RecipeManager 审计（server 侧 AutoJS eval 原始输出）

eval 代码（server 侧，节选）：

```js
var rm = AutoJS.server().getRecipeManager();
var o = rm.byKey(new ResourceLocation("astral_dice:" + id)); // Optional
// 另外用 rm.getRecipeIds() 遍历统计总数 / 含 _chip 的 id
```

原始返回：

```json
{"totalRecipes":1232,"idsEndingWithChip":1,"expectedChipIds":67,
 "chipPresent":0,"chipAbsent":67,
 "byKeyChipHitSample":[],
 "byKeyChipMissSample":["abyss_reagent_chip=false","adrenaline_high_chip=false",
                        "advanced_peripherals_chip=false","airbag_chip=false"],
 "nonChipControlHit":14,"nonChipControlTotal":14}
```

- 14 条非 chip 对照配方全部命中：`blank_chip_duplicate`、`alana_sign`、`ame_sign`、`blank_sign`、`bonnie_sign`、`conductive_wire`、`dice`、`dorothy_sign`、`fanny_sign`、`fen_sign`、`golden_star_plate_from_nether_star`、`golden_star_plate_from_plates`、`haiqing_sign`、`minecraft:stick`。
- 追加查询确认：当前所有「id 里含 `_chip`」的配方**只有 1 条**，就是设计上不参与门控的对照 `astral_dice:blank_chip_duplicate`：
  ```json
  {"ok":true,"value":"[\"astral_dice:blank_chip_duplicate\"]"}
  ```

### 2.3 反向佐证：`/recipe give` 报未知配方

`AutoJS.command("recipe give @a astral_dice:abyss_reagent_chip")` 执行后，`logs/2026-11-02-7.log.gz` 第 453 行：

```
[14:19:45] [Server thread/INFO] (Minecraft) 未知的配方"astral_dice:abyss_reagent_chip"
```

### 2.4 真合成台手工验证（最强判据）

不是用 JEI 推断，而是用**服务端权威的 `CraftingMenu`**：

1. server 侧 `AutoJS.command("setblock 102 72 102 minecraft:crafting_table")` 放置合成台；
2. `level.getBlockState(pos).getMenuProvider(level, pos)` → `player.openMenu(provider)`，得到真实 `net.minecraft.world.inventory.CraftingMenu`（客户端随之打开 `CraftingScreen`）；
3. 按 `abyss_reagent_chip.json` 的 `pattern: ["XLX","RBR","PPP"]` 填 9 格（slot 1..9）：
   prismarine_shard / heart_of_the_sea / prismarine_shard /
   regeneration_reagent / blank_chip / regeneration_reagent /
   star_plate ×3

原始返回（阶段 1）：

```json
{"menu":"net.minecraft.world.inventory.CraftingMenu",
 "filled":[1,2,3,4,5,6,7,8,9],
 "resultEmpty":true,"resultId":"<empty>"}
```

同一次会话、同一 GUI、同一填法，用**不参与门控**的 `blank_chip_duplicate`（`pattern: ["SBS","SCS","SSS"]`，S=star_coin、B=blank_chip、C=iron_block）复测：

```json
{"menu":"net.minecraft.world.inventory.CraftingMenu",
 "resultEmpty":false,"resultId":"astral_dice:blank_chip","resultCount":2}
```

→ 排除「材料填错 / 菜单没生效 / 读错槽位」这类假阴性：**同一套方法下未门控配方正常产出，只有 chip 配方产出为空**。

### 2.5 截图（阶段 1）

| 文件 | 字节 | sha256 |
|---|---|---|
| `fabric-1.20.1/run/client/screenshots/chip_gate_off.png` | 456,288 | 8a6d18a1…a66e82d |
| 副本 `work/shots/fabric-chip-gate/off.png` | 456,288 | 同上 |
| `.../chip_gate_off_control.png`（未门控对照） | 458,851 | c9ad35aa…a1c656 |
| `.../chip_gate_off_v2.png`（JEI 该物品配方页） | 383,944 | 452f319d…196be5d0 |

- `off.png`：合成台界面，9 格材料与 `abyss_reagent_chip` 的配方图案完全一致，**产出槽为空**；鼠标悬停热键栏里的深渊试剂，tooltip 显示「深渊试剂 / 槽位：筹码」，证明物品存在而配方不存在。
- `off_control_ungated_recipe.png`：同一合成台换成 `blank_chip_duplicate` 材料，产出槽出现 `blank_chip`。
- `off_jei_chip_item_recipes.png`：悬停深渊试剂按 R 打开 JEI，分类只有「筹码商店 **1/1**」，**没有「合成」页签**。

## 3. 阶段 2：`enable_chip_recipes = true`

- 改配置：`astral_dice-common.toml:15 → enable_chip_recipes = true`（**在客户端完全退出后**改，避免被运行中进程的保存覆盖）。
- 完全退出客户端（PID 42160 客户端 + 31152 gradle wrapper 精确 kill，见 §6），确认 25565/25585 无监听后再启动；同一条命令、同一存档。
- 新日志：`logs/latest.log` 第 404 行 `[14:23:55] Total time to load game and open world was 14.14971 seconds`（确认是新进程新日志）。

### 3.1 启动日志干净

`grep -c "Failed to parse resource conditions" logs/latest.log` → **0**。

### 3.2 RecipeManager 审计（同一段 eval）

```json
{"totalRecipes":1299,"idsContainingChip":68,
 "chipPresent":67,"chipAbsent":0,"missSample":[],
 "controls":["astral_dice:blank_chip_duplicate=true","minecraft:stick=true",
             "astral_dice:dice=true","astral_dice:star_plate=false","astral_dice:blank_chip=false"]}
```

- 总数 1299 − 1232 = **67**，与 67 条 chip 配方精确对应。
- `idsContainingChip` 由 1 变 68（67 条新加载 + 原有对照）。
- （`star_plate` / `blank_chip` 是物品不是配方，两阶段都为 false，属正常。）

### 3.3 `/recipe give` 反证

```
[14:24:12] [Server thread/INFO] (Minecraft) 已为Player10解锁了1条配方
```

与阶段 1 的「未知的配方」形成同一命令的正反对照。

### 3.4 真合成台手工验证（同机位、同材料）

同一坐标 `102,72,102` 的合成台菜单，填**完全相同的 9 格材料**：

```json
{"menu":"net.minecraft.world.inventory.CraftingMenu",
 "resultEmpty":false,
 "resultId":"astral_dice:abyss_reagent_chip","resultCount":1}
```

### 3.5 截图（阶段 2）

| 文件 | 字节 | sha256 |
|---|---|---|
| `fabric-1.20.1/run/client/screenshots/chip_gate_on.png` | 501,498 | 77912d32…f38385cc |
| 副本 `work/shots/fabric-chip-gate/on.png` | 501,498 | 同上 |
| `.../chip_gate_on_jei.png`（JEI 同物品） | 459,178 | 1e4b8248…a711464f |

- `on.png`：与 `off.png` **同一 GUI 位置、同一 9 格材料**，产出槽出现 `abyss_reagent_chip`。
- `on_jei_chip_item_recipes.png`：同一物品按 R，分类中出现「**合成 1/1**」页签（3×3 图案完整可见），旁边仍是「筹码商店」页签。

## 4. 取证强度说明（诚实标注）

- **强**：RecipeManager 计数（1232→1299、0/67→67/67）、`/recipe give` 正反对照、服务端权威 `CraftingMenu` 的产出槽读数，全部是服务端主线程的状态读取，不受渲染 / JEI 缓存影响。
- **中**：合成台截图提供可视化对照（同图案、只有产出槽不同），但「这 9 格本应产出筹码」这一因果需要读者对照 `abyss_reagent_chip.json` 阅读；报告已把配方 pattern 与截图材料逐格列出，并附未门控对照截图消解「填法错」的替代解释。
- **弱/间接**：JEI「没有合成页签」只能作为辅助证据（JEI 的分类列表取决于配方注册表，属派生视图），主证不依赖它。

## 5. 未确证项

1. **67 条 advancement 未逐条实证**。本次只验证了配方侧；advancement 用的是同一个 `fabric:load_conditions` 与同一个谓词类，理论上与配方同步剔除，但未通过 registries / datapack 查询确认「阶段 1 少 67 条、阶段 2 多 67 条」。
2. **只覆盖单人开发客户端（integrated server）**。未在专用服务器 + 原版客户端（配方同步包）路径下验证；客户端侧只观察到与服务端一致的合成预览，未单独统计客户端 RecipeManager 条数。
3. **未验证其它三条加载器线**（neo 1.21.1 / forge 1.20.1 / neo 26.1.2），任务范围限定 fabric。
4. **未验证 `/reload` 行为**：按代码侧结论（`setupConfig()` 只在启动时读一次），本次直接用「完全重启」作为切换手段；没有做「运行中改配置 + /reload 是否无效」的对照实验（虽然这也是值得测的一条，但会引入混淆）。
5. 截图里两名玩家名不同（阶段 1 = `Player685`，阶段 2 = `Player10`，dev 环境离线名随机），因此背包内容略有差别；合成台图案与产出槽不受影响。

## 6. 收尾状态

- 配置已改回出厂默认：`astral_dice-common.toml:15 → enable_chip_recipes = false`（源码里默认值未动）。
- 进程：精确 kill 了本次两个客户端（阶段 1：42160 / wrapper 31152；阶段 2：21204 / wrapper 44120）。
  - 收尾后 `netstat` 显示 25565/25585 **无 LISTEN**。
  - 残留 java 仅 `4020`（Zulu 21，12:37 起的 Gradle Daemon）与 `30780`/`32100`（Adoptium 17，13:01/14:11 起，早于本次启动，均非本次客户端）。
  - **未执行** `./gradlew.bat --stop`：该 daemon 早于本次会话启动，可能属于其它正在使用的工作流；按「不要杀别人的 GradleDaemon」纪律改为按 PID 精确清理本次进程。
- 日志留档：阶段 1 = `fabric-1.20.1/run/client/logs/2026-11-02-7.log.gz`（14:17:53–14:23:43）；阶段 2 = `fabric-1.20.1/run/client/logs/latest.log`（14:23:55–14:25）。
- 临时脚本：`work/_tmp_list_java.ps1`、`work/_tmp_kill_client.ps1`、`work/_tmp_kill_client2.ps1`（仅为取证辅助，未改动仓库任何源码）。
- 测试存档副作用：`run/client/saves/testworld` 在 (102,72,102) 多了一个合成台；这是 dev 测试存档，不属源码，未做回滚。

## 7. 失败路径与教训（供下一轮复用）

1. **截图必须异步等待**：`Screenshot.grab(...)` 返回后文件还没落盘，必须 ≥2 s 再读；否则 0 字节。判据必须是「文件存在 + 大小 > 0 + IEND 结尾」，不能只看 eval 返回 ok。
2. **不要试图读 `Screenshot.class`**：Rhino 的 NativeJavaClass 没有 `.class` 成员（报 "has no public instance field or method named class"）；直接按已知重载调用 `Screenshot.grab(File, String, RenderTarget, Consumer)` 即可（4 参重载可用，文件名可控）。
3. **GUI hover 不需要 OS 级 SetCursorPos**：写 `MouseHandler.xpos/ypos`（**窗口像素** = gui × guiScale）+ `screen.mouseMoved(guiX, guiY)` 即可；`hoveredSlot` 在下一帧才更新，eval 里立刻读会是 null，要等 ~1 s 再读。
4. **JEI 的 R 键可以程序化触发**：`mc.keyboardHandler.keyPress(windowHandle, 82, 19, 1, 0)`（再发一次 action=0）足以让 Fabric 的 ScreenKeyboardEvents 派发到 JEI，直接打开 `mezz.jei.gui.recipes.RecipesGui`，比模拟鼠标点 JEI 页签可靠得多。
5. **最硬的合成判据是服务端 CraftingMenu，不是 JEI**：`state.getMenuProvider(level,pos)` + `player.openMenu(...)` 打开真实菜单，`menu.slots.get(1..9).set(stack)` 填料，读 `slots.get(0)`。注意 slot 索引 = 1 + x + 3*y；必须配一条**未门控配方**做同 GUI 对照，否则无法排除「材料/槽位搞错」。
6. **关客户端后配置文件会被重写**：编辑 `run/client/config/astral_dice-common.toml` 会撞上 "file changed since it was read"，必须先重新 read 再 edit（阶段 1→2 与 2→收尾各踩一次）。
7. **1.20.1 + Rhino 的 `p.level` 是字段**（不是方法），`p.level()` 会报 "Cannot call property level ... it is object"；`srv.getRecipeManager().byKey(...)` 返回 `Optional`，用 `isPresent()` 判定，`getRecipe`/`byType` 在此版本不存在。
8. **命令执行结果只看日志**：`AutoJS.command(...)` 的返回值是 undefined，判据一律取 `latest.log` 里的原版反馈行（例如「未知的配方」/「已为…解锁了…条配方」）。
9. **阶段日志会被归档**：第 N 次启动会把上一个 latest.log 压成 `logs/YYYY-MM-DD-N.log.gz`；跨阶段取证要 `zcat | grep -n` 取原行号，不要以为 latest.log 还在。
10. **别用 node 写 `/tmp`**：git-bash 的 `/tmp` 与 node 的 `/tmp` 不是同一个位置；走 `base64` 管道在 bash 里解开再 `curl --data-binary`。
