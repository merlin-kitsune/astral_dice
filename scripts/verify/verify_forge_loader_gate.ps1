#Requires -Version 7.0
<#
.SYNOPSIS
    verify_forge_loader_gate — 1.20.1「加载器版本门槛 + Mixin Booster 硬前置」的**独立**静态守门。

.DESCRIPTION
    **为什么独立**：该门槛原由 `scripts/test/mt_loadergate.ps1`（mt 用例链的纯离线执行体）承载，
    读数落 `run/1.20.1/logs/loadergate.log`，再由已删用例 `cases/LOADER-GATE-FORGE-1.20.1.json`
    的 `log` 断言消费。2026-09-19 用户裁决：「将 1.20.1 加载器版本门槛 / Mixin Booster 硬前置
    **单独置于独立脚本**」（= 不恢复该用例）。⇒ 本脚本是唯一落点，**零 mt 依赖**：
      ① 不 `Import-Module scripts/test/lib/*`；② 不读写 `run/**`（不再有 loadergate.log 通道）；
      ③ 不读任何 `cases/*.json`；④ 不用 mt 退出码常量（退出码 = 本脚本自定的 0/1/2）。
    判据本体与旧执行体**逐条等价**（S1..S6），读数前缀沿用 `AP_LG_*` 便于对照历史取证。

    **判据（S1..S6）**
      S1 区间来源链：`forge-1.20.1/gradle.properties` 的 `forge_version`
          （当前 `1.20.1-47.4.10`）→ 派生 `loaderVersion=[<major>,<major+1)>`（`[47,48)`）与
          `modId="forge"` 依赖区间 `[<semver>,<major+1)>`（`[47.4.10,48)`）；三段来源
          （模板 / 生成资源 / 构建产物 jar 内 `META-INF/mods.toml`）取回并要求与派生值一致。
          ⚠️ 模板经 Groovy `SimpleTemplateEngine` 展开，其中是 `${...}` 占位 ⇒ **模板段的
          区间取不到字面值属于正常**（只报 `placeholder`，不判失败），与旧执行体同一容忍口径。
      S2 Mixin Booster 硬前置：三段一致地声明 `modId="mixinbooster"` + `mandatory=true` +
          `versionRange="[0.1.3,)"` + `ordering="AFTER"` + `side="BOTH"`；`forge-1.20.1/build.gradle`
          仍以 `maven.modrinth:mixinbooster` 装配；模板内不得出现**未注释**的 `reason=` 行
          （Forge 1.20.1 的依赖段不支持该键）。前置**真实版本**读自
          `~/.gradle/caches/.../maven.modrinth/mixinbooster/**/*.jar!mixinbooster_version.txt`
          （实机 = `0.1.3+1.20.1`，即 FML 运行时看到的版本）。
      S3 同一实现核对：`fmlloader-<forge_version>.jar` 存在；从它的 POM（与 jar 落在**不同 hash
          目录**，须递归找）取 `maven-artifact` / `commons-lang3` 版本并解析到 jar；对 fmlloader
          jar 做字节码常量池扫描，确认引用 `org/apache/maven/artifact/versioning/VersionRange`
          的类含 `ModSorter` 与 `VersionSupportMatrix`。
      S4/S5 离线判定器：用**同一个** `maven-artifact` jar（classpath 加 `commons-lang3`）编译并运行
          `LoaderGateProbe`，逐字复刻 `ModSorter#modVersionNotContained` +
          `VersionSupportMatrix#testVersionSupportMatrix`（含 `mod.forge → 47.1.79` 别名覆盖项）：
            · 旧区间 `[47,)` 接纳 `47.0.0`/`47.4.9` ⇒ `bug_repro=1`（修复前 BUG 的**反证**，必须保留）；
            · 新区间拒绝 `47.0.0`/`47.4.9`、接纳 `47.4.10`/`47.4.11`/`47.5.0`、拒绝 `48.0.0`；
            · loaderVersion 接纳 javafml `47`、拒绝 `48`（别名键 `languageloader.javafml` **不在**
              overrideVersions，走纯 `containsVersion`）；
            · Mixin Booster 区间 `[0.1.3,)` 对前置 jar 内**真实版本** accept，`0.1.3`/`0.1.4`/`0.2.0`
              accept，`0.1.2`/`0.1.0`/`0.0.9` reject（它**不在** overrideVersions，禁止套 forge 别名回退）。
          ⚠️ 判定器的三条区间入参由本脚本从上述**派生/声明值**传入（不写死在 Java 里），
          `forge_version` 变更时先卡 S1、再让判定器直接判**实际声明值**。
      S6 聚合：任一判据不成立 ⇒ 非零退出；各失败项以 `LG_FAIL:<key>` 逐条打印。

    **退出码契约与优先级（T19-02）**
      0 = 门槛成立（`AP_LG_VERDICT:...:range_only=1:overall=1`）；
      1 = 存在**明确偏差**（逐条 `LG_FAIL:<key>`）：`LG_FAIL` 列表**非空即判 1**，
          且 verdict 为 `AP_LG_VERDICT:...:overall=0`；
      2 = **无法判定**（仅当 `LG_FAIL` 为空**且**存在 `LG_UNDECIDABLE:<key>`）：缺带 javac 的 JDK /
          缺 fmlloader 或 maven-artifact 或 mixinbooster 缓存 / 缺构建产物且未给 `-SkipArtifacts` /
          `-Root` 不是 forge 子项目所在树；每条都带 `LG_REMEDY:<key>:<补充命令>`，
          且 verdict 为 `AP_LG_VERDICT:...:overall=2`。
      ⚠️ **优先级：明确偏差优先于无法判定**——只要 `LG_FAIL` 非空就 **exit 1**，**即使同时存在**
      `LG_UNDECIDABLE`。理由：`forge_version` 漂移、MB 依赖段被删/被改弱（如 `versionRange="*"`）、
      `loaderVersion` 写错这类是**门槛不成立**（要改仓库的配置/代码偏差），不是「环境没准备好」；
      若让 exit 2 无条件胜出，CI 与日志读者会把真实偏差误当环境问题。
      ⇒ **两类 key 并存时都会被打印**：逐条 `LG_FAIL:*`、逐条 `LG_UNDECIDABLE:*` 与
      `LG_REMEDY:*` 全部输出，且 verdict 行同时带 `:fail=<keys>` 与 `:undecidable=<keys>`，
      读者不必猜（实测：`forge_version` 漂移夹具 + 缺构建产物 ⇒ 三类行齐出、exit 1）。

    **区间子判据字段的覆盖范围（T19-01；字段名不得改）**
      `AP_LG_VERDICT` 的 `bug_repro` / `gate_ok` / `loader_ok` / `mb_ok` **只代表离线判定器**
      （`LoaderGateProbe`：逐字复刻 `ModSorter#modVersionNotContained` + `VersionSupportMatrix`）
      **对「版本区间」这一维**的结论：`[47,)` 是否复现修复前 BUG、`[47.4.10,48)` 的拒绝/接纳边界、
      `loaderVersion=[47,48)` 对 javafml 47/48 的判定、`[0.1.3,)` 对前置**真实版本**的判定。
      它们**不覆盖**：MB 依赖段的 `mandatory` / `ordering` / `side` / `versionRange` 声明是否正确、
      `build.gradle` 是否仍装配 `maven.modrinth:mixinbooster`、模板是否存在、三段是否一致 ——
      这些由 S1/S2 的独立失败项（`LG_FAIL:mb_*` / `LG_FAIL:toml_*` / `LG_FAIL:tmpl_*` 等）表达。
      ⇒ **`mb_ok=1` 不等于「MB 一切正常」**。用夹具 `temp/t78/lgfixture-mbfalse`（只把 MB 段的
      `mandatory` 由 `true` 改成 `false`、区间仍是 `[0.1.3,)`）实测即得到二者并存的读数（可复现）：
        `AP_LG_VERDICT:bug_repro=1:gate_ok=1:loader_ok=1:mb_ok=1:fail=mb_mandatory_template=false:fml_stage=dependency_sorting:artifacts=skipped:msg=Missing or unsupported mandatory dependencies:range_only=1:overall=0`
      （区间判定通过 ⇒ `mb_ok=1`，但模板 `mandatory=false` ⇒ 整条门槛不成立、exit 1；
      复现命令：`pwsh -NoProfile -File scripts/verify/verify_forge_loader_gate.ps1 -Root temp/t78/lgfixture-mbfalse -SkipArtifacts`）
      为免误读，**S6 聚合后的判据行**追加了 `range_only=1`（只追加、不替换任何既有字段；`bug_repro` /
      `gate_ok` / `loader_ok` / `mb_ok` / `fail` / `fml_stage` / `artifacts` / `msg` / `overall`
      这些字段名与顺序逐字保持兼容，便于历史取证与文档引用）。前缀闸门的两处早退行
      （非 forge 树 / `forge_version` 缺失）**不**带 `range_only`，因为那时区间子判据根本没被评估。

    **`-SkipArtifacts`（未构建时的静态档）**：跳过分档 S1/S2 的**生成资源**与**构建产物**两段
    （模板段仍查）。此时判定器改用派生值，verdict 显式打 `artifacts=skipped` —— 判据降级必须可见，
    绝不允许伪装成全量通过。

    **只读**：除在工作目录落盘判定器源码/字节码（`temp/t78/loadergate/`，脚本所在仓库的 temp，
    与 `-Root` 指向的树无关）外不写任何文件；产品侧（两条发布线 + 26.1.2）一律只读。

    **夹具（`-Root` 负例/静态档用）**：`temp/t78/` 下（同一套最小树 = `forge-1.20.1/{gradle.properties,
    src/main/templates/META-INF/mods.toml（真模板逐字拷贝）, build.gradle（只含
    `maven.modrinth:mixinbooster` 装配行）}`）：
      · `lgfixture` —— 正对照，`-SkipArtifacts` ⇒ exit 0（`overall=1`、`artifacts=skipped`）；
      · `lgfixture-nomb` —— 整段删除 MB 依赖段 ⇒ exit 1（`LG_FAIL:mb_absent_in_template`）；
      · `lgfixture-mb` —— MB `versionRange` 改成 `"*"` ⇒ exit 1（`LG_FAIL:mb_range_template=*`）；
      · `lgfixture-loader-exact` —— 模板 `loaderVersion` 写成精确区间 `[47.4.10,48)`（2026-09-14
        实机踩过的 `fml.language.missingversion` 写法）⇒ exit 1（`LG_FAIL:tmpl_loader=…`）；
      · `lgfixture-mbfalse` —— MB 段 `mandatory` 改成 `false`（区间不动）⇒ exit 1
        （`LG_FAIL:mb_mandatory_template=false`，且 `mb_ok=1` —— 见上「区间子判据字段」一节的示例）；
      · `lgfixture-vdrift` —— `forge_version` 漂移到 `1.20.1-47.5.0`（真实偏差）+ 不构建（缺产物）
        ⇒ exit **1**（偏差优先于无法判定；`LG_FAIL:*` 与 `LG_UNDECIDABLE:*` 并存）。
    夹具位于被 `.gitignore` 忽略的 `temp/` 下 ⇒ 属本机开发资产（用户 2026-09-19 裁决：保持在本机
    `temp/t78/`，不入库）；若被清理，`-Root temp/t78/lgfixture` 会以 exit 2 + `not_a_forge_tree` 报出
    （**不会假绿**）。重建方式：从 `forge-1.20.1/` 拷贝 `gradle.properties` 与
    `src/main/templates/META-INF/mods.toml`，再写一份含
    `modImplementation "maven.modrinth:mixinbooster:rOaAYvZPZ"` 的最小 `build.gradle` 即可。

