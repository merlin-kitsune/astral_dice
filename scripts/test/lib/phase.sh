#!/usr/bin/env bash
# mt — 阶段标记、计时与退出码（bash 侧统一输出契约）。
# 所有阶段级结论都是单行机器可读标记，供 mt_report.py 与人工快速判定。

# 退出码（与 AGENTS「失败处理」表一致）
MT_EXIT_PASS=0
MT_EXIT_FAIL=1
MT_EXIT_ERROR=2
MT_EXIT_PREFLIGHT=10
MT_EXIT_BLOCKED=11

MT_PHASE_START=0

mt_phase_begin() {
  local name="$1"
  MT_PHASE_START=$(date +%s)
  printf '\n===== MT_PHASE: %s =====\n' "$name"
}

mt_phase_elapsed() {
  local now; now=$(date +%s)
  local secs=$(( now - MT_PHASE_START ))
  if [ "$secs" -ge 60 ]; then printf '%dm%02ds' $(( secs / 60 )) $(( secs % 60 ))
  else printf '%ds' "$secs"; fi
}

mt_ok() {
  local name="$1"; shift
  printf 'MT_%s: OK (%s)%s\n' "$name" "$(mt_phase_elapsed)" "${1:+ — $1}"
}

mt_fail() {
  local name="$1" code="${2:-$MT_EXIT_FAIL}" reason="${3:-}"
  printf 'MT_%s: FAIL (%s)%s\n' "$name" "$(mt_phase_elapsed)" "${reason:+ — $reason}" >&2
  return "$code"
}

mt_blocked() {
  local name="$1" reason="${2:-未知阻塞}"
  printf 'MT_%s: BLOCKED (%s) — %s\n' "$name" "$(mt_phase_elapsed)" "$reason" >&2
}

mt_error() {
  local name="$1" reason="${2:-未知错误}"
  printf 'MT_%s: ERROR (%s) — %s\n' "$name" "$(mt_phase_elapsed)" "$reason" >&2
}

mt_info()  { printf 'MT_INFO: %s\n' "$*"; }
mt_warn()  { printf 'MT_WARN: %s\n' "$*" >&2; }
