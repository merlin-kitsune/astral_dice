# 新会话交接上下文（复制以下全文给新会话）

你是 astral_dice_multiloader-next 开发线的执行代理。先读这四份再动手：
1. `docs/handover-2.0.0-dev-next.md`（常态交接：规范/进度/测试结果/遗留项/坑）
2. `docs/handover-2.0.0-SNAPSHOT.97-chip-shop.md`（**最近一批**：筹码商店批的流程/语义/判据/未完成/新坑，逐条可复算）
3. `AGENTS.md` 的四节：工作树与路径纪律、二次验证规范、子代理与测试环境硬规则、版本优先级与跨线移植纪律
4. 涉及平台差异时：`docs/compat-1.20.1-forge.md`（forge）或 `docs/compat-26.1.2-neoforge.md`（26.1.2）

## 基本盘
- 工作树：`F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`）；**HEAD 与四线版本一律以实测为准**（`git log --oneline -1`、四条 `gradle.properties` 的 `mod_version`）—— 上一版本文档写的是 `2.0.0-SNAPSHOT.96`；截至生成时，筹码商店批已作为 **`.97`（`6c81b7fe`，192 文件）** 落地，本文档随 **`.98`** 提交落地；
- 工作树状态：除 **4 行** `?? %SystemDrive%/ProgramData/Microsoft/Windows/Caches/*.db`（与本项目无关的垃圾，勿提交、勿用 `git add -A`）外**干净**；**未 push**；
- 另一常驻工作树 `F:\MCProject\astral_dice_multiloader`（multi-main，封包态）：**禁止触碰**；收尾核对它必须为空且 HEAD 不变（`git -C <主线> status --short --untracked-files=all` 输出 0 行）；
- 优先级：**P0 = neoforge-1.21.1 + forge-1.20.1（同批）**；**P1 = neoforge-26.1.2 + fabric-1.20.1（待移植）**。

## 已完成（P0 两线对等）
事件体系 13 个 + 事件卡 GUI（0.2s 进 / 1.0s 停 / 0.2s 缩小淡出，弹卡后 38t 结算）+ 大侦探立牌主动技能「事件选择界面」（2 选 1、15s 超时/死亡/登出强触发第一项）+ 调查阶段 4 阶分阶图标与 1:00 计时器 + 全模组发币优先入星币钱包 + 手册/tooltip 文案同步 + 卡牌商店方块（四态朝向 + 自有碎裂粒子）+ **筹码商店（`.97`）**：61 枚筹码的完整抽取与售卖（3 候选加权抽、排除已拥有、三格同价 10 起每买 +5 上限 100、刷新 3~5 次且 30 分钟恢复 1 次、余额不足/次数为 0/物品栏满三种拒绝、方块四向朝向、界面含品质外框与红闪）。

## 立即待办（按用户指令选一个）
A. **P1 移植**：把 `.61`~`.97` 全批移植到 `neoforge-26.1.2` 与 `fabric-1.20.1`（已核：两线**没有** `client/EventCardOverlay.java`、`block/CardShopBlock.java`、`shop/ChipShopCatalog.java` ⇒ 整体新建）。逐线流程：`mt_build` → `scripts/verify/**` 守门 → `code_review` → 实机（需用户下达）。移植时必须带上 `a>=4` 文字守卫、四态 `facing` + 模型 `particle`、筹码屏「条带内相对坐标」口径。
B. **forge 线实机欠账**：筹码商店在 1.20.1 只过了静态判据；补齐要 ① 把服务端探针 `c*` 命令族与客户端 770/771/772 场景移植到 `scripts/test/resources/kubejs/1.20.1/**`（注意 1.20.1 的 `sp_closeMenu`/`SP_ItemStack`/六参 `use(...)` 形态）② 复制 5 条用例为 `CHIP-*-1.20.1.json` ③ 用户下达后跑 env/launch/case/stop --purge-saves。
C. **待用户裁决的历史欠账**（详见批次文档 §4）：fabric 线产物停在 `.49`（`.50`..`.98` 未构建，`mt_build` 不认 fabric）；守门 6 条盲区 + F1/F4/F6/F8/F13/F14 补变异；筹码刷新锚点的 P2 语义（到上限后花 1 次被下一 tick 立即补回）；`--phase report` 总判 FAIL 只因同世代两条历史红标（`SHOP-M-1.21.1`/`DIAG-1.21.1`）；`.97`/`.98` 产物自身的实机证据要不要重取。
D. 用户指定的其它任务（sound/ 21 个 wav 转 ogg、设计待裁决项等，见常态交接文档 §5）。

