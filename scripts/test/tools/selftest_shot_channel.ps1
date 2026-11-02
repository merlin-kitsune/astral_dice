#Requires -Version 7.0
<#
.SYNOPSIS
    截图通道**离线自测**（不起游戏、不用 AutoJS）：证明「落盘兜底」真的能把时间戳命名的游戏截图
    收成 <tag>.png 并登记；同时证明「调用抛异常」和「调用成功但一个文件都没落」仍然报失败。

.DESCRIPTION
    为什么要单测这条路径（2026-11-02 第二轮）：
      上一轮 26.1.2 的 #57/#58 截图判 BLOCK，根因有两个，都不在模组侧：
        ① 4 参 grab 在 26.1.2 **不存在**（只有 3 参与 5 参），T1 直接 InternalError；
        ② T2 其实**调用成功了**，但游戏按时间戳命名落盘到 gameDir/screenshots，测试台只找 <tag>.png，
           于是判 called_no_file。
    本自测用 -EvalOverride / -PathsOverride 注入两个假场景，把这两条判据钉死：
      场景 A：eval 说成功 + 只有时间戳名 PNG（> 20480B）⇒ 必须复制改名 <tag>.png 并登记进 .mt_shots.json
      场景 B：eval 说调用抛异常（InternalError: Can't find method …）⇒ 必须 DELEGATED，清单零新增
      场景 C：eval 说成功但没有任何文件 ⇒ 必须 DELEGATED（called_no_file），清单零新增（防假成功）

    自测产物全部落在 run/26.1.2/.tmp/_selftest-shot-<stamp>/ 下，**不碰**真仓库的
    run/26.1.2/screenshots 与 prodgame。

.PARAMETER Keep
    保留自测临时目录（默认跑完即删）。
#>
[CmdletBinding()]
param([switch]$Keep)

$ErrorActionPreference = 'Stop'
$script:Fail = 0
$script:Pass = 0

