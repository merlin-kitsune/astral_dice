# B6 — Jade 兼容层移植报告（forge-1.20.1 蓝本 → fabric-1.20.1）
- 蓝本：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/compat/jade/`（3 类；同为 MC 1.20.1 + Jade 11.13.3）
- 落点：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/compat/jade/`（新建 3 类）+ `fabric-1.20.1/src/main/resources/fabric.mod.json`（**仅** entrypoints 键）
- 结论：3 类全部落地；Fabric 侧改走入口点注册；javac REAL 分桶 0；Jade 仍为软依赖。
## 1. 交付清单
| 文件 | 行 | 变更 |
|---|---|---|
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/compat/jade/AstralDiceJadePlugin.java` | 51 | 新建 |
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/compat/jade/EventDrawerCostProvider.java` | 40 | 新建 |
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/compat/jade/ShopCooldownClientProvider.java` | 102 | 新建 |
| `fabric-1.20.1/src/main/resources/fabric.mod.json` | 46 | entrypoints 增 `"jade"` 一键（其余键未动；`depends`/`recommends` 未加 jade） |
| `fabric-1.20.1/src/main/resources/assets/astral_dice/lang/{zh_cn,en_us,ja_jp}.json` | — | 各追加 3 个 `jade.astral_dice.*` 键（值逐字取自 forge 同名键） |

> lang 三项超出「落点」字面清单：不补则悬浮框直接显示裸键 `jade.astral_dice.restock_countdown`。属纯新增键，三语键集仍一致。
## 2. 三类逐项对照
| 类 | forge 蓝本 | fabric 实现 | 实质差异 |
|---|---|---|---|
| `AstralDiceJadePlugin` | `@WailaPlugin(MODID)` + `registerClient` 注册 4 条 `registerBlockComponent` | 同 | 仅类注释改写（发现机制换成 entrypoints）；注解**保留**，在 Fabric 侧降级为 modid 自检，非发现机制 |
| `EventDrawerCostProvider` | `enum implements IBlockComponentProvider`；`UID = new ResourceLocation(MODID, "event_drawer_cost")`；`appendTooltip` → `Component.translatable("jade.astral_dice.event_drawer_cost", EventDrawBlock.priceStarCoins())` | 同构 | 无（Fabric 侧 `block/EventDrawBlock.java:65` 已有 `public static int priceStarCoins()`） |
| `ShopCooldownClientProvider` | `UID = new ResourceLocation("astral_dice", "shop_cooldown")`；`remainingTicks` 三分支（Card/Chip/Event）；`langKey`；`formatCountdown` 秒向上取整 `(max(0,t)+19)/20` | 同构 | **唯一差异**（已闭环，2026-11-01 B0-TAIL 批次）：当时 `EventDrawBlock` 分支恒 `return 0L`（本线无 `ModAttachments.getEventDrawerCooldownUntil`，见 §6）；现已在 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/compat/jade/ShopCooldownClientProvider.java:71` 接真为 `ModAttachments.getEventDrawerCooldownUntil(player) - now`，附件键与 `.sync()` 同步见 `component/ModAttachments.java:123-135`/`:1394-1395` |

4 条注册保持蓝本顺序：冷却行 ×3（CardShop/ChipShop/EventDraw）、花费行 ×1（EventDraw）。
## 3. Fabric 插件注册机制差异（实物取证）
Jade fabric jar = `.gradle/loom-cache/remapped_mods/remapped/maven/modrinth/jade-c2d2b86c/ahavHbAT/jade-c2d2b86c-ahavHbAT.jar`（`version: 11.13.3+fabric`）。其 `fabric.mod.json` entrypoints 键集合 = `['main','client','jade','modmenu','jei_mod_plugin']`，`"jade"` = `[snownee.jade.addon.core.CorePlugin, snownee.jade.addon.vanilla.VanillaPlugin, snownee.jade.addon.universal.UniversalPlugin]`。

