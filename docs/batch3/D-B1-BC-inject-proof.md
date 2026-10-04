# D-B1 — 方案 B（DynamicStackHandler#extractItem）与方案 C（CurioSlot#set）取证报告

- 仓库:`F:\MCProject\astral_dice_multiloader`,分支 `multi-1.20.1-1.21.1`,HEAD `027e1e990ebfcaf8648553e6fd649381d7c7adc2`
- 取证手段:Curios 缓存 jar 的 `javap -c -p`(两名:同名类在两版 jar 内**同构**)、MC/加载器反编译源码 jar 的 `Sources` 阅读(带行号)、本仓已构建产物 jar 的注解常量池核对。
- 证据来源 jar:
  - `N`(1.21.1 NeoForge)= `C:\Users\xmace\.gradle\caches\modules-2\files-2.1\maven.modrinth\curios\yohfFbgD\418fcd42e3a7844c9bdc71c9b6401fdb3894e0c4\curios-yohfFbgD.jar`(Curios 5.x,NeoForge 变体)
  - `F`(1.20.1 Forge)= `C:\Users\xmace\.gradle\caches\modules-2\files-2.1\maven.modrinth\curios\IPQlZkz1\452175b95ad3db6ff58bb8968f6bf7a9d1e0f480\curios-IPQlZkz1.jar`(Curios 5.14.1+1.20.1,Modrinth 版本 id `IPQlZkz1`)
  - MC 源码:`neoforge-1.21.1\build\moddev\artifacts\neoforge-21.1.235-sources.jar`、`forge-1.20.1\build\moddev\artifacts\forge-1.20.1-47.4.10-sources.jar`
  - 本仓产物:`forge-1.20.1\build\libs\astral_dice-1.2.1+forge_1.20.1.jar`(2026-09-15 17:38)
- **重要前置事实（本报告多处依赖）**:`N` 侧 Curios 生产 jar 已被 **Mojmap** 命名（NeoForge 1.20.2+ 运行时即 Mojmap）,`F` 侧 Curios 生产 jar 被 **SRG** 命名。故同名方法在两版 jar 中显示名不同:
  - `CurioSlot#set` → N `set`,F **`m_5852_`**
  - `Slot#getItem` → N `getItem`,F **`m_7993_`**
  - `ItemStack.isEmpty` → N `isEmpty`,F **`m_41619_`**
  - 下文凡"同名同构"处会同时给出两版显示名。

---

## 结论摘要

**B 可行且优于 C**:`DynamicStackHandler#extractItem(int,int,boolean)` 的 `simulate` 只是**方法形参**,真实提取的唯一出口是 `invokespecial ...ItemStackHandler.extractItem(IIZ)`;该指令**之前**槽内仍是**真实栈引用**(`this.stacks.get(slot)`,局部变量 4),且 Curios 自己**不做任何清槽**(清槽发生在 super 内部)。故在 `simulate == false` 时于该 INVOKE **之前**清理,既精确又绝不误伤 `simulate=true` 查询;这**正是方案 A 失败原因的反面**。

**C 同样可行**:`CurioSlot#set(ItemStack)` 在偏移 **0–4** 就 `this.getItem()` → 局部变量 2,`getItem()` 链路(`SlotItemHandler#getItem` → `IItemHandler#getStackInSlot`)返回的是**槽内真实对象引用,没有任何 `copy()`**;`super.set` 在偏移 **25–27** 才真正覆写。故 **HEAD 处 `getItem()` 确为真实旧栈**。

**但 C 单独不足以覆盖全部路径,B 单独也不足**,二者互补:B 覆盖所有经 `SlotItemHandler#remove`/`extractItem(false)` 的路径;C 覆盖所有经 `setByPlayer`/`set`/`safeInsert` 的路径。**B+C 仍存在"无覆盖"路径**,见第 6 节(核心:`CuriosContainer#quickMoveStack` 从**玩家背包**侧无法搬入/搬出饰品栏以外的例外、`CosmeticCurioSlot`、`curioBreak` 破坏路径、`CuriosContainer#clicked` 的内部搬移、数据包 `setItem` 客户端覆写等)。

是否需要**额外** mixin:**需要**——至少两处(`DynamicStackHandler#extractItem` + `CurioSlot#set`);若要覆盖破坏路径还需核对第 4 节结论。

---

## 1. 仓库现有 mixin 基础设施

### 1.1 两子项目**都已有** mixin

| 项 | N（`neoforge-1.21.1`） | F（`forge-1.20.1`） |
|---|---|---|
| 配置文件 | `neoforge-1.21.1\src\main\resources\astral_dice.mixins.json`（24 行，`required:true`,`compatibilityLevel:"JAVA_21"`,`package":"com.merlinkitsune.astral_dice.mixin"`,`injectors.defaultRequire:1`） | `forge-1.20.1\src\main\resources\astral_dice.mixins.json`（24 行，同结构，`JAVA_17`） |
| 附加配置 | `neoforge-1.21.1\src\main\resources\astral_dice.neoforge_fixes.mixins.json`（`required:false`,`injectors.defaultRequire:0`,package `...mixin.fixes`,仅 `LivingEntityDamageContainerMixin`） | 无 |
| `mixins` 数组 | `EntityThunderHitMixin`、`LightningBoltStrikeScopeMixin`、`trade.MerchantContainerMixin`、`trade.MerchantMenuMixin`、`trade.MerchantOfferMixin`、`trade.MerchantResultSlotMixin`、`trade.ServerPlayerMixin`、`PiglinAiMixin` | `EntityThunderHitMixin`、`LightningBoltStrikeScopeMixin`、`PiglinAiMixin`、`trade.MerchantContainerMixin`、`trade.MerchantMenuMixin`、`trade.MerchantOfferMixin`、`trade.MerchantResultSlotMixin`、`trade.ServerPlayerMixin` |
| `client` 数组 | `client.AstralUseItemGuardMixin`、`client.EffectRenderingInventoryScreenMixin`、`client.GuiMixin` | 同 N（三项同名） |
| `@Mixin` 类目录 | `neoforge-1.21.1\src\main\java\com\merlinkitsune\astral_dice\mixin\{,trade,client,fixes}\` | `forge-1.20.1\src\main\java\com\merlinkitsune\astral_dice\mixin\{,trade,client}\` |

### 1.2 注册方式（两版**不同**,这是最关键的差异）

**N（NeoForge）——经 mods.toml：**
- `neoforge-1.21.1\src\main\templates\META-INF\neoforge.mods.toml`：
  - `[[mixins]]` / `config="${mod_id}.mixins.json"`
  - `[[mixins]]` / `config="${mod_id}.neoforge_fixes.mixins.json"`（注释说明:`required=false` + `require=0`,目标方法变化时只静默失效,不导致启动失败）
- `neoforge-1.21.1\build.gradle`:158-159 —— 仅 `compileOnly 'org.spongepowered:mixin:0.8.5'`（注释:"NeoForge 运行时自带 Mixin,编译期仅需注解类;NeoForge 编译/运行同为 Mojmap,**无需 refmap**"）。**没有** `mixin { config ... }` 块,也**没有** `MixinConfigs` 清单属性（不需要）。

**F（Forge 1.20.1）——双通道,且 mods.toml 那一路是死路：**
- `forge-1.20.1\src\main\templates\META-INF\mods.toml` 里**也写了** `[[mixins]] config="${mod_id}.mixins.json"`,但 `forge-1.20.1\build.gradle`:230-237 的注释已核实:**FML 47.4.10 对 Mixin 零集成,`[[mixins]]` 在 1.20.1 被完全忽略**（该写法只在 NeoForge 生效）。
- 真正生效的两条通道（`build.gradle`:238-246）：
  1. **生产 jar** → `META-INF/MANIFEST.MF` 的 `MixinConfigs` 属性,由 `tasks.withType(Jar).configureEach { manifest { attributes 'MixinConfigs': 'astral_dice.mixins.json' } }` 写入（本条同时覆盖 `jar` 与 `reobfJar`,因为 `RemapJar extends Jar`）;
  2. **开发环境** → `mixin { config 'astral_dice.mixins.json' }`（MDG 的 mixin 扩展按 RunModel 注入 `--mixin.config`）。
- 运行时由 **Mixin Booster** 接管。已核实 `C:\Users\xmace\.gradle\caches\modules-2\files-2.1\maven.modrinth\mixinbooster\rOaAYvPZ\101cbc74611f0fbde2d332c4385fcc33fce0b8ac\mixinbooster-rOaAYvPZ.jar` 内含 `org/sinytra/mixinbooster/MixinModlauncherRemapper.class`(`implements org.spongepowered.asm.mixin.extensibility.IRemapper`,方法 `mapMethodName(String,String,String)`/`mapFieldName`/`map`/`unmap`/`mapDesc`/`unmapDesc`,构造器 `lambda$new$0(cpw.mods.modlauncher.Environment)` ⇒ 从 ModLauncher 取环境后用其 `INameMappingService` 做映射)。**⇒ "Mojmap 名字写进注解、运行时映射到 SRG" 这条链路是真实存在的,不是猜测。**
- **硬前置已就位**:`forge-1.20.1\build.gradle`:222 `modImplementation "maven.modrinth:mixinbooster:rOaAYvPZ"`;`templates/META-INF/mods.toml` 内 `[[dependencies.${mod_id}]]` 段 `modId="mixinbooster"` + `mandatory=true` + `versionRange="[0.1.3,)"`。⇒ **F 侧已是 Mixin Booster 硬前置**（未安装即 FML 依赖排序阶段拒绝启动）。

**旁证（注解字符串在生产 jar 中确实未被 reobf 改写,故必须靠运行时映射）:**
`forge-1.20.1\build\libs\astral_dice-1.2.1+forge_1.20.1.jar` → `com.merlinkitsune.astral_dice.mixin.PiglinAiMixin` 的 `javap -v` 常量池显示:
- `#20 = Utf8 isWearingGold`（注解值仍是 **Mojmap** 名）
- 同一类的方法体里却是 `#47 Methodref net/minecraft/world/entity/player/Player.m_9236_`（**SRG**）
⇒ 类内代码引用被 reobf 成 SRG,而 `@Inject(method=...)` 的字符串**没有**被 reobf。这既证明了"必须靠 Mixin Booster 运行时映射",也说明**注解字符串在运行时是按名字查表的**。

