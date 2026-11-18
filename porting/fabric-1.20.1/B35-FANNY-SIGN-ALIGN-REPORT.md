# B35 · fabric-1.20.1 · FannySign（大侦探立牌）主动技对齐主线

- 批次：**B35-FANNY-SIGN-ALIGN**
- 目标线：`fabric-1.20.1`
- 主基准：`forge-1.20.1`（次基准 `neoforge-1.21.1`，两线该文件逐行同构，见 §1.2）
- 结论：**已对齐**；四条验收实跑全绿；**零平台垫片**；本批净改动 **1 个 java 文件**。

## 0. 一句话结论

fabric 的 `FannySignItem` 从「11 选 1 随机事件 + 自拟 actionbar + 锁定态门控」改为与主线完全相同的「调 `EventChoiceManager.start(serverPlayer)`，返回 false 就 `InteractionResultHolder.fail`」，类体只剩物品本体与主动技挂载点，行数 **184 → 53**；事件选择后端（会话生命周期、2 选 1、15 秒超时、死亡/登出/换维兜底）全部复用 fabric 既有 `event/EventChoiceManager`，**未新造任何实现**。

## 1. 主基准取证

### 1.1 forge-1.20.1 实现（全文 49 行）

`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/FannySignItem.java`

| 位置 | 内容 |
|---|---|
| :8-21 | 类 javadoc：2026-10-10 用户裁决删除本立牌 11 项随机事件，主动技重做为「事件选择界面」，15 秒内选 1 并确认，经 `AstralEventDispatcher#trigger(..., ACTIVE_SKILL)` 进入既有事件流程 |
| :22-25 | `public class FannySignItem extends BaseSignItem` + 仅 `ctor(Properties)` |
| :27-48 | `@Override protected InteractionResultHolder<ItemStack> handleUse(Level, Player, ItemStack)` |
| :29-31 | `if (level.isClientSide) return InteractionResultHolder.success(stack);` |
| :37-46 | `if (player instanceof ServerPlayer serverPlayer) { boolean opened = EventChoiceManager.start(serverPlayer); if (!opened) return InteractionResultHolder.fail(stack); }` |
| :47 | `return InteractionResultHolder.success(stack);` |

**全类再无其它成员**：无 `onSignActiveTriggered`、无 `startActiveLockOnUse`、无 `isGateEffectActive`、无 `LOCK_GATE_EFFECTS`、无随机数、无自拟 actionbar、无 `giveItem/giveCoins`。

### 1.2 neoforge 交叉确认

`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/FannySignItem.java` 同样 **49 行**，`handleUse` 在 :28、`EventChoiceManager.start` 在 :42、`fail` 在 :44 —— 与 forge 逐行同构，佐证主线唯一实现（与「neoforge 1.21.1 / forge 1.20.1 语言键三语逐字相同」的前置事实同向）。

### 1.3 反向事实（重要，供批次调度参考）

`neoforge-26.1.2`（第三线）**尚未**对齐：其 `FannySignItem.java` 仍是旧实现 188 行，`handleUse` 在 :38、`ThreadLocalRandom.current().nextInt(1, 12)` 在 :43、`msg.astral_dice.fanny_event` 在 :173-174、`@SubscribeEvent` 在 :182。因此「最新主线」= `forge-1.20.1` / `neoforge-1.21.1` 这一对，本批以 forge 为蓝本正确。

## 2. fabric 对齐前现状（旧实现，184 行）

`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/FannySignItem.java`（HEAD 版本）

| 位置 | 旧行为 |
|---|---|
| :35 | `ThreadLocalRandom.current().nextInt(1,12)` 抽 1..11 |
| :46-52 | 自算目标集 `PartyRelations.collectEventTargets(player, AstralEventSystem.EVENT_TARGET_RADIUS)` 并逐目标 `applyEventTo` |
| :56-58 | 若 `lockEnd > getGameTime()` 则 `beginActiveLock(player,"astral_dice:fanny_sign",lockEnd)` |
| :59 | `sendEventActionBar`（自拟文案 `msg.astral_dice.fanny_event` + `.`+roll） |
| :62 | `AstralEventSystem.onEventTriggered(player,"fanny_active")` |
| :73 / :107 / :115 | 11 个分支的效果表 / `applyTimed` / `LOCK_GATE_EFFECTS` |
| :123 / :129 | 覆写 `startActiveLockOnUse` → `isSignActiveLocked`；覆写 `isGateEffectActive` |
| :169-170 | `Component.translatable("msg.astral_dice.fanny_event", Component.translatable("msg.astral_dice.fanny_event." + roll))` |
| :178-183 | `@SubscribeEvent onSignActiveTriggered(...)` → `setHandled()`（吞掉默认 actionbar） |

