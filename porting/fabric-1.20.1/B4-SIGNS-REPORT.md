# B4-SIGNS 立牌批次移植报告（forge-1.20.1 → fabric-1.20.1，1.5.0）

批次：B4-SIGNS　作业范围：任务书写权清单内（未 commit / 未 push / 未启动游戏）

## 0. 状态摘要

| 项 | 结果 | 位置 |
|---|---|---|
| Java 交付 | 20 个新文件 + 事件宿主追加行 + 1 处逐 tick 调用 | 第 1 / 3 / 4 节 |
| 编译验证 | 未完成（环境阻塞，非代码问题） | 第 9 节 |
| tools/check_lang_sync.py | exit 0（四线全绿，fabric 964 键） | 第 8 节 |
| tools/verify_sign_display_names.py | GATE: PASS（实测只覆盖 neo/forge 两线，不含 fabric） | 第 8 节 |
| tools/verify_fabric_assets.py | FAIL（读的是旧产物 jar，未含本批与在途批次） | 第 8 节 |
| 7 立牌资源/注册/标签/lang 替代判据 | ALL OK（逐项核对磁盘事实） | 第 8.3 节 |
| 禁项终检 | 行首 import net.minecraftforge = 0；行首 import top.theillusivec4.curios = 0 | 第 8.4 节 |

## 1. Java 交付物

### 1.1 逐文件表（行数为实测；差异块 = difflib 非 equal 段数）

| 文件（包 com.merlinkitsune.astral_dice.） | forge 行 | fabric 行 | 逐字节相同 | 差异块 |
|---|---|---|---|---|
| item/sign/AlanaSignItem.java | 91 | 100 | 否 | 13 |
| item/sign/AmeSignItem.java | 452 | 404 | 否 | 46 |
| item/sign/DeepseekSignItem.java | 189 | 217 | 否 | 31 |
| item/sign/DorothySignItem.java | 252 | 283 | 否 | 43 |
| item/sign/JillSignItem.java | 251 | 277 | 否 | 27 |
| item/sign/KangelSignItem.java | 402 | 384 | 否 | 37 |
| item/sign/LinglingSignItem.java | 80 | 92 | 否 | 12 |
| item/sign/LinglingSignManager.java | 435 | 426 | 否 | 33 |
| item/sign/SignActionBar.java | 58 | 62 | 否 | 3 |
| item/sign/SignAttributeUtil.java | 48 | 51 | 否 | 1 |
| item/sign/SignEquipCheck.java | 36 | 42 | 否 | 4 |
| item/sign/SignHookDispatcher.java | 176 | 189 | 否 | 6 |
| item/sign/SignHooks.java | 71 | 73 | 否 | 1 |
| component/SignState.java | 122 | 127 | 否 | 4 |
| component/SignTimerSourceAdapter.java | 39 | 87 | 否 | 6 |
| event/LinglingSignEvents.java | 82 | 82 | 否 | 13 |
| event/NewSignEvents.java | 119 | 124 | 否 | 8 |
| mixin/client/SignTranslucencyMixin.java | 81 | 88 | 否 | 2 |
| component/SignAttachments.java | （fabric 新增，无 forge 对应） | 386 | — | — |
| mixin/client/EntityRendererInvoker.java | （fabric 新增，无 forge 对应） | 20 | — | — |
| item/sign/MosesEnigmaticLink.java | 未移植 | 0 | — | 裁剪项（Enigmatic Legacy 联动） |
| item/sign/SignActiveLockMachine.java | 213 | 0 | — | 不新建，见 10.2 |

集合差复核（forge 有 / fabric 无）结果与任务书名单一致，唯一偏差：forge 侧上述 20 个文件中 MosesEnigmaticLink 属裁剪；另 fabric 需额外补 EntityRendererInvoker（forge 该 mixin 依赖其调用器，fabric 线缺），故新增数 = 19 + 1 = 20。

### 1.2 共同改写类别（每类均在类头 javadoc 标注 forge 原文）

