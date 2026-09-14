#Requires -Version 7.0
<#
.SYNOPSIS
    mt_env — 测试环境装配（阶段 E）。

.DESCRIPTION
    职责（只做环境的建立与拆除，不做任何功能断言）:
      mods    安装兼容模组（1.21.1 Sodium/Iris/ModernFix；1.20.1 校验生产环境渲染栈）
      world   重建 testworld（超平坦/创造/允许命令），支持种子快恢复
      kill    按版本精确停止游戏进程（不误杀无关 java 进程）

.EXAMPLE
    pwsh -File scripts/test/mt_env.ps1 mods  --version 1.21.1
    pwsh -File scripts/test/mt_env.ps1 world --version 1.20.1 --seed
    pwsh -File scripts/test/mt_env.ps1 kill  --version 1.21.1

.NOTES
    对应源文件（迁移前）：scripts/test/mt_env.py。

    两处**有意偏差**（都不改变只读判定，只影响取证文件）:
      1. 生成世界时的 runServer 输出：python 丢弃（DEVNULL），此处落到
         `run/<版本>/mt_server_gen.log`(+`.err`) —— 保留现场比丢弃更有用；
      2. gradle 入口：python 走 `bash gradlew`，此处走 `cmd /c gradlew.bat`
         （Windows-only 工具链，不再依赖 git-bash）。

   另修掉两件**原实现里的真 BUG**（python 版同样有，已在对应代码处写明证据与症状；
   不修的话 `world` 阶段在本机**永远失败**，世界生成不出来）:
      1. 移出的客户端模组备份名仍以 `.jar` 结尾 → FML 照样扫描到 → 服务端秒崩；
      2. 「崩溃报告」判定用了目录里的**历史残留**（`any(glob("*.txt"))`）→ 旧报告
         会让世界生成直接放弃；改为只认**本次新增**的崩溃报告。
#>

$ErrorActionPreference = 'Stop'

$script:LibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:LibDir 'Mt.Phase.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Paths.psm1')
Import-Module (Join-Path $script:LibDir 'Mt.Proc.psm1')

Initialize-MtConsole

# ── 兼容模组版本（与 AGENTS「测试环境」表一一对应）────────────────────────
$script:NeoForgeMods = @(
    @{ Pattern = '*sodium-neoforge*0.8.13*.jar'; Target = 'sodium-neoforge-0.8.13+mc1.21.1.jar' }
    @{ Pattern = '*iris-neoforge*1.8.14*.jar'; Target = 'iris-neoforge-1.8.14-beta.1+mc1.21.1.jar' }
    @{ Pattern = '*modernfix-neoforge*5.27.24*.jar'; Target = 'modernfix-neoforge-5.27.24+mc1.21.1.jar' }
)

$script:TAG_BYTE = 1
$script:TAG_SHORT = 2
$script:TAG_INT = 3
$script:TAG_LONG = 4
$script:TAG_FLOAT = 5
$script:TAG_DOUBLE = 6
$script:TAG_BYTE_ARRAY = 7
$script:TAG_STRING = 8
$script:TAG_LIST = 9
$script:TAG_COMPOUND = 10
$script:TAG_INT_ARRAY = 11
$script:TAG_LONG_ARRAY = 12

# ══ NBT 读写（替代原 prepare_world 的内联 C# 实现）════════════════════════
# python 侧用 struct 的 ">" 前缀（**大端**）；.NET 的 BinaryReader/BinaryWriter 是
# 小端，因此这里统一走「取字节 → 反转 → BitConverter」的小工具，语义与 struct 对齐。
#
# ⚠️ PowerShell 陷阱：函数 `return $array` 会把数组**展开**成多个输出对象。本区块里凡是
#    需要「整体返回数组」的地方一律用 `return ,$x`；复合标签的 (tagId, value) 二元组
#    一律经 New-MtPair 构造（避免 @($t, $v) 把 $v 自身是数组时压平）。

function New-MtPair {
    <#
    .SYNOPSIS
        构造 python 语义的二元组 (tagId, value) / (elementType, items)。
    .NOTES
        必须用 [object[]]::new(2) 逐个赋值：@($a, $b) 在 $b 是数组时会被压平。
    #>
    param($A, $B)

    $pair = [object[]]::new(2)
    $pair[0] = $A
    $pair[1] = $B
    return , $pair
}

