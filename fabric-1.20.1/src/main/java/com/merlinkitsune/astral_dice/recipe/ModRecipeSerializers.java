package com.merlinkitsune.astral_dice.recipe;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, AstralDiceMod.MODID);

    // 骰子升级配方:继承基础骰子的星级与已装配攻防牌配置
    public static final RegistryObject<RecipeSerializer<?>> DICE_UPGRADE =
            RECIPE_SERIALIZERS.register("dice_upgrade", () -> DiceUpgradeShapedRecipe.Serializer.INSTANCE);
}
