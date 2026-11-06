#!/usr/bin/env bash
# ============================================================================
# scripts/test/win/launch.sh — Astral Dice 测试台 · Windows(git bash) 启动路径
#
# 语义 = Linux 蓝本 launch.sh 的 Windows 对应物 + mt.ps1 的最小集：
#   拉起 gradlew.bat :<子项目>:runClient（带 -Pquickplay）→ 轮询就绪 → 打印机器行。
#   不复刻 mt_launch.ps1 的窗口注入/截图/世界清场/OP 闸门（见 README「分工边界」）。
#
# 用法：
#   bash scripts/test/win/launch.sh --version <ver> [--timeout 600] [--wait-world 0]
#                                   [--quickplay testworld|--no-quickplay]
#                                   [--kill-on-timeout] [--log-dir DIR]
#                                   [--launch-mode bg|start] [--dry-run]
#                                   [--side client|server] [--enable-autojs]
#   --side / --enable-autojs 只对 fabric 线（fabric-<mcver>）有效（双 run 目录 + 条件 AutoJS）；
#   其它三线传了报错。--enable-autojs 等价 export MT_FAB_AUTOJS=1（带 -PautojsTestMod=1）。
#
# 就绪判据（与 Linux 相同顺序）：
#   * 主判据：AutoJS 客户端侧 —— GET /health 返回 ok(200) 且 MCP tools/call status
#     报 sides[] 里 side=="client" 且 available=true → LAUNCH: READY。
#   * 兜底：日志 marker（1.21.1/1.20.1/fabric："Total time to load game and open world was"；
#     26.1.2："logged in with entity id" 且 "Loaded <N> advancements"）。
#     兜底仅在 MT_ALLOW_NO_AUTOJS=1 或该线 run/mods 无 AutoJS jar 时启用。
#   * 世界就绪：marker 命中另打 LAUNCH: WORLD_READY（Windows 侧用 quickplay 开世界）。
#
# 退出码：0=READY/WORLD_READY；1=启动失败/进程提前退出/Gradle 构建失败；
#         2=用法错误或 ERROR（AutoJS 从未响应且无 marker）；11=BLOCKED
#         （缺 daemon JVM / run 目录不存在 / 同线已有客户端）；12=TIMEOUT；130=用户中断
#
# 进程所有权：gradlew.bat 由本脚本以 `cmd //c` 在后台拉起（git bash 无 setsid），
#   pidfile 记 Windows PID（run/<ver>/win-run.pid）；本脚本退出后游戏继续跑，
#   收停请用 scripts/test/win/stop.sh --version <ver>。
# ============================================================================
set -uo pipefail

WIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
. "$WIN_DIR/lib/common.sh"

MT_REPO_ROOT="$(mt_resolve_repo_root "$WIN_DIR")" || MT_REPO_ROOT=""
if [ -z "$MT_REPO_ROOT" ]; then
    echo "LAUNCH: ERROR — 无法定位仓库根（git rev-parse 与 settings.gradle 上溯都失败）" >&2
    exit 2
fi
export MT_REPO_ROOT
LOG_DIR="${MT_WIN_LOG_DIR:-$WIN_DIR/logs}"

VERSION=""
TIMEOUT="${MT_LAUNCH_READY_TIMEOUT_SEC:-600}"
WAIT_WORLD="${MT_LAUNCH_WAIT_WORLD_SEC:-0}"
QUICKPLAY="${MT_LAUNCH_QUICKPLAY:-testworld}"
KILL_ON_TIMEOUT=0
DRY=0
LAUNCH_MODE="${MT_WIN_LAUNCH_MODE:-bg}"
SIDE="${MT_SIDE:-}"        # 仅 fabric 线（fabric-*）：client|server（其它线必须留空）

