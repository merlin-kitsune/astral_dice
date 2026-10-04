# B7 — 断言窗口改为「自本用例起」+ NANCY-LU-PEARL-IMMUNE 的 P2 `tel` 判据修复

- 执行者：测试资产实施者（**只动 `scripts/test/**`**）
- 仓库：`F:\MCProject\astral_dice_multiloader`，分支 `multi-1.20.1-1.21.1`，起始 HEAD `0f70c40`（工作树干净）
- 上游依据：`docs/batch3/B6-test-perf.md`（工具链改造与三条超时）、`TESTING-SPEC.md`
- 卡死护栏：任一步 / 任一用例 **> 6 分钟无新输出** ⇒ `mt.ps1 --phase stop --force`，记录进度并停下报告；
  实际使用 `mt_watchdog.ps1`（cases 相位用 `-Action stop`，build/env/launch 用 `-Action report` + 人工收停，
  理由见 §5）与 `MT_CASE_TIMEOUT_SEC`
- 本文件为**增量落盘**：每完成一项立即写

---

## 1. 任务 1 —— 断言窗口从「自 launch 起」改为「自本用例起」

### 1.1 缺陷（B6 §4.3 已列为工具链缺陷候选，本批实施）

`mt_assert.ps1:280-285`（旧）取的是**固定 launch 偏移**：`.mt_snapshot.json` 只在 launch 收尾写一次，
于是**全部** `log` / `absent` 断言的窗口是「自 launch 起」而不是「自本用例起」⇒
**任何不带 tag 唯一标识的标记都能被前序用例满足**。B6 实测：`APDUMP|LOCKRAW|` 的命中数随用例递增
`14 → 28 → 42 → … → 126`，而相关断言照样 PASS —— 这在事实上把断言弱化成「本轮任意时刻出现过即通过」。

### 1.2 机制（选它而不是「每条用例自建窗口文件」的理由）

**在每条用例开始处刷新 `offsets`**，并把「自 launch 起」的偏移**另存**为 `launch_offsets`：

| 键 | 写入者 | 语义 |
|---|---|---|
| `offsets` | `snapshot --window launch`（launch 收尾）→ `snapshot --window case`（**每条用例开始**） | **当前窗口起点**，用例期间 = 本用例开始时刻 |
| `launch_offsets` | 只在 `--window launch` 写，`--window case` **永不改写** | 自 launch 起（Mixin 应用、整轮报告摘要仍用它） |

理由：

1. `.mt_snapshot.json` 是本仓既有的**跨脚本契约**（`mt_assert` / `mt_case` / `mt_report` 三方共读），
   沿用它不必再引入第二套偏移来源；
2. **B2 记录的「单阶段 launch 不写快照 ⇒ 陈旧 offset 出假 FAIL」在这里被结构性消除**：
   偏移不再依赖「调用方记得补快照」，而是由**用例执行器每条自己写**（`mt_case.ps1`），
   全流程与 `--phase cases` 单步路线都必然经过同一处；
3. 刷新失败 ⇒ 该条用例记 `ERROR`（`MT_CASE_WINDOW: ERROR`），**绝不静默沿用陈旧偏移**
   （静默沿用正是「窗口跨用例」这一缺陷本身）。

### 1.3 窗口语义（三方一致，逐子命令）

| 子命令 | 默认窗口 | 说明 |
|---|---|---|
| `log` | `case` | 只读本用例起点之后的增量 |
| `absent` | `case` | **仍在本用例窗口内实际求值**：起点由本用例开始时写入、覆盖本用例全部动作（kubeji 热重载 fixture / 注入 / 等待 / 截图 / 探针输出），故不会退化成「永远成立」。窗口只影响「从哪里开始读」，判据本身一字未改 |
| `mixin` | **`launch`** | Mixin 应用发生在启动期；若跟随 `case` 窗口，对绝大多数用例会变成「窗口内无 Mixin 失败」= 恒真 ⇒ **弱化断言**。故保持旧语义（读 `launch_offsets`） |
| `crash` | 窗口内 | `crash_baseline` 随每条用例刷新 ⇒ 归因从「本轮无新增崩溃」变为「本用例无新增崩溃」（更强，且 `mt_case` 另有客户端存活/崩溃收尾校验）|
| `kubejs` | 整文件 | 本来就是整文件读（0 errors 是整轮判据），不受影响 |

