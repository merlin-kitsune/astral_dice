package com.merlinkitsune.astral_dice.compat.jade;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.block.EventDrawBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade 客户端行提供器：抽奖盒「花费」行（“需要支付 N 星币”）。
 *
 * <p><b>数值单一事实源</b>：直接取 {@link EventDrawBlock#priceStarCoins()}，与右键扣费共用同一常量，
 * 改价只改一处，不会出现“Jade 显示 5、实际扣 3”的漂移。
 *
 * <p><b>染色</b>：遵循全仓 tooltip 口径 —— 数值黄 {@code §e}、行尾 {@code §7} 复位，染色码写在语言值里。
 */
public enum EventDrawerCostProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(AstralDiceMod.MODID, "event_drawer_cost");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        tooltip.add(Component.translatable("jade.astral_dice.event_drawer_cost",
                EventDrawBlock.priceStarCoins()));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
