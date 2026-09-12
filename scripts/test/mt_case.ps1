#Requires -Version 7.0
<#
.SYNOPSIS
    mt_case — 测试条目执行器（阶段 C）。

.DESCRIPTION
    设计约束（AGENTS「子技能设计」）:
      1. 原语表封闭 —— 只接受 PRIMITIVES 中列出的 op，未知 op 立即 ERROR 并拒绝执行，
         不产生「半执行」的污染状态；
      2. 需要 stdio MCP 的步骤不在本脚本内直连，改为写「待执行清单」交会话层回填
         （.mcp-pending.json ↔ .mcp-results.json）；
      3. 不做视觉判断 —— vision 断言只输出提问请求，判定交视觉模型。

.EXAMPLE
    pwsh -File scripts/test/mt_case.ps1 run      --version 1.21.1 --case cases/xxx.json
    pwsh -File scripts/test/mt_case.ps1 run-dir  --version 1.21.1
    pwsh -File scripts/test/mt_case.ps1 validate --case cases/xxx.json

.NOTES
    对应源文件（迁移前）：scripts/test/mt_case.py。

    数据结构字段名一律沿用 python 侧的 snake_case（step/hold_ms/no_esc/…），
    目的是让 python → powershell 的逐行对照不需要任何名字映射。

    ## 子脚本调用映射（python `_py(script, *args)` → pwsh 子进程）

    python 版经 `mt_ps.run_py` 调 `python scripts/test/<name>.py`；pwsh 版**不再经过
    python**，改为调同名 pwsh 脚本，参数名与顺序逐项对应：

        mt_assert.py  → mt_assert.ps1    log/absent/crash/kubejs/mixin
        mt_inject.py  → mt_inject.ps1    key/cmd
        mt_capture.py → mt_capture.ps1   capture

    ## 有意为之的等价点（不是偏差，是为了逐字节一致的显式复刻）

    - **`_tail` 的子进程 `\r`**：python 的文本模式会把子进程 stdout/stderr 的 `\n` 写成
      `\r\n`，于是 `_tail` 的 `.replace("\n", " ")` 在折行处**残留 `\r`**
      （实测多行命中示例：`…@0BU\r     line1\r     line2`）。pwsh 子脚本按本仓规范只写 LF，
      因此 `Get-MtTail -FromTextMode` 显式把折行写成 `"\r "` 来复刻它 —— 否则多行 detail
      会差 N 个字节（比对器的 CRLF→LF 归一化**不会**折掉行内 `\r`；实测曾差 3 字节）。
      来自 JSON 文本（mcp 断言的 `text`）的一律走默认折法（两侧同为 LF）。
    - **`[:n]` 与 `_tail` 是两套语义**：`exec_assert` 的 mcp 分支用**裸切片** `text[:200]` /
      `text[:120]`（不 strip、不折行），故单列 `Get-MtSlice`；混用会在 text 带首尾空白或换行时不等
      （实测由夹具 `w8_mcpassert` 覆盖）。

    **无法 1:1 复刻之处（逐条）**:

    1. `--hold-ms` / `--no-esc` → `-HoldMs` / `-NoEsc`。PowerShell 的参数名**不允许
       内嵌连字符**：实测 `pwsh -File mt_inject.ps1 key --hold-ms 1000` 报
       「找不到与参数名称 '-hold-ms' 匹配的参数」并 rc=1（`--no-esc` 同理）。
       子脚本用 param() 声明的是 `-HoldMs`/`-NoEsc`（本文件不得改它们），故这里改传
       PowerShell 拼法。**出现条件与取值语义完全不变**（仍由 `hold_ms`/`no_esc` 的真值决定
       是否追加、`str(hold_ms)` 同值）。单段参数名如 `--version`/`--key`/`--command`/
       `--tag`/`--mode` 两侧同拼法，保持原样。
    2. 子进程**超时**语义：python `subprocess.run(timeout=600)` 超时抛 TimeoutExpired，
       被 `run_py` 折叠为 `(1, "", "<异常文本>")`；pwsh 侧 `Invoke-MtProcessFull` 强杀整棵
       进程树后返回进程的**实际退出码**（TimedOut=$true）。故超时分支的 rc 与 stderr 文本
       与 python 不等；正常（未超时）分支完全一致。
    3. **异常文案**：python 的 `OSError`/`json.JSONDecodeError` 文本在 pwsh 侧没有等价物。
       - **文件不存在**这一类已按 python 的 `[Errno 2] No such file or directory: '<repr>'`
         格式复刻（见 `Get-MtIoErrorText`），该分支逐字节一致；
       - 其它 IO 错误（权限/是目录）与 **所有 JSON 解析错误**是 .NET 原文
         （python 侧是 `Expecting value: line 1 column 1 (char 0)` 这类 json 私有文案）。
       仅影响 `无法读取 …：<异常>` 的冒号之后部分；**校验失败**类文案
       （`缺少必填字段 …`、`未知原语 …` 等，即验收要求的逐字对齐部分）不受影响。
       `UnicodeDecodeError`（非法 UTF-8）在 python 侧会冒泡成 traceback，这里按 U+FFFD 替换后
       继续解析（`.NET UTF8Encoding($false,$false)`）。
    4. `.mt_keep_alive` 标记文件用 **LF** 写（python `write_text` 的文本模式把 `\n`
       翻译成 CRLF）。读方 `mt_cleanup.ps1` 用 `Get-Content -Raw` + `.Trim()`，两种行尾等价；
       本仓规范换行是 LF。
    5. `MT_KEEP_ALIVE` 提示里的复跑入口 `bash scripts/test/mt.sh --phase stop` →
       `pwsh -File scripts/test/mt.ps1 --phase stop`（同一入口的新名字，沿用 mt_report.ps1 /
       mt_launch.ps1 / mt_cleanup.ps1 已确立的文案偏差先例）。
    6. `[:n]` 切片按 **UTF-16 码元**（python 按码点）：仅当第 n 个码元落在代理对中间时输出
       不同（python 会多带/少带半个代理对，pwsh 按码元切）。影响 `_tail` 的 240、
       mcp 的 200/120。另外 python 的 `.strip()` 等价于 .NET `.Trim()`
       （两者都按 Unicode 空白）。
    7. `sorted(...)` 用 **Ordinal** 序（python 按码点）：BMP 范围内完全一致。
    8. `{x!r}` 的 repr 是**近似实现**（单引号包裹 + `\\`/`\'`/`\n`/`\r`/`\t`/`\xNN` 转义）；
       非字符串类型退化为 `str()` 形态。
    9. `ConvertTo-MtJson` 对**顶层空数组**会返回 `null`（`ConvertTo-Json` 的管道会把空集合
       吞成无输出；python `json.dumps([])` 是 `[]`）。`.mcp-pending.json` 只在 append **之后**
       落盘，永远至少 1 项，故用 `(, $data)` 包装即可逐字节对齐 python 的
       `json.dumps(data, ensure_ascii=False, indent=2)`；该分支不涉及浮点字段。
   10. argparse 的 usage/错误文案未复刻：参数缺失/非法一律
       `MT_ERROR: …` + `exit $MT_EXIT_ERROR(2)`（与其它 pwsh 入口脚本同一约定），
       python 侧打印的是 argparse usage 与 `error: …`；`-h/--help` 未移植（python 打 usage 且 rc=0，
       这里按未知参数处理 rc=2 —— 与 mt_report.ps1 / mt_assert.ps1 的既有做法一致）。
       另有一处**更宽松**的偏差：参数解析是全局的，故 `validate --version X` 这类
       「子命令不接受的多余参数」在 python 侧被 argparse 拒绝（rc=2），这里被解析后忽略（rc=0）；
       `mt.ps1` 不会这样调用。
   11. `step["key"]` / `step["pattern"]` 这类**必填键**缺失时，python 抛 KeyError（带
       traceback 退出），这里按「缺键 = 空串」继续（`validate` 已在校验阶段拦截非法用例，
       该分支仅对绕过校验的输入可见）。
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
# ⚠️ 一律不加 -Force：-Force 会「先卸载再重载」被引模块，从而摘掉调用方脚本作用域里
#    已导入的函数（Import-Module 本身不报错，后续调用才炸「术语 … 不会被识别为 cmdlet」）。
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Proc.psm1')

