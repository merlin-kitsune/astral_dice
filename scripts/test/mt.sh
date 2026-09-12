#!/usr/bin/env bash
# mt.sh — 模组测试唯一入口（bash + python 工具链）。
#
# 测试顺序（硬性）:
#   前置检查 → 1.21.1 全流程 → 判定通过后才执行 → 1.20.1 全流程 → 总览
#   两个版本都给出独立的通过/失败结论；1.21.1 不通过时 1.20.1 记为「因门控未执行」。
#
# 阶段: preflight | build | env | launch | cases | report | stop
#
# 用法:
#   bash scripts/test/mt.sh                                  # 全流程（两版本，带门控）
#   bash scripts/test/mt.sh --version 1.21.1 --phase build   # 单阶段
#   bash scripts/test/mt.sh --version 1.21.1 --case cases/X.json
#   bash scripts/test/mt.sh --version 1.21.1 --new ember_chip     # 先生成条目再执行
#   bash scripts/test/mt.sh --phase stop
#
# 退出清理（避免进程泄漏）:
#   全流程会在退出前自动收停本流程进程与 Gradle 守护（正常结束 / 失败 / Ctrl-C
#   三条路径都覆盖），实现见 mt_cleanup.py。单阶段模式**默认不清理**，因为
#   launch → cases 是分步进行的，需要让客户端在两步之间保持运行；如需强制，
#   加 --cleanup-on-exit，或用 --no-cleanup-on-exit 关闭全流程的自动清理。
#   若条目失败且 on_fail=keep_game_running，会落 .mt_keep_alive 标记，
#   此时自动清理只提示不杀进程，保留现场供取证（取证完用 --phase stop --force）。
set -uo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$TEST_DIR/lib/paths.sh"
. "$TEST_DIR/lib/phase.sh"

VERSION=""; PHASE=""; CASE=""; GEN_NEW=""; GEN_FEATURE=""; GEN_SPEC=""
CLEANUP_MODE=""          # "" = 按场景默认；1 = 强制开启；0 = 强制关闭
STOP_FORCE=""; STOP_KEEP_DAEMON=""   # 仅 --phase stop 使用，转发给 mt_cleanup.py
while [ $# -gt 0 ]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --phase)   PHASE="$2"; shift 2 ;;
    --case)    CASE="$2"; shift 2 ;;
    --new)     GEN_NEW="$2"; shift 2 ;;
    --feature) GEN_FEATURE="$2"; shift 2 ;;
    --spec)    GEN_SPEC="$2"; shift 2 ;;
    --cleanup-on-exit)    CLEANUP_MODE=1; shift ;;
    --no-cleanup-on-exit) CLEANUP_MODE=0; shift ;;
    --force)       STOP_FORCE="--force"; shift ;;
    --keep-daemon) STOP_KEEP_DAEMON="--keep-daemon"; shift ;;
    -h|--help) sed -n '2,23p' "$0"; exit 0 ;;
    *) echo "MT_ERROR: 未知参数 $1" >&2; exit $MT_EXIT_ERROR ;;
  esac
done

# ── 退出清理（唯一实现见 mt_cleanup.py）─────────────────────────────────
# 默认策略：全流程开、单阶段关（launch → cases 分步执行需客户端存活）。
MT_DO_CLEANUP=0
MT_CLEANUP_DONE=0
mt_auto_cleanup() {
  [ "$MT_CLEANUP_DONE" -eq 1 ] && return 0
  MT_CLEANUP_DONE=1
  [ "$MT_DO_CLEANUP" -eq 1 ] || return 0
  echo ""
  mt_phase_begin "cleanup (auto)"
  local out rc
  out="$("$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_cleanup.py" run --quiet 2>&1)"; rc=$?
  # 展示给人看的行（机器可读结论行单独取出，不混进日志）
  printf '%s\n' "$out" | grep -v '^MT_CLEANUP_RESULT:' || true
  if printf '%s' "$out" | grep -q 'MT_CLEANUP_RESULT: SKIP'; then
    mt_warn "CLEANUP" "按失败取证标记保留了游戏现场（未收停）—— 取证完用 --phase stop --force 释放"
    return 0
  fi
  if [ "$rc" -eq 0 ]; then
    mt_ok "CLEANUP" "退出清理完成，无本流程残留进程"
    return 0
  fi
  mt_warn "CLEANUP" "退出清理后仍有残留，详见上方 mt_cleanup 输出"
  return 1
}
mt_on_exit() {
  local rc=$?
  trap - EXIT INT TERM
  mt_auto_cleanup || true
  exit "$rc"
}
trap 'mt_on_exit' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# 显式开关优先于任何场景默认（放在分支之前，stop / 生成条目等早退路径同样生效）
[ -n "$CLEANUP_MODE" ] && MT_DO_CLEANUP="$CLEANUP_MODE"

