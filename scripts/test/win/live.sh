#!/usr/bin/env bash
# ============================================================================
# scripts/test/win/live.sh — fabric-1.20.1 实机批次编排（LT0–LT7）
#
# 定位：**只在实机排期时用**的批量编排层，不替代 mt.sh。它做四件事：
#   1) 按批次表（LT0–LT7）挑出本轮要跑的机判条目与人工条目；
#   2) 实机前置：先跑 mt.sh --phase preflight，再打印**用户终端**的 launch 命令
#      （沙箱/CI 里起游戏进程会因 Low IL 删不掉旧 remap 缓存而 AccessDenied，
#       故启动动作永远交回用户终端，本脚本只等待 RCON 就绪）；
#   3) 日志窗口纪律：启动前把两侧 latest.log 轮转，否则条目里的 `log` 断言
#      会因为「整文件匹配」被历史行满足（假绿）；实例已在跑时显式拒绝，
#      除非给 --allow-stale-log；
#   4) 逐条调用 `mt.sh --phase cases --case <json>`，任一非 0 立即中止并打
#      MT_LIVE_ABORT（不吞失败、不继续跑后面的条目）。
#
# 批次表（与 porting/fabric-1.20.1/LIVE-TEST-LIST-2026-11-02.md 同源）
#   LT0 冷启动/注册面        C1  server   LT-ENTITY-SWEEP
#   LT1 饰品后端三态         C2/C3/C4     （人工：三态各一轮）
#   LT2 出牌锁/骰战/防御     C2  server   LT-DUMP-BASELINE, LT-RESET-CARDLOCK,
#                                         LT-CLEAREFFECT-BY-DUMP
#   LT3 事件体系             C2           （人工 + CH-A）
#   LT4 商店/村民/交易       C2           （人工）
#   LT5 立牌                 C2  server   LT-SIGN-COOLDOWN-FINISH
#   LT6 轨道炮/激光 FX       C2+两 P0 线  （人工截图对照）
#   LT7 网络与版本闸门       C2/C5 server LT-VERSION-GATE-LIVE
#
# 用法：
#   bash scripts/test/win/live.sh --batch LT0 --dry-run
#   bash scripts/test/win/live.sh --batch LT2 --allow-auto
#   bash scripts/test/win/live.sh --batch all --allow-auto --stop-after
#   bash scripts/test/win/live.sh --batch LT5 --allow-auto --side server --allow-stale-log
#
# 参数：
#   --batch <LT0..LT7|all>   必填（未知批次 exit 2）
#   --env <C1..C5>           只作记录/提示（真值以批次表为准；给错只警告）
#   --side <client|server>   覆盖批次默认侧（cases 侧；inject_command 需 server）
#   --dry-run                只打印将执行的命令，不启动进程、不跑前置
#   --allow-auto             非 dry-run 批量的闸门（无它直接 exit 2）
#   --allow-stale-log        实例已在跑时仍然继续（默认拒绝，避免假绿）
#   --no-wait-player         不等待 /list 出现玩家（只适用于无 dump 的批次）
#   --stop-after             批次收尾跑 mt.sh --phase stop --force
#   -h|--help
#
# 退出码：0=OK 1=FAIL 2=用法错误/闸门 11=BLOCKED(preflight) 12=TIMEOUT 130=中断
# 机器行：MT_LIVE_BATCH: / MT_LIVE_CASE: / MT_LIVE_MANUAL: / MT_LIVE_LOGROT: /
#         MT_LIVE_ABORT: / MT_LIVE: OK|FAIL|DRY
# ============================================================================
set -uo pipefail

WIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
. "$WIN_DIR/lib/common.sh"

MT_REPO_ROOT="${MT_REPO_ROOT:-$(mt_resolve_repo_root "$WIN_DIR" || true)}"
export MT_REPO_ROOT
[ -n "$MT_REPO_ROOT" ] || { printf 'MT_ERROR: 找不到仓库根（需要 settings.gradle 或 git 工作树）\n' >&2; exit 2; }