### 1.3 新增一处 mixin 需要改的地方（逐条）

**N 侧:**
1. 新建类,放在 `com.merlinkitsune.astral_dice.mixin` 下（子包可,如 `mixin.curios`）,用 `@Mixin(目标类.class)`。目标类若在 Curios 内,`net.neoforged.moddev` 已在 `implementation 'maven.modrinth:curios:yohfFbgD'`(`build.gradle`:155/222)提供**编译期可见性** ⇒ 无需新增依赖。
2. 在 `neoforge-1.21.1\src\main\resources\astral_dice.mixins.json` 的 `mixins`（服务端/通用）或 `client`（仅客户端）数组中**加一条全限定短名**（相对 `package` 声明）。**漏加 = mixin 永不加载,且不报错**（`required:true` 只保证配置文件本身必须存在,不保证其中每条都被加载——只要文件被加载且所列类都存在即可;新增类若只存在于源码而未列入数组,则完全不会被应用）。
3. 若目标是 **`net.minecraft.*`** 方法且方法名在运行时变体不同 ⇒ 不需要额外处理（N 编译/运行同 Mojmap,无 refmap）。
4. 若目标方法/字段是 `private` 或需直接读字段 ⇒ 加 `@Shadow`/`@Accessor`,或在 `astral_dice.mixins.json` 同级新增 access widener/AT（本仓 N 侧目前**没有** AT 文件,`neoforge.mods.toml` 的 `[[accessTransformers]]` 注释块**未被启用**）⇒ **不需要新配置项**。
5. 若新 mixin 目标在未来版本可能不存在,想"静默失效而非启动崩溃" ⇒ 参照 `astral_dice.neoforge_fixes.mixins.json` 的做法:**新建一个独立配置文件**(`required:false` + `injectors.defaultRequire:0`),并在 `neoforge.mods.toml` 再加一个 `[[mixins]] config=...`。这是本仓**已有先例**的"新配置项"。

**F 侧:**
1. 新建类,同上包路径（`forge-1.20.1\src\main\java\com\merlinkitsune\astral_dice\mixin\...`）。Curios 在 F 侧为 `modImplementation "maven.modrinth:curios:IPQlZkz1"`(`build.gradle`:195),编译期可见（MDG 会在解析期把它重映射为 dev Mojmap,见 `build.gradle`:38 注释）⇒ **无需新增依赖**。
2. 在 `forge-1.20.1\src\main\resources\astral_dice.mixins.json` 的 `mixins`/`client` 数组加一条短名。**漏加同样静默失效**。
3. **注解字符串写 Mojmap 名**（本仓既有约定,见 `PiglinAiMixin` 顶部注释"1.20.1 使用 Mixin Booster 运行时重映射,方法引用写 Mojmap 名"）。
4. `MixinConfigs` 清单属性已被 `tasks.withType(Jar).configureEach` 统一覆盖,**新增类无需改 build.gradle**;但如果**新增第二个 mixin 配置文件**,则必须同时:(a) 跑一次 `:forge-1.20.1:build` 验证 `build/generated/sources/modMetadata/META-INF/mods.toml` 未被截断（AGENTS.md 记录的 Groovy 模板展开坑,仅与 mods.toml 注释有关）;(b) 因生产通道只认 `META-INF/MANIFEST.MF` 的 `MixinConfigs`,需把属性改成逗号分隔多值。
5. F 侧**不要**指望 `mods.toml` 的 `[[mixins]]` 生效（已核实被忽略）。
6. Mixin Booster 为**硬前置且已声明**,无需改动。
7. **F 侧残留风险（本报告唯一与"映射"有关的存疑点）**:Curios 生产 jar 中 `CurioSlot#set` 显示为 `m_5852_`（它 override 的 MC 方法是 `Slot.m_5852_`）。Mixin Booster 的 `IRemapper.mapMethodName(owner, name, desc)` 是否会在 `owner = top/theillusivec4/curios/common/inventory/CurioSlot`（**mod 类,不在任何 tsrg 映射表里**）时,仍能沿类层次找到 `net/minecraft/world/inventory/Slot.set` 的映射 —— **本报告无法判定**(需要一次真实启动验证,本任务禁止启动游戏)。⇒ **规避办法见第 3 节"备选注入点 C-b"**:改注入 `net.minecraftforge.items.SlotItemHandler#set`(Forge 自有类,确定在映射表内) + `instanceof CurioSlot` 守卫。

### 1.4 现有 mixin 里是否有 Curios 目标

`grep` 全部 mixin 源文件:**没有任何一个既有 mixin 以 Curios 类为目标**（8 个 N / 8 个 F 全部指向 `net.minecraft.*` / `net.minecraftforge.*` / `net.neoforged.neoforge.*`）。⇒ 本次是**第一次** mixin 进第三方 mod,1.3-7 的映射存疑点没有既有先例可参考。

---

## 2. 方案 B 的注入点

### 2.1 目标类（N/F 同构，全限定名相同）

```
top.theillusivec4.curios.common.inventory.DynamicStackHandler
  extends ItemStackHandler (N: net.neoforged.neoforge.items / F: net.minecraftforge.items)
  implements IDynamicStackHandler
```

- **`CurioStacksHandler` 没有 `extractItem`**（javap 方法表已核对,两版均无）,它只持有 `stackHandler`/`cosmeticStackHandler` 两个 `IDynamicStackHandler` 字段并提供 `getStacks()`/`getCosmeticStacks()`。⇒ **B 的唯一目标就是 `DynamicStackHandler`**。
- 方法签名（两版完全一致）:`public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate)`
- 同名同构证据:两版 `javap -p` 的方法列表逐行相同（唯一差别是 F 的 `CurioUnequipEvent` vs N 的 `CurioCanUnequipEvent`、`Event$Result` vs `TriState`）。

### 2.2 反编译行号表（`javap -l`）

`DynamicStackHandler.extractItem` 的 `LineNumberTable`：

| 源码行 | N 字节码偏移 | F 字节码偏移 | 对应源码 |
|---|---|---|---|
| 78 (N) / 79 (F) | `0` | `0` | `ItemStack existing = this.stacks.get(slot);` |
| 89 (N) / 90 (F) | `111` | `111` | 权限判定分支起始 |
| **93** | **`174`** | **`171`** | **`return super.extractItem(slot, amount, simulate);`** |
| 95 | `182` | `179` | `return ItemStack.EMPTY;` |

`LocalVariableTable`（两版完全一致,这是**最关键的证据**）：

```
Start  Length  Slot  Name       Signature
    0     186     0  this       Ltop/theillusivec4/curios/common/inventory/DynamicStackHandler;
    0     186     1  slot       I
    0     186     2  amount     I
    0     186     3  simulate   Z
   13     173     4  existing   Lnet/minecraft/world/item/ItemStack;   <-- 真实栈引用
   31     155     5  ctx        Ltop/theillusivec4/curios/api/SlotContext;
   44     142     6  unequipEvent ...
   62     124     7  result ...
  111      75     8  isCreative Z
           96      12     9  player ...
```
（F 的 Length 略短:`183/170/152/139/121/72`,slot 与名字完全相同。）

### 2.3 `simulate` 形参落在哪个局部变量槽、在哪条指令被消费

- **落在局部变量槽 3**（`this`=0,`slot`=1,`amount`=2,`simulate`=3）。
- **本方法内 `simulate` 没有被任何 `ifeq`/`ifne` 分支消费**（需要明确纠正这一预设):它在整个方法体里**只被取用一次**,就是作为 `invokespecial` 的第三个实参:
  - **N**:偏移 `177: iload_3` → 偏移 `178: invokespecial #158 // Method net/neoforged/neoforge/items/ItemStackHandler.extractItem:(IIZ)Lnet/minecraft/world/item/ItemStack;`
  - **F**:偏移 `174: iload_3` → 偏移 `175: invokespecial #203 // Method net/minecraftforge/items/ItemStackHandler.extractItem:(IIZ)Lnet/minecraft/world/item/ItemStack;`
- 方法体内的 `if_acmpne 74`(N 偏移 67)、`ifeq 108`(86)、`if_acmpeq 174`(116)、`ifne 143`(124/129)、`ifne 182`(140)、`ifeq 182`(171) 全部只与 `result`(槽 7)、`isCreative`(槽 8)、`existing.isEmpty()`、`EnchantmentHelper.has(...)`、`CuriosApi.getCurio(...).canUnequip(...)` 有关,**与 `simulate` 无关**。
- ⇒ **`simulate==true` / `false` 的分支发生在 super(`ItemStackHandler.extractItem`)内部,不在 `DynamicStackHandler` 里。**

### 2.4 真实提取路径上被提取的栈是哪个局部变量、在何处取出、在何处清槽

