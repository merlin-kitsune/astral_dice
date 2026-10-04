# P5 — §E 饰品装卸五路径 + §G 敌对/目标选择/联动 + §H 全局静态状态(第二轮只读审计)

---

> # 🔴 勘误(2026-09-15):Curios `onUnequip` 参数语义 —— 本文件 §1.2 / §1.4 / §2 的相关系列结论作废
>
> **勘误针对的原始结论**:原 §1.2 第 109 行与 §1.4(第 159 / 169–170 行)称
> 「`ICurioItem.onUnequip` 第 2 参 = `getStack()`(占用槽位的栈);第 3 参 = 调用方传入的 X」,
> 并据此指控"本仓与首轮报告对第 2 参的描述与实现**相反**",判为 blocker,进而推出 §2 各单元格「判据恒 false ⇒ 清理永不执行」。
> **该前提是反的,上述结论整体作废。**
>
> **正确语义(两加载器缓存 jar 的 `javap -c -p` 字节码取证,非注释/非猜测)**:
> `ICurioItem.onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack)` ——
> **第 2 参 `newStack` = 调用方传给 `ICurio.onUnequip(ctx, X)` 的那个 `X`**(将要占槽的栈,或 `ItemStack.EMPTY`);
> **第 3 参 `stack` = `getStack()` = 该 `ICurio` 能力自己持有的栈 = 被卸下的那一件饰品。**
> ⇒ **仓库 `item/CurioSlotUtil.isIntentionalUnequip` / `runOnIntentionalUnequip` 用的是第 3 参,是对的;
> 仓库实现与代码注释正确,本文件 §1.2 / §1.4 的散文标注有误。**
>
> **实际取证的 jar**:`curios-IPQlZkz1.jar`(Forge 1.20.1,`META-INF/mods.toml`)、
> `curios-yohfFbgD.jar`(NeoForge 1.21.1,`META-INF/neoforge.mods.toml`),均取自
> `~/.gradle/caches/modules-2/files-2.1/maven.modrinth/curios/`。第 1)~5) 条所述方法的字节码在两 jar 中一致(例外见第 6 条)。
>
> **1) `common/capability/ItemizedCurioCapability.class` —— 唯一的桥(两 jar 完全一致)**
>
> ```text
> public void onUnequip(SlotContext, ItemStack);
>    0: aload_0
>    1: getfield   // curioItem:ICurioItem
>    4: aload_1    // 第 1 形参 SlotContext
>    5: aload_2    // ← 2 参重载收到的 X            → ICurioItem 的【第 2 形参】
>    6: aload_0
>    7: invokevirtual getStack()                    // → ICurioItem 的【第 3 形参】
>   10: invokeinterface ICurioItem.onUnequip:(SlotContext;ItemStack;ItemStack;)V
> ```
>
> ⇒ **第 2 参 = X,第 3 参 = `getStack()`**。原 §1.2 第 109 行把两者写反了。
>
> **2) `common/event/CuriosEventHandler.class`(tick —— 真实卸下/换装的主路径)**
>
> - Forge 1.20.1:`167: getStackInSlot → astore 12`(当前槽位栈)、`256: getPreviousStackInSlot → astore 15`(上一 tick 快照);
>   `541: aload 16`(用**上一刻快照** `getCurio` 得到的 ICurio)+ `545: aload 12`(当前槽位栈)→
>   `invokedynamic #29` = `BootstrapMethods 29 = lambda$tick$31` → `ICurio.onUnequip(ctx, 当前槽位栈)`。
> - NeoForge 1.21.1:`218: getStackInSlot → astore 14`、`283: getPreviousStackInSlot → astore 16`;
>   `593: aload 17` + `597: aload 14` → `BootstrapMethods 25 = lambda$tick$22` → `ICurio.onUnequip(ctx, 当前槽位栈)`。
>
> ⇒ **X = 当前槽位内容**:真实卸下时为 `ItemStack.EMPTY`,换装 A→B 时为 `B`;
> 而 `getStack()` 取的是**上一 tick 快照**(= 被卸下的那一件)。原 §1.3 表把它标成 `previousStack` 是**命名错误**,数值本身无误。
>
> **3) `common/inventory/CurioStacksHandler.class`(`loseStacks` —— 槽位收缩)**
>
> ```text
> Forge    lambda$loseStacks$4(SlotContext, ICurio):
>   0: aload_1 / 1: aload_0 / 2: getstatic ItemStack.f_41583_(=EMPTY)
>   5: invokeinterface ICurio.onUnequip:(SlotContext;ItemStack;)V
> NeoForge lambda$loseStacks$9(SlotContext, ICurio): 同上(getstatic ItemStack.EMPTY)
> ```
>
> 调用侧 `getCurio` 用的是**被移除的那件**(Forge `301: aload 6`;NeoForge `314: aload 6`),故 `getStack()` = 被移除者;
> 而 **X = `ItemStack.EMPTY`**。⇒ **本文件原第 174 行「`ItemStack.EMPTY` 永远不会作为 `onUnequip` 的实参出现」不成立**
> —— EMPTY 确实会出现,只是出现在**第 2 参**。(第 3 参永远不是 EMPTY。)
>
> **4) `common/network/client/CPacketDestroy.class`(Forge;经 `lambda$handle$0`)/ `common/network/server/CuriosServerPayloadHandler.class`(NeoForge;经 `handleDestroyPacket` → `lambda$handleDestroyPacket$8`)**
> 能力与 X 都取自**同一个槽位栈**(Forge `280/287: aload 10`;NeoForge `299/304: aload 9`)⇒ `(X, getStack())` 同一实例、同一物品。
> 触发方为 `client/gui/GuiEventHandler.onMouseClick`,是**创造模式背包的"销毁物品"格**,不是普通 GUI 卸下。
>
> **5) 穷举核对**:对两 jar 的**全部** `.class` 做字节串扫描,提到 `onUnequip` 的仅
> `ICurio` / `ICurioItem` / `ItemizedCurioCapability` / `CuriosEventHandler` / `CurioStacksHandler` / `CPacketDestroy`(Forge)
> 或 `CuriosServerPayloadHandler`(NeoForge)六类。
> **`CuriosEventHandler.handleDrops` 不调用 `onUnequip`**(只 `setStackInSlot(i, EMPTY)`)
> ⇒ §2.1 ⑤ / §2.2 ⑤ / §2.3 ⑤ 关于「`handleDrops` 先 `onUnequip` 再清槽」的描述**不成立**。
>
> **6) 附注(易误用)**:`ICurioItem.onUnequip(ctx,a,b)` 的**默认实现**两版本不同 —— Forge 1.20.1 版转发
> `ICurio.onUnequip(String,int,LivingEntity,ItemStack)` 用 `13: aload_3`(第 3 参);NeoForge 1.21.1 版转发
> `ICurio.onUnequip(SlotContext,ItemStack)` 用 `4: aload_2`(第 2 参)。那是"物品侧默认实现 → ICurio"方向的桥,
> 与上面第 1 条(调用方 → 物品)是**两条不同的桥**,不可混用(§1.1 的 dump 出自 Forge jar,对 1.21.1 不适用)。
>
> **实际后果(取代原 E1 的"全部清理失效"结论)**:`isIntentionalUnequip(newStack, removed)`
> 在 ①(GUI 卸下 / tick 路径,`(EMPTY, 被卸下者)`)、②(换装 A→B,`(B, A)`)、④(`loseStacks`,`(EMPTY, 被移除者)`)
> 三条路径上**返回 true、清理照常执行**;仅
> 「同一物品、组件在槽内被改动( tick 路径 `(当前栈, 同物品快照)` )」与
> 「创造模式销毁格(`(同一实例, 同一实例)` )」两支被跳过 —— 与仓库代码注释所述完全一致。
> 本条与 `AGENTS.md` 的「饰品卸下(`onUnequip`)的参数语义与已知缺口(2026-09-15)」一致:
> tick 路径调用的是 `prevCurio.onUnequip(ctx, 槽位**当前**内容)`,故 GUI 卸下时第 2 参 = `EMPTY`、第 3 参 = `getPreviousStackInSlot` 的 copy 快照,
> 立牌的**物品组件**归零会写进副本而丢失(玩家侧清理如护甲修饰器/静态累计表仍照常执行)。
>
> **本勘误确实影响 §2**:§2.1 / §2.2 / §2.3 的若干单元格、§2.4 的 E1/E2 结论块与表中所有标注「❌ E1 跳过」的格
> 均已一并就地改正(见各处的 `> 🔴 **勘误(2026-09-15)**` 标注)。
> **§3(§G 敌对判定/目标选择)与 §5(§G3/G4 联动)的全部结论不受本勘误影响**;
> §6 仅两行「E1」验证步骤的**预期观察点**随之改写;§4(§H)除 §4.1 的 Jasmine 一行、
> H2 第 ② 条、§4.2 第 1 条与 §7 第 11 条(它们复述了 §1.3「无 EMPTY 实参」这句)之外,其余结论不受影响。
> ⚠️ 但请注意:因 EMPTY 确实会作为第 2 参出现,**H2「EMPTY 污染不可达」与 H-C19/H-C20 的最终判定本轮未重新作出**,
> 该问题已回退为"待核验"(根因是基线判据在 `X = EMPTY` 时是否放行,属 §5.2 第 2 条的未核验项)。

> 范围:仓 `F:\MCProject\astral_dice_multiloader`,分支 `multi-1.20.1-1.21.1`,双版本 `neoforge-1.21.1`(下称 **A**)+ `forge-1.20.1`(下称 **B**)。
> 本文件是本次唯一允许写入的文件;未修改任何源码/资源/配置。
> 上游清单:`docs/execution-order-checklist.md` §E / §G / §H,结论格式见该文档 §I。
> 首轮扫描:`docs/interaction-audit-1.2.1.md`(S6-C1)、切片 `docs/audit-slices/S6-sign-active-dice.md`。
> 同批报告:`docs/scan2/P1-cooldowns-timers.md`、`docs/scan2/P3-events-priority-cancel.md`。
> 并行子代理取证:`docs/scan2/_work/G34-linkage.md`(§G3/G4,已摘要并入 §5)、`docs/scan2/_work/H-static-state.md`(§H 补充,见 §4.2 与勘误)。

---

## 0. 仓库状态(两次采样,含"改动中"标注)

```text
$ git rev-parse HEAD
d48a529ba5fcf14610dc7b7383a10780dbe49168

$ git rev-parse --abbrev-ref HEAD
multi-1.20.1-1.21.1

$ git diff --name-only          # 采样 T0(本代理开始,~T0)
forge-1.20.1/.../event/PlayerLifecycleHandler.java
forge-1.20.1/.../resource/ResourceConversion.java
neoforge-1.21.1/.../event/PlayerLifecycleHandler.java
neoforge-1.21.1/.../resource/ResourceConversion.java

$ git diff --name-only          # 采样 T1(审计进行中,~T1):32 个已改文件
combat/{DiceCombatEvents,DiceCombatModifiers,HostileTargets}.java
event/{EnderDiceHandler,PlayerLifecycleHandler,PlayerTickEvents}.java
item/CurioSlotUtil.java, item/card/EffectCardPeriod.java, item/chip/BaseChipItem.java,
item/dice/{DiceCurioItem,NetherStarDiceItem}.java,
item/sign/{BaseSignItem,BonnieSignItem,HaiqingSignItem,MosesSignItem}.java,
resource/ResourceConversion.java
(两子项目同名各一份)

$ git status --porcelain        # 新增未跟踪(采样 T2 已增至 4 个新增,status 共 40 项)
?? {neoforge-1.21.1,forge-1.20.1}/src/main/java/.../combat/PlayerHostilityTracker.java
?? {neoforge-1.21.1,forge-1.20.1}/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json
```

**关键**:**HEAD 全程未变(仍是 `d48a529`)**,但工作区在审计进行中由 4 文件**扩张到 32 已改 + 4 新增**(T2 采样共 40 项),即那批"13 项改动"在本代理第 1 次采样之后才落盘,**且到本报告落盘时仍在继续写入**。

因此本报告采用双基线策略:

- **§E/§G 结论以 T1 后的工作区形态为准**(因为这些文件正是本批改动对象),并逐条标注"改动中";
- **§H 结论以基线 `d48a529` 为准**(经 `git show d48a529:<path>` 复算),因为静态状态与工作区改动无关、基线行号稳定;
- 凡引用行号,均在文中注明取自**基线**还是**工作区**。**行号会随并发批次漂移,复算时请优先用 `grep` 关键字定位。**

| 文件 | 改动中? | 与本报告关系 |
|---|---|---|
| `item/CurioSlotUtil.java` | **改动中(A+B)** | S6-C1 修法本体(新 `isIntentionalUnequip`;基线为 `isRealUnequip`) |
| `item/sign/BaseSignItem.java` | **改动中(A+B)** | `onUnequip` 改签名 + 改判据 + 改清理对象 |
| `item/chip/BaseChipItem.java` | **改动中(A+B)** | `onUnequip` 改签名,改传第 3 参 |
| `item/dice/DiceCurioItem.java` | **改动中(A+B)** | `onUnequip` 改传第 3 参 |
| `item/dice/NetherStarDiceItem.java` | **改动中(A+B)** | 仅改签名;**属性注销段未变** |
| `combat/HostileTargets.java` | **改动中(A+B)** | 新增 `isHostile(Entity viewer, Entity target)` 两参重载 |
| `combat/PlayerHostilityTracker.java` | **新增(未跟踪)** | 敌对玩家静态记录表(`HOSTILE_ATTACKERS`) |
| `combat/DiceCombatEvents.java` | **改动中(A+B)** | 溅射/标靶改两参 `isHostile` |
| `combat/DiceCombatModifiers.java` | **改动中(A+B)** | 调查阶段加成改两参 `isHostile` |
| `item/sign/{MisakiSignItem,JasmineSignItem,PadmanSignItem}.java` | **未改动** | `clearSignData` 的组件写入(§E 核心证据) |
| `event/PlayerLifecycleHandler.java` | **改动中(A+B)** | 死亡清理降 `LOWEST`;死亡只清 `MISAKI_SIGN_STACKS` |

