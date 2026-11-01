# Windows 测试台（git bash / MSYS2）— env / launch / stop

本目录是 **`scripts/test/linux/` 的 Windows 对应物**：同一台机器上、同一份契约
（`docs/features/windows-channel-decoupling-spec.md` §3）的 bash 侧实现。

一句话边界：**这里是 Windows 测试台的 bash 侧实现**——

- `mt.sh`（+ `lib/phases.sh`）= **默认编排入口**，与 `scripts/test/mt.ps1` 同一套开关 / 阶段 / 退出码 / 机器行；
- `env.sh` / `launch.sh` / `stop.sh` = 三个可单跑的阶段脚本（体检 / 拉起 / 收停）；
- 用例与断言的**执行器**仍在 pwsh（`mt_case.ps1` / `mt_report.ps1` 等），过渡期由 `mt.sh` 委托并打 `MT_DEPRECATED:`，迁移路线见 `docs/features/bash-first-test-flow-plan.md`。

```
scripts/test/win/
├── mt.sh            # 默认编排入口：--version / --phase / --case / --channel / --allow-auto / --dry-run
├── lib/phases.sh    # 阶段实现（env/build/launch/cases/report/stop/preflight）+ 过渡期 pwsh 委托
├── lib/common.sh    # 版本表 / 机器行 / CIM 判线 / taskkill / AutoJS 探测 / 路径与 JDK 发现
├── env.sh           # 只读体检：逐项 CHECK: → 收尾 ENV_RESULT:，退出码 0/1/2
├── launch.sh        # --version <v>：cmd //c gradlew.bat :<子项目>:runClient → 轮询就绪 → LAUNCH: READY
├── stop.sh          # [--version|--all]：CIM 判线 + taskkill //T //F，可选清存档/停 daemon
├── logs/            # launch 的运行日志落这里（<version>-<ts>.log），日志文件不入库
└── README.md        # 本文件
```

> 写域纪律：本目录**只读**复用 `scripts/test/mt.conf` 与 `run/<v>/**` 的现状，
> 不改 `scripts/test/linux/**`、不改任何 `.ps1` 的内容（只调用）、不改 `mt.conf`。

---

## 1. 底线事实（先读这段，写脚本前必须知道）

| 事实 | 后果 |
|---|---|
| git bash 的 `$TEMP` 就是 `/tmp`，而 `/tmp` 是 `C:\Users\<user>\AppData\Local\Temp` 的 MSYS 挂载点（`mount` 里带 `usertemp`） | 「不用 /tmp」= 「不用 Windows 默认 TEMP」。本机该目录 ACL/沙箱拒写，所以 **AutoJS 探测全程不落临时文件**（`curl -D -` + 管道解析） |
| `cmd //c start "" /B cmd /c "…"` 在本机**阻塞**（6s 超时仍未返回） | `launch.sh` 默认 `--launch-mode bg`（bash 后台 + `cmd //c gradlew.bat`，可拿 PID）；`start` 只是保留的备选 |
| `taskkill //PID …` 的**退出码不可靠**（找不到进程也常返回 0，且输出是 GBK 乱码） | 收停**一律用 CIM 复核**判定残留，不采信 taskkill 的 rc |
| 全仓只有一个 Gradle 并发位，daemon JVM **必须 ≥ 21** | `launch.sh` 找不到 ≥21 的 JDK 就 `LAUNCH: BLOCKED`(11)，绝不用 17 硬起 |
| `run/1.20.1/mods`、`run/fabric-1.20.1/mods` 里实际躺着 `AutoJS-*.jar` | 2026-11-01 用户裁决：AutoJS 就绪判据**放宽至所有版本**，按「本线 `run/<版本>/mods` 里 `AutoJS-*.jar` 是否在位」判定（四线实测都在位 ⇒ 四线全部纳入 AutoJS 主判据；无 jar 的线自动退回日志 marker） |
| bash 在本机会话里对 `scripts/test/**`、`run/**` 的写入可能被 ACL/沙箱拒 | `env.sh` 的 `write_targets` 会 WARN；`launch.sh` 建不了日志目录就直接 `ERROR`(2) 明说，不静默降级 |