## 3. 逐文件改动

| 文件 | 前 | 后 | git numstat(相对 HEAD) | 说明 |
|---|---|---|---|---|
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/FannySignItem.java` | 184 行 | **53 行** | `+31 / -162` | 全量重写为 forge 蓝本 + 一段 fabric 批次留档 javadoc（:22-24） |
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java` | 350 行 | **350 行** | `+53 / -0`（全部来自他批） | 本批**净改动 0 行**：先删 `register(FannySignItem.class)`，核对 `LoaderBus.register(Class)` 语义与文件惯例后**还原**（见 §4.6） |

字节自检（两个文件最终态）：

```
FannySignItem.java : bytes 3453  crlf 53  lf 53  bareLF 0  BOM False
AstralDiceMod.java : bytes 27059 crlf 350 lf 350 bareLF 0  BOM False
```

## 4. 判定依据 —— 为什么这些删改与基准等价

### 4.1 `handleUse` 返回 `fail` 的语义两侧完全一致

fabric `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BaseSignItem.java:152-153`：

```java
InteractionResultHolder<ItemStack> result = sign.handleUse(player.level(), player, stack);
if (result.getResult() != InteractionResult.SUCCESS) return;
```

⇒ 返回 fail 时：**不放技能音效、不发 FanBig/FanSmall 牌、不 post `SignActiveTriggeredEvent`、不进入冷却/锁定**。
forge `forge-1.20.1/.../item/sign/BaseSignItem.java:159-161` 逐字等价。故 fabric 侧无需任何适配即可复用基准的「false ⇒ 空放」口径。

### 4.2 默认 actionbar 提示（旧实现吞掉的那条）恢复为基准口径

fabric `BaseSignItem.java:163` `LoaderBus.INSTANCE.postEvent(triggered)`；`:164-166` 若 `!triggered.isHandled()` 则 `notifyActionBar(player,"msg.astral_dice.sign_active_triggered",…)`。
forge 对应 `BaseSignItem.java:171-176`（用 `MinecraftForge.EVENT_BUS.post`）。
删掉 fabric 旧的自定义 actionbar 与 `@SubscribeEvent` 吞提示后，fabric 走同一分支 ⇒ 提示文案与基线一致。

### 4.3 锁定态：基准本来就不进锁定

fabric `BaseSignItem.java:183` 调 `startActiveLockOnUse(player, now)`，缺省实现 `:413-415` `return false` ⇒ 落到 `:194-203` 的 else 分支 = 立即起基础冷却（写 `setSignActiveCooldownEnd`）。forge 同构。
旧 fabric 覆写把它变成「锁 15 秒 + 11 项效果门控」，属被裁决废弃的设计 ⇒ 删除覆写后行为与基准一致。

### 4.4 事件选择后端 fabric 已完备（复用，未新造）

`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/EventChoiceManager.java`（338 行）：

- `:57 OPTION_COUNT=2`、`:60 CHOICE_TIMEOUT_TICKS=15*20`
- `:104-144 start(ServerPlayer)`：`:108` 事件卡动画窗口期拒绝并提示（`EventAnimationLock`）；`:118-122` 同玩家已有**未结算**会话拒绝；`:125` `AstralEventRegistry.publicPool(player, AstralEventTrigger.ACTIVE_SKILL)`（按「该来源可派发」口径构造）；`:126-129` 池空拒绝；`:131-132` **两次独立抽取**（允许相同/任意正负中性组合，非去重）；`:134-143` 建 session 并下发 `EventChoiceStartMessage`，返回 true
- 生命周期：`:147 confirm`（token + 「尚未结算」双校验；`:172` 唯一结算顺序）、`:195 exitWithoutChoice`、`:211 cancelFor`、`:220 forceFirst`、`:273 tick`、`:299 onPlayerTick`、`:307 onPlayerDeath`、`:316 onPlayerLogout`、`:332 onPlayerChangedDimension`
- 已注册：`fabric-1.20.1/.../AstralDiceMod.java:163 LoaderBus.INSTANCE.register(EventChoiceManager.class)`

⇒ fabric 侧唯一缺口就是「把立牌主动技入口接到 `start()`」，正是本批唯一的功能性改动。

### 4.5 被动不丢：从「显式 `onEventTriggered("fanny_active")`」改由派发收尾统一触发

