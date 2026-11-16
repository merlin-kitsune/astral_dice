# fabric-1.20.1 发布验收 · bash 编排层用法（静态准备版，2026-11-02）

> 事实源：`scripts/test/win/` 下的 `README.md` / `mt.sh` / `env.sh` / `launch.sh` / `stop.sh` / `live.sh` / `lib/common.sh` / `lib/fabric.sh` / `lib/phases.sh`，
> 以及 `scripts/test/mt_case.ps1`。本文的命令均以仓库根 `F:/MCProject/astral_dice_multiloader`（git bash 视角 `/f/...`）为 cwd。
> 本文为**静态核实**（读脚本 + dry-run + 只读探测）；除 dry-run 外未启动任何游戏、未跑 Gradle。

---

## 0. 先记住三条结构事实

1. **fabric 线不走 `scripts/test/mt.ps1` 全流程**：`mt.ps1:97-104` 会把 `fabric-1.20.1` 直接短路；fabric 只走 bash 层 `scripts/test/win/mt.sh`。`scripts/test/lib/Mt.Paths.psm1:41` 仍认 `fabric-1.20.1`，所以 **pwsh 单文件执行器**（`mt_case.ps1` 等）对 fabric 可用。
2. **fabric 是双 run 目录**：`fabric-1.20.1/run/client` 与 `fabric-1.20.1/run/server`（**不是** `run/fabric-1.20.1`）。toolchain 17，gradle 任务 `:fabric-1.20.1:runClient` / `:fabric-1.20.1:runServer`。
3. **用例执行器有两套**，能力不等价：bash 内置（`lib/fabric.sh`，只做 RCON + 离线 jar/日志断言）与 pwsh `mt_case.ps1`（支持 `channel=autojs` 的 `eval_js` / `screenshot` / `vision`）。选错执行器 = 用例永远 BLOCKED。

---

## 1. 阶段、退出码、机器行

阶段：`env | build | launch | cases | report | stop`（另 `preflight`）。fabric 上 preflight / launch / cases / report 是 **bash 原生零 pwsh**（`lib/phases.sh`）。

退出码（env/launch/stop 一致）：`0` OK / `1` FAIL·RESIDUAL / `2` 用法错误·ERROR / `11` BLOCKED / `12` TIMEOUT / `130` Ctrl-C（**不会**连带停游戏）。

机器行（可直接 grep）：
`CHECK: <key>=OK|WARN|FAIL — detail`、`INFO: <key> — detail`、`ENV_RESULT: OK|WARN|FAIL`、
`LAUNCH: READY|READY-by-marker|WORLD_READY|BLOCKED|TIMEOUT|ERROR|FAIL|DRY-RUN`、`WIN_STOP:`、`PURGE:`、
`MT_PHASE:`、`MT_CASE_RCON:`、`MT_VALIDATE:`、`MT_DEPRECATED:`。

**批量闸门**（`mt.sh:174-202`，先于阶段派发）：这些组合在没有 `--allow-auto` / `MT_ALLOW_AUTO=1` 时一律 `exit 2`：
无 `--phase` 的全流程、`--phase cases` 不带 `--case`、`--phase report`、任何**不带 `--version`** 的单阶段。
放行：`build` / `env` / `launch` / `stop`。

---

## 2. 一次完整 fabric 发布验收的确切命令序列

> 用例 `scripts/test/cases/RELEASE150-fabric-1.20.1.json` 头部声明 `"channel": "autojs"`、`"side": "client"`、`"on_fail": "keep_game_running"`。
> 因此"完整验收"= **bash 负责 env/launch/stop，pwsh 负责跑用例**。两者混用是设计使然，不是妥协。

### Step 0 · 环境自检（只读，可随时跑）

```bash
cd /f/MCProject/astral_dice_multiloader
bash scripts/test/win/env.sh --version fabric-1.20.1
```

- 参数：`--version` 四线之一（`1.21.1|1.20.1|26.1.2|fabric-1.20.1`）。
- fabric 分支额外输出：`INFO: run_layout_fabric-1.20.1`（双 run 目录）、`mods_*`、`autojs_jar_*`、`CHECK: fab_rcon`、`INFO: fab_channel`。
- 期望 `ENV_RESULT: OK` 或 `WARN` 且 **0 项阻塞**，`exit 0`。
- 实测（2026-11-02 03:14，fabric）：`ENV_RESULT: WARN — 0 项阻塞，4 项提示（20 项通过）`；其中 `CHECK: fab_rcon=OK`、`INFO: fab_channel — autojs=off`、`CHECK: residue=WARN`（见坑 5）。

