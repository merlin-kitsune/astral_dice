#Requires -Version 7.0
<#
.SYNOPSIS
    脱离式执行器：把任意 mt 阶段/命令放到**独立控制台**里跑，输出落日志，调用方立即返回。

.DESCRIPTION
    为什么需要它（2026-09-12 真机实测，非猜测）:

    mt_build / mt_launch / mt_env(world) 会**故意留下长命子进程** —— Gradle 守护进程与
    Minecraft 客户端必须活过本脚本（launch → cases 是分步执行的），这是工具链的正确设计。
    问题出在**非交互式调用方**（代理/CI/管道捕获）：它按**整棵进程树**等待，
    只要还有后代活着，调用就不返回 —— 表现为「脚本卡滞」，直到超时被强杀，
    而强杀会连带杀掉整棵树（客户端、守护一起死，前面的启动白做）。

    实测对照（本机，2026-09-12）:
      · `mt.ps1 --phase build`（需**新起**守护）: 4s 构建成功，调用 600s 超时
      · `mt.ps1 --phase launch`                : 34s 进入世界，调用 500s 超时
      · 守护**已存在**时再 `--phase build`      : ~1s 正常返回（没有新后代）
      · `cmd /c "pwsh … > file 2>&1"`          : **无效** —— 说明不是 stdout 管道问题，
                                                  而是「等待整棵进程树」
      · `Start-Process -WindowStyle Hidden`    : **立即返回**，客户端/守护继续存活 ✔

    因此：凡会**新起长命后代**的阶段，一律用本脚本脱离执行，再按日志里的终态标记轮询。

.USAGE
    # 1) 脱离执行（立即返回 pid）
    pwsh -File scripts/devtools/Start-MtDetached.ps1 `
         -Script scripts/test/mt.ps1 -ScriptArgs @('--phase','launch','--version','1.21.1') `
         -Out temp/detached_launch_1211.log

    # 2) 轮询终态标记（示例：launch 的终态是 MT_LAUNCH: OK / MT_LAUNCH: FAIL / MT_BLOCKED）
    Select-String -LiteralPath temp/detached_launch_1211.log -Pattern 'MT_LAUNCH: OK'

    前台可安全直调（无长命后代）: mt_preflight / mt_case run / mt_assert / mt_report /
    mt_inject / mt_capture / mt_stop / mt_env(mods)。
    必须脱离的: mt_build（新起守护时）/ mt_launch / mt_env(world)（新起无头服务端时）。

.NOTES
    · `-Out` 与 `-Out.err` 分离落盘；`-Merge` 可合并到一个文件（复刻 cmd 的 `> f 2>&1`）。
    · 子进程拿到的是**文件句柄**（不是管道），故其长命后代写日志不会反向阻塞任何人。
    · 本脚本只负责「起」，不负责「等」：等待交给调用方按日志标记轮询，避免再引入
      一层进程树等待。
#>

$ErrorActionPreference = 'Stop'

$ScriptPath = ''
$ScriptArgs = @()
$Out = ''
$Merge = $false
$WorkDir = (Get-Location).Path

$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    $key = $tok.TrimStart('-').ToLowerInvariant()
    if ($key -eq 'script') {
        if ($i + 1 -ge $args.Count) { Write-Error '缺少 --script 的值'; exit 2 }
        $ScriptPath = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'out') {
        if ($i + 1 -ge $args.Count) { Write-Error '缺少 --out 的值'; exit 2 }
        $Out = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'workdir') {
        if ($i + 1 -ge $args.Count) { Write-Error '缺少 --workdir 的值'; exit 2 }
        $WorkDir = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'merge') {
        $Merge = $true; $i++
    } elseif ($key -eq 'arg' -or $key -eq 'scriptargs') {
        # 其余位置参数一律透传给目标脚本
        $rest = @()
        for ($j = $i + 1; $j -lt $args.Count; $j++) { $rest += [string]$args[$j] }
        $ScriptArgs = $rest
        break
    } else {
        $ScriptArgs += $tok; $i++
    }
}

if (-not $ScriptPath) { Write-Error '必须指定 --script'; exit 2 }
if (-not $Out) { Write-Error '必须指定 --out（日志路径）'; exit 2 }
if (-not (Test-Path -LiteralPath $ScriptPath -PathType Leaf)) { Write-Error "目标脚本不存在: $ScriptPath"; exit 2 }

$outFull = if ([System.IO.Path]::IsPathRooted($Out)) { $Out } else { Join-Path $WorkDir $Out }
$outDir = [System.IO.Path]::GetDirectoryName($outFull)
if ($outDir -and -not (Test-Path -LiteralPath $outDir)) { [void](New-Item -ItemType Directory -Force -Path $outDir) }
if (Test-Path -LiteralPath $outFull) { Remove-Item -LiteralPath $outFull -Force }
$errFull = "$outFull.err"
if (Test-Path -LiteralPath $errFull) { Remove-Item -LiteralPath $errFull -Force }

$psExe = (Get-Process -Id $PID).Path
$argv = @('-NoProfile', '-File', $ScriptPath) + $ScriptArgs

# stdout / stderr 分两个文件落盘（都是**文件句柄**，不会被长命后代反向阻塞）。
# 轮询时两个都要看：Gradle 的失败信息（BUILD FAILED / 编译错误）写在 stderr 侧。
$proc = Start-Process -FilePath $psExe -ArgumentList $argv `
    -PassThru -WindowStyle Hidden -WorkingDirectory $WorkDir `
    -RedirectStandardOutput $outFull -RedirectStandardError $errFull
Write-Output ("MT_DETACHED: pid={0} script={1} log={2}" -f $proc.Id, $ScriptPath, $outFull)
Write-Output ("MT_DETACHED_HINT: 轮询 {0} 里的终态标记；本进程树不会被调用方等待" -f $outFull)
exit 0
