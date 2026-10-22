你接手的是 MC 模组仓库 F:\MCProject\astral_dice_multiloader，分支 multi-main，版本 1.5.0-pre.4。
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

同时把 `docs/handover-1.5.0-pre.4-main.md` 的 §1（路径与命令）改成 Windows 口径（`F:\` 路径、`gradlew.bat`、`pwsh`）。
改完必跑 `python docs/agent/verify-split.py`，四项 `[OK]`（快照 / 红线区逐字 / 索引无空洞 / 体量）全绿才算过。

## 第 1 步：先读（按顺序，实读，不凭记忆）

1. `docs/handover-1.5.0-pre.4-main.md` —— §1 路径与命令、§4 实机读数、§5 遗留、§6 坑。
2. `AGENTS.md` 红线区 + `docs/agent/AGENT-SPLIT.md`（Codex / DSH 分工与互斥，见下节）。
3. `docs/agent/AGENTS-full.md` 按主题 `sed -n '<a>,<b>p'` 取用；行号总表 `docs/agent/AGENTS-TOC.md`。
4. `scripts/test/TESTING-SPEC.md`、`docs/compat-1.5.0-porting.md`。

## 基本盘（版本/坐标/交付面/投放/测试台，全部 Windows 路径）

- 仓库：`F:\MCProject\astral_dice_multiloader`，分支 `multi-main`，交接基线 `7df4dab2`；开工先 `git status --short -uall`（应干净）与 `git rev-parse --short HEAD`。四子项目同树：`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`。
- 版本：四线 `mod_version = 1.5.0-pre.4+<加载器后缀>`；`starengine_version = 1.1.0-pre.2`；jar 内硬前置 `[1.1.0-pre.2,2.0.0)`、`META-INF/jarjar/` 计数 0。
- 交付面：**只 P0** = `neoforge-1.21.1` + `forge-1.20.1`；P1（26.1.2 / fabric）**1.5.0 正式版前不验证、不移植**。
- P0 产物（各自 `build\libs`，2026-10-22 18:32，含筹码商店品质外框恢复 + 卡牌购买钱包音效批重建）：
  - neo：`astral_dice-1.5.0-pre.4+neoforge_1.21.1.jar`，2 558 631 B，sha256 `742132dd929754c84f6df362dacdd36453f5b7e6ce6feee15f780d231a8da59a`
  - forge：`astral_dice-1.5.0-pre.4+forge_1.20.1.jar`，2 604 187 B，sha256 `4b984008a03f4bf8b236144eacdbc5814b821105fd4141f1b90125c521ff269a`
  - （forge dev 用 `build\devlibs` 的 named jar：2 574 721 B，`8f81e4c3ba6710b4a544e8c5df262c1adbb38690dd88dd1c30709ed0784e6e7f`）
- 投放（**成对**放 `astral_dice-*.jar` + `starengine-*-1.1.0-pre.2.jar`，缺库即启动失败 `Missing or unsupported mandatory dependencies`）：
  - neo → `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods`
  - forge → `D:\.minecraft\versions\1.20.1-Forge 模组测试\mods`
- 测试台：`scripts\test\mt.ps1`，`--version <线> --phase env|run|stop`。
- 未推送：**32 个提交**（HEAD 见 `git log -1`；实况以 `git rev-list --count origin/multi-main..HEAD` 为准）；**无** tag；默认**不 push / 不打 tag**（需用户放行）。
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
- **Codex（Linux/容器侧）= 只读验证**：读源码、写 `docs/**`、跑 `dsh_code_review`；禁写源码、禁 commit、禁实机。
- 锁：动工作树前在 `F:\MCProject\.agent-locks\` 建 `dsh.lock`（PID + 时间 + 任务名），结束删除；同一工作树同时只允许一个 agent 跑 gradle。
- `git status` 出现**不属于自己**的改动 ⇒ 立即停手，在 `docs/agent/CONFLICT.md` 记一行（时间/文件/推测归属），交用户裁决。
- 每轮结束更新 `docs/handover-1.5.0-pre.4-main.md` §4（实机读数）与 §5（遗留）；Codex 只消费不回写。

## 立即待办（2026-10-22 pre.4 收尾后，按优先级）

**2026-10-22 19:00–19:40 已做过一轮 pre.4 全量实机（用户下达）：P0 双线各 10 条用例 = 9 条机器判据 PASS + 1 条截图取证**（`PRE4-CHIPSHOP-FRAME-*` 的 vision 断言在测试台是 DELEGATED，不判红绿），读数见正本 §4.8（新增 §4.8 小节）；下面第 1/7/8/9 条的「待用户放行实机」已全部落地（其中 7 的观感、8 的计时、9 的外框都已按机器行/vision 判定；音效与粒子「好不好看/好不好听」仍属人工观感项）。

1. ✅ **已完成（2026-10-22 实机）** pre.4 实体拆除口径：强化拒止应「只吃玩家近战伤害、可按实际伤害拆掉、归零同一 tick 消失且无生物式躺倒动画」；
   路障拆除同理。判据面：`/astralparty killrepulsion all` / `killbarricade all`（权限 2，落 `AFTERM|type=…|killed=…|…` 机器行）+ 玩家实砍。
   静态判据已全绿（`kill()` / `hurt` / `getHurtSound` 字节码、图标 md5、lang 1045 键），见交接正本 §5-13。
2. ✅ **已完成（2026-10-22）** 1.20.1 创造模式出牌扣卡复验 **PASS**：创造扣 **0** 张、生存对照扣 **1** 张；
   用例 `scripts/test/cases/CARD-NOCOST-CREATIVE-1.20.1.json`。`:423`（自身牌 `use()`）与 `CardChoiceMenu:134` 仍只有静态判据（轻度欠账）。
3. ✅ **已完成（2026-10-22）**：`run/<线>/mods` 与两个 P0 整合包已重投 pre.4 并逐份核 sha256（正本 §2）。
4. ✅ **已完成（2026-10-22）**：`verify_probe_class_refs` 六处红点定性为「守门自身缺陷 / 产物陈旧」，残留 1 处 = 26.1.2 旧 jar（P1 待重建）。
5. ✅ **已完成（2026-10-22）**：立牌冷却补齐 **9 个**「起冷却」子类（两线各 9 文件 × +2 行），双线编译绿；`JasmineSignItem` / `KomachiSignItem` 故意不改。
6. P1 欠账登记维护：JEI 筹码 / `BeamFx` / `GlobalEventGate` / `RepulsionEntity` / `CardShopMenu#planPlacement` / `EventDrawBlock`；
   外加 **P1 两线筹码效果图标仍是带边框版**（pre.4 只换 P0 两线 11 张，正本 §5-14），以及 **P1 两线史莱姆被动仍是 20 tick(1 秒)、文案未改**（正本 §5-16：`neoforge-26.1.2/.../LuluSignItem.java:170`、`fabric-1.20.1/.../LuluSignItem.java:161`）。
   另：**强化拒止整张牌尚未移植到 P1 两线**（`grep -rni repulsion` 在两线 `src` 各 0 命中，正本 §5-19⑥）⇒ 随 P1 批次整体移植，勿按「改一行 rarity」估工。
7. ✅ **触发面已实机（2026-10-22）** 电击手套命中 FX：命中目标冒冷色电火花（`comet_azur_filament`×2 / `glintstone_spark`×7 / `frost_spark`×4）+ 圆心一次 `lightning_bolt_impact`（0.5 / 1.5）；只要「真正吃到伤害」的目标才有电光，音效只播一次。实现 `combat/ElectricShockFx.java`（P0 两线）+ `SoundPlayback` 新 7 参重载，判据见正本 §5-20（静态全绿，无实机）。
8. ✅ **已完成（2026-10-22 实机）** 史莱姆立牌被动 15 秒：P0 两线 `LuluSignItem.HURT_COOLDOWN_TICKS = 300`（原 20 = 1 秒），
   三语文案末尾均加「每 15 秒触发一次」；判据 = 受击两次间隔 <15s 时只有第一次生效（`AP_FX_*_HURT` 或治愈点数不增）。静态判据已绿（javap 常量 300 / lang 1045 键 / 三语 § 数相等），见正本 §5-15。
9. ✅ **外框已实机 / 音效到调用层（2026-10-22）** 筹码商店三色品质外框 + 卡牌购买钱包音效：2026-10-22 用户报障「外框丢了 / 买卡没 wallet 音效」；根因 = 提交 `fe4aa05f`（10-21 换筹码图标素材时连外框绘制一起删了），本批把 `screen/ChipShopScreen` 的 `TEX_QUALITY_1/2/3` + `qualityTex(int)` + `renderOffers` blit 恢复（`qualityCode` 优先 / `qualityFrameIndexOf` 兜底 / `NONE` 跳过），`shop/CardShopMenu#handleBuy` 成功尾段补 `SoundPlayback.playTo(buyer, ModSounds.WALLET_DEPOSIT.get())`。守门 `verify_chip_shop_ui.ps1` 的 G2b 已由反向改写为**正向**（旧反向断言在外框被误删时照样全绿），变异套件 M38/M39 同步反转（`MUT_TOTAL: 39，判红 39`）。判据见正本 §5-21（静态全绿，**无实机**）。

10. ✅ **已完成（2026-10-22）** pre.4 全量实机测试：P0 双线各 10/10 PASS，机器行原文见正本 §4.8；测试资产改动 = 两份 `astral_fixbatch_probe.js`（新增 `/astralfixbatch hurtby`、`fbxNow` 修正）+ 两份 `astral_bugfix_probe.js`（新增 `/astralprobe barplace`、`doLuluPrep` 补装金骰子）+ 6 条用例（`PRE4-*` 8 条新建 / `LULU-SIGN-*` 订正 2 条陈旧断言）。**产品源码零改动**。新坑见正本 §6 坑 23–26（Rhino 调不到 `p.hurt` 且 1.20.1 没有 `/damage`；裸 summon 路障必自毁；陈旧用例把规格变更误报成回归；长效增益用例必须 `/effect clear @s`）。

## 硬约束（Windows 版）

- 禁止项（无例外）：`wsl.exe`；`/mnt/...` 路径（含 `/mnt/f/...`）；`~/work/adm-*` 编译副本；`~/.codex` 里的 WSL 注入脚本（代理 / `GH_TOKEN` / JDK）—— 上述一律视为已失效，出现在旧文档里只当历史背景。
- 源码只在 `F:\MCProject\astral_dice_multiloader` 工作树内改；动 `AGENTS.md` 或 `docs/agent/AGENTS-full.md` 后必须跑 `python docs/agent/verify-split.py`，四项 `[OK]` 才算过。
- dev 的 AutoJS：forge 1.20.1 dev 必须用 `build\devlibs` 的 **named** jar（`AutoJS-forge-1.20.1-1.0.0-dev.jar`），整合包用 reobf jar；**两者不可同目录**。
- `--purge-saves` 会删 `testworld`：下轮实机前先 `--phase env`。
- P1 不碰：26.1.2 / fabric 的实现、验证、移植一律等用户下达。
- **`models/` / `images/` / `sound/` 是素材目录（2026-10-22 用户裁决）**：随时更新、有新文件属正常，`git status` 里出现这三处的未跟踪 / 变动项**不算冲突、也不算工作树脏**，但**不得删除 / 移动 / 重命名 / 擅自纳入提交**（需要入库先问用户）；`models/astral_anvil.bbmodel` 是用户下一步更新的素材，**只读**。提交一律显式路径，禁 `git add -A` / `git add .`。
- 不 push、不打 tag、不擅自装工具（`apt`/`npm`/`pip`/新 JDK 等一律先征得用户许可）。
- 独立复核用 DSH 插件 `@michengai/dsh-code-review`（工具 `dsh_code_review({ input })`：`input` 空 ⇒ 原生范围选择，非空 ⇒ 整段原样作为审查要求，范围写自然语言；报告以 GFM 回本会话）。**派活只用 DSH 自带子智能体**：一次性活 ⇒ `subagent`（全新 context，brief 完全自包含）；接续上下文 ⇒ `subagent_fork`；多人并行 ⇒ `spawn_teammate` + `team_task_*`；结果由子代理 `send_message` 回报，**禁轮询**。
- **派子代理一律屏蔽人格注入（2026-10-22 用户裁决）**：brief 除职业声明外必须原文带「不要使用任何人设 / 角色扮演 / 语气修饰 / 昵称自称，按你的职业身份以中性技术口吻输出」；审查 / 取证类任务用全新上下文的 `subagent`（`subagent_fork` 会继承带人设的对话；复核不在此列，复核只用 `dsh_code_review`）；子代理回报出现自称 / 卖萌 / 情绪表演即不合规，要求重发中性版。人设只作用于对用户的表达层。**禁止**用 Codex 的 `delegate_task` / `fork_turns="none"`（属另一宿主，DSH 不再引用）；主 Agent 只做识别需求 → 分配工作 → 验收成果。
