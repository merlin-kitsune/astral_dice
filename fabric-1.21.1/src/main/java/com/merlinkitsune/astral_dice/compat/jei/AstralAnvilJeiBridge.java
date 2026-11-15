package com.merlinkitsune.astral_dice.compat.jei;

import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;

/**
 * 星愿铁砧界面的 JEI 配方跳转桥。
 *
 * <p>本类刻意不引用任何 JEI 类型：未安装 JEI 时 {@code AstralAnvilScreen} 仍可安全加载，
 * {@link #showRecipes(ItemStack)} 直接返回 {@code false}。JEI 插件只在
 * {@code onRuntimeAvailable} 中注入 opener，运行时卸载时清空。
 */
public final class AstralAnvilJeiBridge {

    private static volatile Consumer<ItemStack> recipeOpener;

    private AstralAnvilJeiBridge() {
    }

    public static void setRecipeOpener(Consumer<ItemStack> opener) {
        recipeOpener = opener;
    }

    public static boolean isAvailable() {
        return recipeOpener != null;
    }

    /**
     * 让 JEI 打开指定物品的配方页；返回是否真的调用了 JEI。
     */
    public static boolean showRecipes(ItemStack stack) {
        Consumer<ItemStack> opener = recipeOpener;
        if (opener == null || stack.isEmpty()) {
            return false;
        }
        opener.accept(stack.copy());
        return true;
    }
}
