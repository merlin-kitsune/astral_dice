# Windows 测试台（git bash / MSYS2）— env / launch / stop

本目录是 **`scripts/test/linux/` 的 Windows 对应物**：同一台机器上、同一份契约
（`docs/features/windows-channel-decoupling-spec.md` §3）的 bash 侧实现。

一句话边界：**这里是 Windows 测试台的 bash 侧实现**——

- `mt.sh`（+ `lib/phases.sh`）= **唯一编排入口**（开关 / 阶段 / 退出码 / 机器行沿用迁移前的契约）；
- `env.sh` / `launch.sh` / `stop.sh` = 三个可单跑的阶段脚本（体检 / 拉起 / 收停）；
- 用例 / 断言 / 报告 / 预检的**执行器 = python**（`scripts/test/lib/mt_case.py`、`scripts/test/mt_assert.py`、`scripts/test/mt_report.py`、`scripts/test/mt_preflight.py`），由 `mt.sh` 直接调用，**不经过任何外部脚本解释器**；旧脚本资产已全量退役（登记见 `docs/features/`）。

```
scripts/test/win/
├── mt.sh            # 默认编排入口：--version / --phase / --case / --channel / --allow-auto / --dry-run
├── lib/phases.sh    # 阶段实现（env/build/launch/cases/report/stop/preflight）+ python 执行器分派
├── lib/common.sh    # 版本表 / 机器行 / jps 判线 / 端口探测 / AutoJS 探测 / 路径与 JDK 发现
├── env.sh           # 只读体检：逐项 CHECK: → 收尾 ENV_RESULT:，退出码 0/1/2
├── launch.sh        # --version <v>：cmd //c gradlew.bat :<子项目>:runClient → 轮询就绪 → LAUNCH: READY
├── stop.sh          # [--version|--all]：jps+pidfile+端口判线，只打印待收停 PID（不杀进程），可选清存档/停 daemon
├── logs/            # launch 的运行日志落这里（<version>-<ts>.log），日志文件不入库
└── README.md        # 本文件
```

> 写域纪律：本目录**只读**复用 `scripts/test/mt.conf` 与 `run/<v>/**` 的现状，
> 不改 `scripts/test/linux/**`、不改 `mt.conf`、不新增任何外部脚本资产（旧资产已退役）。

---

## 1. 底线事实（先读这段，写脚本前必须知道）

| 事实 | 后果 |
|---|---|
| git bash 的 `$TEMP` 就是 `/tmp`，而 `/tmp` 是 `C:\Users\<user>\AppData\Local\Temp` 的 MSYS 挂载点（`mount` 里带 `usertemp`） | 「不用 /tmp」= 「不用 Windows 默认 TEMP」。本机该目录 ACL/沙箱拒写，所以 **AutoJS 探测全程不落临时文件**（`curl -D -` + 管道解析） |
| `cmd //c start "" /B cmd /c "…"` 在本机**阻塞**（6s 超时仍未返回） | `launch.sh` 默认 `--launch-mode bg`（bash 后台 + `cmd //c gradlew.bat`，可拿 PID）；`start` 只是保留的备选 |
| 旧式进程终止的退出码不可靠（找不到进程也常返回 0，输出还是 GBK 乱码） | 脚本**不终止进程**：`stop.sh` 只打印待收停 PID，残留判定一律用 jps + 端口复核 |
| 全仓只有一个 Gradle 并发位，daemon JVM **必须 ≥ 21** | `launch.sh` 找不到 ≥21 的 JDK 就 `LAUNCH: BLOCKED`(11)，绝不用 17 硬起 |
| `run/1.20.1/mods`、`run/fabric-1.20.1/mods` 里实际躺着 `AutoJS-*.jar` | 2026-11-01 用户裁决：AutoJS 就绪判据**放宽至所有版本**，按「本线 `run/<版本>/mods` 里 `AutoJS-*.jar` 是否在位」判定（各线实测都在位 ⇒ 各线全部纳入 AutoJS 主判据；无 jar 的线自动退回日志 marker） |
| bash 在本机会话里对 `scripts/test/**`、`run/**` 的写入可能被 ACL/沙箱拒 | `env.sh` 的 `write_targets` 会 WARN；`launch.sh` 建不了日志目录就直接 `ERROR`(2) 明说，不静默降级 |

