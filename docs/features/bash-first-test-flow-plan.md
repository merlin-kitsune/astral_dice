# 测试台 bash 优先化：编排层落地与 PowerShell 淘汰路线

> 状态：P1 已落地（2026-11-01）。契约正本仍是 `docs/features/windows-channel-decoupling-spec.md`；
> 本文只回答「谁接管默认路径」与「pwsh 怎么一步步退场」。
> 行数一律 `wc -l` 实测（2026-11-01，工作树 `D:\MCProject\astral_dice_multiloader`），不做估算。

## 0. 一句话结论

- **默认路径改由 bash 编排**：`bash scripts/test/win/mt.sh …` 与 `pwsh -File scripts/test/mt.ps1 …` 的
  开关 / 阶段 / 退出码 / 机器行等价；`cases`、`report`、`preflight` 三轮过渡期仍委托 pwsh，但每次委托都打
  一行 `MT_DEPRECATED: pwsh <script> —— 计划在 <批次> 迁移到 bash`。
- **淘汰是逐步的**：本轮**没有删除任何 pwsh 文件**（零删除 ⇒ 回滚成本为零，改文档推荐命令即可退回）。
- 剩余 pwsh 面：**31 个 `.ps1` / 18,548 行 + 8 个 `.psm1` / 6,136 行 = 39 文件 / 24,684 行**。

## 1. 现状盘点（实测）

计数口径：任务卡里的「19 个 `.ps1` + 6 个 `.psm1`」是**根级 `scripts/test/mt*.ps1` + `scripts/test/lib/*.psm1`**的口径；
全域实测见下表 —— 多出来的是 `tools/`（2）、`fabric/`（10）、`fabric/lib/`（1）。

| | 文件数 | 行数 |
|---|---|---|
| `scripts/test/**/*.ps1` | 31 | 18,548 |
| `scripts/test/**/*.psm1` | 8 | 6,136 |
| 合计 | 39 | 24,684 |

本轮新建的 bash 编排层（对照口径）：

| 文件 | 行数 | 角色 |
|---|---|---|
| `scripts/test/win/mt.sh` | 316 | CLI / 编排（闸门、单阶段、全流程、门控） |
| `scripts/test/win/lib/phases.sh` | 449 | 阶段实现（env/build/launch/cases/report/stop/preflight + 委托层） |

### 1.1 逐文件盘点（每行 = 一个待迁移或待保留的 PowerShell 资产）

「bash 目标模块」里的 `win/lib/*.sh` 都是**待新建**文件（本轮只建 `mt.sh` 与 `lib/phases.sh`）。

#### 根级入口与阶段脚本（19 个 / 15,502 行）

