# 交接文档 — astral_dice_multiloader-next（开发线 multi-dev-next）

> 生成：2026-10-11 20:20（Asia/Hong_Kong）｜工作树 `F:\MCProject\astral_dice_multiloader-next`
> 分支 `multi-dev-next`｜生成时基线 HEAD **`bb48a206`**（本文件随 `.87` 提交落地；最新值以 `git log -1` 为准）｜四线版本 **`2.0.0-SNAPSHOT.88`**（四条线同号）
> 工作树状态：**干净**（`git status --short -uall` 空）；**未 push**。
> 新会话提示词：`docs/handover-new-session-context.md`（可整份复制给新会话）。

---

## 0. 一页速览

| 项 | 值 |
|---|---|
| 优先级模型 | **P0 = neoforge-1.21.1 + forge-1.20.1（同批、优先）**；**P1 = neoforge-26.1.2 + fabric-1.20.1（待移植）** |
| 功能对等现状 | **P0 两线已对等**（事件体系 + 事件卡 GUI + 事件选择界面 + 发币漏斗 + 手册/tooltip 文案 + 卡牌商店方块）；**P1 两线未移植** —— 已核：`neoforge-26.1.2` 与 `fabric-1.20.1` 目录下**不存在** `client/EventCardOverlay.java` 与 `block/CardShopBlock.java` |
| 构建 | `pwsh -NoProfile -File scripts/test/mt_build.ps1 --version <线> --timeout 300` ⇒ `MT_BUILD: OK` |
| 实机 | `mt.ps1 --version <线> --phase env` → `--phase launch`；**收尾 `--phase stop --force --purge-saves`** |
| 手动启动（人工自测） | 双击 `run/Start-<线>.bat`（只启动，**无任何前置判断**） |
| 复核 | `code_review` **优先**（一次性独立会话）；若该工具实例报错/超时 ⇒ 改派**一次性独立子代理**承担，brief 规格不变（见 §1） |
| 实施分工 | **P0 基准线 `neoforge-1.21.1` = 主 Agent 亲自编写** → `code_review` 复核 → **独立子代理功能审查** → 确认无误后才派子代理移植 `forge-1.20.1` → 该线再 `code_review` + **另一个独立子代理**功能审查（AGENTS.md 硬规则第 7 条，2026-10-12） |
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
| 筹码商店界面骨架 | .86 | 本提交 | 13 张贴图入库 + ChipShopScreen/ChipShopMenu/ChipShopClientState（两线）+ chip_shop 菜单注册与客户端绑定 + 三语 4 键 + docs/chip-shop-ui-spec.md + scripts/verify/verify_chip_shop_ui.ps1（含变异测试反证）；业务逻辑待后续批次 |

**功能面现状（P0 两线已对等）**：13 个事件 + 弹卡 GUI（淡出后无文字残留）+ 事件选择界面（2 选 1、15s、确认即触发、死亡/登出强触发第一项）+ 调查阶段 4 阶图标与 1:00 计时器 + 全模组发币优先入钱包 + 手册/tooltip 文案同步 + 卡牌商店方块（四态朝向 + 自有碎裂粒子）。

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

---

## 5. 遗留项 / 待办

**A. 待开发（P1）**
1. **neoforge-26.1.2 + fabric-1.20.1 未移植 .61~.83 全批**：事件卡 GUI、事件选择界面（含 4 条网络消息）、调查阶段分阶图标与 1:00 计时器、发币走钱包漏斗、手册/tooltip 文案、**卡牌商店方块**。已核：两线目录下**根本没有** `client/EventCardOverlay.java` 与 `block/CardShopBlock.java` ⇒ 属**整体新建**而非改；移植时必须一并带上 §6-10 的 `a>=4` 文字守卫（否则重演文字残留）与四态 `facing` + 模型 `particle` 键。两线 lang 与 ModTooltipHandler 里**仍是旧「麻烦制造者」文案**（故意未改）。
2. **筹码商店素材已就位未接线**：P0 两线有 `models/block/chip_shop.json` + `textures/block/chip_shop.png`（源图 `images/shop/chip_shop_GUI.png`），方块/实体/物品/配方/语言键均未加；P1 两线连素材都还没有。

**B. 设计待裁决**
3. 「指向高亮」与「单击选中」共用同一张 fanny_skill_event_select（素材族只有两张卡底图）。
4. 冷却 180s vs 面板超时 15s：正常游玩中「15 秒内二次使用」先被冷却门挡住，到不了 already_open 分支（空放修复正确但路径罕见）。
5. tier ≤ 0 退回基础图 investigation_stage.png 的分支无实机取证（正常存档不可达，仅源码依据）。
6. card_shop 顶面道具簇的「正向」在固定机位（方块南侧、与放置者反向）下无法唯一判定是否应朝放置者；.83 实机结论 = 四面同贴图、无正背面、道具簇随 facing 正确旋转 ⇒ 不构成缺陷，如要裁决看 `temp/adfix/m_place2.png` 四张特写。

**C. 环境 / 工具**
7. **子代理模型**：DeepSeek（`deepseek-official` / `deepseek-flash`）在本会话实测可用（.83 批 4 个子代理全部完成）；mimo/kimi 仍在本 tool 实例被**构造期禁用**（`child model selection is disabled for this tool instance`）⇒ 需**全新会话**或宿主放行后才可复验。
8. sound/ 目录约 21 个 wav 未转 ogg。
9. 第三方渲染注入的深蓝斜体模组名行（如「星之骰戏」）来源未定位（与本模组无关）。
10. 1.20.1 dev 环境里 **Patchouli 手册手持物呈紫黑格**（`server.log hand=1 guide_book`）：纯 dev 贴图观感、与本批改动无关，未处理；线上/实机如也出现再单开任务。
11. 仓库根**曾**被落未跟踪目录 `%SystemDrive%/ProgramData/Microsoft/Windows/Caches/*.db`（本轮 env→launch 期间由某进程按 cwd 写入未展开字面路径；内容 = Windows 字体缓存；已删除，来源未定位）。⇒ 每次实机收尾若 `git status -uall` 非空，先看是否又是它。

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
| 文档 | `AGENTS.md`、`scripts/test/TESTING-SPEC.md`、`docs/compat-1.20.1-forge.md`、`docs/compat-26.1.2-neoforge.md`、`docs/handover-new-session-context.md`、本文件 |

---

## 8. 新会话建议开工步骤

1. `git -C F:\MCProject\astral_dice_multiloader-next status --short -uall` + `git log --oneline -3`（确认基线之后的收尾提交、工作树干净；HEAD 以 `git log --oneline -1` 实测，勿照抄旧哈希）；
2. 读本文件 + `docs/handover-new-session-context.md` + `AGENTS.md` 的「工作树纪律 / 二次验证 / 子代理与测试环境硬规则 / 版本优先级」四节；
3. 要动代码：先 `mt_build` 确认基线绿，再改；改后 `mt_build` + 相关 `scripts/verify/**`；
4. 复核用 `code_review`（失败即改派一次性子代理）；实机**先问用户**；
5. 提交：升四线版本 + 两份 CHANGELOG + `git commit`（**不 push**）；收尾核对另一工作树为空且 HEAD 不变。