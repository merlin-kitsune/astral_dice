# 新会话交接上下文（复制以下全文给新会话）

你是 astral_dice_multiloader-next 开发线的执行代理。⚠️ **本提示词是 WSL + AutoJS 口径（2026-10-18 起，取代 10-17 的 Windows 原生口径）**：唯一施工副本在 WSL ext4（`~/work/astral_dice_multiloader-next`），构建/实机/用例都在 WSL 内；`/mnt/f/...` 收尾前只读，回写与 push 在 Windows 侧 `cmd.exe`。说明正本 `docs/wsl-autojs-toolchain.md`。

先读这四份（顺序即优先级）：
1. `docs/handover-2.0.0-dev-next.md`（常态交接：规范/进度/测试结果/遗留项/坑）
2. `docs/agent/AGENTS-full.md`「## 待办交接」**第 40 条**（**最近一批正本**：WSL 迁移 + AutoJS 测试台重建 —— 新入口/迁移映射/实机证据/欠账）；上一批 `.132` 见第 39 条；历史详案 `docs/handover-2.0.0-SNAPSHOT.97-chip-shop.md`（筹码商店批，逐条可复算）
3. `AGENTS.md` 的四节：工作树与路径纪律、二次验证规范、子代理与测试环境硬规则、版本优先级与跨线移植纪律
4. 涉及平台差异时：`docs/compat-1.20.1-forge.md`（forge）或 `docs/compat-26.1.2-neoforge.md`（26.1.2）

> **交接文档用法（接手顺序 / 交班义务）见 `AGENTS.md`〈代理协作契约 · C. 交接文档用法〉—— 接手先按那里的顺序实读，不信本文数字。**
> 最新交接条目：`docs/agent/AGENTS-full.md` 的「## 待办交接」区 **第 40 条**（WSL 迁移 + AutoJS 测试台重建）；上一批 **第 39 条**（`.132`：AutoJS 求值通道 / lingling 用例 / 强化拒止牌面）；再往前 **第 37 条**（2026-10-17 晚：选卡界面 + Jade 双线实机验证闭环与 4 项修复）与 **第 38 条**（2026-10-17 深夜：工具链回 Windows 原生）—— 定位：`grep -n '选卡界面+Jade 双线实机验证闭环\|工具链回 Windows 原生' docs/agent/AGENTS-full.md`。

## 0. 环境（WSL ext4 + AutoJS，唯一支持）

### 0.1 位置与自证

- **施工副本（唯一写面）**：`~/work/astral_dice_multiloader-next`（WSL ext4，原生 `.git`，分支 `multi-dev-next`）。
- 源目录（收尾前只读）：`/mnt/f/MCProject/astral_dice_multiloader-next`。
- 另一常驻工作树 `F:\MCProject\astral_dice_multiloader`（`multi-main`，封包态）：**禁止触碰**；收尾核它 `git -C <主线> status --short --untracked-files=all` 为 0 行且 HEAD 不变。
- 开工第一条命令（bash）自证位置与版本：
  ```bash
  cd ~/work/astral_dice_multiloader-next
  git rev-parse --abbrev-ref HEAD && git log --oneline -1 && git status --short -uall
  ```
- HEAD 与四线版本**一律以实测为准**（`git log --oneline -1`；四条 `gradle.properties` 的 `mod_version`）。

### 0.2 工具链（WSL bash + python3 + AutoJS）

```bash
cd ~/work/astral_dice_multiloader-next
scripts/test/mt.sh --version 1.21.1 --phase preflight
scripts/test/mt.sh --version 1.21.1 --phase build          # :neoforge-1.21.1:jar → run/1.21.1/mods
scripts/test/mt.sh --version 1.21.1 --phase env
scripts/test/mt.sh --version 1.21.1 --phase launch         # 脱离式 runClient + AutoJS 闸门
scripts/test/mt.sh --version 1.21.1 --phase case --case scripts/test/cases/<CASE>.json
scripts/test/mt.sh --version 1.21.1 --phase report
scripts/test/mt.sh --version 1.21.1 --phase stop --purge-saves
```

- 通道：AutoJS MCP（`eval_js` / `await_state` / `status` / `kubejs_log`），端点 `http://127.0.0.1:25585/mcp`。
- **没有**键鼠注入 / 窗口焦点 / 截图识别；旧 pwsh 游戏流程脚本已 `git rm`（39 个，可从 git 历史取回）。
- 静态守门仍可用：`scripts/verify/*.ps1`（WSL 自带 pwsh 7）、`tools/*.py`、`./gradlew :<线>:classes`。
- 收尾：`bash scripts/dev/sync-back.sh`（dry-run）→ `--apply` → Windows `cmd.exe /c "git add <逐路径> && git commit"` →（用户放行）push。

