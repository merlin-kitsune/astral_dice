package com.merlinkitsune.astral_dice.mixin.jade;

import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 把 Jade 护甲条读到的数值从**原版护甲值**换成**本模组防御力**（NeoForge 26.1.2 线）。
 *
 * <h2>落点与 1.21.1 线的差别</h2>
 * 26.1 的 Jade（26.1.11）把护甲读取搬进了**内部类** {@code EntityHealthAndArmorProvider$Client}
 * —— 外层 {@code EntityHealthAndArmorProvider} 承担服务端数据同步（{@code streamData} /
 * {@code shouldRequestData}），真正的客户端 tooltip 追加在 {@code $Client#appendTooltip}。
 * 字节码里同样是**两次** {@code LivingEntity#getArmorValue()}：
 * <pre>
 * if (config.get(MC_ENTITY_ARMOR) &amp;&amp; living.getArmorValue() &gt; 0) {   // ① 门控分支（146）
 *     armorElement = new ArmorElement(living.getArmorValue());          // ② 构造入参（158）
 * }
 * </pre>
 * （源码 {@code src/main/java/snownee/jade/addon/vanilla/EntityHealthAndArmorProvider.java} 的
 * {@code Client} 内部类；字节码见 {@code temp/t92/jade_javap.txt} 26.1.2 段。）
 *
 * <h2>只对玩家生效</h2>
 * 防御力是本模组的**玩家侧**派生值，对生物没有意义 ⇒ 非玩家回落原版 {@code getArmorValue()}。
 *
 * <h2>超过一行的退化</h2>
 * 不处理，交给 Jade 自己的 {@code ArmorElement} 构造器（阈值 {@code MC_ENTITY_ARMOR_MAX_FOR_RENDER}，
 * 超出后显示「图标 + 数字」）—— 用户裁决即「照搬 Jade 的显示方式」。
 *
 * <h2>为什么用字符串 {@code targets}</h2>
 * 本配置是可选兼容（{@code required:false}）；用字符串可让 mixin 类在 Jade 缺席时仍能加载。
 */
@Mixin(targets = "snownee.jade.addon.vanilla.EntityHealthAndArmorProvider$Client")
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
