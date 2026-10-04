# B6 — 测试流程提速与 `/astralparty dump` 接入（① 删冗余重试 ② 折叠原版命令 ③ dump 读数 ④ OP 前置 ⑤ `--version` 尊重）

- 执行者：测试资产实施者（**只动 `scripts/test/**`**）
- 仓库：`F:\MCProject\astral_dice_multiloader`，分支 `multi-1.20.1-1.21.1`，起始 HEAD `4e1581c`（工作树干净）
- 唯一依据（只读审计，可直接采信）：`docs/batch3/TEST-CMD-audit.md`
- 卡死护栏：任一步 / 任一用例 **> 6 分钟无新输出** ⇒ 立即 `mt.ps1 --phase stop --force`，记录已完成到哪一条并停下报告
- 本文件为**增量落盘**：每完成一项立即写

---

## 0. 改造前基线（实测自 `HEAD` 的用例文件，非估算）

| 指标 | 改造前 | 改造后 | 变化 |
|---|---|---|---|
| 用例数 | 35 | 35 | 0 |
| 步骤数 | 428 | 368 | **−60** |
| `inject_command` 次数 | **163** | **125** | **−38** |
| `wait` 步数 / 纯等待秒数 | 168 / 155.6 s | 146 / 145.8 s | −22 / **−9.8 s** |
| 断言总数 | **515** | **579** | **+64**（只增不减） |

> 审计给出的 153 次注入是更早时点的计数；本次实测基线为 **163**（`9d0ea49`/`4e1581c` 两批新增用例之后）。
> 其中「冗余重试」仍是审计点名的 **16 次**（8 对），原版命令仍是 **22 次**（gamerule 8 / fill 8 / effect clear 4 / data get 2），两者合计 **38 次 = 本次删除的全部注入**。

**耗时模型**（审计口径，单次 `inject_command` ≥ **2.65 s** = 键盘序列 2050 ms + `mt_case.ps1:583` 后置 600 ms）：

```
省下 = 38 × 2.65 s + 9.8 s(删掉的 wait) = 100.7 + 9.8 ≈ 110.5 s ≈ 1 m 50 s
新增 = 进入世界后的 OP/ dump 前置闸门：探针命令注入 ×2 ≈ 5.3 s + 1.6 s 等待 ≈ 6.9 s（每版本一次）
净省 ≈ 103.6 s / 版本（≈ 审计模型 602 s 的 17%）
```

---

## 1. ⑤ `mt.ps1` 全流程分支忽略 `--version`（已修，待实跑验证）

**缺陷（实测代码路径）**：`mt.ps1` 的**全流程**分支无条件 `foreach ($v in @(Get-MtVersions))`，并把 1.20.1 挂在 `$gateOpen` 上 ⇒
`mt.ps1 --version 1.20.1` 会**先跑 1.21.1**；1.21.1 一旦不通过，1.20.1 立刻被记成 `GATED` 而**根本没跑** —— 与 `--version` 语义完全相反。

**修法**（`scripts/test/mt.ps1`）：

| 位置 | 改动 |
|---|---|
| `mt.ps1:373-374` | 新增 `$flowVersions = if ($Version) { @($Version) } else { @(Get-MtVersions) }` 与 `$multiVersion = -not [bool]$Version` |
| `mt.ps1:383` | preflight 标记只针对 `$flowVersions`（不再无条件给两个版本打 PASS） |
| `mt.ps1:390` | 全流程遍历改为 `$flowVersions` |
| `mt.ps1:395` | 跨版本门控加 `$multiVersion -and`（单版本时不存在"上一个版本"） |
| `mt.ps1:467` | 结束语按单/多版本分别措辞 |
| 文件头 `.DESCRIPTION`/`.EXAMPLE` | 补三条 `--version` 语义说明与示例 |
| `mt.ps1` ×4 阶段 mark | BLOCKED 单列（配合 ④，前置欠缺不再显示成 FAIL） |
| `mt_report.ps1:Invoke-MtReportSummary` | 单版本运行时不把**未执行**的另一个版本判成「未执行 → 总览 FAIL」（否则 `--version` 单跑必然自判失败）；状态文件里出现几个版本就汇总几个 |

---

## 2. ④ OP 前置只读断言（已落地，待实跑取读数）

**探针侧**（`astral_bugfix_probe.js`，两版本同源施加）：新增只读命令 `/astralprobe opprobe`，打印**实测值**：

```
AP_OP_PERM:has2=<0|1>:level=<n>:src=<cmdsource[+profile|+list]|none>:dump=<rc>
```

- `has2` 的判据与 `/astralparty` **完全相同**：玩家命令源上的 `CommandSourceStack#hasPermission(2)`
  （1.21.1 `CommandSourceStack.java:390` / 1.20.1 `:174`），也就是命令注册时 `requires(AstralPartyCommand::hasPermission)` 用的同一个入口；
- `level` 数值级经 `MinecraftServer#getProfilePermissions(GameProfile)`（1.21.1 `:1759` / 1.20.1 `:1516`）取，只为把"为什么够/不够"写清楚；
- `dump` 字段在同一读数里回报 `/astralparty dump` 的返回值（第二道闸门，见 §3.4）。