| 文件 | 行数 | 现职责 | bash 目标模块 | 批次 | 前置条件 | 验收判据 |
|---|---|---|---|---|---|---|
| `scripts/test/mt.ps1` | 960 | 测试唯一入口；闸门 / 阶段编排 / 全流程门控 | `win/mt.sh` | **P1（已落地）** | 无（纯参数与编排） | `bash -n` 通过 + 六条契约验证（见 §5）；`--help` 文案覆盖全部开关 |
| `scripts/test/mt_assert.ps1` | 1372 | 断言引擎（log / absent / source / DELEGATED 语义） | `win/lib/assert.sh` | P2 | `Mt.Phase` 机器行已由 `phases.sh` 接管 | 同一批日志与 pwsh 版逐条对照 **0 差异**（≥20 用例，含 PASS 与 FAIL 两侧） |
| `scripts/test/mt_preflight.ps1` | 763 | 阶段 P 前置检查 | `win/lib/preflight.sh` | P2 | `env.sh` 的只读检查族已覆盖一半 | `pwsh mt_preflight.ps1` 的每条 `CHECK:` 在 bash 版都有对应行，缺一即不通过 |
| `scripts/test/mt_scope.ps1` | 149 | 变更分级自查（只读、只建议） | `win/lib/scope.sh` | P2 | `git diff --name-only` 可读即可 | 对同一改动集输出同一 `非核心/核心` 结论 |
| `scripts/test/mt_loadergate.ps1` | 558 | `LOADER-GATE-FORGE` 用例的纯离线执行体 | `win/lib/loadergate.sh` | P2（可选） | 无游戏进程依赖 | 输出与其 `expected` 判定一致；离线可跑 |
| `scripts/test/tools/lp_offline_assert.ps1` | 41 | 离线断言复算工具 | 并入 `win/lib/assert.sh` 的 `--offline` | P2 | `assert.sh` 先存在 | 与 `mt_assert.ps1` 同判据同结论（含 .NET regex 差异清单） |
| `scripts/test/mt_case.ps1` | 2979 | 用例执行器（阶段 C，含原语 / 断言 / DELEGATED） | `win/lib/case.sh` | P3 | `win/lib/assert.sh`（P2）+ 注入臂 | 逐用例双跑对照：`PASS/FAIL/BLOCKED` 三态一致 |
| `scripts/test/mt_inject.ps1` | 950 | 游戏内命令注入臂 | `win/lib/inject.sh`（AutoJS 通道） | P3 | AutoJS 端点可用 | 同一条命令两种实现的**回执文本**一致 |
| `scripts/test/mt_gen_case.ps1` | 493 | 用例生成器（模板 → JSON） | `win/lib/gen_case.sh` | P3 | 用例 JSON schema 冻结 | 同模板产出**逐字节相同**的 JSON（含键序） |
| `scripts/test/mt_capture.ps1` | 482 | 截图采集与世代管理 | **不迁**（见 §3） | — | — | — |
| `scripts/test/mt_report.ps1` | 1016 | 证据收集与报告生成（阶段 R） | `win/lib/report.sh` | P4 | `case.sh` 已产出同结构证据 | `reports/<runId>/` 目录树与 `SUMMARY.md` 字段集一致 |
| `scripts/test/mt_env.ps1` | 2623 | 环境装配（mods / world / kubejs / 清场） | `win/env.sh`（扩展既有 416 行） | P4 | 现有 `env.sh` 的 mods 分支稳定跑过一轮 | `CHECK:` 行集合是 pwsh 版的超集；`mods` / `world` / `kubejs` 三个子命令各有对应 |
| `scripts/test/mt_cleanup.ps1` | 470 | 退出时的进程收停（唯一实现） | `win/lib/cleanup.sh` | P4 | `stop.sh` 白名单已验证 | 白名单条目集合逐条相等（对齐 `mt_cleanup.ps1:228-265`） |
| `scripts/test/mt_build.ps1` | 193 | Gradle 构建守护（阶段 B） | `win/lib/phases.sh` 的 `mt_phase_build` | P4 | 本轮已有命令构造 + dry-run | `:子项目:classes` 真跑一次 rc 一致；超时/守护处理补齐 |
| `scripts/test/mt_launch.ps1` | 1226 | 启动 runClient 并等进世界（含清场） | `win/launch.sh`（autojs 通道已覆盖） | P4（仅 autojs 通道） | `launch.sh` 的 `LAUNCH: READY` 已实测 | autojs 通道两实现同判据；`native` 通道**保留 pwsh**（§3） |
| `scripts/test/mt_stop.ps1` | 111 | 显式收停 | `win/stop.sh`（已覆盖） | P4 | 无 | `WIN_STOP:` 机器行同集合、退出码同表 |
| `scripts/test/mt_watchdog.ps1` | 465 | 停滞检测（跨进程采样） | `win/lib/watchdog.sh`（混合：CIM 仍走 pwsh） | P4 | 采样口径冻结 | 同一停滞场景下报警时刻误差 ≤1 个采样周期 |
| `scripts/test/mt_ime.ps1` | 427 | 目标窗口输入语言（键盘布局） | **不迁**（见 §3） | — | — | — |
| `scripts/test/mt_fire.ps1` | 159 | 「点火」：把耗时阶段脱离调用方 stdio 启动 | `win/lib/fire.sh` | P4 | `mt_phase_*` 的脱离式启动语义确定 | 返回即刻 + 终态日志落 `temp/mt_detached/` |
| `scripts/test/ref-repro.ps1` | 106 | `loose-ref` 删除隔离试验器 | `win/lib/ref_repro.sh` 或 python | P4（低优先） | 无 | 同一存档两个分支的产物清单一致 |

