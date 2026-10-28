package com.merlinkitsune.astral_dice.item.dice;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * 骰子阶层定义:统一描述某款骰子的 Curios 动态槽位规则(筹码栏)与卡牌栏格数。
 *
 * 卡牌栏格数**由骰子品质(品阶)决定,与星级无关**(用户裁决 2026-10-28):
 * 基础骰子 4 格(攻防各 2)、T1(黄金/玻璃/下界岩) 8 格(各 4)、
 * T2(钻石/绿宝石/黑曜石/诡异/紫晶) 10 格(各 5)、T3(下界合金/绯红/末影) 12 格(各 6)、
 * T4(下界之星) 12 格(各 6,另多 1 个筹码栏)。
 * **星级只影响卡牌费用上限**({@code GameplayConstants.cardCostForStar})与赐福强度,不改格数。
 *
 * 新增骰子:在 {@link DiceTierRegistry} 注册一个 {@link DiceTier} 即可,
 * 筹码栏与卡牌栏计算(GUI)自动适配,无需修改 {@link DiceCurioItem}。
 * 立牌栏固定为 1(stand.json size=1,所有骰子一致),不随骰子/星级变化。
 *
 * 重要:item 使用 {@link Supplier} 延迟解析——禁止在 ModItems 静态初始化阶段调用
 * {@code DeferredHolder.get()}(注册表未加载会抛 IllegalStateException),必须延迟到运行时。
 *
 * @param id         骰子类型 id(如 dice/golden_dice/diamond_dice/netherite_dice)
 * @param item       对应骰子物品供应器(用于匹配;运行时才解析)
 * @param chipBonus  筹码栏加成:输入星级返回筹码栏数
 * @param cardSlots  卡牌栏总格数(按品阶固定,攻防各半;与星级无关)
 */
public record DiceTier(String id, Supplier<Item> item,
                       java.util.function.IntUnaryOperator chipBonus,
                       int cardSlots) {

    // 筹码栏目标数量(必须佩戴骰子才有;未佩戴时由调用方回退 0)
    public int targetChipSlots(int starLevel) {
        return chipBonus.applyAsInt(starLevel);
    }

    public boolean matches(ItemStack stack) {
        Item it = item.get();
        return it != null && stack.is(it);
    }
}
