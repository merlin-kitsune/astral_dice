# D-B1 — 方案 A（「卸下前」清理）可行性与安全性取证

> 取证执行者报告。**本文件是本次任务唯一被写入的文件**；未修改任何源码 / lang / CHANGELOG / AGENTS.md，未执行 `git commit`、`git push`、未构建。
>
> 仓库：`F:\MCProject\astral_dice_multiloader`　分支：`multi-1.20.1-1.21.1`　HEAD：`027e1e9`

---

## 0. 证据基线与复现命令

| 记号 | 含义 | 本机绝对路径 |
|---|---|---|
| **N** | `neoforge-1.21.1` 侧 **Curios API 9.5.1+1.21.1** | `C:\Users\xmace\.gradle\caches\modules-2\files-2.1\maven.modrinth\curios\yohfFbgD\418fcd42e3a7844c9bdc71c9b6401fdb3894e0c4\curios-yohfFbgD.jar` |
| **F** | `forge-1.20.1` 侧 **Curios API 5.14.1+1.20.1** | `C:\Users\xmace\.gradle\caches\modules-2\files-2.1\maven.modrinth\curios\IPQlZkz1\452175b95ad3db6ff58bb8968f6bf7a9d1e0f480\curios-IPQlZkz1.jar` |

版本佐证（读自 jar 内元数据，非推测）：

- **N** `META-INF/neoforge.mods.toml` → `modId="curios"` / `version="9.5.1+1.21.1"` / `[[dependencies.curios]] modId="neoforge" versionRange="[21.1.60,)"` / `modId="minecraft" versionRange="[1.21, 1.22)"`。
- **F** `META-INF/mods.toml` → `version="${file.jarVersion}"`（5.x 线）/ `modId="forge" versionRange="[46,)"` / `modId="minecraft" versionRange="[1.20,1.21)"`。
- 与两个子项目 `build.gradle` 的声明一致：`neoforge-1.21.1/build.gradle:155,222` → `maven.modrinth:curios:yohfFbgD`；`forge-1.20.1/build.gradle:195` → `maven.modrinth:curios:IPQlZkz1`（注释写明 `IPQlZkz1 = 5.14.1+1.20.1`）。

复现命令模板（只读，不修改 jar）：

```powershell
$N = "C:\Users\xmace\.gradle\caches\...\curios-yohfFbgD.jar"
$F = "C:\Users\xmace\.gradle\caches\...\curios-IPQlZkz1.jar"
javap -p -c -classpath $N top.theillusivec4.curios.common.inventory.DynamicStackHandler
javap -p -c -classpath $F top.theillusivec4.curios.common.inventory.DynamicStackHandler
```

> **两版本 API 差异（先记在这里，影响后面每一条）**：
> Curios 在 5.x→9.x 之间把 `CurioEquipEvent`/`CurioUnequipEvent`（可 `setResult(Event.Result.DENY/ALLOW)`）**替换为** `CurioCanEquipEvent`/`CurioCanUnequipEvent`（用 NeoForge `TriState`）。
> 因此 **不存在两版本同名的事件**：
> - N 有 `top.theillusivec4.curios.api.event.CurioCanUnequipEvent`，**没有** `CurioUnequipEvent`。
> - F 有 `top.theillusivec4.curios.api.event.CurioUnequipEvent`，**没有** `CurioCanUnequipEvent`。
> （取自两 jar 的 class 清单，已逐条列举，见 Q1。）

---

## 1. 结论摘要（可裁定）

> **判定：方案 A「不安全」，不推荐按原设想实施。**
>
> **理由（一句话）**：Curios 两版本的 `DynamicStackHandler#extractItem(int,int,boolean)` 都在**任何 `simulate` 判断之前**无条件触发 `CurioCanUnequipEvent`（N）/`CurioUnequipEvent`（F）并调用 `ICurio#canUnequip(SlotContext)`；`simulate` 形参（局部变量 3）**直到方法最末尾才被使用**（只作为 `super.extractItem` 的第 3 个实参转发），因此 **simulate=true 的纯查询调用与 simulate=false 的真实提取在回调层面完全不可区分**，方案 A 的「卸下前清理」会被所有「只查询不提取」的路径命中，把**仍装备在身上的**立牌累计值清零。
>
> **并且**：`canUnequip` 的入参 `SlotContext` 内**不含任何 simulate / 调用来源信息**（字段清单见 Q4），事件对象也**只有 `stack` + `slotContext` + 判定结果**三个字段，**不存在可靠判别式**（详见 Q4）。
>
> **误清可达性**：N 侧 `CurioItemHandler` 是挂在玩家身上的 `IItemHandler` 能力视图，其 `extractItem` 直接转发到 `DynamicStackHandler#extractItem` —— 即**任何**模组/原版通过 `IItemHandler` 能力对饰品槽做 `extractItem(..., simulate=true)` 探测（`ItemHandlerHelper.insertItem` 的模拟轮、漏斗、自动化搬运、能力探测）都会触发回调。
>
> **次优方案对比见 §7。**

**逐问速览**

| 问题 | 结论 | 置信 |
|---|---|---|
| Q1 API 是否存在 / 回调时序 | N=`CurioCanUnequipEvent`+`ICurio#canUnequip(SlotContext)`；F=`CurioUnequipEvent`+同签名 `canUnequip`。回调**确定在提取之前** | 确定（字节码） |
| Q2 simulate=true 是否触发 | **触发**。两版本均如此，`simulate` 形参在末尾才被消费 | 确定（字节码分支） |
| Q3 simulate=true 走提取的路径 | 穷举见 §4；误清**实际可达且高频** | 确定（调用链）+ 频率为推断 |
| Q4 是否有可靠判别式 | **没有**。`canUnequip`/事件均不带 simulate；唯一可靠方案是 mixin `DynamicStackHandler#extractItem` 并在 `simulate==false` 分支内清理（但那是「方案 B」而非「方案 A」） | 确定 |
| Q5 覆盖度 | A 只覆盖「外部 handler 提取」类路径，**不覆盖** GUI 快捷移动/丢弃/死亡掉落/loseStacks/CPacketDestroy 等（逐条见 §6） | 确定（部分路径见 §6 的「无法判定」项） |

---

## 2. Q1 — API 是否存在？签名？回调在提取之前还是之后？

### 2.1 两版本**不是同一套 API**（必须分开写）

从两个 jar 的 class 清单直接枚举（`top/theillusivec4/curios/api/event/` 下）：

