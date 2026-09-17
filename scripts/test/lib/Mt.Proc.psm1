<#
Mt.Proc.psm1 — 外部命令执行与进程识别（Windows 中文环境容错）。

迁移前源文件：scripts/test/lib/mt_ps.py（已在 92fbeaf 删除；取回：`git show 92fbeaf^:scripts/test/lib/mt_ps.py`）。

## 为什么不能直接用 & / Start-Process 捕获输出

Python 版记录过一个**真实故障**：Windows 上子进程输出常混入非 UTF-8 字节——中文路径按
GBK 编码、或控制台 OEM 码页。用默认解码器读会抛 UnicodeDecodeError，而且异常发生在读取
线程里：外部命令明明有输出，调用方却拿到「空结果」，表现为**静默失败**，比直接报错更危险
（典型后果：mt_stop 报告「已停止 0 个进程」，实际进程还在跑）。

对策（本模块复刻）：
  1. 以**原始字节**取回 stdout/stderr（绕开 StreamReader 的解码），
  2. 再用 UTF-8 + 替换字符解码。所有需要匹配的标记（子项目名、run 目录、PID）都是 ASCII，
     替换字符不影响判定。
  3. 启动失败折叠为 (1, "")，调用方按「无输出」处理，不需要 try。
#>

# Stop-MtVersionProcesses 的「  已停止 PID=…」/`MT_KILL:` 行必须与 python 版逐字节一致，
# 因此输出统一走 Mt.Phase 的 LF 原语（不得 Write-Output / Write-Host）。
# ⚠️ 不得用 -Force：-Force 会卸载重载 Mt.Phase，从而摘掉调用方脚本作用域里已导入的
#    Mt.Phase 函数（Import-Module 本身不报错，后续调用才炸）—— 详见 Mt.Paths.psm1 同类注释。
Import-Module (Join-Path $PSScriptRoot 'Mt.Phase.psm1')

function ConvertFrom-MtBytes {
    <#
    .SYNOPSIS
        UTF-8 容错解码：非法字节序列替换为 U+FFFD，绝不抛异常。
    #>
    [CmdletBinding()]
    param([byte[]]$Bytes)

    if ($null -eq $Bytes -or $Bytes.Length -eq 0) { return '' }
    $enc = [System.Text.UTF8Encoding]::new($false, $false)   # 不写 BOM、不抛异常
    return $enc.GetString($Bytes)
}

