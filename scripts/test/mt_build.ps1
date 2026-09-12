#Requires -Version 7.0
<#
.SYNOPSIS
    mt_build — Gradle 构建守护（阶段 B）。

.DESCRIPTION
    规则来源：AGENTS「Gradle 构建守护规则」。单次执行最多 N 秒；超时后先看输出是否已含
    BUILD SUCCESSFUL（构建已完成但进程未退出），否则强杀进程树并按产物 jar 时间戳判定
    是否实际完成；未完成则重试，最多 3 次。
    与旧流程的差异：产物校验按**当前版本子项目**取值，不再硬编码 neoforge 目录。

.EXAMPLE
    pwsh -File scripts/test/mt_build.ps1 --version 1.21.1
    pwsh -File scripts/test/mt_build.ps1 --version 1.21.1 --timeout 60 --retries 3

.NOTES
    对应源文件（迁移前）：scripts/test/mt_build.sh。

    日志拆两个文件（bash 的 `> "$LOG" 2>&1` 是单文件合并）:
      temp/mt_build_<版本>_<epoch>.log      stdout（构建结论行都在这里）
      temp/mt_build_<版本>_<epoch>.log.err  stderr
    见 Mt.Proc.psm1 / Start-MtProcessToFile 的说明。
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Proc.psm1')

Initialize-MtConsole

function Get-MtJarStamp {
    <#
    .SYNOPSIS
        产物 jar 的「文件名 + mtime(epoch)」多行快照，用于判定构建是否真的产出。

    .NOTES
        排序用序数比较（bash 的 glob 是字节序；Sort-Object 默认是文化序，会不一致）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$JarDir)

    if (-not (Test-Path -LiteralPath $JarDir -PathType Container)) { return '' }
    $names = @()
    foreach ($f in @(Get-ChildItem -LiteralPath $JarDir -File)) {
        if ($f.Extension -eq '.jar') { $names += $f.FullName }
    }
    if ($names.Count -eq 0) { return '' }
    [Array]::Sort([string[]]$names, [System.StringComparer]::Ordinal)

    $lines = @()
    foreach ($n in $names) {
        $fi = Get-Item -LiteralPath $n
        $epoch = [DateTimeOffset]::new($fi.LastWriteTimeUtc).ToUnixTimeSeconds()
        $lines += "$($fi.Name) $epoch"
    }
    return ($lines -join "`n")
}

function Get-MtLogTailLines {
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text, [int]$Count = 15)

    $lines = @($Text -split "`r?`n")
    if ($lines.Count -gt 0 -and $lines[-1] -eq '') { $lines = @($lines[0..($lines.Count - 2)]) }
    if ($lines.Count -le $Count) { return $lines }
    return @($lines[($lines.Count - $Count)..($lines.Count - 1)])
}

# ══ 入口 ══════════════════════════════════════════════════════════════════
if ($MyInvocation.InvocationName -ne '.') {

    $Version = ''
    $TimeoutSec = 60
    $Retries = 3

    $i = 0
    while ($i -lt $args.Count) {
        $tok = [string]$args[$i]
        $key = $tok.TrimStart('-').ToLowerInvariant()
        if ($key -eq 'version') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
            $Version = [string]$args[$i + 1]
            $i += 2
        } elseif ($key -eq 'timeout') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --timeout 的值'; exit $MT_EXIT_ERROR }
            $TimeoutSec = [int]$args[$i + 1]
            $i += 2
        } elseif ($key -eq 'retries') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --retries 的值'; exit $MT_EXIT_ERROR }
            $Retries = [int]$args[$i + 1]
            $i += 2
        } else {
            Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
        }
    }

    if (-not $Version) { Write-MtErrorLine '必须指定 --version'; exit $MT_EXIT_ERROR }
    if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }

    $p = Get-MtPaths -Version $Version
    $root = Get-MtRoot

    Start-MtPhase "build ($Version)"

    $jarDir = Join-Path (Join-Path $root $p.subproject) 'build/libs'
    $tempDir = Join-Path $root 'temp'
    if (-not (Test-Path -LiteralPath $tempDir)) { [void](New-Item -ItemType Directory -Force -Path $tempDir) }
    $log = Join-Path $tempDir "mt_build_${Version}_$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds()).log"

    $gradlew = Join-Path $root 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradlew -PathType Leaf)) { $gradlew = Join-Path $root 'gradlew' }

    $attempt = 0
    while ($attempt -lt $Retries) {
        $attempt++
        Write-MtInfo "[尝试 $attempt/$Retries] gradlew $($p.task_build) (超时 ${TimeoutSec}s)"
        $before = Get-MtJarStamp -JarDir $jarDir

        $started = $null
        try {
            $started = Start-MtProcessToFile -FilePath 'cmd.exe' `
                -ArgumentList @('/c', $gradlew, $p.task_build, '--console=plain') `
                -LogPath $log -WorkingDirectory $root -MergeStderr
        } catch {
            $started = $null
        }

        if ($null -eq $started) {
            Write-MtWarn "无法启动 gradlew（$gradlew）"
        } else {
            $elapsed = 0
            while (-not $started.Process.HasExited -and $elapsed -lt $TimeoutSec) {
                [void]$started.Process.WaitForExit(2000)
                $elapsed += 2
            }
            if (-not $started.Process.HasExited) {
                # 超时分支
                $txt = Read-MtSharedText -Path $log
                if ($txt.Contains('BUILD SUCCESSFUL')) {
                    Write-MtWarn '输出已含 BUILD SUCCESSFUL，构建实际已完成，终止残留进程'
                }
                [void](Stop-MtVersionProcesses -Paths $p -Quiet)
                [void]$started.Process.WaitForExit(5000)
                Start-Sleep -Seconds 2
            }
        }

        $txt = Read-MtSharedText -Path $log
        if ($txt.Contains('BUILD FAILED')) {
            Write-MtError 'build' "编译失败（见 $log）"
            foreach ($ln in (Get-MtLogTailLines -Text $txt -Count 15)) { Write-MtErrLine $ln }
            exit $MT_EXIT_ERROR
        }

        $after = Get-MtJarStamp -JarDir $jarDir
        if ($after -and $after -ne $before) {
            Write-MtOk 'BUILD' "产物已更新：$((($after -split "`n")[0]))"
            exit $MT_EXIT_PASS
        }
        if ($txt.Contains('BUILD SUCCESSFUL') -and $after) {
            Write-MtOk 'BUILD' '输出含 BUILD SUCCESSFUL 且产物存在（时间戳未变）'
            exit $MT_EXIT_PASS
        }

        Write-MtWarn '本轮未见构建结果，产物未更新，准备重试'
    }

    Write-MtError 'build' "$Retries 次尝试均失败"
    exit $MT_EXIT_ERROR
}
