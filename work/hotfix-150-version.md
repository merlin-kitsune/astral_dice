# fabric-1.20.1 1.5.0-hotfix 版本落定报告

- 时间：2026-11-02
- 仓库：F:/MCProject/astral_dice_multiloader（分支 multi-main，HEAD 6fe38138 docs(changelog): record the fabric sign tooltip fix in 1.5.0 [republish-assets]）
- 范围：只动 fabric-1.20.1 线的版本号与配套文档；未碰 astral_dice_multiloader-next，未碰 docs/agent、models 等他人未提交改动。
- 结论：版本号已定 1.5.0-hotfix；构建 BUILD SUCCESSFUL；产物 2,944,301 B / SHA256 8a7edad09cf77059dde32b91fd585b4a402265c59832415ee827935d2108d6f2；
  中英 CHANGELOG 对称、两份玩家更新日志已建、两个守门脚本全绿。1 项任务项在本线不成立（类路径版本资源），见 §4.2 —— 非本次改动引入。

## 1. 改动清单

| 文件 | 行号 | 改动 |
|---|---|---|
| fabric-1.20.1/gradle.properties | 26-27（新增） | 删掉 3 行过时注释（原文写「本线并入开发线 multi-dev-next，版本号与三线统一为 2.0.0-SNAPSHOT.19」），换成 2 行与当前实际相符的说明：本线单独出 1.5.0-hotfix 热修、另三线仍 1.5.0、互通号由 majorMinor() 截断取 1.5 |
| fabric-1.20.1/gradle.properties | 28 | `mod_version=1.5.0+fabric_1.20.1` → `mod_version=1.5.0-hotfix+fabric_1.20.1` |
| CHANGELOG.md | 7-21 | 顶部新增 `## 1.5.0-hotfix (2026-11-02)`（Bug Fixes / Player-facing changes 各 1 条），插在 `## 1.5.0 (2026-11-02)`（现 23 行）之上 |
| CHANGELOG_ZH.md | 7-19 | 同一节的中文镜像（已修复BUG / 玩家可见变更 各 1 条），插在 `## 1.5.0 (2026-11-02)`（现 21 行）之上 |
| release/1.5.0-hotfix/PLAYER_CHANGELOG_ZH.md | 新建 1-17 | 玩家口吻短说明（980 B，CRLF） |
| release/1.5.0-hotfix/PLAYER_CHANGELOG.md | 新建 1-19 | 英文镜像（1008 B，CRLF） |

未改动（逐项已实读确认）：`starengine_version` / `starengine_version_range`、另外三线 gradle.properties、README.md（支持矩阵仍是 1.5.0）、任何 lang 文件。

临时脚本（work/ 下，未跟踪）：`_build-hotfix150-fabric.ps1`、`_patch_hotfix150_changelog.py`、`_verify_hotfix150_jar.py`、`_verify_hotfix150_counts.py`、`_write_hotfix150_release_notes.py`、`_evidence_hotfix150.py`。

## 2. 版本号

### 2.1 命令

    git -C F:/MCProject/astral_dice_multiloader diff -U2 -- fabric-1.20.1/gradle.properties

### 2.2 原始输出

    diff --git a/fabric-1.20.1/gradle.properties b/fabric-1.20.1/gradle.properties
    index 61a85b9f..e8e00ef8 100644
    --- a/fabric-1.20.1/gradle.properties
    +++ b/fabric-1.20.1/gradle.properties
    @@ -24,8 +24,7 @@ mod_license=GPL-3.0-only (code) / CC BY-NC-SA 4.0 (assets)
     mod_group_id=com.merlinkitsune.astral_dice
     # 多版本分支约定:版本号带加载器后缀(+fabric_1.20.1),与另三线产物区分
    -# ⚠️ 2026-10-01 用户裁决:本线**并入开发线 `multi-dev-next`**,版本号与三线统一为
    -#    **2.0.0-SNAPSHOT.19**(`-alpha.x` 预发布后缀取消)。
    -#    此后 fabric 与另三线同批演进;正式发布线口径待对外发布时再定。
    -mod_version=1.5.0+fabric_1.20.1
    +# 2026-11-02:本线单独出 `1.5.0-hotfix` 热修(修 `enable_chip_recipes` 开关无效),另三线仍为 `1.5.0`。
    +#    互通号由 VersionGate.majorMinor() 遇 `-`/`+` 截断,取值 `1.5`,与三条 1.5.0 线一致。
    +mod_version=1.5.0-hotfix+fabric_1.20.1
     
     ## 前置库坐标(StarEngine Lib / fabric 平台,库仓库先 ./gradlew :fabric-1.20.1:publishToMavenLocal)
     # ⚠️ lib_version_range 同样必须是 Fabric 谓词语法:空格分隔的多个单谓词 = AND

