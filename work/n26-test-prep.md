# neoforge-26.1.2 · 1.5.0 发布实机测试 —— 静态准备报告

- 生成时间：2026-11-02（Asia/Hong_Kong）。本报告只做**静态准备**：全程未启动 Minecraft、未跑 Gradle、未占用 25565/25585/25575。
- 仓库 `F:/MCProject/astral_dice_multiloader`；branch `multi-main`；HEAD `fd990c15`；git 只读（未做 add/commit/push/tag/checkout/stash；`run/` 被 .gitignore:61 忽略，改动不进版本库）。
- 工具链：pwsh 7.6.9；python `C:/Users/xmace/.local/bin/python.exe`（3.12.16）；java `C:/Program Files/Zulu/zulu-25/bin/java.exe`。
- 参考手册：`work/release150-test-runbook.md`（本报告 §7.2 B3 修正了其中 26.1.2 的 env/launch/stop 口径）。

## 0. 结论速览

| # | 任务 | 结论 | 证据 |
|---|---|---|---|
| 1 | prodlaunch 双版差异 | 26.1.2 版缺 4 类「可核对性/参数化」处理，已补 8 处小 patch（有备份） | §1.2 / §1.3 |
| 2 | dry-run | `DRY-RUN ok`；argv 48 条；classpath 102（missing 0）；未解析占位符 none | §1.4 |
| 3 | 资产 | 1.5.0 正式 jar 三处同哈希；**prodgame 仍在用 rc.7 ⇒ 开跑前必须换** | §2 |
| 4 | 世界 | `prodgame/saves/testworld` 现成，DataVersion **4790 == 26.1.2**；1.21.1 无 testworld 且不需要 | §3 |
| 5 | 用例 validate | `MT_VALIDATE: OK — RELEASE150-26.1.2`（rc=0），**无需改用例** | §4 |
| 6 | Jade×YSM | 本次生产形态**没有 Jade** ⇒ 冲突不成立；最小改法见 §5（开跑时再改 + 备份） | §5 |
| 7 | 产物 | `build/libs` 14 个 jar；jar 断言按 mtime 正确选中 1.5.0 | §6 |
| 8 | 开跑条件 | **具备（条件性具备）**：开跑瞬间需做 3 个动作（B1/B2/B3），均可回滚 | §7 |

**是否具备开跑条件：具备。** 需要 3 个开跑前置动作（§7.2），全部可 2 分钟内完成且有脚本/清单；未发现用例缺陷、产物缺陷或世界缺失。

## 1. prodlaunch.py 差异对比与处理

### 1.1 26.1.2 原始版（163 行 / 5014 B）行为

读实例 json（`D:/.minecraft/versions/26.1.2-NeoForge 模组测试/26.1.2-NeoForge 模组测试.json`，133 libraries / `arguments.jvm` 19 条 / `arguments.game` 32 条）→ 按 `rules` 过滤库 → 拼 classpath → 占位符替换 → `java [jvm] <mainClass> [game]`；env 里 `TEMP=TMP=TMPDIR=.tmp` 且 `pop("JAVA_TOOL_OPTIONS")`；`Popen(..., cwd=GAMEDIR, creationflags=0x200)` 分离启动并写 `.tmp/prod-run.pid`。

三个制胜细节（它比 1.21.1 版更对的地方，不要按 1.21.1 改）：

1. `cwd=GAMEDIR` —— 这是生产日志落 `prodgame/logs/latest.log` 的直接原因（log4j appender 路径 `logs/latest.log` 相对 cwd 解析；实证见 §7.2 B1）。
2. `--quickPlaySingleplayer` 合并为**等号形**（:157-160 `game[i:i+2] = ["--quickPlaySingleplayer=" + game[i+1]]`）——空格形会静默停在主菜单。
3. `creationflags=DETACHED_PROCESS` + `stdin=DEVNULL` —— 命令行返回后游戏独立存活。

### 1.2 与 `run/1.21.1/.tmp/prodlaunch.py`（217 行，1.21.1 实机 PASS 用过）逐项差异判定

