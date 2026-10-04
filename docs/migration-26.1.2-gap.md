# 26.1.2 移植实际缺口报告（2026-09-21 实测）

> 基准：`neoforge-1.21.1`（同为 NeoForge 端口）。
> 本报告**不替代** `docs/migration-26.1.2-prep.md`；prep 报告是**移植前**的规划，
> 本报告是**实测后**的真实缺口清单 —— 两者在「待重写模块」一节上有实质差异，见 §0。

---

## §0 与 prep 报告的关键差异（必须先读）

prep 报告把 26.1.2 描述为「需要**重写 6 处平台适配**」。
实测结论：**那 6 处平台适配已经做完了**，文件都已存在且已按 26.1.2 API 改写：
| prep 报告列为「需重写」 | 实测 26.1.2 现状 |
|---|---|
| item-model datagen | `datagen/ClientDataGenerators.java` + `ModItemModelProvider` 已用 `GatherDataEvent.Client` |
| `EffectsInInventory` mixin | `mixin/client/EffectsInInventoryMixin.java` **已存在**，且 javadoc 记录了方法搬迁 |
| outline capture | `client/OutlineCaptureRenderStateModifier.java` + `mixin/client/LivingEntityOutlineCaptureMixin.java` **已存在** |
| datagen 拆分 | `ClientDataGenerators` / `ServerDataGenerators` 双入口**已存在**，`build.gradle` 有 `clientData`/`serverData` 两个 run、`src/generated/clientResources` 第二输出根 |
| 容器守卫 mixins | ❌ **被删掉了**，见 §3.3（这是真缺口，不是"已重写"） |
| `AstralDiceMod` 客户端类型泄露 | 已修（`ec4fbd80` 之后的下沉调整） |
| Iron 法术联动删除 | ✅ 已删（`event/IronSpellbooksCompat.java` 不存在，`build.gradle:193-195` 有记录） |

**真正的缺口是功能面：26.1.2 落后 1.21.1 整整一批功能，而不是"API 没适配"。**
实测 26.1.2 的困难不在编译错误 —— 它编译**通过**（`BUILD SUCCESSFUL`），
问题是**它编译的是一个功能残缺的旧快照**。

文件数：1.21.1 = **242**，26.1.2 = **216**（§1.1 effect 与 `ModAttachments` 已完成本轮移植后）；
`neoforge-1.21.1` 独有 **32**，26.1.2 独有 **6**（均为已适配的平台替身文件）。

### 量化总表（2026-09-22 实测，含本轮已完成部分）

| 指标 | 1.21.1（基准） | 26.1.2（移植前） | 差 |
|---|---|---|---|
| java 文件 | 242 | 216 | **−32**（原 −38，本轮已补 effect/钩子） |
| java 总行 | 34119 | 29011 | **−5108** |
| 注册 effect | 43 | **43** ✅ | **0**（本轮已补齐） |
| 注册 item | 136 | 124 | **−12**（待办） |
| `ModAttachments` 注册键 | 99 | **99** ✅ | **0**（本轮已补齐） |
| `ModAttachments` 公开访问器 | 299 | **299** ✅ | **0**（本轮已补齐） |
| `ModAttachments` 方法名集 | — | — | **0 / 0**（无缺无多） |

⚠️ 本轮已把 `ModAttachments` 补齐到**逐键对等**（99 键同序、299 访问器、方法名集零差集），
并顺带修掉了它自己的一个真实缺陷：26.1.2 侧 5 处 `UUIDUtil` 序列化只有 3 处包了
`.fieldOf("value")`，另 2 处是裸 `.codec()`（`AttachmentType.Builder#serialize` 只收 `MapCodec`
⇒ 那 2 处不可能编译过；本次统一为 `.codec().fieldOf("value")`）。

**剩余 32 个缺失文件合计 5575 行**（扣除不复移植的 `IronSpellbooksCompat` 28 行 ⇒ 待移植 ~5547 行），
按依赖波次排列见 §10。

---

## §1 注册表层面的硬缺口（最客观的判据）

