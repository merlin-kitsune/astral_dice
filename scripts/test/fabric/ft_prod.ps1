#Requires -Version 7.0
<#
.SYNOPSIS
    生产映射冒烟（fabic 第四条线）—— 在**真实整合包实例**（生产 intermediary 环境）启动一次，判定产物是否可用。

.DESCRIPTION
    为什么必须有这一层（实测教训，见 KNOWN-ISSUES **KI-F13**）：
    Fabric 的 dev 与生产是**两套映射** —— dev 的 `runClient` 跑在 Loom 的 named(Mojang) 映射下，
    而整合包跑在 **intermediary** 下。同一个类/字段两边名字不同：

        | | 类 | color | $VALUES |
        | dev(named)         | net.minecraft.world.item.Rarity | color      | $VALUES    |
        | 生产(intermediary) | net.minecraft.class_1814        | field_8908 | field_8905 |

    ⇒ 任何**按字符串名反射原版成员**的代码（如 `Rarity.class.getDeclaredField("color")`）
      **在 dev 全绿、在整合包里 100% 崩**（NoSuchFieldException → ExceptionInInitializerError
      → 入口点失败 → 游戏进不去）。
    ⇒ dev 冒烟 / 用例 / 静态闸门**全都测不出**这一类缺陷 —— 唯一可靠判据是
      「**把要发布的那份 jar 在真实生产环境启动一次**」。

    本工具做三件事：
      ① 从实例的版本 JSON 构造生产 classpath（rules 过滤 + Maven 坐标推导）；
      ② 自备 log4j 配置（复现原版 client-1.12.xml 的 File+Console 双 appender）；
      ③ 启动并轮询三类判据 —— 就绪 / 崩溃报告新增 / 入口点失败。

    判据（**先报崩溃，再报就绪**，避免「崩了但日志后段有旧的就绪行」这类假绿）：
      崩溃 = ① `crash-reports/` 出现**启动前不存在**的文件，或 ② 日志出现入口点失败；
      就绪 = 日志出现 `Sound engine started`（原版到主菜单的标志行）。

.PARAMETER Instance
    整合包实例目录（形如 `<mcRoot>\versions\<名>`，目录名同时作为实例 id）。

.PARAMETER McRoot
    `.minecraft` 根目录（提供 `libraries/` 与 `assets/`）。

.PARAMETER Java
    `java.exe` 的绝对路径（不假设 PATH；生产实例由外部启动器提供 Java）。

.PARAMETER TimeoutSec
    就绪等待上限（默认 300；143 个模组的客户端冷启动可能 60–120 s）。

.PARAMETER KeepAlive
    就绪后**不**收停进程（默认收停；需要人工进游戏取证时才加）。

.PARAMETER DryRun
    只构造 classpath 并核对前置文件，不启动游戏。

.OUTPUTS
    机器行（供断言/CI）：
      `AP_FAB_PROD_CP:`         classpath 构造读数
      `AP_FAB_PROD_READY:`      就绪（含耗时）
      `AP_FAB_PROD_CRASH:`      崩溃（含新崩溃报告文件名与首个 Caused by 行）
      `AP_FAB_PROD_FAIL:`       其它失败（入口点失败 / 进程退出 / 超时）
      `MT_FAB_PROD:`            摘要
    退出码：0 = PASS（到主菜单）；1 = 产物缺陷（崩了）；2 = ERROR（前置/参数问题）；12 = 超时。

.NOTES
    ⚠️ 必须用 **PowerShell 7**（用到 `ProcessStartInfo.ArgumentList`，PS 5.1 没有这个 API）。
    ⚠️ 本脚本**不写**整合包目录（只读）；唯一写入 = 实例自身的 `logs/`（由游戏进程写）与
       本仓 `temp/`（launch 子进程日志 + 临时 log4j 配置）。
    ⚠️ 参数里的实例路径**必须由调用方传入**（不写进脚本文件）—— 路径含中文，硬编码会踩编码坑。