### 2.3 落定后的该段（实读，行号取自文件）

    25| # 多版本分支约定:版本号带加载器后缀(+fabric_1.20.1),与另三线产物区分
    26| # 2026-11-02:本线单独出 `1.5.0-hotfix` 热修(修 `enable_chip_recipes` 开关无效),另三线仍为 `1.5.0`。
    27| #    互通号由 VersionGate.majorMinor() 遇 `-`/`+` 截断,取值 `1.5`,与三条 1.5.0 线一致。
    28| mod_version=1.5.0-hotfix+fabric_1.20.1

### 2.4 另三线未被牵连（实读 mod_version 行）

    forge-1.20.1/gradle.properties         line 41: mod_version=1.5.0+forge_1.20.1
    neoforge-1.21.1/gradle.properties      line 37: mod_version=1.5.0+neoforge_1.21.1
    neoforge-26.1.2/gradle.properties      line 54: mod_version=1.5.0+neoforge_26.1.2

### 2.5 行尾（既有约定核查）

    fabric-1.20.1/gradle.properties                      bytes=   5266 LF=   93 CRLF=    0 bareLF=   93 CRCRLF=0
    CHANGELOG.md                                         bytes= 568797 LF= 2881 CRLF= 2881 bareLF=    0 CRCRLF=0
    CHANGELOG_ZH.md                                      bytes= 509112 LF= 2450 CRLF= 2450 bareLF=    0 CRCRLF=0
    release/1.5.0-hotfix/PLAYER_CHANGELOG.md             bytes=   1008 LF=   19 CRLF=   19 bareLF=    0 CRCRLF=0
    release/1.5.0-hotfix/PLAYER_CHANGELOG_ZH.md          bytes=    980 LF=   17 CRLF=   17 bareLF=    0 CRCRLF=0

即：gradle.properties 保持 LF（93 个 LF 全是裸 LF，无 CRLF）；两份 CHANGELOG 与两份玩家日志全 CRLF、零裸 LF、零 CRCRLF。

## 3. 构建与产物

### 3.1 命令（pwsh 7.6.9，经 work/_build-hotfix150-fabric.ps1 逐字执行）

    ./gradlew.bat :fabric-1.20.1:build --console=plain -x pushToGame

### 3.2 原始输出（work/hotfix-150-build.log 全文，45 行）

    Starting a Gradle Daemon, 3 busy Daemons could not be reused, use --status for details
    Calculating task graph as configuration cache cannot be reused because Gradle property 'mod_version' has changed.
    
    > Configure project :
    整合包根目录 (MC_HOME): D:/.minecraft          ← 日志该行为控制台 GBK 下显示为乱码，实义如此
    
    > Configure project :fabric-1.20.1
    Fabric Loom: 1.17.21
    
    > Configure project :forge-1.20.1
    forgeVersionRange: [47.4.10,48) / forgeLanguageRange: [47,48) (derived from forge_version=1.20.1-47.4.10)
    
    > Task :fabric-1.20.1:remapSourcesJar SKIPPED
    > Task :fabric-1.20.1:processTestResources NO-SOURCE
    > Task :fabric-1.20.1:validateAccessWidener UP-TO-DATE
    > Task :fabric-1.20.1:processIncludeJars UP-TO-DATE
    > Task :fabric-1.20.1:compileJava UP-TO-DATE
    > Task :fabric-1.20.1:processResources
    > Task :fabric-1.20.1:classes
    > Task :fabric-1.20.1:compileTestJava NO-SOURCE
    > Task :fabric-1.20.1:testClasses UP-TO-DATE
    > Task :fabric-1.20.1:test NO-SOURCE
    > Task :fabric-1.20.1:check UP-TO-DATE
    > Task :fabric-1.20.1:jar
    > Task :fabric-1.20.1:remapJar
    > Task :fabric-1.20.1:assemble
    > Task :fabric-1.20.1:build
    
    > Task :fabric-1.20.1:pushToRootBuild
    pushToRootBuild: pushed astral_dice-1.5.0-hotfix+fabric_1.20.1.jar -> F:\MCProject\astral_dice_multiloader\build\libs
    
    > Task :fabric-1.20.1:pushToDevRun
    pushToDevRun: pushed astral_dice-1.5.0-hotfix+fabric_1.20.1.jar -> F:\MCProject\astral_dice_multiloader\run\fabric-1.20.1\mods
    
    [Incubating] Problems report is available at: file:///F:/MCProject/astral_dice_multiloader/build/reports/problems/problems-report.html
    
    Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.
    
    You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.
    
    For more on this, please refer to https://docs.gradle.org/9.5.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.
    
    BUILD SUCCESSFUL in 28s
    8 actionable tasks: 5 executed, 3 up-to-date
    Configuration cache entry stored.

