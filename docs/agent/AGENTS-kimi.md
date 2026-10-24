# AGENTS-kimi —— Kimi Code 专属规则

> 生效：2026-10-24。适用工作树 `F:\MCProject\astral_dice_multiloader`（分支 `multi-main`）。
> 本文件**只对 Kimi Code 生效**。DSH 读 `docs/agent/AGENTS-dsh.md`，Codex 读 `docs/agent/AGENT-SPLIT.md`；公共分派见根 `AGENTS.md`。

## 0. 用户三项明确纪律（2026-10-24，优先于本文其余条款）

1. **工具选择：Git Bash 优先**。默认用 Git Bash 跑 shell 语义命令；**只有**仓库既有 `.ps1` 脚本、或确需 PowerShell 7 语义时才用 `pwsh -NoProfile -File <绝对路径>`。
2. **K3.1 长上下文任务一律交给 Kimi 原生子代理**（`Agent` / `AgentSwarm`）；主会话只收结论与证据，不自己吞大文件、长日志、整份源码。
3. **版本迭代必须新建交接文档**：每个版本 / 每批交接都新开 `docs/handover-<版本或日期>-<主题>.md`；**禁止**把旧交接文档改名、覆盖或原地重写。

## 1. 接手顺序（每次新会话先做，一律实读）

1. `git -C F:/MCProject/astral_dice_multiloader status --short -uall` + `git -C ... log -1 --oneline`：确认分支、HEAD、是否有他人未提交改动。
2. 读 `docs/handover-2026-10-24-kimi-takeover.md`（Kimi 当前接手正本）；再用 `ls docs/handover-*` 找最新一份，与 `docs/handover-new-session-context.md` 交叉核对。
3. 读 `docs/agent/PROJECT-REFERENCE.md`（当前工程参考：拓扑、四线基线、目录职责、常用命令）。
4. 要具体规则 ⇒ 在 `docs/agent/AGENTS-full.md` 里**按标题名检索**（`grep -n '^#' docs/agent/AGENTS-full.md`，行号总表见 `docs/agent/AGENTS-TOC.md`），只取命中区间 `sed -n '<a>,<b>p'`。
5. 若第 2 / 3 步的两份 Kimi 文档尚未创建（后续任务才落盘）：改读 `docs/handover-new-session-context.md` 与 `docs/handover-*.md` 最新一份，并在回报里点明缺哪份。
6. 不信文档里的版本号 / 行号 / 计数（都是快照）；文档与工作树冲突时以工作树为准，并如实点出差异。

## 2. 工作树与分支归属

- 两棵常驻树：主线 `F:\MCProject\astral_dice_multiloader`（`multi-main`，四条线同树）；开发线 `F:\MCProject\astral_dice_multiloader-next`（`multi-dev-next`）。
- 任务属于哪个分支就**只在那个树的绝对路径**上改；禁止镜像 / 回填 / 同步到另一棵树。
- 跨树写文件一律**绝对路径**（`F:/MCProject/...`），不依赖进程 cwd —— 相对路径会静默落到主会话 cwd（历史事故：一条 0 字节用例文件落错树）。
- 收尾对**另一棵树**跑一次 `git status --short`，确认没有落错文件。
- 主线里的 `scripts/test/**` 属封包内容，只在用户于主线内明确裁决时才动。

## 3. 工具选择：Git Bash → pwsh 7 → Python

| 需求 | 用什么 | 例子 |
|---|---|---|
| 文件 / 目录 / 文本 / git 语义 | **Git Bash** | `ls`、`cp`、`grep -rn`、`git -C ... status`、`sha256sum` |
| 仓库既有 `.ps1`，或 PS7 专属语义 | `pwsh -NoProfile -File <绝对路径>` | `pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase stop --force` |
| 单行 PS 表达式 | `pwsh -NoProfile -Command '…'` | `pwsh -NoProfile -Command 'Test-Path F:\MCProject\.agent-locks\kimi.lock'` |
| JSON / 哈希 / 字符数 / 文本一致性校验 | `python`（仅标准库） | `python -c "import json;json.load(open('x.json',encoding='utf-8'))"` |

