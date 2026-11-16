# AGENTS-full.md 测试台章节重写计划（B 档）

> 状态：**待执行**（A 档已于 2026-11-05 落盘并通过守门）
> 上游裁决：用户 2026-11-05「先按A，同时B列入计划」
> 目标文件：`docs/agent/AGENTS-full.md`（当前 **4,255 行 / 257 个标题 / 全 CRLF**）
> 关联：`docs/agent/AGENTS-dsh.md`（红线区镜像）、`docs/agent/verify-split.py`（守门）、`docs/agent/AGENTS-TOC.md`（标题总表）

## 0 一句话

A 档把能机械替换的（T1 四处对抗、T3 十六个已换语言的脚本名、6 处横幅）就地改完并**锁死 4,255 行**；B 档要把整章「自动化测试流程」**按真实现行 bash/python 测试台重写**，同时解决「TOC 无生成器 + 已失真 46 行 / 缺 66 个标题」这个前置问题 —— 否则重写后行号一漂，TOC 就彻底废了。

## 1 范围与不变量

| 不变量 | 内容 | 守门手段 |
|---|---|---|
| I1 | `AGENTS-full.md` **L2–L129**（生效红线区）逐字不变 | `python -B docs/agent/verify-split.py` ⇒ `全部通过` |
| I2 | `AGENTS-dsh.md` 红线区 = FULL L2–L129 的镜像，两者只能同步改 | 同上（脚本比对 14,523 字符） |
| I3 | 行尾：FULL 全 **CRLF**（裸 LF 0、双 CR 0）；DSH 纯 LF | `python` 数 \r\n 与裸 \n |
| I4 | 不引入新的 旧 shell 依赖；不把已删除的 `旧脚本` 写成「现行」 | `grep` 判据见 §8 |
| I5 | **B 完成后行数不再锁死**（解锁条件 = §6 生成器落地并重算 TOC） | §8-1 |

## 2 事实基线（2026-11-05 实测）

### 2.1 章节几何（L2359–L2962，20 个标题）

```
2359 ## 自动化测试流程（Automated Testing）— 必须遵守（子配置） ← A 档已加横幅
2362 ### 测试资产清零与重建（2026-09-20 用户指令清零；此后按批次重建）
2397 ### 变更分级与验证口径（2026-09-19 用户裁决）— 必须遵守
2424 ### 测试顺序（必须遵守）
2436 ### 测试前清场（Pre-Test Field Cleanup）— 必须遵守
2456 ### 测试后收尾（Post-Test Teardown）— 必须遵守
2482 ### 工具链（scripts/test/…） ← A 档已加横幅
2485 #### 脚本职责 ← 17 行表全 旧 shell
2512 #### 调用顺序 ← 调用树全 旧脚本
2533 #### 两条跨语言约定（改脚本前必读）
2573 #### 退出清理（避免进程泄漏）
2612 ### 阶段 P — 前置检查
2653 ### 阶段 B — 构建
2666 ### 阶段 E — 环境装配
2695 ### 阶段 L — 启动与就绪
2713 ### 全局测试规则（测试环境硬性要求，2026-09-17 用户裁决）
2745 ### 阶段 C — 测试条目
2811 ### 阶段 R — 报告与结论
2829 ### 截图识别规则（必须遵守）
2836 ### 桌面驱动通道（2026-10-18 换血）
2855 ### 环境与兼容栈速查
2873 ### 新增内容 / 用户指定内容的测试（子技能）
2895 ### 注意事项
2911 ### 测试流程超时与看门狗（Test Timeout & Watchdog）— 必须遵守
2963 ## NeoForge 上游 BUG 补丁（neoforge_fixes）— 必须遵守 ← 章外，不动
```

### 2.2 残留 旧 shell 引用（A 档后）