function Read-MtExact {
    [CmdletBinding()]
    param([Parameter(Mandatory)][System.IO.Stream]$Stream, [Parameter(Mandatory)][int]$Count)

    $buf = [byte[]]::new($Count)
    $got = 0
    while ($got -lt $Count) {
        $n = $Stream.Read($buf, $got, $Count - $got)
        if ($n -le 0) { throw [System.IO.EndOfStreamException]::new("NBT 数据意外结束（已读 $got/$Count 字节）") }
        $got += $n
    }
    return , $buf
}

function Read-MtBeInt16 {
    param([System.IO.Stream]$Stream)
    $b = Read-MtExact -Stream $Stream -Count 2; [Array]::Reverse($b)
    return [BitConverter]::ToInt16($b, 0)
}
function Read-MtBeInt32 {
    param([System.IO.Stream]$Stream)
    $b = Read-MtExact -Stream $Stream -Count 4; [Array]::Reverse($b)
    return [BitConverter]::ToInt32($b, 0)
}
function Read-MtBeInt64 {
    param([System.IO.Stream]$Stream)
    $b = Read-MtExact -Stream $Stream -Count 8; [Array]::Reverse($b)
    return [BitConverter]::ToInt64($b, 0)
}
function Read-MtBeSingle {
    param([System.IO.Stream]$Stream)
    $b = Read-MtExact -Stream $Stream -Count 4; [Array]::Reverse($b)
    return [BitConverter]::ToSingle($b, 0)
}
function Read-MtBeDouble {
    param([System.IO.Stream]$Stream)
    $b = Read-MtExact -Stream $Stream -Count 8; [Array]::Reverse($b)
    return [BitConverter]::ToDouble($b, 0)
}

function Write-MtBeInt16 {
    param([System.IO.Stream]$Stream, $Value)
    $b = [BitConverter]::GetBytes([int16]$Value); [Array]::Reverse($b); $Stream.Write($b, 0, 2)
}
function Write-MtBeInt32 {
    param([System.IO.Stream]$Stream, $Value)
    $b = [BitConverter]::GetBytes([int32]$Value); [Array]::Reverse($b); $Stream.Write($b, 0, 4)
}
function Write-MtBeInt64 {
    param([System.IO.Stream]$Stream, $Value)
    $b = [BitConverter]::GetBytes([int64]$Value); [Array]::Reverse($b); $Stream.Write($b, 0, 8)
}
function Write-MtBeSingle {
    param([System.IO.Stream]$Stream, $Value)
    $b = [BitConverter]::GetBytes([single]$Value); [Array]::Reverse($b); $Stream.Write($b, 0, 4)
}
function Write-MtBeDouble {
    param([System.IO.Stream]$Stream, $Value)
    $b = [BitConverter]::GetBytes([double]$Value); [Array]::Reverse($b); $Stream.Write($b, 0, 8)
}

function Read-MtUtf8 {
    <#
    .SYNOPSIS
        读 len 字节并按 UTF-8 解码（python 的 `r.read(n).decode("utf-8")`）。
    #>
    param([System.IO.Stream]$Stream, [int]$Length)

    if ($Length -le 0) { return '' }
    return [System.Text.UTF8Encoding]::new($false, $false).GetString((Read-MtExact -Stream $Stream -Count $Length))
}