---

## 2. Linux 机制 → Windows 对应物

| Linux 蓝本用的是 | Windows 侧对应物 | 说明 / 降级 |
|---|---|---|
| `/proc/<pid>/cmdline`、`/proc/<pid>/cwd` | `jps -lvm` 取「PID + 主类与参数」 | 没有 cwd 字段，判线只能靠命令行；标记串：`:<子项目>:`、`run\\<version>`、`run/<version>`（**不用**裸子项目名，那同时是仓库目录名，会误命中 IDE） |
| `pgrep -f` / `ps` 过滤 | 同一份 jps 输出在 bash 里 `while IFS=$'\t' read -r pid cmd` 过滤 | jps 不可用（缺 JDK / 无权限）⇒ `env.sh` FAIL、`launch.sh`/`stop.sh` BLOCKED/ERROR(2)，绝不「当作没有进程」继续 |
| `kill -SIG -- -<pgid>`（进程组） | 脚本**不做终止**，只输出 PID 列表 | 终止由 Agent 通过 windows MCP 的 `Process(kill)` 执行；没有「整棵进程树」的等价物 |
| `setsid` + `nohup` | bash 后台 `>log 2>&1 </dev/null &` + `cmd //c`；`start "" /B` 作备选 | 本机 `start /B` 实测阻塞 ⇒ 默认走 bg；后台子进程必须重定向三路 FD，否则调用方挂起 |
| pidfile 里的 shell PID | pidfile 记 **Windows PID**：`cat /proc/$!/winpid` | `runClient` 的实际进程是 `java.exe`，`win-run.pid` 只是「本脚本拉起的 wrapper」的锚点，权威判线仍是 jps + 端口 |
| `findmnt -T .` | `df -T .`（Type 列 `ntfs`） | 判据从「挂载源/挂载点」降级为「文件系统类型」；`ntfs` 就代表大小写不敏感 + CRLF 风险 |
| `ss -lntp` | `netstat -ano` 过滤 25585/25565/25575 的 `LISTENING` 行取 PID | 没有「按进程过滤」，取 PID 后与 jps 行、pidfile 三者交叉核对 |
| `/usr/lib/jvm/*` 遍历 | `JAVA_HOME` → `C:/Program Files/Zulu/zulu-<n>` → `C:/Program Files/Java/jdk-<n>*` → `Eclipse Adoptium` → `Amazon Corretto` → `~/.gradle/jdks/*<n>*` → `~/.jdks/*<n>*` | 逐条跑 `java -version` 校验主版本（**精确匹配**才算 OK）；Gradle 自动下载的 JDK 也在这条链里被找到 |
| `comm=java` 判残留 | `jps -lvm` 的 java 行 | 只认 JVM；Gradle daemon 用命令里的 `GradleDaemon` 再分一层 |
| `bash ./gradlew --stop` | `cmd //c "gradlew.bat --stop"` | 需要 ≥21 的 daemon JVM（`stop.sh --stop-daemon` 会自动带上） |
| `trap INT/TERM → 130` | 同款 | 但**不收停游戏**：中断只退出本脚本，游戏进程继续跑（会打印收停命令） |

### 不可移植 / 已放弃的项

1. **进程组语义**：`kill -- -pgid` 没有对应物，脚本也不再终止进程；若某个 java 已 detach 出 wrapper，
   只能靠 jps 判线逐条列出 PID，交给 Agent 终止。
2. **cwd 判据**：Linux 用 `/proc/<pid>/cwd` 精确判「这个 java 是不是在 `run/<v>/` 下」；
   Windows 拿不到别的进程 cwd ⇒ 只能靠命令行标记串，理论上比 cwd 弱。
3. **信号语义**：`SIGTERM` 宽限期不存在，且脚本不发送任何信号；终止方式与时机由 Agent 决定
   （强杀写入中的存档有小概率留残档）。
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

- `CHECK: x=FAIL` 只用于**真阻塞**（jps 不可用、仓库根错、mt.conf 缺键…）；「游戏没启动」这类
  预期状态一律 WARN，避免把正常状态刷成红。