usage() {
    cat <<'USAGE'
用法：
  bash scripts/test/win/launch.sh --version <ver> [--timeout 600] [--wait-world 0]
                                  [--quickplay <世界名>|--no-quickplay]
                                  [--kill-on-timeout] [--log-dir DIR]
                                  [--launch-mode bg|start] [--dry-run]

  --version <ver>   1.21.1 | 1.20.1 | 26.1.2 | fabric-1.20.1 | fabric-1.21.1（必填）
  --timeout <sec>   等 AutoJS 客户端侧就绪的上限，默认 600
  --wait-world <秒> READY 后再等世界 marker 的秒数，默认 0=不额外等
  --quickplay <名>  透传 -Pquickplay=<名>（默认 testworld；build.gradle 四线已配置）
  --no-quickplay    不带 -Pquickplay（客户端停在标题画面；就绪只能靠 AutoJS）
  --kill-on-timeout 超时后自动调 stop.sh 收停（默认只提示）
  --log-dir <dir>   日志目录，默认 scripts/test/win/logs
  --launch-mode bg|start
                    bg（默认）= git bash 后台 + mt_cmdwin gradlew.bat，可拿 PID；
                    start      = mt_cmdwin start "" /B cmd /c "…"（真正脱离控制台，
                                 但拿不到 PID，且本机沙箱下实测 start/b 会阻塞）
  --dry-run         只打印将执行的命令与判定依据，不启动任何进程

环境变量（仅 fabric 线有意义）：
  MT_TEST_SODIUM=false  透传 -PtestSodium=false（fabric 的 Sodium/Iris 是条件依赖，
                        版本不匹配会让客户端崩；手工验证时把它关掉，见
                        docs/handover-1.5.0-main.md §6-7）。未设置 = 不传，行为不变。

就绪：AutoJS health=ok + status(client available=true) → READY；
      日志 marker 命中 → 另打 WORLD_READY；无 AutoJS jar 或 MT_ALLOW_NO_AUTOJS=1 时 marker 可兜底。
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --version)        VERSION="${2:-}"; shift 2 ;;
        --version=*)      VERSION="${1#*=}"; shift ;;
        --timeout)        TIMEOUT="${2:-}"; shift 2 ;;
        --timeout=*)      TIMEOUT="${1#*=}"; shift ;;
        --wait-world)     WAIT_WORLD="${2:-}"; shift 2 ;;
        --wait-world=*)   WAIT_WORLD="${1#*=}"; shift ;;
        --quickplay)      QUICKPLAY="${2:-}"; shift 2 ;;
        --quickplay=*)    QUICKPLAY="${1#*=}"; shift ;;
        --no-quickplay)   QUICKPLAY=""; shift ;;
        --kill-on-timeout) KILL_ON_TIMEOUT=1; shift ;;
        --log-dir)        LOG_DIR="${2:-}"; shift 2 ;;
        --log-dir=*)      LOG_DIR="${1#*=}"; shift ;;
        --launch-mode)    LAUNCH_MODE="${2:-}"; shift 2 ;;
        --launch-mode=*)  LAUNCH_MODE="${1#*=}"; shift ;;
        --side)           SIDE="${2:-}"; shift 2 ;;
        --side=*)         SIDE="${1#*=}"; shift ;;
        --enable-autojs)  MT_FAB_AUTOJS=1; export MT_FAB_AUTOJS; shift ;;
        --disable-autojs) MT_FAB_AUTOJS=0; export MT_FAB_AUTOJS; shift ;;
        --dry-run)        DRY=1; shift ;;
        -h|--help)        usage; exit 0 ;;
        *) echo "用法错误：未知参数 $1（-h 看用法）" >&2; exit 2 ;;
    esac
done

if [ -z "$VERSION" ]; then
    echo "用法错误：必须给 --version（$MT_ALL_VERSIONS）；-h 看用法" >&2
    exit 2
fi
if ! mt_is_version "$VERSION"; then
    echo "用法错误：未知 --version $VERSION（可选 $MT_ALL_VERSIONS）" >&2
    exit 2
fi
# fabric 线：把本次版本写进 fabric 库全局（run 目录 / AutoJS 制品名跟着版本走）
mt_fab_set_version "$VERSION" || true
case "$TIMEOUT" in ''|*[!0-9]*) echo "用法错误：--timeout 必须是整数秒" >&2; exit 2 ;; esac
case "$WAIT_WORLD" in ''|*[!0-9]*) echo "用法错误：--wait-world 必须是整数秒（0=不等）" >&2; exit 2 ;; esac
case "$LAUNCH_MODE" in
    bg|start) ;;
    *) echo "用法错误：--launch-mode 只接受 bg | start" >&2; exit 2 ;;
