# Neo → Forge 移植映射（基准：neoforge-1.21.1）

> 定位：**施工手册**。规则一句话 —— **先做 neo，再照本表落 forge**。
> 本仓的双线内容以 neoforge-1.21.1 为设计正本；forge-1.20.1 侧只允许出现「平台必需」的差异，不允许在移植时顺手优化或重排逻辑。
> 本文只讲「差异点 → 具体写法对照 → 对齐动作 → 证据」，不复述源码教学。

| 项 | 值 |
|---|---|
| 基准线 | neoforge-1.21.1（Mojmap 源码，Java 21） |
| 目标线 | forge-1.20.1（SRG 生产映射，Java 17） |
| 生成时间 | 2026-10-27（Asia/Hong_Kong） |
| 证据来源 | 工作区源码 + build/moddev/artifacts/neoforge-21.1.235-sources.jar + build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar |
| 行号基准 | 生成时刻的工作树。**行号会漂移** —— 每张表的证据都给足了文件路径 + 可 grep 的锚点，复核请以 grep 结果为准 |
| 不在本文范围 | neoforge-26.1.2 / fabric-1.20.1（按要求单独建 mapping）；models/ 素材 |

## 证据标注约定

- **已核** = 我实读了那个 file:line（或 sources jar 的原文），内容与表中描述一致。
- **未验证** = 只是从相邻事实推断、或只从注释里读到而未做对照实验。**未验证的条目不得当作设计依据**，落 forge 前必须补证据。
- **批次新增** = 该点位是本次修复批次（2026-10-27 的 N1/N2/N5/N7/N8 等）之后才出现的代码，旧文档里找不到。

---

## §0 用途与使用流程

### 0.1 为什么需要这份表

两线共享**模组逻辑**，但平台层几乎每一层都不同名、不同签名、不同语义：事件被拆成 Pre/Post 还是单类、注册持有者是 `DeferredHolder` 还是 `RegistryObject`、数据组件是 `DataComponentType` 还是 NBT、属性修饰符 key 是 `ResourceLocation` 还是 `UUID`。历史教训是：**照抄 neo 的 API 到 forge，编译期不一定报错，但运行期会静默失效或数值错**（典型：forge 骰战 `setAmount` 把 `LivingHurtEvent` 段的护盾钳制整段覆盖 —— 审计 N1）。

### 0.2 标准流程

| 步骤 | 动作 | 命令 / 判据 |
|---|---|---|
| S1 | 只在 neoforge-1.21.1 里把功能做成 | `gradlew.bat :neoforge-1.21.1:classes --console=plain` |
| S2 | 给改动归类，查 §11 高频模板 | 命中模板 → 直接按模板改写；**没命中 → 先在 §12 登记一行再动手** |
| S3 | 逐项查表 | 伤害/受击 → §1；订阅与优先级 → §2；注册 → §3；组件 → §4；物品栈 → §5；效果 → §6；属性 → §7；数据资源 → §8；元数据 → §9；测试 → §10 |
| S4 | 落 forge，**逐行按「forge 写法」列改写** | 禁止直接复制 neo 的类名/方法名 |
| S5 | 双线编译 | `gradlew.bat :neoforge-1.21.1:classes :forge-1.20.1:classes --console=plain` |
| S6 | 口径一致性取证 | `grep -rn '<锚点>' neoforge-1.21.1/src/main/java forge-1.20.1/src/main/java`，把命中行贴进交付 |
| S7 | 行为判据 | 按 §10 落探针；没有探针的项在交付里写「无法判定」 |

### 0.3 三条硬规则

1. **forge 不许出现「设计性」差异**。forge 侧每一处与 neo 不同的写法，都必须能在这份表里找到对应行（平台强制），否则视为漂移。
2. **表未覆盖的 API 差异必须先登记**。在 §12 加一行（现象/根因/对齐写法/证据）之后才允许写代码。
3. **数值口径以审计正本为准**。`docs/features/attack-damage-chain-audit.md` §2「目标口径」是不变量；移植时若发现两线数值天然不同，先查该文档再决定改哪边。

### 0.4 批次新增点位索引（2026-10-27 修复批次）

> 本节回答「哪些点位是 batch 改动后新增的」。旧文档/旧注释若与此表冲突，**以本表为准**（来源都是当前工作树实读）。

| 点位 | 线 | 证据 | 说明 |
|---|---|---|---|
| 吸收快照 `PreHurtSnapshot` / `onLivingHurtRecordAbsorption` | forge | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:335-336` | **批次新增**。在 `LivingHurtEvent`(HIGHEST) 记下吸收值，供 `LivingDamageEvent` 段使用 —— 跨事件传值的补丁 |
| `settleDiceDamage` 终值收口 + 幻影吸收 | forge | 同文件 `:365`、`:389`、`:398`、`:431 registerPhantom` | **批次新增**。审计 N1/N2 的实际落地点；也是 forge 侧「改最终掉血量」的唯一正确出口 |
| `isApplyingTrueBonus()` 重入闸门 | 两线 | `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/DamageEffectCardHandler.java:43-45`、`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/DamageEffectCardHandler.java:41` | 审计 N7 用的重入闸门已于 2026-10-27 折叠批（`047bb89c`）**整体删除**（两线 `.java` 0 处）。折叠后一次命中只有一条伤害实例，**新增「受击时」被动不再需要这类闸门** |
| 弱标记 ×1.10 并入终值 | forge | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:327`（常量）、`:1018`、`:1329` | 审计 N8。常量抽出；但法伤链仍走独立的 `onWeakMarkDamage`（见 §1.3） |
| Papara 回血改挂结算后事件 | 两线 | `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/PaparaSignItem.java:72-73`、`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/PaparaSignItem.java:72-73` | 审计 N9。两线写法不同（neo `Post` / forge 单类），属平台差异 |
| `dice_damage` 进 `bypasses_cooldown` | 两线 | `neoforge-1.21.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json`、`forge-1.20.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json` | 审计 N6。两线同值（7 项全集），旧交接里「仍缺」的说法不成立（见 §8.3） |

### 0.5 对齐检查清单（§0）

- [ ] 改动已在 neo 侧完成并通过 `:neoforge-1.21.1:classes`
- [ ] 改动类型已归类到 §11 的某一类（或已在 §12 新增一行）
- [ ] forge 侧每一处差异都能指回本文档的具体小节
- [ ] 双线 `:classes` 均 `BUILD SUCCESSFUL`
- [ ] 交付里贴了双线 grep 命中行（S6）
- [ ] 没有探针覆盖的行为，明确写了「无法判定」

---

## §1 伤害与受击链路

这是两线差异最致命的一节。1.21 把伤害过程拆成多段事件，1.20.1 是「少而粗」的几个事件，**同一段逻辑在两线挂的位置不同 ⇒ 谁覆盖谁的关系也不同**。

### 1.1 事件面对照

