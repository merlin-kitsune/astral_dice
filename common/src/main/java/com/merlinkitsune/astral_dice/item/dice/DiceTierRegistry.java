package com.merlinkitsune.astral_dice.item.dice;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 骰子阶层注册表：按注册顺序存储全部骰子定义，并提供按物品匹配的查询。
 *
 * <p>本类位于主项目 {@code common} 源码根，五个加载器线共用。注册时同时把
 * 描述符写入 StarEngine 的全局骰子注册表，保证第三方通过
 * {@code AstralDiceApi.registerDiceTier(...)} 登记的骰子也能参与主模组玩法
 * （{@link DiceCurioItem} 的筹码栏 / 卡牌栏、星愿铁砧升星等）。
 *
 * <p>注册表在 {@link #freeze()} 之后拒绝写入；冻结由模组在内容注册完成后调用。
 */
public final class DiceTierRegistry {

    private static final List<DiceTier> TIERS = new ArrayList<>();
    private static boolean frozen;

    private DiceTierRegistry() {
    }

    /** 登记一个骰子阶层；同 id 的 StarEngine 描述符按“替换并保留原位置”处理。 */
    public static synchronized void register(DiceTier tier) {
        if (tier == null) {
            throw new NullPointerException("tier");
        }
        if (frozen) {
            throw new IllegalStateException("dice tier registry is frozen");
        }
        if (com.merlinkitsune.starengine.api.content.dice.DiceTiers.isFrozen()) {
            throw new IllegalStateException("starengine dice tier registry is frozen");
        }
        boolean replaced = false;
        for (int i = 0; i < TIERS.size(); i++) {
            if (TIERS.get(i).id().equals(tier.id())) {
                TIERS.set(i, tier);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            TIERS.add(tier);
        }
        com.merlinkitsune.starengine.api.content.dice.DiceTiers.register(
                new com.merlinkitsune.starengine.api.content.dice.DiceTier(
                        tier.id(), tier.cardSlots(), tier::targetChipSlots));
    }

    /** 按物品栈匹配骰子阶层；未匹配返回 {@code null}。 */
    public static synchronized DiceTier get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        for (DiceTier tier : TIERS) {
            if (tier.matches(stack)) {
                return tier;
            }
        }
        return null;
    }

    /** 按 id 取骰子阶层；不存在返回 {@code null}。 */
    public static synchronized DiceTier get(String id) {
        if (id == null) {
            return null;
        }
        for (DiceTier tier : TIERS) {
            if (tier.id().equals(id)) {
                return tier;
            }
        }
        return null;
    }

    /** 是否任意骰子（注册表为空时返回 false）。 */
    public static synchronized boolean isDice(ItemStack stack) {
        return get(stack) != null;
    }

    /** 全部已注册骰子；返回快照，调用方修改不影响注册表。 */
    public static synchronized List<DiceTier> all() {
        return List.copyOf(TIERS);
    }

    /** 冻结注册表，并同时冻结 StarEngine 侧的骰子描述符注册表。 */
    public static synchronized void freeze() {
        frozen = true;
        com.merlinkitsune.starengine.api.content.dice.DiceTiers.freeze();
    }

    public static synchronized boolean isFrozen() {
        return frozen;
    }
}