.EXAMPLE
    pwsh -NoProfile -File scripts/test/fabric/ft_prod.ps1 `
        -Instance 'D:\.minecraft\versions\1.20.1-Fabric 模组测试' `
        -McRoot 'D:\.minecraft' `
        -Java 'C:\Program Files\Zulu\zulu-21\bin\java.exe'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Instance,
    [Parameter(Mandatory = $true)][string]$McRoot,
    [Parameter(Mandatory = $true)][string]$Java,
    [int]$TimeoutSec = 300,
    [switch]$KeepAlive,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

if ($PSVersionTable.PSVersion.Major -lt 7) {
    Write-Output 'AP_FAB_PROD_FAIL: reason=powershell-too-old (need >= 7 for ProcessStartInfo.ArgumentList)'
    Write-Output ('MT_FAB_PROD: ERROR (ps=' + $PSVersionTable.PSVersion + ')')
    exit 2
}

$Instance = $Instance.TrimEnd('\', '/')
$McRoot = $McRoot.TrimEnd('\', '/')
$instId = Split-Path -Leaf $Instance
$jsonPath = Join-Path $Instance ($instId + '.json')
$clientJar = Join-Path $Instance ($instId + '.jar')
$nativesDir = Join-Path $Instance ($instId + '-natives')

$selfDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $selfDir '..\..\..')).Path
$tempDir = Join-Path $repoRoot 'temp'
if (-not (Test-Path -LiteralPath $tempDir -PathType Container)) { [void](New-Item -ItemType Directory -Force -Path $tempDir) }
$outLog = Join-Path $tempDir 'ft_prod.out.txt'
$errLog = Join-Path $tempDir 'ft_prod.err.txt'
$log4jXml = Join-Path $tempDir 'ft_prod_log4j.xml'

function Write-ProdErr([string]$Reason, [string]$Detail) {
    Write-Output ('AP_FAB_PROD_FAIL: reason=' + $Reason + (if ($Detail) { ' detail=' + $Detail } else { '' }))
    Write-Output ('MT_FAB_PROD: ERROR (' + $Reason + ')')
    exit 2
}

# ── ⓪ 前置 ────────────────────────────────────────────────────────────────
foreach ($p in @(
        @{ n = 'inst-json'; v = $jsonPath },
        @{ n = 'client-jar'; v = $clientJar },
        @{ n = 'java'; v = $Java })) {
    if (-not (Test-Path -LiteralPath $p.v -PathType Leaf)) { Write-ProdErr ('missing-' + $p.n) $p.v }
}
$hasNatives = Test-Path -LiteralPath $nativesDir -PathType Container

$j = Get-Content -LiteralPath $jsonPath -Raw -Encoding UTF8 | ConvertFrom-Json

# ── ① classpath 构造 ──────────────────────────────────────────────────────
function Get-MavenRel([string]$name) {
    # group:artifact:version[:classifier] → <group as path>/<artifact>/<version>/<artifact>-<version>[-classifier].jar
    $p = $name.Split(':')
    if ($p.Count -lt 3) { return $null }
    $g = $p[0]; $a = $p[1]; $v = $p[2]
    $c = if ($p.Count -ge 4) { $p[3] } else { $null }
    $f = if ($c) { "$a-$v-$c.jar" } else { "$a-$v.jar" }
    return ($g -replace '\.', '/') + "/$a/$v/$f"
}
function Test-LibAllowed($lib) {
    # 顺序应用 rules，最后一条匹配的生效；无 rules ⇒ 允许（与原版启动器同语义）
    if (-not $lib.rules) { return $true }
    $allow = $false
    foreach ($r in $lib.rules) {
        $ok = $true
        if ($r.os) {
            if ($r.os.name -and $r.os.name -ne 'windows') { $ok = $false }
            if ($r.os.arch -and $r.os.arch -ne 'x86_64') { $ok = $false }
        }
        if ($r.features) { $ok = $false }   # demo 等特性规则：本环境一律不满足
        if ($ok) { $allow = ($r.action -eq 'allow') }
    }
    return $allow
}

$cp = New-Object System.Collections.Generic.List[string]
$missing = New-Object System.Collections.Generic.List[string]
$libTotal = @($j.libraries).Count
foreach ($l in $j.libraries) {
    if (-not (Test-LibAllowed $l)) { continue }
    $rel = $null
    if ($l.downloads -and $l.downloads.artifact -and $l.downloads.artifact.path) { $rel = $l.downloads.artifact.path }
    else { $rel = Get-MavenRel $l.name }
    if (-not $rel) { $missing.Add('<unresolvable> ' + $l.name); continue }
    $full = Join-Path (Join-Path $McRoot 'libraries') $rel
    if (Test-Path -LiteralPath $full) { $cp.Add($full) } else { $missing.Add($full) }
}
$cp.Add($clientJar)

Write-Output ('AP_FAB_PROD_CP: libraries={0} inCP={1} missing={2} natives={3}' -f $libTotal, $cp.Count, $missing.Count, [int]$hasNatives)
if ($missing.Count -gt 0) {
    Write-Output ('MT_FAB_PROD_NOTE: ' + $missing.Count + ' 个库文件缺失（前 5 条）')
    $missing | Select-Object -First 5 | ForEach-Object { Write-Output ('   ' + $_) }
}
if ($DryRun) { Write-Output 'MT_FAB_PROD: DRYRUN'; exit 0 }

# ── ② 启动前基线 ──────────────────────────────────────────────────────────
$crashDir = Join-Path $Instance 'crash-reports'
$preCrashes = @()
if (Test-Path -LiteralPath $crashDir -PathType Container) {
    $preCrashes = @(Get-ChildItem -LiteralPath $crashDir -File | ForEach-Object { $_.Name })
}
$logPath = Join-Path $Instance 'logs\latest.log'

# log4j：复现原版 client-1.12.xml 的 File + Console 双 appender
# （⚠️ 含 OnStartupTriggeringPolicy —— 与正式启动器一致：每次冷启动把 latest.log 轮转掉，
#   故本脚本的就绪判据读**整文件**而非「启动后新增字节」，避免重蹈测试台 D1 的覆辙。）
$xml = @'
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="warn">
  <Appenders>
    <Console name="SysOut" target="SYSTEM_OUT">
      <PatternLayout pattern="[%d{HH:mm:ss}] [%t/%level]: %msg%n"/>
    </Console>
    <RollingRandomAccessFile name="File" fileName="logs/latest.log" filePattern="logs/%d{yyyy-MM-dd}-%i.log.gz">
      <PatternLayout pattern="[%d{HH:mm:ss}] [%t/%level]: %msg%n"/>
      <Policies>
        <OnStartupTriggeringPolicy/>
        <SizeBasedTriggeringPolicy size="10 MB"/>
      </Policies>
      <DefaultRolloverStrategy max="10"/>
    </RollingRandomAccessFile>
  </Appenders>
  <Loggers>
    <Root level="info">
      <AppenderRef ref="SysOut"/>
      <AppenderRef ref="File"/>
    </Root>
  </Loggers>
</Configuration>
'@
[System.IO.File]::WriteAllText($log4jXml, $xml, [System.Text.UTF8Encoding]::new($false))

$cpString = ($cp -join ';')
$psi = [System.Diagnostics.ProcessStartInfo]::new()
$psi.FileName = $Java
$psi.WorkingDirectory = $Instance          # ← gameDir 即 cwd：logs/ 落在这里
$psi.RedirectStandardOutput = $false
$psi.RedirectStandardError = $false
$A = $psi.ArgumentList
[void]$A.Add('-Xmx2G')
if ($hasNatives) {
    [void]$A.Add('-Djava.library.path=' + $nativesDir)
    [void]$A.Add('-Djna.tmpdir=' + $nativesDir)
    [void]$A.Add('-Dorg.lwjgl.system.SharedLibraryExtractPath=' + $nativesDir)
    [void]$A.Add('-Dio.netty.native.workdir=' + $nativesDir)
}
[void]$A.Add('-Dlog4j.configurationFile=' + $log4jXml)
[void]$A.Add('-Dminecraft.launcher.brand=ft-prod-smoke')
[void]$A.Add('-cp');          [void]$A.Add($cpString)
[void]$A.Add($j.mainClass)
[void]$A.Add('--username');   [void]$A.Add('ProdSmoke')
[void]$A.Add('--version');    [void]$A.Add($instId)
[void]$A.Add('--gameDir');    [void]$A.Add($Instance)
[void]$A.Add('--assetsDir');  [void]$A.Add((Join-Path $McRoot 'assets'))
[void]$A.Add('--assetIndex'); [void]$A.Add([string]$j.assets)
[void]$A.Add('--uuid');       [void]$A.Add('00000000000000000000000000000001')
[void]$A.Add('--accessToken');[void]$A.Add('0')
[void]$A.Add('--userType');   [void]$A.Add('legacy')
[void]$A.Add('--versionType');[void]$A.Add('release')
[void]$A.Add('--width');      [void]$A.Add('1280')
[void]$A.Add('--height');     [void]$A.Add('720')

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$proc = [System.Diagnostics.Process]::Start($psi)
Write-Output ('AP_FAB_PROD_LAUNCH: pid={0} cp_len={1} main={2}' -f $proc.Id, $cpString.Length, $j.mainClass)

function Stop-ProdGame($p) {
    try { if (-not $p.HasExited) { $p.Kill($true); $p.WaitForExit(15000) | Out-Null } } catch { }
}

$verdict = 'TIMEOUT'; $detail = ''
while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
    # ① 崩溃报告新增（最高优先级：崩溃时进程会停在 crash screen，日志可能已停止写入）
    if (Test-Path -LiteralPath $crashDir -PathType Container) {
        $now = @(Get-ChildItem -LiteralPath $crashDir -File | ForEach-Object { $_.Name })
        $new = @($now | Where-Object { $preCrashes -notcontains $_ })
        if ($new.Count -gt 0) {
            $verdict = 'CRASH_REPORT'; $detail = $new[-1]
            break
        }
    }
    # ② 日志（整文件，共享读）
    if (Test-Path -LiteralPath $logPath -PathType Leaf) {
        $txt = $null
        try {
            $fs = [System.IO.File]::Open($logPath, 'Open', 'Read', 'ReadWrite')
            $sr = New-Object System.IO.StreamReader($fs, [System.Text.UTF8Encoding]::new($false))
            $txt = $sr.ReadToEnd(); $sr.Close(); $fs.Close()
        } catch { $txt = $null }
        if ($txt) {
            if ($txt -match 'Could not execute entrypoint stage') { $verdict = 'ENTRYPOINT_FAIL'; break }
            if ($txt -match 'Sound engine started') { $verdict = 'READY'; break }
        }
    }
    if ($proc.HasExited) { $verdict = 'EXITED'; $detail = 'exitcode=' + $proc.ExitCode; break }
    Start-Sleep -Milliseconds 1000
}

$elapsed = [int]$sw.Elapsed.TotalSeconds
switch ($verdict) {
    'READY' {
        Write-Output ('AP_FAB_PROD_READY: elapsed={0}s' -f $elapsed)
        Write-Output ('MT_FAB_PROD: PASS (生产环境启动到主菜单, {0}s)' -f $elapsed)
        if (-not $KeepAlive) { Stop-ProdGame $proc }
        exit 0
    }
    'CRASH_REPORT' {
        # 抓首个 Caused by / 首个异常行，便于一眼定性
        $cause = ''
        $crashFile = Join-Path $crashDir $detail
        if (Test-Path -LiteralPath $crashFile -PathType Leaf) {
            $head = Get-Content -LiteralPath $crashFile -TotalCount 40 -Encoding UTF8
            $cause = (@($head | Where-Object { $_ -match 'Caused by:|Description:' } | Select-Object -First 2)) -join ' | '
        }
        Write-Output ('AP_FAB_PROD_CRASH: elapsed={0}s file={1}' -f $elapsed, $detail)
        if ($cause) { Write-Output ('AP_FAB_PROD_CRASH_CAUSE: ' + $cause) }
        Write-Output ('MT_FAB_PROD: FAIL (产物在生产映射下崩溃 —— 这是 dev 冒烟测不出的那一层)')
        if (-not $KeepAlive) { Stop-ProdGame $proc }
        exit 1
    }
    'ENTRYPOINT_FAIL' {
        Write-Output ('AP_FAB_PROD_FAIL: reason=entrypoint elapsed={0}s' -f $elapsed)
        Write-Output 'MT_FAB_PROD: FAIL (入口点执行失败 —— 与崩溃报告同类，先看日志 Could not execute entrypoint stage 上下文)'
        if (-not $KeepAlive) { Stop-ProdGame $proc }
        exit 1
    }
    default {
        Write-Output ('AP_FAB_PROD_FAIL: reason={0} elapsed={1}s detail={2}' -f $verdict.ToLowerInvariant(), $elapsed, $detail)
        Write-Output ('MT_FAB_PROD: TIMEOUT (未在 {0}s 内见到就绪标记；进程仍在跑则用 ft 的收停口径手工清)' -f $TimeoutSec)
        exit 12
    }
}
