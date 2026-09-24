#Requires -Version 7.0
<#
.SYNOPSIS
    mt_gen_case — 测试条目生成器（子技能 mod-test-case 的可执行部分）。

.DESCRIPTION
    只为「新增内容 / 既有功能回归 / 用户指定内容」产出条目文件，**不执行、不启动游戏、
    不修改环境脚本**。产出物是插在环境创建与清理之间的中间层，物理上无法触达两端。

    三态输入与策略:
      --new     <注册id>   规范驱动：按内容类别套用 AGENTS 对应「必须遵守」条款 → 断言
      --feature <功能名>   回归驱动：从 CHANGELOG_ZH.md 找该功能条目 → 抽可复现步骤
      --spec    "<描述>"   描述驱动：拆成可执行步骤 + 可判定断言；不可判定的转为 vision/note

.EXAMPLE
    pwsh -File scripts/test/mt_gen_case.ps1 --version 1.21.1 --new satellite_chip
    pwsh -File scripts/test/mt_gen_case.ps1 --version 1.20.1 --feature dice.star_level
    pwsh -File scripts/test/mt_gen_case.ps1 --version 1.21.1 --spec "验证充能类筹码顺序为永动机在电磁炮之前"

.NOTES
    迁移前源文件 scripts/test/mt_gen_case.py（该原件已在 92fbeaf「工具链收敛为纯 pwsh」删除，取回：`git show 92fbeaf^:scripts/test/mt_gen_case.py`）。

    ## 校验函数的复用方式（1:1 对照）

    python 侧是 `from mt_case import validate`；pwsh 侧等价写法是**点源**引入 mt_case.ps1
    （其入口被 `if ($MyInvocation.InvocationName -ne '.')` 保护，点源时不会执行 main，
    只定义函数）。**不要**把 validate 抄一份进来：两份实现必然漂移，而
    「生成即通过校验」正是本脚本唯一的自洽约束。

    ## 与 python 版的差异（逐条）

    1. **文案偏差**：`MT_GEN_NEXT` 里的复跑入口 `bash scripts/test/mt.sh …`（旧 bash 版脚本已在 92fbeaf 删除）改为
       `pwsh -File scripts/test/mt.ps1 …`（同一入口的新名字，沿用 mt_report.ps1 /
       mt_launch.ps1 / mt_cleanup.ps1 已确立的先例）。
    2. **落盘行尾**：`cases/<id>.json` / `cases/<id>.md` 用 **LF** 写。python 的
       `write_text` 走文本模式，把 `\n` 翻译成 CRLF —— 那是解释器副产物（本仓规范换行是
       LF，比对器也按 LF 归一）。JSON **内容**（含缩进/键序/中文不转义）逐字节一致。
    3. `--dry-run` 的 stdout 与 python 的 `print(json.dumps(case, ensure_ascii=False, indent=2))`
       逐字节一致（含 2 空格缩进、空数组写 `[]`、中文不转义）。
    4. argparse 的 usage/错误文案未复刻：`--version` 非法或 `--new/--feature/--spec`
       不满足「必须且只有一个」时，一律 `MT_ERROR: …` + `exit $MT_EXIT_ERROR(2)`
       （与其它 pwsh 入口脚本同一约定）；`-h/--help` 未移植。
    5. `target.upper()` 用 `ToUpperInvariant()`：ASCII 与 python 一致；`ß` 这类
       非 ASCII 展开（'ß'.upper()=='SS'）不保证一致（注册 id 恒为 ASCII，无实际影响）。
    6. `sorted()`/`splitlines()` 等文本细节：`changelog_hits` 按 LF 切行（python 的
       `read_text()` + `splitlines()` 还会在 \v/\f/\x1c-\x1e/\x85/\u2028/\u2029 处切行，
       本仓 CHANGELOG 不含这些字符）；非 UTF-8 字节按 U+FFFD 替换（python 用
       `errors="ignore"` 丢弃）。
    7. sha1 摘要在 pwsh 侧用 `System.Security.Cryptography.SHA1`（与 python hashlib
       同为标准 SHA-1，十六进制小写前 6 位）。
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
# ⚠️ 一律不加 -Force：-Force 会卸载重载被引模块，从而摘掉调用方脚本作用域里已导入的函数
#    （Import-Module 本身不报错，后续调用才炸「术语 … 不会被识别为 cmdlet」）。
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Proc.psm1')

# 统一控制台 UTF-8 —— 必须是第一行逻辑（否则中文按本机码页写出，与 python 版不等）
Initialize-MtConsole

# python: `from mt_case import validate` —— 点源取用同一个校验实现（不会跑 mt_case 的 main）
. (Join-Path $PSScriptRoot 'mt_case.ps1')