**工具链侧**（`mt_launch.ps1`）：新增 `Invoke-MtLaunchOpPreflight`，在**进入世界之后、任何用例之前**执行（`mt_preflight.ps1` 的契约是"绝不触达游戏"，而 `hasPermissions(2)` 只能由游戏内玩家给出；这一段就是 cases 阶段的前置闸门）：

| 实测 | 结论 | 退出码 |
|---|---|---|
| `has2=1` 且 `dump=rc=1` | `MT_INFO: PREFLIGHT_OP: OK — …` | 0（继续） |
| `has2=0`（权限级 < 2） | `MT_preflight-op: BLOCKED — …` | `MT_EXIT_BLOCKED`(11) |
| 取不到 `AP_OP_PERM` 读数（探针未加载 / 命令不存在） | `MT_preflight-op: ERROR — …` | `MT_EXIT_ERROR`(2) |
| `dump != rc=1` | `MT_preflight-op: ERROR — …` | `MT_EXIT_ERROR`(2) |

**红线遵守**：`requires` 门槛一字未动（仍是 `hasPermission(2)`）；**没有**加测试专用开关/配置项/客户端旁路；`opprobe` 本身**只读**。
**已知覆盖缺口（沿用审计 §4.6 的如实记录）**：单人 quickplay 测试环境恒为 level 4，构造不出「非 OP 玩家」⇒ `hasPermission(2)` 的**否定面**仍无法在 `scripts/test/` 覆盖，只能靠代码审查背书。

---

## 3. ① 删冗余重试注入（16 次）+ ② 折叠 22 次原版命令（逐条）

### 3.1 ① 逐条核实：为什么这 16 次确实是冗余

审计点名的冗余是 `railgunfriendlyread` / `railgunfriendlyend` 的「每相位注入两次」。逐条核实结论：

| 用例（两版本各一份） | 重复注入 | 核实依据 |
|---|---|---|
| `HOSTILE-TARGET-NEUTRAL`（tag `HN`） | `railgunfriendlyread HN` ×2、`railgunfriendlyend HN` ×2 | `doRailgunFriendlyRead` 通篇只有 `rghp()` 读数与 `send()`，**不写任何状态**（幂等）；`doRailgunFriendlyEnd` 第二次进入时 `rgfState` 已被置 `null`（`probe` 内 `rgfState = null;`），只剩"再清一次场 + 再报一次 RESTORE/DONE"，**无增量副作用** |
| `RAILGUN-AOE-SCOPE`（`RG`） | 同上 | 同上 |
| `RAILGUN-OVERRIDE-CLASS`（`OC`） | 同上 | 同上 |
| `RAILGUN-PET-EXCLUDE`（`PE`） | 同上 | 同上 |

⇒ **8 对 × 2 = 16 次注入**，全部属「同一命令重复注入、且第一次必然已生效」。
**为什么"第一次必然生效"**：重复的动机是 NOTE 写的「冷启动后前若干次注入偶发丢失」，而这两个相位位于该用例的**第 4～7 次注入之后**（冷启动期早已过去），且 `mt_inject.ps1` 在每次注入前都做 `Esc→Tab→Enter` 状态归一化 + 强制前台（`:497-502`、`Assert-MtInjectForeground`）——冷启动丢注入的前提（界面未收敛/窗口未获焦）此时已不成立。
**验证方式**：删掉后实跑该 4 条用例（见 §6），断言数不变且全绿。

### 3.2 ② 折叠清单（22 次 → 0 次注入）

| # | 原版命令 | 次数 | 折叠到 | 省 |
|---|---|---|---|---|
| 1 | `/gamerule doFireTick false` | 8（4 用例 × 2 版本） | `doRailgunFriendly` 开头 `runCmd` | 8 × 2.65 s ≈ **21.2 s** |
| 2 | `/fill ~-8 ~-1 ~-6 ~8 ~-1 ~14 minecraft:stone` | 8 | 同上 | 8 × 2.65 s ≈ **21.2 s** |
| 3 | `/effect clear @s` | 4（`EFFECT-DECAY-FLICKER` ×2、`FEN-SPLASH` ×2） | `doDecayClear` 开头 / `doFenSplash` 末尾 | 4 × 2.65 s ≈ **10.6 s** |
| 4 | `/data get entity @e[type=minecraft:wolf,limit=1] Owner` | 2（`RAILGUN-PET-EXCLUDE` ×2） | `doRailgunFriendlyRead`，**仅 tag=PE** | 2 × 2.65 s ≈ **5.3 s** |
| | **合计** | **22** | | **≈ 58.3 s** |

两处**必须**写明的技术判断（都决定成败）：

