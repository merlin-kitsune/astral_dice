<#
.SYNOPSIS
    探针类引用守门：核对 KubeJS 探针里 `Java.loadClass(...)` 的目标是否真的存在于运行时 classpath。

.DESCRIPTION
    **为什么需要这道门（2026-09-22 实测事故）**：1.20.1 / 1.21.1 两线的探针写的是
    `Java.loadClass("com.merlinkitsune.astral_dice.target.SignSelectionGate")`，而该类早已随
    「平台无关能力下沉」搬到了前置库（`com.merlinkitsune.starenginelib.target.SignSelectionGate`）。
    后果是**连锁的、且很容易误判**：
      · KubeJS 在加载脚本时抛 `EvaluatorException: Failed to load Java class … Class could not be found!`
        ⇒ `logs/kubejs/server.log` 出现 ERROR；
      · 于是**每一条**用例的 `type: kubejs` 断言（"server.log 0 errors"）**全红**；
      · 而失败信息是「KubeJS 有错误」，不是「某条命令不存在」⇒ 现场读起来像
        「模组有 bug」或「用例写错了」，而真实原因是一行**从没被跑到的**类加载语句。
    这类「类被搬走、探针没跟」的漂移，靠人眼审 14k 行脚本不可行，必须机器拦。

    判据：对每个 `Java.loadClass("X.Y.Z")`，要求 `X/Y/Z.class`（或其内嵌类形式 `X/Y/Z$Inner.class`）
    出现在下面三处之一：
      1. 本模组构建产物 `build/libs/astral_dice-*.jar`（**取最新一份** —— build/libs 常残留历史版本）；
      2. `run/<版本>/mods/*.jar` 全量（Curios / Patchouli / KubeJS / Rhino / starengine_lib 都在这）；
      3. `build/moddev/artifacts` 的 merged jar（MC 本体 + 加载器）。
    另外补入 `build.gradle` 里 `maven.modrinth:<slug>:<ver>` 形式的依赖（从 gradle 缓存取 jar）——
    这些第三方模组由 `modImplementation` 在 **dev 运行期**注入 classpath，**不落 `run/mods`**，
    不补这一步会把 `CuriosApi` 之类全判成缺失（实测过）。

    已登记的豁免（运行期一定存在，但不在上面三处）：
      · JDK 自带：`java.` / `javax.` / `jdk.` / `sun.` / `com.sun.` / `org.w3c.` / `org.xml.`；
      · `com.mojang.brigadier.` / `com.mojang.authlib.` / `com.mojang.blaze3d.` /
        `com.mojang.datafixers.` / `com.mojang.serialization.` —— 由加载器在运行期提供，
        既不进 merged 产物、也不在 `run/mods`（实测 `neoforge-21.1.235-merged.jar` 里 brigadier 条目为 0）。

    只读，不修改任何工程文件。
    退出码：0 = 全部可解析；1 = 存在不可解析的类引用；用法错误 = 2。

.PARAMETER Version
    要检查的版本线：`1.21.1` / `1.20.1` / `26.1.2` / `all`（默认 all）。
    目录名约定：`1.21.1` → `neoforge-1.21.1`；`1.20.1` → `forge-1.20.1`；`26.1.2` → `neoforge-26.1.2`。

.PARAMETER Root
    仓库根目录（默认当前目录）。

.EXAMPLE
    pwsh -File scripts/verify/verify_probe_class_refs.ps1
    pwsh -File scripts/verify/verify_probe_class_refs.ps1 -Version 1.21.1

.NOTES
    用 pwsh 7 运行（与 `scripts/test/**` 一致）。
    实测：2026-09-22 修 1.20.1 / 1.21.1 两处 `SignSelectionGate` 旧包名后，本脚本从
    `FAIL（2 处）` 转为 `PASS`。
