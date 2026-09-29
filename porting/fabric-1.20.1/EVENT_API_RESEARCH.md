# 「mixin 桥缺失事件」第三方 API 可行性调研（2026-09-29，联网实测）

> 问题背景：Fabric 1.20.1 **没有可改伤害值的事件**（FAPI 的 `ServerLivingEntityEvents.ALLOW_DAMAGE`
> 只能取消、不能改 amount），而本模组的伤害链明确依赖 `EventPriority.LOWEST/HIGHEST` 的**承重语义**
> （源码注释：「气囊恒为第一顺位」「LOWEST = 最终伤害阶段」）。
> 本次调研的问题是：这些「FAPI 没有对应物」的事件，**是否有第三方模组库可以直接提供**，
> 从而免去自写 mixin？

## 一、候选库调研结果（全部为联网实测 / 源码阅读）

### 1. Architectury API（`dev.architectury.event.events.common.*`）

| 项 | 实测结果 |
|---|---|
| 1.20.1 可用性 | ✅ 有（9.x 线；且 **KubeJS Fabric 本就依赖它**，测试环境里必然存在） |
| 事件覆盖面 | `EntityEvent.ADD` / `LIVING_DEATH` / `LIVING_HURT` / `LIVING_CHECK_SPAWN`、`PlayerEvent.PLAYER_CLONE` / `CHANGE_DIMENSION` / `ATTACK_ENTITY` / `PICKUP_ITEM_PRE/POST` / `CRAFT_ITEM`、`InteractionEvent.*`、`BlockEvent.BREAK`、`LifecycleEvent.SETUP`、`TickEvent.*` |
| **致命局限** | **`EntityEvent.LIVING_HURT` 的签名是 `EventResult hurt(LivingEntity, DamageSource, float amount)` —— 只能返回 `EventResult`（pass / interruptTrue / interruptFalse），<br>❌ 不能改伤害值**。而 Forge 的 `LivingHurtEvent` / `LivingDamageEvent` 都有 `setAmount()`，本模组的伤害链（星币/星光/治愈点加伤、气囊/磨刀石保命）**全靠改值**。 |
| 实现方式 | **它自己也是靠 mixin 实现的**（`MixinLivingEntity` / `MixinPlayer` / `MixinServerPlayer` / `MixinExplosion`；Fabric 侧 53 个 mixin）。⇒ 用它不是「免 mixin」，而是「用别人的 mixin」。 |
| 结论 | ⚠️ **不满足核心需求**（不能改伤害值）。可覆盖的只是次要事件（`PLAYER_CLONE`、`CHANGE_DIMENSION`、`CRAFT_ITEM`、`ADD`）。 |

### 2. Porting Lib（`io.github.fabricators_of_create.Porting-Lib`，2.3.2+1.20.1）

| 项 | 实测结果 |
|---|---|
| 1.20.1 可用性 | ✅ 有（Fabric/Quilt，2024 年发布 2.3.2，之后未再更新） |
| **关键能力** | 更新日志明写 **"Add LivingDamageEvent and LivingHurtEvent"** —— 提供与 Forge **同名**的 `LivingHurtEvent` / `LivingDamageEvent`（带 `setAmount`） |
| 其它模块 | `lazy_registration`（Forge `DeferredRegister` 的 Fabric 重写）、`networking`（Forge-like packet）、`config`（Forge config 轻量移植）、`level_events`、`loot`、`tags`、`accessors`、`mixin_extensions`、`attributes`、`extensions`、`fake_players` |
| 引入代价 | ① 它是 **Create Fabric 的前置**（LGPL-2.1-only）⇒ 玩家看到它会困惑，且会与 Create 的版本强耦合；② 需要 `mvn.devos.one` 仓库；③ 2024 年后无更新 ⇒ 维护风险；④ 我们是**从 Forge 移植**，引入「Forge API 的二手移植」会形成三层抽象（我们的 platform 层 → Porting Lib → Fabric），排障困难。 |
| 结论 | ⚠️ 技术上**能满足**改值需求，但引入代价与耦合过高。**不采用**。 |

### 3. Puzzles Lib（`fuzs.puzzleslib`，1.20.1 Fabric = v8.1.32）