MT_LIVE_VERSION="fabric-1.20.1"
MT_LIVE_RCON_WAIT_SEC="${MT_LIVE_RCON_WAIT_SEC:-120}"
MT_LIVE_PLAYER_WAIT_SEC="${MT_LIVE_PLAYER_WAIT_SEC:-120}"

# ── 批次表 ──────────────────────────────────────────────────────────────────
mt_live_batches() { printf '%s' 'LT0 LT1 LT2 LT3 LT4 LT5 LT6 LT7'; }

mt_live_batch_known() {
    case " $(mt_live_batches) " in *" ${1:-} "*) return 0 ;; esac
    return 1
}

## 批次 → 环境代号（只作提示与报告用）
mt_live_batch_env() {
    case "${1:-}" in
        LT0) printf '%s' 'C1' ;;
        LT1) printf '%s' 'C2/C3/C4' ;;
        LT2|LT3|LT4|LT5) printf '%s' 'C2' ;;
        LT6) printf '%s' 'C2+P0x2' ;;
        LT7) printf '%s' 'C2/C5' ;;
        *) return 1 ;;
    esac
}

## 批次 → 默认侧（cases 阶段用；dump/命令注入一律 server 侧）
mt_live_batch_side() {
    case "${1:-}" in
        LT0|LT2|LT5|LT7) printf '%s' 'server' ;;
        LT1|LT3|LT4|LT6) printf '%s' 'client' ;;
        *) return 1 ;;
    esac
}

## 批次 → 机判条目（case_id）；空 = 纯人工批次
mt_live_batch_cases() {
    case "${1:-}" in
        LT0) printf '%s' 'LT-ENTITY-SWEEP' ;;
        LT2) printf '%s' 'LT-DUMP-BASELINE LT-RESET-CARDLOCK LT-CLEAREFFECT-BY-DUMP' ;;
        LT5) printf '%s' 'LT-SIGN-COOLDOWN-FINISH' ;;
        LT7) printf '%s' 'LT-VERSION-GATE-LIVE' ;;
        *)   printf '%s' '' ;;
    esac
}

## 批次 → 人工要点（纯人工批次与混合批次都会打印）
mt_live_batch_manual() {
    case "${1:-}" in
        LT0) printf '%s' '冷启动：加载器无异常/注册面计数（157 物品 / 4 方块 / 67 效果）/ 无 crash-report' ;;
        LT1) printf '%s' '三态各一轮：C2 默认双后端、C3 仅 Accessories、C4 仅 Trinkets；换骰子看槽位即时 resize、蹲右键落点、死亡保留（U3–U6）' ;;
        LT2) printf '%s' '出牌锁四态、骰战防御折算 1:1、伤害数字归属（U 无编号项 + R-2）' ;;
        LT3) printf '%s' '抽卡→动画窗→选择→结算（AP_EVENT_* 族）；U7：动画窗内左键不被吞' ;;
        LT4) printf '%s' '卡牌商店/筹码商店/两位交易员村民：开窗、买卖、冷却、JEI 类别' ;;
        LT5) printf '%s' '7 位新立牌主动技 + 半透明渲染 + 免疫 HARMFUL（后者已登记为缺口）' ;;
        LT6) printf '%s' '轨道炮/激光同角度截图对照（光柱宽度占比、BEACON_V_TILE=8.0 平铺、颜色 0xFFCF4A/0xFF2626）' ;;
        LT7) printf '%s' 'C5：旧客户端 1.3.7-alpha.2 连新版应见 AP_NET_VERSION_REJECT（本条目只覆盖同版本 OK 侧）' ;;
        *) return 1 ;;
    esac
}

# ── 工具 ────────────────────────────────────────────────────────────────────
mt_live_out()  { printf 'MT_LIVE_%s\n' "$*"; }
mt_live_die()  { printf 'MT_ERROR: %s\n' "${1:-}" >&2; exit "${2:-2}"; }

mt_live_case_path() {
    printf '%s/scripts/test/cases/%s-fabric-1.20.1.json' "$MT_REPO_ROOT" "${1:-}"
}

