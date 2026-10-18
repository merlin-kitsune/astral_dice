# 强化拒止效果牌（2.0.0-SNAPSHOT.132）

> 需求（2026-10-18 用户原文，逐字引用）：
> 「创建效果牌：强化拒止（模型："强化拒止 - Converted.bbmodel"，品质：史诗，专属效果牌（属于怪力乱神立牌，该立牌会在后续添加））
> - 功能（描述文本）：在玩家指定地面上放置一个强化拒止，强化拒止会自动吸附8格范围内的敌对目标，并对其持续施加缓慢和虚弱效果。强化拒止每10秒消耗吸附目标数量的耐久度（单次最大消耗5点）
> - 功能补充：强化拒止是一个实体，没有碰撞箱。使用该效果牌时遵循原版放置方块规则将强化止实体放置在指定位置，强化拒止拥有100点耐久，会以1秒为频率将半径8格范围敌对目标向强化拒止的位置逐渐缓慢拉扯（但目标无法逃离强化拒止影响范围），强化拒止不会吸引仇恨，但会持续为怪物施加 缓慢 (0:10)和虚弱 (0:10)，强化拒止耐久耗尽会有碎裂粒子效果，主动攻击并不会对齐造成伤害。」
>
> 本批范围：**P0 两线 neoforge-1.21.1 + forge-1.20.1**（逐项对等；两线代码剥注释后 0 hunk，仅平台 API 差异）。neoforge-26.1.2 / fabric-1.20.1 属 P1，不在本批。

## 1. 冻结设计 → 落点（逐条对应）

| 冻结项（Lead 裁决） | 实现位置 |
|---|---|
| id：物品 `effect_card_repulsion` / 实体 `astral_dice:repulsion` / 类 `RepulsionEntity` / `RepulsionCardItem` / `cardTypeId() = "repulsion"` | `item/ModItems`、`entity/ModEntityTypes`、`entity/RepulsionEntity`、`item/card/RepulsionCardItem` |
| 品质 `AstralRarities.epic()` | `ModItems.EFFECT_CARD_REPULSION` 的 `.rarity(...)` |
| 专属牌：`isExclusive() = true` / `registerExclusiveCard` / `is_exclusive` 标签 / `ExclusiveCardUtil.isExclusive` / tooltip 追加 `exclusive_owner`（DARK_PURPLE） | `RepulsionCardItem#isExclusive`、`RandomCardHandler` 静态块、`data/astral_dice/tags/item/is_exclusive.json`、`ExclusiveCardUtil#isExclusive`、`ModTooltipHandler` |
| 实体：`LivingEntity`、`MAX_HEALTH = 100`、`noPhysics`、`setNoGravity`、`isPushable=false`、`push` 空实现、`hurt` 恒 false、无碰撞方块、不调用 `setTarget` | `RepulsionEntity`（构造器 / `hurt` / `isPushable` / `push`）+ `event/ModEntityEvents` |
| 放置：与路障同款落点判据（clip 4.5 格 → 命中块 + 面方向；`canBeReplaced` + 下方 `isFaceSturdy(UP)` + `isWithinBounds` + `mayInteract`），**不放置方块**；实体生成在落点方块中心 `x+0.5, y, z+0.5`；放不下 ⇒ `fail` + 动作栏红字、**不消耗卡** | `RepulsionCardItem#use` / `#resolvePlacement` / `#applyEffect` |
| 吸附：服务端 `Set<UUID>` 持久化、剪枝（死亡 / 移除 / > 32 格 / **失去吸附资格**）、每 tick 钳制（3D 距离 > 8 格 ⇒ 按 3D 方向拉回 7.5 格并清零速度；落点先过 `level.noCollision`＝方块 / 实体碰撞与边界三空，不可用则依次上抬 y 0.5/1.0/1.5，仍失败回退「只钳水平」）与**每秒一次**的向心拉力（水平归一化 × 0.22，保留 y，中心 0.35 格死区）、每 20 tick 扫描**有吸附资格**的目标（`isAttachable`：敌对 / 中立 ＋ 有仇恨目标或被激怒 ＋ 非已驯服宠物）并施加 缓慢/虚弱 (0:10)、每 tick 剪枝对集合内目标追加同一条 `isAttachable` 判定（**被激怒而吸附的中立生物在平静后、以及失去仇恨目标的敌对生物，会在下一个 tick 被释放（不再拉扯 / 扣耐久 / 施加效果）**）、每 tick 压制被吸附苦力怕的引信（`swellDir > 0 ⇒ -1`）、每 200 tick 扣耐久 `min(8 格内目标数, 5)`、耐久 <= 0 ⇒ 碎裂粒子 + `GLASS_BREAK` + `discard()` | `RepulsionEntity#tickTrackedTargets` / `#clampInside` / `#findFreeLanding` / `#pullTrackedTargets` / `#applyPull` / `#scanAndApplyEffects` / `#isAttachable` / `#suppressTrackedCreeperFuses` / `#consumeDurability` / `#shatter` |
| 苦力怕：被吸附的苦力怕**无法自爆**（每 tick 引信压制 ＋ `ExplosionEvent.Start` 取消），见 §3 与 §9 | `RepulsionEntity#suppressTrackedCreeperFuses` / `#isTracking` / 新增 `event/RepulsionCreeperGuard` |
| 渲染：`RepulsionModel` + `RepulsionRenderer`（`sized(1.6F, 2.4F)`）、贴图 `textures/entity/repulsion.png`、图标 `textures/item/effect_card_repulsion.png`（`images/强化拒止.png` 逐字节复制） | `client/RepulsionModel`、`client/RepulsionRenderer`、`entity/ModEntityTypes`、资源文件 |
| 接线：`ModEntityTypes` / `AstralDiceMod` / `ModEntityEvents` / `ModClientEvents` / `ModItems` / `ModCreativeTabs` / `RandomCardHandler` / `BaseEffectCardItem.cardByTypeId` / `ModTooltipHandler` / `ModItemModelProvider` / 三语 lang / 帕秋莉条目 | 见 §6 文件清单（`AstralDiceMod` 无需改动：`ModEntityTypes.ENTITY_TYPES` 早已统一注册） |
| 文档：本文件 + 两份 CHANGELOG 未发布小节各加一条 | `CHANGELOG_ZH.md` / `CHANGELOG.md` |

