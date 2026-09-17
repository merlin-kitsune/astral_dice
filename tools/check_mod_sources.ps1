#!/usr/bin/env pwsh
#Requires -Version 7.0
# -*- coding: utf-8 -*-
<#
.SYNOPSIS
    模组依赖来源检查（统一口径）—— AGENTS.md「模组依赖添加规则（统一口径，1.20.1 + 1.21.1）」的执行体。

.DESCRIPTION
    用法:
        pwsh -NoProfile -File tools/check_mod_sources.ps1 [-Root <仓库根>]

    规则（与 AGENTS.md 同名章节一一对应）:
      R1 来源仓库统一：`forge-1.20.1` 与 `neoforge-1.21.1` 都必须声明同一组模组来源仓库 ——
         Curse Maven（https://www.cursemaven.com）与 Modrinth Maven（https://api.modrinth.com/maven）；
         同一文件内重复声明同一个仓库 URL 视为冗余（FAIL，Gradle 虽会去重，但重复声明会让「统一口径」失真）。
      R2 模组坐标统一：每个**模组**依赖坐标只能是 `curse.maven:` 或 `maven.modrinth:`；
         本地 jar 兜底（`fileTree(...)` / `files(...)`）一律 FAIL（模组不得来自文件系统）。
         非模组库（mixin / gson / guava / asm / sponge-mixin 等）按 `$LibraryGroups` 白名单放行；
         已知官方 maven 的模组依赖按 `$ModExceptions` 登记为**例外**，每次运行都回显，
         便于逐个裁决「是否迁移到 Curse/Modrinth Maven」，不允许静默存在。
      R3 未被任何 build.gradle 引用的本地模组 jar 只作 INFO 回显（**不删文件**，留待用户裁决）。

    退出码: 0 = 通过（R1/R2 全部满足）; 1 = 存在违规。

.NOTES
    机器可读结论行: `MOD_SOURCE_GATE: OK|FAIL violations=<n> exceptions=<n>`
    （`scripts/test/mt_preflight.ps1` 的阶段 P 与 `scripts/test/TESTING-SPEC.md` §9 静态守门共用本脚本。）
    输出统一经 [Console]::Out（UTF-8 无 BOM + 显式 LF），与 tools/check_lang_sync.ps1 同一口径。
#>
[CmdletBinding()]
param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
try { [Console]::OutputEncoding = $utf8NoBom } catch { }
$OutputEncoding = $utf8NoBom

function Write-Line {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text)
    [Console]::Out.Write("$Text`n")
}

# ── 规则表 ────────────────────────────────────────────────────────────────
# R1：统一口径允许的两个模组来源仓库
$script:RequiredRepos = @(
    'https://www.cursemaven.com'
    'https://api.modrinth.com/maven'
)
# R1 豁免：非模组来源的仓库不受「必须声明」约束，仅参与重复声明检查
# （mavenCentral / neoforged / latvian / architectury / jitpack / blamejared / 本地 .oss-basemod-repo）

# R2：非模组库白名单（库 ≠ 模组，不受「只能经 Curse/Modrinth Maven 获取模组」约束）
#     新增库时在此登记，并在 commit message 里说明它是库而不是模组。
$script:LibraryGroups = @(
    'org.spongepowered'      # Sponge Mixin（1.20.1 注解处理器 + compileOnly）
    'net.fabricmc'           # sponge-mixin（26.1.2 侧的 mixin 实现）
    'com.google.code.gson'   # mixin 注解处理器依赖
    'com.google.guava'       # mixin 注解处理器依赖
    'org.ow2.asm'            # mixin 注解处理器依赖
)

# R2：已知例外（**官方 maven 提供的模组依赖**）。每次运行都回显；是否迁移到 Curse/Modrinth Maven
#     由用户裁决（迁移需要目标 Maven 的版本 id/fileId，且必须重跑依赖解析验证）。
$script:ModExceptions = @{
    'mezz.jei'        = 'JEI 官方 maven（maven.blamejared.com）'
    'dev.architectury' = 'Architectury 官方 maven（maven.architectury.dev）——KubeJS 前置库模组'
}

# R3：可能残留本地模组 jar 的目录（只回显，不删）
$script:LocalJarDirs = @('base-mod-libs', 'base-mod-compile-libs')