#### 工具与 fabric 线（12 个 `.ps1` / 3,046 行；fabric 线的 `.psm1` 见下一张表）

| 文件 | 行数 | 现职责 | bash 目标模块 | 批次 | 前置条件 | 验收判据 |
|---|---|---|---|---|---|---|
| `scripts/test/tools/publish_autojs_maven.ps1` | 294 | 把 AutoJS 测试库发布到 mavenLocal | 保留 pwsh（发布工具，非测试默认路径） | 不迁 | — | — |
| `scripts/test/fabric/ft.ps1` | 166 | fabric 线编排入口（含闸门） | 并入 `win/mt.sh` 的 `fabric-1.20.1` 分支 | FP | P1 全流程稳定 | 与 `ft.ps1` 同闸门文案、同阶段顺序 |
| `scripts/test/fabric/ft_env.ps1` | 363 | fabric 线环境装配（E） | `win/lib/fabric/env.sh` | FP | `env.sh` 的 fabric 分支已存在（`run/` 目录口径不同） | `CHECK:` 行集合是超集 |
| `scripts/test/fabric/ft_build.ps1` | 245 | fabric 构建 + 开包核对（B） | `win/lib/fabric/build.sh` | FP | 开包核对算法可移植（zip 读取） | 产物清单与 pwsh 版逐条相等 |
| `scripts/test/fabric/ft_launch.ps1` | 230 | fabric 客户端/服务端启动（L） | `win/launch.sh`（加 fabric 映射） | FP | `launch.sh` 支持第四条线 | `LAUNCH:` 机器行同集合 |
| `scripts/test/fabric/ft_case.ps1` | 329 | fabric 单条用例执行器（C） | `win/lib/fabric/case.sh` | FP | P3 的 `case.sh` 稳定 | 双跑三态一致 |
| `scripts/test/fabric/ft_assert.ps1` | 356 | fabric 日志断言（判定臂） | `win/lib/assert.sh`（共用） | FP | P2 的 `assert.sh` 稳定 | 同一日志 0 差异 |
| `scripts/test/fabric/ft_inject.ps1` | 295 | fabric 手动注入 | `win/lib/fabric/inject.sh` | FP | P3 的 inject 稳定 | 回执文本一致 |
| `scripts/test/fabric/ft_stop.ps1` | 192 | fabric 收停 | `win/stop.sh`（加 fabric 映射） | FP | 同 `stop.sh` 白名单 | `WIN_STOP:` 同集合 |
| `scripts/test/fabric/ft_prod.ps1` | 369 | 生产映射冒烟（真实整合包实例） | `win/lib/fabric/prod.sh` | FP | 生产实例路径可用 | 冒烟结论与 pwsh 版一致（同一实例两次） |
| `scripts/test/fabric/ft_dispatchreport.ps1` | 166 | 抽取 `LoaderBus#dispatchReport()` 并列 0 派发事件 | `win/lib/fabric/dispatch.sh` | FP | 日志格式稳定 | 0 派发清单逐条相等 |