esac

PROJECT="$(mt_subproject "$VERSION")"
TOOLCHAIN_VER="$(mt_toolchain "$VERSION")"

# ── fabric 线：双 run 目录 + 条件 AutoJS（其它三线走原来的单目录）──────────
# 三线：仓库根/run/<版本>，客户端固定 :<子项目>:runClient。
# fabric：<子项目>/run/<client|server>，任务按侧选 runClient / runServer；
#   -PautojsTestMod=1 只在显式 --enable-autojs（或 MT_FAB_AUTOJS=1）时才带。
FAB_SIDE=""
Gradle_TASK=""
if mt_is_fabric "$VERSION"; then
    FAB_SIDE="$(mt_fab_side "$SIDE")" || {
        echo "用法错误：--side 需要 client 或 server（收到：${SIDE:-<空>}）" >&2
        exit 2
    }
    RUN_DIR="$(mt_fab_side_dir "$FAB_SIDE")"
    Gradle_TASK="$(mt_fab_gradle_task "$FAB_SIDE")"
    AJ_ARG="$(mt_fab_gradle_autojs_arg "$FAB_SIDE")"
else
    if [ -n "$SIDE" ]; then
        echo "用法错误：--side 只对 fabric 线（fabric-<mcver>）有效（其它三线是单 run 目录 run/<版本>；收到 --version $VERSION）" >&2
        exit 2
    fi
    RUN_DIR="$MT_REPO_ROOT/run/$VERSION"
    Gradle_TASK="runClient"
    AJ_ARG=""
fi
# mods 目录的显示用标签（fabric 是双目录，不能拼成 run/<版本>/mods）
if [ -n "$FAB_SIDE" ]; then MODS_LABEL="$MT_FAB_SUBPROJECT/run/$FAB_SIDE/mods"; else MODS_LABEL="run/$VERSION/mods"; fi
PIDFILE="$RUN_DIR/win-run.pid"
ENDPOINT="$(mt_aj_endpoint)"
BASE_URL="${ENDPOINT%/mcp}"

# 就绪 marker：fabric 按侧取（server=Done (...) For help，client=Sound engine），
# 其它三线沿用原判据函数（行为逐字节不变）。
launch_marker_hit() {
    if mt_is_fabric "$VERSION"; then mt_fab_marker_hit "$FAB_SIDE" "$1"; else mt_log_marker_hit "$1" "$VERSION"; fi
}
launch_marker_name() {
    if mt_is_fabric "$VERSION"; then mt_fab_marker_name "$FAB_SIDE"; else mt_log_marker_name "$VERSION"; fi
}
# fabric 的同线残留按侧判定（client 与 server 是两个独立目录，可同时跑）；
# 其它三线仍按版本判，语义不变。
launch_cmdline_matches() {
    if mt_is_fabric "$VERSION"; then
        mt_cmdline_matches_version "$VERSION" "$1" || return 1
        local lc
        lc="$(printf '%s' "$1" | tr 'A-Z' 'a-z')"
        case "$lc" in *run*"$FAB_SIDE"*) return 0 ;; esac
        return 1
    fi
    mt_cmdline_matches_version "$VERSION" "$1"
}

# ── 判定依据：daemon JVM / run 目录 / AutoJS jar / 同线残留 ────────────────
BLOCKERS=""

DAEMON_JDK="$(mt_find_daemon_jdk 2>/dev/null || true)"
if [ -z "$DAEMON_JDK" ]; then
    BLOCKERS="$BLOCKERS daemon_jvm_missing"
fi
if [ ! -d "$RUN_DIR" ]; then
    BLOCKERS="$BLOCKERS run_dir_missing"
fi
if [ ! -f "$MT_REPO_ROOT/gradlew.bat" ]; then
    BLOCKERS="$BLOCKERS gradlew_bat_missing"
fi

aj_n=0
aj_names=""
if [ -d "$RUN_DIR/mods" ]; then
    aj_n="$(mt_autojs_jar_count "$RUN_DIR" 2>/dev/null || echo 0)"
    stats="$(mt_mods_stats "$RUN_DIR" 2>/dev/null || true)"
    aj_names="$(printf '%s' "$stats" | cut -d' ' -f4-)"
