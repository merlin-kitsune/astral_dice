# B3-EFFECTCARDS 移植报告（forge-1.20.1 → fabric-1.20.1）

批次：B3-EFFECTCARDS（效果牌实体化：路障 / 强化拒止）
基线：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/` 同路径同名文件
目标：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`（MC 1.20.1 + FAPI 0.92.12 + Loom，Mojang 官方映射，Java 17）
约束执行情况：本批未运行 gradle、未启动游戏；全部文件 UTF-8 无 BOM + CRLF（已用 python 逐字节核过）；`net.minecraftforge.*` / `net.neoforged.*` / `trinkets` / `accessories` / `wispforest` 在本批 13 个文件里 **0 命中**。

## 1. 交付清单

新增（9 个，forge 同名同包）：

| 文件 | 行数 | 形态 |
|---|---|---|
| `item/RepulsionPlacement.java` | 76 | 逐字复制（vanilla API） |
| `item/RepulsionItem.java` | 209 | 逐字复制 |
| `item/card/BarricadeCardItem.java` | 180 | 逐字复制 |
| `item/card/RepulsionCardItem.java` | 182 | 逐字复制 |
| `combat/DeathTriggerGuard.java` | 97 | 逐字复制（纯 java） |
| `combat/ElectricShockFx.java` | 85 | 逐字复制（原 forge 文件是 LF，已改 CRLF） |
| `event/BarricadeAggroHandler.java` | 77 | 改写（LoaderBus + 本线 `LivingChangeTargetEvent`） |
| `event/RepulsionTeleportGuard.java` | 60 | 改写（本线 `EntityTeleportEvent.EnderEntity`，当前无人派发） |
| `event/RepulsionCreeperGuard.java` | 108 | 重写为**无注解静态入口**（本线不存在 `ExplosionEvent`，见 §3-①） |

编辑（4 个，均为追加/定向替换，不动既有语义）：

- `component/ModDataComponents.java`：追加 4 个键 `OWNER_NAME` / `REPULSION_HEALTH` / `REPULSION_AGE` / `REPULSION_CHARGE`（键名、Codec、次序与 forge :98-109 逐字一致；位置在 `OWNER_UUID` 之后、`private ModDataComponents()` 之前）。
- `item/card/ExclusiveCardUtil.java`：`isExclusive` 追加 `|| stack.is(ModItems.EFFECT_CARD_REPULSION.get())`；并补齐 forge 的 `ownerOf(ItemStack)` / `ownerName(ItemStack)` 两个读数，`setOwner` 连带写入 `OWNER_NAME`（forge 2026-10-27 裁决，tooltip 依赖它）。
- `item/card/BaseEffectCardItem.java`：`cardByTypeId` 追加 `case "barricade"` / `case "repulsion"` 两行（forge :125-126 同款位置）。
- `entity/RepulsionEntity.java`：两处 `TODO-B0-ITEMS` seam 接线（`itemDurability` → `RepulsionItem.getDurability`；`createRecoveredStack` → `RepulsionItem.createRecovered`）+ 新增 `import ...item.RepulsionItem;`；`hasOwnerSign(Player)` **按约定保持返回 true（TODO-B0-SIGN 保留）**。

物品/创造栏（本批独占写权限）：

- `item/ModItems.java`：新增 `EFFECT_CARD_BARRICADE` / `EFFECT_CARD_REPULSION` / `REPULSION` 三条注册（`stacksTo(16)`+稀有 / `stacksTo(16)`+奇特 / `stacksTo(1)`+奇特），注释与 forge :384-407 逐字一致；新增 2 条 import（`BarricadeCardItem`/`RepulsionCardItem`；`RepulsionItem` 同包免 import）。
- `init/ModCreativeTabs.java`：`FATE_GUIDANCE_CARD` 之后插 `EFFECT_CARD_BARRICADE` / `EFFECT_CARD_REPULSION` 两行 accept，并照抄 forge :89-91 的三行「强化拒止物品不进创造栏」裁决注释。**`REPULSION` 物品本身不进创造栏**（forge 2026-10-25 裁决，JEI 列表取自创造栏 ⇒ 同时从 JEI 消失）。

## 2. 1:1 要点（常量逐条照抄）

