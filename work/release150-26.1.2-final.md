# RELEASE150 · neoforge-26.1.2 · 1.5.0 终跑报告

- 执行时间：2026-11-02 05:06–05:27（Asia/Hong_Kong）
- 测试台：`scripts/test/mt.ps1` / `scripts/test/mt_case.ps1`（autojs 通道，端点 127.0.0.1:25585）
- 受测实例：`run/26.1.2/prodgame`（gameDir），客户端 pid 37648
- 用例：`scripts/test/cases/RELEASE150-26.1.2.json`（steps 31 + asserts 27）
- **结论：用例 27 条断言 25 PASS / 0 FAIL / 0 BLOCKED，2 条 vision 待 parent 判 ⇒ `MT_CASE_RESULT: RELEASE150-26.1.2 = PASS`；但本轮实际受测的 astral_dice jar 不是任务书指定的那一个，且 Waystone 判据 5a 未达标（详见 §2、§6）。**

---

## 1. 受测集（97 件）

| 项 | 实测 |
|---|---|
| 目录 | `run/26.1.2/prodgame/mods`（受测期间） |
| 件数 | 97 |
| 总字节 | 257,075,667 B（受测集快照时）/ 257,075,994 B（终态，含 05:15 被替换后的 astral jar） |
| 逐件清单 | `run/26.1.2/.tmp/final-mods-manifest-20261102-0508.json`（97 条 name/bytes/sha256，17,148 B） |
| 组装配方 | 玩家实例 `D:\.minecraft\versions\26.1.2-NeoForge 模组测试\mods`（95）− mru − sounds + kubejs + AutoJS + rhino + better-advanced-tooltips |
| 排除确认 | `mru`、`sounds` 缺席 = True |
| 源一致性核验 | 97 件逐件 sha256 对比参考源：0 mismatch（源文件名含中文方括号，必须 `-LiteralPath`） |

组装时注入的 4 件（sha256 实测，取自终态副本目录）：

| 文件 | 字节 | sha256 |
|---|---|---|
| kubejs-neoforge-26.1.2-8.0.6.jar | 2,214,228 | 36DF262649E53C4B8256193198E7F5D225A6FEC05F4B1E74E55C271E657EA5F0 |
| AutoJS-neoforge-26.1-1.0.0.jar | 164,801 | F867715A0928E23F2F469937A2BCAD02FFBF021D6310D4560DCDFD5D0C042329 |
| rhino-2101.2.8-build.91.jar | 980,936 | 1D53D962AA4DAC54BCBB4F998C1845980CF194318F1D6B1580EA6AC15CCE5EF3 |
| better-advanced-tooltips-2601.1.0-build.10.jar | 38,178 | 7D0EC46407C525F6136C66D44089120B5CB6A288BB50D3450D37E05F0F7732A1 |

---

## 2. astral_dice jar：**指定制品与实际受测制品不一致**（必须上报）

| 阶段 | 字节 | sha256 | 证据 |
|---|---|---|---|
| 任务书指定（05:06 交接） | 2,878,499 | 14B6A2196706ABC6887DBD08781C5C96D601AA8398D84988324B0DC16442D7B4 | 05:08 在 `neoforge-26.1.2/build/libs/` 实测 |
| 05:08:26 组装入 prodgame/mods | 2,878,499 | 14B6A219…（manifest 记录） | `final-mods-manifest-20261102-0508.json` 的 astral 条目；文件 ctime 05:08:26 |
| **实际受测（客户端 05:21:18 加载）** | **2,878,826** | **DBFD9E83725D155150EFD2C48CE2746477837103C53AC9E806A2AC5122E92653** | 文件 mtime **05:15:23**（ctime 仍 05:08:26 ⇒ 原地覆盖）；`neoforge-26.1.2/build/libs` 同名 jar mtime **05:15:16**、同 sha |