---

## 2. Linux 机制 → Windows 对应物

| Linux 蓝本用的是 | Windows 侧对应物 | 说明 / 降级 |
|---|---|---|
| `/proc/<pid>/cmdline`、`/proc/<pid>/cwd` | `Get-CimInstance Win32_Process` 取 `ProcessId` + `CommandLine`（`pwsh -NoProfile`） | 没有 cwd 字段，判线只能靠命令行；标记串与 `Mt.Paths.psm1:141-156` 同款：`:<子项目>:`、`run\<version>`、`run/<version>`（**不用**裸子项目名，那同时是仓库目录名，会误命中 IDE） |
| `pgrep -f` / `ps` 过滤 | 同一份 CIM 输出在 bash 里 `while IFS=$'\t' read -r pid cmd` 过滤 | CIM 查询失败（pwsh 缺/无权限）⇒ `env.sh` FAIL、`launch.sh`/`stop.sh` BLOCKED/ERROR(2)，绝不「当作没有进程」继续 |
| `kill -SIG -- -<pgid>`（进程组） | `taskkill //PID <pid> //T //F` | Windows 没有进程组，`/T` 才是「整棵树」的等价物；`//` 是 MSYS 对 `/` 的转义 |
| `setsid` + `nohup` | bash 后台 `>log 2>&1 </dev/null &` + `cmd //c`；`start "" /B` 作备选 | 本机 `start /B` 实测阻塞 ⇒ 默认走 bg；后台子进程必须重定向三路 FD，否则调用方挂起 |
| pidfile 里的 shell PID | pidfile 记 **Windows PID**：`cat /proc/$!/winpid` | `runClient` 的实际进程是 `java.exe`，`win-run.pid` 只是「本脚本拉起的 wrapper」的锚点，权威判线仍是 CIM |
| `findmnt -T .` | `df -T .`（Type 列 `ntfs`） | 判据从「挂载源/挂载点」降级为「文件系统类型」；`ntfs` 就代表大小写不敏感 + CRLF 风险 |
| `ss -lntp` | `netstat -ano` 过滤端口 + `curl` 直连探测 | 没有「按进程过滤」的等价物；需要 PID 时再回 CIM 交叉核对 |
| `/usr/lib/jvm/*` 遍历 | `JAVA_HOME` → `C:/Program Files/Zulu/zulu-<n>` → `C:/Program Files/Java/jdk-<n>*` → `Eclipse Adoptium` → `Amazon Corretto` → `~/.gradle/jdks/*<n>*` → `~/.jdks/*<n>*` | 逐条跑 `java -version` 校验主版本（**精确匹配**才算 OK）；Gradle 自动下载的 JDK 也在这条链里被找到 |
| `comm=java` 判残留 | CIM 的 `Name='java.exe'` | 只认 `java.exe`；Gradle daemon 用命令里的 `GradleDaemon` 再分一层 |
| `bash ./gradlew --stop` | `cmd //c "gradlew.bat --stop"` | 需要 ≥21 的 daemon JVM（`stop.sh --stop-daemon` 会自动带上） |
| `trap INT/TERM → 130` | 同款 | 但**不收停游戏**：中断只退出本脚本，游戏进程继续跑（会打印收停命令） |

### 不可移植 / 已放弃的项

1. **进程组语义**：`kill -- -pgid` 没有对应物。`taskkill /T` 收的是「进程树」，
   不是「同组的游离进程」——若某个 java 被 `start` 双重 detach 掉出树，只能靠 CIM 判线逐条点杀。
2. **cwd 判据**：Linux 用 `/proc/<pid>/cwd` 精确判「这个 java 是不是在 `run/<v>/` 下」；
   Windows 拿不到别的进程 cwd ⇒ 只能靠命令行标记串，理论上比 cwd 弱。