.EXAMPLE
    pwsh -NoProfile -File scripts/verify/verify_forge_loader_gate.ps1

.EXAMPLE
    # 只做静态档（未构建时）；-Root 支持夹具目录以便跑负例
    pwsh -NoProfile -File scripts/verify/verify_forge_loader_gate.ps1 -Root temp/t78/lgfixture -SkipArtifacts
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Root = $(if ($PSScriptRoot) { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) } else { '' }),

    [switch]$SkipArtifacts
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

# 退出码：本脚本自定，不引用任何 mt 常量。
$EXIT_OK = 0
$EXIT_DEVIATION = 1
$EXIT_UNDECIDABLE = 2

# 门槛断言的期望值 —— 即 AGENTS.md「加载器版本门槛 / Mixin Booster 硬前置」记录的实装口径。
# 刻意写成常量：`forge_version` 一旦变更，S1 立即以 derived_*/toml_* /forge_version_changed 报偏差，
# 迫使维护者同步这里与判定器的版本候选清单，而不是静默跟随。
$EXPECT_FORGE_VERSION = '1.20.1-47.4.10'
$EXPECT_OLD_RANGE = '[47,)'
$EXPECT_NEW_RANGE = '[47.4.10,48)'
$EXPECT_LOADER_RANGE = '[47,48)'
$EXPECT_MB_RANGE = '[0.1.3,)'