function Assert-True {
    param([bool]$Condition, [string]$Name, [string]$Detail = '')
    if ($Condition) {
        $script:Pass++
        Write-Output ('  [PASS] ' + $Name)
    } else {
        $script:Fail++
        Write-Output ('  [FAIL] ' + $Name + ' :: ' + $Detail)
    }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$lib = Join-Path $repoRoot 'scripts/test/lib'
Import-Module (Join-Path $lib 'Mt.Conf.psm1')
Import-Module (Join-Path $lib 'Mt.Paths.psm1')
Import-Module (Join-Path $lib 'Mt.AutoJs.psm1')
Import-Module (Join-Path $lib 'Mt.Channel.psm1')

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$root = Join-Path $repoRoot ('run/26.1.2/.tmp/_selftest-shot-' + $stamp)
$shotDir = Join-Path $root 'shots'
$runDir = Join-Path $root 'runroot'
$gameDir = Join-Path $root 'game'
$gameShots = Join-Path $gameDir 'screenshots'
foreach ($d in @($shotDir, $runDir, $gameShots)) { [void](New-Item -ItemType Directory -Force -Path $d) }

$fakePaths = [pscustomobject]@{
    version    = '26.1.2'
    run_dir    = $runDir
    mods_dir   = (Join-Path $runDir 'mods')
    shot_dir   = $shotDir
    saves_dir  = (Join-Path $runDir 'saves')
    logs_dir   = (Join-Path $runDir 'logs')
    latest_log = (Join-Path $runDir 'logs/latest.log')
    crash_dir  = (Join-Path $runDir 'crash-reports')
}
$manifest = Join-Path $shotDir '.mt_shots.json'

function Get-ManifestCount {
    if (-not (Test-Path -LiteralPath $script:manifest -PathType Leaf)) { return 0 }
    $j = Get-Content -LiteralPath $script:manifest -Raw | ConvertFrom-Json
    $items = @()
    if ($null -ne $j) {
        if ($null -ne $j.PSObject.Properties['shots']) { $items = @($j.shots) } else { $items = @($j) }
    }
    return @($items).Count
}

# ── 模板 PNG：优先用仓库里**真实**的游戏截图（proof_* / 时间戳名），拿不到就合成一个占位件 ──
function Get-TemplatePng {
    $cands = @()
    foreach ($d in @('run/26.1.2/prodgame/screenshots', 'run/26.1.2/screenshots')) {
        $full = Join-Path $repoRoot $d
        if (Test-Path -LiteralPath $full -PathType Container) {
            $cands += @(Get-ChildItem -LiteralPath $full -Filter '*.png' -File | Where-Object { $_.Length -gt 20480 })
        }
    }
    if ($cands.Count -gt 0) { return ($cands | Sort-Object Length -Descending | Select-Object -First 1) }
    return $null
}

function New-FakePng {
    param([Parameter(Mandatory)][string]$Path, [int]$Bytes = 30000, [switch]$Synthetic)
    $tpl = Get-TemplatePng
    # -Synthetic：强制走合成件（场景 E 需要一张**长度可控且与模板不同**的旧图当诱饵）。
    if ($null -ne $tpl -and -not $Synthetic) {
        Copy-Item -LiteralPath $tpl.FullName -Destination $Path -Force
        # 注意：Copy-Item **保留源的 LastWriteTime**（模板图是 2026-09-26 的旧图）。
        # 截图新鲜度判据看的是 mtime（不是登记时刻），所以这里必须显式把 mtime 改成「现在」，
        # 否则 Wait-MtShotFile 会把这张刚"落盘"的图当成上一世代的残留。第一版自测就在这儿
        # 假失败过一次（called_no_file），原因正是忘了 touch mtime。
        (Get-Item -LiteralPath $Path).LastWriteTime = Get-Date
        return 'real_template'
    }
    # 无真实模板时退化为合成件：结构上只有 PNG 签名 + 填充 + IEND（**不是**可解码图像，
    # 仅用于验证兜底的落盘/改名/登记链路；控制台会明确打上 SYNTHETIC 标记）。
    $head = [byte[]](0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    $pad = New-Object byte[] $Bytes
    for ($i = 0; $i -lt $pad.Length; $i++) { $pad[$i] = 0x41 }
    $iend = [byte[]](0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82)
    $all = New-Object byte[] ($head.Length + $pad.Length + $iend.Length)
    [Array]::Copy($head, 0, $all, 0, $head.Length)
    [Array]::Copy($pad, 0, $all, $head.Length, $pad.Length)
    [Array]::Copy($iend, 0, $all, $head.Length + $pad.Length, $iend.Length)
    [System.IO.File]::WriteAllBytes($Path, $all)
    return 'SYNTHETIC'
}

$tplInfo = Get-TemplatePng
if ($null -ne $tplInfo) {
    Write-Output ('模板 PNG：' + $tplInfo.FullName + ' (' + $tplInfo.Length + ' B)')
} else {
    Write-Output '模板 PNG：SYNTHETIC（仓库里找不到 >20480B 的真实截图，改用合成件）'
}

Write-Output ''
Write-Output '=== 场景 A：调用成功，但落盘名是时间戳（26.1.2 的 3 参 grab 行为）==='
$script:scenarioAFile = ''
$evalA = {
    param($js)
    # 模拟游戏：只写时间戳命名的 PNG 到 gameDir/screenshots，**不写** <tag>.png
    $name = '2026-11-02_05.11.02.png'
    $script:scenarioAFile = Join-Path $gameShots $name
    [void](New-FakePng -Path $script:scenarioAFile -Bytes 60000)
    return @{
        Ok           = $true
        Error        = ''
        Diagnostics  = $null
        Value        = $null
        ValueParsed  = @{
            ok     = $true
            err    = ''
            work   = $gameDir
            rtType = 'com.mojang.blaze3d.pipeline.MainTarget'
            tier   = 1
            named  = $false
        }
    }
}
$beforeA = Get-ManifestCount
$resA = Invoke-MtChannelShot -Version '26.1.2' -Tag 'selftest_ts' -EvalOverride $evalA -PathsOverride $fakePaths
$dstA = Join-Path $shotDir 'selftest_ts.png'
Assert-True ([string]$resA.Status -eq 'OK') 'A1 结果 Status=OK（兜底收上来才算成功）' ([string]$resA.Status + ' / ' + [string]$resA.Detail)
Assert-True (Test-Path -LiteralPath $dstA -PathType Leaf) 'A2 <tag>.png 落在 run/<ver>/screenshots' $dstA
$lenA = 0
if (Test-Path -LiteralPath $dstA) { $lenA = (Get-Item -LiteralPath $dstA).Length }
Assert-True ($lenA -gt 20480) 'A3 目标是真 PNG 且 > 20480 B' ([string]$lenA)
Assert-True (Test-MtShotFileReady -Path $dstA) 'A4 Test-MtShotFileReady 通过（尾部有 IEND）' $dstA
$detailA = [string]$resA.Detail
Assert-True ($detailA.Contains('tier=T1')) 'A5 detail 里 tier 明确（tier=T1）' $detailA
Assert-True ($detailA.Contains('sig=grab(File,String,Target,int,Consumer)')) 'A6 detail 里 sig 是候选表第一档' $detailA
Assert-True ($detailA.Contains('src=fallback_copy:')) 'A7 detail 标出兜底来源 src=fallback_copy:<gameDir 路径>' $detailA
Assert-True ($detailA.Contains('registered')) 'A8 登记成功（无 add_shot_err）' $detailA
$afterA = Get-ManifestCount
Assert-True ($afterA -eq ($beforeA + 1)) 'A9 .mt_shots.json 新增 1 条（只经 Add-MtShot 登记）' ([string]$beforeA + ' -> ' + [string]$afterA)
$srcKept = $false
if ($script:scenarioAFile) { $srcKept = Test-Path -LiteralPath $script:scenarioAFile -PathType Leaf }
Assert-True $srcKept 'A10 游戏原本那张（时间戳名）仍在 gameDir/screenshots（复制而非搬走）' ([string]$script:scenarioAFile)

Write-Output ''
Write-Output '=== 场景 B：调用抛异常（26.1.2 的 4 参 grab，复现上一轮 T1 报错）==='
$evalB = {
    param($js)
    return @{
        Ok          = $true
        Error       = ''
        Diagnostics = $null
        Value       = $null
        ValueParsed = @{
            ok     = $false
            err    = "InternalError: Can't find method net.minecraft.client.Screenshot.grab(java.io.File,string,com.mojang.blaze3d.pipeline.MainTarget,adapter1)."
            work   = $gameDir
            rtType = 'com.mojang.blaze3d.pipeline.MainTarget'
        }
    }
}
$beforeB = Get-ManifestCount
$resB = Invoke-MtChannelShot -Version '26.1.2' -Tag 'selftest_throw' -EvalOverride $evalB -PathsOverride $fakePaths
$afterB = Get-ManifestCount
Assert-True ([string]$resB.Status -ne 'OK') 'B1 抛异常时**不得**报 OK' ([string]$resB.Status)
Assert-True ([string]$resB.Status -eq 'DELEGATED') 'B2 报 DELEGATED（通道表达不了，而不是假成功）' ([string]$resB.Status)
Assert-True (-not (Test-Path -LiteralPath (Join-Path $shotDir 'selftest_throw.png'))) 'B3 没有产出 <tag>.png' 'unexpected file'
Assert-True ($afterB -eq $beforeB) 'B4 .mt_shots.json 零新增（不登记假条目）' ([string]$beforeB + ' -> ' + [string]$afterB)
$detailB = [string]$resB.Detail
Assert-True ($detailB.Contains('not_called')) 'B5 失败理由逐档留痕（not_called）' $detailB

Write-Output ''
Write-Output '=== 场景 C：调用成功但一个文件都没落 ⇒ 仍须报失败（防假成功）==='
$evalC = {
    param($js)
    return @{
        Ok          = $true
        Error       = ''
        Diagnostics = $null
        Value       = $null
        ValueParsed = @{ ok = $true; err = ''; work = $gameDir; rtType = 'com.mojang.blaze3d.pipeline.MainTarget' }
    }
}
$beforeC = Get-ManifestCount
$resC = Invoke-MtChannelShot -Version '26.1.2' -Tag 'selftest_nofile' -EvalOverride $evalC -PathsOverride $fakePaths
$afterC = Get-ManifestCount
Assert-True ([string]$resC.Status -eq 'DELEGATED') 'C1 无文件时 Status=DELEGATED（ok:true 不等于成功）' ([string]$resC.Status)
Assert-True ($afterC -eq $beforeC) 'C2 .mt_shots.json 零新增' ([string]$beforeC + ' -> ' + [string]$afterC)
$detailC = [string]$resC.Detail
Assert-True ($detailC.Contains('called_no_file')) 'C3 失败理由写明 called_no_file（且带 work=/rt=/scanned=）' $detailC
Assert-True ($detailC.Contains('work=')) 'C4 called_no_file 里带 gameDir（下次排查一眼看出扫过哪儿）' $detailC

Write-Output ''
Write-Output '=== 场景 E：gameDir/screenshots 里已有同名旧图，本次覆盖同名新图（T1 不该被路径黑名单误杀）==='
# 现象来源（2026-11-02 实机跑 RELEASE150）：#57 那张拿到 tier=T1，后两张（#58 / release150_guide）却退到 T3。
# 根因不是签名表，而是「旧图黑名单按**路径**记」——游戏覆盖同名文件后 mtime 变新，路径却仍在黑名单里被跳过，
# 于是能指定文件名的那一档白白超时。现在 $pre 记的是「快照那一刻的 mtime」，只有文件没变才排除。
$collideTag = 'selftest_collide'
$collideSrc = Join-Path $gameShots ($collideTag + '.png')
[void](New-FakePng -Path $collideSrc -Bytes 90000 -Synthetic)
(Get-Item -LiteralPath $collideSrc).LastWriteTime = (Get-Date).AddHours(-3)   # 先摆一张 3 小时前的同名旧图（90000 B）
$collideOldLen = (Get-Item -LiteralPath $collideSrc).Length
$script:collideFile = $collideSrc
$evalE = {
    param($js)
    # 模拟游戏：把 <tag>.png 覆盖写入 gameDir/screenshots（同名、新内容、新 mtime）
    [void](New-FakePng -Path $script:collideFile -Bytes 45000)
    return @{
        Ok          = $true
        Error       = ''
        Diagnostics = $null
        Value       = $null
        ValueParsed = @{ ok = $true; err = ''; work = $gameDir; rtType = 'com.mojang.blaze3d.pipeline.MainTarget' }
    }
}
$beforeE = Get-ManifestCount
$resE = Invoke-MtChannelShot -Version '26.1.2' -Tag $collideTag -EvalOverride $evalE -PathsOverride $fakePaths
$afterE = Get-ManifestCount
$dstE = Join-Path $shotDir ($collideTag + '.png')
$detailE = [string]$resE.Detail
Assert-True ([string]$resE.Status -eq 'OK') 'E1 存在同名旧图时仍须 Status=OK' $detailE
Assert-True ($detailE.Contains('tier=T1')) 'E2 不许因路径黑名单退档（仍须 tier=T1）' $detailE
$lenE = 0
if (Test-Path -LiteralPath $dstE) { $lenE = (Get-Item -LiteralPath $dstE).Length }
Assert-True (($lenE -gt 0) -and ($lenE -ne $collideOldLen)) 'E3 收上来的是**新图**（字节数 != 3 小时前那张旧图）' ([string]$lenE + ' vs old ' + [string]$collideOldLen)
Assert-True ($afterE -eq ($beforeE + 1)) 'E4 .mt_shots.json 新增 1 条（只经 Add-MtShot）' ([string]$beforeE + ' -> ' + [string]$afterE)
Assert-True (Test-MtShotFileReady -Path $dstE) 'E5 目标 PNG 完好（尾部 IEND）' $dstE

Write-Output ''
Write-Output '=== 附加：候选表与 JS 静态检查 ==='
$cands = @(Get-MtChannelShotCandidates)
Assert-True ($cands.Count -eq 8) 'D1 候选表 8 档' ([string]$cands.Count)
Assert-True ($cands[0].method -eq 'grab' -and (@($cands[0].shape) -join ',') -eq 'File,String,Target,int,Consumer') 'D2 第一档 = 26.1.2 实证可跑的 5 参 grab' ((Format-MtShotShape -Method $cands[0].method -Shape $cands[0].shape))
$js1 = Get-MtChannelShotJs -Tier 1 -NameJsLiteral "'a.png'"
Assert-True ($js1.Contains("var SHAPE = [""File"",""String"",""Target"",""int"",""Consumer""]")) 'D3 JS 里 SHAPE 是 JSON 数组（不是裸串）' 'shape json'
Assert-True ($js1.Contains("var NAME = 'a.png'")) 'D4 JS 里 NAME 原样注入字符串字面量' 'name literal'
$jsBad = $false
try { [void](Get-MtChannelShotJs -Tier 1 -NameJsLiteral 'a.png') } catch { $jsBad = $true }
Assert-True $jsBad 'D5 裸文件名（不带引号）被拒绝（否则会生成 var NAME = a.png 语法错误）' 'no throw'
Assert-True ($js1.Contains('f.apply(S, args)')) 'D6 JS 走 f.apply(S, args) 的统一调用（方法名/类型序来自候选表）' 'apply'
Assert-True (-not $js1.Contains('com.mojang.blaze3d.pipeline')) 'D7 JS 里没有写死渲染目标类的全名（com.mojang.blaze3d.pipeline…）' 'hardcoded'
# 说明：JS 里出现 mainRenderTarget 是**字段名**（运行期 pick 的第一个候选），不是类名；
# 所以 D7 只能判「类全名」不能判裸词，否则会把合法的字段访问误判成写死。
Assert-True (-not $js1.Contains('S.grab(')) 'D8 JS 里没有 S.grab(...) 直调（方法名与类型序都来自候选表，走 f.apply）' 'hardcoded call'
Assert-True ($js1.Contains('valForType')) 'D9 实参按参数类型名现造（valForType）' 'valForType'

Write-Output ''
$total = $script:Pass + $script:Fail
Write-Output ('MT_SELFTEST_RESULT ' + $(if ($script:Fail -eq 0) { 'PASS' } else { 'FAIL' }) + ' pass=' + $script:Pass + ' fail=' + $script:Fail + ' total=' + $total)
Write-Output ('SELFTEST_ROOT ' + $root)

if (-not $Keep) {
    try { Remove-Item -LiteralPath $root -Recurse -Force } catch { Write-Output ('清理失败（可忽略）：' + $_.Exception.Message) }
}

if ($script:Fail -ne 0) { exit 1 }
exit 0