fi
allow_no_autojs="${MT_ALLOW_NO_AUTOJS:-0}"
# 2026-11-01 用户裁决：AutoJS 就绪判据放宽至所有版本 —— 不再按版本白名单（原 mt_line_has_autojs）。
# 2026-11-02 fabric 例外：fabric 的 AutoJS 是**条件依赖**，由 -PautojsTestMod=1 以 modRuntimeOnly 注入
#   运行期 classpath，**永远不进 run/<side>/mods**（制品是 intermediary 命名空间，手放进 mods 会
#   NoSuchMethodError；见 scripts/test/TESTING-RULES-OVERVIEW.md（已删除 2026-11-05）:191 规则 6）。
#   ⇒ fabric 侧的就绪判据 = 「mods 里 AutoJS jar 在位」**或**「本次启动已注入（AJ_ARG 非空）」。
fab_aj_injected=0
if mt_is_fabric "$VERSION" && [ "${FAB_SIDE:-}" = "client" ] && [ -n "$AJ_ARG" ] && [ "$allow_no_autojs" != "1" ]; then
    fab_aj_injected=1
fi
# 双保险（fail-closed）：mods 里真出现 AutoJS jar 时，-PautojsTestMod=1 会再注入同 id 一份
# ⇒ 启动期 duplicate mod 崩服。这里直接 BLOCKED（exit 11），不让它跑到崩服才暴露。
if mt_is_fabric "$VERSION" && [ "${aj_n:-0}" -gt 0 ] && [ -n "$AJ_ARG" ]; then
    BLOCKERS="$BLOCKERS fabric_autojs_duplicate"
fi
if [ "${aj_n:-0}" -gt 0 ] || [ "$fab_aj_injected" = 1 ]; then autojs_deployed=1; else autojs_deployed=0; fi
if [ "$fab_aj_injected" = 1 ]; then
    aj_names="${aj_names}（fabric：$AJ_ARG → AutoJS-${MT_FABRIC_VERSION}-1.0.0.jar 走 mavenLocal 运行期 classpath，不在 mods/）"
fi
# 有 jar（或 fabric 已注入）→ AutoJS 为主判据；都没有（或 MT_ALLOW_NO_AUTOJS=1）→ 纯 marker。
# 只在主判据生效时才真的去探 MCP（否则白等 5s 一轮）。
if [ "$autojs_deployed" = 1 ] && [ "$allow_no_autojs" != "1" ]; then
    probe_enabled=1
    if [ "$fab_aj_injected" = 1 ]; then
        ready_by="AutoJS GET $BASE_URL/health==ok + tools/call status(client available=true)（fabric 通道由 $AJ_ARG 注入，mods/ 无 jar 属正常；marker 仅兜底）"
    else
        ready_by="AutoJS GET $BASE_URL/health==ok + tools/call status(client available=true)（主判据；marker 仅兜底）"
    fi
else
    probe_enabled=0
    ready_by="纯 marker（$MODS_LABEL 无 AutoJS jar，或 MT_ALLOW_NO_AUTOJS=1）"
fi
# marker 恒可作为兜底通道（1=允许）：AutoJS 未就绪时 READY-by-marker，并在输出里标明实际通道。
marker_fallback=1

residue_note=""
cim_ok=1
if rows="$(mt_cim_java_rows 2>/dev/null)"; then
    while IFS=$'\t' read -r pid cmd; do
        [ -n "$pid" ] || continue
        if launch_cmdline_matches "$cmd"; then
            BLOCKERS="$BLOCKERS same_line_running"
            residue_note="$residue_note PID $pid；"
            break
        fi
    done <<< "$rows"
else
    cim_ok=0
fi

# 额外 Gradle 前置参数（MT_GRADLE_PRE_ARGS，形如 -Dorg.gradle.java.installations.paths=...）：
# 必须落在任务名之前（Gradle 只从命令行前段取 -D）。用于规避本机环境问题——例如
# 缓存里某个 auto-detect 到的 JDK 带 Low 完整性标签，用它拉起的游戏进程写不了 run/<v>/logs
# （2026-11-02 实测：1.20.1 报 logs\latest.log 拒绝访问，1.21.1 正常）。
PRE_ARGS=""
[ -n "${MT_GRADLE_PRE_ARGS:-}" ] && PRE_ARGS="${MT_GRADLE_PRE_ARGS} "
CMD_DESC=""
if [ -n "$QUICKPLAY" ]; then
    CMD_DESC="gradlew.bat ${PRE_ARGS}:$PROJECT:$Gradle_TASK --console=plain -Pquickplay=$QUICKPLAY"
