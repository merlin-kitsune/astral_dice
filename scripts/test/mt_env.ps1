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
    迁移前源文件 scripts/test/mt_env.py（该原件已在 92fbeaf「工具链收敛为纯 pwsh」删除，取回：`git show 92fbeaf^:scripts/test/mt_env.py`）。

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

# ── 26.1.2 探针运行时（仅 dev run 需要）──────────────────────────────────────
# 26.1.2 的整合包实例里**没有** KubeJS（用户实例未装），而 KubeJS 是探针脚本的宿主，
# 故从 KubeJS 官方 maven 拉「KubeJS + 必需前置 Rhino」到 `run/26.1.2/mods`：
#   · **不**写进 build.gradle 依赖 —— 否则 datagen/构建也会装载 KubeJS(其自带 data
#     provider 会污染 26.1.2 的两段式数据生成);1.21.1 侧同样采用「run/mods 直接装载」约定;
#   · 版本取自子项目 gradle.properties 的 `kubejs_version`(单一事实来源);
#   · 其余三个版本来自 KubeJS 26.1.2-8.0.6 的元数据/POM,均**必需**:
#       rhino                 —— mods.toml 的 required 依赖 [2101.2.8-build.91,)
#       better-advanced-tooltips —— POM runtime 依赖 [2601.1.0-build.9,)。
#         实测:即使只跑**服务端**(世界生成)也必需 —— KubeJS 的 TextIcons.<clinit>
#         无条件引用 dev.latvian.mods.betteradvancedtooltips.BATIcons,
#         缺它会让 RegisterEvent 阶段抛 NoClassDefFoundError 直接崩服。
#       tiny-java-server      —— POM runtime 依赖,但它是**纯 Java 库(无 mods.toml)**:
#         放进 run/mods 会让 FML 在启动时弹「不是一个有效的模组文件」警告屏并**停在那里**
#         (2026-09-16 实测),且它只服务 KubeJS 自带的 HTTP 面板(本测试链不使用)。
#         ⇒ **故意不装**。若将来确实需要,应走 dev classpath 而不是 run/mods。
#   · 下载缓存在 `temp/probe_mods/<版本>/`,幂等:目标已存在同尺寸文件即跳过。
$script:KubejsRhinoVersion = '2101.2.8-build.91'
$script:KubejsBatVersion = '2601.1.0-build.10'
$script:KubejsTinyJavaServerVersion = '1.0.0-build.45'

# ── 26.1.2 渲染栈（Sodium + Iris）与光影（2026-09-17 用户要求：光影兼容性测试）──
# 来源一律走 **Modrinth Maven**（`https://api.modrinth.com/maven`，本子项目 build.gradle 第 40 行已声明该仓库，
# 与 Curios 同源），**不使用** CDN/GitHub 直链。Maven 坐标为 `maven.modrinth:<slug>:<version>`：
#   maven.modrinth:sodium:mc26.1.2-0.9.2-neoforge
#   maven.modrinth:iris:1.11.4+26.1-neoforge          ← 硬依赖 Sodium（required），必须同装
#   maven.modrinth:complementary-unbound:r5.9.3       ← 光影包同样由 Modrinth Maven 提供（已验证 200）
# 落地方式仍是「下载进 run/26.1.2/mods 与 shaderpacks/」而不是写进 build.gradle 依赖，理由与 KubeJS 相同：
#   两段式数据生成（runClientData/runServerData）与 runClient 共用同一 runtimeClasspath，把**纯客户端**模组
#   写进依赖会让服务端数据生成也装载它们（直接崩）。1.21.1 侧同样是「只放进 run/mods」的约定。
# 版本均为 release、且均声明支持 26.1.2（该线此前注释写「Iris 尚无可用的 26.1.2 构建」——已过期）。
# ⚠️ 这些是纯客户端模组：`mt_env world` 起专用服务器前必须移出（Invoke-MtEnvWorld 已覆盖 sodium/iris），
#   两段式数据生成前也必须移出。
$script:RenderMods2612 = @(
    @{ Name = 'sodium-neoforge-0.9.2+mc26.1.2.jar'
       Coord = 'maven.modrinth:sodium:mc26.1.2-0.9.2-neoforge'
       Url  = 'https://api.modrinth.com/maven/maven/modrinth/sodium/mc26.1.2-0.9.2-neoforge/sodium-neoforge-0.9.2+mc26.1.2.jar'
       Sha1 = 'e03e21bf6553fc517241d59c2c116b8e5cc882f8'
       Size = 1260562 }
    @{ Name = 'iris-neoforge-1.11.4+mc26.1.2.jar'
       Coord = 'maven.modrinth:iris:1.11.4+26.1-neoforge'
       Url  = 'https://api.modrinth.com/maven/maven/modrinth/iris/1.11.4+26.1-neoforge/iris-neoforge-1.11.4+mc26.1.2.jar'
       Sha1 = '15ac52fe7b35c66bb799f0f13021f549bf302c3c'
       Size = 2756643 }
)

