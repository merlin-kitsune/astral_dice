# neoforge-1.21.1 发布验收 · production 形态实机报告（绕开 YSM err54）

- 日期：2026-11-02 02:52–03:32（Asia/Hong_Kong）
- 工作树：`F:\MCProject\astral_dice_multiloader`
- 被测产物：`build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar`
- 生产实例（未改动其存档）：`D:\.minecraft\versions\狐の航空学 Voxy Edition`（1.21.1 / NeoForge 21.1.264，512 个模组 jar）
- 结论：**production 形态跑通**。RELEASE150-1.21.1 全 31 steps 执行完毕，26 条可自动化判定中 **25 PASS / 0 FAIL**，2 条 vision 人工读图判定 PASS（其中 1 条以补充截图为准）。YSM 侧 **无 `err: 54`、无 `Failed to load native lib`、无 attachment 崩溃**，并以像素证据证明其在 production 下接管玩家渲染。

---

## 0. 结论速览

| 项 | 结果 | 依据 |
|---|---|---|
| production 启动（无 userdev 属性） | PASS | argv 内无任何 userdev 标记；`classpath entries: 107 missing: 0` |
| YSM 不再 err54 | PASS | 全会话 `err: 54` 计数 0、`Unsatisfied runtime environment` 计数 0、`Failed to load native lib` 计数 0 |
| attachment 崩溃消失 | PASS | `Data attachment type with default value` 计数 0；`crash-reports` 目录不存在 |
| YSM 真实生效 | PASS（文件+像素+排除法） | `prodgame/config/yes_steve_model/builtin` 本次启动解出 63 MB / 676 文件；第三人称截图里玩家是 YSM 动漫模型 |
| 本模组加载 | PASS | `Astral Dice 1.5.0+neoforge_1.21.1 (astral_dice)` |
| RELEASE150 断言 | 25 PASS / 0 FAIL / 2 MANUAL(PASS) | 见 \5 |
| 收尾（进程/端口/实例 mods） | 已完成 | 见 \7 |

---

## 1. 现场与前置（一次性，不触碰玩家存档）

1. 世界来源：`run/1.21.1/saves/testworld` 的 `session.lock` 被别人在跑的 dev 客户端（PID 34596）持有 ⇒ **不复制它**。改用静止世界 `run/1.21.1/saves/verify`，装成 `run/1.21.1/prodgame/saves/testworld`（114 MB，quickplay 按目录名生效）。
2. 一次性 gameDir：`run/1.21.1/prodgame`（不写玩家实例的 `saves/`）。
3. 模组：实例 mods 的 511 个 jar 真拷贝进 `prodgame/mods`，再放入 `AutoJS-neoforge-1.21.1-1.0.0.jar`（kubejs 本来就在包里，**没有再放第二份**——重复 mod 会拒启动）。当前 `prodgame/mods` = 512 项。
4. options：从实例 options.txt 派生，强制 `fullscreen:false` / `lang:zh_cn` / `guiScale:2`（截图 854×480）。
5. 用户实例 mods 的**临时注入**（仅第一次启动取证据用，任务书要求）：
   - 备份清单：`run/1.21.1/.tmp/instance-mods-before.txt`（512 条，name/size/mtime）
   - 注入：`python run/1.21.1/.tmp/prod_scene.py inject` → 复制 `AutoJS-neoforge-1.21.1-1.0.0.jar`（163541 B）进实例 mods
   - 撤回：`python run/1.21.1/.tmp/prod_scene.py revert` → `removed ...\mods\AutoJS-neoforge-1.21.1-1.0.0.jar`
   - 之后所有启动只用 `prodgame/mods` 里的副本，**不再触碰玩家实例**。

## 2. 命令全文

### 2.1 启动器（新写）

```
# 文件：run/1.21.1/.tmp/prodlaunch.py（照 run/26.1.2/.tmp/prodlaunch.py 改写）
# 关键常量：MC=D:\.minecraft   INST=versions\狐の航空学 Voxy Edition
#           JAVA=C:\Program Files\Zulu\zulu-21\bin\java.exe
#           GAMEDIR=F:\MCProject\astral_dice_multiloader\run\1.21.1\prodgame
#           ASSETS=D:\.minecraft\assets   LIBS=D:\.minecraft\libraries
# 与 26.1.2 模板的差异：classpath 去重；补 classpath_separator=";" 与 user_type="legacy"；
#   --autojs-port N → 插 -Dautojs.port=N；--xmx（默认 8G）；--username；可重复 --game-arg；
#   quickplay 合并为 --quickPlaySingleplayer=<world>（空格分隔会静默停在主菜单）；
#   启动时写 .tmp/prod-run.cmdline.txt 与 .tmp/prod-run.pid。

# dry-run（确认真实 classpath，不启动游戏）
python run/1.21.1/.tmp/prodlaunch.py --dry-run --quickplay testworld --autojs-port 25587

# 实际启动（权威会话，pid 35612）
python run/1.21.1/.tmp/prodlaunch.py --quickplay testworld --autojs-port 25586 --log run/1.21.1/.tmp/prod-run3.log
```

