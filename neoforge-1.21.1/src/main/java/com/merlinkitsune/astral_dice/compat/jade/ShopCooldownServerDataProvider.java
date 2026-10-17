package com.merlinkitsune.astral_dice.compat.jade;

import com.merlinkitsune.astral_dice.block.CardShopBlock;
import com.merlinkitsune.astral_dice.block.ChipShopBlock;
import com.merlinkitsune.astral_dice.block.EventDrawBlock;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.shop.ChipShopState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade 服务端数据提供器（NeoForge 1.21.1）：把**当前看向方块的那名玩家**的冷却剩余 tick
 * 写进 Jade 数据包，键 {@value #NBT_KEY}（带命名空间，避免与其它模组的数据键撞名）。
 *
 * <p>三个方块的冷却真值（全部是玩家级附件，按世界 {@code gameTime} 计时）：
 * <ul>
 *   <li>{@link CardShopBlock}：{@link ModAttachments#getShopTransferCooldownEnd(Player)}（二级转账冷却，60 秒）；</li>
 *   <li>{@link ChipShopBlock}：{@link ChipShopState#cooldownUntil()}（购买冷却，6000 ticks）；</li>
 *   <li>{@link EventDrawBlock}：{@link ModAttachments#getEventDrawerCooldownUntil(Player)}（购买冷却，6000 ticks）。</li>
 * </ul>
 *
 * <p><b>只在冷却中写入</b>（剩余 ≤ 0 时不写键），客户端据此决定是否追加整行；
 * 因此冷却结束后数据包里没有该键 ⇒ 悬浮框不会残留「0:00」。
 */
public enum ShopCooldownServerDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    /** Jade 数据包键（带命名空间）。 */
    public static final String NBT_KEY = "astral_dice:shop_cooldown";

    /** 插件配置用的唯一 id（与客户端提供器共用同一 id：同一条倒计时行的两端）。 */
    static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("astral_dice", "shop_cooldown");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        Player player = accessor.getPlayer();
        if (player == null) {
            return;
        }
        long remaining = remainingTicks(accessor.getBlock(), accessor.getLevel().getGameTime(), player);
        if (remaining > 0L) {
            data.putLong(NBT_KEY, remaining);
        }
    }

    /**
     * 该方块对应当前玩家的冷却剩余 tick（≤ 0 = 不在冷却 / 非目标方块）。
     *
     * @param block  看向的方块
     * @param now    当前世界 {@code gameTime}
     * @param player 看向方块的玩家
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

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