function Read-MtNbtTag {
    <#
    .SYNOPSIS
        读一个 NBT 负载，返回值形态与 python 版 `_read_tag` 一致。

    .NOTES
        形态对照（刻意保留 python 的元组语义，便于逐行对照）:
          TAG_COMPOUND   → [ordered]@{ name = (tagId, value) }
          TAG_LIST       → (elementType, @(value, …))
          TAG_INT/LONG_ARRAY → @(标量…)
          TAG_BYTE_ARRAY → byte[]
          其余           → 标量
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][System.IO.Stream]$Stream, [Parameter(Mandatory)][int]$TagId)

    switch ($TagId) {
        $script:TAG_BYTE { return [sbyte]((Read-MtExact -Stream $Stream -Count 1)[0]) }
        $script:TAG_SHORT { return (Read-MtBeInt16 -Stream $Stream) }
        $script:TAG_INT { return (Read-MtBeInt32 -Stream $Stream) }
        $script:TAG_LONG { return (Read-MtBeInt64 -Stream $Stream) }
        $script:TAG_FLOAT { return (Read-MtBeSingle -Stream $Stream) }
        $script:TAG_DOUBLE { return (Read-MtBeDouble -Stream $Stream) }
        $script:TAG_BYTE_ARRAY {
            $n = Read-MtBeInt32 -Stream $Stream
            if ($n -le 0) { return , ([byte[]]@()) }
            return (Read-MtExact -Stream $Stream -Count $n)
        }
        $script:TAG_STRING {
            $n = Read-MtBeInt16 -Stream $Stream
            return (Read-MtUtf8 -Stream $Stream -Length $n)
        }
        $script:TAG_LIST {
            $et = [int]([sbyte]((Read-MtExact -Stream $Stream -Count 1)[0]))
            $n = Read-MtBeInt32 -Stream $Stream
            $items = @()
            for ($k = 0; $k -lt $n; $k++) {
                $items += , (Read-MtNbtTag -Stream $Stream -TagId $et)
            }
            return (New-MtPair $et $items)
        }
        $script:TAG_COMPOUND {
            # ⚠️ 必须用**区分大小写**的字典：PowerShell 的 [ordered]@{}（以及 @{}）是
            #    大小写不敏感的，而 NBT 里 `Version`（compound）与 `version`（long）
            #    是**两个不同的键** —— 用 [ordered]@{} 会把前者直接覆盖掉，
            #    level.dat 静默少 62 字节。原版 level.dat 两者都写。
            $out = [System.Collections.Specialized.OrderedDictionary]::new([System.StringComparer]::Ordinal)
            while ($true) {
                $t = [int]([sbyte]((Read-MtExact -Stream $Stream -Count 1)[0]))
                if ($t -eq 0) { return $out }
                $nl = Read-MtBeInt16 -Stream $Stream
                $name = Read-MtUtf8 -Stream $Stream -Length $nl
                $out[$name] = (New-MtPair $t (Read-MtNbtTag -Stream $Stream -TagId $t))
            }
        }
        $script:TAG_INT_ARRAY {
            $n = Read-MtBeInt32 -Stream $Stream
            $vals = @()
            for ($k = 0; $k -lt $n; $k++) { $vals += (Read-MtBeInt32 -Stream $Stream) }
            return , $vals
        }
        $script:TAG_LONG_ARRAY {
            $n = Read-MtBeInt32 -Stream $Stream
            $vals = @()
            for ($k = 0; $k -lt $n; $k++) { $vals += (Read-MtBeInt64 -Stream $Stream) }
            return , $vals
        }
        default { throw "未知 NBT 标签类型 $TagId" }
    }
}

function Write-MtNbtTag {
    <#
    .SYNOPSIS
        写一个 NBT 负载（与 python 版 `_write_tag` 对称）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][System.IO.Stream]$Stream, [Parameter(Mandatory)][int]$TagId, $Value)

    switch ($TagId) {
        $script:TAG_BYTE { $Stream.WriteByte([byte]([sbyte]$Value)); return }
        $script:TAG_SHORT { Write-MtBeInt16 -Stream $Stream -Value $Value; return }
        $script:TAG_INT { Write-MtBeInt32 -Stream $Stream -Value $Value; return }
        $script:TAG_LONG { Write-MtBeInt64 -Stream $Stream -Value $Value; return }
        $script:TAG_FLOAT { Write-MtBeSingle -Stream $Stream -Value $Value; return }
        $script:TAG_DOUBLE { Write-MtBeDouble -Stream $Stream -Value $Value; return }
        $script:TAG_BYTE_ARRAY {
            $bytes = [byte[]]$Value
            Write-MtBeInt32 -Stream $Stream -Value $bytes.Length
            $Stream.Write($bytes, 0, $bytes.Length)
            return
        }
        $script:TAG_STRING {
            $raw = [System.Text.UTF8Encoding]::new($false, $false).GetBytes([string]$Value)
            Write-MtBeInt16 -Stream $Stream -Value $raw.Length
            $Stream.Write($raw, 0, $raw.Length)
            return
        }
        $script:TAG_LIST {
            $et = [int]$Value[0]
            $items = @($Value[1])
            $Stream.WriteByte([byte]([sbyte]$et))
            Write-MtBeInt32 -Stream $Stream -Value $items.Count
            foreach ($it in $items) { Write-MtNbtTag -Stream $Stream -TagId $et -Value $it }
            return
        }
        $script:TAG_COMPOUND {
            foreach ($name in $Value.Keys) {
                $pair = $Value[$name]
                $t = [int]$pair[0]
                $v = $pair[1]
                $Stream.WriteByte([byte]([sbyte]$t))
                $nb = [System.Text.UTF8Encoding]::new($false, $false).GetBytes([string]$name)
                Write-MtBeInt16 -Stream $Stream -Value $nb.Length
                $Stream.Write($nb, 0, $nb.Length)
                Write-MtNbtTag -Stream $Stream -TagId $t -Value $v
            }
            $Stream.WriteByte(0)
            return
        }
        $script:TAG_INT_ARRAY {
            $vals = @($Value)
            Write-MtBeInt32 -Stream $Stream -Value $vals.Count
            foreach ($v in $vals) { Write-MtBeInt32 -Stream $Stream -Value $v }
            return
        }
        $script:TAG_LONG_ARRAY {
            $vals = @($Value)
            Write-MtBeInt32 -Stream $Stream -Value $vals.Count
            foreach ($v in $vals) { Write-MtBeInt64 -Stream $Stream -Value $v }
            return
        }
        default { throw "未知 NBT 标签类型 $TagId" }
    }
}

