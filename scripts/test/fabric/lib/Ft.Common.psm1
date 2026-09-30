<#
Ft.Common.psm1 — fabric-1.20.1 线测试台的共享原语（自包含，不依赖生产线 lib/）。

## 为什么另起一套（而不是 Import-Module scripts/test/lib/Mt.*.psm1）

生产线共享模块（Mt.Paths / Mt.Phase / Mt.Proc / Mt.Win32）的**全部语义都锚在
`版本 → 子项目` 的三线映射上**（1.21.1 / 1.20.1 / 26.1.2），而 fabric 是**独立的第四条线**
（2026-10-01 起**与另三线同树于 `multi-main`**，单版本）。复用会把「三线批量」的假设带进来，与
scripts/test/TESTING-RULES-OVERVIEW.md §3.1 的「禁用批量编排」闸门口径相反。
本模块只保留两条**逐字同构**的东西：退出码表、`MT_AUTO_DISABLED` 闸门。

## 依据（全部来自实际源码，非推测）

| 事实 | 依据 |
|---|---|
| fabric 子项目目录 `fabric-1.20.1` | `fabric-1.20.1/build.gradle:1-2` |
| dev 运行目录 `fabric-1.20.1/run/{client,server}` | `fabric-1.20.1/build.gradle:172-182`（`runDir 'run/client'` / `'run/server'`，Loom 相对**子项目**解析） |
| datagen 运行目录 `fabric-1.20.1/run/datagen` | `fabric-1.20.1/build.gradle:189-200` |
| 产物 jar 名 `astral_dice-<version>+fabric_1.20.1.jar` | `fabric-1.20.1/gradle.properties`（`mod_version=1.3.2+fabric_1.20.1`）+ `build.gradle:22` |
| 日志路径 `run/<side>/logs/latest.log` | 实测 `fabric-1.20.1/run/{client,server}/logs/latest.log` 存在（log4j 默认 layout） |
| ⚠️ **latest.log 每次冷启动都被轮转**（本台最关键的一条平台事实） | 实测 2026-09-29：启动瞬间 `run/server/logs/latest.log` 被改名为 `2026-09-29-7.log.gz`（时间戳 18:05:55 = 启动时刻）并新建空的 `latest.log`。⇒ **任何「字节偏移游标」在每次启动都失效**，必须改用「文件身份锚点」（见 `Get-FtLogAnchor`） |
| KubeJS 服务端脚本目录 `run/server/kubejs/server_scripts/` | 实测该目录存在且 `event_bridge_probe.js` 在内；日志证明其被执行（`latest.log:222-229`） |
| KubeJS 客户端脚本目录 `run/client/kubejs/client_scripts/` | 实测该目录存在 |
| 事件派发统计行前缀 | `platform/FabricBridges.java:87,99-100`（`"[Astral Dice] 事件派发统计(关服){}"` / `"(开局 600 tick){}"`） |
| 派发统计正文格式 | `platform/event/LoaderBus.java:87-88`（`"\n  [已派发 N 类] A=1 B=2 "` + `"\n  [未派发 M 类] X Y "`） |
| 退出码表 | `scripts/test/lib/Mt.Phase.psm1:29-36` |
| 闸门行为（stderr 首行 `MT_AUTO_DISABLED:` / 退出码 2 / `--allow-auto` / `MT_ALLOW_AUTO=1`） | `scripts/test/mt.ps1`（`$AllowAuto` 段与闸门段）+ `TESTING-RULES-OVERVIEW.md` §3.1、§10 |
| 内嵌库自检口径 | `build.gradle:378-407`（`META-INF/jars/` 内有 `starengine_lib-fabric-1.20.1-*.jar`） |
| RCON 键存在 | 实测 `fabric-1.20.1/run/server/server.properties` 含 `enable-rcon=false` / `rcon.port=25575` / `rcon.password=`（vanilla 专用服务端原生特性） |
| KubeJS 服务端命令 API | `javap` 实查 `dev.latvian.mods.kubejs.core.MinecraftServerKJS`：`kjs$runCommand(String) -> int`、`kjs$runCommandSilent(String) -> int`、`kjs$getLevel(ResourceLocation)`（JS 侧去掉 `kjs$` 前缀 ⇒ `server.runCommand(...)`） |
#>

# ── 退出码（与 lib/Mt.Phase.psm1:29-36 逐值一致）──────────────────────────
$FT_EXIT_PASS    = 0
$FT_EXIT_FAIL    = 1
$FT_EXIT_ERROR   = 2
$FT_EXIT_BLOCKED = 11
$FT_EXIT_TIMEOUT = 12

