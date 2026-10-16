# 交接：超天酱/地雷女立牌批（2.0.0-SNAPSHOT.128，2026-10-16）

> 本批由「K3 规划 + DeepSeek-flash 子代理执行」全流程落地（协作契约生效后的首个批次）。
> 触发落盘原因：AGENTS.md 协作契约 A5 上下文闸门（会话超限 ⇒ 落盘后新开会话）。

## 基线（实测）
- 分支 `multi-dev-next`；HEAD = **`17634b80`**（feat 批），其下 `5db5951e`（docs 拆分）；未 push（本地领先 origin 306 条为既有状态）。
- 四线 `gradle.properties` 均为 `2.0.0-SNAPSHOT.128+<后缀>`；P0 两线 jar 已构建（`build/libs/astral_dice-2.0.0-SNAPSHOT.128+*.jar`，20:33）。
- 工作树余留（勿提交）：4 行 `?? %SystemDrive%/…*.db` + 2 行 `?? models/{强化拒止,路障} - Converted.bbmodel`（用户资产，非本批）。
- 主线封包树 `F:\MCProject\astral_dice_multiloader`：HEAD `16b6d356`、规范量法（`-c core.autocrlf=true -c core.fileMode=false`）status 0 行。⚠️ 裸 git 在 WSL 会对 CRLF 生成物报 5 个幽灵 M（pom×1 + 生成模型×4），内容逐字节相同，勿理会。

## 本批交付（冻结规格 = `docs/features/kangel-ame-sign-spec.md`）
- **超天酱立牌 `kangel_sign`**（奇特）：主动「网络天使」（min(1+粉丝,10) 星币 / ≥3 回满 / ≥9 友方 3 币，有队全队、无队 64 格）；被动「小天使请安」（近战命中/被敌对攻击 ⇒ 目标上 `kangel_fan` 无限时长标记；粉丝打佩戴者 -1）；一心同体（队内有地雷女 ⇒ 她也上粉丝、计数归队内每个超天酱、粉丝打她也 -1）；粉丝上限 10、每目标每人一次、跨死亡保留、卸下清零。
- **地雷女立牌 `ame_sign`**（奇特）：主动「感情过量摄入」（爱×2 回血 + 移速 +20% 1:00，**仅自然到期**结算 -4 层/上限+1 封顶 32，奶/命令不结算）；被动「无法承受的爱」（6 防御、受敌对攻击 30s 一层爱、装备 0→2、每层攻+1/防-1 最低 0、爱>4 每层 +1 最大生命）；一心同体供给方口径（超天酱仅得 6 防御、受伤喂全队地雷女各 +1 爱）。
- 配套：3 效果（`kangel_fan`/`ame_love`/`ame_overdose`）+ 5 附件 + 死亡保留双层（neo `copyOnDeath`+`DeathPreservedBonuses` 7 槽+String 表 / forge `AstralData` 白名单 4 键+同款兜底）+ 配方 `GNG/WEW/ADA`、`GKG/WEW/CDC` + 手册 sortnum 24/25 + 三语 964 键 + 贴图 5 张/线。
- 效果数两 P0 线 56→59；附件注册声明计数 1.21.1=120 / 1.20.1=122；手册 signs 目录 26 条。

## 审查留痕（本批抓到并已修）
A1 到期结算永不触发（Expired 派发时实例仍在表内，`getEffect()!=null` 早退恒真）· A2 粉丝 -1 保护面收窄 · A4 6 防御改 kangel 侧 20t 自维护自撤销 · A7 ame 侧粉丝入口初版缺失 · 配方笔误 ALA→ADA · tooltip ×2→x2 回改 · actionbar 治疗量传参修复 · ame 攻击修饰器补 `wearsSign` 闸门。

## 守门证据
两线 `runData` + `mt_build` OK；`verify_crafting_recipe_uniqueness`（97 条 0 碰撞）/ `verify_effect_icons`（四线 224 全覆盖）/ `audit_patchouli_keys`（0 悬空）/ `check_lang_sync`（964 三语一致）/ `verify-split.py` exit=0 全绿。既有红（非本批）：`tooltip_color_audit` 64 处、`verify_bountiful_pools` 跨线口径 6 项（含 .121 筹码 P1 待移植）。

## 欠账 / 待用户裁决
1. **实机验证未做**（等用户明确下达）：建议用例 = 双人组队一心同体双向、粉丝计数与主动三档、爱全生命周期（置 2/30s 闸门/到期结算/卸下清零/死亡保留）、指示器图标与等级。
2. `kangel_fan` 同 id 复用的 PvP 边界（佩戴者指示器会被当作目标标记 ⇒ 超天酱互殴误判 -1；被标粉丝的超天酱会被自己镜像清掉）——是否拆双效果 id，待裁决。
3. 摘骰子保留立牌时瞬态修饰器不被撤销（全局闸门推论，既有口径，登记）。
4. P1 两线（26.1.2 / fabric）本批未移植（优先级纪律）。
5. `models/` 2 个 bbmodel 散落文件归属待用户确认。
6. `tooltip_color_audit` 既有 64 处红（fanny_active / red_heat_warning / storm_approaching / problem_delivery），独立批次处理。

## 流程说明（协作契约首个批次）
派活 9 个子代理（实现→修复×2→复核→功能审查→移植→移植复核→移植功能审查→收尾→合规终审），全部 deepseek-flash、一次性、brief 自包含。复核发现率证明流程有效（A1/A7 为功能性缺陷）。主代理在提交回合亲自跑了若干 git 取证命令（与 A2 严格口径有出入，下轮起改由子代理执行）。