## 实例是否在跑：RCON 可达 或 CIM 里还有本线 java 行
mt_live_instance_running() {
    if mt_rcon_probe "$(mt_fab_rcon_port)"; then return 0; fi
    local rows
    rows="$(mt_rows_for_version "$MT_LIVE_VERSION" 2>/dev/null | head -n 1)"
    [ -n "$rows" ]
}

## 轮转一侧 latest.log（实例未在跑时才允许）
mt_live_rotate_log() {
    local side log stamp moved
    side="${1:-}"
    log="$(mt_fab_log_path "$side" 2>/dev/null)" || return 0
    [ -f "$log" ] || return 0
    [ -s "$log" ] || { rm -f "$log" 2>/dev/null; return 0; }
    stamp="$(date +%Y%m%d-%H%M%S)"
    moved="${log}.live-${stamp}"
    if mv "$log" "$moved" 2>/dev/null; then
        mt_live_out "LOGROT: side=$side from=latest.log to=$(basename "$moved")"
    else
        printf 'MT_WARN: 无法轮转 %s（文件被占用？）—— 条目里的 log 断言可能被历史行满足\n' "$log" >&2
    fi
}

## 等 RCON 就绪（只有服务端有 RCON）
mt_live_wait_rcon() {
    local waited=0
    while [ "$waited" -lt "$MT_LIVE_RCON_WAIT_SEC" ]; do
        if mt_rcon_probe "$(mt_fab_rcon_port)"; then
            mt_live_out "RCON_READY: port=$(mt_fab_rcon_port) waited=${waited}s"
            return 0
        fi
        sleep 2
        waited=$((waited + 2))
    done
    return 1
}

## 等至少一名玩家进服（dump 的 @a 需要解析到玩家）
mt_live_wait_player() {
    local waited=0 reply n
    while [ "$waited" -lt "$MT_LIVE_PLAYER_WAIT_SEC" ]; do
        reply="$(mt_rcon_exec 'list' 2>/dev/null || true)"
        n="$(printf '%s' "$reply" | sed -n 's/.*There are \([0-9][0-9]*\) of a max.*/\1/p' | head -n 1)"
        case "$n" in
            ''|0) : ;;
            *) mt_live_out "PLAYER_READY: online=$n waited=${waited}s"; return 0 ;;
        esac
        sleep 3
        waited=$((waited + 3))
    done
    return 1
}

mt_live_print_launch_cmds() {
    local side="${1:-server}" other
    if [ "$side" = client ]; then other=server; else other=client; fi
    printf 'LAUNCH_HINT: 在**用户终端**（Git Bash 或 cmd）执行：\n'
    printf '  cd %s\n' "$MT_REPO_ROOT"
    printf '  bash scripts/test/win/mt.sh --version %s --side %s --phase env\n' "$MT_LIVE_VERSION" "$side"
    printf '  bash scripts/test/win/mt.sh --version %s --side %s --phase launch\n' "$MT_LIVE_VERSION" "$side"
    if [ "$side" != server ]; then
        printf 'LAUNCH_HINT: 本条批次需要**服务端**侧执行命令注入，请在另一个终端同时起：\n'
        printf '  bash scripts/test/win/mt.sh --version %s --side server --phase launch\n' "$MT_LIVE_VERSION"
    else
        printf 'LAUNCH_HINT: 需要玩家在线的条目请同时在另一个终端起客户端：\n'
        printf '  bash scripts/test/win/mt.sh --version %s --side client --phase launch\n' "$MT_LIVE_VERSION"
    fi
    printf 'LAUNCH_HINT: 首次启动前若 .fabric 里留着 10 月旧 remap 缓存，先删 fabric-1.20.1/run/%s/.fabric\n' "$other"
}

# ── 参数 ────────────────────────────────────────────────────────────────────
OPT_BATCH=""
OPT_ENV=""
OPT_SIDE=""
OPT_DRY=0
OPT_ALLOW=0
OPT_STALE=0
OPT_NOWAIT_PLAYER=0
OPT_STOP_AFTER=0

