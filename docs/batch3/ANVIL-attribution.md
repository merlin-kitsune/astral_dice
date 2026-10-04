# ANVIL-STAR-UPGRADE-1.21.1 首次实跑 FAIL 归因（铁砧附加槽残留 3 星币）

调查者：归因调查者（只做静态取证，未改任何代码/脚本/用例）
仓库：`F:\MCProject\astral_dice_multiloader`，分支 `multi-1.20.1-1.21.1`，HEAD `7b9e711`
产出：本文件（唯一允许写入的文件）

**证据基线（本机反编译产物，逐条给行号）**

| 别名 | 路径 |
|---|---|
| `SRC1211` | `neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-sources.jar`（Mojang 映射 + NeoForge 补丁后的源码） |
| `SRC1201` | `forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar` |
| `CLS1211` / `CLS1201` | 同名 `-merged.jar`（用于 `javap` 校验编译后语义） |
| `LOG` | `run/1.21.1/logs/latest.log`（本次实跑的完整 `AP_*` 读数，比 `temp/b1_cases/*.log` 的 40 字符截断版更完整） |
| `PROBE` | `scripts/test/resources/kubejs/1.21.1/server_scripts/astral_bugfix_probe.js` |

> 说明：`SRC1211` 里的 `AnvilMenu.createResult` 已含 NeoForge 补丁（`CommonHooks.onAnvilChange`），
> 行号按该 jar 内实际文本计；涉及 NeoForge 自身实现处另行用 `javap` 读 `CLS1211` 并给**字节码偏移**。

---

## 0. 现象与读数复核（先把事实钉死）

用例步骤（`scripts/test/cases/ANVIL-STAR-UPGRADE-1.21.1.json:16-32`）与探针实现
（`PROBE:822-908` `doAnvilStar`）合起来是：

1. `anvilClearItem(star_coin)` 清空物品栏星币（`PROBE:829`）；
2. 往一个空闲槽塞 `fee + 3` 枚星币（`PROBE:846`：`fee + ANVIL_EXTRA_IN_SLOT`，`ANVIL_EXTRA_IN_SLOT = 3`，见 `PROBE:691`）；
3. 另往一个空闲槽塞 `7` 枚星币作对照（`PROBE:849`：`ANVIL_RESERVE = 7`，见 `PROBE:693`）；
4. 打开铁砧菜单 → 用 `menu.clicked(...)` 把 **18/23/28 枚**搬进**附加槽（槽位 1）**、骰子搬进**第一输入槽（槽位 0）**（`PROBE:869-872`）；
5. 点结果槽（`PROBE:880`）→ 原版 `AnvilMenu#onTake` 结算；
6. 读 `_RIGHT_LEFT`（`PROBE:883`）、`_PUTBACK`（`PROBE:890-893`）；
7. `anvilCloseMenu`（`PROBE:809-816`）关界面 → 读 `_INV_AFTER_CLOSE`（`PROBE:897`）。

本次实跑的**完整**读数（`LOG:1352-1403`，非截断版）：

```
AP_S1_DICE_IN:astral_dice:dice:0      AP_S2_DICE_IN:astral_dice:dice:1      AP_S3_DICE_IN:astral_dice:dice:2
AP_S1_FEE:15:0->1                     AP_S2_FEE:20:1->2                     AP_S3_FEE:25:2->3
AP_S1_COINS_BEFORE:25                 AP_S2_COINS_BEFORE:30                 AP_S3_COINS_BEFORE:35
AP_S1_SRC:direct                      AP_S2_SRC:direct                      AP_S3_SRC:direct
AP_S1_ANVIL_IN:...:star_coin:18       AP_S2_ANVIL_IN:...:star_coin:23       AP_S3_ANVIL_IN:...:star_coin:28
AP_S1_RESULT:astral_dice:dice:1       AP_S2_RESULT:astral_dice:dice:2       AP_S3_RESULT:astral_dice:dice:3
AP_S1_TAKE:clicked:30:20              AP_S2_TAKE:clicked:30:20              AP_S3_TAKE:clicked:30:20
AP_S1_RIGHT_LEFT:...:star_coin:3      AP_S2_RIGHT_LEFT:...:star_coin:3      AP_S3_RIGHT_LEFT:...:star_coin:3
AP_S1_INV_AFTER_TAKE:7                AP_S2_INV_AFTER_TAKE:7                AP_S3_INV_AFTER_TAKE:7
AP_S1_CARRIED:astral_dice:dice:1      AP_S2_CARRIED:astral_dice:dice:2      AP_S3_CARRIED:astral_dice:dice:3
AP_S1_PUTBACK:ok:astral_dice:dice:1   ...
AP_S1_CLOSE:menuRemoved               AP_S2_CLOSE:menuRemoved               AP_S3_CLOSE:menuRemoved
AP_S1_INV_AFTER_CLOSE:7               AP_S2_INV_AFTER_CLOSE:7               AP_S3_INV_AFTER_CLOSE:7
```