### Step 1 · 收停同线残留（否则 Step 3 必 BLOCKED）

```bash
bash scripts/test/win/stop.sh --version fabric-1.20.1 --dry-run    # 先看会杀谁
bash scripts/test/win/stop.sh --version fabric-1.20.1
```

- `--dry-run` 只打印匹配到的命令行与 PID；真跑则 CIM 逐行匹配 → `taskkill //PID x //T //F` → **再用 CIM 复核残留**（taskkill 返回码不可信）。
- 期望 `WIN_STOP: OK` / `exit 0`；有残留 `WIN_STOP: RESIDUAL` / `exit 1`。
- 停的服务端会写 world 并落 `level.dat`；**不要**顺手加 `--purge-saves`（见坑 7）。

### Step 2 · fabric 预检（bash 原生）

```bash
bash scripts/test/win/mt.sh --version fabric-1.20.1 --phase preflight
```

- 检查：子项目目录、双 run 目录、`gradlew.bat`、python、打印 `MT_INFO fabric_autojs`、探 RCON（服务端没起时 WARN 属预期）。
- 有硬问题 `exit 11`。预算 120 s。

### Step 3 · 启动客户端（fabric 用例声明 side=client）

```bash
# 编排器入口（推荐）
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side client --enable-autojs --phase launch

# 等价：直接调单阶段脚本
bash scripts/test/win/launch.sh --version fabric-1.20.1 --side client --enable-autojs
```

实际下发（`launch.sh --dry-run` 实测原文）：

```text
cmd = cmd //c "gradlew.bat :fabric-1.20.1:runClient --console=plain -Pquickplay=testworld -PautojsTestMod=1"
      （cwd=仓库根，JAVA_HOME=C:\\Program Files\\Zulu\\zulu-21）
log = scripts/test/win/logs/fabric-1.20.1-<ts>.log
pid_file = fabric-1.20.1/run/client/win-run.pid
```

关键参数：

| 参数 | 含义 | 默认 |
|---|---|---|
| `--version` | 四线之一；fabric 必须显式给 | 无（必填） |
| `--side client\|server` | **仅 fabric**；决定 run 目录、gradle 任务、日志、pidfile | `client`（`MT_FAB_DEFAULT_SIDE`） |
| `--enable-autojs` / `--disable-autojs` | **仅 fabric**；追加/不追加 `-PautojsTestMod=1` | 不加（AutoJS=off） |
| `--quickplay <name>` | 传 `-Pquickplay=<name>` ⇒ `--quickPlaySingleplayer` | `testworld` |
| `--no-quickplay` | 不加 quickplay（停在标题画面） | — |
| `--timeout <s>` | 就绪等待上限 | `600`（`MT_LAUNCH_READY_TIMEOUT_SEC`） |
| `--wait-world <s>` | READY 后额外等世界 marker | `0`（`MT_LAUNCH_WAIT_WORLD_SEC`） |
| `--kill-on-timeout` | TIMEOUT 时杀进程树 | 关（默认不杀） |
| `--launch-mode bg\|start` | bg = `cmd //c … &`；start = 独立窗口 | `bg`（`start` 本机不可用） |
| `--log-dir` | gradle 日志目录 | `scripts/test/win/logs` |
| `--dry-run` | 只打印命令与 blockers，不起进程 | — |

就绪判据（**读的是 gradle 日志，不是 `run/<side>/logs/latest.log`**）：
`server` ⇒ `Done ([0-9.]+s)! For help`；`client` ⇒ `Sound engine started|finished`。
结果行：`LAUNCH: READY`（AutoJS 端点 + `tools/call status` 通过）/ `LAUNCH: READY-by-marker`（marker 兜底）/ `WORLD_READY`（`--wait-world` 命中）。
失败：`ERROR exit 2`（健康检查从未响应且 marker 未命中）、`TIMEOUT exit 12`（默认**不杀进程**）、`FAIL exit 1`（gradle 日志出现 `BUILD FAILED` / `FAILURE: Build failed`，打尾部 40 行）。
**脚本退出后游戏继续跑**，靠 pidfile + CIM 收停。

### Step 4 · 注入（RCON 通道 / 用例内步骤）

正式用例把注入写在 steps 里（`inject_command`，11 条 `/gamemode`、`/clear`、`/tp`、`/xp`、`/effect`、`/give`）。手工冒烟可用纯 RCON：

