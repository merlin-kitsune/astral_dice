# 交接文档 — astral_dice_multiloader-next（开发线 multi-dev-next）

> 生成：2026-10-11 20:20；**本次刷新：2026-10-18 17:30（Asia/Hong_Kong）**｜工作树 `F:\MCProject\astral_dice_multiloader-next`
> 分支 `multi-dev-next`｜刷新前基线 HEAD **`3c90e653`**（本文件上一版基线 `e06451c5` = 交接文档批 `.98`；`.99` 起依次有 `.100` 实施分工、`.102` 复核分级⑨、`.103`–`.131` 各批（明细见 `docs/agent/AGENTS-full.md`「待办交接」第 20–39 条）、`.132` = **AutoJS 求值通道 / lingling 用例 / 强化拒止牌面** 三批；最新值一律以 `git log -1` 为准）｜四线版本 **`2.0.0-SNAPSHOT.132`**（四条线同号）
> 工作树状态：**干净**（`git status --short -uall` 只剩两行用户资产 `?? models/路障 - Converted.bbmodel`、`?? models/强化拒止 - Converted.bbmodel`；§5-C-11 的 `%SystemDrive%` 缓存目录已于 2026-10-18 清除）；**未 push**。
> **本批交接（.132 三批）**：正本是 `docs/agent/AGENTS-full.md` 末尾「待办交接」**第 39 条**（AutoJS 求值语义 + 测试台闸门 + LINGLING 用例 + 强化拒止牌面 + 本批坑 + 遗留），§3/§4/§6 有对应展开。
> 历史详案：`docs/handover-2.0.0-SNAPSHOT.97-chip-shop.md`（筹码商店，流程 / 语义 / 判据 / 未完成 / 新坑，逐条可复算）。
> **交接文档用法（接手顺序 / 交班义务）见 `AGENTS.md`〈代理协作契约 · C. 交接文档用法〉**，接手时先按那里的顺序实读一遍，别信本文里的数字。
> 新会话提示词：`docs/handover-new-session-context.md`（可整份复制给新会话；**Windows 侧口径**，2026-10-17 晚随「工具链回 Windows 原生」重写）。
> ⚠️ **环境口径（2026-10-17 晚收敛）**：工具链 = 纯 pwsh 7、**Windows 原生**（`scripts/test/mt_build.ps1` 是唯一构建入口，`scripts/test/mt_build.sh` 已删除）；**不再支持从 WSL/Linux 驱动**（根因与数据见 `docs/handover-new-session-context.md` §0.2，仅作知识）。

---

## 0. 一页速览

| 项 | 值 |
|---|---|
| 优先级模型 | **P0 = neoforge-1.21.1 + forge-1.20.1（同批、优先）**；**P1 = neoforge-26.1.2 + fabric-1.20.1（待移植）** |
| 功能对等现状 | **P0 两线已对等**（事件体系 + 事件卡 GUI + 事件选择界面 + 发币漏斗 + 手册/tooltip 文案 + 卡牌商店方块 + **筹码商店（`.97`）**）；**P1 两线未移植** —— 已核：`neoforge-26.1.2` 与 `fabric-1.20.1` 目录下**不存在** `client/EventCardOverlay.java`、`block/CardShopBlock.java`、`shop/ChipShopCatalog.java` |
| 本批交接（`.132` 三批） | `docs/agent/AGENTS-full.md` 末尾「待办交接」**第 39 条**（AutoJS 求值语义 / 测试台闸门 / LINGLING 用例 / 强化拒止牌面 / 本批坑 / 遗留） |
| 最近实机结果（`.132`） | 1.21.1：**`MT_CASE_RESULT: LINGLING-1.21.1 = PASS`**（42 step / 22 assert）；强化拒止牌面 **FRONT vs BACK best=id IoU 1.000 / OVERALL: PASS**；`AUTOJS-SMOKE-1.21.1 = PASS`；**forge 线无用例、未实机** |
| 更早实机结果（`.97`） | 1.21.1 筹码五条用例 **全 PASS**（`CHIP-LOGIC` / `CHIP-REGEN` / `CHIP-CLIENT-BUY` / `CHIP-CLIENT-REFRESH` / `CHIP-CLIENT-DENY`） |
| AutoJS 求值通道 | eval_js **只求最后一个表达式**（顶层 `return` ⇒ `EvaluatorException: invalid return`）；非标量必须 `JSON.stringify(...)` 包裹（否则 `"[object Object]"`）；布尔用 `!!(...)`，`-Mode Bool` 回来是**字符串** `"true"`/`"false"` ⇒ 只能比字面量 |
| 构建 | `pwsh -NoProfile -File scripts/test/mt_build.ps1 --version <线> --timeout 300` ⇒ `MT_BUILD: OK` |
| 实机 | `mt.ps1 --version <线> --phase env` → `--phase launch`；**收尾 `--phase stop --force --purge-saves`** |
| 手动启动（人工自测） | 双击 `run/Start-<线>.bat`（只启动，**无任何前置判断**） |
| 复核 | `code_review` **优先**（一次性独立会话）；若该工具实例报错/超时 ⇒ 改派**一次性独立子代理**承担，brief 规格不变（见 §1） |
| 实施分工 | **P0 基准线 `neoforge-1.21.1` = 派子代理编写**（2026-10-12 二次裁决：主 Agent 不亲自写代码）→ `code_review` 复核 → **独立子代理功能审查** → 确认无误后才派子代理移植 `forge-1.20.1` → 该线再 `code_review` + **另一个独立子代理**功能审查（AGENTS.md 硬规则第 7 条，2026-10-12；2026-10-13 三次裁决增设复核分级⑨：非代码改动与小规模代码修正只做 code_review、新功能按规模自判复验范围、大型变更全量复核、实机仍须许可） |
| 子代理模型 | 默认 **DeepSeek**（`provider=deepseek-official` / `model=deepseek-flash`，本会话 4 个子代理实测可用）；mimo/kimi 在本 tool 实例被构造期禁用 |
| 实机验证 | **必须由用户明确下达**；未实机的欠账如实登记 |

---

## 1. 开发规范（硬约束）

**工作树与路径**
- 开发线任务只在本工作树落地；**禁止**触碰 `F:\MCProject\astral_dice_multiloader`（multi-main，封包态，收尾须仍为空且 HEAD 不变）。
- 跨工作树写文件**一律绝对路径**；git 命令一律 `git -C <工作树绝对路径>`。

**Shell**
- 只用 **pwsh 7**；多行/含 if 表达式 ⇒ 落盘 `temp/tNN/*.ps1` 再 `pwsh -NoProfile -File`；调用本仓脚本必须写全 `pwsh -NoProfile -File …`。