# ── 输出 / 记账（统一 LF，与 scripts/verify 既有脚本同契约）──────────────────
function Write-Out {
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)

    [Console]::Out.Write($Text)
    [Console]::Out.Write("`n")
}

$script:Readings = [System.Collections.Generic.List[string]]::new()
$script:Fail = [System.Collections.Generic.List[string]]::new()
$script:Undecidable = [System.Collections.Generic.List[string]]::new()

function Add-Reading {
    <#
    .SYNOPSIS
        打一行读数（`AP_LG_*`）并留存，供 S6 聚合。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Text)

    $script:Readings.Add($Text)
    Write-Out $Text
}

function Add-Fail {
    <#
    .SYNOPSIS
        记一条偏差并立刻逐条打印失败项 key（退出码 1 的依据）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Key)

    if (-not $script:Fail.Contains($Key)) { $script:Fail.Add($Key) }
    Write-Out ("LG_FAIL:{0}" -f $Key)
}

function Add-Undecidable {
    <#
    .SYNOPSIS
        记一条「无法判定」并打印缺什么 + 补救命令（退出码 2 的依据）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Key, [string]$What = '', [string]$Remedy = '')

    if (-not $script:Undecidable.Contains($Key)) { $script:Undecidable.Add($Key) }
    Write-Out ("LG_UNDECIDABLE:{0}:{1}" -f $Key, $What)
    if ($Remedy) { Write-Out ("LG_REMEDY:{0}:{1}" -f $Key, $Remedy) }
}

# ── 通用取物工具 ────────────────────────────────────────────────────────────
function Find-FirstFile {
    <#
    .SYNOPSIS
        取匹配路径中「最新且非 sources/javadoc」的那个文件（Gradle 缓存里同坐标会有多份）。
    #>
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
        读取 jar 内某个文本条目（用于取产物里的 META-INF/mods.toml / 前置的版本号文件）。
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

    .NOTES
        值是 Groovy 占位（含 `$`）时原样返回，由调用方按「placeholder」容忍 —— 模板段就该是这样。
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

function Get-DepFromToml {
    <#
    .SYNOPSIS
        从 mods.toml 文本里取某个 modId 依赖段的字段。

    .NOTES
        Forge 1.20.1 的 FML 只认 6 个键（实测 `ModInfo$ModVersion` 构造器常量池：
        modId / mandatory / versionRange / ordering / side / referralUrl）—— **没有 `reason`**。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Toml, [Parameter(Mandatory)][string]$ModId)

    $res = [ordered]@{ present = $false; mandatory = ''; range = ''; ordering = ''; side = '' }
    $blocks = [regex]::Split($Toml, '(?m)^\s*\[\[dependencies')
    foreach ($b in $blocks) {
        if ($b -notmatch ('(?m)modId\s*=\s*"' + [regex]::Escape($ModId) + '"')) { continue }
        $res.present = $true
        $m = [regex]::Match($b, '(?m)^\s*mandatory\s*=\s*(\S+)')
        if ($m.Success) { $res.mandatory = $m.Groups[1].Value.Trim() }
        $m = [regex]::Match($b, '(?m)^\s*versionRange\s*=\s*"([^"]+)"')
        if ($m.Success) { $res.range = $m.Groups[1].Value }
        $m = [regex]::Match($b, '(?m)^\s*ordering\s*=\s*"([^"]+)"')
        if ($m.Success) { $res.ordering = $m.Groups[1].Value }
        $m = [regex]::Match($b, '(?m)^\s*side\s*=\s*"([^"]+)"')
        if ($m.Success) { $res.side = $m.Groups[1].Value }
        break
    }
    return $res
}

function Get-JavaHome {
    <#
    .SYNOPSIS
        定位带 javac 的 JDK（JAVA_HOME → Zulu 21 → Adoptium → `~/.gradle/jdks/*`）。
    #>
    [CmdletBinding()]
    param()

    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) { return $env:JAVA_HOME }
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

# ── 入口 ════════════════════════════════════════════════════════════════════
# -Root：缺省由 $PSScriptRoot 派生仓库根（scripts/verify → 上两级），绝不缺省 '.'；
#        支持绝对路径与相对当前目录的路径，便于对夹具跑负例。
if ([string]::IsNullOrWhiteSpace($Root)) {
    Write-Out 'LG_UNDECIDABLE:root_unresolved:无法从 $PSScriptRoot 派生仓库根'
    Write-Out 'LG_REMEDY:root_unresolved:显式传 -Root <仓库根>'
    Write-Out 'AP_LG_VERDICT:overall=2:undecidable=root_unresolved'
    exit $EXIT_UNDECIDABLE
}
if ([System.IO.Path]::IsPathRooted($Root)) {
    $Root = [System.IO.Path]::GetFullPath($Root)
} else {
    $Root = [System.IO.Path]::GetFullPath((Join-Path (Get-Location).Path $Root))
}
$Root = $Root.TrimEnd('\', '/')
$Sub = Join-Path $Root 'forge-1.20.1'

# 判定器工作目录：**脚本所在仓库**的 temp/t78/loadergate（与 -Root 指向的树无关 ⇒ 夹具跑负例时
# 不往夹具里写文件；也不再用旧执行体的 temp/loadergate/，避免两个实现共用工作目录）。
$WorkRoot = if ($PSScriptRoot) { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) } else { $Root }
$WorkDir = Join-Path (Join-Path $WorkRoot 'temp') 't78\loadergate'

$CacheRoot = if ($env:GRADLE_USER_HOME) {
    Join-Path $env:GRADLE_USER_HOME 'caches\modules-2\files-2.1'
} else {
    Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
}

$artifactsState = if ($SkipArtifacts) { 'skipped' } else { 'present' }

