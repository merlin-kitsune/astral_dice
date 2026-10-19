package com.merlinkitsune.astral_dice.item.chip;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.starengine.item.CuriosCompat;

import net.minecraft.world.entity.player.Player;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * ATM机筹码(2026-10-08 用户裁决**重写**):装备后,**每次向其他玩家转账星币**时,转账双方各额外获得
 * {@code 1} 星光与 {@code 2} 星币(由转账发起者是否装备本筹码判定;结算在
 * {@link com.merlinkitsune.astral_dice.shop.CardShopMenu#handleTransfer})。
 *
 * <p>⚠️ 原两项能力(装备时 +1 星光、星光兑换 +40%)已按用户裁决**移除** —— 装备不再发放星光,
 * 故本类不再有 {@code onEquip}/{@code onChipUnequip} 账本钩子;交换加成改由经商立牌被动承担。
 *
 * <p><b>1.20.1 Forge 平台差异</b>:本线 Curios 入口走库 shim
 * {@link com.merlinkitsune.starengine.item.CuriosCompat#getCuriosInventory}
 * (官方 {@code CuriosApi} 在本线返回 {@code LazyOptional},无 {@code isEmpty()/get()} 同形用法)。
 */
public class AtmChipItem extends BaseChipItem {
    public AtmChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴 ATM机筹码
    public static boolean isEquipped(Player player) {
        // 总闸门(骰子装备和卸除调整):未佩戴骰子 ⇒ 本件功能一律不生效(数值保留)
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.ATM.get())).isPresent();
    }

}
