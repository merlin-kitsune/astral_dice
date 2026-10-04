# 第二批改动:立牌主动技能「三态化」侦察与方案设计

- 仓库:`F:\MCProject\astral_dice_multiloader`,分支 `multi-1.20.1-1.21.1`,基线 `HEAD=6daebf9`(工作区干净)
- 子项目:`neoforge-1.21.1`(MC 1.21.1 / NeoForge / Java 21)与 `forge-1.20.1`(MC 1.20.1 / Forge / Java 17),包 `com.merlinkitsune.astral_dice`
- **本阶段严格只读**:只新建本文件;未改任何源码/资源/lang/CHANGELOG/AGENTS;未跑 Gradle;未执行任何 git 写操作
- 所有行号与代码原文均来自实际读取(行号以本次读取的文件版本为准)

---

## §1 枚举清单:16 个立牌主动技能 × "是否施加带时长效果"

判定口径(必须来自**代码可判定来源**):

- **L1 自身效果实例**:主动在**玩家自己**身上 `addEffect`/`EffectTimerGuard.apply` 的 `MobEffectInstance.getDuration()`;
- **L2 自身附件到期刻**:主动写入玩家级 `*_until` / `*_expire` 附件(如 `nancy_lu_hidden_until`、`sign_ready_expire`);
- **L3 目标侧效果实例**:主动施加到**其它实体**(敌对生物/玩家/Boss)身上的效果;
- **L4 无**:只发物品/星币/组件计数,不产生任何计时器。
- 明确排除:**不采信 tooltip 文案**;无限时长(`Integer.MAX_VALUE`)的效果**不算计时器**(见 `EffectTimerGuard.INFINITE_THRESHOLD`,`event/EffectTimerGuard.java:38`,`duration >= Integer.MAX_VALUE/2` 视为永续、连守卫都不记录)。

### 1.1 汇总表(推荐口径:**只有 L1/L2 计入"本技能施加的计时器"**)

| # | 立牌 | 类 | 主动技能 | 施加带时长效果? | 具体效果 / 时长 | 时长的权威来源 | 证据(文件:行号) |
|---|---|---|---|---|---|---|---|
| 1 | 看板 | `MimiSignItem` | 商品补货:回收全部卡牌,返还 N+1 张随机卡 | **否**(L4) | 只发卡牌/星币/随机筹码 | 无 | 1.21.1 `item/sign/MimiSignItem.java:49-71`(handleUse 内无任何 effect API);forge 同类文件 grep `addEffect|EffectTimerGuard.apply` **0 命中** |
| 2 | 经商 | `ParunanSignItem` | 套现 + 随机获得 饱和/幸运/村庄英雄 | **是**(L1,三选一) | `SATURATION` 600t(0:30)/ `LUCK` 6000t(5:00)/ `HERO_OF_THE_VILLAGE` 18000t(15:00) | **MobEffectInstance 的 duration 构造参数**(实例化即携带) | 1.21.1 `item/sign/ParunanSignItem.java:54-63`;forge `:53-63` |
| 3 | 扫地机 | `JasmineSignItem` | 能量过载 + 随机 抗性提升/力量 | **是**(L1) | `JASMINE_SWEEP` 2400t(2:00);随机 `DAMAGE_RESISTANCE` 2400t 或 `DAMAGE_BOOST` 2400t | 同上(字面常量 2400) | 1.21.1 `item/sign/JasmineSignItem.java:59-71`(62/65/67);forge `:58-71`(63/66/68) |
| 4 | 护法 | `MisakiSignItem` | 樱花裂空斩 | **是**(L1) | `MISAKI_BURST` 2400t(2:00) | 同上 | 1.21.1 `item/sign/MisakiSignItem.java:24-37`(27);forge `:23-38`(28) |
| 5 | 史莱姆 | `LuluSignItem` | 治愈粘液 | **否**(自身侧只有 1 tick 瞬间治疗;对敌对目标有 L3) | 自身 `HEAL` 1t(瞬时);敌对 `MOVEMENT_SLOWDOWN` 1200t(1:00)(L3);友方 `HEAL` 1t | L1 只有 1 tick(等同瞬时);L3 不计入 | 1.21.1 `item/sign/LuluSignItem.java:62-90`(70/79/82);forge `:62-90`(70/79/82) |
| 6 | 忍者 | `KomachiSignItem` | 忍术连击:本轮出牌数 +1(一次性) | **否**(L4)——**由第 3 条专用规则接管** | 无效果、无附件计时器;只写 `effect_card_bonus_plays`(0/1) | 无 | 1.21.1 `item/sign/KomachiSignItem.java:63-85`(80 `grantBonusPlay`);forge `:63-85` |
| 7 | 上班族 | `PadmanSignItem` | 真的生气了:重置被动计数器 | **否**(L4) | 只写 `ItemStack` 数据组件(ATK/DEF=4、LAST_REFRESH=now) | 无(组件不是"带时长效果") | 1.21.1 `item/sign/PadmanSignItem.java:34-44`;forge 同类文件 grep 0 命中 |
| 8 | 大侦探 | `FannySignItem` | 麻烦制造者:11 项随机事件之一 | **是(条件性)**:9/11 分支施加 L1 | `REGENERATION`/`DAMAGE_BOOST`/`MOVEMENT_SPEED`/`HUNGER`/`DIG_SLOWDOWN` 600t;`SATURATION` 600t;`farmersdelight:nourishment` 2400t;`POISON`/`WEAKNESS` 300t;`CONFUSION`/`WITHER` 140t;`DARKNESS` 100t;`HARM` 1t(瞬时);case 3/4 只发物品(L4) | 各分支字面 duration(随机结果**运行期才确定**,必须"施加时登记") | 1.21.1 `item/sign/FannySignItem.java:37-81`(54-78)、`:91-99`(95);forge `:37-50`、`:52-81`(53-77)、`:94` |
| 9 | 调查员 | `RinSignItem` | 活体书页:获得 1~2 张活体书页 | **否**(L4) | 只发卡牌 | 无 | 1.21.1 `item/sign/RinSignItem.java:38-51`;forge 同类文件 grep 0 命中 |
| 10 | 占星师 | `HaiqingSignItem` | 虚弱印记(30 秒待命 → 释放) | **待确认**(见 1.2):自身侧只有 L2 `sign_ready_expire`(+600t);施加到目标的是 L3 | 待命窗口 600t(0:30);释放时对目标 `WEAK_MARK` 6000t(5:00)+ `WEAKNESS` 6000t | 待命窗口 = **主动写入的附件** `sign_ready_expire`;目标效果 = duration | 1.21.1 `item/sign/HaiqingSignItem.java:88-99`(94-97);释放侧 `combat/DiceCombatEvents.java:236-254`(243-245);forge `item/sign/HaiqingSignItem.java:88-99`(96)、`combat/DiceCombatEvents.java:241-259`(248-250) |
| 11 | 吸血鬼 | `PaparaSignItem` | 嘬你一口 | **是**(L1) | `PAPARA_BITE` 3600t(3:00) | duration 字面常量 | 1.21.1 `item/sign/PaparaSignItem.java:29-37`(35);forge `:29-37`(35) |
| 12 | 秘密侦探 | `BonnieSignItem` | 隐匿行动(30 秒待命 → 释放) | **待确认**:自身侧只有 L2;目标侧 `UNDERCOVER_INVESTIGATION` 为 `Integer.MAX_VALUE`(无限时长,**不算计时器**) | 待命 600t;目标"隐匿调查"永续 | `sign_ready_expire`(附件);目标效果无有效时长 | 1.21.1 `item/sign/BonnieSignItem.java:91-102`(97-100);释放侧 `combat/DiceCombatEvents.java:255-278`(261-262);forge `item/sign/BonnieSignItem.java:91-102`(99)、`combat/DiceCombatEvents.java:260-283`(266-267) |
| 13 | 大当家 | `FenSignItem` | 运功 | **是**(L1) | `FEN_FRENZY` 1200t(1:00);若养精蓄锐 >0 再加 `MOVEMENT_SPEED` 1200t | duration 常量 `FRENZY_DURATION_TICKS=1200` | 1.21.1 `item/sign/FenSignItem.java:64-82`(70-71、77-78);forge `:64-82`(71/78) |
| 14 | 骇客 | `NancyLuSignItem` | 远程侵入 | **是**(L1 **与** L2 同长) | `INVISIBILITY` 600t(0:30)+ 附件 `nancy_lu_hidden_until = now+600` | **两者都是权威来源**(效果实例 duration + 主动写入的 `*_until` 附件) | 1.21.1 `item/sign/NancyLuSignItem.java:106-125`(118-120);forge `:106-125`(120) |
| 15 | 枪匠 | `MosesSignItem` | 弱点反击(30 秒待命 → 释放) | **待确认**:自身侧只有 L2;目标侧 L3 `MOSES_BROKEN` 2400t(2:00) | 待命 600t;目标破绽 2400t | 待命 = 附件;破绽 = `MosesBrokenEffect.DURATION_TICKS` | 1.21.1 `item/sign/MosesSignItem.java:88-99`(94-97)、`:127-136`(132-133)、`effect/MosesBrokenEffect.java:13`;释放侧 `combat/DiceCombatEvents.java:279-296`(289-294);forge `item/sign/MosesSignItem.java:88-102`(102)、`:127-136`(137)、`effect/MosesBrokenEffect.java:13`、`combat/DiceCombatEvents.java:284-302`(295-300) |
| 16 | 肉弹战车 | `PandamanSignItem` | 大吃特吃 | **否**(自身侧只发一张治疗牌;对敌对目标有 L3) | 敌对 `PANDAMAN_TAUNT` 1200t(1:00)(L3) | L3 不计入 | 1.21.1 `item/sign/PandamanSignItem.java:89-117`(109-110)、常量 `:59`;forge `:89-117`(110) |

**推荐结论(按"自身侧 L1/L2"口径)**:

