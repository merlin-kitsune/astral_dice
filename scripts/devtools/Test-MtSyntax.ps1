<#
Test-MtSyntax.ps1 — 静态语法闸门：对 scripts/ 与 tools/ 下的
  · `.ps1` / `.psm1` → PowerShell 解析器（[Parser]::ParseFile，只解析不执行）；
  · `.js`            → Node 语法自检（`node --check`，2026-09-28 新增）。
两个分支都**不执行**任何脚本内容。

为什么 .js 必须纳入：`scripts/test/resources/kubejs/**` 的探针是「用字符串拼命令名 +
大量立即执行闭包」的代码，历史上出现过**函数尾闭合被吞**（少一个括号）但整文件仍能被
KubeJS 加载、命令静默不注册的事故 ⇒ PowerShell 解析器看不见 .js，`node --check`
是构建前唯一能拦住它的手段。

用法：
    pwsh -NoProfile -File scripts/devtools/Test-MtSyntax.ps1
    pwsh -NoProfile -File scripts/devtools/Test-MtSyntax.ps1 -Path scripts/test

退出码：0 = 全部 0 错误；1 = 有语法错误；2 = 没找到任何文件。
⚠️ `node` 不在 PATH 时，.js 一律记为 `PARSE SKIP`（不改变退出码）—— 避免把
   「环境缺 node」误判成「代码有错」。
#>
[CmdletBinding()]
param(
    [string[]]$Path = @('scripts', 'tools')
)

$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetDirectoryName([System.IO.Path]::GetDirectoryName($PSScriptRoot))

$psFiles = @()
$jsFiles = @()
foreach ($p in $Path) {
    $abs = Join-Path $root $p
    if (-not (Test-Path -LiteralPath $abs)) { continue }
    $found = @(Get-ChildItem -LiteralPath $abs -Recurse -File |
            Where-Object { $_.FullName -notmatch '\\__pycache__\\' -and $_.FullName -notmatch '\\node_modules\\' })
    $psFiles += @($found | Where-Object { $_.Extension -in '.ps1', '.psm1' })
    $jsFiles += @($found | Where-Object { $_.Extension -eq '.js' })
}

if (($psFiles.Count + $jsFiles.Count) -eq 0) {
    [Console]::Error.Write("MT_ERROR: 未找到任何 .ps1/.psm1/.js（已查：$($Path -join ', ')）`n")
    exit 2
}

$node = Get-Command node -ErrorAction SilentlyContinue
$bad = 0
$skip = 0

foreach ($f in $psFiles) {
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

foreach ($f in $jsFiles) {
    $rel = $f.FullName.Substring($root.Length + 1)
    if (-not $node) {
        $skip++
        [Console]::Out.Write("PARSE SKIP  $rel  (node 不在 PATH)`n")
        continue
    }
    $out = & node --check $f.FullName 2>&1
    if ($LASTEXITCODE -ne 0) {
        $bad++
        [Console]::Out.Write("PARSE FAIL  $rel`n")
        foreach ($line in @($out)) { [Console]::Out.Write("            $line`n") }
    } else {
        [Console]::Out.Write("PARSE OK    $rel`n")
    }
}

$total = $psFiles.Count + $jsFiles.Count
[Console]::Out.Write("RESULT: $total 个文件（ps1/psm1=$($psFiles.Count) / js=$($jsFiles.Count)），解析失败 $bad 个，跳过 $skip 个`n")
if ($bad -eq 0) { exit 0 }
exit 1