else
    CMD_DESC="gradlew.bat ${PRE_ARGS}:$PROJECT:$Gradle_TASK --console=plain"
fi
# fabric 默认**不带** -PautojsTestMod（AutoJS 是条件依赖，默认注入通道是 RCON）；
# 只有显式 --enable-autojs 时才追加；其它三线 AJ_ARG 恒为空 ⇒ 命令逐字节不变。
[ -n "$AJ_ARG" ] && CMD_DESC="$CMD_DESC $AJ_ARG"
# fabric 条件依赖里的 Sodium/Iris：版本不匹配会崩客户端，手工验证时用 MT_TEST_SODIUM=false 关掉。
# 其它三线传了必报错（那三线没有 testSodium 属性，静默忽略会让人误以为生效）。
SODIUM_ARG=""
if [ -n "${MT_TEST_SODIUM:-}" ]; then
    if ! mt_is_fabric "$VERSION"; then
        echo "用法错误：MT_TEST_SODIUM 只对 fabric 线（fabric-<mcver>）生效（$VERSION 没有 testSodium 属性）" >&2
        exit 2
    fi
    case "$MT_TEST_SODIUM" in
        true|false) SODIUM_ARG="-PtestSodium=$MT_TEST_SODIUM"; CMD_DESC="$CMD_DESC $SODIUM_ARG" ;;
        *) echo "用法错误：MT_TEST_SODIUM 只接受 true | false（收到：$MT_TEST_SODIUM）" >&2; exit 2 ;;
    esac
fi
# fabric 条件依赖里的 Iris（光影）：默认**装**（钉版 iris 1.8.8 + sodium 0.6.13，dev 可跑）。
# 需要临时排除时才 MT_TEST_IRIS=false（例：排查 sodium 渲染栈、或临时把钉版改回
# iris 1.8.14-beta.1 —— 那个版本在 dev 必崩，取证见
# docs/features/fabric-1211-live-test-plan.md §11）。其它四线没有 testIris 属性，传了必报错。
if mt_is_fabric "$VERSION"; then
    case "${MT_TEST_IRIS:-true}" in
        true) : ;;
        false) IRIS_ARG="-PtestIris=false"; CMD_DESC="$CMD_DESC $IRIS_ARG" ;;
        *) echo "用法错误：MT_TEST_IRIS 只接受 true | false（收到：$MT_TEST_IRIS）" >&2; exit 2 ;;
    esac
fi
TS="$(date +%Y%m%d-%H%M%S)"
LOG="$LOG_DIR/$VERSION-$TS.log"

if [ "$DRY" = 1 ]; then
    mt_launch "DRY-RUN — 未启动任何进程"
    mt_detail "version      = $VERSION"
    mt_detail "project      = :$PROJECT"
    mt_detail "loader       = $(mt_loader "$VERSION")"
    mt_detail "daemon_jvm   = ${DAEMON_JDK:-<未找到>}（Gradle daemon JVM，需 ≥21；四线统一）"
    mt_detail "toolchain    = $TOOLCHAIN_VER（Gradle 自动发现；本机需在位）"
    mt_detail "run_dir      = $RUN_DIR"
    mt_detail "launch_mode  = $LAUNCH_MODE"
    mt_detail "cmd          = mt_cmdwin \"$CMD_DESC\"（cwd=$MT_REPO_ROOT，JAVA_HOME=$(mt_to_win "${DAEMON_JDK:-<none>}")）"
    mt_detail "log          = $LOG"
    mt_detail "pid_file     = $PIDFILE"
    mt_detail "timeout      = ${TIMEOUT}s（等 AutoJS 客户端侧 READY）"
    mt_detail "wait_world   = ${WAIT_WORLD}s（READY 后额外等世界 marker，0=不等）"
    mt_detail "autojs_jar   = ${aj_n} 个${aj_names}"
    mt_detail "test_sodium  = ${SODIUM_ARG:-<未传>}（fabric 条件依赖 Sodium/Iris 的开关）"
    mt_detail "autojs_line  = autojs_deployed=$autojs_deployed / probe_enabled=$probe_enabled / marker_fallback=$marker_fallback / fabric_injected=$fab_aj_injected（MT_ALLOW_NO_AUTOJS=$allow_no_autojs；判据：$MODS_LABEL 里 AutoJS jar 是否在位$( [ "$fab_aj_injected" = 1 ] && printf '，或 fabric 已由 -PautojsTestMod=1 注入' )）"
    mt_detail "ready_by     = $ready_by"
    mt_detail "world_marker = $(launch_marker_name)"
    mt_detail "cim_scan     = $( [ "$cim_ok" = 1 ] && printf 'CIM 可用，同线残留=%s' "${residue_note:-无}" || printf 'CIM 查询失败（无法判残留）' )"
    if [ -n "$BLOCKERS" ]; then
        mt_detail "blockers     =$BLOCKERS（真启动会 exit 11 BLOCKED）"
    else
        mt_detail "blockers     = none"
    fi
    exit 0