# 统一控制台 UTF-8 —— 必须是第一行逻辑（否则中文按本机码页写出，与 python 版不等）
Initialize-MtConsole

$script:TestDir = Get-MtTestDir
$script:CasesDir = Join-Path $script:TestDir 'cases'
$script:PsExe = (Get-Process -Id $PID).Path
$script:TAG_UTF8 = [System.Text.UTF8Encoding]::new($false, $false)

# ── 封闭原语表（与 python PRIMITIVES 同键同集合）──────────────────────────
$script:Primitives = [ordered]@{
    'inject_key'     = @('key', 'hold_ms', 'no_esc')
    'inject_command' = @('command', 'no_esc')
    'kubejs_reload'  = @()
    'wait'           = @('ms')
    'screenshot'     = @('tag', 'mode')
    'assert'         = @('type', 'pattern', 'source', 'image', 'question', 'tool', 'args', 'expect')
    'mcp_call'       = @('tool', 'args', 'id')
    'note'           = @('text')
}
$script:AssertTypes = @('log', 'absent', 'crash', 'kubejs', 'mixin', 'mcp', 'vision')

$script:Pending = Join-Path $script:CasesDir '.mcp-pending.json'
$script:Results = Join-Path $script:CasesDir '.mcp-results.json'
# 失败取证标记：存在时，流程退出清理不杀游戏客户端（见 run_case 与 mt_cleanup.ps1）
$script:KeepAlive = Join-Path $script:CasesDir '.mt_keep_alive'

# ── 通用小工具 ════════════════════════════════════════════════════════════

function New-MtPair {
    <#
    .SYNOPSIS
        构造 python 二元组 (a, b) 的 pwsh 等价物。

    .NOTES
        不能写 `return a, b` / `return @($a, $b)`：PowerShell 的数组字面量会**递归展开**
        嵌套数组，`@($worst, $timeline)` 会把 timeline 的元素摊平进返回值；而
        `return $collection` 又会在单元素时退化成标量。这里显式建 2 元 object[]。
    #>
    [CmdletBinding()]
    param($First, $Second)

    $p = [object[]]::new(2)
    $p[0] = $First
    $p[1] = $Second
    return , $p
}

function Test-MtTruthyValue {
    <#
    .SYNOPSIS
        python 的真值判定（`if x:`）在 pwsh 侧的等价物。

    .NOTES
        python 的假值 = None / False / 0 / "" / [] / {}；PowerShell 在这些值上恰好相反
        （空数组为真、0 为真、"" 为假），因此不能直接用 `if ($x)`。
    #>
    [CmdletBinding()]
    param([AllowNull()]$Value)

    if ($null -eq $Value) { return $false }
    if ($Value -is [bool]) { return [bool]$Value }
    if ($Value -is [string]) { return ([string]$Value).Length -gt 0 }
    if ($Value -is [System.Collections.IDictionary]) { return ([System.Collections.IDictionary]$Value).Count -gt 0 }
    if ($Value -is [System.Collections.IList]) { return ([System.Collections.IList]$Value).Count -gt 0 }
    if ($Value -is [int] -or $Value -is [long] -or $Value -is [double] -or
        $Value -is [decimal] -or $Value -is [single]) { return ([double]$Value -ne 0) }
    return $true
}

function Get-MtMapValue {
    <#
    .SYNOPSIS
        python `mapping.get(key, default)` 的 pwsh 等价物（缺键/非映射都返回默认值）。

    .NOTES
        用 `.Contains()` 而不是 `$Map[$Key] -ne $null`：后者无法区分「键不存在」与
        「键存在但值为 null」，而 python 的 .get 能区分（调用方依赖该区别判断
        `shots`/`offsets` 之类子表是否存在）。
    #>
    [CmdletBinding()]
    param(
        [AllowNull()]$Map,
        [Parameter(Mandatory)][string]$Key,
        [AllowNull()]$Default = $null
    )

    if ($Map -is [System.Collections.IDictionary]) {
        if ($Map.Contains($Key)) { return $Map[$Key] }
    }
    return $Default
}

function ConvertTo-MtPyText {
    <#
    .SYNOPSIS
        python `str(x)` 的常用等价物（None/True/False/整值浮点的 `.0` 写法）。
    #>
    [CmdletBinding()]
    param([AllowNull()]$Value)

    if ($null -eq $Value) { return 'None' }
    if ($Value -is [bool]) { if ($Value) { return 'True' } return 'False' }
    if ($Value -is [string]) { return [string]$Value }
    if ($Value -is [double] -or $Value -is [single] -or $Value -is [decimal]) {
        $d = [double]$Value
        if ([double]::IsNaN($d)) { return 'nan' }
        if ([double]::IsInfinity($d)) { if ($d -gt 0) { return 'inf' } return '-inf' }
        # python 的 repr/str 对整值浮点恒带小数位（100.0），.NET 的 [string] 会省成 100
        if ($d -eq [Math]::Truncate($d) -and [Math]::Abs($d) -lt 1e16) { return ('{0}.0' -f [long]$d) }
        return ([string]$d)
    }
    return ([string]$Value)
}

function ConvertTo-MtPyRepr {
    <#
    .SYNOPSIS
        python `{x!r}` 的近似等价物（字符串走单引号 + Python 风格转义）。

    .NOTES
        python 3 的 str repr 保留可打印 Unicode（中文原样输出），只转义反斜杠、单引号与
        控制字符；本实现按同一规则。非字符串类型退化为 `str()` 形态（不追求等价）。
    #>
    [CmdletBinding()]
    param([AllowNull()]$Value)

    if ($null -eq $Value) { return 'None' }
    if ($Value -isnot [string]) { return (ConvertTo-MtPyText $Value) }

    $s = [string]$Value
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.Append([char]39)
    foreach ($ch in $s.ToCharArray()) {
        $code = [int][char]$ch
        # python repr 里反斜杠写成两个字符（'\' → '\\'）
        if ($ch -eq [char]92) { [void]$sb.Append('\\') }
        elseif ($ch -eq [char]39) { [void]$sb.Append("\'") }
        elseif ($code -eq 10) { [void]$sb.Append('\n') }
        elseif ($code -eq 13) { [void]$sb.Append('\r') }
        elseif ($code -eq 9) { [void]$sb.Append('\t') }
        elseif ($code -lt 32 -or $code -eq 127) { [void]$sb.Append(('\x{0:x2}' -f $code)) }
        else { [void]$sb.Append($ch) }
    }
    [void]$sb.Append([char]39)
    return $sb.ToString()
}

