#!/usr/bin/env pwsh
<#
mt_capture.ps1 — 截图采集与「世代」管理。

对应源文件：scripts/test/mt_capture.py（1:1 移植；python 原件保留，迁移期用于逐字节比对）。

不再依赖任何固定截图目录的环境变量配置：每次运行由 Mt.Paths 分配唯一 run id，
截图在采集时登记进该运行的世代清单（<截图目录>/.mt_shots.json）。
断言一律经 Get-MtCurrentShots(run_id) 取图 —— 因此只会识别「当前（最新）生成的截图」，
上一世代的残留证据不会被误当成本次结果。

模式:
  f2       游戏内 F2 截图（经 mt_inject.ps1 注入），落 run/<ver>/screenshots/
  window   窗口级抓取（.NET System.Drawing 的 Graphics.CopyFromScreen），用于窗口/桌面取证

用法:
  mt_capture.ps1 capture -Version 1.21.1 -Tag tc2_enemy -Mode f2
  mt_capture.ps1 list    -Version 1.21.1
  mt_capture.ps1 prune   -Version 1.21.1 -Keep 3

## 退出码（与 python 版一致）
  capture/list 失败分支 = 2；其余 = 0。

## Pillow → .NET 的等价性要点（本机实测）

1. **抓取范围**：python 侧 `ImageGrab.grab()` 不带 bbox ⇒ 抓**主屏整屏**（与窗口无关，
   `_raise_window` 只为把游戏窗口提到最前）。因此本脚本默认也抓整屏，不裁剪窗口 ——
   若改成窗口裁剪，尺寸就不再与 python 版一致了。
   （想要真窗口裁剪时用扩展开关 `-CropToWindow`，它不是 python 的默认行为。）
2. **DPI**：本机 3840x2160 物理屏 / 150% 缩放，pwsh 是 DPI-unaware，
   `GetSystemMetrics`/`GetDeviceCaps(HORZRES)` 只给 2560x1440；而 Pillow 抓出来是
   3840x2160 —— 因为它的 grabscreen_win32 内部调了 `SetThreadDpiAwarenessContext`
   （反查 _imaging.pyd 导入表确认）。所以这里也必须临时把当前线程提到
   PER_MONITOR_AWARE_V2，否则尺寸差 1.5 倍。
3. PNG 编码器不同 ⇒ 不要求与 Pillow 逐字节相同；判据是「可解码 + 尺寸一致」。

## 与 python 版的差异（逐条）

1. **Pillow → System.Drawing**（见上）。
2. 多两个验证用扩展参数（默认不改变任何行为）：`-Hwnd`（显式指定被提窗/被裁剪的窗口）、
   `-CropToWindow`（按 GetWindowRect 裁剪窗口区域）。
3. `capture_f2` 里调的是 `mt_inject.ps1`（python 调 `mt_inject.py`），其余重试/超时/
   报错文案与判断顺序保持一致。
4. 非 Windows 分支（Pillow 缺失 / `sys.platform != "win32"`）不再适用：本仓库已收敛到
   仅 Windows，且 Mt.Win32.psm1 在非 Windows 上根本无法导入。
5. **参数解析不用 `param()`，改用手写 `$args` 循环**（与 mt.ps1 / mt_env.ps1 /
   mt_cleanup.ps1 / mt_stop.ps1 一致）。原因是 `param()` 有两个硬伤：
     · python 的 **kebab 长选项**（`--list-latest` / `--crop-to-window`）在 PS 参数名里
       不合法（不能含连字符）→ 绑定器直接拒绝；而 `paths.sh` 的 `mt_latest_shot` 与
       `mt_case.py` 就是按 `--list-latest` 调的；
     · 未知参数在 `param()` 下是绑定器报错（**exit 1**），本仓约定是
       `MT_ERROR: 未知参数 <x>` + **exit 2**（与 python argparse 一致）。
   归一化规则：去掉前导 `-` 后**删除全部连字符**再小写比较 ⇒ `--list-latest` /
   `-ListLatest` / `--list_latest` 等价，两种调用风格都能吃下。