`Z1` 相位（`LOG:1404-1413`）：

```
AP_Z1_SETUP:astral_dice:dice:0:coins=9:bags=1
AP_Z1_BAG_RESULT:empty          （拒收正确）
AP_Z1_BAG_RIGHT:astral_dice:star_coin_bag:1
AP_Z1_BAG_DICE:astral_dice:dice:0
AP_Z1_BAG_CLOSE:menuRemoved
AP_Z1_ERR:lost_after_close:-1:5 （PROBE:955 自报：关界面后既找不到骰子(ds=-1)，星币也只剩 5 枚(fs=5)）
```

两个关键事实：**① 取件路径完全正常**（右槽 18/23/28 恰好被扣掉 15/20/25 后剩 3，经验恰好 −10，骰子星级真实 +1）；
**② `_SRC` 全部为 `direct`**，不是 `block` —— 即铁砧菜单是用 `new AnvilMenu(id, inv)`（`PROBE:791-793`）
构造的**两参**菜单，而不是走铁砧方块自带的 `MenuProvider`。

---

## 1. 产品实现：本模组的铁砧代码在哪，如何消费附加槽星币

**在哪**

- 1.21.1：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/AnvilUpgradeHandler.java:108-147`
  （`@EventBusSubscriber` + `@SubscribeEvent public static void onAnvilUpdate(AnvilUpdateEvent event)`）
- 1.20.1：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/AnvilUpgradeHandler.java:104-142`（同构）

**它到底做了什么**（`AnvilUpgradeHandler.java:122-145`，原文）

```java
122:            if (!right.is(ModItems.STAR_COIN.get())) return;
123:            WeaponEnhancement enhancement = left.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
124:            if (enhancement.starLevel() >= 3) return;
125:            int req = switch (enhancement.starLevel()) {
126:                case 0 -> 15;
127:                case 1 -> 20;
128:                case 2 -> 25;
129:                default -> -1;
130:            };
131:            if (right.getCount() < req) return;
132:            ItemStack output = left.copy();
133:            output.set(ModDataComponents.WEAPON_ENHANCEMENT.get(), new WeaponEnhancement(... starLevel() + 1 ...));
142:            event.setOutput(output);
143:            event.setMaterialCost(req);
144:            event.setCost(10);
```

结论（**这一点对定论至关重要**）：

- 模组**从不自己动任何槽**。它只写事件对象的两个数：
  `event.setMaterialCost(req)`（材料费）与 `event.setCost(10)`（经验等级费）。
- `AnvilUpdateEvent.setMaterialCost(int)` 只是给私有字段 `materialCost` 赋值（`CLS1211:net/neoforged/neoforge/event/AnvilUpdateEvent.class`：
  `setMaterialCost(int) Code: 0: aload_0 / 1: iload_1 / 2: putfield #44 // Field materialCost:I / 5: return`）。
- 真正把它落到铁砧菜单字段上的是 NeoForge 的钩子（`CLS1211:net/neoforged/neoforge/common/CommonHooks.class`，
  `onAnvilChange` 字节码偏移 **71-102**）：

```
71: aload_3                       // resultSlots
72: iconst_0 / 73: event.getOutput() / 78: Container.setItem(0, output)
83: aload_0 / 84: event.getCost() / 89: AnvilMenu.setMaximumCost(long)
92: aload_0 / 93: event.getMaterialCost() / 98: putfield #745 // Field AnvilMenu.repairItemCountCost:I
```

- 也就是说：`setMaterialCost(15)` → **`AnvilMenu.repairItemCountCost = 15`**。
- 扣费发生在**原版** `AnvilMenu#onTake`（`SRC1211:net/minecraft/world/inventory/AnvilMenu.java:77-95`，原文）：

```java
77:    protected void onTake(Player player, ItemStack stack) {
78:        if (!player.getAbilities().instabuild) {
79:            player.giveExperienceLevels(-this.cost.get());
80:        }
82:        float breakChance = net.neoforged.neoforge.common.CommonHooks.onAnvilRepair(...);
84:        this.inputSlots.setItem(0, ItemStack.EMPTY);          // 第一输入槽：无条件清空
85:        if (this.repairItemCountCost > 0) {
86:            ItemStack itemstack = this.inputSlots.getItem(1); // 第二输入槽（附加槽）
87:            if (!itemstack.isEmpty() && itemstack.getCount() > this.repairItemCountCost) {
88:                itemstack.shrink(this.repairItemCountCost);   // 数量 > 费用 ⇒ 按费用 shrink
89:                this.inputSlots.setItem(1, itemstack);
90:            } else {
91:                this.inputSlots.setItem(1, ItemStack.EMPTY);  // 数量 ≤ 费用 ⇒ 整槽清空
92:            }
93:        } else {
94:            this.inputSlots.setItem(1, ItemStack.EMPTY);
95:        }
```

