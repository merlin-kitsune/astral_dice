package com.merlinkitsune.astral_dice.compat.jei;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.block.ModBlocks;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * 星愿铁砧的 JEI 插件：注册「星愿铁砧」升星类别，并把界面箭头的点击转成该类别里的精确配方页。
 *
 * <p>本类只由 JEI 发现/实例化；未安装 JEI 时不会被加载。配方跳转本身通过
 * {@link AstralAnvilJeiBridge} 暴露给屏幕，屏幕不直接依赖 JEI 类型。
 */
@JeiPlugin
public final class AstralAnvilJeiPlugin implements IModPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "astral_anvil");

    private AstralAnvilJeiCategory category;

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        this.category = new AstralAnvilJeiCategory(registration.getJeiHelpers().getGuiHelper());
        registration.addRecipeCategories(this.category);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(AstralAnvilJeiCategory.RECIPE_TYPE, AstralAnvilJeiCategory.recipes());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.ASTRAL_ANVIL.get()),
                AstralAnvilJeiCategory.RECIPE_TYPE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        AstralAnvilJeiBridge.setRecipeOpener(stack -> {
            AstralAnvilJeiCategory.Recipe recipe = AstralAnvilJeiCategory.find(stack);
            if (this.category == null) {
                return;
            }
            runtime.getRecipesGui().showRecipes(this.category, AstralAnvilJeiCategory.recipesStartingWith(recipe), List.of());
        });
    }

    @Override
    public void onRuntimeUnavailable() {
        AstralAnvilJeiBridge.setRecipeOpener(null);
    }
}
