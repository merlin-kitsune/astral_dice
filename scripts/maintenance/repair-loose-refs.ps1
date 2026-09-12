#!/usr/bin/env pwsh
#Requires -Version 7.0
# -*- coding: utf-8 -*-
<#
repair-loose-refs.ps1  (scripts/maintenance/repair-loose-refs.sh 的 1:1 PowerShell 移植)
---------------------------------------------------------------------------
背景:本机曾观察到 git 刚写完 .git/refs/heads/<含斜杠的分支>/<引用> 之后,该松散引用
文件连同目录一起"消失",而 .git/logs/... 下的同名 reflog 不受影响。
症状:HEAD -> unknown revision / 分支"消失"。

(2026-09-11 修订:事后证实这些"消失"来自 `git pack-refs`(由 `git gc --auto` 触发)
主动删除松散引用并顺带移除其空父目录,以及"父目录不存在时写入直接失败";
并非外部删除者。无论成因为何,本脚本的处置方式不变:以幸存的 reflog 为准补回真正
丢失的松散引用;能否解析仍以 `git rev-parse --verify` 三重确认,禁止只看松散文件是否存在。)

安全性(与 .sh 版逐条一致):
  - git 正常删除分支时会连 reflog 一起删(git branch -d),所以这里不会
    "复活"任何被有意删除的分支;
  - 引用若仍能被解析(例如已进入 packed-refs),直接跳过,不做任何写入;
  - 只写入 .git/refs 下的松散引用文件,不改动任何对象或工作树;
  - 作用范围**仅限 refs/heads/**(本地分支):远端跟踪引用 refs/remotes/*
    交给 git fetch 管理,绝不由本脚本复活(否则 --prune 会被抵消、并产生幻影分支)。
用法:在仓库内任意位置执行  pwsh -NoProfile -File scripts/maintenance/repair-loose-refs.ps1

输出:成功补回的记录打到 stderr(与 .sh 版一致),格式
      repair-loose-refs: RESTORED <ref> -> <sha 前 12 位>
退出码:恒为 0(拿不到 gitdir 时静默退出)。
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Continue'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
try { [Console]::OutputEncoding = $utf8NoBom } catch { }
$OutputEncoding = $utf8NoBom

function Write-Stderr {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text)
    [Console]::Error.Write("$Text`n")
}

# 执行 git 并取回退出码(标准错误一律吞掉,等价于 shell 的 2>/dev/null)
function Invoke-GitQuiet {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $code = 127
    $output = @()
    try {
        $output = @(& git @Arguments 2>$null)
        if ($null -ne $LASTEXITCODE) { $code = $LASTEXITCODE }
    } catch {
        # git 不存在/无法启动:一律当作"得不到结论",由调用方跳过
        return [pscustomobject]@{ ExitCode = 127; Output = @() }
    }
    return [pscustomobject]@{ ExitCode = $code; Output = $output }
}

# 取文件最后一行并按空白切分后的第 2 个字段(等价于 tail -n 1 | awk '{print $2}')
function Get-ReflogNewValue {
    param([string]$Path)
    $lines = @()
    try {
        $lines = [System.IO.File]::ReadAllLines($Path)
    } catch {
        return ''
    }
    if ($lines.Count -eq 0) { return '' }
    $line = [string]$lines[$lines.Count - 1]
    $fields = @($line.Trim() -split '\s+')
    if ($fields.Count -lt 2) { return '' }
    return [string]$fields[1]
}

$sep = [System.IO.Path]::DirectorySeparatorChar
$altSep = [System.IO.Path]::AltDirectorySeparatorChar

# --- 1) 取绝对 gitdir;拿不到就静默退出(等价于 gitdir=$(...) || exit 0) -------
$gitdir = ''
try {
    $probe = Invoke-GitQuiet @('rev-parse', '--absolute-git-dir')
    if ($probe.ExitCode -eq 0 -and $probe.Output.Count -gt 0) {
        $gitdir = ([string]$probe.Output[0]).Trim()
    }
} catch {
    $gitdir = ''
}
if ([string]::IsNullOrWhiteSpace($gitdir)) { exit 0 }

$logsRoot = Join-Path $gitdir 'logs'
$refsRoot = Join-Path $logsRoot 'refs'
if (-not (Test-Path -LiteralPath $refsRoot -PathType Container)) { exit 0 }

# --- 2) 只遍历 <gitdir>/logs/refs(等价于 find "$gitdir/logs/refs" -type f) ----
$logFiles = @(Get-ChildItem -LiteralPath $refsRoot -Recurse -File -Force -ErrorAction SilentlyContinue)

foreach ($logFile in $logFiles) {
    $ref = ([System.IO.Path]::GetRelativePath($logsRoot, $logFile.FullName)).Replace($altSep, $sep).Replace($sep, '/')

    # 只处理本地分支引用(refs/heads)。
    # 远端跟踪引用(refs/remotes)由 git fetch 管理:prune 掉的陈旧引用必须保持删除,
    # 若在此"复活"会让 git branch -r 出现幻影分支,并破坏 --prune 语义。
    if (-not $ref.StartsWith('refs/heads/', [System.StringComparison]::Ordinal)) { continue }

    $loosePath = Join-Path $gitdir $ref

    # 松散引用还在 -> 无事可做
    if (Test-Path -LiteralPath $loosePath) { continue }

    # 仍可解析(packed-refs 等)-> 不干预
    $verify = Invoke-GitQuiet @('rev-parse', '--verify', '--quiet', $ref)
    if ($verify.ExitCode -eq 0) { continue }

    # 取 reflog 最后一条记录里的新值
    $last = Get-ReflogNewValue $logFile.FullName
    if ($last -eq '' -or $last -eq '0000000000000000000000000000000000000000') { continue }

    # 对象必须真实存在且是提交
    $catFile = Invoke-GitQuiet @('cat-file', '-e', "$last^{commit}")
    if ($catFile.ExitCode -ne 0) { continue }

    # 兜底不变式:只允许在 <gitdir>/refs 下写松散引用(前缀已保证,此处再确认一次)
    $refsPrefix = (Join-Path $gitdir 'refs') + $sep
    $normalizedTarget = $loosePath.Replace($altSep, $sep)
    if (-not $normalizedTarget.StartsWith($refsPrefix, [System.StringComparison]::Ordinal)) { continue }

    # 先建父目录再写(本机曾因父目录不存在导致写入失败)
    $parent = [System.IO.Path]::GetDirectoryName($normalizedTarget)
    if (-not [string]::IsNullOrEmpty($parent)) {
        if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
            New-Item -ItemType Directory -Force -Path $parent -ErrorAction SilentlyContinue | Out-Null
        }
    }

    [System.IO.File]::WriteAllText($normalizedTarget, $last + "`n", $utf8NoBom)

    $short = if ($last.Length -gt 12) { $last.Substring(0, 12) } else { $last }
    Write-Stderr "repair-loose-refs: RESTORED $ref -> $short"
}

exit 0
