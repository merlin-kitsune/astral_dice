# B0-TAIL — 抽奖盒（event_drawer）状态面 + event_ticket 物品（forge-1.20.1 蓝本 → fabric-1.20.1）

- 2026-11-01 · 蓝本 `forge-1.20.1`（次基准，逐字对照）· 主基准 `neoforge-1.21.1`
- 结论：两条 1:1 缺口（`TODO(B0-ATTACHMENTS)` / `TODO(B0-ITEMS)`）**全部清零**；`python scripts/__javac_check.py` 的 REAL 分桶 0 → 0；三语键数 978→980 且三份一致。
- 约束遵守：未跑 gradle、未启动游戏、未 commit、未删文件、未改 `build.gradle` / wrapper / `AstralDiceMod.java`；改动文件全部 CRLF + UTF-8 无 BOM（§4 实测）。

## 1. A 抽奖盒状态面（原缺陷：四 seam 全空 ⇒ 冷却恒 0 = 永远免冷却）

### 1.1 `ModAttachments` 补两键 + 四访问器
- `EVENT_DRAWER_COOLDOWN_UNTIL` → `component/ModAttachments.java:123-135`；蓝本 `component/ModAttachments.java:98-110`。键名/Codec/默认值逐字一致：`builder("event_drawer_cooldown_until", Codec.LONG, () -> 0L).sync().build()` + `getEventDrawerCooldownUntil` / `setEventDrawerCooldownUntil`。
- `EVENT_DRAWER_BLANK_INSERT_AT` → `component/ModAttachments.java:137-150`；蓝本 `:112-125`。无 `.sync()`：`builder("event_drawer_blank_insert_at", Codec.LONG, () -> 0L).build()` + 同名 get/set。
- `SYNCED_KEYS` 增一行 → `component/ModAttachments.java:1394-1395`；蓝本 `:1522-1526`。
- 写法遵循本线既有风格（`register(AttachedDataKey.builder(...).build())`、`static get/set(net.minecraft.world.entity.player.Player, long)`），未照搬 forge import；插入点照蓝本（`LULU_LAST_HURT_TICK` setter 之后、`HEALING_POINTS` 之前）。

### 1.2 同步语义（与蓝本完全一致）
- cooldown：**同步** —— 键上 `.sync()` + 进 `SYNCED_KEYS`（登录快照 + 增量）。必要性：客户端 Jade 悬浮框读剩余倒计时（`ShopCooldownClientProvider` 跑在客户端）。
- blank_insert_at：**不同步** —— 判定只在服务端（投入间隔），客户端不读 ⇒ 不 `.sync()`、不进 `SYNCED_KEYS`。

### 1.3 `EventDrawBlock` 四 seam → 真实转发（上层逻辑一行未动）
- `giveEventTicket(Player)` `:146-151`：`new ItemStack(ModItems.EVENT_TICKET.get())` + `player.getInventory().add(...)`，加不进则 `player.drop(ticket, false)`（蓝本 `block/EventDrawBlock.java:113-116`）。
- `getCooldownUntil` `:154-156` → `ModAttachments.getEventDrawerCooldownUntil(player)`（蓝本 `:95`）；`setCooldownUntil` `:159-161` → `ModAttachments.setEventDrawerCooldownUntil(player, value)`（蓝本 `:85`/`:117`）。
- `getBlankInsertAt` `:164-166` / `setBlankInsertAt` `:169-171` → 同名 ModAttachments 访问器（蓝本 `:77`/`:86`）。
- 方法签名/可见性/调用点保持原样；`TODO(B0-ATTACHMENTS)` 与类头「差异说明」段改写为「状态面接线」；`AP_EVENT_DRAWER:` 日志行未动（测试台机器判据）。
- 效果：购买 → `setCooldownUntil(now + 6000L)`（5:00）；投入空白筹码 → `setCooldownUntil(0L)` + `setBlankInsertAt(now)`（2:00 间隔）。`use()` 本体 `:71-129` 与蓝本 `:64-123` 逐句对应。