`fabric-1.20.1/.../event/AstralEventDispatcher.java:145` → `AstralEventSystem.applyEventBonus(bonusFinal, event.id().toString(), targetsFinal)`；
`AstralEventSystem.java:37 applyEventBonus` → `applySignBuffs`（`:62` 持大侦探立牌者 +3 星币）+ `applyRinSignPassive`（调查员活体书页）。
forge 对应 `AstralEventDispatcher.java:137`。
⇒ 确认后进入同一条派发链，立牌被动按**所选事件 id** 结算，与基准一致；旧实现里那行 `onEventTriggered(player,"fanny_active")` 属重复触发点，删除不丢被动（`AstralEventSystem.onEventTriggered` 仍被 `AstralEventSystem.java:55` 的 investigation 分支调用，未产生死代码）。

### 4.6 `AstralDiceMod` 的登记行：核对后保留（本批净改动 0 行）

类已无 `@SubscribeEvent`，`LoaderBus.register(Class)` 对它是**纯 no-op**：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/event/LoaderBus.java:124-137` `register(Class)` → `scan()` 只把 owner 加入 `registeredOwners`、遍历 `@SubscribeEvent` 方法，没有就不加 listener，不抛错、不打日志。
且本仓现行惯例是「立牌类一律登记」：38 个立牌类中 20 个带订阅，其余 18 个无订阅仍登记（例：`AlanaSignItem` / `DeepseekSignItem` / `DorothySignItem` / `JillSignItem` 实测 `grep -c '@SubscribeEvent'` = 0 但都在 `AstralDiceMod` 注册表里）。
⇒ 保留 `AstralDiceMod.java:204` 的 `register(...FannySignItem.class)` 行：与惯例一致，并避免「将来若给 Fanny 重新加 `@SubscribeEvent` 却静默不生效」的陷阱（删掉反而会制造这个坑）。

## 5. 平台差异与垫片理由

**结论：本批零垫片（0 处新垫片）。** 已知的三条 fabric/forge `BaseSignItem` 差异全部**不落在 Fanny 路径**上：

| 差异 | fabric | forge | 是否影响 Fanny |
|---|---|---|---|
| selector 分支守卫 | `BaseSignItem.java:137-150` 的 selector 分支缺 `EventAnimationLock.isAnimating` 守卫 | 有 | **否** —— 只有 `selectorActionId()!=null` 的立牌才走该分支，Fanny 不定义 selector |
| 事件总线 API | `LoaderBus.INSTANCE.postEvent` | `MinecraftForge.EVENT_BUS.post` | **否** —— 见 §4.2，触发与判定语义等价 |
| 挂接数据 API | `SignAttachments` | `ModAttachments` | **否** —— 旧实现的锁定态删除后，Fanny 不读写任何挂接数据 |
| `handleUse` 契约 | `:152-153` fail 早退 | `:159-161` 同 | **否** —— 等价 |

因此不需要任何 API 垫片，也没有「第三种设计」。

## 6. 残留自检

```
$ grep -rn "fanny_event" fabric-1.20.1/src/main/java
（无输出；退出码 1 = 无匹配）
$ grep -rn "fanny_event" fabric-1.20.1/src/main/java | wc -l
0
```

**预期 = 0，实测 = 0 ✅**（旧实现里有 2 处：`:169` 外层 key 与 `:"msg.astral_dice.fanny_event." + roll` 拼接）。
附：编译产物一致的交叉验证 —— `fabric-1.20.1/build/classes/java/main/.../FannySignItem.class`（1818 B）经 `javap -p -c` 只有 `ctor` 与 `handleUse`，`handleUse` 字节码为：
`isClientSide` 早退 → `instanceof ServerPlayer` → `invokestatic EventChoiceManager.start:(Lnet/minecraft/server/level/ServerPlayer;)Z` → `ifne 42` → 否则 `InteractionResultHolder.fail` → 末尾 `success`，与 §1.1 逐条对应。
jar 内同名类 1621 B，常量池 `fanny_event`=无、`EventChoiceManager`=有、`startActiveLockOnUse`/`isGateEffectActive`/`onSignActiveTriggered`=全部无。

## 7. 验收原始末段（四条全部实跑，末段逐字粘贴）

### ① `:fabric-1.20.1:classes` → BUILD SUCCESSFUL

```
$ bash -c 'set -o pipefail; cmd.exe //c "gradlew.bat :fabric-1.20.1:classes --console=plain" 2>&1 | tail -10'
Reusing configuration cache.
> Task :fabric-1.20.1:processResources UP-TO-DATE
> Task :fabric-1.20.1:compileJava
> Task :fabric-1.20.1:classes