dry-run 原文（关键行）：

```
java      : C:\Program Files\Zulu\zulu-21\bin\java.exe
gamedir   : F:\MCProject\astral_dice_multiloader\run\1.21.1\prodgame
classpath entries: 107 missing: 0
unresolved placeholders: none
argv entries: 62
ARG| -Dautojs.port=25587
```

缺件说明：`missing: 0`，无逐条解释项。
过程记录：首版启动器未去重，实例 json 的 144 条 libraries 里 11 条指向同一 jar，BootstrapLauncher 建 union FS 时抛
`java.lang.IllegalStateException: Duplicate key D:\.minecraft\libraries\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar ...`
← `cpw.mods.securejarhandler/cpw.mods.niofs.union.UnionFileSystem.<init>(UnionFileSystem.java:134)` ⇒ 已加 classpath 去重（118 → 107 条）。

完整命令行（11251 字符）存于 `run/1.21.1/.tmp/prod-run.cmdline.txt`（sha256 `648d6b8c70f5f829be03f3f150d450d2cd82a8e109e3ab7d166d3c8df3c8f2cc`）。头部与游戏侧参数原文：

```
"C:\Program Files\Zulu\zulu-21\bin\java.exe" -Xms2G -Xmx8G
 -XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump
 -Djava.library.path=D:\.minecraft\versions\狐の航空学 Voxy Edition\狐の航空学 Voxy Edition-natives
 -Djna.tmpdir=<同上>  -Dorg.lwjgl.system.SharedLibraryExtractPath=<同上>  -Dio.netty.native.workdir=<同上>
 -Dminecraft.launcher.brand=prodlaunch-1.21.1 -Dminecraft.launcher.version=1.0
 -cp <107 项，分号分隔>
 cpw.mods.bootstraplauncher.BootstrapLauncher
 --username DevTester --version 狐の航空学 Voxy Edition
 --gameDir F:\MCProject\astral_dice_multiloader\run\1.21.1\prodgame
 --assetsDir D:\.minecraft\assets --assetIndex 17
 --uuid 00000000000000000000000000000001 --accessToken 0 --clientId 0 --xuid 0
 --userType legacy --versionType release
 --fml.neoForgeVersion 21.1.264 --fml.fmlVersion 4.0.45 --fml.mcVersion 1.21.1
 --fml.neoFormVersion 20240808.144430 --launchTarget forgeclient
```

注：该 cmdline 文件被最后一次（误触发、随即停止的）启动覆写，因此不含 `-Dautojs.port=25586`；该参数注入由上面 dry-run 的 `ARG| -Dautojs.port=25587` 证明，权威会话确实由 25586 端点响应（见 \4）。

### 2.2 用例合法性校验

```
pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case scripts/test/cases/RELEASE150-1.21.1.json
→ MT_VALIDATE: OK — RELEASE150-1.21.1
```

### 2.3 现场整备（不改用例）

```
python run/1.21.1/.tmp/pre_setup3.py
```

pre_setup3.py 通过 AutoJS server 侧注入：`/effect give @a minecraft:instant_health 1 10 true`、`/effect give @a minecraft:saturation 1 10 true`、`/fill 96 119 96 104 119 104 minecraft:stone`、`/setblock 100 121 100 minecraft:air`、`/gamerule doImmediateRespawn true`，并把 hotbar 选择位设为 0（`inv.selected = 0`）。读回原文：`{"hp":"20","food":"20","pos":"BlockPos{x=100, y=120, z=100}"}`。

### 2.4 跑用例（自写最小执行器）

```
python run/1.21.1/.tmp/rel150_prod.py --case scripts/test/cases/RELEASE150-1.21.1.json --extra-log run/1.21.1/.tmp/prod-run3.log --out run/1.21.1/.tmp/rel150-results-final.json --run-label final3
```

### 2.5 补充取证（tooltip 可读截图）

```
python run/1.21.1/.tmp/tip_prod.py   # 自绘 Screen + Screen.getTooltipFromItem + renderComponentTooltip 后截图
```

### 2.6 收尾

```
pwsh -NoProfile -File run/1.21.1/.tmp/stop_mine.ps1      # 只停我自己的 pid 35612
python run/1.21.1/.tmp/prod_scene.py revert              # 撤回实例 mods 里的 AutoJS
python run/1.21.1/.tmp/prod_scene.py manifest-after      # 重新生成清单
diff -u run/1.21.1/.tmp/instance-mods-before.txt run/1.21.1/.tmp/instance-mods-after.txt
```

