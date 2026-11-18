# astral_dice 1.5.0-hotfix+fabric_1.20.1 启动冒烟报告

- 仓库：`F:/MCProject/astral_dice_multiloader`（分支未改动，未执行任何 git 操作）
- 任务性质：**只做启动冒烟**（加载 + 进世界），不验证玩法、不验证渲染正确性、不改源码
- 执行时间：2026-11-02 18:35:53 启动 → 18:36:11 进世界 → 18:39 前后停机
- 环境：Minecraft 1.20.1 / Fabric Loader 0.19.5 / 87 个 mod

## 结论（全部四项判据）

| # | 判据 | 期望 | 实测 | 判定 |
|---|---|---|---|---|
| 1 | mod 清单行 `astral_dice` 版本 | `1.5.0-hotfix+fabric_1.20.1` | 同左 | **通过** |
| 2 | `[astral_dice-version-gate]` 行 | `网络互通号=1.5` | `mod_version=1.5.0-hotfix+fabric_1.20.1 → 网络互通号=1.5` | **通过** |
| 3 | `Failed to parse resource conditions` 计数 | 0 | **0** | **通过** |
| 4 | ERROR/FATAL 且栈含 `com.merlinkitsune` 的帧 | 0 | **0**（日志中 `com.merlinkitsune` 出现 0 次） | **通过** |

进世界判据（等价行 `Time from main menu to in-game`）命中，总加载 18.6 s。

**一句话结论**：hotfix jar 在 Fabric 1.20.1 上正常加载并进入世界，版本门禁解析为互通号 1.5，资源条件解析无失败，无本模组相关错误帧。

## 步骤 1：mods 目录核对

`run/fabric-1.20.1/mods/` 实测内容（**无需复制或删除**，投件已是 hotfix 件，mtime 18:32:16 与 build 产物一致）：

```
astral_dice-1.5.0-hotfix+fabric_1.20.1.jar   2944301   2026/11/2 18:32:16
AutoJS-fabric-1.20.1-1.0.0.jar                164493   2026/10/22 12:49:33
starengine-fabric-1.20.1-1.1.0.jar            112634   2026/11/2 12:26:48
```

- 旧件 `astral_dice-1.5.0+fabric_1.20.1.jar` **不在** mods 目录内（它只存在于构建输出目录 `fabric-1.20.1/build/libs/`，未进入运行期）。
- 前置 starengine 同目录存在：`starengine-fabric-1.20.1-1.1.0.jar`，运行期以 `starengine 1.1.0` 出现在 mod 清单第 87 行。
- jar 元数据实测：`fabric.mod.json` → `id=astral_dice`，`version=1.5.0-hotfix+fabric_1.20.1`，入口 `com.merlinkitsune.astral_dice.AstralDiceMod` / `AstralDiceClient`，依赖 `starengine >=1.1.0 <2.0.0`。

## 步骤 2：端口占用

- 启动前：`netstat -ano` 全表过滤 `:25565` / `:25585` → **0 条监听**。
- 停机后：`Get-NetTCPConnection -State Listen` 过滤 25565/25585 → `NO_LISTEN_25565_25585`（**0 条监听**）。
- 全程未发现其它游戏进程占用这两个端口，未杀除任何非本次启动的进程。

## 步骤 3：启动

先删除陈旧参数文件（按任务要求）：

```
rm -f fabric-1.20.1/build/loom-cache/argFiles/runClient
# 删除后该目录只剩 runDatagen / runServer
```

启动命令（后台执行，控制台输出落 `work/_smoke/runclient-console.log`）：

```
gradlew.bat :fabric-1.20.1:runClient --console=plain -PautojsTestMod=1 -PtestSodium=false -Pquickplay=testworld
```

进世界证据（`fabric-1.20.1/run/client/logs/latest.log`）：

```
403:[18:36:11] [Render thread/WARN] (ModernFix) Time from main menu to in-game was 2.6083264 seconds
404:[18:36:11] [Render thread/WARN] (ModernFix) Total time to load game and open world was 18.638327 seconds
```

quickplay 生效佐证：`saves/` 下存在 `testworld`，日志出现 `Starting integrated minecraft server version 1.20.1`（301 行）、`Preparing start region for dimension minecraft:overworld`（303 行）、`Player892加入了游戏`（320 行）。

## 步骤 4：判据原文（逐条照抄 latest.log）

日志对象：`fabric-1.20.1/run/client/logs/latest.log`（本次运行共 **419 行**，时间戳 18:35–18:38）；同批快照留存于 `work/_smoke/latest-150-hotfix.log`。

### 4.1 mod 清单行

```
2:[18:35:56] [main/INFO] (FabricLoader) Loading 87 mods:
5:	- astral_dice 1.5.0-hotfix+fabric_1.20.1
```

### 4.2 版本门禁行

```
171:[18:36:05] [Render thread/WARN] (astral_dice-version-gate) [Astral Dice] 版本互通门槛:类路径上找不到 /astral_dice_version.properties,改用加载器元数据解析版本号
172:[18:36:05] [Render thread/INFO] (astral_dice-version-gate) [Astral Dice] 版本互通门槛(Version Gate):mod_version=1.5.0-hotfix+fabric_1.20.1 → 网络互通号=1.5;正式版按二号位(major.minor)互通,含 SNAPSHOT 的构建只与完整版本串相同的快照互通。
```