| 项 | 实测结果（源码已克隆到 `ref/puzzles-lib`，逐文件核对） |
|---|---|
| 事件覆盖面 | **极完整**：`LivingHurtCallback`（`EventResult onLivingHurt(LivingEntity, DamageSource, MutableFloat amount)` —— **✅ 可改值**）、`LivingAttackCallback`、`LivingDeathCallback`、`LivingChangeTargetCallback`、`LivingKnockBackCallback`、`LivingDropsCallback`、`LivingFallCallback`、`LivingExperienceDropCallback`、`LootingLevelCallback`、`ShieldBlockCallback`、`MobEffectEvents`（`AFFECTS`/`APPLY`/`REMOVE`/`EXPIRE` —— 对应 Forge `MobEffectEvent.*`）、`PlayerEvents`（`Copy`=Clone、`AfterChangeDimension`、`ItemPickup`、`LoggedIn/Out`）、`ItemTossCallback`、`AnvilUpdateCallback`、`ProjectileImpactCallback`、`BlockEvents.Break`、`LootTableLoadEvents`、`ServerLifecycleEvents`、`EntityRidingEvents` |
| 数值可变支持 | ✅ 有 `DefaultedDouble` / `MutableDouble` / `DefaultedFloat` / `MutableInt` / `MutableValue` 一族（正是 Forge 事件「可改数值」的等价物） |
| 引入代价 | ① **新增硬前置**（玩家安装面变化，用户未要求）；② 它自己还要求 **Fabric API + Forge Config API Port** 两个前置；③ 其事件**注入点由它自己决定**，与 Forge 的 `ForgeHooks.onLivingHurt` / `onLivingDamage` 是否逐点对齐**不可控**。 |
| 结论 | ⚠️ 覆盖度最高、语义最接近，但「新增前置 + 注入点黑盒」两点与项目红线冲突。**不采用**（若未来用户愿意加前置，这是第一顺位候选）。 |

### 4. MixinExtras —— **不需要引入**（已内置）

| 项 | 实测结果 |
|---|---|
| 内置状态 | ✅ **Fabric Loader 0.17.0 起内置 MixinExtras**（本线用 loader **0.19.5**）⇒ `@WrapOperation` / `@ModifyExpressionValue` / `@ModifyReturnValue` / `@WrapWithCondition` / `@Cancellable` / `@Local` / `@Share` **零依赖可用** |
| 收益 | 相比 `@Redirect` / `@ModifyConstant`，新注入器**可链式共存**（不会与其它模组的注入互相顶掉），显著降低整合包内冲突概率 |
| 结论 | ✅ **采用**（零新增依赖的纯收益）。 |

### 5. Fabric API 自身的覆盖（全部已用上）

| 事件 | FAPI 回调 | 能否改值 |
|---|---|---|
| 伤害（应用前） | `ServerLivingEntityEvents.ALLOW_DAMAGE` | ❌ 仅可取消 |
| 伤害（应用后） | `ServerLivingEntityEvents.AFTER_DAMAGE` | ❌ 只读（`baseDamage`/`actualDamage`/`blocked`） |
| 死亡 | `ServerLivingEntityEvents.ALLOW_DEATH`（可取消）/ `AFTER_DEATH` | — |
| 玩家克隆 | `ServerPlayerEvents.COPY_FROM` / `AFTER_RESPAWN` | — |
| 战利品 | `LootTableEvents.MODIFY`（**5 参**：`ResourceManager, LootManager, Identifier, LootTable.Builder, LootTableSource`） | — |
| 交互 | `UseItemCallback` / `UseBlockCallback` / `UseEntityCallback` | — |
| tooltip | `ItemTooltipCallback.EVENT` | — |
| 数据包同步 | `ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS` | — |

## 二、逐事件可行性总表（决定「FAPI / 自写 mixin」）