| 语义阶段 | neoforge-1.21.1 | forge-1.20.1 | 差异要点 |
|---|---|---|---|
| 攻击最开始（可取消、早于一切） | `LivingIncomingDamageEvent`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:1836`（`applyDodgeCancel`）、`:1864`（`onMosesBrokenDodge`）；import 见同文件 `:53` | `LivingAttackEvent`：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:2081`（`applyDodgeCancel`）、`:2123`（`onMosesBrokenDodge`）；import 见同文件 `:47`；派发点注释 `:2065`、`:2102`（「LivingAttackEvent 在 hurt 第一条语句派发，早于…」） | **已核**。名字不同，语义位置对等。 |
| 护甲/抗性结算前（可改金额，此时吸收还没扣） | 无同名事件（neo 在这一段没有自家钩子） | `LivingHurtEvent`：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:335 @SubscribeEvent(priority = EventPriority.HIGHEST)` → `:336 onLivingHurtRecordAbsorption(LivingHurtEvent)`；类本身 `@Cancelable`（sources jar `net/minecraftforge/event/entity/living/LivingHurtEvent.java:33-34`） | **已核**。forge 独有的一段，用来在吸收被扣之前记录快照（批次新增）。 |
| 护甲/魔法之后、吸收之前或之后（可改终值） | `LivingDamageEvent.Pre`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:279 @SubscribeEvent(priority = EventPriority.NORMAL)` → `:280 onLivingDamagePre(LivingDamageEvent.Pre event)`，改值用 `event.setNewDamage(...)`（`:824`、`:1101`、`:1555`） | `LivingDamageEvent`（单类）：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:365 settleDiceDamage(LivingEntity, LivingDamageEvent, float)`，改值用 `event.setAmount(...)`（`:389`、`:399`）；另一处 `:1774 event.setAmount(event.getAmount() * multiplier)` | **已核**。这是全仓最大坑，见 1.2。 |
| 结算完成（读最终伤害） | `LivingDamageEvent.Post`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DamageNumberAggregator.java:113 onLivingDamagePost(LivingDamageEvent.Post event)`（注释 `:38`、`:55` 说明 Post 的 `getNewDamage()` 是实际扣血量） | `LivingDamageEvent`：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DamageNumberAggregator.java:119 onLivingDamage(LivingDamageEvent)`（注释 `:38`「1.20.1 是单类、没有 Pre/Post 之分」） | **已核**。neo 有两段可取，forge 只有一段 ⇒ forge 里「读终值」和「改终值」挤在同一个事件，优先级决定先后。 |
| 护盾（吸收池）钳制 | `item/RenShieldManager`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/RenShieldManager.java:414 event.setNewDamage(remaining)`（挂在 Pre） | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/RenShieldManager.java:416 event.setAmount(remaining)`（挂在 `LivingHurtEvent`） | **已核**。落点不同 ⇒ forge 的钳制会被后面的 `LivingDamageEvent` 覆盖掉（1.2）。 |
| 法伤加成段（**已折叠，不再是第二次 hurt**） | 折叠点 = **护甲之前**：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/DamageEffectCardHandler.java:73-74 `SubscribeEvent(priority = EventPriority.HIGHEST) onLivingIncomingDamagePre(LivingIncomingDamageEvent)`，唯一写值 `:180 event.setAmount(foldBonusBypassingArmor(...))` | 折叠点 = `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/DamageEffectCardHandler.java:66-67 onLivingHurtPre(LivingHurtEvent)` `HIGHEST，唯一写值 `:172` 同一句 | **已核（2026-10-27 折叠批 `047bb89c` + 裁决批 `34929f46`）**。旧口径「法伤 = 两次 hurt、两段吃吸收顺序相反」**作废**；现在加成与本体同管道、只结算一次，且 `foldBonusBypassingArmor` 通过在护甲公式上做二分预补偿实现「加成不吃护甲」（见审计 §7.8）。 |

### 1.2 谁能覆盖谁（本仓踩过的最大坑）

| 场景 | 事实 | 正确写法 |
|---|---|---|
| forge 骰战覆盖护盾钳制 | `RenShieldManager` 只能挂 `LivingHurtEvent` 改「平台数值」；骰战随后在 `LivingDamageEvent` 用终值 `setAmount` 覆盖 ⇒ 那次钳制被整段丢弃。仓内已有明确注释：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:366-368`（**已核**，批次新增）。 | 把「本模组自己的」数值钳制搬进 `settleDiceDamage`（`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:365`），**不要**指望 `LivingHurtEvent`。 |
| ~~forge 吸收被扣两次~~ **REFUTED** | ① `setAbsorptionAmount`（`LivingEntity.java:2932-2938`）把负值钳 0 ⇒ 玩家/非玩家路径的总消耗恒为 `min(d, A)`，`d > A` 时不存在双扣（批次 D 的 P1-1 已被 Lead 与批次 E 双双否掉）；② 真正存在的是**幻影 H 挂在玩家黄心上**：`Player#actuallyHurt`（`Player.java:940-965`，覆写不调 super）**没有** `LivingEntity.java:1684` 那一步 ⇒ 批次 F 约定的「平台会再扣一次 H」对玩家不成立。 | **已修（2026-10-27 近战批 F1）**：`settleDiceDamage` 对 `target instanceof Player` 直接写真实终态、**不登记幻影**。不要再照抄「补偿玩家吸收」的修法。 |
| neo 侧等价的坑 | neo 的 `Pre` 就是终值唯一出口，`Post` 只读；平台吸收在 Pre 之后扣（`LE.java:1790-1792`）⇒ 不存在「后续事件覆盖前面钳制」的问题。 | neo 侧照常写 `Pre` + `setNewDamage`。 |
| 迁移动作（一句话） | **neo 写在 `LivingDamageEvent.Pre` 里的 `setNewDamage`，落到 forge 必须写进 `settleDiceDamage`（或确保是该事件里最后一个 `setAmount`）**；写在 `LivingHurtEvent` 里的改动，凡是会改「最终掉血量」的，一律视为会被覆盖。 | — |

### 1.3 两次 hurt 的连带影响（**历史**：2026-10-27 折叠后不再成立）

> ⚠️ 本节记录的是**折叠前**的事实，仅供理解历史缺陷；现行做法见本节末段「移植纪律」。

法伤命中 = 本体一段 + 加成段一次独立 `hurt`。加成段用的伤害类型在 `bypasses_cooldown` 里（`neoforge-1.21.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json`、`forge-1.20.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json`，两线同值），所以不进无敌帧分支。代价是**所有「每次受击」类效果会执行两次**：

| 受影响点 | neo | forge | 备注 |
|---|---|---|---|
| 弱标记 ×1.10 | `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:1538-1539 onWeakMarkDamage(LivingDamageEvent.Pre)` | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java` 的同名 handler（`LivingDamageEvent` 段，吸收后） | 两段各乘一次 ⇒ 1.10²；且 forge 侧基数在吸收后（审计 D2） |
| Papara 回血 | `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/PaparaSignItem.java:72-73`（挂 `Post`） | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/PaparaSignItem.java:72-73`（挂 `LivingDamageEvent`） | 仍会触发两次（审计 P1-2 清单第 1 条未加闸门） |
| 狂暴反噬 / 芯片承伤 | `DiceCombatEvents.java` 内对应 handler | 同名 handler | 见审计 P1-2 |

**移植纪律**：2026-10-27 折叠批（`047bb89c`）之后，法伤加成并入**同一次**伤害事件、只结算一次 ⇒ 不再存在「加成段第二次 hurt」，`DamageEffectCardHandler.isApplyingTrueBonus()` 已删除（两线 `.java` 0 处），**不要再照抄旧口径去加这个闸门**。需要判断「本次受击是否来自法伤」时用 `SpellDamageRegistry.isSpellDamage(source, source.getDirectEntity())`。

### 1.4 对齐检查清单（§1）

- [ ] 新写的金额改动，落点在 forge 侧是 `settleDiceDamage` 还是 `LivingHurtEvent`？只有前者能生效
- [ ] 没有误用 `LivingHurtEvent` 去改「最终掉血量」（会被覆盖）
- [ ] 读终值：neo 用 `Post`，forge 用同一 `LivingDamageEvent` 的更高/更低优先级，**没有**照抄 `LivingDamageEvent.Post`
- [ ] 新增「受击时」被动已判断法伤双击影响
- [ ] 若涉及吸收/幻影吸收，已读过 `settleDiceDamage` 与 `registerPhantom`，没有重复实现平台已做的事

---

## §2 事件总线与优先级

### 2.1 订阅姿势对照

