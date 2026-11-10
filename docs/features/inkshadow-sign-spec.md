# 小猎手立牌（inkshadow）规格与实现记录

> 本文是**语义冻结 + 实现记录**：P0 两线（`neoforge-1.21.1` / `forge-1.20.1`）2026-11-10 首版落地（用户 m02258 下达，明确「先做第一版供验收，显示效果由我自己定」）。
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

## §1 标识与常量（首版冻结值，全部可在代码内一处调整）

| 项 | 值 | 说明 |
|---|---|---|
| 注册 id | `astral_dice:inkshadow_sign` | `ModItems` 注册，`stacksTo(1)`，稀有度 `ASTRAL_DICE_EPIC`（紫色/史诗） |
| 放置窗口 | `WINDOW_TICKS = 600`（30 s） | 起始门放下后必须在窗口内放下终点门 |
| 成对存活 | `PAIR_LIFETIME_TICKS = 6000`（5:00） | 到期两扇门一起消失，无动画 |
| 放置距离 | `PLACE_RANGE = 8.0D` | 准星射线取落点 |
| 放置期移速 | `MOVEMENT_SPEED` +10%，`ADD_MULTIPLIED_TOTAL` | 放置期专属，终点门放下即移除 |
| 主动冷却 | `ACTIVE_COOLDOWN_TICKS = 2400`（2:00） | **待用户验收调整**；锁定结束由 `SignActiveLockMachine` 起冷却 |
| 被动叠层 | 每层攻击力 `+2`，时长 `STACK_DURATION_TICKS = 2400`（2:00） | 每次穿越刷新时长，层数不设上限 |
| 敌对伤害 | `PORTAL_HAZARD_RADIUS = 2.5` 格内每 20 tick 受 `2.0` 点 `SKILL_DAMAGE` | 用户口径「魔法伤害」= 本仓 `SKILL_DAMAGE`（与魔渊触须同款） |
| 防弹跳 | `RETRIGGER_TICKS = 60` | 同玩家对同一扇门 3 s 内不重复触发 |
| 显示常量 | `MODEL_SCALE` / `MODEL_Y_OFFSET` / `PENDING_ALPHA` / `VORTEX_RADIUS` / `VORTEX_PERIOD_TICKS` / `VORTEX_ALPHA` | 集中声明并注释「待用户验收调整」 |

状态载体（均为玩家级附件，primitive、仅服务端）：`INKSHADOW_PENDING_UUID`、`INKSHADOW_PENDING_DIM`、`INKSHADOW_PENDING_DEADLINE`、`INKSHADOW_LOCKED_SLOT`、`INKSHADOW_STACKS`、`INKSHADOW_STACKS_EXPIRE`。
冷却复用立牌统一口径（`SIGN_ACTIVE_COOLDOWN_END` / `SIGN_ACTIVE_MAX_COOLDOWN`），不新建。

## §2 语义

### §2.1 主动「暗影之门」

1. 佩戴者按 J（`BaseSignItem.performSkillForCurio` 链路）→ `InkShadowSignItem.handleUse`。
2. **无 pending**：准星 8 格内取落点，生成**起始门**（state=PENDING，半透明、不可穿越），写 pending（实体 UUID + 维度 + 截止刻 = now + 600），进入**锁定态**（`beginActiveLock`），并施加 +10% 移速。
3. **有 pending 且在窗口内**：第二次按键**不经过 `performSkillForCurio`** —— 由网络入口 `SignActivatePayload.handle`（neo21）/ `ModNetwork.SignActivateMessage` 前置钩子（forge）先问 `InkShadowSignManager.tryPlaceEndFromKey(player)`，命中即 `return`。钩子内落地**终点门**，两扇门同时转 ACTIVE（双向可穿越），`expireTick = now + 6000`，清 pending、移除移速、锁定随 `isGateEffectActive` 变 false 而结束并起 `ACTIVE_COOLDOWN_TICKS` 冷却。
   - 这样设计的理由：一次主动技能只结算一次。若第二次按键仍走 `performSkill`，会二次触发风扇筹码发牌 / 电流核心 / 技能音效 / `SignActiveTriggeredEvent`。
   - 终点门落点非法（准星没指到可放置方块面）⇒ 红字 `msg.astral_dice.inkshadow_no_ground`，**保留窗口可重试**，不消耗冷却。
4. 放置期限制（服务端强制）：无法攻击（`AttackEntityEvent` 取消）、不能切换武器（每 tick 复位锁定槽位）、不能与方块实体交互（`PlayerInteractEvent` 五个子事件取消）；ESC 与立牌技能键不受影响。
5. **作废路径**：窗口超时、死亡（`LivingDeathEvent`）、退出（`PlayerLoggedOutEvent`）⇒ 起始门 discard、pending 清空、移速移除、槽位解锁；终点门不存在 ⇒ 无残留。
6. 起始门在终点门放下前**无效**：PENDING 态不参与穿越扫描，也不触发被动。

