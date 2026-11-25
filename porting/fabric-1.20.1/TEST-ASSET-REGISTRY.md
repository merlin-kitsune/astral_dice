# fabric-1.20.1 实机测试台登记 + 测试资产整理（2026-11-01）

> 本报告记录两件事：**A. 把 fabric-1.20.1 正式登记进 PowerShell 编排层**（scripts/test/mt*.ps1 + lib/Mt.*.psm1），**B. 整理测试资产**（用例归并、pwsh 遗产界定、临时文件清理）。
> 全程遵守：不 commit / 不 push / 不 stash；不动工作树里他人/前序批次的既有改动；未启动游戏、未跑 gradle。所有判据命令都可离线复跑（见 §8）。

## 0. 结论速览

| 项 | 登记前 | 登记后 |
|---|---|---|
| PowerShell 版本表（Mt.Paths.psm1） | 3 条线 `1.21.1 / 1.20.1 / 26.1.2` | 4 条线（末尾追加 `fabric-1.20.1`，既有三行值一字未变） |
| fabric 的 run 根 | 无（PowerShell 侧只有 `run/<版本>` 单目录语义） | 双根 `<repo>/fabric-1.20.1/run/client` 与 `/run/server`，按 side 解析 |
| `mt_case.ps1` 能校验的用例 | 266 个文件 → **257 通过 / 9 失败**（9 条全是 fabric） | 273 个文件 → **273 通过 / 0 失败 / 0 解析错误** |
| fabric 用例数量 | `scripts/test/cases/` 9 条（未跟踪）+ 遗留目录 8 条（旧命名、无 version 字段 ⇒ 跑不了） | `scripts/test/cases/` **16 条**（9 + 归并 7）；遗留目录的 8 条保留作历史，映射见 §6 |
| pwsh 遗产层 | 未界定 | 10 个 ft_*.ps1 + 4 个探针 js 逐个判定，写进 `scripts/test/fabric/README.md` §11 |
| porting/fabric-1.20.1 临时文件 | 35 个 `.b*` 点文件 + 1 个 `.bak` | 全部删除（另有 1 个因 ACL 删不掉，见 §7） |

**零回归口径（实测，非推断）**：另三线（1.21.1 / 1.20.1 / 26.1.2）的 `Get-MtPaths` 全部 24 个字段 + 3 条 process marker，与改动前的模块副本逐字段对比 **0 处差异**（§4.1）；`cases/` 下另三线既有 257 条用例的校验结果不变（§4.2）。

## 1. 登记前基线（实读）

```
# scripts/test/lib/Mt.Paths.psm1（改动前，625 行）
:32  $script:VERSIONS   = @('1.21.1', '1.20.1', '26.1.2')
:33  $script:LOADER     = @{ '1.21.1'='neoforge'; '1.20.1'='forge'; '26.1.2'='neoforge' }
:34  $script:SUBPROJECT = @{ '1.21.1'='neoforge-1.21.1'; '1.20.1'='forge-1.20.1'; '26.1.2'='neoforge-26.1.2' }
:95  $runDir = Join-Path (Join-Path $root 'run') $Version     # 单目录语义，fabric 无处安放
:100-104  $confKey = switch ($Version) { '1.21.1'{...} '26.1.2'{...} default{'MT_PACK_MODS_FORGE'} }
```

登记前 `cases/` 全量校验（改动前模块 + 改动前 mt_case.ps1）：

```
MT_VALIDATE_ALL: total=266 ok=257 fail=9 parse_error=0
# 9 条失败全是 *-fabric-1.20.1.json，每个文件报三类错误：
#   1) version 非法：fabric-1.20.1
#   2) 条目没有任何断言（asserts 或内联 assert 步骤）        ← 用的是单数 assert 键
#   3) 步骤 N: 未知原语 jar_stats（不允许自造 op）            ← 以及断言类型 jar/bytecode
```

⇒ 登记 fabric 不只是「往版本表加一行」：还要补 **单数 assert 别名**、**jar_stats 原语**、**jar/bytecode/bytecode_absent 断言族**，否则要么校验不过、要么「校验通过却零断言」的假绿。

## 2. 任务 A1：scripts/test/lib/Mt.Paths.psm1（唯一路径事实源）

改动后 750 行，CRLF 750 / bareLF 0 / bareCR 0 / 无 BOM / `Parser::ParseFile` 错误 0。**另有 209 行 diff 全部是新增或注释**（§4.3 逐行）。

