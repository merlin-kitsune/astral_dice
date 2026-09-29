#Requires -Version 7.0
<#
.SYNOPSIS
    ft_inject — fabric-1.20.1 线的手动命令注入（逐条下达、边发边看回显）。

.DESCRIPTION
    两条通道（**手动粒度 = 命令级**，与 TESTING-RULES-OVERVIEW.md §3.2 的现行范式一致）：

      ① `--channel rcon`（**默认**，仅服务端）
         走 vanilla 专用服务端的原生 RCON：`run/server/server.properties` 的
         `enable-rcon` / `rcon.port` / `rcon.password`（实测该文件:4,33,41 三个键已存在）。
         优点：**不需要冷启动**（只要服务端启动时已 enable-rcon=true），协议固定、不依赖任何模组 API。
         启用：`pwsh -File scripts/test/fabric/ft_env.ps1 --side server --enable-rcon` 后重启服务端。

      ② `--channel kubejs`（次通道，仅服务端）
         往 `run/server/kubejs/.ft_cmd_queue.txt` **追加一行**；由已安装的 KubeJS 观察脚本
         `ft_cmd_watcher.js`（ft_env --install-watcher 安装）每 20 tick 读走并
         `server.runCommand(...)` 执行。
         ⚠️ 生效前提：观察脚本必须在游戏启动前就位（KubeJS 只在**冷启动/`/kubejs reload server_scripts`**
            时加载脚本）；因此首次使用请先跑 `ft_env --side server --install-watcher` 再启动。

    为什么不照搬生产线的 mt_inject.ps1：那是 Win32 `PostMessage` 键鼠注入（依赖 Mt.Win32 模块、
    en-US 键盘布局、窗口线程输入法切换），面向**客户端 GUI** 的键鼠动作；fabric 台 v1 的注入
    目标是**服务端命令**（探针读数、/give、/kill 等），走 RCON/脚本队列即可，
    不需要引入窗口与输入法这一整层（也就不会受「容器界面吞掉注入」的坑影响）。
    ⇒ 已知缺口：**客户端 GUI 键鼠注入本台 v1 未覆盖**（已在 README 的覆盖缺口里如实登记）。

.PARAMETER Channel
    rcon（默认）| kubejs。

.PARAMETER Side
    server | client（默认 server；kubejs 通道仅服务端，client 会被拒）。

.PARAMETER Port / Password / Host
    RCON 连接参数；缺省从 `run/server/server.properties` 的 `rcon.port` / `rcon.password` 读取，
    读不到时回落到 25575 / `astralft`（ft_env --enable-rcon 写入的默认值）。

.EXAMPLE
    pwsh -NoProfile -File scripts/test/fabric/ft_inject.ps1 cmd --command "/give @s astral_dice:star_coin 3"
    pwsh -NoProfile -File scripts/test/fabric/ft_inject.ps1 cmd --command "time set day"
    pwsh -NoProfile -File scripts/test/fabric/ft_inject.ps1 cmd --command "/say hi" --channel kubejs

.NOTES
    输出：AP_FAB_INJECT: … / AP_FAB_INJECT_RESP: …；退出码 0 = 已投递（含服务端回显）；2 = 通道不可用。
#>

$ErrorActionPreference = 'Stop'

$script:FtLibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:FtLibDir 'Ft.Common.psm1') -Force

Initialize-FtConsole

# ══ RCON 协议原语（Source RCON：4 字节长度(LE) + 4 字节 id + 4 字节 type + body(ASCIIZ) + 1 字节 pad）══
function New-FtRconPacket {
    [CmdletBinding()]
    param([Parameter(Mandatory)][int]$Id, [Parameter(Mandatory)][int]$Type, [Parameter(Mandatory)][AllowEmptyString()][string]$Body)

    $bodyBytes = [System.Text.Encoding]::ASCII.GetBytes($Body)
    $len = 4 + 4 + $bodyBytes.Length + 2
    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter($ms)
    try {
        $bw.Write([int]$len)
        $bw.Write([int]$Id)
        $bw.Write([int]$Type)
        $bw.Write($bodyBytes)
        $bw.Write([byte]0)
        $bw.Write([byte]0)
        $bw.Flush()
        return [byte[]]$ms.ToArray()
    } finally {
        $bw.Dispose()
        $ms.Dispose()
    }
}