> 标注「未改动」的文件 = 本报告对其的结论取自**干净工作树**,不受那批改动污染。

---

## 1. §E 基础事实:Curios `onUnequip` 的真实参数语义(字节码取证)

本节是整张矩阵的地基。**证据来源为两加载器缓存 jar 的 `javap -c` 字节码**(非注释、非文档、非猜测)。

### 1.1 接口签名(两版本一致,参数名出自 `LocalVariableTable`)

```text
top/theillusivec4/curios/api/type/capability/ICurioItem.class
  public default void onEquip(SlotContext, ItemStack, ItemStack)
  public default void onUnequip(SlotContext, ItemStack, ItemStack)
  public default void onUnequip(SlotContext, ItemStack)          // 2 参重载
```

`ICurioItem.onUnequip` 3 参 **桥接到 2 参**(`javap -c` 原文;参数槽 `aload_1`=ctx、`aload_3`=第 3 参):

```text
  public default void onUnequip(SlotContext, ItemStack, ItemStack);
       0: aload_0
       1: aload_1
       2: invokevirtual  // SlotContext.identifier:()Ljava/lang/String;
       5: aload_1
       6: invokevirtual  // SlotContext.index:()I
       9: aload_1
      10: invokevirtual  // SlotContext.entity:()Lnet/minecraft/world/entity/LivingEntity;
      13: aload_3                      // ← 第 3 参 → ICurio.onUnequip 的 ItemStack 参数
      14: invokeinterface // ICurio.onUnequip:(Ljava/lang/String;ILnet/minecraft/world/entity/LivingEntity;)V
```

### 1.2 `ICurio` → `ICurioItem` 的转发(唯一的桥)

```text
top/theillusivec4/curios/common/capability/ItemizedCurioCapability.class
  public void onUnequip(SlotContext, ItemStack);
       0: aload_0
       1: getfield      // curioItem:ICurioItem
       4: aload_1
       5: aload_2                      // ← 2 参重载收到的 ItemStack
       6: aload_0
       7: invokevirtual // getStack:()Lnet/minecraft/world/item/ItemStack;
      10: invokeinterface // ICurioItem.onUnequip:(SlotContext;ItemStack;ItemStack;)V
```

⇒ **`ICurioItem.onUnequip` 的第 2 参 = `ICurio.onUnequip(ctx, X)` 里的 `X`(调用方传入的"将要占槽的栈");第 3 参 = `getStack()`(该能力自己持有的栈 = 被卸下的那一件)。**

> 🔴 **勘误(2026-09-15)**:以上 `javap -c` dump 本身是**对的**(`5: aload_2` 进第 2 形参、`7: getStack()` 进第 3 形参),
> 但本节原散文结论把它写反了 —— 原句为
> 「⇒ **`ICurioItem.onUnequip` 的第 2 参 = `getStack()`(当前占用该槽位的栈);第 3 参 = `ICurio.onUnequip(ctx, X)` 里的 `X`。**」。
> **正确表述见上句;仓库实现取第 3 参的做法是正确的。**

### 1.3 全仓仅 3 个类调用 `ICurio.onUnequip`(穷举)

对 Curios jar 全量 class 逐个 `javap -c`,匹配 `ICurio.onUnequip` 与 `setStackInSlot` 的**共现**,命中且只有:

| 调用者 | 2 参实参 | 紧邻动作 |
|---|---|---|
| `common/event/CuriosEventHandler`(经 `lambda$tick$31`) | **当前槽位栈**(Forge `aload 12` = `getStackInSlot(i)`;真实卸下时为 `EMPTY`、换装 A→B 时为 `B`)| `lambda$tick$32`(onEquip)传 **`previousStack`** |
| `common/inventory/CurioStacksHandler`(经 `lambda$loseStacks$4`) | **`ItemStack.EMPTY`**(`lambda$loseStacks$4` 体内 `getstatic ItemStack.f_41583_`;局部 `6` 是被移除栈,但它进的是**第 3 参**) | `onUnequip` 在 `:313`,`setStackInSlot(i, EMPTY)` 在 `:322`(**先回调后清槽**) |
| `common/network/client/CPacketDestroy`(经 `lambda$handle$0`) | **被卸下的 `stack`**(局部 `10`,由 `getStackInSlot` 取) | `onUnequip(ctx, stack)` → `setStackInSlot(i, EMPTY)` |

> 🔴 **勘误(2026-09-15)**:上表前两行的「2 参实参」原分别写作 **`previousStack`(槽位上一 tick 的栈)** 与 **被移除的栈(局部 `6`)**,
> 二者都错:tick 路径捕获的是**当前槽位栈**(`aload 12`/`aload 14`),`loseStacks` 捕获的是**常量 `ItemStack.EMPTY`**。
> 正确值见上表(第三行 `CPacketDestroy` 原本就是对的)。字节码见文件顶部勘误块。

`CuriosEventHandler.tick` 的关键字节码(**`previousStack` 与 `newStack` 的区分**,这是全部结论的枢轴):

```text
     252: aload  7
     254: iload  9
     256: invokeinterface // IDynamicStackHandler.getPreviousStackInSlot:(I)Lnet/minecraft/world/item/ItemStack;
     261: astore 15                      // 15 = previousStack(上一 tick)
     263: aload 12                       // 12 = newStack(本 tick 槽内实际内容)
     265: aload 15
     267: invokestatic  // ItemStack.matches:(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z
     270: ifne 803                       // 未变化 → 跳过整段
     273: aload 15
     275: invokestatic  // CuriosApi.getCurio:(Lnet/minecraft/world/item/ItemStack;)LazyOptional;  ← 用的是 previousStack
     ...
     326: invokestatic  // syncCurios:(LivingEntity, ItemStack, ...)   ← 传的是 15 = previousStack
```

而两个 lambda(注意 `aload_2` = `ICurio` 实例,`aload_1` = 2 参实参):

```text
  private static void lambda$tick$32(SlotContext, ItemStack, ICurio);
       0: aload_2
       1: aload_0
       2: aload_1
       3: invokeinterface // ICurio.onEquip:(SlotContext;ItemStack;)V      ← previousStack(调用点在 :741 捕获 aload 15)

  private static void lambda$tick$31(SlotContext, ItemStack, ICurio);
       0: aload_2
       1: aload_0
       2: aload_1
       3: invokeinterface // ICurio.onUnequip:(SlotContext;ItemStack;)V    ← 当前槽位栈(调用点在 :547 捕获 aload 12)
```

> 🔴 **勘误(2026-09-15)**:上面两段 lambda 的字节码正确,但原行尾注释把捕获对象写反了
> (原文为 `← newStack` / `← previousStack`)。正确:tick 的 onEquip 捕获 `previousStack`、onUnequip 捕获**当前槽位栈**。

### 1.4 结论(§I 格式)

```text
ID:E0
核验:~~证实(字节码级)~~ → **勘误(2026-09-15):原结论作废,改为「仓库实现正确」**
现象:~~本仓与首轮报告对 Curios onUnequip 第 2 参的语义描述与实现**相反**。~~
     → **正确现象:第 2 参 = 调用方传入的 X;第 3 参 = `getStack()` = 被卸下的那一件。仓库实现(取第 3 参)正确。**
顺序依赖:无(是签名语义问题,不是时序问题)。
证据:A/B 共用同一 Curios API(2026-09-15 复取 `javap -c -p`;此处已就地更正原标注):
  - ItemizedCurioCapability.class(两 jar 相同):`5: aload_2`(2 参实参 X)→ **第 2 形参**;`7: getStack()` → **第 3 形参**
  - CuriosEventHandler.class:onUnequip 捕获**当前槽位栈**(Forge `545: aload 12`;NeoForge `597: aload 14`),
    onEquip 捕获 **previousStack**(Forge `739: aload 15`;NeoForge `805: aload 16`);
    ICurio 实例分别取自 `getCurio(previousStack)` / `getCurio(newStack)` ⇒ 第 3 参即"被卸下/被装上"的那一件
  - CurioStacksHandler.class:lambda$loseStacks$4(N)体内 `getstatic ItemStack.EMPTY` ⇒ **X = EMPTY**(非"被移除栈")
  - CPacketDestroy.class / CuriosServerPayloadHandler.class:lambda 传"被卸下的 stack"⇒ X 与第 3 参同实例
  - ICurioItem.class 的 `13: aload_3` 属**另一条桥**(物品侧默认实现 → `ICurio.onUnequip(String,int,LivingEntity,ItemStack)`),
    Forge jar 如此、NeoForge jar 为 `4: aload_2`;与本仓参数无关,见顶部勘误块第 6 条
可达性:100% 可达——所有 ICurioItem 实现的 onUnequip 都经该桥转发。
修法:不存在"需要用户先定夺口径"的余地:第 3 参 = `getStack()` = 被卸下的那一件;
     第 2 参 = 调用方传入的 X(真实卸下/loseStacks 时为 `ItemStack.EMPTY`,换装时为新栈)。
     清理**必须**用第 3 参 —— **仓库当前实现正是如此,无需修改**。
严重度:~~blocker(它使 S6-C1 修法的判据前提失效,见 E1)~~ → **info(原判为 blocker 属于误判;详见 E1 勘误)**
```

> 🔴 **勘误(2026-09-15)**:本结论块(原 E0)的「现象 / 修法 / 严重度」三行原先把第 2、3 参写反,并据此把仓库实现判为有缺陷。
> 正确表述已在上方就地改写:第 2 参 = X、第 3 参 = `getStack()`(= 被卸下者),仓库取第 3 参是对的。

**另一个由此推出的关键事实(经 2026-09-15 复取后修正)**:`ItemStackHandler.setStackInSlot` **不派发任何回调**;全仓同时出现 `onUnequip` 的类只有上表 3 类(+ NeoForge 的 `CuriosServerPayloadHandler` / 其 `deactivateSlot`),它们都是**显式**调用回调。
但 **`ItemStack.EMPTY` 确实会作为 `onUnequip` 的实参出现** —— 由 `CurioStacksHandler.loseStacks` 传在**第 2 参**(见顶部勘误块第 3 条),
故 `ItemStack.EMPTY` **永远不会**出现在**第 3 参**上(第 3 参恒为真实饰品栈)。

> 🔴 **勘误(2026-09-15)**:本段原句为「因此 `ItemStack.EMPTY` **永远不会**作为 `onUnequip` 的实参出现(见 H2 勘误)」——
> 该断言的正确范围是「永远不会作为**第 3 参**」;作为**第 2 参**它确实出现(`loseStacks`)。此错误同时传染 H2 第 ② 条与 §4.2 第 1 条,已在原处标注。

---

## 2. §E 装卸五路径矩阵

**矩阵单元格读法**:`该状态下调用的清理入口 → 传入的栈 → 是否真的清到"被卸下的那一件"`。

图例:✅ 正确;⚠️ 语义/行为可疑;❌ 错误(清不到或误清);⛔ 不可达(说明原因)。

### 2.1 立牌(16 种 `*SignItem`,基类 `BaseSignItem`,槽 `stand` size=1)

| 路径 | 触发的 Curios 回调与实参 | 清理入口 | 传入的栈 | 判定 |
|---|---|---|---|---|
| ① 玩家有意卸下 | `ICurio.onUnequip(ctx, 当前槽位内容=EMPTY)` → `BaseSignItem.onUnequip(ctx, newStack=EMPTY, stack=被卸下的那件)` | `isIntentionalUnequip` → `clearSignData(player, stack)` | 第 3 参 = **被卸下的那件**(正确) | ✅ **判据通过**(第 2 参为空)⇒ 清理**会执行**(原标 ❌ 见勘误);⚠️ tick 路径第 3 参是 `getPreviousStackInSlot` 的 **copy 快照**,物品组件写入落在副本上(已知缺口,见 §5.2 第 2 条) |
| ② 换装 A→B | 同上,`newStack = B`、第 3 参 = `A` | 同上 | 第 3 参 = **A**(正确) | ✅ `!B.is(A.getItem())` = true ⇒ 清理执行(原标 ❌ 见勘误) |
| ③ Curios 自身重载 from=to | tick 比对 `!ItemStack.matches(new, previous)` 才触发;同物品同一栈实例 ⇒ `matches` 为 true ⇒ **不触发** | — | — | ⛔ **该路径在 `from=to 同一栈实例` 时不触发 `onUnequip`**(设计如此);仅当"同物品但不同栈实例(组件不同)"才触发 |
| ④ Curios 自身导致(槽位收缩) | `CurioStacksHandler.loseStacks`:**`:313` 先 `onUnequip`,`:322` 再清槽** ⇒ 第 3 参 `getStack()` = 被移除的那件 | `clearSignData` | 第 3 参 = **被移除的那件**(正确) | ✅ **判据通过**(`lambda$loseStacks$4` 传的第 2 参是常量 `ItemStack.EMPTY`,不是 `removed`;原写 `(removed, removed)` 有误)⇒ 清理**会执行** |
| ⑤ 死亡/重生(掉落) | **`handleDrops` 不调用 `onUnequip`**(只 `setStackInSlot(i, EMPTY)`;全 jar 穷举已证)⇒ 本路径无回调 | `clearSignData`(仅在 `onUnequip` 内) | **无实参可传** | ⛔ **该路径不触发回调 ⇒ 清理不执行**;另见 §2.4(死亡走 `onCurioTick` 不跑,叠加) |

> 🔴 **勘误(2026-09-15)**:上表 ①②④ 的判定列原写「❌ 见 E1:…**跳过清理**」「⇒ `(removed, removed)`」,其前提(第 2 参 = `getStack()`)是反的,故原结论作废:
> ①②④ 的第 2 参实际分别为 `EMPTY` / `B` / `EMPTY`,判据返回 true,**清理会执行**。⑤ 的 `handleDrops` **根本不调用 `onUnequip`**(原文「`:269-276` 先 `onUnequip` 再清槽」有误)。
> 字节码见文件顶部勘误块。**唯一仍然成立的缺口**:① 的物品组件写入落在 tick 快照副本上(§5.2 第 2 条未核验项)。