| # | 位置 | 改动 | 为什么 |
|---|---|---|---|
| A1-1 | `:41-43` | `VERSIONS` 末尾追加 `'fabric-1.20.1'`；`LOADER` 加 `'fabric-1.20.1'='fabric'`；`SUBPROJECT` 加 `'fabric-1.20.1'='fabric-1.20.1'` | 版本表本身。**既有三行只做行尾追加，值一个字符没改**，且 fabric 排在 26.1.2 之后、没插进 1.21.1→1.20.1 的门控咬合段 |
| A1-2 | `:28-40`（新增注释块） | 写清「三处一致」的**实际**对应关系 | 文件头原注释要求版本表与 `paths.sh` / `mt_paths.py` 一致，但这两个文件已随 commit 92fbeaf 删除 ⇒ 本模块是 pwsh 侧唯一来源；bash 侧等价源是 `scripts/test/win/lib/common.sh:26 MT_ALL_VERSIONS` / `:36 mt_subproject` / `:46 mt_loader` / `:55 mt_toolchain` |
| A1-3 | `:56-66` | `DEFAULT_PACK_MODS` 加 `'fabric-1.20.1' = 'D:\.minecraft\versions\1.20.1-Fabric 模组测试\mods'` | 该实例目录实测存在。**没有这一条时** fabric 会落进 confKey 的 `default` 分支拿到 `MT_PACK_MODS_FORGE` ⇒ 把 Forge 整合包的 mods 当 fabric 的兼容模组源 |
| A1-4 | `:76-158`（新增） | 常量 `$FABRIC_VERSION` / `$FABRIC_SIDES=@('client','server')` / `$FABRIC_DEFAULT_SIDE='client'` + 5 个函数：`Get-MtSides` `:86`、`Get-MtFabricVersion` `:87`、`Resolve-MtSide` `:89`、`Get-MtRunRoot` `:116`、`Get-MtRunLabel` `:139` | 双 run 根的**唯一拼装点**。`Resolve-MtSide` 三级解析：显式 `-Side` > `$env:MT_SIDE` > `'client'`（与 bash `fabric.sh:402` 的 `${MT_SIDE:-}` + `MT_FAB_DEFAULT_SIDE` 同义）；非法值 **throw**（写错 side 只会读到另一个 side 的日志，属静默失败）。⚠️ 必须 `Trim().ToLowerInvariant()` 后再比：PowerShell 的 `-contains` 对字符串**大小写不敏感**，`'Server'` 会「校验通过」却拼出 `run\Server` |
| A1-5 | `:192-197` | `Get-MtPaths` 加 `param([AllowEmptyString()][string]$Side = '')` | side 在模块内解析，**全仓既有 40 处 `Get-MtPaths` 调用点（不含本批新增的 2 处）一律不改**（它们只传 `-Version`） |
| A1-6 | `:206-207` | `$sideName = Resolve-MtSide …` + `$runDir = Get-MtRunRoot …`（替换原 `$runDir = Join-Path (Join-Path $root 'run') $Version`） | 双根 vs 单根的唯一分歧点；另三线由 `Get-MtRunRoot` 走原式子，**语义一字不变** |
| A1-7 | `:211-219` | `confKey` switch 增 `'fabric-1.20.1' { 'MT_PACK_MODS_FABRIC' }`（在 `default` 之前） | 见 A1-3 |
| A1-8 | `:229-232` | 返回对象新增 **两个纯新增字段** `side` / `run_label` | 既有 40 处调用只读既有属性 ⇒ 向后兼容；`run_label` 与 bash `mt_fab_run_label` 同形（`fabric-1.20.1/run/client` vs `run/1.21.1`） |
| A1-9 | `:274-279` | `Get-MtProcessMarkers` 增 fabric 分支：`@(":fabric-1.20.1:", 'fabric-1.20.1\run\<side>', <绝对 run_dir>)`；另三线返回原三元素数组 | fabric 的 run 根不在 `run\<版本>` 下，`run\<版本>` 式标记会**一个进程也匹配不到**（mt_stop 收不干净）；同时仍刻意不用裸子项目名（会误杀 IDE 语言服务器，见函数内注释） |
| A1-10 | `:739-750` | `Export-ModuleMember` 首行加 5 个新函数名 | 不导出就调用不到 |

## 3. 任务 A2 / A3：mt_case.ps1（用例校验与执行）与 mt.ps1（入口 --side）

### 3.1 scripts/test/mt_case.ps1（改后 3233 行 CRLF / bareLF 0 / 无 BOM / 解析错误 0）