## 3. 为什么自写最小执行器，而不是复用 mt.ps1 / mt_case.ps1

实读结论（三条都不可绕过）：

1. **端点不可参数化**：`scripts/test/mt_case.ps1` 的 `Get-MtAutoJsEndpointUrl`（约 line 686）只读仓库级 `mt.conf` 的 `MT_AUTOJS_ENDPOINT`，无 CLI/env 覆盖；改 `mt.conf` 会波及正在跑的并发工作流。而本任务必须用 25586（25585 已被并发 dev 客户端占用）。
2. **日志根写死**：其 log 断言根固定在 `run/1.21.1/logs/latest.log`（dev 客户端日志），production 日志在 `run/1.21.1/prodgame/logs/latest.log` ⇒ 直接复用会读到别人进程的日志，等于假证据。
3. **python 侧工具链已死**：`scripts/test/lib/mt_case.py` 里 `from mt_autojs import AutoJs`，而 `scripts/test/lib/mt_autojs.py` 已不存在；其 PRIMITIVES 也把 `inject_command` / `screenshot` 标为 inactive。

因此写了 `run/1.21.1/.tmp/rel150_prod.py`（仅标准库），实现**同一套 op/断言语义**：`note / inject_command / eval_js / wait / screenshot / jar_stats` 与 `log / eval / absent / crash / jar / vision`；命令注入三级链 `autojs_command → perform_prefixed → send_command`；截图 JS 逐字复用 `scripts/test/lib/Mt.Channel.psm1` 的 `Invoke-MtChannelShot`（`Screenshot.grab` 三档签名 + `f.exists()` 判真落盘）。MCP 传输自实现（initialize → notifications/initialized → tools/call，兼容 `application/json` 与 `text/event-stream`，回带 `Mcp-Session-Id`）。

### 3.1 该执行器抓出并修掉的 7 个判据缺陷（先修再用，避免假 PASS）

| # | 缺陷 | 表现 | 修法 |
|---|---|---|---|
| 1 | log/absent 用 `re.search(pat, whole_text, re.M|re.S)` | 点星可跨行，把 JS 源码行与后一行的真值拼成命中（曾把 `[REL150] ver=1.21.1 gamemode=' + st.gameMode` 判成 PASS） | 逐行匹配并统计 hits |
| 2 | eval 断言只读 `pattern`，而用例写 `expect:{regex}` / `expect:{name:{regex}}` / `expect:{screen:{regex}}` | 落进「value 非空即 PASS」分支 | 优先 `expect.regex`，其次 `expect.<field>.regex` 并对该字段取值匹配；都缺则 UNKNOWN-EXPECT |
| 3 | Rhino 返回对象被字符串化成 `[object Object]` | 字段全丢，字段级断言无从判定 | `op_eval` 把脚本包成 `JSON.stringify(( CODE ))` 再 `json.loads` 回结构化 |
| 4 | 截图同名覆盖时被 `grab4` 异步写入的空文件骗过（exists 但 size 0） | 交出 0 B 假截图 | 抓图前先删同名旧文件 |
| 5 | AutoJS `logEval=true` 把每次 eval 的**源码**也回显进日志 | 源码行可满足断言 | `--run-label` 打 `[REL150-RUN] phase=case run=<label>` banner，断言只扫 banner 之后、且只认形如 `[<时间戳>] [<logger>/<thread>]` 的真实记录 |
| 6 | 日志字符集 | 该实例 `latest.log` 为 **cp936**，按 UTF-8 带 replace 解码会把中文全毁，含中文的断言（如 `name=深渊试剂`）永远 FAIL | 先严格 UTF-8 解码，失败回落 `gbk` |
| 7 | banner 剪切位置 | banner 同时进 `latest.log` 与启动器 stdout 日志，按拼接文本 `rfind` 会切到 stdout 那份，导致本轮机器行被切掉 | 每个文件各自在自己最后一次 banner 处切 |

## 4. 启动取证

### 4.1 端口前置

启动前 `25565 / 25575 / 25585` 均无 LISTENING；但 25585 **被并发 dev 客户端占用**（PID 34596，由 `gradlew :neoforge-1.21.1:runClient` 起），故改用 `-Dautojs.port=25586`（AutoJS 优先级 `-Dautojs.port` > `AUTOJS_PORT` > `config/autojs.json`），实测 25586 空闲。并发工作流（fabric 线 `KnotServer/KnotClient` PID 37252、gradlew PID 29924、gradle daemon 5088/37020）全程未触碰。

### 4.2 会话时间线（权威会话 pid 35612）

