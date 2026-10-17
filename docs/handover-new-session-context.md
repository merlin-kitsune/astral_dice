# 新会话交接上下文（复制以下全文给新会话）

你是 astral_dice_multiloader-next 开发线的执行代理。⚠️ **本提示词面向 Linux 环境**——仓库本体是 Windows 侧开发树，两边的可用命令**不一样**，务必先读 §0 环境分界再动手。

先读这四份（顺序即优先级）：
1. `docs/handover-2.0.0-dev-next.md`（常态交接：规范/进度/测试结果/遗留项/坑）
2. `docs/handover-2.0.0-SNAPSHOT.97-chip-shop.md`（**最近一批功能**：筹码商店批的流程/语义/判据/未完成/新坑，逐条可复算）
3. `AGENTS.md` 的四节：工作树与路径纪律、二次验证规范、子代理与测试环境硬规则、版本优先级与跨线移植纪律
4. 涉及平台差异时：`docs/compat-1.20.1-forge.md`（forge）或 `docs/compat-26.1.2-neoforge.md`（26.1.2）

> ⚠️ 上列 1~4 与 `AGENTS.md` 的「命令执行规范」都是**Windows 侧**口径（pwsh 7 / `F:\` 路径 / `.bat` 启动器）。在 Linux 上读它们当**知识**可以，照抄命令**不行** —— 见 §0。

## 0. 环境分界（Linux 侧 Agent 必读）

- **仓库本体 = Windows 侧开发树** `F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`）。Linux 侧的实际挂载点**未知** ⇒ 开工第一条命令先自证位置与版本：
  ```bash
  git rev-parse --show-toplevel && git rev-parse --abbrev-ref HEAD && git log --oneline -1
  ```
  此后本文件用 `<repo>` 指代该路径（不要假设它等于任何 Windows 路径）。
- ⛔ **禁止执行本仓的 Windows 专属 PowerShell 工具链**：`scripts/**/*.ps1`、`run/*.bat`。**即使 Linux 上装了 pwsh 也不要跑** —— 这些脚本硬编码 Windows 路径与进程管理（实证：`scripts/test/fabric/ft_stop.ps1:96/136`、`scripts/test/fabric/ft_build.ps1:151`、`scripts/devtools/Start-SelfTest.ps1:469` 的 `taskkill.exe /T /F`；`scripts/test/lib/Mt.Proc.psm1:739`、`scripts/test/mt.ps1:226` 的 `Start-Process -WindowStyle Hidden`；`fabric-1.20.1/build.gradle:312` 的 `D:/.minecraft/versions/1.20.1-Fabric 模组测试/mods`），在 Linux 上要么直接失败、要么**静默跑错范围**（判据不再可信，比失败更糟）。
  - ⚠️ **唯一例外 = `tools/check_lang_sync.ps1`**（lang 三语键集同步闸门；521 行，无盘符、无 `taskkill`、无 `Start-Process`）—— **CI 已在 Linux 上跑通它**（`.github/workflows/build.yml:30` = `runs-on: ubuntu-latest`；`:109`/`:112`/`:115`/`:121` = `pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir <线>/src/main/resources/assets/astral_dice/lang`）⇒ Linux 侧若有 pwsh 7，可以跑它。
- ✅ **Linux 侧可用**（只读取证 / 静态核对）：
  - `git`：`log` / `show` / `diff --numstat` / `status --short -uall` / `ls-files --eol`
  - `grep -rn` / `sed` / `find` / `file`（读源码、核字面量、查行尾）
  - `python3 tools/*.py`：**跨平台**静态闸门，例如 `verify_party_api.py`、`audit_patchouli_keys.py`、`verify_effect_icons.py`、`verify_fabric_assets.py`、`verify_selector_key_ownership.py`、`audit_mixin_injection.py`（参数以各自 `--help` 为准）
  - `unzip -l` / `jar tf` / `javap -p`（开产物核验签名与常量，需 JDK）
  - ⚠️ **`./gradlew <task>`：2026-10-13 起默认禁用、回退 Windows 侧**（原因与数据见下方「WSL 构建回退」条）。CI 在 Linux 上实证过（`build.yml:106/:127`），本机 WSL 也能跑通但速度不可接受；仓内保留 `scripts/test/mt_build.sh`（60s 窗口看护）作为将来恢复时的入口。部署任务 `pushToGame` 写死 Windows 路径，WSL 侧永远不可用。
- ❌ **Linux 侧不可用（Windows 专属）**：`scripts/test/mt.ps1`（测试台 `env/launch/case/report/stop`）、`scripts/test/mt_build.ps1`、`scripts/verify/*.ps1`（守门 + 变异）、`scripts/test/fabric/ft_*.ps1`、`run/Start-*.bat`、以及**整合包部署**（各线 `build.gradle` 的 `pushToGame`/`pushToDevRun` 写死 `D:\.minecraft\versions\…`）。
- **WSL 构建回退（2026-10-13 用户裁决）**：默认构建一律改回 **WSL 互操作调 Windows `pwsh.exe` 跑 `scripts/test/mt_build.ps1`**（`/mnt/c/Program Files/PowerShell/7/pwsh.exe`，cwd 自动映射 `F:\`；命令前缀需补 `$env:PATHEXT='.COM;.EXE;.BAT;.CMD'` —— interop 会话 PATHEXT 只有 `.CPL`，脚本内嵌套 `pwsh` 会失名）。回退原因（实测数据）：① 仓库在 DrvFs（`/mnt/f`，9p）上，Gradle 每次全树 stat/hash 付 9p 税 —— 对 `neoforge-1.21.1` 的 335 个 java 源文件 find+stat：9p **1.65s** vs ext4 **0.006s（≈275×）**，且第二次**无缓存改善**（1.649s）；写 200 小文件 0.58s vs 0.006s（≈100×）；② 冷 `~/.gradle` 的一次性管线被放大：1.21.1 `:classes` 首跑 **9m22s**（热身后 4s），forge LegacyForge 冷管线连吃 3×570s 超时仍未完成（mergetool 单步 271s），每次超时重启还重复昂贵的配置阶段；③ 「产完不退」驻留是本仓**已知 bug（Windows 同样存在**，mt_build 系列守护脚本即为此设），非 WSL 特有。环境已备（若将来恢复）：JDK 17/21/25 @ `/usr/lib/jvm/java-*-openjdk-amd64`、`~/.gradle/gradle.properties` 已配 `127.0.0.1:7897` 代理、`~/.m2` 已备 starengine_lib `2.0.0-SNAPSHOT.12` 四线工件、`scripts/test/mt_build.sh` 已在仓。
- **github-cli（2026-10-13）**：WSL 内已装**原生** `gh 2.102.0`（`~/.local/bin/gh`，`~/.profile` 已含该 PATH）；⛔ **禁止把 Windows 版 gh.exe 带进 WSL**（会导致 codex 无法启动）；token 以 **`GH_TOKEN` 环境变量**提供（**已配置并实测可用**：账号 `merlin-kitsune`，scopes = gist/repo/workflow（缺 `read:org`，仅影响组织查询），存于 `~/.profile`；WSL 直连 api.github.com 实测可用，如遇网络抖动再加 `HTTPS_PROXY=http://127.0.0.1:7897`）。
- 🔁 **需要上面那些 Windows 专属判据（守门 / 变异 / 用例 / 实机 / 收停）时**：把「命令 + 判据字符串 + 期望输出」写清楚，交**用户或 Windows 侧**执行并回传输出，再下结论。**没有真实输出，禁止声称「守门全绿 / 用例 PASS / 已构建 / 已部署」**；读数一律标明来源（谁跑的、哪台机器、什么命令）。
- **行尾**：本树 LF / CRLF 混存（`neoforge-1.21.1` 的 Java 与 `docs/handover-*` 是 LF；`forge-1.20.1` 的 Java、`scripts/test/resources/kubejs/1.21.1/*.js`、`docs/compat-1.20.1-forge.md`、`AGENTS.md` 是 CRLF）。**禁止全仓转换行尾**（会污染 diff 与逐字节判据）；核对用 `git ls-files --eol -- <路径>`。

## 基本盘
- 工作树：Windows 侧 `F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`）；**HEAD 与四线版本一律以实测为准**（`git log --oneline -1`、四条 `gradle.properties` 的 `mod_version`）—— 历史：上一版本文档写的是 `.96`；筹码商店批 = **`.97`（`6c81b7fe`，192 文件）**；交接文档批 = **`.98`（`e06451c5`，11 文件）**；本文件随 **`.99`** 落地（Linux 提示词改写）；
- 工作树状态：本机（Windows）除 **4 行** `?? %SystemDrive%/ProgramData/Microsoft/Windows/Caches/*.db`（与本项目无关的路径解析垃圾，勿提交、勿用 `git add -A`）外**干净**；**未 push**。⚠️ 那 4 行是 Windows 侧特有的，Linux 侧应看不到 —— 若 Linux 侧出现**别的**未跟踪文件，如实报告、不要擅自删；
- 另一常驻工作树 `F:\MCProject\astral_dice_multiloader`（multi-main，封包态）：**禁止触碰**；收尾核对它必须为空且 HEAD 不变（`git -C <主线> status --short --untracked-files=all` 输出 0 行）；
- 优先级：**P0 = neoforge-1.21.1 + forge-1.20.1（同批）**；**P1 = neoforge-26.1.2 + fabric-1.20.1（待移植）**。

## 已完成（P0 两线对等）
事件体系 13 个 + 事件卡 GUI（0.2s 进 / 1.0s 停 / 0.2s 缩小淡出，弹卡后 38t 结算）+ 大侦探立牌主动技能「事件选择界面」（2 选 1、15s 超时/死亡/登出强触发第一项）+ 调查阶段 4 阶分阶图标与 1:00 计时器 + 全模组发币优先入星币钱包 + 手册/tooltip 文案同步 + 卡牌商店方块（四态朝向 + 自有碎裂粒子）+ **筹码商店（`.97`）**：61 枚筹码的完整抽取与售卖（3 候选加权抽、排除已拥有、三格同价 10 起每买 +5 上限 100、刷新 3~5 次且 30 分钟恢复 1 次、余额不足/次数为 0/物品栏满三种拒绝、方块四向朝向、界面含品质外框与红闪）。

## 立即待办（按用户指令选一个）
A. **P1 移植**：把 `.61`~`.97` 全批移植到 `neoforge-26.1.2` 与 `fabric-1.20.1`（已核：两线**没有** `client/EventCardOverlay.java`、`block/CardShopBlock.java`、`shop/ChipShopCatalog.java` ⇒ 整体新建）。逐线流程：`mt_build` → `scripts/verify/**` 守门 → `code_review` → 实机（需用户下达）。移植时必须带上 `a>=4` 文字守卫、四态 `facing` + 模型 `particle`、筹码屏「条带内相对坐标」口径。**Linux 侧可做**：源码落地、平台差异核对、`python3` 类闸门、`grep`/`git diff` 复算；**需要 Windows 侧**：`mt_build` / `scripts/verify/*.ps1` / 实机。
B. **forge 线实机欠账**：筹码商店在 1.20.1 只过了静态判据；补齐要 ① 把服务端探针 `c*` 命令族与客户端 770/771/772 场景移植到 `scripts/test/resources/kubejs/1.20.1/**`（注意 1.20.1 的 `sp_closeMenu`/`SP_ItemStack`/六参 `use(...)` 形态）② 复制 5 条用例为 `CHIP-*-1.20.1.json` ③ 用户下达后跑 env/launch/case/stop --purge-saves。**整条都在 Windows 侧**。
C. **待用户裁决的历史欠账**（详见批次文档 §4）：fabric 线产物停在 `.49`（`.50` 起至今未构建，`mt_build` 不认 fabric）；守门 6 条盲区 + F1/F4/F6/F8/F13/F14 补变异；筹码刷新锚点的 P2 语义（到上限后花 1 次被下一 tick 立即补回）；`--phase report` 总判 FAIL 只因同世代两条历史红标（`SHOP-M-1.21.1`/`DIAG-1.21.1`）；`.97` 及其后产物自身的实机证据要不要重取。
D. 用户指定的其它任务（sound/ 21 个 wav 转 ogg、设计待裁决项等，见常态交接文档 §5）。

## 常用命令

### A) Linux 侧（可直接跑；只读取证 / 静态核对）
```bash
cd <repo>
git status --short -uall ; git log --oneline -3 ; git diff --numstat
git ls-files --eol -- neoforge-1.21.1/src/main/java | head            # 行尾核查
grep -rn "CHIPUI_TOTAL_FAIL" scripts/verify/verify_chip_shop_ui.ps1   # 只读对照判据字面量（不要执行该脚本）
python3 tools/verify_party_api.py --help                              # 跨平台闸门（参数以 --help 为准）
python3 tools/audit_patchouli_keys.py
jar tf neoforge-1.21.1/build/libs/astral_dice-*.jar | head            # 开产物核验
```

### B) Windows 侧（**Linux 上禁止执行**；仅用于向用户 / Windows 侧下单）
```powershell
cd F:\MCProject\astral_dice_multiloader-next
pwsh -NoProfile -File scripts/test/mt_build.ps1 --version 1.21.1 --timeout 300
pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1          # 两线守门，判据 CHIPUI_TOTAL_FAIL: 0 / CHIPUI_RESULT: OK
pwsh -NoProfile -File scripts/verify/mutate_chip_shop_ui.ps1          # 变异 37 条，判据 MUT_TOTAL: 37，判红 37 / MUT_RESULT: OK
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase env
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase launch
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase cases --case cases/CHIP-LOGIC-1.21.1.json
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase report --allow-auto
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase stop --force --purge-saves
```
手动启动游戏（人工自测，Windows 侧）：双击 `run/Start-1.21.1.bat`（或 1.20.1 / 26.1.2）——脚本只做启动，无任何前置判断。

## 必须遵守的几条铁律
0. **实施分工（2026-10-12 用户裁决，同日二次裁决修订：写代码一律派 Subagent）**：**P0 基准线 `neoforge-1.21.1` 派子代理编写**（主 Agent 只负责规划 / 派活 / 验收 / 提交，不亲自编写任何代码）→ `code_review` 复核 → **派子代理做独立功能审查**（静态取证 / 编译 / `scripts/verify/**` 守门 / 与需求逐字对账）→ 主 Agent 逐条回应、派新子代理修完确认无误后才派子代理做 `forge-1.20.1` 移植 → 移植线再 `code_review` + **另派一个独立子代理**功能审查；派发优先 DeepSeek（功能实现用 DeepSeek V41 Flash）；一个子代理只做一个任务、用完即弃；**复核分级（2026-10-13）**：非代码改动与小规模代码修正只做 code_review、免子代理功能复验；新功能按改动规模自判复验范围（全量/部分/不做，记录依据）；大型变更全量复核；实机仍须用户许可，无法仅靠复核确认时须说明并请求实机；
1. **复核优先用 `code_review`**（一次性独立会话，brief 一次写全，禁止把主会话结论当事实喂它）；该工具实例报错/超时 ⇒ 改派**一次性独立子代理**，判据规格不变；
2. **实机验证必须由用户明确下达**；靶子非亡灵；收尾 `--purge-saves`（判据 `MT_CLEANUP_SAVES: PURGED n` + `MT_CLEANUP_RESULT: OK`）+ 删临时探针 + 贴 `git status`（以上均为 Windows 侧动作）；
3. **每次提交升四线版本 + 两份 CHANGELOG 段头**（只改 `<N>`；bump 在 build 之前；一次提交 = 一个版本号）；**默认禁止 `git push`**；
4. **不改动另一工作树**；写文件用绝对路径；提交不要用 `git add -A`（会带上那 4 个垃圾目录）；
5. **改动后旧实机证据作废**：先停旧实例再测新构建；
6. **payload 类不得直接引用 `net/minecraft/client/**`**（专服会崩），客户端动作下沉 helper + dist 守卫；
7. **跨环境纪律（Linux 侧新增）**：Linux 侧只做「读 + 写源码 + 可跨平台的闸门」；**不得**把 Windows 专属判据在 Linux 上转译/模拟后当判据（真要移植测试台属独立任务，需用户裁决）；**不得**用实测口吻描述未在本机跑过的东西；
8. **批处理脚本空参要用 `if not "%~1"==""` 守卫**；手动启动脚本不要加前置判断（Windows 侧存档规则）。

## 子代理与实机的小抄（踩过的坑）
- 子代理默认 **DeepSeek**（`provider=deepseek-official` / `model=deepseek-flash`，实测可用）；mimo/kimi 在本 tool 实例被构造期禁用。
- 一个子代理只做一个任务、brief 自包含；先验证后下结论、每条结论附证据、优先找反例、取不到证据写「无法判定」。
- KubeJS 探针：命令同 tick 不生效（tick 末落地）⇒ 清空用 API 直写，摆位与放置拆两个 tick；Rhino 没有 `setYRot`，转向用 `teleport(...,yaw,pitch)`。
- **`inject_command` 走聊天通道 ⇒ 容器屏开着会被吞**；屏内动作只能用客户端探针的 `csCmd`（`mc.player.connection.sendCommand`，不带前导斜杠）。
- 筹码屏命中坐标是「条带内相对」+ `(STRIP_X=34, STRIP_Y=6)`；探针少加偏移会「点击落空但返回 true」。
- `tools.list_agents`/`tools.job_list` 必须传空对象；`tools.edit`/`tools.write` 改已存在文件前必须先用 `read` 工具读过。
- 实机收尾判据（Windows 侧）：`MT_CLEANUP_SAVES: PURGED n` + `MT_CLEANUP_RESULT: OK`；再核 `fml.modFolders` java 进程数 = 0。
- **Linux 侧另有一条**：除 CI 里的 `tools/check_lang_sync.ps1`（lang 键集闸门）外，本仓 bash/POSIX 侧入口只有 `scripts/test/mt_build.sh`（构建看护，默认停用见 §0）—— 探针、用例、变异、守门 `scripts/verify/*.ps1`、收停仍全部依赖 Windows 测试台（经 WSL 互操作执行）；在 Linux 上要「证明」某事，只能用**源码 / 产物 / git / CI 日志**四类实物证据。

## 交接时的工作树状态
- 生成时基线：**以 `git log -1` 实测为准**（本文件随 **`.99`** 提交落地；前一批为交接文档批 `.98` = `e06451c5`，再前为筹码商店批 `.97` = `6c81b7fe`，192 文件）；`git status --short -uall` = **仅 4 行** `?? %SystemDrive%/…db`（Windows 侧特有垃圾）；
- **三条线**（P0 两线 + P1 的 `neoforge-26.1.2`）产物**以 `ls <线>/build/libs` 实测为准**（**本机现为 `.99`**：本批由 Windows 侧 `mt_build` 构建三条线，`MT_BUILD: OK` 3s / 5s / 3s ⇒ `neoforge-1.21.1/build/libs`、`forge-1.20.1/build/libs`、`neoforge-26.1.2/build/libs`）；**fabric 线产物停在 `.49`**；**P1 两线尚未移植筹码商店与事件卡 GUI 等**；
- 测试世界在最后一次实机收尾时已 `--purge-saves` 清理 ⇒ 下次实机先 `--phase env`；
- 未提交内容：**无**（除那 4 行垃圾）。

> **最新交接条目（2026-10-17）**：`docs/agent/AGENTS-full.md` 的「## 待办交接」区**第 36 条**「立牌批验证闭环 + 选卡界面 + Jade 倒计时（2026-10-17）」—— 定位：`grep -n '立牌批验证闭环' docs/agent/AGENTS-full.md`。含本批提交链、实机闭环证据、**两项实机欠账**（选卡界面 / Jade 倒计时，均须用户下达）与 P1 两线移植搁置项。
