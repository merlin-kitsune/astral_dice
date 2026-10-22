你接手的是 MC 模组仓库 F:\MCProject\astral_dice_multiloader，分支 multi-main，版本 1.5.0-pre.3。
你是 DeepSeek Harness（DSH），在 Windows 原生环境工作（只用 git bash + PowerShell 7），直接在本机工作树动手。

## 第 0 步（必须先做）：修正你自己的规则

把 `AGENTS.md`（红线区）与 `docs/agent/AGENTS-full.md` 里所有 WSL 专用条款标注**失效**或改写为 Windows 口径，一条不漏，口径如下表：

| WSL 条款（原文位置：AGENTS.md 红线区 / 全局 `~/.codex` 规则） | 改写为 |
|---|---|
| `/etc/profile.d` 代理注入（`99-wsl-proxy.sh` 等）、`wsl-env-restore.sh` | Windows 侧代理走系统环境变量（Clash 127.0.0.1:7897），无注入脚本、无需恢复 |
| `/opt/zulu` + `use-jdk` 切换 JDK | JDK 用 `C:\Program Files\Zulu\zulu-21`（21.0.12）；1.20.1 的 toolchain 17 由 Gradle 从 `%USERPROFILE%\.gradle\jdks` 取（已缓存 Adoptium 17.0.20） |
| `rsync` 到 `~/work/adm-*` 副本再编译 | 副本口径作废：直接在工作树 `F:\MCProject\astral_dice_multiloader` 编译，产物与提交同源 |
| WSL git 对 worktree 报 `not a git repository` 的变通（`--git-dir=` / 改走 `cmd.exe`） | 该问题不存在：git 一律用 git bash 的 `git`，`git -C F:\...` 或先 `cd` 进工作树 |
| 「bash 里没有 pwsh / 只能 sh 跑测试」类兼容说明 | 作废：测试台一律 `pwsh -NoProfile -File <绝对路径>`，bash 只承担 git |

