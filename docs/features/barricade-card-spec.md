# 路障效果牌（2.0.0-SNAPSHOT.130）

> 需求（2026-10-17 用户原文，逐字引用）：
> 「创建新效果牌：路障（品质：稀有，取同名图标）：- 功能（描述文本）：在玩家指定地面上放置一个路障，路障会吸引6格范围内非精英和Boss敌对生物的仇恨  - 功能补充：路障使用「路障 - Converted.bbmodel」创建实体，使用该效果牌时遵循原版放置方块规则将路障实体放置在指定位置，路障本身30点耐久，被怪物攻击会降低一点，会像栅栏一样阻止玩家和生物越过。路障覆盖范围为半径6格（球形），靠近的怪物仇恨都会转移到路障并主动攻击路障。」
>
> 本批范围：**P0 两线 neoforge-1.21.1 + forge-1.20.1**（逐项对等）；neoforge-26.1.2 / fabric-1.20.1 属 P1 待移植（按 AGENTS 分级，不在本批）。

## 1. 实现形态（Lead 冻结裁决）

**混合体**：本仓此前没有任何「实体 / 方块放置」先例，唯一稳的路径是把两件事拆开：

| 组成 | 类 | 注册 id | 作用 |
|---|---|---|---|
| 可见实体 | `entity/BarricadeEntity` | `astral_dice:barricade` | 渲染模型、承受攻击、拉仇恨、维护耐久 |
| 隐形碰撞方块 | `block/BarricadeBlock` | `astral_dice:barricade_collision`（**无 BlockItem**） | 只提供「像栅栏一样挡路」的碰撞与寻路阻断 |
| 效果牌 | `item/card/BarricadeCardItem` | `astral_dice:effect_card_barricade` | 落点预检 + 通过基类出牌流程后放置 |

实体与方块**成对存在**：牌放下时同时生成实体与方块；实体死亡 / 移除（KILLED / DISCARDED）时删掉方块；方块被外力清掉时实体自毁。

> ⚠️ **如实披露**：`hurt` **全覆写** ⇒ 绕过平台伤害事件（NeoForge `LivingIncomingDamageEvent` / `LivingDamageEvent`；Forge `LivingAttackEvent` / `LivingHurtEvent` / `LivingDamageEvent`），也绕过盾牌 / 圣盾格挡、无敌帧与 `lastHurtByPlayer` 归因。这是「每次固定 -1、无视伤害数值」的代价：**第三方伤害修正不作用于路障**。

## 2. 放置规则（= 原版「放在实心面」）

客户端与服务端执行**同一份**预检（`BarricadeCardItem#resolvePlacement`）：

1. 视线射线：`level.clip(new ClipContext(eyePos, eyePos + look × 4.5, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))`（**4.5 格 = 原版方块交互距离**）；
2. 必须命中方块（`HitResult.Type.BLOCK`），落点 = `hit.getBlockPos().relative(hit.getDirection())`；
3. 落点在世界边界内（`level.getWorldBorder().isWithinBounds(target)`）且在可交互范围内（`level.mayInteract(player, target)`；1.20.1/1.21.1 的 `ServerLevel#mayInteract` 实际只含出生点保护）；
4. 落点方块 `canBeReplaced()`（空气 / 水 / 草等可替换）；
5. 落点**下方**方块 `isFaceSturdy(level, below, Direction.UP)`（= 有实心面可依附，原版放置规则的等价判据）；
6. 落点方块不是 `barricade_collision`（同格不重复）。

> **相邻格可放**：不再用实体 AABB 做过重叠判据（旧判据会让 1.2 宽实体溢出到四邻/斜邻格、导致只能隔 1 格放，连不成墙）。同格重复由「落点方块不可替换」天然挡住。

不满足 ⇒ `InteractionResultHolder.fail` + 动作栏红字 `msg.astral_dice.effect_card_barricade_invalid`，**不消耗卡牌**（不进入基类 `use`）。
满足 ⇒ 调 `super.use(...)`（基类负责出牌锁 / 出牌数 / 冷却 / 消耗），在 `applyEffect`（服务端）里再算一次落点并放置。

