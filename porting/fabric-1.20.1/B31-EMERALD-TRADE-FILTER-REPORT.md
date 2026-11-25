# B31 绿宝石骰子「星币代替绿宝石」交易过滤器 — fabric-1.20.1 移植报告

- 分支：`multi-main`（工作树 `D:/MCProject/astral_dice_multiloader`）
- 基准：以 **neoforge-1.21.1 为主基准**、**forge-1.20.1 为次基准**（同 MC 1.20.1，机制最接近，作直接蓝本）
- 范围：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/{trade,mixin/trade}/**`（未动 lang / patchouli / build.gradle / gradle.properties / accesswidener / mixins.json）
- 结论：最新主线**确有**这段逻辑（两条基准线都有），fabric 侧**确缺**（移植前 4 个符号全仓 0 命中）。已按次基准 1:1 移植落码并通过编译与产物校验。

---

## 一、取证 A：两基准线的符号落位（文件:行 / 签名 / 描述符 / 调用点）

### 1. neoforge-1.21.1（主基准）

| 符号 | 位置 | 签名 |
|---|---|---|
| `isEmeraldToStarCoin` | `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/trade/EmeraldDiceTrade.java:76` | `public static boolean isEmeraldToStarCoin(MerchantOffer offer)` |
| `filteredFor(Player, MerchantOffers)` | `.../trade/EmeraldDiceTrade.java:93` | `public static MerchantOffers filteredFor(Player player, MerchantOffers offers)` |
| `filteredFor(Merchant)` | `.../trade/EmeraldDiceTrade.java:111` | `public static MerchantOffers filteredFor(Merchant merchant)` |
| `rawCostA` | **不存在**（1.21.1 直接读 `offer.getBaseCostA()`，该取值器未被本包 mixin 改写） | — |
| `MerchantOfferRawAccess` | **不存在**（仅 1.20.1 需要，见下） | — |

调用点（3 处，均按玩家视角注入）：

| 注入点 | 文件:行 | 注解 |
|---|---|---|
| 发报价给客户端 | `neoforge-1.21.1/.../mixin/trade/ServerPlayerMixin.java:33` | `@ModifyVariable(method = "sendMerchantOffers", at = @At("HEAD"), argsOnly = true, index = 2)` |
| 菜单报价索引 | `neoforge-1.21.1/.../mixin/trade/MerchantMenuMixin.java:37` | `@Inject(method = "getOffers", at = @At("HEAD"), cancellable = true)` |
| 结果槽报价匹配 | `neoforge-1.21.1/.../mixin/trade/MerchantContainerMixin.java:34` | `@Redirect(method = "updateSellItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/Merchant;getOffers()Lnet/minecraft/world/item/trading/MerchantOffers;"))` |

### 2. forge-1.20.1（次基准，本移植的直接蓝本）

| 符号 | 位置 | 签名 / 描述符 |
|---|---|---|
| `isEmeraldToStarCoin` | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/EmeraldDiceTrade.java:116` | `public static boolean isEmeraldToStarCoin(MerchantOffer offer)` |
| `rawCostA` | `.../trade/EmeraldDiceTrade.java:131` | `public static ItemStack rawCostA(MerchantOffer offer)` |
| `filteredFor(Player, MerchantOffers)` | `.../trade/EmeraldDiceTrade.java:149` | `public static MerchantOffers filteredFor(Player player, MerchantOffers offers)` |
| `filteredFor(Merchant)` | `.../trade/EmeraldDiceTrade.java:167` | `public static MerchantOffers filteredFor(Merchant merchant)` |
| `MerchantOfferRawAccess` | `forge-1.20.1/.../trade/MerchantOfferRawAccess.java:20` | `public interface MerchantOfferRawAccess { ItemStack astralDice$rawCostA(); }`（duck interface，**不进 mixins.json**） |
| 同上实现体 | `forge-1.20.1/.../mixin/trade/MerchantOfferMixin.java:92-95` | `@Override public ItemStack astralDice$rawCostA() { return this.baseCostA; }`（`@Mixin(MerchantOffer.class) ... implements MerchantOfferRawAccess`，见 :21） |

调用点（3 处，与 1.21.1 一一对应，但换费用与过滤合并进同一注入体）：

| 注入点 | 文件:行 | 注解 / 描述符 |
|---|---|---|
| 发报价给客户端 | `forge-1.20.1/.../mixin/trade/ServerPlayerMixin.java:34` | `@Inject(method = "sendMerchantOffers", at = @At("HEAD"), cancellable = true)`，方法签名 `sendMerchantOffers(ILnet/minecraft/world/item/trading/MerchantOffers;IIZZ)V`；体内 :39 过滤、:41 换费用、:44 `if (payload == offers) return;` |
| 菜单报价索引 | `forge-1.20.1/.../mixin/trade/MerchantMenuMixin.java:39` | `@Inject(method = "getOffers", at = @At("HEAD"), cancellable = true)`，`CallbackInfoReturnable<MerchantOffers>`；返回 `MerchantMenu.getOffers()Lnet/minecraft/world/item/trading/MerchantOffers;` |
| 结果槽报价匹配 | `forge-1.20.1/.../mixin/trade/MerchantContainerMixin.java:46` | `@Redirect(method = "updateSellItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/Merchant;getOffers()Lnet/minecraft/world/item/trading/MerchantOffers;"))`，签名 `(Lnet/minecraft/world/item/trading/Merchant;)Lnet/minecraft/world/item/trading/MerchantOffers;` |

### 3. fabric-1.20.1 移植前（缺口）

| 符号 | 移植前命中数 |
|---|---|
| `isEmeraldToStarCoin` | 0（全仓，仅 `porting/` 旧文档记录「待移植」） |
| `rawCostA` | 0 |
| `MerchantOfferRawAccess` | 0 |
| `filteredFor` | 0 |

即 fabric 侧当时**没有任何**报价条目过滤；已有的 `EmeraldDiceTrade` 只做「绿宝石费用 → 星币费用」的等值替换，`ServerPlayerMixin` 只换费用不过滤。

---

## 二、取证 B：这段逻辑的目的与作用面

1. **介入时机**：不做事件/全局 tick，只在「报价列表被某玩家看到/被用于索引」的三个瞬时点上介入 —— 发报价包（`ServerPlayer#sendMerchantOffers`）、取菜单报价视图（`MerchantMenu#getOffers`）、结果槽匹配（`MerchantContainer#updateSellItem` 内读 `merchant.getOffers()`）。
2. **过滤对象**：`MerchantOffers`（村民级共享的报价列表）里的**条目**，判据是「该条目的原始 costA 是绿宝石且 result 是星币」。只动 `costA`（`buy`），不碰 `costB`（`buyB`）。
3. **对绿宝石骰子佩戴者的实际效果**：其绿宝石费用被 1:1 换成星币后，若那条「绿宝石 → 星币」兑换报价仍留着，就退化成「用星币买星币」的空循环（凭空刷币）。因此该条目必须从**这个玩家看到的列表**里剔除；村民本体报价表不动（其他玩家照旧）。
4. **与 `trade/EmeraldDiceTrade.java` 的关系**：同一工具类的两个互补决策 —— `transform*` 负责「其余条目的绿宝石费用 → 星币」，`isEmeraldToStarCoin` / `filteredFor` 负责「把兑换条目本身剔除」。二者在 `ServerPlayerMixin` 里**同一注入体内按顺序**执行（先过滤、后换费用），语义等价于 1.21.1 的「`@ModifyVariable` 过滤 + 另一处换费用」。
5. **为什么 1.20.1 必须多一个 `rawCostA`**：1.21.1 没有 `ItemCost` 改价需求，`getBaseCostA()` 保持原值，判据可直读；1.20.1 无 `ItemCost`，费用替换只能覆写 `getBaseCostA()` 本身 —— 交换上下文激活时它会返回**星币**，判据就再也认不出兑换条目。因此必须绕开取值器读原始字段。

---

## 三、取证 C：fabric 侧等价物与方案取舍

### 3.1 注入点等价性（1.20.1 Fabric 无 `VillagerTradesEvent`）

本逻辑本来就不是 `VillagerTradesEvent`（那是**追加交易**用的事件），而是「按玩家改写已有报价视图」，原版类即可承载，故 **Mixin 直挂原版方法是正解**，无需平台垫片：

| 需求 | 等价注入点（1.20.1 原版字节码） | 是否已有 mixin 类 |
|---|---|---|
| 发报价过滤 | `ServerPlayer#sendMerchantOffers(ILnet/minecraft/world/item/trading/MerchantOffers;IIZZ)V` | 已有 `trade.ServerPlayerMixin` |
| 菜单视图过滤 | `MerchantMenu#getOffers()Lnet/minecraft/world/item/trading/MerchantOffers;` | 已有 `trade.MerchantMenuMixin` |
| 结果槽匹配过滤 | `MerchantContainer#updateSellItem()V` 内 `Merchant.getOffers()` | 已有 `trade.MerchantContainerMixin` |

### 3.2 `MerchantOfferRawAccess`：为什么**不用** accesswidener

候选方案对比：

| 方案 | 结论 | 理由 |
|---|---|---|
| A. accesswidener 开放 `MerchantOffer.baseCostA` 并直接字段访问 | **否决** | 需要把 `net.minecraft.world.item.trading.MerchantOffer` 标 accessible；而本仓 `astral_dice.accesswidener` 的既有粒度是自有类，为一个只读字段扩大原版类可访问面，属于扩大暴露面且与 forge 线形态不一致 |
| B. `@Mixin` accessor（`@Accessor("baseCostA")`） | 可行但多一个类 | 需新增一个 accessor mixin 并登记 mixins.json |
| C. duck interface（**选用**） | **采用** | 与 `forge-1.20.1` **逐字同构**：接口放 `trade/`（非 mixin 包，故不进 mixins.json），由已存在的 `MerchantOfferMixin` 实现（该 mixin 早已 `@Shadow @Final private ItemStack baseCostA`，直接返字段即可）。零新增 mixin 类、零 accesswidener 改动、两条 1.20.1 线可逐行对照 |

兜底：`rawCostA` 保留 `ItemStack.of(offer.createTag().getCompound("buy"))` 分支。**已用 javap 验证该兜底在 1.20.1 字节码上等价**：`MerchantOffer.createTag()` 的 `"buy"` 直接来自 `getfield baseCostA`（偏移 11），不经过取值器，因此即使 duck interface 未生效也不会静默降级成错误判据。

### 3.3 `require` 命中数取证（决定运行期是否抛 `InjectionError`）

对 fabric 线**实际使用的** 1.20.1 映射 jar（`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2.jar`）执行 `javap -p -c`：

- `MerchantContainer` **全类** `Merchant.getOffers()` 调用共 **1 处**：`updateSellItem` 偏移 90，`invokeinterface #129, 1 // InterfaceMethod net/minecraft/world/item/trading/Merchant.getOffers:()Lnet/minecraft/world/item/trading/MerchantOffers;`。⇒ 新 `@Redirect` 写 **`require = 1`**（写 2 会在目标类加载时抛 `InjectionError`）。
- `ServerPlayer` 中 `sendMerchantOffers` 方法数 **1** ⇒ 该 `@Inject` 沿用 `injectors.defaultRequire = 1`。
- `MerchantMenu` 中 `getOffers()` 方法数 **1**，方法体即 `aload_0; getfield trader; invokeinterface Merchant.getOffers; areturn` ⇒ `@Inject(HEAD, cancellable)` 为整体替换，语义安全。

其他已核对的签名/结构：

- `MerchantOffers extends java.util.ArrayList<MerchantOffer>`（故 `new MerchantOffers()` + `add(offer)` 成立，且「只包原实例、不深拷贝」不会脱离 `maxUses`/`demand` 共享状态）。
- `Merchant` 接口：`Player getTradingPlayer()`、`MerchantOffers getOffers()`，均在 `net.minecraft.world.item.trading` 包（1.21.1 亦同包，描述符无需改写）。

---

## 四、逐文件改动（fabric-1.20.1）

| 文件 | 性质 | 改动 |
|---|---|---|
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/MerchantOfferRawAccess.java` | **新增**（24 行） | 从 `forge-1.20.1` 同名文件**逐字节复制**（`public interface MerchantOfferRawAccess { ItemStack astralDice$rawCostA(); }`）。不进 `mixins.json`（非 `@Mixin` 类） |
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/EmeraldDiceTrade.java` | 修改（127 → 190 行） | 在 `transformOffers` 之后、`resendOffers` 之前插入 4 个方法：`isEmeraldToStarCoin`(:117)、`rawCostA`(:132)、`filteredFor(Player,MerchantOffers)`(:150)、`filteredFor(Merchant)`(:168)。**正文与 forge-1.20.1 逐字一致**，仅判据取值走 `rawCostA`（1.20.1 特有） |
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/trade/MerchantOfferMixin.java` | 修改（83 → 96 行） | ① 加 `import ...trade.MerchantOfferRawAccess;`；② 类声明 `implements MerchantOfferRawAccess`（:21）；③ 新增 `astralDice$rawCostA()`（:93）返回 `this.baseCostA` 字段。原有 getBaseCostA/getCostA/getCostB/satisfiedBy 四处注入与 `astralDice$isRequiredItem` **未改动** |
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/trade/ServerPlayerMixin.java` | 修改（33 → 49 行） | `@Inject(method="sendMerchantOffers", at=HEAD, cancellable=true)` 注入体内**先过滤后换费用**：`filteredFor(self, offers)` → `transformOffers(base)` → `payload` → `if (payload == offers) return;` → 自行发包并 `ci.cancel()`。修正了旧实现的语义缺口：**只过滤未换费用时必须仍然发出过滤后的列表**（旧代码在 `transformed == null` 时直接 return，会把未过滤列表发出去） |
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/trade/MerchantMenuMixin.java` | 修改（30 → 46 行） | 加 `MerchantOffers` / `CallbackInfoReturnable` 两个 import；新增 `@Inject(method="getOffers", at=HEAD, cancellable=true)`（:39）把菜单报价视图换成过滤视图（否则客户端列表少一条、`tryMoveItems` 索引错位 = 点 A 成交 B） |
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/trade/MerchantContainerMixin.java` | 修改（30 → 54 行） | 加 `MerchantOffers` / `Redirect` 两个 import；新增 `@Redirect(method="updateSellItem", require=1, at=INVOKE Merchant.getOffers)`（:47），让结果槽匹配也走过滤视图 |

**未改动**（并在核对后确认无需改动）：

- `fabric-1.20.1/src/main/resources/astral_dice.mixins.json`：5 个 trade mixin 早已登记（`trade.MerchantContainerMixin` / `trade.MerchantMenuMixin` / `trade.MerchantOfferMixin` / `trade.MerchantResultSlotMixin` / `trade.ServerPlayerMixin`）。本次**没有新增 mixin 类**，故无需登记。`git status` 里该文件显示为已修改，是因为**其他批次**在 19:21:21 追加了 `bridge.*` / `HealOrHarmMobEffectMixin` / `client.*` 等条目，与本任务无关，未触碰。
- `fabric-1.20.1/src/main/resources/astral_dice.accesswidener`：未改（见 3.2 方案 C）。
- `mixin/trade/MerchantResultSlotMixin.java`：与 forge 线相比少 `notifyCardObtained` 调用，**不属于本任务范围**，未动。

---

## 五、验证（原始输出）

### 5.1 编译

```
$ cmd.exe //c "gradlew.bat :fabric-1.20.1:classes --console=plain"
Reusing configuration cache.
> Task :fabric-1.20.1:processResources
> Task :fabric-1.20.1:compileJava
> Task :fabric-1.20.1:classes

BUILD SUCCESSFUL in 2s
2 actionable tasks: 2 executed
Configuration cache entry reused.
```

### 5.2 打包（jar + remapJar；新增注入后按任务要求复核产物）

```
$ cmd.exe //c "gradlew.bat :fabric-1.20.1:jar --console=plain"
> Task :fabric-1.20.1:jar
BUILD SUCCESSFUL in 720ms
3 actionable tasks: 1 executed, 2 up-to-date
Configuration cache entry reused.

$ cmd.exe //c "gradlew.bat :fabric-1.20.1:remapJar --console=plain"
> Task :fabric-1.20.1:remapJar
BUILD SUCCESSFUL in 4s
5 actionable tasks: 1 executed, 4 up-to-date
Configuration cache entry reused.
```

> Loom 结构注记：`:jar` 的产物是 `fabric-1.20.1/build/devlibs/…-dev.jar`（20:15:50 重建），最终发布产物 `fabric-1.20.1/build/libs/astral_dice-1.5.0-rc.7+fabric_1.20.1.jar` 由 `remapJar` 生成（20:16:42 重建）。两者的 `mixins.json` 的 `mixins` 数组均含全部 5 个 trade 类。

### 5.3 产物内容校验（`libs/` 最终 jar）

```
libs EDT filteredFor? True isEmeraldToStarCoin? True RawAccess ref? True
libs MOM rawCostA? True implements ref? True
libs has RawAccess.class? True
libs MC has astralDice$filterEmeraldExchange? True
libs MM has filterEmeraldExchange? True
libs SP references filteredFor? True
mixins trade: ['trade.MerchantContainerMixin', 'trade.MerchantMenuMixin', 'trade.MerchantOfferMixin', 'trade.MerchantResultSlotMixin', 'trade.ServerPlayerMixin']
```

### 5.4 重映射后注解（intermediary，`javap -v` 于最终 jar）

证明两件事：注入注解被 Loom 正确重映射（jar 内无 `refmap.json`，本项目走**就地重映射**），以及 `require = 1` 落到了产物里。

| 类 | 注解 | 重映射后 |
|---|---|---|
| `MerchantContainerMixin` | `@Redirect(method="updateSellItem", require=1, ...)` | `method=["method_7645"]`，`require=1`，`target="Lnet/minecraft/class_1915;method_8264()Lnet/minecraft/class_1916;"` |
| `MerchantMenuMixin` | `@Inject(method="getOffers", at=HEAD)` | `method=["method_17438"]`（`tryMoveItems` = `method_20215`） |
| `ServerPlayerMixin` | `@Inject(method="sendMerchantOffers", at=HEAD)` | `method=["method_17354"]` |
| `EmeraldDiceTrade` | 新方法 | `isEmeraldToStarCoin(class_1914)`、`rawCostA(class_1914)`、`filteredFor(class_1657, class_1916)`、`filteredFor(class_1915)` |

### 5.5 编码红线自检（python，逐文件）

```
PASS fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/EmeraldDiceTrade.java lines= 190 bareLF= 0 BOM= False
PASS fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/MerchantOfferRawAccess.java lines= 24 bareLF= 0 BOM= False
PASS fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/trade/MerchantOfferMixin.java lines= 96 bareLF= 0 BOM= False
PASS fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/trade/ServerPlayerMixin.java lines= 49 bareLF= 0 BOM= False
PASS fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/trade/MerchantMenuMixin.java lines= 46 bareLF= 0 BOM= False
PASS fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/trade/MerchantContainerMixin.java lines= 54 bareLF= 0 BOM= False
ALL PASS
```

判据：`CRLF 计数 == LF 计数`（`bareLF=0`）、文件头非 `EF BB BF`。全部改动均为**定点替换**，未使用 `json.dump` 或整份重写工具改写任何 json。

---

## 六、无法判定项 / 残余风险

1. **运行期未验证（本任务禁止启动实机）**：三条注入能否在真实村民交易界面命中、以及「点 A 成交 B」是否真的消失，均**未经运行验证**。替代证据是：描述符与 `@At` 全部取自本线实际使用的 1.20.1 映射 jar 的 `javap` 反汇编，且产物内注解已完成 intermediary 重映射（5.4）。建议后续在允许实机时，用「持绿宝石骰子 → 与村民交易 → 确认兑换条目消失且索引不错位」做一次性验收。
2. **`filteredFor` 的调用频度未实测**：`MerchantMenu#getOffers()` 与 `MerchantContainer#updateSellItem` 调用较频繁，而 `filteredFor` 每次命中都会新建 `MerchantOffers`。该行为与两条基准线**完全一致**（非本次引入的偏差），但 fabric 侧的实际 CPU 影响未测量。
3. **`require = 1` 的时效性**：数字针对 1.20.1 + 本仓当前映射 jar（`hash.2198`）。若上游 MC/映射变化导致 `updateSellItem` 内 `getOffers` 出现第二处调用，运行期会抛 `InjectionError` —— 这是刻意选择的失败方式（宁可响亮失败，也不要静默只改一半）。
4. **duck interface 生效性未运行验证**：若 `MerchantOfferMixin` 因故未被应用（例如被门禁关闭），`rawCostA` 会走 `createTag()` 兜底；该兜底**已在字节码层面证明等价**（`"buy"` 读 `baseCostA` 字段），但「静默走兜底」这一事实本身只会在运行期可见。
5. **客户端观感未验**：本次只保证服务端下发/索引的列表一致；客户端渲染（报价条目数、星币贴图与数量文字）未观测。

---

## 七、过程备注（同宿主环境限制，供后续同批任务参考）

- 本沙箱的 shell（bash `/ python`）进程处于**低完整性级别（Low IL）**：`icacls` 可见新建对象带 `Mandatory Label\Low Mandatory Level:(I)(NW)`。后果是**能创建新文件、但覆盖既有文件会被 `PermissionError` 拒绝**（非只读属性问题，`os.access(W_OK)` 也返回 True）。
- 因此所有涉及**既有文件**的落盘改用宿主的 `edit` 工具（字节级定点替换，已实测保留 CRLF：`bareLF` 恒为 0），新增文件才用 shell。**未使用**任何整份重写。
- `/tmp`、`$LOCALAPPDATA/Temp`、仓外目录在本沙箱一律不可写，故字节码取证统一用 `javap -cp <jar> <fqcn>` **直读 jar**，不做解包。