#>

$ErrorActionPreference = 'Stop'

$script:MtLibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:MtLibDir 'Mt.Phase.psm1') -Force
Import-Module (Join-Path $script:MtLibDir 'Mt.Paths.psm1') -Force
Import-Module (Join-Path $script:MtLibDir 'Mt.Proc.psm1') -Force
Import-Module (Join-Path $script:MtLibDir 'Mt.Win32.psm1') -Force

# ⚠️ 必须第一件事：不设 UTF-8 输出编码时中文会按本机码页(936/GBK)写出，与 python 版不等
Initialize-MtConsole

# ⚠️ 必须在这里（脚本顶层）加载：函数体里的 [System.Drawing.Bitmap] 类型字面量在函数
#    首次被调用时解析，若等到函数内才 Add-Type，解析时机可能早于程序集加载而报错。
Add-Type -AssemblyName System.Drawing

$script:MtPwshExe = Join-Path $PSHOME 'pwsh.exe'
if (-not (Test-Path -LiteralPath $script:MtPwshExe)) { $script:MtPwshExe = 'pwsh' }

# ══ 时间/文件助手 ════════════════════════════════════════════════════════

function ConvertTo-MtUnixSec {
    <#
    .SYNOPSIS
        转成 python `time.time()` / `st_mtime` 口径的 unix 秒（**UTC 纪元秒**）。
    .NOTES
        ⚠️ 不能直接对 Local 的 DateTime 用 ToUnixTimeSeconds()：那样会按本地时区偏移，
        与 mtime 差 8 小时（本机 UTC+8）→ 比 `_newest(since)` 的判据永远不成立。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][datetime]$Time)

    return ([DateTimeOffset]::new($Time)).ToUnixTimeMilliseconds() / 1000.0
}

function Get-MtNewestShot {
    <#
    .SYNOPSIS
        截图目录里 mtime >= since 的最新一张 PNG；没有则返回 $null
        （对应 python _newest）。
    .NOTES
        ⚠️ 不用 `Get-ChildItem -Include '*.png' -Recurse`：在 PS 7 上会参数集冲突。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Dir, [Parameter(Mandatory)][double]$Since)

    if (-not (Test-Path -LiteralPath $Dir -PathType Container)) { return $null }
    $cands = @(Get-ChildItem -LiteralPath $Dir -File |
            Where-Object { $_.Extension -eq '.png' -and (ConvertTo-MtUnixSec $_.LastWriteTime) -ge $Since })
    if ($cands.Count -eq 0) { return $null }
    return ($cands | Sort-Object LastWriteTime -Descending | Select-Object -First 1)
}

# ══ 提窗 ═════════════════════════════════════════════════════════════════

