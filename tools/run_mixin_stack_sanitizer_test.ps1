#Requires -Version 7.0
<#
.SYNOPSIS
    DamageStackSanitizer 纯 Java 语义测试运行器（NeoForge 21.1.x LivingEntity#hurt 容器栈泄漏补丁）。

.DESCRIPTION
    只编译真实源文件 neoforge-1.21.1\...\mixin\fixes\DamageStackSanitizer.java（纯 JDK，无第三方依赖）
    与 tools\mixin-stack-sanitizer-test\DamageStackSanitizerTest.java，不复制任何逻辑。
    覆盖：未修复取消出口补 1 次 pop / 上游 backport 后 0 次 pop（不抛 EmptyStackException）/ 嵌套 hurt 逐层配对
    / null 与哨兵边界 / 两个正常出口 0 次 pop / 绝不弹到低于进入深度 / 配对失同步 no-op / 存活日志每 JVM 一次。

.EXAMPLE
    pwsh -NoProfile -File tools/run_mixin_stack_sanitizer_test.ps1
#>
[CmdletBinding()]
param(
    [string]$OutDir = (Join-Path $env:TEMP 'astral_mss_out')
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sanitizer = Join-Path $root 'neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/mixin/fixes/DamageStackSanitizer.java'
$test = Join-Path $root 'tools/mixin-stack-sanitizer-test/DamageStackSanitizerTest.java'

foreach ($f in @($sanitizer, $test)) {
    if (-not (Test-Path -LiteralPath $f)) { Write-Error "缺少源文件: $f"; exit 2 }
}

function Resolve-JdkTool([string]$Name) {
    if ($env:JAVA_HOME) {
        $p = Join-Path $env:JAVA_HOME "bin/$Name.exe"
        if (Test-Path -LiteralPath $p) { return $p }
    }
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($guess in @('C:\Program Files\Zulu\zulu-21\bin', 'C:\Program Files\Eclipse Adoptium\jdk-21*\bin')) {
        $hit = Get-ChildItem -Path (Join-Path $guess "$Name.exe") -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}

$javac = Resolve-JdkTool 'javac'
$java = Resolve-JdkTool 'java'
if (-not $javac -or -not $java) { Write-Error '找不到 javac/java（设置 JAVA_HOME 或加入 PATH）'; exit 2 }

if (Test-Path -LiteralPath $OutDir) { Remove-Item -LiteralPath $OutDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

Write-Host "javac = $javac"
Write-Host "java  = $java"
Write-Host "out   = $OutDir"
Write-Host ''

& $javac -encoding UTF-8 -d $OutDir $sanitizer $test
$jc = $LASTEXITCODE
Write-Host "javac exit=$jc"
if ($jc -ne 0) { Write-Host 'MSS_RESULT: FAIL (javac)'; exit 1 }

& $java -cp $OutDir DamageStackSanitizerTest
$rc = $LASTEXITCODE

Write-Host ''
if ($rc -eq 0) { Write-Host 'MSS_RESULT: PASS (10/10)' } else { Write-Host "MSS_RESULT: FAIL (java exit=$rc)" }
exit $rc