1. **`data get` 折叠可行**（有既成事实背书）：`performPrefixedCommand` **不抑制输出**，`data get` 的回显会进聊天栏 → 落 `latest.log`。本仓探针上游已有同样的既成事实：`doRailgunFriendly` 里的 `runCmd(ctx, "data get entity @e[type=minecraft:polar_bear,limit=1] AngerTime")`（1.21.1 `:2211`）产出的 `北极熊拥有以下实体数据：1200` 在 `reports/20260915-091733/1.20.1/latest.log` 中可见 4 次 ⇒ 折叠 `Owner` 回读后断言文本 `狼拥有以下实体数据：\[I;` 一字未改即可继续命中。
2. **`data get` 只在 `tag === "PE"` 时跑**：断言窗口是「自 launch 快照起的 `latest.log` 增量」、**跨用例共享**（`mt_assert.ps1` 的 `Get-MtSnapFor` 只读 launch 时写下的固定偏移）。若四条 railgun 用例都跑这条回读，按名字序先跑的 `railgun-aoe` / `railgun-override` 会先把 PET-EXCLUDE 的断言**喂饱** ⇒ 等于把该断言弱化成"任何一轮都能过"。故按 tag 收口。
3. **`/effect clear @s` 在 FEN-SPLASH 必须放在 `doFenSplash` 末尾**：`performPrefixedCommand` 在 1.21.1 上会被推迟到本 tick 末执行（探针多处实测注释）。若放进命中相位开头，清除会晚于 `meleeHit` ⇒ 赐福不会重新触发（用例 NOTE 记录的实测现象：`recharge` 停在 5、`in_range=0`）。放在末尾则被用例原有的 1500 ms 等待完全吸收。

### 3.3 用例侧的步骤变化

| 用例（×2 版本） | 删除的步骤 | 注入数变化 |
|---|---|---|
| `HOSTILE-TARGET-NEUTRAL` / `RAILGUN-AOE-SCOPE` / `RAILGUN-OVERRIDE-CLASS` | `inject gamerule`+`wait`+`inject fill`+`wait`；第 2 次 `…read`；第 2 次 `…end` | −4 |
| `RAILGUN-PET-EXCLUDE` | 同上 + `inject /data get … Owner`+`wait` | −5 |
| `EFFECT-DECAY-FLICKER` | `inject /effect clear @s`+`wait` | −1 |
| `FEN-SPLASH-MAIN-TARGET` | `inject /effect clear @s`+`wait` | −1 |

---

## 4. ③ 只读探针命令改走 `/astralparty dump`（红线遵守 + 诚实偏差）

### 4.1 探针侧新增

| 符号 | 作用 |
|---|---|
| `dumpState(ctx, tag)` | 调 `runCmd(ctx, "astralparty dump")`；成功落 `AP_<tag>_DUMP:rc=1`，**失败另落 `AP_<tag>_DUMP_ERR:`**（用例对其做 `absent` 断言 ⇒ 落 FAIL，绝不静默降级） |
| `/astralprobe dumpstate <tag>` | `dumpState` 的命令入口（任何用例都可直接取 dump 原始值） |
| `/astralprobe opprobe` | §2 的只读 OP 断言 + dump 可用性 |

6 个纯只读命令**全部**接上 `dumpState`：`komachiread`(K5)、`nancystate`(N3/N6)、`airbagread`(A)、`railgunfriendlyread`(HN/RG/OC/PE)、`railtruedmgread`、`fensplashread`(FS)。

### 4.2 断言侧（新增 64 条，全部是 `log`/`absent`，无一条放宽）

- 每条 railgun 用例：`AP_<tag>_PREP:rule=rc=…:fill=rc=…`、`AP_<tag>_DUMP:rc=1`、`APDUMP|LOCKRAW|`、`APDUMP|SIGN|sign_active_cooldown_end=`、absent 追加 `AP_<tag>_DUMP_ERR`（PE 另加 `AP_PE_OWNER:rc=`）；
- `KOMACHI-EXTRA-PLAY`：`AP_K5_DUMP:rc=1` + **四条 `LOCKRAW` 原始值锚点**（`effect_card_bonus_plays=0` / `effect_card_play_count=0` / `effect_card_cooldown_end=0` / `max_allowed=1`）+ absent `AP_K5_DUMP_ERR`；
- `NANCY-LU-CLOAK`：`AP_N3_DUMP:rc=1`、`AP_N6_DUMP:rc=1`、`APDUMP|LOCKRAW|`、`APDUMP|SIGN|sign_active_lock_sign=`、absent 追加 `AP_N3_DUMP_ERR|AP_N6_DUMP_ERR`；
- `AIRBAG-BYPASS-KILL`：`AP_A_DUMP:rc=1`、`APDUMP|LOCKRAW|`、absent 追加 `AP_A_DUMP_ERR`；
- `EFFECT-DECAY-FLICKER`：`AP_X0_CLEAR:rc=…`；`FEN-SPLASH`：`AP_FS_CLEAR:rc=…`、`AP_FS_DUMP:rc=1`、`APDUMP|LOCKRAW|`、absent 追加 `AP_FS_DUMP_ERR`。