**回答「从槽里取走还是扣玩家背包」**：**从槽里取走**。模组没有任何「扣物品栏」的代码路径；
材料费只作用在 `inputSlots`（铁砧自己的 2 格容器）的第 1 格上，`player.getInventory()` 在整个扣费链路里
只被原版 `removed()/clearContainer()` 触碰（见第 2 条）。1.20.1 逐行相同
（`SRC1201:...AnvilMenu.java:69-87`，只是第 66 行的 `mayPickup` 与第 74 行的钩子换成 Forge 的 `ForgeHooks.onAnvilRepair`）。

**实跑数值自证**（`LOG:1356-1360`）：进 18 ⇒ 扣 15 ⇒ 剩 3；进 23 ⇒ 扣 20 ⇒ 剩 3；进 28 ⇒ 扣 25 ⇒ 剩 3。
`itemstack.getCount() (18) > repairItemCountCost (15)` 命中 `:88` 的 shrink 分支 ⇒ 观察值与代码一致。

---

## 2. 期望的正确行为是什么（原版语义取证）

### 2.1 原版关界面时对**两个输入槽**的处理

调用链：`ServerPlayer#closeContainer()` → `ServerPlayer#doCloseContainer()` → `AbstractContainerMenu#removed(Player)`
→ `ItemCombinerMenu#removed(Player)` → `clearContainer(player, inputSlots)`。

`SRC1211:net/minecraft/server/level/ServerPlayer.java:1234-1245`（原文）

```java
1234:    public void closeContainer() {
1235:        this.connection.send(new ClientboundContainerClosePacket(this.containerMenu.containerId));
1236:        this.doCloseContainer();
1237:    }
1240:    public void doCloseContainer() {
1241:        this.containerMenu.removed(this);
1242:        this.inventoryMenu.transferState(this.containerMenu);
1244:        this.containerMenu = this.inventoryMenu;
1245:    }
```

（对照：基类 `SRC1211:net/minecraft/world/entity/player/Player.java:506-508` 的 `closeContainer()` 只是
`this.containerMenu = this.inventoryMenu;`，**不调 `removed`** —— 所以「被关掉的菜单会退物品」全靠
`ServerPlayer` 的重写，这也是探针里 `AP_*_CLOSE` 走的是 `ServerPlayer` 分支的前提。）

`SRC1211:net/minecraft/world/inventory/ItemCombinerMenu.java:120-124`（原文）

```java
120:    @Override
121:    public void removed(Player player) {
122:        super.removed(player);
123:        this.access.execute((p_39796_, p_39797_) -> this.clearContainer(player, this.inputSlots));
124:    }
```

`SRC1211:net/minecraft/world/inventory/AbstractContainerMenu.java:579-592`（原文）

```java
579:    protected void clearContainer(Player player, Container container) {
580:        if (!player.isAlive() || player instanceof ServerPlayer && ((ServerPlayer)player).hasDisconnected()) {
581:            for (int j = 0; j < container.getContainerSize(); j++) {
582:                player.drop(container.removeItemNoUpdate(j), false);      // 死了/掉线 ⇒ 掉地上
583:            }
584:        } else {
585:            for (int i = 0; i < container.getContainerSize(); i++) {
586:                Inventory inventory = player.getInventory();
587:                if (inventory.player instanceof ServerPlayer) {
588:                    inventory.placeItemBackInInventory(container.removeItemNoUpdate(i));   // 否则 ⇒ 回物品栏
589:                }
590:            }
591:        }
592:    }
```

**关键点**：`container.getContainerSize()` 是**整个 `inputSlots` 容器**（铁砧为 2 格，
见 `SRC1211:AnvilMenu.java:57-64` 的 `ItemCombinerMenuSlotDefinition`：slot0=左输入、slot1=右输入、slot2=结果外置）。
`clearContainer` **循环 0..size-1**，**不区分**第一/第二输入槽 —— 所以：

> **原版对槽 0 与槽 1 的处理完全相同：关闭界面时两格里的东西都会被退回玩家物品栏**
> （玩家死亡/掉线时改为掉落在脚下）。原文 `SRC1211:AbstractContainerMenu.java:585-590`。

### 2.2 但这条回退被一个「静默 no-op」条件门控着 —— 这正是本次 FAIL 的根因

`ItemCombinerMenu.removed` 的回退**写在 `this.access.execute(...)` 里**（`SRC1211:ItemCombinerMenu.java:123`）。
`ContainerLevelAccess` 的契约是：

`SRC1211:net/minecraft/world/inventory/ContainerLevelAccess.java:9-37`（原文）