```powershell
# 仓库自带的只读探针（python 3 纯 socket，和 lib/fabric.sh 的 mt_rcon_exec 同协议）
python work/_rcon_probe.py 25575 astralft "list"
# 输出示例：AUTH rid=1 / CMD 'list' -> 'There are 0 of a max of 10 players online: '
```

通道配置来源（**实测值**，`fabric-1.20.1/run/server/server.properties`）：
`enable-rcon=true`、`rcon.port=25575`、`rcon.password=astralft`、`server-port=25565`、`level-name=world`。
`lib/fabric.sh` 里另有常量兜底：`MT_FAB_RCON_PORT=25575`、`MT_FAB_RCON_PASSWORD=astralft`。
bash 层 RCON 依赖 **python3**（缺 python ⇒ `MT_ERROR`，`return 2`）。

### Step 5 · 跑用例（二选一，按用例 channel 决定）

**5A 正式 27 条（`channel=autojs`）⇒ 必须用 pwsh 执行器**

```powershell
# 前置：客户端已按 Step 3 起来，且 AutoJS 端点健康（先自己探一次，见坑 1）
curl.exe -s http://127.0.0.1:25585/health
"C:/Program Files/PowerShell/7/pwsh" -NoProfile -File scripts/test/mt_case.ps1 run `
  --version fabric-1.20.1 `
  --case scripts/test/cases/RELEASE150-fabric-1.20.1.json
```

- side 取自**用例的 `side` 字段**（`client`），fabric 下解析成 `fabric-1.20.1/run/client`。
- `channel` 取自用例 `channel`（可用 `--channel` 覆盖）；端点 URL 取 `mt.conf` 的 `MT_AUTOJS_ENDPOINT`（`http://127.0.0.1:25585/mcp`）。
- 端点不可用时该步/该断言为 **BLOCKED（11）**，不是 FAIL（`mt_case.ps1:507`、`:1008`）。
- `--case-timeout <s>` 默认 `600`；结果落 `scripts/test/win/logs/` 与 `.mt_snapshot.json` 同级结构化记录。

**5B RCON 子集（bash 内置执行器；跑不了本用例）**

```bash
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase cases \
  --case scripts/test/cases/RELEASE150-fabric-1.20.1.json
```

- `--side` 决定读 `fabric-1.20.1/run/<side>/logs/latest.log`（**不看用例的 side 字段**）。
- `eval_js` / `await_state` / `mcp_status` / `screenshot` / `kubejs_reload` 与断言 `eval` / `vision` ⇒ 逐条 **BLOCKED**，整体 `exit 11`。
- 结果：`scripts/test/win/logs/fabric/cases-fabric-1.20.1.jsonl`（每次运行覆盖）+ `summary-fabric-1.20.1.md`。

### Step 6 · 停机

```bash
bash scripts/test/win/stop.sh --version fabric-1.20.1
```

- 顺序：CIM 匹配 kill 进程树 → 读 pidfile → 读 `scripts/test/fabric/.ft_launch_state.json` → 再复核残留。
- `--grace <s>`（默认 10）、`--stop-daemon`、`--purge-saves`（清世界，见坑 7）、`--dry-run`、`--all`。

---

## 3. 已知坑（逐条带证据）