```
2388  | [0211月2026 03:23:14.878] [modloading-worker-0/INFO] [AutoJS/Config/]: MCP port overridden to 25586 by autojs.port
6200  | [0211月2026 03:24:07.477] [Render thread/INFO] [net.minecraft.client.sounds.SoundEngine/SOUNDS]: Sound engine started
10566 | [0211月2026 03:25:00.880] [Render thread/WARN] [ModernFix/]: Total time to load game and open world was 161.34793 seconds
11952 | [0211月2026 03:27:22.668] [Server thread/INFO] [KubeJS Server/]: autojs-eval#1: [REL150-RUN] phase=case run=final3
```

### 4.3 YSM：无错、真的在工作

全会话计数（证据日志 = `run/1.21.1/prodgame/logs/2026-11-02-4.log.gz`，解压 2170646 B / 12412 行，sha256 `3bb742cf59542c1513d0033ebf407cd8d931fad66aa57b41d8da35ef6359acec`）：

```
count(err: 54                                ) = 0
count(Failed to load native lib              ) = 0
count(Unsatisfied runtime environment         ) = 0
count(Data attachment type with default value ) = 0
count(Minecraft has crashed                   ) = 0
count(must be registered                      ) = 1   <- 唯一一条：A recipe category must be registered in order to use this recipe type.
crash-reports dir exists = False
```

**一条实测事实**：YSM 健康时**不打任何 `[yes_steve_model/]` 日志行**（logger-prefixed 计数 0），故「正证据行」不能指望 YSM 自己的日志。可用正证据三层：

1. 模组在列：
```
1360 | 		Yes Steve Model 2.6.5-neoforge+mc1.21.1 (yes_steve_model)
1362 | 		YSM 体验优化（YSM Enhancements） 1.1.0 (ysm_enhancements)
```
2. **本次启动解出**原生模型资源（目录自带「每次启动清空」声明）：
   - `prodgame/config/yes_steve_model/builtin` = 63 MB / 676 文件（含 `builtin/default/{animations,avatar,lang,models,textures,ysm.json}` 与 `builtin/wine_fox/01_taisho_maid` 等 10+ 套）
   - `cache/client` 19 MB、`cache/server` 19 MB
   - `builtin/notice.txt` 原文：`This directory is cleared every time the game starts!` / `该目录会在每次游戏启动时清空！`
3. 像素级 + 排除法：
   - 截图 `run/1.21.1/prodgame/screenshots/ysm_thirdperson_prod.png`（112728 B，sha256 `d44f25a7028df1cc7006d81ee432dbfea50f2dd2f73eebd0d5ab32f6fe58ea9b`）：第三人称后背视角（`Client.options.setCameraType(CameraType.THIRD_PERSON_BACK)`）下玩家是**粉白长发 + 红色裙装动漫少女**（非 Steve/Alex），左上还有同一模型的 YSM HUD 画框与时钟。
   - `prodgame/mods` 内无 Figura / playeranimator / emotecraft / modelengine（grep 仅命中 `appleskin`、`configurable-difficulty`）⇒ 玩家模型只能来自 YSM。
   - 补充截图中亦可见同一少女模型：`abyss_reagent_tooltip_prod.png`、`abyss_reagent_tooltip.png` 左侧。
4. 与 dev 形态那条死亡链对照：崩溃文件 `run/1.21.1/crash-reports/crash-2026-11-02_02.27.10-client.txt` 的 `:124` 行 `[yes_steve_model/]: Failed to load native lib` 及随后的 `Data attachment type with default value ... must be registered!` 在 production 会话里**均 0 次出现**，且 `crash-reports` 目录都没有生成。

### 4.4 本模组加载

```
822 | 		Astral Dice 1.5.0+neoforge_1.21.1 (astral_dice)
```

### 4.5 AutoJS 在 production 下的可用性（实测）

- 端点：`GET http://127.0.0.1:25586/health` = 200；就绪约 77 s，世界载入再约 40 s。
- **server 侧与 client 侧都可 eval**：本次机器行分别来自 `[Server thread/INFO] [KubeJS Server/]` 与 `[Render thread/INFO] [KubeJS Client/]`，两侧都真实产出。
- 与 dev 形态不同的上下文事实（脚本必须按此写，否则 ReferenceError）：
  - `java` / `net` / `mc` 均 undefined；必须 `Java.loadClass("java.io.File")` 这种形式。
  - **`Client` 就是 Minecraft 实例本身**（`Client.player`、`Client.font`、`Client.setScreen`、`Client.getWindow()`）。
  - 返回对象会字符串化成 `[object Object]` ⇒ 执行器已统一包 `JSON.stringify`。
  - Java 对象没有 `getClass()` / `getUUID()` 可调（Rhino 侧 TypeError）。
  - `Screen.getTooltipFromItem(Client, stack)` 可用；但 `GuiGraphics.renderTooltip(Font, ItemStack, int, int)` 在 Rhino 下**重载歧义**（InternalError: "The choice of Java method net.minecraft.client.gui.GuiGraphics.renderTooltip matching JavaScript argument types ... is ambiguous"），要改用 `renderComponentTooltip(Font, List, int, int)`。
  - 窗口无法提到前台（`GLFW_FOCUSED=0`，`glfwShowWindow/glfwFocusWindow` 无效；`glfwSetCursorPos` 后 `mouseHandler.xpos()` 仍是 427,240）⇒ **真鼠标悬停取证不可行**，tooltip 只能「自绘 Screen 渲染 tooltip 列表再截图」。