3. **信号语义**：`SIGTERM` 宽限期不存在。`taskkill` 不带 `/F` 发的是 `WM_CLOSE` 类请求，
   对无窗口的 java 基本无效，所以本脚本几乎总是 `/F`（强杀）。存档写入中的强杀有小概率留残档。
4. **`setsid`**：没有。`start /B` 本机又阻塞 ⇒ 只能「bash 后台 + 三路重定向」，父 shell 退出后子进程存活（已实测）。
5. **`/tmp` 可写**：这条在本机是「连 Windows 默认 TEMP 都不可写」⇒ AutoJS 探测改成纯管道，
   任何新增功能都**不要**再假设有可写临时目录。

---

## 3. 机器行契约

一行一条、`KEY: payload` 前缀固定，便于 `grep`/`awk` 当断言锚点：

```
CHECK: <key>=OK|WARN|FAIL — <detail>      # env.sh 逐项体检（FAIL 走 stderr）
INFO: <key> — <detail>                    # env.sh 提示项（--quiet 可抑制）
ENV_RESULT: OK|WARN|FAIL — <汇总一行>      # env.sh 收尾
LAUNCH: READY|WORLD_READY|BLOCKED|TIMEOUT|ERROR|FAIL|INTERRUPTED|DRY-RUN — <说明>
  key = value                             # LAUNCH 之后的缩进字段行（两个空格 + key = value）
WIN_STOP: OK|RESIDUAL|DAEMON-STOP|killed|kill-call|would-kill|would-remove-pidfile|would-run|DRY-RUN|ERROR — <说明>
PURGE: removed|failed|would-remove|DRY-RUN — <路径>（<版本> 世界存档）
```

判定口径：

- `CHECK: x=FAIL` 只用于**真阻塞**（pwsh 不可用、仓库根错、mt.conf 缺键…）；「游戏没启动」这类
  预期状态一律 WARN，避免把正常状态刷成红。
- `LAUNCH: BLOCKED` 的三种成因：daemon JVM 缺 / `run/<v>` 不存在 / **同一线已有客户端在跑**。
  第四种（CIM 不可用、判不了线）也归 BLOCKED——不知道就别起。
- `LAUNCH: READY`（AutoJS 在线）与 `LAUNCH: WORLD_READY`（世界 marker 命中）可以同时出现；
  只有 READY 时 exit 仍为 0（客户端可用但世界可能还没进）。

## 4. 退出码

| 码 | `env.sh` | `launch.sh` | `stop.sh` |
|---|---|---|---|
| 0 | 全 OK 或仅 WARN | READY / WORLD_READY | 无残留（含「本来就没有」）/ dry-run |
| 1 | 有 FAIL | 进程提前退出 / Gradle `BUILD FAILED` | 仍有残留（`WIN_STOP: RESIDUAL`） |
| 2 | 用法错误 | 用法错误 / `ERROR`（AutoJS 从未响应且无 marker） | 用法错误 / CIM 不可用 |
| 11 | — | `BLOCKED`（daemon JVM/run 目录/同线残留/CIM 不可用） | — |
| 12 | — | `TIMEOUT`（health 响过但 client 侧没就绪） | — |
| 130 | — | 用户 Ctrl-C（**不收停游戏**） | — |

## 5. 用法