**红线核对**：
1. `dump` **只用于读数** —— `dumpState` 自身不写任何状态；被测动作（真实出牌 / `registerPlay` / `tick` / `meleeHit` / 各相位生产入口）一字未动；
2. 断言**只锚定原始值组** `LOCKRAW` / `SIGN` / `EFFECTS` / `PENDING`；**没有**任何断言落在 `LOCKDERIVED` 或 `SIGN|is_sign_active_locked`（行尾 `|assert=forbidden`）；
3. **没有**用 `dump` 顶替任何**被测动作**，也没有删掉任何真实玩法路径的覆盖（断言 515 → 579，只增不减）；
4. 命令缺失/失败 ⇒ `AP_<tag>_DUMP_ERR` ⇒ 用例 FAIL；launch 阶段另有第二道闸门（`dump != rc=1` ⇒ ERROR）。

### 4.3 ⚠️ 诚实偏差：审计的「省 120–180 行 JS」达不到，原因有二（**不粉饰**）

1. **6 个纯只读命令里有 4 个读的是原版实体血量差值**（`railgunfriendlyread` 6 只靶、`railtruedmgread` 2 只、`fensplashread` 4 只；另 `airbagread` 读健康/装备/充能、`nancystate` 读 `hidden_until`/`vis`/`bonus`）。
   `dump` 的能力边界（`AstralPartyCommand` javadoc 明确写死）是「**只**输出本模组自身状态，**不**输出血量、经验、背包、原版或其它模组的效果」⇒ 这些读数**不可能**由 `dump` 提供。
2. **断言窗口跨用例共享，未带 tag 的 `APDUMP|` 行无法归属到某条用例**（`mt_assert.ps1` 的 `Get-MtSnapFor` 只读 launch 时写下的固定偏移，逐用例不再重取快照）。
   ⇒ 若拿通用 `APDUMP|` 行**顶替** tag 唯一的既有断言（如 `AP_K5_READ`），前序用例的 dump 就能把它"喂饱"，**等于弱化断言** —— 违反"禁止弱化断言"。故 `dumpState` 采取**追加**而非替换。

**实际行数变化**：两版本探针各 **+145 / +155 行**（新增 3 个只读辅助/命令 + 折叠的 prep 与注释；`doKomachiRead` 的旧读数**保留**）。
**产品/工具链缺陷候选（只列不改）**：`scripts/test/mt_assert.ps1:280-285`（`Invoke-MtAssertLog` 取固定 launch 偏移）⇒ 全部 `log` 断言窗口是「自 launch 起」而非「自本用例起」，任何**不带 tag 唯一标识**的标记都可被前序用例满足（本批实测已在上游造成 `北极熊拥有以下实体数据：1200` 在 4 条 railgun 用例中各命中一次）。最小复现：在 `RAILGUN-AOE-SCOPE` 之后单独跑 `HOSTILE-TARGET-NEUTRAL`，把 `doRailgunFriendly` 的 `ANGER_NBT` 回读注释掉，该断言仍会因前一条用例的输出而 PASS。

---

## 5. ⑥ 超时机制 / 看门狗（追加任务）

### 5.1 先修根因：`Start-Process -Wait` 等整棵进程树 ⇒ 全流程卡在 build 阶段

**这是本轮三位执行者卡死的直接根因，实测复现（不是推测）**：

| 时点 | 观测 |
|---|---|
| 22:07:38 | `mt.ps1 --version 1.21.1` 全流程启动（此前刚跑过 `--phase stop --force`，**Gradle 守护已被杀**） |
| 22:07:43 | `MT_BUILD: OK (4s)` —— 构建完成 |
| 22:07:43 → 22:13（≈5.3 min） | **再无任何输出**；`pwsh` 进程树里**没有** `mt_env.ps1` / `mt_report.ps1` 子进程；`gradle` 守护 `pid=24780`（父进程 `24008` 已退出）**仍活着**；`.mt_run_state.json` 里只有 `preflight` |

**机制**：`mt.ps1` 的 `Invoke-MtChild` 用 `Start-Process … -NoNewWindow -PassThru -Wait`，而
`-Wait` 等的是**整棵进程树**（本文件早已在 `launch` 阶段记录过同一个坑，见 `mt.ps1` 的 A1 注释与
`docs/batch3/B1-in-game-results.md` ①）。`mt_build.ps1` 会让 `gradlew` **新起一个 Gradle 守护**，
它是 `mt_build` 子进程的后代 ⇒ 子进程退出后 `-Wait` 仍在等守护。
**触发条件（为什么以前没炸）**：只有在**守护不在场**时本次构建才会新起守护 ⇒
`--phase stop --force` 之后的第一次全流程**必然**卡住；守护本来就活着时（复用既有守护）不触发。
这解释了"为什么三位执行者都在同一段卡死"。

**修法**（`scripts/test/mt.ps1` 的 `Invoke-MtChild` 非脱离式分支）：去掉 `-Wait`，改为
`-PassThru` + 轮询 `HasExited`（不跟踪后代）+ `$TimeoutSec` 上限；超时只
`Stop-Process` 该子进程自身（**绝不** taskkill 进程树，避免误伤 Minecraft 客户端或用户其它 java）。
**实测验证**：同一命令（`mt.ps1 --version 1.21.1`，此前停在 `MT_BUILD: OK`）修后顺利
`build → env → launch → cases`（见 §6）。

### 5.2 ⑥-1 单条用例硬超时

