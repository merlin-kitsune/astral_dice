# scripts/test/linux — Linux 侧最小测试路径（task-4 报告）

> 状态：2026-10-31。作者：DSH teammate `test-harness`（task-4）。
> 本目录只做「能在 Linux 上跑起来的最小路径」；Windows 完整测试台仍在 `scripts/test/*.ps1`，语义以它为准。
> 三个脚本均为 bash，**零 pwsh 依赖**，不写 `run/` 下既有日志。

## 0. 结论（先看这里）

1. **Windows 测试台不能在 Linux 直接运行**：19,384 行 pwsh 代码在 pwsh 7.6.9 下**语法可解析**、参数解析与「禁用自动测试」闸门**可跑通**，但任何触达 Win32 API / Windows 进程模型的路径都失败（证据见 §4）。核心阻塞点是 `lib/Mt.Win32.psm1`（user32 P/Invoke）与 `mt_inject.ps1`（键盘/鼠标注入），其次 `taskkill` / `cmd.exe` / `Get-CimInstance`。
2. **Linux 侧已落地三件套**：
   - `env.sh`：只读环境体检（四线 toolchain JDK + daemon JVM、run 目录/模组、AutoJS 三态探活、客户端残留、Gradle 进程、锁、git autocrlf）。**实测通过**（`ENV_RESULT: WARN`, exit 0）。
   - `launch.sh`：启动 `runClient` 并轮询就绪标志（优先 `run/start-<ver>.sh`，否则 `bash ./gradlew :<line>:runClient --console=plain`）。**只做过 `--dry-run`**，真实启动由 Lead 在 task-1 完成后统一验证（避免与 gradle-launcher 抢全队唯一的 Gradle 并发位）。
   - `stop.sh`：按 `run/<ver>/linux-run.pid` 进程组 + `/proc` 扫描收停，`LINUX_STOP: OK|RESIDUAL`。**两条 kill 路径均以假仓库实测通过**（§9.3）。
3. **不可移植的测试能力**：`mt_case.ps1`（用例编排）/`mt_assert.ps1`（断言）/`mt_report.ps1`/`mt_watchdog.ps1` 都建立在「Windows 进程 + 键盘注入 + 窗口」之上，Linux 侧不复刻；替代路径是 **AutoJS HTTP 通道**（`docs/features/autojs-testing-flow.md` 六步流程），它不需要键鼠注入。详见 §6、§7。

## 1. 文件

| 文件 | 作用 | 实测状态 |
|---|---|---|
| `env.sh` | 只读环境体检，机器行 `CHECK: key=OK\|WARN\|FAIL — detail` + `ENV_RESULT: OK\|WARN\|FAIL` | ✅ 实测（exit 0 / WARN） |
| `launch.sh` | 启动客户端 → 轮询就绪标志 → 可选 AutoJS 探活；日志 `scripts/test/linux/logs/<ver>-<ts>.log`，PID `run/<ver>/linux-run.pid` | ⏳ 仅 `--dry-run`（真实启动留给 Lead） |
| `stop.sh` | 收停客户端（pid 文件进程组 → `/proc` 扫描 → 复核），可选 `--purge-saves` / `--stop-daemon` | ✅ 实测（含假仓库 kill 测试） |
| `logs/` | launch.sh 默认日志目录（`logs/.gitignore` 已忽略内容，不会污染 `git status`） | — |

## 2. 用法

```bash
# 1) 环境体检（只读，随便跑）
scripts/test/linux/env.sh
scripts/test/linux/env.sh --version 1.21.1 --quiet

# 2) 启动（linux 宿主；真实启动由 Lead 统一做，本目录只验过 --dry-run）
scripts/test/linux/launch.sh --version 1.21.1                 # 等 AutoJS 标题画面就绪（LAUNCH: READY）
scripts/test/linux/launch.sh --version 1.21.1 --quickplay testworld --wait-world 120  # 进世界并等 marker
scripts/test/linux/launch.sh --version 26.1.2 --timeout 900 --kill-on-timeout

# 3) 收停
scripts/test/linux/stop.sh --version 1.21.1
scripts/test/linux/stop.sh --all --force
scripts/test/linux/stop.sh --version 1.21.1 --purge-saves   # 删测试世界（saves/* 与 run/<ver>/*/level.dat）
```

线路映射（Gradle daemon JVM 四线统一 **JDK 21**；子项目 toolchain 由 Gradle 自动发现）：

