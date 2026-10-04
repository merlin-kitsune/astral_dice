# S2 攻击/赐福触发链

- 仓库:`F:\MCProject\astral_dice_multiloader` 分支 `multi-1.20.1-1.21.1`,基线 `d48a529`(工作区干净,本次只读审计,未改动任何源码/配置)。
- 路径缩写(下文所有 `file:line` 均相对这两个根):
  - **A** = `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/`
  - **B** = `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`
- 切片范围:一次近战攻击内的全部被动/主动效果(赐福触发、战斗骰、攻防修饰器、伤害定稿、后置效果)。**不包含**纯法伤卡牌数值链(另见 `SpellDamageRegistry`)、事件系统、掉落/工具提示。
- 结论只依据代码;注释/文档不作为判据(注释与代码不一致处已单独标注)。
- 事件语义按 NeoForge/Forge 规则:HIGHEST 最先 → HIGH → 默认 → LOW → LOWEST 最后;**同优先级 = 注册顺序**(下面所有"同优先级"结论均标注为脆弱依赖)。

---

## 1. 参与方与订阅点清单

> `触发条件` = 什么情况下这段逻辑参与一次近战攻击;`事件/入口` = 它挂在哪里。
> 无 `@SubscribeEvent` 的类都是"被主流程直接调用"的被动钩子(入口列为调用方)。

