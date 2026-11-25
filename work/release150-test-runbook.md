# Astral Dice 1.5.0 · 发布实机测试执行手册（RELEASE150 四条线）

> 适用范围：为 1.5.0 正式版做**发布验收**，用「模拟玩家数据 + 截图 + 机器断言」证明本版在四条线
> （`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`）可用。
> 本文只讲**怎么跑**；用例的断言细节见第 9 节对照表与用例文件本身。
> 本文不含任何 build/gradlew 前置之外的写操作；测试台脚本（mt.ps1 / mt_case.ps1 等）一律只读。

---

## 0. 一句话流程

每条线都是同一个五步：
**env（模组稳态 + 世界就绪）→ launch（客户端进世界）→ cases --case（跑 RELEASE150 用例）→ report（出报告）→ stop --force --purge-saves（收停清档）**。
三条 NeoForge/Forge 线全程 pwsh（`scripts/test/mt.ps1`）；**fabric-1.20.1 的 env/launch 走 bash 层**
（`scripts/test/win/`），**cases/report 必须回到 pwsh**（原因见 §4.1，bash 侧的 fabric 用例执行器不支持
`screenshot` / `eval_js` / `vision`）。

---

## 1. 四态清单

| 线 | 用例文件 | env / launch | cases / report | 必备前置 |
|---|---|---|---|---|
| neoforge-1.21.1 | `scripts\test\cases\RELEASE150-1.21.1.json` | pwsh `mt.ps1`（env 里的 world 子阶段直接调 `mt_env.ps1`，见 §3.1） | pwsh `mt.ps1` | 无种子包 ⇒ 现场生成 `testworld`，必须给 `mt_env.ps1 world --timeout 900` |
| forge-1.20.1 | `scripts\test\cases\RELEASE150-1.20.1.json` | pwsh `mt.ps1` | pwsh `mt.ps1` | `resources/testworld-seed-1.20.1.zip` 在位（已有） |
| neoforge-26.1.2 | `scripts\test\cases\RELEASE150-26.1.2.json` | pwsh `mt.ps1` | pwsh `mt.ps1` | `resources/testworld-seed-26.1.2.zip` 在位（已有） |
| fabric-1.20.1 | `scripts\test\cases\RELEASE150-fabric-1.20.1.json` | **bash** `win/mt.sh --side client` | **pwsh** `mt_case.ps1 run` + `mt.ps1 --phase report` | ① `ft_env.ps1 --install-embedded` ② `fabric-1.20.1/run/client/saves/testworld` 必须存在 ③ launch 带 `--enable-autojs` |

四条用例均为：`channel=autojs`、31 步、27 条断言、3 张截图、11 条 `inject_command`、
`on_fail=keep_game_running`（fabric 用例额外带 `"side":"client"`）。

---

## 2. 共同事实（跑之前先记住这 6 条）

1. **报告落点**：`D:\MCProject\astral_dice_multiloader\scripts\test\reports\<run_id>\<version>\`
   （`report.md` / `latest.log` / `debug.log` / `kubejs_server.log` / `runclient_launch.log` / `screenshots/`）。
   `<run_id>` 是**粘性**的，存在 `scripts\test\cases\.mt_active_run`。
2. **截图真值**：`run/<version>/screenshots/`（fabric：`fabric-1.20.1/run/client/screenshots/`），
   tag→文件名映射**只在**同目录的 `.mt_shots.json` 里；断言侧只允许走 `Get-MtCurrentShots(run_id)`
   （实现：`scripts/test/lib/Mt.Paths.psm1:335`）。**不要 glob 截图目录**（会误认上一代残图）。
3. **退出码**：PASS=0 / FAIL=1 / ERROR=2 / PREFLIGHT=10 / BLOCKED=11 / TIMEOUT=12。
4. **机器行**：`MT_<PHASE>: OK|FAIL|BLOCKED|ERROR`、`MT_CASE_RESULT: <case> = PASS|FAIL|…`、
   `MT_CHANNEL: delegated_count=<n>`、`LAUNCH: READY|WORLD_READY|…`（bash 侧）。
5. **auto 闸门**：不带 `--allow-auto` 时**只放行**「单阶段且带 `--version`」与「`--phase cases --case <一条>`」；
   `--phase report`、`--phase cases`（整目录）、全流程都必须加 `--allow-auto`。
6. **命令选择器口径**：本用例所有 `inject_command` 经 `AutoJS.command()` 执行，其服务端实现是
   `Commands.performPrefixedCommand(server.createCommandSourceStack(), cmd)`（字节码取证：
   `AutoJS-neoforge-1.21.1-1.0.0.jar` 内 `AutoJSVanilla.runCommand` 调 `MinecraftServer.createCommandSourceStack`），
   **命令源是控制台不是玩家** ⇒ 用例里目标一律写 `@a`；`@s` 会静默失败。改用例时不得改回 `@s`。

---

## 3. 三条 pwsh 线（1.21.1 / 1.20.1 / 26.1.2）

> 所有命令都写在**同一个 pwsh 会话**里（`$env:…` 才会被下面的 `pwsh -File` 子进程继承）。
> 路径一律绝对路径；不要在 bash 里用 `"...\$var"` 拼 pwsh 参数（反斜杠会吞变量）。

### 3.1 neoforge-1.21.1（无种子包，env 现场生成世界）

```powershell
# 1) 前置：只查本线残留进程（不 --all，避免误停别的线）
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 1.21.1 --phase preflight