| `--version` | Gradle 子项目 | daemon JVM（统一） | toolchain（自动发现） |
|---|---|---|---|
| `1.21.1` | `:neoforge-1.21.1` | `/usr/lib/jvm/java-21-openjdk-amd64` | 21 |
| `1.20.1` | `:forge-1.20.1` | `/usr/lib/jvm/java-21-openjdk-amd64` | 17 |
| `26.1.2` | `:neoforge-26.1.2` | `/usr/lib/jvm/java-21-openjdk-amd64` | 25 |
| `fabric-1.20.1` | `:fabric-1.20.1` | `/usr/lib/jvm/java-21-openjdk-amd64` | 17 |

## 3. 输出契约

机器行（UTF-8，便于 Lead/脚本 grep）：

- env.sh：`CHECK: <key>=OK|WARN|FAIL — <detail>`、`INFO: <key> — <detail>`、末尾 `ENV_RESULT: OK|WARN|FAIL`
- launch.sh：`LAUNCH: starting|READY|WORLD_READY|OK|FAIL|TIMEOUT|BLOCKED|ERROR|DRY-RUN`（READY=AutoJS 客户端侧就绪；WORLD_READY=日志 marker 附加信息）
- stop.sh：`LINUX_STOP: pid=<pid> …`、`LINUX_STOP: pidfile-removed …`、`PURGE: removed …`、末尾 `LINUX_STOP: OK|RESIDUAL`

退出码（对齐 `lib/Mt.Phase.psm1` 语义）：

| 码 | 含义 |
|---|---|
| 0 | 成功 / OK |
| 1 | 失败（launch 进程提前退出；stop 有残留） |
| 2 | 用法错误；launch 中 AutoJS 端点从未响应 health 且无世界 marker（ERROR 级） |
| 10 | （保留：preflight 级，本三件套未用） |
| 11 | BLOCKED（同线已有客户端 / 缺 JDK / run 目录不存在） |
| 12 | TIMEOUT（AutoJS 客户端侧在 `--timeout` 内未就绪） |
| 130 | 用户 Ctrl-C 中断（游戏为独立进程组，不受影响） |

## 4. 移植判定与证据（pwsh 7.6.9 只读探针）

pwsh 安装：`/home/kitsune/.local/powershell/pwsh`（7.6.9，linux-x64 tar.gz，sha256 `1da6d6d6…a884b`，`ldd` 无缺失）。

| 探针 | 结果 | 原文/关键输出 |
|---|---|---|
| `mt.ps1` AST 解析 | ✅ 0 错误 | `PARSE_ERRORS=0`（851 行全部可解析） |
| `mt.ps1 --help` | ✅ exit 0 | 参数表正常输出 |
| 自动测试闸门 | ✅ 生效 | 三例均 exit 2，stderr 首行 `MT_AUTO_DISABLED: …` |
| `Mt.Win32.psm1` 导入 | ⚠️ 可导入 | `Import-Module` OK、导出函数齐全，但调用即失败 |
| `Get-MtScreenSize` | ❌ | `MethodInvocationException: Exception calling "GetPrimaryScreenSize" … "Value cannot be null. (Parameter 'path1')"` |
| `NativeLibrary.Load("user32.dll")` | ❌ | `Unable to load shared library 'user32.dll' or one of its dependencies. … cannot open shared object file: No such file or directory`（对照 libc 可加载：`LIBC_OK`，故非通用加载器问题） |
| `taskkill` / `cmd.exe` | ❌ 不存在 | 命令查找失败 |
| `Get-CimInstance` | ❌ | `CommandNotFoundException`（`Win32_Process` 查询不可用） |
| `mt_ime.ps1 status` | ❌ exit 1 | 同 `Get-MtTopLevelWindows` 一族的 Win32 失败 |
| `mt_inject.ps1`（无参） | ⚠️ 参数层 OK | `MT_ERROR: 缺少子命令（可选：key cmd mouse）`，exit 2（真正注入路径需 user32，必失败） |
| `Mt.AutoJs.psm1` | ✅ 可导入 | 导出 9 函数，endpoint=`http://127.0.0.1:25585/mcp`（纯 HTTP，Linux 可复用） |

结论：**pwsh 侧代码本身跨平台质量不错**（大量逻辑与 Win32 解耦），阻塞集中在少数 native/进程/IME 模块（§5）。

## 5. Windows-only 依赖清单（移植时要替换的全部点）