| # | 位置 | 改动 | 为什么 |
|---|---|---|---|
| A2-1 | `:226` | `$script:AssertTypes` 追加 `'jar','bytecode','bytecode_absent'` | 对齐 bash 运行器 `scripts/test/win/lib/fabric.sh:527-603` 已有的断言族。另三线不写这些 type 就一次也走不到 |
| A2-2 | `:219` | `Primitives` 表新增 `'jar_stats' = @('save_as')` | bash 的 op `jar_stats` 只吃 `save_as`（默认键 `'jar'`）；不登记就报「未知原语」 |
| A2-3 | `:1279-1307` | 新增 `Get-MtCaseAsserts`：按 `assert` → `asserts` 顺序取第一个非空清单 | 与 bash `fabric.sh:654` 的 `list(case.get('assert') or case.get('asserts') or [])` 同义。⚠️ 校验期（`Test-MtCaseValid`）与执行期（`Invoke-MtCaseRun`）**必须共用**这一个函数，只改校验期会得到「校验通过、跑 0 条断言」的假绿 |
| A2-4 | `:1360` | 断言存在性判据改用 helper | 单数 `assert` 也算数 |
| A2-5 | `:1363-1371` | 新增用例级 `side` 校验（只接受 `client` / `server`；**缺键不报错**） | fabric 双 run 目录；另三线既有用例无该键 ⇒ 零改动 |
| A2-6 | `:1462-1496` | 断言清单改 helper + `jar` / `bytecode` / `bytecode_absent` 的**字段封闭校验** | bash 对未知字段返回 `None` ⇒ BLOCKED（不静默通过）；这里提前到校验期拦下 |
| A2-7 | `:1623-1700` | 新增 `Get-MtCaseJarEntries` / `Get-MtCaseJarStats` | 离线读 `<repo>/<subproject>/build/libs` 下最新 `astral_dice-*.jar`（排除 `-sources/-dev/-slim`），用 `[System.IO.Compression.ZipFile]::OpenRead` 取条目名；字段（entries/models/recipes/trinkets/bountiful/embed/signs）逐项对齐 `fabric.sh:484-501`。**不启游戏、不跑 gradle** |
| A2-8 | `:1841-1877` | `Invoke-MtCaseAssert` 新增 `jar` 分支 | min 系比 `>=`、max 系（`bountiful_max`/`embed_max`）比 `<=`、`embed_lib` 比 `(embed>0)`；缺 state 时 FAIL `jar-state-missing`（与 bash 逐字同形，绝不静默通过） |
| A2-9 | `:1879-1900` | 新增 `bytecode` / `bytecode_absent` 分支 | `pattern` 对 jar 条目名 `-match`；`bytecode` 命中 ≥1 PASS，`bytecode_absent` 命中 0 PASS；无 jar ⇒ FAIL `bytecode:no-jar` |
| A2-10 | `:2133-2152` | `Invoke-MtCaseOp` 新增 `jar_stats` 分支 | 结果存 `$script:CaseScope[save_as]`（默认 `'jar'`）；取不到 jar ⇒ `BLOCKED`「找不到产物 jar（先跑 --phase build）」 |
| A2-11 | `:2280` / `:2738` | `Get-MtCaseClientStatus` 与 `Invoke-MtCaseRun` 透传 `-Side $caseSide` | 用例顶层 `side` 决定读哪个 run 根的 `latest.log`；日志路径本身由 `$Paths.run_dir` 派生（`mt_case.ps1:2467` 的 `runclient_launch.log` 自动跟随），无第二处拼装 |

### 3.2 scripts/test/mt.ps1（改后 990 行 CRLF / bareLF 0 / 无 BOM / 解析错误 0）

| # | 位置 | 改动 | 为什么 |
|---|---|---|---|
| A3-1 | `:26` | `.EXAMPLE` 新增 `pwsh -File scripts/test/mt.ps1 --version fabric-1.20.1 --side server --phase launch` | 用法可发现 |
| A3-2 | `:645-647` | 帮助窗口 `Select-Object -Skip 1 -First 24` → `-First 25`（注释同步说明） | 新增一行示例，不 +1 会把最后一条示例裁掉 |
| A3-3 | `:569` | `$script:Opt` 新增 `Side = ''` | 参数落点 |
| A3-4 | `:610` | `$script:ArgSpec` 新增 `'side'`（Value 型，缺值文案「缺少 --side 的值」） | 表驱动解析 |
| A3-5 | `:715-738` | 新增 side 归一块：`Trim().ToLowerInvariant()` → 非 fabric 版本 ⇒ 报错退出 2；非 client/server ⇒ 报错退出 2；通过则 `$env:MT_SIDE = $Side` + 打印 `MT_INFO: SIDE: …` | 与 bash `scripts/test/win/mt.sh:136-158` 逐条对齐。**为什么用环境变量**：side 由 Mt.Paths 在每个进程内统一解析（全仓 40 处调用点只传 `-Version`），env 能让父进程与全部子进程（mt_preflight / mt_launch / mt_case / mt_assert）看到同一 side，且不新增参数面 |

### 3.3 mt_launch.ps1 / mt_preflight.ps1：**判定「无需改动」+ 判据**

两个文件相对改前副本 **diff = 0 行**（`diff -u --strip-trailing-cr` 输出为空）。判据：

1. 全仓既有 `Get-MtPaths` 调用 **40 处**（不含本批新增的 2 处），**全部只传 `-Version`**，side 在模块内解析 ⇒ 无需逐点改。
2. `mt_launch.ps1` 用到 run 目录的地方只有 4 处，全部读 `$p.run_dir`：`:492 Sync-MtProbeScripts -RunDir`、`:494 runclient_launch.log`、`:688 scanTargets`、`:740 iris.properties`。
3. `mt_preflight.ps1` 只有 4 处：`:549-550 New-Item $p.run_dir`、`:552 .mt_write_probe`、`:558 返回 $run_dir`。
4. 两文件里的版本字面量（55 / 10 处）**全是注释或按版本分支的校验语义**（就绪行、模组清单解析、gamerule 名、光影/史莱姆/Carpet 闸门），**不含路径拼装** ⇒ 属「登记边界」（fabric 的等价语义 bash 侧已实现，pwsh 侧未实现，见 §9）。
5. 反证：本次改动后 fabric 的 `run_dir` 实测为 `<repo>\fabric-1.20.1\run\client|server`（§4.1），而两文件 diff 为 0 ⇒ 它们确实只是消费者。

## 4. 任务 A4：零回归判据与实测输出

### 4.1 另三线路径 24 字段 + 3 marker：0 处差异

方法：把改动前的模块副本放进独立目录（`$script:ROOT` 由模块自身位置推导），分别 dump 改前 / 改后模块对 `Get-MtPaths` 的全部字段，再**按各自 ROOT 归一化**后逐字段比对。