| 项 | neoforge-1.21.1 | forge-1.20.1 | 说明 |
|---|---|---|---|
| 类级注解 | `@EventBusSubscriber(modid = AstralDiceMod.MODID)`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/choice/CardChoiceManager.java:53` | `@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)`：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/choice/CardChoiceManager.java:44` | **已核**。forge 多一层 `@Mod.` 前缀。 |
| 仅客户端 | `@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/client/BeamFxRenderer.java:38` | `@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)`：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/BeamFxRenderer.java:38` | **已核**。同一套 `value = Dist.X`。 |
| MOD 总线订阅 | `bus = MOD`：见 `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java:89` 的注释行 | forge 侧 MOD 总线通常写 `bus = Mod.EventBusSubscriber.Bus.MOD` | neo 侧形态**已核**（注释）；forge 侧具体写法**未验证**（本仓当前无该类用例，需实测）。 |
| 手动注册入口 | `AstralDiceMod.java:38 public AstralDiceMod(IEventBus modEventBus, ModContainer modContainer)`，`:53-67` 各 `XXX.register(modEventBus)` | `AstralDiceMod.java:52 IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();`，`:57-75` 同形注册（`ModEnchantments.ENCHANTMENTS.register(modEventBus)` 在 `:60`） | **已核**。forge 没有注入的 `IEventBus` 参数，要从 `FMLJavaModLoadingContext` 取。 |
| 方法级注解 | `@SubscribeEvent(priority = ...)` | `@SubscribeEvent(priority = ...)` | **已核**，同名同参。 |
| 取消态订阅 | `receiveCanceled = true`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/client/ModernUITooltipCompat.java:176`（注释 `:173-176` 说明 Modern UI 会在 LOW 取消该事件） | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/ModernUITooltipCompat.java:176` 同写法 | **已核**，两线同形。 |

### 2.2 可取消性差异（会直接改变行为）

| 事件 | neoforge-1.21.1 | forge-1.20.1 | 对齐动作 |
|---|---|---|---|
| `MobEffectEvent.Expired` | `implements ICancellableEvent`（sources jar `net/neoforged/neoforge/event/entity/living/MobEffectEvent.java`，类声明 `public static class Expired extends MobEffectEvent implements ICancellableEvent`，注释「This event is {@link ICancellableEvent}」） | 类声明 `public static class Expired extends MobEffectEvent`，注释「This event is not {@link Cancelable}」，**全文件 `Cancellable` 出现 0 次**（sources jar 同名路径） | **已核**。forge 侧**不能** `setCanceled()`；要「阻止移除」只能挂 `MobEffectEvent.Remove`（forge 该内部类带 `@Cancelable`，sources jar `MobEffectEvent.java:43-47`）。 |
| `MobEffectEvent.Remove` | 存在且可取消；本仓消费点 `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/ModEffectEvents.java:19 onModEffectRemovalPrevented(MobEffectEvent.Remove)`（另有 `:76 onEffectTimerForget`） | 存在且 `@Cancelable`（sources jar `:46`）；消费点 **`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModEffectEvents.java:19`**（与 neo 同一个行号，另有 `:76`、`:106 onExternalRemovalRestore`）。**注**：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/command/AstralPartyCommand.java:623-624` 的注释仍写着 `:107-127`，那是失效行号（现 `:107-127` 落在 `onExternalRemovalRestore` 上）—— 行号漂移的活样本 | **已核**。 |
| `LivingHurtEvent` | 无此事件 | `@Cancelable`（sources jar `net/minecraftforge/event/entity/living/LivingHurtEvent.java:33-34`） | **已核**。forge 独有可取消点。 |
| 事件拆分粒度 | `LivingDamageEvent.Pre` / `.Post`、`LivingIncomingDamageEvent` | `LivingDamageEvent` 单类、`LivingAttackEvent`、`LivingHurtEvent` | **已核**。见 §1。 |

### 2.3 优先级纪律

- 两线都用 `EventPriority`（`HIGHEST/HIGH/NORMAL/LOW/LOWEST`），且顺序语义一致（越早执行优先级枚举越靠前）。
- **已核**的用法：`EventPriority.NORMAL`（neo `combat/DiceCombatEvents.java:279`）、`EventPriority.LOW`（neo `:1474`、`:1538`）、`EventPriority.HIGHEST`（forge `:335`）、`EventPriority.LOWEST`（两线 `client/ModernUITooltipCompat.java:176`）。
- **教训**：forge 的 `LivingDamageEvent` 同时承担「改终值」和「读终值」，所以**同一条链上必须显式指定优先级**，不能用默认 NORMAL 让注册顺序决定；neo 因为分了 Pre/Post，天然有先后。

### 2.4 对齐检查清单（§2）

- [ ] 注解是 `@EventBusSubscriber`（neo）还是 `@Mod.EventBusSubscriber`（forge）
- [ ] MOD 总线事件在 forge 侧确认用的是 MOD bus（不是默认 FORGE bus）
- [ ] 若 neo 侧用了可取消事件，已核 forge 同名事件是否真的 `@Cancelable`
- [ ] `MobEffectEvent.Expired` 一律按「forge 不可取消」处理
- [ ] `LivingDamageEvent` 上的多个 handler 已显式写 `priority`，不依赖注册顺序

---

## §3 注册与持有者

### 3.1 持有者类型

| 项 | neoforge-1.21.1 | forge-1.20.1 | 对齐动作 |
|---|---|---|---|
| 持有者类 | `DeferredHolder<MobEffect, MobEffect>`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/effect/ModEffects.java:34 public static final DeferredHolder<MobEffect, MobEffect> KING_POWER` | `RegistryObject<MobEffect>`：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/effect/ModEffects.java:34 public static final RegistryObject<MobEffect> KING_POWER`（注册器 `:31 DeferredRegister<MobEffect> EFFECTS`） | **已核**。常量名可以完全一样，**类型不同**。 |
| 全仓口径 | `RegistryObject` 在 neo 侧**0 处**（grep 无命中） | 全仓通用 `RegistryObject` | **已核**。 |
| 取实例 | `DeferredHolder` 本身实现 `Holder`/`Supplier` ⇒ 大多数 API 直接传常量即可（例：`player.hasEffect(ModEffects.DICE_BLESSING)`，`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:386`） | 必须 `.get()`（例：`player.hasEffect(ModEffects.DICE_BLESSING.get())`，`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:607`） | **已核**。这是落 forge 时最高频的编译错误来源。 |

### 3.2 注册入口差异

| 项 | neoforge-1.21.1 | forge-1.20.1 |
|---|---|---|
| 取 mod 总线 | 构造器注入：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java:38` | 静态取：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java:52` |
| 注册调用 | 同文件 `:53-67` | 同文件 `:57-75` |
| 附魔注册 | **无 `ModEnchantments` 类**（只有 `event/HiddenCurseEnchantment.java`） | `effect/ModEnchantments.java:22 public static final RegistryObject<Enchantment> CURSE_MARKER`，注册在 `AstralDiceMod.java:60` |

**已核**。两线附魔接线**不同构**，1.21 走数据驱动/其他路径；移植附魔相关逻辑时不能照抄。

### 3.3 踩坑顺序

- `.get()` 只能**在注册完成之后**调用；静态初始化里互相调 `.get()` 会拿到 `null` 或抛异常。本仓未发现该类用例 ⇒ **未验证**（若将来需要，用延迟到 `FMLCommonSetupEvent` / `ServerStartedEvent` 的方式）。

### 3.4 对齐检查清单（§3）

- [ ] 所有 `ModXxx.CONST` 在 forge 侧都补了 `.get()`（`hasEffect`、`addEffect`、`getAttribute`、物品相关）
- [ ] 没有在静态初始化/构造期调用 `.get()`
- [ ] 新增注册表项时，两线的 `DeferredRegister.create(...)` 注册表 key 一致
- [ ] 附魔相关改动已确认两线接线不同构（neo 无 `ModEnchantments`）

---

## §4 数据组件与存档

### 4.1 两线承载方式

| 项 | neoforge-1.21.1 | forge-1.20.1 | 对齐动作 |
|---|---|---|---|
| 声明 | `DeferredHolder<DataComponentType<?>, DataComponentType<WeaponEnhancement>> WEAPON_ENHANCEMENT`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/component/ModDataComponents.java:19` | `ItemDataKey`（starengine 提供）：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/component/ModDataComponents.java:10`（import `com.merlinkitsune.starengine.component.ItemDataKey`）、`:17 ItemDataKey.create("weapon_enhancement", WeaponEnhancement.CODEC)` | **已核**。常量名两线可以完全一样，**机理不同**。 |
| 持久化 | `.persistent(WeaponEnhancement.CODEC)`：同文件 `:22` | `ItemDataKey.create(name, codec)` 内部走 ItemStack NBT | **已核**。 |
| 网络同步 | `.networkSynchronized(WeaponEnhancement.STREAM_CODEC)`：同文件 `:23` | 无对应调用；仓内注释 `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/component/ModDataComponents.java:13-15`「1.20.1 shim：常量名与 1.21 一一对应，内部由 ItemDataKey(ItemStack NBT) 承载；1.21 的 networkSynchronized 语义由 NBT 自动同步承担」 | **已核（注释级）**。落 forge 时**不要**去找同步方法，也不要手写 buf 编解码。 |
| 读 | `stack.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), null)`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:365` | `ItemDataKey.getOrDefault(stack, fallback)`：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/RepulsionItem.java:44-46`（注释给出两线写法原话） | **已核**。 |
| 写 | `stack.set(KEY.get(), v)`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:511` | `ItemDataKey.set(stack, v)`：同上 `RepulsionItem.java:44-46` 注释 | **已核**。 |
| 默认值 | 组件注册时给 codec 默认或调用点传 fallback | `ItemDataKey.withItemDefault("card_uses", Codec.INT, ...)`：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/component/ModDataComponents.java:27-28` | **已核**。同名字段两线默认值必须一致，否则同一物品跨线数值不同。 |
| 注册 | `AstralDiceMod.java:54` 把 `ModDataComponents.DATA_COMPONENT_TYPES` 注册到 mod 总线 | forge 侧注册清单 `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java:57-75` **不含** `ModDataComponents`（1.20.1 无数据组件注册表） | **已核**（两侧清单都读过）。 |

### 4.2 坑

