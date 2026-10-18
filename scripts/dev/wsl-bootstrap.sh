#!/usr/bin/env bash
# wsl-bootstrap.sh — 幂等建立 WSL 施工副本（ext4），并做 git 判据自检。
set -euo pipefail
SRC="${SRC:-/mnt/f/MCProject/astral_dice_multiloader}"
WIN="${WIN:-/mnt/f/MCProject/astral_dice_multiloader-next}"
DST="${DST:-$HOME/work/astral_dice_multiloader-next}"
BRANCH="${BRANCH:-multi-dev-next}"
if [ ! -d "$DST/.git" ]; then
  mkdir -p "$(dirname "$DST")"
  git clone -b "$BRANCH" "$SRC" "$DST"
fi
cd "$DST"
git config core.autocrlf true
for d in run .codex models; do
  if [ -d "$WIN/$d" ] && [ ! -d "$DST/$d" ]; then rsync -a "$WIN/$d/" "$DST/$d/"; fi
done
echo "WSL_BOOTSTRAP: branch=$(git rev-parse --abbrev-ref HEAD) head=$(git rev-parse --short HEAD)"
echo "WSL_BOOTSTRAP: status=$(git --no-optional-locks status --short -uall | wc -l) 行"
