#!/usr/bin/env bash
# mt_common.sh — WSL/bash 测试台的公共层（路径 / 配置 / 日志 / 版本映射）。
# 与 pwsh 版 Mt.Paths.psm1 + Mt.Conf.psm1 同口径：mt.conf 逐行 KEY=VALUE，缺文件回落内置默认。
set -uo pipefail

MT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
MT_TEST_DIR="$MT_ROOT/scripts/test"
MT_CONF="${MT_CONF:-$MT_TEST_DIR/mt.conf}"

mt_conf_get() { # $1=key $2=default
  local key="$1" def="${2:-}"
  [ -f "$MT_CONF" ] || { printf '%s' "$def"; return; }
  awk -v k="$key" '
    /^[[:space:]]*#/ {next} /^[[:space:]]*$/ {next}
    { line=$0; sub(/^[[:space:]]+/,"",line); n=index(line,"="); if(n==0) next;
      key=substr(line,1,n-1); val=substr(line,n+1); gsub(/^[[:space:]]+|[[:space:]]+$/,"",val);
      gsub(/^["\x27]|["\x27]$/,"",val); if(key==k){print val; exit} }' "$MT_CONF" 2>/dev/null | head -1 \
    | { read -r v && [ -n "$v" ] && printf '%s' "$v" || printf '%s' "$def"; }
}

MT_AUTOJS_ENDPOINT="$(mt_conf_get MT_AUTOJS_ENDPOINT http://127.0.0.1:25585/mcp)"
MT_AUTOJS_TIMEOUT_MS="$(mt_conf_get MT_AUTOJS_TIMEOUT_MS 15000)"
export MT_AUTOJS_ENDPOINT MT_AUTOJS_TIMEOUT_MS

mt_line() { printf '%s\n' "$*"; }
mt_info() { printf '[mt] %s\n' "$*"; }
mt_warn() { printf 'MT_WARN: %s\n' "$*"; }
mt_err()  { printf 'MT_ERROR: %s\n' "$*" >&2; }

mt_version_dir() {
  case "$1" in
    1.21.1) echo "neoforge-1.21.1" ;;
    1.20.1) echo "forge-1.20.1" ;;
    26.1.2) echo "neoforge-26.1.2" ;;
    fabric|fabric-1.20.1) echo "fabric-1.20.1" ;;
    *) return 1 ;;
  esac
}

mt_version_jdk() {
  case "$1" in
    1.20.1|fabric|fabric-1.20.1) echo 17 ;;
    1.21.1) echo 21 ;;
    26.1.2) echo 25 ;;
    *) echo 21 ;;
  esac
}

mt_use_jdk() {
  local v; v="$(mt_version_jdk "$1")"
  if [ -x "/opt/zulu/zulu$v/bin/java" ]; then
    export JAVA_HOME="/opt/zulu/zulu$v"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
}

mt_versions() { echo "1.21.1 1.20.1 26.1.2 fabric-1.20.1"; }

mt_run_dir() { echo "$MT_ROOT/run/$1"; }
