package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.item.EmpowerManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 原初核心筹码:每消耗 1 层充能,获得 1 层「赋能」。
 *
 * <p>每层赋能提供攻击力 +1 与防御力 +1;防御力折算为真实护甲
 * (1 防御力 = 2 护甲值,见 {@link DiceCombatModifiers#setDefenseArmorBonus});
 * 赋能每 0:30 减少 1 层,层数为 1 时直接归 0
 * (计时器见 {@link EmpowerManager})。
 */
public class PrimordialCoreChipItem extends BaseChipItem {

    public PrimordialCoreChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴原初核心(赋能转换的前提) */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.PRIMORDIAL_CORE_CHIP.get())).isPresent();
    }

    /** 当前攻击力加成 = 赋能层数(未佩戴为 0) */
    public static int getAttackBonus(Player player) {
        if (!isEquipped(player)) return 0;
        return EmpowerManager.getStacks(player);
    }

    /** 当前防御力加成 = 赋能层数(未佩戴为 0) */
    public static int getDefenseBonus(Player player) {
        if (!isEquipped(player)) return 0;
        return EmpowerManager.getStacks(player);
    }

    /** 每 tick 驱动:把赋能层数折算为真实护甲 */
    public static void updateArmorBonus(Player player) {
        DiceCombatModifiers.setDefenseArmorBonus(player, "primordial_core_def_armor", getDefenseBonus(player));
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        DiceCombatModifiers.setDefenseArmorBonus(player, "primordial_core_def_armor", 0);
        EmpowerManager.removeAll(player);
    }
}