`javap -p -c snownee.jade.util.CommonProxy` → `loadComplete()`：`FabricLoader.getInstance().getEntrypointContainers("jade", IWailaPlugin.class).forEach(...)` 之后 `Jade.loadComplete()`；`lambda$loadComplete$5` 关键分支：
```
 37: checkcast     snownee/jade/api/IWailaPlugin
 48: invokevirtual Class.getDeclaredAnnotation:(Ljava/lang/Class;)   // @WailaPlugin
 58: ifnull 88     // 无注解 ⇒ 放行；63..84 isModLoaded(value) → ifne 88
 87: return        // 注解写的 modid 未加载 ⇒ 跳过本插件
100: invokeinterface IWailaPlugin.register(WailaCommonRegistration)
105: invokestatic isPhysicallyClient() → ifeq 120   // 仅物理客户端才调 registerClient
115: invokeinterface IWailaPlugin.registerClient(WailaClientRegistration)
123..180: catch Throwable → LOGGER.error("Error loading plugin at %s")；仅当提供者 modid == "jade" 时 rethrow
```
`javap -v snownee.jade.api.WailaPlugin` → `RuntimeVisibleAnnotations: java.lang.annotation.Retention(value=RUNTIME)`，故 `getDeclaredAnnotation` 在 Fabric 变体同样有效。

**结论**：Forge = 扫模组 jar 的 `@WailaPlugin`（`ModFileScanData`）；Fabric = `fabric.mod.json` 的 `entrypoints."jade"` 列表，注解在 Fabric 侧**只是自检**——不登记入口点本类永远不会被构造。故本批加 `"jade": ["com.merlinkitsune.astral_dice.compat.jade.AstralDiceJadePlugin"]`（入口点类须 public + 无参构造，本类隐式 public 构造满足）。
## 4. javap API 摘录（同版本 Fabric 变体实测）
```
interface IWailaPlugin { default void register(IWailaCommonRegistration);
                        default void registerClient(IWailaClientRegistration); }
interface IWailaClientRegistration { void registerBlockComponent(IBlockComponentProvider, Class<? extends Block>); }
interface IBlockComponentProvider extends IToggleableProvider {
  default IElement getIcon(BlockAccessor, IPluginConfig, IElement);
  void appendTooltip(ITooltip, BlockAccessor, IPluginConfig); }          // abstract
interface ITooltip { default void add(net.minecraft.network.chat.Component); ... }
interface BlockAccessor extends Accessor<BlockHitResult> { getBlock():Block; getBlockState(); getBlockEntity(); }
interface Accessor<T extends HitResult> { getLevel():net.minecraft.world.level.Level; getPlayer():Player; }
```
与 forge 变体（同 11.13.3）逐字一致 ⇒ 两个 provider 用同一份实现，无需平台分支。
## 5. 未移植 `ShopCooldownServerDataProvider`（取舍）
neo 版独有（`IServerDataProvider<BlockAccessor>`，NBT_KEY `astral_dice:shop_cooldown`）。forge 蓝本**没有**该类，且其类注释给出机制判据：Jade 11.13.3 的 `IWailaCommonRegistration#registerBlockDataProvider` 只接受 `Class<? extends BlockEntity>`，服务端在 `BlockAccessor.getBlockEntity() == null` 时直接不发数据包，而本模组三个方块都没有 BlockEntity。Fabric 侧同版本 Jade、同 `BlockAccessor` 语义（`getBlockEntity()` 在 javap 中存在）⇒ 结论相同，移植过去只能是永不产出数据的死代码。**不移植**。
## 6. 已知缺口：抽奖盒倒计时无数据源（不阻塞编译/启动）
- fabric `component/ModAttachments.java` 无 `EVENT_DRAWER_COOLDOWN_UNTIL`（grep 仅命中 Mamushi/ShopPromotion/ShopTransfer 三组冷却键）。
- fabric `block/EventDrawBlock.java:148-157` 四个私有 seam 仍是 `TODO(B0-ATTACHMENTS)`：`getCooldownUntil(Player)` 恒 `0L`、`setCooldownUntil` 空实现。
- 故 `ShopCooldownClientProvider.remainingTicks` 的 `EventDrawBlock` 分支恒 0 ⇒ 抽奖盒不显示倒计时行，**与方块自身行为一致**（方块内部也没有真冷却）。B0-ATTACHMENTS 落地后把该分支换成 `ModAttachments.getEventDrawerCooldownUntil(player) - now` 即可，其余代码不动。
- 不受影响：卡牌商店倒计时（`SHOP_TRANSFER_COOLDOWN_END` 已 `.sync()`，`component/ModAttachments.java:1778-1783`）、筹码商店倒计时（`ChipShopState.CHIP_SHOP_STATE` 已 `.sync()`，`shop/ChipShopState.java:93-99`）、抽奖盒花费行（只读公有常量）。
## 7. 软依赖：未装 Jade 时的行为推演
1. `"jade"` 入口点键只由 Jade 自己读；无 Jade ⇒ 该键永不被请求 ⇒ `AstralDiceJadePlugin` 永不加载，`NoClassDefFoundError` 无发生路径。
2. 本线其余代码零引用 `compat/jade/*`（只有 `fabric.mod.json` 的字符串引用），主入口不会提前触发类加载。
3. `astral_dice.jade.mixins.json` 仍 `required:false`：目标类 `snownee.jade.*` 缺失 ⇒ 整组 mixin 跳过（既有策略，两个 mixin 未改）。
4. `depends` 未加 jade ⇒ Fabric 不会因缺 Jade 拒绝启动；`recommends` 亦未加（避免把纯可选联动渲染成「缺可选前置」提示）。装了 Jade 时日志打 `Start loading plugin from Astral Dice`，随后仅物理客户端调 `registerClient`。
## 8. 自检原始输出
`python scripts/__javac_check.py` —— **改动前**：
```
EXIT 1 ERRORS 174 jars 674 srcs 502
  163  NOISE_AW      7  NOISE_IFACE_INJECTION      4  REAL
    3  cannot find symbol | symbol: class EventChoiceClientHandler location: package com.merlinkitsune.ast...
    1  cannot find symbol | symbol: class EventCardOverlay location: package com.merlinkitsune.astral_dice
```
**改动后（终态）**：
```
EXIT 1 ERRORS 170 jars 684 srcs 508
  163  NOISE_AW      7  NOISE_IFACE_INJECTION
=== REAL errors ===      (空)
=== REAL by file ===      (空)
```
归因（如实登记）：REAL 4→0 **不是**我这批修掉的。那 4 条属另一批次——`client/EventChoiceClientHandler.java`、`client/EventCardOverlay.java` 在我这轮会话期间被**新建**（`git status` 为 `??`，时间戳 18:43/18:44；同时段 `srcs` 502→508、`jars` 674→684 也说明工作树有并发写入）。我这 3 个文件在 `fabric-1.20.1/build/javac-check/errors.txt` 里命中 **0** 条（grep `compat/jade` = 0），即新增代码 0 错误。

