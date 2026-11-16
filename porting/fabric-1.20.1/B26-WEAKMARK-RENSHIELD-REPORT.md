# B26 — R-3（RenShield 文档漂移）/ R-5（弱点击破守卫缺失）报告

- 仓库：`F:\MCProject\astral_dice_multiloader`，分支 `multi-main`（未 commit / 未 stash / 未 add / 未 checkout，未跑 gradle，未启动游戏）
- 目标线：`fabric-1.20.1`（功能基线 = `forge-1.20.1` P0 次基准 + `neoforge-1.21.1` P0 主基准）
- 本批改动仅两个文件：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/RenShieldManager.java`、`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java`
- 取证工具：`C:/Program Files/Zulu/zulu-21/bin/javap.exe`；合并 jar = `C:/Users/xmace/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2.jar`（26,688,459 B，21,644 entries）
- Puzzles 侧 jar = `fabric-1.20.1/build/loom-cache/remapped_working/remapped.maven.modrinth-puzzles-lib-c2d2b86c-N8gFdljq.jar`

## 0 结论摘要

| 条目 | 结论 | 落点 | 净行数 |
|---|---|---|---|
| R-3 | **成立**：本线两个可挂伤害事件都在护甲/抗性/吸收**之前**派发，原 javadoc 断言与字节码相反 | `item/RenShieldManager.java:396-427`（只改注释） | +32 / −7（422 → 447 行） |
| R-5 | **成立**：本线缺 forge 的 N8 守卫，且本线**已有**等价能力 ⇒ 已按 forge 判定顺序补齐 | `combat/DiceCombatEvents.java:1682-1686`、`:1695-1731` | +43 / −7（2180 → 2216 行） |
| 验收 a | `python scripts/__javac_check.py` → ERRORS 171 = 164 NOISE_AW + 7 NOISE_IFACE_INJECTION，**REAL 桶为空（0 条）** | — | — |
| 验收 b | `python tools/check_lang_sync.py --line fabric-1.20.1` → `[OK]` | — | — |
| 验收 c | `python tools/verify_fabric_assets.py` → `PASS: 8 项闭环全部通过` | — | — |
| BLOCKED | **无** | — | — |

---

## 1 R-3：`RenShieldManager` javadoc 与字节码相反

### 1.1 待证伪的断言（原 `:396-402`，共 7 行，整段替换；下列为删除语句的摘录）

> 「1.21.1 用 LivingDamageEvent.Pre（护甲之后、吸收之前）；**本线只能用 LivingHurtEvent** —— 它由 ForgeHooks.onLivingHurt 在 LivingEntity#actuallyHurt（1.20.1 混淆名 **m_6475_**）的最开头派发，而吸收结算（**m_6103_**）在同一方法内、晚于它；**本线的 LivingDamageEvent 则派发在吸收之后**（那时红心已经掉过，见 event/ChipDamageHandler 的类注释），故不可用。」

这条断言有两层问题：① 拿 Forge 的 `ForgeHooks` / SRG 名（`m_6475_` / `m_6103_`）当本线锚点；② 断言本线 `LivingDamageEvent` 在吸收之后——与本线实际注入点相反。

### 1.2 取证 A：原版 `LivingEntity#actuallyHurt` 字节码（`javap -p -c` 原始片段）

命令：`javap -p -c -cp "<minecraft-merged-1.20.1…jar>" net.minecraft.world.entity.LivingEntity`（摘出 `actuallyHurt` 方法体共 89 行，原样粘贴）

```
  protected void actuallyHurt(net.minecraft.world.damagesource.DamageSource, float);
    Code:
       0: aload_0
       1: aload_1
       2: invokevirtual #1769               // Method isInvulnerableTo:(Lnet/minecraft/world/damagesource/DamageSource;)Z
       5: ifeq          9
       8: return
       9: aload_0
      10: aload_1
      11: fload_2
      12: invokevirtual #2504               // Method getDamageAfterArmorAbsorb:(Lnet/minecraft/world/damagesource/DamageSource;F)F
      15: fstore_2
      16: aload_0
      17: aload_1
      18: fload_2
      19: invokevirtual #2506               // Method getDamageAfterMagicAbsorb:(Lnet/minecraft/world/damagesource/DamageSource;F)F
      22: fstore_2
      23: fload_2
      24: fstore_3
      25: fload_2
      26: aload_0
      27: invokevirtual #1239               // Method getAbsorptionAmount:()F
      30: fsub
      31: fconst_0
      32: invokestatic  #2483               // Method java/lang/Math.max:(FF)F
      35: fstore_2
      36: aload_0
      37: aload_0
      38: invokevirtual #1239               // Method getAbsorptionAmount:()F
      41: fload_3
      42: fload_2
      43: fsub
      44: fsub
      45: invokevirtual #1354               // Method setAbsorptionAmount:(F)V
      48: fload_3
      49: fload_2
      50: fsub
      51: fstore        4
      53: fload         4
      55: fconst_0
      56: fcmpl
      57: ifle          107
      60: fload         4
      62: ldc_w         #1918               // float 3.4028235E37f
      65: fcmpg
      66: ifge          107
      69: aload_1
      70: invokevirtual #1838               // Method net/minecraft/world/damagesource/DamageSource.getEntity:()Lnet/minecraft/world/entity/Entity;
      73: astore        6
      75: aload         6
      77: instanceof    #883                // class net/minecraft/server/level/ServerPlayer
      80: ifeq          107
      83: aload         6
      85: checkcast     #883                // class net/minecraft/server/level/ServerPlayer
      88: astore        5
      90: aload         5
      92: getstatic     #2509               // Field net/minecraft/stats/Stats.DAMAGE_DEALT_ABSORBED:Lnet/minecraft/resources/ResourceLocation;
      95: fload         4
      97: ldc_w         #1817               // float 10.0f
     100: fmul
     101: invokestatic  #1927               // Method java/lang/Math.round:(F)I
     104: invokevirtual #1931               // Method net/minecraft/server/level/ServerPlayer.awardStat:(Lnet/minecraft/resources/ResourceLocation;I)V
     107: fload_2
     108: fconst_0
     109: fcmpl
     110: ifne          114
     113: return
     114: aload_0
     115: invokevirtual #2087               // Method getCombatTracker:()Lnet/minecraft/world/damagesource/CombatTracker;
     118: aload_1
     119: fload_2
     120: invokevirtual #2512               // Method net/minecraft/world/damagesource/CombatTracker.recordDamage:(Lnet/minecraft/world/damagesource/DamageSource;F)V
     123: aload_0
     124: aload_0
     125: invokevirtual #1214               // Method getHealth:()F
     128: fload_2
     129: fsub
     130: invokevirtual #313                // Method setHealth:(F)V
     133: aload_0
     134: aload_0
     135: invokevirtual #1239               // Method getAbsorptionAmount:()F
     138: fload_2
     139: fsub
     140: invokevirtual #1354               // Method setAbsorptionAmount:(F)V
     143: aload_0
     144: getstatic     #2515               // Field net/minecraft/world/level/gameevent/GameEvent.ENTITY_DAMAGE:Lnet/minecraft/world/level/gameevent/GameEvent;
     147: invokevirtual #1199               // Method gameEvent:(Lnet/minecraft/world/level/gameevent/GameEvent;)V
     150: return

```

