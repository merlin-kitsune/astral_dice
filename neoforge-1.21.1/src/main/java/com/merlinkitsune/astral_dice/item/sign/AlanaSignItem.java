package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.starengine.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 修女立牌（alana，稀有）。
 *
 * <p><b>被动「鼓起勇气」</b>：每次触发骰神赐福 +5 攻击（叠加）；期间单次受击 &gt;10 点立即移除全部加成；
 * 骰神赐福结束时始终移除。
 *
 * <p><b>主动「铁处女」</b>：{@code alana_iron_maiden} 30 秒，免疫一切伤害与负面效果；若处于赐福期间
 * 且被动已失效，则立即恢复被动加成。生效期间玩家半透明显示（客户端 mixin）。
 */
public class AlanaSignItem extends BaseSignItem implements SignHooks.BlessingTriggered {
    private static final int IRON_MAIDEN_TICKS = 600;
    public static final int BONUS_PER_TRIGGER = 5;

    public AlanaSignItem(Properties properties) {
        super(properties);
    }

    /** 是否佩戴本立牌且带了骰子（被动统计的前提）。 */
    public static boolean isEquipped(Player player) {
        return SignEquipCheck.isEquipped(player, ModItems.ALANA_SIGN.get());
    }

    /** 骰神赐福触发一次 ⇒ 累加 5 点攻击（由 ``combat/DiceCombatEvents`` 在 triggeredBlessing 为真时调用）。 */
    public static void onBlessingTriggered(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        int bonus = ModAttachments.getAlanaPassiveBonus(player) + BONUS_PER_TRIGGER;
        ModAttachments.setAlanaPassiveBonus(player, bonus);
        refreshBonus(player);
    }

    // === B1 注册式钩子分发入口（实例转发；静态方法保留兼容外部直调） ===

    @Override
    public void handleBlessingTriggered(Player player) {
        onBlessingTriggered(player);
    }

    public static void refreshBonus(Player player) {
        int bonus = ModAttachments.getAlanaPassiveBonus(player);
        SignAttributeUtil.apply(player, Attributes.ATTACK_DAMAGE, "alana_courage", bonus,
                AttributeModifier.Operation.ADD_VALUE);
    }

    /** 移除全部被动加成（受击 &gt;10 / 赐福结束 / 卸下）。 */
    public static void clearBonus(Player player) {
        if (player == null) return;
        ModAttachments.setAlanaPassiveBonus(player, 0);
        SignAttributeUtil.remove(player, Attributes.ATTACK_DAMAGE, "alana_courage");
    }

    @Override
    protected net.minecraft.world.InteractionResultHolder<ItemStack> handleUse(Level level, Player player,
                                                                              ItemStack stack) {
        if (!level.isClientSide) {
            boolean blessingActive = player.hasEffect(ModEffects.DICE_BLESSING);
            EffectTimerGuard.apply(player, new MobEffectInstance(ModEffects.ALANA_IRON_MAIDEN, IRON_MAIDEN_TICKS, 0,
                    false, true, true));
            if (blessingActive && ModAttachments.getAlanaPassiveBonus(player) <= 0) {
                ModAttachments.setAlanaPassiveBonus(player, BONUS_PER_TRIGGER);
                refreshBonus(player);
            }
            sendSignActionBar(player, "msg.astral_dice.alana_done");
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void onUnequip(top.theillusivec4.curios.api.SlotContext slotContext, ItemStack newStack,
                             ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            clearBonus(player);
        }
        super.onUnequip(slotContext, newStack, stack);
    }
}