**版本与提交**
- **每次提交升一版**（四线同号）+ 两份 CHANGELOG 段头（`## 未发布（<版本>）` / `## Unreleased (<版本>)`）同步；产物名 `astral_dice-<版本>+<加载器>.jar`。
- **默认不 git push**；远端操作走代理 `http://127.0.0.1:7897`。文件按 CRLF 维护。

**依赖与联动**
- 新增第三方**模组**依赖只能经 Curse Maven / Modrinth Maven；禁止 fileTree/files。
- 第三方联动**禁止猜 API**：先向用户索取 jar/源码；反射签名必须来自实物（javap -p）；失败即永久关闭该后端。
- 平台差异逐条登记：`docs/compat-1.20.1-forge.md`、`docs/compat-26.1.2-neoforge.md`。

**测试与验收**
- 机器守门：`verify_event_registry.ps1` / `verify_resource_integrity.ps1` / `verify_forge_loader_gate.ps1` / `verify_chip_recipes.ps1` / `tools/check_lang_sync.ps1` / `tools/verify_effect_icons.py` / `tools/audit_patchouli_keys.py`。
- 二次验证 = `code_review`：把「复核对象 + 期望不变量 R1..Rn + 逐字证据要求」一次写全；**禁止**把主会话结论当事实喂给它。**备用**：工具实例失败时改派一次性子代理，规格同上（本仓 .83 批已按此路径完成 R1..R8 复核）。
- 实机：用户下达才做；靶子**非亡灵**；收尾带 `--purge-saves`（判据 `MT_CLEANUP_SAVES: PURGED n` + `MT_CLEANUP_RESULT: OK`）+ 删临时探针 + 贴 `git status` 原文。
- 子代理：**一个子代理只做一个任务**，用完即弃，brief 自包含。

---

## 2. 工具链速查

```powershell
# 构建（长任务用 *> 文件，避免管道阻塞）
pwsh -NoProfile -File scripts/test/mt_build.ps1 --version 1.21.1 --timeout 300

# 实机三阶段
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase env     # 重建/校验世界 + 同步模组与 KubeJS
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase launch  # 脱离式启动并等进世界
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase stop --force --purge-saves

# 手动启动（用户自测）：双击 run\Start-1.21.1.bat（或 1.20.1 / 26.1.2）

# 守门
pwsh -NoProfile -File scripts/verify/verify_event_registry.ps1 -Root <绝对路径> -Line neoforge-1.21.1
pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir neoforge-1.21.1/src/main/resources/assets/astral_dice/lang
python tools/verify_effect_icons.py ; python tools/audit_patchouli_keys.py

# 注入（真按键 / 命令）
pwsh -NoProfile -File scripts/test/mt_inject.ps1 key --key skill
pwsh -NoProfile -File scripts/test/mt_inject.ps1 cmd -Command "/astralprobecard red" --version 1.21.1
```

> 写探针前必读：KubeJS 命令在**本 handler 同 tick 不生效**（tick 末才落地），详见 §6-11。

---

## 3. 已完成进度（按批次，含提交）

| 批次 | 版本 | 提交 | 内容 |
|---|---|---|---|
| 事件体系 | .61 | d719fb64 | 框架 + 13 个注册事件 + 调查阶段（4 阶级）+ 事件票据/抽取器 + forge 镜像 + 文档 |
| 事件补丁 | .62 | 8c662b0c | 12 个事件效果（fire-then-advance 阶级 / 多侦探仲裁 / apply gate / pool gate） |
| 指示器 | .63 | 6d52df32 | 3 个事件状态指示器（图标取 images/<中文名>.png 逐字节副本）+ 夜间刷怪钩子 + 攻防走模组战斗模型 |
| 事件卡 GUI | .64 | 34e04c11 | 弹卡（0.2s 进 / 1.0s 停 / 0.2s 缩小淡出）+ 38t 延迟结算（⚠️ 该提交主题写作「39t」，实测为 38t，见 §4 结算时机行）+ 触发音效 + 卡面按内容居中 + 极性订正 + 4 项缺陷修复 |
| 分阶图标 | .65 | 38a36cd6 | 调查阶段 4 张分阶图标按阶级上屏（tier>0 ⇒ <id>_<suffix>） |
| 1:00 计时器 | .66 | f93d0af1 | 调查阶段 defaultDurationTicks 0→1200 |
| 计时器登记时机 | .67 | c9c9946b | EventTimerStore.start 移入延迟块（与效果同刻） |
| 发币统一 | .71 | e8447d4c | 全模组发币优先入钱包（ResourceConversion.giveItem 漏斗） |
| 居中微调 | .72 | ef6b21df | 事件选择界面倒计时像素级居中（DX/DY=0.000） |
| forge 移植 | .73 | b79bd346 | 事件选择界面整体移植 forge + 会话闸门 + DiceCombatEvents 发币漏斗 |
| 空放修复 | .74 | 2182225c | 立牌空放改 fail（与其它立牌统一，零副作用） |
| tooltip 恢复 | .75 | 7ff857f3 | 补 tooltip.astral_dice.sign.fanny_active（三语）+ 接线 |
| 确认无等待 | .76 | 65d5ff78 | 移除确认后 1 秒等待；确认不播音效 |
| 手册修订 | .77 | 087fc1f3 | 作废旧「麻烦制造者」11 效果清单 → 新「事件选择」 |
| CHANGELOG 一致性 | .78 | 35958f5f | 删除自相矛盾的旧口径 |
| tooltip 顺序 | .79 | 78525209 | 主动标题→主动正文→被动标题→被动正文 |
| 卡面素材更新 | .79 | 54a6e576 | fanny_skill_event / _select 换新素材（两线逐字节一致） |
| 交接文档 | .80 | b67f017f | 新建交接文档 + 新会话上下文（mimo/kimi 遗留等） |
| AGENTS 规则 | .81 | 1c937e9c | 开发流程 + 七条铁律入 AGENTS；过时「三线同批」口径归档 |
| 子代理模型 | .82 | 2b2ba338 | 派发默认 DeepSeek，其它模型仅作备份 |
| 卡文字残留 / 商店方块 | .83 | d2299def | 事件卡淡出后文字残留 + card_shop 四态朝向与碎裂粒子（含 1.21.1 `codec()` 补全、两份 CHANGELOG 断行无损修复） |
| 交接刷新 | .84 | 6a0a2efc | 交接文档刷新至 .84 + 交接提示词更新 |
| 文档复核对账 | .85 | c7cc367b | 按二次验证结论修订：P1 两线 jar 版本写实（.58/.49）、淡出判据改为可复算口径、.64 主题 39t 注记 |
| 筹码商店界面骨架 | .86 | bb48a206 | 13 张贴图入库 + ChipShopScreen/ChipShopMenu/ChipShopClientState（两线）+ chip_shop 菜单注册与客户端绑定 + 三语 4 键 + docs/chip-shop-ui-spec.md + scripts/verify/verify_chip_shop_ui.ps1（含变异测试反证）；业务逻辑待后续批次 |
| 实施分工规范 | .87 | b1294bd2 | AGENTS 入「P0 基准线由主 Agent 亲自写 → code_review → 独立子代理功能审查 → 才派 forge 移植」流程 |
| 规范清残 | .88~.89 | b1c1e2c4 / 24d568ad | 清掉「三线同批」遗留措辞、子代理并行旧口径、交接版本漂移 |
| 设计稿入库 | .90~.92 | 026ff76f / 6e2c7c81 / 7b7d86b0 | `docs/chip-shop-logic-plan.md` 入库 + Q13–Q16（无折扣 / 离线恢复 / 重抽口径 / 图标供给）与 Q17（不绘标题）裁决落定、计划冻结待开工令 |
| 复审收尾 | .93~.96 | f7edb0a4 / 583ec48f / 37137548 / 6b87640d | 功能审查与二次验证的收尾修订（悬空章节引用、三线措辞残留、handover 状态诚实化等纯文档） |
| 中间批次 | .98~.131 | — | 明细见 `docs/agent/AGENTS-full.md`「待办交接」**第 20–38 条**（本文件自 .98 起不再逐条复制批次行） |
| repulsion 吸附口径 | .132 | 3d3555ea | 吸附口径收窄（有仇恨的敌对 / 被激怒中立，平静即释放）+ 苦力怕自爆拦截（P0 两线） |
| 怪力乱神（lingling） | .132 | 6cf9f6e0 / 298a4c3a | 新增史诗立牌「怪力乱神」（拦截者被动 + 能量突击主动，neo 线 4 材料创造栏解绑）→ forge-1.20.1 全量移植 + 实机用例 + 冷却口径订正 |
| AutoJS 求值通道 | .132 | e066f7db / 02cfa6c0 | 实机测试台接入 AutoJS 运行时求值（MCP 主通道，KubeJS 降为 legacy）+ 桌面驱动改走 DSH dsh-computer-use（移除 computer-control-mcp 键鼠逻辑）；求值语义坑见 §6-25 |
| 强化拒止牌面 | .132 | f3aca047 / d8d48431 / ba05258b / 0f99ba12 / c00b13fc | 五轮收敛：贴图换 bbmodel 原图 → UV 相对 bbmodel 歪 90° → 图案翻转 180° + 立牌类不显示名条 → 北面 UV 复用反面矩形（失败）→ **北块 := 南块逆时针 90°**（两线，判据 OVERALL: PASS） |
| lingling 用例判据 | .132 | 3c90e653 | d_damage 改用 ArmorStand 承伤，规避骰战伤害覆写造成的攻击强度缩放污染（1.21.1 22 断言 PASS） |
| 商店货架 / 文案 | .132 | 0014e4e0 / f62f12c1 / 20e82a12 | 货架补「路障」+ 佩戴怪力乱神时显示「强化拒止」；路障/强化拒止/怪力乱神 tooltip 精简排版（双线三语）；规格键数订正（1001 键 / 3·5 行） |
| **筹码商店批** | **.97** | **6c81b7fe** | **两线完整实现**（61 筹码池 + 抽取/刷新/购买/拒绝 + 价格曲线 + 方块与四向朝向 + 界面与品质框 + 三包网络 + 附件落盘 + 61 张图标 + 配方/掉落表 + 三语 7 键）+ 守门 G1..G6·G9..G27 + 变异 37 条 + 5 条实机用例（1.21.1 全 PASS）+ 探针 `c*` 命令族与 770/771/772 场景；192 文件 |