| # | 1.21.1 版的处理 | 26.1.2 原始状态 | 是否需要 | 判据 |
|---|---|---|---|---|
| 1 | `subs` 含 `classpath_separator=";"`、`user_type="legacy"` | 无 | **不需要** | 26.1.2 实例 json 的占位符全集（21 个，实测枚举）不含这两键，加了是死码 |
| 2 | `lib_path()` 支持 4 段 classifier | 只有 `downloads.artifact.path` + 3 段回退 | **加了（硬化）** | 57 条库名是 4 段（lwjgl/jtracy/netty natives）；它们都有 `downloads.artifact`，故原版 missing 也是 0 ⇒ 补丁**不改变本次 classpath 组成**（前后都 102 条 / missing 0），只消除「downloads 缺失时静默少一条」的隐患 |
| 3 | classpath 去重（`Collectors.toMap` 重复键会当场崩） | 无 | **不需要** | 按规则解析后 101 条库路径**重复数 = 0**（distinct 101），不存在 1.21.1 那种 gson 重复 |
| 4 | quickPlay 等号形合并 | **已有** | 已在 | 见 §1.1 |
| 5 | `-Dautojs.port=<N>` 注入（端口抢占时换端口） | 无 | **加了（可选，默认不注入）** | 本线端点固定 25585；仅在并发抢占时用 `--autojs-port`。依据：javap 取证 `com.sighs.autojs.config.AutoJSConfig.applyLaunchPort` — `ldc "autojs.port"` → `System.getProperty`，另支持 env `AUTOJS_PORT`，日志 `"MCP port overridden to {} by {}"` |
| 6 | 未解析占位符上报（`leftovers`） | 无 | **加了** | 防 `${...}` 静默漏替换；实测 leftovers = none |
| 7 | dry-run 逐条打印 `ARG|` + 写 `prod-run.cmdline.txt` | 只打 4 行摘要 | **加了** | 任务 2 的核心缺口：原版 dry-run 打不出命令行 |
| 8 | `--xmx` / `--username` / `--game-arg`、`argval()` 越界安全、`Xms2G`、`makedirs`、`stdin=DEVNULL` | 硬编码 `-Xms1G -Xmx4G` / `DevTester` | **加了（参数化，默认值与原始一致）** | `argval` 越界安全避免「flag 放最后」时 IndexError |

**结论：26.1.2 版缺的是「可核对性」与「参数化」，不缺血性逻辑**——rules 过滤 / features 门控 / 占位符替换 / `JAVA_TOOL_OPTIONS` 剥离四件它本来就有；1.21.1 版里真正影响启动的三条（等号 quickplay、JAVA_TOOL_OPTIONS、detached）26.1.2 版也都在。不移植的 2 条已给出实测依据。

26.1.2 实例 json 占位符全集（21）：`auth_player_name / version_name / game_directory / assets_root / assets_index_name / auth_uuid / auth_access_token / clientid / auth_xuid / version_type / resolution_width / resolution_height / quickPlayPath / quickPlaySingleplayer / quickPlayMultiplayer / quickPlayRealms / natives_directory / launcher_name / launcher_version / classpath / library_directory`。
（`arguments.game` 的 5 个条件块 `--demo / --width,--height / --quickPlayPath / --quickPlaySingleplayer / --quickPlayMultiplayer / --quickPlayRealms` 全部按 features 门控，脚本一律 skip ⇒ 不启用时不会留字面量。`jvm` 条件块 `-XstartOnFirstThread`(osx) / `HeapDumpPath`(windows) / `-Xss1M`(x86) 本机只命中 windows 那条。）

### 1.3 已实施 patch 清单

备份：`run/26.1.2/.tmp/prodlaunch.py.bak-before-release150-run`（5014 B）；补丁后 `prodlaunch.py` 6796 B。

1. `argval()` 越界安全；新增 `--xmx`（默认 4G）、`--username`（默认 DevTester）、`--autojs-port`。
2. `lib_path()` 支持 4 段 classifier。
3. `auth_player_name` 取 `--username`。
4. quickPlay 等号形合并（沿用原逻辑，加注释说明不能空格形）。
5. `-Dautojs.port=<N>` 注入（仅当传 `--autojs-port`）。
6. 未解析占位符上报（`leftovers`）。
7. dry-run 增强：打印 java / instance / gamedir / natives / assetsidx+version / classpath 计数 / unresolved / argv 条数，并逐条 `ARG|`（>400 字符截断）。
8. 启动段 `makedirs(TMP 与 logpath 父目录)` + `stdin=DEVNULL` + 写 `prod-run.cmdline.txt`；`cwd=GAMEDIR` 保持不变。

校验：`py_compile` rc=0；`--dry-run` rc=0；无进程产生（dry-run 在 `Popen` 前 `sys.exit(0)`）。

> `run/26.1.2/.tmp/prod-run.cmdline.txt` 在 dry-run 后**不生成**——该文件只在真实启动分支写；dry-run 的逐条 `ARG|` 就是本次核对凭据，真实开跑后可拿该文件复核同一条命令行。

### 1.4 dry-run 实测命令行（`--dry-run --quickplay testworld`，rc=0）

摘要：`classpath entries: 102 missing: 0` / `unresolved placeholders: none` / `argv entries: 48` / `DRY-RUN ok`。

- classpath 102 = 规则通过的 101 条库（distinct 101、重复 0、missing 0）+ 实例 jar `26.1.2-NeoForge 模组测试.jar`（38,113,927 B）；被 rules 排除 32 条（linux/macos natives、java-objc-bridge 等）。classpath 字符串 8728 字符。
- `assetsidx : 30 | version: 26.1.2-NeoForge` ⇒ **assets_index = 30**；**MC 版本 26.1.2**。

48 条 argv（真实传参顺序）：

