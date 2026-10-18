#!/usr/bin/env bash
# mt.sh — 模组测试唯一入口（WSL/bash + AutoJS 通道）。
#
# 阶段: preflight | build | env | launch | case | report | stop
#
# 与 pwsh 版的关系：**游戏内流程已改 AutoJS 求值通道**（不再有 inject/截图/键鼠），
# 本入口只保留「能自动化的那一半」：构建、环境投放、脱离式启动、就绪闸门、用例执行、报告、收停。
# 旧 pwsh 的 mt_inject / mt_ime / mt_capture / mt_fire / mt_scope / mt_watchdog 属废除面，不翻译。
#
# 例:
#   scripts/test/mt.sh --version 1.21.1 --phase preflight
#   scripts/test/mt.sh --version 1.21.1 --phase build
#   scripts/test/mt.sh --version 1.21.1 --phase env
#   scripts/test/mt.sh --version 1.21.1 --phase launch
#   scripts/test/mt.sh --version 1.21.1 --phase case --case scripts/test/cases/AUTOJS-SMOKE-1.21.1.json
#   scripts/test/mt.sh --version 1.21.1 --phase case --all
#   scripts/test/mt.sh --version 1.21.1 --phase stop --purge-saves
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/mt_common.sh"

VERSION=""; PHASE=""; CASE=""; ALL=0; PURGE=0; TIMEOUT=""; QUICKPLAY="testworld"; CASE_TIMEOUT="600"
while [ $# -gt 0 ]; do
  case "$1" in
    --version) VERSION="${2:-}"; shift 2 ;;
    --phase) PHASE="${2:-}"; shift 2 ;;
    --case) CASE="${2:-}"; shift 2 ;;
    --all) ALL=1; shift ;;
    --purge-saves) PURGE=1; shift ;;
    --timeout) TIMEOUT="${2:-}"; shift 2 ;;
    --quickplay) QUICKPLAY="${2:-}"; shift 2 ;;
    --case-timeout) CASE_TIMEOUT="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) mt_err "未知参数 $1"; exit 2 ;;
  esac
done

PHASE="${PHASE:-}"
[ -n "$PHASE" ] || PHASE="preflight"
[ -n "$VERSION" ] || VERSION="1.21.1"
VDIR="$(mt_version_dir "$VERSION")" || { mt_err "未知版本 $VERSION"; exit 2; }
RUNDIR="$(mt_run_dir "$VERSION")"
LAUNCH_LOG="$RUNDIR/mt_launch.log"
PIDFILE="$RUNDIR/.mt_launch.pid"
mt_use_jdk "$VERSION"

phase_preflight() {
  local rc=0
  command -v java >/dev/null || { mt_err "缺 java"; rc=1; }
  java -version 2>&1 | head -1 | sed 's/^/MT_PREFLIGHT: java /'
  command -v python3 >/dev/null || { mt_err "缺 python3"; rc=1; }
  [ -x "$MT_ROOT/gradlew" ] || { mt_err "gradlew 不可执行"; rc=1; }
  if ! python3 "$MT_TEST_DIR/lib/mt_case.py" validate --cases-dir "$MT_TEST_DIR/cases" | tail -1; then rc=1; fi
  local legacy
  legacy="$(grep -rl '"op": "inject_\|"op": "screenshot"\|"op": "kubejs_reload"' "$MT_TEST_DIR/cases" --include='*.json' 2>/dev/null | wc -l)"
  if [ "$legacy" -ne 0 ]; then mt_err "用例里仍有 $legacy 个 legacy 驱动原语文件"; rc=1; else mt_line "MT_PREFLIGHT: purity OK (0 legacy ops)"; fi
  local epok=0
  python3 - "$MT_AUTOJS_ENDPOINT" <<'PY' && epok=1
import socket,sys,urllib.parse
u=urllib.parse.urlsplit(sys.argv[1]); h=u.hostname or '127.0.0.1'; p=u.port or 80
s=socket.socket(); s.settimeout(1.0)
try: s.connect((h,p)); print(f"MT_PREFLIGHT: autojs endpoint {h}:{p} TCP OK（游戏可能未启动，属正常）")
except OSError as e: print(f"MT_PREFLIGHT: autojs endpoint {h}:{p} 未监听（游戏未启动时正常）: {e}")
PY
  [ "$(ls "$RUNDIR/mods"/*[Aa]uto[Jj][Ss]*.jar 2>/dev/null | wc -l)" -gt 0 ] \
    && mt_line "MT_PREFLIGHT: autojs jar OK" || mt_warn "run/$VERSION/mods 下没有 autojs jar（实机会 BLOCKED）"
  mt_line "MT_PREFLIGHT: version=$VERSION dir=$VDIR cases=$(find "$MT_TEST_DIR/cases" -name '*.json' -not -path '*/.mt_results/*' | wc -l)"
  return $rc
}