### 1.1 与冻结设计稿的 3 处偏差（均已取证，属「按目的落地」而非改需求）

1. **cube B 的 `texOffs` 取 `(0,3)`，不是冻结稿的 `(0,4)`**。
   冻结稿写「北面 2,5..14,17 → 用 `texOffs(0,4)`（u+d=2、**v+1=5** 反推）」，用的是旧版（1.12 及更早）
   `v+1` 的盒子 UV 公式；1.21.1 的实际布局是 `north = (u+d, v+d)`（d = 深度 2，不是 1）——
   见 `ModelPart.Cube` 构造器源码：`f5 = texCoordU + dimensionZ`、`f11 = texCoordV + dimensionZ`，
   北面取 `(f5, f11, f6, f12)`。代入 w=12/h=12/d=2：
   - `texOffs(0,3)` ⇒ 北面 `(2,5)..(14,17)` ✓ **与作者面 UV 逐位一致**（也等于 bbmodel 自带的 `uv_offset (0,3)`）；
   - `texOffs(0,4)` ⇒ 北面 `(2,6)..(14,18)` ✗ 整体下移 1 像素。
   冻结稿自身给出的目的就是「使盒子 UV 与作者一致」，故取 (0,3)。
2. **实体贴图是 128×128 解码后按 UV 画布 2:1 降采样为 64×64**。
   冻结稿写「（64×64）」，但 bbmodel 的 `textures[0].source` 实际是 **128×128** PNG，而该贴图的
   `uv_width/uv_height` = **64**（Blockbench 把 128 图像铺在 64 的 UV 画布上）。Minecraft 的盒子 UV
   按「模型像素 = 贴图像素」取样 ⇒ 贴图必须是 64×64，否则 UV 只会采样到 128 图的左上角四分之一。
   实现：最近邻降采样（`PIL.Image.resize((64,64), NEAREST)`），使 64 网格与像素一一对应；原图保留在
   `models/强化拒止 - Converted.bbmodel` 内。
3. **落点判据多一条「同格占用」**（§2 第 6 条）。
   冻结稿要求「不放置任何方块」，于是路障用来挡同格重复放置的「落点方块不可替换」在这里失效
   ⇒ 同一格可以无限叠放实体。补一条只比 `blockPosition()` 的判据（**不**用 1.6 宽 AABB ——
   那会溢出到四邻 / 斜邻格，路障 2026-10-18 审查已踩过这个坑）。

