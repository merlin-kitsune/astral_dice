#Requires -Version 7.0
<#
.SYNOPSIS
    Mt.Channel — 测试通道抽象（autojs / native）。

.DESCRIPTION
    把「往游戏里下发一条命令 / 一次按键 / 一次点击 / 一张截图」从唯一的 Win32 注入路径
    （mt_inject.ps1 / mt_capture.ps1）里拆出来，改成两条可切换的通道：
      · autojs — 走 AutoJS 端点（lib/Mt.AutoJs.psm1）的 `eval_js`；不需要前台窗口、不需要
                 键盘布局、不占用鼠标，可与人工操作共存。
      · native — 走既有 mt_inject.ps1 / mt_capture.ps1（user32 扫描码 / PostMessage / GDI 抓窗）。

    **本模块在 native 通道下不驱动任何注入**：native 的语义是「保持改造前原路径」，由调用方
    （mt_case / mt_launch）直接调 mt_inject.ps1 / mt_capture.ps1，从而行为与改造前逐字节一致。
    模块里 send* 函数在 native 模式下一律返回 Status='DELEGATED' / Channel='native'，只作为
    「本 op 该由 legacy 路径执行」的显式信号，绝不会被误读成成功。

    契约（唯一事实源）：docs/features/windows-channel-decoupling-spec.md §1。

.NOTES
    ① **只导出 7 个名字**（契约 §1 的清单）：Get-MtChannelMode / Test-MtChannelAutoJs /
       Send-MtChannelCommand / Send-MtChannelKey / Send-MtChannelMouse /
       Invoke-MtChannelShot / Get-MtChannelPlan。
    ② 返回值统一为 [pscustomobject] @{Status; Channel; Detail; Raw}：
         Status  = 'OK' | 'DELEGATED' | 'ERROR'
         Channel = 'autojs' | 'native' | 'delegated'
         Detail  = 单行文本（**绝不含换行**，可直接拼进机器行）
         Raw     = 底层结果（AutoJS 的 hashtable / $null）
       `Status='DELEGATED'` 的语义是「该 op 在 autojs 通道下**无法等价表达**」：**不是失败**、
       不计入用例失败、也不写**假的**取证文件（§1.1b）；只追加委派清单
       run/<version>/.mt_delegated.json，并在机器行里可见。
    ③ 机器行（§1.3）只由本模块写，形状固定（不新增第四种）：
         MT_CHANNEL: mode=autojs|native
         MT_CHANNEL: op=<command|key|mouse|shot|reload|probe> channel=… status=… detail=…
         MT_CHANNEL: delegated_count=<n>      ← 由调用方（mt_case）按用例/整轮计数后写出
       `op=probe` 是本批唯一在 §1.3 枚举之外追加的值（诊断项，仅由 Test-MtChannelAutoJs 写出），
       它只出现在**新增**的 MT_CHANNEL 家族里，不触碰任何既有机器行的取值域。
    ④ 输出纪律：**不用** Write-Output / Write-Host（会污染调用方管道），一律
       `[Console]::Out.Write($Text + "`n")`，与 Mt.Phase.psm1 的 Write-MtLine 同口径。
    ⑤ 依赖模块一律**不带 -Force** 导入：-Force 会「先卸载再重载」，把调用方已导入的函数摘掉
       （mt_case.ps1:95 的注释是同一个坑）。
    ⑥ 探测链缓存按**版本**键存（同一进程内可能混跑多条线），首个成功的策略名记在
       $script:ProbeCache，后续同版本命令直接优先用它（§1.1b「首个成功者被记住」）。
#>

$ErrorActionPreference = 'Stop'

# 不带 -Force：见 .NOTES ⑤
Import-Module (Join-Path $PSScriptRoot 'Mt.Paths.psm1')
Import-Module (Join-Path $PSScriptRoot 'Mt.AutoJs.psm1')

$script:DefaultChannel = 'autojs'
$script:ValidChannels = @('autojs', 'native')
$script:DelegatedFileName = '.mt_delegated.json'
$script:ModeLineEmitted = $false
$script:ModeLineValue = ''
$script:ResolvedMode = ''
$script:ProbeCache = @{}
$script:DelegatedCount = 0
$script:TagUtf8 = [System.Text.UTF8Encoding]::new($false, $false)

# lclick / attack 的「准星命中面」：命中实体 ⇒ attack，命中方块 ⇒ mine，其它 ⇒ NOHIT（委派）。
# 用字面量 `NOHIT` 而不是异常回传：这是「没有可等价的目标」，不是通道故障。
$script:JsAttackOrMine = '(function(){ var h = null; try { h = Client.hitResult; } catch (e0) { return "NOHIT"; } if (h === null || h === undefined) { return "NOHIT"; } var t = String(h.getType()); if (t.indexOf("ENTITY") >= 0) { var e = h.getEntity(); if (e === null || e === undefined) { return "NOHIT"; } AutoJS.attack(e); return "attack"; } if (t.indexOf("BLOCK") >= 0) { AutoJS.mine(h.getBlockPos()); return "mine"; } return "NOHIT"; })()'
$script:JsClearScreen = '(function(){ Client.setScreen(null); return "esc"; })()'

# ── 输出 / 结果构造 ─────────────────────────────────────────────────────
function Write-MtChannelLine {
    <#
    .SYNOPSIS
        写一行 stdout（显式 LF，不用 Write-Output / Write-Host —— 见 .NOTES ④）。
    #>
    [CmdletBinding()]
    param([AllowEmptyString()][string]$Text = '')

    [Console]::Out.Write([string]$Text + [string][char]10)
}

function ConvertTo-MtChannelOneLine {
    <#
    .SYNOPSIS
        把任意文本压成单行（机器行 detail 里**不允许**换行，见 §1.3）。
    #>
    [CmdletBinding()]
    param([AllowNull()][AllowEmptyString()][string]$Text = '', [int]$MaxLen = 300)

    $s = [string]$Text
    if (-not $s) { return '' }
    $s = ($s -replace "\r?\n", ' ').Trim()
    if ($s.Length -gt $MaxLen) { $s = $s.Substring(0, $MaxLen) + '…' }
    return $s
}

function Write-MtChannelOp {
    <#
    .SYNOPSIS
        契约 §1.3 的 op 机器行：`MT_CHANNEL: op=… channel=… status=… detail=…`。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Op,
        [Parameter(Mandatory)][string]$Channel,
        [Parameter(Mandatory)][string]$Status,
        [AllowEmptyString()][string]$Detail = ''
    )

    $d = ConvertTo-MtChannelOneLine -Text $Detail
    Write-MtChannelLine (('MT_CHANNEL: op={0} channel={1} status={2} detail={3}' -f $Op, $Channel, $Status, $d))
}

function New-MtChannelResult {
    <#
    .SYNOPSIS
        统一返回对象（契约 §1 的 Status / Channel / Detail / Raw）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][ValidateSet('OK', 'DELEGATED', 'ERROR')][string]$Status,
        [Parameter(Mandatory)][ValidateSet('autojs', 'native', 'delegated')][string]$Channel,
        [AllowEmptyString()][string]$Detail = '',
        [AllowNull()]$Raw = $null
    )

    return [pscustomobject][ordered]@{
        Status  = $Status
        Channel = $Channel
        Detail  = (ConvertTo-MtChannelOneLine -Text $Detail)
        Raw     = $Raw
    }
}

function Get-MtChannelField {
    <#
    .SYNOPSIS
        从 hashtable / pscustomobject 里取字段（AutoJS 的返回是 hashtable，PascalCase 键）。
    #>
    [CmdletBinding()]
    param([AllowNull()]$Map, [Parameter(Mandatory)][string]$Key, [AllowNull()]$Default = $null)

    if ($null -eq $Map) { return $Default }
    if ($Map -is [System.Collections.IDictionary]) {
        if ($Map.Contains($Key)) { return $Map[$Key] }
        return $Default
    }
    $prop = $Map.PSObject.Properties[$Key]
    if ($null -ne $prop) { return $prop.Value }
    return $Default
}