**A/B 证据(改动中,以下为 T1 后形态;行号取自工作区)**

```text
A item/sign/BaseSignItem.java
:214    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
:217        if (!CurioSlotUtil.isIntentionalUnequip(newStack, stack)) return;
:218        clearSignData(player, stack);
B item/sign/BaseSignItem.java :215/:218/:219(逐行同构)
```

改动前的旧形态(**基线 `d48a529`**,即首轮 S6-C1 所分析的对象):

```text
$ git show d48a529:neoforge-1.21.1/.../item/sign/BaseSignItem.java
:165    public void onUnequip(SlotContext slotContext, ItemStack stack, ItemStack prevStack) {
:168-173    boolean stillInSlot = ... h.getStacks().getStackInSlot(index).getItem() == stack.getItem() ...;
:174-177    if (stillInSlot) return;          // 重载场景:不清理
:178        clearSignData(player, stack);    // stack 实参 = 第 2 参 = 调用方传入的 X(真实卸下/loseStacks 时为 EMPTY)
```

**旧实现的真实缺陷(与首轮报告的表述不同,需修正)**

旧代码把第 2 参(=`getStack()`,槽位当前栈)当作"被卸下的物品"传给 `clearSignData`。结合 §1.3:

- `loseStacks` / `CPacketDestroy` / `handleDrops` 三条路径都在**清槽之前**回调 ⇒ `getStack()` **恰好就是**被卸下的那件 ⇒ 旧代码在这三处**清对了栈**;
- `stillInSlot` 用 `getItem() == stack.getItem()` 的**引用比较**:`loseStacks`/`handleDrops` 场景 `getStack()` 与实参是同一引用 ⇒ 判 true ⇒ **跳过清理**(与设计意图"重载不清理"一致,故这两条路径实际不清理);
- 玩家从 GUI 真正卸下时,`CurioSlot.set()` 传入的可能是**另一份副本**(`new ItemStack(...)`/`copy()`)⇒ 引用不等 ⇒ 判 false,此时 `clearSignData` 收到的 `stack` 是那份副本,组件归零写到副本上——**是否真的清不到原件,取决于 Curios 传入的是否为原槽内引用,该点未核验**(见 §5.2 第 2 条)。
- ⇒ 旧代码的行为是**反的**:真正的"有意卸下"可能清错对象,"系统导致"也清(取决于引用是否同一)。

> 🔴 **勘误(2026-09-15)**:本小节的参数标注建立在本文件原「第 2 参 = `getStack()`」的反向前提上,故更正三点:
> (a) 基线传给 `clearSignData` 的是**第 2 参 = 调用方传入的 X**,不是 `getStack()`;真实 GUI 卸下与 `loseStacks` 两路的 `X = ItemStack.EMPTY`;
> (b) **`handleDrops` 不调用 `onUnequip`**,不属"三条路径"(见顶部勘误块第 5 条);
> (c) 因此基线 `stillInSlot`(拿**当前槽位内容**的 item 与 X 的 item 比较)在"真实卸下"格的实际取值,
> 以及它是否会把组件写进 `ItemStack.EMPTY`,取决于 Curios 侧传引用还是传副本 —— 这正是 §5.2 第 2 条的未核验项,
> **本轮未重新判定**;原句「旧代码的行为是**反的**……」的最终结论请勿直接引用。

### 2.2 筹码(50 个 `*ChipItem`,基类 `BaseChipItem`,槽 `chip` 动态 0..7)

| 路径 | 清理入口 | 传入的栈 | 判定 |
|---|---|---|---|
| ① 有意卸下 | `runOnIntentionalUnequip(newStack=EMPTY, stack=被卸下者, player, p -> onChipUnequip(p, stack))` | 第 3 参 | ✅ 判据通过 ⇒ `onChipUnequip` **会执行**(16 处全为玩家级清理,无需读栈;原标 ❌ 见勘误) |
| ② 换装 A→B | 同上,`newStack = B`、第 3 参 = `A` | 第 3 参 | ✅ 清理执行 |
| ③ 自身重载 | 同立牌:同栈实例 ⇒ 不触发回调 | — | ⛔ 不触发 |
| ④ **筹码槽收缩(骰子降星/卸骰子)** | `DiceCurioItem.setSlotCount(target, forceRemove=false)` **在移除槽有物品时 `return` 不收缩**;仅 `forceRemove=true`(玻璃骰死亡 / 卸骰子)走 `setStackInSlot(i, EMPTY)` + `shrink()` | 第 3 参 | ⚠️ 见 E2:`forceRemove=false` 时降星**不会**挤出筹码 ⇒ 该格不可达;`forceRemove=true` 路径可达,`loseStacks` 传 `(EMPTY, 被移除者)` ⇒ **清理会执行**(原「被 E1 跳过」有误) |
| ⑤ 死亡/重生 | **`handleDrops` 不调用 `onUnequip`** | **无实参** | ⛔ 该路径不触发回调 ⇒ 清理不执行 |

> 🔴 **勘误(2026-09-15)**:上表 ① ② ④ 判定列原写「❌ 同 E1(判据恒 false ⇒ 跳过)」「`forceRemove=true` 路径可达但被 E1 跳过」,前提(第 2 参 = `getStack()`)是反的,故原结论作废:
> ①②④ 的第 2 参实际分别为 `EMPTY` / `B` / `EMPTY`,判据返回 true,**筹码侧清理照常执行**(且这 16 处全部只读 `player`,不依赖栈身份)。⑤ 的 `handleDrops` **不调用 `onUnequip`**。
> 字节码见文件顶部勘误块。

```text
A item/chip/BaseChipItem.java
:79     public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
:83         CurioSlotUtil.runOnIntentionalUnequip(newStack, stack, player, p -> onChipUnequip(p, stack));
B item/chip/BaseChipItem.java :80/:84(逐行同构)
基线形态:git show d48a529:... = `:79 onUnequip(ctx, ItemStack curio, ItemStack newStack)` + `:83 runOnRealUnequip(slotContext, curio, ...)`
```

**16 个 `onChipUnequip` 覆写逐个核对:全部只读 `player`,无一读 `stack`**

```text
A item/chip/AdrenalineChipItem.java:71-74       setDefenseArmorBonus(player, "adrenaline_def_armor"+bonus, 0)
A item/chip/CandyChipItem.java:47-49            setCandyChipPlayBonusActive(player,false)
A item/chip/CursedSwordChipItem.java:70-75      setCursedSwordBonus / setCursedSwordBlessingTriggered + removeBlueCurse
A item/chip/EightSidedDiceChipItem.java:18-20   setEightSidedAccum(player,0)
A item/chip/ElectricSwordChipItem.java:54-58    KILL_COUNTS.remove(player.getUUID())          ← 静态表
A item/chip/EnergyRecyclerChipItem.java:64-68   clearTracking + removeSpeedBonus
A item/chip/FlashlightChipItem.java:84-86       clearGrantedTargets(player)
A item/chip/MagicQuiverChipItem.java:77-79      setMagicQuiverTracking(player,false)
A item/chip/MagicTomeChipItem.java:65-68        setMagicTomeUseCount(player,0) + remove effect
A item/chip/MedkitCompleteChipItem.java:18-20   空实现
A item/chip/MedkitEmergencyChipItem.java:18-20  空实现
A item/chip/PiggyBankChipItem.java:46-48        setPiggyBankUseCount(player,0)
A item/chip/PrimordialCoreChipItem.java:50-53   setDefenseArmorBonus + EmpowerManager.removeAll
A item/chip/RevengeHalberdChipItem.java:90-93   setDefenseArmorBonus(player,"revenge_halberd_def_armor",0)
A item/chip/SatelliteChipItem.java:81-84        setSatellitePlayBonusActive(player,false)
A item/chip/StarCoinHammerChipItem.java:209-211 setStarCoinHammerBonus(player,0)
```

⇒ **S6-C1 修法"改传第 3 参"对筹码侧无行为差异**(16 处全不读该参);~~**但 E1 的判据反转对筹码侧有真实后果**:这 16 项清理**全部**不再触发。~~

> 🔴 **勘误(2026-09-15)**:划除句不成立 —— 判据在 ①②④ 三条路径上返回 true,这 16 项玩家级清理**照常执行**(见上表与顶部勘误块)。

### 2.3 骰子(13 种,基类 `DiceCurioItem` + `NetherStarDiceItem`,槽 `dice` size=1)

| 路径 | 清理入口 / 实参 | 传入的栈 | 判定 |
|---|---|---|---|
| ① 有意卸下 | `DiceCurioItem.onUnequip` → `tryRemoveChipBonus(slotContext, stack)`(第 3 参);`NetherStarDiceItem.onUnequip` → 清 `dice_nether_star_dice_armor` / `dice_nether_star_dice_attack` | 第 3 参 | ✅ **正确**(该栈从未被读取,仅用于 `setChipSlotCount` / 属性注销,与栈身份无关) |
| ② 换装 A→B | 同上 | 同上 | ✅ |
| ③ 自身重载 | 同栈实例不触发回调 | — | ⛔ 不触发 |
| ④ 骰子自身导致 | 无 ④(骰子槽恒 1;立牌栏亦恒 1,不做动态调整 —— 见 `DiceCurioItem:75` 注释) | — | ⛔ 不可达 |
| ⑤ 死亡/重生 | ①玻璃骰:`removeGlassDiceOnDeath` 显式 `setStackInSlot(0, EMPTY)`;②其余骰:**`handleDrops` 不调用 `onUnequip`**(原写「→ `onUnequip`」有误,见顶部勘误块第 5 条) | 第 3 参 | ⚠️ ①(玻璃骰)走显式路径 ✅;②其余骰在该路径**无回调** ⇒ §2.4 同行原标「✅ 经 `handleDrops`→`onUnequip`」需一并更正 |

```text
A item/dice/DiceCurioItem.java
:81     public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
:84         tryRemoveChipBonus(slotContext, stack);
A item/dice/NetherStarDiceItem.java
:62     public void onUnequip(SlotContext slotContext, ItemStack curio, ItemStack prevStack) {
:67         DiceCombatModifiers.setDefenseArmorBonus(player, ARMOR_MODIFIER_KEY, 0);
:68         setAttackBonus(player, 0);
```

> ⚠️ `NetherStarDiceItem` **未随本批改动重命名参数**(仍是 `curio`/`prevStack`),但其体内**只用 `slotContext.entity()`**,不读任何 stack ⇒ 命名误导无行为后果。

### 2.4 属性修饰器 / 数据组件注销五格对称性(§E5)

| 载体 | 注销动作 | ① 有意卸下 | ② 换装 | ③ 重载 | ④ 自身导致 | ⑤ 死亡/重生 |
|---|---|---|---|---|---|---|
| `NetherStarDiceItem` 星级属性 | `setDefenseArmorBonus(...,0)` + `setAttackBonus(player,0)`(`:67-68`) | ✅ | ✅ | ⛔ 不触发回调 ⇒ 靠 `curioTick` 每 20 tick 重算(`:57-58`) | ⛔ | ⚠️ 原标「✅ 经 `handleDrops`→`onUnequip`」**不成立**(`handleDrops` 不回调)⇒ 该路径不执行属性注销,后果本轮未实测 |
| `MisakiSignItem` `MISAKI_SIGN_STACKS` | `stack.set(...,0)`(`:44`) | ✅ 判据通过(⚠️ 组件写入落在 tick 快照副本上,见 §5.2) | ✅ 判据通过 | ⛔ | ✅ 判据通过(立牌栏恒 1,该格本不可达) | ✅ **有替代路径**:`PlayerLifecycleHandler:147-154`(`:151-152`)显式清该组件 |
| `JasmineSignItem` `JASMINE_ATK/DEF_BONUS` + `jasmine_def_armor` + `lastPosMap/walkAccumMap` | `:51-55` | ✅ 判据通过 | ✅ | ⛔ | ✅ | ⚠️ **死亡清理清单里没有 Jasmine**(只清 Misaki)⇒ 残留 |
| `PadmanSignItem` `PADMAN_ATK/DEF_BONUS`/`LAST_REFRESH` + `padman_def_armor` | `:50-53` | ✅ 判据通过 | ✅ | ⛔ | ✅ | ⚠️ **同上,死亡清单独缺** |
| 各立牌 `*_def_armor`(papara/moses/nancy_lu 等) | `clearSignData` 内 | ✅ 判据通过 | ✅ | ⛔ | ✅ | ⚠️ 死亡清单独缺 |

> 🔴 **勘误(2026-09-15)**:上表原标的所有「❌ E1 跳过」**全部作废**(E1 前提反了,判据在 ①②④ 返回 true ⇒ 注销/清理**会执行**),
> 已就地改为「✅ 判据通过」。`NetherStarDiceItem` ⑤ 的「✅ 经 `handleDrops`→`onUnequip`」亦作废(`handleDrops` 不调用 `onUnequip`)。
> **仍然成立**的两条缺口:① 立牌的**物品组件**写入落在 tick 快照副本上(§5.2 第 2 条);② 死亡清理清单缺 Jasmine/Padman。