| 事件类 | N（9.5.1+1.21.1） | F（5.14.1+1.20.1） |
|---|---|---|
| `CurioCanUnequipEvent` | **存在** | **不存在** |
| `CurioCanEquipEvent` | **存在** | **不存在** |
| `CurioUnequipEvent` | **不存在** | **存在** |
| `CurioEquipEvent` | **不存在** | **存在** |
| `CurioChangeEvent` / `CurioAttributeModifierEvent` / `CurioDropsEvent` / `DropRulesEvent` / `SlotModifiersUpdatedEvent` | 存在 | 存在 |

**判定结果签名**：N 用 NeoForge `TriState`（`getUnequipResult()/setUnequipResult(TriState)`，字段 `private TriState result`，**构造器不写入默认值** ⇒ 默认 `null`）；F 用 Forge `Event.Result`（`getResult()` 继承自 `Event`，语义为 `DENY/ALLOW/DEFAULT`）。

### 2.2 `canUnequip` 真实签名（两版本**一致**）

`ICurio`（N 与 F 同签名；F 另有若干已弃用的旧式 `(String, LivingEntity)` 重载，本模组不用）：

```java
// top.theillusivec4.curios.api.type.capability.ICurio
public default boolean canUnequip(SlotContext slotContext);
public default boolean canEquip(SlotContext slotContext);
public abstract ItemStack getStack();
```

`ICurioItem`（N 与 F 同签名）：

```java
// top.theillusivec4.curios.api.type.capability.ICurioItem
public default boolean canUnequip(SlotContext slotContext, ItemStack stack);
public default boolean canEquip(SlotContext slotContext, ItemStack stack);
```

`javap` 原文（N）：

```
public interface top.theillusivec4.curios.api.type.capability.ICurioItem {
  public default boolean canUnequip(top.theillusivec4.curios.api.SlotContext, net.minecraft.world.item.ItemStack);
```

桥接实现（`ICurioItem` 的静态 default 实现，N；F 同构，仅方法引用编号不同）：

```
public boolean canUnequip(top.theillusivec4.curios.api.SlotContext);
  Code:
     0: aload_0
     1: aload_1
     2: invokevirtual #..  // Method getStack:()Lnet/minecraft/world/item/ItemStack;
     5: invokeinterface #31,  3  // InterfaceMethod ICurioItem.canUnequip:(LSlotContext;LItemStack;)Z
    10: ireturn
```

⇒ **`ICurio#canUnequip(ctx)` 完全不接收 simulate 信息**，第二个重载也只是把「饰品栈」传进来。

### 2.3 回调时序：**确定在提取之前**

**N** — `top.theillusivec4.curios.common.inventory.DynamicStackHandler#extractItem(int,int,boolean)` 字节码（`javap -p -c`，`iload_1`=slot、`iload_2`=amount、**`iload_3`=simulate**）：

```
public net.minecraft.world.item.ItemStack extractItem(int, int, boolean);
  Code:
     0: aload_0
     1: getfield      #122   // Field stacks:NonNullList
     4: iload_1
     5: invokevirtual #39    // NonNullList.get:(I)Ljava/lang/Object;
     8: checkcast     #8     // class ItemStack
    11: astore        4      // var4 = 槽位当前栈
    13: aload_0
    14: getfield      #25    // Field ctxBuilder:Function
    17: iload_1
    18: invokestatic  #43    // Integer.valueOf
    21: invokeinterface #49  // Function.apply
    26: checkcast     #55    // class SlotContext
    29: astore        5      // var5 = slotContext
    31: new           #125   // class top/theillusivec4/curios/api/event/CurioCanUnequipEvent   <== 构造
    34: dup
    35: aload         4
    37: aload         5
    39: invokespecial #127   // CurioCanUnequipEvent."<init>":(LItemStack;LSlotContext;)V         <== 构造
    42: astore        6
    44: getstatic     #106   // Field net/neoforged/neoforge/common/NeoForge.EVENT_BUS:IEventBus
    47: aload         6
    49: invokeinterface #112 // IEventBus.post:(LEvent;)LEvent;                                     <== 事件发布
    54: pop
    55: aload         6
    57: invokevirtual #130   // CurioCanUnequipEvent.getUnequipResult:()LTriState;
    60: astore        7
    62: aload         7
    64: getstatic     #100   // Field TriState.FALSE
    67: if_acmpne     74
    70: getstatic     #7     // Field ItemStack.EMPTY
    73: areturn
    ...
   143: aload         4
   145: invokestatic  #63    // CuriosApi.getCurio:(LItemStack;)Ljava/util/Optional;
   148: aload         5
   150: invokedynamic #157   // InvokeDynamic #1:apply:(LSlotContext;)Ljava/util/function/Function;
   155: invokevirtual #70    // Optional.map
   ...
   171: ifeq          182    // canUnequip 返回 false ⇒ 走 182 返回 EMPTY
   174: aload_0
   175: iload_1
   176: iload_2
   177: iload_3              // <<<<<< simulate 形参第一次也是唯一一次被读取
   178: invokespecial #158   // ItemStackHandler.extractItem:(IIZ)LItemStack;   <== 真正提取
   181: areturn
   182: getstatic     #7     // Field ItemStack.EMPTY
   185: areturn
```

`lambda$extractItem$1`（第 150 行 `InvokeDynamic` 的目标）：

```
private static java.lang.Boolean lambda$extractItem$1(SlotContext, ICurio);
  Code:
     0: aload_1
     1: aload_0
     2: invokeinterface #176,  2  // InterfaceMethod ICurio.canUnequip:(LSlotContext;)Z
     7: invokestatic  #76         // Boolean.valueOf
    10: areturn
```

**F** — 同名方法、同结构，仅事件类与结果类型不同：

```
public net.minecraft.world.item.ItemStack extractItem(int, int, boolean);
  Code:
     0..29: 同上（取 var4=槽位栈、var5=slotContext）
    31: new           #176   // class top/theillusivec4/curios/api/event/CurioUnequipEvent      <== 构造
    39: invokespecial #177   // CurioUnequipEvent."<init>":(LItemStack;LSlotContext;)V
    44: getstatic     #97    // Field net/minecraftforge/common/MinecraftForge.EVENT_BUS:IEventBus
    49: invokeinterface #103 // IEventBus.post:(LEvent;)Z                                          <== 事件发布
    55: aload         6
    57: invokevirtual #178   // CurioUnequipEvent.getResult:()LEvent$Result;
    62: aload         7
    64: getstatic     #111   // Field net/minecraftforge/eventbus/api/Event$Result.DENY
    67: if_acmpne     74
    70: getstatic     #35    // Field ItemStack.f_41583_ (EMPTY)
    73: areturn
    ...
   140: aload         4
   142: invokestatic  #124   // CuriosApi.getCurio:(LItemStack;)Lnet/minecraftforge/common/util/LazyOptional;
   ...
   168: ifeq          179
   171: aload_0
   172: iload_1
   173: iload_2
   174: iload_3              // <<<<<< simulate 第一次也是唯一一次被读取
   175: invokespecial #203   // ItemStackHandler.extractItem:(IIZ)LItemStack;
   178: areturn
   179: getstatic     #35    // Field ItemStack.EMPTY
   182: areturn
```