### 1.4 `compat/jade/ShopCooldownClientProvider` 倒计时接线
- `:71` 由 `return 0L`（原带 `TODO(B0-ATTACHMENTS)`）改为 `return ModAttachments.getEventDrawerCooldownUntil(player) - now;`；类头 `:29-35` 缺口说明段改写为「已接线」。
- 口径与同文件另两支一致：剩余 tick → `(max(0,t)+19)/20` 秒向上取整 → `%d:%02d`；`remaining <= 0` 时外层不显示（原逻辑未动）。

## 2. B `event_ticket` 物品（本线唯一缺的物品）

### 2.1 物品类 `item/EventDrawItem.java`（新建 75 行）
- 蓝本 `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/EventDrawItem.java`（73 行）；`diff` 仅 3 处、均本线体例：+2 行移植注记 javadoc、`!level.isClientSide` → `!level.isClientSide()`、`level.isClientSide` → `level.isClientSide()`。
- 行为逐字保留：`AP_EVENT_ANIM` / `AP_EVENT_DRAW` 判据日志、EventAnimationLock 播动画期拒绝、`AstralEventRegistry.drawRandom` → `AstralEventDispatcher.trigger(..., ITEM_USE)`、触发成功才 `stack.shrink(1)`（创造不消耗）、`getCooldowns().addCooldown(this, EventCardService.QUESTION_COOLDOWN_TICKS)`、`InteractionResultHolder.sidedSuccess`。
- 依赖面实测在位且签名一致：`event/EventAnimationLock.java:55/80`、`event/AstralEventRegistry.java:125`、`event/AstralEventDispatcher.java:58`、`event/EventCardService.java:57`、`network/ModNetwork.java:276`。

### 2.2 注册 / 创造栏 / 发放
- `item/ModItems.java:1089-1091`：`EVENT_TICKET = registerItem("event_ticket", () -> new EventDrawItem(new Item.Properties().stacksTo(16).rarity(Rarity.COMMON)))`，位置介于 CHIP_SHOP 与 EVENT_DRAWER（蓝本 `item/ModItems.java:455` 逐字）。
- `init/ModCreativeTabs.java:217`：`output.accept(ModItems.EVENT_TICKET.get());`（蓝本 `init/ModCreativeTabs.java:227`）。
- `block/EventDrawBlock.java:146-151`：发放体（蓝本 `block/EventDrawBlock.java:113-116`）。forge 对 `EVENT_TICKET` 的 Java 引用全集 = 这 3 处，已全覆盖。

### 2.3 配套资源（逐项与 forge 同名文件比对）
- 模型：`src/generated/resources/assets/astral_dice/models/item/event_ticket.json`（114 B / CRLF×5 / 无尾换行），与 forge 同形（`item/generated` + `astral_dice:item/event_ticket`）；放 generated 因 `fabric-1.20.1/build.gradle:31-33` 的 `sourceSets.main.resources { srcDir('src/generated/resources') }`。
- 贴图：`src/main/resources/assets/astral_dice/textures/item/event_ticket.png`（274 B，从 forge 原样 cp）；`cmp` → identical；两侧 sha256 `d4711b00b5fe9d97aa8b30223bc0c351e5e2870583db18b87b1aba31c673a8d1`。
- 三语 lang：`assets/astral_dice/lang/{zh_cn,en_us,ja_jp}.json` 各 +2 键（978→980）：`item.astral_dice.event_ticket` + `astral_dice.guide.entry.event_ticket.1`，取值逐字取自 forge（奇怪的问号 / Strange Question Mark / 奇妙なハテナ + 说明段）。
- 手册：`patchouli_books/astral_guide/en_us/entries/materials/event_ticket.json`（13 行，`cmp` → identical；`category: astral_dice:materials`、`sortnum: 10`，本线 materials 现用 0-9 正好接续）。
- datagen：`datagen/ModItemModelProvider.java:176` 补 `generateFlatItem(..., ModelTemplates.FLAT_ITEM)`（forge 无对应，本线物品模型由 datagen 产出，保证 rebuild 后同一文件）。
- tag：无镜像对象 —— forge **未**把它写进任何 tag（`grep -rl event_ticket forge-1.20.1 --include='*.json'` 只命中 lang / 模型 / 手册）。
- 引用面对齐：`grep -rl "event_ticket"` fabric 侧命中 = {generated 模型, lang×3, 手册, EventDrawBlock, ModItems}，与 forge（排除 `bin/`、`build/`）一致。