- **"是"= 7 个**:经商 `parunan`、扫地机 `jasmine`、护法 `misaki`、大侦探 `fanny`(概率性:9/11 分支)、吸血鬼 `papara`、骇客 `nancy_lu`、大当家 `fen`。
- **"否"= 9 个**:看板 `mimi`、史莱姆 `lulu`、忍者 `komachi`(另有专用规则)、上班族 `padman`、调查员 `rin`、占星师 `haiqing`、秘密侦探 `bonnie`、枪匠 `moses`、肉弹战车 `pandaman`。
- 其中 `komachi` 之所以"否"却仍要特别处理,正是第 3 条规则存在的原因(它没有任何自身计时器,只能跟随出牌周期)。
- 若改为"目标侧 L3 也计入",则 `lulu`(1:00)、`moses`(2:00)、`pandaman`(1:00)、`haiqing`(5:00)会一并进入锁定期,冷却会被显著拉长 —— 见 §5-Q1/Q2。

### 1.2 特别确认:占星师 / 秘密侦探 / 枪匠的"30 秒待命窗口"是否算"本技能施加的计时器"

**代码事实**:

1. 三者的主动 `handleUse` 都只是写玩家级附件并加一个**永续**提示效果:

```java
// neoforge-1.21.1/item/sign/HaiqingSignItem.java:94-97
        ModAttachments.setSignReadyType(player, READY_TYPE);
        ModAttachments.setSignReadyExpire(player,
                level.getGameTime() + GameplayConstants.SKILL_WAIT_SECONDS * 20L);
        player.addEffect(new MobEffectInstance(ModEffects.HAIQING_READY, Integer.MAX_VALUE, 0, false, false, true));
        return InteractionResultHolder.success(stack);
```

`BonnieSignItem.java:97-100`、`MosesSignItem.java:94-97` 逐字同构(仅 READY_TYPE / 效果不同)。三处的提示效果都是 `Integer.MAX_VALUE`,**不构成计时器**(`EffectTimerGuard.INFINITE_THRESHOLD` 不会为它记录)。

2. 真正的 30 秒窗口在附件 `sign_ready_expire` 上,而**超时归零由玩家级 tick 负责**(`BaseSignItem.tickSignReadyTimeout`,`item/sign/BaseSignItem.java:171-189`),并且**不写任何冷却**:

```java
// neoforge-1.21.1/item/sign/BaseSignItem.java:178-181
        if (expire > 0 && player.level().getGameTime() < expire) return;
        // 计时器归 0:自动重置待命状态并移除对应的"待命"提示效果
        ModAttachments.setSignReadyType(player, 0);
        ModAttachments.setSignReadyExpire(player, 0);
```

3. 释放时的冷却写在 `combat/DiceCombatEvents`(`performSkill` 的门槛 `ModAttachments.getSignReadyType(player) <= 0` 使这三者不在按下瞬间起冷却,见 §2.1 第 6 步)。

**我的判断与理由(推荐口径:不算)**:

- **不算**:`sign_ready_expire` 虽然字面上是"主动写入的 `*_expire` 附件",但它的语义是**等待玩家选目标的窗口**,不是"技能效果的持续时间"。把它算作"本技能施加的计时器"会推出两个与现有既定行为冲突的结论:① 超时(未命中)也要起冷却,而现行设计/tooltip 明确"未命中则不消耗冷却"(`tooltip.astral_dice.sign.haiqing_active`、`moses_active`、`bonnie_active` 三行文案,`assets/astral_dice/lang/zh_cn.json:546/554/576`);② 第 1 条规定的"锁定期内按键无效"会把等待期二次加锁,但等待期本身按下按键已被 `isSkillWaiting` 拦掉(`BaseSignItem.java:98`),属于重复语义。
- 因此推荐:**这三个立牌不进入"锁定(生效中)"态**,保持"释放即起冷却 / 超时不起冷却"的现状;若用户要求"超时也起冷却",那应当作为独立的一条需求(而不是靠锁定态顺带实现)。
- 另一个独立问题:释放时施加到**目标**身上的 `WEAK_MARK` 5:00 / `MOSES_BROKEN` 2:00 是否算"该技能施加的计时器"?推荐**不算**(理由见 §5-Q1:目标身上的效果不代表"施加者身上的主动仍在生效中",且会让占星师冷却变成 3+5=8 分钟)。

---

## §2 现有实现触点(两版本,文件:行号 + 逐字原文)

### 2.1 `item/sign/BaseSignItem.java` — `performSkill` 完整判定链

1.21.1(forge 侧行号差 +0~1,已在括号内标注):

```java
// neoforge-1.21.1/.../item/sign/BaseSignItem.java:80-96  (forge: 81-97)
    private static void performSkill(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof BaseSignItem sign)) return;
        long now = player.level().getGameTime();
        net.minecraft.network.chat.Component signName = stack.getHoverName();
        // 1. 玩家级冷却检查:冷却中按键默认无效,并明确提示"<立牌名>冷却中"(修复:触发成功与冷却拒绝的反馈混淆)
        //    电流核心筹码:冷却中按下主动技能键 → 按剩余冷却占比消耗充能并立即使冷却完成(佩戴且充能足够时)
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        if (cdEnd > 0 && now < cdEnd) {
            int coreResult = com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem
                    .tryFinishCooldown(player, cdEnd, now);
            if (coreResult != com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem.FINISH_NONE) {
                // 已完成冷却(等待玩家再次按键释放)或充能不足(已提示):不再叠加默认冷却提示
                return;
            }
            notifyActionBar(player, "hud.astral_dice.sign_active_cooldown", signName, ChatFormatting.RED);
            return;
        }
```

```java
// neoforge-1.21.1/.../item/sign/BaseSignItem.java:97-101  (forge: 98-102)
        // 2. 等待状态检查:存在等待目标释放的主动技能时按键无效
        if (isSkillWaiting(player)) return;
        // 3. 触发主动技能
        InteractionResultHolder<ItemStack> result = sign.handleUse(player.level(), player, stack);
        if (result.getResult() != InteractionResult.SUCCESS) return;
```

```java
// neoforge-1.21.1/.../item/sign/BaseSignItem.java:102-112  (forge: 103-113)
        // 4. 手持风扇-大筹码:使用主动技能后,获得一张随机效果牌(不含专属),并对周围范围内敌对目标施加标记
        FanBigChipItem.applyAfterSignSkill(player);
        FanSmallChipItem.applyAfterSignSkill(player);
        // 5. 立牌主动技能响应事件:立牌类订阅本事件注册自身 ActionBar 反馈(见 SignActiveTriggeredEvent);
        //    无任何处理器响应(未注册)时,发送默认提示"xxx立牌:主动技能已启动!"
        com.merlinkitsune.astral_dice.event.SignActiveTriggeredEvent triggered =
                new com.merlinkitsune.astral_dice.event.SignActiveTriggeredEvent(player, stack);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(triggered);   // forge: net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(triggered);
        if (!triggered.isHandled()) {
            notifyActionBar(player, "msg.astral_dice.sign_active_triggered", signName, ChatFormatting.YELLOW);
        }
```

```java
// neoforge-1.21.1/.../item/sign/BaseSignItem.java:113-128  (forge: 114-129) —— 「写冷却 + setSignActiveMaxCooldown」那一段
        // 6. 冷却:等待类技能(激活了玩家级等待状态)待完成指定目标/超时后再开始冷却;其余立牌立即开始玩家级冷却
        //    2026-09-15 用户裁决(S6-C2,状态与计时器分离):门槛只看"当前是否处于待命状态"(sign_ready_type),
        //    不再看原始计时器数值(sign_ready_expire)——**陈旧的正计时器不得阻止冷却**: ...
        if (ModAttachments.getSignReadyType(player) <= 0) {
            // 诡异骰子:立牌主动冷却 -50%
            int signCooldownTicks = com.merlinkitsune.astral_dice.event.WeirdDiceHandler.signCooldownTicks(player);
            ModAttachments.setSignActiveCooldownEnd(player, now + signCooldownTicks);
            // 路线 A:记录本次冷却实际使用的最大冷却值,所有减免方一律读它(不再各自重算基准)
            ModAttachments.setSignActiveMaxCooldown(player, signCooldownTicks);
            // 电流核心筹码:主动技能实际生效时充能 +1
            com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem.onActiveSkillUsed(player);
        }
    }
```

`sendSignActionBar` / `notifyActionBar`(提示发送方式,新提示必须复用):

```java
// neoforge-1.21.1/.../item/sign/BaseSignItem.java:131-148  (forge: 132-149)
    protected static void sendSignActionBar(Player player, String langKey, Object... args) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        net.minecraft.network.chat.Component msg =
                net.minecraft.network.chat.Component.translatable(langKey, args).withStyle(ChatFormatting.YELLOW);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                new com.merlinkitsune.astral_dice.network.ActionBarPayload(msg,
                        GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }

    // 服务端发送立牌技能反馈(actionbar 提示,带立牌名称前缀;统一由服务端判定成功/拒绝,避免客户端推测混淆)
    private static void notifyActionBar(Player player, String langKey, net.minecraft.network.chat.Component signName, ChatFormatting color) {
        ...
                Component.translatable(langKey, signName).withStyle(color);
        ...
    }
```

> ⚠️ `notifyActionBar` 会**无条件把 signName 作为第 1 个格式化参数**传入 ⇒ 新 lang 值必须恰好含一个 `%s`(与 `msg.astral_dice.sign_active_triggered` = `"%s:主动技能已启动!"` 同构)。见 §5-Q9。

`isSkillWaiting` 与 `tickSignReadyTimeout`:

```java
// neoforge-1.21.1/.../item/sign/BaseSignItem.java:153-157  (forge: 154-158)
    private static boolean isSkillWaiting(Player player) {
        long expire = ModAttachments.getSignReadyExpire(player);
        return ModAttachments.getSignReadyType(player) > 0 && expire > 0
                && player.level().getGameTime() < expire;
    }
```

```java
// neoforge-1.21.1/.../item/sign/BaseSignItem.java:171-189 (节选)  (forge: 172-190)
    public static void tickSignReadyTimeout(Player player) {
        if (player == null) return;
        if (player.level().isClientSide()) return;
        int type = ModAttachments.getSignReadyType(player);
        if (type <= 0) return;
        long expire = ModAttachments.getSignReadyExpire(player);
        // 计时器仍有效(未归 0 且未到期):等待继续,不做处理
        if (expire > 0 && player.level().getGameTime() < expire) return;
        ModAttachments.setSignReadyType(player, 0);
        ModAttachments.setSignReadyExpire(player, 0);
        if (type == HaiqingSignItem.READY_TYPE) { ModEffectRemoval.remove(player, ModEffects.HAIQING_READY); }   // forge: HAIQING_READY.get()
        ...
    }
```