# 2) 环境：分两步，**不要**用 mt.ps1 --phase env（原因见下方注）
#    2a) 模组稳态
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt_env.ps1' mods --version 1.21.1
#    2b) 世界重建：本线没有种子包 ⇒ 现起 runServer 生成 testworld。
#        ⚠️ 世界生成期限由 mt_env.ps1 自己的 --timeout 决定（默认 180s）；mt.ps1 --phase env **不转发**该参数
#        （mt.ps1:478 只把相位预算当进程超时传给子进程），所以必须直接调 mt_env 并把 --timeout 抬到 900
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt_env.ps1' world --version 1.21.1 --timeout 900
#  期望：MT_WORLD: OK — 世界重建（allowCommands=1, keepInventory=true） …\run\1.21.1\saves\testworld
#  失败取证：run\1.21.1\mt_server_gen.log 与 .err
#  注：mt.ps1 --phase env 也能跑，但它给 world 子阶段的是「相位预算」（MT_ENV_TIMEOUT_SEC，默认 240s），
#      而 world 内部的生成期限仍是 180s ⇒ 生成慢时照样 MT_WORLD: BLOCKED。发布验收要跑大世界，直接用上面两步。

# 3) 启动：客户端 quickplay=testworld 直接进世界后**脱离执行**（本命令返回时游戏仍在跑）
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 1.21.1 --phase launch
#  期望：MT_LAUNCH: OK — 已进入世界（quickplay=testworld）

# 4) 用例（单条，**不需要** --allow-auto）
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 1.21.1 --phase cases --case 'D:\MCProject\astral_dice_multiloader\scripts\test\cases\RELEASE150-1.21.1.json'
#  期望：MT_CASE_RESULT: RELEASE150-1.21.1 = PASS

# 5) 报告（需要 --allow-auto）
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 1.21.1 --phase report --allow-auto