phase_build() {
  local budget="${TIMEOUT:-300}"
  mt_info "构建 :$VDIR:jar（上限 ${budget}s）"
  if ! timeout "$budget" "$MT_ROOT/gradlew" ":$VDIR:jar" --console=plain -q; then
    mt_err "MT_BUILD: FAIL"; return 1
  fi
  mkdir -p "$RUNDIR/mods"
  local jar
  jar="$(ls -t "$MT_ROOT/$VDIR/build/libs/"*.jar 2>/dev/null | grep -v -- '-sources\|-javadoc' | head -1)"
  [ -n "$jar" ] || { mt_err "MT_BUILD: FAIL（找不到产物 jar）"; return 1; }
  cp -f "$jar" "$RUNDIR/mods/"
  mt_line "MT_BUILD: OK $(basename "$jar") → run/$VERSION/mods"
}

phase_env() {
  mkdir -p "$RUNDIR/mods" "$RUNDIR/config"
  local aj="$RUNDIR/config/autojs.json"
  if [ ! -f "$aj" ]; then
    cat > "$aj" <<'JSON'
{
  "enabled": true,
  "unrestricted": true,
  "bindAddress": "127.0.0.1",
  "port": 25585,
  "enableServerSide": true,
  "enableClientSide": true,
  "token": "",
  "evalTimeoutMs": 5000,
  "maxResultChars": 100000,
  "logEval": true
}
JSON
    mt_line "MT_ENV: wrote config/autojs.json"
  fi
  local n; n="$(ls "$RUNDIR/mods"/*[Aa]uto[Jj][Ss]*.jar 2>/dev/null | wc -l)"
  [ "$n" -gt 0 ] || mt_warn "run/$VERSION/mods 缺 autojs jar"
  [ -d "$RUNDIR/kubejs" ] || mt_warn "run/$VERSION/kubejs 不存在（tick 采样器探针不可用）"
  [ -d "$RUNDIR/saves/$QUICKPLAY" ] || mt_warn "世界 $QUICKPLAY 不存在（先建或换 --quickplay）"
  mt_line "MT_ENV: OK run=$RUNDIR world=$QUICKPLAY autojs_jars=$n"
}

