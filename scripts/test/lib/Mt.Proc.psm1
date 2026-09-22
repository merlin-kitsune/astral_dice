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

function Get-MtRotatedLatestLogs {
    <#
    .SYNOPSIS
        latest 家族的轮转件（`<yyyy-MM-dd>-<n>.log[.gz]`）里 `LastWriteTime -ge -Since` 的那些，按 mtime **倒序**。

    .NOTES
        **轮转识别口径的唯一实现**：两个消费方共用它，禁止各自再写一套正则/过滤 ——

          · `Read-MtLogWithRotation`（t20）：判据是「**是否存在某行**」（模组清单等启动期事实）；
          · `Read-MtLogWindow`（t22）：判据是「**窗口起点在哪**」（把某用例的日志窗口跨轮转拼回来）。

        两者语义不同（前者是集合成员判定，后者是字节起点重建），但「哪些文件算 latest 家族的轮转件」
        必须同一口径，故枚举收在这里。

        只认 latest 家族（带日期 filePattern），**不碰 debug 家族**：1.20.1 的模组清单读 `debug.log`，
        而它的 filePattern 是 `debug-%i`（**无日期**）⇒ 只在**启动时**轮转、不跨零点日切
        （t20 实测结论），故 debug 家族不存在同类问题，也不需要放宽。

        ⚠️ 调用约定：本函数返回 `, @(…)`（保证「零个候选」也是数组）。**不要把返回值直接塞进 `@( … )`**——
        `@(f)` 会把整个数组当成**单个元素**收进去，得到「元素是数组」的嵌套结构
        （实测：`@(f).Count = 1` 且 `[0]` 是 `Object[]`；`$x = f` 或 `@((f) | Sort-Object …)` 才是平的）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$LogsDir,
        [datetime]$Since = [datetime]::MinValue,
        # 最多取几段（正常跨零点只会切 1 段；留少量余量，避免误把多个历史文件并进来）
        [int]$MaxRotated = 4
    )

    if (-not (Test-Path -LiteralPath $LogsDir -PathType Container)) { return , @() }
    return , @(Get-ChildItem -LiteralPath $LogsDir -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^\d{4}-\d{2}-\d{2}-\d+\.log(\.gz)?$' -and $_.LastWriteTime -ge $Since } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First $MaxRotated)
}

