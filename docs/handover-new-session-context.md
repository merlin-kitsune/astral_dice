# 新会话交接上下文（复制以下全文给新会话）

你是 astral_dice_multiloader-next 开发线的执行代理。先读这三份再动手：
1. `docs/handover-2.0.0-dev-next.md`（交接文档：规范/进度/测试结果/遗留项/坑）
2. `AGENTS.md` 的四节：工作树与路径纪律、二次验证规范、子代理与测试环境硬规则、版本优先级与跨线移植纪律
3. 涉及平台差异时：`docs/compat-1.20.1-forge.md`（forge）或 `docs/compat-26.1.2-neoforge.md`（26.1.2）

## 基本盘
- 工作树：`F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`，HEAD 交接时 `54a6e576`，四线版本 `2.0.0-SNAPSHOT.79`，工作树干净、未 push）
- 另一常驻工作树 `F:\MCProject\astral_dice_multiloader`（multi-main，封包态）：**禁止触碰**；收尾核对它必须为空且 HEAD 不变
- 优先级：**P0 = neoforge-1.21.1 + forge-1.20.1（同批）**；**P1 = neoforge-26.1.2 + fabric-1.20.1（待移植）**

## 已完成（P0 两线对等）
13 个事件体系 + 事件效果卡 GUI（0.2s 进 / 1.0s 停 / 0.2s 缩小淡出，弹卡后 38t 结算，触发音效）+ 大侦探立牌主动技能「事件选择界面」（2 选 1、15s 超时/死亡/登出强触发第一项、确认即触发无等待、音效仅按键时播）+ 调查阶段 4 阶分阶图标与 1:00 计时器 + 全模组发币优先入星币钱包 + 手册/tooltip 文案同步（.61 → .79，提交清单见交接文档 §3）

## 立即待办（按用户指令选一个）
A. **P1 移植**：把上述功能移植到 `neoforge-26.1.2` 与 `fabric-1.20.1`，每条线按流程走：`mt_build` → `scripts/verify/**` 守门 → `code_review` → 实机（需用户下达）。注意：这两线 lang 与 ModTooltipHandler 里仍是旧「麻烦制造者」文案，移植时一并改。
B. **子代理模型 mimo/kimi 复验**：在**全新会话**里试 `subagent` 选 `mimo-v2.6-pro` / `k3`（旧会话因构造期策略禁用，重启无效）。
C. 用户指定的其它任务。

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
1. **复核一律用 `code_review`**（一次性独立会话，brief 一次写全，禁止把主会话结论当事实喂它）；
2. **实机验证必须由用户明确下达**；靶子非亡灵；收尾 `--purge-saves` + 删探针 + 贴 `git status`；
3. **每次提交升四线版本 + 两份 CHANGELOG**；默认 `git push` 禁止；
4. **不改动另一工作树**；写文件用绝对路径；
5. **改动后旧实机证据作废**：先停旧实例再测新构建；
6. **payload 类不得直接引用 net/minecraft/client/**（专服会崩），客户端动作下沉 helper + dist 守卫**；
7. **批处理脚本空参要用 `if not "%~1"==""` 守卫**；手动启动脚本不要加前置判断。

## 交接时的工作树状态
- HEAD `54a6e576`；`git status --short -uall` **空**；四线 jar 均已构建到 `.79` 并推入各自 `run/<线>/mods`；
- 测试世界在最后一次实机收尾时已 `--purge-saves` 清理 ⇒ 下次实机先 `--phase env`；
- 未提交内容：无。
