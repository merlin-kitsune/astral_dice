# fabric-1.20.1 · 1.5.0 发布验收 · 静态准备报告

- 仓库：`D:/MCProject/astral_dice_multiloader`（git 只读：全程未 add/commit/tag/checkout/stash）
- 时间：2026-11-02（静态作业，未启动游戏、未跑 Gradle、未占用 25565/25575/25585）
- 配套文档：`work/fabric-bash-harness-usage.md`（命令序列 / 参数表 / 已知坑全文）
- 证据脚本：`work/_rcon_probe.py`、`work/_probe_cl.ps1`、`work/_probe_level.py`、`work/_probe_case.py`、`work/_probe_jar2.py`、`work/_probe_jar3.py`、`work/_probe_risk.py`

---

## 0. 结论（先看这一段）

| 事项 | 状态 |
|---|---|
| 测试世界就位（客户端） | ✅ 已复制，51 文件 / 25 目录 / 20,595,982 B，前后一致 |
| 用例 schema 校验 | ✅ `MT_VALIDATE: OK`，exit 0（用例零缺陷，未改一字） |
| 产物 1.5.0 正式版 | ✅ `astral_dice-1.5.0+fabric_1.20.1.jar` 在位，a24 阈值全部有余量 |
| RCON 通道 | ✅ 可用（25575 / astralft，实测 AUTH 通过 + `list` 往返） |
| **AutoJS 通道** | ❌ **硬阻塞**：harness 两处 AutoJS 判定只看 `run/<side>/mods` 里有没有 `AutoJS-*.jar`，而 AutoJS 在本线是 `modRuntimeOnly`（classpath）⇒ 判定恒假 |
| 27 条断言可达性 | 纯 RCON 只覆盖 **4/27**；即使开 `-PautojsTestMod=1`，因上述判定仍有 **20 条取不到证** |
| 是否具备开跑条件 | **流程/环境具备，用例执行不具备**（需先解决 §6 的 B1，可选 A/B 两方案） |

一句话：**除了 AutoJS 部署判定这一处，全部准备好了。** 不解决它，27 条里能判的只有 4 条。

---

## 1. 任务书既有前提的三处偏差（已实测，非推测）

| 前提（任务书） | 实测 |
|---|---|
| `run/fabric-1.20.1/mods` 只有 AutoJS（无 YSM） | **该路径不存在**。fabric 是双 run 目录：`fabric-1.20.1/run/client/mods` 与 `fabric-1.20.1/run/server/mods`，两边**只有** `astral_embedded-*.jar`（7 个内嵌库）+ `.astral_embedded_jars.json`；**没有 AutoJS，也没有 YSM** |
| `run/fabric-1.20.1/server/saves` 为空，需复制世界过去 | 服务端**根本没有 `saves/` 目录**：它按 `server.properties` 的 `level-name=world` 用 `fabric-1.20.1/run/server/world`（已存在，且当时正被 PID 37252 独占） |
| 复制 `run/1.20.1/saves/testworld` | 1.20.1 线上**没有 testworld**：`run/1.20.1/saves/` 下唯一世界是 **`verify`**（也没有 `run/1.20.1/client/saves`）。本次按 `verify` 复制 |

> `-Pquickplay=testworld` 要的是**目录名 testworld**，与源世界目录名无关；把 `verify` 落成 `saves/testworld` 是正确做法。

---

## 2. 一次完整 fabric 发布验收的命令序列（摘要）

```bash
cd /d/MCProject/astral_dice_multiloader

# 0 环境自检（只读）
bash scripts/test/win/env.sh --version fabric-1.20.1

# 1 收停同线残留（先 dry-run 看会杀谁）
bash scripts/test/win/stop.sh --version fabric-1.20.1 --dry-run
bash scripts/test/win/stop.sh --version fabric-1.20.1

# 2 预检
bash scripts/test/win/mt.sh --version fabric-1.20.1 --phase preflight

# 3 启动客户端（用例声明 side=client；AutoJS 打开）
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side client --enable-autojs --phase launch

# 4 跑 27 条用例（channel=autojs ⇒ pwsh 执行器）
"C:/Program Files/PowerShell/7/pwsh" -NoProfile -File scripts/test/mt_case.ps1 run \
  --version fabric-1.20.1 --case scripts/test/cases/RELEASE150-fabric-1.20.1.json

# 5 停机
bash scripts/test/win/stop.sh --version fabric-1.20.1
```