- `LAUNCH: BLOCKED` 的三种成因：daemon JVM 缺 / `run/<v>` 不存在 / **同一线已有客户端在跑**。
  第四种（jps 不可用、判不了线）也归 BLOCKED——不知道就别起。
- `LAUNCH: READY`（AutoJS 在线）与 `LAUNCH: WORLD_READY`（世界 marker 命中）可以同时出现；
  只有 READY 时 exit 仍为 0（客户端可用但世界可能还没进）。

## 4. 退出码

| 码 | `env.sh` | `launch.sh` | `stop.sh` |
|---|---|---|---|
| 0 | 全 OK 或仅 WARN | READY / WORLD_READY | 无残留（含「本来就没有」）/ dry-run |
| 1 | 有 FAIL | 进程提前退出 / Gradle `BUILD FAILED` | 仍有残留（`MT_STOP: FAIL`） |
| 2 | 用法错误 | 用法错误 / `ERROR`（AutoJS 从未响应且无 marker） | 用法错误 / jps 不可用 |
| 11 | — | `BLOCKED`（daemon JVM/run 目录/同线残留/jps 不可用） | — |
| 12 | — | `TIMEOUT`（health 响过但 client 侧没就绪） | — |
| 130 | — | 用户 Ctrl-C（**不收停游戏**） | — |

## 5. 用法

```bash
# 0) 默认编排入口（同一套开关/阶段/退出码）
bash scripts/test/win/mt.sh --version 1.21.1 --phase env
bash scripts/test/win/mt.sh --version 1.21.1 --phase build --dry-run   # 只看将执行什么
bash scripts/test/win/mt.sh --version 26.1.2 --phase launch
bash scripts/test/win/mt.sh --version 1.21.1 --phase cases --case scripts/test/cases/X-1.21.1.json
bash scripts/test/win/mt.sh --version 1.21.1 --phase stop --purge-saves
bash scripts/test/win/mt.sh --allow-auto          # 全流程（各线顺序 + 1.21.1 门控）

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
bash scripts/test/win/stop.sh --dry-run                  # 缺省 = 各线全扫，只报告
bash scripts/test/win/stop.sh --version 1.21.1
bash scripts/test/win/stop.sh --all --purge-saves        # 收停 + 清测试世界存档
bash scripts/test/win/stop.sh --version 26.1.2 --stop-daemon
```

线路映射（各线齐全；bash 侧版本表目前只有三条，本目录补齐 fabric）：

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
   兜底恒可用（`marker_fallback=1`）。AutoJS 为主判据时，marker 只有在端点**从头到尾没响应**（`health_seen=0`）时才允许提前收工；端点有响应就继续等 `status(client available=true)`——否则放宽规则反而会制造「各线一律 READY-by-marker」的假绿。
3. **世界就绪**：marker 命中 → 另打 `LAUNCH: WORLD_READY`。Windows 侧默认带
   `-Pquickplay=testworld`（build.gradle 各线已配置），所以正常情况下两者会一起出现。

## 7. 执行器构成（2026-11-02：全 bash + python；2026-11-09 起零外部脚本解释器）

| 层 | 实现 | 说明 |
|---|---|---|
| 编排 | `mt.sh` + `lib/phases.sh` | `preflight → 逐版本 build/env/launch/cases → report → stop → summary` |
| 阶段 | `env.sh` / `launch.sh` / `stop.sh` / `lib/fabric.sh` | 可单跑；fabric 四阶段全是 bash 原生 |
| 用例 / 断言 / 报告 / 预检 | `scripts/test/lib/mt_case.py`、`scripts/test/mt_assert.py`、`scripts/test/mt_report.py`、`scripts/test/mt_preflight.py`、`scripts/test/mt_inject.py` | 由 `phases.sh` 的 `mt_phase_py_file` 直接调用 |
| 进程 / 窗口定位 | `scripts/test/lib/mt_ps.py` + `lib/common.sh` 的 jps 封装 | 进程表一律 `jps -lvm`（JDK 自带）；jps 不可用 ⇒ ERROR(2)，不静默降级 |

**调用方向**：`mt.sh` → `python …`。没有任何一层再调用外部脚本资产。

**能力变化（相对迁移前的测试台）**：
- `launch --channel native` 现记 `BLOCKED(11)`：GUI 窗口定位 / 合成输入原先由已退役的启动器提供。
  底层键鼠通道已由 `scripts/test/mt_inject.py`（ctypes 直调 `user32.SendInput`）重建，是否接回 native 通道待裁决。
