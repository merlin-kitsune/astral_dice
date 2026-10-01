# fabric-1.20.1 线测试台（`scripts/test/fabric/`）

> 自包含的第四条线测试台。**不修改、不依赖** `scripts/test/mt*.ps1` + `lib/Mt.*.psm1` + `cases/*.json`  
> 那一套生产线资产（那套是 NeoForge 1.21.1 / Forge 1.20.1 / NeoForge 26.1.2 三线的口径）。  
> 一律 PowerShell 7（`pwsh`）；禁用 Windows PowerShell 5.1（见 `AGENTS.md`「命令执行规范」）。



---

## 1. 为什么**不能**复用 `mt.ps1`（逐项差异）

| 维度     | 生产线（Forge/NeoForge）                                                             | fabric-1.20.1 线                                                                                                                                                                                                                             | 后果：为什么生产线脚本用不了                                             |
| ------ | ------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- |
| 事件注册   | `@Mod.EventBusSubscriber` 自动注册                                                  | **自建反射总线** `platform/event/LoaderBus`；须在 `AstralDiceMod.registerListeners()` 显式登记（`platform/event/LoaderBus.java:103-161`、`AstralDiceMod.java:51,91`）                                                                                       | 没有「注册失败」这种可 grep 的 Forge 报错形态；必须用 fabric 专属的派发计数报告         |
| 事件派发   | Forge 原生 EventBus                                                               | **四路**：FAPI 回调（`platform/FabricBridges.java:107-121`）+ Puzzles Lib（`platform/PuzzlesBridges`）+ 你自写 mixin（`mixin/bridge/**`）+ 前置库事件                                                                                                          | 「桥装了但事件从不触发」是**静默**失效；生产线没有对应护栏                            |
| 启动入口   | `@Mod` 构造器                                                                      | `fabric.mod.json` 的 `main` / `client` / `fabric-datagen` entrypoint（`src/main/resources/fabric.mod.json:14-24`）                                                                                                                             | 启动期事实的锚点不同                                                 |
| 运行目录   | 仓库根 `run/<版本>`（gradle `gameDirectory = rootProject.file('run/1.21.1')`）         | **子项目内** `fabric-1.20.1/run/{client,server}`（Loom `runDir 'run/client'` 相对**子项目**解析，`build.gradle:172-182`）；datagen → `run/datagen`                                                                                                         | 所有路径推导都不一样                                                 |
| 构建     | `gradlew :forge-1.20.1:build`                                                   | `gradlew :fabric-1.20.1:build`                                                                                                                                                                                                              | 任务名不同                                                      |
| 依赖     | Curios / Mixin Booster                                                          | Trinkets 3.7.2（+ Accessories 可选）+ Puzzles Lib 8.1.33 + Forge Config API Port + Fabric API + Cardinal Components（`build.gradle:65-158`、`gradle.properties`）                                                                                  | `mt_env.ps1` 装的前置清单完全不适用                                   |
| 探测手段   | KubeJS 探针 + `/astralparty` 管理员命令                                                | 同样 KubeJS（已在 `modRuntimeOnly`），**外加** fabric 专属 `LoaderBus#dispatchReport()`                                                                                                                                                                | 生产线没有 dispatchReport，抓不到「0 次派发」                            |
| 判定读数   | `AP_<tag>_<KEY>:` 聊天通道机器行                                                       | 本台沿用 `AP_` 前缀族，但读数源改为：加载器日志 + 派发统计 + 开包条目 + （RCON 注入回显）                                                                                                                                                                                     | 断言对象不同                                                     |
| 收停     | `mt_cleanup.ps1` 按 gradle 选择器 + run 目录匹配                                        | Loom dev-run 入口主类 `net.fabricmc.devlaunchinjector.Main` + 命令行含 `fabric-1.20.1`（实测 `dev-launch-injector-0.2.1+build.8.jar` 内 `net/fabricmc/devlaunchinjector/Main.class`，其属性名实测为 `fabric.dli.config` / `fabric.dli.env` / `fabric.dli.main`） | 判据不同                                                       |
| 输入注入   | Win32 `PostMessage` 键鼠（`mt_inject.ps1` + `Mt.Win32.psm1`，依赖 en-US 键盘布局与窗口线程输入法） | 本台面向**服务端命令**：**RCON（唯一通道）**                                                                                                                                                                                                                | 客户端 GUI 键鼠注入本台未覆盖（缺口，见 §8）                                 |
| 日志窗口   | 文件身份锚点（ctime + 头部指纹 + 长度）                                                       | **同口径**（`Get-FtLogAnchor`）—— 因为 latest.log **每次冷启动都被 log4j 轮转**，纯字节偏移游标在每次启动都失效（实测缺陷，见 §7.4）                                                                                                                                                | 旧实现用「启动前字节长度」当游标 ⇒ 每次启动必然假超时                               |
| 日志读取   | 文件空闲时读（可 `ReadAllBytes`）                                                        | **必须共享读写打开**（`FileShare.ReadWrite`）—— 游戏运行时 latest.log 被独占，`File.ReadAllBytes`（share=Read）**每次**都抛「being used by another process」（实测缺陷，见 §7.4）                                                                                              | 不共享打开 ⇒「服务端还活着时读日志」全线不可用                                   |
| 批量编排闸门 | `mt.ps1` / `mt_case.ps1` / `mt_watchdog.ps1`（三处 + watchdog）                     | `ft.ps1` / `ft_case.ps1` / `ft_launch.ps1 --side both`                                                                                                                                                                                      | 闸门维度不同：fabric 单版本 ⇒「三线顺序」与 `watchdog -Action stop` **N/A** |

**从第一性看**：生产线测试台的每一个共享函数（`Mt.Paths` 的版本→子项目映射、`Mt.Phase` 的三线门控、  
`mt_env` 的前置清单、`mt_launch` 的窗口/输入法/禁 AI 闸门、`mt_case` 的进度信标）都把「三线 + Forge 语义」  
写死在参数面与路径面上。复用等于把错误的假设带进来，比另写一套更贵。

---

## 2. 文件清单与职责

| 文件                                | 职责                                                                                                          | 关键约定                                                                      |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| `lib/Ft.Common.psm1`              | 共享原语：路径派生（全部 `$PSScriptRoot` 绝对路径）、统一输出、退出码、**批量编排闸门**、日志窗口读取、jar 开包统计、字节透传子进程                              | 退出码与 `scripts/test/lib/Mt.Phase.psm1:29-36` 逐值一致                          |
| `ft.ps1`                          | 阶段编排入口（`--phase build\|env\|launch\|stop\|case\|report`）+ 闸门（无 `--phase` / `case` 无 `--case` / `report`）    | 逐字节透传子脚本输出；只返回退出码                                                         |
| `ft_build.ps1`                    | `gradlew :fabric-1.20.1:build` + **开包核对**（models/item、recipes、bountiful=0、trinkets、`META-INF/jars`）         | 读数行 `AP_FAB_*`；核对失败 = FAIL(1)，跑不起来 = ERROR(2)                             |
| `ft_env.ps1`                      | 运行环境装配/校验：目录骨架、`eula.txt`、前置可得性（Loom 缓存 / `mods` 双查）、可选装 RCON、可选装 KubeJS **读数探针**                           | `--mode verify`（默认，只读）/ `install`；`--enable-rcon`；`--install-probe [脚本名]` |
| `ft_launch.ps1`                   | 后台起 `:fabric-1.20.1:runServer\|runClient` + 就绪轮询（**文件身份锚点**，只认本次启动新写入的日志）+ 冻结 launch 窗口 + `--gradle-arg` 透传 | 就绪标记：服务端 `Done (…)`/客户端 `Sound engine started`                            |
| `ft_stop.ps1`                     | 状态文件 PID 树 + CIM 扫描 `devlaunchinjector` 进程，只杀本线；可选 `--stop-daemon` / `--purge-saves`                        | 绝不 taskkill 无关 java                                                       |
| `ft_inject.ps1`                   | 命令注入：`--channel rcon`（**唯一通道**，vanilla 原生、同步返回回显）                                                           | 传 `kubejs` 会**显式失败**并给出实测理由（见 §7.3/D3）                                    |
| `ft_assert.ps1`                   | 断言引擎：`snapshot` / `log` / `absent` / `case`；窗口 `case`(默认)`\|launch\|whole`                                  | 可 dot-source（`InvocationName` 守卫）供其它脚本复用                                  |
| `ft_dispatchreport.ps1`           | 抽取 `LoaderBus#dispatchReport()`，**把 0 次派发的事件类单独列出**                                                         | 硬判据：`ServerTickEvent > 0`                                                 |
| `ft_prod.ps1`                     | **生产映射冒烟**：在**真实整合包实例**（生产 intermediary 环境）启动一次，按「崩溃报告新增 / 入口点失败 / 注入失败 / 到主菜单」四类判据裁决（详见 §7.2.2）                    | **只用 PowerShell 7**（依赖 `ProcessStartInfo.ArgumentList`）；实例路径**必须传参**（脚本内不硬编码中文路径）；`-PreloadClasses` 提前加载目标类、`-ExtraJvmArg` 透传任意 JVM 参数；默认收停，`-KeepAlive` 保留现场 |

| `cases/FAB-BOOT-EMBED.json`       | ① 服务端启动到 `Done` + 内嵌库自检（9 断言）                                                                               | 只需一次已完成的启动；**刻意不含**派发统计断言（那是 DISPATCH-BASIC 的职责，且会引入 30 s 的隐藏时间耦合）        |
| `cases/FAB-DISPATCH-BASIC.json`   | ② 派发报告里基础事件为正数（8 断言）                                                                                        | 需服务端跑过 600 tick                                                           |
| `cases/FAB-JAR-ASSETS.json`       | ③ 产物资源完整性（1 断言，**不需要游戏**）                                                                                   | 只需 `ft_build`                                                             |
| `cases/FAB-CLIENT-BOOT.json`      | ④ 客户端启动期事实：桥已安装 / 注册桥读数 / 审计通过 / 无类加载与崩溃（8 断言）                                                              | 需 `ft_launch --side client --gradle-arg -PtestSodium=false`               |
| `cases/FAB-INJECT-ROUNDTRIP.json` | ⑤ 注入往返（3 断言）                                                                                                | 需活着且已启 RCON 的服务端；锚点 = `/say <tag>` 的控制台回声                                 |
| `astral_fabric_curio_probe.js`    | **饰品后端取证探针**（2026-10-01 新增）：命令 `/astralfab <env\|sources\|groups\|state\|equip\|unequip\|clear\|fillchip\|refresh\|enforce\|clearinv>`；权威出口是 `console.info`（→ `run/server/logs/latest.log`），`sendFailure` 只作 RCON 同步回显。用于「Trinkets / Accessories 两后端行为等价性」取证 | 见 §7.2.6；探针**只读写观测**，槽位增减全部走产品入口 |
| `event_bridge_probe.js`           | **既有文件，原样保留**：人工取证脚本（造僵尸/伤害/效果来区分「桥没接」与「确实没发生」）                                                             | 由 `ft_env --install-probe` 装进 `run/server/kubejs/server_scripts/`         |
| `.syntax-check.txt`               | 全部 `.ps1/.psm1` 的 PowerShell 语法解析自检结果（见 §7）                                                                 | 每次改动后重跑（命令见 §7）                                                           |