同时把 `docs/handover-1.5.0-pre.3-main.md` 的 §1（路径与命令）改成 Windows 口径（`F:\` 路径、`gradlew.bat`、`pwsh`）。
改完必跑 `python docs/agent/verify-split.py`，四项 `[OK]`（快照 / 红线区逐字 / 索引无空洞 / 体量）全绿才算过。

## 第 1 步：先读（按顺序，实读，不凭记忆）

1. `docs/handover-1.5.0-pre.3-main.md` —— §1 路径与命令、§4 实机读数、§5 遗留、§6 坑。
2. `AGENTS.md` 红线区 + `docs/agent/AGENT-SPLIT.md`（Codex / DSH 分工与互斥，见下节）。
3. `docs/agent/AGENTS-full.md` 按主题 `sed -n '<a>,<b>p'` 取用；行号总表 `docs/agent/AGENTS-TOC.md`。
4. `scripts/test/TESTING-SPEC.md`、`docs/compat-1.5.0-porting.md`。

## 基本盘（版本/坐标/交付面/投放/测试台，全部 Windows 路径）

- 仓库：`F:\MCProject\astral_dice_multiloader`，分支 `multi-main`，交接基线 `7df4dab2`；开工先 `git status --short -uall`（应干净）与 `git rev-parse --short HEAD`。四子项目同树：`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`。
- 版本：四线 `mod_version = 1.5.0-pre.3+<加载器后缀>`；`starengine_version = 1.1.0-pre.2`；jar 内硬前置 `[1.1.0-pre.2,2.0.0)`、`META-INF/jarjar/` 计数 0。
- 交付面：**只 P0** = `neoforge-1.21.1` + `forge-1.20.1`；P1（26.1.2 / fabric）**1.5.0 正式版前不验证、不移植**。
- P0 产物（各自 `build\libs`，2026-10-22 14:25）：
  - neo：`astral_dice-1.5.0-pre.3+neoforge_1.21.1.jar`，2 555 453 B，sha256 `09980b2e0927f99a9086084f7b426d09c6ab038fdc43a5d7e95107b13e849a02`
  - forge：`astral_dice-1.5.0-pre.3+forge_1.20.1.jar`，2 601 019 B，sha256 `70b347ba2367e13823a66789c22b05172f3a8ca8d3228060049d29f3c9695507`
- 投放（**成对**放 `astral_dice-*.jar` + `starengine-*-1.1.0-pre.2.jar`，缺库即启动失败 `Missing or unsupported mandatory dependencies`）：
  - neo → `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods`
  - forge → `D:\.minecraft\versions\1.20.1-Forge 模组测试\mods`
- 测试台：`scripts\test\mt.ps1`，`--version <线> --phase env|run|stop`。
- 未推送：开工时 **17 个提交**（2026-10-22 后又追加规则/守门/立牌冷却等提交，实况以
  `git rev-list --count origin/multi-main..HEAD` 为准）；**无** `1.5.0-pre.3` tag；默认**不 push / 不打 tag**（需用户放行）。
- 守门：**8 跑 7 绿**，唯一红 `verify_probe_class_refs`。⚠️ 2026-10-22 已查明：红点原有 6 处、**无一是源码缺口**
  （JEI 走 Curse Maven 未被脚本解析 / LWJGL 由加载器运行期提供 / 一处只出现在 JS 注释里 / 26.1.2 产物陈旧）；
  守门脚本已补 Curse Maven 解析 + `org.lwjgl.` 白名单 + 剥 JS 注释，现只剩 **1 处**（26.1.2 `build/libs` 停在
  `astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar`，2026-10-05）。详见交接正本 §5-1。

## 工具集（git bash / pwsh / JDK / gradlew.bat 的具体调用方式与自检命令）

- git（git bash）：`"C:\Program Files\Git\bin\bash.exe" -lc 'cd /f/MCProject/astral_dice_multiloader && git status --short -uall'`；PowerShell 里直接 `git -C F:\MCProject\astral_dice_multiloader status --short -uall`。git bash 内盘符写 `/f/`。
- pwsh：`C:\Program Files\PowerShell\7\pwsh.exe`；一律 `pwsh -NoProfile -File <绝对路径>.ps1`，禁用 Windows PowerShell 5.1（不支持三元 / `??` / `-AsHashtable`）。
- JDK：`JAVA_HOME=C:\Program Files\Zulu\zulu-21`（21.0.12，P0 主线用）；forge-1.20.1 的 `toolchain 17` 由 Gradle 走 `settings.gradle` 的 foojay resolver，已缓存在 `%USERPROFILE%\.gradle\jdks`，不要手改。
- 编译：在 `F:\MCProject\astral_dice_multiloader` 执行 `gradlew.bat :neoforge-1.21.1:classes`（或 `:jar`）；`.sh` 测试入口已删除，不要找也不要再造。
- 自检三连：`where java` / `java -version`（应为 Zulu 21.0.12）/ `gradlew.bat -v`（Gradle 版本 + JVM 一屏确认）。
- 收尾（每轮必做）：`pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version <线> --phase stop --force`；归档再加 `--purge-saves`，判据 `MT_STOP: OK` / `MT_CLEANUP_SAVES: PURGED <n>` / `MT_CLEANUP_RESULT: OK`，并核对 `Get-Process java` 无本流程残留。

## 分工与互斥（Codex vs DSH，见 docs/agent/AGENT-SPLIT.md）

- **DSH（你）= 唯一执行者**：写源码、编译、`git commit`（push 仍需用户放行）、实机 / `mt.ps1`、投放整合包。
- **Codex（Linux/容器侧）= 只读验证**：读源码、写 `docs/**`、跑 `code_review`；禁写源码、禁 commit、禁实机。
- 锁：动工作树前在 `F:\MCProject\.agent-locks\` 建 `dsh.lock`（PID + 时间 + 任务名），结束删除；同一工作树同时只允许一个 agent 跑 gradle。
- `git status` 出现**不属于自己**的改动 ⇒ 立即停手，在 `docs/agent/CONFLICT.md` 记一行（时间/文件/推测归属），交用户裁决。
- 每轮结束更新 `docs/handover-1.5.0-pre.3-main.md` §4（实机读数）与 §5（遗留）；Codex 只消费不回写。

## 立即待办（沿用现有 5 条，按优先级）

1. **（待用户放行实机）** 1.20.1 创造模式出牌扣卡复验：期望扣 **0** 张。静态取证已完成且成立 ——
   `forge-1.20.1/.../item/card/BaseEffectCardItem.java:337`、`:423`、`.../screen/CardChoiceMenu.java:134` 均为
   `if (!player.isCreative()) <shrink>`；neo 侧对应点用 `stack.consume(1, player)`（内建 instabuild 判定）。
2. ✅ **已完成（2026-10-22）**：`run/1.21.1/mods` 与 `run/1.20.1/mods` 已用 `build/libs` 覆盖并逐份核 sha256；
   两个 P0 整合包本来就是同一对 pre.3 jar（各自带 `starengine-*-1.1.0-pre.2.jar`）。
3. ✅ **已完成（2026-10-22）**：`verify_probe_class_refs` 六处红点全部定性为「守门自身缺陷 / 产物陈旧」，
   脚本已修三处，残留 1 处 = 26.1.2 旧 jar（P1 待重建）。
4. ✅ **已完成（2026-10-22）**：立牌冷却补齐 **9 个**「起冷却」子类（两线各 9 文件 × +2 行），
   `:neoforge-1.21.1:classes` + `:forge-1.20.1:classes` 双绿。`JasmineSignItem` / `KomachiSignItem` **故意不改**
   （它们是 `sign_active_max_cooldown` 的消费者，只缩短已有冷却）。
5. P1 欠账登记维护：JEI 筹码 / `BeamFx` / `GlobalEventGate` / `RepulsionEntity` / `CardShopMenu#planPlacement` / `EventDrawBlock`
   （已在交接正本 §5 Major-3 登记；1.5.0 正式版前不动）。

## 硬约束（Windows 版）

- 禁止项（无例外）：`wsl.exe`；`/mnt/...` 路径（含 `/mnt/f/...`）；`~/work/adm-*` 编译副本；`~/.codex` 里的 WSL 注入脚本（代理 / `GH_TOKEN` / JDK）—— 上述一律视为已失效，出现在旧文档里只当历史背景。
- 源码只在 `F:\MCProject\astral_dice_multiloader` 工作树内改；动 `AGENTS.md` 或 `docs/agent/AGENTS-full.md` 后必须跑 `python docs/agent/verify-split.py`，四项 `[OK]` 才算过。
- dev 的 AutoJS：forge 1.20.1 dev 必须用 `build\devlibs` 的 **named** jar（`AutoJS-forge-1.20.1-1.0.0-dev.jar`），整合包用 reobf jar；**两者不可同目录**。
- `--purge-saves` 会删 `testworld`：下轮实机前先 `--phase env`。
- P1 不碰：26.1.2 / fabric 的实现、验证、移植一律等用户下达。
- 不 push、不打 tag、不擅自装工具（`apt`/`npm`/`pip`/新 JDK 等一律先征得用户许可）。
- 独立复核用 Codex 侧 `code_review`（`mcp__code_review__code_review`）；派活走明文委派 `delegate_task`（一次一件事，timeout ≤1800s，禁轮询，结果只从返回值或落盘文件验收）；禁止 `fork_turns="none"`。