#>
[CmdletBinding()]
param(
    [ValidateSet('1.21.1', '1.20.1', '26.1.2', 'all')]
    [string]$Version = 'all',

    [string]$Root = '.'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue

# 版本 -> 各路径模板
$script:Lines = [ordered]@{
    '1.21.1' = @{
        Proj      = 'neoforge-1.21.1'
        ModsDir   = 'run/1.21.1/mods'
        Artifacts = 'neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235*.jar'
    }
    '1.20.1' = @{
        Proj      = 'forge-1.20.1'
        ModsDir   = 'run/1.20.1/mods'
        Artifacts = 'forge-1.20.1/build/moddev/artifacts/forge-1.20.1-*.jar'
    }
    '26.1.2' = @{
        Proj      = 'neoforge-26.1.2'
        ModsDir   = 'run/26.1.2/mods'
        Artifacts = 'neoforge-26.1.2/build/moddev/artifacts/minecraft-patched-*.jar'
    }
}

# JDK 自带 ⇒ 永不参与判据
$script:JdkPrefixes = @('java.', 'javax.', 'jdk.', 'sun.', 'com.sun.', 'org.w3c.', 'org.xml.')

# 运行期一定在 classpath、但不在本轮收集的 jar 里 ⇒ 登记豁免（每条写明理由）
$script:Whitelist = @(
    'com.mojang.brigadier.',      # 加载器运行期提供（merged 产物里 0 条目）
    'com.mojang.authlib.',        # 同上（GameProfile 等）
    'com.mojang.blaze3d.',        # Blaze3D 由加载器运行期提供
    'com.mojang.datafixers.',     # DataFixerUpper 由加载器运行期提供
    'com.mojang.serialization.'   # DFU 序列化层同上
)

$script:ClassRe = [regex]'Java\.loadClass\(\s*[''"]([A-Za-z_$][A-Za-z0-9_$.]*)[''"]\s*\)'
$script:ModrinthRe = [regex]'maven\.modrinth:([\w.\-]+):([\w.+\-]+)'

# 条目池与失败计数放脚本域：**不要**用「函数返回集合」—— PowerShell 会把函数返回的
# IEnumerable 展平进管道，返回值一多（这里 3 万条 class 条目）语义很难控制，实测踩过坑。
$script:Entries = New-Object 'System.Collections.Generic.HashSet[string]'
$script:BadRefs = New-Object 'System.Collections.Generic.List[object]'

function Get-MtNewestFile {
    <#
    .SYNOPSIS
        按最后写入时间取最新的一份（build/libs 里常残留历史版本 jar，全取会污染判据）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Pattern)

    $hits = @(Get-ChildItem -Path $Pattern -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'sources|javadoc' })
    if ($hits.Count -eq 0) { return $null }
    return ($hits | Sort-Object LastWriteTime -Descending | Select-Object -First 1)
}

function Add-ZipEntries {
    <#
    .SYNOPSIS
        把一个 jar 的全部条目名并入 $script:Entries；读不了返回 $false。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    $zip = $null
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
        foreach ($e in $zip.Entries) { [void]$script:Entries.Add($e.FullName) }
        return $true
    } catch {
        return $false
    } finally {
        if ($zip) { $zip.Dispose() }
    }
}

function Get-GradleModJars {
    <#
    .SYNOPSIS
        解析 `<Proj>/build.gradle` 里 `maven.modrinth:<slug>:<ver>` 形式的依赖，从 gradle 缓存取 jar。
        返回一个 hashtable（单对象，避免数组展平）：@{ Jars = @(...); Unresolved = @(...) }。

    .NOTES
        这些第三方模组（Curios / Patchouli / JEI / Jade…）由 `modImplementation` 在 dev 运行期
        注入 classpath，不落 `run/<版本>/mods` ⇒ 不补这一步会把 `CuriosApi` 之类全判成缺失。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Proj)

    $jars = New-Object 'System.Collections.Generic.List[string]'
    $unresolved = New-Object 'System.Collections.Generic.List[string]'

    $g = Join-Path $Proj 'build.gradle'
    if (-not (Test-Path -LiteralPath $g -PathType Leaf)) {
        [void]$unresolved.Add("(缺 build.gradle: $g)")
        return @{ Jars = $jars.ToArray(); Unresolved = $unresolved.ToArray() }
    }

    $text = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $g).Path)
    $pairs = New-Object 'System.Collections.Generic.List[string]'
    foreach ($m in $script:ModrinthRe.Matches($text)) {
        $key = '{0}|{1}' -f $m.Groups[1].Value, $m.Groups[2].Value
        if (-not $pairs.Contains($key)) { [void]$pairs.Add($key) }
    }

    $cacheRoot = Join-Path (Join-Path $env:USERPROFILE '.gradle') 'caches\modules-2\files-2.1'
    foreach ($k in $pairs) {
        $parts = $k -split '\|'
        $pat = Join-Path (Join-Path (Join-Path $cacheRoot 'maven.modrinth') $parts[0]) "$($parts[1])\*\*.jar"
        $hits = @(Get-ChildItem -Path $pat -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch 'sources' })
        if ($hits.Count -gt 0) {
            [void]$jars.Add(($hits | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName)
        } else {
            [void]$unresolved.Add(('maven.modrinth:{0}:{1}' -f $parts[0], $parts[1]))
        }
    }
    return @{ Jars = $jars.ToArray(); Unresolved = $unresolved.ToArray() }
}

function Get-ClassVariants {
    <#
    .SYNOPSIS
        全限定名 → 可能的 class 条目名（含内嵌类折叠成 `$Inner` 的写法）。

    .NOTES
        探针既可能写顶级类 `a.b.C`，历史上也有人把内嵌类写成 `a.b.C.D`；后者在 Rhino 下并不合法，
        但两种形态都当「存在」处理，避免误报。返回 `[string[]]`，恒非空。
    #>
    [CmdletBinding()]
    [OutputType([string[]])]
    param([Parameter(Mandatory)][string]$Fqn)

    $parts = $Fqn.Split('.')
    $out = New-Object 'System.Collections.Generic.List[string]'
    [void]$out.Add(($parts -join '/') + '.class')
    for ($i = $parts.Count - 1; $i -gt 2; $i--) {
        $head = $parts[0..($i - 1)] -join '/'
        $tail = $parts[$i..($parts.Count - 1)] -join '$'
        # ⚠️ 必须整体加括号：直接写 `Add('{0}${1}.class' -f $head, $tail)` 时 PowerShell 会把
        #    逗号当成**方法实参分隔符**，于是 `-f` 只拿到 $head ⇒ `{1}` 越界报
        #    「设置字符串格式时出错: Index … less than the size of the argument list」。
        [void]$out.Add(('{0}${1}.class' -f $head, $tail))
    }
    return $out.ToArray()
}