$script:TestDir = Get-MtTestDir
$script:CasesDir = Join-Path $script:TestDir 'cases'
$script:Changelog = Join-Path (Get-MtRoot) 'CHANGELOG_ZH.md'
$script:Agents = Join-Path (Get-MtRoot) 'AGENTS.md'
$script:TAG_UTF8 = [System.Text.UTF8Encoding]::new($false, $false)

# 内容类别 → 校验切入点（规范驱动时用来挑断言）。顺序即匹配顺序（Python 3.7+ 字典有序）。
$script:CategoryRules = [ordered]@{
    'chip'        = [ordered]@{
        keywords = @('chip', '筹码')
        asserts  = @([ordered]@{ type = 'kubejs' }, [ordered]@{ type = 'crash' })
        notes    = '筹码需核对：配方两档互不越界、tooltip 染色（非时间数值黄/时间蓝）、创造栏顺序、手册条目与 lang 三版本同步'
    }
    'sign'        = [ordered]@{
        keywords = @('sign', '立牌')
        asserts  = @([ordered]@{ type = 'log'; pattern = 'TS_EQUIP_OK' }, [ordered]@{ type = 'crash' })
        notes    = '立牌需核对：J 进入选择、施加效果与冷却、旧机制无残留、玩家目标规则'
    }
    'dice'        = [ordered]@{
        keywords = @('dice', '骰子')
        asserts  = @([ordered]@{ type = 'kubejs' }, [ordered]@{ type = 'crash' })
        notes    = '骰子需核对：星级决定卡牌格数（0★4/1★6/2★8/3★12，攻防各半）'
    }
    'effect_card' = [ordered]@{
        keywords = @('effect_card', '效果牌')
        asserts  = @([ordered]@{ type = 'crash' }, [ordered]@{ type = 'kubejs' })
        notes    = '效果牌需核对：冷却统一由 EffectCardPeriod 管理、命名规范、手册条目'
    }
    'recipe'      = [ordered]@{
        keywords = @('recipe', '配方')
        asserts  = @([ordered]@{ type = 'crash' })
        notes    = '配方需核对：配方 id 与物品 id 可能不同、手册 crafting 页引用存在'
    }
}
$script:OtherRule = [ordered]@{
    keywords = @()
    asserts  = @([ordered]@{ type = 'crash' })
    notes    = '未匹配到已知类别，按通用回归处理'
}

# ── 类别判定 ══════════════════════════════════════════════════════════════
function Get-MtGenClassify {
    <#
    .SYNOPSIS
        python `classify(target)`：返回 (类别名, 规则表)。

    .NOTES
        `target.lower()` 用 `ToLowerInvariant()`（比区域性 ToLower 更接近 python 语义，
        避免土耳其语 I/ı 之类的区域差异）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Target)

    $low = $Target.ToLowerInvariant()
    foreach ($name in $script:CategoryRules.Keys) {
        $rule = $script:CategoryRules[$name]
        foreach ($kw in @($rule['keywords'])) {
            if ($kw -and $low.Contains([string]$kw)) { return (New-MtPair $name $rule) }
        }
    }
    return (New-MtPair 'other' $script:OtherRule)
}

function ConvertFrom-MtRawText {
    <#
    .SYNOPSIS
        读一个 UTF-8 文本文件：CRLF/孤立 CR 一律折成 LF（复刻 python 文本模式的通用换行）。
    .NOTES
        必须显式归一：不折行的话，按 `$`/`(?m)^` 锚定的匹配与 `splitlines()` 的结果都会
        与 python 侧悄悄不同。非法字节按 U+FFFD 替换（python 用 errors="ignore" 丢弃）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
    try {
        $bytes = [System.IO.File]::ReadAllBytes($Path)
        return $script:TAG_UTF8.GetString($bytes).Replace("`r`n", "`n").Replace("`r", "`n")
    } catch {
        return ''
    }
}

function Get-MtGenChangelogHits {
    <#
    .SYNOPSIS
        python `changelog_hits(feature, limit=5)`：在 CHANGELOG_ZH.md 里按
        「feature 的最后一段点分名」做子串匹配，取最后 limit 条命中行。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string]$Feature,
        [int]$Limit = 5
    )

    if (-not (Test-Path -LiteralPath $script:Changelog -PathType Leaf)) { return , @() }
    $parts = $Feature.Split('.')
    $key = $parts[$parts.Count - 1]
    $hits = @()
    foreach ($ln in @((ConvertFrom-MtRawText -Path $script:Changelog) -split "`n")) {
        $s = ([string]$ln).Trim()
        if ($key -and $s.Contains($key)) { $hits += $s }
    }
    if ($hits.Count -gt $Limit) { return , @($hits[($hits.Count - $Limit)..($hits.Count - 1)]) }
    return , $hits
}