- **在何处取出**:偏移 `0: aload_0` / `1: getfield #122 (N) | #174 (F) // Field stacks:Lnet/minecraft/core/NonNullList;` / `4: iload_1` / `5: invokevirtual NonNullList.get:(I)Ljava/lang/Object;` / `8: checkcast ItemStack` / `11: astore 4`。
  - `NonNullList.get(int)` 是 **`return this.list.get(index);`**（`neoforge-21.1.235-sources.jar` → `net/minecraft/core/NonNullList.java:68-70`),**不做任何复制**。⇒ **局部变量 4 `existing` 就是槽位里那个真实 `ItemStack` 对象本身。**
- **Curios 自己不清槽**:`DynamicStackHandler.extractItem` 的字节码里**没有** `setStackInSlot`、也没有 `NonNullList.set`、也没有 `ItemStack.shrink`;唯一的清槽发生在 `super.extractItem` 内部：
  - N:`net/neoforged/neoforge/items/ItemStackHandler.java:106-113` ⇒ 整栈提取且 `!simulate` 时 `this.stacks.set(slot, ItemStack.EMPTY)`（偏移 `108`),否则 `return existing.copy()`;
    行 `114-121` ⇒ 部分提取且 `!simulate` 时 `this.stacks.set(slot, existing.copyWithCount(existing.getCount() - toExtract))`。
  - F:`net/minecraftforge/items/ItemStackHandler.java:123-133`（同构,`this.stacks.set(slot, ItemStack.EMPTY)` 在行 127;`return existing;` 在行 129——**F 47.4.10 也直接返回 `existing` 本体,不是 copy**）。
  - ⇒ **无论两版哪一支,"清槽"都发生在 `invokespecial` 之后**。

### 2.5 ⇒ 注入点建议

**结论:B 可行,而且注入点非常干净。** 应在 **`DynamicStackHandler#extractItem`** 内、`invokespecial ItemStackHandler.extractItem(IIZ)…` **这条指令之前**注入（即 N 偏移 `178`、F 偏移 `175` 之前）:

```
// 伪代码（描述,不是待提交代码）
@Mixin(DynamicStackHandler.class)
abstract class DynamicStackHandlerMixin {
    @Inject(method = "extractItem",
            at = @At(value = "INVOKE", opcode = Opcodes.INVOKESPECIAL,
                     target = "Lnet/neoforged/neoforge/items/ItemStackHandler;extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;"))  // F 换成 net/minecraftforge/items/ItemStackHandler
    private void astral$cleanOnRealUnequip(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (simulate) return;                       // ← 天然只在真实提取时触发
        ItemStack real = ((DynamicStackHandler)(Object)this).getStackInSlot(slot);   // 或 @Shadow 读 local 4
        ... 清理 real 的物品组件 ...
    }
}
```

**为什么这个位置是"既只在真实提取时触发、又拿得到槽内真实栈"的唯一/最佳解:**

| 候选位置 | `simulate==true` 查询会不会触发 | 拿到的栈 | 判定 |
|---|---|---|---|
| `@At("HEAD")` + `if(!simulate)` | **不会**（形参已知,可判) | `getStackInSlot(slot)` = 真实栈 ✓ | **次优**:HEAD 处还不知道 `result` 会不会 DENY(偏移 62-73 直接 `areturn EMPTY`);若外部 mod 用 `CurioCanUnequipEvent`/`CurioUnequipEvent` 拒绝卸下,物品其实**没被移走**,而 HEAD 已经清过组件 ⇒ **误清**。 |
| **`@At(INVOKE, INVOKESPECIAL, ItemStackHandler.extractItem)`（推荐)** | **不会**（形参已知) | `getStackInSlot(slot)` = 真实栈 ✓ | **最优**:此处已通过 `result`/`canUnequip`/`PREVENT_ARMOR_CHANGE` 全部闸门,提取**必然发生**;且 super 尚未执行 ⇒ 槽内还是那一件。 |
| `@At("RETURN")` | 不会(可判) | 返回的是提取结果（N/F 整栈分支返回 `existing` 本体;部分分支返回 `copyWithCount`) | 可行但**更差**:① 槽已被清,`getStackInSlot` 返回 EMPTY;② 部分提取时槽里可能留有残余(带脏组件);③ 需要额外判 EMPTY 区分"被拒绝"。 |
| `@At(HEAD of canUnequip)` / `CurioCanUnequipEvent` | **会**(已证,见 `docs/batch3/D-B1-optionA-proof.md`) | 真实栈 | **不可用** |

**B 的注入点拿得到真实栈吗？——拿得到。** 证据链:(a) 偏移 4-11 从 `NonNullList.get` 取到的是元素本体;(b) Curios 自身不清槽;(c) 清槽在 super 内、且在注入点之后。(不存在"真实提取时也已先复制再清槽"的情形 ⇒ **B 可行**。)

**B 的额外好消息（映射风险为 0）**:`extractItem` 是 **Forge/NeoForge 自己声明的方法**(`net.minecraftforge.items.ItemStackHandler.extractItem` / `net.neoforged.neoforge.items.ItemStackHandler.extractItem`),**不在 MC 的 SRG 映射表内**。已核实:F 生产 Curios jar 里 `DynamicStackHandler.extractItem` 就叫 `extractItem`(它 override 的是 Forge 方法),因此 **F 侧 `method="extractItem"` 与 `@At` 的 `target="Lnet/minecraftforge/items/ItemStackHandler;extractItem(IIZ)..."` 都不需要任何重映射**,写法在两版一致。

---

## 3. 方案 C 的注入点

### 3.1 CurioSlot 全限定名与 set 签名（N/F 同构,显示名不同）

```
top.theillusivec4.curios.common.inventory.CurioSlot
  N: extends net.neoforged.neoforge.items.SlotItemHandler
  F: extends net.minecraftforge.items.SlotItemHandler
```
- N:`public void set(net.minecraft.world.item.ItemStack)`
- F:`public void m_5852_(net.minecraft.world.item.ItemStack)` —— **SRG 名**,它 override 的是 `Slot.m_5852_`（即 Mojmap 的 `Slot#set`）。
- `CurioSlot` **没有** `setByPlayer` 覆写;`setByPlayer(ItemStack)` 是 `Slot` 的方法：N `Slot.java:71-73` `this.setByPlayer(stack, this.getItem())` → `:75-77` `this.setByPlayer(newStack, oldStack) { this.set(stack); }`。
- `CurioSlot` **没有** `onTake`、**没有** `onBreak`、**没有** `remove`、**没有** `canUnequip` 覆写（javap 全量方法表已核:仅 `getIdentifier/canToggleRender/isActiveState/isCosmetic/showCosmeticToggle/getSlotExtension/getSlotContext/getRenderStatus/getSlotName/set/allowModification`）。
- `CosmeticCurioSlot extends CurioSlot`（N/F 同）且**不覆写 `set`** ⇒ C 对"装饰栏"同样生效。

### 3.2 HEAD 处 `getItem()` 是否确为真实旧栈 —— **是,且不经 `copy()`**

`CurioSlot#set` 字节码（N,`javap -c -p`,带 `-l` 行号）:
```
  public void set(net.minecraft.world.item.ItemStack);
       0: aload_0
       1: invokevirtual #161   // Method getItem:()Lnet/minecraft/world/item/ItemStack;   <-- 进入方法后第一次 getItem()
       4: astore_2
       5: aload_2
       6: invokevirtual #165   // Method net/minecraft/world/item/ItemStack.isEmpty:()Z
       9: ifeq          23
      12: aload_1
      13: invokevirtual #165   // isEmpty()
      16: ifeq          23
      19: iconst_1
      20: goto          24
      23: iconst_0
      24: istore_3
      25: aload_0
      26: aload_1
      27: invokespecial #170   // Method net/neoforged/neoforge/items/SlotItemHandler.set:(Lnet/minecraft/world/item/ItemStack;)V   <-- 真正覆写槽内容
      30: iload_3
      ...
      77: return
  LineNumberTable: line 155: 0 / 156: 5 / 157: 25 / 159: 30 / ... / 165: 77
  LocalVariableTable: slot 1 = stack, slot 2 = current, slot 3 = flag
```
F 完全同构:`public void m_5852_(ItemStack)`，偏移 `1: invokevirtual #201 // Method m_7993_:()...`（`Slot.m_7993_` = Mojmap `Slot#getItem`），偏移 `27: invokespecial #208 // Method net/minecraftforge/items/SlotItemHandler.m_5852_(...)`，`LineNumberTable: 115:0 / 116:5 / 117:25 / 119:30 / ... / 124:70`。

**链路逐跳核实（无 copy）:**
1. `CurioSlot.getItem()`（javac 以 owner=CurioSlot 发出 Methodref，运行时解析到 `SlotItemHandler#getItem`）
2. `SlotItemHandler#getItem` N 偏移 `0-13`:`getItemHandler()` → `IItemHandler.getStackInSlot(index)` → `areturn`；F 偏移 `0-13` 逐字节同构。
3. `ItemStackHandler#getStackInSlot(int)` → `this.stacks.get(slot)`（越界则 `ItemStack.EMPTY`）。
4. `NonNullList#get` = `return this.list.get(index);`（`NonNullList.java:68-70`）。

⇒ **把 `current`(局部变量 2)写成 `stack.copy()` 之类的地方一个都没有**（`CurioSlot.set` 的常量池里没有 `ItemStack.copy`，只用了 `isEmpty`/`ItemStack.matches`）。**HEAD 的 `getItem()` = 槽内真实旧栈对象**,并且写入它会即时反映到 `DynamicStackHandler` 的 `NonNullList` 元素上。

### 3.3 逐个路径确认（原版 `AbstractContainerMenu#doClick` 行号 + 调用链）

> 行号取自两份反编译源码 jar（N `AbstractContainerMenu.java` / F 同名文件），两版结构一致,N 的行号整体比 F 小约 16 行。