参数含义、退出码（0/1/2/11/12/130）、机器行（`ENV_RESULT` / `LAUNCH:` / `WIN_STOP:` / `MT_VALIDATE:`）与 12 条已知坑见 `work/fabric-bash-harness-usage.md`。

实测要点（来自 dry-run 与脚本原文，不是文档抄写）：
- `launch.sh --version fabric-1.20.1 --side client --dry-run` 下发 `cmd //c "gradlew.bat :fabric-1.20.1:runClient --console=plain -Pquickplay=testworld"`；加 `--enable-autojs` 才追加 `-PautojsTestMod=1`。
- **就绪 marker 读的是 gradle 日志**（`scripts/test/win/logs/fabric-1.20.1-<ts>.log`），不是 `run/client/logs/latest.log`；client 判据 `Sound engine started|finished`，server 判据 `Done ([0-9.]+s)! For help`。
- **TIMEOUT 默认不杀进程**（需 `--kill-on-timeout`）。
- `same_line_running` 当前会拦：实测 `launch.sh --side server --dry-run` → `blockers = same_line_running（真启动会 exit 11 BLOCKED）`。

---

## 3. 世界复制证据（任务 2）

**源**：`run/1.20.1/saves/verify` → **目标**：`fabric-1.20.1/run/client/saves/testworld`（此前目录不存在，`client/saves` 为空）

| 指标 | 复制前（源） | 复制后（目标） | 复制后（源，复核未被改动） |
|---|---|---|---|
| 文件数 | 51 | 51 | 51 |
| 目录数 | 25 | 25 | 25 |
| 总字节 | 20,595,982 | 20,595,982 | 20,595,982 |
| `level.dat` | 45,407 | 45,407 | 45,407 |
| `DataVersion` | 3465（=1.20.1） | 3465 | 3465 |

分项（源）：region 19,099,648 / entities 966,656 / serverconfig 256,256 / poi 151,552 / level.dat 45,407 / icon.png 10,505 / playerdata 7,996 / data 5,137 / advancements 5,098。
方式：python `shutil.copytree`，耗时 0.05 s。**`run/1.20.1` 原世界零改动**（复制后重新统计，文件数/目录数/字节数完全一致）。

服务端：**未复制、也不需要**——`runServer` 完全忽略 `-Pquickplay`，按 `level-name=world` 使用 `fabric-1.20.1/run/server/world`（该世界当时被 PID 37252 占用，不可动）。

附带说明：
- `session.lock` 随目录一起被复制。它是 OS 级锁文件、无内容，MC 打开世界时会自行重写，**未删除**（避免改成"复制不完整"）。
- 世界来自 **Forge 1.20.1** 线，带 `serverconfig/`、`ftbteams/`、`enigmatic_transience.json`、`kubejs_persistent_data.nbt`。Fabric 不消费这些数据；风险是**日志噪声**，可能干扰 a23（absent）与 a25（crash）——见 §6 B6。

---

## 4. validate 结果（任务 3）

```powershell
pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case scripts/test/cases/RELEASE150-fabric-1.20.1.json
```

实测输出（两次运行完全一致）：

```text
MT_VALIDATE: OK — RELEASE150-fabric-1.20.1
```
exit code 0。

判定：**用例无自身缺陷 ⇒ 未做任何修改，也未生成 `.bak-before-release150-run` 备份**（没有需要备份的改动；每条断言依赖的资源我都做了源码级核对，见 §5.3 表格）。

必须说明 validate 的**覆盖边界**：它只校验 schema（字段 / step op 白名单 / assert 类型 / 必填项 / no_esc 约束 / 序号），**不校验通道可行性**——一个只声明 `channel=autojs` 的用例，在机器一根 AutoJS 都没有时照样 `MT_VALIDATE: OK`。所以 §5.3 的逐条风险分析不能省。

---

## 5. 注入通道结论（任务 4）

### 5.1 RCON（可用的那条）