**功能面现状（P0 两线已对等）**：13 个事件 + 弹卡 GUI（淡出后无文字残留）+ 事件选择界面（2 选 1、15s、确认即触发、死亡/登出强触发第一项）+ 调查阶段 4 阶图标与 1:00 计时器 + 全模组发币优先入钱包 + 手册/tooltip 文案同步 + 卡牌商店方块（四态朝向 + 自有碎裂粒子）+ **筹码商店**（61 枚筹码的抽取/售卖方块，四向朝向、21 步可见行为与拒绝码见 `docs/chip-shop-logic-plan.md` 与 `docs/handover-2.0.0-SNAPSHOT.97-chip-shop.md`）。

---

## 4. 测试结果汇总（实机实测，数值可复算）

| 判据 | 实测 | 版本 |
|---|---|---|
| 事件卡居中 | 卡内容中心 (960.0,542.0) vs 屏幕中心 (960,540)：水平 0、垂直 +2px ⇒ 加 yOff=-0.5 后 **Δ=(0,0)** | .64 ⚠ |
| 卡面淡入淡出 | α 实测 0.118→0.622→**1.000**→0.642→0.117（三路反演，噪声底 0.008） | .64 ⚠ |
| 结算时机 | exec_at = 弹卡 gt + 38（8/8），墙钟 1999–2002 ms | .64 ⚠ |
| event_fade_in | 8/8 弹卡后 1 tick 内 OpenAL 通道 0→1 | .64 ⚠ |
| 事件卡淡出无文字残留 | 以 `af3_b1_13t`（卡已移除）为基准、窗口 (760,400)–(1160,690)、逐像素任一通道 \|Δ\|>8 计数：**动画期 4t=43571 / 5t=36511 px**，**缓冲窗 6t~12t = 68/63/61/61/54/36/5 px**，第二轮 5t=40 px；换紧框 (860,440)–(1060,640) 则缓冲窗 = 1/0/0/0/0/0/0、第二轮 5t=0 ⇒ 缓冲窗与移除后几近等同（有文字时是 4 万 px 量级）。1.20.1：缓冲窗截图（卡实例仍在，比移除早 ~300ms）无任何文字，动画期卡名+计时可见（对照） | .83（数字本轮复算） |
| card_shop 四向朝向 | 真实 `item.place` / `useOn` 4/4：facing = 放置者反向（south→north / west→east / north→south / east→west，yaw 0/90/180/270 全中） | .83 |
| card_shop 碎裂粒子 | sprite = `astral_dice:block/card_shop`（非 `missingno`），13 帧紫黑判据像素 = 0；模型六面同贴图 + 6 elements 双证 ⇒ 无正背面，无需 +180 | .83 |
| 分阶图标 | tier1/2/3/4 → _1/_2/_3/_revelation **各 100.00%（0 不一致像素）**，次优差 ≥673 px | .65 |
| 1:00 计时器 | 字模合成 "1:00" 与实测**逐像素 EXACT MATCH**；600 候选唯一命中 | .66 |
| 事件选择界面 12+4 项 | 四锚点 Δ=0；倒计时 15→1；三态逐帧 diff；侦探门禁 n=13 inv=1 命中 11/60 vs n=12 inv=0 命中 0/100；弹卡 38t 内零副作用；超时/死亡/登出三路 force pick=0；已确认不被覆盖 | .70/.73 |
| 专服不崩 | Done (0.436s) / Done (0.657s)，RuntimeDistCleaner 命中 0，javap 反证 payload 无客户端类型引用 | .70/.73 |
| tooltip 顺序 | 截图逐行 + getTooltipLines 索引序 5<6<7<8 双证据 | .79 |
| 确认无 1 秒等待 | confirm → AP_EVENT_CARD: shown = **0.001–0.002 s（同 tick）**；shown→FIRE = 38t | .76 |
| 空放零副作用 | AP_SIGN_SOUND 2→2；卡牌/筹码计数 +0；冷却 cd 0→0；无新 open（仅 already_open 告警） | .74 |
| 新卡面素材 | 默认态环带 4886/4886 = **100.00%** 匹配 fanny_skill_event；指向/选中 2824/2824 = **100.00%** 匹配 _select | .79 |

