#Requires -Version 7.0
<#
.SYNOPSIS
    静态守门：探针命令参数形态 ↔ 用例注入命令 一致性校验器（纯离线、只读）。

.DESCRIPTION
    背景（2026-09-20 实跑取证）：`astral_bugfix_probe.js` 曾把 `zhaoplay` 的 `item` 注册成
    `StringArg.word()`（= Brigadier `readUnquotedString`，允许字符集 `[0-9A-Za-z_-.+]`，**不含** `:`
    与 `"`），而用例按同族约定写成 `"astral_dice:fu_card"` ⇒ 游戏内解析失败、**命令根本没执行**，
    `AP_Z7..ZG` / `AP_K1..` 读数整段缺失、连带 20+ 断言假 FAIL，且第一次被误判成「探针崩溃」。
    本脚本把这一类不一致**在跑游戏之前**判死。

    判据（全部机械可核验，命令/参数清单**从探针文件派生**，不硬编码）：
      1. 解析探针 `astralprobe` 命令树：按**括号深度**扫描 `.then(Commands.literal("X")` /
         `.then(Commands.argument("Y", <ArgType>(…))`，得到「子命令 → 有序参数（名 + 类型）」树；
      2. 解析 4 个在册用例里 `inject_command` 步骤的 `/astralprobe …`（其余命令跳过）；
      3. 逐 token 判定：`word()` 值含 `:`/引号、`string()` 值未加成对引号、引号未闭合、
         实参个数不足/多余、未注册的子命令名 ⇒ 一律 `verdict=UNPARSEABLE`；
      4. 词法上可解析但类型未被本脚本识别 ⇒ `verdict=UNKNOWN`（**不得静默放行**，具名回显）。

    退出码：`0` = 全一致；`1` = 发现不一致；`2` = 无法判定（文件缺失 / 树解析不出 / 脚本自身异常）。

.OUTPUTS
    具名行（可直接 grep 统计）：
      APCA_PROBE:version=<v>:path=<p>:subcommands=<n>:arguments=<m>
      APCA_TREE:version=<v>:sub=<name>:args=<name:type,…>
      APCA_UNKNOWN_TYPE:version=<v>:sub=<name>:arg=<name>:type=<expr>
      APCA_CASE:case=<id>:version=<v>:commands=<n>
      APCA_CMD:case=<id>:sub=<name>:arg=<name>:type=<t>:value=<v>:verdict=<verdict>[:reason=<r>]
      APCA_SUMMARY:cases=<n>:commands=<n>:ok=<n>:mismatch=<n>:unknown=<n>:verdict=<PASS|FAIL|UNKNOWN>
      APCA_RESULT: <PASS|FAIL|UNKNOWN> — <一句话>

.PARAMETER Root
    仓库根目录。缺省由 `$PSScriptRoot` 推导（= 本脚本的上两级），**不是** `.`（cwd 可能是别的树）。
    负例夹具可指向任意目录，只要该目录下有 `scripts/test/resources/kubejs/**` 与 `scripts/test/cases/**`。

.NOTES
    · 纯离线只读：不跑 gradle、不进游戏、不写 `run/**`、不修改任何探针/用例/产品源码。
    · 与 `tools/check_mod_sources.ps1`（模组来源）、`verify_bountiful_pools.ps1`（赏金池）同类：
      静态守门，先于实跑给出具名结论。
    · 已知边界（见报告「遗留未覆盖的类型清单」）：只做命令行层面的词法/类型检查，不做 Brigadier
      语义检查（如 `suggests`、redirect、权限门槛 `guard(...)` 内部行为），也不校验探针是否真的发送了
      对应 `AP_*` 读数（那需要实跑）。
#>
[CmdletBinding()]
param(
    [string]$Root = ''
)

$ErrorActionPreference = 'Stop'