fi

# ── 真启动前的阻塞判定 ───────────────────────────────────────────────────
if [ -z "$DAEMON_JDK" ]; then
    mt_launch_err "BLOCKED — 未找到 Gradle daemon JVM（需 ≥21）：设 MT_GRADLE_DAEMON_JAVA_HOME，或装 JDK 21+（Zulu 21/25 均可）"
    exit 11
fi
if [ ! -d "$RUN_DIR" ]; then
    mt_launch_err "BLOCKED — $( [ -n "$FAB_SIDE" ] && printf '%s' "$RUN_DIR" || printf 'run/%s' "$VERSION" ) 不存在（先让 Gradle 解析一次项目：mt_cmdwin gradlew.bat :$PROJECT:build 或直接跑一次 $Gradle_TASK）"
    exit 11
fi
if [ ! -f "$MT_REPO_ROOT/gradlew.bat" ]; then
    mt_launch_err "BLOCKED — 缺 gradlew.bat（$MT_REPO_ROOT/gradlew.bat）"
    exit 11
fi
if [ "$cim_ok" = 0 ]; then
    mt_launch_err "BLOCKED — CIM 查询失败（pwsh -NoProfile Get-CimInstance 不可用），无法判断同线是否已在运行"
    exit 11
fi
if [ -n "$residue_note" ]; then
    mt_launch_err "BLOCKED — 同一线客户端已在运行：$residue_note"
    mt_launch_err "  先收停：bash scripts/test/win/stop.sh --version $VERSION"
    exit 11
fi
# 其它线在跑只提示（全队只有一个 Gradle 并发位）
while IFS=$'\t' read -r pid cmd; do
    [ -n "$pid" ] || continue
    for ov in $MT_ALL_VERSIONS; do
        [ "$ov" = "$VERSION" ] && continue
        if mt_cmdline_matches_version "$ov" "$cmd"; then
            mt_launch_err "WARN — 其它线（$ov）客户端也在跑：PID $pid；Gradle 并发位只有一个"
            break
        fi
    done
done <<< "$rows"

# ── 启动 ─────────────────────────────────────────────────────────────────
mkdir -p "$LOG_DIR" 2>/dev/null || {
    mt_launch_err "ERROR — 无法创建日志目录 $LOG_DIR"
    exit 2
}

# fabric：把「本次启动是否真的注入了 AutoJS」落到 run/<side>/.mt_autojs_injected。
# PowerShell 侧通道就绪判定（Mt.Channel.psm1 Test-MtChannelAutoJsDeployed）读它 ——
# 因为 mods/ 里永远不会有 AutoJS jar。stop.sh 收停时删除，保证与进程同生命周期。
if mt_is_fabric "$VERSION"; then
    if mt_fab_autojs_state_write "${FAB_SIDE:-client}" >/dev/null 2>&1; then
        mt_detail "autojs_state = $(mt_fab_autojs_state_path "${FAB_SIDE:-client}")"
    else
        mt_detail "autojs_state = 写入失败（PowerShell 侧判定将退回 marker 兜底）"
    fi