判定链（可复算）：
1. 用例 jar 断言实测 `models=158>=120 entries=2452>=1700 signs=32>=25`；`zipfile` 数 `build/libs` DBFD 制品 entries = **2452**，与断言一致。
2. 本轮 javap 取证所用的 `WaystoneWarpCompat.class`（`run/26.1.2/.tmp/wwc/WaystoneWarpCompat.class`，8,631 B，sha256 `54F2DE25C78D79B7C50AA52976B0A1C86A720BA79DAF25701BD999110B720BC0`）与 DBFD 制品内同名类逐字节相同 ⇒ 我的字节码分析属于 DBFD 制品。
3. 任务书指定的 2,878,499 B 制品**在本机已不存在**：已搜 `repo`、`neoforge-26.1.2/build`、`run/26.1.2`（含 .tmp 全递归）、`D:\.minecraft\versions\26.1.2-NeoForge 模组测试\mods`，无 2,878,499 B 的同名 jar。

覆盖的证据（并发痕迹，非我发起）：
- `run/26.1.2/.tmp/prod-run-final2.log`：**05:13:11** 起客户端，`[AutoJS/Config/]: MCP port overridden to 25586 by autojs.port`，同一 gameDir；文件 mtime 05:14:26。
- `run/26.1.2/.tmp/prod-run-final3.log`：**05:15:24** 起客户端，同样 `autojs.port`=25586、同一 gameDir；文件 mtime 05:17:30。
- `neoforge-26.1.2/build/libs` 同名 jar 于 **05:15:16** 被重建；prodgame/mods 内那一份于 **05:15:23** 被原地替换。
- 我自己的两次启动是 05:08:53（pid 22472，25585 被 fabric 占用后收停）与 **05:21:18（pid 37648，25585 绑定成功）**；本轮一切用例/截图/日志证据都出自后者。

**影响**：PASS、三张截图、guide_open、Waystone 日志行都只对 **DBFD9E83 制品**成立；「换入任务书指定 jar 再跑一遍」这个前提在本轮**没有被满足**，且指定制品已被覆盖销毁，无法回溯比对。

---

## 3. 端口门与启动

- 05:06–05:08 首查：25585 空闲；05:08:53 首次启动失败——25585 被另一 agent 的 `gradlew :fabric-1.20.1:runServer`（pid 40308/12448）占用，日志 `[Render thread/ERROR] [AutoJS/]: Could not bind the MCP endpoint on 127.0.0.1:25585`。按纪律**未 kill 他人进程**，收停自己的 22472，日志归档为 `prodgame/logs/latest.log.aborted-port25585busy-20261102-0509`。
- 05:21:13 后台轮询确认 **PORT_FREE**（fabric 自停）；05:21:18 第二次启动成功。

启动命令（`python run/26.1.2/.tmp/prodlaunch.py --quickplay testworld`；实际 java 命令行尾部，来自 `run/26.1.2/.tmp/prod-run.cmdline.txt`）：

```
-Xms1G -Xmx4G … -Djava.net.preferIPv6Addresses=system -DlibraryDirectory=D:\.minecraft\libraries
--add-opens java.base/java.lang.invoke=ALL-UNNAMED --add-exports jdk.naming.dns/com.sun.jndi.dns=java.naming
net.neoforged.fml.startup.Client --username DevTester --version 26.1.2-NeoForge
--gameDir F:\MCProject\astral_dice_multiloader\run\26.1.2\prodgame --assetsDir D:\.minecraft\assets --assetIndex 30
--uuid 00000000000000000000000000000001 --accessToken 0 --clientId 0 --xuid 0 --versionType release
--quickPlaySingleplayer=testworld --fml.neoForgeVersion 26.1.2.109 --fml.mcVersion 26.1.2 --fml.neoFormVersion 1
```

启动前轮转（stamp=20261102-0508，全部改名留档，未删除）：`prodgame/logs` 22 个文件 → `*.prev-20261102-0508`；`prodgame/crash-reports` 1 个 → 轮转；`screenshots` 三图 → `*.png.prev-20261102-0508`；`.mt_shots.json` → `.bak-before-final-20261102-0508`。

---

## 4. 就绪证据

