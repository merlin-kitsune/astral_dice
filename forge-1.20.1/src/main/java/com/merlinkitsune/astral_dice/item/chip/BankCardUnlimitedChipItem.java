package com.merlinkitsune.astral_dice.item.chip;
import com.merlinkitsune.astral_dice.item.CuriosCompat;

import com.merlinkitsune.astral_dice.resource.ResourceConversion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;

/**
 * 银行卡-用不完筹码:装备时获得 3 点星光(一次性);
 * 每次骰神赐福效果结束后,使自身及团队所有成员获得 3 星币(由 {@link #onBlessingEnd} 在赐福结束时调用)。
 */
public class BankCardUnlimitedChipItem extends BaseChipItem {
    /** 赐福结束后发放的星币数量 */
    public static final int REWARD_COINS = 3;

    public BankCardUnlimitedChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴本筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.BANK_CARD_UNLIMITED.get())).isPresent();
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack curio, ItemStack prevStack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!prevStack.isEmpty()) return;
        // 装备时星光 +3(上限由 StarLightManager 统一管理)
        StarLightManager.add(player, 3);
    }

    /**
     * 骰神赐福结束时调用:使自身及团队所有成员(Minecraft 同队)获得 3 星币。
     * 死亡清场时(玩家已死亡)不发放。
     */
    public static void onBlessingEnd(Player player) {
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (player.isDeadOrDying()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        for (ServerPlayer sp : serverLevel.players()) {
            if (sp == player || (sp.getTeam() != null && sp.getTeam() == player.getTeam())) {
                giveCoins(sp);
            }
        }
    }

    private static void giveCoins(Player player) {
        ResourceConversion.giveItem(player, new ItemStack(ModItems.STAR_COIN.get(), REWARD_COINS));
    }
}