# ── 输出原语 ──────────────────────────────────────────────────────────────
function Initialize-FtConsole {
    <#
    .SYNOPSIS
        统一控制台 I/O 编码为 UTF-8（无 BOM）。
    .NOTES
        与 Mt.Phase 的 Initialize-MtConsole 同因：Windows 默认码页（本机 936/GBK）会把中文
        写成 GBK 字节，而调用方按 UTF-8 解码 ⇒ 乱码。
    #>
    [CmdletBinding()]
    param()

    $utf8 = [System.Text.UTF8Encoding]::new($false)
    try { [Console]::OutputEncoding = $utf8 } catch { }
    try { [Console]::InputEncoding = $utf8 } catch { }
    try { $global:OutputEncoding = $utf8 } catch { }
}

function Write-FtLine {
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    [Console]::Out.Write($Text + "`n")
}

function Write-FtErrLine {
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    [Console]::Error.Write($Text + "`n")
}

function Write-FtOk {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Name, [string]$Detail)
    $suffix = ''
    if ($PSBoundParameters.ContainsKey('Detail') -and $Detail) { $suffix = " — $Detail" }
    Write-FtLine ("MT_FAB_{0}: OK — {1}" -f $Name, $(if ($suffix) { $Detail } else { '' }))
}

function Write-FtFail {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][string]$Reason)
    Write-FtErrLine ("MT_FAB_{0}: FAIL — {1}" -f $Name, $Reason)
}

function Write-FtError {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][string]$Reason)
    Write-FtErrLine ("MT_FAB_{0}: ERROR — {1}" -f $Name, $Reason)
}

function Write-FtBlocked {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][string]$Reason)
    Write-FtErrLine ("MT_FAB_{0}: BLOCKED — {1}" -f $Name, $Reason)
}

function Write-FtInfo {
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    Write-FtLine "MT_FAB_INFO: $Text"
}

function Write-FtWarn {
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    Write-FtErrLine "MT_FAB_WARN: $Text"
}

function Write-FtErrorLine {
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
    Write-FtErrLine "MT_ERROR: $Text"
}

# ── 参数归一化（与 mt_inject.ps1 的约定一致：去前导 '-' → 删全部 '-' → 小写）──
function Get-FtArgKey {
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Token)
    return ([string]$Token).TrimStart('-').Replace('-', '').ToLowerInvariant()
}