- `RepulsionPlacement`：`PLACE_REACH = 4.5D`；判据顺序 = `level.clip(eye→eye+view(1.0F)×4.5, COLLIDER, NONE)` → 命中类型为 BLOCK → `relative(hit.getDirection())` → 世界内 → `WorldBorder#isWithinBounds` → `mayInteract` → `canBeReplaced` → 下方 `isFaceSturdy(below, UP)` → 同格存活 `RepulsionEntity`（`AABB(x,y,z,x+1,y+1,z+1)` + `entity.blockPosition().equals(target)`）。
- `RepulsionItem`：`MAX_DURABILITY = 50`、`LIFETIME_TICKS = 24000L`；耐久 clamp 0..50、年龄 clamp 0..24000、`getRemainingTicks = max(0, 24000 - elapsed)`、充能 clamp 0..`RepulsionEntity.MAX_CHARGE_STACKS(5)`；放置 yaw = `Math.round(player.getYRot()/90.0F)*90.0F`；`restoreState` 三参、`bindIfAbsent` 先于 `addFreshEntity`、`if (!player.isCreative()) stack.shrink(1)`；耐久条 `getBarWidth = Math.max(1, Math.round(13.0F*durability/50))`、`getBarColor = Mth.hsvToRgb(ratio/3.0F,1.0F,1.0F)`。
- `BarricadeCardItem`：`CARD_TYPE_ID="barricade"`、`PLACE_REACH = 4.5D`、yaw = `Math.round(user.getYRot()/180.0F)*180.0F`；放置 = `BARRICADE_COLLISION` 隐形碰撞块 + `BarricadeEntity`，`addFreshEntity` 失败则 `removeBlock` 回滚；音效 `SoundEvents.WOOD_PLACE`。**resolvePlacement 不含 AABB 重叠判据**（2026-10-18 起刻意删除：1.2 宽 AABB 会挡住邻格），本批照抄未回加。
- `RepulsionCardItem`：`CARD_TYPE_ID="repulsion"`、`isExclusive() = true`、yaw = 90° 倍数、`setOwnerUUID` 在 `addFreshEntity` **之前**、音效 `GLASS_PLACE`；放置判据只转发 `RepulsionPlacement.resolve`（单一事实源）。
- `BarricadeAggroHandler`：`HOLD_RANGE_SQR = 64.0D`；只对 `Mob`+`Enemy` 且 `!EliteTargets.isEliteOrBoss` 生效；`getNewTarget() instanceof BarricadeEntity` 时不动，否则 `setNewTarget(barricade)`。
- `RepulsionCreeperGuard` / `RepulsionTeleportGuard`：`LINK_RADIUS = 16.0D`；`creeper.getBoundingBox().inflate(16)` 内任一 `RepulsionEntity#isTracking(uuid)` 即命中；苦力怕取消后额外 `setSwellDir(-1)` + `FIRE_EXTINGUISH`（`SoundSource.BLOCKS`, 1.0F, 1.2F）。
- `DeathTriggerGuard`：`SAME_DEATH_WINDOW_TICKS = 1L`、`SAME_SWING_WINDOW_TICKS = 1L`、`PRUNE_THRESHOLD = 4096`、`PRUNE_KEEP_TICKS = 400L`；key = `uuid + "|" + channel`；`now - last <= 窗口` 即重复（拒）。
- `ElectricShockFx`：`BOLTS=2`、`SPARKS=7`、`CRACKLES=4`、`ELECTRIC_RGB=0x9BE8FF`、`CRACKLE_RGB=0xE8FBFF`、`BOLT_SCALE=1.7F`、`SPARK_SCALE=0.9F`、`CRACKLE_SCALE=0.7F`、`SOUND_VOLUME=0.5F`、`SOUND_PITCH=1.5F`；三次 `sendParticles`（COMET_AZUR_FILAMENT 0.18/0.22/0.18/0.02、GLINTSTONE_SPARK 0.30³/0.08、FROST_SPARK 0.24³/0.12）；音效走 `SoundPlayback.playAt(..., LIGHTNING_BOLT_IMPACT, 0.5F, 1.5F)`（本线已有 (volume,pitch) 重载，与 forge 完全同源）。

## 3. 偏差（forge 原样 → fabric 改法 + 理由）

1. `event/RepulsionCreeperGuard`：forge = `@Mod.EventBusSubscriber(bus=FORGE)` + `@SubscribeEvent onExplosionStart(ExplosionEvent.Start)`（`event.setCanceled(true)`）
   → fabric = 无注解静态入口 `public static boolean handleExplosionStart(Level level, Entity directSource, LivingEntity indirectSource)`，命中返回 `true`（由调用方 `setCanceled(true)`）。
   **理由**：本线**没有** `platform/event/level/ExplosionEvent`，也没有 `Explosion#explode` 的 bridge mixin（`porting/fabric-1.20.1/MAPPING-1.5.0.md` 把「新增 ExplosionEvent + 爆炸 bridge mixin」记为跨批次缺口 B-10）。判据、常量、反应（`setSwellDir(-1)` + 灭火音）逐条保留，只是订阅面改由未来的 B-10 桥调用。**这是本批唯一改动语义形态的文件。**