`--window` 取值：`case` | `launch` | `whole`；`--no-snapshot` 保留为 `--window whole` 的别名（旧调用点兼容）。
用例 JSON 侧对应 `assert.scope`：缺省 `case`、`scope=whole`（启动期事实，如 `Mixing client.GuiMixin …`）、`scope=launch`（两者之间的显式 opt-in）。
⚠️ **审计发现的漏标（见 §5）**：`LOOT-MODIFIER-1.20.1` 的反向断言里**混装了启动/数据包解码期文本** `Could not decode GlobalLootModifier`，
B7 初版未给它标 `scope` ⇒ 收窄后该启动期覆盖被**静默丢掉**；已补 `"scope": "whole"`。
非法 `scope` 在 `mt_case.ps1 validate` 阶段即被拦下（不在执行期静默落回默认窗口）。

**证据可读性**：每条断言的结论行都带窗口标签，例如
`MT_ASSERT_LOG: PASS — /…/ 命中 1 次（latest:latest.log@123456B(win=case)）`；
`MT_CASE_WINDOW: <case_id> — 断言窗口自本用例起（MT_SNAPSHOT: OK — … window=case latest=…B …）`。

### 1.4 兼容性（既有用法全部继续可用）

| 既有用法 | 是否继续可用 | 依据 |
|---|---|---|
| `scope=whole`（`--no-snapshot`） | ✅ | 现映射为 `--window whole`，`--no-snapshot` 仍被接受 |
| `--phase launch` 单步（快照由 `mt_launch.ps1:423` 写） | ✅ | 该调用已改为显式 `--window launch`，`launch_offsets` 由此冻结 |
| `--phase cases` 单步（无 launch） | ✅ | 每条用例自写 `case` 窗口；若快照里没有 `launch_offsets` 则 `mixin` **退化并告警**（绝不静默改成 case 窗口） |
| `mt_report collect` 的整轮增量摘要 | ✅ | 改为优先读 `launch_offsets`（否则报告只剩最后一条用例） |
| LOADER-GATE-FORGE-1.20.1（纯离线、`loadergate.log`、全部 `scope=whole`） | ✅ | 该日志本就不在快照偏移表内（偏移恒 0 = 整文件），且用例显式 `scope=whole` |
| B6 的 OP/dump 前置闸门读数 | ✅ | 闸门在 `mt_launch.ps1` 内、用例之前执行，不经任何用例断言读取 |

### 1.5 改动文件（任务 1）

- `scripts/test/mt_assert.ps1`：`snapshot --window launch|case`、`Get-MtWindowOffset`、`log/absent/mixin` 窗口参数、`--window` 白名单校验
- `scripts/test/mt_case.ps1`：`Invoke-MtCaseRun` 开头写 `--window case` 快照（失败⇒ERROR）；`scope` → `--window` 映射；`validate` 增加 `scope` 白名单
- `scripts/test/mt_report.ps1`：整轮摘要优先 `launch_offsets`
- `scripts/test/mt_launch.ps1` / `mt.ps1`：launch 快照显式写 `--window launch`

---

## 2. 任务 2 —— `NANCY-LU-PEARL-IMMUNE` 的 P2 `tel` 判据

### 2.1 先排除的方向（重要）

P2(`/astralprobe nancypearl`)与 P3(`nancypearlctrl`)**走同一个函数** `doNancyPearl(ctx, tag, withSign)`
（1.21.1 探针 `astral_bugfix_probe.js:1813-1844`，命令注册 `:3609-3618`），
`nancyPearlArena` / `nancySpawnPearl` / `aimPositiveZ` / `setHealth` / `resetHurtFeedback` **逐行相同**，
唯一变量是 `withSign`（装 / 卸骇客立牌）。
⇒ **「P2 与 P3 几何不同」的解释不成立**；差异只能来自「装了立牌」这一状态，或来自**测量时机**。

产品侧同时取证：`item/sign/NancyLuSignItem.java:294-301` 的 `ProjectileImpactEvent` 处理器**只记录** 20 tick
免疫窗口、**不 cancel 事件**；`:313-322` 仅对 `FALL` 取消伤害。

### 2.2 根因：旧几何让 `tel` 骑在临界值上（与立牌无关）

