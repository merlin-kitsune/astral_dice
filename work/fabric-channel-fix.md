# fabric-1.20.1 AutoJS 通道：部署证据与就绪判据修复

- 时间：2026-11-02 03:20–03:40 +08:00
- 仓库：`F:\MCProject\astral_dice_multiloader`（git 只读：未 add/commit/push/tag/checkout/stash）
- 约束遵守：未启动任何 Minecraft 进程、未跑 Gradle、未占用 25565/25585/25575（实测全 free）
- 配套：开跑命令序列 `work/fabric-test-runplan.md`

## 0. 结论摘要

| 问题 | 结论 |
| --- | --- |
| 权威 jar | `C:\Users\xmace\.m2\repository\cc\sighs\autojs\AutoJS-fabric-1.20.1\1.0.0\AutoJS-fabric-1.20.1-1.0.0.jar`，164275 B，sha256 `0dc8c3a834591dc4a210d4b4c18d09a0405ff3df0752dc0e2b8bf6154f95fcde` |
| 是否按原方案复制进 mods | **没有，且不应做**：违反仓内规则 6，且该 jar 是 intermediary 命名空间，放进 named 运行期会类错位；同时会与 `-PautojsTestMod=1` 形成同 modId 双载崩服 |
| 判据是否修好 | 已修：fabric 以 `-PautojsTestMod=1` 启动时，`launch.sh` 判 `probe_enabled=1`、`ready_by` 走 25585 health+status；PowerShell 侧 `Test-MtChannelAutoJsDeployed` 经状态文件翻成 true，五个操作面全判 autojs |
| fabric 用例可判性 | 走 `mt.sh --phase cases` ⇒ 恒 BLOCKED（0/27）；走 `pwsh mt_case.ps1 run --case` ⇒ 25/27 机器可判 + 2 vision 按设计 DELEGATED |
| 开跑条件 | 具备（静态侧全绿）。唯一未实证项：fabric + `-PautojsTestMod=1` 下 25585 是否真的在服务 —— 已列为 runplan 第 6 步第一检查点 |

## 1. 任务一：jar 定位、去重、目录清单

### 1.1 三个候选路径（2026-11-02T03:29 实测）

| 路径 | 大小 | sha256 | 判定 |
| --- | --- | --- | --- |
| `C:\Users\xmace\.m2\repository\cc\sighs\autojs\AutoJS-fabric-1.20.1\1.0.0\AutoJS-fabric-1.20.1-1.0.0.jar` | 164275 B | `0dc8c3a834591dc4a210d4b4c18d09a0405ff3df0752dc0e2b8bf6154f95fcde` | **权威**：`build.gradle:199-201` 的 `modRuntimeOnly cc.sighs.autojs:AutoJS-fabric-1.20.1:1.0.0` 由 Loom 解析后就是这一份（Loom 在解析期做 intermediary→named） |
| `scripts/test/resources/mods/autojs/AutoJS-fabric-1.20.1-1.0.0.jar` | 162928 B | `66fc361f4a030bc43bf152d29cbc24ca0860bb8d86d46ee71127fc83c661cda6` | 来源副本（publish 脚本的输入），**与 mavenLocal 不是同一次构建**；不作为投放源 |
| `fabric-1.20.1/build/libs/AutoJS-fabric-1.20.1-1.0.0.jar` | — | — | 不存在（AutoJS 不是本仓产物） |

mavenLocal 该目录仅两个文件：`AutoJS-fabric-1.20.1-1.0.0.jar`（164275 B）+ `AutoJS-fabric-1.20.1-1.0.0.pom`（2192 B，`autojs.bytecode.namespace=intermediary`）。

### 1.2 为什么没有复制进 `run/client/mods`（原方案 A 被否决）

三条独立理由，任何一条都足够：