function Invoke-MtRaiseWindow {
    <#
    .SYNOPSIS
        把本版本 dev 客户端窗口提到最前（返回 hwnd，0 = 未找到）。

    .NOTES
        只为「窗口抓取」这条兜底路径服务：抓屏抓的是屏幕像素，窗口被别的窗口盖住就抓不到
        游戏画面。**不做最小化再还原** —— 那会触发 GLFW 的焦点事件与一次 pause/unpause
        抖动，可能打乱 tick 驱动的探针窗口。

        注入走 PostMessage、不依赖前台焦点，因此提窗对 F2/命令注入无副作用；
        且 options.txt 已设 pauseOnLostFocus:false，失焦不会暂停世界。

        Windows 有「前台窗口抢占限制」：后台进程直接 SetForegroundWindow 常被静默忽略，
        表现为「按键注入了但游戏收不到」（F2 截图不落盘）。标准解法两连：
          ① SetWindowPos 到 TOPMOST 再回 NOTOPMOST，强制把窗口抬到最前；
          ② AttachThreadInput 把本线程输入队列挂到当前前台线程上，绕过前台限制。
    #>
    [CmdletBinding()]
    param([string]$Version, [long]$Hwnd = 0)

    $h = $Hwnd
    if (-not $h) { $h = Find-MtMinecraftWindow -Version $Version }
    if (-not $h) { return [long]0 }

    [void](Invoke-MtShowWindow -Hwnd $h -CmdShow 9)   # SW_RESTORE（非 6/SW_MINIMIZE，避免 tick 抖动）
    [void](Set-MtWindowPos -Hwnd $h -InsertAfter -1 -X 0 -Y 0 -Cx 0 -Cy 0 -Flags 3)   # HWND_TOPMOST, NOSIZE|NOMOVE
    [void](Set-MtWindowPos -Hwnd $h -InsertAfter -2 -X 0 -Y 0 -Cx 0 -Cy 0 -Flags 3)   # HWND_NOTOPMOST
    [void](Invoke-MtBringWindowToTop -Hwnd $h)

    $fg = Get-MtForegroundWindow
    $tidFg = Get-MtThreadOfWindow -Hwnd $fg
    $tidSelf = Get-MtCurrentThreadId
    $attached = $false
    if ($tidFg -and $tidFg -ne $tidSelf) {
        $attached = Set-MtAttachThreadInput -FromTid $tidSelf -ToTid $tidFg -Attach $true
    }
    try {
        [void](Set-MtForegroundWindow -Hwnd $h)
    } finally {
        if ($attached) { [void](Set-MtAttachThreadInput -FromTid $tidSelf -ToTid $tidFg -Attach $false) }
    }
    Start-Sleep -Milliseconds 600
    return [long]$h
}

# ══ 采集 ═════════════════════════════════════════════════════════════════

