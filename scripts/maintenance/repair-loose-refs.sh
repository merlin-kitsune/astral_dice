#!/bin/sh
# repair-loose-refs.sh
# ---------------------------------------------------------------------------
# 背景：本机存在一个"外部删除者"（内核态文件系统过滤层，疑为杀软实时防护），
# 它会在 git 刚写完 .git/refs/heads/<含斜杠的分支>/<引用> 之后把该松散引用文件
# 连同目录一起删掉，而 .git/logs/... 下的同名 reflog 不受影响。
# 症状：HEAD -> unknown revision / 分支"消失"。
#
# 本脚本用"幸存的 reflog"作为事实来源，把丢失的松散引用补回来。
# 安全性：
#   - git 正常删除分支时会连 reflog 一起删（git branch -d），所以这里不会
#     "复活"任何被有意删除的分支；
#   - 引用若仍能被解析（例如已进入 packed-refs），直接跳过，不做任何写入；
#   - 只写入 .git/refs 下的松散引用文件，不改动任何对象或工作树；
#   - 作用范围**仅限 refs/heads/**（本地分支）：远端跟踪引用 refs/remotes/*
#     交给 git fetch 管理，绝不由本脚本复活（否则 --prune 会被抵消、并产生幻影分支）。
# 用法：在仓库内任意位置执行  sh scripts/maintenance/repair-loose-refs.sh
# ---------------------------------------------------------------------------
set -u

gitdir=$(git rev-parse --absolute-git-dir 2>/dev/null) || exit 0
[ -n "${gitdir:-}" ] || exit 0
[ -d "$gitdir/logs/refs" ] || exit 0

find "$gitdir/logs/refs" -type f 2>/dev/null | while IFS= read -r logfile; do
    ref=${logfile#"$gitdir/logs/"}
    # 只处理本地分支引用(refs/heads)。
    # 远端跟踪引用(refs/remotes)由 git fetch 管理:prune 掉的陈旧引用必须保持删除,
    # 若在此"复活"会让 git branch -r 出现幻影分支,并破坏 --prune 语义。
    case "$ref" in
        refs/heads/*) ;;
        *) continue ;;
    esac

    # 松散引用还在 -> 无事可做
    [ -e "$gitdir/$ref" ] && continue

    # 仍可解析（packed-refs 等）-> 不干预
    if git rev-parse --verify --quiet "$ref" >/dev/null 2>&1; then
        continue
    fi

    # 取 reflog 最后一条记录里的新值
    last=$(tail -n 1 "$logfile" 2>/dev/null | awk '{print $2}')
    case "$last" in
        '' | 0000000000000000000000000000000000000000) continue ;;
    esac

    # 对象必须真实存在且是提交
    git cat-file -e "$last^{commit}" 2>/dev/null || continue

    mkdir -p "$gitdir/$(dirname "$ref")"
    printf '%s\n' "$last" > "$gitdir/$ref"
    printf 'repair-loose-refs: RESTORED %s -> %.12s\n' "$ref" "$last" >&2
done

exit 0
