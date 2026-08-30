package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;

/**
 * 吸血鬼立牌(命名:papara)。
 * 被动:生命值 ≤ 最大生命值一半时,攻击力/防御力 +3(血量高于一半后效果消失,动态判断)。
 * 主动:获得"嘬一口"效果 3:00,期间攻击时恢复骰神赐福最终伤害的一半、受伤时恢复单次受到伤害的一半(取整)。
 */
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class PaparaSignItem extends BaseSignItem {
    public PaparaSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动:获得"嘬一口"效果 3:00(visible=true 使效果图标在 HUD 正常显示)
        player.addEffect(new MobEffectInstance(ModEffects.PAPARA_BITE, 3600, 0, false, true, true));
        return InteractionResultHolder.success(stack);
    }

    // 吸血鬼立牌(papara)主动"嘬一口":受伤时恢复单次受到伤害的一半生命(取整,至少 1 点)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPaparaBiteHurtHeal(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!player.hasEffect(ModEffects.PAPARA_BITE)) return;
        int heal = Math.max(1, (int) event.getNewDamage() / 2);
        player.heal(heal);
    }

}