| 类 | 触发条件 | 事件/入口 | 1.21.1 file:line | 1.20.1 file:line |
|---|---|---|---|---|
| `combat/DiceCombatEvents` | 任意 LivingDamage 事件(受击者/攻击者皆可) | `LivingDamageEvent.Pre`(默认) | A:`DiceCombatEvents.java:184-185` | B:`DiceCombatEvents.java:180-181`(`LivingDamageEvent`,非 `.Pre`) |
| 同上(狂暴自伤放大) | 受击者带 `berserk` | `LivingDamageEvent.Pre`(**LOW**) | A:`894-901` | B:`888-895` |
| 同上(虚弱印记) | 受击者带 `weak_mark` | `LivingDamageEvent.Pre`(**LOW**) | A:`940-958` | B:`934-952` |
| 同上(破绽闪避+反击) | 受击者为佩戴枪匠立牌的玩家且攻击者带 `moses_broken` | **最前置取消**:A `LivingIncomingDamageEvent` / B `LivingAttackEvent`(默认) | A:`1088-1105` | B:`1096-1115` |
| 同上(嘲讽反击) | 受击者为玩家、攻击者被该玩家嘲讽 | `LivingDamageEvent.Pre`(默认) | A:`1109-1123` | B:`1119-1133` |
| 同上(赐福到期) | `dice_blessing` 自然结束 | `MobEffectEvent.Expired`(默认) | A:`826-889` | B:`821-884` |
| 同上(影袭禁索敌) | 目标玩家佩戴骰子+赐福+已装暗影突袭 | `LivingChangeTargetEvent`(默认) | A:`785-824` | B:`780-819` |
| `combat/DiceCombatModifiers` | 赐福攻击的攻防聚合(静态注册表,主流程遍历) | 被 `DiceCombatEvents` 调用,无事件 | A:`121-432`(27 攻 + 1 防) | B:`124-435`(27 攻 + 1 防,顺序逐条一致) |
| `combat/CardRegistry` | 攻击牌/防御牌掷骰、费用、耐久 | 被修饰器/GUI 调用 | A:`100-107`(掷骰)、`81-88`(费用) | B:`100-107`、`81-88` |
| `item/sign/PadmanSignItem` | 佩戴上班族:赐福攻击力 ±、骰 6 破防、每 60s 刷新 | 被修饰器读 `getAttackBonus`;破防标志写 `ctx` | A:`368-382`(修饰器#23)、`66-68` | B:`371-385`、`66-68` |
| 同上(骰点 1 → 下次必 6) | 战斗骰掷出 1 后置位、下次置 6 | 主流程内联 | A:`402-413` | B:`399-410` |
| 同上(主动"真的生气了") | 按主动键:攻防点数置 4 | `handleUse` | A:`35-44` | B:`35-44` |
| `item/sign/MisakiSignItem` | 剑气层数/爆发星级追加/名刀费用 | 主流程 `391-399` + `CardRegistry` 掷骰 | A:`391-399`、`48-63` | B:`388-396`、`49-63` |
| `item/sign/FenSignItem` | 赐福触发时养精蓄锐 -1;满 5 层 → 本次攻击定稿后溅射 | 主流程 `347` + `623-673` | A:`111-124`、`133-137` | B:`112-125`、`134-138` |
| `item/sign/PaparaSignItem` | 主动"汲取":攻击时按最终伤害回血;受伤时按单次伤害回血 | 主流程 `676-678` + 自订阅受击 | A:`56-63`(**LOWEST**)、`675-678` | B:`56-63`(**LOWEST**)、`674-676` |
| `item/sign/HaiqingSignItem` | 待命期内攻击符合条件目标 → 施虚弱印记并起冷却 | 主流程 `228-241` | A:`99-104`、`41` | B:`98-103`、`40` |
| `item/sign/BonnieSignItem` | 待命期内攻击 → 施"隐匿调查" | 主流程 `243-262` | A:`41`、`74-75`(SignActiveTriggeredEvent)、`137-138`(死亡) | B:`40`、`76-77`、`137` |
| `item/sign/MosesSignItem` | 待命期内攻击 → 施破绽;攻击带破绽目标 → +1 层弱点识破;赐福结束 -1 层 | 主流程 `266-281`、`848` | A:`107-112`、`132-141`、`146-152`、`168-176` | B:`112-126`、`137-146`、`151-157`、`173-181` |
| `item/sign/JasmineSignItem` | 攻击力增益(移动充能);加急加快减冷却 | 修饰器#11 + `BaseEffectCardItem` | A:`240-250`、`83-92` | B:`243-253`、`84-93` |
| `item/sign/NancyLuSignItem` | 隐身攻击 → 解隐 + 按战斗牌费用×2 加攻击力;免疫末影珍珠摔落 | `AttackEntityEvent`(近战)+ `LivingDamageEvent.Pre`(远程/魔法)+ 最前置取消 | A:`248-258`、`262-271`、`294-303` | B:`250-260`、`264-273`、`296-305` |
| `item/sign/LuluSignItem` | 受伤 → +1 治愈、主动冷却 -200t | `BaseSignItem.onHurt` 分发 | A:`45-59`(调用点 A:`193`) | B:`45-59`(调用点 B:`189`) |
| `item/sign/KomachiSignItem` | 每 3 张效果牌 → 复制 + 冷却剩余 ×0.7 | `BaseEffectCardItem` | A:`100-129`、`131-141` | B:`101-130`、`132-142` |
| `item/sign/RinSignItem` | 法伤活体书页加成(不在骰战链) | `SpellDamageRegistry` | A:`33` | B:`33` |
| `item/sign/MimiSignItem` | 筹码栏 +1(骰子槽位计算);战斗牌回收给星币 | `DiceCurioItem`/`VitaminPillChipItem` | A:`104`、`111-118` | B:`105`、`112-119` |
| `item/sign/ParunanSignItem` | 赐福触发 → 星光 = 触发时骰点×2 | 主流程 `416-421` | A:`61` | B:`61` |
| `item/sign/PandamanSignItem` | 反击伤害附加缺失生命;嘲讽 | `computeCounterDamage` | A:`1204-1210` | B:`1215-1221` |
| `item/dice/DiceCurioItem` | 卡牌栏格数、筹码栏数 | 被 GUI/槽位逻辑调用 | A:`35-47`(4/6/8/12) | B:`36-47` |
| `item/dice/NetherStarDiceItem` | T4:卡牌/费用恒按 3★;每星 +2 攻 / +2 防(→护甲) | `curioTick` | A:`38-47`、`71-93` | B:同构(未逐行核对) |
| `item/dice/ObsidianDiceItem` | +3 基础防御(→护甲)、火焰伤害 -70% | 属性 + 前置减伤 | A:`event/ObsidianDiceHandler.java:29-37`(HIGH) | B:`event/ObsidianDiceHandler.java:28-35`(**默认**) |
| `chip/ChipDamageHandler` | 安全气囊无效化致命伤 / 磨刀石减伤 | `LivingDamageEvent.Pre`(**LOWEST**) | A:`45-66` | B:`45-66` |
| `chip/AdrenalineChipItem` | 高效肾上腺素 20% 闪避(最前置取消) | A `LivingIncomingDamageEvent` / B `LivingAttackEvent`(默认) | A:`86-102` | B:`87-105` |
| `chip/AirbagChipItem`/`WhetstoneChipItem` | 保命/减伤数值提供方 | 被 `ChipDamageHandler` 调用 | A:`45`、`60` | B:`45`、`60` |
| `chip/MotoHelmetChipItem` | 纯属性护甲/韧性(无事件) | 属性修饰器 | A:`46-48` | B:`44-46` |
| `chip/RailgunChipItem` | 攻击时消 6 充能、延迟 1s 雷击=最终伤害×50% | 主流程 `207`/`607` | A:`99`、`607` | B:`99`、`604` |
| `chip/FlashlightChipItem` | 攻击敌对目标 +1 星光 | 主流程 `691` | A:`50` | B:`50` |
| `event/EnderDiceHandler` | 雨中/水下受伤 +40%(前置) | A `LivingDamageEvent.Pre`(**HIGH**)/ B `LivingHurtEvent`(**HIGH**) | A:`134-143` | B:`135-143` |
| `event/DamageEffectCardHandler` | 法伤加成(独立真伤,不入骰战) | A `LivingDamageEvent.Pre` / B `LivingHurtEvent`(默认) | A:`31-77` | B:`28-79` |
| `event/CrimsonDiceHandler` | 战斗骰掷出 1 → 自伤 6(掷骰内即刻) | 被 `rollCombatDie` 调用 | A:`42-67`、`976-992` | B:`42-67`、`970-986` |

---

## 2. 触发顺序

### 场景 1:普通近战(玩家持近战武器,对手为敌对生物)完整顺序

主入口 A:`DiceCombatEvents.java:184-185` / B:`180-181`(默认优先级,`LivingDamageEvent.Pre` / `LivingDamageEvent`)。

1. **受击者侧钩子(先于攻击结算)**:A:`193` `BaseSignItem.invokeHurtHooks(targetPlayer, event.getNewDamage())`、A:`195` `BufferShieldChipItem.onHurt(...)`。
   依据:A:`191-196`(B:`187-192`)。
2. **闸门**:A:`200` `if (aoeProcessing || counterDepth > 0) return;`、A:`201` `if (!(directEntity instanceof Player player)) return;`、A:`212` `if (!isMeleeWeaponAttack(player)) return;`
   依据:A:`198-212`(B:`194-208`)。`isMeleeWeaponAttack` = 主手为 Sword/Axe/Mace/Trident 且非盾牌(A:`995-1003`;B:`989-997` 无 `MaceItem` 分支,1.20.1 无重锤,非缺陷)。
3. **电磁炮登记**:A:`207-208`(伤害先按即时值兜底,定稿后回填)。
4. **读骰子与卡牌强化**:A:`217-224` → `diceStack` / `enhancement`。
5. **待命类主动释放**(仅当等待器命中且目标符合条件):占星师 A:`228-241` → 秘密侦探 A:`243-262` → 枪匠 A:`266-277` → 枪匠被动(攻击带破绽目标 +1 层弱点识破)A:`279-281`。
   **注意顺序:主动释放全部早于赐福触发与掷骰**,因此本次攻击新获得的"破绽/弱点识破层数"会在同一击内继续参与掷骰与攻击力(见场景 3)。
6. **赐福触发块**:A:`288-291` `addEffect(DICE_BLESSING, GameplayConstants.DICE_BLESSING_DURATION_TICKS)`(A:`component/GameplayConstants.java:65,69` = 60s);随后依次:
   - 重置防御牌已消耗/诅咒之剑标记 A:`294-296`;
   - PvP:被攻击方也佩戴骰子则同时赐福 A:`298-309`;
   - 标靶筹码 → 最近敌对目标标记 A:`311-332`;
   - 星币锤 A:`334-339`、永动机 A:`341`、高级外设 A:`343`、会员推荐信 A:`345`;
   - **大当家**山雨欲来置位 A:`347` `fenSplashArmed = FenSignItem.onBlessingTriggered(player)`;
   - 治愈体系 A:`350`。
   依据:A:`284-351`(B:`282-347`)。
7. **进入骰战的硬前置**:A:`354-362`(`hasEffect(DICE_BLESSING)` + `diceStack != null` + `enhancement` 兜底)。
8. **骰点生成**:A:`364` `int baseDice = rollCombatDie(player)` → A:`976-992`:诡异骰偏置(`WeirdDiceHandler.rollD6`)/绯红骰偏置并**掷出 1 即刻自伤 6**(`CrimsonDiceHandler.java:63-65`)/均匀;最后 A:`988-990` 用枪匠弱点识破层数抬高下限 `roll = Math.max(roll, 1 + stacks)`。
9. **护法被动层数/爆发与星级追加**:A:`367-388`(触发赐福 → 层数 +1,上限 3)→ A:`391-399`(爆发期间 `baseDice += 星级追加 1/2/3`)。
10. **上班族强命 6 / 置位**:A:`402-413`(有 `padman_force_six` → `baseDice = 6` 并清标记;否则若 `baseDice == 1` → 置位下次必 6)。
11. **读取骰点的附属奖励**:经商星光 A:`416-421`(`baseDice*2`)→ 占星师"骰 6 得 6 星币" A:`424-432` → 八面骰筹码 A:`436-457`。**这三处读的都是第 9/10 步修改后的值**。
12. **构建上下文**:A:`460-463`;`attackerCardsMax` 由构造器按"骰子是否为玻璃骰"决定(`DiceCombatContext.java:72`)。
13. **攻击修饰器叠加(顺序 = 注册顺序,脆弱依赖)**:A:`466-468` 遍历 `DiceCombatModifiers.attackModifiers()`。A:`121-432` 内 27 条,顺序见表:
    `1` 王之力/狂暴 A:`123` → `2` 力量 A:`134` → `3` 攻击牌掷骰(写 `ctx.attackCardSum`)A:`144` → `4` 护法层数/爆发 A:`156` → `5` 美工刀 A:`174` → `6` 瞄具/鹰眼(并施标记)A:`190` → `7` 标靶/手电筒 A:`205` → `8` 电流剑 A:`216` → `9` 高级外设 A:`223` → `10` 夹心饼干 A:`230` → `11` 扫地机 A:`240` → `12` 吸血鬼 A:`253` → `13` 拳套 A:`264` → `14` 星币锤 A:`278` → `15` 诅咒之剑 A:`286` → `16` 复仇之戟 A:`294` → `17` 养精蓄锐 A:`304` → `18` 战斗爽 A:`314` → `19` 骇客 A:`322` → `20` 秘密侦探 A:`330` → `21` 弱点识破 A:`338` → `22` 调查阶段 A:`348` → `23` 上班族(+`padmanDefBypass`)A:`368` → `24` 肾上腺素 A:`385` → `25` 原初核心 A:`395` → `26` 电磁炮 A:`402` → `27` 磨刀石 A:`409`。
    B:`126-412` 逐条同序(subagent 逐条比对,仅 API 差异)。
14. **捕获副作用标志(必须在防御修饰器之前读)**:A:`470-471` `hasShadowStrike = ctx.hasShadowStrike; hasFullPower = ctx.hasFullPower;` + A:`473-480` 二次兜底扫描 `full_power`。
15. **七咒减益只作用于"骰点 + 卡牌"**:A:`486` `diceAttackBonus = applyCurseToDicePoints(player, baseDice + attackCardSum)`(实现 A:`166-181`:-40%,持启示之证再 -20%,爆发/倒转之启/恩惠之典免疫)→ A:`488-489` `baseDamage = attackPower; attackPower += diceAttackBonus;`
16. **全力攻击最终倍率**:A:`493-495` `attackPower = Math.ceil(attackPower * 1.5)`(作用在"属性+修饰器+骰点+卡牌"总和上,**减防御之前**)。
17. **防御侧输入**:A:`502-542`
    - 目标是玩家且有骰子+赐福 → A:`525` `defenseBaseDice = rollCombatDie(targetPlayer)`(同样吃诡异/绯红偏置与缨红自伤)、A:`527-531` 写 `ctx.targetEnhancement` / `ctx.targetCardsMax`(玻璃骰防御方取最大);
    - 目标是怪物 → A:`534-541`:带破绽则 `defenseBaseDice = 0`,否则 `ThreadLocalRandom 1..6`。
18. **防御修饰器(仅 1 条)**:A:`555-557` → `DiceCombatModifiers.java:419-431` 把 `ctx.targetEnhancement` 的**全部** `appliedStones` 掷骰求和写入 `ctx.defenseCardSum`(**未按 `isDefense` 过滤**,见 S2-C1)。
19. **防御力口径**:A:`563-570`
    ```java
    double rawArmor = Math.min(target.getArmorValue(), 20);
    defensePower = 2 + effectiveArmor / 2.0 + 1.4 * toughness + defenseBaseDice + ctx.defenseCardSum;
    ```
20. **上班族无视防御力**:A:`576-578` `if (ctx.padmanDefBypass && !skipDefense) defensePower = defenseBaseDice + ctx.defenseCardSum;`(整项 `2 + 护甲÷2 + 1.4×韧性` 不计入,保留防御骰与防御牌)。
21. **伤害定稿**:A:`580` `finalDmg = Math.max(1, attackPower - defensePower)` → A:`584-586` 标记 +1 → A:`590-596` 虚弱印记 ×1.1(命运指引再 +20% = ×1.3)→ A:`600-602` 外部因子(含七咒实际倍率,内置因子 A:`925-936`)→ A:`604` `event.setNewDamage((float) finalDmg)`(B:`601` `setAmount`)→ A:`605` 跳字 → A:`607` 电磁炮回填。
22. **后置效果(全部在 `setNewDamage` 之后)**:
    - PvP 防御牌耐久 A:`610-613`(`consumeDefenseCardDurabilityOnce` A:`769-781`);
    - **大当家溅射** A:`623-673`(先扣层 `consumeSplashCost` A:`624`,伤害 `max(5, finalDmg*0.88)` A:`628-630`,范围 `target ± 6` A:`631-632`,只打敌对 A:`633-635`,真伤 A:`640-641`,主目标临时清零无敌帧 A:`648-656`,粒子/音效 A:`659-668`);
    - **吸血立牌吸血** A:`676-678` `player.heal(Math.max(1, (int) finalDmg / 2))`;
    - 暗影突袭黑暗 A:`680-682`;
    - **攻击牌耐久**(仅在触发赐福的这次)A:`686-688` → A:`697-734`;
    - 手电筒星光 A:`691`。
23. **本处理器之外、同一伤害事件的后续阶段**(受击者侧):
    - LOW:A:`894-901` 狂暴 +1/层;B:`888-895`。**同优先级**的 A:`940-958` 虚弱印记(骰战攻击被守卫跳过,见 S2-C10)。
    - LOWEST:A:`ChipDamageHandler.java:45-66` 气囊无效化 + 磨刀石减伤;同优先级另有 `PaparaSignItem.java:56-63` 受击回血(见 S2-C8)。
    - 前置阶段(EnderDice HIGH 雨中 +40%、Obsidian 火焰 -70%):被第 21 步的**覆盖式写入**吞掉(见 S2-C2)。

### 场景 2:多立牌同时影响一击(padman × misaki × fen × papara 读取顺序)

| 步骤 | 谁读谁 | 证据 | 是否互相影响结果 |
|---|---|---|---|
| 骰点 | misaki 爆发星级追加**先**改 `baseDice`;padman 强命 6**后**覆盖;padman 破防标志在第 13 步读 `ctx.baseDice` | A:`391-399` → A:`402-413` → A:`368-382` | **是**。星级追加可把 3 变 6 → 触发 padman 破防与占星师 6 星币(S2-C4);强命 6 会**丢弃**星级追加 |
| 攻击牌 | 修饰器 #3 掷骰写 `ctx.attackCardSum`,随后七咒减益统一处理 | A:`144-153`、A:`486` | 卡牌与骰点同被七咒减益,立牌/筹码加成不被减 |
| 溅射 | fen 读**局部 `finalDmg`**(含卡牌、倍率、虚弱印记、外部因子) | A:`623-630` | **是**。溅射 = 最终伤害×0.88,所以 misaki/padman/虚弱印记/全力攻击的改动都会改变溅射值;但溅射本身不吃虚弱印记的 +10%(S2-C10) |
| 吸血 | papara 读 `finalDmg`,取一半 | A:`676-678` | **是**,与溅射同源;溅射不改变 `finalDmg`,故不影响吸血值 |
| 攻击力修正写入 | padman 攻击加成可为 **-2**,与 misaki 层数/burst 正加成直接相加 | A:`368-382`、A:`156-171` | 纯加法,顺序无关;但攻防不对称见 S2-C5 |

### 场景 3:待命类主动(占星师/秘密侦探/枪匠)与攻击的交叉

1. 按主动键:A:`BaseSignItem.java:78-119`。判定顺序 = ①玩家级冷却(冷却中还可被电流核心"完成冷却")A:`84-94` → ②等待器存在则拒绝 A:`96` → ③`handleUse`。等待类主动在 `handleUse` 里只写等待器与提示效果(占星师 A:`HaiqingSignItem.java:99-102`、秘密侦探 A:`BonnieSignItem.java:102-103`、枪匠 A:`MosesSignItem.java:99-102`),**不起冷却**:A:`112-118` `if (ModAttachments.getSignReadyExpire(player) <= 0) { ... setSignActiveCooldownEnd(...) }`。
2. 等待期内攻击 → 在**当前这次攻击的伤害事件里**释放(主流程第 5 步),释放后立即起冷却:A:`238-239`(占星师)、A:`259-260`(秘密侦探)用 `WeirdDiceHandler.signCooldownTicks`;A:`273-274`(枪匠)用 `MosesSignItem.signCooldownTicks`(基础 120s,A:`117-125`)。
3. **主动与被动的先后**:释放块(A:`228-282`)**早于**赐福触发块(A:`288-351`)与掷骰(A:`364`)。可观察后果:
   - 枪匠主动在同一击施加破绽 → A:`279-281` 立即 +1 层弱点识破 → A:`988-990` 抬高本次骰点下限 → A:`338-344` 本次攻击力 +1(A:`341`)。
   - 占星师主动在同一击施加虚弱印记 → A:`590-596` 本次伤害即 ×1.1(印记是即时生效的 `addEffect`)。
4. **冷却减免叠加口径**(全部走"写 `sign_active_cooldown_end`"):
   - `WeirdDiceHandler.signCooldownTicks` A:`36-42`:基准 `SIGN_ACTIVE_COOLDOWN_TICKS`(=180s,A:`component/GameplayConstants.java:43-45`)→ 诡异骰 `/2` → `ChargeManager.cooldownTicks`(有充能 -20%,A:`item/ChargeManager.java:64-68`)。
   - 枪匠 A:`MosesSignItem.java:117-125`:基准 120s(佩戴时)→ 诡异 `/2` → 充能 -20%。
   - 命运指引 A:`FateGuidanceCardItem.java:62-71`:`remaining -= WeirdDiceHandler.signCooldownTicks(player) / 2`。
   - 扫地机 A:`JasmineSignItem.java:83-92`:`cdEnd -= GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS / 2`(固定 1800t)。
   - 史莱姆 A:`LuluSignItem.java:52-56`:`cdEnd -= 200`。
   - 忍者 A:`KomachiSignItem.java:131-141`:`remaining *= 0.7`。
   - 电流核心 A:`CurrentCoreChipItem.java:78-95`:按剩余/`MAX_COOLDOWN_SECONDS` 比例耗充能,直接把结束时刻置为 `now`(A:`92`);且每次主动实际生效 `onActiveSkillUsed` +1 充能(A:`55-59`,调用点 A:`240/261/275`、`BaseSignItem.java:117`)→ 正面反馈。
   四种不同基准 → S2-C9。

### 场景 4:玻璃骰 / 绯红 / 紫晶骰的特殊路径

- **玻璃骰**:攻击方 `ctx.attackerCardsMax`(`DiceCombatContext.java:72`)→ A:`CardRegistry.java:100-107` 仅对"随机骰点牌"(`medium/large/epic/defense_*/meito`,A:`110-116`)返回 `maxRoll`,固定值牌仍走原 roller 以保留 `hasShadowStrike/hasFullPower` 副作用。防御方 `ctx.targetCardsMax` A:`527-531`。
- **绯红骰**:A:`CrimsonDiceHandler.java:42-67`,自伤在**掷骰函数内部** `roller.hurt(...)`,早于 A:`988-990` 的枪匠最低骰点抬高 → 最低骰点无法防止自伤;防御方掷骰(A:`525`)与反击计算掷骰(A:`computeCounterDamage` A:`1170`)同样触发自伤。自伤伤害源 `diceDamage` 的 `source` 实体 = 掷骰者本人(A:`damage/ModDamageTypes.java:31-33`),进入骰战时被 A:`202` `if (target == player) return;` 挡下,不会递归。
- **紫晶骰(远程/魔法触发战斗骰)**:**不在近战链内**,由法伤注册表实现:`SpellDamageRegistry.java:338-349` `bonus + AmethystDiceHandler.rollD6(ctx.attacker)`;因此它是"独立真伤"(由 `DamageEffectCardHandler.java:62-71` 单独 `hurt`,不经骰战),既不消耗卡牌耐久也不触发赐福。近战(非魔法伤害类型)不会走这条链;反之带 `magic` 伤害类型的模组近战武器会同击命中骰战+法伤两条链(见 §5-3)。

### 场景 5:PvP(双方都有骰子)

1. 攻击方触发赐福时给目标也加赐福 A:`298-309`;
2. 目标作为防御方掷骰 A:`525` 并参与防御修饰器;
3. 定稿后消耗防御方防御牌耐久一次 A:`610-613` + A:`769-781`(每赐福周期一次,标记 A:`294` / A:`837`);
4. 溅射/真伤等嵌套 `hurt` 不再进入骰战:闸门 A:`200`(`aoeProcessing`,设置点 A:`625`、`SpellDamageRegistry.java:256/374`)。

### 场景 6:赐福到期(`MobEffectEvent.Expired`)

A:`826-889` 顺序:重置防御牌标记 A:`837` → 星币锤清除 A:`840` → 银行卡 A:`842` → 大碗炖肉 A:`844` → 骇客刷新 A:`846` → 枪匠弱点识破 -1 层 A:`848`(实现 A:`MosesSignItem.java:168-176`,并重设护甲修饰器) → 蓄力→全力攻击返还 A:`858-888`(费用释放用 `AppliedStone.cost` A:`865`,见 S2-C14)。

---

## 3. 冲突项

> `两版本` 列:是/否 = 1.20.1 是否同样存在(行号同构时给出 B 行号)。

### S2-C1 | **high** | 骰战防御把防御方的"攻击牌"也折算成防御,且与 GUI 显示口径矛盾

- 现象:防御方(玩家)卡牌栏里的攻击牌(中/大/特大/暗影突袭/蓄力/全力攻击/名刀)会在每次受击时被掷骰并**加到防御力**上;而界面显示的范围把它们排除了。
- 证据(实际结算,无过滤):
  A:`DiceCombatModifiers.java:419-431`
  ```java
  registerDefenseModifier((ctx, dp) -> {
      int sum = 0;
      if (ctx.targetEnhancement != null) {
          for (AppliedStone stone : ctx.targetEnhancement.appliedStones()) {
              sum += CardRegistry.roll(stone.type(), ctx, ctx.targetCardsMax);
          }
      }
      ctx.defenseCardSum = sum;
  ```
  证据(显示口径,有过滤):A:`DiceCombatModifiers.java:489-493`
  ```java
  if (player.hasEffect(ModEffects.DICE_BLESSING)) {
      for (AppliedStone stone : enhancement.appliedStones()) {
          if (!CardRegistry.isDefense(stone.type())) continue;
  ```
  同一差异在 B:`422-434`(无过滤)与 B:`494-495`(有过滤)。伤害使用点 A:`570`(`+ ctx.defenseCardSum`)。
- 附带影响:`shadow_strike`/`full_power` 的 roller 会写 `ctx.hasShadowStrike/hasFullPower`(A:`CardRegistry.java:191,216`),防御方装这两张牌时会在共享 `ctx` 上留下副作用;当前因为主流程在 A:`470-471` **先**捕获局部变量才没被污染——这是一条隐式时序契约(改动即出错)。
- 建议修法:防御修饰器内加 `if (!CardRegistry.isDefense(stone.type())) continue;`,与 `getDisplayDefenseRange` 完全对齐;或明确"攻击牌也参与防御"并同步改显示。
- 两版本:是(A:`419-431` / B:`422-434`)。

### S2-C2 | **high** | 默认优先级"覆盖式写入"吞掉前置修饰器:雨中/水下 +40%、黑曜石火焰 -70%

- 现象:骰神赐福近战命中时,受击者在雨中/水下的 +40%(`EnderDiceHandler`)被完全丢弃;用火焰近战武器攻击黑曜石骰佩戴者时 -70% 也被丢弃。而 LOW 阶段的增幅(狂暴)却保留 → 只有"前置于默认优先级"的修饰器会被吞。
- 证据(前置写入):
  A:`event/EnderDiceHandler.java:134-143`
  ```java
  @SubscribeEvent(priority = EventPriority.HIGH)
  public static void onLivingDamagePre(LivingDamageEvent.Pre event) { ... event.setNewDamage(event.getNewDamage() * RAIN_WATER_DAMAGE_MULTIPLIER);
  ```
  A:`event/ObsidianDiceHandler.java:29-37`(HIGH,`LivingIncomingDamageEvent`,`event.setAmount(reduced)`)。
  证据(覆盖写入,不读旧值):A:`DiceCombatEvents.java:604` `event.setNewDamage((float) finalDmg);`(B:`601` `event.setAmount`);A:`580` 的 `finalDmg` 只由 `attackPower - defensePower` 等本地量构成,从未读取 `event.getNewDamage()`。
  对照证据(LOW 阶段能保留):A:`894-901` `event.setNewDamage(event.getNewDamage() + 1 * (amp+1))` — 在覆盖之后执行,所以生效。
- 建议修法:在 A:`580` 之后把"前置阶段已应用的倍率"显式并回(例如记录 `event.getNewDamage()/原始值` 的比率,或在骰战结算处以 `finalDmg *= 前置倍率`);或把 EnderDice/Obsidian 的修正改挂到骰战链内部(经 `registerDiceCombatFactor`)。
- 两版本:**是**,且实现阶段不同(A 在 `LivingDamageEvent.Pre` HIGH;B 在 `LivingHurtEvent` HIGH,B:`135-143`)——两版本都会在 `LivingDamageEvent` 阶段被覆盖,但 B 的黑曜石减伤挂 `LivingHurtEvent` 默认优先级(B:`28-35`),A 挂 `LivingIncomingDamageEvent` HIGH,阶段不同(见 §4)。

### S2-C3 | **medium** | 全力攻击 ×1.5:乘在减防御**之前**,且预览/GUI 完全不含该倍率

- 现象:实际伤害 = `ceil(1.5 × (属性+修饰器+骰点+卡牌)) - 防御`;防御项**不被加倍**,所以高防御目标能吃掉大部分加成。而 `getDisplayAttackRange` 从不应用 ×1.5。
- 证据:A:`493-495`
  ```java
  if (hasFullPower) {
      attackPower = Math.ceil(attackPower * 1.5);
  }
  ```
  A:`580` `finalDmg = Math.max(1, attackPower - defensePower);`
  显示侧:A:`DiceCombatModifiers.java:438-467`(遍历攻击修饰器后直接 `min/max += 卡牌上下限`,无 ×1.5)。B 同构:B:`490-492`、B:`577`、B:`441-470`。
- 建议:明确口径。若"最终攻击力 +50%"就是当前实现,则 GUI 预览应同步乘 1.5(或标注"未含全力攻击倍率");若期望"最终伤害 +50%",应把倍率移到 A:`580` 之后。
- 两版本:是。

### S2-C4 | **medium** | "骰点 = 6"判定读取被其它效果抬高后的值

- 现象:`padmanDefBypass`(无视防御力)与占星师 +6 星币、经商的星光 ×2 都基于**被修改过**的 `baseDice`;护法爆发星级追加(+1/+2/+3)或枪匠"最低骰点 +1"可把非自然 6 变成 6,从而凭空触发破防与奖励。反向:枪匠层数 ≥1 或护法爆发期间 `baseDice` 最低为 2,上班族"骰出 1 → 下次必 6"永远无法置位。
- 证据:
  A:`391-399`(星级追加,`baseDice += starBonus`)→ A:`402-413`(`PADMAN_FORCE_SIX` / `else if (baseDice == 1)` 置位)→ A:`460-463`(写入 `ctx.baseDice`)→ A:`368-382`
  ```java
  if (ctx.baseDice == 6) {
      ctx.padmanDefBypass = true;
  }
  ```
  → A:`424-432`(`baseDice == 6` 给 6 星币)→ A:`416-421`(星光 ×2)。
  最低骰点抬高在 `rollCombatDie`:A:`988-990` `roll = Math.max(roll, 1 + WeaknessRevealEffect.getStacks(roller));`
- 建议:为"自然 6"与"结算 6"分设变量(在 `rollCombatDie` 返回处保留 raw 值),把破防/奖励绑定到明确口径;并给上班族的置位判定改用 raw 值。
- 两版本:是(B:`388-396`、`399-410`、`457-460`、`371-385`、`421-429`)。

### S2-C5 | **medium** | 上班族攻防不对称:攻击可为负,防御负值被护甲下限吞掉

- 现象:"毫无主见"每 60s 在 -2~+4 取攻防点数;攻击侧 `ap += getAttackBonus(stack)` 可为 -2,防御侧经 `setDefenseArmorBonus` 折算护甲,而该函数对 `armor <= 0` 直接**移除修饰器**(不是减护甲),因此防御 -2 等于 0。
- 证据:A:`item/sign/PadmanSignItem.java:56-64`(`int min = -2; int max = 4;` 分别写攻/防)、A:`30-31`(`setDefenseArmorBonus(player, "padman_def_armor", getDefenseBonus(stack))`)、A:`DiceCombatModifiers.java:87-102`
  ```java
  double armor = defensePoints * 2.0;
  ...
  if (armor <= 0) {
      if (existing != null) attr.removeModifier(id);
      return;
  }
  ```
  攻击侧读取:A:`DiceCombatModifiers.java:368-382` `ap += PadmanSignItem.getAttackBonus(stack);`
- 建议:明确"负防御是否应生效";若要生效需改用可负的 ADD_VALUE 修饰器并同步骰战护甲下限语义(现在 `Math.max(0, Math.min(...))` 也会夹到 0,A:`565`)。
- 两版本:是(B:`30-31`、B:`56-64`、B:`371-385`)。

### S2-C6 | **low** | 上班族主动的防御加成有 1 tick 延迟,攻击加成即时

- 现象:主动把攻防点数置 4 后,攻击力在同 tick 的攻击里立即读取生效;防御力必须等下一次 `curioTick`(A:`19-32`)才折算进 ARMOR 属性,同 tick 内结算的攻击可能吃不到。
- 证据:A:`item/sign/PadmanSignItem.java:35-44`(主动只写组件)→ A:`19-32`(`onCurioTick` 里才 `setDefenseArmorBonus`);对比 A:`DiceCombatModifiers.java:375` 直接读 stack 组件。
- 建议:主动里同步调用一次 `setDefenseArmorBonus`,而不是只写组件。
- 两版本:是。

### S2-C7 | **medium** | 同优先级脆弱依赖:狂暴(LOW)与虚弱印记(LOW)的相对顺序决定"先加后乘/先乘后加"

- 现象:两者同为 `EventPriority.LOW` 且**同一个类**的相邻方法。当前声明顺序为狂暴在前 → 结果 = `(骰战伤害 + 1×层) × 1.1(或 1.3)`;若顺序颠倒则 = `骰战伤害 × 1.1 + 1×层`,数值不同。
- 证据:A:`894-895`(`@SubscribeEvent(priority = EventPriority.LOW)` + `onBerserkDamageTaken`,加法)、A:`940-941`(同为 LOW 的 `onWeakMarkDamage`,乘法 A:`957`)。二者无任何显式相对优先级(A:`893` 与 A:`939` 的注释只声明"须先于 LOWEST",未声明互相顺序)。
- 建议:给其中一个显式指定不同优先级(如虚弱印记 LOW、狂暴 LOWEST 之前另设一档/或合并进同一处理器按固定顺序执行)。
- 两版本:是(B:`888-895` / B:`934-952`,同为 LOW)。

### S2-C8 | **medium** | 同优先级脆弱依赖:吸血受击回血(LOWEST)与安全气囊(LOWEST)跨类顺序未定义

- 现象:被气囊无效化的那一击,若吸血立牌处理器先跑,回血 = 半伤(且 ≥1);若气囊先跑(把伤害置 0),回血恒为 1。两者都是 LOWEST,分属不同类,注册顺序由事件总线扫描顺序决定,源码中未表达。
- 证据:A:`item/sign/PaparaSignItem.java:56-63`
  ```java
  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void onPaparaBiteHurtHeal(LivingDamageEvent.Pre event) { ... int heal = Math.max(1, (int) event.getNewDamage() / 2);
  ```
  A:`event/ChipDamageHandler.java:45-58`
  ```java
  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void onLivingDamagePre(LivingDamageEvent.Pre event) { ... if (damage >= player.getHealth() && AirbagChipItem.tryNegateFatal(player)) event.setNewDamage(0.0F);
  ```
- 建议:把吸血受击回血改到明确低于/高于气囊的优先级(例如 `LOW`,在气囊之前)。
- 两版本:是(A:`56` / B:`56`,同为 LOWEST)。

### S2-C9 | **medium** | 立牌主动冷却减免的"最大冷却"基准四种口径,与枪匠 120s 基准冲突

- 现象:同一语义("减少最大冷却的一半 / 一定比例")在不同立牌上使用四个不同基准:
  1. `WeirdDiceHandler.signCooldownTicks`(180s → 诡异减半 → 充能 -20%)A:`event/WeirdDiceHandler.java:36-42`;
  2. `GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS / 2`(固定 1800t,忽略诡异与充能)A:`item/sign/JasmineSignItem.java:89-90`;
  3. 固定 200t A:`item/sign/LuluSignItem.java:54-55`;
  4. 剩余 ×0.7 A:`item/sign/KomachiSignItem.java:138`;
  而枪匠的实际基准是 120s A:`item/sign/MosesSignItem.java:117-125`。后果示例:枪匠冷却 120s 时用命运指引的 180s 基准减 90s → 削减量 90s > 120s 的"一半"(60s),甚至可把冷却直接清零(被 `Math.max(0, ...)` 截断,A:`FateGuidanceCardItem.java:69`)。
- 证据:A:`FateGuidanceCardItem.java:62-71`
  ```java
  long maxCooldown = com.merlinkitsune.astral_dice.event.WeirdDiceHandler.signCooldownTicks(player);
  long reduction = maxCooldown / 2;
  ```
  + A:`JasmineSignItem.java:87-90`(用常量而非 `signCooldownTicks`)+ A:`MosesSignItem.java:117-125`。
  电流核心的即时完成费用同样按通用 180s 基准算比例(A:`CurrentCoreChipItem.java:65-70`)。
- 建议:抽出唯一入口"当前立牌的基础冷却"(按立牌类型返回 180/120),所有减免统一引用它;并在减免后再套用诡异/充能。
- 两版本:是(B:`item/card/FateGuidanceCardItem.java:66`、`item/sign/JasmineSignItem.java:89-90`、`item/sign/MosesSignItem.java:122`)。

### S2-C10 | **medium** | 虚弱印记"任意伤害 +10%"被骰战跳过守卫漏掉:骰战派生的真伤(大当家溅射等)不吃加成

- 现象:`onWeakMarkDamage` 的跳过条件只判断"攻击者是玩家 + 有赐福 + 有骰子 + 手持近战武器",而大当家溅射/法伤真伤的伤害源是 `trueDamage(level, player)`(`getEntity()` = 玩家、`directEntity` = null,A:`damage/ModDamageTypes.java:48-50`),该条件**全部成立** → 返回,不再乘 1.1。于是"受到任意伤害 +10%"对溅射无效,但骰战主伤害已自结算 1.1 倍。
- 证据:A:`DiceCombatEvents.java:946-951`
  ```java
  if (event.getSource().getEntity() instanceof Player attacker
          && attacker.hasEffect(ModEffects.DICE_BLESSING)
          && attackerHasDiceCurio(attacker)
          && isMeleeWeaponAttack(attacker)) {
      return;
  ```
  溅射真伤源:A:`640-641`;法伤真伤源:A:`event/DamageEffectCardHandler.java:62-71`。
- 建议:跳过条件改为"该事件就是骰战主伤害"(例如用 `aoeProcessing`/来源 `directEntity instanceof Player` 判定),而非"攻击者具备骰战资格"。
- 两版本:是(B:`940-945`、B:`637-638`)。

### S2-C11 | **medium** | 七咒减益只作用于"骰点+卡牌",立牌/筹码/效果攻击加成不受影响

- 现象:文档语义是"骰子伤害加成(骰点 + 卡牌点数)降低 40%",实现确实只处理 `baseDice + attackCardSum`;但护法层数、上班族、拳套、星币锤等全部在 `attackPower` 里且不被减益,因此七咒对"赐福攻击总伤害"的实际削减远低于 40%。
- 证据:A:`DiceCombatEvents.java:486-489`
  ```java
  double diceAttackBonus = applyCurseToDicePoints(player, baseDice + attackCardSum);
  double baseDamage = attackPower;
  attackPower += diceAttackBonus;
  ```
  减益实现 A:`166-181`(仅缩放传入的 points)。
- 建议:确认是否应把全部攻骰战加成纳入减益基数;若维持现状,应在 tooltip 明确"仅骰点与卡牌"。
- 两版本:是(B:`483-486`、B:`164-177`)。

### S2-C12 | **low** | 绯红骰自伤早于"最低骰点",且防御方/反击掷骰同样自伤

- 现象:`CrimsonDiceHandler.rollD6` 在返回前就 `hurt(6)`,随后 `rollCombatDie` 才用枪匠层数抬高点数 → 层数不能防自伤;防御方掷防御骰(A:`525`)与反击计算掷骰(A:`1170`)也走同一入口,因此"帮别人反击/被攻击"都可能自伤 6 点。
- 证据:A:`event/CrimsonDiceHandler.java:63-65`
  ```java
  if (result == 1 && !roller.level().isClientSide() && roller.isAlive()) {
      roller.hurt(ModDamageTypes.diceDamage(roller.level(), roller), SELF_DAMAGE_ON_ONE);
  }
  ```
  A:`DiceCombatEvents.java:976-992`(先 `rollD6` 再 `Math.max(roll, 1+stacks)`)。
- 建议:如需"最低骰点"保护,应把自伤判定移到最低骰点之后;并确认防御/反击掷骰是否应触发自伤。
- 两版本:是(B:`42-67`、B:`970-986`)。

### S2-C13 | **low** | 名刀费用 3 在两处独立硬编码,且存在 `AppliedStone.cost(String)` 传 null 玩家进入 `hasMisakiEquipped(null)` 的路径

- 证据(两处折扣实现):
  A:`combat/CardRegistry.java:81-88` `if ("meito".equals(typeId) && MisakiSignItem.hasMisakiEquipped(player)) return 3;`
  A:`item/sign/MisakiSignItem.java:48-58` `return hasMisakiEquipped(player) ? 3 : AppliedStone.cost("meito");`
  证据(null 路径):A:`component/AppliedStone.java:38-39` `return CardRegistry.cost(type, null);` → A:`item/sign/MisakiSignItem.java:60-63` `hasMisakiEquipped(Player player)` 内**无 null 检查**直接 `CuriosApi.getCuriosInventory(player)`。
  目前可达路径:`MisakiSignItem.getMeitoCost(player)` 在未佩戴护法时走 `AppliedStone.cost("meito")`(A:`49`);`DiceCombatEvents.java:865` 只对 `charge` 调用 `AppliedStone.cost`(A:`862-869`),不涉及 meito。
  多来源取值实例(被动受限):骇客解隐时 `bonus = max(2, CardRegistry.cost(typeId, player) * 2)`,A:`item/sign/NancyLuSignItem.java:144-145` → 隐身时消耗"名刀"且佩戴护法 → 费用 3 → 加成 6(而非 8)。
- 建议:删除 `MisakiSignItem` 的重复折扣,统一走 `CardRegistry.cost(type, player)`;给 `hasMisakiEquipped` 加 null 守卫或禁止 `AppliedStone.cost(String)` 传 null。
- 两版本:是(B:`CardRegistry.java:84`、B:`MisakiSignItem.java:49-63`、B:`NancyLuSignItem.java:146`、B:`AppliedStone.java` 同构)。
- 待确认:`CuriosApi.getCuriosInventory(null)` 是否抛 NPE 未能在本次只读审计中证实(见 §5-6)。

### S2-C14 | **low** | 蓄力返还使用 `AppliedStone.cost` 而非 `effectiveCost`(当前等价,属硬编码脆弱点)

- 证据:A:`DiceCombatEvents.java:862-869`
  ```java
  if ("charge".equals(stone.type())) {
      foundCharge = true;
      costFreed += AppliedStone.cost(stone.type());
  ```
  对照攻击牌耐久释放费用使用 `MisakiSignItem.effectiveCost`(A:`716`、A:`748`)。当前 `effectiveCost` 只对 meito 特判,charge 不受影响,故数值一致;但一旦其它卡加折扣,这条返还路径会漏改。
- 建议:统一改用 `MisakiSignItem.effectiveCost(player, stone.type())`。
- 两版本:是。

### S2-C15 | **low** | 骰战防御的护甲上限 20(防御点 >10 部分完全无效)

- 现象:`rawArmor = min(getArmorValue(), 20)` 且 `effectiveArmor = max(0, min(rawArmor + modifierDefense*2, 20))`,而"1 防御力 = 2 护甲" → 任何超过 10 点的防御力在骰战里不再提供收益(例如岿然不动 3 层 = +24 护甲、拳套/摩托头盔等叠加)。
- 证据:A:`DiceCombatEvents.java:563-565`;显示侧同一上限 A:`DiceCombatModifiers.java:481-483`。
- 建议:确认 20 是否为刻意的骰战上限;若否,改上限或改折算口径。
- 两版本:是(B:`560-562`、B:`484-486`)。

### S2-C16 | **medium** | 跨版本行为不等价:末影骰死亡图腾在 1.20.1 清空**全部**效果

- 现象:A 只清除"可被图腾治愈"的效果,与不死图腾一致;B 调 `removeAllEffects()`,会把玩家全部状态效果一并清掉(含本模组的赐福/增益)。
- 证据 A:`event/EnderDiceHandler.java:160` `player.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.PROTECTED_BY_TOTEM);`
  B:`event/EnderDiceHandler.java:161` `player.removeAllEffects();`
- 建议:1.20.1 用 Forge 的等价写法(过滤 `MobEffectCategory`/`isBeneficial` 或按图腾规则表),不要 `removeAllEffects`。
- 两版本:仅 1.20.1 有此差异。

---

## 4. 跨版本对等性差异

1. **主骰战入口事件**:A `LivingDamageEvent.Pre`(A:`184-185`)↔ B `LivingDamageEvent`(B:`180-181`)。Forge 1.20.1 的 `LivingDamageEvent` 与 NeoForge 1.21.1 的 `LivingDamageEvent.Pre` 都在 `actuallyHurt` 内、护甲/药水减免之后派发,语义等价;写入 API 由 `getNewDamage/setNewDamage` 变为 `getAmount/setAmount`(A:`604` ↔ B:`601`)。**注意**:审计任务描述中的"1.20.1 用 LivingHurtEvent"只对下述若干**前置**处理器成立,骰战主入口并不是 `LivingHurtEvent`。
2. **前置阶段处理器的事件类不同**(阶段语义因此不同):
   - `event/EnderDiceHandler`:A `LivingDamageEvent.Pre` HIGH(A:`134-135`)↔ B `LivingHurtEvent` HIGH(B:`135-136`)。
   - `event/ObsidianDiceHandler`:A `LivingIncomingDamageEvent` **HIGH**(A:`29-30`)↔ B `LivingHurtEvent` **默认优先级**(B:`28-29`)——优先级本身也不对等。
   - `event/DamageEffectCardHandler`:A `LivingDamageEvent.Pre`(A:`31-32`)↔ B `LivingHurtEvent`(B:`28-29`)。
   - `item/card/FateGuidanceCardItem`:A 2 个订阅(`LivingIncomingDamageEvent` LOWEST,A:`85-87`)↔ B 3 个订阅(`LivingAttackEvent` HIGHEST 捕获原始值 B:`86-90` + `LivingHurtEvent` LOWEST B:`94-95`)。
3. **最前置"取消"事件**:A 用 `LivingIncomingDamageEvent`(A:`1067`、A:`1089`、A:`294-295`、`chip/AdrenalineChipItem.java:86-87`)↔ B 用 `LivingAttackEvent`(B:`1061`、B:`1097`、B:`296-297`、B:`87-88`)。因为 `LivingAttackEvent` 派发更早(在 `isInvulnerableTo`/创造无敌/濒死之前),B 额外补了一层 `isImmuneToDamage`(B:`1081-1088`,调用点 B:`1102`、`AdrenalineChipItem.java:92`)来复刻 A 的阶段;但 **`NancyLuSignItem.onNancyLuEnderPearlDamage`(B:`296-305`)没有补这层守卫**,是遗漏点。
4. **近战武器白名单**:A 含 `MaceItem`(A:`995-1003`),B 不含(B:`989-997`)。1.20.1 无重锤,非缺陷,但要注意两版本白名单不可直接互抄。
5. **骰战修饰器注册顺序**:A:`121-432` 与 B:`124-435` 逐条同序(27 攻 + 1 防),仅 `setDefenseArmorBonus` 的修饰器 id/Operation API 不同(A:`ResourceLocation.fromNamespaceAndPath` + `ADD_VALUE` ↔ B:`UUID.nameUUIDFromBytes` + `ADDITION`)。**这是本切片里对等性最好的一环**。
6. **1.21.1 专有的嵌套伤害补丁**:A 有 `fixes/DamageStackSanitizer.java`(纯逻辑类,无事件订阅),修 NeoForge 21.1.x `LivingEntity#hurt` 在 `LivingIncomingDamageEvent` 被取消时不弹 `DamageContainer` 的泄漏(A:`10-30`)。骰战链里存在**嵌套** `hurt`:大当家溅射(A:`652`)、法伤真伤(A:`DamageEffectCardHandler.java:65-66`)、绯红自伤(A:`CrimsonDiceHandler.java:64`)、反击(A:`1143`)。1.20.1 无对应结构(Forge 无该栈),因此同一场景下 A 依赖补丁、B 不适用。
7. **已核查、确认非冲突**:
   - 骇客"隐身攻击"在近战路径上**不会**出现时序不确定:`AttackEntityEvent`(A:`248-258`)在伤害事件之前触发并已完成解隐与加成;随后 `LivingDamageEvent.Pre` 分支(A:`262-271`)因 `onAttackWhileHidden` 的前置守卫(A:`131-136`:窗口已清零/无隐身效果即返回)成为 no-op。
   - 大当家溅射的递归保护在两版本一致(A:`625/671`、B:`622/668`;`SpellDamageRegistry` 同样置位 A:`256/374`)。

---

## 5. 未能判定 / 需人工确认

1. **同优先级注册顺序无法从源码确定**:S2-C7 / S2-C8 涉及"同优先级但不显式排序"的处理器。事件总线的同优先级顺序取决于扫描/注册顺序(A 与 B 的扫描实现不同),**只能靠运行时实测**才能确定当前实际顺序。建议实测点:①同时带狂暴与虚弱印记的受击数值;②被气囊无效化且带吸血立牌时的回血量。
2. **S2-C2 的实际吞掉幅度未做运行时验证**:结论由代码路径得出(前置写入 + 覆盖写入,两者之间没有读取)。若 NeoForge/Forge 在某阶段还存在本模组未列出的其它处理器,数值可能有额外叠加,需游戏内对照。
3. **"骰战 + 法伤"两条链同击命中的可达性**:`isSpellDamage`(A:`SpellDamageRegistry.java:117-123`)依赖 `source.getMsgId()` 为 `magic/indirectMagic` 或白名单伤害类型;若某模组近战武器使用魔法伤害类型且是 Sword/Axe/Trident,则同一次近战会同时进入骰战与法伤链。本仓库无此武器样本,**未验证实际组合**。
4. **S2-C4 的口径属设计裁决,不是实现错误**:代码一律使用"结算后的骰点";是否应改为"自然骰点"需产品裁决(游戏内文档/tooltip 均只写"骰点为 6 时/骰点为 1 时")。
5. **`AppliedStone.cost(String)` 的 `null` 玩家是否安全**:`CardRegistry.cost(typeId, null)` 会走到 `MisakiSignItem.hasMisakiEquipped(null)`(A:`MisakiSignItem.java:60-63` 无 null 守卫)再进 `CuriosApi.getCuriosInventory(null)`。该 API 对 null 的处理未在本次只读审计中证实(工作区内 Curios 为二进制 jar,未能成功反编译核对),因此不能断言是 NPE 还是"返回空"。需人工确认或加守卫。
6. **`B:EnderDiceHandler` 的 `removeAllEffects()` 是否已有意为之**:仅从代码看与 A 不等价,但缺少设计依据,需裁决。
7. **`getDisplayDefenseRange` 与实战的差异(S2-C1)是否已被玩家界面其它位置弥补**:本次只核查了 `DiceCombatModifiers` 内的显示范围函数,未穷举 `event/ModTooltipHandler.java`(1286 行)与 `screen/CardInventoryScreen.java` 的全部展示入口。
8. **大当家溅射的"主目标无敌帧临时清零"**(A:`648-656`)在多人/多目标同 tick 场景下的副作用(例如同一目标同 tick 的其它伤害源)未做时序推演。
