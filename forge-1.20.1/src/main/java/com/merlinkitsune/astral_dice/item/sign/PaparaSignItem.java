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
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * 吸血鬼立牌(命名:papara)。
 * 被动:生命值 ≤ 最大生命值一半时,攻击力/防御力 +3(血量高于一半后效果消失,动态判断)。
 * 主动:获得"嘬一口"效果 3:00,期间攻击时恢复骰神赐福最终伤害的一半、受伤时恢复单次受到伤害的一半(取整)。
 */
@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
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
        player.addEffect(new MobEffectInstance(ModEffects.PAPARA_BITE.get(), 3600, 0, false, true, true));
        return InteractionResultHolder.success(stack);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 被动:半血或"嘬一口"期间防御力 +3 → 护甲 +6(动态判断,经 ARMOR 属性生效)
        boolean active = player.getHealth() <= player.getMaxHealth() / 2.0f || player.hasEffect(ModEffects.PAPARA_BITE.get());
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(
                player, "papara_def_armor", active ? 3 : 0);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(player, "papara_def_armor", 0);
    }

    // 吸血鬼立牌(papara)主动"嘬一口":受伤时恢复单次受到伤害的一半生命(取整,至少 1 点)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPaparaBiteHurtHeal(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!player.hasEffect(ModEffects.PAPARA_BITE.get())) return;
        int heal = Math.max(1, (int) event.getAmount() / 2);
        player.heal(heal);
    }

}
