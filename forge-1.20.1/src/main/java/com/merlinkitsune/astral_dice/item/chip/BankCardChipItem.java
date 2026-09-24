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
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // ⚠️ Curios 官方签名 `onEquip(slotContext, prevStack, stack)`:第 2 参 prevStack 是**槽位原内容**
        // (往空槽装备时即 `ItemStack.EMPTY`),**第 3 参 stack 才是刚装上的那件**
        // (由 `ItemizedCurioCapability#onEquip` 以 `this.getStack()` 传入,恒非空)。
        // 旧代码把 `!prevStack.isEmpty()` 这个空槽守卫写在第 3 参上 ⇒ 恒为真、恒 return,
        // 银行卡的基础星光**从未生效**(2026-09-24 用户实报「所有银行卡都无法提供星光点数」;
        // 同款签名问题在 `DiceCurioItem` 早有记录,筹码/立牌这几处一直漏改)。
        // 本处**不加任何守卫**:set() 本身即幂等(只在低于基础值时抬升),重复触发无副作用,
        // 因而"换装进非空槽 / 槽位激活 / 登录重放"各条路径都能覆盖到。
        // 装备后立即把当前星光提升到至少基础值(set 内部 Math.max(base, ...))
        StarLightManager.set(player, StarLightManager.get(player));
    }
}