- 全文件 `旧 shell|旧 shell|旧 shell|\旧脚本|\旧脚本模块` 命中 **188 处 / 58 个不同路径**（A 档前 223 处 / 86 路径）。
- 命中集中在前 10 个路径（旧测试台编排、阶段库、fabric 专用台与部署脚本）。
- **判定**：残余 **全部属于 T2（本章重写范围）与 T4（无等价物，需裁决）**；A 档已把 T3 可机械替换项清零。唯一漏项：`scripts/verify/verify_chip_recipes.py`（L1182，已有同名 `.py`）⇒ B 顺手改。

### 2.3 现行 bash/python 测试台（读自实物，13,412 行）

| 层 | 文件 | 行数 |
|---|---|---|
| 编排 | `scripts/test/win/mt.sh` | 350 |
| 阶段 | `scripts/test/win/env.sh` / `launch.sh` / `stop.sh` / `live.sh` | 485 / 509 / 398 / 345 |
| lib | `scripts/test/win/lib/common.sh` / `phases.sh` / `fabric.sh` | 639 / 438 / 952 |
| python 大件 | `scripts/test/mt_env.py` / `mt_inject.py` / `mt_assert.py` / `mt_report.py` / `mt_capture.py` / `mt_preflight.py` / `mt_cleanup.py` / `mt_gen_case.py` | 1212 / 974 / 945 / 969 / 687 / 586 / 422 / 330 |
| python lib | `scripts/test/lib/mt_case.py` / `mt_compat.py` / `mt_autojs.py` / `mt_channel.py` / `mt_ime.py` / `mt_ps.py` / `mt_paths.py` | 970 / 851 / 374 / 170 / 370 / 307 / 129 |

### 2.4 TOC 已失真（B 的前置问题）

- `docs/agent/AGENTS-TOC.md` 表头自述「当前 **3,665 行 / 191 个标题**」；实际 **4,255 行 / 257 个标题** ⇒ 缺 **66 个标题**。
- 抽查三处行号漂移：TOC `L2567` `### 阶段 P — 前置检查` ⇒ 实际 **2612（+45）**；TOC `L3553` `### 2. 工具映射（现行）` ⇒ 实际 **3599（+46）**；TOC `L3525` `## 🧰 代码审查工具链更新` ⇒ 实际 **3571（+46）**。
- 仓库内 **没有** TOC 生成器（`grep -rln AGENTS-TOC tools scripts docs .github` = 0 命中）⇒ 历次都是手算，这才是行号必须锁死的真实原因。

## 3 逐节处置表