| 坑 | 后果 | 写法 |
|---|---|---|
| forge 侧照抄 `DataComponentType.builder()` | 编译不过（1.20.1 无该类） | 用 `ItemDataKey.create(name, CODEC)` / `withItemDefault(...)` |
| 漏掉 `networkSynchronized` | 客户端侧读不到服务端写入的值（红字「组件未同步」） | **未验证**（这是 1.21 官方 API 语义，本仓当前所有组件都补了 `networkSynchronized`，无反面用例） |
| 组件值可变 | `Codec` 序列化后比较/缓存失效，组件相等判断出问题 | **未验证**（本仓组件值都是 record/不可变对象，无反面用例） |
| 组件值里塞 `ItemStack` | 1.21 需要 `ItemStack.CODEC` 且要处理空栈；1.20.1 直接 NBT | 尽量避免；必须用时两线都做空值兜底 |

### 4.3 对齐检查清单（§4）

- [ ] 新增字段两线**同名**（`"weapon_enhancement"` 这类字符串一致）
- [ ] neo 侧 `.persistent(...)` 与 `.networkSynchronized(...)` 都写了
- [ ] forge 侧走 `ItemDataKey`，没有引入任何 1.21 组件类
- [ ] 两线的默认值一致（对比 `withItemDefault` 与 neo 的 fallback）
- [ ] 读取点统一用 `.get()`（neo 的 holder）+ `ItemDataKey.getOrDefault`（forge）
- [ ] 旧存档兼容：1.20.1 走 NBT 键，字段重命名会导致老物品读默认值 —— 改名前先确认是否需要迁移

---

## §5 物品栈 API

### 5.1 对照表

| 操作 | neoforge-1.21.1 | forge-1.20.1 | 说明 |
|---|---|---|---|
| 扣 1 个（尊重创造模式） | `stack.consume(1, player)`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/card/BaseEffectCardItem.java:339`、`:428`；实现见 sources jar `net/minecraft/world/item/ItemStack.java:1089-1093` = `if (entity == null || !entity.hasInfiniteMaterials()) shrink(amount)` | `if (!player.isCreative()) live.shrink(1)`：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/BaseEffectCardItem.java:338`；`stack.shrink(1)`：同文件 `:428` | **已核**。两线创造模式语义等价（都「不扣」），但 **1.20.1 根本没有 `consume` 方法**（sources jar `ItemStack.java` 只有 `shrink` 在 `:1017`）⇒ 照抄必然编译失败。 |
| 同物品判定 | `ItemStack.isSameItemSameComponents(a, b)`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/card/BaseEffectCardItem.java:346`、`screen/CardChoiceScreen.java:516`；sources jar `ItemStack.java:632` | `ItemStack.isSameItemSameTags(a, b)`：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/BaseEffectCardItem.java:345`、`screen/CardChoiceScreen.java:498`、`screen/CardShopScreen.java:1635`、`shop/CardShopMenu.java:980`；sources jar `:489` | **已核**。语义差别：1.21 只比「已注册组件」，1.20.1 比整包 NBT ⇒ 若对方物品带 1.20.1 特有的杂项 NBT，判定会不同（**未验证**，本仓物品均为自有 NBT key）。 |
| 只缩数量 | `shrink(n)`（sources jar `:1085`） | `shrink(n)`（sources jar `:1017`） | 两线同名，可直接用。 |
| `isEmpty` / `getCount` | 同名 | 同名 | — |

### 5.2 移植动作

| 场景 | neo 写法 | forge 写法 |
|---|---|---|
| 用掉一张牌 | `live.consume(1, player)` | `if (!player.isCreative()) live.shrink(1)`（注意：必须是**同一个** `ItemStack` 实例，1.20.1 的 `shrink` 改的是实例自身） |
| 判断「还是同一张牌」 | `isSameItemSameComponents` | `isSameItemSameTags` |
| 判断物品类型 | `stack.is(ModItems.X.get())`（neo 侧 `ModItems.X` 是 `DeferredHolder`，也可直接传） | `stack.is(ModItems.X.get())`（forge 必须 `.get()`） |

### 5.3 对齐检查清单（§5）

- [ ] 全仓 grep `\.consume(` 在 forge 侧命中数为 0（该 API 不存在）
- [ ] 全仓 grep `isSameItemSameComponents` 在 forge 侧命中数为 0；`isSameItemSameTags` 在 neo 侧为 0
- [ ] 创造模式语义已比对（两线都应「不扣」）
- [ ] `shrink` 的调用对象是活的 `ItemStack`（不是副本），且改完写回容器/背包

---

## §6 状态效果

### 6.1 对照表

| 项 | neoforge-1.21.1 | forge-1.20.1 | 说明 |
|---|---|---|---|
| 持有者类型 | `DeferredHolder<MobEffect, MobEffect>`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/effect/ModEffects.java:34` | `RegistryObject<MobEffect>`：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/effect/ModEffects.java:34`（注册器 `:31 DeferredRegister<MobEffect> EFFECTS`） | **已核**。 |
| `hasEffect` | `player.hasEffect(ModEffects.DICE_BLESSING)`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:386`、`:406`、`:476`、`:506` | `player.hasEffect(ModEffects.DICE_BLESSING.get())`：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:607`、`:627`、`:695`、`:725` | **已核**。漏 `.get()` = 编译错误（好事）。 |
| 无限时长 | `MobEffectInstance.INFINITE_DURATION`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/effect/AmeLoveEffect.java:32` | 同常量：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/effect/AmeLoveEffect.java:36` | **已核**，两线同名同值（-1）。 |
| 每 tick 生效 | `applyEffectTick(LivingEntity, int)`（sources jar `net/minecraft/world/effect/MobEffect.java:74`）+ 生效频率钩子 `shouldApplyEffectTickThisTick(int, int)`（`:82`） | `applyEffectTick`（`MobEffect.java:62`）+ `isDurationEffectTick(int, int)`（`:111`） | **已核**。方法名不同是 1.20.5 的改名，**改「多久生效一次」的逻辑要换方法名**。 |
| 内部移除通道 | `ModEffectRemoval.remove(player, holder)`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/effect/AmeLoveEffect.java:50`、`effect/ChargeEffect.java:70`、`command/AstralPartyCommand.java:625` | `ModEffectRemoval.remove(player, ModEffects.X.get())`：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/effect/AmeLoveEffect.java:54`、`effect/ChargeEffect.java:71`、`:86`、`command/AstralPartyCommand.java:635` | **已核**。forge 侧必须 `.get()`；该通道存在的原因是 forge 有 `onModEffectRemovalPrevented` 会拦截直接移除（见 §2.2）。 |
| 效果类 mixin 目标 | `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/mixin/HealOrHarmMobEffectMixin.java:41`、`RegenerationMobEffectMixin.java:23`（都注入 `applyEffectTick`） | 只有 `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/RegenerationMobEffectMixin.java:25`（`applyEffectTick`），**无 heal/harm mixin** | **已核**。两线 mixin 面不同，移植时要确认目标 mixin 是否已存在。 |

### 6.2 坑

| 坑 | 现象 | 写法 |
|---|---|---|
| 在 forge 直接 `removeEffect` | 被 `onModEffectRemovalPrevented` 拦回（效果不消失） | 一律走 `ModEffectRemoval.remove(...)` |
| 用 `isDurationEffectTick` 覆盖 neo 的 `shouldApplyEffectTickThisTick` 逻辑 | forge 编不过 / neo 编不过 | 按线换方法名，逻辑体一致 |
| `addEffect(new MobEffectInstance(...))` 的构造参数 | 两线参数顺序基本一致，但 1.21 有 `ambient/visible/showIcon` 的变体 | 用最简单的 `(Holder/RegistryObject, duration, amplifier)`，其余靠 setter |

### 6.3 对齐检查清单（§6）

- [ ] 所有 `ModEffects.X` 在 forge 侧带 `.get()`
- [ ] `INFINITE_DURATION` 直接用常量，没有魔数 `-1`
- [ ] 移除效果走 `ModEffectRemoval`（两线同 API，参数类型不同）
- [ ] 若改了「生效频率」，已按线换 `shouldApplyEffectTickThisTick` / `isDurationEffectTick`
- [ ] 效果 mixin 的 `method` 名在生产环境是 SRG（forge）⇒ 已在 §9 的 refmap 前提下工作

---

## §7 属性与修饰符

### 7.1 对照表