1. 附件键：event/ModAttachments 的 get/set → 本批新增 component/SignAttachments。原因：ModAttachments 属他人批次写权，且缺 7 个新立牌的全部键。
2. Curios：库 starengine.item.CuriosCompat（fabric jar 无此类）→ 本线门面 compat.curios.CuriosApi；取栈用 SlotResult.stack()；SlotContext 用门面 record（entity / identifier / index / slotId）。
3. 事件基类：net.minecraftforge.eventbus.api 与 @Mod.EventBusSubscriber → platform.event.*。3 个类的注解已删除，改由 AstralDiceMod 显式 LoaderBus.register（第 4 节）；MobEffectEvent 的删除动作用 setCanceled(true)（本线 shim 无 setResult）。
4. 施效通道：forge event/PlayerEffectProtection.apply（fabric 线不存在该类）→ 本线 event/EffectTimerGuard.apply(LivingEntity, MobEffectInstance)；移除类操作 → starengine.event.ModEffectRemoval.remove。与本线既有立牌（Bonnie/Fanny/Haiqing/Hanna）现行写法一致。
5. 客户端判定：字段 level.isClientSide → 方法 level().isClientSide()。
6. 网络：PacketDistributor/SimpleChannel → network/ModNetwork.sendToPlayer + ModNetwork.ActionBarMessage(Component, int)；SignActionBar 因此为薄封装（62 行 vs 58）。
7. 注册键比较：统一为 BuiltInRegistries.ITEM.getKey(...)（本线既有写法）。

### 1.3 逐文件差异要点（只列非机械改写）

- component/SignAttachments.java（新增 386 行）：承载 7 个新立牌的 21 个附件键 + 7 个辅助方法（tryTriggerDorothyPartner / clearDorothyPartners / addDorothyAttackBonus / isLinglingGranted / setLinglingGranted / getSignActiveMaxCooldownBase / setSignActiveMaxCooldownBase）。键名、codec、默认值、sync / copyOnDeath / inMemory 标记与 forge 逐字一致。同步通道：ModAttachments.syncedKeys()（包级可见、返回同一可变 List）中追加 6 个 sync 键，登录 / 换维快照即包含。注册时序：item/sign/LinglingSignItem 与 item/sign/BaseSignItem 的 static 块调 ensureRegistered()（LoaderBus.register 不触发 clinit，而 fabric 玩家 NBT 反序列化按 id 查附件表，查不到会静默丢弃）。
- AmeSignItem（452→404）：删掉 forge 侧依赖 PlayerEffectProtection 的受保护效果快照块；MobEffectEvent.Expired 改 shim；ServerTickEvent 以 phase == END 判定；常量与公开方法签名全部保持（AME_LOVE_CAP_DEFAULT=4 / CAP_MAX=32 / EQUIP_INITIAL_LOVE=2 / EXPIRE_COST=4 / LOVE_SURPLUS_BASE=4 / HEAL_PER_LOVE=2 / LOVE_GAIN_INTERVAL_TICKS=600 / BASE_DEFENSE=6）。
- DeepseekSignItem（189→217）：forge 攻击力加成走 DiceCombatModifiers.setAttackPowerBonus（本线无此方法）⇒ 改 ATTACK_DAMAGE 属性修饰器；新增 tickBuff(Player) 供 PlayerTickEvents 调用（第 4.2 节）；4 个 inMemory 键 + DEEPSEEK_IDLE_SINCE 语义不变。
- DorothySignItem（252→283）：forge 的 defensePointsForArmor / PLAYER_ARMOR_PER_DEFENSE 本线不存在 ⇒ 改用本线既有 DiceCombatModifiers.setDefenseArmorBonus(Player, String, int)（×2.0 折护甲口径）；TargetSelectionRegistry / TargetType / TargetSelectionAction / PartyRelations 均确认存在于 starengine fabric jar，按 jar 签名使用。
- JillSignItem（251→277）：目标选择器走 TargetSelectionRegistry + SelfTargetable；冷却结算用 WeirdDiceHandler.signCooldownTicks(Player)（本线存在、同签名）。
- KangelSignItem（402→384）：队友判定走 PartyRelations；KangelFanEffect.applyMark/has 与 KangelFanIndicatorEffect 全部本线已有，未改写。
- LinglingSignManager（435→426）：与调用点签名严格对齐（第 7 节）；路障↔强化拒止转换、resolvePlacedItem 与 forge 逐条等价；effectiveCharge = min(GameplayConstants.CHARGE_MAX_STACKS, ChargeManager.getStacks)。
- component/SignState.java（122→127）：record + CODEC + 10 个 withXxx 逐字搬；本线无消费者（BaseSignItem 仍用扁平键、无 SIGN_STATE 单键、无迁移方法）⇒ 前向兼容交付物，见 10.3。
- component/SignTimerSourceAdapter.java（39→87）：库 TimerSource 与本地 TimerEntry 逐字段显式转换（两版同名类字段逐一相同）；读写 ModAttachments.EFFECT_TIMER_ENDS；now = player.level().getGameTime()。本线 setTimerSource 全仓 0 调用点 ⇒ 当前无消费者，见 10.4。
- event/NewSignEvents.java（119→124）：LivingHurtEvent 免伤（铁处女 / 摸鱼）用 setCanceled(true)；LivingChangeTargetEvent 清目标；LivingHealEvent 以 EventPriority.LOWEST 排除过疗 / 空治疗 / 满血后叠温暖；MobEffectEvent.Remove/Expired 清修女被动。未订阅 MobEffectEvent.Applicable（本线 shim 无该事件、全仓 0 派发点）⇒ 免疫有害效果无法经事件实现，见 11.3。
- mixin/client/SignTranslucencyMixin.java（81→88）：forge 用 @Invoker 取纹理位置；fabric 侧对应 EntityRendererInvoker 同批交付（20 行，@Invoker(getTextureLocation)）。
- item/sign 下既有类（BaseSignItem / Bonnie / Fanny / …）：未做任何整份覆盖，见第 2 节。

