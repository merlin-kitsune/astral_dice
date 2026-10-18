# 需求分析与任务分配（PM 正本）

> 时点：2026-10-18（Asia/Hong_Kong）｜施工线：`multi-dev-next`｜**实测 HEAD = `c325e045`**
> 本文与 `00-master-plan.md`（/root/a_progress）、`work/wsl-migration/00-PLAN.md`（/root/b_tests）的关系：
> **本文是「需求 → 验收判据 → 任务写域」的唯一分配正本**；两份计划的阶段划分（P0–P7）继续有效，冲突处以本文 §3 决策为准。

---

## 1. 实测基线（与前人文档的差异已逐条订正）

| 项 | 实测值 | 差异 / 证据 |
|---|---|---|
| 分支 / HEAD | `multi-dev-next` / **`c325e045`** | 两份计划都写 `bcae58f9`（上一个提交）；`git log -8 --oneline` 实测 |
| 工作树 | 干净，仅 `models/*.bbmodel` 两行用户资产（+ 两份计划文档未跟踪） | `git status --short -uall` 实测 4 行 |
| 四线版本 | `2.0.0-SNAPSHOT.132` | 交接正本 header |
| 用例总数 | **230**（顶层 218 + `blocked-26.1.2` 12） | `docs/features/autojs-legacy-debt.md` §1.1 |
| 已迁 AutoJS | **1 / 230**（`AUTOJS-SMOKE-1.21.1`） | 同上 §7 |
| legacy 断言 | **4741**（log 3314 / absent 945 / crash 222 / kubejs 198 / mixin 53 / vision 9） | 同上 §1.2 |
| 步骤面 | `inject_command` 4846、`inject_key` 65、`inject_mouse` 4、`screenshot` 111、`vision` 4、`eval_js` 24、`await_state` 6 | `00-master-plan.md` §0 |
| AutoJS SKILL | 在盘 `.codex/skills/autojs/{SKILL.md,references/}`，**但 `.codex/` 被 `.gitignore:102` 排除 ⇒ 不在版本库** | `git ls-files --error-unmatch` 报 `did not match`；见 §3-D3 |
| AutoJS jar | 四线产物在 **`scripts/test/resources/mods/autojs/`**（含 `PROVENANCE.md` + SHA256）⇒ 随仓库走，不依赖 Windows `run/` | `ls scripts/test/resources/mods/autojs/` |
| AutoJS 端点 | `http://127.0.0.1:25585/mcp`（`scripts/test/mt.conf:27`），当前 `http=000`（游戏未启动，属正常） | `curl` 实测 |
| WSL 环境 | Ubuntu / home=`/home/merlin` / ext4 余 **910G** / `pwsh 7.6.7` / `rsync` / `git 2.43` / Zulu **21** / `python3` / `jq` / WSLg `DISPLAY=:0` / **`/dev/dxg` 存在** | 本次实测 |
| **WSL git 致命坑（新发现）** | ① `.git` 是指针文件写 `F:/...` ⇒ `git -C` 报 `not a git repository`；② 即使绕过（`--git-dir`+`--work-tree`），WSL git 默认 `core.autocrlf=false` ⇒ **1000+ 文件假 modified**（blob=LF、工作树=CRLF）。设 `core.autocrlf=true` 后收敛为 4 行 | 本次实测，**必须写进 WSL 引导脚本** |

---

## 2. 用户诉求 → 验收判据（DoD）