while [ $# -gt 0 ]; do
    case "$1" in
        --batch)   [ $# -ge 2 ] || mt_live_die "--batch 需要值"; OPT_BATCH="$2"; shift 2 ;;
        --batch=*) OPT_BATCH="${1#*=}"; shift ;;
        --env)     [ $# -ge 2 ] || mt_live_die "--env 需要值"; OPT_ENV="$2"; shift 2 ;;
        --env=*)   OPT_ENV="${1#*=}"; shift ;;
        --side)    [ $# -ge 2 ] || mt_live_die "--side 需要值"; OPT_SIDE="$2"; shift 2 ;;
        --side=*)  OPT_SIDE="${1#*=}"; shift ;;
        --dry-run) OPT_DRY=1; shift ;;
        --allow-auto) OPT_ALLOW=1; shift ;;
        --allow-stale-log) OPT_STALE=1; shift ;;
        --no-wait-player) OPT_NOWAIT_PLAYER=1; shift ;;
        --stop-after) OPT_STOP_AFTER=1; shift ;;
        -h|--help) sed -n '2,50p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) mt_live_die "未知参数：$1（--help 看用法）" ;;
    esac
done

[ -n "$OPT_BATCH" ] || mt_live_die "必须给 --batch（$(mt_live_batches) 或 all）"
if ! mt_live_batch_known "$OPT_BATCH" && [ "$OPT_BATCH" != all ]; then
    mt_live_die "未知批次：$OPT_BATCH（可选：$(mt_live_batches) / all）"
fi
if [ -n "$OPT_SIDE" ] && ! mt_fab_side_valid "$OPT_SIDE"; then
    mt_live_die "--side 需要 client 或 server（收到：$OPT_SIDE）"
fi

if [ "$OPT_BATCH" = all ]; then
    BATCH_LIST="$(mt_live_batches)"
else
    BATCH_LIST="$OPT_BATCH"
fi

# ── dry-run：只打印 ────────────────────────────────────────────────────────
if [ "$OPT_DRY" = 1 ]; then
    printf 'MT_LIVE: DRY batch=%s env=%s repo=%s\n' "$OPT_BATCH" "${OPT_ENV:-<表内默认>}" "$MT_REPO_ROOT"
    for b in $BATCH_LIST; do
        side="${OPT_SIDE:-$(mt_live_batch_side "$b")}"
        printf 'MT_LIVE_BATCH: %s env=%s side=%s mode=%s\n' "$b" "$(mt_live_batch_env "$b")" "$side" \
            "$([ -n "$(mt_live_batch_cases "$b")" ] && printf cases || printf manual)"
        printf '  DRY_CMD: bash scripts/test/win/mt.sh --version %s --phase preflight\n' "$MT_LIVE_VERSION"
        mt_live_print_launch_cmds "$side"
        for c in $(mt_live_batch_cases "$b"); do
            printf '  DRY_CMD: bash scripts/test/win/mt.sh --version %s --side %s --phase cases --case %s\n' \
                "$MT_LIVE_VERSION" "$side" "$(mt_live_case_path "$c")"
        done
        printf 'MT_LIVE_MANUAL: %s — %s\n' "$b" "$(mt_live_batch_manual "$b")"
    done
    printf 'MT_LIVE: DRY_OK batches=%s\n' "$(printf '%s' "$BATCH_LIST" | wc -w | tr -d ' ')"
    exit 0
fi

# ── 闸门：非 dry-run 的批量执行必须显式放行 ───────────────────────────────
if [ "$OPT_ALLOW" != 1 ] && [ "${MT_ALLOW_AUTO:-0}" != 1 ]; then
    printf 'MT_ERROR: 批量实机编排需要 --allow-auto（或 MT_ALLOW_AUTO=1）—— 它会连续跑多条用例并等 RCON，\n' >&2
    printf 'MT_ERROR: 无人值守时请先 --dry-run 复核将执行什么。\n' >&2
    exit 2
fi

# ── 前置：mt.sh preflight（fabric 的原生 bash 实现）──────────────────────────
bash "$WIN_DIR/mt.sh" --version "$MT_LIVE_VERSION" --phase preflight
rc=$?
if [ "$rc" -ne 0 ]; then
    printf 'MT_LIVE_ABORT: preflight rc=%s（修好前置再跑本批次）\n' "$rc" >&2
    exit "$rc"
fi

