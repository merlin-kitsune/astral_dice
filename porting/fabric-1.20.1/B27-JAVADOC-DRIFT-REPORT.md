# B27 · fabric-1.20.1 javadoc 事实订正报告

| 项 | 值 |
|---|---|
| 仓库 / 分支 | F:/MCProject/astral_dice_multiloader · multi-main |
| 范围 | fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/ 下 4 个文件的注释文本 |
| 性质 | 注释 only（不改 if、返回值、方法签名、数值） |
| 取证日期 | 2026-11-01 |
| 结论 | 6 个注释块订正，1 项判定「无需改」；验收 a/c/d 全绿，REAL 两节为空；无 BLOCKED |

## 0. 结论摘要

1. 已确证的事实（javap 复核，见第 1 节）：fabric-1.20.1 的 `LivingDamageEvent` 由 Puzzles Lib 8.1.33 的 `fuzs.puzzleslib.mixin.LivingEntityFabricMixin#actuallyHurt` 以 `@Inject(at=@At("HEAD"))` 派发；1.20.1 的 `LivingEntity#actuallyHurt(DamageSource,float)` **方法体内部**才做护甲（偏移 12）、魔法抗性（19）、吸收（25-45）与扣血（123-140）。⇒ 本线该事件载荷是**护甲与吸收之前**的原始值。
2. 因此凡写「1.20.1 的 LivingDamageEvent 在吸收之后派发」的注释在本线都是错的（该说法对 Forge 成立、对 Fabric 不成立）。本批把这类表述全部换成 fabric 侧锚点。
3. 改动 4 个文件、6 个注释块，B27 增量 +36/-29，**非注释行增删为 0**（判据与原始输出见第 4.2 节）。
4. 第 5 项：`event/ChipDamageHandler.java` 中批次 D-10 留下的平台注本来就写对了（注入点在方法入口、载荷在护甲/抗性/吸收之前），**判定无需改，保留原文**（见第 3 节）。

## 1. 取证

### 1.1 派发链路（本线）

- Puzzles Lib 的 mixin `LivingEntityFabricMixin#actuallyHurt(...CallbackInfo)` 在 `actuallyHurt` 的 `HEAD` 回调 `LivingHurtCallback`；
- 本线 `platform/PuzzlesBridges.java:99-110` 把它桥接成自有的 `LivingDamageEvent` 并 `LoaderBus.INSTANCE.post(event)`，若事件被取消则 `EventResult.INTERRUPT`，若回调改了值则 `amount.accept(...)`（写回由同 mixin 的 `@ModifyVariable(at=HEAD, ordinal=0, argsOnly=true)` 在方法头完成，仍在护甲之前）；
- 本线另有自己的 `mixin/bridge/LivingHurtBridgeMixin` 以 `@WrapOperation` 包住 `hurt` 内对 `actuallyHurt` 的调用，桥接 `LivingHurtEvent`，因此 `LivingHurtEvent` 与 `LivingDamageEvent` **都在护甲之前**。

### 1.2 取证命令与制品

- minecraft-merged（fabric-loom 1.20.1 命名合并 jar，26,688,459 B）：
```text
/c/Users/xmace/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2.jar
```
- Puzzles Lib 8.1.33（fabric.mod.json 内 `id=puzzleslib`, `version=8.1.33`，1,084,397 B）：
```text
/c/Users/xmace/.gradle/caches/modules-2/files-2.1/maven.modrinth/puzzles-lib/N8gFdljq/13282e818c9b5d20ba9f50949e3776d678d6926d/puzzles-lib-N8gFdljq.jar
```
- 命令（同一条用于两个 jar，只换 classpath 与类名）：
```bash
javap -p -c -classpath "<jar>" net.minecraft.world.entity.LivingEntity
javap -v -p -classpath "<jar>" fuzs.puzzleslib.mixin.LivingEntityFabricMixin
```

### 1.3 `net.minecraft.world.entity.LivingEntity#actuallyHurt`（原始片段，未经改写）

```text
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
偏移对照：`0-8` 先判 `isInvulnerableTo`（假则继续）；`12` `getDamageAfterArmorAbsorb`（护甲）；`19` `getDamageAfterMagicAbsorb`（魔法抗性）；`25-35` 吸收量扣减；`36-45` `setAbsorptionAmount`；`107-113` `fcmpl/ifne 114`（伤害为 0 则 `return`，不扣血）；`123-140` `setHealth` 与吸收量写回；`143-147` `GameEvent.ENTITY_DAMAGE`。

### 1.4 `fuzs.puzzleslib.mixin.LivingEntityFabricMixin#actuallyHurt`（原始片段，未经改写）

