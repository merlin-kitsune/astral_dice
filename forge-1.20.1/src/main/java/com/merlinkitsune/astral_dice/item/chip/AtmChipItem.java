package com.merlinkitsune.astral_dice.item.chip;
import com.merlinkitsune.starenginelib.item.CuriosCompat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.resource.ResourceConversion;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;

/**
 * ATM机筹码:装备时获得 1 点星光(一次性);
 * 使用星光兑换星币时,兑换量(星币产出)增加 40%(结算在 {@link com.merlinkitsune.astral_dice.resource.ResourceConversion})。
 */
public class AtmChipItem extends BaseChipItem {
    public AtmChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴 ATM机筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.ATM.get())).isPresent();
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // ⚠️ 第 3 参 stack 才是刚装上的那件(恒非空);旧代码把空槽守卫写在第 3 参上 ⇒ 恒 return、星光从未发放。
        // 一次性发放改由「装备会话闸门」判定:Curios 登录 / 重生 / 切维度后会重放 onEquip
        // (重放时 prevStack 恰好是空栈,空槽守卫拦不住)⇒ 见 StarLightManager#claimEquipGrant。
        if (!StarLightManager.claimEquipGrant(player, StarLightManager.GRANT_BIT_ATM)) return;
        // 装备时星光 +1(上限由 StarLightManager 统一管理)
        StarLightManager.add(player, 1);
    }

    // 卸下筹码:释放发放闸门 ⇒ 再次装备可再发
    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        StarLightManager.releaseEquipGrant(player, StarLightManager.GRANT_BIT_ATM);
    }
}
