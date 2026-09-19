<#
.SYNOPSIS
  dev 工作树（multi-dev-next）自测启用脚本：构建 → 成对部署（本模组 jar + starengine_lib 库 jar）→ 可选启动 dev 客户端。

.DESCRIPTION
  一条命令完成「用户自行测试」所需的三件事：
    1) 构建所选平台（默认 1.21.1）；
    2) 把本次构建的本模组 jar 与配套的 starengine_lib 库 jar **成对**部署到目标 mods 目录：
       部署前先删除目标目录内旧的 astral_dice-*.jar 与 starengine_lib-*.jar（先删后拷），
       使目标目录内两者各恰一份且版本一致。**缺库 jar 或版本不匹配时报错停下**并给出解决指引
       —— 只放本模组 jar 会得到 FML「Missing or unsupported mandatory dependencies /
       Currently, starengine_lib is not installed」而拒绝启动。
    3) 可选启动 dev 客户端（-Launch，缺省关闭）。

  ⚠️ 仓库根用 $PSScriptRoot 解析，**不依赖当前工作目录**（本仓有「相对路径落到另一个工作树」的
     实际事故；所有写操作都用绝对路径）。

  ⚠️ **forge 1.20.1 的 jar 形态（2026-09-18 实测查证）**：
     · 库 `mavenLocal` 的 `starengine_lib-forge-1.20.1` 是 **reobf（生产 SRG）** 产物；
       dev `run/1.20.1` 需要 **dev/Mojmap** 形态 —— 直接拷 SRG jar 进 dev run 会以
       `NoSuchFieldError(f_…)` 之类崩（与 `pushToDevRun` 必须推 dev jar 同理）。
     · 故本脚本对 **dev 目标**在 forge 线改用**库仓库的 dev jar**
       `<libRepo>\forge-1.20.1\build\devlibs\starengine_lib-forge-1.20.1-<ver>.jar`
       （需该文件存在；不存在即报错并给出重建指引），本模组 jar 也**只**取 `<line>\build\devlibs`
       —— 缺失即 Fail，**不**回退 `build\libs` 的 reobf 产物（那正是上面会崩的组合）。
     · 对**外部/生产目标**（`-TargetDir` 指向整合包等）则用生产形态：库取 mavenLocal 的 reobf jar，
       本模组取 `<line>\build\libs`。
     · 形态闸门对**本模组 jar 与库 jar 对称**：dev 目标两者都必须是 dev/Mojmap；prod 目标 + forge 线
       两者都必须是 reobf/SRG（断言失败即 Fail，并指出正确来源目录）。
     · dev/prod 判定（缺省 `-TargetKind auto`）：①显式 `-TargetKind dev|prod` 优先；②目标目录在
       `<repo>\run\` 下 ⇒ dev；③目标目录**最末段以 `dev` 结尾**（如 `…\target-dev`、`…\mods-dev`）⇒ dev；
       ④否则 prod（外部实例）。判定依据每次运行都会打印。
     · 形态判据：对 jar 内全部 .class 的字节做 `\b[fm]_\d+_` 正则计数 —— SRG 形态有命中
       （库 reobf 29 处、本模组 reobf 1608 处），dev/Mojmap 形态 0 处。

  ⚠️ 关于「dev run 的库来源」：Gradle 侧 `implementation`/`modImplementation` 已把库放进 run 的
     `runtimeClasspath`（2026-09-18 本机 `dependencyInsight --configuration runtimeClasspath` 实测命中
     当时声明的 `…:<starengine_lib_version>`），FML 会从 classpath 发现它；本脚本再往 `run/<版本>/mods` 放一份
     **版本一致**的同名 mod，FML 的 `UniqueModListBuilder` 会按 modId 去重（mods 目录优先），
     因此不会重复加载；反之**版本不一致**就会出现两份不同版本、行为不可预期 —— 这正是脚本
     先删后拷、并强制校验版本一致的原因。

.PARAMETER Version
  平台：1.21.1（缺省） / 1.20.1 / 26.1.2。

.PARAMETER TargetDir
  目标 mods 目录（绝对路径）。缺省 `<repo>\run\<Version>\mods`。
  指向 `<repo>\run\` 以外（如某个整合包实例）时按 `-TargetKind` 的判定改用**生产形态** jar（见上）。

.PARAMETER TargetKind
  目标形态：`auto`（缺省，按目标目录推断）/ `dev` / `prod`。仅影响 jar **形态选择**，不改变
  `-TargetDir` 的语义；显式传入时优先于目录推断（用于仓库外的 dev 形态测试目录）。

.PARAMETER SkipBuild
  跳过构建，直接用现有产物部署（产物不存在即报错）。

.PARAMETER Launch
  **人工测试路径 —— 代理不得自动调用**。构建+部署完成后后台启动 dev 客户端，
  日志写 `temp\self-test\<版本>-client-<时间戳>.log`，并打印 PID 与停止方式。缺省**不**启动。

.PARAMETER DryRun
  只打印计划，零落盘（不构建、不拷贝、不移动任何文件）。

.PARAMETER CleanOldJars
  额外执行「旧版 jar 清理」：把构建产物 / run 目录里**不属于当前各线 `mod_version`** 的本模组 jar，
  以及 mavenLocal 里**每个 artifact 下除该线当前 `starengine_lib_version` 之外的全部版本目录**
  （不枚举具体版本号，避免新旧版本漏配）**移动**（不硬删）到 `<repo>\temp\old-jars-<时间戳>\`，
  并生成清单（原路径 / 大小 / sha256）。保留清单与清理范围都由各线 `gradle.properties` 派生，
  `mod_version` / `starengine_lib_version` 变更后无需改脚本。缺省关闭。

.PARAMETER BuildTimeoutSec
  Gradle 看门狗超时（秒，缺省 180）。超时即强制结束进程树，随后按仓库规则做
  「日志 BUILD SUCCESSFUL/FAILED + 产物时间戳」双验证；构建失败**不得**继续部署。

.EXAMPLE
  pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader-next\scripts\devtools\Start-SelfTest.ps1 -DryRun
.EXAMPLE
  pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader-next\scripts\devtools\Start-SelfTest.ps1 -Version 1.21.1
.EXAMPLE
  # 指向外部实例（生产形态 jar），并清理旧版 jar
  pwsh -NoProfile -File ...\Start-SelfTest.ps1 -Version 1.20.1 -TargetDir 'D:\.minecraft\versions\1.20.1 模组测试\mods' -CleanOldJars
.EXAMPLE
  # 仓库外的 dev 形态测试目录：目录名以 dev 结尾会被判为 dev（也可用 -TargetKind dev 显式指定）
  pwsh -NoProfile -File ...\Start-SelfTest.ps1 -Version 1.20.1 -TargetDir 'D:\dev-runs\mods-dev' -SkipBuild
#>
[CmdletBinding()]
param(
    [ValidateSet('1.21.1', '1.20.1', '26.1.2')]
    [string]$Version = '1.21.1',

    [string]$TargetDir,

    # 目标形态（jar 形态选择）：auto = 按目标目录推断（<repo>\run\ 下或目录名以 dev 结尾 ⇒ dev）
    [ValidateSet('auto', 'dev', 'prod')]
    [string]$TargetKind = 'auto',

    [switch]$SkipBuild,

    [switch]$Launch,

    [switch]$DryRun,

    [switch]$CleanOldJars,

    [int]$BuildTimeoutSec = 180,

    # 库仓库磁盘路径（缺省 = dev 工作树的同级 starengine_lib；仅 forge dev 形态需要它）
    [string]$LibRepoPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# 0. 路径与常量（全部由 $PSScriptRoot 推导，绝不依赖 cwd）
# ---------------------------------------------------------------------------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $ScriptDir '..\..')).Path
$RepoParent = Split-Path -Parent $RepoRoot
if (-not $LibRepoPath) { $LibRepoPath = Join-Path $RepoParent 'starengine_lib' }

$LibGroupPath = 'com\merlinkitsune\starenginelib'
$M2RepoRoot = Join-Path $env:USERPROFILE ".m2\repository\$LibGroupPath"

# 当前版本必须保留的本模组 jar 名 / 库版本：**一律由各线 gradle.properties 派生**（禁止硬编码版本字面量）
# 见下方 Get-CurrentModJarNames / Get-PinnedLibVersions（在 helper 定义之后调用）

$LineMap = @{
    '1.21.1'   = @{ Sub = 'neoforge-1.21.1'; Kind = 'neoforge'; Artifact = 'starengine_lib-neoforge-1.21.1'; GradlePath = ':neoforge-1.21.1' }
    '1.20.1'   = @{ Sub = 'forge-1.20.1';    Kind = 'forge';    Artifact = 'starengine_lib-forge-1.20.1';    GradlePath = ':forge-1.20.1' }
    '26.1.2'   = @{ Sub = 'neoforge-26.1.2'; Kind = 'neoforge'; Artifact = 'starengine_lib-neoforge-26.1.2'; GradlePath = ':neoforge-26.1.2' }
}

function Write-Step([string]$msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Info([string]$msg) { Write-Host "    $msg" }
function Write-Warn2([string]$msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Ok([string]$msg) { Write-Host "[ OK ] $msg" -ForegroundColor Green }
function Fail([string]$msg, [string[]]$hints) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    foreach ($h in $hints) { Write-Host "       · $h" -ForegroundColor Red }
    throw "Start-SelfTest: $msg"
}

function Read-GradleProperties([string]$path) {
    $map = @{}
    foreach ($line in (Get-Content -LiteralPath $path)) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $i = $t.IndexOf('=')
        if ($i -lt 1) { continue }
        $map[$t.Substring(0, $i).Trim()] = $t.Substring($i + 1).Trim()
    }
    return $map
}

function Get-Sha256([string]$path) { return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLower() }

function Get-JarForm([string]$jarPath) {
    # 形态判据：jar 内 .class 字节里 SRG 形（f_/m_ + 数字 + 下划线）命中数
    Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        $hits = 0
        foreach ($e in $zip.Entries) {
            if ($e.FullName -notlike '*.class') { continue }
            $ms = New-Object System.IO.MemoryStream
            $s = $e.Open(); $s.CopyTo($ms); $s.Close()
            $txt = [System.Text.Encoding]::ASCII.GetString($ms.ToArray())
            $hits += ([regex]::Matches($txt, '\b[fm]_\d+_')).Count
        }
        if ($hits -gt 0) { return @{ Form = 'reobf/srg'; Hits = $hits } }
        return @{ Form = 'dev/mojmap'; Hits = 0 }
    }
    finally { $zip.Dispose() }
}

# ---------------------------------------------------------------------------
# 0.1 派生值（保留清单 / 库版本钉死值）—— 全部读各线 gradle.properties，禁止硬编码
# ---------------------------------------------------------------------------
function Get-CurrentModJarNames {
    # 保留清单 = 三条线各自 mod_version 推出的本模组 jar 名（mod_version 自带加载器后缀）
    $names = New-Object System.Collections.Generic.List[string]
    foreach ($v in @('1.21.1', '1.20.1', '26.1.2')) {
        $p = Join-Path $RepoRoot "$($LineMap[$v].Sub)\gradle.properties"
        if (-not (Test-Path -LiteralPath $p)) { continue }
        $mv = [string](Read-GradleProperties $p)['mod_version']
        if ($mv) { $names.Add("astral_dice-$mv.jar") }
    }
    return $names
}

function Get-PinnedLibVersions {
    # 每个 artifact 的当前库版本 = 对应线 gradle.properties 的 starengine_lib_version
    $map = @{}
    foreach ($v in @('1.21.1', '1.20.1', '26.1.2')) {
        $l = $LineMap[$v]
        $p = Join-Path $RepoRoot "$($l.Sub)\gradle.properties"
        if (-not (Test-Path -LiteralPath $p)) { continue }
        $map[$l.Artifact] = [string](Read-GradleProperties $p)['starengine_lib_version']
    }
    return $map
}

$KEEP_MOD_JARS = @(Get-CurrentModJarNames)
$PINNED_LIB_VERSIONS = Get-PinnedLibVersions
if ($KEEP_MOD_JARS.Count -eq 0) { Fail '保留清单为空：三线 gradle.properties 都读不到 mod_version' @("仓库根 = $RepoRoot") }

function Get-BuildArtifactDirs {
    return @(
        (Join-Path $RepoRoot 'neoforge-1.21.1\build\libs'),
        (Join-Path $RepoRoot 'forge-1.20.1\build\libs'),
        (Join-Path $RepoRoot 'forge-1.20.1\build\devlibs'),
        (Join-Path $RepoRoot 'neoforge-26.1.2\build\libs'),
        (Join-Path $RepoRoot 'build\libs')
    )
}

function Get-CleanupPlan([string]$quarantine) {
    # 纯只读扫描（零落盘）：DryRun 预演与实际执行共用同一份判定，保证「计划 = 行为」
    $keep = New-Object System.Collections.Generic.List[string]
    $move = New-Object System.Collections.Generic.List[object]
    $m2Remain = New-Object System.Collections.Generic.List[string]
    $notes = New-Object System.Collections.Generic.List[string]

    # (a) 构建产物目录
    foreach ($d in (Get-BuildArtifactDirs)) {
        if (-not (Test-Path -LiteralPath $d)) { continue }
        foreach ($f in (Get-ChildItem -LiteralPath $d -File -Filter 'astral_dice-*.jar')) {
            if ($KEEP_MOD_JARS -contains $f.Name) { $keep.Add($f.FullName); continue }
            $tag = 'build-' + $f.Directory.Parent.Parent.Name + '-' + $f.Directory.Name + '-' + $f.Name
            $move.Add([pscustomobject]@{ 原路径 = $f.FullName; 去向 = (Join-Path $quarantine $tag) })
        }
    }

    # (b) run\<版本>\mods：旧本模组 jar + 与当前库版本不符的库 jar
    foreach ($v in @('1.21.1', '1.20.1', '26.1.2')) {
        $d = Join-Path $RepoRoot "run\$v\mods"
        if (-not (Test-Path -LiteralPath $d)) { continue }
        $pinnedLib = [string]$PINNED_LIB_VERSIONS[$LineMap[$v].Artifact]
        if (-not $pinnedLib) { $notes.Add("run/$v/mods：$($LineMap[$v].Artifact) 无 starengine_lib_version ⇒ 库 jar 一律保留（不动）") }
        foreach ($f in (Get-ChildItem -LiteralPath $d -File -Filter '*.jar')) {
            if ($f.Name -like 'astral_dice-*.jar') {
                if ($KEEP_MOD_JARS -contains $f.Name) { $keep.Add($f.FullName); continue }
                $move.Add([pscustomobject]@{ 原路径 = $f.FullName; 去向 = (Join-Path $quarantine ("run-$v-mods-" + $f.Name)) })
                continue
            }
            if ($f.Name -like 'starengine_lib-*.jar') {
                if (-not $pinnedLib) { continue }
                if ($f.Name -eq "$($LineMap[$v].Artifact)-$pinnedLib.jar") { $keep.Add($f.FullName); continue }
                $move.Add([pscustomobject]@{ 原路径 = $f.FullName; 去向 = (Join-Path $quarantine ("run-$v-mods-" + $f.Name)) })
            }
        }
    }

    # (c) mavenLocal：每个 artifact 下除「该线当前 starengine_lib_version」外的**全部**版本目录一律隔离
    foreach ($artifactDir in (Get-ChildItem -LiteralPath $M2RepoRoot -Directory -ErrorAction SilentlyContinue)) {
        $pinned = [string]$PINNED_LIB_VERSIONS[$artifactDir.Name]
        $remain = New-Object System.Collections.Generic.List[string]
        if (-not $pinned) {
            $notes.Add("m2/$($artifactDir.Name)：本仓三线均未声明它 ⇒ 整目录保留（不动）")
            foreach ($vd0 in (Get-ChildItem -LiteralPath $artifactDir.FullName -Directory)) { $remain.Add($vd0.Name) }
        } else {
            if (-not (Test-Path -LiteralPath (Join-Path $artifactDir.FullName $pinned))) {
                $notes.Add("m2/$($artifactDir.Name)：缺少当前版本目录 $pinned（该线 run 需要它，留意是否漏发布）")
            }
            foreach ($vd in (Get-ChildItem -LiteralPath $artifactDir.FullName -Directory)) {
                if ($vd.Name -eq $pinned) { $remain.Add($vd.Name); continue }
                foreach ($f in (Get-ChildItem -LiteralPath $vd.FullName -File -Recurse)) {
                    $rel = $f.FullName.Substring($vd.FullName.Length).TrimStart('\')
                    $move.Add([pscustomobject]@{ 原路径 = $f.FullName; 去向 = (Join-Path $quarantine ("m2-" + $artifactDir.Name + "-" + $vd.Name + "-" + $rel.Replace('\', '_'))) })
                }
            }
        }
        $m2Remain.Add("$($artifactDir.Name): $($remain -join ', ')")
    }

    return [pscustomobject]@{ Keep = $keep; Move = $move; M2Remain = $m2Remain; Notes = $notes }
}

function Show-CleanupPlan($plan, [string]$quarantineLabel) {
    Write-Info "保留清单（当前各线 mod_version 派生，共 $($plan.Keep.Count) 个文件）："
    foreach ($k in $plan.Keep) { Write-Info "  · [保留] $k" }
    Write-Info "待隔离清单（移到 $quarantineLabel，共 $($plan.Move.Count) 个文件）："
    foreach ($m in $plan.Move) { Write-Info "  · [隔离] $($m.原路径)" }
    Write-Info 'mavenLocal 清理后剩余版本（每个 artifact）：'
    foreach ($r in $plan.M2Remain) { Write-Info "  · $r" }
    foreach ($n in $plan.Notes) { Write-Warn2 $n }
}

# ---------------------------------------------------------------------------
# 1. 解析本次运行的参数与目标
# ---------------------------------------------------------------------------
$line = $LineMap[$Version]
$sub = $line.Sub
$propsPath = Join-Path $RepoRoot "$sub\gradle.properties"
if (-not (Test-Path -LiteralPath $propsPath)) { Fail "找不到 $propsPath（仓库根解析异常？）" @("仓库根 = $RepoRoot") }
$props = Read-GradleProperties $propsPath
$libVersion = $props['starengine_lib_version']
$libRange = $props['starengine_lib_version_range']
$modVersion = $props['mod_version']
if (-not $libVersion) { Fail "$sub\gradle.properties 里没有 starengine_lib_version" @('库版本必须由该键驱动，不允许脚本硬编码') }

if (-not $TargetDir) { $TargetDir = Join-Path $RepoRoot "run\$Version\mods" }
$TargetDir = [System.IO.Path]::GetFullPath($TargetDir)

# 目标形态（dev/prod）：-TargetKind 显式优先；否则 <repo>\run\ 下 或 目录名以 dev 结尾 ⇒ dev；其余 = 外部/生产
$runPrefix = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot 'run')) + [System.IO.Path]::DirectorySeparatorChar
$targetLeaf = Split-Path -Leaf ($TargetDir.TrimEnd('\', '/'))
if ($TargetKind -ne 'auto') {
    $isDevTarget = ($TargetKind -eq 'dev')
    $kindReason = "显式 -TargetKind $TargetKind"
} elseif ($TargetDir.StartsWith($runPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    $isDevTarget = $true
    $kindReason = '目标在 <repo>\run\ 下'
} elseif ($targetLeaf -match '(?i)dev$') {
    $isDevTarget = $true
    $kindReason = "目标目录最末段以 dev 结尾（$targetLeaf）"
} else {
    $isDevTarget = $false
    $kindReason = '目标在 <repo>\run\ 之外且目录名不以 dev 结尾（外部实例 ⇒ 生产形态）'
}
$form = if ($isDevTarget) { 'dev' } else { 'prod' }

# 本模组 jar：dev 目标 + forge 线**只**允许 build\devlibs（dev/Mojmap；缺失即 Fail，不回退 build\libs）；其余 -> build\libs
$modJarName = "astral_dice-$modVersion.jar"
$modJarCandidates = @()
if ($form -eq 'dev' -and $line.Kind -eq 'forge') {
    $modJarCandidates += (Join-Path $RepoRoot "$sub\build\devlibs\$modJarName")
} else {
    $modJarCandidates += (Join-Path $RepoRoot "$sub\build\libs\$modJarName")
}
$modJar = $modJarCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1

# 库 jar：dev 目标 + forge 线 -> 库仓 build\devlibs；其余 -> mavenLocal
$libJarDev = Join-Path $LibRepoPath "forge-1.20.1\build\devlibs\$($line.Artifact)-$libVersion.jar"
$libJarM2 = Join-Path $M2RepoRoot "$($line.Artifact)\$libVersion\$($line.Artifact)-$libVersion.jar"
$libJar = if ($form -eq 'dev' -and $line.Kind -eq 'forge') { $libJarDev } else { $libJarM2 }
$libJarForm = if ($form -eq 'dev' -and $line.Kind -eq 'forge') { 'dev（库仓 devlibs）' } else { '生产/常规（mavenLocal）' }

# ---------------------------------------------------------------------------
# 2. 打印计划
# ---------------------------------------------------------------------------
Write-Host ''
Write-Host "=========== Start-SelfTest（dev 工作树自测启用） ===========" -ForegroundColor Magenta
Write-Info "仓库根（由 `$PSScriptRoot 推导） : $RepoRoot"
Write-Info "平台 / 子项目                   : $Version / $sub"
Write-Info "目标 mods 目录                  : $TargetDir"
Write-Info "目标形态                        : $form（dev ⇒ dev/Mojmap 形态 jar；prod ⇒ 生产 reobf 形态 jar）｜判定依据：$kindReason（可用 -TargetKind dev|prod 覆盖）"
Write-Info "本模组 mod_version              : $modVersion"
Write-Info "库坐标                          : $($props['starengine_lib_group']):$($line.Artifact):$libVersion"
Write-Info "库前置区间                      : $libRange"
Write-Info "库 jar 形态来源                 : $libJarForm"
Write-Info "本模组 jar                      : $(if ($modJar) { $modJar } else { "<未找到: $($modJarCandidates -join ' | ')>" })"
Write-Info "配套库 jar                      : $(if (Test-Path -LiteralPath $libJar) { $libJar } else { "<缺失: $libJar>" })"
Write-Info "构建                            : $(if ($SkipBuild) { '跳过（-SkipBuild）' } else { "会执行 $($line.GradlePath):build（后台 + ${BuildTimeoutSec}s 看门狗）" })"
Write-Info "启动客户端                      : $(if ($Launch) { '【人工测试路径】会后台启动 runClient' } else { '否（缺省不启动；-Launch 为人工路径，代理不得自动调用）' })"
$cleanupDesc = if ($CleanOldJars) { "会移动到 $RepoRoot" + '\temp\old-jars-<时间戳>\' } else { '否（-CleanOldJars 才执行）' }
Write-Info "旧版 jar 清理                   : $cleanupDesc"
if ($DryRun) { Write-Host "    *** -DryRun：只打印计划，零落盘 ***" -ForegroundColor Yellow }
Write-Host "==========================================================" -ForegroundColor Magenta
Write-Host ''

if ($DryRun) {
    Write-Step 'DryRun 计划明细'
    Write-Info "1) $(if ($SkipBuild) { '(跳过构建)' } else { "后台构建：$RepoRoot\gradlew.bat $($line.GradlePath):build" })"
    Write-Info "2) 成对部署到：$TargetDir"
    Write-Info "   · 先删除：astral_dice-*.jar、starengine_lib-*.jar"
    Write-Info "   · 再拷贝：$(Split-Path -Leaf $modJarCandidates[0])"
    Write-Info "   · 再拷贝：$(Split-Path -Leaf $libJar)"
    Write-Info "3) $(if ($CleanOldJars) { '清理旧版 jar -> temp\old-jars-<时间戳>\' } else { '(不清理旧版 jar)' })"
    Write-Info "4) $(if ($Launch) { '后台启动 dev 客户端（人工路径）' } else { '(不启动客户端)' })"
    $dryQuarantine = Join-Path $RepoRoot 'temp\old-jars-<时间戳>'
    Write-Step "DryRun 旧版 jar 清理计划（纯只读扫描；$(if ($CleanOldJars) { '本次带 -CleanOldJars，实际执行时按此计划移动' } else { '本次未带 -CleanOldJars，仅预演、不移动' })）"
    Show-CleanupPlan (Get-CleanupPlan $dryQuarantine) $dryQuarantine
    Write-Host ''
    Write-Ok 'DryRun 结束：未构建、未拷贝、未移动任何文件。'
    exit 0
}

# ---------------------------------------------------------------------------
# 3. 旧版 jar 清理（可选，移动到隔离目录 + 清单）
# ---------------------------------------------------------------------------
function Invoke-OldJarCleanup {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $quarantine = Join-Path $RepoRoot "temp\old-jars-$stamp"
    New-Item -ItemType Directory -Force -Path $quarantine | Out-Null
    $moved = New-Object System.Collections.Generic.List[object]

    Write-Step "清理旧版 jar -> $quarantine"
    $plan = Get-CleanupPlan $quarantine
    Write-Info "保留清单：$($plan.Keep.Count) 个文件（由各线 mod_version / starengine_lib_version 派生，不动）"
    Write-Info "待隔离清单：$($plan.Move.Count) 个文件"

    # 预演与实际执行共用同一份判定（Get-CleanupPlan）⇒「-DryRun 计划 = 实际行为」
    foreach ($m in $plan.Move) {
        $src = Get-Item -LiteralPath $m.原路径
        $entry = [pscustomobject]@{ 原路径 = $m.原路径; 大小 = $src.Length; sha256 = (Get-Sha256 $m.原路径); 去向 = $m.去向 }
        Move-Item -LiteralPath $m.原路径 -Destination $m.去向 -Force
        $moved.Add($entry)
        Write-Info "移动 $($m.原路径)  ($($entry.大小) B)"
    }
    foreach ($n in $plan.Notes) { Write-Warn2 $n }

    # mavenLocal：被搬空的版本目录一并移除（每个 artifact 只保留该线当前的 starengine_lib_version）
    foreach ($artifactDir in (Get-ChildItem -LiteralPath $M2RepoRoot -Directory -ErrorAction SilentlyContinue)) {
        $pinned = [string]$PINNED_LIB_VERSIONS[$artifactDir.Name]
        if (-not $pinned) { continue }
        foreach ($vd in (Get-ChildItem -LiteralPath $artifactDir.FullName -Directory -ErrorAction SilentlyContinue)) {
            if ($vd.Name -eq $pinned) { continue }
            $left = @(Get-ChildItem -LiteralPath $vd.FullName -File -Recurse -Force)
            if ($left.Count -eq 0) {
                Remove-Item -LiteralPath $vd.FullName -Recurse -Force
                Write-Info "移除空版本目录：m2\$($artifactDir.Name)\$($vd.Name)"
            }
        }
    }

    $manifestCsv = Join-Path $quarantine 'manifest.csv'
    $moved | Export-Csv -LiteralPath $manifestCsv -NoTypeInformation -Encoding UTF8
    $lines = @("# 旧版 jar 隔离清单（Start-SelfTest -CleanOldJars）", "", "隔离目录：$quarantine", "生成时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')", "", "| # | 原路径 | 大小(B) | sha256 | 去向 |", "|---|---|---|---|---|")
    $i = 0
    foreach ($m in $moved) { $i++; $lines += "| $i | ``$($m.原路径)`` | $($m.大小) | ``$($m.sha256)`` | ``$($m.去向)`` |" }
    if ($moved.Count -eq 0) { $lines += "| - | （无旧版 jar，无需移动） | - | - | - |" }
    $manifestMd = Join-Path $quarantine 'manifest.md'
    $lines | Set-Content -LiteralPath $manifestMd -Encoding UTF8
    Write-Ok "已移动 $($moved.Count) 个文件到隔离目录（不硬删）"
    Write-Info "CSV  : $manifestCsv"
    Write-Info "MD   : $manifestMd"
    $afterPlan = Get-CleanupPlan $quarantine
    Write-Info '清理后 mavenLocal 各 artifact 剩余版本（每个 artifact 只应剩当前 starengine_lib_version）：'
    foreach ($r in $afterPlan.M2Remain) { Write-Info "  · $r" }
    return $quarantine
}

if ($CleanOldJars) { $null = Invoke-OldJarCleanup }

# ---------------------------------------------------------------------------
# 4. 构建（看门狗：后台 + 超时 + 日志/产物双验证）
# ---------------------------------------------------------------------------
$buildLog = Join-Path $RepoRoot ("temp\self-test\build-$Version-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $buildLog) | Out-Null

if (-not $SkipBuild) {
    Write-Step "构建 $($line.GradlePath):build（日志：$buildLog）"
    $gradlew = Join-Path $RepoRoot 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradlew)) { Fail "找不到 $gradlew" @('仓库根解析异常') }
    $startedAt = Get-Date
    $proc = Start-Process -FilePath $gradlew -ArgumentList @("$($line.GradlePath):build", '--console=plain') `
        -WorkingDirectory $RepoRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $buildLog -RedirectStandardError "$buildLog.err"
    $exited = $proc.WaitForExit($BuildTimeoutSec * 1000)
    if (-not $exited) {
        Write-Warn2 "超过 ${BuildTimeoutSec}s 未退出（仓库已知的 Gradle 守护进程不退出问题）⇒ 强制结束进程树，随后按规则做日志+产物双验证"
        & taskkill /PID $proc.Id /T /F 2>&1 | Out-Null
    }
    # 双验证（1）：日志
    $logText = (Get-Content -LiteralPath $buildLog -Raw -ErrorAction SilentlyContinue)
    if ($logText -match 'BUILD FAILED' -or $logText -match 'error:') {
        Fail "构建失败（日志见 $buildLog）" @("先修构建错误再重试；本次**不会**部署任何 jar")
    }
    if ($logText -notmatch 'BUILD SUCCESSFUL') {
        Fail "构建未出现 BUILD SUCCESSFUL（超时或异常；日志见 $buildLog）" @("看 $buildLog 与 $buildLog.err", '确认 Gradle 守护进程状态：gradlew --stop')
    }
    Write-Ok '日志验证：BUILD SUCCESSFUL'
} else {
    Write-Step '跳过构建（-SkipBuild）'
}
Write-Info "$buildLog"

# ---------------------------------------------------------------------------
# 5. 成对部署（核心）
# ---------------------------------------------------------------------------
Write-Step '成对部署（本模组 jar + 库 jar）'

# 双验证（2）：产物时间戳（-SkipBuild 时只校验存在性）
$modJar = $modJarCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $modJar) {
    Fail "找不到本次构建的本模组 jar：$modJarName" @(
        "本目标形态（$form）只接受：$($modJarCandidates -join ' ; ')",
        "先跑：$RepoRoot\gradlew.bat $($line.GradlePath):build",
        $(if ($form -eq 'dev' -and $line.Kind -eq 'forge') { 'forge 线 dev 目标**不回退** build\libs 的 reobf(SRG) 产物（那会在 dev run 里崩）；请确认 :forge-1.20.1:build 已在 build\devlibs 产出 dev(Mojmap) jar' } else { '确认该线构建真的产出了 jar（或去掉 -SkipBuild 重新构建）' })
    )
}
if (-not $SkipBuild -and $modJar) {
    $jarTime = (Get-Item -LiteralPath $modJar).LastWriteTime
    if ($jarTime -lt $startedAt.AddSeconds(-5)) {
        # ⚠️ Gradle 的 UP-TO-DATE 是**合法**结果，不是「没真正重建」：
        #    输入（源码/资源）内容未变时 Gradle 不重写 jar，时间戳自然保持旧值，而该文件仍是
        #    本次输入的正确产物 —— 且 Gradle 的判定基于**输出内容哈希快照**，比时间戳更强。
        #    2026-09-19 实测踩坑：只改了 javadoc 注释 ⇒ 类字节不变 ⇒ `Task :…:jar UP-TO-DATE`
        #    ⇒ 旧判据一律 Fail、部署被自己挡住（run/<版本>/mods 因此长期停在旧库 jar 上，
        #    Start-<版本>.bat 的前置检查于是拒绝启动）。故此处按日志放行 UP-TO-DATE，
        #    只在**日志里也没有 UP-TO-DATE 证据**时才判为「未真正重建」。
        $jarTaskRe = [regex]::Escape("$($line.GradlePath):jar") + '\s+UP-TO-DATE'
        if ($logText -match $jarTaskRe) {
            Write-Warn2 "本模组 jar 时间戳未更新（$jarTime）：构建日志显示 $($line.GradlePath):jar UP-TO-DATE —— Gradle 已按内容哈希确认该产物与本次输入一致，放行部署"
        } else {
            Fail "本模组 jar 时间戳早于本次构建开始，且构建日志里没有 $($line.GradlePath):jar UP-TO-DATE 证据（可能未真正重建）：$modJar ($jarTime)" @(
                "检查 build 日志：$buildLog",
                '必要时先 clean 再构建（gradlew.bat clean ' + $line.GradlePath + ':build）'
            )
        }
    }
    Write-Ok "产物验证：$([System.IO.Path]::GetFileName($modJar))  ($jarTime)"
}
$modForm = Get-JarForm $modJar
Write-Info "本模组 jar 形态：$($modForm.Form)（SRG 命中 $($modForm.Hits)）"
# F3：与库 jar 对称的形态断言（dev ⇒ 两者都必须 dev/Mojmap；prod + forge ⇒ 两者都必须 reobf/SRG）
if ($form -eq 'dev' -and $modForm.Form -ne 'dev/mojmap') {
    Fail "dev 目标下本模组 jar 不是 dev/Mojmap 形态：$modJar" @(
        'dev run 目录必须放 dev/Mojmap 形态 jar：reobf(SRG) 产物会以 NoSuchFieldError(f_xxxxx_) 崩',
        "forge 线正确来源：$RepoRoot\$sub\build\devlibs\$modJarName（先跑 $RepoRoot\gradlew.bat $($line.GradlePath):build）",
        'NeoForge 线（无 reobf 环节）正确来源：' + (Join-Path $RepoRoot "$sub\build\libs\$modJarName")
    )
}
if ($form -eq 'prod' -and $line.Kind -eq 'forge' -and $modForm.Form -ne 'reobf/srg') {
    Fail "prod 目标(1.20.1)下本模组 jar 不是 reobf/SRG 形态：$modJar" @(
        '外部实例/整合包必须放发货形态（reobfJar 产物），dev/Mojmap 形态会被 Forge 以 SRG 名解析不到而报错',
        "正确来源：$RepoRoot\$sub\build\libs\$modJarName",
        '需要 dev 形态请在 run 目录（或 -TargetKind dev）下测试'
    )
}

if (-not (Test-Path -LiteralPath $libJar)) {
    Fail "配套库 jar 不存在：$libJar" @(
        "本模组把 starengine_lib $libVersion 声明为必需前置；只放本模组 jar 会被 FML 拒绝（Currently, starengine_lib is not installed）",
        "1.21.1/26.1.2：在库仓库执行 `./gradlew publishToMavenLocal`（或指定版本）后重试",
        "1.20.1 dev 目标需要库仓库的 dev jar：cd $LibRepoPath && ./gradlew :forge-1.20.1:build（产出 build\devlibs\starengine_lib-forge-1.20.1-$libVersion.jar）",
        "库版本必须与 $sub\gradle.properties 的 starengine_lib_version=$libVersion 一致"
    )
}
$libForm = Get-JarForm $libJar
Write-Info "库 jar 形态：$($libForm.Form)（SRG 命中 $($libForm.Hits)）"
if ($form -eq 'dev' -and $line.Kind -eq 'forge' -and $libForm.Form -ne 'dev/mojmap') {
    Fail "dev 目标(1.20.1)下库 jar 不是 dev/Mojmap 形态：$libJar" @(
        'SRG 形态的库 jar 放进 dev run 会以 NoSuchFieldError 崩',
        "请用库仓库 devlibs 的 dev jar（重新执行库的 :forge-1.20.1:build）"
    )
}
if ($form -eq 'prod' -and $line.Kind -eq 'forge' -and $libForm.Form -ne 'reobf/srg') {
    Fail "prod 目标(1.20.1)下库 jar 不是 reobf/SRG 形态：$libJar" @(
        '外部实例/整合包必须用发货形态（mavenLocal 里的 reobf 库 jar）',
        "正确来源：$libJarM2（在库仓库执行 ./gradlew publishToMavenLocal 后重试）"
    )
}

# 版本一致性（从文件名解析）
if ([System.IO.Path]::GetFileName($modJar) -ne $modJarName) {
    Fail "本模组 jar 名与 gradle.properties 的 mod_version 不一致（期望 $modJarName）" @("实际：$([System.IO.Path]::GetFileName($modJar))")
}
$libJarName = [System.IO.Path]::GetFileName($libJar)
if ($libJarName -notlike "*$libVersion.jar") {
    Fail "库 jar 版本与 starengine_lib_version=$libVersion 不一致：$libJarName" @('先同步消费方 gradle.properties 或重发库 jar')
}

# 先删后拷
if (-not (Test-Path -LiteralPath $TargetDir)) { New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null }
$stale = Get-ChildItem -LiteralPath $TargetDir -File -Filter '*.jar' |
    Where-Object { $_.Name -like 'astral_dice-*.jar' -or $_.Name -like 'starengine_lib-*.jar' }
foreach ($f in $stale) { Write-Info "删除旧 jar：$($f.Name)"; Remove-Item -LiteralPath $f.FullName -Force }

Copy-Item -LiteralPath $modJar -Destination (Join-Path $TargetDir $modJarName) -Force
Copy-Item -LiteralPath $libJar -Destination (Join-Path $TargetDir $libJarName) -Force
Write-Ok "已拷贝：$modJarName"
Write-Ok "已拷贝：$libJarName"

# 部署后校验：各恰一份且版本一致
$after = Get-ChildItem -LiteralPath $TargetDir -File -Filter '*.jar'
$mods = @($after | Where-Object { $_.Name -like 'astral_dice-*.jar' })
$libs = @($after | Where-Object { $_.Name -like 'starengine_lib-*.jar' })
if ($mods.Count -ne 1 -or $libs.Count -ne 1) {
    Fail "部署后目标目录内 astral_dice-*.jar=$($mods.Count) 份、starengine_lib-*.jar=$($libs.Count) 份（应各恰一份）" @(
        "目录：$TargetDir",
        '成对部署是硬要求：本模组把库声明为必需前置，缺库即拒绝启动'
    )
}
Write-Ok "成对校验：$($mods[0].Name) + $($libs[0].Name)（sha256 前 12 位 $((Get-Sha256 $mods[0].FullName).Substring(0,12)) / $((Get-Sha256 $libs[0].FullName).Substring(0,12))）"

# ---------------------------------------------------------------------------
# 6. 可选启动（人工测试路径）
# ---------------------------------------------------------------------------
if ($Launch) {
    Write-Host ''
    Write-Host '===================================================================' -ForegroundColor Yellow
    Write-Host ' 【人工测试路径】-Launch：代理不得自动调用本分支；此处由用户显式触发。' -ForegroundColor Yellow
    Write-Host '===================================================================' -ForegroundColor Yellow
    $clientLog = Join-Path $RepoRoot ("temp\self-test\$Version-client-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $clientLog) | Out-Null
    $gradlew = Join-Path $RepoRoot 'gradlew.bat'
    $p = Start-Process -FilePath $gradlew -ArgumentList @("$($line.GradlePath):runClient", '--console=plain') `
        -WorkingDirectory $RepoRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $clientLog -RedirectStandardError "$clientLog.err"
    Write-Ok "已后台启动 dev 客户端：PID = $($p.Id)"
    Write-Info "日志：$clientLog"
    Write-Info "停止：taskkill /PID $($p.Id) /T /F   或   工具链 mt.ps1 --phase stop --force"
} else {
    Write-Info '未启动客户端（缺省）。需要人工测试时由用户显式加 -Launch。'
}

Write-Host ''
Write-Ok "完成：$Version 已构建并成对部署到 $TargetDir"
Write-Info '用户在游戏内自测时请确认：日志里 starengine_lib 与 astral_dice 都已加载，且无 "Missing or unsupported mandatory dependencies"。'