BUILD SUCCESSFUL in 1s
2 actionable tasks: 1 executed, 1 up-to-date
Configuration cache entry reused.
PS=0
```

（另跑一次强制全量重编译，证明**当前工作树源码**确实可编译，而非命中缓存：）

```
$ ... gradlew.bat :fabric-1.20.1:compileJava --rerun-tasks --console=plain ... | tail -10
注: 某些输入文件使用或覆盖了已过时的 API。
注: 有关详细信息, 请使用 -Xlint:deprecation 重新编译。
注: 某些输入文件使用了未经检查或不安全的操作。
注: 有关详细信息, 请使用 -Xlint:unchecked 重新编译。

[Incubating] Problems report is available at: file:///F:/MCProject/astral_dice_multiloader/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 5s
1 actionable task: 1 executed
Configuration cache entry reused.
PS=0
```

### ② `:fabric-1.20.1:remapJar` → BUILD SUCCESSFUL，libs 只有 1 个 jar

```
$ ... gradlew.bat :fabric-1.20.1:remapJar --console=plain ... | tail -10
> Task :fabric-1.20.1:processIncludeJars UP-TO-DATE
> Task :fabric-1.20.1:compileJava UP-TO-DATE
> Task :fabric-1.20.1:processResources UP-TO-DATE
> Task :fabric-1.20.1:classes UP-TO-DATE
> Task :fabric-1.20.1:jar
> Task :fabric-1.20.1:remapJar

BUILD SUCCESSFUL in 4s
5 actionable tasks: 2 executed, 3 up-to-date
Configuration cache entry reused.
PS=0
```

```
$ ls -la --time-style=+%H:%M:%S fabric-1.20.1/build/libs/
total 2972
drwxr-xr-x 1 xmace 197609       0 Nov  1 20:35:31 .
drwxr-xr-x 1 xmace 197609       0 Nov  1 20:10:45 ..
-rw-r--r-- 1 xmace 197609 3035669 20:35:31 astral_dice-1.5.0-rc.7+fabric_1.20.1.jar
```

（目录内**只有** `astral_dice-1.5.0-rc.7+fabric_1.20.1.jar`；体积 3035669 B 与 20:29 那次 3034761 B 不同，是因为**并发批次**在同一时段改了 `item/chip/`，与本批无关，见 §9。）

### ③ `python tools/verify_fabric_assets.py` → PASS 8 项闭环

```
jar = fabric-1.20.1\build\libs\astral_dice-1.5.0-rc.7+fabric_1.20.1.jar