**这是新增"锁定态 tick"最合适的同构模板**(幂等、玩家级、与立牌是否在槽位无关),新逻辑建议直接并排挂在同一个事件方法里。

### 2.2 `item/chip/CurrentCoreChipItem.java`(全文要点;两版本逐行等价,仅 forge 多 `CuriosCompat` 导入与 `ModNetwork` 发包)

```java
// neoforge-1.21.1/.../item/chip/CurrentCoreChipItem.java:26-38  (forge 同)
    public static final int CHARGE_PER_SKILL = 1;
    public static final int MAX_COOLDOWN_SECONDS = 180;
    public static final int MAX_COOLDOWN_COST = 6;
    public static final int FINISH_NONE = 0;
    public static final int FINISH_DONE = 1;
    public static final int FINISH_NOT_ENOUGH = -1;
```

```java
// neoforge-1.21.1/.../item/chip/CurrentCoreChipItem.java:45-60  (forge 同)
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);            // forge: CuriosCompat.getCuriosInventory(player)
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.CURRENT_CORE_CHIP.get())).isPresent();
    }
    public static void onActiveSkillUsed(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        ChargeManager.addStacks(player, CHARGE_PER_SKILL);
    }
```

```java
// neoforge-1.21.1/.../item/chip/CurrentCoreChipItem.java:83-102  (forge 同) —— tryFinishCooldown 调用契约 / 扣充能 / "立即完成"写入
    public static int tryFinishCooldown(Player player, long cooldownEnd, long now) {
        if (player == null || player.level().isClientSide()) return FINISH_NONE;
        if (!isEquipped(player)) return FINISH_NONE;
        long remaining = cooldownEnd - now;
        if (remaining <= 0) return FINISH_NONE;
        // 路线 A:档位分母取起冷却时记录的"本次冷却实际使用的最大冷却"(记录缺失时回退硬编码 180 秒)
        int cost = instantCooldownCost(remaining, ModAttachments.getSignActiveMaxCooldown(player));
        if (ChargeManager.getStacks(player) < cost) {
            sendActionBar(player, "hud.astral_dice.current_core_not_enough", cost);
            return FINISH_NOT_ENOUGH;
        }
        for (int i = 0; i < cost; i++) {
            ChargeManager.consumeOne(player);
        }
        // 立即完成冷却:结束时刻置为当前时刻(后续判定 now < cdEnd 不再成立),并让"本次最大冷却"记录随冷却一起失效
        ModAttachments.setSignActiveCooldownEnd(player, now);
        ModAttachments.setSignActiveMaxCooldown(player, 0);
        sendActionBar(player, "hud.astral_dice.current_core_finish", cost);
        return FINISH_DONE;
    }
```

调用契约:`FINISH_NONE` = 未佩戴(交回默认冷却提示);`FINISH_DONE` = 已扣充能并立即完成;`FINISH_NOT_ENOUGH` = 已扣不动、已提示。**唯一调用点**是 `BaseSignItem.performSkill` 的冷却分支(1.21.1 `:88-93` / forge `:89-94`)。`instantCooldownCost` 在 `:69-75`。

### 2.3 `item/card/EffectCardPeriod.java`

`tick` 三分支(1.21.1 `:334-361`;forge `:335-362`,逐字等价):

```java
// neoforge-1.21.1/.../item/card/EffectCardPeriod.java:334-361
    public static void tick(Player player) {
        long now = player.level().getGameTime();
        long cooldown = ModAttachments.getEffectCardCooldownEnd(player);
        int played = ModAttachments.getEffectCardPlayCount(player);
        if (cooldown > 0 && now < cooldown) return;          // 冷却进行中:不动
        if (cooldown <= 0) {
            if (played <= 0) return;                          // 无残留(不凭空开冷却)
            int maxAllowed = getMaxAllowed(player);
            if (played < maxAllowed) {
                if (getRemainingBlockTicks(player) > 0) return;
            }
            long recoverTicks = ChargeManager.cooldownTicks(player,
                    GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L);
            ModAttachments.setEffectCardCooldownEnd(player, now + recoverTicks);
            return;
        }
        ModAttachments.setEffectCardCooldownEnd(player, 0);
        ModAttachments.setEffectCardPlayCount(player, 0);
        clearRoundBonuses(player);
        com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem.disarmAoe(player);
    }
```

`registerPlay` 的边界块(1.21.1 `:270-304`;forge `:271-305`):

```java
// neoforge-1.21.1/.../item/card/EffectCardPeriod.java:271-289
        long now = player.level().getGameTime();
        long cooldown = ModAttachments.getEffectCardCooldownEnd(player);
        int played = ModAttachments.getEffectCardPlayCount(player);
        if ((cooldown > 0 && now >= cooldown)
                || (cooldown <= 0 && played > 0 && played >= getMaxAllowed(player))) {
            ModAttachments.setEffectCardCooldownEnd(player, 0);
            ModAttachments.setEffectCardPlayCount(player, 0);
            clearRoundBonuses(player);
            cooldown = 0;
        }
        int count = ModAttachments.getEffectCardPlayCount(player) + 1;
        ModAttachments.setEffectCardPlayCount(player, count);
```

其余关键方法(两版本逐字等价,forge 侧仅 `Holder<MobEffect>` → `MobEffect`、`ModEffects.X` → `ModEffects.X.get()`):

| 方法 | 1.21.1 行号 | forge 行号 | 语义要点 |
|---|---|---|---|
| `EffectPendingSource` 接口(`isActive` / `effect()`) | 65-72 | 66-73 | `effect()` 返回 null 表示不参与时长计算 |
| `registerEffectPendingSource(EffectPendingSource)` | 89-91 | 90-92 | 唯一注册入口 |
| `registerEffectPendingSource(Holder<MobEffect>)` | 94-106 | 95-107 | 效果驱动的"待定"源 |
| 静态块中的待定注册源(9 个) | 122-131 | 123-132 | 活体书页/激光/板砖/轨道炮/定向爆破/命运指引/王之力/狂暴/岿然不动 |
| `getMaxAllowed` | 135-152 | 136-153 | `min(9, 1 + 固定 + 临时 + 活体书页累计 + getBonusPlays)` |
| `getBonusPlays` / `grantBonusPlay` | 158-175 | 159-176 | 一次性 0/1 |
| `clearRoundBonuses` | 184-189 | 185-190 | **唯一入口**,调用点仅 `registerPlay` 边界 + `tick` 周期结束 |
| `isBurstFull` / `isCooldownActive` | 200-206 / 209-212 | 201-207 / 210-213 | — |
| `isEffectPending` | 216-223 | 217-224 | 遍历注册源 |
| `getRemainingBlockTicks` / `getRemainingBlockSeconds` / `remainingEffectTicks` | 227-237 / 240-242 / 244-247 | 228-238 / 241-243 / 245-248 | 剩余被锁时长 = `max(出牌冷却结束刻, now + 各源效果剩余)` |
| `isBlocked` | 256-260 | 257-261 | 打满 → 阻止;冷却中 → 放行;否则看效果待定 |

> ⚠️ 这套"效果待定/剩余被锁时长"是**效果牌**的机制,语义域不是立牌主动;新锁定态**不得**把立牌门控效果塞进 `EFFECT_PENDING_SOURCES`(会污染出牌锁判定)。

### 2.4 `item/sign/KomachiSignItem.java`(主动三条前置 + 提示 key + 被动)

```java
// neoforge-1.21.1/.../item/sign/KomachiSignItem.java:63-85  (forge: 63-85)
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (EffectCardPeriod.isCooldownActive(player)) {
            sendSignActionBar(player, "msg.astral_dice.komachi_active_cooldown");
            return InteractionResultHolder.fail(stack);
        }
        if (EffectCardPeriod.getMaxAllowed(player) >= GameplayConstants.MAX_EFFECT_CARD_PLAYS) {
            sendSignActionBar(player, "msg.astral_dice.komachi_active_capped",
                    GameplayConstants.MAX_EFFECT_CARD_PLAYS);
            return InteractionResultHolder.fail(stack);
        }
        // 一次性授予:本轮已授予过则不再释放(不消耗主动技能冷却)
        if (!EffectCardPeriod.grantBonusPlay(player)) {
            sendSignActionBar(player, "msg.astral_dice.komachi_active_used");
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.success(stack);
    }
```

被动(每 3 张效果牌 → 复制 + 冷却 −30% + 伤害加成 +1)与**冷却减免方之一**:

```java
// neoforge-1.21.1/.../item/sign/KomachiSignItem.java:131-141  (forge: 132-142)
    private static void reduceSignCooldown(Player player) {
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        if (cdEnd > 0) {
            long now = player.level().getGameTime();
            long remaining = cdEnd - now;
            if (remaining > 0) {
                ModAttachments.setSignActiveCooldownEnd(player, now + (long) (remaining * 0.7));
            }
        }
    }
```

> 注意:**用户第 2 条列出的减免方是「命运的指引 / 加急加快 / 史莱姆 −10 秒 / 电流核心」,没有把忍者被动 −30% 列进去**。若按字面,忍者被动在锁定期仍按现行为处理(`cdEnd > now` 才减,锁定期 `cdEnd == 0` ⇒ 空操作,既不入池也不报错)。见 §5-Q11。

### 2.5 `event/DiceCombatEvents.java` 三处待命释放写冷却

1.21.1 `:236-254`(占星师)/`:255-278`(秘密侦探)/`:279-296`(枪匠);forge `:241-259` / `:260-283` / `:284-302`。三处结构一致,每处结尾都是同一套"起冷却三连":