```
legacy field diffs (self-root normalized) = 0
baseline root = D:\MCProject\astral_dice_multiloader\scripts\test\win   # 副本所在位置（归一化用）
final    root = D:\MCProject\astral_dice_multiloader
```

比对范围：`loader / subproject / root / run_dir / mods_dir / saves_dir / logs_dir / latest_log / debug_log / kubejs_log / kubejs_client_log / probe_log / crash_dir / shot_dir / client_world / server_world / task_build / task_client / task_server / task_gametest / pack_mods_dir / markers`（1.21.1 / 1.20.1 / 26.1.2 各一遍）。

（注意：第一次比对报 45 处「差异」，全是 ROOT 前缀不同造成的——副本模块的 ROOT 是它所在的 `scripts/test/win`。按各自 ROOT 归一化后为 0。）

### 4.2 cases/ 全量校验：273/273 通过

```
$ pwsh -NoProfile -NonInteractive -File scripts/test/win/logs/_baseline/validate-all-cases.ps1
MT_VALIDATE_ALL: total=273 ok=273 fail=0 parse_error=0      # EXIT=0
```

改动前同一条命令：`total=266 ok=257 fail=9 parse_error=0`（9 条失败 = 当时的 9 条 fabric 用例）。另三线 257 条**结果不变**（改动前后都是 ok）。

### 4.3 git diff 逐行说明（相对「改前工作树副本」，不含前序批次改动）

| 文件 | diff 行数 | 说明 |
|---|---|---|
| `scripts/test/lib/Mt.Paths.psm1` | 209 | 4 个 hunk：版本表段（注释+三行行尾追加）、DEFAULT_PACK_MODS+双根函数块（新增）、Get-MtPaths（param/sideName/runDir/confKey/两个新字段）、markers 分支+导出清单。**没有删除或改写任何另三线语义行**，唯一被替换的原句是 `$runDir = Join-Path (Join-Path $root 'run') $Version`，其等价语义由 `Get-MtRunRoot` 的非 fabric 分支逐字保留 |
| `scripts/test/mt_case.ps1` | 374 | 8 个 hunk（见 §3.1 表）。所有新增分支都以 `$t -eq 'jar'` / `'bytecode…'` / `$op -eq 'jar_stats'` 为前置条件，另三线用例走不到 |
| `scripts/test/mt.ps1` | 71 | 4 处（见 §3.2 表）。`--side` 未给出时整块不执行（`if ($Side)`）⇒ 默认路径零变化 |
| `scripts/test/mt_launch.ps1` | **0** | 未改动 |
| `scripts/test/mt_preflight.ps1` | **0** | 未改动 |
| `scripts/test/fabric/README.md` | 新增两段 | 头部登记更新 + 末尾 §11 资产登记与遗产界定（§6/§7） |

### 4.4 行尾与编码（改后实测）

```
scripts/test/lib/Mt.Paths.psm1 | bytes=35387 crlf=750  barelf=0 bare_cr=0 bom=False parse_errors=0   (改前 625 CRLF)
scripts/test/mt_case.ps1      | bytes=180184 crlf=3233 barelf=0 bare_cr=0 bom=False parse_errors=0   (改前 2979 CRLF)
scripts/test/mt.ps1           | bytes=62233 crlf=990  barelf=0 bare_cr=0 bom=False parse_errors=0   (改前 960 CRLF)
```

三文件全部 **每行都以 CRLF 结尾**（`barelf=0`、`bare_cr=0`），**无 BOM**；含中文内容经 `[System.Management.Automation.Language.Parser]::ParseFile` 解析错误 **0**（无 BOM 也能被 PowerShell 正确解析，因为文件是 UTF-8 且脚本内没有依赖 BOM 的字面量）。CRLF 行数比改前多，纯粹是新增行数（209/374/71 行 diff 中绝大部分是新增行），**没有把任何既有行改成裸 LF**。

另外：7 个新用例 JSON 位于 `scripts/test/cases/`，该目录既有约定是 **LF + UTF-8 无 BOM**（与另外 9 条 fabric 用例、另三线用例一致），实测「BOM/CR 的 fabric 用例 = 空」。`scripts/test/fabric/README.md` 保持 CRLF（1104 行 CRLF / bareLF 0）。

## 5. 登记后 fabric 的实际解析结果（实测 dump）

```
fabric-1.20.1                 side=''  → run_dir=<repo>\fabric-1.20.1\run\client
fabric-1.20.1 -Side client    → run_dir=<repo>\fabric-1.20.1\run\client    run_label=fabric-1.20.1/run/client
fabric-1.20.1 -Side server    → run_dir=<repo>\fabric-1.20.1\run\server    run_label=fabric-1.20.1/run/server
$env:MT_SIDE='server'         → run_dir=<repo>\fabric-1.20.1\run\server
task_build=:fabric-1.20.1:build  task_client=:fabric-1.20.1:runClient
task_server=:fabric-1.20.1:runServer  task_gametest=:fabric-1.20.1:runGameTestServer
pack_mods_dir=D:\.minecraft\versions\1.20.1-Fabric 模组测试\mods   (Test-Path=True)
markers=[":fabric-1.20.1:", "fabric-1.20.1\run\client", "<绝对 run_dir>"]
```