1. **仓内规则明令禁止**。`scripts/test/TESTING-RULES-OVERVIEW.md:191`（规则 6，逐字）：
   > 6. **1.20.1 / fabric-1.20.1 不得手放 jar 进 `run/*/mods`。** 那两条线的产物是 SRG / intermediary，直接投放会 `NoSuchMethodError` / 类错位；必须走 `-PautojsTestMod=1` 条件化的 `modRuntimeOnly`（`forge-1.20.1/build.gradle:382-391`、`fabric-1.20.1/build.gradle:182-190`），坐标由 `scripts/test/tools/publish_autojs_maven.ps1` 发布到 mavenLocal。
   （该行引用的 `fabric-1.20.1/build.gradle:182-190` 已漂移，实际条件块在 `:199-201`。）
2. **映射命名空间对不上**。jar 字节码实测：`net/minecraft/class_<n>` 形式出现 725 次（intermediary），named 路径引用 31 次；manifest 亦为 `Fabric-Mapping-Namespace: intermediary`。而 dev run 是 `fabric-1.20.1/build.gradle:67 mappings loom.officialMojangMappings()`（named）。Loom 只在**解析依赖时**从 maven 重映射 modRuntimeOnly 制品，手工投放绕过了这条路径。
3. **双载崩服**。`scripts/test/win/lib/fabric.sh:127-144` 的 `mt_fab_autojs_enabled` 在 `<side>/mods` 里看到任何 `^autojs` 文件名即返回真 ⇒ `mt_fab_gradle_autojs_arg` 会补 `-PautojsTestMod=1` ⇒ mods 里一份 + modRuntimeOnly 一份同 modId，Fabric 启动期直接拒绝。原方案写的“切勿同时使用”，在 fabric 侧其实是**不可能不**同时，除非改判据。

### 1.3 目标目录最终清单（`fabric-1.20.1/run/client/mods`，8 个文件，全为仓库内嵌库）

```
.astral_embedded_jars.json                           628 B
astral_embedded-cloth-basic-math.jar              10678 B
astral_embedded-com_github_llamalad7_mixinextras.jar  145069 B
astral_embedded-fiber.jar                        231646 B
astral_embedded-io_wispforest_endec_gson.jar      20694 B
astral_embedded-io_wispforest_endec_netty.jar     10164 B
astral_embedded-io_wispforest_endec.jar          146742 B
astral_embedded-puzzlesaccessapi.jar              61896 B
```

去重核对：
- `autojs` 命中数 **0**（大小写不敏感全名匹配）；`.bak` / `.old` / `.disabled` / `~$` 命中数 **0**。
- `run/server/mods`、`run/datagen/mods` 同样 autojs 命中 0（各 8 个文件），不存在跨 side 残留。
- `run/<side>/.mt_autojs_injected` 在 idle 状态**不存在**（client / server 均为 False）。
- `run/client/.fabric/processedMods` 6 个文件、可写；`run/client/saves/testworld` 存在（跑完手工删，见 runplan 第 9 步）。

⇒ 该目录“干净到可以直接用 `-PautojsTestMod=1`”：没有第二个同 id jar，也没有 AutoJS 的任何旧副本。

## 2. 任务二：就绪判据（改前 / 改后 + 行号 + 读什么路径）

### 2.1 改前：两条判据都只认 mods 目录

| 判据 | 文件:行 | 读什么 | fabric 下的结果 |
| --- | --- | --- | --- |
| bash 侧 | `scripts/test/win/launch.sh:191-197`（改前） `aj_n=$(mt_autojs_jar_count "$RUN_DIR")` | `run/<side>/mods` 里 `*autojs*.jar` 的**个数**（`lib/common.sh:588-596`） | 恒 0 ⇒ `autojs_deployed=0` ⇒ `probe_enabled=0`，`ready_by` 退成纯 marker，**25585 一次都不探** |
| PowerShell 侧 | `scripts/test/lib/Mt.Channel.psm1:225-242`（改前）返回 `@(Get-ChildItem -LiteralPath $modsDir -Filter AutoJS-*.jar -File).Count -gt 0` | `mods_dir`（由 `Get-MtPaths -Version fabric-1.20.1` 解析成 `fabric-1.20.1/run/client/mods`） | 恒 false ⇒ `Send-MtChannelCommand` / `Key` / `Mouse` / `Invoke-MtChannelShot` 全部走 `New-MtChannelDelegatedResult` ⇒ inject/screenshot/ reload 全部 DELEGATED |