| 项 | 值 | 证据 |
|---|---|---|
| 配置文件 | `fabric-1.20.1/run/server/server.properties` | 实测读取 |
| `enable-rcon` | `true` | 同上 |
| `rcon.port` | `25575` | 同上 |
| `rcon.password` | `astralft` | 同上 |
| `server-port` / `level-name` | `25565` / `world` | 同上 |
| harness 兜底常量 | `MT_FAB_RCON_PORT=25575`、`MT_FAB_RCON_PASSWORD=astralft` | `scripts/test/win/lib/fabric.sh` |
| 实测连通 | AUTH rid=1 通过；`list` → `There are 0 of a max of 10 players online:` | `python work/_rcon_probe.py 25575 astralft list` |
| 依赖 | bash 层 RCON 走**纯 python socket**，缺 `python3` ⇒ `MT_ERROR` / rc=2 | `lib/fabric.sh` 的 `mt_rcon_exec` |

端口现状（静态观测）：`25565` 与 `25575` **均 LISTENING**，属当时运行的 fabric dev server（PID 29924 / 37252）；`25585` 关闭。

### 5.2 AutoJS（不可用的那条）

- **`run/<side>/mods` 里没有任何 `AutoJS-*.jar`**（任务书假设的 `run/fabric-1.20.1/mods/AutoJS-*.jar` 路径本身不存在）。
- 制品其实在 mavenLocal：`C:/Users/xmace/.m2/repository/cc/sighs/autojs/AutoJS-fabric-1.20.1/1.0.0/AutoJS-fabric-1.20.1-1.0.0.jar`（164,275 B，2026-10-18，66 个 class）。
- 部署方式：`fabric-1.20.1/build.gradle:198-201`
  `if (providers.gradleProperty('autojsTestMod').orNull == '1') { modRuntimeOnly 'cc.sighs.autojs:AutoJS-fabric-1.20.1:1.0.0' }`
  ⇒ **只在 `-PautojsTestMod=1` 时以 modRuntimeOnly 进 classpath，永不进 `mods/`**。其运行时依赖已常年就位：`fabric-api`（`build.gradle:70` modImplementation）、`rhino`（由 kubejs modRuntimeOnly 提供）。
- 运行期默认值（`javap -c cc/sighs/autojs/config/AutoJSConfig`）：`enabled=true`、`bindAddress=127.0.0.1`、`port=25585`、`enableServerSide=true`、`enableClientSide=true`、`token=""`、`evalTimeoutMs=5000`、`maxResultChars=100000`；端点 `/mcp` 与 `/health`；配置文件名 `autojs.json`；端口可由 `-Dautojs.port` / `AUTOJS_PORT` 覆盖。
  ⇒ **客户端侧求值默认开启**；`evalTimeoutMs=5000` 对 s22 / s29 这类重活偏紧（harness 默认给 15000）。
- 另需注意：那次 02:28 的客户端启动（"Loading 85 mods"）**没装 AutoJS**——`run/client/logs/latest.log` 里 `autojs` 命中 **0** 行。

**两处判定错位（本节核心结论）**

1. `scripts/test/win/launch.sh:191-210` —— 是否去探 25585，只看 `run/<side>/mods` 有没有 `autojs*.jar`。
   实测 `--enable-autojs --dry-run`：`autojs_jar = 0 个`、`autojs_deployed=0 / probe_enabled=0`、`ready_by = 纯 marker`。
   ⇒ **加了 `--enable-autojs` 也不会探 25585**，`LAUNCH: READY-by-marker` 完全不代表 AutoJS 可用。
2. `scripts/test/lib/Mt.Channel.psm1:225-242` 的 `Test-MtChannelAutoJsDeployed` —— 同样只按 `mods` 目录里的 `AutoJS-*.jar` 判定（`Get-ChildItem -Filter`），**无环境变量可绕过**（`MT_CHANNEL` 只能选 autojs/native，不等于绕过判定）。
   该判定被 `inject / mouse / shot / reload` 四类 op 与 `Get-MtChannelPlan` 共用（`Mt.Channel.psm1:390 / :486 / :635 / :685 / :745`）。

由此产生的**行为落差**（对 27 条断言是决定性的）：
- `eval_js` / `await_state` / `mcp_status` 走 `Mt.AutoJs.psm1` **直连端点**，不受该判定影响——只要 25585 真起来就能跑。
- `inject_command` / `screenshot` / `reload` 走 `Mt.Channel`，**被判定拦住** ⇒ 返回 `DELEGATED`（`mt_case` 记 `MT_CHANNEL: delegated_count=N`，并且**不把整轮判成失败**，很容易被误读成"通过"）。
- 截图 `DELEGATED` ⇒ `screenshots/<tag>.png` 不落盘 ⇒ a26 / a27 的 vision 断言拿不到图 ⇒ BLOCKED。
- 顺带纠正一处常见误判：harness **不需要 AutoJS 自带截图工具**——`Mt.Channel.psm1:761-787` 是拼一段 JS 在客户端 `eval` 里调原版 `net.minecraft.client.Screenshot.grab`（三档签名回退）。AutoJS 1.0.0 的 66 个 class 里确实没有 Screenshot 工具类（只有 `EvalJsTool` / `StatusTool` / `KubeJSLogTool` / `ReloadScriptsTool`），这**不构成**阻塞。