### §2.2 被动「双生之影」

- **穿越计数按「每扇门 × 每人」**：每扇门各自维护 `visitedUuids`；同一玩家穿过起始门与终点门各计一次（用户备注明确）。
- 触发：门主人（`ownerUuid`）获得 +1 层，攻击力修饰符 = `2.0 × 层数`（固定 UUID、`ADD_VALUE`），时长刷新到 now + 2400；层过期清零并移除修饰符。
- 门主人离线、未佩戴本牌、或已不满足 `isEquipped` ⇒ 仍计穿越但不加层（不报错）。
- **【跃迁核心】联动**：每次成功穿越额外调用本仓最接近的实存物 `WarpEngineChipItem.onPortalOrWaystoneTeleport(player)`（其自身 3600 tick 冷却与充能奖励原样保留）。仓内**没有**名为「跃迁核心」的物品（只有 `warp_engine_chip` 跃迁引擎 / 电流核心 / 原初核心）⇒ 该映射待用户确认。
- 敌对伤害：ACTIVE 门每 20 tick 对 `PORTAL_HAZARD_RADIUS` 内的敌对生物造 2 点 `SKILL_DAMAGE`。

### §2.3 门实体（`InkShadowPortalEntity`）

- `extends Entity implements GeoEntity`；`noPhysics`、无重力、不可拾取、不可被攻击（`isInvulnerableTo` 恒 true、`hurt` 恒 false）、无碰撞箱 ⇒ 不能破坏、不能被攻击、不挡路。
- 穿越：ACTIVE 门每 5 tick 扫 `inflate(0.6)` 内玩家；命中 ⇒ 传送到配对门（**跨维度**用 `teleportTo(ServerLevel, x, y, z, yaw, pitch)`），标记该门 `visitedUuids`、写 `RETRIGGER_TICKS` 防弹跳、结算被动、调跃迁引擎钩子。
- 到期：`expireTick` 到 ⇒ 两扇门一起 discard。

### §2.4 渲染（首版占位，用户验收后定）

- 门体：`inkshadoweffect.geo.json` + `textures/entity/inkshadoweffect.png`，GeckoLib idle 动画 `animation.inkshadoweffect.idle`（2.0 s 循环，四骨 Y 轴自转，来自 bbmodel 的 48 帧 Idle）。
- **激活门中间单独一层黑色漩涡**：`textures/entity/inkshadow_vortex.png`（64×64 黑色螺旋 alpha），独立旋转 quad，`VORTEX_PERIOD_TICKS` 转一圈；门模型本身不含漩涡（用户备注）。
- PENDING 态：不画漩涡 + 整体半透明（`PENDING_ALPHA`）。

## §3 落点清单（P0 两线同构）

| 类别 | 文件（相对各线 `src/main/`） |
|---|---|
| 物品 | `java/.../item/sign/InkShadowSignItem.java` |
| 逻辑 | `java/.../item/sign/InkShadowSignManager.java` |
| 实体 | `java/.../entity/InkShadowPortalEntity.java` |
| 渲染 | `java/.../client/InkShadowPortalModel.java`、`client/InkShadowPortalRenderer.java` |
| 事件 | `java/.../event/InkShadowSignEvents.java` |
| 注册 | `ModItems`、`ModCreativeTabs`、`ModEntityTypes`、客户端渲染注册（`ModClientEvents` / forge 对应入口）、`component/ModAttachments` |
| 资产 | `assets/astral_dice/geo/inkshadoweffect.geo.json`、`animations/inkshadoweffect.animation.json`、`textures/entity/inkshadoweffect.png`、`textures/entity/inkshadow_vortex.png`、`textures/item/inkshadow_sign.png`（顶点源自 `models/inkshadoweffect.bbmodel`，经 `tools/bbmodel_to_geo.py` 转换） |
| 数据（**datagen 产出**，落各线 `src/generated/resources/`） | `assets/astral_dice/models/item/inkshadow_sign.json`、`data/astral_dice/recipe(s)/inkshadow_sign.json`（**占位配方**）、`data/astral_dice/advancement(s)/recipes/misc/inkshadow_sign.json` |
| 数据（手写，落各线 `src/main/resources/`） | `data/astral_dice/tags/item(s)/signs.json`、`data/curios/tags/item(s)/stand.json`、Patchouli `entries/signs/inkshadow_sign.json` |
| datagen 登记 | `datagen/ModItemModelProvider`（`basicItem(ModItems.INKSHADOW_SIGN.get())`）+ `datagen/ModRecipeProvider`（`BXB/KCK/XDX` 占位配方，`unlockedBy has_blank_chip`） |
| 文本 | `lang/{zh_cn,en_us,ja_jp}.json` 各 11 键（见 §4） |