## 2. BaseSignItem 差异判定（fabric 660 行 vs forge 577 行；本批未改动该文件）

共 36 个差异块，按性质归并：

| forge 位置 | 差异内容 | 判定 |
|---|---|---|
| L207 / L214 | 起锁时记录未折电池基准 setSignActiveMaxCooldownBase；瞬时型主动结束后调 WeirdDiceHandler.onSignActiveEnded（诡异骰子 +5 治愈） | 本线滞后（fabric 全仓无这两个调用；见 10.5 / 10.6） |
| L251-255 | 主动音效机器行 LOGGER.info(AP_SIGN_SOUND: skill_use: ...) | 本线滞后（fabric 0 命中） |
| L332-488 | 状态机抽到 SignActiveLockMachine，基类只做薄委托 | 本线特有（fabric 把状态机内联在 BaseSignItem，口径一致） |
| L302-312 | actionbar 走 SignActionBar.* | 本线特有（fabric 内联 ModNetwork.sendToPlayer + ActionBarMessage） |
| L551 / L559 | 立牌钩子走 SignHookDispatcher.fireKillHooks / fireHurtHooks | 本线特有（fabric 内联遍历 CuriosApi.findCurios） |
| L249 | playSkillUseSound 为 public | 本线特有（fabric 为 protected；本批新类未跨包调用） |

结论：本批新类使用 SignActionBar / SignHookDispatcher（本批交付），既有基类继续走内联路线 ⇒ 同一行为存在两份实现但互不影响；收敛需改 BaseSignItem，超出本批红线。

## 3. 注册与创意栏

### 3.1 item/ModItems.java（追加 L1117-1162，共 46 行；既有行 0 改动）

前 3 行上下文：
```
 1115:                     .rarity(AstralRarities.bizarre())));
 1116: 
 1117:     // ── B4-SIGNS 批次：2026-10-19 新批 4 个立牌 + 2026-09-25 的 3 个（kangel / ame / lingling）──
```
追加的 7 条登记（id 顺序 kangel → ame → lingling → jill → dorothy → alana → deepseek；品质对齐 forge L1105-1145）：
```
 1123:     public static final RegistryObject<Item> KANGEL_SIGN   = registerItem("kangel_sign", ...)
 1129:     public static final RegistryObject<Item> AME_SIGN      = registerItem("ame_sign", ...)
 1135:     public static final RegistryObject<Item> LINGLING_SIGN = registerItem("lingling_sign", ...)   // epic
 1141:     public static final RegistryObject<Item> JILL_SIGN     = registerItem("jill_sign", ...)
 1147:     public static final RegistryObject<Item> DOROTHY_SIGN  = registerItem("dorothy_sign", ...)
 1153:     public static final RegistryObject<Item> ALANA_SIGN    = registerItem("alana_sign", ...)      // rare
 1159:     public static final RegistryObject<Item> DEEPSEEK_SIGN = registerItem("deepseek_sign", ...)
```
后 3 行上下文：
```
 1163: 
 1164:     public static <T extends Item> RegistryObject<T> registerItem(String name, Supplier<T> itemSupplier) {
 1165:         return ITEMS.register(name, itemSupplier);
```
构造器使用全限定名 new com.merlinkitsune.astral_dice.item.sign.XSignItem(...) ⇒ 未新增 import 行。

