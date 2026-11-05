package com.merlinkitsune.astral_dice.compat.jei;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.block.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 筹码商店的 JEI 插件：向 JEI 注册「筹码商店」获取方式类别（astral_dice:chip_shop）。
 *
 * <p>与 {@link CardShopJeiPlugin}（astral_dice:card_shop）完全对称，两者是**各自独立的插件**：
 * JEI 的插件 uid 必须全局唯一，故这里用 {@code astral_dice:chip_shop}，不与卡牌商店共用。
 *
 * <p><b>JEI 缺席安全</b>：Fabric 侧 JEI 通过 {@code fabric.mod.json} 的 {@code jei_mod_plugin}
 * entrypoint 反射实例化本类（见 mezz.jei.fabric.startup.FabricPluginFinder#getModPlugins），
 * 只有装了 JEI 并由其读取 entrypoint 时才会加载本类；模组其它代码没有任何静态引用，
 * 未安装 JEI 时本包不会被加载。{@code @JeiPlugin} 注解在 Fabric 侧不参与发现，保留仅为与
 * forge 蓝本源码对齐。
 *
 * <p><b>Fabric 1.20.1 移植说明</b>：本文件由 forge-1.20.1 的同路径文件移植而来，目标 JEI 为
 * 15.62.0.216。本插件用到的 JEI API 已用 javap 核对，签名与 forge 侧一致；{@code UID} 沿用
 * {@code ResourceLocation.fromNamespaceAndPath(ns, path)}，与 fabric 线既有写法一致。
 *
 * <p>注册三件事：
 * <ol>
 *   <li>{@link #registerCategories} —— 建类别（astral_dice:chip_shop）；</li>
 *   <li>{@link #registerRecipes} —— 把池内每枚筹码注册为一条记录，
 *       玩家在 JEI 里查该筹码的「用途」页即可看到「如何获得」；</li>
 *   <li>{@link #registerRecipeCatalysts} —— 把筹码商店方块登记为该类别的催化剂，
 *       玩家查商店方块即可看到整个类别。</li>
 * </ol>
 */
@JeiPlugin
public class ChipShopJeiPlugin implements IModPlugin {

    /** 插件 uid（JEI 要求全局唯一；与卡牌商店的 astral_dice:card_shop 不重复）。 */
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "chip_shop");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ChipShopCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<ChipShopRecipe> recipes = new ArrayList<>();
        for (Item chip : ChipShopJeiRecipes.sellableChips()) {
            recipes.add(new ChipShopRecipe(chip));
        }
        registration.addRecipes(ChipShopCategory.RECIPE_TYPE, recipes);
        // 加分项：商店方块自己的「用途」页说明
        registration.addItemStackInfo(new ItemStack(ModBlocks.CHIP_SHOP.get()),
                Component.translatable("jei.astral_dice.chip_shop.block_info"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.CHIP_SHOP.get()),
                ChipShopCategory.RECIPE_TYPE);
    }
}
