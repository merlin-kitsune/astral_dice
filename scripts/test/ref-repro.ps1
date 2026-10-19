#!/usr/bin/env pwsh
#Requires -Version 7.0
# -*- coding: utf-8 -*-
<#
ref-repro.ps1  --  loose-ref deletion isolation tester
                  (scripts/test/ref-repro.cmd 的 1:1 PowerShell 移植)

Purpose: this machine has a user-mode "safe delete" component that
sends newly created loose refs under .git\refs\... to the Recycle Bin
(git reports success, then the ref is gone).  %TEMP% is exempt, all
other paths are affected.  Use this script to find the culprit:
run it once as a baseline, then exit one suspect app at a time and
run it again.  When the F: count becomes 5/5, the last app you
exited is the culprit.

Self-check: the %TEMP% column should always be 5/5.  If BOTH columns
are 5/5 there is nothing intercepting right now.

用法(与原 .cmd 的差异只有一处,见下):
    pwsh -NoProfile -File scripts/test/ref-repro.ps1 [-NoPause]

原 .cmd 用 `if "%~1"=="" pause` 判定:无参数时等待按键。PS 版改用开关 `-NoPause`
显式跳过该等待(便于非交互/CI 环境跑基线);默认行为仍是等待按键。

安全:本脚本只在两个"脚本自有"的临时目录内做纯本地 git 实验
(`%TEMP%\_refcheck_tmp` 与 `F:\MCProject\_refcheck`),只创建/删除这两个目录;
不触碰仓库本体、不删除任何用户数据。
#>
[CmdletBinding()]
param(
    # 跳过结尾的 "pause"(原 .cmd 有参数时不等待按键)
    [switch]$NoPause
)

$ErrorActionPreference = 'Continue'
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
try { [Console]::OutputEncoding = $utf8NoBom } catch { }
$OutputEncoding = $utf8NoBom

function Write-Stdout {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text)
    [Console]::Out.Write("$Text`n")
}

# 与 .cmd 的 :probe 子过程一一对应:在一个临时目录里建仓,尝试 5 条嵌套松散引用,
# 返回"仍然存在"的条数。
function Invoke-RefProbe {
    param([Parameter(Mandatory = $true)][string]$Root)

    if (Test-Path -LiteralPath $Root) {
        Remove-Item -LiteralPath $Root -Recurse -Force -ErrorAction SilentlyContinue
    }
    New-Item -ItemType Directory -Force -Path $Root -ErrorAction SilentlyContinue | Out-Null

    & $script:Git -C $Root init -q 2>$null | Out-Null
    & $script:Git -C $Root config user.email t@t 2>$null | Out-Null
    & $script:Git -C $Root config user.name t 2>$null | Out-Null

    [System.IO.File]::WriteAllText((Join-Path $Root 'f.txt'), "x`n", $utf8NoBom)

    & $script:Git -C $Root add -A 2>$null | Out-Null
    & $script:Git -C $Root commit -q -m i 2>$null | Out-Null

    $count = 0
    foreach ($i in 1..5) {
        & $script:Git -C $Root update-ref "refs/heads/n$i/deep" HEAD 2>$null | Out-Null
        $refFile = Join-Path -Path $Root -ChildPath '.git' -AdditionalChildPath 'refs', 'heads', "n$i", 'deep'
        if (Test-Path -LiteralPath $refFile) { $count++ }
    }
    return $count
}

# --- 定位 git(与原 .cmd 相同的回退顺序) -------------------------------------
$script:Git = 'C:\Program Files\Git\cmd\git.exe'
if (-not (Test-Path -LiteralPath $script:Git)) { $script:Git = 'git' }

$tmpCount = Invoke-RefProbe -Root (Join-Path $env:TEMP '_refcheck_tmp')
$fCount = Invoke-RefProbe -Root 'F:\MCProject\_refcheck'

Write-Stdout ''
Write-Stdout '==================================================================='
Write-Stdout "  $env:TEMP      : $tmpCount / 5   (control, expected 5)"
Write-Stdout "  F:\MCProject: $fCount / 5   (tested path)"
Write-Stdout '-------------------------------------------------------------------'
if ($fCount -eq 5) {
    Write-Stdout '  RESULT: no interceptor right now  <== fixed!'
} else {
    Write-Stdout '  RESULT: interceptor still active'
}
Write-Stdout '==================================================================='
Write-Stdout ''

if (-not $NoPause) {
    [Console]::Out.Write('Press any key to continue . . . ')
    try {
        $null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')
    } catch {
        $null = Read-Host
    }
    [Console]::Out.Write("`n")
}

exit 0
