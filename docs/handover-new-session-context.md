# 新会话交接上下文（复制以下全文给新会话）

你是 astral_dice_multiloader-next 开发线的执行代理。⚠️ **本提示词是 Windows 侧口径**（PowerShell 7 / `F:\` 路径 / 本仓工具链为 Windows 专用）：仓库、工具链、dev 游戏实例都在**本机 Windows** 上，直接从 Windows 会话驱动即可，**不要**再从 WSL/Linux 中转（历史结论见 §0.2，仅作知识）。

先读这四份（顺序即优先级）：
1. `docs/handover-2.0.0-dev-next.md`（常态交接：规范/进度/测试结果/遗留项/坑）
2. `docs/handover-2.0.0-SNAPSHOT.97-chip-shop.md`（**最近一批功能**：筹码商店批的流程/语义/判据/未完成/新坑，逐条可复算）
3. `AGENTS.md` 的四节：工作树与路径纪律、二次验证规范、子代理与测试环境硬规则、版本优先级与跨线移植纪律
4. 涉及平台差异时：`docs/compat-1.20.1-forge.md`（forge）或 `docs/compat-26.1.2-neoforge.md`（26.1.2）

> 最新交接条目：`docs/agent/AGENTS-full.md` 的「## 待办交接」区 **第 37 条**（2026-10-17 晚：选卡界面 + Jade 双线实机验证闭环与 4 项修复）与 **第 38 条**（2026-10-17 深夜：工具链回 Windows 原生）—— 定位：`grep -n '选卡界面+Jade 双线实机验证闭环\|工具链回 Windows 原生' docs/agent/AGENTS-full.md`。

## 0. 环境（Windows 原生，唯一支持）

### 0.1 位置与自证

- 仓库本体：`F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`）。
- 另一常驻工作树 `F:\MCProject\astral_dice_multiloader`（`multi-main`，封包态）：**禁止触碰**；收尾核它 `git -C <主线> status --short --untracked-files=all` 为 0 行且 HEAD 不变。
- 开工第一条命令（pwsh 7）自证位置与版本：
  ```powershell
  git -C F:\MCProject\astral_dice_multiloader-next rev-parse --abbrev-ref HEAD
  git -C F:\MCProject\astral_dice_multiloader-next log --oneline -1
  git -C F:\MCProject\astral_dice_multiloader-next status --short -uall
  ```
- HEAD 与四线版本**一律以实测为准**（`git log --oneline -1`；四条 `gradle.properties` 的 `mod_version`）。

### 0.2 工具链（纯 pwsh 7，Windows 专用）

全部在本机 Windows 直接跑（脚本硬编码 Windows 路径与进程管理：`taskkill`、`Start-Process -WindowStyle Hidden`、各线 `build.gradle` 的 `pushToGame` 目标 `D:\.minecraft\versions\…`）：

```powershell
# 构建（唯一入口；阶段 B）
pwsh -NoProfile -File scripts/test/mt_build.ps1 --version 1.21.1 --timeout 300
# 测试台（阶段 P / E / L / C / R）
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase env
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase launch
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase cases --case cases/<CASE>.json
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase report --allow-auto
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase stop --force --purge-saves
# 守门（机器判据）
pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1
pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir neoforge-1.21.1/src/main/resources/assets/astral_dice/lang
# 跨平台 python 静止闸门
python tools/verify_effect_icons.py ; python tools/audit_patchouli_keys.py
# 工具链语法门（改脚本后必跑；判据 = 解析失败 0 个）
pwsh -NoProfile -File scripts/devtools/Test-MtSyntax.ps1
```

- 手动启动（人工自测）：双击 `run\Start-1.21.1.bat`（或 1.20.1 / 26.1.2）—— 脚本只做启动，无任何前置判断。
- fabric 线不走 `mt_build.ps1`（不认该线）：用 `scripts/test/fabric/ft_build.ps1`。
- ⛔ **不再支持从 WSL/Linux 驱动本工具链**（2026-10-17 晚收敛）：`scripts/test/mt_build.sh` 已删除；`/mnt/...`、`wslpath`、`cmd.exe` 中转等写法一律不要再引入。
- 历史环境事实（2026-10-13～17 的 WSL 驱动阶段，**只作知识、不要据此施工**）：① `/mnt/f`（9p）上 Gradle 全树 stat/hash 慢 ≈275×（`neoforge-1.21.1` 335 个源文件 find+stat：9p 1.65s vs ext4 0.006s），`:classes` 冷跑 9m22s、forge 冷管线连吃 3×570s 超时；② 从 WSL interop 直接起的 `pwsh.exe` 里 `& <exe>` 会静默不执行子进程（`$LASTEXITCODE` 不被创建），曾把 `mt_launch` 的 doImmediateRespawn 闸门误报成 `MT_LAUNCH: ERROR`（D4-5）；③ WSL 默认不透传 Windows PATH（`pwsh.exe` 需全路径）。
- 为②留下的 `lib/Mt.Proc.psm1 → Invoke-MtChildProcess`（`Start-Process -NoNewWindow -PassThru` + `Process.ExitCode` + `-TimeoutSec` / `-Capture`）**保留**，作为工具链统一的子进程原语：Windows 原生下同样更稳（退出码不经 `$LASTEXITCODE`、支持超时强杀），`mt_launch` / `mt_stop` / `mt_loadergate` 继续用它。
- 行尾：本树 LF / CRLF **逐文件混存**，不要按目录猜 —— 一律以 `git ls-files --eol -- <路径>` 实测为准（2026-10-17 实测：`neoforge-1.21.1/src/main/java/**/*.java` = crlf 315 / lf 41 / mixed 2；`AGENTS.md` = LF；`docs/handover-1.3.0.md` = w/crlf；`docs/handover-new-session-context.md` = LF；`mt_build.ps1` 为 w/crlf，属 `core.autocrlf=true` 的正常检出）。**禁止全仓转换行尾**；改文件前先核对，避免整文件行尾翻转污染 diff。

## 基本盘

- 工作树：`F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`）；**HEAD 与四线版本一律以实测为准**——第 37 条交接基线 `61968fc5`（第 36 条 `f7ee13e0` → 首测 `96352433` → 修复 `71e38e43` → Jade 开关 `2cb56b13` → mt 工具链 `b05ce817` → WSL pwsh 健壮性 `95b29313` → 交接 `61968fc5`）；四线 `mod_version` = **`2.0.0-SNAPSHOT.128+<后缀>`**；
- 工作树状态：除 **4 行** `?? %SystemDrive%/ProgramData/Microsoft/Windows/Caches/*.db`（与本项目无关的路径解析垃圾，勿提交、勿用 `git add -A`）与 **2 行** `?? models/*.bbmodel`（归属未定，见第 37 条）外**干净**；**未 push**（`git status -sb` 的 `ahead N` 随提交递增，以实测为准；推送须用户明确要求且走代理 `http://127.0.0.1:7897`）；
- 优先级：**P0 = neoforge-1.21.1 + forge-1.20.1（同批）**；**P1 = neoforge-26.1.2 + fabric-1.20.1（待移植）**。

## 已完成（P0 两线对等）

事件体系 13 个 + 事件卡 GUI（0.2s 进 / 1.0s 停 / 0.2s 缩小淡出，弹卡后 38t 结算）+ 大侦探立牌主动技能「事件选择界面」（2 选 1、15s 超时/死亡/登出强触发第一项）+ 调查阶段 4 阶分阶图标与 1:00 计时器 + 全模组发币优先入星币钱包 + 手册/tooltip 文案同步 + 卡牌商店方块（四态朝向 + 自有碎裂粒子）+ **筹码商店（.97）**：61 枚筹码的完整抽取与售卖（3 候选加权抽、排除已拥有、三格同价 10 起每买 +5 上限 100、刷新 3~5 次且 30 分钟恢复 1 次、余额不足/次数为 0/物品栏满三种拒绝、方块四向朝向、界面含品质外框与红闪）+ **卡梅/天音立牌批与选卡界面/Jade 倒计时（.128，第 36/37 条）**：类型过滤三键按下原地不动、双击/Shift 一次只入 1 张、`-PtestJade=false` 无 Jade 启动验证。

## 立即待办（按用户指令选一个）

A. **P1 移植**：把 `.61`~`.97` 全批（事件卡 GUI、事件选择界面、分阶图标与 1:00 计时器、发币漏斗、手册/tooltip 文案、卡牌商店方块、筹码商店整批）与 `.128` 批（立牌批 + 选卡界面 + Jade 倒计时）移植到 `neoforge-26.1.2` 与 `fabric-1.20.1`（已核：两线**没有** `client/EventCardOverlay.java`、`block/CardShopBlock.java`、`shop/ChipShopCatalog.java` ⇒ 整体新建）。逐线流程：`mt_build` → `scripts/verify/**` 守门 → `code_review` → 独立功能审查子代理 → 实机（需用户下达）。移植时必须带上 `a>=4` 文字守卫、四态 `facing` + 模型 `particle`、筹码屏「条带内相对坐标」口径。
B. **forge 线实机欠账**：筹码商店在 1.20.1 只过了静态判据；补齐要 ① 把服务端探针 `c*` 命令族与客户端 770/771/772 场景移植到 `scripts/test/resources/kubejs/1.20.1/**`（注意 1.20.1 的 `sp_closeMenu`/`SP_ItemStack`/六参 `use(...)` 形态）② 复制 5 条用例为 `CHIP-*-1.20.1.json` ③ 用户下达后跑 env/launch/case/stop --purge-saves。
C. **待用户裁决的历史欠账**（详见第 37 条 ⑤）：两线 dev 客户端**运行期共存**仍互撞 `glfw.dll`（是否升级为会话级互斥或分机）；Shift+点击现在也一次只搬 1 张是否符合预期；键盘改绑的拾取键未拦截（代码推论）；`mt_loadergate` 既有 FAIL（`mixinbooster` 三段 `mandatory` 口径与设计冲突，改动前就存在）；`--phase report` 历史红标（`SHOP-M-1.21.1` / `DIAG-1.21.1`）；fabric 线产物停在 `.49`。
D. 用户指定的其它任务（`sound/` 21 个 wav 转 ogg、设计待裁决项等，见常态交接文档 §5）。

## 必须遵守的几条铁律

0. **实施分工（2026-10-12 用户裁决，同日二次裁决修订：写代码一律派 Subagent）**：**P0 基准线 `neoforge-1.21.1` 派子代理编写**（主 Agent 只负责规划 / 派活 / 验收 / 提交）→ `code_review` 复核 → **派子代理做独立功能审查**（静态取证 / 编译 / `scripts/verify/**` 守门 / 与需求逐字对账）→ 主 Agent 逐条回应、派新子代理修完确认无误后才派子代理做 `forge-1.20.1` 移植 → 移植线再 `code_review` + **另派一个独立子代理**功能审查；派发优先 DeepSeek（功能实现用 DeepSeek V41 Flash）；一个子代理只做一个任务、用完即弃；**复核分级（2026-10-13）**：非代码改动与小规模代码修正只做 `code_review`、免子代理功能复验；新功能按改动规模自判复验范围（全量/部分/不做，记录依据）；大型变更全量复核；实机仍须用户许可，无法仅靠复核确认时须说明并请求实机；
1. **复核优先用 `code_review`**（一次性独立会话，brief 一次写全，禁止把主会话结论当事实喂它）；该工具实例报错/超时 ⇒ 改派**一次性独立子代理**，判据规格不变；
2. **实机验证必须由用户明确下达**；靶子非亡灵；收尾 `--purge-saves`（判据 `MT_CLEANUP_SAVES: PURGED n` + `MT_CLEANUP_RESULT: OK`）+ 删临时探针 + 贴 `git status`；
3. **每次提交升四线版本 + 两份 CHANGELOG 段头**（只改 `<N>`；bump 在 build 之前；一次提交 = 一个版本号）；**默认禁止 `git push`**；
4. **不改动另一工作树**；写文件用绝对路径；提交不要用 `git add -A`（会带上那 4 个垃圾目录）；
5. **改动后旧实机证据作废**：先停旧实例再测新构建；
6. **payload 类不得直接引用 `net/minecraft/client/**`**（专服会崩），客户端动作下沉 helper + dist 守卫；
7. **工具链纪律（Windows 原生）**：只在本机 Windows 跑 pwsh 7 工具链；**禁止**把它搬到 WSL/Linux 转译或模拟后当判据，也禁止用 Linux 侧 `pwsh` 跑本仓脚本（依赖 `Add-Type` / `user32` / `taskkill` 与 Windows 路径）。确需 Linux 侧只读取证时，仅用 `git` / `grep` / `python3 tools/*.py`（跨平台闸门），并如实标明读数来源（谁跑的、哪台机器、什么命令）；**不得**用实测口吻描述未在本机跑过的东西；
8. **批处理脚本空参要用 `if not "%~1"==""` 守卫**；手动启动脚本不要加前置判断（Windows 侧存档规则）。

## 子代理与实机的小抄（踩过的坑）

- 子代理默认 **DeepSeek**（`provider=deepseek-official` / `model=deepseek-flash`，实测可用）；mimo/kimi 在部分 tool 实例被构造期禁用。
- 一个子代理只做一个任务、brief 自包含；先验证后下结论、每条结论附证据、优先找反例、取不到证据写「无法判定」。
- KubeJS 探针：命令同 tick 不生效（tick 末落地）⇒ 清空用 API 直写，摆位与放置拆两个 tick；Rhino 没有 `setYRot`，转向用 `teleport(...,yaw,pitch)`。
- **`inject_command` 走聊天通道 ⇒ 容器屏开着会被吞**；屏内动作只能用客户端探针的 `csCmd`（`mc.player.connection.sendCommand`，不带前导斜杠）。
- 筹码屏命中坐标是「条带内相对」+ `(STRIP_X=34, STRIP_Y=6)`；探针少加偏移会「点击落空但返回 true」。
- `tools.list_agents`/`tools.job_list` 必须传空对象；`tools.edit`/`tools.write` 改已存在文件前必须先用 `read` 工具读过。
- 实机收尾判据：`MT_CLEANUP_SAVES: PURGED n` + `MT_CLEANUP_RESULT: OK`；再核 `fml.modFolders` java 进程数 = 0。

## 交接时的工作树状态

- 生成时基线：**以 `git log -1` 实测为准**（本文件随 2026-10-17 晚「工具链回 Windows 原生」提交落地；上一基线 `61968fc5`）；`git status --short -uall` = **4 行** `?? %SystemDrive%/…db` + **2 行** `?? models/*.bbmodel`（均非本批改动）；
- **三条线**（P0 两线 + P1 的 `neoforge-26.1.2`）产物**以 `ls <线>/build/libs` 实测为准**（现为 `.128`；本批不跑产品构建时沿用既有产物）；**fabric 线产物停在 `.49`**；**P1 两线尚未移植**（筹码商店 / 事件卡 GUI / 立牌批 / 选卡界面 / Jade 倒计时）；
- 测试世界在最后一次实机收尾时已 `--purge-saves` 清理 ⇒ 下次实机先 `--phase env`；
- 未提交内容：**无**（除那 6 行未跟踪）。

> **最新交接条目（2026-10-17）**：`docs/agent/AGENTS-full.md`「## 待办交接」**第 36 条**「立牌批验证闭环 + 选卡界面 + Jade 倒计时」—— `grep -n '立牌批验证闭环' docs/agent/AGENTS-full.md`。
> **最新交接条目（2026-10-17 晚）**：**第 37 条**「选卡界面+Jade 双线实机验证闭环与 4 项修复」—— `grep -n '选卡界面+Jade 双线实机验证闭环' docs/agent/AGENTS-full.md`。
> **最新交接条目（2026-10-17 深夜）**：**第 38 条**「工具链回 Windows 原生」—— `grep -n '工具链回 Windows 原生' docs/agent/AGENTS-full.md`。