[1] 物品闭环: 注册 157 (方块物品 3) / 模型 158 / 贴图 155 (方块贴图另计)
[2] 标签闭环: 引用 10 / 自建 29 / 有提供者 10
[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
[4] 粒子闭环: 注册 2 / particles/*.json 2
[5] 语言闭环: zh_cn 1094 / en_us 1094 / ja_jp 1094
[6] 手册闭环: 条目 184 / 引用配方 125
[7] 创意标签覆盖: 156/157
    已知与基线一致（不阻塞）: ['repulsion']
[8] 饰品槽位图标: 检查 7 条 icon

=== PASS: 8 项闭环全部通过 ===
PS=0
```

### ④ `python tools/check_lang_sync.py` → 四线全 [OK]

```
$ PYTHONIOENCODING=utf-8 python tools/check_lang_sync.py | tail -12
[OK]   neoforge-1.21.1: en_us=1070 键，3 份语言键集一致
[OK]   forge-1.20.1: en_us=1070 键，3 份语言键集一致
[OK]   neoforge-26.1.2: en_us=1094 键，3 份语言键集一致
[OK]   fabric-1.20.1: en_us=1094 键，3 份语言键集一致
PS=0
```

（首次不带 `PYTHONIOENCODING=utf-8` 直跑时中文因 MSYS 代码页变成乱码，但四行 `[OK]` 与键数完全一致；本批未改任何 lang，键数 1094 与基线相同，fabric 键集无变化 ⇒ 未越界。）

## 8. 文案批次待办（本批按红线未动任何 lang json）

### 8.1 在 fabric 侧变成 0 引用的键（可删，但**不要**在本批删）

`msg.astral_dice.fanny_event`、`msg.astral_dice.fanny_event.1` … `.11`，**共 12 个键**。

- 代码侧证据：`grep -rn "fanny_event" fabric-1.20.1/src/main/java` = **0 命中**。
- lang 侧现状：`zh_cn.json` / `en_us.json` / `ja_jp.json` 各 12 处仍在（`en_us.json:430-441` 起；zh_cn 同段）。
- **重要修正**：这 12 个键**不是 fabric 独有** —— 实测 `neoforge-26.1.2` 三语也各有 12 处（该线 `FannySignItem.java:173-174` 仍引用，仍是旧实现），而 `forge-1.20.1` / `neoforge-1.21.1` 三语中**均为 0**。
  ⇒ 文案批次删这 12 键时，只应随「已经对齐的那条线」删；若同时删 26.1.2 的，必须先让 26.1.2 的 Fanny 也完成对齐（否则 26.1.2 那边会缺键）。

### 8.2 不是孤儿、但文案已陈旧（需改写，**不要删**）

| 键 | 现状 | 处置建议 |
|---|---|---|
| `astral_dice.guide.entry.fanny_sign.1` | 仍被 `assets/astral_dice/patchouli_books/astral_guide/en_us/entries/signs/fanny_sign.json` 引用（该条目共 3 个 guide 键）；java 侧 0 引用 | 描述的是旧「11 项随机事件」⇒ 需改写为「2 选 1 事件选择界面 / 15 秒内确认」 |
| `astral_dice.guide.entry.fanny_sign.2` | 同上 | 同上 |
| `astral_dice.guide.entry.fanny_sign.3` | 同上 | 被动（+3 星币）描述，本次行为未变 ⇒ **无需改** |
| `tooltip.astral_dice.sign.fanny_active` | 仍被 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java:1164` 引用 | **不是孤儿**；若文案与主线不同，交文案批次比对后决定 |

### 8.3 lang 未改动的取证

`git status --porcelain` 里 fabric 三份 lang 显示 `M`，但其 mtime = `2026-11-01 20:21:58`（早于本批首次写盘 20:28:36），本批**未写过**它们；其相对 HEAD 的差异（en_us +556 / ja_jp +448 / zh_cn +464）属**其它批次在途改动**，本批未触碰、未回滚。

## 9. 并发状态知会（不属于本批，但影响构建证据的时间点）

构建期间**另一个批次**正在同一工作树改 `item/chip/` 与 `ModItems` / `StarLightManager`（实测 mtime 20:30:58 – 20:35:25），中途一度让 `:fabric-1.20.1:compileJava` 在**别人的文件**上失败：

```
F:\...\item\ModItems.java:585: 错误: 找不到符号
                    .rarity(AstralRarities.rare()), BankCardChipItem.BASE_LOW));
  符号:   变量 BASE_LOW
  位置:   类 BankCardChipItem
F:\...\item\StarLightManager.java:155: 错误: 找不到符号
                total += BankCardUnlimitedChipItem.STARLIGHT_ON_EQUIP;
... 共 4 个错误
```

本批**没有**改动这些文件（`git diff --numstat` 对 chip 包与本批无关）；等对方落到可编译状态后重跑，四条验收全部通过（§7 记录的是**最终态**的构建）。

## 10. 无法判定项

1. **未实机**（红线禁止启动 MC）⇒ 选择界面在 fabric 客户端的实际渲染与交互（两张卡、确认按钮、15 秒倒计时、超时/死亡/登出/换维强制第一个）本批**未验证**，只有静态等价 + 编译产物字节码核对。
2. `EventChoiceScreen`（fabric client）自身与主线是否逐像素一致 —— 不在本批范围，未核。
3. `fanny_active` 这个事件 id 从 fabric 代码消失后，**仓外**（数据包 / KubeJS / 其它模组）是否有人依赖 —— 本仓 `grep` 为 0（唯一残留是 `ModTooltipHandler.java:1164` 的 tooltip **键**，不是事件 id），仓外未知。
4. `AstralEventRegistry.publicPool(player, ACTIVE_SKILL)` 在 fabric 侧的可派发集合是否与 forge 完全一致 —— 属事件体系批次，本批未逐条比对。
5. `AstralDiceMod.java:200` 的注释「以下 5 个立牌类」与实际登记数量（现存 15+）不符，属他批落后注释，本批**未动**。
6. `neoforge-26.1.2` 线仍为旧实现（§1.3）—— 对齐该线属后续批次，本批未涉。
7. 并发批次（§9）在最后一次验收之后是否又改动编译输入 —— 本报告结论以 §7 记录的时间点（20:35）为准。