function Read-FtRconPacket {
    [CmdletBinding()]
    param([Parameter(Mandatory)][System.Net.Sockets.NetworkStream]$Stream)

    $lenBuf = New-Object byte[] 4
    $read = 0
    while ($read -lt 4) {
        $n = $Stream.Read($lenBuf, $read, 4 - $read)
        if ($n -le 0) { throw 'RCON 连接被对端关闭（读取长度头）' }
        $read += $n
    }
    $len = [System.BitConverter]::ToInt32($lenBuf, 0)
    if ($len -lt 10 -or $len -gt 1048576) { throw "RCON 包长度异常：$len" }

    $buf = New-Object byte[] $len
    $read = 0
    while ($read -lt $len) {
        $n = $Stream.Read($buf, $read, $len - $read)
        if ($n -le 0) { throw 'RCON 连接被对端关闭（读取包体）' }
        $read += $n
    }
    $id = [System.BitConverter]::ToInt32($buf, 0)
    $type = [System.BitConverter]::ToInt32($buf, 4)
    $bodyLen = $len - 10
    $body = ''
    if ($bodyLen -gt 0) { $body = [System.Text.Encoding]::UTF8.GetString($buf, 8, $bodyLen) }
    return [pscustomobject]@{ Id = $id; Type = $type; Body = $body }
}

function Invoke-FtRconCommand {
    <#
    .SYNOPSIS
        连 RCON、认证、逐条下发命令，返回每条命令的服务端回显。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$HostName,
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)][string]$Password,
        [Parameter(Mandatory)][string[]]$Commands,
        [int]$TimeoutMs = 8000
    )

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne($TimeoutMs)) {
            throw "连接 $HostName`:$Port 超时（RCON 未启用？服务端没起？）"
        }
        $client.EndConnect($iar)
        $client.ReceiveTimeout = $TimeoutMs
        $stream = $client.GetStream()

        # 认证（type=3）
        $auth = New-FtRconPacket -Id 1 -Type 3 -Body $Password
        $stream.Write($auth, 0, $auth.Length)
        $stream.Flush()
        $resp = Read-FtRconPacket -Stream $stream
        if ($resp.Id -eq -1) { throw 'RCON 认证失败（rcon.password 不匹配）' }
        if ($resp.Id -ne 1) { throw "RCON 认证响应 id 异常：$($resp.Id)" }

        $out = New-Object System.Collections.Generic.List[string]
        $id = 2
        foreach ($c in $Commands) {
            $pkt = New-FtRconPacket -Id $id -Type 2 -Body $c
            $stream.Write($pkt, 0, $pkt.Length)
            $stream.Flush()
            $r = Read-FtRconPacket -Stream $stream
            $text = [string]$r.Body
            # 大响应会分多包；把已经到达的后续包一并并进来
            $guard = 0
            while ($stream.DataAvailable -and $guard -lt 64) {
                $guard++
                Start-Sleep -Milliseconds 30
                $r2 = Read-FtRconPacket -Stream $stream
                $text += [string]$r2.Body
            }
            $out.Add($text)
            $id++
        }
        return @($out)
    } finally {
        try { $client.Close() } catch { }
    }
}

# ══ 参数 ══════════════════════════════════════════════════════════════════
$Sub = ''
$Command = ''
$Channel = 'rcon'
$Side = 'server'
$HostName = '127.0.0.1'
$Port = 0
$Password = ''
$ArgList = @($args)
$subIdx = -1
for ($j = 0; $j -lt $args.Count; $j++) {
    if (-not ([string]$args[$j]).StartsWith('-')) { $Sub = ([string]$args[$j]).ToLowerInvariant(); $subIdx = $j; break }
}
if (-not $Sub) { $Sub = 'help' }

$i = 0
while ($i -lt $args.Count) {
    if ($i -eq $subIdx) { $i++; continue }
    $tok = [string]$args[$i]
    $key = Get-FtArgKey -Token $tok
    if ($key -eq 'command') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --command 的值'; exit $FT_EXIT_ERROR }
        $Command = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'channel') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --channel 的值'; exit $FT_EXIT_ERROR }
        $Channel = ([string]$args[$i + 1]).ToLowerInvariant(); $i += 2
    } elseif ($key -eq 'side') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --side 的值'; exit $FT_EXIT_ERROR }
        $Side = ([string]$args[$i + 1]).ToLowerInvariant(); $i += 2
    } elseif ($key -eq 'host') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --host 的值'; exit $FT_EXIT_ERROR }
        $HostName = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'port' -or $key -eq 'rconport') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --port 的值'; exit $FT_EXIT_ERROR }
        $Port = [int]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'password' -or $key -eq 'rconpassword') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --password 的值'; exit $FT_EXIT_ERROR }
        $Password = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'allowauto') {
        $i++
    } elseif ($key -eq 'h' -or $key -eq 'help') {
        Write-FtLine '用法: ft_inject.ps1 cmd --command "<命令>" [--channel rcon|kubejs] [--side server]'
        Write-FtLine '                       [--host 127.0.0.1] [--port 25575] [--password <pw>]'
        exit $FT_EXIT_PASS
    } else {
        Write-FtErrorLine "未知参数 $tok"; exit $FT_EXIT_ERROR
    }
}