171 行的 WARN 是**预期回退路径**（生产 jar 里不带 `astral_dice_version.properties`，改用加载器元数据），不是故障；172 行即判据命中行。

另有一条运行期互通佐证（握手确实按 1.5 上报）：

```
321:[18:36:09] [Server thread/INFO] (AstralDice) AP_NET_VERSION_PROBE: player=Player892 local=1.5 ticks=0
```

### 4.3 资源条件解析失败计数

```
$ grep -n "Failed to parse resource conditions" latest.log | wc -l
0
```

**计数 = 0**。

### 4.4 ERROR / FATAL 与本模组栈帧

```
$ grep -cE "\[(ERROR|FATAL)\]|/(ERROR|FATAL)\]" latest.log
1
$ grep -c "com\.merlinkitsune" latest.log
0
```

**栈含 `com.merlinkitsune` 的帧 = 0**；实际上整个日志里 `com.merlinkitsune` 字符串出现 0 次（本模组包根确认为 `com.merlinkitsune.astral_dice`，故该 0 是有意义的 0，不是包名对不上导致的假阴性）。

唯一一条 ERROR 行**与本模组无关**，且其后没有任何栈帧（下一行即 JEI 初始化）：

```
323:[18:36:09] [Render thread/ERROR] (SyncEntireContainer) Unable to Sync Container Data for a given Entity as its Capability is null on the Client! [EntityId: 2]
324:[18:36:09] [Render thread/INFO] (LoggedTimer) Starting JEI...
```

- `SyncEntireContainer` 为第三方模组的日志通道名，非 astral_dice；无堆栈、无崩溃、无 `crash-reports` 目录生成（`fabric-1.20.1/run/client/crash-reports` 不存在）。

## 步骤 5：收尾（按 PID 精确停机）

本次启动的进程链（全部创建于 18:35:53–18:35:55）：

| PID | 角色 | 创建时间 | 处置 |
|---|---|---|---|
| 43528 | `cmd.exe`（wrapper 外层） | 18:35:53 | 已 `Stop-Process -Force`，确认 GONE |
| 2408 | `java.exe`（gradle wrapper `GradleWrapperMain`） | 18:35:53 | 已 `Stop-Process -Force`，确认 GONE |
| 42472 | `java.exe`（Fabric 客户端 `KnotClient`，PPID=33152） | 18:35:55 | 已 `Stop-Process -Force`，确认 GONE |
| **33152** | `GradleDaemon 9.5.1` | **18:31:53（早于本次启动）** | **未动，仍存活** |

- 停机后 java 进程只剩 33152（本次启动前既有的 GradleDaemon），未误杀。
- 停机后端口复查：`NO_LISTEN_25565_25585`，25565/25585 **无监听**。

## 未确证项 / 口径说明

1. **只做启动冒烟**：未验证任何玩法、配方、GUI、渲染与网络互通的实际行为；`互通号=1.5` 仅证明门禁解析结果，未做双端联机比对。
2. **客户端为强制终止**（`Stop-Process -Force`），未走游戏内正常退出流程，因此日志中没有 `Stopping!` 一类收尾行；世界数据靠暂停时的自动存档落盘（`Saving and pausing game...` 出现 3 次，最后一次 18:38:05）。
3. 进世界后客户端长时间处于暂停状态（`Saving and pausing game`），本次**未做任何输入/交互**，故「暂停状态下是否一切正常」之外的表现未取证。
4. 唯一 ERROR（`SyncEntireContainer`，EntityId: 2）本次**未追查影响面**，只确认其不属于本模组、无堆栈、未导致崩溃。
5. 构建输出目录 `fabric-1.20.1/build/libs/` 内**新旧两个 jar 并存**（`astral_dice-1.5.0+fabric_1.20.1.jar` 与 `astral_dice-1.5.0-hotfix+fabric_1.20.1.jar`）；按任务口径本次只要求运行期 mods 目录是 hotfix 件，已满足，构建目录未清理。
6. 本次改动仅限：删除构建产物 `fabric-1.20.1/build/loom-cache/argFiles/runClient`（任务要求），新增 `work/_smoke/`（辅助脚本、控制台日志、日志快照）与本报告。未改源码、未 commit/push、未触碰 `astral_dice_multiloader-next`。
7. 启动前后 `F:/MCProject/.agent-locks/` 只有两个与本次无关的 `_b1_*.py` 文件，无 `dsh.lock`/`kimi.lock` 活跃锁。

## 复跑用命令

```powershell
# 判据复核（在仓库根执行，日志以 work/_smoke/latest-150-hotfix.log 或 run 侧 latest.log 为准）
grep -n -- "- astral_dice " fabric-1.20.1/run/client/logs/latest.log
grep -n "astral_dice-version-gate" fabric-1.20.1/run/client/logs/latest.log
grep -n "Failed to parse resource conditions" fabric-1.20.1/run/client/logs/latest.log | wc -l    # 期望 0
grep -cE "\[(ERROR|FATAL)\]|/(ERROR|FATAL)\]" fabric-1.20.1/run/client/logs/latest.log          # 实测 1（第三方 SyncEntireContainer）
grep -c "com\.merlinkitsune" fabric-1.20.1/run/client/logs/latest.log                              # 期望 0
grep -n "Time from main menu to in-game" fabric-1.20.1/run/client/logs/latest.log
```
