#!/usr/bin/env bash
# ============================================================================
# scripts/test/linux/launch.sh — Astral Dice 测试台 · Linux 侧最小启动路径
#
# 语义 = `scripts/test/win/mt.sh --phase launch` 的最小集：启动 runClient → 轮询就绪 → 可选 AutoJS 探活。
# 不复刻 Windows 侧的进程防撞锁/世界清场/OP-禁AI 闸门（见 README「与 Windows 台差异」）。
#
# 用法：
#   scripts/test/linux/launch.sh --version <ver> [--timeout 600] [--wait-world 0]
#                               [--quickplay <世界名>] [--kill-on-timeout]
#                               [--log-dir DIR] [--dry-run]
#
# 就绪判据（Linux 与 Windows 不同；2026-10-31 Lead 1.21.1 实机裁决）：
#   * 主判据：AutoJS 客户端侧就绪 = GET /health 返回 200 且 MCP initialize + tools/call status
#     报 sides[] 里 side=="client" 且 available=true → LAUNCH: READY，exit 0。
#   * 辅助信息：日志 marker（1.21.1/1.20.1/fabric: "Total time to load game and open world was"；
#     26.1.2: "logged in with entity id" + "Loaded <N> advancements"）命中时额外打
#     LAUNCH: WORLD_READY，但**不作为 READY 的必要条件**。
#   * MT_ALLOW_NO_AUTOJS=1：允许 AutoJS 未就绪时用日志 marker 兜底判 READY；run/mods 无 AutoJS jar
#     的线也自动允许 marker 兜底（无法探测）。两套都没有才 exit 2（通道不可用）/12（就绪超时）。
#   * 原因：Linux dev 客户端默认停在标题画面、不自动开世界（1.21.1 实测 ~97s 后 AutoJS 就绪：
#     health=ok、status registered=true available=true state="client running, no world loaded"）；
#     Windows 测试台总用 quickplay 开世界，才能认 "Total time to load game and open world was"。
#
# 退出码：0=READY；1=启动失败/进程提前退出；2=ERROR（AutoJS 通道不可用且无 marker / 用法错误）；
#         11=BLOCKED（同线已有客户端 / 缺 JDK / run 目录不存在）；12=TIMEOUT；130=用户中断
#
# 启动顺序（Lead 2026-10-31 裁决）：
#   1) run/start-<ver>.sh 存在且可执行 → 直接调用（gradle-launcher 产出）
#   2) 否则回退 bash ./gradlew :<project>:runClient --console=plain
#   Gradle daemon JVM 统一用 JDK 21（Gradle 要求 daemon JVM ≥21，按线切 17 会在配置期崩）；
#   子项目 toolchain（forge/fabric=17、neoforge-1.21.1=21、neoforge-26.1.2=25）由 Gradle 自动发现。
#   run/start-<ver>.sh 内部自己设置 JAVA_HOME（以其为准）；回退 gradlew 路径由本脚本注入 21。
#
# 日志：<log-dir>/<ver>-<ts>.log（绝不写 run/ 下既有日志）
# PID ：<run-dir>/linux-run.pid（stop.sh 优先按它收停）
# 注意：游戏用 setsid 独立进程组启动，本脚本退出/Ctrl-C 不会杀游戏；
#       收停请用 scripts/test/linux/stop.sh --version <ver>。
# ============================================================================
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$DIR/../../.." && pwd)"
CONF="$REPO_ROOT/scripts/test/mt.conf"
LOG_DIR="${MT_LINUX_LOG_DIR:-$DIR/logs}"

VERSION=""
TIMEOUT="${MT_LAUNCH_READY_TIMEOUT_SEC:-600}"
WAIT_WORLD="${MT_LAUNCH_WAIT_WORLD_SEC:-0}"
QUICKPLAY=""
KILL_ON_TIMEOUT=0
DRY=0

