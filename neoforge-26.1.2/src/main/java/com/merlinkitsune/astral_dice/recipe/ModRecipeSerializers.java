package com.merlinkitsune.astral_dice.recipe;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, AstralDiceMod.MODID);

    // 骰子升级配方:继承基础骰子的星级与已装配攻防牌配置
    // 26.1.2:RecipeSerializer 已是 record,实例由 DiceUpgradeShapedRecipe.SERIALIZER 提供
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> DICE_UPGRADE =
            RECIPE_SERIALIZERS.register("dice_upgrade", () -> DiceUpgradeShapedRecipe.SERIALIZER);
}
