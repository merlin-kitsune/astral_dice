package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.StarLightManager;

/**
 * 银行卡筹码(余额少/余额多):装备时一次性发放星光,卸下时按账本扣回。
 * 发放走 {@link StarLightManager#grantStarlightOnEquip}(装备会话闸门 + 实际量记账);
 * 兑换星币成功后的补充由 {@link StarLightManager#replenishOnExchange} 按当前佩戴统一处理。
 */
public class BankCardChipItem extends BaseChipItem {
    /** 银行卡-余额少:装备时发放的星光量 */
    public static final int STARLIGHT_LOW = 4;
    /** 银行卡-余额多:装备时发放的星光量 */
    public static final int STARLIGHT_HIGH = 7;

    private final int starlightAmount;

    public BankCardChipItem(Properties properties, int starlightAmount) {
        super(properties);
        this.starlightAmount = starlightAmount;
    }

    public int getStarlightAmount() {
        return starlightAmount;
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // ⚠️ Curios 官方签名 `onEquip(slotContext, prevStack, stack)`:第 2 参 prevStack 是**槽位原内容**
        // (往空槽装备时即 `ItemStack.EMPTY`),**第 3 参 stack 才是刚装上的那件**
        // (由 `ItemizedCurioCapability#onEquip` 以 `this.getStack()` 传入,恒非空)。
        // 旧代码把 `!prevStack.isEmpty()` 这个空槽守卫写在第 3 参上 ⇒ 恒为真、恒 return,
        // 银行卡的星光**从未发放**(2026-09-24 用户实报「所有银行卡都无法提供星光点数」;
        // 同款签名问题在 `DiceCurioItem` 早有记录,筹码/立牌这几处一直漏改)。
        // 发放走「装备会话闸门 + 发放账本」(grantStarlightOnEquip):闸门挡掉登录/重生重放,
        // 账本记下**实际**抬升量 ⇒ 卸下时能按实际值严格扣除(见 revokeStarlightOnUnequip)。
        // ⚠️ 不能只把当前值抬到发放量:那样卸下时既不会扣回(银行卡刷星光的根因),
        // 也不能在上限已满(一点没涨)时避免白扣玩家自己攒的星光。
        StarLightManager.grantStarlightOnEquip(player, grantBitForStarlight(), starlightAmount);
    }

    /** 与 {@link #starlightAmount} 对应的发放账本位(本类被注册两次:银行卡-余额少 / 余额多)。 */
    private int grantBitForStarlight() {
        return starlightAmount == STARLIGHT_HIGH
                ? StarLightManager.GRANT_BIT_BANK_CARD_HIGH
                : StarLightManager.GRANT_BIT_BANK_CARD_LOW;
    }

    // 卸下筹码:「卸除即扣除」全筹码底线 —— 按账本把装备时实际获得的星光扣回(并释放闸门)
    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        StarLightManager.revokeStarlightOnUnequip(player, grantBitForStarlight());
    }
}