function Read-MtLogWithRotation {
    <#
    .SYNOPSIS
        读「本次会话的日志」= `latest.log` + **同一会话跨零点被 log4j 日切出去的那一段**。

    .NOTES
        ⚠️ 与 t22 的 `Read-MtLogWindow` 的分工（**不要混用**）：本函数是「**是否存在某行**」的取值域，
        把本会话所有轮转件都并进来；`Read-MtLogWindow` 是「**窗口起点在哪**」的字节起点重建，
        只从身份匹配的那一段的某个字节开始拼。断言层需要后者（并进来会前移窗口 ⇒ 跨用例串读）。
        为什么需要（2026-09-18 t20 实测）：`logs/latest.log` 的 log4j 配置带日期 filePattern
        （`logs/%d{yyyy-MM-dd}-%i.log.gz`）⇒ **跨零点时活动文件被日切**：会话启动期写下的
        「已加载模组清单」（`Mod List:` 与 `显示名 版本 (modId)` 行）会整块留在
        `logs/2026-09-17-1.log.gz` 里，而 00:00:01 起新建的 `latest.log` **一条括号清单行都没有**。
        mt_launch 又在启动前删除 `latest.log`（保证只读本轮），故只读 `latest.log` 的判据
        （史莱姆压制硬闸门、Sodium/Iris/KubeJS/优化类模组读数、`Using shaderpack`）在
        **跨零点冷启动**时全部假阴性 —— 实测 23:59:50 启动的会话：Mod List 写在 23:59:53，
        00:00:00 日切，`latest.log` 里查不到 ⇒ 硬闸门 ERROR「未检测到…模组」，重跑即 OK。

        **只补「同一会话被切走的那一段」，绝不回退到别的会话**：候选轮转文件必须满足
        `LastWriteTime -ge -Since`（`-Since` 传本次 launch 的时刻）。依据：mt_launch 在启动前
        会拒绝「全机已存在客户端进程」（TESTING-SPEC §10 第 10 条）⇒ 启动窗口内只有本会话在写
        日志，「日切发生在本次启动之后」等价于「该片段属于本会话」；上一会话的轮转文件其
        `LastWriteTime` 早于 `-Since`，被排除。**这条边界是判据强度的一部分**：若不过滤，
        「上一会话装过该模组、本会话已移除」会被误判成已加载（正是本判据要防的假阳性）。

        只认 **latest 家族的轮转名**（`<yyyy-MM-dd>-<n>.log.gz` / `.log`），不碰 `debug-*.log.gz`：
        1.20.1 的模组清单读 `debug.log`，而它的 filePattern 是 `debug-%i`（**无日期**）⇒ 只在
        启动时轮转、不跨零点日切，故 1.20.1 侧不存在同类假阴性，也不需要放宽。

        读取容错与 `Read-MtSharedText` 一致（`FileShare::ReadWrite` / UTF-8 容错 / 读不到返回 ''）。
        **本函数不判定任何语义**：调用方拿到的仍是原始文本，判据（如带括号 modId 的正则）不变。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$LogsDir,
        [Parameter(Mandatory)][datetime]$Since,
        # 最多补几段轮转（正常跨零点只会切 1 段；留少量余量，避免误把多个历史文件并进来）
        [int]$MaxRotated = 4
    )

    $text = Read-MtSharedText -Path $Path
    # 轮转件枚举收在 Get-MtRotatedLatestLogs（唯一口径，与 Read-MtLogWindow 共用）
    $candidates = Get-MtRotatedLatestLogs -LogsDir $LogsDir -Since $Since -MaxRotated $MaxRotated

    foreach ($c in $candidates) {
        $part = ''
        if ($c.Extension -eq '.gz') {
            $fs = $null; $gz = $null; $sr = $null
            try {
                $fs = [System.IO.File]::Open($c.FullName, [System.IO.FileMode]::Open,
                    [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
                $gz = New-Object System.IO.Compression.GZipStream($fs, [System.IO.Compression.CompressionMode]::Decompress)
                $sr = [System.IO.StreamReader]::new($gz, [System.Text.UTF8Encoding]::new($false, $false))
                $part = $sr.ReadToEnd()
            } catch {
                $part = ''
            } finally {
                if ($null -ne $sr) { $sr.Dispose() } elseif ($null -ne $gz) { $gz.Dispose() } elseif ($null -ne $fs) { $fs.Dispose() }
            }
        } else {
            $part = Read-MtSharedText -Path $c.FullName
        }
        if ($part) { $text += "`n" + $part }
    }
    return $text
}

# ── t22：断言窗口的「跨轮转锚定」────────────────────────────────────────────
# 头部指纹取多少字节当「文件身份」：64 KiB 足以区分「同一个文件被追加」与「换成了另一个文件」，
# 而 latest.log 可能数十 MB ⇒ 只读头部，不在每条用例的快照路径上整读。
$script:MT_ANCHOR_PREFIX_BYTES = 65536
# 创建时间比较容差（ms）：不同文件系统的创建时间粒度不同（FAT 2 s），留余量；
# 真正把关「是不是同一个文件」的是头部指纹，时间只是第一道筛。
$script:MT_ANCHOR_CTIME_TOLERANCE_MS = 2000
# 轮转件 mtime 预筛容差（ms）：日切件的 mtime = 轮转时刻 ≥ 锚点时刻，留时钟粒度余量。
$script:MT_ANCHOR_MTIME_TOLERANCE_MS = 2000

function Get-MtAnchorValue {
    <#
    .SYNOPSIS
        从锚点字典里安全取值（锚点可能来自 JSON 反序列化的 Hashtable / OrderedDictionary）。
    #>
    [CmdletBinding()]
    param($Anchor, [Parameter(Mandatory)][string]$Key)

    if ($null -eq $Anchor) { return $null }
    if ($Anchor -is [System.Collections.IDictionary]) {
        if ($Anchor.Contains($Key)) { return $Anchor[$Key] }
    }
    return $null
}

function Read-MtLogBytesShared {
    <#
    .SYNOPSIS
        按**字节**整读日志文件（游戏进程持有句柄时也能读）；读不到返回 $null。

    .NOTES
        与 `Read-MtSharedText`（字符串版）同一套容错（`FileShare::ReadWrite`），区别是这里**不解码** ——
        断言窗口是按**字节偏移**切的，跨轮转重拼也必须在字节域做，否则多字节字符会错位。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    $fs = $null; $ms = $null
    try {
        $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        $ms = [System.IO.MemoryStream]::new()
        $fs.CopyTo($ms)
        return $ms.ToArray()
    } catch {
        return $null
    } finally {
        if ($null -ne $ms) { $ms.Dispose() }
        if ($null -ne $fs) { $fs.Dispose() }
    }
}

function Read-MtLogBytesHead {
    <#
    .SYNOPSIS
        只读文件头 Count 字节（不足则读多少算多少）；读不到返回空数组。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path, [int]$Count)

    if ($Count -le 0) { return , @() }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return , @() }
    $fs = $null
    try {
        $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        $buf = [byte[]]::new([Math]::Min([long]$Count, $fs.Length))
        $read = 0
        while ($read -lt $buf.Length) {
            $n = $fs.Read($buf, $read, $buf.Length - $read)
            if ($n -le 0) { break }
            $read += $n
        }
        if ($read -eq $buf.Length) { return , $buf }
        $out = [byte[]]::new($read)
        if ($read -gt 0) { [Array]::Copy($buf, $out, $read) }
        return , $out
    } catch {
        return , @()
    } finally {
        if ($null -ne $fs) { $fs.Dispose() }
    }
}