#### 共享模块（8 个 / 6,136 行）
| 文件 | 行数 | 现职责 | bash 目标模块 | 批次 | 前置条件 | 验收判据 |
|---|---|---|---|---|---|---|
| `scripts/test/lib/Mt.Phase.psm1` | 218 | 退出码常量 + 机器行 + 耗时格式 | `win/lib/phases.sh`（**已被取代**） | P1（已落地） | 无 | 机器行格式逐字符对齐（`MT_<N>: OK (<elapsed>) — <detail>`） |
| `scripts/test/lib/Mt.Conf.psm1` | 57 | `mt.conf` 读取 | `win/lib/common.sh` 的 `mt_conf_*`（已覆盖） | P1（已落地） | 无 | 同一 conf 读出同值 |
| `scripts/test/lib/Mt.Paths.psm1` | 625 | 版本/路径/runId 解析 | `common.sh` + `phases.sh`（已覆盖主要函数） | P1→P4 收尾 | `$VERSIONS` 补 fabric（见 §6） | 四线版本表、`runId` 粘性语义一致 |
| `scripts/test/lib/Mt.AutoJs.psm1` | 837 | AutoJS MCP 探测与会话 | `win/lib/autojs.sh` | P2 | `curl` + JSON 解析可接受 | `mt_aj_probe` 与 pwsh 版对同一端点同结论 |
| `scripts/test/lib/Mt.Channel.psm1` | 864 | 通道状态机（autojs / native） | `win/lib/channel.sh`（**仅 autojs 段**） | P3 | — | autojs 段等价；native 段保留 pwsh |
| `scripts/test/lib/Mt.Proc.psm1` | 1512 | 进程查询 / 脱离式启动 / 杀树 | **保留 pwsh 作查询后端** | 不迁（§3） | — | — |
| `scripts/test/lib/Mt.Win32.psm1` | 1229 | user32 合成输入 / 窗口 / 键盘布局 | **保留 pwsh 作被调用端** | 不迁（§3） | — | — |
| `scripts/test/fabric/lib/Ft.Common.psm1` | 794 | fabric 线公共函数 | `win/lib/fabric/common.sh` | FP | 与 `common.sh` 的去重设计 | 函数面覆盖 fabric 线所需，无重复实现 |

## 2. 四批路线

批次划分的唯一标准是**依赖顺序**（谁能先独立验收就先走），不是行数。

### P1 编排层 —— 本轮，已落地

- 范围：`mt.ps1`（960）、`Mt.Phase.psm1`（218）、`Mt.Conf.psm1`（57）。
- 产出：`win/mt.sh`（316）+ `win/lib/phases.sh`（449）。`env` / `launch` / `stop` 直接调既有 `.sh`；
  `build` 只做命令构造与 dry-run（本轮不跑 gradle）；`cases` / `report` / `preflight` 过渡期委托 pwsh。
- 验收：`bash -n` 双脚本通过；`--help` / `--version` 校验 / 未知参数 / 缺值 / 非法通道 / 批量闸门 / 四阶段 dry-run
  全部实测（见本文 §5 与提交说明里的原始输出）。
- **回滚条件**：无需回滚代码 —— 没删也没改任何 pwsh 文件。若 bash 编排层出现契约偏差，把文档与交接里的推荐命令改回
  `pwsh -File scripts/test/mt.ps1` 即可，零迁移成本。
- 不可迁移项：无（编排层不含平台调用）。

### P2 只读取数与断言

- 范围：`mt_assert.ps1`（1372）、`mt_preflight.ps1`（763）、`mt_scope.ps1`（149）、
  `lp_offline_assert.ps1`（41）、`Mt.AutoJs.psm1`（837，探测段）、`mt_loadergate.ps1`（558，可选）。
- 产出：`win/lib/assert.sh`、`win/lib/preflight.sh`、`win/lib/scope.sh`、`win/lib/autojs.sh`。
- 前置条件：P1 的机器行与退出码已稳定（断言层只产出 `MT_<N>: OK|FAIL`，不碰编排）。
- 验收判据：**双跑差分** —— 取以往 run 落盘日志 ≥20 份（含应 PASS 与应 FAIL 两侧），
  bash 版与 pwsh 版逐条结论相同；正则差异必须显式记录（.NET `[regex]` vs POSIX/GNU grep）。
- **回滚条件**：任意一份日志上出现「bash 判 PASS / pwsh 判 FAIL」或反方向的**结论翻转**，或
  `absent` 语义（日志缺失 vs 模式缺失）判据不一致 ⇒ 立即回退到 `mt_assert.ps1`，不回退 P1。
