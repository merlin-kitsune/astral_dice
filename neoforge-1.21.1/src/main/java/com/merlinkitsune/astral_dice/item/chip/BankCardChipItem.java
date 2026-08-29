package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.StarLightManager;

/**
 * 银行卡筹码(余额少/余额多):装备期间提供常驻星光基础值(下限)。
 * 基础值由 {@link StarLightManager#getBasePoints} 实时计算,装备期间生效、卸下立即移除;
 * 装备时若当前星光低于基础值,由 {@link StarLightManager#set} 自动补回基础值。
 */
public class BankCardChipItem extends BaseChipItem {
    /** 银行卡-余额少:基础星光 +4 */
    public static final int BASE_LOW = 4;
    /** 银行卡-余额多:基础星光 +7 */
    public static final int BASE_HIGH = 7;

    private final int baseStarlight;

    public BankCardChipItem(Properties properties, int baseStarlight) {
        super(properties);
        this.baseStarlight = baseStarlight;
    }

    public int getBaseStarlight() {
        return baseStarlight;
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack curio, ItemStack prevStack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!prevStack.isEmpty()) return;
        // 装备后立即把当前星光提升到至少基础值(set 内部 Math.max(base, ...))
        StarLightManager.set(player, StarLightManager.get(player));
    }
}
