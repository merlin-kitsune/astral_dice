# -*- coding: utf-8 -*-
<#
Bountiful 赏金板「外部模组物品清除」校验（只读守门）。

背景
----
Bountiful 8.x 自带一批**兼容池**，位于 bountiful 自己的 jar 内：
  data/bountiful/bounty_pools/<modid>/<pool>.json
（本机实例里 <modid> = supplementaries，14 文件 / 31 条目，全部 content 指向
 `supplementaries:` 命名空间）。这些条目不是 Supplementaries 注册的，
而是 Bountiful 主动生成的，会挤占赏金板。

本脚本独立复算「实例里还有哪些赏金池条目引用了目标模组命名空间」，并核验
两种清除机制是否把每一条都覆盖住：

  机制 A —— config/bountiful/bountiful.json 的 general.dataPathsToExclude
             每项把 `*` 替换为 `([A-Za-z_/]+)` 构成正则，对
             ResourceLocation.getPath() 去掉 ".json" 后的字符串做**全串匹配**
             （反编译 io.ejekta.bountiful.config.ResourceLoadStrategy#getResources 证实）。
             → "bounty_pools/<modid>/*" 即排除该目录下所有池文件。
             ⚠️ 该字段由 BountifulIO.reloadConfig() = saveConfig() + loadConfig()
                先写后读 → **必须在游戏关闭时改文件**，否则运行中改会被内存旧值回写覆盖。

  机制 B —— config/bountiful/bounty_pools/<与 jar 内同名的池>.json
             把条目键的值设为 null。池身份 = **文件名**（不含目录），config 层与
             jar 层同名池会合并；反编译 io.ejekta.bountiful.data.Pool#merged 证实：
               键在 base 中不存在 → 原样放入（null 也放）
               值 == null          → 覆盖为 null
               否则                → KudzuVine.graft 深合并
             随后 Pool#setup 里 `if (json == null) return@forEach` → null 条目被跳过。
             → 条目被真正移除。config 层文件 Bountiful 从不回写，/reload 即生效。