其它判据：
- `fabric.mod.json`：`json.loads` 通过；46 处 CRLF / 裸 LF 0 / 无 BOM / CRLF 结尾；entrypoints 键 = `main, client, fabric-datagen, jade`；`jade` **不在** `depends`、**不在** `recommends`（实读校验）。
- 铁律 grep（本次新增目录，4 项全 0）：`net.minecraftforge`=0、`net.neoforged`=0、`top.theillusivec4.curios`=0、`ResourceLocation.fromNamespaceAndPath`=0。工作树别处的命中全在注释/javadoc（含 `event/EventEffects.java:375` 起的 `/* ... */` 蓝本对照段）或既有 compat 垫片里，属既有状态，本批未触碰。
- 编码：3 个新 `.java` 均 UTF-8 无 BOM、CRLF（51/40/102 行，裸 LF 0）；3 个 lang 追加后仍 CRLF、无 BOM。
- lang 一致性：`python tools/check_lang_sync.py --line fabric-1.20.1` → `[OK]   fabric-1.20.1: en_us=967 键，3 份语言键集一致`；三键值与 forge 同名键逐字相等（脚本断言 `match=True`）。
## 9. 未做项
- 未移植 `ShopCooldownServerDataProvider`（理由见 §5）。
- 抽奖盒倒计时未接通（阻塞于 B0-ATTACHMENTS，见 §6）；**未**改 `ModAttachments` / `EventDrawBlock`（超出本批落点）。
- 未跑 gradle、未启动游戏、未提交（按铁律）；故「装 Jade 后悬浮框实际外观」只有字节码 + 时序推演，无实机截图。
- 未动 `mixin/jade/` 下既有两个 mixin（`ArmorValueProviderMixin` / `ArmorElementIconMixin`），行为与 `required:false` 策略保持原样。
