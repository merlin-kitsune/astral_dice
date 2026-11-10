# 小猎手立牌（inkshadow）规格与实现记录

> 本文是**语义冻结 + 实现记录**：P0 两线（`neoforge-1.21.1` / `forge-1.20.1`）2026-11-10 首版落地（用户 m02258 下达，明确「先做第一版供验收，显示效果由我自己定」）。
> **2026-11-10 第三轮（被动改按存活门对生效，用户 m04094）同样只落在 `neoforge-1.21.1`**；中文 tooltip 由用户亲手改写并按用户版本落盘。
> **2026-11-10 第二轮验收改版（用户 m02953 / m03003）目前只落在 `neoforge-1.21.1`**：门贴地、门面改末地传送门样式平铺地面且只在成对时渲染、站定 1.5 s 传送、每门 5 s 静默冷却、主人先离开 4 格、成对存活 3:00、被动状态指示器用立牌图标。forge-1.20.1 仍是首版语义（见 §5 末行）。
> **代码为真源**：本文任何数字若与代码不符，以代码为准并回改本文。§7 实机取证在验收轮次回填。
> 需求正本：仓库根 `立牌计划表.txt`（小猎手段）+ 用户 m02258 的补充说明；`docs/features/sign-roadmap.md` §2.1 记录排期。

## §0 定名与命名门禁

| 项 | 值 | 出处 |
|---|---|---|
| 中文名 | 小猎手立牌 | `lang/zh_cn.json` `item.astral_dice.inkshadow_sign` |
| 英文名 | InkShadow Sign | `lang/en_us.json` |
| 日文名 | インクシャドウのスタンド | `lang/ja_jp.json` |
| wiki 页（暂定） | InkShadow | `tools/sign_display_names.json` |
| 门禁 `lines` | `neoforge-1.21.1` / `forge-1.20.1` | 同上（P1 三线未实装 ⇒ 不入门禁） |

**命名风险（待用户确认）**：wiki（astralparty.miraheze.org）本轮 API 被反爬拦截（HTTP 403），无法核实英文页名与 `nameJapanese` ⇒ 暂按 id 直译「InkShadow Sign」+ 片假名转写「インクシャドウのスタンド」，`note` 已标注待日服核实。用户确认后改 `tools/sign_display_names.json` 与本表，再跑门禁。

## §1 标识与常量（2026-11-10 第二轮验收改版后的值，全部可在代码内一处调整）

| 项 | 值 | 说明 |
|---|---|---|
| 注册 id | `astral_dice:inkshadow_sign` | `ModItems` 注册，`stacksTo(1)`，稀有度 `ASTRAL_DICE_EPIC`（紫色/史诗） |
| 放置窗口 | `WINDOW_TICKS = 600`（30 s） | 起始门放下后必须在窗口内放下终点门 |
| 成对存活 | `PAIR_LIFETIME_TICKS = 3600`（3:00） | 用户 m03003 更正「一组传送门存活期为3:00」；到期两扇门一起消失，无动画 |
| 放置距离 | `PLACE_RANGE = 8.0D` | 准星射线取落点 |
| 放置期移速 | `MOVEMENT_SPEED` +10%，`ADD_MULTIPLIED_TOTAL` | 放置期专属，终点门放下即移除 |
| 主动冷却 | **全局立牌规则**：`GameplayConstants.SIGN_ACTIVE_COOLDOWN_SECONDS`（默认 180 s = 3600 tick） | 2026-11-10 用户裁决「遵守全局规则，默认 180 秒」⇒ 不覆写 `activeCooldownBaseTicks` / `activeCooldownBaseTicksNoBattery`，直接继承 `BaseSignItem` 默认链（`WeirdDiceHandler.signCooldownTicks`，含充能封顶 180→160 s 与「额外电池筹码 ×0.5」减免）；锁定结束由 `SignActiveLockMachine` 起冷却 |
| 传送冷却 | `TELEPORT_COOLDOWN_TICKS = 100`（5 s，静默） | 同一玩家对同一扇门每 5 s 只能传一次；**无 actionbar 提示**（用户 m02953 第 6 条） |
| 站定半径 | `STAND_RADIUS = 0.55D` | 2026-11-10 用户裁决「判定范围太大，改为踩在门正上方」：按与门盘中心的**水平距离**判定（旧实现用实体包围箱 `inflate(0.6)`，等效半径约 1.2 格，贴边走过也会传）；另要求脚底与门同层（`dy ∈ [-0.5, 1.0]`） |
| 站定判定 | `STAND_TICKS = 30`（1.5 s） | 玩家踩进门盘正上方需连续站定 1.5 s 才实际传送（每 2 tick 累计一次，离开即清零）；**踩上去就每 2 tick 播一次门面粒子**（不等站定完成，冷却中/保险未解除也播） |
| 主人保险 | `OWNER_ARM_RADIUS = 4.0D` | 刚放好的一对门不会立刻传送放置者本人：本人需先离开门 4 格外，该门对主人才解除保险（用户 m02953 第 7 条） |
| 被动叠层 | 每层攻击力 `+2`；**生效条件 = 主人名下存在存活门对**（`INKSHADOW_PAIR_EXPIRIES` 里还有未到期的到期刻），**不再有 2:00 计时** | 2026-11-10 用户裁决「由持续 2:00 改成有属于自己的传送门存在时，效果就能生效」；层数不设上限，门对全部到期即清零并撤攻击修饰符 |
| 敌对伤害 | `PORTAL_HAZARD_RADIUS = 2.5` 格内每 20 tick 受 `2.0` 点 `SKILL_DAMAGE` | 用户口径「魔法伤害」= 本仓 `SKILL_DAMAGE`（与魔渊触须同款） |
| 一次性计数 | `visitedUuids`（NBT `Visited`） | 每扇门 × 每人只结算一次被动叠层；【传送本身】可重复，不受此限制 |
| 显示常量 | `MODEL_SCALE = 1.6` / `GROUND_LIFT = 0.12` / `SURFACE_Y = 0.03` / `SURFACE_RADIUS = 0.62` / `SURFACE_SPIN_TICKS = 200` / `PENDING_RENDER_SCALE = 0.95` | 集中声明在 `client/InkShadowPortalRenderer`，注释「待用户验收调整」 |