```java
// neoforge-1.21.1/.../combat/DiceCombatEvents.java:247-253  (占星师;forge: 252-258)
                ModEffectRemoval.remove(player, ModEffects.HAIQING_READY);
                int signCooldownTicks = WeirdDiceHandler.signCooldownTicks(player);
                ModAttachments.setSignActiveCooldownEnd(player,
                        player.level().getGameTime() + signCooldownTicks);
                // 路线 A:记录本次冷却实际使用的最大冷却值,所有减免方一律读它(不再各自重算基准)
                ModAttachments.setSignActiveMaxCooldown(player, signCooldownTicks);
                CurrentCoreChipItem.onActiveSkillUsed(player);
```

秘密侦探:`:271-277`(forge `:276-282`);枪匠:`:288-294`(forge `:294-300`,基准改用 `MosesSignItem.signCooldownTicks(player)`)。

### 2.6 `component/ModAttachments.java` — 两个键的两版本写法(照抄风格)

1.21.1(`NeoForge AttachmentType` / `DeferredRegister`):

```java
// neoforge-1.21.1/.../component/ModAttachments.java:399-427
    // 立牌主动技能冷却结束时刻(玩家级,不受立牌装卸影响;0 表示无冷却)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SIGN_ACTIVE_COOLDOWN_END =
            ATTACHMENTS.register("sign_active_cooldown_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .sync(ByteBufCodecs.VAR_LONG)
                    .build());

    public static long getSignActiveCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return player.getData(SIGN_ACTIVE_COOLDOWN_END.get());
    }

    public static void setSignActiveCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(SIGN_ACTIVE_COOLDOWN_END.get(), value);
    }

    // 立牌主动技能"本次冷却实际使用的最大冷却 tick"(路线 A:起冷却时与 SIGN_ACTIVE_COOLDOWN_END 成对写入,
    // 所有减免方一律读它作基准,不再各自重算;0 表示缺失/无冷却,减免方回退旧行为;仅服务端使用,无需同步客户端)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SIGN_ACTIVE_MAX_COOLDOWN =
            ATTACHMENTS.register("sign_active_max_cooldown", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());
```

1.20.1(`component/AttachedDataKey` + `AstralData` Capability):

```java
// forge-1.20.1/.../component/ModAttachments.java:371-395
    public static final AttachedDataKey<Long> SIGN_ACTIVE_COOLDOWN_END =
            register(AttachedDataKey.builder("sign_active_cooldown_end", Codec.LONG, () -> 0L).sync().build());
    ...
    public static final AttachedDataKey<Long> SIGN_ACTIVE_MAX_COOLDOWN =
            register(AttachedDataKey.builder("sign_active_max_cooldown", Codec.LONG, () -> 0L).build());
```

同步清单(forge 专有,新增 synced 键必须在此登记):

```java
// forge-1.20.1/.../component/ModAttachments.java:834-866(节选)
    static List<AttachedDataKey<?>> syncedKeys() {
        if (SYNCED_KEYS.isEmpty()) {
            ...
            SYNCED_KEYS.add(SIGN_ACTIVE_COOLDOWN_END);   // :846
            SYNCED_KEYS.add(SIGN_READY_TYPE);
            SYNCED_KEYS.add(SIGN_READY_EXPIRE);
            ...
        }
        return SYNCED_KEYS;
    }
```

### 2.7 `event/PlayerTickEvents.java` — 玩家级 tick 挂点(forge 每 tick 两次)

```java
// neoforge-1.21.1/.../event/PlayerTickEvents.java:110-119
    @SubscribeEvent
    public static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        EffectTimerGuard.tick(player);
        BaseSignItem.tickSignReadyTimeout(player);
    }
```

```java
// forge-1.20.1/.../event/PlayerTickEvents.java:108-118
    @SubscribeEvent
    public static void onPlayerTickPre(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (player.level().isClientSide()) return;
        EffectTimerGuard.tick(player);
        // (本方法随 PlayerTickEvent 的 START/END 两个阶段各执行一次,重置逻辑幂等,无副作用)
        BaseSignItem.tickSignReadyTimeout(player);
    }
```

出牌周期 tick 挂点:`onPlayerTick`(1.21.1 `:123-147`,其中 `EffectCardPeriod.tick(player)` 在 `:141`,且被 `if (player.tickCount % 20 != 0) return;`(`:137`)节流;forge `:122-146`,`:140`,`:136`)。

### 2.8 lang 四个文件里与立牌主动提示相关的既有 key 清单

| key | 1.21.1 / forge `zh_cn.json` 行号 | 1.21.1 / forge `en_us.json` 行号 | 现有中文值 |
|---|---|---|---|
| `hud.astral_dice.sign_active` | 262 | 262 | `"%s:技能已激活!"`(**代码 0 引用,孤儿键**) |
| `hud.astral_dice.sign_active_cooldown` | 263 | 263 | `"%s:主动技能冷却中!"`(`BaseSignItem.java:94/95` 使用) |
| `hud.astral_dice.sign_ready` | 264 | 264 | `"技能已激活,攻击目标以释放!"`(**代码 0 引用**) |
| `msg.astral_dice.sign_active_triggered` | 416 | 416 | `"%s:主动技能已启动!"` |
| `msg.astral_dice.komachi_active` | 592 | 592 | `"忍者立牌:已获得效果牌出牌数+1,剩余出牌数:%d"` |
| `msg.astral_dice.komachi_active_capped` | 593 | 593 | `"...出牌数已达上限(%d 张),主动技能未释放"` |
| `msg.astral_dice.komachi_active_used` | 594 | 594 | `"...本轮出牌数 +1 已生效(一次性),主动技能未释放"` |
| `msg.astral_dice.komachi_active_cooldown` | 595 | 595 | `"...效果牌冷却中,主动技能未释放"` |
| `msg.astral_dice.haiqing_ready` / `bonnie_ready` / `moses_ready` / `moses_apply` | 412 / 394 / 395 / 396 | 同 | 待命提示 |
| `hud.astral_dice.current_core_not_enough` / `current_core_finish` | 见 `chip` 段(`CurrentCoreChipItem.java:91/100`) | 同 | 电流核心提示 |
| `tooltip.astral_dice.sign.cooldown_remaining` | 548 | 548 | `"冷却中:§c%s§c 秒"`(tooltip 用;唯一读 `sign_active_cooldown_end` 的显示) |

> 新键 `msg.astral_dice.sign_active_in_effect` 建议插入:**zh_cn/en_us 第 416 行相邻**(与同类 `msg.astral_dice.sign_active_triggered` 同段;两版本行号一致)。备选:放在 262-264 段(与 `sign_active_cooldown` 同段,该段是 `hud.*` 前缀,与 `msg.*` 命名不一致,不推荐)。
> `item/sign/` 目录下 grep `astral_dice\.(sign|komachi|haiqing|moses|bonnie|lulu|jasmine)` 的实际 Java 引用点:GUI 侧只有 `BaseSignItem.java:94`(冷却)与 `:111`(启动)、`KomachiSignItem.java:71/75/81/94`、`HaiqingSignItem.java:63`、`MosesSignItem.java:62/134`、`BonnieSignItem.java:63`。

---

## §3 设计方案(可直接据以实施的最小 diff 方向)

### 3.1 新增玩家级状态(附件键)

至少 4 个(推荐 5 个):

| 键名(两版本同名) | 类型 | 用途 | 1.21.1 写法 | 1.20.1 写法 |
|---|---|---|---|---|
| `sign_active_lock_sign` | `String` | 锁定态标记:哪个立牌的主动正在生效中;`""` = 未锁定 | `.serialize(Codec.STRING)`(不同步) | `AttachedDataKey.builder("sign_active_lock_sign", Codec.STRING, () -> "")`(不 `.sync()`) |
| `sign_active_lock_end` | `Long` | 该技能施加的**全部计时器**的到期刻(取 max),作为硬上界/兜底判据 | `.serialize(Codec.LONG)` | `.builder("sign_active_lock_end", Codec.LONG, () -> 0L)` |
| `sign_active_reduction_pool` | `Long` | 锁定期间累计的冷却减免池(tick) | `.serialize(Codec.LONG)` | 同上 |
| `sign_active_lock_grace_end` | `Long` | **忍者专用**宽限到期刻(触发主动时刻 + 1200;0 = 不适用) | `.serialize(Codec.LONG)` | 同上 |
| `sign_active_lock_played` | `Boolean` | **忍者专用**宽限期内是否已出过效果牌 | `.serialize(Codec.BOOL)` | `.builder(..., Codec.BOOL, () -> false)` |

草稿(1.21.1,紧随 `SIGN_ACTIVE_MAX_COOLDOWN` 之后插入,风格照抄):

```java
    // 立牌主动技能"锁定(生效中)"态:记录正在生效中的主动所属立牌(物品注册 id;"" = 未锁定)。
    // 判定方式见 BaseSignItem#isSignActiveLocked:以"该技能施加的计时器是否仍在跑"为准。
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> SIGN_ACTIVE_LOCK_SIGN =
            ATTACHMENTS.register("sign_active_lock_sign", () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING)
                    .build());

    // 锁定态到期刻(全部计时器取 max 的硬上界;效果实例被提前移除时以"效果是否还在"为准)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SIGN_ACTIVE_LOCK_END =
            ATTACHMENTS.register("sign_active_lock_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    // 锁定期间累计的冷却减免池(tick):锁定结束时一次性从"起冷却基准"中抵扣并归零
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SIGN_ACTIVE_REDUCTION_POOL =
            ATTACHMENTS.register("sign_active_reduction_pool", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    public static long getSignActiveReductionPool(net.minecraft.world.entity.player.Player player) {
        return player.getData(SIGN_ACTIVE_REDUCTION_POOL.get());
    }

    public static void addSignActiveReductionPool(net.minecraft.world.entity.player.Player player, long delta) {
        player.setData(SIGN_ACTIVE_REDUCTION_POOL.get(),
                Math.max(0L, player.getData(SIGN_ACTIVE_REDUCTION_POOL.get()) + delta));
    }

    // 忍者立牌专用:宽限期到期刻(触发主动时刻 + 1:00)与"期内是否已出过效果牌"
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SIGN_ACTIVE_LOCK_GRACE_END =
            ATTACHMENTS.register("sign_active_lock_grace_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> SIGN_ACTIVE_LOCK_PLAYED =
            ATTACHMENTS.register("sign_active_lock_played", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build());
```

草稿(1.20.1,紧随 `SIGN_ACTIVE_MAX_COOLDOWN` 之后插入):