```text
  protected void actuallyHurt(net.minecraft.class_1282, float, org.spongepowered.asm.mixin.injection.callback.CallbackInfo);
    descriptor: (Lnet/minecraft/class_1282;FLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V
    flags: (0x0004) ACC_PROTECTED
    Code:
      stack=4, locals=4, args_size=4
         0: aload_0
         1: aload_1
         2: invokevirtual #459                // Method method_5679:(Lnet/minecraft/class_1282;)Z
         5: ifne          54
         8: aload_0
         9: fload_2
        10: invokestatic  #464                // InterfaceMethod fuzs/puzzleslib/api/event/v1/data/DefaultedFloat.fromValue:(F)Lfuzs/puzzleslib/api/event/v1/data/DefaultedFloat;
        13: putfield      #466                // Field puzzleslib$damageAmount:Lfuzs/puzzleslib/api/event/v1/data/DefaultedFloat;
        16: getstatic     #469                // Field fuzs/puzzleslib/api/event/v1/FabricLivingEvents.LIVING_HURT:Lnet/fabricmc/fabric/api/event/Event;
        19: invokevirtual #126                // Method net/fabricmc/fabric/api/event/Event.invoker:()Ljava/lang/Object;
        22: checkcast     #471                // class fuzs/puzzleslib/api/event/v1/entity/living/LivingHurtCallback
        25: ldc           #128                // class net/minecraft/class_1309
        27: aload_0
        28: invokevirtual #134                // Method java/lang/Class.cast:(Ljava/lang/Object;)Ljava/lang/Object;
        31: checkcast     #128                // class net/minecraft/class_1309
        34: aload_1
        35: aload_0
        36: getfield      #466                // Field puzzleslib$damageAmount:Lfuzs/puzzleslib/api/event/v1/data/DefaultedFloat;
        39: invokeinterface #475,  4          // InterfaceMethod fuzs/puzzleslib/api/event/v1/entity/living/LivingHurtCallback.onLivingHurt:(Lnet/minecraft/class_1309;Lnet/minecraft/class_1282;Lfuzs/puzzleslib/api/event/v1/data/MutableFloat;)Lfuzs/puzzleslib/api/event/v1/core/EventResult;
        44: invokevirtual #144                // Method fuzs/puzzleslib/api/event/v1/core/EventResult.isInterrupt:()Z
        47: ifeq          54
        50: aload_3
        51: invokevirtual #149                // Method org/spongepowered/asm/mixin/injection/callback/CallbackInfo.cancel:()V
        54: return
      StackMapTable: number_of_entries = 1
        frame_type = 54 /* same */
      LineNumberTable:
        line 224: 0
        line 225: 8
        line 226: 16
        line 227: 50
        line 230: 54
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0      55     0  this   Lfuzs/puzzleslib/mixin/LivingEntityFabricMixin;
            0      55     1 damageSource   Lnet/minecraft/class_1282;
            0      55     2 damageAmount   F
            0      55     3 callback   Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;
    RuntimeVisibleAnnotations:
      0: #108(#109=[s#453],#110=[@#111(#7=s#112)],#113=Z#114)
        org.spongepowered.asm.mixin.injection.Inject(
          method=["actuallyHurt"]
          at=[@org.spongepowered.asm.mixin.injection.At(
            value="HEAD"
          )]
          cancellable=true
        )
    MethodParameters:
      Name                           Flags
      damageSource
      damageAmount
      callback


```

### 1.5 同 mixin 的 `@ModifyVariable` 写回处理器（原始片段）

```text
  protected float actuallyHurt(float, net.minecraft.class_1282);
    descriptor: (FLnet/minecraft/class_1282;)F
    flags: (0x0004) ACC_PROTECTED
    Code:
      stack=2, locals=3, args_size=3
         0: aload_0
         1: aload_2
         2: invokevirtual #459                // Method method_5679:(Lnet/minecraft/class_1282;)Z
         5: ifne          47
         8: aload_0
         9: getfield      #466                // Field puzzleslib$damageAmount:Lfuzs/puzzleslib/api/event/v1/data/DefaultedFloat;
        12: ldc_w         #479                // String damage amount is null
        15: invokestatic  #303                // Method java/util/Objects.requireNonNull:(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;
        18: pop
        19: aload_0
        20: getfield      #466                // Field puzzleslib$damageAmount:Lfuzs/puzzleslib/api/event/v1/data/DefaultedFloat;
        23: invokeinterface #482,  1          // InterfaceMethod fuzs/puzzleslib/api/event/v1/data/DefaultedFloat.getAsOptionalFloat:()Ljava/util/Optional;
        28: fload_1
        29: invokestatic  #488                // Method java/lang/Float.valueOf:(F)Ljava/lang/Float;
        32: invokevirtual #323                // Method java/util/Optional.orElse:(Ljava/lang/Object;)Ljava/lang/Object;
        35: checkcast     #484                // class java/lang/Float
        38: invokevirtual #492                // Method java/lang/Float.floatValue:()F
        41: fstore_1
        42: aload_0
        43: aconst_null
        44: putfield      #466                // Field puzzleslib$damageAmount:Lfuzs/puzzleslib/api/event/v1/data/DefaultedFloat;
        47: fload_1
        48: freturn
      StackMapTable: number_of_entries = 1
        frame_type = 47 /* same */
      LineNumberTable:
        line 234: 0
        line 235: 8
        line 236: 19
        line 237: 42
        line 239: 47
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0      49     0  this   Lfuzs/puzzleslib/mixin/LivingEntityFabricMixin;
            0      49     1 damageAmount   F
            0      49     2 damageSource   Lnet/minecraft/class_1282;
    RuntimeVisibleAnnotations:
      0: #289(#109=[s#453],#110=@#111(#7=s#112),#291=I#292,#477=Z#114)
        org.spongepowered.asm.mixin.injection.ModifyVariable(
          method=["actuallyHurt"]
          at=@org.spongepowered.asm.mixin.injection.At(
            value="HEAD"
          )
          ordinal=0
          argsOnly=true
        )
    MethodParameters:
      Name                           Flags
      damageAmount
      damageSource


```

### 1.6 两处注入注解原文