| 类别 | 位置 | 用途 | Linux 替代 |
|---|---|---|---|
| Win32 P/Invoke | `lib/Mt.Win32.psm1` L349/L945（内联 C# user32/keyboard/窗口）、导出 20+ 函数 | 窗口查找、DPI、键鼠、窗口置顶 | Wayland computer-use MCP（task-2 已集成）/ xdotool |
| 键鼠注入 | `mt_inject.ps1`（950 行，L349 内联 C#） | `WM_KEYDOWN`/`WM_CHAR`/`WM_*BUTTON` 按美式扫描码直投，绕过焦点/IME | computer-use MCP `act`（keyboard/pointer）；或用 AutoJS `eval_js` 驱动游戏状态（优先） |
| 输入法切换 | `mt_ime.ps1`（427 行，`GetKeyboardLayout`/`ActivateKeyboardLayout`，KLID `00000409`） | 保证注入文本不被中文 IME 截获 | Linux 无对应需求（先确定输入法/布局再用 `fcitx5-remote` 等；当前不做） |
| 进程管理 | `lib/Mt.Proc.psm1` L157/L1052 `taskkill /T /F`、L299 `Get-CimInstance Win32_Process Name='java.exe'`；`mt_case.ps1` L2184、`mt_watchdog.ps1` L128 同类查询 | 进程树强杀 / 按名发现 | `/proc` 扫描（已实现于 stop.sh / env.sh） |
| `cmd.exe` 调 Gradle | `mt_build.ps1` L144、`mt_launch.ps1` L461 `cmd.exe /c gradlew.bat`、`mt_env.ps1` L2396、`mt_cleanup.ps1` L343 | 构建/启动/停守护 | `bash ./gradlew …`（本目录已用） |
| 绝对 Windows 路径 | `lib/Mt.Paths.psm1` L50-52 `D:\.minecraft`、`mt_loadergate.ps1` L159-160（Zulu/Adoptium）、`ref-repro.ps1`（Git/F:）、`fabric/ft_prod.ps1` | 机器本地路径 | env.sh 已检测 `mt.conf` 里 `D:/`、`C:/` 值并 WARN |
| 截图 | `mt_capture.ps1` L70 `Add-Type System.Drawing` + `CopyFromScreen` | 整屏截图（f2 走游戏内 F2） | `grim`（Wayland）/ `gnome-screenshot` / `scrot`；AutoJS 可 `screenshot` 原语（需游戏侧） |
| 压缩库 | `mt_env.ps1` L1372、`mt_loadergate.ps1` L85/L346 `System.IO.Compression` | 解压世界种子/模组 | unzip/7z 或 pwsh 仍可（该模块本身跨平台） |
| MCP 外部二进制 | `mt.conf` `MT_MCP_BIN=C:/Users/xmace/.local/bin/computer-control-mcp.exe` | Windows 会话层 MCP | 已由 task-2 的 Wayland computer-use MCP 取代 |

## 6. 与 Windows 测试台的差异（Linux 最小路径**不做**什么）

- ❌ 用例编排 `mt_case.ps1`（2780 行，封闭原语表 inj/client/assert）：Linux 无键鼠注入与窗口，不复刻。
- ❌ 断言引擎 `mt_assert.ps1`（1372 行，log/absent/crash/kubejs/mixin/vision/eval）与报告 `mt_report.ps1`：Linux 侧不用日志游标断言，改由 AutoJS 直接取状态断言。
- ❌ 世界清场/OP-禁AI/史莱姆压制等 launch 闸门、`Enter-MtLaunchLock` 全机锁：Linux 侧未实现（stop.sh 有并发提示，env.sh 报告锁与 Gradle 进程）。
- ❌ `--phase` 批量编排与「禁用自动测试」闸门：由 Lead/团队命令纪律保证，不写进 bash（避免绕过 Windows 侧用户裁决）。
- ❌ Windows 的「世界已加载」日志判据不能直接挪用：Linux dev 客户端默认停在标题画面、不开世界（详见 §8.5）。
- ✅ 保留：阶段语义（env/launch/stop）、退出码、机器行、日志落点、PID 协议、AutoJS 端点探活。

## 7. 残余 Windows-only 能力与建议（按优先级）