```java
    // 立牌主动技能"锁定(生效中)"态 / 到期刻 / 减免累计池 / 忍者宽限(仅服务端使用,故不 .sync()、不入 SYNCED_KEYS)
    public static final AttachedDataKey<String> SIGN_ACTIVE_LOCK_SIGN =
            register(AttachedDataKey.builder("sign_active_lock_sign", Codec.STRING, () -> "").build());
    public static final AttachedDataKey<Long> SIGN_ACTIVE_LOCK_END =
            register(AttachedDataKey.builder("sign_active_lock_end", Codec.LONG, () -> 0L).build());
    public static final AttachedDataKey<Long> SIGN_ACTIVE_REDUCTION_POOL =
            register(AttachedDataKey.builder("sign_active_reduction_pool", Codec.LONG, () -> 0L).build());
    public static final AttachedDataKey<Long> SIGN_ACTIVE_LOCK_GRACE_END =
            register(AttachedDataKey.builder("sign_active_lock_grace_end", Codec.LONG, () -> 0L).build());
    public static final AttachedDataKey<Boolean> SIGN_ACTIVE_LOCK_PLAYED =
            register(AttachedDataKey.builder("sign_active_lock_played", Codec.BOOL, () -> false).build());

    public static long getSignActiveReductionPool(net.minecraft.world.entity.player.Player player) {
        return SIGN_ACTIVE_REDUCTION_POOL.get(player);
    }

    public static void addSignActiveReductionPool(net.minecraft.world.entity.player.Player player, long delta) {
        SIGN_ACTIVE_REDUCTION_POOL.set(player, Math.max(0L, SIGN_ACTIVE_REDUCTION_POOL.get(player) + delta));
    }
```

**是否需要 `.sync()`?——推荐:全部不同步。** 理由:

1. 第 4 条只要求 **ActionBar 提示**(服务端 `notifyActionBar` 直接发包),不要求 tooltip;
2. tooltip 目前只在 `event/ModTooltipHandler.java:248-257` 读 `sign_active_cooldown_end` 显示"冷却中",锁定态**不新增显示**即可零改动;
3. 一旦给锁定键加 sync,forge 侧必须同时 `SYNCED_KEYS.add(...)`(`ModAttachments.java:834-866`,现 28 项),并牵动 AGENTS.md 里"28 个 synced 键"的既有表述 —— 属额外风险面。
4. 客户端不参与锁定期判定(锁定期判定只在服务端 `performSkill` / `tryFinishCooldown` / 玩家级 tick),故不同步不影响正确性。

### 3.2 状态机(可用 → 锁定 → 冷却 → 可用)

```
                    ┌───────────────────────────── 可用(无锁、无冷却)────────────────────────────┐
                    │ handleUse == SUCCESS                                                      │
                    │  · 该立牌登记了计时器(startActiveLockOnUse == true)  ──► 锁定             │
                    │  · 无计时器,或 sign_ready_type > 0(三类待命技能)      ──► 冷却            │
                    └───────────────────────────────────────────────────────────────────────────┘
锁定:  locked = (lock_sign != "") && ( 任一"门控计时器"仍在跑 || now < lock_end )
       · 按键 → ActionBar「主动技能生效中!」,直接 return(优先于冷却分支、优先于电流核心)
       · 真伤/效果牌等其它系统不受影响(锁定只拦主动技能按键)
       ├─ 门控计时器全部跑完 ──► 起冷却:effective = max(0, base - pool);pool = 0;清锁
       └─(忍者)宽限 logic(见 3.6)
冷却:  cdEnd > 0 && now < cdEnd
       · 按键 → CurrentCoreChipItem.tryFinishCooldown(既有行为:按占比耗充能立即完成)
       · 期间所有减免方**直接改 cdEnd**(既有行为,基准 = sign_active_max_cooldown)
       └─ now >= cdEnd ──► 可用(不改写任何值,现有"读判定"语义)
```

**判定函数签名(建议全部放 `BaseSignItem`,public static,供 `performSkill` / 各减免方 / `CurrentCoreChipItem` / 玩家级 tick / `EffectCardPeriod` 共用)**:

```java
    /** 锁定态判定:主动技能是否仍处于"生效中"(第 1 条)。 */
    public static boolean isSignActiveLocked(Player player);

    /** 锁定态剩余 tick(0 = 未锁定或已跑完)。 */
    public static long signActiveLockRemainingTicks(Player player);

    /** 正在生效中的主动所属立牌注册 id("" = 未锁定)。 */
    public static String getSignActiveLockSignId(Player player);

    /** 进入锁定态(普通计时类):由各立牌在 handleUse 内/performSkill 内调用,返回是否成功进入。 */
    public static boolean beginActiveLock(Player player, String signId, long lockEndTick);

    /** 进入锁定态(效果门控类):gateEffects 为本次技能实际施加到自身的效果;取 max 到期刻。 */
    public static boolean beginActiveLock(Player player, String signId,
                                          java.util.List<net.minecraft.core.Holder<MobEffect>> gateEffects);

    /** 玩家级 tick(幂等):锁定结束时起冷却;忍者宽限判定。挂在 PlayerTickEvents.onPlayerTickPre。 */
    public static void tickSignActiveLock(Player player);

    /** 出牌轮"完全重置"回调:由 EffectCardPeriod 的唯一两处周期边界调用(第 3 条忍者专用)。 */
    public static void onEffectCardRoundReset(Player player);

    /** 各立牌覆写:登记本主动施加的计时器;返回 true = 需要进入锁定态。默认 false。 */
    protected boolean startActiveLockOnUse(Player player, long now);
```

迁移条件(精确):

| 迁移 | 触发条件 | 动作 |
|---|---|---|
| 可用 → 锁定 | `handleUse` 返回 `SUCCESS`,且 `sign.startActiveLockOnUse(player, now)` 为 true | 写 `lock_sign` / `lock_end`;`setSignActiveMaxCooldown(base)`(**提前写基准**,见 3.4);`CurrentCoreChipItem.onActiveSkillUsed(player)`;**不写** `sign_active_cooldown_end` |
| 可用 → 冷却 | `handleUse` SUCCESS 且 `startActiveLockOnUse == false`,并且 `getSignReadyType(player) <= 0` | 逐字保留现有三连(`BaseSignItem.java:119-127`) |
| 锁定 → 冷却 | `!任何门控计时器仍在跑` | `effective = max(0, getSignActiveMaxCooldown - getSignActiveReductionPool)`;`setSignActiveCooldownEnd(now + effective)`;`setSignActiveMaxCooldown(effective)`;`pool = 0`;清空 5 个锁定键;`onActiveSkillUsed` 已在锁定开始时给过,**不重复** |
| 锁定(忍者)→ 冷却 | 见 3.6 | 同上(但 `lock_end` 为 0,由出牌周期驱动) |
| 冷却 → 可用 | `now >= cdEnd`(**只读判定,不写值**;`tryFinishCooldown` 会显式写 `cdEnd = now`) | 无 |
| 任意 → 可用(异常兜底) | 锁定态下门控效果被外部移除/玩家死亡重生 ⇒ 新实体默认值 | 由 3.3 的判据自然覆盖,不会永久锁死 |

### 3.3 "计时器是否跑完"如何判定(优先复用既有机制)

**设计:门控计时器 = 主动在自身身上施加的效果实例 + 主动写入的自身附件到期刻**,统一登记为 `lock_end`(取 max),判定时再叠一层"效果实例仍在"的 OR:

```java
    public static boolean isSignActiveLocked(Player player) {
        String signId = ModAttachments.getSignActiveLockSign(player);
        if (signId == null || signId.isEmpty()) return false;
        long now = player.level().getGameTime();
        if (now < ModAttachments.getSignActiveLockEnd(player)) return true;          // L2/附件型或效果仍按 20t/s 跑
        return anyGateEffectActive(player, signId);                                  // L1/效果实例型(守卫会纠正时长,实例在即仍在跑)
    }
```

- 复用 **`EffectTimerGuard`** 已有的"本模组效果严格 20t/s"保证:`EffectTimerGuard.record`(`:77-92`)在施加时登记结束刻、`tick`(`:95-147`)每 tick 把剩余时长拉回预期 ⇒ **效果实例的 `getDuration()` 就是权威剩余时长**,不需要自建计时器。
- `remainingEffectTicks` 的写法直接照抄 `EffectCardPeriod.java:244-247`(但**不要**复用 `EFFECT_PENDING_SOURCES`,语义域不同)。
- 对**随机效果**(`parunan` / `fanny`)与**附件驱动**(`nancy_lu_hidden_until`、`sign_ready_expire`)这类"无法用固定效果常量覆盖"的立牌,最小扩展方式是:**立牌在施加效果处自行登记**(`startActiveLockOnUse` 里拿到刚施加的 `MobEffectInstance` / 刚写入的 `*_until`),不新增全局注册表。

逐立牌适用性:

| 立牌 | 门控来源 | 适用性 |
|---|---|---|
| `parunan` | 随机到的 `SATURATION` / `LUCK` / `HERO_OF_THE_VILLAGE` 实例 | ✅ 施加重构:`MobEffectInstance effect = ...; EffectTimerGuard.apply(player, effect); beginActiveLock(player, "parunan_sign", effect);` |
| `jasmine` | `JASMINE_SWEEP` 2400(另两个同长) | ✅ 登记 `JASMINE_SWEEP`(取 max 后等价) |
| `misaki` | `MISAKI_BURST` 2400 | ✅ |
| `papara` | `PAPARA_BITE` 3600 | ✅ |
| `fen` | `FEN_FRENZY` 1200(+`MOVEMENT_SPEED` 1200) | ✅ 取 max |
| `nancy_lu` | `INVISIBILITY` 600 + `nancy_lu_hidden_until`(=now+600) | ✅ 两来源同长;`lock_end = now+600`,并让 `anyGateEffectActive` 认 `INVISIBILITY` ⇒ 攻击提前破隐(`NancyLuSignItem.java:128-151` 会 `removeEffect`)时**锁定提前结束** |
| `fanny` | 9/11 分支各自施加的效果实例 | ✅ 在 `applyEvent` 各分支内登记(瞬时 `HARM` 1t 分支登记 0 ⇒ 不锁) |
| `komachi` | 无(第 3 条专用) | 走 3.6 |
| 其余 9 个 | 不登记 | `startActiveLockOnUse` 保持默认 `false` ⇒ 与现状逐字一致 |