### 5.3 27 条断言逐条风险（"能否在不加 AutoJS 的情况下跑完"）

（下表 24 行覆盖 27 条断言：a16–a19 四条同类合并为一行，其余逐条列出。）

前提：bash 层 `inject_command` 只在 `--side server` 下可用，且打的是**专用服务端**；本用例声明 `side=client`（客户端内置服务器 + AutoJS）。"纯 RCON"列 = 不加 AutoJS、只走 25575 的最佳情形。

| # | 断言（要点） | 数据来源 | 纯 RCON | AutoJS 通道 | 风险 / 判据 |
|---|---|---|---|---|---|
| a01 | `- astral_dice 1.5.0+fabric_1.20.1` | 启动日志 mod 列表 | ✅ | ✅ | 日志**不轮转**时会命中历史行 = 假绿（B3） |
| a02 | `[REL150] ver=… gamemode=creative` | s16 eval_js(server) | ❌ 行不存在 | ✅ | 行由 eval 打印；需确认 server 侧输出落在**客户端** `latest.log`（集成服务器通常同日志，**待首跑验证**） |
| a03 | `pos=100,-?[0-9]+,100` | s16 | ❌ | ✅ | `/tp` 早于玩家进入则落空 ⇒ pos 不变 ⇒ 假红（B2） |
| a04 | `hp=20` | s16 | ❌ | ✅ | 世界若带伤害/负面效果遗留则非 20 |
| a05 | `item=astral_dice:abyss_reagent_chip` | s16 | ❌ | ✅ | 依赖 `/give` 已到达玩家 |
| a06 | `xp=30` | s16 | ❌ | ✅ | 对应 `/xp set @a 30 levels` |
| a07 | `slotted=[1-9][0-9]*` | s16 | ❌ | ✅ | 依赖饰品槽有内容（Forge 世界遗物可能影响） |
| a08 | `chips=3` | s16 | ❌ | ✅ | 对应 `/give … 3` |
| a09 | `modver=1\.5\.0\+` | s16 | ❌ | ✅ | 与 a01 同源 |
| a10 | `chip id=… count=3 name=(深渊试剂\|Abyss Reagent)` | s17 eval_js(client) | ❌ | ✅ | 客户端语言实测 `lang:zh_cn` ✓（若改成 ja_jp 则两分支全不匹配）；中文名 `zh_cn.json:993`、英文名 `en_us.json:993` 均存在 |
| a11 | `tip i=0 text=.+` | s17 的 getTooltipFromItem | ❌ | ⚠️ | 客户端未真正 hover 物品时 tooltip 可能为空 ⇒ 假红；s18 只是 best-effort 移动鼠标 |
| a12 | `guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=.+` | s26 Patchouli | ❌ | ⚠️ | 键在 jar 内存在（en_us / zh_cn / ja_jp 的 `.json:994`，patchouli 条目 `chips_healing/abyss_reagent_chip.json:9`）；但 `run/client` 下**尚无 `patchouli_books/astral_guide`**，首开需导出 + 贴图加载，`wait 1400ms` 可能不够 |
| a13 | `inventory open=1` | s18 InventoryScreen | ❌ | ✅ | 客户端 `mc.setScreen` |
| a14 | `CHIP_SHOP_OPEN player=` | 服务端 LOGGER | ❌ | ✅ | 真实日志行：`shop/ChipShopOpener.java:64` `LOGGER.info("[astral_dice] CHIP_SHOP_OPEN player={} offers={} purchased={} refreshes={}"`，由 s22 触发 |
| a15 | `screen=\S*ChipShopScreen` | s24 eval(client) | ❌ | ✅ | 类存在：`screen/ChipShopScreen.java:66`，注册 `client/ModClientEvents.java:67` |
| a16–a19 | `sys id=<4 个> ok=1` | s29 eval(server) | ❌ | ✅ | 4 个 id 均注册于 `item/ModItems.java`（`:285` attack_card_medium、`:869` abyss_reagent_chip、`:1148` jill_sign、dice） |
| a20 | eval from=state 正则 | save_as=state | ❌ | ✅ | 依赖 a02–a09 同源值 |
| a21 | eval from=chip `^(深渊试剂\|Abyss Reagent)` | save_as=chip | ❌ | ✅ | 同 a10 |
| a22 | eval from=screen `ChipShopScreen$` | save_as=screen | ❌ | ✅ | 同 a15 |
| a23 | absent `No player was found\|没有找到玩家\|…` | 日志 | ❌ **必假红** | ⚠️ | 纯 RCON / 无玩家时 `@a` 为空 ⇒ 服务端**必然**打 `No player was found`；AutoJS 通道下仍可能因"注入早于玩家进世界"命中（B2） |
| a24 | jar models≥120 recipes≥100 signs≥25 trinkets≥6 embed=true entries≥1800 | `build/libs` 最新 jar | ✅ | ✅ | 实测 1.5.0 jar：158 / 125 / 32 / 8 / 1 / 2305 ⇒ **全部有余量** |
| a25 | crash | 日志 / 崩溃报告 | ✅ | ✅ | 两端**都无 `crash-reports` 目录** ⇒ 无旧报告假红；但历史崩溃行会残留（日志不轮转，B3） |
| a26 | vision `abyss_reagent_tooltip` | 截图 + 视觉判定 | ❌ BLOCKED | ⚠️ | 截图被 `Mt.Channel` 判定拦 ⇒ DELEGATED ⇒ 无图 |
| a27 | vision `chip_shop_ui` | 同上 | ❌ BLOCKED | ⚠️ | 同上 |

