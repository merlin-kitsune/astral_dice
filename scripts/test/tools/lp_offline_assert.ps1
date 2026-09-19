# 离线断言复算:按 mt_assert.ps1 的语义(log/absent + source)对已落盘日志重跑用例断言。
# 用途:在不重启游戏的前提下反复校准断言正则(判据与 mt_assert.ps1 同为 .NET [regex])。
# ⚠️ 解析结果**不能**存回 `$Case`/`$case`:param 里的 `[string]$Case` 会给该变量加**类型约束**,
#    再赋值对象会被强制 ToString()(实测:读出来是 System.String ⇒ asserts=0,静默全过)。
param(
    [Parameter(Mandatory)][string]$Case,
    [Parameter(Mandatory)][string]$Version
)
$ErrorActionPreference = 'Stop'
# 仓库根 = 本脚本上溯三级(scripts/test/tools → scripts/test → scripts → 根);不要写死绝对路径
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$casePath = if ([System.IO.Path]::IsPathRooted($Case)) { $Case } else { Join-Path "$root\scripts\test\cases" $Case }
$raw = Get-Content -LiteralPath $casePath -Raw
$doc = $raw | ConvertFrom-Json
$logs = "$root\run\$Version\logs"
$latestPath = Join-Path $logs 'latest.log'
$debugPath = Join-Path $logs 'debug.log'
$latest = if (Test-Path $latestPath) { Get-Content -LiteralPath $latestPath -Raw } else { '' }
$debug = if (Test-Path $debugPath) { Get-Content -LiteralPath $debugPath -Raw } else { '' }

$i = 0; $fail = 0; $log = 0; $abs = 0
Write-Host ("MT_OFFLINE_ASSERT_INPUT: case={0} asserts={1} latest={2}B debug={3}B" -f `
        [System.IO.Path]::GetFileName($casePath), $doc.asserts.Count, $latest.Length, $debug.Length)
foreach ($a in $doc.asserts) {
    $i++
    $t = [string]$a.type
    if ($t -ne 'log' -and $t -ne 'absent') { continue }
    $src = if ($a.source) { [string]$a.source } else { 'latest' }
    $text = if ($src -eq 'debug') { $debug } else { $latest }
    $hit = [regex]::IsMatch($text, [string]$a.pattern)
    if ($t -eq 'log') { $log++; $ok = $hit } else { $abs++; $ok = -not $hit }
    if ($ok) {
        Write-Host ("  [PASS] #{0} [{1}/{2}] {3}" -f $i, $t, $src, $a.pattern)
    } else {
        $fail++
        Write-Host ("  [FAIL] #{0} [{1}/{2}] {3}" -f $i, $t, $src, $a.pattern)
    }
}
Write-Host ("MT_OFFLINE_ASSERT: case={0} log={1} absent={2} FAIL={3}" -f `
        [System.IO.Path]::GetFileName($casePath), $log, $abs, $fail)
if ($fail -gt 0) { exit 1 }
