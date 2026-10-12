# 新会话交接上下文（复制以下全文给新会话）

你是 astral_dice_multiloader-next 开发线的执行代理。先读这三份再动手：
1. `docs/handover-2.0.0-dev-next.md`（交接文档：规范/进度/测试结果/遗留项/坑）
2. `AGENTS.md` 的四节：工作树与路径纪律、二次验证规范、子代理与测试环境硬规则、版本优先级与跨线移植纪律
3. 涉及平台差异时：`docs/compat-1.20.1-forge.md`（forge）或 `docs/compat-26.1.2-neoforge.md`（26.1.2）

## 基本盘
- 工作树：`F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`）；**HEAD 与四线版本一律以实测为准**（`git log --oneline -1`、四条 `gradle.properties` 的 `mod_version`）—— 本文档上一版曾写 `d2299def` / `2.0.0-SNAPSHOT.85`，其后已有 `.86` 筹码商店界面骨架与 `.87` 实施分工规范两次提交，现号 `2.0.0-SNAPSHOT.92`；工作树**仅一项未跟踪**（`?? docs/chip-shop-logic-plan.md`）、未 push
- 另一常驻工作树 `F:\MCProject\astral_dice_multiloader`（multi-main，封包态）：**禁止触碰**；收尾核对它必须为空且 HEAD 不变
- 优先级：**P0 = neoforge-1.21.1 + forge-1.20.1（同批）**；**P1 = neoforge-26.1.2 + fabric-1.20.1（待移植）**

## 已完成（P0 两线对等）
13 个事件体系 + 事件效果卡 GUI（0.2s 进 / 1.0s 停 / 0.2s 缩小淡出，弹卡后 38t 结算，触发音效）+ 大侦探立牌主动技能「事件选择界面」（2 选 1、15s 超时/死亡/登出强触发第一项、确认即触发无等待、音效仅按键时播）+ 调查阶段 4 阶分阶图标与 1:00 计时器 + 全模组发币优先入星币钱包 + 手册/tooltip 文案同步 + **卡牌商店方块（四态朝向 + 自有碎裂粒子）**（.61 → .83，提交清单见交接文档 §3）

最近一批 P0 两线代码（.83, `d2299def`；其后 `.86` 只加筹码商店界面骨架、`.87` 只改规范）修了两处缺陷并已双线实机 PASS：
- 事件卡淡出后**文字残留**（根因：原版 `Font#adjustColor` 把 alpha<4 强制弹回不透明）⇒ `EventCardOverlay` 画文字前 `if (a >= 4)`；
- 卡牌商店方块**放置恒朝北 + 敲碎粒子紫黑** ⇒ 改 `HorizontalDirectionalBlock` 四态 `facing` + 模型补 `particle` 键（另含 1.21.1 必需的 `codec()` 补全）。

## 立即待办（按用户指令选一个）
A. **P1 移植**：把 .61~.83 全批移植到 `neoforge-26.1.2` 与 `fabric-1.20.1`（已核：两线**没有** `client/EventCardOverlay.java` 与 `block/CardShopBlock.java` ⇒ 整体新建）。每条线按流程走：`mt_build` → `scripts/verify/**` 守门 → `code_review` → 实机（需用户下达）。移植时必须带上 `a>=4` 文字守卫与四态 `facing`/`particle` 修复；两线 lang 与 ModTooltipHandler 仍是旧「麻烦制造者」文案，一并改。
B. **筹码商店接线**：`models/block/chip_shop.json` + `textures/block/chip_shop.png` 已就位（仅 P0 两线），方块/实体/物品/配方/语言键尚未加。
C. 用户指定的其它任务（sound/ 21 个 wav 转 ogg、设计待裁决项等，见交接文档 §5）。