function ConvertTo-MtChannelJsLiteral {
    <#
    .SYNOPSIS
        把任意字符串转成**合法的 JS 字符串字面量**（JSON 的转义规则与 JS 一致：`"` / `\` /
        控制字符 / 非 ASCII 都会正确转义），从而避免字符串拼接注入（契约 §1.1）。
    #>
    [CmdletBinding()]
    param([AllowNull()][AllowEmptyString()][string]$Value = '')

    $json = ConvertTo-Json -InputObject ([string]$Value) -Compress
    if (-not $json) { return '""' }
    return [string]$json
}

function Test-MtChannelTransportError {
    <#
    .SYNOPSIS
        判定一次失败是否属「通道本身（端点）不可用」——调用方据此记 BLOCKED 而不是 ERROR。
    #>
    [CmdletBinding()]
    param([AllowNull()][AllowEmptyString()][string]$Text = '')

    if (-not $Text) { return $false }
    return [bool]($Text -match '(?i)传输层|initialize 失败|HTTP \d|connect|拒绝|连不上|refused|timed out|超时')
}

function Test-MtChannelDiagClean {
    <#
    .SYNOPSIS
        eval_js 的 diagnostics 是否「没有错误证据」（契约 §1.1b：ok:true 不代表没出错）。

    .NOTES
        diagnostics 里既有可能是错误信息，也可能是引擎附带的普通元数据。判定刻意**只认错误
        证据**（键名像 error/exception/stack，或值里出现异常类名 / 栈帧），否则每一条命令都会
        被误判成失败并静默降级成 DELEGATED —— 那比漏判更危险。
    #>
    [CmdletBinding()]
    param([AllowNull()]$Diagnostics = $null)

    if ($null -eq $Diagnostics) { return $true }
    $items = @()
    if ($Diagnostics -is [System.Collections.IDictionary]) {
        foreach ($k in @($Diagnostics.Keys)) { $items += , @([string]$k, $Diagnostics[$k]) }
    } elseif ($Diagnostics -is [string]) {
        if (-not ([string]$Diagnostics).Trim()) { return $true }
        $items += , @('diagnostics', [string]$Diagnostics)
    } else {
        foreach ($pr in @($Diagnostics.PSObject.Properties)) { $items += , @([string]$pr.Name, $pr.Value) }
    }
    foreach ($it in $items) {
        $name = [string]$it[0]
        $val = $it[1]
        if ($null -eq $val) { continue }
        if ($val -is [bool]) { if (-not $val) { continue } }
        $s = [string]$val
        if (-not $s.Trim()) { continue }
        if ($name -match '(?i)error|exception|throwable|stack|fail') { return $false }
        if ($s -match '(?i)exception|error|at [a-z_$][\w$\.]*\(') { return $false }
    }
    return $true
}

function Test-MtChannelEvalOk {
    <#
    .SYNOPSIS
        eval_js 结果是否「真的成功」：Ok 为真 且 diagnostics 无错误证据。
    #>
    [CmdletBinding()]
    param([AllowNull()]$Result = $null)

    if ($null -eq $Result) { return $false }
    if (-not [bool](Get-MtChannelField -Map $Result -Key 'Ok' -Default $false)) { return $false }
    return (Test-MtChannelDiagClean -Diagnostics (Get-MtChannelField -Map $Result -Key 'Diagnostics'))
}

function Test-MtChannelFabricAutoJsInjected {
    <#
    .SYNOPSIS
        fabric-1.20.1 专用：读 run/<side>/.mt_autojs_injected，判断**本次启动**是否真的注入了 AutoJS。

    .NOTES
        为什么不能只看 mods/：fabric 的 AutoJS 是条件依赖，由 -PautojsTestMod=1 以 modRuntimeOnly 注入
        运行期 classpath，永不在 mods/（制品是 intermediary 命名空间，手放进 mods 会 NoSuchMethodError；
        见 scripts/test/TESTING-RULES-OVERVIEW.md:191 规则 6）。
        状态文件由 scripts/test/win/launch.sh 真启动前写、scripts/test/win/stop.sh 收停时删 ⇒
        它只在「launch.sh 起的那局游戏还活着」期间存在。
        文件缺失 / 非 fabric / autojs=0 ⇒ 一律 $false（fail-closed：退回 marker 兜底，绝不假装通道可用）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [AllowEmptyString()][string]$Side = ''
    )

    try {
        if ($Version -ne (Get-MtFabricVersion)) { return $false }
        $paths = Get-MtPaths -Version $Version -Side $Side
    } catch {
        return $false
    }
    $stateFile = Join-Path ([string]$paths.run_dir) '.mt_autojs_injected'
    if (-not (Test-Path -LiteralPath $stateFile -PathType Leaf)) { return $false }
    try {
        $text = [string](Get-Content -LiteralPath $stateFile -Raw -ErrorAction Stop)
    } catch {
        return $false
    }
    return ($text -match '(?m)^autojs\s*=\s*1\s*$')
}

function Test-MtChannelAutoJsDeployed {
    <#
    .SYNOPSIS
        本线 run/<ver>/mods 里是否有 autojs jar，**或** fabric 线本次启动是否已注入 AutoJS。
        不再按版本白名单（2026-11-01 用户裁决：1.21.1 / 1.20.1 / 26.1.2 / fabric-1.20.1 四线均可）。

    .NOTES
        2026-11-02：fabric-1.20.1 的 jar 永不进 mods/（条件依赖注入运行期 classpath），
        仅按 mods/ 判定会让它恒为 DELEGATED —— 先看 jar，再看 fabric 的注入状态文件。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [AllowEmptyString()][string]$Side = ''
    )

    try {
        $paths = Get-MtPaths -Version $Version -Side $Side
    } catch {
        return $false
    }
    $modsDir = [string]$paths.mods_dir
    if ($modsDir -and (Test-Path -LiteralPath $modsDir -PathType Container)) {
        $jarCount = @(Get-ChildItem -LiteralPath $modsDir -Filter 'AutoJS-*.jar' -File -ErrorAction SilentlyContinue).Count
        if ($jarCount -gt 0) { return $true }
    }
    return (Test-MtChannelFabricAutoJsInjected -Version $Version -Side $Side)
}

function Add-MtChannelDelegation {
    <#
    .SYNOPSIS
        追加一条委派记录到 run/<version>/.mt_delegated.json（契约 §1.3 的清单）。

    .NOTES
        写盘失败**不得改变判定**：清单是证据，不是判据（返回 $false，由调用方在 Detail 里注明）。
        字段名随 op 变化：key / button / tag / target（契约 §1.3）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][string]$Op,
        [AllowEmptyString()][string]$Target = '',
        [AllowEmptyString()][string]$Reason = ''
    )

    $script:DelegatedCount++
    try {
        $paths = Get-MtPaths -Version $Version
        $path = Join-Path ([string]$paths.run_dir) $script:DelegatedFileName
        $dir = [System.IO.Path]::GetDirectoryName($path)
        if ($dir -and -not (Test-Path -LiteralPath $dir -PathType Container)) {
            [void](New-Item -ItemType Directory -Force -Path $dir)
        }
        $arr = @()
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            try {
                $txt = [System.IO.File]::ReadAllText($path, $script:TagUtf8)
                if ($txt.Trim()) {
                    $parsed = ConvertFrom-Json -InputObject $txt
                    $arr = @($parsed)
                }
            } catch { $arr = @() }
        }
        $field = switch ($Op) {
            'key' { 'key' }
            'mouse' { 'button' }
            'shot' { 'tag' }
            default { 'target' }
        }
        $entry = [ordered]@{ op = $Op }
        $entry[$field] = [string]$Target
        $entry['reason'] = (ConvertTo-MtChannelOneLine -Text $Reason -MaxLen 200)
        $entry['ts'] = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0
        $arr = @($arr) + , $entry
        [System.IO.File]::WriteAllText($path, (ConvertTo-MtJson -InputObject $arr), $script:TagUtf8)
        return $true
    } catch {
        return $false
    }
}

function New-MtChannelNativeDelegate {
    <#
    .SYNOPSIS
        native 通道的显式信号：本 op 由调用方走 legacy 路径（mt_inject / mt_capture），模块不驱动
        注入（契约 §1.2「native 行为逐字节一致」）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Op, [AllowEmptyString()][string]$Reason = '')

    $d = ConvertTo-MtChannelOneLine -Text $Reason
    Write-MtChannelOp -Op $Op -Channel 'native' -Status 'delegated' -Detail $d
    return (New-MtChannelResult -Status 'DELEGATED' -Channel 'native' -Detail $d)
}

function New-MtChannelDelegatedResult {
    <#
    .SYNOPSIS
        autojs 通道下无法等价表达 / 端点侧拿不到目标：记委派清单 + 返回 DELEGATED。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][string]$Op,
        [AllowEmptyString()][string]$Target = '',
        [AllowEmptyString()][string]$Reason = '',
        [AllowNull()]$Raw = $null
    )

    $wrote = Add-MtChannelDelegation -Version $Version -Op $Op -Target $Target -Reason $Reason
    $suffix = if ($wrote) { '' } else { '（委派清单写入失败：判定不变）' }
    $d = ('{0} — {1}{2}' -f (ConvertTo-MtChannelOneLine -Text $Target -MaxLen 80), $Reason, $suffix)
    Write-MtChannelOp -Op $Op -Channel 'delegated' -Status 'delegated' -Detail $d
    return (New-MtChannelResult -Status 'DELEGATED' -Channel 'delegated' -Detail $d -Raw $Raw)
}

# ── 模式解析（契约 §1）──────────────────────────────────────────────────
function Get-MtChannelMode {
    <#
    .SYNOPSIS
        解析当前测试通道：显式参数 > 环境变量 MT_CHANNEL > 默认 autojs。非法值直接 throw。

    .NOTES
        解析结果与 `MT_CHANNEL: mode=…` 机器行都**每进程只产出一次**，无论被调用多少次。
    #>
    [CmdletBinding()]
    param([AllowEmptyString()][string]$Requested = '')

    # ⚠️ 缓存只用于**省掉重复的机器行**，不能让它吞掉调用方显式传入的值：
    #    同一进程里先 Get-MtChannelMode（默认 autojs）再 Get-MtChannelMode -Requested native，
    #    如果直接返回缓存，调用方会以为自己拿到的是 native —— 那是静默错配，比多打一行糟得多。
    $raw = ''
    if ($Requested -and $Requested.Trim()) {
        $raw = $Requested.Trim()
    } elseif ($env:MT_CHANNEL -and ([string]$env:MT_CHANNEL -match '\S')) {
        $raw = ([string]$env:MT_CHANNEL).Trim()
    } else {
        $raw = $script:DefaultChannel
    }

    $mode = $raw.ToLowerInvariant()
    if ($script:ValidChannels -notcontains $mode) {
        throw ("MT_CHANNEL 取值非法：'{0}'（只接受 autojs / native）" -f $raw)
    }

    $script:ResolvedMode = $mode
    # 机器行只在「本进程第一次解析」或「解析结果变了」时写，保证一次运行里 `mode=` 唯一。
    if ((-not $script:ModeLineEmitted) -or ($script:ModeLineValue -ne $mode)) {
        Write-MtChannelLine ('MT_CHANNEL: mode=' + $mode)
        $script:ModeLineEmitted = $true
        $script:ModeLineValue = $mode
    }
    return $mode
}

function Test-MtChannelAutoJs {
    <#
    .SYNOPSIS
        autojs 通道是否可用（端点 /health + MCP initialize 是否成功）。

    .NOTES
        · native 模式：不探测端点（native 不需要），直接返回 $true 并留一行说明；
        · 本线缺 autojs jar（run/<ver>/mods 里没有 AutoJS-*.jar）：返回 $false（实例层面不可用，不是端点问题）；
        · 端点没起来：返回 $false —— 由调用方决定记 BLOCKED 还是 WARN。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version, [int]$TimeoutMs = 0)

    $mode = Get-MtChannelMode
    if ($mode -eq 'native') {
        Write-MtChannelOp -Op 'probe' -Channel 'native' -Status 'ok' -Detail 'mode=native：不探测 AutoJS 端点'
        return $true
    }

    if (-not (Test-MtChannelAutoJsDeployed -Version $Version)) {
        Write-MtChannelOp -Op 'probe' -Channel 'delegated' -Status 'delegated' `
            -Detail ('{0} 缺 autojs jar（run/<版本>/mods 里没有 AutoJS-*.jar）' -f $Version)
        return $false
    }

    if (-not (Get-Command -Name 'Test-MtAutoJsEndpoint' -ErrorAction SilentlyContinue)) {
        Write-MtChannelOp -Op 'probe' -Channel 'autojs' -Status 'error' -Detail 'Mt.AutoJs.psm1 未加载 endpoint=unavailable'
        return $false
    }

    $params = @{}
    if ($TimeoutMs -gt 0) { $params['TimeoutMs'] = $TimeoutMs }
    $probe = Test-MtAutoJsEndpoint @params
    $ep = ''
    try { $ep = ('{0}:{1}' -f [string]$probe.Host, [string]$probe.Port) } catch { $ep = '' }
    if ([bool]$probe.Ok) {
        Write-MtChannelOp -Op 'probe' -Channel 'autojs' -Status 'ok' `
            -Detail ('endpoint={0} attempts={1} protocol={2}' -f $ep, [string]$probe.Attempts, [string]$probe.ProtocolVersion)
        return $true
    }
    Write-MtChannelOp -Op 'probe' -Channel 'autojs' -Status 'error' `
        -Detail ('endpoint={0} reason={1} attempts={2} endpoint=unavailable' -f $ep, [string]$probe.Reason, [string]$probe.Attempts)
    return $false
}

