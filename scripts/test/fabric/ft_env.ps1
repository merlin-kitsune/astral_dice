#Requires -Version 7.0
<#
.SYNOPSIS
    ft_env — fabric-1.20.1 线运行环境装配/校验（fabric 测试台的 E 阶段）。

.DESCRIPTION
    与生产线的根本差异（为什么不能照搬 mt_env.ps1）：
      · 运行目录**在子项目内**：`fabric-1.20.1/run/{client,server}`（依据 build.gradle:172-182 的
        `runDir 'run/client'/'run/server'`，Loom 相对**子项目**解析），而不是仓库根 `run/<版本>`。
      · 前置清单完全不同：Curios/MixinBooster → Trinkets + Puzzles Lib + Forge Config API Port
        +（dev 必需的）Cardinal Components +（可选）Accessories/Cloth Config。
      · **dev 运行的两条路径要分开判**（这是最容易误判的一点）：
          ① `gradlew :fabric-1.20.1:runServer/runClient`：Loom 把 `modRuntimeOnly` 依赖
             remap 后**放进 classpath**（实测 `<sub>/build/loom-cache/remapped_working/`），
             `run/<side>/mods` 里**不需要**再放同一批 jar（放进去会与 classpath 重复加载）。
          ② 直接 java 启动 / 生产形态实例：前置 jar 必须**物理落在** `run/<side>/mods`。
        因此 `--mode verify` 判定「前置是否可得」时，**loom 缓存命中即算可得**，
        并额外报告 mods 目录实况；`--mode install` 才把缺失前置从 `--from`（默认 loom 缓存）
        拷进 mods 目录（供第 ② 条路径用），且会打印重复加载告警。

.PARAMETER Side
    client | server | both（默认 both）。

.PARAMETER Mode
    verify（默认，只读）| install（把缺失前置拷进 run/<side>/mods）。

.PARAMETER From
    install 模式的来源目录（默认 `<sub>/build/loom-cache/remapped_working`）。

.PARAMETER EnableRcon
    服务端：把 `enable-rcon=true` / `rcon.port` / `rcon.password` 写进 `run/server/server.properties`，
    供 ft_inject 的 rcon 通道使用（默认端口 25575，默认口令 astralft）。

.PARAMETER InstallProbe
    把 KubeJS **读数探针**脚本拷进 `run/<side>/kubejs/{server_scripts|client_scripts}/`。
    可跟一个脚本名（默认 `event_bridge_probe.js`），源目录 = 本测试台目录 `scripts/test/fabric/`。
    ⚠️ KubeJS 只在**冷启动**或 `/kubejs reload <type>` 后加载脚本 ⇒ 装完必须重启才生效。
    ⚠️ 为什么不再提供「命令队列观察者」（旧 `--install-watcher`）：实测 KubeJS 6+ 的类过滤
       不放行 `java.nio.file.*` / `java.io.*`、脚本 bindings 无文件包装器、`java`/`Packages`
       全局已移除 ⇒ 观察者无法读队列文件，**从未成功执行过一条命令**。KubeJS 在本台的正确定位是
       **「产出读数的探针」**（往日志打 `AP_…` / 自定义标记行，供 ft_assert 断言），
       而**命令注入统一走 RCON**（见 ft_inject.ps1 的说明）。

.EXAMPLE
    pwsh -NoProfile -File scripts/test/fabric/ft_env.ps1 --side both
    pwsh -NoProfile -File scripts/test/fabric/ft_env.ps1 --side server --enable-rcon
    pwsh -NoProfile -File scripts/test/fabric/ft_env.ps1 --side server --install-probe
    pwsh -NoProfile -File scripts/test/fabric/ft_env.ps1 --side server --mode install

.NOTES
    机器可读读数行（前缀 AP_FAB_ENV*）。退出码：0 = PASS；1 = FAIL（缺硬前置/缺关键文件）；2 = ERROR。
    依据（前置清单）：
      · fabric.mod.json:29-40（depends: fabricloader/minecraft/fabric-api/trinkets/
        fabric-data-attachment-api-v1/puzzleslib/starengine_lib；recommends: accessories）
      · build.gradle:80-82（Puzzles Lib 硬依赖 + Forge Config API Port 为 dev 运行期投放）
      · build.gradle:91-92（Trinkets 硬前置）
      · build.gradle:105-110（Accessories 可选 + Cloth Config 为其硬前置）
      · build.gradle:123-128（Cardinal Components：Trinkets 声明硬依赖但**未内嵌**⇒ dev 运行环境必须投放）
      · build.gradle:85-89（starengine_lib 由 Loom include 内嵌，**不需要**在 mods 里单放）
      · server.properties 原生键：enable-rcon / rcon.port / rcon.password（实测该文件:4,33,41）