**统计**：
- 纯 RCON 可判 = **4/27**（a01、a23、a24、a25），其中 a23 必假红 ⇒ **有效 3/27**。
- AutoJS 通道（端点真起来的前提下）schema 层面 **27/27 可判**；但 a26 / a27 当前被部署判定拦住 ⇒ 实际 **25/27**，另 a11 / a12 属时序/首开风险，需首跑盯。

> 结论：**"不加 AutoJS 跑完 27 条" 不可能**。a02–a22 的数据全部由 `eval_js` 打印，没有 AutoJS 这些行在物理上不存在；a26 / a27 需要客户端帧缓冲。最接近的替代是"RCON 版冒烟清单"（a01 / a24 / a25），只能当健康检查，不能当发布验收。

---

## 6. 阻塞项与建议

### B1（硬阻塞）AutoJS 部署判定与真实部署方式错位
位置：`launch.sh:191-210` + `Mt.Channel.psm1:225-242`。两条最小改法，二选一：

- **方案 A（环境侧，零改码，建议先试）**：**不加** `-PautojsTestMod=1`，改为把 mavenLocal 的
  `AutoJS-fabric-1.20.1-1.0.0.jar` 手动放进 `fabric-1.20.1/run/client/mods/`（需要服务端侧就同时放 `run/server/mods/`）。
  这样两处判定都成立（`mods` 里真有 jar）。**前提是不要再同时传 `-PautojsTestMod=1`**，否则同一 mod id 会重复加载 ⇒ 启动崩溃。
  依赖已就位：`fabric-api`（modImplementation，恒在）、`rhino`（kubejs modRuntimeOnly，恒在）。
  验收方式：启动后 `curl http://127.0.0.1:25585/health` 应返回 `ok`。
- **方案 B（harness 侧，改 1 处判定）**：给 `Test-MtChannelAutoJsDeployed`（以及 `launch.sh` 的 autojs 判定）在 fabric 上补一条"classpath 部署"分支——例如 `-PautojsTestMod=1` 或 `/health` 通即视为已部署。改动面小，但属测试台代码，需走你们的评审流程。

### B2（时序风险）注入早于玩家进世界
client 就绪 marker 是 `Sound engine started`——**世界还没加载**；用例 s04–s14 立刻注入 `@a`，玩家可能尚未加入 ⇒ `No player was found` ⇒ a23 假红，且 a03 / a05 / a08 全部落空。
建议（不改用例）：用 `launch.sh --wait-world <s>` 等世界 marker；或开跑前手工确认 `/list` 有玩家。
若允许增强用例，最小改法是在 s03 后插一个 `await_state`（等 `AutoJS.player()` 非空）——当前 validate 为 OK，故**未改动**。