# ── 命令下发（三级探测链，契约 §1.1 / §1.1b）─────────────────────────────
function Invoke-MtChannelProbeChain {
    <#
    .SYNOPSIS
        按 Plan 顺序逐条 eval，首个成功者被记住（$script:ProbeCache[版本] = 策略名）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Plan,
        [int]$TimeoutMs = 0
    )

    $ordered = @($Plan)
    if ($script:ProbeCache.ContainsKey($Version)) {
        $cached = [string]$script:ProbeCache[$Version]
        $hit = @($Plan | Where-Object { [string]$_['Name'] -eq $cached })
        if ($hit.Count -gt 0) {
            $ordered = @($hit[0]) + @($Plan | Where-Object { [string]$_['Name'] -ne $cached })
        }
    }

    $lastErr = ''
    foreach ($cand in $ordered) {
        $name = [string]$cand['Name']
        $side = [string]$cand['Side']
        $code = [string]$cand['Code']
        $r = Invoke-MtAutoJsEval -Code $code -Side $side -Mode 'Raw' -TimeoutMs $TimeoutMs
        if (Test-MtChannelEvalOk -Result $r) {
            $script:ProbeCache[$Version] = $name
            return [pscustomobject][ordered]@{ Ok = $true; Via = $name; Raw = $r; Error = '' }
        }
        $err = [string](Get-MtChannelField -Map $r -Key 'Error' -Default '')
        if (-not $err) { $err = 'eval 未成功（diagnostics 含错误证据）' }
        $lastErr = ('{0}: {1}' -f $name, (ConvertTo-MtChannelOneLine -Text $err -MaxLen 120))
    }
    return [pscustomobject][ordered]@{ Ok = $false; Via = ''; Raw = $null; Error = $lastErr }
}

function Send-MtChannelCommand {
    <#
    .SYNOPSIS
        下发一条命令（带不带前导斜杠都可）。契约 §1.1：server 主路径 + 三级探测链。

    .NOTES
        三级（首个成功者被记住）：
          1) AutoJS.command("<cmd>")                                            （服务端身份）
          2) server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "<cmd>")
          3) Client.getConnection().sendCommand("<cmd>")                        （客户端发包）
        三级全失败 ⇒ Status DELEGATED（契约 §1.1b 明文规定，不是 ERROR）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][AllowEmptyString()][string]$Command = '',
        [ValidateSet('server', 'client')][string]$Side = 'server',
        [int]$TimeoutMs = 0
    )

    $mode = Get-MtChannelMode
    if ($mode -eq 'native') {
        return (New-MtChannelNativeDelegate -Op 'command' -Reason 'mode=native：命令由 mt_inject.ps1 下发（legacy 路径）')
    }

    $cmdText = [string]$Command
    if (-not $cmdText.Trim()) {
        Write-MtChannelOp -Op 'command' -Channel 'autojs' -Status 'error' -Detail 'command_empty'
        return (New-MtChannelResult -Status 'ERROR' -Channel 'autojs' -Detail 'command_empty：命令为空')
    }

    if (-not (Test-MtChannelAutoJsDeployed -Version $Version)) {
        return (New-MtChannelDelegatedResult -Version $Version -Op 'command' -Target $cmdText `
            -Reason ('{0} 缺 autojs jar（run/<版本>/mods 里没有 AutoJS-*.jar），命令通道不可用' -f $Version))
    }

    $lit = ConvertTo-MtChannelJsLiteral -Value $cmdText
    $plan = [System.Collections.Generic.List[object]]::new()
    $codeSend = '(function(){ Client.getConnection().sendCommand(' + $lit + '); return "send_command"; })()'
    if ($Side -eq 'server') {
        $plan.Add(@{ Name = 'autojs_command'; Side = 'server'; Code = ('(function(){ AutoJS.command(' + $lit + '); return "autojs_command"; })()') })
        $plan.Add(@{ Name = 'perform_prefixed'; Side = 'server'; Code = ('(function(){ var s = server; var c = s.getCommands(); return String(c.performPrefixedCommand(s.createCommandSourceStack(), ' + $lit + ')); })()') })
    }
    $plan.Add(@{ Name = 'send_command'; Side = 'client'; Code = $codeSend })

    $chain = Invoke-MtChannelProbeChain -Version $Version -Plan $plan.ToArray() -TimeoutMs $TimeoutMs
    if ([bool]$chain.Ok) {
        $d = ('cmd_via={0} cmd={1}' -f $chain.Via, $cmdText)
        Write-MtChannelOp -Op 'command' -Channel 'autojs' -Status 'ok' -Detail $d
        return (New-MtChannelResult -Status 'OK' -Channel 'autojs' -Detail $d -Raw $chain.Raw)
    }
    return (Send-MtChannelCommandTail -Version $Version -Command $cmdText -Chain $chain)
}

function Send-MtChannelCommandTail {
    <#
    .SYNOPSIS
        （内部）`Send-MtChannelCommand` 探测链全败时的收口：区分「端点没起来」与「AutoJS 表达不了」。
    .NOTES
        不区分会在实际运行里造成**静默放过**：游戏没起来 ⇒ 端点连不上 ⇒ 三步全败，
        如果一律记 DELEGATED，用例就变成「降级跳过」而不是「客户端不在，结果不可信」。
        端点不可用 ⇒ ERROR（detail 带 `endpoint=unavailable`，mt_case 侧映射成 BLOCKED）；
        端点活着但每一级都被拒 ⇒ 才是契约 §1.1b 的 DELEGATED。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][string]$Command,
        [Parameter(Mandatory)][AllowNull()]$Chain
    )

    $chainErr = [string]$Chain.Error
    if (Test-MtChannelTransportError -Text $chainErr) {
        $d = ('cmd={0} {1} endpoint=unavailable' -f $Command, (ConvertTo-MtChannelOneLine -Text $chainErr))
        Write-MtChannelOp -Op 'command' -Channel 'autojs' -Status 'error' -Detail $d
        return (New-MtChannelResult -Status 'ERROR' -Channel 'autojs' -Detail $d -Raw $Chain.Raw)
    }
    return (New-MtChannelDelegatedResult -Version $Version -Op 'command' -Target $Command `
        -Reason ('探测链三步全部失败（端点可达但无一级被接受）：{0}' -f $chainErr))
}

