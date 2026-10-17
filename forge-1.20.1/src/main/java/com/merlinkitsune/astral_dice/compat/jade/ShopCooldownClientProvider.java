package com.merlinkitsune.astral_dice.compat.jade;

import com.merlinkitsune.astral_dice.block.CardShopBlock;
import com.merlinkitsune.astral_dice.block.ChipShopBlock;
import com.merlinkitsune.astral_dice.block.EventDrawBlock;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.shop.ChipShopState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/**
 * Jade 客户端行提供器（Forge 1.20.1）：从**已同步到客户端的玩家冷却值**现算剩余 tick，
 * 追加一行倒计时。
 *
 * <p>为什么不用服务端数据包：见 {@link AstralDiceJadePlugin} 的类注释——Jade 11.13.3 只为
 * BlockEntity 发数据，本模组三个方块都没有 BlockEntity。冷却真值仍是服务端权威的那份玩家数据：
 * {@code ModAttachments} 的 {@code SHOP_TRANSFER_COOLDOWN_END} / {@code CHIP_SHOP_STATE}
 * 已补 {@code .sync()}，{@code EVENT_DRAWER_COOLDOWN_UNTIL} 本来就是同步键。
 *
 * <p>文本：卡牌商店 = {@code jade.astral_dice.restock_countdown}（「补货倒计时 M:SS」）；
 * 筹码商店 / 抽奖盒 = {@code jade.astral_dice.enter_countdown}（「可进入倒计时 M:SS」）。
 * 时间值按全仓 tooltip 染色口径 {@code §9}（蓝），语言值里自带染色码，行尾 {@code §7} 复位。
 *
 * <p>剩余 ≤ 0（无冷却 / 已结束）⇒ 不追加该行。
 */
public enum ShopCooldownClientProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation("astral_dice", "shop_cooldown");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        Player player = accessor.getPlayer();
        if (player == null) {
            return;
        }
        long remaining = remainingTicks(accessor.getBlock(), accessor.getLevel().getGameTime(), player);
        if (remaining <= 0L) {
            return;
        }
        tooltip.add(Component.translatable(langKey(accessor.getBlock()), formatCountdown(remaining)));
    }

    /**
     * 该方块对应当前玩家的冷却剩余 tick（≤ 0 = 不在冷却 / 非目标方块）。
     *
     * <p>客户端读取的三个值都来自同步缓存（{@code ClientAstralData}）；未同步/未知键回退默认值 0
     * ⇒ 不显示该行（不会出现错误读数）。
     */
    static long remainingTicks(Block block, long now, Player player) {
        if (block instanceof CardShopBlock) {
            return ModAttachments.getShopTransferCooldownEnd(player) - now;
        }
        if (block instanceof ChipShopBlock) {
            return ChipShopState.get(player).cooldownUntil() - now;
        }
        if (block instanceof EventDrawBlock) {
            return ModAttachments.getEventDrawerCooldownUntil(player) - now;
        }
        return 0L;
    }

    /** 行文案键：卡牌商店显示「补货倒计时」，筹码商店 / 抽奖盒显示「可进入倒计时」。 */
    static String langKey(Block block) {
        return block instanceof CardShopBlock
                ? "jade.astral_dice.restock_countdown"
                : "jade.astral_dice.enter_countdown";
    }

    /**
     * tick → {@code M:SS}，**秒向上取整**（剩余 1 tick 也显示 0:01；与
     * {@code CardShopMenu#computeTransferCooldownSeconds} 的 {@code (remain + 19) / 20} 同口径）。
     */
    static String formatCountdown(long ticks) {
        long seconds = (Math.max(0L, ticks) + 19L) / 20L;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