function Read-MtNbt {
    <#
    .SYNOPSIS
        读整个 NBT 文件（gzip）。返回 @{ TagId; Name; Payload }。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Path)

    $fs = [System.IO.File]::OpenRead($Path)
    $gz = [System.IO.Compression.GZipStream]::new($fs, [System.IO.Compression.CompressionMode]::Decompress)
    try {
        $tid = [int]([sbyte](Read-MtExact -Stream $gz -Count 1)[0])
        $nl = Read-MtBeInt16 -Stream $gz
        $name = Read-MtUtf8 -Stream $gz -Length $nl
        $payload = Read-MtNbtTag -Stream $gz -TagId $tid
        return [pscustomobject]@{ TagId = $tid; Name = $name; Payload = $payload }
    } finally {
        $gz.Dispose(); $fs.Dispose()
    }
}

function Write-MtNbt {
    <#
    .SYNOPSIS
        写整个 NBT 文件（gzip）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][int]$TagId,
        [Parameter(Mandatory)][AllowEmptyString()][string]$Name,
        $Payload
    )

    # python 先写进内存 BytesIO，最后一次性 gzip 落盘 —— 这里同样先后台打包，避免半截文件
    $ms = [System.IO.MemoryStream]::new()
    try {
        $ms.WriteByte([byte]([sbyte]$TagId))
        $nb = [System.Text.UTF8Encoding]::new($false, $false).GetBytes($Name)
        Write-MtBeInt16 -Stream $ms -Value $nb.Length
        $ms.Write($nb, 0, $nb.Length)
        Write-MtNbtTag -Stream $ms -TagId $TagId -Value $Payload
        $raw = $ms.ToArray()
    } finally {
        $ms.Dispose()
    }

    $fs = [System.IO.File]::Create($Path)
    try {
        $gz = [System.IO.Compression.GZipStream]::new($fs, [System.IO.Compression.CompressionLevel]::Optimal)
        try { $gz.Write($raw, 0, $raw.Length) } finally { $gz.Dispose() }
    } finally {
        $fs.Dispose()
    }
}

function Set-MtAllowCommands {
    <#
    .SYNOPSIS
        单人存档的「允许命令」由 level.dat 的 Data.allowCommands 决定。

    .DESCRIPTION
        runServer 生成的世界默认不写该字段，因此必须补上，否则 /give 等
        测试命令不可用。返回 $true 表示字段已就位。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$LevelDat)

    try {
        $nbt = Read-MtNbt -Path $LevelDat
    } catch {
        Write-MtErrorLine "解析 level.dat 失败：$($_.Exception.Message)"
        return $false
    }

    if ($nbt.TagId -ne $script:TAG_COMPOUND -or -not $nbt.Payload.Contains('Data')) {
        Write-MtErrorLine 'level.dat 结构异常（缺少 Data）'
        return $false
    }

    $data = $nbt.Payload['Data'][1]
    if ($data -is [System.Collections.IDictionary]) {
        $data['allowCommands'] = (New-MtPair $script:TAG_BYTE 1)
    } else {
        Write-MtErrorLine 'Data 不是复合标签'
        return $false
    }

    try {
        Write-MtNbt -Path $LevelDat -TagId $nbt.TagId -Name $nbt.Name -Payload $nbt.Payload
    } catch {
        Write-MtErrorLine "写回 level.dat 失败：$($_.Exception.Message)"
        return $false
    }
    return $true
}