状态载体（均为玩家级附件、仅服务端）：`INKSHADOW_PENDING_UUID`、`INKSHADOW_PENDING_DIM`、`INKSHADOW_PENDING_DEADLINE`、`INKSHADOW_LOCKED_SLOT`、`INKSHADOW_STACKS`、`INKSHADOW_PAIR_EXPIRIES`（String，逗号分隔的存活门对到期刻；门对建立时追加一条、每次刷新被动时剪掉已过期项）、`INKSHADOW_STACKS_EXPIRE`（**已停用**，仅为旧存档兼容保留声明）。
冷却复用立牌统一口径（`SIGN_ACTIVE_COOLDOWN_END` / `SIGN_ACTIVE_MAX_COOLDOWN`），不新建。

## §2 语义

### §2.1 主动「暗影之门」

1. 佩戴者按 J（`BaseSignItem.performSkillForCurio` 链路）→ `InkShadowSignItem.handleUse`。
2. **无 pending**：准星 8 格内取落点，生成**起始门**（state=PENDING，不画门面、不可穿越），写 pending（实体 UUID + 维度 + 截止刻 = now + 600），进入**锁定态**（`beginActiveLock`），并施加 +10% 移速。起始门与终点门都由 `InkShadowSignManager` 用 `setPos(x+0.5, y, z+0.5)` 落在方块地面（2026-11-10 改版去掉了早期 +0.25 的悬浮偏移）。
3. **有 pending 且在窗口内**：第二次按键**不经过 `performSkillForCurio`** —— 由网络入口 `SignActivatePayload.handle`（neo21）/ `ModNetwork.SignActivateMessage` 前置钩子（forge）先问 `InkShadowSignManager.tryPlaceEndFromKey(player)`，命中即 `return`。钩子内落地**终点门**，两扇门同时转 ACTIVE（双向可穿越），`expireTick = now + 3600`，清 pending、移除移速、锁定随 `isGateEffectActive` 变 false 而结束并按全局立牌规则起冷却（默认 180 s）。
   - 这样设计的理由：一次主动技能只结算一次。若第二次按键仍走 `performSkill`，会二次触发风扇筹码发牌 / 电流核心 / 技能音效 / `SignActiveTriggeredEvent`。
   - 终点门落点非法（准星没指到可放置方块面）⇒ 红字 `msg.astral_dice.inkshadow_no_ground`，**保留窗口可重试**，不消耗冷却。
   - **提示口径（2026-11-10 用户裁决「只用单个 actionbar，优先公用通道，有覆写用覆写」）**：起始门落地的那一条 actionbar 由公用事件 `SignActiveTriggeredEvent` 承载 —— `InkShadowSignEvents.onSignActiveTriggered` 先 `setHandled()` 压掉基类的通用文案 `<立牌>主动技能已启动!`，再代发 `msg.astral_dice.inkshadow_place_start`；`placeStartPortal` 自身**不再**发提示（早期两条叠在一起）。终点门走前置钩子、不经该事件，仍由 `placeEndPortal` 单选 `msg.astral_dice.inkshadow_place_end`。实机两条各只出现一条。