> 基类 `BaseEffectCardItem#use` 的顺序是「客户端预检 → 服务端 tryUseCard(专属→出牌锁→applyEffect→音效→出牌登记→各钩子) → `stack.consume(1, player)`」，
> 因此把落点预检放在 `super.use` **之前**即可保证「放不下就绝不消耗」。

## 3. 实体：`entity/BarricadeEntity`

| 项 | 实现 |
|---|---|
| 基类 | `extends LivingEntity`（**不**用 Mob AI） |
| 属性 | `MAX_HEALTH = 30`，由 `event/ModEntityEvents` 的 `EntityAttributeCreationEvent#put` 注册（`LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 30.0)`） |
| 物理 | `noPhysics = true`、`setNoGravity(true)`、`isPushable() = false`；`push(...)` 空实现 + 每 tick 把位置钉回碰撞方块中心（爆炸/活塞/位移效果都无法把它推离方块；实机：苦力怕爆炸曾把它从 y=96 推到 y=105.75） |
| 尺寸 / 视点 | 实体类型 `sized(1.2F, 2.0F)`：视点 = 0.85×2.0 = **1.7 > 碰撞方块顶 1.5**。2026-10-18 实机取证：实体本体站在 1.5 格高的碰撞方块内时，怪物 `hasLineOfSight` 的射线终点落在方块内部被挡住 ⇒ `MeleeAttackGoal` 永不触发（卫道士走到 0.8 格、`reach=true` 却 12 秒 0 次 `hurt`）；把视点抬到方块顶以上后 LOS 恢复、卫道士每秒命中一次（30→16，−14/12s）。 |
| 受伤 | 覆写 `hurt(DamageSource, float)`（仅服务端）：仅接受 `source.getEntity() instanceof LivingEntity`（怪物 / 玩家 / 其发射物都算）；**无视伤害值、每次固定 -1**（`setHealth(getHealth() - 1)`；不写 `invulnerableTime`、不依赖无敌帧），扣到 0 调 `die` |
| 受击反馈 | `markHurt()` + `level().broadcastDamageEvent(this, source)`（客户端 `handleDamageEvent` 置 `hurtTime = hurtDuration = 10` 并播受击音；服务端不再重复 `playSound`） |
| 仇恨守门 | `event/BarricadeAggroHandler`（游戏总线 `LivingChangeTargetEvent`）：怪物想从路障改投别人时，只要旧目标仍是 6 格内存活路障且自己是敌对非精英，就把「即将设置的新目标」改回路障；初次拉仇恨仍归 `pullAggro` |
| 音效 | `getHurtSound` → `SoundEvents.WOOD_HIT`；`getDeathSound` → `SoundEvents.WOOD_BREAK` |
| 掉落 | `shouldDropLoot() = false`、`shouldDropExperience() = false`（不掉物品、不给经验） |
| 拉仇恨 | 服务端每 4 tick：`getEntitiesOfClass(Mob.class, getBoundingBox().inflate(6))`，再按**球形**（`distanceToSqr <= 36`）过滤 `instanceof Enemy`、`!EliteTargets.isEliteOrBoss(mob)`、`isAlive()`，命中即 `mob.setTarget(this)`；改投由 `BarricadeAggroHandler` 兜底 |
| 死亡 / 移除清理 | 清空半径 8 内以本实体为目标的怪（`mob.setTarget(null)`）、删除碰撞方块、播 `WOOD_BREAK` + 少量 `ParticleTypes.CRIT`；只在 `KILLED` / `DISCARDED` 时执行（`UNLOADED_TO_CHUNK` 等**不**删方块） |
| 方块校验 | 每 100 tick 校验碰撞方块仍在，被 `/setblock` 等外力移除则自毁；NBT 存方块坐标（`BarricadeBlock` long），加载后首 tick 立即校验一次，方块不在则自毁 |

## 4. 碰撞方块：`block/BarricadeBlock`