# 6) 收停 + 清档
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 1.21.1 --phase stop --force --purge-saves
```

**备用快路径（不想等世界生成时）**：若 `run\1.21.1\saves\testworld` 已存在（或把
`run\1.21.1\saves\verify` 复制成 `testworld`），可只跑 `mt_env.ps1 mods`，跳过 `world` 子阶段：

```powershell
Copy-Item -Recurse -Force 'D:\MCProject\astral_dice_multiloader\run\1.21.1\saves\verify' 'D:\MCProject\astral_dice_multiloader\run\1.21.1\saves\testworld'
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt_env.ps1' mods --version 1.21.1
```

### 3.2 forge-1.20.1

```powershell
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 1.20.1 --phase preflight
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 1.20.1 --phase env
#  本线有 resources/testworld-seed-1.20.1.zip ⇒ 秒级快恢复，无需抬预算
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 1.20.1 --phase launch
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 1.20.1 --phase cases --case 'D:\MCProject\astral_dice_multiloader\scripts\test\cases\RELEASE150-1.20.1.json'
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 1.20.1 --phase report --allow-auto
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 1.20.1 --phase stop --force --purge-saves
```

> 建议**先跑 1.20.1**：它的 `env` 会产生 `run\1.20.1\saves\testworld`，fabric 线（§4）的世界可以直接从它复制。

### 3.3 neoforge-26.1.2

```powershell
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 26.1.2 --phase preflight
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 26.1.2 --phase env
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 26.1.2 --phase launch
#  本线就绪 marker 与另两线不同（logged in with entity id + Loaded <N> advancements）
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 26.1.2 --phase cases --case 'D:\MCProject\astral_dice_multiloader\scripts\test\cases\RELEASE150-26.1.2.json'
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 26.1.2 --phase report --allow-auto
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version 26.1.2 --phase stop --force --purge-saves
```

---

## 4. fabric-1.20.1（bash 起停 + pwsh 跑用例）

### 4.1 为什么必须混用两层

- **env 不能走 pwsh**：`mt.ps1:852-859` 的 `$pwshFlowExcludes = @('fabric-1.20.1')`，理由是
  `mt_env.ps1` / `mt_loadergate.ps1` 的稳态模组期望表还是 NeoForge/Forge 口径，对 fabric 会给出
  **假 BLOCKED**（实测：`MT_MODS: BLOCKED — 整合包缺少 *sodium-neoforge*0.8.13*.jar, *iris-neoforge*…`）。
- **cases 不能走 bash**：bash 侧 fabric 用例执行器（`win/lib/fabric.sh:660-710`）对 `screenshot`
  （`screenshot 在 bash 侧未实现`）、`vision`（`vision 需要截图通道（bash 侧未实现）`）、
  `eval_js`/`await_state`/`mcp_status`（需 AutoJS）一律返回 BLOCKED ⇒ 本用例 3 张截图 + 9 个 eval
  全都会被打掉，跑出来的「PASS」是假的。
- ⇒ **bash 负责 env/launch/stop，pwsh 负责 cases/report**。

### 4.2 前置（缺一不可）

```powershell
# 前置 A：dev 内嵌库投放（KI-F23）。Loom 重映射会剥掉 fabric.mod.json 的 jars 声明，
#         不打这一步 Puzzles Lib / Accessories / Patchouli / Cloth Config / KubeJS 在 dev 里全起不来
#         （HARD_DEP_NO_CANDIDATE / NoClassDefFoundError）。换 -PtestTrinkets / -PtestAccessories 后必须重跑。
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\fabric\ft_env.ps1' --side both --install-embedded

# 前置 B：世界（quickplay 默认 testworld，而 fabric-1.20.1\run\client\saves 目前是空的；
#         缺世界 ⇒ 客户端停在标题画面，服务端 eval 全废）
$dst = 'D:\MCProject\astral_dice_multiloader\fabric-1.20.1\run\client\saves'
New-Item -ItemType Directory -Force -Path $dst | Out-Null
Copy-Item -Recurse -Force 'D:\MCProject\astral_dice_multiloader\run\1.20.1\saves\testworld' (Join-Path $dst 'testworld')
#  若 1.20.1 线还没跑过（没有 saves\testworld），退而用同版本的 run\1.20.1\saves\verify 复制成 testworld；
#  再不行就起一次 fabric 服务端生成：bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase launch，收停后把
#  fabric-1.20.1/run/server/world 复制成 fabric-1.20.1/run/client/saves/testworld。
```

### 4.3 命令序列

```bash
# 1) 环境（bash 权威实现；只查本线）
bash /d/MCProject/astral_dice_multiloader/scripts/test/win/mt.sh --version fabric-1.20.1 --side client --phase env

# 2) 启动客户端并**显式开 AutoJS**（--enable-autojs = -PautojsTestMod=1；
#    不开则 AutoJS 不是运行时依赖，注入退回 RCON，本用例的 eval_js/截图断言全部无法成立）
bash /d/MCProject/astral_dice_multiloader/scripts/test/win/mt.sh --version fabric-1.20.1 --side client --enable-autojs --phase launch
#  期望：LAUNCH: READY … （AutoJS /health 探到 side=client available=true）
```

```powershell
# 3) 用例（pwsh；MT_SIDE 显式钉到 client，避免 report/case 读到 server 的 run 目录）
$env:MT_SIDE='client'
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt_case.ps1' run --version fabric-1.20.1 --case 'D:\MCProject\astral_dice_multiloader\scripts\test\cases\RELEASE150-fabric-1.20.1.json'
#  等价的包装入口：pwsh -NoProfile -File ...\mt.ps1 --version fabric-1.20.1 --phase cases --case <同上>
#  期望：MT_CASE_RESULT: RELEASE150-fabric-1.20.1 = PASS