phase_launch() {
  local budget="${TIMEOUT:-900}"
  if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
    mt_err "已有本线客户端在跑（pid $(cat "$PIDFILE")）—— 先 --phase stop"; return 1
  fi
  mkdir -p "$RUNDIR"
  : > "$LAUNCH_LOG"
  mt_info "脱离式启动 :$VDIR:runClient -Pquickplay=$QUICKPLAY（日志 $LAUNCH_LOG）"
  ( cd "$MT_ROOT" && exec setsid ./gradlew ":$VDIR:runClient" "-Pquickplay=$QUICKPLAY" --console=plain ) \
    > "$LAUNCH_LOG" 2>&1 &
  echo $! > "$PIDFILE"
  local pid; pid="$(cat "$PIDFILE")"
  local start now elapsed last_beat=0
  start="$(date +%s)"
  while :; do
    now="$(date +%s)"; elapsed=$((now-start))
    if ! kill -0 "$pid" 2>/dev/null; then
      mt_err "MT_LAUNCH: FAIL — 客户端进程已退出（见 $LAUNCH_LOG 尾部）"
      tail -20 "$LAUNCH_LOG" >&2; return 1
    fi
    if python3 - "$MT_ROOT" "$MT_AUTOJS_ENDPOINT" <<'PY' 2>/dev/null
import sys,os
sys.path.insert(0,os.path.join(sys.argv[1],'scripts/test/lib'))
from mt_autojs import AutoJs
c=AutoJs(endpoint=sys.argv[2]); p=c.probe(timeout_ms=2500,poll_ms=500)
if not p['ok']: raise SystemExit(1)
r=c.eval_js("!!(Java.loadClass('net.minecraft.client.Minecraft').getInstance().player)",side='client',mode='bool',timeout_ms=5000)
raise SystemExit(0 if (r.get('ok') and str(r.get('value')).strip()=='true') else 1)
PY
    then
      mt_line "MT_LAUNCH: OK (elapsed=$(( $(date +%s)-start ))s, world=$QUICKPLAY)"
      return 0
    fi
    if [ $elapsed -ge "$budget" ]; then
      mt_err "MT_LAUNCH: TIMEOUT（${budget}s，日志 $(wc -c < "$LAUNCH_LOG") 字节）"; return 12
    fi
    if [ $((elapsed-last_beat)) -ge 30 ]; then
      mt_line "MT_WAIT: ${elapsed}s / ${budget}s（日志 $(wc -c < "$LAUNCH_LOG") 字节）"; last_beat=$elapsed
    fi
    sleep 5
  done
}

phase_case() {
  mkdir -p "$RUNDIR/logs"
  local args=(run --run-dir "$RUNDIR" --cases-dir "$MT_TEST_DIR/cases" --case-timeout "$CASE_TIMEOUT")
  if [ -n "$CASE" ]; then args+=(--case "$CASE"); else args+=(--version "$VERSION"); fi
  python3 "$MT_TEST_DIR/lib/mt_case.py" "${args[@]}"
}

phase_report() {
  python3 - "$MT_TEST_DIR/cases/.mt_results" <<'PY'
import glob,json,os,sys,collections
d=sys.argv[1]; rows=[]
for f in sorted(glob.glob(os.path.join(d,'*.json'))):
    try: rows.append(json.load(open(f,encoding='utf-8')))
    except Exception: pass
c=collections.Counter(r['verdict'] for r in rows)
print("MT_REPORT: cases=%d " % len(rows) + " ".join(f"{k}={v}" for k,v in sorted(c.items())))
for r in rows:
    if r['verdict']!='PASS': print(f"  - {r['case_id']}: {r['verdict']} {r.get('detail','')[:120]}")
raise SystemExit(0 if all(r['verdict']=='PASS' for r in rows) else 1)
PY
}

phase_stop() {
  local killed=0
  if [ -f "$PIDFILE" ]; then
    local pid; pid="$(cat "$PIDFILE")"
    if kill -0 "$pid" 2>/dev/null; then
      kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null
      for _ in $(seq 1 20); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
      kill -0 "$pid" 2>/dev/null && { kill -KILL -- "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null; }
      killed=1
    fi
    rm -f "$PIDFILE"
  fi
  local purged=0
  if [ "$PURGE" -eq 1 ]; then
    if [ -d "$RUNDIR/saves" ]; then
      purged="$(find "$RUNDIR/saves" -mindepth 1 -maxdepth 1 | wc -l)"
      rm -rf "$RUNDIR/saves"/* 2>/dev/null
    fi
    mt_line "MT_CLEANUP_SAVES: PURGED $purged"
  fi
  mt_line "MT_STOP: OK (killed=$killed purged=$purged)"
}

case "$PHASE" in
  preflight) phase_preflight ;;
  build) phase_build ;;
  env) phase_env ;;
  launch) phase_launch ;;
  case) phase_case ;;
  report) phase_report ;;
  stop) phase_stop ;;
  *) mt_err "未知阶段 $PHASE"; exit 2 ;;
esac