# ── 按键 / 鼠标（autojs 助手面，契约 §1.1）──────────────────────────────
function Get-MtChannelKeySpec {
    <#
    .SYNOPSIS
        按键 → autojs 等价实现；无等价实现时返回 $null（调用方记 DELEGATED）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Key = '')

    $k = ([string]$Key).Trim().ToLowerInvariant()
    switch ($k) {
        'escape' { return [pscustomobject]@{ Side = 'client'; Code = $script:JsClearScreen; Note = 'setScreen(null)' } }
        'esc' { return [pscustomobject]@{ Side = 'client'; Code = $script:JsClearScreen; Note = 'setScreen(null)' } }
        'e' {
            return [pscustomobject]@{
                Side = 'client'
                Code = '(function(){ Client.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(Client.player)); return "inventory"; })()'
                Note = 'InventoryScreen'
            }
        }
        'f1' {
            return [pscustomobject]@{
                Side = 'client'
                Code = '(function(){ Client.options.hideGui = !Client.options.hideGui; return String(Client.options.hideGui); })()'
                Note = 'hideGui toggle'
            }
        }
        'rclick' { return [pscustomobject]@{ Side = 'server'; Code = '(function(){ return String(AutoJS.use()); })()'; Note = 'AutoJS.use()' } }
        'use' { return [pscustomobject]@{ Side = 'server'; Code = '(function(){ return String(AutoJS.use()); })()'; Note = 'AutoJS.use()' } }
        'right' { return [pscustomobject]@{ Side = 'server'; Code = '(function(){ return String(AutoJS.use()); })()'; Note = 'AutoJS.use()' } }
        'lclick' { return [pscustomobject]@{ Side = 'client'; Code = $script:JsAttackOrMine; Note = 'attack/mine' } }
        'attack' { return [pscustomobject]@{ Side = 'client'; Code = $script:JsAttackOrMine; Note = 'attack/mine' } }
        default { return $null }
    }
}

function Invoke-MtChannelJsOp {
    <#
    .SYNOPSIS
        执行一段单步 JS（按键/鼠标用）：成功 OK、脚本回 NOHIT 记 DELEGATED、eval 失败记 ERROR。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][string]$Op,
        [AllowEmptyString()][string]$Target = '',
        [Parameter(Mandatory)][string]$Side,
        [Parameter(Mandatory)][string]$Code,
        [AllowEmptyString()][string]$Note = '',
        [int]$TimeoutMs = 0
    )

    $r = Invoke-MtAutoJsEval -Code $Code -Side $Side -Mode 'Raw' -TimeoutMs $TimeoutMs
    if (Test-MtChannelEvalOk -Result $r) {
        $val = [string](Get-MtChannelField -Map $r -Key 'Value' -Default '')
        if ($val -eq 'NOHIT') {
            return (New-MtChannelDelegatedResult -Version $Version -Op $Op -Target $Target `
                -Reason '准星未命中实体/方块：autojs 通道无空挥 / GUI 点击等价实现' -Raw $r)
        }
        $d = ('target={0} via={1} value={2}' -f $Target, $Note, $val)
        Write-MtChannelOp -Op $Op -Channel 'autojs' -Status 'ok' -Detail $d
        return (New-MtChannelResult -Status 'OK' -Channel 'autojs' -Detail $d -Raw $r)
    }

    $err = [string](Get-MtChannelField -Map $r -Key 'Error' -Default 'eval 失败')
    $tail = if (Test-MtChannelTransportError -Text $err) { ' endpoint=unavailable' } else { '' }
    $d = ('target={0} {1}{2}' -f $Target, (ConvertTo-MtChannelOneLine -Text $err), $tail)
    Write-MtChannelOp -Op $Op -Channel 'autojs' -Status 'error' -Detail $d
    return (New-MtChannelResult -Status 'ERROR' -Channel 'autojs' -Detail $d -Raw $r)
}

function Send-MtChannelKey {
    <#
    .SYNOPSIS
        按键注入的通道化版本（契约 §1.1 的键位映射表）。

    .NOTES
        · `-NoEsc` 的语义与 mt_inject 对齐：**不带** -NoEsc 时先关掉可能开着的界面（等价「注入前
          按一次 Esc」；客户端侧在同一段 IIFE 内做，服务端侧先单独 eval 一次）；
        · `-HoldMs` 在 autojs 通道下无等价实现 ⇒ DELEGATED（助手面只有瞬时 use/attack/mine）；
        · 映射表之外的键（k / 数字 / 任意键）⇒ DELEGATED 并记进清单（契约 §1.1）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][AllowEmptyString()][string]$Key = '',
        [int]$HoldMs = 0,
        [switch]$NoEsc
    )

    $mode = Get-MtChannelMode
    if ($mode -eq 'native') {
        return (New-MtChannelNativeDelegate -Op 'key' -Reason 'mode=native：按键由 mt_inject.ps1 投递扫描码（legacy 路径）')
    }
    $keyText = ([string]$Key).Trim()
    if (-not $keyText) {
        Write-MtChannelOp -Op 'key' -Channel 'autojs' -Status 'error' -Detail 'key_empty'
        return (New-MtChannelResult -Status 'ERROR' -Channel 'autojs' -Detail 'key_empty：按键为空')
    }
    if (-not (Test-MtChannelAutoJsDeployed -Version $Version)) {
        return (New-MtChannelDelegatedResult -Version $Version -Op 'key' -Target $keyText `
            -Reason ('{0} 缺 autojs jar（run/<版本>/mods 里没有 AutoJS-*.jar）' -f $Version))
    }
    if ($HoldMs -gt 0) {
        return (New-MtChannelDelegatedResult -Version $Version -Op 'key' -Target $keyText `
            -Reason ('-HoldMs={0} 在 autojs 通道下无等价实现（助手面只有瞬时 use/attack/mine）' -f $HoldMs))
    }

    $spec = Get-MtChannelKeySpec -Key $keyText
    if ($null -eq $spec) {
        return (New-MtChannelDelegatedResult -Version $Version -Op 'key' -Target $keyText `
            -Reason ('键「{0}」在 autojs 通道下无等价实现（无键位 / 输入原语；GUI 按键一律委派）' -f $keyText))
    }

    $code = [string]$spec.Code
    $isEscKey = ($spec.Note -eq 'setScreen(null)')
    if ($spec.Side -eq 'server' -and -not $NoEsc) {
        # 服务端助手（use）受界面状态影响：先关屏，失败不致命（无界面时是空操作）
        [void](Invoke-MtAutoJsEval -Code $script:JsClearScreen -Side 'client' -Mode 'Raw')
    } elseif ($spec.Side -eq 'client' -and -not $NoEsc -and -not $isEscKey) {
        $code = '(function(){ try { Client.setScreen(null); } catch (e0) { } return ' + $code + '; })()'
    }

    return (Invoke-MtChannelJsOp -Version $Version -Op 'key' -Target $keyText `
        -Side ([string]$spec.Side) -Code $code -Note ([string]$spec.Note))
}

function Send-MtChannelMouse {
    <#
    .SYNOPSIS
        鼠标点击的通道化版本（契约 §1.1）。左键 = 准星攻击 / 挖掘，右键 = use()。

    .NOTES
        · `-Shift` ⇒ 先 `AutoJS.crouch(true)`、finally 里 `crouch(false)`（潜行状态下的点击）；
        · `-HoldMs` 无等价实现 ⇒ DELEGATED；
        · Shift+左键（容器内 Shift 点击）无 GUI 点击原语 ⇒ DELEGATED。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][ValidateSet('left', 'right')][string]$Button = 'left',
        [switch]$Shift,
        [int]$HoldMs = 0
    )

    $mode = Get-MtChannelMode
    if ($mode -eq 'native') {
        return (New-MtChannelNativeDelegate -Op 'mouse' -Reason 'mode=native：鼠标由 mt_inject.ps1 投递（legacy 路径）')
    }
    if (-not (Test-MtChannelAutoJsDeployed -Version $Version)) {
        return (New-MtChannelDelegatedResult -Version $Version -Op 'mouse' -Target $Button `
            -Reason ('{0} 缺 autojs jar（run/<版本>/mods 里没有 AutoJS-*.jar）' -f $Version))
    }
    if ($HoldMs -gt 0) {
        return (New-MtChannelDelegatedResult -Version $Version -Op 'mouse' -Target $Button `
            -Reason ('-HoldMs={0} 在 autojs 通道下无等价实现（无按住时长语义）' -f $HoldMs))
    }
    if ($Shift -and $Button -eq 'left') {
        return (New-MtChannelDelegatedResult -Version $Version -Op 'mouse' -Target 'left+shift' `
            -Reason 'Shift+左键在 autojs 通道下无等价实现（GUI 点击 / 潜行交互无原语）')
    }

    if ($Button -eq 'left') {
        $inner = $script:JsAttackOrMine
        $note = 'attack/mine'
    } else {
        $inner = '(function(){ return String(AutoJS.use()); })()'
        $note = 'AutoJS.use()'
    }
    $code = $inner
    if ($Shift) {
        $code = '(function(){ var __r = "NOHIT"; AutoJS.crouch(true); try { __r = ' + $inner + '; } finally { AutoJS.crouch(false); } return __r; })()'
        $note = $note + '+crouch'
    }

    return (Invoke-MtChannelJsOp -Version $Version -Op 'mouse' -Target $Button -Side 'client' -Code $code -Note $note)
}

