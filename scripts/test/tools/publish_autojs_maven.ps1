#!/usr/bin/env pwsh
#Requires -Version 7.0
# -*- coding: utf-8 -*-
<#
.SYNOPSIS
    把 AutoJS 实机测试库的两个产物按 Maven 布局发布到 mavenLocal（jar + 手写最小 pom.xml）。

.DESCRIPTION
    用法:
        pwsh -NoProfile -File scripts/test/tools/publish_autojs_maven.ps1 [-Force] [-M2Repo <dir>]

    发布坐标（Gradle 侧由 -PautojsTestMod=1 条件启用，见 forge-1.20.1 / fabric-1.20.1 的 build.gradle）:
        cc.sighs.autojs:AutoJS-forge-1.20.1:1.0.0
        cc.sighs.autojs:AutoJS-fabric-1.20.1:1.0.0

    目标目录:
        <m2>\cc\sighs\autojs\<artifactId>\1.0.0\<artifactId>-1.0.0.{jar,pom}

    源 jar（优先用仓库内 vendor 副本；缺失时从构建产物复制，绝不重建）:
        scripts/test/resources/mods/autojs/AutoJS-<loader>-1.20.1-1.0.0.jar
        <- temp/AutoJS-src/targets/<loader>-1.20.1/build/libs/AutoJS-<loader>-1.20.1-1.0.0.jar

    幂等判据:
      1) vendor 副本已存在且 SHA256 == 构建产物 => 不复制（不触碰时间戳、不覆盖）；
         两处 SHA256 不一致 => FAIL（不静默覆盖别人的产物；确认以构建产物为准时加 -Force）。
      2) 目标 jar SHA256 == 源 jar 且 pom 文本逐字节相同 => SKIP（不重写）;
         同一内容重复运行 => 第二次必须全部 SKIP。

    输出（[Console]::Out 显式 LF，UTF-8 无 BOM，机器可读）:
        MT_AUTOJS_MAVEN: OK   — published <coord> jar=… sha256=… pom=…
        MT_AUTOJS_MAVEN: SKIP — <coord> already current (sha256=…)
        MT_AUTOJS_MAVEN: FAIL — <coord> <原因>
        MT_AUTOJS_MAVEN: PROVENANCE — APPENDED|CREATED|SKIP …
        MT_AUTOJS_MAVEN_RESULT: OK|FAIL published=<n> skipped=<n> failed=<n>

.NOTES
    ⚠️ pom 是**本地测试分发件**的元数据：不是 Curse/Modrinth 发布件。
    上游项目 https://github.com/MerrySnow404/AutoJS ，许可证 GPL-3.0（与 jar 内
    mods.toml / fabric.mod.json 的 license 字段一致）。
    PROVENANCE.md 若已存在，本脚本只**追加**自己的两条记录（标记去重），绝不整份覆盖。
#>
[CmdletBinding()]
param(
    [switch]$Force,
    [string]$M2Repo = (Join-Path $env:USERPROFILE '.m2\repository'),
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
try { [Console]::OutputEncoding = $utf8NoBom } catch { }
$OutputEncoding = $utf8NoBom

function Write-Line {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text)
    [Console]::Out.Write("$Text`n")
}

function Get-Sha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

$Group = 'cc.sighs.autojs'
$Version = '1.0.0'
$Upstream = 'https://github.com/MerrySnow404/AutoJS'
$VendorRel = 'scripts/test/resources/mods/autojs'
$ProvenanceRel = 'scripts/test/resources/mods/autojs/PROVENANCE.md'

# 两个产物：同名构建产物 → vendor 副本 → mavenLocal。
$Artifacts = @(
    [pscustomobject]@{
        Id         = 'AutoJS-forge-1.20.1'
        Loader     = 'forge'
        JarName    = 'AutoJS-forge-1.20.1-1.0.0.jar'
        BuildRel   = 'temp/AutoJS-src/targets/forge-1.20.1/build/libs/AutoJS-forge-1.20.1-1.0.0.jar'
        Namespace  = 'srg'
        RemapAtResolve = 'ModDevGradle legacyforge: modRuntimeOnly 解析期 SRG->named'
    },
    [pscustomobject]@{
        Id         = 'AutoJS-fabric-1.20.1'
        Loader     = 'fabric'
        JarName    = 'AutoJS-fabric-1.20.1-1.0.0.jar'
        BuildRel   = 'temp/AutoJS-src/targets/fabric-1.20.1/build/libs/AutoJS-fabric-1.20.1-1.0.0.jar'
        Namespace  = 'intermediary'
        RemapAtResolve = 'Fabric Loom: modRuntimeOnly 解析期 intermediary->named'
    }
)