# ── 目标 ──────────────────────────────────────────────────────────────────
$script:Targets = @('forge-1.20.1', 'neoforge-1.21.1')   # 统一口径约束的两条发布线
$script:InfoTargets = @('neoforge-26.1.2')               # 第三条线：只回显，不参与 R1/R2 判定

$script:Violations = [System.Collections.Generic.List[string]]::new()
$script:Exceptions = [System.Collections.Generic.List[string]]::new()
$script:Infos = [System.Collections.Generic.List[string]]::new()

# ── 解析 ──────────────────────────────────────────────────────────────────
function Get-BuildGradleLines {
    param([Parameter(Mandatory)][string]$Path)
    return @(Get-Content -LiteralPath $Path)
}

function Get-DependencyCoordinate {
    <#
    .SYNOPSIS
        从一条 build.gradle 行的「坐标部分」抽坐标；返回 @(种类, 坐标)。
        种类: mod-spi / modrinth / curse / local-jar / library / exception / unknown / none
    #>
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Rest)

    if ($Rest -match 'fileTree\s*\(|files\s*\(') { return @('local-jar', 'fileTree/files(...)') }
    $m = [regex]::Match($Rest, "['""]([^'""]+)['""]")
    if (-not $m.Success) { return @('none', '') }
    $coord = $m.Groups[1].Value.Trim()
    if ($coord -like 'curse.maven:*') { return @('curse', $coord) }
    if ($coord -like 'maven.modrinth:*') { return @('modrinth', $coord) }
    $group = ($coord -split ':')[0]
    if ($script:ModExceptions.ContainsKey($group)) { return @('exception', $coord) }
    if ($script:LibraryGroups -contains $group) { return @('library', $coord) }
    return @('unknown', $coord)
}