```text
ID:E1
核验:~~证实(字节码 + 代码逐行)~~ → **🔴 勘误(2026-09-15):原结论作废,本项不是缺陷**
现象:~~S6-C1 的修法把 Curios onUnequip 第 2 参当作"将要占用槽位的新栈",据此写判据:
     `if (newStack == null || newStack.isEmpty()) return true;`
     `return !newStack.is(removedStack.getItem());`
     但第 2 参是 getStack()(槽位当前栈),在①②④⑤四条路径上与第 3 参**恒为同一对象**
     ⇒ `newStack.is(removedStack.getItem())` 恒为 true ⇒ 返回 false ⇒
     **clearSignData / onChipUnequip 永不执行**。~~
     → **正确现象**:第 2 参 = 调用方传入的 X,第 3 参 = `getStack()` = 被卸下的那一件;
     ①②④ 的第 2 参分别为 `ItemStack.EMPTY` / `B` / `ItemStack.EMPTY` ⇒ 判据返回 **true**、
     **清理照常执行**;仅"同一物品组件在槽内被改动"与"创造模式销毁格"两支跳过(与仓库注释一致)。
     ⇒ **S6-C1 的修法正确,无需改动。**
顺序依赖:判据在清理之前(`:217`)。~~反转后果 = 全部清理失效(即当前状态)~~ → 无反转、无后果。
证据:A item/CurioSlotUtil.java(改动中,行号取工作区)
:100    public static boolean isIntentionalUnequip(ItemStack newStack, ItemStack removedStack) {
:103        if (removedStack == null || removedStack.isEmpty()) return false;
:105        if (newStack == null || newStack.isEmpty()) return true;
:107        return !newStack.is(removedStack.getItem());
     ~~字节码依据见 §1(ICurioItem 桥 aload_3;ItemizedCurioCapability aload_2 + getStack();
     lambda$tick$31 传 previousStack;lambda$loseStacks$4 / lambda$handle$0 传被移除栈)。~~
     → 字节码依据见文件顶部勘误块(`ItemizedCurioCapability`: `5: aload_2` → 第 2 形参、`7: getStack()` → 第 3 形参;
     tick 的 onUnequip 捕获**当前槽位栈**;`lambda$loseStacks$4` 传常量 `ItemStack.EMPTY`)。
可达性:①(玩家从饰品 GUI 取下)②(换装)④(玻璃骰死亡收缩筹码槽)全部可达**且清理生效**;
     ⑤(死亡掉落)**该路径不触发 `onUnequip`**;
     ③(from=to 同一栈实例)**不可达**(ItemStack.matches 为 true 时不触发回调)。
修法:~~清理判据不能用"第 2 参与第 3 参是否同一物品"。当前证据下 ①②④⑤ 都应当清理
     ⇒ 最小修法是**恢复无条件清理**(判据恒 true)。
     若"重载不清理"确实需要,唯一可用的真实信号是死亡态(`player.isDeadOrDying()` /
     `slotContext.entity().isAlive()`),但需先找到一条**真会触发回调**的重载路径。~~
     → **无需修法**:现行判据语义正确(第 3 参为空 ⇒ 不清理;第 2 参为空或与被卸下者非同一物品 ⇒ 真实移除 ⇒ 清理;
     第 2 参非空且同物品 ⇒ Curios 槽内触发 ⇒ 不清理)。原提的"恢复无条件清理"会破坏注释所述的重载保护。
严重度:~~blocker~~ → **info(误判作废;余下唯一真实缺口是 tick 路径的组件写入落在快照副本上,属 §5.2 第 2 条未核验项)**

⚠️ **同物品不同组件实例的换装(边缘场景,需指令构造)**:
CuriosEventHandler.tick 的触发条件是 `!ItemStack.matches(newStack, previousStack)`,而 ItemStack.matches
比较物品 **+ 全部组件**。故"同名立牌但组件不同"(如把带 2 层剑气的护法立牌换成背包里另一枚 0 层的护法立牌)
会 `matches == false` ⇒ **确实触发** onUnequip(第 3 参 = A) + onEquip(第 3 参 = B)。
此时 `isIntentionalUnequip(B, A)` 里 `B.is(A.getItem())` 为 true ⇒ 返回 false ⇒ 跳过(该场景下"跳过"是对的)。
旧实现在此场景 `stillInSlot == true` ⇒ 同样跳过,故该边缘行为**未变**。
触发门槛:`BaseSignItem.canEquip` 用 `CurioSlotUtil.hasSameItemEquipped` 拒绝**同物品**重复装备
⇒ 玩家无法同时拥有两枚同类立牌,只能由指令/其它模组构造。**严重度:low。**

> 🔴 **勘误(2026-09-15)**:本 E1 结论块原判「blocker:①②④⑤ 清理全部失效」,唯一依据是「第 2 参 = `getStack()`,与第 3 参恒为同一对象」;
> 该依据经字节码复核**是反的**(第 2 参 = 调用方传入的 X)⇒ **E1 作废,`isIntentionalUnequip` 无需修改**。
> 上文「同物品不同组件实例的换装」一段原写 `onUnequip(previous=A) + onEquip(new=B)`(按旧语义),已按正确语义改为第 3 参。
```

```text
ID:E2
核验:证实
现象:筹码槽收缩有"防御模式"——`forceRemove=false` 时,只要被移除的槽位区间内有任意物品就**整体放弃收缩**
     (`return`),`shrink()` 根本不调用 ⇒ 筹码不会被挤出。故"骰子降星把筹码挤出"这一格**不可达**。
     真正的挤出只发生在 `forceRemove=true` 的两处:玻璃骰死亡、卸骰子。
顺序依赖:无。
证据:A item/dice/DiceCurioItem.java
:136-151  private static void setSlotCount(Player player, ICurioStacksHandler handler, int target, boolean forceRemove) {
:138          if (current > target) {
:139              if (!forceRemove) {
:141                  for (int i = target; i < current; i++) { ... if (!getStackInSlot(i).isEmpty()) { hasItems = true; break; } }
:151                  if (hasItems) return;      // ← 放弃收缩
:152              } else { ... 归还背包 + setStackInSlot(i, EMPTY) ... }
:168          handler.shrink(current - target);
B forge-1.20.1 item/dice/DiceCurioItem.java 同构
可达性:降星挤出**不可达**;玻璃骰死亡(forceRemove=true)可达,~~但被 E1 跳过~~ ⇒
     `loseStacks` 传 `(ItemStack.EMPTY, 被移除者)`,判据返回 true ⇒ **被挤出筹码的 onChipUnequip 会执行**(勘误见顶部)。
修法:~~先修 E1;~~ 随后确认 forceRemove=true 时 shrink() 内部 loseStacks 的 onUnequip 能拿到被移除栈
     (字节码已证:回调在清槽之前,且第 3 参 `getStack()` 即被移除者 —— Forge `301: getCurio(aload 6)` → `:322 setStackInSlot(i, EMPTY)`)。
严重度:medium(把"降星会丢数据"当结论会误导修复方向;真实可达路径在玻璃骰死亡)
```

---

## 3. §G 敌对判定与目标选择

### 3.1 两参重载的语义(改动中)

```text
A combat/HostileTargets.java(改动中,新增 :45-64)
:52     public static boolean isHostile(Entity viewer, Entity target) {
:53         if (target == null) return false;
:55         if (isHostile(target)) return true;                  // 敌对生物 ∪ 被激怒中立生物
:56         if (!(viewer instanceof Player viewerPlayer)) return false;
:57         if (!(target instanceof Player targetPlayer)) return false;
:58         if (viewerPlayer == targetPlayer) return false;
:60-61      if (viewerPlayer.getTeam() != null && viewerPlayer.isAlliedTo(targetPlayer)) return false;
:62         return PlayerHostilityTracker.hasAttacked(viewerPlayer, targetPlayer);
```

### 3.2 `PlayerHostilityTracker`(新增)

```text
A combat/PlayerHostilityTracker.java(新增,未跟踪;行号取 T1 工作区)
:49     private static final Map<UUID, Set<UUID>> HOSTILE_ATTACKERS = new HashMap<>();
:59-69  @SubscribeEvent onLivingDamagePre(LivingDamageEvent.Pre)  → 记录 attacker → victim 名单
:72-77  @SubscribeEvent(priority = LOWEST) onLivingDeath          → forget(uuid)(:74 `if (event.isCanceled()) return;`)
:80-84  @SubscribeEvent onPlayerClone(wasDeath)                   → forget(uuid)
:87-91  @SubscribeEvent onPlayerLoggedOut                         → forget(uuid)
:93     public static boolean hasAttacked(Player victim, Player attacker) {
:95         Set<UUID> attackers = HOSTILE_ATTACKERS.get(victim.getUUID());
:96         return attackers != null && attackers.contains(attacker.getUUID());
```

### 3.3 `isHostile` 全部调用点清单(改动后最终形态,共 25 处实调用)

**图例**:`✔2` = 已用两参重载(有上下文);`✘1需改` = **上下文可用却仍用单参** ⇒ 因本轮"玩家也算敌对"的新口径而漏判;`—1` = 单参语义正确(该处确实不需要上下文);`⚠️` = 需用户定夺。

| # | 位置(A;行号取 T1 工作区) | 当前形态 | 上下文可用? | 判定 |
|---|---|---|---|---|
| 1 | `combat/DiceCombatEvents.java:321`(标靶筹码:赐福后对最近敌对目标标记) | `isHostile(player, living)` | — | ✔2 |
| 2 | `combat/DiceCombatEvents.java:642`(大当家溅射) | `isHostile(player, e)` | — | ✔2 |
| 3 | `combat/DiceCombatEvents.java:1019`(`isBlessingTarget`) | `isHostile(target)` | 有 `player` 形参 | —1(**有意**:下一行 `target instanceof Player` 分支已单独处理队伍口径) |
| 4 | `combat/DiceCombatEvents.java:1125`(肉弹战车嘲讽反击) | `isHostile(attacker)` | **有 `player`(受害者)** | **✘1需改** ⇒ 玩家攻击者被排除,嘲讽反击对 PvP 失效 |
| 5 | `combat/DiceCombatModifiers.java:403`(调查阶段加成) | `isHostile(ctx.attacker, ctx.target)` | — | ✔2 |
| 6 | `combat/SpellDamageRegistry.java:249`(**定向爆破**周围 6 格) | `isHostile(e)` | **有 `ctx.attacker`** | **✘1需改** ⇒ PvP 中定向爆破打不到敌对玩家 |
| 7 | `combat/SpellDamageRegistry.java:289`(贯穿之铳) | `isHostile(ctx.target)` | **有 `ctx.attacker`** | **✘1需改** ⇒ 对被攻击玩家不加成 |
| 8 | `combat/SpellDamageRegistry.java:370`(**电击手套** 3 格扩散) | `isHostile(e)` | **有 `ctx.attacker`** | **✘1需改** ⇒ PvP 电击扩散漏玩家 |
| 9 | `damage/RailgunBolts.java:58` | `isHostile(target)` | 需查调用方签名 | **✘1存疑**(见 §5.2 第 1 条) |
| 10 | `item/chip/AdrenalineChipItem.java:96` | `isHostile(attacker)` | 是伤害来源,受害者为佩戴者 | —1(语义:敌人打我 ⇒ 单体判定成立) |
| 11 | `item/chip/CursedSwordChipItem.java:122` | `isHostile(target)` | **有击杀者 `player`** | **✘1需改** ⇒ "击杀 ≥20 血敌对目标"对玩家目标不发奖 |
| 12 | `item/chip/ElectricSwordChipItem.java:79` | `isHostile(event.getEntity())` | 击杀者为佩戴者 | —1(击杀计数,单参可接受) |
| 13 | `item/chip/FanBigChipItem.java:46`(**手持风扇-大**:16 格标记) | `isHostile(e)` | **有 `player` 形参** | **✘1需改** ⇒ PvP 不标记敌对玩家 |
| 14 | `item/chip/FanSmallChipItem.java:33`(**手持风扇-小**) | `isHostile(e)` | **有 `player` 形参** | **✘1需改** |
| 15 | `item/chip/FlashlightChipItem.java:53`(手电筒 +1 星光) | `isHostile(target)` | **有攻击者** | **✘1需改** |
| 16 | `item/chip/RailgunChipItem.java:103` | `isHostile(target)` | **有攻击者** | **✘1需改** |
| 17 | `item/chip/RailgunChipItem.java:145`(电磁炮 3 格) | `isHostile(e)` | **有攻击者** | **✘1需改** |
| 18 | `item/chip/SatelliteChipItem.java:102`(探天卫星) | `isHostile(target)` | **有攻击者** | **✘1需改** |
| 19 | `item/chip/SmartWatchChipItem.java:51` | `isHostile(event.getEntity())` | 击杀者为佩戴者 | —1 |
| 20 | `item/sign/BonnieSignItem.java:113` | `isHostile(killed)` | **有击杀者 `player`** | **✘1需改** ⇒ 秘密侦探"击杀 ≥20 血敌对目标发牌"对玩家目标不发 |
| 21 | `item/sign/LuluSignItem.java:76`(史莱姆:敌对生物施缓慢) | `isHostile(entity)` | 有施放者 | —1(设计上"对敌对生物",玩家不在此列) |
| 22 | `item/sign/NancyLuSignItem.java:214`(赐福结束时 6 格内无敌对生物才 +3 攻) | `isHostile(e)` | **有 `player`** | ⚠️**语义待定夺**:若"玩家也算敌对",会改变赐福结束的攻/防分支 |
| 23 | `item/sign/NancyLuSignItem.java:255`(骇客隐身攻击解除) | `isHostile(target) \|\| target instanceof Player` | 有 | ⚠️`instanceof Player` **过宽**:同队/未激怒玩家也解除隐身(应改两参) |
| 24 | `item/sign/NancyLuSignItem.java:268`(同上,伤害路径) | `isHostile(victim) \|\| victim instanceof Player` | 有 | ⚠️同上 |
| 25 | `item/sign/PandamanSignItem.java:105`(肉弹战车嘲讽 16 格) | `isHostile(e) && !(e instanceof Player)` | 有 | ⚠️**显式排除玩家** ⇒ 与"玩家也算敌对"新裁决**直接冲突**(AGENTS.md 称"嘲讽永不会施加给玩家") |

**范围波及类效果的过滤顺序核对(§G2)**

| 效果 | 过滤顺序 | 主目标是否被过滤掉 |
|---|---|---|
| 大当家溅射 `DiceCombatEvents:639-642` | 先 `getEntitiesOfClass(AABB, isHostile(player,e) && isAlive)` → 再逐个 `hurt`,并对 `victim == target` 临时清零 `invulnerableTime`(`:655-663`) | 否。对**敌对生物主目标**为 true;对**玩家主目标**仅在"曾攻击过我"时为 true ⇒ 未先攻击过我的玩家主目标**仍被剔除**(与首轮 G1/G2 结论一致,本轮未修复) |
| 定向爆破 `SpellDamageRegistry:246-250` | 条件里已 `e != ctx.target` ⇒ **AOE 明确排除主目标**(主目标由主伤害结算) | 否(设计如此) |
| 电击手套 `SpellDamageRegistry:369-370` | 同上 `e != ctx.target` | 否(设计如此) |
| 标靶筹码 `DiceCombatEvents:318-328` | 全维度实体扫描 → `isHostile(player, living)` → 取最近 | 不适用(选最近目标) |