# Complementary Shaders - Unbound（用户指定用于光影兼容性测试）
# `maven.modrinth:complementary-unbound:r5.9.3`；落位 `run/<版本>/shaderpacks/`，并在 Iris 配置里选中它。
$script:ShaderPack2612 = @{
    Name  = 'ComplementaryUnbound_r5.9.3.zip'
    Coord = 'maven.modrinth:complementary-unbound:r5.9.3'
    Url   = 'https://api.modrinth.com/maven/maven/modrinth/complementary-unbound/r5.9.3/ComplementaryUnbound_r5.9.3.zip'
    Sha1  = '2ee08300e1d6f039e63eae8484dddf57b3aaaf67'
    Size  = 553400
}

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

# ══ 子命令：kubejs（探针脚本同步）══════════════════════════════════════════
function Sync-MtEnvKubejs {
    <#
    .SYNOPSIS
        把 `scripts/test/resources/kubejs/<版本>/**` 同步到 `run/<版本>/kubejs/**`。

    .NOTES
        **为什么必须有这一步（2026-09-16 实测事故根因）**：在此之前**没有任何脚本**负责把
        KubeJS 探针脚本装进 run 目录 —— 它们是被**手工**拷进去的。于是「模板已更新、run 目录
        里还是旧探针」会**静默**发生：用例照样注入 `/astralprobe …`，而游戏侧根本没有那条
        命令 ⇒ 所有断言都读不到读数、每个断言都在等一个永不出现的标记。这正是那次
        「launch 之后 cases 空转 7 分 45 秒、跑完还查不出原因」的形态之一。
        判据用**内容哈希**（不用大小+mtime：大小相同而内容不同一样会漏）。
        本函数**只碰 run/<版本>/kubejs**，不动世界、不动 mods。
    .OUTPUTS
        @(更新数, 检查数, 源目录是否存在)
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $src = Join-Path (Join-Path (Get-MtTestDir) 'resources') "kubejs/$Version"
    if (-not (Test-Path -LiteralPath $src -PathType Container)) { return , @(0, 0, $false) }

    $dstRoot = Join-Path (Get-MtPaths -Version $Version).run_dir 'kubejs'
    $copied = 0
    $total = 0
    $removed = 0
    # ⚠️ 2026-09-17 新增:只「拷贝」不「清理」会留下**已被模板删除的旧探针**
    #    (实测事故:26.1.2 的最小探针 astral_probe.js 被完整探针取代后,run 目录里那份
    #     仍在,两份都注册 `/astralprobe` ⇒ KubeJS 命令重复注册 / 读数来自旧脚本)。
    #    故同步时顺带删除 run 侧 `server_scripts/**` 中模板已不存在的 .js。
    $keep = @{}
    foreach ($f in @(Get-ChildItem -LiteralPath $src -Recurse -File)) {
        $keep[$f.FullName.Substring($src.Length).TrimStart('\', '/')] = $true
    }
    $dstMeta = Join-Path $dstRoot 'server_scripts'
    if (Test-Path -LiteralPath $dstMeta -PathType Container) {
        foreach ($f in @(Get-ChildItem -LiteralPath $dstMeta -Recurse -File -Filter '*.js')) {
            $rel = $f.FullName.Substring($dstRoot.Length).TrimStart('\', '/')
            if (-not $keep.ContainsKey($rel)) {
                Remove-Item -LiteralPath $f.FullName -Force -ErrorAction SilentlyContinue
                $removed++
            }
        }
    }
    foreach ($f in @(Get-ChildItem -LiteralPath $src -Recurse -File)) {
        $rel = $f.FullName.Substring($src.Length).TrimStart('\', '/')
        $dst = Join-Path $dstRoot $rel
        $total++
        $need = $true
        if (Test-Path -LiteralPath $dst -PathType Leaf) {
            try {
                $hs = (Get-FileHash -LiteralPath $f.FullName -Algorithm SHA256).Hash
                $hd = (Get-FileHash -LiteralPath $dst -Algorithm SHA256).Hash
                if ($hs -eq $hd) { $need = $false }
            } catch { $need = $true }
        }
        if ($need) {
            $dir = [System.IO.Path]::GetDirectoryName($dst)
            if (-not (Test-Path -LiteralPath $dir)) { [void](New-Item -ItemType Directory -Force -Path $dir) }
            Copy-Item -LiteralPath $f.FullName -Destination $dst -Force
            $copied++
        }
    }
    return , @($copied, $total, $true, $removed)
}

function Invoke-MtEnvKubejs {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $r = Sync-MtEnvKubejs -Version $Version
    if (-not $r[2]) {
        Write-MtLine ("MT_KUBEJS: SKIP — 模板目录不存在 scripts/test/resources/kubejs/{0}" -f $Version)
        return 0
    }
    if ($r[3] -gt 0) {
        Write-MtLine ("MT_KUBEJS: 已清理 {0} 个模板中已不存在的旧脚本（避免重复注册 /astralprobe）" -f $r[3])
    }
    if ($r[0] -gt 0) {
        Write-MtLine ("MT_KUBEJS: OK — 已同步 {0}/{1} 个脚本到 run/{2}/kubejs（模板有更新；探针命令的改动需**冷启动**才生效）" -f $r[0], $r[1], $Version)
    } else {
        Write-MtLine ("MT_KUBEJS: OK — {0} 个脚本均与模板一致（无需同步）" -f $r[1])
    }
    return 0
}

# ══ 26.1.2 探针运行时装装（KubeJS + Rhino）══════════════════════════════════
function Install-MtProbeRuntime {
    <#
    .SYNOPSIS
        把 26.1.2 探针所需的 KubeJS + Rhino 放进 `run/26.1.2/mods`（幂等）。

    .NOTES
        为什么不用 Gradle 依赖：`runData/runServerData/runClientData` 与 runClient 共用同一
        runtimeClasspath，把 KubeJS 写进依赖会让**数据生成**也装载它（KubeJS 自带 data
        provider，会干扰 26.1.2 的两段式生成）。放 run/mods 是 1.21.1 侧既有的约定。

        版本单一事实来源 = 子项目 gradle.properties 的 `kubejs_version`；
        Rhino 版本取 KubeJS 26.1.2-8.0.6 的 neoforge.mods.toml 中 `required` 区间下限。
        下载失败一律**硬失败**（退出码 14），不静默降级为「探针缺失」。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    if ($Paths.version -ne '26.1.2') { return 0 }

    $propsPath = Join-Path (Get-MtRoot) "$($Paths.subproject)/gradle.properties"
    if (-not (Test-Path -LiteralPath $propsPath -PathType Leaf)) {
        Write-MtLine "MT_MODS: BLOCKED — 找不到 $propsPath（无法确定 kubejs_version）"
        return 14
    }
    $kubejsVersion = ''
    foreach ($ln in (Get-Content -LiteralPath $propsPath)) {
        if ($ln -match '^\s*kubejs_version\s*=\s*(.+?)\s*$') { $kubejsVersion = $Matches[1]; break }
    }
    if (-not $kubejsVersion) {
        Write-MtLine 'MT_MODS: BLOCKED — gradle.properties 缺少 kubejs_version'
        return 14
    }

    $cache = Join-Path (Join-Path (Get-MtRoot) 'temp\probe_mods') $Paths.version
    [void](New-Item -ItemType Directory -Force -Path $cache)

    $specs = @(
        @{ Name = "kubejs-neoforge-$kubejsVersion.jar"
           Url  = "https://maven.latvian.dev/releases/dev/latvian/mods/kubejs-neoforge/$kubejsVersion/kubejs-neoforge-$kubejsVersion.jar" }
        @{ Name = "rhino-$($script:KubejsRhinoVersion).jar"
           Url  = "https://maven.latvian.dev/releases/dev/latvian/mods/rhino/$($script:KubejsRhinoVersion)/rhino-$($script:KubejsRhinoVersion).jar" }
        @{ Name = "better-advanced-tooltips-$($script:KubejsBatVersion).jar"
           Url  = "https://maven.latvian.dev/releases/dev/latvian/mods/better-advanced-tooltips/$($script:KubejsBatVersion)/better-advanced-tooltips-$($script:KubejsBatVersion).jar" }
    )

    $installed = @()
    foreach ($spec in $specs) {
        $cached = Join-Path $cache $spec.Name
        if (-not (Test-Path -LiteralPath $cached -PathType Leaf)) {
            try {
                Write-MtLine "MT_MODS: 下载探针运行时 $($spec.Name)"
                $ProgressPreference = 'SilentlyContinue'
                Invoke-WebRequest -Uri $spec.Url -OutFile "$cached.part" -TimeoutSec 180 -UseBasicParsing
                Move-Item -LiteralPath "$cached.part" -Destination $cached -Force
            } catch {
                Remove-Item -LiteralPath "$cached.part" -Force -ErrorAction SilentlyContinue
                Write-MtLine "MT_MODS: BLOCKED — 下载失败 $($spec.Url) :: $($_.Exception.Message)"
                return 14
            }
        }
        $dst = Join-Path $Paths.mods_dir $spec.Name
        $needCopy = $true
        if (Test-Path -LiteralPath $dst -PathType Leaf) {
            if ((Get-Item -LiteralPath $dst).Length -eq (Get-Item -LiteralPath $cached).Length) { $needCopy = $false }
        }
        if ($needCopy) { Copy-Item -LiteralPath $cached -Destination $dst -Force }
        $installed += $spec.Name
    }

    Write-MtLine ("MT_MODS: OK — 探针运行时就位（KubeJS {0} / Rhino {1}）→ {2}" -f `
            $kubejsVersion, $script:KubejsRhinoVersion, $Paths.mods_dir)
    return 0
}

# ══ 子命令：mods ══════════════════════════════════════════════════════════
function Install-MtRenderStack {
    <#
    .SYNOPSIS
        把 26.1.2 的渲染栈（Sodium + Iris）与光影包放进 run/<版本>/（幂等）。

    .NOTES
        · 来源 = **Modrinth Maven**（`https://api.modrinth.com/maven`，坐标见 $script:RenderMods2612 / $script:ShaderPack2612
          的 `Coord` 字段，形如 `maven.modrinth:sodium:mc26.1.2-0.9.2-neoforge`）；**不使用** CDN/GitHub 直链。
        · mods 落位 `run/26.1.2/mods`；光影包落位 `run/26.1.2/shaderpacks/`；
        · 缓存在 `temp/probe_mods/26.1.2/`（与探针运行时同一缓存目录，便于整体清理）；
        · 命中判据 = 目标文件存在且**尺寸一致**，下载后按固定 sha1 校验，失败硬报 14，不静默降级；
        · 顺带把 Iris 的选中光影写进 `config/iris.properties`（`shaderPack=<包名>`）。

        ⚠️ **`enableShaders` 默认写 false（2026-09-17 实测结论）**：在本机 dev 环境下（Sodium 0.9.2 + Iris 1.11.4 +
        MC 26.1.2）**一旦启用光影，渲染世界时必崩**：
            java.lang.IllegalStateException: Missing sampler Sampler1
              at com.mojang.blaze3d.opengl.GlCommandEncoder.trySetup(GlCommandEncoder.java:531)
          崩点上的两条 mixin 分别是 sodium 的 `core.GlCommandEncoderAccessor` 与 iris 的 `MixinGlCommandEncoder`；
        **两个互不相关的光影包（Complementary Unbound r5.9.3 与 MakeUp Ultra Fast 9.5e）复现完全相同的崩溃**，
        而 `enableShaders=false` 时进世界、工具链（注入/探针/用例）全部正常 ⇒ 属 Iris/Sodium 侧在 26.1.2 的着色器
        管线缺陷（上游同类 issue：IrisShaders/Iris #3182「Compatibility issues with sodium in version 26.1.2」、
        #2719「Game crash due to missing sampler」），**与本模组无关**。
        要做光影兼容性测试时把 `run/26.1.2/config/iris.properties` 的 `enableShaders` 改成 `true` 即可复现；
        该文件由本函数每次 `mt_env mods` 重写为 false，避免默认环境变成「一进世界就崩」。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    if ($Paths.version -ne '26.1.2') { return 0 }

    $cache = Join-Path (Join-Path (Get-MtRoot) 'temp\probe_mods') $Paths.version
    [void](New-Item -ItemType Directory -Force -Path $cache)
    [void](New-Item -ItemType Directory -Force -Path $Paths.mods_dir)

    $progressBak = $ProgressPreference
    $ProgressPreference = 'SilentlyContinue'
    try {
        # 1) 渲染模组 → run/<版本>/mods
        $installed = @()
        foreach ($spec in $script:RenderMods2612) {
            $cached = Join-Path $cache $spec.Name
            $ok = $false
            if (Test-Path -LiteralPath $cached -PathType Leaf) {
                $ci = Get-Item -LiteralPath $cached
                $ok = ($ci.Length -eq $spec.Size)
                if ($ok -and $spec.Sha1) {
                    $ok = ((Get-FileHash -LiteralPath $cached -Algorithm SHA1).Hash.ToLowerInvariant() -eq $spec.Sha1)
                }
                if (-not $ok) { Remove-Item -LiteralPath $cached -Force -ErrorAction SilentlyContinue }
            }
            if (-not $ok) {
                try {
                    Write-MtLine "MT_MODS: 下载渲染模组 $($spec.Name) ← $($spec.Coord)"
                    Invoke-WebRequest -Uri $spec.Url -OutFile "$cached.part" -TimeoutSec 300 -UseBasicParsing
                    Move-Item -LiteralPath "$cached.part" -Destination $cached -Force
                } catch {
                    Remove-Item -LiteralPath "$cached.part" -Force -ErrorAction SilentlyContinue
                    Write-MtLine "MT_MODS: BLOCKED — 渲染模组下载失败 [Modrinth Maven] $($spec.Coord) $($spec.Url) :: $($_.Exception.Message)"
                    return 14
                }
                $got = Get-Item -LiteralPath $cached
                if ($got.Length -ne $spec.Size) {
                    Write-MtLine "MT_MODS: BLOCKED — $($spec.Name) 尺寸不符（期望 $($spec.Size)，实得 $($got.Length)）"
                    return 14
                }
                if ($spec.Sha1 -and ((Get-FileHash -LiteralPath $cached -Algorithm SHA1).Hash.ToLowerInvariant() -ne $spec.Sha1)) {
                    Write-MtLine "MT_MODS: BLOCKED — $($spec.Name) sha1 校验失败"
                    return 14
                }
            }
            $dst = Join-Path $Paths.mods_dir $spec.Name
            $needCopy = $true
            if (Test-Path -LiteralPath $dst -PathType Leaf) {
                if ((Get-Item -LiteralPath $dst).Length -eq (Get-Item -LiteralPath $cached).Length) { $needCopy = $false }
            }
            if ($needCopy) { Copy-Item -LiteralPath $cached -Destination $dst -Force }
            $installed += $spec.Name
        }

        # 2) 光影包 → run/<版本>/shaderpacks
        $sp = $script:ShaderPack2612
        $spDir = Join-Path $Paths.run_dir 'shaderpacks'
        [void](New-Item -ItemType Directory -Force -Path $spDir)
        $spCache = Join-Path $cache $sp.Name
        $spOk = (Test-Path -LiteralPath $spCache -PathType Leaf) -and ((Get-Item -LiteralPath $spCache).Length -eq $sp.Size)
        if (-not $spOk) {
            if (Test-Path -LiteralPath $spCache) { Remove-Item -LiteralPath $spCache -Force -ErrorAction SilentlyContinue }
            try {
                Write-MtLine "MT_MODS: 下载光影包 $($sp.Name) ← $($sp.Coord)"
                Invoke-WebRequest -Uri $sp.Url -OutFile "$spCache.part" -TimeoutSec 300 -UseBasicParsing
                Move-Item -LiteralPath "$spCache.part" -Destination $spCache -Force
            } catch {
                Remove-Item -LiteralPath "$spCache.part" -Force -ErrorAction SilentlyContinue
                Write-MtLine "MT_MODS: BLOCKED — 光影包下载失败 [Modrinth Maven] $($sp.Coord) $($sp.Url) :: $($_.Exception.Message)"
                return 14
            }
            if ((Get-Item -LiteralPath $spCache).Length -ne $sp.Size) {
                Write-MtLine "MT_MODS: BLOCKED — 光影包尺寸不符（期望 $($sp.Size)）"
                return 14
            }
            if ($sp.Sha1 -and ((Get-FileHash -LiteralPath $spCache -Algorithm SHA1).Hash.ToLowerInvariant() -ne $sp.Sha1)) {
                Write-MtLine 'MT_MODS: BLOCKED — 光影包 sha1 校验失败'
                return 14
            }
        }
        $spDst = Join-Path $spDir $sp.Name
        if (-not (Test-Path -LiteralPath $spDst -PathType Leaf) -or
            ((Get-Item -LiteralPath $spDst).Length -ne (Get-Item -LiteralPath $spCache).Length)) {
            Copy-Item -LiteralPath $spCache -Destination $spDst -Force
        }

        # 3) Iris 配置：选中该光影并开启光影（Iris 1.11.x 的 config/iris.properties）
        $irisCfg = Join-Path (Join-Path $Paths.run_dir 'config') 'iris.properties'
        [void](New-Item -ItemType Directory -Force -Path (Split-Path $irisCfg))
        $lines = @()
        if (Test-Path -LiteralPath $irisCfg -PathType Leaf) { $lines = @(Get-Content -LiteralPath $irisCfg) }
        # enableShaders 固定写 false：见本函数 .NOTES —— 26.1.2 上启用光影必崩（两个包均复现），
        # 默认环境必须可用；要复现/做光影测试请手动把该键改成 true。
        $set = @{ 'shaderPack' = $sp.Name; 'enableShaders' = 'false' }
        foreach ($k in $set.Keys) {
            $found = $false
            for ($i = 0; $i -lt $lines.Count; $i++) {
                if ($lines[$i] -match "^\s*$([regex]::Escape($k))\s*=") { $lines[$i] = "$k=$($set[$k])"; $found = $true; break }
            }
            if (-not $found) { $lines += "$k=$($set[$k])" }
        }
        Set-Content -LiteralPath $irisCfg -Value $lines -Encoding utf8

        Write-MtLine ("MT_MODS: OK — 渲染栈就位（{0}）＋ 光影 {1}；Iris 已选中该光影" -f `
                ($installed -join ' / '), $sp.Name)
        return 0
    } finally {
        $ProgressPreference = $progressBak
    }
}

function Invoke-MtEnvMods {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version)

    $p = Get-MtPaths -Version $Version
    if (-not (Test-Path -LiteralPath $p.mods_dir)) {
        [void](New-Item -ItemType Directory -Force -Path $p.mods_dir)
    }

    # 探针脚本与 mods 同批同步（都要在 launch 之前就位；此前这一步完全缺失）
    [void](Invoke-MtEnvKubejs -Version $Version)

    if ($Version -eq '26.1.2') {
        # 探针运行时(KubeJS + Rhino)必须装 —— 否则探针命令不存在，launch 的 OP 闸门会记 ERROR。
        $rc = Install-MtProbeRuntime -Paths $p
        if ($rc -ne 0) { return $rc }
        # 渲染栈 + 光影（2026-09-17 用户要求：Sodium/Iris 最新版 + Complementary Unbound，用于光影兼容性测试）。
        # 该线此前**不装**渲染模组，理由是「26.1.2 的 Iris 尚无可用的构建」——该理由已过期
        # （实测 Modrinth 已有 sodium 0.9.2 / iris 1.11.4 的 26.1.2 release 构建）。
        $rc = Install-MtRenderStack -Paths $p
        if ($rc -ne 0) { return $rc }
        Write-MtLine 'MT_MODS: OK — 26.1.2 dev run：探针运行时 + Sodium/Iris + Complementary Unbound 光影'
        Write-MtLine 'MT_MODS: 注意 — Sodium/Iris 为纯客户端模组：`mt_env world` 起专用服务器会自动移出，但**两段式数据生成（runClientData/runServerData）前必须手动移出** run/26.1.2/mods（与探针运行时同规则）'
        return 0
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
    # ⚠️ 修正一件原实现的真 BUG（已在 92fbeaf 删除的 python 版 scripts/test/mt_env.py L329 同样有）：
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
# 迁移期的一次性原型即靠这一点直接调用 Set-MtAllowCommands（该原型已随 temp/ 清理移除）。
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

    if ($Cmd -notin @('mods', 'world', 'kill', 'kubejs')) {
        Write-MtErrorLine '必须指定子命令 mods / world / kill / kubejs'
        exit $MT_EXIT_ERROR
    }
    if (-not $Version) {
        Write-MtErrorLine '必须指定 --version'
        exit $MT_EXIT_ERROR
    }
    if (-not (Assert-MtVersion -Version $Version)) { exit $MT_EXIT_ERROR }

    switch ($Cmd) {
        'mods' { exit (Invoke-MtEnvMods -Version $Version) }
        'kubejs' { exit (Invoke-MtEnvKubejs -Version $Version) }
        'world' { exit (Invoke-MtEnvWorld -Version $Version -Seed $SeedFlag -Timeout $TimeoutSec) }
        'kill' {
            [void](Stop-MtVersionProcesses -Paths (Get-MtPaths -Version $Version))
            exit $MT_EXIT_PASS
        }
    }
    exit $MT_EXIT_ERROR
}