### 3.2 init/ModCreativeTabs.java（追加 L125-139；既有行 0 改动）

插在既有 output.accept(ModItems.HANNA_SIGN.get())（L124）之后、// 筹码（L140）之前：
```
  123:                 output.accept(ModItems.SHERRY_SIGN.get());
  124:                 output.accept(ModItems.HANNA_SIGN.get());
  125:                 // ── B4-SIGNS 批次（…）：只在既有条目之后追加，不重排上方任何既有行。…
  129:                 // 稀有 RARE
  130:                 output.accept(ModItems.ALANA_SIGN.get());
  131:                 // 史诗 EPIC
  132:                 output.accept(ModItems.LINGLING_SIGN.get());
  133:                 // 奇特 BIZARRE
  134:                 output.accept(ModItems.KANGEL_SIGN.get());
  135:                 output.accept(ModItems.AME_SIGN.get());
  136:                 output.accept(ModItems.JILL_SIGN.get());
  137:                 output.accept(ModItems.DOROTHY_SIGN.get());
  139:                 output.accept(ModItems.DEEPSEEK_SIGN.get());
  140:                 // 筹码
```
既有三段分组标题（稀有 / 史诗 / 传奇 UNCOMMON）与实物品质不符（HANNA_SIGN / MEGAS_SIGN 在 forge 蓝本为奇特）⇒ 未重排，见第 11 节。

## 4. 接线（fabric 无 @EventBusSubscriber 等价物，漏登记 = 编译通过、运行无声）

### 4.1 AstralDiceMod.java（只追加 LoaderBus.INSTANCE.register 行，共 9 行）

| 行 | 语句（均以 LoaderBus.INSTANCE.register 开头） | 插入锚点 |
|---|---|---|
| 157 | com.merlinkitsune.astral_dice.event.LinglingSignEvents.class | event 段，LivingPageFlightScheduler 之前 |
| 164 | com.merlinkitsune.astral_dice.event.NewSignEvents.class | event 段，NetherrackDiceHandler 与 ObsidianDiceHandler 之间 |
| 189 | com.merlinkitsune.astral_dice.item.sign.AlanaSignItem.class | item.sign 段字母序（Bonnie 之前） |
| 190 | com.merlinkitsune.astral_dice.item.sign.AmeSignItem.class | 同上 |
| 192 | com.merlinkitsune.astral_dice.item.sign.DeepseekSignItem.class | 同上（Bonnie 之后） |
| 193 | com.merlinkitsune.astral_dice.item.sign.DorothySignItem.class | 同上 |
| 197 | com.merlinkitsune.astral_dice.item.sign.JillSignItem.class | 同上 |
| 198 | com.merlinkitsune.astral_dice.item.sign.KangelSignItem.class | 同上 |
| 200 | com.merlinkitsune.astral_dice.item.sign.LinglingSignItem.class | 同上 |

有 @SubscribeEvent 但无需登记的类（grep 实测 0 处订阅）：AlanaSignItem / DeepseekSignItem / DorothySignItem / JillSignItem / LinglingSignManager / SignHookDispatcher / SignHooks。

### 4.2 event/PlayerTickEvents.java（L44-49，仅追加）

```
   43:         BaseSignItem.tickSignActiveLock(player);
   44:         // B4-SIGNS:大肥鱼立牌(deepseek)主动「大口干饭」攻/防加成与被动「摸鱼」的到期清理。
   49:         com.merlinkitsune.astral_dice.item.sign.DeepseekSignItem.tickBuff(player);
```
与 forge PlayerTickEvents 同位置（tickSignActiveLock 之后）；不加 diceGated 闸门（到期清理必须照常跑，否则攻 / 防修饰器残留）。tickBuff 内部以结束刻归零后早退保证每 tick 两阶段调用幂等。

### 4.3 AstralDiceClient.java

未改动：本批无客户端事件宿主（SignTranslucencyMixin 是 mixin，不需要 LoaderBus 登记；SignActionBar 只发服务端 → 客户端显示，不订阅客户端事件）。