### 1.1 状态效果：缺 11 个（1.21.1 = 43，26.1.2 = 32）

| 注册键 | 载体类（1.21.1） | 所属功能 |
|---|---|---|
| `dragon_roar_break` | `DragonRoarBreakEffect` | 龙吼破防（咬击牌链） |
| `hanna_doll_complete` | `HannaDollCompleteEffect` | 汉娜人偶·完成 |
| `hanna_doll_craft` | `HannaDollCraftEffect` | 汉娜人偶·制作 |
| `hanna_float` | `HannaFloatEffect` | 汉娜浮空 |
| `mamushi_dragon` | `MamushiDragonEffect` | 蝮蛇龙化 |
| `misfortune` | `MisfortuneEffect` | 厄运 |
| `nardis_privilege` | `NardisPrivilegeEffect` | **女王特权**（临时牌系统真值） |
| `sherry_reasoning` | `SherryReasoningEffect` | 雪莉推理 |
| `teru_descent` | `TeruDescentEffect` | 辉夜降临 |
| `teru_huguang` | `HuguangEffect` | 湖光 |
| `zhao_blessing` | `ZhaoBlessingEffect` | 赵的祝福 |

### 1.2 物品：缺 12 个（1.21.1 = 136，26.1.2 = 124）

| 注册 id | 载体类（1.21.1） | 类型 |
|---|---|---|
| `hanna_sign` | `HannaSignItem` | 立牌 |
| `mamushi_sign` | `MamushiSignItem` | 立牌 |
| `nardis_sign` | `NardisSignItem` | 立牌（绿洲女王） |
| `sherry_sign` | `SherrySignItem` | 立牌 |
| `teru_sign` | `TeruSignItem` | 立牌 |
| `zhao_sign` | `ZhaoSignItem` | 立牌 |
| `fu_card` | `FuCardItem` | 卡牌 |
| `huo_card` | `HuoCardItem` | 卡牌 |
| `attack_card_bite` | （`BaseEffectCardItem` 注册项） | 卡牌 |
| `attack_card_dragon_roar` | （`BaseEffectCardItem` 注册项） | 卡牌 |
| `golden_shooting_star_chip` | `ShootingStarChipItem` | 筹码 |
| `purple_shooting_star_chip` | `ShootingStarChipItem` | 筹码 |

⚠️ 这 12 项在 26.1.2 侧**连语言键与贴图都没有**（实测 `lang` 命中 0、`textures/item/*.png` 缺失），
说明不是"注册漏了"，而是**整批功能从未迁移** —— 补齐需要「代码 + 语言 + 贴图 + 配方 + 标签」全链。

---

## §2 1.21.1 独有文件全清单（44 个，按包）

```
[client]          1   ClientKeyNames.java
[client/gui]      1   StarCoinWalletButtons.java
[combat]          3   EliteTargets.java
                      SherryThrowManager.java
                      ShootingStarManager.java
[datagen]         1   DataGenerators.java              ← 不移植（已被 Client/ServerDataGenerators 取代）
[economy]         4   StarCoinBalanceSync.java
                      StarCoinCurrency.java
                      StarCoinPickupHandler.java
                      StarCoinWalletActions.java
[effect]         11   DragonRoarBreakEffect / HannaDollCompleteEffect / HannaDollCraftEffect
                      HannaFloatEffect / HuguangEffect / MamushiDragonEffect / MisfortuneEffect
                      NardisPrivilegeEffect / SherryReasoningEffect / TeruDescentEffect / ZhaoBlessingEffect
[event]           2   IronSpellbooksCompat.java          ← 不移植（用户明确指示删除）
                      TemporaryCardEvents.java
[fixes]           2   DamageStackSanitizer.java          ← 不移植（26.1.2 上游已修）
                      NeoForgeFixesLog.java              ← 不移植（同上）
[init]            1   ModCompatibilityCheck.java         ← 不兼容黑名单，必须移植
[item/card]       5   DragonCardUtil.java
                      FuCardItem.java
                      HuoCardItem.java
                      TemporaryCardPermissiveSlot.java
                      TemporaryCardUtil.java
[item/chip]       1   ShootingStarChipItem.java
[item/sign]       6   HannaSignItem / MamushiSignItem / NardisSignItem
                      SherrySignItem / TeruSignItem / ZhaoSignItem
[mixin/client]    1   EffectRenderingInventoryScreenMixin.java  ← 不移植（已由 EffectsInInventoryMixin 取代）
[mixin/container] 2   ContainerMoveGuardMixin.java      ← 必须移植（临时牌三道保护之一）
                      SlotPlaceGuardMixin.java         ← 必须移植（同上）
[mixin/fixes]     1   LivingEntityDamageContainerMixin.java  ← 不移植（上游已修）
[network]         2   StarCoinBalancePayload.java
                      StarCoinWalletPayload.java
```