> ⚠ = 该行来自 .64，而 .83 改动了同一文件 `client/EventCardOverlay.java`（仅新增 a>=4 文字守卫）⇒ 按「改动后旧实机证据作废」纪律，居中 / α / 结算时机三行的数值**尚未在 .83 构建上重测**（.83 实机只复验了「动画期卡面与文字可见」这一对照项）。
> ⚠️ .83 那一行的数字在**本轮复核时被重算并替换**：原记「45/43/44/42px、对照 42919px、第二轮 0–3px」系当轮探针自带比对脚本的口径，该脚本与裁剪框已按「收尾删探针」规则随探针删除 ⇒ 无法复算（二次验证判为「无法判定」）。现行数字用**写死在行内的口径**（基准帧 + 窗口 + 阈值）重测，不依赖任何脚本。
> ⚠️ 取证帧 `run/1.21.1/screenshots/af3_b*.png` 与 `temp/t-verify/crops4/*.png` 均**未入库**（`run/` `temp/` 被 .gitignore 排除，属本机留存）⇒ 这些数字只能在保留帧的机器上复算；换机后按现象层（卡在 13t 完全消失、缓冲窗无文字、源码 `if (a >= 4)` 守卫）验收。
> 以上均为**过去已完成**的验证；再次改动相关代码/资源后同样作废，需重测。

**筹码商店批（`.97`）的实机与静态判据**（明细见 `docs/handover-2.0.0-SNAPSHOT.97-chip-shop.md` §3）：

| 判据 | 实测 | 版本 |
|---|---|---|
| 筹码五条用例（1.21.1） | `CHIP-LOGIC`（58 步/34 断言）/ `CHIP-REGEN`（38/11）/ `CHIP-CLIENT-BUY`（19/10）/ `CHIP-CLIENT-REFRESH`（19/10）/ `CHIP-CLIENT-DENY`（19/10）**全部 PASS** | .97 |
| 筹码静态守门（两线） | `CHIPUI_TOTAL_FAIL: 0` / `CHIPUI_RESULT: OK`（G1..G6 + G9..G21 + G21b 按线分支 + G22 配方撞车 + G23..G27） | .97 |
| 筹码变异（两线） | `MUT_TOTAL: 37，判红 37` / `MUT_RESULT: OK`（37 条逐条 SHA256 字节还原；harness 侧失败改走 `MUT_HARNESS` + `exit 2`） | .97 |
| 筹码实机收尾 | `MT_CLEANUP_SAVES: PURGED 1` + `MT_CLEANUP_RESULT: OK` + `MT_STOP: OK (1s)`；报告需 `--phase report --allow-auto`（否则被批量编排闸门拦，退出码 2） | .97 |
| 图标与池 | 61 张 `textures/gui/chip_shop/chips/*.png` 32×32、与源素材逐字节一致；池 61 条与计划表 §1 逐行一致（稀有 19 / 史诗 24 / 传奇 18） | .97 |

> ⚠️ 上表的读数取自与提交内容一致的产品源码；其后只改过注释/文档/版本号/测试资产 ⇒ 若要求 `.97`/`.98` 产物自身的实机证据需重跑（见批次文档 §4-7）。
> ⚠️ **forge 线本批没有实机**（探针 `c*` 命令与用例都还没移植到 1.20.1）—— 欠账已登记，不得据此声称两线实机对等。

**`.132` 三批（AutoJS 通道 / LINGLING 用例 / 强化拒止牌面）的实机判据**：

| 判据 | 实测（机器行原文） | 版本 |
|---|---|---|
| AutoJS 求值通道 | `MT_CASES_SUMMARY: AUTOJS-SMOKE-1.21.1=PASS`（负例反证：`await_state` 收到字符串 `"false"` 曾瞬时假通过，修后等 9 次重试才 FAIL） | .132 |
| LINGLING-1.21.1 用例 | `MT_CASE_RESULT: LINGLING-1.21.1 = PASS` + `MT_CASES_SUMMARY: LINGLING-1.21.1=PASS`（42 step / 22 assert，exit 0） | .132 |
| lingling 读数样本 | a_grant `{"eq":true,"granted":true,"rep":2,"bar":0}`；c_read `{"own":5,"bonus":5,"eff":5,"stacks":0,"foreign":1}`；c_cap_read `{"own":23,"bonus":23,"eff":20,"max":20}`；d_damage `{"gg_tracked":10,"gg_control":5,"gg_diff":5,"pa_tracked":6,"pa_control":6,"pa_diff":0}`；e_trigger `{"rep_delta":1,"atk":6,"spd":105,"hasSurge":true,"dur":1200,"cd":3600,"eff":5}`；g_tab `{"chips_enabled":false,"lingling":1,"total":152}` | .132 |
| 冷却口径 | `cd=3600` tick = **180 s**，而规格写 120 s ⇒ **REFUTED（口径不一致，未改代码，登记待裁决）** | .132 |
| 强化拒止牌面 | `temp/t74_cmp.py`：`[front] blob_px=13557` / `[back] blob_px=13557` 同 quad；`FRONT vs BACK best=id 1.000  2nd=r180 0.824`；末行 **`OVERALL: PASS (criterion: FRONT==BACK as id >=0.98)`** | .132 |
| 贴图哈希 | 两线 `textures/entity/repulsion.png` = **B59C576BD944F226D8B1C9BB51F984BD42477530660EAEA944E4FD0252088EBE**（改前 `DDD9E97C…4A9E`；中间失败版 `DADF0537…CE4F`） | .132 |
| 无头顶名条 | `client/RepulsionRenderer.shouldShowName` 恒 `false`；截图里屏幕上方那块「强化拒止 100/100 / Astral Dice」是 Jade/HUD 框，不是名条 | .132 |
| 编译 | `:neoforge-1.21.1:classes :forge-1.20.1:classes` ⇒ `BUILD SUCCESSFUL`（84 条既有 deprecation 警告） | .132 |
| 收尾 | `MT_CLEANUP_SAVES: PURGED` + `MT_CLEANUP_RESULT: OK` + `MT_STOP: OK` | .132 |

