package com.merlinkitsune.astral_dice.item.dice;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * 骰子阶层注册表:按注册顺序存储全部骰子定义,并提供按物品匹配的查询。
 * 新增骰子:调用 {@link #register(DiceTier)} 注册即可(建议在 ModItems 中随物品创建后调用,
 * 或集中在一个静态初始化块)。未匹配到注册表时回退基础骰子规则。
 */
public final class DiceTierRegistry {

    private static final List<DiceTier> TIERS = new ArrayList<>();

    private DiceTierRegistry() {
    }

    public static void register(DiceTier tier) {
        TIERS.add(tier);
    }

    // 按物品栈匹配骰子阶层;未匹配返回 null
    public static DiceTier get(ItemStack stack) {
        for (DiceTier tier : TIERS) {
            if (tier.matches(stack)) {
                return tier;
            }
        }
        return null;
    }

    // 是否任意骰子(与 DiceCurioItem.isDiceItem 等价;注册表为空时回退为 false)
    public static boolean isDice(ItemStack stack) {
        return get(stack) != null;
    }

    public static List<DiceTier> all() {
        return List.copyOf(TIERS);
    }
}
