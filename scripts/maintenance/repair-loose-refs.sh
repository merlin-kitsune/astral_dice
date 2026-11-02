#!/bin/sh
# repair-loose-refs.sh —— 用幸存 reflog 修复被外部删除的 loose ref（python/bash 复原版）。
#
# 症状：git 刚写好 .git/refs/heads/<带斜杠的分支>/<ref>，但外部删除器（内核文件系统过滤驱动 /
# 杀软实时扫描）或紧跟着的 git pack-refs（会由 git gc --auto 触发）把 loose ref 删掉，
# 而 .git/logs/... 里的 reflog 幸存。结果是 HEAD -> unknown revision / 分支「凭空消失」。
#
# 做法：以**幸存的 reflog** 为事实源，把缺失的 loose ref 写回去。
#
# 安全不变量（改这个脚本前先读一遍）：
#   1. git 正常删分支（git branch -d）会连 reflog 一起删 —— 所以本脚本不会让「有意删掉的分支」
#      复活；只有 reflog 还在、ref 却没了，才认为是被外部删除的。
#   2. ref 仍能解析（例如已进 packed-refs）就跳过，一个字节都不写。
#   3. 只写 .git/refs 下的 loose ref 文件，绝不碰 objects / 工作树；写之前再断言一次
#      目标路径以 <gitdir>/refs/ 开头，并先创建父目录（父目录缺失曾导致写失败）。
#   4. 范围**只有** refs/heads/**：refs/remotes/** 归 git fetch 管，复活它会破坏 --prune
#      语义、凭空长出「幽灵分支」。
#
# 退出码恒为 0（拿不到 gitdir 就静默退出）：这是自动维护动作，不应因为当前目录不是 git 仓库就报错。
#
# 与 repair-loose-refs.ps1（139 行）的差异：ps1 用 [System.IO.File]::WriteAllText + UTF8(no BOM)
# 写 ref，本脚本用 printf（ref 文件是纯 ASCII 十六进制 + 换行，编码无歧义）；其余判定逐一对应。

set -u

gitdir=$(git rev-parse --absolute-git-dir 2>/dev/null) || exit 0
[ -n "$gitdir" ] || exit 0
[ -d "$gitdir/logs/refs" ] || exit 0

find "$gitdir/logs/refs" -type f 2>/dev/null | while IFS= read -r logfile; do
    ref=${logfile#"$gitdir/logs/"}

    case "$ref" in
        refs/heads/*) ;;
        *) continue ;;
    esac

    # 2. ref 还在（loose 或 packed）就不动
    [ -e "$gitdir/$ref" ] && continue
    git rev-parse --verify --quiet "$ref" >/dev/null 2>&1 && continue

    last=$(tail -n 1 "$logfile" 2>/dev/null | awk '{print $2}')
    case "$last" in
        '' | 0000000000000000000000000000000000000000) continue ;;
    esac

    # 只认已存在的 commit 对象（reflog 末行可能是奇怪的记录）
    git cat-file -e "$last^{commit}" 2>/dev/null || continue

    target="$gitdir/$ref"
    case "$target" in
        "$gitdir"/refs/*) ;;
        *) continue ;;
    esac

    # 4. 父目录先建出来（曾因父目录缺失写失败）
    parent=$(dirname "$target")
    mkdir -p "$parent" 2>/dev/null || continue

    printf '%s\n' "$last" > "$target" 2>/dev/null || continue
    printf 'repair-loose-refs: RESTORED %s -> %.12s\n' "$ref" "$last" >&2
done

exit 0