## §4 文本键（zh_cn / en_us / ja_jp 三份）

`item.astral_dice.inkshadow_sign`、`tooltip.astral_dice.sign.inkshadow.active`、`tooltip.astral_dice.sign.inkshadow_active.{1..4}`、`tooltip.astral_dice.sign.inkshadow.passive`、`tooltip.astral_dice.sign.inkshadow_passive.{1,2}`；手册 `astral_dice.guide.entry.inkshadow_sign.{1,2}`。

tooltip 挂点：`event/ModTooltipHandler` 的 `ModItems.INKSHADOW_SIGN` 块（照 `AL_SIGN` 块：`addSignKeyHint` → 主动标题/正文 → 被动标题/正文 → `addSignCooldownRemaining`）。

## §5 边界与已知取舍

- **显示效果是占位**（模型缩放、漩涡尺寸/转速/透明度、起始门半透明程度）：全部集中常量，用户验收后单独一轮调整。
- **冷却 2:00 是占位值**：用户未给数值，按「同类主动技能常规量级」取 2400 tick。冷却从锁定结束时由 `SignActiveLockMachine` 起算；**放置窗口超时、放置期死亡/退出、以及放置中卸下立牌都算已消耗，照付全额 2400**（首版口径，用户可改）。
- **卸下即作废**：`onUnequip` 清 pending + 起始门 discard + 移速移除 + 层数清零 + 撤攻击修饰符。
- **配方占位**：`BXB/KCK/XDX`（墨囊 / 星尘 / 金块 / 空白筹码 / 钻石骰）——与少主配方同形，仅换关键材料；待用户定。
- **跃迁核心映射**：仓内无该物品，接到 `WarpEngineChipItem.onPortalOrWaystoneTeleport`；若用户指的是新物品需另立规格。
- 门不可破坏/不可攻击由「非 `LivingEntity` + 不可拾取 + `isInvulnerableTo` 恒真 + `hurt` 恒 false」实现，不走方块（因此不受领地/破坏事件影响）。
- 未做：P1 三线、门与门的视觉配对提示（颜色/编号）、音效、穿越粒子、多人并发门数量上限（首版不限制）。

## §6 跨线覆盖

| 线 | 状态 |
|---|---|
| `neoforge-1.21.1` | 首版已落地（2026-11-10） |
| `forge-1.20.1` | 首版已落地（2026-11-10） |
| `fabric-1.20.1` / `fabric-1.21.1` / `neoforge-26.1.2` | **未做**（红线：P1 不自动跟进，等用户裁决） |

## §7 实机取证

（待回填：P0 双线 启动就绪 / 物品注册 / tooltip 全行 / 放置起始门 / 终点门 / 穿越 + 叠层 / 5 分钟到期 / 死亡作废 / 敌对伤害 / 截图）

## §8 复跑清单

1. 编译：`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --no-daemon :neoforge-1.21.1:compileJava :forge-1.20.1:compileJava --console=plain`
2. datagen：`:neoforge-1.21.1:runData` / `:forge-1.20.1:runData`（任务名实读自各线 `build.gradle` 的 `data { data() }`）。**跑前必须把 `run/1.21.1/mods` 与 `run/1.20.1/mods` 里的 `astral_dice-*.jar` 移出**（否则 runData 的 classpath 会带上旧 jar，`Found valid mod file` 与 `build/classes` 同名 mod 冲突，datagen 跑的是旧代码，新增 provider 全部不生效）；跑完放回。
   - 2026-11-10 实证：park 前跑两遍都「成功但无 inkshadow 产物」，park 后一次到位。
   - **`runData` 收尾会卡住**：provider 与 `HashCache: Caching: … written: N` 行出现即产物已落盘，之后 JVM 不死（Collective 更新检查线程），`BUILD SUCCESSFUL` 可能永不到达 ⇒ 判据用 `HashCache` 行 + `git status <线>/src/generated`，然后 `pkill -f dataRunProgramArgs`。（`pkill -f` 的 pattern 不要写进会匹配到自己 shell 的字符串，否则会把自己打死。）
   - 本批实测漂移面：P0 两线 25 个既有 JSON 纯格式漂移（键序 / 去尾换行）= datagen 真值，接受；`sykes_sign` 的 advancement 在 P0 两线首次生成（此前手写缺）。
3. 门禁：`python3 tools/verify_sign_display_names.py`（`GATE: PASS`）、`python3 -B docs/agent/verify-split.py`、`python3 tools/gen_agents_toc.py --check`。
4. 实机：`bash scripts/test/linux/launch.sh --version 1.21.1 --quickplay testworld --timeout 900 --wait-world 300`，AutoJS `eval_js` 取证后 `stop.sh`，再换 `1.20.1`。
