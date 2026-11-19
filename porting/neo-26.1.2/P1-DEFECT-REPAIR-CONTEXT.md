# NeoForge 26.1.2 缺陷修复上下文（交付可写 26.1.2 的会话执行）

> 生成者：fabric-1.20.1 移植会话（2026-11-01 23:2x）。按用户指令（m02678）本会话**只出上下文、未改动 26.1.2 任何文件**。
> 数据源：porting/fabric-1.20.1/ALIGNMENT-AUDIT-2026-11-01.md 的 P-1..P-5 条目，外加本会话逐条实读/实跑复核（其中 P-3 的失效面比审计描述更宽，已在下文扩写）。
> 行号为当次快照（multi-main / HEAD fd990c15），执行前请用第 9 节的 grep 判据复核语义，不要死信行号。

## 0. 范围与边界

- 只允许改 neoforge-26.1.2/src/** 与 neoforge-26.1.2/build.gradle（若确需）。
- **禁止**顺手改 fabric-1.20.1 / neoforge-1.21.1 / forge-1.20.1（fabric 侧同类问题已在本会话修完；他线若发现问题只写进报告，不动手）。
- 仓库红线：默认不 commit / push / tag、不部署、不启动实机（实机必须用户明确下令）。
- 基线口径：主基准 = neoforge-1.21.1，次基准 = forge-1.20.1；用户裁决「一律以最新主线为准，旧版本差异全部放弃」（m01840）。
- 构建：JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.13.102-hotspot；命令 gradlew.bat :neoforge-26.1.2:build --console=plain。

## 1. 该线的关键机制（改之前先知道）

- 构建插件：net.neoforged.moddev 2.0.147（与 1.21.1 的 2.0.141 不同版本，API 面基本同构）。
- 网络：RegisterPayloadHandlersEvent + PayloadRegistrar，注册器版本号取 **VersionGate.interopVersion()**（major.minor 派生，**禁止硬编码版本号**）；NeoForge 在配置阶段逐通道比对版本，不等即握手失败。
- 立牌钩子：26.1.2 同时存在两套风格 —— ① 旧的 SignHooks 接口 + SignHookDispatcher 分发（**零调用者，见 P-3**）；② 新的「在 DiceCombatEvents 里直接调各立牌静态方法」直调（已在用，覆盖 6 族）。
- 平台差异（写修复时别照抄 fabric）：fabric 用自建门面 compat/curios/CuriosApi（多源聚合 Accessories 主 + Trinkets 兜底），26.1.2 用官方 top.theillusivec4.curios；fabric 的 MobEffectInstance/Holder/RegistryObject 语义都与 26.1.2 不同。

## 2. P-1（高）BankCardUnlimitedChipItem 未跟「无队伍 32 格」裁决

### 现象
neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/item/chip/BankCardUnlimitedChipItem.java 全文仅 **82 行**，无 ALLY_RANGE 常量；onBlessingEnd 仍按旧口径「未组队 = 全服在线玩家」发放。

### 现状原文（:61-77）
    /**
     * 骰神赐福结束时调用:使自身及友方玩家获得 3 星币。
     * 友方 = 已加入队伍时同队在线玩家(MC/FTB/OPAC);未加入任何队伍时 = 全服在线玩家。
     * 死亡清场时(玩家已死亡)不发放。
     */
    public static void onBlessingEnd(Player player) {
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (player.isDeadOrDying()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        java.util.List<Player> allies = PartyRelations.collectTeamPlayers(player);
        for (ServerPlayer sp : serverLevel.players()) {
            if (sp == player || allies.contains(sp)) {
                giveCoins(sp);
            }
        }
    }

### 期望行为（= 主基准实现）
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BankCardUnlimitedChipItem.java:30 有 public static final double ALLY_RANGE = 32.0;，:67-73 的 javadoc 记录「⚠️ 2026-10-15 用户裁决:无队伍一侧的范围由『全服在线玩家』收紧为『自身 32 格内』」，:72-93 的实现为：
    boolean hasTeam = PartyRelations.hasTeam(player);
    java.util.List<Player> allies = hasTeam ? PartyRelations.collectTeamPlayers(player) : java.util.List.of();
    double rangeSqr = ALLY_RANGE * ALLY_RANGE;
    for (ServerPlayer sp : serverLevel.players()) {
        if (sp == player) { giveCoins(sp); continue; }            // 自身恒在列
        if (hasTeam) { if (allies.contains(sp)) giveCoins(sp); }
        else if (sp.distanceToSqr(player) <= rangeSqr) giveCoins(sp);
    }
forge-1.20.1 同文件 :31/:85 同值同构 ⇒ 两条基准一致，26.1.2 是唯一滞后线。

### 最小修复
1. 在类内常量区（STARLIGHT_ON_EQUIP / REWARD_COINS 旁）加 public static final double ALLY_RANGE = 32.0; 并写 javadoc 引裁决日期。
2. 用上面 1.21.1 的循环体替换 onBlessingEnd 旧循环，javadoc 的「未加入任何队伍时 = 全服在线玩家」改成「= 自身 32 格内的所有玩家(自身恒在列)」，并加一条说明「不再依赖 collectTeamPlayers 的未组队兜底（该兜底是 FTB/OPAC 反射失效时的放大器）」。
3. 不要动 isEquipped / onEquip / onChipUnequip（与主线逐字一致）。

### 验收
- grep -n ALLY_RANGE 该文件 ⇒ ≥3 命中（声明 1 + rangeSqr 计算 1 + javadoc 至少 1）。
- 语义判据：onBlessingEnd 内不再出现「未组队直接 allies.contains」(即 hasTeam 二分支)。
- 行为验证属实机项（赐福结束、无队伍、两名玩家相距 >32 格 ⇒ 远处那名不应得币）。

### 风险
- 缩范围后若玩家实际游玩距离常 >32 格，队友收益会下降 —— 这是用户裁决，不要自行放宽。

## 3. P-2（高）CardChoiceConfirmPayload / CardChoiceClosePayload 有类、有发送点，但从未注册

### 证据
- 类都在：network/CardChoiceClosePayload.java:18（record int token implements CustomPacketPayload，TYPE :20，STREAM_CODEC :24-26 用 StreamCodec.composite(ByteBufCodecs.VAR_INT, ...），handle :33）；network/CardChoiceConfirmPayload.java:19 同构。
- 活路径发送点两处：
  - choice/CardChoiceManager.java:199  PacketDistributor.sendToPlayer(player, new CardChoiceClosePayload(token));
  - screen/CardChoiceScreen.java:304  net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new CardChoiceConfirmPayload(this.menu.token()));
- 注册面：network/ModPayloads.java 全文 192 行，grep -c CardChoice = **0** ⇒ 两个包从未注册。
  （同文件里 EventCardPayload 的注释记录过同类事故：「26.1.2 移植时该条被漏掉 ⇒ 覆盖层永远收不到卡，本处按 1.21.1 基准补回」。）

### 期望
「确认选卡」C→S 与「服务端关闭选卡」S→C 两条通道可用；未注册时表现可能是（U9 未决）：发送即抛异常 / 被静默丢弃 ⇒ 选卡界面卡死或点了没反应。

### 最小修复
在 ModPayloads.register(...) 内、紧邻 TargetSelect 那批（:74-98）之后追加两条，形状照抄同文件已有的 playToServer / playToClient：
    registrar.playToServer(
            CardChoiceConfirmPayload.TYPE,
            CardChoiceConfirmPayload.STREAM_CODEC,
            CardChoiceConfirmPayload::handle
    );
    registrar.playToClient(
            CardChoiceClosePayload.TYPE,
            CardChoiceClosePayload.STREAM_CODEC,
            CardChoiceClosePayload::handle
    );
并补 import（若该文件用全限定名则省略）。通道方向务必按上面的 S/C 语义，写反了会在 26.1.2 的注册器上直接失败。

### 验收
- grep -c CardChoice neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/network/ModPayloads.java ⇒ ≥2。
- 先跑一次 gradlew.bat :neoforge-26.1.2:build（重复注册会在这里或在配置阶段炸，早炸比进游戏炸好）。
- 实机：触发一次选卡界面，点「确认」应正常下发并关闭（U9 判定后可同时确认不再有未注册告警/异常）。

### 风险
- 若这两条包其实通过其它入口注册（例如客户端独立注册器），补齐会造成 duplicate registration 崩溃 ⇒ 动手前先全仓 grep CardChoiceConfirmPayload / CardChoiceClosePayload 的 .TYPE 使用点。

## 4. P-3（中 → 实测扩为高）立牌钩子分发层零调用者，3 族效果彻底失效

### 结构事实
- neoforge-26.1.2/.../item/sign/SignHookDispatcher.java:25（public final class）有 11 个 fire：fireKillHooks(:69)、fireHurtHooks(:81)、fireBlessingTriggered(:92)、fireDiceBlessingTriggered(:106)、fireDiceBlessingEnded(:115)、fireAttackBrokenTarget(:124)、fireDiceJudgmentRoll(:133)、fireDiceRollResult(:142)、fireDodgeCounter(:151)、fireDragonRoar(:160)、fireSplashCost(:169)。
- **grep -rn SignHookDispatcher neoforge-26.1.2/src/main/java ⇒ 仅 4 处，全在 SignHookDispatcher.java 自身（:25/:26/:61）与 SignHooks.java:13 的 javadoc ⇒ 全仓零调用者。**
- SignHooks.java 有 10 个钩子接口（:23-70）：BlessingTriggered.handleBlessingTriggered(Player) void、SplashBlessingTrigger.handleBlessingTriggered(Player) boolean、BiteBlessingTrigger.handleDiceBlessingTriggered(Player)、BlessingEnded.handleDiceBlessingEnded(Player)、BrokenTargetAttack.handleAttackBrokenTarget(Player, LivingEntity)、DiceJudgmentRoll.handleDiceRollResult(Player, int) 返回 String、DiceRollResult.handleDiceRollResult 返回 void、DodgeCounter.handleDodgeCounter(Player, LivingEntity)、DragonRoar.handleDragonRoar(Player, LivingEntity)、SplashCost.handleSplashCost(Player)。
- 实现类 7 个：AlanaSignItem:29（BlessingTriggered，静态 onBlessingTriggered :43，实例 handleBlessingTriggered :54）、FenSignItem:37（SplashBlessingTrigger, SplashCost，handleBlessingTriggered :214 / handleSplashCost :219）、HannaSignItem:68（DiceRollResult）、MamushiSignItem:74（BiteBlessingTrigger, BlessingEnded, DragonRoar，handleDragonRoar :343）、MosesSignItem:46（BrokenTargetAttack, DodgeCounter）、NancyLuSignItem:48（BlessingEnded）、ZhaoSignItem:105（DiceJudgmentRoll）。
- 对照：主基准 1.21.1 的 DiceCombatEvents 有 12 处 fire 调用（:396 fireAttackBrokenTarget、:474 fireBlessingTriggered、:478 fireDiceBlessingTriggered、:576 fireDiceJudgmentRoll、:584 fireDiceRollResult、:891 fireDragonRoar、:912 fireSplashCost、:1449 fireDiceBlessingEnded、:2020 fireDodgeCounter，另 AdrenalineChipItem:114 也调 fireDodgeCounter）；forge-1.20.1 同构（:708/:786/:788/:886/:893/:1200/:1221/:1722/:2325 + AdrenalineChipItem:117）。⇒ **26.1.2 是唯一把分发层整体丢掉的线。**
- kill/hurt 两族不受影响：BaseSignItem.java:656 invokeKillHooks / :672 invokeHurtHooks 自己遍历已装备立牌（:658 CuriosApi.getCuriosInventory(killer).ifPresent(...)），不依赖 dispatcher。

### 26.1.2 现在的替代直调（已覆盖 6 族，共 9 处）
- combat/DiceCombatEvents.java:365 MosesSignItem.onAttackBrokenTarget(player, target)
- :441 fenSplashArmed = FenSignItem.onBlessingTriggered(player)；:448 HealingManager.onBlessingTriggered(player)
- :542 ZhaoSignItem.onDiceRollResult(player, baseDice)；:550 HannaSignItem.onDiceRollResult(player, baseDice)
- :1281 NancyLuSignItem.onDiceBlessingEnded(player)；:1283 MamushiSignItem.onDiceBlessingEnded(player)
- :1829 MosesSignItem.onDodgeCounter(player, attacker)

### 彻底失效的 3 族（grep 全仓零调用点）
1. **阿兰娜立牌（BlessingTriggered，修女「鼓起勇气」被动）**：AlanaSignItem.java:43 public static void onBlessingTriggered(Player) 存在；:54 handleBlessingTriggered 实现接口；但 grep AlanaSignItem.onBlessingTriggered 与 AlanaSignItem.handleBlessing 均 0 命中 ⇒ 被动完全不生效，且无任何日志。
2. **蛟龙立牌（DragonRoar，命中施加龙之咆哮减益）**：MamushiSignItem.java:343 handleDragonRoar 实现接口，但 grep onDragonRoar = 0（26.1.2 内无任何静态入口、无调用）⇒ 减益从不施加。
3. **大当家立牌（SplashCost，「战斗爽·溅射」的代价结算）**：FenSignItem.java:219 handleSplashCost 实现接口，grep onSplashCost = 0 ⇒ 代价分支无入口。

### 修复路线（两条，建议 A）
- **A（推荐，与主线同形）**：在 DiceCombatEvents 的对应位置补 fire* 调用，让已存在且已实现的接口重新通电：赐福触发处补 SignHookDispatcher.fireBlessingTriggered(player)（注意它返回 boolean = fenSplashArmed，需按主线 :474 的语义合并）、命中处补 fireDragonRoar(player, target)、溅射结算处补 fireSplashCost(player)。**同时要处理与现有直调的重复**：若同一事件点既直调 FenSignItem.onBlessingTriggered 又 fireBlessingTriggered，Fen 会被触发两次 ⇒ 二者只能留一套。
- **B（fabric 采用的路线）**：保留直调，补 3 个缺失族的直调，然后删掉 SignHookDispatcher + SignHooks 及其 implements 声明（fabric-1.20.1 已按此删完，可作为蓝本对照，但**不要复制它的 Curios 门面代码**）。
- 三条最小直调补丁（若选 B）：
    // 赐福触发点（与现有 FenSignItem.onBlessingTriggered 并列，注意别重复触发 Fen）
    AlanaSignItem.onBlessingTriggered(player);
    // 命中点（已有 target 时）
    MamushiSignItem.onDragonRoar(player, target);   // 若该静态方法尚不存在，需在 MamushiSignItem 内新增，方法体 = 现 handleDragonRoar 的实现
    // 溅射代价结算点
    FenSignItem.onSplashCost(player);               // 同上：静态化 handleSplashCost 的方法体
  注意：若两个类的钩子目前只有实例方法（handleDragonRoar / handleSplashCost），B 路线需要把它们改成静态或新增静态转发；A 路线不需要改实现类。**这也是 A 更省事的理由。**

### 验收
- grep -rn "AlanaSignItem.onBlessingTriggered" neoforge-26.1.2/src/main/java ⇒ ≥1（调用点）。
- grep -rn "onDragonRoar\|onSplashCost" neoforge-26.1.2/src/main/java ⇒ 各 ≥1（若走 A 则命中的是 fire 方法名，同样算过）。
- 若删死代码：grep -rn SignHookDispatcher neoforge-26.1.2/src/main/java ⇒ 0，且编译通过。
- 实机：阿兰娜佩戴 → 触发骰神赐福 → 攻击加成生效；蛟龙命中 → 目标获得 dragon_roar_break；大当家溅射 → 养精蓄锐层数按 SPLASH_COST=2 扣减。

## 5. P-4（低）BEAM_PARTICLES 死常量

- neoforge-26.1.2/.../combat/DamageCardFxScheduler.java:114 private static final int BEAM_PARTICLES = 84;（:113 注释「光束每 tick 粒子数」），全文件无引用（同文件早已改成 sendBeamFx 真实光线），全仓 grep 只有它自己。1.21.1 同文件 grep BEAM_PARTICLES = 0 ⇒ 只在 26.1.2 残留。
- 修复：删常量与注释；client/BeamFxRenderer.java:32 的 javadoc 若把 BEAM_PARTICLES 当现役常量描述，改成历史叙述或删。
- 验收：grep -rn BEAM_PARTICLES neoforge-26.1.2/src ⇒ 0。

## 6. P-5（低，但可能藏高优先缺口）创造栏与效果差集

- 效果多 1（领先，不是缺口）：effect/ModEffects.java:157-158 public static final DeferredHolder<MobEffect, MobEffect> RAILGUN_READY = EFFECTS.register("railgun_ready", () -> new ChipReadyEffect(0x81D4FA)); fabric 也独有这样的领先项；是否保留由用户裁决（fabric 侧保留）。
- 物品少 2：ATTACK_CARD_BITE / ATTACK_CARD_DRAGON_ROAR 在 neoforge-26.1.2 的 item/ModItems.java **未定义**；26.1.2 内命中它们的地方全是引用方：combat/CardRegistry.java:260/263、datagen/ModItemModelProvider.java:135-136、event/ModTooltipHandler.java:634/644。1.21.1 的定义在 item/ModItems.java:332/341。
- **先判一件事**：CardRegistry 那两处引用的是 Item 常量还是注册名字面量？
  - 若是寄存器常量 ⇒ 编译期就该错（既然能编译，说明写法是字面量或已存在别处）。
  - 若是 ResourceLocation/字符串字面量 ⇒ 运行时是「查不到物品」，表现为选卡界面给出空气卡 / 日志 missing item ⇒ 优先级要提到 P-2 同级。
- 修复方向：按 1.21.1 定义补齐两个物品（含 models/item、lang 三语、创造栏 accept），或若这两张卡在 26.1.2 视为未实装则把引用方一并裁掉并写进报告。**选哪条要先问用户**，因为涉及内容裁剪。
- 验收：补齐后 verify 方式 = 开游戏看创造栏 157 项 / 或静态比对 ModItems 定义数 vs 引用数；lang 键集用 python tools/check_lang_sync.py 守。

## 7. 未决项 U9（做 P-2 之前先定级）

- 问题：NeoForge 26.1.2 在「向未注册通道发送 CustomPacketPayload」时的行为是抛异常还是静默丢弃？
- 影响：若抛异常 ⇒ P-2 从「不生效」升级为「点确认即崩」，必须在任何实机演示前修掉。
- 判定法（只读，不改代码）：javap/反编译 26.1.2 的 neoforge 网络实现里 PayloadRegistrar / 发送路径对未知通道的处理；或最小实机点一次「确认选卡」看 latest.log / crash-report。
- 判定结果请回填到本文件 §7 并同步给用户（U9 结论会影响是否允许实机）。

## 8. 建议批次（每批独立编译 + 报告）

| 批次 | 内容 | 产出 |
|---|---|---|
| B1 | 只读定级：U9 + P-5 的 CardRegistry 判据 | porting/neo-26.1.2/B1-U9-P5-RECON.md |
| B2 | P-2 两条通道注册 | porting/neo-26.1.2/B2-PAYLOAD-REPORT.md |
| B3 | P-3 三族钩子（含死代码取舍） | porting/neo-26.1.2/B3-SIGNHOOK-REPORT.md |
| B4 | P-1 半径裁决对齐 | porting/neo-26.1.2/B4-BANKCARD-REPORT.md |
| B5 | P-4 / P-5 清理 | porting/neo-26.1.2/B5-CLEANUP-REPORT.md |

顺序理由：B2 最小且能立刻验证注册链路；B3 面最大；B1 先行是为了避免在「点确认就崩」的情况下安排实机。

## 9. 验收命令与判据（可复制）

    cd /d F:\MCProject\astral_dice_multiloader
    gradlew.bat :neoforge-26.1.2:build --console=plain
    python tools/check_lang_sync.py            # 四线三语键集一致
    python tools/verify_effect_icons.py        # 四线效果图标守门（若动了 ModEffects）

静态判据一览（全部在 neoforge-26.1.2/src 内跑）：

- P-1：grep -n ALLY_RANGE .../item/chip/BankCardUnlimitedChipItem.java ⇒ ≥3
- P-2：grep -c CardChoice .../network/ModPayloads.java ⇒ ≥2
- P-3：grep -rn "AlanaSignItem.onBlessingTriggered" ⇒ ≥1；grep -rn "onDragonRoar\|onSplashCost" ⇒ 各 ≥1；若删死代码 grep -rn SignHookDispatcher ⇒ 0
- P-4：grep -rn BEAM_PARTICLES ⇒ 0
- P-5：grep -c "ATTACK_CARD_BITE\|ATTACK_CARD_DRAGON_ROAR" .../item/ModItems.java ⇒ 定义数（2 或 0，取决于用户裁决）

## 10. 禁止事项与常见坑

1. **不要复制 fabric 实现**：fabric 的 CuriosApi 是自建门面（compat/curios），26.1.2 用官方 CuriosApi；fabric 的 MobEffectInstance/Holder/RegistryObject 语义与 26.1.2（DeferredHolder）不同，照抄会编译不过或语义错。
2. **不要为了「顺手对齐」改其它线**；发现他线问题写进报告。
3. **lang 三语必须齐全**：任何新增键要同时改 en_us / zh_cn / ja_jp，保持 CRLF、无 BOM；字面百分号写 %%。
4. **网络注册版本号只能取 VersionGate.interopVersion()**，写死会导致跨端握手直接失败。
5. **注册重复 = 配置阶段硬失败**：补通道注册前先 grep 全仓确认没有第二处注册。
6. 立牌钩子修复后，务必确认同一点不会被直调 + 分发双触发（Fen 会重复扣层、Moses 会重复加层）。

## 11. 附：本会话用到的取证命令（复核用）

    grep -rn "SignHookDispatcher" neoforge-26.1.2/src/main/java
    grep -rn "handleBlessingTriggered\|handleDragonRoar\|handleSplashCost" neoforge-26.1.2/src/main/java
    grep -rn "onDiceRollResult\|onDiceBlessingEnded\|onAttackBrokenTarget\|onDodgeCounter" neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java
    grep -rn "SignHookDispatcher.fire" neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java
    grep -rn "CardChoiceConfirmPayload\|CardChoiceClosePayload" neoforge-26.1.2/src/main/java
    grep -rn "BEAM_PARTICLES" neoforge-26.1.2/src neoforge-1.21.1/src
    grep -rn "ATTACK_CARD_BITE\|ATTACK_CARD_DRAGON_ROAR" neoforge-26.1.2/src/main/java | head -20

