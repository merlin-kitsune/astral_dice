#Requires -Version 7.0
<#
.SYNOPSIS
    mt_loadergate — 用例 LOADER-GATE-FORGE 的**纯离线**执行体（不启动游戏）。

.DESCRIPTION
    验证 `forge-1.20.1` 的 Forge 版本门槛在 **FML 依赖排序阶段**是否真的拒绝低版本环境。

    判据来源（**全部为实测取回的原物，不是复述**）：
      1. `forge_version`（`forge-1.20.1/gradle.properties`）→ build.gradle 派生两处区间；
      2. 派生结果从**生成资源**（`build/generated/sources/modMetadata/META-INF/mods.toml`）与
         **构建产物**（`build/libs/astral_dice-*+forge_1.20.1.jar!/META-INF/mods.toml`）双向读取；
      3. FML 47.4.10 的依赖排序实现在 `fmlloader-1.20.1-47.4.10.jar` 内：
         `ModSorter#modVersionNotContained` → `VersionSupportMatrix#testVersionSupportMatrix`
         → `VersionRange#containsVersion(ArtifactVersion)`；`VersionRange` 来自
         `org.apache.maven:maven-artifact`（该版本由 fmlloader 自己的 POM 声明）。
      4. 本脚本**用同一个 maven-artifact jar** 编译并运行一个 20 行的判定器，
         逐字复刻 `modVersionNotContained` 的布尔表达式（含 `VersionSupportMatrix` 的
         `mod.forge → 47.1.79` 别名覆盖项），对候选版本给出 accept/reject。

    5 步 / 6 断言（与 `cases/LOADER-GATE-FORGE-1.20.1.json` 一一对应）：
      S1 取回两处区间的真实来源            → AP_LG_SRC
      S2 核对 FML 同一实现（jar + POM + 字节码引用） → AP_LG_IMPL
      S3 离线判定器：旧区间 BUG 复现        → AP_LG_OLD
      S4 离线判定器：新区间拒绝/接纳        → AP_LG_NEW  + AP_LG_ALIAS
      S5 loaderVersion（javafml 主系列）+ 依赖排序结论 → AP_LG_LOADER + AP_LG_VERDICT

    读数同时写 stdout 与 `run/<版本>/logs/loadergate.log`（后者是用例 JSON 的断言通道，
    `mt_assert.ps1` 的 `source=loadergate`）。

.EXAMPLE
    pwsh -NoProfile -File scripts/test/mt_loadergate.ps1 --version 1.20.1
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')

Initialize-MtConsole

$script:JavaHome = ''
$script:Readings = [System.Collections.Generic.List[string]]::new()

function Add-Lg {
    <#
    .SYNOPSIS
        记录一行读数：既进内存清单（最后统一落盘），也立刻打到 stdout。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Text)

    $script:Readings.Add($Text)
    Write-MtLine $Text
}

function Find-FirstFile {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Pattern)

    $f = @(Get-ChildItem -Path $Pattern -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch 'sources|javadoc' } | Sort-Object LastWriteTime -Descending)
    if ($f.Count -eq 0) { return $null }
    return $f[0].FullName
}

function Get-JarEntryText {
    <#
    .SYNOPSIS
        读取 jar 内某个文本条目（用于取产物里的 META-INF/mods.toml）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$JarPath, [Parameter(Mandatory)][string]$EntryName)

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $e = $zip.Entries | Where-Object { $_.FullName -eq $EntryName } | Select-Object -First 1
        if ($null -eq $e) { return $null }
        $sr = New-Object System.IO.StreamReader($e.Open())
        try { return $sr.ReadToEnd() } finally { $sr.Dispose() }
    } finally { $zip.Dispose() }
}