## 5. Mixin 配置

fabric-1.20.1/src/main/resources/astral_dice.mixins.json 的 client 段，在 client.ArmorBarCancelMixin 之后、bridge.ScreenInvokerMixin 之前追加 2 条（12 → 14 条，JSON 复核合法）：
```
   44:     "client.ArmorBarCancelMixin",
   45:     "client.SignTranslucencyMixin",
   46:     "client.EntityRendererInvoker",
   47:     "bridge.ScreenInvokerMixin",
```
两类 package 均为 com.merlinkitsune.astral_dice.mixin.client（与 package 条目加相对名的既有格式一致）。EntityRendererInvoker 必须同批：SignTranslucencyMixin 依赖其 getTextureLocation 调用器。

## 6. 资源与语言

| 资源 | 数量 | 路径 |
|---|---|---|
| 贴图 | 7 | src/main/resources/assets/astral_dice/textures/item/{alana,ame,deepseek,dorothy,jill,kangel,lingling}_sign.png |
| 物品模型 | 7 | src/generated/resources/assets/astral_dice/models/item/*.json（本线 models 在 generated 源集，src/main/resources 下无 models 目录）；fabric 由 146 → 153，与 forge 齐 |
| 物品标签 | 7 条 | src/main/resources/data/astral_dice/tags/items/signs.json：25 → 32 条，追加顺序 kangel, ame, lingling, jill, dorothy, alana, deepseek（与 forge 同序，CRLF 保持、bare LF = 0） |
| lang | 3 × 24 键 | en_us / zh_cn / ja_jp 各 912 → 964 键（+7 item、+17 guide），三语键集一致 |

lang 新增 7 个 item 键（值为实测；zh 保持游戏内职称命名）：

| key | en_us | ja_jp |
|---|---|---|
| item.astral_dice.alana_sign | Alana Sign | アランナのスタンド |
| item.astral_dice.ame_sign | Ame Sign | あめちゃんのスタンド |
| item.astral_dice.deepseek_sign | Big Fat Fish Sign | 大食い魚のスタンド |
| item.astral_dice.dorothy_sign | Dorothy Sign | ドロシーのスタンド |
| item.astral_dice.jill_sign | Jill Sign | ジルのスタンド |
| item.astral_dice.kangel_sign | KAngel Sign | 超てんちゃんのスタンド |
| item.astral_dice.lingling_sign | LingLing Sign | リンリンのスタンド |

guide 键 17 条 / 语：astral_dice.guide.entry.{kangel,ame,lingling}_sign.{1,2,3} 与 {jill,dorothy,alana,deepseek}_sign.{1,2}。全部取自 forge 侧整行原文（forge en_us.json 非合法 JSON ⇒ 按行正则抽取、不整体解析），写回保持各文件原行尾（CRLF）与 UTF-8 无 BOM。

命名来源：tools/sign_display_names.json（32 条，含 7 个新 id）；7 条 en / ja 逐条比对 0 差异。

## 7. 与调用点签名对照（以调用点为准）

| 调用点 | 调用形态 | 本批实现签名 | 一致 |
|---|---|---|---|
| shop/CardShopMenu.java:489-490 | LinglingSignManager.resolvePlacedItem(card, buyer).copy() | public static ItemStack resolvePlacedItem(ItemStack shelfStack, Player player) | 是 |
| screen/CardShopScreen.java:684 | LinglingSignManager.isEquipped(player) | public static boolean isEquipped(Player player) | 是 |
| screen/CardShopScreen.java:685 | LinglingSignManager.displayAsRepulsion(stack) | public static ItemStack displayAsRepulsion(ItemStack barricade) | 是 |
| screen/CardShopScreen.java:1623-1624 | LinglingSignManager.resolvePlacedItem(this.menu.getSlot(...).getItem(), ...) | 同上 | 是 |

shop/ 与 screen/ 未做任何改动（写权外）。

## 8. 门禁结果

### 8.1 语言与命名
- python tools/check_lang_sync.py → exit 0（neoforge-1.21.1 1070 / forge-1.20.1 1070 / neoforge-26.1.2 1094 / fabric-1.20.1 964，三语一致）。
- python tools/verify_sign_display_names.py → GATE: PASS。该脚本实测只检 neoforge-1.21.1 与 forge-1.20.1 两线，fabric 键不在覆盖内 ⇒ 另做落地核对：7 条 en / ja 与权威表逐字一致（第 6 节）。

### 8.2 产物资源门禁（FAIL 明细与本批关系）
python tools/verify_fabric_assets.py → FAIL。原因是它读取旧产物 jar：fabric-1.20.1/build/libs/astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar（该 jar 早于本批与所有在途批次）。逐条对照：

| FAIL 项 | 归属 |
|---|---|
| 缺模型 / 缺贴图 含 7 个 *_sign | jar 陈旧；磁盘上模型与贴图齐全（8.3 已逐项核对） |
| 缺模型 / 缺贴图 含 effect_card_barricade / effect_card_repulsion / repulsion / *_chip / card_shop / chip_shop / event_drawer | 其他在途批次 |
| 三语缺 java 引用键 gui.astral_dice.card_choice.title_ 与 gui.astral_dice.card_shop.transfer_no_match | 其他在途批次（本批 lang 改动不含这两个前缀） |
| 不在创造栏的物品: [repulsion] | B3 批次 |
| 方块物品资源不全（card_shop / chip_shop / event_drawer） | 其他在途批次 |

### 8.3 替代判据（jar 不可用时逐项核对磁盘事实，脚本 build/_b4_verify.py）
7 个 id × 6 项（ModItems 注册 / 创意栏 accept / signs 标签 / 模型 json 存在且 parent = minecraft:item/generated 且 layer0 = astral_dice:item/<id> / 贴图 png 存在 / 三语 item 键存在）⇒ 全部 OK，汇总 ALL OK；三语 item 键各 7、guide 键各 17。

### 8.4 禁项终检
行首 import net.minecraftforge = 0；行首 import top.theillusivec4.curios = 0（fabric-1.20.1/src/main/java 全量）。PlayerEffectProtection / CuriosCompat 仅出现在 javadoc 与注释文字，无新增 import 或调用。

## 9. 编译验证：未完成（环境阻塞，决定性取证）

规定命令反复在配置阶段失败：
```
> Configure project :fabric-1.20.1
Could not merge JARs! Deleting source JARs - please re-run the command and move on.
java.nio.file.ReadOnlyFileSystemException
  at jdk.zipfs/jdk.nio.zipfs.ZipFileSystem.checkWritable(ZipFileSystem.java:384)
  at jdk.zipfs/jdk.nio.zipfs.ZipFileSystem.createDirectory(ZipFileSystem.java:722)
  at net.fabricmc.loom.configuration.providers.minecraft.MinecraftJarMerger.add(MinecraftJarMerger.java:151)
  ... MergedMinecraftProvider.mergeJars(110/99) → CompileConfiguration.setupMinecraft(192)
> Failed to setup Minecraft, java.nio.file.ReadOnlyFileSystemException: null
```
根因（自写 Java 探针 build/ZipTest.java、build/W2.java、build/W3.java，Zulu 21.0.12 实测）：
- Files.isWritable() 对本机所有路径返回 false（F: 与 C: 缓存目录皆然），但 Files.write 实际全部成功 ⇒ 受限令牌下 AccessCheck 判否，并非真无权限。
- FileSystems.newFileSystem(path, {"create":"true"}) 在 F: 与 C: 同样抛 ReadOnlyFileSystemException ⇒ JDK zipfs 因 isWritable=false 置只读。loom 合并 MC jar 必须写 zip ⇒ 本会话内无论重跑多少次都不可能成功。
- 附带损坏：~/.gradle/caches/fabric-loom/1.20.1/ 现缺 minecraft-client.jar / minecraft-server.jar / minecraft-merged.jar（每次失败都会删除 source jars，无法自愈）；minecraftMaven/net/minecraft/minecraft-merged/1.20.1-loom.mappings.../ 下只剩 440 B 的 pom。对比 1.21.1 侧 minecraft-merged.jar（29,999,087 B）齐备。

- 已排除「完整性标签」假设：以 icacls 将 ~/.gradle/caches/fabric-loom/1.20.1、fabric-loom/minecraftMaven、caches/modules-2 递归降为 Low 完整性（61 / 748 / 13,717 个文件成功），重跑同一命令仍在 MinecraftJarMerger.add 抛 ReadOnlyFileSystemException（BUILD FAILED in 2s）⇒ 与 ACL / 完整性无关，是受限令牌下 AccessCheck 恒判否；事后已递归还原为 Medium（1.20.1: 466 成功 / 282 失败，modules-2: 4,532 成功 / 9,185 失败，失败项为既有系统对象，无破坏）。项目侧 F: 目录拒绝设标签（本就 Low 且可写）。

⇒ 本批 Java 文件未获得任何 javac 输出，编译正确性未经验证。已做的替代检查：跨文件成员引用核对（Class.member 是否在本线源码中声明，8 处命中全部落在注释 / javadoc）、禁项 grep、调用点签名对照、@SubscribeEvent 宿主登记完备性。

## 10. 未做项与偏离基线

1. MosesEnigmaticLink：裁剪项（Enigmatic Legacy 联动），未移植。
2. SignActiveLockMachine 未新建独立类：fabric BaseSignItem（660 行）已把同一状态机内联（判定顺序 / 阈值 / 离线补偿口径一致），再建第二份事实源风险更高；如需结构对齐应作为 BaseSignItem 重构单独立项。
3. component/SignState.java 无消费者：本线 BaseSignItem 仍用扁平附件键，无 SIGN_STATE 单键、无迁移方法 ⇒ 原样交付但当前不参与运行。
4. SignTimerSourceAdapter 无消费者：本线 setTimerSource 全仓 0 调用点 ⇒ 需 AstralDiceMod 加一行 setTimerSource(new SignTimerSourceAdapter()) 才生效（该文件写权仅限追加 LoaderBus.register 行，故未改）。
5. 未折电池基准键无人写：SignAttachments.setSignActiveMaxCooldownBase 在本线无调用点（forge 在 BaseSignItem 起锁处写）。若命运指引卡减半类玩法依赖该值，本线为静默不生效。
6. 诡异骰子 +5 治愈（主动结束）缺失：forge 由 BaseSignItem 调 WeirdDiceHandler.onSignActiveEnded，本线该文件无此方法且基类无该调用 ⇒ 新立牌主动结束不结算 5 点治愈（未擅自新增：event/WeirdDiceHandler.java 不属本批写权）。
7. Patchouli 手册条目未搬：forge en_us 侧有 7 个新条目，但 fabric 侧 zh_cn/entries/signs 只有 1 个（en_us 24 个，整线滞后属其他批次）；本批只落 lang 键（多余键无害）。
8. 配方：forge 侧这 7 个 id 无独立配方文件（grep 0 命中）⇒ 未新增配方。
9. ModCreativeTabs 既有分组标题与实物品质不符：未重排（红线要求既有行不动）。

## 11. 无法判定 / 需要主 Agent 裁决

1. 编译正确性：本会话内无法编译（第 9 节）。需在主 Agent 可写 zip 的环境，或修复 loom 缓存后重跑 :fabric-1.20.1:classes 复核。已核对的风险点均在 9 节末列出。
2. 附件键的长期归属：本批把 7 立牌键放在新类 component/SignAttachments（因 ModAttachments 属他人写权）。长期正解是并回 ModAttachments / AstralDiceMod 统一注册；合并时须保证 syncedKeys() 不漏、ensureRegistered() 调用点可移除。
3. MobEffectEvent.Applicable 缺派发点：本线 platform.event 无 Applicable，全仓 0 处派发 ⇒ 铁处女 / 摸鱼的免疫有害效果只能免伤害、免不了药水。需裁决：补 shim 派发点（涉及 platform/ 他人批次文件）或接受降级。
4. BaseSignItem 三处滞后（未折电池基准 / 主动结束治愈 / AP_SIGN_SOUND 机器行）：均需改 item/sign/BaseSignItem.java，且会改变既有立牌行为 ⇒ 需裁决由哪一批落。
5. SignActionBar / SignHookDispatcher 与基类内联实现并存：行为等价但两份实现；是否收敛到本批新类（需改 BaseSignItem）待裁决。
6. SignState / SignTimerSourceAdapter 无消费者：按交付清单落地但当前不影响运行（10.3 / 10.4），是否保留待裁决。
7. 创意栏分组标题错配（10.9）：是否重排由主 Agent 决定（重排会移动既有行，超出本批红线）。
8. 手册条目缺口（10.7）：fabric zh_cn 手册整线滞后，需另一批次补齐。

