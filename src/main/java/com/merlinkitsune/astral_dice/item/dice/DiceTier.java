package com.merlinkitsune.astral_dice.item.dice;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * 骰子阶层定义:统一描述某款骰子的卡牌栏槽位与 Curios 动态槽位规则。
 *
 * 新增骰子:在 {@link DiceTierRegistry} 注册一个 {@link DiceTier} 即可,
 * 槽位计算(GUI 卡牌栏/筹码栏)全部自动适配,无需修改 {@link DiceCurioItem}。
 * 立牌栏固定为 1(stand.json size=1,所有骰子一致),不随骰子/星级变化。
 *
 * 重要:item 使用 {@link Supplier} 延迟解析——禁止在 ModItems 静态初始化阶段调用
 * {@code DeferredHolder.get()}(注册表未加载会抛 IllegalStateException),必须延迟到运行时。
 *
 * @param id         骰子类型 id(如 dice/golden_dice/diamond_dice/netherite_dice)
 * @param item       对应骰子物品供应器(用于匹配;运行时才解析)
 * @param cardSlots  卡牌放置栏总槽位数(攻防各一半)
 * @param chipBonus  筹码栏加成:输入星级返回筹码栏数
 */
public record DiceTier(String id, Supplier<Item> item, int cardSlots,
                       java.util.function.IntUnaryOperator chipBonus) {

    // 筹码栏目标数量(必须佩戴骰子才有;未佩戴时由调用方回退 0)
    public int targetChipSlots(int starLevel) {
        return chipBonus.applyAsInt(starLevel);
    }

    public boolean matches(ItemStack stack) {
        Item it = item.get();
        return it != null && stack.is(it);
    }
}