| 点击类型 | doClick 行号（N / F） | 调用链 | 是否经过 `extractItem(false)`（B） | 是否经过 `CurioSlot#set`（C） | 生效判定 |
|---|---|---|---|---|---|
| **PICKUP 空手取出**（左键） | N `:424` `slot7.tryRemove(...)` / F `:431` | `tryRemove`(`Slot.java` N `:154-172` / F `:151-172`) → `remove`(N `:107-109` / F `:104-106`) → `SlotItemHandler#remove`（N 偏移 8-15: `extractItem(index, amount, iconst_0)`) → `DynamicStackHandler#extractItem` | **会** | 之后 `newStack.isEmpty()` 时 `setByPlayer(EMPTY, itemstack)`（N `:165-167` / F `:162-164`）→ `set` → **会,但此刻槽已空** | **B 生效**,C 空跳过 |
| **PICKUP 光标换装**（光标上有**不同**饰品） | N `:433-435` `setCarried(itemstack9); slot7.setByPlayer(itemstack10);` / F `:440-442` | 直接 `setByPlayer` | **不会** | **会**,且 `getItem()` = `itemstack9`（即将被放到光标上的**同一对象**） | **C 生效**（唯一生效者） |
| **PICKUP 同类合并**（光标同物品） | N `:430-432` `safeInsert` / F `:437-439` | `safeInsert`(`Slot.java:184-200`) → `setByPlayer` | 不会 | 会（`getItem()` 可能是被 `setCount/shrink` 就地改动过的同一对象） | 属装备方向,C 无害跳过 |
| **PICKUP_ALL 双击收集** | N `:499-521`（`:515` `slot8.safeTake(...)`）/ F `:504-524` | `safeTake`(`Slot.java:174-178`) → `tryRemove` → `remove` → `extractItem(false)` | **会** | 之后 `setByPlayer(EMPTY)` → 会但槽已空 | **B 生效** |
| **THROW 丢弃**（Q 键 / F 键） | N `:494-498`（`:497` `slot3.safeTake(j1, MAX, player)`）/ F `:499-503` | 同上 | **会** | 同上 | **B 生效** |
| **SWAP 数字键 / 副手 F** | N `:451-487` / F `:456-492` | 见下 | **不会**（分支内无 `remove`） | **会**（三种子分支都会 `setByPlayer`） | **C 生效** |
| **QUICK_MOVE 快捷移动（shift 点击）** | N `:391-405`（`:401` `quickMoveStack`）/ F `:400-411` | Curios 覆写的 `quickMoveStack` → `moveItemStackTo`（N `AbstractContainerMenu.java:630-692`）→ 之后 `Slot.set(EMPTY)` | **不会** | **会,但 `getItem()` 已被 `moveItemStackTo` 就地改成 count=0 的空栈** | **两者都失效 ⇒ 无覆盖（见下）** |
| **QUICK_CRAFT 拖拽分发** | N `:329-345`（`:366` `slot1.setByPlayer(...)`）/ F `:345-358`（`:375`） | `setByPlayer` | 不会 | 会（目标通常是空槽 ⇒ `getItem()` 为空） | 装备方向 |
| **CLONE 创造中键** | N `:488-493`（`:492` `setCarried(itemstack5.copyWithCount(max))`）/ F `:493-498` | —— | 不会 | 不会 | **不是卸下**（槽内容不变） |
| **拖出窗口（slotId = -999）** | N `:382-390` / F `:391-399` | `player.drop(getCarried(), ...)` | 不会（作用于光标栈） | 不会 | 光标上的物品此前已经过 B 或 C |

**`Slot.java` 两版行号对照**（同一逻辑,F 整体小 3 行左右;本报告正文凡只给一个行号处均为 **N**)：

| 方法 | N 行号 | F 行号 |
|---|---|---|
| `getItem()` | `:63-65` | `:64-66` |
| `setByPlayer(ItemStack)` / `setByPlayer(ItemStack,ItemStack)` | `:71-73` / `:75-77` | `:72-74` / `:76-78` |
| `set(ItemStack)` | `:82-85` | `:79-82` |
| `remove(int)` | `:107-109` | `:104-106` |
| `tryRemove(int,int,Player)` | `:154-172` | `:151-172` |
| `safeTake(int,int,Player)` | `:174-178` | `:171-175` |
| `safeInsert(ItemStack[,int])` | `:180-200` | `:179-200` |
| `onTake(Player,ItemStack)` | `:52-54` | `:53-55` |
| `mayPickup(Player)` | `:114-116` | `:111-113` |
| `allowModification(Player)` | （NeoForge 新增，`Slot` 内为默认实现） | `:201-203` |

**SWAP 分支逐子分支（N `:451-487`,F `:456-492`）:**
- `itemstack2.isEmpty()`(热键槽为空) → N `:458 inventory.setItem(button, itemstack7)`（**`itemstack7 = slot5.getItem()`，同一对象**）→ N `:460 slot5.setByPlayer(ItemStack.EMPTY)` ⇒ C 的 `getItem()` = 那个对象,而它正躺在热键槽里 ⇒ **C 清理它 = 清理玩家真正拿到的那件**。✔
- `itemstack7.isEmpty()`(饰品槽为空) → N `:467/:470 slot5.setByPlayer(itemstack2...)` ⇒ C 的 `getItem()` = EMPTY ⇒ 跳过（正确）。✔
- 两者都非空 → N `:482 inventory.setItem(button, itemstack7)` + `:483 slot5.setByPlayer(itemstack2)` ⇒ 同第一个子分支。✔
- 大堆叠子分支 N `:476 slot5.setByPlayer(itemstack2.split(k2))` + `:478 if (!inventory.add(itemstack7)) player.drop(...)` ⇒ 旧对象被 `inventory.add`/`drop` 拿走,C 仍拿到它。✔

**QUICK_MOVE 为什么会失效（关键证据）:**
- `CuriosContainer.quickMoveStack`（N）偏移 `27` 取 `itemstack = slot.getItem()` —— **活对象**;偏移 `32-37` 才 `itemstack.copy()` 存返回値。
- 偏移 `49-57`（`slotId==0`）/`83-91`/`107-115`/`170-180`/`215-228`/`295-303`/`319-327`/`343-351`/`361-369` 全部调用 `moveItemStackTo(itemstack, ...)`。
- `AbstractContainerMenu#moveItemStackTo`（N `:630-692`）里**对源槽只有就地改动**:`:645 stack.setCount(0)`、`:650 stack.shrink(...)`、`:677 slot1.setByPlayer(stack.split(...))`（**注意 `slot1` 是目标槽,不是源槽**）;`split` = `copyWithCount`（`ItemStack.java:580-588` → `copy()` → `:570-578 new ItemStack(item, count, this.components.copy())`）。⇒ **进入背包/热键的是在 `split` 里产生的副本,副本建立时刻远早于任何 `set`。**
- 偏移 `379-395`（N;F `CuriosContainerV2` 偏移 `378-394`、F legacy `CuriosContainer` 偏移 `347-363`）：`if (itemstack.isEmpty()) slot.set(ItemStack.EMPTY);` ⇒ C 在这里被调用,但 `getItem()`（= `itemstack`,同一对象）此时 **count 已为 0（`isEmpty()==true`）**。
- **⇒ QUICK_MOVE 下 B 不触发、C 触发时已经太晚(且对象已空)。**

### 3.4 Curios 自身路径

| 路径 | 存在性（javap 核实） | 是否走 `extractItem` | 是否走 `set`/`setStackInSlot` |
|---|---|---|---|
| `CurioSlot#onTake` | **不存在**（`CurioSlot` 未覆写） | — | — |
| `CuriosEventHandler#curioRightClick`（N `:public void curioRightClick(PlayerInteractEvent$RightClickItem)`） | 存在 | 不 | 不（只读 `getStacks()`/`getActiveStates()`/`getRenders()` 并回调 `ICurio` 的右键使用钩子） |
| `curioBreak` | 存在:接口 `ICurio#curioBreak(SlotContext)`、`ICurioItem#curioBreak(SlotContext, ItemStack)`、桥接 `ItemizedCurioCapability#curioBreak` | 不 | 不 |
| `CuriosBrokenEvent` | **不存在**（两版 jar 内无此类） | — | — |
| `CuriosContainer#clicked`（N） | 存在,且**只**拦 `ClickType.CLONE` + `hasInfiniteMaterials` + 光标空 + `slot instanceof CurioSlot` → `ICurioSlotExtension.getCloneStack(...)` → `setCarried(copyWithCount(max))`;其余一律 `invokespecial RecipeBookMenu.clicked` → `AbstractContainerMenu.clicked` → `doClick`（N 偏移 `0-103`） | 不 | 不（CLONE 不改槽） |
| `CuriosContainerV2`（F） / F 基类 `CuriosContainer` 的 `clicked`（`m_4426_`） | **两版 F 容器都没有 `clicked` 覆写**⇒ N 的 CLONE 拦截在 F **不存在** | — | — |
| `CuriosContainer#quickMoveStack` / `CuriosContainerV2#m_7648_` | 存在 | 不 | **会**:末尾 `Slot.set(EMPTY)`（N 偏移 `392`；F V2 偏移 `391`；F legacy 偏移 `360`） |
| `CuriosContainer#setItem`（N） | 存在:`if (slots.size() > slotId) super.setItem(slotId, stateId, stack)` → `AbstractContainerMenu.setItem`（N `:604-607`）→ `getSlot(slotId).set(stack)` | 不 | **会（客户端也会）** |

**Curios 内所有改动 curious 槽内容的入口（全 jar 字符串枚举,可作为覆盖度审计口径）:**

