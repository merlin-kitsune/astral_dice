<#
.SYNOPSIS
    探针类引用守门：核对 KubeJS 探针里 `Java.loadClass(...)` 的目标是否真的存在于运行时 classpath。

.DESCRIPTION
    **为什么需要这道门（2026-09-22 实测事故）**：1.20.1 / 1.21.1 两线的探针写的是
    `Java.loadClass("com.merlinkitsune.astral_dice.target.SignSelectionGate")`，而该类早已随
    「平台无关能力下沉」搬到了前置库（`com.merlinkitsune.starengine.target.SignSelectionGate`）。
    后果是**连锁的、且很容易误判**：
      · KubeJS 在加载脚本时抛 `EvaluatorException: Failed to load Java class … Class could not be found!`
        ⇒ `logs/kubejs/server.log` 出现 ERROR；
      · 于是**每一条**用例的 `type: kubejs` 断言（"server.log 0 errors"）**全红**；
      · 而失败信息是「KubeJS 有错误」，不是「某条命令不存在」⇒ 现场读起来像
        「模组有 bug」或「用例写错了」，而真实原因是一行**从没被跑到的**类加载语句。
    这类「类被搬走、探针没跟」的漂移，靠人眼审 14k 行脚本不可行，必须机器拦。

    判据：对每个 `Java.loadClass("X.Y.Z")`，要求 `X/Y/Z.class`（或其内嵌类形式 `X/Y/Z$Inner.class`）
    出现在下面五处之一：
      1. 本模组构建产物 `build/libs/astral_dice-*.jar`（**取最新一份** —— build/libs 常残留历史版本）；
      2. `run/<版本>/mods/*.jar` 全量（Curios / Patchouli / KubeJS / Rhino / starengine 都在这）；
      3. `build/moddev/artifacts` 的 merged jar（MC 本体 + 加载器）。
      4. 上述任意外层 jar 里 **JarJar 内嵌 jar**（`META-INF/jarjar/*.jar`）的全部条目 —— 见下条；
      5. `mavenLocal` 的 StarEngine 库 jar（`~/.m2/.../starengine-<平台>/<starengine_version>/*.jar`）——
         P0 两线已取消 JarJar 内嵌，dev 的库类只在 mavenLocal / Gradle 缓存里。
    另外补入 `build.gradle` 里 `maven.modrinth:<slug>:<ver>` 形式的依赖（从 gradle 缓存取 jar）——
    这些第三方模组由 `modImplementation` 在 **dev 运行期**注入 classpath，**不落 `run/mods`**，
    不补这一步会把 `CuriosApi` 之类全判成缺失（实测过）。

    ⚠️ **为什么第 4/5 处必须有（2026-10-20 新口径，此前 17 处假红）**：前置库 starengine 的承载方式
    按线不同 —— 26.1.2 仍以 JarJar 形式**内嵌**进产物（`META-INF/jarjar/`，第 4 处展开）；
    P0 两线（neoforge-1.21.1 / forge-1.20.1）已改为**独立前置**（`build.gradle` 的 `jarJar` 已删），
    库类**既不在外层 jar 的条目里、也不一定落 `run/<版本>/mods`**（dev 由 Gradle 从 `mavenLocal`
    解析进 runtimeClasspath；run/mods 那份要靠 Start-SelfTest 成对投放）。只看 1–4 就会把
    `GameplayConstants` / `StarCoinWalletState` / `BossEntityUtil` / `SignSelectionGate` /
    `TargetSelectionRegistry` 整批判成「不在运行时 classpath」——而它们实际都在，属**脚本盲区**而非真缺陷。
    第 5 处从 mavenLocal 取与 `gradle.properties:starengine_version` **完全同名**的库 jar：
    依次尝试 `$HOME/.m2` / `%USERPROFILE%\.m2`（Windows）与 `/mnt/c/Users/<user>/.m2`（WSL）。
    递归深度上限 `$script:MaxJarDepth`（防自引用内嵌导致的无限递归）。

    已登记的豁免（运行期一定存在，但不在上面 1–5 的收集范围）：
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
    实测：2026-09-27 补「JarJar 内嵌 jar 展开」后，`verify_probe_class_refs` 的 17 处
    `com.merlinkitsune.starengine.*` 假红清零（此前它把内嵌库整个当不存在）。
    实测：2026-10-20 P0 两线取消内嵌后，补「mavenLocal 库 jar」收集，避免上述 17 处假红回归。
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
        LibArtifact = 'starengine-neoforge-1.21.1'
        ModsDir   = 'run/1.21.1/mods'
        Artifacts = 'neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235*.jar'
    }
    '1.20.1' = @{
        Proj      = 'forge-1.20.1'
        LibArtifact = 'starengine-forge-1.20.1'
        ModsDir   = 'run/1.20.1/mods'
        Artifacts = 'forge-1.20.1/build/moddev/artifacts/forge-1.20.1-*.jar'
    }
    '26.1.2' = @{
        Proj      = 'neoforge-26.1.2'
        LibArtifact = 'starengine-neoforge-26.1.2'
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
    'com.mojang.serialization.',  # DFU 序列化层同上
    'org.lwjgl.'                  # LWJGL 由加载器/启动器在运行期提供，版本随 MC 钉死、不进 build.gradle；
                                  # 实测 gradle 缓存 lwjgl-glfw-3.3.x.jar 内含 org/lwjgl/glfw/GLFW.class（2026-10-22 取证）
)