偏移 ↔ 语义（逐条读自上面字节码）：

| 偏移 | 指令 | 含义 |
|---|---|---|
| 0–8 | `isInvulnerableTo` + `ifeq 9` / `return` | 方法头只有无敌判断，**无任何伤害扣减** |
| 12 | `invokevirtual … getDamageAfterArmorAbsorb` | 护甲减免 |
| 19 | `invokevirtual … getDamageAfterMagicAbsorb` | 抗性/保护减免 |
| 25–35 | `getAbsorptionAmount` / `fsub` / `Math.max` / `fstore_2` | 吸收：算 `max(伤害−黄心, 0)` |
| 36–45 | `getAbsorptionAmount … setAbsorptionAmount` | 扣黄心 |
| 107–113 | `fload_2` / `fcmpl` / `ifne 114` / `return` | 伤害为 0 时提前返回 |
| 123–140 | `getHealth`/`setHealth`、`getAbsorptionAmount`/`setAbsorptionAmount` | 真正扣红心与黄心 |

⇒ 方法体**头部**（偏移 0–8）无任何减免；护甲在 12、抗性在 19、吸收在 25–45 与 133–140，全部**晚于**头部。

### 1.3 取证 B：本线 `LivingDamageEvent` 的实际注入点（Puzzles，`javap -v`）

命令：`javap -v -p -cp "<remapped…puzzles-lib…jar>" fuzs.puzzleslib.mixin.LivingEntityFabricMixin`

handler `protected void actuallyHurt(DamageSource, float, CallbackInfo)` 的注解（原样）：

```
        org.spongepowered.asm.mixin.injection.Inject(
          method=["actuallyHurt"]
          at=[@org.spongepowered.asm.mixin.injection.At(
            value="HEAD"
          )]
          cancellable=true
        )
```

同方法第二个 handler（把改后的值写回形参）的注解（原样）：

```
        org.spongepowered.asm.mixin.injection.ModifyVariable(
          method=["actuallyHurt"]
          at=@org.spongepowered.asm.mixin.injection.At(
            value="HEAD"
          )
          ordinal=0
          argsOnly=true
        )
```

handler 方法体（原样节选）：

```
0: aload_0 / 1: aload_1 / 2: invokevirtual isInvulnerableTo:(DamageSource;)Z / 5: ifne 54
8: aload_0 / 9: fload_2 / 10: invokestatic DefaultedFloat.fromValue:(F) / 13: putfield puzzleslib$damageAmount
16: getstatic FabricLivingEvents.LIVING_HURT / 19-22: Event.invoker() / checkcast LivingHurtCallback
39: invokeinterface LivingHurtCallback.onLivingHurt:(LivingEntity;DamageSource;MutableFloat;)Lfuzs/puzzleslib/api/event/v1/core/EventResult;
44: invokevirtual EventResult.isInterrupt:()Z / 47: ifeq 54 / 50: CallbackInfo.cancel / 54: return
```

⇒ 回调代码注入在 `actuallyHurt` 的 **HEAD**（原方法偏移 0 之前），载荷 = `actuallyHurt` 的形参 `damageAmount`，即**未经护甲/抗性/吸收的原始值**；`ordinal=0, argsOnly=true` 亦确认改的是形参而非局部变量。

### 1.4 本线两处派发者（源码锚点）

| 事件 | 派发者 | 时机 |
|---|---|---|
| `platform/event/entity/living/LivingHurtEvent` | `mixin/bridge/LivingHurtBridgeMixin.java:37-55`：`@WrapOperation(method="hurt", at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"))` | `hurt()` 里**调用 `actuallyHurt` 之前**；载荷 = 即将传入的实参 |
| `platform/event/entity/living/LivingDamageEvent` | `platform/PuzzlesBridges.java:98-110`（`LivingHurtCallback` → `new LivingDamageEvent(entity, source, amount.getAsFloat())` → `LoaderBus.post`） | `actuallyHurt` 的 **HEAD**（`isInvulnerableTo` 之后、其余方法体之前） |

⇒ **本线没有**「吸收之后」的伤害事件。`onRenShieldAbsorb` 挂在 `LivingHurtEvent` 上，读到的是护甲/抗性/吸收之前的原始值——这与该方法体的钳制用法自洽（钳到剩余黄心以内后，后续吸收必然整份吃掉，红心不掉）。

⚠️ 误导源登记：`platform/event/entity/living/LivingHurtEvent.java:14-32` 与 `LivingDamageEvent.java:13-31` 的类注释是**照抄 Forge 原文**（`LivingDamageEvent` 那段写着 "armor, potion and absorption modifiers have already been applied to damage - this is FINAL value"）。该描述对 Forge 成立，对本线不成立。

### 1.5 改动（只动 javadoc，逻辑零改动）

- 删除：原 `:396-402`（7 行）
- 新增：现 `:396-427`（32 行）
- 方法体 `onRenShieldAbsorb` 由原 `:405-414` 下移到现 `:429-438`，**逐字未改**（仅位置随注释变长而下移）

新增 javadoc 原文（现 `:396-427`，原样）：