## 常用命令（务必 pwsh，禁止 5.1）
```powershell
cd F:\MCProject\astral_dice_multiloader-next
git status --short -uall ; git log --oneline -3
pwsh -NoProfile -File scripts/test/mt_build.ps1 --version 1.21.1 --timeout 300
pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1          # 两线守门，判据 CHIPUI_TOTAL_FAIL: 0 / CHIPUI_RESULT: OK
pwsh -NoProfile -File scripts/verify/mutate_chip_shop_ui.ps1          # 变异 37 条，判据 MUT_TOTAL: 37，判红 37 / MUT_RESULT: OK
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase env
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase launch
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase cases --case cases/CHIP-LOGIC-1.21.1.json
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase report --allow-auto
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase stop --force --purge-saves
pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir neoforge-1.21.1/src/main/resources/assets/astral_dice/lang
```
手动启动游戏（人工自测）：双击 `run/Start-1.21.1.bat`（或 1.20.1 / 26.1.2）——脚本只做启动，无任何前置判断。

## 必须遵守的几条铁律
0. **实施分工（2026-10-12 用户裁决）**：**P0 基准线 `neoforge-1.21.1` 由主 Agent 亲自编写** → `code_review` 复核 → **派子代理做独立功能审查**（静态取证 / 编译 / `scripts/verify/**` 守门 / 与需求逐字对账）→ 主 Agent 修完确认无误后才派子代理做 `forge-1.20.1` 移植 → 移植线再 `code_review` + **另派一个独立子代理**功能审查；派发优先 DeepSeek（功能实现用 DeepSeek V41 Flash）；一个子代理只做一个任务、用完即弃；
1. **复核优先用 `code_review`**（一次性独立会话，brief 一次写全，禁止把主会话结论当事实喂它）；该工具实例报错/超时 ⇒ 改派**一次性独立子代理**，判据规格不变；
2. **实机验证必须由用户明确下达**；靶子非亡灵；收尾 `--purge-saves`（判据 `MT_CLEANUP_SAVES: PURGED n` + `MT_CLEANUP_RESULT: OK`）+ 删临时探针 + 贴 `git status`；
3. **每次提交升四线版本 + 两份 CHANGELOG 段头**（只改 `<N>`；bump 在 build 之前；一次提交 = 一个版本号）；**默认禁止 `git push`**；
4. **不改动另一工作树**；写文件用绝对路径；提交不要用 `git add -A`（会带上那 4 个垃圾目录）；
5. **改动后旧实机证据作废**：先停旧实例再测新构建；
6. **payload 类不得直接引用 `net/minecraft/client/**`**（专服会崩），客户端动作下沉 helper + dist 守卫；
7. **批处理脚本空参要用 `if not "%~1"==""` 守卫**；手动启动脚本不要加前置判断。

## 子代理与实机的小抄（踩过的坑）
- 子代理默认 **DeepSeek**（`provider=deepseek-official` / `model=deepseek-flash`，实测可用）；mimo/kimi 在本 tool 实例被构造期禁用。
- 一个子代理只做一个任务、brief 自包含；先验证后下结论、每条结论附证据、优先找反例、取不到证据写「无法判定」。
- KubeJS 探针：命令同 tick 不生效（tick 末落地）⇒ 清空用 API 直写，摆位与放置拆两个 tick；Rhino 没有 `setYRot`，转向用 `teleport(...,yaw,pitch)`。
- **`inject_command` 走聊天通道 ⇒ 容器屏开着会被吞**；屏内动作只能用客户端探针的 `csCmd`（`mc.player.connection.sendCommand`，不带前导斜杠）。
- 筹码屏命中坐标是「条带内相对」+ `(STRIP_X=34, STRIP_Y=6)`；探针少加偏移会「点击落空但返回 true」。
- `tools.list_agents`/`tools.job_list` 必须传空对象；`tools.edit`/`tools.write` 改已存在文件前必须先用 `read` 工具读过。
- 实机收尾判据：`MT_CLEANUP_SAVES: PURGED n` + `MT_CLEANUP_RESULT: OK`；再核 `fml.modFolders` java 进程数 = 0。

## 交接时的工作树状态
- 生成时基线：`git log -1` 实测为准（**本文件随 `.98` 提交落地**，其前一批为筹码商店 `.97` = `6c81b7fe`，192 文件）；`git status --short -uall` = **仅 4 行** `?? %SystemDrive%/…db`；
- **三条线**（P0 两线 + P1 的 `neoforge-26.1.2`）产物已构建到 `.97`（`neoforge-1.21.1/build/libs`、`forge-1.20.1/build/libs`、`neoforge-26.1.2/build/libs`）；**fabric 线产物停在 `.49`**；**P1 两线尚未移植筹码商店与事件卡 GUI 等**；
- 测试世界在最后一次实机收尾时已 `--purge-saves` 清理 ⇒ 下次实机先 `--phase env`；
- 未提交内容：**无**（除那 4 行垃圾）。