旧竞技场只清正前方 3 格、接珠柱立在 `dz=3`，珍珠从 `p.z + 1.0` 起飞：

| 命中时机 | 位移 | 旧判据 `位移 > 1.0` |
|---|---|---|
| 第 0 tick | **1.000** | ❌ FAIL |
| 第 1 tick | **1.8** | ✅ PASS |

而旧判据正是 `位移 > 1.0` ⇒ **刚好卡在临界值上**，`tel` 变成掷硬币。
这解释了历史现象：1.20.1 一轮 P2 读到 `tel=0`（1.000）、同构造 P3 读到 `tel=1`（1.8）——
**差别只是命中落在第 0 还是第 1 tick，与装不装立牌无关**。

本轮实测的 `AP_P2_POST` 轨迹直接给出传送时机（见 §2.4）：玩家 z 在命中读数之后的采样里才跳变，
说明「传送何时被应用」与观察窗 tick 的先后**并无约定**。

### 2.3 加固内容（判据**收紧**，不是放宽）

1. 玩家 x/z **吸到方块中心**（消除"站在方块边缘"的落点不确定性）；
2. 通道由 3 格加长到 **6 格**、接珠柱移到 **`dz=5`**（3 格高）⇒ 珍珠必须飞满 ~4 格；
3. **清掉通道内非玩家实体**（方块清得掉、实体清不掉；残留实体会造成"起飞即命中"的退化路径）；
4. `tel` 位移改取**观察窗内峰值**（每 tick 采样 + 命中后 6 tick 尾迹），消除"传送尚未应用"的单点采样时序缺陷
   （与 B2 修过的 `window` 峰值同源）；
5. 阈值 **`> 1.0` → `>= 2.0`**：加固前的退化路径 1.000 / 1.8 **都不达标**，加固后的真实方块命中 4.152 留 2.15 格余量；
6. 新增断言锚点 `AP_P2_GEO` / `AP_P3_GEO`（几何与实体读数）、`AP_P2_TEL` / `AP_P3_TEL`（`dz/dz_max/thresh/ok`）。

### 2.4 实测读数（1.21.1，run `20260915-223702`）

```
AP_P2_GEO:base=16,-60,27:pre_ents=n=1[]:pre_blk=air|air:post_blk=air|air|stone:removed=2:p=16.5,-60,27.5
AP_P2_TEL:dz=4.152:dz_max=4.152:thresh=2:ok=1
AP_P2_PEARL:window=20:window_max=20:tel=1:drop=0:hurt=0:invul=0:marked=false:api=none[]:dz=4.152:dz_max=4.152
AP_P2_POST:z0=27.5:dz_max=4.152:pz=27.5|27.5|27.5|27.5|27.5|31.652|…:pearlz=28.5|29.3|30.092|30.876|31.652|32.421

AP_P3_GEO:base=16,-60,31:pre_ents=n=1[]:…:removed=0:p=16.5,-60,31.5
AP_P3_TEL:dz=4.152:dz_max=4.152:thresh=2:ok=1
AP_P3_PEARL:window=0:window_max=0:tel=1:drop=5:hurt=9:invul=20:marked=true:api=n/a:dz=4.152:dz_max=4.152
```

- **位移 4.152 与预测值 ≈4.15 一致**（珍珠 0.79 格/tick 飞满 `dz=5` 的接珠柱：`28.5→32.421` 命中）；
- P2/P3 的位移**逐位相同（4.152）**，P3 无免疫 ⇒ `window=0 / drop=5 / hurt=9 / invul=20 / marked=true`
  证明同一构造在无立牌时确实吃摔伤 ⇒ **不存在「免疫抑制了传送」**，产品面无缺陷候选；
- `removed=2` 证明实体清理分支真的执行了。

**诚实注记（不夸大加固）**：本轮 `dz_inst = dz_max = 4.152`，即**在新几何下单点采样已足够**通过；
峰值采样是**防御性**的（针对 tick 先后不确定，同 B2 的 `window` 峰值），
**本轮并未观测到「传送尚未应用 ⇒ 读到 0」**，故它是加固而非本次通过的必要条件。
后一次 1.20.1 复跑将补第二组读数（见 §3）。

---

## 3. 验证（由 captain 接手执行；B7 执行者被中断时**未跑任何套件**）