---

## 3. 用法

```powershell
# ── 基础设施（放行清单：把运行环境弄起来 / 收停）────────────────────────────
pwsh -NoProfile -File scripts/test/fabric/ft_build.ps1                  # 构建 + 开包核对
pwsh -NoProfile -File scripts/test/fabric/ft_env.ps1 --side both        # 前置/目录校验（只读）
pwsh -NoProfile -File scripts/test/fabric/ft_env.ps1 --side server --enable-rcon   # 开 RCON（注入的唯一通道）
pwsh -NoProfile -File scripts/test/fabric/ft_env.ps1 --side server --install-probe # 装 KubeJS 读数探针
pwsh -NoProfile -File scripts/test/fabric/ft_env.ps1 --side both --install-embedded # 补 Loom 剥离的 JiJ 内嵌库（**dev 必做**，见 §7.2.6 与 KI-F23）
pwsh -NoProfile -File scripts/test/fabric/ft_launch.ps1 --side server   # 起服务端并等就绪
# 客户端：本机 dev 环境**必须**带 -PtestSodium=false（Sodium 要 LWJGL 3.3.1，环境是 3.3.2-snapshot）
pwsh -NoProfile -File scripts/test/fabric/ft_launch.ps1 --side client --timeout 420 --gradle-arg -PtestSodium=false
# 客户端直接进世界（需要已有存档）：再加一个
#   --gradle-arg -Pquickplay=<世界名>

# ── 手动读数 / 手动注入（逐条、边发边看）──────────────────────────────────
pwsh -NoProfile -File scripts/test/fabric/ft_dispatchreport.ps1 --side server
pwsh -NoProfile -File scripts/test/fabric/ft_assert.ps1 snapshot --side server --window case
pwsh -NoProfile -File scripts/test/fabric/ft_assert.ps1 log --pattern '事件派发统计' --window whole
pwsh -NoProfile -File scripts/test/fabric/ft_assert.ps1 absent --pattern 'Unknown or incomplete command'
pwsh -NoProfile -File scripts/test/fabric/ft_inject.ps1 cmd --command "/give @s astral_dice:star_coin 3"
pwsh -NoProfile -File scripts/test/fabric/ft_inject.ps1 cmd --command "/say FT-ANCHOR-1"    # 可被日志断言
```

> ⚠️ **读数类脚本在服务端活着时也能跑**（这是本轮修掉的一个致命缺陷）：日志走共享读写打开  
> （`Read-FtFileBytesShared`），不再用会被独占挡住的 `File.ReadAllBytes`。

```powershell
# ── 单条用例（放行）─────────────────────────────────────────────────────
pwsh -NoProfile -File scripts/test/fabric/ft_case.ps1 list
pwsh -NoProfile -File scripts/test/fabric/ft_case.ps1 validate --all
pwsh -NoProfile -File scripts/test/fabric/ft_case.ps1 run --case scripts/test/fabric/cases/FAB-JAR-ASSETS.json
pwsh -NoProfile -File scripts/test/fabric/ft.ps1 --phase case --case scripts/test/fabric/cases/FAB-DISPATCH-BASIC.json

# ── 收尾 ───────────────────────────────────────────────────────────────
pwsh -NoProfile -File scripts/test/fabric/ft_stop.ps1 --side server --purge-saves
pwsh -NoProfile -File scripts/test/fabric/ft_stop.ps1 --side client
```

推荐顺序（与 §1 的差异对应）：`ft_build` → `ft_env --side both` → `ft_env --side server --enable-rcon`  
（→ 想用探针再加 `--install-probe`）→ `ft_launch --side server`（冷启动，让 RCON 与探针生效）  
→ 手动 `ft_inject`/`ft_assert` → 逐条 `ft_case run --case` → `ft_stop`。

---

## 4. 批量编排闸门（与生产线同构）

规则来源：`scripts/test/TESTING-RULES-OVERVIEW.md` §3.1（2026-09-27 用户裁决「禁用自动测试，改为纯手动下达命令」）。  
实现：`lib/Ft.Common.psm1` 的 `Assert-FtAutoGate` —— 拦截时 **stderr 首行 `MT_AUTO_DISABLED: …`、退出码 2**；  
临放行开关 **`--allow-auto`** 或环境变量 **`MT_ALLOW_AUTO=1`**（与生产线同形）。

| fabric 侧被拦下的能力                                     | 拦在哪一处           | 对应生产线的哪一项                                     |
| -------------------------------------------------- | --------------- | --------------------------------------------- |
| 全流程（`ft.ps1` 无 `--phase`）                          | `ft.ps1`        | `mt.ps1` 无 `--phase`                          |
| `--phase case` 无 `--case`（走目录批量）                   | `ft.ps1`        | `mt.ps1 --phase cases` 无 `--case`             |
| `--phase report`（收集自动跑出来的结果）                       | `ft.ps1`        | `mt.ps1 --phase report`                       |
| `ft_case.ps1 run` 无 `--case`、`ft_case.ps1 run-dir` | `ft_case.ps1`   | `mt_case.ps1 run-dir`                         |
| `ft_launch.ps1 --side both`（顺序起两条实例）               | `ft_launch.ps1` | `mt.ps1 --phase <p>` 无 `--version`（三线顺序）      |
| —（N/A）                                             | —               | `mt_watchdog.ps1 -Action stop`：本台不提供 watchdog |
| —（N/A）                                             | —               | 三线顺序：fabric 只有一条线，无此维度                        |

**放行**：`--phase build / env / launch / stop`、`--phase case --case <X>`、`ft_case.ps1 run --case <X>`、  
`ft_case.ps1 validate`、`ft_case.ps1 list`、以及手动读数/注入类（`ft_inject` / `ft_assert` / `ft_dispatchreport`）。

---

## 5. 用例 JSON 格式（fabric 侧自定）

```json
{
  "case_id": "FAB-XXX",                     // 必填
  "title": "一句话说明",                      // 必填
  "side": "server",                          // 可选，默认 server
  "window": "case",                          // 可选，默认 case（case|launch|whole）
  "on_fail": "keep_game_running",            // 仅为与生产线用例形似；本台 v1 未实现该行为
  "steps": [
    { "op": "note",           "text": "…" },
    { "op": "inject_command", "command": "/say FT-ANCHOR-1", "channel": "rcon" },
    { "op": "wait",           "ms": 1500 },
    { "op": "wait_for",       "pattern": "事件派发统计", "timeout_ms": 120000, "interval_ms": 1500 },
    { "op": "snapshot",       "window": "case" }
  ],
  "asserts": [
    { "type": "log",    "pattern": "…", "window": "whole" },
    { "type": "absent", "pattern": "…" },
    { "type": "jar",    "models_min": 137, "recipes_min": 118, "bountiful_max": 0, "trinkets_min": 8, "embed_lib": true },
    { "type": "dispatch_tick" },
    { "type": "dispatch_fired", "events": ["ServerTickEvent"] },
    { "type": "dispatch_zero_absent", "events": ["ServerTickEvent"] },
    { "type": "crash" }
  ]
}
```

- `op` 全集：`note` / `wait`（`ms`）/ **`wait_for`**（`pattern` + `timeout_ms` + `interval_ms` + 可选 `window`）/  
  `snapshot` / `inject_command`（`command` 或 `text`；`channel` 默认且**只能**是 `rcon`；`side` 取用例级）。
- **`wait_for` 为什么必要**（真实教训）：本线有读数只在**开局 600 tick（约 30 s）**&#x624D;打印。用固定 `wait 25000`  
  时采样点没到 ⇒ 6 条断言集体失败，现场看起来像**产品缺陷**（「未找到派发报告」），实际只是**等待条件未满足**。  
  固定等待要么太短（假 FAIL）要么太长（白等）。`wait_for` 命中即继续；超时则以 **TIMEOUT(12)** 中止  
  并提示去用 `ft_dispatchreport.ps1` 手动读数 —— 与「断言失败 = 1」明确区分  
  （语义边界同 `TESTING-RULES-OVERVIEW.md` §7：FAIL = 断言不满足，TIMEOUT = 预算内没跑完）。
- 退出码：`0` PASS / `1` 断言失败 / `2` ERROR（参数或用例非法、注入失败、闸门拦截）/ `12` TIMEOUT（`wait_for` 超时）。
- `assert.type` 全集见上；`log`/`absent` 的 `window` 可逐条覆盖用例级默认值。
- 用例级 `window: "whole"` 用于**启动期事实**（如 mods 清单打印在 launch 窗口起点之前）；写成 `case` 会必然假 FAIL。
- 校验：`pwsh -File scripts/test/fabric/ft_case.ps1 validate --all`（schema 检查，不碰游戏）。
- 注入失败（命令缺失 / 通道不可用）⇒ 用例**显式 ERROR 中止**，不静默降级（沿用生产线 §5 的读数纪律）。



---

## 6. 机器可读读数行（前缀汇总）

