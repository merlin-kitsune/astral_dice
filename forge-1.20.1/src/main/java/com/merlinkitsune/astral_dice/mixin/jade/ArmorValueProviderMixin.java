package com.merlinkitsune.astral_dice.mixin.jade;

import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 把 Jade 护甲条读到的数值从**原版护甲值**换成**本模组防御力**（Forge 1.20.1 线）。
 *
 * <h2>落点与 neo 两线不同</h2>
 * Jade 11.13.3（1.20-forge）**没有** {@code EntityHealthAndArmorProvider}，护甲由独立枚举
 * {@code snownee.jade.addon.vanilla.EntityArmorProvider} 提供（源码
 * {@code Jade-120-forge/src/main/java/snownee/jade/addon/vanilla/EntityArmorProvider.java:21-27}）：
 * <pre>
 * LivingEntity living = (LivingEntity) accessor.getEntity();
 * float armor = living.getArmorValue();      // ← 唯一一次读取
 * if (armor == 0) return;                    // 「0 就不显示」的门控用返回值局部变量，不用二次调用
 * tooltip.add(new ArmorElement(armor));
 * </pre>
 * 因此这里 {@code getArmorValue()} 只有**一次**调用，重定向后门控与数值同时落在防御力上 ——
 * 防御力下限为 2（{@code DiceCombatModifiers#defensePowerOf}）恒 &gt; 0 ⇒ 护甲行永远显示。
 *
 * <h2>只对玩家生效</h2>
 * 防御力是本模组的玩家侧派生值 ⇒ 非玩家回落原版 {@code getArmorValue()}，生物显示保持原样。
 *
 * <h2>1.20 的方法名重映射</h2>
 * 本线产物是**生产 SRG** jar（目标 jar 里 {@code getArmorValue} 实际是 {@code m_21230_}），
 * {@code @At} 里的 {@code Lnet/minecraft/world/entity/LivingEntity;getArmorValue()I} 由
 * MDG Legacy 生成的 {@code astral_dice.refmap.json} 负责映射（同 {@code LightningBoltStrikeScopeMixin}
 * 对 {@code Level#getEntities} 的既有做法）。
 *
 * <h2>⚠️ 本类能编译，靠的是 AP 的「挂起错误抑制」——别动 {@code @At}</h2>
 * 上面的 {@code method}（{@code EntityArmorProvider.appendTooltip}）是 **Jade 自己的方法**，
 * MDG Legacy 的 {@code namedToIntermediate.tsrg} 里没有任何映射能命中它 ⇒ AP 在
 * {@code AnnotatedMixinElementHandlerInjector#registerInjector} 里会挂起一条
 * {@code NO_OBFDATA_FOR_TARGET} 错误。它之所以没有变成编译失败，是因为同一次注入里的
 * {@code @At} 指向的是**原版方法**（可重映射）：重映射成功会调
 * {@code InjectorRemap#notifyRemapped()} → {@code clearMessage()} 把那条错误丢掉，
 * 而编译收尾的 {@code dispatchPendingMessages} 只在 {@code remappedCount == 0} 时才真正抛出
 * （依据 = {@code org.spongepowered.tools.obfuscation.struct.InjectorRemap} 的类头注释）。
 * <p>⇒ **若把 {@code @At} 改成同样不可重映射的目标（例如某个 Jade 方法），本类会立刻以
 * {@code Unable to locate obfuscation mapping for @Redirect target appendTooltip(...)} 编译失败**
 * （{@link ArmorElementIconMixin} 正是这种情形）。届时正确做法是给该注入器显式写
 * {@code remap = false}，而不是去改 {@code method}。
 * <p>同名的 neo 两线（1.21.1 的 {@code EntityHealthAndArmorProvider}、26.1.2 的
 * {@code EntityHealthAndArmorProvider$Client}）不受此影响 —— 那两线未注册任何混淆环境，
 * AP 整体不做重映射处理（编译日志里没有 {@code Supported obfuscation types} 那一行）。
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
    private static int astralDice$armorAsDefense(LivingEntity entity) {
        if (entity instanceof Player player) {
            return DiceCombatModifiers.defensePowerOf(player);
        }
        return entity.getArmorValue();
    }
}