**`EventTargetCollector` 口径是否被误用(§G5)**:未发现。`HostileTargets.isHostile(viewer,target)` 内**没有**调用 `EventTargetCollector`;其同队豁免走 `viewerPlayer.getTeam() != null && isAlliedTo(...)`,`:60` 注释亦显式声明"EventTargetCollector 的『未加入队伍视为全服玩家』只适用于发奖,不适用于此处"。⇒ **该口径未被错误套用**(负结论,避免误改)。

```text
ID:G1
核验:证实
现象:本轮把"非同队且曾主动攻击过你的玩家"纳入敌对(两参 isHostile),但**只有 5 处调用点**升级为两参
     (`DiceCombatEvents:321/642`、`DiceCombatModifiers:403`),**另有 12 处上下文可得却仍用单参**。
     后果:同一份"谁视谁为敌"的裁决,在溅射/调查阶段加成生效,而在定向爆破、电击手套、手持风扇、
     电磁炮/贯穿之铳、手电筒、探天卫星、诅咒之剑发奖、秘密侦探发牌、肉弹战车反击上**完全不生效**。
顺序依赖:过滤发生在伤害/发奖之前;两参与单参混用导致同一 tick 内不同子系统对同一对玩家给出**相反**的敌对结论。
证据:A(改动后)
  ✔ 两参:combat/DiceCombatEvents.java:321, :642;combat/DiceCombatModifiers.java:403
  ✘ 单参(上下文可得):见 §3.3 表中 12 个「✘1需改」行(逐条给了 文件:行号 + 原文)
  B(forge-1.20.1)与 A 逐处对应(行号 ±1~4)
可达性:两参重载可达性 = ①对手先打过我(LivingDamageEvent.Pre 记录)+ ②非同队 + ③在范围内。
  玩家↔玩家:受伤方戴任意立牌/筹码,对手先用近战/远程打中受伤方,再由受伤方触发溅射/风扇/电击手套。
  ⚠️ **首击不生效**:第一次被打时名单尚空,只有"被打过一次之后"才把对手视为敌对。
修法:把上列 12 处逐一改为两参重载(参数取当前作用域内的 player/ctx.attacker/击杀者)。
     另需定夺:NancyLuSignItem:255/268 的 `|| victim instanceof Player` 应否收敛为两参;
     PandamanSignItem:105 的 `!(e instanceof Player)` 与新裁决冲突,是否解除。
严重度:high
```

```text
ID:G2
核验:证实
现象:PlayerHostilityTracker 的"主动攻击过"记录挂在 LivingDamageEvent.Pre(最终伤害阶段)。
     HostileTargets.isHostile(viewer,target) 的首次判定发生在**对手第一次打中你之后**;
     而"被最前置取消的攻击"(骇客隐身免疫 / 枪匠破绽闪避)**不产生该事件**,按设计不计入。
顺序依赖:记录写于伤害结算中(LivingDamageEvent.Pre,默认优先级),查询发生在之后任意时刻:
     ⇒ 同一 tick 内"我先打你"不会让你把我视为敌对(缺少对称性)。
证据:A combat/PlayerHostilityTracker.java:50-69(记录);combat/HostileTargets.java:62(查询);
     对照 A combat/DiceCombatEvents.java:642
B forge-1.20.1 同构(1.20.1 对应阶段为 LivingDamageEvent)
可达性:玩家 A 先打玩家 B(单方面)。B 的名单里有 A ⇒ B 的溅射/风扇能命中 A;
     A 的名单里**没有** B ⇒ A 的同类效果打不到 B ⇒ **单方面敌对**(A 能打 B,B 不能还手打 A)。
修法:口径问题,需用户定夺:是否在"我方主动攻击"时也把对方记入我方名单(对称化),
     还是明确接受"只有被打过才算敌对"的单方面语义。
严重度:medium(需定夺)
```

```text
ID:G3
核验:证实(细节修正)
现象:hasAttacked 的**形参命名与语义相反**:声明为 `(Player victim, Player attacker)`,
     调用处传的是 `(viewerPlayer, targetPlayer)`;方法体用 `get(victim.getUUID()).contains(attacker.getUUID())`,
     即"第一个参数是**被查询的视角方/受害者**,第二个参数是**被检查的嫌疑攻击者**"。
     当前调用**功能正确**,但形参名与调用顺序相反,极易在后续改动中被"按名字"用反。
顺序依赖:无。
证据:A combat/PlayerHostilityTracker.java
:93     public static boolean hasAttacked(Player victim, Player attacker) {
:95         Set<UUID> attackers = HOSTILE_ATTACKERS.get(victim.getUUID());
:96         return attackers != null && attackers.contains(attacker.getUUID());
     调用方 A combat/HostileTargets.java:62
:62         return PlayerHostilityTracker.hasAttacked(viewerPlayer, targetPlayer);
B forge-1.20.1 同构
可达性:100%(每次两参 isHostile 都走这里)。
修法:重命名形参为 `(Player viewer /*被打的一方*/, Player candidate /*可疑攻击者*/)`,
     或把方法改名为 `hasAttackedMe(viewer, candidate)`,消除歧义。
严重度:low(命名,非当前行为缺陷)
```

---

## 4. §H 全局静态状态

### 4.1 全仓静态可变容器清单(以**基线 `d48a529`** 取证;A = neoforge-1.21.1)

`static final` 但**内容可变**者全部计入;仅注册期写入的注册表单列见文末。

| 载体 | 声明 | 键 | 清理点 | 登出清理? | 判定 |
|---|---|---|---|---|---|
| `PlayerHostilityTracker.HOSTILE_ATTACKERS` | `:49`(工作区)`Map<UUID,Set<UUID>>` | 受害者 UUID | 死亡(LOWEST)/克隆/登出 | ✅ | 低危(空集合残留,见 H1) |
| `ElectricSwordChipItem.KILL_COUNTS` | `:35` `Map<UUID,Integer>` | 玩家 UUID | `onChipUnequip:56`、满 10 重置 `:68` | ❌ | ⚠️ **登出泄漏**:佩戴中离线 ⇒ 条目常驻至服务器重启;重登后计数**延续** |
| `EnergyRecyclerChipItem.lastPosMap` / `walkAccumMap` | `:42` / `:43` | 玩家 UUID | `clearTracking`(`onChipUnequip:65`) | ❌ | ⚠️ 登出泄漏;**且不随死亡清理** |
| `JasmineSignItem.lastPosMap` / `walkAccumMap` | `:29` / `:30` | 玩家 UUID | `clearSignData:53-54`(**需玩家有意卸下**) | ❌ | ⚠️ 登出泄漏;~~叠加 E1 +~~ 死亡清单独缺 ⇒ 死亡一路确实失效(「有意卸下」一路经勘误确认**正常**);另见 H-C1(无瞬移闸门) |
| `ChargeManager.DEATH_PRESERVED_STACKS` | `:21` `Map<UUID,Integer>` | 玩家 UUID | `:77` / `:85`(取走即删)/`:94` `clearDeathPreserved` | ❌ | ✅ 低危:只活到重生瞬间。**但 `clearDeathPreserved` 与 `HealingManager.spend` 全仓零调用(死代码,见 §4.2)** |
| `DeathPreservedBonuses.PRESERVED` | `:33` `ConcurrentHashMap<UUID,int[]>` | 玩家 UUID | `:50` 取走即删 | ❌ | ✅ 低危:只活到重生瞬间 |
| `FriendshipBadgeChipItem.LAST_TRIGGER` | `:36` `Map<String,Long>` | **非 UUID 键** | `:61-62` `size() > 500` 时**整体 clear** | n/a | ⚠️ 无界上限用"全清"兜底 ⇒ 清空瞬间所有冷却失效;另见 H-C2(跨世界 gameTime 负差 ⇒ 永久失效) |
| `WaystoneWarpCompat.PENDING` | `:34` `Map<UUID,Long>` | 玩家 UUID | 待查 | ? | 未核验 |
| `RailgunStrikeScheduler.PENDING` | `:81` `List<Pending>` | — | 处理即摘队列 | n/a | ⚠️ 见 §4.2 H-C3(持旧 `ServerLevel`) |
| `MarkManager.PENDING_DECAY` | `:30` `List<PendingDecay>` | — | 处理即摘 | n/a | ✅ 低危 |
| `ClientDamageNumbers.activeNumbers` | `:9` `Map<Integer,FloatingNumber>`(**客户端**) | 实体 id | 待查 | n/a | 见 H4 / §4.2 H-C18 |

**全局可变静态标量**

```text
A component/GameplayConstants.java:23,37,39,41,43,45,47,49,51,53,55,60,62,65,67,69
  public static boolean GIVE_GUIDE_BOOK_ON_FIRST_JOIN = true;
  public static boolean EVENT_APPLY_MC_TEAM = true;
  public static int SIGN_ACTIVE_COOLDOWN_SECONDS = 180;
  ...
A event/EffectTimerGuard.java:43   private static boolean forcedRemoval = false;   ← 门闩,须 try/finally
A event/ModEffectRemoval.java:16   private static boolean internal = false;        ← 门闩,须 try/finally
A compat/WaystoneWarpCompat.java:35 private static boolean registered = false;
A combat/DiceCombatEvents.java:134  static boolean aoeProcessing = false;          ← 普通 static,非深度计数
A combat/DiceCombatEvents.java:143  private static int counterDepth = 0;
```

**仅注册期写入的注册表(无害,计数可辩护)**:`CardRegistry.BY_ID:39`、`DiceTierRegistry.TIERS:16`、
`DiceCombatModifiers.{ATTACK,DEFENSE,VICTIM_DAMAGE}_MODIFIERS`、`SpellDamageRegistry.{MATCHERS,MODIFIERS}`、
`EffectCardPeriod.{FIXED,TEMPORARY,EFFECT_PENDING}_SOURCES`、`RandomCardHandler.EXCLUSIVE_CARDS`、
`DiceCombatEvents.EXTERNAL_DAMAGE_FACTORS` —— 构造后只读,不构成 H1 缺陷。

```text
ID:H1
核验:证实
现象:本批**新增**一个全局可变静态映射 HOSTILE_ATTACKERS(受害者→攻击者集合),无上限、无 TTL,
     生命周期 = 服务器进程(不持久化)。清理只发生在三个事件:目标玩家死亡(LOWEST)、
     死亡克隆、**任意**玩家登出。
顺序依赖:死亡清理取 LOWEST,晚于安全气囊/末影骰的"取消死亡"判定 ⇒ 死亡被救回时不清理(有意)。
证据:A combat/PlayerHostilityTracker.java(新增,未跟踪;行号取 T1 工作区)
:49     private static final Map<UUID, Set<UUID>> HOSTILE_ATTACKERS = new HashMap<>();
:67-68      HOSTILE_ATTACKERS.computeIfAbsent(victimPlayer.getUUID(), k -> new HashSet<>()).add(attackerPlayer.getUUID());
:72-77  @SubscribeEvent(priority = EventPriority.LOWEST) onLivingDeath → forget(uuid)(:74 有 isCanceled 早退)
:87-91  onPlayerLoggedOut → forget(event.getEntity().getUUID())
:100-106 private static void forget(UUID uuid) { HOSTILE_ATTACKERS.remove(uuid); for (...values()) attackers.remove(uuid); }
可达性:100%(每次 PvP 命中)。
泄漏面评估:
  - **跨世界/跨维度**:UUID 为键 ⇒ 不因换维度或换世界而串扰 ✅
  - **重登**:登出即 forget ✅;**服务器重启**:不持久化,自然清空 ✅
  - **多玩家串扰**:键为受害者 UUID、值为攻击者 UUID ⇒ 无串扰 ✅
  - ⚠️ **空集合残留**:forget 只删"该 UUID 的键"与"各值集合中的该 UUID",**不删除变空的值集合**;
     若受害者 A 一直在线而其所有攻击者都已登出,`HOSTILE_ATTACKERS.get(A)` 留一个**空 Set** 常驻。
     无界增长需"A 在线期间与无限多个不同玩家 PvP 且对方全部登出"⇒ 实为小对象泄漏,非玩法缺陷。
     (并行子代理 §H 的 H-C17 亦独立命中此项,判 medium;本报告按"无玩法后果"判 low。)
  - ⚠️ **单服务端线程假设**:HashMap 非并发容器;写入点 LivingDamageEvent.Pre 与登出/克隆事件均在
     服务端主线程 ⇒ 假设成立(与既有 ElectricSwordChipItem.KILL_COUNTS 同一先例)。**未核验**是否有
     Netty 线程读取路径。
修法:低优先。可选:在 forget 末尾清理空集合;或改用 ConcurrentHashMap(防御性,非必需)。
严重度:low
```