扫描来源（每个实例）
  - <root>/mods/*.jar            → data/*/bounty_pools/**.json
  - <root>/config/bountiful/bounty_pools/*.json
  - <root>/saves/*/datapacks/*/data/*/bounty_pools/**.json
  - <root>/datapacks/*/data/*/bounty_pools/**.json

退出码：0 = 目标命名空间条目 0 泄漏且覆盖完整；1 = 有泄漏/覆盖不全。只读。

用法:
  pwsh -File scripts/verify/verify_bountiful_instance_exclusions.ps1
  pwsh -File scripts/verify/verify_bountiful_instance_exclusions.ps1 -Target supplementaries
  pwsh -File scripts/verify/verify_bountiful_instance_exclusions.ps1 -Instances "D:\.minecraft\versions\狐の航空学"

—— PowerShell 移植版:1:1 对应 scripts/verify/verify_bountiful_instance_exclusions.py（原 .py 已在 92fbeaf 删除；取回：`git show 92fbeaf^:scripts/verify/verify_bountiful_instance_exclusions.py`）。
#>
[CmdletBinding()]
param(
    [string]$Target = 'supplementaries',
    [string[]]$Instances = @()
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$DEFAULT_BASES = @(
    'D:\.minecraft\versions',
    [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($PSScriptRoot, '..', '..', 'run'))
)

$errors = New-Object System.Collections.Generic.List[string]

# ---------------------------------------------------------------------------
# 输出:统一经 [Console]::Out 写显式 LF（不得 CRLF;与 Mt.Phase 的 Write-MtLine 同契约)
#       Python print() 在 Windows 上把 "\n" 按 os.linesep 翻译成 CRLF 的行为）
# ---------------------------------------------------------------------------
function Write-Out([string]$text) {
    [Console]::Out.Write($text)
    [Console]::Out.Write("`n")
}

function New-Map {
    return , ([System.Collections.Specialized.OrderedDictionary]::new([System.StringComparer]::Ordinal))
}

function New-StrSet {
    return , ([System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal))
}

function ConvertTo-PyRepr {
    param($Value)
    if ($null -eq $Value) { return 'None' }
    if ($Value -is [bool]) { if ($Value) { return 'True' } else { return 'False' } }
    if ($Value -is [string]) {
        return "'" + $Value.Replace('\', '\\').Replace("'", "\'").Replace("`n", '\n').Replace("`r", '\r') + "'"
    }
    if ($Value -is [System.Collections.IDictionary]) {
        $parts = @()
        foreach ($e in $Value.GetEnumerator()) { $parts += ((ConvertTo-PyRepr $e.Key) + ': ' + (ConvertTo-PyRepr $e.Value)) }
        return '{' + ($parts -join ', ') + '}'
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        $parts = @()
        foreach ($x in $Value) { $parts += (ConvertTo-PyRepr $x) }
        return '[' + ($parts -join ', ') + ']'
    }
    return [string]$Value
}

function Sort-Ordinal {
    param($Values)
    $arr = @($Values)
    [Array]::Sort($arr, [System.StringComparer]::Ordinal)
    return , $arr
}

function Add-Err {
    param([string]$msg)
    $errors.Add($msg)
}

function ConvertTo-WildcardRegex {
    <#
    复刻 Kotlin 侧 Regex(pattern.replace(Regex("[*]"), "([A-Za-z_/]+)"))
    #>
    param([string]$pattern)
    return $pattern.Replace('*', '([A-Za-z_/]+)')
}

function Test-Excluded {
    <#
    path_no_ext 例：bounty_pools/supplementaries/_all_objs
    #>
    param([string]$pathNoExt, $patterns)
    foreach ($p in $patterns) {
        $rx = '\A(?:' + (ConvertTo-WildcardRegex $p) + ')\z'
        if ([regex]::Match($pathNoExt, $rx).Success) { return $true }
    }
    return $false
}

function Get-RawDirEntries {
    <#
    按文件系统原始枚举顺序返回目录项（等价 Python 的 os.scandir / glob 的无序语义）。
    #>
    param([string]$dir)
    if (-not [System.IO.Directory]::Exists($dir)) { return }
    $items = @()
    try { $items = @([System.IO.Directory]::EnumerateFileSystemEntries($dir)) } catch { $items = @() }
    foreach ($x in $items) { $x }
}

function Get-JarFilesIn {
    param([string]$dir)
    foreach ($e in (Get-RawDirEntries $dir)) {
        if (-not [System.IO.File]::Exists($e)) { continue }
        $n = [System.IO.Path]::GetFileName($e)
        if ($n.StartsWith('.')) { continue }
        if ($n.ToLowerInvariant().EndsWith('.jar')) { $e }
    }
}

function Find-BountifulJars {
    param([string]$root)
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($p in (Get-JarFilesIn ([System.IO.Path]::Combine($root, 'mods')))) { $out.Add($p) }
    foreach ($d in (Get-RawDirEntries $root)) {
        if (-not [System.IO.Directory]::Exists($d)) { continue }
        foreach ($p in (Get-JarFilesIn ([System.IO.Path]::Combine($d, 'mods')))) { $out.Add($p) }
    }
    $res = @()
    foreach ($p in $out) {
        if ([System.IO.Path]::GetFileName($p).ToLowerInvariant().Contains('bountiful')) { $res += $p }
    }
    return , $res
}

function Get-ExcludedPatterns {
    param([string]$root)
    $cfg = [System.IO.Path]::Combine($root, 'config', 'bountiful', 'bountiful.json')
    if (-not [System.IO.File]::Exists($cfg)) {
        return $null  # 无 bountiful 配置文件
    }
    try {
        $data = ConvertFrom-Json -InputObject ([System.IO.File]::ReadAllText($cfg, [System.Text.Encoding]::UTF8)) -AsHashtable
    }
    catch {
        Add-Err ($cfg + ' 解析失败: ' + $_.Exception.Message)
        return , @()
    }
    $res = @()
    if (($data -is [System.Collections.IDictionary]) -and $data.Contains('general')) {
        $g = $data['general']
        if (($g -is [System.Collections.IDictionary]) -and $g.Contains('dataPathsToExclude')) {
            $v = $g['dataPathsToExclude']
            if ($v) { $res = @($v) }
        }
    }
    return , $res
}

function Read-SafeText {
    param([string]$fp)
    try {
        return [System.IO.File]::ReadAllText($fp, [System.Text.Encoding]::UTF8)
    }
    catch {
        return ''
    }
}

function Get-DirsDfs {
    <#
    目录的 DFS 前序展开（含自身），对应 Python glob 的 `**` 展开顺序。
    #>
    param([string]$d)
    $d
    foreach ($e in (Get-RawDirEntries $d)) {
        if ([System.IO.Directory]::Exists($e)) {
            $n = [System.IO.Path]::GetFileName($e)
            if ($n.StartsWith('.')) { continue }
            Get-DirsDfs $e
        }
    }
}

function Get-JsonIn {
    param([string]$d)
    foreach ($e in (Get-RawDirEntries $d)) {
        if (-not [System.IO.File]::Exists($e)) { continue }
        $n = [System.IO.Path]::GetFileName($e)
        if ($n.StartsWith('.')) { continue }
        if ($n.ToLowerInvariant().EndsWith('.json')) { $e }
    }
}

function Expand-DirStar {
    <#
    把若干「带 * 的目录段」逐级展开（对应 Python glob 的逐段展开）。
    #>
    param([string]$root, [string[]]$segments)
    $dirs = @($root)
    foreach ($seg in $segments) {
        $next = @()
        foreach ($d in $dirs) {
            foreach ($e in (Get-RawDirEntries $d)) {
                if (-not [System.IO.Directory]::Exists($e)) { continue }
                $n = [System.IO.Path]::GetFileName($e)
                if ($n.StartsWith('.')) { continue }
                if ($seg -ceq '*') { $next += $e }
                elseif ($seg -ceq $n) { $next += $e }
            }
        }
        $dirs = $next
    }
    return , $dirs
}

function Get-DatapackPoolFiles {
    param([string]$root, [string]$pattern)
    $suffixRec = '/**/*.json'
    $suffixFlat = '/*.json'
    $prefix = $null
    $recursive = $false
    if ($pattern.EndsWith($suffixRec)) {
        $prefix = $pattern.Substring(0, $pattern.Length - $suffixRec.Length)
        $recursive = $true
    }
    elseif ($pattern.EndsWith($suffixFlat)) {
        $prefix = $pattern.Substring(0, $pattern.Length - $suffixFlat.Length)
    }
    else {
        return
    }
    $segs = $prefix -split '/'
    $dirs = Expand-DirStar $root $segs
    foreach ($d in $dirs) {
        if ($recursive) {
            foreach ($dd in (Get-DirsDfs $d)) {
                foreach ($f in (Get-JsonIn $dd)) { $f }
            }
        }
        else {
            foreach ($f in (Get-JsonIn $d)) { $f }
        }
    }
}