```java
 9: public interface ContainerLevelAccess {
10:     ContainerLevelAccess NULL = new ContainerLevelAccess() {
11:         @Override
12:         public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> p_39304_) {
13:             return Optional.empty();                 // ← NULL 的 evaluate 永远 empty
14:         }
15:     };
17:     static ContainerLevelAccess create(final Level level, final BlockPos pos) {
18:         return new ContainerLevelAccess() {
20:             public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> p_39311_) {
21:                 return Optional.of(p_39311_.apply(level, pos));   // ← 方块访问：真的执行
22:             }
23:         };
24:     }
26:     <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> levelPosConsumer);
32:     default void execute(BiConsumer<Level, BlockPos> levelPosConsumer) {
33:         this.evaluate((p_39296_, p_39297_) -> { levelPosConsumer.accept(p_39296_, p_39297_); return Optional.empty(); });
34:     }
```

`execute` 是 **default 方法**（`ContainerLevelAccess.java:32`），它把 `BiConsumer` 包进 `BiFunction` 再交给
`evaluate`；**`NULL` 覆写了 `evaluate` 让它返回 `Optional.empty()`，却完全没碰 `execute`** ⇒
`NULL.execute(任何 consumer)` **永远不执行那个 consumer**。字节码层面双证（`CLS1211`）：

```
// ContainerLevelAccess.execute（default）
   0: aload_0 / 1: aload_1 / 2: invokedynamic ... BiFunction / 7: invokeinterface #7 evaluate / 12: pop / 13: return
// ContainerLevelAccess$1（= NULL）只有 evaluate：
  public <T> java.util.Optional<T> evaluate(java.util.function.BiFunction<...>);
     0: invokestatic  #7  // Method java/util/Optional.empty
     3: areturn
```

**方块铁砧用的是 `create`**（真访问）：

`SRC1211:net/minecraft/world/level/block/AnvilBlock.java:76-80`（原文）

```java
76:    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
77:        return new SimpleMenuProvider(
78:            (p_48785_, p_48786_, p_48787_) -> new AnvilMenu(p_48785_, p_48786_, ContainerLevelAccess.create(level, pos)), CONTAINER_TITLE
79:        );
80:    }
```

**探针的 `direct` 路径用的是 `NULL`**：

`SRC1211:net/minecraft/world/inventory/AnvilMenu.java:48-55`（原文）

```java
48:    public AnvilMenu(int containerId, Inventory playerInventory) {
49:        this(containerId, playerInventory, ContainerLevelAccess.NULL);   // ← 两参构造 = NULL
50:    }
52:    public AnvilMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
53:        super(MenuType.ANVIL, containerId, playerInventory, access);
54:        this.addDataSlot(this.cost);
55:    }
```

`PROBE:790-796` 的兜底分支正是两参构造：

```js
790:    if (provider == null) {
791:        provider = new SimpleMenuProviderClass(function (id, inv, pl) {
792:            return new AnvilMenuClass(id, inv);      // ← 两参 ⇒ ContainerLevelAccess.NULL
793:        }, ComponentClass.literal("astral_probe_anvil"));
...
866:    send(ctx, "AP_" + tag + "_SRC:" + opened.src);    // 本次实跑全部打印 direct
```

### 2.3 逐一判定三种口径

| 口径 | 判定 | 依据 |
|---|---|---|
| **(b) 剩余物品本来就该留在铁砧槽里（`7` 才是正确值）** | **不成立** | 原版 `ItemCombinerMenu#removed` → `AbstractContainerMenu#clearContainer` 对 `inputSlots` 的**全部 2 格**做 `placeItemBackInInventory`（`SRC1211:ItemCombinerMenu.java:120-124` + `AbstractContainerMenu.java:585-590`）。铁砧界面是**临时容器**，关掉后输入槽不保留任何内容，玩家不可能在下次开界面时看到那 3 枚。 |
| **(a) 关界面时剩余物品应当退回玩家背包** | **成立（原版设计语义）** | 同上。且这是「真人右键铁砧方块」走 `AnvilBlock.java:76-80` 的 `ContainerLevelAccess.create` 时的**实际行为**。 |
| **(c) 应当被销毁/不退回，`lost_after_close` 另有含义** | **不成立** | 没有任何原版分支把输入槽「销毁」；只有 `player.drop(...)`（死亡/掉线）与 `placeItemBackInInventory(...)`（正常）两种去向（`AbstractContainerMenu.java:580-591`）。 |

**★ 最关键的一条原版依据（一次说清）**

- 铁砧的第一输入槽与第二输入槽在「关界面」这件事上**行为没有差别**：两者都属于同一个 `inputSlots`
  容器、都被同一个 `for (int i = 0; i < container.getContainerSize(); i++)` 循环覆盖
  （`SRC1211:AbstractContainerMenu.java:585-590`）。
- 差别只在**取件（`onTake`）**时：槽 0 无条件清空、槽 1 按 `repairItemCountCost` shrink
  （`SRC1211:AnvilMenu.java:84-95`）。