```
 0  C:/Program Files/Zulu/zulu-25/bin/java.exe
 1  -Xms1G
 2  -Xmx4G
 3  -XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump
 4  --sun-misc-unsafe-memory-access=allow
 5  --enable-native-access=ALL-UNNAMED
 6  -Djava.library.path=<INST>/26.1.2-NeoForge 模组测试-natives
 7  -Djna.tmpdir=<同上>
 8  -Dorg.lwjgl.system.SharedLibraryExtractPath=<同上>
 9  -Dio.netty.native.workdir=<同上>
10  -Dminecraft.launcher.brand=prodlaunch
11  -Dminecraft.launcher.version=1.0
12  -cp
13  <classpath，8728 字符；首项 libraries/at/yawk/lz4/lz4-java/1.10.1/lz4-java-1.10.1.jar>
14  -Djava.net.preferIPv6Addresses=system
15  -DlibraryDirectory=D:/.minecraft/libraries
16  --add-opens
17  java.base/java.lang.invoke=ALL-UNNAMED
18  --add-exports
19  jdk.naming.dns/com.sun.jndi.dns=java.naming
20  net.neoforged.fml.startup.Client        ← mainClass（26.1.2 引导入口，不是 net.minecraft.client.main.Main）
21  --username
22  DevTester
23  --version
24  26.1.2-NeoForge
25  --gameDir
26  F:\MCProject\astral_dice_multiloader\run\26.1.2\prodgame
27  --assetsDir
28  D:/.minecraft/assets
29  --assetIndex
30  30
31  --uuid
32  00000000000000000000000000000001
33  --accessToken
34  0
35  --clientId
36  0
37  --xuid
38  0
39  --versionType
40  release
41  --quickPlaySingleplayer=testworld
42  --fml.neoForgeVersion
43  26.1.2.109
44  --fml.mcVersion
45  26.1.2
46  --fml.neoFormVersion
47  1
```

要点：

- `--gameDir` 是**反斜杠**形态，命令行因此含 `run\26.1.2` 子串 ⇒ 命中 harness 进程标记（`scripts/test/lib/Mt.Paths.psm1:260-280` 的 `run\<版本>`），客户端存活判定与 `mt_stop` 认领都成立。
- `mainClass = net.neoforged.fml.startup.Client` ⇒ harness 已适配（`scripts/test/lib/Mt.Proc.psm1:944-951` 显式识别该入口），不会误判「客户端未在运行」。
- 不传 `--quickplay` 时该参数整条不出现（`rule_ok` 的 `is_quick_play_singleplayer` 门控），argv 47 条。
- 控制台把中文实例名显示成乱码（`ģ�����`）是 pwsh/git-bash 代码页问题；脚本内 UTF-8，不影响启动。

## 2. 资产盘点

### 2.1 三个 mods 目录（实测 2026-11-02）

| 目录 | 条目 | 说明 |
|---|---|---|
| 整合包 `D:/.minecraft/versions/26.1.2-NeoForge 模组测试/mods` | **95 jar** | 完整玩家环境 |
| `run/26.1.2/mods` | 13 jar + 2 `.bak` | 测试最小集（dev 形态；**没有** curios/starengine，dev 由 Gradle classpath 供给） |
| `run/26.1.2/prodgame/mods` | 15 jar + 1 `.bak` | **生产形态实际加载面** = 最小集 + `curios` + `starengine` |

整合包里与本测试相关的 jar（文件名 + 字节）：

| 文件 | 字节 | 备注 |
|---|---|---|
| `astral_dice-1.5.0+neoforge_26.1.2.jar` | 2,875,237 | **正式产物**；sha256 `dd22295c…`；与 `build/libs`、`run/26.1.2/mods` 逐字节相同 |
| `[是，史蒂夫模型] ysm-2.6.5-neoforge+mc26.1-hotfix.jar` | 63,016,428 | hotfix 版；与 prodgame 同名 jar **同哈希** |
| `curios-neoforge-15.0.0+26.1.2.jar` | 441,630 | sha256 `ea1e92cd…`；prodgame 的 `curios-dRnRThvD.jar` 与之**同哈希**（仅改名） |
| `[帕秋莉手册] patchouli-neoforge-26.1-94.jar` | 672,884 | astral_dice 的 optional 依赖 |
| `[玉 🔍] Jade-mc26.1-NeoForge-26.1.11.jar` | 1,030,012 | 见 §5 |
| `JadeAddons-26.1.2-NeoForge-26.0.2.jar` | 87,795 | 见 §5 |
| AutoJS / kubejs / rhino | — | 整合包**0 命中** ⇒ 纯测试专用 |

prodgame 的 15 个 jar（生产形态真实加载面）：`astral_dice-1.5.0-rc.7+…`(2,875,013) / `AutoJS-neoforge-26.1-1.0.0.jar`(164,801) / `kubejs-neoforge-26.1.2-8.0.6.jar`(2,214,228) / `rhino-2101.2.8-build.91.jar`(980,936) / `curios-dRnRThvD.jar`(441,630) / `starengine-neoforge-26.1.2-1.1.0.jar`(98,970) / `[是，史蒂夫模型] ysm-2.6.5-neoforge+mc26.1-hotfix.jar`(63,016,428) / `ModernUI-NeoForge-26.1.2-3.13.0.5-universal.jar`(25,732,554) / `iris-neoforge-1.11.4+mc26.1.2.jar`(2,756,643) / `sodium-neoforge-0.9.1+mc26.1.2.jar`(1,185,970) / `collective-26.1.2-8.32.jar`(1,054,031) / `modernfix-neoforge-5.27.22+mc26.1.2.jar`(505,496) / `ferritecore-9.0.0-neoforge.jar`(71,947) / `better-advanced-tooltips-2601.1.0-build.10.jar`(38,178) / `superflatworldnoslimes-26.1.2-3.6.jar`(28,935)。

