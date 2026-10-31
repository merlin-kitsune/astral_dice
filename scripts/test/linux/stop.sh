#!/usr/bin/env bash
# ============================================================================
# scripts/test/linux/stop.sh — Astral Dice 测试台 · Linux 侧收停
#
# 对标 Windows 侧 mt_stop.ps1 → mt_cleanup.ps1 的语义（唯一收停实现）：
#   1) 先按 <run-dir>/linux-run.pid 收停整个进程组（TERM → 等待 → KILL）
#   2) 再用 /proc/<pid>/cwd + /proc/<pid>/cmdline 扫本 repo 的 Minecraft 客户端
#      （java 进程；cwd 在 run/<ver>，或 cmdline 含 -Dfml / net.minecraft / runClient）
#   3) 复核无残留才报 OK，删 pid 文件
#   4) --purge-saves 额外删 run/<ver>/saves/* 与 run/<ver>/*/level.dat 世界目录
#
# 用法：
#   scripts/test/linux/stop.sh --version 1.21.1 [--force] [--dry-run] [--purge-saves]
#   scripts/test/linux/stop.sh --all            # 收停所有线的客户端
#
#   --version <ver>  1.21.1 | 1.20.1 | 26.1.2 | fabric-1.20.1
#   --all            所有线（与 --version 二选一，必填其一，对标 mt_stop.ps1）
#   --force          宽限期 15s → 3s
#   --purge-saves    收停后删测试世界（对标 mt_cleanup.ps1 --purge-saves）
#   --stop-daemon    额外 `bash ./gradlew --stop`（会停全机 Gradle 守护！默认不做）
#   --dry-run        只打印将做什么
#
# 输出机器行：LINUX_STOP: pid=…; LINUX_STOP: OK / LINUX_STOP: RESIDUAL；PURGE: …
# 退出码：0=OK（无残留）；1=RESIDUAL 有残留；2=用法错误
# ============================================================================
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${MT_STOP_REPO_ROOT:-$(cd "$DIR/../../.." && pwd)}"   # MT_STOP_REPO_ROOT 仅供脚本自测注入假仓库根
ALL_VERSIONS="1.21.1 1.20.1 26.1.2 fabric-1.20.1"

VERSION=""
ALL=0
FORCE=0
DRY=0
PURGE=0
STOP_DAEMON=0

usage() { sed -n '8,28p' "$0" | sed -E 's/^# ?//'; }

while [ $# -gt 0 ]; do
    case "$1" in
        --version)      VERSION="${2:-}"; shift 2 ;;
        --version=*)    VERSION="${1#*=}"; shift ;;
        --all)          ALL=1; shift ;;
        --force)        FORCE=1; shift ;;
        --purge-saves)  PURGE=1; shift ;;
        --stop-daemon)  STOP_DAEMON=1; shift ;;
        --dry-run)      DRY=1; shift ;;
        -h|--help)      usage; exit 0 ;;
        *) echo "用法错误：未知参数 $1（-h 看用法）" >&2; exit 2 ;;
    esac
done

if [ "$ALL" = 1 ] && [ -n "$VERSION" ]; then
    echo "用法错误：--all 与 --version 不能同时给" >&2; exit 2
fi
if [ "$ALL" = 0 ] && [ -z "$VERSION" ]; then
    echo "用法错误：必须给 --version <ver> 或 --all（-h 看用法）" >&2; exit 2
fi
if [ -n "$VERSION" ]; then
    case " $ALL_VERSIONS " in
        *" $VERSION "*) ;;
        *) echo "用法错误：未知 --version $VERSION（可选 $ALL_VERSIONS）" >&2; exit 2 ;;
    esac
fi

if [ "$ALL" = 1 ]; then VERSIONS="$ALL_VERSIONS"; else VERSIONS="$VERSION"; fi
GRACE=15
[ "$FORCE" = 1 ] && GRACE=3

alive() { kill -0 "$1" 2>/dev/null; }

kill_group() {
    local pid="$1" sig="$2"
    kill "-$sig" -- "-$pid" 2>/dev/null || kill "-$sig" "$pid" 2>/dev/null
}