## 基本盘

- 工作树：`F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`）；**HEAD 与四线版本一律以实测为准**——最近交接基线 `bcae58f9`（**= `.132` 交接文档刷新提交自身**，2026-10-18；其前 `3c90e653` ← 第 38 条 `61968fc5` ← 第 37 条 `f7ee13e0` ← 首测 `96352433` ← 修复 `71e38e43` ← Jade 开关 `2cb56b13` ← mt 工具链 `b05ce817` ← WSL pwsh 健壮性 `95b29313`）；四线 `mod_version` = **`2.0.0-SNAPSHOT.132+<后缀>`**；
- 工作树状态：除 **2 行** `?? models/*.bbmodel`（用户 Blockbench 资产，勿用 `git add -A`）外**干净**；`%SystemDrive%` 那 4 行缓存目录**已于 2026-10-18 销账**（`git status --short -uall` 实测已不再出现）；**未 push**（`git status -sb` 的 `ahead N` 随提交递增，以实测为准；推送须用户明确要求且走代理 `http://127.0.0.1:7897`）；
- 优先级：**P0 = neoforge-1.21.1 + forge-1.20.1（同批）**；**P1 = neoforge-26.1.2 + fabric-1.20.1（待移植）**。

## 已完成（P0 两线对等）

事件体系 13 个 + 事件卡 GUI（0.2s 进 / 1.0s 停 / 0.2s 缩小淡出，弹卡后 38t 结算）+ 大侦探立牌主动技能「事件选择界面」（2 选 1、15s 超时/死亡/登出强触发第一项）+ 调查阶段 4 阶分阶图标与 1:00 计时器 + 全模组发币优先入星币钱包 + 手册/tooltip 文案同步 + 卡牌商店方块（四态朝向 + 自有碎裂粒子）+ **筹码商店（.97）**：61 枚筹码的完整抽取与售卖（3 候选加权抽、排除已拥有、三格同价 10 起每买 +5 上限 100、刷新 3~5 次且 30 分钟恢复 1 次、余额不足/次数为 0/物品栏满三种拒绝、方块四向朝向、界面含品质外框与红闪）+ **卡梅/天音立牌批与选卡界面/Jade 倒计时（.128，第 36/37 条）**：类型过滤三键按下原地不动、双击/Shift 一次只入 1 张、`-PtestJade=false` 无 Jade 启动验证 + **`.132` 三批（第 39 条）**：AutoJS 运行时求值通道（MCP 主通道、KubeJS 降为 legacy；`eval_js` 只求最后表达式 / 非标量必须 stringify / bool 用 `!!()`；闸门 `scripts/test/lib/Mt.AutoJs.psm1` 的 `-Mode Raw/Json/Bool`）+ **怪力乱神（lingling）立牌**（拦截者被动 + 能量突击主动，P0 两线；`MT_CASE_RESULT: LINGLING-1.21.1 = PASS`，42 step/22 assert）+ **强化拒止牌面**（北块 := 南块逆时针 90°，两线同 sha256，`FRONT vs BACK best=id 1.000` / `OVERALL: PASS`，立牌类实体不显示名条）+ 商店货架/文案与 tooltip 注释订正。

## 立即待办（按用户指令选一个）