#>

$ErrorActionPreference = 'Stop'

$script:FtLibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:FtLibDir 'Ft.Common.psm1') -Force

Initialize-FtConsole

# ══ 通用：server.properties 风格的 key=value 定点改写（保行尾、保其它行原样）══
function Set-FtPropFileKey {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][hashtable]$Pairs
    )
    $raw = [System.IO.File]::ReadAllText($Path)
    $nl = if ($raw -match "`r`n") { "`r`n" } else { "`n" }
    $lines = $raw -split "`r?`n"
    $out = New-Object System.Collections.Generic.List[string]
    $seen = @{}
    foreach ($ln in $lines) {
        $m = [regex]::Match($ln, '^([A-Za-z0-9_.\-]+)=')
        if ($m.Success -and $Pairs.ContainsKey($m.Groups[1].Value)) {
            $k = $m.Groups[1].Value
            $out.Add("$k=$($Pairs[$k])")
            $seen[$k] = $true
        } else {
            $out.Add($ln)
        }
    }
    foreach ($k in @($Pairs.Keys)) {
        if (-not $seen.ContainsKey($k)) { $out.Add("$k=$($Pairs[$k])") }
    }
    [System.IO.File]::WriteAllText($Path, ($out -join $nl), [System.Text.UTF8Encoding]::new($false))
}

# ══ 前置清单（正则按 jar 文件名匹配；Loom 侧与 mods 侧同一套正则）══════════
function Get-FtRequirements {
    [CmdletBinding()]
    param()
    # ⚠️ fabric-api 的正则要小心：Loom 缓存的命名是 `<group>-<artifact>-<hash8>-<version>.jar`，
    #    即 `remapped.net.fabricmc.fabric-api-fabric-api-c2d2b86c-0.92.12+1.20.1.jar`
    #    ⇒ 「根模块」判据 = `fabric-api-fabric-api-` 后紧跟 **8 位十六进制 hash + '-' + 数字**。
    #    不能简单写 `fabric-api-fabric-api-\d`（hash 挡在版本号前，实测不匹配），
    #    也不能写 `fabric-api-fabric-api-[0-9a-f]`（`base` 的 'b' 是十六进制字符 ⇒ 会误匹配子模块）。
    return @(
        [pscustomobject]@{ Id = 'fabric-api';                  Regex = 'fabric-api-fabric-api-[0-9a-f]{8}-\d|^fabric-api-\d'; Hard = $true;  Note = 'fabric.mod.json depends' },
        [pscustomobject]@{ Id = 'trinkets';                    Regex = 'trinkets-';                                Hard = $true;  Note = '替代 Curios；fabric.mod.json depends' },
        [pscustomobject]@{ Id = 'puzzleslib';                  Regex = 'puzzles-lib-';                             Hard = $true;  Note = '事件桥硬依赖；fabric.mod.json depends' },
        [pscustomobject]@{ Id = 'forge-config-api-port';       Regex = 'forge-config-api-port-';                   Hard = $true;  Note = 'Puzzles Lib 的运行前置（dev 期投放）' },
        [pscustomobject]@{ Id = 'cardinal-components-base';    Regex = 'cardinal-components-base-';                Hard = $true;  Note = 'Trinkets 声明硬依赖但未内嵌 ⇒ dev 必须投放' },
        [pscustomobject]@{ Id = 'cardinal-components-entity';  Regex = 'cardinal-components-entity-';              Hard = $true;  Note = '同上' },
        [pscustomobject]@{ Id = 'accessories';                 Regex = 'accessories-';                             Hard = $false; Note = '可选（fabric.mod.json recommends）' },
        [pscustomobject]@{ Id = 'cloth-config';                Regex = 'cloth-config-';                            Hard = $false; Note = 'Accessories 的硬前置（随其投放）' }
    )
}