两个后果叠加：
- 客户端其实起来了（`run/client/logs/latest.log` 2026-11-02 02:28 那次 `Sound engine started` L265、`Total time to load game and open world was 16.16 s` L365），但脚本认为“AutoJS 不可用”，于是**不探端点**、把所有注入判给 DELEGATED；
- DELEGATED 在 `mt_case.ps1:2899-2900` 只累加 `delegatedSteps`，不升级 `$worst`（`:2926-2932` 只有 ERROR/TIMEOUT/BLOCKED/FAIL 会改判）⇒ 一轮“全 DELEGATED”在汇总上仍然不是 FAIL，极易被读成通过。

### 2.2 改后：fabric 走“注入即部署”判据

**bash 侧** `scripts/test/win/launch.sh:204-229`（新增，判据行如下）：

```bash
204: fab_aj_injected=0
205: if mt_is_fabric "$VERSION" && [ "${FAB_SIDE:-}" = "client" ] && [ -n "$AJ_ARG" ] && [ "$allow_no_autojs" != "1" ]; then
206:     fab_aj_injected=1
207: fi
208: # 双保险（fail-closed）：mods 里真出现 AutoJS jar 时，-PautojsTestMod=1 会再注入同 id 一份
210: if mt_is_fabric "$VERSION" && [ "${aj_n:-0}" -gt 0 ] && [ -n "$AJ_ARG" ]; then
211:     BLOCKERS="$BLOCKERS fabric_autojs_duplicate"
212: fi
213: if [ "${aj_n:-0}" -gt 0 ] || [ "$fab_aj_injected" = 1 ]; then autojs_deployed=1; else autojs_deployed=0; fi
219: if [ "$autojs_deployed" = 1 ] && [ "$allow_no_autojs" != "1" ]; then
220:     probe_enabled=1
222:     ready_by="AutoJS GET $BASE_URL/health==ok + tools/call status(client available=true)（fabric 通道由 $AJ_ARG 注入，mods/ 无 jar 属正常；marker 仅兜底）"
```

- `AJ_ARG` 来自 `lib/fabric.sh:146-153 mt_fab_gradle_autojs_arg`（enabled 时打印 `-PautojsTestMod=1`），`mt_fab_autojs_enabled` 在 `:127-144`（本会话补了 falsy 分支，`--disable-autojs` 现在真的生效）。
- `BASE_URL` = `mt_aj_endpoint`（`lib/common.sh:426-428`）⇒ 默认 `http://127.0.0.1:25585/mcp`。
- 真启动前还会写状态文件（`launch.sh` `starting …` banner 之前）：`fabric-1.20.1/run/client/.mt_autojs_injected`，内容含 `autojs=1` / `gradle_arg=…` / `artifact=AutoJS-fabric-1.20.1-1.0.0.jar` / `source=mavenLocal:…`；`stop.sh` 收停时删除。

**PowerShell 侧** `scripts/test/lib/Mt.Channel.psm1:260-…` `Test-MtChannelAutoJsDeployed` 改成两级：先按 `mods_dir` 找 `AutoJS-*.jar`（非 fabric 线行为完全不变），miss 则调 `Test-MtChannelFabricAutoJsInjected -Version -Side`（`:225-258`），读 `<run_dir>/.mt_autojs_injected` 并匹配 `(?m)^autojs\s*=\s*1\s*$`。fail-closed：文件缺失 / 非 fabric / `autojs=0` ⇒ false。

### 2.3 实测证据（无游戏、无 gradle）

**(a) `launch.sh --dry-run`（真启动前会打印的就是这些判据值）**