> ⚠️ 强化拒止「两面图案一致」**已判 PASS**，但「牌面厚度边在相反两侧」这一条**无法用像素定量确认**（两面同图案后两帧 bbox 平均差 1.6/765）⇒ 仅以「同机位修前测得 r180、修后测得 id 1.000」作为两面确实不同的替代证据，详见 §6-26。
> ⚠️ 上述截图存于 `run/1.21.1/screenshots/`（被 .gitignore 排除，属本机留存），判据脚本在 `temp/`；两者均未入库 ⇒ 复算需重跑同机位。

---

## 5. 遗留项 / 待办

**A. 待开发 / 欠账（按「需要什么才能继续」写；明细见 `docs/handover-2.0.0-SNAPSHOT.97-chip-shop.md` §4）**
1. **P1 两线移植（工作量最大）**：把 `.61`~`.97` 全批（事件卡 GUI、事件选择界面（含 4 条网络消息）、分阶图标与 1:00 计时器、发币漏斗、手册/tooltip 文案、卡牌商店方块、**筹码商店整批**）移植到 `neoforge-26.1.2` 与 `fabric-1.20.1`。已核：两线目录下**根本没有** `client/EventCardOverlay.java`、`block/CardShopBlock.java`、`shop/ChipShopCatalog.java` ⇒ 属**整体新建**；移植时必须一并带上 §6-10 的 `a>=4` 文字守卫、四态 `facing` + 模型 `particle` 键、筹码屏「条带内相对坐标」口径（批次文档 §6-1）与 §6-9 的变异 harness 说明。两线 lang 与 ModTooltipHandler 里**仍是旧「麻烦制造者」文案**（故意未改）。差异映射：`docs/compat-26.1.2-neoforge.md`、`porting/fabric-1.20.1/FABRIC-DIFFS.md`。
2. **forge 线实机欠账（本批新增）**：筹码商店在 `forge-1.20.1` 只过了静态判据（编译 + 守门 + 变异），**没有任何实机用例**。补齐需要：① 把服务端探针 `c*` 命令族与客户端 770/771/772 场景移植到 `scripts/test/resources/kubejs/1.20.1/**`（注意 1.20.1 的 `sp_closeMenu`/`SP_ItemStack`/六参 `use(...)` 形态）；② 复制 5 条用例为 `CHIP-*-1.20.1.json`；③ 用户下达后跑 env/launch/case/stop --purge-saves。
3. **fabric 线产物缺档**：`fabric-1.20.1/build/libs` 最新仍是 `…SNAPSHOT.49+fabric_1.20.1.jar`（2026-10-08 16:55）—— `.50`..`.98` 的 bump 都没重跑该线（`mt_build.ps1` 不认 `fabric-1.20.1`，要走 `scripts/test/fabric/ft_build.ps1`）。是否补跑**需用户裁决**。
4. **守门 6 条盲区 + F 系补变异（审查建议，未动手）**：① 越线字面只覆盖 `block/ChipShopBlock.java` 且只 2 个字面（`verify_chip_shop_ui.ps1:636`/`:619`）；② G22 不校验 T/M 材料值；③ G23 不校验 DataSlot 下标常量数值；④ G6 只查子串 `chip_shop`；⑤ 无网络包 id 唯一性/顺序/方向判据；⑥ 无跨线值比对。建议补的六条 forge 侧变异：F1 包注册次序位移、F4 附件键名与 `.sync()`、F6 删 `anchor <= 0L ||`、F8 删 `ownsEverything` 分支、F13 `DATA_PRICE` 下标、F14 `HIDDEN_X` 常量。
5. **P2 待用户裁决（计划表 §11 Q2）**：刷新已达上限时锚点停在过去 ⇒ 花 1 次刷新后被下一 tick **立即补回**（偏「免费」）。最小修法 = 刷新成功后 `anchor = Math.max(anchor, now + REFRESH_REGEN_TICKS)`。实机已用 `cregen CG pend` 取证。
6. **报告里的历史红标**：`SHOP-M-1.21.1` 与 `DIAG-1.21.1` 的 FAIL 标记残留当前世代（`scripts/test/cases/.mt_run_state.json` 的条目级平面映射、无时间戳）⇒ `--phase report` 总判恒 FAIL（筹码五条本身全 PASS）。处置：重跑这两条或走世代轮转（`scripts/test/mt_report.ps1:246-286`）。
7. **`.97`/`.98` 产物的实机时点**：五用例读数对应的产品源码与提交一致，其后只改注释/文档/版本号/测试资产 ⇒ 若要求「产物自身的实机证据」需重跑一轮（约 15 分钟）。

**B. 设计待裁决**
8. 「指向高亮」与「单击选中」共用同一张 fanny_skill_event_select（素材族只有两张卡底图）。
9. 冷却 180s vs 面板超时 15s：正常游玩中「15 秒内二次使用」先被冷却门挡住，到不了 already_open 分支（空放修复正确但路径罕见）。
10. tier ≤ 0 退回基础图 investigation_stage.png 的分支无实机取证（正常存档不可达，仅源码依据）。
11. card_shop 顶面道具簇的「正向」在固定机位（方块南侧、与放置者反向）下无法唯一判定是否应朝放置者；.83 实机结论 = 四面同贴图、无正背面、道具簇随 facing 正确旋转 ⇒ 不构成缺陷，如要裁决看 `temp/adfix/m_place2.png` 四张特写。
12. **筹码商店刷新锚点的 P2 语义**（见 A-5）：到上限后锚点停在过去 ⇒ 花 1 次被下一 tick 立即补回。