4. 放置期限制（服务端强制）：无法攻击（`AttackEntityEvent` 取消）、不能切换武器（每 tick 复位锁定槽位）、不能与方块实体交互（`PlayerInteractEvent` 五个子事件取消）；ESC 与立牌技能键不受影响。
5. **作废路径**：窗口超时、死亡（`LivingDeathEvent`）、退出（`PlayerLoggedOutEvent`）⇒ 起始门 discard、pending 清空、移速移除、槽位解锁；终点门不存在 ⇒ 无残留。
6. 起始门在终点门放下前**无效**：PENDING 态不参与穿越扫描，也不触发被动。

### §2.2 被动「双生之影」

- **穿越计数按「每扇门 × 每人」**：每扇门各自维护 `visitedUuids`；同一玩家穿过起始门与终点门各计一次（用户备注明确）。
- 触发：门主人（`ownerUuid`）获得 +1 层，攻击力修饰符 = `2.0 × 层数`（固定 UUID、`ADD_VALUE`）。
- **生效条件（2026-11-10 用户裁决，第三轮）**：层数不再按时间过期 —— 只要主人名下还有存活门对（`livePairDeadline(player) > 0`）就一直有效；门对全部到期后下一拍 `refreshStacks` 把层数清零并移除修饰符。注册表是玩家附件 `INKSHADOW_PAIR_EXPIRIES`：`placeEndPortal` 成功时 `registerLivePair` 追加 `now + 3600`，`livePairDeadline` 每次读取顺手剪掉已过期项，因此不依赖门实体回写、登出/重启/跨维度都自洽。
- HUD 指示器：`inkshadow_stack` 效果的持续时间挂成**最晚存活门对的剩余时间**，门对到期效果自然消失。
- 门主人离线、未佩戴本牌、或已不满足 `isEquipped` ⇒ 仍计穿越但不加层（不报错）。
- **【跃迁核心】联动**：每次成功穿越额外调用本仓最接近的实存物 `WarpEngineChipItem.onPortalOrWaystoneTeleport(player)`（其自身 3600 tick 冷却与充能奖励原样保留）。仓内**没有**名为「跃迁核心」的物品（只有 `warp_engine_chip` 跃迁引擎 / 电流核心 / 原初核心）⇒ 该映射待用户确认。
- 敌对伤害：ACTIVE 门每 20 tick 对 `PORTAL_HAZARD_RADIUS` 内的敌对生物造 2 点 `SKILL_DAMAGE`。

### §2.3 门实体（`InkShadowPortalEntity`）

- `extends Entity implements GeoEntity`；`noPhysics`、无重力、不可拾取、不可被攻击（`isInvulnerableTo` 恒 true、`hurt` 恒 false）、无碰撞箱 ⇒ 不能破坏、不能被攻击、不挡路。
- 穿越（2026-11-10 改版）：ACTIVE 门每 2 tick 在 `inflate(1.0)` 内向候选玩家筛「踩在门正上方」（`isStandingOn`：水平距离 ≤ `STAND_RADIUS = 0.55` 且 `dy ∈ [-0.5, 1.0]`），每名玩家累计 `standingTicks`；有人踩着就每 2 tick `sendParticles(ParticleTypes.PORTAL, 6, 0.32, 0.05, 0.32, 0.02)`（踩上去即有反馈）；累计 ≥ `STAND_TICKS`（1.5 s）且该玩家未在 `teleportCooldown` 内 ⇒ 传送到配对门（**跨维度**用 `teleportTo(ServerLevel, x, y, z, yaw, pitch)`，落点 y = 对面门 y + 0.05），随后：写本门 5 s 冷却、给对面门写 5 s 冷却并清其站定计数、`visitedUuids` 首次为真时结算被动叠层、调跃迁引擎钩子。
- **主人保险**：每 tick 检查门主人与门的距离，超过 `OWNER_ARM_RADIUS` 才把 `ownerArmed` 置 true；未解除保险时主人本人踩门不传送（NBT `OwnerArmed` 持久化）。
- 离开门范围即 `standingTicks` 清零；不再使用原版 `setPortalCooldown`（会连带影响原版传送门，且 5 s 静默要求由本实体自己的表实现）。
- 穿越视听（2026-11-10 用户裁决「传送可使用末影珍珠传送音效」）：出发前在起点、传送后在落点各播一次 `SoundEvents.ENDERMAN_TELEPORT`（`SoundSource.PLAYERS`，1.0/1.0）+ 40 粒 `ParticleTypes.PORTAL`，与 `event/EnderDiceHandler#playTeleportEffects` 同源同参数（掷末影珍珠的传送音效）。落点特效在 `teleportTo` 之后播，跨维度时包才送得到。
- 到期：`expireTick` 到 ⇒ 两扇门一起 discard。