**净需移植 ≈ 34 个**（44 − 10 个"不移植"），另需同步注册表与资源。

---

## §3 三个必须单独说明的高危缺口

### 3.1 临时牌系统（「女王特权」）**整体缺失** ⇒ 功能不完整

1.21.1 侧是一套**四层保护 + 一个真值 + 一个菜单接口**的完整设计：

| 层 | 文件 | 职责 |
|---|---|---|
| 真值 | `effect/NardisPrivilegeEffect` | 「临时牌是否在有效期内」的**唯一判据** = 效果实例存在 |
| 判据 | `item/card/TemporaryCardUtil` | `mark/isTemporary/glint/onDroppedByPlayer/fitsInsideContainer/isPlacementBlocked/isRangePlayerOwnedOrPermissive` |
| 标记接口 | `item/card/TemporaryCardPermissiveSlot` | 卡牌栏两槽实现它（临时牌放进去 = 装备） |
| 守卫① | `mixin/container/SlotPlaceGuardMixin` | `Slot#mayPlace` HEAD 拦截 |
| 守卫② | `mixin/container/ContainerMoveGuardMixin` | `AbstractContainerMenu#moveItemStackTo` HEAD 拦截 |
| 守卫③ | `item/card/CardItem` + `BaseEffectCardItem` | 覆写 `isFoil/onDroppedByPlayer/canFitInsideContainerItems` |
| 维护 | `event/TemporaryCardEvents` | 玩家 tick 自检（有临时牌但无效果 ⇒ 立即清空） |
| 菜单 | `screen/CardInventoryMenu` | 两槽 `implements TemporaryCardPermissiveSlot` + `mark()` 调用 |

⚠️ **`SlotPlaceGuardMixin` 与 `ContainerMoveGuardMixin` 必须成对**：1.21.1 源码注释实测，
`AbstractContainerMenu#moveItemStackTo` 的**同类栈合并分支完全不查 `mayPlace`** ——
只补 `SlotPlaceGuardMixin` 时，「Shift 点击把临时牌并进箱子里已有的同类栈」会**绕过全部槽位校验**
（临时牌被悄悄搬进容器）。两条缺一不可，且都必须调用**同一个**判据
`TemporaryCardUtil.isPlacementBlocked(...)`。

**26.1.2 现状：上述 8 项全部为 0**（全仓 grep `TemporaryCard` / `NardisPrivilege` 零命中）。

### 3.2 星币钱包系统整体缺失

26.1.1 的 4 个 `economy/*` + 2 个 `network/StarCoin*Payload` + `client/gui/StarCoinWalletButtons`
在 26.1.2 全部不存在。注意与 §0 的对照：**`config/ModCommonConfig.java` 此前是 `CONFIG_VERSION = 2`**
（缺 `ALLOW_FIREARM_DAMAGE` 与 7 项钱包选项），本轮已用 1.21.1 版本覆盖为 `CONFIG_VERSION = 4` ——
但**钱包本体的实现类仍缺**，配置项目前是"有配置、无功能"。

### 3.3 `init/ModCompatibilityCheck` 缺失 ⇒ **黑名单保护失效**

