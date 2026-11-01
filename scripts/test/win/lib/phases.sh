#!/usr/bin/env bash
# ============================================================================
# scripts/test/win/lib/phases.sh — 阶段实现层（P1：能 bash 化的走 bash，其余过渡期委托 pwsh）
#
# 被 scripts/test/win/mt.sh source。本文件只负责「一个阶段怎么执行」，
# 参数解析、批量闸门、版本顺序与门控留在 mt.sh（对应 mt.ps1 的 Invoke-MtRunPhase）。
#
# P1 分工（与 docs/features/bash-first-test-flow-plan.md 的批次表对齐）：
#   env       → bash scripts/test/win/env.sh            （bash 原生；只读体检）
#   build     → cmd //c "gradlew.bat :<子项目>:classes"  （bash 原生；四线映射见本文件）
#   launch    → bash scripts/test/win/launch.sh          （autojs 通道）
#               --channel native → 过渡期委托 pwsh mt_launch.ps1（窗口/输入注入 bash 无等价物）
#   stop      → bash scripts/test/win/stop.sh            （bash 原生）
#   cases     → pwsh scripts/test/mt_case.ps1            （P3 迁移；打 MT_DEPRECATED 行）
#   report    → pwsh scripts/test/mt_report.ps1          （P4 迁移；打 MT_DEPRECATED 行）
#   preflight → pwsh scripts/test/mt_preflight.ps1       （P2 迁移；打 MT_DEPRECATED 行）
#
# 退出码（与 scripts/test/lib/Mt.Phase.psm1:29-36 逐值一致）：
#   0=PASS 1=FAIL 2=ERROR 10=PREFLIGHT 11=BLOCKED 12=TIMEOUT（130=中断，由 mt.sh 的 trap 出）
# 机器行（与 mt.ps1 同一族，不新造前缀）：MT_ERROR: / MT_INFO: / MT_WARN: / MT_PHASE: /
#   MT_<NAME>: OK|FAIL|BLOCKED|TIMEOUT|ERROR / MT_DEPRECATED: / LAUNCH_BUDGET: / CASES_BUDGET:
#
# 本文件不写 run/ 与 scripts/test/**，不启游戏、不跑 gradlew（只有 build 阶段真跑时才跑，
# 且 P1 验证一律 --dry-run）。
# ============================================================================
set -uo pipefail

if [ "${MT_PHASES_LIB_LOADED:-0}" = 1 ]; then
    return 0
fi
MT_PHASES_LIB_LOADED=1
MT_PHASES_VERSION="1.0.0-win"

MT_PHASES_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -z "${MT_COMMON_VERSION:-}" ]; then
    # shellcheck source=./common.sh
    . "$MT_PHASES_DIR/common.sh"
fi

# ── 退出码常量（只在未定义时落值，避免覆盖调用方）────────────────────────
: "${MT_EXIT_PASS:=0}"
: "${MT_EXIT_FAIL:=1}"
: "${MT_EXIT_ERROR:=2}"
: "${MT_EXIT_PREFLIGHT:=10}"
: "${MT_EXIT_BLOCKED:=11}"
: "${MT_EXIT_TIMEOUT:=12}"

# 阶段集合：任务要求的六个 + preflight（mt.ps1 仍是全流程第一步，bash 侧先接受再 P2 迁移）。
MT_PHASES="env build launch cases report stop preflight"

# ── 小工具 ───────────────────────────────────────────────────────────────
mt_phase_info()  { printf 'MT_INFO: %s\n' "$*"; }
mt_phase_warn()  { printf 'MT_WARN: %s\n' "$*" >&2; }
mt_phase_err()   { printf 'MT_ERROR: %s\n' "$*" >&2; }

mt_phase_begin() {
    MT_PHASE_T0="$(date +%s)"
    printf '\n===== MT_PHASE: %s =====\n' "${1:-}"
}

# 已耗时文案，与 Mt.Phase.psm1:108-111 的 Get-MtPhaseElapsed 同格式（秒 / m05s）
mt_phase_elapsed() {
    local now secs
    now="$(date +%s)"
    secs=$(( now - ${MT_PHASE_T0:-$now} ))
    if [ "$secs" -ge 60 ]; then
        printf '%dm%02ds' $(( secs / 60 )) $(( secs % 60 ))
    else
        printf '%ss' "$secs"
    fi
}