function Read-MtRotatedBytes {
    <#
    .SYNOPSIS
        读一个轮转件的**原始字节**：`.gz` 解压后返回，其它按字节直读；读不到返回 $null。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    if (-not $Path.EndsWith('.gz', [System.StringComparison]::OrdinalIgnoreCase)) {
        return (Read-MtLogBytesShared -Path $Path)
    }
    $fs = $null; $gz = $null; $ms = $null
    try {
        $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        $gz = [System.IO.Compression.GZipStream]::new($fs, [System.IO.Compression.CompressionMode]::Decompress)
        $ms = [System.IO.MemoryStream]::new()
        $gz.CopyTo($ms)
        return $ms.ToArray()
    } catch {
        return $null
    } finally {
        if ($null -ne $ms) { $ms.Dispose() }
        if ($null -ne $gz) { $gz.Dispose() }
        if ($null -ne $fs) { $fs.Dispose() }
    }
}

function Get-MtBytesPrefixHash {
    <#
    .SYNOPSIS
        字节数组头部 Count 字节的 SHA-256（小写 hex）；Count ≤ 0 或空数组返回 ''。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Bytes, [int]$Count)

    if ($null -eq $Bytes -or $Bytes.Length -eq 0) { return '' }
    $n = [int][Math]::Min([long]$Count, [long]$Bytes.Length)
    if ($n -le 0) { return '' }
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([System.BitConverter]::ToString($sha.ComputeHash($Bytes, 0, $n))).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function ConvertFrom-MtLogBytes {
    <#
    .SYNOPSIS
        字节切片 → 文本：UTF-8 容错解码 + **通用换行翻译**（与 mt_assert 既有口径一致）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Bytes, [long]$From = 0)

    if ($null -eq $Bytes -or $Bytes.Length -eq 0) { return '' }
    $from = [Math]::Max([long]0, $From)
    if ($from -ge $Bytes.Length) { return '' }
    $enc = [System.Text.UTF8Encoding]::new($false, $false)
    $txt = $enc.GetString($Bytes, [int]$from, [int]($Bytes.Length - $from))
    return $txt.Replace("`r`n", "`n").Replace("`r", "`n")
}

