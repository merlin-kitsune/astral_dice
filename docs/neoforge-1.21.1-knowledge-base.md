# Minecraft 1.21.1 + NeoForge 21.1.235 模组开发知识库笔记（astral_dice 专用）

> 调研日期：本次会话。所有 Curios / KubeJS 部分均通过对**本项目 Gradle 缓存中实际依赖 jar** 的 `javap` 反编译与源码阅读逐条验证（权威性高于网络教程）；NeoForge 部分以官方文档 + 1.21.1 javadoc 为准。
> 项目基线（来自 `gradle.properties` / `build.gradle`）：Minecraft `1.21.1`、NeoForge `21.1.235`、Java 21、ModDevGradle `2.0.141`、Parchment `2024.11.17`；Curios = `maven.modrinth:curios:yohfFbgD`（即 Curios **9.x for 1.21.1**）；KubeJS = `dev.latvian.mods:kubejs-neoforge:2101.7.2-build.372`（运行时自动化测试用）。

---

## 目录

1. [NeoForge 1.21.1 事件系统](#1-neoforge-1211-事件系统)
2. [Data Component（数据组件，1.21 重大变化）](#2-data-component数据组件121-重大变化)
3. [Data Attachment（附件，玩家数据存储）](#3-data-attachment附件玩家数据存储)
4. [Curios API 9.x（1.21.1，已对 jar 验证）](#4-curios-api-9x1211已对-jar-验证)
5. [NeoForge 网络（Payload）API](#5-neoforge-网络payload-api)
6. [数据生成（Data Generation）](#6-数据生成data-generation)
7. [菜单与屏幕（Menu / Screen）](#7-菜单与屏幕menu--screen)
8. [状态效果（MobEffect）注册与属性修改](#8-状态效果mobeffect注册与属性修改)
9. [战斗/伤害事件（1.21.1 重点）](#9-战斗伤害事件1211-重点)
10. [Mod 事件与 NeoForge 事件的区别（IModBusEvent）](#10-mod-事件与-neoforge-事件的区别imbusevent)
11. [DamageType 注册与 DamageSources](#11-damagetype-注册与-damagesources)
12. [ModDevGradle 2.x 用法](#12-moddevgradle-2x-用法)
13. [KubeJS 7（1.21.1 / 2101.x）](#13-kubejs-71211--2101x)
14. [所有参考 URL](#14-所有参考-url)

---

## 1. NeoForge 1.21.1 事件系统

### 1.1 双事件总线（与 1.20.x 的核心差异）

NeoForge 从 1.20.2 起将 Forge 的"单事件总线 + 分类"重构为**两条独立总线**：

| | Mod 事件总线（Mod Event Bus） | NeoForge 事件总线（Game Bus） |
|---|---|---|
| 获取方式 | `@Mod` 类构造器注入 `IEventBus` | `NeoForge.EVENT_BUS`（静态单例） |
| 事件类型 | 实现 `IModBusEvent` 的注册/生命周期事件 | 游戏内常规事件 |
| 典型事件 | `RegisterEvent`、`RegisterMenuScreensEvent`、`RegisterPayloadHandlersEvent`、`GatherDataEvent`、`FMLCommonSetupEvent`、`FMLClientSetupEvent`、`RegisterCapabilitiesEvent`（见注） | `LivingDamageEvent`、`PlayerEvent`、`ServerStartedEvent`、`EntityJoinLevelEvent` 等 |
| 订阅注解 | `@EventBusSubscriber(modid=..., bus = EventBusSubscriber.Bus.MOD)` | `@EventBusSubscriber(modid=...)`（默认 GAME）或手动 `NeoForge.EVENT_BUS.addListener(...)` |

> 注：`RegisterCapabilitiesEvent` 属于 **NeoForge 总线**（游戏总线），不要放在 MOD 总线上。Curios 的饰品能力注册也用它。

### 1.2 标准骨架（1.21.1 MDK 结构）

```java
// 主类：构造器注入 mod 事件总线（1.20.x 是 FMLJavaModLoadingContext.get().getModEventBus()，1.21 改为注入）
@Mod(AstralDice.MODID)
public class AstralDice {
    public static final String MODID = "astral_dice";
    public AstralDice(IEventBus modEventBus) {
        // DeferredRegister 全部挂到 mod 总线
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.DATA_COMPONENT_TYPES.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModEffects.MOB_EFFECTS.register(modEventBus);
        // 游戏总线监听（也可用 @EventBusSubscriber）
        NeoForge.EVENT_BUS.register(this);
    }
}

// 事件订阅者类（游戏总线）
@EventBusSubscriber(modid = AstralDice.MODID)
public class GameEvents {
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) { }
}

// 事件订阅者类（mod 总线）
@EventBusSubscriber(modid = AstralDice.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModEvents {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) { }
}
```

### 1.3 DeferredRegister 与 RegisterEvent

```java
public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AstralDice.MODID);
    public static final DeferredHolder<Item, Item> SOLAR_DIE =
            ITEMS.register("solar_die", () -> new Item(new Item.Properties().stacksTo(1)));
}
```

- `DeferredRegister.createItems(modid)` 是 1.21 的便捷工厂（等价 `create(Registries.ITEM, modid)`）；`DeferredRegister.createBlock(...)` 同理。
- `RegisterEvent`（mod 总线）用于**动态**注册 vanilla registry 条目（如动态 `DataComponentType`、`RecipeSerializer` 等），签名：`event.register(Registries.X, helper -> helper.register(id, value))`。
- `DeferredHolder<R, T>`：1.21 起 `RegistryObject` 更名为 `DeferredHolder`；`holder.get()` / `holder.getId()`。

### 1.4 与 1.20.x 的差异清单

- 包名 `net.minecraftforge.*` → `net.neoforged.neoforge.*`（如 `net.neoforged.neoforge.event.entity.living.*`）。
- Forge 的 `MinecraftForge.EVENT_BUS` → `NeoForge.EVENT_BUS`。
- mod 事件总线不再用 `FMLJavaModLoadingContext` 获取，改为构造器注入。
- 事件优先使用 `@SubscribeEvent` 的静态方法 + `@EventBusSubscriber`；`IEventBus.addListener(Consumer)` 仍可用。
- 1.21.1 中 `@EventBusSubscriber` 默认 `bus = Bus.GAME`。

**权威来源**：
- [NeoForged docs — Events](https://docs.neoforged.net/docs/concepts/events/)
- [NeoForged docs — Getting Started (1.21.1)](https://docs.neoforged.net/docs/1.21.1/gettingstarted/)
- [NeoForged docs — Mod Files (1.21.1)](https://docs.neoforged.net/docs/1.21.1/gettingstarted/modfiles/)
- [MCBBS Wiki 中文 1.21.1 事件教程](https://mcbbs.wiki/index.php?title=%E7%94%A8%E6%88%B7:MashKJo/1.21.1%E6%A8%A1%E7%BB%84%E5%BC%80%E5%8F%91%E6%95%99%E7%A8%8B/2.%E4%BA%8B%E4%BB%B6)

---

## 2. Data Component（数据组件，1.21 重大变化）

### 2.1 背景：从 NBT 到 Data Component

1.20.5 引入、1.21 定型的物品数据体系。**物品栈数据不再用 `CompoundTag` 直接读写**，而是由"组件"（Component）组成：

- `DataComponentMap`：**不可变**的组件集合（如 `ItemStack.getComponents()`、`Item.Properties` 内默认组件）。
- `DataComponentPatch`：**增量补丁**（`getComponentsPatch()`），描述相对默认值的增/删/改，用于网络同步与保存。
- `ItemStack` 常用 API：`stack.get(componentType)`、`stack.set(componentType, value)`、`stack.update(componentType, value, updateFunction)`、`stack.remove(componentType)`、`stack.has(componentType)`、`stack.copyWithCount(n)`。
- 内置组件都在 `DataComponents` 类（如 `DataComponents.CUSTOM_DATA`、`DataComponents.ATTRIBUTE_MODIFIERS`、`DataComponents.MAX_STACK_SIZE`、`DataComponents.FOOD`）。

```java
// 读取/写入自定义组件
ItemStack stack = ...;
Integer v = stack.get(ModDataComponents.EXAMPLE_INT.get());   // null 表示不存在
stack.set(ModDataComponents.EXAMPLE_INT.get(), 42);
stack.update(ModDataComponents.EXAMPLE_INT.get(), 0, old -> old + 1);
boolean has = stack.has(ModDataComponents.EXAMPLE_INT.get());
```

### 2.2 自定义 ComponentType 注册（1.21.1 官方推荐：DeferredRegister）

```java
public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AstralDice.MODID);

    // 持久化(存档) + 网络同步(发包) 都需要，否则对应场景会丢数据
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> EXAMPLE_INT =
            DATA_COMPONENT_TYPES.register("example_int", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)                    // 存档编解码
                    .networkSynchronized(ByteBufCodecs.VAR_INT) // 网络同步
                    .build());

    // 只存档不同步
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> EXAMPLE_TAG =
            DATA_COMPONENT_TYPES.register("example_tag", () -> DataComponentType.<CompoundTag>builder()
                    .persistent(CompoundTag.CODEC)
                    .build());
}
```

> 要点：
> - 1.21.1 的 builder 方法是 `.persistent(Codec)` 与 `.networkSynchronized(StreamCodec)`（**1.21.2+ 改名为 `.codec()` / `.streamCodec()`**，升级时注意）。
> - 若某组件注册时序遇到冻结问题（极少数情况），可改用 mod 总线 `RegisterEvent` + `event.register(Registries.DATA_COMPONENT_TYPE, helper -> ...)`。
> - 给物品**默认组件**：`new Item.Properties().component(ModDataComponents.EXAMPLE_INT.get(), 7)`，或在 `Item` 构造里用 `Item.Properties` 的 `.component(...)`。

### 2.3 与 1.20.x 的差异（重点）

- `stack.getOrCreateTag()` / `stack.getTag()` / `stack.setTag(CompoundTag)` **已弃用**，语义被组件取代；自定义数据应放自定义 `DataComponentType`（`DataComponents.CUSTOM_DATA` 保留给玩家/旧数据兼容，但不推荐新增业务）。
- `ItemStack` 是**不可变数据 + 可拷贝**的：`stack.set(...)` 返回新栈（1.20.5+ 已是 copy-on-write，1.21 进一步强化），不要假设原地修改。
- `AttributeModifier` 的 id 从 `UUID` 改为 `ResourceLocation`（见 §8）。
- 注册表键：`Registries.DATA_COMPONENT_TYPE`（1.21 引入）。

**权威来源**：
- [NeoForged docs — Data Components (1.21.1)](https://docs.neoforged.net/docs/1.21.1/items/datacomponents/)
- [NeoForged docs — Data Components（中文镜像）](https://zh-neoforge.netlify.app/docs/1.21.1/items/datacomponents/)

---

## 3. Data Attachment（附件，玩家数据存储）

### 3.1 概念

NeoForge 的 **Attachment（附件）** 系统用于给 `IAttachmentHolder` 实现者挂自定义数据，**替代 Forge 时代"Capability + 存储 NBT"的玩家/实体数据方案**。1.21.1 中 `IAttachmentHolder` 的实现者：`Entity`（含 `Player`）、`Level`、`BlockEntity`、`ChunkAccess`、`ItemStack`（**注意：1.21.1 的 ItemStack 不实现 IAttachmentHolder**，物品数据用 Data Component，见 [NeoForge issue #1630](https://github.com/neoforged/NeoForge/issues/1630)）。

### 3.2 注册 AttachmentType

```java
public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AstralDice.MODID);

    // 必须提供默认值工厂；serialize 提供 Codec 才会存档
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> MANA =
            ATTACHMENT_TYPES.register("mana", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)          // 存档（1.21.1 为 serialize(Codec)；1.21.6+ 改为 map codec 形式）
                    .copyOnDeath()                 // 死亡不掉落/保留（对玩家死亡时保留数据）
                    .build());
}
```

### 3.3 读写 API

```java
// 直接挂在玩家/实体/Level/BlockEntity 上
Player player = ...;
player.setData(ModAttachments.MANA, 100);        // 写
int mana = player.getData(ModAttachments.MANA);  // 读（无则返回默认值）
boolean has = player.hasData(ModAttachments.MANA);
player.removeData(ModAttachments.MANA);
```

- 对 `Level`：`level.getData(...)` / `level.setData(...)`。
- 换维度/重生：默认附件会随实体持久化（有 Codec 时）；`copyOnDeath()` 控制死亡保留；跨存档/维度同步见下。
- **客户端同步：1.21.1 的 Attachment 不会自动同步到客户端**（无 `sync` 配置项；NeoForge 的自动同步到 1.21.6+ 才完善，且 [issue #2510](https://github.com/neoforged/NeoForge/issues/2510) 记录过换维度不同步的 bug）。**需要客户端可见的数据请自己用 §5 的 payload 网络同步**，或改用 Data Component + 物品栈天然同步。

### 3.4 与 1.20.x 的差异

- Forge 的 `Capability<T>` + `ICapabilityProvider` + `AttachCapabilitiesEvent` 存储玩家数据的经典方案 → 1.21 官方推荐 **Attachment**（简单、类型安全、自带默认值与存档 Codec）。
- 附件注册表：`NeoForgeRegistries.ATTACHMENT_TYPES`（NeoForge 自有注册表，用 DeferredRegister 注册，随 mod 总线挂载）。
- 注意与 **Data Component** 的分工：物品上的数据 → Component；实体/世界/方块实体上的数据 → Attachment；ItemStack 附件在 1.21.1 不可用。

**权威来源**：
- [NeoForged docs — Data Attachments](https://docs.neoforged.net/docs/1.21.8/datastorage/attachments/)（版本锚点页面结构同 1.21.1；示例 API 以 1.21.1 javadoc 为准）
- [NeoForge issue #1630 — ItemStack 不实现 IAttachmentHolder](https://github.com/neoforged/NeoForge/issues/1630)
- [NeoForge issue #2510 — 附件维度切换不同步](https://github.com/neoforged/NeoForge/issues/2510)

---

## 4. Curios API 9.x（1.21.1，已对 jar 验证）

> 本节全部签名来自项目依赖 `maven.modrinth:curios:yohfFbgD`（Curios 9.x，1.21.1）jar 的 `javap` 反编译结果，保证与项目实际一致。

### 4.1 依赖与仓库（项目已配置）

```groovy
repositories {
    maven { url 'https://api.modrinth.com/maven' }   // Curios 在 Modrinth Maven
}
dependencies {
    implementation 'maven.modrinth:curios:yohfFbgD'  // Curios 9.x for 1.21.1
}
```

### 4.2 核心类（jar 验证的权威包结构）

| 类 | 说明 |
|---|---|
| `top.theillusivec4.curios.api.CuriosApi` | 主入口：`getCuriosInventory(LivingEntity)`、`registerCurio(Item, ICurioItem)`、`registerCurioPredicate(ResourceLocation, Predicate<SlotResult>)` |
| `top.theillusivec4.curios.api.CuriosCapability` | 能力常量：`INVENTORY`、`ITEM_HANDLER`、`ITEM` |
| `top.theillusivec4.curios.api.type.capability.ICurio` | 完整饰品接口（大量 default 方法：`equip/unequip/curioTick/canEquip/canUnequip/getAttributeModifiers/getDropRule/getEquipSound/canEquipFromUse/writeSyncData` 等） |
| `top.theillusivec4.curios.api.type.capability.ICurioItem` | 饰品物品包装接口（`defaultInstance`、`onEquip/onUnequip/curioTick/canEquip/...`，实现它后经 `CuriosApi.registerCurio` 使用） |
| `top.theillusivec4.curios.api.type.capability.ICuriosItemHandler` | 玩家 Curio 总控：`getCurios()`、`getStacksHandler(String)`、`findFirstCurio(...)`、`findCurios(...)`、`isEquipped(...)`、`getEquippedCurios()` |
| `top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler` | 单槽位组：`getStacks()`、`getCosmeticStacks()`、`getSlots()`、`getIdentifier()`、`getModifiers()` |
| `top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler` | 动态栈处理器：`getStackInSlot(i)` / `setStackInSlot(i, stack)` / `getSlots()` |
| `top.theillusivec4.curios.api.SlotContext` | 槽位上下文（`entity()`、`identifier()`、`index()`、`amount()` 等） |
| `top.theillusivec4.curios.api.SlotResult` | 查找结果（`slotContext()`、`stack()`） |
| `top.theillusivec4.curios.api.SlotTypePreset` | 内置槽位预设（`RING`、`NECKLACE`、`BACK`、`BELT`、`BODY`、`BRACELET`、`HEAD`、`HANDS`、`CHARM`、`CURIO` 等） |
| `top.theillusivec4.curios.api.CuriosDataProvider` | Datagen 用：生成 `curios/slots/*.json` 与 `curios/entities/*.json` |
| `top.theillusivec4.curios.api.CuriosTags` | 标签常量（如 `CuriosTags.Items.RING`） |

`CuriosCapability` 的精确能力类型（javap 原文）：

```java
public static final EntityCapability<ICuriosItemHandler, Void> INVENTORY;   // 实体 -> Curio 总控
public static final EntityCapability<IItemHandler, Void> ITEM_HANDLER;      // 实体 -> 统一 IItemHandler 视图
public static final ItemCapability<ICurio, Void> ITEM;                      // 物品栈 -> ICurio
```

### 4.3 让物品成为饰品（1.21.1 推荐方式 A：RegisterCapabilitiesEvent）

```java
// 游戏总线（NeoForge.EVENT_BUS）上的 RegisterCapabilitiesEvent
@SubscribeEvent
public static void registerCapabilities(RegisterCapabilitiesEvent event) {
    event.registerItem(
            CuriosCapability.ITEM,                     // ItemCapability<ICurio, Void>
            (stack, context) -> new ICurio() {
                @Override
                public void curioTick(SlotContext slotContext, ItemStack stack) {
                    // 每 tick 逻辑
                }
                @Override
                public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) { }
                @Override
                public boolean canEquip(SlotContext slotContext, ItemStack stack) { return true; }
                // 属性：getAttributeModifiers(SlotContext, ResourceLocation, ItemStack)
            },
            ModItems.SOLAR_DIE.get());                 // 指定物品类
}
```

### 4.4 方式 B：`CuriosApi.registerCurio(Item, ICurioItem)`

```java
// 适合不想包一层 ICurio 的简单情况（jar 验证的签名）
CuriosApi.registerCurio(ModItems.SOLAR_DIE.get(), new ICurioItem() {
    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) { }
});
```

### 4.5 槽位（Slot）注册：JSON 数据包（官方推荐）

Curios 槽位是**数据驱动**的，放在 `data/` 下（可选 `curios` 命名空间为 Curios 自带）：

`src/main/resources/data/astral_dice/curios/slots/star.json`（定义新槽位类型）：

```json
{
  "size": 2,
  "icon": "curios:slot/empty_curio_slot",
  "useNativeGui": true,
  "operation": "SET",
  "dropRule": "DEFAULT",
  "order": 1000
}
```

`src/main/resources/data/astral_dice/curios/entities/player.json`（给玩家分配槽位）：

```json
{
  "entities": ["player"],
  "slots": ["ring", "necklace", "star"]
}
```

- 也可以用 `SlotTypePreset` 的内置槽位名（`"ring"`、`"necklace"` 等），无需自建 JSON。
- 想用 Datagen 生成这些 JSON → 继承 `CuriosDataProvider`（jar 验证存在）。

### 4.6 读取玩家 Curio 槽位中的物品（核心业务）

```java
// 推荐入口 1：CuriosApi.getCuriosInventory
CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
    // 按槽位组读取
    handler.getStacksHandler("ring").ifPresent(stacksHandler -> {
        IDynamicStackHandler stacks = stacksHandler.getStacks();  // 实际物品槽
        for (int i = 0; i < stacks.getSlots(); i++) {
            ItemStack curio = stacks.getStackInSlot(i);
            if (curio.is(ModItems.SOLAR_DIE)) { /* 命中 */ }
        }
    });
    // 或直接查找
    Optional<SlotResult> result = handler.findFirstCurio(ModItems.SOLAR_DIE.get());
    result.ifPresent(r -> { ItemStack stack = r.stack(); SlotContext ctx = r.slotContext(); });
    boolean equipped = handler.isEquipped(ModItems.SOLAR_DIE.get());
});

// 推荐入口 2：NeoForge 能力 API 直接取（等价）
ICuriosItemHandler handler = player.getCapability(CuriosCapability.INVENTORY);
```

> 自动化测试（KubeJS 脚本）中判断"玩家是否佩戴某饰品"的最简思路：按槽位组遍历 `getStacks()` 或使用 `findFirstCurio(predicate)`。

### 4.7 Curios 事件（jar 验证：9.x 事件类清单，全部挂在 NeoForge 游戏总线）

| 事件类（`top.theillusivec4.curios.api.event`） | 触发时机 | 备注 |
|---|---|---|
| `CurioChangeEvent` | 槽位物品**变化后** | 常用：`getSlotId()`、`getSlotType()`、`getFrom()`、`getTo()` |
| `CurioCanEquipEvent` | **穿戴前**校验 | **可取消**（`event.setCanceled(true)` 阻止穿戴） |
| `CurioCanUnequipEvent` | **卸下前**校验 | **可取消** |
| `CurioAttributeModifierEvent` | 计算饰品属性时 | 修改/新增属性修改器 |
| `CurioDropsEvent` | 饰品掉落时 | 控制掉落 |
| `DropRulesEvent` | 槽位掉落规则变化 | 少见 |
| `SlotModifiersUpdatedEvent` | 槽位修饰符更新 | 少见 |

```java
@EventBusSubscriber(modid = AstralDice.MODID)   // 默认 GAME 总线
public class CurioEvents {
    @SubscribeEvent
    public static void onCurioChange(CurioChangeEvent event) {
        // 玩家 event.getEntity()；槽位 event.getSlotId()；旧/新栈 event.getFrom()/event.getTo()
    }

    @SubscribeEvent
    public static void onCanEquip(CurioCanEquipEvent event) {
        if (event.getStack().is(ModItems.FORBIDDEN.get())) event.setCanceled(true);
    }
}
```

### 4.8 与 1.20.x 的差异（重点）

- **`CurioEquipEvent` / `CurioUnequipEvent` 在 9.x 已不存在**（jar 验证），被 **`CurioCanEquipEvent` / `CurioCanUnequipEvent`** 取代（语义：穿戴前可取消校验）。
- 1.20.x 用 Forge `Capability` 体系注册 curio（`registerItem(CuriosCapability.ITEM, ...)` 仍在），1.21 底层换成 **NeoForge `ItemCapability` / `EntityCapability`**（`CuriosCapability.ITEM` 现在是 `ItemCapability<ICurio, Void>`）。
- `getAttributeModifiers` 的 id 参数从 `UUID` 版过渡为 `ResourceLocation` 版（两者签名并存，见 javap；1.21 属性系统以 ResourceLocation 为主）。
- 槽位注册仍以 JSON 数据包为准（跨版本稳定），`CuriosDataProvider` 用于 datagen。

**权威来源**：
- [Curios 官方 Wiki — Creating a Curio](https://docs.illusivesoulworks.com/curios/items/curio-creation)
- [Curios 官方 Wiki — Basic Inventory Management](https://docs.illusivesoulworks.com/1.20.x/curios/inventory/basic-inventory)
- [SSKirillSS/Curios 仓库（1.21.1 分支 README）](https://github.com/SSKirillSS/Curios/blob/1.21.1/README.md)
- [Modrinth — Curios 9.5.0+1.21.1](https://modrinth.com/mod/curios/version/9.5.0+1.21.1)
- [MC百科 — Curios API](https://www.mcmod.cn/class/2029.html)

---

## 5. NeoForge 网络（Payload）API

### 5.1 注册通道（`RegisterPayloadHandlersEvent`，**mod 总线**）

```java
@EventBusSubscriber(modid = AstralDice.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModNetwork {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(AstralDice.MODID).versioned("1");
        registrar.playToServer(MyPayload.TYPE, MyPayload.STREAM_CODEC, MyPayload::handle);
        registrar.playToClient(MyPayload.TYPE, MyPayload.STREAM_CODEC, MyPayload::handle);
        // 还可用 playToServer(..., IPayloadHandler) / playToClient(..., IPayloadHandler) 或 common(...)
    }
}
```

### 5.2 Payload 定义（record + StreamCodec）

```java
public record MyPayload(int data) implements CustomPacketPayload {
    public static final Type<MyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AstralDice.MODID, "my_payload"));

    public static final StreamCodec<ByteBuf, MyPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, MyPayload::data, MyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 服务端/客户端主线程逻辑；先判断 context.flow() 是 SERVERBOUND/CLIENTBOUND 再强转
            if (context.flow() == NetworkDirection.PLAY_TO_SERVER) {
                Player player = context.player();   // IPayloadContext#player() 1.21.1 提供
                // ...
            }
        });
    }
}
```

### 5.3 发送

```java
// 客户端 -> 服务端
PacketDistributor.sendToServer(new MyPayload(42));

// 服务端 -> 指定玩家
PacketDistributor.sendToPlayer(player, new MyPayload(42));
// 服务端 -> 追踪某实体的所有玩家
PacketDistributor.sendToPlayersTrackingEntity(entity, new MyPayload(42));
```

### 5.4 与 1.20.x 的差异

- 1.20.1 的 `SimpleChannel` + `NetworkRegistry.newSimpleChannel` + `sendToServer`/`send` 体系在 NeoForge 1.20.2+ 被 **Payload + StreamCodec** 取代；1.21.1 完全使用 `CustomPacketPayload`。
- 编解码器从 `FriendlyByteBuf` 手写读写 → **`StreamCodec` 声明式组合**（`ByteBufCodecs` 提供基础编解码器，`StreamCodec.composite(...)` 组合，`ByteBufCodecs.fromCodec(Codec)` 可复用 Codec）。
- 业务逻辑必须包在 `context.enqueueWork(...)` 里切回主线程；响应包用 `context.reply(...)`。

**权威来源**：
- [NeoForged docs — Networking / Payloads](https://docs.neoforged.net/docs/networking/payload/)
- [1.21.x NeoForge javadoc — PayloadRegistrar](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/neoforged/neoforge/network/registration/PayloadRegistrar.html)

---

## 6. 数据生成（Data Generation）

### 6.1 入口（`GatherDataEvent`，**mod 总线**）

```java
@EventBusSubscriber(modid = AstralDice.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModDataGen {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper helper = event.getExistingFileHelper();

        generator.addProvider(event.includeServer(), new ModRecipeProvider(output));
        generator.addProvider(event.includeServer(), new ModItemModelProvider(output, helper));
        // 也常用：LootTableProvider、BlockStateProvider、TagsProvider、AdvancementProvider、CuriosDataProvider
    }
}
```

### 6.2 RecipeProvider（1.21 变化：`RecipeOutput`）

```java
public class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output) { super(output); }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SOLAR_DIE.get())
                .pattern("ABA")
                .pattern("BCB")
                .pattern("ABA")
                .define('A', Items.GOLD_INGOT)
                .define('B', Items.DIAMOND)
                .define('C', Items.NETHER_STAR)
                .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
                .save(output);
    }
}
```

> **1.20.5+ 变化**：`RecipeProvider` 所有 builder 的 `.save(...)` 参数从 `Consumer<FinishedRecipe>` 变为 **`RecipeOutput`**；`has(ItemLike)` 等工具方法仍在 `RecipeProvider` 继承链中。

### 6.3 ItemModelProvider（NeoForge 客户端模型生成器）

```java
public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper helper) {
        super(output, AstralDice.MODID, helper);
    }
    @Override
    protected void registerModels() {
        basicItem(ModItems.SOLAR_DIE.get());      // 简单方块/物品模型
        // 1.21.1 还可用 withExistingParent / getBuilder(...).parent(...).texture(...) 等
    }
}
```

### 6.4 运行

- 项目 build.gradle 已配置 `data` run：`./gradlew runData`（`--mod astral_dice --all --output src/generated/resources/ --existing src/main/resources/`）。
- 生成物输出到 `src/generated/resources/`（build.gradle 已把该目录加入 `sourceSets.main.resources`）。
- 只跑某个 provider：命令行 `--mod astral_dice --all` 里可换成具体 provider 名（`--output`、`--existing` 配合）。

**权威来源**：
- [NeoForged docs — Model Datagen (1.21.1)](https://docs.neoforged.net/docs/1.21.1/resources/client/models/datagen/)
- [中文教程 — 1.21 NeoForge 数据生成](https://beishanair.github.io/2025/08/19/nf121/datagen/)

---

## 7. 菜单与屏幕（Menu / Screen）

### 7.1 注册 MenuType（1.21.1）

```java
public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, AstralDice.MODID);

    // 1.21.1：MenuType 直接接收 MenuSupplier（MyMenu::new 对应 (int, Inventory) 构造器）
    public static final DeferredHolder<MenuType<?>, MenuType<MyMenu>> MY_MENU =
            MENUS.register("my_menu", () -> new MenuType<>(MyMenu::new));
}
```

> ⚠️ **版本差异**：1.21.1 的 `MenuType` 构造器为 `MenuType(MenuSupplier<T>)`；**1.21.2+ 签名改为 `MenuType(Supplier<MenuSupplier<T>>)`**（`new MenuType<>(() -> MyMenu::new)`）。升级 MC 版本时务必核对。

### 7.2 菜单类

```java
public class MyMenu extends AbstractContainerMenu {
    public static final int SLOT_COUNT = 27;

    public MyMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, playerInventory.player.level().getBlockEntity(...)); // 或 SimpleMenuProvider 打开时传入
    }

    // 服务端构造（带 BlockEntity 数据）
    public MyMenu(int containerId, Inventory playerInventory, ContainerData data) {
        super(ModMenus.MY_MENU.get(), containerId);
        // addSlot(new SlotItemHandler(...)) 等
        addDataSlots(data);   // ContainerData 同步 int 数据（如进度/能量）
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { /* 转移逻辑 */ return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) { return true; }
}
```

- 数据同步：**`ContainerData`（`addDataSlots`）** 同步整数；复杂/自定义数据用 §5 payload。
- 打开菜单：服务端 `player.openMenu(new SimpleMenuProvider((id, inv, p) -> new MyMenu(id, inv, data), Component.literal("My Menu")));`（`MenuProvider` 的 `getMenu` 返回菜单实例）。

### 7.3 屏幕注册（`RegisterMenuScreensEvent`，**mod 总线**）

```java
@EventBusSubscriber(modid = AstralDice.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModScreens {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MY_MENU.get(), MyScreen::new);
    }
}

public class MyScreen extends AbstractContainerScreen<MyMenu> {
    public MyScreen(MyMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // 1.20.4+ 用 GuiGraphics（不再有 PoseStack 直接参数）；blit(...)
    }
}
```

> 1.20.4/1.21 差异：渲染回调用 `GuiGraphics`；`Screen`/`AbstractContainerScreen` 位置与 1.20.x 相同（`net.minecraft.client.gui.screens`）。

**权威来源**：
- [NeoForged docs — Menus (1.21.1)](https://docs.neoforged.net/docs/1.21.1/gui/menus/)
- [NeoForge GUI 开发完全指南（中文）](https://doc.ideafox.top/docs/a-dai/mods-dev/gui_guide)

---

## 8. 状态效果（MobEffect）注册与属性修改

### 8.1 注册

```java
public class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, AstralDice.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> STARLIGHT =
            MOB_EFFECTS.register("starlight", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x8a2be2) {
                // 可选覆写：applyEffectTick / shouldApplyEffectTickThisTick
            });
}
```

- `MobEffectCategory.BENEFICIAL / HARMFUL / NEUTRAL`，颜色为 RGB int。
- 1.21.1 需要在 mods.toml / datagen 中给效果提供 `mob_effect` 模型贴图（`assets/<ns>/textures/mob_effect/*.png`）。

### 8.2 自定义属性修改（1.21 签名变化）

```java
public static final ResourceLocation STARLIGHT_MODIFIER =
        ResourceLocation.fromNamespaceAndPath(AstralDice.MODID, "starlight.speed");

new MobEffect(MobEffectCategory.BENEFICIAL, 0x8a2be2) {
    @Override
    public void addAttributeModifiers(AttributeMap attributeMap, int amplifier, MobEffectInstance effectInstance) {
        // 1.20.x 签名是 addAttributeModifiers(LivingEntity, AttributeMap, int)；1.21 改为 (AttributeMap, int, MobEffectInstance)
        attributeMap.addTransientAttributeModifiers(Attributes.MOVEMENT_SPEED,
                new AttributeModifier(STARLIGHT_MODIFIER, 0.1 * (amplifier + 1),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    @Override
    public void removeAttributeModifiers(AttributeMap attributeMap, int amplifier, MobEffectInstance effectInstance) {
        attributeMap.removeAttributeModifiers(Attributes.MOVEMENT_SPEED, STARLIGHT_MODIFIER);
    }
};
```

> **1.21 差异（重点）**：
> - `MobEffect.addAttributeModifiers` 参数从 `(LivingEntity, AttributeMap, int)` 变为 **`(AttributeMap, int, MobEffectInstance)`**。
> - `AttributeModifier` 构造器改用 **`ResourceLocation` 作 id**（`new AttributeModifier(ResourceLocation, double, Operation)`），替代 1.20.x 的 UUID 版本。
> - 属性引用用 `Holder<Attribute>`（`Attributes.MOVEMENT_SPEED` 直接是 `Holder`），`AttributeMap.addTransientAttributeModifiers(Holder<Attribute>, AttributeModifier)`。
> - 若在物品上配属性：`Item.Properties().attributes(...)` 或 `DataComponents.ATTRIBUTE_MODIFIERS`（JSON 为 `attribute_modifiers` 组件）。

**权威来源**：
- [NeoForged docs — Mob Effects & Potions (1.21.1)](https://docs.neoforged.net/docs/1.21.1/items/mobeffects/)

---

## 9. 战斗/伤害事件（1.21.1 重点）

### 9.1 四个事件的语义（1.21.1，包 `net.neoforged.neoforge.event.entity.living`）

| 事件 | 阶段 | 能力 | 推荐用途 |
|---|---|---|---|
| **`LivingIncomingDamageEvent`** | 伤害**刚进入**结算管线（护甲前） | 通过 `DamageContainer` 做精细修改（`addModifier`、`addReductionModifier`、`addPostMitigationModifier` 等）；也有 `getAmount()` | **首选**：减免/加成、伤害来源过滤（替代旧 `LivingAttackEvent`） |
| `LivingAttackEvent` | 同前（**1.21.1 已弃用**） | 仅 `setCanceled`/`getAmount` | 兼容旧代码；新代码用 IncomingDamageEvent |
| **`LivingHurtEvent`** | 护甲/附魔**结算后**、伤害**应用前** | `setAmount(float)` | 最终伤害数值修正（如暴击倍率、全局减免） |
| **`LivingDamageEvent`** | 伤害**已应用**（即将扣血/触发受击动画） | `getAmount()`/`setAmount()` | 兜底；不建议做数值游戏逻辑 |
| `LivingDeathEvent` | 实体死亡 | 可取消阻止死亡 | 死亡处理 |

> 1.21.1 中 `DamageContainer` 是核心（javadoc 确认存在于 1.21.1-21.1.x）：它承载原始伤害并分阶段提供修改点（`Reduction`、`PostMitigation`、`PostAbsorption` 等阶段函数 `IReductionFunction`）。**想做"饰品减伤/增伤"请用 `LivingIncomingDamageEvent` + `container.addReductionModifier(...)`**。
> 命名易混淆：`LivingIncomingDamageEvent`（新，1.21.1）实际对应旧 `LivingAttackEvent` 的"受击前"语义；社区对此命名有过吐槽（[MCreator issue #5121](https://github.com/MCreator/MCreator/issues/5121)）。

### 9.2 示例：饰品伤害减免

```java
@EventBusSubscriber(modid = AstralDice.MODID)
public class CombatEvents {
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // 检查 Curios 槽位（§4.6）
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            if (handler.isEquipped(ModItems.SOLAR_DIE.get())) {
                event.getContainer().addReductionModifier(DamageContainer.Reduction.ARMOR,
                        (container, current) -> current * 0.9f);  // 减免 10%（按阶段）
            }
        });
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setAmount(event.getAmount() * 1.05f);  // 最终数值修正
        }
    }
}
```

### 9.3 与 1.20.x 的差异

- **`LivingIncomingDamageEvent` + `DamageContainer` 是 1.21.1 新引入**的伤害管线（1.20.x 没有）；`LivingAttackEvent` 弃用。
- 伤害事件仍在 NeoForge 游戏总线，包名 `net.neoforged.neoforge.event.entity.living`。
- 同类还有 `LivingShieldBlockEvent`（盾牌格挡）、`LivingKnockBackEvent`（击退，可 `setStrength`）等。

**权威来源**：
- [1.21.x NeoForge javadoc — entity.living 事件包](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/neoforged/neoforge/event/entity/living/package-summary.html)
- [1.21.1 javadoc — LivingIncomingDamageEvent](https://lexxie.dev/neoforge/1.21.1/net/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent.html)
- [1.21.1 javadoc — DamageContainer](https://aldak0.ru/javadoc/1.21.1-21.1.x/net/neoforged/neoforge/common/damagesource/DamageContainer.html)

---

## 10. Mod 事件与 NeoForge 事件的区别（IModBusEvent）

- **`IModBusEvent`**：接口标记。实现它的**全部在 mod 事件总线**上触发，且只能被 mod 订阅（游戏总线不接收）；典型：`RegisterEvent`、`RegisterMenuScreensEvent`、`RegisterPayloadHandlersEvent`、`GatherDataEvent`、`FMLCommonSetupEvent`、`FMLClientSetupEvent`。
- **NeoForge 事件**（未实现 `IModBusEvent`）：在 `NeoForge.EVENT_BUS` 上触发，所有 mod 共享，**不允许**注册到 mod 总线（会抛异常或静默不触发，[官方 issue #2550](https://github.com/neoforged/NeoForge/issues/2550) 有相关讨论）。
- **判断方法**：看 javadoc 的 "Fired on the **NeoForge** event bus" 或 "Fired on the **mod** event bus"，或事件类是否实现 `IModBusEvent`。
- **订阅选择**：`@EventBusSubscriber(bus = Bus.MOD)`（mod 总线）/ 默认 `Bus.GAME`（NeoForge 总线）。

```java
// 错误示例：把 NeoForge 事件(如 LivingDamageEvent)注册到 MOD 总线 —— 不会触发
// 正确：NeoForge.EVENT_BUS.register(...) 或 @EventBusSubscriber(bus = Bus.GAME)
```

**权威来源**：
- [NeoForged docs — Event System and Mod Lifecycle](https://deepwiki.com/neoforged/Documentation/3.3-event-system-and-mod-lifecycle)
- [NeoForge issue #2550 — IModBusEvent 不允许在 common 总线](https://github.com/neoforged/NeoForge/issues/2550)

---

## 11. DamageType 注册与 DamageSources

### 11.1 注册（数据驱动 JSON，1.21.1 与 1.20.x 一致）

`src/main/resources/data/astral_dice/damage_type/solar_burn.json`：

```json
{
  "message_id": "astral_dice.solar_burn",
  "exhaustion": 0.1,
  "scaling": "when_caused_by_living_non_player",
  "effects": "burning",
  "death_message_type": "default"
}
```

字段说明：
- `message_id`：死亡消息 key（`death.attack.<message_id>`）。
- `exhaustion`：饥饿消耗。
- `scaling`：`never` / `when_caused_by_living_non_player` / `always`。
- `effects`：`hurt` / `thorns` / `drowning` / `burning` / `freezing`（受击动画/音效风格）。
- `death_message_type`：`default` / `fall_variants` / `intentional_game_design`。

### 11.2 代码中使用（Holder + DamageSources）

```java
// 取 Holder（需服务端 registryAccess）
Registry<DamageType> registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
Holder<DamageType> solarBurn = registry.getHolderOrThrow(
        ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AstralDice.MODID, "solar_burn")));

// 构造来源并造成伤害
DamageSource source = new DamageSource(solarBurn, attacker);   // 也可传 (holder, attacker, sourcePosition)
entity.hurt(source, amount);

// 内置来源：entity.damageSources().genericKill() / .fall() / .onFire() / .playerAttack(player) 等
DamageSources ds = entity.damageSources();
```

> 注意：1.21.1 中 `DamageSource` 构造器用 `Holder<DamageType>`（1.20.x 后期已如此）。`DamageSources` 提供全部原版来源工厂。

**权威来源**：
- [NeoForged docs — Damage Types & Damage Sources (1.21.1)](https://docs.neoforged.net/docs/1.21.1/resources/server/damagetypes/)
- [1.21.1 mappings — DamageType](https://mappings.dev/1.21.1/net/minecraft/world/damagesource/DamageType.html)

---

## 12. ModDevGradle 2.x 用法

### 12.1 插件与核心块（项目已按此配置）

```groovy
plugins {
    id 'java-library'
    id 'net.neoforged.moddev' version '2.0.141'
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

neoForge {
    version = project.neo_version                    // 21.1.235
    parchment {
        mappingsVersion = project.parchment_mappings_version
        minecraftVersion = project.parchment_minecraft_version
    }
    runs { /* 见下 */ }
    mods { "${mod_id}" { sourceSet(sourceSets.main) } }
}
```

### 12.2 runs 配置（项目实际使用）

```groovy
runs {
    client {
        client()
        if (project.hasProperty('quickplay')) {
            programArgument '--quickPlaySingleplayer'   // 原版 quickPlay：直接进单机世界
            programArgument project.findProperty('quickplay')
        }
        programArgument '--width'; programArgument '1920'   // 固定窗口，便于自动化 OCR
        programArgument '--height'; programArgument '1080'
        systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id
    }
    server {
        server()
        programArgument '--nogui'
        systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id
    }
    gameTestServer {
        type = "gameTestServer"   // 跑完 gametest 自动退出
        systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id
    }
    data {
        data()
        programArguments.addAll '--mod', project.mod_id, '--all',
                '--output', file('src/generated/resources/').getAbsolutePath(),
                '--existing', file('src/main/resources/').getAbsolutePath()
    }
    configureEach {
        systemProperty 'forge.logging.markers', 'REGISTRIES'
        logLevel = org.slf4j.event.Level.DEBUG
    }
}
```

- 任务：`runClient` / `runServer` / `runData` / `runGameTestServer`；构建 `build`（含 `jar`）、发布 `publish`。
- quickPlay 用法：`./gradlew runClient -Pquickplay=<世界名>`（项目注释已声明；对应原版 `--quickPlaySingleplayer <世界名>`）。
- IDE：`neoForge.ideSyncTask generateModMetadata` 让元数据生成任务随 IDE 同步；`./gradlew ide` 可生成 IDEA 工程。
- 运行时依赖注意：项目注释明确 **KubeJS/Curios 必须用 `implementation` 声明**（ModDevGradle 的 mod 定位只识别 implementation 的 mod，`localRuntime` 不会进 dev 环境 mod 列表）——这是本项目踩过的坑，保留此写法。

### 12.3 元数据（neoforge.mods.toml 由模板生成）

- 项目用 `src/main/templates` + `generateModMetadata`（`ProcessResources` + `expand`）把 `${neo_version}` 等替换生成 `META-INF/neoforge.mods.toml`（1.21 文件名从 `mods.toml` 改名为 **`neoforge.mods.toml`**，位于 `src/main/resources/META-INF/`）。
- `mods.toml` 关键字段：`modLoader="javafml"`、`loaderVersion`、`[[mods]]`（modId/version/displayName）、`[[dependencies.astral_dice]]`（modId=`neoforge` type=required、`curios`、`kubejs` type=optional 等）。

### 12.4 与 1.20.x 差异

- ModDevGradle 2.x 是 NeoForge 官方工具链（替代 ForgeGradle）；1.20.1 的 `forge` 插件体系不适用。
- 1.21 起依赖仓库为 `maven.neoforged.net/releases`；mod 定位用 `neoForge { mods { ... } }` 而非 FG 的 `minecraft { runs }` 旧写法。
- `runs` 内用 `client()`/`server()`/`data()` DSL 方法启用类型，自定义类型用 `type = "..."`。

**权威来源**：
- [ModDevGradle 2 稳定版发布说明](https://neoforged.net/news/moddevgradle2/)
- [NeoForged docs — ModDevGradle](https://docs.neoforged.net/toolchain/docs/plugins/mdg/)
- [neoforged/ModDevGradle 仓库](https://github.com/neoforged/ModDevGradle)

---

## 13. KubeJS 7（1.21.1 / 2101.x）

> 本节事件表与插件接口均来自项目依赖 `kubejs-neoforge-2101.7.2-build.372-sources.jar` 的**源码逐条核对**。

### 13.1 脚本目录与加载

| 目录 | 端 | 用途 |
|---|---|---|
| `kubejs/startup_scripts/` | 客户端+服务端（启动期） | 注册物品/方块/音效等（`StartupEvents.registry`） |
| `kubejs/server_scripts/` | 仅服务端 | 配方、进度、事件（`ServerEvents.recipes`、`PlayerEvents` 等） |
| `kubejs/client_scripts/` | 仅客户端 | HUD、tooltip、渲染相关 |
| `kubejs/data/`、`kubejs/assets/` | 双方 | 附加数据包/资源包 |

- 脚本热重载：`/reload`（服务端脚本）、`/kubejs reload startup_scripts`、客户端 `F3+T` 等。
- 1.21.1 对应 KubeJS **7**（版本号 `2101.x`，本项目 2101.7.2-build.372）；语法用 Rhino（ES5+ 扩展，`let`/`const`/箭头函数可用）。

### 13.2 内置事件组全表（源码验证，2101.7.2-build.372）

**`PlayerEvents`**（组名 `PlayerEvents`）：
`loggedIn`、`loggedOut`、`cloned`、`respawned`、`tick`、`decorateChat`(可改)、`chat`(可改)、`advancement`(按 id 定向)、`inventoryOpened`、`inventoryClosed`(按菜单定向)、`inventoryChanged`(按物品定向)、`chestOpened`、`chestClosed`、`stageAdded`、`stageRemoved`(按字符串定向)。

**`ServerEvents`**：
`loaded`、`unloaded`、`tick`、`commandRegistry`、`recipeMappingRegistry`、`recipeSchemaRegistry`、`recipes`、`afterRecipes`、`specialRecipeSerializers`、`compostableRecipes`。

**`ItemEvents`**：
`modification`(startup)、`toolTierRegistry`(startup)、`rightClicked`(按物品定向，可返回结果)、`canPickUp`、`pickedUp`、`dropped`、`entityInteracted`、`crafted`、`smelted`、`foodEaten`、`modifyTooltips`、`dynamicTooltips`(client)、`modelProperties`(startup)、`firstRightClicked`、`firstLeftClicked`、`destroyed`。

**`EntityEvents`**（全部按实体类型定向）：
`death`(可改)、`beforeHurt`(可改)、`afterHurt`、`checkSpawn`(可改)、`spawned`(可改)、`drops`(可改)。

**`StartupEvents`**：`registry(type, handler)`、`modifyRegistry`、`init`、`postInit`、`serverInit` 等（registry 类型字符串：`item`、`block`、`mob_effect`、`damage_type`、`menu` 等）。

### 13.3 脚本示例

```js
// server_scripts/example.js
ServerEvents.recipes(event => {
    event.remove({ output: 'minecraft:nether_star' });
    event.shaped('astral_dice:solar_die', ['ABA','BCB','ABA'], {
        A: 'minecraft:gold_ingot', B: 'minecraft:diamond', C: 'minecraft:nether_star'
    });
});

PlayerEvents.loggedIn(event => {
    const p = event.player;
    p.tell('欢迎，' + p.username);
    // p.mainHandItem / p.inventory / p.persistentData 等
});

PlayerEvents.tick(event => {
    const p = event.player;
    // 每 tick 检查饰品（见 13.4 的 Curios 绑定）
});

EntityEvents.death(event => {
    if (event.entity.type === 'minecraft:zombie') event.server.runCommandSilent('say 僵尸死了');
});
```

### 13.4 在 KubeJS 中访问 Curios 槽位（自动化测试关键）

**重要事实（已从 2101.7.2-build.372 源码 jar 验证）：KubeJS 核心**不包含任何 Curios 绑定**（jar 内无 curios 相关引用）。因此脚本里直接 `Java.type('top.theillusivec4.curios.api.CuriosApi')` 默认会被 ClassFilter 拦截。三种可行方案：

**方案 A（推荐，最可控）：自写 KubeJSPlugin 绑定 CuriosApi** —— 在你的 Java mod 里加一个插件类：

```java
// KubeJSPlugin 实现（KubeJS 7 接口，源码验证）
public class CuriosKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void init() { }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        // 给脚本暴露全局对象：CuriosApi
        bindings.add("CuriosApi", new CuriosApiBinding());
    }

    @Override
    public void registerClasses(ClassFilter filter) {
        filter.allow("top.theillusivec4.curios.api");   // 放行 Curios 类（按需）
    }
}
// 注册：META-INF/services/dev.latvian.mods.kubejs.KubeJSPlugin 里写实现类全名
```

```java
// 绑定对象：把 §4.6 的读取逻辑包装成 JS 友好接口
public class CuriosApiBinding {
    public boolean isEquipped(Player player, String itemId) {
        return CuriosApi.getCuriosInventory(player)
                .map(h -> h.isEquipped(s -> s.is(ResourceLocation.parse(itemId)::equals) || s.getItem().toString().equals(itemId)))
                .orElse(false);
    }
    public int countInSlot(Player player, String slotType) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(h -> h.getStacksHandler(slotType))
                .map(h -> { int n = 0; var st = h.getStacks(); for (int i = 0; i < st.getSlots(); i++) if (!st.getStackInSlot(i).isEmpty()) n++; return n; })
                .orElse(0);
    }
}
```

脚本里：

```js
PlayerEvents.tick(event => {
    const player = event.player;
    if (CuriosApi.isEquipped(player, 'astral_dice:solar_die')) {
        // 自动化测试断言：佩戴成功
    }
});
```

**方案 B：使用 KubeJS-Curios 第三方 addon**（[zhaijineet/KubeJS-Curios](https://deepwiki.com/zhaijineet/KubeJS-Curios)）—— 为 1.20.1 KJS6 设计，提供 `CuriosAPI` 绑定（`getCuriosInventory(player)` → `handler.getStacksHandler('ring')` 等）；用于 1.21.1 需确认其是否有 KJS7 分支/构建，否则需自行移植。

**方案 C：纯脚本 + 数据**：如果只是"检测是否佩戴"，可让 Java mod 把状态写进玩家 `persistentData`（`player.getPersistentData()` 在 1.21.1 仍可用作 KubeJS `p.persistentData`），KubeJS 只读该字段，避免直接触碰 Curios 类。简单但引入双份状态同步问题。

> 参考阅读（KubeJS6 时代的 Curios 实例，思路可迁移）：
> [MC百科 — KJS6 为依赖 Curios 的饰品添加检测](https://www.mcmod.cn/post/4393.html)、[KJS6 Curios 实例及 ambiguous 歧义方法处理](https://www.mcmod.cn/post/4338.html)、[KubeJS6 快速确定是否装备某饰品](https://www.mcmod.cn/post/4451.html)

### 13.5 KubeJS 与 Java mod 互操作（plugin / binding）

- 插件加载：`META-INF/services/dev.latvian.mods.kubejs.KubeJSPlugin`（ServiceLoader）；KubeJS 启动时扫描并调用。
- `KubeJSPlugin` 关键方法（源码验证，全部 default）：
  - `init()` / `initStartup()` / `afterInit()`：生命周期钩子。
  - `registerBindings(BindingRegistry)`：`bindings.add("名称", 对象)` 暴露全局绑定（脚本中直接可用）。
  - `registerEvents(EventGroupRegistry)`：`registry.register(EventGroup.of("MyEvents").server("myEvent", () -> MyKubeEvent.class))` 注册自定义事件组（脚本 `MyEvents.myEvent(event => ...)`）。
  - `registerClasses(ClassFilter)`：放行 Java 类给脚本（`ClassFilter.allow("包名")`）。
  - `registerTypeWrappers(TypeWrapperRegistry)`：为 Java 类型注册脚本转换器。
  - `registerRecipeComponents/RecipeMappings/RecipeSchemas`：扩展配方系统（KJS7 的新配方框架）。
  - `attachPlayerData(AttachedData<Player>)`：给玩家附加 KubeJS 侧数据。
- `BindingRegistry` 源码：`record BindingRegistry(KubeJSContext context, Scriptable scope)`，`add(String name, Object value)` 直接注入作用域。
- KJS7 配方系统大改：`event.recipes` 基于 RecipeSchema/RecipeMapping（`ServerEvents.recipeMappingRegistry`、`recipeSchemaRegistry`），老版 `event.recipes.minecraft.shaped` 风格仍兼容多数原版配方。

### 13.6 KubeJS 6 → 7（1.20.1 → 1.21.1）差异

- 版本号体系：`6001.x`（1.20.1）→ **`2101.x`**（1.21.1），大版本 **KubeJS 7**。
- 事件/绑定/插件 API 大体延续；配方系统引入 Schema 框架（向后兼容常用写法）。
- 脚本仍为 Rhino；物品数据在新版可用组件 API（如 `stack.getComponent('minecraft:custom_data')` 相关封装，视版本支持度）。

**权威来源**：
- [KubeJS 官网 — Events](https://kubejs.com/wiki/events)
- [KubeJS 官网 — 7.0 (1.21) 大版本更新](https://kubejs.com/wiki/other/major-updates/7.0)
- [kube-mods/kubejs 仓库](https://github.com/kube-mods/kubejs)
- [MC百科 — KubeJS](https://www.mcmod.cn/class/2450.html)
- [Wudji KubeJS 中文教程](https://github.com/Wudji/XPlus-KubeJS-Tutorial/blob/v1/Gitbook/the-start/file-structure.md)

---

## 14. 所有参考 URL

### NeoForge 官方文档（含 1.21.1 版本页）
- https://docs.neoforged.net/docs/1.21.1/gettingstarted/ （入门）
- https://docs.neoforged.net/docs/1.21.1/gettingstarted/modfiles/ （mod 文件 / neoforge.mods.toml）
- https://docs.neoforged.net/docs/concepts/events/ （事件系统）
- https://docs.neoforged.net/docs/1.21.1/items/datacomponents/ （Data Components）
- https://zh-neoforge.netlify.app/docs/1.21.1/items/datacomponents/ （中文镜像）
- https://docs.neoforged.net/docs/1.21.8/datastorage/attachments/ （Data Attachments，API 以 1.21.1 javadoc 为准）
- https://docs.neoforged.net/docs/networking/payload/ （网络 Payload）
- https://docs.neoforged.net/docs/1.21.1/gui/menus/ （菜单）
- https://docs.neoforged.net/docs/1.21.1/items/mobeffects/ （MobEffect）
- https://docs.neoforged.net/docs/1.21.1/resources/server/damagetypes/ （DamageType）
- https://docs.neoforged.net/docs/1.21.1/resources/client/models/datagen/ （模型 datagen）
- https://docs.neoforged.net/toolchain/docs/plugins/mdg/ （ModDevGradle 文档）
- https://github.com/neoforged/Documentation （文档源码仓库）

### NeoForge javadoc（1.21.1 / 1.21.x）
- https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/neoforged/neoforge/event/entity/living/package-summary.html
- https://lexxie.dev/neoforge/1.21.1/net/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent.html
- https://aldak0.ru/javadoc/1.21.1-21.1.x/net/neoforged/neoforge/common/damagesource/DamageContainer.html
- https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/neoforged/neoforge/network/registration/PayloadRegistrar.html
- https://mappings.dev/1.21.1/net/minecraft/world/damagesource/DamageType.html

### Curios API
- https://docs.illusivesoulworks.com/curios/items/curio-creation （官方 Wiki：创建饰品）
- https://docs.illusivesoulworks.com/1.20.x/curios/inventory/basic-inventory （官方 Wiki：库存管理）
- https://github.com/SSKirillSS/Curios/blob/1.21.1/README.md （1.21.1 分支）
- https://modrinth.com/mod/curios/version/9.5.0+1.21.1 （Modrinth 版本页）
- https://www.mcmod.cn/class/2029.html （MC百科）

### KubeJS
- https://kubejs.com/wiki/events （事件文档）
- https://kubejs.com/wiki/other/major-updates/7.0 （KJS7 / 1.21 更新）
- https://github.com/kube-mods/kubejs （仓库）
- https://www.mcmod.cn/class/2450.html （MC百科）
- https://github.com/Wudji/XPlus-KubeJS-Tutorial/blob/v1/Gitbook/the-start/file-structure.md （中文教程-目录结构）
- https://deepwiki.com/zhaijineet/KubeJS-Curios （KubeJS-Curios addon）
- https://www.mcmod.cn/post/4393.html （KJS6 Curios 饰品检测）
- https://www.mcmod.cn/post/4338.html （KJS6 Curios 实例与歧义方法）
- https://www.mcmod.cn/post/4451.html （KJS6 快速确定装备饰品）

### ModDevGradle / 工具链
- https://neoforged.net/news/moddevgradle2/ （MDG2 发布说明）
- https://github.com/neoforged/ModDevGradle （仓库）
- https://deepwiki.com/neoforged/ModDevGradle/5.1-run-configurations （runs 配置）

### 其他权威/问题追踪
- https://github.com/neoforged/NeoForge/issues/2510 （附件换维度不同步）
- https://github.com/neoforged/NeoForge/issues/1630 （ItemStack 不实现 IAttachmentHolder）
- https://github.com/neoforged/NeoForge/issues/2550 （IModBusEvent 总线限制）
- https://github.com/MCreator/MCreator/issues/5121 （1.21.1 伤害事件命名混淆）
- https://mcbbs.wiki/index.php?title=%E7%94%A8%E6%88%B7:MashKJo/1.21.1%E6%A8%A1%E7%BB%84%E5%BC%80%E5%8F%91%E6%95%99%E7%A8%8B/2.%E4%BA%8B%E4%BB%B6 （中文 1.21.1 教程）
- https://beishanair.github.io/2025/08/19/nf121/datagen/ （中文 1.21 datagen）
- https://doc.ideafox.top/docs/a-dai/mods-dev/gui_guide （NeoForge GUI 中文指南）

---

## 附：1.21.1 ↔ 1.20.x 差异速查表

| 主题 | 1.20.x | 1.21.1 |
|---|---|---|
| 事件包 | `net.minecraftforge.event.*` | `net.neoforged.neoforge.event.*` |
| 游戏总线 | `MinecraftForge.EVENT_BUS` | `NeoForge.EVENT_BUS` |
| mod 总线获取 | `FMLJavaModLoadingContext.get().getModEventBus()` | `@Mod` 构造器注入 |
| 物品数据 | `ItemStack.getOrCreateTag()/setTag()` | Data Component：`get/set/update/remove(ComponentType)` |
| 组件注册 | — | `Registries.DATA_COMPONENT_TYPE` + `.persistent(Codec).networkSynchronized(StreamCodec)` |
| 实体数据 | Capability + `AttachCapabilitiesEvent` | Attachment：`AttachmentType` + `getData/setData` |
| 网络 | `SimpleChannel` | `CustomPacketPayload` + `StreamCodec` + `PayloadRegistrar` |
| 菜单 | `MenuType(MenuSupplier)` | 同 1.21.1（1.21.2+ 改 `Supplier<MenuSupplier>`） |
| 效果属性 | `addAttributeModifiers(LivingEntity, AttributeMap, int)` | `addAttributeModifiers(AttributeMap, int, MobEffectInstance)`；`AttributeModifier` 用 ResourceLocation id |
| 伤害前事件 | `LivingAttackEvent` | `LivingIncomingDamageEvent` + `DamageContainer`（Attack 弃用） |
| 元数据文件 | `META-INF/mods.toml` | `META-INF/neoforge.mods.toml` |
| Curios 事件 | `CurioEquipEvent`/`CurioUnequipEvent` | `CurioCanEquipEvent`/`CurioCanUnequipEvent`/`CurioChangeEvent` |
| KubeJS | 6（6001.x） | 7（2101.x，Rhino、配方 Schema） |