# ══ 参数 ══════════════════════════════════════════════════════════════════
$Side = 'both'
$Mode = 'verify'
$FromDir = ''
$EnableRcon = $false
$InstallProbe = ''
$RconPort = 25575
$RconPassword = 'astralft'
$ArgList = @($args)

$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    $key = Get-FtArgKey -Token $tok
    if ($key -eq 'side') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --side 的值'; exit $FT_EXIT_ERROR }
        $Side = ([string]$args[$i + 1]).ToLowerInvariant(); $i += 2
    } elseif ($key -eq 'mode') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --mode 的值'; exit $FT_EXIT_ERROR }
        $Mode = ([string]$args[$i + 1]).ToLowerInvariant(); $i += 2
    } elseif ($key -eq 'from') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --from 的值'; exit $FT_EXIT_ERROR }
        $FromDir = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'enablercon') {
        $EnableRcon = $true; $i++
    } elseif ($key -eq 'rconport') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --rcon-port 的值'; exit $FT_EXIT_ERROR }
        $RconPort = [int]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'rconpassword') {
        if ($i + 1 -ge $args.Count) { Write-FtErrorLine '缺少 --rcon-password 的值'; exit $FT_EXIT_ERROR }
        $RconPassword = [string]$args[$i + 1]; $i += 2
    } elseif ($key -eq 'installprobe') {
        # 可选跟一个脚本名；缺省用 event_bridge_probe.js
        $InstallProbe = 'event_bridge_probe.js'
        if ($i + 1 -lt $args.Count -and -not ([string]$args[$i + 1]).StartsWith('-')) {
            $InstallProbe = [string]$args[$i + 1]; $i += 2
        } else {
            $i++
        }
    } elseif ($key -eq 'allowauto') {
        $i++
    } elseif ($key -eq 'h' -or $key -eq 'help') {
        Write-FtLine '用法: ft_env.ps1 [--side client|server|both] [--mode verify|install] [--from <dir>]'
        Write-FtLine '                  [--enable-rcon] [--rcon-port N] [--rcon-password P] [--install-probe [脚本名]]'
        exit $FT_EXIT_PASS
    } else {
        Write-FtErrorLine "未知参数 $tok"; exit $FT_EXIT_ERROR
    }
}

if ($Side -ne 'client' -and $Side -ne 'server' -and $Side -ne 'both') {
    Write-FtErrorLine "非法 --side '$Side'（只接受 client | server | both）"
    exit $FT_EXIT_ERROR
}
if ($Mode -ne 'verify' -and $Mode -ne 'install') {
    Write-FtErrorLine "非法 --mode '$Mode'（只接受 verify | install）"
    exit $FT_EXIT_ERROR
}

$sub = Get-FtSubprojectRoot
$sides = if ($Side -eq 'both') { @('client', 'server') } else { @($Side) }
$loomDir = Join-Path $sub 'build\loom-cache\remapped_working'
if (-not $FromDir) { $FromDir = $loomDir }

$reqs = Get-FtRequirements
$problems = New-Object System.Collections.Generic.List[string]