- `mt_case.ps1` 新增 `$script:CaseTimeoutSec`（默认 **300 s**），覆写：环境变量 `MT_CASE_TIMEOUT_SEC`、CLI `--case-timeout <秒>`（`0` = 关闭）。
- 超时路径：**跳过剩余步骤** + 该条记**新状态 `TIMEOUT`**（`MT_CASE_TIMEOUT: <id> — 第 i/N 步前已超过 Ns 硬超时`）+ **继续跑下一条**。
- 判据优先级：`ERROR > TIMEOUT > FAIL > BLOCKED > PASS`；`MT_CASE_RESULT: <id> = TIMEOUT`；
  `MT_CASES_SUMMARY` 原样列出 `NAME=TIMEOUT`；`run-dir` 返回**独立退出码 12**（`MT_EXIT_TIMEOUT`，新增于 `lib/Mt.Phase.psm1`）。
- **不留挂起子进程**：两层收口 —— ① 步骤循环每步前核对 deadline；② `Invoke-MtCaseChild` 把**剩余预算**折算成子进程超时（`Get-MtCaseStepBudget`，下限 5 s / 上限 600 s），`Invoke-MtProcessFull` 超时会**强杀该子进程树**。
- `mt_report` 汇总把 TIMEOUT 单列（`⏱ TIMEOUT（NAME=TIMEOUT）`），不再与 FAIL 混同。
- `TIMEOUT` **不**置位 `.mt_keep_alive`（否则 `mt_cleanup` 会拒绝收停、整套流程再无人清理）。

### 5.3 ⑥-2 全局运行超时

- `mt.ps1 --run-timeout <秒>`（默认 **0 = 不限**；环境变量 `MT_RUN_TIMEOUT_SEC` 可覆写默认值）。
- 在「进入每个版本」与「cases 跑完」两处核对预算；超时 → `Invoke-MtRunTimeoutStop`：
  跑 `mt_stop.ps1 --force`（唯一收停实现）→ 报告记 `TIMEOUT` → **退出码 12**（与 `FAIL=1` 区分）。

### 5.4 ⑥-3 看门狗 `scripts/test/mt_watchdog.ps1`

参数：`-Version` / `-StallSeconds`（默认 360）/ `-PollSeconds`（默认 10）/ `-Action report|stop`（默认 report）/ `-MaxSeconds`（观察窗上限，0 = 一直看）。
参数归一化去前导 `-` 并**删掉全部内部连字符** ⇒ `-StallSeconds` 与 `--stall-seconds` 两种拼法都吃下（实测两种都通）。

| 项 | 实测 |
|---|---|
| 进展信号 | `cases/.mt_run_state.json`、`cases/.mt_active_run`、`reports/<run_id>/<版本>/**`、`run/<版本>/logs/latest.log`、`run/<版本>/runclient_launch.log`、`run/<版本>/logs/kubejs/server.log`（后三者按大小 + mtime） |
| 心跳 | `MT_WATCHDOG: ALIVE t=<秒> last_signal=<文件@时间>（停滞 <秒>）` 每 `-PollSeconds` 一行 |
| 停滞 | `MT_WATCHDOG: STALL` + 最后进展信号 + `latest.log` / `kubejs/server.log` / `runclient_launch.log` 尾部 |
| 退出码 | **42** = 检出停滞；0 = 观察窗内始终有进展；2 = 参数错误 |
| 安全 | 收停只经 `mt.ps1 --phase stop --force`；脚本内**没有任何** taskkill |

文档：`scripts/test/TESTING-SPEC.md` 新增 **§12「超时机制与看门狗」**（三层超时的默认值与覆写、"必须由 watchdog 包裹或至少设 `MT_CASE_TIMEOUT_SEC`"的纪律、`TIMEOUT`/`STALL` 的处置），并在 §10 追加两条实测坑（22 `-Wait` 进程树、23 超时是独立结论）；§3 阶段表补 `--version` 语义与 launch 的 OP/dump 闸门。

---

## 7. 实跑验证（全部实测，2026-09-15 22:16–22:42）

### 7.1 全量回归：两版本各一次全流程，**全部 PASS**

| 轮 | 命令 | 退出码 | 结论 | 条目 | 断言步 |
|---|---|---|---|---|---|
| 1.21.1 | `mt.ps1 --version 1.21.1` | **0** | `MT_VERSION_VERDICT: 1.21.1 = PASS` / `MT_RUN: PASS — 1.21.1 通过（--version 指定单版本，未执行跨版本门控）` | 17 执行（16 版本内 + `SMOKE-TOOLCHAIN`）/ 18 SKIP | **284**（`LOG:PASS 230` / `ABSENT:PASS 20` / `KUBEJS:PASS 16` / `CRASH:PASS 16` / `MIXIN:PASS 2`），**断言级 FAIL = 0** |
| 1.20.1 | `mt.ps1 --version 1.20.1` | **0** | `MT_VERSION_VERDICT: 1.20.1 = PASS` / `MT_RUN: PASS — 1.20.1 通过（--version 指定单版本，未执行跨版本门控）` | 18 执行 / 17 SKIP | **295**（`LOG:PASS 240` / `ABSENT:PASS 20` / `KUBEJS:PASS 17` / `CRASH:PASS 17` / `MIXIN:PASS 1`），**断言级 FAIL = 0** |