## 2. 放置规则（= 原版「放在实心面」，与路障同一份判据）

客户端与服务端执行**同一份**预检（`RepulsionCardItem#resolvePlacement`）：

1. 视线射线：`level.clip(new ClipContext(eye, eye + look × 4.5, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))`（4.5 格 = 原版方块交互距离）；
2. 必须命中方块（`HitResult.Type.BLOCK`），落点 = `hit.getBlockPos().relative(hit.getDirection())`；
3. 落点在世界边界内且 `level.mayInteract(player, target)`（= 出生点保护 + 世界边界，覆写该方法的第三方领地插件同样生效）；
4. 落点方块 `canBeReplaced()`；
5. 落点**下方**方块 `isFaceSturdy(level, below, Direction.UP)`；
6. 落点格**没有**存活的 `RepulsionEntity`（`getEntitiesOfClass(RepulsionEntity.class, <落点格 AABB>)` 里 `blockPosition().equals(target)` 者存在 ⇒ 判为放不下）。

不满足 ⇒ `InteractionResultHolder.fail` + 动作栏红字 `msg.astral_dice.effect_card_repulsion_invalid`，**不消耗卡牌**（不进入基类 `use`）。
满足 ⇒ `super.use(...)`（基类负责专属校验 / 出牌锁 / 出牌数 / 冷却 / 消耗），在 `applyEffect`（服务端）里再算一次落点并生成实体。

> 基类 `BaseEffectCardItem#use` 的顺序是「客户端预检 → 服务端 tryUseCard（专属 → 出牌锁 → applyEffect → 出牌音效 → 出牌登记 → 各钩子）→ `stack.consume(1, player)`」，因此把落点预检放在 `super.use` **之前**，「放不下」这条路径在**同一调用栈内实际不可达**（预检失败直接 `fail` 返回，根本不进基类）。
>
> ⚠️ 但这**不**等于「结构上不可能消耗」：基类在 `applyEffect` 之后**无条件** `consume`，所以 `addFreshEntity` 失败（实体数上限 / chunk 拒绝）等**极端早退路径**仍会消耗一张牌 —— 如实登记，不加补偿逻辑。

生成：`moveTo(target.x + 0.5, target.y, target.z + 0.5, yaw, 0)`，`yaw = round(player.yRot / 90) × 90`（**观感选择**，冻结稿未规定；本模型是一块竖直薄片，朝向决定宽面朝哪边）。随后 `addFreshEntity`（没有方块需要回滚）、`ExclusiveCardUtil.bindIfAbsent`（首次成功放置绑定获得者）、`SoundEvents.GLASS_PLACE` + 动作栏绿字。

## 3. 实体：`entity/RepulsionEntity`

