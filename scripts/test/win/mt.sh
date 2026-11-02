#!/usr/bin/env bash
# ============================================================================
# scripts/test/win/mt.sh — Astral Dice 测试台 · bash 编排层（P1 契约层）
#
# 全 bash/python 实现，**不依赖任何 .ps1 资产**（旧 pwsh 测试台已整体废弃删除）。
#   env/build/launch/stop        → bash（scripts/test/win/*.sh + lib/*.sh）
#   cases/report/preflight       → python（scripts/test/lib/mt_case.py / mt_report.py / mt_preflight.py）
# 机器行、退出码、开关面与旧 pwsh 版逐字对齐；差异只有「能力缺口记 BLOCKED(11)」这一条：
#   native 注入通道（窗口定位 / user32 合成输入）随 pwsh 资产删除而失效，见 phases.sh 的 launch 阶段。
#
# 阶段：env | build | launch | cases | report | stop（另接受 preflight，P2 迁移）
#
# 用法：
#   bash scripts/test/win/mt.sh --version 1.21.1 --phase env
#   bash scripts/test/win/mt.sh --version 1.21.1 --phase build
#   bash scripts/test/win/mt.sh --version 1.21.1 --phase launch
#   bash scripts/test/win/mt.sh --version 1.21.1 --phase cases --case scripts/test/cases/X-1.21.1.json
#   bash scripts/test/win/mt.sh --version 1.21.1 --phase report
#   bash scripts/test/win/mt.sh --version 1.21.1 --phase stop --purge-saves
#   bash scripts/test/win/mt.sh --version 1.21.1 --phase build --dry-run   # 只看将执行什么
#   bash scripts/test/win/mt.sh --allow-auto    # 全流程（四线顺序 + 1.21.1 门控）
#   bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase launch  # fabric 双 run 目录
#   bash scripts/test/win/mt.sh --version fabric-1.20.1 --enable-autojs --phase launch # 显式开 -PautojsTestMod=1
#
# 退出码：0=OK 1=FAIL 2=用法错误/ERROR 10=PREFLIGHT 11=BLOCKED 12=TIMEOUT 130=中断
# 机器行：MT_ERROR: / MT_INFO: / MT_WARN: / MT_PHASE: / MT_AUTO_DISABLED: / MT_RUN: /
#         MT_VERSION_VERDICT: / MT_DEPRECATED: / MT_<阶段>: OK|FAIL|BLOCKED|TIMEOUT|ERROR
#
# 批量闸门（2026-09-27 规则，逐字保留）：无 --phase 的全流程 / --phase cases 未带
#   --case / --phase report / 无 --version 的单阶段 —— 一律拦下并 exit 2；
#   放行：追加 --allow-auto 或设 MT_ALLOW_AUTO=1。
# ============================================================================
set -uo pipefail

WIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
. "$WIN_DIR/lib/common.sh"
# shellcheck source=lib/phases.sh
. "$WIN_DIR/lib/phases.sh"

MT_REPO_ROOT="$(mt_resolve_repo_root "$WIN_DIR")" || MT_REPO_ROOT=""
if [ -z "$MT_REPO_ROOT" ]; then
    printf 'MT_ERROR: 无法定位仓库根（git rev-parse 与 settings.gradle 上溯都失败）\n' >&2
    exit 2
fi
export MT_REPO_ROOT

usage() { sed -n '2,30p' "$0"; }

# ── 选项默认值（对齐 mt.ps1:558-582 的 $script:Opt 表）─────────────────────
OPT_VERSION=""
OPT_PHASE=""
OPT_CASE=""
OPT_CHANNEL=""
OPT_SIDE=""               # 仅 fabric-1.20.1：client|server（其它线传了报错）
OPT_FAB_AUTOJS=""         # 仅 fabric-1.20.1：1=显式开 -PautojsTestMod=1，0=显式关
OPT_ALLOW_AUTO=0
OPT_FORCE=0
OPT_KEEP_DAEMON=0
OPT_PURGE_MODE=""        # "" = 未指定；1 = 清存档；0 = 保留存档
OPT_DRY_RUN=0
CHANNEL=""               # 解析后归一（显式 > MT_CHANNEL > autojs）