# 退出码 → 报告结果字（mt.ps1:878 的 PASS/BLOCKED/TIMEOUT/FAIL 四档）
mt_phase_result_of() {
    case "${1:-}" in
        0)  printf '%s' PASS ;;
        11) printf '%s' BLOCKED ;;
        12) printf '%s' TIMEOUT ;;
        *)  printf '%s' FAIL ;;
    esac
}

# 运行 id：.mt_active_run 是 pwsh 侧粘性写盘的文件（gitignored），bash 只读不写。
# 不存在时请求 pwsh 建（与 mt.ps1:714 的 Get-MtActiveRunId 同语义），仍失败才本地生成。
mt_phase_run_id() {
    local f rid=""
    f="$MT_REPO_ROOT/scripts/test/cases/.mt_active_run"
    if [ -f "$f" ]; then
        rid="$(tr -d '\r\n' < "$f" 2>/dev/null)"
        [ -n "$rid" ] && { printf '%s' "$rid"; return 0; }
    fi
    local exe
    exe="$(mt_phase_pwsh_exe 2>/dev/null)" || exe=""
    if [ -n "$exe" ]; then
        rid="$("$exe" -NoProfile -NonInteractive -Command \
            "Import-Module '$(mt_to_win "$MT_REPO_ROOT/scripts/test/lib/Mt.Paths.psm1")' -Force; Get-MtActiveRunId" \
            2>/dev/null | tr -d '\r\n')"
        [ -n "$rid" ] && { printf '%s' "$rid"; return 0; }
    fi
    mt_phase_warn ".mt_active_run 无法读取且 pwsh 取 runId 失败 —— 退回本地时间戳（reports/<id> 可能与之不同）"
    printf '%s' "$(date +%Y%m%d-%H%M%S)"
}

mt_phase_is_phase() {
    case " $MT_PHASES " in *" ${1:-} "*) return 0 ;; esac
    return 1
}

# 阶段预算（秒），对齐 mt.ps1:149-161 的 PhaseBudgets；bash P1 不强制超时，只用于打印判据。
mt_phase_budget() {
    case "${1:-}" in
        preflight) printf '%s' 120 ;;
        gen)       printf '%s' 120 ;;
        build)     printf '%s' 300 ;;
        env)       printf '%s' 240 ;;
        launch)    printf '%s' 180 ;;
        cases)     printf '%s' 300 ;;
        report)    printf '%s' 90 ;;
        stop)      printf '%s' 150 ;;
        cleanup)   printf '%s' 180 ;;
        *)         printf '%s' 300 ;;
    esac
}

mt_phase_require_repo() {
    if [ -z "${MT_REPO_ROOT:-}" ]; then
        mt_phase_err "仓库根未确定（MT_REPO_ROOT 为空）—— 先由 mt.sh 解析"
        return "$MT_EXIT_ERROR"
    fi
    return 0
}

# 版本合法性：文案与 scripts/test/lib/Mt.Paths.psm1:78 同款，版本列表用 bash 侧的四线。
mt_phase_require_version() {
    local v="${1:-}"
    if mt_is_version "$v"; then return 0; fi
    mt_phase_err "未知版本 $v（可选：$MT_ALL_VERSIONS）"
    return "$MT_EXIT_ERROR"
}

# ── 四线项目名映射（mt.ps1 的 build 阶段等价物）───────────────────────────
# 与 lib/common.sh 的 mt_subproject 必须一致；mt_phase_assert_mapping 负责自证。
mt_phase_subproject() {
    case "${1:-}" in
        1.21.1)        printf '%s' 'neoforge-1.21.1' ;;
        1.20.1)        printf '%s' 'forge-1.20.1' ;;
        26.1.2)        printf '%s' 'neoforge-26.1.2' ;;
        fabric-1.20.1) printf '%s' 'fabric-1.20.1' ;;
        *) return 1 ;;
    esac
}

# 自证：本文件的映射表 == common.sh 的映射表（四线逐条比对）。dry-run 与 build 阶段都会调。
mt_phase_assert_mapping() {
    local v a b bad=0
    for v in $MT_ALL_VERSIONS; do
        a="$(mt_phase_subproject "$v" 2>/dev/null)" || a=""
        b="$(mt_subproject "$v" 2>/dev/null)" || b=""
        if [ -z "$a" ] || [ "$a" != "$b" ]; then
            printf 'CHECK: subproject_mapping=WARN — %s：phases.sh=%s common.sh=%s\n' "$v" "${a:-空}" "${b:-空}"
            bad=1
        fi
    done
    if [ "$bad" -eq 0 ]; then
        printf 'CHECK: subproject_mapping=OK — %s\n' "$MT_ALL_VERSIONS"
        return 0
    fi
    return 1
}