**C. 环境 / 工具**
13. **子代理模型**：DeepSeek（`deepseek-official` / `deepseek-flash`）在本会话实测可用（`.83` 批 4 个、筹码批 2 个——移植与功能审查——全部完成）；mimo/kimi 仍在本 tool 实例被**构造期禁用**（`child model selection is disabled for this tool instance`）⇒ 需**全新会话**或宿主放行后才可复验。
14. `sound/` 目录约 21 个 wav 未转 ogg。
15. 第三方渲染注入的深蓝斜体模组名行（如「星之骰戏」）来源未定位（与本模组无关）。
16. 1.20.1 dev 环境里 **Patchouli 手册手持物呈紫黑格**（`server.log hand=1 guide_book`）：纯 dev 贴图观感、与本批改动无关，未处理；线上/实机如也出现再单开任务。
17. **仓库根 4 个未跟踪垃圾目录（2026-10-18 已销账：实测已不存在，见 §8-1）**：`%SystemDrive%/ProgramData/Microsoft/Windows/Caches/*.db`（字面量 `%SystemDrive%`；某进程按 cwd 写入未展开路径所致，内容 = Windows 字体缓存）。当轮**未删除**（避免在无引用证据时删系统缓存镜像物）；**2026-10-18 复核：该 4 行已不存在**，`git status --short -uall` 现只剩 `models/*.bbmodel` 两行用户资产。历史口径保留如下 —— 提交时**不要用 `git add -A`**，改用显式顶层路径 `git add -A -- AGENTS.md CHANGELOG.md CHANGELOG_ZH.md docs neoforge-1.21.1 forge-1.20.1 neoforge-26.1.2 fabric-1.20.1 scripts`。
18. **`mt_build.ps1` 不支持 fabric**：`--version fabric-1.20.1` ⇒ `MT_ERROR: 未知版本 fabric-1.20.1（可选：1.21.1 1.20.1 26.1.2）`；fabric 线用 `scripts/test/fabric/ft_build.ps1`。
19. **测试环境的第三方引脚会漂移**：用户升级整合包后 `mt_env.ps1 mods` 会 `MT_MODS: BLOCKED — 整合包缺少 *modernfix-neoforge*X*`（本批实测 5.27.24 → **5.27.26**）⇒ 同批改 `scripts/test/mt_env.ps1:48` 与 `AGENTS.md` 测试环境表那一行。
**D. `.132` 批新增欠账（如实登记，禁删；判据明细见 §4 的 `.132` 判据表）**

20. **强化拒止「厚度边在相反两侧」不可测度（`.132` 新登记）**：两面图案改为逐像素同一律后，正/背两帧牌面 bbox 平均像素差仅 **1.6/765**、列差 2.7 vs 2.3（无翻转），像素法判不出「两面不同」。现行替代证据 = 同机位在修前测得 r180、修后测得 id 1.000 ⇒ 两个机位确实对着不同的面且改动只落在北块。要更强的证据须从侧视角（约 30°）拍厚度边，**未做**。
21. **forge 线无用例（`.132` 新登记）**：`LINGLING-1.21.1` / `AUTOJS-SMOKE-1.21.1` 只在 1.21.1 实机跑过；`forge-1.20.1` 仅过 `:forge-1.20.1:classes` 编译 + 贴图同哈希 ⇒ **实机零证据**。
22. **P1 两线未移植（`.132` 新增内容）**：怪力乱神（lingling）立牌、强化拒止贴图/UV 修正都只在 P0 两线；`neoforge-26.1.2` 与 `fabric-1.20.1` 未移植。
23. **冷却读数口径陷阱（`.132` 新登记）**：`ModAttachments.getSignActiveCooldownEnd(p)` 是**绝对 tick**（种在世界时钟上），而 `mcp().getTickCount()` 随服务器重启归零 ⇒ 用例必须先 `--phase env` 重建世界再读；否则实测 `cd=15600`（差 12000t）会假 FAIL。
24. **交接文档惯性滞后**：本文件与 `docs/handover-new-session-context.md` 在 `.98`→`.132` 期间长期没跟版（§3 只到 `.97`、索引末行停在 L3488 导致 `verify-split.py` check#3 长期 FAIL）⇒ 已按 `AGENTS.md`〈契约 · C. 交接文档用法〉补齐；后续每批收班必须刷新，见该节「交班义务」。
25. **`code_review` 覆盖不完整（`.132`，如实登记）**：两个 `client/RepulsionModel.java` 复核 `completed` / 0 findings；但 `scripts/test/cases/LINGLING-1.21.1.json` 连续两轮 `MODEL_INVALID_JSON`（`Model response is not complete JSON … envelope=plain, shape=non-json-leading`）、`textures/entity/repulsion.png` 一律 `blocked`（非 UTF-8）⇒ **用例 JSON 与贴图没有第三方复核证据**，现由主会话实测替代（用例：实机 PASS 22 断言；贴图：两线 sha256 一致 + 像素判据 PASS）。要补须换复核通道（一次性独立子代理）。
26. **既存搁置（非本批引入，仍有效）**：① `scripts/test/cases/` 下 229 条 legacy 用例未迁 `eval_js`（统计与迁移方式见 `docs/features/autojs-legacy-debt.md`）；② `neoforge-26.1.2` / `fabric-1.20.1` 的 AutoJS 实机**一次都没跑过**（含 `AUTOJS-SMOKE`，见 §5-22）；③ lingling 冷却 **实测 180 s（`cd=3600` tick）vs 规格 120 s** 属 **REFUTED 规格漂移**（§4 L177）—— 本批**未改任何一边**，待用户裁决「改规格」还是「改代码」。


---

## 6. 已知坑与教训