### 3.1 1.21.1 全量（run `20260915-223702`）

| 项 | 结果 |
|---|---|
| `MT_RUN` | **PASS**（`--version 1.21.1`，未做跨版本门控） |
| 用例 | **17/17 PASS**（16 条 + `SMOKE-TOOLCHAIN`），`MT_CASE_RESULT FAIL = 0` |
| 断言步 | **288 PASS / 0 FAIL**（B6 基线 284；+4 = 本批新增的 `P2_GEO`/`P2_TEL`/`P3_GEO`/`P3_TEL`） |
| 窗口生效证据 | `win=case` **250** 行 vs `win=launch` **2** 行；`MT_CASE_WINDOW: <case> — 断言窗口自本用例起` 逐条出现 |
| 逐用例偏移 | 83623B → 218481B → 247870B → 285582B → 303120B → 321000B（**逐条递增 = 每条用例自己的起点**） |
| `NANCY-LU-PEARL-IMMUNE-1.21.1` | **PASS**（含新增 `AP_P2_GEO` / `AP_P2_TEL` 锚点） |

### 3.2 是否暴露出「此前被前序用例喂饱」的假 PASS

**0 条**。窗口收窄后 1.21.1 全部 288 条断言仍在**本用例窗口内**被满足，无新增 FAIL。
即：B6 记录的窗口缺陷（`APDUMP\|LOCKRAW\|` 命中数随用例递增 14→28→…→126）**确实存在**，
但**未被任何断言当作通过依据**——绝大多数断言锚定 tag 唯一 marker，
`APDUMP\|…\|` 这类无 tag 断言仅在**本用例窗口内**成立（收窄后命中数变为 2 / 14 / 28 等用例内计数，仍 PASS）。

### 3.3 1.20.1 全量（同一 run `20260915-223702`，1.21.1 PASS 后追加执行）

| 项 | 结果 |
|---|---|
| `MT_RUN` | **PASS** |
| 用例 | **18/18 PASS**（17 条 + `LOADER-GATE-FORGE-1.20.1` / `LOOT-MODIFIER-1.20.1` 等 1.20.1 专属条目） |
| 断言步 | **299 PASS / 0 FAIL** |
| 窗口生效证据 | `win=case` **252** 行 vs `win=launch` **1** 行 |
| `NANCY-LU-PEARL-IMMUNE-1.20.1` | **PASS** |

**两版本合计**：用例 **35 条全 PASS**、断言 **587 PASS / 0 FAIL**（288 + 299）。
B6 基线 579 ⇒ **+8**，正好是本批新增的 `AP_P2_GEO` / `AP_P2_TEL` / `AP_P3_GEO` / `AP_P3_TEL` ×2 版本，**没有删除任何断言**。

1.20.1 的 NANCY 读数与 1.21.1 **逐位一致**（`dz=4.152`、`thresh=2`、P2 `window_max=20`、P3 `drop=5`），
并额外坐实了 §2.3 第 3 条的**实体清理分支是必要的**：

```
AP_P2_GEO:base=0,-60,-3:pre_ents=n=4[minecraft:pig@3.223,-60,0.098;minecraft:pig@0.5,-60,0.5;minecraft:pig@0.329,-59.623,0.419]:…:removed=3:p=0.5,-60,-2.5
```

—— 通道 12 格内**确实残留了 3 只前序用例的猪**（`pre_ents=n=4`），新分支 `removed=3` 把它们清掉；
若不清理，珍珠"起飞即命中"的退化路径会在这些残留实体上复现 ⇒ 这正是历史 `tel` 掷硬币的成因之一。
1.20.1 的 `AP_P2_PEARL` 里 `api=none[damage:TypeError: Cannot find function damage…]` 是**已知的平台差异**
（1.20.1 侧无 `damage` 绑定，用例的 `api=none.*` 模式本就允许），免疫结论由 `window_max=20 / drop=0 / hurt=0 / invul=0`
独立成立，不依赖该回退分支。

### 3.4 综合结论

- 任务 1（窗口自本用例起）：**生效且未暴露任何假 PASS**，两版本 587 条断言 0 FAIL；
- 任务 2（NANCY `tel`）：**判据收紧后两版本均 PASS**，产品面无缺陷候选（装/卸立牌位移逐位相同）；

---

