package com.merlinkitsune.astral_dice.crafting;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * 本模组自建的配方序列化器注册表。
 *
 * <p>原版 {@code BuiltInRegistries.RECIPE_SERIALIZER} 在 1.20.1 是**可写**的
 * （原版自身就在静态块里 {@code register("crafting_shaped", …)}），Fabric 侧无需 Mixin
 * 或扩展点即可追加 —— 这是 {@code Ingredient} 封死之后仍能实现「指定 NBT 合成」的关键。
 *
 * <p>注册时机：必须早于数据包加载，即 {@code ModInitializer#onInitialize} 期间
 * （见 {@code AstralDiceMod#onInitialize}）。
 */
public final class AstralRecipeSerializers {

    /**
     * 「带 NBT 约束的有序合成」：配方 {@code type} = {@code astral_dice:nbt_shaped}。
     *
     * <p>产出物仍是 {@link net.minecraft.world.item.crafting.RecipeType#CRAFTING} 下的
     * {@code CraftingRecipe}，因此 JEI 等按 recipe type（而非 serializer id）索引的
     * 配方查看器无需任何适配。
     */
    public static final RecipeSerializer<NbtShapedRecipe> NBT_SHAPED = new NbtShapedRecipeSerializer();

    private AstralRecipeSerializers() {
    }

    /** 由 {@code AstralDiceMod#onInitialize} 调用一次。 */
    public static void register() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                new ResourceLocation("astral_dice", "nbt_shaped"), NBT_SHAPED);
    }
}