function New-PomXml {
    param([Parameter(Mandatory = $true)]$Artifact)
    $pom = @"
<?xml version="1.0" encoding="UTF-8"?>
<!--
  AutoJS 实机测试库 —— 本地 mavenLocal 分发件（不是 Curse/Modrinth 发布件）。
  由 scripts/test/tools/publish_autojs_maven.ps1 生成；仅供 astral_dice 的 dev 运行期使用
  （forge-1.20.1 / fabric-1.20.1 的 build.gradle 内 -PautojsTestMod=1 条件 modRuntimeOnly 依赖）。
  上游项目: https://github.com/MerrySnow404/AutoJS
  许可证: GPL-3.0
-->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>cc.sighs.autojs</groupId>
  <artifactId>$($Artifact.Id)</artifactId>
  <version>1.0.0</version>
  <packaging>jar</packaging>

  <name>AutoJS ($($Artifact.Loader)-1.20.1)</name>
  <description>AutoJS 实机测试库（$($Artifact.Loader) / Minecraft 1.20.1）。本工件由 scripts/test/tools/publish_autojs_maven.ps1 从本地构建产物发布到 mavenLocal，不是 Curse/Modrinth 发布件。</description>
  <url>$Upstream</url>

  <licenses>
    <license>
      <name>GNU General Public License v3.0</name>
      <url>https://www.gnu.org/licenses/gpl-3.0.html</url>
      <distribution>repo</distribution>
      <comments>SPDX: GPL-3.0 —— 与 jar 内 mods.toml / fabric.mod.json 的 license 字段一致。</comments>
    </license>
  </licenses>

  <scm>
    <connection>scm:git:$Upstream.git</connection>
    <developerConnection>scm:git:$Upstream.git</developerConnection>
    <url>$Upstream</url>
  </scm>

  <properties>
    <autojs.distribution>local-maven-only</autojs.distribution>
    <autojs.release-channel>not-curse-or-modrinth</autojs.release-channel>
    <autojs.minecraft.version>1.20.1</autojs.minecraft.version>
    <autojs.loader>$($Artifact.Loader)</autojs.loader>
    <autojs.bytecode.namespace>$($Artifact.Namespace)</autojs.bytecode.namespace>
    <autojs.remap-at-resolve>$($Artifact.RemapAtResolve)</autojs.remap-at-resolve>
  </properties>
</project>
"@
    # pom 一律 LF + 无 BOM（与仓库文本纪律一致，且跨轮次可逐字节比较）。
    return ($pom -replace "`r`n", "`n")
}

function Add-ProvenanceBlock {
    param(
        [Parameter(Mandatory = $true)][string]$Block,
        [Parameter(Mandatory = $true)][string]$Marker
    )
    $path = Join-Path $Root $ProvenanceRel
    $dir = Split-Path -Parent $path
    if (-not (Test-Path -LiteralPath $dir -PathType Container)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        $existing = [System.IO.File]::ReadAllText($path)
        if ($existing.Contains($Marker)) { return 'SKIP (already recorded)' }
        $nl = if ($existing.Contains("`r`n")) { "`r`n" } else { "`n" }
        $body = $Block -replace "`r`n", "`n"
        if ($nl -ne "`n") { $body = $body -replace "`n", $nl }
        $prefix = ''
        if ($existing.Length -gt 0 -and -not $existing.EndsWith("`n")) { $prefix = $nl }
        [System.IO.File]::AppendAllText($path, $prefix + $nl + $body, $utf8NoBom)
        return 'APPENDED'
    }
    $body = ($Block -replace "`r`n", "`n") -replace "`n", "`r`n"
    $header = '# AutoJS 测试库来源记录（PROVENANCE）' + "`r`n" + "`r`n" +
        '本文件记录测试库类 jar 的来源与本地分发坐标；各投放步骤只**追加**自己的记录，不覆盖他人内容。' + "`r`n" + "`r`n"
    [System.IO.File]::WriteAllText($path, $header + $body, $utf8NoBom)
    return 'CREATED'
}

Write-Line 'AutoJS -> mavenLocal 发布（scripts/test/tools/publish_autojs_maven.ps1）'
Write-Line ("ROOT: {0}" -f $Root)
Write-Line ("M2  : {0}" -f $M2Repo)
Write-Line ''

$published = 0
$skipped = 0
$failed = 0
$records = [System.Collections.Generic.List[object]]::new()
$marker = '### AutoJS Maven 分发记录（publish_autojs_maven.ps1）'