function Get-MtGenHeader {
    <#
    .SYNOPSIS
        python `build_header(body)`（原样移植）。

    .NOTES
        在 python 侧**定义了但从未被调用**（死代码）。为了逐行 1:1 对照一并移植，
        不删除 —— 删除会让「两侧函数清单不一致」，反而增加复核成本。
    #>
    [CmdletBinding()]
    param([string[]]$Body = @())

    # ⚠️ 不能写 `@('a','b','c') + $Body` 之外的紧凑形式，更不能写 `'a' + $x, 'b' + $y`
    #    （逗号运算符优先级高于 '+'，那会算成一个字符串）。这里逐项拼，避免歧义。
    $out = @(
        '// 由 mt_gen_case.py 生成；可手工调整后复跑。'
        '// 断言优先选择可机械判定的类型（log/absent/crash/kubejs/mixin）；'
        '// 视觉类一律降级为 vision（判定交视觉模型），不得用像素硬编码判定。'
    )
    foreach ($b in @($Body)) { $out += $b }
    return , $out
}

# ── 三种生成策略 ══════════════════════════════════════════════════════════
function Get-MtGenNew {
    <#
    .SYNOPSIS
        python `gen_new(version, target)`：规范驱动生成「新增内容」条目骨架。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][AllowEmptyString()][string]$Target
    )

    $pair = Get-MtGenClassify -Target $Target
    $cat = [string]$pair[0]
    $rule = $pair[1]

    $asserts = @()
    foreach ($a in @($rule['asserts'])) { $asserts += $a }
    $asserts += [ordered]@{
        type     = 'vision'
        image    = "${Target}_given.png"
        question = "物品栏中是否出现了 $Target，且图标/名称渲染正常？"
    }

    return [ordered]@{
        case_id  = ("NEW-{0}" -f $Target.ToUpperInvariant().Replace('_', '-'))
        title    = "新增内容 $Target 的可用性与规范一致性（$cat）"
        version  = $Version
        target   = [ordered]@{ type = 'new-content'; ref = $Target; category = $cat }
        fixtures = [ordered]@{ kubejs = @() }
        setup    = @(
            [ordered]@{ op = 'inject_command'; command = '/clear Dev' }
            [ordered]@{ op = 'wait'; ms = 300 }
        )
        steps    = @(
            [ordered]@{ op = 'inject_command'; command = "/give Dev astral_dice:$Target" }
            [ordered]@{ op = 'wait'; ms = 600 }
            [ordered]@{ op = 'screenshot'; tag = "${Target}_given" }
        )
        asserts  = $asserts
        evidence = @("${Target}_given.png")
        notes    = @([string]$rule['notes'], '生成器只给出骨架；请按 AGENTS 对应章节补足行为断言。')
        on_fail  = 'keep_game_running'
    }
}

function Get-MtGenFeature {
    <#
    .SYNOPSIS
        python `gen_feature(version, feature)`：回归驱动生成条目骨架。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][AllowEmptyString()][string]$Feature
    )

    $hits = Get-MtGenChangelogHits -Feature $Feature
    return [ordered]@{
        case_id  = ("REG-{0}" -f $Feature.ToUpperInvariant().Replace('.', '-').Replace('_', '-'))
        title    = "既有功能回归：$Feature"
        version  = $Version
        target   = [ordered]@{ type = 'existing-feature'; ref = $Feature }
        fixtures = [ordered]@{ kubejs = @() }
        setup    = @(
            [ordered]@{ op = 'inject_command'; command = '/clear Dev' }
            [ordered]@{ op = 'wait'; ms = 300 }
        )
        steps    = @(
            [ordered]@{ op = 'kubejs_reload' }
            [ordered]@{ op = 'note'; text = '按 CHANGELOG 条目复现该功能的最小操作路径' }
        )
        asserts  = @(
            [ordered]@{ type = 'crash' }
            [ordered]@{ type = 'kubejs' }
        )
        evidence = @()
        # python 侧写的是 `["CHANGELOG 命中条目：", *hits] or ["CHANGELOG 未命中，请手工补充步骤。"]`
        # —— 左侧**恒非空**，`or` 的兜底分支是死代码（已在报告中作为 python 侧可疑点列出）。
        # 此处按实际生效语义移植。
        notes    = @('CHANGELOG 命中条目：') + $hits
        on_fail  = 'keep_game_running'
    }
}