```text
    RuntimeVisibleAnnotations:
      0: #108(#109=[s#453],#110=[@#111(#7=s#112)],#113=Z#114)
        org.spongepowered.asm.mixin.injection.Inject(
          method=["actuallyHurt"]
          at=[@org.spongepowered.asm.mixin.injection.At(
            value="HEAD"
          )]
          cancellable=true
        )
    MethodParameters:
      Name                           Flags
      damageSource
      damageAmount
      callback

    RuntimeVisibleAnnotations:
      0: #289(#109=[s#453],#110=@#111(#7=s#112),#291=I#292,#477=Z#114)
        org.spongepowered.asm.mixin.injection.ModifyVariable(
          method=["actuallyHurt"]
          at=@org.spongepowered.asm.mixin.injection.At(
            value="HEAD"
          )
          ordinal=0
          argsOnly=true
        )
    MethodParameters:
      Name                           Flags
      damageAmount
      damageSource


```
⇒ 与 `LivingEntity#actuallyHurt` 的偏移对照：注入点在 `HEAD`（偏移 0 之前），护甲在 12、魔抗在 19、吸收在 25-45。**事件载荷（以及回调写回的改值）一律是护甲与吸收之前的量**。

## 2. 逐处前后对照

> 说明：行号为 B27 落盘后的实测行号；「原文」取自改前基线快照（`.dsh-tmp-b27/baseline/`），「改后」取自当前工作树，两者均由脚本从磁盘原文抽取，未手工转抄。

### 2.1 event/ChipDamageHandler.java · 类 javadoc（事件时机 + 源码依据）