| 项 | 实现 |
|---|---|
| 基类 | `extends LivingEntity`（**不**用 Mob AI），只为拿到生命值同步通道 |
| 属性 | `MAX_HEALTH = 100`，由 `event/ModEntityEvents` 的 `EntityAttributeCreationEvent#put` 注册 |
| 尺寸 | `sized(1.6F, 2.4F)`、`clientTrackingRange(10)`、`MobCategory.MISC` |
| 物理 | `noPhysics = true`、`setNoGravity(true)`、`isPushable() = false`、`push(...)` 空实现（爆炸 / 活塞 / 水流都推不动） |
| 碰撞 | **没有任何碰撞方块**；实体本身仍有 1.6×2.4 的拾取 / 攻击判定框（`getBoundingBox()`），但 `hurt` 恒 false ⇒ 攻击对它无效；`noPhysics = true` ⇒ 不参与方块碰撞、不提供可站立的碰撞面 |
| 免疫 | `hurt(...)` **恒返回 false**：主动攻击 / 环境伤害 / `/kill` 都不造成伤害；唯一消亡路径是耐久耗尽（`shatter()`） |
| 不吸引仇恨 | 全程**不调用任何 `setTarget`**，也不清除目标原有仇恨（与路障的「主动改写仇恨」语义相反） |
| 持久化 | 被吸附目标 `Set<UUID>` 存字段；NBT `long[]`（每目标 most/least 两个 long），`readAdditionalSaveData` 恢复。⚠️ 落盘的只是 UUID 集合：`ServerLevel#getEntity` 在区块未加载 / 跨维度时返回 null ⇒ **不保证跨卸载保留**（卸载再加载后要等下一次 8 格扫描才会重新吸附，期间不施加效果、也不计耐久） |
| 剪枝 | 每 tick：目标不是 `Mob` / 死亡 / `isRemoved` / `distanceToSqr > 32²` / **失去吸附资格**（`isAttachable` 为假，判据见「吸附 + 效果」行）⇒ 从集合移除（跨维度 / 区块未加载时 `ServerLevel#getEntity` 返回 null ⇒ 同样移除）。**平静即释放**：被激怒而吸附的中立生物在平静后、以及失去仇恨目标的敌对生物，会在**下一个 tick** 被释放（不再拉扯 / 扣耐久 / 施加效果；已有的缓慢 / 虚弱按原时长自然结束，不主动清除）。判定顺序固定为「alive / removed → > 32 格 → `isAttachable` → 越界钳制」 |
| 钳制 | 每 tick：`distanceToSqr > 8²` ⇒ 沿**归一化 3D 向量**拉回 **7.5 格**处（`teleportTo`）并 `setDeltaMovement(Vec3.ZERO)`；落点先过 `level.noCollision(mob, bbox.move(位移))`（`CollisionGetter` 默认实现：方块碰撞 + 其他实体碰撞 + 世界边界**三者都空**才通过），不可用则把 y 上抬 0.5 / 1.0 / 1.5 重试，仍失败回退「只钳水平到 7.5、y 保持」。钳制后 **3D** 距离 = 7.5 ≤ 8 ⇒ 不会每 tick 反复瞬移 |
| 拉力 | **每 20 tick（1 秒）**：对 `distanceToSqr <= 8²` 且**水平距离 ≥ 0.35 格**的被吸附目标施加一次 `setDeltaMovement(水平朝本实体归一化 × 0.22, 原 y, ...)`。⚠️ 冲量是**覆盖**写法（每跳抹掉目标当时的水平动量）；`moveRelative` 把输入**相加**、地面 `travel` 每 tick 乘约 0.546、`Mob#aiStep` 不重置 delta ⇒ 约 0.2–0.5 格/秒的缓慢位移（机制详见 `RepulsionEntity` 类 javadoc〈拉力机制〉） |
| 吸附 + 效果 | 每 20 tick：`getEntitiesOfClass(Mob.class, bbox.inflate(8))` 里**有吸附资格**（`isAttachable`：先排除 `TamableAnimal#isTame()` 的已驯服宠物 ⇒ 再要求 `Enemy` 或 `NeutralMob` ⇒ 再要求 `getTarget() != null` 或中立生物 `NeutralMob#isAngry()`）、存活、`distanceToSqr <= 64` ⇒ 加入集合并施加 `MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0, false, true, true)` 与 `MobEffects.WEAKNESS` 同参（0:10、I 级）。⚠️ 集合成员每 tick 由剪枝复核同一条 `isAttachable`（失去资格 ⇒ 下一 tick 释放，见「剪枝」行） |
| 苦力怕（引信压制） | **每 tick**：被吸附集合内的 `Creeper`，`getSwellDir() > 0` ⇒ `setSwellDir(-1)`（`suppressTrackedCreeperFuses`）。⚠️ 只压得住**自然膨胀**：`isIgnited()`（打火石 / 火焰点燃）会让原版 `Creeper#tick` 在读值前**每 tick 强制** `setSwellDir(1)` ⇒ 必须由下一行的爆炸取消兜底 |
| 苦力怕（爆炸取消） | 新增 `event/RepulsionCreeperGuard`（**游戏总线** `ExplosionEvent.Start`）：爆炸的**直接 / 间接来源实体**是 `Creeper`，且其 16 格内存在 `RepulsionEntity` 正在追踪它（`isTracking(creeper.getUUID())`）⇒ `event.setCanceled(true)` + `creeper.setSwellDir(-1)` + 爆炸点播 `SoundEvents.FIRE_EXTINGUISH`。取消点在 `Level#explode` 的 `explosion.explode()` 之前（neo `Level.java:791` / forge `Level.java:577`）⇒ 不掉方块、不造成伤害、无爆炸粒子 / 音效；但原版 `explodeCreeper()` 的 `dead = true` / `discard()` 仍会执行 ⇒ **苦力怕无声消失**（既定代价）。非被吸附苦力怕 / TNT / 其它爆炸一律放行 |
| 耐久 | 每 200 tick：`durability -= min(当前 8 格内被吸附目标数, 5)`；目标数为 0 时不扣 |
| 碎裂 | 耐久 <= 0 ⇒ `CRIT` 32 个 + `POOF` 8 个 + `SoundEvents.GLASS_BREAK`（`SoundSource.BLOCKS`）⇒ `setHealth(0)` + `discard()` |
| 掉落 | `shouldDropLoot() = false`、`shouldDropExperience() = false` |

