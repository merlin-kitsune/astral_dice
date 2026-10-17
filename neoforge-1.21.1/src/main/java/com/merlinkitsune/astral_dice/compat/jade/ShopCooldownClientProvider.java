package com.merlinkitsune.astral_dice.compat.jade;

import com.merlinkitsune.astral_dice.block.CardShopBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/**
 * Jade 客户端行提供器（NeoForge 1.21.1）：读服务端写进数据包的剩余 tick，追加一行倒计时。
 *
 * <p>文本：卡牌商店 = {@code jade.astral_dice.restock_countdown}（「补货倒计时 M:SS」）；
 * 筹码商店 / 抽奖盒 = {@code jade.astral_dice.enter_countdown}（「可进入倒计时 M:SS」）。
 * 时间值按全仓 tooltip 染色口径 {@code §9}（蓝），语言值里自带染色码，行尾 {@code §7} 复位。
 *
 * <p><b>不写数据就不加行</b>：服务端在剩余 ≤ 0 时不写 {@link ShopCooldownServerDataProvider#NBT_KEY}，
 * 这里 {@code getLong} 取到 0 ⇒ 直接 return（冷却结束悬浮框不会残留 0:00）。
 */
public enum ShopCooldownClientProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        long remaining = accessor.getServerData().getLong(ShopCooldownServerDataProvider.NBT_KEY);
        if (remaining <= 0L) {
            return;
        }
        tooltip.add(Component.translatable(langKey(accessor.getBlock()), formatCountdown(remaining)));
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
        return ShopCooldownServerDataProvider.UID;
    }
}