function Get-MtLogAnchor {
    <#
    .SYNOPSIS
        为一个日志文件建立**窗口锚点**：快照时刻的字节起点 + 该文件的**身份指纹**。

    .NOTES
        为什么需要「身份」而不只是「字节偏移」（2026-09-18 t22 实测/构造复现）：断言窗口原本只记
        「用例开始时的字节偏移」，读取时直接对 `latest.log` 做 `Seek(偏移)`。而 latest.log 的
        log4j filePattern 带日期（`logs/%d{yyyy-MM-dd}-%i.log.gz`）⇒ **跨零点日切**把活动文件更名、
        新建一个更小的 latest.log，于是原偏移越界 ⇒ 旧实现 `if ($Offset -gt $size) { $Offset = 0 }`
        **静默**改成整读新文件：窗口前半段（用例开始 → 零点）整段丢失 ⇒ `log` 假 FAIL、
        `absent` 漏判（假 PASS）。只记偏移时，「偏移越界」既可能是日切、也可能是别的原因，
        没有证据就无法安全重建窗口。

        锚点字段（跨语言契约：`.mt_snapshot.json` 的 `anchors` / `launch_anchors`，键 = 日志文件名）：
          · `len`        —— 快照时刻的文件字节长度（= 窗口起点，与 `offsets` 同值）
          · `ctime_ms`   —— 创建时间（unix ms）：认「还是不是同一个文件」
          · `mtime_ms`   —— 最后写入时间（unix ms）：只做轮转件的**预筛**（日切件 mtime = 轮转时刻 ≥ 它）
          · `prefix_len` / `prefix_sha` —— 头部 64 KiB 的 SHA-256：认「被日切出去的那一段是不是它」

        **只有身份匹配的轮转件才允许并入窗口**（见 `Read-MtLogWindow`）：不匹配时绝不猜、绝不无条件
        并入「所有轮转件」或「整个日志」，故不会串读到上一用例/上一会话的行。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    try {
        $fi = Get-Item -LiteralPath $Path
        $len = [long]$fi.Length
        $ctimeMs = [long]([DateTimeOffset]::new($fi.CreationTimeUtc)).ToUnixTimeMilliseconds()
        $mtimeMs = [long]([DateTimeOffset]::new($fi.LastWriteTimeUtc)).ToUnixTimeMilliseconds()
        $head = Read-MtLogBytesHead -Path $Path -Count $script:MT_ANCHOR_PREFIX_BYTES
        return [ordered]@{
            len        = $len
            ctime_ms   = $ctimeMs
            mtime_ms   = $mtimeMs
            prefix_len = [int]$head.Length
            prefix_sha = (Get-MtBytesPrefixHash -Bytes $head -Count $head.Length)
        }
    } catch {
        return $null
    }
}