**结论（Q1）**：
1. `ICurio#canUnequip(SlotContext)` / `ICurioItem#canUnequip(SlotContext, ItemStack)` **两版本都存在且签名一致**。
2. 「`canUnequip` 事件」**存在但两版本不同名**：N=`CurioCanUnequipEvent`（`TriState`），F=`CurioUnequipEvent`（`Event.Result`）。
3. 事件发布 + `ICurio#canUnequip` 调用**都在 `super.extractItem` 之前**（N 偏移 31–171 < 174；F 偏移 31–168 < 171）。⇒ 「卸下前回调」这一前提**成立**。
4. 但事件在 `TriState`/`Result` 为「同意/默认」时**不会**中止；它只是「可否提取」的判定闸门，**不代表提取一定会发生**。

### 2.4 附带确认：`canUnequip` 拿到的确实是**槽内真实栈**（A 的机制前提成立）

`ICurio#canUnequip(ctx)` 的桥接链（N 逐指令追到底，F 同构）：

```
DynamicStackHandler.extractItem
  → var4 = stacks.get(slot)                       // 槽内真实栈对象
  → CuriosApi.getCurio(var4)                       // 见下
  → .map(ctx -> curio.canUnequip(ctx))
```

- `CuriosImplMixinHooks.getCurio(ItemStack)`（N）= `stack.getCapability(CuriosCapability.ITEM)` → `Optional.ofNullable`；F = `stack.getCapability(...)` → `LazyOptional`。
- N 的 ITEM 能力提供者 `Curios.lambda$registerCaps$2(Item, ItemStack, Void)`：
  ```
  40: aload 4            // curioItem（ICurioItem）
  47: aload_1            // 传入的 ItemStack
  48: invokeinterface ICurioItem.hasCurioCapability:(LItemStack;)Z
  56: new           #367  // class ItemizedCurioCapability
  60: aload 4
  62: aload_1            // <<<<<< 原样传入同一个 ItemStack 引用
  63: invokespecial #369  // ItemizedCurioCapability."<init>":(LICurioItem;LItemStack;)V
  ```
- `ItemizedCurioCapability`（N 与 F 均）：构造器 `putfield stack` 原样保存；`getStack()` 直接返回该字段；`canUnequip(ctx)` = `curioItem.canUnequip(ctx, this.getStack())`。

⇒ **`ICurioItem#canUnequip(ctx, stack)` 的 `stack` 参数就是饰品槽里那个活对象**，在 `canUnequip` 里改它的数据组件是**写得到真实物品上**的。方案 A 的机制前提（「卸下前拿得到真实栈」）**成立**。
（本仓 `BaseSignItem` 已经是 `public abstract class BaseSignItem extends Item implements ICurioItem`（N `BaseSignItem.java:26`，F `:27`），所以直接覆写 `canUnequip(SlotContext, ItemStack)` 即可挂上 A。）

---

## 3. Q2（枢轴）— `extractItem(simulate=true)` 是否触发 `canUnequip` / 该事件？

> ### 结论：**会触发。两版本都会，且没有任何前置分支按 `simulate` 短路。**

### 3.1 字节码级证据：`simulate` 形参在方法末尾才被消费

`DynamicStackHandler#extractItem(int slot, int amount, boolean simulate)` 的局部变量表：

| 局部变量 | 形参 |
|---|---|
| `0` | `this` |
| `1` | `slot` |
| `2` | `amount` |
| **`3`** | **`simulate`** |

**N** 中 `iload_3` 在整个方法里**只出现一次**：

```
   177: iload_3              <-- 唯一一次读取 simulate
   178: invokespecial #158   // ItemStackHandler.extractItem:(IIZ)
   181: areturn
```

**F** 中同样只有一次：

```
   174: iload_3              <-- 唯一一次读取 simulate
   175: invokespecial #203   // ItemStackHandler.extractItem:(IIZ)
   178: areturn
```

而在这些偏移**之前**，两版本的 `if_acmpne/if_acmpeq/ifne/ifeq` 分支全部只依赖以下量，**没有一个依赖 `iload_3`**：

| 偏移（N / F） | 分支条件 | 说明 |
|---|---|---|
| 62–67 / 62–67 | `getUnequipResult() == TriState.FALSE`（N）/ `getResult() == Result.DENY`（F） | 事件否决 ⇒ 返回 EMPTY |
| 81–86 / 81–86 | `entity instanceof Player` | 计算 creative 标志 |
| 96–101 / 96–101 | `player.isCreative()` | 同上 |
| 111–116 / 111–116 | `result == TRUE`（N）/ `== ALLOW`（F） | 直接放行提取 |
| 119–124 / 119–124 | `stack.isEmpty()` | 跳过绑定诅咒检查 |
| 127–129 / 127–129 | `creative` | 同上 |
| 132–140 / 132–137 | `ItemStack.has(EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)`（N）/ `EnchantmentHelper.m_44920_(ItemStack)`（F，常量池原文；映射名未在本报告中断言） | 绑定类物品拒绝 |
| 143–171 / 140–168 | `ICurio.canUnequip(ctx)` | **A 的挂点** |

⇒ **在 `simulate=true` 的调用中，`CurioCanUnequipEvent`（N）/ `CurioUnequipEvent`（F）照样被 post，`ICurio#canUnequip(SlotContext)` 照样被调用。**
（唯一能让 `canUnequip` 不被调到的情形是 `stack.has(PREVENT_ARMOR_CHANGE)`；立牌不吃该附魔组件，**不构成保护**。）

### 3.2 反向印证：Curios **自己**就用 `simulate=true` 做可行性查询

这比「理论上会」更强 —— Curios 源码里就有一处**明确的** `simulate=true` 提取查询（不是 `false`）：

**N** `CuriosEventHandler.lambda$curioRightClick$14`（右键装备处理）：