## 5. 断言逐条（用例 `scripts/test/cases/RELEASE150-1.21.1.json`：31 steps / 27 asserts）

结果：**PASS 25 / FAIL 0 / MANUAL 2（人工判定均 PASS）**。权威结果文件 `run/1.21.1/.tmp/rel150-results-final.json`。

| # | 类型 | 断言 | 判定 | 证据（原文） |
|---|---|---|---|---|
| 00 | log(whole) | `Astral Dice 1\.5\.0\+neoforge_1\.21\.1` | PASS | `Astral Dice 1.5.0+neoforge_1.21.1 (astral_dice)` |
| 01 | log | `\[REL150\] ver=1\.21\.1 gamemode=creative ` | PASS | 见 04 行 |
| 02 | log | `\[REL150\] ver=1\.21\.1 ` + ` pos=100,-?[0-9]+,100 ` | PASS | `pos=100,120,100` |
| 03 | log | `\[REL150\] ver=1\.21\.1 ` + ` hp=20 ` | PASS | `hp=20` |
| 04 | log | `\[REL150\] ver=1\.21\.1 ` + ` item=astral_dice:abyss_reagent_chip` | PASS | `12018 \| [0211月2026 03:27:23.543] [Server thread/INFO] [KubeJS Server/]: autojs-eval#23: [REL150] ver=1.21.1 gamemode=creative pos=100,120,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+neoforge_1.21.1` |
| 05 | log | `\[REL150\] ver=1\.21\.1 ` + ` xp=30 ` | PASS | `xp=30` |
| 06 | log | `\[REL150\] ` + ` slotted=[1-9][0-9]* ` | PASS | `slotted=6` |
| 07 | log | `\[REL150\] ` + ` chips=3 ` | PASS | `chips=3` |
| 08 | log | `\[REL150\] ` + ` modver=1\.5\.0\+` | PASS | `modver=1.5.0+neoforge_1.21.1` |
| 09 | log | `\[REL150\] chip id=... count=3 name=(深渊试剂\|Abyss Reagent) ` | PASS | `12057 \| ... [KubeJS Client/]: autojs-eval#28: [REL150] chip id=astral_dice:abyss_reagent_chip count=3 name=深渊试剂 tipn=9 path=screen` |
| 10 | log | `\[REL150\] tip i=0 text=\.+` | PASS | `[REL150] tip i=0 text=深渊试剂` |
| 11 | log | `\[REL150\] guide key=astral_dice\.guide\.entry\.abyss_reagent_chip\.1 text=\.+` | PASS | `[REL150] guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=装备时治愈层数 +2；触发治愈回血时，获得「实际治疗量 ×2」的伤害吸收。` |
| 12 | log | `\[REL150\] inventory open=1` | PASS | `[REL150] inventory open=1 hover=133,172 slot=45 win=266,344` |
| 13 | log | `CHIP_SHOP_OPEN player=` | PASS | `[0211月2026 03:27:24.549] [Server thread/INFO] [com.merlinkitsune.astral_dice.shop.ChipShopOpener/]: [astral_dice] CHIP_SHOP_OPEN player=DevTester offers=eight_sided_dice_chip+revenge_halberd_chip+whetstone_chip purchased=0 refreshes=3` |
| 14 | log | `\[REL150\] screen=\S*ChipShopScreen` | PASS | `[REL150] screen=com.merlinkitsune.astral_dice.screen.ChipShopScreen` |
| 15 | log | `\[REL150\] sys id=astral_dice:dice ok=1` | PASS | `[REL150] sys id=astral_dice:dice ok=1 name=骰子` |
| 16 | log | `\[REL150\] sys id=astral_dice:attack_card_medium ok=1` | PASS | `[REL150] sys id=astral_dice:attack_card_medium ok=1 name=攻击(中)` |
| 17 | log | `\[REL150\] sys id=astral_dice:abyss_reagent_chip ok=1` | PASS | `[REL150] sys id=astral_dice:abyss_reagent_chip ok=1 name=深渊试剂` |
| 18 | log | `\[REL150\] sys id=astral_dice:jill_sign ok=1` | PASS | `[REL150] sys id=astral_dice:jill_sign ok=1 name=调酒师立牌` |
| 19 | eval(from=state) | `\[REL150\] ver=\S+ gamemode=creative pos=100,-?[0-9]+,100 hp=20 .*item=astral_dice:abyss_reagent_chip` | PASS | 结构化值 = 04 的完整机器行 |
| 20 | eval(from=chip, field=name) | `^(深渊试剂\|Abyss Reagent)` | PASS | `name=深渊试剂` |
| 21 | eval(from=screen, field=screen) | `ChipShopScreen$` | PASS | `screen=com.merlinkitsune.astral_dice.screen.ChipShopScreen` |
| 22 | absent | `Unknown or incomplete command\|未知或不完整的命令\|No player was found\|没有找到玩家\|No entity was found\|没有与选择器匹配的实体` | PASS | 全日志 0 命中 |
| 23 | jar | `models_min=120 / signs_min=25 / entries_min=1700` | PASS | `models=158 >= 120 ok; signs=32 >= 25 ok; entries=2016 >= 1700 ok` |
| 24 | crash | `crash-reports` 为空 | PASS | `crash-reports empty`（目录不存在） |
| 25 | vision | tag `abyss_reagent_tooltip`：画面里是否看得到 tooltip 或背包界面 | **PASS（人工，附注）** | 看到了：画面是**创造模式物品栏**（上方标签页、类别「建筑方块」），并且确有 tooltip 浮出——但浮出的是 `空心云杉原木 (quark:hollow_spruce_log)`，**不是深渊试剂**。原因见 \6.1；「深渊试剂 tooltip 可读」由补充截图 `abyss_reagent_tooltip_prod.png` 证明 |
| 26 | vision | tag `chip_shop_ui`：画面是否是筹码商店/容器界面 | **PASS（人工）** | 是商店：顶部 5 个货位（`八面骰 10` / `复仇之戟` / `磨刀石` / `3`）、中部空格货架、底部筹码条，与日志 `offers=eight_sided_dice_chip+revenge_halberd_chip+whetstone_chip` 一致 |