1.21.1 侧对 `magic_coins` / `sg_economy` 命中即**拒绝启动**（用平台加载异常上屏，
见 `AGENTS.md`）。26.1.2 缺这个类 ⇒ 装了不兼容模组时会**静默正常运行**，然后出现经济系统双重定义/覆盖。
这是**用户可见的恶性缺陷**，优先级最高。

---

## §4 6 个 26.1.2 独有文件（平台适配，保留）

```
client/OutlineCaptureRenderStateModifier.java   ← 取代 1.21.1 的 outline 捕获写法
datagen/ClientDataGenerators.java               ← 取代 datagen/DataGenerators.java（客户端半边）
datagen/ServerDataGenerators.java               ← 同上（服务端半边）
event/ChipSlotMigrationHandler.java             ← 筹码槽迁移
mixin/client/EffectsInInventoryMixin.java       ← 取代 EffectRenderingInventoryScreenMixin
mixin/client/LivingEntityOutlineCaptureMixin.java
```

`astral_dice.mixins.json` 两侧结构对照（**26.1.2 需补回 `container.*` 两条**）：

| 分区 | 1.21.1 | 26.1.2 | 差 |
|---|---|---|---|
| `mixins` | 10 条（含 `container.ContainerMoveGuardMixin`、`container.SlotPlaceGuardMixin`） | 8 条 | −2 ← **§3.1 的守卫** |
| `client` | 3 条（含 `client.EffectRenderingInventoryScreenMixin`） | 4 条（含 `client.EffectsInInventoryMixin`、`client.LivingEntityOutlineCaptureMixin`） | 名称已换代 |
| `compatibilityLevel` | `JAVA_21` | `JAVA_25` | 正确 |

---

## §5 建议执行顺序（按"用户可见性"降序）

1. **P0 — `init/ModCompatibilityCheck`**：黑名单失效是恶性缺陷，且是单文件、零依赖。
2. **P0 — 11 个 effect**：全部是 `mob_effect`，是 12 个缺失物品的**前置**（立牌主动技能靠它们做状态载体）。
3. **P1 — 6 个立牌物品 + 2 个卡牌 + 2 个流星筹码 `ShootingStarChipItem` + `combat/{EliteTargets,SherryThrowManager,ShootingStarManager}` + `item/card/DragonCardUtil`**：物品与其实体逻辑成组。
4. **P1 — 临时牌系统 8 件套**（§3.1），含 2 条 mixin 与 `mixins.json` 登记。
5. **P1 — 星币钱包 7 件套**（§3.2），含 2 个网络包登记。
6. **P2 — 注册表收口**：`ModItems` / `ModEffects` / `ModCreativeTabs` 展示序 / `ModPayloads` / `ModMenuTypes`。
7. **P2 — 资源收口**：`lang/zh_cn.json` + `lang/en_us.json` 语言键、`textures/item/*.png`、`textures/mob_effect/*.png`、配方与标签、`bountiful` 池。
8. **P3 — EventBusSubscriber 全量核对**（用户明确要求，见 §6）。
9. **P3 — 构建验证四件套** + **移植后代码审查**。

---

## §6 EventBusSubscriber 核对口径（用户明确要求，务必严格）

实测 26.1.2 现有 **44 处** `EventBusSubscriber` 出现点（含 import 与 javadoc 引用）。
核对时必须覆盖**三类**，缺一不可：

1. **新增类必须带注解**：本轮移植的每个带 `@SubscribeEvent` 的类都要有 `@EventBusSubscriber`，
   且 26.1.2 只剩 `value()/modid()` 两个属性（`bus=` 已删）。
2. **反向核对：无 `@SubscribeEvent` 方法的类不得带注解**。
   26.1.2 对「订阅者类里没有 `@SubscribeEvent` 方法」**直接抛异常**（比 1.21.1 严格）。
   1.21.1 侧已有先例注释：`item/sign/MosesSignItem`、`item/sign/RenSignItem` 明确**不得**标注。
3. **两面都要有**：1.21.1 有注解而 26.1.2 没有 ⇒ 事件不会触发（功能静默失效）。
   这是本轮最危险的一类，因为**编译期看不出来**。

---