```
     * <p><b>⚠️ 平台差异（锚点与 1.21.1 不同，原因是事件时序）</b>：1.21.1 用
     * {@code LivingDamageEvent.Pre}（护甲之后、吸收之前）；本线**没有**等价事件 ——
     * 本线可挂的两个伤害事件都在护甲 / 抗性 / 吸收**之前**派发：
     * <ul>
     *   <li>{@code LivingHurtEvent}（本方法所挂）—— 由 {@code mixin/bridge/LivingHurtBridgeMixin}
     *       用 MixinExtras {@code @WrapOperation} 包裹 {@code LivingEntity#hurt} 里对
     *       {@code actuallyHurt(DamageSource,float)} 的那一次调用，在**调用之前**派发；
     *       载荷 = 即将传给 {@code actuallyHurt} 的实参。</li>
     *   <li>{@code LivingDamageEvent} —— 由 {@code platform/PuzzlesBridges} 从 Puzzles Lib 的
     *       {@code LivingHurtCallback} 桥出，而该回调注入在 {@code LivingEntity#actuallyHurt} 的
     *       <b>HEAD</b>（{@code isInvulnerableTo} 之后、方法体其余部分之前）。</li>
     * </ul>
     * ⇒ <b>本线该事件在护甲 / 抗性 / 吸收之前派发</b>：1.20.1 的 {@code actuallyHurt} 是在**方法体内**
     * 才依次做 {@code getDamageAfterArmorAbsorb} → {@code getDamageAfterMagicAbsorb} → 吸收扣减
     * （{@code getAbsorptionAmount} / {@code setAbsorptionAmount}）⇒ HEAD 与 hurt() 处拿到的
     * 都是**未扣减**的原始值。<b>若按「吸收之后」的语义使用本事件（或本线的
     * {@code LivingDamageEvent}），读到的是未扣减值。</b>
     * <p>⚠️ 别拿 {@code platform/event/entity/living/LivingDamageEvent} 的类注释当时序依据 ——
     * 它（与 {@code LivingHurtEvent} 一样）是照抄 Forge 的原文，那几行「armor, potion and
     * absorption modifiers have already been applied … FINAL value」描述的是 **Forge 的**事件时序，
     * 在本线不成立。
     *
     * <p><b>取证锚点</b>（1.20.1 合并 jar，Mojang 映射，{@code javap -p -c}）：
     * {@code LivingEntity#actuallyHurt} 字节码偏移 0-8 只有 {@code isInvulnerableTo} + 早退；
     * 护甲 = 偏移 12 的 {@code getDamageAfterArmorAbsorb}；抗性/保护 = 偏移 19 的
     * {@code getDamageAfterMagicAbsorb}；吸收 = 偏移 25-45 与 133-140。
     * Puzzles 的 {@code LivingHurtCallback} 注入点 = {@code @At} 的 HEAD（偏移 0 之前）。
     *
     * <p>代价 = 钳制值还会被护甲再减一次 ⇒ **护盾这一击消耗得比 1.21.1 略少**（严格更保守，绝不漏血）。
     * 另：骰战会在更晚的 {@code LivingDamageEvent} 上 {@code setAmount} 覆盖本钳制 ⇒ 已由
     * {@code combat/DiceCombatEvents#settleDiceDamage} 的 N1 段重新钳一次（见该方法的注释）。
     * 优先级 {@link EventPriority#LOWEST}：本线同事件上只有 {@code EnderDiceHandler}（HIGH）。
```

要点：① 本线**没有**等价于 `LivingDamageEvent.Pre` 的事件；② `LivingHurtEvent` 的派发点（`@WrapOperation` 包裹 `hurt()` 里对 `actuallyHurt` 的调用、在调用之前）；③ `LivingDamageEvent` 由 `PuzzlesBridges` 从 `LivingHurtCallback` 桥出、注入在 `actuallyHurt` 的 HEAD；④ 明写「**本线该事件在护甲/抗性/吸收之前派发；若按吸收后语义使用会读到未扣减值**」；⑤ 警告类注释是 Forge 原文、不可当时序依据；⑥ 给出 `actuallyHurt` 逐偏移锚点与 Puzzles `@At` HEAD；⑦ 保留「代价 = 钳制值还会被护甲再减一次」；⑧ 新增「骰战会在更晚的 `LivingDamageEvent` 上 `setAmount` 覆盖本钳制 ⇒ 已由 `settleDiceDamage` 的 N1 段重新钳一次」。

### 1.6 R-3 自检

- `fabric-1.20.1/.../item/RenShieldManager.java`：422 → 447 行，`bareLF = 0`，UTF-8 无 BOM
- 逻辑零改动：只替换了注释块，方法体字节未变（§1.5 第 3 条）

---

## 2 R-5：`onWeakMarkDamage` 缺 `isDiceBattleSettledDamage`（forge N8）守卫

### 2.1 forge 蓝本原文（`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:1930-1983`，原样）