| 类 | N | F |
|---|---|---|
| `CuriosEventHandler` | `setStackInSlot` + `extractItem` | 同 |
| `CurioStacksHandler` | `setStackInSlot`（`loseStacks`） | 同 |
| `DynamicStackHandler` | `extractItem` | 同 |
| `CuriosServerPayloadHandler`(N) / `CPacketDestroy`(F) | `setStackInSlot` | `setStackInSlot` |
| `CuriosClientPackets`(N) / `SPacketSyncStack`(F) | `setStackInSlot` | `setStackInSlot` |
| `CuriosCommand`（`/curios` 命令） | `setStackInSlot` | 同 |
| `CurioInventory` / `CurioInventoryCapability`(N) / `CurioInventoryCapability$CurioInventoryWrapper`(F) / `CurioItemHandler`(N) | `setStackInSlot` / `extractItem` | `setStackInSlot` |

⇒ **C 只覆盖"经 `CurioSlot`"的那一支;所有直接写 handler 的路径（`CurioStacksHandler.loseStacks`、`CPacketDestroy`、`/curios` 命令、实体 Curios、客户端同步）都不经 `CurioSlot#set`。**

### 3.5 ⭐ 更优的等价替代：**方案 D = 注入 `ItemStackHandler#setStackInSlot`**

因为 `CurioSlot#set` → `SlotItemHandler#set` → `IItemHandlerModifiable#setStackInSlot`（N `SlotItemHandler#set` 偏移 `12: invokeinterface IItemHandlerModifiable.setStackInSlot`），而 `DynamicStackHandler` **没有覆写** `setStackInSlot`（javap 已核），所以:

```
@Mixin(targets = "net.neoforged.neoforge.items.ItemStackHandler")          // F: net.minecraftforge.items.ItemStackHandler
@Inject(method = "setStackInSlot", at = @At("HEAD"))
private void astral$cleanOnSlotReplace(int slot, ItemStack newStack, CallbackInfo ci) {
    if (!(this instanceof DynamicStackHandler dh)) return;      // 只关心 Curios 的槽
    ItemStack old = dh.getStackInSlot(slot);                    // HEAD 处仍是真实旧栈
    if (old.isEmpty() || old == newStack) return;
    if (!newStack.isEmpty() && newStack.is(old.getItem())) return;   // 同物品入槽 = Curios 重载,不清理
    ... 清理 old ...
}
```

**D 相对 C 的优势（全部有字节码支撑）:**
1. **覆盖 C 的全部路径**（因为 C 的实现最终就是 `setStackInSlot`），另加 `loseStacks`、`CPacketDestroy`、`/curios` 命令、实体 Curios、客户端同步写入。
2. **目标方法是 Forge/NeoForge 自有方法名（`setStackInSlot`），不需要 SRG 重映射** ⇒ 绕开第 1.3-7 条的 F 侧映射存疑点；C 依赖 `CurioSlot#set` 的 SRG 名 `m_5852_` 被 Mixin Booster 从 mod 类上解析出来。
3. 判据可以完全从 handler 侧推导（`old` / `newStack`），不再依赖 `onUnequip` 的两个参数。
4. 代价:需要 `instanceof DynamicStackHandler` 守卫（否则影响所有用 `ItemStackHandler` 的模组），以及需要 `@Accessor` 拿到 `ctxBuilder`（`DynamicStackHandler.ctxBuilder` 是 `protected`）以便判"服务端/玩家"。

**B 与 D 不会互相重复触发**:`ItemStackHandler.extractItem` 清槽走的是 `this.stacks.set(slot, ItemStack.EMPTY)`（**直接写 `NonNullList`**，见 `ItemStackHandler.java` N `:108` / F `:127`），**不经过 `setStackInSlot`** ⇒ D 在"真实提取"这一刻不会触发。D 只在"有人用新栈替换槽内容"时触发（`Slot.set`/`setByPlayer`/`safeInsert`/`loseStacks`/`CPacketDestroy`/`/curios` 命令/客户端同步）。

**不建议**写成 `@Mixin(DynamicStackHandler.class) @Inject(method="setStackInSlot")`:该方法并非 `DynamicStackHandler` 所声明,是否会被 Mixin 沿类层次解析到 super —— **本报告标记为「无法判定」**,直接 mixin 声明类 + `instanceof` 守卫可完全避免这个不确定性。

**结论(第 3 问):C 可行(HEAD `getItem()` 确为真实旧栈),但推荐用 D 取代 C。** 二者都不能覆盖 QUICK_MOVE(见第 6 节)。

---

## 4. BREAK 路径

### 4.1 存在性

| 候选 | 结论 | 证据 |
|---|---|---|
| `ICurio#curioBreak(SlotContext)` | **存在** | `javap -p top.theillusivec4.curios.api.type.capability.ICurio`（N/F 同） |
| `ICurioItem#curioBreak(SlotContext, ItemStack)` | **存在**（`ICurioItem` 新签名） | 同上 |
| `ItemizedCurioCapability#curioBreak(SlotContext)` | **存在**（桥接到 `ICurioItem#curioBreak(ctx, getStack())`） | N javap |
| `CuriosBrokenEvent` | **不存在** | 两版 jar 类清单中无此类。事件类各 **7** 个:N = `CurioAttributeModifierEvent`/`CurioCanEquipEvent`/`CurioCanUnequipEvent`/`CurioChangeEvent`/`CurioDropsEvent`/`DropRulesEvent`/`SlotModifiersUpdatedEvent`;F = 把 N 的 `CurioCanUnequipEvent` 换成 `CurioEquipEvent`+`CurioUnequipEvent`。**两版都没有 Broken 系事件。** |
| `CurioSlot#onBreak` | **不存在** | `CurioSlot` 全量方法表无 `onBreak` |
| `CuriosHelper#onBrokenCurio(SlotContext)` / `onBrokenCurio(String,int,LivingEntity)` | **存在**（旧 API 名） | N javap；字节码只有 `CuriosApi.broadcastCurioBreakEvent(ctx)` 一条调用，**不改槽** |
| `CuriosApi#broadcastCurioBreakEvent(SlotContext)` → `CuriosImplMixinHooks#broadcastCurioBreakEvent` | 存在 | N 偏移 `0-36`:构造 `SPacketBreak(entityId, identifier, index)` → `PacketDistributor.sendToPlayersTrackingEntityAndSelf`；F 用 `NetworkHandler.INSTANCE.send(TRACKING_ENTITY_AND_SELF, ...)` |

### 4.2 走不走 `extractItem` / `set`

**都不走,而且 `curioBreak` 在 jar 内唯一的调用点是客户端。**

- 全 jar 字符串扫描:"`curioBreak`" 只出现在 `ICurio`、`ICurioItem`、`ItemizedCurioCapability`、以及客户端包处理器（N `CuriosClientPackets` / F `SPacketBreak`）。
- N `CuriosClientPackets#handle(SPacketBreak)` → `lambda$handle$1` 偏移 `51: invokeinterface ICurio.curioBreak:(Ltop/theillusivec4/curios/api/SlotContext;)V`；F `SPacketBreak#lambda$handle$0` 偏移 `51` 同构。两者都在 `Minecraft.getInstance().level` 上取实体 ⇒ **纯客户端**。
- 同一个 lambda 里还先读 `getStackInSlot(slotId)`（N `lambda$handle$2` 偏移 `0-15`），若该栈没有 curio 能力则调用 `ICurio.playBreakAnimation(stack, entity)`（**播放破碎动画**，不删物品）。
- ⇒ **BREAK 不经过 B、也不经过 C/D**（服务端侧没有任何 `extractItem`/`setStackInSlot` 被这条路径触发）。

### 4.3 那"破坏"后的清理靠什么

1. **物品组件:不需要清理。** 饰品耐久耗尽由原版 `ItemStack` 耐久逻辑就地处理（`setDamageValue(...)` 然后 `ItemStack.shrink(1)`，`ItemStack.java:1085-1087 shrink → grow → setCount`）——**对象仍留在 `DynamicStackHandler.stacks` 里,但 `isEmpty()` 已为 true**。物品实体已经消失,其组件随之不可再获得,**不会"被下一个同 id 物品继承"**。⇒ 这正是"破坏即清空"由其它机制自动完成的情形。
2. **玩家级清理:由既有 tick `onUnequip` 路径完成。** `CuriosEventHandler#tick` 发现槽内栈变化后调 `prevCurio.onUnequip(ctx, 槽位当前内容=EMPTY)`,第 3 参是 `getPreviousStackInSlot(i)` 留下的快照副本 ⇒ `CurioSlotUtil.isIntentionalUnequip(EMPTY, 快照)` = **true** ⇒ 玩家级动作（护甲修饰器、移动累计表、待命计时器等）照常执行。**只有"写进第 3 参副本的物品组件归零"会丢**——而破坏路径本来就不需要它。
3. 若**第三方 mod** 主动调用 `CuriosApi.broadcastCurioBreakEvent` / `ICuriosHelper.onBrokenCurio`,同样只发包、不改槽 ⇒ 与我们无关。

**⇒ BREAK 既不是 B/C 的缺口,也不需要额外 mixin。**（唯一可讨论项:若将来把"耐久饰品"做成可保留累计值的形态,则须重新评估。）

---

## 5. 重复触发与幂等

### 5.1 一次 GUI 操作会不会**同时**触发 B 与 C/D —— **会,而且是固定顺序**

以最典型的"左键把饰品取到光标上"为例（N `Slot.java:154-172`）:

```
tryRemove(count, decrement, player)
  ├─ mayPickup(player)                     // SlotItemHandler#mayPickup → extractItem(index, 1, true)  ← simulate=true 查询(方案A被它误触发)
  ├─ count = min(count, decrement)
  ├─ this.remove(count)                    // Slot.java:107-109
  │    └─ SlotItemHandler#remove → IItemHandler.extractItem(index, count, false)   ← ★B 在这里
  └─ if (this.getItem().isEmpty())
         this.setByPlayer(ItemStack.EMPTY, itemstack)   // Slot.java:165-167 → Slot#setByPlayer → CurioSlot#set → setStackInSlot  ← ★C/D 在这里
```

⇒ **顺序恒为"先 `extractItem(false)` 后 `set`"**。所以:
- 到 C/D 触发时,`getItem()` **已经是 `EMPTY`**（B 的 super 已把槽设成 EMPTY,或 count 已归零）。
- 而 **B 触发时槽内仍是真实栈** ⇒ **B 必须是这条路径上真正干活的那个**;C/D 必须能识别"没有旧栈"并安全跳过。
- **反过来说:单靠 C 无法覆盖 PICKUP 取出路径。** 这纠正了"只靠 C 就够了"的直觉。

而 **SWAP / 光标换装** 路径恰好相反:没有任何 `extractItem`,只有 `setByPlayer` ⇒ **只有 C/D 会触发**,且 `getItem()` 是真实旧栈（也是玩家随后拿到的那一个对象）。

### 5.2 对同一个真实栈清理两次会不会有问题

不会,只要两个钩子都满足下面三条:

1. **不写 `ItemStack.EMPTY`。** 第二次触发时读到的 `old` 极可能是 `ItemStack.EMPTY`（**全局单例**）;若对它 `set(component, 0)` 会污染全局单例——本仓 `CurioSlotUtil.java:123-125` 已经因为同一个坑加了守卫,新钩子必须照抄（`old.isEmpty()` ⇒ return）。
2. **判据用"当前 handler 内容"而非回调参数。** 统一写成 `旧栈非空 且 (新栈为空 或 新旧不是同一物品)` ⇒ 第二次触发时 `旧栈.isEmpty()` 为真 ⇒ 自然跳过。
3. **写 0 本身幂等。** 就算偶发重复（例如 B 与 D 在同一次操作里都命中同一对象），`stack.set(MISAKI_SIGN_STACKS, 0)` 写两遍结果相同;`clearSignData` 的玩家级动作需另判重（现有实现里护甲修饰器 `removeModifier` 是幂等的,移动累计表若用 `clear()` 也幂等)。

> 注意一个真会重复的组合:**PICKUP 取出**下 `tryRemove` 先 `remove`（B 命中,清真实栈）后 `setByPlayer(EMPTY)`（D 命中,但 `old` 已是 EMPTY ⇒ 跳过）⇒ 实际只清一次。
> **SWAP** 下只有 `setByPlayer`（D 命中一次）⇒ 清一次。
> **光标换装** 下只有 `setByPlayer`（D 命中一次）⇒ 清一次。
> ⇒ 现行机制下**不存在"同一对象被清两次"**;即便出现也因三条不变式而无害。

### 5.3 `CurioSlotUtil.isIntentionalUnequip`（N `:122-130` / F `:123-131`）能不能直接复用

**语义上能复用,形式上需要新入口。** 逐条对照:

| 钩子 | 等价的 `(newStack, removedStack)` | `isIntentionalUnequip` 结果 | 是否正确 |
|---|---|---|---|
| **B**（`extractItem` 真实分支,提取前） | `newStack = ItemStack.EMPTY`（槽即将被清空）,`removedStack = old`(真实栈,非空) | `true` | ✔ 清理 |
| **D**（`setStackInSlot` HEAD） | `newStack` = 传入的新栈,`removedStack = old`(真实栈) | 新栈空 ⇒ true;新栈不同物品 ⇒ true;新栈同物品 ⇒ **false** | ✔ 与既有语义完全一致（同物品=Curios 重载不清理） |
| **C**（`CurioSlot#set` HEAD） | 与 D 相同 | 同上 | ✔ |
| 既有 tick 路径 | `newStack` = 当前槽内容(EMPTY),`removedStack` = 快照副本 | true（但**副本**） | 玩家级 ✔ / 物品组件 ✘（已知缺口） |
| `loseStacks` | `(EMPTY, getStackInSlot(i))` —— 第 3 参经 `ItemizedCurioCapability#getStack()` 返回**构造时存下的引用**,而 `CuriosApi.getCurio(stack)` 就是把槽内活对象包进去的 | true，且 **`removedStack` 是真实栈** ⇒ 现有清理其实有效 | ✔ |
| `CPacketDestroy` | `(槽内活对象, 同一活对象)` ⇒ 同物品 ⇒ **false** | **false** | ✘ **真正的漏网**(`CurioSlotUtil.java:120-121` 已把它记为"已知残留缺口") |

**结论:**
- **B / C / D 三个钩子都可以直接调用同一个 `isIntentionalUnequip(newStack, removedStack)`**（参数顺序按 `(新, 旧)` 传即可,不需要新判据）。
- 但 mixin 里**需要一个新的"按栈分发"helper**（现有清理是 `ICurioItem#onUnequip` 的实例回调链 `BaseSignItem.clearSignData`，mixin 侧只有一个裸 `ItemStack`）。建议在 `CurioSlotUtil` 增一个静态方法（例如 `cleanStacksOnUnequip(ItemStack realStack)`,内部 `if (realStack.getItem() instanceof BaseSignItem s) s.clearSignData(player, realStack);` 之类）,使 mixin 只依赖一个入口。
- `CPacketDestroy` 的漏网**不需要新判据**:D 钩子是 handler 侧判据,天然把"写入 EMPTY"识别为真实移除 ⇒ 加 D 后该缺口自动消失（顺带把玩家级清理也补上,属**行为变更**,需用户裁定;现状是"与改动前行为一致"的有意选择）。

---

## 6. 覆盖度总表

列含义：**B** = `DynamicStackHandler#extractItem` 的 `simulate==false` 分支前注入；**C** = `CurioSlot#set` HEAD；**D** = `ItemStackHandler#setStackInSlot` HEAD（`instanceof DynamicStackHandler` 守卫）；**现有** = 现有 `onUnequip`（tick / loseStacks）+ `PlayerLifecycleHandler`。

| # | 卸下路径 | B | C | D | 现有路径 | 结论 |
|---|---|---|---|---|---|---|
| 1 | GUI 左键取出（PICKUP，光标为空） | ✅ 生效 | ⚠️ 会调用但槽已空 ⇒ 无效 | ⚠️ 同 C | ❌（副本） | **B 覆盖** |
| 2 | GUI 光标换装（光标上有不同饰品） | ❌ 不调用 | ✅ 生效（真实旧栈=光标对象） | ✅ 生效 | ❌ | **C/D 覆盖** |
| 3 | 双击收集（PICKUP_ALL） | ✅ | ⚠️ 空 | ⚠️ 空 | ❌ | **B 覆盖** |
| 4 | 丢弃（THROW，Q/F 键） | ✅ | ⚠️ 空 | ⚠️ 空 | ❌ | **B 覆盖** |
| 5 | 拖出窗口丢光标物品（slotId=-999） | 不适用（光标栈早已过 B/C） | 不适用 | 不适用 | — | 由 #1/#2 间接覆盖 |
| 6 | **快捷移动（shift 点击）出饰品栏** | ❌ 不调用 | ⚠️ 调用但旧栈已被 `moveItemStackTo` 就地清空、副本已进背包 | ⚠️ 同 C | ❌ | **⚠️ 无覆盖**（必须新增第三处注入） |
| 7 | 换装（同槽替换，Curios 自身重载） | 视路径 | ✅/⚠️ | ✅/⚠️ | ❌（有意不清理） | 由 #2/#4 覆盖 |
| 8 | 数字键 / 副手 F（SWAP） | ❌ 不调用 | ✅ | ✅ | ❌ | **C/D 覆盖** |
| 9 | 死亡掉落（`CuriosEventHandler#handleDrops`，偏移 `279 setStackInSlot(i, EMPTY)`，**无 extractItem**） | ❌ | ❌ | ✅（`getDroppedItem` 的 `ItemEntity` 持有**同一对象**，偏移 `258-266` 先建实体后清槽） | ✅ `PlayerLifecycleHandler`（LOWEST） | **现有覆盖**（D 顺带覆盖） |
| 10 | `CurioStacksHandler#loseStacks`（偏移 `329-335 setStackInSlot(i, EMPTY)`；offset `314-326` 先回调 `onUnequip(ctx, EMPTY)`） | ❌ | ❌ | ✅ | ✅（第 3 参 = 真实栈 ⇒ 现有清理**落点正确**） | **现有覆盖** |
| 11 | **`CPacketDestroy`**（N `CuriosServerPayloadHandler#lambda$handleDestroyPacket$9` 偏移 `314-320` = `setStackInSlot(i, EMPTY)`；F `CPacketDestroy#lambda$handle$3` 偏移 **`303`** = 同） | ❌ | ❌ | ✅ | ❌（`isIntentionalUnequip` 判"同物品"⇒跳过，见 5.3） | **⚠️ 无覆盖**（D 可补） |
| 12 | BREAK（耐久耗尽 / `curioBreak`） | ❌ | ❌ | ❌ | ✅ 玩家级（tick）；物品组件**不需要** | **无需覆盖**（见第 4 节） |
| 13 | `/curios set` 等命令（`CuriosCommand` 有 `setStackInSlot`） | ❌ | ❌ | ✅ | ❌ | D 顺带覆盖 |
| 14 | 客户端同步写入（N `CuriosClientPackets`、F `SPacketSyncStack` → `menu.setItem` → `Slot.set`） | ❌ | ⚠️ 会在客户端触发 | ⚠️ 会在客户端触发 | ❌ | **必须在钩子里加客户端守卫**（见第 7 节） |
| 15 | 实体（非玩家）Curios（N `CurioInventory`/`CurioItemHandler`、F `CurioInventoryCapability$CurioInventoryWrapper`） | 可达 | ❌ | ✅ | — | 与本模组无关（立牌只在玩家身上），但 D 会命中 ⇒ 需要 `instanceof Player` 守卫 |
| 16 | 创造模式 CLONE（N 被 `CuriosContainer#clicked` 拦截为"复制到光标"） | ❌ | ❌ | ❌ | ❌ | 非卸下;但**复制出的副本携带累计组件**（另一个问题，见「其它」） |