function Get-Sources {
    <#
    返回 [(source_label, path_no_ext, raw_text)]，path_no_ext 已剥掉 .json 与 data/<ns>/ 前缀
    #>
    param([string]$root)
    $out = New-Object System.Collections.Generic.List[object]

    $jars = Sort-Ordinal (Find-BountifulJars $root)
    foreach ($jar in $jars) {
        $zip = $null
        try {
            $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
        }
        catch {
            Add-Err ('jar 无法打开: ' + $jar + ' (' + $_.Exception.Message + ')')
            continue
        }
        try {
            foreach ($e in $zip.Entries) {
                $name = $e.FullName
                $m = [regex]::Match($name, '^data/([^/]+)/bounty_pools/(.+?)\.json$')
                if (-not $m.Success) { continue }
                $raw = $null
                try {
                    $stream = $e.Open()
                    try {
                        $sr = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
                        try { $raw = $sr.ReadToEnd() } finally { $sr.Dispose() }
                    }
                    finally { $stream.Dispose() }
                }
                catch {
                    continue
                }
                # path relative to data/<ns>/ -> "bounty_pools/<...>"，与 dataPathsToExclude 口径一致
                # 注意:PowerShell 的逗号运算符优先级高于 +,每个拼接项必须各自加括号
                $out.Add(@(('jar:' + [System.IO.Path]::GetFileName($jar)), ('bounty_pools/' + $m.Groups[2].Value), $raw))
            }
        }
        finally {
            $zip.Dispose()
        }
    }

    $cfgDir = [System.IO.Path]::Combine($root, 'config', 'bountiful', 'bounty_pools')
    $cfgFiles = @()
    foreach ($e in (Get-RawDirEntries $cfgDir)) {
        if (-not [System.IO.File]::Exists($e)) { continue }
        $n = [System.IO.Path]::GetFileName($e)
        if ($n.StartsWith('.')) { continue }
        if ($n.ToLowerInvariant().EndsWith('.json')) { $cfgFiles += $e }
    }
    foreach ($f in (Sort-Ordinal $cfgFiles)) {
        $bn = [System.IO.Path]::GetFileName($f)
        $out.Add(@(('config:' + $bn), ('bounty_pools/' + $bn.Substring(0, $bn.Length - 5)), (Read-SafeText $f)))
    }

    foreach ($pat in @('saves/*/datapacks/*/data/*/bounty_pools/**/*.json',
            'saves/*/datapacks/*/data/*/bounty_pools/*.json',
            'datapacks/*/data/*/bounty_pools/**/*.json',
            'datapacks/*/data/*/bounty_pools/*.json')) {
        foreach ($f in (Get-DatapackPoolFiles $root $pat)) {
            $m = [regex]::Match($f, '[/\\]data[/\\][^/\\]+[/\\](bounty_pools[/\\].+)\.json$')
            $rel = ''
            if ($m.Success) { $rel = $m.Groups[1].Value.Replace('\', '/') }
            else { $rel = [System.IO.Path]::GetRelativePath($root, $f) }
            $out.Add(@(('datapack:' + [System.IO.Path]::GetRelativePath($root, $f)), $rel, (Read-SafeText $f)))
        }
    }
    return , $out
}


function Invoke-Audit {
    param([string]$root, [string]$target)
    Write-Out ('=' * 86)
    Write-Out ('INSTANCE : ' + $root)
    $jars = Find-BountifulJars $root
    if (@($jars).Count -eq 0) {
        Write-Out '  (无 bountiful jar，跳过)'
        return , @(0, 0, 0)
    }

    $patterns = Get-ExcludedPatterns $root
    if ($null -eq $patterns) {
        Write-Out '  ⚠ 未找到 config/bountiful/bountiful.json（首次运行游戏后才会生成）'
        $patterns = @()
    }
    $jarNames = @()
    foreach ($j in $jars) { $jarNames += [System.IO.Path]::GetFileName($j) }
    Write-Out ('  jar          : ' + (ConvertTo-PyRepr $jarNames))
    Write-Out ('  exclude 列表 : ' + (ConvertTo-PyRepr $patterns))

    $srcs = Get-Sources $root
    # config 覆盖层：池名 -> 被 null 的键集合
    $cfgNull = New-Map
    foreach ($s in $srcs) {
        $label = $s[0]
        $rel = $s[1]
        $raw = $s[2]
        if (-not $label.StartsWith('config:')) { continue }
        try { $d = ConvertFrom-Json -InputObject $raw -AsHashtable }
        catch { continue }
        $pool = [System.IO.Path]::GetFileName($rel)
        if (-not $cfgNull.Contains($pool)) { $cfgNull[$pool] = New-StrSet }
        if (($d -is [System.Collections.IDictionary]) -and $d.Contains('content') -and ($null -ne $d['content'])) {
            foreach ($kv in $d['content'].GetEnumerator()) {
                if ($null -eq $kv.Value) { [void]$cfgNull[$pool].Add([string]$kv.Key) }
            }
        }
    }

    $total = 0
    $leaked = 0
    $covered = 0
    $leakRows = New-Object System.Collections.Generic.List[object]
    foreach ($s in $srcs) {
        $label = $s[0]
        $rel = $s[1]
        $raw = $s[2]
        if (-not ([string]$raw).Contains($target + ':')) { continue }
        try { $d = ConvertFrom-Json -InputObject $raw -AsHashtable }
        catch {
            Add-Err ($label + ' 的 ' + $rel + ' JSON 解析失败')
            continue
        }
        $pool = [System.IO.Path]::GetFileName($rel)
        $excl = Test-Excluded $rel $patterns
        $keys = @()
        if (($d -is [System.Collections.IDictionary]) -and $d.Contains('content') -and ($null -ne $d['content'])) {
            $keys = @($d['content'].Keys)
        }
        foreach ($key in (Sort-Ordinal $keys)) {
            $val = $d['content'][$key]
            if ($null -eq $val) { continue }
            if (-not (ConvertTo-Json -InputObject $val -Compress -Depth 64).Contains($target + ':')) { continue }
            $total += 1
            $nulled = $false
            if ($cfgNull.Contains($pool)) { $nulled = $cfgNull[$pool].Contains([string]$key) }
            $ok = $excl -or $nulled
            if ($ok) {
                $covered += 1
            }
            else {
                $leaked += 1
                $leakRows.Add(@($rel, $key, $label))
            }
        }
        if ((-not $excl) -and (-not $cfgNull.Contains($pool))) {
            Add-Err ('池 ' + $rel + ' 既未被 dataPathsToExclude 排除，也没有 config 覆盖文件（可能泄漏）')
        }
    }

    foreach ($row in $leakRows) {
        Write-Out ('  ✗ 泄漏: ' + $row[0] + ' :: ' + $row[1] + '    (来源 ' + $row[2] + ')')
    }
    Write-Out ("  目标命名空间('" + $target + ":')条目总数 : " + $total)
    Write-Out ('  已被机制 A/B 覆盖          : ' + $covered)
    Write-Out ('  仍会出现在赏金板           : ' + $leaked)
    $verdict = '**LEAK**'
    if ($leaked -eq 0) { $verdict = 'CLEAR' }
    Write-Out ('  → ' + $verdict)
    return , @($total, $covered, $leaked)
}


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------
$roots = @()
if (@($Instances).Count -gt 0) {
    $roots = @($Instances)
}
else {
    foreach ($base in $DEFAULT_BASES) {
        $b = [System.IO.Path]::GetFullPath($base)
        if (-not [System.IO.Directory]::Exists($b)) { continue }
        $names = @()
        foreach ($dd in [System.IO.Directory]::EnumerateDirectories($b)) { $names += [System.IO.Path]::GetFileName($dd) }
        foreach ($n in (Sort-Ordinal $names)) { $roots += [System.IO.Path]::Combine($b, $n) }
    }
}

$grand = @(0, 0, 0)
foreach ($r in $roots) {
    if (-not [System.IO.Directory]::Exists($r)) {
        Add-Err ('实例目录不存在: ' + $r)
        continue
    }
    $res = Invoke-Audit $r $Target
    $grand = @(($grand[0] + $res[0]), ($grand[1] + $res[1]), ($grand[2] + $res[2]))
}

Write-Out ('=' * 86)
Write-Out ('汇总: 目标条目 ' + $grand[0] + ' | 已覆盖 ' + $grand[1] + ' | 泄漏 ' + $grand[2] + ' | 错误 ' + $errors.Count)
foreach ($e in $errors) { Write-Out ('  ! ' + $e) }
$clear = ($grand[2] -eq 0) -and ($errors.Count -eq 0)
$result = 'PROBLEM'
if ($clear) { $result = 'ALL CLEAR' }
Write-Out ('RESULT: ' + $result)
[Console]::Out.Flush()
if ($clear) { exit 0 } else { exit 1 }