```
// 行号标注：下列代码在 forge 文件中的实际行号见每行开头注释以外的右对齐表（见 §2.2 对照表）
    // 伤害放大须先于 ChipDamageHandler(安全气囊,LOWEST)执行,故用 LOW
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onWeakMarkDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!target.hasEffect(ModEffects.WEAK_MARK.get())) return;
        // 骰战路径(玩家赐福近战 / 生物近战)的倍率已并入骰战终值,跳过以免双重应用。
        // 判据与写终值处同源,见 isDiceBattleSettledDamage(N8)。
        if (isDiceBattleSettledDamage(event.getSource(), target)) {
            return;
        }
        float multiplier = WEAK_MARK_DAMAGE_MULTIPLIER;
        if (event.getSource().getEntity() instanceof Player attacker2
                && FateGuidanceCardItem.isFateGuidanceActive(attacker2)) {
            multiplier += 0.20f;
        }
        event.setAmount(event.getAmount() * multiplier);
        // 2026-10-27 批次F(P2-B):本处理器 @LOW,若这一击由骰战 settle 写终值(吸收里含幻影 H),
        // 改了金额就必须同步重对齐吸收,否则平台 :1684 的扣减量 != H。无登记时是 no-op。
        compensateAbsorption(target, event.getAmount());
    }

    /** N8:该次伤害是否属于「骰战自算终值」路径 —— 是则虚弱印记倍率已在骰战里并入,外部处理器必须跳过。
     *  <p>判定顺序(与 {@link #onLivingDamagePre} 的入口分流**逐条同源**):<br>
     *  ① 客户端 / 内部波及(AOE 顺劈、溅射、法伤波及)与反击链中的伤害不是骰战本体 ⇒ false;<br>
     *  ② 直接伤害实体是玩家:必须近战武器、非**法伤**(法伤走 SpellDamageRegistry 加成链)、
     *     不是自伤;装备骰子 ⇒ 看是否赐福激活(骰战入口同判据),未装备 ⇒ 看目标是否为
     *     「骰神赐福合法目标」(入口的 diceStack == null 分支);<br>
     *  ③ 直接伤害实体是生物且目标是玩家 ⇒ 生物近战 —— {@link #resolveMobMeleeAttack} 无论是否
     *     对骰成立都会自算并 setAmount,故整条路径都是骰战终值;<br>
     *  ④ 其余(投掷物 / 无直接实体 / 环境伤害)⇒ false。<br>
     *  必须连内部窗口一起判:入口在 {@code isInternalAoe() || counterDepth > 0} 时直接 return,
     *  此时骰战**没有**接管金额 => 本判据也必须返回 false,否则那段伤害会丢掉虚弱印记增益。 */
    public static boolean isDiceBattleSettledDamage(DamageSource source, LivingEntity target) {
        if (source == null || target == null) return false;
        if (target.level().isClientSide()) return false;
        if (isInternalAoe() || isInCounterChain()) return false;
        Entity direct = source.getDirectEntity();
        if (direct instanceof Player attacker) {
            // F2(2026-10-27 近战审计):判据必须与 onLivingDamagePre 的入口分流**逐条同源** ——
            //   入口是「有骰 ⇒ 必须赐福激活 / 无骰 ⇒ 必须是赐福合法目标」。旧判据只写了前半句,
            //   于是「无骰玩家打赐福合法目标」(入口显式支持)走进骰战并乘过一次虚弱印记后,
            //   这里仍返回 false ⇒ 外部处理器再乘一次 ⇒ ×1.21(指引激活 ×1.69)。
            if (!isMeleeWeaponAttack(attacker)) return false;
            // 法伤链不参与骰战结算(入口 :650 的法伤闸门同源)⇒ 不算骰战终值。
            if (com.merlinkitsune.astral_dice.combat.SpellDamageRegistry.isSpellDamage(source, direct)) {
                return false;
            }
            if (attacker == target) return false;
            if (attackerHasDiceCurio(attacker)) return attacker.hasEffect(ModEffects.DICE_BLESSING.get());
            return isBlessingTarget(target, attacker);
        }
        return direct instanceof Mob && target instanceof Player;
    }
```

（上述片段覆盖 `onWeakMarkDamage` `:1930-1950` 与 `isDiceBattleSettledDamage` `:1952-1983`，中间空行 `:1951`。）

forge 常量与调用点（取证）：`WEAK_MARK_DAMAGE_MULTIPLIER = 1.10f` 在 `:332`；`isDiceBattleSettledDamage` 在 `onWeakMarkDamage` 内的调用在 `:1938`；生物近战路径的虚弱印记折叠在 `resolveMobMeleeAttack` 内 `:1477-1479`（`if (target.hasEffect(ModEffects.WEAK_MARK.get())) { finalDmg *= WEAK_MARK_DAMAGE_MULTIPLIER; }`，注释 `:1473-1476` 即 N8 说明）。

### 2.2 fabric 改前（原 `:1676-1695`，本次会话改前读取的原文；其中 `:1682-1688` 为被替换的旧式内联判据）

```
    // 伤害放大须先于 ChipDamageHandler(安全气囊,LOWEST)执行,故用 LOW
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onWeakMarkDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!target.hasEffect(ModEffects.WEAK_MARK.get())) return;
        // 骰战攻击(攻击者赐福激活 + 佩戴骰子 + 近战):虚弱印记倍率已并入骰战结算,跳过以免双重应用
        if (event.getSource().getEntity() instanceof Player attacker
                && attacker.hasEffect(ModEffects.DICE_BLESSING.get())
                && attackerHasDiceCurio(attacker)
                && isMeleeWeaponAttack(attacker)) {
            return;
        }
        float multiplier = 1.10f;
        if (event.getSource().getEntity() instanceof Player attacker2
                && FateGuidanceCardItem.isFateGuidanceActive(attacker2)) {
            multiplier += 0.20f;
        }
        event.setAmount(event.getAmount() * multiplier);
    }
```

### 2.3 fabric 改后（现 `:1676-1731`，原样，含新增守卫与私有 helper）