function Set-MtKeepInventory {
    <#
    .SYNOPSIS
        测试世界规则：keepInventory=true（死亡不掉落）。返回 $true 表示规则已就位。

    .DESCRIPTION
        自动化用例会主动击杀/被击杀（僵尸靶子、雷击、骰战反伤等），若死亡掉落物品，
        掉落物会留在世界里污染后续用例（背包/装备状态被清空、地面残留实体卡 tick），
        且「死亡前后背包一致」类断言的基线不再稳定。故**任何新建或恢复的测试世界**
        都必须带 keepInventory=true。

        存储位置：单人存档的 gamerule 在 level.dat 的 `Data.GameRules` 复合标签里，
        值是 **TAG_String**（"true"/"false"），与 `Data.allowCommands`（TAG_Byte）不同。
        `GameRules` 缺失时按 Ordinal 比较器新建（NBT 键大小写敏感，不能用 [ordered]@{}）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$LevelDat)

    try {
        $nbt = Read-MtNbt -Path $LevelDat
    } catch {
        Write-MtErrorLine "解析 level.dat 失败：$($_.Exception.Message)"
        return $false
    }

    if ($nbt.TagId -ne $script:TAG_COMPOUND -or -not $nbt.Payload.Contains('Data')) {
        Write-MtErrorLine 'level.dat 结构异常（缺少 Data）'
        return $false
    }

    $data = $nbt.Payload['Data'][1]
    if (-not ($data -is [System.Collections.IDictionary])) {
        Write-MtErrorLine 'Data 不是复合标签'
        return $false
    }

    $rules = $null
    if ($data.Contains('GameRules')) {
        $candidate = $data['GameRules'][1]
        if ($candidate -is [System.Collections.IDictionary]) { $rules = $candidate }
    }
    if ($null -eq $rules) {
        $rules = [System.Collections.Specialized.OrderedDictionary]::new([System.StringComparer]::Ordinal)
        $data['GameRules'] = (New-MtPair $script:TAG_COMPOUND $rules)
    }
    $rules['keepInventory'] = (New-MtPair $script:TAG_STRING 'true')

    try {
        Write-MtNbt -Path $LevelDat -TagId $nbt.TagId -Name $nbt.Name -Payload $nbt.Payload
    } catch {
        Write-MtErrorLine "写回 level.dat 失败：$($_.Exception.Message)"
        return $false
    }
    return $true
}

# ══ 子命令：mods ══════════════════════════════════════════════════════════
function Invoke-MtEnvMods {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $p = Get-MtPaths -Version $Version
    if (-not (Test-Path -LiteralPath $p.mods_dir)) {
        [void](New-Item -ItemType Directory -Force -Path $p.mods_dir)
    }

    if ($Version -eq '1.20.1') {
        # dev run 不装渲染模组（Embeddium/Oculus 的 refmap 在 mojmap 下无法解析）；
        # 本步骤只确认用户生产环境渲染栈就绪，供兼容性人工验证。
        if (-not (Test-Path -LiteralPath $p.pack_mods_dir -PathType Container)) {
            Write-MtLine "MT_MODS: BLOCKED — 生产环境目录不存在 $($p.pack_mods_dir)"
            return 11
        }
        $imblocker = @(Get-ChildItem -LiteralPath $p.pack_mods_dir -File -Filter '*.jar' |
                Where-Object { $_.Name.ToLowerInvariant().Contains('imblocker') }) | Select-Object -First 1
        if ($imblocker) {
            Write-MtLine "MT_WARN: 生产环境仍含 IMBlocker（$($imblocker.Name)），需移入 __disabled__"
        }
        Write-MtLine 'MT_MODS: OK — 1.20.1 dev run 不使用渲染模组；生产环境已校验'
        return 0
    }

    $copied = @()
    $missing = @()
    foreach ($spec in $script:NeoForgeMods) {
        $src = $null
        foreach ($f in @(Get-ChildItem -LiteralPath $p.pack_mods_dir -File -Filter '*.jar' |
                    Where-Object { $_.Name -like $spec.Pattern })) {
            if ($f.Name.ToLowerInvariant().Contains('neoforge')) { $src = $f; break }
        }
        if ($null -eq $src) { $missing += $spec.Pattern; continue }

        $dst = Join-Path $p.mods_dir $spec.Target
        if (Test-Path -LiteralPath $dst -PathType Leaf) {
            $di = Get-Item -LiteralPath $dst
            if ($di.LastWriteTimeUtc -ge $src.LastWriteTimeUtc) { continue }
        }
        Copy-Item -LiteralPath $src.FullName -Destination $dst -Force
        $copied += $spec.Target
    }

    if ($missing.Count -gt 0) {
        Write-MtLine "MT_MODS: BLOCKED — 整合包缺少 $($missing -join ', ')"
        return 11
    }
    $detail = if ($copied.Count -gt 0) { "新装 $($copied.Count) 个" } else { '已是最新' }
    Write-MtLine "MT_MODS: OK — Sodium/Iris/ModernFix $detail"
    return 0
}