### §2.4 渲染（首版占位，用户验收后定）

- 门体：`inkshadoweffect.geo.json` + `textures/entity/inkshadoweffect.png`（**64×64，与 bbmodel 内嵌贴图逐字节一致**，geo 的 `texture_width/height` 同步为 64、全部 354 个面的 uv/uv_size ×2），GeckoLib idle 动画 `animation.inkshadoweffect.idle`（**4.0 s 循环**，四骨 Y 轴自转时长按 2.0 s 的 `Idle` 关键帧整体×2 —— 2026-11-10 用户裁决「动画太快，速度减半」；运行时 `getAnimation(...).length()` = 80 tick）。
  - 2026-11-10 修（闪修第二轮）：首版曾把 64×64 贴图缩到 32×32 去迁就 geo 的 `texture_width: 32`，等于<b>降分辨率</b>；后来闪烁在关光影时同样出现，用户点名「检查是不是又是降分辨率导致的」⇒ 改为保留原始 64×64 并把 geo 的 uv 空间整体 ×2，同时补 `inkshadoweffect.png.mcmeta`：
    ```json
    { "texture": { "blur": false, "clamp": true } }
    ```
    `blur:false` 关掉 mipmap，避免 cutout 的 alpha 阈值在缩小采样时逐帧把像素裁进裁出（那种「边缘溶解」在远处看起来就是闪烁）。
- 整体抬升 `GROUND_LIFT = 0.12`：geo 最低点 -0.9945 单位 × 1.6 / 16 ≈ -0.0995 格，抬升后底边落在方块顶面；多出的 2 cm 是给模型里那两层**零厚度地面贴片**（LiedInner 约 6 mm、LiedOuter 约 2 mm）留深度余量——它们若与草地共面，远处会 z-fighting 出闪斑。
- **地面门面（末地传送门样式）**：`textures/entity/inkshadow_portal_surface.png`（64×64，纯黑底 + 紫色星盘 + 圆形 alpha 边缘）贴在模型内框上方 `SURFACE_Y = 0.02` 的水平四边形，半径 `SURFACE_RADIUS = 0.62`（不超出门框），绕 Y 轴 `SURFACE_SPIN_TICKS = 200` 慢转；用 `entityTranslucentEmissive` + `LightTexture.FULL_BRIGHT` 自发光。
- **只在成对激活时渲染门面**：未配对的起始门只显示模型本体（`PENDING_RENDER_SCALE = 0.95` 略缩），符合用户 m02953 第 5 条。
- 渲染层：模型走 `RenderType.entityCutoutNoCull`（**不透明 + 写深度、不剔除背面**）。首版用 `entityTranslucent`，门环是一堆零厚度薄片，半透明不写深度只能靠排序，看起来像「鬼影」而且随视角逐帧变；改成 cutout 后进不透明通道，观感立刻变实心。
- 渲染顺序：模型已写深度，门面（`SURFACE_Y = 0.03`，高于模型所有薄片）走正常深度测试即可；**已删除**旧版 `endBatch() + disableDepthTest + depthMask(false)` 的状态 hack（在 Iris 延迟管线里会打乱通道顺序）。
- **眼睛闪烁的根因与修法（第三轮）**：黄色眼睛只出现在 `InkShadow` 骨上；该骨的三块薄片里，中间大平板在 z=-6.6，两片侧翼在 z=-6.6153，只差 0.015 单位（1.6 缩放下约 0.15 mm）⇒ 与中间平板几乎共面，深度值逐帧翻转，眼睛就在「亮/灭」之间跳。修法：把两片侧翼后移到 z=-6.75（约 1.5 cm），眼睛所在的中间平板永远在前，闪烁消失。**注意**：曾试过给整个模型开背面剔除（`entityCutout`）——眼睛会直接消失（单侧 UV 被剔），该方向不可取。

## §3 落点清单（P0 两线同构）

