#Requires -Version 7.0
<#
.SYNOPSIS
    mt_env — 测试环境装配（阶段 E）。

.DESCRIPTION
    职责（只做环境的建立与拆除，不做任何功能断言）:
      mods    安装兼容模组（三条线：优化类 ImmediatelyFast/FerriteCore；1.21.1 另装探针宿主 KubeJS/Rhino/
              Architectury + 史莱姆压制；1.20.1 另装优化类并校验生产环境渲染栈；26.1.2 另装探针运行时/
              渲染栈/光影/史莱姆压制）
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

# ── 1.21.1 探针宿主（KubeJS + Rhino + Architectury API）──────────────────────
# `neoforge-1.21.1/build.gradle` **故意不依赖 KubeJS**（其 L249-252 注释写明：2026-09 起改由
# dev run 的 `run/mods` 目录直接装载整合包同版 kubejs(2101.7.2-build.374)，因为 KubeJS 自带
# data provider，写进依赖会让 **datagen 也装载它**）。但 `mt_env mods` 此前**没有**这一步 ⇒
# 新克隆 / dev-next 工作树的 `run/1.21.1/mods` 里**没有 KubeJS**，于是 launch 的
# `MT_ASSERT_KUBEJS` 恒为 `BLOCKED — 未找到 KubeJS server.log`、`/astralprobe` 命令不存在，
# 后续所有探针用例与 `MT_preflight-op` 闸门都无法通过（2026-09-17 在 dev-next 上实测暴露；
# 主线工作树的 run 目录里是**手工**放进去的，故从未暴露这条缺口）。
# 现改为**按整合包复制**（与 Sodium/Iris/ModernFix 同一套「文件名匹配 + 时间戳幂等」）。
# 三个 jar 缺一不可：KubeJS 的 required 前置 = Rhino + Architectury API（1.21.1 线还**不**需要
# better-advanced-tooltips —— 那是 26.1.2 的 KubeJS 8 才有的硬前置，见 Install-MtProbeRuntime）。
# `Match` 是「剥掉整合包文件名里的中文方括号前缀」后的正则：整合包那份叫 `[犀牛] rhino-….jar`。
$script:ProbeHostMods1211 = @(
    @{ Pattern = 'kubejs-neoforge-*.jar'; Match = '^kubejs-neoforge-' }
    @{ Pattern = '*rhino-*.jar'; Match = '^rhino-' }
    @{ Pattern = 'architectury-*-neoforge.jar'; Match = '^architectury-' }
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
#   maven.modrinth:sodium:mc26.1.2-0.9.1-neoforge
#   maven.modrinth:iris:1.11.4+26.1-neoforge          ← 硬依赖 Sodium（required），必须同装
#   maven.modrinth:complementary-unbound:r5.9.3       ← 光影包同样由 Modrinth Maven 提供（已验证 200）
# 落地方式仍是「下载进 run/26.1.2/mods 与 shaderpacks/」而不是写进 build.gradle 依赖，理由与 KubeJS 相同：
#   两段式数据生成（runClientData/runServerData）与 runClient 共用同一 runtimeClasspath，把**纯客户端**模组
#   写进依赖会让服务端数据生成也装载它们（直接崩）。1.21.1 侧同样是「只放进 run/mods」的约定。
# 版本均为 release、且均声明支持 26.1.2（该线此前注释写「Iris 尚无可用的 26.1.2 构建」——已过期）。
# ⚠️ 这些是纯客户端模组：`mt_env world` 起专用服务器前必须移出（Invoke-MtEnvWorld 已覆盖 sodium/iris），
#   两段式数据生成前也必须移出。
$script:RenderMods2612 = @(
    @{ Name = 'sodium-neoforge-0.9.1+mc26.1.2.jar'
       Coord = 'maven.modrinth:sodium:mc26.1.2-0.9.1-neoforge'
       Url  = 'https://api.modrinth.com/maven/maven/modrinth/sodium/mc26.1.2-0.9.1-neoforge/sodium-neoforge-0.9.1+mc26.1.2.jar'
       Sha1 = 'f369407251bdeb3b91d3e67fbbc133263b0c9078'
       Size = 1185970 }
    @{ Name = 'iris-neoforge-1.11.4+mc26.1.2.jar'
       Coord = 'maven.modrinth:iris:1.11.4+26.1-neoforge'
       Url  = 'https://api.modrinth.com/maven/maven/modrinth/iris/1.11.4+26.1-neoforge/iris-neoforge-1.11.4+mc26.1.2.jar'
       Sha1 = '15ac52fe7b35c66bb799f0f13021f549bf302c3c'
       Size = 2756643 }
)

# 渲染模组**按版本**（2026-09-17 用户要求「游戏环境缺少光影包，添加光影包并设置默认启用」）：
#   · 26.1.2：Sodium/Iris 由本工具链从 Modrinth Maven 下载（见上）；
#   · 1.21.1：Sodium/Iris **由整合包复制**（`$script:NeoForgeMods`）⇒ 这里**故意为空**，
#     只补「光影包 + Iris 配置」两件事（见 Install-MtRenderStack）；
#   · 1.20.1：渲染栈（Embeddium + Oculus）由 `forge-1.20.1/build.gradle` 的 `modImplementation` 提供
#     ⇒ 本表无该键（不进 run/mods）；**光影可用**（2026-09-17 实测 `Using shaderpack: …`，
#     仅「下载渲染模组」这一步 SKIP，光影包与 iris.properties 照常落位）。
$script:RenderModsByVersion = @{
    '1.21.1' = @()
    '26.1.2' = $script:RenderMods2612
}

# ── 优化类模组（2026-09-17 用户要求：进一步验证优化类模组的兼容性）──────────────
# 来源同样走 **Modrinth Maven**（与渲染栈/史莱姆压制同规则）：
#   maven.modrinth:immediatelyfast:adbrNJLm （1.15.3+26.1-neoforge，`environment=client_only`）
#   maven.modrinth:modernfix:j7EoxpYe       （5.27.22+mc26.1.2，`client_or_server_prefers_both`）
# ⚠️ 两者的「侧别」不同，处理必须分开：
#   · **ImmediatelyFast 是纯客户端**（即时渲染缓冲/符号图集等客户端优化）⇒ 必须进
#     `Invoke-MtEnvWorld` 的移出名单（否则专用服务器会加载客户端模组；同规则也适用于
#     两段式数据生成 runClientData/runServerData 前的手工移出）。
#   · **ModernFix 两侧皆可**（它同时优化客户端与服务端的启动/内存/资源加载）⇒ 生成世界时
#     **保留**，服务端也能拿到它的启动期优化。
# 注：1.21.1 线早已集成 ModernFix（`install_test_mods.ps1` 复制进 run/1.21.1/mods，launch 亦校验其
# 加载完成日志）；本清单是 26.1.2 线的对应实现。
$script:PerfMods2612 = @(
    # ⚠️ **ImmediatelyFast 已于 2026-09-22 从 26.1.2 移除**（用户裁决：「移除 ImmediatelyFast 模组，
    #    其不兼容 Iris 已经明确标注」）。实测证据：加入 ImmediatelyFast 后，**开启光影**的客户端在
    #    进入世界约 10 秒内必崩，崩溃报告指向 ImmediatelyFast 自己的批处理路径 ——
    #      java.lang.IllegalStateException: Missing sampler Sampler1
    #        at com.mojang.blaze3d.opengl.GlCommandEncoder.trySetup
    #        at …immediatelyfast…feature.core.BatchableBufferSource.drawDirect(BatchableBufferSource.java:178)
    #        at …MultiBufferSource$BufferSource.endBatch → RenderType.draw
    #      （`crash-2026-09-22_10.04.21-client.txt`；栈上 Iris/Sodium/ImmediatelyFast 三方 mixin 同在
    #        `GlCommandEncoder` 上，故障点是 IF 的 BatchableBufferSource 复用 RenderPass 后
    #        sampler 绑定丢失。）
    #    这与本表 1.20.1 条目记载的是**同一类互斥**（「装 ImmediatelyFast」与「光影默认启用」不可兼得；
    #    1.20.1 上的表现是模组构造期 ClassNotFoundException）。两线取舍一致 = **保光影**，
    #    因为光影兼容性测试是本项目明确要求的验证项，而 ImmediatelyFast 只是优化类附加验证。
    #    若将来要恢复：先把 `mt_env.ps1 shaders --state off` 关光影，再放开本条目。
    @{ Name = 'modernfix-neoforge-5.27.22+mc26.1.2.jar'
       Coord = 'maven.modrinth:modernfix:j7EoxpYe'
       Url  = 'https://api.modrinth.com/maven/maven/modrinth/modernfix/j7EoxpYe/modernfix-neoforge-5.27.22%2Bmc26.1.2.jar'
       Sha1 = '334500dd0c94a552005a432114fae32fe6c518fc'
       Size = 505496 }
    # FerriteCore（2026-09-17 用户要求：所有测试环境都要带上它做优化类模组兼容性验证）。
    # `environment` = client/server 皆 optional（方块状态/模型去重的内存优化），故**两侧都保留**，
    # 不进 `Invoke-MtEnvWorld` 的纯客户端移出名单。
    @{ Name = 'ferritecore-9.0.0-neoforge.jar'
       Coord = 'maven.modrinth:ferrite-core:LtVvw4uS'
       Url  = 'https://api.modrinth.com/maven/maven/modrinth/ferrite-core/LtVvw4uS/ferritecore-9.0.0-neoforge.jar'
       Sha1 = 'b8bfb14ba6ce7068aae08c3a132ca40bda6bd143'
       Size = 71947 }
)

# 注：**没有**全局的「族前缀表」—— 清理前缀由每次 `Install-MtSpecList` 调用**按族显式传入**
# （渲染栈 `sodium-`/`iris-`、史莱姆压制 `superflatworldnoslimes-`/`collective-`、
# 优化模组 `immediatelyfast-`/`modernfix-`）。旧版本清理的必要性：`Install-MtRemoteMod` 只负责
# 「放新文件」，不做「删旧文件」⇒ 换版本后新旧 jar 会**同时存在**，FML 报重复模组
# （`Duplicate mods:` / `Found duplicate mod …`）拒绝启动，看起来像「新版本装坏了」。
# 前缀带连字符，故不会误删 `reeses-sodium-options-*` 这类名字含关键字、注册 id 不同的模组。
# ⚠️ 第一版曾用一张全局前缀表，结果「装史莱姆压制」那一趟把上一趟刚装好的 Sodium/Iris 删掉了
# （每趟只知道自己的 `$installed`）—— 故清理范围必须跟着调用走，见 `Install-MtSpecList` 注释。

# ── 超平坦测试世界的「史莱姆压制」模组（2026-09-17 用户硬性要求）──────────────
# 用户原话：「测试环境强制要求加入 Superflat world no slimes 模组，否则因为超平坦世界
# 生成的史莱姆会严重干扰测试流程」。测试世界是超平坦（`mt_env world` 生成 + quickplay 直进），
# 超平坦下方 y<40 处处是史莱姆区块 ⇒ 测试期间会持续刷出史莱姆，干扰实体类断言
# （`/kill @e[type=!player]` 只在 launch 前清一次，测试过程中新刷的照样存在）。
# 来源同样走 **Modrinth Maven**（与渲染栈同规则，不使用 CDN/GitHub 直链）：
#   maven.modrinth:superflat-world-no-slimes:Onb8latt （26.1.2-3.6，`environment=server_only`）
#   maven.modrinth:collective:iXqgYZEw               （26.1.2-8.32；上者的 **required** 前置库）
# ⚠️ 两条重要性质（决定了它不能被当成「客户端模组」处理）：
#   1. 史莱姆压制模组在 Modrinth 上标为 `server_only` ⇒ **必须留在大世界生成用的专用服务器**里，
#      故 `Invoke-MtEnvWorld` 的「纯客户端模组移出」名单（imblocker/sodium/iris/embeddium/oculus）
#      **不得**加入它们（现在的匹配是子串命中，两个文件名都不含这些子串 ⇒ 天然安全）。
#   2. 它是**运行时刷怪逻辑**（取消超平坦世界的史莱姆自然生成），不是世界生成特性 ⇒ 世界已生成
#      也照样生效，无需重建世界。
$script:SlimeGuard2612 = @(
    @{ Name = 'superflatworldnoslimes-26.1.2-3.6.jar'
       Coord = 'maven.modrinth:superflat-world-no-slimes:Onb8latt'
       Url  = 'https://api.modrinth.com/maven/maven/modrinth/superflat-world-no-slimes/Onb8latt/superflatworldnoslimes-26.1.2-3.6.jar'
       Sha1 = 'd47af65db00db70a47f29390386a52290cf93481'
       Size = 28935 }
    @{ Name = 'collective-26.1.2-8.32.jar'
       Coord = 'maven.modrinth:collective:iXqgYZEw'
       Url  = 'https://api.modrinth.com/maven/maven/modrinth/collective/iXqgYZEw/collective-26.1.2-8.32.jar'
       Sha1 = '13887a5d78938c6ce55c15bcbd1352db625e84e1'
       Size = 1054031 }
)

# ── 优化类模组 / 史莱姆压制：**按版本**清单（2026-09-17 用户要求，三条线一致）──────
# 用户原话（两轮）：「1.21.1 环境缺少没有史莱姆的超平坦世界模组，这是必需的模组，没有会使史莱姆
# 干扰测试，补全该模组然后重新运行 1.21.1 测试」+「所有测试环境增加 ImmediatelyFast、FerriteCore
# 模组，用于优化模组兼容性测试」。
#
# ⚠️ 与 26.1.2 的关键差别（决定「哪条线从哪来」）：1.20.1 的 `forge-1.20.1/build.gradle` **已经**
#    用 `modImplementation` 声明了 `collective`(curse 7148968 = collective-1.20.1-8.13) 与
#    `superflat-world-no-slimes`(curse 6184996 = superflatworldnoslimes-1.20.1-3.5) —— 它们由
#    Gradle 提供 dev 运行时，**再往 `run/1.20.1/mods` 放一份就会被 FML 判为重复模组**
#    （`Duplicate mods:` / `Found duplicate mod`，表现为「装完反而起不来」）。
#    ⇒ 1.20.1 的史莱姆压制**故意不在本表里**（`$script:SlimeGuardByVersion` 无该键 = 跳过安装，
#      并在 `Install-MtSlimeGuard` 里打印来源说明）。
#    ImmediatelyFast / FerriteCore：1.21.1 与 26.1.2 由本表下载进 `run/<版本>/mods`（这两条线的 dev
#    运行能直接加载生产 jar）；**1.20.1 例外** —— 它由 `forge-1.20.1/build.gradle` 的 `modImplementation`
#    提供（生产 SRG jar 必须经 MDG 重映射，详见该表 1.20.1 键的注释）。
$script:PerfModsByVersion = @{
    # 1.20.1 **故意为空**（2026-09-17 实测，三个独立结论）：
    #   ① 来源限制（对**两个**模组都成立）：Modrinth/CF 提供的是**生产(SRG)字节码**的 Forge 1.20.1
    #      jar，而 dev 环境是 Mojmap 命名 —— 手工放进 run/mods **不会被重映射**，FerriteCore 6.0.1
    #      实测在 vanilla `Bootstrap.bootStrap` 阶段直接 `NoSuchMethodError: 'it.unimi.dsi.fastutil.
    #      Hash$Strategy net.minecraft.Util.m_137583_()'`（`malte0811.ferritecore.fastmap.PropertyIndexer.<clinit>`）。
    #      这与「Embeddium/Oculus 无法进 1.20.1 dev run」是同一类限制。
    #   ② FerriteCore ⇒ **改由 build.gradle 的 `modImplementation` 提供**（MDG 解析期重映射，与
    #      collective/superflat/JEI 同一机制），实测正常加载（`FERRITECORE_LOADED=true`）。
    #   ③ ImmediatelyFast ⇒ **1.20.1 上无解，不是重映射问题**（2026-09-17 javap 取证的硬结论，
    #      1.2.3 / 1.2.4 两个 1.20.1 构建 + 1.5.5+1.20.4 全部复测）：见 build.gradle 里那段注释 ——
    #      IF 1.2.x 的 `IrisCompat.init()` 只认 **Iris 1.6 的包名** `net.coderbot.iris.*`，而 1.20.1 的
    #      光影加载器 Oculus 1.8.0 基于 Iris 1.7+（包名 `net.irisshaders.iris`）⇒ 它在**模组构造期**
    #      `ClassNotFoundException` 直接终止客户端（`runClient` exit -1）。两个 User 硬性要求
    #      （「光影包默认启用」与「装 ImmediatelyFast」）在 1.20.1 上互斥，当前取舍 = 保光影。
    #   ⇒ 本表 1.20.1 为空；该函数在该版本只做「跳过下载 + 清理 run/mods 里的历史副本」。
    '1.20.1' = @()
    '1.21.1' = @(
        @{ Name = 'ImmediatelyFast-NeoForge-1.6.14+1.21.1.jar'
           Coord = 'maven.modrinth:immediatelyfast:OUpXxw4n'
           Url  = 'https://api.modrinth.com/maven/maven/modrinth/immediatelyfast/OUpXxw4n/ImmediatelyFast-NeoForge-1.6.14%2B1.21.1.jar'
           Sha1 = 'fee59af2f39c66d09c4c09f441c799e76af70f97'
           Size = 365195 }
        @{ Name = 'ferritecore-7.0.3-neoforge.jar'
           Coord = 'maven.modrinth:ferrite-core:x7kQWVju'
           Url  = 'https://api.modrinth.com/maven/maven/modrinth/ferrite-core/x7kQWVju/ferritecore-7.0.3-neoforge.jar'
           Sha1 = '9563692efb708b6b568df27a01ec52f6311928ef'
           Size = 121559 }
    )
    # 26.1.2 沿用原清单（ImmediatelyFast + ModernFix），已在上面补入 FerriteCore
    '26.1.2' = $script:PerfMods2612
}

# 清理前缀**跟着版本走**（不是全局表）：1.21.1/1.20.1 的 ModernFix 由整合包复制（`$script:NeoForgeMods`），
# 若在这里也用 `modernfix-` 前缀清理，会把复制来的那份删掉、再被复制回来 —— 徒增抖动，
# 故这两条线的优化模组前缀**只含** immediatelyfast- / ferritecore-。
$script:PerfPrefixesByVersion = @{
    '1.20.1' = @('immediatelyfast-', 'ferritecore-')
    '1.21.1' = @('immediatelyfast-', 'ferritecore-')
    '26.1.2' = @('immediatelyfast-', 'modernfix-', 'ferritecore-')
}

$script:SlimeGuardByVersion = @{
    '1.21.1' = @(
        # 用户硬性要求：1.21.1 测试环境此前**缺**这个模组（2026-09-17 实测 run/1.21.1/mods 里没有），
        # 超平坦世界 y<40 的史莱姆区块会持续刷怪污染实体类读数。
        @{ Name = 'superflatworldnoslimes-1.21.1-3.5.jar'
           Coord = 'maven.modrinth:superflat-world-no-slimes:5VtNIDJA'
           Url  = 'https://api.modrinth.com/maven/maven/modrinth/superflat-world-no-slimes/5VtNIDJA/superflatworldnoslimes-1.21.1-3.5.jar'
           Sha1 = '9989735bf3518c4e16f1c3df1e83c43b24179d18'
           Size = 25651 }
        @{ Name = 'collective-1.21.1-8.39.jar'
           Coord = 'maven.modrinth:collective:4XRlrKGN'
           Url  = 'https://api.modrinth.com/maven/maven/modrinth/collective/4XRlrKGN/collective-1.21.1-8.39.jar'
           Sha1 = 'b1153f03c97bccaa6bc11d6199f07f16ef4318ff'
           Size = 1061931 }
    )
    # 1.20.1 **故意缺省**：已由 `forge-1.20.1/build.gradle` 的 modImplementation 提供（见上）
    '26.1.2' = $script:SlimeGuard2612
}

# ── Carpet: NeoForged（玩家 bot：`/player <name> spawn` / `/player <name> kill`）──────────
# 用户要求（2026-09-27 原话）：「向 1.21.1 和 1.20.1 测试端插入 Carpet: NeoForged 模组（Fabric
# Carpet 的 Forge/NeoForge 移植，https://github.com/chililisoup/neoforge-carpet）……在测试脚本中，
# 插入 /player 命令用于创建玩家 bot，该 bot 可被用作测试 2 人及更多玩家的联动功能；使用 /kill
# 命令击杀 bot 玩家可使其退出（建议阅读源代码以掌握该模组全部指令）。」
# 用途：单人测试世界此前**没有第二名真实玩家** ⇒ teru 降神的「另一名玩家」只能用 FakePlayer/
# 自身脚手架（见探针 teru 段注释）；Carpet 的 bot 是**真 ServerPlayer**（在玩家列表里、参与 tick、
# 可被攻击/被选为目标、死亡走 disconnect），因此能覆盖「真实双人联动」「目标中途离线的链接自愈」。
# 来源：**Modrinth Maven**（`maven.modrinth:neoforge-carpet:<versionId>`，project_id = XqqOkvZz），
# 与渲染栈/史莱姆压制同规则（不走 CDN/GitHub 直链）。
# 侧别：`environment = server_only_client_optional` ⇒ 专用服务器（生成世界）与客户端都可保留
# （`/player` 是服务端命令，集成服务器必须装载；客户端侧只有 Carpet 的 GUI 可选）。文件名不含
# imblocker/sodium/iris/embeddium/oculus 子串 ⇒ 不会被 `Invoke-MtEnvWorld` 的纯客户端移出名单误删。
#
# ⚠️ 两条线的**装配机制不同**，不要照抄同一套：
#   · **1.21.1 → 本表**（放进 `run/1.21.1/mods`）：NeoForge 1.21.1 无 reobf，生产 jar 本身就是
#     Mojmap 命名，dev run 可直接加载（与 ImmediatelyFast/FerriteCore/史莱姆压制同一机制）。
#   · **1.20.1 → 故意不在本表**：Forge 1.20.1 的生产 jar 是 **SRG 字节码 + SRG refmap**，手工放进
#     `run/1.20.1/mods` 既不会被重映射、refmap 也不会被改写（mixin 静默失效，见本仓既有取证：
#     `NoSuchMethodError … Util.m_137583_()`）⇒ 改由 `forge-1.20.1/build.gradle` 的
#     `modImplementation` 提供（MDG 解析期重映射，与 KubeJS/JEI/collective/superflat/FerriteCore
#     同一机制）；再往 run/mods 放一份会被 FML 判「重复模组」。
#   · **26.1.2 → Sinytra Connector 栈**（2026-09-22 用户改定，覆盖 2026-09-27 的「不使用」裁决）：
#     该线原裁决「用 Mojang 自带 bot 管理指令」经查**不成立** —— 26.1.2 原版没有任何 player/bot
#     管理命令（逐条核对 net/minecraft/server/commands/ 80 个文件）。Carpet 官方只有 Fabric 构建，
#     故本线改为「Connector + Launchpad + Forgified Fabric API + fabric-carpet」四件套
#     （清单见 `$script:ConnectorStack2612`）。⚠️ 该栈是 beta 且侵入，风险与复验要求见该表上方注释。
$script:CarpetByVersion = @{
    '1.21.1' = @(
        @{ Name = 'neoforge-carpet-1.21.1-1.0.8+v251027.jar'
           Coord = 'maven.modrinth:neoforge-carpet:lnOeoKcQ'
           Url  = 'https://api.modrinth.com/maven/maven/modrinth/neoforge-carpet/lnOeoKcQ/neoforge-carpet-1.21.1-1.0.8%2Bv251027.jar'
           Sha1 = 'fffcc899d13b25c808d4d906cafa41de8cffc861'
           Size = 1498683 }
    )
    # 26.1.2:Fabric 版 Carpet 本体（其三个前置在 ConnectorStack2612 里,由 Install-MtCarpet 一并装）
    '26.1.2' = @(
        @{ Name = 'fabric-carpet-26.1+v260402.jar'
           Coord = 'maven.modrinth:carpet:26.1'
           Url  = 'https://api.modrinth.com/maven/maven/modrinth/carpet/26.1/fabric-carpet-26.1+v260402.jar'
           Sha1 = 'f786a53c97e7caaa5b34c94309e79ed1201c0114'
           Size = 1535777 }
    )
}

# ── 26.1.2 · Sinytra Connector 栈（2026-09-22 用户要求：让 **Fabric** 版 Carpet 能在
#    NeoForge 26.1.2 上跑，从而启用 12 条「需第二玩家」的用例）────────────────────
# 背景：26.1.2 原版**没有** bot 管理命令（已逐条核对
#   `minecraft-patched-26.1.2.109-sources.jar` 的 net/minecraft/server/commands/ 全部 80 个文件，
#   无 player/bot 命令；全库只有 NeoForge 的 FakePlayer，且它**不进 PlayerList**
#   ⇒ 探针「按名字取玩家」的路径全部失效）。因此第二玩家必须由**测试环境模组**提供。
# Carpet 官方只有 **Fabric** 构建（Modrinth `loaders: ['fabric']`），要落在 NeoForge 上
# 只能经 Sinytra Connector 转发。
#
# 四件套（全部走 **Modrinth Maven**，与 Sodium/Iris 同源、同口径；均实测 307 可达）：
#   1) Launchpad        —— Sinytra 的类加载/Mixin 基础设施，Connector 的**必需**前置；
#   2) Connector        —— Fabric 模组 → NeoForge 的转译层（本版为 beta，见下方风险提示）；
#   3) Forgified Fabric API —— Fabric API 在 NeoForge 上的实现，Carpet 的**必需**前置
#                             （Connector 元数据里 `Aqlf1Shp` = 本项，dependency_type=required）；
#   4) fabric-carpet    —— Carpet 本体，直接放 run/mods（它是 Fabric 模组，不经 Gradle 依赖）。
#
# ⚠️ 已知风险（必须在测试报告里如实登记，不能当成「环境已就绪」直接给结论）：
#   · Connector 在本版是 **3.0.0-beta.6**（beta）；它做的是**类加载期转译 + Mixin 重映射**，
#     属侵入式改造，可能与被测模组、或与 Sodium/Iris/ModernFix/FerriteCore 相互干扰；
#   · 因此**加入 Connector 后必须重新验证此前已通过的启动闸门**
#     （NOAI / KUBEJS / OP / 清场 / immediate_respawn），任一闸门回归都说明该栈不可用；
#   · Carpet 是 Fabric 模组 ⇒ 只保证「在 Connector 转发下能加载并被 `/player` 驱动」，
#     不保证与 Fabric 原环境的完全一致。
# 版本锁定依据：Modrinth API `game_versions` 含 "26.1.2"（2026-09-22 实查）。
$script:ConnectorStack2612 = @(
    @{ Name = 'launchpad-1.9.2+26.1.2-full.jar'
       Coord = 'maven.modrinth:launchpad:1.9.2+26.1.2'
       Url  = 'https://api.modrinth.com/maven/maven/modrinth/launchpad/1.9.2+26.1.2/launchpad-1.9.2+26.1.2-full.jar'
       Sha1 = '2f4514d1980c735bde499bc25760fae45f44448e'
       Size = 466664 }
    @{ Name = 'connector-3.0.0-beta.6+26.1.2-full.jar'
       Coord = 'maven.modrinth:connector:3.0.0-beta.6+26.1.2'
       Url  = 'https://api.modrinth.com/maven/maven/modrinth/connector/3.0.0-beta.6+26.1.2/connector-3.0.0-beta.6+26.1.2-full.jar'
       Sha1 = 'be19358e16a22ad6174075e6f5b0f78fd1b36e12'
       Size = 1011536 }
    @{ Name = 'forgified-fabric-api-0.155.2+26.1.2+3.5.5.jar'
       Coord = 'maven.modrinth:forgified-fabric-api:0.155.2+26.1.2+3.5.5'
       Url  = 'https://api.modrinth.com/maven/maven/modrinth/forgified-fabric-api/0.155.2+26.1.2+3.5.5/forgified-fabric-api-0.155.2+26.1.2+3.5.5.jar'
       Sha1 = '35ced241d822f055ff9b31eec9fd4b3fa0d5c9f4'
       Size = 2972661 }
)
$script:ConnectorStackPrefixes2612 = @('launchpad-', 'connector-', 'forgified-fabric-api-')

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

# ══ 26.1.2：gamerule 独立文件（level.dat 不再是存储位置）════════════════════
#
# 背景（2026-09-19 本机实测 + 反编译源码双证据）:
#   MC 26.1 起 gamerule 整体搬出 level.dat，改由 SavedData 承载:
#     · 路径  <世界目录>/data/minecraft/game_rules.dat   （gzip 压缩的 NBT）
#     · 负载  { data: { "minecraft:keep_inventory": 0b, … }, DataVersion: <int> }
#     · 键名  蛇形 + `minecraft:` 命名空间前缀；布尔值是 **TAG_Byte**(0/1)，不是旧版 TAG_String
#   实测（run/26.1.2/saves/testworld/data/minecraft/game_rules.dat，解压后 2000 字节）:
#     minecraft:keep_inventory = 0b(false)  ← 与 AGENTS「测试世界必须死亡不掉落」冲突
#     minecraft:natural_health_regeneration = 0b、mob_griefing = 1b、spawn_mobs = 1b、
#     immediate_respawn = 0b、fire_spread_radius_around_player = 128(TAG_Int)
#   同一世界的 level.dat 里**没有** GameRules 键：`Set-MtKeepInventory` 写进去的那份既没有
#   任何读者（26.1.2 只从**文件**读规则），又会在游戏自己保存 level.dat 时被丢弃；且 26.1.2 的
#   file fix（LevelDatToSavedDataFileFix）只认 level.dat 的 `game_rules` 键，**不认** `GameRules`。
#   ⇒ 在该版本上旧写法等于「写入无人读的数据 + 拿自己刚写的数据自我复核」= **假通过**。
#
#   源码依据（neoforge-26.1.2/build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar）:
#     · GameRuleMap.TYPE = new SavedDataType(Identifier.withDefaultNamespace("game_rules"), …)
#     · SavedDataStorage#getDataFile  → `<dataFolder>/<namespace>/<path>.dat`
#     · SavedDataStorage#encodeUnchecked → `tag.put("data", payload)` + addCurrentDataVersion
#     · SavedDataStorage#readSavedData   → 只读 `data` 负载；文件缺失 ⇒ GameRuleMap.of()（空表）
#     · GameRules 构造器对**缺失的规则**调用 GameRuleMap#reset(默认值)
#       ⇒ 文件里少写的键由游戏补默认值（不会 NPE），且 reset→set→setDirty() 会让游戏下次保存
#         把整份规则写全（既有非默认值不会因此丢失）。
#   ⇒ 只需精确改写 `minecraft:keep_inventory` 一个键，其余既有键**逐键原样保留**。
#
#   ⚠️ 只有 26.1.2 走这条路：1.20.1 / 1.21.1 的 gamerule 仍在 level.dat（TAG_String），
#      它们的产物与行为由下面的 `$Version -ne $script:MtGameRuleFileVersion` 早退保证不变。
$script:MtGameRuleFileVersion = '26.1.2'
$script:MtKeepInventoryRuleKey = 'minecraft:keep_inventory'

function Get-MtGameRulesFile {
    <#
    .SYNOPSIS
        26.1.2 的 gamerule 存储文件：<世界目录>/data/minecraft/game_rules.dat。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$WorldDir)

    return (Join-Path (Join-Path (Join-Path $WorldDir 'data') 'minecraft') 'game_rules.dat')
}

