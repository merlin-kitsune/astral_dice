package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 立牌装备判定统一入口（等价重构）：
 * 原 22 个立牌类各自维护的 {@code static boolean isEquipped(Player)} 均为
 * 「已佩戴骰子（总闸门）+ Curios 中存在该立牌」的同一套判定，收敛到此单一实现。
 */
public final class SignEquipCheck {
    private SignEquipCheck() {
    }

    /**
     * 立牌是否**在位**（只看 Curios {@code stand} 槽，**不含骰子闸门**）。
     *
     * <p>2026-10-27 用户裁决「强化拒止必须依附于怪力乱神立牌」引入：这是**合法性**判据，
     * 不是**功能性**判据 —— 换骰子只让功能暂时不生效，不该把强化拒止销毁；只有立牌离身才算失去依附。
     */
    public static boolean hasSign(Player player, Item signItem) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(signItem)).isPresent();
    }

    /** 是否佩戴了 {@code signItem} 且已佩戴骰子（被动统计的前提）。 */
    public static boolean isEquipped(Player player, Item signItem) {
        if (player == null) return false;
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        return hasSign(player, signItem);
    }
}