function ConvertTo-MtPySortedListRepr {
    <#
    .SYNOPSIS
        `sorted(iterable)` 的 Python repr（`['a', 'b']`；空 → `[]`）。

    .NOTES
        排序用 Ordinal（与 python 的码点序在 BMP 内一致）；**不要**用 Sort-Object 的
        默认比较——那是区域性/忽略大小写的，`['B.png','a.png']` 这类输入会排错，
        而该顺序会直接打进 BLOCKED 文案里被逐字节比对。
    #>
    [CmdletBinding()]
    param([string[]]$Items = @())

    if ($null -eq $Items -or $Items.Count -eq 0) { return '[]' }
    $arr = [string[]]@($Items)
    [Array]::Sort($arr, [System.StringComparer]::Ordinal)
    return '[' + (($arr | ForEach-Object { ConvertTo-MtPyRepr $_ }) -join ', ') + ']'
}

function Get-MtTail {
    <#
    .SYNOPSIS
        `_tail(text, n=240)`：折成单行、去首尾空白、截断到 n（python 默认 240）。

    .PARAMETER FromTextMode
        输入来自**子进程 stdout/stderr** 时置位：python 的子进程（文本模式）在 Windows 上
        把 `\n` 写成 `\r\n`，因此 `_tail` 的 `.replace("\n", " ")` 会在折行处**残留 `\r`**
        （实测：多行命中示例的 detail 里是 `…@0B\r     line1\r     line2`）。pwsh 侧子脚本
        按本仓规范只写 LF，所以这里显式补上那个 `\r`，两侧的 detail 才会逐字节相同。
        **不是可选美化**：`\r` 夹在两个空格之间，比对器的 CRLF→LF 归一化**不会**把它折掉
        （实测多行 detail 曾差 3 字节 = 3 个折行点）。
        来自 JSON 文本（如 mcp 断言的 `text` 字段）的一律用默认值：json 解析出来的
        `\n` 在两侧同为 LF，补 `\r` 反而会造出差异。
    #>
    [CmdletBinding()]
    param(
        [AllowNull()][AllowEmptyString()][string]$Text,
        [int]$Limit = 240,
        [switch]$FromTextMode
    )

    if ($null -eq $Text) { return '' }
    $sep = if ($FromTextMode) { "`r " } else { ' ' }
    $t = $Text.Trim().Replace("`n", $sep)
    if ($t.Length -gt $Limit) { $t = $t.Substring(0, $Limit) }
    return $t
}

function Get-MtSlice {
    <#
    .SYNOPSIS
        python 的 `text[:n]`（**不**做 strip、**不**折行）。
    .NOTES
        `exec_assert` 的 mcp 分支用的就是这个（`text[:200]` / `text[:120]`），
        与 `_tail` 语义不同 —— 早期实现误用了 `_tail`，单行短文本看不出差别，
        一旦 text 带首尾空白或换行就会与 python 不等。切片按 UTF-16 码元
        （python 按码点，仅代理对边界有差异）。
    #>
    [CmdletBinding()]
    param(
        [AllowNull()][AllowEmptyString()][string]$Text,
        [int]$Limit = 200
    )

    if ($null -eq $Text) { return '' }
    if ($Text.Length -gt $Limit) { return $Text.Substring(0, $Limit) }
    return $Text
}

