package com.merlinkitsune.astral_dice.mixin.jade;

import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.starengine.combat.CombatFormula;
import com.merlinkitsune.starengine.combat.TargetCategory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * <h2>玩家与生物：同一个单位（2026-10-23 用户裁决 A）</h2>
 * 玩家 = {@code DiceCombatModifiers#defensePowerOf(Player)}；生物 =
 * {@code CombatFormula#mobDefenseInt(TargetCategory.classify(e), e.getArmorValue(), 韧性)}
 * —— 后者正是骰战结算（{@code DiceCombatEvents} 防御结算块）与贯穿之铳
 * （{@code PiercingGunChipItem#getTargetDefense}）用的那一支。
 * <p>⇒ Jade 的护甲图标（{@code ArmorElement} 每图标 2 点）在**两条分支上都是防御力**，
 * 与 HUD、与伤害结算同源；生物不再显示原版护甲值。
 * <p>2026-09-26 那句「防御力是玩家侧派生值、对生物没有意义」**已过期**（生物当日就有独立公式），
 * 本改动即为此收口：全套铁甲僵尸 = {@code floor(15×0.40) = 6} 点防御（3 图标），
 * 而不是原版 15 护甲（8 图标）。
 * <p>门控同时统一：{@code getArmorValue() > 0} 被重定向后判的是**防御力 > 0** ——
 * 护甲折不出 1 点防御（&lt; 2.5）的生物整行不显示，与公式自洽。
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
        // 生物：与骰战结算同一口径（CombatFormula.mobDefenseInt，含向下取整），
        // 不再显示原版护甲值 —— 否则 Jade 的「护甲」与真实防御力是两个单位。
        return CombatFormula.mobDefenseInt(
                TargetCategory.classify(entity),
                entity.getArmorValue(),
                entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
    }
}