| 前缀 | 出现脚本 | 含义 |
|---|---|---|
| `AP_FAB_JAR` / `_MODELS` / `_RECIPES` / `_BOUNTIFUL` / `_TRINKETS` / `_EMBED` / `_EMBED_LIB` / `_BUILD` | `ft_build` | 开包核对读数（jar vs 磁盘源对照） |
| `AP_FAB_ENV_DIR` / `_SUBDIRS` / `_EULA` / `_SURFACE` / `_MODS_LIST` / `_REQ` / `_RCON` / `_PROBE` / `_INSTALL` / `AP_FAB_ENV` | `ft_env` | 环境实况与前置判定、RCON 开关、探针安装 |
| `AP_FAB_LAUNCH_TASK` / `_PID` / `_READY` / `AP_FAB_LAUNCH` | `ft_launch` | 启动与就绪 |
| `AP_FAB_STOP_STATE` / `_KILL` / `_DAEMON` / `_PURGE` / `_KILLED` / `AP_FAB_STOP` | `ft_stop` | 收停 |
| `AP_FAB_INJECT_TARGET` / `AP_FAB_INJECT` / `AP_FAB_INJECT_RESP` | `ft_inject` | 注入与回显 |
| `AP_FAB_SNAPSHOT` / `AP_FAB_ASSERT` / `AP_FAB_ASSERT_CASE` | `ft_assert` | 快照与逐条断言 |
| `AP_FAB_DISPATCH` / `_FIRED` / `_ZERO` / `_ZERO_LIST` / `_ZERO_COUNT` / `_TICK` / `_RESULT` | `ft_dispatchreport` | 派发统计（**`_ZERO` 是护栏主体**） |
| `AP_FAB_CASE_BEGIN` / `_ENTRY` / `_VALIDATE` / `_VALIDATE_SUMMARY` / `AP_FAB_CASE` | `ft_case` | 用例执行/校验 |
| `AP_FAB_PHASE_BEGIN` / `AP_FAB_PHASE` / `AP_FAB_REPORT` | `ft.ps1` | 阶段编排 |
| `AP_FAB_PROD_CP` / `_LAUNCH` / `_READY` / `_CRASH` / `_CRASH_CAUSE` / `_FAIL` | `ft_prod` | 生产映射冒烟：classpath 构造 / 启动 / 就绪 / 崩溃 / 失败原因 |
| `AP_FAB_PROD_MIXIN_FAIL` / `_MIXIN_FAIL_DETAIL` | `ft_prod` | 注入失败（`InjectionError`）时那一行原文 —— 一眼看出是哪个模组的哪个注入器 |
| `AP_FAB_PARTY` | **模组侧**（非本台脚本） | 队伍判定三条后端的接入状态：`sw_mc/sw_ftb/sw_opac`（配置开关）+ `back_ftb/back_opac`（反射契约是否解析成功）+ `why_ftb/why_opac`（失败原因）。由 `PartyRelations#reportBackends()` 在 common setup 打印，用例 `FAB-PARTY-BACKENDS` 断言其形态 |

| `MT_FAB_<NAME>: OK/FAIL/ERROR/BLOCKED` | 全部 | 结论行（stderr 走 FAIL/ERROR，stdout 走 OK） |
| `MT_FAB_NOTE[n]` / `MT_FAB_INFO` / `MT_FAB_WARN` | 全部 | 过程行 |

---

## 7. 实测 vs 仅静态校验（诚实区分）

### 7.1 语法解析自检（全部 10 个 `.ps1/.psm1`，`$errs.Count` = 0）

自检命令（把结果落盘后读取，因为本会话的 PowerShell 工具不回显 stdout）：

```powershell
pwsh -NoProfile -Command "$errs=$null; [void][System.Management.Automation.Language.Parser]::ParseFile('<路径>',[ref]$null,[ref]$errs); $errs.Count"
```

结果（同时落盘在 `scripts/test/fabric/.syntax-check.txt`，可随时重生成；该文件被 `.gitignore` 忽略，
属本地证据，末尾还附了最后两条复验调用（`validate --all`、`FAB-JAR-ASSETS`）的原始输出。`PSVersion=7.6.6`）：

| 文件 | `$errs.Count` |
|---|---|
| `ft.ps1` | 0 |
| `ft_build.ps1` | 0 |
| `ft_env.ps1` | 0 |
| `ft_launch.ps1` | 0 |
| `ft_stop.ps1` | 0 |
| `ft_inject.ps1` | 0 |
| `ft_assert.ps1` | 0 |
| `ft_dispatchreport.ps1` | 0 |
| `ft_case.ps1` | 0 |
| `lib/Ft.Common.psm1` | 0 |

> 首轮 `ft_env.ps1` 曾报 3 处 `变量引用无效。“:”后没有有效的变量名称字符`（`"$s: …"` 被解析成
> `$s:` 作用域限定）—— 已改为 `"${s}: …"`，复检 0 错误。

### 7.2 **真机实跑**过的清单（含退出码）— 2026-09-29 复验

方法：`Start-Process -RedirectStandardOutput/-RedirectStandardError` 逐条跑、落盘再读
（刻意**不用** `2>` 原生重定向：它会把子进程输出二次解码，中文变乱码）。
下表是**修复后**的复验结果；修复前的失败读数见 §7.3。

| # | 调用 | 退出码 | 读数 |
|---|---|---|---|
| 01 | `ft_build.ps1`（**完整路径**，含真实 `gradlew :fabric-1.20.1:build`） | 0 | `[尝试 1/2]` → `构建成功，耗时 0.6s`（up-to-date）→ 开包核对全绿 |
| 02 | `ft_build.ps1 --jar <产物>` | 0 | `models jar=138 src=137 ok=true`、`recipes jar=132 src=118 ok=true`、`bountiful jar=0 ok=true`、`trinkets jar=15 src=8 ok=true`、`EMBED_LIB present=true`（jar=1 773 251 B / 1581 entries） |
| 03 | `ft_env.ps1 --side server --mode verify` | 0 | 8 项前置全 `present=true source=loom` |
| 04 | `ft_env.ps1 --side server --enable-rcon` | 0 | `enabled=true port=25575 password_set=true changed=true`（写进 `server.properties`，**重启**生效） |
| 05 | `ft_env.ps1 --side server --install-probe` | 0 | `AP_FAB_ENV_PROBE: installed=…\event_bridge_probe.js`；冷启动后该探针确有执行 |
| 06 | `ft_launch.ps1 --side server` | 0 | `AP_FAB_LAUNCH_READY: pattern=Done \(\d+(\.\d+)?s\)! For help cursor=22827`（**修复前此项恒为 12/TIMEOUT**，见 §7.3/D1） |
| 07 | `ft_launch.ps1 --side client --gradle-arg -PtestSodium=false` | 0 | `AP_FAB_LAUNCH_READY: pattern=Sound engine started cursor=23798` |
| 08 | `ft_assert.ps1 log --pattern 'Done \(\d' --side server --window whole`（**服务端活着时**） | 0 | `hits=1` ⇒ 在线读日志可用（修复前这条必然抛 IOException，见 §7.3/D2） |
| 09 | `ft_assert.ps1 log --pattern 'Sound engine started' --side client --window whole`（在线） | 0 | `hits=1` |
| 10 | `ft_assert.ps1 log --pattern '事件订阅审计' --side client --window whole`（在线） | 0 | `hits=2`（服务端 50 类 + 客户端 61 类各一条） |
| 11 | `ft_dispatchreport.ps1 --side server` | 0 | `fired=10 idle=26 ServerTickEvent=599`，26 个 0 次事件逐条列出 |
| 12 | `ft_assert.ps1 snapshot --side server --window case` + 前向断言 | 0 / **1** | `cursor=19707`；窗口内找不到时 `ok=false` 且 rc=**1** ✔（FAIL 语义正确） |
| 13 | `ft_inject.ps1 cmd --command /list --channel kubejs`（**已废除的通道**） | **2** | `reason=unsupported-by-platform` + 完整实测理由 ✔ 显式失败，不静默 |
| 14 | `ft_inject.ps1 cmd --command /list --side client` | **2** | `reason=client-unsupported` ✔ |
| 15 | `ft_inject.ps1 cmd --command "/say ft-inject-ok" --channel rcon`（**无服务端**） | 2 | `reason=connect-or-auth` + 检查指引（真实错误路径） |
| 16 | `ft_inject.ps1 cmd --command "/say ft-inject-ok" --channel rcon`（**有服务端**） | 0 | `AP_FAB_INJECT: OK`、`AP_FAB_INJECT_RESP: [1] (empty)`；服务端日志出现 `[Not Secure] [Rcon] ft-inject-ok` |
| 17 | `ft_case.ps1 list` | 0 | 5 条用例 |
| 18 | `ft_case.ps1 validate --all` | 0 | `total=5 bad=0` |
| 19 | `ft_case.ps1 run --case FAB-BOOT-EMBED.json` | 0 | **9/9** 断言通过（原 10 条里的 `事件派发统计` 已移出 —— 见 §7.3/D5） |
| 20 | `ft_case.ps1 run --case FAB-DISPATCH-BASIC.json` | 0 | **8/8** 通过（`ServerTickEvent=599`）；先经 `wait_for` 等到采样点 |
| 21 | `ft_case.ps1 run --case FAB-JAR-ASSETS.json` | 0 | **1/1** 通过 |
| 22 | `ft_case.ps1 run --case FAB-CLIENT-BOOT.json`（**本轮新增**） | 0 | **8/8** 通过 |
| 23 | `ft_case.ps1 run --case FAB-INJECT-ROUNDTRIP.json`（改 rcon，**有服务端**） | 0 | **3/3** 通过 |
| 24 | `ft_case.ps1 run --case FAB-INJECT-ROUNDTRIP.json`（**无服务端**） | 2 | 注入步骤**显式 ERROR 中止**（`reason=inject-failed step=2`）✔ 不静默降级 |
| 25 | `wait_for` 超时路径（等待条件在预算内不满足） | **12** | `reason=wait-for-timeout` + `MT_FAB_CASE: ERROR` ⇒ TIMEOUT 与 FAIL 明确区分 ✔ |
| 26 | `ft_stop.ps1 --side server`（**真在跑**的实例） | 0 | `KILL target=launcher pid=… tree=true`、`count=1`；随后 java 进程消失、`latest.log` 文件锁释放 |
| 27 | `ft_stop.ps1 --side client`（真在跑的实例） | 0 | 同上（`latest.log` 可读写且未残留） |
| 28 | `ft.ps1`（无 `--phase`） | **2** | `MT_AUTO_DISABLED: 全流程…` ✔ |
| 29 | `ft.ps1 --phase case`（无 `--case`） | **2** | `MT_AUTO_DISABLED: --phase case 未指定 --case…` ✔ |
| 30 | `ft.ps1 --phase report` | **2** | `MT_AUTO_DISABLED: --phase report…` ✔ |
| 31 | `ft_case.ps1 run-dir` | **2** | `MT_AUTO_DISABLED: run-dir…` ✔ |
| 32 | `ft_launch.ps1 --side both` | **2** | `MT_AUTO_DISABLED: --side both…` ✔ |
| 33 | `ft.ps1 --phase case --case FAB-JAR-ASSETS.json`（阶段透传） | 0 | `AP_FAB_PHASE: case rc=0` ✔ |
| 34 | `ft_assert.ps1 log --pattern ZZZ_NOT_EXIST_ZZZ`（**期望 FAIL**） | 1 | `MT_FAB_ASSERT: FAIL — hits=0` ✔ |
| 35 | `ft_env.ps1 --help` / `ft_launch.ps1 --help` | 0 | 用法（`ft_launch` 含 `--gradle-arg`） |

### 7.2.1 冒烟批次实测（2026-09-29 18:40–18:44，**用户显式要求**，全清单）

按 `TESTING-SPEC.md` §1.1：**用户显式要求**是全清单的唯一自动放行条件。本次按本线跑完整流程，
分三批执行（每批 ≤1 分钟，避免长流程被掐断；每步走独立子进程并记录 rc 与耗时）。
驱动脚本 = `temp/smoke.ps1`（批 A/B）与 `temp/smoke_c.ps1`（批 C）；`temp/` 被 .gitignore 忽略，属本地证据。

