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
        [double]$TimeoutSec = 60
    )

    $r = Invoke-MtProcessFull -FilePath $FilePath -ArgumentList $ArgumentList -TimeoutSec $TimeoutSec
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
        [double]$TimeoutSec = 60
    )

    $fail = [pscustomobject]@{ ExitCode = 1; StdOut = ''; StdErr = ''; TimedOut = $false }

    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $FilePath
    foreach ($a in $ArgumentList) { [void]$psi.ArgumentList.Add([string]$a) }
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

Export-ModuleMember -Function @(
    'ConvertFrom-MtBytes', 'Invoke-MtProcess', 'Invoke-MtProcessFull',
    'Stop-MtProcessTree', 'Get-JavaProcesses', 'Get-GradleDaemonProcesses'
)