`--side` 的入口实测（**不启动游戏**，全部走早退路径）：

| 命令 | 结果 |
|---|---|
| `--version 1.21.1 --side server …` | `MT_ERROR: --side 只对 fabric-1.20.1 有意义（收到 --version 1.21.1；其它三线是单 run 目录 run/<版本>）` EXIT=2 |
| `--version fabric-1.20.1 --side both …` | `MT_ERROR: --side 需要 client 或 server（收到：both）` EXIT=2 |
| `--version fabric-1.20.1 --side server --phase nosuchphase` | 先 `MT_INFO: SIDE: server（fabric-1.20.1 运行目录 fabric-1.20.1/run/server）`，再 `MT_ERROR: 未知阶段 nosuchphase` EXIT=2（证明归一块确实执行且未进入任何阶段） |
| `--side`（缺值） | `MT_ERROR: 缺少 --side 的值` EXIT=2 |
| `--help` | EXIT=0，末尾含新增的 fabric 示例行 |

## 6. 任务 B1：用例去重归并（映射表）

### 6.1 遗留目录 `scripts/test/fabric/cases/`（8 条，旧命名 `FAB-*`）

共同 schema：`asserts / case_id / evidence / on_fail / side / steps / title / window`，**没有 `version` 字段** ⇒ 现在的 bash/pwsh 运行器（要求 `version == 'fabric-1.20.1'`）**一条也跑不了**。

| 遗留用例 | 独有覆盖 | 处置 | 并入 |
|---|---|---|---|
| `FAB-BOOT-EMBED` | Loader mods 清单里出现 `- starengine`（证明 Loom `include` 的内嵌 jar 真被加载） | **迁入** | `cases/BOOT-EMBED-fabric-1.20.1.json` |
| `FAB-CATCHUP-PARITY` | `/astralcatchup` 的 ENV/MELEE/RANGE/CONC/LEGACY 五条机器行 | **迁入** | `cases/CATCHUP-PARITY-fabric-1.20.1.json` |
| `FAB-CLIENT-BOOT` | 客户端事件桥 / 客户端注册桥 / 事件订阅审计（中文机器行） | **迁入** | `cases/CLIENT-BOOT-fabric-1.20.1.json` |
| `FAB-INJECT-ROUNDTRIP` | rcon 注入 → 日志回读的端到端往返 | **迁入** | `cases/INJECT-ROUNDTRIP-fabric-1.20.1.json` |
| `FAB-JAR-ASSETS` | `bountiful_max: 0`（`data/bountiful` 必须为空）；其余四项已被卡片/资源/饰品用例覆盖 | **迁入** | `cases/JAR-ASSETS-fabric-1.20.1.json` |
| `FAB-PARTY-BACKENDS` | `AP_FAB_PARTY:` 队伍后端（sw_mc/sw_ftb/sw_opac/back_*/why_*） | **迁入** | `cases/PARTY-BACKENDS-fabric-1.20.1.json` |
| `FAB-Q2Q3-INVARIANTS` | `AP_CATCHUP_EFFREM:` / `AP_CATCHUP_MEDKIT:` 两条不变量 | **迁入** | `cases/Q2Q3-INVARIANTS-fabric-1.20.1.json` |
| `FAB-DISPATCH-BASIC` | 事件派发统计（`[已派发 N 类]` / `[未派发 N 类]`）+ 断言 `dispatch_tick` / `dispatch_fired` / `dispatch_zero_absent` + 步骤 `wait_for` | **保留 legacy，不迁入** | — |

**`FAB-DISPATCH-BASIC` 为什么保留**：其断言类型 `dispatch_tick / dispatch_fired / dispatch_zero_absent` 与步骤 `wait_for` 只存在于 pwsh 遗产层（`ft_assert.ps1` / `ft_case.ps1`），**bash 运行器（`fabric.sh` 的 `eval_assert`）与 `mt_case.ps1` 都没有实现**。迁进 `cases/` 只会变成「校验期报未知类型」或「跑起来恒 BLOCKED」——那比留在原处更糟。它的一部分事实（事件派发是否发生、有无 tick 异常）已由 `cases/TICK-DISPATCH-fabric-1.20.1.json` 以 bytecode + `absent Exception ticking` 的方式覆盖。**没有静默删除任何文件**。

### 6.2 迁移时的字段变换（逐条已执行）

1. `case_id` = 文件名主干（含 `-fabric-1.20.1`）；
2. 补 `"version": "fabric-1.20.1"`；
3. **保留顶层 `side`**（client / server），名称不变；
4. 删除 `window` 键：原 `window: whole` 的用例，把 **每条 log / absent 断言**加 `"scope": "whole"`；原 `window: case` 不加（缺省即 case）；
5. `inject_command` 步骤**去掉 `channel` 字段**（bash 运行器只读 `command`，`mt_case.ps1` 的原语字段表是封闭的）；
6. 有 `jar` / `bytecode` 断言的用例，在 `steps` 首位补 `{"op": "jar_stats", "save_as": "jar"}`（否则断言 `jar-state-missing`）；
7. 第一条 `note` 前置「【迁移说明 2026-11-01】…」，指明文中 `ft_*.ps1` 命令属遗产层、等价 bash 命令见 README 资产清单表；
8. 顶层键序 `case_id / title / version / side / on_fail / steps / asserts`，断言清单统一用**复数 `asserts`**（校验器与 bash 都收）。