> **精英 / Boss 不排除**：冻结稿只写「8 格范围内的敌对目标」，未要求排除精英 / Boss；本牌不拉仇恨，不会把 Boss 的仇恨从玩家身上抢走，故按字面实现（与路障的「非精英非 Boss」口径**不同**，不要互相参照）。

## 4. 模型与贴图（来源：`models/强化拒止 - Converted.bbmodel`）

bbmodel 原文（64×64 分辨率、box_uv、modded_entity）：

| cube | bbmodel `from`→`to` | `origin` | `rotation` | 面 UV | 模型实现 |
|---|---|---|---|---|---|
| A（blade） | `(1,0,-0.5)`→`(25,2,0.5)` | `(1,0,-0.5)` | `(0,0,90)` | 北面 `1,1..25,3` | `PartPose.offsetAndRotation(1, 24, -0.5, 0, 0, −90°)` + cube `(0,-2,0, 24,2,1)`，`texOffs(0,0)` |
| B（head） | `(0,22.5,-1)`→`(12,34.5,1)` | `(0,22.5,-1)` | `(0,0,45)` | `uv_offset (0,3)`、北面 `2,5..14,17` | `PartPose.offsetAndRotation(0, 1.5, -1, 0, 0, −45°)` + cube `(0,-12,0, 12,12,2)`，`texOffs(0,3)` |

- **枢轴 y**：`y_mc = 24 − origin.y`（地面 = 24 像素）⇒ A：24；B：24 − 22.5 = **1.5**。
- **cube 本地 y 取负**（bbmodel `0..2` / `0..12` ⇒ `-2..0` / `-12..0`）。
- **Z 旋转取反**：设 `M = diag(1,-1)`（Y 翻转），有 `M·R(θ) = R(−θ)·M` ⇒ bbmodel 的 +90° / +45° 写成 **−90° / −45°**。
  自检：blade 角点 `(24,−2)` 经 `R(−90°)` ⇒ `(−2,−24)`，枢轴 y=24 ⇒ 该角落在 **y=0（模型最高处）**，即刀刃从地面向上立起；符号写反会钻进地面。
- **UV**：`north = (u+d, v+d)`（1.21.1 源码口径）⇒ A 取 `(0,0)`、B 取 `(0,3)`（见 §1.1 第 1 条）。
- **贴图**：`textures[0].source`（128×128）按 UV 画布 64 最近邻降采样 ⇒ `textures/entity/repulsion.png`（64×64）。
- **图标**：`images/强化拒止.png`（32×32）**逐字节复制** ⇒ `textures/item/effect_card_repulsion.png`。

几何高度自检：菱形头绕 Z 旋转 45° 后对角线 = 12√2 ≈ **16.97 像素**，枢轴 22.5 ⇒ 顶端 ≈ 39.5 像素 ≈ **2.47 格**；
实体高度取 2.4 格（略小于模型顶端 2.47 格，属可见取舍）；宽度对角线 ≈ 1.06 格 < 1.6 格。

## 5. 专属牌口径（与既有专属牌一致）

| 通道 | 落点 |
|---|---|
| 物品自身 | `RepulsionCardItem#isExclusive() = true`（基类 `tryUseCard` / `isBlockedOnClient` 据此走 `ExclusiveCardUtil.canUse`） |
| 随机发放强制排除 | `RandomCardHandler` 静态块 `registerExclusiveCard(ModItems.EFFECT_CARD_REPULSION)`；**不入** `effectCards()` 列表 |
| 复制 / 返还绑定获得者 | `ExclusiveCardUtil#isExclusive` 加入本牌（`BaseEffectCardItem#createCopyFor` 由此补绑 owner） |
| 数据标签（商店 / 村民交易排除） | `data/astral_dice/tags/item/is_exclusive.json` 追加 `astral_dice:effect_card_repulsion`（`ModTags.Items.IS_EXCLUSIVE` / `CardTradeFilter` 消费） |
| 无配方 | `datagen/ModRecipeProvider` 未添加任何配方 |
| 不进赏金池 | `data/bountiful/bounty_pools/**/astral_rews.json` **未**添加（实测：既有专属牌 `fu_card` / `huo_card` / `living_page` / `fate_guidance` 均不在 `astral_rews` 里，只有非专属的 `effect_card_barricade` 在 ⇒ 专属牌不进奖励池是既有口径） |
| tooltip | `ModTooltipHandler` 末尾追加 `tooltip.astral_dice.card.exclusive_owner`（`ChatFormatting.DARK_PURPLE`，照活体书页那段） |