**无法用既有机制覆盖的情形(如实记录)**:`hair`类"技能整体窗口"而非"效果"的设计(如把待命窗口当计时器)不在本方案内;效果被外模组**延长**时本方案不会延长锁定(取登记时刻的 max + 实例仍在,不追加重置),效果被外模组**提前移除**时锁定提前结束 —— 两者都与"等本技能施加的计时器跑完"的字面一致,但需用户确认(§5-Q12)。

### 3.4 减免入池(逐个减免方:改前 → 改后)

**共同前提:锁定开始时就把"本次冷却实际使用的基准"提前写入 `sign_active_max_cooldown`**(与起冷却同一算式,例如普通立牌 `WeirdDiceHandler.signCooldownTicks(player)`、枪匠走不到锁定、忍者同为 `WeirdDiceHandler.signCooldownTicks`)。这样四个减免方**读的键不变**(仍是 `sign_active_max_cooldown`),最小 diff。

#### (A) 命运的指引 `FateGuidanceCardItem.reduceActiveSkillCooldown`

- 1.21.1 `item/card/FateGuidanceCardItem.java:60-77`;forge `:59-76`。

改前(现状):

```java
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        long now = player.level().getGameTime();
        if (cdEnd > now) {                                   // 冷却为 0 或已过期:直接返回
            long remaining = cdEnd - now;
            long maxCooldown = ModAttachments.getSignActiveMaxCooldown(player);
            if (maxCooldown <= 0) maxCooldown = WeirdDiceHandler.signCooldownTicks(player);
            long reduction = maxCooldown / 2;
            ModAttachments.setSignActiveCooldownEnd(player, now + Math.max(0, remaining - reduction));
        }
```

改后(锁定分支插在**最前**,连 `cdEnd > now` 都不进入):

```java
        long now = player.level().getGameTime();
        long maxCooldown = ModAttachments.getSignActiveMaxCooldown(player);
        if (maxCooldown <= 0) maxCooldown = WeirdDiceHandler.signCooldownTicks(player);
        long reduction = maxCooldown / 2;
        if (BaseSignItem.isSignActiveLocked(player)) {                 // ★ 新增:锁定期只入池,不改 cdEnd
            ModAttachments.addSignActiveReductionPool(player, reduction);
            return;
        }
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        if (cdEnd > now) {
            long remaining = cdEnd - now;
            ModAttachments.setSignActiveCooldownEnd(player, now + Math.max(0, remaining - reduction));
        }
```

#### (B) 加急加快 `JasmineSignItem.onExpressDeliveryUsed`

- 1.21.1 `item/sign/JasmineSignItem.java:82-99`;forge `:83-100`。调用点 `item/card/BaseEffectCardItem.java:233-236`。
- 改前:`if (cdEnd > now) { maxCooldown = getSignActiveMaxCooldown 或 SIGN_ACTIVE_COOLDOWN_TICKS; set(cdEnd - maxCooldown/2, 夹底 now) }`。
- 改后:在 `if (cdEnd > now)` 之前插入

```java
        long maxCooldown = ModAttachments.getSignActiveMaxCooldown(player);
        if (maxCooldown <= 0) maxCooldown = GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;
        if (BaseSignItem.isSignActiveLocked(player)) {                 // ★ 锁定期只入池
            ModAttachments.addSignActiveReductionPool(player, maxCooldown / 2);
            return;
        }
```

#### (C) 史莱姆 −10 秒 `LuluSignItem.onHurt`

- 1.21.1 `item/sign/LuluSignItem.java:44-60`(53-57);forge `:44-60`(53-57)。
- 改前:`if (cdEnd > nowTick) set(max(nowTick, cdEnd - 200));`
- 改后:

```java
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        if (BaseSignItem.isSignActiveLocked(player)) {                 // ★ 锁定期入池(绝对量 200t,与基准无关)
            ModAttachments.addSignActiveReductionPool(player, 200L);
        } else if (cdEnd > nowTick) {
            ModAttachments.setSignActiveCooldownEnd(player, Math.max(nowTick, cdEnd - 200));
        }
```

#### (D) 电流核心 `CurrentCoreChipItem.tryFinishCooldown`

- 1.21.1 `item/chip/CurrentCoreChipItem.java:83-102`;forge `:83-102`。
- 改后(锁定态**不触发、不入池、不扣充能**;提示由 `performSkill` 的锁定分支统一发出):

```java
    public static int tryFinishCooldown(Player player, long cooldownEnd, long now) {
        if (player == null || player.level().isClientSide()) return FINISH_NONE;
        if (BaseSignItem.isSignActiveLocked(player)) return FINISH_NONE;   // ★ 纵深防御(锁定期不得消耗充能)
        if (!isEquipped(player)) return FINISH_NONE;
        ...
```

- ⚠️ 与用户第 2 条的字面存在口径冲突(第 2 条把"电流核心"列为入池方,第 5 条却禁止它在锁定期被触发)⇒ §5-Q3。

#### 池消费(唯一出口)

```java
    private static void endLockAndStartCooldown(Player player) {
        long now = player.level().getGameTime();
        long base = ModAttachments.getSignActiveMaxCooldown(player);
        long pool = ModAttachments.getSignActiveReductionPool(player);
        long effective = Math.max(0L, base - pool);                 // 一次性抵扣
        ModAttachments.setSignActiveCooldownEnd(player, now + effective);
        ModAttachments.setSignActiveMaxCooldown(player, effective); // 电流核心档位分母 = 实际冷却
        ModAttachments.setSignActiveReductionPool(player, 0L);
        ModAttachments.setSignActiveLockSign(player, "");
        ModAttachments.setSignActiveLockEnd(player, 0L);
        ModAttachments.setSignActiveLockGraceEnd(player, 0L);
        ModAttachments.setSignActiveLockPlayed(player, false);
    }
```

> 分母口径:抵扣后实际冷却 = `effective`(推荐,留给 §5-Q4 定夺)。

### 3.5 第 4/5 条:`performSkill` 判定顺序 + 提示发送

```java
    private static void performSkill(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof BaseSignItem sign)) return;
        long now = player.level().getGameTime();
        net.minecraft.network.chat.Component signName = stack.getHoverName();
        // 0. ★ 新增:锁定态优先于冷却态(第 1/4 条)——锁定期内按键无效,且电流核心不得介入(第 5 条)
        if (isSignActiveLocked(player)) {
            notifyActionBar(player, "msg.astral_dice.sign_active_in_effect", signName, ChatFormatting.YELLOW);
            return;
        }
        // 1. 玩家级冷却检查(原样保留,含 CurrentCoreChipItem.tryFinishCooldown)
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        if (cdEnd > 0 && now < cdEnd) { ...原样... }
        // 2. 等待状态检查(原样)
        if (isSkillWaiting(player)) return;
        // 3. handleUse(原样)
        ...
        // 6. 冷却/锁定(改造点)
        if (ModAttachments.getSignReadyType(player) <= 0) {
            if (sign.startActiveLockOnUse(player, now)) {                 // ★ 会施加计时器 → 进入锁定态
                /* startActiveLockOnUse 内部:写 lock_sign/lock_end/grace/played
                   + setSignActiveMaxCooldown(base) 提前写基准 */
                com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem.onActiveSkillUsed(player);
            } else {
                int signCooldownTicks = com.merlinkitsune.astral_dice.event.WeirdDiceHandler.signCooldownTicks(player);
                ModAttachments.setSignActiveCooldownEnd(player, now + signCooldownTicks);
                ModAttachments.setSignActiveMaxCooldown(player, signCooldownTicks);
                com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem.onActiveSkillUsed(player);
            }
        }
    }
```

- 发送方式**复用 `notifyActionBar`**(黄/红由参数给;第 4 条未指定颜色,建议 `YELLOW` 与"已启动!"一致)。
- 新 lang 值必须带 `%s`(见 2.1 注)。
- 忍者:由 `startActiveLockOnUse` 返回 true 但写入宽限态(`lock_end = 0`、`grace_end = now + 1200`),**不写** `sign_active_cooldown_end`。

### 3.6 忍者:锁定态如何与 `EffectCardPeriod` 周期重置对接

**"周期完全重置"的唯一可挂钩时刻 = 恰好两处,都在 `EffectCardPeriod`**:

1. `tick` 的**情形 1**(1.21.1 `:354-360` / forge `:355-361`):`setEffectCardCooldownEnd(0)` + `setEffectCardPlayCount(0)` + `clearRoundBonuses(player)` —— 这是"冷却到期后计数归零"的周期结束;
2. `registerPlay` 的**周期边界块**(1.21.1 `:279-286` / forge `:280-287`):同样三件事。

> **不是** `tick` 的情形 2/3(1.21.1 `:339-353`):那里只启动 30 秒出牌冷却,**不清零 count**(明确注释"不作废剩余出牌数"),不构成"完全重置"。
> **不是** `clearRoundBonuses` 本身:它只清 4 个"每轮一次"标记,与 count/cooldown 解耦。

最小对接方式:新增 `EffectCardPeriod.onRoundFullyReset(Player)`,在上述**两处**紧接 `clearRoundBonuses(player)` 之后调用(与它并列),内部转调 `BaseSignItem.onEffectCardRoundReset(player)`;`onEffectCardRoundReset` 只在 `lock_sign == "komachi_sign"` 时起冷却(`endLockAndStartCooldown`)。这样"唯一入口"纪律不破(`clearRoundBonuses` 仍只有两处调用,新钩子也只两处)。

**忍者宽限 1:00 的计时载体与判定位置**:

- 载体:附件 `sign_active_lock_grace_end = now + 1200`(触发主动时写入)。
- 判定位置:玩家级 tick —— `PlayerTickEvents.onPlayerTickPre` 里新增 `BaseSignItem.tickSignActiveLock(player);`(1.21.1 `:118` 之后 / forge `:117` 之后)。
- 幂等性(forge 每 tick 触发两次):函数首行 `if (getSignActiveLockSign(player).isEmpty()) return;`;真正迁移只发生一次(迁移后 `lock_sign` 被清空)⇒ 第二次执行直接早退,与 `tickSignReadyTimeout` 同构。
- **"未出牌"的确切判据(推荐)**:新增 `sign_active_lock_played`;
  - 唯一置真入口 = `EffectCardPeriod.registerPlay`(1.21.1 `:270` / forge `:271`)的开头:
    ```java
        if (BaseSignItem.isSignActiveLocked(player)) {
            ModAttachments.setSignActiveLockPlayed(player, true);
        }
    ```
    (`registerPlay` 全仓唯一调用点 = `item/card/BaseEffectCardItem.java:220`,服务端路径,权威。)
  - 宽限到期判定:
    ```java
        if (now >= ModAttachments.getSignActiveLockGraceEnd(player)) {
            if (!ModAttachments.getSignActiveLockPlayed(player)) {
                // 期内自始至终未出任何效果牌 ⇒ 强制重置出牌状态 + 起冷却
                ModAttachments.setEffectCardCooldownEnd(player, 0);
                ModAttachments.setEffectCardPlayCount(player, 0);
                EffectCardPeriod.clearRoundBonuses(player);
                endLockAndStartCooldown(player);
            } else {
                // 一旦出过牌 ⇒ 保险失效:清宽限刻,等 registerPlay/tick 的自然重置
                ModAttachments.setSignActiveLockGraceEnd(player, 0);
            }
        }
    ```
  - **为什么不建议** `effect_card_play_count == 0 && 未消耗 bonus`:
    ① `registerPlay` 的周期边界会把 count 归零(`EffectCardPeriod.java:282`),于是"授予 +1 后未出牌"与"周期刚重置"两种情形 count 都是 0,无法区分;
    ② `clearRoundBonuses` 会把 `effect_card_bonus_plays` 清 0(`:185`),而宽限期的"是否出过牌"必须**跨该边界**保持为真 ⇒ bonus 不能当判据;
    ③ 用户口径是"未出任何**效果牌**",而 bonus 是"授予",语义不同。

### 3.7 边界:逐条结论

| # | 边界 | 结论 | 是否需要在现有钩子上改动 |
|---|---|---|---|
| ① | 锁定期内**死亡** | **保留 = 随新实体自然清空**。5 个新键都不是 `copyOnDeath`(1.21.1 附件默认不复制;1.20.1 `AstralData#onPlayerClone` 死亡分支只复制 `rin_pages`/`komachi_damage_bonus`)⇒ 重生后默认值;死亡**被取消**(末影骰子/安全气囊)时按 `PlayerLifecycleHandler.java:121` 的 `if (event.isCanceled()) return;` 早退 ⇒ 锁定保留(玩家没死,合理) | **无需改动**。不建议在 `LivingDeathEvent`(`:117-184`)里逐项写默认值(该文件 `:132-138` 明确记录"逐项写默认值是空操作"的既有裁决) |
| ② | **重登** | **保留**。5 个新键都 `.serialize(...)`(1.21.1)/ 持久化(1.20.1 Capability)⇒ 跨重登保留;原版存档也保留 `MobEffectInstance`;登录后第一次玩家级 tick 重新判定:若门控计时器已跑完 ⇒ 解锁并起冷却 | **无需改动**;特别地**不要**在 `PlayerLoggedInEvent`(`:200-213`)里清锁(否则重登可刷掉锁定/冷却) |
| ③ | **换/卸立牌(含死亡掉落)** | **保留**(与冷却同为玩家级,现状注释 `BaseSignItem.java:203-204` 已明确"主动技能冷却为玩家级,不受立牌装卸影响")。**各立牌 `clearSignData` 不得触碰** `sign_active_lock_*` / `sign_active_reduction_pool` | 需人工核对 16 个 `clearSignData` 均不改新键;**特例**:`NancyLuSignItem.clearSignData`(`:88-104`)会 `removeEffect(INVISIBILITY)`(`:95`)与 `NANCY_LU_HACK`(`:102`)⇒ 卸牌会使 `anyGateEffectActive` 转假 ⇒ 锁定提前结束并起冷却(推荐保留该行为:卸牌即失去效果,计时器自然终止) |
| ④ | 锁定期结束那一刻**玩家不在线** | 离线不 tick ⇒ 下次上线后的第一次判定才迁移到冷却(等效于"离线期间冷却不走") | **无需改动**(现有冷却同样由 tick/事件里的 `getGameTime()` 读判定驱动)。备选"按 `lock_end` 回填冷却结束刻"见 §5-Q6 |
| ⑤ | **忍者宽限期内死亡** | 宽限/played 附件非 `copyOnDeath` ⇒ 重生后清空 ⇒ **不会**补算主动冷却(与现状一致:今天忍者按主动后立即起冷却,死亡同样清空冷却——`DeathPreservedBonuses` 只保 `rin_pages`/`komachi_damage_bonus`)。**不产生新的"可刷冷却"路径** | **无需改动**。若用户要求"死亡也要清算",需仿 `component/DeathPreservedBonuses.java:28-59` 新增暂存(不推荐,会把死亡变成更重惩罚) |

---

## §4 风险与不确定

### 4.1 会破坏既有工具链断言的地方(已实际读到,必须同批修改或明确豁免)

1. **`scripts/test/resources/kubejs/1.21.1/server_scripts/astral_bugfix_probe.js`(forge 同名文件行号 −9 ~ −11)**
   - `doKomachiCast`(`:1096-1122`)在 `BaseSignItemClass.performSkillForCurio(p)` 之后直接断言"冷却已开始":
     ```js
     // :1118
     var ok = (extraAfter === 1) && (maxAfter === maxBefore + 1) && (cd > 0);
     ```
     新规则下忍者**不立即起冷却**(改为"出牌轮完全重置那一刻"起冷却)⇒ `AP_*_RELEASED` 必然变 0。该用例必须改写(或改为断言"锁定期内 cd==0 且再次按键被拒")。
   - `resetEffectCardCycle`(`:1067-1075`)只重置 `effect_card_*` + `sign_active_cooldown_end`(`:1071`)。**新增 5 个锁定键后必须同步扩该脚手架**,否则锁定态会跨用例残留,读数变成顺序相关。
   - `doKomachiRepeat`(`:1130-1160`)刻意把 `sign_active_cooldown_end` 置为"已过期但非 0"(`:1142`)以越过冷却分支。新锁定分支在冷却分支**之前** ⇒ 若上一条用例留下了锁,该用例会走进锁定分支而失败 ⇒ 必须先清锁。
2. **`scripts/test/resources/kubejs/*/server_scripts/astral_dice_target_select_check.js`**(1.21.1 `:92`、1.20.1 `:110`)写 `ModAttachments.setSignActiveCooldownEnd(p, 0);`。若采纳"三类待命技能不进入锁定态"的推荐口径,则该脚本不受影响;若采纳相反口径,需同步清锁。
3. **`scripts/test/cases/KOMACHI-EXTRA-PLAY-1.21.1.json` / `...-1.20.1.json`(第 12 行 `text`)**:用例说明文字逐字描述了"③K1 正常释放(加成 0→1、上限 +1、主动冷却起算)"与"⑥K6 效果牌冷却进行中必须拒绝且不改写…主动冷却" —— 这两条口径在第二批下**过时**,用例文本与断言都要改。
4. **`event/ModTooltipHandler.java:248-257`** 是唯一读 `sign_active_cooldown_end` 的显示方。锁定期该值为 0 ⇒ tooltip 不显示任何"进行中"信息(第 4 条只要求 ActionBar,故可接受)。若后续要 tooltip 显示"生效中",则必须给锁定键加同步,并牵动 forge `SYNCED_KEYS`(现 28 项,`ModAttachments.java:834-866`)与 AGENTS.md 中"28 个 synced 键"的表述。

### 4.2 我未能核实的点

1. **未跑构建/未跑游戏内验证**(本阶段只读):`startActiveLockOnUse` 的覆写点、`Codec.STRING`/`Codec.BOOL` 在 1.21.1 `AttachmentType.builder(...).serialize(...)` 下是否有额外约束(现有代码未用过 `Codec.STRING`,本方案是首次),需要实施阶段实测。
2. **未能核实外模组对本模组效果时长的影响**:`EffectTimerGuard` 会针对"本模组自定义效果"与经 `apply` 包装的原版效果做 20t/s 校正;`PaparaSignItem.java:35` 用的是裸 `player.addEffect(...)`,**其记录来自 `MobEffectEvent.Added`**(`EffectTimerGuard` 类注释 `:32-33`),我没有核实该 Added 监听器是否对所有 `astral_dice:*` 效果都生效(若某效果未被记录,其实例时长可能被外部加速 ⇒ 锁定提前/延后)。建议实施时确认 `MobEffectEvent.Added` 的注册点。
3. **未能核实"锁定期内其他来源刷新同一效果"的行为**:本方案取"登记时刻的 max 到期刻" + "实例仍在"的 OR,若玩家身上本来就有更长的同类效果(例如已有的迅捷 10:00),`lock_end` 更短 ⇒ 锁定按本技能时长结束(不延长);反之若锁定期内被刷新,也不会延长。需用户确认这是否符合"必须等该技能施加的全部计时器跑完"。
4. **未能核实 `fanny` 的 `farmersdelight:nourishment` 分支**(`FannySignItem.java:91-99`)在未装农夫乐事时的行为对锁定的影响:该分支 `catch (Exception ignored)` 静默失败、不施加任何效果 ⇒ 该次主动无门控 ⇒ **应立即起冷却**(而不是空转锁定)。实施时必须按"实际施加成功与否"决定是否进入锁定。
5. **未能核实 `EffectCardPeriod.registerPlay` 在客户端是否会被调用**:唯一调用点 `BaseEffectCardItem.java:220` 在服务端路径,但若存在任何客户端调用,则写 `sign_active_lock_played` 会污染服务端判定(1.20.1 `AttachedDataKey.set` 在客户端是 no-op,风险较低;1.21.1 `player.setData` 在客户端只写本地)。实施时加 `if (player.level().isClientSide()) return;` 防御。
6. **未核实 AGENTS.md 是否要求"新增玩家附件键必须同步更新文档中的键数量"**(AGENTS.md 明确写了"1.21.1 = 62、1.20.1 = 64"与"28 个 synced 键")。本批新增 5 个持久化键(不新增 synced 键)⇒ 若维持该纪律,需同步改 AGENTS.md 的计数(本阶段禁止改)。