1. **P0 已解决**：Linux 上「起客户端 / 探活 / 收停」闭环 → 本目录三件套。
2. **P1 建议**：把 AutoJS 通道作为 Linux 侧唯一「用例执行臂」——`docs/features/autojs-testing-flow.md` 六步流程 + 本仓 `autojs` skill；不需要键鼠，`stop.sh` 负责收尾。四条线的 `run/<ver>/mods` **均已就位 AutoJS jar**（env.sh 四线报 OK，见 §9.1）；换线/重装后仍建议先跑 env.sh 复核。
3. **P2 建议**：若必须保留「键鼠注入级」用例（GUI 拖拽/按键序列），改走 task-2 的 Wayland computer-use MCP（`act` 的 keyboard/pointer）；不要复活 `mt_inject` 的 user32 代码。
4. **P3 建议**：`mt_ime`/`mt_capture`/`Mt.Win32` 保持 Windows-only，不强行移植；Linux 截图用 `grim` 即可。
5. **不建议**：把 19k 行 pwsh 整体「去 Win32 化」——收益低（真正难替换的只有注入/窗口/IME 三块），且会把 Windows 侧稳定流程置于风险中。

## 8. 已知限制与坑

- **hgfs 共享盘慢**（`stat -f -c %T` = `fuseblk`）：Gradle 构建建议在本地盘做，或在 `--project-cache-dir` 指定本地缓存；`gradlew` 在 hgfs 上通常没有 `+x`（无碍：全部用 `bash ./gradlew`）。
- **Gradle 并发位只有 1 个**：本目录三件套**从不**自动跑 Gradle；launch.sh 真实启动前请确认没有别的 agent 在构建（env.sh 的 `INFO: gradle` 行给出 daemon/wrapper 计数）。
- **PID 文件写在 `run/<ver>/linux-run.pid`**（Lead 裁决）：与 Windows 台 `run/` 产物并存；`run/` 已被 git 忽略，不会污染提交。
- **AutoJS 探活**：`GET <endpoint>/health` 三态（200=ONLINE / 000=DOWN / 其它=HTTP_ERROR）；`GET /mcp` 返回 405 是正常（无服务端 SSE）。
- **fabric-1.20.1 的就绪判据与其他线一致**：主判据是 AutoJS 客户端侧（§8.5），不再依赖「世界已加载」日志 marker。
- **`mt.conf` 里的 `MT_PACK_MODS_*` / `MT_MCP_BIN` 是 Windows 机器历史值**：env.sh 检测到 `D:/`、`C:/` 会 WARN，不影响 Linux 运行。

## 8.5 Linux 就绪判据与 Windows 的差异（2026-10-31 1.21.1 实机裁决）

- **Windows 测试台**：`mt_launch.ps1` 轮询 `Total time to load game and open world was`（26.1.2 另认 `logged in with entity id` + `Loaded <N> advancements`）。其前提是测试台总用 quickplay 直接进单机世界，日志一定会出现世界加载完成行。
- **Linux 实测（gradle-launcher 起的 `run/start-1.21.1.sh`，Lead 复核）**：dev 客户端默认停在**标题画面**、不会自动开世界，所以上面两行日志**根本不会出现**；而 AutoJS 在 ~97s 后就绪：
  - `GET http://127.0.0.1:25585/health` = `ok`（HTTP 200）
  - `status` 报 client `registered=true available=true state="client running, no world loaded"`
- **因此 `launch.sh` 改以 AutoJS 为准**：
  - 主判据：`health`==200 **且** MCP `initialize` + `tools/call status` 报 `sides[]` 中 `side=="client"` 且 `available=true` → `LAUNCH: READY`，exit 0。
  - 日志 marker 只作附加信息：命中时额外打 `LAUNCH: WORLD_READY`，**不作为 READY 必要条件**；`--wait-world <秒>`（默认 0）可在 READY 后额外等它。
  - `MT_ALLOW_NO_AUTOJS=1`：允许 AutoJS 未就绪时用日志 marker 兜底判 READY；`run/<ver>/mods` 无 AutoJS jar 的线也自动允许 marker 兜底。两套都没有 → AutoJS 通道从未响应 health 则 exit 2，否则 exit 12。
- **需要世界的用例**：用 `--quickplay <世界名>`（build.gradle 四线已实现 `-Pquickplay`：1.21.1/1.20.1/fabric 映射 `--quickPlaySingleplayer <名>`，26.1.2 必须等号形式 `--quickPlaySingleplayer=<名>`；launch.sh 经 `run/start-*.sh` 的 `"$@"` 透传或直接附给 gradlew，**本脚本传参未实机验证**），或先在标题画面用 AutoJS `eval_js` 建/开世界（见 `docs/features/autojs-testing-flow.md`）。

## 9. 实测记录（2026-10-31）

### 9.1 env.sh（只读，全四线；2026-10-31 修假 WARN 后重跑）