# 取值类参数：缺值文案与 mt.ps1:599-613 逐字一致
need_value() {  # need_value <开关名> <剩余参数个数>
    if [ "${2:-0}" -lt 1 ]; then
        printf 'MT_ERROR: 缺少 --%s 的值\n' "$1" >&2
        exit 2
    fi
}

while [ $# -gt 0 ]; do
    case "$1" in
        --version)   need_value version $(( $# - 1 ));  OPT_VERSION="${2:-}";  shift 2 ;;
        --version=*) OPT_VERSION="${1#*=}"; shift ;;
        --phase)     need_value phase $(( $# - 1 ));    OPT_PHASE="${2:-}";    shift 2 ;;
        --phase=*)   OPT_PHASE="${1#*=}"; shift ;;
        --case)      need_value case $(( $# - 1 ));     OPT_CASE="${2:-}";     shift 2 ;;
        --case=*)    OPT_CASE="${1#*=}"; shift ;;
        --channel)   need_value channel $(( $# - 1 ));  OPT_CHANNEL="${2:-}";  shift 2 ;;
        --channel=*) OPT_CHANNEL="${1#*=}"; shift ;;
        --side)      need_value side $(( $# - 1 ));    OPT_SIDE="${2:-}";     shift 2 ;;
        --side=*)    OPT_SIDE="${1#*=}"; shift ;;
        --enable-autojs)  OPT_FAB_AUTOJS=1; shift ;;
        --disable-autojs) OPT_FAB_AUTOJS=0; shift ;;
        --run-timeout|--case-timeout|--new|--feature|--spec|--cleanup-on-exit|--no-cleanup-on-exit)
            printf 'MT_ERROR: %s 尚未在 bash 编排层实现（P1 范围外；环境变量/后续批次替代，见 docs/features/bash-first-test-flow-plan.md）\n' "$1" >&2
            exit 2 ;;
        --allow-auto)  OPT_ALLOW_AUTO=1; shift ;;
        --force)       OPT_FORCE=1; shift ;;
        --keep-daemon) OPT_KEEP_DAEMON=1; shift ;;
        --purge-saves) OPT_PURGE_MODE=1; shift ;;
        --keep-saves)  OPT_PURGE_MODE=0; shift ;;
        --dry-run)     OPT_DRY_RUN=1; shift ;;
        -h|--help)     usage; exit 0 ;;
        *) printf 'MT_ERROR: 未知参数 %s\n' "$1" >&2; exit 2 ;;
    esac
done

# ── 通道归一（windows-channel-decoupling-spec §2；顺序 显式 > 环境 > 默认）──
channel_raw="$OPT_CHANNEL"
[ -z "$channel_raw" ] && channel_raw="${MT_CHANNEL:-}"
if [ -z "$channel_raw" ]; then
    CHANNEL="autojs"
else
    CHANNEL="$(printf '%s' "$channel_raw" | tr 'A-Z' 'a-z' | tr -d '[:space:]')"
    if [ "$CHANNEL" != "autojs" ] && [ "$CHANNEL" != "native" ]; then
        printf 'MT_ERROR: --channel 需要 autojs 或 native（收到：%s）\n' "$channel_raw" >&2
        exit 2
    fi
fi
MT_CHANNEL="$CHANNEL"
export MT_CHANNEL

# 放行开关：--allow-auto 或 MT_ALLOW_AUTO=1（正则 ^(1|true|yes|on)$，pwsh -match 大小写不敏感）
ALLOW_AUTO=0
[ "$OPT_ALLOW_AUTO" = 1 ] && ALLOW_AUTO=1
case "$(printf '%s' "${MT_ALLOW_AUTO:-}" | tr 'A-Z' 'a-z')" in
    1|true|yes|on) ALLOW_AUTO=1 ;;
esac
MT_ALLOW_AUTO_ACTIVE=$ALLOW_AUTO
export MT_ALLOW_AUTO_ACTIVE