| 类别 | 文件（相对各线 `src/main/`） |
|---|---|
| 物品 | `java/.../item/sign/InkShadowSignItem.java` |
| 逻辑 | `java/.../item/sign/InkShadowSignManager.java` |
| 实体 | `java/.../entity/InkShadowPortalEntity.java` |
| 渲染 | `java/.../client/InkShadowPortalModel.java`、`client/InkShadowPortalRenderer.java` |
| 事件 | `java/.../event/InkShadowSignEvents.java` |
| 效果（状态指示器） | `java/.../effect/InkShadowStackEffect.java` + `effect/ModEffects.java` 注册 `inkshadow_stack`（图标 = `textures/mob_effect/inkshadow_stack.png`，即立牌图标） |
| 注册 | `ModItems`、`ModCreativeTabs`、`ModEntityTypes`、客户端渲染注册（`ModClientEvents` / forge 对应入口）、`component/ModAttachments` |
| 资产 | `assets/astral_dice/geo/inkshadoweffect.geo.json`、`animations/inkshadoweffect.animation.json`、`textures/entity/inkshadoweffect.png`（64×64）+ `inkshadoweffect.png.mcmeta`、`textures/entity/inkshadow_portal_surface.png`（地面门面）、`textures/mob_effect/inkshadow_stack.png`、`textures/item/inkshadow_sign.png`（物品图标取自用户素材 `images/小猎手立牌.png`；模型顶点源自 `models/inkshadoweffect.bbmodel`，经 `tools/bbmodel_to_geo.py` 转换） |
| 数据（**datagen 产出**，落各线 `src/generated/resources/`） | `assets/astral_dice/models/item/inkshadow_sign.json`、`data/astral_dice/recipe(s)/inkshadow_sign.json`（**占位配方**）、`data/astral_dice/advancement(s)/recipes/misc/inkshadow_sign.json` |
| 数据（手写，落各线 `src/main/resources/`） | `data/astral_dice/tags/item(s)/signs.json`、`data/curios/tags/item(s)/stand.json`、Patchouli `entries/signs/inkshadow_sign.json` |
| datagen 登记 | `datagen/ModItemModelProvider`（`basicItem(ModItems.INKSHADOW_SIGN.get())`）+ `datagen/ModRecipeProvider`（`BXB/KCK/XDX` 占位配方，`unlockedBy has_blank_chip`） |
| 文本 | `lang/{zh_cn,en_us,ja_jp}.json` 各 11 + 本轮新增键（见 §4）；另有 `entity.astral_dice.inkshadow_portal`（末影传送门）、`effect.astral_dice.inkshadow_stack` + `.description`。**2026-11-10 中文 `_active` / `_passive` / `.description` 由用户亲自改写，以用户版本为准**，英日按同义重译（被动表述同步为「存在自身放置的传送门期间」） |
| 环境脚本 | `run/start-{1.21.1,1.20.1,26.1.2}.sh` 增补 `DISPLAY` / `XAUTHORITY` 兜底（后台 job 里两者常为空，见 §8） |

## §4 文本键（zh_cn / en_us / ja_jp 三份）

`item.astral_dice.inkshadow_sign`、`tooltip.astral_dice.sign.inkshadow.active`、`tooltip.astral_dice.sign.inkshadow_active`（**单键多行**：值内用 `\n` 分隔，子项以两个空格开头，由 `addSignLines` 拆行；第 5 段为本轮新增：站定 1.5 s、每门 5 s 静默、放置者需先离开 4 格）、`tooltip.astral_dice.sign.inkshadow.passive`、`tooltip.astral_dice.sign.inkshadow_passive`；手册 `astral_dice.guide.entry.inkshadow_sign.{1,2}`；实体名 `entity.astral_dice.inkshadow_portal`（zh「末影传送门」/ en「Ink Shadow Portal」/ ja「インクシャドウポータル」）；被动效果 `effect.astral_dice.inkshadow_stack` + `.description`。

tooltip 挂点：`event/ModTooltipHandler` 的 `ModItems.INKSHADOW_SIGN` 块（照 `AL_SIGN` 块：`addSignKeyHint` → 主动标题/正文 → 被动标题/正文 → `addSignCooldownRemaining`）。

⚠️ **文本键必须是单键多行，不能写成 `.1`/`.2` 编号键**（2026-11-10 实机发现的缺陷）：`addSignLines(tooltip, key)` 只读 `translationString(key)` 后按 `\n` 拆行，**不会**遍历 `key.1`、`key.2`；写成编号键时 lang 里的文本完全不会被读到，tooltip 会原样渲染 `§7tooltip.astral_dice.sign.inkshadow_active` 这样的键名。本批已把 1.21.1 的 `_active.1..5` / `_passive.1..2` 合并回单键（三语同改）。同理由 `addSignNoteLines` / `addCardLines` 处理的键也一样。

## §5 边界与已知取舍