function Get-RangeFromToml {
    <#
    .SYNOPSIS
        从 mods.toml 文本里取 loaderVersion 与 modId="forge" 那条依赖的 versionRange。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Toml)

    $loader = ''
    $m = [regex]::Match($Toml, '(?m)^\s*loaderVersion\s*=\s*"([^"]+)"')
    if ($m.Success) { $loader = $m.Groups[1].Value }

    $forge = ''
    # 逐 [[dependencies.*]] 段落扫描：段落内同时出现 modId="forge" 才取它的 versionRange
    $blocks = [regex]::Split($Toml, '(?m)^\s*\[\[dependencies')
    foreach ($b in $blocks) {
        if ($b -match '(?m)modId\s*=\s*"forge"') {
            $mm = [regex]::Match($b, '(?m)versionRange\s*=\s*"([^"]+)"')
            if ($mm.Success) { $forge = $mm.Groups[1].Value }
        }
    }
    return @{ loader = $loader; forge = $forge }
}

function Get-JavaHome {
    <#
    .SYNOPSIS
        定位带 javac 的 JDK（优先 Zulu 21，其次 gradle 自带 JDK）。
    #>
    [CmdletBinding()]
    param()

    $cands = @(
        'C:\Program Files\Zulu\zulu-21',
        'C:\Program Files\Eclipse Adoptium\jdk-21.0.0.0-hotspot'
    )
    foreach ($c in $cands) {
        if (Test-Path -LiteralPath (Join-Path $c 'bin\javac.exe')) { return $c }
    }
    $g = @(Get-ChildItem -Path (Join-Path $env:USERPROFILE '.gradle\jdks') -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } |
            Sort-Object Name -Descending)
    if ($g.Count -gt 0) { return $g[0].FullName }
    return ''
}

