#!/usr/bin/env bash
# ============================================================================
# scripts/test/win/stop.sh — Astral Dice 测试台 · Windows(git bash) 收停路径
#
# 语义 = Linux 蓝本 stop.sh 的 Windows 对应物：
#   * 判线：pwsh -NoProfile + Get-CimInstance Win32_Process 取 java.exe 的
#     ProcessId/CommandLine，按 Mt.Paths.psm1:141-156 的同款标记串匹配
#     （":<子项目>:"、"run\<version>"、"run/<version>"，不用裸子项目名）。
#   * 收停：taskkill //PID <pid> //T //F（替代 kill -- -pgid；Windows 无进程组，
#     /T 才是整棵树的等价物）。
#   * 清存档：--purge-saves，白名单对齐 mt_cleanup.ps1:228-265
#     （client_world=run/<v>/saves/testworld、server_world=run/<v>/testworld、
#      以及 run/<v>/saves/ 下各世界目录），绝不碰 mods/config/logs。
#
# 用法：
#   bash scripts/test/win/stop.sh [--version <ver>|--all] [--purge-saves]
#                                 [--stop-daemon] [--grace <秒>] [--dry-run]
#
# 退出码：0=无残留（含"本来就没有"、dry-run）；1=仍有残留；2=用法错误
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

VERSION=""
ALL=0
PURGE=0
FORCE=0
DRY=0
STOP_DAEMON=0
GRACE=10