- 碰撞箱 `Shapes.box(0, 0, 0, 1, 1.5, 1)`（栅栏高度 ⇒ 跳不过去）；
- `getRenderShape` = `RenderShape.INVISIBLE`；`noOcclusion()`、`isViewBlocking→false`、`isSuffocating→false`、`isRedstoneConductor→false`、`noLootTable()`、`strength(-1.0F, 3600000.0F)`（不可破坏）、`sound(SoundType.WOOD)`；
- **寻路层放行 + 物理层挡路**：`isPathfindable()` 返回 **true**、`getBlockPathType()` 返回 **WALKABLE**（neo `PathType.WALKABLE` / forge `BlockPathTypes.WALKABLE`）。2026-10-18 实机取证：旧口径 `isPathfindable=false` 会让怪物的 `PathNavigation` 规划不到「路障所占格」⇒ 只站远处盯目标、不靠近也不攻击；放行后卫道士会走到 0.8 格贴身处，被 1.5 格碰撞箱挡住（**依旧越不过去**）并在近战距离攻击路障。
- **投射物转发**：`onProjectileHit` 把命中本方块的箭/弹转发给路障实体（`hurt(damageSources().thrown(projectile, owner), 1)`，无主投射物不转发）。实机：掠夺者的弩弹会被 1.5 格碰撞箱先挡住、到不了实体 ⇒ 在方块层接住并同样扣 1 点。
- 资产：`blockstates/barricade_collision.json` → `astral_dice:block/barricade_collision`，模型 `{"textures":{"particle":"minecraft:block/oak_planks"},"elements":[]}`（避免 Missing model 日志）。

> ⚠️ **视觉高 ≈0.83 格 vs 碰撞 1.5 格**：需求优先「像栅栏一样挡路」（栅栏 = 1.5），而 bbmodel 实物只有 14 像素高（倾斜后 ≈0.83 格）⇒ 板顶以上约 0.67 格是隐形墙。这是**有意取舍**（保挡路、不拉伸美术）；若要改成「碰撞 1.0 + 可跳过」或「缩放模型填满 1.5」，属玩法决策。

## 5. 文件清单

| 文件 | 作用 |
|---|---|
| `entity/BarricadeEntity.java` | 路障实体（耐久 / 拉仇恨 / 清理 / 方块校验） |
| `entity/ModEntityTypes.java` | 实体类型注册（neo `DeferredRegister<EntityType<?>>` / forge 同构），`sized(1.2F, 2.0F)`、`clientTrackingRange(8)` |
| `event/ModEntityEvents.java` | `EntityAttributeCreationEvent` 注册 30 点最大生命 |
| `block/BarricadeBlock.java` | 隐形碰撞方块 |
| `block/ModBlocks.java` | 追加 `BARRICADE_COLLISION` 注册 |
| `item/card/BarricadeCardItem.java` | 效果牌（预检 + 放置） |
| `item/ModItems.java` | 追加 `EFFECT_CARD_BARRICADE`（稀有 / 16 堆叠） |
| `item/card/BaseEffectCardItem.java` | `cardByTypeId` 追加 `"barricade"`（复制 / 返还路径可找回本体） |
| `item/card/RandomCardHandler.java` | `effectCards()` 入池 |
| `init/ModCreativeTabs.java` | 创造栏功效牌段追加 |
| `client/BarricadeModel.java` / `client/BarricadeRenderer.java` | 模型 + 渲染器 |
| `client/ModClientEvents.java` | `RegisterRenderers` / `RegisterLayerDefinitions` |
| `event/ModTooltipHandler.java` | 追加物品 tooltip 段（`tooltip.astral_dice.card.barricade` + 出牌数 / 效果冷却行） |
| `datagen/ModItemModelProvider.java` | `basicItem(...)`（产物见 `src/generated/resources/.../effect_card_barricade.json`） |
| `AstralDiceMod.java` | `.register(modEventBus)` 追加实体类型注册器 |
| 资源 | 图标 `textures/item/effect_card_barricade.png`（`images/路障.png` 逐字节复制）、实体贴图 `textures/entity/barricade.png`（bbmodel `textures[0].source` 解码）、方块资产、`data/astral_dice/tags/item/effect_cards.json`、`data/bountiful/bounty_pools/bountiful/astral_rews.json`、三语 lang、帕秋莉手册条目 |