### 4.3 我认为最容易踩坑的两点

1. **忍者"周期完全重置"的挂钩点选择**:若把钩子挂在 `clearRoundBonuses` 内部,会连带在 `tick` 的情形 2/3 之外被误触发;若挂在 `tick` 的返回之前(方法末尾),会因为情形 1/2/3 都有 `return` 而漏触发。必须精确落在 `tick` 情形 1(`:354-360`)与 `registerPlay` 边界块(`:279-286`)两处,且**不得**在情形 2/3(`:339-353`)触发。
2. **`sign_active_max_cooldown` 的"提前写入"与既有 4 个起冷却入口的成对不变量**:AGENTS.md 明确该键与 `sign_active_cooldown_end` **成对写入**,且"仅服务端使用、不 `.sync()`"。本方案为了"锁定期间减免方仍能读到基准",改为**锁定开始时先写基准、冷却开始时再按池抵扣并改写为 effective** —— 这打破了"成对写入"的既有表述,必须同步更新代码注释与 AGENTS.md 描述;同时要保证:① 锁定期间该键非 0 不会被误当成"正在冷却"(现有减免方的判据是 `cdEnd > now`,不会误判,但 `ModTooltipHandler` 只读 `cooldown_end`,安全);② 若锁定中途被打断(死亡/重登/卸牌导致效果消失),`endLockAndStartCooldown` 仍会消费基准并起冷却,不留下"半写"状态。

---

## §5 待用户确认的问题清单

> 每条给出 2-3 个备选与我的推荐(标注「推荐」)。

**Q1「本技能施加的计时器」是否包含施加到目标/友军身上的效果(L3)?**
- A(推荐):**只算自身侧**(L1 自身效果实例 + L2 自身附件到期刻)。⇒ "是"= 7 个(见 §1.1)。
- B:连目标侧也算 ⇒ `lulu`(敌对缓慢 1:00)、`moses`(破绽 2:00)、`pandaman`(嘲讽 1:00)、`haiqing`(虚弱印记 5:00)也进入锁定,冷却被拉到 3~8 分钟,且"目标提前死亡/效果被清"会让锁定提前结束。
- C:只算自身侧,但把"目标侧效果仍在"作为**可选的额外解锁条件**(更复杂,不建议)。

**Q2 占星师/秘密侦探/枪匠的"30 秒待命窗口"是否算本技能施加的计时器?**
- A(推荐):**不算** ⇒ 这三者不进入锁定态,保持"释放即起冷却 / 超时不起冷却"现状(与 tooltip `未命中则不消耗冷却` 一致)。
- B:算 ⇒ 超时(未命中)也必须起冷却 —— 与现有 tooltip 与设计直接冲突,属需求变更,需另行改写三条 tooltip 与用例。

**Q3 电流核心在锁定期的口径冲突:第 2 条把它列为"入池方",第 5 条又禁止它在锁定态被触发/耗充能**
- A(推荐):**严格按第 5 条** ⇒ 锁定期完全禁用(不触发、不扣充能、不入池),只发「主动技能生效中!」。
- B:按第 2 条字面 ⇒ 锁定期按下时折算一个"等效减免量"入池,但仍不扣充能、不改冷却(那么"按下"是否算一次操作?与"按键无效"矛盾)。
- C:锁定期禁按;仅当锁定结束、进入真正冷却后,电流核心仍按既有行为生效(这是 A 的自然结果)。

**Q4 池抵扣后,`sign_active_max_cooldown`(电流核心档位分母)取哪个值?**
- A(推荐):取**抵扣后的实际冷却**(`effective`)—— 电流核心按"剩余/实际冷却"计费,语义自洽。
- B:保留抵扣前基准 —— 抵扣后剩余占比恒 < 1 ⇒ 电流核心更便宜(变相二次减免)。

**Q5 忍者"未出牌"判据**
- A(推荐):新增 `sign_active_lock_played`,唯一置真入口 `EffectCardPeriod.registerPlay`(跨周期边界保持)。
- B:`effect_card_play_count == 0 && bonus 未消耗` ⇒ 周期边界会把两者都归零,无法区分"未出牌"与"刚重置",会误判为"未出牌"并提前强制重置。

**Q6 锁定期结束那一刻玩家不在线 ⇒ 冷却何时起算?**
- A(推荐):**上线后第一次判定起算**(离线期间冷却不走;实现零额外代码)。
- B:按 `lock_end` 回填 `cdEnd = lock_end + effective`(离线也走冷却,等效"离线不计费"的反面;需处理 `lock_end` 早于登录时刻的情形)。

**Q7 锁定/池在"卸下立牌"时是否清除?**
- A(推荐):**不清除**(玩家级,与冷却一致;以"门控计时器是否还在跑"为准 ⇒ `nancy_lu` 卸牌会因效果被移除而提前解锁)。
- B:卸牌即解锁并立刻起冷却(需要为 16 个 `clearSignData` 增加统一钩子,且会让"临时卸下再装上"变成可利用操作)。

**Q8 忍者宽限期内死亡是否清算主动冷却?**
- A(推荐):**不清算**(与现状"死亡清空冷却"一致,不产生新可利用路径)。
- B:清算 ⇒ 需新增死亡暂存(仿 `DeathPreservedBonuses`),把死亡惩罚加重。

**Q9「主动技能生效中!」ActionBar 是否带立牌名前缀?**
- A(推荐):带前缀,值 = `"%s:主动技能生效中!"`(en: `"%s: Active skill in effect!"`),直接复用 `notifyActionBar`(它无条件注入 signName)。
- B:纯文本「主动技能生效中!」⇒ 必须改用 `sendSignActionBar`(无前缀版本),与同类 `msg.astral_dice.sign_active_triggered` 风格分叉。

**Q10 tooltip 是否显示"生效中(锁定)"?**
- A(推荐):不显示 ⇒ 5 个新键**全部不同步**,零客户端改动。
- B:显示 ⇒ 需给 `sign_active_lock_sign`/`sign_active_lock_end` 加 `.sync()`(1.21.1)/`.sync()`+`SYNCED_KEYS`(forge),并同步 AGENTS.md 的 synced 键计数。

**Q11 忍者被动「冷却 −30%」是否也纳入"锁定期间入池"?**
- 用户第 2 条列举的四个减免方**不含**忍者被动。
- A(推荐):按字面 ⇒ 锁定期 `cdEnd == 0` 时该 −30% 为**空操作**(既不入池也不报错)。
- B:一并入池(需明确 −30% 在锁定期的"基准"是什么:`sign_active_max_cooldown × 0.3`,且与"剩余部分"语义不同)。

**Q12 门控效果被外部**延长/刷新**时,锁定是否跟随延长?**
- A(推荐):**不跟随**(锁定 = 本技能施加时的 max 到期刻 + "效果实例仍在"的 OR;刷新不延长)。
- B:跟随(每 tick 重算"门控效果剩余时长的 max")⇒ 会被外部刷新无限延长锁定,风险较高。

**Q13 "锁定结束时必起冷却"是否会有例外?(例如门控效果被外部彻底移除、或玩家卸牌导致效果消失)**
- A(推荐):**不设例外** ⇒ 锁定 ⇄ 冷却之间不存在"什么都不做"的空档(避免"锁没了但也不冷却"的漏洞)。
- B:允许例外(例如卸牌使效果消失则解锁而不起冷却)⇒ 形成"卸牌刷冷却"的可用操作,不建议。

---

## 附:本次侦察覆盖的文件清单(全部为实际读取)

**neoforge-1.21.1**:`item/sign/BaseSignItem.java`、`KomachiSignItem.java`、`MimiSignItem.java`、`ParunanSignItem.java`、`JasmineSignItem.java`、`MisakiSignItem.java`、`LuluSignItem.java`、`PadmanSignItem.java`、`FannySignItem.java`、`RinSignItem.java`、`HaiqingSignItem.java`、`PaparaSignItem.java`、`BonnieSignItem.java`、`FenSignItem.java`、`NancyLuSignItem.java`、`MosesSignItem.java`、`PandamanSignItem.java`;`item/chip/CurrentCoreChipItem.java`;`item/card/EffectCardPeriod.java`、`FateGuidanceCardItem.java`、`BaseEffectCardItem.java`;`combat/DiceCombatEvents.java`;`component/ModAttachments.java`、`GameplayConstants.java`、`DeathPreservedBonuses.java`;`event/PlayerTickEvents.java`、`PlayerLifecycleHandler.java`、`EffectTimerGuard.java`、`WeirdDiceHandler.java`、`ModTooltipHandler.java`;`effect/MosesBrokenEffect.java`;`item/ChargeManager.java`;`assets/astral_dice/lang/zh_cn.json`(grep)、`en_us.json`(grep)。

**forge-1.20.1**:`item/sign/BaseSignItem.java`、`KomachiSignItem.java`、`LuluSignItem.java`、`ParunanSignItem.java`、`JasmineSignItem.java`;`item/chip/CurrentCoreChipItem.java`;`item/card/EffectCardPeriod.java`、`FateGuidanceCardItem.java`;`combat/DiceCombatEvents.java`;`component/ModAttachments.java`、`AttachedDataKey.java`、`GameplayConstants.java`(grep);`event/PlayerTickEvents.java`;`effect/MosesBrokenEffect.java`(grep);`assets/astral_dice/lang/zh_cn.json`(grep);其余 forge 立牌类以 `grep addEffect|EffectTimerGuard.apply` 的命中/0 命中作为文件级证据(未逐文件读全文)。

**scripts**(只读,用于风险评估):`scripts/test/resources/kubejs/1.21.1|1.20.1/server_scripts/astral_bugfix_probe.js`(节选)、`astral_dice_target_select_check.js`(grep)、`scripts/test/cases/KOMACHI-EXTRA-PLAY-1.21.1.json|1.20.1.json`(grep)。