### 「无覆盖」路径（显式列出）

1. **快捷移动（shift 点击）把饰品从饰品栏移出** —— B 不调用;C/D 调用时旧栈已被 `moveItemStackTo` 就地清空,且真正进入背包的是更早 `split/copy` 出来的副本。**必须加第三处注入**（见「最小改动清单」项 3）。
2. **`CPacketDestroy`**（N）/ F 等价路径 —— B/C 都不覆盖;D 覆盖。若不加 D,则需按 5.3 的结论**改 `isIntentionalUnequip` 或为该调用点单开判据**（现状属"有意保留的已知缺口"，`CurioSlotUtil.java:120-121`）。

### 「其它」需要留意（非卸下，但与本机制交互）

- **CLONE 复制**（N 经 `ICurioSlotExtension#getCloneStack`）：复制出的栈保留累计组件 ⇒ 创造模式下可以"刷出"一个高累计值的同名饰品。这不是"卸下丢清理"问题,而是"累计值可被复制"问题,是否要处理由用户裁定。本仓物品**未**实现 `getCloneStack`（已 grep 全仓：无匹配）。
- 本仓物品**未**覆写 `ItemStack#overrideStackedOnOther` / `overrideOtherStackedOnMe`（已 grep 全仓：无匹配）⇒ 不存在"物品自己接管点击、绕过 `doClick`"的路径。

---

## 7. 风险

### 7.1 `simulate` 语义与其它模组

- **B 不改语义**:注入用 `@Inject`（不是 `@Redirect`/`@Overwrite`）,不改返回值、不拦截 `invokespecial`、不触碰 `simulate==true` 分支的任何指令。`simulate==true` 时钩子直接 `return`（形参已知,见 2.3）。**方案 A 的"每次单击都触发"问题不会复现。**
- **B 的侵入面最小**:目标类/方法都是 Curios 自己的 `DynamicStackHandler.extractItem`,不影响任何第三方。
- **D 有全局侵入面**:`net.neoforged.neoforge.items.ItemStackHandler` / `net.minecraftforge.items.ItemStackHandler` 是所有模组通用的基类。**必须** `if (!(this instanceof DynamicStackHandler)) return;` 作为第一条语句,否则会给其它模组的槽位写入逻辑增加开销（并且一旦判据写错就会误清数据）。加上守卫后对第三方零影响。
- **C 的侵入面小**（只 Curios 的 `CurioSlot`）,但面临 1.3-7 的 F 侧注解名映射不确定性。
- 建议所有钩子**只做 item-component 读取/写入 + 早退**,不抛异常、不打日志（`extractItem`/`setStackInSlot` 在容器点击热路径上,每 tick/每次点击都会调用）。

### 7.2 在 `simulate==false` 分支里"写组件"是否安全（并发 / 线程）

- **执行线程**:容器点击（`ServerboundContainerClickPacket` → `ServerPlayer#doClick` → `AbstractContainerMenu#clicked`/`doClick`）、客户端 `ClientPacketListener#handleContainerSetSlot` → `menu.setItem`、Curios 的 `IPayloadContext#enqueueWork`（N `handleDestroyPacket` 偏移 `0-12` 即 `enqueueWork`；F `NetworkEvent$Context#enqueueWork`）**全部投递回各自的主线程**（服务端 server thread / 客户端 render thread）。⇒ **没有并发写入 `ItemStack` 组件的场景**（`PatchedDataComponentMap` 非线程安全,但此处单线程）。
- 唯一需要避免的是"在 `extractItem` 里做重活拖慢点击"（例如全背包扫描）——本方案不做扫描,只写 1~2 个组件。

### 7.3 客户端与服务端都会走这两个方法吗？会不会写坏客户端数据？

**会走,而且 C/D 在客户端是必然路径。** 证据:
- 链路:`ClientPacketListener#handleContainerSetSlot`（N `:1305` / F `:1193`）→（`containerId` 非 `-1/-2/0` 时）`player.containerMenu.setItem(i, stateId, itemstack)`（**N `:1333` / F `:1223`**）→ `AbstractContainerMenu#setItem`（N **`:604-607`**）`{ this.getSlot(slotId).set(stack); }` —— 这是 `ClientboundContainerSetSlotPacket` 的客户端落点;N 的 `CuriosContainer#setItem` 还**覆写**了它并在范围内转发 `super.setItem` ⇒ 每次槽位同步都会在客户端走到 `CurioSlot#set` → `setStackInSlot` ⇒ **C/D 必然在客户端触发**。
- N `CuriosClientPackets#handle(SPacketQuickMove)`（偏移 `27-42`）在**客户端**调用 `CuriosContainer#quickMoveStack` ⇒ 若把 QUICK_MOVE 补丁挂在 `moveItemStackTo`/`quickMoveStack` 上,**客户端同样会触发**。
- `DynamicStackHandler#extractItem` 在客户端**基本不会**被调用（客户端不执行 `doClick`,槽内容由 `set`/`setStackInSlot` 写入）,但不能假定绝对为零（其它客户端模组可能遍历 `IItemHandler` 并 `extractItem(...,false)`）。

**⇒ 硬性要求:每个钩子必须自己判服务端。** 判据来源:
- B:`DynamicStackHandler.ctxBuilder`（`protected`）→ `SlotContext#entity()` → `entity.level().isClientSide()`。若不引入 accessor,也可在本模组侧用一个 `@Shadow` 声明（但 `ctxBuilder` 属 `DynamicStackHandler`,B 的 mixin 恰好以它为 target,**可以直接 `@Shadow`**）。
- C:`CurioSlot` 有 `private final Player player` 字段（javap 已核）⇒ `@Shadow` 后判 `player.level().isClientSide()`;`getSlotContext().entity()` 亦可。
- D:必须借 `@Accessor` 接口 mixin 读 `DynamicStackHandler.ctxBuilder`（`@Mixin(DynamicStackHandler.class) interface ... { @Accessor("ctxBuilder") Function<Integer, SlotContext> astral$ctxBuilder(); }`）,或退一步用 `((DynamicStackHandler)this)` 的公开方法（`DynamicStackHandler` 无公开取 entity 的方法 ⇒ 只能 accessor）。

**若不加守卫会怎样**:客户端也会把组件清零 ⇒ 客户端侧 tooltip/图标立刻变干净,与服务器端结果一致,**通常无害**;但① 客户端与服务器对同一"累计值"的权威副本不同（服务器另有权威数据,如 `PlayerLifecycleHandler`/附件）,客户端清零不影响服务器判据;② 更危险的是**客户端可能先于服务器清掉,导致服务器随后读到的同步值已被覆盖**（取决于谁先写）——这条**无法在不实测的情况下判定**。**⇒ 保守做法:与服务端既有清理保持一致,一律 `isClientSide()` 早退。**

### 7.4 F 侧 `CuriosContainerV2` 与 N 侧 `CuriosContainer` 的差异

| 差异点 | N（1.21.1） | F（1.20.1） | 影响 |
|---|---|---|---|
| 容器类 | 只有 `CuriosContainer extends RecipeBookMenu` | `CuriosContainer extends InventoryMenu` **+** `CuriosContainerV2 extends CuriosContainer` | 若在 `quickMoveStack` 上挂钩,F 需覆盖两个类（V2 **覆写了** `m_7648_`,不会走 super） |
| 实际用哪个容器 | 唯一 | `CuriosContainerProvider#m_7208_` 按 `CuriosConfig.SERVER.enableLegacyMenu` 选 legacy 或 V2 | 两版行为可能不同,但**QUICK_MOVE 的缺口与尾段 `Slot.set(EMPTY)` 两版一致**（已逐段比对字节码） |
| `clicked` 覆写 | **有**（拦 CLONE + `ICurioSlotExtension#getCloneStack`） | **无**（`CuriosContainer`/`CuriosContainerV2` 均无 `m_4426_`） | N 的 CLONE 拦截是 N 独有的**上游不对称**;若依赖 `CurioSlot#set` 覆盖 CLONE,两版结论不同（但 CLONE 不改槽,与卸下清理无关） |
| 网络 | `CustomPacketPayload` + NeoForge `PacketDistributor` | `SimpleChannel` + FML `NetworkEvent$Context` | mixin 不需触碰 |
| 破坏包 | `CuriosClientPackets` | `SPacketBreak` | 同上 |
| `DynamicStackHandler` / `CurioSlot` / `ItemStackHandler` | **逐字节同构**（除事件类型与 SRG/Mojmap 显示名） | 同 | ⇒ B 与 D 的注入点在两版**写法可完全一致**（D 甚至完全不需要区分版本）;C 需注意 F 的方法名重映射 |

**⇒ 建议:优先采用 D（两版写法完全一致、无重映射问题）,B 作为"真实提取"路径的补充;放弃 C 或把 C 降级为 D 已覆盖后的可选冗余。**

---

## 可落地的最小改动清单（**只列方案,不改任何代码**）

### 1) 新增 mixin 配置项? —— **不需要**