- **显示效果仍待用户逐项验收**（模型缩放 1.6、地面门面半径 0.62 / 转速 200 tick / 自发光、未配对门 0.95 缩放）：全部集中常量，验收后单独一轮调整。当前门面是**贴地的末地传送门样式星盘**（黑底 + 紫星 + 自转），旧版竖立 billboard 漩涡已删除。
- **冷却走全局口径**（2026-11-10 用户裁决）：不再写死 2400，继承 `BaseSignItem` 的全局链 = `SIGN_ACTIVE_COOLDOWN_SECONDS`（默认 180 s）→ 充能封顶 180→160 s → 额外电池 ×0.5。冷却从锁定结束时由 `SignActiveLockMachine` 起算；**放置窗口超时、放置期死亡/退出、以及放置中卸下立牌都算已消耗，照付全额**。
- **卸下即作废**：`onUnequip` 清 pending + 起始门 discard + 移速移除 + 层数清零 + 撤攻击修饰符。
- **配方占位**：`BXB/KCK/XDX`（墨囊 / 星尘 / 金块 / 空白筹码 / 钻石骰）——与少主配方同形，仅换关键材料；待用户定。
- **跃迁核心映射**：仓内无该物品，接到 `WarpEngineChipItem.onPortalOrWaystoneTeleport`；若用户指的是新物品需另立规格。
- 门不可破坏/不可攻击由「非 `LivingEntity` + 不可拾取 + `isInvulnerableTo` 恒真 + `hurt` 恒 false」实现，不走方块（因此不受领地/破坏事件影响）。
- **传送语义（第二轮）**：门可反复使用，没有一次性限制；同一扇门对同一玩家 5 s 冷却、站定 1.5 s 才传，全程无 actionbar 提示；被动叠层仍是每扇门 × 每人一次性（`visitedUuids`）。门是**双向**的：两扇门都能把人送到对面（Apex 恶灵大招的折跃门机制）。
- **被动状态指示器**：叠层期间给玩家挂 `inkshadow_stack` 增益效果（amplifier = 层数 − 1，图标即立牌图标），HUD 的剩余时间即**最晚存活门对的剩余时间**；效果与攻击修饰符同生命周期（`refreshStackHud` 在层数/门对失效/卸下三处同步）。
- 未做：P1 三线、门与门的视觉配对提示（颜色/编号）、放置音效、多人并发门数量上限（首版不限制）。
- **forge-1.20.1 仍是旧值**：本批（冷却全局化 + 末影传送音效）按要求**只改 1.21.1**，forge 线保留 `ACTIVE_COOLDOWN_TICKS = 2400` 与无音效版本，待验收通过后同步。

## §6 跨线覆盖

| 线 | 状态 |
|---|---|
| `neoforge-1.21.1` | 首版已落地（2026-11-10）+ 第二轮验收改版已实机验证 + **第三轮（被动改按存活门对生效）已实机验证**（本节 §7） |
| `forge-1.20.1` | 首版已落地（2026-11-10）；**第二、三轮改动均尚未同步**（冷却仍写死 2400、无末影音效、门仍 +0.25、旧的一次性穿越逻辑、被动仍是 2:00 计时），待 1.21.1 验收通过后同步 |
| `fabric-1.20.1` / `fabric-1.21.1` / `neoforge-26.1.2` | **未做**（红线：P1 不自动跟进，等用户裁决） |

## §7 实机取证

### §7.1 1.21.1 第二轮（2026-11-10 20:31–20:57，AutoJS `eval_js` 服务端探针 + 窗口截图）

| 项 | 读数 |
|---|---|
| 启动 | `launch.sh --version 1.21.1 --quickplay testworld` 不带任何环境变量 ⇒ `LAUNCH: READY` + `WORLD_READY`（log `scripts/test/linux/logs/1.21.1-20261110-205203.log`，game_pid 117991 / wrapper 117816） |
| 物品 | `astral_dice:inkshadow_sign` 注册、`item.astral_dice.inkshadow_sign`、显示名「小猎手立牌」、`maxStackSize=1` |
| 实体名 | `getDisplayName()` = 「末影传送门」（`entity.astral_dice.inkshadow_portal`） |
| 起始门 | `setPos(y=pos.getY())` 后 y=-60（贴地，严格等于方块顶面）；`isActive()=false`；未配对时**不画门面** |
| 终点门 | `tryPlaceEndFromKey(p)` ⇒ `true`，两门 `isActive()=true`、`State=1`、`Expire` 相同 |
| 成对存活 | 两门 `Expire` 一致（同一 `now + 3600`）；20:56:35 建的门在 20:59:50 复查已 `count=0`（≈3 分钟整消失） |
| 主人保险 | 放置者站自己门里 26 s 不传送 |
| 站定传送 | 离开 5 格再回门内站 1.5 s ⇒ (46.5,-60,48.5) → (40.5,-60,42.5) |
| 5 s 冷却 | 到达后站 2.5 s 仍在原地（冷却中）；5 s 后再站 1.5 s ⇒ 反向传回 (46.5,-60,48.5)（双向成立） |
| 跨维度 | 下界 (0.5,129,0.5) 站定 → 主世界 (60.5,-60,62.5) |
| 被动叠层 | `INKSHADOW_STACK` amplifier=1、duration=2236；`ATTACK_DAMAGE` base 1 → value 5（2 层 × 2.0 `ADD_VALUE`） |
| 敌对伤害 | husk（20 hp，NoAI）在门旁 2.5 格内：hp 18 → 16 → 14（t=4138 / 4165 / 4193），约 2 点 / 1.3 s |
| 视听 | 穿越播 `ENDERMAN_TELEPORT` + 40 粒 `PARTICLE_PORTAL`（起点与落点各一次） |
| 编译 | `./gradlew --no-daemon --offline :neoforge-1.21.1:compileJava` ⇒ BUILD SUCCESSFUL |
| 截图 | 双门低角度 / 俯视 / overlay 版 / 未配对单门（sha256 见交付报告；`temp/` 下未入库） |