function Invoke-MtCaptureF2 {
    <#
    .SYNOPSIS
        经注入通道按 F2，等游戏落盘后登记世代（对应 python capture_f2）。

    .NOTES
        F2 走的是游戏自身帧缓冲落盘，**不受窗口遮挡影响**，是首选取证方式
        （上一轮改用 F2 失败的真因是：注入 F2 前若打开了 GUI 界面（如物品栏），
        Minecraft 只在 screen == null 时处理 keybind，F2 会被静默吞掉 —— 因此本
        流程的条目里不得再出现「先开界面再截图」的步骤）。

        **多次重试**：前台窗口抢占限制会让 SetForegroundWindow 偶发失效，此时
        PostMessage 注入的 F2 不会进入游戏键绑定；单次失败不能判定为「不可取证」，
        故每轮重试都重新提窗 + 重新注入。全部失败才退回窗口抓取。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][psobject]$Paths,
        [Parameter(Mandatory)][string]$Tag,
        [Parameter(Mandatory)][string]$RunId,
        [double]$TimeoutSec = 10.0,
        [int]$Attempts = 3,
        [long]$Hwnd = 0,
        [bool]$Crop = $false
    )

    $inject = Join-Path $PSScriptRoot 'mt_inject.ps1'
    $lastErr = ''
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        [void](Invoke-MtRaiseWindow -Version $Paths.version -Hwnd $Hwnd)
        $before = ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0) - 1.0
        $r = Invoke-MtProcessFull -FilePath $script:MtPwshExe -TimeoutSec 120 -ArgumentList @(
            '-NoProfile', '-File', $inject, 'key', '-Key', 'f2', '-Version', [string]$Paths.version
        )
        if ($r.ExitCode -ne 0) {
            $lastErr = "F2 注入失败($($r.StdErr.Trim()))"
            continue
        }
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSec)
        while ([DateTime]::UtcNow -lt $deadline) {
            $shot = Get-MtNewestShot -Dir $Paths.shot_dir -Since $before
            if ($shot) {
                Add-MtShot -Paths $Paths -FilePath $shot.FullName -Tag $Tag -RunId $RunId
                Write-MtLine ('MT_CAPTURE: OK — {0} (tag={1}, run={2}, mode=f2, attempt={3})' -f `
                        $shot.Name, $Tag, $RunId, $attempt)
                return 0
            }
            Start-Sleep -Milliseconds 500
        }
        $lastErr = ('{0}s 内无 F2 落盘' -f [int]$TimeoutSec)
    }
    Write-MtErrLine ('MT_CAPTURE: WARN — {0}（已重试 {1} 次，{2}），改用窗口抓取' -f `
            $lastErr, $Attempts, $Paths.shot_dir)
    return (Invoke-MtCaptureWindow -Paths $Paths -Tag $Tag -RunId $RunId -Hwnd $Hwnd -Crop $Crop)
}

function Invoke-MtCaptureWindow {
    <#
    .SYNOPSIS
        窗口级抓取（对应 python capture_window，Pillow → System.Drawing）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][psobject]$Paths,
        [Parameter(Mandatory)][string]$Tag,
        [Parameter(Mandatory)][string]$RunId,
        [long]$Hwnd = 0,
        [bool]$Crop = $false
    )

    $h = Invoke-MtRaiseWindow -Version $Paths.version -Hwnd $Hwnd
    if (-not $h) {
        Write-MtErrLine 'MT_CAPTURE: WARN — 未定位到本版本游戏窗口，抓取的可能是桌面其它内容'
    }
    if (-not (Test-Path -LiteralPath $Paths.shot_dir)) {
        [void](New-Item -ItemType Directory -Force -Path $Paths.shot_dir)
    }
    $dst = Join-Path $Paths.shot_dir ("{0}_{1}.png" -f $Tag, $RunId)

    # ── DPI：与 Pillow 的 grabscreen_win32 同法（见文件头第 2 条）──────────
    $oldCtx = Set-MtThreadDpiAwareness -Context -4
    try {
        if ($Crop -and $h) {
            $rect = Get-MtWindowRect -Hwnd $h
            $srcX = $rect.Left; $srcY = $rect.Top; $w = $rect.Width; $hh = $rect.Height
        } else {
            $size = Get-MtScreenSize
            $srcX = 0; $srcY = 0; $w = $size.Width; $hh = $size.Height
        }
        if ($w -le 0 -or $hh -le 0) { throw "抓取区域为空（${w}x${hh}）" }

        $bmp = [System.Drawing.Bitmap]::new($w, $hh)
        try {
            $g = [System.Drawing.Graphics]::FromImage($bmp)
            try {
                $g.CopyFromScreen($srcX, $srcY, 0, 0, [System.Drawing.Size]::new($w, $hh))
            } finally { $g.Dispose() }
            $bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally { $bmp.Dispose() }
    } catch {
        # python 把平台差异统一归为环境问题，异常文本进 ERROR 行后退出 2
        Write-MtErrLine ('MT_CAPTURE: ERROR — 窗口抓取失败：{0}' -f $_.Exception.Message)
        return 2
    } finally {
        [void](Set-MtThreadDpiAwareness -Context $oldCtx)
    }

    Add-MtShot -Paths $Paths -FilePath $dst -Tag $Tag -RunId $RunId
    Write-MtLine ('MT_CAPTURE: OK — {0} (tag={1}, mode=window)' -f [System.IO.Path]::GetFileName($dst), $Tag)
    return 0
}

# ══ 清单 ═════════════════════════════════════════════════════════════════

function Get-MtShotsTagMap {
    <#
    .SYNOPSIS
        清单里的 文件名 → tag 映射（对应 python `tags = {s["file"]: s.get("tag","")}`）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $data = Get-MtShots -Paths $Paths
    $map = @{}
    if ($data.ContainsKey('shots')) {
        foreach ($s in @($data['shots'])) {
            if ($null -eq $s) { continue }
            $tag = ''
            if ($s.ContainsKey('tag') -and $null -ne $s['tag']) { $tag = [string]$s['tag'] }
            $map[[string]$s['file']] = $tag
        }
    }
    return $map
}