1. **AutoJS 就绪判定与实际加载方式错位（最关键）**：`launch.sh:191-210` 判 AutoJS 只看 `run/<side>/mods` 里有没有 `autojs*.jar`；而 AutoJS 在 fabric 上是 `modRuntimeOnly`（`fabric-1.20.1/build.gradle:199-201`），**走 classpath 不进 mods/**。
   实测 `launch.sh --enable-autojs --dry-run`：`autojs_jar = 0 个`、`autojs_deployed=0 / probe_enabled=0`、`ready_by = 纯 marker`。
   ⇒ 加了 `--enable-autojs` 之后**仍然不会探 25585**，`READY-by-marker` 不代表 AutoJS 可用。**必须自己 `curl /health` 再开跑用例**。
2. **就绪 marker 读 gradle 日志**（`$LOG_DIR/<version>-<ts>.log`），不是 `run/<side>/logs/latest.log`；用后者判断启动会永远等不到。
3. **TIMEOUT 不自动停**（`launch.sh` 只在 `--kill-on-timeout` 时杀），残留进程会让下一次 launch 卡在 `same_line_running`。
4. **`same_line_running` 实测**：`launch.sh --side server --dry-run` 当前输出 `blockers = same_line_running（真启动会 exit 11 BLOCKED）`（因 fabric 服务端在跑，PID 29924/37252）。
5. **跨线命令行匹配缺陷**：`lib/common.sh:263-269` 的 fabric 兜底分支 `*"$MT_FAB_SUBPROJECT"*run*client|server*` **不判版本**，因此任何版本都会命中 fabric 进程。
   实测 `mt_rows_for_version`：`1.21.1 / 1.20.1 / 26.1.2 / fabric-1.20.1` **四条线全部**命中 PID 29924、37252；`env.sh` 因此把 fabric 残留报成 `1.21.1 线` 并建议 `stop.sh --version 1.21.1`——**照做会杀掉 fabric 服务端**。跨线作业时必须按 PID 手工核对。
6. **`latest.log` 不轮转**：bash 执行器整文件读日志（无行号窗口），历史行会造成**假绿**（`log` 断言）或**假红**（`absent` / `crash` 断言）。`live.sh` 会先轮转 `latest.log → latest.log.live-<ts>`；直接 `mt.sh --phase cases` **不会**。
7. **`--purge-saves` 会删掉刚复制好的世界**：白名单含 `<run dir>/saves/testworld`，加了这个开关，Step 3 的 `-Pquickplay=testworld` 下次就没有世界可进。除非要重开世界，别加。
8. **端口账**：`server-port=25565`、`rcon.port=25575`（都在 `run/server/server.properties`）、AutoJS `25585`（`scripts/test/mt.conf` 的 `MT_AUTOJS_ENDPOINT`）。25565 被占 ⇒ fabric 服务端 `FAILED TO BIND TO PORT`；25575 被占 ⇒ RCON 起不来，注入全废。
9. **`--launch-mode start` 在本机不可用**（会阻塞）；固定用默认 `bg`。
10. **`--side` / `--enable-autojs` 只对 fabric 合法**，其它线传了 `exit 2`。
11. **`mt.sh --phase launch --dry-run` 只打印委托命令**（"`DRY-RUN: cd … bash launch.sh …`"）；想看 gradle 命令行 / blockers 必须**直接** `launch.sh --dry-run`。
12. **fabric 不进 `mt.ps1`**：任何 `docs` 里"四线一条命令"的旧说法都不适用于 fabric，fabric 必须 bash + `--side`。

---

## 4. 两套执行器能力对照（fabric）

| 用例元素 | bash（`lib/fabric.sh`） | pwsh（`mt_case.ps1`） |
|---|---|---|
| `note` / `wait` | OK | OK |
| `inject_command` / `rcon` | 仅 `--side server`（走 25575） | OK（channel=autojs 时走 `AutoJS.command`） |
| `jar_stats` | OK（build/libs 最新 jar，排除 `-(sources\|dev\|slim)`） | OK（同契约） |
| `eval_js` / `await_state` / `mcp_status` | **BLOCKED** | OK（需 25585 端点） |
| `screenshot` / `kubejs_reload` | **BLOCKED**（`kubejs_reload` 已退役） | OK |
| 断言 `log/absent/crash/kubejs/mixin/state/jar/bytecode` | OK | OK |
| 断言 `eval` / `vision` | **BLOCKED** | OK（vision 只出题，判定交视觉模型） |

---

## 5. 世界来源（`-Pquickplay` 到底读哪里）

- 客户端：`--quickPlaySingleplayer <name>` 读 `fabric-1.20.1/run/client/saves/<name>`；launch 默认 `-Pquickplay=testworld` ⇒ 需要 `fabric-1.20.1/run/client/saves/testworld`。
- 服务端：`runServer` **忽略** `-Pquickplay`，用 `server.properties` 的 `level-name=world` ⇒ `fabric-1.20.1/run/server/world`。
- `--purge-saves` 白名单：`<run dir>/saves/testworld`、`<run dir>/testworld`、`<run dir>/client_world/testworld`、`<run dir>/server_world/testworld`，以及 `saves/` 下其它世界目录与任何含 `level.dat` 的兄弟目录（`PROTECTED` 名单除外）。
- LT 实机批次另有 `live.sh`（`--batch LT0..LT7|all`）：先 `mt.sh --phase preflight`，拒绝在实例已运行时开跑（除非 `--allow-stale-log`），再轮转双端日志、等 RCON（`MT_LIVE_RCON_WAIT_SEC=120`）、等玩家（`MT_LIVE_PLAYER_WAIT_SEC=120`，`/list` 解析 `There are N of a max`），最后按用例逐条调 `mt.sh --phase cases`。