- **断言总数 = 284 + 295 = 579**，等于静态断言计数 579，**高于基线 515（+64）**；变化全部来自本批**新增**锚点（清单见 §3.2 / §4.2），**没有删除任何一条既有断言**。
- 合并总览 `reports/20260915-221655/SUMMARY.md`：两个版本各 `✅ PASS`，综合 `✅ 全部通过`（另存 `temp/b6_goodrun_SUMMARY.md`）。
- 逐条结论：`MT_CASES_SUMMARY` 里 1.21.1 的 17 条 = PASS、1.20.1 的 18 条 = PASS，其余为面向另一版本的 `SKIP`。**无 FAIL / ERROR / TIMEOUT / BLOCKED**。
- **无产品级 FAIL**：本批 0 条用例失败、0 条断言失败 ⇒ **未发现新的产品缺陷**。

### 7.2 ①②③ 的落地证据（逐条命中）

| 证据 | 实测 |
|---|---|
| ② 折叠的 `gamerule`+`fill` 真的跑了 | `AP_HN_PREP:rule=rc=(undefined\|[0-9]+):fill=rc=(undefined\|[0-9]+)` 与 `AP_RG_PREP` / `AP_OC_PREP` / `AP_PE_PREP` **各命中 1 次**（两版本两轮均 PASS） |
| ② 折叠的 `/effect clear @s` 真的跑了 | `AP_X0_CLEAR:rc=`（`EFFECT-DECAY-FLICKER`）、`AP_FS_CLEAR:rc=`（`FEN-SPLASH`）各命中 1 次；两用例的 `_STATE` / `_VERDICT` 断言**语义与文本不变**（`in_range=1:out_range=1:true_damage=1` 等仍全绿） |
| ② 折叠的 `/data get … Owner` 回显仍在 | `AP_PE_OWNER:rc=` 命中 1 次，且原有断言 `狼拥有以下实体数据：\[I;` 命中（`RAILGUN-PET-EXCLUDE` PASS） |
| ① 删掉 16 次重复注入后仍全绿 | `HOSTILE-TARGET-NEUTRAL` / `RAILGUN-AOE-SCOPE` / `RAILGUN-OVERRIDE-CLASS` / `RAILGUN-PET-EXCLUDE` 四用例 × 2 版本**全部 PASS**（`_RESTORE:creative`、`_AFTER`、`_VERDICT` 断言全中） |
| ③ dump 真的产出机器行 | `APDUMP\|LOCKRAW\|` 在 1.21.1 轮**命中 14/28/42/…/126 次**（随用例累积 —— 见 §4.3 的窗口共享说明）；dump 由 5 个只读相位各调一次 |
| ③ tag 唯一的调用标记 | 1.21.1：`AP_A_DUMP:rc=undefined`；1.20.1：`dump=rc=1`（跨版本差异见 §7.4）；两版本断言均 PASS |
| ③ KOMACHI 的原始值锚点 | `APDUMP\|LOCKRAW\|effect_card_bonus_plays=0` / `effect_card_play_count=0` / `effect_card_cooldown_end=0` / `max_allowed=1` **各命中 8 次**；两条既有 tag 唯一断言（`AP_K5_READ:extra=0:count=0:cd=0:max=1`、`AP_K5_CLEARED:1`）**仍 PASS** |

### 7.3 ④ OP 前置实测读数

```
MT_INFO: PREFLIGHT_OP: hasPermissions(2)=1 level=4 src=cmdsource+profile required=2
MT_INFO: ASTRALPARTY_DUMP: rc=rc=undefined lockraw_rows=28     # 1.21.1
MT_INFO: ASTRALPARTY_DUMP: rc=rc=1          lockraw_rows=28     # 1.20.1
MT_INFO: PREFLIGHT_OP: OK — has2=1 level=4 src=cmdsource+profile dump=…
```

- **实测 `hasPermissions(2)=1`、权限级 `level=4`**，与审计 §4.3/4.4 的预测**完全一致**（单人 quickplay 集成服 `isSingleplayerOwner` 分支 ⇒ `return 4`）。
- 判据来源 `src=cmdsource+profile`：①`CommandSourceStack#hasPermission(2)`（与 `/astralparty` **同一入口**）②`MinecraftServer#getProfilePermissions`（数值级）。
- **BLOCKED 语义**：`has2 != 1` ⇒ `MT_preflight-op: BLOCKED (…s) — 实测 hasPermissions(2)=0（权限级 level=0…）` + 退出码 **11**，用例阶段**不执行**；探针/dump 不可用则记 **ERROR**（退出码 2）。二者都在**用例之前**收口。
- 本节**没有**改动任何 `requires` 门槛、**没有**测试专用开关/后门、**没有**客户端旁路。

### 7.4 跨版本实测坑（已在代码与文档中固化）