### B3（判据污染）`latest.log` 不轮转
跑前手工把 `fabric-1.20.1/run/{client,server}/logs/latest.log` 轮转为 `latest.log.pre-<ts>`，否则 a01 / a23 / a25 会被历史行污染。`live.sh` 内置这套逻辑，直接调 `mt.sh --phase cases` 没有。

### B4（危险坑）跨线命令行匹配 bug
`lib/common.sh:263-269` 的 fabric 兜底分支不判版本 ⇒ `mt_rows_for_version` 对 **1.21.1 / 1.20.1 / 26.1.2 / fabric-1.20.1 四线全部命中** fabric 进程（实测 PID 29924、37252 全中）。后果：`env.sh` 把 fabric 残留报成"1.21.1 线"并建议 `stop.sh --version 1.21.1`——**照做会杀掉 fabric 服务端**。跨线作业务必先 `stop.sh --dry-run` 核 PID。

### B5（危险开关）`--purge-saves`
白名单含 `<run dir>/saves/testworld` ⇒ 会把 §3 刚复制好的世界删掉。除非要重开世界，禁止使用。

### B6（噪声）世界来自 Forge 线
`serverconfig/`、`ftbteams/`、`enigmatic_transience.json`、`kubejs_persistent_data.nbt` 在 fabric 下不被消费，可能触发第三方 mod 的告警日志；对 a23（absent）与 a25（crash）是污染源。可接受，但要知情。

### B7（登记，不动）产物残留
`astral_dice-1.5.0-rc.7+fabric_1.20.1.jar` 属旧版残留，按任务要求**只登记不删**。注意 `jar_stats` 按 mtime 取最新，1.5.0 更新 ⇒ 断言仍指向正式版（若有人回退文件时间戳会误取 rc.7）。

### B8（外部依赖）端口与同线进程
静态作业期间 `25565 / 25575` 被 fabric dev server 占用，另一条线的 Forge 客户端（PID 35632）在跑。**开跑前必须确认另一条线实机测试已结束**，避免端口 / CPU / 内存互相干扰。

---

## 7. fabric 产物清单（任务 5，只登记不动）

`fabric-1.20.1/build/libs/` 共 **2** 个 jar：

| 文件 | 字节 | mtime | jar 内 version | 判定 |
|---|---|---|---|---|
| `astral_dice-1.5.0+fabric_1.20.1.jar` | 3,031,074 | 2026-11-02 02:12:28 | `1.5.0+fabric_1.20.1` | ✅ 当前 1.5.0 正式版 |
| `astral_dice-1.5.0-rc.7+fabric_1.20.1.jar` | 3,027,461 | 2026-11-01 23:16:45 | `1.5.0-rc.7+fabric_1.20.1` | ⚠️ 旧版残留（后续清理范围） |

jar_stats（与 harness 同一套计数规则）：

| jar | entries | models | recipes | trinkets | signs | bountiful | embed | 内嵌库 |
|---|---|---|---|---|---|---|---|---|
| 1.5.0 | 2305 | 158 | 125 | 8 | 32 | 0 | 1 | `META-INF/jars/starengine-fabric-1.20.1-1.1.0.jar` |
| 1.5.0-rc.7 | 2299 | 158 | 125 | 8 | 32 | 0 | 1 | 同上 |

两个 jar 都**不含 autojs 条目**（正确：AutoJS 是 modRuntimeOnly，不 jarJar）。
a24 阈值（120 / 100 / 25 / 6 / true / 1800）在 1.5.0 上**全部有余量**。

---

## 8. 开跑条件判定

**可以直接用的**：`env` / `preflight` / `launch` / `stop` / `validate` / 世界就位 / 产物。

**开跑 27 条前必须先做的三件事**：
1. 解决 **B1**（方案 A 最省事：AutoJS jar 手动进 `run/client/mods`，且不要同时开 `-PautojsTestMod`）；
2. 跑前轮转两端 `latest.log`（**B3**）；
3. 用 `--wait-world` 或前置"等玩家"（**B2**），否则 a23 必假红。

**B1 未解决时的现实预期**：走到 s16（首个 eval_js）即 `BLOCKED`；`mt_case` 以 exit 11 结束，报告为 20 条 BLOCKED / 4 条可判 / 3 条通过——**不能作为发布验收结论**。