function Read-MtGameRuleKeepInventoryFile {
    <#
    .SYNOPSIS
        从 game_rules.dat **真实读回** minecraft:keep_inventory 的值。
    .OUTPUTS
        $null = 文件不存在 / 不可解析 / 无该键 / 类型不是 TAG_Byte；否则 [int] 0 或 1。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$WorldDir)

    $file = Get-MtGameRulesFile -WorldDir $WorldDir
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { return $null }
    try { $nbt = Read-MtNbt -Path $file } catch { return $null }
    if ($nbt.TagId -ne $script:TAG_COMPOUND -or -not $nbt.Payload.Contains('data')) { return $null }
    $data = $nbt.Payload['data'][1]
    if (-not ($data -is [System.Collections.IDictionary])) { return $null }
    if (-not $data.Contains($script:MtKeepInventoryRuleKey)) { return $null }
    $pair = $data[$script:MtKeepInventoryRuleKey]
    if ([int]$pair[0] -ne $script:TAG_BYTE) { return $null }
    return [int][sbyte]$pair[1]
}

function Test-MtGameRuleKeepInventoryFile {
    <#
    .SYNOPSIS
        26.1.2 的 keepInventory 硬闸门：game_rules.dat 里 minecraft:keep_inventory 必须读回 1。
    .NOTES
        刻意重新从磁盘解析（不复用刚写进内存的对象）—— 这正是旧实现假通过的根因。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$WorldDir)

    return ((Read-MtGameRuleKeepInventoryFile -WorldDir $WorldDir) -eq 1)
}