# ── 0. 根目录（默认由脚本位置推导，绝不用 cwd）───────────────────────────────
if (-not $Root) {
    $Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
} else {
    if (-not (Test-Path -LiteralPath $Root -PathType Container)) {
        Write-Host "APCA_ERROR: root-not-found:$Root"
        Write-Host 'APCA_RESULT: UNKNOWN — 指定根目录不存在'
        exit 2
    }
    $Root = (Resolve-Path -LiteralPath $Root).Path
}
Write-Host "APCA_ROOT: $Root"

# 在册用例 → 版本（清单固定：这 4 条是本批「在册用例」）
$CaseSpecs = @(
    @{ Id = 'ZHAO-SIGN-1.21.1'; Version = '1.21.1' },
    @{ Id = 'HUO-CURSE-1.21.1'; Version = '1.21.1' },
    @{ Id = 'ZHAO-BLESSING-1.21.1'; Version = '1.21.1' },
    @{ Id = 'ZHAO-SIGN-1.20.1'; Version = '1.20.1' }
)
$Versions = @('1.21.1', '1.20.1')

# ── 1. 词法工具 ────────────────────────────────────────────────────────────
function Remove-JsComments {
    <# 去 `//` 与 `/* */` 注释（**字符串内的不误删**）——注释里也常出现 `Commands.literal("…")` 的说明文字。 #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Text)

    $sb = [System.Text.StringBuilder]::new($Text.Length)
    $i = 0; $n = $Text.Length; $inStr = $false; $quote = [char]0
    while ($i -lt $n) {
        $c = $Text[$i]
        if ($inStr) {
            [void]$sb.Append($c)
            if ($c -eq '\' -and $i + 1 -lt $n) { [void]$sb.Append($Text[$i + 1]); $i += 2; continue }
            if ($c -eq $quote) { $inStr = $false }
            $i++; continue
        }
        if ($c -eq '"' -or $c -eq "'") { $inStr = $true; $quote = $c; [void]$sb.Append($c); $i++; continue }
        if ($c -eq '/' -and $i + 1 -lt $n -and $Text[$i + 1] -eq '/') {
            while ($i -lt $n -and $Text[$i] -ne "`n") { $i++ }
            continue
        }
        if ($c -eq '/' -and $i + 1 -lt $n -and $Text[$i + 1] -eq '*') {
            $i += 2
            while ($i + 1 -lt $n -and -not ($Text[$i] -eq '*' -and $Text[$i + 1] -eq '/')) { $i++ }
            $i += 2; continue
        }
        [void]$sb.Append($c); $i++
    }
    return $sb.ToString()
}

function Get-ArgTypeClass {
    <# 把参数类型表达式归一为类型类；**按方法名后缀识别**，因此对 `StringArg` / `StringArgumentType` 等别名都成立。 #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$TypeExpr)

    # 用「方法名 + 词边界」识别（容忍 `StringArg.word()` / `StringArg.word` 两种写法，
    # 也因此对 `StringArg` / `StringArgumentType` 之类别名一律成立）。
    if ($TypeExpr -match '\.greedyString\b') { return 'greedyString' }
    if ($TypeExpr -match '\.word\b') { return 'word' }
    if ($TypeExpr -match '\.string\b') { return 'string' }
    if ($TypeExpr -match '\.bool\b') { return 'bool' }
    if ($TypeExpr -match '\.integer\b') { return 'integer' }
    if ($TypeExpr -match '\.double\b') { return 'double' }
    return 'unknown'
}

function Get-ArgIntBounds {
    <# 从 `IntegerArg.integer(0, 20)` 里取上下界（取不到就返回 $null，不做任何猜测）。 #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$TypeExpr)

    $m = [regex]::Match($TypeExpr, '\.integer\s*\(\s*(-?\d+)\s*,\s*(-?\d+)\s*\)')
    if ($m.Success) { return @([long]$m.Groups[1].Value, [long]$m.Groups[2].Value) }
    return $null
}

function Find-MatchingParen {
    <#
    .SYNOPSIS
        给定 `(` 的位置，返回与它配对的 `)` 的位置（-1 = 没找到）。
    .NOTES
        字符串 / 模板字面量 / 正则字面量里的括号**不计**（命令树区域内二者通常没有，但这里是防御性实现：
        一旦误计，整棵树的层级会整体偏移，症状是「所有子命令都判未注册」）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Text, [Parameter(Mandatory)][int]$OpenPos)

    $n = $Text.Length
    if ($OpenPos -lt 0 -or $OpenPos -ge $n -or $Text[$OpenPos] -ne '(') { return -1 }
    $d = 0; $i = $OpenPos; $inStr = $false; $quote = [char]0; $lastSig = ''
    while ($i -lt $n) {
        $c = $Text[$i]
        if ($inStr) {
            if ($c -eq '\') { $i += 2; continue }
            if ($c -eq $quote) { $inStr = $false }
            $i++; continue
        }
        if ($c -eq '`') {
            $i++
            while ($i -lt $n) { if ($Text[$i] -eq '\') { $i += 2; continue }; if ($Text[$i] -eq '`') { $i++; break }; $i++ }
            $lastSig = '`'; continue
        }
        if ($c -eq '"' -or $c -eq "'") { $inStr = $true; $quote = $c; $i++; continue }
        if ($c -eq '/' -and ($lastSig -eq '' -or ('([{,;=:!&|?+-*%^~<>'.IndexOf($lastSig) -ge 0))) {
            $i++; $inClass = $false
            while ($i -lt $n) {
                $rc = $Text[$i]
                if ($rc -eq '\') { $i += 2; continue }
                if ($rc -eq "`n") { break }
                if ($rc -eq '[') { $inClass = $true } elseif ($rc -eq ']') { $inClass = $false }
                elseif ($rc -eq '/' -and -not $inClass) { $i++; break }
                $i++
            }
            $lastSig = '/'; continue
        }
        if ($c -eq '(') { $d++ }
        elseif ($c -eq ')') { $d--; if ($d -eq 0) { return $i } }
        if (-not [char]::IsWhiteSpace($c)) { $lastSig = $c }
        $i++
    }
    return -1
}

function Get-ProbeCommandTree {
    <#
    .SYNOPSIS
        从探针源码派生 `astralprobe` 命令树：节点 = {Depth, Kind, Name, TypeExpr, TypeClass, Pos, Executes, Children}。
    .NOTES
        扫描规则：整段文本按字符走，跟踪**括号深度**（字符串内的括号不计）；遇到
        `Commands.literal("X")` / `Commands.argument("Y", <expr>(…))` 就记一个节点，
        节点的树层级 = 括号深度；父 = 深度比它小 1 的**最近一个**节点（栈式重建）。
        `.executes(` 归属**同深度的最近一个**节点（Brigadier 的链式写法：`.executes` 与它修饰的
        `.then(…)` 节点同层）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$Version)

    $raw = [System.IO.File]::ReadAllText($Path, (New-Object System.Text.UTF8Encoding($false)))
    $text = Remove-JsComments $raw

    $rootIdx = $text.IndexOf('literal("astralprobe")')
    if ($rootIdx -lt 0) { return $null }

    $nodes = [System.Collections.Generic.List[object]]::new()
    $execSites = [System.Collections.Generic.List[object]]::new()
    $depth = 0; $inStr = $false; $quote = [char]0
    $lastSig = ''          # 最近一个有效字符（用于区分「正则字面量 / 除法」）
    $base = $null          # 归一化基准 = 根节点自身的括号深度
    $i = 0; $n = $text.Length
    while ($i -lt $n) {
        $c = $text[$i]
        if ($inStr) {
            if ($c -eq '\') { $i += 2; continue }
            if ($c -eq $quote) { $inStr = $false }
            $i++; continue
        }
        # 模板字面量（反引号）：整体视作字符串（内容不计括号；本文件命令树区域内无模板）
        if ($c -eq '`') {
            $i++
            while ($i -lt $n) {
                if ($text[$i] -eq '\') { $i += 2; continue }
                if ($text[$i] -eq '`') { $i++; break }
                $i++
            }
            $lastSig = '`'
            continue
        }
        if ($c -eq '"' -or $c -eq "'") { $inStr = $true; $quote = $c; $i++; continue }
        # 正则字面量：`/` 出现在「表达式起始」位置（前一个有效字符是分隔符）⇒ 跳到收尾 `/`
        if ($c -eq '/') {
            $prevWord = ''
            $k = $i - 1
            while ($k -ge 0 -and [char]::IsLetterOrDigit($text[$k])) { $prevWord = $text[$k] + $prevWord; $k-- }
            $isRegex = ($lastSig -eq '') -or ('([{,;=:!&|?+-*%^~<>'.IndexOf($lastSig) -ge 0) -or ($prevWord -eq 'return')
            if ($isRegex) {
                $i++
                $inClass = $false
                while ($i -lt $n) {
                    $rc = $text[$i]
                    if ($rc -eq '\') { $i += 2; continue }
                    if ($rc -eq "`n") { break }
                    if ($rc -eq '[') { $inClass = $true }
                    elseif ($rc -eq ']') { $inClass = $false }
                    elseif ($rc -eq '/' -and -not $inClass) { $i++; break }
                    $i++
                }
                $lastSig = '/'
                continue
            }
        }
        if ($c -eq '(') { $depth++; $lastSig = '('; $i++; continue }
        if ($c -eq ')') { $depth--; $lastSig = ')'; $i++; continue }
        if ($c -eq '.' -and $i + 9 -le $n -and $text.Substring($i, 9) -eq '.executes') {
            $execSites.Add([pscustomobject]@{ Pos = $i; Depth = $(if ($null -ne $base) { [int]$depth - [int]$base } else { [int]$depth }) })
            $i += 9; $lastSig = ')'; continue
        }
        if ($c -eq 'C') {
            if ($i + 17 -le $n -and $text.Substring($i, 17) -eq 'Commands.literal(') {
                $q = $text.IndexOf('"', $i); $q2 = $text.IndexOf('"', $q + 1)
                if ($q -lt 0 -or $q2 -lt 0) { $i++; continue }
                if ($null -eq $base) { $base = [int]$depth }
                # 整段调用（含配对右括号）跳过去：**不改变深度**（否则每次记节点都会少一层）
                $close = Find-MatchingParen -Text $text -OpenPos ($i + 16)
                $nodes.Add([pscustomobject]@{
                        Depth = [int]$depth - [int]$base; Kind = 'literal'; Name = $text.Substring($q + 1, $q2 - $q - 1)
                        TypeExpr = ''; TypeClass = ''; Pos = $i; Executes = $false
                    })
                $i = $(if ($close -gt 0) { $close + 1 } else { $q2 + 1 })
                continue
            }
            if ($i + 18 -le $n -and $text.Substring($i, 18) -eq 'Commands.argument(') {
                $q = $text.IndexOf('"', $i); $q2 = $text.IndexOf('"', $q + 1)
                if ($q -lt 0 -or $q2 -lt 0) { $i++; continue }
                $name = $text.Substring($q + 1, $q2 - $q - 1)
                $close = Find-MatchingParen -Text $text -OpenPos ($i + 17)
                # 类型表达式 = 名称之后、逗号之后到该调用配对右括号之前（如 `StringArg.word()`）
                $typeExpr = ''
                if ($close -gt $q2) {
                    $inner = $text.Substring($q2 + 1, $close - $q2 - 1)
                    $parts = $inner -split ',', 2
                    if ($parts.Count -ge 2) { $typeExpr = $parts[1].Trim() }
                }
                if ($null -eq $base) { $base = [int]$depth }
                $nodes.Add([pscustomobject]@{
                        Depth = [int]$depth - [int]$base; Kind = 'argument'; Name = $name
                        TypeExpr = $typeExpr; TypeClass = (Get-ArgTypeClass -TypeExpr $typeExpr)
                        Pos = $i; Executes = $false
                    })
                $i = $(if ($close -gt 0) { $close + 1 } else { $q2 + 1 })
                continue
            }
        }
        if (-not [char]::IsWhiteSpace($c)) { $lastSig = $c }
        $i++
    }

    if ($nodes.Count -eq 0) { return $null }

    # `.executes(` 归属：同深度的最近一个节点（Brigadier 链式写法里 `.executes` 与它修饰的节点同层）。
    # 深度由**主扫描**就地记录（execSites），不再二次重扫 —— 重扫会被正则/模板字面量的括号干扰。
    foreach ($site in $execSites) {
        $owner = $null
        foreach ($nd in $nodes) { if ($nd.Pos -lt $site.Pos -and $nd.Depth -eq $site.Depth) { $owner = $nd } }
        if ($null -ne $owner) { $owner.Executes = $true }
    }

    # 树重建：父 = 深度减 1 的最近节点
    $stack = [System.Collections.Generic.List[object]]::new()
    foreach ($nd in $nodes) {
        while ($stack.Count -gt 0 -and $stack[$stack.Count - 1].Depth -ge $nd.Depth) { $stack.RemoveAt($stack.Count - 1) }
        $parent = if ($stack.Count -gt 0) { $stack[$stack.Count - 1] } else { $null }
        $nd | Add-Member -NotePropertyName Parent -NotePropertyValue $parent -Force
        $nd | Add-Member -NotePropertyName Children -NotePropertyValue ([System.Collections.Generic.List[object]]::new()) -Force
        if ($null -ne $parent) { $parent.Children.Add($nd) }
        $stack.Add($nd)
    }
    $root = $nodes[0]
    if ($root.Kind -ne 'literal' -or $root.Name -ne 'astralprobe') { return $null }
    return [pscustomobject]@{ Version = $Version; Path = $Path; Root = $root; Nodes = $nodes }
}

function Split-CommandLine {
    <#
    .SYNOPSIS
        把一行命令切成 token（**引号感知**）：返回 @{ Text; Quoted; Closed }。
    .NOTES
        只做 Brigadier 需要的那点词法：`"…"` 视为一个 token（内容原样，含 `\` 转义到下一个字符）。
        引号未闭合 ⇒ 该 token 标 Closed=$false，交由调用方判 UNPARSEABLE。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Line)

    $tokens = [System.Collections.Generic.List[object]]::new()
    $i = 0; $n = $Line.Length
    while ($i -lt $n) {
        while ($i -lt $n -and [char]::IsWhiteSpace($Line[$i])) { $i++ }
        if ($i -ge $n) { break }
        if ($Line[$i] -eq '"') {
            $i++
            $sb = [System.Text.StringBuilder]::new()
            $closed = $false
            while ($i -lt $n) {
                if ($Line[$i] -eq '\' -and $i + 1 -lt $n) { [void]$sb.Append($Line[$i + 1]); $i += 2; continue }
                if ($Line[$i] -eq '"') { $closed = $true; $i++; break }
                [void]$sb.Append($Line[$i]); $i++
            }
            $tokens.Add([pscustomobject]@{ Text = $sb.ToString(); Quoted = $true; Closed = $closed })
            continue
        }
        $sb = [System.Text.StringBuilder]::new()
        while ($i -lt $n -and -not [char]::IsWhiteSpace($Line[$i])) { [void]$sb.Append($Line[$i]); $i++ }
        $tokens.Add([pscustomobject]@{ Text = $sb.ToString(); Quoted = $false; Closed = $true })
    }
    return , $tokens
}

function Test-ArgValue {
    <#
    .SYNOPSIS
        按参数类型判定一个实参 token 是否**可被 Brigadier 解析**；返回 $null = 可解析，否则返回 reason 字符串。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Token, [Parameter(Mandatory)]$Arg)

    $t = [string]$Token.Text
    switch ([string]$Arg.TypeClass) {
        'word' {
            # readUnquotedString：允许集 = 字母数字 + `_-.+`；引号本身也非法 ⇒ 被引号包起来的资源位置必然失败
            if ($Token.Quoted) { return 'word-quoted(引号不在 readUnquotedString 允许集内)' }
            if ($t -match '[^0-9A-Za-z_\-\.\+]') {
                $bad = ([regex]::Match($t, '[^0-9A-Za-z_\-\.\+]')).Value
                return "word-illegal-char('$bad')"
            }
            return $null
        }
        'string' {
            # readString：必须成对引号
            if (-not $Token.Quoted) { return 'string-not-quoted(readString 需要成对引号)' }
            if (-not $Token.Closed) { return 'string-unclosed-quote' }
            return $null
        }
        'greedyString' {
            if (-not $Token.Quoted) { return 'greedy-not-quoted(readString 需要成对引号)' }
            return $null
        }
        'integer' {
            if ($t -notmatch '^-?\d+$') { return 'integer-not-numeric' }
            $b = Get-ArgIntBounds -TypeExpr ([string]$Arg.TypeExpr)
            if ($null -ne $b) {
                $v = [long]$t
                if ($v -lt $b[0] -or $v -gt $b[1]) { return "integer-out-of-bounds($($b[0])..$($b[1]))" }
            }
            return $null
        }
        'bool' {
            if ($t -notin @('true', 'false')) { return 'bool-not-boolean' }
            return $null
        }
        'double' {
            if ($t -notmatch '^-?\d+(\.\d+)?$') { return 'double-not-numeric' }
            return $null
        }
        default { return 'unknown-type' }
    }
}

function Get-CommandsFromCase {
    <# 取用例里所有 `inject_command` 且以 `/astralprobe` 开头的命令（其余命令跳过）。 #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$JsonPath)

    $j = [System.IO.File]::ReadAllText($JsonPath, (New-Object System.Text.UTF8Encoding($false))) | ConvertFrom-Json
    $out = [System.Collections.Generic.List[object]]::new()
    foreach ($s in $j.steps) {
        if ([string]$s.op -ne 'inject_command') { continue }
        $cmd = [string]$s.command
        if ([string]::IsNullOrWhiteSpace($cmd)) { continue }
        if (-not $cmd.StartsWith('/astralprobe')) { continue }
        $out.Add($cmd)
    }
    return , $out
}

function Get-NodeByLiteralName {
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Children, [Parameter(Mandatory)][string]$Name)
    foreach ($c in $Children) { if ($c.Kind -eq 'literal' -and $c.Name -eq $Name) { return $c } }
    return $null
}

# ── 2. 主流程 ──────────────────────────────────────────────────────────────
$script:Mismatch = 0
$script:OkCount = 0
$script:UnknownCount = 0
$script:CmdTotal = 0
$script:Fatal = $false

$trees = @{}
foreach ($v in $Versions) {
    $p = Join-Path $Root "scripts/test/resources/kubejs/$v/server_scripts/astral_bugfix_probe.js"
    if (-not (Test-Path -LiteralPath $p -PathType Leaf)) {
        # ⚠️ 必须写成 `${v}`：`"$v:path"` 会被 PowerShell 解析成「作用域/驱动器限定变量」而输出空串
        Write-Host "APCA_MISSING: version=${v}:path=$p"
        $script:Fatal = $true
        continue
    }
    $t = Get-ProbeCommandTree -Path $p -Version $v
    if ($null -eq $t) {
        Write-Host "APCA_PARSE_FAIL: version=${v}:path=$p:reason=未找到 astralprobe 根或未解析出任何节点"
        $script:Fatal = $true
        continue
    }
    $trees[$v] = $t
    $subCount = @($t.Nodes | Where-Object { $_.Kind -eq 'literal' -and $_.Depth -eq ($t.Root.Depth + 1) }).Count
    $argCount = @($t.Nodes | Where-Object { $_.Kind -eq 'argument' }).Count
    Write-Host ("APCA_PROBE:version={0}:path={1}:subcommands={2}:arguments={3}" -f $v, $p, $subCount, $argCount)

    # 顶层子命令的参数形态摘要（每条一行，便于与用例对照）
    $subs = @($t.Root.Children | Where-Object { $_.Kind -eq 'literal' })
    foreach ($s in $subs) {
        $args = [System.Collections.Generic.List[string]]::new()
        $cur = $s
        while ($true) {
            $nextArg = $null
            foreach ($c in $cur.Children) { if ($c.Kind -eq 'argument') { $nextArg = $c; break } }
            if ($null -eq $nextArg) { break }
            $args.Add(("{0}:{1}" -f $nextArg.Name, $nextArg.TypeClass))
            $cur = $nextArg
        }
        Write-Host ("APCA_TREE:version={0}:sub={1}:args={2}" -f $v, $s.Name, $(if ($args.Count -gt 0) { $args -join ',' } else { '(none)' }))
    }

    # 未知类型具名回显（不得静默放行）
    $seen = @{}
    foreach ($nd in ($t.Nodes | Where-Object { $_.Kind -eq 'argument' -and $_.TypeClass -eq 'unknown' })) {
        $key = "$($nd.Name)|$($nd.TypeExpr)"
        if ($seen.ContainsKey($key)) { continue }
        $seen[$key] = $true
        $owner = $nd.Parent
        $subName = if ($null -ne $owner -and $owner.Kind -eq 'literal') { $owner.Name } else { '(nested)' }
        Write-Host ("APCA_UNKNOWN_TYPE:version={0}:sub={1}:arg={2}:type={3}" -f $v, $subName, $nd.Name, $nd.TypeExpr)
    }
}

foreach ($spec in $CaseSpecs) {
    $jsonPath = Join-Path $Root "scripts/test/cases/$($spec.Id).json"
    if (-not (Test-Path -LiteralPath $jsonPath -PathType Leaf)) {
        Write-Host "APCA_MISSING: case=$($spec.Id):path=$jsonPath"
        $script:Fatal = $true
        continue
    }
    $cmds = Get-CommandsFromCase -JsonPath $jsonPath
    Write-Host ("APCA_CASE:case={0}:version={1}:commands={2}" -f $spec.Id, $spec.Version, $cmds.Count)
    $tree = $trees[$spec.Version]
    foreach ($cmd in $cmds) {
        $script:CmdTotal++
        $tokens = Split-CommandLine -Line $cmd
        # token[0] = /astralprobe
        $sub = if ($tokens.Count -gt 1) { [string]$tokens[1].Text } else { '' }
        if ($tokens.Count -le 1) {
            $script:Mismatch++
            Write-Host ("APCA_CMD:case={0}:sub=(none):arg=-:type=-:value={1}:verdict=UNPARSEABLE:reason=missing-subcommand" -f $spec.Id, $cmd)
            continue
        }
        if ($null -eq $tree) {
            $script:UnknownCount++
            Write-Host ("APCA_CMD:case={0}:sub={1}:arg=-:type=-:value={2}:verdict=UNKNOWN:reason=no-tree" -f $spec.Id, $sub, $cmd)
            continue
        }
        $node = Get-NodeByLiteralName -Children $tree.Root.Children -Name $sub
        if ($null -eq $node) {
            $script:Mismatch++
            Write-Host ("APCA_CMD:case={0}:sub={1}:arg=-:type=-:value={2}:verdict=UNPARSEABLE:reason=unregistered-subcommand" -f $spec.Id, $sub, $cmd)
            continue
        }

        $bad = $null; $badArg = $null; $badVal = $null
        $idx = 2
        while ($idx -lt $tokens.Count -and $null -eq $bad) {
            $tok = $tokens[$idx]
            $lit = $null
            if (-not $tok.Quoted) { $lit = Get-NodeByLiteralName -Children $node.Children -Name ([string]$tok.Text) }
            if ($null -ne $lit) { $node = $lit; $idx++; continue }
            $arg = $null
            foreach ($c in $node.Children) { if ($c.Kind -eq 'argument') { $arg = $c; break } }
            if ($null -eq $arg) { $bad = 'extra-token(该位置没有已注册参数)'; $badVal = $tok.Text; break }
            $r = Test-ArgValue -Token $tok -Arg $arg
            if ($null -ne $r) { $bad = $r; $badArg = $arg; $badVal = $tok.Text; break }
            $node = $arg; $idx++
        }

        if ($null -ne $bad) {
            if ($bad -eq 'unknown-type') {
                $script:UnknownCount++
                Write-Host ("APCA_CMD:case={0}:sub={1}:arg={2}:type={3}:value={4}:verdict=UNKNOWN:reason=unknown-arg-type" -f `
                        $spec.Id, $sub, $(if ($badArg) { $badArg.Name } else { '-' }), $(if ($badArg) { $badArg.TypeExpr } else { '-' }), $badVal)
            } else {
                $script:Mismatch++
                Write-Host ("APCA_CMD:case={0}:sub={1}:arg={2}:type={3}:value={4}:verdict=UNPARSEABLE:reason={5}" -f `
                        $spec.Id, $sub, $(if ($badArg) { $badArg.Name } else { '(position)' }), $(if ($badArg) { $badArg.TypeClass } else { '-' }), $badVal, $bad)
            }
            continue
        }

        # 收尾：当前节点必须**可执行**（有 .executes），否则命令写不全
        if (-not $node.Executes) {
            $hasArg = @($node.Children | Where-Object { $_.Kind -eq 'argument' }).Count -gt 0
            $reason = if ($hasArg) { "missing-argument(节点 $($node.Name) 仍有未注册实参)" } else { "incomplete-command(节点 $($node.Name) 无 .executes 也无未消费参数)" }
            $script:Mismatch++
            Write-Host ("APCA_CMD:case={0}:sub={1}:arg=-:type=-:value={2}:verdict=UNPARSEABLE:reason={3}" -f $spec.Id, $sub, $cmd, $reason)
            continue
        }
        $script:OkCount++
        Write-Host ("APCA_CMD:case={0}:sub={1}:arg=-:type=-:value={2}:verdict=OK" -f $spec.Id, $sub, $cmd)
    }
}

$verdict = if ($script:Fatal) { 'UNKNOWN' } elseif ($script:Mismatch -gt 0) { 'FAIL' } elseif ($script:UnknownCount -gt 0) { 'UNKNOWN' } else { 'PASS' }
Write-Host ("APCA_SUMMARY:cases={0}:commands={1}:ok={2}:mismatch={3}:unknown={4}:verdict={5}" -f `
        $CaseSpecs.Count, $script:CmdTotal, $script:OkCount, $script:Mismatch, $script:UnknownCount, $verdict)

switch ($verdict) {
    'PASS' { Write-Host 'APCA_RESULT: PASS — 4 个在册用例的 /astralprobe 命令与探针注册形态逐 token 一致'; exit 0 }
    'FAIL' { Write-Host ("APCA_RESULT: FAIL — 发现 {0} 处不一致（见上 APCA_CMD …verdict=UNPARSEABLE）" -f $script:Mismatch); exit 1 }
    default { Write-Host ("APCA_RESULT: UNKNOWN — 无法判定（fatal={0} / unknown-type={1}）" -f $script:Fatal, $script:UnknownCount); exit 2 }
}