退出码 0；`-x pushToGame` 生效（只跑 pushToRootBuild / pushToDevRun，未碰整合包）。第 2 行同时证明配置缓存因 mod_version 变化而失效重算，不是复用旧图。

### 3.3 产物

    astral_dice-1.5.0-hotfix+fabric_1.20.1.jar   bytes : 2944301
                                                 sha256: 8a7edad09cf77059dde32b91fd585b4a402265c59832415ee827935d2108d6f2
                                                 entries: 2307

对比同目录上一次 1.5.0 的 jar：2,944,296 B / sha256 d185d7dd24e14dddbfb17486f405cddd77dd7891227876323ca52bad60c84b31 / 2307 entries（条目数一致，只多了版本串，多 5 B）。

## 4. 开 jar 核对（4 项）

### 4.1 ① fabric.mod.json 的 version 已展开

    {"id": "astral_dice", "name": "Astral Dice", "version": "1.5.0-hotfix+fabric_1.20.1", ...}

原文行：`"version": "1.5.0-hotfix+fabric_1.20.1",` —— 由 build.gradle:294 `templateProps` + :304-306 `filesMatching('fabric.mod.json') { expand(templateProps) }` 展开，PASS。

### 4.2 ② 类路径版本资源 —— 本线不存在（偏差项，非本次引入）

    astral_dice-1.5.0+fabric_1.20.1.jar        entries=2307  version-properties=[]
    astral_dice-1.5.0-hotfix+fabric_1.20.1.jar entries=2307  version-properties=[]

事实链：

1. fabric 线没有 generateModMetadata 任务，也没有 astral_dice_version.properties 模板；`fabric-1.20.1/build.gradle` 里与资源展开相关的只有 :294 `def templateProps = [`、:304-306 `processResources { inputs.properties(templateProps); filesMatching('fabric.mod.json') { expand(templateProps) } }`。另三线才有该任务（forge-1.20.1/build.gradle:172、neoforge-1.21.1/build.gradle:213、neoforge-26.1.2/build.gradle:242）。
2. 因此 jar 内没有 astral_dice_version.properties（新旧两个 jar 都实测没有，条目数都是 2307）—— 这是本线既有状态，不是本次改版本号造成的。
3. 运行期影响可控：`VersionGate.java:46` `VERSION_RESOURCE = "/astral_dice_version.properties"`；`:180-194` `readVersionResource()` 拿不到资源时 `in == null` → 打 warn「类路径上找不到 ...，改用加载器元数据解析版本号」→ 返回 null；`:160-178` `resolveFullVersion()` 随后退回 `ModList.get().getModContainerById(AstralDiceMod.MODID)...getVersion()`，在 fabric 上即 fabric.mod.json 的 version，也就是 1.5.0-hotfix+fabric_1.20.1 —— 与 ① 已经核对过的值完全相同。

结论：任务清单第 ② 项（「类路径版本资源内含 mod_version=1.5.0-hotfix+fabric_1.20.1」）在本线**不成立**，无法按字面核到。**未擅自给 fabric 线加 generateModMetadata / 资源展开**：那会改动构建配置与 jar 内容，超出「只改版本号」的本次范围，且版本号实际取值已由 fabric.mod.json 兜底取得。是否给本线补齐该资源，留待 Lead 决定。