function Read-MtLevelDataVersion {
    <#
    .SYNOPSIS
        读 level.dat 的 Data.DataVersion（新建 game_rules.dat 时用作其 DataVersion）。
    .OUTPUTS
        [int] 版本号；读不到返回 0。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$LevelDat)

    try {
        $nbt = Read-MtNbt -Path $LevelDat
        if ($nbt.TagId -eq $script:TAG_COMPOUND -and $nbt.Payload.Contains('Data')) {
            $data = $nbt.Payload['Data'][1]
            if (($data -is [System.Collections.IDictionary]) -and $data.Contains('DataVersion')) {
                return [int]$data['DataVersion'][1]
            }
        }
    } catch { return 0 }
    return 0
}

function Set-MtGameRuleKeepInventoryFile {
    <#
    .SYNOPSIS
        26.1.2：把 <世界>/data/minecraft/game_rules.dat 的 minecraft:keep_inventory 置为 1b。

    .DESCRIPTION
        只改这一个键，其余既有规则（natural_health_regeneration / mob_griefing / …）逐键原样保留；
        不整份重建、不引入任何第三方依赖（复用文件顶部的 New-MtPair / Read-MtNbt / Write-MtNbt）。
        文件不存在时按 vanilla 形态新建最小文件 { data: {…}, DataVersion }（缺失键由游戏补默认值）。
        已经是 1 时**不重写**（幂等：不制造 mtime 抖动，也不给并发读方留半截文件的窗口）。
        对已存在的世界同样有效（把 0 纠正为 1）。

    .OUTPUTS
        $true = 已就位或写入成功；$false = 机制性失败（调用方负责 BLOCKED）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$WorldDir,
        [Parameter(Mandatory)][string]$LevelDat
    )

    $file = Get-MtGameRulesFile -WorldDir $WorldDir

    # 世界目录都不存在时**绝不**代为创建（否则会凭空造出一个假世界目录）；
    # 调用链上 Set-MtKeepInventory 已先要求 level.dat 存在，这里是显式兜底。
    if (-not (Test-Path -LiteralPath $WorldDir -PathType Container)) {
        Write-MtErrorLine "世界目录不存在，拒绝创建 game_rules.dat：$WorldDir"
        return $false
    }

    # 已就位 ⇒ 幂等返回，不重写
    if ((Read-MtGameRuleKeepInventoryFile -WorldDir $WorldDir) -eq 1) { return $true }

    $payload = $null
    $rootName = ''
    if (Test-Path -LiteralPath $file -PathType Leaf) {
        try { $nbt = Read-MtNbt -Path $file } catch {
            Write-MtErrorLine "解析 game_rules.dat 失败：$($_.Exception.Message)"
            return $false
        }
        if ($nbt.TagId -ne $script:TAG_COMPOUND -or -not ($nbt.Payload -is [System.Collections.IDictionary])) {
            Write-MtErrorLine 'game_rules.dat 结构异常（根不是复合标签）'
            return $false
        }
        $payload = $nbt.Payload
        $rootName = $nbt.Name
    } else {
        # 世界已建但该文件还没落盘（首次保存前）⇒ 按 vanilla 形态新建，键数最小
        $payload = [System.Collections.Specialized.OrderedDictionary]::new([System.StringComparer]::Ordinal)
    }

    $data = $null
    if ($payload.Contains('data')) {
        $cand = $payload['data'][1]
        if ($cand -is [System.Collections.IDictionary]) { $data = $cand }
    }
    if ($null -eq $data) {
        $data = [System.Collections.Specialized.OrderedDictionary]::new([System.StringComparer]::Ordinal)
        $payload['data'] = (New-MtPair $script:TAG_COMPOUND $data)
    }

    $data[$script:MtKeepInventoryRuleKey] = (New-MtPair $script:TAG_BYTE 1)

    # DataVersion：沿用文件里已有的（保证数据修复链是 no-op），缺失才从 level.dat 取
    if (-not $payload.Contains('DataVersion')) {
        $dv = Read-MtLevelDataVersion -LevelDat $LevelDat
        if ($dv -gt 0) {
            $payload['DataVersion'] = (New-MtPair $script:TAG_INT $dv)
        } else {
            Write-MtErrorLine '警告：无法确定 DataVersion（level.dat 里读不到），game_rules.dat 将不带该键'
        }
    }

    $dir = Split-Path -Parent $file
    if (-not (Test-Path -LiteralPath $dir -PathType Container)) {
        [void](New-Item -ItemType Directory -Force -Path $dir)
    }

    try {
        Write-MtNbt -Path $file -TagId $script:TAG_COMPOUND -Name $rootName -Payload $payload
    } catch {
        Write-MtErrorLine "写回 game_rules.dat 失败：$($_.Exception.Message)"
        return $false
    }
    return $true
}