```
   321: aload         9      // IDynamicStackHandler stacks
   323: iload         11     // slot index
   325: aload_1               // 玩家手里的 ItemStack
   326: invokevirtual #874    // ItemStack.getMaxStackSize:()I
   329: iconst_1              // <<<<<< simulate = true
   330: invokeinterface #877,  4  // IDynamicStackHandler.extractItem:(IIZ)LItemStack;
   335: invokevirtual #857    // ItemStack.getCount:()I
   338: aload_1
   339: invokevirtual #857    // ItemStack.getCount:()I
```

**F** `CuriosEventHandler.lambda$curioRightClick$22`：

```
   305: aload         9
   307: iload         10
   309: aload_1
   310: invokevirtual #1352   // ItemStack.m_41741_ (getMaxStackSize)
   313: iconst_1              // <<<<<< simulate = true
   314: invokeinterface #1356,  4  // IDynamicStackHandler.extractItem:(IIZ)LItemStack;
```

⇒ **两版本的「右键装备」路径都会以 `simulate=true` 提取饰品槽**，纯粹为了比对数量。若 A 在 `canUnequip` 里做清理，**这条路会把仍装备在身上的立牌清零**。

---

## 4. Q3 — 穷举以 `simulate=true` 走 `extractItem` 的调用路径

### 4.1 直接调用者总表（全 jar 字节码扫描 + 加载器/原版源码核对）

| # | 调用者 | 版本 | simulate | 会不会导致误清 |
|---|---|---|---|---|
| 1 | `SlotItemHandler#mayPickup(Player)`（L65-66 N / L102-104 F）→ `getItemHandler().extractItem(index, 1, **true**)` | N+F | **true** | **会**（见 4.2） |
| 2 | `SlotItemHandler#remove(int)`（L70-71 N / L109-111 F）→ `extractItem(index, amount, **false**)` | N+F | false | 不会（这是真实提取） |
| 3 | `CuriosEventHandler.lambda$curioRightClick$*` → `extractItem(slot, getMaxStackSize(), **true**)` | N+F | **true** | **会**（纯查询，与卸下无关） |
| 4 | `CurioItemHandler#extractItem` → 直接转发给 `IItemHandler#extractItem`（N，`CurioItemHandler` 全文只有这一处转发） | N | 透传 | 取决于外部调用方传什么 |

`CurioItemHandler` 的构造（N）把每个 `ICurioStacksHandler.getStacks()`（即 `DynamicStackHandler`）塞进 `CombinedInvWrapper`，所以对外暴露的每个槽都直达 `DynamicStackHandler#extractItem`。

### 4.2 原版 MC 侧：谁调 `mayPickup`（= `simulate=true` 提取）

**关键事实**：`CurioSlot extends SlotItemHandler`（N 与 F 均，见 Q1 附带的 `javap` 类声明），而 **`CurioSlot` 没有覆写 `mayPickup`**（N 的 `CurioSlot` 方法清单里只有 `set/allowModification/lambda$set$1/lambda$new$0`；F 的只有 `m_5852_/m_150651_/lambda$set$1/lambda$new$0`）。
⇒ **`CurioSlot#mayPickup` 就是 `SlotItemHandler#mayPickup`，即一次 `simulate=true` 的提取。**

`AbstractContainerMenu#doClick`（N 1.21.1 行号；F 结构相同，行号整体 ~+4）中调用 `mayPickup` 的位置：

| 行 | clickType | 代码 |
|---|---|---|
| N:397 | **QUICK_MOVE**（Shift 左键） | `if (!slot6.mayPickup(player)) return;` |
| N:421 | **PICKUP**（左/右键） | `} else if (slot7.mayPickup(player)) {` |
| N:457 | **SWAP**（数字键/F 键换位） | `if (slot5.mayPickup(player)) {` |
| N:473 | **SWAP**（目标槽非空） | `} else if (slot5.mayPickup(player) && slot5.mayPlace(itemstack2)) {` |
| N:502 | **PICKUP_ALL**（双击收集） | `if (!itemstack4.isEmpty() && (!slot2.hasItem() \|\| !slot2.mayPickup(player)))` |
| N:511 | **PICKUP_ALL** | `&& slot8.mayPickup(player)` |

并且 `Slot#tryRemove`（N `Slot.java:154-155` / F `:151-152`）与 `Slot#allowModification`（N `:202-203` / F `:201-202`）也会调 `mayPickup`：

```java
// net/minecraft/world/inventory/Slot.java (N)
154: public Optional<ItemStack> tryRemove(int count, int decrement, Player player) {
155:     if (!this.mayPickup(player)) { return Optional.empty(); }
157:     } else if (!this.allowModification(player) && decrement < this.getItem().getCount()) {
...
202: public boolean allowModification(Player player) {
203:     return this.mayPickup(player) && this.mayPlace(this.getItem());
```

`tryRemove` 被 `doClick` 的 PICKUP（N:424、438）、THROW（经 `Slot#safeTake` N:497→175→tryRemove）、PICKUP_ALL（经 `safeTake` N:515）调用。

⇒ **一次点击可能触发 `mayPickup` 2–3 次**（`doClick` 一次 + `tryRemove` 一次 + `allowModification` 一次），每一次都 `simulate=true`。

**`moveItemStackTo` 不调 `mayPickup`、不调 `extractItem`**（N `AbstractContainerMenu.java:630-692` 全文只调 `slot.getItem()/mayPlace/setByPlayer/setChanged/getMaxStackSize`）——这一点必须记住，否则会误判「快捷移动」。

### 4.3 加载器 / 集成路径