- 玩家把物品放进第二槽后**直接关界面**，原版**会还给他** —— 前提是该 `AnvilMenu` 携带真实的
  `ContainerLevelAccess`（即由铁砧方块打开的菜单，`AnvilBlock.java:78` 的 `create`）。
  用 `new AnvilMenu(id, inv)` 两参构造出来的菜单其 `access == NULL`，
  `ItemCombinerMenu.java:123` 的 `access.execute(...)` 变成**静默 no-op** ⇒ 两格都不退。

---

## 3. 那 3 枚星币的去向：算术与守恒

以 **S1** 为例（`LOG:1352-1365`），逐项对齐：

| 时刻 | 物品栏星币 | 铁砧附加槽(槽1) | 铁砧第一槽(槽0) | 备注 |
|---|---|---|---|---|
| `_SETUP` 后 / `_COINS_BEFORE` | 7（对照堆） | — | — | `PROBE:846-849`：`fee+3 = 18` 进 feeSlot，`7` 进 reserveSlot |
| 搬入铁砧后 `_ANVIL_IN` | 7 | **18** | 骰子 | `LOG:1357` `...:star_coin:18` |
| 取件后 `_RIGHT_LEFT` | 7（`_INV_AFTER_TAKE`，`LOG:1361`） | **3** | — | 18 − 15 = 3；`SRC1211:AnvilMenu.java:88` 的 shrink |
| 关界面后 `_INV_AFTER_CLOSE` | **7**（实测） | 3（容器已被丢弃） | — | `LOG:1365` |

**总账**（S1）：

```
25 = 7(物品栏对照) + 18(附加槽)
消费 = 15（★0→★1 的按等级扣费，AGENTS.md「★0→★3 每级 15/20/25」）
18 − 15 = 3 ← 恰好等于「放进去的数量 − 消费掉的数量」= 用例刻意多放的 3 枚
应得物品栏 = 7 + 3 = 10（= COINS_BEFORE − 费用 = 25 − 15）
实测物品栏 = 7      ⇒ 差额 3，与 `_RIGHT_LEFT=3` 一一对应
```

同一算式对 S2（`30 − 20 = 10`，实测 7，差 3，`_RIGHT_LEFT=3`，`LOG:1374-1383`）与
S3（`35 − 25 = 10`，实测 7，差 3，`_RIGHT_LEFT=3`，`LOG:1391-1400`）逐条复现。

**判定**：

- **不是「被吞了」**：15/20/25 的费用被 `onTake` 正确地按 `repairItemCountCost` 从槽里扣掉
  （`SRC1211:AnvilMenu.java:85-92`），扣费语义与用例期望**完全一致**；
  经验按 `cost=10` 扣（`LOG:1352` 的 `_TAKE:clicked:30:20`，`:79` 的 `giveExperienceLevels(-cost)`），
  骰子带 +1 星回到物品栏（`_PUTBACK:ok:astral_dice:dice:1`）。
- **差额就是「只是没退回」**：`fee+3` 里多出来的那 3 枚**确实没被消费**（否则 `_RIGHT_LEFT` 不会是 3），
  它们**停在了附加槽里**；然后因为本用例走的 `direct` 菜单 `access == NULL`，
  `removed()` 的退回分支被静默跳过（`SRC1211:ItemCombinerMenu.java:123` + `ContainerLevelAccess.java:12-14`），
  槽内容随菜单对象一起被丢弃 ⇒ 「没退回」而非「被扣掉」。
- **跨档累计**：S1/S2/S3 各残留 3 枚，本用例一轮共「未退回」`3 × 3 = 9` 枚；
  文档 `docs/batch3/B1-in-game-results.md:127` 的「恰好补齐差额」说法与上述逐档算术一致。

**Z1 尚未闭合的一处（如实记录，不猜）**：`AP_Z1_ERR:lost_after_close:-1:5`（`LOG:1410`）显示关界面后
物品栏星币只剩 **5** 枚，而 `_SETUP` 是 `coins=9`（`LOG:1404`）—— 少 4 枚，且骰子完全找不到
（`ds = -1`）。其中「骰子找不到」**与 no-op 结论完全自洽**：骰子在 `PROBE:960-961` 被搬进菜单第一输入槽后
从未被取回，`removed()` 又没退，自然不在 `p.getInventory()` 里。但「9 → 5」这 4 枚的**具体去向无法判定**：
静态证据里找不到能把它们移出物品栏的路径（`removed()` 是 no-op，`onTake` 未触发，没有 `drop()` 证据），
世界存档的区块数据里也查不到 `astral_dice:star_coin` 的掉落物实体。Z1 相位的日志在 `_BAG_CLOSE` 之后
立刻被 `_ERR` 截断（`_FEW_IN`/`_FEW_*`/`_DONE` 全部缺失），缺少可交叉验证的中间读数 ⇒
**该 4 枚差额记为「无法判定」，需要第 7 节的探针才能定论**。
这不影响第 2、4 条的结论：S1/S2/S3 三档各有完整的分步读数且互证（`_ANVIL_IN` 与 `_RIGHT_LEFT` 严格相差费用），
而 `ContainerLevelAccess.NULL.execute` 为 no-op 是**字节码级事实**，与 Z1 的差额无关。