function Get-MtIoErrorText {
    <#
    .SYNOPSIS
        把「读文件失败」的异常文本对齐成 python `{exc}` 的形态。

    .NOTES
        python 侧是 `except (OSError, json.JSONDecodeError) as exc: … {exc}`。其中
        **文件不存在**这一类是可以确定性复刻的：
            FileNotFoundError → `[Errno 2] No such file or directory: '<repr(path)>'`
        （路径按 python 的 repr 规则渲染：单引号包裹、反斜杠写成 `\\`）。
        因此这里复刻它，让「用例文件不存在」分支与 python 侧逐字节一致；
        其余 IO 错误（权限拒绝、路径是目录）与**所有 JSON 解析错误**仍返回 .NET 原文
        （python 的是 json 模块私有文案，见文件头第 3 条已知偏差）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string]$Path,
        [Parameter(Mandatory)]$Exception
    )

    $ex = $Exception
    # [System.IO.File]::ReadAllText 抛出的异常常被包成 MethodInvocationException
    if ($ex -is [System.Management.Automation.MethodInvocationException] -and $null -ne $ex.InnerException) {
        $ex = $ex.InnerException
    }
    if (($ex -is [System.IO.FileNotFoundException]) -or ($ex -is [System.IO.DirectoryNotFoundException])) {
        if (-not (Test-Path -LiteralPath $Path)) {
            # pathlib 在 Windows 上会把 '/' 归一成 '\'，FileNotFoundError 里带的是归一后的路径
            return ("[Errno 2] No such file or directory: {0}" -f (ConvertTo-MtPyRepr ($Path.Replace('/', '\'))))
        }
    }
    return [string]$ex.Message
}

# ── 校验 ──────────────────────────────────────────────────────────────────
function Test-MtCaseValid {
    <#
    .SYNOPSIS
        python `validate(case)` 的 1:1 移植：返回错误信息数组（空数组 = 通过）。

    .NOTES
        错误文案必须与 python 逐字一致（`mt_gen_case.py` 与 `mt_case.py validate` 都依赖它）。
        返回 `, $errs`：单条错误时若直接 `return $errs` 会被展开成标量，
        调用方 `.Count` 就退化成 String.Length。
    #>
    [CmdletBinding()]
    param([AllowNull()]$Case)

    $errs = @()
    foreach ($key in @('case_id', 'title', 'version', 'steps')) {
        if (-not (Test-MtTruthyValue (Get-MtMapValue -Map $Case -Key $key))) {
            $errs += "缺少必填字段 $key"
        }
    }

    $version = Get-MtMapValue -Map $Case -Key 'version'
    if ((Test-MtTruthyValue $version) -and (@('1.21.1', '1.20.1') -notcontains [string]$version)) {
        $errs += "version 非法：$(ConvertTo-MtPyText $version)"
    }

    $steps = @(Get-MtMapValue -Map $Case -Key 'steps' -Default @())
    $inlineAssert = $false
    foreach ($s in $steps) {
        if ((Get-MtMapValue -Map $s -Key 'op') -eq 'assert') { $inlineAssert = $true; break }
    }
    if (-not (Test-MtTruthyValue (Get-MtMapValue -Map $Case -Key 'asserts')) -and -not $inlineAssert) {
        $errs += '条目没有任何断言（asserts 或内联 assert 步骤）'
    }

    for ($i = 0; $i -lt $steps.Count; $i++) {
        $step = $steps[$i]
        $op = Get-MtMapValue -Map $step -Key 'op'
        $opText = [string]$op
        # `$null -ne $op` 必须先判：Hashtable.Contains($null) 会抛 ArgumentNullException，
        # 而 python 的 `None in PRIMITIVES` 只是 False（落到「未知原语」分支）。
        if (-not (($null -ne $op) -and $script:Primitives.Contains($opText))) {
            $errs += ("步骤 {0}: 未知原语 '{1}'（不允许自造 op）" -f $i, (ConvertTo-MtPyText $op))
            continue
        }
        $allowed = @($script:Primitives[$opText])
        $unknown = @()
        if ($step -is [System.Collections.IDictionary]) {
            foreach ($k in $step.Keys) {
                $ks = [string]$k
                if ($ks -eq 'op') { continue }
                if ($allowed -contains $ks) { continue }
                $unknown += $ks
            }
        }
        if ($unknown.Count -gt 0) {
            $errs += ("步骤 {0}: '{1}' 含非法字段 {2}" -f $i, $opText, (ConvertTo-MtPySortedListRepr -Items $unknown))
        }
    }

    $asserts = @(Get-MtMapValue -Map $Case -Key 'asserts' -Default @())
    for ($i = 0; $i -lt $asserts.Count; $i++) {
        $a = $asserts[$i]
        $at = [string](Get-MtMapValue -Map $a -Key 'type')
        if ($script:AssertTypes -notcontains $at) {
            $errs += ("断言 {0}: 未知类型 '{1}'" -f $i, (ConvertTo-MtPyText (Get-MtMapValue -Map $a -Key 'type')))
        }
        if ($at -eq 'log' -and -not (Test-MtTruthyValue (Get-MtMapValue -Map $a -Key 'pattern'))) {
            $errs += ("断言 {0}: log 断言缺少 pattern" -f $i)
        }
    }

    return , $errs
}

# ── 子进程与判定 ──────────────────────────────────────────────────────────

function Invoke-MtCaseChild {
    <#
    .SYNOPSIS
        python `_py(script, *args)` 的 pwsh 版：调同名 pwsh 子脚本（不再经 python）。

    .NOTES
        走 Invoke-MtProcessFull（.NET ProcessStartInfo.ArgumentList）：参数由 .NET 负责
        转义，中文/空格/引号都不需要自己加引号（**不要**改用 Start-Process，它不给数组
        元素加引号，含空白的参数会被拆开）。
        timeout 固定 600s，与 python 侧 `run_py` 的默认值一致（超时语义差异见文件头第 2 条）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Script,
        [string[]]$ScriptArgs = @()
    )

    $argv = @('-NoProfile', '-File', (Join-Path $script:TestDir $Script)) + $ScriptArgs
    return (Invoke-MtProcessFull -FilePath $script:PsExe -ArgumentList $argv -TimeoutSec 600)
}

function Get-MtVerdict {
    <#
    .SYNOPSIS
        断言子进程返回码 → 结果（python `_verdict`）。

    .NOTES
        mt_assert 约定:0=PASS, 1=FAIL(标记未命中/出现), 2=ERROR(环境或引擎问题,
        例如断言通道缺失、正则非法)。**必须把 2 单列成 ERROR**:若与 FAIL 混为一谈,
        「日志文件不存在」「正则写错」这类测试链故障会被显示成「被测修复无效」。
        `r.stdout or r.stderr` 的语义是「取第一个非空串」，不是拼接。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][int]$ExitCode,
        [Parameter(Mandatory)]$Result
    )

    $stdout = [string]$Result.StdOut
    $stderr = [string]$Result.StdErr
    if ($ExitCode -eq 0) {
        $text = if ($stdout) { $stdout } else { $stderr }
        return (New-MtPair 'PASS' (Get-MtTail -Text $text -FromTextMode))
    }
    if ($ExitCode -eq 2) {
        $text = if ($stderr) { $stderr } else { $stdout }
        return (New-MtPair 'ERROR' (Get-MtTail -Text $text -FromTextMode))
    }
    $text = if ($stdout) { $stdout } else { $stderr }
    return (New-MtPair 'FAIL' (Get-MtTail -Text $text -FromTextMode))
}

# ── MCP 待执行清单 ↔ 回填结果 ═════════════════════════════════════════════

function Get-MtMcpResults {
    <#
    .SYNOPSIS
        python `_read_mcp_results()`：读 `.mcp-results.json`（列表）→ {id: item} 映射。

    .NOTES
        文件缺失/损坏一律返回空表（与 python 的 `except (JSONDecodeError, OSError)` 同义）。
        返回 OrderedHashtable（**不加** unary comma 包装）：PowerShell 的输出流不展开
        IDictionary，调用方的 `.Contains()`/索引都能正常工作。
    #>
    [CmdletBinding()]
    param()

    $map = [ordered]@{}
    if (-not (Test-Path -LiteralPath $script:Results -PathType Leaf)) { return $map }
    try {
        $txt = [System.IO.File]::ReadAllText($script:Results, $script:TAG_UTF8)
        $data = $txt | ConvertFrom-Json -AsHashtable -ErrorAction Stop
    } catch {
        return $map
    }
    if ($null -eq $data) { return $map }
    foreach ($item in @($data)) {
        if ($null -eq $item) { continue }
        $id = [string](Get-MtMapValue -Map $item -Key 'id' -Default '')
        $map[$id] = $item
    }
    return $map
}