# ── 运行标识（bash/python 两侧共享）──────────────────────────────────────
RUN_ID="$("$MT_PYTHON" -c "
import sys; sys.path.insert(0, r'$MT_TEST_DIR_WIN/lib')
from mt_paths import active_run_id; print(active_run_id())")"

# ── 需要生成条目时先交给子技能 ──────────────────────────────────────────
if [ -n "$GEN_NEW$GEN_FEATURE$GEN_SPEC" ]; then
  [ -n "$VERSION" ] || { echo "MT_ERROR: 生成条目需同时指定 --version" >&2; exit $MT_EXIT_ERROR; }
  if [ -n "$GEN_NEW" ];     then "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_gen_case.py" --version "$VERSION" --new "$GEN_NEW" || exit $MT_EXIT_ERROR; fi
  if [ -n "$GEN_FEATURE" ]; then "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_gen_case.py" --version "$VERSION" --feature "$GEN_FEATURE" || exit $MT_EXIT_ERROR; fi
  if [ -n "$GEN_SPEC" ];    then "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_gen_case.py" --version "$VERSION" --spec "$GEN_SPEC" || exit $MT_EXIT_ERROR; fi
  exit $MT_EXIT_PASS
fi

# ── stop 阶段独立可用 ───────────────────────────────────────────────────
# --force / --keep-daemon 在此阶段透传给 mt_cleanup.py（本身不参与阶段编排）。
if [ "$PHASE" = "stop" ]; then
  if [ -n "$VERSION" ]; then
    bash "$MT_TEST_DIR_WIN/mt_stop.sh" --version "$VERSION" $STOP_FORCE $STOP_KEEP_DAEMON
  else
    bash "$MT_TEST_DIR_WIN/mt_stop.sh" --all $STOP_FORCE $STOP_KEEP_DAEMON
  fi
  exit $?
fi

# ── 单阶段模式 ──────────────────────────────────────────────────────────
# 单阶段默认不清理（launch → cases 需分步执行、客户端要活着）；显式开关已在上面生效。
run_phase() {
  local v="$1" phase="$2"
  case "$phase" in
    build)  bash "$MT_TEST_DIR_WIN/mt_build.sh"  --version "$v" ;;
    env)    "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_env.py" mods --version "$v" \
              && "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_env.py" world --version "$v" "${SEED_ARGS[@]}" ;;
    launch) bash "$MT_TEST_DIR_WIN/mt_launch.sh" --version "$v" ;;
    cases)  if [ -n "$CASE" ]; then "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_case.py" run --version "$v" --case "$CASE"
            else "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_case.py" run-dir --version "$v"; fi ;;
    report) "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_report.py" collect --version "$v" ;;
    *) echo "MT_ERROR: 未知阶段 $phase" >&2; return $MT_EXIT_ERROR ;;
  esac
}
SEED_ARGS=()
[ "$PHASE" = "env" ] && [ -f "$TEST_DIR/resources/testworld-seed-1.20.1.zip" ] && SEED_ARGS=(--seed)

if [ -n "$PHASE" ]; then
  if [ -z "$VERSION" ]; then
    # 无 --version 时对两个版本顺序执行该阶段
    rc=0
    for v in "${MT_VERSIONS[@]}"; do mt_resolve "$v" || exit $MT_EXIT_ERROR; run_phase "$v" "$PHASE" || rc=$?; done
    exit $rc
  fi
  mt_resolve "$VERSION" || exit $MT_EXIT_ERROR
  run_phase "$VERSION" "$PHASE"; exit $?