```
    // 伤害放大须先于 ChipDamageHandler(安全气囊,LOWEST)执行,故用 LOW
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onWeakMarkDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!target.hasEffect(ModEffects.WEAK_MARK.get())) return;
        // 骰战路径(玩家赐福近战,含无骰子但目标属于骰战适用目标的那一支)的倍率已并入骰战终值,
        // 跳过以免双重应用。判据与写终值处同源,见 isDiceBattleSettledDamage(N8)。
        if (isDiceBattleSettledDamage(event.getSource(), target)) {
            return;
        }
        float multiplier = 1.10f;
        if (event.getSource().getEntity() instanceof Player attacker2
                && FateGuidanceCardItem.isFateGuidanceActive(attacker2)) {
            multiplier += 0.20f;
        }
        event.setAmount(event.getAmount() * multiplier);
    }

    /**
     * 「这次伤害是否由骰战结算写出终值」—— {@link #onWeakMarkDamage} 的跳过去重判据（N8）。
     *
     * <p>判定顺序与 {@link #onLivingDamagePre} 的入口分流**逐条同源**（forge-1.20.1 的同名方法同形），
     * 所以返回 {@code true} 等价于「该伤害的虚弱印记倍率已经在写终值处乘过」，本事件必须跳过。
     *
     * <h2>本线差异（与 forge-1.20.1 不同，理由=同源而非照抄）</h2>
     * <ul>
     *   <li><b>法伤子句缺失</b>：forge 还有一条 {@code SpellDamageRegistry.isSpellDamage(source, direct)}
     *       排除，因为 forge 的入口先有一道法伤闸门把法术挡在骰战之外。本线入口<b>没有</b>那道闸门
     *       ⇒ 只要主手持近战武器，法术伤害也会走骰战结算、并在终值里乘过虚弱印记 ⇒ 此处必须跳过；
     *       照抄 forge 那条会造成**双重应用**。</li>
     *   <li><b>生物→玩家近战返回 false</b>（forge 返回 {@code direct instanceof Mob && target instanceof Player}）：
     *       forge 的生物近战路径已把虚弱印记并入终值（{@code resolveMobMeleeAttack} 内），本线的
     *       {@code resolveMobMeleeAttack} **没有**这一步 ⇒ 生物打玩家的 +10% 仍由本事件提供；
     *       此处若返回 {@code true}，会让「带虚弱印记的玩家挨生物近战」整段丢掉 +10%（回归）。</li>
     * </ul>
     * <p>⚠️ 这两条都是「本线终值折叠点比 forge 少」的症状登记：等本线补齐「入口法伤闸门」与
     * 「生物近战路径的虚弱印记折叠」之后，本方法应改回与 forge 逐条同形。
     */
    private static boolean isDiceBattleSettledDamage(DamageSource source, LivingEntity target) {
        if (source == null || target == null) return false;
        if (target.level().isClientSide()) return false;
        // 本模组内部 AOE(顺劈/溅射/法伤波及)与反击链:入口在分流前直接 return,不进骰战 ⇒ 未折叠
        if (isInternalAoe() || isInCounterChain()) return false;
        Entity direct = source.getDirectEntity();
        if (direct instanceof Player attacker) {
            if (!isMeleeWeaponAttack(attacker)) return false;
            if (attacker == target) return false;
            // 与入口的骰战适用闸门同源:有骰子槽 ⇒ 要求骰神赐福;无骰子 ⇒ 要求目标属于骰战适用目标
            if (attackerHasDiceCurio(attacker)) {
                return attacker.hasEffect(ModEffects.DICE_BLESSING.get());
            }
            return isBlessingTarget(target, attacker);
        }
        return false;
    }
```

### 2.4 forge vs fabric 改前：逐条差异（4 条）

| # | forge | fabric 改前 | 后果 |
|---|---|---|---|
| ① | `if (isInternalAoe() \|\| isInCounterChain()) return false;` | 无 | 内部 AOE（顺劈/溅射/法伤波及）与反击链中的伤害在骰战入口 `:434` 已被 `return` 挡下（未折叠倍率），旧判据却可能把它们判成「骰战终值」而跳过 +10% |
| ② | 用 `source.getDirectEntity()` | 用 `source.getEntity()` | 荆棘/反伤（`directEntity` 为目标自己或 `null`、`getEntity()` 仍是该玩家）与宠物 `mob_attack`（归属者=玩家但 `directEntity`=宠物）会被误判为「玩家骰战攻击」并跳过 +10% |
| ③ | 有骰 ⇒ 看赐福；无骰 ⇒ `isBlessingTarget(target, attacker)` | 仅「有赐福 + 有骰 + 近战」 | 入口 `:607-611` 显式支持「无骰玩家打 `isBlessingTarget` 目标」也进骰战（该路径终值在 `:931-939` 已乘虚弱印记）⇒ 旧判据此处返回 false，**再乘一次**（×1.10，命运指引激活时 ×1.32） |
| ④ | 生物→玩家分支 `return direct instanceof Mob && target instanceof Player;` | 无（落到末尾返回 false / 不进守卫） | 见 §2.5 偏离说明（本线生物路径未折叠，故有意返回 false） |

### 2.5 本线已有的等价能力（全部存在，未新增依赖）

| 符号 | 位置（现行号） | 用途 |
|---|---|---|
| `isInCounterChain()` | `combat/DiceCombatEvents.java:294` | 反击链窗口判定 |
| `isInternalAoe()` | `combat/DiceCombatEvents.java:300` | 内部 AOE 窗口判定（`aoeDepth`/`counterDepth` 计数） |
| `attackerHasDiceCurio(Player)` | `combat/DiceCombatEvents.java:1734` | Curios 骰子槽探测 |
| `isMeleeWeaponAttack(Player)` | `combat/DiceCombatEvents.java:1839` | 近战武器判定 |
| `isBlessingTarget(LivingEntity, Player)` | `combat/DiceCombatEvents.java:1909` | 骰战适用目标判定（入口 `:608` 同用） |
| 入口 `onLivingDamagePre` | `combat/DiceCombatEvents.java:405` | 分流顺序的「源」（`:434` 内部窗口 → `:458` 玩家直伤 → `:463` 生物近战 → `:488` 近战武器 → `:607-611` 骰战适用闸门） |
| `settleDiceDamage` | `combat/DiceCombatEvents.java:176` | 两条写终值路径的唯一出口（玩家 `:958`、生物 `:1264`） |

旁证：入口的「有骰」判据用的是 `diceStack`（`:491-500`，`findFirstCurio(DiceCurioItem::isDiceItem)`），与 `attackerHasDiceCurio` 同一查询 ⇒ 本 helper 的分支选择与入口等价。

### 2.6 本批实现与 forge 的两处**有意**偏离（已在 helper javadoc 内写明）

1. **不写 forge 的法伤子句** `SpellDamageRegistry.isSpellDamage(source, direct)`（forge 在 `:1975-1977`）。理由：forge 入口 `:680` 有一道法伤闸门把法术挡在骰战之外，故 N8 里「法伤 ⇒ 不算骰战终值」成立；本线 `DiceCombatEvents.java` 全文件 **没有** `SpellDamageRegistry` 的任何调用（grep 零命中），法术伤害只要主手持近战武器就会走骰战结算、其终值已在 `:931-939` 乘过虚弱印记 ⇒ 这里必须返回 true（跳过），照抄 forge 那条会造成**双重应用**。
2. **生物→玩家分支返回 false**（forge `:1982` 返回 `direct instanceof Mob && target instanceof Player`）。理由：forge 的生物近战路径已在 `resolveMobMeleeAttack` 内 `:1477-1479` 折叠虚弱印记；本线同一路径 `resolveMobMeleeAttack`（`:1193` 起，终值 `:1264`）**没有**这一步——`WEAK_MARK` 在 fabric 该文件里只有 `:933`（玩家路径）与 `:1681`（本事件）两处 ⇒ 若照抄，本线「玩家身上有虚弱印记、被生物近战命中」会整段丢掉 +10%（回归）。