function Test-ProbeLine {
    <#
    .SYNOPSIS
        检查一条版本线的探针；报告直接写管道，失败数累加到 $script:BadRefs。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Ver)

    $cfg = $script:Lines[$Ver]
    $proj = Join-Path $Root $cfg.Proj

    # ── classpath 收集 ────────────────────────────────────────────────────
    $jars = New-Object 'System.Collections.Generic.List[string]'
    $modJar = Get-MtNewestFile -Pattern (Join-Path $proj 'build/libs/astral_dice-*.jar')
    if ($modJar) { [void]$jars.Add($modJar.FullName) }
    $art = Get-MtNewestFile -Pattern (Join-Path $Root $cfg.Artifacts)
    if ($art) { [void]$jars.Add($art.FullName) }
    $modsDir = Join-Path $Root $cfg.ModsDir
    $modsJars = @(Get-ChildItem -Path (Join-Path $modsDir '*.jar') -File -ErrorAction SilentlyContinue)
    foreach ($j in $modsJars) { [void]$jars.Add($j.FullName) }
    $dep = Get-GradleModJars -Proj $proj
    foreach ($j in $dep.Jars) { [void]$jars.Add($j) }

    Write-Output ('## {0}   classpath jar = {1} 个（run/mods {2} + gradle 依赖 {3}）' -f `
            $Ver, $jars.Count, $modsJars.Count, @($dep.Jars).Count)
    if (-not $modJar) {
        Write-Output ('   ! 未找到构建产物（{0}/build/libs）—— 该线的模组类判据会大面积误报，先跑构建' -f $cfg.Proj)
    }
    foreach ($u in $dep.Unresolved) { Write-Output ('   ! 依赖未在 gradle 缓存找到：{0}' -f $u) }

    $script:Entries.Clear()
    foreach ($j in $jars) {
        if (-not (Add-ZipEntries -Path $j)) { Write-Output ('   ! 无法读取：{0}' -f $j) }
    }
    Write-Output ('   条目合计 = {0}' -f $script:Entries.Count)

    # ── 扫描探针 ──────────────────────────────────────────────────────────
    $srcDir = Join-Path $Root ('scripts/test/resources/kubejs/' + $Ver)
    if (-not (Test-Path -LiteralPath $srcDir -PathType Container)) {
        Write-Output '   （无探针目录，SKIP）'
        Write-Output ''
        return
    }
    $files = @(Get-ChildItem -LiteralPath $srcDir -Recurse -File -Filter '*.js')
    $checked = 0
    $bad = New-Object 'System.Collections.Generic.List[object]'
    foreach ($f in $files) {
        $text = [System.IO.File]::ReadAllText($f.FullName)
        $seen = New-Object 'System.Collections.Generic.HashSet[string]'
        foreach ($m in $script:ClassRe.Matches($text)) {
            $fqn = $m.Groups[1].Value
            if (-not $seen.Add($fqn)) { continue }

            $skip = $false
            foreach ($p in $script:JdkPrefixes) { if ($fqn.StartsWith($p)) { $skip = $true; break } }
            if (-not $skip) {
                foreach ($p in $script:Whitelist) { if ($fqn.StartsWith($p)) { $skip = $true; break } }
            }
            if ($skip) { continue }

            $checked++
            $found = $false
            foreach ($v in (Get-ClassVariants -Fqn $fqn)) {
                if ($script:Entries.Contains($v)) { $found = $true; break }
            }
            if (-not $found) { [void]$bad.Add([pscustomobject]@{ File = $f.Name; Class = $fqn }) }
        }
    }
    Write-Output ('   脚本 {0} 个 / 参与判据的 loadClass {1} 处 / **不可解析 {2} 处**' -f `
            $files.Count, $checked, $bad.Count)
    foreach ($b in $bad) {
        Write-Output ('     x {0,-40} {1}' -f $b.File, $b.Class)
        Write-Output '       ^ 该类不在运行时 classpath。若它已被下沉到 starengine_lib，须改写为 com.merlinkitsune.starenginelib.*'
        [void]$script:BadRefs.Add($b)
    }
    Write-Output ''
}

$vers = @($Version)
if ($Version -eq 'all') { $vers = @('1.21.1', '1.20.1', '26.1.2') }

foreach ($v in $vers) { Test-ProbeLine -Ver $v }

if ($script:BadRefs.Count -eq 0) {
    Write-Output 'RESULT: PASS —— 三线探针的 Java.loadClass 目标全部可在运行时 classpath 解析'
    exit 0
} else {
    Write-Output ('RESULT: FAIL —— 存在 {0} 处不可解析的类引用（脚本会加载失败 => 所有 type:kubejs 断言全红）' -f `
            $script:BadRefs.Count)
    exit 1
}