```
bash scripts/test/win/launch.sh --version fabric-1.20.1 --side client --enable-autojs --dry-run
  cmd         = gradlew.bat :fabric-1.20.1:runClient --console=plain -Pquickplay=testworld -PautojsTestMod=1
  autojs_jar  = 0 个（fabric：-PautojsTestMod=1 → AutoJS-fabric-1.20.1-1.0.0.jar 走 mavenLocal 运行期 classpath，不在 mods/）
  autojs_line = autojs_deployed=1 / probe_enabled=1 / marker_fallback=1 / fabric_injected=1
  ready_by    = AutoJS GET http://127.0.0.1:25585/health==ok + tools/call status(client available=true)（fabric 通道由 -PautojsTestMod=1 注入，mods/ 无 jar 属正常；marker 仅兜底）
  world_marker= Sound engine started|finished
  blockers    = （空）

bash scripts/test/win/launch.sh --version fabric-1.20.1 --side client --disable-autojs --dry-run
  cmd         = …（无 -PautojsTestMod=1）
  autojs_line = autojs_deployed=0 / probe_enabled=0 / fabric_injected=0
  ready_by    = 纯 marker（… 无 AutoJS jar，或 MT_ALLOW_NO_AUTOJS=1）
```

两次 dry-run 均 exit 0，未启动任何进程。

**(b) PowerShell 通道面（`work/_probe/verify-channel.ps1`，翻转状态文件五态）**

| 态 | 状态文件 | `Get-MtChannelPlan` 输出 |
| --- | --- | --- |
| A | 无 | `mode=autojs deployed=False command=delegated key=delegated mouse=delegated shot=delegated reload=delegated`（＝今天的现状） |
| B | `autojs=1` | `deployed=True`，五个操作面**全 autojs** |
| C | `autojs=0` | deployed=False，全 delegated |
| D | 删除 | deployed=False，全 delegated |
| E | 对照 1.21.1 | deployed=True（`run/1.21.1/mods` 里有 AutoJS jar）⇒ 另三线行为未受影响 |

路径确认：`mods_dir=F:\MCProject\astral_dice_multiloader\fabric-1.20.1\run\client\mods`、`run_dir=…\run\client`、`latest_log=…\run\client\logs\latest.log`（即 client 用例读的就是 client 的日志，不会串到 server）。

### 2.4 判据结论（回答问题：“放 jar 后能不能探到 25585”）

**能探，但不是靠“放 jar”——靠 `-PautojsTestMod=1` + 本会话改的两处判据。** 三件事同时成立才会真的打 `GET http://127.0.0.1:25585/health`：

1. `mt_is_fabric fabric-1.20.1` 且 `FAB_SIDE=client` 且 `AJ_ARG` 非空（即脚本自己去拉 `-PautojsTestMod=1`）⇒ `fab_aj_injected=1`；
2. `autojs_deployed=1` ⇒ `probe_enabled=1`（否则 marker 单一判据）；
3. 游戏内 AutoJS 真的在 25585 监听（**静态无法证明**，见 §5）。

### 2.5 仍然存在的“静默降级”缺口（未改，仅记录）

`launch.sh:416-419`：若端点**从未应答**，marker 命中即 `READY` 且 **exit 0**（detail 标 `autojs = 未响应（marker_fallback=1…）`）。也就是说“通道没起来”不会被判失败。可选加固（本次未做，需用户裁决）：加 `MT_REQUIRE_AUTOJS=1`，把这种 READY 改成 exit 2。

## 3. 任务三：开跑命令序列

见 `work/fabric-test-runplan.md`（10 步，每条命令带用途与风险）。索引：