## §7 不移植清单（含理由，须写进 CHANGELOG）
| 文件 | 理由 |
|---|---|
| `datagen/DataGenerators.java` | 26.1.2 的 `GatherDataEvent` 已拆分为 `.Client`/`.Server`，旧单一入口无法加载 |
| `fixes/DamageStackSanitizer.java` | 26.1.2 上游已修（`neoforge-26.1.2.109-userdev.jar → patches/.../LivingEntity.java.patch` 的取消分支已含 `this.damageContainers.pop();`，1.21.1 的没有） |
| `fixes/NeoForgeFixesLog.java` | 同上，配套日志类 |
| `mixin/fixes/LivingEntityDamageContainerMixin.java` | 同上，上游修复后该 mixin 变成重复补丁 |
| `mixin/client/EffectRenderingInventoryScreenMixin.java` | MC 26.1.2 把效果面板抽成 `EffectsInInventory`，已由 `EffectsInInventoryMixin` 取代 |
| `event/IronSpellbooksCompat.java` | 用户明确指示：Iron's Spells 'n Spellbooks 无 26.1.x 构建，直接删除联动 |
| `base-mod-compile-libs/irons_spellbooks.jar` | 同上（若存在） |

⚠️ `combat/SpellDamageRegistry.java` 里的 `irons_spellbooks:*` 只是**字符串 id**，
保留无害，不需改动（`build.gradle:195` 已有记录）。

---

## §8 26.1.2 实测 API 断裂清单（`javap` 对 26.1.2.109 工件取证，非推测）

> 取证方式：`unzip` 出 `neoforge-26.1.2/build/moddev/artifacts/minecraft-patched-26.1.2.109-merged.jar`
> 中的目标 class，`javap -p` 读签名；并与 `neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-merged.jar`
> 同名类逐项对比。

### 8.1 `Inventory` —— 字段被删（影响面最广）

| 1.21.1 | 26.1.2 | 处置 |
|---|---|---|
| `public final NonNullList<ItemStack> items` | **`private final`** | 改 `getNonEquipmentItems()`（返回 `NonNullList`，可直接遍历/`.get(i)`/`.size()`） |
| `public final NonNullList<ItemStack> offhand` | **完全不存在** | 副手改用 `SLOT_OFFHAND` 常量 + `EntityEquipment`，或 `getItem(SLOT_OFFHAND)` |

⚠️ 26.1.2 侧**已有本机既定写法**，写在 `item/card/BaseEffectCardItem` 的 javadoc：
> 「背包遍历用 `Inventory#getNonEquipmentItems()`（1.21.1 的 `Inventory#items` 字段已删）」

多个现存文件（`SatelliteChipItem` / `SmartWatchChipItem` / `MimiSignItem` / `NancyLuSignItem` /
`RinSignItem`）都已按此改写 ⇒ **新增代码必须沿用，不得回退到字段访问**。

### 8.2 `DustParticleOptions` —— 构造器与颜色类型变了

| 1.21.1 | 26.1.2 |
|---|---|
| `DustParticleOptions(Vector3f, float)` | **`DustParticleOptions(int, float)`** |
| `public static final Vector3f REDSTONE_PARTICLE_COLOR` | `public static final int REDSTONE_PARTICLE_COLOR` |
| `private final Vector3f color` / `getColor() → Vector3f` | `private final int color` / `getColor() → Vector3f`（方法签名未变，字段变了） |

⇒ 1.21.1 用 `new DustParticleOptions(kind.color /*Vector3f*/, scale)` 的写法**必须**换成打包成
`0xRRGGBB` 的 int。26.1.2 侧的颜色惯例是**直接写十六进制 int**（见 `ColorParticleOption.create(type, 0xFFFFFF)`）。

### 8.3 `ParticleTypes.FLASH` —— 泛型变了

| 1.21.1 | 26.1.2 |
|---|---|
| `SimpleParticleType`（可直接传给 `sendParticles`） | **`ParticleType<ColorParticleOption>`** |