```bash
# 0) 默认编排入口（与 mt.ps1 同一套开关/阶段/退出码）
bash scripts/test/win/mt.sh --version 1.21.1 --phase env
bash scripts/test/win/mt.sh --version 1.21.1 --phase build --dry-run   # 只看将执行什么
bash scripts/test/win/mt.sh --version 26.1.2 --phase launch
bash scripts/test/win/mt.sh --version 1.21.1 --phase cases --case scripts/test/cases/X-1.21.1.json
bash scripts/test/win/mt.sh --version 1.21.1 --phase stop --purge-saves
bash scripts/test/win/mt.sh --allow-auto          # 全流程（四线顺序 + 1.21.1 门控）

# 1) 只读体检（随时可跑；不碰进程、不写文件）
bash scripts/test/win/env.sh
bash scripts/test/win/env.sh --version 1.21.1 --quiet   # 单线 + 静音 INFO

# 2) 先看将执行什么（不启动任何进程）
bash scripts/test/win/launch.sh --version 1.21.1 --dry-run

# 3) 真启动（默认 -Pquickplay=testworld ⇒ 进世界；日志落 scripts/test/win/logs/）
bash scripts/test/win/launch.sh --version 1.21.1
bash scripts/test/win/launch.sh --version 26.1.2 --timeout 900 --wait-world 120
bash scripts/test/win/launch.sh --version 1.20.1 --no-quickplay    # 停标题画面，只等 marker

# 4) 收停 / 清场
bash scripts/test/win/stop.sh --dry-run                  # 缺省 = 四线全扫，只报告
bash scripts/test/win/stop.sh --version 1.21.1
bash scripts/test/win/stop.sh --all --purge-saves        # 收停 + 清测试世界存档
bash scripts/test/win/stop.sh --version 26.1.2 --stop-daemon
```

线路映射（四线齐全；`Mt.Paths.psm1` 的 `$VERSIONS` 目前只有三条，本目录补齐 fabric）：

| `--version` | Gradle 子项目 | 加载器 | toolchain | AutoJS 就绪判据 |
|---|---|---|---|---|
| `1.21.1` | `:neoforge-1.21.1` | neoforge | 21 | ✅ 主判据（jar 在位） |
| `1.20.1` | `:forge-1.20.1` | forge | 17 | ✅ 主判据（jar 在位） |
| `26.1.2` | `:neoforge-26.1.2` | neoforge | 25 | ✅ 主判据（jar 在位） |
| `fabric-1.20.1` | `:fabric-1.20.1` | fabric | 17 | ✅ 主判据（jar 在位） |

> 判据只看 jar 是否在位；缺 jar 的线自动退回日志 marker（`env.sh` 出 WARN，`launch.sh` 置 `probe_enabled=0`）。

环境变量逃生阀：

| 变量 | 作用 |
|---|---|
| `MT_GRADLE_DAEMON_JAVA_HOME` | 指定 daemon JVM（省掉自动探测） |
| `MT_ALLOW_NO_AUTOJS=1` | 强制允许「只靠日志 marker 判就绪」（AutoJS 端点坏掉时用） |
| `MT_WIN_LAUNCH_MODE=bg\|start` | 等价于 `--launch-mode` |
| `MT_LAUNCH_QUICKPLAY` / `MT_LAUNCH_READY_TIMEOUT_SEC` / `MT_LAUNCH_WAIT_WORLD_SEC` | 覆盖默认 quickplay 世界名 / 超时 / 世界等待 |
| `MT_WIN_LOG_DIR` / `MT_WIN_TMP_DIR` / `MT_WIN_TEMP` | 日志目录 / 临时目录 / 强制 Windows TEMP 值 |

## 6. 就绪判据（与 Linux 台一致，细节不同）

1. **主判据（AutoJS）**：`GET <endpoint 去掉 /mcp>/health` 返回 `ok`，然后
   MCP `initialize`（`protocolVersion 2025-06-18`）→ 拿 `Mcp-Session-Id` →
   `notifications/initialized` → `tools/call {"name":"status"}`，
   在 `result.content[].text` 的 JSON 里找 `sides[]`，要求 `side=="client"` 且 `available==true`。
2. **兜底（日志 marker）**：`1.21.1 / 1.20.1 / fabric-1.20.1` = `Total time to load game and open world was`；
   `26.1.2` = `logged in with entity id` 且 `Loaded <N> advancements`。
   兜底恒可用（`marker_fallback=1`）。AutoJS 为主判据时，marker 只有在端点**从头到尾没响应**（`health_seen=0`）时才允许提前收工；端点有响应就继续等 `status(client available=true)`——否则放宽规则反而会制造「四线一律 READY-by-marker」的假绿。
3. **世界就绪**：marker 命中 → 另打 `LAUNCH: WORLD_READY`。Windows 侧默认带
   `-Pquickplay=testworld`（build.gradle 四线已配置），所以正常情况下两者会一起出现。