# ── 截图（契约 §1.1：逐档探测签名 + 只有真的落盘才写 .mt_shots.json）──────
#
# 2026-11-02 第二轮（任务书要求「不要写死类名，改成运行期反射」）——
# ⚠️ 结论先说：**KubeJS 沙箱禁止反射**，任务书给的 getDeclaredMethods 写法在本运行时不可执行。
#    三条实测证据（client 侧 autojs-eval，全部是实机返回的原话）：
#      ① Java.loadClass('net.minecraft.client.Screenshot').class
#         → InternalError: Java class "net.minecraft.client.Screenshot" has no public instance field or method named "class"
#      ② Java.loadClass('net.minecraft.client.Screenshot').getDeclaredMethods()
#         → InternalError: … has no public instance field or method named "getDeclaredMethods"
#      ③ java.lang.Class.forName("net.minecraft.client.Screenshot")
#         → JavaException: java.lang.IllegalAccessException: Attempt to lookup caller-sensitive method using restricted lookup object
#         （Thread.currentThread().getContextClassLoader() 同样被拦；mc.getClass() 亦不可达 —— Rhino 包装器不暴露 Object 方法）
#    ⇒ 拿不到 Method 列表，反射这条路在本运行时**不存在**。本模块改为**等价的运行期类型协商**：
#      候选只声明「方法名 + 参数类型序列」（见 $script:ShotCandidates），实参在 JS 里按**类型名**现取现造：
#        File      → 从 Minecraft 实例取**现成的** gameDirectory（JS 侧禁止构造 File，java.io 在黑名单）
#        String    → <tag>.png
#        *Target*  → 名字里含 target 的任何类型都喂同一个渲染目标对象
#                    ⇒ MainTarget / RenderTarget 这类类名不出现在本文件里（26.1.2 到底叫哪个无需知道）
#        int → downscale 1；boolean → false；float/double → 1
#        *Consumer*/*Callback*/*Function*/*Listener* → JavaAdapter 回调
#      未覆盖的类型名一律抛错并换下一档（**拒绝猜值**）。
#      调用统一走 f.apply(S, args)；成功判据只有一条：**文件真的落盘**（外加 Invoke-MtChannelShot 的落盘兜底）。
#
# 档位顺序（先「能指定文件名」的档，再「匿名/时间戳命名」的档，最后最简档）：
#   T1 grab(File,String,Target,int,Consumer)   26.1.2 实测可跑（forceName 原样当文件名）
#   T2 grab(File,String,Target,Consumer)       1.21.1 世代
#   T3 grab(File,Target,Consumer)              26.x / 1.21.1 匿名档（时间戳命名 ⇒ 靠兜底改名）
#   T4 m_92289_(File,Target,Consumer)          1.20.1 SRG 匿名档（历史实测唯一能落盘的 SRG 档）
#   T5 m_92295_(File,String,Target,Consumer)   1.20.1 SRG 命名档
#   T6 grab(File,String,Target) / T7 grab(File,Target) / T8 grab(File)   无回调的最简档
# 26.1.2 实证（2026-11-02 04:47，sources jar + 实机双证）：Screenshot 只有 3 参 grab(File,Target,Consumer)
# 与 5 参 grab(File,String,Target,int,Consumer)。**不存在 4 参 grab** —— 上一轮 T1 报的
# "Can't find method ...grab(java.io.File,string,MainTarget,adapter1)" 就是调了不存在的 4 参。
# 5 参档的 forceName 是**原样文件名**（不追加 .png），内部 new File(new File(workDir,"screenshots"), forceName)；
# 3 参档的 forceName=null ⇒ 用 Util.getFilenameFormattedDateTime() 时间戳命名。
$script:ShotCandidates = @(
    @{ method = 'grab';     shape = @('File', 'String', 'Target', 'int', 'Consumer') }
    @{ method = 'grab';     shape = @('File', 'String', 'Target', 'Consumer') }
    @{ method = 'grab';     shape = @('File', 'Target', 'Consumer') }
    @{ method = 'm_92289_'; shape = @('File', 'Target', 'Consumer') }
    @{ method = 'm_92295_'; shape = @('File', 'String', 'Target', 'Consumer') }
    @{ method = 'grab';     shape = @('File', 'String', 'Target') }
    @{ method = 'grab';     shape = @('File', 'Target') }
    @{ method = 'grab';     shape = @('File') }
)

function Get-MtChannelShotCandidates {
    <#
    .SYNOPSIS
        截图候选签名表（副本）。每项 = @{method=方法名; shape=@(参数类型名…)}，供 Invoke-MtChannelShot 逐档驱动。
    #>
    [CmdletBinding()]
    param()

    $out = @()
    foreach ($c in $script:ShotCandidates) {
        $out += , ([pscustomobject]@{ method = [string]$c.method; shape = @([string[]]$c.shape) })
    }
    return $out
}

function Format-MtShotShape {
    <#
    .SYNOPSIS
        把「方法名 + 参数类型序列」渲染成签名串（写进 MT_CHANNEL 的 sig= 字段）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$Shape = @()
    )

    return ('{0}({1})' -f $Method, ($Shape -join ','))
}

function Get-MtChannelShotJs {
    <#
    .SYNOPSIS
        生成单档截图 JS（供 Invoke-MtChannelShot 逐档驱动；也供离线自测做静态检查）。

    .NOTES
        · 类载入只用 Java.loadClass('全限定名')；禁止 java.包名 访问器写法 ——
          KubeJS6 已移除该全局访问器，实机报 LegacyError: Line 4: 'java()' is no longer supported!；
        · 不构造 java.io.File（java.io 在 KubeJS 包黑名单里）：游戏目录 File 从 Minecraft 实例取；
        · 实参按参数类型序列构造：类型名里带 target 的一律喂渲染目标对象 ⇒ 26.1.2 的 MainTarget
          与 1.21.1 的 RenderTarget 共用同一档写法，代码里没有类名；
        · JS 不判定落盘：只报「调用是否抛异常 + gameDir 绝对路径（out.work）+ 渲染目标类型名」，
          文件由 PowerShell 侧轮询（含 gameDir 兜底扫描）认领。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][ValidateRange(1, 16)][int]$Tier,
        # 已带引号的 JS 字符串字面量（例：'a.png'）。不是裸文件名：JS 里是 var NAME = 这里，
        # 传裸名会生成 var NAME = a.png; 这种语法错误（2026-11-02 静态检查抓到）。
        [Parameter(Mandatory)][string]$NameJsLiteral,
        # 可选：直接指定方法名 / 参数类型序列（离线自测与诊断用；生产路径由 -Tier 从候选表取）。
        [AllowEmptyString()][string]$Method = '',
        [AllowEmptyCollection()][string[]]$Shape = @()
    )

    $cands = @($script:ShotCandidates)
    $useMethod = [string]$Method
    $useShape = @($Shape)
    if ((-not $useMethod) -or ($useShape.Count -eq 0)) {
        if (($Tier -lt 1) -or ($Tier -gt $cands.Count)) {
            throw ('-Tier 越界：{0}（候选表共 {1} 档）' -f $Tier, $cands.Count)
        }
        $useMethod = [string]$cands[$Tier - 1].method
        $useShape = @([string[]]$cands[$Tier - 1].shape)
    }
    $shapeJson = ConvertTo-Json -InputObject @($useShape) -Compress
    if (-not $shapeJson) { $shapeJson = '[]' }
    if (-not $shapeJson.TrimStart().StartsWith('[')) { $shapeJson = '[' + $shapeJson + ']' }

    $js = @'