```text
ID:H2
核验:修正(反编译源码级 + Curios 字节码级);**首轮报告的"EMPTY 被污染"与并行子代理的 H-C19/H-C20 均不成立**
现象:ItemStack.EMPTY 确实是**可变全局单例**(EMPTY.components 为 PatchedDataComponentMap,
     对其 set(...) 会直接写 patch),但**本仓从来没有把 EMPTY 写进去过**:
     ① 基线 CurioSlotUtil.isRealUnequip:87-93 以"槽位里现在还是不是同一物品"为判据;
        真正有意卸下时槽位为空/已换物 ⇒ 判 false ⇒ onChipUnequip 不执行(不写);
        重载场景槽位仍是同一实例 ⇒ 判 true ⇒ 也不写(跳过);
        仅 loseStacks / handleDrops 这类"回调发生在清槽之前"的路径会写,而那时传入的是**被卸下的真实栈,不是 EMPTY**。
        ⚠️ **勘误(2026-09-15)**:`handleDrops` **不回调**(该半句作废);更关键的是 `loseStacks` 传入的
        **正是 `ItemStack.EMPTY`**(进第 2 参,非"被卸下的真实栈")—— 故本句对 `loseStacks` 的描述是错的。
     ② 由 §1.3 字节码穷举:3 个 ICurio.onUnequip 调用点传入的都是**被卸下的真实栈**
        (或"槽位上一 tick 的栈"),**没有任何一处把 ItemStack.EMPTY 作为该实参**。
        handleDrops 的 setStackInSlot(i, EMPTY) 只是清槽动作;ItemStackHandler.setStackInSlot
        **不派发任何回调**,EMPTY 进不到 onUnequip 的参数里。
        ⚠️ **本句已被 2026-09-15 勘误推翻**:`CurioStacksHandler.loseStacks` 的 `lambda$loseStacks$4`(N)
        恰恰把**常量 `ItemStack.EMPTY`** 传给了 `ICurio.onUnequip(ctx, X)` 的 **X**(即第 2 参);
        `handleDrops` 不调用 `onUnequip` 这一半仍然成立。**故本节 ② 的"无 EMPTY 实参"论证不成立**,
        H2「EMPTY 污染不可达」这一总结论**本轮未重新判定**(取决于基线 `stillInSlot` / `isRealUnequip`
        在 `X = EMPTY` 时是否放行 —— 见 §2.1「旧实现的真实缺陷」勘误 (c) 与 §5.2 第 2 条)。
     ③ 另:DiceCurioItem.onEquip:68-69 的 curio.set(...) 被 `if (!curio.has(WEAPON_ENHANCEMENT))` 守卫;
        且 ICurio.onEquip 的实参来自 lambda$tick$32 的 newStack(槽内新内容),
        空槽时 CuriosEventHandler.tick 根本不会走到 onEquip(被 `newStack.isEmpty()` 分支挡掉)—— 故**也不会写 EMPTY**。
     => 并行子代理 §H 的 H-C19(high)/H-C20(medium)把"第 2 参"理解为"真卸下时的 EMPTY",与字节码相反;
        其 H-C21(筹码不写 EMPTY)**与本节结论一致**。首轮报告同一处描述同样需要更正。
     ⚠️ **勘误(2026-09-15)**:上句把 H-C19/H-C20 判为"与字节码相反"**依据有误** ——
     第 2 参在真实卸下与 `loseStacks` 两路**确实**是 `ItemStack.EMPTY`(顶部勘误块第 2、3 条),
     故其"第 2 参 = EMPTY"的前提是**对的**;能否达成"污染"取决于基线判据是否放行(见本节 ② 与 §5.2 第 2 条),
     **本轮未重新判定**,请勿据本文引用。
顺序依赖:无。
证据:反编译源码(A/B 共用 MC):
  build/moddev/artifacts/neoforge-21.1.235-sources.jar → net/minecraft/world/item/ItemStack.java
:304    public boolean isEmpty() { return this == EMPTY || this.item == Items.AIR || this.count <= 0; }   // this==EMPTY 短路
        public static final ItemStack EMPTY = new ItemStack((Void)null);
:715    public <T> T set(DataComponentType<? super T> component, @Nullable T value) { return this.components.set(component, value); }
  net/minecraft/core/component/PatchedDataComponentMap.java
:65-77  public <T> T set(...) { validateComponent(value); ensureMapOwnership(); ... this.patch.put(component, Optional.ofNullable(value)); ... }
  基线本仓(`git show d48a529:...`):
  A item/CurioSlotUtil.java:87-93  public static boolean isRealUnequip(SlotContext slotContext, ItemStack stack, LivingEntity entity) {
:91              .map(h -> ... && !h.getStacks().getStackInSlot(slotContext.index()).isEmpty()
:92                      && h.getStacks().getStackInSlot(slotContext.index()).getItem() == stack.getItem()).orElse(false);
  A item/chip/BaseChipItem.java:79  public void onUnequip(SlotContext slotContext, ItemStack curio, ItemStack newStack) {
:83          CurioSlotUtil.runOnRealUnequip(slotContext, curio, player, p -> onChipUnequip(p, curio));
  A item/dice/DiceCurioItem.java:67-69  onEquip(...){ if (!curio.has(WEAPON_ENHANCEMENT.get())) { curio.set(...); } }
可达性:EMPTY 污染**不可达**(基线/工作区皆然)。若未来有人把"第 2 参"当真卸下的 EMPTY 并直接 set,
     才会达成污染 —— 这是需要防守的方向,而非既有缺陷。
修法:保留工作区 `CurioSlotUtil:103 if (removedStack == null || removedStack.isEmpty()) return false;`
     这道守卫(无害且防御性);更稳的做法是在各 clearSignData 入口加 `if (stack.isEmpty()) return;`。
严重度:info(**首轮报告与 H-C19/H-C20 的触发路径均不成立,需更正**;H-C21 结论正确)
```

```text
ID:H3
核验:证实
现象:多个**按玩家 UUID 索引的静态映射没有登出清理**,只在"有意卸下饰品"或"死亡重生"时清理。
     玩家佩戴中退出服务器 ⇒ 条目保留至服务器重启。
顺序依赖:清理点全部挂在"物品事件"上,而"玩家登出"不会触发饰品卸下回调
     (Curios 登出路径是否回调 onUnequip 未核验)⇒ 清理与生命周期不匹配。
证据(基线 `d48a529`):A item/chip/ElectricSwordChipItem.java
:35     private static final Map<UUID, Integer> KILL_COUNTS = new HashMap<>();
:56         KILL_COUNTS.remove(player.getUUID());       // 仅 onChipUnequip
A item/sign/JasmineSignItem.java
:29-30  private static final Map<UUID, Vec3> lastPosMap = new HashMap<>(); / Map<UUID,Float> walkAccumMap
:53-54      lastPosMap.remove(player.getUUID()); / walkAccumMap.remove(player.getUUID());   // 仅 clearSignData
A item/chip/EnergyRecyclerChipItem.java :42-43, :127-128(仅 clearTracking)
A event/PlayerLifecycleHandler.java:179-192  onPlayerLoggedInClearDiceBlessing
     —— **只清效果/计时器,不碰上述任何静态表**
可达性:可达。最小验证:玩家戴"电击剑筹码"击杀 7 只敌对目标(计数 7)→ 退出服务器 → 重登 → 再杀 3 只
     → 若第 10 只即触发"满 10 得 2 充能"(而非重新从 10 开始),则计数**未在登出时清理**。
     Jasmine/EnergyRecycler 同理(重登后先走 1 格看 walkAccumMap 是否从 0 起算)。
修法:在"玩家登出"处统一遍历并清除上述按 UUID 的表(可加一个 PlayerEvent.PlayerLoggedOutEvent
     订阅者集中调用各 manager 的 clear);或把"累计值"改用附件(随实体生命周期,天然随玩家对象回收)。
严重度:low(单条目极小;真实风险是"重登后计数延续"这一语义,需用户定夺期望)
```

```text
ID:H4
核验:未核验(客户端侧)
现象:客户端静态缓存 ClientDamageNumbers.activeNumbers(`:9`)与 1.20.1 的 ClientAstralData 会话清理,
     本轮**未逐行核验**;首轮 §H2 的假设("服务端只下发存在的键 ⇒ 显示上一局旧值")需在此复核。
     **并行子代理 §H 的 H-C18 给出了相反的修正**:forge 侧已被"登出 clear + 全量默认值快照"双层覆盖;
     1.21.1 无此静态缓存(NeoForge 原生逐实体并同步删除)。两说冲突,以子代理的反编译取证为准,
     但本代理**未独立复核**,故保留为未核验。
证据:A client/ClientDamageNumbers.java:9;B 侧 component/ClientAstralData.java、client/ClientSessionEvents.java
     (后二者**仅 forge-1.20.1 存在**,属单侧差异)
可达性:未核验。
修法:待复核后补。
严重度:未核验
```

### 4.2 并行子代理 §H 的补充与勘误

子代理报告 `docs/scan2/_work/H-static-state.md`(H-C1…H-C21,证据全部取基线 `d48a529`)。**采纳**其下列发现(本代理未逐条复核,标注为子代理来源):

| 子代理 ID | 内容 | 严重度 | 本代理复核 |
|---|---|---|---|
| **H-C1** | `JasmineSignItem` 移动累计表**缺瞬移闸门**(同仓 `EnergyRecyclerChipItem:113-115` 有 `if (dist > MAX_MOVE_PER_TICK) return;`)⇒ 维度切换/`/tp`/末影珍珠/重登可瞬间拉满 +20 攻 +20 防 | **medium** | 未独立复核;**与 H3 同源**(同两张表),建议合并处理 |
| **H-C2** | `FriendshipBadgeChipItem.LAST_TRIGGER` 时间戳跨世界 `gameTime` 负差 ⇒ 徽章触发永久失效 | medium | 未独立复核 |
| **H-C3** | `RailgunStrikeScheduler.PENDING:81,134-140` 持旧 `ServerLevel` 且超龄判定基于其冻结 `gameTime` ⇒ 世界不可回收 | medium | 未独立复核 |
| **H-C4~C7** | 各 UUID/死亡暂存表跨世界残值;`ChargeManager.clearDeathPreserved` 全仓**无调用者**(死代码) | low–info | 死代码一项与 §4.1 一致 |
| **H-C8** | `EmeraldDiceTrade` ThreadLocal + HEAD/RETURN 非 `finally` | medium | 未独立复核 |
| **H-C9~C16** | 单线程门闩 / ThreadLocal / 配置常量 / 只写一次注册表 | info | 与 §4.1 结论一致 |
| **H-C17** | **工作区新增** `PlayerHostilityTracker` 缺世界切换清理 | medium | 与 H1 同一对象,见 H1 的"空集合残留" |
| **H-C18** | forge 侧客户端缓存已被双层覆盖;1.21.1 结构性不适用 | 修正 | 与 H4 冲突,保留未核验 |

**勘误(本代理以字节码 + 基线复算反驳子代理两处)**

1. ~~**H-C19(high)/H-C20(medium)不成立**。二者称"`BaseSignItem.onUnequip` / `DiceCurioItem.onEquip` 把第 2 参
   (真卸下时 = `ItemStack.EMPTY`)当被卸下者 ⇒ 写 EMPTY 单例"。但 §1.3 已证 3 个 `ICurio.onUnequip` 调用点
   传入的**都是被卸下的真实栈**,`ItemStackHandler.setStackInSlot` 不派发回调 ⇒ **EMPTY 从未作为实参出现**;
   `onEquip` 的实参是 `newStack`(槽内新内容),空槽时不调用。详见 H2。~~
   → 🔴 **勘误(2026-09-15):本条反驳的依据已被推翻**。字节码复取显示"第 2 参 = 真卸下时的 `ItemStack.EMPTY`"
   **是对的**(tick 路径第 2 参 = 当前槽位栈 = 真实卸下时为空;`loseStacks` 第 2 参 = 常量 `ItemStack.EMPTY`)。
   故 H-C19/H-C20 的**前提成立**;是否真会写进 EMPTY 单例,取决于基线判据在 `X = EMPTY` 时是否放行
   (**本轮未重新判定** —— 见 H2 ② 与 §5.2 第 2 条)。本条不再作为"反驳"引用。
2. **子代理的行号均取基线**,与 §E/§G 的工作区行号体系不同;引用时需注意。子代理对工作区漂移的记载
   ("32 已改 + 4 新增")与本报告 §0 一致。

**单侧差异(子代理提供)**:`component/ClientAstralData.java`、`client/ClientSessionEvents.java`、
`ModNetwork.syncSnapshot` / `AttachedDataKey.defaultRawTag` / `ModCapabilities` 三快照钩子、
`component/ItemDataKey.java` —— **仅 forge-1.20.1 存在**;§H 其余 20 项两版本一一对应。

---

## 5. §G3/G4 联动回路与去重

### 5.0 结论总表(12 条;来自 `_work/G34-linkage.md`,已摘要)

| ID | 一句话 | 核验 | 严重度 |
|---|---|---|---|
| G-C1 | 同一 tick 两次**真实**「调查阶段」事件:大侦探 +3 星币计 2 次,调查员活体书页只发 1 张(2 tick 窗口吞掉第 2 次) | 证实 | **high** |
| G-C2 | 大侦探 +3 星币路径**完全无去重/无守卫**(首轮结论成立) | 证实 | medium |
| G-C3 | `"fanny_active"` 一个 id 覆盖大侦探 11 项不同事件;`"investigation"` 不区分同类多次真实事件 ⇒ 键不唯一 | 证实 | low |
| G-C4 | `applyRinSignPassive(Player)` 单参重载 + `"sign_effect"` 是**死代码** | 证实 | info |
| G-C5 | 去重注释声称的动机「多立牌槽导致 onKill 多次调用」**已不存在** | 证实 | info |
| G-C6 | 占星师:带虚弱印记的**玩家**死亡被末影骰/气囊取消后,印记释放者仍拿 3 星币、击杀者仍拿命运的指引 | 证实 | **high** |
| G-C7 | 秘密侦探:带隐匿调查的**玩家**死亡被取消后,仍推进调查阶段 + 向全部在线调查员发活体书页,且隐匿调查**永不消失**(`Integer.MAX_VALUE`)⇒ 无界刷取 | 证实 | **blocker** |
| G-C8 | 首轮列的 CursedSword / Satellite / Bonnie 缺 `isCanceled()` **属实但本模组内不可达**(奖励前置要求"非玩家敌对生物",而只有玩家死亡会被取消) | **修正** | low |
| G-C9 | 基线 `LivingDeathEvent` 订阅者 **10** 个;当前工作区因新增 `PlayerHostilityTracker` 变 **11**;同优先级无顺序契约 | 证实 | info |
| G-C10 | 全部命名联动链路逐一核过:**唯一成环**的是「电流核心→冷却→立牌主动→电流核心」,由充能账目终止;其余无回边 | 证实 | info |
| G-C11 | `aoeProcessing` 是**普通 static boolean**(非 ThreadLocal、非深度计数),仅靠 try/finally 复位;当前无嵌套可达 | 修正 | low |
| G-C12 | `EffectCardPeriod.grantBonusPlay` 的"每轮一次"守卫**正确**,不存在 +1 双发 | **证伪**(无缺陷) | info |