## 6. 模型几何（来源：`models/路障 - Converted.bbmodel`）

两块 20×14×1 的板，贴图 64×64。bbmodel 为 **+Y 向上**，Minecraft 实体模型为 **+Y 向下且 24 像素 = 地面**，换算：

| 板 | bbmodel `from`→`to` | bbmodel 枢轴 / 绕 X 旋转 | 模型实现 |
|---|---|---|---|
| A | `(-10.01, 0, 5)`→`(9.99, 14, 6)` | `(-12.01, 0, 5)` / bbmodel −22.5° | `PartPose.offsetAndRotation(0, 24, 5, +22.5°, 0, 0)` + cube `(-10.01, -14, 0, 20, 14, 1)`，`texOffs(0, 0)` |
| B | `(-10, 0.4, -6)`→`(10, 14.4, -5)` | `(-12, 0.4, -6)` / bbmodel +22.5° | `PartPose.offsetAndRotation(0, 23.6, -6, −22.5°, 0, 0)` + cube `(-10, -14, 0, 20, 14, 1)`，`texOffs(0, 15)` |

- 枢轴 y 由 `24 − bbY` 换算（A：24−0 = 24；B：24−0.4 = 23.6），故 A 的 pose offset y 是 `24`、B 是 `23.6`
  （Lead 冻结稿里写的 `(0,0,5)` / `(0,0.4,-6)` 是 bbmodel 的原始枢轴值；实体模型的地面在 y=24，不加这个基准会把整块板吊在半空）。
- **旋转符号**：Y 轴被翻转（bbmodel +Y 向上 ⇒ 模型 +Y 向下）时，绕 X 的角度要**取反**，否则两块板的倾角与 bbmodel 里看到的相反。
  bbmodel 里 A 是 −22.5°、B 是 +22.5°，且两板**上沿互相靠拢**（成「Λ」形）；落到模型里即 A `+22.5°`、B `−22.5°`。
  （Lead 冻结稿按 bbmodel 原始数值写的 `A −22.5° / B +22.5°`，直接照抄会得到反过来的「V」形 —— 见 §9 第 1 条。）
- UV：bbmodel 的 UV 布局与 `texOffs(u,v)` 的盒子 UV 展开一致（A 用 `(0,0)`、B 用 `(0,15)` 即覆盖 0..42 × 0..15 / 15..30 两个区域）。

## 7. 数值与文案

| 项 | 取值 |
|---|---|
| 品质 | 稀有（`AstralRarities.rare()`），赏金池 `"rarity": "RARE"` |
| 堆叠 | 16 |
| 耐久 | 30（每次被生物命中 −1） |
| 仇恨半径 | 6 格（球形，`distanceToSqr <= 36`） |
| 放置距离 | 4.5 格（= 原版方块交互距离） |
| 清理半径 | 8 格（死亡时解除仇恨） |
| 动作栏 | `msg.astral_dice.effect_card_barricade_invalid`（红，仅「需要可依附的实心面」）/ `..._placed`（绿） |
| 手册 | `astral_dice.guide.entry.effect_card_barricade.1..3`（章内 sortnum 17，章末） |
| 物品 tooltip | `tooltip.astral_dice.card.barricade`（三语；三行：用法 / 挡路 + 拉仇恨 / **耐久（末行）**，行内数值着 `§e`；与既有效果牌同款 `addCardLines` + 出牌数/冷却行 ⇒ 耐久行紧贴在「出牌数」之上。2026-10-18 用户裁决：耐久描述从第 2 行移到描述末行） |

## 8. 判据（静态可验证项）