(function(){
  var out = { ok: false, tier: __MT_TIER__, method: '', shape: '', named: false, work: '', file: '', rtType: '', err: '', notes: [] };
  var TIER = __MT_TIER__;
  var METHOD = __MT_METHOD__;
  var SHAPE = __MT_SHAPE__;
  var NAME = __MT_NAME__;
  function note(s) { out.notes.push(String(s)); }
  function pick(o, names) {
    for (var i = 0; i < names.length; i++) {
      try {
        var v = o[names[i]];
        if (typeof v === 'function') { v = o[names[i]](); }
        if (v !== null && v !== undefined) { return { v: v, n: names[i] }; }
      } catch (e) { }
    }
    return null;
  }
  try {
    // Minecraft 实例：KubeJS 客户端全局 Client 优先；否则 Java.loadClass + getInstance 探测
    var mc = null;
    try { if (typeof Client !== 'undefined' && Client !== null) { mc = Client; note('mc=Client'); } } catch (e0) { }
    if (mc === null) {
      var MC = Java.loadClass('net.minecraft.client.Minecraft');
      var mn = ['getInstance', 'm_91087_'];
      for (var i = 0; i < mn.length; i++) {
        try {
          if (typeof MC[mn[i]] === 'function') { mc = MC[mn[i]](); if (mc !== null) { note('mc=' + mn[i]); break; } }
        } catch (e1) { }
      }
    }
    if (mc === null) { out.err = 'Minecraft 实例取不到（Client 全局与 getInstance 均不可用）'; return out; }
    // 游戏目录：直接取 Minecraft 上现成的 File 实例（JS 侧不构造任何 File 类）
    var wp = pick(mc, ['gameDirectory', 'f_91069_', 'getGameDirectory']);
    if (wp === null) { out.err = '游戏目录 File 取不到（gameDirectory / f_91069_ / getGameDirectory 均不可用）'; return out; }
    var work = wp.v;
    note('work=' + wp.n);
    try { out.work = String(work.getAbsolutePath()); } catch (e2) { out.work = ''; }
    // 渲染目标：拿对象本身，不关心它叫 MainTarget 还是 RenderTarget
    var rp = pick(mc, ['mainRenderTarget', 'f_91042_', 'getMainRenderTarget', 'm_91385_']);
    var rt = (rp === null) ? null : rp.v;
    if (rp !== null) { note('rt=' + rp.n); }
    try { out.rtType = String(rt).split('@')[0]; } catch (e3) { out.rtType = ''; }
    // Consumer（grab 的回调；落盘判定只认文件，这里只需一个能收下回调的适配器）
    var consumer = null;
    try {
      var Ck = Java.loadClass('java.util.function.Consumer');
      consumer = new JavaAdapter(Ck, { accept: function (x) { } });
      note('consumer=adapter');
    } catch (e4) { consumer = null; note('consumer=null'); }
    // 按参数类型名造实参（类型名里带 target 一律喂渲染目标；不写死任何类名）
    // ⚠ 必须用 `var fn = function`，不能写 `function valForType(...)`：KubeJS/Rhino 里块作用域
    //   函数声明（这里在 try{} 块内）**不保证**在块内后续语句可见 —— 2026-11-02 实机实测
    //   `TypeError: valForType is not a function, it is undefined`，八档全灭。
    var valForType = function (tn) {
      var tl = String(tn).toLowerCase();
      if (tl === 'file' || tl === 'java.io.file') { return work; }
      if (tl === 'string' || tl === 'java.lang.string') { return NAME; }
      if (tl.indexOf('target') >= 0) {
        if (rt === null) { throw '参数类型 ' + tn + ' 需要渲染目标，但 mainRenderTarget 取不到'; }
        return rt;
      }
      if (tl === 'int' || tl === 'integer') { return 1; }
      if (tl === 'float' || tl === 'double') { return 1; }
      if (tl === 'boolean') { return false; }
      if (tl.indexOf('consumer') >= 0 || tl.indexOf('callback') >= 0 || tl.indexOf('function') >= 0 || tl.indexOf('listener') >= 0) {
        if (consumer === null) { throw '参数类型 ' + tn + ' 需要回调，但 JavaAdapter 构造失败'; }
        return consumer;
      }
      throw '未知参数类型 ' + tn + '（候选表未覆盖，拒绝猜值）';
    };
    var S = Java.loadClass('net.minecraft.client.Screenshot');
    var f = S[METHOD];
    if (typeof f !== 'function') { out.err = 'Screenshot.' + METHOD + ' 不存在'; return out; }
    var args = [];
    for (var k = 0; k < SHAPE.length; k++) { args.push(valForType(SHAPE[k])); }
    out.method = METHOD;
    out.shape = SHAPE.join(',');
    out.named = (out.shape.indexOf('String') >= 0);
    out.file = NAME;
    f.apply(S, args);
    out.ok = true;
  } catch (outer) {
    out.ok = false;
    out.err = String(outer);
  }
  return out;
})()
'@
    if ($NameJsLiteral -notmatch '^([''"])[\s\S]*\1$') {
        throw "-NameJsLiteral 必须是带引号的 JS 字符串字面量（例：'a.png'）；收到：$NameJsLiteral"
    }
    $methodLit = ConvertTo-MtChannelJsLiteral -Value $useMethod
    return ($js.Replace('__MT_TIER__', [string]$Tier).Replace('__MT_METHOD__', $methodLit).Replace('__MT_SHAPE__', $shapeJson).Replace('__MT_NAME__', $NameJsLiteral))
}
function Test-MtShotFileReady {
    <#
    .SYNOPSIS
        判断一张待认领的截图文件是否「已经写完」——非空且 PNG 结构完整（尾部含 IEND）。

    .DESCRIPTION
        B2 失真级根因（2026-11-02）：Screenshot.grab 走 Util.ioPool / 渲染线程异步落盘，会先创建
        0 字节文件，再慢慢写内容。旧轮询只判「eval 前不存在 + mtime ≥ 起点」，于是 0 字节半成品被
        当成本次证据认领成功（本轮三条 op=shot 全报 bytes=0，vision 断言拿到的其实是空帧/半帧）。
        此处把「写完」的判据前移进轮询：
          ① 文件存在且 Length > 0；
          ② 末 8 字节含 ASCII 'IEND'（标准 PNG 以 IEND 数据块收尾，其 CRC 之后的 4 字节正是 'IEND'）。
        读尾失败（文件被占用/刚被移走）一律视为未就绪 —— 宁可多等一轮，也不把半成品报成功。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    try {
        if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
        $fi = Get-Item -LiteralPath $Path -ErrorAction Stop
        $len = [long]$fi.Length
        if ($len -le 0) { return $false }
        $tailLen = [int][Math]::Min(8, $len)
        $buf = New-Object byte[] $tailLen
        $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        try {
            [void]$fs.Seek(-1 * [long]$tailLen, [System.IO.SeekOrigin]::End)
            $read = 0
            while ($read -lt $tailLen) {
                $n = $fs.Read($buf, $read, $tailLen - $read)
                if ($n -le 0) { break }
                $read += $n
            }
            if ($read -lt $tailLen) { return $false }
        } finally { $fs.Dispose() }
        $tail = [System.Text.Encoding]::ASCII.GetString($buf)
        return ($tail.Contains('IEND'))
    } catch {
        return $false
    }
}