| 行段（现） | 标题 | 处置 | 依据 |
|---|---|---|---|
| L2359–2361 | 章标题 + 横幅 | **保留横幅**，正文改写为「本章 = bash/python 测试台唯一口径」 | I4 |
| L2362–2396 | 测试资产清零与重建 | **保留**（策略不变，仅把 `bash scripts/test/win/mt.sh` 命令换成 `mt.sh`） | 内容仍有效 |
| L2397–2423 | 变更分级与验证口径 | **保留**（纯策略，无脚本名） | 抽查 |
| L2424–2435 | 测试顺序 | **保留** | 纯流程 |
| L2436–2455 | 测试前清场 | **改写命令**（`bash scripts/test/win/mt.sh --phase env clean` ⇒ `bash scripts/test/win/mt.sh --phase env` 或 `mt_cleanup.py`） | T2 |
| L2456–2481 | 测试后收尾 | **改写命令**（三行收尾 ⇒ `--phase stop --force --purge-saves`；`MT_STOP` ⇒ `WIN_STOP`） | `lib/common.sh:156` |
| L2482–2484 | 工具链小节标题 | **重写导语**（现行入口 + 三层分工） | §2.3 |
| L2485–2511 | 脚本职责（17 行表） | **整表重写**：旧 shell 实现名 → bash/python 实现名（见 §4） | T2 核心 |
| L2512–2532 | 调用顺序（调用树） | **整树重写**（`bash scripts/test/win/mt.sh` → `mt.sh` → `lib/phases.sh` → `*.sh` / `mt_*.py`） | T2 核心 |
| L2533–2572 | 两条跨语言约定 | **改写**：(1) `旧脚本模块` 模块约定 ⇒ 作废；(2) 保留「machine line / exit code 契约」但改由 bash/python 承载 | 契约仍有效 |
| L2573–2611 | 退出清理 | **保留骨架 + 换实现**（`旧件「mt.proc」` ⇒ `lib/common.sh` 的 pid/端口回收） | T2 |
| L2612–2652 | 阶段 P 前置检查 | **改写命令**（`mt_preflight.py`，A 档已换名，仍需换参数说明） | T3 后续 |
| L2653–2665 | 阶段 B 构建 | **改写命令**（`bash scripts/test/win/mt.sh --phase build [--dry-run]`） | T2 |
| L2666–2694 | 阶段 E 环境装配 | **改写命令**（`env.sh`；fabric 双 run 目录 `--side`） | T2 |
| L2695–2712 | 阶段 L 启动与就绪 | **改写命令** + **明示能力缺口**（`--channel native` = BLOCKED 11，只认 autojs/kubejs） | `phases.sh:264-269` |
| L2713–2744 | 全局测试规则 | **保留**（硬性要求） | 抽查 |
| L2745–2810 | 阶段 C 测试条目 | **改写命令**（`mt_case.py` + `--run-dir` 必填差异；fabric 走 bash 原生解析） | `phases.sh:329-352` |
| L2811–2828 | 阶段 R 报告 | **改写命令**（`mt_report.py collect --version … --verdict …`） | T2 |
| L2829–2835 | 截图识别规则 | **保留**（`Get-MtCurrentShots` ⇒ python 等价函数，需点名） | T2 残余 |
| L2836–2854 | 桌面驱动通道 | **重写**（2026-10-18 那版是 旧 shell 窗口合成；现行 = AutoJS HTTP 注入 + `mt_ps.py`/`mt_ime.py`） | T4 裁决点 |
| L2855–2872 | 环境与兼容栈速查 | **保留**（版本表与语言无关，但需核 26.1.2 sodium 0.9.2 等钉版） | 实读 |
| L2873–2894 | 新增内容测试（子技能） | **保留** | 抽查 |
| L2895–2910 | 注意事项 | **改写**（含 `旧 shell` 提及） | T2 |
| L2911–2962 | 超时与看门狗 | **重写**（`bash scripts/test/win/mt.sh --phase stop --verify` 无等价物 ⇒ 要么 bash 落地，要么降级为「靠 `timeoutMs` + `--phase` 预算」） | T4 裁决点 |

**T4 两个裁决点**（B 执行前必须拍板，否则只能写「暂无等价实现」）：

1. `bash scripts/test/win/mt.sh --phase stop --verify` / `旧件「mt_scope」` / `旧件「mt_loadergate」` / `旧件「chipcommon」` / `旧件「repair-loose-refs」` —— **补 python 等价物**，还是**在文档里登记为「已退役、无替代」**？
2. 桌面驱动 `--channel native` —— **移植到 python**（user32 合成输入），还是**永久改为 BLOCKED**（只走 AutoJS）？

## 4 旧 → 新映射（B 执行时直接用）