Write-Out '== verify_forge_loader_gate（1.20.1 加载器版本门槛 + Mixin Booster 硬前置；独立于 mt 用例链）=='
Write-Out ("LG_ROOT:{0}:sub={1}:artifacts={2}:cache={3}" -f $Root, $Sub, $artifactsState, $CacheRoot)

# ── S1：区间来源链（gradle.properties → 派生 → 模板/生成资源/产物三段）─────────
Write-Out 'LG_STEP:1/6 取回区间来源(gradle.properties → build.gradle 派生 → 模板/生成资源/产物)'
$propsPath = Join-Path $Sub 'gradle.properties'
if (-not (Test-Path -LiteralPath $propsPath)) {
    # 前缀闸门①：给定的树根本不是 forge 子项目所在树 ⇒ 无法判定（不是「偏差」）。
    Add-Undecidable 'not_a_forge_tree' "-Root 下缺少 forge-1.20.1/gradle.properties（$propsPath）" `
        '把 -Root 指向仓库根（缺省即由 $PSScriptRoot 派生），或指向含 forge-1.20.1/ 子项目的夹具树'
    Write-Out ("AP_LG_VERDICT:bug_repro=0:gate_ok=0:loader_ok=0:mb_ok=0:fail=:fml_stage=dependency_sorting:artifacts={0}:msg=Missing or unsupported mandatory dependencies:undecidable=not_a_forge_tree:overall=2" -f $artifactsState)
    Write-Out 'LG_DONE'
    exit $EXIT_UNDECIDABLE
}

$forgeVersion = ''
$m = [regex]::Match([System.IO.File]::ReadAllText($propsPath), '(?m)^\s*forge_version\s*=\s*(.+?)\s*$')
if ($m.Success) { $forgeVersion = $m.Groups[1].Value.Trim() }
$semver = if ($forgeVersion) { ($forgeVersion -split '-')[-1] } else { '' }

if (-not $forgeVersion -or $semver -notmatch '^\d+(\.\d+)*$') {
    # 前缀闸门②：声明链起点坏了（键被删/写成非法值）⇒ 这是**偏差**，且此时的派生比较无意义
    # （会产出 `[,1)` 这类噪声判据）⇒ 早退并只报这一条根因。
    Add-Fail 'forge_version_missing'
    Write-Out 'LG_NOTE:声明链前缀闸门不成立（forge_version 缺失/非法）⇒ 早退，避免在不可判定输入上产出误导性判据'
    Write-Out ("AP_LG_VERDICT:bug_repro=0:gate_ok=0:loader_ok=0:mb_ok=0:fail={0}:fml_stage=dependency_sorting:artifacts={1}:msg=Missing or unsupported mandatory dependencies:overall=0" -f ($script:Fail -join '|'), $artifactsState)
    Write-Out 'LG_DONE'
    exit $EXIT_DEVIATION
}

$major = [int](($semver -split '\.')[0])
$derivedNew = "[$semver,$($major + 1))"
$derivedLoader = "[$major,$($major + 1))"
$derivedOld = "[$major,)"

if ($forgeVersion -ne $EXPECT_FORGE_VERSION) { Add-Fail "forge_version_changed=$forgeVersion" }
if ($derivedNew -ne $EXPECT_NEW_RANGE) { Add-Fail "derived_forge=$derivedNew" }
if ($derivedLoader -ne $EXPECT_LOADER_RANGE) { Add-Fail "derived_loader=$derivedLoader" }
if ($derivedOld -ne $EXPECT_OLD_RANGE) { Add-Fail "derived_old=$derivedOld" }

# 三段来源：模板（可为占位）/ 生成资源 / 构建产物 jar
$tmplPath = Join-Path $Sub 'src\main\templates\META-INF\mods.toml'
$tomlTemplate = ''
if (Test-Path -LiteralPath $tmplPath) { $tomlTemplate = [System.IO.File]::ReadAllText($tmplPath) }
else { Add-Fail 'template_mods_toml_absent' }

$tomlGen = ''
$rGen = $null
if ($SkipArtifacts) {
    Add-Reading 'AP_LG_SRC_GEN:skipped(artifacts=skipped)'
} else {
    $genPath = Join-Path $Sub 'build\generated\sources\modMetadata\META-INF\mods.toml'
    if (Test-Path -LiteralPath $genPath) {
        $tomlGen = [System.IO.File]::ReadAllText($genPath)
        $rGen = Get-RangeFromToml -Toml $tomlGen
        Add-Reading ("AP_LG_SRC_GEN:loader={0}:forge={1}" -f $rGen.loader, $rGen.forge)
        if (-not $rGen.loader) { Add-Fail 'gen_loaderVersion_absent' }
        if (-not $rGen.forge) { Add-Fail 'gen_forge_range_absent' }
    } else {
        Add-Reading 'AP_LG_SRC_GEN:absent'
        Add-Undecidable 'generated_mods_toml_absent' "缺少 $genPath（尚未数据生成）" `
            'pwsh -NoProfile -File scripts/test/mt_build.ps1 --version 1.20.1；或 gradlew :forge-1.20.1:generateModMetadata'
    }
}