### §7.1c 提示口径 / 动画时长 / 站定范围（2026-11-10 22:1x–22:2x，AutoJS 服务端探针 + 窗口截图）

| 项 | 读数 |
|---|---|
| 起始门提示 | 触发后 actionbar **只有一条**「暗影之门：起始之门已放置，30 秒内选择终点之门的位置」，基类通用文案「小猎手立牌:主动技能已启动!」已被 `setHandled()` 压掉；`pending=true`（截图 `temp/shots/actionbar/start-single.png`） |
| 终点门提示 | `tryPlaceEndFromKey` ⇒ `true`、`pending=false`，actionbar 只有一条「暗影之门：终点之门已放置，配对存在 3:00」（截图 `temp/shots/actionbar/end-single.png`） |
| 动画时长 | `GeckoLibCache.getBakedAnimations()` 的 key 是资源路径（`astral_dice:animations/inkshadoweffect.animation.json`）；`getAnimation('animation.inkshadoweffect.idle').length()` = **80 tick**（4.0 s，改前半速 40） |
| 站定范围 | 门对 `(316.5,-60,300.5)` / `(352.5,-60,300.5)`：站到距中心 **1.3 格**（旧判定会命中的位置）保持 3 s ⇒ 原地不动（读回 `x=317.80`）；站到中心 ⇒ 传送生效（随后读到配对门位置 `x=352.50`） |
| 踩门粒子 | 站在门盘中心约 0.35 s 后截图可见门面上一片 `PORTAL` 粒子（`sendParticles(ParticleTypes.PORTAL, 6, 0.32, 0.05, 0.32, 0.02)` 每 2 tick） |
| 编译 | `:neoforge-1.21.1:compileJava` ⇒ BUILD SUCCESSFUL；Java 改动必须 `stop.sh` + `launch.sh` 重启才生效 |

**未验证 / 留给用户**：门面尺寸与转速是否合眼、模型缩放 1.6 是否合适、3:00 到期的实际消失时刻（本轮只核了 `Expire` 同值）、死亡/退出作废在第二轮代码下未重跑（首版曾验）、forge 线未同步。

### §7.1b tooltip 文本修复复核（2026-11-10 21:56，AutoJS 服务端探针）

编号键 → 单键多行合并后，用 `stack.getTooltipLines(TC.of(p.serverLevel()), p, TooltipFlag.NORMAL)` 读到的完整行序：

```
小猎手立牌 / 栏位: 立牌 / (空) / 按下 J 触发 / (空)
主动技能（暗影之门）：
  按技能键在脚下放置起始之门，需在 30 秒内在其他位置放置终点之门
  放置期间：移动速度 +10%
  - 无法攻击、无法切换武器、无法与方块实体交互
  两门成型后成对存在 3:00，任意玩家进入一端后从另一端离开，可跨维度
  终点未放置时起始之门无效（半透明且不可穿越）
  - 放置窗口内死亡、登出或被击杀：起始之门消失且本次主动失效
  站入门内 1.5 秒后传送；同一扇门对同一玩家每 5 秒最多一次（无提示）
  - 放置者需先离开门 4 格，之后才会被自己的门传送
被动技能（双生之影）：
  每有一名新玩家穿过任意一端：自身攻击力 +2，持续 2:00，可叠加（每名玩家仅计一次，含自身）
  每扇门对每名玩家只计一次；敌对目标靠近传送门时受到 2 点魔法伤害
Astral Dice
```

语言热更判据：客户端 `Java.loadClass('net.minecraft.client.Minecraft').getInstance().reloadResourcePacks()` 后，`net.minecraft.locale.Language.getInstance().getOrDefault('tooltip.astral_dice.sign.inkshadow_active')` 返回合并正文、`…_active.1` 返回键名本身（旧键已删）。

### §7.1d 被动改按存活门对生效 + 用户文本落盘（2026-11-10 22:39–22:45，AutoJS 服务端/客户端探针）

