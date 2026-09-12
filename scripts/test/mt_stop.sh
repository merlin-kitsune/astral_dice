#!/usr/bin/env bash
# mt_stop — 显式收停游戏与构建进程（阶段 D 的收尾动作）。
#
# 实现已收敛到 mt_cleanup.py（唯一来源）：本脚本只做参数映射，
# 与「流程退出时的自动清理」走完全相同的代码路径，避免两套逻辑各自漂移。
#
# 与旧流程的差异：不再「按进程名 java 全杀」，改为按 gradle 任务选择器 +
# run 目录精确匹配本流程进程，不会误杀用户其它 Java 程序 / IDE 语言服务器。
#
# 用法:
#   mt_stop.sh --version 1.21.1     只收停该版本（不动 Gradle 守护）
#   mt_stop.sh --all                全版本收停，并停 Gradle 守护
#   mt_stop.sh --all --keep-daemon  全版本收停，保留 Gradle 守护（构建热态）
#   mt_stop.sh --all --force        忽略失败取证标记（.mt_keep_alive）强制收停
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib/paths.sh"
. "$(dirname "${BASH_SOURCE[0]}")/lib/phase.sh"

TARGETS=()
MODE=""
FORCE=""
KEEP_DAEMON=""
while [ $# -gt 0 ]; do
  case "$1" in
    --version) TARGETS+=("$2"); MODE="version"; shift 2 ;;
    --all) MODE="all"; shift ;;
    --force) FORCE="--force"; shift ;;
    --keep-daemon) KEEP_DAEMON="--keep-daemon"; shift ;;
    *) echo "MT_ERROR: 未知参数 $1" >&2; exit $MT_EXIT_ERROR ;;
  esac
done
[ -n "$MODE" ] || { echo "MT_ERROR: 必须指定 --version 或 --all" >&2; exit $MT_EXIT_ERROR; }

ARGS=()
if [ "$MODE" = "version" ]; then
  for v in "${TARGETS[@]}"; do ARGS+=("--version" "$v"); done
fi

mt_phase_begin "stop"
"$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_cleanup.py" run $FORCE $KEEP_DAEMON "${ARGS[@]+"${ARGS[@]}"}"
rc=$?
if [ "$rc" -eq 0 ]; then
  mt_ok "STOP" "收停完成，无本流程残留进程"
else
  mt_warn "STOP" "收停后仍有残留，详见上方 mt_cleanup 输出"
fi
exit "$rc"