# 阶段 / 版本合法性（文案与 mt.ps1 的 未知阶段 / Assert-MtVersion 同款）
if [ -n "$OPT_PHASE" ]; then
    if ! mt_phase_is_phase "$OPT_PHASE"; then
        printf 'MT_ERROR: 未知阶段 %s（可选：%s）\n' "$OPT_PHASE" "$MT_PHASES" >&2
        exit 2
    fi
fi
if [ -n "$OPT_VERSION" ]; then
    if ! mt_is_version "$OPT_VERSION"; then
        printf 'MT_ERROR: 未知版本 %s（可选：%s）\n' "$OPT_VERSION" "$MT_ALL_VERSIONS" >&2
        exit 2
    fi
fi

# ── fabric-1.20.1 专属开关（--side / --enable-autojs）──────────────────────
# 语义：只有 fabric 线有「双 run 目录」和「条件 AutoJS」，其它三线传这两个开关一律报错，
#   避免「以为切了侧 / 以为开了 AutoJS」而实际没生效的静默偏差。
MT_SIDE=""
if [ -n "$OPT_SIDE" ]; then
    if ! mt_is_fabric "$OPT_VERSION"; then
        printf 'MT_ERROR: --side 只对 fabric-1.20.1 有意义（收到 --version %s；其它三线是单 run 目录 run/<版本>）\n' "${OPT_VERSION:-<未指定>}" >&2
        exit 2
    fi
    if ! mt_fab_side_valid "$OPT_SIDE"; then
        printf 'MT_ERROR: --side 需要 client 或 server（收到：%s）\n' "$OPT_SIDE" >&2
        exit 2
    fi
    MT_SIDE="$OPT_SIDE"
fi
if [ -n "$OPT_FAB_AUTOJS" ]; then
    if ! mt_is_fabric "$OPT_VERSION"; then
        printf 'MT_ERROR: --enable-autojs/--disable-autojs 只对 fabric-1.20.1 有意义（收到 --version %s）\n' "${OPT_VERSION:-<未指定>}" >&2
        exit 2
    fi
    MT_FAB_AUTOJS="$OPT_FAB_AUTOJS"
fi
export MT_SIDE MT_FAB_AUTOJS

# stop 阶段的开关透传（mt.ps1:783-790）
MT_STOP_FORCE=$OPT_FORCE
MT_STOP_KEEP_DAEMON=$OPT_KEEP_DAEMON
MT_STOP_PURGE_SAVES=0
[ "$OPT_PURGE_MODE" = 1 ] && MT_STOP_PURGE_SAVES=1
export MT_STOP_FORCE MT_STOP_KEEP_DAEMON MT_STOP_PURGE_SAVES
export MT_DRY_RUN=$OPT_DRY_RUN

# 中断自癒：bash 侧只退出自己，不收停游戏（与 win/launch.sh、Linux 蓝本一致）
on_interrupt() { printf 'MT_WARN: 收到中断信号，编排层退出（130）；游戏进程不动，收停用 --phase stop\n' >&2; exit 130; }
trap on_interrupt INT TERM

# ══ 🚫 批量编排闸门（对齐 mt.ps1:738-777，必须放在 stop / 单阶段分支之前）════
# 为什么前移：单阶段分支以 exit 收尾，闸门写在后面会被 --phase report 直接绕过（实测踩过）。
if [ "$ALLOW_AUTO" != 1 ]; then
    auto_blocked=0
    auto_what=""
    if [ -z "$OPT_PHASE" ]; then
        auto_blocked=1
        auto_what='全流程（无 --phase；会四线顺序自动跑完 build→env→launch→cases→report）'
    elif [ "$OPT_PHASE" = "cases" ] && [ -z "$OPT_CASE" ]; then
        auto_blocked=1
        auto_what='--phase cases 未指定 --case（走 run-dir，自动跑完该线全部用例）'
    elif [ "$OPT_PHASE" = "report" ]; then
        auto_blocked=1
        auto_what='--phase report（收集自动跑出来的结果）'
    elif [ -z "$OPT_VERSION" ]; then
        auto_blocked=1
        auto_what="--phase $OPT_PHASE 未指定 --version（会对四线顺序批量执行）"
    fi
    if [ "$auto_blocked" = 1 ]; then
        {
            printf 'MT_AUTO_DISABLED: %s —— 已被禁用。\n' "$auto_what"
            printf '  当前规则：「纯手动下达命令」——逐条注入、逐条看读数、逐步决策。\n'
            printf '  放行的阶段：build / env / launch / stop（把客户端弄起来/收停所需的基础设施）\n'
            printf '  单条执行：bash scripts/test/win/mt.sh --version <ver> --phase cases --case scripts/test/cases/<CASE>.json\n'
            printf '  直接注入：python scripts/test/mt_inject.py cmd --command "/astralprobe ..."\n'
            printf '  手动读数：python scripts/test/mt_assert.py log --version <ver> --pattern "<正则>"\n'
            printf '  临时放行：追加 --allow-auto（或设 MT_ALLOW_AUTO=1）\n'
        } >&2
        exit 2
    fi