2. `event/BarricadeAggroHandler`：forge = 自动订阅 `LivingChangeTargetEvent`（Forge 事件总线）
   → fabric = `@SubscribeEvent` + 方法体逐条照抄，但**必须由 `AstralDiceMod.registerListeners()` 显式登记**（本线 LoaderBus 不自动扫描）。行为等价，接线见 §4-①。
3. `event/RepulsionTeleportGuard`：forge = 自动订阅 `EntityTeleportEvent.EnderEntity`
   → fabric = `@SubscribeEvent` + `EntityTeleportEvent.EnderEntity`（类与本线同名同 API，含 `getEntityLiving()`）。**但本线当前没有任何地方 fire 该事件**（`EnderPearl` 走独立 `EnderPearlTeleportBridgeMixin`，末影人/潜影贝瞬移无桥）⇒ 编译通过、行为休眠。需要 bridge（§4-⑤）。
4. `combat/ElectricShockFx`：与 forge **逐字一致**，无功能偏差；只把换行由 LF 统一成 CRLF（本线红线）。
5. `item/ModItems.java` 三条注册的 `rarity` 与 forge 同名同参（`AstralRarities.rare()` / `AstralRarities.bizarre()`）——本线 `starengine.item.AstralRarities` 已具备这两个工厂方法。

## 4. 需主 Agent 接线（确切语句 + 位置）

**① `AstralDiceMod.java` 的 `registerListeners()`（本批无权改）**，按现有字典序插入两行：

```java
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.AnvilUpgradeHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.BarricadeAggroHandler.class);   // ← 新增（路障拉仇恨）
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.ChipDamageHandler.class);
...
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.RailgunStrikeScheduler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.RepulsionTeleportGuard.class);  // ← 新增(需 §4-⑤ 的桥才生效)
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.TemporaryCardEvents.class);
```

漏登记后果：`BarricadeAggroHandler` 静默失效（路障不再 6 格拉住仇恨）；`SubscriptionAudit` 启动期会 WARN。

**② `item/card/RandomCardHandler` 的 `static` 块**（forge :123-124 同款，本批无权改）：

```java
        // 怪力乱神立牌专属效果牌(2026-10-18):只能由该立牌发放,不进任何随机池
        registerExclusiveCard(ModItems.EFFECT_CARD_REPULSION); // 强化拒止
```

漏了 ⇒ 强化拒止效果牌会混进随机抽卡池（与「专属牌不进随机池」裁决冲突）。

**③ `event/ModTooltipHandler`**：forge `:606-660` 三段照抄到 fabric 同名文件同一位置（`EFFECT_CARD_BARRICADE` 静态说明 / `EFFECT_CARD_REPULSION` 静态说明 + 专属行 / `ModItems.REPULSION` 物品三份状态 + 所有者两行）。依赖项已就绪：`ExclusiveCardUtil.ownerName(stack)`、`RepulsionItem.getDurability/getRemainingTicks/getChargeGiven/MAX_DURABILITY`、`RepulsionEntity.MAX_CHARGE_STACKS`、本线 `ModTooltipHandler` 已有 `addCardLines(List, String, Object...)`(:112) 与 `formatMmSs`。lang 键见 §5-③。

**④ `item/chip/ElectricGloveChipItem`**（forge :175 / :182）：`ElectricShockFx` 已落线但**本线无人调用** ⇒ 电击手套命中观感在 fabric 上不存在。补两处：

```java
ElectricShockFx.sparksAt(level, ElectricShockFx.victimCenter(victim));       // 每个真正吃到伤害的受害者
ElectricShockFx.shockSound(level, center.add(0.0D, 1.0D, 0.0D));             // 结算圆心播一次
```

（`level` 必须是 `ServerLevel`；`center` = forge 同名局部变量，即电击结算的圆心。）

**⑤ 两个平台缺口（本批不可自建，需跨批次）**：

