package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.concurrent.ThreadLocalRandom;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;

public class MisakiSignItem extends BaseSignItem {
    public MisakiSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (!level.isClientSide) {
            // 主动技能:获得"爆发"效果 2:00(visible=true 使效果图标在 HUD 正常显示)
            player.addEffect(new MobEffectInstance(ModEffects.MISAKI_BURST.get(), 2400, 0, false, true, true));

            // 若被动层数已达 3 层:减少 2 层,并向物品栏增加一张"名刀嘎呜切"
            int stacks = ModDataComponents.MISAKI_SIGN_STACKS.getOrDefault(stack,  0);
            if (stacks >= 3) {
                ModDataComponents.MISAKI_SIGN_STACKS.set(stack,  stacks - 2);
                ItemStack meito = new ItemStack(ModItems.ATTACK_CARD_MEITO.get());
                VitaminPillChipItem.giveCard(player, meito);
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 清除剑气层数
        ModDataComponents.MISAKI_SIGN_STACKS.set(stack,  0);
    }

    // 装备护法立牌时,"名刀嘎呜切"费用降低为 3 点
    public static int getMeitoCost(Player player) {
        return hasMisakiEquipped(player) ? 3 : AppliedStone.cost("meito");
    }

    // 名刀费用考虑护法立牌后的实际费用
    public static int effectiveCost(Player player, String type) {
        if ("meito".equals(type)) {
            return getMeitoCost(player);
        }
        return AppliedStone.cost(type);
    }

    public static boolean hasMisakiEquipped(Player player) {
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.MISAKI_SIGN.get())).isPresent();
    }
}