function Set-MtWorldKeepInventory {
    <#
    .SYNOPSIS
        落地并**读回复核**「测试世界必须 keepInventory=true」，供种子快恢复与世界重建两条路径复用。

    .OUTPUTS
        $null = 已就位且复核通过；否则返回应接在 `MT_WORLD: BLOCKED — ` 之后的**原因文案**。
    .NOTES
        ⚠️ 1.20.1 / 1.21.1 走 `$Version -ne $script:MtGameRuleFileVersion` 早退：只调用原有的
        `Set-MtKeepInventory`（level.dat，TAG_String），原因文案与被调用的写函数均未变 ⇒
        这两个版本的成功/失败输出**逐字节不变**，也不产生新文件。
        26.1.2 追加独立文件的写入 + 从磁盘读回复核（见 Set-MtGameRuleKeepInventoryFile）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [Parameter(Mandatory)][psobject]$Paths
    )

    $level = Join-Path $Paths.client_world 'level.dat'
    if (-not (Set-MtKeepInventory -LevelDat $level)) {
        return 'level.dat 的 GameRules.keepInventory 未能设置'
    }
    if ($Version -ne $script:MtGameRuleFileVersion) { return $null }

    # ── 26.1.2：真正的存储在独立文件里 ──────────────────────────────────────
    $rulesFile = Get-MtGameRulesFile -WorldDir $Paths.client_world
    if (-not (Set-MtGameRuleKeepInventoryFile -WorldDir $Paths.client_world -LevelDat $level)) {
        return ("game_rules.dat 的 {0} 未能写入（{1}）" -f $script:MtKeepInventoryRuleKey, $rulesFile)
    }
    $readBack = Read-MtGameRuleKeepInventoryFile -WorldDir $Paths.client_world
    if ($readBack -ne 1) {
        $shown = '读不到（文件缺失 / 无该键 / 类型不符）'
        if ($null -ne $readBack) { $shown = "$readBack" }
        return ("game_rules.dat 的 {0} 读回复核不为 1b：{1}（{2}）" -f $script:MtKeepInventoryRuleKey, $shown, $rulesFile)
    }
    Write-MtLine ("MT_WORLD: keepInventory 落地于 {0}（{1}=1b，读回复核通过）" -f $rulesFile, $script:MtKeepInventoryRuleKey)
    return $null
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
    #    2026-09-22 扩展:`client_scripts` / `startup_scripts` **同样纳入清理** —— 客户端
    #    探针(`resources/kubejs/<版本>/client_scripts/**`,用于读客户端侧状态)加入后若不清理,
    #    残留的旧客户端脚本会与模板并行注册同一批客户端事件,读数来源就不唯一了(与上面
    #    服务端那次的失效形态同源)。只遍历这三个**脚本目录**,不碰 KubeJS 自有的
    #    config/ data/ assets/ logs/ exported/。
    foreach ($sub in @('server_scripts', 'client_scripts', 'startup_scripts')) {
        $dstMeta = Join-Path $dstRoot $sub
        if (-not (Test-Path -LiteralPath $dstMeta -PathType Container)) { continue }
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
function Install-MtRemoteMod {
    <#
    .SYNOPSIS
        下载/校验/落位**单个** Modrinth Maven 模组规格（幂等：尺寸 + sha1 命中即跳过）。

    .NOTES
        从 `Install-MtRenderStack` 的内联循环提取（2026-09-17），供渲染栈与史莱姆压制模组共用 ——
        两处的语义必须完全一致：缓存在 `temp/probe_mods/<版本>/`，命中判据 = 目标文件存在且**尺寸一致**
        （有 sha1 时再校验 sha1）；下载走 `<缓存>.part` 再原子改名；任何尺寸/sha1 不符都**硬报 14**，
        不静默降级。返回 0 = 就位，14 = 下载或校验失败。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][psobject]$Paths,
        [Parameter(Mandatory)]$Spec,
        [Parameter(Mandatory)][string]$Cache,
        [string]$Label = '模组'
    )

    $cached = Join-Path $Cache $Spec.Name
    $ok = $false
    if (Test-Path -LiteralPath $cached -PathType Leaf) {
        $ci = Get-Item -LiteralPath $cached
        $ok = ($ci.Length -eq $Spec.Size)
        if ($ok -and $Spec.Sha1) {
            $ok = ((Get-FileHash -LiteralPath $cached -Algorithm SHA1).Hash.ToLowerInvariant() -eq $Spec.Sha1)
        }
        if (-not $ok) { Remove-Item -LiteralPath $cached -Force -ErrorAction SilentlyContinue }
    }
    if (-not $ok) {
        try {
            Write-MtLine "MT_MODS: 下载$Label $($Spec.Name) ← $($Spec.Coord)"
            Invoke-WebRequest -Uri $Spec.Url -OutFile "$cached.part" -TimeoutSec 300 -UseBasicParsing
            Move-Item -LiteralPath "$cached.part" -Destination $cached -Force
        } catch {
            Remove-Item -LiteralPath "$cached.part" -Force -ErrorAction SilentlyContinue
            Write-MtLine "MT_MODS: BLOCKED — $Label下载失败 [Modrinth Maven] $($Spec.Coord) $($Spec.Url) :: $($_.Exception.Message)"
            return 14
        }
        $got = Get-Item -LiteralPath $cached
        if ($got.Length -ne $Spec.Size) {
            Write-MtLine "MT_MODS: BLOCKED — $($Spec.Name) 尺寸不符（期望 $($Spec.Size)，实得 $($got.Length)）"
            return 14
        }
        if ($Spec.Sha1 -and ((Get-FileHash -LiteralPath $cached -Algorithm SHA1).Hash.ToLowerInvariant() -ne $Spec.Sha1)) {
            Write-MtLine "MT_MODS: BLOCKED — $($Spec.Name) sha1 校验失败"
            return 14
        }
    }
    $dst = Join-Path $Paths.mods_dir $Spec.Name
    $needCopy = $true
    if (Test-Path -LiteralPath $dst -PathType Leaf) {
        if ((Get-Item -LiteralPath $dst).Length -eq (Get-Item -LiteralPath $cached).Length) { $needCopy = $false }
    }
    if ($needCopy) { Copy-Item -LiteralPath $cached -Destination $dst -Force }
    return 0
}

function Install-MtSpecList {
    <#
    .SYNOPSIS
        按规格清单逐个下载/校验/落位（`Install-MtRemoteMod` 的批量包装），并按 `-Prefixes` 清掉**同族旧版本**。

    .NOTES
        为什么把「装 + 清」绑在一起（2026-09-17）：只装不清会让新旧版本 jar 并存 ⇒ FML 判重复模组
        拒绝启动；调用方若忘了清理就会得到「看起来像新版本装坏了」的假象。

        ⚠️ **清理范围必须由调用方按族显式给出（`-Prefixes`），不得用「全局族前缀表」** ——
        2026-09-17 实测踩坑：第一版用全局前缀表，于是「装史莱姆压制」那一趟把**上一趟刚装好的
        Sodium/Iris 当成不在本次规格里的文件删掉了**（每趟调用只知道自己的 `$installed`），
        结果 run/mods 里渲染栈凭空消失，看起来像下载失败。按族传入前缀后，各趟互不干扰。

        前缀用大小写不敏感 `-like "$pre*"` 匹配（`ImmediatelyFast-…` 要能被 `immediatelyfast-` 命中），
        且带连字符，故不会误删 `reeses-sodium-options-*` 这类名字里含关键字、注册 id 不同的模组。
        返回 0 = 全部就位，其它 = `Install-MtRemoteMod` 的错误码（14 = 下载/校验失败，直接透传）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][psobject]$Paths,
        [Parameter(Mandatory)]$Specs,
        [Parameter(Mandatory)][string]$Cache,
        [Parameter(Mandatory)][string]$Label,
        [string[]]$Prefixes = @()
    )

    $installed = @()
    foreach ($spec in $Specs) {
        $rc = Install-MtRemoteMod -Paths $Paths -Spec $spec -Cache $Cache -Label $Label
        if ($rc -ne 0) { return $rc }
        $installed += $spec.Name
    }
    if ($Prefixes.Count -gt 0) {
        foreach ($f in @(Get-ChildItem -LiteralPath $Paths.mods_dir -File -Filter '*.jar')) {
            $isFamily = $false
            foreach ($pre in $Prefixes) {
                if ($f.Name -like "$pre*") { $isFamily = $true; break }
            }
            if (-not $isFamily) { continue }
            if ($installed -contains $f.Name) { continue }
            Remove-Item -LiteralPath $f.FullName -Force -ErrorAction SilentlyContinue
            Write-MtLine ("MT_MODS: 清理旧版本{0} {1}" -f $Label, $f.Name)
        }
    }
    return 0
}