usage() {
    cat <<'USAGE'
用法：
  scripts/test/linux/launch.sh --version <ver> [--timeout 600] [--wait-world 0]
                              [--quickplay <世界名>] [--kill-on-timeout]
                              [--log-dir DIR] [--dry-run]

  --version <ver>   1.21.1 | 1.20.1 | 26.1.2 | fabric-1.20.1（必填）
  --timeout <sec>   等待标题画面 READY（AutoJS 客户端侧就绪）上限，默认 600
  --wait-world <秒> READY 后再等世界就绪 marker 的秒数，默认 0=不等待（仅打印已命中的 marker）
  --quickplay <名>  透传 -Pquickplay=<名>（经 run/start-*.sh 或 gradlew；build.gradle 四线已配置，
                    26.1.2 用等号形式；本脚本传参未实机验证）
  --kill-on-timeout 超时后自动调 stop.sh 收停（默认只提示，不杀）
  --log-dir <dir>   日志目录，默认 <repo>/scripts/test/linux/logs
  --dry-run         只打印解析结果与将执行的命令，不启动

就绪判据：AutoJS 客户端侧 health=200 + status(client available=true) → READY；
           日志 marker 只作辅助信息（LAUNCH: WORLD_READY）；MT_ALLOW_NO_AUTOJS=1 时可用 marker 兜底。
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --version)       VERSION="${2:-}"; shift 2 ;;
        --version=*)     VERSION="${1#*=}"; shift ;;
        --timeout)       TIMEOUT="${2:-}"; shift 2 ;;
        --timeout=*)     TIMEOUT="${1#*=}"; shift ;;
        --wait-world)    WAIT_WORLD="${2:-}"; shift 2 ;;
        --wait-world=*)  WAIT_WORLD="${1#*=}"; shift ;;
        --quickplay)     QUICKPLAY="${2:-}"; shift 2 ;;
        --quickplay=*)   QUICKPLAY="${1#*=}"; shift ;;
        --kill-on-timeout) KILL_ON_TIMEOUT=1; shift ;;
        --log-dir)       LOG_DIR="${2:-}"; shift 2 ;;
        --log-dir=*)     LOG_DIR="${1#*=}"; shift ;;
        --dry-run)       DRY=1; shift ;;
        -h|--help)       usage; exit 0 ;;
        *) echo "用法错误：未知参数 $1（-h 看用法）" >&2; exit 2 ;;
    esac
done

if [ -z "$VERSION" ]; then
    echo "用法错误：必须给 --version（1.21.1 | 1.20.1 | 26.1.2 | fabric-1.20.1）；-h 看用法" >&2
    exit 2
fi
case "$VERSION" in
    1.21.1)        PROJECT="neoforge-1.21.1"; TOOLCHAIN_VER=21 ;;
    1.20.1)        PROJECT="forge-1.20.1";    TOOLCHAIN_VER=17 ;;
    26.1.2)        PROJECT="neoforge-26.1.2"; TOOLCHAIN_VER=25 ;;
    fabric-1.20.1) PROJECT="fabric-1.20.1";   TOOLCHAIN_VER=17 ;;
    *) echo "用法错误：未知 --version $VERSION" >&2; exit 2 ;;
esac
case "$TIMEOUT" in
    ''|*[!0-9]*) echo "用法错误：--timeout 必须是整数秒" >&2; exit 2 ;;
esac
case "$WAIT_WORLD" in
    ''|*[!0-9]*) echo "用法错误：--wait-world 必须是整数秒（0=不等待）" >&2; exit 2 ;;
esac

RUN_DIR="$REPO_ROOT/run/$VERSION"