三系统覆盖（任务要求「骰子/卡牌/筹码至少各一条断言」）：骰子 `#15`、卡牌 `#16`（attack_card_medium）、筹码 `#17` + `#09` + 筹码商店 `#13/#14/#26`。物品与文本：`alyss\_reagent` 不存在——实际 id 为 `astral_dice:abyss_reagent_chip`，存在/give/名称/tooltip/图鉴条目均由 `#04/#09/#10/#11/#17/#20` 覆盖。

## 6. 截图（绝对路径 + tag）

| tag | 绝对路径 | 大小 | sha256 |
|---|---|---|---|
| `abyss_reagent_tooltip` | `F:\MCProject\astral_dice_multiloader\run\1.21.1\prodgame\screenshots\abyss_reagent_tooltip.png` | 165951 B | `20da11f7101b84891827beba3de92f7b6b44a333e0f428ddb5548baa3b0b7ae6` |
| `chip_shop_ui` | `F:\MCProject\astral_dice_multiloader\run\1.21.1\prodgame\screenshots\chip_shop_ui.png` | 94752 B | `93d54e4d4dcb27356b1e09ec477807a1821c53f3201737e81d45d5170bc88b8d` |
| `release150_guide` | `F:\MCProject\astral_dice_multiloader\run\1.21.1\prodgame\screenshots\release150_guide.png` | 105910 B | `3846fe2ba5e1dbf15362268f978d86539c871b55520e0d3c69a731a0f5448225` |
| `abyss_reagent_tooltip_prod`（补充，非用例 tag） | `F:\MCProject\astral_dice_multiloader\run\1.21.1\prodgame\screenshots\abyss_reagent_tooltip_prod.png` | 84147 B | `9d7f9e47227111270d1d7f481d0cb1f9b03bc84c62960b2c9d21a87a81de272a` |
| `ysm_thirdperson_prod`（YSM 正证据，非用例 tag） | `F:\MCProject\astral_dice_multiloader\run\1.21.1\prodgame\screenshots\ysm_thirdperson_prod.png` | 112728 B | `d44f25a7028df1cc7006d81ee432dbfea50f2dd2f73eebd0d5ab32f6fe58ea9b` |

读图结论（人工）：
- `abyss_reagent_tooltip_prod.png`：**可清晰读出**「深渊试剂 / 栏位: 筹码 / astral_dice:abyss_reagent_chip / 装备时治愈层数 +3，触发治愈回血时：获得实际治疗量 ×2 的伤害吸收 / 当前治愈: 0 / 32 / 按住 CTRL 来查看标签 / Astral Dice」共 9 行。
- `chip_shop_ui.png`：筹码商店界面（货位 + 价格 + 底部筹码条）。
- `release150_guide.png`：Patchouli 手册「恋的规则书」类别页（左侧带 YSM 玩家模型）。
- `ysm_thirdperson_prod.png`：第三人称下玩家为 YSM 动漫模型。