⇒ 必须包一层：`ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF)`。
26.1.2 的 `event/LivingPageFlightScheduler.java:196` 就是这条写法 ——
而 `ShootingStarManager` 恰恰是**照 `LivingPageFlightScheduler` 写的**（其 javadoc 明确写了"复用活体书页的观感与轨迹参数"）
⇒ 移植时照抄这一行即可。

### 8.4 `ServerLevel#sendParticles` —— 可用（仅重载数变了）

26.1.2 保留下来的泛型重载：
```java
public <T extends ParticleOptions> int sendParticles(T, double,double,double, int, double,double,double,double);
```
⇒ 原调用形状不变，**无需改写**（前提是粒子类型本身合规，见 8.3）。

### 8.5 `ResourceLocation` → `Identifier`（已在本轮 effect 移植中验证）

| 1.21.1 | 26.1.2 |
|---|---|
| `net.minecraft.resources.ResourceLocation` | **`net.minecraft.resources.Identifier`** |
| `ResourceLocation.fromNamespaceAndPath(ns, path)` | `Identifier.fromNamespaceAndPath(ns, path)`（方法名同） |

⚠️ 但**并非全仓都换**：`DiceCombatEvents` 用的是 `Identifier.parse(...)`，
而 `BuiltInRegistries.ITEM.get(...)` 在 26.1.2 返回的是 **`Optional<Holder<Item>>` 而非 `Item`**
（见 `DiceCombatEvents` 里 `hasEnigmaticCurse` 的 `.map(Holder::value).orElse(null)`）。
⇒ 凡涉及 `BuiltInRegistries` 查表的写法，**必须逐处核对返回值包装类型**。

---

## §9 依赖波次（实测硬顺序，不可乱序）

```
波1  ModAttachments：58 个访问器 + 28 个 AttachmentType              ✅ 已完成（99=99 键同序 / 299=299 访问器）
波2  11 个 effect + ModEffects 对账 + ModCompatibilityCheck          ✅ 已完成（43=43）
波2' 基础物品：ModItems 12 个缺失注册 + 6 个 sign 类 + Fu/Huo 卡 + ShootingStarChipItem   ← 当前
     ↑ 波2' 是波2 的下游（sign 类构造器要引用 effect 的 Holder）
波3  ModAttachments 下游：移回 parked/ShootingStarManager（访问器已就绪，仅剩粒子 API）
波4  item/card：TemporaryCardUtil / TemporaryCardPermissiveSlot / DragonCardUtil
波5  mixin/container 两条守卫 + astral_dice.mixins.json 登记（+2 条）
波6  economy 6 件套 + network 2 包 + gui/StarCoinWalletButtons + mixin/client/EffectRenderingInventoryScreenMixin
波7  其余：combat/SherryThrowManager + event/TemporaryCardEvents + fixes/{DamageStackSanitizer,NeoForgeFixesLog}
            + client/ClientKeyNames + datagen/DataGenerators
波8  注册表收口：ModItems / ModCreativeTabs / ModPayloads / ModMenuTypes
波9  资源收口：lang ×2 / textures/item / textures/mob_effect / recipes / tags / bountiful 池
波10 EventBusSubscriber 全量核对（用户明确要求，见 §6）
波11 构建四件套验证 + 移植后代码审查
```

### 已暂存待接回的文件

以下 3 个文件已从 1.21.1 移植并做过 API 修复分析，**波1 已解除其中两个的阻塞**，
暂存于 `temp/mig/parked/`（`temp/` 已 gitignore）：

- `combat/ShootingStarManager.java` — ✅ 阻塞已解除（`get/setShootingStarCooldownEnd` 已就绪）；
  仅剩 §8.2 / §8.3 两处粒子 API 待改（`DustParticleOptions(int,float)`、`ParticleTypes.FLASH` 需 `ColorParticleOption`）
- `item/card/DragonCardUtil.java` — ❌ 仍阻塞：需 `MamushiSignItem.ROAR_USES`、
  `ModItems.ATTACK_CARD_DRAGON_ROAR/_BITE`（属波2'）；三处 `Inventory#items` 待改 `getNonEquipmentItems()`