| 步 | 命令要点 | 风险点 |
| --- | --- | --- |
| 1 | `work/_probe/ports.ps1` 查 25565/25585/25575 | 25585 被占＝上次进程没清 |
| 2 | 轮转 `run/client/logs/latest.log` + `run/server/logs/latest.log` | 不轮转 ⇒ log 断言（尤其 absent）假绿 |
| 3 | `mt.sh --phase env` | fabric 打印 autojs=0 属正常 |
| 4 | `mt.sh --phase build` + `:fabric-1.20.1:jar` | 起 gradle daemon；只到 classes 不产 jar |
| 5 | `launch.sh --side client --enable-autojs --wait-world 180 --timeout 600` | exit 0 可能是 marker 兜底；11=duplicate；12=TIMEOUT |
| 6 | `/health` + `verify-channel.ps1` | **第一检查点**，唯一未实证项 |
| 7 | `pwsh mt_case.ps1 run --case RELEASE150-fabric-1.20.1.json` | 不要走 `mt.sh --phase cases` |
| 8 | `stop.sh --version fabric-1.20.1` | **禁用** `--version 1.21.1`（matcher bug）；不加 `--purge-saves` |
| 9 | `rm -rf fabric-1.20.1/run/client/saves/testworld` | 路径必须精确 |
| 10 | 端口回 free + `stop.sh --dry-run` 复核 | — |

## 4. 任务四：`RELEASE150-fabric-1.20.1.json` 的 27 项断言审计

### 4.1 先纠一个路由事实（决定性）

`scripts/test/win/lib/phases.sh:355-360`：fabric 的 `--phase cases` **不走** `mt_case.ps1`，而是走 bash 原生跑器 `mt_fab_cases_run`（`lib/fabric.sh:447-802`，内嵌 python）。该跑器（`fabric.sh:711-757`）：
- `eval_js` / `await_state` / `mcp_status` ⇒ **恒** `BLOCKED`（“需要 AutoJS（当前 AutoJS=%s；fabric 的 AutoJS 是 -PautojsTestMod=1 条件依赖）”），**即使 AutoJS 已注入**；
- `screenshot` / `kubejs_reload` ⇒ **恒** `BLOCKED`（“在 bash 侧未实现”）；
- `inject_command` ⇒ 只有 `side=server` 才可用（走 RCON），而该用例 `side=client`；
- 断言侧：`type=eval` ⇒ None（BLOCKED）、`type=vision` ⇒ None（“vision 需要截图通道（bash 侧未实现）”）；bash 侧**没有 DELEGATED 概念**，判不了就整条用例 BLOCKED（`:749-751`、`:762-764`）。

⇒ 走 `mt.sh --phase cases`，该用例**在第一条 `eval_js` 就 BLOCKED，0/27 可达**。要判它必须直接调 `pwsh scripts/test/mt_case.ps1 run --case …`（`run --case` 不受 `--allow-auto` 闸门限制，闸门只挡 `run-dir`，见 `mt_case.ps1:3167-3169`、`:3217`）。

### 4.2 断言逐条（pwsh 通道下的可判性）