## 6. 文件清单

| 文件 | 作用 |
|---|---|
| `entity/RepulsionEntity.java` | 强化拒止实体（吸附 / 钳制 / 拉力 / 效果 / 耐久 / 碎裂 / 免疫 / 不拉仇恨） |
| `entity/ModEntityTypes.java` | 追加 `REPULSION`（`sized(1.6F, 2.4F)`、`clientTrackingRange(10)`） |
| `event/ModEntityEvents.java` | `EntityAttributeCreationEvent` 注册 100 点最大生命 |
| `item/card/RepulsionCardItem.java` | 效果牌（落点预检 + 放置 + 专属绑定） |
| `item/ModItems.java` | 追加 `EFFECT_CARD_REPULSION`（`stacksTo(16)` / `AstralRarities.epic()`） |
| `item/card/BaseEffectCardItem.java` | `cardByTypeId` 追加 `"repulsion"` |
| `item/card/ExclusiveCardUtil.java` | `isExclusive` 追加本牌 |
| `item/card/RandomCardHandler.java` | `registerExclusiveCard` 追加本牌（静态块）+ 类头注释同步 |
| `item/ModTags.java` / `shop/CardTradeFilter.java` | 注释里的专属牌数量 6 → 7 |
| `init/ModCreativeTabs.java` | 创造栏功效牌段追加 |
| `client/RepulsionModel.java` / `client/RepulsionRenderer.java` | 模型 + 渲染器 |
| `client/ModClientEvents.java` | `RegisterRenderers` / `RegisterLayerDefinitions` 追加 |
| `event/ModTooltipHandler.java` | tooltip 段（`tooltip.astral_dice.card.repulsion` + 出牌数 + 冷却 + 专属行） |
| `event/RepulsionCreeperGuard.java` | 苦力怕自爆拦截（游戏总线 `ExplosionEvent.Start`；只对正在被吸附的苦力怕生效，取消爆炸 + 掐引信 + `FIRE_EXTINGUISH`） |
| `datagen/ModItemModelProvider.java` | `basicItem(...)`（产物 `src/generated/resources/assets/astral_dice/models/item/effect_card_repulsion.json`） |
| 资源 | `textures/item/effect_card_repulsion.png`、`textures/entity/repulsion.png`、`data/astral_dice/tags/item/is_exclusive.json`、`.../effect_cards.json`、三语 lang（item / entity / tooltip / guide 1..3 / msg invalid / msg placed）、帕秋莉 `entries/cards_effect/effect_card_repulsion.json`（spotlight + text×2、sortnum 18） |
| 文档 | 本文件、`CHANGELOG_ZH.md` / `CHANGELOG.md` 未发布小节 |

> forge-1.20.1 侧为**同路径镜像**（`RegistryObject`/`ForgeRegistries.ENTITY_TYPES`、两参 `ResourceLocation`、`renderToBuffer` 8 参、`MobEffects` 裸常量、`tags/**items**` 复数）；资源（两张贴图、手册条目、datagen 模型）与 neo **逐字节相同**。

## 7. 判据（静态可验证项）

| 判据 | 命令 |
|---|---|
| 两线编译 | `pwsh -NoProfile -File scripts/test/mt_build.ps1 --version 1.21.1|1.20.1 --timeout 300` ⇒ `MT_BUILD: OK` |
| 模型 / 贴图链闭合 | `pwsh -NoProfile -File scripts/verify/verify_resource_integrity.ps1` ⇒ `RESULT: PASS` |
| 三语键集一致 | `pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir <线>/src/main/resources/assets/astral_dice/lang`（两线各 991 键） |
| 手册无悬空键 | `python tools/audit_patchouli_keys.py` ⇒ 悬空 0 |
| 档位词与 `.rarity()` 一致 | `python tools/verify_rarity_consistency.py` ⇒ PASS |
| datagen 产物 | 两线 `src/generated/resources/assets/astral_dice/models/item/effect_card_repulsion.json` 存在（逐字节相同） |