- **禁止** Windows PowerShell 5.1；报错出现「术语 'if' 不会被识别为 cmdlet」即用错 shell。
- 属 PS7 语义的写法（`if` 作表达式、三元 `? :`、`??` / `?.`、`ConvertFrom-Json -AsHashtable`、`$PSNativeCommandUseErrorActionPreference`）⇒ 落盘 `.ps1` 再用 `pwsh -NoProfile -File` 跑，不要塞进 bash。
- 反之，bash 能完成的事不绕道 PowerShell（启动开销大、引号转义易错）。
- Python 只用标准库，不新增依赖，不改 `requirements*`。

## 4. 长上下文任务一律委派（K3.1）

判据：需要读**多份大文件 / 整份源码 / 长日志 / 全仓检索**，或预计主会话要贴超过一屏原文 ⇒ **委派**。

- 通道：Kimi 原生 `Agent`（单子代理）与 `AgentSwarm`（并行子代理群）。主会话只收：结论 + `文件:行号` + 命令原文 + 输出片段 + `git rev-parse --short`。
- brief 必须**自包含**（子代理看不到本会话）：目标 · 绝对路径 · 要遵守的规则区间（`sed -n '<a>,<b>p' docs/agent/AGENTS-full.md`）· 可独立执行的判据命令 · 交付格式 · 禁止事项（是否允许改文件、禁止 commit/push）· 是否只读取证。
- 一次只派一件事、只给一个可验收产物；`AgentSwarm` 并行时各子任务写作用域**互不重叠**，写同一文件的任务串行。
- 不向同一子代理追加第二个任务；新任务新开子代理。
- 子代理回报「没收到任务 / 需要澄清」⇒ 补一次 brief，最多重试一次；仍失败如实上报用户，不在主会话反复重派。

## 5. 独立复核（不得自证）

- 非平凡改动（代码 / 资源 / 批量替换 / 跨线 / 结论型交付）宣布完成前，必须用**独立证据**复核：新开一个不继承本会话结论与假设的 Kimi `Agent`，或按用户指定方式复核。
- 只喂：目标、路径、**期望的不变量**、可独立执行的判据命令；**不得**把自己的结论、「我已经改好了」当事实喂进去。
- 复核结论必须挂原始证据（命令 + 输出片段），并显式列出「不一致 / 无法判定 / 未覆盖」三类项；主会话逐条回应，认同即改，不认同给反证，不得静默忽略。
- 两层都要：机器判据（`scripts/verify/**`、`tools/audit_*.py`、`tools/check_lang_sync.ps1`）与独立复核**互不替代**；守门全绿 ≠ 已验收。

## 6. 构建与部署副作用

- 编译只用**定向任务**：`cd /f/MCProject/astral_dice_multiloader && ./gradlew :neoforge-1.21.1:classes`（同理 `:forge-1.20.1:classes` / `:neoforge-26.1.2:classes` / `:fabric-1.20.1:classes`）。
- **`gradlew build` 不是纯编译**：旧流程里它会在收尾阶段把产物投放到整合包 `run/<线>/mods`（部署副作用）。Kimi **默认不跑 `build` / `deploy` / 任何投放脚本**；确需时先说清会写哪些目录，拿到明确授权再跑。
- 构建前：查 `F:/MCProject/.agent-locks/`；查 `build/` 最近修改时间（近 30 分钟有写入却无锁 ⇒ 可能他人正在编译，等待或询问）。
- 构建后：产物以工作树 `build/libs` 为唯一来源；做过投放必须 `sha256sum` 核验并贴原文，不把同名当新构建。
- 不因「顺手」而自动部署、自动上传、自动发版。

## 7. 实机测试授权与收尾

- **实机必须由用户明确下达**（启动 dev 客户端、连拍、探针、用例都算）。默认只做静态取证、编译、守门脚本；未实机的欠账如实登记。
- 获得授权后：先停旧实例再测新构建（旧实例持有旧 jar，叠加会污染结果）。
- 收尾**必做**：`pwsh -NoProfile -File scripts\test\mt.ps1 --version <线> --phase stop --force`，报告里贴 `MT_STOP: OK` 原文 + `Get-Process java` 计数；本流程客户端有残留即不合格。
- 清存档按旧规则：标准收尾带 `--purge-saves`，成功判据 `MT_CLEANUP_SAVES: PURGED <n>` + `MT_CLEANUP_RESULT: OK`；存在 `.mt_keep_alive-<版本>` 取证现场时只提示，说明保留原因与路径，取证完立即 `--phase stop --force`。
- 收尾同时删除本轮注入的临时探针（`run/<线>/kubejs/**`、`scripts/test/resources/kubejs/**`），并贴 `git status --short -uall` 原文。
- 测试靶子用**非亡灵**生物（`minecraft:pillager` / `minecraft:cow` 等），不用僵尸。