## 7. 与 pwsh 主入口 `scripts/test/mt.ps1` 的分工（2026-11-01 更新：bash 反向编排 pwsh）

| 维度 | `scripts/test/win/mt.sh`（bash 默认入口） | `scripts/test/mt.ps1`（pwsh 入口，过渡期并存） |
|---|---|---|
| 定位 | **默认编排层**：`preflight → 逐版本 build/env/launch/cases → report → stop → summary` | 原全流程测试台，行为契约的参照实现 |
| 用例 / 断言执行 | 委托 pwsh（`mt_case.ps1` / `mt_report.ps1` / `mt_preflight.ps1`），打 `MT_DEPRECATED:` | 原生 |
| 实机注入 | 不做任何 GUI 注入；launch 只走 AutoJS `/health` + `tools/call status` | `--channel autojs` 默认零 GUI 注入；`native` 才动 `Mt.Win32` |
| 预算 / 心跳 / 看门狗 | P1 只打印数字，不掐表（P4 补 `watchdog`） | 有（`PhaseBudgets` / 10s 心跳 / 90s STALL） |
| 版本表 | 四线齐全（含 `fabric-1.20.1`） | `Mt.Paths.psm1` 的 `$VERSIONS` 三线（缺 fabric） |

**调用方向（单向）**：`mt.sh` → `pwsh …`。反向不成立 —— `mt.ps1` 不知道本目录存在，也不调用任何 `.sh`。
淘汰节奏见 `docs/features/bash-first-test-flow-plan.md`：S0 默认路径不再经过 pwsh → S1 打 deprecated → S2 无默认引用 → S3 物理删除；当前基线 `grep -rn '\.ps1' scripts/test/win/ | wc -l` = 59 行（P4 收尾后才是 S2 验收时点）。

## 8. 本机实测记录（2026-11-01，Windows 原生 + git bash UCRT64）

| 项 | 实测 |
|---|---|
| `bash --version` | 5.3.15（MSYS2 UCRT64，`/usr/bin/bash`） |
| `git rev-parse --show-toplevel` | `F:/MCProject/astral_dice_multiloader` → `cygpath` 转 `/f/...` |
| `df -T .` | `F: ntfs … /f`（判据 `ntfs`） |
| `JAVA_HOME` | `C:\Program Files\Zulu\zulu-21\`，`java -version` = 21.0.12；JDK 17 只有 `~/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2`（Gradle 自动下载） |
| `$TEMP` | `/tmp` = `C:\Users\xmace\AppData\Local\Temp`（`mount` 里 `usertemp`），**写入被 ACL/沙箱拒** |
| AutoJS 端点 | `curl --noproxy '*' -m 3 http://127.0.0.1:25585/health` → `000`（未启动游戏，属预期） |
| java 进程 | CIM 查询为空（无残留） |
| Gradle daemon | 0 个 |
| `git config --get core.autocrlf` | `true` |
| 锁目录 | `/f/MCProject/.agent-locks` 存在，仅 `dsh.lock`（本宿主） |
| `cmd //c start "" /B …` | 6s 超时未返回（阻塞）⇒ 默认 `--launch-mode bg` |
| bash 后台存活 | 父 shell 退出后子进程继续写文件（130B → 308B），可作为 `setsid` 替代 |
| `cat /proc/<bashpid>/winpid` | 有值（MSYS 映射），可写进 pidfile |

## 9. 已知限制

1. 判线依赖命令行标记串；若某进程的命令行被截断（`Win32_Process.CommandLine` 为空/权限不足），
   会判成「不属于本 repo」⇒ `stop.sh` 报 OK 但实际有残留。此时用 `env.sh` 的 `residue` 项与
   任务管理器交叉确认。
2. `LAUNCH: TIMEOUT` / `ERROR` 后默认**不收停**（与 Linux 蓝本一致），避免把「还在加载」误杀；
   需要自动收就加 `--kill-on-timeout`。