### 2.2 测试专用 vs 整合包自带

- **仅测试侧有**（整合包 0 命中）：`AutoJS`、`kubejs`、`rhino` —— 通道与脚本能力。
- **仅生产 gameDir 补充**（`run/26.1.2/mods` 没有）：`curios`（astral_dice 的 **required** 依赖，versionRange `[15,)`）、`starengine`（required；jar 内 `META-INF/jarjar/` 已内嵌同版本 98,970 B，外置放置已被 22:39 那次生产运行证明可正常加载）。
- **版本差异（记录，不处理）**：整合包 `sodium 0.9.2`(1,260,562)，测试集钉 `0.9.1`(1,185,970)；`iris/moderfix/ferritecore/collective/ysm` 两侧同名同版或同哈希。
- `run/26.1.2/mods` 的两个 `.bak`：`…rc.7…old-20261101.jar.bak`(2,866,366)、`…rc.7…jar.old-20261101-2240.bak`(2,875,164) —— 历史部署残留，不影响加载。

### 2.3 哈希对账（sha256）

| 位置 | 字节 | sha256（前 8） | 结论 |
|---|---|---|---|
| `neoforge-26.1.2/build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar` | 2,875,237 | `dd22295c` | 本次发布产物 |
| 整合包 `mods/astral_dice-1.5.0+neoforge_26.1.2.jar` | 2,875,237 | `dd22295c` | 与产物**逐字节相同** |
| `run/26.1.2/mods/astral_dice-1.5.0+neoforge_26.1.2.jar` | 2,875,237 | `dd22295c` | 同上 |
| `prodgame/mods/astral_dice-1.5.0-rc.7+…jar` | 2,875,013 | `6b278f07` | **旧 rc.7（当前在用）** |
| `prodgame/mods/…rc.7….jar.compat-on.bak` | 2,875,164 | `7e2c56ae` | 又一份 rc.7，与 `run/mods` 的 `-2240.bak` 同哈希 |
| `build/libs/astral_dice-1.5.0-rc.7+…jar` | 2,875,210 | `a48d099b` | 第三个 rc.7 构建 |

⇒ **rc.7 存在三个不同构建**（2,875,013 / 2,875,164 / 2,875,210）。`docs/features/ysm-compat-err54-and-verification.md` §5 的「两处 sha256 一致」是 rc.7 当次状态；现在 prodgame 在用的那份与 `build/libs` 的 rc.7 不同源。1.5.0 三处一致，无此问题。

### 2.4 资产侧待办

prodgame 当前加载 **rc.7**（见 §7.2 B2）。用例 `type=log` 的 `astral_dice-1.5.0+neoforge_26.1.2.jar` 与 `modver=1.5.0+` 两条断言会直接暴露这一点。

## 3. 世界方案

### 3.1 现状（实测）

| 路径 | level.dat | DataVersion | LevelName | GameType |
|---|---|---|---|---|
| `run/26.1.2/saves/testworld` | 859 B | **4790** | testworld | 1（创造） |
| `run/26.1.2/testworld`（顶层副本） | 813 B | **4790** | testworld | 1 |
| `run/26.1.2/prodgame/saves/testworld` | — | **4790** | testworld | 1 |

- 26.1.2 客户端 jar 内 `version.json`：`world_version = 4790`、`protocol_version = 775`、`stable = true`。
- 三份 testworld 的 DataVersion 全部 = 4790 ⇒ **都是 26.1.2 原生世界**，无需升级。
- `prodgame/saves/testworld` 内含 `data/ dimensions/ players/ serverconfig/ datapacks/ icon.png kubejs_persistent_data.nbt`，另有 `session.lock`（3 B，上次运行遗留，客户端启动会接管）。

### 3.2 「从 1.21.1 复制 testworld」—— 不需要，也不建议

1. **源头不存在**：`run/1.21.1/saves` 为空；`run/1.21.1/testworld/level.dat` 与 `run/1.21.1/saves/testworld/level.dat` 均 MISS。1.21.1 的世界由 `mt_env` 现场生成（runbook §3.1），没有可复制产物。
2. **格式判断依据**：世界格式由 `level.dat` 的 `DataVersion` 决定，26.1.2 = **4790**；1.21.1 远低于该值（跨多个大版本）。低版本世界在 26.1.2 打开会被 DataFixerUpper **就地升级**：技术上能开，但（a）升级**不可逆改写存档**；（b）升级后的方块/组件语义可能与 26.1.2 原生世界不同，会把「环境差异」混进发布验收；（c）26.1.2 自己的 testworld 已现成且是 4790。
   ⇒ **直接用 `prodgame/saves/testworld`**，不复制、不升级。

### 3.3 quickplay 口径