| 路径 | simulate | 会不会误清 | 证据 |
|---|---|---|---|
| `ItemHandlerHelper.insertItem(dest, stack, simulate)` / `insertItemStacked(...)` | 只调 `dest.insertItem(...)` | **不会** | N `ItemHandlerHelper.java:19-24, 38-66`、F `:22-29, 88-124`：**全文没有任何 `extractItem` 调用**（此前的猜想不成立，已被源码否证） |
| NeoForge `Capabilities.ItemHandler.ENTITY`（即 `CuriosCapability.ITEM_HANDLER`） | 由外部决定 | **会（若外部传 true）** | N `CuriosCapability`：`public static final EntityCapability<IItemHandler, Void> ITEM_HANDLER`；`Curios.registerCaps(RegisterCapabilitiesEvent)` 里 `registerEntity(ITEM_HANDLER, ..., lambda$registerCaps$0)`，而 `lambda$registerCaps$0` = `new CurioItemHandler(livingEntity)`（offset 24-29）。⇒ **玩家实体对外暴露的就是 curios 槽的 `IItemHandler`**，任何模组 `extractItem(slot, n, true)` 探测都会打到 `DynamicStackHandler#extractItem` |
| Forge `CapabilityItemHandler.ITEM_HANDLER_CAPABILITY` | — | **F 侧不适用** | F 全 jar 扫描 `CapabilityItemHandler`/`ITEM_HANDLER` 常量池引用：**0 处**。F 只注册 `CuriosCapability.INVENTORY`（类型 `Capability<ICuriosItemHandler>`，**不是** `IItemHandler`）与 `CuriosCapability.ITEM`。⇒ **F 没有「实体 IItemHandler 能力」这一误清面** |
| 漏斗 / 自动化搬运 | — | **不会** | F：`HopperBlockEntity.java:437-438` 只 `createUnSidedHandler()` = `new net.minecraftforge.items.VanillaHopperItemHandler(this)`（包装漏斗**自己的**容器）；N：`HopperBlockEntity.java` 全文无 `IItemHandler`/`ItemHandler` 文本引用。漏斗只经方块实体/`Container` 取放，**够不到玩家实体上的 curios 能力** |
| `Slot#mayPlace` → `SlotItemHandler#mayPlace`（L26-29 N / L29 F）→ `DynamicStackHandler#isItemValid` | 产出 `CurioCanEquipEvent` + `ICurio#canEquip` | **不会误清** | `DynamicStackHandler#isItemValid`（N 偏移 70-122）post 的是 `CurioCanEquipEvent`，走的是**装备**侧，与 `canUnequip` 无关 |
| `CuriosContainer#clicked`（N 有该方法） | — | 未知 | 未逐条反编译（见 §8「无法判定」） |
| 客户端预测（`MultiPlayerGameMode.handleInventoryMouseClick` → `menu.clicked`） | 同 `doClick` | 会（客户端侧副本） | 客户端同样跑 `doClick`；本模组的 `curioTick` 服务端写入逻辑在 `if (level.isClientSide) goto` 处被跳过（N `lambda$tick$25` 偏移 269-276），但 `mayPickup` 无此保护 |

### 4.4 误清的**实际可达性与频率**判断

**先纠正一个提法**：**「打开 Curios GUI」本身不会触发**（GUI 构建/渲染不走 `mayPickup`/`extractItem`）。真正的触发面是**对已装备立牌所在的饰品槽发起一次容器点击**。

按 clickType 逐条判断「会不会真的卸下」与「会不会误清」：

| 玩家动作 | 是否真的卸下 | A 是否会误清 |
|---|---|---|
| 左键点饰品格，光标为空（取出） | 是（`tryRemove`→`Slot#remove`→`extractItem(false)`） | 不误清（本就该清），但**回调会执行 2 次** |
| 右键点饰品格，光标为空（取一半） | 是 | 不误清 |
| **左键点饰品格，光标持有另一件饰品且槽不接收它** | **否** | **误清**（N:421 `mayPickup` 先执行，N:429 `mayPlace` 才为假，L437 物品又不同 ⇒ 什么都不做） |
| **Shift 左键点饰品格，但背包放不下 / 目标槽不可放置** | **否** | **误清**（N:397 `mayPickup` 先执行；真实移除由 `CuriosContainer#quickMoveStack` 的 `Slot#set(EMPTY)` 完成，见 §6） |
| **数字键换位（SWAP）且不满足换位条件** | **否** | **误清**（N:457/473） |
| **双击收集（PICKUP_ALL）但 `canTakeItemForPickAll` 拒绝** | **否** | **误清**（N:511 `mayPickup` 在 N:512 的判断之前） |
| **手持同名立牌右键**（Curios `curioRightClick` 的 `simulate=true` 查询） | **否** | **误清**（§3.2，两版本都有） |
| Q 键丢弃（THROW） | 是 | 不误清 |
| 第三方模组对玩家实体取 `Capabilities.ItemHandler.ENTITY` 并 `extractItem(...,true)` 探测 | 否 | **误清**（仅 N；F 无此能力） |
| 漏斗/自动化 | — | 不会 |

**频率判断**：误清**不需要任何异常操作，只需一次普通点击**。对佩戴护法/扫地机/上班族立牌的玩家而言，「打开背包 → 点一下饰品格看看 / 拖动整理 / 换位」这种操作在正常游戏中高频发生；护法剑气（≤3 层）与上班族攻防会立刻归零，扫地机的「移动累计」会丢失全部进度。
⇒ **误清实际可达、且不是边缘情况。** 这是「A 不安全」的判决性依据。

---

## 5. Q4 — 是否存在可靠判别式让 A 只在「真实提取」时清理？

### ① `canUnequip` / 事件是否携带可区分 simulate 的信息？——**不携带**

`SlotContext`（N，`javap -p` 全字段；`Record`）:

```
public final class top.theillusivec4.curios.api.SlotContext extends java.lang.Record {
  private final java.lang.String identifier;
  private final net.minecraft.world.entity.LivingEntity entity;
  private final int index;
  private final boolean cosmetic;
  private final boolean visible;
}
```

⇒ 字段只有 `identifier / entity / index / cosmetic / visible`。**没有 simulate、没有 amount、没有调用来源、没有调用栈标记。**
（两版本一致：构造器签名 `(String, LivingEntity, int, boolean, boolean)`。）

事件对象同样只有三个字段：

- N `CurioCanUnequipEvent`：`slotContext`、`stack`、`TriState result`；公开方法 `getUnequipResult/setUnequipResult/getSlotContext/getStack`。
- F `CurioUnequipEvent`：`slotContext`、`stack`；公开方法 `getSlotContext/getStack`（判定走继承的 `Event#getResult`）。

⇒ **事件也没有 simulate 位。**

### ② 改为 mixin `DynamicStackHandler#extractItem`，在 `simulate==false` 分支内清理 —— **可行，但覆盖度不足**

**可行性（注入点明确）**：
`DynamicStackHandler#extractItem(int,int,boolean)` 是本模组能看到的**唯一**「真实提取 = `simulate==false`」的汇聚点（F 侧 `forge-1.20.1` 已具备 Mixin Booster 硬前置，见 `AGENTS.md`；N 侧原生支持）。注入点可取：

- `@Inject(method = "extractItem", at = @At("HEAD"))` + `if (!simulate && !stack.isEmpty())` 清理；或
- `@Inject(..., at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/items/ItemStackHandler;extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;"))`（N）/ 对应 Forge 目标（F），此时 `simulate` 已在栈上。
- 需要在注入点取回 `stacks.get(slot)`——它是 `ItemStackHandler` 的 `protected NonNullList<ItemStack> stacks` 字段，mixin 需 `@Shadow` 或另取 `CurioStacksHandler#getStacks()`（mixin 内可直接以 `this` 读 `stacks`）。