# ══ 子命令：world ═════════════════════════════════════════════════════════
$script:ServerPropsLines = @(
    '#Minecraft server properties',
    'online-mode=false',
    'level-name={world}',
    'level-type=flat',
    'generator-settings=',
    'gamemode=creative',
    'difficulty=easy',
    'spawn-protection=0',
    'enable-command-block=true',
    'max-players=2',
    'allow-cheats=true'
)

function Remove-MtTree {
    param([string]$Path)
    if (Test-Path -LiteralPath $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Disable-MtPauseOnLostFocus {
    <#
    .SYNOPSIS
        失焦暂停会让后台注入失效，必须关闭。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $opt = Join-Path $Paths.run_dir 'options.txt'
    $lines = @()
    if (Test-Path -LiteralPath $opt -PathType Leaf) {
        $lines = @(Get-Content -LiteralPath $opt -Encoding UTF8 -ErrorAction SilentlyContinue |
                Where-Object { -not ([string]$_).StartsWith('pauseOnLostFocus:') })
    }
    $lines += 'pauseOnLostFocus:false'
    # python 用 ascii 编码写回；这里显式用 ASCII（无 BOM、无 CRLF）
    [System.IO.File]::WriteAllText($opt, (($lines -join "`n") + "`n"), [System.Text.Encoding]::ASCII)
}

function Restore-MtSeed {
    <#
    .SYNOPSIS
        从 resources/testworld-seed-<版本>.zip 快恢复世界；无种子包返回 $false。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $seed = Join-Path (Join-Path (Join-Path (Get-MtRoot) 'scripts') 'test') "resources/testworld-seed-$Version.zip"
    if (-not (Test-Path -LiteralPath $seed -PathType Leaf)) { return $false }

    $p = Get-MtPaths -Version $Version
    Remove-MtTree -Path $p.client_world
    Remove-MtTree -Path $p.server_world
    if (-not (Test-Path -LiteralPath $p.saves_dir)) {
        [void](New-Item -ItemType Directory -Force -Path $p.saves_dir)
    }
    Expand-Archive -LiteralPath $seed -DestinationPath $p.saves_dir -Force
    Disable-MtPauseOnLostFocus -Paths $p
    return $true
}

function Start-MtGradleServer {
    <#
    .SYNOPSIS
        后台启动 runServer（用于生成世界），返回 { Process; LogPath }。

    .NOTES
        python 侧把输出丢弃（DEVNULL）；此处落到 run/<版本>/mt_server_gen.log(+.err)，
        便于世界生成失败时取证。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $root = Get-MtRoot
    $gradlew = Join-Path $root 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradlew -PathType Leaf)) { $gradlew = Join-Path $root 'gradlew' }

    $log = Join-Path $Paths.run_dir 'mt_server_gen.log'
    return (Start-MtProcessToFile -FilePath 'cmd.exe' `
            -ArgumentList @('/c', $gradlew, $Paths.task_server, '--console=plain') `
            -LogPath $log -WorkingDirectory $root -MergeStderr)
}

