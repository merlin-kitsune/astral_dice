#Requires -Version 7.0
<#
.SYNOPSIS
    ft_build — fabric-1.20.1 线构建守护 + **开包核对**（fabric 测试台的 B 阶段）。

.DESCRIPTION
    ① 跑 `gradlew :fabric-1.20.1:build`（超时看门狗 + BUILD SUCCESSFUL/BUILD FAILED 识别 + 重试）；
    ② 把产物 jar **真正解开核对资源条目**（而不是只看 jar 存在）。

    为什么要开包核对（本项目真实坑，已登记在 build.gradle:27-30）：
      fabric-1.20.1 是从 forge-1.20.1 移植来的，移植时**漏抄了 sourceSets 的
      `srcDir('src/generated/resources')`** ⇒ datagen 产物（137 个物品模型 + 118 个配方 + 118 个进度）
      全部不进产物 jar。编译无感、启动无感、测试台「产物存在」也通过 —— 只在游戏里表现为
      物品紫黑 / 配方不存在。因此**只看 jar 是否存在是不够的**，必须打开包数条目，
      且必须与磁盘 `src/` 侧的数量对照（jar 自身数量无法自证「少了」）。

    核对项与判据：
      · assets/astral_dice/models/item/*.json   jar_count ≥ 磁盘 src/generated/... 数量
      · data/astral_dice/recipes/*.json         jar_count ≥ 磁盘 src/generated/... 数量
      · data/bountiful/**                       jar_count == 0（bountiful 不是本线前置；实测源里也没有）
      · data/trinkets/**                        jar_count ≥ 磁盘 src/main/resources/data/trinkets 数量
                                                （替代 Forge 侧 Curios 的饰品数据，见 build.gradle:91-92）
      · META-INF/jars/*.jar                     非空，且含 starengine_lib（Loom 的 include ⇒ JarJar，
                                                依据 build.gradle:85-89 与 :378-407 的内嵌自检）

    机器可读读数行（前缀 AP_FAB_）：
      AP_FAB_JAR:          name=<...> bytes=<n> entries=<n>
      AP_FAB_MODELS:       jar=<n> src=<m> ok=<true|false>
      AP_FAB_RECIPES:      jar=<n> src=<m> ok=<true|false>
      AP_FAB_BOUNTIFUL:    jar=<n> expected=0 ok=<true|false>
      AP_FAB_TRINKETS:     jar=<n> src=<m> ok=<true|false>
      AP_FAB_EMBED:        count=<n> jars=<a.jar,b.jar>
      AP_FAB_EMBED_LIB:    name=starengine_lib present=<true|false>
      AP_FAB_BUILD:        OK|FAIL (reason=<...>)

.PARAMETER Jar
    只对指定 jar 做开包核对（跳过构建）。用于 CI/离线复核。

.PARAMETER SkipInspect
    只构建，不做开包核对。

.PARAMETER Timeout
    单次构建超时秒数（默认 900）。超时后若日志已含 BUILD SUCCESSFUL 则视为完成。

.PARAMETER Retries
    最大尝试次数（默认 2）。

.PARAMETER AllowAuto
    放行批量编排闸门（本脚本属放行清单，一般不需要）。

.EXAMPLE
    pwsh -NoProfile -File scripts/test/fabric/ft_build.ps1
    pwsh -NoProfile -File scripts/test/fabric/ft_build.ps1 --jar fabric-1.20.1/build/libs/astral_dice-1.3.2+fabric_1.20.1.jar
    pwsh -NoProfile -File scripts/test/fabric/ft_build.ps1 --skip-inspect --timeout 1200

.NOTES
    退出码：0 = PASS；2 = ERROR（构建失败/参数非法/工具链缺失）。
    与生产线 mt_build.ps1 的差异：不做「产物时间戳快照对比」（单版本线无需区分版本目录），
    改为「BUILD SUCCESSFUL 识别 + 开包条目核对」；核对失败属 **FAIL(1)**，构建跑不起来属 **ERROR(2)**。
#>

$ErrorActionPreference = 'Stop'

$script:FtLibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:FtLibDir 'Ft.Common.psm1') -Force

Initialize-FtConsole

# ══ 参数（手写 $args 循环，kebab 选项与生产线一致）════════════════════════
$JarArg = ''
$DoBuild = $true
$DoInspect = $true
$TimeoutSec = 900
$Retries = 2
$ArgList = @($args)

$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    $key = Get-FtArgKey -Token $tok
    if ($key -eq 'jar') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --jar 的值'; exit $FT_EXIT_ERROR }
        $JarArg = [string]$args[$i + 1]; $DoBuild = $false; $i += 2
    } elseif ($key -eq 'skipinspect') {
        $DoInspect = $false; $i++
    } elseif ($key -eq 'timeout') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --timeout 的值'; exit $FT_EXIT_ERROR }
        if ([string]$args[$i + 1] -notmatch '^\d+$') { Write-FtErrorLine '--timeout 需要非负整数（秒）'; exit $FT_EXIT_ERROR }
        $TimeoutSec = [int]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'retries') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --retries 的值'; exit $FT_EXIT_ERROR }
        if ([string]$args[$i + 1] -notmatch '^\d+$') { Write-FtErrorLine '--retries 需要非负整数'; exit $FT_EXIT_ERROR }
        $Retries = [int]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'allowauto') {
        $i++
    } elseif ($key -eq 'h' -or $key -eq 'help') {
        Write-FtLine '用法: ft_build.ps1 [--jar <path>] [--skip-inspect] [--timeout N] [--retries N]'
        exit $FT_EXIT_PASS
    } else {
        Write-FtErrorLine "未知参数 $tok"; exit $FT_EXIT_ERROR
    }
}

$root = Get-FtRepoRoot

# ══ 构建 ══════════════════════════════════════════════════════════════════
$buildOk = $false
$buildReason = ''

if ($DoBuild) {
    $gradlewBat = Join-Path $root 'gradlew.bat'
    $gradlewSh = Join-Path $root 'gradlew'
    if (-not (Test-Path -LiteralPath $gradlewBat -PathType Leaf) -and
        -not (Test-Path -LiteralPath $gradlewSh -PathType Leaf)) {
        Write-FtErrorLine "找不到 gradlew（$gradlewBat）"
        Write-FtLine 'AP_FAB_BUILD: FAIL (reason=no-gradlew)'
        exit $FT_EXIT_ERROR
    }

    $tempDir = Join-Path $root 'temp'
    if (-not (Test-Path -LiteralPath $tempDir -PathType Container)) {
        [void](New-Item -ItemType Directory -Force -Path $tempDir)
    }
    $stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $outLog = Join-Path $tempDir "ft_build_${stamp}.out.log"
    $errLog = Join-Path $tempDir "ft_build_${stamp}.err.log"

    $attempt = 0
    while ($attempt -lt $Retries -and -not $buildOk) {
        $attempt++
        Write-FtLine ("MT_FAB_INFO: [尝试 {0}/{1}] gradlew :fabric-1.20.1:build (超时 {2}s)" -f $attempt, $Retries, $TimeoutSec)

        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $proc = $null
        try {
            $proc = Start-Process -FilePath 'cmd.exe' `
                -ArgumentList @('/c', $gradlewBat, ':fabric-1.20.1:build', '--console=plain') `
                -WorkingDirectory $root -NoNewWindow -PassThru `
                -RedirectStandardOutput $outLog -RedirectStandardError $errLog
        } catch {
            Write-FtWarn "无法启动 gradlew：$($_.Exception.Message)"
            $proc = $null
        }

        $timedOut = $false
        if ($null -ne $proc) {
            $exited = $proc.WaitForExit($TimeoutSec * 1000)
            if (-not $exited) {
                $timedOut = $true
                # 超时：不动整棵进程树是不可靠的（gradle wrapper → daemon 有长命后代），逐一收停
                & taskkill.exe /T /F /PID $proc.Id 2>$null | Out-Null
                [void]$proc.WaitForExit(10000)
            }
        }
        $sw.Stop()

        $outTxt = Read-FtLogText -Path $outLog
        $errTxt = Read-FtLogText -Path $errLog
        $allTxt = $outTxt + "`n" + $errTxt

        if ($allTxt -match 'BUILD FAILED') {
            $buildReason = "compile-failed (见 $outLog)"
            Write-FtError 'BUILD' $buildReason
            foreach ($ln in @(($outTxt -split "`r?`n") | Select-Object -Last 15)) { Write-FtErrLine $ln }
            break
        }
        if ($allTxt -match 'BUILD SUCCESSFUL') {
            $buildOk = $true
            $note = if ($timedOut) { '（超时但已 BUILD SUCCESSFUL）' } else { '' }
            Write-FtLine ("MT_FAB_INFO: 构建成功{0}，耗时 {1:N1}s" -f $note, $sw.Elapsed.TotalSeconds)
            break
        }
        $buildReason = "no-build-result (第 $attempt 次尝试未看到 BUILD SUCCESSFUL)"
        Write-FtWarn $buildReason
    }
} else {
    $buildOk = $true
    Write-FtLine 'MT_FAB_INFO: --jar 模式，跳过构建'
}

if (-not $buildOk) {
    Write-FtLine ("AP_FAB_BUILD: FAIL (reason={0})" -f $buildReason)
    exit $FT_EXIT_ERROR
}

# ══ 开包核对 ══════════════════════════════════════════════════════════════
if (-not $DoInspect) {
    Write-FtOk 'BUILD' '构建完成（--skip-inspect，未开包核对）'
    Write-FtLine 'AP_FAB_BUILD: OK (inspect=skipped)'
    exit $FT_EXIT_PASS
}

$jar = $JarArg
if (-not $jar) { $jar = Get-FtProductJar }
if (-not $jar) {
    Write-FtError 'BUILD' "找不到产物 jar（fabric-1.20.1/build/libs/astral_dice-*.jar）——构建可能未真正产出"
    Write-FtLine 'AP_FAB_BUILD: FAIL (reason=jar-not-found)'
    exit $FT_EXIT_ERROR
}

try {
    $st = Get-FtJarStats -JarPath $jar
} catch {
    Write-FtError 'BUILD' "开包失败：$($_.Exception.Message)"
    Write-FtLine 'AP_FAB_BUILD: FAIL (reason=jar-unreadable)'
    exit $FT_EXIT_ERROR
}

Write-FtLine ("AP_FAB_JAR: name={0} bytes={1} entries={2}" -f $st.JarName, $st.Bytes, $st.Entries)

$okModels = ($st.Models -ge $st.SrcModels)
Write-FtLine ("AP_FAB_MODELS: jar={0} src={1} ok={2}" -f $st.Models, $st.SrcModels, $okModels.ToString().ToLowerInvariant())

$okRecipes = ($st.Recipes -ge $st.SrcRecipes)
Write-FtLine ("AP_FAB_RECIPES: jar={0} src={1} ok={2}" -f $st.Recipes, $st.SrcRecipes, $okRecipes.ToString().ToLowerInvariant())

$okBountiful = ($st.Bountiful -eq 0 -and $st.SrcBountiful -eq 0)
Write-FtLine ("AP_FAB_BOUNTIFUL: jar={0} expected=0 ok={1}" -f $st.Bountiful, $okBountiful.ToString().ToLowerInvariant())

$okTrinkets = ($st.Trinkets -ge $st.SrcTrinkets -and $st.Trinkets -gt 0)
Write-FtLine ("AP_FAB_TRINKETS: jar={0} src={1} ok={2}" -f $st.Trinkets, $st.SrcTrinkets, $okTrinkets.ToString().ToLowerInvariant())

Write-FtLine ("AP_FAB_EMBED: count={0} jars={1}" -f $st.EmbedJars.Count, ($st.EmbedJars -join ','))

$libName = ''
foreach ($n in $st.EmbedJars) { if ($n -like 'starengine_lib*') { $libName = $n } }
$okEmbed = ($st.EmbedJars.Count -gt 0 -and $libName -ne '')
Write-FtLine ("AP_FAB_EMBED_LIB: name=starengine_lib present={0}" -f $okEmbed.ToString().ToLowerInvariant())

$failed = @()
if (-not $okModels) { $failed += "models($($st.Models)<$($st.SrcModels))" }
if (-not $okRecipes) { $failed += "recipes($($st.Recipes)<$($st.SrcRecipes))" }
if (-not $okBountiful) { $failed += "bountiful($($st.Bountiful)!=0)" }
if (-not $okTrinkets) { $failed += "trinkets($($st.Trinkets) vs src $($st.SrcTrinkets))" }
if (-not $okEmbed) { $failed += "embed($($st.EmbedJars.Count))" }

if ($failed.Count -gt 0) {
    Write-FtLine ("AP_FAB_BUILD: FAIL (reason={0})" -f ($failed -join ';'))
    Write-FtFail 'BUILD' ("开包核对失败: " + ($failed -join '; ') + " —— 见 $($st.JarPath)")
    exit $FT_EXIT_FAIL
}

Write-FtLine 'AP_FAB_BUILD: OK'
Write-FtOk 'BUILD' ("开包核对通过: models=$($st.Models) recipes=$($st.Recipes) trinkets=$($st.Trinkets) bountiful=0 embed=$($libName)")
exit $FT_EXIT_PASS