| 批 | 步 | rc | 耗时 | 关键读数 |
|---|---|---|---|---|
| A | B 构建 + 开包核对 | 0 | 1.7s | `models jar=138 src=137 ok=true`、`recipes jar=132 src=118 ok=true`、`EMBED_LIB present=true` |
| A | E 环境（`--side both`，只读） | 0 | 0.6s | 前置齐备 |
| A | E 环境（`--enable-rcon`） | 0 | 0.6s | `enabled=true port=25575 password_set=true` |
| A | L 启动服务端（**经编排入口** `ft.ps1 --phase launch`） | 0 | **11.5s** | `AP_FAB_LAUNCH_READY … cursor=18445` ⇒ **D6 的回归证据** |
| A | 在线断言（**服务端存活期间**） | 0 | 0.6s | `hits=1` ⇒ **D2 的回归证据** |
| A | `FAB-JAR-ASSETS` | 0 | 0.7s | 1/1 PASS |
| A | `FAB-BOOT-EMBED` | 0 | 0.7s | 9/9 PASS |
| A | `FAB-DISPATCH-BASIC` | 0 | **27.8s** | 8/8 PASS（其中 ~27s 是 `wait_for` 在等 600-tick 采样点，属预期） |
| A | `FAB-INJECT-ROUNDTRIP` | 0 | 2.9s | 3/3 PASS（rcon + `/say` 锚点） |
| A | 在线派发统计 | 0 | 0.6s | `ServerTickEvent=599 fired=10 idle=26` |
| A | 收停服务端 | 0 | 2.5s | `已收停 1 个进程，无残留` |
| B | L 启动客户端（经编排入口 + `--gradle-arg -PtestSodium=false`） | 0 | **12.4s** | `Sound engine started cursor=23794` |
| B | `FAB-CLIENT-BOOT` | 0 | 0.8s | 8/8 PASS |
| B | 收停客户端 | 0 | 2.5s | 无残留 |
| C | lang 三语键一致性（本线 lang 目录） | 0 | — | `zh_cn/en_us/ja_jp 各 829 key 完全一致`（1 条**既有** WARN：`nancy_lu_active` 结构标记，三线一致、非本次引入） |
| C | 工具链语法门（`scripts/` + `tools/` 全量） | 0 | — | `53 个文件，解析失败 0` |
| C | 模组来源统一口径 | 0 | — | `MOD_SOURCE_GATE: OK violations=0 exceptions=0` |
| C | 本线资源 / 注册闭环（7 项） | 0 | — | `=== PASS: 7 项闭环全部通过 ===` |

**合计：5/5 用例 PASS、4/4 静态闸门绿、构建与开包核对绿、无残留进程。**

> ⚠️ **D6/D7 就是这一批跑出来的**（不是读代码看出来的）：批 A 第一次执行时卡在 launch 步 6 分钟不返回
> —— 顺着「子脚本早已打印 OK」这条线索才挖到 `Invoke-FtChildProcess` 的 `-Wait` 与回读共享模式问题。
> 这正是「主干没真机跑过就别写已实测」的又一次证明。

未纳入本批的静态闸门（**按「与改动无直接关系的模块一律不在本批运行」的口径**）：
`scripts/audit/tooltip_color_audit.ps1`、`scripts/verify/verify_chip_*.ps1`、`verify_bountiful_*.ps1`、
`verify_forge_loader_gate.ps1` —— 它们的作用域**只覆盖两条生产线**（实测：`$SUBPROJECTS = @('neoforge-1.21.1','forge-1.20.1')`），
与本线无关；`verify_content_library.ps1` 已被 §9 裁出，且其对照件 `docs/1.2.0-content.json` 在本仓不存在。

### 7.2.2 生产映射冒烟（2026-09-29 19:05–19:14，**本台新增能力**）

**为什么必须有它**（实测事故 **KI-F13**）：Fabric 的 **dev 与生产是两套映射** —— 本台此前**全部**手段
（`ft_launch` 的 dev 客户端、5 条用例、4 个静态闸门）**都跑在 named 映射下**。于是「按**字符串名**反射原版成员」
这类代码（如 `Rarity.class.getDeclaredField("color")`）**在 dev 全绿、在整合包里 100% 崩**，且**谁也测不出来**：

| | 类 | `color` | `$VALUES` |
|---|---|---|---|
| dev（Loom named / Mojang） | `net.minecraft.world.item.Rarity` | `color` ✅ | `$VALUES` ✅ |
| 生产（intermediary） | `net.minecraft.class_1814` | `field_8908` ❌ | `field_8905` ❌ |

⇒ 唯一可靠判据 = **把要发布的那份 jar 在真实生产环境启动一次**。

**工具**：`ft_prod.ps1`（自包含，不 `Import` `Ft.Common`）。它 ① 从实例版本 JSON 构造生产 classpath
（rules 按 OS 过滤 + 缺 `artifact.path` 时按 Maven 坐标推导）；② 自备 log4j 配置（复现原版 `client-1.12.xml`
的 File+Console 双 appender）；③ 启动后轮询三类判据 —— **崩溃报告新增 → FAIL(1)** / **入口点失败 → FAIL(1)** /
**`Sound engine started` → PASS(0)**；超时 12、前置不足 2。

```powershell
# 先干跑（只构造 classpath，核对前置文件；不启动游戏）
pwsh -NoProfile -File scripts/test/fabric/ft_prod.ps1 -DryRun `
  -Instance 'D:\.minecraft\versions\1.20.1-Fabric 模组测试' `
  -McRoot 'D:\.minecraft' -Java 'C:\Program Files\Zulu\zulu-21\bin\java.exe'

# 真跑（默认到主菜单后收停；要人工进游戏取证时加 -KeepAlive）
pwsh -NoProfile -File scripts/test/fabric/ft_prod.ps1 `
  -Instance 'D:\.minecraft\versions\1.20.1-Fabric 模组测试' `
  -McRoot 'D:\.minecraft' -Java 'C:\Program Files\Zulu\zulu-21\bin\java.exe' -TimeoutSec 300
```

| # | 命令 | rc | 读数 |
|---|---|---|---|
| 26 | `ft_prod.ps1 -DryRun` | 0 | `AP_FAB_PROD_CP: libraries=96 inCP=73 missing=0 natives=1` |
| 27 | 修复**前**（19:07，当时用等价临时探针） | **1** | `VERDICT = CRASH_REPORT` → 新增 `crash-…19.07.04-client.txt`（`NoSuchFieldException: color`） |
| 28 | 修复**后**（19:11，正式工具） | **0** | `AP_FAB_PROD_READY: elapsed=23s`；日志 `Sound engine started`；`crash-reports/` **无新增** |

**同一对 jar，只差库版本**：
```
修复前：astral_dice 1.3.2+fabric_1.20.1         \-- starengine_lib 1.0.7          → 崩
修复后：astral_dice 1.3.2-alpha.1+fabric_1.20.1 \-- starengine_lib 1.0.5-alpha.1  → PASS（23 s 到主菜单）
```

⚠️ **它测什么、不测什么**：只判「**能不能起来**」（入口点 + 到主菜单），**不管玩法**（那仍归 dev 侧用例）。
但**凡映射相关的缺陷都在它的射程内** —— 反射原版成员、内嵌件重映射错、依赖解析失败、缺失前置，
而这些恰恰是 dev 侧**永远测不到**的一类。本台原有的"内嵌件产线可加载"缺口（旧 §8 项）由此闭合。
#### 7.2.2.1 类预加载：把「类加载期才暴露的 mixin 缺陷」提前到启动期（2026-09-29 新增）

⚠️ **只判「到主菜单」还不够** —— 有一类缺陷**只在某个目标类被加载时才暴露**，而那个类常常
「要玩家交互才加载」。实测事故 **KI-F14**：本模组的 `TooltipRenderUtilColorMixin` 与 Architectury
抢同一个 `@ModifyConstant` 常量，目标类 `TooltipRenderUtil` 只在**首次渲染 tooltip** 时才加载
⇒ 能过主菜单、**进世界 / 悬停 GUI 时才崩**，纯启动期冒烟完全看不见。

**做法**：客户端加一个**类预加载钩子**，由本模组在客户端初始化末尾主动 `Class.forName` 加载目标类：

```powershell
# 生产侧（ft_prod；逗号分隔可传多个）
pwsh -NoProfile -File scripts/test/fabric/ft_prod.ps1 `
  -Instance 'D:\.minecraft\versions\1.20.1-Fabric 模组测试' -McRoot 'D:\.minecraft' `
  -Java 'C:\Program Files\Zulu\zulu-21\bin\java.exe' `
  -PreloadClasses 'net.minecraft.class_8002'

# 想看注入结果（导出 mixin 应用后的字节码到 <实例>/.mixin.out/）
#   -ExtraJvmArg '-Dmixin.debug.export=true'

# dev 侧（Loom 对称支持）
./gradlew :fabric-1.20.1:runClient "-PpreloadClasses=net.minecraft.class_8002"
```

| 要点 | 说明 |
|---|---|
| 类名写法 | 一律写 **intermediary 名**（如 `net.minecraft.class_8002`）。模组侧用 `MappingResolver.mapClassName("intermediary", …)` 转成当前环境真名 ⇒ **同一个参数 dev 与生产都能用**（生产恒等、dev 转成 named） |
| 失败行为 | **fail-loud**：记 `[preload] FAIL …` 后**重新抛出**（不吞异常）⇒ 崩溃报告与日志双通道可判；吞掉就成了假绿 |
| 判据 | 日志 `[preload] OK`（证明该类**真的被加载了**，否则"不崩"是假绿）+ `InjectionError` 判据 + `crash-reports/` 无新增 |
| 不设属性时 | 直接 return，**零副作用**（生产玩家路径完全不受影响） |

**实测（KI-F14，修复后）**：`-PreloadClasses net.minecraft.class_8002` ⇒ `[preload] OK   net.minecraft.class_8002 -> net.minecraft.class_8002`
+ `Sound engine started` + `crash-reports/` 无新增；再用 `-Dmixin.debug.export=true` 导出注入后的字节码，
直接看到 **4 个 wrapper 覆盖 6 个调用点，且 architectury 的 `@ModifyConstant` 注入同时存在**（19 处引用）
—— 这就是「包裹调用指令」与「改 ldc 常量」可共存的字节码级证据。
#### 7.2.3 饰品前置四态矩阵：把「声明层面表达不了的事」变成可跑的四态（2026-09-29 新增）

本线的饰品栏前置是 **「Trinkets 或 Accessories 二选一」**（KI-F15）。⚠️ Fabric 的 `depends` 是
**AND 语义、表达不了 OR** ⇒ 两个都只进 `recommends`，权威判定在运行时
（`ModCompatibilityCheck#verifyAccessoryProviderOrThrow()`，`onInitialize()` 第一条语句）。