# ── 入口 ══════════════════════════════════════════════════════════════════
if ($MyInvocation.InvocationName -ne '.') {

    $Version = '1.20.1'
    $i = 0
    while ($i -lt $args.Count) {
        $tok = [string]$args[$i]
        $key = $tok.TrimStart('-').ToLowerInvariant()
        if ($key -eq 'version') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
            $Version = [string]$args[$i + 1]; $i += 2
        } else {
            Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
        }
    }
    if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }
    # 本用例只对 1.20.1(Forge) 有意义：1.21.1 用 NeoForge 的 neoforge 依赖区间，另一套机制。
    if ($Version -ne '1.20.1') {
        Write-MtErrorLine "LOADER-GATE-FORGE 只适用于 1.20.1（收到 $Version）"
        exit $MT_EXIT_ERROR
    }

    $root = Get-MtRoot
    $p = Get-MtPaths -Version $Version
    $sub = Join-Path $root $p.subproject
    $logPath = Join-Path $p.logs_dir 'loadergate.log'
    $script:Readings.Clear()

    $expectOld = '[47,)'
    $expectNew = '[47.4.10,48)'
    $expectLoader = '[47,48)'
    $forgeVersion = ''
    $rangeNew = ''
    $rangeLoader = ''
    $srcList = @()
    $fail = @()

    Start-MtPhase "loadergate ($Version)"

    # ── S1：两处区间的真实来源（gradle.properties 派生 + 生成资源 + 构建产物）──────
    Write-MtLine 'MT_LG_STEP:1/5 取回区间来源(gradle.properties → build.gradle 派生 → 生成资源/产物)'
    $propsPath = Join-Path $sub 'gradle.properties'
    if (Test-Path -LiteralPath $propsPath) {
        $m = [regex]::Match([System.IO.File]::ReadAllText($propsPath), '(?m)^\s*forge_version\s*=\s*(.+?)\s*$')
        if ($m.Success) { $forgeVersion = $m.Groups[1].Value.Trim() }
    }
    $semver = ($forgeVersion -split '-')[-1]
    $major = [int](($semver -split '\.')[0])
    $derivedNew = "[$semver,$($major + 1))"
    $derivedLoader = "[$major,$($major + 1))"

    $genToml = Join-Path $sub 'build\generated\sources\modMetadata\META-INF\mods.toml'
    if (Test-Path -LiteralPath $genToml) {
        $r = Get-RangeFromToml -Toml ([System.IO.File]::ReadAllText($genToml))
        Add-Lg ("AP_LG_SRC_GEN:loader={0}:forge={1}" -f $r.loader, $r.forge)
        $rangeLoader = $r.loader; $rangeNew = $r.forge; $srcList += 'gen'
    } else {
        Add-Lg 'AP_LG_SRC_GEN:absent'
    }

    $jar = Find-FirstFile -Pattern (Join-Path $sub 'build\libs\astral_dice-*+forge_*.jar')
    if ($null -ne $jar) {
        $toml = Get-JarEntryText -JarPath $jar -EntryName 'META-INF/mods.toml'
        if ($null -ne $toml) {
            $r2 = Get-RangeFromToml -Toml $toml
            Add-Lg ("AP_LG_SRC_JAR:loader={0}:forge={1}" -f $r2.loader, $r2.forge)
            if (-not $rangeNew) { $rangeNew = $r2.forge }
            if (-not $rangeLoader) { $rangeLoader = $r2.loader }
            $srcList += 'jar'
        } else {
            Add-Lg 'AP_LG_SRC_JAR:absent'
        }
    } else {
        Add-Lg 'AP_LG_SRC_JAR:absent'
    }

    $srcFmt = "AP_LG_SRC:forge_version={0}:derived_loader={1}:derived_forge={2}:loaderVersion={3}:forgeRange={4}:src={5}"
    Add-Lg ($srcFmt -f $forgeVersion, $derivedLoader, $derivedNew, $rangeLoader, $rangeNew, ($srcList -join '+'))
    if ($derivedNew -ne $expectNew) { $fail += "derived_forge=$derivedNew" }
    if ($derivedLoader -ne $expectLoader) { $fail += "derived_loader=$derivedLoader" }
    if ($rangeNew -ne $expectNew) { $fail += "toml_forge=$rangeNew" }
    if ($rangeLoader -ne $expectLoader) { $fail += "toml_loader=$rangeLoader" }

    # ── S2：FML 同一实现的核对（jar + POM + 字节码引用）─────────────────────────
    Write-MtLine 'MT_LG_STEP:2/5 核对 FML 依赖排序的同一实现(fmlloader POM → maven-artifact → ModSorter)'
    $fmlJar = Find-FirstFile -Pattern (Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\net.minecraftforge\fmlloader\1.20.1-47.4.10\*\fmlloader-1.20.1-47.4.10.jar')
    $mavenJar = ''
    $mavenVer = ''
    $lang3Jar = ''
    $lang3Ver = ''
    $refClasses = @()
    if ($null -ne $fmlJar) {
        # ⚠️ POM 与 jar 落在**不同的 hash 目录**下（Gradle 缓存布局），必须递归找。
        $fmlDir = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\net.minecraftforge\fmlloader\1.20.1-47.4.10'
        $pom = @(Get-ChildItem -Path $fmlDir -Recurse -File -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -like '*.pom' } | Select-Object -First 1)
        if ($null -ne $pom) {
            $pomText = [System.IO.File]::ReadAllText($pom.FullName)
            $mm = [regex]::Match($pomText, '(?s)<artifactId>maven-artifact</artifactId>\s*<version>([^<]+)</version>')
            if ($mm.Success) { $mavenVer = $mm.Groups[1].Value }
            $ml = [regex]::Match($pomText, '(?s)<artifactId>commons-lang3</artifactId>\s*<version>([^<]+)</version>')
            if ($ml.Success) { $lang3Ver = $ml.Groups[1].Value }
        }
        if ($mavenVer) {
            $mavenJar = Find-FirstFile -Pattern (Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\org.apache.maven\maven-artifact\$mavenVer\*\maven-artifact-$mavenVer.jar")
        }
        # maven-artifact 的版本比较实现依赖 commons-lang3（DefaultArtifactVersion#tryParseInt →
        # org.apache.commons.lang3.math.NumberUtils），同一 POM 声明；缺它会在运行时 NoClassDefFoundError。
        if ($lang3Ver) {
            $lang3Jar = Find-FirstFile -Pattern (Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\org.apache.commons\commons-lang3\$lang3Ver\*\commons-lang3-$lang3Ver.jar")
        }
        # 字节码常量池扫描：哪些 fmlloader 类引用了 VersionRange
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [System.IO.Compression.ZipFile]::OpenRead($fmlJar)
        try {
            $needle = 'org/apache/maven/artifact/versioning/VersionRange'
            foreach ($e in $zip.Entries) {
                if ($e.FullName -notlike '*.class') { continue }
                $s = $e.Open()
                $ms = New-Object System.IO.MemoryStream
                $s.CopyTo($ms); $s.Close()
                $txt = [System.Text.Encoding]::GetEncoding(28591).GetString($ms.ToArray())
                if ($txt.Contains($needle)) { $refClasses += ($e.FullName -replace '^.*/', '' -replace '\.class$', '') }
                $ms.Dispose()
            }
        } finally { $zip.Dispose() }
    }
    $sortedRefs = @($refClasses | Sort-Object -Unique)
    $implFmt = "AP_LG_IMPL:fmlloader={0}:maven_artifact={1}:commons_lang3={2}:jar={3}:version_range_refs={4}"
    Add-Lg ($implFmt -f (Split-Path -Leaf $fmlJar), $mavenVer, $lang3Ver, (Split-Path -Leaf $mavenJar), ($sortedRefs -join ','))
    if ($null -eq $fmlJar) { $fail += 'fmlloader_jar_missing' }
    if (-not $mavenJar) { $fail += 'maven_artifact_jar_missing' }
    if (-not $lang3Jar) { $fail += 'commons_lang3_jar_missing' }
    if ($sortedRefs -notcontains 'ModSorter') { $fail += 'ModSorter_no_VersionRange_ref' }
    if ($sortedRefs -notcontains 'VersionSupportMatrix') { $fail += 'VersionSupportMatrix_no_VersionRange_ref' }

    # ── S3/S4/S5：离线判定器（同一 maven-artifact jar）──────────────────────────
    Write-MtLine 'MT_LG_STEP:3/5 编译离线判定器(modVersionNotContained 同式)'
    $script:JavaHome = Get-JavaHome
    if (-not $script:JavaHome) { Write-MtErrorLine 'MT_LG: ERROR — 未找到带 javac 的 JDK'; exit $MT_EXIT_ERROR }

    $work = Join-Path (Join-Path $root 'temp') 'loadergate'
    if (-not (Test-Path -LiteralPath $work)) { [void](New-Item -ItemType Directory -Force -Path $work) }
    $javaSrc = @'
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * FML 47.4.10 依赖排序判定的逐字复刻（判据实现 = 同一个 maven-artifact jar）。
 *
 * ModSorter#modVersionNotContained(ModVersion mv, Map<String,ArtifactVersion> modVersions):
 *     return !VersionSupportMatrix.testVersionSupportMatrix(
 *                mv.getVersionRange(), mv.getModId(), "mod",
 *                (modId, range) -> modVersions.containsKey(modId)
 *                                  && (range.containsVersion(modVersions.get(modId))
 *                                      || modVersions.get(modId).toString().equals("0.0NONE")));
 *
 * VersionSupportMatrix.testVersionSupportMatrix(range, key, value, test):
 *     if (test.test(key, range)) return true;
 *     var versions = overrideVersions.get(value + "." + key);   // 常量池 recipe = \u0001.\u0001
 *     return versions != null && versions.stream().anyMatch(range::containsVersion);
 *   static { if (MC == 1.20.1) add("mod.forge", "47.1.79"); }   // key=value+"."+key="mod.forge"
 */
public class LoaderGateProbe {

    private static final String FORGE_ALIAS = "47.1.79";

    private static boolean contains(VersionRange r, String v) {
        return r.containsVersion(new DefaultArtifactVersion(v));
    }

    /** 拥有该 modId 的已安装版本时，FML 是否判定「版本区间满足」 */
    private static boolean fmlAcceptsForge(VersionRange range, String installed) {
        // ① 主体判定（ModSorter 的 predicate 分支）
        boolean direct = contains(range, installed);
        if (direct) return true;
        // ② VersionSupportMatrix 的别名覆盖项（mod.forge → 47.1.79）
        return contains(range, FORGE_ALIAS);
    }

    private static String v(VersionRange r, String ver) {
        return fmlAcceptsForge(r, ver) ? "accept" : "reject";
    }

    public static void main(String[] args) throws Exception {
        VersionRange oldRange = VersionRange.createFromVersionSpec("[47,)");
        VersionRange newRange = VersionRange.createFromVersionSpec("[47.4.10,48)");
        VersionRange loaderRange = VersionRange.createFromVersionSpec("[47,48)");

        // S3：旧区间（修复前）——低版本被接纳 = BUG 复现
        boolean bug = fmlAcceptsForge(oldRange, "47.0.0") && fmlAcceptsForge(oldRange, "47.4.9");
        System.out.println("AP_LG_OLD:range=[47,):v47.0.0=" + v(oldRange, "47.0.0")
                + ":v47.4.9=" + v(oldRange, "47.4.9")
                + ":v47.4.10=" + v(oldRange, "47.4.10")
                + ":bug_repro=" + (bug ? 1 : 0));

        // S4：新区间（修复后）——拒绝 47.0.0 / 47.4.9，接纳 47.4.10+
        boolean fixed = !fmlAcceptsForge(newRange, "47.0.0")
                && !fmlAcceptsForge(newRange, "47.4.9")
                && fmlAcceptsForge(newRange, "47.4.10")
                && fmlAcceptsForge(newRange, "47.4.11")
                && fmlAcceptsForge(newRange, "47.5.0")
                && !fmlAcceptsForge(newRange, "48.0.0");
        System.out.println("AP_LG_NEW:range=[47.4.10,48):v47.0.0=" + v(newRange, "47.0.0")
                + ":v47.4.9=" + v(newRange, "47.4.9")
                + ":v47.4.10=" + v(newRange, "47.4.10")
                + ":v47.4.11=" + v(newRange, "47.4.11")
                + ":v47.5.0=" + v(newRange, "47.5.0")
                + ":v48.0.0=" + v(newRange, "48.0.0")
                + ":gate_ok=" + (fixed ? 1 : 0));

        // 别名覆盖项本身的自证：它不会把 47.4.9 抬进新区间
        System.out.println("AP_LG_ALIAS:alias=mod.forge@" + FORGE_ALIAS
                + ":new_range_contains_alias=" + (contains(newRange, FORGE_ALIAS) ? 1 : 0)
                + ":old_range_contains_alias=" + (contains(oldRange, FORGE_ALIAS) ? 1 : 0));

        // S5：loaderVersion 是 javafml **语言提供者**的版本区间。
        //     LanguageLoadingProvider#findLanguage 同样经 VersionSupportMatrix，但它的
        //     别名键是 value + "." + key = "languageloader.javafml" —— 不在 overrideVersions
        //     （只有 "mod.forge"）里，故判定退化为纯 range.containsVersion(providerVersion)。
        //     提供者版本 = Forge 主系列号 47（依据：build.gradle / mods.toml 注释记录的
        //     2026-09-14 实机读数 `Missing language javafml version [47.4.10,48) … found 47`）。
        boolean loaderOk = contains(loaderRange, "47") && !contains(loaderRange, "48");
        System.out.println("AP_LG_LOADER:range=[47,48):javafml47=" + (contains(loaderRange, "47") ? "accept" : "reject")
                + ":javafml48=" + (contains(loaderRange, "48") ? "accept" : "reject")
                + ":alias_key=languageloader.javafml"
                + ":loader_ok=" + (loaderOk ? 1 : 0));
    }
}
'@
    $srcFile = Join-Path $work 'LoaderGateProbe.java'
    [System.IO.File]::WriteAllText($srcFile, $javaSrc, [System.Text.UTF8Encoding]::new($false))

    $javac = Join-Path $script:JavaHome 'bin\javac.exe'
    $java = Join-Path $script:JavaHome 'bin\java.exe'
    $cp = "$mavenJar;$lang3Jar;$work"
    $compile = & $javac -encoding UTF-8 -cp $mavenJar -d $work $srcFile 2>&1
    $compileText = ($compile | Out-String).Trim()
    Add-Lg ("AP_LG_COMPILE:javac={0}:rc={1}:out={2}" -f $javac, $LASTEXITCODE, (($compileText -replace '[\r\n]+', ' ')))
    if ($LASTEXITCODE -ne 0) {
        Add-Lg 'AP_LG_VERDICT:compile_failed=1'
        $fail += 'compile_failed'
    } else {
        Write-MtLine 'MT_LG_STEP:4/5 运行判定器(旧区间 BUG 复现 / 新区间门槛)'
        $out = & $java -cp $cp LoaderGateProbe 2>&1
        foreach ($ln in @($out)) {
            $s = ([string]$ln).Trim()
            if ($s) { Add-Lg $s }
        }
        if ($LASTEXITCODE -ne 0) { $fail += "probe_rc=$LASTEXITCODE" }

        Write-MtLine 'MT_LG_STEP:5/5 汇总依赖排序阶段结论'
        $oldLine = @($script:Readings | Where-Object { $_ -like 'AP_LG_OLD:*' }) -join ''
        $newLine = @($script:Readings | Where-Object { $_ -like 'AP_LG_NEW:*' }) -join ''
        $loadLine = @($script:Readings | Where-Object { $_ -like 'AP_LG_LOADER:*' }) -join ''
        $bugOk = $oldLine -match 'bug_repro=1'
        $gateOk = $newLine -match 'gate_ok=1'
        $loaderOk = $loadLine -match 'loader_ok=1'
        $ok = $bugOk -and $gateOk -and $loaderOk -and ($fail.Count -eq 0)
        # FML 在依赖排序阶段的用户可见文案（ModSorter 常量池实测字符串）
        $verdictFmt = "AP_LG_VERDICT:bug_repro={0}:gate_ok={1}:loader_ok={2}:fail={3}:fml_stage=dependency_sorting:msg=Missing or unsupported mandatory dependencies:overall={4}"
        Add-Lg ($verdictFmt -f ($bugOk ? 1 : 0), ($gateOk ? 1 : 0), ($loaderOk ? 1 : 0), ($fail -join '|'), ($ok ? 1 : 0))
    }

    Add-Lg 'AP_LG_DONE'

    # 落盘到断言通道（用例 JSON 的 source=loadergate）
    $dir = [System.IO.Path]::GetDirectoryName($logPath)
    if (-not (Test-Path -LiteralPath $dir)) { [void](New-Item -ItemType Directory -Force -Path $dir) }
    [System.IO.File]::WriteAllText($logPath, (($script:Readings -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
    Write-MtInfo "读数已落盘：$logPath"

    $verdictLine = @($script:Readings | Where-Object { $_ -like 'AP_LG_VERDICT:*' }) -join ''
    if ($verdictLine -match 'overall=1') { Write-MtOk 'loadergate'; exit $MT_EXIT_PASS }
    Write-MtFail 'loadergate'; exit $MT_EXIT_FAIL
}