### 4.3 ③ 门控条数仍为 67 / 67

    recipes (data/<ns>/recipes/**): 125
    advancements (data/<ns>/advancements/**): 109
    gated recipes     : 68 | with chip cond: 67
    gated advancements: 67 | with chip cond: 67
    non-chip gated recipes    : ['data/astral_dice/recipes/astral_guide.json']
    non-chip gated advancements: []
    chip recipes by ns: {'astral_dice': 67}
    chip advancements by ns: {'astral_dice': 67}
    recipes with chip in path: 69  gated: 67  ungated: ['data/astral_dice/recipes/blank_chip_duplicate.json', 'data/astral_dice/recipes/chip_shop.json']

逐条说明：

- 带 `astral_dice:chip_recipes_enabled` 条件的**筹码配方 67 条**、**对应进度 67 条**，与上一次门控工作（work/fabric-chip-recipe-gate.md）记录的数字完全一致 ⇒ 本次只改版本号，没有影响门控。
- 另外 1 条 gated recipe 是 `astral_guide.json`，条件是 `fabric:all_mods_loaded` / `patchouli`（前置手册联动，属既有实现），不在筹码门控计数内。
- 未门控的两条含 chip 字样的配方：`blank_chip_duplicate`（空白筹码复制，按既有设计不进筹码开关）与 `chip_shop.json`（筹码商店方块的合成，不是筹码本体配方）。
- 样例正文（`data/astral_dice/recipes/abyss_reagent_chip.json`）：

      {
        "fabric:load_conditions": [ { "condition": "astral_dice:chip_recipes_enabled" } ],
        "type": "minecraft:crafting_shaped", ...

### 4.4 ④ 内嵌库仍为 0

    META-INF/jars entries: 0

（上一版 1.5.0 已撤除 Loom 的 include，本次沿用，PASS。）

## 5. VersionGate 对 1.5.0-hotfix+fabric_1.20.1 的解析结论

代码依据（fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionGate.java）：

    :95-97   private static boolean isSnapshot(String version) {
                 return version != null && version.toUpperCase(Locale.ROOT).contains("SNAPSHOT");
             }
    :105-114 private static String interopNumberOf(String version) {   // SNAPSHOT: 前缀幂等；否则 majorMinor()
             }
    :123-144 public static String majorMinor(String version) {
                 String trimmed = version.trim();
                 int cut = trimmed.length();
                 for (int i = 0; i < trimmed.length(); i++) {
                     char c = trimmed.charAt(i);
                     if (c == '-' || c == '+' || c == ' ') { cut = i; break; }   // 遇 - / + / 空格 截断
                 }
                 String core = trimmed.substring(0, cut);                       // "1.5.0-hotfix+fabric_1.20.1" -> "1.5.0"
                 String[] parts = core.split("\\.");
                 String major = leadingDigits(parts.length > 0 ? parts[0] : "");   // "1"
                 ...
                 String minor = leadingDigits(parts.length > 1 ? parts[1] : "");   // "5"
                 return major + "." + (minor.isEmpty() ? "0" : minor);            // "1.5"
             }

推导：`1.5.0-hotfix+fabric_1.20.1` 在第 5 个字符 `-`（下标 5）处被截断 → core = `1.5.0` → major=`1`、minor=`5` → **互通号 `1.5`**。`isSnapshot()` 只查子串 `SNAPSHOT`，该串不含 → 不判 SNAPSHOT，走 majorMinor 分支。与三条 1.5.0 线（`1.5.0+forge_1.20.1` / `1.5.0+neoforge_1.21.1` / `1.5.0+neoforge_26.1.2` 同样得 `1.5`）**一致**，四条线仍可互通。

补充：`1.5.0-hotfix` 中的 `hotfix` 不参与互通号计算（`-` 之前的部分才是），因此不会因为热修号而产生互通不匹配。

## 6. 文档

### 6.1 CHANGELOG（中英逐条对称，插在 1.5.0 之上）

    CHANGELOG.md:7:## 1.5.0-hotfix (2026-11-02)      ... CHANGELOG.md:23:## 1.5.0 (2026-11-02)
    CHANGELOG_ZH.md:7:## 1.5.0-hotfix (2026-11-02)   ... CHANGELOG_ZH.md:21:## 1.5.0 (2026-11-02)

    CHANGELOG.md    9:### Bug Fixes                17:### Player-facing changes
    CHANGELOG_ZH.md 9:### 已修复BUG                16:### 玩家可见变更

每条内容（两侧语义一一对应）：① 修复 fabric-1.20.1 的 `enable_chip_recipes` 开关此前无效（配方无生成条件块），现改用 Fabric 官方资源条件 `fabric:load_conditions` 门控 67 条筹码配方与对应的 67 条进度，改配置后重启生效；② fabric-1.20.1 版本号定为 `1.5.0-hotfix`，另三线仍为 `1.5.0`，互通号仍 `1.5`。

字节数变化：CHANGELOG_ZH.md 508368 → 509112（+14 行），CHANGELOG.md 567956 → 568797（+16 行，英文行更长用了 3 行续行）。补丁脚本对写入前后都断言「0 个 CRCRLF、0 个裸 LF」，两条都通过。

### 6.2 玩家更新日志（新建，CRLF）

- `release/1.5.0-hotfix/PLAYER_CHANGELOG_ZH.md`：980 B / 17 行，3 节（发布说明 / 修了什么 / 怎么生效），玩家口吻，未出现文件路径、类名、`fabric:load_conditions` 之类工程细节。
- `release/1.5.0-hotfix/PLAYER_CHANGELOG.md`：1008 B / 19 行，逐条对应。
- 沿用 `release/1.5.0/` 的标题体例（`# 星之骰戏 (Astral Dice) <版本> 玩家更新日志` / `# Astral Dice <版本> — Player Changelog`）与 CRLF 行尾。

## 7. 守门脚本

### 7.1 python tools/check_changelog_parity.py

    WARN P2 ## 1.3.0: entries equal (58/58) but bold-start counts differ EN=53 ZH=40 (historical style difference; not a failure)
    WARN P2 ## 2.0.0-SNAPSHOT.5: entries equal (2/2) but bold-start counts differ EN=2 ZH=1 (historical style difference; not a failure)
    CHANGELOG_PARITY: OK sections=27 entries=791/791
    exit=0

（两条 WARN 是历史版本既有的风格差异，与本次无关；节数 27、条目 791/791 完全对称。）

### 7.2 python tools/check_lang_sync.py

    [OK]   neoforge-1.21.1: en_us=1142 键，3 份语言键集一致
    [OK]   forge-1.20.1: en_us=1142 键，3 份语言键集一致
    [OK]   neoforge-26.1.2: en_us=1166 键，3 份语言键集一致
    [OK]   fabric-1.20.1: en_us=1151 键，3 份语言键集一致
    exit=0

四线全 [OK]，本次未动任何 lang 文件。

## 8. 未确证 / 偏差项（需要 Lead 裁决或后续跟进的）

1. **§4.2 的类路径版本资源缺失**：fabric 线没有 `astral_dice_version.properties`，VersionGate 走加载器元数据兜底，最终取值与要求值一致（1.5.0-hotfix+fabric_1.20.1 → 互通 1.5）。已按事实记录，未擅自改 build.gradle。若要求本线也具备该资源（与另三线行为严格一致），需另开一次改动。
2. **未做游戏内验证**：本次只做到静态核对（jar 内容 / 代码推导），没有启动客户端或服务端，也没有实测 `enable_chip_recipes=false` 时筹码配方在游戏中确实消失、以及版本互通握手在真机上的表现。按仓库红线（默认不启动实机）未自行开游戏。
3. **旧产物仍在**：`fabric-1.20.1/build/libs/astral_dice-1.5.0+fabric_1.20.1.jar`（2,944,296 B）与根 `build/libs` 里三线旧 jar 未删除 —— 属构建目录（gitignore），未清理以免影响他人正在使用的产物。
4. **构建的副作用**（pushToRootBuild / pushToDevRun，非 -x 排除项）：写了 `build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar` 与 `run/fabric-1.20.1/mods/...`，两处均在 .gitignore 内（.gitignore:60 `build/`、:61 `run/`），不产生待提交项。
5. **无任何 git 写操作**：只用了 `git status` / `git diff` / `git check-ignore` 等只读命令；commit / push / tag 留给 Lead。当前待提交项为 `M CHANGELOG.md`、`M CHANGELOG_ZH.md`、`M fabric-1.20.1/gradle.properties`、`?? release/1.5.0-hotfix/`。
6. **CHANGELOG 里「重启生效」的表述**来自既有门控工作的结论（Fabric 资源条件在 world/datapack 加载时求值），本次未在真机复验。