文件：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ChipDamageHandler.java`

**原文（B27 前，9 行）**

```java
 * 统一在 {@link LivingDamageEvent} 处理:该阶段护甲/药水减免已结算、生命扣减尚未发生,
 * 可直接改写最终伤害值(设置 0 即完全无效化)。
 *
 * <p><b>安全气囊对「无视无敌」的伤害源同样生效(2026-09-15 用户裁决)</b>:不再按
 * {@code DamageTypeTags.BYPASSES_INVULNERABILITY} 排除。原版源码依据:本事件由
 * {@code ForgeHooks.onLivingDamage} 在 {@code LivingEntity#actuallyHurt} 内派发,
 * 且 {@code actuallyHurt} 的 {@code isInvulnerableTo} 前置判定对 bypasses_invulnerability
 * 的伤害源为假 → 事件照常派发;把最终伤害改成 0 后 {@code if (f1 != 0.0F)} 分支跳过扣血,
 * 与普通致命伤完全等价。故 {@code /kill}(generic_kill)与其它模组的真伤都会在气囊前止步。
```

**改后（11 行）**

```java
 * 统一在 {@link LivingDamageEvent} 处理:生命扣减尚未发生,可直接改写伤害值(设置 0 即完全无效化);
 * <b>但本线该事件在护甲/吸收之前派发</b>(fabric 锚点见下),载荷是 {@code actuallyHurt} 的**原始入参**,
 * 不是「护甲与黄心结算之后」的最终值 —— 以「最终伤害」为口径的判定都要按此重看。
 *
 * <p><b>安全气囊对「无视无敌」的伤害源同样生效(2026-09-15 用户裁决)</b>:不再按
 * {@code DamageTypeTags.BYPASSES_INVULNERABILITY} 排除。本线依据:事件由 Puzzles Lib 8.1.33 的
 * {@code LivingEntityFabricMixin#actuallyHurt}({@code @Inject(method="actuallyHurt", at=@At("HEAD"))})
 * 派发,该处理器先判 {@code isInvulnerableTo}(intermediary {@code method_5679})再派发,而它对
 * bypasses_invulnerability 的伤害源为假 ⇒ 事件照常派发;改 0 后 {@code actuallyHurt} 在偏移
 * 107-113({@code fload_2};{@code fconst_0};{@code fcmpl};{@code ifne 114} 不成立即 {@code return})跳过扣血,
 * 与普通致命伤等价,故 {@code /kill} 与其它模组的真伤都会在气囊前止步。
```

**理由**：原文有两个错：(1) 把本事件说成「护甲/药水减免已结算」的阶段；(2) 把原版源码依据写成 Forge 的 ForgeHooks.onLivingDamage 在 LivingEntity#actuallyHurt 内派发 —— 该锚点只对 forge-1.20.1 线成立，在本线是误导源。新文按实测改正为「生命扣减尚未发生、载荷是 actuallyHurt 的原始入参」，并换成本线真实锚点：Puzzles Lib 8.1.33 的 LivingEntityFabricMixin#actuallyHurt 以 @Inject(at=@At(HEAD)) 派发。原文「气囊对 bypasses_invulnerability 同样生效」的结论保留，依据改写为本线字节码：处理器先判 isInvulnerableTo（intermediary method_5679）、该判定对 bypasses_invulnerability 为假，且改 0 后 actuallyHurt 在偏移 107-113 处 fcmpl/ifne 不成立直接 return，跳过扣血。

**落盘自检**：改后文本在当前文件中按行连续命中 = 是

### 2.2 event/ChipDamageHandler.java · A6 口径注释（「天然达标」）

文件：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ChipDamageHandler.java`

**原文（B27 前，4 行）**

```java
        // A6 口径(致命判定按**吸收(黄心)之后**)在本版本**天然达标**,无需换算:
        // 1.20.1 的 LivingDamageEvent 由 ForgeHooks.onLivingDamage 在
        // forge-1.20.1-47.4.10 LivingEntity#actuallyHurt L1680 派发,晚于 L1669-1670 的吸收结算,
        // 传入的 damage 已是"吸收后仍会扣的生命值"⇒ 直接与生命值比较即为同一口径。
```

**改后（5 行）**

```java
        // A6 口径(致命判定按**吸收(黄心)之后**)在本线**并不天然成立**:下面的 damage 是
        // actuallyHurt 的原始入参 —— 事件由 Puzzles 的 LivingEntityFabricMixin#actuallyHurt
        // (@Inject(method="actuallyHurt", at=@At("HEAD")))在偏移 0 派发,而护甲(偏移 12
        // getDamageAfterArmorAbsorb)、魔抗(19 getDamageAfterMagicAbsorb)、吸收(25-45)全在其后
        // ⇒ 此处与生命值比较的是护甲/吸收**之前**的原始值,本线不做换算。
```

**理由**：原文断言「A6 口径（致命判定按吸收之后）在本版本天然达标」，并用 forge-1.20.1-47.4.10 的 L1669-1670 / L1680 行号作证。本线 HEAD 注入在 actuallyHurt 偏移 0，吸收结算在偏移 25-45，故「天然达标」不成立；damage 是原始入参，与生命值比较并非同一口径。新文据实标为「并不天然成立」并给出偏移次序，同时说明本文件内的使用点为何仍安全（见 ChipDamageHandler 对 0 的处理）。

**落盘自检**：改后文本在当前文件中按行连续命中 = 是

### 2.3 event/DamageEffectCardHandler.java · 事件时机（AGENTS 映射表引用）

文件：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/DamageEffectCardHandler.java`

**原文（B27 前，9 行）**

```java
    // ⚠️ 事件时机必须与 1.21.1 对齐(2026-09-15 用户裁决修补 KI-5「电击手套 AOE 基准跨版本」):
    // 1.21.1 挂在 LivingDamageEvent.Pre(**护甲/附魔减免之后**),而 1.20.1 原先挂 LivingHurtEvent
    // (**护甲前**,LivingEntity.java:1665 早于 :1667-1668 的护甲减免)⇒ 同一发法伤在 1.20.1 上
    // 会以「护甲前原始值」为基准做 3 格 AOE 波及,带甲目标周围多打一截。
    // 按 AGENTS.md「伤害事件映射」的既定口径(LivingDamageEvent.Pre ↔ 1.20.1 LivingDamageEvent,
    // 即**护甲之后**),此处改挂 LivingDamageEvent;`SpellDamageContext.event` 的类型随之同步。
    // 残余(平台固有,已在 AGENTS.md 记录):1.20.1 的 LivingDamageEvent 在**吸收结算之后**
    // (吸收 :1669-1670 早于派发 :1680),1.21.1 的 Pre 在**吸收之前**(:1789 早于 :1790-1792),
    // 故目标带吸收(黄心)时两版本基准仍会有差异——普通目标无吸收,实际影响可忽略。
```

**改后（9 行）**

```java
    // ⚠️ 事件时机(B27 订正本线事实;2026-09-15 用户裁决修补 KI-5「电击手套 AOE 基准跨版本」):
    // 本处挂在 LivingDamageEvent,与 1.21.1 的 LivingDamageEvent.Pre 同名对应。但本次改挂
    // **不会**让本线拿到「护甲之后」的值:该事件由 Puzzles Lib 8.1.33 的
    // fuzs.puzzleslib.mixin.LivingEntityFabricMixin#actuallyHurt(@Inject(at=@At("HEAD"))) 在
    // actuallyHurt 偏移 0 派发,而护甲(偏移 12 getDamageAfterArmorAbsorb)、魔抗(19
    // getDamageAfterMagicAbsorb)、吸收(25-45)全在方法体内、注入点之后 ⇒ 载荷仍是护甲与吸收
    // **之前**的原始入参。桥接处见 platform/PuzzlesBridges.java:99-110。
    // 残余(平台固有,已登记 B27 报告):本线基准比 1.21.1 Pre 还早一层(护甲前),
    // 带甲或带吸收(黄心)的目标都会有差;本处只改挂点,不引入换算。
```

**理由**：原文按 AGENTS 映射表断言「1.20.1 改挂 LivingDamageEvent 即护甲之后」，并称残余问题是「吸收结算（:1669-1670）早于派发（:1680）」。这两条都是 Forge 的字节码事实，本线不成立。新文改为：改挂不会拿到护甲之后的值，并指向本线桥接处 platform/PuzzlesBridges.java:99-110（LivingHurtCallback → LivingDamageEvent）。

**落盘自检**：改后文本在当前文件中按行连续命中 = 是

### 2.4 event/DamageEffectCardHandler.java · 伤害数字聚合器取值口径

文件：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/DamageEffectCardHandler.java`

**原文（B27 前，3 行）**

```java
        // 2026-09-26:本处原有的「HUD 数显 = getAmount() + bonus」直发已移除 —— 显示值**同源**,
        // 但改由 combat/DamageNumberAggregator 在伤害管线终值(本线 = LivingDamageEvent#getAmount(),
        // 已是吸收之后的最终值)上取,并与基础伤害累加成一个法伤总值。
```

**改后（3 行）**

```java
        // 2026-09-26:本处原有的「HUD 数显 = getAmount() + bonus」直发已移除 —— 显示值**同源**,
        // 但改由 combat/DamageNumberAggregator 取(本线 = LivingDamageEvent#getAmount();该值在
        // 护甲/吸收**之前**,不是「吸收之后的最终值」,见上方挂点说明)并与基础伤害累加。
```

**理由**：原文称聚合器取的是「伤害管线终值（本线 = LivingDamageEvent#getAmount()，已是吸收之后的最终值）」。本线该值在护甲/吸收之前，故订正为「不是吸收之后的最终值」，并保留聚合器仍可用的结论（它只做数值累计与展示，不依赖护甲是否已结算）。

**落盘自检**：改后文本在当前文件中按行连续命中 = 是

### 2.5 item/chip/WhetstoneChipItem.java · 入参契约

文件：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/WhetstoneChipItem.java`

**原文（B27 前，3 行）**

```java
     * <p>⚠️ 入参 {@code damage} 必须是**「吸收(黄心)之后仍会扣的生命」**(2026-09-24 用户裁决):
     * 1.20.1 的 {@code LivingDamageEvent} 天然派发于吸收之后;1.21.1 / 26.1.2 的 {@code Pre} 在吸收**之前**,
     * 由 {@code ChipDamageHandler} 先换算再传入(否则带黄心时会被**过度削减**)。
```

**改后（6 行）**

```java
     * <p>⚠️ 本方法约定入参 {@code damage} 是**「护甲与吸收(黄心)之后仍会扣的生命」**(2026-09-24 用户裁决):
     * 该约定源自 forge-1.20.1 线(该线事件在吸收之后派发);1.21.1 / 26.1.2 的 {@code Pre} 在吸收**之前**,
     * 由 {@code ChipDamageHandler} 先换算再传入(否则带黄心时会被**过度削减**)。但**本线(fabric-1.20.1)不满足该约定**:
     * 事件由 Puzzles 的 {@code LivingEntityFabricMixin#actuallyHurt}({@code @Inject(at=@At("HEAD"))})在偏移 0 派发,
     * 护甲(偏移 12 {@code getDamageAfterArmorAbsorb})与吸收(25-45)全在其后;唯一调用点
     * {@code ChipDamageHandler} 直接传入 {@code event.getAmount()},未做任何换算 ⇒ 实际拿到的是原始入参。
```

**理由**：原文把「入参必须是吸收之后仍会扣的生命值」当作 1.20.1 的天然事实。该约定来自 forge-1.20.1 线；本线不满足，且全仓唯一调用点 event/ChipDamageHandler.java 直传 event.getAmount()、未做任何换算（grep 确认唯一调用点）。新文据实说明，避免后续有人按错误前提改这里的语义。

**落盘自检**：改后文本在当前文件中按行连续命中 = 是

### 2.6 item/sign/SherrySignItem.java · 挂点时机

文件：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/SherrySignItem.java`

**原文（B27 前，3 行）**

```java
     * <p>挂点 = {@code LivingDamageEvent}(护甲/附魔减免之后),对应 1.21.1 的
     * {@code LivingDamageEvent.Pre};残余平台差异(1.20.1 在吸收结算之后派发)对本判定无影响
     * —— 这里只读实体与伤害源,不看伤害数值。
```

**改后（4 行）**

```java
     * <p>挂点 = {@code LivingDamageEvent},与 1.21.1 的 {@code LivingDamageEvent.Pre} 同名对应;
     * 本线该事件由 Puzzles 的 {@code LivingEntityFabricMixin#actuallyHurt}({@code @Inject(at=@At("HEAD"))})
     * 在护甲(偏移 12)/魔抗(19)/吸收(25-45)**之前**派发,载荷是原始入参而不是「护甲之后」的值
     * —— 对本判定无影响:这里只读实体与伤害源,不看伤害数值。
```

**理由**：原文称挂点是「LivingDamageEvent（护甲/附魔减免之后）」且「1.20.1 在吸收结算之后派发」。时机描述按实测订正为 HEAD 注入、护甲/魔抗/吸收之前。原文结论句「对本判定无影响——这里只读实体与伤害源，不看伤害数值」经复核成立，予以保留。

**落盘自检**：改后文本在当前文件中按行连续命中 = 是

## 3. 「判定无需改」与唯一一处连带订正

按要求先判定结论是否本来就成立。本批只发现 1 处结论正确、不该按新口径重写：`event/ChipDamageHandler.java` 中批次 D-10 留下的「本线平台注」。

- 该注的技术内容（注入点在 `actuallyHurt` 的**方法入口**、载荷在护甲/抗性/吸收**之前**、偏移 0-54、「致命判定天然按吸收之后」在本线不成立）与本报告第 1 节 javap 取证逐条一致，且未引用任何 Forge 锚点 ⇒ **判定无需按新口径改写**。
- 但它开头的从句「以上是 forge 蓝本的事实。」在 2.2 处把上文订正为本线事实之后会自相矛盾（上文已不再是 forge 蓝本），属本轮连带引入的陈述漂移 ⇒ 只删掉这一行里的该从句，其余一字不动。这是本批第 7 处改动，同样注释-only。

**改前（基线快照，B27 前）** `.dsh-tmp-b27/baseline/ChipDamageHandler.java:85-96`

```java
        // 安全气囊:致命伤害(伤害 ≥ 当前血量)时消耗 6 点充能使本次伤害无效。
        // 不排除「无视无敌」(bypasses_invulnerability)的伤害源:气囊必须始终有效(见类注释)。
        // A6 口径(致命判定按**吸收(黄心)之后**)在本版本**天然达标**,无需换算:
        // 1.20.1 的 LivingDamageEvent 由 ForgeHooks.onLivingDamage 在
        // forge-1.20.1-47.4.10 LivingEntity#actuallyHurt L1680 派发,晚于 L1669-1670 的吸收结算,
        // 传入的 damage 已是"吸收后仍会扣的生命值"⇒ 直接与生命值比较即为同一口径。
        // ⚠️ 本线平台注(批次 D-10 取证):以上是 forge 蓝本的事实。本线该事件由 Puzzles 的 LIVING_HURT
        //    回调桥接(platform/PuzzlesBridges.java:100-112),注入点在 actuallyHurt 的**方法入口**,
        //    载荷是护甲 / 抗性 / 吸收**之前**的原始入参(字节码:fuzs.puzzleslib.mixin.LivingEntityFabricMixin
        //    的 actuallyHurt 处理器先判 isInvulnerableTo 再派发,偏移 0-54)⇒「致命判定天然按吸收之后」
        //    在本线**并不成立**;此处沿用本线既有口径(与骰战两条写终值路径同一口径),
        //    该平台差异的完整取证与后续处理建议登记在 B20B 报告。
```

**改后（当前工作树）** `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ChipDamageHandler.java:87-99`

```java
        // 安全气囊:致命伤害(伤害 ≥ 当前血量)时消耗 6 点充能使本次伤害无效。
        // 不排除「无视无敌」(bypasses_invulnerability)的伤害源:气囊必须始终有效(见类注释)。
        // A6 口径(致命判定按**吸收(黄心)之后**)在本线**并不天然成立**:下面的 damage 是
        // actuallyHurt 的原始入参 —— 事件由 Puzzles 的 LivingEntityFabricMixin#actuallyHurt
        // (@Inject(method="actuallyHurt", at=@At("HEAD")))在偏移 0 派发,而护甲(偏移 12
        // getDamageAfterArmorAbsorb)、魔抗(19 getDamageAfterMagicAbsorb)、吸收(25-45)全在其后
        // ⇒ 此处与生命值比较的是护甲/吸收**之前**的原始值,本线不做换算。
        // ⚠️ 本线平台注(批次 D-10 取证):本线该事件由 Puzzles 的 LIVING_HURT
        //    回调桥接(platform/PuzzlesBridges.java:100-112),注入点在 actuallyHurt 的**方法入口**,
        //    载荷是护甲 / 抗性 / 吸收**之前**的原始入参(字节码:fuzs.puzzleslib.mixin.LivingEntityFabricMixin
        //    的 actuallyHurt 处理器先判 isInvulnerableTo 再派发,偏移 0-54)⇒「致命判定天然按吸收之后」
        //    在本线**并不成立**;此处沿用本线既有口径(与骰战两条写终值路径同一口径),
        //    该平台差异的完整取证与后续处理建议登记在 B20B 报告。
```

**理由**：技术结论不动。基线首句「以上是 forge 蓝本的事实。」在 2.2 处订正后已不成立（上文改成了本线事实），删掉该从句即可与上文自洽；「该平台差异的完整取证与后续处理建议登记在 B20B 报告」等待办指引原样保留。

## 4. 验收原始输出

### 4.1 a) 编译噪声门禁 `python scripts/__javac_check.py`

```text
EXIT 1 ERRORS 171 jars 685 srcs 525
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  164  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 984

```
`=== REAL errors ===` 与 `=== REAL by file ===` 两节均为空；ERRORS 171 = 164 NOISE_AW + 7 NOISE_IFACE_INJECTION，与基线一致 ⇒ PASS。

### 4.2 b) 差异与「注释 only」判据

**b-1 全量 numstat（原始输出，含前序批次 B1..B26 已落盘的真实逻辑改动）**

```text
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
40	11	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ChipDamageHandler.java
11	11	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/DamageEffectCardHandler.java
41	2	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModEffectEvents.java
74	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java
78	15	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/PlayerTickEvents.java
54	5	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/RailgunStrikeScheduler.java
34	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/TemporaryCardEvents.java
41	8	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/WeirdDiceHandler.java
33	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/init/ModCreativeTabs.java
40	6	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/ChargeManager.java
16	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/HealingManager.java
168	16	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java
30	6	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/RenShieldManager.java
10	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/BaseEffectCardItem.java
18	2	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/ExclusiveCardUtil.java
15	5	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/FateGuidanceCardItem.java
1	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/RandomCardHandler.java
153	7	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/TemporaryCardUtil.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/UnwaveringCardItem.java
2	2	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/AdrenalineChipItem.java
12	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BaseChipItem.java
3	2	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BookmarkChipItem.java
2	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/CurrentCoreChipItem.java
137	36	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/ElectricGloveChipItem.java
8	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/MemberRecommendationChipItem.java
8	7	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/MotoHelmetChipItem.java
58	4	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/PerpetualMotionChipItem.java
3	3	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/PiercingGunChipItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/PrimordialCoreChipItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/RevengeHalberdChipItem.java
15	0	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/VitaminPillChipItem.java
6	5	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/WarpEngineChipItem.java
6	3	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/WhetstoneChipItem.java
8	5	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/DiceCurioItem.java
13	5	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/DiceTier.java
5	4	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/NetherStarDiceItem.java
5	4	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/ObsidianDiceItem.java
66	3	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BaseSignItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BonnieSignItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/HaiqingSignItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/JasmineSignItem.java
5	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/LuluSignItem.java
4	5	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/MegasSignItem.java
5	6	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/MosesSignItem.java
4	3	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/NancyLuSignItem.java
3	3	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/NardisSignItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/PadmanSignItem.java
86	71	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/ParunanSignItem.java
1	1	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/RenSignItem.java
19	8	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/SherrySignItem.java
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
6	10	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/EmeraldDiceTrade.java

```
**b-2 本批 4 个文件的 numstat（原始输出）**

```text
40	11	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ChipDamageHandler.java
11	11	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/DamageEffectCardHandler.java
6	3	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/WhetstoneChipItem.java
19	8	fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/SherrySignItem.java

```
说明：`event/DamageEffectCardHandler.java`（11/11）与 `item/chip/WhetstoneChipItem.java`（6/3）在前序批次**未被改过**，其 numstat 恰等于本批增量，可与 b-3 交叉印证；`ChipDamageHandler.java`（40/11）与 `SherrySignItem.java`（19/8）同时含前序批次的逻辑改动，**不能**用全量 diff 判「注释 only」，必须用 b-3 的基线差分。

**b-3 可复核判据：基线与工作树的 B27 增量（原始输出）**

判据命令（对 4 个文件各跑一次，基线 = 本批动手前从磁盘复制的副本）：
```bash
git --no-pager diff --no-index -U0 .dsh-tmp-b27/baseline/<file> <file>
```
判据程序逐行分类：`+`/`-` 行去掉前导空白后，若以 `*`、`//`、`/*`、`*/` 开头（即位于注释块内），计入「注释行」；否则计入「非注释行」。同时把两版的「去注释后的代码行序列」逐行比对，必须完全相同。

```text
ChipDamageHandler.java           +15  -12   added-not-comment=0  removed-not-comment=0  code-lines-identical=True
   added lines:
     +  * 统一在 {@link LivingDamageEvent} 处理:生命扣减尚未发生,可直接改写伤害值(设置 0 即完全无效化);
     +  * <b>但本线该事件在护甲/吸收之前派发</b>(fabric 锚点见下),载荷是 {@code actuallyHurt} 的**原始入参**,
     +  * 不是「护甲与黄心结算之后」的最终值 —— 以「最终伤害」为口径的判定都要按此重看。
     +  * {@code DamageTypeTags.BYPASSES_INVULNERABILITY} 排除。本线依据:事件由 Puzzles Lib 8.1.33 的
     +  * {@code LivingEntityFabricMixin#actuallyHurt}({@code @Inject(method="actuallyHurt", at=@At("HEAD"))})
     +  * 派发,该处理器先判 {@code isInvulnerableTo}(intermediary {@code method_5679})再派发,而它对
     +  * bypasses_invulnerability 的伤害源为假 ⇒ 事件照常派发;改 0 后 {@code actuallyHurt} 在偏移
     +  * 107-113({@code fload_2};{@code fconst_0};{@code fcmpl};{@code ifne 114} 不成立即 {@code return})跳过扣血,
     +  * 与普通致命伤等价,故 {@code /kill} 与其它模组的真伤都会在气囊前止步。
     +         // A6 口径(致命判定按**吸收(黄心)之后**)在本线**并不天然成立**:下面的 damage 是
     +         // actuallyHurt 的原始入参 —— 事件由 Puzzles 的 LivingEntityFabricMixin#actuallyHurt
     +         // (@Inject(method="actuallyHurt", at=@At("HEAD")))在偏移 0 派发,而护甲(偏移 12
     +         // getDamageAfterArmorAbsorb)、魔抗(19 getDamageAfterMagicAbsorb)、吸收(25-45)全在其后
     +         // ⇒ 此处与生命值比较的是护甲/吸收**之前**的原始值,本线不做换算。
     +         // ⚠️ 本线平台注(批次 D-10 取证):本线该事件由 Puzzles 的 LIVING_HURT
DamageEffectCardHandler.java     +11  -11   added-not-comment=0  removed-not-comment=0  code-lines-identical=True
   added lines:
     +     // ⚠️ 事件时机(B27 订正本线事实;2026-09-15 用户裁决修补 KI-5「电击手套 AOE 基准跨版本」):
     +     // 本处挂在 LivingDamageEvent,与 1.21.1 的 LivingDamageEvent.Pre 同名对应。但本次改挂
     +     // **不会**让本线拿到「护甲之后」的值:该事件由 Puzzles Lib 8.1.33 的
     +     // fuzs.puzzleslib.mixin.LivingEntityFabricMixin#actuallyHurt(@Inject(at=@At("HEAD"))) 在
     +     // actuallyHurt 偏移 0 派发,而护甲(偏移 12 getDamageAfterArmorAbsorb)、魔抗(19
     +     // getDamageAfterMagicAbsorb)、吸收(25-45)全在方法体内、注入点之后 ⇒ 载荷仍是护甲与吸收
     +     // **之前**的原始入参。桥接处见 platform/PuzzlesBridges.java:99-110。
     +     // 残余(平台固有,已登记 B27 报告):本线基准比 1.21.1 Pre 还早一层(护甲前),
     +     // 带甲或带吸收(黄心)的目标都会有差;本处只改挂点,不引入换算。
     +         // 但改由 combat/DamageNumberAggregator 取(本线 = LivingDamageEvent#getAmount();该值在
     +         // 护甲/吸收**之前**,不是「吸收之后的最终值」,见上方挂点说明)并与基础伤害累加。
WhetstoneChipItem.java           +6   -3    added-not-comment=0  removed-not-comment=0  code-lines-identical=True
   added lines:
     +      * <p>⚠️ 本方法约定入参 {@code damage} 是**「护甲与吸收(黄心)之后仍会扣的生命」**(2026-09-24 用户裁决):
     +      * 该约定源自 forge-1.20.1 线(该线事件在吸收之后派发);1.21.1 / 26.1.2 的 {@code Pre} 在吸收**之前**,
     +      * 由 {@code ChipDamageHandler} 先换算再传入(否则带黄心时会被**过度削减**)。但**本线(fabric-1.20.1)不满足该约定**:
     +      * 事件由 Puzzles 的 {@code LivingEntityFabricMixin#actuallyHurt}({@code @Inject(at=@At("HEAD"))})在偏移 0 派发,
     +      * 护甲(偏移 12 {@code getDamageAfterArmorAbsorb})与吸收(25-45)全在其后;唯一调用点
     +      * {@code ChipDamageHandler} 直接传入 {@code event.getAmount()},未做任何换算 ⇒ 实际拿到的是原始入参。
SherrySignItem.java              +4   -3    added-not-comment=0  removed-not-comment=0  code-lines-identical=True
   added lines:
     +      * <p>挂点 = {@code LivingDamageEvent},与 1.21.1 的 {@code LivingDamageEvent.Pre} 同名对应;
     +      * 本线该事件由 Puzzles 的 {@code LivingEntityFabricMixin#actuallyHurt}({@code @Inject(at=@At("HEAD"))})
     +      * 在护甲(偏移 12)/魔抗(19)/吸收(25-45)**之前**派发,载荷是原始入参而不是「护甲之后」的值
     +      * —— 对本判定无影响:这里只读实体与伤害源,不看伤害数值。

RESULT: PASS - all B27 additions are comment lines and no code line changed

```
⇒ 4 个文件 B27 增量合计 +36/-29（ChipDamageHandler +15/-12、DamageEffectCardHandler +11/-11、WhetstoneChipItem +6/-3、SherrySignItem +4/-3），**非注释行增删 0**，去注释后的代码行序列前后完全一致 ⇒ 注释-only 判据 PASS。

**b-4 行尾自检（CRLF + UTF-8 无 BOM + bareLF=0）**

```text
ChipDamageHandler          bytes=10772   crlf=155   bareLF=0   bom=False endsCRLF=True
DamageEffectCardHandler    bytes=7742    crlf=115   bareLF=0   bom=False endsCRLF=True
WhetstoneChipItem          bytes=6936    crlf=127   bareLF=0   bom=False endsCRLF=True
SherrySignItem             bytes=31986   crlf=532   bareLF=0   bom=False endsCRLF=True

```
⇒ 4 个文件全部 CRLF、`bareLF=0`、无 BOM、以 CRLF 结尾 ⇒ PASS。

### 4.3 c) 资源闭环门禁 `python tools/verify_fabric_assets.py`

```text
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

```
⇒ 8 项闭环全部通过（末行原文 `=== PASS: 8 项闭环全部通过 ===`）。运行时会提示 `build/libs` 里的 jar 版本与 `mod_version` 不符，这是前序构建产物过期的既有现象，脚本按预期改扫源码资源目录，不影响判定。

### 4.4 d) 语言键集 `python tools/check_lang_sync.py --line fabric-1.20.1`

```text
[OK]   fabric-1.20.1: en_us=1094 键，3 份语言键集一致

```
⇒ `[OK]`。注意：该脚本把中文打到 stdout，在 GBK 控制台下会乱码，复跑时需带 `PYTHONIOENCODING=utf-8`（本次即如此运行）。

## 5. 范围外发现（只登记，本批未改）

1. `docs/agent/AGENTS-full.md:798` 的「伤害事件映射」把 `LivingDamageEvent.Pre` 映射到 1.20.1 的 `LivingDamageEvent` 并注明「护甲+附魔减免之后」「1.20.1 在吸收之后派发」。这套口径来自 forge-1.20.1 线，对 fabric-1.20.1 线不成立（第 1 节已取证）。它是本轮多处错误注释的源头之一，建议单独批次订正规则文档。
2. `platform/PuzzlesBridges.java:43` 的映射表在描述 `LivingHurtCallback` 时写「对应 Forge 的 LivingDamageEvent」并提到 Forge 的派发位置。作为与 Forge 的对照说明可以接受，但若被当成「本线时机」的依据仍会读出错误结论。本批未改（同文件 :61-66 已明确写了 MutableFloat 是 `actuallyHurt` 的入参，整段语义尚可自洽）。

## 6. 施工记录

- 工作树在动手前已含前序批次 B1..B26 的改动（`git diff --numstat -- fabric-1.20.1/src/main/java` 当前列出 82 个文件），其中 `ChipDamageHandler.java`、`SherrySignItem.java` 也有前序批次的真实逻辑改动。为能证明本批「注释 only」，动手前先把这 4 个文件复制到 `.dsh-tmp-b27/baseline/`，收尾再用基线差分取证（4.2 的 b-3），避免把前序批次的代码改动混进判据。
- 4 个文件的改前/改后字节、行数、行尾状态由脚本逐一比对；写盘采用「整行块替换 + 每块断言恰好命中 1 次」的字节级替换，未整文件重写，避免行尾与空白漂移。
- 期间 `python` 以 `wb` 打开 `event/DamageEffectCardHandler.java`、`item/chip/WhetstoneChipItem.java`、`item/sign/SherrySignItem.java` 会抛 `PermissionError`（ACL 为 `Authenticated Users (I)(M)`、`attrib` 无只读位、`os.replace`/`utime` 亦被拒）；改用 DSH 的文件编辑工具对同一路径写入即可成功，三处均如此落盘（`ChipDamageHandler.java` 两种方式都可写）。属环境 ACL 现象，不影响产物。
- 编译门禁口径：`scripts/__javac_check.py` 以 Loom 的 AW/接口注入补丁为前提，171 条 ERRORS 全部落在 NOISE_AW / NOISE_IFACE_INJECTION 两桶，本批改动前后数量与分桶完全一致。

- `.dsh-tmp-b27/` 是本批临时目录（基线副本 + 取证脚本 + 门禁输出），收尾时已删除；改前的完整原文已逐块抄录在本报告第 2 节与第 3 节，可与工作树逐行对照复核。

— 报告结束 —
