# WSL 迁移 + 测试流程全面 AutoJS 化 —— 总计划

> 制定：2026-10-18｜规划者：项目经理代理（/root/a_progress）
> 施工分支：`multi-dev-next`｜基线 HEAD `bcae58f9`｜四线版本 `2.0.0-SNAPSHOT.132`
> 用户指令（本次）：仓库整体迁入 WSL 处理 → 收尾同步回 `F:\` 并用 Windows `cmd.exe` 执行 `git push`；
> AutoJS 成为唯一实机测试通道；旧 pwsh 测试流程中「改为 AutoJS 的那部分」才转 bash；旧资产清理；实机在 WSL 下跑。
> 复核口径：`code_review`（Codex 原生），不再派 subagent 做二次验证。

---

## 0. 已核实的基线（不是文档转述，是实测）

| 项 | 值 | 证据 |
|---|---|---|
| HEAD / 版本 | `bcae58f9` / 四线 `2.0.0-SNAPSHOT.132` | 交接正本 header；收尾前以 `git log -1` 复核 |
| 工作树 | 仅 2 个未跟踪用户资产 `models/*.bbmodel` | `git status --short -uall` |
| 用例总数 | **230**（顶层 218 + `blocked-26.1.2` 12） | 见 `docs/features/autojs-legacy-debt.md` §1.1 |
| 已迁 AutoJS | **1**（`AUTOJS-SMOKE-1.21.1`）⇒ 完成度 **1/230** | 同上 §7 |
| legacy 断言 | **4741** 条（`log` 3314 / `absent` 945 / `crash` 222 / `kubejs` 198 / `mixin` 53 / `vision` 9） | 同上 §1.2 |
| 步骤类型（实测解析） | `inject_command` **4846**、`wait` 4791、`note` 1067、`screenshot` 111、`inject_key` 65、`eval_js` 24、`await_state` 6、`inject_mouse` 4、`vision` 4 | 本次 python 全量解析 |
| AutoJS 部署 | 四线 `run/*/mods/` 均已有 jar（1.0.0，各线加载器产物） | `ls run/*/mods/*AutoJS*` |
| AutoJS 端点 | `http://127.0.0.1:25585/mcp`，`unrestricted: true`，`run/1.21.1/config/autojs.json` 已就位 | `cat run/1.21.1/config/autojs.json`、`scripts/test/mt.conf` |
| AutoJS SKILL | **已在项目中**：`.codex/skills/autojs/{SKILL.md,references/}`（另有旧版 `.from-jar-1.0.0.bak`） | `ls .codex/skills/autojs` |
| 目标规格 | `docs/features/autojs-test-harness-spec.md`（A 档：核心通道 + 样板用例，§8 明确「全量迁移」为欠账） | 全文 |
| 迁移口径 | `docs/features/autojs-legacy-debt.md`（分类、优先级、对照表、checklist、完成度算法） | 全文 |
| **历史 bash 台** | **存在过并被删除**：`scripts/test/{mt.sh,mt_build.sh,mt_launch.sh,mt_stop.sh}`、`lib/{paths.sh,phase.sh}`，在 `92fbeaf71`「工具链收敛为纯 pwsh」中删除（该提交即回滚基线） | `git log --diff-filter=D`、`git show 92fbeaf71^:<path>` |
| WSL 环境 | Ubuntu 24.04 / 16 核 / 62G / ext4 余 910G / WSLg(`DISPLAY=:0`) / `/usr/bin/pwsh 7.6.7` / Zulu 17·21·25 | 本次实测 |
| WSL 下的 git | **不可用**：`.git` 是指针文件指向 Windows 路径 ⇒ `git -C` 报 `not a git repository`。绕行 `--git-dir=<主仓>/.git/worktrees/... --work-tree=...` 可用 | 本次实测 |

### 0.1 前任放弃 WSL 的三条根因，本计划的逐条回应

| 根因（2026-10-13~17 实测） | 本计划回应 |
|---|---|
| ① `/mnt/f`(9p) stat 慢 ≈275×，`:classes` 冷跑 9m22s | **仓库整体复制到 ext4**（`~/work/...`），不再在 9p 上编译 → 根因消除（由 c_wsl 实测确认） |
| ② 从 WSL interop 起 `pwsh.exe` 时 `& <exe>` 静默不执行 | **不再跨 interop 调 Windows pwsh**：构建/运行/断言全在 WSL 内（bash + python3 + WSL 原生 `pwsh` 仅用于**读旧脚本做分析**） |
| ③ WSL 不透传 Windows PATH | 同上，WSL 内自带 JDK/Gradle/pwsh，无需 PATH 直通；仅在「收尾 push」一步显式用 Windows `cmd.exe` |

> 结论：三条根因都不是「WSL 不行」，而是「在 9p 上跑 + 跨 interop 调 pwsh」。本计划的形态正好避开全部三条。

---

## 1. 目标形态（Definition of Done）

**D1 工作区**：唯一施工面 = WSL ext4 上的克隆；`git` 在该克隆里原生可用；构建产物不落 9p。
**D2 测试台**：`bash` 入口 + `python3`（仅标准库）实现，阶段 `preflight → build → env → launch → case → report → stop`；**无 pwsh 依赖、无 taskkill/Start-Process/user32/注册表**。
**D3 通道**：唯一实机驱动 = AutoJS MCP（`eval_js` / `await_state` / `status` / `reload_scripts` / `kubejs_log`）；**零** `inject_command` / `inject_key` / `inject_mouse` / `screenshot` / `vision` 步骤。
**D4 用例**：230/230 带 `"channel":"autojs"`；每条至少有 **1 条带明确 `expect` 的 `eval`/`state` 断言**（纯 `crash`/`mixin` 启动期闸门用例可豁免，须在用例里注明理由）。
**D5 资产**：仅被 legacy 流程引用的脚本 / KubeJS 探针 / 运行时产物全部删除或 gitignore；删除项在报告中列「路径 + 依据」。
**D6 文档**：`AGENTS.md` 红线区、`docs/agent/AGENTS-full.md` 测试章、`docs/handover-*.md`、`docs/handover-new-session-context.md`、`TESTING-SPEC.md` / `TESTING-RULES-OVERVIEW.md`、`autojs-legacy-debt.md` 全部改为 WSL/AutoJS 口径，且**不再存在**「禁止 WSL / 仅 Windows 原生」的现行表述（历史根因只保留在「历史」小节）。
**D7 收尾**：变更 rsync 回 `F:\MCProject\astral_dice_multiloader-next`，Windows 侧 `cmd.exe /c` 完成 `git add`（逐路径，禁 `-A`）→ `commit` → `push`（走代理 `127.0.0.1:7897`）。

---

## 2. 阶段划分与交付物

### P0 取证（并行，只读）
| ID | 任务 | 交付物 |
|---|---|---|
| P0-a | 进度与口径冲突审计 | `docs/reports/wsl-migration/01-progress-audit.md` |
| P0-b | 测试资产与用例普查 | `docs/reports/wsl-migration/02-test-audit.md` |
| P0-c | WSL 可行性（git 克隆 / `:classes` / 部署副作用 / 两模式实机 / GL） | `docs/reports/wsl-migration/03-wsl-feasibility.md` |

### P1 WSL 工作区落地（P0-c 的结论驱动）
1. 克隆到 `~/work/astral_dice_multiloader-next`（优先 `git clone -b multi-dev-next <主仓>`；失败退回 `rsync + git init + fetch`）。
2. 校验：`git log -1 == bcae58f9`、`git status --short -uall` 仅 2 个 bbmodel、`git ls-files --eol` 抽样行尾稳定。
3. Gradle 部署任务守卫：`pushToGame` 类任务在目标盘不存在时必须 **skip 而非 fail**（WSL 内没有 `D:\`）。
4. 交付 `scripts/dev/wsl-bootstrap.sh`（幂等建工作区）+ `scripts/dev/sync-back.sh`（`--dry-run` 默认，列出将回写的文件）。

### P2 bash + python 测试台
| 文件 | 职责 | 来源 |
|---|---|---|
| `scripts/test/mt.sh` | 阶段入口 `--version/--phase/--case/--purge-saves` | 恢复 `92fbeaf71^:scripts/test/mt.sh` 后重写阶段表 |
| `scripts/test/lib/paths.sh` / `phase.sh` | 路径解析 / 阶段预算与超时 | 同上，去 Windows 化 |
| `scripts/test/lib/proc.sh` | 分离式启动、进程组收停、日志尾随、超时强杀 | 新写（替代 `Mt.Proc.psm1` 的 `taskkill`） |
| `scripts/test/lib/autojs.py` | MCP 客户端：initialize → tools/call、JSON+SSE 双形态、session id、四工具包装、错误归一 | 语义照抄 `Mt.AutoJs.psm1` |
| `scripts/test/lib/case.py` | 用例 schema 校验 + 原语执行（`eval_js` / `await_state` / `assert{log,absent,crash,kubejs,mixin,eval,state}`）+ 退出码 | 语义照抄 `mt_case.ps1` |
| `scripts/test/lib/report.py` | 报告与 `MT_*` 标记抽取 | 照抄 `mt_report.ps1` |
| `scripts/test/mt_build.sh` / `mt_env.sh` / `mt_launch.sh` / `mt_stop.sh` | 四阶段 | 恢复 bash 原件 + WSL 化 |
**取舍**：用例执行/断言用 python3 而非 bash —— 4744 条断言 + 嵌套 `expect` 用 bash 实现不可维护；python3 已在本机且跨平台（`tools/*.py` 同款）。

### P3 用例迁移（229 条，按机制分批，非按文件）
| 批 | 对象 | 数量（文件） | 手法 | 验收 |
|---|---|---|---|---|
| **M1** | 仅 `inject_command`+`note`+`wait`+`log` 断言 | **168** | 命令 → `eval_js`（server，直接读注册表/世界状态）；`wait` → `await_state`；`log` → `eval/state` 带 `expect` | `validate` 通过 + 至少 1 条明确判据 |
| **M2** | 含 `screenshot` | **28** | 截图步骤删除；判据改为 client 侧 `eval_js`（`Minecraft.getInstance()` 的 `screen` / `level` / 槽位 / FPS） | 同上；如确无断言面 ⇒ 用例降级为 `retired` 并登记理由 |
| **M3** | 含 `inject_key` | **7** | 走真实输入路径（`KeyMapping` / `Screen.mouseClicked` / `AutoJS.move`），client 侧 `eval_js` | 同上 |
| **M4** | 含 `inject_mouse` / `vision` | **1 + 2** | 同 M3；`vision` 断言改结构化客户端状态，或退役 | 同上 |
| **M5** | `blocked-26.1.2/` 12 条 | **12** | 同步改 `channel=autojs` 结构，仍标 blocked（该线未实机） | 结构合法 + `blocked_reason` 保留 |
| **Mt（不移）** | `crash` / `mixin` / `kubejs` / 禁 AI 四层审计 | 全仓 | 断言保留（事实只存在于文件层），但**用例**同为 `channel=autojs` | 报告里单列「日志通道断言」 |
**并行策略**：M1 按线拆 3 份（1.21.1 / 1.20.1 / 26.1.2），每份一个子代理，各自只改自己后缀的用例文件，避免并发写冲突。

### P4 资产清理（依赖 P2/P3 的引用面结论）
- 删除：`mt_inject.ps1`、`mt_ime.ps1`、`mt_capture.ps1`、`mt_fire.ps1`、`mt_scope.ps1`、`mt_watchdog.ps1`、`mt_loadergate.ps1`、`mt_gen_case.ps1`、`lib/Mt.Win32.psm1`、`lib/Mt.Proc.psm1` 及被替代的 `mt_*.ps1`（逐个给「零引用」证据）。
- 删除：仅服务 `AP_*` 打印的 KubeJS 探针（`scripts/test/resources/kubejs/**` 内），保留 `astral_test_noai.js` 与 `crash/mixin/kubejs` 需要的部分。
- gitignore：`scripts/test/cases/.mt_*.json`、`scripts/test/reports/**` 运行时产物、`temp/**`。
- `run/Start-*.bat` → 改为 `scripts/dev/start-client.sh`（WSL），`.bat` 删除或标注 legacy。

### P5 实机验证（WSL，用户已下达实机授权）
1. `:classes` 通过（WSL ext4）。
2. 冷启动 → `MT_LAUNCH` 就绪 → AutoJS 闸门（`status` 两端在线）。
3. 跑 M1 样板（每线 2–3 条）+ `AUTOJS-SMOKE`。
4. 收尾 `stop --purge-saves`，判据 `MT_STOP: OK` + `MT_CLEANUP_SAVES: PURGED n`。
5. **模式判定**：优先 WSL 内跑客户端（WSLg + d3d12）；若 GL 不足 ⇒ 退回「Windows 客户端 + WSL 驱动 MCP」，但**必须**在报告与本计划里写明理由并请用户确认。

### P6 文档与规则改写
- `AGENTS.md`：命令执行规范（pwsh→bash/pwsh 双轨）、工具链纪律第 7 条（Windows 原生 → WSL 唯一）、工作树纪律（WSL 克隆 + sync-back + Windows push）、测试流程章节。
- `docs/agent/AGENTS-full.md` 对应章节 + 末尾新增一条「待办交接」第 40 条（本批）。
- `docs/handover-2.0.0-dev-next.md`（header/§0/§2/§5/§6/§7）、`docs/handover-new-session-context.md`（§0 全节重写）。
- `docs/features/autojs-legacy-debt.md` 数字归零并保留历史。

### P7 收尾与推送
1. `git status --short -uall` 原文（WSL 侧）。
2. `sync-back.sh --dry-run` → 人工核对清单 → 实际回写（**不覆盖** `models/*.bbmodel` 用户资产）。
3. Windows：`cmd.exe /c "cd /d F:\MCProject\astral_dice_multiloader-next && git add <逐路径> && git commit -m ..."`。
4. `cmd.exe /c "... git -c http.proxy=http://127.0.0.1:7897 push"`（默认仍不 push，**本次用户已明确要求 push**）。
5. 核验另一工作树 `F:\MCProject\astral_dice_multiloader` 仍为空、HEAD 未变。

---

## 3. 子代理分工（一次派发、单一写域）

| 代理 | 职业 | 写域（唯一） | 依赖 |
|---|---|---|---|
| A | 项目文件分析员（只读） | `01-progress-audit.md` | — |
| B | 测试资产审计员（只读） | `02-test-audit.md` | — |
| C | 构建与环境工程师（只读 + `~/tmp` 实验） | `03-wsl-feasibility.md` | — |
| D | 构建/CI 工程师（bash） | `scripts/test/{mt.sh,mt_*.sh,lib/*.sh}`、`scripts/dev/*.sh` | C |
| E | 测试框架工程师（python） | `scripts/test/lib/{autojs,case,report}.py` | C、D（接口先行） |
| F/G/H | MC 模组测试工程师 ×3（按线） | `scripts/test/cases/*-1.21.1.json` / `*-1.20.1.json` / `*-26.1.2.json` | E |
| I | 资源与测试资产生命周期工程师 | 删除清单 + gitignore + `run/Start-*` | E、F/G/H |
| J | 技术文档工程师 | AGENTS/handover/TESTING-* | 全部 |

规则：**一份文档同一时刻只有一个写者**；每条 brief 首句写职业；子代理用完即弃；回报只给摘要 + 证据路径。

---

## 4. 风险与已定对策

| 风险 | 对策 |
|---|---|
| WSL 内 GL 不足，客户端跑不起来 | P0-c 先判；不足则退回 Windows 客户端 + WSL 驱动 MCP，并请用户确认（不改判据口径） |
| 1.20.1 / fabric 的 AutoJS 产物是 SRG / intermediary，不能进 `run/mods` | 走 `build.gradle` 的 `modImplementation`（spec §6）；若不可行 ⇒ 该线记「不支持 AutoJS」而非静默跳过 |
| 229 条用例体量大、判据可能被弱化 | 硬约束：每条必须带明确 `expect`；`{ok:true}` 这类形式判据**不算**；迁移后逐条 `validate` |
| 并发改同一文件 | 按后缀分线、按目录分写域；改动前 `git diff` 自检 |
| 行尾被整文件翻转 | 只改必要行；提交前 `git diff --stat` 复核，异常即回滚该文件 |
| 9p 回写覆盖用户资产 | `sync-back.sh` 显式排除 `models/*.bbmodel`、`run/**`、`build/**`、`.gradle/**` |

---

## 5. 完成度度量（唯一口径）

- 迁移进度 = `channel=autojs` 的用例数 / 230。
- 通道纯度 = 用例里 `inject_*/screenshot/vision` 步骤出现次数（目标 0）。
- 测试台纯度 = `scripts/test` 下被入口引用的 `.ps1` 数量（目标 0）。
- 构建纯度 = WSL 内 `:neoforge-1.21.1:classes` exit 0 且不触碰 `D:\`。