function Test-ModSourceFile {
    param(
        [Parameter(Mandatory)][string]$Project,
        [Parameter(Mandatory)][string]$Path,
        [switch]$Enforce
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        if ($Enforce) { $script:Violations.Add("${Project}: 缺少 $Path") }
        else { $script:Infos.Add("${Project}: 缺少 $Path（跳过）") }
        return
    }

    $lines = Get-BuildGradleLines -Path $Path
    $depRe = '^\s*(modImplementation|modCompileOnly|modRuntimeOnly|modApi|implementation|compileOnly|runtimeOnly|api|jarJar)\b(.*)$'
    $repoRe = "maven\s*\{\s*url\s*=?\s*['""]([^'""]+)['""]"

    $seenRepos = @{}
    $declaredRepos = [System.Collections.Generic.List[string]]::new()

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $raw = $lines[$i]
        $trimmed = $raw.Trim()
        if ($trimmed.StartsWith('//')) { continue }
        $lineNo = $i + 1

        # ── R1：仓库声明（含重复声明检查）──────────────────────────────
        foreach ($rm in [regex]::Matches($raw, $repoRe)) {
            $url = $rm.Groups[1].Value.Trim().TrimEnd('/').ToLowerInvariant()
            $declaredRepos.Add($url) | Out-Null
            if ($seenRepos.ContainsKey($url)) {
                if ($Enforce) { $script:Violations.Add("$Project`:$lineNo R1 重复声明仓库 $url（同一 build.gradle 内应只声明一次）") }
            } else { $seenRepos[$url] = $true }
        }

        # ── R2：依赖坐标 ──────────────────────────────────────────────
        $dm = [regex]::Match($raw, $depRe)
        if (-not $dm.Success) { continue }
        $kind, $coord = Get-DependencyCoordinate -Rest $dm.Groups[2].Value
        switch ($kind) {
            'none' { }
            'curse' { }
            'modrinth' { }
            'library' { }
            'local-jar' {
                # 只有「实际命中某个存在的 jar」才算违规；指向空目录/不存在的目录只是死配置（INFO）。
                $rest = $dm.Groups[2].Value
                $refs = @()
                foreach ($dm2 in [regex]::Matches($rest, "dir:\s*['""]([^'""]+)['""]")) { $refs += $dm2.Groups[1].Value }
                foreach ($fm in [regex]::Matches($rest, 'files\s*\(([^)]*)\)')) {
                    foreach ($qm in [regex]::Matches($fm.Groups[1].Value, "['""]([^'""]+)['""]")) { $refs += $qm.Groups[1].Value }
                }
                $hits = @()
                foreach ($r in $refs) {
                    $p = if ([System.IO.Path]::IsPathRooted($r)) { $r } else { Join-Path (Split-Path -Parent $Path) $r }
                    if (Test-Path -LiteralPath $p -PathType Container) {
                        $hits += @(Get-ChildItem -LiteralPath $p -File -Filter '*.jar' -ErrorAction SilentlyContinue |
                                ForEach-Object { $_.Name })
                    } elseif (Test-Path -LiteralPath $p -PathType Leaf) { $hits += $r }
                }
                if ($hits.Count -gt 0) {
                    if ($Enforce) {
                        $script:Violations.Add("$Project`:$lineNo R2 本地 jar 兜底不得作为模组来源" +
                            "（实际命中：$($hits -join ', ')）：$($trimmed)")
                    }
                } else {
                    $script:Infos.Add("$Project`:$lineNo 本地 jar 兜底指向的目录为空/不存在（不构成实际来源）：$($trimmed)")
                }
            }
            'exception' {
                $group = ($coord -split ':')[0]
                $script:Exceptions.Add("$Project`:$lineNo 例外($($script:ModExceptions[$group]))：$coord") | Out-Null
            }
            'unknown' {
                if ($Enforce) {
                    $script:Violations.Add("$Project`:$lineNo R2 未知来源坐标 `"$coord`"（模组必须用 curse.maven: / maven.modrinth:；" +
                        "确为库则在 tools/check_mod_sources.ps1 的 `$LibraryGroups 登记，确为官方 maven 模组则在 `$ModExceptions 登记）")
                }
            }
        }
    }

    if ($Enforce) {
        foreach ($req in $script:RequiredRepos) {
            if (-not $seenRepos.ContainsKey($req)) {
                $script:Violations.Add("$Project R1 缺少统一的模组来源仓库：$req（两个发布线子项目必须声明同一组来源）")
            }
        }
    }

    # ── R3：本地模组 jar 残留（只回显）────────────────────────────────
    $projDir = Split-Path -Parent $Path
    foreach ($d in $script:LocalJarDirs) {
        $dir = Join-Path $projDir $d
        if (-not (Test-Path -LiteralPath $dir -PathType Container)) { continue }
        $jars = @(Get-ChildItem -LiteralPath $dir -File -Filter '*.jar' -ErrorAction SilentlyContinue)
        if ($jars.Count -eq 0) { continue }
        $names = ($jars | ForEach-Object { $_.Name }) -join ', '
        if ($Enforce) {
            $script:Infos.Add("$Project/$d 内有未被引用的本地 jar（$names）——模组获取已统一走 Curse/Modrinth Maven，" +
                '该目录不再参与编译；是否删除由用户裁决（本脚本不删文件）')
        } else {
            $script:Infos.Add("$Project/$d 内有本地 jar（$names）")
        }
    }
}

# ── 主流程 ────────────────────────────────────────────────────────────────
Write-Line "模组来源统一口径检查（tools/check_mod_sources.ps1）root=$Root"
Write-Line ''

foreach ($p in $script:Targets) {
    Write-Line "[$p]"
    Test-ModSourceFile -Project $p -Path (Join-Path (Join-Path $Root $p) 'build.gradle') -Enforce
}
foreach ($p in $script:InfoTargets) {
    Write-Line "[$p]（仅回显，不作为统一口径的判定对象）"
    Test-ModSourceFile -Project $p -Path (Join-Path (Join-Path $Root $p) 'build.gradle')
}

Write-Line ''
foreach ($e in $script:Exceptions) { Write-Line "  [EXC ] $e" }
foreach ($n in $script:Infos) { Write-Line "  [INFO] $n" }
foreach ($v in $script:Violations) { Write-Line "  [FAIL] $v" }

Write-Line ''
$verdict = if ($script:Violations.Count -eq 0) { 'OK' } else { 'FAIL' }
Write-Line ("MOD_SOURCE_GATE: {0} violations={1} exceptions={2} infos={3}" -f `
        $verdict, $script:Violations.Count, $script:Exceptions.Count, $script:Infos.Count)

if ($script:Violations.Count -gt 0) { exit 1 }
exit 0