```
CHECK: repo=OK / mount=WARN(fuse.vmhgfs-fuse, source=vmhgfs-fuse) / gradlew=OK
CHECK: jdk_1.21.1=OK (21.0.12.1) / jdk_1.20.1=OK (17.0.20.1) / jdk_26.1.2=OK (25.0.4.1) / jdk_fabric-1.20.1=OK (17.0.20.1)
CHECK: run_dir_*=OK（run/1.21.1, run/1.20.1, run/26.1.2, run/fabric-1.20.1 均存在）
CHECK: autojs_jar_1.21.1=OK（AutoJS-neoforge-1.21.1-1.0.0.jar）
CHECK: autojs_jar_1.20.1=OK（AutoJS-forge-1.20.1-1.0.0-dev.jar）
CHECK: autojs_jar_26.1.2=OK（AutoJS-neoforge-26.1-1.0.0.jar）
CHECK: autojs_jar_fabric-1.20.1=OK（AutoJS-fabric-1.20.1-1.0.0.jar）
CHECK: autojs_health=WARN（GET http://127.0.0.1:25585/health = 000 未运行）
CHECK: residue=OK（/proc 扫描无客户端）/ conf_windows=WARN（mt.conf 含 D:/、C:/ 历史值）
CHECK: cwd=OK / jdk_paths=OK / git_autocrlf=OK / locks_other=OK（仅 dsh.lock）
ENV_RESULT: WARN — 0 项阻塞，3 项提示
```

### 9.2 launch.sh `--dry-run`（四线均验证；daemon JVM 统一 21，toolchain 按线）

`1.20.1`（forge，start.sh 已由 gradle-launcher 产出 → 直接调用）：
```
LAUNCH: DRY-RUN — 未启动任何进程
  version      = 1.20.1
  project      = :forge-1.20.1
  daemon_jvm   = /usr/lib/jvm/java-21-openjdk-amd64（Gradle daemon JVM，需 ≥21；四线统一）
  toolchain    = 17（Gradle 自动发现，本机需在位）
  start_source = run/start-1.20.1.sh（可执行，直接调用）
  log          = scripts/test/linux/logs/1.20.1-<ts>.log
  pid_file     = run/1.20.1/linux-run.pid
  timeout      = 600s（等 AutoJS 客户端侧 READY；旧日志 marker 不作必要条件）
  wait_world   = 0s（READY 后额外等世界 marker，0=不等）
  autojs_jar   = 1 个  AutoJS-forge-1.20.1-1.0.0-dev.jar
  ready_by     = AutoJS health=200 + status(client available=true)；marker 仅辅助
```

`26.1.2`（neo26，toolchain=25）：
```
  project      = :neoforge-26.1.2
  daemon_jvm   = /usr/lib/jvm/java-21-openjdk-amd64
  toolchain    = 25
  start_source = run/start-26.1.2.sh（可执行，直接调用）
```

`fabric-1.20.1`（尚无 run/start-fabric-1.20.1.sh → 回退 gradlew，由本脚本注入 daemon JVM=21）：
```
  project      = :fabric-1.20.1
  daemon_jvm   = /usr/lib/jvm/java-21-openjdk-amd64
  toolchain    = 17
  start_source = bash ./gradlew :fabric-1.20.1:runClient --console=plain
```

### 9.3 stop.sh 假仓库 kill 测试（`MT_STOP_REPO_ROOT` 自测钩子）

```
# (a) pid 文件路径：setsid sleep 300 → run/26.1.2/linux-run.pid
LINUX_STOP: pid=45219 ver=26.1.2 — SIGTERM（进程组）
LINUX_STOP: pid=45219 已退出
LINUX_STOP: pidfile-removed /tmp/fake-repo/run/26.1.2/linux-run.pid
LINUX_STOP: OK                                   EXIT=0   # 进程已消失

# (b) /proc 扫描路径：真 java（cwd=run/1.21.1，cmdline 含 -Dfml…），无 pid 文件
LINUX_STOP: pid=48337 ver=1.21.1 — SIGTERM（/proc 扫描命中，cwd=/tmp/fake-repo/run/1.21.1）
LINUX_STOP: pid=48337 已退出
LINUX_STOP: OK                                   EXIT=0   # 进程已消失
```

### 9.5 复核修订（2026-10-31，Lead 复核后）