### 6.3 正式目录构成（16 条 fabric 用例）

| 来源 | 条数 | 说明 |
|---|---|---|
| 前序批次已存在的 `*-fabric-1.20.1.json` | 9 | BOOT-READY / CARD-CHIP-UI / CURIOS-SLOT-SIZING / JAR-RESOURCE / PLATFORM-BRIDGE / SIGN-ATTR / TICK-DISPATCH / TRINKET-BACKEND / VERSION-GATE（**未改动**） |
| 本次 B1 归并 | 7 | 见 §6.1 表 |
| `scripts/test/fabric/cases/` 遗留 | 8（不参与执行） | 保留作历史；README §11.1 有映射表与「已并入 cases/<新名>，保留仅作历史」标注 |

实测：`bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase cases --allow-auto --dry-run` ⇒ `DRY-RUN: bash（原生）解析 scripts/test/cases/*-fabric-1.20.1.json —— 条目 16 个`（EXIT=0，不启动游戏）。

## 7. 任务 B2 / B3：pwsh 遗产判定 + 临时文件清理

### 7.1 遗产判定（已写进 `scripts/test/fabric/README.md` §11.3）

| 文件 | 职责 | bash 等价实现 | 判定 | 依据/缺口 |
|---|---|---|---|---|
| `ft.ps1` | 旧法总入口 | `scripts/test/win/mt.sh` | 纯遗产 | 仅被 README / docs 引用，无代码调用 |
| `ft_launch.ps1` | 单 side 启动（强制单 side、拦 `--side both`） | `phases.sh:289 mt_phase_launch` + `fabric.sh:342 mt_fab_phase_launch` | 纯遗产 | 被 `ft.ps1:122`、`ft_env.ps1:200`、`ft_stop.ps1:9,86` 引用（**同层内部**引用） |
| `ft_stop.ps1` | 收停 | `phases.sh:326 mt_phase_stop` | 纯遗产 | 被 `ft.ps1:123`、`ft_launch.ps1:53,229` 引用（同层） |
| `ft_inject.ps1` | rcon 注入（要求 server side） | `fabric.sh:224 mt_rcon_exec` | 纯遗产 | 被 `ft_case.ps1:191`、`ft_env.ps1:32,42,325,346`、`ft.ps1:123` 引用（同层） |
| `ft_build.ps1` | 构建 | `phases.sh:266 mt_phase_build` | 部分独有 | bash 缺「磁盘源 ↔ 产物」对照 |
| `ft_assert.ps1` | 断言引擎 | `fabric.sh:527-603 eval_assert` | 部分独有 | bash 缺 `dispatch_*` 三种断言与 snapshot 窗口 |
| `ft_case.ps1` | 用例调度 | `fabric.sh:398-753 mt_fab_cases_run` | 部分独有 | bash 缺 `wait_for` 步骤 |
| `ft_env.ps1` | 环境准备 | `phases.sh:250 mt_phase_env` + `fabric.sh:193-216` | 部分独有 | bash 不写 `server.properties`、不 `install-probe` |
| `ft_dispatchreport.ps1` | 派发报告（读日志窗口） | 无 | **独有能力**（保留） | 无等价实现 |
| `ft_prod.ps1` | 生产冒烟入口 | 无 | **独有能力**（保留） | 被 `scripts/test/prod_smoke.py:7,22`、`TESTING-SPEC.md:2174,2250`、`linux/README.md:101` 引用 |

探针 js（4 个）——**全部活资产，本轮一个都不移**：

| 文件 | 引用面 | 判定 |
|---|---|---|
| `astral_catchup_probe.js` | 2 条正式用例（`CATCHUP-PARITY` / `Q2Q3-INVARIANTS` 的机器行来源） | 活资产 |
| `astral_fabric_curio_probe.js` | 仅 README:55 引用，但**独有**（Fabric 侧 Curios/Trinkets 探针） | 活资产（独有） |
| `astral_gs_probe.js` | `ft_env.ps1:36,152-153` | 活资产（遗产层依赖） |
| `event_bridge_probe.js` | `ft_env.ps1:36,152-153` | 活资产（遗产层依赖） |

结论：**不存在「零引用的纯遗产」**——最安全的裁撤候选是 `ft.ps1`（只被文档引用），但仍保留；本轮**没有移动/删除任何 ft_*.ps1 或探针**，因此**未创建 `legacy/` 子目录**（建了却没内容反而误导）。

### 7.2 临时文件清理（先 `ls` 列清单，后逐条删）

删除前核对：`git ls-files` 对这 36 个路径**零命中**（全部未跟踪、非 ignored）⇒ 删除不产生任何 git 删除记录，不影响他人改动。

`porting/fabric-1.20.1/` 下删除的 35 个点文件：

