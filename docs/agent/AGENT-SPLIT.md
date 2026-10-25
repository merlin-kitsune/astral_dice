# AGENT-SPLIT —— Codex 与 DSH 的操作模式分离（2026-10-22 用户裁决）

适用：`F:\MCProject\astral_dice_multiloader`（分支 `multi-main`，版本 1.5.0-pre.4）。
一句话：**DSH 是唯一执行者，Codex 只做只读验证**；两种模式并行时互不踩踏。

## 一、职责矩阵

| 维度 | Codex（Linux/容器侧，只读验证） | DSH（Windows 原生，唯一执行者） |
|---|---|---|
| 读源码 | 可以（只读） | 可以 |
| 写源码 | **禁止** | **唯一允许** |
| 编译 | 仅在私有副本（`~/work/...`） | 直接在工作树 `gradlew.bat` |
| git commit / push | **禁止** | 唯一允许（push 仍需用户放行） |
| 实机 / `mt.ps1` | **禁止** | **唯一允许** |
| 投放整合包 | 禁止 | 允许 |
| 文档 | 可以（`docs/**`） | 可以 |

判定口径：凡会改变工作树内容或占用本机进程/GPU 的动作（写文件、`gradlew`、`mt.ps1`、`git commit`、
复制 jar 到整合包）一律归 DSH；Codex 只做读取、`grep`、`dsh_code_review`、`docs/**` 写作。
Codex 私有副本的编译结果**不作为验收证据**（副本与工作树可能不同步），只用于它自己判断可行性。

## 二、互斥（同一工作树只允许一个执行者）

1. 锁目录：`F:\MCProject\.agent-locks\`（在仓库之外，不入 git）。
2. DSH **每次动工作树前**创建 `dsh.lock`，内容三行：`PID=<进程号>` / `TIME=<ISO8601>` / `TASK=<任务名>`；
   本轮结束（成功 / 失败 / 中断）**必须删除**。
3. Codex 只读，**不加锁**，也不得创建或删除 `dsh.lock`。
4. 同一工作树同时只允许一个 agent 跑 gradle。启动构建前先查两件事：
   - `dsh.lock` 是否存在、PID 是否存活（`Get-Process -Id <PID>` 报「找不到进程」⇒ 陈旧锁，可删并记录时间）；
   - `build\` 与各子项目 `build\` 的最新修改时间是否在近 30 分钟内（有写入却无锁 ⇒ 他人正在编译，等或问）。
5. 抢锁/查锁命令（只读，任意一侧可跑）：

```powershell
pwsh -NoProfile -Command 'Get-Content F:\MCProject\.agent-locks\dsh.lock -ErrorAction SilentlyContinue'
pwsh -NoProfile -Command 'Get-ChildItem F:\MCProject\astral_dice_multiloader -Recurse -Filter build -Directory -Depth 2 | ForEach-Object { $_.LastWriteTime.ToString("s") + "  " + $_.FullName }'
```

## 三、交接

1. DSH 每轮结束**必须**更新**当前滚动正本**（2026-10-24 起 = `docs/handover-1.5.0-rc.1-main.md`；pre.5 及更早见 `docs/handover-1.5.0-pre.5-main.md`）的 **§4（实机读数，贴机器原文）** 与
   **§5（遗留：销账或新增，禁止静默删）**；并同步刷新 `docs/handover-new-session-context.md`。版本 bump 时**新建**下一份正本（`docs/handover-<版本>-main.md`），旧本不删不改正文。
2. Codex **只消费不回写**：可读上述两文件作为事实源，不得修改 §4 / §5，也不得代填读数。
3. 交接正本行号会漂移 ⇒ 引用一律「§号 + 标题」，不写死行号。

## 四、冲突处理

1. 任一方发现 `git status --short -uall` 里有**不属于自己**的改动（含未跟踪文件）⇒ **立即停手**：
   不 `add`、不 `commit`、不 `stash`、不 `checkout`、不删文件。
2. 在 `docs/agent/CONFLICT.md` **追加一行**（不改旧行）：
   `<ISO 时间> | <文件或路径> | <推测归属：Codex/DSH/未知> | <现场：命令与关键输出片段>`。
3. 交用户裁决；裁决前不动该文件，也不回退别人的改动。
4. `CONFLICT.md` 不存在或没有条目 = 无冲突；**不要**为占位预先创建空条目。

## 五、核查（收尾自查，任一方向适用）

```powershell
git -C F:\MCProject\astral_dice_multiloader status --short -uall      # 只应有自己改的文件
pwsh -NoProfile -Command 'Test-Path F:\MCProject\.agent-locks\dsh.lock'  # DSH 收尾后应为 False
python docs/agent/verify-split.py                                      # 动过规则文件后四项全 [OK]
```

## 六、Kimi Code 兼容说明（2026-10-24 追加；第一至五节原文不变）

1. 第一至五节的矩阵、互斥与冲突处理仍属 **Codex / DSH** 口径，Kimi Code 不据此执行；Kimi 的规则正本是
   `docs/agent/AGENTS-kimi.md`，公共分派见根 `AGENTS.md`。
2. 锁目录新增 Kimi 的 `kimi.lock`：Kimi 每次动工作树前创建（`PID=` / `TIME=` / `TASK=` 三行），收尾删除。
   **Codex / DSH 启动写入或构建前，除 `dsh.lock` 外也必须检查 `kimi.lock`**：存在且 PID 存活 ⇒ 等待或询问用户，
   **不得删除、不得覆盖**；陈旧锁同样不自行删除，记录后交用户裁决。
3. 冲突处理依旧按第四节：发现不属于自己的改动即停手，追加到 `docs/agent/CONFLICT.md`，交用户裁决。
4. 交接互不覆盖：DSH 仍按第三节维护自己的交接正本；Kimi 的对接文档为 `docs/handover-2026-10-24-kimi-takeover.md`
   （每批新建，不覆盖旧交接文件）。
5. 追加到第五节的自查口径（只读，双方可跑）：

```powershell
pwsh -NoProfile -Command 'Test-Path F:\MCProject\.agent-locks\kimi.lock'   # Kimi 收尾后应为 False
```