$tomlJar = ''
$rJar = $null
if ($SkipArtifacts) {
    Add-Reading 'AP_LG_SRC_JAR:skipped(artifacts=skipped)'
} else {
    $jar = Find-FirstFile -Pattern (Join-Path $Sub 'build\libs\astral_dice-*+forge_*.jar')
    if ($null -ne $jar) {
        $toml = Get-JarEntryText -JarPath $jar -EntryName 'META-INF/mods.toml'
        if ($null -ne $toml) {
            $tomlJar = $toml
            $rJar = Get-RangeFromToml -Toml $toml
            Add-Reading ("AP_LG_SRC_JAR:jar={0}:loader={1}:forge={2}" -f (Split-Path -Leaf $jar), $rJar.loader, $rJar.forge)
            if (-not $rJar.loader) { Add-Fail 'jar_loaderVersion_absent' }
            if (-not $rJar.forge) { Add-Fail 'jar_forge_range_absent' }
        } else {
            Add-Reading 'AP_LG_SRC_JAR:absent(no mods.toml in jar)'
            Add-Undecidable 'jar_mods_toml_absent' "构建产物 $jar 内没有 META-INF/mods.toml" `
                'gradlew :forge-1.20.1:build（产物 jar 必须内嵌展开后的 mods.toml）'
        }
    } else {
        Add-Reading 'AP_LG_SRC_JAR:absent'
        Add-Undecidable 'built_jar_absent' "缺少 $Sub\build\libs\astral_dice-*+forge_*.jar（尚未构建）" `
            'gradlew :forge-1.20.1:build（或先跑 -SkipArtifacts 只做静态档）'
    }
}

# 三段一致性：模板段容忍 Groovy 占位；生成资源/产物必须与派生值逐字一致
$tmplRanges = if ($tomlTemplate) { Get-RangeFromToml -Toml $tomlTemplate } else { @{ loader = ''; forge = '' } }
$tmplLoaderState = if (-not $tmplRanges.loader) { 'absent' } elseif ($tmplRanges.loader -like '*$*') { 'placeholder' } else { $tmplRanges.loader }
$tmplForgeState = if (-not $tmplRanges.forge) { 'absent' } elseif ($tmplRanges.forge -like '*$*') { 'placeholder' } else { $tmplRanges.forge }
Add-Reading ("AP_LG_SRC_TMPL:loader={0}:forge={1}" -f $tmplLoaderState, $tmplForgeState)
if ($tmplRanges.loader -and $tmplRanges.loader -notlike '*$*' -and $tmplRanges.loader -ne $EXPECT_LOADER_RANGE) {
    Add-Fail "tmpl_loader=$($tmplRanges.loader)"
}
if ($tmplRanges.forge -and $tmplRanges.forge -notlike '*$*' -and $tmplRanges.forge -ne $EXPECT_NEW_RANGE) {
    Add-Fail "tmpl_forge=$($tmplRanges.forge)"
}
if ($null -ne $rGen) {
    if ($rGen.loader -ne $derivedLoader) { Add-Fail "toml_gen_loader=$($rGen.loader)" }
    if ($rGen.forge -ne $derivedNew) { Add-Fail "toml_gen_forge=$($rGen.forge)" }
}
if ($null -ne $rJar) {
    if ($rJar.loader -ne $derivedLoader) { Add-Fail "toml_jar_loader=$($rJar.loader)" }
    if ($rJar.forge -ne $derivedNew) { Add-Fail "toml_jar_forge=$($rJar.forge)" }
}
if (($null -ne $rGen) -and ($null -ne $rJar)) {
    if ($rGen.loader -ne $rJar.loader) { Add-Fail 'loader_mismatch_gen_vs_jar' }
    if ($rGen.forge -ne $rJar.forge) { Add-Fail 'forge_range_mismatch_gen_vs_jar' }
}

# 判定器要判的**实际声明值**：优先生成资源/产物（FML 真读的那份），否则回落到派生值
$declaredLoader = ''
$declaredForge = ''
$srcList = @()
if ($null -ne $rGen) { $declaredLoader = $rGen.loader; $declaredForge = $rGen.forge; $srcList += 'gen' }
if ($null -ne $rJar) {
    if (-not $declaredLoader) { $declaredLoader = $rJar.loader }
    if (-not $declaredForge) { $declaredForge = $rJar.forge }
    $srcList += 'jar'
}
if ($tomlTemplate) { $srcList += 'template' }
if (-not $declaredLoader) { $declaredLoader = $derivedLoader }
if (-not $declaredForge) { $declaredForge = $derivedNew }

Add-Reading ("AP_LG_SRC:forge_version={0}:derived_loader={1}:derived_forge={2}:derived_old={3}:loaderVersion={4}:forgeRange={5}:src={6}:artifacts={7}" -f `
        $forgeVersion, $derivedLoader, $derivedNew, $derivedOld, $declaredLoader, $declaredForge, ($srcList -join '+'), $artifactsState)

# ── S2：Mixin Booster 强依赖声明（三段一致 + 前置真实版本 + build.gradle 装配）───
Write-Out 'LG_STEP:2/6 核对 Mixin Booster 前置门槛声明(模板/生成资源/产物)'
$mbSrc = @()
$mbSeen = [ordered]@{}
foreach ($pair in @(
        @{ k = 'template'; t = $tomlTemplate; skipped = $false; artifact = $false },
        @{ k = 'gen'; t = $tomlGen; skipped = $SkipArtifacts; artifact = $true },
        @{ k = 'jar'; t = $tomlJar; skipped = $SkipArtifacts; artifact = $true })) {
    if ($pair.skipped) { continue }
    if (-not $pair.t) {
        # 模板缺 = 真偏差；生成资源/产物缺 = 已记 undecidable 的环境态（不再重复报失败，避免噪声）
        if ($pair.artifact) { Write-Out ("LG_NOTE:mb_src_absent_{0}（该段缺产物，已记 undecidable，不重复判失败）" -f $pair.k) }
        else { Add-Fail "mb_no_toml_$($pair.k)" }
        continue
    }
    $d = Get-DepFromToml -Toml $pair.t -ModId 'mixinbooster'
    if (-not $d.present) { Add-Fail "mb_absent_in_$($pair.k)"; continue }
    if ($d.mandatory -ne 'true') { Add-Fail "mb_mandatory_$($pair.k)=$($d.mandatory)" }
    if ($d.range -ne $EXPECT_MB_RANGE) { Add-Fail "mb_range_$($pair.k)=$($d.range)" }
    if ($d.ordering -ne 'AFTER') { Add-Fail "mb_ordering_$($pair.k)=$($d.ordering)" }
    if ($d.side -ne 'BOTH') { Add-Fail "mb_side_$($pair.k)=$($d.side)" }
    $mbSrc += $pair.k
    if (-not $mbSeen.Contains('range')) {
        $mbSeen.range = $d.range; $mbSeen.mandatory = $d.mandatory
        $mbSeen.ordering = $d.ordering; $mbSeen.side = $d.side
    }
}
if (-not $mbSeen.Contains('range')) {
    $mbSeen.range = ''; $mbSeen.mandatory = ''; $mbSeen.ordering = ''; $mbSeen.side = ''
}

# 前置本体：maven.modrinth:mixinbooster 的纯服务 jar，版本号读自 jar 根 mixinbooster_version.txt
# —— 这正是 FML 运行时看到的版本（实机 debug.log：`Found valid mod file transmog-mod.jar with
# {mixinbooster} mods - versions {0.1.3+1.20.1}`）。
$mbJar = Find-FirstFile -Pattern (Join-Path $CacheRoot 'maven.modrinth\mixinbooster\*\*\*.jar')
$mbArtifactVersion = ''
if ($null -ne $mbJar) {
    $vTxt = Get-JarEntryText -JarPath $mbJar -EntryName 'mixinbooster_version.txt'
    if ($null -ne $vTxt) { $mbArtifactVersion = $vTxt.Trim() }
    if (-not $mbArtifactVersion) {
        Add-Undecidable 'mixinbooster_version_txt_absent' "缓存 jar $mbJar 内没有 mixinbooster_version.txt" `
            'gradlew :forge-1.20.1:dependencies --refresh-dependencies（重取前置）'
    }
} else {
    Add-Undecidable 'mixinbooster_cache_absent' "Gradle 缓存里找不到 maven.modrinth:mixinbooster 的 jar（$CacheRoot）" `
        'gradlew :forge-1.20.1:dependencies（首次解析依赖会把前置拉进缓存）'
}

# 开发运行时侧：前置必须仍挂在 build.gradle 上（否则 dev 里 mixin 全静默失效）
$buildGradle = Join-Path $Sub 'build.gradle'
$mbBuildDep = 'absent'
if (Test-Path -LiteralPath $buildGradle) {
    if ([System.IO.File]::ReadAllText($buildGradle) -match 'maven\.modrinth:mixinbooster') { $mbBuildDep = 'present' }
} else {
    Add-Undecidable 'build_gradle_absent' "缺少 $buildGradle" '把 -Root 指向 forge 子项目所在的仓库根'
}
if ($mbBuildDep -ne 'present') { Add-Fail 'build_gradle_mixinbooster_missing' }

# Forge 1.20.1 不认 `reason` 键：模板里若出现未注释的 reason= 行即为无效声明（只会误导后来者）
$reasonKey = 'none'
if ($tomlTemplate -and ([regex]::Matches($tomlTemplate, '(?m)^\s*reason\s*=').Count -gt 0)) { $reasonKey = 'present' }
if ($reasonKey -ne 'none') { Add-Fail 'unsupported_reason_key_in_template' }

Add-Reading ("AP_LG_MB:src={0}:modId=mixinbooster:mandatory={1}:range={2}:ordering={3}:side={4}:present={5}:artifact_version={6}:build_dep={7}:reason_key={8}" -f `
        ($mbSrc -join '+'), $mbSeen.mandatory, $mbSeen.range, $mbSeen.ordering, $mbSeen.side,
        (($mbSrc.Count -gt 0) ? 1 : 0), $mbArtifactVersion, $mbBuildDep, $reasonKey)

# ── S3：FML 同一实现的核对（jar + POM + 字节码引用）─────────────────────────
Write-Out 'LG_STEP:3/6 核对 FML 依赖排序的同一实现(fmlloader POM → maven-artifact → ModSorter)'
$fmlJar = $null
$mavenJar = ''
$mavenVer = ''
$lang3Jar = ''
$lang3Ver = ''
$refClasses = @()
$fmlVer = $forgeVersion
$fmlJar = Find-FirstFile -Pattern (Join-Path $CacheRoot "net.minecraftforge\fmlloader\$fmlVer\*\fmlloader-$fmlVer.jar")
if ($null -ne $fmlJar) {
    # ⚠️ POM 与 jar 落在**不同的 hash 目录**下（Gradle 缓存布局），必须递归找。
    $fmlDir = Join-Path $CacheRoot "net.minecraftforge\fmlloader\$fmlVer"
    $pom = @(Get-ChildItem -Path $fmlDir -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like '*.pom' } | Select-Object -First 1)
    if ($pom.Count -gt 0) {
        $pomText = [System.IO.File]::ReadAllText($pom[0].FullName)
        $mm = [regex]::Match($pomText, '(?s)<artifactId>maven-artifact</artifactId>\s*<version>([^<]+)</version>')
        if ($mm.Success) { $mavenVer = $mm.Groups[1].Value }
        $ml = [regex]::Match($pomText, '(?s)<artifactId>commons-lang3</artifactId>\s*<version>([^<]+)</version>')
        if ($ml.Success) { $lang3Ver = $ml.Groups[1].Value }
    } else {
        Add-Undecidable 'fmlloader_pom_absent' "fmlloader 缓存目录里找不到 POM（$fmlDir）" `
            'gradlew :forge-1.20.1:dependencies --refresh-dependencies'
    }
    if ($mavenVer) {
        $mavenJar = Find-FirstFile -Pattern (Join-Path $CacheRoot "org.apache.maven\maven-artifact\$mavenVer\*\maven-artifact-$mavenVer.jar")
    } else {
        Add-Undecidable 'maven_artifact_version_unknown' 'fmlloader 的 POM 未声明 maven-artifact 版本' `
            'gradlew :forge-1.20.1:dependencies --refresh-dependencies'
    }
    # maven-artifact 的版本比较实现依赖 commons-lang3（DefaultArtifactVersion#tryParseInt →
    # org.apache.commons.lang3.math.NumberUtils），缺它会在运行时 NoClassDefFoundError。
    if ($lang3Ver) {
        $lang3Jar = Find-FirstFile -Pattern (Join-Path $CacheRoot "org.apache.commons\commons-lang3\$lang3Ver\*\commons-lang3-$lang3Ver.jar")
    } else {
        Add-Undecidable 'commons_lang3_version_unknown' 'fmlloader 的 POM 未声明 commons-lang3 版本' `
            'gradlew :forge-1.20.1:dependencies --refresh-dependencies'
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
} else {
    Add-Undecidable 'fmlloader_jar_absent' "Gradle 缓存里找不到 fmlloader-$fmlVer.jar（$CacheRoot）" `
        'gradlew :forge-1.20.1:dependencies（首次解析依赖会把 fmlloader 拉进缓存）'
}
if ($mavenVer -and -not $mavenJar) {
    Add-Undecidable 'maven_artifact_jar_absent' "缓存里找不到 maven-artifact-$mavenVer.jar" `
        'gradlew :forge-1.20.1:dependencies --refresh-dependencies'
}
if ($lang3Ver -and -not $lang3Jar) {
    Add-Undecidable 'commons_lang3_jar_absent' "缓存里找不到 commons-lang3-$lang3Ver.jar" `
        'gradlew :forge-1.20.1:dependencies --refresh-dependencies'
}
$sortedRefs = @($refClasses | Sort-Object -Unique)
Add-Reading ("AP_LG_IMPL:fmlloader={0}:maven_artifact={1}:commons_lang3={2}:jar={3}:version_range_refs={4}" -f `
        $(if ($fmlJar) { Split-Path -Leaf $fmlJar } else { '' }), $mavenVer, $lang3Ver, $(if ($mavenJar) { Split-Path -Leaf $mavenJar } else { '' }), ($sortedRefs -join ','))
if ($null -ne $fmlJar) {
    if ($sortedRefs -notcontains 'ModSorter') { Add-Fail 'ModSorter_no_VersionRange_ref' }
    if ($sortedRefs -notcontains 'VersionSupportMatrix') { Add-Fail 'VersionSupportMatrix_no_VersionRange_ref' }
} else {
    # jar 缺席已记 undecidable：此时"没扫到引用类"不构成偏差，禁止产出误导性失败项。
    Write-Out 'LG_NOTE:fml_static_scan_skipped（fmlloader jar 缺席，已记 undecidable）'
}

# ── S4/S5：离线判定器（同一 maven-artifact jar）─────────────────────────────
Write-Out 'LG_STEP:4/6 编译离线判定器(modVersionNotContained 同式)'
$javaHome = Get-JavaHome
if (-not $javaHome) {
    Add-Undecidable 'javac_not_found' '本机找不到带 javac 的 JDK（JAVA_HOME / Zulu 21 / Adoptium / ~/.gradle/jdks）' `
        '安装 JDK 17 或 21，或设置 JAVA_HOME 指向含 bin\javac.exe 的 JDK'
} elseif (-not $mavenJar -or -not $lang3Jar) {
    Add-Undecidable 'probe_classpath_incomplete' '判定器 classpath 不完整（缺 maven-artifact 或 commons-lang3）' `
        'gradlew :forge-1.20.1:dependencies --refresh-dependencies'
} else {
    if (-not (Test-Path -LiteralPath $WorkDir)) { [void](New-Item -ItemType Directory -Force -Path $WorkDir) }
    $javaSrc = @'
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * FML 依赖排序判定的逐字复刻（判据实现 = 同一个 maven-artifact jar）。
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
 *
 * 入参（由 verify_forge_loader_gate.ps1 从 gradle.properties 派生 + mods.toml 实际声明值传入，
 * 不写死）：args[0]=旧区间 args[1]=新区间 args[2]=loaderVersion 区间 args[3]=MB 区间 args[4]=MB 实装版本。
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

    /**
     * Mixin Booster 专用：它**不在** VersionSupportMatrix 的 overrideVersions 里
     * （那里只有 "mod.forge"），故判定退化为纯 range.containsVersion —— 不能用
     * fmlAcceptsForge（那个别名回退会让 [0.1.3,) 误判 "47.1.79" 为满足）。
     */
    private static String vb(VersionRange r, String ver) {
        return contains(r, ver) ? "accept" : "reject";
    }

    public static void main(String[] args) throws Exception {
        String oldSpec = args.length > 0 ? args[0] : "[47,)";
        String newSpec = args.length > 1 ? args[1] : "[47.4.10,48)";
        String loaderSpec = args.length > 2 ? args[2] : "[47,48)";
        VersionRange oldRange = VersionRange.createFromVersionSpec(oldSpec);
        VersionRange newRange = VersionRange.createFromVersionSpec(newSpec);
        VersionRange loaderRange = VersionRange.createFromVersionSpec(loaderSpec);

        // 旧区间（修复前）——低版本被接纳 = BUG 复现
        boolean bug = fmlAcceptsForge(oldRange, "47.0.0") && fmlAcceptsForge(oldRange, "47.4.9");
        System.out.println("AP_LG_OLD:range=" + oldSpec + ":v47.0.0=" + v(oldRange, "47.0.0")
                + ":v47.4.9=" + v(oldRange, "47.4.9")
                + ":v47.4.10=" + v(oldRange, "47.4.10")
                + ":bug_repro=" + (bug ? 1 : 0));

        // 新区间（修复后）——拒绝 47.0.0 / 47.4.9，接纳 47.4.10+
        boolean fixed = !fmlAcceptsForge(newRange, "47.0.0")
                && !fmlAcceptsForge(newRange, "47.4.9")
                && fmlAcceptsForge(newRange, "47.4.10")
                && fmlAcceptsForge(newRange, "47.4.11")
                && fmlAcceptsForge(newRange, "47.5.0")
                && !fmlAcceptsForge(newRange, "48.0.0");
        System.out.println("AP_LG_NEW:range=" + newSpec + ":v47.0.0=" + v(newRange, "47.0.0")
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

        // loaderVersion 是 javafml **语言提供者**的版本区间。LanguageLoadingProvider#findLanguage
        // 同样经 VersionSupportMatrix，但它的别名键是 value + "." + key = "languageloader.javafml"
        // —— 不在 overrideVersions（只有 "mod.forge"）里，故判定退化为纯 containsVersion。
        // 提供者版本 = Forge 主系列号 47（依据：2026-09-14 实机读数
        // `Missing language javafml version [47.4.10,48) … found 47`）。
        boolean loaderOk = contains(loaderRange, "47") && !contains(loaderRange, "48");
        System.out.println("AP_LG_LOADER:range=" + loaderSpec + ":javafml47=" + (contains(loaderRange, "47") ? "accept" : "reject")
                + ":javafml48=" + (contains(loaderRange, "48") ? "accept" : "reject")
                + ":alias_key=languageloader.javafml"
                + ":loader_ok=" + (loaderOk ? 1 : 0));

        // Mixin Booster 强依赖区间（Forge 1.20.1 的 FML 无 Mixin 集成，Sponge Mixin 由该前置 jar
        // 内嵌的 fabric-mixin 以 ModLauncher 服务提供；缺它/旧它都会让本模组的全部 mixin
        // **静默失效**——不报任何错，所以必须在依赖排序阶段硬拒）。
        if (args.length >= 5) {
            String spec = args[3];
            String actual = args[4];
            VersionRange mb = VersionRange.createFromVersionSpec(spec);
            boolean mbOk = contains(mb, actual)   // 实装前置版本必须落在区间内
                    && contains(mb, "0.1.3")      // 下限闭合：[0.1.3,) 含 0.1.3
                    && contains(mb, "0.1.4")
                    && contains(mb, "0.2.0")
                    && !contains(mb, "0.1.2")     // 旧前置必须被拒（不是"装了就算过"）
                    && !contains(mb, "0.1.0")
                    && !contains(mb, "0.0.9");
            System.out.println("AP_LG_MB_RANGE:range=" + spec + ":artifact=" + actual
                    + ":v0.1.0=" + vb(mb, "0.1.0")
                    + ":v0.1.2=" + vb(mb, "0.1.2")
                    + ":v0.1.3=" + vb(mb, "0.1.3")
                    + ":v" + actual + "=" + vb(mb, actual)
                    + ":v0.1.4=" + vb(mb, "0.1.4")
                    + ":v0.2.0=" + vb(mb, "0.2.0")
                    + ":gate_ok=" + (mbOk ? 1 : 0));
        }
    }
}
'@
    $srcFile = Join-Path $WorkDir 'LoaderGateProbe.java'
    [System.IO.File]::WriteAllText($srcFile, $javaSrc, [System.Text.UTF8Encoding]::new($false))
    Write-Out ("LG_PROBE:src={0}" -f $srcFile)

    $javac = Join-Path $javaHome 'bin\javac.exe'
    $java = Join-Path $javaHome 'bin\java.exe'
    $compile = & $javac -encoding UTF-8 -cp $mavenJar -d $WorkDir $srcFile 2>&1
    $compileText = ($compile | Out-String).Trim()
    Add-Reading ("AP_LG_COMPILE:javac={0}:rc={1}:out={2}" -f $javac, $LASTEXITCODE, ($compileText -replace '[\r\n]+', ' '))
    if ($LASTEXITCODE -ne 0) {
        Add-Fail 'probe_compile_failed'
    } else {
        Write-Out 'LG_STEP:5/6 运行判定器(旧区间 BUG 复现 / 新区间门槛 / loaderVersion / Mixin Booster 区间)'
        $cp = "$mavenJar;$lang3Jar;$WorkDir"
        if ($mbSeen.range -and $mbArtifactVersion) {
            $out = & $java -cp $cp LoaderGateProbe $derivedOld $declaredForge $declaredLoader $mbSeen.range $mbArtifactVersion 2>&1
            $probeRc = $LASTEXITCODE
        } else {
            $out = & $java -cp $cp LoaderGateProbe $derivedOld $declaredForge $declaredLoader 2>&1
            $probeRc = $LASTEXITCODE
            Add-Reading ("AP_LG_MB_RANGE:skipped=1:range={0}:artifact={1}:gate_ok=0" -f ([string]$mbSeen.range), $mbArtifactVersion)
        }
        foreach ($ln in @($out)) {
            $s = ([string]$ln).Trim()
            if ($s) { Add-Reading $s }
        }
        if ($probeRc -ne 0) { Add-Fail "probe_rc=$probeRc" }
    }
}

$oldLine = @($script:Readings | Where-Object { $_ -like 'AP_LG_OLD:*' }) -join ''
$newLine = @($script:Readings | Where-Object { $_ -like 'AP_LG_NEW:*' }) -join ''
$loadLine = @($script:Readings | Where-Object { $_ -like 'AP_LG_LOADER:*' }) -join ''
$mbLine = @($script:Readings | Where-Object { $_ -like 'AP_LG_MB_RANGE:*' }) -join ''
$bugOk = $oldLine -match 'bug_repro=1'
$gateOk = $newLine -match 'gate_ok=1'
$loaderOk = $loadLine -match 'loader_ok=1'
$mbRangeOk = $mbLine -match 'gate_ok=1'

# ── S6：聚合判定 ────────────────────────────────────────────────────────────
Write-Out 'LG_STEP:6/6 汇总依赖排序阶段结论'
# 用户可见文案 = FML 在依赖排序阶段的原生拒绝文案（ModSorter 常量池实测字符串）。
$verdictHead = "AP_LG_VERDICT:bug_repro={0}:gate_ok={1}:loader_ok={2}:mb_ok={3}:fail={4}:fml_stage=dependency_sorting:artifacts={5}:msg=Missing or unsupported mandatory dependencies" -f `
    (($bugOk) ? 1 : 0), (($gateOk) ? 1 : 0), (($loaderOk) ? 1 : 0), (($mbRangeOk) ? 1 : 0), ($script:Fail -join '|'), $artifactsState

# ── 退出码优先级（T19-02 收正）：明确偏差优先 ────────────────────────────────
# `LG_FAIL` 非空 ⇒ exit 1（即使同时存在无法判定项）：`forge_version` 漂移 / MB 段被删或被改弱 /
# `loaderVersion` 写错这类是**门槛不成立**（要改仓库），不是「环境没准备好」。
# 仅当「无偏差 且 存在无法判定项」才是 exit 2。两类 key 并存时**都会**打印：上面的
# `LG_FAIL:*` / `LG_UNDECIDABLE:*` / `LG_REMEDY:*` 逐条输出，verdict 行也同时带
# `:fail=<keys>` 与 `:undecidable=<keys>`，读者无需猜。
$hasFail = ($script:Fail.Count -gt 0)
$hasUndecidable = ($script:Undecidable.Count -gt 0)
$undecidableField = ''
if ($hasUndecidable) { $undecidableField = ':undecidable=' + ($script:Undecidable -join '|') }
$ok = $bugOk -and $gateOk -and $loaderOk -and $mbRangeOk -and (-not $hasFail)

if ($hasFail) {
    Write-Out ("{0}:range_only=1{1}:overall=0" -f $verdictHead, $undecidableField)
    Write-Out 'LG_DONE'
    exit $EXIT_DEVIATION
}
if ($hasUndecidable) {
    Write-Out ("{0}:range_only=1{1}:overall=2" -f $verdictHead, $undecidableField)
    Write-Out 'LG_DONE'
    exit $EXIT_UNDECIDABLE
}
Write-Out ("{0}:range_only=1:overall={1}" -f $verdictHead, (($ok) ? 1 : 0))
Write-Out 'LG_DONE'
if ($ok) { exit $EXIT_OK }
# 无 `LG_FAIL` 项但区间子判据自身不成立（如 probe 输出缺 gate_ok）⇒ 仍按「门槛不成立」记 1。
exit $EXIT_DEVIATION