conf_get() {
    local key="$1" def="${2:-}" v=""
    if [ -f "$CONF" ]; then
        v="$(grep -E "^[[:space:]]*${key}=" "$CONF" 2>/dev/null | tail -n 1 \
             | sed -E 's/^[^=]*=//' | tr -d '\r' | sed -E 's/^"//; s/"$//')"
    fi
    if [ -n "$v" ]; then printf '%s' "$v"; else printf '%s' "$def"; fi
}
scan_clients() {
    local p pid cwd
    for p in /proc/[0-9]*; do
        pid="${p#/proc/}"
        [ "$pid" = "$$" ] && continue
        [ -r "$p/comm" ] || continue
        [ "$(cat "$p/comm" 2>/dev/null || true)" = "java" ] || continue
        cwd="$(readlink "$p/cwd" 2>/dev/null || true)"
        case "$cwd" in
            "$REPO_ROOT"/run/*) printf '%s|%s\n' "$pid" "$cwd" ;;
        esac
    done
}
log_marker_hit() {   # 仅作辅助信息：世界是否已加载/进入
    local log="$1"
    case "$VERSION" in
        26.1.2)
            grep -q 'logged in with entity id' "$log" 2>/dev/null \
                && grep -qE 'Loaded [0-9]+ advancements' "$log" 2>/dev/null
            ;;
        *)
            grep -q 'Total time to load game and open world was' "$log" 2>/dev/null
            ;;
    esac
}
log_marker_name() {
    case "$VERSION" in
        26.1.2) printf '%s' '"logged in with entity id" + "Loaded <N> advancements"' ;;
        *)      printf '%s' '"Total time to load game and open world was"' ;;
    esac
}
tail_log() { echo "── 日志尾部 40 行（$1）──" >&2; tail -n 40 "$1" >&2 2>/dev/null || true; }

# AutoJS 客户端侧探测：health==ok(200) 且 MCP initialize + tools/call status 报 client available=true。
# 成功：stdout 打印 "health=ok state=... registered=... available=..."，exit 0；失败 exit 1。
aj_probe() {
    local endpoint="$1" base_url hdrf initf callf sid code health_body detail
    local -a sid_args=()
    command -v python3 >/dev/null 2>&1 || return 1
    base_url="${endpoint%/mcp}"
    health_body="$(curl -fsS -m 2 "$base_url/health" 2>/dev/null)" || health_body=""
    if [ "$health_body" != "ok" ]; then
        return 1
    fi
    hdrf="$(mktemp -t aj-hdr.XXXXXX)" || return 1
    initf="$(mktemp -t aj-init.XXXXXX)" || { rm -f "$hdrf"; return 1; }
    callf="$(mktemp -t aj-call.XXXXXX)" || { rm -f "$hdrf" "$initf"; return 1; }
    code="$(curl -sS -m 5 -D "$hdrf" -o "$initf" -w '%{http_code}' -X POST "$endpoint" \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json, text/event-stream' \
        --data '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"linux-launch-sh","version":"1.0.0"}}}' 2>/dev/null)" || code="000"
    if [ "$code" != "200" ]; then
        rm -f "$hdrf" "$initf" "$callf"
        return 1
    fi
    sid="$(awk -F': ' 'tolower($1)=="mcp-session-id"{v=$2} END{if(v!="")print v}' "$hdrf" | tr -d '\r')"
    if [ -n "$sid" ]; then sid_args=(-H "Mcp-Session-Id: $sid"); fi
    curl -sS -m 5 -o /dev/null -X POST "$endpoint" \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json, text/event-stream' \
        "${sid_args[@]}" \
        --data '{"jsonrpc":"2.0","method":"notifications/initialized"}' >/dev/null 2>&1 || true
    code="$(curl -sS -m 8 -o "$callf" -w '%{http_code}' -X POST "$endpoint" \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json, text/event-stream' \
        "${sid_args[@]}" \
        --data '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"status","arguments":{}}}' 2>/dev/null)" || code="000"
    if [ "$code" = "200" ] && detail="$(python3 - "$initf" "$callf" <<'PY'
import json, sys

def load_json(path):
    txt = open(path, encoding='utf-8', errors='replace').read()
    try:
        return json.loads(txt)
    except Exception:
        pass
    obj = None
    for line in txt.splitlines():
        line = line.strip()
        if line.startswith('data:'):
            try:
                obj = json.loads(line[5:].strip())
            except Exception:
                continue
    return obj

call = load_json(sys.argv[2])
if not isinstance(call, dict):
    sys.exit(1)
res = call.get('result') or {}
content = res.get('content') or []
text = ''
for c in content:
    if isinstance(c, dict) and c.get('type') == 'text':
        text = c.get('text') or ''
        break
if not text:
    sys.exit(1)
try:
    data = json.loads(text)
except Exception:
    sys.exit(1)
sides = data.get('sides') or []
client = next((s for s in sides if s.get('side') == 'client'), None)
if not client:
    sys.exit(1)
available = client.get('available')
registered = client.get('registered')
state = client.get('state', '')
if available is not True:
    sys.exit(1)
print('state="%s" registered=%s available=%s' % (state, str(registered).lower(), str(available).lower()))
PY
)"; then
        rm -f "$hdrf" "$initf" "$callf"
        printf 'health=%s %s\n' "$health_body" "$detail"
        return 0
    fi
    rm -f "$hdrf" "$initf" "$callf"
    return 1
}

# Gradle daemon JVM 必须 ≥21（实测：JAVA_HOME=java-17 时配置期报
#   "Dependency requires at least JVM runtime version 21. This build uses a Java 17 JVM."）。
DAEMON_JDK="${MT_GRADLE_DAEMON_JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
if [ ! -x "$DAEMON_JDK/bin/java" ]; then
    echo "LAUNCH: BLOCKED — 未找到 Gradle daemon JVM（需 ≥21）：$DAEMON_JDK" >&2
    exit 11
fi
JDK_HOME="$DAEMON_JDK"
if [ ! -d "$RUN_DIR" ]; then
    echo "LAUNCH: BLOCKED — run/$VERSION 不存在（先让 Gradle 解析一次项目）" >&2
    exit 11
fi

# AutoJS jar 探测（大小写不敏感；只认 .jar 结尾，排除 .jar.bak-*/.jar.reobf-*）。
aj_n=0
aj_names=""
for jar in "$RUN_DIR/mods"/*.jar; do
    [ -e "$jar" ] || continue
    b="$(basename "$jar")"
    case "$b" in
        *[Aa][Uu][Tt][Oo][Jj][Ss]*.jar) aj_n=$((aj_n + 1)); aj_names="$aj_names $b" ;;
    esac
done
endpoint="$(conf_get MT_AUTOJS_ENDPOINT "${MT_AUTOJS_ENDPOINT:-http://127.0.0.1:25585/mcp}")"
base_url="${endpoint%/mcp}"
allow_no_autojs="${MT_ALLOW_NO_AUTOJS:-0}"
# marker 兜底是否允许：显式逃生阀，或该线根本没有 AutoJS jar（无法探测）。
if [ "$aj_n" -eq 0 ] || [ "$allow_no_autojs" = "1" ]; then marker_fallback=1; else marker_fallback=0; fi

residue="$(scan_clients || true)"
if [ -n "$residue" ]; then
    while IFS='|' read -r pid cwd; do
        case "$cwd" in
            "$RUN_DIR"|"$RUN_DIR"/*)
                echo "LAUNCH: BLOCKED — 同一线客户端已在运行：PID $pid（cwd=$cwd）" >&2
                echo "  先收停：bash scripts/test/linux/stop.sh --version $VERSION" >&2
                exit 11
                ;;
            *) echo "LAUNCH: WARN — 其它线客户端在跑：PID $pid（cwd=$cwd）；全队只有一个 gradle 并发位" >&2 ;;
        esac
    done <<< "$residue"
fi

# 启动方式：优先 run/start-<ver>.sh（存在且可执行，且它会把 "$@" 原样传给 gradlew），否则回退 gradlew。
CMD=()
START_SRC=""
if [ -f "$REPO_ROOT/run/start-$VERSION.sh" ] && [ -x "$REPO_ROOT/run/start-$VERSION.sh" ]; then
    CMD=("$REPO_ROOT/run/start-$VERSION.sh")
    START_SRC="run/start-$VERSION.sh（可执行，直接调用）"
else
    if [ -f "$REPO_ROOT/run/start-$VERSION.sh" ]; then
        echo "LAUNCH: WARN — run/start-$VERSION.sh 存在但无 +x，回退 gradlew（NTFS/FUSE 挂载常见）" >&2
    fi
    CMD=(bash ./gradlew ":$PROJECT:runClient" --console=plain)
    START_SRC="bash ./gradlew :$PROJECT:runClient --console=plain"
fi
if [ -n "$QUICKPLAY" ]; then
    # build.gradle 四线都实现 -Pquickplay：1.21.1/1.20.1/fabric → --quickPlaySingleplayer <名>；
    # 26.1.2 必须等号形式 --quickPlaySingleplayer=<名>（见 neoforge-26.1.2/build.gradle）。本脚本传参未实机验证。
    CMD+=("-Pquickplay=$QUICKPLAY")
fi

TS="$(date +%Y%m%d-%H%M%S)"
LOG="$LOG_DIR/$VERSION-$TS.log"
PIDFILE="$RUN_DIR/linux-run.pid"

if [ "$DRY" = 1 ]; then
    echo "LAUNCH: DRY-RUN — 未启动任何进程"
    echo "  version      = $VERSION"
    echo "  project      = :$PROJECT"
    echo "  daemon_jvm   = $JDK_HOME（Gradle daemon JVM，需 ≥21；四线统一）"
    echo "  toolchain    = $TOOLCHAIN_VER（Gradle 自动发现，本机需在位）"
    echo "  run_dir      = $RUN_DIR"
    echo "  start_source = $START_SRC"
    echo "  cmd          = ${CMD[*]}"
    echo "  log          = $LOG"
    echo "  pid_file     = $PIDFILE"
    echo "  timeout      = ${TIMEOUT}s（等 AutoJS 客户端侧 READY；旧日志 marker 不作必要条件）"
    echo "  wait_world   = ${WAIT_WORLD}s（READY 后额外等世界 marker，0=不等）"
    echo "  autojs_jar   = ${aj_n} 个 ${aj_names:-（无 → 允许 marker 兜底）}"
    echo "  ready_by     = AutoJS health=200 + status(client available=true)；marker 仅辅助"
    exit 0
fi

mkdir -p "$LOG_DIR"
echo "LAUNCH: starting $VERSION (:$PROJECT) …"
echo "  start_source = $START_SRC"
echo "  cmd          = ${CMD[*]}"
echo "  log          = $LOG"
echo "  daemon_jvm   = $JDK_HOME（Gradle daemon JVM，≥21；toolchain $TOOLCHAIN_VER 自动发现）"

trap 'echo; echo "LAUNCH: INTERRUPTED — 本脚本退出；游戏为 setsid 独立进程组，不受影响。" >&2; echo "  收停：bash scripts/test/linux/stop.sh --version '"$VERSION"'" >&2; exit 130' INT TERM

(
    cd "$REPO_ROOT"
    export JAVA_HOME="$JDK_HOME"
    export PATH="$JDK_HOME/bin:$PATH"
    export MT_LINUX_LAUNCH=1
    setsid nohup "${CMD[@]}" >"$LOG" 2>&1 < /dev/null &
    echo $! > "$PIDFILE"
)
WRAPPER_PID="$(cat "$PIDFILE" 2>/dev/null || echo '')"
if [ -z "$WRAPPER_PID" ]; then
    echo "LAUNCH: FAIL — 未能记录启动 PID" >&2
    exit 1
fi
echo "  wrapper_pid  = $WRAPPER_PID（setsid 独立进程组；PID 文件 $PIDFILE）"

# ── 轮询：AutoJS 为主判据；日志 marker 只记录，不作 READY 必要条件 ──────────────
deadline=$(( $(date +%s) + TIMEOUT ))
aj_ok=0
aj_detail=""
health_seen=0
marker=""
last_probe=0
while [ "$(date +%s)" -lt "$deadline" ]; do
    now="$(date +%s)"
    if [ -z "$marker" ] && log_marker_hit "$LOG"; then marker="$(log_marker_name)"; fi

    # 轻量 health 探活（2s 间隔）
    hcode="$(curl -s -o /dev/null -w '%{http_code}' -m 2 "$base_url/health" 2>/dev/null)"
    [ "${hcode:-000}" = "200" ] && health_seen=1

    # AutoJS 客户端侧完整探测（≥5s 一次，避免频繁 initialize/tools-call）
    if [ "$aj_n" -gt 0 ] && [ $((now - last_probe)) -ge 5 ]; then
        last_probe=$now
        if aj_detail="$(aj_probe "$endpoint" 2>/dev/null)" && [ -n "$aj_detail" ]; then
            aj_ok=1
            break
        fi
    fi

    if ! kill -0 "$WRAPPER_PID" 2>/dev/null; then
        # 进程已退出：再确认一次 AutoJS / marker，两者皆无才算 FAIL
        if [ "$aj_n" -gt 0 ] && [ "$aj_ok" = 0 ]; then
            if aj_detail="$(aj_probe "$endpoint" 2>/dev/null)" && [ -n "$aj_detail" ]; then aj_ok=1; fi
        fi
        if [ "$aj_ok" = 1 ] || { [ -n "$marker" ] && [ "$marker_fallback" = 1 ]; }; then
            break
        fi
        echo "LAUNCH: FAIL — 启动进程已退出且未就绪（wrapper PID $WRAPPER_PID；health_seen=$health_seen）" >&2
        tail_log "$LOG"
        exit 1
    fi

    if grep -qE 'BUILD FAILED|FAILURE: Build failed' "$LOG" 2>/dev/null; then
        echo "LAUNCH: FAIL — Gradle 构建失败" >&2
        tail_log "$LOG"
        exit 1
    fi

    # marker 兜底：显式逃生阀（或该线无 AutoJS jar）时，marker 出现即可 READY
    if [ -n "$marker" ] && [ "$marker_fallback" = 1 ]; then
        break
    fi
    sleep 2
done

if [ "$aj_ok" != 1 ]; then
    if [ -n "$marker" ] && [ "$marker_fallback" = 1 ]; then
        echo "LAUNCH: READY — $VERSION（AutoJS 未就绪，marker 兜底：$marker）"
        echo "  autojs      = 未响应（marker_fallback=1：MT_ALLOW_NO_AUTOJS=${allow_no_autojs} / aj_n=${aj_n}）"
    else
        tail_log "$LOG"
        if [ "$aj_n" -gt 0 ] && [ "$health_seen" = 0 ]; then
            echo "LAUNCH: ERROR — ${TIMEOUT}s 内 AutoJS 端点从未响应 health（$endpoint），且无世界 marker" >&2
            echo "  run/$VERSION/mods 有 AutoJS jar，但端点不可用；逃生阀：MT_ALLOW_NO_AUTOJS=1（需日志 marker 兜底）" >&2
            if [ "$KILL_ON_TIMEOUT" = 1 ]; then bash "$DIR/stop.sh" --version "$VERSION" >&2 || true; fi
            exit 2
        fi
        echo "LAUNCH: TIMEOUT — ${TIMEOUT}s 内 AutoJS 客户端侧未就绪（health_seen=$health_seen）" >&2
        if [ "$health_seen" = 1 ]; then
            echo "  health 已响应但 status 未报 client available=true（可能仍在加载世界/脚本；可 --timeout 放宽）" >&2
        fi
        if [ "$KILL_ON_TIMEOUT" = 1 ]; then
            echo "LAUNCH: --kill-on-timeout → 调 stop.sh 收停" >&2
            bash "$DIR/stop.sh" --version "$VERSION" >&2 || true
        else
            echo "  手动收停：bash scripts/test/linux/stop.sh --version $VERSION" >&2
        fi
        exit 12
    fi
else
    echo "LAUNCH: READY — $VERSION 标题画面就绪（AutoJS 客户端侧 available=true）"
    echo "  autojs      = $aj_detail"
    echo "  health      = ok（HTTP 200 @ $base_url/health）"
fi

# 世界就绪是可选附加信息：READY 后按 --wait-world 额外等 marker（默认 0=不等）。
if [ -z "$marker" ] && [ "$WAIT_WORLD" -gt 0 ]; then
    world_deadline=$(( $(date +%s) + WAIT_WORLD ))
    while [ "$(date +%s)" -lt "$world_deadline" ]; do
        if log_marker_hit "$LOG"; then marker="$(log_marker_name)"; break; fi
        sleep 2
    done
fi
if [ -n "$marker" ]; then
    echo "LAUNCH: WORLD_READY — 世界已加载（marker: $marker）"
else
    if [ "$WAIT_WORLD" -gt 0 ]; then
        echo "LAUNCH: WARN — --wait-world ${WAIT_WORLD}s 内未见世界 marker（仍为标题画面 READY，exit 0）" >&2
    else
        echo "LAUNCH: WORLD_READY — 未命中（标题画面 READY；需要世界请 --quickplay 或 --wait-world）"
    fi
fi

# 打印游戏 PID（/proc 扫描 cwd==run/<ver> 的 java）与日志/pid 路径。
game_pid="$(scan_clients | while IFS='|' read -r p c; do
    case "$c" in "$RUN_DIR"|"$RUN_DIR"/*) echo "$p" ;; esac
done | head -n 1)"
echo "  game_pid    = ${game_pid:-未找到（可能在启动早期）}"
echo "  wrapper_pid = $WRAPPER_PID"
echo "  log         = $LOG"
echo "  pid_file    = $PIDFILE"
echo "  收停：bash scripts/test/linux/stop.sh --version $VERSION"
exit 0
