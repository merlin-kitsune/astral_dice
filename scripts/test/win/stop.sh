#!/usr/bin/env bash
# ============================================================================
# scripts/test/win/stop.sh — Astral Dice 测试台 · Windows(git bash) 收停路径
#
# 语义 = Linux 蓝本 stop.sh 的 Windows 对应物，但**本脚本不终止任何进程**：
#   * 判线：jps -lvm（JDK 自带）的 java 行 + run/<ver>/win-run.pid（fabric 为
#     run/<side>/win-run.pid）+ netstat -ano 里 25585/25565/25575 的 LISTENING PID。
#     标记串沿用 ":<子项目>:"、"run\<version>"、"run/<version>"（不用裸子项目名）。
#   * 收停：只解析并打印应终止的 PID（MT_STOP: PENDING pid=…），
#     实际终止由 Agent 调用 windows MCP 的 Process(mode:"kill") 执行。
#   * --verify：只读核对——三个测试台端口无 LISTENING 且无匹配 jps 行 ⇒ MT_STOP: OK；
#     否则 MT_STOP: FAIL（exit 1）。
#   * 清存档：--purge-saves，白名单对齐旧编排器
#     （client_world=run/<v>/saves/testworld、server_world=run/<v>/testworld、
#      以及 run/<v>/saves/ 下各世界目录），绝不碰 mods/config/logs。
#
# 用法：
#   bash scripts/test/win/stop.sh [--version <ver>|--all] [--verify] [--purge-saves]
#                                 [--stop-daemon] [--grace <秒>] [--dry-run] [--force]
#
# 退出码：0=已打印 PENDING/OK（含本来就无残留、dry-run）；1=--verify 仍有残留；
#         2=用法错误
# ============================================================================
set -uo pipefail

WIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
. "$WIN_DIR/lib/common.sh"

MT_REPO_ROOT="$(mt_resolve_repo_root "$WIN_DIR")" || MT_REPO_ROOT=""
if [ -z "$MT_REPO_ROOT" ]; then
    echo "WIN_STOP: ERROR — 无法定位仓库根（git rev-parse 与 settings.gradle 上溯都失败）" >&2
    exit 2
fi
export MT_REPO_ROOT

# 收停机器行前缀（与 winux 侧 MT_* 家族一致）：MT_STOP: PENDING|OK|FAIL …
mt_stop()     { printf 'MT_STOP: %s\n' "$*"; }
mt_stop_err() { printf 'MT_STOP: %s\n' "$*" >&2; }

VERSION=""
ALL=0
PURGE=0
FORCE=0
DRY=0
VERIFY=0
STOP_DAEMON=0
GRACE=10