function Invoke-MtProcess {
    <#
    .SYNOPSIS
        执行外部命令并以容错方式取回输出。
    .OUTPUTS
        PSCustomObject：ExitCode（int）、StdOut（string）。
    .NOTES
        超时后强杀**整棵进程树**（.NET Core 的 Kill(entireProcessTree)），并把退出码按
        实际死亡结果返回；调用方负责判定「超时」这件事本身。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$ArgumentList = @(),
        [double]$TimeoutSec = 60,
        [string]$WorkingDirectory = ''
    )

    $r = Invoke-MtProcessFull -FilePath $FilePath -ArgumentList $ArgumentList `
        -TimeoutSec $TimeoutSec -WorkingDirectory $WorkingDirectory
    return [pscustomobject]@{ ExitCode = $r.ExitCode; StdOut = $r.StdOut }
}

function Invoke-MtProcessFull {
    <#
    .SYNOPSIS
        同 Invoke-MtProcess，但额外返回 StdErr 与 TimedOut 标志。
    .OUTPUTS
        PSCustomObject：ExitCode（int）、StdOut、StdErr、TimedOut（bool）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$ArgumentList = @(),
        [double]$TimeoutSec = 60,
        [string]$WorkingDirectory = ''
    )

    $fail = [pscustomobject]@{ ExitCode = 1; StdOut = ''; StdErr = ''; TimedOut = $false }

    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $FilePath
    foreach ($a in $ArgumentList) { [void]$psi.ArgumentList.Add([string]$a) }
    if ($WorkingDirectory) { $psi.WorkingDirectory = $WorkingDirectory }
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true

    $proc = $null
    try {
        $proc = [System.Diagnostics.Process]::Start($psi)
    } catch {
        return $fail
    }
    if ($null -eq $proc) { return $fail }

    $outMs = [System.IO.MemoryStream]::new()
    $errMs = [System.IO.MemoryStream]::new()
    $outTask = $null
    $errTask = $null
    try {
        $outTask = $proc.StandardOutput.BaseStream.CopyToAsync($outMs)
        $errTask = $proc.StandardError.BaseStream.CopyToAsync($errMs)

        $timeoutMs = [int][Math]::Max(1, [Math]::Round($TimeoutSec * 1000))
        $exited = $proc.WaitForExit($timeoutMs)
        $timedOut = -not $exited
        if ($timedOut) {
            Stop-MtProcessTree -Process $proc
            [void]$proc.WaitForExit(30000)
        }
        # 排水：等两个读取任务收尾（进程已死，管道已关闭，不会永久阻塞）
        foreach ($t in @($outTask, $errTask)) {
            if ($null -ne $t) { [void]$t.Wait(10000) }
        }
    } catch {
        Stop-MtProcessTree -Process $proc
    } finally {
        try { if (-not $proc.HasExited) { Stop-MtProcessTree -Process $proc } } catch { }
    }

    $code = 1
    try { $code = $proc.ExitCode } catch { $code = 1 }

    return [pscustomobject]@{
        ExitCode = $code
        StdOut   = (ConvertFrom-MtBytes $outMs.ToArray())
        StdErr   = (ConvertFrom-MtBytes $errMs.ToArray())
        TimedOut = $timedOut
    }
}

function Stop-MtProcessTree {
    <#
    .SYNOPSIS
        强杀一棵进程树。优先用 .NET 的整树 Kill；失败退回 taskkill /T /F。
    .NOTES
        ⚠️ 调用方必须先确认目标 PID 属于本流程（见 Get-MtProcessMarkers），
        本函数不做任何归属判断 —— 绝不按进程名批量杀 java。
    #>
    [CmdletBinding()]
    param(
        [System.Diagnostics.Process]$Process,
        [int]$ProcessId = 0
    )

    if ($null -ne $Process) {
        try {
            if (-not $Process.HasExited) { $Process.Kill($true) }
            return
        } catch { }
        try {
            if (-not $Process.HasExited) { $Process.Kill() }
            return
        } catch { }
    }
    if ($ProcessId -gt 0) {
        [void](Invoke-MtProcess -FilePath 'taskkill' -ArgumentList @('/PID', "$ProcessId", '/T', '/F') -TimeoutSec 30)
    }
}

function Get-JavaProcesses {
    <#
    .SYNOPSIS
        当前 java 进程的 (Pid, CommandLine) 列表。
    .OUTPUTS
        PSCustomObject 数组：Pid（int）、CommandLine（string）。
    .NOTES
        Windows 专属实现（原 python 版的 ps -eo 分支随「仅 Windows」的迁移一并移除）。
    #>
    [CmdletBinding()]
    param()

    $out = @()
    try {
        $out = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction Stop |
            ForEach-Object {
                [pscustomobject]@{
                    Pid         = [int]$_.ProcessId
                    CommandLine = [string]$_.CommandLine
                }
            })
    } catch {
        $out = @()
    }
    return $out
}

function Get-GradleDaemonProcesses {
    <#
    .SYNOPSIS
        当前 Gradle 守护进程列表（命令行含 GradleDaemon）。
    #>
    [CmdletBinding()]
    param()

    return @(Get-JavaProcesses | Where-Object { $_.CommandLine -like '*GradleDaemon*' })
}

function Read-MtSharedText {
    <#
    .SYNOPSIS
        读取一个**正被其它进程写入**的文本文件（容错 UTF-8）。

    .NOTES
        用途：读取 Gradle 后台构建日志 / runClient 启动日志。python 版的 `grep "$LOG"`
        也是读一个仍在增长的文件，但 CPython 的 open() 默认共享，Windows 上 .NET 的
        File.ReadAllText 默认 **FileShare.Read**（拒绝写入者共享）会抛 IOException。
        这里显式以 FileShare::ReadWrite 打开，读不到就返回 ''（与 grep 找不到等价）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
    $fs = $null
    $sr = $null
    try {
        $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        $sr = [System.IO.StreamReader]::new($fs, [System.Text.UTF8Encoding]::new($false, $false))
        return $sr.ReadToEnd()
    } catch {
        return ''
    } finally {
        if ($null -ne $sr) { $sr.Dispose() } elseif ($null -ne $fs) { $fs.Dispose() }
    }
}

function ConvertTo-MtStartArgs {
    <#
    .SYNOPSIS
        给 Start-Process 用的参数数组：把**含空白**的参数自己包上引号。

    .NOTES
        实测（本机 pwsh 7.6.6）：Start-Process **不会**自动给数组元素加引号 ——
        `-ArgumentList @('/c', 'probe.cmd', 'one two')` 到子进程只剩 `one`（被拆成两个参数）。
        因此这里显式补引号；已经自带引号的不重复添加。
        对照：Invoke-MtProcess* 走 ProcessStartInfo.ArgumentList（.NET 负责转义），不需要本函数。
    #>
    [CmdletBinding()]
    param([string[]]$ArgumentList = @())

    $out = @()
    foreach ($a in $ArgumentList) {
        $s = [string]$a
        if ($s -match '\s' -and -not ($s.StartsWith('"') -and $s.EndsWith('"'))) { $s = '"' + $s + '"' }
        $out += $s
    }
    return , $out
}

function Start-MtProcessToFile {
    <#
    .SYNOPSIS
        异步启动一个外部命令，把 stdout 落到 LogPath、stderr 落到 LogPath.err。

    .OUTPUTS
        PSCustomObject：Process（System.Diagnostics.Process）、LogPath。
        调用方用 `$r.Process.HasExited` / `WaitForExit(ms)` 做存活与超时判定，用
        Read-MtSharedText -Path $LogPath 读实时日志。

    .NOTES
        **实现要点（不是随便挑的）**：重定向交给**子 shell**（cmd 的 `> 文件 2>&1`）或
        Start-Process 的 `-RedirectStandardOutput <文件>` —— 两者都是**真实文件句柄**，
        而不是管道。这一点很关键：mt_launch 启动的 runClient 必须在**本脚本退出之后继续跑**
        （launch → cases 分步执行），若用管道（ProcessStartInfo.RedirectStandardOutput +
        父进程读线程），父进程一退出管道读端就关闭，子进程写 stdout 会直接失败（客户端崩）。
        实测：父 pwsh 退出后，子进程仍能把后续输出写进该文件。

        **`-MergeStderr` 是 mt_build / mt_launch / mt_env(world) 必须开的**：
        Gradle 把 `BUILD FAILED`、编译错误、异常栈全部写在 **stderr**（bash 版正是靠
        `> "$LOG" 2>&1` 才抓得到）。若拆成 `<LogPath>` + `<LogPath>.err` 两个文件，
        「编译失败」会退化成「本轮未见构建结果」（实测踩到）。开这个开关就复刻 bash 的
        单文件合并语义。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$ArgumentList = @(),
        [Parameter(Mandatory)][string]$LogPath,
        [string]$WorkingDirectory = '',
        [switch]$MergeStderr
    )

    if ($MergeStderr) {
        $argv = $ArgumentList + @('>', ('"' + $LogPath + '"'), '2>&1')
        $spMerge = @{
            FilePath     = $FilePath
            ArgumentList = (ConvertTo-MtStartArgs -ArgumentList $argv)
            PassThru     = $true
            NoNewWindow  = $true
        }
        if ($WorkingDirectory) { $spMerge['WorkingDirectory'] = $WorkingDirectory }
        $procM = Start-Process @spMerge
        return [pscustomobject]@{ Process = $procM; LogPath = $LogPath }
    }

    $sp = @{
        FilePath               = $FilePath
        ArgumentList           = (ConvertTo-MtStartArgs -ArgumentList $ArgumentList)
        RedirectStandardOutput = $LogPath
        RedirectStandardError  = "$LogPath.err"
        PassThru               = $true
        NoNewWindow            = $true
    }
    if ($WorkingDirectory) { $sp['WorkingDirectory'] = $WorkingDirectory }

    $proc = Start-Process @sp

    return [pscustomobject]@{ Process = $proc; LogPath = $LogPath }
}

function Test-MtClientProcess {
    <#
    .SYNOPSIS
        该 java 进程是否为「**本版本**的 Minecraft 客户端」（进程级双路径判定）。

    .NOTES
        2026-09-15 新增。此前只有「命令行命中本流程标记」一条路径，而本机 DevLaunch
        （ModDevGradle legacyforge/neoforge 的 runClient）把真正的参数写进 **args 文件**，
        `Win32_Process.CommandLine` 只剩
        `net.caffeinemc.sodium / net.minecraft.client.main.Main /`（实测 56 字符），
        **不含**子项目名与 run 目录 → 旧判定恒为「客户端未在运行」。

        双路径（**同等严格**，不是放宽）：
          ① 命令行命中 `Get-MtProcessMarkers`（普通 Gradle 启动仍然走这条）；
          ② **窗口标题包含本版本号**（`MainWindowTitle`，与 mt_inject 的
             「标题含版本号」回退同一口径）。

        两条都不成立才算「不是本版本客户端」：
          · 崩溃/被收停 → 进程已退出 → 无标题 → false；
          · Gradle 守护 / 包装器 → 命令行不含 Main，或标题为空 → false；
          · 另一个版本的客户端 → 命令行无本版本 marker **且**标题含的是另一个版本号 → false。
        ⚠️ 版本号匹配**必须**用 `.Contains()` 字面量：`-match '1.2.1'` 的正则点号会误命中
           `1.21.1`；`-match '1.21.1'` 同理会被 `1x21y1` 之类文本命中。禁止改成正则。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][psobject]$Paths,
        [Parameter(Mandatory)][int]$ProcessId,
        [Parameter(Mandatory)][AllowEmptyString()][string]$CommandLine
    )

    $title = ''
    try {
        $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
        if ($null -ne $proc) { $title = [string]$proc.MainWindowTitle }
    } catch {
        $title = ''
    }

    # 路径①（最强、跨加载器）：**窗口标题含本版本号**。
    #   1.21.1 DevLaunch → `Minecraft NeoForge* 1.21.1 - 单人游戏`
    #   1.20.1 DevLaunch → `Minecraft* Forge 1.20.1 - 单人游戏`
    #   Gradle 守护/包装器无窗口 → MainWindowTitle 为空 → 不会误命中。
    if ($title -and $title.Contains([string]$Paths.version)) { return $true }

    # 路径②（无窗口/标题读不到时的兜底）：命令行必须是**客户端入口**，且带本版本证据。
    #   ⚠️ 2026-09-15 实测：1.20.1 的 DevLaunch 客户端命令行**不含**
    #   `net.minecraft.client.main.Main`（走 `cpw.mods.bootstraplauncher.BootstrapLauncher`，
    #   且不含 `:forge-1.20.1:` / `run\1.20.1` 任一 marker）——只认 Main + marker 的旧判定
    #   对 1.20.1 恒为「未在运行」，连带 `mt_stop --version 1.20.1` 静默杀 0 个进程。
    #   ⚠️ 2026-09-16 补充：MC 26.1.2 的 NeoForge 客户端入口是
    #   `net.neoforged.fml.startup.Client`（不再是 `net.minecraft.client.main.Main`），
    #   必须一并识别，否则 26.1.2 客户端的兜底判定恒为 false。
    #   注意用**全限定名**匹配：`net.neoforged.fml.startup.DataClient`（datagen 运行）
    #   不含该子串，不会被误认成客户端。
    $isClientEntry = $CommandLine.Contains('net.minecraft.client.main.Main') -or
                     $CommandLine.Contains('bootstraplauncher') -or
                     $CommandLine.Contains('net.neoforged.fml.startup.Client')
    if (-not $isClientEntry) { return $false }

    foreach ($m in @(Get-MtProcessMarkers -Paths $Paths)) {
        if ($m -and $CommandLine.Contains([string]$m)) { return $true }
    }
    # 命令行里同时出现**本版本号**与**本子项目名**也算证据（如
    # `-Dfml.modFolders=astral_dice%%…\forge-1.20.1\build\…`）。
    if ($CommandLine.Contains([string]$Paths.version) -and $CommandLine.Contains([string]$Paths.subproject)) {
        return $true
    }
    return $false
}

function Test-MtPipelineProcess {
    <#
    .SYNOPSIS
        该 java 进程是否属于**本版本的测试流程**（客户端 **或** 专用服务端 / 数据生成 / run 任务包装器）。

    .NOTES
        2026-09-17 新增（用户规则「测试任务完成后关闭测试端…避免游戏进程长时间驻留」暴露的缺口）：
        收停路径此前只认**客户端入口**（`Test-MtClientProcess`），于是一台由 `mt_env world`
        或 `runServerData` 起起来的**专用服务端**永远杀不掉 —— 实测 `mt_env world` 跑完后
        `:neoforge-26.1.2:runServer` 的 Gradle 包装器与它的服务端 JVM 双双存活，
        还因为孙进程仍持有父进程的 stdout 句柄，把 `mt.ps1 --phase env` 卡在等待里。

        判定 = **版本证据**（marker 或 version+subproject，口径与 `Test-MtClientProcess` 完全一致）
        **且** 命中下列任一「本流程产物」：
          ① 客户端 —— 直接复用 `Test-MtClientProcess`（含窗口标题回退，最严格）；
          ② 本版本的 **run/runData 任务包装器**：命令行含 `gradle-wrapper.jar` 且含
             `:<子项目>:run` 选择器。⚠️ 只认 `run` 家族：`:neoforge-26.1.2:build` 之类的
             **构建任务不属于测试流程，绝不能杀**（否则构建中途被收停会毁产物）；
          ③ 本版本的 **dev-launch JVM**：入口 `net.neoforged.devlaunch.Main`。专用服务端与
             两段式数据生成的命令行**不含** run 目录与子项目选择器（2026-09-17 实测：
             `:neoforge-26.1.2:`/`run\26.1.2` 均为 false，只有 `-Dfml.modFolders=…\neoforge-26.1.2\build\…`），
             故必须靠「version + subproject」这条证据认领。

        客户端存活判定（`Get-MtClientStatus`）**仍然只用** `Test-MtClientProcess`：
        服务端在跑 ≠ 客户端在跑，这个区别必须保持。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][psobject]$Paths,
        [Parameter(Mandatory)][int]$ProcessId,
        [Parameter(Mandatory)][AllowEmptyString()][string]$CommandLine
    )

    if (Test-MtClientProcess -Paths $Paths -ProcessId $ProcessId -CommandLine $CommandLine) { return $true }

    $evidence = $false
    foreach ($m in @(Get-MtProcessMarkers -Paths $Paths)) {
        if ($m -and $CommandLine.Contains([string]$m)) { $evidence = $true; break }
    }
    if (-not $evidence) {
        $evidence = $CommandLine.Contains([string]$Paths.version) -and $CommandLine.Contains([string]$Paths.subproject)
    }
    if (-not $evidence) { return $false }

    if ($CommandLine.Contains('gradle-wrapper.jar') -and
        $CommandLine.Contains((':{0}:run' -f [string]$Paths.subproject))) { return $true }

    if ($CommandLine.Contains('net.neoforged.devlaunch.Main')) { return $true }

    return $false
}

function Stop-MtVersionProcesses {
    <#
    .SYNOPSIS
        只停止属于**本版本测试**的 java 进程；返回停止数量。

    .NOTES
        对应 python 侧的 `mt_env.kill_version`（本函数是它在 PowerShell 侧的唯一实现，
        mt_env.ps1 / mt_cleanup.ps1 共用，避免两套逻辑漂移）。匹配依据由调用方经
        `Get-MtProcessMarkers` 给出：刻意不用裸子项目名（那同时是仓库内的目录名，会把
        只是引用了该目录的进程——IDE 语言服务器等——误杀）。旧流程「按进程名 java 全杀」
        更是明确的破坏性行为。

        2026-09-15：判定统一改为 `Test-MtClientProcess`（命令行 marker **或**
        本版本窗口标题）。原因：DevLaunch 客户端的命令行不含任何版本 marker，
        旧判定导致 `mt_stop --version X` **静默杀 0 个**，而「杀不掉」会直接
        毒化下一次 mt_launch 的日志窗口（读到他人/上一轮的 latest.log）。
        窗口标题路径同样**只认本版本号**，不会退化成「按 java 名全杀」。

        2026-09-17：判定改为 `Test-MtPipelineProcess` —— 在客户端之上**补上专用服务端、
        数据生成与 run 任务包装器**（见该函数的注释：`mt_env world` 起的 runServer 此前
        永远杀不掉，还会把 `--phase env` 卡住）。客户端存活判定不受影响（仍用
        `Test-MtClientProcess`）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][psobject]$Paths,
        [switch]$Quiet
    )

    $killed = 0
    foreach ($p in Get-JavaProcesses) {
        if (-not (Test-MtPipelineProcess -Paths $Paths -ProcessId $p.Pid -CommandLine ([string]$p.CommandLine))) {
            continue
        }
        [void](Invoke-MtProcess -FilePath 'taskkill' `
                -ArgumentList @('/PID', "$($p.Pid)", '/T', '/F') -TimeoutSec 30)
        $killed++
        if (-not $Quiet) { Write-MtLine "  已停止 PID=$($p.Pid)" }
    }
    if (-not $Quiet) { Write-MtLine "MT_KILL: $($Paths.version) 停止 $killed 个进程" }
    return $killed
}