if ($Sub -ne 'cmd') {
    Write-FtErrorLine "未知子命令 '$Sub'（只接受 cmd）"
    exit $FT_EXIT_ERROR
}
if (-not $Command) { Write-FtErrorLine '--command 必填'; exit $FT_EXIT_ERROR }
if ($Command.Contains("`n") -or $Command.Contains("`r")) {
    Write-FtErrorLine '--command 里不允许换行（一次只发一条，手动粒度 = 命令级）'
    exit $FT_EXIT_ERROR
}
if ($Channel -notin @('rcon', 'kubejs')) {
    Write-FtErrorLine "非法 --channel '$Channel'（只接受 rcon | kubejs）"; exit $FT_EXIT_ERROR
}
[void](Assert-FtSide -Side $Side)

# ══ 通道 ②：kubejs 队列 ══════════════════════════════════════════════════
if ($Channel -eq 'kubejs') {
    if ($Side -ne 'server') {
        Write-FtLine ("AP_FAB_INJECT: FAIL (channel=kubejs reason=client-unsupported)")
        Write-FtError 'INJECT' 'kubejs 通道仅服务端可用（客户端没有 runCommand 等价入口）；客户端注入本台 v1 未提供。'
        exit $FT_EXIT_ERROR
    }
    $queue = Join-Path (Get-FtSideDir -Side $Side) 'kubejs\.ft_cmd_queue.txt'
    $watcher = Join-Path (Get-FtKubejsScriptDir -Side $Side) 'ft_cmd_watcher.js'
    if (-not (Test-Path -LiteralPath $watcher -PathType Leaf)) {
        Write-FtLine 'AP_FAB_INJECT: FAIL (channel=kubejs reason=watcher-missing)'
        Write-FtError 'INJECT' ("观察脚本不在位：$watcher —— 先跑 `pwsh -File scripts/test/fabric/ft_env.ps1 --side server --install-watcher` 再冷启动服务端")
        exit $FT_EXIT_ERROR
    }
    $queueDir = Split-Path -Parent $queue
    if (-not (Test-Path -LiteralPath $queueDir -PathType Container)) { [void](New-Item -ItemType Directory -Force -Path $queueDir) }
    [System.IO.File]::AppendAllText($queue, ($Command + "`n"), [System.Text.UTF8Encoding]::new($false))
    Write-FtLine ("AP_FAB_INJECT: OK (channel=kubejs side={0} queue={1} cmd={2})" -f $Side, $queue, $Command)
    Write-FtWarn 'kubejs 通道是异步的：观察脚本每 20 tick 轮询一次；用 ft_assert 找到 `[ft-inject] ran rc=` 行才算真正执行。'
    exit $FT_EXIT_PASS
}

# ══ 通道 ①：RCON（默认）═══════════════════════════════════════════════════
$props = Join-Path (Get-FtSideDir -Side 'server') 'server.properties'
$propsText = ''
if (Test-Path -LiteralPath $props -PathType Leaf) { $propsText = Read-FtLogText -Path $props }

if ($Port -le 0) {
    $Port = 25575
    $m = [regex]::Match($propsText, '(?m)^\s*rcon\.port\s*=\s*(\d+)\s*$')
    if ($m.Success) { $Port = [int]$m.Groups[1].Value }
}
if (-not $Password) {
    $Password = 'astralft'
    $m = [regex]::Match($propsText, '(?m)^\s*rcon\.password\s*=\s*(.+?)\s*$')
    if ($m.Success -and $m.Groups[1].Value) { $Password = $m.Groups[1].Value }
}
$enabled = ($propsText -match '(?m)^\s*enable-rcon\s*=\s*true\s*$')
Write-FtLine ("AP_FAB_INJECT_TARGET: channel=rcon host={0} port={1} enabled_in_props={2}" -f $HostName, $Port, $enabled.ToString().ToLowerInvariant())
if (-not $enabled) {
    Write-FtWarn 'server.properties 里 enable-rcon 不是 true ⇒ 若服务端是改之前启动的，连接必然失败。'
}

try {
    $responses = Invoke-FtRconCommand -HostName $HostName -Port $Port -Password $Password -Commands @($Command)
} catch {
    Write-FtLine 'AP_FAB_INJECT: FAIL (channel=rcon reason=connect-or-auth)'
    Write-FtError 'INJECT' ("$($_.Exception.Message)　⇒ 检查：① ft_env --side server --enable-rcon 后**重启**服务端；② 口令与 server.properties 的 rcon.password 一致。")
    exit $FT_EXIT_ERROR
}

Write-FtLine ("AP_FAB_INJECT: OK (channel=rcon side=server cmd={0})" -f $Command)
$idx = 0
foreach ($r in $responses) {
    $idx++
    if ([string]::IsNullOrEmpty($r)) {
        Write-FtLine ("AP_FAB_INJECT_RESP: [{0}] (empty)" -f $idx)
        continue
    }
    foreach ($ln in @($r -split "`r?`n")) {
        if ($ln -ne '') { Write-FtLine ("AP_FAB_INJECT_RESP: [{0}] {1}" -f $idx, $ln) }
    }
}
Write-FtOk 'INJECT' ("channel=rcon cmd=$Command")
exit $FT_EXIT_PASS