## 8. 未验证 / 风险（需实机；本轮按纪律不启动游戏）

1. **渲染朝向 / 贴图面序**：模型按 bbmodel 的几何 + Y 翻转 + Z 取反落地，但「−90°/−45° 是否符合美术预期」属视觉问题；
   `LivingEntityRenderer` 的 `scale(-1,-1,1)` 还会让模型在 X 上镜像（本几何 X 方向近似对称，理论上无观感差异）。
2. **拉拽手感**：向心拉力已改为**每秒一次**（`PULL_STRENGTH = 0.22`）；「约 0.2–0.5 格/秒」是按 `travel` 地面阻尼（每 tick ×0.546）**推算**的位移量（`moveRelative` 相加、`aiStep` 不重置 delta），未实机读数；若观感过快 / 过慢只调 `PULL_STRENGTH` 一个量。
3. **钳制是否被 AI 覆盖**：8 格外「拉回 7.5 格」每 tick 一次，已改 3D 并带无碰撞落点校验（不塞墙、也不会每 tick 反复瞬移），但击退 / 寻路 / 载具仍可能在两 tick 之间把它推出 8 格（下一 tick 再次拉回，属预期抖动）。另：`noCollision` 把**其他实体**也算碰撞 ⇒ 目标挤在怪群里时 3D 落点常不可用而走「只钳水平」回退，竖直偏移大的目标在拥挤场景下仍可能被反复水平挪位（有意保守，未实机核对）。
4. **耐久数值**：100 点耐久、每 10 秒最多扣 5 点 ⇒ **5 个目标持续吸附时约 200 秒（100 / 5 × 10s）**；0 目标不扣（`attached <= 0` 直接返回）；吸附目标数只统计「8 格内、仍在集合里」的被吸附目标（敌对 / 中立，且已过 §3 的吸附资格）。
5. **破碎粒子 / 音效**：`CRIT` + `POOF` + `GLASS_BREAK` 的观感与音量未实机核对。
6. **`sized(1.6F, 2.4F)` 与模型顶端 2.47 格的差异**：属可见取舍（模型顶端约 7 像素可能超出实体盒）。
7. **吸附资格未实机核对（2026-10-18 追加 R1）**：未激怒的末影人 / 无目标游荡的僵尸应当**不被**吸附；被激怒的中立（末影人 / 僵尸猪灵 / 猪灵 / 北极熊 / 蜜蜂 / 铁傀儡 / 未驯服狼）应当被吸附；已驯服的狼 / 猫 / 鹦鹉应当**不被**吸附。判据按 `NeutralMob#isAngry()` / `TamableAnimal#isTame()` 源码写死（两线签名已核），未实机。
8. **「已吸附后失去仇恨」→ 平静即释放（已解决，2026-10-18 晚 R5：tick 剪枝追加 `isAttachable`）**：`tickTrackedTargets` 的每 tick 剪枝已追加同一条 `isAttachable` 判定 ⇒ 被激怒而吸附的中立生物一旦平静、以及失去仇恨目标的敌对生物会在**下一个 tick** 被移出集合（不再施加缓慢 / 虚弱、不再拉扯 / 钳制、不再计入耐久消耗；已施加的缓慢 / 虚弱按原时长自然结束，不主动清除）。⚠️ 取舍：释放后目标可自行走开，但这只发生在它**已不再具备吸附资格**之后，与「目标无法逃离影响范围」不冲突 —— 钳制只对仍有资格的目标生效。未实机核对：`getTarget()` 可能被 AI goal 每 tick 重写，「平静」的判定时机（例如末影人刚被打完的短暂窗口）属观感问题。
9. **苦力怕「禁止自爆」的两条路径与副作用（2026-10-18 追加 R2）**：自然膨胀靠每 tick `setSwellDir(-1)`（不改私有 `swell`；两种 tick 顺序下要么回落到 0、要么停在当前值，都到不了 `maxSwell`）；点燃路径靠 `ExplosionEvent.Start` 取消 ⇒ 苦力怕会**无声消失**（原版 `dead = true` 在 explode 之前设置、`discard()` 在之后无条件执行），不掉战利品、不走死亡动画。未实机；若观感不可接受，最小改法是取消爆炸后补播 `CREEPER_DEATH`（不改生命周期 —— 原版没有可取消 `discard()` 的钩子）。
10. **事件层的 16 格只是查询余量**：`RepulsionCreeperGuard` 先按 16 格包围盒找 `RepulsionEntity`，再以 `isTracking(UUID)` 定论 ⇒ 半径**不是**新口径（正常被吸附目标一直被钳在 8 格内）；若将来允许跨 16 格仍被吸附，需同步改 `LINK_RADIUS`。

