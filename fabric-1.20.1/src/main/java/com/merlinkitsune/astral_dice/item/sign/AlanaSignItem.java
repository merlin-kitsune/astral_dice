package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.compat.curios.SlotContext;
import com.merlinkitsune.astral_dice.component.SignAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 修女立牌（alana，稀有）。
 *
 * <p><b>被动「鼓起勇气」</b>：每次触发骰神赐福 +5 攻击（叠加）；期间单次受击 &gt;10 点立即移除全部加成；
 * 骰神赐福结束时始终移除。
 *
 * <p><b>主动「铁处女」</b>：{@code alana_iron_maiden} 30 秒，免疫一切伤害与负面效果；若处于赐福期间
 * 且被动已失效，则立即恢复被动加成。生效期间玩家半透明显示（客户端 {@code SignTranslucencyMixin}）。
 *
 * <p><b>平台差异(Fabric 1.20.1 / 逐条理由)</b>:
 * <ul>
 *   <li><b>附件键</b>:{@code ModAttachments.getAlanaPassiveBonus} ⇒ {@link SignAttachments}
 *       (ModAttachments 在本批次写权之外,键名/codec/默认值/同步标记逐字相同);</li>
 *   <li><b>施效通道</b>:forge 的 {@code event.PlayerEffectProtection#apply} 在本线不存在,
 *       换成 {@link EffectTimerGuard#apply}(本线既有立牌 Bonnie/Fanny/Haiqing/Hanna 的现行写法,
 *       语义都是「施加本模组有时长效果并记账」);</li>
 *   <li><b>Curios 槽位上下文</b>:{@code top.theillusivec4.curios.api.SlotContext} ⇒ 本线门面
 *       {@link SlotContext}(禁止 import top.theillusivec4.curios.*)。</li>
 * </ul>
 */
public class AlanaSignItem extends BaseSignItem {
    private static final int IRON_MAIDEN_TICKS = 600;
    public static final int BONUS_PER_TRIGGER = 5;

    public AlanaSignItem(Properties properties) {
        super(properties);
    }

    /** 是否佩戴本立牌且带了骰子（被动统计的前提）。 */
    public static boolean isEquipped(Player player) {
        return SignEquipCheck.isEquipped(player, ModItems.ALANA_SIGN.get());
    }

    /** 骰神赐福触发一次 ⇒ 累加 5 点攻击。 */
    public static void onBlessingTriggered(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        int bonus = SignAttachments.getAlanaPassiveBonus(player) + BONUS_PER_TRIGGER;
        SignAttachments.setAlanaPassiveBonus(player, bonus);
        refreshBonus(player);
    }

    public static void refreshBonus(Player player) {
        int bonus = SignAttachments.getAlanaPassiveBonus(player);
        SignAttributeUtil.apply(player, Attributes.ATTACK_DAMAGE, "alana_courage", bonus,
                AttributeModifier.Operation.ADDITION);
    }

    /** 移除全部被动加成（受击 &gt;10 / 赐福结束 / 卸下）。 */
    public static void clearBonus(Player player) {
        if (player == null) return;
        SignAttachments.setAlanaPassiveBonus(player, 0);
        SignAttributeUtil.remove(player, Attributes.ATTACK_DAMAGE, "alana_courage");
    }

    @Override
    protected net.minecraft.world.InteractionResultHolder<ItemStack> handleUse(Level level, Player player,
                                                                              ItemStack stack) {
        if (!level.isClientSide()) {
            boolean blessingActive = player.hasEffect(ModEffects.DICE_BLESSING.get());
            EffectTimerGuard.apply(player, new MobEffectInstance(ModEffects.ALANA_IRON_MAIDEN.get(), IRON_MAIDEN_TICKS, 0,
                    false, true, true));
            if (blessingActive && SignAttachments.getAlanaPassiveBonus(player) <= 0) {
                SignAttachments.setAlanaPassiveBonus(player, BONUS_PER_TRIGGER);
                refreshBonus(player);
            }
            sendSignActionBar(player, "msg.astral_dice.alana_done");
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            clearBonus(player);
        }
        super.onUnequip(slotContext, newStack, stack);
    }

    /**
     * 非玩家 owner 的主动（2026-11-11 P1 补齐）：只实现「铁处女 30s 免疫」这一段。
     */
    @Override
    protected boolean activateForEntity(net.minecraft.world.entity.LivingEntity owner) {
        if (owner == null || owner.level().isClientSide()) {
            return false;
        }
        owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.merlinkitsune.astral_dice.effect.ModEffects.ALANA_IRON_MAIDEN.get(), IRON_MAIDEN_TICKS, 0, false, true, true));
        org.slf4j.LoggerFactory.getLogger(AlanaSignItem.class).info(
                "AP_SIGN_ACTIVE_ENTITY: sign=alana:owner={}:duration={}",
                owner.getName().getString(), IRON_MAIDEN_TICKS);
        return true;
    }
}