| 证据 | 原文位置 |
|---|---|
| AutoJS 客户端侧就绪（05:21:40） | `{"endpoints":{"bindAddress":"127.0.0.1","port":25585},"sides":[{"side":"server","registered":false,"available":false},{"side":"client","registered":true,"available":true,"state":"client running, no world loaded, scripts loaded"}]}` |
| 模组加载 | latest.log **L662** `[modloading-sync-worker/INFO] [com.merlinkitsune.astral_dice.AstralDiceMod/]: Astral Dice mod loaded.` |
| 版本门 | latest.log **L712** `[astral_dice-version-gate/]: …(Version Gate):mod_version=1.5.0+neoforge_26.1.2…` |
| 集成服务器起 | latest.log **L825** `[05:21:42.320] [Server thread/INFO] [net.minecraft.client.server.IntegratedServer/]: Starting integrated minecraft server version 26.1.2` |
| 世界就绪 | latest.log **L854** `Time elapsed: 387 ms`（05:21:43.083） |
| Waystones 在位 | latest.log **L158** `Waystones 26.1.2.16 (waystones)`；L355 jar 路径；L513 `Xaero's Minimap: Waystones found!` |

---

## 5. 用例结果

用例日志：`run/26.1.2/.tmp/case-run-final-20261102-0522.log`（19,404 B / 76 行）。末尾原文：

```
MT_CASE_RESULT: RELEASE150-26.1.2 = PASS
MT_CHANNEL: delegated_count=2
MT_CASES_SUMMARY: RELEASE150-26.1.2=PASS
```

计数：`[ PASS]` 49 / `[ FAIL]` 0 / `BLOCKED` 0 / `[ →VIS]` 2（vision 两条由 parent 把关）。证据窗口：`CASE_EVIDENCE_WINDOW: RELEASE150-26.1.2 截图证据窗口起点 2026-11-02 05:22:43.560`；`MT_SNAPSHOT: OK · 26.1.2 run=20260926-230116 window=case latest=194444B debug=2374761B probe=0B anchors=3`。

### 27 条 asserts 逐条（harness 编号 n32–n58）

| # | 类型 | 判据摘要 | 结果 |
|---|---|---|---|
| n32 | log | `(Version Gate):mod_version=1.5.0+neoforge_26.1.2` | PASS（命中 1，L712） |
| n33 | log | `[REL150] ver=26.1.2 gamemode=creative` | PASS |
| n34 | log | `… pos=100,<n>,100` | PASS |
| n35 | log | `… hp=20` | PASS |
| n36 | log | `… item=astral_dice:abyss_reagent_chip` | PASS |
| n37 | log | `… xp=30` | PASS |
| n38 | log | `… slotted=[1-9][0-9]*` | PASS |
| n39 | log | `… chips=3` | PASS |
| n40 | log | `… modver=1.5.0+` | PASS |
| n41 | log | `chip id=astral_dice:abyss_reagent_chip count=3 name=(深渊试剂|Abyss Reagent)` | PASS |
| n42 | log | `tip i=0 text=.+` | PASS |
| n43 | log | `guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=.+` | PASS（命中 2 行） |
| n44 | log | `inventory open=1` | PASS |
| n45 | log | `CHIP_SHOP_OPEN player=` | PASS |
| n46 | log | `screen=\S*ChipShopScreen` | PASS |
| n47 | log | `sys id=astral_dice:dice ok=1` | PASS |
| n48 | log | `sys id=astral_dice:attack_card_medium ok=1` | PASS |
| n49 | log | `sys id=astral_dice:abyss_reagent_chip ok=1` | PASS |
| n50 | log | `sys id=astral_dice:jill_sign ok=1` | PASS |
| n51 | eval(state) | `^"[REL150] ver=\S+ gamemode=creative pos=100,-?\d+,100 hp=20 .*item=astral_dice:abyss_reagent_chip` | PASS |
| n52 | eval(chip) | `^"?(深渊试剂|Abyss Reagent)"?$` | PASS |
| n53 | eval(screen) | `ChipShopScreen"?$` | PASS |
| n54 | absent | `Unknown or incomplete command|未知或不完整的命令|No player was found|没有找到玩家|No entity was found|没有与选择器匹配的实体` | PASS（未出现） |
| n55 | jar | models≥120 / signs≥25 / entries≥1700（实测 158 / 32 / 2452） | PASS |
| n56 | crash | 无新增崩溃报告（基线 0 个） | PASS |
| n57 | vision | abyss_reagent_tooltip 是否看得到物品提示框 | **→VIS（parent 判）** |
| n58 | vision | chip_shop_ui 是否为筹码店界面 | **→VIS（parent 判）** |