- 必须**等号形** `--quickPlaySingleplayer=testworld`（空格形静默停主菜单，1.21.1 已实证）；补丁已保证。
- 世界名 = 目录名 `testworld`，解析到 `<gameDir>/saves/testworld`；生产形态 gameDir = prodgame ⇒ 加载 `prodgame/saves/testworld`（存在 ✓）。
- 用例第 4 步 `/gamemode creative` 与世界的 GameType=1 一致，不冲突。

## 4. 用例 validate 结果

```
pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case scripts/test/cases/RELEASE150-26.1.2.json
→ MT_VALIDATE: OK — RELEASE150-26.1.2      (rc=0)
```

- **用例无结构缺陷；未做任何修改，也未生成 `.bak-before-release150-run`**（备份只在真改文件时才需要）。
- 体量：`case_id=RELEASE150-26.1.2`，31 steps，27 asserts（release 1 / log 19 / eval 3 / absent 1 / jar 1 / crash 1 / vision 2）。
- 结构核对（后续改动用例时须保持一致）：
  - **19 条 `type=log` 都没有写 `source`** ⇒ 全部走默认 `latest` = `run/26.1.2/logs/latest.log`（这条把 §7.2 B1 变成硬前置）。
  - 19 条中只有第 1 条 `astral_dice-1\.5\.0\+neoforge_26\.1\.2\.jar` 是 `scope=whole`，其余 18 条是默认 case 增量窗口。
  - `[REL150]` 机器行**全部由用例自身 `eval_js` 用 `console.info` 打印**（不是模组发射）：对 1.5.0 产物逐 class 扫 ASCII `REL150` = **0 命中，属预期**。
  - 27 条断言**没有** `optional`/`severity` 之类门控字段 ⇒ 每条都必须命中。
- 关键 id/类已逐个核到实物（防「断言对但目标不存在」）：
  - `CHIP_SHOP_OPEN` 在 `com/merlinkitsune/astral_dice/shop/ChipShopOpener.class` 内（断言 #13 有据）；
  - `astral_dice.guide.entry.abyss_reagent_chip.1` 在 `assets/astral_dice/lang/{zh_cn,en_us,ja_jp}.json` 与帕秋莉条目内 ⇒ 断言 #11 的 `Component.translatable` 有真实译文，**即使不装 patchouli 也命中**（它读 lang 键，不读书）；
  - AutoJS 1.0.0 内确认存在 `modVersion` / `state` / `inventory` / `command` / `grab`；`AutoJS.state()` 的 JSON 键实测含 `gameMode / xpLevel / armor / dimension / health / held`，与 step16 用法一致。
- `scripts/test/cases/blocked-26.1.2/` 里有 20+ 条已知 BLOCKED 用例 —— RELEASE150-26.1.2 **不在其中**。

## 5. Jade × YSM 处置

### 5.1 事实

- `run/26.1.2/config/jade/plugins.json` **不存在**；Jade 26.1.11 已把插件配置并进 `config/jade/jade.json` 的 `plugin` 对象（该文件 3825 B）。
- 当前两侧**未关**：`run/26.1.2/config/jade/jade.json` 的 `plugin.jade_access = {npc_description:true, held_item:true, sign:true, block:true, entity:true, entity_variant:true}`、`plugin.yes_steve_model = {model_info:true}`。
- 1.21.1 的处置对照（`run/1.21.1/config/jade/plugins.json` vs `plugins.json.bak-release150`，逐键 diff **只有 3 处**）：`jade_access.entity: true→false`、`jade_access.entity_variant: true→false`、`yes_steve_model.model_info: true→false`。
- **但本次生产形态根本不加载 Jade**：`prodgame/mods` 无 Jade jar，`prodgame/config/jade/` 目录都不存在（Jade 从未在这个 gameDir 跑过）。Jade 只在整合包里（`Jade 26.1.11` + `JadeAddons`）。
- 所以 `prodgame/logs/latest.log` 只有两条无害 WARN：`@Mixin target snownee.jade.addon.vanilla.EntityHealthAndArmorProvider$Client was not found … ArmorValueProviderMixin` 与 `… DisplayHelper was not found … DisplayHelperArmorIconMixin` —— 正是 `astral_dice.jade.mixins.json` 的 `required=false` 设计（Jade 缺席只降级 WARN，不阻断启动）。

### 5.2 结论与最小改法

- **本次 RELEASE150-26.1.2 不存在 YSM×Jade 冲突**，开跑前不需要改任何 Jade 配置；用例也没有 Jade 断言。
- **最小改法（仅在「把 Jade 加进 prodgame」或「回 dev 形态」时才需要；开跑时再改 + 先备份）**：把**实际被加载的 gameDir** 下 `config/jade/jade.json` 里这三个键置 false：
  - `plugin.jade_access.entity`
  - `plugin.jade_access.entity_variant`
  - `plugin.yes_steve_model.model_info`

  改前 `Copy-Item jade.json jade.json.bak-release150`。注意：改 `run/26.1.2/config/jade/jade.json` **不等于**生效——生产形态读的是 `prodgame/config/jade/jade.json`（当前不存在）。

## 6. 产物清单 `neoforge-26.1.2/build/libs/`

14 个 jar（mtime UTC，升序）：