function Read-MtLogWindow {
    <#
    .SYNOPSIS
        读「某个断言窗口自锚点起」的日志文本；跨 log4j 零点日切时，把该窗口**跨轮转拼回来**。

    .NOTES
        语义边界（与 t20 的 `Read-MtLogWithRotation` 刻意区分）：

          · `Read-MtLogWithRotation` 回答「**是否存在某行**」（模组清单等启动期事实）⇒ 把本会话
            所有轮转件都并进来，取值域；
          · 本函数回答「**窗口起点在哪**」⇒ 只从**身份匹配的那一个轮转件的某个字节**开始拼，
            起点之前的内容**绝不并入**（并入就等于把窗口前移，制造跨用例串读）。

        判定顺序：
          1. 偏移 ≤ 0 → 整读当前文件（既有语义）；
          2. 无锚点（旧快照）→ 与既有实现逐字节同义（越界则 clamp 到 0）；
          3. 锚点存在且**当前文件就是锚点文件**（创建时间相符 + 头部指纹相符）→ 从偏移处切；
          4. 当前文件不是锚点文件 → 说明锚点文件被替换（日切/被删）：在 latest 家族轮转件里找
             **头部指纹与锚点一致且解压后长度 ≥ 偏移**的那一段 ⇒ 窗口 = 该段[偏移..] + 当前文件；
             更晚的轮转段**不并入**（身份不可验证，并入即等于「所有轮转件都并进来」）；
             找不到 ⇒ **不猜**，退回既有语义（clamp 整读当前文件）并置 `Kind='unreconstructable'`，
             由调用方**显式报出**（不静默）。

        返回 hashtable：`Text` / `Kind`（`missing` | `from-zero` | `legacy` | `intact` | `reassembled` |
        `unreconstructable`）/ `Offset`（实际使用的起点）/ `Rotated`（并入的轮转段数）/ `Note`。
        本函数**不判定任何断言语义**：调用方拿到的仍是原始文本，判据一字不变。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [long]$Offset = 0,
        $Anchor = $null,
        [string]$LogsDir = '',
        # 轮转件上限（正常跨零点只会切 1 段；用例有硬超时，不可能横跨两个零点）
        [int]$MaxRotated = 16
    )

    $cur = Read-MtLogBytesShared -Path $Path
    if ($null -eq $cur) {
        return @{ Text = ''; Kind = 'missing'; Offset = [long]0; Rotated = 0; Note = '' }
    }
    if ($Offset -le 0) {
        return @{ Text = (ConvertFrom-MtLogBytes -Bytes $cur); Kind = 'from-zero'; Offset = [long]0; Rotated = 0; Note = '' }
    }

    $anchorCtime = Get-MtAnchorValue -Anchor $Anchor -Key 'ctime_ms'
    $anchorPfxLen = Get-MtAnchorValue -Anchor $Anchor -Key 'prefix_len'
    $anchorPfxSha = [string](Get-MtAnchorValue -Anchor $Anchor -Key 'prefix_sha')

    $sameFile = $true
    if ($null -ne $anchorCtime) {
        $sameFile = $false
        try {
            $nowCtime = [long]([DateTimeOffset]::new((Get-Item -LiteralPath $Path).CreationTimeUtc)).ToUnixTimeMilliseconds()
            if ([Math]::Abs($nowCtime - [long]$anchorCtime) -le $script:MT_ANCHOR_CTIME_TOLERANCE_MS) {
                $plen = if ($null -ne $anchorPfxLen) { [int]$anchorPfxLen } else { 0 }
                $nowSha = Get-MtBytesPrefixHash -Bytes $cur -Count $plen
                # 头部指纹相符 = 还是那个被追加的文件（头部永不被追加改写）
                $sameFile = ($nowSha -eq $anchorPfxSha)
            }
        } catch {
            $sameFile = $false
        }
    }

    if ($sameFile) {
        $off = $Offset
        if ($off -gt $cur.Length) { $off = [long]0 }   # 原地被截断：与既有实现同义的兜底
        return @{
            Text    = (ConvertFrom-MtLogBytes -Bytes $cur -From $off)
            Kind    = $(if ($null -eq $anchorCtime) { 'legacy' } else { 'intact' })
            Offset  = $off
            Rotated = 0
            Note    = ''
        }
    }

    # ── 锚点文件已被替换：只有**身份匹配**的轮转段才允许并入窗口 ────────────────
    $since = [datetime]::MinValue
    $anchorMtime = Get-MtAnchorValue -Anchor $Anchor -Key 'mtime_ms'
    if ($null -ne $anchorMtime) {
        $since = ([DateTimeOffset]::FromUnixTimeMilliseconds([long]$anchorMtime - $script:MT_ANCHOR_MTIME_TOLERANCE_MS)).UtcDateTime.ToLocalTime()
    }

    $rotated = @()
    if ($LogsDir) {
        # ⚠️ 必须写成 `@((…) | Sort-Object …)`：`@(Get-MtRotatedLatestLogs …)` 会得到嵌套数组（见函数 NOTES）
        $rotated = @((Get-MtRotatedLatestLogs -LogsDir $LogsDir -Since $since -MaxRotated $MaxRotated) |
            Sort-Object LastWriteTime)   # 升序：最靠近锚点的那一段在最前
    }

    $parts = [System.Collections.Generic.List[byte[]]]::new()
    $rotCount = 0
    $matchedIdx = -1
    $bytesCache = @{}
    for ($i = 0; $i -lt $rotated.Count; $i++) {
        $b = Read-MtRotatedBytes -Path $rotated[$i].FullName
        if ($null -eq $b) { continue }
        $bytesCache[$i] = $b
        if ($matchedIdx -ge 0) { continue }
        if ($b.Length -lt $Offset) { continue }
        $plen = if ($null -ne $anchorPfxLen) { [int]$anchorPfxLen } else { 0 }
        if ($b.Length -lt $plen) { continue }
        if ((Get-MtBytesPrefixHash -Bytes $b -Count $plen) -eq $anchorPfxSha) { $matchedIdx = $i }
    }

    if ($matchedIdx -ge 0) {
        $anchorBytes = $bytesCache[$matchedIdx]
        $head = [byte[]]::new($anchorBytes.Length - [int]$Offset)
        [Array]::Copy($anchorBytes, [int]$Offset, $head, 0, $head.Length)
        $parts.Add($head)
        $rotCount = 1
        $parts.Add($cur)

        $ms = [System.IO.MemoryStream]::new()
        try {
            foreach ($p in $parts) { $ms.Write($p, 0, $p.Length) }
            $all = $ms.ToArray()
        } finally { $ms.Dispose() }

        # 比锚点段**更晚**的轮转段不再并入：它们的身份无法用锚点指纹验证，无条件并入就等于
        # 「把所有轮转件并进窗口」（正是本函数要防的串读）。窗口横跨两个零点需要一次 >24h 的
        # 会话，而用例/启动都有硬超时 ⇒ 实际不可能；真出现也只在此处**显式报出**，不静默。
        $later = [Math]::Max(0, $rotated.Count - $matchedIdx - 1)
        $note = "跨零点日切：锚点文件已轮转为 $($rotated[$matchedIdx].Name)，窗口自其 ${Offset}B 处 + 当前文件重拼"
        if ($later -gt 0) {
            $note += "；另有 $later 段更晚的轮转件**未并入**（身份不可验证）"
        }
        return @{
            Text    = (ConvertFrom-MtLogBytes -Bytes $all)
            Kind    = 'reassembled'
            Offset  = $Offset
            Rotated = $rotCount
            Note    = $note
        }
    }

    # 找不到锚点文件（例如 mt_launch 在重登时删除了 latest.log）⇒ 不猜：退回既有语义并显式报出
    $off = $Offset
    if ($off -gt $cur.Length) { $off = [long]0 }
    return @{
        Text    = (ConvertFrom-MtLogBytes -Bytes $cur -From $off)
        Kind    = 'unreconstructable'
        Offset  = $off
        Rotated = 0
        Note    = ("锚点文件已不在（候选轮转段 $($rotated.Count) 个，均无匹配身份）⇒ 窗口退化为当前文件（起点 ${off}B）," +
            '可能是重登/重启删除了 latest.log，或轮转件已被清理')
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
            # ⚠️ **必须新建控制台（-WindowStyle Hidden），不得用 `-NoNewWindow`**（2026-09-17 实测根因）：
            #    「构建只要 4~5 秒，命令却几分钟不返回」的元凶不是构建，而是**进程收尾**——
            #    `-NoNewWindow` 让子进程链（cmd → gradlew → Gradle 守护进程 / 游戏客户端）与父 pwsh
            #    **共用同一个控制台**；长驻子进程一直持有它 ⇒ 父 pwsh 走完 `exit` 后卡在**控制台拆卸**上
            #    （取证：脚本已打印 `MT_BUILD: OK (4s)`、进程 CPU 仅 0.44s，却存活 20+ 分钟，
            #     且其子进程里挂着一个 `conhost.exe`；调用方走 `| Select-Object` 时永远等不到 EOF）。
            #    隐藏窗口只作用于**控制台窗口**本身：日志仍由 `> 文件 2>&1` 落盘（抓取口径不变），
            #    游戏窗口由 GLFW 自建、与这里无关（`mt_launch` 实测进入世界正常）。
            WindowStyle  = 'Hidden'
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
        # 同 `-MergeStderr` 分支：新建（隐藏）控制台，避免长驻子进程与父 pwsh 共用控制台导致收尾阻塞
        WindowStyle            = 'Hidden'
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
             `:<子项目>:run` 选择器。⚠️ 只认 `run` 家族：`:neoforge-1.21.1:build` /
             `:neoforge-26.1.2:build` 之类的
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
    'Read-MtSharedText', 'Read-MtLogWithRotation', 'Get-MtRotatedLatestLogs',
    'Get-MtLogAnchor', 'Read-MtLogWindow', 'ConvertTo-MtStartArgs', 'Start-MtProcessToFile',
    'Stop-MtVersionProcesses', 'Get-MtClientStatus', 'Test-MtClientProcess', 'Test-MtPipelineProcess'
)