- `combat/EliteTargets.java` — ❌ 仍阻塞：需核 `BossEntityUtil.isBossEntity` 与
  `getPersistentData()` 在 26.1.2 的返回形态（`CompoundTag.getBoolean` 的 `||` 报类型错误）

---

## §10 32 个缺失文件实测清单（按行数降序，2026-09-22）

| 行数 | 路径（`…/astral_dice/` 之后） | 波次 | 备注 |
|---|---|---|---|
| 649 | `item/sign/TeruSignItem.java` | 2' | 辉夜立牌：降神+湖光 |
| 486 | `item/sign/ZhaoSignItem.java` | 2' | 赵立牌：祝福+厄运镜像 |
| 476 | `item/sign/MamushiSignItem.java` | 2' | 蝮蛇立牌：觉醒/真龙形态/连锁反应 |
| 469 | `item/card/TemporaryCardUtil.java` | 4 | 临时牌系统核心 |
| 360 | `item/sign/HannaSignItem.java` | 2' | 汉娜立牌：人偶制作/幻想千金/挚友祝福 |
| 346 | `combat/ShootingStarManager.java` | 3 | 已暂存，粒子 API 待改 |
| 301 | `item/sign/NardisSignItem.java` | 2' | 娜迪丝立牌：威压/女王特权入口 |
| 286 | `item/sign/SherrySignItem.java` | 2' | 雪莉立牌：推理时间 |
| 280 | `client/gui/StarCoinWalletButtons.java` | 6 | 钱包 GUI 控件（注入 InventoryScreen） |
| 242 | `item/card/DragonCardUtil.java` | 4 | 咬击→龙吼转换 |
| 232 | `item/card/HuoCardItem.java` | 2' | 符卡·祸（厄运） |
| 209 | `economy/StarCoinCurrency.java` | 6 | 星币面额口径（1 币=1 / 1 袋=9） |
| 152 | `fixes/DamageStackSanitizer.java` | 7 | 伤害栈净化 |
| 147 | `combat/SherryThrowManager.java` | 7 | 雪莉投掷物 |
| 122 | `item/card/FuCardItem.java` | 2' | 符卡·福 |
| 92 | `economy/StarCoinWalletActions.java` | 6 | 存取意图（服务端判定） |
| 79 | `mixin/fixes/LivingEntityDamageContainerMixin.java` | 7 | fixes 相关 mixin |
| 67 | `economy/StarCoinBalanceSync.java` | 6 | 余额 S2C 每 20 tick 轮询 |
| 66 | `combat/EliteTargets.java` | 7 | 已暂存，NBT 形态待核 |
| 60 | `client/ClientKeyNames.java` | 7 | 按键名 |
| 56 | `event/TemporaryCardEvents.java` | 7 | 临时牌事件 |
| 48 | `mixin/container/SlotPlaceGuardMixin.java` | 5 | ⚠️ 与下条**互为必需** |
| 45 | `mixin/container/ContainerMoveGuardMixin.java` | 5 | ⚠️ `moveItemStackTo` 的合并分支不查 `mayPlace` |
| 43 | `network/StarCoinWalletPayload.java` | 6 | 钱包 C2S 意图序数 |
| 42 | `economy/StarCoinPickupHandler.java` | 6 | 拾取即入钱包 |
| 41 | `network/StarCoinBalancePayload.java` | 6 | 余额 S2C |
| 37 | `mixin/client/EffectRenderingInventoryScreenMixin.java` | 6 | 效果渲染注入 |
| 35 | `fixes/NeoForgeFixesLog.java` | 7 | |
| 31 | `item/chip/ShootingStarChipItem.java` | 2' | 紫/金飞星共用一个类 |
| 29 | `datagen/DataGenerators.java` | 7 | 已被 `Client/ServerDataGenerators` 取代 ⇒ **可能只需确认** |
| 28 | `event/IronSpellbooksCompat.java` | — | ❌ **按用户要求不移植** |
| 19 | `item/card/TemporaryCardPermissiveSlot.java` | 4 | |