⇒ **四种安装组合都要能跑到「就绪」或「明确拒绝」**，dev 侧用两个开关构造（两者都在 `build.gradle`）：

```powershell
# ① 仅 Accessories（= 生产整合包现状）
pwsh -NoProfile -File scripts/test/fabric/ft_launch.ps1 --side client --timeout 300 `
  --gradle-arg -PtestSodium=false --gradle-arg -PtestTrinkets=false

# ② 仅 Trinkets
pwsh -NoProfile -File scripts/test/fabric/ft_launch.ps1 --side client --timeout 300 `
  --gradle-arg -PtestSodium=false --gradle-arg -PtestAccessories=false

# ③ 两者同装（默认，不传开关）

# ④ 两者皆无 ⇒ 期望**拒绝启动**并给出中文说明（无需等满超时）
pwsh -NoProfile -File scripts/test/fabric/ft_launch.ps1 --side client --timeout 150 `
  --gradle-arg -PtestSodium=false --gradle-arg -PtestTrinkets=false --gradle-arg -PtestAccessories=false
```

**判据**（每次启动都会打，可直接 grep）：

| 机器行 / 读数 | 含义 |
|---|---|
| `AP_FAB_ACCESSORY_PROVIDER: OK (trinkets=… accessories=…)` | 守卫放行；括号里是**实际判定结果** |
| `AP_FAB_ACCESSORY_PROVIDER: FAIL (…)` | 两者皆无 ⇒ 紧随其后抛 `ModLoadingException`。⚠️ **拒绝文案不在 `latest.log` 里**（日志在抛出处就断了），而在**崩溃报告** `run/client/crash-reports/crash-*.txt`：`Description: Initializing game` → `Caused by: …ModLoadingException: [星之骰戏] 缺少饰品栏前置：既没有检测到 Trinkets，也没有检测到 Accessories。` + 后面一整段中文操作指引 |
| `[Astral Dice] 已为 N 件饰品物品注册 Trinkets 适配器` | 仅当 Trinkets 在场才出现 |
| `[Astral Dice] 已为 N 件饰品物品注册 Accessories 适配器` | 仅当 Accessories 在场才出现 |
| `NoClassDefFoundError`（`dev/emi/trinkets` 或 `io/wispforest/accessories`） | **反面判据**：守卫漏了才会出现 ⇒ 一旦出现即为缺陷 |

**四态实测读数（2026-09-29 20:08–20:12）**：

| 态 | 加载模组数 | 就绪 | `AP_FAB_ACCESSORY_PROVIDER` | Trinkets 适配器 | Accessories 适配器 | rc |
|---|---|---|---|---|---|---|
| 仅 Accessories | 82 | ✅ `Sound engine started` | `OK (trinkets=false accessories=true)` | — | ✅ | 0 |
| 仅 Trinkets | 81 | ✅ `Sound engine started` | `OK (trinkets=true accessories=false)` | ✅ | — | 0 |
| 两者同装 | 83 | ✅ `Sound engine started` | `OK (trinkets=true accessories=true)` | ✅ | ✅ | 0 |
| **两者皆无** | 80 | ⛔ 拒绝启动 | `FAIL (trinkets=false accessories=false)` | — | — | 非 0 |

⚠️ 四态里 **`NoClassDefFoundError` 均为 False**（含「仅 Accessories」）—— 这正是守卫到位的直接证据。

⚠️ **一条踩过的坑（KI-F16）**：`-PtestAccessories=false` 这个开关**曾经从来不能通过编译** ——
Loom 的**依赖方接口注入**会随「依赖是否进入**运行期**」而开关（Accessories 的
`custom.loom:injected_interfaces` 把 `AbstractButtonExtension` 注入 `AbstractButton`；
移出运行期后注入消失，源码里那个 `@Override` 就变成「不覆盖任何方法」）。
修法是去掉该 `@Override`（两种注入态都成立）。
**教训：凡在注释/文档里承诺「用某参数可以跑某态」，就必须实跑一次该参数** ——
否则它只是一个会把人引向错误结论的假入口。

#### 7.2.4 跨会话 A/B：把「重开游戏才出现」的缺陷变成可复现（2026-09-29 新增，为排 KI-F17 / KI-F18）

**为什么需要**：`ft_launch` 起的客户端，Loom 默认给一个**随机**玩家名（`Player353` / `Player705`…），
而**离线模式下玩家身份（UUID）由名字派生** ⇒ **每次运行都是一个全新玩家、玩家数据互不相通**。
于是「跨会话」缺陷（重复发放、冷却残留、持久化标记读不出来）在 dev 里**从原理上就复现不了** ——
**这就是 KI-F17（手册每次登录补发一本）能潜伏到整合包才被发现的结构性原因**。

**做法**：`build.gradle` 新增 `-PdevUsername=<名字>`（转发为 `--username`），两轮用**同一个名字** ⇒ 同一玩家反复登录。

```powershell
# 第一轮：首登（应发一本手册）
pwsh -NoProfile -File scripts/test/fabric/ft_launch.ps1 --side client --timeout 400 `
  --gradle-arg -PtestSodium=false --gradle-arg -PtestTrinkets=false --gradle-arg -PdevUsername=AstralDev
# 收停后再起第二轮：重登（必须**不补发**）
pwsh -NoProfile -File scripts/test/fabric/ft_launch.ps1 --side client --timeout 400 `
  --gradle-arg -PtestSodium=false --gradle-arg -PtestTrinkets=false --gradle-arg -PdevUsername=AstralDev
```

⚠️ 单机下还存在 `level.dat` 的 `Player` 标签这条通路（`PlayerList#load` 对单人房主**优先读它**）
⇒「固定玩家名」只保证**身份稳定**，不等于数据一定从 `playerdata/<uuid>.dat` 读。

**本轮读数（KI-F17 / KI-F18 双验，2026-09-29 20:55 / 21:01）**：

| 轮次 | 机器行 | 判据 |
|---|---|---|
| session1 首登 | `AP_FAB_ATTACHMENTS: 附件键已注册 109 个`（20:55:38，**早于**登录 20:55:42） | 注册必须**早于**玩家数据反序列化 |
| session1 首登 | `AP_FAB_GUIDEBOOK: … given=false … books=0` → `GIVEN … reread=true books=1` | `reread=true` ⇒ 109 键**注册成功且没丢键** |
| session2 重登 | `AP_FAB_ATTACHMENTS: … 109 个`（21:01:37）→ 登录（21:01:41）→ `given=true … books=1` | **不补发**、仍只有 1 本 |
| session2 重登 | `AP_FAB_SLOT_ICON: 槽位图标自检 槽位=15 文件缺失=0 图集未收录=0` + 逐槽位明细 | 贴图修复生效（本模组 3 槽位 `file=yes inBlocksAtlas=yes`） |
| 两轮全文 | `Unknown attachment type` 命中 = **0**（`latest.log` + `debug.log`） | 丢键缺陷彻底消除 |

**三条新增诊断机器行**（常驻，不带任何开关）：

| 机器行 | 含义 |
|---|---|
| `AP_FAB_ATTACHMENTS: 附件键已注册 N 个(必须先于任何玩家数据反序列化)` | 附件键注册数；**出现得比登录行早**才算对 |
| `AP_FAB_GUIDEBOOK: uuid=… call=… given=… patchouli=… enabled=… books=…`<br>`GIVEN uuid=… reread=… books=…` | 发放判定 + **写后回读**；`books=` 是背包里手册的**实际数量** |
| `AP_FAB_SLOT_ICON: 槽位图标自检 槽位=N 文件缺失=N 图集未收录=N`<br>`AP_FAB_SLOT_ICON_DETAIL:`（逐槽位明细） | 图标**双层**检查（文件存在 **且** 被图集收录） |

⚠️ **「写后立刻回读」是本轮的关键手法**：`setGuideBookGiven(player, true)` 之后马上 `isGuideBookGiven(player)` ——
若这里读到 `false`，说明是「值取不出来」（**存取通路**问题）而**不是**「发放时机不对」。
KI-F17 正是靠这一条读数把方向扭过来的（否则很容易继续在「发放守卫写在哪」上打转）。

⚠️ **读日志的 gitignore 陷阱**：`run/` 被 `.gitignore` 排除 ⇒ `Grep` 之类**遵守 gitignore 的搜索工具
会静默跳过它**（返回 “No matches” 是**假阴性**，不是「真的没有」）。查 `run/client/logs/**` 必须用不遵守
gitignore 的方式或显式指定文件路径，否则「`Unknown attachment type` 命中 0」这种结论根本不可信。

### 7.2.5 第三方接入后端的「注入第三方模组」验证法（2026-10-01，`AP_FAB_PARTY` / `FAB-PARTY-BACKENDS`）

**要验证的问题**：`PartyRelations` 用反射接 FTB Teams / OPAC（不能编译期依赖）。这类「后端」在**没装**第三方模组时
必然走「未安装」分支 ⇒ **只跑常规冒烟永远测不到真正生效的那条路径**（本线三个整合包都没装 FTB、也都没装 OPAC，
所以那段代码从来没人验过 —— 它实际上**一直是坏的**，见 KI-F22）。

**做法 = 把第三方 jar 丢进 `run/<side>/mods/` 再跑，用机器行做 A/B/C/D 对照**：

| 轮次 | 放入 `run/client/mods/` 的东西 | 预期 `AP_FAB_PARTY` |
|---|---|---|
| A | （无，基线） | `back_ftb=off back_opac=off why_*=ClassNotFoundException:…` |
| B | `ftb-teams-fabric-2001.3.2` + `ftb-library-fabric-2001.2.0` + `architectury-9.1.13-fabric` | `back_ftb=on why_ftb=OK` |
| C | B + `open-parties-and-claims-fabric-1.20.1-0.31.6` | `back_ftb=on back_opac=on why_*=OK` |
| D | 同 C，但 `run/server/mods/` + `--side server`（**队友判定的实际执行侧**） | 同上，且启动干净、零告警 |

⚠️ **跑完必须把 jar 从 `run/*/mods/` 移除**（还原 dev 环境）；`run/` 本就被 gitignore，不会被误提交。
jar 与原始读数留档在 `temp/party_verify/`（含修复前源码快照 `PartyRelations.prefix.java`）。

