#Requires -Version 7.0
<#
.SYNOPSIS
    mt_cleanup — 流程退出时的进程收停（唯一实现）。

.DESCRIPTION
    由三个入口共用，避免出现两套逻辑：
      - `mt.ps1` 的退出钩子（正常结束 / 失败 / 中断三条路径都覆盖）；
      - `mt.ps1 --phase stop`（显式收停）；
      - `mt_stop.ps1`（薄封装）。

    收停范围（只碰本流程自己的东西）:
      1. **本流程的 dev 客户端 / Gradle 任务进程** —— 判据为 Get-MtProcessMarkers
         （gradle 任务选择器 + run 目录），不会误杀 IDE 语言服务器或用户自己的客户端；
      2. **Gradle 守护进程** —— 用 `gradlew --stop` 优雅停止（而非 taskkill /IM gradle.exe，
         后者会连用户其它项目的 Gradle 一起杀）。--keep-daemon 可跳过以保留构建缓存热态。

    与失败取证的配合：条目 `on_fail=keep_game_running` 失败时会落 `.mt_keep_alive` 标记。
    该标记存在时本模块**不杀客户端也不停守护进程**（守护进程可能是客户端的父进程），
    只打印提示 —— 否则「自动清理」会在失败瞬间销毁现场，让取证变得不可能。

    存档清理（`--purge-saves`，2026-09-17 用户规则「测试任务完成后关闭测试端并清理旧存档数据，
    避免游戏进程长时间驻留」）：
      · 删 `<run>/saves/<世界名>`（客户端读取位置）与 `<run>/<世界名>`（runServer 生成位置），
        以及 `<run>/saves/` 下**其它**含 level.dat 的历史遗留世界目录（逐个回显后删除）；
      · 只动 `run/<版本>/` 内的世界目录，绝不碰 `run` 之外、也不碰种子包 resources/testworld-seed-*.zip；
      · 删完即「下次必须先 `mt_env world` 重建世界」—— 与全局规则「每次开新的自动化测试都必须
        重建世界，不接受复用上一轮存档」一致，留着旧存档反而会掩盖「忘了重建」；
      · `.mt_keep_alive` 存在时整个清理（含本步骤）一律 SKIP，先取证再 `--force`。
      · ⚠️ **重登类用例不得用它**：CHIP-RELOG 系列必须 `saveall` → stop（**保留存档**）→ launch 读回，
        故 `--phase stop` 默认**不**清理存档，只有显式 `--purge-saves` 或全流程退出清理才清。

.PARAMETER Cmd
    status | run

.PARAMETER Version
    run 时只收停指定版本（可重复；缺省=全部）

.PARAMETER Force
    忽略失败取证标记，强制收停

.PARAMETER KeepDaemon
    保留 Gradle 守护进程（构建热态）

.PARAMETER PurgeSaves
    收停后清理测试存档（旧存档数据）：`<run>/saves/<世界名>`、`<run>/<世界名>` 及 saves 下其它
    含 level.dat 的历史遗留世界。默认关闭（重登类用例要跨 stop 保留存档）。

.PARAMETER Quiet
    只输出机器可读结论行

.EXAMPLE
    pwsh -File scripts/test/mt_cleanup.ps1 status
    pwsh -File scripts/test/mt_cleanup.ps1 run --quiet
    pwsh -File scripts/test/mt_cleanup.ps1 run --version 1.21.1
    pwsh -File scripts/test/mt_cleanup.ps1 run --force
    pwsh -File scripts/test/mt_cleanup.ps1 run --purge-saves     # 收停 + 清理旧存档

.NOTES
    迁移前源文件 scripts/test/mt_cleanup.py（该原件已在 92fbeaf「工具链收敛为纯 pwsh」删除，取回：`git show 92fbeaf^:scripts/test/mt_cleanup.py`）。CLI 保持 `--version / --force /
    --keep-daemon / --quiet` 写法，同时接受 PowerShell 风格 `-Version / -Force`（`$args`
    解析时统一剥掉前导 `-`）。

    **有意偏差（1 处）**：SKIP 分支里的取证提示原为
    `bash scripts/test/mt.sh --phase stop --force`（旧 bash 版脚本已在 92fbeaf 删除），此处改为等价的 pwsh 入口。
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
# 依赖顺序：Mt.Phase 必须先于 Mt.Proc（Mt.Proc 内部也会引它）。
# 一律不加 -Force：-Force 的卸载重载会摘掉已注入本作用域的函数（详见 Mt.Paths.psm1 注释）。
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Proc.psm1')

Initialize-MtConsole

$script:KeepAlive = Join-Path (Join-Path (Get-MtTestDir) 'cases') '.mt_keep_alive'