1. **批处理空参陷阱**：`for %%A in (%*) do (` 在无参数时展开成 `for %%A in () do (` ⇒ 语法错误、窗口闪退。必须用 `if not "%~1"==""` 守卫。
2. **手动启动脚本不要塞前置判断**：曾比对 gradle.properties 引脚与 mods 里 jar 名 ⇒ 每次升版号都 FAIL。用户裁决：手动脚本**只启动游戏**（现 6 行）。
3. **mt.ps1 --phase launch 需要世界存在**（purge 之后必须先 --phase env）。
4. **LF 锚点在 CRLF 文件里静默不命中** ⇒ 用行裁切或显式 \r\n。
5. **行裁切必须编译验证后再提交**（曾误删一行声明，把编译失败提交进去，后 --amend 修复）。
6. **改动后旧实机证据作废**：新构建必须先停旧实例再测（用户硬规则）。
7. **并发代理会互相踩**：同一工作树多会话同时跑实机/改文件会串命令、探针互相覆盖；开工前先 git status + 确认无其它会话。
8. **增量编译不重打包**：run/<线>/mods 里是上次 build 的 jar；只 compileJava 不 jar 会跑旧代码且不报错。
9. **payload 类不得直接引用 net/minecraft/client/**（专服 RuntimeDistCleaner 崩）**：客户端动作下沉到 client helper + FMLEnvironment.dist == Dist.CLIENT 守卫。
10. **时间源唯一化 + alpha 钳制 + 文字守卫**：动画与结算共用 DELAY_TICKS=38 / CARD_ANIM_TICKS=28 / SETTLE_BUFFER_TICKS=10，进度必须 clamp 到 [0,1]。⚠️ **只 clamp 不够**：1.21.1 `Font#adjustColor`（`Font.java:175-177`）/ 1.20.1（`:113-115`）把 alpha<4 的颜色**强制弹回不透明** ⇒ 淡出末段与缓冲窗文字残留（.83 前真实缺陷）。画文字前必须 `if (a >= 4)`，颜色才 `| (a << 24)`。
11. **KubeJS 探针的 tick 语义**：脚本里发命令（setblock 等）**在本 handler 同 tick 不生效、tick 末才落地** ⇒ 「先清空再放置」会把刚放好的方块擦掉（.83 实机 RUN1/RUN2 假失败的根因）。正解 = 用 Java/KubeJS API **直写** + 把「摆位」与「放置」拆到**两个 tick**。
12. **Rhino 里没有 Entity#setYRot**（`TypeError: Cannot find function setYRot`）⇒ 转向用 `player.connection.teleport(x,y,z,yaw,pitch)`。
13. **1.21.1 的 HorizontalDirectionalBlock 把 `codec()` 重声明为抽象** ⇒ 自定义朝向方块必须补 `public static final MapCodec<X> CODEC = simpleCodec(X::new);` + `@Override public codec()`；1.20.1 无此机制（两线不对称，只有编译 1.21.1 才暴露）。
14. **复核通道的备用路径**：`code_review` 原生工具可能报「原生子 Agent 未完成」或 600s 超时 ⇒ 立即改派**一次性独立子代理**承担（brief 规格不变：目标/路径/不变量 R1..Rn + 逐字证据 + 优先反例 + 取不到证据写「无法判定」）；**不得**因工具失败跳过复核。
15. **实机期的进程与会话**：客户端可能被外部终止（日志无 shutdown 行、无 crash-report）⇒ 尽量在**同一个程序内**完成 stop→env→launch→取证；收尾核对命令行含 `fml.modFolders=...\<线>\build\classes` 的 java 进程数为 0。
16. **筹码屏的命中坐标是「条带内相对」**（`.97` 批实测）：`ChipShopScreen` 的 `CELL_*`/`LABEL_*`/`CONFIRM_*`/`REFRESH_*` 都相对条带，命中走 `isHoveringStrip → stripX/stripY`（再叠加 `STRIP_X=34`/`STRIP_Y=6`）。探针少加偏移时 `mouseClicked` **落空却返回 true**（`ret=1` 是假信号）⇒ 表现为 `sel` 恒 -1、确认键空转、屏不关。正确中心：筹码格 (99,45)/(142,45)/(185,45)、标签框 (…,74)、确认键 (57,54)、刷新键 (227,54)。
17. **`inject_command` 走聊天通道，容器屏开着会被吞**：筹码屏/卡牌屏开着时注入的命令**不执行也不报错** ⇒ 屏内动作只能由客户端探针用 `csCmd`（`mc.player.connection.sendCommand`，**不带前导斜杠**、GUI 开着也能发）。
18. **购买成功后服务端同拍关店 ⇒ 屏内的 `step N` 读数永远来不及跑**：`closeContainer()` 是 `handleBuy` 最后一条语句，客户端下一 tick 进关屏分支、场景机复位。正解 = 在**关屏那一拍**补落读数（客户端态是**类级静态**、不随屏销毁），并用 `csCmd` 同拍回读服务端 `AP_<tag>_CST`。
19. **跨用例/跨轮次状态泄漏**：筹码附件与**世界里的方块**都会留存 ⇒ 用例必须自带 `creset`；`cblock` 的 `set=1` 在二次运行时因方块已在位变 `set=0`，需先 `/setblock ~2 ~ ~ air`（探针用 `player.blockPosition().offset(2,0,0)`，命令侧 `~2 ~ ~` 等价）。
20. **服务端 `denyReason()` 恒为 `DENY_NONE`**（`player.level().isClientSide() ? clientDenyReason : DENY_NONE`）⇒ 探针在服务端读到的 `deny=` **不是**拒绝证据；三条拒绝也没有服务端日志 ⇒ 判据只能是「状态未变 + 余额未变 + 无购买行」。
21. **`run_code` 自身有 120s 硬 deadline**：`timeoutMs` 必须传给 `run_code`（只传给内层 `tools.pwsh` 无效）；链式跑多条用例会被拦腰掐断（本批踩过：LOGIC 第 85/90 行被杀）。长任务改后台 job + 轮询。
22. **`--phase report` 被批量编排闸门禁用**（`MT_AUTO_DISABLED`、退出码 2）⇒ 加 `--allow-auto`；报告总判 FAIL 可能只反映**同世代的历史标记**（`.mt_run_state.json` 无时间戳平面映射）。
23. **变异 harness 的行尾陷阱（已修）**：脚本内锚点用 LF 拼、forge 线源码是 CRLF ⇒ 多行锚点 `IndexOf = -1`、「变异根本没注入」会被静默当成一条结果。现已按**目标文件自己的行尾**归一化（`ConvertTo-FileEol`）并把 harness 失败与「守门漏判」分桶（`MUT_HARNESS` + `exit 2`）；新增 **M37** 用多行 CRLF 锚点长期守住这条。
24. **工具调用侧的两个硬性小事**：`tools.list_agents`/`tools.job_list` 等**必须传空对象**（`await tools.list_agents({})`），否则报 `ToolCallError: binding arguments must be lossless JSON`；`tools.edit`/`tools.write` 改**已存在**文件前必须先用 `read` 工具读过（`fs.readFileSync` 不算），否则报 `file has not been read`。
25. **AutoJS 求值语义（`.132` 实测，写任何 `eval_js` 前必读）**：① `eval_js` **只求最后一个表达式**，顶层 `return` ⇒ `EvaluatorException: invalid return (autojs-eval#N)`、`syntax error` 同理 ⇒ 片段必须是 IIFE 表达式；② 非标量返回值被序列化成字符串 `"[object Object]"`，要拿结构必须自己 `JSON.stringify((function(){…; return {…};})())`（测试台 `-Mode Json` 已代包，用例里**不要**再自己 stringify）；③ 布尔必须 `!!(…)`，且 `-Mode Bool` 回来的 `value` 是**字符串** `"true"`/`"false"` ⇒ 判真只能比字面量（曾把字符串 `"false"` 当 true 造成瞬时假通过）。
26. **牌面视觉判据的两个陷阱（`.132`）**：① 天空 + 云 + 地平线雾霾会让「最大连通域」把牌面与背景连成一片（blob 20 万 px）⇒ 必须在牌后放 **`minecraft:red_concrete` 纯色墙**、夜间先 `time set 4000` + `doDaylightCycle false`、并确认 `mc.screen === null`（曾有 Patchouli 书 GUI 把牌面完整遮住，blob 只剩 392px）；② 菱形的「四极值点 → 正方形角」反算带 **90° 象限自由度**，绝对映射会整体偏 90°，只有**正面 vs 背面**这类相对量可靠（早期「南面=r180」口径即由此作废）。
27. **`/kill` 杀不掉 RepulsionEntity**：`LivingEntity.kill()` 走 `hurt()`，而 `RepulsionEntity.hurt()` 恒返 false ⇒ 只能用 Java 侧 `e.discard()`；且该实体 `noPhysics = true`，召唤必须带 `{NoGravity:1b}`，否则脚下无方块会一路下落。
28. **`run_code` 大 cell 的 worker-exit**：单个 cell 里既跑较长 `pwsh`（gradlew / mt.ps1 / verify-split）又要后续工具调用，极易 `worker-exit: kernel helper did not answer utilization sampling before its deadline`；**崩溃 cell 里的 `tools.edit` 可能根本没落地** ⇒ 必须回读确认。长命令一律 `run_in_background: true` 起 job 再 `job_output` 收。


---

## 7. 关键路径索引

| 主题 | 路径 |
|---|---|
| 事件框架 | `<线>/src/main/java/com/merlinkitsune/astral_dice/event/`（AstralEventRegistry / AstralEventDispatcher / EventEffects / EventCardService / EventChoiceManager / ModTooltipHandler） |
| 事件卡与选择界面（客户端） | `client/EventCardOverlay.java`（淡出文字守卫 `a>=4`）、`client/EventChoiceScreen.java`、`client/EventChoiceClientHandler.java` |
| 卡牌商店方块 | `block/CardShopBlock.java`（HorizontalDirectionalBlock 四态 + CODEC）、`assets/astral_dice/blockstates/card_shop.json`、`models/block/card_shop.json`（含 `particle` 键）、`textures/block/card_shop.png` |
| 网络 | neo：`network/EventCardPayload.java` + `EventChoice{Start,Confirm,Close,Tick}Payload.java` + `ModPayloads.java`；forge：`EventCardMessage.java` + `EventChoice*Message.java` + `ModNetwork.java` |
| 立牌 | `item/sign/BaseSignItem.java`、`FannySignItem.java`（大侦探）、`BonnieSignItem.java`（秘密侦探） |
| 发币漏斗 | `resource/ResourceConversion.java#giveItem` → `economy/StarCoinCurrency#tryAbsorbIntoWallet` |
| 事件卡素材 | 源 `images/shop/fanny_skill_*.png` → `<线>/src/main/resources/assets/astral_dice/textures/gui/shop/` |
| **筹码商店（`.97` 批）** | `shop/ChipShop{Catalog,Roller,State,Opener,RegenTicker,Menu}.java`、`block/ChipShopBlock.java`、`screen/ChipShopScreen.java`、`client/ChipShopClientState.java`、`network/ChipShop{Buy,Refresh,Deny}Payload.java`；资源 `assets/astral_dice/{blockstates,models/item}/chip_shop.json` + `textures/gui/chip_shop/chips/*.png`（61）+ `data/astral_dice/{recipe\|recipes}/chip_shop.json` + `data/astral_dice/{loot_table\|loot_tables}/blocks/chip_shop.json` |
| 筹码守门与实机资产 | `scripts/verify/verify_chip_shop_ui.ps1`（G1..G6 + G9..G27）、`scripts/verify/mutate_chip_shop_ui.ps1`（37 条）、`scripts/test/cases/CHIP-*-1.21.1.json`、`scripts/test/resources/kubejs/1.21.1/{server_scripts/astral_shop_probe.js,client_scripts/astral_shop_client.js}` |
| 筹码设计 / 界面 / 平台差异 | `docs/chip-shop-logic-plan.md`、`docs/chip-shop-ui-spec.md`、`docs/compat-1.20.1-forge.md` §十七 |
| 文档 | `AGENTS.md`、`scripts/test/TESTING-SPEC.md`、`docs/compat-1.20.1-forge.md`、`docs/compat-26.1.2-neoforge.md`、`docs/handover-new-session-context.md`、**`docs/handover-2.0.0-SNAPSHOT.97-chip-shop.md`（历史详案，`.97` 批）**、本文件 |
| **AutoJS 求值通道 / 用例（`.132`）** | `scripts/test/lib/Mt.AutoJs.psm1`（`Invoke-MtAutoJsEval -Mode Raw\|Json\|Bool` + `ValueParsed`）、`scripts/test/mt_case.ps1`（`eval_js` 走 Json / `await_state` 走 Bool）、`scripts/test/cases/AUTOJS-SMOKE-1.21.1.json`、`scripts/test/cases/LINGLING-1.21.1.json` |
| **怪力乱神（lingling）** | `item/sign/LinglingSignItem.java`、`sign/LinglingSignManager.java`、`effect/ModEffects.LINGLING_SURGE`、`client/Lingling*`、`scripts/test/cases/LINGLING-1.21.1.json` |
| **强化拒止牌面（`.132`）** | `<线>/src/main/resources/assets/astral_dice/textures/entity/repulsion.png`（北块 = 南块逆时针 90°）、`client/RepulsionModel.java`（注释含 UV 口径）、`client/RepulsionRenderer.java`（`shouldShowName` 恒 false）、`entity/RepulsionEntity.java` |
| **`.132` 实测取证资产（未入库，重跑前先看是否已被清理）** | `temp/t74_cmp.py`（牌面正/背 IoU 对比：`best=id` / `2nd=r180`，判据 `FRONT==BACK as id >=0.98`）、`run/1.21.1/screenshots/2026-10-18_17.11.15.png`（正面帧）/`2026-10-18_17.12.01.png`（背面帧）、`docs/features/lingling-sign-spec.md`（冷却规格 120 s，与实测 180 s 不一致 ⇒ §5-D-26） |


---

## 8. 新会话建议开工步骤

1. `git -C F:\MCProject\astral_dice_multiloader-next status --short -uall` + `git log --oneline -3`（确认收尾提交；**HEAD 以 `git log --oneline -1` 实测，勿照抄旧哈希**；`status` 正常应只剩 `models/*.bbmodel` 两行用户资产 —— `%SystemDrive%` 缓存目录已于 2026-10-18 销账，见 §5-C-17；若交接文档本身有未提交改动，先 `git diff` 看清再接手）；
   ⇒ **权威顺序见 `AGENTS.md`〈代理协作契约 · C. 交接文档用法〉**：git status → git log -1 → 四线 `mod_version` → 读正本；**文档里的一切数字都是快照，必须实读**。
2. 读本文件 + **`docs/agent/AGENTS-full.md`「待办交接」第 39 条（`.132` 三批正本：AutoJS 求值语义 / 测试台闸门 / LINGLING 用例 / 强化拒止牌面）** + `docs/handover-2.0.0-SNAPSHOT.97-chip-shop.md`（历史详案） + `docs/handover-new-session-context.md`。
3. 要动代码：先 `mt_build` 确认基线绿，再改；改后 `mt_build` + 相关 `scripts/verify/**`；
4. 复核用 `code_review`（失败即改派一次性子代理）；实机**先问用户**；
5. 提交：升四线版本 + 两份 CHANGELOG + `git commit`（**不 push**）；收尾核对另一工作树为空且 HEAD 不变。