# 4) 报告
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1' --version fabric-1.20.1 --phase report --allow-auto
```

```bash
# 5) 收停 + 清档（fabric 收停同样以 bash 为准）
bash /d/MCProject/astral_dice_multiloader/scripts/test/win/stop.sh --version fabric-1.20.1 --force --purge-saves
```

> `--side` 只有 `--version fabric-1.20.1` 接受；对另三线传 `--side` 会直接
> `MT_ERROR: --side 只对 fabric-1.20.1 有意义`（exit 2）。

---

## 5. 从报告里取证（四条线通用）

```powershell
# A) 本轮报告目录
$rid = (Get-Content 'D:\MCProject\astral_dice_multiloader\scripts\test\cases\.mt_active_run' -Raw).Trim()
$rep = "D:\MCProject\astral_dice_multiloader\scripts\test\reports\$rid\1.21.1"   # fabric 改成 fabric-1.20.1
Get-ChildItem $rep | Select-Object Name,Length

# B) 模拟玩家数据证据行（一用例只有一条，前缀 [REL150]）
Select-String -LiteralPath (Join-Path $rep 'latest.log') -Pattern '\[REL150\]' | ForEach-Object { $_.Line }

# C) 只捞状态行（贴验收单用）
(Select-String -LiteralPath (Join-Path $rep 'latest.log') -Pattern '\[REL150\] ver=' | Select-Object -First 1).Line
#  形如： [REL150] ver=1.21.1 gamemode=creative pos=100,120,100 hp=20 food=20 xp=30 arm=0 slotted=16 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+neoforge_1.21.1

# D) 截图清单（tag → 文件；用测试台唯一认可入口，别 glob）
pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt_capture.ps1' list --version 1.21.1
#  或直接读清单： Get-Content 'D:\MCProject\astral_dice_multiloader\run\1.21.1\screenshots\.mt_shots.json' -Raw | ConvertFrom-Json | Select-Object -ExpandProperty shots | Format-Table tag,file,ts
#  fabric： run 根换成 D:\MCProject\astral_dice_multiloader\fabric-1.20.1\run\client\screenshots\.mt_shots.json