# ══ 进程归属 ══════════════════════════════════════════════════════════════
function Get-MtPipelineProcs {
    <#
    .SYNOPSIS
        本流程自己的 java 进程：[{Version, Pid, CommandLine}]。

    .NOTES
        Versions 为空时扫描全部版本；否则只保留指定版本（用于
        `mt_stop.ps1 --version X` 这类「只收停某版本」的调用）。

        2026-09-17：成员判定从「命令行命中 marker」改为 `Test-MtPipelineProcess` ——
        与收停用**同一个判据**，否则「收停后自检」会漏掉 marker 里没有、但确实属于本流程的
        进程（实测：`runServer` 的 dev-launch JVM 命令行不含 run 目录/子项目选择器，
        旧判据既杀不掉也检不出）。
    #>
    [CmdletBinding()]
    param([string[]]$Versions)

    $wanted = if ($null -ne $Versions -and $Versions.Count -gt 0) { @($Versions) } else { @(Get-MtVersions) }
    $pathsByVersion = [ordered]@{}
    foreach ($v in $wanted) { $pathsByVersion[$v] = Get-MtPaths -Version $v }

    $out = @()
    foreach ($p in Get-JavaProcesses) {
        $cmd = [string]$p.CommandLine
        foreach ($v in @($pathsByVersion.Keys)) {
            if (Test-MtPipelineProcess -Paths $pathsByVersion[$v] -ProcessId ([int]$p.Pid) -CommandLine $cmd) {
                $out += [pscustomobject]@{ Version = $v; Pid = [int]$p.Pid; CommandLine = $cmd }
                break
            }
        }
    }
    return $out
}

function Get-MtDaemonProcs {
    <#
    .SYNOPSIS
        Gradle 守护进程（不属于任何单版本，故单独处理）。
    #>
    [CmdletBinding()]
    param()

    $out = @()
    foreach ($p in Get-JavaProcesses) {
        $cmd = [string]$p.CommandLine
        if ($cmd.Contains('GradleDaemon')) {
            $out += [pscustomobject]@{ Pid = [int]$p.Pid; CommandLine = $cmd }
        }
    }
    return $out
}

function Get-MtKeepAliveReason {
    [CmdletBinding()]
    param()

    if (-not (Test-Path -LiteralPath $script:KeepAlive -PathType Leaf)) { return $null }
    try {
        $t = (Get-Content -LiteralPath $script:KeepAlive -Raw -Encoding UTF8).Trim()
        if ($t) { return $t }
        return '（未记录原因）'
    } catch {
        return '（读取标记失败）'
    }
}

function Get-MtPreview {
    <#
    .SYNOPSIS
        复刻 python 的 `s[:160]`（按字符截断）。
    #>
    [CmdletBinding()]
    param([AllowEmptyString()][string]$Text)

    if ($null -eq $Text) { return '' }
    if ($Text.Length -le 160) { return $Text }
    return $Text.Substring(0, 160)
}

