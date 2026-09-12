<#
Compare-MtOutput.ps1 — python 版 vs powershell 版 的等价性比对器。

用途：迁移期（python 原件与 pwsh 移植件并存时）对同一组参数分别执行两侧，
      比较 stdout、stderr 与退出码，并打印首个差异的字节偏移。

## 换行判据（重要，别改成"严格逐字节"）

本仓的**规范换行是 LF**（git 索引 blob 里 0 个 CRLF；`autocrlf=true` 只在检出时把
工作区写成 CRLF）。而 python 在 Windows 上 `print()` / `sys.stdout` 会做文本模式换行
翻译，把 `\n` 写成 `\r\n` —— 那是解释器副产物，不是设计选择（bash 版的 `printf` 输出 LF）。

因此本比对器的判据是：

  1. **PS 侧 stdout 必须不含任何 `CR LF`**（纯 LF）—— 这条是硬断言；
  2. python 侧先把 `CR LF` 归一成 `LF`；
  3. 归一后两侧**逐字节相同**，且退出码相同。

这样既抓住真正的语义差异，又不会把「Python 的 CRLF」误判成移植错误。
需要真·严格逐字节时用 `-RequireExact`（例如 PS vs PS 自比）。

## 为什么不用 `>` 重定向取输出

PowerShell 的重定向会按 $OutputEncoding 重新编码、可能加 BOM 并把 LF 翻成 CRLF，
比较结果就不可信了。这里直接抓子进程的**原始字节**写到文件，再按字节比较。

## 三层判据（按需要选用）

  1. `-RequireExact`：两侧**逐字节**相同（PS vs PS 自比、或已确认无解释器副产物时用）；
  2. 默认：python 侧 CRLF → LF 归一后逐字节相同；
  3. `-MaskPattern`：先对两侧做正则掩码再比（用于**必然不同**的字段——耗时 `(1s)`/`(0s)`、
     PID、run id、时间戳）。掩码后逐字节相同即判 EQUIVALENT，并在输出里列出掩码规则，
     避免"悄悄放过真实差异"。

用法：
    pwsh -NoProfile -File scripts/devtools/Compare-MtOutput.ps1 `
        -PythonArgs @('scripts/verify/verify_chip_recipes.py') `
        -PsArgs     @('scripts/verify/verify_chip_recipes.ps1') `
        -Label      'verify_chip_recipes'

    # bash 侧原件用 -PythonExe 指定解释器
    & scripts/devtools/Compare-MtOutput.ps1 -PythonExe 'C:\Program Files\Git\bin\bash.exe' `
        -PythonArgs @('scripts/test/mt_stop.sh','--all','--keep-daemon') `
        -PsArgs     @('scripts/test/mt_stop.ps1','--all','--keep-daemon') `
        -MaskPattern @('\(\d+m?\d*s\)') -Label 'stop_all_keepdaemon'

退出码：0 = 等价；1 = 有差异；2 = 用法/环境错误。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string[]]$PythonArgs,
    [Parameter(Mandatory)][string[]]$PsArgs,
    [string]$Label = 'compare',
    [string]$PythonExe = 'python',
    [string]$PwshExe = 'pwsh',
    [string]$WorkDir = (Get-Location).Path,
    [string[]]$MaskPattern = @(),
    [switch]$ShowDiff,
    [switch]$RequireExact
)

$ErrorActionPreference = 'Stop'

function Invoke-RawCapture {
    <#
    .SYNOPSIS
        执行命令，把 stdout/stderr 的**原始字节**分别落到文件；返回退出码。
    .PARAMETER ForceUtf8Env
        给子进程注入 PYTHONIOENCODING=utf-8 / PYTHONUTF8=1。
        这是项目自己的做法：mt_ps.py 的 py_env() 就是靠这两个变量让「父进程读子进程输出」
        在任何区域设置下都成立。不给 python 子进程注入的话，它会按本机码页（936/GBK）
        写中文，与 pwsh 侧（UTF-8）比较时会产生**假差异**。
    #>
    param(
        [string]$Exe,
        [string[]]$Argv,
        [string]$OutFile,
        [string]$ErrFile,
        [switch]$ForceUtf8Env
    )

    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $Exe
    foreach ($a in $Argv) { [void]$psi.ArgumentList.Add([string]$a) }
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.WorkingDirectory = $WorkDir
    if ($ForceUtf8Env) {
        $psi.Environment['PYTHONIOENCODING'] = 'utf-8'
        $psi.Environment['PYTHONUTF8'] = '1'
    }

    $p = [System.Diagnostics.Process]::Start($psi)
    $fsOut = [System.IO.File]::Create($OutFile)
    $fsErr = [System.IO.File]::Create($ErrFile)
    try {
        $t1 = $p.StandardOutput.BaseStream.CopyToAsync($fsOut)
        $t2 = $p.StandardError.BaseStream.CopyToAsync($fsErr)
        $p.WaitForExit()
        $t1.Wait(); $t2.Wait()
    } finally {
        $fsOut.Dispose(); $fsErr.Dispose()
    }
    return $p.ExitCode
}

function Get-CrlfCount {
    param([byte[]]$Bytes)
    $n = 0
    for ($i = 0; $i -lt $Bytes.Length - 1; $i++) {
        if ($Bytes[$i] -eq 13 -and $Bytes[$i + 1] -eq 10) { $n++ }
    }
    return $n
}