**配套的静态闸门（可与运行时独立复跑）**：`tools/verify_party_api.py`
—— 自动从 `PartyRelations.java` 抽取反射契约（`Class.forName` 绑定 + `getMethod` 签名），逐个与**真实 jar** 比对。
```bash
python tools/verify_party_api.py            # 校验当前源码        → 期望 PASS=17 FAIL=0
python tools/verify_party_api.py --pre-fix  # 校验修复前快照      → 期望 PASS=9  FAIL=5（红灯）
```
实现上有两个**坑**（都已在脚本里注释）：① 两个内部类 `Ftb` / `Opac` **共用** `apiCls` / `API_CLASS` 等变量名
⇒ 必须**按内部类分段解析**，否则后者的绑定会覆盖前者、把 FTB 的方法算到 OPAC 类名下；
② 不同发布方 remap 口径不同（FTB 产物是 **intermediary** 类名 `net.minecraft.class_1657`，OPAC 产物是 **Mojmap**），
⇒ 参数类型比对要**两种写法都接受**，映射取自 Loom 的 `mappings.tiny`（权威）。

### 7.2.6 饰品后端（Trinkets / Accessories）四态实机验证（2026-10-01，`AP_FAB_ACCESSORY_PROVIDER` / `AP_<tag>_SRC`）

**要验证的问题**：`compat/curios/CuriosApi` 门面把 Accessories（主源）与 Trinkets（兜底）聚合成同一视图，
消费方 94 处 `CuriosApi.*` 调用一行不改。但**两个后端在「槽位组尺寸」上的机制是否真的等价**从未进游戏验证过 ——
尤其是「筹码栏随骰子星级增减」依赖的
`addPersistentModifier(Operation.ADDITION) → update() → 容器尺寸变化` 这条链路。

**驱动方式**：专用 dev 服务端（`gradlew :fabric-1.20.1:runServer`）+ **RCON** + **Carpet 假玩家**。
本台此前的缺口是「客户端 GUI 键鼠注入未覆盖」（§8.1），本轮绕开它的办法是：
`/player Bot spawn` 造一个**真实 ServerPlayer**（`PlayerList#placeNewPlayer` 入表，实测于 jar 字节码），
再用 `/astralfab` 探针（KubeJS）对它做装备/读数。装备走**产品入口** `CurioSlotUtil#tryAutoEquip`
（等价于真人下蹲右键自动装备，仅少一层按键判定）。

**四态**（`build.gradle` 既有开关）+ 逐态读数：

| 轮次 | 启动参数 | `AP_FAB_ACCESSORY_PROVIDER` | `AP_<tag>_SRC` | 合并视图 `groups` |
|---|---|---|---|---|
| A 双装 | （默认） | `OK (trinkets=true accessories=true)` | `trinkets_on=1 accessories_on=1 trinkets_groups=3 accessories_used_slots=3 accessories_groups=15` | Accessories 15 组（含 chip/dice/stand 与 Accessories 默认槽） |
| B 仅 Accessories | `-PtestTrinkets=false` | `OK (trinkets=false accessories=true)` | `trinkets_on=0 … trinkets_groups=off accessories_groups=15` | 同 A |
| C 仅 Trinkets | `-PtestAccessories=false` | `OK (trinkets=true accessories=false)` | `trinkets_on=1 … trinkets_groups=3 accessories_*=off` | **仅 `chip=0,dice=1,stand=1`** |
| D 皆无 | `-PtestTrinkets=false -PtestAccessories=false` | `FAIL (trinkets=false accessories=false)` | — | —（**按设计拒绝启动**） |

⚠️ `_SRC` 子命令是专门为 A 轮加的：**双装态下聚合视图把同名槽位并集了，门面读数分辨不出来源**
（并集后 `chip` 只有一个）。它绕过门面直接问两条通道各自的原始 API
（`TrinketsCompat.getCuriosMap` / `AccessoriesAPI.getUsedSlotsFor`）。
⚠️ 它必须**先过 `ModCompatibilityCheck.isTrinketsPresent()/isAccessoriesPresent()` 再触碰对应类** ——
实测：Accessories 缺席时调 `AccessoriesAPI.*` 会抛 `java.lang.NoClassDefFoundError: io/wispforest/accessories/api/Accessory`，
而 **`java.lang.Error` 不会被探针的 `catch` 接住** ⇒ 整条命令硬中止、一行读数都不出（首版踩中，已修）。

**A/B/C 三轮的行为读数逐条一致**（同一份命令序列 `temp/gate/cmds_p1.txt`，24 条）：

| 步骤 | 期望 | A 双装 | B 仅 Acc | C 仅 Trk |
|---|---|---|---|---|
| 装 `nether_star_dice`(0★=4 格) | chip 栏长到 4 | `slots=4`、`mods=[a5d1c9e2-…=4*]` ✔ | 同 ✔ | 同 ✔ |
| 塞 4 枚筹码 | `cap=4 filled=4` | ✔ | ✔ | ✔ |
| 取走骰子 | **防御式：槽位与筹码都不缩** | `slots=4`、4 枚仍在 ✔ | ✔ | ✔ |
| 此时闸门 | `hasDice=0` | ✔ | ✔ | ✔ |
| 换装 `golden_dice`(0★=1 格) | **强制收缩到 1，3 枚退回物品栏** | 下一拍 `slots=1`、`INV total=3 chips=3` ✔ | ✔ | ✔ |
| 登出→登录（未佩戴骰子） | **槽位归零 + 全部筹码退回物品栏** | `slots=0`、`INV total=4 chips=4` ✔ | ✔ | ✔ |

**两条独立结论**：
1. **`addPermanentModifier(ADDITION) + update()` 在 Accessories 上确实改变 `getSize()`** —— 这是本轮最核心的假设，
   静态分析时一度因为 `AccessoriesContainerImpl#update()` 里有个 `allowResizing()` 闸门而疑似会失败；
   实测 `slots=0 → 4` 且 `mods` 里出现本模组固定 UUID 的绝对修饰符 ⇒ 闸门默认放行（`ExtraSlotTypeProperties.DEFAULT.allowResizing = true`）。
2. **`onEquip` 在 Accessories 上的生效比 Curios 晚一拍**：`equip` 命令的**同一拍**读数是旧尺寸，
   下一个动作（间隔 ≈1.6 s）读到新尺寸。Trinkets 侧同样如此。⇒ 换装后的强制收缩**不是同 tick 完成**的，
   任何「装备后立刻断言」的用例都会假红 —— 读数必须留一拍。

**四态结论**：A ≡ B ≡ C（后端行为等价、聚合视图在两个单源态下也自洽）；D 按设计**拒绝启动**并给出完整中文说明
（两条安装路径 + 双装说明 + 重启提示），`ModLoadingException` 落在 `ModCompatibilityCheck#verifyAccessoryProviderOrThrow`。
⚠️ D 态的原文**只在** `temp/gate/server_none.out.txt`（`temp/` 不入库，且 `latest.log`
**每轮冷启动都会被轮转** ⇒ 复跑该断言时别去 `latest.log` 里找，会假红）。关键原文：
```
[21:58:01] [main/INFO] (AstralDice) AP_FAB_ACCESSORY_PROVIDER: FAIL (trinkets=false accessories=false)
java.lang.RuntimeException: Could not execute entrypoint stage 'main' due to errors, provided by 'astral_dice'
Caused by: com.merlinkitsune.astral_dice.platform.fml.ModLoadingException: [星之骰戏] 缺少饰品栏前置：既没有检测到 Trinkets，也没有检测到 Accessories。
  … 【一】Trinkets …【二】Accessories … 两者同时安装也可以 … 装好其中一个之后重新启动游戏即可。
  at …ModCompatibilityCheck.verifyAccessoryProviderOrThrow(ModCompatibilityCheck.java:139)
```
（同轮 `grep -c` 统计 `Done (` 命中 = 0 ⇒ 确实没起到完成态。）

⚠️ **本轮的 dev 环境前置**：Loom 1.14.10 重映射第三方 mod 时会剥离 `fabric.mod.json` 的 `jars` 声明
⇒ Puzzles Lib / Accessories / Patchouli / Cloth Config / KubeJS **全部起不来**。处置见 **KI-F23** 与
`tools/loom_embedded_jars.py`（`ft_env --install-embedded` 已接线）。**换后端态后必须重跑一次**。

#### 仍未覆盖（如实标注）

- **双装态下「两边都装、各装一件」的写入落点**未测：探针只覆盖「同槽位空 → 落主源」这一支；
  `MergedSlots.setStackInSlot` 的「写当前持有内容的那一侧」需要玩家用两套 GUI 各自装备一次，dev 单进程驱动不到。
- **立牌被动/主动技能在闸门下的行为**未在游戏内断言（本轮只断言 `hasDice` 这个判据本身）；
  `BaseSignItem#curioTick` / `performSkill` 等闸门点仍是源码级结论。
- **客户端渲染面**（饰品栏 GUI 布局、槽位图标）未覆盖，属本台既有缺口（§8.1）。
- **探针里的 `refresh` / `enforce` 两个子命令在本轮 24 条命令里一次都没下达**：四态读数依赖的是**隐式回调**
  （`onEquip` / `onUnequip` / `curioTick` 对账 / 登录钩子），不是这两条显式入口。它们确实存在且指向产品入口，
  但**本轮没有实机调用证据**。（“重进复位”走的是真实的 `/player Bot kill` + `/player Bot spawn`
  登出→登录路径，比显式调 `enforce` 更强。）
- **槽位读数无法单独区分“防御分支跑了”与“回调根本没触发”**：两条路径都产 `slots` 不变，
  且防御分支**不打日志**（只有 forceRemove 分支打 WARN）。本轮靠“forceRemove=false 会把 target 抬到
  最靠后非空槽 +1 ⇒ 满槽时缩不动”这条推理排除了“对账干的”；若将来要直接断言，
  需要给防御分支补一条机器行。

### 7.3 已修复的测试台缺陷（7 个；每个都附实测证据）

前两个是**阻断级**：只要有它们，`ft_launch` **每一次启动都必然失败**，连带 env/launch 之后的一切
（在线读数、命令注入、全部用例）统统不可用 —— 这就是「测试流程完全不可用」的根因。
**D6/D7 是冒烟跑出来的**（不是读代码看出来的）—— 见 §7.2.1。

#### D1 就绪游标被 log4j 的「启动轮转」击穿 ⇒ 每次启动必然假超时

- **现象（修复前实测）**：`ft_launch.ps1 --side server --timeout 240` → `rc=12`、
  `AP_FAB_LAUNCH: FAIL (reason=timeout timeout=240s)`；而同一时刻 `run/server/logs/latest.log` 里
  早已有 `[18:06:04] Done (0.333s)! For help, type "help"`（启动后 **9 秒**就绪）。
  超时后**进程仍在跑**（orphan），日志里连完整的 600-tick 派发报告都产出了。