- 不可迁移项：`mt_assert.ps1` 里若含 .NET 专有正则构造（lookbehind / 命名组 / `\p{L}`）需逐条换写，
  换写不了的用 `grep -P` 或落 python；不强行统一。

### P3 用例执行器

- 范围：`mt_case.ps1`（2979）、`mt_inject.ps1`（950）、`mt_gen_case.ps1`（493）、`Mt.Channel.psm1`（864，仅迁 autojs 段）。
- 产出：`win/lib/case.sh`、`win/lib/inject.sh`、`win/lib/gen_case.sh`、`win/channel.sh`。
- 前置条件：P2 的 `assert.sh` 稳定；`MT_DEPRECATED` 行能让「哪些步骤还委派 pwsh」在输出里可枚举。
- 验收判据：逐用例双跑三态一致（`PASS` / `FAIL` / `BLOCKED`）；`DELEGATED` 步骤的**委派边界**必须与 pwsh 版
  逐条相同（哪些步骤仍下沉给 pwsh / python 要能 grep 出来）。
- **回滚条件**：任一用例三态翻转；或 `--case-timeout` 语义从「单步」变成「整条」；或委派边界变化导致
  机器行数量变化 ⇒ 回退 `mt_case.ps1`。
- 不可迁移项：`mt_ime.ps1`（427，键盘布局）、`mt_capture.ps1`（482，GDI 截图）虽是 C 阶段依赖，但**不在本批迁**，
  继续由 pwsh 提供原子操作（invoke 式，不参与编排）。

### P4 收尾与基础设施

- 范围：`mt_report.ps1`（1016）、`mt_env.ps1`（2623）、`mt_cleanup.ps1`（470）、`mt_build.ps1`（193）、
  `mt_launch.ps1`（1226，仅 autojs 通道）、`mt_stop.ps1`（111）、`mt_watchdog.ps1`（465）、`mt_fire.ps1`（159）、
  `ref-repro.ps1`（106）、`Mt.Paths.psm1`（625 收尾）。
- 产出：`win/lib/report.sh`、`win/env.sh` 扩展（mods/world/kubejs）、`win/lib/cleanup.sh`、
  `win/lib/watchdog.sh`、`win/lib/fire.sh`。
- 前置条件：P2+P3 完成（报告要消费它们的证据；env 要被 launch 稳定调用）。
- 验收判据：`reports/<runId>/` 目录树与 `SUMMARY.md` 字段集**不变**（下游按字段读，不按行数读）；
  `env.sh` 的 `CHECK:` 行集合是 pwsh 版超集；`mt_cleanup.ps1:228-265` 白名单逐条相等。
- **回滚条件**：报告字段集变化或 `SUMMARY.md` 解析失败 ⇒ 停止 P4 并回退到 pwsh report；
  `env.sh` 出现 pwsh 版能过而 bash 版 FAIL 的检查项 ⇒ 该检查项回退 pwsh 子调用（混合模式），不整体回滚。
- 不可迁移项：见 §3。

### FP（平行轨）fabric 线

- 范围：`fabric/` 10 个 `.ps1`（2,711）+ `fabric/lib/Ft.Common.psm1`（794）= 3,505 行。
- 为什么单列：fabric 线的目录口径、启动产物、断言面都与三条主线不同（自包含子项目），
  在 `win/mt.sh` 里它只是第四个版本名。FP 复用 P2/P3 产出的 `assert.sh` / `case.sh`，因此必须排在 P3 之后。
- 验收判据与回滚条件与 P2/P3/P4 同族（双跑对照 + 三态一致 + 报告字段集不变）。

## 3. 不可迁移项（硬结论）

