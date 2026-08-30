package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 看板立牌(mimi)。
 *
 * <p>被动:
 * - 合成、奖励、返还卡牌后,每获得一张战斗牌,增加 1 星币;
 * - 装备时,筹码栏位 +1;
 * - 每次通过被动获得 25 个星币后,获得 1 个随机筹码
 *   (蓝色 60%,紫色 35%,金色 5%)。
 *
 * <p>主动:将物品栏中所有卡牌回收(包括专属牌),并返还 N+1 张随机卡牌;
 * 返还的随机卡牌不会包含专属牌。
 */
public class MimiSignItem extends BaseSignItem {
    /** 被动累计星币阈值 */
    public static final int STAR_COIN_THRESHOLD = 25;
    /** 随机筹码概率 */
    private static final double BLUE_CHANCE = 0.60;
    private static final double PURPLE_CHANCE = 0.35;

    public MimiSignItem(Properties properties) {
        super(properties);
    }


    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        ModAttachments.setMimiStarCoinCounter(player, 0);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 回收物品栏中所有卡牌(含专属牌),返还 N+1 张随机卡牌(不含专属)
        int recycled = recycleAllCards(player);
        for (int i = 0; i < recycled + 1; i++) {
            RandomCardHandler.giveCardTo(player, RandomCardHandler.CardCategory.ALL);
        }
        return InteractionResultHolder.success(stack);
    }

    // 玩家是否佩戴看板立牌
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.MIMI_SIGN.get())).isPresent();
    }

    // 被动:每获得一张战斗牌时调用(与维生素药丸相同触发机制,不包含拾取)
    public static void onBattleCardGained(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        giveStarCoin(player);
        // 每累计 25 个被动星币,获得一个随机筹码
        int counter = ModAttachments.getMimiStarCoinCounter(player) + 1;
        if (counter >= STAR_COIN_THRESHOLD) {
            ModAttachments.setMimiStarCoinCounter(player, 0);
            giveRandomChip(player);
        } else {
            ModAttachments.setMimiStarCoinCounter(player, counter);
        }
    }

    // 回收物品栏中所有卡牌(包括专属牌),返回回收数量
    private static int recycleAllCards(Player player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.isEmpty()) continue;
            if (ModItems.isCardItem(stack)) {
                count += stack.getCount();
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        return count;
    }

    private static void giveStarCoin(Player player) {
        ItemStack coin = new ItemStack(ModItems.STAR_COIN.get());
        if (!player.getInventory().add(coin)) {
            player.drop(coin, false);
        }
    }

    // 随机筹码:蓝色 60%,紫色 35%,金色 5%
    private static void giveRandomChip(Player player) {
        double roll = ThreadLocalRandom.current().nextDouble();
        List<ItemStack> pool;
        if (roll < BLUE_CHANCE) {
            pool = blueChips();
        } else if (roll < BLUE_CHANCE + PURPLE_CHANCE) {
            pool = purpleChips();
        } else {
            pool = goldChips();
        }
        if (pool.isEmpty()) return;
        ItemStack chip = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        if (!player.getInventory().add(chip)) {
            player.drop(chip, false);
        }
    }

    private static List<ItemStack> blueChips() {
        List<ItemStack> list = new ArrayList<>();
        list.add(new ItemStack(ModItems.MEDKIT_EMERGENCY_CHIP.get()));
        list.add(new ItemStack(ModItems.TARGET_CHIP.get()));
        list.add(new ItemStack(ModItems.MARKER_SPRAYER_CHIP.get()));
        list.add(new ItemStack(ModItems.HAND_FAN_SMALL_CHIP.get()));
        list.add(new ItemStack(ModItems.EIGHT_SIDED_DICE.get()));
        list.add(new ItemStack(ModItems.ATM.get()));
        list.add(new ItemStack(ModItems.BANK_CARD_LOW.get()));
        list.add(new ItemStack(ModItems.BOXING_GLOVES_LOW.get()));
        list.add(new ItemStack(ModItems.SPEED_SKATES_LOW.get()));
        list.add(new ItemStack(ModItems.MOTO_HELMET_LOW.get()));
        list.add(new ItemStack(ModItems.SANDWICH_LOW.get()));
        list.add(new ItemStack(ModItems.BUFFER_SHIELD.get()));
        list.add(new ItemStack(ModItems.CURSED_SWORD.get()));
        return list;
    }

    private static List<ItemStack> purpleChips() {
        List<ItemStack> list = new ArrayList<>();
        list.add(new ItemStack(ModItems.FLASHLIGHT_CHIP.get()));
        list.add(new ItemStack(ModItems.CUTTER_CHIP.get()));
        list.add(new ItemStack(ModItems.SCOPE_CHIP.get()));
        list.add(new ItemStack(ModItems.VITAMIN_PILL_CHIP.get()));
        list.add(new ItemStack(ModItems.MAGIC_TOME_CHIP.get()));
        list.add(new ItemStack(ModItems.BIG_BACKPACK_CHIP.get()));
        list.add(new ItemStack(ModItems.HAND_FAN_BIG_CHIP.get()));
        list.add(new ItemStack(ModItems.BANK_CARD_HIGH.get()));
        list.add(new ItemStack(ModItems.BOXING_GLOVES_MEDIUM.get()));
        list.add(new ItemStack(ModItems.SPEED_SKATES_MEDIUM.get()));
        list.add(new ItemStack(ModItems.MOTO_HELMET_MEDIUM.get()));
        list.add(new ItemStack(ModItems.SANDWICH_MEDIUM.get()));
        list.add(new ItemStack(ModItems.MAGIC_QUIVER.get()));
        list.add(new ItemStack(ModItems.REVENGE_HALBERD.get()));
        list.add(new ItemStack(ModItems.CANDY_CHIP.get()));
        list.add(new ItemStack(ModItems.FRIENDSHIP_BADGE.get()));
        return list;
    }

    private static List<ItemStack> goldChips() {
        List<ItemStack> list = new ArrayList<>();
        list.add(new ItemStack(ModItems.CUTTER_BLADE_CHIP.get()));
        list.add(new ItemStack(ModItems.EAGLE_SCOPE_CHIP.get()));
        list.add(new ItemStack(ModItems.MEDKIT_COMPLETE_CHIP.get()));
        list.add(new ItemStack(ModItems.NINJA_STAR_CHIP.get()));
        list.add(new ItemStack(ModItems.BANK_CARD_UNLIMITED.get()));
        list.add(new ItemStack(ModItems.BOXING_GLOVES_HIGH.get()));
        list.add(new ItemStack(ModItems.SPEED_SKATES_HIGH.get()));
        list.add(new ItemStack(ModItems.MOTO_HELMET_HIGH.get()));
        list.add(new ItemStack(ModItems.SANDWICH_HIGH.get()));
        list.add(new ItemStack(ModItems.STAR_COIN_HAMMER.get()));
        list.add(new ItemStack(ModItems.PIERCING_GUN.get()));
        list.add(new ItemStack(ModItems.SATELLITE_CHIP.get()));
        return list;
    }
}
