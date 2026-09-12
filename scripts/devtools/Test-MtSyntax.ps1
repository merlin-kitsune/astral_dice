<#
Test-MtSyntax.ps1 — 对 scripts/ 与 tools/ 下所有 .ps1 / .psm1 做语法解析自检。

只做解析（[Parser]::ParseFile），不执行脚本 —— 用它做每一批移植的「静态闸门」。

用法：
    pwsh -NoProfile -File scripts/devtools/Test-MtSyntax.ps1
    pwsh -NoProfile -File scripts/devtools/Test-MtSyntax.ps1 -Path scripts/test

退出码：0 = 全部 0 错误；1 = 有解析错误；2 = 没找到任何文件。
#>
[CmdletBinding()]
param(
    [string[]]$Path = @('scripts', 'tools')
)

$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetDirectoryName([System.IO.Path]::GetDirectoryName($PSScriptRoot))

$files = @()
foreach ($p in $Path) {
    $abs = Join-Path $root $p
    if (-not (Test-Path -LiteralPath $abs)) { continue }
    $files += @(Get-ChildItem -LiteralPath $abs -Recurse -File |
            Where-Object { $_.Extension -in '.ps1', '.psm1' -and $_.FullName -notmatch '\\__pycache__\\' })
}

if ($files.Count -eq 0) {
    [Console]::Error.Write("MT_ERROR: 未找到任何 .ps1/.psm1（已查：$($Path -join ', ')）`n")
    exit 2
}

$bad = 0
foreach ($f in $files) {
    $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($f.FullName, [ref]$null, [ref]$errors)
    $rel = $f.FullName.Substring($root.Length + 1)
    if ($errors -and $errors.Count -gt 0) {
        $bad++
        [Console]::Out.Write("PARSE FAIL  $rel`n")
        foreach ($e in $errors) {
            [Console]::Out.Write(("            L{0}:{1}  {2}`n" -f $e.Extent.StartLineNumber, $e.Extent.StartColumnNumber, $e.Message))
        }
    } else {
        [Console]::Out.Write("PARSE OK    $rel`n")
    }
}

[Console]::Out.Write("RESULT: $($files.Count) 个文件，解析失败 $bad 个`n")
if ($bad -eq 0) { exit 0 }
exit 1