$script:ClassRe = [regex]'Java\.loadClass\(\s*[''"]([A-Za-z_$][A-Za-z0-9_$.]*)[''"]\s*\)'
$script:ModrinthRe = [regex]'maven\.modrinth:([\w.\-]+):([\w.+\-]+)'
# Curse Maven：curse.maven:<slug>-<projectId>:<fileId>；缓存路径 = curse.maven/<group>/<fileId>/<sha1>/<file>.jar
# （2026-10-22 补：JEI 自 2026-09-17 改走 Curse Maven，原实现只补 maven.modrinth ⇒ JEI 的
#  mezz.jei.common.Internal 被误判「不在运行时 classpath」，实测该 jar 内含该类）
$script:CurseRe = [regex]'curse\.maven:([\w.\-]+):(\d+)'
# JarJar 内嵌 jar 的递归深度上限（本模组只有 1 层；上限只为防自引用内嵌导致的无限递归）
$script:MaxJarDepth = 3

# 条目池与失败计数放脚本域：**不要**用「函数返回集合」—— PowerShell 会把函数返回的
# IEnumerable 展平进管道，返回值一多（这里 3 万条 class 条目）语义很难控制，实测踩过坑。
$script:Entries = New-Object 'System.Collections.Generic.HashSet[string]'
$script:BadRefs = New-Object 'System.Collections.Generic.List[object]'
$script:NoProbeDir = $false   # 任一版本无探针目录 ⇒ 无法判定（防「SKIP ⇒ BadRefs 空 ⇒ PASS」的假绿）

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

function Add-NestedJarEntries {
    <#
    .SYNOPSIS
        递归展开一个 zip 流里的 JarJar 内嵌 jar，把它们的条目并入 $script:Entries。

    .NOTES
        ⚠️ 本模组把 starengine 内嵌成 `META-INF/jarjar/<name>.jar` ⇒ 那批类既不在外层 jar
        的条目里、也不落 `run/<版本>/mods`。不展开 ⇒ 整库的类被误判为缺失（2026-09-27 实测 17 处）。
        本函数**不输出任何管道值**（全部 `[void]` / `Dispose()` 均为 void），避免污染调用方的布尔返回。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Stream,
        [Parameter(Mandatory)][int]$Depth
    )

    $za = $null
    try {
        # leaveOpen = $true：流的释放交给调用方（否则内层 Dispose 会把 MemoryStream 一并关掉）
        $za = New-Object System.IO.Compression.ZipArchive($Stream, [System.IO.Compression.ZipArchiveMode]::Read, $true)
        foreach ($e in $za.Entries) { [void]$script:Entries.Add($e.FullName) }
        if ($Depth -ge $script:MaxJarDepth) { return }
        foreach ($e in $za.Entries) {
            if ($e.FullName -notmatch '^META-INF/jarjar/.+\.jar$') { continue }
            $ms = New-Object System.IO.MemoryStream
            try {
                $src = $null
                try {
                    $src = $e.Open()
                    [void]$src.CopyTo($ms)
                } finally {
                    if ($src) { $src.Dispose() }
                }
                [void]$ms.Seek(0, [System.IO.SeekOrigin]::Begin)
                Add-NestedJarEntries -Stream $ms -Depth ($Depth + 1)
            } finally {
                $ms.Dispose()
            }
        }
    } catch {
        # 内嵌 jar 读不了不改变外层结论（外层条目已经并入）
    } finally {
        if ($za) { $za.Dispose() }
    }
}

