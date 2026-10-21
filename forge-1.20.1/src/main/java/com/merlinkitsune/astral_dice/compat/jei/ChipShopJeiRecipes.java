package com.merlinkitsune.astral_dice.compat.jei;

import com.merlinkitsune.astral_dice.shop.ChipShopCatalog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * JEI 侧的可售筹码清单。
 *
 * <p><b>唯一判据</b>：直接遍历 {@link ChipShopCatalog#entries()}（67 条静态池），
 * 不做任何额外过滤 —— 商店货架本身就是从同一个池子里按品质权重抽的，
 * 这里若另写过滤条件，JEI 展示就会与实机货架口径漂移。
 * 仅跳过注册表缺失导致的 {@code minecraft:air}（旧存档 / 裁剪线安全降级）。
 *
 * <p>本类只在 JEI 插件被加载时（{@code @JeiPlugin}）被引用，JEI 缺席时不会被加载。
 *
 * <p>1.20.1 移植说明：与 neoforge-1.21.1 的同名文件逐字等价（两线的
 * ChipShopCatalog 池表本身即逐字等价），未做任何平台改写。
 */
public final class ChipShopJeiRecipes {

    private ChipShopJeiRecipes() {
    }

    /**
     * 返回筹码商店池中所有筹码，顺序与 {@link ChipShopCatalog#entries()} 一致。
     *
     * @return 可售筹码物品清单（副本，调用方可自由修改）
     */
    public static List<Item> sellableChips() {
        List<Item> chips = new ArrayList<>();
        for (ChipShopCatalog.Entry entry : ChipShopCatalog.entries()) {
            Item item = entry.item();
            if (item != null && item != Items.AIR) {
                chips.add(item);
            }
        }
        return chips;
    }
}