function Install-MtProbeHost {
    <#
    .SYNOPSIS
        把 1.21.1 的探针宿主（KubeJS + Rhino + Architectury API）从整合包复制进 `run/1.21.1/mods`。

    .NOTES
        · 为什么必须由工具链来做：`neoforge-1.21.1/build.gradle` 故意不依赖 KubeJS（会让 datagen
          也装载它），约定「dev run 直接从 run/mods 装载整合包同版 kubejs」——此前只靠**手工**复制，
          新工作树必然缺（2026-09-17 在 dev-next 上实测：launch 的 MT_ASSERT_KUBEJS=BLOCKED）。
        · 只对 1.21.1 生效（1.20.1 的 KubeJS 走 build.gradle；26.1.2 走 Install-MtProbeRuntime 下载）。
        · 判据 = 目标文件存在、尺寸一致、且不旧于整合包那份（与 `$script:NeoForgeMods` 同一口径）；
          整合包那份缺失即 BLOCKED（返回 11），不静默降级 —— 缺 KubeJS 时所有探针用例都跑不起来。
        · 文件名里的中文方括号前缀（整合包的 `[犀牛] rhino-….jar`）在落位时剥掉，保持 run/mods 纯 ASCII。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    if ($Paths.version -ne '1.21.1') { return 0 }
    if (-not (Test-Path -LiteralPath $Paths.pack_mods_dir -PathType Container)) {
        Write-MtLine "MT_MODS: BLOCKED — 整合包目录不存在 $($Paths.pack_mods_dir)"
        return 11
    }

    $copied = @(); $kept = @(); $missing = @()
    foreach ($spec in $script:ProbeHostMods1211) {
        $src = @(Get-ChildItem -LiteralPath $Paths.pack_mods_dir -File -Filter $spec.Pattern |
                Where-Object { ($_.Name -replace '^\[[^\]]+\]\s*', '') -match $spec.Match } |
                Sort-Object Name | Select-Object -First 1)
        if ($src.Count -eq 0) { $missing += $spec.Pattern; continue }

        $name = $src[0].Name -replace '^\[[^\]]+\]\s*', ''
        $dst = Join-Path $Paths.mods_dir $name
        if (Test-Path -LiteralPath $dst -PathType Leaf) {
            $di = Get-Item -LiteralPath $dst
            if ($di.Length -eq $src[0].Length -and $di.LastWriteTimeUtc -ge $src[0].LastWriteTimeUtc) { $kept += $name; continue }
        }
        Copy-Item -LiteralPath $src[0].FullName -Destination $dst -Force
        $copied += $name
    }

    if ($missing.Count -gt 0) {
        Write-MtLine "MT_MODS: BLOCKED — 整合包缺少 1.21.1 探针宿主 $($missing -join ', ')（KubeJS 是探针宿主，缺它所有探针用例与 MT_preflight-op 都会失败）"
        return 11
    }
    $detail = if ($copied.Count -gt 0) { "新装 $($copied.Count) 个" } else { '已是最新' }
    Write-MtLine ("MT_MODS: OK — 1.21.1 探针宿主 $detail（{0}）" -f (@($copied) + @($kept) -join ' / '))
    return 0
}

function Install-MtPerfMods {
    <#
    .SYNOPSIS
        装优化类模组（ImmediatelyFast + ModernFix）到 run/<版本>/mods（幂等）。

    .NOTES
        用户要求（2026-09-17）：「所有测试环境增加 ImmediatelyFast、FerriteCore 模组，用于优化模组
        兼容性测试」（26.1.2 侧此前已装 ImmediatelyFast + ModernFix，本轮补 FerriteCore）。
        清单与来源见 `$script:PerfModsByVersion`；侧别差异：ImmediatelyFast 纯客户端（生成世界时移出）、
        FerriteCore 与 ModernFix 两侧皆可（保留）。
        ⚠️ **1.20.1 例外（本表该版本为空数组）**：FerriteCore 由 `forge-1.20.1/build.gradle` 的
        `modImplementation` 提供（生产 SRG jar 必须在 MDG 解析期重映射，手工放 run/mods 会
        `NoSuchMethodError … Util.m_137583_()`）；**ImmediatelyFast 在 1.20.1 上装不了**（IF 1.2.x 只认
        Iris 1.6 的 `net.coderbot.iris` 包名，与 Oculus 1.8 = Iris 1.7+ 冲突，模组构造期即
        `ClassNotFoundException` 终止客户端）⇒ 本函数对该版本只**清理历史副本**并回显来源，不下载。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $specs = $script:PerfModsByVersion[$Paths.version]
    if (-not $specs -or @($specs).Count -eq 0) {
        if ($Paths.version -eq '1.20.1') {
            Write-MtLine 'MT_MODS: SKIP — 1.20.1 的 FerriteCore 由 build.gradle 的 modImplementation 提供（6.0.1-forge；生产 SRG jar 必须经 MDG 重映射）；ImmediatelyFast 在 1.20.1 上无解（IF 1.2.x 只认 Iris 1.6 包名，与 Oculus 1.8 冲突，见 build.gradle 注释）'
            # 清理历史上被本函数下载进来的副本：classpath 上已由 Gradle 提供同一模组，
            # run/mods 再放一份会被 FML 判「重复模组」而拒绝启动。
            if (Test-Path -LiteralPath $Paths.mods_dir -PathType Container) {
                foreach ($pat in @('immediatelyfast-*', 'ferritecore-*')) {
                    foreach ($f in @(Get-ChildItem -LiteralPath $Paths.mods_dir -File -Filter $pat -ErrorAction SilentlyContinue)) {
                        Remove-Item -LiteralPath $f.FullName -Force -ErrorAction SilentlyContinue
                        if (-not (Test-Path -LiteralPath $f.FullName)) { Write-MtLine ("MT_MODS: 清理历史副本 {0}（改为 Gradle modImplementation 提供）" -f $f.Name) }
                    }
                }
            }
        }
        return 0
    }

    $cache = Join-Path (Join-Path (Get-MtRoot) 'temp\probe_mods') $Paths.version
    [void](New-Item -ItemType Directory -Force -Path $cache)
    [void](New-Item -ItemType Directory -Force -Path $Paths.mods_dir)

    $prefixes = $script:PerfPrefixesByVersion[$Paths.version]
    $rc = Install-MtSpecList -Paths $Paths -Specs $specs -Cache $cache -Label '优化模组' -Prefixes $prefixes
    if ($rc -ne 0) { return $rc }
    $names = ($specs | ForEach-Object { $_.Name }) -join ' / '
    Write-MtLine ("MT_MODS: OK — 优化类模组就位（{0}）；ImmediatelyFast 为纯客户端，生成世界时会被移出" -f $names)
    return 0
}

function Install-MtSlimeGuard {
    <#
    .SYNOPSIS
        把「超平坦世界无史莱姆」模组（+ 其必需前置 Collective）放进 run/<版本>/mods（幂等）。

    .NOTES
        · 用户硬性要求，见 `$script:SlimeGuardByVersion` 的注释（超平坦世界刷史莱姆会干扰测试流程）；
        · **三条线统一**（2026-09-17 用户裁决：「1.21.1 环境缺少没有史莱姆的超平坦世界模组，这是
          必需的模组……补全该模组然后重新运行 1.21.1 测试」）：
            26.1.2 → 本表（Modrinth Maven 下载，原有实现）
            1.21.1 → 本表（Modrinth Maven 下载，本轮补全）
            1.20.1 → **不在本表**：已由 `forge-1.20.1/build.gradle` 的 `modImplementation`
                     （collective-1.20.1-8.13 / superflatworldnoslimes-1.20.1-3.5）提供，
                     再放一份进 run/mods 会被 FML 判重复模组；
        · 两者都是**服务端/运行期**模组，专用服务器生成世界时**保留**（见 $script:SlimeGuard2612 注释）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $specs = $script:SlimeGuardByVersion[$Paths.version]
    if (-not $specs) {
        if ($Paths.version -eq '1.20.1') {
            Write-MtLine 'MT_MODS: SKIP — 1.20.1 的史莱姆压制由 build.gradle 的 modImplementation 提供（collective / superflat-world-no-slimes），不再放入 run/mods（避免重复模组）'
        }
        return 0
    }

    $cache = Join-Path (Join-Path (Get-MtRoot) 'temp\probe_mods') $Paths.version
    [void](New-Item -ItemType Directory -Force -Path $cache)
    [void](New-Item -ItemType Directory -Force -Path $Paths.mods_dir)

    $installed = @()
    $rc = Install-MtSpecList -Paths $Paths -Specs $specs -Cache $cache -Label '史莱姆压制模组' -Prefixes @('superflatworldnoslimes-', 'collective-')
    if ($rc -ne 0) { return $rc }
    $installed = @($specs | ForEach-Object { $_.Name })
    Write-MtLine ("MT_MODS: OK — 超平坦世界史莱姆压制就位（{0}；运行时生效，无需重建世界）" -f ($installed -join ' / '))
    return 0
}