| 字节 | mtime (UTC) | 文件 |
|---|---|---|
| 967,109 | 2026-09-17T05:41 | `astral_dice-1.2.1-beta+neoforge_26.1.2.jar` |
| 967,218 | 2026-09-18T10:59 | `astral_dice-1.2.1-beta.2+neoforge_26.1.2.jar` |
| 1,238,398 | 2026-09-24T07:43 | `astral_dice-1.3.0-beta.1+neoforge_26.1.2.jar` |
| 1,439,335 | 2026-09-25T11:05 | `astral_dice-1.3.1-beta.1+neoforge_26.1.2.jar` |
| 1,505,092 | 2026-09-29T07:11 | `astral_dice-1.3.2-beta.1+neoforge_26.1.2.jar` |
| 1,506,256 | 2026-09-29T11:53 | `astral_dice-1.3.3-beta.1+neoforge_26.1.2.jar` |
| 1,508,301 | 2026-09-29T18:30 | `astral_dice-1.3.4-beta.1+neoforge_26.1.2.jar` |
| 1,531,207 | 2026-10-01T01:51 | `astral_dice-1.3.5-beta.1+neoforge_26.1.2.jar` |
| 1,533,289 | 2026-10-01T02:40 | `astral_dice-1.3.5-beta.2+neoforge_26.1.2.jar` |
| 1,552,315 | 2026-10-02T02:14 | `astral_dice-1.3.6-beta.1+neoforge_26.1.2.jar` |
| 1,556,596 | 2026-10-04T10:51 | `astral_dice-1.3.7-beta.1+neoforge_26.1.2.jar` |
| 1,556,673 | 2026-10-05T03:55 | `astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar` |
| 2,875,210 | 2026-11-01T15:31 | `astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar` ← 旧残留（rc） |
| 2,875,237 | 2026-11-01T18:01 | `astral_dice-1.5.0+neoforge_26.1.2.jar` **← 本次发布产物** |

**旧 jar 残留登记（只登记，未清理、未移动）**：上表前 12 个（1.2.1-beta ~ 1.3.7-beta.2）+ `1.5.0-rc.7` = 共 13 个历史产物，后续可清理；清理属独立批次，本次不动。

### 6.1 `type=jar` 读的是哪个 jar（决定断言可信度）

`scripts/test/mt_case.ps1:1639-1647`（`Get-MtCaseJarEntries`）：jarDir = `<repo>/<subproject>/build/libs`；候选 = `astral_dice-*.jar`（排除 `-(sources|dev|slim).jar`）；**取 `LastWriteTimeUtc` 最新的一条**；找不到返回 null ⇒ 断言记 BLOCKED（绝不静默通过）。

实测按该规则选中 `astral_dice-1.5.0+neoforge_26.1.2.jar`（mtime 最新）⇒ **旧 jar 不会抢走判据**。

### 6.2 计数口径与实测（`Get-MtCaseJarStats`，`scripts/test/mt_case.ps1:1661-1701`）

| 字段 | 口径 | 1.5.0 实测 | 用例阈值 |
|---|---|---|---|
| entries | 全部条目 | 2452 | `entries_min 1700` ✓ |
| models | `assets/astral_dice/models/item/**/*.json` | 158 | `models_min 120` ✓ |
| signs | `item/sign/[A-Za-z]+SignItem\.class$` | 32 | `signs_min 25` ✓ |
| recipes | `data/astral_dice/recipes/**`（**复数**） | 0 | 未断言 |
| trinkets / bountiful / embed | — | 0 / 4 / 0 | 未断言 |

- `recipes=0`、`embed=0` 是**跨版本口径差**，不是缺陷：26.1.2 用**单数**目录（`data/astral_dice/recipe/` 实测 125 条），NeoForge 的 jar-in-jar 落 `META-INF/jarjar/`；用例未断言这两项。
- rc.7 的计数与 1.5.0 完全相同（entries 2452 / models 158 / signs 32）⇒ **jar 断言区分不出 1.5.0 与 rc.7**；「跑的是不是正式版」由 `type=log` 的 jar 文件名行与 `modver=1.5.0+` 两条保证。

## 7. 开跑条件、阻塞项与开跑序

### 7.1 一句话

**具备开跑条件**：用例无缺陷、产物已定版且三处同哈希、dry-run 命令行可核对、世界现成。但生产形态与 harness 之间有一处**路径契约不对齐（B1）**，加上 **prodgame 仍是 rc.7（B2）**——这两条必须在开跑瞬间处理，否则会得到「假绿 / 大面积红」。

### 7.2 阻塞项（按严重度）

**B1｜必须处理｜日志与崩溃目录对不上（会造成大面积假结果）**