# ── 逐批次 ─────────────────────────────────────────────────────────────────
fail_total=0
case_ok=0
case_fail=0
for b in $BATCH_LIST; do
    side="${OPT_SIDE:-$(mt_live_batch_side "$b")}"
    cases="$(mt_live_batch_cases "$b")"
    printf 'MT_LIVE_BATCH: %s env=%s side=%s mode=%s\n' "$b" "$(mt_live_batch_env "$b")" "$side" \
        "$([ -n "$cases" ] && printf cases || printf manual)"
    printf 'MT_LIVE_MANUAL: %s — %s\n' "$b" "$(mt_live_batch_manual "$b")"
    [ -n "$cases" ] || continue

    # 日志窗口纪律：实例已在跑 ⇒ 拒绝（除非显式允许）
    if mt_live_instance_running; then
        if [ "$OPT_STALE" = 1 ]; then
            printf 'MT_WARN: %s 批次时实例已在运行且给了 --allow-stale-log —— 日志断言可能被历史行满足\n' "$b" >&2
        else
            printf 'MT_LIVE_ABORT: 实例已在运行，latest.log 未轮转；log 断言会把历史行当成本轮证据（假绿）。\n' >&2
            printf 'MT_LIVE_ABORT: 先 bash scripts/test/win/mt.sh --version %s --phase stop --force，或加 --allow-stale-log 明确接受该风险。\n' \
                "$MT_LIVE_VERSION" >&2
            exit 12
        fi
    else
        mt_live_rotate_log server
        mt_live_rotate_log client
    fi

    mt_live_print_launch_cmds "$side"
    printf 'MT_LIVE: waiting rcon up to %ss ...\n' "$MT_LIVE_RCON_WAIT_SEC"
    if ! mt_live_wait_rcon; then
        printf 'MT_LIVE_ABORT: %ss 内 RCON 未就绪（端口 %s）—— 服务端没起来，或 run/server/server.properties 里 enable-rcon=false\n' \
            "$MT_LIVE_RCON_WAIT_SEC" "$(mt_fab_rcon_port)" >&2
        exit 12
    fi

    if [ "$OPT_NOWAIT_PLAYER" != 1 ]; then
        printf 'MT_LIVE: waiting player (/list) up to %ss ...\n' "$MT_LIVE_PLAYER_WAIT_SEC"
        if ! mt_live_wait_player; then
            printf 'MT_LIVE_ABORT: 等不到玩家进服 —— dump 的 @a 解析不到目标（批次 %s）\n' "$b" >&2
            exit 12
        fi
    fi

    for c in $cases; do
        path="$(mt_live_case_path "$c")"
        if [ ! -f "$path" ]; then
            printf 'MT_LIVE_ABORT: 条目文件不存在：%s\n' "$path" >&2
            fail_total=$((fail_total + 1)); case_fail=$((case_fail + 1))
            break
        fi
        bash "$WIN_DIR/mt.sh" --version "$MT_LIVE_VERSION" --side "$side" --phase cases --case "$path"
        crc=$?
        if [ "$crc" -eq 0 ]; then
            case_ok=$((case_ok + 1))
            printf 'MT_LIVE_CASE: %s = OK\n' "$c"
        else
            case_fail=$((case_fail + 1)); fail_total=$((fail_total + 1))
            printf 'MT_LIVE_CASE: %s = FAIL(rc=%s)\n' "$c" "$crc" >&2
            printf 'MT_LIVE_ABORT: %s 批次在 %s 处中止（后续条目不再执行）\n' "$b" "$c" >&2
            break
        fi
    done
done

if [ "$OPT_STOP_AFTER" = 1 ]; then
    bash "$WIN_DIR/mt.sh" --version "$MT_LIVE_VERSION" --side "${OPT_SIDE:-server}" --phase stop --force
fi

if [ "$fail_total" -gt 0 ]; then
    printf 'MT_LIVE: FAIL cases_ok=%s cases_fail=%s\n' "$case_ok" "$case_fail" >&2
    exit 1
fi
printf 'MT_LIVE: OK cases_ok=%s cases_fail=0\n' "$case_ok"
exit 0