### 6.1 用例自带 `abyss_reagent_tooltip` 截图为何拍不到深渊试剂

用例 step 4 把玩家切到 **creative**，而 step 18 的定位按**生存物品栏**布局算坐标（`lx=(w-195)/2`、悬浮点 `leftPos+17, topPos+151`），创造栏布局不同 ⇒ 悬浮落在别的格子，拍到的是 `quark:hollow_spruce_log` 的 tooltip。这是**用例自身坐标假设与 creative 状态冲突**，不是渲染失败：日志侧 chip/tooltip 断言（`#09/#10/#12/#17/#20`）全部 PASS，因为它们走 `Screen.getTooltipFromItem` 而非渲染命中。**未修改用例**（禁令），改为补一张 `abyss_reagent_tooltip_prod` 截图作为「图上能读出深渊试剂」的证据。

## 7. 收尾（已完成）

- 停客户端：`run/1.21.1/.tmp/stop_mine.ps1` → `stopping PID 35612 (java) started 11/02/2026 03:22:29` / `STOPPED OK`。
- 误触发的一次启动（命令里 `--quietplay` 拼错，实际起了 pid 17536）已在数秒内 `STOPPED OK`；其连带的 `CrashAssistant` 助手进程 28700 也已停止（由该客户端启动时自起的子进程，父进程已死）。
- 端口：`Get-NetTCPConnection -State Listen` 在 25565/25575/25585/25586 上**无任何监听**；`curl http://127.0.0.1:25586/health` 返回 `000`（连接失败）。（netstat 偶现一条 `25586 ... LISTENING 0` 的 `OwningProcess=0` 记录，无进程承接，属内核残留表项。）
- 剩余 java 进程全部非本任务：`5088 / 37020`（gradle daemon）、`29924`（fabric 线 gradlew）、`37252`（fabric 线 KnotServer）、`35632`（别人的 1.20.1 客户端）。
- 实例 mods 恢复：`prod_scene.py revert` 移除 AutoJS（`removed D:\.minecraft\versions\狐の航空学 Voxy Edition\mods\AutoJS-neoforge-1.21.1-1.0.0.jar`）；`manifest-after` = 511 条；`ls .../mods | grep -i autojs` → `no AutoJS in instance mods`。
- `diff -u instance-mods-before.txt instance-mods-after.txt` 全文**只有一处差异**：

```diff
--- run/1.21.1/.tmp/instance-mods-before.txt	2026-11-02 02:56:40
+++ run/1.21.1/.tmp/instance-mods-after.txt	2026-11-02 03:28:51
@@ -25,7 +25,6 @@
-Ixeris-4.6.11+1.21.1-neoforge.jar	698847	1793451343
 Ixeris-4.6.11-1.21.1-neoforge.jar	698847	1793451343
```

该条**不是我的改动**：我的注入只新增 AutoJS；这个 jar 在 02:56–03:02 之间被**外部并发工作流**删除（两份 Ixeris sha256 相同、manifest 都声明 `Automatic-Module-Name: me.decce.ixeris.neoforge`，并存时 FML 会给第二个建 `ixeris_dummy` 模块并因包导出冲突崩在 `ModuleLayerHandler.buildLayer`——这也是我第一次启动失败的原因，处理方式是从**一次性副本** `prodgame/mods` 删掉加号那份，没动玩家实例）。两份清单都不含 AutoJS 行 ⇒ 注入已完全回收。

## 8. 已证 / 未证 / 局限

### 已证（只列有实读日志或真实截图支撑的）

1. **production 形态可启动该 1.21.1 整合包**：`classpath entries: 107 missing: 0`、`unresolved placeholders: none`、argv 无 userdev 标记；`Sound engine started`、`Total time to load game and open world was 161.34793 seconds`；25586 端点双层 eval 真实产出。
2. **YSM err54 被绕过**：全会话 `err: 54` / `Unsatisfied runtime environment` / `Failed to load native lib` / `Data attachment type with default value` 计数均 0，`crash-reports` 目录不存在。
3. **YSM 在 production 下真的接管玩家渲染**：本次启动解出 63 MB / 676 个内置模型文件（该目录每次启动清空）+ 第三人称截图里玩家为动漫模型 + 包内无其他玩家模型模组。
4. **本模组加载**：`Astral Dice 1.5.0+neoforge_1.21.1 (astral_dice)`。
5. **RELEASE150-1.21.1 的 31 steps 在 production 下全部可执行**（11 条 `inject_command` 全部 ok、3 张截图落盘、骰子/卡牌/筹码/立牌注册读取 ok、筹码商店真的打开、Patchouli 手册真的打开），自动断言 25 PASS / 0 FAIL。
6. **AutoJS 在 production 形态可用**（server + client 双侧），可作发布验收通道；但脚本写法受 \4.5 约束。