| # | 诉求 | DoD（可机器复算） |
|---|---|---|
| R1 | 读 AGENTS + 交接文档，厘清已/未完成 | `01-progress-audit.md` 含「已完成批次 / 未完成清单 / 文档矛盾清单」，每条带 `文件:行号` |
| R2 | 项目整体搬到 WSL，仅收尾回写，Windows `cmd.exe` 跑 push | ext4 克隆 `git log -1 == c325e045`；`status` 与源目录逐行一致；`wsl-bootstrap.sh` 幂等；`sync-back.sh --dry-run` 默认不写盘 |
| R3 | 检查 AutoJS 现状 / 未迁移数量 / SKILL 是否入项目 | `02-test-audit.md` 给出 229/230、按线按机制分布、SKILL 在盘但被 gitignore |
| R4 | 测试资产全转 AutoJS，旧资产清理 | 用例 `channel=autojs` 覆盖率按 §4 分批达标；被删资产每条给「零引用」`grep` 证据 |
| R5 | pwsh：只有「改 AutoJS 的」才转，其余分析后转 bash | `03-ps1-triage.md` 三分表（转 bash / 转 AutoJS / 删除），每行带判据；**不得**产出 bash 版模拟键鼠 |
| R6 | 实机改 WSL + AutoJS，废除键鼠，重建资产 | WSL `runClient` 起得来（GL 门禁）；`AUTOJS-SMOKE` 在 WSL 链路 PASS；用例侧 `inject_*/screenshot/vision` 计数 = 0 |
| R7 | code_review 交给 Codex `code_review` | 每个代码批次有 job id + findings 计数；失败改派一次性子代理（规格不变） |

---

## 3. 关键决策（先定死）

1. **D1 唯一施工副本** = `~/work/astral_dice_multiloader-next`（ext4）。`/mnt/f/...` 收尾前**只读**，禁止双向同步。
2. **D2 克隆方式** = `git clone -b multi-dev-next /mnt/f/MCProject/astral_dice_multiloader <dst>`（产出原生 `.git`），随后 `git config core.autocrlf true`（**必须**）+ 复制 2 个未跟踪 `models/*.bbmodel`。
3. **D3 `.codex/` 不入库（保持现状）**：`.gitignore:102` 已排除，属既定口径。本批只做：AGENTS 登记路径 + 交付报告注明「SKILL 在盘、非版本库资产」。要入库须用户裁决（规则变更）。
4. **D4 AutoJS jar 来源** = `scripts/test/resources/mods/autojs/`（入库、带 SHA256）；WSL `env` 阶段从这里投放，不从 Windows `run/` 拷。
5. **D5 测试台三层形态**：① 入口 `scripts/test/mt.sh`（bash，阶段 `preflight|build|env|launch|case|report|stop`）；② 通道 `scripts/test/lib/autojs.py`（python3 标准库 MCP 客户端：initialize → tools/call、JSON/SSE 双形态、session id）；③ 用例 `scripts/test/lib/case.py` + `scripts/test/cases/*.json`。
6. **D6 pwsh→bash 边界**：只有「已改 AutoJS 的流程」转 bash 外壳；**模拟键鼠/窗口类一律删除、不翻译**：`mt_inject.ps1`、`mt_ime.ps1`、`mt_capture.ps1`、`mt_fire.ps1`、`mt_scope.ps1`、`mt_watchdog.ps1`、`lib/Mt.Win32.psm1`、`lib/Mt.Proc.psm1`（逐个核零引用）。
7. **D7 实机位置** = WSL 内 `./gradlew :<线>:runClient` ⇒ AutoJS 端点在 WSL loopback，无跨 interop；GL 门禁失败即上报用户，**禁止**擅自退回 Windows/键鼠。
8. **D8 兼容层**：`channel` 缺省 = `kubejs`；迁移必须显式写 `autojs`。`crash/mixin/kubejs/vision` 断言保留为「日志通道」单列。
9. **D9 构建守卫**：推送整合包任务在 WSL 下目标盘不存在 ⇒ skip 不 fail。
10. **D10 收尾回写**：`rsync` 回 `F:\MCProject\astral_dice_multiloader-next`（排除 `build/ .gradle/ run/ .git/`，不覆盖 `models/*.bbmodel`）；`git add` 逐顶层路径（禁 `-A`）；push 经 `cmd.exe` 走代理 `127.0.0.1:7897`。

---

## 4. 用例迁移分批（不追求一次吃完 229 条）