| 资产 | 行数 | 为什么 bash 迁不了 | 结论 |
|---|---|---|---|
| `scripts/test/lib/Mt.Win32.psm1` | 1229 | user32 合成输入（`SendInput` / `SetForegroundWindow` / 键盘布局 / 窗口枚举）属 Win32 API，bash 无等价实现，也不是「调个 exe 就能顶」的事（时序与焦点状态要自己维护） | **永久保留为被调用端**；替代路线是走 AutoJS 注入或 computer-use，不由 bash 直连 |
| `scripts/test/mt_ime.ps1` | 427 | 同上（输入语言 / 键盘布局） | 永久保留，invoke 式调用 |
| `scripts/test/mt_capture.ps1` | 482 | GDI 截图 + 世代管理 | 保留 pwsh（或改外部截图工具，单独评估，不在本路线内） |
| `scripts/test/lib/Mt.Proc.psm1` | 1512 | CIM/WMI 查询 + 脱离式 `Start-Process`；bash 侧只能靠 `pwsh`/`wmic`（后者已弃用） | 保留为 pwsh **查询后端**：bash 编排层通过 `pwsh -Command` 调它，允许长期存在 |
| `scripts/test/lib/Mt.Channel.psm1` 的 native 段 | （见左列整文件 864） | 窗口定位 + 合成输入（native 通道） | 保留；`launch --channel native` 在 bash 侧仍委派 `mt_launch.ps1` |
| `scripts/test/mt_launch.ps1` 的 native 分支 | — | 依赖上一条 | 保留（autojs 分支由 `launch.sh` 接管） |
| `scripts/test/tools/publish_autojs_maven.ps1` | 294 | 发布工具，不在测试默认路径 | 不在淘汰范围（要迁也归发布工具链，不归测试台） |

结论：**这不是「消灭 pwsh」，而是「默认路径不再经过 pwsh 编排」**。上面这些资产是平台能力的被调用端，
它们的存在不会让默认测试流程回到 pwsh 控制。

## 4. 淘汰四阶段（每个文件都要逐档走完）

| 阶段 | 动作 | 可观测判据（必须贴原始输出） |
|---|---|---|
| **S0 默认路径不再经过** | bash 侧换成自己的实现，pwsh 版仍可手动调用 | `grep -rn "<script>.ps1" scripts/test/win/*.sh` 仅剩注释或 `--legacy` 分支（默认分支 0 命中） |
| **S1 标记 deprecated** | 文件头加 `# DEPRECATED:`、`win/README.md` 与本文表格标注、运行期打 `MT_DEPRECATED:` 行 | `grep -c "MT_DEPRECATED" scripts/test/win/lib/phases.sh` 覆盖该脚本；跑一次能看到该行 |
| **S2 无默认引用** | 删除 bash 侧所有默认路径引用；保留的只允许是 §3 的被调用端 | `grep -rn "\.ps1" scripts/test/win/ --include='*.sh' \| wc -l` → **0**（或仅 §3 白名单条目） |
| **S3 物理删除** | `git rm` + CHANGELOG 记录 | 同时满足：S2 判据成立 ≥2 个发布周期；`git log --oneline -- <file>` 期间无回滚提交；`pwsh -File mt.ps1` 不再被任何文档/CI 引用（`grep -rn "mt.ps1" docs/ .github/ scripts/`） |

当前基线（2026-11-01 实测，用于将来对比）：`grep -rn '\.ps1' scripts/test/win/ | wc -l` = **59 行**，
分布在 `env.sh` / `launch.sh` / `stop.sh` / `lib/phases.sh` / `mt.sh` / `README.md` 六个文件里。
P1 阶段这个数字**不该下降**（过渡期委托必须显式可见），P4 收尾后才是 S2 的验收时点。

## 5. P1 的验收记录（六条，已实测）