- 事实：harness 只认 `run/26.1.2/logs/latest.log`（`scripts/test/lib/Mt.Paths.psm1:237`，`latest_log = Join-Path $logsDir 'latest.log'`，无覆盖参数）与 `run/26.1.2/crash-reports/`；而生产形态 `cwd=prodgame` ⇒ 游戏写 `prodgame/logs/latest.log`。实测：`prodgame/logs/latest.log` mtime = 2026-11-01T14:40:50Z（22:39 那次生产运行），`run/26.1.2/logs` 里最新的是 **2026-09-30** 的 dev 期日志，`repo/logs` 不存在（⇒ 既不是「按 cwd 之外的仓库根」，也不是随机位置）。
- 后果：19 条 `type=log` + 1 条 `type=absent` 读陈旧日志（大概率全红或误绿）；`type=crash` 永远看不到新崩溃（**假绿，最危险**）。
- 最小改法（不改 harness、不改游戏行为、可回滚）：两个目录做 NTFS 目录联接指向 prodgame。脚本已备好：`run/26.1.2/.tmp/prep-harness-paths.ps1`（默认只打印计划，`-Apply` 才执行；现有实体目录会改名为 `logs.dev-<stamp>` / `crash-reports.dev-<stamp>` 后建联接，已是正确联接则幂等跳过）。
  ```
  pwsh -NoProfile -File run/26.1.2/.tmp/prep-harness-paths.ps1          # 看计划（本次已跑，输出见下）
  pwsh -NoProfile -File run/26.1.2/.tmp/prep-harness-paths.ps1 -Apply   # 开跑前执行这一条
  ```
  本次计划模式实测：`[logs] rename-then-junction`、`[crash-reports] rename-then-junction+mkdir-target`（prodgame 还没有 crash-reports，脚本会先建）。
- 备选（不用联接，代价更大）：把 gameDir 改成 `run/26.1.2` 本身。需给 `run/26.1.2/mods` 补 `curios-neoforge-15.0.0+26.1.2.jar`（与 prodgame 那份同哈希）与 `starengine`，并会重新引入 dev 期配置（`run/26.1.2/kubejs` 脚本、dev 口径 `jade.json`）。**不推荐**：会推翻已实证的 prodgame 现场。

**B2｜必须处理｜prodgame 加载的是 rc.7，不是 1.5.0**

- 现状：`prodgame/mods/astral_dice-1.5.0-rc.7+…jar`（2,875,013 / `6b278f07`）在跑，正式产物是 `dd22295c…`。
- 最小改法（开跑前）：① 把 rc.7 改名加 `.bak-<stamp>`（不删）；② 把 `build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar`（或整合包那份，同哈希）复制进 `prodgame/mods`；③ `…rc.7….jar.compat-on.bak` 已是 `.bak` 后缀，NeoForge 不加载，不用管。
- 校验：开跑后 `latest.log` 应出现 `astral_dice-1.5.0+neoforge_26.1.2.jar`；`modver=1.5.0+` 由用例第 16 步打出。

**B3｜口径必须纠正｜runbook §3.3 的 26.1.2 步骤不适用于本次发布验收**

- `work/release150-test-runbook.md` §3.3 写的是 `--phase env` → `--phase launch` → `--phase cases`。其中 `--phase launch` 走 **Gradle runClient（dev 形态）**，与「26.1.2 必须 production 形态」冲突（dev 下 YSM err54 并连带 Jade 崩）。
- 另外 `--phase env` 会按 harness 期望表重建 `run/26.1.2/mods` 与世界；`--phase stop --purge-saves` 会删存档 —— 两者都会破坏 prodgame 现场与现成 testworld。
- 正确序列见 §7.3。

**B4｜记录，不阻塞｜控制台代码页**：dry-run 输出里中文实例名显示乱码；`prep-harness-paths.ps1` 已内置 `[Console]::OutputEncoding = UTF8`。用例/harness 不依赖该显示。

**B5｜风险｜机器负载**：当前 7 个 `java.exe` 常驻（Gradle daemon / IDE，最大 12,131,816 K）；prodlaunch 自身 `-Xmx4G`。开跑前确认内存余量 ≥ 8 GB，避免 GLFW/驱动抖动干扰截图断言。25565 / 25585 / 25575 本次实测**均未监听**，但另一条线随时可能起——开跑前必须复查，本线需独占 25585。

### 7.3 建议开跑序（26.1.2，production 形态）

> 前置：确认另一条线客户端已收停、25565/25585/25575 空闲、内存余量足够。

```powershell
# 1) 桥接 harness 路径（B1）—— 幂等，可重复执行
pwsh -NoProfile -File F:/MCProject/astral_dice_multiloader/run/26.1.2/.tmp/prep-harness-paths.ps1 -Apply

# 2) 把 prodgame 的 astral_dice 换成 1.5.0（B2）；rc.7 改名保留，不删
$m = 'F:/MCProject/astral_dice_multiloader/run/26.1.2/prodgame/mods'
Rename-Item -LiteralPath (Join-Path $m 'astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar') -NewName ('astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar.bak-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
Copy-Item -LiteralPath 'F:/MCProject/astral_dice_multiloader/neoforge-26.1.2/build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar' -Destination $m
(Get-FileHash -LiteralPath (Join-Path $m 'astral_dice-1.5.0+neoforge_26.1.2.jar') -Algorithm SHA256).Hash
#   期望 dd22295c7c4036ae0cb95a2cc5b861de30f5cbbf4e7a8859211830874dac53a1

# 3) 启动生产形态客户端（返回即脱离）
C:/Users/xmace/.local/bin/python.exe run/26.1.2/.tmp/prodlaunch.py --quickplay testworld --log run/26.1.2/.tmp/prod-release150.log
#   等就绪：prodgame/logs/latest.log 出现 logged in with entity id + Loaded <N> advancements

# 4) 跑用例（单条，无需 --allow-auto）
pwsh -NoProfile -File scripts/test/mt_case.ps1 run --version 26.1.2 --case cases/RELEASE150-26.1.2.json
#   期望 MT_CASE_RESULT: RELEASE150-26.1.2 = PASS

# 5) 报告（需 --allow-auto）
pwsh -NoProfile -File scripts/test/mt.ps1 --version 26.1.2 --phase report --allow-auto

# 6) 收停（不要用 --purge-saves，保留 testworld）
pwsh -NoProfile -File scripts/test/mt.ps1 --version 26.1.2 --phase stop --force
```