### steps 内实测读数（节选，均 PASS）

- step 17（server eval）：`[REL150] ver=26.1.2 gamemode=creative pos=100,109,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+neoforge_26.1.2`
- step 19：`open=1 hover=133,172 creative=1 glfw=1`
- step 25：`com.merlinkitsune.astral_dice.screen.ChipShopScreen`
- jar_stats note：entries=2452 models=158 recipes=124 trinkets=0 bountiful=4 embed=0 signs=32

---

## 6. 本轮两个新增判据

### 6a) Waystone 传送接入 —— **未达标**

判据要求：`Failed to register Waystone teleport integration` **0 行**，且出现 INFO `Waystone teleport integration enabled for 跃迁引擎.`。

实测（`run/26.1.2/prodgame/logs/latest.log`）：
```
# Select-String -Pattern 'WaystoneWarpCompat|teleport integration|Failed to register Waystone'
# → 0 行
```
「Failed = 0 行」字面成立，但 **INFO enabled 也 0 行**。真正的现场行在 debug.log：

```
run/26.1.2/prodgame/logs/debug.log L5145:
[0211…2026 05:21:27.657] [modloading-worker-0/DEBUG] [com.merlinkitsune.astral_dice.event.WaystoneWarpCompat/]: [Astral Dice] Waystone teleport integration not registrable on this platform: java.lang.reflect.InvocationTargetException
```

即新 jar 走的是第三条分支（「平台不可注册」降级），**既不是 enabled，也不是 `not available (API absent)`**；WARN 消失只是因为被降级成 DEBUG，接入本身仍未生效。

根因（字节码 + jar 实测，非推测）：
1. `init()` 前置门 `ModList.get().isLoaded("waystones")` 通过；随后 `registerWaystoneTeleportEvent(TELEPORT_PRE_EVENT_CANDIDATES, …)`，候选 `{WaystoneTeleportEvent$Pre, $Before, $Prepare}`，本轮命中 `$Before`（26.1.2.16 的 api/event 里没有 `$Pre`）。
2. 注册走 `NeoForge.EVENT_BUS`（反射 `getField("EVENT_BUS")` → `getMethod("addListener", Class, Consumer)` → `invoke(eventClass, consumer)`）。
3. Waystones 26.1.2 的 `WaystoneTeleportEvent$Before extends WaystoneTeleportEvent`，而 `WaystoneTeleportEvent` 是 **abstract 且直接继承 Object、未 implements net.neoforged.bus.api.Event**；NeoForge bus 8.0.5（命令行 classpath 实见 `net\neoforged\bus\8.0.5\bus-8.0.5.jar`）在 `validateAbstractChain` 中要求父类也 abstract 或链顶为 `net.neoforged.bus.api.Event`，否则抛 `IllegalArgumentException`。
4. `Method.invoke` 把该 IAE 包成 `InvocationTargetException`；`isPlatformUnregistrable()` 沿 cause 链命中 IAE 且消息含 `has a non-abstract superclass` ⇒ 判 true ⇒ 打 DEBUG「not registrable on this platform」，`registered` 保持 false。
5. 结论：Waystones 26.1.2 的传送事件是 **Balm 事件**（`net.blay09.mods.balm.platform.event.BidirectionalEventMapper`，见 `mods/balm-neoforge-26.1.2-26.1.2.15.jar`），不是 NeoForge 事件总线事件；正确入口是 `WaystoneTeleportEvent$Before.EVENT` 这个 mapper，而不是 `EVENT_BUS.addListener(Class, Consumer)`。当前实现的「平台不可注册」判据恰好把真实缺陷伪装成环境不支持。