| # | 命令 | 期望 | 实测 |
|---|---|---|---|
| 1 | `bash -n scripts/test/win/mt.sh scripts/test/win/lib/phases.sh` | rc=0 无输出 | rc=0 |
| 2 | `bash scripts/test/win/mt.sh --help \| head -25` | 打印用法块 | 通过（`usage()` = `sed -n '2,28p' "$0"`） |
| 3 | `bash scripts/test/win/mt.sh --version 1.21.1 --phase env` | 调 `win/env.sh`，`ENV_RESULT:` + rc=0 | `ENV_RESULT: WARN`（0 阻塞 / 4 提示），`MT_ENV: OK (5s)`，rc=0 |
| 4 | `bash scripts/test/win/mt.sh --version 1.21.1 --phase build --dry-run` | 只打印命令与判据 | `cmd //c "gradlew.bat :neoforge-1.21.1:classes"`，rc=0 |
| 5 | `bash scripts/test/win/mt.sh --phase cases --version 1.21.1` | 闸门拦下，rc=2 | `MT_AUTO_DISABLED: --phase cases 未指定 --case…`，rc=2 |
| 6 | `bash scripts/test/win/mt.sh --version 1.21.1 --phase launch --dry-run` | 只打印命令与判据 | `bash scripts/test/win/launch.sh --version 1.21.1`，rc=0 |

补充实测（契约边界）：未知版本 / 未知阶段 / 非法 `--channel` / 未知参数 / 缺值 → 全部 `MT_ERROR: …` + rc=2；
`--phase report` → 闸门 rc=2；`--phase stop --dry-run` → rc=0；`--allow-auto` 与 `MT_ALLOW_AUTO=1` 均能放行；
`--version 1.20.1 --phase build --dry-run` → `:forge-1.20.1:classes`（四线映射逐个可查）。

## 6. 已知不等价与风险（P1 → P4 全程有效）

1. **过渡期委托 pwsh 的退出码漂移（最高优先）**
   - `pwsh -File` 对 `exit N` 是忠实透传的，但**未捕获异常会折成 1**（bash 侧会当成 `FAIL(1)` 而不是 `ERROR(2)`），
     语法错误/参数错误才是 2。也就是说「脚本自己崩了」和「断言失败」在退出码上不可区分。
   - 缓解：`mt_phase_pwsh_file` 把 `127`（命令不存在）显式折成 `2`；其余原样透传，交给 `MT_DEPRECATED:` 行 +
     stderr 里的 `MT_ERROR:` 文案区分「崩了」还是「判定失败」。**不要**在 bash 侧把 pwsh 的 1 猜成 2。
   - `Ctrl-C`：bash 只退出编排层（`130`），不会替子进程收停游戏；pwsh 侧有 `cleanup-on-exit` 自动清档，P1 不做
     （P1 显式拒绝 `--cleanup-on-exit`，rc=2）。
2. **预算与看门狗缺失**：`mt.ps1` 有 `PhaseBudgets`（build 300 / env 240 / launch 180 基线 / cases 300 / report 90）、
   10 s 心跳、90 s STALL、`--run-timeout` 全局预算；P1 只打印预算数字，不掐表、不心跳。全流程 `--dry-run` 不受影响，
   真跑长流程时**没有**停滞检测。这是 P4 必须补齐的项（`watchdog.sh`）。
3. **`--run-timeout` / `--case-timeout` / `--new` / `--feature` / `--spec` / `--cleanup-on-exit` 在 P1 显式拒绝（rc=2）**。
   注意 `mt.ps1` 侧 `--run-timeout` 当前**恒 exit 2**（`MtNonNegativeIntPattern = '^\\d+$'` 双反斜杠导致永不匹配），
   所以两者在「不可用」这件事上是等价的，只是文案不同；`--case-timeout` 用环境变量 `MT_CASE_TIMEOUT_SEC` 替代。
4. **`launch` 的通道差异**：`win/launch.sh` 不接受 `--channel`，只有 AutoJS 就绪判定；`--channel native` 仍走
   `pwsh mt_launch.ps1` 并打 `MT_DEPRECATED`（P4，且大概率长期保留，见 §3）。
5. **版本表口径**：`Mt.Paths.psm1:78` 的 `$VERSIONS` 只有三条线（缺 `fabric-1.20.1`），bash 侧 `MT_ALL_VERSIONS` 是四线。
   因此 `pwsh mt.ps1 --version fabric-1.20.1` 会报「未知版本」，`bash mt.sh` 能过。这是**有意偏差**（P1 起四线一等公民），
   但要写进交接，避免被当成 bug。
