#Requires -Version 7.0
<#
Mt.AutoJs.psm1 — AutoJS（脚本上下文 MCP）客户端：把 Streamable HTTP 上的 JSON-RPC 包成几个可复用函数。

## 协议要点（唯一事实来源：docs/features/autojs-test-harness-spec.md §3 + .codex/skills/autojs/SKILL.md）
· 传输：HTTP POST <endpoint>（默认 http://127.0.0.1:25585/mcp），JSON-RPC 2.0，**没有服务端 SSE 流**
  （GET /mcp 回 405）；每个请求都是一次独立 POST，服务端因此是**无状态**的。
· 请求头：Content-Type: application/json；Accept: application/json, text/event-stream
  —— 服务端两种都能回，客户端**两种都必须会读**（本模块都支持）；
  initialize 的响应头若带 Mcp-Session-Id，后续请求一律回带（Reset-MtAutoJsSession 清缓存）。
· 握手顺序：initialize（回带服务端的 protocolVersion）→ notifications/initialized（202/204、无 body）
  → 目标 tools/call。会话 id 变了 / 丢了就必须重新握手。
· 工具面只有四个：status / eval_js {code, side, timeout_ms} / kubejs_log {side, kind, limit} /
  reload_scripts {side, timeout_ms}；side ∈ auto|server|client（auto = 先 server 后 client）。