function Invoke-MtCaptureList {
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths, [bool]$ListLatest)

    $runId = Get-MtActiveRunId
    $shots = @(Get-MtCurrentShots -Paths $Paths -RunId $runId)
    if ($ListLatest) {
        if ($shots.Count -eq 0) {
            Write-MtErrLine 'MT_CAPTURE: ERROR — 当前世代无截图'
            return 2
        }
        Write-MtLine ([string]$shots[-1].FullName)
        return 0
    }
    if ($shots.Count -eq 0) {
        Write-MtLine ('MT_CAPTURE: 当前世代（{0}）无截图' -f $runId)
        return 0
    }
    $tags = Get-MtShotsTagMap -Paths $Paths
    foreach ($s in $shots) {
        $t = ''
        if ($tags.ContainsKey($s.Name)) { $t = $tags[$s.Name] }
        Write-MtLine ("$($s.Name)`t$t")
    }
    return 0
}

function Invoke-MtCapturePrune {
    <#
    .SYNOPSIS
        清理历史世代截图，只保留当前世代（对应 python cmd_prune）。
    .NOTES
        ⚠️ python 版**解析了 --keep 却从未使用**（cmd_prune 只认 current_shots(run_id)），
        也就是说 `--keep N` 是死参数：本函数照抄该行为（接受并忽略 -Keep），
        以免迁移期行为分叉。这是 python 版的缺陷，不是本移植的取舍。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths, [int]$Keep = 3)

    $runId = Get-MtActiveRunId
    $keepSet = @{}
    foreach ($s in @(Get-MtCurrentShots -Paths $Paths -RunId $runId)) { $keepSet[$s.FullName] = $true }

    if (-not (Test-Path -LiteralPath $Paths.shot_dir -PathType Container)) {
        Write-MtLine 'MT_CAPTURE: 无可清理内容'
        return 0
    }

    $removed = 0
    foreach ($f in (Get-ChildItem -LiteralPath $Paths.shot_dir -File | Where-Object { $_.Extension -eq '.png' })) {
        if ($keepSet.ContainsKey($f.FullName)) { continue }
        try {
            Remove-Item -LiteralPath $f.FullName -Force -ErrorAction Stop
            $removed++
        } catch {
            # python: except OSError: pass
        }
    }

    # 清单只保留当前世代
    if ($keepSet.Count -gt 0) {
        $data = Get-MtShots -Paths $Paths
        $kept = @()
        if ($data.ContainsKey('shots')) {
            foreach ($s in @($data['shots'])) {
                if ($null -eq $s) { continue }
                if ($keepSet.ContainsKey((Join-Path $Paths.shot_dir ([string]$s['file'])))) {
                    $kept += , ([ordered]@{ file = [string]$s['file']; tag = [string]$s['tag']; ts = $s['ts'] })
                }
            }
        }
        Set-Content -LiteralPath (Get-MtShotsManifest -Paths $Paths) -Encoding utf8NoBOM -NoNewline `
            -Value (ConvertTo-MtJson -InputObject ([ordered]@{ run_id = $runId; shots = $kept }))
    }

    Write-MtLine ('MT_CAPTURE: 已清理 {0} 张历史截图，保留当前世代 {1} 张' -f $removed, $keepSet.Count)
    return 0
}

# ══ 入口：参数解析（本仓入口脚本统一约定：手写 $args 循环，不用 param()）═══

function ConvertTo-MtArgLong {
    <#
    .SYNOPSIS
        解析整数型选项值（非整数时报 `MT_ERROR:` 并退出 2，与 argparse 一致）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Raw, [Parameter(Mandatory)][string]$Name)

    $v = [long]0
    if (-not [long]::TryParse($Raw, [ref]$v)) {
        Write-MtErrorLine "参数 $Name 需要整数，收到 $Raw"
        exit $MT_EXIT_ERROR
    }
    return $v
}

$Command = ''
$Version = ''
$Tag = ''
$Mode = 'f2'
$ListLatest = $false
$Keep = 3
$Hwnd = [long]0
$CropToWindow = $false

$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    if (-not $tok.StartsWith('-')) {
        if (-not $Command) { $Command = $tok; $i++; continue }
        Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
    }
    $optName = $tok.TrimStart('-').ToLowerInvariant().Replace('-', '')
    if ($optName -eq 'version') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
        $Version = [string]$args[$i + 1]; $i += 2
    } elseif ($optName -eq 'tag') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --tag 的值'; exit $MT_EXIT_ERROR }
        $Tag = [string]$args[$i + 1]; $i += 2
    } elseif ($optName -eq 'mode') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --mode 的值'; exit $MT_EXIT_ERROR }
        $Mode = [string]$args[$i + 1]; $i += 2
    } elseif ($optName -eq 'keep') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --keep 的值'; exit $MT_EXIT_ERROR }
        $Keep = [int](ConvertTo-MtArgLong -Raw ([string]$args[$i + 1]) -Name '--keep'); $i += 2
    } elseif ($optName -eq 'hwnd') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --hwnd 的值'; exit $MT_EXIT_ERROR }
        $Hwnd = ConvertTo-MtArgLong -Raw ([string]$args[$i + 1]) -Name '--hwnd'; $i += 2
    } elseif ($optName -eq 'listlatest') {
        $ListLatest = $true; $i++
    } elseif ($optName -eq 'croptowindow') {
        $CropToWindow = $true; $i++
    } else {
        Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
    }
}

$cmdName = if ($null -eq $Command) { '' } else { $Command.Trim() }
switch ($cmdName) {
    'capture' {
        if (-not $Version) { Write-MtErrorLine '缺少必填参数 --version'; exit $MT_EXIT_ERROR }
        if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }
        if (-not $Tag) { Write-MtErrorLine '缺少必填参数 --tag'; exit $MT_EXIT_ERROR }
        if ($Mode -ne 'f2' -and $Mode -ne 'window') {
            Write-MtErrorLine ("非法 --mode 值 {0}（可选：f2 window）" -f $Mode)
            exit $MT_EXIT_ERROR
        }
        $paths = Get-MtPaths -Version $Version
        $runId = Get-MtActiveRunId
        if ($Mode -eq 'f2') {
            $rc = Invoke-MtCaptureF2 -Paths $paths -Tag $Tag -RunId $runId -Hwnd $Hwnd -Crop ([bool]$CropToWindow)
        } else {
            $rc = Invoke-MtCaptureWindow -Paths $paths -Tag $Tag -RunId $runId -Hwnd $Hwnd -Crop ([bool]$CropToWindow)
        }
        exit $rc
    }
    'list' {
        if (-not $Version) { Write-MtErrorLine '缺少必填参数 --version'; exit $MT_EXIT_ERROR }
        if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }
        $rc = Invoke-MtCaptureList -Paths (Get-MtPaths -Version $Version) -ListLatest ([bool]$ListLatest)
        exit $rc
    }
    'prune' {
        if (-not $Version) { Write-MtErrorLine '缺少必填参数 --version'; exit $MT_EXIT_ERROR }
        if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }
        $rc = Invoke-MtCapturePrune -Paths (Get-MtPaths -Version $Version) -Keep $Keep
        exit $rc
    }
    default {
        if (-not $cmdName) {
            Write-MtErrorLine '缺少子命令（可选：capture list prune）'
        } else {
            Write-MtErrorLine ("未知子命令 {0}（可选：capture list prune）" -f $cmdName)
        }
        exit $MT_EXIT_ERROR
    }
}

