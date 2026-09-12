#!/usr/bin/env bash
# mt_build — Gradle 构建守护（阶段 B）。
#
# 规则来源：AGENTS「Gradle 构建守护规则」。单次执行最多 N 秒；超时后先看输出是否已含
# BUILD SUCCESSFUL（构建已完成但进程未退出），否则强杀进程树并按产物 jar 时间戳判定
# 是否实际完成；未完成则重试，最多 3 次。
# 与旧流程的差异：产物校验按**当前版本子项目**取值，不再硬编码 neoforge 目录。
#
# 用法: mt_build.sh --version 1.21.1 [--timeout 60] [--retries 3]
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib/paths.sh"
. "$(dirname "${BASH_SOURCE[0]}")/lib/phase.sh"

VERSION=""; TIMEOUT=60; RETRIES=3
while [ $# -gt 0 ]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --timeout) TIMEOUT="$2"; shift 2 ;;
    --retries) RETRIES="$2"; shift 2 ;;
    *) echo "MT_ERROR: 未知参数 $1" >&2; exit $MT_EXIT_ERROR ;;
  esac
done
[ -n "$VERSION" ] || { echo "MT_ERROR: 必须指定 --version" >&2; exit $MT_EXIT_ERROR; }
mt_resolve "$VERSION" || exit $MT_EXIT_ERROR

mt_phase_begin "build ($VERSION)"

JAR_DIR="$MT_ROOT/$MT_SUBPROJECT/build/libs"
LOG="$MT_ROOT/temp/mt_build_${VERSION}_$(date +%s).log"
mkdir -p "$MT_ROOT/temp"

jar_stamp() {  # 产物 jar 名+修改时间，用于判定构建是否真的产出
  [ -d "$JAR_DIR" ] || return 0
  for f in "$JAR_DIR"/*.jar; do
    [ -e "$f" ] || continue
    printf '%s %s\n' "$(basename "$f")" "$(date -r "$f" +%s 2>/dev/null || stat -c %Y "$f")"
  done
}

for attempt in $(seq 1 "$RETRIES"); do
  mt_info "[尝试 $attempt/$RETRIES] gradlew $MT_TASK_BUILD (超时 ${TIMEOUT}s)"
  before="$(jar_stamp)"
  : > "$LOG"

  bash "$MT_ROOT/gradlew" "$MT_TASK_BUILD" --console=plain > "$LOG" 2>&1 &
  pid=$!

  elapsed=0
  while kill -0 "$pid" 2>/dev/null && [ "$elapsed" -lt "$TIMEOUT" ]; do
    sleep 2; elapsed=$((elapsed + 2))
  done

  if kill -0 "$pid" 2>/dev/null; then
    # 超时分支
    if grep -q "BUILD SUCCESSFUL" "$LOG"; then
      mt_warn "输出已含 BUILD SUCCESSFUL，构建实际已完成，终止残留进程"
    fi
    "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_env.py" kill --version "$VERSION" >/dev/null 2>&1 || true
    sleep 2
  fi

  if grep -q "BUILD FAILED" "$LOG"; then
    mt_error "build" "编译失败（见 $LOG）"
    tail -n 15 "$LOG" >&2
    exit $MT_EXIT_ERROR
  fi

  after="$(jar_stamp)"
  if [ -n "$after" ] && [ "$after" != "$before" ]; then
    mt_ok "BUILD" "产物已更新：$(printf '%s' "$after" | head -n1)"
    exit $MT_EXIT_PASS
  fi
  if grep -q "BUILD SUCCESSFUL" "$LOG" && [ -n "$after" ]; then
    mt_ok "BUILD" "输出含 BUILD SUCCESSFUL 且产物存在（时间戳未变）"
    exit $MT_EXIT_PASS
  fi

  mt_warn "本轮未见构建结果，产物未更新，准备重试"
done

mt_error "build" "$RETRIES 次尝试均失败"
exit $MT_EXIT_ERROR