第 3 处差异是可见性：本 helper 为 `private static`（forge 是 `public static`），因为本批授权只覆盖 `onWeakMarkDamage` 及其私有 helper。

### 2.7 行为影响

- **收敛（修复）**：荆棘/反伤、宠物 `mob_attack`、内部 AOE/反击链中的伤害不再被误判为骰战终值 ⇒ 这些伤害重新获得 +10%（命运指引激活时 +20%）虚弱印记增益；「无骰玩家打骰战适用目标」不再被叠乘两次。
- **保持**：生物→玩家的近战仍由本事件提供 +10%（本线未折叠，见 §2.6.2）；潜行/闪避对骰等未折叠路径不受影响。
- **已知边界**：`PLAYER_DODGE_ENABLED`（`:259`，当前 `false`，编译期关闭）一旦启用，玩家路径 `:820-829` 会在 `settleDiceDamage` 之前 `return`（不折叠倍率），届时本 helper 的玩家分支必须同步加一条「闪避未生效」判定，否则该分支会误跳过 +10%。已写入 helper javadoc 的警告段。

### 2.8 R-5 自检

- `fabric-1.20.1/.../combat/DiceCombatEvents.java`：2180 → 2216 行，`bareLF = 0`，UTF-8 无 BOM
- 净变化 +43 / −7：守卫替换 `+5 / −7`（`:1682-1686`），helper 新增 38 行（`:1695-1731` + 其后空行）
- 后续行号整体后移 38 行左右：`attackerHasDiceCurio` 定义 1698 → 1734，`isMeleeWeaponAttack` → `:1839`，`isBlessingTarget` → `:1909`；`onLivingDamagePre` 仍在 `:405`（未变）

---

## 3 改动行号汇总

| 文件 | 位置（现行号） | 性质 | 新增/删除 |
|---|---|---|---|
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/RenShieldManager.java` | `:396-427` | javadoc 重写（原 `:396-402`） | +32 / −7 |
| 同上 | `:429-438` | `onRenShieldAbsorb` 方法体，位置下移，**内容未变** | 0 |
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java` | `:1682-1686` | 守卫替换（原 `:1682-1688` 内联判据） | +5 / −7 |
| 同上 | `:1695-1731` | 新增 `private static boolean isDiceBattleSettledDamage(DamageSource, LivingEntity)`（含 javadoc `:1695-1714`） | +38 |
| 同上 | `:1676-1693` | `onWeakMarkDamage` 全貌（函数体其余部分逐字未改） | 0 |

两个文件的 `bareLF` 均为 0，UTF-8 无 BOM；未发生整文件重写或格式化（改动均落在上表区间内）。

---

## 4 验收原始输出

### 4.1 `python scripts/__javac_check.py`

```
EXIT 1 ERRORS 171 jars 685 srcs 525
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  164  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 984
```

逐条核对 REAL 桶（对 `fabric-1.20.1/build/javac-check/errors.txt` 用与脚本相同的 `bucket()` 规则复核）：

```
total 171 {"NOISE_AW":164,"NOISE_IFACE_INJECTION":7}
REAL count 0
errors mentioning my files: 0 []
```

⇒ **REAL 桶为空，本批引入 0 条**；171 = 164 + 7，与脚本输出一致；`RenShieldManager.java` / `DiceCombatEvents.java` 均无任何 error 记录。`EXIT 1` 是 javac 对 NOISE 桶（Loom AW / 接口注入，javac 侧无法复现）的正常非零返回，不是本批失败。

⚠️ 与任务书基线的差异（记录，非本批引起）：任务书给的基线是 `ERRORS 172 = 164 NOISE_AW + 7 NOISE_IFACE_INJECTION + 1 REAL`，那条 REAL 在 `network/VersionHandshake.java:259`。本次运行 ERRORS 171、REAL 空 ⇒ 该 REAL 已不在。本批未触碰该文件，判断是他人在同一工作树上继续修改（该文件当前仍为未跟踪的新文件）后的状态，属并行改动，与本批无关。

### 4.2 `python tools/check_lang_sync.py --line fabric-1.20.1`

```
[OK]   fabric-1.20.1: en_us=1094 键，3 份语言键集一致
EXIT=0
```

（首次运行时控制台按 cp936 解码，中文显示为乱码；以 `PYTHONIOENCODING=utf-8` 重跑得到以上原文，`EXIT=0` 与 `[OK]` 一致。）

### 4.3 `python tools/verify_fabric_assets.py`

```
jar = fabric-1.20.1\build\libs\astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar

[!] build/libs 内 jar 版本 1.3.7-alpha.2+fabric_1.20.1 与 mod_version 1.5.0-rc.7+fabric_1.20.1 不符，改为扫描源码资源目录
[1] 物品闭环: 注册 157 (方块物品 3) / 模型 158 / 贴图 155 (方块贴图另计)
[2] 标签闭环: 引用 11 / 自建 29 / 有提供者 11
[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
[4] 粒子闭环: 注册 2 / particles/*.json 2
[5] 语言闭环: zh_cn 1094 / en_us 1094 / ja_jp 1094
[6] 手册闭环: 条目 184 / 引用配方 147
[7] 创意标签覆盖: 156/157
    已知与基线一致（不阻塞）: ['repulsion']
[8] 饰品槽位图标: 检查 7 条 icon

=== PASS: 8 项闭环全部通过 ===
EXIT=0
```

（`[!]` 那行是既有告警：`build/libs` 里的旧 jar 版本号落后于 `gradle.properties` 的 `mod_version`，脚本按设计改扫源码资源目录——本批禁跑 gradle，无法刷新该 jar；与 8 项闭环结论无关。）

### 4.4 `git diff --numstat`（原文，未做任何裁剪）