# ══ 每个 side 逐项检查 ════════════════════════════════════════════════════
foreach ($s in $sides) {
    $sideDir = Get-FtSideDir -Side $s
    $modsDir = Get-FtModsDir -Side $s

    # ① 目录骨架
    if (Test-Path -LiteralPath $sideDir -PathType Container) {
        Write-FtLine ("AP_FAB_ENV_DIR: side={0} ok=true dir={1}" -f $s, $sideDir)
    } else {
        Write-FtLine ("AP_FAB_ENV_DIR: side={0} ok=false dir={1}" -f $s, $sideDir)
        $problems.Add("${s}: 运行目录不存在（先跑一次 pwsh -File scripts/test/fabric/ft_launch.ps1 --side $s 生成）")
    }

    $need = @('config', 'kubejs', 'logs')
    $missing = New-Object System.Collections.Generic.List[string]
    foreach ($d in $need) {
        if (-not (Test-Path -LiteralPath (Join-Path $sideDir $d) -PathType Container)) { $missing.Add($d) }
    }
    $missingTxt = if ($missing.Count -eq 0) { 'none' } else { ($missing -join ',') }
    Write-FtLine ("AP_FAB_ENV_SUBDIRS: side={0} missing={1}" -f $s, $missingTxt)
    foreach ($d in $missing) { $problems.Add("${s}: 缺子目录 $d") }

    if ($s -eq 'server') {
        $eula = Join-Path $sideDir 'eula.txt'
        $eulaOk = $false
        if (Test-Path -LiteralPath $eula -PathType Leaf) {
            $t = Read-FtLogText -Path $eula
            $eulaOk = ($t -match '(?m)^\s*eula\s*=\s*true\s*$')
        }
        Write-FtLine ("AP_FAB_ENV_EULA: ok={0}" -f $eulaOk.ToString().ToLowerInvariant())
        if (-not $eulaOk) { $problems.Add('server: eula.txt 缺失或未置 eula=true（服务端会拒绝启动）') }

        $props = Join-Path $sideDir 'server.properties'
        if (-not (Test-Path -LiteralPath $props -PathType Leaf)) {
            $problems.Add('server: 缺 server.properties（首次启动后自动生成）')
        }
    }

    # ② 前置可得性：loom 缓存（gradle dev-run 的 classpath）与 mods 目录各查一遍
    if (-not (Test-Path -LiteralPath $loomDir -PathType Container)) {
        Write-FtWarn "loom 缓存目录不存在：$loomDir（可能是还没跑过任何 gradle run*/build）"
    }
    $loomFiles = @()
    if (Test-Path -LiteralPath $loomDir -PathType Container) {
        $loomFiles = @(Get-ChildItem -LiteralPath $loomDir -File -Filter '*.jar' | Where-Object { $_.Name -notmatch 'sources' })
    }
    $modFiles = @()
    if (Test-Path -LiteralPath $modsDir -PathType Container) {
        $modFiles = @(Get-ChildItem -LiteralPath $modsDir -File -Filter '*.jar')
    }

    Write-FtLine ("AP_FAB_ENV_SURFACE: side={0} mods_files={1} loom_files={2}" -f $s, $modFiles.Count, $loomFiles.Count)
    if ($modFiles.Count -gt 0) {
        Write-FtLine ("AP_FAB_ENV_MODS_LIST: side={0} names={1}" -f $s, (($modFiles | ForEach-Object { $_.Name }) -join '|'))
    } else {
        Write-FtLine ("AP_FAB_ENV_MODS_LIST: side={0} names=-" -f $s)
    }

    foreach ($r in $reqs) {
        $inLoom = @($loomFiles | Where-Object { $_.Name -match $r.Regex })
        $inMods = @($modFiles | Where-Object { $_.Name -match $r.Regex })
        $present = ($inLoom.Count -gt 0) -or ($inMods.Count -gt 0)
        $source = if ($inMods.Count -gt 0) { 'mods' } elseif ($inLoom.Count -gt 0) { 'loom' } else { 'absent' }
        Write-FtLine ("AP_FAB_ENV_REQ: side={0} id={1} present={2} source={3} mods={4} loom={5}" -f `
                $s, $r.Id, $present.ToString().ToLowerInvariant(), $source, $inMods.Count, $inLoom.Count)
        if (-not $present -and $r.Hard) {
            $problems.Add("${s}: 缺硬前置 $($r.Id)（$($r.Note)）")
        }
    }

    # ③ install 模式：把 loom 缓存里缺失的硬前置拷进 mods
    if ($Mode -eq 'install') {
        if (-not (Test-Path -LiteralPath $modsDir -PathType Container)) {
            [void](New-Item -ItemType Directory -Force -Path $modsDir)
        }
        Write-FtWarn "install 模式：往 $modsDir 拷前置 jar。⚠️ 若随后用 'gradlew :fabric-1.20.1:runServer/runClient' 启动，" +
            '这些模组**已在 classpath 上**，重复投放会被 Fabric Loader 记为重复 mod 而启动失败；' +
            'install 模式只适用于「直接 java 启动 / 生产形态实例」路径。'
        foreach ($r in $reqs) {
            $inMods = @($modFiles | Where-Object { $_.Name -match $r.Regex })
            if ($inMods.Count -gt 0) { continue }
            $cands = @($loomFiles | Where-Object { $_.Name -match $r.Regex })
            if ($cands.Count -eq 0) {
                Write-FtLine ("AP_FAB_ENV_INSTALL: id={0} action=no-source" -f $r.Id)
                continue
            }
            $src = ($cands | Sort-Object -Property LastWriteTimeUtc -Descending | Select-Object -First 1)
            # Loom 缓存里的名字带 `remapped.<group>-<artifact>-<hash>-<ver>.jar` 前缀，拷进去前还原成
            # 可辨识的部署名（去 remapped. 前缀 + 去 hash 段）。
            $deployName = $src.Name -replace '^remapped\.', '' -replace '-c2d2b86c-', '-'
            $dst = Join-Path $modsDir $deployName
            if (Test-Path -LiteralPath $dst -PathType Leaf) {
                Write-FtLine ("AP_FAB_ENV_INSTALL: id={0} action=exists file={1}" -f $r.Id, $deployName)
                continue
            }
            Copy-Item -LiteralPath $src.FullName -Destination $dst -Force
            Write-FtLine ("AP_FAB_ENV_INSTALL: id={0} action=copied file={1}" -f $r.Id, $deployName)
        }
    }

    # ④ KubeJS 读数探针（往日志打标记行，供 ft_assert 断言；**不是**命令注入通道）
    if ($InstallProbe) {
        $probeSrc = Join-Path (Get-FtSelfDir) $InstallProbe
        if (-not (Test-Path -LiteralPath $probeSrc -PathType Leaf)) {
            $problems.Add("找不到探针脚本：$probeSrc")
        } else {
            $target = Join-Path (Get-FtKubejsScriptDir -Side $s) $InstallProbe
            $targetDir = Split-Path -Parent $target
            if (-not (Test-Path -LiteralPath $targetDir -PathType Container)) {
                [void](New-Item -ItemType Directory -Force -Path $targetDir)
            }
            Copy-Item -LiteralPath $probeSrc -Destination $target -Force
            Write-FtLine ("AP_FAB_ENV_PROBE: side={0} installed={1}" -f $s, $target)
            Write-FtWarn 'KubeJS 只在**冷启动**或 `/kubejs reload <type>` 后加载脚本 ⇒ 装完必须重启服务端才生效。'
        }
    }

    # ⑤ RCON（仅服务端；供 ft_inject 默认通道）
    if ($s -eq 'server') {
        $props = Join-Path $sideDir 'server.properties'
        if (Test-Path -LiteralPath $props -PathType Leaf) {
            if ($EnableRcon) {
                Set-FtPropFileKey -Path $props -Pairs @{
                    'enable-rcon'   = 'true'
                    'rcon.port'     = "$RconPort"
                    'rcon.password' = "$RconPassword"
                }
                Write-FtLine ("AP_FAB_ENV_RCON: enabled=true port={0} password_set=true changed=true" -f $RconPort)
                Write-FtWarn 'RCON 只在**服务端下次启动**时生效（server.properties 于启动期读取）。'
            } else {
                $t = Read-FtLogText -Path $props
                $en = if ($t -match '(?m)^\s*enable-rcon\s*=\s*true\s*$') { 'true' } else { 'false' }
                $port = '0'
                $pm = [regex]::Match($t, '(?m)^\s*rcon\.port\s*=\s*(\d+)\s*$')
                if ($pm.Success) { $port = $pm.Groups[1].Value }
                $pwdSet = if ($t -match '(?m)^\s*rcon\.password\s*=\s*(.+?)\s*$') { 'true' } else { 'false' }
                Write-FtLine ("AP_FAB_ENV_RCON: enabled={0} port={1} password_set={2} changed=false" -f $en, $port, $pwdSet)
                if ($en -ne 'true') {
                    Write-FtWarn "RCON 未启用 ⇒ ft_inject 的 rcon 通道不可用；加 --enable-rcon 后重启服务端启用。"
                }
            }
        }
    }
}

# ══ 汇总 ══════════════════════════════════════════════════════════════════
if ($problems.Count -gt 0) {
    foreach ($p in $problems) { Write-FtErrLine "  · $p" }
    Write-FtLine ("AP_FAB_ENV: FAIL (problems={0})" -f $problems.Count)
    Write-FtFail 'ENV' ("环境不满足：{0} 项问题（见上方 · 行）" -f $problems.Count)
    exit $FT_EXIT_FAIL
}

Write-FtLine 'AP_FAB_ENV: OK'
Write-FtOk 'ENV' ("side=$Side mode=$Mode 前置齐备（loom 缓存={0}，mods={1}）" -f $loomDir, 'run/<side>/mods')
exit $FT_EXIT_PASS
