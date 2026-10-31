#!/usr/bin/env bash
# 与「原始项目路径」（hgfs 镜像）同步内容。
#
# 用法：
#   bash scripts/dev/rsync-original.sh pull            # 原始 → 本机（默认 dry-run，只显示差异）
#   bash scripts/dev/rsync-original.sh pull --apply    # 真正执行
#   bash scripts/dev/rsync-original.sh push --apply    # 本机 → 原始
#   bash scripts/dev/rsync-original.sh diff            # 同 pull 的 dry-run（习惯用词）
#
# 说明：
# - 脚本按自身位置解析本机仓库根；原始根默认 /mnt/hgfs/MCProject，可用 ORIGINAL_ROOT 覆盖。
# - 原始目录名 = 本机仓库目录名（astral_dice_multiloader / ...-next / starengine / starengine_next）。
# - 默认不同步：.git、build/、.gradle/、run 的日志/存档/崩溃报告、temp/、.idea/。
# - 默认**不** --delete；确实需要删除对侧多余文件时加 --delete（危险，先 dry-run 看清单）。
# - 冲突口径见 docs/handover-2026-10-31-local-disk-workspace.md：本机盘是工作正本，动手前先 pull 再改，push 前先 git commit。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOCAL_NAME="$(basename "$LOCAL_ROOT")"
ORIGINAL_ROOT="${ORIGINAL_ROOT:-/mnt/hgfs/MCProject}"
ORIGINAL="${ORIGINAL_ROOT}/${LOCAL_NAME}"

MODE="${1:-}"
case "$MODE" in
  pull|push|diff) shift ;;
  ""|-h|--help)
    sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 0 ;;
  *) echo "ERROR: 未知模式 '$MODE'（仅 pull | push | diff）" >&2; exit 2 ;;
esac

APPLY=0; DELETE=0
for a in "$@"; do
  case "$a" in
    --apply) APPLY=1 ;;
    --delete) DELETE=1 ;;
    -n|--dry-run) APPLY=0 ;;
    *) echo "ERROR: 未知参数 '$a'" >&2; exit 2 ;;
  esac
done

[ -d "$ORIGINAL" ] || { echo "ERROR: 原始路径不存在：$ORIGINAL（hgfs 未挂载？可用 ORIGINAL_ROOT=... 覆盖）" >&2; exit 1; }

EXCLUDES=(
  --exclude='/.git/'
  --exclude='/build/'
  --exclude='/*/build/'
  --exclude='/.gradle/'
  --exclude='/temp/'
  --exclude='/.idea/'
  --exclude='/run/*/logs/'
  --exclude='/run/*/saves/'
  --exclude='/run/*/crash-reports/'
  --exclude='/run/*-launch-*.log'
  --exclude='/logs/'
  --exclude='.DS_Store'
)
EXTRA=()
[ "$DELETE" = 1 ] && EXTRA+=(--delete)

case "$MODE" in
  pull|diff) SRC="$ORIGINAL/"; DST="$LOCAL_ROOT/" ;;
  push)      SRC="$LOCAL_ROOT/"; DST="$ORIGINAL/" ;;
esac

echo "mode=$MODE  from=$SRC  to=$DST  apply=$APPLY delete=$DELETE"
if [ "$MODE" = push ] && [ -d "$LOCAL_ROOT/.git" ]; then
  if [ -n "$(git -C "$LOCAL_ROOT" status --porcelain 2>/dev/null)" ]; then
    echo "NOTE: 本机工作树有未提交改动（含新增文件）；push 会把它们一起同步到原始路径。建议先 git commit 或确认。"
  fi
fi

RSYNC_OPTS=(-rlt --human-readable --itemize-changes "${EXCLUDES[@]}" "${EXTRA[@]}")
if [ "$APPLY" = 1 ]; then
  rsync "${RSYNC_OPTS[@]}" "$SRC" "$DST"
  echo "DONE: $MODE applied"
else
  rsync -n "${RSYNC_OPTS[@]}" "$SRC" "$DST"
  echo "DRY-RUN: 加 --apply 才会真正同步"
fi