function Add-MtMcpPending {
    <#
    .SYNOPSIS
        python `_queue_mcp(step)`：把需要 stdio MCP 的调用登记进待执行清单。

    .NOTES
        JSON 形态必须与 python 的 `json.dumps(data, ensure_ascii=False, indent=2)`
        逐字段一致（id/tool/args）。args 默认 `{}`（python 的 `step.get("args", {})`）。
        `ConvertTo-MtJson -InputObject (, $data)` 的 unary comma **不是可选装饰**：
        ConvertTo-MtJson 内部走管道，单元素数组会被展开成裸对象（实测输出 `{…}` 而非 `[{…}]`）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Step)

    $dir = [System.IO.Path]::GetDirectoryName($script:Pending)
    if (-not (Test-Path -LiteralPath $dir)) { [void](New-Item -ItemType Directory -Force -Path $dir) }

    $data = @()
    if (Test-Path -LiteralPath $script:Pending -PathType Leaf) {
        try {
            $raw = [System.IO.File]::ReadAllText($script:Pending, $script:TAG_UTF8)
            $parsed = $raw | ConvertFrom-Json -AsHashtable -ErrorAction Stop
            $data = @($parsed)
        } catch {
            $data = @()
        }
    }

    $id = Get-MtMapValue -Map $Step -Key 'id'
    if (-not (Test-MtTruthyValue $id)) { $id = "m$($data.Count + 1)" }
    $rec = [ordered]@{
        id   = [string]$id
        tool = [string](Get-MtMapValue -Map $Step -Key 'tool')
        args = (Get-MtMapValue -Map $Step -Key 'args' -Default ([ordered]@{}))
    }
    $data = @($data) + , $rec

    [System.IO.File]::WriteAllText($script:Pending, (ConvertTo-MtJson -InputObject (, $data)), $script:TAG_UTF8)
    Write-MtLine ("MT_MCP_PENDING: 已登记 {0} → {1}（等待会话层回填 {2}）" -f `
            $rec['id'], $rec['tool'], [System.IO.Path]::GetFileName($script:Results))
}

function Wait-MtMcpResult {
    <#
    .SYNOPSIS
        python `_await_mcp(key, timeout)`：轮询回填结果，超时返回「未找到」。

    .NOTES
        返回值用 `, @($found)` 包一层：返回空数组与返回「找到但值为 null」必须可区分，
        否则调用方的 `res is None` 判定会失真。调用方读 `$pair[0]`。
    #>
    [CmdletBinding()]
    param([AllowEmptyString()][string]$Key = '', [double]$TimeoutSec = 120)

    $deadline = ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0) + $TimeoutSec
    while (([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0) -lt $deadline) {
        if (Test-Path -LiteralPath $script:Results -PathType Leaf) {
            $all = Get-MtMcpResults
            if ($all.Contains($Key)) { return , @($all[$Key]) }
        }
        Start-Sleep -Milliseconds 1000
    }
    return , @($null)
}

# ── 断言 ──────────────────────────────────────────────────────────────────
function Invoke-MtCaseAssert {
    <#
    .SYNOPSIS
        python `exec_assert(p, a, run_id)`：返回 (结果, 说明)。
        结果 ∈ PASS/FAIL/BLOCKED/ERROR/DELEGATED。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][psobject]$Paths,
        [Parameter(Mandatory)]$Assert,
        [Parameter(Mandatory)][AllowEmptyString()][string]$RunId
    )

    $t = [string](Get-MtMapValue -Map $Assert -Key 'type')
    if ($t -eq 'log') {
        $r = Invoke-MtCaseChild -Script 'mt_assert.ps1' -ScriptArgs @(
            'log', '--version', $Paths.version,
            '--pattern', (ConvertTo-MtPyText (Get-MtMapValue -Map $Assert -Key 'pattern')),
            '--source', (Get-MtMapValue -Map $Assert -Key 'source' -Default 'latest'))
        return (Get-MtVerdict -ExitCode $r.ExitCode -Result $r)
    }
    if ($t -eq 'absent') {
        $r = Invoke-MtCaseChild -Script 'mt_assert.ps1' -ScriptArgs @(
            'absent', '--version', $Paths.version,
            '--pattern', (ConvertTo-MtPyText (Get-MtMapValue -Map $Assert -Key 'pattern')),
            '--source', (Get-MtMapValue -Map $Assert -Key 'source' -Default 'latest'))
        return (Get-MtVerdict -ExitCode $r.ExitCode -Result $r)
    }
    if ($t -eq 'crash') {
        $r = Invoke-MtCaseChild -Script 'mt_assert.ps1' -ScriptArgs @('crash', '--version', $Paths.version)
        return (Get-MtVerdict -ExitCode $r.ExitCode -Result $r)
    }
    if ($t -eq 'kubejs') {
        $r = Invoke-MtCaseChild -Script 'mt_assert.ps1' -ScriptArgs @('kubejs', '--version', $Paths.version)
        if ($r.ExitCode -eq 11) { return (New-MtPair 'BLOCKED' (Get-MtTail -Text ([string]$r.StdErr) -FromTextMode)) }
        return (Get-MtVerdict -ExitCode $r.ExitCode -Result $r)
    }
    if ($t -eq 'mixin') {
        $r = Invoke-MtCaseChild -Script 'mt_assert.ps1' -ScriptArgs @('mixin', '--version', $Paths.version)
        return (Get-MtVerdict -ExitCode $r.ExitCode -Result $r)
    }
    if ($t -eq 'mcp') {
        $all = Get-MtMcpResults
        $key = [string](Get-MtMapValue -Map $Assert -Key 'id' -Default '')
        $res = $null
        if ($all.Contains($key)) { $res = $all[$key] }
        if ($null -eq $res) {
            return (New-MtPair 'BLOCKED' ("会话层未回填 MCP 结果（id={0}）" -f (ConvertTo-MtPyText (Get-MtMapValue -Map $Assert -Key 'id'))))
        }
        $text = ConvertTo-MtPyText (Get-MtMapValue -Map $res -Key 'text' -Default '')
        if (Test-MtTruthyValue (Get-MtMapValue -Map $res -Key 'isError')) {
            # python: text[:200]（**裸切片**，不是 _tail：不 strip、不折行）
            return (New-MtPair 'FAIL' (Get-MtSlice -Text $text -Limit 200))
        }
        $expect = Get-MtMapValue -Map $Assert -Key 'expect'
        if ((Test-MtTruthyValue $expect) -and (-not $text.Contains([string]$expect))) {
            return (New-MtPair 'FAIL' ("期望包含 {0}，实际 {1}" -f `
                        (ConvertTo-MtPyRepr $expect), (ConvertTo-MtPyRepr (Get-MtSlice -Text $text -Limit 120))))
        }
        return (New-MtPair 'PASS' (Get-MtSlice -Text $text -Limit 200))
    }
    if ($t -eq 'vision') {
        # image 可以是「截图文件名」,也可以是截图 op 登记过的 **tag** —— 用例普遍写 tag,
        # 早期实现直接拿 tag 去和文件名比对,导致所有 vision 断言恒为 BLOCKED(已在
        # 2026-09-12 真机暴露)。此处统一解析成当前世代内的真实文件,并把绝对路径
        # 回填进 detail,判定方才拿得到可读的图。
        $want = Get-MtMapValue -Map $Assert -Key 'image'
        $names = @()
        foreach ($s in @(Get-MtCurrentShots -Paths $Paths -RunId $RunId)) { $names += [string]$s.Name }

        $byTag = [ordered]@{}
        $shotsData = Get-MtShots -Paths $Paths
        foreach ($s in @(Get-MtMapValue -Map $shotsData -Key 'shots' -Default @())) {
            $tag = Get-MtMapValue -Map $s -Key 'tag'
            $file = Get-MtMapValue -Map $s -Key 'file'
            # 清单按时间追加，后者覆盖前者 = 最新
            if ((Test-MtTruthyValue $tag) -and (Test-MtTruthyValue $file)) {
                $byTag[[string]$tag] = [string]$file
            }
        }

        $target = $null
        if ((Test-MtTruthyValue $want) -and ($names -contains [string]$want)) { $target = [string]$want }
        if ($null -eq $target -and (Test-MtTruthyValue $want) -and $byTag.Contains([string]$want)) {
            if ($names -contains [string]$byTag[[string]$want]) { $target = [string]$byTag[[string]$want] }
        }
        if ((Test-MtTruthyValue $want) -and ($null -eq $target)) {
            $msg = "截图 {0} 不属于当前世代 (世代内: {1}, tag 表: {2})" -f `
                (ConvertTo-MtPyRepr $want),
                (ConvertTo-MtPySortedListRepr -Items $names),
                (ConvertTo-MtPySortedListRepr -Items @($byTag.Keys))
            return (New-MtPair 'BLOCKED' $msg)
        }
        if ($null -eq $target) {
            return (New-MtPair 'BLOCKED' 'vision 断言未指定 image')
        }
        $question = Get-MtMapValue -Map $Assert -Key 'question' -Default '请判定截图中的视觉断言是否成立'
        return (New-MtPair 'DELEGATED' ("VISION_PATH={0} | {1}" -f (Join-Path $Paths.shot_dir $target), $question))
    }
    return (New-MtPair 'ERROR' "未实现的断言类型 $t")
}

# ── 原语执行 ──────────────────────────────────────────────────────────────
function Invoke-MtCaseOp {
    <#
    .SYNOPSIS
        python `exec_op(p, step, run_id, results)`：执行一个原语，返回 (结果, 说明)。

    .NOTES
        `-Results` 对应 python 的同名形参 —— 两侧都**从未使用**它（保留是为了逐行对照）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][psobject]$Paths,
        [Parameter(Mandatory)]$Step,
        [Parameter(Mandatory)][AllowEmptyString()][string]$RunId,
        [AllowNull()]$Results = $null
    )

    $op = [string](Get-MtMapValue -Map $Step -Key 'op')

    if ($op -eq 'inject_key') {
        $argv = @('key', '--key', (ConvertTo-MtPyText (Get-MtMapValue -Map $Step -Key 'key')), '--version', $Paths.version)
        if (Test-MtTruthyValue (Get-MtMapValue -Map $Step -Key 'hold_ms')) {
            # python 传 --hold-ms；PowerShell 参数名不允许内嵌连字符（见文件头第 1 条）
            $argv += @('-HoldMs', (ConvertTo-MtPyText (Get-MtMapValue -Map $Step -Key 'hold_ms')))
        }
        $r = Invoke-MtCaseChild -Script 'mt_inject.ps1' -ScriptArgs $argv
        $text = if ($r.StdOut) { [string]$r.StdOut } else { [string]$r.StdErr }
        return (New-MtPair $(if ($r.ExitCode -eq 0) { 'PASS' } else { 'ERROR' }) (Get-MtTail -Text $text -FromTextMode))
    }

    if ($op -eq 'inject_command') {
        $argv = @('cmd', '--command', (ConvertTo-MtPyText (Get-MtMapValue -Map $Step -Key 'command')), '--version', $Paths.version)
        if (Test-MtTruthyValue (Get-MtMapValue -Map $Step -Key 'no_esc')) {
            # python 传 --no-esc；同上看第 1 条
            $argv += '-NoEsc'
        }
        $r = Invoke-MtCaseChild -Script 'mt_inject.ps1' -ScriptArgs $argv
        Start-Sleep -Milliseconds 600
        $text = if ($r.StdOut) { [string]$r.StdOut } else { [string]$r.StdErr }
        return (New-MtPair $(if ($r.ExitCode -eq 0) { 'PASS' } else { 'ERROR' }) (Get-MtTail -Text $text -FromTextMode))
    }

    if ($op -eq 'kubejs_reload') {
        [void](Invoke-MtCaseChild -Script 'mt_inject.ps1' -ScriptArgs @(
                'cmd', '--command', '/kubejs reload server-scripts', '--version', $Paths.version))
        Start-Sleep -Milliseconds 1000
        [void](Invoke-MtCaseChild -Script 'mt_inject.ps1' -ScriptArgs @(
                'cmd', '--command', '/reload', '--version', $Paths.version))
        Start-Sleep -Milliseconds 2000
        return (New-MtPair 'PASS' '已热重载 KubeJS 脚本')
    }

    if ($op -eq 'wait') {
        $ms = Get-MtMapValue -Map $Step -Key 'ms' -Default 500
        Start-Sleep -Milliseconds ([int]$ms)
        return (New-MtPair 'PASS' ("等待 {0}ms" -f (ConvertTo-MtPyText $ms)))
    }

    if ($op -eq 'screenshot') {
        $r = Invoke-MtCaseChild -Script 'mt_capture.ps1' -ScriptArgs @(
            'capture', '--version', $Paths.version,
            '--tag', (ConvertTo-MtPyText (Get-MtMapValue -Map $Step -Key 'tag')),
            '--mode', (Get-MtMapValue -Map $Step -Key 'mode' -Default 'f2'))
        $text = if ($r.StdOut) { [string]$r.StdOut } else { [string]$r.StdErr }
        return (New-MtPair $(if ($r.ExitCode -eq 0) { 'PASS' } else { 'ERROR' }) (Get-MtTail -Text $text -FromTextMode))
    }

    if ($op -eq 'mcp_call') {
        Add-MtMcpPending -Step $Step
        $key = [string](Get-MtMapValue -Map $Step -Key 'id' -Default '')
        $timeout = [double](Get-MtMapValue -Map $Step -Key 'timeout' -Default 120)
        $pair = Wait-MtMcpResult -Key $key -TimeoutSec $timeout
        $res = $pair[0]
        if ($null -eq $res) {
            return (New-MtPair 'BLOCKED' ("MCP 握手超时（id={0}）" -f (ConvertTo-MtPyText (Get-MtMapValue -Map $Step -Key 'id'))))
        }
        $ok = if (Test-MtTruthyValue (Get-MtMapValue -Map $res -Key 'isError')) { 'FAIL' } else { 'PASS' }
        return (New-MtPair $ok (Get-MtTail (ConvertTo-MtPyText (Get-MtMapValue -Map $res -Key 'text' -Default ''))))
    }

    if ($op -eq 'assert') {
        return (Invoke-MtCaseAssert -Paths $Paths -Assert $Step -RunId $RunId)
    }

    if ($op -eq 'note') {
        $text = ''
        if ($Step -is [System.Collections.IDictionary] -and $Step.Contains('text')) {
            $text = ConvertTo-MtPyText $Step['text']
        }
        return (New-MtPair 'NOTE' $text)
    }

    return (New-MtPair 'ERROR' "未知原语 $op")
}

# ── 条目路径解析 ──────────────────────────────────────────────────────────

function Resolve-MtCasePath {
    <#
    .SYNOPSIS
        python `_resolve_case(spec)`：把条目参数解析为路径，且与调用者 cwd 无关。

    .NOTES
        解析顺序:绝对路径 → 相对 TEST_DIR → 补 .json 后在 TEST_DIR/cases 下 → 按 cwd 解释。
        偏差：`Path(spec).is_absolute()` 在 Windows 上对 `/x`（无盘符）为 False，而
        `[System.IO.Path]::IsPathRooted('/x')` 为 True —— 仅影响这种盘符相对路径写法。
        返回前按 pathlib 的规则把 '/' 归一成 '\'：该字符串会原样出现在
        `无法读取 …` / FileNotFoundError 文案里（python 侧同样是归一后的 str(Path)）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Spec)

    if ([System.IO.Path]::IsPathRooted($Spec)) { return (ConvertTo-MtPathText $Spec) }
    $cand = Join-Path $script:TestDir $Spec
    if (Test-Path -LiteralPath $cand -PathType Leaf) { return (ConvertTo-MtPathText $cand) }
    $name = if ($Spec.ToLowerInvariant().EndsWith('.json')) { $Spec } else { "$Spec.json" }
    $cand2 = Join-Path $script:CasesDir $name
    if (Test-Path -LiteralPath $cand2 -PathType Leaf) { return (ConvertTo-MtPathText $cand2) }
    return (ConvertTo-MtPathText $Spec)
}

function Resolve-MtCaseDir {
    <#
    .SYNOPSIS
        python `_resolve_dir(spec)`：--dir 同理，相对路径优先按 TEST_DIR 解释。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Spec)

    if ([System.IO.Path]::IsPathRooted($Spec)) { return (ConvertTo-MtPathText $Spec) }
    $cand = Join-Path $script:TestDir $Spec
    if (Test-Path -LiteralPath $cand -PathType Container) { return (ConvertTo-MtPathText $cand) }
    return (ConvertTo-MtPathText $Spec)
}

function ConvertTo-MtPathText {
    <#
    .SYNOPSIS
        把路径渲染成 python `str(Path(...))` 的形态（Windows：'/' → '\'）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Path)

    return $Path.Replace('/', '\')
}

function Get-MtCaseFiles {
    <#
    .SYNOPSIS
        python `_case_files(directory)`：`sorted(directory.glob("*.json"))`。

    .NOTES
        不用 `-Include`（`Get-ChildItem -LiteralPath <dir> -Recurse -File -Include …` 在
        pwsh 7 是参数集冲突，会直接抛异常）；改为先枚举再按名字过滤。
        不传 -File：python 的 glob 也会匹配名为 `x.json` 的**目录**（随后读取时才失败），
        这里保持同一集合，避免两侧的「文件清单」在极端情况下不一致。
        排序复刻 `sorted(Path.glob(...))`：Windows 上 pathlib 的 `_str_normcase` 是
        「整串小写 + '/' 归一为 '\'」，再按 Ordinal 比较 —— 因此用「小写键 + Ordinal」
        排，而不是 Sort-Object 的区域性比较（后者对 `a.json`/`B.json` 这种会给错序）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Directory)

    $out = @()
    foreach ($item in @(Get-ChildItem -LiteralPath $Directory -ErrorAction SilentlyContinue)) {
        if ([string]$item.Name -like '*.json') {
            # python 的 glob 返回「目录参数原样 + 名字」：目录是相对的，结果就是相对的
            $full = if ([System.IO.Path]::IsPathRooted($Directory)) { $item.FullName } else { Join-Path $Directory $item.Name }
            $out += (ConvertTo-MtPathText $full)
        }
    }
    if ($out.Count -eq 0) { return , @() }
    $arr = [string[]]@($out)
    $keys = [string[]]@($arr | ForEach-Object { ([string]$_).ToLowerInvariant().Replace('/', '\') })
    [Array]::Sort($keys, $arr, [System.StringComparer]::Ordinal)
    return , $arr
}

# ── 条目执行 ──────────────────────────────────────────────────────────────
function Invoke-MtCaseRun {
    <#
    .SYNOPSIS
        python `run_case(version, case_path, run_id)`：执行单条用例，返回 (结论, 时间线)。

    .NOTES
        返回 2 元 object[]（见 New-MtPair 的注释）：直接 `return $worst, $timeline`
        会把时间线的元素摊平进顶层。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][AllowEmptyString()][string]$CaseFile,
        [Parameter(Mandatory)][AllowEmptyString()][string]$RunId
    )

    $case = $null
    try {
        $raw = [System.IO.File]::ReadAllText($CaseFile, $script:TAG_UTF8)
        $case = $raw | ConvertFrom-Json -AsHashtable -ErrorAction Stop
    } catch {
        Write-MtErrLine ("MT_CASE: ERROR — 无法读取 {0}：{1}" -f $CaseFile, (Get-MtIoErrorText -Path $CaseFile -Exception $_.Exception))
        return (New-MtPair 'ERROR' @())
    }

    $errs = Test-MtCaseValid -Case $case
    if ($errs.Count -gt 0) {
        Write-MtErrLine ("MT_CASE: ERROR — {0} 校验失败：" -f [System.IO.Path]::GetFileName($CaseFile))
        foreach ($e in $errs) { Write-MtErrLine ("    {0}" -f $e) }
        return (New-MtPair 'ERROR' @())
    }

    $caseId = [string](Get-MtMapValue -Map $case -Key 'case_id')
    $caseVersion = [string](Get-MtMapValue -Map $case -Key 'version')
    if ($caseVersion -ne $Version) {
        Write-MtLine ("MT_CASE: SKIP — {0} 面向 {1}，跳过 {2}" -f $caseId, $caseVersion, $Version)
        return (New-MtPair 'SKIP' @())
    }

    $p = Get-MtPaths -Version $Version
    Write-MtLine ''
    Write-MtLine ("--- MT_CASE: {0} — {1} ---" -f $caseId, (Get-MtMapValue -Map $case -Key 'title'))

    $steps = @()
    $fixtures = Get-MtMapValue -Map $case -Key 'fixtures'
    if (Test-MtTruthyValue (Get-MtMapValue -Map $fixtures -Key 'kubejs')) {
        $steps += [ordered]@{ op = 'kubejs_reload' }
    }
    $steps += @(Get-MtMapValue -Map $case -Key 'steps' -Default @())
    foreach ($a in @(Get-MtMapValue -Map $case -Key 'asserts' -Default @())) {
        # python: {"op": "assert", **a} —— a 里的 "op" 会覆盖前者，故这里也先写后覆盖
        $merged = [ordered]@{ op = 'assert' }
        if ($a -is [System.Collections.IDictionary]) {
            foreach ($k in $a.Keys) { $merged[[string]$k] = $a[$k] }
        }
        $steps += $merged
    }

    $marks = @{
        'PASS' = 'PASS'; 'FAIL' = 'FAIL'; 'BLOCKED' = 'BLOCK'; 'ERROR' = 'ERROR'
        'SKIP' = 'SKIP'; 'NOTE' = 'note'; 'DELEGATED' = '→VIS'
    }

    $timeline = @()
    $worst = 'PASS'
    for ($i = 1; $i -le $steps.Count; $i++) {
        $step = $steps[$i - 1]
        $t0 = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0
        $pair = Invoke-MtCaseOp -Paths $p -Step $step -RunId $RunId
        $outcome = [string]$pair[0]
        $detail = [string]$pair[1]
        $secs = [Math]::Round((([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0) - $t0), 1)
        $timeline += [ordered]@{
            i       = $i
            op      = [string](Get-MtMapValue -Map $step -Key 'op')
            outcome = $outcome
            detail  = $detail
            secs    = $secs
        }

        $mark = if ($marks.ContainsKey($outcome)) { $marks[$outcome] } else { $outcome }
        $label = ''
        foreach ($k in @('key', 'command', 'tag')) {
            $v = Get-MtMapValue -Map $step -Key $k
            if (Test-MtTruthyValue $v) { $label = ConvertTo-MtPyText $v; break }
        }
        Write-MtLine ("  [{0,5}] {1,2}. {2} {3} — {4}" -f `
                $mark, $i, [string](Get-MtMapValue -Map $step -Key 'op'), $label, $detail)

        if ($outcome -eq 'ERROR') {
            $worst = 'ERROR'
        } elseif ($outcome -eq 'BLOCKED' -and @('PASS', 'SKIP') -contains $worst) {
            $worst = 'BLOCKED'
        } elseif ($outcome -eq 'FAIL' -and $worst -ne 'ERROR') {
            $worst = 'FAIL'
        }
    }

    Write-MtLine ("MT_CASE_RESULT: {0} = {1}" -f $caseId, $worst)

    # on_fail=keep_game_running：失败时保留游戏现场供取证。
    # 落一个标记文件，供流程退出清理判断 —— 否则「自动杀进程」会在失败瞬间
    # 把现场销毁，让取证变成不可能。
    if (@('FAIL', 'ERROR') -contains $worst -and (Get-MtMapValue -Map $case -Key 'on_fail') -eq 'keep_game_running') {
        $dir = [System.IO.Path]::GetDirectoryName($script:KeepAlive)
        if (-not (Test-Path -LiteralPath $dir)) { [void](New-Item -ItemType Directory -Force -Path $dir) }
        $stamp = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss', [cultureinfo]::InvariantCulture)
        [System.IO.File]::WriteAllText($script:KeepAlive, ("{0} ({1}) @ {2}`n" -f $caseId, $worst, $stamp), $script:TAG_UTF8)
        Write-MtLine ("MT_KEEP_ALIVE: 已置位（{0} 失败，退出时保留游戏现场） —— 取证完成后执行 pwsh -File scripts/test/mt.ps1 --phase stop 收停" -f $caseId)
    }

    return (New-MtPair $worst $timeline)
}