---

## 4. 本模组是否有意为之 / 是否注入影响了 `removed`/`clearContainer`

**结论：没有任何「附加槽残留材料不退回」的设计说明，也没有任何注入。本模组不是差异的引入者。**

逐项取证：

1. **全仓唯一的铁砧相关模组代码**就是 `AnvilUpgradeHandler`（两版本各一份）。
   全仓 `setMaterialCost` 只出现 2 处：`neoforge-1.21.1/.../AnvilUpgradeHandler.java:143`、
   `forge-1.20.1/.../AnvilUpgradeHandler.java:138`；`repairItemCountCost` 在模组源码里出现 **0** 次。
2. **没有任何 Mixin 指向铁砧/容器菜单**。1.21.1 的 mixin 清单
   `neoforge-1.21.1/src/main/resources/astral_dice.mixins.json:6-20` 全部为
   `EntityThunderHitMixin` / `LightningBoltStrikeScopeMixin` / `trade.Merchant*` / `PiglinAiMixin` /
   3 个 client mixin；`astral_dice.neoforge_fixes.mixins.json:6-8` 只有 `LivingEntityDamageContainerMixin`。
   本次实跑的 MiXin 应用日志也印证：`run/1.21.1/logs/debug.log` 里 `Mixing ... from astral_dice.mixins.json into ...`
   的落点只有 `Minecraft`/`Entity`/`EffectRenderingInventoryScreen`/`ServerPlayer`/`LightningBolt`/
   `MerchantMenu`/`MerchantResultSlot`/`Gui`/`MerchantOffer`/`MerchantContainer` ——
   **没有 `AnvilMenu`/`ItemCombinerMenu`/`AbstractContainerMenu`/`ContainerLevelAccess`**。
3. **模组代码里唯一的 `removed(...)` 覆写与铁砧无关**：`screen/CardInventoryMenu.java:379`（1.21.1）/
   `:369`（1.20.1），是本模组自己的卡牌栏菜单。
4. 代码与注释里搜不到任何「附加槽残留不退回 / 材料不退 / 关界面吞材料」的设计说明；
   `AnvilUpgradeHandler` 的注释（`:115`、`:149-168`）只讲白名单覆盖范围与 tooltip 格式。
5. `AnvilUpdateEvent` 这条路径的**杠杆只有** `setOutput / setMaterialCost / setCost`
   （`CLS1211:AnvilUpdateEvent` 字段清单：`left/right/name/output/cost/materialCost/player`），
   其中 `materialCost` 只影响 `AnvilMenu.repairItemCountCost`（`CommonHooks.onAnvilChange` 偏移 92-98），
   而 `repairItemCountCost` **只被 `onTake` 读**（`SRC1211:AnvilMenu.java:85,87,88`）——
   它在「关界面」路径上从不参与 ⇒ **模组在结构上不可能影响本次现象**。
6. **还有一条独立于模组的「关界面路径」异常，同样指向测试脚手架**：
   所有 `AP_*_CLOSE` 读数都是 **`menuRemoved`**（`LOG:1364,1382,1399,1409`），而 `PROBE:809-816` 只有在
   `p.closeContainer()` **抛异常**时才会落到 `how = "menuRemoved"`：

   ```js
   809: function anvilCloseMenu(p) {
   810:     var how = "unavailable";
   811:     try { p.closeContainer(); how = "closeContainer"; }
   812:     catch (e1) {
   813:         try { p.containerMenu.removed(p); how = "menuRemoved"; } catch (e2) { /* 都不可用 */ }
   814:     }
   815:     return how;
   816: }
   ```

   即：本次实跑里 `closeContainer()` 是**抛异常**的，退回动作是靠 `p.containerMenu.removed(p)`
   直接调用的。这只影响「哪条路径执行」，不改变 `removed()` 的 no-op 语义（两参构造的菜单
   `access == NULL`，`SRC1211:ItemCombinerMenu.java:123` 依然是 no-op），
   但 `closeContainer()` 为何抛异常属于**测试脚手架问题**（在 KubeJS 的 `Player` 对象上调用
   `ServerPlayer#closeContainer()`；`run/1.21.1/logs/latest.log` 与 `debug.log` 在该时段
   均未落任何 ERROR/堆栈，异常被探针 `catch` 静默吞掉）——建议第 7 节的探针一并把
   `e1` 的堆栈打印出来，这也是修复 6.2 的前置信息。