# ── 过渡期委托标记 ───────────────────────────────────────────────────────
# 去重：同一脚本在同一次调用里只打一次，避免 cases/report 的 mark 循环刷屏。
mt_phase_deprecated() {
    local script="${1:-}" batch="${2:-}"
    case " ${MT_DEPRECATED_SEEN:-} " in
        *" $script "*) return 0 ;;
    esac
    MT_DEPRECATED_SEEN="${MT_DEPRECATED_SEEN:-} $script"
    printf 'MT_DEPRECATED: pwsh %s —— 计划在 %s 迁移到 bash\n' "$script" "$batch"
}

# ── pwsh 调用 ────────────────────────────────────────────────────────────
# 为什么自己解析 exe：lib/common.sh 的 mt_pwsh 只接受 `-Command <串>`，-File 调用拿不到 exe。
mt_phase_pwsh_exe() {
    local exe="${MT_PWSH_EXE:-}"
    if [ -n "$exe" ]; then printf '%s' "$exe"; return 0; fi
    if command -v pwsh >/dev/null 2>&1; then command -v pwsh; return 0; fi
    if [ -x "/c/Program Files/PowerShell/7/pwsh.exe" ]; then
        printf '%s' "/c/Program Files/PowerShell/7/pwsh.exe"
        return 0
    fi
    return 127
}

# pwsh -NoProfile -File <scripts/test 下的相对路径> <args...>
mt_phase_pwsh_file() {
    local rel="${1:-}"; shift || true
    local ps1 exe win rc
    # 单独 source 本库（不经 mt.sh）时 MT_REPO_ROOT 可能未设 —— 就地反推一次，避免 set -u 直接炸
    if [ -z "${MT_REPO_ROOT:-}" ]; then
        MT_REPO_ROOT="$(mt_resolve_repo_root "${MT_WIN_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}" 2>/dev/null || true)"
    fi
    if [ -z "${MT_REPO_ROOT:-}" ]; then
        mt_phase_err '无法定位仓库根（MT_REPO_ROOT 未设置，且反推失败）'
        return "$MT_EXIT_ERROR"
    fi
    ps1="$MT_REPO_ROOT/scripts/test/$rel"
    if [ ! -f "$ps1" ]; then
        mt_phase_err "找不到脚本 scripts/test/$rel（pwsh 委托目标缺失）"
        return "$MT_EXIT_ERROR"
    fi
    exe="$(mt_phase_pwsh_exe)" || {
        mt_phase_err "pwsh 不可用（PATH 与 C:/Program Files/PowerShell/7 都没找到）"
        return "$MT_EXIT_ERROR"
    }
    win="$(mt_to_win "$ps1")"
    "$exe" -NoProfile -NonInteractive -File "$win" "$@"
    rc=$?
    if [ "$rc" -eq 127 ]; then
        mt_phase_err "pwsh 调用返回 127（命令不存在），按 ERROR($MT_EXIT_ERROR) 处理"
        return "$MT_EXIT_ERROR"
    fi
    return "$rc"
}

# 阶段退出码 → 机器行（格式对齐 Mt.Phase.psm1:113-171：
#   MT_<NAME>: OK (<elapsed>) / MT_<NAME>: FAIL (<elapsed>) — <原因> / BLOCKED / ERROR）
mt_phase_verdict() {
    local name="${1:-PHASE}" rc="${2:-0}" el
    el="$(mt_phase_elapsed)"
    case "$rc" in
        0)  printf 'MT_%s: OK (%s)\n' "$name" "$el" ;;
        1)  printf 'MT_%s: FAIL (%s) — 阶段返回 1\n' "$name" "$el" >&2 ;;
        11) printf 'MT_%s: BLOCKED (%s) — 阶段返回 11（前置欠缺，非产品缺陷）\n' "$name" "$el" >&2 ;;
        12) printf 'MT_%s: TIMEOUT (%s) — 阶段返回 12\n' "$name" "$el" >&2 ;;
        *)  printf 'MT_%s: ERROR (%s) — 阶段返回 %s\n' "$name" "$el" "$rc" >&2 ;;
    esac
}

# ── dry-run 打印 ─────────────────────────────────────────────────────────
# --dry-run 的语义：打印「将执行什么 + 判据」，不执行、也不打 MT_PHASE:
mt_phase_dry() {
    printf 'DRY-RUN: %s\n' "$1"
    shift || true
    local line
    for line in "$@"; do printf 'DRY-RUN:   %s\n' "$line"; done
}