# java 客户端扫描：cwd 落在 run/<ver>（或 run/ 全部）或 cmdline 含客户端标识。
scan_pids() {
    local p pid cwd cmd
    for p in /proc/[0-9]*; do
        pid="${p#/proc/}"
        [ "$pid" = "$$" ] && continue
        [ -r "$p/comm" ] || continue
        [ "$(cat "$p/comm" 2>/dev/null || true)" = "java" ] || continue
        cwd="$(readlink "$p/cwd" 2>/dev/null || true)"
        cmd="$(tr '\0' ' ' < "$p/cmdline" 2>/dev/null || true)"
        matched=0
        if [ "$ALL" = 1 ]; then
            case "$cwd" in "$REPO_ROOT"/run/*) matched=1 ;; esac
        else
            case "$cwd" in "$REPO_ROOT/run/$VERSION"|"$REPO_ROOT/run/$VERSION"/*) matched=1 ;; esac
            case "$cmd" in *"$REPO_ROOT/run/$VERSION"*) matched=1 ;; esac
        fi
        if [ "$matched" = 0 ]; then
            case "$cmd" in
                *-Dfml*|*net.minecraft*|*runClient*) matched=1 ;;
            esac
        fi
        [ "$matched" = 1 ] && printf '%s|%s\n' "$pid" "$cwd"
    done
}

saw_pid=0
residual=0

for v in $VERSIONS; do
    RUN_DIR="$REPO_ROOT/run/$v"
    PIDFILE="$RUN_DIR/linux-run.pid"

    # 1) PID 文件 → 进程组
    if [ -f "$PIDFILE" ]; then
        pid="$(tr -d ' \r\n' < "$PIDFILE" 2>/dev/null || true)"
        case "$pid" in ''|*[!0-9]*) pid="" ;; esac
        if [ -n "$pid" ] && alive "$pid"; then
            saw_pid=1
            if [ "$DRY" = 1 ]; then
                echo "LINUX_STOP: would-kill pid=$pid ver=$v (SIGTERM group, grace ${GRACE}s, then SIGKILL)"
            else
                echo "LINUX_STOP: pid=$pid ver=$v — SIGTERM（进程组）"
                kill_group "$pid" TERM
                deadline=$(( $(date +%s) + GRACE ))
                while alive "$pid" && [ "$(date +%s)" -lt "$deadline" ]; do sleep 0.5; done
                if alive "$pid"; then
                    echo "LINUX_STOP: pid=$pid 未在 ${GRACE}s 内退出 → SIGKILL（进程组）"
                    kill_group "$pid" KILL
                    sleep 1
                fi
                if alive "$pid"; then
                    echo "LINUX_STOP: RESIDUAL pid=$pid 仍存活（可能是 D 状态/僵尸，查 ps -o stat= -p $pid）"
                    residual=1
                else
                    echo "LINUX_STOP: pid=$pid 已退出"
                fi
            fi
        elif [ -n "$pid" ]; then
            echo "LINUX_STOP: pidfile=stale ver=$v pid=$pid（进程已不存在）"
        fi
    fi

    # 2) /proc 扫描补漏
    while IFS='|' read -r pid cwd; do
        [ -n "$pid" ] || continue
        saw_pid=1
        if [ "$DRY" = 1 ]; then
            echo "LINUX_STOP: would-kill pid=$pid ver=$v cwd=$cwd（/proc 扫描命中）"
        else
            echo "LINUX_STOP: pid=$pid ver=$v — SIGTERM（/proc 扫描命中，cwd=$cwd）"
            kill_group "$pid" TERM
            sleep 1
            if alive "$pid"; then
                echo "LINUX_STOP: pid=$pid 仍存活 → SIGKILL"
                kill_group "$pid" KILL
                sleep 1
            fi
            if alive "$pid"; then
                echo "LINUX_STOP: RESIDUAL pid=$pid cwd=$cwd"
                residual=1
            else
                echo "LINUX_STOP: pid=$pid 已退出"
            fi
        fi
    done <<< "$(scan_pids || true)"

    # 3) 复核 + 删 pid 文件
    left="$(scan_pids || true)"
    if [ -n "$left" ]; then
        residual=1
        while IFS='|' read -r pid cwd; do [ -n "$pid" ] && echo "LINUX_STOP: RESIDUAL pid=$pid cwd=$cwd"; done <<< "$left"
    fi
    if [ "$DRY" = 1 ]; then
        [ -f "$PIDFILE" ] && echo "LINUX_STOP: would-remove-pidfile $PIDFILE"
    else
        if [ -f "$PIDFILE" ]; then
            rm -f -- "$PIDFILE" && echo "LINUX_STOP: pidfile-removed $PIDFILE"
        fi
    fi

    # 4) --purge-saves（只删 saves/* 与 run/<v>/*/level.dat 世界目录，绝不碰 mods/config/logs）
    if [ "$PURGE" = 1 ]; then
        saves="$RUN_DIR/saves"
        if [ -d "$saves" ]; then
            for w in "$saves"/*; do
                [ -e "$w" ] || continue
                [ -d "$w" ] || continue
                if [ "$DRY" = 1 ]; then echo "PURGE: would-remove $w"; else rm -rf -- "$w" && echo "PURGE: removed $w"; fi
            done
        fi
        for lvl in "$RUN_DIR"/*/level.dat; do
            [ -e "$lvl" ] || continue
            wdir="$(dirname "$lvl")"
            case "$(basename "$wdir")" in
                mods|config|logs|saves|crash-reports|screenshots|resourcepacks|shaderpacks|defaultconfigs|local|temp) continue ;;
            esac
            [ "$wdir" = "$RUN_DIR" ] && continue
            if [ "$DRY" = 1 ]; then echo "PURGE: would-remove $wdir"; else rm -rf -- "$wdir" && echo "PURGE: removed $wdir"; fi
        done
    fi
done

if [ "$STOP_DAEMON" = 1 ]; then
    if [ "$DRY" = 1 ]; then
        echo "LINUX_STOP: would-run bash ./gradlew --stop（会停全机 Gradle 守护！）"
    else
        echo "LINUX_STOP: WARN — 执行 bash ./gradlew --stop（会停全机 Gradle 守护，可能影响其它 agent）"
        ( cd "$REPO_ROOT" && bash ./gradlew --stop ) >/dev/null 2>&1 \
            && echo "LINUX_STOP: gradle-daemon-stopped" \
            || echo "LINUX_STOP: WARN — gradlew --stop 失败（不影响已收停客户端）"
    fi
fi

if [ "$residual" = 1 ]; then
    echo "LINUX_STOP: RESIDUAL"
    exit 1
fi
if [ "$saw_pid" = 0 ] && [ "$DRY" = 0 ]; then
    echo "LINUX_STOP: OK — 未发现本 repo 的 Minecraft 客户端（无需收停）"
else
    echo "LINUX_STOP: OK"
fi
exit 0