**合计 5575 行；扣除 Iron 的 28 行 ⇒ 待移植 ~5547 行。**

---

## §11 半移植造成的「既有文件静默缺陷」（本轮新发现，比缺文件更隐蔽）

⚠️ 26.1.2 不是"少了一批文件"，还有一批**已存在但被削掉功能**的文件 —— 它们编译通过、注册数量也够，
但功能残缺，**只有把新移植的类接上去时才会暴露**。实测已确认 3 处：

### 11.1 `item/sign/BaseSignItem.java`：缺 4 个可覆写钩子（585 → 465 行，−120）

| 缺失方法 | 后果 |
|---|---|
| `canBeginSelectorSession(Player)` | 「选择器类」立牌的**前置拒绝**钩子整体消失 ⇒ 教主立牌「降神生效中不可重复施放」失效；`TeruSignItem` 覆写它直接报 `@Override` 错 |
| `activeCooldownBaseTicks(Player)` | 立牌主动冷却基准的覆写点消失 ⇒ `SherrySignItem` 覆写报错 |
| `forcedActiveCooldownTicks()` | 蛟龙立牌「连锁反应」**强制冷却硬闸门**失效（诡异骰子 −50% 会照常生效）|
| `forcedCooldownUntil(Player)` | 同上；且附件 `MAMUSHI_FORCED_COOLDOWN_UNTIL` 虽已移植却**无人读取** |

⚠️ 注意：`MamushiSignItem` / `SherrySignItem` 在 26.1.2 **已存在**（不是本轮新移植的），
却在覆写这些方法 —— 说明 26.1.2 是**半移植**状态：sign 类搬过来了、基类契约没搬。

### 11.2 `combat/DiceCombatModifiers.java`：缺 4 个方法（722 → 591 行）

| 缺失方法 | 后果 |
|---|---|
| `attackPowerOf(Player)` | 教主立牌降神「目标攻击力 50%」「狐光攻击基数」全部无法取数 |
| `defensePowerOf(Player)` | 同上（防御力侧）|
| `attackPowerBase(...)` / `defensePowerBase(...)` | 连带缺失；26.1.2 的 `getDisplayAttackRange/DefenseRange` 把 base 计算**内联**了，破坏了 1.21.1 的「显示与结算同源」契约 |

### 11.3 `item/sign/FenSignItem.java`：缺 `addRecharge(Player,int)`

赵立牌「完美帮手」（对装备大当家立牌者施加白泽赐福时给 1 层养精蓄锐）依赖它。

### 11.4 `combat/SpellDamageRegistry.java`：缺 `ALLOW_FIREARM_DAMAGE` 配置闸门

26.1.2 写成 `if (isFirearmDamage(source)) return false;`，丢了 1.21.1 的
`if (!GameplayConstants.ALLOW_FIREARM_DAMAGE && isFirearmDamage(source)) return false;`
⇒ `allow_firearm_damage` 配置完全失效（军火类伤害恒被排除）。

### 11.5 API 形变：`Entity#getServer()` 在 26.1.2 已删

1.21.1 用 `player.getServer().getPlayerList()`；26.1.2 必须走 **`player.level().getServer()`**
（既有先例：`item/chip/BigBowlStewChipItem:97`）。

### 11.6 API 形变：`ModItems.registerItem` 形态不同（**不可逐字复制**）

| | 形态 |
|---|---|
| 1.21.1 | `registerItem(String, Supplier<T>)`，属性写 `new Item.Properties()` |
| 26.1.2 | `registerItem(String, Function<Item.Properties, ? extends T>)`，属性写传进来的 `props` |

原因（26.1.2 的 javadoc 已写明）：`Item` 构造器经 `Item.Properties#itemIdOrThrow` 推导默认描述 id，
自己 `new Item.Properties()` 会触发 `NullPointerException: Item id not set`。
⇒ 新增物品一律 `props -> new XxxItem(props...)`，**且**属性链里的 `new Item.Properties()` 要换成 `props`。
本轮 12 个新增物品全部按此改写（已用脚本批量转换并核对）。