# ── 子命令 ════════════════════════════════════════════════════════════════

function Invoke-MtCaseValidate {
    <#
    .SYNOPSIS
        python `validate` 子命令：纯校验，不触达游戏。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Spec)

    $path = Resolve-MtCasePath -Spec $Spec
    $case = $null
    try {
        $case = ([System.IO.File]::ReadAllText($path, $script:TAG_UTF8)) | ConvertFrom-Json -AsHashtable -ErrorAction Stop
    } catch {
        Write-MtErrLine ("MT_VALIDATE: ERROR — 无法读取 {0}：{1}" -f $Spec, (Get-MtIoErrorText -Path $path -Exception $_.Exception))
        return $MT_EXIT_ERROR
    }

    $errs = Test-MtCaseValid -Case $case
    if ($errs.Count -gt 0) {
        Write-MtLine ("MT_VALIDATE: FAIL — {0} 项" -f $errs.Count)
        foreach ($e in $errs) { Write-MtLine ("    {0}" -f $e) }
        return $MT_EXIT_FAIL
    }
    Write-MtLine ("MT_VALIDATE: OK — {0}" -f (Get-MtMapValue -Map $case -Key 'case_id'))
    return 0
}

function Invoke-MtCaseRunCommand {
    <#
    .SYNOPSIS
        python `main()` 的 run / run-dir 分支：解析条目文件、逐条执行、汇总退出码。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][string]$Command,
        [AllowEmptyString()][string]$CasePath = '',
        [AllowEmptyString()][string]$Dir = ''
    )

    $runId = Get-MtActiveRunId
    if ($Command -eq 'run') {
        $files = @(Resolve-MtCasePath -Spec $CasePath)
    } else {
        $dirSpec = if ($Dir) { $Dir } else { $script:CasesDir }
        # ⚠️ 不能写成 `@(Get-MtCaseFiles …)`：该函数用 `return , $arr` 保护「单元素/空数组」
        #    的形态，外面再套 @() 会把它当成**一个**输出对象（空列表也变成 Count=1）。
        $files = Get-MtCaseFiles -Directory (Resolve-MtCaseDir -Spec $dirSpec)
    }

    if ($files.Count -eq 0) {
        Write-MtErrLine 'MT_CASE: 无条目文件'
        return $MT_EXIT_BLOCKED
    }

    $summary = @()
    $worst = 'PASS'
    foreach ($f in $files) {
        try {
            $pair = Invoke-MtCaseRun -Version $Version -CaseFile $f -RunId $runId
            $outcome = [string]$pair[0]
        } catch {
            Write-MtErrLine ("MT_CASE: ERROR — {0}: {1}" -f [System.IO.Path]::GetFileName($f), $_.Exception.Message)
            $outcome = 'ERROR'
        }
        $summary += ("{0}={1}" -f [System.IO.Path]::GetFileNameWithoutExtension($f), $outcome)
        if ($outcome -eq 'ERROR') {
            $worst = 'ERROR'
        } elseif ($outcome -eq 'FAIL' -and $worst -ne 'ERROR') {
            $worst = 'FAIL'
        } elseif ($outcome -eq 'BLOCKED' -and @('PASS', 'SKIP') -contains $worst) {
            $worst = 'BLOCKED'
        }
    }

    Write-MtLine ''
    Write-MtLine ("MT_CASES_SUMMARY: {0}" -f ($summary -join ', '))
    switch ($worst) {
        'FAIL' { return $MT_EXIT_FAIL }
        'BLOCKED' { return $MT_EXIT_BLOCKED }
        'ERROR' { return $MT_EXIT_ERROR }
        default { return 0 }
    }
}