```
1	1	AGENTS.md
5	3	docs/agent/AGENTS-dsh-linux.md
3	1	docs/compat-1.5.0-porting.md
15	9	fabric-1.20.1/build.gradle
8	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceClient.java
53	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java
27	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/audio/ModSounds.java
15	2	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/audio/SoundPlayback.java
16	3	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/ModClientEvents.java
38	5	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DamageCardFxScheduler.java
335	25	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java
86	16	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatModifiers.java
38	55	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/SpellDamageRegistry.java
110	2	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/command/AstralPartyCommand.java
32	4	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/component/AppliedStone.java
91	21	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/component/DeathPreservedBonuses.java
133	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/component/ModAttachments.java
19	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/component/ModDataComponents.java
15	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/config/ModCommonConfig.java
7	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen/ModItemModelProvider.java
79	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen/ModRecipeProvider.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/effect/DragonRoarBreakEffect.java
110	3	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/effect/ModEffects.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/effect/UnwaveringEffect.java
101	31	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/AnvilUpgradeHandler.java
15	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/AstralEventSystem.java
26	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ChipDamageHandler.java
41	2	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModEffectEvents.java
74	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java
78	15	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/PlayerTickEvents.java
54	5	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/RailgunStrikeScheduler.java
34	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/TemporaryCardEvents.java
41	8	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/WeirdDiceHandler.java
33	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/init/ModCreativeTabs.java
40	6	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/ChargeManager.java
16	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/HealingManager.java
152	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java
30	6	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/RenShieldManager.java
10	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/BaseEffectCardItem.java
18	2	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/ExclusiveCardUtil.java
15	5	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/FateGuidanceCardItem.java
1	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/RandomCardHandler.java
153	7	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/TemporaryCardUtil.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/UnwaveringCardItem.java
2	2	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/AdrenalineChipItem.java
12	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BaseChipItem.java
2	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/CurrentCoreChipItem.java
137	36	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/ElectricGloveChipItem.java
8	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/MemberRecommendationChipItem.java
8	7	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/MotoHelmetChipItem.java
58	4	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/PerpetualMotionChipItem.java
3	3	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/PiercingGunChipItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/PrimordialCoreChipItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/RevengeHalberdChipItem.java
15	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/VitaminPillChipItem.java
2	2	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/NetherStarDiceItem.java
5	4	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/ObsidianDiceItem.java
66	3	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BaseSignItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BonnieSignItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/HaiqingSignItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/JasmineSignItem.java
5	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/LuluSignItem.java
4	5	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/MegasSignItem.java
5	6	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/MosesSignItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/NancyLuSignItem.java
3	3	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/NardisSignItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/PadmanSignItem.java
86	71	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/ParunanSignItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/RenSignItem.java
15	5	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/SherrySignItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/TeruSignItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/ZhaoSignItem.java
199	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/ModNetwork.java
61	21	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/VersionGate.java
8	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/FabricBridges.java
26	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/PuzzlesBridges.java
10	5	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/network/NetworkRegistry.java
38	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/network/SimpleChannel.java
25	3	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/screen/CardInventoryMenu.java
15	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/screen/ModMenuTypes.java
257	9	fabric-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json
257	9	fabric-1.20.1/src/main/resources/assets/astral_dice/lang/ja_jp.json
257	9	fabric-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json
40	0	fabric-1.20.1/src/main/resources/assets/astral_dice/sounds.json
-	-	fabric-1.20.1/src/main/resources/assets/astral_dice/textures/mob_effect/railgun_ready.png
7	1	fabric-1.20.1/src/main/resources/astral_dice.mixins.json
8	1	fabric-1.20.1/src/main/resources/data/astral_dice/tags/items/signs.json
7	1	fabric-1.20.1/src/main/resources/data/curios/tags/items/chip.json
9	1	fabric-1.20.1/src/main/resources/fabric.mod.json
1	1	gradle/wrapper/gradle-wrapper.properties
9	0	neoforge-26.1.2/src/main/templates/META-INF/neoforge.mods.toml
226	117	scripts/test/mt.ps1
205	6	scripts/test/mt_case.ps1
111	16	scripts/test/mt_launch.ps1
88	1	scripts/test/mt_preflight.ps1
30	7	tools/verify_defense_unit_parity.py
110	7	tools/verify_fabric_assets.py
```

**归属说明（重要）**：该工作树在我动手前就带有前几批（B19–B25 等）的大量未提交改动，`git diff` 是相对 `HEAD` 的全量差分，故上表数字是**累计值**，不等于本批改动。本批实际归属：

| 文件 | 本批新增 | 本批删除 | 说明 |
|---|---|---|---|
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/RenShieldManager.java` | 32 | 7 | numstat 显示 `30	6` 中的本批部分；其余归前几批 |
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java` | 43 | 7 | numstat 显示 `335	25` 中的本批部分 |

（本批没有新增/删除任何文件，未触碰其余路径；报告中列举的其余文件仅为取证引用。）

---

## 5 需要你裁决（5 条）

### 5.1 R-5 偏离①：未照抄 forge 的法伤子句

- **现状**：新 helper 里没有 `if (SpellDamageRegistry.isSpellDamage(source, direct)) return false;`（forge 在 `:1975-1977`）。
- **原因**：forge 的入口 `onLivingDamagePre` 有一道法伤闸门（`forge-1.20.1/.../combat/DiceCombatEvents.java:680`，把法术源挡在骰战之外），所以 forge 的 N8 才成立；本线入口**没有**这道闸门（`fabric-1.20.1/.../combat/DiceCombatEvents.java` 全文件无 `SpellDamageRegistry` 调用，grep 零命中），法术伤害只要主手持近战武器就会进骰战结算，其终值已在 `:931-939` 乘过虚弱印记。这里若照抄返回 false，本事件会**再乘一次**（×1.10，命运指引激活时 ×1.32）。
- **请你裁决**：(A) 接受当前实现（本线口径：法伤走骰战时不算「非骰战伤害」）；或 (B) 要求与 forge 逐条同形——那就必须先在入口补 forge `:680` 那道法伤闸门，这属于入口分流行为变更，超出本批授权（只允许改 `onWeakMarkDamage` 及其私有 helper），需要另开批次。