| 文件 | 大小 |
|---|---|
| `.b3-fx.diff` | 5,124 B |
| `.b3-diff.tmp` | 20,817 B |
| `.b19-crlf-table.json` | 8,870 B |
| `.b19-report-draft.md` | 16,536 B |
| `.b32-apply.log` | — |
| `.b32-audit-full.txt` | — |
| `.b32-audit2.txt` | — |
| `.b32-bytes.txt` | — |
| `.b32-changes-applied.json` | — |
| `.b32-changes.json` | — |
| `.b32-codediff.txt` | — |
| `.b32-drift.json` | — |
| `.b32-drift.md` | — |
| `.b32-dry.log` | — |
| `.b32-evidence.txt` | — |
| `.b32-gates.txt` | — |
| `.b32-gates2.txt` | — |
| `.b32-neo-vs-forge.md` | — |
| `.b32-part1.md` | — |
| `.b32-per.json` | — |
| `.b32-post.txt` | — |
| `.b32-post2.txt` | — |
| `.b32-pouch.txt` | — |
| `.b32-seg.txt` | — |
| `.b32-state.txt` | — |
| `.b32-verify1.txt` … `.b32-verify9.txt` | 9 个 |
| `.b32-who.txt` | — |
| `.b32_apply.py` / `.b32_check.py` / `.b32_dump.py` / `.b32_neocheck.py` / `.b32_post.py` / `.b32_seg.py` | 6 个 |

另删：`fabric-1.20.1/__glove_before.java.bak`（4,734 B）。

验证：`ls -1a porting/fabric-1.20.1/ | grep -E '^\.b' | wc -l` ⇒ **0**；`ls fabric-1.20.1/__glove_before.java.bak` ⇒ `No such file or directory`。

⚠️ **删不掉、已跳过**：`fabric-1.20.1/__dsh_probe.txt`（6 B，2026-11-01 17:07）。

```
$ rm -f fabric-1.20.1/__dsh_probe.txt
rm: cannot remove 'fabric-1.20.1/__dsh_probe.txt': Permission denied      # exit 1，文件仍在
```

与已知的父目录 ACL `Everyone:(CI)(DENY)(DC)` 一致（本轮未再单独测 ACL，结论来自 `rm` 的 Permission denied）。该文件不是本任务产物，保留不影响任何判据。

## 8. 可复跑判据命令清单（本轮已实际跑过，均不启动游戏、不跑 gradle）

### 8.1 pwsh 组

```bash
cd /d/MCProject/astral_dice_multiloader

# ① 三个改动文件的 CRLF / 裸 LF / BOM / 语法解析（期望：bare_lf=0 bare_cr=0 bom=False parse_errors=0）
pwsh -NoProfile -NonInteractive -Command '
  foreach ($f in @("scripts/test/lib/Mt.Paths.psm1","scripts/test/mt_case.ps1","scripts/test/mt.ps1")) {
    $t = [System.IO.File]::ReadAllText((Resolve-Path $f), [System.Text.Encoding]::UTF8)
    $tok=$null; $err=$null
    [void][System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path $f).Path, [ref]$tok, [ref]$err)
    "{0}: crlf={1} bare_lf={2} parse_errors={3}" -f $f, ([regex]::Matches($t,"\r\n")).Count, ([regex]::Matches($t,"(?<!\r)\n")).Count, $err.Count
  }'

# ② 全量用例校验（期望：total=273 ok=273 fail=0 parse_error=0，EXIT=0）
pwsh -NoProfile -NonInteractive -File scripts/test/win/logs/_baseline/validate-all-cases.ps1

# ③ 版本表 / side 解析（期望：VERSIONS 含 fabric-1.20.1；bad side 抛错；另三线 side 为空）
pwsh -NoProfile -NonInteractive -File scripts/test/win/logs/_baseline/check_side.ps1

# ④ 路径 dump 与三线零回归对比（期望：legacy field diffs = 0）
pwsh -NoProfile -NonInteractive -File scripts/test/win/logs/_baseline/dump_paths.ps1 \
  -ModulePath scripts/test/lib/Mt.Paths.psm1 -OutFile scripts/test/win/logs/_baseline/paths_final.json

# ⑤ 入口 --side 的早退判据（每条都应非 0 退出且报指定文案）
pwsh -NoProfile -NonInteractive -File scripts/test/mt.ps1 --version 1.21.1 --side server --phase launch   # EXIT=2 只对 fabric 有意义
pwsh -NoProfile -NonInteractive -File scripts/test/mt.ps1 --version fabric-1.20.1 --side both --phase launch  # EXIT=2 client 或 server
pwsh -NoProfile -NonInteractive -File scripts/test/mt.ps1 --side                                  # EXIT=2 缺少 --side 的值
```

> ③④ 的脚本落在 `scripts/test/win/logs/_baseline/`（未跟踪草稿区，不进交付）。若该目录被清理，等价做法：`Import-Module <绝对路径>/scripts/test/lib/Mt.Paths.psm1` 后直接 `Get-MtPaths -Version <v> [-Side client|server]` 对比输出。

### 8.2 bash 组

```bash
cd /d/MCProject/astral_dice_multiloader

# ① fabric 用例清单解析（期望：条目 16 个，side=server，EXIT=0，不启动游戏）
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side server --phase cases --allow-auto --dry-run

# ② fabric 前置检查 dry（期望：DRY-RUN: bash（原生）fabric 前置检查…，EXIT=0）
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side client --phase preflight --dry-run

# ③ 另三线的裸端口径未被改动（期望：MT_DEPRECATED + DRY-RUN: pwsh -File …/mt_preflight.ps1 --version 1.21.1，EXIT=0）
bash scripts/test/win/mt.sh --version 1.21.1 --phase preflight --dry-run

# ④ --side 口径（期望两条都是 EXIT=2 + 指定文案）
bash scripts/test/win/mt.sh --version fabric-1.20.1 --side bogus --phase cases
bash scripts/test/win/mt.sh --version 1.21.1 --side server --phase cases
```