A. **P1 移植**：把 `.61`~`.97` 全批（事件卡 GUI、事件选择界面、分阶图标与 1:00 计时器、发币漏斗、手册/tooltip 文案、卡牌商店方块、筹码商店整批）与 `.128` 批（立牌批 + 选卡界面 + Jade 倒计时）与 `.132` 批（AutoJS 求值闸门 + lingling 立牌与用例 + 强化拒止牌面 UV）移植到 `neoforge-26.1.2` 与 `fabric-1.20.1`（已核：两线**没有** `client/EventCardOverlay.java`、`block/CardShopBlock.java`、`shop/ChipShopCatalog.java` ⇒ 整体新建）。逐线流程：`mt_build` → `scripts/verify/**` 守门 → `code_review` → 独立功能审查子代理 → 实机（需用户下达）。移植时必须带上 `a>=4` 文字守卫、四态 `facing` + 模型 `particle`、筹码屏「条带内相对坐标」口径。
B. **forge 线实机欠账**：筹码商店在 1.20.1 只过了静态判据；补齐要 ① 把服务端探针 `c*` 命令族与客户端 770/771/772 场景移植到 `scripts/test/resources/kubejs/1.20.1/**`（注意 1.20.1 的 `sp_closeMenu`/`SP_ItemStack`/六参 `use(...)` 形态）② 复制 5 条用例为 `CHIP-*-1.20.1.json` ③ 用户下达后跑 env/launch/case/stop --purge-saves。
C. **待用户裁决的历史欠账**（详见第 37 条 ⑤）：两线 dev 客户端**运行期共存**仍互撞 `glfw.dll`（是否升级为会话级互斥或分机）；Shift+点击现在也一次只搬 1 张是否符合预期；键盘改绑的拾取键未拦截（代码推论）；`mt_loadergate` 既有 FAIL（`mixinbooster` 三段 `mandatory` 口径与设计冲突，改动前就存在）；`--phase report` 历史红标（`SHOP-M-1.21.1` / `DIAG-1.21.1`）；fabric 线产物停在 `.49`。
D. 用户指定的其它任务（`sound/` 21 个 wav 转 ogg、设计待裁决项等，见常态交接文档 §5）。
E. **`.132` 收尾欠账**（明细见常态交接 §5-D）：① lingling 冷却**实测 180 s（`cd=3600`）vs 规格 120 s** ⇒ REFUTED 规格漂移，**待用户裁决改规格还是改代码**；② **`code_review` 覆盖：2026-10-18 补跑已闭环** —— 两个 `client/RepulsionModel.java` + `scripts/test/cases/LINGLING-1.21.1.json` 均 `completed` / 0 findings（首跑 job `b766f2a0-2273-4fbd-9bc4-e58e6deacb8e` 用例 `MODEL_INVALID_JSON`，重试 job `24e1c33b-4441-43e7-89b7-a0c4b3198e15` 完成、其余 4 文件走缓存 `excluded`）；**唯一缺口 = 两张二进制 `textures/entity/repulsion.png`**（`blocked`：`The encoded data was not valid for encoding utf-8`）⇒ 已用「双线同 sha256 + `rot90(newNorth,3)==south` 逐像素断言 + 实机 `FRONT vs BACK` IoU 1.000」三重核对替代，无需再补通道；③ forge 线 lingling **无用例**（`LINGLING-1.20.1.json` 未建、实机零证据）；④ `scripts/test/cases/` 229 条 legacy 用例未迁 `eval_js`。

## 必须遵守的几条铁律

0. **实施分工（2026-10-12 用户裁决，同日二次裁决修订：写代码一律派 Subagent）**：**P0 基准线 `neoforge-1.21.1` 派子代理编写**（主 Agent 只负责规划 / 派活 / 验收 / 提交）→ `code_review` 复核 → **派子代理做独立功能审查**（静态取证 / 编译 / `scripts/verify/**` 守门 / 与需求逐字对账）→ 主 Agent 逐条回应、派新子代理修完确认无误后才派子代理做 `forge-1.20.1` 移植 → 移植线再 `code_review` + **另派一个独立子代理**功能审查；派发优先 DeepSeek（功能实现用 DeepSeek V41 Flash）；一个子代理只做一个任务、用完即弃；**复核分级（2026-10-13）**：非代码改动与小规模代码修正只做 `code_review`、免子代理功能复验；新功能按改动规模自判复验范围（全量/部分/不做，记录依据）；大型变更全量复核；实机仍须用户许可，无法仅靠复核确认时须说明并请求实机；
1. **复核优先用 `code_review`**（一次性独立会话，brief 一次写全，禁止把主会话结论当事实喂它）；该工具实例报错/超时 ⇒ 改派**一次性独立子代理**，判据规格不变；
2. **实机验证必须由用户明确下达**；靶子非亡灵；收尾 `--purge-saves`（判据 `MT_CLEANUP_SAVES: PURGED n` + `MT_CLEANUP_RESULT: OK`）+ 删临时探针 + 贴 `git status`；
3. **每次提交升四线版本 + 两份 CHANGELOG 段头**（只改 `<N>`；bump 在 build 之前；一次提交 = 一个版本号）；**默认禁止 `git push`**；
4. **不改动另一工作树**；写文件用绝对路径；提交不要用 `git add -A`（会带上未跟踪的用户资产 `models/*.bbmodel`）；
5. **改动后旧实机证据作废**：先停旧实例再测新构建；
6. **payload 类不得直接引用 `net/minecraft/client/**`**（专服会崩），客户端动作下沉 helper + dist 守卫；
7. **工具链纪律（Windows 原生）**：只在本机 Windows 跑 pwsh 7 工具链；**禁止**把它搬到 WSL/Linux 转译或模拟后当判据，也禁止用 Linux 侧 `pwsh` 跑本仓脚本（依赖 `Add-Type` / `user32` / `taskkill` 与 Windows 路径）。确需 Linux 侧只读取证时，仅用 `git` / `grep` / `python3 tools/*.py`（跨平台闸门），并如实标明读数来源（谁跑的、哪台机器、什么命令）；**不得**用实测口吻描述未在本机跑过的东西；
8. **批处理脚本空参要用 `if not "%~1"==""` 守卫**；手动启动脚本不要加前置判断（Windows 侧存档规则）。
9. **一份文档同一时刻只能有一个写者**（`.132` 实测：两队员并发改同一 handover 文档 ⇒ 重复段，已合并去重；事故与预防见第 39 条 ⑤ 与常态交接 §6-29）：派活时在共享任务 `write_scopes` 显式声明写域、写前先看 `git diff`。