fi

mt_launch "starting $VERSION (:$PROJECT) …"
mt_detail "launch_mode  = $LAUNCH_MODE"
mt_detail "cmd          = mt_cmdwin \"$CMD_DESC\""
mt_detail "log          = $LOG"
mt_detail "daemon_jvm   = $DAEMON_JDK（≥21；toolchain $TOOLCHAIN_VER 由 Gradle 自动发现）"

trap 'echo; echo "LAUNCH: INTERRUPTED — 本脚本退出；游戏进程不受影响。" >&2; echo "  收停：bash scripts/test/win/stop.sh --version '"$VERSION"'" >&2; exit 130' INT TERM

OLD_PWD="$PWD"
cd "$MT_REPO_ROOT" || { mt_launch_err "ERROR — 无法进入仓库根 $MT_REPO_ROOT"; exit 2; }
export JAVA_HOME="$(mt_to_win "$DAEMON_JDK")"
export PATH="$DAEMON_JDK/bin:$PATH"
export MT_WIN_LAUNCH=1

BG_PID=""
if [ "$LAUNCH_MODE" = "start" ]; then
    # 真正脱离控制台（拿不到 PID：pidfile 里只能写"未知"，由 CIM 判线兜底）
    mt_cmdwin "start \"\" /B cmd /c \"$CMD_DESC > \"$LOG\" 2>&1\"" >/dev/null 2>&1 </dev/null &
    BG_PID=""
    : > "$PIDFILE" 2>/dev/null || true
else
    mt_cmdwin "$CMD_DESC" >"$LOG" 2>&1 </dev/null &
    BG_PID=$!
    winpid="$(cat "/proc/$BG_PID/winpid" 2>/dev/null | tr -d '\r\n')"
    printf '%s\n' "${winpid:-$BG_PID}" > "$PIDFILE" 2>/dev/null || true
fi
cd "$OLD_PWD" 2>/dev/null || cd "$MT_REPO_ROOT"

if [ -n "$BG_PID" ]; then
    mt_detail "wrapper_pid  = ${winpid:-$BG_PID}（Windows PID；pidfile $PIDFILE）"
else
    mt_detail "wrapper_pid  = 未知（launch_mode=start 不返回 PID；stop.sh 靠 CIM 判线）"
fi

# ── 轮询 ─────────────────────────────────────────────────────────────────
deadline=$(( $(date +%s) + TIMEOUT ))
aj_ok=0
aj_detail=""
health_seen=0
marker=""
last_probe=0

while [ "$(date +%s)" -lt "$deadline" ]; do
    now="$(date +%s)"
    [ -z "$marker" ] && launch_marker_hit "$LOG" && marker="$(launch_marker_name)"

    [ "$(mt_aj_health_code 2)" = "200" ] && health_seen=1

    if [ "$probe_enabled" = 1 ] && [ $((now - last_probe)) -ge 5 ]; then
        last_probe=$now
        if aj_detail="$(mt_aj_probe 2>/dev/null)" && [ -n "$aj_detail" ]; then
            aj_ok=1
            break
        fi
    fi

    if [ -n "$BG_PID" ] && ! kill -0 "$BG_PID" 2>/dev/null; then
        if [ "$probe_enabled" = 1 ] && [ "$aj_ok" = 0 ]; then
            if aj_detail="$(mt_aj_probe 2>/dev/null)" && [ -n "$aj_detail" ]; then aj_ok=1; fi
        fi
        if [ "$aj_ok" = 1 ] || { [ -n "$marker" ] && [ "$marker_fallback" = 1 ]; }; then
            break
        fi
        mt_launch_err "FAIL — 启动进程已退出且未就绪（wrapper PID ${winpid:-$BG_PID}；health_seen=$health_seen）"
        echo "── 日志尾部 40 行（$LOG）──" >&2
        tail -n 40 "$LOG" >&2 2>/dev/null || true
        exit 1
    fi

    if grep -qE 'BUILD FAILED|FAILURE: Build failed' "$LOG" 2>/dev/null; then
        mt_launch_err "FAIL — Gradle 构建失败"
        echo "── 日志尾部 40 行（$LOG）──" >&2
        tail -n 40 "$LOG" >&2 2>/dev/null || true
        exit 1
    fi

    # AutoJS 为主判据时，marker 只有在「端点从头到尾没响应（health_seen=0）」时才允许提前收工；
    # 端点已响应说明通道在，继续等 status available=true（否则 AutoJS 判据永远得不到执行）。
    if [ -n "$marker" ] && [ "$marker_fallback" = 1 ] \
       && { [ "$probe_enabled" != 1 ] || [ "$health_seen" = 0 ]; }; then
        break
    fi

    sleep 2
