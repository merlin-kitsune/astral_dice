#Requires -Version 7.0
<#
.SYNOPSIS
    mt_stop — 显式收停游戏与构建进程（阶段 D 的收尾动作）。

.DESCRIPTION
    实现已收敛到 mt_cleanup.ps1（唯一来源）：本脚本只做参数映射，
    与「流程退出时的自动清理」走完全相同的代码路径，避免两套逻辑各自漂移。

    与旧流程的差异：不再「按进程名 java 全杀」，改为按 gradle 任务选择器 +
    run 目录精确匹配本流程进程，不会误杀用户其它 Java 程序 / IDE 语言服务器。

.PARAMETER Version
    只收停该版本（可重复；不动 Gradle 守护）

.PARAMETER All
    全版本收停，并停 Gradle 守护

.PARAMETER KeepDaemon
    全版本收停，保留 Gradle 守护（构建热态）

.PARAMETER Force
    忽略失败取证标记（.mt_keep_alive）强制收停

.EXAMPLE
    pwsh -File scripts/test/mt_stop.ps1 --version 1.21.1
    pwsh -File scripts/test/mt_stop.ps1 --all
    pwsh -File scripts/test/mt_stop.ps1 --all --keep-daemon
    pwsh -File scripts/test/mt_stop.ps1 --all --force

.NOTES
    迁移前源文件 scripts/test/mt_stop.sh（该原件已在 92fbeaf「工具链收敛为纯 pwsh」删除，取回：`git show 92fbeaf^:scripts/test/mt_stop.sh`）。

    子进程走 `pwsh -File mt_cleanup.ps1`：**不捕获输出**、直接继承控制台句柄，
    这样 mt_cleanup 的机器可读结论行（MT_CLEANUP_RESULT:）以原始字节透传，
    不会象「取回字符串再重新编码」那样被改写。
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')

Initialize-MtConsole

$Targets = @()
$Mode = ''
$ForceFlag = $false
$KeepDaemonFlag = $false

$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    $key = $tok.TrimStart('-').ToLowerInvariant()
    if ($key -eq 'version') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
        $Targets += [string]$args[$i + 1]
        $Mode = 'version'
        $i += 2
    } elseif ($key -eq 'all') {
        $Mode = 'all'; $i++
    } elseif ($key -eq 'force') {
        $ForceFlag = $true; $i++
    } elseif ($key -eq 'keep-daemon') {
        $KeepDaemonFlag = $true; $i++
    } else {
        Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
    }
}

if (-not $Mode) {
    Write-MtErrorLine '必须指定 --version 或 --all'
    exit $MT_EXIT_ERROR
}

$cleanupArgs = @('run')
if ($ForceFlag) { $cleanupArgs += '--force' }
if ($KeepDaemonFlag) { $cleanupArgs += '--keep-daemon' }
if ($Mode -eq 'version') { foreach ($v in $Targets) { $cleanupArgs += @('--version', $v) } }

Start-MtPhase 'stop'

# 当前 pwsh 可执行文件（用 $PSHOME 拼接在 Store/绿色版下可能错位，取自身进程路径更稳）
$psExe = (Get-Process -Id $PID).Path
& $psExe -NoProfile -File (Join-Path $PSScriptRoot 'mt_cleanup.ps1') @cleanupArgs
$rc = $LASTEXITCODE

if ($rc -eq 0) {
    Write-MtOk 'STOP' '收停完成，无本流程残留进程'
} else {
    Write-MtWarn 'STOP: 收停后仍有残留，详见上方 mt_cleanup 输出'
}
exit $rc