fi

# ── stop 阶段独立可用（不参与阶段编排，也不打 MT_PHASE:）──────────────────
if [ "$OPT_PHASE" = "stop" ]; then
    mt_phase_stop "$OPT_VERSION"
    exit $?
fi

# ── 单阶段模式 ──────────────────────────────────────────────────────────
if [ -n "$OPT_PHASE" ]; then
    if [ -z "$OPT_VERSION" ]; then
        rc=0
        for v in $MT_ALL_VERSIONS; do
            one=0
            mt_run_phase "$v" "$OPT_PHASE" "$OPT_CASE" || one=$?
            [ "$one" -ne 0 ] && rc="$one"
        done
        exit "$rc"
    fi
    mt_run_phase "$OPT_VERSION" "$OPT_PHASE" "$OPT_CASE"
    exit $?
fi

# ══ 全流程（只有 --allow-auto / MT_ALLOW_AUTO=1 能到这里）═══════════════════
# P1 简化点（见淘汰路线「已知不等价」）：不做全局 --run-timeout 预算、不做退出清理
# 自动清档（每线跑完本身会 --phase stop 收停本线客户端）。
run_id="$(mt_phase_run_id)"
printf '\n########## MT RUN %s ##########\n' "$run_id"

if [ "$OPT_DRY_RUN" = 1 ]; then
    printf 'DRY-RUN: 全流程计划 —— %s（%s）\n' "$MT_ALL_VERSIONS" \
        "$([ -n "$OPT_VERSION" ] && echo "单版本，无跨版本门控" || echo "四线顺序 + 1.21.1 门控")"
fi

mt_phase_begin preflight
mt_phase_preflight "$OPT_VERSION"
rc=$?
if [ "$rc" -ne 0 ] && [ "$OPT_DRY_RUN" != 1 ]; then
    printf 'MT_RUN: ABORT（前置失败）\n' >&2
    if [ -n "$OPT_VERSION" ]; then
        printf '提示: 如需清理前置检查发现的残留进程，执行 bash scripts/test/win/mt.sh --version %s --phase stop\n' "$OPT_VERSION" >&2
    else
        printf '提示: 如需清理前置检查发现的残留进程，执行 bash scripts/test/win/mt.sh --allow-auto --phase stop\n' >&2
    fi
    exit "$MT_EXIT_PREFLIGHT"
fi

# 参与本次全流程的线：显式 --version 只跑该线且不门控
if [ -n "$OPT_VERSION" ]; then
    flow_versions="$OPT_VERSION"
    multi=0
else
    flow_versions="$MT_ALL_VERSIONS"
    multi=1
fi

for v in $flow_versions; do
    mt_phase_report_mark "$v" preflight PASS || true
done

overall=$MT_EXIT_PASS
gate_open=1