### 5.2 R-5 偏离②：生物→玩家分支返回 false

- **现状**：`direct instanceof Mob && target instanceof Player` 时，本 helper 落到末尾 `return false;`（= 不跳过，照常 +10%）。forge `:1982` 返回 true（= 跳过，因为 forge 已在生物近战路径折叠过）。
- **原因**：forge 的生物近战路径在 `resolveMobMeleeAttack` 内 `:1477-1479` 有 N8 折叠（`finalDmg *= WEAK_MARK_DAMAGE_MULTIPLIER`，注释 `:1473-1476` 明写「onWeakMarkDamage 对生物近战骰战路径改为跳过（判据 isDiceBattleSettledDamage 同源）」）；本线同一路径（`fabric-1.20.1/.../combat/DiceCombatEvents.java:1193` 起，终值 `:1264`）**没有**这一步——`WEAK_MARK` 在该文件里只有 `:933`（玩家路径）与 `:1681`（本事件）两处。照抄会让「玩家带虚弱印记、被生物近战命中」整段丢掉 +10%（回归）。
- **请你裁决**：(A) 接受当前实现（本线生物近战仍由本事件提供 +10%，与改前行为一致，只是判据收敛了误跳面）；或 (B) 要求与 forge 完全同形——那需要把 forge `:1477-1479` 的折叠搬进本线 `resolveMobMeleeAttack`（改变折叠时序：从「吸收之前的事件」移到 settle 终值），同属入口/结算路径变更，超出本批授权。

### 5.3 helper 可见性

- **现状**：`private static boolean isDiceBattleSettledDamage(DamageSource, LivingEntity)`；forge 是 `public static`。
- **原因**：本批授权只覆盖 `onWeakMarkDamage` 及其私有 helper。目前本线无外部调用点（grep 只有本文件 `:1685` 一处调用）。
- **请你裁决**：是否需要统一为 `public static` 以便后续批次（如把 N8 判据复用到其它事件）调用。

### 5.4 同族 javadoc 漂移（4 处，本批未改）

以下位置仍写着「1.20.1 的 `LivingDamageEvent` 在吸收结算之后」，与 §1 取证的结论相反；它们**不在**本批允许改动范围，故只在报告登记：

| 文件 | 位置 |
|---|---|
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ChipDamageHandler.java` | 类注释 + `:88` |
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/DamageEffectCardHandler.java` | `:33-38` |
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/WhetstoneChipItem.java` | `:79` |
| `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/SherrySignItem.java` | `:517-518` |

**请你裁决**：是否另开批次按 R-3 的口径统一订正（判据一律以注入点为准——Puzzles `LivingHurtCallback` = `actuallyHurt` 的 HEAD；自写 `LivingHurtBridgeMixin` = `hurt()` 内对 `actuallyHurt` 的调用点之前）。

### 5.5 基线 ERRORS 计数差异

任务书基线：`ERRORS 172`（164 NOISE_AW + 7 NOISE_IFACE_INJECTION + 1 REAL @ `network/VersionHandshake.java:259`）。本次实测：`ERRORS 171`，REAL 桶为空。本批未触碰该文件（它当前仍为未跟踪的新文件），判为他人在同一工作树上的并行改动。仅登记待复核，不影响本批结论。

---

## 6 BLOCKED

**无。** R-3 与 R-5 均已在授权文件与授权范围内闭环：R-3 结论经 javap 复核实证成立、仅改 javadoc 文本；R-5 的等价能力在本线全部存在、守卫已按本线入口同源补齐。两处与 forge 蓝本的有意偏离已显式记录（§2.6）并提交裁决（§5.1 / §5.2），不构成阻塞。

---

## 7 附：取证复现命令

### 7.1 vanilla `LivingEntity#actuallyHurt`

```
C:/Program Files/Zulu/zulu-21/bin/javap.exe -p -c -cp \
  "C:/Users/xmace/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2.jar" \
  net.minecraft.world.entity.LivingEntity
```

### 7.2 Puzzles 事件桥注入点

```
C:/Program Files/Zulu/zulu-21/bin/javap.exe -v -p -cp \
  "fabric-1.20.1/build/loom-cache/remapped_working/remapped.maven.modrinth-puzzles-lib-c2d2b86c-N8gFdljq.jar" \
  fuzs.puzzleslib.mixin.LivingEntityFabricMixin
```

### 7.3 两个陷阱（复现时必看）

1. **`~/.gradle/caches/fabric-loom/1.20.1/minecraft-merged.jar` 是 22 字节占位符**，对它跑 `javap` 只会得到「找不到类」。真正的字节码在 `minecraftMaven/net/minecraft/minecraft-merged/…-loom.mappings…jar`（本例 26,688,459 B / 21,644 entries）。
2. **`javap -cp` 直接读 jar 即可，不要解包**——在受限 shell 下 `mkdir /tmp` 与 `mkdir ~` 都可能 `Permission denied`。

---

## 8 结论

| 项 | 结论 | 证据 |
|---|---|---|
| R-3 | **闭环**。javap 复核确认：本线两个字面事件都派发在护甲/抗性/吸收**之前**，原 javadoc 的「本事件在吸收扣减之后派发」与字节码相反；仅改注释文本，逻辑零改动 | §1.2–§1.5、§4.1 |
| R-5 | **闭环**。本线已有 `isInCounterChain` / `isInternalAoe` / `attackerHasDiceCurio` / `isMeleeWeaponAttack` / `isBlessingTarget` 全套等价能力；守卫已按本线入口 `onLivingDamagePre` 的判定顺序补齐，两处与 forge 蓝本的有意偏离已记录 | §2.1–§2.8、§5.1–§5.2 |
| REAL errors | **空**（171 = 164 NOISE_AW + 7 NOISE_IFACE_INJECTION，本批引入 0 条） | §4.1 |
| lang 同步 | `[OK]`，1094 键 × 3 一致 | §4.2 |
| 资源闭环 | 8 项全部 PASS | §4.3 |
| 改动面 | 仅 2 个授权文件，本批 +75 / −14；无格式化、无整文件重写 | §3、§4.4 |
| BLOCKED | 无 | §6 |