| 现象 | 实测 | 处置 |
|---|---|---|
| `performPrefixedCommand` 返回值跨版本不一致 | 1.21.1 = `undefined`（命令其实执行了）；1.20.1 = `1`/`2` | `dumpState` **不用**「期望 rc=1」判成功，改用「解析失败面 `rc=0` / 探针 `ERR:`」；**成功证据是 `APDUMP\|` 原始值行本身**。用例断言改成 `rc=`（版本容忍）；`mt_launch` 的闸门改用 `lockraw_rows>=1` |
| 断言窗口跨用例共享 ⇒ `APDUMP\|` 行可被前序用例"喂饱" | `APDUMP\|LOCKRAW\|` 命中次数随用例递增：14 → 28 → 42 → … → 126 | `dumpState` 一律**追加**锚点，不顶替 tag 唯一的既有断言；已列为工具链缺陷候选（§4.3） |
| `Start-Process -Wait` 等整棵进程树 | 见 §5.1（全流程卡在 build，三位执行者的卡死根因） | `Invoke-MtChild` 改为轮询 `HasExited` + 超时，只终止该子进程自身 |

### 7.5 耗时对比（尽可能实测；模型估算已明确标注）

**A. 结构性指标（精确，来自用例文件 diff）**

| 指标 | 改造前 (HEAD `4e1581c`) | 改造后 | 变化 |
|---|---|---|---|
| `inject_command` 次数（两版本合计） | 163 | **125** | **−38** |
| 步骤数 | 428 | 368 | −60 |
| `wait` 步数 / 纯等待秒 | 168 / 155.6 s | 146 / 145.8 s | −22 / **−9.8 s** |
| 断言总数 | 515 | **579** | **+64** |

**B. 模型估算**（审计口径：单次注入 ≥ 2.65 s = 键盘序列 2050 ms + `mt_case.ps1:583` 后置 600 ms）

```
每版本省下 = 19 次注入 × 2.65 s + 删掉的 wait ≈ 50.4 s + ≈5 s ≈ 55 s
两版本合计 ≈ 110 s（与审计 §2.4/§3.4 的 16×2.65 + 22×2.65 ≈ 100 s 同量级；差额来自本批实际 38 次）
新增成本  = 进入世界后的 OP/dump 前置闸门：探针注入 ×2 ≈ 5.3 s + 1.6 s ≈ 6.9 s/版本
⇒ 每版本净省 ≈ 48 s（≈ 审计 602 s 模型的 8%）；两版本 ≈ 96 s
```

**C. 实测（cases 相位跨度 = `latest.log` 里首条 `AP_` 行 → 末条 `AP_` 行）**

| 版本 | 改造前 | 改造后 | 变化 |
|---|---|---|---|
| 1.21.1 | **571.2 s**（`reports/20260915-091733/1.21.1/latest.log`，18:54:47→19:04:19） | **429.1 s**（`reports/20260915-221655/1.21.1/latest.log`，22:17:50→22:24:59） | **−142.1 s（−24.9%）** |
| 1.20.1 | **482.6 s**（`reports/20260915-091733/1.20.1/latest.log`，21:20:19→21:28:22） | **439.0 s**（`reports/20260915-221655/1.20.1/latest.log`，22:27:27→22:34:46） | **−43.6 s（−9.0%）** |

> ⚠️ **诚实标注（实测口径的局限）**：上表"改造前"取自本机**现存的上一轮全流程日志**，其**资产修订点与 `4e1581c` 不完全相同**（1.21.1 那份是 19:04 的较早修订），且同机 GPU/碎片化噪声不可控。因此：
> · **1.20.1 的 −43.6 s 与模型值（≈48 s）吻合**，可信度最高（该份 before 日志与 `4e1581c` 同批）；
> · **1.21.1 的 −142 s 明显大于模型值**，差值应归因于资产修订差异与机器波动，**不作为提速结论**；
> · 确定性结论只有 A 表（−38 次注入 / −9.8 s 纯等待）与 B 表的模型值。

**D. 全流程墙钟（参考）**：1.21.1 `22:16:55 → ≈22:26`（≈9 min：build 3 s + env 世界重建 + launch 47 s + cases 429 s + 报告）；1.20.1 `22:27:0x → 22:34:57`（≈8 min，env 走 1.20.1 种子包快恢复）。

### 7.6 ⑤ `--version` 实跑验证

```
$ pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.20.1
########## MT VERSION: 1.20.1 ##########
MT_BUILD: OK (4s) — … astral_dice-1.2.1+forge_1.20.1.jar
MT_WORLD: OK — 种子快恢复（…）        # 1.20.1 走自己的种子包；未跑 1.21.1 的 mt_env
… 18 条用例全 PASS …
MT_VERSION_VERDICT: 1.20.1 = PASS
MT_RUN: PASS — 1.20.1 通过（--version 指定单版本，未执行跨版本门控）
```