function ConvertTo-LfBytes {
    <#
    .SYNOPSIS
        把字节序列里的 CR LF 归一成 LF（只动 CRLF，不动孤立的 CR）。
    #>
    param([byte[]]$Bytes)

    $ms = [System.IO.MemoryStream]::new()
    for ($i = 0; $i -lt $Bytes.Length; $i++) {
        if ($Bytes[$i] -eq 13 -and ($i + 1) -lt $Bytes.Length -and $Bytes[$i + 1] -eq 10) { continue }
        $ms.WriteByte($Bytes[$i])
    }
    return $ms.ToArray()
}

function Get-FirstDiffOffset {
    param([byte[]]$A, [byte[]]$B)
    $n = [Math]::Min($A.Length, $B.Length)
    for ($i = 0; $i -lt $n; $i++) { if ($A[$i] -ne $B[$i]) { return $i } }
    if ($A.Length -ne $B.Length) { return $n }
    return -1
}

function Mask-Bytes {
    <#
    .SYNOPSIS
        按正则掩码字节序列（先 UTF-8 容错解码 → 逐条 -replace → 再编码）。
    .NOTES
        只用于**必然不同**的字段（耗时/PID/run id）。掩码规则会在输出里列出，
        不允许"静默放过"。
    #>
    param([byte[]]$Bytes, [string[]]$Patterns)

    $dec = [System.Text.UTF8Encoding]::new($false, $false)
    $text = $dec.GetString($Bytes)
    foreach ($p in $Patterns) { $text = [regex]::Replace($text, $p, '<MASK>') }
    return $dec.GetBytes($text)
}

$tmp = Join-Path $WorkDir 'temp'
if (-not (Test-Path -LiteralPath $tmp)) { [void](New-Item -ItemType Directory -Force -Path $tmp) }
$tag = ($Label -replace '[^\w\.\-]', '_')
$pyOut = Join-Path $tmp "$tag.py.out"
$pyErr = Join-Path $tmp "$tag.py.err"
$psOut = Join-Path $tmp "$tag.ps.out"
$psErr = Join-Path $tmp "$tag.ps.err"

$pyRc = Invoke-RawCapture -Exe $PythonExe -Argv $PythonArgs -OutFile $pyOut -ErrFile $pyErr -ForceUtf8Env
$psRc = Invoke-RawCapture -Exe $PwshExe -Argv (@('-NoProfile', '-File') + $PsArgs) -OutFile $psOut -ErrFile $psErr

$raw1 = [System.IO.File]::ReadAllBytes($pyOut)
$raw2 = [System.IO.File]::ReadAllBytes($psOut)
$pyCrlf = Get-CrlfCount $raw1
$psCrlf = Get-CrlfCount $raw2

if ($RequireExact) {
    $n1 = $raw1
    $lfRule = 'exact (CRLF 不归一)'
} else {
    $n1 = ConvertTo-LfBytes $raw1
    $lfRule = 'LF 归一'
}
$off = Get-FirstDiffOffset -A $n1 -B $raw2

# PS 侧必须纯 LF（在掩码之前判定 —— 掩码会重编码，不能用来掩盖 CRLF）
$psPureLf = ($psCrlf -eq 0)
$ok = ($off -eq -1) -and ($pyRc -eq $psRc) -and $psPureLf
if ($ok -and $MaskPattern.Count -gt 0) {
    $m1 = Mask-Bytes -Bytes $n1 -Patterns $MaskPattern
    $m2 = Mask-Bytes -Bytes $raw2 -Patterns $MaskPattern
    $off = Get-FirstDiffOffset -A $m1 -B $m2
    $ok = ($off -eq -1)
}
$verdict = if ($ok) { 'EQUIVALENT' } else { 'DIFF' }

[Console]::Out.Write("COMPARE $Label : $verdict`n")
[Console]::Out.Write(("  mode   {0}{1}`n" -f $lfRule, $(if ($MaskPattern.Count -gt 0) { ' + 掩码 ' + ($MaskPattern -join ' | ') } else { '' })))
[Console]::Out.Write(("  exit   python={0}  pwsh={1}`n" -f $pyRc, $psRc))
[Console]::Out.Write(("  stdout python={0}B (CRLF={1})  pwsh={2}B (CRLF={3})  firstDiff={4}`n" -f `
            $raw1.Length, $pyCrlf, $raw2.Length, $psCrlf, $off))
if (-not $psPureLf) {
    [Console]::Out.Write("  !! pwsh 侧含 CRLF —— 违反「PS 输出纯 LF」硬断言`n")
}

if (-not $ok -and $ShowDiff) {
    $show = [Math]::Max(0, ($off - 200))
    if ($show -lt 0) { $show = 0 }
    $len = [Math]::Min(400, [Math]::Max($n1.Length, $raw2.Length) - $show)
    if ($len -gt 0) {
        $enc = [System.Text.UTF8Encoding]::new($false, $false)
        $s1 = if ($n1.Length -gt $show) { $enc.GetString($n1, $show, [Math]::Min($len, $n1.Length - $show)) } else { '' }
        $s2 = if ($raw2.Length -gt $show) { $enc.GetString($raw2, $show, [Math]::Min($len, $raw2.Length - $show)) } else { '' }
        [Console]::Out.Write("  --- python@${show} ---`n$s1`n  --- pwsh@${show} ---`n$s2`n")
    }
    $e1 = [System.IO.File]::ReadAllBytes($pyErr)
    $e2 = [System.IO.File]::ReadAllBytes($psErr)
    [Console]::Out.Write(("  stderr python={0}B  pwsh={1}B`n" -f $e1.Length, $e2.Length))
}

if ($ok) { exit 0 }
exit 1
