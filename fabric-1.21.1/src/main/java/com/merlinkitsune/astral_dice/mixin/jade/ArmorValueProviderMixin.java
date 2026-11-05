package com.merlinkitsune.astral_dice.mixin.jade;

import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 把 Jade 护甲条读到的数值从**原版护甲值**换成**本模组防御力**（Fabric 1.21.1 线，Jade 15.10.6）。
 *
 * <h2>落点：与 1.20.1 线**不同**的供应器</h2>
 * 本线钉的是 Jade {@code 15.10.6+fabric}（Modrinth {@code adpNvTZS}）。该构建里
 * {@code snownee.jade.addon.vanilla.EntityArmorProvider} **已经不存在**（{@code unzip -l} 实证：
 * {@code MISSING snownee/jade/addon/vanilla/EntityArmorProvider}），护甲行并入
 * {@code snownee.jade.addon.vanilla.EntityHealthAndArmorProvider}（枚举，实现客户端
 * {@code IEntityComponentProvider}）—— 与 neoforge-1.21.1 线同一个类。
 * <p>本类按**实际参与编译的 remap jar**反编译确证：
 * {@code fabric-1.21.1/build/loom-cache/remapped_working/remapped.maven.modrinth-jade-37f46004-adpNvTZS.jar}
 * （sha256 前缀 {@code 9d10c726968566c9}）。
 *
 * <h2>该 provider 内 getArmorValue() 有**两处**调用</h2>
 * {@code javap -c -p} 实证（{@code appendTooltip} 方法体）：
 * <pre>
 *  111: invokevirtual LivingEntity.getArmorValue:()I  → ifle 155   // ① 门控：&gt; 0 才画护甲行
 *  117: new ArmorElement
 *  123: invokevirtual LivingEntity.getArmorValue:()I              // ② 构造入参
 *  126: i2f; 127: invokespecial ArmorElement.&lt;init&gt;:(F)V
 * </pre>
 * 单条 {@code @Redirect} 同时接管①②：sponge-mixin 0.8.7 的 {@code BeforeInvoke.matchesOrdinal}
 * 为 {@code ordinal == -1 || ordinal == ordinal}，本注解不写 {@code ordinal} ⇒ 匹配该 INVOKE 的
 * **全部**出现点。结果门控与数值同源，不会出现「按原版护甲判定为 0 直接不显示、而实际防御力 &gt; 0」的错配。
 * <p>对**非玩家**本处理器原样返回 {@code entity.getArmorValue()} ⇒ 两处调用与重定向前逐字节等价，
 * 生物显示行为完全不变；只有玩家被换成防御力（下限 2，{@code DiceCombatModifiers#defensePowerOf}）
 * ⇒ 玩家护甲行恒定显示。
 *
 * <h2>{@code method} 为什么写完整描述符</h2>
 * 本类另有一个**合成桥接**重载 {@code appendTooltip(ITooltip, Accessor, IPluginConfig)V}
 * （{@code javap} 实证：方法体只有一句 {@code invokevirtual appendTooltip:(...EntityAccessor...)V}）。
 * 裸方法名会命中两个方法；写完整描述符只命中带 {@code EntityAccessor} 的那个。
 * 该描述符里**只有 Jade 自己的类**（{@code ITooltip} / {@code EntityAccessor} / {@code IPluginConfig}），
 * Jade 不混淆 ⇒ 生产环境字符串完全一致，可安全写死。
 *
 * <h2>处理器为什么是 static</h2>
 * 与 neoforge-1.21.1 线保持一致。@Redirect 的静态性判据有两道，**都不禁止 static 处理器打实例方法**：
 * <ol>
 *   <li>{@code InvokeInjector.checkTarget(Target)} 里那条 {@code checkTargetModifiers(target, true)}
 *       对 @Redirect **不生效** —— {@code RedirectInjector} 把它覆写成空实现
 *       （注释原文：Overridden so we can do this check later in a location-aware manner）。</li>
 *   <li>真正的收尾检查在 {@code RedirectInjector#inject} → {@code checkTargetForNode(...)} →
 *       {@code checkTargetModifiers(target, false)}；{@code exactMatch == false} 时短路返回，
 *       静态性不参与判定（该分支只服务于「构造器 super() 之前的注入必须 static」）。</li>
 * </ol>
 * <p>注意 handler 的**形参表与处理器是否 static 无关**：{@code RedirectedInvokeData.handlerArgs}
 * 由被重定向的调用决定 —— 此处是 {@code INVOKEVIRTUAL LivingEntity.getArmorValue()I}，故
 * {@code handlerArgs = concat(ownerType, targetArgs) = (LivingEntity)}，单个形参就是该调用的接收者。
 *
 * <h2>为什么不写 {@code remap = false}</h2>
 * {@code @At} 的 target 里含**原版成员**（{@code LivingEntity.getArmorValue}），Fabric 生产环境走
 * intermediary ⇒ **必须**经重映射才能命中 {@code method_6096}。Loom 1.17 的非 legacy mixin AP
 * 在 {@code remapJar} 阶段重映射注解值，本线不存在 forge 线那条「Unable to locate obfuscation mapping」
 * 编译门禁。
 *
 * <h2>为什么用字符串 {@code targets} 而不是 {@code @Mixin(X.class)}</h2>
 * 本配置是**可选兼容**（{@code required:false}，见 {@code astral_dice.jade.mixins.json}）。
 * 用 {@code targets="..."} 时注解里不含 Jade 的类型常量，Jade 缺席时本 mixin 类仍能正常加载、
 * 由 Mixin 跳过，不会因为 {@code NoClassDefFoundError} 把可选兼容变成硬依赖。
 *
 * <h2>与 neoforge-1.21.1 线的一处**已知差异**（不是缺陷）</h2>
 * neo 线对生物改用 {@code CombatFormula#mobDefenseInt(TargetCategory.classify(e), armor, toughness)}，
 * 本线沿用 fabric 家族的既有决定（与 fabric-1.20.1 完全一致）：**只改玩家**，生物回落原版护甲值。
 * 两者若要统一属产品口径变更，另开改动处理，不在本次 mixin 修复范围内。
 */
@Mixin(targets = "snownee.jade.addon.vanilla.EntityHealthAndArmorProvider")
public abstract class ArmorValueProviderMixin {

    @Redirect(
            // 完整描述符（避开同名的合成桥接重载 `(ITooltip, Accessor, IPluginConfig)V`）
            method = "appendTooltip(Lsnownee/jade/api/ITooltip;Lsnownee/jade/api/EntityAccessor;"
                    + "Lsnownee/jade/api/config/IPluginConfig;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getArmorValue()I"))
    private static int astralDice$armorAsDefense(LivingEntity entity) {
        if (entity instanceof Player player) {
            return DiceCombatModifiers.defensePowerOf(player);
        }
        return entity.getArmorValue();
    }
}
