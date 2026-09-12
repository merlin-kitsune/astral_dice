#!/usr/bin/env bash
# mt_launch — 启动 runClient 并等待进入世界（阶段 L），收尾自动开放局域网。
#
# 就绪判据（沿用既有约定）: 基础等待 30s，随后轮询 ModernFix 加载完成日志
#   "Total time to load game and open world was"
# 每 15s 复检一次，180s 上限；出现崩溃报告或进程退出即判失败。
# 进入世界后自动执行 /publish 25565（供 minecraft MCP 第二玩家 LLMBot 连接）。
#
# 用法: mt_launch.sh --version 1.21.1 [--publish|--no-publish]
set -uo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib/paths.sh"
. "$(dirname "${BASH_SOURCE[0]}")/lib/phase.sh"

VERSION=""; DO_PUBLISH=1
while [ $# -gt 0 ]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --no-publish) DO_PUBLISH=0; shift ;;
    --publish) DO_PUBLISH=1; shift ;;
    *) echo "MT_ERROR: 未知参数 $1" >&2; exit $MT_EXIT_ERROR ;;
  esac
done
[ -n "$VERSION" ] || { echo "MT_ERROR: 必须指定 --version" >&2; exit $MT_EXIT_ERROR; }
mt_resolve "$VERSION" || exit $MT_EXIT_ERROR

mt_phase_begin "launch ($VERSION)"

if [ ! -f "$MT_CLIENT_WORLD/level.dat" ]; then
  mt_blocked "launch" "测试世界不存在，请先执行 mt.sh --phase env"
  exit $MT_EXIT_BLOCKED
fi

rm -f "$MT_LATEST_LOG"
# 服务端权威通道（KubeJS 探针追加写）必须每次运行清零，否则上一轮标记会污染本轮断言
rm -f "$MT_RUN_DIR/astral_probe.log"
rm -rf "$MT_CRASH_DIR"
mkdir -p "$MT_SHOT_DIR"

LAUNCH_LOG="$MT_RUN_DIR/runclient_launch.log"
: > "$LAUNCH_LOG"

mt_info "启动 runClient(quickplay=$MT_WORLD_NAME)"
bash "$MT_ROOT/gradlew" "$MT_TASK_CLIENT" "-Pquickplay=$MT_WORLD_NAME" --console=plain \
  > "$LAUNCH_LOG" 2>&1 &
gradle_pid=$!

# 基础等待
sleep 30

deadline=$(( $(date +%s) + 180 ))
entered=0
while [ "$(date +%s)" -lt "$deadline" ]; do
  if [ -f "$MT_LATEST_LOG" ] && grep -q "Total time to load game and open world was" "$MT_LATEST_LOG"; then
    entered=1; break
  fi
  # 崩溃报告出现即失败（启动期大量良性 Exception 不应中止）
  if [ -d "$MT_CRASH_DIR" ] && [ -n "$(ls -A "$MT_CRASH_DIR"/*.txt 2>/dev/null)" ]; then
    mt_error "launch" "检测到崩溃报告"
    exit $MT_EXIT_ERROR
  fi
  if ! kill -0 "$gradle_pid" 2>/dev/null; then
    mt_error "launch" "runClient 进程已退出，启动失败"
    tail -n 30 "$LAUNCH_LOG" >&2
    exit $MT_EXIT_ERROR
  fi
  sleep 15
done

if [ "$entered" -ne 1 ]; then
  mt_blocked "launch" "未在时限内进入世界"
  exit $MT_EXIT_BLOCKED
fi

# 兼容性信号（1.21.1: Sodium/Iris；1.20.1: Embeddium/Oculus）
if [ "$VERSION" = "1.21.1" ]; then
  grep -q "Sodium" "$MT_LATEST_LOG" && mt_info "SODIUM_LOADED=true" || mt_warn "SODIUM_LOADED=false"
  grep -q "Iris"   "$MT_LATEST_LOG" && mt_info "IRIS_LOADED=true"   || mt_warn "IRIS_LOADED=false"
else
  grep -qi "Embeddium" "$MT_LATEST_LOG" && mt_info "EMBEDDIUM_LOADED=true" || mt_info "EMBEDDIUM_LOADED=false(dev run 预期)"
  grep -qi "Oculus"    "$MT_LATEST_LOG" && mt_info "OCULUS_LOADED=true"    || mt_info "OCULUS_LOADED=false(dev run 预期)"
fi

# KubeJS 脚本健康（进入世界后第一步）
"$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_assert.py" kubejs --version "$VERSION" || mt_warn "KubeJS server.log 非 0 errors"

# 开放局域网，供第二玩家（LLMBot）接入
if [ "$DO_PUBLISH" -eq 1 ]; then
  sleep 2
  if "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_inject.py" cmd --command "/publish $MT_PUBLISH_PORT" \
       --version "$VERSION" >/dev/null 2>&1; then
    sleep 4
    if grep -qiE "publish|open to lan|local network|$MT_PUBLISH_PORT" "$MT_LATEST_LOG" 2>/dev/null; then
      mt_info "MT_PUBLISH: OK — 日志已留痕"
    else
      mt_info "MT_PUBLISH: SENT — 连通性由会话层 minecraft MCP 验证（BOT_JOINED）"
    fi
  else
    mt_warn "MT_PUBLISH: FAILED — 注入失败，双人条目将不可用"
  fi
fi

mt_ok "LAUNCH" "已进入世界（quickplay=$MT_WORLD_NAME）"
exit $MT_EXIT_PASS
