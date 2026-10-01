package com.merlinkitsune.astral_dice.mixin.jade;

import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 把 Jade 护甲条读到的数值从**原版护甲值**换成**本模组防御力**（Fabric 1.20.1 线）。
 *
 * <h2>落点与 neo 两线不同、与 forge 线**相同**</h2>
 * Jade 11.13.3（{@code 11.13.3+fabric}）**没有** {@code EntityHealthAndArmorProvider}，护甲由独立枚举
 * {@code snownee.jade.addon.vanilla.EntityArmorProvider} 提供。
 * <p>本类按 **Jade-Fabric 生产 jar（`maven.modrinth:jade:ahavHbAT`）** 反编译确证（{@code javap -c -p}）：
 * <pre>
 *   6: checkcast     class net/minecraft/class_1309            // LivingEntity
 *  13: invokevirtual net/minecraft/class_1309.method_6096:()I  // getArmorValue  ← 全方法内**仅此 1 次**
 *  28: new           class snownee/jade/impl/ui/ArmorElement
 *  34: invokespecial ArmorElement."&lt;init&gt;":(F)V
 * </pre>
 * ⇒ 与 forge 线**逐项同构**（连坐标都对得上）。因此这里 {@code getArmorValue()} 只有一次调用，
 * 重定向后门控与数值同时落在防御力上 —— 防御力下限为 2（{@code DiceCombatModifiers#defensePowerOf}）
 * 恒 &gt; 0 ⇒ 护甲行永远显示。
 *
 * <h2>只对玩家生效</h2>
 * 防御力是本模组的玩家侧派生值 ⇒ 非玩家回落原版 {@code getArmorValue()}，生物显示保持原样。
 *
 * <h2>与 forge 线的两处平台差异（**不要互抄**）</h2>
 * <ol>
 *   <li><b>没有 Mixin 注解处理器（AP）</b>：Loom 1.14 起 AP 默认关闭、注解值改由 {@code remapJar}
 *       阶段重映射 ⇒ **不存在** forge 线那条「{@code Unable to locate obfuscation mapping}」编译门禁。
 *       故本类**不写** {@code remap = false} —— 恰恰相反，{@code @At} 的 target 里含**原版成员**
 *       （{@code LivingEntity#getArmorValue}），**必须**走重映射才能在 intermediary 生产环境命中
 *       （实测：{@code getArmorValue} → {@code method_6096}、{@code Player} → {@code class_1657}）。</li>
 *   <li><b>{@code method} 用完整描述符是安全的</b>（与 {@link ArmorElementIconMixin} 相反）：
 *       本描述符里**只有 Jade 自己的类**（{@code ITooltip} / {@code EntityAccessor} / {@code IPluginConfig}），
 *       Jade 不混淆 ⇒ 生产环境字符串完全一致；而 Loom 对带描述符的 {@code method} 原样透传 ⇒ 结果正确。
 *       写完整描述符还能避开同名的合成桥接重载。</li>
 * </ol>
 *
 * <h2>处理器为什么是**非 static**</h2>
 * 被注入的 {@code EntityArmorProvider#appendTooltip} 是**实例方法**（javap 实证）。
 * Fabric 侧用 Mixin **0.8.7**，其收尾判据为 {@code checkTargetModifiers(target, false)} ——
 * 只拦「非 static 处理器打 static 方法」，「非 static 处理器打实例方法」本来就合法
 * ⇒ 写成非 static 是**两侧都安全**的写法（也正因如此，本类不会重演 forge 线 0.8.5 的
 * {@code InvalidInjectionException}）。
 *
 * <h2>为什么用字符串 {@code targets}</h2>
 * 本配置是可选兼容（{@code required:false}）；用字符串可让 mixin 类在 Jade 缺席时仍能加载。
 */
@Mixin(targets = "snownee.jade.addon.vanilla.EntityArmorProvider")
public abstract class ArmorValueProviderMixin {

    @Redirect(
            // 完整描述符（避开同名的合成桥接重载 `(ITooltip, Accessor, IPluginConfig)V`）
            method = "appendTooltip(Lsnownee/jade/api/ITooltip;Lsnownee/jade/api/EntityAccessor;"
                    + "Lsnownee/jade/api/config/IPluginConfig;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getArmorValue()I"))
    private int astralDice$armorAsDefense(LivingEntity entity) {
        if (entity instanceof Player player) {
            return DiceCombatModifiers.defensePowerOf(player);
        }
        return entity.getArmorValue();
    }
}