**致命短板**：这条路径**覆盖不到** §6 表中「无 simulate=false」的那几类卸下（GUI 快捷移动、GUI 换装、GUI 数字键换位、死亡掉落、`loseStacks`、`CPacketDestroy`）。**其中「快捷移动」与「换装」正是玩家最常用的卸下方式**。

### ②' 更贴切的替代注入点：`CurioSlot#set(ItemStack)` —— 此处**仍拿得到真实旧栈**

`CurioSlot#set(ItemStack)`（N，`javap -c`）：

```
   0: aload_0
   1: invokevirtual #161   // getItem:()Lnet/minecraft/world/item/ItemStack;   <== 旧栈（仍是槽内活对象）
   4: astore_2
   ...
  25: aload_0
  26: aload_1
  27: invokespecial #170   // SlotItemHandler.set:(LItemStack;)V              <== 真正写入槽位
```

F 的 `CurioSlot.m_5852_(ItemStack)` 结构相同（偏移 0/1/27 对应）。
而 `SlotItemHandler#set` = `((IItemHandlerModifiable) getItemHandler()).setStackInSlot(index, stack)`（N `SlotItemHandler.java:39-41`）。

⇒ 在 `CurioSlot#set` 的 HEAD 处，`getItem()` **仍是即将被替换掉的那件真实栈**；这正是「卸下前拿到真实栈」的合法窗口，且**不经过 `extractItem`**，因此**能补上「快捷移动 / 换装 / 数字键换位」这三条 A 想要覆盖、而 simulate 判别式又覆盖不到的路**。判据可写成：`old = getItem(); if (!old.isEmpty() && !ItemStack.matches(old, newStack)) { 清理(old) }`（放入空槽时 `old` 为空 ⇒ 天然不触发；同内容写回 ⇒ `matches` 拦住）。
**注意**：这已经**不是方案 A**，而是另一个方案；它需要与 §6 中其余路径各自配对。

### ③ 线程 / 调用栈判别？——**不可靠，不推荐**

- `mayPickup` 与真实 `remove` 均发生在**同一逻辑线程**（服务端主线程 / 客户端主线程），线程判别无效。
- `StackWalker` / 调用栈判别属启发式：真实提取同样经由 `AbstractContainerMenu#doClick` → `mayPickup`（先）→ `remove`（后），栈帧相似；一旦其他模组或原版版本变化即失效，且客户端预测路径会引入假阳性。**不构成「可靠判别式」。**

### ④ 结论

> **找不到任何可靠、可维护的判别式，能让「方案 A」（在 `ICurio#canUnequip` / `CurioCanUnequipEvent`(N) / `CurioUnequipEvent`(F) 中做清理）只在真实提取时生效。**
> 原因有两层：**(a)** 回调两版本都在 `simulate` 分支之前触发，且回调参数不含 `simulate`；**(b)** Curios 自己就把 `simulate=true` 当作「能不能拿走」的正式查询手段（`SlotItemHandler#mayPickup`、`curioRightClick`）。
> ⇒ **判定：A 不安全。**

---

## 6. Q5 — 方案 A 的覆盖度（逐条）

判据：**A = 在 `ICurioItem#canUnequip(SlotContext, ItemStack)`（或等价事件订阅）里，对传入的真实槽内栈做组件归零。**

「sim(1)」= 触发一次 `simulate=true` 回调；「sim(0)」= 触发一次 `simulate=false` 回调。