⇒ **本次 FAIL 不是产品缺陷**；`_SRC:direct` 是用例/探针侧的路径选择，
`AP_*_SRC` 断言本身写的是 `(block|direct)`（`scripts/test/cases/ANVIL-STAR-UPGRADE-1.21.1.json:87,137,187`），
即**用例允许降级到 direct**，但**后续断言仍按 block 语义（物品能退回）来要求**，
两者自相矛盾 —— 这是**用例期望（判据）写错**的部分：它不是「`7` 才对」，
而是「这条路径无法验证退回语义，应该判为路径不合格/换路径」。
另外 `AP_Z1_ERR` 的成因（`PROBE:953-955`：`anvilFindSlot(dice) < 0` 即判「物品丢了」）
在 `direct` 路径下**必然触发**（骰子还在槽 0 里，从未回到物品栏），这也是探针自身的判据缺陷。

---

## 5. 跨版本（1.20.1）静态比对

**结论：同一段实现同构；原版 `removed/clearContainer/ContainerLevelAccess` 语义两版本一致，
所以 1.20.1 若跑同一用例会得到同样的 `direct` 现象（同一个探针缺陷）。**

| 环节 | 1.21.1 | 1.20.1 | 是否相同 |
|---|---|---|---|
| 模组事件处理器 | `neoforge-1.21.1/.../AnvilUpgradeHandler.java:110-147`（NeoForge `AnvilUpdateEvent`） | `forge-1.20.1/.../AnvilUpgradeHandler.java:106-142`（Forge `AnvilUpdateEvent`） | 同构（仅 `ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(stack, EMPTY)` 的组件 vs NBT 键写法差异，见 `:119` vs `:123`） |
| 费用写入 | `setMaterialCost(req)` `:143` / `setCost(10)` `:144` | `setMaterialCost(req)` `:138` / `setCost(10)` `:139` | 相同 |
| 扣费落点 | `SRC1211:AnvilMenu.java:85-92` | `SRC1201:AnvilMenu.java:77-84` | **逐行相同**（槽0无条件清空；槽1 `getCount() > repairItemCountCost` 则 shrink 否则清空） |
| 关界面退回 | `SRC1211:ItemCombinerMenu.java:120-124`（`access.execute(() -> clearContainer(player, inputSlots))`） | `SRC1201:ItemCombinerMenu.java:108-113`（同） | 相同 |
| `clearContainer` | `SRC1211:AbstractContainerMenu.java:579-592` | `SRC1201:AbstractContainerMenu.java:576-589` | 相同（`hasDisconnected()` ⇒ drop；否则 `placeItemBackInInventory`；循环覆盖整个容器） |
| `ContainerLevelAccess.NULL` | `SRC1211:ContainerLevelAccess.java:10-15`（`evaluate` → `Optional.empty()`）+ `:32-37` default `execute` | `SRC1201:ContainerLevelAccess.java:10-24` + `:30-36` | 相同；`javap CLS1201` 亦证 `ContainerLevelAccess$1` **只有 `evaluate` 返回 `Optional.empty()`**，`execute` 是接口 default（`invokeinterface evaluate` + `pop`） ⇒ `NULL.execute` 同为 **no-op** |
| 方块铁砧菜单来源 | `SRC1211:AnvilBlock.java:76-80`（`ContainerLevelAccess.create`） | `SRC1201:AnvilBlock.java` 同构（`getMenuProvider` → `new AnvilMenu(id, inv, ContainerLevelAccess.create(level, pos))`） | 相同 |

**1.20.1 侧仅有的实质差异（与本次现象无关）**：
`SRC1201:AnvilMenu.java:66` 的 `mayPickup` 用 `player.getAbilities().instabuild || player.experienceLevel >= cost.get()`，
而 1.21.1 用 `player.hasInfiniteMaterials() || ...`（`SRC1211:AnvilMenu.java:73`，等价语义）；
1.21.1 的 `setMaximumCost` 收 `long`（`:316-318`）而 1.20.1 收 `int`（`:312` 附近）——只是 NeoForge/Forge 补丁签名差异。

**已确认的实跑状态**：1.20.1 侧**本次没有跑过**该用例（`temp/b1_cases/` 下无 `ANVIL-STAR-UPGRADE-1.20.1.log`；
`run/1.20.1/logs/latest.log`（最后写入 2026-09-15 14:03）里搜不到任何 `AP_S1_/AP_Z1_/INV_AFTER_CLOSE/lost_after_close` 读数）。
用例文件 `scripts/test/cases/ANVIL-STAR-UPGRADE-1.20.1.json` 存在且断言逐条对等
（其 `:12` 的 note 明确说断言语义两版本对等）。

---

## 6. 最小修法（**仅建议，未实施**）

### 6.1 若判为「产品缺陷」—— 本调查**不支持**这条，但按要求给出

唯一能「让铁砧附加槽残留必定退回」的改法是在模组侧兜底，属于**为测试补丁原版行为**，
不推荐（会与「真人方块铁砧本来就会退」重复，且需要自建菜单跟踪状态）：