function Install-MtCarpet {
    <#
    .SYNOPSIS
        把 Carpet（玩家 bot 模组）放进 `run/<版本>/mods`（幂等；1.20.1 走 SKIP 分支）。

    .NOTES
        · 用户要求见 `$script:CarpetByVersion` 的注释（`/player <name> spawn` 造 bot，用于 2 人及以上
          联动的游戏内测试；`/player <name> kill` 或 `/kill <name>` 使其退出）；
        · **1.21.1 → Modrinth Maven 下载**（NeoForge 1.21.1 无 reobf，生产 jar 即 Mojmap 命名）；
        · **1.20.1 → SKIP + 清理历史副本**：改由 `forge-1.20.1/build.gradle` 的 modImplementation 提供
          （生产 SRG jar + SRG refmap 必须经 MDG 重映射；手工放 run/mods 会静默失效）；
        · **26.1.2 → 默认 SKIP**（该线无法获得第二玩家能力，理由见函数体内的完整实测记录）；
          提供**实验路径**：`MT_CARPET_VIA_CONNECTOR=1` 时走「Connector 栈 + Fabric 版 Carpet」，
          但 2026-09-22 实测该路径会导致专用服务器启动致命失败，仅供将来复测用。
          启用时须与 `mt_launch.ps1` 的同名开关保持一致（那边据此决定是否设 Carpet 硬闸门）。
        · 侧别 `server_only_client_optional` ⇒ 专用服务器生成世界时**保留**（不会被移出名单命中）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $specs = $script:CarpetByVersion[$Paths.version]
    if (-not $specs -or @($specs).Count -eq 0) {
        if ($Paths.version -eq '1.20.1') {
            Write-MtLine 'MT_MODS: SKIP — 1.20.1 的 Carpet 由 build.gradle 的 modImplementation 提供（forge-carpet-1.20.1-1.0.8；生产 SRG jar + SRG refmap 必须经 MDG 重映射，手工放 run/mods 会让 mixin 静默失效）'
            # 清理历史上被本函数下载进来的副本：classpath 上已由 Gradle 提供同一模组，
            # run/mods 再放一份会被 FML 判「重复模组」而拒绝启动。
            if (Test-Path -LiteralPath $Paths.mods_dir -PathType Container) {
                foreach ($f in @(Get-ChildItem -LiteralPath $Paths.mods_dir -File -Filter 'forge-carpet-*' -ErrorAction SilentlyContinue)) {
                    Remove-Item -LiteralPath $f.FullName -Force -ErrorAction SilentlyContinue
                    if (-not (Test-Path -LiteralPath $f.FullName)) { Write-MtLine ("MT_MODS: 清理历史副本 {0}（改为 Gradle modImplementation 提供）" -f $f.Name) }
                }
            }
        }
        return 0
    }

    $cache = Join-Path (Join-Path (Get-MtRoot) 'temp\probe_mods') $Paths.version
    [void](New-Item -ItemType Directory -Force -Path $cache)
    [void](New-Item -ItemType Directory -Force -Path $Paths.mods_dir)

    # 26.1.2：Carpet 只有 **Fabric** 构建，需 Sinytra Connector 三件套转发。
    # ⚠️ **默认关闭**（2026-09-22 实测不可用，见下），必须显式 `MT_CARPET_VIA_CONNECTOR=1` 才装。
    #
    # 实测结论（2026-09-22，本机，NeoForge 26.1.2.109 + Connector 3.0.0-beta.6 + FFAPI 0.155.2
    #   + Launchpad 1.9.2 + fabric-carpet 26.1+v260402）：
    #   · 四件套**能装、能被 FML 识别、Connector 转译器能起来**（日志可见
    #     `ConnectorPlugin from mods/connector-….jar` 与 53 个 JiJ 依赖）;
    #   · 但 **Carpet 的 mixin 全部无法注入** ⇒ 专用服务器启动**致命失败**：
    #       MixinTransformerError: Critical injection failure: Constant modifier method
    #       addFillUpdatesInt(I)I in carpet.mixins.json:Level_fillUpdatesMixin from mod carpet
    #       failed injection check, (0/1) succeeded. Scanned 0 target(s). **No refMap loaded.**
    #     根因：`carpet.mixins.json` **不含 refmap 字段**（实测 refmap=None；`fabric.mod.json`
    #     的 mixins 段也只是裸文件名），171 个 mixin 的方法/字段引用以 Fabric **intermediary**
    #     名硬编码；Fabric 环境下由 Fabric Loader 的运行时反向映射兜底，而 Connector 在本版
    #     未能为它生成/应用 refmap ⇒ 目标方法名解析不到（`Scanned 0 target(s)`）。
    #     （日志里另有一条 `Reference map '' for adapter.init.mixins.json could not be read`
    #      —— 那属于 Sinytra Mixin Adapter 自己的配置，**故意不带 refmap**，是良性告警，
    #      不是本故障的原因。）
    #   · 影响面：**是阻断性的**。世界生成阶段即崩，整条 26.1.2 测试线（含原本可跑的 10 条单机
    #     用例）都会变得不可运行 ⇒ 默认必须关闭，否则「为一个功能废掉一整条线」。
    #   · 备选路径同样不存在：NeoForge 原生移植 `Carpet: NeoForged`（`neoforge-carpet`）
    #     只发布到 **1.20.1 / 1.21.1**，**没有 26.1.x 构建**（Modrinth 实查 2026-09-22）。
    #   · `connector-extras` 不解决该问题 —— 它是第三方 API 桥接（能量/REI/…），与 mixin 重映射无关。
    # ⇒ 结论：**该 MC×加载器组合下无法引入第二玩家能力**；12 条双人用例保持 BLOCKED。
    #   若将来 Connector 修好 refmap 生成、或 Carpet 出了带 refmap 的构建、或有 NeoForge 原生
    #   26.1.x Carpet，只需把 `Install-MtCarpet` 的 26.1.2 分支恢复为无条件安装即可。
    if ($Paths.version -eq '26.1.2') {
        if ($env:MT_CARPET_VIA_CONNECTOR -ne '1') {
            Write-MtLine 'MT_MODS: SKIP — 26.1.2 不装 Carpet（Connector 路径实测不可用：Carpet 的 171 个 mixin 缺 refmap ⇒ 目标名解析不到 ⇒ 专用服务器启动致命失败；详见 Install-MtCarpet 注释。要复测该项请设 MT_CARPET_VIA_CONNECTOR=1）'
            # 清理历史上被本函数装进来的 Connector 栈与 Fabric Carpet，避免残留把环境继续弄坏。
            if (Test-Path -LiteralPath $Paths.mods_dir -PathType Container) {
                $stale = @('fabric-carpet-*') + @($script:ConnectorStackPrefixes2612 | ForEach-Object { "$_*" })
                foreach ($pat in $stale) {
                    foreach ($f in @(Get-ChildItem -LiteralPath $Paths.mods_dir -File -Filter $pat -ErrorAction SilentlyContinue)) {
                        Remove-Item -LiteralPath $f.FullName -Force -ErrorAction SilentlyContinue
                        if (-not (Test-Path -LiteralPath $f.FullName)) { Write-MtLine ("MT_MODS: 清理 {0}（Connector 路径默认关闭）" -f $f.Name) }
                    }
                }
            }
            return 0
        }
        $rc = Install-MtSpecList -Paths $Paths -Specs $script:ConnectorStack2612 -Cache $cache `
            -Label 'Sinytra Connector 栈（Launchpad/Connector/Forgified Fabric API）' `
            -Prefixes $script:ConnectorStackPrefixes2612
        if ($rc -ne 0) { return $rc }
        $stackNames = @($script:ConnectorStack2612 | ForEach-Object { $_.Name }) -join ' / '
        Write-MtWarn ("MT_MODS: 已装 Connector 栈（{0}）—— 这是**实验路径**：2026-09-22 实测 Carpet 在此栈下 mixin 注入失败、专用服务器启动即崩，预期世界生成会 BLOCKED" -f $stackNames)
    }

    $rc = Install-MtSpecList -Paths $Paths -Specs $specs -Cache $cache -Label 'Carpet 玩家 bot' -Prefixes @('neoforge-carpet-', 'forge-carpet-', 'fabric-carpet-')
    if ($rc -ne 0) { return $rc }
    $names = @($specs | ForEach-Object { $_.Name }) -join ' / '
    if ($Paths.version -eq '26.1.2') {
        Write-MtWarn ("MT_MODS: 已装 Carpet(Fabric, 经 Connector 转发)（{0}）—— 实验路径，见上方风险说明" -f $names)
    } else {
        Write-MtLine ("MT_MODS: OK — Carpet(NeoForged) 就位（{0}）；`/player <name> spawn 建 bot、`/player <name> kill 或 `/kill <name> 使其退出" -f $names)
    }
    return 0
}

function Get-MtIrisConfigFile {
    <#
    .SYNOPSIS
        返回该线**光影加载器实际读写**的配置文件路径（1.20.1 = Oculus，其余 = Iris）。

    .NOTES
        2026-09-17 实测（必须按版本取，否则写了个没人读的文件）：
          · 1.21.1 / 26.1.2 用 Iris ⇒ `run/<V>/config/iris.properties`；
          · **1.20.1 的加载器是 Oculus（Iris 的 Forge 移植），它读写的是 `config/oculus.properties`** ——
            实测 `run/1.20.1/config/` 下只有 `oculus.properties`（内含 `shaderPack=…` + `enableShaders=true`），
            **没有** `iris.properties`；旧代码一律写 `iris.properties` ⇒ 1.20.1 的「默认启用光影」
            其实只是被 Oculus 自身默认值兜住，并没有被工具链真正设定过。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $name = if ($Paths.version -eq '1.20.1') { 'oculus.properties' } else { 'iris.properties' }
    return (Join-Path (Join-Path $Paths.run_dir 'config') $name)
}