- **假 WARN ①（AutoJS jar 误报缺失）**：原用 `ls autojs*.jar` 大小写敏感匹配，而实际产物是 `AutoJS-<loader>-<ver>-<v>.jar`。改为大小写不敏感（`*[Aa][Uu][Tt][Oo][Jj][Ss]*.jar`），且只认以 `.jar` 结尾的文件（排除 `.jar.bak-*` / `.jar.reobf-*` 备份）。四线 run/mods 实测均有 jar（见 §9.1），不再误报。
- **假 WARN ②（hgfs 挂载类型误报 fuseblk）**：`stat -f -c %T` 在 hgfs 上返回 `fuseblk`；改用 `findmnt -n -o FSTYPE,SOURCE -T <repo>` → 实测 `fuse.vmhgfs-fuse` / source=`vmhgfs-fuse`，与 `mount` 输出一致。
- 修后重跑 `ENV_RESULT: WARN — 0 项阻塞，3 项提示`；剩余三条均为真实提示：hgfs 共享盘（I/O 慢）、AutoJS 端点未运行（游戏没起）、`mt.conf` 的 Windows 历史路径。
- 附带修复（Lead 批准后）：`launch.sh` 的 AutoJS 探活门槛同样改为大小写不敏感匹配（只认 `.jar` 结尾，排除 `.jar.bak-*`/`.jar.reobf-*`），并在 `AUTOJS: OK` 行输出命中的 jar 名；`stop.sh` 无 `autojs*.jar` 匹配（只按 pid 文件 / `/proc` cmdline 收停），无需改。

### 9.4 真实仓库空转

```
$ scripts/test/linux/stop.sh --all
LINUX_STOP: OK — 未发现本 repo 的 Minecraft 客户端（无需收停）    EXIT=0
```

### 9.6 Gradle daemon JVM 口径修正（2026-10-31，task-1 实跑后）

**背景证据**（starengine，01:46，非本机复跑）：`JAVA_HOME=java-17 ./gradlew :fabric-1.20.1:publishToMavenLocal` 在配置期失败：

```
Could not resolve net.fabricmc:fabric-loom:1.14.10
Dependency requires at least JVM runtime version 21. This build uses a Java 17 JVM.
```

且本仓 `settings.gradle` 四线全 include，任何一条线的 Gradle 命令都会走到 `Configure project :fabric-1.20.1`，因此 **daemon JVM 必须 ≥21，不能按线切 17**；子项目 toolchain（17/21/25）由 Gradle 自动发现。

修正：
- `launch.sh`：删掉按线 `JAVA_HOME` 表，统一 `DAEMON_JDK=/usr/lib/jvm/java-21-openjdk-amd64`（可用 `MT_GRADLE_DAEMON_JAVA_HOME` 覆盖）；`--dry-run` 输出 `daemon_jvm` + `toolchain` 两行；回退 gradlew 路径注入该 21。`run/start-<ver>.sh` 存在时会被其内部 `JAVA_HOME` 覆盖（以其为准；`start-1.20.1.sh` 内部 `DEFAULT_JDK` 由 gradle-launcher 一并改为 21）。
- `env.sh`：四线 `jdk_*` 判据改为「本线 toolchain=X 且本机 X 在位」；新增 `gradle_daemon_jvm=OK/FAIL` 检查（21 在位，四线统一）。
- 验证：`bash -n` 三脚本 OK；`launch.sh --version 1.20.1 --dry-run` / `26.1.2 --dry-run` 均 EXIT=0 且显示 `daemon_jvm=…21…`、`toolchain=17/25`；`env.sh` EXIT=0 → `CHECK: gradle_daemon_jvm=OK`、`ENV_RESULT: WARN（0 阻塞，3 提示）`不变。

### 9.7 Linux 就绪判据修正（2026-10-31，Lead 1.21.1 实机后）

- 旧判据（世界 marker）在 Linux 会**误超时**：dev 客户端停在标题画面，`Total time to load game and open world was` 永不出现，即使游戏已起来也会等到 exit 12。
- 实测读数（Lead 复核）：~97s 后 `GET /health` = `ok` HTTP 200；`status` = client `registered=true available=true state="client running, no world loaded"`。
- 修正后 `launch.sh`：READY=AutoJS 客户端侧 `available=true`；输出 `LAUNCH: READY` + `health`/`status` 原文 + 游戏 PID；`LAUNCH: WORLD_READY` 降级为附加信息；新增 `--wait-world`；`--quickplay` 透传（配置已确认、传参未实机验证）。
- 自检：`bash -n` 三脚本 OK；`--version 1.21.1 --dry-run` EXIT=0（daemon_jvm/toolchain/cmd/log/pid 字段齐全 + 新增 wait_world/autojs_jar/ready_by）。