补充：

- 第 3 步想核对命令行先跑一次 `--dry-run`；真实启动后 `run/26.1.2/.tmp/prod-run.cmdline.txt` 落盘，可逐字比对 §1.4。
- 第 4 步客户端存活判定走 `scripts/test/lib/Mt.Proc.psm1:894-963`：窗口标题含 `26.1.2`，或命令行含 `net.neoforged.fml.startup.Client` **且** 含 `run\26.1.2` 标记（我们的命令行两者都有）。
- 三张截图由 AutoJS 通道 `Screenshot.grab` **按绝对路径**写 `run/26.1.2/screenshots/<tag>.png`（`scripts/test/lib/Mt.Channel.psm1:715-821`），与 gameDir 无关，不需要额外桥接。
- **收尾必须人工判定**：两条 `type=vision` 只产出提问（`abyss_reagent_tooltip` / `chip_shop_ui`），需视觉判定「看到了 / 是商店」才算闭环；`release150_guide` 那张只留档（prodgame 无 patchouli，书不会打开，用例不据此判 PASS）。

### 7.4 未改动清单（遵守「不动」边界）

- 用例 JSON：未改（validate 已 OK，不触发 `.bak-before-release150-run`）。
- Jade 配置：未改（§5.2 只给最小改法）。
- `build/libs` 旧 jar：未删未移，只登记。
- `run/26.1.2/mods` 与 `prodgame/mods` 的 jar 集合：未改（B2 的替换留给开跑时）。
- 端口/进程：未触碰；未启动任何 Minecraft / Gradle。

## 附录 A · 本次实测常量与路径（便于复算）

| 项 | 值 |
|---|---|
| 实例 json | `D:/.minecraft/versions/26.1.2-NeoForge 模组测试/26.1.2-NeoForge 模组测试.json`（71,985 B；id `26.1.2-NeoForge 模组测试`；assets `30`；javaVersion major 25；type release；releaseTime 2026-04-09） |
| mainClass | `net.neoforged.fml.startup.Client` |
| MC 版本 / DataVersion / protocol | 26.1.2 / 4790 / 775 |
| NeoForge | `--fml.neoForgeVersion 26.1.2.109`、`--fml.neoFormVersion 1` |
| 实例 jar | `…/26.1.2-NeoForge 模组测试.jar`（38,113,927 B） |
| natives | `…/26.1.2-NeoForge 模组测试-natives`（存在） |
| gameDir | `F:/MCProject/astral_dice_multiloader/run/26.1.2/prodgame` |
| AutoJS 端点 | `http://127.0.0.1:25585/mcp`（`prodgame/config/autojs.json`：port 25585 / 双侧 enable / token `""` / evalTimeoutMs 5000 / logEval true） |
| 整合包 mods | `D:/.minecraft/versions/26.1.2-NeoForge 模组测试/mods`（95 jar） |
| 报告落点 | `scripts/test/reports/<run_id>/26.1.2/` |

## 附录 B · `run/26.1.2/.tmp` 现场（192 条目，清理留后续）

- `prodlaunch.py` 6796 B（已补丁）+ `prodlaunch.py.bak-before-release150-run` 5014 B + `prep-harness-paths.ps1`（本次新增）；`prod-run.pid` 5 B（22:39 遗留）。
- 61 个 `+~JF*.tmp`（JVM 字体临时文件，因 `TEMP` 被改到 `.tmp` 堆积，无害）。
- `prod-*.log` 十份：prod-final 82,693 / prod-full 95,785 / prod-fix 98,992 / prod-fix2 131,245 / prod-dbg 96,836 / prod-dbg2 127,307 / prod-compat2 85,944 / prod-noysm 91,223 / prod-verbose 191,243 / prod-ysm 40,289。
- 目录：agenttmp / autojsx / capstone-venv / dumps / frida-pkg / frida-tmp / hsperfdata_xmace / jdk / jna-* / lwjgl*（14 个解压目录）/ lwjgl_xmace / node-compile-cache / npm-cache / rea-tmp / rprobe / shots / tools / ysm / ysm120 / ysm120forge / ysm121 / __pycache__。
- 本次取证脚本（可随时删）：`_prep_probe_scan.ps1`、`_dv.py`、`_dv2.py`、`_logcfg.py`、`_scan2.py`、`_scan3.py`。
