package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;

import java.util.concurrent.ThreadLocalRandom;
import com.merlinkitsune.astral_dice.resource.ResourceConversion;
import com.merlinkitsune.astral_dice.item.StarLightManager;

public class ParunanSignItem extends BaseSignItem {
    public ParunanSignItem(Properties properties) {
        super(properties);
    }

    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        long gameTime = player.level().getGameTime();
        if (gameTime % (GameplayConstants.PARUNAN_PASSIVE_INTERVAL_SECONDS * 20L) == 0) {
            // 被动:每 N 秒星光 +1(上限由 StarLightManager 统一管理)
            StarLightManager.add(player, 1);
        }
    }

    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        int starlight = StarLightManager.get(player);
        if (starlight <= 0) {
            return InteractionResultHolder.fail(stack);
        }

        // 每 2 点星光返还 1 个星币(转化比例集中管理,余数部分保留)
        com.merlinkitsune.astral_dice.resource.ResourceConversion.starlightToStarCoins(player, -1);

        // 主动技能:随机获得以下任一效果
        int choice = ThreadLocalRandom.current().nextInt(3);
        MobEffectInstance effect;
        if (choice == 0) {
            effect = new MobEffectInstance(MobEffects.SATURATION, 600, 0, false, true); // 饱和 30 秒
        } else if (choice == 1) {
            effect = new MobEffectInstance(MobEffects.LUCK, 6000, 0, false, true); // 幸运 5 分钟
        } else {
            effect = new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 18000, 0, false, true); // 村庄英雄 15 分钟
        }
        player.addEffect(effect);

        return InteractionResultHolder.success(stack);
    }

    // 触发骰神赐福后立即获得 骰点*2 星光(上限由 StarLightManager 统一管理)
    public static void gainStarlightOnBlessing(Player player, int dicePoint) {
        if (player.level().isClientSide()) return;
        StarLightManager.add(player, dicePoint * 2);
    }
}
