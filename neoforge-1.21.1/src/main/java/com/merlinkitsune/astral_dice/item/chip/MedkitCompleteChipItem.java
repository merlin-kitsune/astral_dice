package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.HealingManager;

/**
 * 医疗箱-完备治疗筹码:触发骰神赐福时增加 3 点治愈(与每 1:00 结算时同理,由 {@link HealingManager#onBlessingTriggered} / {@link HealingManager#onTimerEnded} 结算);
 * <p>本条即用户 2026-10-01 裁决的「装备后未能立即触发治愈、重生后也不触发」修复:
 * 装备时、以及死亡重生 / 重新登录 / 切换维度后(筹码仍在槽位)各**完整触发**一次治愈
 * (先 +3 点治愈 → 按当前层数 ×2 回血 → 起/重置 1:00 计时器),由
 * {@link HealingManager#triggerMedkitOnEquip} 统一结算。
 *
 * <p>卸下时按账本回撤**本次装备触发**累计获得的层数(「卸除即扣除」);由赐福触发 / 每 1:00 结算
 * 获得的层数不在此列 —— 它们是对战局表现的奖励,卸下不回撤。已结算过的回血不追回。
 */
public class MedkitCompleteChipItem extends BaseChipItem {
    public MedkitCompleteChipItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // ⚠️ 不能用 prevStack 判"是否真的新装上":Curios 只持久化 stacks、**不持久化 previousStacks**
        // ⇒ 登录 / 重生 / 切维度后首 tick 的 prevStack 恒为空栈,Curios 会把这判成一次装备变化并
        // **重放 onEquip**,空槽守卫挡不住。区分手段是玩家级持久化闸门,见
        // HealingManager#triggerMedkitOnEquip 与 #claimMedkitEquipGrant。
        HealingManager.triggerMedkitOnEquip(player);
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        // 「卸除即扣除」:按账本回撤本次装备触发累计获得的层数(其它来源的层数不受影响)。
        HealingManager.revokeMedkitOnUnequip(player, HealingManager.GRANT_BIT_MEDKIT_COMPLETE);
    }
}