| 批 | 对象 | 文件数 | 手法 | 验收 |
|---|---|---|---|---|
| M0 | 1.21.1 代表用例（在 SMOKE 基础上扩到 4–6 条） | 4–6 | `eval_js` + `state` 带明确 `expect` | `validate` + WSL 实机 PASS |
| M1 | 纯 `inject_command`+`wait`+`log` | 168（分线） | 命令 → server `eval_js`；`wait` → `await_state` | 每条至少 1 条明确判据 |
| M2 | 含 `screenshot` | 28 | 删截图，改 client 结构化读数 | 无断言面者 `retired` + 理由 |
| M3 | 含 `inject_key`/`inject_mouse` | 7 + 1 | `KeyMapping`/`Screen.mouseClicked` 求值 | 同上 |
| M4 | `blocked-26.1.2/` | 12 | 结构改 `autojs` + 保留 `blocked_reason` | 结构合法 |
| — | 1.20.1 / 26.1.2 / fabric | 118+ | 先解 §6 接入路线，否则记「不支持」 | 不得静默跳过 |

---

## 5. 任务分解与写域（brief 首句必须是职业声明）

| ID | 职业 | 任务 | 写域（唯一） | 依赖 | 验收 |
|---|---|---|---|---|---|
| W1 | 项目文件分析员 | 进度台账 + 文档矛盾 | `01-progress-audit.md` | — | 每条带行号 |
| W2 | 测试自动化审计员 | 测试资产普查 + pwsh 三分表 | `02-test-audit.md`、`03-ps1-triage.md` | — | 三类清单 + `grep` 证据 |
| W3 | Linux/WSL 平台工程师 | ext4 克隆 + 环境门禁（git/GL/gradle/端点） | `~/work/**`、`04-wsl-feasibility.md` | — | 四判据 + GL 结论 |
| W4 | Java 构建与 CI 工程师 | `wsl-bootstrap.sh`/`sync-back.sh`/构建守卫 | `scripts/dev/*.sh`、`*.gradle` 守卫块 | W3 | 幂等 + `--dry-run` |
| W5 | bash/DevOps 工程师 | `mt.sh` + `lib/{paths,proc,phase}.sh` | 同名文件 | W4 | 阶段空转可跑 |
| W6 | 测试框架工程师（python） | `lib/{autojs,case,report}.py` | `scripts/test/lib/*.py` | W5 | 单测 + `validate` |
| W7 | MC 模组测试工程师 | M0 迁移 + WSL 实机 | `cases/*-1.21.1.json`（M0 子集） | W6 | `MT_CASE_RESULT: PASS` |
| W8 | 独立清理审查员 | 删除清单 + gitignore + `Start-*.bat` | 删除项 + `.gitignore` | W6/W7 | `grep` 零引用 |
| W9 | 技术文档工程师 | AGENTS/AGENTS-full/handover/TESTING-*/debt | 文档 | W5–W8 | `verify-split.py` 四项 OK |
| W10 | MC 资源与测试资产工程师 | WSL `run/` 与 `scripts/test/resources/` 归位 | `scripts/test/resources/**` | W3 | jar SHA256 一致 |

**并发纪律**：一份文档同一时刻只有一个写者；用例按线后缀分写域；brief 自带判据命令；子代理用完即弃。

---

## 6. 门禁与风险

| 门禁 | 判据 | 失败动作 |
|---|---|---|
| G-1 WSL git | 克隆内 `git log -1 == c325e045` 且 `status` 仅 2 个 bbmodel | 停，回报 |
| G-2 GL | WSL `runClient` 进主菜单 + AutoJS `status` 两端在线 | 停，请用户裁决（不退回键鼠） |
| G-3 构建 | `./gradlew :neoforge-1.21.1:classes` exit 0，不触碰 `D:\` | 修守卫 |
| G-4 通道 | `AUTOJS-SMOKE-1.21.1` PASS | 修通道 |
| G-5 纯度 | 用例侧 `inject_*/screenshot/vision` 计数 = 0（M0 后） | 继续迁移 |

**风险**：① WSLg 跑 MC 的帧率/音频；② 1.20.1/26.1.2 的 AutoJS 产物 remap 路线未验证；③ 229 条用例 + 5 槽位上限 ⇒ 必须分批、可中断续做。