- **根因**：Minecraft 的 log4j 配置带 `OnStartupTriggeringPolicy` —— **每一次冷启动**都把 `latest.log`
  改名成 `2026-09-29-7.log.gz`（实测时间戳 18:05:55 = 启动瞬间）再**新建**一个空 `latest.log`。
  旧实现拿「启动前记录的字节长度」当游标，且只在 `len > preLen` 时才读 ⇒ 要等新文件长过旧长度
  （约 20 KiB）条件才成立，而就绪行**早在文件前部**，因此**永远匹配不到**。
- **次生伤害（更隐蔽）**：新文件长过旧长度之后，那个偏移量落进**新文件中间**，
  `Get-FtLogWindow('case'|'launch')` 会**静默**返回一个错误窗口（漏启动行、混进无关行）——
  这正是「假 PASS / 假 FAIL」的来源。旧实现只在「长度回退」时报 WARN，覆盖不到这种情形。
- **修法**：把「字节位置」换成「**文件身份锚点**」= 创建时间(UTC ticks) + 头部 4 KiB 的 SHA1 + 长度
  （`Ft.Common.psm1#Get-FtLogAnchor` / `Get-FtLogWindowFromAnchor`）：身份变了 ⇒ 整个新文件就是
  「本次新增」；身份没变才做前向切片。另加**反向自检**：超时时若整份日志里已有就绪标记，
  报 `reason=ready-but-undetected`（与普通 timeout 区分）—— 把「工具缺陷」与「游戏没起来」彻底分开。

#### D2 游戏运行时 `latest.log` 被独占 ⇒「在线读日志」全线不可用

- **现象**：服务端运行期间读日志抛
  `The process cannot access the file '…latest.log' because it is being used by another process.`
- **根因**：`[System.IO.File]::ReadAllBytes` 内部的共享模式是 `FileShare.Read`，而 Windows 的共享
  检查是**对称**的 —— 只允许别人「读」，就无法与仍持有**写**句柄的游戏进程共存。
  ⚠️ 这个缺陷此前**一直被掩盖**：旧 `ft_launch` 的读分支因 D1 的判定条件恒不成立而**从未真正执行**，
  也就是说「起服务端 → 在线读数」这条主干（`ft_assert` / `ft_dispatchreport` / 轮询 / 回显校验）
  从来就没跑通过。
- **修法**：新增 `Read-FtFileBytesShared`（`FileShare.ReadWrite`），`Read-FtLogText` 与窗口切分全走它。

#### D3 KubeJS 命令队列通道从根上不可实现 ⇒ 已废除（附证据链）

- **现象**：观察脚本 `ft_cmd_watcher.js` 每 20 tick 抛一次
  `[ft-inject] ERR 读队列失败: TypeError: Cannot call method "get" of null`（`FT_Paths` 恒为 null），
  **从未成功执行过一条命令**。而宽模式 `\[ft-inject\]` 会把这一堆 ERROR 行当成"命中" —— 本轮正是靠
  `FAB-INJECT-ROUNDTRIP` 的精细断言把它揪出来（`ran rc=` 命中 0、`ERR` 命中 3）。
- **实证（全部可复算）**：
  1. KubeJS 的类过滤不放行 `java.nio.file.*` / `java.io.*`：`Java.loadClass('java.nio.file.Paths')`
     不产生 `Loaded Java class '…'` 行且返回 null；`java.lang.String` / `dev.latvian.mods.kubejs.*` 才会；
  2. 面向脚本的 bindings **没有**任何文件/路径包装器（`unzip -l` 实测只有
     Block / DamageSource / Ingredient / Item / Java / KMath / Text / Utils 八个 Wrapper）；
  3. Rhino 的 `java` / `Packages` 全局已移除 —— 实测报错
     `Line 57: 'java()' is no longer supported! Read more on wiki: https://kubejs.com/kjs6`。
  ⇒ 三条路全断，「往文件写队列让 KubeJS 读」在 KubeJS 6+ 上**无法实现**。
- **修法**：整体移除该通道与观察脚本；`--channel kubejs` 保留为**显式失败**
  （`reason=unsupported-by-platform` + 完整理由）—— 比「看起来能用、实际永远失败」安全得多。
  KubeJS 在本台的定位收敛为**「产出读数的探针」**（`ft_env --install-probe`），命令注入统一走 RCON。

#### D4 `ft_launch` 无法向 gradlew 透参 ⇒ 客户端在本机 dev 环境必然启动失败

- **现象**：旧 `ft_launch --side client` 只会起 `gradlew :fabric-1.20.1:runClient`，而本机 dev 环境里
  Sodium 要求 LWJGL 3.3.1、环境装的是 3.3.2-snapshot ⇒ 启动期硬失败，且没有任何绕过手段。
- **修法**：新增可重复的 `--gradle-arg <x>` 透传（同时写进状态文件与 `AP_FAB_LAUNCH_TASK` 读数行）。
  实跑：`--side client --gradle-arg -PtestSodium=false` → `AP_FAB_LAUNCH_READY … Sound engine started`，rc=0。
  该开关是工程 `build.gradle` **既有**的测试开关（注释写明用于「只为跑一次 GUI 验证」）；
  跑渲染 / tooltip 类验证时排除 Sodium 反而更干净（不被第三方渲染模组干扰）。

#### D5 用例里的**固定 `wait`** 把「等待条件未满足」伪装成「断言失败」⇒ 新增 `wait_for` 步骤

- **现象（本轮复验时真实踩到）**：`ft_launch --side server` 就绪后等 25 s 再跑用例，
  `FAB-BOOT-EMBED` **1/10** 失败、`FAB-DISPATCH-BASIC` **6/8** 失败，两条都是
  `事件派发统计` 相关断言报 `未找到派发报告` —— 现场读起来像**产品缺陷**（桥没派发？内嵌库坏了？），
  实际只是**采样点没到**（该报告在开局 600 tick ≈ 30 s 才打印）。用例自己的注释里就写着
  「⚠️ 若在 600 tick 之前断言，请改用 ft_dispatchreport 手动读数」—— 靠注释提醒纪律，不如靠机制。
- **修法（两层）**：
  1. 新增 `wait_for` 步骤（`pattern` + `timeout_ms` + `interval_ms` + 可选 `window`）：命中即继续，
     超时以 **ERROR(12) TIMEOUT** 中止并在信息里给出「去用 `ft_dispatchreport.ps1` 手动读数」的指引
     —— 与「断言失败 = 1」明确区分（语义边界同 `TESTING-RULES-OVERVIEW.md` §7）。
     `FAB-DISPATCH-BASIC` 改用它，不再依赖任何固定等待。
  2. **解耦**：把 `事件派发统计` 断言从 `FAB-BOOT-EMBED` 里**移出**（10 → 9 条）。它是「派发」这个
     独立关注点，归 `FAB-DISPATCH-BASIC` 管；留在启动期用例里就制造了一个隐藏的 30 s 时间耦合。
- **教训（通用）**：**用例里的固定 `sleep` 会把时序问题伪装成功能缺陷**。凡「等某个读数出现」的场合，
  都应换成「有界轮询 + 超时给出不同结论」；否则失败现场会把排查方向带偏（本轮如果直接信那 6 条 FAIL，
  就会去查事件桥，而真相是等太短）。

#### D6 `Invoke-FtChildProcess` 的 `-Wait` 会等**整棵进程树** ⇒ `ft.ps1 --phase launch` 挂到游戏退出

- **现象（冒烟时实测）**：批处理驱动在「启动服务端」这一步**卡住 6 分钟不返回**，而 `ft_launch.ps1`
  的输出文件里**早已**有 `AP_FAB_LAUNCH: OK`（就绪在 11 秒时已捕获）、服务端日志里连 600-tick
  派发报告都打出来了。
- **根因**：`Start-Process -Wait` 会等**整棵进程树**，而 `ft_launch.ps1` 是「孵化游戏后立刻返回」的语义
  —— 它把游戏进程 detached 地起出去、自己随即退出。用 `-Wait` 等它，等于一直等到**游戏退出**。
  ⚠️ 这个坑**项目自己的文档里就写着**（`TESTING-RULES-OVERVIEW.md` §13：「`-Wait` 会等整棵进程树」），
  但 `Invoke-FtChildProcess`（`ft.ps1` / `ft_case.ps1` 共用的子进程封装）仍然用了它 ⇒
  **`ft.ps1 --phase launch` 从设计上就不可能正常返回**。影响面精确为这一处：
  `ft_build` 用的是**按进程**的 `WaitForExit(timeout)`（正确）；`ft_stop` 的 `taskkill /T` 是**故意**杀树（正确）。
- **修法**：`-PassThru` + 只对**该进程本身** `WaitForExit()`（不递归后代）。
- **验证**：`ft.ps1 --phase launch --side server` **11.4 秒**返回、`rc=0`（修复前无限挂住）。

#### D7 子进程输出回读撞上**同一个共享模式陷阱**（D2 的镜像）

- **现象**：把 D6 的 `-Wait` 换成 `WaitForExit()` 之后，`ft.ps1 --phase launch` 立刻以 rc=1 失败：
  `调用"ReadAllBytes"… 发生异常：The process cannot access the file '…\temp\ft_child_*.out.txt'
  because it is being used by another process.`
- **根因**：`Invoke-FtChildProcess` 用 `[System.IO.File]::ReadAllBytes` 回读子进程的重定向输出，而该文件
  在子进程退出后仍被**本方（父进程）的写句柄**持有一小段时间 ⇒ 与 **D2 完全同一个陷阱**
  （`ReadAllBytes` 的共享模式是 `FileShare.Read`）。`-Wait` 顺带等了整棵树，恰好把这个竞态**掩盖**了；
  一改成语义正确的 `WaitForExit()`，它立刻显形。
- **修法**：① `WaitForExit()` 后先 `$proc.Dispose()` 释放句柄；② 回读改走 `Read-FtFileBytesShared`
  （与日志读取**同一条**共享读写实现）。教训：**同一类问题要收敛到同一套原语** —— D2 修好之后
  没顺手把「读文件」这件事收敛成一个入口，才有了 D7。


### 7.4 仍未实跑 / 仍未覆盖

| 项 | 为什么没跑 | 补验方式 |
|---|---|---|
| `ft_build.ps1` 的 `--timeout` / `--retries` **超时与重试分支** | 需要构造一次真实超时（分钟级 + 破坏构建） | 影响面小，按需验 |
| `ft_env.ps1 --mode install`（拷前置进 `mods`） | 会写运行目录，且与 gradle classpath 重复投放有风险（脚本已 WARN） | 仅在「直接 java 启动」路径下用 |
| `--fg` 前台启动模式 | 会占住当前控制台（分钟级） | `ft_launch --side server --fg` |
| 客户端**世界内**用例（渲染 / HUD / tooltip） | 需要 quickplay 进世界 + 存档准备；且 GUI 悬停无法可靠自动化 | `--gradle-arg -Pquickplay=<世界>` 后另写用例 |
| `ft.ps1 --phase report` 的**放行后路径** | 默认被批量编排闸门拦下（刻意） | 加 `--allow-auto` 才可达；本轮只验了拦截码 = 2 |