fi

# ── 全流程 ──────────────────────────────────────────────────────────────
# 全流程默认开启退出清理（可用 --no-cleanup-on-exit 关闭）。
[ -z "$CLEANUP_MODE" ] && MT_DO_CLEANUP=1 || MT_DO_CLEANUP="$CLEANUP_MODE"
printf '\n########## MT RUN %s ##########\n' "$RUN_ID"

mt_phase_begin "preflight"
"$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_preflight.py" --all || {
  echo "MT_RUN: ABORT（前置失败）" >&2
  echo "提示: 如需清理前置检查发现的残留进程，执行 bash scripts/test/mt.sh --phase stop" >&2
  exit $MT_EXIT_PREFLIGHT
}
"$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_report.py" mark --version 1.21.1 --phase preflight --result PASS
"$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_report.py" mark --version 1.20.1 --phase preflight --result PASS

overall=$MT_EXIT_PASS
gate_open=1

for v in "${MT_VERSIONS[@]}"; do
  mt_resolve "$v" || exit $MT_EXIT_ERROR
  echo ""
  printf '########## MT VERSION: %s ##########\n' "$v"

  if [ "$v" = "1.20.1" ] && [ "$gate_open" -ne 1 ]; then
    mt_blocked "version-$v" "1.21.1 未通过，按测试顺序门控不执行 1.20.1"
    "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_report.py" mark --version "$v" --phase cases --result "GATED"
    break
  fi

  vrc=0
  mt_phase_begin "build";  run_phase "$v" build  || vrc=$?
  "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_report.py" mark --version "$v" --phase build \
      --result "$([ "$vrc" -eq 0 ] && echo PASS || echo FAIL)"

  if [ "$vrc" -eq 0 ]; then
    mt_phase_begin "env"; run_phase "$v" env || vrc=$?
    "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_report.py" mark --version "$v" --phase env \
        --result "$([ "$vrc" -eq 0 ] && echo PASS || echo FAIL)"
  fi

  if [ "$vrc" -eq 0 ]; then
    mt_phase_begin "launch"; run_phase "$v" launch || vrc=$?
    "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_report.py" mark --version "$v" --phase launch \
        --result "$([ "$vrc" -eq 0 ] && echo PASS || echo FAIL)"
    # 快照点：此后所有日志断言只看增量区间
    [ "$vrc" -eq 0 ] && "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_assert.py" snapshot --version "$v"
  fi

  if [ "$vrc" -eq 0 ]; then
    mt_phase_begin "cases"; run_phase "$v" cases || vrc=$?
    "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_report.py" mark --version "$v" --phase cases \
        --result "$([ "$vrc" -eq 0 ] && echo PASS || echo FAIL)"
  fi

  mt_phase_begin "report"
  "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_report.py" collect --version "$v" \
      --verdict "$([ "$vrc" -eq 0 ] && echo PASS || echo FAIL)"
  bash "$MT_TEST_DIR_WIN/mt_stop.sh" --version "$v" >/dev/null 2>&1 || true

  if [ "$vrc" -eq 0 ]; then
    printf 'MT_VERSION_VERDICT: %s = PASS\n' "$v"
  else
    printf 'MT_VERSION_VERDICT: %s = FAIL (exit=%s)\n' "$v" "$vrc"
    overall=$MT_EXIT_FAIL
    gate_open=0   # 关闭门控：1.20.1 不再执行
  fi
done

mt_phase_begin "summary"
"$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_report.py" summary || overall=$MT_EXIT_FAIL

# 退出清理走在这里（早于最终判定行），EXIT 钩子只是兜底（Ctrl-C / 异常退出）
mt_auto_cleanup || true

echo ""
if [ "$overall" -eq 0 ]; then
  printf 'MT_RUN: PASS — 两版本均通过\n'
else
  printf 'MT_RUN: FAIL — 见 reports/%s/SUMMARY.md\n' "$RUN_ID"
fi
exit "$overall"