# ══ 阶段：env（bash 原生，委托 win/env.sh）═════════════════════════════════
mt_phase_env() {
    local v="${1:-}" rc
    mt_phase_require_version "$v" || return $?
    mt_phase_require_repo || return $?
    if [ "${MT_DRY_RUN:-0}" = 1 ]; then
        mt_phase_dry "cd ${MT_REPO_ROOT} && bash scripts/test/win/env.sh --version ${v}" \
            '判据：ENV_RESULT: OK|WARN → PASS(0)；ENV_RESULT: FAIL 或脚本 rc=1 → FAIL(1)；rc=2 → 用法错误(2)'
        return 0
    fi
    ( cd "$MT_REPO_ROOT" && bash "$MT_PHASES_DIR/../env.sh" --version "$v" )
    rc=$?
    mt_phase_verdict ENV "$rc"
    return "$rc"
}

# ══ 阶段：build（bash 原生，cmd //c gradlew.bat :<子项目>:classes）══════════
mt_phase_build() {
    local v="${1:-}" sp rc
    mt_phase_require_version "$v" || return $?
    mt_phase_require_repo || return $?
    sp="$(mt_phase_subproject "$v")" || {
        mt_phase_err "未知版本 $v（无子项目映射）"
        return "$MT_EXIT_ERROR"
    }
    if [ "${MT_DRY_RUN:-0}" = 1 ]; then
        mt_phase_assert_mapping || true
        mt_phase_dry "cd ${MT_REPO_ROOT} && cmd //c \"gradlew.bat :${sp}:classes\"" \
            "版本 ${v} → 子项目 :${sp}（toolchain $(mt_toolchain "$v")）" \
            "判据：gradlew 退出 0 → MT_BUILD: OK；非 0 → MT_BUILD: FAIL；缺 gradlew.bat → ERROR(2)" \
            "预算：$(mt_phase_budget build)s（对齐 mt.ps1 PhaseBudgets.build，bash P1 不强制掐表）"
        return 0
    fi
    ( cd "$MT_REPO_ROOT" && cmd //c "gradlew.bat :${sp}:classes" )
    rc=$?
    mt_phase_verdict BUILD "$rc"
    return "$rc"
}

# ══ 阶段：launch（bash 原生 launch.sh；native 通道过渡期委托 pwsh）════════════
mt_phase_launch() {
    local v="${1:-}" rc
    mt_phase_require_version "$v" || return $?
    mt_phase_require_repo || return $?
    # fabric-1.20.1：走 fabric.sh 的 bash 实现（双 run 目录 + 按侧就绪 marker），不委托 pwsh
    if mt_is_fabric "$v"; then
        mt_fab_phase_launch "$v"
        return $?
    fi
    local channel="${MT_CHANNEL:-autojs}"
    if [ "$channel" = "native" ]; then
        if [ "${MT_DRY_RUN:-0}" = 1 ]; then
            mt_phase_dry "pwsh -File scripts/test/mt_launch.ps1 --version ${v} --channel native（脱离式）" \
                '判据：MT_LAUNCH: OK|BLOCKED|ERROR（mt_launch.ps1 的终态标记）' \
                '原因：native 通道 = 窗口定位 + user32 合成输入，bash 无等价实现（见淘汰路线「不可迁移项」）'
            return 0
        fi
        mt_phase_deprecated 'scripts/test/mt_launch.ps1' 'P4'
        mt_phase_pwsh_file 'mt_launch.ps1' --version "$v" --channel native
        rc=$?
        return "$rc"
    fi
    if [ "${MT_DRY_RUN:-0}" = 1 ]; then
        mt_phase_dry "cd ${MT_REPO_ROOT} && bash scripts/test/win/launch.sh --version ${v}" \
            '判据：LAUNCH: READY → PASS(0)；BLOCKED(11) / TIMEOUT(12) / ERROR(2) 原样透传' \
            '通道：autojs —— launch.sh 只有 AutoJS 就绪判定（不接受 --channel 参数，故不转发）' \
            "预算：MT_LAUNCH_TIMEOUT_SEC 优先，否则 launch.sh 默认（mt.ps1 基线 $(mt_phase_budget launch)s）"
        return 0
    fi
    ( cd "$MT_REPO_ROOT" && bash "$MT_PHASES_DIR/../launch.sh" --version "$v" )
    rc=$?
    mt_phase_verdict LAUNCH "$rc"
    return "$rc"
}

# ══ 阶段：stop（bash 原生，委托 win/stop.sh）═══════════════════════════════
# 参数语义对齐 mt.ps1:783-790：--version | --all + --force / --keep-daemon / --purge-saves。
mt_phase_stop() {
    local v="${1:-}" rc
    local -a args=()
    if [ -n "$v" ]; then args+=(--version "$v"); else args+=(--all); fi
    [ "${MT_STOP_FORCE:-0}" = 1 ] && args+=(--force)
    [ "${MT_STOP_PURGE_SAVES:-0}" = 1 ] && args+=(--purge-saves)
    mt_phase_require_repo || return $?
    if [ "${MT_DRY_RUN:-0}" = 1 ]; then
        mt_phase_dry "cd ${MT_REPO_ROOT} && bash scripts/test/win/stop.sh ${args[*]}" \
            '判据：WIN_STOP: OK → PASS(0)；WIN_STOP: RESIDUAL → FAIL(1)；rc=2 → 用法错误/CIM 不可用' \
            'PURGE: removed|failed — 清存档白名单与 mt_cleanup.ps1:228-265 对齐'
        if [ "${MT_STOP_KEEP_DAEMON:-0}" = 1 ]; then
            mt_phase_dry '--keep-daemon：不传 stop.sh --stop-daemon（Gradle 守护不归阶段管）'
        fi
        return 0
    fi
    if [ "${MT_STOP_FORCE:-0}" = 1 ]; then
        mt_phase_info 'STOP: --force 在 bash 侧是 no-op（stop.sh 恒用 taskkill //T //F，无优雅信号档）'
    fi
    if [ "${MT_STOP_KEEP_DAEMON:-0}" = 1 ]; then
        mt_phase_info 'STOP: --keep-daemon 生效（不传 --stop-daemon，守护保留）'
    fi
    ( cd "$MT_REPO_ROOT" && bash "$MT_PHASES_DIR/../stop.sh" "${args[@]}" )
    rc=$?
    mt_phase_verdict STOP "$rc"
    return "$rc"
}

# ══ 阶段：cases（过渡期委托 pwsh mt_case.ps1，P3 迁移）══════════════════════
mt_phase_cases() {
    local v="${1:-}" casepath="${2:-}" rc percase budget
    # fabric-1.20.1：bash 原生解析 scripts/test/cases/*-fabric-1.20.1.json，不委托 mt_case.ps1
    if mt_is_fabric "$v"; then
        mt_fab_phase_cases "$v" "$casepath"
        return $?
    fi
    mt_phase_require_version "$v" || return $?
    mt_phase_require_repo || return $?
    percase="${MT_CASE_TIMEOUT_SEC:-600}"
    case "$percase" in ''|*[!0-9]*) percase=600 ;; esac

    local -a args
    if [ -n "$casepath" ]; then
        args=(run --case "$casepath" --version "$v" --case-timeout "$percase")
        budget=$((percase + 90))
    else
        args=(run-dir --version "$v" --case-timeout "$percase")
        budget=$((90 + percase))
    fi
    [ "${MT_CHANNEL:-autojs}" != "autojs" ] && args+=(--channel "$MT_CHANNEL")
    [ "${MT_ALLOW_AUTO_ACTIVE:-0}" = 1 ] && args+=(--allow-auto)
    mt_phase_deprecated 'scripts/test/mt_case.ps1' 'P3'

    if [ "${MT_DRY_RUN:-0}" = 1 ]; then
        mt_phase_dry "pwsh -File scripts/test/mt_case.ps1 ${args[*]}" \
            '判据：rc=0 → 全部步骤 PASS；rc=11 → BLOCKED（前置欠缺）；rc=12 → TIMEOUT；其它非 0 → FAIL' \
            "CASES_BUDGET: ${budget}s（单条上限 ${percase}s + 余量 90s；覆写 MT_CASE_TIMEOUT_SEC）"
        return 0
    fi
    mt_phase_pwsh_file 'mt_case.ps1' "${args[@]}"
    rc=$?
    mt_phase_verdict CASES "$rc"
    return "$rc"
}