| 旧件侧（已删除） | 新（bash / python） |
|---|---|
| 旧件「mt」 --version X --phase env | `bash scripts/test/win/mt.sh --version X --phase env` |
| `--phase build` / `launch` / `stop` | 同形，`mt.sh` 转发到 `win/{env,launch,stop}.sh` |
| `--phase cases --case <json>` | `mt.sh --phase cases --case <json>`（转发 `lib/mt_case.py`，`--run-dir` 显式给） |
| `--phase report` | `mt.sh --phase report` 或 `python scripts/test/mt_report.py collect …` |
| 旧件「mt_preflight」 --version X | `python scripts/test/mt_preflight.py --version X`（A 档已换名） |
| 旧件「mt_env」 world --timeout 900 | `bash scripts/test/win/env.sh --version X …`（`mt.sh` **不转发** `--timeout`，须直跑） |
| 旧件「mt_build」 | `bash scripts/test/win/mt.sh --phase build [--dry-run]` |
| 旧件「mt_launch」 --channel autojs | `bash scripts/test/win/launch.sh …`（native 通道已失效） |
| 旧件「mt_stop」 --force --purge-saves | `bash scripts/test/win/mt.sh --version X --phase stop --force --purge-saves`（机器行 `WIN_STOP: OK`） |
| 旧件「mt_assert」 / 旧件「mt_inject」 / 旧件「mt_ime」 | `mt_assert.py` / `mt_inject.py` / `lib/mt_ime.py`（由阶段层调用，一般不手跑） |
| 旧件「mt.proc」 / 旧件「mt.paths」 / 旧件「mt.conf」 / 旧件「mt.phase」 / 旧件「mt.win32」 | `scripts/test/win/lib/{common,phases,fabric}.sh` + `lib/mt_paths.py` |
| 旧件「verify_chip_recipes」 | `python scripts/verify/verify_chip_recipes.py`（B 顺手补） |
| 旧件「deploy」 | **无等价物**（该脚本未跟踪且被 `.gitignore:68` 忽略）⇒ 文档改为「部署步骤手写命令」 |

## 5 重写后的章节骨架（目标）

```
## 自动化测试流程 — 必须遵守（子配置）
### 0. 三分钟上手（唯一入口 mt.sh + 五阶段 + 退出码表）
### 1. 测试资产与用例（cases/*.json、TESTING-SPEC.md 的分级口径）
### 2. 变更分级与验证口径
### 3. 测试顺序
### 4. 测试前清场
### 5. 测试后收尾（WIN_STOP）
### 6. 工具链
 6.1 三层分工（mt.sh 编排 / win 阶段 bash / mt_*.py 能力层）
 6.2 脚本职责表（重写）
 6.3 调用顺序（重写）
 6.4 契约：机器行 / 退出码 / 批量闸门
 6.5 退出清理与端口互斥（25585 单线占用）
### 7. 阶段 P / B / E / L / C / R（每阶段：目的 → 命令 → 机器行 → 失败判据）
### 8. 截图识别规则
### 9. 桌面驱动与游戏内注入（AutoJS HTTP :25585；native 通道状态）
### 10. 环境与兼容栈速查
### 11. 新增内容 / 用户指定内容的测试
### 12. 超时与看门狗（裁决后填实）
### 13. 注意事项
```

## 6 TOC 再生成方案（B 的 P0，必须先做）

**问题**：`AGENTS-TOC.md` 191 行自述 3,665 行，实际 4,255 行 / 257 标题；无生成器 ⇒ A 档才被迫锁死行数。

**方案**：新增 `tools/gen_agents_toc.py`（纯标准库，约 120 行）：

- 输入 `docs/agent/AGENTS-full.md`（`newline=''` 读，按 CRLF 计偏移），输出 `docs/agent/AGENTS-TOC.md`。
- 抽 `^#{1,4} ` 标题；列 **行号 / 字符偏移 / 标题**；偏移口径 = `sum(len(line)+2)`（与现表一致，含 `\r`）。
- 表头自动写「生成时间 + 来源行数 / 标题数 + 口径说明 + 复算命令」，替换手写的那段超长历史说明（历史挪到文件末尾 `## 生成日志` 追加一行）。
- 两种模式：
 - `--check`：只比对，不一致 `exit 1` 并打印首个漂移项（可进 CI / 守卫脚本）；
 - `--write`：重写文件（保留 `## 生成日志` 段）。
- **验收**：`python -B tools/gen_agents_toc.py --check` 在 B 之后 `exit 0`；在 B 之前先跑一次 `--write` 把当前真实状态固化（预期 257 行，可作为重写前的基线快照）。

## 7 执行顺序（P0–P6）