| # | 行 | 类型 | 判据 | pwsh+AutoJS 下 |
| --- | --- | --- | --- | --- |
| a1 | :177 | log(whole) | `- astral_dice 1\.5\.0\+fabric_1\.20\.1` | 可判（2026-11-02 02:28 客户端日志 L8 实测就是这串） |
| a2 | :182 | log | `gamemode=creative` | 可判（来自 server 侧 eval 的 console.info） |
| a3 | :186 | log | `pos=100,-?[0-9]+,100` | 可判 |
| a4 | :190 | log | `hp=20` | 可判 |
| a5 | :194 | log | `item=astral_dice:abyss_reagent_chip` | 可判 |
| a6 | :198 | log | `xp=30` | 可判 |
| a7 | :202 | log | `slotted=[1-9][0-9]*` | 可判 |
| a8 | :206 | log | `chips=3` | 可判 |
| a9 | :210 | log | `modver=1\.5\.0\+` | 可判（版本钉死，同 a1） |
| a10 | :214 | log | `chip … count=3 name=(深渊试剂\|Abyss Reagent)` | 可判（client eval 生成） |
| a11 | :218 | log | `tip i=0 text=.+` | 可判（tooltip 文本由 `getTooltipFromItem` **直接取**，不依赖真实 hover） |
| a12 | :222 | log | `guide key=… text=.+` | 可判（读 lang，与 Patchouli 首开无关） |
| a13 | :226 | log | `inventory open=1` | 可判（依赖反射设 `leftPos/topPos` + `mouseHandler` 成功） |
| a14 | :230 | log | `CHIP_SHOP_OPEN player=` | 可判（server eval 调 opener 时的日志） |
| a15 | :234 | log | `screen=\S*ChipShopScreen` | 可判 |
| a16–a19 | :238-252 | log ×4 | `sys id=… ok=1` | 可判 |
| a20 | :255 | eval(from=state) | 状态快照正则 | 可判 |
| a21 | :261 | eval(from=chip) | `^(深渊试剂\|Abyss Reagent)` | 可判 |
| a22 | :268 | eval(from=screen) | `ChipShopScreen$` | 可判 |
| a23 | :275 | absent(whole) | `Unknown or incomplete command\|未知或不完整的命令\|No player was found\|没有找到玩家\|No entity was found\|没有与选择器匹配的实体` | 可判 |
| a24 | :279 | jar | models≥120 recipes≥100 signs≥25 trinkets≥6 embed_lib=true entries≥1800 | 可判，但**需要 `build/libs/astral_dice-*.jar`**（runplan 第 4 步），否则 BLOCKED |
| a25 | :289 | crash | 无新崩溃报告 | 可判 |
| **a26** | :292 | **vision** | image=`abyss_reagent_tooltip` | **DELEGATED（设计如此）**：截图能拍（`shot=autojs` → `net.minecraft.client.Screenshot.grab` 三档签名），但 `mt_case.ps1:1898-1935` 只输出 `VISION_PATH=<abs> | <question>`，由外部视觉模型判 |
| **a27** | :297 | **vision** | image=`chip_shop_ui` | 同上 DELEGATED |

**合计：25 条机器可判 + 2 条 vision 按设计外判。**“27/27 自动通过”在本仓设计上不存在——a26/a27 过去被拦是因为**通道没起、截图拿不到**（更早还会因“截图不属于当前世代”被 BLOCKED）；通道起来后它们从 BLOCKED 变成 DELEGATED，但仍不会自动 PASS。

### 4.3 时序风险与缓解

| 风险 | 说明 | 缓解 |
| --- | --- | --- |
| R1 冷启动时间 | 现基线“open world 16.16 s”（2026-11-02 02:28 日志 L365）；用例内固定 `wait 800/900/1400/400` 是按热态写的 | 第一次跑前先热身：手动进一次世界再退出，或接受第一轮更可能出现的超时/空图，用第二轮判定 |
| R2 a11 相关（视觉面） | a11 本身**不需要**真 hover（tooltip 文本由 eval 直接算），但 a26 的截图需要 tooltip 真渲染出来——即 `hover` 步（反射设 `mouseHandler.xpos/ypos` + `InventoryScreen`）必须成功，且渲染要在 900 ms 内到位 | 把 hover 后的 wait 提到 1500–2000 ms；先看 a13（`inventory open=1`）是否过——不过说明反射设位失败 |
| R3 a12 相关（Patchouli 首开） | a12 读 lang 不受影响；但 `release150_guide` 截图（无断言）是 Patchouli **首开**：要解析 book JSON + 纹理，1400 ms 可能不够 | 若要这张图当证据，把 guide 后 wait 提到 2500–3000 ms；首开慢也只会影响这张“证据图”，不影响 27 条断言 |
| R4 a27 的屏幕 | `ChipShopScreen` 由 server eval 触发 opener ⇒ 需一个 C→S→C 往返 + 首次类加载；1400 ms 边界 | wait 提到 2000 ms；先看 a15（`screen=…ChipShopScreen`）是否过，过不了就别急着判 a27 |
| R5 a24 的 jar | 需要 `build/libs` 产物；只跑 `--phase build`（classes）不产 jar | 补 `:fabric-1.20.1:jar`（runplan 第 4 步） |
| R6 log 世代 | 断言 `scope=whole` 读 `latest.log`；不轮转会把上一轮的行算进来（尤其 absent 与 a1） | runplan 第 2 步轮转 |
| R7 版本钉死 | a1/a9 要求 `1.5.0+`；`gradle.properties:29` 现为 `1.5.0+fabric_1.20.1`（匹配），但 22:55 那次是 `1.5.0-rc.7+…`（不匹配） | 开跑前确认 `mod_version`；如需 rc 标记，须同步改用例 |