```text
ID:G-C1
核验:证实
现象:玩家同时具备大侦探(fanny)与(另一名)调查员(rin)配置时,**同一 tick 内两个不同目标各自触发的
     两次「调查阶段」事件**:大侦探获得 +3×2 = **6 星币**,而所有调查员佩戴者**只收到 1 张活体书页**。
     反向情形同样成立:若两次都是活体书页而星币路径不发,则表现为"少发 1 张牌"。
顺序依赖:必须先有 applySignBuffs(无守卫、先执行)再有去重(applyRinSignPassive 内有窗口);
     onEventTriggered 内顺序固定为 24→25 行。契约:两条奖励路径**不共享**去重状态 ⇒ 不是"契约反转",
     而是**契约缺失**。
证据:A(未改动)event/AstralEventSystem.java
:22-26   public static void onEventTriggered(Player triggerer, String eventId) {
:24          applySignBuffs(triggerer);
:25          applyRinSignPassive(triggerer, eventId);
:71-72       if (signature.equals(...getRinGiftSignature(sp)) && now - ...getRinGiftTick(sp) <= 2) { continue; }
B(未改动)同文件 22-26 / 71-76(逐字相同,仅 holdsSign 走 CuriosCompat)
两次事件入口:A item/InvestigationEventUtil.java:49,56(B :47,54)
可达性:可达。最小复现:① A 装末影骰子(或气囊 + 6 充能)、B 装 bonnie;② B 对两只 ≥20 血敌对生物
     各施加一次"隐匿调查";③ A 用大当家「战斗爽·溅射」一发同时击杀两只 → 同 tick 两次
     LivingDeathEvent → 两次 triggerByKill。观察:B 星币 +6;场上任一 rin 佩戴者只 +1 张活体书页。
     **对照**:把两次击杀分到两个 tick → 活体书页 +2。该对照即"吞牌"判据。
修法:给两条奖励路径统一同一去重键;或把窗口从 <= 2 改为"同 tick 且同一次事件实例"。
     **需用户先定夺**:同一 tick 的两次独立击杀算 2 次事件还是 1 次事件。建议前者 + 事件实例 id。
严重度:high
```

```text
ID:G-C6
核验:证实
现象:佩戴末影骰子(或气囊 +6 充能)的玩家被"击杀"时,死亡事件被取消、玩家不死,但**虚弱印记的击杀奖励照发**:
     印记释放者 +3 星币、击杀者获一张「命运的指引」。死亡未成立 ⇒ 印记(5:00)可复用 ⇒ 每次"差点死掉"
     都能再领一份。
顺序依赖:取消方(EnderDiceHandler,NORMAL)与发奖方(HaiqingSignItem.onWeakMarkKill,NORMAL)同优先级
     ⇒ 顺序无契约;但**任何顺序都会发奖** ⇒ 不是竞态,而是缺守卫的确定性缺陷。
证据:A(改动中)item/sign/HaiqingSignItem.java:127-137
:127     public static void onWeakMarkKill(LivingDeathEvent event) {
:130         if (!target.hasEffect(ModEffects.WEAK_MARK)) return;
:136             HaiqingSignItem.grantWeakMarkKillReward(applier, killer);      // ← 无 isCanceled() 守卫
B(forge-1.20.1)同文件 :126-137 逐字等价
取消方:A event/EnderDiceHandler.java:146-159 / A event/ChipDamageHandler.java:75-85(HIGHEST)
契约面:A event/PlayerLifecycleHandler.java:121 已明文"取消死亡 = 不是死亡,不做清理",占星师未遵守同一契约
可达性:可达(两版本)。最小复现见 §6。
修法:onWeakMarkKill 第 128 行后加 `if (event.isCanceled()) return;`。**无需用户定夺**(与既有 A4 契约一致)。
严重度:high
```

```text
ID:G-C7
核验:证实(本批最严重)
现象:带"隐匿调查"的**玩家**死亡被取消后,InvestigationEventUtil 仍**推进调查阶段**(min(stage+1,4))
     并调用事件系统 ⇒ 触发者 +3 星币(若佩戴大侦探)、**向服务器上所有佩戴调查员立牌的玩家各发一张活体书页**。
     而"隐匿调查"以 `Integer.MAX_VALUE` 时长施加 ⇒ 死亡被取消不会移除它 ⇒ **同一目标可无限次复用**,
     每次只受末影骰 5:00 / 气囊 1:00 冷却限制。叠加 G-C2 后是一条可重复的奖励生产线。
顺序依赖:关键不对称 —— PlayerLifecycleHandler(:121)与 PlayerHostilityTracker(:74)都检查 isCanceled(),
     而**状态推进与发奖方不检查** ⇒ 契约被单方面违反。
证据:A(未改动)item/InvestigationEventUtil.java
:108     public static void onUndercoverInvestigationKill(LivingDeathEvent event) {
:111         if (!target.hasEffect(ModEffects.UNDERCOVER_INVESTIGATION)) return;
:118         InvestigationEventUtil.triggerByKill(killer, applier, markLevel);   // ← 无 isCanceled() 守卫
:48,55       ModAttachments.setInvestigationStage(applier, Math.min(stage + 1, 4));
A(改动中)combat/DiceCombatEvents.java:248-249
:248         target.addEffect(new MobEffectInstance(ModEffects.UNDERCOVER_INVESTIGATION,
:249                 Integer.MAX_VALUE, 0, false, true));
B(forge-1.20.1)item/InvestigationEventUtil.java:103-116(逐字等价);DiceCombatEvents.java:243-245
可达性:可达(两版本)。最小复现:① B 装 bonnie,待命期内近战攻击玩家 A(A 与 B 不同队)⇒ A 获永久隐匿调查;
     ② A 装末影骰子;③ B 击杀 A ⇒ A 因图腾存活;④ 观察:B 调查阶段 I→II,且**每一名**在线 rin 佩戴者
     各多一张活体书页;⑤ 等 5:00 重复 ③ ⇒ 阶段封顶 IV 但**每次仍发牌与星币** ⇒ 无界重复即证实。
修法:onUndercoverInvestigationKill 首行加 `if (event.isCanceled()) return;`,并把"事件发奖"统一挂到
     "死亡成立"的守卫之后。**无需用户定夺**(A4 既有契约的执行遗漏)。
严重度:blocker
```

```text
ID:G-C2 / G-C3(去重键与星币路径)
核验:证实
现象:G-C2(大侦探 +3 星币)**无任何去重/窗口/冷却**;G-C3 去重键 = `triggerer.getUUID() + "|" + eventId`,
     而 eventId 不唯一标识真实事件。
证据:A(未改动)event/AstralEventSystem.java
:36-38       if (holdsSign(player, ModItems.FANNY_SIGN.get())) { giveStarCoins(player, 3); }   // 无守卫
:62          String signature = triggerer.getUUID() + "|" + eventId;
A(未改动)item/sign/FannySignItem.java:43-48   roll = nextInt(1,12); ... onEventTriggered(player, "fanny_active");
B(未改动)同(行号 -1)
可达性:G-C2 100% 可达;G-C3①`"fanny_active"` 覆盖 11 项事件**当前不可达**(立牌槽 size=1 + 玩家级冷却 ⇒
     同 2 tick 内不可能出现两个 fanny_active);G-C3②`"investigation"` **可达**(= G-C1 的双击杀)。
修法:见 G-C1;G-C3 建议事件 id 带上"类型 + 本次调用序号/实体 id"。**需用户先定夺**(11 项算不算 11 个事件)。
严重度:medium(星币) / low(键不唯一)
```

```text
ID:G-C4 / G-C5 / G-C8 / G-C9 / G-C10 / G-C11 / G-C12(低危与修正项)
核验:G-C4 证实(死代码);G-C5 证实(注释脱节);G-C8 **修正**;G-C9/G-C10 证实;G-C11 修正;G-C12 **证伪**
- G-C4:A/B event/AstralEventSystem.java:46-48 `applyRinSignPassive(Player)` 单参重载传 `"sign_effect"`,
  **全仓无调用者** ⇒ 死代码。info
- G-C5:同文件 :50-56 的 javadoc 声称去重动机是"多立牌槽导致 onKill 多次调用";但 invokeKillHooks 仅 1 个
  调用点(BonnieSignItem),rin 发牌早已不在 onKill 路径;立牌槽两版本均为 **size 1** ⇒ 该前提不存在。
  窗口如今只起负作用(吞 G-C1 的合法奖励)。info
- **G-C8(修正首轮)**:首轮把 CursedSwordChipItem:119 / SatelliteChipItem:99 / BonnieSignItem:138 缺
  isCanceled() 与占星师/调查员并列为 5 处;本轮证实这 3 处的奖励前置要求 HostileTargets.isHostile(target)
  且"非玩家",而 isHostile(Entity) 对玩家**恒为 false**;本模组**唯一**的 LivingDeathEvent 取消方只对玩家
  生效(ChipDamageHandler:79 / EnderDiceHandler:150 均有 `if (!(entity instanceof Player player)) return;`)
  ⇒ **本模组内不可达**。真正可造成实际后果的只有 **Haiqing(G-C6)与 Investigation(G-C7)两处**。
  修法:顺手补守卫(防第三方模组取消怪物死亡),但按纪律只判 low。low
- G-C9:LivingDeathEvent 订阅者基线 **10** 个 → 当前工作区 **11** 个(新增 PlayerHostilityTracker:64);
  同优先级顺序 = jar 内 class 条目顺序,**不是契约**。info
- G-C10:全部命名联动链路逐一核过,**唯一成环**的是电流核心链路
  (`CurrentCoreChipItem.tryFinishCooldown → BaseSignItem.performSkill → handleUse → onActiveSkillUsed`),
  终止机制 = 充能账目(消耗≥1 / 获得 1)+ 每次闭合需玩家按键。其余链路无回边
  (雷击走 `ModDamageTypes.trueDamage(Level)` **无来源实体** ⇒ 全部击杀钩子前置失败)。
  另:counterDepth(深度计数)与 DamageEffectCardHandler.APPLYING_TRUE_BONUS(ThreadLocal)均有 try/finally ⇒ 异常安全。
- G-C11(修正):aoeProcessing 是**普通 static boolean**,两处 AOE 都在 try/finally 内置真/置假;嵌套时
  内层 finally 会提前清闸。但**当前不可达**(AOE 伤害走 trueDamage(level, attacker),getDirectEntity() 为 null,
  且 astral_dice:true_damage 不在法伤 matcher 白名单)⇒ 潜在脆弱点,非现实缺陷。low
- G-C12(**证伪**):EffectCardPeriod.grantBonusPlay 的 `if (getBonusPlays(player) > 0) return false;` 守卫正确,
  不存在 +1 双发。info
```

**去重键清单(必答项)**

| 触发入口 | 文件:行号 | eventId 实参 | 窗口/守卫 | 唯一性判定 |
|---|---|---|---|---|
| 大侦探主动(11 项随机事件全部) | A `item/sign/FannySignItem.java:48` / B `:47` | `"fanny_active"` | **无任何窗口/守卫** | ✗ 11 个语义不同事件共用一个 id;且星币发放本身无去重 |
| 调查阶段事件(击杀隐匿调查目标) | A `item/InvestigationEventUtil.java:49,56` / B `:47,54` | `"investigation"` | **2 tick(gameTime)** 窗口,判定 A `event/AstralEventSystem.java:71-72` | △ 类级唯一、**实例级不唯一**:同 tick 两次真实击杀 ⇒ 第二次被吞 |
| 兼容重载(无调用者) | A/B `event/AstralEventSystem.java:46-48` | `"sign_effect"` | 同上窗口 | — 死代码 |
| 星币发放(大侦探) | A/B `event/AstralEventSystem.java:36-37`(`giveStarCoins` `:90-92`) | 无 | **无** | ✗ 调用 N 次即发 N 次 |
| 活体书页发放(调查员) | A/B `event/AstralEventSystem.java:57-83` | 继承调用方 id | **每收件人各存一份**签名 + 时刻 | ✓ 收件人粒度正确 |
| 占星师击杀奖励(虚弱印记) | A `item/sign/HaiqingSignItem.java:127-137` / B `:126-137` | 无 | **无去重** | ✗ 死亡被取消时不消耗印记 ⇒ 可反复发奖(G-C6) |
| 调查阶段推进 | A `item/InvestigationEventUtil.java:48,55` / B `:46,53` | 无 | `Math.min(stage+1,4)` 封顶 | ✗ stage 封顶 ≠ 奖励封顶(G-C7) |
| 出牌数 +1(立牌主动) | A/B `item/card/EffectCardPeriod.java:171-175` | 无 | `effect_card_bonus_plays > 0` 即拒绝 | ✓ 每轮一次,正确 |

**窗口性质**:大小 = **2**;基准 = `ServerLevel#getGameTime()`(A/B `AstralEventSystem.java:59`),即
**世界 gameTime 窗口**(不是 `player.tickCount`、不是自增计数器);判定式 `now - 存值 <= 2`(**含 2** ⇒
实际覆盖 [t, t+2] 共 3 个 tick 边界)。两侧附件默认值 `""` / `0L` 使"从未发过牌"的玩家不会被误判。

**回路清单**