# ══ 阶段：report（过渡期委托 pwsh mt_report.ps1，P4 迁移）═══════════════════
mt_phase_report() {
    local v="${1:-}" verdict="${2:-}" rc
    # fabric-1.20.1：bash 原生汇总，不委托 mt_report.ps1
    if mt_is_fabric "$v"; then
        mt_fab_phase_report "$v" "$verdict"
        return $?
    fi
    mt_phase_require_version "$v" || return $?
    mt_phase_require_repo || return $?
    local -a args=(collect --version "$v")
    [ -n "$verdict" ] && args+=(--verdict "$verdict")
    mt_phase_deprecated 'scripts/test/mt_report.ps1' 'P4'
    if [ "${MT_DRY_RUN:-0}" = 1 ]; then
        mt_phase_dry "pwsh -File scripts/test/mt_report.ps1 ${args[*]}" \
            '判据：rc=0 → 报告落 reports/<runId>/；rc=2 → 中断/无效运行（原样透传，不折成 1）'
        return 0
    fi
    mt_phase_pwsh_file 'mt_report.ps1' "${args[@]}"
    rc=$?
    return "$rc"
}

# report 的 mark / summary / collect 辅助（全流程用；P1 不做超时预算）
mt_phase_report_mark() {
    local v="${1:-}" phase="${2:-}" result="${3:-}"
    if mt_is_fabric "$v"; then mt_fab_report_mark "$v" "$phase" "$result"; return $?; fi
    [ "${MT_DRY_RUN:-0}" = 1 ] && { mt_phase_dry "pwsh -File scripts/test/mt_report.ps1 mark --version ${v} --phase ${phase} --result ${result}"; return 0; }
    mt_phase_deprecated 'scripts/test/mt_report.ps1' 'P4'
    mt_phase_pwsh_file 'mt_report.ps1' mark --version "$v" --phase "$phase" --result "$result"
}