- `vision` 类断言（截图比对）记 `BLOCKED(11)`：截图通道自检件随退役删除。

迁移/退役的完整口径、能力损失登记与等价性证据：`docs/features/` 下的退役登记。

## 8. 本机实测记录（2026-11-01，Windows 原生 + git bash UCRT64）

| 项 | 实测 |
|---|---|
| `bash --version` | 5.3.15（MSYS2 UCRT64，`/usr/bin/bash`） |
| `git rev-parse --show-toplevel` | `F:/MCProject/astral_dice_multiloader` → `cygpath` 转 `/f/...` |
| `df -T .` | `F: ntfs … /f`（判据 `ntfs`） |
| `JAVA_HOME` | `C:\Program Files\Zulu\zulu-21\`，`java -version` = 21.0.12；JDK 17 只有 `~/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2`（Gradle 自动下载） |
| `$TEMP` | `/tmp` = `C:\Users\xmace\AppData\Local\Temp`（`mount` 里 `usertemp`），**写入被 ACL/沙箱拒** |
| AutoJS 端点 | `curl --noproxy '*' -m 3 http://127.0.0.1:25585/health` → `000`（未启动游戏，属预期） |
| java 进程 | `jps -lvm` 查询为空（无残留） |
| Gradle daemon | 0 个 |
| `git config --get core.autocrlf` | `true` |
| 锁目录 | `/f/MCProject/.agent-locks` 存在，仅 `dsh.lock`（本宿主） |
| `cmd //c start "" /B …` | 6s 超时未返回（阻塞）⇒ 默认 `--launch-mode bg` |
| bash 后台存活 | 父 shell 退出后子进程继续写文件（130B → 308B），可作为 `setsid` 替代 |
| `cat /proc/<bashpid>/winpid` | 有值（MSYS 映射），可写进 pidfile |

## 9. 已知限制

1. 判线依赖命令行标记串；若某进程的 jps 行缺失（权限不足 / 非本用户 JVM 等），
   会判成「不属于本 repo」⇒ `stop.sh` 报 OK 但实际有残留。此时用 `env.sh` 的 `residue` 项与
   任务管理器交叉确认。
2. `LAUNCH: TIMEOUT` / `ERROR` 后默认**不收停**（与 Linux 蓝本一致），避免把「还在加载」误杀；
   需要收停时由 Agent 用 windows MCP 终止（脚本只打印 PID）。
3. `--purge-saves` 只清白名单世界目录；`run/<v>/logs`、`hs_err_pid*.log`、`crash-reports` 一律不动
   （要清就手工，别让测试脚本替人删日志）。
4. `--launch-mode start` 在本机不可用（阻塞）；保留该选项是为了其他机器/未来的 `start` 语义。
5. 本目录脚本落地即 `755`；若从 git 检出后发现不可执行，`bash <脚本>` 方式调用即可，
   或在 git bash 里 `chmod +x`（注意 `core.autocrlf=true` 时保持 LF 提交）。

## 10. 1.20.1 Fabric 线（双 run 目录 + RCON 通道）

fabric 线与其余各线有**两处结构差异**，用法据此分叉：

1. **run 目录是双份**：`fabric-1.20.1/run/client` 与 `fabric-1.20.1/run/server`（其余各线是单目录 `run/<版本>`）。
   ⇒ 必须用 `--side client|server` 指定一侧；不带时默认 `client`。其它各线传 `--side` 会被明确拒绝（`exit 2`）。
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

**机器行**：`fabric.sh` 内部沿用旧 fabric 测试台的 `MT_FAB_*` 契约，但经 `mt_fab_normalize_stream`
统一折叠成 `MT_*` 输出（`MT_FAB_PHASE:` → `MT_PHASE:` 等）。`MT_FAB_*` 只出现在 `MT_DEPRECATED:` 提示里。

**零外部脚本资产**：fabric 的四个阶段（launch / cases / report / preflight）全部走 `lib/fabric.sh` 的 bash 实现，
不委托任何已退役脚本；`scripts/test/fabric/` 保留为只读参考（语义与机器行契约来源）。