function Wait-MtShotFile {
    <#
    .SYNOPSIS
        轮询等待「本次新产出且已经写完」的 PNG，返回其 FileInfo；超时返回 $null。

    .DESCRIPTION
        B2 修复（2026-11-02）：判据从「出现即成功」收紧为「Test-MtShotFileReady 为真才成功」，
        避免把 0 字节半成品当证据（当时三条 op=shot 全报 bytes=0）。

        第二轮（2026-11-02，落盘兜底）三条改动：
          · -MinBytes：**非精确名**（时间戳命名）文件的最小字节下限，默认 0，兜底认领用 20480。
            26.1.2 的 3 参 grab 用 Util.getFilenameFormattedDateTime() 命名，落盘名不是 <tag>.png，
            旧轮询只认精确名，于是「真拍到了」被判成 called_no_file。
          · -SinceSlackSeconds：**精确名**文件的 mtime 宽限（默认 30s）。精确名是「我们知道它必须是
            本次写的」那一档，给一点宽限抵掉文件系统时间戳精度/跨线程时间差；非精确名不给宽限
            （旧图必须在 mtime 上被排除）。
          · 每轮扫描取**最新 mtime** 的那一张（旧实现按目录顺序返回第一个，多目录时会抓到旧图）；
            精确名一旦就绪立刻返回，优先于任何非精确名命中。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string[]]$ScanDirs,
        [Parameter(Mandatory)][hashtable]$Pre,
        [Parameter(Mandatory)][double]$SinceUnix,
        [string]$TargetFile = '',
        [int]$TimeoutSeconds = 4,
        [int]$PollMilliseconds = 120,
        [long]$MinBytes = 0,
        [double]$SinceSlackSeconds = 30.0,
        [hashtable]$Trace = $null
    )

    if ($null -ne $Trace) { $Trace['unready'] = 0; $Trace['seen'] = 0; $Trace['fallback'] = 0 }
    $sinceExact = $SinceUnix - $SinceSlackSeconds
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $best = $null
        $bestU = 0.0
        foreach ($d in $ScanDirs) {
            if (-not (Test-Path -LiteralPath $d -PathType Container)) { continue }
            foreach ($fi in Get-ChildItem -LiteralPath $d -Filter '*.png' -File) {
                $isTarget = ($TargetFile -and ($fi.FullName -eq $TargetFile))
                $u = $fi.LastWriteTime.ToFileTime() / 10000000.0 - 11644473600
                # $pre 里存的是「快照那一刻该文件的 mtime」。只有**文件压根没变**（mtime 未被刷新）才当旧图排除。
                # 按路径一刀切会把「游戏覆盖同名旧文件」误杀：2026-11-02 实机实测，<tag>.png 在
                # gameDir/screenshots 里已存在旧图时，T1（能指定文件名那一档）明明写好了新图，却因为路径
                # 在黑名单里被判 called_no_file，白白退到 T3（时间戳命名）再靠落盘兜底收上来。
                if (-not $isTarget) {
                    if ($Pre.ContainsKey($fi.FullName)) {
                        $prevU = $null
                        try { $prevU = [double]$Pre[$fi.FullName] } catch { $prevU = $null }
                        if (($null -ne $prevU) -and ($u -le ($prevU + 0.001))) { continue }
                    }
                }
                if ($null -ne $Trace) { $Trace['seen'] = 1 + [int]$Trace['seen'] }
                if ($isTarget) {
                    if ($u -lt $sinceExact) { continue }
                } else {
                    if ($u -lt $SinceUnix) { continue }
                    if ($fi.Length -lt $MinBytes) { continue }
                }
                if (-not (Test-MtShotFileReady -Path $fi.FullName)) {
                    if ($null -ne $Trace) { $Trace['unready'] = 1 + [int]$Trace['unready'] }
                    continue
                }
                if ($isTarget) { return $fi }
                if (($null -eq $best) -or ($u -gt $bestU)) { $best = $fi; $bestU = $u }
            }
        }
        if ($null -ne $best) {
            if ($null -ne $Trace) { $Trace['fallback'] = 1 }
            return $best
        }
        Start-Sleep -Milliseconds $PollMilliseconds
    }
    return $null
}
function Invoke-MtChannelShot {
    <#
    .SYNOPSIS
        截图：客户端侧 `net.minecraft.client.Screenshot.grab(...)` 落到
        run/<version>/screenshots/<tag>.png，成功才登记 .mt_shots.json。

    .NOTES
        · 不写**假的**清单条目（§1.1b）：grab 没落文件 ⇒ DELEGATED，不动清单；
        · autojs 通道抓的是**游戏帧缓冲**（等价 F2 的成像面，不含窗口装饰）；`-Crop` 需要 GDI
          裁剪能力，本通道不提供 ⇒ Detail 里如实标 `crop=ignored`（不假装裁过）；
        · 六档签名依次尝试（Mojmap 命名/匿名 × RenderTarget/Consumer × downscale，再 1.20.1 SRG 两档），
          每档先 typeof 探测方法存在再调用，避免把某一版的签名硬编码进来。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][AllowEmptyString()][string]$Tag = '',
        [ValidateSet('f2', 'window')][string]$Mode = 'f2',
        [switch]$Crop,
        [scriptblock]$EvalOverride = $null,
        [psobject]$PathsOverride = $null
    )

    $chanMode = Get-MtChannelMode
    if ($chanMode -eq 'native') {
        return (New-MtChannelNativeDelegate -Op 'shot' -Reason 'mode=native：截图由 mt_capture.ps1 执行（legacy 路径）')
    }
    $tagText = ([string]$Tag).Trim()
    if (-not $tagText) {
        Write-MtChannelOp -Op 'shot' -Channel 'autojs' -Status 'error' -Detail 'tag_empty'
        return (New-MtChannelResult -Status 'ERROR' -Channel 'autojs' -Detail 'tag_empty：截图 tag 为空')
    }
    if (($null -eq $EvalOverride) -and (-not (Test-MtChannelAutoJsDeployed -Version $Version))) {
        return (New-MtChannelDelegatedResult -Version $Version -Op 'shot' -Target $tagText `
            -Reason ('{0} 缺 autojs jar（run/<版本>/mods 里没有 AutoJS-*.jar），截图通道不可用' -f $Version))
    }

    if ($null -ne $PathsOverride) { $paths = $PathsOverride } else { $paths = Get-MtPaths -Version $Version }
    $shotDir = [string]$paths.shot_dir
    $tagSafe = ($tagText -replace '[^\w\-\.]', '_')
    $fileName = $tagSafe + '.png'
    if (-not (Test-Path -LiteralPath $shotDir -PathType Container)) {
        [void](New-Item -ItemType Directory -Force -Path $shotDir)
    }
    $targetFile = Join-Path $shotDir $fileName
    # ⚠ Screenshot.grab 的第一个参数是**游戏目录**（run/<版本>，一个 java.io.File 实例），
    #   不是截图目录本身。JS 侧**不得构造** File：KubeJS 把 java.io / java.nio 等包列入黑名单，
    #   `Java.loadClass('java.io.File')` 必然抛错，而老式 `java.io.File` 包名写法在 KubeJS6 会触发
    #   LegacyError: 'java()' is no longer supported（2026-11-02 实机证据：1.20.1 op=shot 全部 DELEGATED）。
    #   ⇒ 改为从 Minecraft 实例上取**现成的** gameDirectory File 对象（SRG f_91069_ / Mojmap gameDirectory）。
    $workDir = [string](Split-Path -Parent $shotDir)
    # 清掉同名旧图：命名档可能落在游戏目录（grab(File,String,...) 的 name 副本）或 screenshots/，
    # 两处都清，确保只认「本次真落盘」的新证据。
    foreach ($st in @((Join-Path $shotDir $fileName), (Join-Path $workDir $fileName))) {
        if (Test-Path -LiteralPath $st -PathType Leaf) { Remove-Item -LiteralPath $st -Force }
    }
    $nameLit = ConvertTo-MtChannelJsLiteral -Value $fileName

    # 档位表来自 $script:ShotCandidates（不是硬编码清单）：每档一次 eval；签名串直接进 MT_CHANNEL 的 sig=。
    $cands = @(Get-MtChannelShotCandidates)
    $tierDesc = @()
    foreach ($c in $cands) { $tierDesc += (Format-MtShotShape -Method $c.method -Shape $c.shape) }
    $tierCount = $tierDesc.Count
    # 扫描面 = run/<版本>/screenshots + run/<版本>（历史档位落点）；每档 eval 之后再把 JS 报回的
    # gameDir 与 gameDir/screenshots 追加进来 —— 26.1.2 的 grab 就是落在 gameDir/screenshots，
    # 上一轮没扫这个面，于是「真拍到了文件」被判成 called_no_file。
    $scanDirs = @($shotDir, $workDir) | Select-Object -Unique
    $pre = @{}
    foreach ($d in $scanDirs) {
        if (Test-Path -LiteralPath $d -PathType Container) {
            foreach ($fi in Get-ChildItem -LiteralPath $d -Filter '*.png' -File) { $pre[$fi.FullName] = ($fi.LastWriteTime.ToFileTime() / 10000000.0 - 11644473600) }
        }
    }
    # 新鲜度起点改为**逐档**取（见循环里的 $tierSinceUnix），不再用「进循环前的 now-2s」——
    # 那份固定 2s 宽限会把上一次调用/上一个场景刚落盘的旧图算成本次证据（2026-11-02 离线自测
    # 场景 C 实证：上一场景留下的时间戳图被当成本次结果收走）。兜底（时间戳名）额外留 1s 余量
    # 抵掉文件系统 mtime 精度，旧图一律靠 $pre 排除。
    $fallbackSlackSeconds = 1.0
    # 落盘兜底：非精确名（时间戳命名）文件的字节下限。20KB 以下的 PNG 基本是半张/空图，不当证据。
    $minShotBytes = 20480

    $ok = $false
    $tierUsed = 0
    $tierNotes = @()
    $found = $null
    $lastRaw = $null
    $transportErr = ''
    $foundSrc = $null
    $rtUsed = ''
    for ($tier = 1; $tier -le $tierCount; $tier++) {
        # 本档起点 = 「本次调用」的时间边界：候选文件的 mtime 必须晚于它。
        $tierSinceUnix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0
        # 滚动快照：已知扫描面里「早于本档起点」的 PNG 记为旧图（$pre）。
        # 有了这一步，下面新发现的 gameDir 也能安全排除旧图；本档刚写的图 mtime >= 起点，不受影响。
        foreach ($d in @($scanDirs)) {
            if (-not (Test-Path -LiteralPath $d -PathType Container)) { continue }
            foreach ($fi in Get-ChildItem -LiteralPath $d -Filter '*.png' -File) {
                if (($fi.LastWriteTime.ToFileTime() / 10000000.0 - 11644473600) -lt $tierSinceUnix) {
                    $pre[$fi.FullName] = ($fi.LastWriteTime.ToFileTime() / 10000000.0 - 11644473600)
                }
            }
        }
        $js = Get-MtChannelShotJs -Tier $tier -NameJsLiteral $nameLit
        if ($null -ne $EvalOverride) {
            # 离线自测注入点（生产不传）：用一个 scriptblock 替掉 AutoJS 往返，参数是生成的 JS 原文。
            $r = & $EvalOverride $js
        } else {
            $r = Invoke-MtAutoJsEval -Code $js -Side 'client' -Mode 'Json'
        }
        $lastRaw = $r
        if (-not (Test-MtChannelEvalOk -Result $r)) {
            $e = [string](Get-MtChannelField -Map $r -Key 'Error' -Default '')
            if (-not $e) { $e = 'eval 失败' }
            $tierNotes += ('T{0} eval_error: {1}' -f $tier, (ConvertTo-MtChannelOneLine -Text $e))
            if (Test-MtChannelTransportError -Text $e) { $transportErr = $e; break }
            continue
        }
        $parsed = Get-MtChannelField -Map $r -Key 'ValueParsed'
        if ($null -eq $parsed) { $parsed = Get-MtChannelField -Map $r -Key 'Value' }
        $called = [bool](Get-MtChannelField -Map $parsed -Key 'ok' -Default $false)
        $callErr = [string](Get-MtChannelField -Map $parsed -Key 'err' -Default '')
        # ── 落盘兜底（一）：把 JS 报回的 gameDir 与 gameDir/screenshots 加进扫描面 ──
        #    out.work 是 JS 侧从 Minecraft 实例取到的 gameDirectory 绝对路径。上一轮就是从不扫这里，
        #    才把「文件真的写到了 prodgame/screenshots」判成 called_no_file。
        $jsWork = [string](Get-MtChannelField -Map $parsed -Key 'work' -Default '')
        $jsRt = [string](Get-MtChannelField -Map $parsed -Key 'rtType' -Default '')
        if ($jsWork) {
            $rtUsed = $jsRt
            foreach ($nd in @($jsWork, (Join-Path $jsWork 'screenshots'))) {
                try { $nf = [System.IO.Path]::GetFullPath($nd) } catch { continue }
                if (@($scanDirs) -notcontains $nf) {
                    $scanDirs = @(@($scanDirs) + $nf)
                    # 新目录补一次快照：早于本档起点的 PNG 记为旧图；本档刚落盘的（mtime >= 起点）不记，
                    # 否则「文件比 eval 返回先到」的竞态会把真证据误排除。
                    if (Test-Path -LiteralPath $nf -PathType Container) {
                        foreach ($fi in Get-ChildItem -LiteralPath $nf -Filter '*.png' -File) {
                            if (($fi.LastWriteTime.ToFileTime() / 10000000.0 - 11644473600) -lt $tierSinceUnix) {
                                $pre[$fi.FullName] = ($fi.LastWriteTime.ToFileTime() / 10000000.0 - 11644473600)
                            }
                        }
                    }
                }
            }
        }
        if (-not $called) {
            $tierNotes += ('T{0} not_called: {1}' -f $tier, (ConvertTo-MtChannelOneLine -Text $callErr))
            continue
        }
        # ── 落盘兜底（二）：精确名按 mtime 宽限认领；时间戳名要求「比起点新 + >=20480B + PNG 写完」，
        #    每轮取最新 mtime 的那一张。grab 走 Util.ioPool 异步落盘，4s 内轮询（120ms 一次）。
        $waitTrace = @{ unready = 0; seen = 0; fallback = 0 }
        $found = Wait-MtShotFile -ScanDirs $scanDirs -Pre $pre -SinceUnix ($tierSinceUnix - $fallbackSlackSeconds) -TargetFile $targetFile -TimeoutSeconds 4 -MinBytes $minShotBytes -Trace $waitTrace
        if ($null -ne $found) { $ok = $true; $tierUsed = $tier; $foundSrc = $found; break }
        if ([int]$waitTrace['unready'] -gt 0) {
            $errSuffix = ''
            if ($callErr) { $errSuffix = '; err=' + (ConvertTo-MtChannelOneLine -Text $callErr) }
            $tierNotes += ('T{0} called_file_unready: 出现 {1} 次未写完的 PNG（0 字节或尾部无 IEND），4s 内未就绪{2}' -f $tier, [int]$waitTrace['unready'], $errSuffix)
        } else {
            $tierNotes += ('T{0} called_no_file: work={1} rt={2} scanned={3} err={4}' -f $tier, (ConvertTo-MtChannelOneLine -Text $jsWork), (ConvertTo-MtChannelOneLine -Text $jsRt), @($scanDirs).Count, (ConvertTo-MtChannelOneLine -Text $callErr))
        }
    }
    if ($ok -and ($null -ne $foundSrc)) {
        # ── 落盘兜底（三）：源文件不在 run/<版本>/screenshots 时**复制**过来改名成 <tag>.png，
        #    源在本目录内则直接改名。原件保留在 gameDir/screenshots（那是游戏自己的落盘点，
        #    不搬走他人的文件；也让报告能引用原始路径）。
        $srcPath = [string]$foundSrc.FullName
        $srcNote = ' src=direct'
        if ($srcPath -ne $targetFile) {
            $srcDirFull = ''
            $shotDirFull = ''
            try { $srcDirFull = [System.IO.Path]::GetFullPath((Split-Path -Parent $srcPath)) } catch { $srcDirFull = '' }
            try { $shotDirFull = [System.IO.Path]::GetFullPath($shotDir) } catch { $shotDirFull = '' }
            if ($srcDirFull -and ($srcDirFull -eq $shotDirFull)) {
                Move-Item -LiteralPath $srcPath -Destination $targetFile -Force
                $srcNote = ' src=moved'
            } else {
                Copy-Item -LiteralPath $srcPath -Destination $targetFile -Force
                $srcNote = (' src=fallback_copy:{0}' -f (ConvertTo-MtChannelOneLine -Text $srcPath))
            }
        }
        if (Test-Path -LiteralPath $targetFile -PathType Leaf) {
            $runId = ''
            try { $runId = [string](Get-MtActiveRunId) } catch { $runId = '' }
            # B2：文件已通过 Test-MtShotFileReady ⇒ bytes 必为非零真实值；这里再核一次，绝不报 0。
            $bytes = 0
            try { $bytes = [long](Get-Item -LiteralPath $targetFile).Length } catch { $bytes = 0 }
            $pngOk = 1
            if (-not (Test-MtShotFileReady -Path $targetFile)) { $pngOk = 0 }
            # 硬门：清单**只**经 Add-MtShot 登记（本函数不手工写 .mt_shots.json）。
            $regNote = ' registered'
            try {
                [void](Add-MtShot -Paths $paths -FilePath $targetFile -Tag $tagText -RunId $runId -Mode $Mode -Path ([System.IO.Path]::GetFullPath($targetFile)))
            } catch {
                $regNote = ' add_shot_err=' + (ConvertTo-MtChannelOneLine -Text $_.Exception.Message)
            }
            $cropNote = if ($Crop) { ' crop=ignored（autojs 通道无 GDI 裁剪，截图为整帧）' } else { '' }
            # 成功时也把前面各档的失败原因带出来（2026-11-02 实测：只报 tier=T3 时看不出 T1 为什么没成）；
            # 上限 400 字，保证机器行单行可读。
            $attemptsNote = ''
            if (@($tierNotes).Count -gt 0) {
                $attemptsNote = ' attempts=' + (ConvertTo-MtChannelOneLine -Text (@($tierNotes) -join '; ') -MaxLen 400)
            }
            $d = ('file={0} mode={1} bytes={2} tier=T{3} sig={4} png_ok={5} rt={6}{7}{8}{9}{10}' -f $fileName, $Mode, $bytes, $tierUsed, $tierDesc[$tierUsed - 1], $pngOk, $rtUsed, $regNote, $cropNote, $srcNote, $attemptsNote)
            Write-MtChannelOp -Op 'shot' -Channel 'autojs' -Status 'ok' -Detail $d
            return (New-MtChannelResult -Status 'OK' -Channel 'autojs' -Detail $d -Raw $lastRaw)
        }
        $tierNotes += '文件落到目标路径前消失（复制/改名后不存在）'
    }
    $err = ('各档签名均未产出 {0}：{1}' -f $fileName, ($tierNotes -join '; '))
    if (-not $tierNotes -or $tierNotes.Count -eq 0) { $err = ('截图未落盘（{0}）：无档位可用' -f $fileName) }
    # 「端点没起来」不是「autojs 表达不了」：前者必须记 ERROR（→ mt_case 侧 BLOCKED），
    # 否则客户端没开时截图会静默降级成 DELEGATED，vision 断言看起来「跳过」而不是「不可信」。
    if ($transportErr -or (Test-MtChannelTransportError -Text $err)) {
        $raw = if ($transportErr) { $transportErr } else { $err }
        $dErr = ('tag={0} {1} endpoint=unavailable' -f $tagText, (ConvertTo-MtChannelOneLine -Text $raw))
        Write-MtChannelOp -Op 'shot' -Channel 'autojs' -Status 'error' -Detail $dErr
        return (New-MtChannelResult -Status 'ERROR' -Channel 'autojs' -Detail $dErr -Raw $lastRaw)
    }
    return (New-MtChannelDelegatedResult -Version $Version -Op 'shot' -Target $tagText -Reason $err -Raw $lastRaw)
}

# ── 诊断（契约 §1 的 Get-MtChannelPlan）──────────────────────────────────
function Get-MtChannelPlan {
    <#
    .SYNOPSIS
        诊断用：当前模式 + autojs 可用性 + 各 op 的预期去向。纯查询，**不写机器行**（op=plan 不在
        §1.3 的 op 枚举内，凭空加一种会被判据当成非法行）。数据在 Raw 里。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $mode = Get-MtChannelMode
    $deployed = Test-MtChannelAutoJsDeployed -Version $Version
    $probeCache = ''
    if ($script:ProbeCache.ContainsKey($Version)) { $probeCache = [string]$script:ProbeCache[$Version] }

    $ops = [ordered]@{}
    foreach ($op in @('command', 'key', 'mouse', 'shot', 'reload')) {
        $ops[$op] = if ($mode -eq 'native') { 'native' } elseif (-not $deployed) { 'delegated' } else { 'autojs' }
    }

    $delegatedPath = ''
    try { $delegatedPath = Join-Path ([string](Get-MtPaths -Version $Version).run_dir) $script:DelegatedFileName } catch { $delegatedPath = '' }

    $opsText = ($ops.GetEnumerator() | ForEach-Object { '{0}={1}' -f $_.Key, $_.Value }) -join ' '
    $detail = ('mode={0} deployed={1} {2} probe_cache={3}' -f $mode, $deployed, $opsText, $probeCache)

    $raw = [pscustomobject][ordered]@{
        Mode              = $mode
        Version           = $Version
        AutoJsDeployed    = $deployed
        CommandProbeCache = $probeCache
        Ops               = [pscustomobject]$ops
        DelegatedFile     = $delegatedPath
        DelegatedCount    = $script:DelegatedCount
    }
    return (New-MtChannelResult -Status 'OK' -Channel $mode -Detail $detail -Raw $raw)
}

Export-ModuleMember -Function @(
    'Get-MtChannelMode', 'Test-MtChannelAutoJs', 'Send-MtChannelCommand',
    'Send-MtChannelKey', 'Send-MtChannelMouse', 'Invoke-MtChannelShot', 'Get-MtChannelPlan', 'Get-MtChannelShotJs',
    'Test-MtShotFileReady', 'Wait-MtShotFile',
    'Get-MtChannelShotCandidates', 'Format-MtShotShape'
)