# ── 入口 ══════════════════════════════════════════════════════════════════
if ($MyInvocation.InvocationName -ne '.') {

    # 手写参数解析：`$key = $tok.TrimStart('-').ToLowerInvariant()` 使 `--version` 与
    # `-Version` 都接受。**不用 param() 块**：`pwsh -File` 下数组参数会被拆散。
    $Cmd = ''
    $Version = ''
    $CasePath = ''
    $Dir = ''

    $i = 0
    while ($i -lt $args.Count) {
        $tok = [string]$args[$i]
        $key = $tok.TrimStart('-').ToLowerInvariant()
        if ($tok -notlike '-*') {
            if ($Cmd) { Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR }
            $Cmd = $key
            $i++
        } elseif ($key -eq 'version') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
            $Version = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'case') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --case 的值'; exit $MT_EXIT_ERROR }
            $CasePath = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'dir') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --dir 的值'; exit $MT_EXIT_ERROR }
            $Dir = [string]$args[$i + 1]; $i += 2
        } else {
            Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
        }
    }

    if ($Cmd -notin @('run', 'run-dir', 'validate')) {
        Write-MtErrorLine '必须指定子命令 run / run-dir / validate'
        exit $MT_EXIT_ERROR
    }
    if ($Cmd -eq 'validate') {
        if (-not $CasePath) { Write-MtErrorLine 'validate 子命令必须指定 --case'; exit $MT_EXIT_ERROR }
        exit (Invoke-MtCaseValidate -Spec $CasePath)
    }
    if (-not $Version) { Write-MtErrorLine '必须指定 --version'; exit $MT_EXIT_ERROR }
    if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }
    if ($Cmd -eq 'run' -and -not $CasePath) { Write-MtErrorLine 'run 子命令必须指定 --case'; exit $MT_EXIT_ERROR }
    exit (Invoke-MtCaseRunCommand -Version $Version -Command $Cmd -CasePath $CasePath -Dir $Dir)
}