## 3. 自检原始输出（真跑；未跑 gradle / 未启动游戏）

### 3.1 `python scripts/__javac_check.py`（唯一编译判据，只看 REAL）
```
改动前：
EXIT 1 ERRORS 170 jars 685 srcs 516
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  163  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 978
改动后：
EXIT 1 ERRORS 171 jars 685 srcs 517
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  164  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 984
```
**REAL = 0 → 0（达标）。** 两处计数增量归因：
- `srcs 516→517` = 新增 `item/EventDrawItem.java`。
- `NOISE_AW 163→164` = 新增的那一行模型生成器调用。证据：`fabric-1.20.1/build/javac-check/errors.txt` 里 "generateFlatItem in class ItemModelGenerators cannot be applied" 条目数 = 144 = `ModItemModelProvider.java` 的 generateFlatItem 调用数 144（改动前 143/143）；报错体 `required: Item,ModelTemplate / found: Item,ModelTemplate / reason: generateFlatItem(Item,ModelTemplate) has private access in ItemModelGenerators` —— 与同文件既有 143 条完全同因（Loom/mappings 访问修饰差异，javac 不可复现，真实 gradle 不报）。
- 反向核验：`grep 'EventDrawItem.java\|EventDrawBlock.java\|ShopCooldownClientProvider.java\|component.ModAttachments.java\|item.ModItems.java\|ModCreativeTabs.java' errors.txt` → **0 命中**（本次改动业务代码零编译错误）。

### 3.2 `python tools/verify_fabric_assets.py`（改动后全文）
```
jar = fabric-1.20.1\build\libs\astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar

[1] 物品闭环: 注册 157 (方块物品 3) / 模型 138 / 贴图 138 (方块贴图另计)
[2] 标签闭环: 引用 10 / 自建 29 / 有提供者 10
[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
[4] 粒子闭环: 注册 2 / particles/*.json 2
[5] 语言闭环: zh_cn 980 / en_us 980 / ja_jp 980
[6] 手册闭环: 条目 157 / 引用配方 138
[7] 创意标签覆盖: 156/157
    已知与基线一致（不阻塞）: ['repulsion']
[8] 饰品槽位图标: 检查 7 条 icon

=== FAIL ===
  ✗ 方块物品资源不全(blockstate=['card_shop', 'chip_shop', 'event_drawer'] / model=['card_shop', 'chip_shop', 'event_drawer'] / texture=['card_shop', 'chip_shop', 'event_drawer'])
  ✗ 缺模型(紫黑格): ['abyss_reagent_chip', 'alana_sign', 'ame_sign', 'card_shop', 'chip_shop', 'deepseek_sign', 'dorothy_sign', 'effect_card_barricade', 'effect_card_repulsion', 'event_drawer', 'event_ticket', 'extra_battery_chip', 'flashlight_burst_chip', 'fortune_stick_chip', 'infinite_grid_chip', 'jill_sign', 'kangel_sign', 'lingling_sign', 'repulsion', 'sensor_chip']
  ✗ 缺贴图: ['abyss_reagent_chip', 'alana_sign', 'ame_sign', 'deepseek_sign', 'dorothy_sign', 'effect_card_barricade', 'effect_card_repulsion', 'event_ticket', 'extra_battery_chip', 'flashlight_burst_chip', 'fortune_stick_chip', 'infinite_grid_chip', 'jill_sign', 'kangel_sign', 'lingling_sign', 'repulsion', 'sensor_chip']
```
改动前后对照（同一命令）：`[1] 注册 156 → 157`（+1 符合预期）；模型 138 / 贴图 138 不变；`[5] 978/978/978 → 980/980/980`（三语相等）；`[6] 156 → 157`；`[7] 155/156 → 156/157`（白名单仍只有 `repulsion`）。三条 FAIL 与基线同类同源：门脚本的模型/贴图集合取自 **jar namelist**（`tools/verify_fabric_assets.py:130-135`），注册 id 取自**源码**（`:106-110`）⇒ 新注册 id 必然出现在缺模型/缺贴图列表，直到下次真实构建进 jar；**未做任何「修」动作**。