function Install-MtRenderStack {
    <#
    .SYNOPSIS
        把渲染栈（Sodium + Iris，26.1.2 从 Maven 装 / 1.21.1 由整合包复制）与**光影包**放进
        `run/<版本>/`，并让 Iris **默认选中并启用**该光影（幂等）。

    .NOTES
        · 来源 = **Modrinth Maven**（`https://api.modrinth.com/maven`，坐标见 $script:RenderMods2612 / $script:ShaderPack2612
          的 `Coord` 字段，形如 `maven.modrinth:sodium:mc26.1.2-0.9.1-neoforge`）；**不使用** CDN/GitHub 直链。
        · mods 落位 `run/<版本>/mods`；光影包落位 `run/<版本>/shaderpacks/`；
        · 缓存在 `temp/probe_mods/<版本>/`（与探针运行时同一缓存目录，便于整体清理）；
        · 命中判据 = 目标文件存在且**尺寸一致**，下载后按固定 sha1 校验，失败硬报 14，不静默降级；
        · 顺带把光影加载器选中的光影与开关写进它的配置（`shaderPack=<包名>` + `enableShaders=true`）——**路径按版本取**：Iris 线是 `config/iris.properties`，**1.20.1 是 Oculus 的 `config/oculus.properties`**（见 Get-MtIrisConfigFile）。

        ⚠️ **`enableShaders` 自 2026-09-17 起默认写 true**（用户要求「添加光影包并设置默认启用」）；
        此前默认 false 只是「测试不需要光影 + 省性能」，**不是因为崩**。经用户点出并用两轮实测确认：26.1.2 上「开光影即崩 `IllegalStateException: Missing
        sampler Sampler1`（`GlCommandEncoder.trySetup`）」的元凶是 **Sodium 0.9.2**；把它降到**整合包同款的
        0.9.1**（本函数当前的规格）后，Iris 1.11.4 + Complementary Unbound r5.9.3 **正常工作** ——
        `SHADER-VISION-26.1.2` 用例由 FAIL（0.9.2：`Using shaderpack:` 后立即崩 + 新增崩溃报告）转
        **PASS**（光影渲染正常、无崩溃报告；视觉读数：原版方块云 → 光影体积云/大气散射/色调映射）。
        更早那条「属上游无解缺陷、与版本无关」的结论**作废**（当时的对照只换了光影包、没换 Sodium 版本）。
        ⇒ 改动 Sodium/Iris/光影包版本后**必须复跑** `SHADER-VISION-26.1.2`；换版本号时注意本函数末尾的
        「旧版本清理」，否则新旧 Sodium 并存会被 FML 判重复模组。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    if ($Paths.version -notin @('1.21.1', '26.1.2')) {
        # 1.20.1 **渲染栈本身可用**（2026-09-17 实测复验）：Embeddium 0.3.31 + Oculus 1.8.0 由
        # `forge-1.20.1/build.gradle` 的 `modImplementation` 提供（MDG 解析期重映射，与 FerriteCore /
        # collective / superflat 同一机制），dev run 实测 `EMBEDDIUM_LOADED=true` / `OCULUS_LOADED=true`
        # 且进世界后打印 `Using shaderpack: ComplementaryUnbound_r5.9.3.zip`。
        # 这里 SKIP 的只是**本函数的「下载渲染模组」一步**（生产 SRG jar 手工放进 run/mods 不会被重映射）。
        # ⚠️ 旧文案曾写「1.20.1 不装渲染栈 ⇒ 光影不可用」——**该说法已作废**（当时的结论来自「手工放
        # run/mods」那条错路；改走 modImplementation 后光影正常）。光影包与 iris.properties 的写入
        # 由本函数更早的 `Install-MtRenderStack` 步骤完成，1.20.1 同样生效。
        Write-MtLine ("MT_MODS: SKIP(下载渲染模组) — {0} 的 Embeddium/Oculus 由 forge-1.20.1/build.gradle 的 modImplementation 提供（生产 SRG jar 必须经 MDG 重映射）；光影包与 iris.properties 已在前面落位，1.20.1 光影**可用**" -f $Paths.version)
        return 0
    }

    $cache = Join-Path (Join-Path (Get-MtRoot) 'temp\probe_mods') $Paths.version
    [void](New-Item -ItemType Directory -Force -Path $cache)
    [void](New-Item -ItemType Directory -Force -Path $Paths.mods_dir)

    $progressBak = $ProgressPreference
    $ProgressPreference = 'SilentlyContinue'
    try {
        # 1) 渲染模组 → run/<版本>/mods（含同族旧版本清理，见 Install-MtSpecList）
        #    26.1.2 = 从 Modrinth Maven 下载；1.21.1 = 规格为空（Sodium/Iris 由整合包复制）
        $specs = $script:RenderModsByVersion[$Paths.version]
        $installed = @()
        if ($specs -and @($specs).Count -gt 0) {
            $rc = Install-MtSpecList -Paths $Paths -Specs $specs -Cache $cache -Label '渲染模组' -Prefixes @('sodium-', 'iris-')
            if ($rc -ne 0) { return $rc }
            $installed = @($specs | ForEach-Object { $_.Name })
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

        # 3) 光影加载器配置：选中该光影（Iris 的 config/iris.properties；
        #    **1.20.1 是 Oculus，配置文件名不同** ⇒ 一律经 Get-MtIrisConfigFile 取，别写死）
        #    2026-09-17 用户要求「添加光影包并设置默认启用」⇒ **1.21.1 / 1.20.1 默认写 true**。
        #    ⚠️ **26.1.2 例外：默认写 false**（2026-09-22 三次实测：Iris 在该线上的 GUI 渲染管线
        #       与光影不兼容 ⇒ 一进世界就崩、客户端被 emergencySaveAndCrash 终止）：
        #         · `Iris/FATAL: Missing program minecraft:pipeline/gui_text in override list`
        #         · `IllegalStateException: Missing sampler Sampler1`（**移除 ImmediatelyFast 后仍复现**
        #           ⇒ 元凶不是 ImmediatelyFast；Sodium 亦已降到注释里记的 0.9.1，仍崩 ⇒ 该修复不再复现）
        #       其表征极易误判：`mt_launch` 报 `MT_LAUNCH: OK (40s) — 已进入世界`，随后客户端
        #       数秒内**静默消失**（无 shutdown 消息、无 crash-reports 之外的下文），用例全报
        #       「26.1.2 客户端未在运行」⇒ 排查时**先看 `SHADERS=` 与本项**，不要误判成宿主回收进程树。
        #       需要「开光影」做对照时显式 `mt_env.ps1 shaders --version 26.1.2 --state on`。
        $irisCfg = Get-MtIrisConfigFile -Paths $Paths
        [void](New-Item -ItemType Directory -Force -Path (Split-Path $irisCfg))
        $lines = @()
        if (Test-Path -LiteralPath $irisCfg -PathType Leaf) { $lines = @(Get-Content -LiteralPath $irisCfg) }
        # 26.1.2 = false（理由见上）；其余线维持用户 2026-09-17 的「默认启用」裁决。
        $shadersOn = if ($Paths.version -eq '26.1.2') { 'false' } else { 'true' }
        $set = @{ 'shaderPack' = $sp.Name; 'enableShaders' = $shadersOn }
        foreach ($k in $set.Keys) {
            $found = $false
            for ($i = 0; $i -lt $lines.Count; $i++) {
                if ($lines[$i] -match "^\s*$([regex]::Escape($k))\s*=") { $lines[$i] = "$k=$($set[$k])"; $found = $true; break }
            }
            if (-not $found) { $lines += "$k=$($set[$k])" }
        }
        Set-Content -LiteralPath $irisCfg -Value $lines -Encoding utf8

        $modsText = if ($installed.Count -gt 0) { $installed -join ' / ' } else { '（渲染模组由整合包复制，见 NeoForgeMods）' }
        Write-MtLine ("MT_MODS: OK — 渲染栈就位（{0}）＋ 光影 {1}；光影加载器已选中该光影（enableShaders={2} @ {3}）" -f `
                $modsText, $sp.Name, $shadersOn, (Split-Path -Leaf $irisCfg))
        return 0
    } finally {
        $ProgressPreference = $progressBak
    }
}

function Invoke-MtEnvShaders {
    <#
    .SYNOPSIS
        读写光影加载器配置的 `enableShaders`（`--state off|on|status`），**不触碰 mods/缓存、不联网**。路径按版本取：Iris 线 = `config/iris.properties`，1.20.1 = Oculus 的 `config/oculus.properties`（Get-MtIrisConfigFile）。

    .NOTES
        为什么需要它（2026-09-17 实测踩坑）：`Install-MtRenderStack` 只在 `mt_env mods` 时把
        `enableShaders` 写回 false；而**游戏内的光影开关会持久化该键**（Iris 的 K 键
        `iris.keybind.toggleShaders` 与光影界面 Apply 都会写该配置文件（1.20.1 上 Oculus 写的是 `oculus.properties`））。
        于是一次「光影开启」验证跑完（客户端按 K 打开光影）会把该键留在 `true`，
        **下一次冷启动会在进入世界的第一帧就崩**（launch 阶段报
        `MT_LAUNCH: ERROR — 进入世界后立即崩溃`）——这是真实的现场，但会让后续任何用例
        都跑不起来。故把「把光影状态摆回已知值」做成一条**显式、幂等、可复现**的命令：
        `pwsh -File scripts/test/mt_env.ps1 shaders --version 26.1.2 --state off`。
        光影回归 (`SHADER-VISION-26.1.2`) 的前置就该是它 + 冷启动。

        只改这一行，保留文件里其它键（shaderPack / 调过的设置）不动；缺文件时按 `status`
        报 BLOCKED（不臆造配置）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Version, [string]$State = 'status')

    $Paths = Get-MtPaths -Version $Version
    $irisCfg = Get-MtIrisConfigFile -Paths $Paths
    if (-not (Test-Path -LiteralPath $irisCfg -PathType Leaf)) {
        Write-MtBlocked 'shaders' ("未找到 {0}（先跑 mt_env.ps1 mods --version {1}）" -f $irisCfg, $Version)
        return $MT_EXIT_BLOCKED
    }

    $lines = @(Get-Content -LiteralPath $irisCfg)
    $current = '(未设置)'
    $idx = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\s*enableShaders\s*=\s*(.*)$') { $current = $Matches[1].Trim(); $idx = $i; break }
    }

    if ($State -eq 'status') {
        Write-MtLine ("MT_SHADERS: {0} (enableShaders={1}, {2})" -f $current, $current, $irisCfg)
        return $MT_EXIT_PASS
    }
    if ($State -notin @('off', 'on')) {
        Write-MtErrorLine ("非法 --state {0}（可选：off on status）" -f $State)
        return $MT_EXIT_ERROR
    }

    $want = if ($State -eq 'on') { 'true' } else { 'false' }
    if ($idx -ge 0) { $lines[$idx] = "enableShaders=$want" } else { $lines += "enableShaders=$want" }
    Set-Content -LiteralPath $irisCfg -Value $lines -Encoding utf8
    Write-MtLine ("MT_SHADERS: {0} → {1} (enableShaders={2})" -f $current, $State, $want)
    return $MT_EXIT_PASS
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
        # （实测 Modrinth 已有 sodium 0.9.1 / iris 1.11.4 的 26.1.2 release 构建；0.9.1 是用户要求的、与整合包一致的版本）。
        $rc = Install-MtRenderStack -Paths $p
        if ($rc -ne 0) { return $rc }
        # 超平坦世界史莱姆压制（2026-09-17 用户硬性要求，见 $script:SlimeGuard2612 注释）
        $rc = Install-MtSlimeGuard -Paths $p
        if ($rc -ne 0) { return $rc }
        # 优化类模组（2026-09-17 用户要求：ImmediatelyFast + ModernFix 兼容性验证）
        $rc = Install-MtPerfMods -Paths $p
        if ($rc -ne 0) { return $rc }
        # Carpet：玩家 bot（2026-09-27 用户要求）。**26.1.2 走 SKIP 分支** —— 用户裁决该线不使用
        # 本模组（Mojang 自带 bot 管理指令，后续再学）；这里显式调用只为在 env 日志里留一行可见的
        # 「为什么不装」，避免后来者以为漏了。
        $rc = Install-MtCarpet -Paths $p
        if ($rc -ne 0) { return $rc }
        Write-MtLine 'MT_MODS: OK — 26.1.2 dev run：探针运行时 + Sodium/Iris + Complementary Unbound 光影(默认启用) + 超平坦史莱姆压制 + 优化模组(ModernFix/FerriteCore)'
        Write-MtLine 'MT_MODS: 注意 — ImmediatelyFast 在 26.1.2 **故意不装**（与 Iris 开光影互斥，实测 Missing sampler 崩溃；见 $script:PerfMods2612 注释）。Sodium/Iris 为纯客户端模组：`mt_env world` 起专用服务器会自动移出，但**两段式数据生成（runClientData/runServerData）前必须手动移出** run/26.1.2/mods（与探针运行时同规则）'
        [void](Invoke-MtPauseLockEnforce -Paths $p)
    [void](Invoke-MtKeyBindingEnforce -Paths $p)
    [void](Invoke-MtWindowedEnforce -Paths $p)
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
        # 优化类模组（2026-09-17 用户要求：所有测试环境装 ImmediatelyFast + FerriteCore）。
        # 史莱姆压制**不在这里装** —— 1.20.1 由 build.gradle 的 modImplementation 提供（见 Install-MtSlimeGuard）。
        $rc = Install-MtSlimeGuard -Paths $p   # 该版本走 SKIP 分支，打印来源说明
        if ($rc -ne 0) { return $rc }
        $rc = Install-MtPerfMods -Paths $p
        if ($rc -ne 0) { return $rc }
        # Carpet：玩家 bot（2026-09-27 用户要求）。1.20.1 走 SKIP 分支 —— 由 build.gradle 的
        # modImplementation 提供（生产 SRG jar 必须经 MDG 重映射），本调用只回显来源并清理历史副本。
        $rc = Install-MtCarpet -Paths $p
        if ($rc -ne 0) { return $rc }
        Write-MtLine 'MT_MODS: OK — 1.20.1 渲染栈（Embeddium/Oculus）与 FerriteCore 由 build.gradle 的 modImplementation 提供、光影包与光影加载器配置已落位（2026-09-17 实测：EMBEDDIUM_LOADED/OCULUS_LOADED=true + `Using shaderpack: ComplementaryUnbound_r5.9.3.zip`）；生产环境目录已校验'
        [void](Invoke-MtPauseLockEnforce -Paths $p)
    [void](Invoke-MtKeyBindingEnforce -Paths $p)
    [void](Invoke-MtWindowedEnforce -Paths $p)
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

    # 1.21.1 独有：探针宿主（KubeJS/Rhino/Architectury）+ 史莱姆压制 + 优化类模组（2026-09-17 用户要求）。
    # 顺序说明：放在「从整合包复制 Sodium/Iris/ModernFix」**之后**，避免复制循环看到半装的 mods 目录；
    # 各族的清理前缀互不相交（superflatworldnoslimes-/collective- vs immediatelyfast-/ferritecore-），
    # 且都不与整合包文件名冲突，故先后不影响结果。
    $rc = Install-MtProbeHost -Paths $p
    if ($rc -ne 0) { return $rc }
    $rc = Install-MtSlimeGuard -Paths $p
    if ($rc -ne 0) { return $rc }
    $rc = Install-MtPerfMods -Paths $p
    if ($rc -ne 0) { return $rc }
    # Carpet：玩家 bot（2026-09-27 用户要求，`/player <name> spawn`；见 $script:CarpetByVersion）。
    # 放在这里（复制循环之后）与其它「按族清理」的调用并列：前缀 neoforge-carpet- 不与整合包
    # 文件名（sodium/iris/modernfix）相交，故无顺序依赖。
    $rc = Install-MtCarpet -Paths $p
    if ($rc -ne 0) { return $rc }
    # 光影包 + Iris 默认启用（2026-09-17 用户要求「游戏环境缺少光影包，添加光影包并设置默认启用」）。
    # 1.21.1 的 Sodium/Iris 由上面的整合包复制提供，本调用只补「光影包 + 光影加载器配置（iris.properties / 1.20.1 为 oculus.properties）」。
    $rc = Install-MtRenderStack -Paths $p
    if ($rc -ne 0) { return $rc }
    Write-MtLine 'MT_MODS: 提示 — ImmediatelyFast 为纯客户端：`mt_env world` 起专用服务器会自动移出；FerriteCore 两侧皆可，保留'
    [void](Invoke-MtPauseLockEnforce -Paths $p)
    [void](Invoke-MtKeyBindingEnforce -Paths $p)
    [void](Invoke-MtWindowedEnforce -Paths $p)
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
        失焦暂停会让后台注入失效，必须关闭（= 全局测试规则「禁止游戏失焦打开 ESC 菜单」）。

    .NOTES
        2026-09-17 起实现下沉到 `lib/Mt.Paths.psm1` 的 `Set-MtPauseOnLostFocus`（mt_launch 也要用同一实现
        在每次冷启动前强制），本函数保留为兼容包装。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    [void](Set-MtPauseOnLostFocus -Paths $Paths -Enabled $false)
}

function Invoke-MtPauseLockEnforce {
    <#
    .SYNOPSIS
        全局测试规则「禁止游戏失焦打开 ESC 菜单」的唯一落地点：写 `options.txt` 的
        `pauseOnLostFocus:false` 并回显 `MT_PAUSE_LOCK: on`（幂等）。

    .NOTES
        为什么是全局规则（2026-09-17 用户裁决）：失焦暂停会让后台注入（mt_inject）与截图（mt_capture）
        全部失效，并且暂停菜单会顶在画面上——历史上正是为了关掉这个菜单才在流程里塞进多余的
        Esc 按键。现在由工具链在 env/launch 两处强制关闭，测试流程里不再需要那些 Esc。
        查询入口：`mt_env.ps1 debug --version <V> --pause-lock status`。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $on = Set-MtPauseOnLostFocus -Paths $Paths -Enabled $false
    Write-MtLine ("MT_PAUSE_LOCK: on — 禁止失焦打开 ESC 菜单（pauseOnLostFocus=false，{0}）" -f `
            (Join-Path $Paths.run_dir 'options.txt'))
    return $on
}

function Invoke-MtWindowedEnforce {
    <#
    .SYNOPSIS
        全局测试规则「runClient 必须窗口模式」的唯一落地点：写 `options.txt` 的 `fullscreen:false`
        并回显 `MT_WINDOWED: on`（幂等）。

    .NOTES
        为什么是全局规则（2026-09-21 实测）：全屏下 mt_inject 的键鼠注入送不到，
        launch 的 `MT_preflight-op` 闸门会稳定 FAIL，而脚本自发心跳 `AP_NOAI` 仍然正常
        —— 极易被误诊为「探针未加载」。与 pause-lock / keybinds 同属**环境不变量**。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    [void](Set-MtFullscreenDisabled -Paths $Paths)
    Write-MtLine ("MT_WINDOWED: on — runClient 强制窗口模式（fullscreen=false，{0}）" -f `
            (Join-Path $Paths.run_dir 'options.txt'))
    return $true
}

function Invoke-MtKeyBindingEnforce {
    <#
    .SYNOPSIS
        测试环境按键绑定不变量：把 `Get-MtTestKeyBindings` 里的一对（潜行/冲刺）幂等改回期望值，
        并回显 `MT_KEYBINDS: OK` / `MT_KEYBINDS: REPAIRED …`。

    .NOTES
        2026-09-19 实测踩坑：`run/1.21.1/options.txt` 的 sneak/sprint 被换绑后，
        「右键 + 潜行 = 取消」用例（SELECTOR-KEYS）注入的 LEFT SHIFT 被游戏当成**冲刺**，
        客户端始终走「右键 = 自用提示」分支 ⇒ 断言 `key=right_sneak action=cancel` 稳定失败，
        而与工具链无关的 1.20.1（绑定未变）同一用例全绿 —— 这类环境漂移**只能靠不变量挡住**。
        查询入口：`mt_env.ps1 debug --version <V> --keybinds status`。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][psobject]$Paths)

    $fixed = @(Repair-MtTestKeyBindings -Paths $Paths)
    $path = Join-Path $Paths.run_dir 'options.txt'
    if ($fixed.Count -eq 0) {
        Write-MtLine ("MT_KEYBINDS: OK — 潜行/冲刺绑定未被改动（{0}）" -f $path)
    } else {
        $detail = ($fixed | ForEach-Object {
                '{0}:{1}→{2}' -f $_.Key, $(if ($null -eq $_.From -or $_.From -eq '') { '(缺失)' } else { $_.From }), $_.To
            }) -join '; '
        Write-MtWarn ("MT_KEYBINDS: REPAIRED — 测试环境按键绑定被改动过，已改回期望值：{0}（{1}）" -f $detail, $path)
    }
    return $fixed
}

function Invoke-MtEnvDebug {
    <#
    .SYNOPSIS
        全局调试/测试环境开关子命令（三条线同一入口）：
        `mt_env.ps1 debug --version <V> [--pause-lock on|off|status] [--shaders on|off|status] [--keybinds status|repair]`。

    .DESCRIPTION
        2026-09-17 用户要求「添加全局调试命令，禁止游戏失焦打开 ESC 菜单」。本子命令是这些
        **测试环境开关**的统一入口（只读写 run 目录里的配置，不联网、不碰 mods）：
          · `--pause-lock on`  → `options.txt` `pauseOnLostFocus:false`（**默认期望值**：禁止失焦弹 ESC 菜单）
            `--pause-lock off` → 写回 true（对照实验用；会明确 WARN，因为之后注入/截图可能失效）
          · `--shaders on|off|status` → 透传到 `Invoke-MtEnvShaders`（路径按版本取：Iris 线 = config/iris.properties，1.20.1 = Oculus 的 config/oculus.properties）
          · `--keybinds status|repair` → 潜行/冲刺绑定不变量（2026-09-19 新增）：`status` 只读回显
            「当前值 vs 期望值」，`repair` 幂等改回期望值；env 阶段每次都会自动 repair
        不带任何开关时只**回显当前状态**（等效于都给 status）。

    .NOTES
        ⚠️ 游戏退出时会重写 `options.txt`，故 pause-lock / keybinds 必须在**冷启动之前**设置；
        mt_launch 每次启动前也会自动强制 pause-lock，本命令用于「先设好、再手工启动」或事后核对。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Version,
        [string]$PauseLock = 'status',
        [string]$Shaders = 'status',
        [string]$Keybinds = 'status'
    )

    $Paths = Get-MtPaths -Version $Version
    $rc = 0
    $pauseState = Get-MtPauseOnLostFocus -Paths $Paths

    if ($PauseLock -in @('on', 'off')) {
        $wantEnabled = ($PauseLock -eq 'off')   # pause-lock on ⇒ pauseOnLostFocus=false
        [void](Set-MtPauseOnLostFocus -Paths $Paths -Enabled $wantEnabled)
        $pauseState = $wantEnabled
        if ($PauseLock -eq 'on') {
            Write-MtLine 'MT_DEBUG: PAUSE_LOCK=on — 失焦不再打开 ESC 暂停菜单（测试流程据此删除多余的 Esc 按键）'
        } else {
            Write-MtWarn 'MT_DEBUG: PAUSE_LOCK=off — 已允许失焦暂停：后台注入/截图可能被 ESC 菜单挡住（仅对照实验用）'
        }
    } elseif ($PauseLock -ne 'status') {
        Write-MtErrorLine ("非法 --pause-lock {0}（可选：on off status）" -f $PauseLock)
        return $MT_EXIT_ERROR
    }

    $pauseText = if ($null -eq $pauseState) { '(未设置/options.txt 不存在)' }
    elseif (-not $pauseState) { 'on (pauseOnLostFocus=false)' } else { 'off (pauseOnLostFocus=true)' }
    Write-MtLine ("MT_DEBUG: {0} pause-lock={1}" -f $Version, $pauseText)

    if ($Keybinds -eq 'repair') {
        [void](Invoke-MtKeyBindingEnforce -Paths $Paths)
    } elseif ($Keybinds -eq 'status') {
        $drift = @()
        foreach ($k in @((Get-MtTestKeyBindings).Keys)) {
            $want = [string](Get-MtTestKeyBindings)[$k]
            $have = Get-MtKeyBinding -Paths $Paths -Key $k
            $haveText = if ($null -eq $have -or $have -eq '') { '(未设置)' } else { $have }
            if ($have -ne $want) { $drift += ('{0}:{1}（期望 {2}）' -f $k, $haveText, $want) }
        }
        if ($drift.Count -eq 0) {
            Write-MtLine ("MT_DEBUG: {0} keybinds=OK（潜行/冲刺绑定为期望值）" -f $Version)
        } else {
            Write-MtWarn ("MT_DEBUG: {0} keybinds=DRIFT — {1}；用 `--keybinds repair` 或重跑 `mt_env world/mods` 改回" -f `
                    $Version, ($drift -join '; '))
        }
    } else {
        Write-MtErrorLine ("非法 --keybinds {0}（可选：status repair）" -f $Keybinds)
        return $MT_EXIT_ERROR
    }

    if ($Shaders -ne 'status') {
        $src = Invoke-MtEnvShaders -Version $Version -State $Shaders
        if ($src -ne $MT_EXIT_PASS) { $rc = $src }
    } else {
        # 只读状态：**不**调用 Invoke-MtEnvShaders —— 缺配置文件时它会打一条
        # BLOCKED 噪音。直接读键，路径按版本取（1.20.1 = Oculus 的 oculus.properties）。
        $irisCfg = Get-MtIrisConfigFile -Paths $Paths
        $shadersText = 'n/a(该线无光影加载器配置)'
        if (Test-Path -LiteralPath $irisCfg -PathType Leaf) {
            $irisLines = @(Get-Content -LiteralPath $irisCfg)
            $cur = '(未设置)'; $pack = '(未设置)'
            foreach ($ln in $irisLines) {
                if ($ln -match '^\s*enableShaders\s*=\s*(.*)$') { $cur = $Matches[1].Trim() }
                if ($ln -match '^\s*shaderPack\s*=\s*(.*)$') { $pack = $Matches[1].Trim() }
            }
            $shadersText = ("{0} (shaderPack={1})" -f $cur, $pack)
        }
        Write-MtLine ("MT_DEBUG: {0} shaders={1}" -f $Version, $shadersText)
    }
    return $rc
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
        # 测试规则：新建/恢复的世界必须 keepInventory=true（见 Set-MtKeepInventory）；
        # 26.1.2 追加：真正存储在 data/minecraft/game_rules.dat，写后**从文件读回**校验
        # （见 Set-MtWorldKeepInventory）。1.20.1/1.21.1 的文案与产物不变。
        $kinvErr = Set-MtWorldKeepInventory -Version $Version -Paths $p
        if ($null -ne $kinvErr) {
            Write-MtLine "MT_WORLD: BLOCKED — $kinvErr"
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
            foreach ($k in @('imblocker', 'sodium', 'iris', 'embeddium', 'oculus', 'immediatelyfast')) {
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
    # 测试规则：新建/恢复的世界必须 keepInventory=true（见 Set-MtKeepInventory）；
    # 26.1.2 追加：真正存储在 data/minecraft/game_rules.dat，写后**从文件读回**校验
    # （见 Set-MtWorldKeepInventory）。1.20.1/1.21.1 的文案与产物不变。
    $kinvErr = Set-MtWorldKeepInventory -Version $Version -Paths $p
    if ($null -ne $kinvErr) {
        Write-MtErrLine "MT_WORLD: BLOCKED — $kinvErr"
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
    $ShaderState = 'status'
    # 全局调试子命令的开关（2026-09-17）：pause-lock = 禁止失焦打开 ESC 菜单（见 Invoke-MtEnvDebug）
    $PauseLockState = 'status'
    # 测试环境按键绑定不变量（2026-09-19）：潜行/冲刺绑定漂移会让 `--shift` 注入语义错位
    $KeybindState = 'status'

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
        } elseif ($key -eq 'state') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --state 的值'; exit $MT_EXIT_ERROR }
            $ShaderState = ([string]$args[$i + 1]).ToLowerInvariant()
            $i += 2
        } elseif ($key -eq 'pause-lock') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --pause-lock 的值'; exit $MT_EXIT_ERROR }
            $PauseLockState = ([string]$args[$i + 1]).ToLowerInvariant()
            $i += 2
        } elseif ($key -eq 'keybinds') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --keybinds 的值'; exit $MT_EXIT_ERROR }
            $KeybindState = ([string]$args[$i + 1]).ToLowerInvariant()
            $i += 2
        } elseif ($key -eq 'timeout') {
            if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --timeout 的值'; exit $MT_EXIT_ERROR }
            $TimeoutSec = [int]$args[$i + 1]
            $i += 2
        } else {
            Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
        }
    }

    if ($Cmd -notin @('mods', 'world', 'kill', 'kubejs', 'shaders', 'debug')) {
        Write-MtErrorLine '必须指定子命令 mods / world / kill / kubejs / shaders / debug'
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
        'shaders' { exit (Invoke-MtEnvShaders -Version $Version -State $ShaderState) }
        'debug' { exit (Invoke-MtEnvDebug -Version $Version -PauseLock $PauseLockState -Shaders $ShaderState -Keybinds $KeybindState) }
        'kill' {
            [void](Stop-MtVersionProcesses -Paths (Get-MtPaths -Version $Version))
            exit $MT_EXIT_PASS
        }
    }
    exit $MT_EXIT_ERROR
}