function Add-ZipEntries {
    <#
    .SYNOPSIS
        把一个 jar（**含其 JarJar 内嵌 jar**）的全部条目名并入 $script:Entries；读不了返回 $false。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    $zip = $null
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
        foreach ($e in $zip.Entries) { [void]$script:Entries.Add($e.FullName) }
        foreach ($e in $zip.Entries) {
            if ($e.FullName -notmatch '^META-INF/jarjar/.+\.jar$') { continue }
            $ms = New-Object System.IO.MemoryStream
            try {
                $src = $null
                try {
                    $src = $e.Open()
                    [void]$src.CopyTo($ms)
                } finally {
                    if ($src) { $src.Dispose() }
                }
                [void]$ms.Seek(0, [System.IO.SeekOrigin]::Begin)
                Add-NestedJarEntries -Stream $ms -Depth 1
            } finally {
                $ms.Dispose()
            }
        }
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
    # 注释行里被注掉的依赖不得参与解析（实测 forge-1.20.1/build.gradle 有一行注释掉的 immediatelyfast 被误报成未解析）
    $text = Remove-JsComments $text
    $pairs = New-Object 'System.Collections.Generic.List[string]'
    $cursePairs = New-Object 'System.Collections.Generic.List[string]'
    foreach ($m in $script:ModrinthRe.Matches($text)) {
        $key = '{0}|{1}' -f $m.Groups[1].Value, $m.Groups[2].Value
        if (-not $pairs.Contains($key)) { [void]$pairs.Add($key) }
    }
    foreach ($m in $script:CurseRe.Matches($text)) {
        $key = '{0}|{1}' -f $m.Groups[1].Value, $m.Groups[2].Value
        if (-not $cursePairs.Contains($key)) { [void]$cursePairs.Add($key) }
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
    foreach ($k in $cursePairs) {
        $parts = $k -split '\|'
        $pat = Join-Path (Join-Path (Join-Path $cacheRoot 'curse.maven') $parts[0]) "$($parts[1])\*\*.jar"
        $hits = @(Get-ChildItem -Path $pat -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch 'sources' })
        if ($hits.Count -gt 0) {
            [void]$jars.Add(($hits | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName)
        } else {
            [void]$unresolved.Add(('curse.maven:{0}:{1}' -f $parts[0], $parts[1]))
        }
    }
    return @{ Jars = $jars.ToArray(); Unresolved = $unresolved.ToArray() }
}

function Remove-JsComments {
    <#>
    .SYNOPSIS
        单趟、字符串感知地剥掉 JS/Groovy 注释，只对代码里的引用判据。

    .NOTES
        2026-10-22 二次修复（独立复核 R3-④ REFUTED）：旧实现「先全文删块注释、再删行注释」会误删真代码，两个可复现反例：
        ① var s = "http://host//x"; Java.loadClass("good.ClassE");  → 只保护了 ://，URL 里第二个 // 把同行真代码吃掉；
        ② scripts/test/resources/kubejs/1.21.1/server_scripts/astral_hostile_probe.js:18 的行注释里出现块注释开头符，旧实现一路吃到 L77 的结束符。
        现实现 = 单趟扫描：字符串字面量（双引号/单引号，含转义）原样保留 → 块注释替换成等量换行（保行号）→ 行注释丢到行尾。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)

    $LF = [char]10
    $BS = [char]92
    $sb = New-Object System.Text.StringBuilder ($Text.Length + 16)
    $n = $Text.Length
    $i = 0
    $quote = [char]0
    while ($i -lt $n) {
        $c = $Text[$i]
        if ($quote -ne [char]0) {
            [void]$sb.Append($c)
            if ($c -eq $BS -and ($i + 1) -lt $n) { [void]$sb.Append($Text[$i + 1]); $i += 2; continue }
            if ($c -eq $quote) { $quote = [char]0 }
            $i++
            continue
        }
        if ([int]$c -eq 34 -or [int]$c -eq 39) { $quote = $c; [void]$sb.Append($c); $i++; continue }
        if ($c -eq '/' -and ($i + 1) -lt $n) {
            $d = $Text[$i + 1]
            if ($d -eq '*') {
                $end = $Text.IndexOf('*/', $i + 2)
                if ($end -lt 0) { break }
                $block = $Text.Substring($i, $end + 2 - $i)
                $nlCount = @($block.ToCharArray() | Where-Object { $_ -eq $LF }).Count
                if ($nlCount -gt 0) { [void]$sb.Append($LF.ToString() * $nlCount) }
                $i = $end + 2
                continue
            }
            if ($d -eq '/') {
                $eol = $Text.IndexOf($LF, $i)
                if ($eol -lt 0) { break }
                $i = $eol
                continue
            }
        }
        [void]$sb.Append($c)
        $i++
    }
    return $sb.ToString()
}

