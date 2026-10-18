#!/usr/bin/env bash
# sync-back.sh — 把 WSL 施工副本的改动回写到 Windows 工作树（默认 dry-run）。
# 只回写 git 视角的「改动/新增/删除」，排除构建与运行时产物；不覆盖用户 bbmodel 资产。
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DST="${DST:-/mnt/f/MCProject/astral_dice_multiloader-next}"
DRY=1
[ "${1:-}" = "--apply" ] && DRY=0
cd "$ROOT" || exit 1
mapfile -d '' -t raw < <(git --no-optional-locks status --porcelain=v1 -z -uall)
files=()
for e in "${raw[@]}"; do files+=("${e:3}"); done
echo "SYNC_SOURCE: $ROOT"
echo "SYNC_TARGET: $DST"
echo "SYNC_FILES: ${#files[@]}"
for f in "${files[@]}"; do
  case "$f" in
    models/*.bbmodel) echo "  SKIP(user-asset) $f"; continue ;;
    run/*|build/*|*/build/*|.gradle/*|work/*) echo "  SKIP(runtime/scratch) $f"; continue ;;
  esac
  if [ $DRY -eq 1 ]; then echo "  WOULD $f"; continue; fi
  if [ -d "$ROOT/$f" ]; then mkdir -p "$DST/$f"; continue; fi
  if [ -e "$ROOT/$f" ]; then
    mkdir -p "$(dirname "$DST/$f")"; cp -f "$ROOT/$f" "$DST/$f"
  else
    rm -f "$DST/$f"
  fi
done
[ $DRY -eq 1 ] && echo "SYNC_RESULT: DRY-RUN（加 --apply 才写盘）" || echo "SYNC_RESULT: APPLIED ${#files[@]}"