| # | 卸下路径 | 触发点（类 + 方法 + 偏移/行） | A 是否覆盖 | 是否有「仅 sim(1)、无 sim(0)」的误清 |
|---|---|---|---|---|
| 1 | **GUI 取出**（左/右键，光标空） | `AbstractContainerMenu#doClick`（N:411-428）→ `slot7.mayPickup`(N:421, **sim1**) → `slot7.tryRemove`(N:424) → `Slot#tryRemove`(N:155 **sim1**、N:157→`allowModification` N:203 **sim1**) → `Slot#remove`(N:161) → `SlotItemHandler#remove`(N:70-71 **sim0**) | ✅ **覆盖** | 否（真卸下）；但回调会跑 2–3 次 |
| 2 | **GUI 快捷移动**（Shift 左键） | `doClick` QUICK_MOVE（N:391-401）→ `slot6.mayPickup`(N:397，**sim1**)；真实移除由 `CuriosContainer#quickMoveStack` 完成 —— N 偏移 379-400：`if (局部副本.isEmpty()) slot6.set(ItemStack.EMPTY) else slot6.setChanged()`，即 **`Slot#set` ⇒ `setStackInSlot`，不经过 `extractItem`**；F `CuriosContainer#m_7648_` 偏移 348-368 同构（`Slot.m_5852_(EMPTY)`，常量池原文） | ⚠️ **仅靠 sim(1) 覆盖**（无 sim0） | **是**：目标容器放不下/目标槽不接受时物品仍在槽内，却已被清零 |
| 3 | **GUI 换装**（光标持另一件饰品） | `doClick` PICKUP：N:421 `mayPickup`(**sim1**) → N:429 `mayPlace` → N:435 `slot7.setByPlayer(itemstack10)` ⇒ **`Slot#set`，不走 `extractItem`** | ⚠️ **仅靠 sim(1) 覆盖**（无 sim0） | **是**（`mayPickup` 在 `mayPlace` 之前）；另若走 N:437「同物品合并」分支（`tryRemove` 取一部分）则物品**没离开槽位** |
| 4 | **GUI 数字键换位**（SWAP） | `doClick` SWAP（N:451-486）→ `slot5.mayPickup`(N:457、N:473，**sim1**)；实际 `slot5.setByPlayer(...)`（N:460/467/470/476/483）⇒ `Slot#set` | ⚠️ **仅靠 sim(1) 覆盖**（无 sim0） | **是**（N:473 分支在 `mayPlace` 之后但仍在动作前；条件不满足时物品不动） |
| 5 | **GUI 双击收集**（PICKUP_ALL） | `doClick` PICKUP_ALL（N:499-518）→ `slot2.mayPickup`(N:502) / `slot8.mayPickup`(N:511)（**sim1**）→ `slot8.safeTake`(N:515) ⇒ `tryRemove` → `Slot#remove` ⇒ **sim0** | ✅ **覆盖**（有 sim0） | 否（真取出）；但 N:511 的 sim1 在 `canTakeItemForPickAll`(N:512) 之前 ⇒ 若被拒则误清 |
| 6 | **丢弃**（Q 键 / THROW） | `doClick` THROW（N:494-498）→ `slot3.safeTake`(N:497) → `Slot#safeTake`(N:175) → `tryRemove`(N:155 **sim1**) → `Slot#remove`(**sim0**) | ✅ **覆盖** | 否 |
| 7 | **死亡掉落** | `LivingDropsEvent` → `CuriosEventHandler#handleDrops`（F，偏移 276 `setStackInSlot(i, EMPTY)`）/ N `playerDrops` 同构；**不触发** `canUnequip`、**不触发** `extractItem` | ❌ **不覆盖** | — （但**已被现有路径覆盖**：N `event/PlayerLifecycleHandler.java:116` `@SubscribeEvent(priority = EventPriority.LOWEST)` 的 `onPlayerDeathClearEffects`，在 `LivingDeathEvent`（早于 `LivingDropsEvent`）里用 `findFirstCurio` 直接清装备中的真实栈，L148/L157/L165 分别处理 misaki/jasmine/padman） |
| 8 | **`loseStacks`**（槽位收缩，如骰子星级下降导致卡牌/筹码栏缩减） | `CurioStacksHandler#resize` → `loseStacks`（N 偏移 33 调用）；`loseStacks` 主体：偏移 50/55 `var6 = stacks.getStackInSlot(i)` → 偏移 316 `CuriosApi.getCurio(var6)` → 偏移 321 `ifPresent` → `lambda$loseStacks$9` = `curio.onUnequip(ctx, **ItemStack.EMPTY**)` → 偏移 332-335 `setStackInSlot(i, EMPTY)` | ❌ **不覆盖**（走 `onUnequip`，不走 `canUnequip`/`extractItem`） | — （但**已被现有路径正确覆盖**：`ICurio.getStack()` = `var6` = **槽内真实栈**（§2.4），故现有 `isIntentionalUnequip(EMPTY, realStack)` 判为「真实移除 ⇒ 清理」，且写在真栈上） |
| 9 | **客户端 `CPacketDestroy`**（Curios GUI 的销毁/删除按钮） | F `CPacketDestroy#lambda$handle$3`：偏移 280-294 `getCurio(槽位栈 var10).ifPresent(...)` → `lambda$handle$0` = `curio.onUnequip(ctx, **var10 = 同一个槽位栈**)`；随后偏移 297-303 `setStackInSlot(i, EMPTY)`。N `CuriosServerPayloadHandler#lambda$handleDestroyPacket$9` 同构（偏移 299 `getCurio`，320/371 `setStackInSlot(i, EMPTY)`，`lambda$handleDestroyPacket$8` 调 `onUnequip`） | ❌ **不覆盖** | — （现有 `isIntentionalUnequip` 因「第 2 参与第 3 参同一物品」判为**不清理** ⇒ **已知残留缺口**，A 也补不上；物品随即被销毁） |
| 10 | **手持 shift 点击 / 右键装备** | `CuriosEventHandler#curioRightClick`：**N 偏移 329 `iconst_1` → 330 `extractItem`**；**F 偏移 313 `iconst_1` → 314 `extractItem`**（**simulate=true 的纯查询**）。本模组自己的 `CurioSlotUtil#tryAutoEquip`（N `CurioSlotUtil.java:76-82`）直接 `setStackInSlot`，不经 `extractItem` | ❌ **不覆盖**（不是卸下） / **但会误清** | **是**：两版本都有；这是「A 会把仍装备的立牌清零」的直接证据 |
| 11 | **物品损坏**（`curioBreak` / `SPacketBreak`） | 未逐条反编译 | **无法判定** | **无法判定** |
| 12 | **`CuriosContainer#clicked`（N 有该方法）** | 未逐条反编译 | **无法判定** | **无法判定** |

### 结论（覆盖度）

- **A 名义上「覆盖」的只有 1/5/6/2/3/4 这六条 GUI 路径**，且其中 2/3/4 是**靠 `simulate=true` 的副作用**覆盖的 —— 这正是它不安全的根源。
- **A 真正需要的三条**（GUI 取出 / 快捷移动 / 换装）里，**只有「GUI 取出」有 `simulate=false`**；另两条只有在 `CurioSlot#set` 处才能可靠拿到真实旧栈。
- **A 完全覆盖不到**：死亡掉落（#7）、`loseStacks`（#8）、`CPacketDestroy`（#9）——这三条必须**保留/依赖现有清理路径**（`PlayerLifecycleHandler` @LOWEST、`CurioStacksHandler.loseStacks` 的 `onUnequip(EMPTY, realStack)`、以及 `isIntentionalUnequip` 现有语义）。

---

## 7. 次优方案对比表（A 不安全 ⇒ 供裁定用）

> ⚠️ **以下均为方案描述，未修改任何代码。**

| 方案 | 覆盖度 | 主要副作用 / 风险 | 结论 |
|---|---|---|---|
| **A. `ICurioItem#canUnequip` / `CurioCanUnequipEvent`(N) / `CurioUnequipEvent`(F) 内清理** | GUI 取出、快捷移动、换装、数字键、双击、丢弃（其中 3 条靠 sim1 副作用） | **严重**：`mayPickup`(sim1) 与 `curioRightClick`(sim1) 会把仍装备的立牌清零；两版本都存在；判别式不存在 | **不推荐** |
| **B. Mixin `DynamicStackHandler#extractItem`，仅在 `simulate==false` 分支清理真实槽内栈** | 仅「GUI 取出 / 丢弃 / 双击收集」三类真提取 | 无误清 ✅；但**漏**「快捷移动 / 换装 / 数字键 / 死亡 / loseStacks / CPacketDestroy」；两版本方法名相同（`extractItem`），但 NeoForge/Forge 的 `ItemStackHandler` 包名不同，注入目标字符串需按子项目各写一份 | **可作为「真提取」这一半的正确实现**，不能单独用 |
| **C. Mixin `CurioSlot#set(ItemStack)`（N）/ `CurioSlot#m_5852_`（F），在 HEAD 取 `getItem()` 旧栈，`!old.isEmpty() && !matches(old,new)` 时清理** | **补齐「快捷移动 / 换装 / 数字键」** —— 这三条唯一可靠的真实旧栈窗口 | 需要两版本各写一份 mixin（方法名不同）；须防「放入空槽」（`old.isEmpty()` ⇒ 天然不触发）与「同内容写回」（`matches` 拦住）；`curioTick` 自写组件会导致 `matches` 为假 ⇒ 需与 `isIntentionalUnequip` 同样的「同物品不清理」语义 | **推荐与 B 组合**（B 管真提取、C 管 set 类替换） |
| **D. 装备时清理（`ICurioItem#onEquip`）** | 只覆盖「装入」；不解决卸下 | 语义只能保证「新装上的立牌不继承旧累计」，**卸下时旧累计照样丢失**；且 tick 路径的 `onEquip(ctx, 快照)` 第 3 参是副本（同样写不到实物） | 单独用**不解决问题** |
| **E. 背包按值反查（卸下后按物品 + 组件特征在背包里找同名栈并清理）** | 覆盖「物品确实进了玩家背包」的路径（GUI 取出/快捷移动/换装掉落物不在背包时漏） | **启发式**：同物品多件、专属绑定/耐久不同的副本会误伤；掉落物/被销毁/被漏斗吸走均漏；`ItemStack` 组件变化后可能匹配不到 | 高风险，**不推荐** |
| **F. 接管 `previousStacks`（让 tick 路径拿到真实栈）** | — | **已证不可行**（既不能按引用也不能按槽位取到被卸下的那件） | 排除 |
| **G. 接受现状（仅保留玩家级清理 + 死亡/loseStacks 现有路径）** | 死亡、loseStacks、玩家级资源清理照常；**物品组件在 GUI 卸下路径上继续归零丢失** | 零新增风险；玩家感知 = 卸下再装上时剑气/移动累计/上班光攻防归零 | **零风险基线**；若用户不接受该感知，需 B+C 组合 |