**没有一项结论是「猜」的**：路径 / 类名 / 方法名 / 日志前缀都来自 §9 的依据索引，或来自
`javap` / 开包 / **真机实跑读数**；§7.3 的每个根因都附了可复算的现场证据（时间戳、压缩包名、异常原文）。

---

## 8. 已知覆盖缺口与限制（如实标注）

1. **客户端 GUI 键鼠注入未覆盖**：生产线的 `mt_inject.ps1`（Win32 `PostMessage` + en-US 键盘布局 + 窗口线程
   输入法切换）未移植；本台的注入只面向**服务端命令**，且只有 RCON 一条通道。客户端的
   渲染 / 输入类缺陷本台**抓不到**（本轮实测也印证了这一点：`computer_control` 的按键到 MC 极不稳定）。
2. **客户端派发统计未断言**：`ft_dispatchreport.ps1 --side client` 已实现读取，但**拿不到数据** ——
   原因是平台事实而非本台缺陷：`事件派发统计` 的打印点是 FabricBridges 的「开局 600 tick」
   （服务端 tick 回调）与「关服」；客户端只有**进入世界**（内置服务端跑起来）才会出现该块，
   且关服那份需要**优雅退出**，而 `ft_stop` 是收停进程（`taskkill /T`）。
   ⇒ 客户端侧证据链 = `FAB-CLIENT-BOOT`（桥已安装 + 注册桥读数 + 审计 61 类通过）；
   需要「世界内渲染确实在派发」时另跑一次 quickplay 进世界的用例（见 §7.4）。
3. **无 watchdog / 无停滞自动收停**：按现行规则这是刻意缺省（无人值守长流程已被闸门禁用）。
4. **断言窗口已改为「文件身份锚点」**（与生产线 `mt_assert.ps1` 同口径）：创建时间 + 头部 4 KiB SHA1 + 长度。
   之所以必须这样，是因为 latest.log **每次冷启动都被轮转**（见 §7.3/D1）—— 纯字节偏移游标在每次启动
   都会失效，且会在「长度重新长过旧偏移」时**静默**给出错误窗口。读到旧格式（裸字节长度）快照时
   只 WARN 并退化为整文件，不静默沿用错误偏移。
5. **`on_fail: keep_game_running` 未实现**（生产线的「保留现场」机制）；失败时实例照常留着，由人工决定收停。
6. **未做「清场 `/kill @e`」与「禁用生物 AI」硬闸门**（生产线 §2 强制项）：fabric 侧需要时经
   `ft_inject` 的 RCON 通道手动下发 `/kill @e[type=!player,distance=..128]`。
7. **`run/fabric-1.20.1/mods`（`build.gradle:248,313-326` 的 `pushToDevRun` 目标）与 Loom dev-run 的
   classpath 是两套投放面**，本台的 `ft_env` 只校验后者（`loom-cache/remapped_working`）+ `run/<side>/mods`。
8. **生产映射冒烟只判「能不能起来」**：`ft_prod.ps1`（§7.2.2）补上了此前最大的缺口
   ——「dev 全绿 ≠ 产物可用」（KI-F13 正是在这个缺口里潜伏到整合包才炸）。但它的判据止于
   「入口点未失败 + 到主菜单」；**游戏内玩法、渲染细节、按键响应**仍需 dev 侧用例与人工取证。
   ⚠️ 它读的是**真实整合包目录**：会触发实例 `logs/latest.log` 轮转（与本台其它脚本同口径，读整文件）；
   默认收停不留进程，`-KeepAlive` 才会保留现场。

---

## 9. 依据索引（脚本注释里也逐条写了，此处汇总）

| 事实 | 依据 |
|---|---|
| fabric 子项目 / 运行目录 / datagen 目录 | `fabric-1.20.1/build.gradle:1-2, 172-182, 189-200` |
| `src/generated/resources` 必须进产物（真实坑） | `fabric-1.20.1/build.gradle:27-43` 的注释与 `sourceSets.main.resources` |
| `META-INF/jars` 内嵌库口径 | `fabric-1.20.1/build.gradle:85-89, 378-407` |
| 前置清单 | `fabric-1.20.1/build.gradle:65-158`、`fabric-1.20.1/gradle.properties`、`src/main/resources/fabric.mod.json:29-40` |
| 产物分发三落点与白名单 | `fabric-1.20.1/build.gradle:226-416` |
| 退出码表 | `scripts/test/lib/Mt.Phase.psm1:29-36` |
| 闸门口径 | `scripts/test/TESTING-RULES-OVERVIEW.md` §3.1、§3.2、§10 |
| `LoaderBus#dispatchReport()` 格式 | `fabric-1.20.1/src/main/java/.../platform/event/LoaderBus.java:67-89` |
| 派发统计打印点（关服 / 开局 600 tick） | `.../platform/FabricBridges.java:81-104` |
| tick → 事件派发路径 | `.../platform/FabricBridges.java:107-137` |
| 桥接覆盖表（哪些事件由谁派发） | `.../platform/FabricBridges.java:45-73` |
| 必须装桥否则全不派发（真实事故） | `.../AstralDiceMod.java:47-56` |
| entrypoint | `fabric-1.20.1/src/main/resources/fabric.mod.json:14-24` |
| dev-run 主类与 `fabric.dli.*` 属性 | 实测 `net.fabricmc:dev-launch-injector:0.2.1+build.8` 内 `net/fabricmc/devlaunchinjector/Main.class`（jar 列目录 + 常量池 `grep`） |
| ⚠️ **latest.log 每次冷启动都被轮转**（本台最关键的平台事实） | 实测 2026-09-29：启动瞬间 `run/server/logs/2026-09-29-7.log.gz` 被写出（时间戳 18:05:55 = 启动时刻），`latest.log` 被重建。⇒ 字节偏移游标在每次启动都失效，必须用文件身份锚点（`Ft.Common.psm1#Get-FtLogAnchor`） |
| ⚠️ **游戏运行时 latest.log 被独占** | 实测：`[System.IO.File]::ReadAllBytes(latest.log)` 在服务端运行期间抛 `The process cannot access the file … because it is being used by another process.` ⇒ 必须用 `FileShare.ReadWrite` 打开（`Read-FtFileBytesShared`） |
| ⚠️ **KubeJS 6+ 不放行 `java.nio.file.*` / `java.io.*`** | 实测：`Java.loadClass('java.nio.file.Paths')` 返回 null 且不产生 `Loaded Java class '…'` 行；`java.lang.String` / `dev.latvian.mods.kubejs.*` 才有该行。⇒ 观察者无法构造 `Path` |
| ⚠️ **KubeJS 6+ 已移除 `java` / `Packages` 全局** | 实测日志：`Line 57: 'java()' is no longer supported! Read more on wiki: https://kubejs.com/kjs6` |
| KubeJS 面向脚本的 bindings 清单（无文件/路径包装器） | `unzip -l <loom-cache 的 kubejs jar>` → `bindings/` 下只有 Block/DamageSource/Ingredient/Item/Java/KMath/Text/Utils 八个 Wrapper |
| KubeJS 的类过滤入口 | `javap` → `KubeJSPlugins.createClassFilter(ScriptType)`；`ScriptManager.loadJavaClass(String, boolean)` 的拒绝文案 = `Failed to load Java class '%s': Class is not allowed by class filter!` |
| KubeJS `server.runCommand` | `javap dev.latvian.mods.kubejs.core.MinecraftServerKJS` → `kjs$runCommand(String):int`、`kjs$runCommandSilent(String):int` |
| KubeJS `Java.loadClass` | `javap dev.latvian.mods.kubejs.bindings.JavaWrapper#loadClass(String):Object` |
| KubeJS `JsonIO.readString(Path)` | `javap dev.latvian.mods.kubejs.util.JsonIO#readString(java.nio.file.Path):String` |
| `ServerEvents.tick` + `console.info` 可用（探针模式成立） | `event_bridge_probe.js` 实测执行并打印 `[event-probe] …`（KubeJS 在本台的定位 = 读数探针） |
| 服务端就绪标记 `Done (…)!` | 实测 `run/server/logs/latest.log`（多轮，最近一轮在启动后 9–12 s） |
| 客户端就绪标记 `Sound engine started` | 实测 `run/client/logs/debug.log`（`[Render thread/INFO] (net.minecraft.client.sounds.SoundEngine) Sound engine started`） |
| 客户端 run 的必需参数 `-PtestSodium=false` | `fabric-1.20.1/build.gradle` 的 client run 段（工程既有测试开关）；不带则 Sodium 因 LWJGL 版本不符在启动期硬失败 |
| RCON 键存在 | 实测 `run/server/server.properties`（`rcon.port=25575` / `enable-rcon` / `rcon.password`）；`ft_env --enable-rcon` 写入后**重启**生效 |
| `/say` 经 RCON 的控制台回声（可断言锚点） | 实测 `[18:19:30] [Server thread/INFO] (Minecraft) [Not Secure] [Rcon] ft-inject-ok` |
| 日志实测样例（派发报告 / 内嵌库） | `run/server/logs/latest.log`（`- starengine_lib 1.0.7`、`[已派发 10 类] … ServerTickEvent=599`） |

---

## 10. 与既有资产的关系（只读边界）

* **未改动**任何生产线文件：`src/` 源码、`scripts/test/mt*.ps1`、`scripts/test/lib/*.psm1`、
  `scripts/test/cases/*.json`、`scripts/test/TESTING-SPEC.md`、`TESTING-RULES-OVERVIEW.md` 全部原样。
* `scripts/test/fabric/event_bridge_probe.js` **原样保留**（它是既有人工取证脚本，本台不接管它，
  只提供 `ft_env --install-probe` 把它拷进运行目录）。
* ⚠️ **已删除**：`scripts/test/fabric/ft_cmd_watcher.js`（KubeJS 命令队列观察者）。删除理由见 §7.3/D3 ——
  它在 KubeJS 6+ 上**从根上不可实现**，且「看起来存在、实际永远失败」比没有更危险。
  对应的 `ft_env --install-watcher` / `ft_inject --channel kubejs` 一并撤除
  （后者保留为**显式失败**，附完整实测理由）。
* 本台运行时只写三类位置：`scripts/test/fabric/`（`.ft_offsets.json` / `.ft_launch_state.json` /
  `reports/`，见 `.gitignore`）、仓库根 `temp/`（子进程日志）、以及 fabric 的运行目录
  （`fabric-1.20.1/run/**`，仅 `ft_env --mode install/--enable-rcon/--install-probe`、`ft_launch`、
  `ft_stop --purge-saves` 会写；默认的 `verify` / 读数类脚本**只读**）。
