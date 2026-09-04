package com.merlinkitsune.astral_dice.trade;

import com.merlinkitsune.astral_dice.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 绿宝石骰子交易能力(1.20.1):佩戴绿宝石骰子时,村民交易中的绿宝石费用改为星币支付,并享受 20% 折扣。
 *
 * <p>实现方式:在村民交易的关键路径(发送报价 / 报价匹配 / 自动放入 / 成交)上,用 {@link #CONTEXT}
 * 线程局部变量携带当前交易玩家;{@code MerchantOfferMixin} 在上下文中把绿宝石费用替换为星币并打折。
 */
public final class EmeraldDiceTrade {

    private static final ThreadLocal<Player> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private EmeraldDiceTrade() {
    }

    public static void begin(Player player) {
        if (!hasEmeraldDice(player)) return;
        if (CONTEXT.get() == null) {
            CONTEXT.set(player);
            DEPTH.set(0);
        }
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void end() {
        if (CONTEXT.get() == null) return;
        int depth = DEPTH.get() - 1;
        if (depth <= 0) {
            CONTEXT.remove();
            DEPTH.remove();
        } else {
            DEPTH.set(depth);
        }
    }

    public static boolean isSwapActive() {
        return CONTEXT.get() != null;
    }

    public static boolean hasEmeraldDice(Player player) {
        if (player == null || player.level().isClientSide()) return false;
        return CuriosCompat.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(s -> s.is(ModItems.EMERALD_DICE.get())).isPresent())
                .orElse(false);
    }

    public static boolean isEmerald(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.EMERALD);
    }

    /** 绿宝石费用 → 星币费用(1:1),数量按 20% 折扣(至少 1)。非绿宝石费用原样返回。 */
    public static ItemStack transform(ItemStack stack) {
        if (!isEmerald(stack)) return stack;
        return new ItemStack(ModItems.STAR_COIN.get(), discount(stack.getCount()));
    }

    /** 20% 折扣(向下取整,至少 1)。 */
    public static int discount(int count) {
        return Math.max(1, (int) Math.floor(count * 0.8));
    }
}