function Invoke-FtChildProcess {
    <#
    .SYNOPSIS
        以子进程方式运行一个 pwsh 脚本，**按字节透传**其 stdout/stderr，并**只返回退出码**。

    .DESCRIPTION
        为什么不能用 `& pwsh -File …`（两者都是实测踩出来的）：
          ① `&` 会把子进程的 stdout **收进当前管道** ⇒ 用 `$rc = Invoke-Xxx` 接收退出码时，
             返回值会变成「一整串输出行 + 退出码」的数组 —— 实测 ft.ps1 打印过
             `AP_FAB_PHASE: build rc=System.Object[]`；
          ② PowerShell 对原生命令的 `>` / `2>` 重定向会按 `$OutputEncoding` **二次解码再编码**，
             中文直接变乱码（实测：子进程写出的 GBK stderr 被当 UTF-8 解码，文件里成了 `涓枃` 之类）。
          ③ **`Start-Process -Wait` 会等整棵进程树** ⇒ 对 `ft_launch.ps1`（孵化游戏后立刻返回）
             会把调用方挂到**游戏退出**为止（实测：驱动卡在 launch 步 6 分钟不返回，而子脚本早已打印 OK）。
             这正是 `TESTING-RULES-OVERVIEW.md` §13 记过的坑。故改用 `-PassThru` + 只等该进程本身。
        本实现改为 Start-Process + 临时文件 + 原始字节转发，三个问题一并消除。

    .PARAMETER ScriptPath
        子脚本绝对路径。
    .PARAMETER ScriptArgs
        透传给子脚本的参数（内含空白的项会自动加引号）。
    .OUTPUTS
        [int] 子进程退出码。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$ScriptPath,
        [string[]]$ScriptArgs = @()
    )

    if (-not (Test-Path -LiteralPath $ScriptPath -PathType Leaf)) {
        Write-FtErrorLine "子脚本不存在：$ScriptPath"
        return $FT_EXIT_ERROR
    }

    $tempDir = Join-Path (Get-FtRepoRoot) 'temp'
    if (-not (Test-Path -LiteralPath $tempDir -PathType Container)) {
        [void](New-Item -ItemType Directory -Force -Path $tempDir)
    }
    $stamp = [guid]::NewGuid().ToString('N').Substring(0, 10)
    $outFile = Join-Path $tempDir "ft_child_${stamp}.out.txt"
    $errFile = Join-Path $tempDir "ft_child_${stamp}.err.txt"

    $quoted = @()
    foreach ($a in @($ScriptArgs)) {
        $s = [string]$a
        if ($s -match '[\s"]') { $s = '"' + ($s -replace '"', '\"') + '"' }
        $quoted += $s
    }
    $argStr = (@('-NoProfile', '-File', $ScriptPath) + $quoted) -join ' '

    $psExe = (Get-Process -Id $PID).Path

    # ⚠️ 这里**不能**用 `-Wait`（实测缺陷，见 README §7.3/D6）：
    #    `Start-Process -Wait` 会等**整棵进程树**，而 `ft_launch.ps1` 是「后台孵化 + 立刻返回」的语义
    #    —— 它把游戏进程 detached 地起出去后自己就退出了。用 `-Wait` 会让调用方（`ft.ps1 --phase launch`）
    #    一直挂到**游戏退出**为止（实测：批处理驱动卡在 launch 步 6 分钟不返回，而子脚本早已打印 OK）。
    #    这正是 `TESTING-RULES-OVERVIEW.md` §13 记过的坑：「`-Wait` 会等整棵进程树」。
    #    正确做法 = `-PassThru` + 只对**该进程本身** `WaitForExit()`（不递归后代）。
    $proc = Start-Process -FilePath $psExe -ArgumentList $argStr -NoNewWindow -PassThru `
        -RedirectStandardOutput $outFile -RedirectStandardError $errFile
    $proc.WaitForExit()
    $childRc = [int]$proc.ExitCode
    # 先释放 Process 句柄（它会持有重定向文件），再回读 —— 见下方 ReadAllBytes 的说明。
    try { $proc.Dispose() } catch { }

    # 原始字节转发（先刷我们自己的 Console.Out 缓冲，避免与子进程输出交错错位）
    try { [Console]::Out.Flush() } catch { }
    try { [Console]::Error.Flush() } catch { }
    foreach ($pair in @(@{ f = $outFile; s = 'out' }, @{ f = $errFile; s = 'err' })) {
        if (-not (Test-Path -LiteralPath $pair.f -PathType Leaf)) { continue }
        # ⚠️ 不能用 [System.IO.File]::ReadAllBytes（实测缺陷，见 README §7.3/D7）：
        #    它与 D2 是**同一个共享模式陷阱** —— ReadAllBytes 的共享模式是 FileShare.Read，
        #    而重定向文件在子进程退出后仍被本方（父进程）的写句柄持有一小段时间 ⇒ 抛
        #    `The process cannot access the file … because it is being used by another process.`
        #    （把 `-Wait` 换成 `WaitForExit()` 之后这个竞态才暴露：`-Wait` 顺带等了整棵树，恰好掩盖了它。）
        #    改用共享读写打开（与日志读取同一条实现）。
        $bytes = Read-FtFileBytesShared -Path $pair.f
        if ($null -eq $bytes -or $bytes.Length -eq 0) { continue }
        if ($pair.s -eq 'out') {
            $stream = [Console]::OpenStandardOutput()
        } else {
            $stream = [Console]::OpenStandardError()
        }
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush()
    }

    Remove-Item -LiteralPath $outFile -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $errFile -Force -ErrorAction SilentlyContinue
    return $childRc
}

# ── 路径派生（一律绝对路径，由 $PSScriptRoot 派生；依据 AGENTS「工作树与路径纪律」）──
function Get-FtRepoRoot {
    <#
    .SYNOPSIS
        仓库根 = <root>/scripts/test/fabric/lib 往上四级。
    .NOTES
        刻意不做 cwd 探测：调用方 cwd 可能是另一个 worktree（AGENTS 明文事故）。
    #>
    [CmdletBinding()]
    param()
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\..'))
}

function Get-FtSubprojectRoot {
    [CmdletBinding()]
    param()
    return (Join-Path (Get-FtRepoRoot) 'fabric-1.20.1')
}

function Get-FtSelfDir {
    <#
    .SYNOPSIS
        本测试台目录 scripts/test/fabric。
    #>
    [CmdletBinding()]
    param()
    return (Join-Path (Get-FtRepoRoot) 'scripts\test\fabric')
}

function Get-FtRunRoot {
    [CmdletBinding()]
    param()
    return (Join-Path (Get-FtSubprojectRoot) 'run')
}

function Assert-FtSide {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Side)
    if ($Side -ne 'client' -and $Side -ne 'server') {
        Write-FtErrorLine "非法 --side '$Side'（只接受 client | server）"
        exit $FT_EXIT_ERROR
    }
    return $Side
}

function Get-FtSideDir {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Side)
    [void](Assert-FtSide -Side $Side)
    return (Join-Path (Get-FtRunRoot) $Side)
}

function Get-FtLogPath {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Side)
    return (Join-Path (Get-FtSideDir -Side $Side) 'logs\latest.log')
}

function Get-FtModsDir {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Side)
    return (Join-Path (Get-FtSideDir -Side $Side) 'mods')
}

function Get-FtKubejsScriptDir {
    <#
    .SYNOPSIS
        KubeJS 脚本目录：server → server_scripts，client → client_scripts。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Side)
    [void](Assert-FtSide -Side $Side)
    $sub = if ($Side -eq 'server') { 'server_scripts' } else { 'client_scripts' }
    return (Join-Path (Get-FtSideDir -Side $Side) "kubejs\$sub")
}

function Get-FtProductJar {
    <#
    .SYNOPSIS
        fabric 子项目 build/libs 下的正式产物（取 mtime 最新，排除 -sources/-dev/-slim）。
    .NOTES
        依据 build.gradle:21-23 + gradle.properties 的 mod_version；后缀 `+fabric_1.20.1` 是本线专属。
    #>
    [CmdletBinding()]
    param()

    $dir = Join-Path (Get-FtSubprojectRoot) 'build\libs'
    if (-not (Test-Path -LiteralPath $dir -PathType Container)) { return '' }
    $jars = @(Get-ChildItem -LiteralPath $dir -File -Filter 'astral_dice-*.jar' |
        Where-Object { $_.Name -notmatch '-sources|-dev|-slim' })
    if ($jars.Count -eq 0) { return '' }
    return ($jars | Sort-Object -Property LastWriteTimeUtc -Descending | Select-Object -First 1).FullName
}

# ── jar 开包统计（ft_build 与 ft_case 的 `jar` 断言共用同一实现）──────────
function Get-FtJarStats {
    <#
    .SYNOPSIS
        开包统计产物 jar 的资源条目数（不依赖 unzip 外部程序）。
    .DESCRIPTION
        返回对象字段：
          Models       assets/astral_dice/models/item/ 下的 .json 条目数
          Recipes      data/astral_dice/recipes/ 下的 .json 条目数
          Bountiful    data/bountiful/ 前缀下的条目数（**必须为 0**：bountiful 不是本线前置）
          Trinkets     data/trinkets/ 前缀下的条目数（替代 Forge 侧 Curios 的饰品数据）
          EmbedJars    META-INF/jars/ 下的内嵌 jar（Loom 的 include ⇒ JarJar）
          Entries      总条目数
        SourceCounts 为「磁盘 src/ 侧应存在的条目数」，供调用方做「产物 vs 源」比对 ——
        这正是本项目踩过的真实坑（`sourceSets` 漏配 ⇒ `src/generated/resources` 不进产物），
        只看 jar 自身数量无法发现该缺陷，必须与磁盘源数量对照。
    .NOTES
        依据：
          · `META-INF/jars/` 内嵌口径（build.gradle:85-89 的 include + :378-407 的自检）
          · `data/trinkets/**` 由 src/main/resources/data/trinkets 提供（实测目录树）
          · `bountiful` 无任何数据（实测 `find src -path "*bountiful*"` 为空）
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$JarPath)

    if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
        throw "jar 不存在: $JarPath"
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
    $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $models = 0; $recipes = 0; $bountiful = 0; $trinkets = 0; $entries = 0
        $embed = New-Object System.Collections.Generic.List[string]
        foreach ($e in $zip.Entries) {
            $entries++
            $n = $e.FullName
            if ($n.StartsWith('assets/astral_dice/models/item/') -and $n.EndsWith('.json')) { $models++ }
            if ($n.StartsWith('data/astral_dice/recipes/') -and $n.EndsWith('.json')) { $recipes++ }
            if ($n.StartsWith('data/bountiful/')) { $bountiful++ }
            if ($n.StartsWith('data/trinkets/')) { $trinkets++ }
            if ($n.StartsWith('META-INF/jars/') -and $n.EndsWith('.jar')) { $embed.Add($n.Substring('META-INF/jars/'.Length)) }
        }
    } finally {
        $zip.Dispose()
    }

    # 磁盘源数量（对照件）
    $sub = Get-FtSubprojectRoot
    $srcModelsDir = Join-Path $sub 'src\generated\resources\assets\astral_dice\models\item'
    $srcRecipesDir = Join-Path $sub 'src\generated\resources\data\astral_dice\recipes'
    $srcTrinketsDir = Join-Path $sub 'src\main\resources\data\trinkets'
    $srcBountifulDir = Join-Path $sub 'src\main\resources\data\bountiful'

    $srcModels = 0
    if (Test-Path -LiteralPath $srcModelsDir -PathType Container) {
        $srcModels = @(Get-ChildItem -LiteralPath $srcModelsDir -File -Filter '*.json').Count
    }
    $srcRecipes = 0
    if (Test-Path -LiteralPath $srcRecipesDir -PathType Container) {
        $srcRecipes = @(Get-ChildItem -LiteralPath $srcRecipesDir -File -Filter '*.json').Count
    }
    $srcTrinkets = 0
    if (Test-Path -LiteralPath $srcTrinketsDir -PathType Container) {
        $srcTrinkets = @(Get-ChildItem -LiteralPath $srcTrinketsDir -Recurse -File -Filter '*.json').Count
    }
    $srcBountiful = 0
    if (Test-Path -LiteralPath $srcBountifulDir -PathType Container) {
        $srcBountiful = @(Get-ChildItem -LiteralPath $srcBountifulDir -Recurse -File).Count
    }

    $fi = Get-Item -LiteralPath $JarPath
    return [pscustomobject]@{
        JarPath        = $fi.FullName
        JarName        = $fi.Name
        Bytes          = $fi.Length
        Entries        = $entries
        Models         = $models
        Recipes        = $recipes
        Bountiful      = $bountiful
        Trinkets       = $trinkets
        EmbedJars      = @($embed)
        SrcModels      = $srcModels
        SrcRecipes     = $srcRecipes
        SrcTrinkets    = $srcTrinkets
        SrcBountiful   = $srcBountiful
    }
}

# ── 日志读取与断言原语 ─────────────────────────────────────────────────────
function Read-FtFileBytesShared {
    <#
    .SYNOPSIS
        以**共享读写**方式整读一个文件（文件被别的进程独占写入时也能读）。
    .DESCRIPTION
        ⚠️ 不能直接用 `[System.IO.File]::ReadAllBytes`：它内部的共享模式是 `FileShare.Read`，
        而 Windows 的共享检查是**对称**的 —— 我们只允许别人「读」，就无法与仍持有**写**句柄的
        游戏进程共存 ⇒ 抛
          `The process cannot access the file '…latest.log' because it is being used by another process.`
        实测 2026-09-29：服务端运行期间 `ReadAllBytes(latest.log)` **每次**都失败。
        ⇒ 本台「服务端还活着时读日志」这条主干（ft_assert / ft_dispatchreport / ft_launch 轮询 /
        ft_inject 回显校验）在旧实现下**根本不可用**（旧 ft_launch 的读分支因判定条件恒不成立
        而从未真正执行，所以这个缺陷一直被掩盖）。

        这里显式用 `FileShare.ReadWrite`：既允许对方继续持有写句柄，也允许我们自己读。
    .OUTPUTS
        [byte[]]（失败时为空数组，不抛）
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return , ([byte[]]@()) }
    $fs = $null
    $buf = [byte[]]@()
    try {
        $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open,
                                     [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        $len = [int64]$fs.Length
        if ($len -gt 0) {
            $buf = New-Object byte[] ([int]$len)
            $read = 0
            while ($read -lt $len) {
                $k = $fs.Read($buf, $read, [int]($len - $read))
                if ($k -le 0) { break }
                $read += $k
            }
            if ($read -le 0) { $buf = [byte[]]@() }
            elseif ($read -lt $len) { $buf = $buf[0..($read - 1)] }
        }
    } catch {
        return , ([byte[]]@())
    } finally {
        if ($null -ne $fs) { $fs.Dispose() }
    }
    return , $buf
}

function Read-FtLogText {
    <#
    .SYNOPSIS
        以 UTF-8（非法字节替换）整读日志文件；文件不存在/读不到返回 ''。
    .NOTES
        走 Read-FtFileBytesShared（共享读写）—— 见该函数的说明：游戏运行时日志被独占，
        File.ReadAllBytes 读不了。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
    $bytes = Read-FtFileBytesShared -Path $Path
    if ($null -eq $bytes -or $bytes.Length -eq 0) { return '' }
    $enc = [System.Text.UTF8Encoding]::new($false, $false)
    return $enc.GetString($bytes)
}

function Get-FtOffsetsPath {
    [CmdletBinding()]
    param()
    return (Join-Path (Get-FtSelfDir) '.ft_offsets.json')
}

function Get-FtHeadHash {
    <#
    .SYNOPSIS
        文件头部 N 字节的 SHA1（取前 16 个十六进制字符）。
    .NOTES
        共享打开（FileShare.ReadWrite）：日志正被游戏进程写入时也要能读。
        .NET 的 File.ReadAllBytes 用的是 FileShare.Read，对「别人仍持有写句柄」不友好。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path, [int]$HeadBytes = 4096)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
    $buf = [byte[]]@()
    $fs = $null
    try {
        $fs = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open,
                                     [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        $n = [Math]::Min([int64]$HeadBytes, $fs.Length)
        if ($n -gt 0) {
            $buf = New-Object byte[] ([int]$n)
            $read = 0
            while ($read -lt $n) {
                $k = $fs.Read($buf, $read, [int]($n - $read))
                if ($k -le 0) { break }
                $read += $k
            }
            if ($read -le 0) {
                $buf = [byte[]]@()
            } elseif ($read -lt $n) {
                $buf = $buf[0..($read - 1)]
            }
        }
    } catch {
        return ''
    } finally {
        if ($null -ne $fs) { $fs.Dispose() }
    }
    $sha = [System.Security.Cryptography.SHA1]::Create()
    try {
        $hash = $sha.ComputeHash($buf)
        return ([BitConverter]::ToString($hash)).Replace('-', '').Substring(0, 16)
    } finally {
        $sha.Dispose()
    }
}

function Get-FtLogAnchor {
    <#
    .SYNOPSIS
        日志文件**身份锚点**：创建时间(UTC ticks) + 长度 + 头部指纹。
    .DESCRIPTION
        ⚠️ 为什么必须记「身份」而不是「字节位置」（本台最大的一个真实缺陷的修法）：
        Minecraft 的 log4j 配置带 OnStartupTriggeringPolicy —— **每一次冷启动**都会把
        `latest.log` 改名成 `YYYY-MM-DD-N.log.gz` 再**新建**一个空的 `latest.log`。
        于是「用启动前的字节长度当游标」在**每一次启动**都失效：

          · 旧长度(~20 KiB) > 新文件长出该长度之前的长度 ⇒ 旧实现 `len -gt preLen` 永不成立
            ⇒ `ft_launch` **每次启动都超时**（实测 2026-09-29：服务端 9 秒就打出 `Done (…)`，
              但 `ft_launch` 干等 240 秒后报 TIMEOUT）；
          · 新文件长过旧长度之后，那个偏移量落进**新文件的中间** ⇒ 「窗口」既漏掉启动行、
            又混进无关行。旧实现只在「长度回退」时报 WARN，这种情况**静默**给出错误窗口
            —— 正是「假绿 / 假 FAIL」的来源。

        判据改为：只要 (创建时间, 头部指纹) 与锚点不一致，就认定**换了文件** ⇒ 窗口 = 整个新文件；
        只有身份一致时才按长度做前向切片。这样「旋转」与「同文件追加」两种情况都被正确区分。
    .OUTPUTS
        [pscustomobject]@{ Exists; Length; CreationTicks; HeadHash }
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    $o = [pscustomobject]@{
        Exists        = $false
        Length        = [int64]0
        CreationTicks = [int64]0
        HeadHash      = ''
    }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $o }
    try {
        $fi = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
        $o.Exists = $true
        $o.Length = [int64]$fi.Length
        $o.CreationTicks = [int64]$fi.CreationTimeUtc.Ticks
        $o.HeadHash = Get-FtHeadHash -Path $Path
    } catch {
        # 打不开/瞬时不可见 ⇒ 保持 Exists=false，调用方按「无锚点」退化处理（不抛）
    }
    return $o
}

function Test-FtAnchorReplaced {
    <#
    .SYNOPSIS
        锚点是否已被轮转/替换（创建时间或头部指纹变化）。
    .NOTES
        Length 回退也算（有些轮转实现是「原地截断」而非「改名+新建」，那时创建时间不变，
        但长度回退 + 头部指纹变化能兜住）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][object]$Anchor,
        [Parameter(Mandatory)][object]$Current
    )
    if (-not $Current.Exists) { return $true }
    if ($Current.CreationTicks -ne $Anchor.CreationTicks) { return $true }
    if ($Current.HeadHash -ne $Anchor.HeadHash) { return $true }
    if ($Current.Length -lt $Anchor.Length) { return $true }
    return $false
}

function Get-FtLogWindowFromAnchor {
    <#
    .SYNOPSIS
        以锚点为起点返回「之后新写入」的日志文本。
    .DESCRIPTION
         · 锚点缺失 / 起点为 0 / 身份已变（轮转） ⇒ **整文件**（此时整文件本身就是新一轮内容）
         · 身份未变                                  ⇒ UTF-8 字节前向切片
        ⚠️ 与旧实现的关键差别：轮转时返回整文件而不是「退化为整文件 + 静默算错偏移」。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [AllowNull()][object]$Anchor,
        [string]$Label = '窗口'
    )

    $whole = Read-FtLogText -Path $Path
    if ($null -eq $Anchor) { return $whole }
    if ([int64]$Anchor.Length -le 0) { return $whole }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }

    $cur = Get-FtLogAnchor -Path $Path
    if (-not $cur.Exists) { return '' }

    if (Test-FtAnchorReplaced -Anchor $Anchor -Current $cur) {
        # 启动期轮转是**预期**行为（latest.log 每次冷启动都重建）⇒ 只提示一次，不刷屏。
        if (-not $script:FtAnchorNotice) { $script:FtAnchorNotice = @{} }
        if (-not $script:FtAnchorNotice.ContainsKey($Label)) {
            $script:FtAnchorNotice[$Label] = $true
            Write-FtInfo ('日志' + $Label + ' 已轮转（latest.log 每次冷启动都会重建）⇒ 本轮按【整文件】判读（同一条只提示一次）')
        }
        return $whole
    }
    if ($cur.Length -le [int64]$Anchor.Length) { return '' }

    $bytes = Read-FtFileBytesShared -Path $Path
    $off = [int][int64]$Anchor.Length
    if ($null -eq $bytes -or $off -ge $bytes.Length) { return '' }
    $enc = [System.Text.UTF8Encoding]::new($false, $false)
    return $enc.GetString($bytes, $off, $bytes.Length - $off)
}

function Save-FtSnapshot {
    <#
    .SYNOPSIS
        记录 <side> 的日志窗口起点（**身份锚点**）。窗口名 case / launch。
    .OUTPUTS
        [int64] 起点字节长度（仅为兼容调用方的 `cursor=` 打印；判据本身是锚点对象）
    .NOTES
        存储格式（`.ft_offsets.json`）：`"<side>_<window>"` → `{ Length; CreationTicks; HeadHash }`。
        ⚠️ 旧格式是裸 int64（纯字节长度）。读到旧格式时 `Get-FtLogWindow` 会 WARN 并退化为整文件，
        不静默沿用错误偏移 —— 换台机器/旧快照不会给出假窗口。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Side, [Parameter(Mandatory)][string]$Window)

    $log = Get-FtLogPath -Side $Side
    $anchor = Get-FtLogAnchor -Path $log

    $state = @{}
    $p = Get-FtOffsetsPath
    if (Test-Path -LiteralPath $p -PathType Leaf) {
        try {
            $raw = Get-Content -LiteralPath $p -Raw -Encoding UTF8 | ConvertFrom-Json -AsHashtable
            if ($raw -is [hashtable]) { $state = $raw }
        } catch { $state = @{} }
    }
    $state["${Side}_${Window}"] = @{
        Length        = [int64]$anchor.Length
        CreationTicks = [int64]$anchor.CreationTicks
        HeadHash      = [string]$anchor.HeadHash
    }
    $state["${Side}_${Window}_ts"] = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    ($state | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $p -Encoding utf8
    return [int64]$anchor.Length
}

function Get-FtLogWindow {
    <#
    .SYNOPSIS
        按窗口返回日志文本。
    .PARAMETER Window
        whole   整文件
        case    自最近一次 `snapshot --window case` 起（默认）
        launch  自最近一次 `snapshot --window launch` 起
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Side,
        [string]$Window = 'case'
    )

    $log = Get-FtLogPath -Side $Side
    $whole = Read-FtLogText -Path $log
    if ($Window -eq 'whole') { return $whole }

    $p = Get-FtOffsetsPath
    if (-not (Test-Path -LiteralPath $p -PathType Leaf)) {
        Write-FtWarn "无快照（$p 不存在）⇒ 窗口 '$Window' 退化为整文件读取"
        return $whole
    }
    $state = @{}
    try {
        $raw = Get-Content -LiteralPath $p -Raw -Encoding UTF8 | ConvertFrom-Json -AsHashtable
        if ($raw -is [hashtable]) { $state = $raw }
    } catch {
        Write-FtWarn "快照文件解析失败（$p）⇒ 退化为整文件读取"
        return $whole
    }
    $key = "${Side}_${Window}"
    if (-not $state.ContainsKey($key)) {
        Write-FtWarn "无 '$key' 快照 ⇒ 窗口 '$Window' 退化为整文件读取"
        return $whole
    }
    $anchorRaw = $state[$key]
    if ($anchorRaw -isnot [hashtable]) {
        Write-FtWarn "快照 '$key' 是旧格式（裸字节长度）⇒ 窗口 '$Window' 退化为整文件读取；请重跑一次 snapshot 刷新为身份锚点"
        return $whole
    }
    $anchor = [pscustomobject]@{
        Length        = [int64]$anchorRaw['Length']
        CreationTicks = [int64]$anchorRaw['CreationTicks']
        HeadHash      = [string]$anchorRaw['HeadHash']
    }
    return (Get-FtLogWindowFromAnchor -Path $log -Anchor $anchor -Label "窗口 '$key'")
}

function Test-FtLogPattern {
    <#
    .SYNOPSIS
        在给定文本上做正则计数。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string]$Text,
        [Parameter(Mandatory)][string]$Pattern
    )
    if ([string]::IsNullOrEmpty($Text)) { return 0 }
    try {
        return ([regex]::Matches($Text, $Pattern)).Count
    } catch {
        throw "非法正则 '$Pattern': $($_.Exception.Message)"
    }
}

# ── 批量编排闸门（2026-09-27 用户裁决；TESTING-RULES-OVERVIEW.md §3.1）──────
function Get-FtAllowAuto {
    <#
    .SYNOPSIS
        是否显式放行批量编排：--allow-auto 或 MT_ALLOW_AUTO=1 / true / yes / on。
    #>
    [CmdletBinding()]
    param([string[]]$ArgList = @())

    $allow = $false
    if ($env:MT_ALLOW_AUTO -and $env:MT_ALLOW_AUTO -match '^(1|true|yes|on)$') { $allow = $true }
    foreach ($a in @($ArgList)) {
        if ([string]$a -eq '--allow-auto') { $allow = $true }
    }
    return $allow
}

function Assert-FtAutoGate {
    <#
    .SYNOPSIS
        拦截「无人干预地自动跑完一批」的调用；拦截时 stderr 首行 `MT_AUTO_DISABLED:` 并 exit 2。
    .PARAMETER What
        被拦下的能力描述（写进首行）。
    .PARAMETER ArgList
        原始参数清单（用于识别 --allow-auto）。
    .NOTES
        fabric 侧的拦截清单（与生产线同构，逐项对应 TESTING-RULES-OVERVIEW.md §3.1「被闸门拦下的能力」）：
          ① ft.ps1 无 --phase（全流程：build→env→launch→case→report 顺序自动跑完）
          ② ft.ps1 --phase case 未指定 --case（走目录，自动跑完该线全部用例）
          ③ ft.ps1 --phase report（把自动跑出来的结果收集成报告）
          ④ ft_case.ps1 run / run-dir 未指定 --case（整目录批量）
        明确 N/A 的两项（fabric 单版本，不存在这两个维度）：
          · 「三线顺序（--phase 无 --version）」—— fabric 只有一条线；
          · 「watchdog -Action stop」—— 本台不提供 watchdog。若将来新增，其 -Action stop 必须同样受闸门约束。
        放行清单：build / env / launch / stop、单条 `--phase case --case <X>`、`ft_case.ps1 run --case <X>`、
                  `ft_case.ps1 validate`，以及手动读数类 ft_inject / ft_assert / ft_dispatchreport。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$What,
        [string[]]$ArgList = @()
    )

    if (Get-FtAllowAuto -ArgList $ArgList) { return }

    Write-FtErrLine ("MT_AUTO_DISABLED: {0} —— 已被禁用。" -f $What)
    Write-FtErrLine '  当前规则：「纯手动下达命令」——逐条注入、逐条看读数、逐步决策（2026-09-27 用户裁决）。'
    Write-FtErrLine '  依据：scripts/test/TESTING-RULES-OVERVIEW.md §3.1 + §10'
    Write-FtErrLine '  放行：ft_build / ft_env / ft_launch / ft_stop（把运行环境弄起来/收停所需的基础设施）'
    Write-FtErrLine '  单条用例：pwsh -File scripts/test/fabric/ft_case.ps1 run --case scripts/test/fabric/cases/<CASE>.json'
    Write-FtErrLine '  手动注入：pwsh -File scripts/test/fabric/ft_inject.ps1 cmd --command "/say hi"'
    Write-FtErrLine '  手动读数：pwsh -File scripts/test/fabric/ft_assert.ps1 log --pattern "<正则>"'
    Write-FtErrLine '  临时放行：追加 --allow-auto（或设 MT_ALLOW_AUTO=1）'
    exit $FT_EXIT_ERROR
}

Export-ModuleMember -Function @(
    'Initialize-FtConsole',
    'Write-FtLine', 'Write-FtErrLine',
    'Write-FtOk', 'Write-FtFail', 'Write-FtError', 'Write-FtBlocked',
    'Write-FtInfo', 'Write-FtWarn', 'Write-FtErrorLine',
    'Get-FtArgKey', 'Invoke-FtChildProcess',
    'Get-FtRepoRoot', 'Get-FtSubprojectRoot', 'Get-FtSelfDir', 'Get-FtRunRoot',
    'Assert-FtSide', 'Get-FtSideDir', 'Get-FtLogPath', 'Get-FtModsDir', 'Get-FtKubejsScriptDir',
    'Get-FtProductJar', 'Get-FtJarStats',
    'Read-FtLogText', 'Read-FtFileBytesShared', 'Get-FtOffsetsPath', 'Save-FtSnapshot', 'Get-FtLogWindow', 'Test-FtLogPattern',
    'Get-FtHeadHash', 'Get-FtLogAnchor', 'Test-FtAnchorReplaced', 'Get-FtLogWindowFromAnchor',
    'Get-FtAllowAuto', 'Assert-FtAutoGate'
) -Variable @(
    'FT_EXIT_PASS', 'FT_EXIT_FAIL', 'FT_EXIT_ERROR', 'FT_EXIT_BLOCKED', 'FT_EXIT_TIMEOUT'
)