| 检查 | 读数 |
|---|---|
| 建立一对门 | `M.placePortal` 两次 ⇒ `ok1=true ok2=true`；`INKSHADOW_PAIR_EXPIRIES=264442`，`livePairDeadline=264442` |
| 叠层与加成 | `addStack` ×2 ⇒ `stacks=2`、`ATTACK_DAMAGE=5`（base 1 + 2×2.0）、HUD `INKSHADOW_STACK amp=1 dur=3600`（= 门对剩余时间） |
| 越过旧 2:00 计时（放置后 153 s） | `stacks=2`、`atk=5`、`deadline` 不变、HUD `dur=540` ⇒ **层数没有按 2:00 消失** |
| 门对到期后（放置后 226 s） | `stacks=0`、`atk=1`、`expiries=`（空）、`deadline=0`、HUD `none` ⇒ 无存活门对即失效并清零 |
| 文本热更 | `Minecraft.getInstance().reloadResourcePacks()` 后客户端 `Language.getOrDefault` 读到用户手改的中文；`getTooltipLines` 输出 14 行，主动 3 段 + 被动 2 段，无编号键残留、无「2:00」字样 |

> 复跑提示：改 lang 后既要 `cp` 到 `neoforge-1.21.1/build/resources/main/assets/astral_dice/lang/`，也要客户端侧 `reloadResourcePacks()`；服务端 `Language` 是启动时加载的，读 tooltip 请走**客户端**探针（`Item$TooltipContext.of(mc.level)` + `TooltipFlag.NORMAL`）。

### §7.2 首版（2026-11-10 白天，历史）

P0 双线：物品注册 / 起始门 `+0.25` 悬浮版 / 一次性 `visitedUuids` 穿越 / 2400 冷却（forge 仍是此版）/ 1.21.1 tooltip 全行（含「金鳞」式状态行由 viewer 决定的那类历史结论不适用于本牌）。

## §8 复跑清单

1. 编译：`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --no-daemon :neoforge-1.21.1:compileJava :forge-1.20.1:compileJava --console=plain`
2. datagen：`:neoforge-1.21.1:runData` / `:forge-1.20.1:runData`（任务名实读自各线 `build.gradle` 的 `data { data() }`）。**跑前必须把 `run/1.21.1/mods` 与 `run/1.20.1/mods` 里的 `astral_dice-*.jar` 移出**（否则 runData 的 classpath 会带上旧 jar，`Found valid mod file` 与 `build/classes` 同名 mod 冲突，datagen 跑的是旧代码，新增 provider 全部不生效）；跑完放回。
   - 2026-11-10 实证：park 前跑两遍都「成功但无 inkshadow 产物」，park 后一次到位。
   - **`runData` 收尾会卡住**：provider 与 `HashCache: Caching: … written: N` 行出现即产物已落盘，之后 JVM 不死（Collective 更新检查线程），`BUILD SUCCESSFUL` 可能永不到达 ⇒ 判据用 `HashCache` 行 + `git status <线>/src/generated`，然后 `pkill -f dataRunProgramArgs`。（`pkill -f` 的 pattern 不要写进会匹配到自己 shell 的字符串，否则会把自己打死。）
   - 本批实测漂移面：P0 两线 25 个既有 JSON 纯格式漂移（键序 / 去尾换行）= datagen 真值，接受；`sykes_sign` 的 advancement 在 P0 两线首次生成（此前手写缺）。
3. 门禁：`python3 tools/verify_sign_display_names.py`（`GATE: PASS`）、`python3 -B docs/agent/verify-split.py`、`python3 tools/gen_agents_toc.py --check`。
4. 实机：`bash scripts/test/linux/launch.sh --version 1.21.1 --quickplay testworld --timeout 900 --wait-world 300`，AutoJS `eval_js` 取证后 `stop.sh`，再换 `1.20.1`。
   - **跑前必须 park `run/<线>/mods/astral_dice-*.jar`**（`mkdir -p temp/live-parked && mv run/1.21.1/mods/astral_dice-*.jar temp/live-parked/`）：runClient 会把 mods 里的同名 jar 与 `-Dfml.modFolders` 的 `build/classes` 一起当 mod 载入，**实机跑的是 jar 里的旧代码**（log 里 `Considering mod file candidate … Found valid mod file astral_dice-…jar`）。跑完 `mv` 回。2026-11-10 实证：不 park 时源码已改 `setPos(pos.getY())`，实机门仍在 -59.75。
   - 从后台 job 启动时 `DISPLAY` / `XAUTHORITY` 常为空 ⇒ 客户端会带着 `Authorization required, but no authorization protocol specified` 卡 6 分钟后失败；`run/start-*.sh` 已加两段兜底（X11 socket 存在则 `DISPLAY=:0`，`/run/user/$(id -u)/xauth_*` 取第一个）。
   - clash 内核未起时（`ss -ltnp | grep 7897` 为空）全链路加 `--offline`。
