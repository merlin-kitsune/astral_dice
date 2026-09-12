<#
Mt.Proc.psm1 — 外部命令执行与进程识别（Windows 中文环境容错）。

对应源文件：scripts/test/lib/mt_ps.py。

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
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][psobject]$Paths,
        [switch]$Quiet
    )

    $markers = @(Get-MtProcessMarkers -Paths $Paths)
    $killed = 0
    foreach ($p in Get-JavaProcesses) {
        $cmd = [string]$p.CommandLine
        $hit = $false
        foreach ($m in $markers) {
            if ($m -and $cmd.Contains([string]$m)) { $hit = $true; break }
        }
        if (-not $hit) { continue }
        [void](Invoke-MtProcess -FilePath 'taskkill' `
                -ArgumentList @('/PID', "$($p.Pid)", '/T', '/F') -TimeoutSec 30)
        $killed++
        if (-not $Quiet) { Write-MtLine "  已停止 PID=$($p.Pid)" }
    }
    if (-not $Quiet) { Write-MtLine "MT_KILL: $($Paths.version) 停止 $killed 个进程" }
    return $killed
}

Export-ModuleMember -Function @(
    'ConvertFrom-MtBytes', 'Invoke-MtProcess', 'Invoke-MtProcessFull',
    'Stop-MtProcessTree', 'Get-JavaProcesses', 'Get-GradleDaemonProcesses',
    'Read-MtSharedText', 'ConvertTo-MtStartArgs', 'Start-MtProcessToFile',
    'Stop-MtVersionProcesses'
)