| 链路 | 是否成环 | 终止机制 | 文件:行号 |
|---|---|---|---|
| fanny → 事件系统 → rin 发牌 | 否(无回边) | `giveCard` 只走 `HealingManager.add` | A `item/sign/FannySignItem.java:48`;`event/AstralEventSystem.java:22-26,78-80` |
| bonnie → 调查阶段 → rin 发牌 | 否(无回边) | 阶段单调封顶 | A `item/InvestigationEventUtil.java:41-58,108-119` |
| haiqing 印记 → 击杀奖励 → 命运的指引 | 否(无回边) | 奖励只产币/牌 | A `item/sign/HaiqingSignItem.java:103-138` |
| railgun → 雷击 → 真伤 → 击杀钩子 | 否(源无实体 ⇒ 钩子全部前置失败) | 1:00 冷却 + 6 层充能 | A `item/chip/RailgunChipItem.java:80-83,99-111`;`damage/ModDamageTypes.java:39-41` |
| **current core → 立牌冷却 → 立牌主动 → current core** | **是** | 充能账目(消耗≥1 / 获得 1)+ 每次闭合需按键 | A `item/chip/CurrentCoreChipItem.java:55-59,78-95`;`item/sign/BaseSignItem.java:87-96,113-122` |
| 骰战 → 大当家溅射(嵌套 hurt) → 骰战 | 否(结构截断) | `aoeProcessing` + 真伤源 `getDirectEntity()==null` | A `combat/DiceCombatEvents.java:134,200,635-686` |
| 反击 → 闪避 → 反击 | 否(结构截断) | `counterDepth` 深度计数 + try/finally | A `combat/DiceCombatEvents.java:143,1146-1155` |
| 法伤加成 → 真伤 hurt → 法伤加成 | 否 | `ThreadLocal<Boolean> APPLYING_TRUE_BONUS` + matcher 不匹配 `true_damage` | A `event/DamageEffectCardHandler.java:29,62-69` |

### 5.1 单侧差异汇总(A 有 / B 无,或行号/实现不同)

| 项 | A(neoforge-1.21.1) | B(forge-1.20.1) | 差异性质 |
|---|---|---|---|
| 敌对玩家记录事件 | `LivingDamageEvent.Pre` | `LivingDamageEvent`(1.20.1 对应阶段) | **API 差异,语义等价**(见首轮 A1 阶段映射) |
| `PlayerHostilityTracker` | 新增 98 行 | 新增 98 行 | 逐行同构 |
| `NetherStarDiceItem.onUnequip` 形参名 | `(curio, prevStack)` | `(curio, prevStack)` | **两侧均未随本批改名** ⇒ 无单侧差异,但两侧都命名误导 |
| `CurioSlotUtil.isIntentionalUnequip` | 新增(工作区 `:100-108`) | 新增(行号 ±1) | 同构 |
| 死亡清理清单(立牌组件) | `PlayerLifecycleHandler:147-154` 只清 `MISAKI_SIGN_STACKS` | 同 | **两侧同缺 Jasmine/Padman**(共同缺口,非单侧) |
| §G3/G4 涉及路径 | — | — | **无语义差异**(`CuriosApi` vs `CuriosCompat`、`ModEffects.get()`、`TickEvent.Phase.END` vs `ServerTickEvent.Post`、`igniteForSeconds` vs `setSecondsOnFire`) |
| §H 客户端缓存 | 无 | `ClientAstralData` / `ClientSessionEvents` / `ItemDataKey` / 三快照钩子 | **仅 forge 侧存在**(1.21.1 走 NeoForge 原生附件,结构性不适用) |

⇒ **本轮 §E/§G 未发现新的单侧功能差异**;唯一"单侧"性质的是加载器事件类名与 §H 客户端缓存基础设施。

### 5.2 未核验(需实机或额外反编译)

1. **`RailgunBolts.java:58` 的调用方是否持有攻击者上下文**(§3.3 第 9 号待改点,未确认外层能否拿到 `player`)。
2. **`CurioSlot.set()` 传入的栈是否为副本**:这决定**基线** `stillInSlot` 的引用比较在"玩家从 GUI 卸下"时的真假,
   进而决定基线是否把组件归零写到了副本上。本轮已从字节码确认 `onUnequip` 的实参来源,但**未反编译
   `SlotItemHandler.set` 的写槽实现**。
3. **`handleDrops` 的 `onUnequip` 与 `PlayerLifecycleHandler.onPlayerDeathClearEffects` 的先后**:两者都在
   `LivingDeathEvent` 链上,`handleDrops` 走 Curios 自己的 `LivingDropsEvent`;`DEFAULT` 掉落规则下精确顺序未实测。
4. **`keepInventory=true` 死亡**:测试世界默认 `keepInventory=true`(见 AGENTS.md),此时 Curios 不掉落 ⇒
   **⑤ 路径可能不可达**,`onUnequip` 不触发 ⇒ Jasmine/Padman 的组件在死亡后**保留**。需实机确认。
5. **`NancyLuSignItem:214`** 的语义归属(赐福结束时"6 格内无敌对生物"是否应把敌对玩家计入)需用户定夺。
6. **`WaystoneWarpCompat.PENDING`**(A `:34` `Map<UUID,Long>`)的键构成与清理点:仅见声明与
   `FriendshipBadge` 的 `size()>500` 全清兜底式写法,**未逐行核对**其登出/过期语义。
7. **Curios 在"玩家登出"时是否回调 `onUnequip`**:这决定 H3 的泄漏严重度。已核验的 3 个 `ICurio.onUnequip`
   调用点中,`CPacketDestroy` 是客户端包("销毁饰品"动作),**不必然是登出路径**。
8. **客户端静态缓存会话清理(H4)** 与子代理 H-C18 的相反结论需复核。
9. **同一实体同一 tick 是否可能两次 `LivingDeathEvent`**(子代理提出):影响 G-C6/G-C7 的"每次冷却能领几份"定量。
10. **第三方模组取消敌对生物死亡**时 G-C8 三处的实际后果(本仓无法复现)。

---

## 6. 最小实机验证步骤(按可区分性排序)

| 目标结论 | 最小步骤 | 观察点 |
|---|---|---|
| ~~**E1(判据反转)**~~ **勘误后改测** | 单机创造,给玩家一枚**护法立牌**;先用主动技能叠 1~2 层剑气(看 tooltip 层数)→ 打开饰品栏**手动取下** → 重新戴上 | ~~层数**仍保留**(未归零)⇒ 证实 E1~~ → 按勘误:E1 已作废,**预期层数归零**(清理会执行);若 tooltip 仍显示旧层数,则命中的是 §5.2 第 2 条的**快照副本**缺口,而非 E1 |
| ~~**E1(筹码侧)**~~ **勘误后改测** | 佩戴**八面骰筹码**,战斗几次使累计点 >0 → 手动取下 → 重戴 | ~~累计点未清 ⇒ 证实~~ → 按勘误:**预期累计点清零**(16 处 `onChipUnequip` 全为玩家级清理) |
| **E1(定向爆破漏判)** | 双人:B 装**定向爆破**;A(非同队)先近战打中 B 一次 → B 对 A 使用定向爆破 | A 周围 6 格内的**其它玩家**是否受 5 点波及 ⇒ 否即证实 `SpellDamageRegistry:249` 漏判 |
| **G1(风扇标记)** | B 装手持风扇-大 + 任意立牌;A 先打 B 一次 → B 触发立牌主动 | A 是否被施加标记 ⇒ 否即证实 `FanBigChipItem:46` 漏判 |
| **G1(首击不对称)** | A 打 B 一次;B 不还手,仅触发溅射;随后反向操作 | B 的溅射命中 A,而 A 的溅射打不到 B ⇒ 证实 G2 单方面敌对 |
| **G-C6(占星师)** | B 装占星师,待命后攻击 A(A 与 B 不同队)施加虚弱印记 → 把 A 打到 0 血(A 装末影骰/气囊) | A 活着,B 背包 +3 星币且获「命运的指引」⇒ 证实 |
| **G-C7(秘密侦探)** | B 装 bonnie,待命后攻击玩家 A 施加隐匿调查 → A 装末影骰 → B 击杀 A | A 存活且 B 调查阶段前进、在线 rin 佩戴者各得 1 张活体书页;等 5:00 重复仍发 ⇒ 无界重复即证实 |
| **G-C1(吞牌)** | 同 tick 双杀两只带隐匿调查的怪 | 星币 +6 而活体书页只 +1;把双杀拆到两 tick 则书页 +2 |
| **H3(静态表登出)** | 戴"电击剑筹码"击杀 7 只 → 退出服务器 → 重登 → 再杀 3 只 | 第 10 只即触发"满 10 得 2 充能"⇒ 计数未在登出清理 |
| **H-C1(Jasmine 瞬移)** | 戴扫地机立牌,`/tp` 瞬移 6000 格(或进出下界门) | tooltip 攻/防加成一次跳满 20/20 ⇒ 证实缺瞬移闸门 |

---

## 7. 结论摘要(给上级代理)

1. ~~**E1 是本节最高危项(blocker)**:S6-C1 修法对 Curios 第 2 参的语义判断**与字节码相反**,导致①②④⑤四条
   路径的立牌/筹码清理**全部失效**;唯一想保护的 ③(同栈重载)**本来就不可达**(`ItemStack.matches` 判等后
   不触发回调)。筹码侧 16 个 `onChipUnequip` 全不读该栈 ⇒"改传第 3 参"无差异,但判据反转使 16 项清理全失效。~~
   → 🔴 **勘误(2026-09-15):E1 作废,不再是高危项**。第 2 参 = 调用方传入的 X、第 3 参 = `getStack()` = 被卸下者;
   `isIntentionalUnequip` 在 ①②④ 上返回 true,**清理与筹码侧 16 项注销均正常执行**(详见文件顶部勘误块)。
   ③(同栈重载)不可达这一条**仍然成立**。E1 现为 **info(误判)**。
2. **E2**:筹码槽收缩的"降星挤出"格**不可达**(`forceRemove=false` 时被移除槽有物品即整体放弃收缩);
   真实可达的挤出只在玻璃骰死亡/卸骰子(该路径经勘误确认**清理会执行**,原「被 E1 跳过」作废)。
3. **属性/组件注销对称性**:骰子侧 ✅;立牌侧 ✅(~~受 E1 影响~~ 勘误后:注销**会执行**),
   但**死亡清单独缺 Jasmine/Padman**(只清 `MISAKI_SIGN_STACKS`)⇒ 死亡后这两件立牌的累计值残留;
   另:`handleDrops` **不调用 `onUnequip`**,死亡一路本就不经该回调(勘误)。
4. **G1(high)**:两参 `isHostile` 只升级了 5 处,**另有 12 处上下文可得却仍用单参**,同一 tick 内不同子系统
   对同一对玩家给出相反敌对结论;`NancyLu:255/268` 的 `instanceof Player` 过宽、`Pandaman:105` 显式排除
   玩家与新裁决冲突(均需定夺)。
5. **G-C7(blocker)**:`InvestigationEventUtil.onUndercoverInvestigationKill` 缺 `isCanceled()`;隐匿调查为
   `Integer.MAX_VALUE` 永久 ⇒ 玩家"被击杀但没死"仍推进调查阶段 + 向**所有在线 rin 佩戴者**发活体书页,可无限重复。
6. **G-C6(high)**:`HaiqingSignItem.onWeakMarkKill` 同款缺守卫 ⇒ 未死的玩家仍发 3 星币 + 命运的指引。
7. **G-C1(high)**:同一 tick 两次真实「调查阶段」事件 ⇒ 星币计 2 次(无守卫)而活体书页只发 1 张(2 tick 窗口吞掉)。
8. **G-C8(修正首轮)**:CursedSword/Satellite/Bonnie 缺 `isCanceled()` **本模组内不可达**
   (奖励前置要求非玩家敌对生物,而只有玩家死亡会被取消)⇒ 真正可造成后果的只有 G-C6/G-C7 两处。
9. **G-C10/G-C12(负结论,避免误改)**:全部联动链路中**唯一成环**的是电流核心↔立牌主动,由充能账目终止;
   `grantBonusPlay` 的"每轮一次"守卫**正确**。`G3` 命名:`hasAttacked(victim, attacker)` 形参名与调用顺序相反。
10. **H1(§H)**:新增静态表 `HOSTILE_ATTACKERS` 无跨世界/重登泄漏,仅有空集合残留(低危)。
11. **H2(§H,勘误)**:`ItemStack.EMPTY` 全局写入**不可达**(基线/工作区皆然)。
    **首轮报告"EMPTY 被污染"的触发路径不成立**;并行子代理 §H 的 **H-C19(high)/H-C20(medium) 亦不成立**
    (已用字节码反驳,见 H2);其 H-C21 结论正确。
    > 🔴 **勘误(2026-09-15)**:本条第 2、3 句的反驳依据已被推翻 —— 第 2 参在真实卸下与 `loseStacks` 两路**确实是 `EMPTY`**
    > (顶部勘误块第 2、3 条),故 **H-C19/H-C20 的前提成立**;其结论是否成立取决于基线判据在 `X = EMPTY` 时是否放行,
    > **本轮未重新判定**(见 H2 ② 与 §5.2 第 2 条)。第 1 句「EMPTY 全局写入不可达」保持**未复核**,请勿据本条引用。
12. **H3(§H)**:`KILL_COUNTS`(电击剑)、`JasmineSignItem.lastPosMap/walkAccumMap`、
    `EnergyRecyclerChipItem.lastPosMap/walkAccumMap` 等**按玩家 UUID 的静态表没有登出清理**
    (`PlayerLifecycleHandler` 的登出处理器只清效果/计时器);重登后计数**延续**。低危但语义需定夺。
13. **H-C1(子代理,medium,建议合并进 H3)**:`JasmineSignItem` 移动累计**缺瞬移闸门**(同仓 `EnergyRecycler`
    有 `if (dist > MAX_MOVE_PER_TICK) return;`)⇒ 换维度/`/tp`/末影珍珠可瞬间拉满 +20 攻 +20 防。

### 需用户定夺(汇总)

| # | 问题 | 出处 |
|---|---|---|
| 1 | 同一 tick 的两次独立击杀算"2 次事件"还是"1 次事件"?(建议 2 次 + 引入事件实例 id) | G-C1/G-C2 |
| 2 | 是否允许"死亡被取消后仍发击杀奖励"?(当前 G-C6/G-C7 就是"允许"的后果) | G-C6/G-C7 |
| 3 | `NancyLuSignItem:255/268` 的 `\|\| victim instanceof Player` 是否收敛为两参 `isHostile`? | G1 |
| 4 | `PandamanSignItem:105` 显式排除玩家是否解除(与"玩家也算敌对"新裁决冲突)? | G1 |
| 5 | "只有被打过才算敌对"的单方面语义是否接受(G2)? | G2 |
| 6 | 立牌累计值(电击剑计数/Jasmine 移动累计)是否应改为"登出即清"还是"随玩家实体回收"? | H3/H-C1 |