### 3.3 `python tools/check_lang_sync.py --line fabric-1.20.1`
```
[OK]   fabric-1.20.1: en_us=980 键，3 份语言键集一致
```
（控制台裸跑时 CJK 被打花成 `[OK]   fabric-1.20.1: en_us=980 ����3 �����Լ���һ��`；上行为同一命令加 `PYTHONIOENCODING=utf-8` 的原样输出，exit 0。）

### 3.4 `grep -rn "TODO(B0-ATTACHMENTS)\|TODO(B0-ITEMS)" fabric-1.20.1/src/main/java`
```
（无输出，exit 1 = 无匹配）
```
抽奖盒相关（`B0-ATTACHMENTS` / `B0-ITEMS`）**已清零**。其它类别原样报出、未动：`recipe/condition/ChipRecipesEnabledCondition.java:58` 与 `:66`（TODO(CHIP-RECIPES-MECHANISM)）、`item/chip/AbyssReagentChipItem.java:139`（TODO(PLAYER-EFFECT-PROTECTION)）。

## 4. 文件清单 / 行尾实测
改 6：`component/ModAttachments.java`、`block/EventDrawBlock.java`、`compat/jade/ShopCooldownClientProvider.java`、`item/ModItems.java`、`init/ModCreativeTabs.java`、`datagen/ModItemModelProvider.java`；新建 4：`item/EventDrawItem.java`、generated `models/item/event_ticket.json`、`textures/item/event_ticket.png`、手册 `en_us/entries/materials/event_ticket.json`；另 3 份 lang 为改动（各 +2 键）。
实测（bytes / CRLF / bareLF / BOM=false / utf8-ok，12 个文本文件全部通过）：
```
component/ModAttachments.java             105352  1841  0
block/EventDrawBlock.java                   8987   172  0
compat/jade/ShopCooldownClientProvider.java 4692    96  0
item/EventDrawItem.java                     4241    75  0
item/ModItems.java                         75430  1188  0
init/ModCreativeTabs.java                  13876   222  0
datagen/ModItemModelProvider.java          15258   178  0
models/item/event_ticket.json                114     5  0
lang/zh_cn.json 128553/984/0 · lang/en_us.json 132311/984/0 · lang/ja_jp.json 164169/984/0
patchouli .../materials/event_ticket.json    317    13  0
```
（PNG 为二进制不入表；其 `cmp` 与 forge 逐字节相等。）

## 5. 未做 / 无法判定
- **未跑 gradle / datagen / 实机**（硬约束）⇒ 手写 `models/item/event_ticket.json` 未经 1:1 datagen 对比，只以「与 `blank_chip.json` 同形 + 同一生成器调用」对齐；下次 `runDatagen` 若产出差异，以 datagen 为准。
- 因未重建 jar：`[1]` 的缺模型/缺贴图仍会列 `event_ticket`（旧 jar 事实），下次构建后自动消失；**不是待修项**。
- Jade 实机悬浮框倒计时显示未验证（需客户端+服务端），只静态确认数据链：附件 → `SYNCED_KEYS` → 客户端 provider。
- 投入空白筹码复用 `msg.astral_dice.chip_shop_blank_cooldown`（与蓝本同），未新增语言键。
- 既有文档 `porting/fabric-1.20.1/B6-JADE-REPORT.md:20` 仍写「`EventDrawBlock` 分支恒 `return 0L`」，已被本批消除；该文件不属本批落点，**未改动**（避免与并行批次冲突），需后续批次更新。
- 未判定项：无（两条缺口均已在源码层闭环）。