· 响应两种形态：application/json 直接是 JSON-RPC 信封；text/event-stream 是一串 data: 行
  ⇒ **取最后一条** JSON-RPC 消息（服务端可能先发通知再发结果，取第一条会拿到 notifications/*）。
· 工具结果：result.content[].text 里是一条 **JSON 字符串**（成功形如
  {"ok":true,"side":"server","elapsedMs":6,"type":"string","value":"…"}），本模块把它解析成对象再返回。

## 错误归一化（调用方只认这三条）
1. result.isError = true，或 content[].text 解析出的对象 ok = false ⇒ 记为失败，
   统一返回 @{ Ok=$false; Error=…; Stack=…; Diagnostics=… }（Error 优先取工具自己的 error 字段）。
2. 传输层失败（连不上 / 4xx-5xx / 信封里是 JSON-RPC error）⇒ 同一个形状，Error 里写明
   阶段（initialize / notifications/initialized / tools/call <name>）与状态码。
3. **端点没起来 ≠ 断言失败**：调用方拿到 Ok=$false、且 Test-MtAutoJsEndpoint 也不通时，
   用例必须记 **BLOCKED（11）**，**不得**记 FAIL —— AutoJS 是取证通道，
   「端点在不在」与「功能对不对」是两件事。

## 输出纪律
本模块**不**用 Write-Output / Write-Host（会污染调用方的管道）：需要给观众看的行一律走
Write-MtAutoJsLine ⇒ [Console]::Out.Write($Text + [string][char]10)，与 Mt.Phase.psm1 的 Write-MtLine 同口径（显式 LF）。

## 参数约定
所有公开函数都接受 -EndpointUrl（缺省取 mt.conf 的 MT_AUTOJS_ENDPOINT，再缺省内置 25585）与
-TimeoutMs（缺省取 mt.conf 的 MT_AUTOJS_TIMEOUT_MS，再缺省 15000）；
MT_AUTOJS_TOKEN 非空时自动加 Authorization: Bearer <token>。这两个开关是给自测/多实例用的。
#>

$ErrorActionPreference = 'Stop'

# ⚠️ 与 Mt.Paths.psm1 同一个理由**不得**用 -Force：-Force 会先卸载再重载被引入的模块，
#    把调用方作用域里已经注入的函数一起摘掉（实测表现为随后「术语 … 不会被识别」而无报错）。
Import-Module (Join-Path $PSScriptRoot 'Mt.Paths.psm1')

$script:DefaultEndpointUrl = 'http://127.0.0.1:25585/mcp'
$script:DefaultTimeoutMs = 15000
$script:DefaultPollMs = 500
$script:ProtocolVersion = '2025-06-18'
$script:ClientName = 'mt-autojs-client'
$script:Session = @{
    EndpointUrl     = ''
    SessionId       = ''
    ProtocolVersion = ''
    Initialized     = $false
}

function Write-MtAutoJsLine {
    <#
    .SYNOPSIS
        与 Mt.Phase.psm1 的 Write-MtLine 同口径的输出原语（显式 LF，不经管道）。
    #>
    param([string]$Text)
    [Console]::Out.Write($Text + [string][char]10)
}

# ── 配置解析 ──────────────────────────────────────────────────────────────

function Get-MtAutoJsConf {
    <#
    .SYNOPSIS
        读 mt.conf（缺文件/读不到时返回 @{}）。仅供本模块内部使用。
    #>
    [CmdletBinding()]
    param()

    try {
        $conf = Get-MtConf
        if ($null -ne $conf) { return $conf }
    } catch {
        # mt.conf 缺失不应让 MCP 客户端崩：退回内置默认值
    }
    return @{}
}

function Get-MtAutoJsEndpointUrl {
    <#
    .SYNOPSIS
        端点 URL：参数 > mt.conf 的 MT_AUTOJS_ENDPOINT > 内置 http://127.0.0.1:25585/mcp。
    #>
    [CmdletBinding()]
    param([string]$EndpointUrl = '')

    if ($EndpointUrl -and $EndpointUrl.Trim()) { return $EndpointUrl.Trim() }
    $conf = Get-MtAutoJsConf
    if ($conf.ContainsKey('MT_AUTOJS_ENDPOINT')) {
        $raw = [string]$conf['MT_AUTOJS_ENDPOINT']
        if ($raw -and $raw.Trim()) { return $raw.Trim() }
    }
    return $script:DefaultEndpointUrl
}

function Get-MtAutoJsTimeoutMs {
    <#
    .SYNOPSIS
        超时（毫秒）：参数 > mt.conf 的 MT_AUTOJS_TIMEOUT_MS > 15000。
    #>
    [CmdletBinding()]
    param([int]$TimeoutMs = 0)

    if ($TimeoutMs -gt 0) { return $TimeoutMs }
    $conf = Get-MtAutoJsConf
    if ($conf.ContainsKey('MT_AUTOJS_TIMEOUT_MS')) {
        $parsed = 0
        if ([int]::TryParse(([string]$conf['MT_AUTOJS_TIMEOUT_MS']).Trim(), [ref]$parsed) -and $parsed -gt 0) {
            return $parsed
        }
    }
    return $script:DefaultTimeoutMs
}

function Get-MtAutoJsAuthHeaders {
    <#
    .SYNOPSIS
        MT_AUTOJS_TOKEN 非空时给出 Bearer 头；否则空 hashtable。
    #>
    [CmdletBinding()]
    param()

    $conf = Get-MtAutoJsConf
    if ($conf.ContainsKey('MT_AUTOJS_TOKEN')) {
        $token = ([string]$conf['MT_AUTOJS_TOKEN']).Trim()
        if ($token) { return @{ 'Authorization' = ('Bearer ' + $token) } }
    }
    return @{}
}

function ConvertTo-MtAutoJsEndpointParts {
    <#
    .SYNOPSIS
        把端点 URL 拆成 scheme/host/port；不是合法 http(s) 绝对 URL 时返回 $null。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Url)

    try {
        $u = [System.Uri]::new($Url.Trim())
    } catch {
        return $null
    }
    if (-not $u.IsAbsoluteUri) { return $null }
    if ($u.Scheme -notin @('http', 'https')) { return $null }
    $port = if ($u.Port -gt 0) { $u.Port } else { if ($u.Scheme -eq 'https') { 443 } else { 80 } }
    return @{ Scheme = $u.Scheme; Host = $u.Host; Port = $port }
}

# ── 传输层 ────────────────────────────────────────────────────────────────

function Test-MtAutoJsTcpPort {
    <#
    .SYNOPSIS
        纯 TCP 连通性探测（不握手、不发请求）。返回 @{ Ok; Reason }，**绝不抛**。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$HostName,
        [Parameter(Mandatory)][int]$Port,
        [int]$TimeoutMs = 2000
    )

    $where = $HostName + ':' + $Port
    $client = $null
    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        $task = $client.ConnectAsync($HostName, $Port)
        $waitMs = [math]::Max(1, $TimeoutMs)
        if (-not $task.Wait($waitMs)) {
            return @{ Ok = $false; Reason = ('TCP 连接超时（' + $where + '，' + $waitMs + ' ms）') }
        }
        if (-not $client.Connected) {
            return @{ Ok = $false; Reason = ('TCP 未建立连接（' + $where + '）') }
        }
        return @{ Ok = $true; Reason = '' }
    } catch {
        $msg = $_.Exception.Message
        $inner = $_.Exception.InnerException
        if ($null -ne $inner) { $msg = $inner.Message }
        return @{ Ok = $false; Reason = ('TCP 连接失败（' + $where + '）：' + $msg) }
    } finally {
        if ($null -ne $client) { try { $client.Dispose() } catch { } }
    }
}

function Get-MtAutoJsHeaderValue {
    <#
    .SYNOPSIS
        从 Invoke-WebRequest 的响应头里取一个值（取不到返回空串）。两种字典形态都兼容。
    #>
    [CmdletBinding()]
    param($Headers, [Parameter(Mandatory)][string]$Name)

    if ($null -eq $Headers) { return '' }
    try {
        $v = $Headers[$Name]
        if ($null -ne $v) { return ((@($v) | ForEach-Object { [string]$_ }) -join ', ') }
    } catch {
    }
    try {
        foreach ($p in @($Headers.PSObject.Properties)) {
            if ($p.Name -ieq $Name -and $null -ne $p.Value) {
                return ((@($p.Value) | ForEach-Object { [string]$_ }) -join ', ')
            }
        }
    } catch {
    }
    return ''
}

function Send-MtAutoJsHttp {
    <#
    .SYNOPSIS
        发一次 JSON-RPC POST。返回 @{ StatusCode; Body; ContentType; SessionId; Error }，**绝不抛**。

    .NOTES
        · -SkipHttpErrorCheck：4xx/5xx 也要拿到状态码与 body（会话失效要靠它判定），不能抛。
        · -NoProxy：走本机回环地址，绕开系统代理（代理会把 127.0.0.1 变成 502，
          这类假阴性最难查 —— 报错长得像「端点没起来」）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$EndpointUrl,
        [Parameter(Mandatory)][string]$JsonBody,
        [hashtable]$ExtraHeaders = @{},
        [int]$TimeoutMs = 15000
    )

    $headers = @{ 'Accept' = 'application/json, text/event-stream' }
    foreach ($k in $ExtraHeaders.Keys) { $headers[$k] = $ExtraHeaders[$k] }

    $sec = [math]::Max(1, [int][math]::Ceiling($TimeoutMs / 1000.0))
    try {
        $resp = Invoke-WebRequest -Uri $EndpointUrl -Method Post -Body ([System.Text.Encoding]::UTF8.GetBytes($JsonBody)) -ContentType 'application/json' -Headers $headers -TimeoutSec $sec -SkipHttpErrorCheck -NoProxy -ErrorAction Stop
    } catch {
        return @{ StatusCode = 0; Body = ''; ContentType = ''; SessionId = ''; Error = $_.Exception.Message }
    }

    return @{
        StatusCode  = [int]$resp.StatusCode
        Body        = [string]$resp.Content
        ContentType = (Get-MtAutoJsHeaderValue -Headers $resp.Headers -Name 'Content-Type')
        SessionId   = (Get-MtAutoJsHeaderValue -Headers $resp.Headers -Name 'Mcp-Session-Id')
        Error       = ''
    }
}

function ConvertFrom-MtAutoJsRpcBody {
    <#
    .SYNOPSIS
        把响应体解析成一条 JSON-RPC 消息：application/json 取本体，
        text/event-stream 取**最后一条** data: 里的 JSON-RPC 消息。

    .OUTPUTS
        @{ Msg = <hashtable 或 $null>; Kind = 'json' | 'sse' | $null; Raw = <原文> }
    #>
    [CmdletBinding()]
    param(
        [AllowEmptyString()][string]$Body = '',
        [AllowEmptyString()][string]$ContentType = ''
    )

    $texts = @()
    $isSse = ($ContentType -match 'text/event-stream') -or ($Body -match '(?m)^\s*data:')
    if ($isSse) {
        foreach ($ln in ($Body -split '\r?\n')) {
            $t = $ln.Trim()
            if ($t.StartsWith('data:')) { $texts += $t.Substring(5).Trim() }
        }
    } elseif ($Body -and $Body.Trim()) {
        $texts += $Body.Trim()
    }

    $msgs = @()
    foreach ($t in $texts) {
        if (-not $t) { continue }
        if ($t -eq '[DONE]') { continue }
        try { $msgs += ($t | ConvertFrom-Json -AsHashtable -ErrorAction Stop) } catch { }
    }
    $kind = if ($isSse) { 'sse' } else { 'json' }
    if ($msgs.Count -eq 0) {
        return @{ Msg = $null; Kind = $kind; Raw = $Body }
    }

    # SSE 里可能先是 notifications/*、最后才是本次结果 ⇒ 从后往前找带 result/error 的那一条。
    $pick = $null
    for ($i = $msgs.Count - 1; $i -ge 0; $i--) {
        $m = $msgs[$i]
        if ($m -is [hashtable] -and ($m.ContainsKey('result') -or $m.ContainsKey('error'))) { $pick = $m; break }
    }
    if ($null -eq $pick) { $pick = $msgs[$msgs.Count - 1] }
    return @{ Msg = $pick; Kind = $kind; Raw = $Body }
}

# ── JSON-RPC 信封 / 结果归一化 ─────────────────────────────────────────────

function New-MtAutoJsRpcRequest {
    <#
    .SYNOPSIS
        生成 JSON-RPC 2.0 请求体；-Notification 时不带 id（用于 notifications/initialized）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Method,
        $Params = $null,
        [int]$Id = 0,
        [switch]$Notification
    )

    $obj = [ordered]@{ jsonrpc = '2.0' }
    if (-not $Notification) { $obj['id'] = $Id }
    $obj['method'] = $Method
    if ($null -ne $Params) { $obj['params'] = $Params }
    return ($obj | ConvertTo-Json -Depth 20 -Compress)
}

function New-MtAutoJsFailure {
    <#
    .SYNOPSIS
        失败结果的唯一形状：@{ Ok=$false; Error=…; Stack=…; Diagnostics=… }。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$ErrorText,
        [AllowEmptyString()][string]$Stack = '',
        $Diagnostics = @{}
    )

    if ($null -eq $Diagnostics) { $Diagnostics = @{} }
    return @{ Ok = $false; Error = $ErrorText; Stack = $Stack; Diagnostics = $Diagnostics }
}

function Initialize-MtAutoJsSession {
    <#
    .SYNOPSIS
        initialize + notifications/initialized（已握手且端点未变则直接复用缓存）。

    .OUTPUTS
        @{ Ok = $true; SessionId; ProtocolVersion; ServerInfo } 或失败形状。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$EndpointUrl,
        [int]$TimeoutMs = 15000,
        [switch]$Force
    )

    if (-not $Force -and $script:Session.Initialized -and $script:Session.EndpointUrl -eq $EndpointUrl) {
        return @{ Ok = $true; SessionId = $script:Session.SessionId; ProtocolVersion = $script:Session.ProtocolVersion; Cached = $true }
    }

    $params = [ordered]@{
        protocolVersion = $script:ProtocolVersion
        capabilities    = [ordered]@{}
        clientInfo      = [ordered]@{ name = $script:ClientName; version = '1.0.0' }
    }
    $r = Send-MtAutoJsHttp -EndpointUrl $EndpointUrl -TimeoutMs $TimeoutMs -JsonBody (New-MtAutoJsRpcRequest -Method 'initialize' -Params $params -Id 1) -ExtraHeaders (Get-MtAutoJsAuthHeaders)

    if ($r.Error) {
        return (New-MtAutoJsFailure -ErrorText ('initialize 失败（传输层）：' + $r.Error) -Diagnostics @{ Phase = 'initialize'; EndpointUrl = $EndpointUrl })
    }
    if ($r.StatusCode -ge 400) {
        return (New-MtAutoJsFailure -ErrorText ('initialize 失败：HTTP ' + $r.StatusCode) -Diagnostics @{ Phase = 'initialize'; StatusCode = $r.StatusCode })
    }

    $parsed = ConvertFrom-MtAutoJsRpcBody -Body $r.Body -ContentType $r.ContentType
    if ($null -eq $parsed.Msg) {
        return (New-MtAutoJsFailure -ErrorText 'initialize 应答里没有 JSON-RPC 消息' -Diagnostics @{ Phase = 'initialize'; Body = $r.Body })
    }
    if ($parsed.Msg -is [hashtable] -and $parsed.Msg.ContainsKey('error')) {
        $errObj = $parsed.Msg['error']
        $msg = if ($errObj -is [hashtable] -and $errObj.ContainsKey('message')) { [string]$errObj['message'] } else { '未知 JSON-RPC 错误' }
        return (New-MtAutoJsFailure -ErrorText ('initialize 失败：JSON-RPC 错误 ' + $msg) -Diagnostics @{ Phase = 'initialize'; Error = $errObj })
    }

    $protocol = $script:ProtocolVersion
    $serverInfo = $null
    if ($parsed.Msg -is [hashtable] -and $parsed.Msg.ContainsKey('result') -and $parsed.Msg['result'] -is [hashtable]) {
        $result = $parsed.Msg['result']
        if ($result.ContainsKey('protocolVersion') -and $result['protocolVersion']) { $protocol = [string]$result['protocolVersion'] }
        if ($result.ContainsKey('serverInfo')) { $serverInfo = $result['serverInfo'] }
    }

    $sessionId = [string]$r.SessionId
    $notifyHeaders = @{}
    $authHeaders = Get-MtAutoJsAuthHeaders
    foreach ($k in $authHeaders.Keys) { $notifyHeaders[$k] = $authHeaders[$k] }
    if ($sessionId) { $notifyHeaders['Mcp-Session-Id'] = $sessionId }
    if ($protocol) { $notifyHeaders['MCP-Protocol-Version'] = $protocol }

    $n = Send-MtAutoJsHttp -EndpointUrl $EndpointUrl -TimeoutMs $TimeoutMs -JsonBody (New-MtAutoJsRpcRequest -Method 'notifications/initialized' -Notification) -ExtraHeaders $notifyHeaders
    if ($n.Error) {
        return (New-MtAutoJsFailure -ErrorText ('notifications/initialized 失败（传输层）：' + $n.Error) -Diagnostics @{ Phase = 'notifications/initialized'; EndpointUrl = $EndpointUrl })
    }
    if ($n.StatusCode -ge 400) {
        return (New-MtAutoJsFailure -ErrorText ('notifications/initialized 失败：HTTP ' + $n.StatusCode) -Diagnostics @{ Phase = 'notifications/initialized'; StatusCode = $n.StatusCode })
    }

    $script:Session.EndpointUrl = $EndpointUrl
    $script:Session.SessionId = $sessionId
    $script:Session.ProtocolVersion = $protocol
    $script:Session.Initialized = $true
    return @{ Ok = $true; SessionId = $sessionId; ProtocolVersion = $protocol; ServerInfo = $serverInfo; Cached = $false }
}

function Reset-MtAutoJsSession {
    <#
    .SYNOPSIS
        清掉会话 id 缓存（切换线路 / 重启游戏 / 端点换端口之后调用）。

    .NOTES
        缓存的存在意义只有一个：服务端重启后旧 Mcp-Session-Id 会被拒（4xx），
        Invoke-MtAutoJsTool 会**自动**重握手一次；这里给的是「我明确知道换了端点」时的手动清空。
    #>
    [CmdletBinding()]
    param()

    $script:Session.EndpointUrl = ''
    $script:Session.SessionId = ''
    $script:Session.ProtocolVersion = ''
    $script:Session.Initialized = $false
    return $true
}

function ConvertFrom-MtAutoJsJsonText {
    <#
    .SYNOPSIS
        把 eval_js 返回的 JSON 文本反序列化（只在「像 JSON」时才试；失败返回 $null，绝不抛）。

    .NOTES
        AutoJS 对**非标量**返回值一律以字符串送回（报文 "type":"string"），所以 -Mode Json 的
        JSON.stringify 包裹拿回来的是 JSON 文本，必须再解一次才拿得到结构。
        判定口径（需求书 §3）：type=string 且首尾是 { } / [ ]，或字面量 true/false/null/数字。
        ⚠️ 与 -Mode Bool 的分工：布尔判定**不能**靠 JSON.stringify(false)（那得到字符串 "false"，
        非空即真值），必须让 AutoJS 侧直接返回 bool。
    #>
    [CmdletBinding()]
    param([AllowNull()]$Value, [AllowEmptyString()][string]$Type = '')

    if ($null -eq $Value) { return $null }
    if ($Value -isnot [string]) { return $null }
    if ($Type -and $Type -ne 'string') { return $null }
    $text = ([string]$Value).Trim()
    if (-not $text) { return $null }
    $looksJson = $false
    if (($text.StartsWith('{') -and $text.EndsWith('}')) -or ($text.StartsWith('[') -and $text.EndsWith(']'))) {
        $looksJson = $true
    } elseif ($text -eq 'true' -or $text -eq 'false' -or $text -eq 'null') {
        $looksJson = $true
    } elseif ($text -match '^-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+\-]?[0-9]+)?$') {
        $looksJson = $true
    }
    if (-not $looksJson) { return $null }
    try {
        return ($text | ConvertFrom-Json -AsHashtable -ErrorAction Stop)
    } catch {
        return $null
    }
}
function ConvertFrom-MtAutoJsToolResult {
    <#
    .SYNOPSIS
        把 tools/call 的 JSON-RPC 结果归一化成「已解析的对象」。

    .NOTES
        三层漏斗：① isError ⇒ 失败；② content[].text 的 JSON 里 ok=false ⇒ 失败；
        ③ 其余 ⇒ 成功，并把 payload 的关键字段摊到顶层（Side/ElapsedMs/Type/Value/Diagnostics），
        调用方因此不需要知道 MCP 的信封长什么样。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$ToolName,
        $Message
    )

    if ($null -eq $Message) {
        return (New-MtAutoJsFailure -ErrorText ('tools/call ' + $ToolName + ' 的应答里没有 JSON-RPC 消息') -Diagnostics @{ Phase = 'tools/call'; Tool = $ToolName })
    }
    if ($Message -is [hashtable] -and $Message.ContainsKey('error')) {
        $errObj = $Message['error']
        $msg = if ($errObj -is [hashtable] -and $errObj.ContainsKey('message')) { [string]$errObj['message'] } else { '未知 JSON-RPC 错误' }
        $stack = if ($errObj -is [hashtable] -and $errObj.ContainsKey('data')) { [string]$errObj['data'] } else { '' }
        return (New-MtAutoJsFailure -ErrorText ('tools/call ' + $ToolName + ' 失败：JSON-RPC 错误 ' + $msg) -Stack $stack -Diagnostics @{ Phase = 'tools/call'; Tool = $ToolName; Error = $errObj })
    }
    if (-not ($Message -is [hashtable]) -or -not $Message.ContainsKey('result')) {
        return (New-MtAutoJsFailure -ErrorText ('tools/call ' + $ToolName + ' 的应答缺少 result') -Diagnostics @{ Phase = 'tools/call'; Tool = $ToolName })
    }

    $result = $Message['result']
    $isError = $false
    $texts = @()
    $structured = $null
    if ($result -is [hashtable]) {
        if ($result.ContainsKey('isError')) { $isError = [bool]$result['isError'] }
        if ($result.ContainsKey('structuredContent')) { $structured = $result['structuredContent'] }
        foreach ($c in @($result['content'])) {
            if ($c -is [hashtable] -and $c.ContainsKey('text') -and $null -ne $c['text']) { $texts += [string]$c['text'] }
        }
    }

    $payload = $null
    foreach ($t in $texts) {
        if (-not $t.Trim()) { continue }
        try {
            $payload = ($t | ConvertFrom-Json -AsHashtable -ErrorAction Stop)
            if ($null -ne $payload) { break }
        } catch {
            $payload = $null
        }
    }
    if ($null -eq $payload -and $null -ne $structured) { $payload = $structured }

    if ($null -eq $payload) {
        if ($isError) {
            return (New-MtAutoJsFailure -ErrorText ('tools/call ' + $ToolName + ' 报错（isError=true，且 content 里没有 JSON 负载）') -Stack (@($texts) -join [string][char]10) -Diagnostics @{ Phase = 'tools/call'; Tool = $ToolName })
        }
        # 非 JSON 的纯文本结果：当成成功，把文本放进 Value
        $textValue = (@($texts) -join [string][char]10)
        return @{ Ok = $true; Tool = $ToolName; Type = 'text'; Value = $textValue; ValueParsed = (ConvertFrom-MtAutoJsJsonText -Value $textValue); Side = ''; ElapsedMs = 0; Diagnostics = $null; Payload = $result; ProtocolVersion = [string]$script:Session.ProtocolVersion }
    }

    $ok = -not $isError
    if ($payload -is [hashtable] -and $payload.ContainsKey('ok')) { $ok = [bool]$payload['ok'] }
    if (-not $ok) {
        $errText = 'tools/call ' + $ToolName + ' 失败'
        $stack = ''
        $diag = $null
        if ($payload -is [hashtable]) {
            if ($payload.ContainsKey('error') -and $payload['error']) { $errText = [string]$payload['error'] }
            if ($payload.ContainsKey('stack') -and $payload['stack']) { $stack = [string]$payload['stack'] }
            if ($payload.ContainsKey('diagnostics')) { $diag = $payload['diagnostics'] }
        }
        if ($null -eq $diag) { $diag = @{ Phase = 'tools/call'; Tool = $ToolName } }
        return (New-MtAutoJsFailure -ErrorText $errText -Stack $stack -Diagnostics $diag)
    }

    $side = ''
    $elapsed = 0
    $type = ''
    $value = $null
    $diagOk = $null
    if ($payload -is [hashtable]) {
        if ($payload.ContainsKey('side')) { $side = [string]$payload['side'] }
        if ($payload.ContainsKey('elapsedMs')) { $elapsed = [int]$payload['elapsedMs'] }
        if ($payload.ContainsKey('type')) { $type = [string]$payload['type'] }
        if ($payload.ContainsKey('value')) { $value = $payload['value'] }
        if ($payload.ContainsKey('diagnostics')) { $diagOk = $payload['diagnostics'] }
    }
    return @{
        Ok          = $true
        Tool        = $ToolName
        Side        = $side
        ElapsedMs   = $elapsed
        Type        = $type
        Value       = $value
        ValueParsed = (ConvertFrom-MtAutoJsJsonText -Value $value -Type $type)
        Diagnostics = $diagOk
        Payload     = $payload
        ProtocolVersion = [string]$script:Session.ProtocolVersion
    }
}

# ── 公开 API ──────────────────────────────────────────────────────────────

function Test-MtAutoJsEndpoint {
    <#
    .SYNOPSIS
        端点就绪探测：TCP 连通 → initialize 握手 → tools/call status，三步任一失败即 Ok=$false。

    .DESCRIPTION
        以 MT_AUTOJS_TIMEOUT_MS（默认 15000ms）为总预算、-PollMs（默认 500ms）为间隔**轮询重试** ——
        游戏从冷启动到 MCP 监听之间有一段空窗，单次探测会把「还没起来」误判成「起不来」。
        **绝不抛**：任何异常都折成 Ok=$false（调用方据此记 BLOCKED，而不是 FAIL）。

    .OUTPUTS
        [pscustomobject]：Ok / Reason / EndpointUrl / Host / Port / TimeoutMs / PollMs / Attempts /
        ElapsedMs / TcpOk / InitializeOk / StatusOk / Status / ProtocolVersion / Diagnostics。
        ProtocolVersion 来自 initialize 应答（取不到留空串）：mt_launch 的闸门机器行靠它印出协议版本。
    #>
    [CmdletBinding()]
    param(
        [string]$EndpointUrl = '',
        [int]$TimeoutMs = 0,
        [int]$PollMs = 500
    )

    $ep = Get-MtAutoJsEndpointUrl -EndpointUrl $EndpointUrl
    $ms = Get-MtAutoJsTimeoutMs -TimeoutMs $TimeoutMs
    $poll = if ($PollMs -gt 0) { $PollMs } else { $script:DefaultPollMs }
    $parts = ConvertTo-MtAutoJsEndpointParts -Url $ep
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $state = @{
        Ok           = $false
        Reason       = '未探测'
        Attempts     = 0
        TcpOk        = $false
        InitializeOk = $false
        StatusOk     = $false
        Status       = $null
        Host         = ''
        Port         = 0
        Diagnostics  = @{}
    }

    if ($null -eq $parts) {
        $sw.Stop()
        return [pscustomobject]@{
            Ok = $false; Reason = ('端点 URL 非法：' + $ep); EndpointUrl = $ep; Host = ''; Port = 0
            TimeoutMs = $ms; PollMs = $poll; Attempts = 0; ElapsedMs = 0
            TcpOk = $false; InitializeOk = $false; StatusOk = $false; Status = $null
            ProtocolVersion = [string]$script:Session.ProtocolVersion
            Diagnostics = @{ Phase = 'parse'; EndpointUrl = $ep }
        }
    }
    $state.Host = $parts.Host
    $state.Port = $parts.Port

    try {
        $deadline = [DateTime]::UtcNow.AddMilliseconds($ms)
        while ($true) {
            $state.Attempts++
            $remainMs = [int][math]::Max(200, ($deadline - [DateTime]::UtcNow).TotalMilliseconds)
            $tcpMs = [math]::Min($remainMs, 2000)

            $tcp = Test-MtAutoJsTcpPort -HostName $parts.Host -Port $parts.Port -TimeoutMs $tcpMs
            $state.TcpOk = [bool]$tcp.Ok
            if (-not $tcp.Ok) {
                $state.Reason = $tcp.Reason
                $state.Diagnostics = @{ Phase = 'tcp'; EndpointUrl = $ep; Host = $parts.Host; Port = $parts.Port }
            } else {
                $initTimeout = [int][math]::Max(1000, ($deadline - [DateTime]::UtcNow).TotalMilliseconds)
                $init = Initialize-MtAutoJsSession -EndpointUrl $ep -TimeoutMs $initTimeout -Force
                $state.InitializeOk = [bool]$init.Ok
                if (-not $init.Ok) {
                    $state.Reason = [string]$init.Error
                    $state.Diagnostics = $init.Diagnostics
                } else {
                    $statusTimeout = [int][math]::Max(1000, ($deadline - [DateTime]::UtcNow).TotalMilliseconds)
                    $status = Invoke-MtAutoJsTool -Name 'status' -Arguments @{} -TimeoutMs $statusTimeout -EndpointUrl $ep
                    $state.StatusOk = [bool]$status.Ok
                    if (-not $status.Ok) {
                        $state.Reason = [string]$status.Error
                        $state.Diagnostics = $status.Diagnostics
                    } else {
                        $state.Ok = $true
                        $state.Reason = ''
                        $state.Status = $status
                        break
                    }
                }
            }

            if (([DateTime]::UtcNow.AddMilliseconds($poll)) -ge $deadline) { break }
            Start-Sleep -Milliseconds $poll
        }
    } catch {
        $state.Ok = $false
        $state.Reason = ('探测过程异常：' + $_.Exception.Message)
        $state.Diagnostics = @{ Phase = 'probe'; EndpointUrl = $ep }
    }

    $sw.Stop()
    return [pscustomobject]@{
        Ok           = [bool]$state.Ok
        Reason       = [string]$state.Reason
        EndpointUrl  = $ep
        Host         = $state.Host
        Port         = $state.Port
        TimeoutMs    = $ms
        PollMs       = $poll
        Attempts     = [int]$state.Attempts
        ElapsedMs    = [int]$sw.ElapsedMilliseconds
        TcpOk        = [bool]$state.TcpOk
        InitializeOk = [bool]$state.InitializeOk
        StatusOk     = [bool]$state.StatusOk
        Status       = $state.Status
        ProtocolVersion = [string]$script:Session.ProtocolVersion
        Diagnostics  = $state.Diagnostics
    }
}

function Invoke-MtAutoJsTool {
    <#
    .SYNOPSIS
        调用一个 AutoJS 工具（完整 JSON-RPC 流程：initialize → notifications/initialized → tools/call）。

    .PARAMETER Name
        status / eval_js / kubejs_log / reload_scripts（服务端只有这四个）。

    .PARAMETER Arguments
        工具的 params（原样进 JSON-RPC params.arguments）：eval_js 用 @{code=…; side=…; timeout_ms=…}。

    .NOTES
        · 会话 id 缓存失效（HTTP 4xx）时**自动重新握手并重试一次**（服务端重启的典型症状）；
        · 5xx / 传输层失败不重试（重试只会把同样的错再打一遍日志）；
        · 返回已解析对象（见 ConvertFrom-MtAutoJsToolResult），失败时是 @{Ok=$false; Error; Stack; Diagnostics}。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][ValidateSet('status', 'eval_js', 'kubejs_log', 'reload_scripts')][string]$Name,
        [hashtable]$Arguments = @{},
        [int]$TimeoutMs = 0,
        [string]$EndpointUrl = ''
    )

    $ep = Get-MtAutoJsEndpointUrl -EndpointUrl $EndpointUrl
    $ms = Get-MtAutoJsTimeoutMs -TimeoutMs $TimeoutMs

    try {
        for ($attempt = 1; $attempt -le 2; $attempt++) {
            $init = Initialize-MtAutoJsSession -EndpointUrl $ep -TimeoutMs $ms -Force:($attempt -gt 1)
            if (-not $init.Ok) {
                return (New-MtAutoJsFailure -ErrorText ([string]$init.Error) -Stack ([string]$init.Stack) -Diagnostics $init.Diagnostics)
            }

            $headers = Get-MtAutoJsAuthHeaders
            if ($script:Session.SessionId) { $headers['Mcp-Session-Id'] = $script:Session.SessionId }
            if ($script:Session.ProtocolVersion) { $headers['MCP-Protocol-Version'] = $script:Session.ProtocolVersion }

            $params = [ordered]@{ name = $Name; arguments = $Arguments }
            $r = Send-MtAutoJsHttp -EndpointUrl $ep -TimeoutMs $ms -JsonBody (New-MtAutoJsRpcRequest -Method 'tools/call' -Params $params -Id 2) -ExtraHeaders $headers

            if ($r.Error) {
                return (New-MtAutoJsFailure -ErrorText ('tools/call ' + $Name + ' 失败（传输层）：' + $r.Error) -Diagnostics @{ Phase = 'tools/call'; Tool = $Name; EndpointUrl = $ep })
            }
            if ($r.StatusCode -ge 400 -and $r.StatusCode -lt 500) {
                Reset-MtAutoJsSession
                if ($attempt -lt 2) {
                    Write-MtAutoJsLine ('MT_AUTOJS: 会话失效（tools/call ' + $Name + ' → HTTP ' + $r.StatusCode + '），重新握手后重试一次')
                    continue
                }
                return (New-MtAutoJsFailure -ErrorText ('tools/call ' + $Name + ' 失败：HTTP ' + $r.StatusCode + '（重新握手后仍被拒）') -Diagnostics @{ Phase = 'tools/call'; Tool = $Name; StatusCode = $r.StatusCode })
            }
            if ($r.StatusCode -ge 500) {
                return (New-MtAutoJsFailure -ErrorText ('tools/call ' + $Name + ' 失败：HTTP ' + $r.StatusCode) -Diagnostics @{ Phase = 'tools/call'; Tool = $Name; StatusCode = $r.StatusCode })
            }

            $parsed = ConvertFrom-MtAutoJsRpcBody -Body $r.Body -ContentType $r.ContentType
            return (ConvertFrom-MtAutoJsToolResult -ToolName $Name -Message $parsed.Msg)
        }
        return (New-MtAutoJsFailure -ErrorText ('tools/call ' + $Name + ' 失败：重试后仍未拿到应答') -Diagnostics @{ Phase = 'tools/call'; Tool = $Name })
    } catch {
        return (New-MtAutoJsFailure -ErrorText ('tools/call ' + $Name + ' 异常：' + $_.Exception.Message) -Stack ([string]$_.ScriptStackTrace) -Diagnostics @{ Phase = 'tools/call'; Tool = $Name; EndpointUrl = $ep })
    }
}

function Invoke-MtAutoJsEval {
    <#
    .SYNOPSIS
        在指定侧求值一段 JS（工具 eval_js）。返回已解析对象（Value 就是 JS 的返回值）。

    .NOTES
        side 口径：世界状态/权威数据用 server；UI/输入/HUD/FPS 用 client；拿不准用 auto（先 server）。
        ⚠️ 集成服务器的 server 侧**要等世界就绪**才存在 ⇒ side=server 的求值必须发生在就绪闸门之后。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Code,
        [ValidateSet('auto', 'server', 'client')][string]$Side = 'auto',
        [ValidateSet('Raw', 'Json', 'Bool')][string]$Mode = 'Raw',
        [int]$TimeoutMs = 0,
        [string]$EndpointUrl = ''
    )

    # Mode 只负责「发出去之前怎么包」，不改变 code 本身的语义：
    #   Json ⇒ JSON.stringify( (<code>) )：AutoJS 对非标量只回字符串，包一层才拿得到结构（见 ConvertFrom-MtAutoJsJsonText）
    #   Bool ⇒ !!( <code> )：布尔判定必须落到真 bool 上，字符串 "false" 会被判真值
    #   Raw  ⇒ 原样（向后兼容）
    $wrapped = $Code
    if ($Mode -eq 'Json') { $wrapped = ('JSON.stringify((' + $Code + '))') }
    elseif ($Mode -eq 'Bool') { $wrapped = ('!!(' + $Code + ')') }

    $toolArgs = @{ code = $wrapped; side = $Side }
    if ($TimeoutMs -gt 0) { $toolArgs['timeout_ms'] = $TimeoutMs }
    return (Invoke-MtAutoJsTool -Name 'eval_js' -Arguments $toolArgs -TimeoutMs $TimeoutMs -EndpointUrl $EndpointUrl)
}

function Get-MtAutoJsStatus {
    <#
    .SYNOPSIS
        读端点状态（工具 status）：哪些侧活着、监听地址、版本、限额。
    #>
    [CmdletBinding()]
    param([int]$TimeoutMs = 0, [string]$EndpointUrl = '')

    return (Invoke-MtAutoJsTool -Name 'status' -Arguments @{} -TimeoutMs $TimeoutMs -EndpointUrl $EndpointUrl)
}

function Get-MtAutoJsKubejsLog {
    <#
    .SYNOPSIS
        读 KubeJS 日志（工具 kubejs_log）。
    #>
    [CmdletBinding()]
    param(
        [ValidateSet('auto', 'server', 'client')][string]$Side = 'server',
        [string]$Kind = '',
        [int]$Limit = 0,
        [int]$TimeoutMs = 0,
        [string]$EndpointUrl = ''
    )

    $toolArgs = @{ side = $Side }
    if ($Kind) { $toolArgs['kind'] = $Kind }
    if ($Limit -gt 0) { $toolArgs['limit'] = $Limit }
    return (Invoke-MtAutoJsTool -Name 'kubejs_log' -Arguments $toolArgs -TimeoutMs $TimeoutMs -EndpointUrl $EndpointUrl)
}

function Invoke-MtAutoJsReload {
    <#
    .SYNOPSIS
        热重载脚本（工具 reload_scripts）。运行期**不能**新建注册表条目，新增注册只能重启。
    #>
    [CmdletBinding()]
    param(
        [ValidateSet('auto', 'server', 'client')][string]$Side = 'auto',
        [int]$TimeoutMs = 0,
        [string]$EndpointUrl = ''
    )

    $toolArgs = @{ side = $Side }
    if ($TimeoutMs -gt 0) { $toolArgs['timeout_ms'] = $TimeoutMs }
    return (Invoke-MtAutoJsTool -Name 'reload_scripts' -Arguments $toolArgs -TimeoutMs $TimeoutMs -EndpointUrl $EndpointUrl)
}

Export-ModuleMember -Function @(
    'Test-MtAutoJsEndpoint', 'Invoke-MtAutoJsTool',
    'Invoke-MtAutoJsEval', 'Get-MtAutoJsStatus', 'Get-MtAutoJsKubejsLog', 'Invoke-MtAutoJsReload',
    'Reset-MtAutoJsSession', 'Get-MtAutoJsEndpointUrl', 'Get-MtAutoJsTimeoutMs'
)