mt_phase_report_summary() {
    # fabric-1.20.1：bash 原生 SUMMARY.md（旧 pwsh 报告脚本没有 fabric 分支）
    if mt_is_fabric "${1:-}"; then mt_fab_report_summary "${1:-}"; return $?; fi
    [ "${MT_DRY_RUN:-0}" = 1 ] && { mt_phase_dry 'pwsh -File scripts/test/mt_report.ps1 summary'; return 0; }
    mt_phase_deprecated 'scripts/test/mt_report.ps1' 'P4'
    mt_phase_pwsh_file 'mt_report.ps1' summary
}

# ══ 阶段：preflight（过渡期委托 pwsh mt_preflight.ps1，P2 迁移）══════════════
mt_phase_preflight() {
    local v="${1:-}" rc
    local -a args
    if [ -n "$v" ]; then args=(--version "$v"); else args=(--all); fi
    # fabric-1.20.1：bash 原生前置检查（子项目 / 双 run 目录 / gradlew / python / RCON），不委托 pwsh
    if [ -n "$v" ] && mt_is_fabric "$v"; then mt_fab_phase_preflight "$v"; return $?; fi
    [ "${MT_CHANNEL:-autojs}" != "autojs" ] && args+=(--channel "$MT_CHANNEL")
    mt_phase_require_repo || return $?
    mt_phase_deprecated 'scripts/test/mt_preflight.ps1' 'P2'
    if [ "${MT_DRY_RUN:-0}" = 1 ]; then
        mt_phase_dry "pwsh -File scripts/test/mt_preflight.ps1 ${args[*]}" \
            '判据：rc=0 → 前置通过；非 0 → 全流程按 MT_EXIT_PREFLIGHT(10) 中止'
        return 0
    fi
    mt_phase_pwsh_file 'mt_preflight.ps1' "${args[@]}"
    rc=$?
    return "$rc"
}

# ══ 分发：run_phase <版本> <阶段> [用例路径]（对应 mt.ps1 的 Invoke-MtRunPhase）══
mt_run_phase() {
    local v="${1:-}" phase="${2:-}" casepath="${3:-}"
    mt_phase_is_phase "$phase" || {
        mt_phase_err "未知阶段 $phase"
        return "$MT_EXIT_ERROR"
    }
    mt_phase_begin "$phase"
    case "$phase" in
        env)       mt_phase_env "$v" ;;
        build)     mt_phase_build "$v" ;;
        launch)    mt_phase_launch "$v" ;;
        cases)     mt_phase_cases "$v" "$casepath" ;;
        report)    mt_phase_report "$v" ;;
        stop)      if [ -n "$v" ]; then mt_phase_stop "$v"; else mt_phase_stop ""; fi ;;
        preflight) mt_phase_preflight "$v" ;;
        *)         mt_phase_err "未知阶段 $phase"; return "$MT_EXIT_ERROR" ;;
    esac
}