# ══ 存档清理（--purge-saves）══════════════════════════════════════════════
function Remove-MtSaveTree {
    <#
    .SYNOPSIS
        删目录树（尽力而为；与 mt_env.ps1 的同名局部助手同实现，刻意不复用共享模块，
        以免跨版本分支移植时牵动 Mt.Paths）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    if (Test-Path -LiteralPath $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Remove-MtSaveData {
    <#
    .SYNOPSIS
        清理本次测试流程的存档数据（旧存档），返回「已删除路径」数组。

    .NOTES
        只扫 `run/<版本>/` 内的世界目录，且 `<run>/saves` 下的额外目录必须含 `level.dat`
        才删（避免误删用户手放的其它文件）。世界名由 Mt.Paths 的 `client_world` 派生，
        不硬编码 'testworld'。
    #>
    [CmdletBinding()]
    param([string[]]$Versions)

    $removed = @()
    $targets = if ($null -ne $Versions -and $Versions.Count -gt 0) { @($Versions) } else { @(Get-MtVersions) }
    foreach ($v in $targets) {
        $p = Get-MtPaths -Version $v
        $worldName = Split-Path -Leaf $p.client_world
        foreach ($w in @($p.client_world, $p.server_world)) {   # saves/<name> 与 run/<name>
            if (Test-Path -LiteralPath $w) {
                Remove-MtSaveTree -Path $w
                if (-not (Test-Path -LiteralPath $w)) { $removed += $w }
            }
        }
        if (Test-Path -LiteralPath $p.saves_dir -PathType Container) {
            foreach ($d in @(Get-ChildItem -LiteralPath $p.saves_dir -Directory -ErrorAction SilentlyContinue)) {
                if ($d.Name -eq $worldName) { continue }
                if (-not (Test-Path -LiteralPath (Join-Path $d.FullName 'level.dat') -PathType Leaf)) { continue }
                Remove-MtSaveTree -Path $d.FullName
                if (-not (Test-Path -LiteralPath $d.FullName)) { $removed += $d.FullName }
            }
        }
    }
    # ⚠️ 不要写 `return , $removed`：调用方是 `@(Remove-MtSaveData …)`，再包一层会让
    # 返回的数组变成「一个元素」（`$gone.Count` 恒为 1、`$gone[0]` 是整个数组）——
    # 2026-09-17 实测：删了 2 个世界却报 `PURGED 1` 且两个路径挤在同一行。
    return $removed
}

# ══ status ════════════════════════════════════════════════════════════════
function Invoke-MtCleanupStatus {
    [CmdletBinding()]
    param()

    $ppl = @(Get-MtPipelineProcs)
    $dps = @(Get-MtDaemonProcs)

    Write-MtLine "本流程进程 $($ppl.Count) 个："
    foreach ($p in $ppl) {
        Write-MtLine "    [$($p.Version)] PID=$($p.Pid)  $(Get-MtPreview $p.CommandLine)"
    }
    Write-MtLine "Gradle 守护进程 $($dps.Count) 个："
    foreach ($p in $dps) {
        Write-MtLine "    PID=$($p.Pid)  $(Get-MtPreview $p.CommandLine)"
    }

    $reason = Get-MtKeepAliveReason
    if ($reason) { Write-MtLine ''; Write-MtLine "取证标记存在（退出清理会保留现场）：$reason" }

    $clean = ($ppl.Count -eq 0 -and $dps.Count -eq 0)
    Write-MtLine ''
    if ($clean) { Write-MtLine 'MT_CLEANUP_STATUS: CLEAN — 无残留' }
    else { Write-MtLine 'MT_CLEANUP_STATUS: RESIDUAL — 仍有残留' }
    if ($clean) { return 0 }
    return 1
}

# ══ run ═══════════════════════════════════════════════════════════════════
function Invoke-MtRunCleanup {
    <#
    .SYNOPSIS
        收停本流程进程（可复用入口）。

    .NOTES
        mt_preflight 也调它做「启动前兜底清理」—— 在下次启动时生效的兜底，不能只靠
        退出钩子。
    #>
    [CmdletBinding()]
    param(
        [string[]]$Versions,
        [bool]$Force = $false,
        [bool]$KeepDaemon = $false,
        [double]$DaemonTimeout = 90.0,
        [bool]$Quiet = $false,
        [bool]$PurgeSaves = $false
    )

    $hasVersions = ($null -ne $Versions -and $Versions.Count -gt 0)

    $reason = Get-MtKeepAliveReason
    if ($reason -and -not $Force) {
        Write-MtLine "MT_CLEANUP: SKIP — 失败取证标记存在，保留游戏现场（$reason）"
        Write-MtLine "           取证完成后执行：pwsh -File scripts/test/mt.ps1 --phase stop --force"
        Write-MtLine 'MT_CLEANUP_RESULT: SKIP'
        return 0
    }

    $killed = 0
    $targets = if ($hasVersions) { @($Versions) } else { @(Get-MtVersions) }
    foreach ($v in $targets) {
        $killed += [int](Stop-MtVersionProcesses -Paths (Get-MtPaths -Version $v) -Quiet)
    }

    $stoppedDaemon = $false
    if (-not $KeepDaemon) {
        # 守护进程不归属单版本：只收停部分版本时不该动它，否则会破坏其它版本的构建热态
        if ($hasVersions) {
            if (-not $Quiet) { Write-MtLine 'MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程' }
        } else {
            $gradlew = Join-Path (Get-MtRoot) 'gradlew.bat'
            if (-not (Test-Path -LiteralPath $gradlew -PathType Leaf)) {
                $gradlew = Join-Path (Get-MtRoot) 'gradlew'
            }
            if (Test-Path -LiteralPath $gradlew -PathType Leaf) {
                $r = Invoke-MtProcessFull -FilePath 'cmd.exe' `
                    -ArgumentList @('/c', $gradlew, '--stop') -TimeoutSec $DaemonTimeout
                # python 侧 subprocess.run(timeout=…) 超时会折叠成 rc=1，此处对齐
                $rc = if ($r.TimedOut) { 1 } else { [int]$r.ExitCode }
                $stoppedDaemon = ($rc -eq 0)
                if (-not $stoppedDaemon -and -not $Quiet) {
                    $o = $r.StdOut.Trim()
                    $extra = if ($o) { '：' + (Get-MtPreview $o) } else { '' }
                    Write-MtLine "MT_CLEANUP: 警告 — gradlew --stop 未成功（rc=$rc）$extra"
                }
            }
        }
    }

    if ($reason -and $Force) {
        try {
            Remove-Item -LiteralPath $script:KeepAlive -Force -ErrorAction Stop
            Write-MtLine 'MT_CLEANUP: 已按 --force 清除失败取证标记'
        } catch { }
    }

    # 存档清理必须**在进程收停之后**（世界文件被客户端句柄占用时删不干净）。
    # 只删 run/<版本>/ 内的世界目录；`.mt_keep_alive` 存在时已在上面整个 SKIP。
    if ($PurgeSaves) {
        $gone = @(Remove-MtSaveData -Versions $targets)
        if (-not $Quiet) {
            foreach ($g in $gone) { Write-MtLine ("MT_CLEANUP: 已清理旧存档 $g") }
        }
        Write-MtLine ("MT_CLEANUP_SAVES: PURGED {0}" -f $gone.Count)
    } else {
        Write-MtLine 'MT_CLEANUP_SAVES: KEPT（未指定 --purge-saves）'
    }

    $left = @(Get-MtPipelineProcs -Versions $targets)
    # 收停是异步的（TerminateProcess 返回 ≠ 进程已消失，实测客户端被杀后仍会短暂存在于
    # 进程表里）：2026-09-17 起**先等它退出**再判残留，否则「测试后收尾」这条强制纪律
    # 会被慢退出的客户端误报成 RESIDUAL/退出码 1。上限 20s，超时仍按残留如实报。
    if ($left.Count -gt 0) {
        $settleDeadline = (Get-Date).AddSeconds(20)
        while ($left.Count -gt 0 -and (Get-Date) -lt $settleDeadline) {
            Start-Sleep -Milliseconds 500
            $left = @(Get-MtPipelineProcs -Versions $targets)
        }
    }
    $ldaemon = @(Get-MtDaemonProcs)
    if (-not $Quiet) {
        $scope = if ($hasVersions) { '（范围：' + ($targets -join ', ') + '）' } else { '' }
        $daemonTxt = if ($stoppedDaemon) { '，Gradle 守护已停止' } else { '' }
        Write-MtLine "MT_CLEANUP: 已收停 $killed 个本流程进程$scope$daemonTxt"
        if ($KeepDaemon) {
            Write-MtLine "MT_CLEANUP: 按 --keep-daemon 保留 $($ldaemon.Count) 个守护进程"
        }
        if ($left.Count -gt 0) {
            $pids = '[' + (($left | ForEach-Object { $_.Pid }) -join ', ') + ']'
            Write-MtLine "MT_CLEANUP: 警告 — 仍有 $($left.Count) 个本流程进程未退出：$pids"
        }
        if (-not $KeepDaemon -and -not $hasVersions -and $ldaemon.Count -gt 0) {
            Write-MtLine "MT_CLEANUP: 提示 — 仍有 $($ldaemon.Count) 个 Gradle 守护进程（可能属其它项目，未强杀）"
        }
    }

    # 机器可读结论（`--quiet` 也输出）：供 mt.ps1 决定提示语，避免「跳过」被说成「完成」
    if ($left.Count -gt 0) { Write-MtLine 'MT_CLEANUP_RESULT: RESIDUAL'; return 1 }
    Write-MtLine 'MT_CLEANUP_RESULT: OK'
    return 0
}

# ══ 入口 ══════════════════════════════════════════════════════════════════
$Cmd = ''
$VersionArgs = @()
$ForceFlag = $false
$KeepDaemonFlag = $false
$PurgeSavesFlag = $false
$DaemonTimeoutSec = 90.0
$QuietFlag = $false

$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    $key = $tok.TrimStart('-').ToLowerInvariant()
    if ($tok -notlike '-*') {
        if ($Cmd) { Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR }
        $Cmd = $key
        $i++
    } elseif ($key -eq 'version') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
        $VersionArgs += [string]$args[$i + 1]
        $i += 2
    } elseif ($key -eq 'force') {
        $ForceFlag = $true; $i++
    } elseif ($key -eq 'keep-daemon') {
        $KeepDaemonFlag = $true; $i++
    } elseif ($key -eq 'purge-saves') {
        $PurgeSavesFlag = $true; $i++
    } elseif ($key -eq 'daemon-timeout') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --daemon-timeout 的值'; exit $MT_EXIT_ERROR }
        $DaemonTimeoutSec = [double]$args[$i + 1]
        $i += 2
    } elseif ($key -eq 'quiet') {
        $QuietFlag = $true; $i++
    } else {
        Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
    }
}

if ($Cmd -eq 'status') {
    $rc = Invoke-MtCleanupStatus
    exit $rc
}
if ($Cmd -eq 'run') {
    $rc = Invoke-MtRunCleanup -Versions $VersionArgs -Force $ForceFlag `
        -KeepDaemon $KeepDaemonFlag -DaemonTimeout $DaemonTimeoutSec -Quiet $QuietFlag `
        -PurgeSaves $PurgeSavesFlag
    exit $rc
}

Write-MtErrorLine '必须指定子命令 status 或 run'
exit $MT_EXIT_ERROR