function ConvertTo-MtAsciiSlug {
    <#
    .SYNOPSIS
        python `_ascii_slug(text, limit=32)`：把任意描述压成 ASCII 文件名安全的 slug。

    .NOTES
        `ch.isascii() and ch.isalnum()` 必须**先判 ASCII**：`[char]::IsLetterOrDigit` 是
        Unicode 感知的（会把「一」「²」判成字母/数字），只用它会写出非 ASCII 文件名。
        非 ASCII 字符 → '-'，且与前一字符同为 '-' 时不重复追加（与 python 同）。
        逐 UTF-16 码元遍历与 python 的逐码点遍历在结果上等价：代理对的每个码元都不是
        ASCII，都会落到「补一个 '-'」且被去重折叠。
    #>
    [CmdletBinding()]
    param([AllowEmptyString()][string]$Text = '', [int]$Limit = 32)

    $out = [System.Collections.Generic.List[string]]::new()
    foreach ($ch in $Text.ToCharArray()) {
        $code = [int][char]$ch
        if ($code -lt 128 -and [char]::IsLetterOrDigit($ch)) {
            $out.Add(([string]$ch).ToUpperInvariant())
        } elseif ($out.Count -gt 0 -and $out[$out.Count - 1] -ne '-') {
            $out.Add('-')
        }
    }
    $slug = ($out -join '').Trim('-')
    if ($slug.Length -gt $Limit) { $slug = $slug.Substring(0, $Limit) }
    $slug = $slug.Trim('-')
    if (-not $slug) { return 'CASE' }
    return $slug
}

function Get-MtGenSpec {
    <#
    .SYNOPSIS
        python `gen_spec(version, spec_text)`：描述驱动生成条目骨架。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][AllowEmptyString()][string]$SpecText
    )

    $sha = [System.Security.Cryptography.SHA1]::HashData([System.Text.Encoding]::UTF8.GetBytes($SpecText))
    $hex = -join ($sha | ForEach-Object { $_.ToString('x2') })
    $digest = $hex.Substring(0, 6)
    $slug = ConvertTo-MtAsciiSlug -Text $SpecText -Limit 24
    # python `spec_text[:60]`：中文全在 BMP，按 UTF-16 码元切与按码点切一致
    $titleText = if ($SpecText.Length -gt 60) { $SpecText.Substring(0, 60) } else { $SpecText }

    return [ordered]@{
        case_id  = "SPEC-$slug-$digest"
        title    = "用户指定：$titleText"
        version  = $Version
        target   = [ordered]@{ type = 'user-specified'; ref = $SpecText }
        fixtures = [ordered]@{ kubejs = @() }
        setup    = @([ordered]@{ op = 'note'; text = '按用户描述拆分的最小前置' })
        steps    = @([ordered]@{ op = 'note'; text = '在此填入可执行步骤（封闭原语）' })
        asserts  = @(
            [ordered]@{ type = 'crash' }
            [ordered]@{ type = 'vision'; question = "截图是否满足以下描述：$SpecText" }
        )
        evidence = @()
        notes    = @(
            '描述驱动生成：可机械判定的部分请改为 log/absent/kubejs 断言；'
            '无法机械判定的保留为 vision，并在报告中标注需人工复核。'
        )
        on_fail  = 'keep_game_running'
    }
}