| 项 | neoforge-1.21.1 | forge-1.20.1 | 说明 |
|---|---|---|---|
| 构造 | `new AttributeModifier(ResourceLocation id, double amount, Operation op)`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/EventEffects.java:75`、`:91` | `new AttributeModifier(UUID id, String name, double amount, Operation op)`：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/EventEffects.java:76`、`:97` | **已核**。forge 多一个 `name` 参数，且第一个参数是 `UUID`。 |
| id 生成 | `attributeModifierId("armor")`：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/MotoHelmetChipItem.java:48` | `new AttributeModifier(attributeModifierId("armor"), "armor", ...)`：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/MotoHelmetChipItem.java:46` | **已核**。helper 名两线相同，但返回类型不同（`ResourceLocation` vs `UUID`）。 |
| Operation 枚举 | `ADD_VALUE` / `ADD_MULTIPLIED_BASE` / `ADD_MULTIPLIED_TOTAL`（例：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:1209 AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL`） | `ADDITION` / `MULTIPLY_BASE` / `MULTIPLY_TOTAL`（例：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:1422 AttributeModifier.Operation.MULTIPLY_TOTAL`） | **已核**。一一映射，**语义相同** —— 公式注释可证：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatModifiers.java:1004` 与 `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatModifiers.java:1002`。 |
| 效果里的修饰符 | neo `addAttributeModifier(Holder<Attribute>, ResourceLocation, ...)` | forge `addAttributeModifier(Attribute, String uuid, ...)`：**必须是 UUID 字符串**（内部 `UUID.fromString`）——仓内注释证据：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/effect/DorothySocialiteEffect.java:15`、`effect/AmeOverdoseEffect.java:45`、`effect/BlueCurseEffect.java:14`、`effect/DragonRoarBreakEffect.java:24` | **已核（注释级）**。传非 UUID 字符串会直接抛异常。 |

### 7.2 坑

| 坑 | 后果 | 写法 |
|---|---|---|
| 把 `ResourceLocation` 传给 forge | 编译错 | 用 `attributeModifierId(...)` 的 forge 版本（返回 `UUID`） |
| UUID 不稳定（每次随机生成） | 重登/重进后修饰符叠加 | **未验证**（本仓统一用固定字符串算 UUID，无反面用例），但沿用现有 helper 即可 |
| 用公式注释互相「翻译」Operation | 误以为两线公式不同 | 不要翻译：两线公式注释已经逐字对应（§7.1 末两行） |
| 一个属性挂多个修饰符时用同一个 id | 后一个覆盖前一个 | id 里带语义后缀（本仓已有 `attributeModifierId("armor")` 的写法） |

### 7.3 对齐检查清单（§7）

- [ ] `new AttributeModifier(` 在 forge 侧是 4 参（UUID + name + amount + op）
- [ ] `Operation` 已按 `ADD_VALUE↔ADDITION`、`ADD_MULTIPLIED_BASE↔MULTIPLY_BASE`、`ADD_MULTIPLIED_TOTAL↔MULTIPLY_TOTAL` 映射
- [ ] 效果类里的 `addAttributeModifier` 第二参是合法 UUID 字符串
- [ ] 移除修饰符用的 id 与添加时**逐字节相同**
- [ ] 公式注释两线一致（不重复实现公式）

---

## §8 伤害类型与数据资源

### 8.1 路径与文件集