function Get-GradleProperty {
    <#
    .SYNOPSIS
        从 gradle.properties 读一个键（缺文件 / 缺键返回 $null）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Key
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    $text = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $Path).Path)
    $m = [regex]::Match($text, '(?m)^\s*' + [regex]::Escape($Key) + '\s*=\s*(.+?)\s*$')
    if (-not $m.Success) { return $null }
    return $m.Groups[1].Value.Trim()
}

function Get-M2RepositoryRoots {
    <#
    .SYNOPSIS
        mavenLocal 仓库根候选：Windows（$HOME / %USERPROFILE% / C:\Users\<user>）与 WSL（/mnt/c/Users/<user>）。

    .NOTES
        只做路径规范化与去重；是否存在由调用方判断（便于在输出里给出「已尝试」清单）。
    #>
    [CmdletBinding()]
    [OutputType([string[]])]
    param()

    $cands = New-Object 'System.Collections.Generic.List[string]'
    if ($HOME) { [void]$cands.Add((Join-Path $HOME '.m2/repository')) }
    if ($env:USERPROFILE) { [void]$cands.Add((Join-Path $env:USERPROFILE '.m2/repository')) }
    # 用户名的两个来源都试（Windows: USERNAME；WSL: USER 可能是 Linux 用户名）⇒ 各自补一条 Windows 家目录候选
    $unames = New-Object 'System.Collections.Generic.List[string]'
    if ($env:USERNAME) { [void]$unames.Add($env:USERNAME) }
    if ($env:USER -and $env:USER -ne $env:USERNAME) { [void]$unames.Add($env:USER) }
    foreach ($uname in $unames) {
        # PowerShell 7：$IsLinux 恒可用；5.1 下先由版本号短路，避免引用未定义变量
        if ($PSVersionTable.PSVersion.Major -ge 6 -and $IsLinux) {
            [void]$cands.Add("/mnt/c/Users/$uname/.m2/repository")
        } else {
            [void]$cands.Add("C:\Users\$uname\.m2\repository")
        }
    }

    $roots = New-Object 'System.Collections.Generic.List[string]'
    foreach ($c in $cands) {
        if ([string]::IsNullOrWhiteSpace($c)) { continue }
        try { $full = [System.IO.Path]::GetFullPath($c) } catch { continue }
        if (-not $roots.Contains($full)) { [void]$roots.Add($full) }
    }
    return $roots.ToArray()
}