### 6b) guide_open —— **达标**

```
run/26.1.2/prodgame/logs/latest.log L2030:
[0211…2026 05:22:48.383] [Render thread/INFO] [KubeJS Client/]: autojs-eval#9: [REL150] guide_open=ok
```
（用例 step 27 = `eval_js side=client` → 输出 `ok`、`elapsedMs=1`；脚本源行在同 log L1513 `console.info('[REL150] guide_open=' + out);`；上一轮同位置是 `ERR:…ResourceLocation`，本轮已消失。）

---

## 7. 三张截图（均为本轮新产）

| 文件 | 字节 | mtime | sha256 | 通道读数 |
|---|---|---|---|---|
| `run/26.1.2/screenshots/abyss_reagent_tooltip.png` | 147,783 | 2026-11-02 05:22:46 | 1B867669DE76248F05C4AA114BB58FF8342DCCD44471CE124C08000514EA5A15 | `tier=T1 sig=grab(File,String,Target,int,Consumer) png_ok=1 rt=com.mojang.blaze3d.pipeline.MainTarget registered` |
| `run/26.1.2/screenshots/chip_shop_ui.png` | 142,679 | 2026-11-02 05:22:48 | 75DAEBC188F7898CD050D19DE4E051F08DBE06C3949C370D076D43ACA7ED6765 | 同上 |
| `run/26.1.2/screenshots/release150_guide.png` | 127,683 | 2026-11-02 05:22:49 | 87109A9D05013AFD5300C75CBA97D86894BA00C6EC1C733F3BBF8C55160BD2FD | 同上 |

- `.mt_shots.json` 本轮三条记录 ts = 1793568166.551 / 1793568168.373 / 1793568170.003（与上面 mtime 一致），同文件内更早的上一轮记录（ts≈1793567850）已作废但保留。
- 三张 bytes 均非零、mtime 落在本轮窗口（05:22:46–05:22:49，晚于 `CASE_EVIDENCE_WINDOW` 05:22:43.560）。
- `release150_guide.png` 人工确认：854×480，帕秋莉手册翻页界面——粉色书页标题「恋的规则书」，左侧正文「感谢你游玩《星之骰》…你可以在这本手册里详细了解…」，右侧「类别」图标网格（骰子/筹码/卡牌/立牌等），底部快捷栏可见筹码物品；满足「是帕秋莉手册页」。
- 上一轮同三图 sha256 已留档（`*.png.prev-20261102-0508`）：855B2EBE…/6A96D75C…/A7812AF5…，与本轮不同 ⇒ 确非旧图复用。

---

## 8. 日志中本模组 ERROR/WARN 全清单

latest.log（本轮，225,339 B）中全部 `WARN|ERROR` 行 235 行。**本模组 logger（`com.merlinkitsune.*` / `astral_dice`）0 行**（唯一一条本模组异常是 §6a 的 debug.log DEBUG 行）。

第三方 logger 分布（降序）：patchouli 84、mixin 42、LanguageProviderLoader 17、euphoria_patcher 14、ModelManager 10、ftblibrary EntityIconLoader 10、ModernFix 8、RecipeManager 6、Iris 6、KubeJS 5、VersionChecker 5、ServerLifecycleHooks 3、placebo 2、JarSelector 2、Sodium-Workarounds 2、AtlasManager 2、GlProgram 2、modloading-sync-worker 2，其余各 1。

与本模组内容相关（第三方 logger 打的，非本模组代码报错）：
- `[patchouli/]: Recipe astral_dice:<id> (of type minecraft:crafting) not found` **共 83 行，latest.log L972–L1054**（例：piercing_gun_chip、magic_quiver_chip、cutter_chip、member_recommendation_chip、star_coin_hammer_chip、purple_shooting_star_chip、cutter_blade_chip、vitamin_pill_chip、attack_card_charge、boxing_gloves_medium_chip、effect_card_orbital_strike、marker_sprayer_chip、current_core_chip、effect_card_king_power、sandwich_low_chip、scope_chip、airbag_chip、electric_sword_chip、warp_engine_chip、…）。性质：手册引用了本模组未注册为 `minecraft:crafting` 的配方页，属内容侧待办，不影响本轮断言。
- 唯一 ERROR（第三方）：latest.log **L551** `[modloading-worker-0/ERROR] [KubeJS/]: Failed to load plugin dev.latvian.mods.kubejs.integration.architectury.ArchitecturyIntegration architectury from source kubejs: java.lang.ClassNotFoundException: dev.latvian.mods.kubejs.integration.architectury.ArchitecturyIntegration`（KubeJS 自身的可选集成类缺失，与 astral_dice 无关）。