- N:把新类加进现有 `neoforge-1.21.1\src\main\resources\astral_dice.mixins.json` 的 `mixins` 数组即可（`[[mixins]]` 已声明该文件）。若希望"目标方法消失时只静默失效",可仿 `astral_dice.neoforge_fixes.mixins.json` 再开一个 `required:false` 配置并在 `neoforge.mods.toml` 多加一个 `[[mixins]]`——**可选,非必需**。
- F:把新类加进 `forge-1.20.1\src\main\resources\astral_dice.mixins.json` 的 `mixins` 数组。**不要**指望 `mods.toml` 的 `[[mixins]]`（FML 47 忽略它）;生产通道是 `build.gradle:243-246` 的 `MixinConfigs` 清单属性,已覆盖 `jar` + `reobfJar`。**不需要**改 `build.gradle`（只要不新增第二个配置文件）。
- `compileOnly 'org.spongepowered:mixin:0.8.5'` 两侧都已存在。Curios 已是 `implementation`(N) / `modImplementation`(F) 依赖,编译期可见。**无需新增任何依赖。**
- Mixin Booster 已是 F 侧硬前置（`mods.toml` + `build.gradle:222`）,**无需改动**。

### 2) 要 mixin 的类/方法/注入点

| 编号 | 目标类 | 方法 | 注入点 | 说明 |
|---|---|---|---|---|
| **B** | `top.theillusivec4.curios.common.inventory.DynamicStackHandler` | `extractItem(int,int,boolean)` | `@At(value="INVOKE", opcode=INVOKESPECIAL, target="Lnet/neoforged/neoforge/items/ItemStackHandler;extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;")`（N）/ `...Lnet/minecraftforge/items/ItemStackHandler;...`（F），并在回调里 `if (simulate) return;` | 覆盖 #1/#3/#4（真实提取）;此处槽内仍是真实栈 |
| **D**（推荐,替代 C） | `net.neoforged.neoforge.items.ItemStackHandler`（N）/ `net.minecraftforge.items.ItemStackHandler`（F） | `setStackInSlot(int, ItemStack)` | `@At("HEAD")` + 首行 `if (!(this instanceof DynamicStackHandler dh)) return;` | 覆盖 #2/#7/#8/#9/#10/#11/#13/#15 以及客户端同步;方法名**不需要**重映射 |
| **C**（可选,若坚持"面向 `CurioSlot`"） | `top.theillusivec4.curios.common.inventory.CurioSlot` | N `set` / F `set`（运行时需映射到 `m_5852_`） | `@At("HEAD")` | D 已包含其全部效果;保留 C 只增加映射风险,不增加覆盖 |
| **E**（必需,补 QUICK_MOVE） | 见下"项 3" | | | |
| — | `DynamicStackHandler`（accessor mixin） | 字段 `ctxBuilder` | `@Accessor("ctxBuilder")` | 供 B/D 判"是否有实体 / 是否客户端"（`protected` 字段） |

### 3) QUICK_MOVE 缺口（「无覆盖」路径）的补法（二选一）

- **E-1（推荐,两版各一处）**:mixin `net.minecraft.world.inventory.AbstractContainerMenu#moveItemStackTo`（N）/ `#m_38903_`（F，纯 MC 类,映射由 Mixin Booster 通用处理）`@At("HEAD")`:
  1. `if (!(this instanceof CuriosContainer)) return;`（N）/ `instanceof ICuriosMenu`（F，`CuriosContainerV2` 与 legacy 都实现它）；
  2. 在 `this.slots` 里按**引用同一性**找 `slot.getItem() == stack` 的 `CurioSlot`；
  3. 用与 `moveItemStackTo` 相同的判据做一次"确实会移动"的干跑（`getItem().isEmpty() && mayPlace(stack)`，或 `stack.isStackable() && isSameItemSameComponents(...) && count < maxStack`）以避免"背包满 ⇒ 其实没移动"时的误清；
  4. 命中则清理该 CurioSlot 的真实旧栈。
  - 优点:F 侧**只需一处**（不管 `enableLegacyMenu` 打开哪个容器）。
- **E-2（更贴 Curios 但 F 要两处）**:分别在 N `CuriosContainer#quickMoveStack`、F `CuriosContainer#quickMoveStack` + `CuriosContainerV2#quickMoveStack` 的 `@At("HEAD")` 做同样的事（F 因为 V2 **覆写**了该方法是**两个**独立目标）。
- 若用户接受"快捷移动时物品组件不清零"的残余风险,也可**不加 E**,但必须在文档/AGENTS 中显式记为已知缺口（第 6 节 #6）。

### 4) 判据 / helper

- **判据可直接复用** `CurioSlotUtil.isIntentionalUnequip(newStack, removedStack)`（N `:122-130` / F `:123-131`），B/C/D 三处都按 `(新栈, 旧栈)` 传参（见 5.3 对照表）。**不需要新判据**。
- **需要一个新 helper**（mixin 侧只有裸 `ItemStack`,没有 `ICurioItem` 回调）:建议在 `CurioSlotUtil` 增加
  `public static void cleanStacksOnUnequip(LivingEntity entity, ItemStack removedStack)`
  —— 内部先判 `entity instanceof Player && !entity.level().isClientSide()` 与 `removedStack` 非空,再按 `removedStack.getItem()` 分发到 `BaseSignItem.clearSignData(player, removedStack)`（立牌）与 `BaseChipItem` 的卸下清理（筹码）。**两版都加。**
- **写法建议**:mixin 内**不要**直接写业务逻辑,只做 `guard + 调用 helper`,便于后续把"清理对象"从"第 3 参副本"统一切换为"真实栈"。
- `PlayerLifecycleHandler`（LOWEST 死亡路径）与既有 `onUnequip` 路径**保留不动**（它们是 #9/#10 的覆盖来源，且 `onEquip` 侧语义不受影响）。

### 5) 两版本差异点（实施时逐条对齐）

1. **D 的目标类名不同**:N `net.neoforged.neoforge.items.ItemStackHandler` / F `net.minecraftforge.items.ItemStackHandler`;**方法名 `setStackInSlot` 两版一致且都不需要 SRG 重映射。**
2. **B 的 `@At` target 类名不同**（同上）;**方法名 `extractItem` 一致、不需重映射。**
3. **C 只在 F 侧有名字问题**（`set` → `m_5852_`）;若采用 D 则**该问题不存在**。
4. **E 的 F 侧候选类有两个**（`CuriosContainer` / `CuriosContainerV2`），N 只有一个;若走 E-1（`moveItemStackTo`）则**两版都只需一处**。
5. **客户端守卫的取实体方式不同**:N/F 都是 `SlotContext#entity()`;`CurioSlot.player` 两版都有;`DynamicStackHandler.ctxBuilder` 两版都有（accessor 写法一致）。
6. **F 侧不要新增 `mods.toml` 注释里含裸 `$标识符` 的内容**（Groovy 模板展开坑，AGENTS.md 已记录);本次改动若只碰 `*.mixins.json` 与 Java 源,不涉及该坑。
7. 完成后按仓库规范跑 `:neoforge-1.21.1:build` 与 `:forge-1.20.1:build` 验证（尤其 F 侧要确认 mixin 真的生效,而不是静默失效——这是 Mixin Booster 场景的已知陷阱）。

### 6) 需要用户裁定的一项

- 加 D 后,**`CPacketDestroy` 路径的玩家级清理也会被补上**（现状有意跳过,`CurioSlotUtil.java:120-121`）。这属**行为变更**,需用户决定是"顺带修复"还是"保持现状（D 里对 destroy 路径跳过）"。

---

## 明确的「无法判定」项

1. **F 侧 `@Mixin(CurioSlot.class)` + `method="set"` 能否被 Mixin Booster 正确映射到生产 jar 的 `m_5852_`** —— 已证实 Mixin Booster 提供 `IRemapper`(`org.sinytra.mixinbooster.MixinModlauncherRemapper`,基于 ModLauncher 的 `INameMappingService`),也证实注解字符串在生产 jar 中**未被 reobf**;但 `mapMethodName(owner,name,desc)` 在 `owner` 是 **mod 类**(`top.theillusivec4.curios...`,不在任何 tsrg 表内)时是否仍能沿类层次找到 `net/minecraft/world/inventory/Slot#set` 的映射 —— **本报告无法判定**（需一次真实启动验证;本任务禁止启动游戏）。**规避方式:改用 D。**
2. **`@Mixin(DynamicStackHandler.class)` + `@Inject(method="setStackInSlot")` 是否能解析到继承自 `ItemStackHandler` 的方法** —— Mixin 对"目标类未声明、仅继承"的方法的解析行为**未在本仓取证**（无先例）。**规避方式:直接 mixin 声明类 + `instanceof` 守卫。**
3. **客户端清理的副作用**:若不加 `isClientSide()` 守卫,客户端先清零是否会影响服务器随后读到的值 —— 取决于同步时序,**无法判定**（未实测）。建议一律加守卫。
4. **`CurioSlot.set` 在"光标换装"分支里的 `getItem()` 是否可能已被 `setCarried(itemstack9)` 影响** —— `setCarried` 只改菜单的光标字段（`AbstractContainerMenu.carried`）,不触碰槽内 handler,故判断为"不受影响";但该判断基于源码阅读而非运行验证,标为**低置信度**（若采用 D 则不依赖该分支细节,风险消失）。
5. **`CuriosContainerProvider` 在 F 侧默认选 legacy 还是 V2**（`CuriosConfig.SERVER.enableLegacyMenu` 的默认值）—— 本次未展开读 `CuriosConfig` 的默认值,**无法判定**;采用 E-1（`moveItemStackTo`）则不需要该信息。