## 子代理与实机的小抄（踩过的坑）

- 子代理默认 **DeepSeek**（`provider=deepseek-official` / `model=deepseek-flash`，实测可用）；mimo/kimi 在部分 tool 实例被构造期禁用。
- 一个子代理只做一个任务、brief 自包含；先验证后下结论、每条结论附证据、优先找反例、取不到证据写「无法判定」。
- KubeJS 探针：命令同 tick 不生效（tick 末落地）⇒ 清空用 API 直写，摆位与放置拆两个 tick；Rhino 没有 `setYRot`，转向用 `teleport(...,yaw,pitch)`。
- **`inject_command` 走聊天通道 ⇒ 容器屏开着会被吞**；屏内动作只能用客户端探针的 `csCmd`（`mc.player.connection.sendCommand`，不带前导斜杠）。
- 筹码屏命中坐标是「条带内相对」+ `(STRIP_X=34, STRIP_Y=6)`；探针少加偏移会「点击落空但返回 true」。
- `tools.list_agents`/`tools.job_list` 必须传空对象；`tools.edit`/`tools.write` 改已存在文件前必须先用 `read` 工具读过。
- 实机收尾判据：`MT_CLEANUP_SAVES: PURGED n` + `MT_CLEANUP_RESULT: OK`；再核 `fml.modFolders` java 进程数 = 0。

## 交接时的工作树状态

- 生成时基线：**以 `git log -1` 实测为准**（本文件随 2026-10-18 晚「`.132` 交接文档刷新」提交落地；该提交自身 = **`bcae58f9`**；其前基线 **`3c90e653`** / 四线 **`2.0.0-SNAPSHOT.132`**；上一基线 `61968fc5`）；`git status --short -uall` = **2 行** `?? models/*.bbmodel`（用户资产、非本批改动）；`%SystemDrive%` 那 4 行缓存目录已销账、不再出现在 status 输出；
- **三条线**（P0 两线 + P1 的 `neoforge-26.1.2`）产物**以 `ls <线>/build/libs` 实测为准**（现为 `.132`：P0 两线与 `neoforge-26.1.2` 均有 `…SNAPSHOT.132+<后缀>.jar`；`.132` 批未重跑产品构建时沿用既有产物）；**fabric 线产物停在 `.49`**；**P1 两线尚未移植**（筹码商店 / 事件卡 GUI / 立牌批 / 选卡界面 / Jade 倒计时）；
- 测试世界在最后一次实机收尾时已 `--purge-saves` 清理 ⇒ 下次实机先 `--phase env`；
- 未提交内容：**无**（只有 2 个用户资产未跟踪：`models/路障 - Converted.bbmodel`、`models/强化拒止 - Converted.bbmodel`；`%SystemDrive%` 缓存目录已于 2026-10-18 清除）。

> **最新交接条目（2026-10-17）**：`docs/agent/AGENTS-full.md`「## 待办交接」**第 36 条**「立牌批验证闭环 + 选卡界面 + Jade 倒计时」—— `grep -n '立牌批验证闭环' docs/agent/AGENTS-full.md`。
> **最新交接条目（2026-10-17 晚）**：**第 37 条**「选卡界面+Jade 双线实机验证闭环与 4 项修复」—— `grep -n '选卡界面+Jade 双线实机验证闭环' docs/agent/AGENTS-full.md`。
> **最新交接条目（2026-10-17 深夜）**：**第 38 条**「工具链回 Windows 原生」—— `grep -n '工具链回 Windows 原生' docs/agent/AGENTS-full.md`。
> **最新交接条目（2026-10-18，即本批正本）**：**第 40 条**「WSL 迁移 + AutoJS 测试台重建」—— `grep -n 'WSL 迁移 + AutoJS 测试台重建' docs/agent/AGENTS-full.md`；上一批 **第 39 条**「AutoJS 求值通道语义 + 怪力乱神用例 + 强化拒止牌面收正」—— `grep -n 'AutoJS 求值通道语义' docs/agent/AGENTS-full.md`。交接文档用法见 `AGENTS.md`〈代理协作契约 · C〉。