| 项 | neoforge-1.21.1 | forge-1.20.1 | 说明 |
|---|---|---|---|
| 伤害类型目录 | `neoforge-1.21.1/src/main/resources/data/astral_dice/damage_type/` | `forge-1.20.1/src/main/resources/data/astral_dice/damage_type/` | **路径完全同名**（已核，两线各 7 个 json）。 |
| 标签目录 | `neoforge-1.21.1/src/main/resources/data/minecraft/tags/damage_type/` | `forge-1.20.1/src/main/resources/data/minecraft/tags/damage_type/` | **路径完全同名**（两线各 4 个 json：`bypasses_armor` / `bypasses_cooldown` / `bypasses_enchantments` / `bypasses_resistance`）。 |
| 工厂方法 | `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/damage/ModDamageTypes.java`：`:127 diceDamage`、`:135 trueDamage(level)`、`:144 trueDamage(level, causing)`、`:153 cardSpell`、`:162 skillDamage`、`:171 extraDamage`、`:183 cardCost`、`:191 unreducibleDamage` | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/damage/ModDamageTypes.java` **行号逐个相同** | **已核**。两线工厂方法形状完全对齐，改签名时两边都改。 |
| 逐字节一致 | 7 个 json 与 4 个 tag 两线**内容相同**（批次 D 用 md5 核过：`bypasses_armor` 3ee8d720…（223 B）、`bypasses_cooldown` f7fbb3a3…（255 B）、`bypasses_enchantments`/`bypasses_resistance` 02288108…（94 B）） | 同 | **已核**。 |

### 8.2 七个类型的 bypass 面（两线一致）

| 类型 | bypasses_armor | bypasses_cooldown | bypasses_enchantments | bypasses_resistance | 语义要点 |
|---|---|---|---|---|---|
| `card_spell` | ✔ | ✔ | ✘ | ✘ | 法术直伤；不吃护甲、不吃无敌帧、仍吃附魔保护与抗性 |
| `true_damage` | ✔ | ✔ | ✘ | ✘ | 法伤加成段用它（`DamageEffectCardHandler` 的第二次 hurt） |
| `skill_damage` | ✔ | ✔ | ✘ | ✘ | 技能伤害；**禁止**加进 matcher 白名单（`SpellDamageRegistry` 内硬注释） |
| `extra_damage` | ✔ | ✔ | ✘ | ✘ | 附加伤害段 |
| `card_cost` | ✔ | ✔ | ✔ | ✔ | 代价型（自伤/反噬），连附魔保护与抗性都不吃 |
| `unreducible_damage` | ✔ | ✔ | ✔ | ✔ | 不可减免；批次 N5 的惩罚伤害走这个 |
| `dice_damage` | ✘ | ✔ | ✘ | ✘ | **唯一吃护甲**的类型；骰战主伤害 |

**已核**（两线 tag json 逐项读过）。

### 8.3 一处需要纠正的既有说法（反例）

> 旧交接里提到「P1 两线仍缺 `dice_damage` 于 `bypasses_cooldown`」。

**不成立。** 实读两线文件：`neoforge-1.21.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json` 与 `forge-1.20.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json` 的值都是 7 项全集（`true_damage`、`skill_damage`、`extra_damage`、`card_cost`、`unreducible_damage`、`card_spell`、`dice_damage`）。若别处仍报「缺」，那是更早的快照，或指的是 26.1.2 / fabric 两条线（不在本文件范围）。**已核**。

### 8.4 新增伤害类型 / 加标签的动作清单

| 步骤 | 动作 |
|---|---|
| 1 | 两线同时新增 `data/astral_dice/damage_type/<name>.json`（`message_id` 用 `astral_dice.<name>`，两线同名） |
| 2 | 决定是否进 4 个 bypass 标签；**两线必须同答案**，否则同一行为在两线数值不同 |
| 3 | 在 `ModDamageTypes` 加工厂方法（两线行号应对齐，便于 grep） |
| 4 | 若涉及法术判定，检查 `SpellDamageRegistry` 的 matcher 是否需要认识它（注意 §8.5 的禁忌） |

### 8.5 禁忌

- **不要把 `skill_damage` 加进 `isSpellDamage` 的 matcher**（`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/SpellDamageRegistry.java:234-237` 有硬注释说明原因：会让技能伤害重复吃法术加成）。
- 不要在两线写不同的 bypass 集合 —— 这类差异不会编译报错，只在实机数值上体现。

### 8.6 对齐检查清单（§8）

- [ ] 两线 `data/astral_dice/damage_type/` 的文件名集合相同
- [ ] 两线 `data/minecraft/tags/damage_type/` 的文件名集合相同（4 个）
- [ ] 同名 json 内容一致（可用 `diff -r --strip-trailing-cr` 或哈希比对）
- [ ] `ModDamageTypes` 工厂方法两线签名一致
- [ ] 新增类型时没破坏「`dice_damage` 是唯一吃护甲的类型」这一事实

---

## §9 元数据与构建

### 9.1 mods.toml：文件位置与字段

| 项 | neoforge-1.21.1 | forge-1.20.1 | 对齐动作 |
|---|---|---|---|
| 文件 | `neoforge-1.21.1/src/main/templates/META-INF/neoforge.mods.toml`（**在 `templates/` 下，不在 `resources/`**） | `forge-1.20.1/src/main/templates/META-INF/mods.toml`（同样在 `templates/` 下） | **已核**。改动 mod 元数据要改模板目录里的文件。 |
| modLoader | `:1 modLoader="javafml"` | `:1 modLoader="javafml"` | **已核**，同名。 |
| loaderVersion | `:3 loaderVersion="${loader_version_range}"` | `:7 loaderVersion="${forge_language_version_range}"` ；**坑**见 `:5` 注释：「实测 loaderVersion="[47.4.10,48)" 会被 FML 判为 fml.language.missingversion 而拒绝加载」 | **已核（注释级）**。forge 侧不要手写 loader 区间，用模板变量。 |
| 依赖声明语法 | `:80 type="required"`、`:118`/`:126 type="optional" versionRange="*"`（NeoForge 1.20.5+ 新语法） | `:77 mandatory=true`、`:100 mandatory=true`；`:70-72` 注释：「Forge 1.20.1 依赖声明用 mandatory 布尔（NeoForge 1.20.5+/1.21 才是 type=required/optional）；1.20.1 无 optional 依赖类型」 | **已核（注释级）**。⇒ forge 侧**不能**写 `type="optional"`，只能 `mandatory=false` 或干脆不声明。 |
| 依赖版本 | `:84 versionRange="[21.1,21.2)"`（neoforge）、`:97 versionRange="${minecraft_version_range}"` | `:89 versionRange="${forge_version_range}"` | **已核**。 |
| curios 依赖 | `:111 versionRange="[9,)"` | 对应行需单独查（本表未逐一核）⇒ **未验证** | 落 forge 时 grep `curios` 在自身 toml 确认。 |
| 前置库 starengine | `:142 modId="starengine"`、`:144 versionRange="[${starengine_version},2.0.0)"`；`:133-135` 注释说明「不内置，走 mavenLocal」 | `:156 modId="starengine"`、`:158 versionRange="[${starengine_version},2.0.0)"` | **已核**。两线区间写法相同，版本号来自各自 `gradle.properties` 的 `starengine_version`。 |
| 模板展开机制 | 模板位于 `src/main/templates` ⇒ 由构建插件展开；`processResources`/`expand` 在 `neoforge-1.21.1/build.gradle` 与 `forge-1.20.1/build.gradle` 里 grep **无命中** | 同 | **未验证**（只证明 toml 在 `templates/` 下、且构建脚本里没有显式 `expand`；展开由 ModDevGradle/Legacy 插件的默认行为完成，未做对照实验）。 |

### 9.2 构建系统与工具链

| 项 | neoforge-1.21.1 | forge-1.20.1 | 说明 |
|---|---|---|---|
| Gradle 插件 | `neoforge-1.21.1/build.gradle:3 id 'net.neoforged.moddev' version '2.0.141'` | `forge-1.20.1/build.gradle:5 id 'net.neoforged.moddev.legacyforge' version '2.0.144'`（`:4` 注释：「ModDevGradle Legacy：支持 Forge ≤1.20.1（与 1.21.1 分支的 net.neoforged.moddev 同族，统一构建 UX）」） | **已核**。同一族插件，**任务名与产物目录不同**。 |
| Java toolchain | `build.gradle:63 java.toolchain.languageVersion = JavaLanguageVersion.of(21)` | `build.gradle:63 ... of(17)` | **已核**。跨线 copy 代码时注意 17 不能用 21 的语法（record pattern、sealed 等都别用）。 |
| 映射 | Mojmap（编译与运行一致），构建脚本 `build.gradle:203` 注释：「NeoForge 编译/运行同为 Mojmap，无需 refmap`、`:208` 引 `org.spongepowered:mixin:0.8.5` | SRG（生产）+ Mojmap（dev）；`build.gradle:147` 注释：「refmap 只有一份 astral_dice.refmap.json（Mixin AP 合并全部引用生成），jade 配置也写同一个 refmap 名` | **已核（注释级）**。⇒ forge 侧**任何** mixin 都要有 refmap，否则生产环境注入失败而 dev 正常。 |
| mixin 配置数量 | 3 个 json：`astral_dice.mixins.json`、`astral_dice.jade.mixins.json`、`astral_dice.neoforge_fixes.mixins.json` | 2 个 json：`astral_dice.mixins.json`、`astral_dice.jade.mixins.json`（**无 neoforge_fixes**） | **已核**。移植 neoforge_fixes 里的 mixin 前先判断 forge 是否需要。 |
| dev 运行参数 | 无 `-Dmixin.config` 手工参数（插件自动） | `build.gradle:86-91`：dev run 加 `-Dmixin.config=astral_dice.mixins.json`、`-Dmixin.config=astral_dice.jade.mixins.json`、`-Dmixin.debug.export=true` | **已核**。新增 mixin 配置时 forge 侧要同步加 `-Dmixin.config`（否则 dev 不加载）。 |
| 前置库解析 | `mavenLocal()` 优先（`build.gradle` 注释：库先「publishToMavenLocal」） | 同 | **已核**。 |

### 9.3 版本号与产物

| 项 | neoforge-1.21.1 | forge-1.20.1 |
|---|---|---|
| 平台版本 | `gradle.properties:21 neo_version=21.1.235` | `gradle.properties:19 forge_version=1.20.1-47.4.10` |
| mod 版本 | `gradle.properties:37 mod_version=1.5.0-rc.4+neoforge_1.21.1` | 对应行带 `+forge_1.20.1` 后缀（**未验证**该行行号；以 grep `mod_version` 为准） |
| 前置库版本 | `gradle.properties:126 starengine_version=1.1.0`；`:128-137` 注释：2026-09-25 起**不再声明** `starengine_version_range` | 同机制（区间由单量展开）⇒ **未验证** forge 侧是否同样只留单量 |

### 9.4 对齐检查清单（§9）

- [ ] 元数据改动落在 `templates/` 下的对应文件（neo `neoforge.mods.toml` / forge `mods.toml`）
- [ ] forge 依赖用 `mandatory=` 而不是 `type=`
- [ ] 两份 toml 的 `starengine` 区间与 `gradle.properties` 的 `starengine_version` 一致
- [ ] forge 新增 mixin 时：json 注册 + `-Dmixin.config` + refmap 生效
- [ ] 代码在 Java 17 下可编译（没有 21 专有语法）
- [ ] 双线 `:classes` 通过，且没有改构建脚本

---

## §10 测试与探针通道

这一节回答「改完怎么在实机证明」。两线的探针接入方式不同，混用会得到假结果（forge 用了 SRG jar ⇒ 反射/类名全错）。

### 10.1 AutoJS 接入差异

| 项 | neoforge-1.21.1 | forge-1.20.1 | 证据 |
|---|---|---|---|
| 客户端 mod 文件 | `run/1.21.1/mods/AutoJS-neoforge-1.21.1-1.0.0.jar`（包根 `cc.sighs`） | 必须用 devlibs 的 **named jar** `AutoJS-forge-1.20.1-1.0.0-dev.jar`（包根 `com.sighs`）；生产 jar 是 SRG，放进 `run/1.20.1/mods` 会 `NoSuchMethodError` | `docs/features/autojs-testing-flow.md:24`、`:25`、`:119`；`docs/features/autojs-test-harness-spec.md:13`、`:22`、`:75` |
| 依赖接入方式 | 可直接放 `run/…/mods` | 走 `build.gradle` 的 `modImplementation`（即 `build/devlibs`），并受 `tools/check_mod_sources.ps1` 的 `$ModExceptions` 白名单约束 | 同上 |
| 26.1.2 / fabric | P1（不在本文件范围） | 同 | `docs/features/autojs-testing-flow.md:26`、`:27` |

### 10.2 探针可用的断言通道

| 通道 | 用法 | 约束 |
|---|---|---|
| KubeJS 服务端脚本 | 命令注册必须写在 `ServerEvents.commandRegistry` 回调内；执行体提成顶层命名函数并用 `var`；类加载用 `Java.loadClass`（不是 `Java.type`）；失败用 `sendFailure` | `docs/agent/AGENTS-full.md:2860`；**已核** |
| 重载 | 改脚本后 `/kubejs reload server-scripts` + `/reload` | 同上 |
| 注入式命令的延迟 | 命令端到端延迟 **> 2 秒**；短于约 3 秒的状态窗口不能靠「施放后注入读数」取证 | `docs/agent/AGENTS-full.md:498`；**已核** |
| 时间与实体身份 | KubeJS 里 `entity.getUUID()` 与 `entity.level.getGameTime()` **都取不到**（实测退化成 `idx0` 与 `0`，导致 `dur` 恒 0）⇒ 必须服务端 `ServerEvents.tick` 自持 tick 计数器采样、用 `console.info` 落 `latest.log` | 同上；容错 UUID 取值器 `playerUuid(p)` 见 `:3184` |
| 机器行格式 | 探针打印形如 `AP_<TAG>_<NAME>:k=v:…` 的单行，断言只锚这种行 | 本仓探针规范（`docs/batch3/TEST-CMD-audit.md:155` 记录 `/astralprobe` 共 27 个子命令、注册源码 `astral_bugfix_probe.js:3101-3344`） |
| 两线脚本资产 | `scripts/test/resources/kubejs/1.21.1/server_scripts/astral_bugfix_probe.js` 与 `…/1.20.1/…` 有逐字节相同的块 | `docs/agent/AGENTS-full.md:3340`、`:3405` |

### 10.3 映射差异对取证的影响

| 场景 | neoforge-1.21.1 | forge-1.20.1 |
|---|---|---|
| 用 `javap` 看生产 jar | Mojmap（可读名） | **SRG**（`m_xxxxx_`、`f_xxxxx_`）⇒ 断言方法名必须用 dev/named jar，或用 SRG 名 |
| 反射调用模组/原版方法 | 名字与源码一致 | 生产 jar 里名字是 SRG；dev 环境是 Mojmap ⇒ **同一段反射代码在两线行为不同** |
| mixin `method` 目标名 | Mojmap | 生产 SRG，靠 refmap 映射；dev 靠 `-Dmixin.config` + Mojmap |

### 10.4 对齐检查清单（§10）

- [ ] 探针跑的是**对应线**的实例（1.21.1 / 1.20.1 各自 `run/`）
- [ ] forge 侧 AutoJS 用的是 `build/devlibs` 的 named jar，不是生产 jar
- [ ] 断言只看机器行（`AP_…`），不看自然语言日志
- [ ] 状态窗口 < 3 秒的，用 tick 采样器而不是「施放后再注入」
- [ ] 探针脚本两线差异只允许 API 层，机器行格式必须逐字一致（便于同一份 case 复用）
- [ ] 没有探针可覆盖的项，交付里写「无法判定」

---

## §11 高频改写模板

> 用法：找到对应模板 → 复制 neo 片段 → 用「forge 片段」列改写 → 跑该模板的判据。
> 片段是**示意**，具体锚点见对应小节。

### T1 改伤害金额

| | neo（基准） | forge（改写） |
|---|---|---|
| 挂点 | `LivingDamageEvent.Pre` | `LivingDamageEvent`（单类） |
| 代码 | `@SubscribeEvent(priority = EventPriority.NORMAL)` + `onLivingDamagePre(LivingDamageEvent.Pre e)` + `e.setNewDamage(x)` | `onLivingDamage(LivingDamageEvent e)` + 计算后**必须**经 `settleDiceDamage(target, e, x)`（内部 `e.setAmount(...)`） |
| 判据 | `grep -n 'setNewDamage' DiceCombatEvents.java`，命中点在同一条链上只有一个出口 | `grep -n 'event.setAmount(' DiceCombatEvents.java`，除 `settleDiceDamage` 与等价收尾点外不应出现 |

### T2 加「受击时」被动

| | neo | forge |
|---|---|---|
| 挂点 | `LivingDamageEvent.Pre`（要影响本次伤害）或 `.Post`（只读/触发副作用） | 统一 `LivingDamageEvent`，用 `priority` 决定先后 |
| 代码 | `@SubscribeEvent(priority = EventPriority.LOW)` + `if (SpellDamageRegistry.isSpellDamage(source, source.getDirectEntity())) return;`（旧口径的 `isApplyingTrueBonus()` 已于 `047bb89c` 删除） | 同结构，但 `event.getAmount()` 是唯一读数 |
| 判据 | 一次法伤命中，被动触发次数打印到探针（预期 1 次，除非明确接受 2 次） | 同上，且 forge 侧要注意吸收已被扣（`getAmount()` 是扣血值） |

### T3 加数据组件

| | neo | forge |
|---|---|---|
| 声明 | `ModDataComponents` 里加 `DeferredHolder<DataComponentType<?>, DataComponentType<T>>`，`.persistent(CODEC)` + `.networkSynchronized(STREAM_CODEC)` | 同文件加 `ItemDataKey.create("同名字段", CODEC)`（或 `withItemDefault`） |
| 读写 | `stack.set(K.get(), v)` / `stack.getOrDefault(K.get(), fallback)` | `ItemDataKey.set(stack, v)` / `ItemDataKey.getOrDefault(stack, fallback)` |
| 判据 | 服务端写、客户端读，值一致（需同步的组件） | 存档重进后值仍在；跨线同名字段默认值一致 |

### T4 加状态效果

| | neo | forge |
|---|---|---|
| 注册 | `DeferredRegister<MobEffect>` + `DeferredHolder<MobEffect, MobEffect>` | 同结构但持有者 `RegistryObject<MobEffect>` |
| 读取 | `player.hasEffect(ModEffects.X)` | `player.hasEffect(ModEffects.X.get())` |
| 移除 | `ModEffectRemoval.remove(player, ModEffects.X)` | `ModEffectRemoval.remove(player, ModEffects.X.get())` |
| 图标/语言 | 四线图标守门 + 三语 lang | 同 |
| 判据 | 施加/剩余时长/移除三条探针读数；移除必须走内部通道（否则 forge 被拦截） |

### T5 加伤害类型 / 加标签

| | neo | forge |
|---|---|---|
| 数据 | `src/main/resources/data/astral_dice/damage_type/<name>.json` | 同名同路径 |
| 标签 | `src/main/resources/data/minecraft/tags/damage_type/*.json` 加条目 | 同名同路径 |
| 代码 | `ModDamageTypes` 加工厂方法 | 同签名同位置（两线行号应对齐） |
| 判据 | 两线 4 个 tag 文件内容一致（哈希比对）；新类型在两线的 bypass 面相同 |

---

### 对齐检查清单（§11）

- [ ] 已确认改动属于 T1–T5 的某一类；跨类时按最严格的那类做
- [ ] neo 片段已跑通，forge 片段按「forge 写法」列改写（没有直接复制类名）
- [ ] 该模板的判据命令已执行，输出贴进交付
- [ ] 若模板不匹配，已在 §12 补一行并在交付里引用该编号


## §12 已知平台陷阱表

> 一行一条：现象 / 根因 / 对齐写法 / 证据。**未验证**的行单独标注。

| # | 现象 | 根因 | 对齐写法 | 证据 |
|---|---|---|---|---|
| T-01 | forge 的护盾钳制「没生效」，玩家被一击打穿护盾 | `RenShieldManager` 挂在 `LivingHurtEvent`，骰战随后在 `LivingDamageEvent` 用终值覆盖 | 钳制搬进 `settleDiceDamage` | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/RenShieldManager.java:416` vs `combat/DiceCombatEvents.java:365`、注释 `:366-368` |
| T-02 | forge 吸收被扣两次，吸收池出现负值 | 原版 `LivingEntity.java:1684` 与 `:1669-1670` 各扣一次，叠加骰战自己的补偿 | 按段补偿 + 在 `LivingDamageEvent` LOWEST 收尾；不改原版 | `forge-1.20.1/.../combat/DiceCombatEvents.java` 的 `registerPhantom` 段（批次 F）+ 审计 P1-1 |
| T-03 | neo 能取消 `MobEffectEvent.Expired`，forge 写了 `setCanceled` 编译错/无效 | forge 的 `Expired` 不是 `@Cancelable` | 改用 `MobEffectEvent.Remove` 拦截 | sources jar 两侧 `MobEffectEvent.java`（neo `implements ICancellableEvent`；forge 注释 `not Cancelable`、`Remove` 在 `:46` 带 `@Cancelable`） |
| T-04 | 给吸收做「补偿式」修法时算错段数 | ① 法伤已折叠为**单次** hurt（2026-10-27），旧的「两次 hurt、每段各扣一次」口径作废；② 更危险的是把 `LivingEntity.java:1684` 的补偿逻辑套到**玩家**身上 —— `Player#actuallyHurt`（`Player.java:940-965`）没有那一步 | 数值型改动优先写在 `settleDiceDamage` 出口；玩家与非玩家必须分叉（见 F1） | 审计 §7.8 + §7.9.1；`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:374-416` |
| T-05 | forge 编译报 `incompatible types: DeferredHolder … RegistryObject` | 持有者类型不同 | forge 侧一律 `.get()`，或把变量类型改成 `RegistryObject` | `effect/ModEffects.java:34`（两线） |
| T-06 | 效果「删不掉」 | 直接 `removeEffect` 被「阻止移除」逻辑拦回 | 走 `ModEffectRemoval` | `command/AstralPartyCommand.java:623-624`（forge 注释）；neo `:625` / forge `:635` |
| T-07 | forge 编不过 `consume` | 1.20.1 无 `ItemStack#consume` | `if (!player.isCreative()) shrink(1)` | sources jar `ItemStack.java`：neo `:1089` 有 `consume`，forge 无 |
| T-08 | 物品「明明同一种」却判定不等 | 1.21 比组件、1.20.1 比 NBT | 按线用 `isSameItemSameComponents` / `isSameItemSameTags` | `item/card/BaseEffectCardItem.java:346`(neo) / `:345`(forge) |
| T-09 | forge 属性修饰符抛 `IllegalArgumentException` | forge 第 1 参是 `UUID`，效果里要 UUID 字符串 | 用 `attributeModifierId(...)` 的 forge 版本；字符串必须是合法 UUID | `event/EventEffects.java:76`(forge) / `:75`(neo)；`effect/DorothySocialiteEffect.java:15`(注释) |
| T-10 | 以为两线公式不同 | Operation 改名（`ADD_VALUE`↔`ADDITION` 等） | 只换枚举名，不重写公式 | `DiceCombatModifiers.java:1004`(neo) / `:1002`(forge) |
| T-11 | 客户端读不到组件值 | 1.21 忘了 `networkSynchronized` | 按线实现（neo 补同步，forge 靠 NBT） | `component/ModDataComponents.java:23`(neo) / `:13-15`(forge 注释) |
| T-12 | forge 生产环境 mixin 没注入（dev 正常） | 生产是 SRG，缺 refmap | 新 mixin 必须进 `mixin { config(...) }` 且 refmap 生效 | `forge-1.20.1/build.gradle:142-151`、`:147`；`neoforge-1.21.1/build.gradle:203` |
| T-13 | 代码在 neo 能编，forge 报 `source level 17` | toolchain 21 vs 17 | 不用 21 专有语法 | 两线 `build.gradle:63` |
| T-14 | forge 启动报 `fml.language.missingversion` | 手写 「loaderVersion」 区间 | 用模板变量 `forge_language_version_range` | `forge-1.20.1/src/main/templates/META-INF/mods.toml:5`、`:7` |
| T-15 | 探针读到 `dur=0` / `idx0` | KubeJS 里 `getUUID()`/`getGameTime()` 取不到 | 服务端 `ServerEvents.tick` 自持计数器 | `docs/agent/AGENTS-full.md:498` |
| T-16 | forge 探针一调用就 `NoSuchMethodError` | 用了 SRG/生产 jar | 用 `build/devlibs` 的 named jar | `docs/features/autojs-testing-flow.md:119`、`docs/features/autojs-test-harness-spec.md:13`（文件名以实际为准） |
| T-17 | 两线数值不同但代码「看起来一样」 | 事件落点不同（`Pre` vs 单类），或读的基数是吸收前/后 | 按 §1.2 决定落点；用探针同时读「本体段」与「加成段」 | 见 §1.1 表 |
| T-18 | 新增伤害类型后某线没生效 | 只在一侧加 json / 加标签 | 两线同路径同内容；哈希比对 | §8.1、§8.6 |

**未验证条目统计**：全表 18 行里只有 T-02 的**修法细节**属未实施建议（不是事实陈述）；其余 17 行的证据均已在当前工作树实读（T-16 引用的两个文档 `docs/features/autojs-testing-flow.md`、`docs/features/autojs-test-harness-spec.md` 均已确认存在）。

---

### 对齐检查清单（§12）

- [ ] 本次改动涉及的每条陷阱都已在交付里引用编号（T-xx）
- [ ] 新增陷阱已按「现象/根因/对齐写法/证据」四列补齐，且证据是两线各自的 file:line
- [ ] 表里标「未验证」的行没有被当作设计依据

## §13 未验证与待补

### 13.1 未验证清单（落码前必须补证据）

| # | 条目 | 为什么没核 | 怎么补 |
|---|---|---|---|
| U-01 | `templates/` 的展开机制（谁做替换） | 两条 `build.gradle` 里 grep `processResources`/`expand` 无命中 | 读 ModDevGradle 2.0.141 / legacyforge 2.0.144 文档，或做一次改值构建对照 |
| U-02 | 注释/文档里还剩多少失效行号（实例：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/command/AstralPartyCommand.java:623-624` 仍指 `event/ModEffectEvents.java:107-127`，实际 handler 在 `:19`） | 只核了这一处 | 全仓扫「注释里带 `文件:行号`」的段落，逐条比对 |
| U-03 | forge `bus = Mod.EventBusSubscriber.Bus.MOD` 的实际写法 | 本仓当前无该类用例 | 新增一个 MOD 总线订阅者时实测 |
| U-04 | 1.20.1 全仓是否还有「裸 removeEffect」漏网 | 未全量扫 | `grep -rn 'removeEffect(' forge-1.20.1/src/main/java` |
| U-05 | `isSameItemSameTags` 与 `isSameItemSameComponents` 在「带杂项 NBT 的第三方物品」上的差异 | 本仓物品都是自有 NBT | 用带 NBT 的第三方物品做对照 |
| U-06 | 组件值可变性要求 | 无反面用例 | 查 1.21 组件规范 |
| U-07 | `starengine_version_range` 在 forge `gradle.properties` 是否也已移除 | 只读了 neo 的注释 | `grep -n 'starengine' forge-1.20.1/gradle.properties` |
| U-08 | curios 依赖在 forge toml 的行号与区间 | 未逐一核 | `grep -n -i curios forge-1.20.1/src/main/templates/META-INF/mods.toml` |
| ~~U-09~~ | ~~法伤链的「两次 hurt」在 forge 侧对吸收池的实际分配顺序~~ | **已作废**：折叠为单次结算后不存在两段；且玩家路径的吸收分配已按 `Player.java:946-947` 静态核清（只剩实机读数待做） | 若要补读数：探针比对「黄心终态」与 `A0 - min(F, A0)` |
| U-10 | `settleDiceDamage` 的 `registerPhantom` 与「幻影吸收 H」的完整语义 | 批次 F 新增，仅读到片段 | 通读该文件相关方法 |

### 13.2 行号漂移


**成文时工作树状态（重要）**：定稿瞬间 `git status --short -uall` 显示两线仍有 **14 个源码文件处于 M（未提交）** 状态 —— `forge-1.20.1` 与 `neoforge-1.21.1` 各 7 个：`combat/DamageNumberAggregator.java`、`combat/DeathTriggerGuard.java`、`combat/DiceCombatEvents.java`、`combat/LivingPageImpact.java`、`event/DamageEffectCardHandler.java`、`event/LinglingSignEvents.java`、`item/InvestigationEventUtil.java`。这些是**并行批次正在改的同批文件**，本文行号取的就是这个中间态；等批次落地/提交后请按锚点复核，别按行号硬跳。
本文所有行号都是 **2026-10-27 工作树** 的快照。两线在本批次内有大量改动（N1/N2/N5/N7/N8 + 幻影吸收），行号会继续漂移。**复核纪律**：用每条的「锚点」（方法名/常量名/注释里的原话）grep，而不是直接跳到行号。

### 13.3 维护规则

1. 每次移植时发现**表里没有**的差异：先在 §12 加一行（含两线 file:line），再写代码。
2. 每条 §12 新增行都要在交付里被引用（说明「这次踩的是 T-xx」）。
3. 26.1.2 / fabric 的 mapping 另建文件，不往本文里塞。

---

### 13.4 对齐检查清单（§13）

- [ ] 本次交付里，凡是取不到证据的结论都写了「无法判定」，没有脑补
- [ ] 新增的未验证项已登记进 13.1（含「怎么补」）
- [ ] 引用行号前用锚点 grep 复核过（13.2 的纪律）
- [ ] 没有往本文塞 26.1.2 / fabric 的内容（13.3 第 3 条）


## 附录 A 本文件的自检

| 检查 | 值 |
|---|---|
| 编码 / 行尾 | UTF-8 + CRLF（bareLF = 0） |
| 章节 | §0–§13，共 14 节，全部含「表格 + 对齐检查清单」 |
| 证据形式 | 每条差异挂两线各自的仓库根相对路径 `文件:行号`（或 sources jar 路径 + 行号） |
| 已核 / 未验证 | 全部差异行标了「已核」或「未验证」；未验证清单见 §13.1（10 条） |
| 规模 | 602 行；行尾 CRLF 601 处、bareLF = 0；字节数 ≈ 62.8 KB |
| 批次新增标记 | §1.1（forge `onLivingHurtRecordAbsorption`）、§1.2（「settleDiceDamage 注释」与幻影吸收）已标「批次新增」 |

## 附录 B 修订记录

| 版本 | 日期 | 内容 |
|---|---|---|
| v1 | 2026-10-27 | 初版：以 neoforge-1.21.1 为基准建 14 节 mapping；证据取自工作区源码 + 两侧 moddev sources jar；标注 10 条未验证 |