3. `--purge-saves` 只清白名单世界目录；`run/<v>/logs`、`hs_err_pid*.log`、`crash-reports` 一律不动
   （要清就手工，别让测试脚本替人删日志）。
4. `--launch-mode start` 在本机不可用（阻塞）；保留该选项是为了其他机器/未来的 `start` 语义。
5. 本目录脚本落地即 `755`；若从 git 检出后发现不可执行，`bash <脚本>` 方式调用即可，
   或在 git bash 里 `chmod +x`（注意 `core.autocrlf=true` 时保持 LF 提交）。

## 10. 1.20.1 Fabric 线（双 run 目录 + RCON 通道）

fabric 线与前三线有**两处结构差异**，用法据此分叉：

1. **run 目录是双份**：`fabric-1.20.1/run/client` 与 `fabric-1.20.1/run/server`（前三线是单目录 `run/<版本>`）。
   ⇒ 必须用 `--side client|server` 指定一侧；不带时默认 `client`。其它三线传 `--side` 会被明确拒绝（`exit 2`）。
2. **AutoJS 是条件依赖**：`fabric-1.20.1/build.gradle:182-190` 只在 `-PautojsTestMod=1` 时挂 `modRuntimeOnly`。
   ⇒ launch **默认不带**该开关；显式加 `--enable-autojs`（或环境变量 `MT_FAB_AUTOJS=1`）才会追加 `-PautojsTestMod=1`。
   未开启时注入/读数回退 **RCON**（`127.0.0.1:25575`，密码 `astralft`，见 `fabric-1.20.1/run/server/server.properties`），
   `env.sh` 会打印 `MT_INFO: fab_channel` / `MT_OK: fab_rcon`，`mt_line_has_autojs` 会明确报「无 AutoJS，回退 RCON」而不是静默 false。

```bash
# 六阶段（每阶段都要带 --side）
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase env
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase build
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side client --phase launch          # 默认不带 -PautojsTestMod
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side client --phase launch --enable-autojs
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase cases  --allow-auto
bash scripts/test/win/mt.sh --version fabric-1.20.1 --phase report --allow-auto
bash scripts/test/win/mt.sh --version fabric-1.20.1 --phase stop --force

# 干跑（不启动任何进程，先看要跑什么）
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase launch --dry-run
bash scripts/test/win/launch.sh --version fabric-1.20.1 --side server --dry-run
```

| 项 | fabric 线取值 |
|---|---|
| 子项目 | `:fabric-1.20.1`（toolchain 17） |
| Gradle 任务 | `:fabric-1.20.1:runClient` / `:fabric-1.20.1:runServer`（按 `--side`） |
| run 根 | `fabric-1.20.1/run/<side>`（`mt_fab_side_dir`） |
| 就绪 marker | server：`Done (<N>s)! For help`；client：`Sound engine started`（`MT_FAB_READY_*`） |
| 注入 / 读数 | AutoJS（仅 `--enable-autojs`）或 RCON `25575`（`mt_rcon_exec`） |
| 进程 PID | `fabric-1.20.1/run/<side>/win-run.pid`，兼容旧 `scripts/test/fabric/.ft_launch_state.json` 的 `PID` |
| 用例 | `scripts/test/cases/*-fabric-1.20.1.json`（9 条：启动就绪 / 饰品双后端 / 槽位尺寸 / 卡牌筹码 UI / tick 分发 / 立牌 / JAR 资源 / 平台桥 / 版本门槛） |

**机器行**：`fabric.sh` 内部沿用 `scripts/test/fabric/ft.ps1` 的 `MT_FAB_*` 契约，但经 `mt_fab_normalize_stream`
统一折叠成 `MT_*` 输出（`MT_FAB_PHASE:` → `MT_PHASE:` 等）。`MT_FAB_*` 只出现在 `MT_DEPRECATED:` 提示里。

**零 pwsh**：fabric 的四个阶段（launch / cases / report / preflight）全部走 `lib/fabric.sh` 的 bash 实现，
不委托 `ft_*.ps1`；`scripts/test/fabric/` 保留为只读参考（语义与机器行契约来源）。