| 判据 | 命令 |
|---|---|
| 两线编译 | `pwsh -NoProfile -File scripts/test/mt_build.ps1 --version 1.21.1|1.20.1 --timeout 300` ⇒ `MT_BUILD: OK` |
| 模型 / 贴图不缺 | `pwsh -NoProfile -File scripts/verify/verify_resource_integrity.ps1` ⇒ PASS（两线各 149/149） |
| 三语键集一致 | `pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir <线>/src/main/resources/assets/astral_dice/lang`（两线各 1001/1001/1001） |
| 手册无悬空键 | `python tools/audit_patchouli_keys.py` |
| 档位词与 `.rarity()` 一致 | `python tools/verify_rarity_consistency.py` |
| 赏金池 | `pwsh -NoProfile -File scripts/verify/verify_bountiful_pools.ps1`（P0 两线条目一致 + 价值平衡 PASS；26.1.2 因 P1 未移植出现 `effect_card_barricade` 缺失红项，属预期） |
| datagen 产物 | 两线 `src/generated/resources/assets/astral_dice/models/item/effect_card_barricade.json` |

## 9. 独立审查与修订记录（2026-10-18）

只读子代理独立审查（两线逐文件 + sources jar 取证）判定需求 R1a–R1g 与两线对等全部成立，并提出 4 项必修 + 2 项披露要求；本批已全部落地：

| 审查发现 | 修订 |
|---|---|
| spec 陈旧（.129 / 只做 neo） | 本文件改为 .130 + P0 两线 |
| 视觉 0.83 格 vs 碰撞 1.5 格 | 保留栅栏级挡路（1.5），在 §4 显式登记取舍 |
| 相邻/斜邻格无法放第二座（实体 AABB 溢出） | 删除实体 AABB 重叠判据（§2 第 6 条） |
| `addFreshEntity` 返回值未检查 | `applyEffect` 落判 + 回滚方块 |
| `invulnerableTime=0` 为死代码、无受击红闪 | 删死代码，改 `broadcastDamageEvent`（客户端红闪 + 受击音） |
| 每 4 tick 轮询存在改投空窗 | 新增 `BarricadeAggroHandler`（`LivingChangeTargetEvent` 守改投） |
| 「遵循原版规则」缺 `mayInteract`/世界边界；reach 5.0 | 补两条判据 + reach 改 4.5 |
| forge 三语 guide 键缩进 | 已补 2 空格 |
| 绕过平台伤害事件未披露 | 已在 §1 显式披露 |

## 10. 未验证 / 风险（需实机，按规则须用户明确下达）