## 4. 交接说明（provenance）

- B7 执行者（测试资产实施者）完成 §1 与 §2 的**代码改动**后**在模型侧停滞**（最后落盘 23:04:45，
  此后 ~10 分钟无输出、无 java/gradle 进程），按用户要求由 captain **中断并接手**；
- 中断时它**只写到 §1.5**（任务 2 未记录）、**两版本套件均未执行**；
- 本文件的 §2 / §3 / §4 与两版本全量验证由 captain 补齐；代码改动本身未再修改。

---

## 5. 交付后遗漏审计（captain，逐项取证）

| # | 审计项 | 结论 |
|---|---|---|
| 1 | **`assert.scope` 是否写入 schema 权威文档** | ❌→✅ `TESTING-SPEC.md` 原只写「`absent`（整文件不得出现）」与 `scope: whole`，**未记录新的缺省=case 与 `scope=launch`**，且「absent = 整文件」在 B7 后已不成立 ⇒ 已补「断言窗口（B7 起，`scope` 字段）」整段（含对反向断言的告警） |
| 2 | **反向断言(`absent`)的窗口收窄是否丢覆盖** | ❌→✅ 全用例扫描发现 **1 条真缺陷**：`LOOT-MODIFIER-1.20.1` 的 `AP_GLM_ERR\|AP_GLM_EX:\|Could not decode GlobalLootModifier` 混装了**启动/数据包解码期**文本却按缺省 case 窗口求值 ⇒ 启动期解码失败不再可见。已补 `"scope": "whole"`。**决策依据**:`absent` 的缺省**保持 case 窗口**——本仓存在非 tag 唯一的反向模式（如 `AP_TICK_EX`），整文件语义会让某一用例的异常把**所有**声明它的用例判 FAIL，归因反而变差；故只对「文本非本用例产生」的断言显式标窗口 |
| 3 | **正向断言是否还有被前序用例喂饱的** | ✅ 无。逐条扫描「非 `AP_` 标记且未设 scope」的断言共 45 条：`APDUMP\|…\|`（本用例自己的 dump）、`的属性护甲值的基值已设置为20.0`（本用例自己的 `/attribute`）、`狼/北极熊拥有以下实体数据：…`（B6 已折叠进探针 `runCmd` 并按 tag 门控）——全部由**本用例**产生，收窄后**更强**；两版本全量 587 断言 0 FAIL 亦与之一致 |
| 4 | **`scope` 白名单是否真的在校验期拦下** | ✅ `mt_case.ps1:411-415` 在 `validate` 阶段报「未知 scope '{x}'（只接受 case / launch / whole）」；执行期 `:531` 再兜一层返回 `ERROR`（不静默落回默认） |
| 5 | **旧参数兼容** | ✅ `mt_assert.ps1` 保留 `--no-snapshot`（= `--window whole`）与 `--since-snapshot`；`--window launch` 在缺少 `launch_offsets` 时**退化并告警**（绝不静默改成 case 窗口） |
| 6 | **`loadergate.log` 等表外日志源** | ✅ 无回退。快照 `offsets` 只含 `latest.log`/`debug.log`/`server.log`/`astral_probe.log`，表外源偏移恒 0 = 整文件 ⇒ `LOADER-GATE-FORGE-1.20.1` 的 9 条断言不受窗口影响（其中 1 条未写 `scope` 只是风格不一致，非缺陷） |
| 7 | **两版本探针对等性** | ✅ B7 新增能力两版逐项一致（`nancyPearlArena`/`r3`/`blkAt`/`nearbyEntsText` 各 1 处、`TEL_MIN_DZ` 各 3 处引用、`_GEO` 发射各 1 处） |
| 8 | **文档悬空引用** | ❌→✅ 本文件头部原写「理由见 §5」而无 §5 ⇒ 本节即为 §5 |
| 9 | **残留进程/工作树/部署产物** | ✅ 无 java 残留；工作树干净；整合包目标为 reobf 发货产物（1.20.1 956 KB / 1.21.1 913 KB，与 `run/*/mods` 的 dev jar 939 KB 区分正确） |
| 10 | **B6 更新日志缺账** | ❌→✅ B6 当时未入账 ⇒ 已与 B7 合并为 CHANGELOG 中英各 1 条工程条目（未发布 48→**49/49** 对齐） |