6. **无 `--version` 的单阶段**：`mt.ps1` 对三条线顺序跑，bash 对四条线跑。该路径平时被闸门拦下；`--allow-auto` 下可达，
   差异表现为多跑 fabric 线。
7. **`.mt_active_run` 的粘性**：`runId` 由 pwsh 侧 `Get-MtActiveRunId` 写盘（`scripts/test/cases/.mt_active_run`，gitignored）。
   bash 侧只读；读不到时才请 pwsh 建；pwsh 也不可用才本地生成时间戳 —— 此时 `reports/<runId>` 可能与 pwsh 侧不同步。
8. **`win/README.md` 的边界声明已过时**：该文 §7 写「两边不互相调用 / mt.ps1 不需要知道本目录存在」。
   P1 之后 bash 反向编排 pwsh，这句话在「bash → pwsh 委托」方向上是反的。README 不在本轮写域，
   需要在 P2 批次随同更新（列进 P2 前置条件）。

## 7. 下一步（建议顺序）

1. P2 开批前先把 `win/README.md` §7 的边界声明改掉（否则文档与现实互相矛盾，后来者会按旧文档写代码）。
2. P2 先做 `mt_assert.ps1`（1372）——它是**唯一**同时被 cases / report 消费的判定臂，先迁它，P3/P4 的对照成本最低。
3. P3 开批时同步冻结 `DELEGATED` 步骤清单（哪些步骤仍下沉 pwsh / python），否则「淘汰完成」没有可观测定义。
4. 全流程预算与看门狗（risk §6.2）不要拖到 P4 末尾 —— 它是真跑长流程的安全网。

## 8. 用户裁决（2026-11-01 追加，优先级高于上文）

1. **PowerShell 全局禁用 ⇒ `scripts/test/mt.ps1` 视为废弃**。它的缺陷（含 `mt.ps1:172` 的 `'^\\d+$'` 双反斜杠使所有 timeout 开关恒 exit 2）**不再修复**；`mt.sh` 是唯一编排入口，`mt.ps1` 只作 P2–P4 的对照参照物，不再演进。
2. **默认测试流程转 bash 控制、逐步淘汰所有 PowerShell** —— P1 已完成的那条路不变，但**不要再往 pwsh 侧加功能**，只做等价替换 + 删除。
3. **实机暂不动**：`launch.sh` 的 AutoJS 主判据与 1.20.1 dev jar 的能力验证推迟，本阶段一律只做 `--dry-run` / 只读体检。
4. **`win/README.md` 边界声明已修**（2026-11-01，主会话）：头部边界、§5 用法、§7 分工表全部改为「`mt.sh` 为默认编排入口、单向委托 pwsh」，§6.8 与 §7.1 的前置条件已消解。
5. **CRLF**：目标是把 pwsh 资产统一为 CRLF。2026-11-01 实测本机**两条独立阻断**（已反馈用户）：
   - 本会话 bash/pwsh/cmd 子进程对工作区**根目录以外的任何路径**都无写权限（连 `.git/objects` 也写不了，`git hash-object -w` 报 `insufficient permission`）；用户对 `scripts|run|docs` 补的 `xmace:(OI)(CI)M` 不改变结果 —— 子进程是受限令牌，写判定走受限 SID 列表；
   - DSH 文件工具（Write/Edit）能写子目录，但**运输层吃掉 CR**：写入 `A\r\nB\r\nC\r\n` 落盘后 `od -c` = `A \n B \n C \n`；`edit` 的 `old_string`/`new_string` 只差 CR 会被判成「必须不同」。
   ⇒ 结论：CRLF 转换只能由**用户自己的终端**执行（字节安全做法：`ReadAllBytes` → 规范化 → `WriteAllText` 配 `UTF8Encoding($false)`），或接受 pwsh 资产保持 LF（`core.autocrlf=true` 下提交后 checkout 本就还原 CRLF，`git diff` 无换行噪声）。