# E) 结论与断言明细
Get-Content (Join-Path $rep 'report.md') -Raw
```

三张截图固定 tag（四条线一致，报告里 `screenshots/` 是同批副本）：

| tag | 拍的是什么 | 用途 |
|---|---|---|
| `abyss_reagent_tooltip` | 背包界面 + 悬停 `astral_dice:abyss_reagent_chip` | 改名验收（`深渊试剂 / Abyss Reagent`） |
| `chip_shop_ui` | `ChipShopScreen` 筹码商店 | 功能界面 + 商店系统回归 |
| `release150_guide` | Patchouli 手册（尽力而为，失败不 gate PASS） | 图鉴/手册回归 |

---

## 6. 失败时保留现场

用例自身已带 `on_fail: "keep_game_running"`（失败即写 `scripts\test\cases\.mt_keep_alive-<version>`，
退出清理只 warn 不杀客户端；**TIMEOUT 不写该标记**，所以下一条用例仍能跑）。补充手段：

| 想干什么 | 开关 |
|---|---|
| 失败后不要自动收停（整轮） | `mt.ps1 … --no-cleanup-on-exit` |
| 收停但**保留存档** | `--phase stop --force`（不带 `--purge-saves`），或 `--keep-saves` |
| 先出报告再收停 | 顺序固定 §0；`report` 只读日志与截图，不收停 |
| 单条用例给更多时间 | `--case-timeout 900` 或 `$env:MT_CASE_TIMEOUT_SEC='900'` |
| 启动等更久 | `$env:MT_LAUNCH_READY_TIMEOUT_SEC='300'` |
| fabric 卡在端点 | `MT_ALLOW_NO_AUTOJS=1` **仅用于定位**（会退化成纯日志 marker 判据，用例的 eval/截图断言必然 BLOCKED） |

---

## 7. 机器行 → 原因 → 处置（速查）

| 机器行 | 真实原因 | 处置 |
|---|---|---|
| `MT_WORLD: BLOCKED — 未找到种子包 resources/testworld-seed-1.21.1.zip` | 手工加了 `--seed` 而本线没有种子包 | 去掉 `--seed`（`--phase env` 默认不加）；走现场生成，并按 §3.1 抬 `MT_ENV_TIMEOUT_SEC` |
| `MT_WORLD: BLOCKED — 世界未在时限内生成` | `runServer` 生成被 **mt_env 内部的 180s** 掐断（`mt.ps1 --phase env` 不转发 `--timeout`） | 直接 `pwsh -File scripts\test\mt_env.ps1 world --version <v> --timeout 900`；查 `run/<v>/mt_server_gen.log(.err)` |
| `MT_MODS: BLOCKED — 整合包缺少 *sodium-neoforge*…` | fabric 线误用了 pwsh `env` | 改走 §4.3 bash 层 |
| `LAUNCH: BLOCKED` / 客户端停在标题画面 | quickplay 的 `testworld` 不存在（fabric 高发） | 按 §4.2 前置 B 备世界；1.21.1 先跑 `--phase env` |
| `MT_CASE_RESULT: … = BLOCKED`（`endpoint=unavailable`） | AutoJS 端点 25585 没起 / fabric 没开 `--enable-autojs` | 先核端点，再补开关重跑 launch |
| 断言 FAIL 且日志有 `No player was found` / `没有找到玩家` | 命令写成了 `@s`（控制台源解析不到玩家） | 用例里已统一 `@a`；改用例时不要改回 |
| `chip id=… tipn=0` / `tip i=0` FAIL | 客户端不在世界内，或悬停没生效（`hover=skip` 属已知软失败） | 确认 quickplay 进了世界；截图仍应落盘，按 `abyss_reagent_tooltip` 目视复核 |
| `CHIP_SHOP_OPEN player=` 缺失 | `ChipShopOpener.open` 被三道闸门之一拒（`owned_all` / `inventory_full` / `cooldown`） | 用例已先 `ChipShopState.store(p, EMPTY)` 重置；若仍拒，查 `inventory_full`（先 `/clear @a` 再给物） |
| `MT_AUTO_DISABLED` | 少了 `--allow-auto` | `--phase report` / 整目录 cases / 全流程加 `--allow-auto` |
| `MT_CASE_RESULT: … = TIMEOUT` | 单条超 600s | `--case-timeout 900` |

---

## 8. 执行风险（按危害排序）与规避

1. **1.21.1 没有种子包 ⇒ 要现起 `runServer` 生成 `testworld`，而 `mt.ps1 --phase env` 给世界生成留的期限只有 180s**
   （证据：`mt.ps1:478` 调 `mt_env.ps1 world` 时只传 `-TimeoutSec $envBudget`，不传 `--timeout`；
   `mt_env.ps1` 入口的 `$TimeoutSec` 默认 180）⇒ 生成一旦超 180s 就 `MT_WORLD: BLOCKED`，
   抬 `MT_ENV_TIMEOUT_SEC` **无效**（那只管父进程预算）。规避：按 §3.1 第 2b 步直接
   `mt_env.ps1 world --version 1.21.1 --timeout 900`。失败取证在 `run\1.21.1\mt_server_gen.log`。
   **不要**为了省时间跳过世界准备直接 `--phase launch`，那样客户端停标题画面、用例会在 state 行上莫名其妙地全红。
2. **fabric 的世界与 AutoJS 是双重前置**：`run/client/saves` 为空 + AutoJS 只是 `-PautojsTestMod=1` 条件依赖
   ⇒ 少任何一项，用例都会 BLOCKED（不是 FAIL，容易被误读成「环境问题不用管」）。
   规避：§4.2 两条前置都做完再 launch；launch 必须带 `--enable-autojs`；
   并且**必须用 pwsh 跑 cases**（bash 的 fabric cases 会静默降级截图/eval）。
3. **截图取证依赖「悬停 → tooltip」这条链**：hover 注入用反射改 `MouseHandler` 坐标，失败时用例只打
   `hover=skip` 并继续；`vision` 断言返回 DELEGATED（永不计 PASS）⇒ 图必须**人眼复核**
   `abyss_reagent_tooltip`，不要把它当成机器结论。
4. **`type=jar` 不能核对文件名**（该断言字段是闭集，没有 filename 判据）。故「jar 名含 1.5.0」由两处证据满足：
   用例里 `scope=whole` 的版本行断言（1.21.1 `Astral Dice 1\.5\.0\+neoforge_1\.21\.1` /
   1.20.1 `mod_version=1\.5\.0\+forge_1\.20\.1` / 26.1.2 `astral_dice-1\.5\.0\+neoforge_26\.1\.2\.jar` /
   fabric `- astral_dice 1\.5\.0\+fabric_1\.20\.1`）＋ 用例内 `modver=1\.5\.0\+` 断言；
   发布前再补一条文件级核对：
   `Get-ChildItem D:\MCProject\astral_dice_multiloader\<subproject>\build\libs\astral_dice-1.5.0*.jar | Select-Object Name`
5. **rc 与正式版的版本号差异**：现有 `latest.log` 快照里出现过 `1.5.0-rc.7+…`。本版锁正式版后，
   版本行断言用的正则不匹配 `-rc` 前缀（`1\.5\.0\+` 要求紧跟 `+`）⇒ 若构建产物仍是 rc 号，
   四条用例会在**第一条断言**就红——这是**期望行为**（发布验收就该拦住 rc 号），不要改用例去迁就。

---

## 9. 用例覆盖点对照（27 条断言 → 任务 a~e）

| 要求 | 覆盖断言（按用例内顺序） |
|---|---|
| a 模拟玩家数据 | 1 条 `log` 命中 `[REL150] ver=<线> gamemode=creative …`；再 8 条字段级 `log`：`gamemode=creative` / `pos=100,y,100` / `hp=20` / `item=astral_dice:abyss_reagent_chip` / `xp=30` / `slotted≥1` / `chips=3` / `modver=1.5.0+`；外加 1 条 `eval` 断言（`from:"state"`，正则整行）＋ `absent` 断言（命令静默失败的四种话术） |
| b 改名验收 | `chip id=astral_dice:abyss_reagent_chip count=3 name=(深渊试剂\|Abyss Reagent)`（中文/英文客户端都认，fabric 客户端语言是 en_us）；`inventory open=1`（创造栏/背包可取到）；截图 `abyss_reagent_tooltip`（f2）＋ `eval` 断言 `from:"chip"` 取显示名 |
| c 截图取证（≥3 张） | `abyss_reagent_tooltip` / `chip_shop_ui` / `release150_guide` 三张，各带 tag；2 条 `vision` 断言（tooltip、商店） |
| d 回归面（3 系统 + jar） | `sys id=astral_dice:dice ok=1`（骰子）/ `attack_card_medium`（卡牌）/ `abyss_reagent_chip`（芯片）/ `jill_sign`（立牌）；商店：`CHIP_SHOP_OPEN player=` + `screen=…ChipShopScreen`；图鉴：`guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=`；jar：`type=jar`（1.21.1/26.1.2：models≥120, signs≥25, entries≥1700；1.20.1 另加 recipes≥100；fabric 另加 recipes≥100, trinkets≥6, embed_lib=true, entries≥1800） |
| e 无崩溃 | `type=crash` |
| 版本门 | `scope=whole` 的版本行断言（见风险 4） |

> 断言阈值取自实测 GA 产物：models 158 / signs 32（四线相同）、recipes 125（1.20.1、fabric；
> 两条 neoforge 线因用单数 `data/astral_dice/recipe/` 计 0）、trinkets 8 与 embed 1 仅 fabric、
> entries 2016 / 2032 / 2452 / 2299。留了安全余量，**不要为了「跑绿」下调**。

---

## 10. 用例自检（改完 JSON 必跑）

```powershell
foreach ($f in '1.21.1','1.20.1','26.1.2','fabric-1.20.1') {
  pwsh -NoProfile -File 'D:\MCProject\astral_dice_multiloader\scripts\test\mt_case.ps1' validate --case "D:\MCProject\astral_dice_multiloader\scripts\test\cases\RELEASE150-$f.json"
}
# 期望四行：MT_VALIDATE: OK — RELEASE150-1.21.1 / …-1.20.1 / …-26.1.2 / …-fabric-1.20.1
```

`validate` 会拦下：未知 op、未知断言 type、未知 scope、jar 断言无判据、`eval` 断言缺 `expect`、
`from` 指向不存在的 `save_as`、`vision` 指向不存在的截图 tag。**它是发布前的第一道门，必须四项全 OK。**