| 步 | 动作 | 产出 / 判据 |
|---|---|---|
| P0 | 写 `tools/gen_agents_toc.py`，`--write` 固化当前 257 标题 | `--check` exit 0 |
| P1 | 用户裁决两个 T4 问题（watchdog 家族 / native 通道） | 裁决记录进本章 §9 / §12 |
| P2 | 重写 §6 工具链（脚本职责表 + 调用树 + 契约） | `grep` 该段 `\旧脚本` = 0 |
| P3 | 逐阶段重写 §7（P/B/E/L/C/R，每阶段含机器行与失败判据） | 每阶段命令可复制执行 |
| P4 | §8 截图 / §9 注入 / §12 看门狗 / §13 注意事项 | `\旧脚本` / `旧 shell` 全章 = 0（T4 若判「已退役」则保留一行登记） |
| P5 | 顺手改 `scripts/verify/verify_chip_recipes.py` ⇒ `.py`（L1182） | 全文件 `旧 shell|\旧脚本|\旧脚本模块` 命中 = 0（或仅剩登记行） |
| P6 | `gen_agents_toc.py --write` 重算 + `verify-split.py` + 行尾核对 | 见 §8 |

## 8 验收判据

1. `python -B docs/agent/verify-split.py` ⇒ `结果：全部通过`（红线区 L2–L129 未动）。
2. `grep -cE '旧 shell|旧脚本扩展名|旧脚本模块扩展名' docs/agent/AGENTS-full.md` ⇒ **0**（T4 若判「已退役」则允许 ≤ 3 行登记，且不含 `旧 shell` 可执行命令）。
3. `grep -c '旧 shell' docs/agent/AGENTS-full.md` ⇒ 仅剩历史条目（§「历史条目读法」允许的反面表述）。
4. `python -B tools/gen_agents_toc.py --check` ⇒ exit 0（行号 / 偏移 / 标题数全对）。
5. 行尾：`AGENTS-full.md` CRLF = 行数、裸 LF = 0、双 CR = 0。
6. `AGENTS-dsh.md` 与 FULL 红线区仍逐字一致（判据 1 覆盖）。
7. 抽检 10 条命令：粘贴进 `ops_bash` 能跑（`--help` 级即可）或明确标注「历史 / 未实现」。
8. 重写后行数可任意变（I5 解锁），但 TOC 必须同步 —— 判据 4 兜底。

## 9 风险与回滚

| 风险 | 说明 | 缓解 |
|---|---|---|
| R1 | 重写时把「历史裁决」当现行写成新命令 | 逐节处置表 §3 已标「保留 / 改写 / 重写」；改写只动命令，不动裁决正文 |
| R2 | 行号解锁后别处引用（`AGENTS-dsh.md` 的规则索引、交接文档）指向旧行号 | 先跑 `grep -rn 'AGENTS-full.md.*L[0-9]' docs` 建引用清单，重写后逐个核 |
| R3 | T4 若判「移植而非退役」，工作量翻倍（`mt_watchdog` 等 ~1,500 行 旧 shell） | P1 先裁决；判移植则 B 拆成 B1（文档）+ B2（实现）两批 |
| R4 | 章节重写误伤红线区 | 红线区在 L2–L129，与本章（L2359+）无交集；判据 1 兜底 |
| 回滚 | 全部改动在 git 工作区，未 commit | `git checkout -- docs/agent/AGENTS-full.md docs/agent/AGENTS-TOC.md`（A 档改动会一并丢失，需先确认 A 是否已提交） |

## 10 工作量估算

| 项 | 估计 |
|---|---|
| `tools/gen_agents_toc.py` | 120 行 / 0.5 天 |
| §6 工具链重写 | 60 行替换 / 0.5 天 |
| §7 六阶段重写 | 200 行改写 / 1 天（需逐阶段实跑 `--help` 核对） |
| §8–§13 收尾 | 80 行 / 0.5 天 |
| T4 若判「移植」 | +2,000 行实现 / 2–3 天（另立批） |
| 合计（不含 T4 移植） | **约 2.5 天**，单会话可完成 |