function Get-MtClientStatus {
    <#
    .SYNOPSIS
        本版本 Minecraft 客户端（runClient）的存活状态。

    .NOTES
        2026-09-13 新增。此前工具链**完全没有客户端存活校验**：崩溃/被收停后各步骤仍照跑并
        逐条打印 PASS（注入器只打 MT_INJECT_CMD，不校验命令是否落地），失败要等几个用例之后
        才以猜谜式的「断言未命中」暴露。判定口径：
          ① 进程：java 进程中**同时**命中本流程标记（Get-MtProcessMarkers，与
             Stop-MtVersionProcesses 同一套）且命令行含 net.minecraft.client.main.Main
             —— 后半句把 Gradle 守护/包装器排除掉；
          ② 崩溃报告：run/<版本>/crash-reports/crash-*.txt 的数量与最新一份（供调用方按
             「相对基线是否新增」判因）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $pids = @()
    $titles = @()
    foreach ($p in Get-JavaProcesses) {
        if (-not (Test-MtClientProcess -Paths $Paths -ProcessId $p.Pid -CommandLine ([string]$p.CommandLine))) {
            continue
        }
        $pids += [int]$p.Pid
        try {
            $pr = Get-Process -Id $p.Pid -ErrorAction SilentlyContinue
            if ($null -ne $pr) { $titles += [string]$pr.MainWindowTitle }
        } catch { /* 忽略标题读取失败 */ }
    }
    $crashes = @()
    $crashDir = [string]$Paths.crash_dir
    if ($crashDir -and (Test-Path -LiteralPath $crashDir -PathType Container)) {
        $crashes = @(Get-ChildItem -LiteralPath $crashDir -Filter 'crash-*.txt' -File -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime)
    }
    return [pscustomobject]@{
        Version         = [string]$Paths.version
        Alive           = ($pids.Count -gt 0)
        Pids            = $pids
        Titles          = $titles
        CrashCount      = $crashes.Count
        LatestCrash     = $(if ($crashes.Count -gt 0) { [string]$crashes[-1].FullName } else { '' })
        LatestCrashTime = $(if ($crashes.Count -gt 0) { $crashes[-1].LastWriteTime } else { [datetime]::MinValue })
    }
}
Export-ModuleMember -Function @(
    'ConvertFrom-MtBytes', 'Invoke-MtProcess', 'Invoke-MtProcessFull',
    'Stop-MtProcessTree', 'Get-JavaProcesses', 'Get-GradleDaemonProcesses',
    'Read-MtSharedText', 'ConvertTo-MtStartArgs', 'Start-MtProcessToFile',
    'Stop-MtVersionProcesses', 'Get-MtClientStatus', 'Test-MtClientProcess', 'Test-MtPipelineProcess'
)