## 常用命令（务必 pwsh，禁止 5.1）
```powershell
cd F:\MCProject\astral_dice_multiloader-next
git status --short -uall ; git log --oneline -3
pwsh -NoProfile -File scripts/test/mt_build.ps1 --version 1.21.1 --timeout 300
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase env
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase launch
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase stop --force --purge-saves
pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir neoforge-1.21.1/src/main/resources/assets/astral_dice/lang
```
手动启动游戏（人工自测）：双击 `run/Start-1.21.1.bat`（或 1.20.1 / 26.1.2）——脚本只做启动，无任何前置判断。

## 必须遵守的几条铁律
0. **实施分工（2026-10-12 用户裁决）**：**P0 基准线 `neoforge-1.21.1` 由主 Agent 亲自编写**（不派实现子代理）→ `code_review` 复核 → **派子代理做独立功能审查**（静态取证 / `:<线>:classes` 编译 / `scripts/verify/**` 守门 / 与需求逐字对账）→ 主 Agent 修完并确认无误后，才派子代理做 `forge-1.20.1` 移植 → 移植线再 `code_review` + **另派一个独立子代理**功能审查；派发优先 DeepSeek（功能实现用 DeepSeek V41 Flash）；
1. **复核优先用 `code_review`**（一次性独立会话，brief 一次写全，禁止把主会话结论当事实喂它）；该工具实例报错/超时 ⇒ 改派**一次性独立子代理**承担，判据规格不变（本仓 .83 批已按此路径完成 R1..R8 复核）；
2. **实机验证必须由用户明确下达**；靶子非亡灵；收尾 `--purge-saves` + 删探针 + 贴 `git status`；
3. **每次提交升四线版本 + 两份 CHANGELOG**；默认 `git push` 禁止；
4. **不改动另一工作树**；写文件用绝对路径；
5. **改动后旧实机证据作废**：先停旧实例再测新构建；
6. **payload 类不得直接引用 net/minecraft/client/**（专服会崩），客户端动作下沉 helper + dist 守卫**；
7. **批处理脚本空参要用 `if not "%~1"==""` 守卫**；手动启动脚本不要加前置判断。

## 子代理与实机的小抄（踩过的坑）
- 子代理默认 **DeepSeek**（`provider=deepseek-official` / `model=deepseek-flash`，实测可用）；mimo/kimi 在本 tool 实例被构造期禁用。
- 一个子代理只做一个任务、用完即弃、brief 自包含；**先验证后下结论**、每条结论附证据、优先找反例、取不到证据写「无法判定」。
- KubeJS 探针：命令同 tick 不生效（tick 末落地）⇒ 清空用 API 直写，摆位与放置拆两个 tick；Rhino 没有 `setYRot`，转向用 `teleport(...,yaw,pitch)`。
- 实机收尾判据：`MT_CLEANUP_SAVES: PURGED n` + `MT_CLEANUP_RESULT: OK`；再核 `fml.modFolders` java 进程数 = 0。

## 交接时的工作树状态
- 生成时基线 HEAD 以 `git log -1` 实测为准（本行随 `.92` 提交落地）；`git status --short -uall` 仅余未跟踪 `docs/chip-shop-logic-plan.md`（筹码商店逻辑设计稿，待用户点「按计划开工」后随实现同批提交）；**P0 两线** jar 已构建到 `.85`（`run/1.21.1/mods`、`run/1.20.1/mods` 实测），**P1 两线尚未移植**（`run/26.1.2/mods` = `.58`、`run/fabric-1.20.1/mods` = `.49`）；`.84`/`.85`/`.87` 均为文档提交、不改代码，`.86` 只新增尚无开屏入口的筹码商店界面骨架 ⇒ 四线都不需要为此重建；
- 测试世界在最后一次实机收尾时已 `--purge-saves` 清理 ⇒ 下次实机先 `--phase env`；
- 未提交内容：仅未跟踪 `docs/chip-shop-logic-plan.md`（筹码商店逻辑设计稿，待用户点「按计划开工」后随实现同批提交）。