## 5. 开跑条件判定

**具备开跑条件（静态侧全绿）**，但有一项必须由实机确认：

- 已证：客户端能起（02:28 基线）、mods 目录干净、判据在 dry-run 下给出 `probe_enabled=1` + 25585 ready_by、PowerShell 五个操作面在状态文件存在时全 autojs、端口全 free、`bash -n` 三脚本语法通过。
- **未证（唯一）**：fabric + `-PautojsTestMod=1` 时 25585 是否真在监听。静态可给的支持证据：jar 内 `AutoJSRuntime` / `mcp/McpServer` / `mcp/tools/StatusTool` 命中 `endpoint` 字符串（端口非字面常量，来自运行时配置）；`scripts/test/cases/.mt_autojs.json` 里 25585 的实测记录属于 **1.21.1 线**，不能当 fabric 证据。
- 因此 runplan 把“第 6 步 `/health` + `verify-channel.ps1`”设为第一检查点：不通过就停，别往下跑用例（否则 25 条会大面积 BLOCKED/DELEGATED，仍可能被读成“跑过了”）。

## 6. 本会话改动的文件（4 个，均只动测试台，未碰模组源码/资源）

| 文件 | 改动 |
| --- | --- |
| `scripts/test/win/lib/fabric.sh` | `mt_fab_autojs_enabled` 补 falsy 分支；新增 `mt_fab_autojs_state_path/_write/_clear/_read`（`.mt_autojs_injected`） |
| `scripts/test/win/launch.sh` | `:191-231` 判据加 fabric 注入分支 + `fabric_autojs_duplicate` fail-closed 守卫；banner 前写状态文件 |
| `scripts/test/lib/Mt.Channel.psm1` | 新增 `Test-MtChannelFabricAutoJsInjected`（`:225-258`）；`Test-MtChannelAutoJsDeployed` 改为 jar∨注入状态，两个函数都加 `-Side` |
| `scripts/test/win/stop.sh` | 收停时删两侧 `.mt_autojs_injected`（含 dry-run 分支） |

行尾：三个 `.sh` 全 LF（launch.sh 0 CR/454 LF、fabric.sh 0/902、stop.sh 0/381），`Mt.Channel.psm1` 全 CRLF（864/864），与各自原样式一致；`bash -n` 三脚本通过。

## 附录 A：探针脚本（可复跑）

| 脚本 | 作用 |
| --- | --- |
| `work/_probe/deploy-evidence.ps1` | 三候选 jar 的 size+sha256、mods 目录全清单、去重扫描、状态文件存在性、saves 清单 |
| `work/_probe/ports.ps1` | 25565/25585/25575 是否 LISTEN |
| `work/_probe/verify-channel.ps1` | 翻转 `.mt_autojs_injected` 五态，打印 `Get-MtChannelPlan`（五个操作面） |
| `work/_probe/clientlog.ps1` | 从 `run/client/logs/latest.log` 摘 modlist / 世界就绪 / 结尾 |
| `work/_probe/jarprobe.ps1` | jar 清单、`fabric.mod.json`、mixin 配置、intermediary vs named 引用计数 |
| `work/_probe/jarport.ps1` | jar 内 `25585` / `endpoint` 字符串命中 |

## 附录 B：开跑命令序列

`work/fabric-test-runplan.md`（10 步，含用途/风险/PORT 前置检查/停服禁用项）。
