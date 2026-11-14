package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import com.merlinkitsune.starengine.component.GameplayConstants;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 星愿铁砧升星的**唯一事实源**（2026-11-15 用户裁决）。
 *
 * <p>把原先散落在 {@code event.AnvilUpgradeHandler} 与星愿铁砧菜单里的数值收在一处，四个入口：
 * <ul>
 *   <li>{@link #isDice(ItemStack)}：13 种骰子白名单（升星输入判定）；</li>
 *   <li>{@link #starCoinCost(int)}：星币成本 0★→15 / 1★→20 / 2★→25，不可升（≥3★）⇒ -1；</li>
 *   <li>{@link #resultFor(ItemStack)}：按 {@link #experimentalEnabled()} 产出「星级 +1 且卡牌保留」或「0★ 且清空卡牌」；</li>
 *   <li>{@link #xpCostFor(ItemStack)}：关闭时固定 10 级；开启时按输入骰子的 dice_t0..dice_t4 分级取 5 / 10 / 15 / 20 / 30，未命中 5。</li>
 * </ul>
 *
 * <p>⚠️ 预览（铁砧更新事件）、真正授予与星愿铁砧菜单必须**都调本类**，不得在调用方再写第二套数值 ——
 * 两套数值一旦漂移，预览价与实际授予条件就会不一致。
 */
public final class DiceStarUpgrade {
    /** 关闭实验开关时的经验等级（与改动前逐字一致：固定 10 级）。 */
    public static final int XP_COST_NORMAL = 10;
    /** dice_t0（基础骰）。 */
    public static final int XP_COST_T0 = 5;
    /** dice_t1（黄金 / 玻璃 / 下界岩）。 */
    public static final int XP_COST_T1 = 10;
    /** dice_t2（钻石 / 绿宝石 / 黑曜石 / 诡异 / 紫晶）。 */
    public static final int XP_COST_T2 = 15;
    /** dice_t3（下界合金 / 绯红 / 末影）。 */
    public static final int XP_COST_T3 = 20;
    /** dice_t4（下界之星）。 */
    public static final int XP_COST_T4 = 30;
    /** 未命中任何 dice_tN 标签（13 种骰子已全覆盖，这里只是兜底口径）。 */
    public static final int XP_COST_UNTIERED = 5;

    private DiceStarUpgrade() {
    }

    /** {@code experimental_star_upgrade} 开关（服务端玩法项，默认 false）。 */
    public static boolean experimentalEnabled() {
        return ModCommonConfig.EXPERIMENTAL_STAR_UPGRADE.get();
    }

    /** 13 种骰子白名单（基础/黄金/玻璃/下界岩/钻石/绿宝石/黑曜石/诡异/紫晶/下界合金/绯红/末影/下界之星）。 */
    public static boolean isDice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(ModItems.DICE.get()) || stack.is(ModItems.GOLDEN_DICE.get()) || stack.is(ModItems.DIAMOND_DICE.get())
                || stack.is(ModItems.NETHERITE_DICE.get()) || stack.is(ModItems.EMERALD_DICE.get())
                || stack.is(ModItems.GLASS_DICE.get()) || stack.is(ModItems.NETHERRACK_DICE.get())
                || stack.is(ModItems.OBSIDIAN_DICE.get()) || stack.is(ModItems.WEIRD_DICE.get())
                || stack.is(ModItems.AMETHYST_DICE.get()) || stack.is(ModItems.CRIMSON_DICE.get())
                || stack.is(ModItems.ENDER_DICE.get()) || stack.is(ModItems.NETHER_STAR_DICE.get());
    }

    /** 升星星币成本：0★→15 / 1★→20 / 2★→25；不可升（≥3★ 或非法星阶）⇒ -1。 */
    public static int starCoinCost(int starLevel) {
        return switch (starLevel) {
            case 0 -> 15;
            case 1 -> 20;
            case 2 -> 25;
            default -> -1;
        };
    }

    /**
     * 便捷重载：按输入骰子当前星阶取星币成本（预览/菜单侧按栈取价时用）。
     *
     * @return 与 {@link #starCoinCost(int)} 同一组数值；非骰子 ⇒ -1
     */
    public static int starCoinCost(ItemStack input) {
        if (!isDice(input)) return -1;
        return starCoinCost(enhancementOf(input).starLevel());
    }

    /**
     * 铁砧预览/产出的形态（**不含**星币数量校验：数量是否够由调用方判）。
     *
     * @return 新产物栈（输入栈已复制）；输入不是骰子或该星阶不可升 ⇒ {@link ItemStack#EMPTY}
     */
    public static ItemStack resultFor(ItemStack input) {
        if (!isDice(input)) return ItemStack.EMPTY;
        if (starCoinCost(enhancementOf(input).starLevel()) < 0) return ItemStack.EMPTY;
        return experimentalEnabled() ? experimentalReset(input) : normalUpgraded(input);
    }

    /** 普通升星（实验开关关闭）：星级 +1，卡牌（appliedStones）**原样透传**，卡牌上限按新星阶重算。 */
    public static ItemStack normalUpgraded(ItemStack input) {
        if (!isDice(input)) return ItemStack.EMPTY;
        WeaponEnhancement enhancement = enhancementOf(input);
        int star = enhancement.starLevel();
        if (starCoinCost(star) < 0) return ItemStack.EMPTY;
        ItemStack output = input.copy();
        setEnhancement(output, new WeaponEnhancement(
                enhancement.usedCost(),
                GameplayConstants.cardCostForStar(star + 1),
                enhancement.usedDefenseCost(),
                GameplayConstants.cardCostForStar(star + 1),
                star + 1,
                // ⚠️ appliedStones 必须**原样透传**(不重建):临时牌(绿洲女王 nardis)的
                // temporary 分量存在 AppliedStone 里,重建/拷贝时丢标记 = 该牌到期清不掉。
                enhancement.appliedStones()));
        return output;
    }

    /** 实验性升星（实验开关开启）：产出 **0★ + 空 appliedStones**，卡牌由取走时的 {@code CardReturnHelper} 归还。 */
    public static ItemStack experimentalReset(ItemStack input) {
        if (!isDice(input)) return ItemStack.EMPTY;
        if (starCoinCost(enhancementOf(input).starLevel()) < 0) return ItemStack.EMPTY;
        ItemStack output = input.copy();
        setEnhancement(output, new WeaponEnhancement(
                0,
                GameplayConstants.cardCostForStar(0),
                0,
                GameplayConstants.cardCostForStar(0),
                0,
                List.of()));
        return output;
    }

    /**
     * 升星消耗的经验等级：关闭实验开关 ⇒ 固定 10；开启 ⇒ 按**输入骰子**的标签分级（与星级无关）。
     */
    public static int xpCostFor(ItemStack input) {
        if (!experimentalEnabled()) return XP_COST_NORMAL;
        if (input == null || input.isEmpty()) return XP_COST_UNTIERED;
        if (input.is(ModTags.Items.DICE_T0)) return XP_COST_T0;
        if (input.is(ModTags.Items.DICE_T1)) return XP_COST_T1;
        if (input.is(ModTags.Items.DICE_T2)) return XP_COST_T2;
        if (input.is(ModTags.Items.DICE_T3)) return XP_COST_T3;
        if (input.is(ModTags.Items.DICE_T4)) return XP_COST_T4;
        return XP_COST_UNTIERED;
    }

    private static WeaponEnhancement enhancementOf(ItemStack stack) {
        return ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(stack, WeaponEnhancement.EMPTY);
    }

    private static void setEnhancement(ItemStack stack, WeaponEnhancement value) {
        ModDataComponents.WEAPON_ENHANCEMENT.set(stack, value);
    }
}