---

## 9. 收尾与复原

`pwsh -NoProfile -File scripts/test/mt.ps1 --version 26.1.2 --phase stop --force`（exit=0）原文片段：

```
===== MT_PHASE: stop =====
MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程
MT_CLEANUP_SAVES: KEPT（未指定 --purge-saves）
MT_CLEANUP: 已收停 1 个本流程进程（范围：26.1.2）
MT_CLEANUP_RESULT: OK
MT_STOP: OK (0s) · 收停完成，无本流程残留进程
```

- **25585 已释放**：`Get-NetTCPConnection -LocalPort 25585` 无连接；`http://127.0.0.1:25585/health` 连接被拒。
- **无 `run/26.1.2` 残留 java**：以 `Win32_Process` 命令行正则 `run\\+26\.1\.2` 过滤 java.exe，命中 0。其余 8 个 java 与本次无关（GradleDaemon ×2、gradle worker、`:fabric-1.20.1:runClient/runServer` 各一组、fabric loom 客户端/服务端、以及由我客户端派生的 crash_assistant pid 40256）；按纪律**未 kill 任何他人进程**。
- **prodgame/mods 已复原为受测前状态**：受测前 15 jar + 2 `.bak`（共 17 entries）；本轮 97 件整体 Move（非删除）到 `run/26.1.2/.tmp/mods-prodgame-after-final-20261102-0524`，再把 15+2 移回；复核 `prodgame/mods` = 17 entries，`astral_dice-1.5.0+neoforge_26.1.2.jar` 回到 2,875,237 B / `DD22295C7C4036AE0CB95A2CC5B861DE30F5CBBF4E7A8859211830874DAC53A1`，两个 `.bak` sha256 = `6B278F07…C49D1` / `7E2C56AE…D02AD`。
- 旧制品留档未删：`.tmp/OLD-astral_dice-1.5.0+neoforge_26.1.2.jar.prev-20261102-0508`（2,875,237 B）等，均在 `run/26.1.2/.tmp/` 下。

---

## 10. 结论与遗留

1. **用例层面**：`MT_CASE_RESULT: RELEASE150-26.1.2 = PASS`，27 条断言 25 PASS / 0 FAIL / 0 BLOCKED，2 条 vision 待判；`guide_open=ok` 新判据达标；三张截图为本轮新产且 guide 图确为帕秋莉手册页。
2. **制品层面（阻塞项）**：实际受测 jar = 2,878,826 B / `DBFD9E83…`，**不是**任务书给的 2,878,499 B / `14B6A219…`；后者在 05:15:23 被并发重建覆盖后已不存在于本机任何可搜路径。本轮 PASS 不能代替「指定制品」的验收。
3. **Waystone（未达标）**：WARN 消失是「降 DEBUG」的结果，接入仍未生效；真实缺陷是拿 NeoForge 事件总线去注册 Balm 事件（`WaystoneTeleportEvent$Before.EVENT`），应按 Balm `BidirectionalEventMapper` 改写后再验。
4. **并发风险**：`.tmp/prod-run-final2/3.log` 显示 05:13/05:15 另有 actor 用同一 gameDir 起客户端（autojs.port=25586）并重建/替换 jar。建议后续终跑约定独占：单 gameDir 单客户端、启动前冻结 build/libs 校验 sha256、纳入「起跑前/结束时 jar sha256 一致」的硬门。
5. 报告未改动任何用例断言，未 git add/commit/push/tag，未触碰 `run/1.20.1`、`run/1.21.1`、`fabric-1.20.1`。