for v in $flow_versions; do
    if ! mt_is_version "$v"; then
        printf 'MT_ERROR: 未知版本 %s（可选：%s）\n' "$v" "$MT_ALL_VERSIONS" >&2
        exit 2
    fi
    # 门控：首条线（1.21.1）未通过 ⇒ 自 1.20.1 起逐条登记 GATED，跳过语义不变
    if [ "$multi" = 1 ] && [ "$v" = "1.20.1" ] && [ "$gate_open" != 1 ]; then
        gate_mark=0
        for gv in $flow_versions; do
            [ "$gv" = "$v" ] && gate_mark=1
            [ "$gate_mark" != 1 ] && continue
            printf 'MT_version-%s: BLOCKED (%s) — 1.21.1 未通过，按测试顺序门控不执行\n' "$gv" "$(mt_phase_elapsed)" >&2
            mt_phase_report_mark "$gv" cases GATED || true
        done
        break
    fi

    printf '\n########## MT VERSION: %s ##########\n' "$v"

    vrc=0
    mt_run_phase "$v" build "" || vrc=$?
    mt_phase_report_mark "$v" build "$(mt_phase_result_of "$vrc")" || true

    if [ "$vrc" -eq 0 ]; then
        mt_run_phase "$v" env "" || vrc=$?
        mt_phase_report_mark "$v" env "$(mt_phase_result_of "$vrc")" || true
    fi

    if [ "$vrc" -eq 0 ]; then
        mt_run_phase "$v" launch "" || vrc=$?
        mt_phase_report_mark "$v" launch "$(mt_phase_result_of "$vrc")" || true
    fi

    if [ "$vrc" -eq 0 ]; then
        mt_run_phase "$v" cases "$OPT_CASE" || vrc=$?
        mt_phase_report_mark "$v" cases "$(mt_phase_result_of "$vrc")" || true
    fi

    mt_phase_begin report
    verdict="$([ "$vrc" -eq 0 ] && echo PASS || { [ "$vrc" -eq 12 ] && echo TIMEOUT || echo FAIL; })"
    mt_phase_report "$v" "$verdict" || true
    mt_phase_stop "$v" || true

    if [ "$OPT_DRY_RUN" = 1 ]; then
        continue
    fi
    if [ "$vrc" -eq 0 ]; then
        printf 'MT_VERSION_VERDICT: %s = PASS\n' "$v"
    else
        label="$([ "$vrc" -eq 12 ] && echo TIMEOUT || echo FAIL)"
        printf 'MT_VERSION_VERDICT: %s = %s (exit=%s)\n' "$v" "$label" "$vrc"
        if [ "$vrc" -eq 12 ]; then overall=$MT_EXIT_TIMEOUT; else overall=$MT_EXIT_FAIL; fi
        gate_open=0
    fi
done

if [ "$OPT_DRY_RUN" = 1 ]; then
    printf '\nDRY-RUN: 全流程演练结束 —— 未执行任何阶段，未做通过/失败判定（去掉 --dry-run 才真跑）\n'
    exit 0
fi

mt_phase_begin summary
summary_rc=0
mt_phase_report_summary || summary_rc=$?
if [ "$summary_rc" -ne 0 ]; then
    if [ "$summary_rc" -eq 2 ]; then overall=$MT_EXIT_ERROR; else overall=$MT_EXIT_FAIL; fi
fi

printf '\n'
if [ "$overall" -eq 0 ]; then
    if [ "$multi" = 1 ]; then
        printf 'MT_RUN: PASS — 四线均通过\n'
    else
        printf 'MT_RUN: PASS — %s 通过（--version 指定单版本，未执行跨版本门控）\n' "$OPT_VERSION"
    fi
elif [ "$overall" -eq "$MT_EXIT_TIMEOUT" ]; then
    printf 'MT_RUN: TIMEOUT — 见 reports/%s/SUMMARY.md（超时与 FAIL 是两种结论）\n' "$run_id"
elif [ "$overall" -eq "$MT_EXIT_ERROR" ]; then
    printf 'MT_RUN: 无效运行（MT_USER_TERMINATED）— 本次测试结果全部无效，需重新执行测试验证（与 FAIL 是两种结论）\n'
    printf 'MT_RUN: INVALID reason=USER_TERMINATED exit=%s — 见 reports/%s/SUMMARY.md\n' "$overall" "$run_id"
else
    printf 'MT_RUN: FAIL — 见 reports/%s/SUMMARY.md\n' "$run_id"
fi
exit "$overall"