> 注意：bash 的 `--phase cases` 不带 `--case` 时会被 `MT_AUTO_DISABLED` 闸门拦（EXIT=2，提示逐条执行 `--phase cases --case <file>`），所以「清单解析」这条必须同时给 `--allow-auto --dry-run`。

## 9. 登记边界与无法判定项（如实列出）

1. **已登记 ≠ 全部阶段已适配**。本次登记覆盖：版本表、路径（含双 run 根 + 进程标记）、用例校验与执行（jar/bytecode 断言族、side 透传）、入口 `--side`。**未适配**：`mt_env.ps1`（190 处版本字面量）与 `mt_loadergate.ps1`（19 处）对 fabric 的阶段实现——bash 侧由 `fabric.sh` 原生实现（`ft_env` 等价能力部分缺失，见 §7.1），pwsh 侧这部分仍是三线口径。
2. **无 `--version` 的默认全流程会多跑一条 fabric 线**：`mt.ps1:852` 的 `$flowVersions = if ($Version) { @($Version) } else { @(Get-MtVersions) }` 现在包含 fabric。由于 §9.1 的阶段未适配，**多线默认全流程暂不应把 fabric 算进去**（单版本 `--version fabric-1.20.1` 路径是完整可用的）。是否给全流程加白名单属产品口径决策，本轮未擅自改动门控顺序（A4 要求门控一字不变）。**建议由用户裁决**。
3. **`pack_mods_dir` 对 fabric 的语义**：bash 侧 fabric 只用 `<side>/mods`，不使用整合包 mods（兼容模组来自 `fabric-1.20.1/build.gradle` 的 Loom 依赖）。`Get-MtPaths.pack_mods_dir` 是 pwsh 侧概念，本次给了 `1.20.1-Fabric 模组测试` 的默认值；若将来的 pwsh 阶段对 fabric 使用该字段，需先确认它是否需要。
4. **`mt.conf` 未改**（无 `MT_PACK_MODS_FABRIC` 键）⇒ fabric 走 `DEFAULT_PACK_MODS` 内置默认值。要覆盖时在 `scripts/test/mt.conf` 加该键即可（该文件 gitignored）。
5. **`FAB-DISPATCH-BASIC` 的唯一覆盖暂无用例承接**（`dispatch_*` 断言与 `wait_for` 步骤两端运行器都没实现）。它保留在 `scripts/test/fabric/cases/`，README §11.1 已标注「唯一覆盖 + 不迁入理由」。若要恢复执行，需在 bash `fabric.sh` 的 `eval_assert` 与 `mt_case.ps1` 的 `AssertTypes` 两侧同时实现——**属另一批工作**。
6. **`__dsh_probe.txt` 删除失败**（ACL `Everyone:(CI)(DENY)(DC)`），见 §7.2。
7. **工作树出现并发改动**：`fabric-1.20.1/build.gradle` 在 22:55:28 被本会话之外的写入改过（`git diff --numstat` 显示 +21/−9）；`git status` 里 45 项 **tracked 删除**全部在 `fabric-1.20.1/src/generated/` 与一个 png 贴图，均非本批所为。初始口述的 649 项改动与当前 617 项的差额中，本批确定增量为 **−36（删除未跟踪临时文件）+ 7（新增用例）= −29**，其余 −3 归因于上述并发改动。

## 10. 改动文件清单（本批）

| 文件 | 类型 | 行尾 |
|---|---|---|
| `scripts/test/lib/Mt.Paths.psm1` | 修改 | CRLF（750） |
| `scripts/test/mt_case.ps1` | 修改 | CRLF（3233） |
| `scripts/test/mt.ps1` | 修改 | CRLF（990） |
| `scripts/test/fabric/README.md` | 修改（新增两段） | CRLF（1104） |
| `scripts/test/cases/{BOOT-EMBED,CATCHUP-PARITY,CLIENT-BOOT,INJECT-ROUNDTRIP,JAR-ASSETS,PARTY-BACKENDS,Q2Q3-INVARIANTS}-fabric-1.20.1.json` | 新增（7） | LF（与本目录既有用例一致） |
| `porting/fabric-1.20.1/TEST-ASSET-REGISTRY.md` | 新增（本报告） | CRLF |
| `scripts/test/mt_launch.ps1` / `scripts/test/mt_preflight.ps1` | **未改动**（diff 0） | — |
| `porting/fabric-1.20.1/` 下 35 个 `.b*` 点文件 + `fabric-1.20.1/__glove_before.java.bak` | **删除**（未跟踪） | — |

> 说明：`git status` 里 `mt_launch.ps1` / `mt_preflight.ps1` 也显示为 ` M`，那是**前序批次**的既有改动（本批未触碰，相对改前副本 diff = 0 行）。同理 `scripts/test/cases/*-fabric-1.20.1.json` 里的 9 条是前序批次留下的未跟踪文件。

未提交（遵守禁令）：本批**未** `git add` / `commit` / `push` / `stash`，也未回滚任何前序批次改动。