function Invoke-MtEnvWorld {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [bool]$Seed = $false,
        [int]$Timeout = 180
    )

    $p = Get-MtPaths -Version $Version

    if ($Seed) {
        if (-not (Restore-MtSeed -Version $Version)) {
            Write-MtLine "MT_WORLD: BLOCKED — 未找到种子包 resources/testworld-seed-$Version.zip"
            return 11
        }
        if (-not (Set-MtAllowCommands -LevelDat (Join-Path $p.client_world 'level.dat'))) {
            Write-MtLine 'MT_WORLD: BLOCKED — level.dat 的 AllowCommands 未能设置'
            return 11
        }
        # 测试规则：新建/恢复的世界必须 keepInventory=true（见 Set-MtKeepInventory）
        if (-not (Set-MtKeepInventory -LevelDat (Join-Path $p.client_world 'level.dat'))) {
            Write-MtLine 'MT_WORLD: BLOCKED — level.dat 的 GameRules.keepInventory 未能设置'
            return 11
        }
        Write-MtLine "MT_WORLD: OK — 种子快恢复（allowCommands=1, keepInventory=true） $($p.client_world)"
        return 0
    }

    # 1. 清旧世界与旧日志
    Remove-MtTree -Path $p.client_world
    Remove-MtTree -Path $p.server_world
    if (-not (Test-Path -LiteralPath $p.saves_dir)) {
        [void](New-Item -ItemType Directory -Force -Path $p.saves_dir)
    }
    if (Test-Path -LiteralPath $p.latest_log -PathType Leaf) {
        Remove-Item -LiteralPath $p.latest_log -Force -ErrorAction SilentlyContinue
    }

    # 2. server.properties
    $world = Get-MtWorldName
    $text = (($script:ServerPropsLines | ForEach-Object { $_ -replace '\{world\}', $world }) -join "`n") + "`n"
    [System.IO.File]::WriteAllText((Join-Path $p.run_dir 'server.properties'), $text, [System.Text.Encoding]::ASCII)
    Disable-MtPauseOnLostFocus -Paths $p

    # 3. 生成世界期间临时移出纯客户端模组（服务端加载会崩溃或挂起）
    #
    # ⚠️ 修正一件原实现的真 BUG（python 版 scripts/test/mt_env.py L329 同样有）：
    #    原来的备份名是 `__clientonly_bak__<原名>`，**仍以 .jar 结尾**，而 FML 在
    #    ModDirTransformerDiscoverer 阶段扫描 mods 目录里的**所有 .jar** —— 于是
    #    「移走」的 Sodium 照样被发现，服务端启动瞬间就崩：
    #      Found additional transformation services from discovery services:
    #        [...\run\1.21.1\mods\__clientonly_bak__sodium-neoforge-0.8.13+mc1.21.1.jar]
    #      Invoking bootstrap method sodium
    #      Exception in thread "main" java.lang.NoClassDefFoundError: org/lwjgl/Version
    #    （真机实测：runServer 2 秒退出、exit 1、latest.log 只到 ModLauncher 启动行）
    #    因此给备份名再加 `.disabled` 后缀，让它不再被当作模组。
    #    恢复逻辑用代码里记下的 (Bak, Orig) 配对，不依赖文件名可逆，故改名安全。
    $moved = @()
    if (Test-Path -LiteralPath $p.mods_dir -PathType Container) {
        foreach ($f in @(Get-ChildItem -LiteralPath $p.mods_dir -File -Filter '*.jar')) {
            $lower = $f.Name.ToLowerInvariant()
            $isClientOnly = $false
            foreach ($k in @('imblocker', 'sodium', 'iris', 'embeddium', 'oculus')) {
                if ($lower.Contains($k)) { $isClientOnly = $true; break }
            }
            if (-not $isClientOnly) { continue }
            $bak = Join-Path $f.DirectoryName ("__clientonly_bak__" + $f.Name + ".disabled")
            Move-Item -LiteralPath $f.FullName -Destination $bak -Force
            $moved += [pscustomobject]@{ Bak = $bak; Orig = $f.FullName }
        }
    }

    # 崩溃基线：只看**本次生成期间新增**的崩溃报告
    #
    # ⚠️ 同样修正原实现的真 BUG（python 版 L347 的 `any(p.crash_dir.glob("*.txt"))`）：
    #    原来只要目录里**存在任何**历史崩溃报告就直接判 FAIL 并放弃生成 —— 本机
    #    run/1.21.1/crash-reports 里躺着一条 2026-09-11 的旧报告，于是世界永远生成不出来
    #    （实测先打印 `MT_WORLD: FAIL — 生成世界期间产生崩溃报告` 再 BLOCKED）。
    #    期望语义显然是「生成期间产生的崩溃报告」，故先记基线、只认新增。
    $crashBaseline = @()
    if (Test-Path -LiteralPath $p.crash_dir -PathType Container) {
        $crashBaseline = @(Get-ChildItem -LiteralPath $p.crash_dir -File -Filter '*.txt' | ForEach-Object { $_.Name })
    }

    $done = $false
    $started = $null
    try {
        try {
            $started = Start-MtGradleServer -Paths $p
        } catch {
            $started = $null
        }
        $deadline = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + $Timeout
        while ([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() -lt $deadline) {
            Start-Sleep -Seconds 5
            if (Test-Path -LiteralPath $p.latest_log -PathType Leaf) {
                $txt = Read-MtSharedText -Path $p.latest_log
                if ($txt.Contains('Done (')) { $done = $true; break }
                $crashes = @()
                if (Test-Path -LiteralPath $p.crash_dir -PathType Container) {
                    $crashes = @(Get-ChildItem -LiteralPath $p.crash_dir -File -Filter '*.txt' |
                        Where-Object { $crashBaseline -notcontains $_.Name })
                }
                if ($crashes.Count -gt 0) {
                    Write-MtLine "MT_WORLD: FAIL — 生成世界期间产生崩溃报告（$($crashes[0].Name)）"
                    break
                }
            }
            if ($null -ne $started -and $started.Process.HasExited -and -not $done) { break }
        }
    } finally {
        [void](Stop-MtVersionProcesses -Paths $p -Quiet)
        foreach ($m in $moved) {
            if (Test-Path -LiteralPath $m.Bak) {
                Move-Item -LiteralPath $m.Bak -Destination $m.Orig -Force
            }
        }
        Start-Sleep -Seconds 3
    }

    # 4. 世界从 run/<ver>/testworld 搬到 saves/testworld（客户端读取位置）
    if (Test-Path -LiteralPath $p.server_world -PathType Container) {
        Remove-MtTree -Path $p.client_world
        Move-Item -LiteralPath $p.server_world -Destination $p.client_world -Force
    }

    $level = Join-Path $p.client_world 'level.dat'
    if (-not ($done -and (Test-Path -LiteralPath $level -PathType Leaf))) {
        Write-MtErrLine 'MT_WORLD: BLOCKED — 世界未在时限内生成'
        return 11
    }
    if (-not (Set-MtAllowCommands -LevelDat $level)) {
        Write-MtErrLine 'MT_WORLD: BLOCKED — level.dat 的 AllowCommands 未能设置'
        return 11
    }
    # 测试规则：新建/恢复的世界必须 keepInventory=true（见 Set-MtKeepInventory）
    if (-not (Set-MtKeepInventory -LevelDat $level)) {
        Write-MtErrLine 'MT_WORLD: BLOCKED — level.dat 的 GameRules.keepInventory 未能设置'
        return 11
    }
    Write-MtLine "MT_WORLD: OK — 世界重建（allowCommands=1, keepInventory=true） $($p.client_world)"
    return 0
}