- **`MT VERSION: 1.21.1` 出现 0 次**（`Select-String` 计数 = 0）⇒ **确实只跑了 1.20.1**。
- **`GATED` 出现 0 次**（唯一 1 次命中是无关断言的字段名 `gated_clear`）⇒ 未做跨版本门控。
- 对照：改造前该命令会**先跑 1.21.1**（`foreach ($v in @(Get-MtVersions))` 无条件），1.21.1 一失败就把 1.20.1 记 `GATED`。**改前行为由 `git diff scripts/test/mt.ps1` 直接可见**（一行 `foreach` + 一行 `$gateOpen`）；为避免再耗一轮全流程，未重跑改前版本。

### 7.7 ⑥ 超时 / 看门狗的故意停滞验证

**演示 A —— watchdog 停滞（`-Action report`，无客户端在跑）：**

```
$ pwsh -File scripts/test/mt_watchdog.ps1 -Version 1.20.1 -StallSeconds 20 -PollSeconds 5 -Action report
MT_WATCHDOG: ALIVE t=5s  last_signal=runclient_launch.log@22:34:56（停滞 5s）
MT_WATCHDOG: ALIVE t=20s last_signal=runclient_launch.log@22:34:56（停滞 20s）
MT_WATCHDOG: STALL — 已 20s 无任何进展（阈值 20s）
MT_WATCHDOG: STALL last_signal=runclient_launch.log@22:34:56
===== 诊断尾部 =====   … latest.log / kubejs/server.log / runclient_launch.log 末 N 行 …
退出码=42   实际耗时=20.5s
```
**怎么造的**：让机器处于"没有任何测试流程在跑"的状态（所有进展信号恒定）。**观察到**：`MT_WATCHDOG: STALL` + 最后进展信号 + 三个日志的尾部诊断，**退出码 42**，20.5 s 即返回，**没有挂死**。

**演示 B —— 单条用例硬超时（`MT_CASE_TIMEOUT_SEC=8`，客户端已就绪）：**

```
$ $env:MT_CASE_TIMEOUT_SEC='8'; pwsh -File scripts/test/mt_case.ps1 run-dir --version 1.21.1
MT_CASE_TIMEOUT: AIRBAG-BYPASS-KILL-1.21.1 — 第 5/28 步前已超过 8s 硬超时，跳过剩余步骤
MT_CASE_TIMEOUT: ANVIL-STAR-UPGRADE-1.21.1 — 第 5/56 步前已超过 8s 硬超时，跳过剩余步骤
… （16 条 1.21.1 用例逐条 TIMEOUT；1.20.1 的 18 条 SKIP）…
MT_CASES_TIMEOUT: 16 条用例硬超时（8s/条上限，MT_CASE_TIMEOUT_SEC 可覆写）
退出码=12   实际耗时=155.1s
```
**怎么造的**：把单条用例预算临时压到 **8 s**（远小于任一用例的真实耗时 ≈20–30 s），按正常流程跑 `run-dir`。**观察到**：① 每条用例都产出 `MT_CASE_RESULT: <id> = TIMEOUT`（**16 条**）与 `MT_CASE_TIMEOUT: … 第 i/N 步前已超过 8s`；② **逐条继续、整体 155 s 跑完全部 17 条，没有挂死**；③ 退出码 **12**（与 `FAIL=1` 区分）；④ 汇总里 `TIMEOUT` 与 `PASS/SKIP` 并列可见。

**演示 C —— watchdog `-Action stop`（客户端空闲 ⇒ STALL ⇒ 自动收停）：**

```
$ pwsh -File scripts/test/mt_watchdog.ps1 -Version 1.21.1 -StallSeconds 25 -PollSeconds 5 -Action stop
MT_WATCHDOG: STALL — 已 26s 无任何进展（阈值 25s）
MT_CLEANUP: 已收停 1 个本流程进程，Gradle 守护已停止
退出码=42   耗时=27.4s   java(before)=3 → java(after)=0
```
**观察到**：停滞检出后**只经 `mt.ps1 --phase stop --force`** 收停（`MT_CLEANUP` 输出即其内部实现），客户端与 Gradle 守护双双退出，**收尾干净**（`java` 进程 3 → 0）；退出码 42。

### 7.8 边界与未覆盖项（如实）

1. **改造前的耗时只有"上一轮现存日志"可用**（见 §7.5-C 标注），不是同资产同机的 A/B 对照；A 表与 B 表才是确定性结论。
2. **`hasPermission(2)` 的否定面**（非 OP 应被拒）在单人 quickplay 环境**无法覆盖** —— 只有一个恒为 level 4 的玩家（沿用审计 §4.6 的已记缺口，不用后门伪装成已覆盖）。
3. **`APDUMP\|` 行无法按用例归属**（窗口共享）⇒ 它对"raw value 存在性"是有效锚点；"该用例自己的状态"仍由 tag 唯一的 `AP_<tag>_DUMP` 与 `AP_<tag>_READ` 背书。
4. 演示 B 的 8 s 预算是**故意的实验设置**，不代表默认值；默认仍为 **300 s**。
5. 本批**未触发卡死护栏**：唯一一次「>6 分钟无新输出」出现在 §5.1 的 build 挂死（22:07:43→22:13，约 5.3 min 时主动 `--phase stop --force` 收停），根因已修复且修后两轮全流程各约 8–9 min 正常结束（看门狗全程心跳可见）。

