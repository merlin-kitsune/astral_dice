package com.merlinkitsune.astral_dice.item.dice;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * 骰子阶层定义：统一描述某款骰子的 Curios 动态筹码栏规则与卡牌栏格数。
 *
 * <p>本类位于主项目 {@code common} 源码根，五个加载器线共用同一份实现；
 * 不依赖任何加载器 API。
 *
 * <p>卡牌栏格数由骰子品质（品阶）决定，与星级无关；
 * 筹码栏数量是“当前星级下的绝对格数”，由 {@link #targetChipSlots(int)} 返回。
 *
 * <p>item 使用 {@link Supplier} 延迟解析，禁止在 ModItems 静态初始化阶段调用
 * {@code DeferredHolder.get()}（注册表未加载会抛 IllegalStateException）。
 *
 * @param id        骰子类型 id（如 dice/golden_dice/diamond_dice/netherite_dice）
 * @param item      对应骰子物品供应器（用于匹配；运行时才解析）
 * @param chipSlots 筹码栏绝对数量：输入星级返回筹码栏格数
 * @param cardSlots 卡牌栏总格数（按品阶固定，攻防各半；与星级无关）
 */
public record DiceTier(String id, Supplier<Item> item,
                       IntUnaryOperator chipSlots,
                       int cardSlots) {

    /** 筹码栏目标数量（必须佩戴骰子才有；未佩戴时由调用方回退 0）。 */
    public int targetChipSlots(int starLevel) {
        return Math.max(0, chipSlots.applyAsInt(Math.max(0, starLevel)));
    }

    public boolean matches(ItemStack stack) {
        Item it = item.get();
        return it != null && stack.is(it);
    }
}