### 未证

1. **YSM 半透明兼容层的像素级 A/B**（`docs/features/ysm-compat-err54-and-verification.md` \8.7 的 1.21.1/1.20.1 待实测表）：本报告只证明 YSM 渲染生效，**没有**做「兼容层开/关两态同机位像素差」的量化对比 ⇒ 该行 1.21.1 结论仍为「未测」。
2. **production 形态的长时间稳定性 / 多人环境**：单次会话约 15 分钟、1 个本地玩家、客户端内置服；无多人、无 soak。
3. **YSM 外部（非内置）模型包**：只用 `builtin` 默认资源，未投放玩家自定义 ysm 模型验证加载链路。
4. **vision 两条没有机器判据**：由我读图人工判定；`abyss_reagent_tooltip` 那张按其字面问题（「是否看得到 tooltip 或背包界面」）判 PASS，但它拍到的是**别的物品**的 tooltip，真实「深渊试剂 tooltip 可读」靠补充截图证明。

### 局限

1. **没有复用 `mt.ps1` / `mt_case.ps1`**（\3）：自写执行器断言语义对齐但**不是同一份实现**，报告判定不等同官方测试台判据；建议后续给 `mt_assert` 加 `--endpoint` / `--log-root` 覆盖后做一次官方口径复跑。
2. **用例自带 tooltip 截图与 creative 状态冲突**（\6.1），本次按禁令未改用例；要让自带 tag 的截图直接可读，需按 `mc.player.containerMenu` 实际槽位定位，或把该 step 重排到切 creative 之前。
3. **执行器修的 7 个缺陷**（\3.1）说明「先跑一遍拿到 25 PASS」本身有假阳性风险；本报告数字来自修复后的 `--run-label final3` 跑批，且断言只认 banner 之后的时间戳日志行 + 只认结构化 eval 字段。
4. **日志字符集是 cp936**（本机 Minecraft 写入），按 UTF-8 读 `latest.log` 会把中文打坏 ⇒ 任何按默认编码读日志的工具（含 python 默认编码）都会出现中文断言恒 FAIL 的假阴性。这是测试台层面的潜在缺陷，值得单独修。
5. **会话结束时日志会滚动**：`latest.log` 已被压成 `logs/2026-11-02-4.log.gz`（本报告证据锚点，sha256 `3bb742cf59542c1513d0033ebf407cd8d931fad66aa57b41d8da35ef6359acec`），另有只读副本 `run/1.21.1/.tmp/rel150-final-session.log.gz`。只读 `latest.log` 的取证脚本在会话结束后读不到本轮内容。
6. **一次性现场保留未清**：`run/1.21.1/prodgame`（512 jar ≈1.2 GB + 114 MB 世界 + YSM 63 MB 资源）留作复现；它完全独立于玩家实例的 `saves/`，如需回收请手工删该目录。
7. **端口 25585 的争用是环境事实**：本任务全程用 25586，未触碰并发 dev 客户端与 fabric 工作流；但并发工作流在 03:06 前后清理 java 进程时**误杀了我的第一个客户端**（pid 42100，日志断尾、无 crash 文本），这是本次需要多起一次客户端的原因。

## 9. 产物清单（便于复核）

| 文件 | 说明 |
|---|---|
| `run/1.21.1/.tmp/prodlaunch.py` | 1.21.1 production 启动器（--dry-run / --quickplay / --autojs-port / --xmx / --username / --game-arg） |
| `run/1.21.1/.tmp/prod_scene.py` | 现场整备：manifest-before/after、inject/revert、scene（一次性 gameDir + 世界 + mods 拷贝 + options） |
| `run/1.21.1/.tmp/pre_setup3.py` | 跑批前状态整备（血量/饱食/落地平台/重生规则/hotbar 选择位） |
| `run/1.21.1/.tmp/rel150_prod.py` | 最小执行器（MCP Streamable-HTTP 客户端 + op + 断言 + --run-label banner） |
| `run/1.21.1/.tmp/tip_prod.py` | 自绘 tooltip 截图脚本 |
| `run/1.21.1/.tmp/rel150-results-final.json` | 权威跑批逐条判定与 evidence |
| `run/1.21.1/.tmp/evidence3.txt` | 从滚动日志抽取的原文行（含行号），报告引文均出自此文件 |
| `run/1.21.1/.tmp/instance-mods-before.txt` / `instance-mods-after.txt` | 实例 mods 前后清单（已恢复的证据） |
| `run/1.21.1/.tmp/rel150-final-session.log.gz` | 权威会话日志只读副本 |
