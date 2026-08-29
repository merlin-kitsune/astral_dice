package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 吸血鬼立牌(命名:papara)。
 * 被动:生命值 ≤ 最大生命值一半时,攻击力/防御力 +3(血量高于一半后效果消失,动态判断)。
 * 主动:获得"嘬一口"效果 3:00,期间攻击时恢复骰神赐福最终伤害的一半、受伤时恢复单次受到伤害的一半(取整)。
 */
public class PaparaSignItem extends BaseSignItem {
    public PaparaSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动:获得"嘬一口"效果 3:00(visible=true 使效果图标在 HUD 正常显示)
        player.addEffect(new MobEffectInstance(ModEffects.PAPARA_BITE.get(), 3600, 0, false, true, true));
        return InteractionResultHolder.success(stack);
    }
}