- 苦力怕爆炸：新增 `platform/event/level/ExplosionEvent`（含 `Start`：`getLevel()`/`getExplosion()`/`setCanceled`）+ `mixin/bridge/ExplosionBridgeMixin`（`Explosion#explode` HEAD 派发）+ `resources/astral_dice.mixins.json` 登记；桥里调用：
  ```java
  if (com.merlinkitsune.astral_dice.event.RepulsionCreeperGuard.handleExplosionStart(
          level, explosion.getDirectSourceEntity(), explosion.getIndirectSourceEntity())) {
      event.setCanceled(true);
  }
  ```
- 末影人/潜影贝瞬移：mixin 到 `EnderMan#teleport`（及潜影贝 `Shulker#teleport`）在成功前派发 `EntityTeleportEvent.EnderEntity`；派发后 `RepulsionTeleportGuard` 自动生效（它已登记即可）。

## 5. 本批补的符号 / 仍缺的共享件

① 已补（均在白名单内、只追加）：
- `component/ModDataComponents`：`OWNER_NAME`(String) / `REPULSION_HEALTH` / `REPULSION_AGE` / `REPULSION_CHARGE`(Integer)，键名 `owner_name` / `repulsion_health` / `repulsion_age` / `repulsion_charge`。
- `ExclusiveCardUtil`：`ownerOf(ItemStack)`、`ownerName(ItemStack)`，且 `setOwner` 补写 `OWNER_NAME`（forge 2026-10-27 裁决）。
- `BaseEffectCardItem`：`cardByTypeId` 两个 case。

② **阻塞项（非本批职责）**：`item/sign/LinglingSignManager` 在 fabric 线**整个类不存在**（`item/sign/` 目录在，但没有该文件）。本批 `RepulsionItem` / `RepulsionCardItem` 按 forge 直接 import 并使用其 `hasSign(Player)` / `isRepulsion(ItemStack)` / `toBarricade(ItemStack)`——因为 `screen/CardShopScreen.java:7`、`shop/CardShopMenu.java` 早已 import 同一个类，整条线在立牌批次落线前本来就编译不过，本批没有新增缺口。立牌批次需提供的最小面：
```java
public static boolean hasSign(net.minecraft.world.entity.player.Player player);   // 身上带「怪力乱神」立牌
public static boolean isRepulsion(net.minecraft.world.item.ItemStack stack);        // 强化拒止效果牌
public static net.minecraft.world.item.ItemStack toBarricade(net.minecraft.world.item.ItemStack stack); // 卡 → 路障卡
```
（`entity/RepulsionEntity.hasOwnerSign` 仍是 TODO-B0-SIGN 返回 true；立牌落线后改一行即可。）

③ **lang 缺键**（三个语言文件都要加，键名见下；值可从 forge-1.20.1 同路径 lang 直接取）：
`item.astral_dice.effect_card_barricade`、`item.astral_dice.effect_card_repulsion`、`item.astral_dice.repulsion`；
（交报告中复核：截至本次收尾，`en_us` / `ja_jp` / `zh_cn` 三份 lang 上述键**全部缺失**；物品模型已由并行批次生成，`src/generated/resources/assets/astral_dice/models/item/{effect_card_barricade,effect_card_repulsion,repulsion}.json` 均存在。）
`msg.astral_dice.effect_card_barricade_invalid` / `_placed`、`msg.astral_dice.effect_card_repulsion_invalid` / `_placed`、`msg.astral_dice.repulsion_invalid` / `_placed` / `_need_sign`；
tooltip 组：`tooltip.astral_dice.card.barricade`、`tooltip.astral_dice.card.repulsion`、`tooltip.astral_dice.item.repulsion` + `repulsion_durability` / `repulsion_lifetime` / `repulsion_charge` / `repulsion_owner` / `repulsion_owner_name` / `repulsion_owner_unknown`、`tooltip.astral_dice.card.exclusive_owner`（若该键在本线尚缺）。

## 6. 无法判定项

- `RepulsionCreeperGuard` 的最终行为取决于 B-10 的爆炸桥是否派发在 `Explosion#explode` 的 HEAD（forge 语义）：若桥派发点改成「已 apply 之后」，`setCanceled` 将不再阻止爆炸 ⇒ 届时必须回到 forge 的 HEAD 口径。
- `EntityTeleportEvent.EnderEntity` 的 fabric 语义（是否包含跨维度、是否允许在 `setCanceled` 后回退 choke 计数）本批无实现可对照，等桥落线后需实测。
- `RepulsionItem` 的耐久条颜色 `Mth.hsvToRgb(ratio/3.0F,...)` 依赖 `Item#getBarColor` 的 fabric 桥是否被客户端调用（本线 `item` 层的耐久条渲染桥不在本批范围），是否已在 `platform` 侧接线**未验证**（无编译环境）。