function Get-MavenLocalLibJar {
    <#
    .SYNOPSIS
        按 `gradle.properties:starengine_version` 在 mavenLocal 找该线的 StarEngine 库 jar。

    .NOTES
        P0 两线已取消 JarJar 内嵌 ⇒ 库类只能从 mavenLocal / Gradle 缓存进 classpath。
        文件名与 starengine_version **完全一致**（不做通配），避免拿到历史版本。
        返回单对象 hashtable（避免数组展平）：@{ Jar = <路径或 $null>; Version = <版本或 $null>; Tried = @(<尝试过的路径>) }。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Proj,
        [Parameter(Mandatory)][string]$Artifact
    )

    $ver = Get-GradleProperty -Path (Join-Path $Proj 'gradle.properties') -Key 'starengine_version'
    $tried = New-Object 'System.Collections.Generic.List[string]'
    if (-not $ver) { return @{ Jar = $null; Version = $null; Tried = $tried.ToArray() } }

    $rel = [System.IO.Path]::Combine('com', 'merlinkitsune', 'starengine', $Artifact, $ver, "$Artifact-$ver.jar")
    foreach ($root in (Get-M2RepositoryRoots)) {
        $p = [System.IO.Path]::Combine($root, $rel)
        [void]$tried.Add($p)
        if (Test-Path -LiteralPath $p -PathType Leaf) {
            return @{ Jar = $p; Version = $ver; Tried = $tried.ToArray() }
        }
    }
    return @{ Jar = $null; Version = $ver; Tried = $tried.ToArray() }
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
    # 第 5 处：mavenLocal 的 StarEngine 库 jar（P0 两线取消内嵌后，dev 的库类只在这里）
    $libInfo = Get-MavenLocalLibJar -Proj $proj -Artifact $cfg.LibArtifact
    if ($libInfo.Jar) { [void]$jars.Add($libInfo.Jar) }

    Write-Output ('## {0}   classpath jar = {1} 个（run/mods {2} + gradle 依赖 {3} + mavenLocal 库 {4}）' -f `
            $Ver, $jars.Count, $modsJars.Count, @($dep.Jars).Count, $(if ($libInfo.Jar) { 1 } else { 0 }))
    if ($libInfo.Jar) {
        Write-Output ('   + 库 jar 来自 mavenLocal：{0}' -f $libInfo.Jar)
    } else {
        $libWhy = if ($libInfo.Version) { "starengine_version=$($libInfo.Version)" } else { 'gradle.properties 无 starengine_version' }
        Write-Output ('   ! mavenLocal 未找到库 jar（{0}）：com.merlinkitsune.starengine.* 可能误报；请安装到 mavenLocal 或把库 jar 放进 run/<版本>/mods' -f $libWhy)
        foreach ($t in $libInfo.Tried) { Write-Output ('     - 已尝试：{0}' -f $t) }
    }
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
        Write-Output '   [FATAL] 无探针目录 ⇒ 无法判定（ROOT 错或路径约定变化）；不得判 PASS'
        $script:NoProbeDir = $true
        Write-Output ''
        return
    }
    $files = @(Get-ChildItem -LiteralPath $srcDir -Recurse -File -Filter '*.js')
    if ($files.Count -eq 0) {
        Write-Output '   [FATAL] 探针目录存在但 0 个 .js 脚本 ⇒ 无法判定（路径约定变化 / 资源缺失）；不得判 PASS'
        $script:NoProbeDir = $true
        Write-Output ''
        return
    }
    $checked = 0
    $bad = New-Object 'System.Collections.Generic.List[object]'
    foreach ($f in $files) {
        $text = Remove-JsComments ([System.IO.File]::ReadAllText($f.FullName))
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
        Write-Output '       ^ 该类不在运行时 classpath。若它已被下沉到 starengine，须改写为 com.merlinkitsune.starengine.*'
        [void]$script:BadRefs.Add($b)
    }
    Write-Output ''
}

$vers = @($Version)
if ($Version -eq 'all') { $vers = @('1.21.1', '1.20.1', '26.1.2') }

foreach ($v in $vers) { Test-ProbeLine -Ver $v }

if ($script:NoProbeDir) {
    Write-Output 'RESULT: ERROR（无法判定）—— 至少一条线无探针目录或目录内 0 个 .js 脚本，未核到任何类引用'
    [Console]::Out.Flush()
    exit 2
}
if ($script:BadRefs.Count -eq 0) {
    Write-Output 'RESULT: PASS —— 三线探针的 Java.loadClass 目标全部可在运行时 classpath 解析'
    exit 0
} else {
    Write-Output ('RESULT: FAIL —— 存在 {0} 处不可解析的类引用（脚本会加载失败 => 所有 type:kubejs 断言全红）' -f `
            $script:BadRefs.Count)
    exit 1
}