| Forge 事件（本模组用到的） | 处理器数 | 第三方可用？ | 本线采用的方案 |
|---|---|---|---|
| `LivingDamageEvent`（改最终伤害） | 11 | Architectury ❌不能改值；**Porting Lib ✅**；**Puzzles Lib ✅** | **自写 mixin** |
| `LivingHurtEvent`（改护甲后伤害） | 4 | 同上 | **自写 mixin** |
| `LivingDeathEvent`（取消死亡） | 13 | Architectury ✅(EventResult) / FAPI `ALLOW_DEATH` ✅ | FAPI `ALLOW_DEATH` + 自写 mixin（需 Forge 的**派发时机**） |
| `LivingAttackEvent`（可取消） | 5 | FAPI `ALLOW_DAMAGE` ✅ | **已桥接**（FAPI） |
| `MobEffectEvent.Added/Remove/Expired/Pre` | 4+4+4+2 | Puzzles Lib ✅(`MobEffectEvents`) | **自写 mixin**（4 个注入点） |
| `LivingHealEvent` | 1 | Puzzles Lib ❌无此项 | **自写 mixin** |
| `LivingChangeTargetEvent` | 1 | Architectury ✅(`LIVING_CHANGE_TARGET`) / Puzzles ✅ | 自写 mixin（注入点自主） |
| `LivingUseTotemEvent` | 1 | 均 ❌ | 自写 mixin |
| `LivingDropsEvent` | 1 | Puzzles ✅ | **自写 mixin**（已在 `LootInjectionHandler` 保留原逻辑） |
| `EntityItemPickupEvent` | 1 | Architectury ✅(`PICKUP_ITEM_PRE`) | 自写 mixin |
| `AttackEntityEvent` | 1 | Architectury ✅ / Porting Lib ✅ | FAPI `AttackEntityCallback` ✅ 或自写 mixin |
| `ItemTossEvent` | 1 | Puzzles ✅(`ItemTossCallback`) | 自写 mixin |
| `ItemCraftedEvent` | 2 | Architectury ✅(`CRAFT_ITEM`) | 自写 mixin |
| `AnvilUpdateEvent` | 1 | Puzzles ✅ | 自写 mixin |
| `ProjectileImpactEvent` | 1 | Architectury ✅ / Porting Lib ✅ | 自写 mixin |
| `EntityTravelToDimensionEvent` | 1 | 均 ❌（Puzzles 只有**事后**的 `AfterChangeDimension`） | 自写 mixin |
| `EntityTeleportEvent`（末影珍珠） | 1 | 均 ❌ | 自写 mixin |
| `BlockEvent.BreakEvent` | 1 | Architectury ✅ / Puzzles ✅ | 自写 mixin |
| `RightClickItem/Block`、`EntityInteract` | 3 | **FAPI 原生** ✅ | FAPI 回调 |
| `ItemTooltipEvent` | 1 | **FAPI 原生** ✅ | FAPI 回调 |
| `PlayerTickEvent`、`ServerTickEvent` | 11 | FAPI ✅ | **已桥接** |
| `PlayerLoggedIn/OutEvent`、`Clone`、`PlayerRespawnEvent` | 11 | FAPI ✅ | **已桥接** |
| `RegisterCommandsEvent`、`OnDatapackSyncEvent` | 2 | FAPI ✅ | **已桥接** |
| 客户端（`RenderLevelStageEvent`/`RenderHandEvent`/`MouseScrollingEvent`/`RegisterGuiOverlaysEvent`/`RegisterParticleProvidersEvent`/`RegisterKeyMappingsEvent`/`ItemTooltipEvent.Color`/`Opening`/`Finish` 等） | ~12 | 部分有（Architectury `ClientPlayerEvent` 等） | FAPI（`HudRenderCallback`/`ScreenEvents`/`WorldRenderEvents`/`ParticleFactoryRegistry`）+ client mixin |

## 三、最终决策与理由

**不引入任何第三方事件库**（Architectury / Porting Lib / Puzzles Lib 全部不采用），
**自写 mixin 桥**覆盖上表中标注的事件；MixinExtras 作为 Loader 内置能力直接使用。

理由（按权重排序）：

1. **注入点必须自主可控**。本模组的伤害链是「气囊保命优先、磨刀石次之、LOWEST = 最终伤害阶段」这种**顺序承重**的语义，
   Forge 侧是靠 `ForgeHooks.onLivingHurt` / `onLivingDamage` 的**精确派发时机**实现的。
   第三方库的回调挂在它自己选的注入点上，我们无法保证逐点对齐 —— 一旦错位，表现是**数值悄悄不对**（最难发现的一类回归）。
2. **零新增前置**。玩家已需 Trinkets（而 Trinkets 又硬依赖 CCA）；再加 Puzzles Lib（及其 Forge Config API Port）
   会显著抬高安装门槛，而用户本次并未要求改变前置面。
3. **交付文档口径**：移植本体用「自有 platform 层 + 自有 mixin」是**单层抽象**，排障路径短；
   引入 Porting Lib 会变成「我们的 platform 层 → Porting Lib → Fabric」的三层，且其 2024 年后停更。
4. **不需要为省 mixin 而引入**：本模组在 Forge 侧**本来就有 18 个 mixin**，
   这批判定只需把「已有的 mixin 体系 + 事件类（已逐字从 Forge sources 转译）」延伸到新注入点，
   边际成本远低于引入并适配一个第三方事件系统。

## 四、本轮由此产生的两条硬性工程结论

1. **Loom 只重映射「纯方法名」的 `@Inject(method = ...)` 注解值**。
   实测：`method = "getMaxStackSize"` → `method_7914`（intermediary，✅ 已重映射）；
   而 `method = "drop(Z)Lnet/minecraft/world/entity/item/ItemEntity;"`（**带描述符**）→ **原样保留**（❌ 未重映射）。
   在 intermediary 生产环境里后者会「找不到目标方法 → 注入失败 → `defaultRequire: 1` 直接崩」。
   ⇒ **红线：本线所有 mixin 的 `method` 一律写纯方法名**（重载歧义交给 Mixin 按 handler 签名自动筛选）。
   已复核：生产 jar 内 **21 个 mixin 类、0 处 Mojmap 残留**。

2. **1.20.1 的战利品表不是注册表条目**。没有 `Registries.LOOT_TABLE`，
   `LootTableReference.lootTableReference(...)` 收 **`ResourceLocation`**（不是 1.21 的 `ResourceKey<LootTable>`），
   `LootTable.Builder#pool(...)` 收 **`LootPool`**（不是 `LootPool.Builder`，需 `.build()`）。
   这三处是 1.20.1 ↔ 1.21 最容易误写的地方。