usage() {
    cat <<'USAGE'
用法：
  bash scripts/test/win/stop.sh [--version <ver>|--all] [--purge-saves]
                                [--stop-daemon] [--grace <秒>] [--dry-run]

  --version <ver>   1.21.1 | 1.20.1 | 26.1.2 | fabric-1.20.1（只收这一条线）
  --all             四条线全收（1.21.1 1.20.1 26.1.2 fabric-1.20.1）
                    缺省不给两者时 = --all（收停是幂等安全检查）
  --purge-saves     同时清测试世界存档（白名单：run/<v>/saves/*、run/<v>/testworld、
                    run/<v>/client_world/testworld、run/<v>/server_world/testworld、
                    fabric 双侧 fabric-1.20.1/run/<client|server>；不碰 mods / config / logs）
  --force           对齐 mt.ps1 的命令行契约；bash 侧恒 taskkill //T //F，本开关无副作用
  --stop-daemon     收停后顺带 `gradlew.bat --stop`（Gradle daemon）
  --grace <秒>      判残留的复核等待上限，默认 10
  --dry-run         只打印 would-kill / would-remove，不杀进程、不删文件

判线标记串（与 scripts/test/lib/Mt.Paths.psm1:141-156 一致）：
  ":<子项目>:"、"run\<version>"、"run/<version>" —— 刻意不用裸子项目名，避免误杀 IDE。
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --version)      VERSION="${2:-}"; shift 2 ;;
        --version=*)    VERSION="${1#*=}"; shift ;;
        --all)          ALL=1; shift ;;
        --purge-saves)  PURGE=1; shift ;;
        # --force 对齐 mt.ps1:783-790 的命令行契约：bash 侧恒用 taskkill //T //F，
        # 没有优雅信号档 ⇒ 只「接受但无副作用」，绝不因它报未知参数。
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
# 缺省（--version 与 --all 都不给）= 四线全收。
# 与 Linux 蓝本"必须给其一"不同：契约把两者都标为可选，且收停是幂等安全检查，
# 默认扫全仓比报错更适合"先确保没有残留再干活"的用法。
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

# ── CIM 判线（一次查询，多条线复用）────────────────────────────────────
ROWS=""
cim_ok=1
if rows="$(mt_cim_java_rows 2>/dev/null)"; then
    ROWS="$rows"
else
    cim_ok=0
fi
if [ "$cim_ok" = 0 ]; then
    if [ "$DRY" = 1 ]; then
        mt_winstop "DRY-RUN — CIM 查询失败（pwsh -NoProfile Get-CimInstance 不可用）；真实运行会 exit 2"
        exit 2
    fi
    mt_winstop_err "ERROR — CIM 查询失败（pwsh -NoProfile Get-CimInstance 不可用），无法判线"
    exit 2
fi

if [ -z "$ROWS" ]; then
    mt_info "cim" "无 java.exe 进程（CIM 判线结果为空）"
fi

# ── 收停：逐线收集 → taskkill //T //F ──────────────────────────────────
killed_any=0
pids_killed=""

kill_pid() {
    local pid="$1" ver="$2" cmd="$3"
    if [ "$DRY" = 1 ]; then
        mt_winstop "would-kill — PID $pid（$ver）"
        mt_detail "cmd          = $(printf '%s' "$cmd" | cut -c1-200)"
        mt_detail "would-call   = taskkill //PID $pid //T //F"
        return 0
    fi
    local tk_out
    if tk_out="$(taskkill //PID "$pid" //T //F 2>&1)"; then
        mt_winstop "killed — PID $pid（$ver）"
    else
        # taskkill 的退出码在本机不可靠（找不到进程也常返回 0，输出为 GBK），
        # 一律以随后的 CIM 复核为准，这里只把原始输出附在字段行。
        mt_winstop "kill-call — PID $pid（$ver）返回非零，按 CIM 复核结果判定"
        mt_detail "taskkill_out = $(printf '%s' "$tk_out" | tr -d '\r' | head -n 2 | tr '\n' ' ')"
    fi
    pids_killed="$pids_killed $pid"
    return 0
}

for v in $TARGETS; do
    project="$(mt_subproject "$v")"
    # fabric 线的 run 目录由全局 MT_FAB_SUBPROJECT 决定，裸调本脚本时必须先按本次版本切过来，
    # 否则 pidfile / AutoJS 状态文件会落到另一条 fabric 线的目录里(2026-11-05 实测遗漏)。
    mt_fab_set_version "$v" || true
    run_dir="$MT_REPO_ROOT/run/$v"
    # fabric-1.20.1 是双 run 目录（<子项目>/run/<client|server>）：pidfile 每侧各一个，
    # 另外旧 pwsh 测试台在 scripts/test/fabric/.ft_launch_state.json 里也记了 PID。
    pid_dirs="$run_dir"
    if mt_is_fabric "$v"; then
        pid_dirs="$(mt_fab_side_dir client) $(mt_fab_side_dir server)"
        run_dir="$(mt_fab_side_dir client)"
        mt_detail "run_layout   = $(mt_fab_run_label "$v" client) | $(mt_fab_run_label "$v" server)"
    fi

    # 1) 先按 CIM 判线（命令行是权威，pidfile 可能是上一轮遗留）
    hit=0
    while IFS=$'\t' read -r pid cmd; do
        [ -n "$pid" ] || continue
        if mt_cmdline_matches_version "$v" "$cmd"; then
            kill_pid "$pid" "$v" "$cmd"
            hit=1
        fi
    done <<< "$ROWS"

    # 2) pidfile（win-run.pid）—— 记的是 Windows PID
    for pd in $pid_dirs; do
    pidfile="$pd/win-run.pid"
    if [ -f "$pidfile" ]; then
        p="$(tr -d '\r\n' < "$pidfile" 2>/dev/null || true)"
        case "$p" in
            ''|*[!0-9]*) p="" ;;
        esac
        if [ -n "$p" ]; then
            if mt_pid_alive "$p"; then
                kill_pid "$p" "$v" "pidfile=$pidfile"
                hit=1
            elif [ "$DRY" = 1 ]; then
                mt_winstop "would-remove-pidfile — $pidfile（PID $p 已不是 java 进程）"
            fi
        fi
        if [ "$DRY" = 1 ]; then
            mt_winstop "would-remove-pidfile — $pidfile"
        else
            rm -f "$pidfile" 2>/dev/null && mt_info "pidfile" "已删除 $pidfile" \
                || mt_info "pidfile" "删除失败（沙箱或占用）：$pidfile"
        fi
    fi
    done

    # 2b) fabric 旧 pwsh 测试台的启动状态文件（scripts/test/fabric/.ft_launch_state.json）
    if mt_is_fabric "$v"; then
        sp="$(mt_fab_state_pid 2>/dev/null || true)"
        case "$sp" in
            ''|*[!0-9]*) sp="" ;;
        esac
        if [ -n "$sp" ]; then
            sside="$(mt_fab_state_get side 2>/dev/null || echo '?')"
            if mt_pid_alive "$sp"; then
                kill_pid "$sp" "$v" "ft_launch_state(side=$sside)"
                hit=1
            else
                mt_info "fabric_state" ".ft_launch_state.json 记的 PID $sp（side=$sside）已不在运行（陈旧状态，不删他人文件）"
            fi
        fi
    fi

    # 2c) fabric：AutoJS 注入状态文件（run/<side>/.mt_autojs_injected）—— launch.sh 写、本脚本删。
    #     「mods 里有没有 AutoJS jar」在 fabric 线恒为假，PowerShell 侧通道判定改读这个文件，
    #     所以它必须与进程同生命周期：收停即删，避免下次手工启动时被读成「已注入」。
    if mt_is_fabric "$v"; then
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

    if [ "$hit" = 0 ] && [ "$DRY" = 0 ]; then
        mt_info "noop" "$v 线未发现本 repo 的 java 进程（CIM 判线为空）"
    fi
    if [ -f "$run_dir/linux-run.pid" ]; then
        mt_info "linux-pidfile" "存在 run/$v/linux-run.pid（Linux 台遗留；本脚本不删他人状态文件）"
    fi
done

# ── 复核残留（taskkill 是同步的，但 /T 收树需要一点时间）───────────────
wait_residual() {
    local secs="$1"
    [ "$DRY" = 1 ] && return 0
    local i=0
    while [ "$i" -lt "$secs" ]; do
        local rows2 left=0
        if rows2="$(mt_cim_java_rows 2>/dev/null)"; then
            while IFS=$'\t' read -r pid cmd; do
                [ -n "$pid" ] || continue
                for v in $TARGETS; do
                    if mt_cmdline_matches_version "$v" "$cmd"; then left=1; break; fi
                done
                [ "$left" = 1 ] && break
            done <<< "$rows2"
            [ "$left" = 0 ] && return 0
        fi
        sleep 1
        i=$((i + 1))
    done
    return 1
}

residual_pids() {
    local rows2
    rows2="$(mt_cim_java_rows 2>/dev/null)" || return 2
    local out="" left=0
    while IFS=$'\t' read -r pid cmd; do
        [ -n "$pid" ] || continue
        for v in $TARGETS; do
            if mt_cmdline_matches_version "$v" "$cmd"; then
                out="$out PID $pid($v)"
                left=1
                break
            fi
        done
    done <<< "$rows2"
    printf '%s' "$out"
    [ "$left" = 1 ] && return 0 || return 1
}

if [ "$DRY" = 1 ]; then
    mt_winstop "DRY-RUN — 未杀进程、未删文件"
    if [ "$PURGE" = 1 ]; then
        mt_detail "purge        = 已请求（真实运行会逐条打 PURGE: removed/would-remove）"
    else
        mt_detail "purge        = 未请求（加 --purge-saves 才清存档）"
    fi
    mt_detail "stop_daemon  = $([ "$STOP_DAEMON" = 1 ] && echo 会执行 || echo 不执行)"
else
    wait_residual "$GRACE" || true
    left="$(residual_pids)"; rc=$?
    if [ "$rc" = 2 ]; then
        mt_winstop_err "ERROR — 复核阶段 CIM 查询失败，无法判定残留"
        exit 2
    elif [ "$rc" = 0 ]; then
        mt_winstop "RESIDUAL — 仍在本 repo 运行：$left"
        mt_detail "提示         = 残留可能是「不是 java.exe 名字的派生进程」，或权限不足；可先核对 CIM 输出"
        exit 1
    else
        mt_winstop "OK — 四线范围内无本 repo 的 Minecraft 客户端"
    fi
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
        [ -d "$run_dir" ] || { mt_info "purge-skip" "${run_dir#$MT_REPO_ROOT/} 不存在"; continue; }

        # (a) 命名世界（对齐 mt_cleanup.ps1:228-265 的 client_world / server_world）
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

# ── --stop-daemon ────────────────────────────────────────────────────
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
    exit 0
fi

# ── 收尾机器行 ───────────────────────────────────────────────────────
if [ "$killed_any" = 0 ] && [ -z "$pids_killed" ]; then
    mt_winstop "OK — 未发现本 repo 的 Minecraft 客户端（无需收停）"
fi
exit 0
