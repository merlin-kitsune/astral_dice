package com.merlinkitsune.astral_dice.mixin.jade;

import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 把 Jade 护甲条读到的数值从**原版护甲值**换成**本模组防御力**（NeoForge 1.21.1 线）。
 *
 * <h2>落点</h2>
 * Jade 15.10.6 的护甲供应器是 {@code snownee.jade.addon.vanilla.EntityHealthAndArmorProvider}
 * （枚举，实现客户端 {@code IEntityComponentProvider}），其
 * {@code appendTooltip(ITooltip, EntityAccessor, IPluginConfig)} 内**两次**读取
 * {@code LivingEntity#getArmorValue()}：
 * <pre>
 * if (config.get(MC_ENTITY_ARMOR) &amp;&amp; living.getArmorValue() &gt; 0) {   // ① 门控分支
 *     armorElement = new ArmorElement(living.getArmorValue());          // ② 构造入参
 * }
 * </pre>
 * （源码 {@code src/main/java/snownee/jade/addon/vanilla/EntityHealthAndArmorProvider.java}；
 * 字节码 {@code _jade_javap.txt} 第 111/123 条指令处。）
 * {@code @Redirect} 默认命中该方法内**全部**匹配调用，故①②同时被替换 —— 门控与数值一致，
 * 不会出现「按原版护甲判定为 0 直接不显示」的错配。
 *
 * <h2>只对玩家生效</h2>
 * 防御力是本模组的**玩家侧**派生值（{@code DiceCombatModifiers#defensePowerOf(Player)}，
 * 来源 = 骰子 + 筹码 + 立牌的结算口径），对生物没有意义 ⇒ 非玩家实体一律回落原版
 * {@code getArmorValue()}，怪物/动物的 Jade 护甲显示保持原样。
 *
 * <h2>超过一行怎么办</h2>
 * 不处理。阈值、分页、超出后退化成「图标 + 数字」全部由 Jade 自己的
 * {@code ArmorElement} 构造器决定（{@code MC_ENTITY_ARMOR_MAX_FOR_RENDER} =
 * {@code MC_ENTITY_HEALTH_ICONS_PER_LINE} 行 × 2 点）—— 用户裁决就是「照搬 Jade 的显示方式」，
 * 只要数值换掉，退化行为自动跟随。
 *
 * <h2>为什么用字符串 {@code targets} 而不是 {@code @Mixin(X.class)}</h2>
 * 本配置是**可选兼容**（{@code required:false}，见 {@code astral_dice.jade.mixins.json}）。
 * 用 {@code targets="..."} 时注解里不含 Jade 的类型常量，Jade 缺席时本 mixin 类仍能正常加载、
 * 由 Mixin 跳过，不会因为 {@code NoClassDefFoundError} 把可选兼容变成硬依赖。
 */
@Mixin(targets = "snownee.jade.addon.vanilla.EntityHealthAndArmorProvider")
public abstract class ArmorValueProviderMixin {

    @Redirect(
            // 用**完整描述符**而不是裸方法名：本类另有一个合成的桥接重载
            // `appendTooltip(ITooltip, Accessor, IPluginConfig)`，裸名会指向两个方法。
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