done

if [ "$aj_ok" != 1 ]; then
    if [ -n "$marker" ] && [ "$marker_fallback" = 1 ]; then
        mt_launch "READY — $VERSION（AutoJS 未就绪，marker 兜底：$marker）"
        mt_detail "autojs      = 未响应（marker_fallback=1：MT_ALLOW_NO_AUTOJS=$allow_no_autojs / aj_n=$aj_n）"
    else
        echo "── 日志尾部 40 行（$LOG）──" >&2
        tail -n 40 "$LOG" >&2 2>/dev/null || true
        if [ "$probe_enabled" = 1 ] && [ "$health_seen" = 0 ]; then
            mt_launch_err "ERROR — ${TIMEOUT}s 内 AutoJS 端点从未响应 health（$BASE_URL/health），且无世界 marker"
            mt_launch_err "  $MODS_LABEL 有 AutoJS jar，但端点不可用；逃生阀：MT_ALLOW_NO_AUTOJS=1（需日志 marker 兜底）"
            if [ "$KILL_ON_TIMEOUT" = 1 ]; then bash "$WIN_DIR/stop.sh" --version "$VERSION" >&2 || true; fi
            exit 2
        fi
        mt_launch_err "TIMEOUT — ${TIMEOUT}s 内 AutoJS 客户端侧未就绪（health_seen=$health_seen）"
        if [ "$health_seen" = 1 ]; then
            mt_launch_err "  health 已响应但 status 未报 client available=true（可能仍在加载世界；可 --timeout 放宽）"
        fi
        if [ "$KILL_ON_TIMEOUT" = 1 ]; then
            mt_launch_err "  --kill-on-timeout → 调 stop.sh 收停"
            bash "$WIN_DIR/stop.sh" --version "$VERSION" >&2 || true
        else
            mt_launch_err "  手动收停：bash scripts/test/win/stop.sh --version $VERSION"
        fi
        exit 12
    fi
else
    mt_launch "READY — $VERSION 客户端就绪（AutoJS 客户端侧 available=true）"
    mt_detail "autojs      = $aj_detail"
    mt_detail "health      = ok（HTTP 200 @ $BASE_URL/health）"
fi

# ── 世界就绪（marker）─────────────────────────────────────────────────────
if [ -z "$marker" ] && [ "$WAIT_WORLD" -gt 0 ]; then
    world_deadline=$(( $(date +%s) + WAIT_WORLD ))
    while [ "$(date +%s)" -lt "$world_deadline" ]; do
        if launch_marker_hit "$LOG"; then marker="$(launch_marker_name)"; break; fi
        sleep 2
    done
fi
if [ -n "$marker" ]; then
    mt_launch "WORLD_READY — 世界已加载（marker: $marker）"
else
    if [ "$WAIT_WORLD" -gt 0 ]; then
        mt_launch_err "WARN — --wait-world ${WAIT_WORLD}s 内未见世界 marker（仍为 READY，exit 0）"
    else
        mt_detail "world       = 未命中 marker（需要世界就用默认 -Pquickplay，或 --wait-world N）"
    fi
fi

game_pid=""
if [ -d "$RUN_DIR" ] && rows2="$(mt_cim_java_rows 2>/dev/null)"; then
    while IFS=$'\t' read -r p c; do
        [ -n "$p" ] || continue
        if launch_cmdline_matches "$c"; then game_pid="$p"; break; fi
    done <<< "$rows2"
fi
mt_detail "game_pid    = ${game_pid:-未找到（可能仍在启动早期；用 CIM 判线）}"
mt_detail "log         = $LOG"
mt_detail "pid_file    = $PIDFILE"
mt_detail "收停        = bash scripts/test/win/stop.sh --version $VERSION"
exit 0