- 文件：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/AnvilUpgradeHandler.java`
  + `forge-1.20.1/.../AnvilUpgradeHandler.java`
- 做法：新增一个 `PlayerContainerEvent.Close` 监听（NeoForge）/ `PlayerContainerEvent.Close`（Forge），
  当 `event.getContainer() instanceof AnvilMenu menu` 时把 `menu.getSlot(1).getItem()` 取出并
  `player.getInventory().placeItemBackInInventory(...)`（并清槽）。
- 风险：与 `ItemCombinerMenu#removed` 的 `clearContainer` 可能**双重退回**（真方块路径下会复制物品），
  必须加「仅当 access 为 NULL / 槽仍非空」的守卫 —— 复杂度与风险都高于收益。**不建议采用。**

### 6.2 若判为「用例期望/路径判据错」—— 本调查支持这条

只动用例与探针（**不碰产品代码**），三处最小改动：

1. **把「必须走 block 路径」变成硬前置**，而不是允许 `direct` 兜底：
   `scripts/test/cases/ANVIL-STAR-UPGRADE-1.21.1.json:87,137,187`
   的 `AP_S{1,2,3}_SRC:(block|direct)` 改为 `AP_S{1,2,3}_SRC:block`；
   同时修 `PROBE:781-800`/`PROBE:768-774` 里让 `getMenuProvider` 返回 null 的原因
   （`PROBE:786-788`：`p.blockPosition().relative(p.getDirection())` 放下铁砧后立刻取
   `getBlockState(pos).getMenuProvider(level, pos)`；本次实跑 100% 落到 `direct`，
   需要查清是放置位置被占用、方向朝向不可替换方块，还是取 provider 的时机问题）。
2. **给 `direct` 路径下的退回语义单独写断言**，不要用 block 语义要求它：
   例如把 `AP_S*_INV_AFTER_CLOSE:10` 换成「按 `_SRC` 分流」的两条断言，
   direct 时只断言 `_RIGHT_LEFT`（扣费正确性），block 时才断言 `_INV_AFTER_CLOSE:10`。
3. **修 `AP_Z1_ERR` 的误报成因**：`PROBE:953-955` 把「物品栏里找不到骰子」直接判成
   `lost_after_close`，在 direct 路径下骰子本来就在铁砧槽 0（`PROBE:960-961` 才把它搬进菜单），
   应改为「关界面后若 `_SRC == block` 才要求物品回物品栏；direct 时把槽内容当作已随菜单丢弃，
   读 `menu.getSlot(0)` 取回再继续」，否则 Z1 相位必然提前 return（`_FEW_*`/`_DONE` 永远缺失）。
   （这一条就是 53 条断言里 4 条 `AP_Z1_FEW_*`/`AP_Z1_DONE` 未命中的直接原因。）

> 两条路线互斥；本调查的依据（第 2、4 条）指向 **6.2**。

---

## 7. 是否需要游戏内补充证据（以及需要什么）

**需要，但只是一条「探针自证」级的补充，用来闭合两件事：**

1. **闭合 Z1 的 `9 → 5`**（第 3 节末尾），并顺带验证 `removed()` 的 no-op 行为：
   建议在探针里加一个**只读**读数，不引入新的被测逻辑：
   - 关界面前后各打印一次「物品栏星币总数 + 铁砧槽 0/1 内容 + 世界里以玩家为中心 8 格内的
     `ItemEntity` 列表（id/count/pos）」——若关界面后有 `star_coin` 掉落物，说明是 `drop()` 路径；
     若没有且槽内容仍在本地引用里，则确认是「随菜单丢弃」。
   - 同时打印 `menu.access == ContainerLevelAccess.NULL`（或用 `javap` 已证的等价判据：
     `AP_*_SRC` 是否为 `direct`）。
2. **验证真人路径（方块铁砧）确实会退回**：
   让探针真的走通 `block` 路径（`AP_*_SRC:block`），此时 `_INV_AFTER_CLOSE` 应恰为 `10`。
   这是把第 2 条结论从「静态 + 字节码」升级为「游戏内实证」的唯一一步；
   **若 block 路径实测仍为 7，则本报告的结论需要推翻**，请立刻回报。
3. **顺带查清 `closeContainer()` 为何抛异常**（第 4 节第 6 点）：在 `PROBE:811-812` 的 `catch (e1)` 里
   加一行 `print(e1)` / 把 `exText(e1)` 打进 `AP_*_CLOSE` 读数即可 —— 这是 6.2 的
   「让 block 路径真正跑通」是否能成立的前置信息（若异常来自探针自身而非菜单，
   那 `direct` 兜底也不会消失，必须先修这一条）。

在拿到 1、2 两条之前，本报告对「3 枚星币的去向」的判定建立在
`SRC1211:ItemCombinerMenu.java:120-124` + `ContainerLevelAccess.java:10-15,32-37`
（及其 1.20.1 对应物）与 `PROBE:791-793` 的 `direct` 事实之上，属于**静态可判定**部分。