# ── 落盘 ══════════════════════════════════════════════════════════════════
function Write-MtGenCase {
    <#
    .SYNOPSIS
        python `write_case(case, dry_run)`：先校验，再打印（dry-run）或落盘 json + md。

    .NOTES
        `CASES_DIR` 与 python 侧同源（scripts/test/cases）。非 dry-run 会真实写入该目录，
        因此**比对验证必须在整棵 scripts/test 的临时副本里跑**（见最终报告「避免污染」）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Case, [switch]$DryRun)

    # ⚠️ 不要包 @(...)：Test-MtCaseValid 用 `return , $errs` 保护「零条/单条」形态，
    #    外面再套 @() 会把结果当成一个输出对象（零条错误也会变成 Count=1 → 合法用例被判 FAIL）。
    $errs = Test-MtCaseValid -Case $Case
    if ($errs.Count -gt 0) {
        Write-MtErrLine 'MT_GEN: ERROR — 生成的条目未通过校验：'
        foreach ($e in $errs) { Write-MtErrLine ("    {0}" -f $e) }
        return 2
    }

    if (-not (Test-Path -LiteralPath $script:CasesDir)) {
        [void](New-Item -ItemType Directory -Force -Path $script:CasesDir)
    }
    $caseId = [string]$Case['case_id']
    $jsonPath = Join-Path $script:CasesDir "$caseId.json"
    $mdPath = Join-Path $script:CasesDir "$caseId.md"

    if ($DryRun) {
        Write-MtLine (ConvertTo-MtJson -InputObject $Case)
        return 0
    }

    [System.IO.File]::WriteAllText($jsonPath, (ConvertTo-MtJson -InputObject $Case), $script:TAG_UTF8)

    $tick = [char]96
    $title = [string]$Case['title']
    $target = $Case['target']
    $md = @(
        "# $caseId — $title"
        ''
        "- 版本: $($Case['version'])"
        "- 目标类型: $($target['type'])（$($target['ref'])）"
        "- 断言数: $(@($Case['asserts']).Count)"
        ''
        '## 步骤'
        ''
    )
    $i = 0
    foreach ($s in @($Case['steps'])) {
        $i++
        $desc = ''
        foreach ($k in @('command', 'key', 'tag', 'text')) {
            $v = Get-MtMapValue -Map $s -Key $k
            if (Test-MtTruthyValue $v) { $desc = ConvertTo-MtPyText $v; break }
        }
        $md += ("{0}. {1}{2}{1} {3}" -f $i, $tick, $s['op'], $desc)
    }
    $md += @('', '## 断言', '')
    foreach ($a in @($Case['asserts'])) {
        $desc = ''
        $pat = Get-MtMapValue -Map $a -Key 'pattern'
        if (Test-MtTruthyValue $pat) {
            $desc = ConvertTo-MtPyText $pat
        } else {
            $q = Get-MtMapValue -Map $a -Key 'question'
            if (Test-MtTruthyValue $q) { $desc = ConvertTo-MtPyText $q }
        }
        $md += ("- {0}{1}{0} {2}" -f $tick, $a['type'], $desc)
    }
    if (Test-MtTruthyValue (Get-MtMapValue -Map $Case -Key 'notes')) {
        $md += @('', '## 备注', '')
        foreach ($n in @($Case['notes'])) {
            if (Test-MtTruthyValue $n) { $md += ("- {0}" -f $n) }
        }
    }
    [System.IO.File]::WriteAllText($mdPath, (($md -join "`n") + "`n"), $script:TAG_UTF8)

    Write-MtLine ("MT_GEN: OK — {0} + {1}" -f `
            [System.IO.Path]::GetFileName($jsonPath), [System.IO.Path]::GetFileName($mdPath))
    Write-MtLine ("MT_GEN_NEXT: 执行 pwsh -File scripts/test/mt.ps1 --version {0} --case cases/{1}" -f `
            $Case['version'], [System.IO.Path]::GetFileName($jsonPath))
    return 0
}

# ── 入口 ══════════════════════════════════════════════════════════════════
if ($MyInvocation.InvocationName -ne '.') {

    # 手写参数解析（`--x` 与 `-X` 都接受）。**不用 param() 块**：`pwsh -File` 下数组参数会被拆散。
    $Version = ''
    $GenNew = ''
    $GenFeature = ''
    $GenSpec = ''
    $DryRun = $false

    $i = 0
    while ($i -lt $args.Count) {
        $tok = [string]$args[$i]
        $key = $tok.TrimStart('-').ToLowerInvariant()
        if ($key -eq 'version') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
            $Version = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'new') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --new 的值'; exit $MT_EXIT_ERROR }
            $GenNew = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'feature') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --feature 的值'; exit $MT_EXIT_ERROR }
            $GenFeature = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'spec') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --spec 的值'; exit $MT_EXIT_ERROR }
            $GenSpec = [string]$args[$i + 1]; $i += 2
        } elseif ($key -eq 'dry-run') {
            $DryRun = $true; $i++
        } else {
            Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
        }
    }

    if (-not $Version) { Write-MtErrorLine '必须指定 --version'; exit $MT_EXIT_ERROR }
    if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }

    # python 是 argparse 的 required mutually_exclusive_group：必须且只能给一个
    $given = 0
    if ($GenNew) { $given++ }
    if ($GenFeature) { $given++ }
    if ($GenSpec) { $given++ }
    if ($given -ne 1) {
        Write-MtErrorLine '必须且只能指定 --new / --feature / --spec 之一'
        exit $MT_EXIT_ERROR
    }

    if ($GenNew) {
        $case = Get-MtGenNew -Version $Version -Target $GenNew
    } elseif ($GenFeature) {
        $case = Get-MtGenFeature -Version $Version -Feature $GenFeature
    } else {
        $case = Get-MtGenSpec -Version $Version -SpecText $GenSpec
    }
    exit (Write-MtGenCase -Case $case -DryRun:$DryRun)
}