## 8. 路径与 git 纪律

- 路径写**绝对路径**，或先 `cd` 到目标树；`git` 一律 `git -C <工作树绝对路径> <子命令>`。
- **默认不 commit / push / 打 tag**：改动留在工作树，由用户决定是否提交。用户明确要求提交时，只 `add` 自己改的文件并逐文件列名，**禁止** `add -A` / `add .`。
- 远程 git 操作（fetch / pull / push / clone）必须走本地代理 `http://127.0.0.1:7897`，不绕过直连。
- `models/`、`images/`、`sound/` 是随时更新的素材目录：只动任务指定的那一个文件，其余不碰。
- `git status` 里出现不属于自己的改动（含未跟踪文件）⇒ 立即停手、报告，不 `stash` / 不 `checkout` / 不 `add` / 不删。
- 不执行破坏性 git 命令（`reset --hard`、`clean -fd`、`branch -D`、`checkout -- .`），除非用户点名要求且已确认无他人改动。

## 9. 版本迭代与交接文档

- 每进入**新版本 / 新批次**，**新建**一份交接文档：`docs/handover-<版本或日期>-<主题>.md`（当前 Kimi 接手掌：`docs/handover-2026-10-24-kimi-takeover.md`）。
- **禁止**把旧交接文档改名、覆盖或原地重写来「变成新的」：旧文件是历史证据，只允许追加，不得改写既有结论。
- 交班至少覆盖：日期 / 分支 / HEAD / 四线 `mod_version` / 工作树残留 / 已完成 / 遗留（销账或新增，**禁止静默删**）/ 新坑 / 关键路径。
- 未实机的欠账、无法测度的判据、与规格不一致（REFUTED）的差异**如实登记**。
- 引用交接写「§号 + 标题」，不写死行号（行号会漂移）。

## 10. 互斥锁协议（可执行）

锁目录：`F:/MCProject/.agent-locks/`（在仓库之外，不入 git）。DSH 用 `dsh.lock`，Kimi 用 `kimi.lock`，Codex 只读不加锁。

**写文件 / 跑 Gradle 之前先查：**

```bash
ls -la /f/MCProject/.agent-locks/ 2>/dev/null
cat /f/MCProject/.agent-locks/dsh.lock 2>/dev/null   # PID/TIME/TASK 三行
```

- `dsh.lock` **活跃**（PID 存活）⇒ 等待或询问用户；**不得删除、不得覆盖**。只读任务不受限。
- `dsh.lock` 陈旧（PID 已不存在）⇒ **也不要自己删**；记录 PID 与时间，交用户裁决。
- 自己的 `kimi.lock` 残留 ⇒ 说明上一轮未清理，先核对 TASK 是否为本轮再替换。

**开始动工作树时创建自己的锁：**

```bash
printf 'PID=%s\nTIME=%s\nTASK=%s\n' "$$" "$(date -Iseconds)" "<任务名>" > /f/MCProject/.agent-locks/kimi.lock
```

**结束（成功 / 失败 / 中断）都删掉自己的锁：**

```bash
rm -f /f/MCProject/.agent-locks/kimi.lock
```

- 只清理 `kimi.lock`；`dsh.lock` 与任何未知锁文件一律不碰。
- 本轮只执行协议检查，**不预先创建**锁文件：锁只在真正动工作树的那一轮创建。

## 11. 收尾自查清单

1. `git -C F:/MCProject/astral_dice_multiloader status --short -uall` —— 只应出现自己改的文件；他人改动与素材目录改动如实列出，不代改。
2. `git -C ... rev-parse --short HEAD` —— 默认口径下不应有新提交。
3. `ls /f/MCProject/.agent-locks/` —— 自己的 `kimi.lock` 已清理。
4. 报告涉及版本号 / 路径 / 行号的，全部用命令重算，不抄旧文档。
5. 报告必须含：文件清单 + 行号、命令与原始输出片段、未验证项 / 风险、`git rev-parse --short`。