---

## 8. 明确的「无法判定」项（禁止猜测）

1. **`curioBreak` / `SPacketBreak`（物品损坏）路径是否经过 `extractItem`、是否会触发 `canUnequip`** —— **无法判定**（未逐条反编译这两条链路）。
2. **`CuriosContainer#clicked`（N 存在该覆写方法）内部是否额外调用 `mayPickup` / `extractItem`** —— **无法判定**（只确认了 `CuriosContainer` 的**方法清单**，未展开其字节码）。
3. **F 侧 `CuriosContainerV2`（第二套 GUI）内是否有独立的 `mayPickup` 触发面** —— **无法判定**（`CuriosContainerV2` 未反编译）。
4. **第三方模组中实际会对玩家实体 `Capabilities.ItemHandler.ENTITY` 传 `simulate=true` 调 `extractItem` 的具体模组清单** —— **无法判定**（本机无法穷举整合包内全部模组的字节码；只能确定「能力已暴露、机制上可达」，见 §4.3）。
5. **`isIntentionalUnequip` 对 #9 `CPacketDestroy` 的「不清理」是否真的无害** —— 本报告只确认「A 覆盖不到该路径且现有判据会跳过」；**是否应改判为清理属产品决策，不是取证结论**。
6. **误清发生的实测频率（单位时间次数）** —— **无法判定**（本报告给出的是机制上的触发面与「每次点击至少 2 次 sim1 回调」的静态计数，未做游戏内计数实测；本任务禁止构建与运行）。

---

## 9. 最终裁定建议（一句话）

> **不推荐方案 A。**
> 建议改为组合方案 **B（`DynamicStackHandler#extractItem` 的 `simulate==false` 分支清理）+ C（`CurioSlot#set` HEAD 处按 `getItem()` 旧栈清理）**，并**保留**现有 `PlayerLifecycleHandler`（死亡，LOWEST）、`loseStacks` 的 `onUnequip`（已正确落在真栈上）、以及 `isIntentionalUnequip` 的「同物品不清理」语义作为其余路径的兜底；`CPacketDestroy` 的已知缺口是否补，属产品决策。

---

### 附：本次取证使用的只读命令（可复现）

```powershell
$N = "C:\Users\xmace\.gradle\caches\modules-2\files-2.1\maven.modrinth\curios\yohfFbgD\418fcd42e3a7844c9bdc71c9b6401fdb3894e0c4\curios-yohfFbgD.jar"   # Curios 9.5.1+1.21.1
$F = "C:\Users\xmace\.gradle\caches\modules-2\files-2.1\maven.modrinth\curios\IPQlZkz1\452175b95ad3db6ff58bb8968f6bf7a9d1e0f480\curios-IPQlZkz1.jar"     # Curios 5.14.1+1.20.1
javap -p -c -classpath $N top.theillusivec4.curios.common.inventory.DynamicStackHandler
javap -p -c -classpath $F top.theillusivec4.curios.common.inventory.DynamicStackHandler
javap -p -c -classpath $N top.theillusivec4.curios.common.inventory.CurioSlot
javap -p -c -classpath $N top.theillusivec4.curios.common.event.CuriosEventHandler        # tick / curioRightClick / handleDrops
javap -p -c -classpath $F top.theillusivec4.curios.common.event.CuriosEventHandler
javap -p -c -classpath $N top.theillusivec4.curios.Curios                                # registerCaps / lambda$registerCaps$*
javap -p -c -classpath $N top.theillusivec4.curios.common.capability.ItemizedCurioCapability
javap -p -classpath     $N top.theillusivec4.curios.api.SlotContext
# MC / 加载器源码（zip 内条目直接读）
#   neoforge-1.21.1\build\moddev\artifacts\neoforge-21.1.235-sources.jar
#     net/neoforged/neoforge/items/SlotItemHandler.java          (mayPickup L65-66 / remove L70-71)
#     net/neoforged/neoforge/items/ItemHandlerHelper.java        (无 extractItem)
#     net/minecraft/world/inventory/AbstractContainerMenu.java   (doClick mayPickup 六处 / moveItemStackTo L630-692)
#     net/minecraft/world/inventory/Slot.java                    (tryRemove L154-155 / allowModification L202-203)
#   forge-1.20.1\build\moddev\artifacts\forge-1.20.1-47.4.10-sources.jar
#     net/minecraftforge/items/SlotItemHandler.java              (mayPickup L102-104 / remove L109-111)
#     net/minecraftforge/items/ItemHandlerHelper.java            (无 extractItem)
#     net/minecraft/world/inventory/AbstractContainerMenu.java   (mayPickup 六处)
#     net/minecraft/world/inventory/Slot.java                    (tryRemove L151-152 / allowModification L201-202)
#     net/minecraft/world/level/block/entity/HopperBlockEntity.java (L437-438 createUnSidedHandler)
```

*报告完成时间：本次会话；仓库 HEAD `027e1e9`；唯一写入文件：本文件。*