1. **实体渲染朝向 / 两块板的夹角方向**：模型已按 bbmodel 的**几何**落地（含 §6 的 Y 翻转与旋转取反），但「取反后的倾角是否符合美术预期」属视觉问题，静态无法判定。
   若实机看到两块板朝外张成「V」形（而非向内成「Λ」），把 A/B 的 \`xRot\` 同时取反即可；若整块板吊在半空 / 陷进地面，先查 \`PartPose\` 的 y 基准（地面 = 24）。
2. **挡路手感**：碰撞方块高 1.5 格、寻路层放行/物理层挡路；**实机已验证**怪会走到 0.8 格贴身处（见 §11），「跳不过」仍属手感项（建议玩家视角复测一次）。
3. **仇恨稳定性**：每 4 tick 重设 `setTarget` + `BarricadeAggroHandler` 守改投；**实机已验证**卫道士在 12 秒内持续锁定并攻击（14 次命中，§11）。
4. **怪物攻击能否命中实体**：实体 AABB（1.2×1.0）与碰撞方块同格，怪站在邻格时应够得着（近战 reach ≈ 2 格），但需实机确认。
5. `/kill` 等**无实体来源**的伤害被 `hurt` 设计性忽略（只有生物来源才扣耐久），故管理员清场需用 `/setblock` 清碰撞方块（方块被清后实体 100 tick 内自毁）。

## 11. 实机验证记录（2026-10-18，用户明确下达「走实机测试，需要验证生物ai会不会主动攻击路障」）

> 环境：`neoforge-1.21.1` dev 客户端（测试世界超平坦，平台 y=95），`MT_ALLOW_MOB_AI=1` 且**临时移除** `astral_test_noai.js` 硬闸门（测试后已恢复）。靶子 = **卫道士（非亡灵）**。

| 项 | 结果 |
|---|---|
| 真实卡牌 use 放置 | `InteractionResultHolder` 成功、卡片 1（创造模式不扣，原版语义）、路障 hp=30/30 |
| 6 格内锁定 | 卫道士 `target=astral_dice:barricade`，从 4.00 格走到 **0.80 格**（`reach=true`） |
| 主动攻击 | 12 秒内 **14 次** `BARRICADE_HURT msg=mob entity=Vindicator`，耐久 **30 → 16**（每次固定 −1） |
| 6 格外对照 | 12.5 格外的卫道士不锁定路障、路障 hp 保持 30 |
| 精英对照 | `maxHealth=50` 的卫道士（命中 `EliteTargets` 的「血量>40」口径）`target=none`、路障 hp 保持 30 |

### 11.1 实机暴露并已修复的 2 个缺陷

1. **旧 `isPathfindable=false` ⇒ 怪物不靠近**：`PathNavigation` 规划不到「路障所占格」，卫道士只站 4 格外盯目标（12 秒距离恒为 4.00）。修复 = 寻路层放行（`isPathfindable=true` + `getBlockPathType=WALKABLE`），物理仍被 1.5 格碰撞箱挡住 ⇒ 走到 0.8 格贴脸攻击。
2. **旧实体视点 0.85 落在 1.5 格碰撞方块内 ⇒ 怪物 LOS 被挡、永不攻击**：修复前 12 秒 **0 次** `hurt`（`reach=true` 也不打）。修复 = 实体类型 `sized(1.2F, 2.0F)`（视点 1.7 > 方块顶 1.5），LOS 从方块顶掠过；代价是命中箱比模型（0.83 格）高，属可见取舍。

### 11.2 未覆盖 / 待复测（如实登记）

- 玩家视角的「跳不过 / 绕得开」手感未做逐帧复测；
- forge-1.20.1 线未实机（实体/方块代码与 neo 逐行等价，仅平台 API 差异已在编译期验证）；
- 远程怪（骷髅/掠夺者）与爆炸伤害未单独测试；
- 耐久归零后的「清方块 + 解除仇恨」路径未跑到（本轮只打到 16）。

### 11.3 第二轮（2026-10-18 01:0x，覆盖 forge 线与剩余项）

| 项 | neo 1.21.1 | forge 1.20.1 |
|---|---|---|
| 放置 + 碰撞箱 | 落点正确、`shapeY=1.50` | 落点正确、`shapeY=1.50` |
| 近战 AI 攻击 | 卫道士 30→16（12s/14 次） | 卫道士 30→16（12s/14 次） |
| 6 格外对照 | 不锁定、hp 30 | 不造成伤害、hp 30 |
| 精英对照（maxHealth=50） | `target=none`、hp 30 | 不造成伤害、hp 30 |
| 死亡清理 | bar=none、方块已删、3 只怪 `target=none` | bar=none、方块已删、3 只怪 `target=none` |
| 远程伤害 | 女巫药水 30→27 | 女巫药水 30→26 |
| 爆炸伤害 + 不被推开 | 苦力怕 30→29，位置不变 | 苦力怕 26→24，位置不变 |
| 玩家跳越 | 静态：碰撞箱 1.5 格 > 原版跳跃高度 1.2522 ⇒ 跳不过（逐帧手感未复测） | 同左 |

**本轮实机暴露并已修复的 2 个新缺陷**：
1. **弩弹/箭被碰撞方块先挡住**（掠夺者锁定并射击，耐久不降）⇒ 在 `BarricadeBlock#onProjectileHit` 里把命中本方块、有主投射物的伤害转发给路障实体（同样每次 −1；无主投射物不转发）。
2. **爆炸把实体推离方块**（苦力怕把路障从 y=96 推到 y=105.75，方块还在原地）⇒ `push(...)` 空实现 + 每 tick 位置钉回碰撞方块中心。