foreach ($a in $Artifacts) {
    $coord = "$Group`:$($a.Id)`:$Version"
    $vendorPath = Join-Path $Root (Join-Path $VendorRel $a.JarName)
    $buildPath = Join-Path $Root $a.BuildRel
    $targetDir = Join-Path $M2Repo (Join-Path 'cc\sighs\autojs' (Join-Path $a.Id $Version))
    $targetJar = Join-Path $targetDir "$($a.Id)-$Version.jar"
    $targetPom = Join-Path $targetDir "$($a.Id)-$Version.pom"

    # ── 1) 源 jar 落地（缺则从构建产物复制；已存在且 SHA256 相同不覆盖）──
    $vendorState = ''
    try {
        if (Test-Path -LiteralPath $vendorPath -PathType Leaf) {
            $vendorHash = Get-Sha256 -Path $vendorPath
            if (Test-Path -LiteralPath $buildPath -PathType Leaf) {
                $buildHash = Get-Sha256 -Path $buildPath
                if ($vendorHash -ne $buildHash) {
                    if ($Force) {
                        Copy-Item -LiteralPath $buildPath -Destination $vendorPath -Force
                        if ((Get-Sha256 -Path $vendorPath) -ne $buildHash) { throw "重复制后 SHA256 不一致：$vendorPath" }
                        $vendorState = 'RECOPIED(-Force)'
                    } else {
                        throw "vendor 副本与构建产物 SHA256 不一致（vendor=$vendorHash build=$buildHash）；确认以构建产物为准请加 -Force"
                    }
                } else {
                    $vendorState = 'KEPT(identical)'
                }
            } else {
                $vendorState = 'KEPT(build output absent)'
            }
        } else {
            if (-not (Test-Path -LiteralPath $buildPath -PathType Leaf)) {
                throw "缺少源 jar：vendor 副本与构建产物都不存在（$VendorRel/$($a.JarName) / $($a.BuildRel)）"
            }
            $vendorDir = Split-Path -Parent $vendorPath
            if (-not (Test-Path -LiteralPath $vendorDir -PathType Container)) {
                New-Item -ItemType Directory -Path $vendorDir -Force | Out-Null
            }
            Copy-Item -LiteralPath $buildPath -Destination $vendorPath
            if ((Get-Sha256 -Path $vendorPath) -ne (Get-Sha256 -Path $buildPath)) { throw "复制后 SHA256 不一致：$vendorPath" }
            $vendorState = 'COPIED'
        }
    } catch {
        $failed++
        Write-Line "MT_AUTOJS_MAVEN: FAIL — $coord vendor: $($_.Exception.Message)"
        continue
    }

    # ── 2) 发布到 mavenLocal（jar + pom；同内容 => SKIP）──
    try {
        $srcHash = Get-Sha256 -Path $vendorPath
        $pom = New-PomXml -Artifact $a

        $jarUpToDate = (Test-Path -LiteralPath $targetJar -PathType Leaf) -and ((Get-Sha256 -Path $targetJar) -eq $srcHash)
        $pomUpToDate = (Test-Path -LiteralPath $targetPom -PathType Leaf) -and
            ([System.IO.File]::ReadAllText($targetPom) -eq $pom)

        if ($jarUpToDate -and $pomUpToDate) {
            $skipped++
            Write-Line "MT_AUTOJS_MAVEN: SKIP — $coord already current (sha256=$($srcHash.Substring(0, 16))…) dir=$targetDir vendor=$vendorState"
        } else {
            if (-not (Test-Path -LiteralPath $targetDir -PathType Container)) {
                New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
            }
            Copy-Item -LiteralPath $vendorPath -Destination $targetJar -Force
            [System.IO.File]::WriteAllText($targetPom, $pom, $utf8NoBom)
            if ((Get-Sha256 -Path $targetJar) -ne $srcHash) { throw "发布后 jar SHA256 不一致：$targetJar" }
            $published++
            Write-Line "MT_AUTOJS_MAVEN: OK — published $coord jar=$targetJar sha256=$srcHash pom=$targetPom vendor=$vendorState"
        }
        $records.Add([pscustomobject]@{
                Coord = $coord
                Vendor = "$VendorRel/$($a.JarName)"
                Build = $a.BuildRel
                Hash = $srcHash
                Remap = $a.RemapAtResolve
            })
    } catch {
        $failed++
        Write-Line "MT_AUTOJS_MAVEN: FAIL — $coord $($_.Exception.Message)"
    }
}

# ── 3) PROVENANCE.md：只追加自己的两条记录（已存在则不覆盖）──
if ($records.Count -gt 0) {
    $nl = "`n"
    $lines = @()
    $lines += $marker
    $lines += ''
    $lines += '由 scripts/test/tools/publish_autojs_maven.ps1 写入（本地 mavenLocal 分发；**不是** Curse/Modrinth 发布件）。'
    $lines += ''
    foreach ($r in $records) {
        $lines += "- ``$($r.Coord)``"
        $lines += "  - jar: ``$($r.Vendor)``（SHA256 ``$($r.Hash)``）"
        $lines += "  - 构建来源: ``$($r.Build)``"
        $lines += "  - 装载方式: $($r.Remap)"
    }
    $lines += ''
    $block = ($lines -join $nl) + $nl
    try {
        $pv = Add-ProvenanceBlock -Block $block -Marker $marker
        Write-Line ''
        Write-Line "MT_AUTOJS_MAVEN: PROVENANCE — $pv file=$ProvenanceRel"
    } catch {
        Write-Line ''
        Write-Line "MT_AUTOJS_MAVEN: PROVENANCE — FAIL $($_.Exception.Message)"
    }
}

Write-Line ''
$verdict = if ($failed -eq 0) { 'OK' } else { 'FAIL' }
Write-Line ("MT_AUTOJS_MAVEN_RESULT: {0} published={1} skipped={2} failed={3}" -f $verdict, $published, $skipped, $failed)
if ($failed -gt 0) { exit 1 }
exit 0