# ══ 入口 ══════════════════════════════════════════════════════════════════
# 点源（`. ./mt_env.ps1`）时只加载函数、不执行入口 —— 迁移期的 NBT 等价门
# （temp/nbt_gate.ps1）正是靠这一点直接调用 Set-MtAllowCommands。
if ($MyInvocation.InvocationName -ne '.') {

    $Cmd = ''
    $Version = ''
    $SeedFlag = $false
    $TimeoutSec = 180

    $i = 0
    while ($i -lt $args.Count) {
        $tok = [string]$args[$i]
        $key = $tok.TrimStart('-').ToLowerInvariant()
        if ($tok -notlike '-*') {
            if ($Cmd) { Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR }
            $Cmd = $key
            $i++
        } elseif ($key -eq 'version') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
            $Version = [string]$args[$i + 1]
            $i += 2
        } elseif ($key -eq 'seed') {
            $SeedFlag = $true; $i++
        } elseif ($key -eq 'timeout') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --timeout 的值'; exit $MT_EXIT_ERROR }
            $TimeoutSec = [int]$args[$i + 1]
            $i += 2
        } else {
            Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
        }
    }

    if ($Cmd -notin @('mods', 'world', 'kill')) {
        Write-MtErrorLine '必须指定子命令 mods / world / kill'
        exit $MT_EXIT_ERROR
    }
    if (-not $Version) {
        Write-MtErrorLine '必须指定 --version'
        exit $MT_EXIT_ERROR
    }
    if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }

    switch ($Cmd) {
        'mods' { exit (Invoke-MtEnvMods -Version $Version) }
        'world' { exit (Invoke-MtEnvWorld -Version $Version -Seed $SeedFlag -Timeout $TimeoutSec) }
        'kill' {
            [void](Stop-MtVersionProcesses -Paths (Get-MtPaths -Version $Version))
            exit $MT_EXIT_PASS
        }
    }
    exit $MT_EXIT_ERROR
}