usage() {
    cat <<'USAGE'
用法：
  bash scripts/test/win/stop.sh [--version <ver>|--all] [--verify] [--purge-saves]
                                [--stop-daemon] [--grace <秒>] [--dry-run] [--force]

  --version <ver>   1.21.1 | 1.20.1 | 26.1.2 | fabric-1.20.1 | fabric-1.21.1（只收这一条线）
  --all             五条线全收（缺省 = --all；收停是幂等安全检查）
  --verify          只读核对：三个测试台端口无 LISTENING 且无匹配 jps 行 ⇒ MT_STOP: OK
  --purge-saves     同时清测试世界存档（白名单：run/<v>/saves/*、run/<v>/testworld；
                    不碰 mods / config / logs）
  --force           兼容旧契约；已降级为 no-op（脚本不终止进程）
  --stop-daemon     收停后顺带 `gradlew.bat --stop`（Gradle daemon；不是进程强杀）
  --grace <秒>      兼容旧契约；脚本不再等待进程退出，此值仅被接受
  --dry-run         只打印待终止 PID 与 would-remove，不删文件

判线三源（缺一不可）：jps -lvm 行、run/<ver>/win-run.pid、25585/25565/25575 的 LISTENING。
终止动作由 Agent 通过 windows MCP 的 Process(mode:"kill") 执行；脚本本身不做任何 kill。
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --version)      VERSION="${2:-}"; shift 2 ;;
        --version=*)    VERSION="${1#*=}"; shift ;;
        --all)          ALL=1; shift ;;
        --verify)       VERIFY=1; shift ;;
        --purge-saves)  PURGE=1; shift ;;
        # 兼容旧命令行契约；真正终止已移交 Agent，故该开关无副作用。
        --force)        FORCE=1; shift ;;
        --stop-daemon)  STOP_DAEMON=1; shift ;;
        --grace)        GRACE="${2:-}"; shift 2 ;;
        --grace=*)      GRACE="${1#*=}"; shift ;;
        --dry-run)      DRY=1; shift ;;
        -h|--help)      usage; exit 0 ;;
        *) echo "用法错误：未知参数 $1（-h 看用法）" >&2; exit 2 ;;
    esac
done

if [ "$ALL" = 1 ] && [ -n "$VERSION" ]; then
    echo "用法错误：--version 与 --all 互斥，二选一" >&2
    exit 2
fi
# 缺省（--version 与 --all 都不给）= 全收。
# 收停是幂等安全检查，默认扫全仓比报错更适合"先确保没有残留再干活"的用法。
if [ "$ALL" = 0 ] && [ -z "$VERSION" ]; then
    ALL=1
fi
if [ -n "$VERSION" ] && ! mt_is_version "$VERSION"; then
    echo "用法错误：未知 --version $VERSION（可选 $MT_ALL_VERSIONS）" >&2
    exit 2
fi
case "$GRACE" in ''|*[!0-9]*) echo "用法错误：--grace 必须是整数秒" >&2; exit 2 ;; esac

if [ "$ALL" = 1 ]; then
    TARGETS="$MT_ALL_VERSIONS"
else
    TARGETS="$VERSION"
fi

if [ "$FORCE" = 1 ]; then
    mt_winstop_err "提示 — --force 已降级为 no-op：脚本不终止进程，终止由 Agent 用 windows MCP 的 Process(kill) 执行"
fi

# ── jps 进程表（一次查询，多条线复用）────────────────────────────────────
ROWS=""
PROCS_OK=1
if ! ROWS="$(mt_jps_java_rows 2>/dev/null)"; then
    PROCS_OK=0
    ROWS=""
fi
if [ "$PROCS_OK" = 0 ]; then
    mt_winstop_err "WARN — jps -lvm 不可用（未找到 \$JAVA_HOME/bin/jps 或 PATH 的 jps）；本轮只能靠 pidfile + 端口判线"
fi

# ── --verify：只读残留核对（无监听 + 无匹配 jps 行）───────────────────────
if [ "$VERIFY" = 1 ]; then
    bad=""
    for port in $MT_GAME_PORTS; do
        for p in $(mt_port_pids "$port" 2>/dev/null); do
            bad="$bad port:$port(pid=$p)"
        done
    done
    if [ "$PROCS_OK" = 1 ] && [ -n "$ROWS" ]; then
        while IFS=$'\t' read -r pid cmd; do
            [ -n "$pid" ] || continue
            for v in $TARGETS; do
                if mt_cmdline_matches_version "$v" "$cmd"; then
                    bad="$bad jps:$v(pid=$pid)"
                    break
                fi
            done
        done <<< "$ROWS"
    elif [ "$PROCS_OK" = 0 ]; then
        bad="$bad jps-unavailable"
    fi
    if [ -z "$bad" ]; then
        mt_stop "OK — 25585/25565/25575 无 LISTENING，且无匹配 jps 行（$TARGETS）"
        exit 0
    fi
    mt_stop "FAIL — 仍有残留：$bad"
    exit 1
fi

# ── 收集待终止 PID（jps 行 + pidfile + 端口）──────────────────────────────
declare -a PENDING_PIDS=()
declare -a PENDING_SRC=()
declare -a STALE_PIDFILES=()
declare -a KEEP_PIDFILES=()

add_pid() {
    local pid="$1" src="$2" x
    case "$pid" in ''|*[!0-9]*) return 0 ;; esac
    for x in "${PENDING_PIDS[@]}"; do
        [ "$x" = "$pid" ] && return 0
    done
    PENDING_PIDS+=("$pid")
    PENDING_SRC+=("$src")
}

for v in $TARGETS; do
    project="$(mt_subproject "$v")"
    # fabric 线的 run 目录由全局 MT_FAB_SUBPROJECT 决定，裸调本脚本时必须先按本次版本切过来，
    # 否则 pidfile / AutoJS 状态文件会落到另一条 fabric 线的目录里(2026-11-05 实测遗漏)。
    mt_fab_set_version "$v" || true
    run_dir="$MT_REPO_ROOT/run/$v"
    pid_dirs="$run_dir"
    if mt_is_fabric "$v"; then
        pid_dirs="$(mt_fab_side_dir client) $(mt_fab_side_dir server)"
        run_dir="$(mt_fab_side_dir client)"
        mt_detail "run_layout   = $(mt_fab_run_label "$v" client) | $(mt_fab_run_label "$v" server)"
    fi

    before="${#PENDING_PIDS[@]}"

    # jps 行（命令行是权威；pidfile 可能是上一轮遗留）
    hit=0
    if [ "$PROCS_OK" = 1 ] && [ -n "$ROWS" ]; then
        while IFS=$'\t' read -r pid cmd; do
            [ -n "$pid" ] || continue
            if mt_cmdline_matches_version "$v" "$cmd"; then
                add_pid "$pid" "jps:$v"
                mt_detail "cmd          = PID $pid $(printf '%s' "$cmd" | cut -c1-160)"
                hit=1
            fi
        done <<< "$ROWS"
    fi

    # pidfile（win-run.pid，记的是 Windows PID）；活着才进 PENDING，否则记为陈旧
    for pd in $pid_dirs; do
        pidfile="$pd/win-run.pid"
        [ -f "$pidfile" ] || continue
        p="$(tr -d '\r\n' < "$pidfile" 2>/dev/null || true)"
        case "$p" in
            ''|*[!0-9]*)
                STALE_PIDFILES+=("$pidfile|")
                continue
                ;;
        esac
        if mt_pid_alive "$p"; then
            add_pid "$p" "pidfile:${pidfile#"$MT_REPO_ROOT"/}"
            KEEP_PIDFILES+=("$pidfile|$p")
            hit=1
        else
            STALE_PIDFILES+=("$pidfile|$p")
        fi
    done

    # 测试台端口：25585 AutoJS / 25565 游戏 / 25575 fabric RCON
    for port in $MT_GAME_PORTS; do
        for p in $(mt_port_pids "$port" 2>/dev/null); do
            add_pid "$p" "port:$port"
            hit=1
        done
    done

    # fabric：AutoJS 注入状态文件（run/<side>/.mt_autojs_injected）—— launch.sh 写、本脚本删。
    # 「mods 里有没有 AutoJS jar」在 fabric 线恒为假，通道判定改读这个文件，
    # 所以它必须与进程同生命周期；仅在该版本已无待终止 PID 时才删。
    if mt_is_fabric "$v" && [ "${#PENDING_PIDS[@]}" = "$before" ]; then
        for sd in client server; do
            sf="$(mt_fab_autojs_state_path "$sd" 2>/dev/null || true)"
            [ -n "$sf" ] || continue
            [ -f "$sf" ] || continue
            if [ "$DRY" = 1 ]; then
                mt_winstop "would-remove-state — $sf"
            else
                rm -f "$sf" 2>/dev/null && mt_info "autojs_state" "已删除 $sf" \
                    || mt_info "autojs_state" "删除失败（沙箱或占用）：$sf"
            fi
        done
    fi

    if [ "$hit" = 0 ]; then
        mt_info "noop" "$v 线未发现本 repo 的测试台进程（jps/pidfile/端口均为空）"
    fi
    if [ -f "$run_dir/linux-run.pid" ]; then
        mt_info "linux-pidfile" "存在 run/$v/linux-run.pid（Linux 台遗留；本脚本不删他人状态文件）"
    fi
done

# ── 陈旧 pidfile：进程确实不在才清理（活进程的 pidfile 一律保留）──────────
if [ "${#STALE_PIDFILES[@]}" -gt 0 ]; then
    for rec in "${STALE_PIDFILES[@]}"; do
        f="${rec%%|*}"
        p="${rec#*|}"
        if [ -n "$p" ]; then
            mt_detail "stale        = $f（PID $p 不在 jps/端口表里）"
        else
            mt_detail "stale        = $f（内容无有效 PID）"
        fi
        if [ "$DRY" = 1 ]; then
            mt_winstop "would-remove-pidfile — $f"
        else
            rm -f "$f" 2>/dev/null && mt_info "pidfile" "已删除陈旧 pidfile $f" \
                || mt_info "pidfile" "删除失败（沙箱或占用）：$f"
        fi
    done
fi
for rec in "${KEEP_PIDFILES[@]}"; do
    mt_detail "pidfile      = 保留 $rec（进程仍在）"
done

# ── 收尾机器行：PENDING / OK（不杀进程）──────────────────────────────────
if [ "${#PENDING_PIDS[@]}" -eq 0 ]; then
    mt_stop "OK — 未发现本 repo 的 Minecraft 客户端（无需收停）"
else
    joined=""
    i=0
    for pid in "${PENDING_PIDS[@]}"; do
        if [ "$i" -eq 0 ]; then joined="$pid"; else joined="$joined,$pid"; fi
        i=$((i + 1))
        mt_detail "pending      = PID $pid（${PENDING_SRC[$((i - 1))]}）"
    done
    mt_stop "PENDING pid=$joined"
    mt_stop "由 Agent 用 mcp__windows__Process(mode:\"kill\") 终止上述 PID；脚本不做任何 kill"
fi

# ── --purge-saves：白名单清测试世界 ───────────────────────────────────
PROTECTED="mods config logs saves crash-reports screenshots resourcepacks shaderpacks defaultconfigs local temp server.properties eula.txt run"

purge_dir() {
    local d="$1" v="$2"
    [ -d "$d" ] || return 0
    case "$(basename "$d")" in
        /|.|..) return 0 ;;
    esac
    if [ "$DRY" = 1 ]; then
        mt_purge "would-remove — $d（$v 世界存档）"
        return 0
    fi
    local rel="${d#"$MT_REPO_ROOT"/}"
    if rm -rf -- "$d" 2>/dev/null; then
        mt_purge "removed — $rel（$v 世界存档）"
    else
        mt_purge "failed — $rel（沙箱拒绝或用例占用；bash 在 Windows 下删除 run/** 可能被策略拦）"
    fi
}

if [ "$PURGE" = 1 ]; then
    for v in $TARGETS; do
        # fabric 双侧各有一套世界存档（<子项目>/run/<client|server>）；其它线单目录。
        purge_dirs="$MT_REPO_ROOT/run/$v"
        if mt_is_fabric "$v"; then
            purge_dirs="$(mt_fab_side_dir client) $(mt_fab_side_dir server)"
        fi
        for run_dir in $purge_dirs; do
        [ -d "$run_dir" ] || { mt_info "purge-skip" "${run_dir#"$MT_REPO_ROOT"/} 不存在"; continue; }

        # (a) 命名世界（对齐旧编排器的 client_world / server_world）
        for named in "$run_dir/saves/testworld" "$run_dir/testworld" \
                     "$run_dir/client_world/testworld" "$run_dir/server_world/testworld"; do
            purge_dir "$named" "$v"
        done

        # (b) saves/ 下其它世界目录（同样只删目录，不删单文件）
        if [ -d "$run_dir/saves" ]; then
            for d in "$run_dir/saves"/*; do
                [ -d "$d" ] || continue
                base="$(basename "$d")"
                [ "$base" = "testworld" ] && continue
                case " $PROTECTED " in
                    *" $base "*) continue ;;
                esac
                purge_dir "$d" "$v"
            done
        fi

        # (c) 其它命名世界目录（run/<v>/<name> 含 level.dat），跳过受保护名
        for d in "$run_dir"/*; do
            [ -d "$d" ] || continue
            base="$(basename "$d")"
            [ "$base" = "testworld" ] && continue
            case " $PROTECTED " in
                *" $base "*) continue ;;
            esac
            [ -f "$d/level.dat" ] || continue
            purge_dir "$d" "$v"
        done
        done
    done
    if [ "$DRY" = 1 ]; then
        mt_purge "DRY-RUN — 未删除任何文件"
    fi
fi

# ── --stop-daemon（唯一允许的"停止"动作：Gradle 自带命令，不是进程强杀）──
if [ "$STOP_DAEMON" = 1 ]; then
    if [ "$DRY" = 1 ]; then
        mt_winstop "would-run — mt_cmdwin \"gradlew.bat --stop\"（cwd=$MT_REPO_ROOT）"
    else
        jdk="$(mt_find_daemon_jdk 2>/dev/null || true)"
        if [ -n "$jdk" ]; then
            export JAVA_HOME="$(mt_to_win "$jdk")"
            export PATH="$jdk/bin:$PATH"
        fi
        (
            cd "$MT_REPO_ROOT" || exit 1
            mt_cmdwin "gradlew.bat --stop" 2>&1 | tail -n 5
        ) || true
        daemons="$(mt_gradle_daemon_rows 2>/dev/null | grep -c . || true)"
        mt_winstop "DAEMON-STOP — gradlew.bat --stop 已调用；当前 GradleDaemon 进程数=$daemons"
    fi
fi

if [ "$DRY" = 1 ]; then
    mt_winstop "DRY-RUN — 未杀进程、未删文件（只读预览）"
fi
exit 0