## 9. 变更记录

- **2026-10-18 晚（按用户追加规则）**：用户原话「吸附怪物增加被激怒的中立，排除没有仇恨目标的敌对（如末影人），增加额外功能，吸附到苦力怕时阻止苦力怕自爆」。落地四项：① **R1 吸附资格**（`RepulsionEntity#isAttachable`：`Enemy` ∪ `NeutralMob`，且 `getTarget() != null` 或中立 `isAngry()`，且非 `TamableAnimal#isTame()`）—— 刻意窄于 `HostileTargets`，缘由与先例见该类 javadoc〈吸附资格〉；② **R2 苦力怕禁止自爆**（每 tick `suppressTrackedCreeperFuses` ＋ 新增 `event/RepulsionCreeperGuard` 取消 `ExplosionEvent.Start`，并新增 `RepulsionEntity#isTracking(UUID)` 作为事件层唯一判据）；③ **R3 文案**（三语 `tooltip.astral_dice.card.repulsion` 与 `guide.entry.effect_card_repulsion.2/.3`，键集不变仍 991 键）；④ **R4 文档**（本文件 §1 / §3 / §6 / §8 与两份 CHANGELOG 的条目末尾追加一句）。P0 两线 neoforge-1.21.1 + forge-1.20.1 同步，代码逐字对等（仅平台 API 差异）。
- **2026-10-18 晚（R5，平静即释放）**：`RepulsionEntity#tickTrackedTargets` 的每 tick 剪枝对**仍存在且存活**的 `Mob` 追加同一条 `isAttachable` 判定，顺序固定为「alive / removed → > 32 格 → `isAttachable` → 越界钳制」⇒ **被激怒而吸附的中立生物一旦平静、以及失去仇恨目标的敌对生物，会在下一个 tick 被释放**（不再施加缓慢 / 虚弱、不再被拉扯 / 钳制、不再计入耐久消耗；已施加的缓慢 / 虚弱按原时长自然结束，不主动清除）。`isAttachable` 本身未改动；类 javadoc 的「已知口径缺口」条目改写为「平静即释放」，§8 第 8 条同步改为已解决。P0 两线 neoforge-1.21.1 + forge-1.20.1 同步，代码逐字对等（仅平台 API 差异）。
- **2026-10-18 晚（R6，tooltip 文案精简）**：用户原话「移除强化拒止tooltip中的括号注释内容；将怪力乱神立牌和强化拒止tooltip中分号改为换行；路障和强化拒止耐久度描述向下移（放在描述最后，在出牌数上面）」。落地（三语 × P0 两线共 6 个 lang 文件、键集不变）：① `tooltip.astral_dice.card.repulsion` 改为 5 行（用法 / 吸附 + 缓慢·虚弱 / 苦力怕不能自爆 / 不吸引仇恨·免疫伤害 / **耐久 + 每 10 秒消耗规则（末行）**），四处括号注释整段移除（`（遵循原版放置规则，放不下不消耗）`、`（无法逃离；…玩家宠物除外）`、`（单次最多 §e5§7 点）`、`（引信被压制、爆炸被取消）`），原分号一律改 `\n` 断行；② `tooltip.astral_dice.card.barricade` 改为 3 行并把耐久行移到最后；③ `tooltip.astral_dice.sign.lingling_*` 的分号改换行、`_note` 删去「充能上限为 §e20§7 层」（充能体系全局规则，见 §4.6，不在本牌 tooltip 重复）。判据：`tools/check_lang_sync.ps1` OK（1001 键 × 两线）、`scripts/audit/tooltip_color_audit.ps1` 64 处违规（与改动前基线同数，无一条命中这三组键）。
